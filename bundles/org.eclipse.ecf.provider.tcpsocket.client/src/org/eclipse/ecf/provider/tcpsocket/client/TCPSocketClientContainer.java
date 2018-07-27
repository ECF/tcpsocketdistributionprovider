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

import org.eclipse.ecf.core.util.OSGIObjectInputStream;
import org.eclipse.ecf.core.util.OSGIObjectOutputStream;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.remoteservice.IRemoteService;
import org.eclipse.ecf.remoteservice.client.AbstractRSAClientContainer;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;

public class TCPSocketClientContainer extends AbstractRSAClientContainer {

	public TCPSocketClientContainer() {
		super(TCPSocketNamespace.createID());
	}

	private Socket clientSocket;

	@Override
	protected IRemoteService createRemoteService(final RemoteServiceClientRegistration aRegistration) {
		try {
			// create socket
			clientSocket = new Socket("localhost", 3000);
			ObjectOutputStream oos = new OSGIObjectOutputStream(clientSocket.getOutputStream());
			oos.writeLong(0);
			oos.flush();
			ObjectInputStream ois = new OSGIObjectInputStream(Activator.getContext().getBundle(),
					clientSocket.getInputStream());
			return new TCPSocketClientService(this, aRegistration, ois, oos);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void disconnect() {
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
