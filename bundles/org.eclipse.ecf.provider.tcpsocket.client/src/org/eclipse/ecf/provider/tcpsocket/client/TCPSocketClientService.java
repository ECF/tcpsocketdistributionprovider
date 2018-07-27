/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Callable;

import org.eclipse.ecf.remoteservice.client.AbstractClientContainer;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientService;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.eclipse.ecf.remoteservice.events.IRemoteCallCompleteEvent;

public class TCPSocketClientService extends AbstractRSAClientService {

	private ObjectInputStream ois;
	private ObjectOutputStream oos;

	public TCPSocketClientService(AbstractClientContainer container, RemoteServiceClientRegistration registration,
			ObjectInputStream ois, ObjectOutputStream oos) {
		super(container, registration);
		this.ois = ois;
		this.oos = oos;
	}

	@Override
	protected Callable<IRemoteCallCompleteEvent> getAsyncCallable(final RSARemoteCall call) {
		return () -> {
			synchronized (ois) {
				Object result = invokeRemote(call.getMethod(), call.getParameters());
				if (result instanceof Throwable)
					return createRCCEFailure((Throwable) result);
				return createRCCESuccess(result);
			}
		};
	}

	Object invokeRemote(String methodName, Object[] args) throws Exception {
		long rsvcid = (Long) getRegistration().getProperty(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID);
		oos.writeLong(rsvcid);
		oos.writeObject(methodName);
		oos.writeObject(args);
		oos.flush();
		return ois.readObject();
	}

	@Override
	protected Callable<Object> getSyncCallable(final RSARemoteCall call) {
		return () -> {
			synchronized (ois) {
				return invokeRemote(call.getReflectMethod().getName(), call.getParameters());
			}
		};
	}

}
