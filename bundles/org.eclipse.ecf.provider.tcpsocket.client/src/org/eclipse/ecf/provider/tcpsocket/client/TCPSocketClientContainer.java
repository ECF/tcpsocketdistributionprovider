/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.Callable;

import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.events.ContainerConnectedEvent;
import org.eclipse.ecf.core.events.ContainerConnectingEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.identity.URIID;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.ecf.core.util.OSGIObjectInputStream;
import org.eclipse.ecf.core.util.OSGIObjectOutputStream;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.remoteservice.IRemoteService;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientContainer;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientService;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.eclipse.ecf.remoteservice.events.IRemoteCallCompleteEvent;

public class TCPSocketClientContainer extends AbstractRSAClientContainer {

	public TCPSocketClientContainer() {
		super(TCPSocketNamespace.createClientID());
	}

	private Socket clientSocket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	@Override
	public void connect(ID targetID, IConnectContext connectContext1) throws ContainerConnectException {
		synchronized (connectLock) {
			if (connectedID == null) {
				fireContainerEvent(new ContainerConnectingEvent(containerID, targetID));
				try {
					URI uri = ((URIID) targetID).toURI();
					// open socket with given host and port
					this.clientSocket = new Socket(uri.getHost(), uri.getPort());
					// create object output stream
					this.oos = new OSGIObjectOutputStream(clientSocket.getOutputStream());
					// writeLong(0) to connect
					this.oos.writeLong(0);
					this.oos.flush();
					// open object input stream for return values
					this.ois = new OSGIObjectInputStream(Activator.getContext().getBundle(),
							clientSocket.getInputStream());
				} catch (Exception e) {
					this.clientSocket = null;
					throw new ContainerConnectException("Cannot connect to targetID=" + targetID.getName(), e);
				}
				connectedID = targetID;
				this.connectContext = connectContext1;
				fireContainerEvent(new ContainerConnectedEvent(containerID, targetID));
			} else if (!connectedID.equals(targetID)) {
				throw new ContainerConnectException(
						"Container already connected to targetID=" + this.connectedID.getName());
			}
		}
	}

	@Override
	protected IRemoteService createRemoteService(final RemoteServiceClientRegistration aRegistration) {
		return new AbstractRSAClientService(TCPSocketClientContainer.this, aRegistration) {
			@Override
			protected Callable<IRemoteCallCompleteEvent> getAsyncCallable(final RSARemoteCall call) {
				return () -> {
					Object result = invokeRemote(call.getMethod(), call.getParameters());
					if (result instanceof Throwable)
						return createRCCEFailure((Throwable) result);
					return createRCCESuccess(result);
				};
			}

			Object invokeRemote(String methodName, Object[] args) throws Exception {
				synchronized (clientSocket) {
					RemoteServiceClientRegistration reg = getRegistration();
					long rsvcid = (Long) reg
							.getProperty(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID);
					String rsFilter = (String) reg.getProperty(org.eclipse.ecf.remoteservice.Constants.ENDPOINT_REMOTESERVICE_FILTER);
					oos.writeLong(rsvcid);
					oos.writeUTF(rsFilter);
					oos.writeObject(methodName);
					oos.writeObject(args);
					oos.flush();
					return ois.readObject();
				}
			}

			@Override
			protected Callable<Object> getSyncCallable(final RSARemoteCall call) {
				return () -> {
					return invokeRemote(call.getReflectMethod().getName(), call.getParameters());
				};
			}
		};
	}

	@Override
	public void disconnect() {
		synchronized (connectLock) {
			super.disconnect();
			if (clientSocket != null) {
				try {
					clientSocket.close();
				} catch (IOException e) {
				}
				clientSocket = null;
			}
		}
	}
}
