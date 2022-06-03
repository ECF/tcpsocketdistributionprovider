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
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
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
import org.eclipse.ecf.remoteservice.RemoteServiceID;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientContainer;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientService;
import org.eclipse.ecf.remoteservice.client.IRemoteCallable;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.eclipse.ecf.remoteservice.events.IRemoteCallCompleteEvent;

public class TCPSocketClientContainer extends AbstractRSAClientContainer {

	public TCPSocketClientContainer() {
		super(TCPSocketNamespace.createClientID());
	}

	private Socket clientSocket;
	private ObjectOutputStream oos;
	private ObjectInputStream ois;

	class TCPClientRegistration extends RSAClientRegistration {

		Long convertPropValueToLong(Dictionary<?, ?> props) {
			Object val = props.get("ecf.socket.server.remoteserviceid");
			if (val == null)
				return null;
			if (val instanceof Long)
				return (Long) val;
			if (val instanceof String) {
				try {
					return Long.valueOf((String) val);
				} catch (Exception e) {
				}
			}
			return null;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		Dictionary createDictionary(Dictionary original, long rsId) {
			Dictionary result = new Properties();
			for (Enumeration<?> e = original.keys(); e.hasMoreElements();) {
				String key = (String) e.nextElement();
				if (key.equals("ecf.rsvc.id")) {
					result.put(key, rsId);
				} else {
					result.put(key, original.get(key));
				}
			}
			return result;
		}

		public TCPClientRegistration(ID targetID, String[] classNames, IRemoteCallable[][] restCalls,
				@SuppressWarnings("rawtypes") Dictionary properties) {
			super(targetID, classNames, restCalls, properties);
			Long rsId = convertPropValueToLong(properties);
			if (rsId != null && rsId > 0) {
				this.serviceID = new RemoteServiceID(getConnectNamespace(), this.containerId, rsId);
				this.properties = createDictionary(properties, rsId);
			}
		}

	}

	protected RemoteServiceClientRegistration createRSAClientRegistration(ID targetID, String[] interfaces,
			Map<String, Object> endpointDescriptionProperties) {
		@SuppressWarnings("rawtypes")
		Dictionary d = createRegistrationProperties(endpointDescriptionProperties);
		return new TCPClientRegistration(targetID, interfaces, createRegistrationCallables(targetID, interfaces, d), d);
	}

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
					long rsvcid = (Long) getRegistration().getProperty(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID);
					oos.writeLong(rsvcid);
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
