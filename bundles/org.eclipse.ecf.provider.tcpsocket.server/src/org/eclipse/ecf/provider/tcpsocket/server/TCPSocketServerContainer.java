/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.util.OSGIObjectInputStream;
import org.eclipse.ecf.core.util.OSGIObjectOutputStream;
import org.eclipse.ecf.core.util.reflection.ClassUtil;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.remoteservice.AbstractRSAContainer;
import org.eclipse.ecf.remoteservice.Constants;
import org.eclipse.ecf.remoteservice.IRemoteCall;
import org.eclipse.ecf.remoteservice.RSARemoteServiceContainerAdapter.RSARemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.asyncproxy.AsyncReturnUtil;
import org.eclipse.ecf.remoteservice.util.AsyncUtil;

public class TCPSocketServerContainer extends AbstractRSAContainer {

	private ExecutorService executor = Executors.newCachedThreadPool();

	class ContainerServerSocket extends ServerSocket implements Runnable, Closeable {

		private boolean listening;
		private Future<?> future;
		
		public ContainerServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
			super(port);
			listening = true;
			future = executor.submit(this);
		}

		@Override
		public void run() {
			while (listening) {
				try {
					containerClients.add(new ContainerClient(this.accept()));
				} catch (IOException e) {
					listening = false;
				}
			}
		}

		public void close() throws IOException {
			listening = false;
			future.cancel(true);
			super.close();
		}
	}

	private ContainerServerSocket serverSocket;

	public TCPSocketServerContainer(URI uri, int backlog, InetAddress bindAddress) throws ContainerCreateException {
		super(TCPSocketNamespace.createServerID(uri));
		try {
			this.serverSocket = new ContainerServerSocket(uri.getPort(), backlog, bindAddress);
		} catch (IOException e) {
			throw new ContainerCreateException("Could not open container server socket for tcpsocket server", e);
		}
	}

	class ContainerClient implements Runnable {

		private Socket s;
		private boolean connected;
		private ObjectInputStream ois;
		private ObjectOutputStream oos;

		public ContainerClient(Socket s) {
			this.s = s;
			executor.submit(this);
		}

		void close() {
			connected = false;
			containerClients.remove(ContainerClient.this);
			try {
				s.close();
			} catch (IOException e1) {
			}
		}

		long getTimeoutForRegistration(RSARemoteServiceRegistration reg) {
			long timeout = IRemoteCall.DEFAULT_TIMEOUT;
			Object o = reg.getReference().getProperty(Constants.OSGI_BASIC_TIMEOUT_INTENT);
			if (o != null) {
				if (o instanceof Number)
					timeout = ((Number) o).longValue();
				else if (o instanceof String)
					timeout = Long.valueOf((String) o);
			}
			return timeout;
		}

		@Override
		public void run() {
			try {
				ois = new OSGIObjectInputStream(Activator.getContext().getBundle(), s.getInputStream());
				// first thing should be 'connect' code == 0
				long connect = ois.readLong();
				if (connect != 0)
					throw new IOException("Invalid connect code");
				oos = new OSGIObjectOutputStream(s.getOutputStream());
			} catch (IOException e) {
				close();
				return;
			}
			connected = true;
			while (connected) {
				try {
					// Read long rsvcid
					long rsvcid = ois.readLong();
					// If it's < 0 then that's a disconnect
					if (rsvcid < 0) {
						// disconnect
						close();
						return;
					}
					Object result = null;
					// Otherwise it should be a rsvcid, so use to look up registered
					// services
					RSARemoteServiceRegistration reg = registrations.get(rsvcid);
					// If not found we throw, disconnect
					if (reg == null)
						result = new IOException("Service object id=" + rsvcid + " not found");
					else {
						// Read method name
						String methodName = (String) ois.readObject();
						// Read array of object for arguments to remote call
						Object[] args = (Object[]) ois.readObject();
						// Get service from registration
						Object service = reg.getService();
						try {
							// Find appropriate method
							final Method method = ClassUtil.getMethod(service.getClass(), methodName,
									RSARemoteServiceRegistration.getTypesForParameters(args));
							// Synchronously invoke method on service object
							result = method.invoke(service, args);
							if (result != null) {
								// get return type
								Class<?> returnType = method.getReturnType();
								// provider must expose osgi.async property and must be async return type
								if (AsyncUtil.isOSGIAsync(reg.getReference())
										&& AsyncReturnUtil.isAsyncType(returnType))
									result = AsyncReturnUtil.convertAsyncToReturn(result, returnType,
											getTimeoutForRegistration(reg));
							}
						} catch (Throwable e) {
							if (e instanceof InvocationTargetException)
								e = ((InvocationTargetException) e).getTargetException();
							result = e;
						}
						// Write result object or Throwable
						oos.writeObject(result);
						oos.flush();
					}
				} catch (ClassNotFoundException | IOException e) {
					close();
				}
			}
		}
	}

	private List<ContainerClient> containerClients = Collections.synchronizedList(new ArrayList<ContainerClient>());

	private Map<Long, RSARemoteServiceRegistration> registrations = Collections
			.synchronizedMap(new HashMap<Long, RSARemoteServiceRegistration>());

	@Override
	protected Map<String, Object> exportRemoteService(RSARemoteServiceRegistration registration) {
		registrations.put(registration.getServiceId(), registration);
		return null;
	}

	@Override
	protected void unexportRemoteService(RSARemoteServiceRegistration registration) {
		registrations.remove(registration.getServiceId());
	}

	@Override
	public void dispose() {
		if (this.serverSocket != null) {
			try {
				this.serverSocket.close();
			} catch (IOException e) {}
			this.serverSocket = null;
		}
		if (this.executor != null) {
			this.executor.shutdown();
			try {
				this.executor.awaitTermination(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {}
			this.executor.shutdownNow();
			this.executor = null;
		}
		super.dispose();
	}
}
