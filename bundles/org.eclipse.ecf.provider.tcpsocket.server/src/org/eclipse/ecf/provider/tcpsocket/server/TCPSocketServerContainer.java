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
import java.util.Collections;
import java.util.Dictionary;
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
import org.eclipse.ecf.remoteservice.IRemoteServiceReference;
import org.eclipse.ecf.remoteservice.RSARemoteServiceContainerAdapter;
import org.eclipse.ecf.remoteservice.RSARemoteServiceContainerAdapter.RSARemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistrationImpl;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistryImpl;
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
					containerClients.add(new Client(this.accept()));
				} catch (IOException e) {
					listening = false;
				}
			}
		}

		public void close() throws IOException {
			listening = false;
			if (future != null) {
				future.cancel(true);
			}
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

	class Client implements Runnable {

		private Socket s;
		private boolean connected;
		private ObjectInputStream ois;
		private ObjectOutputStream oos;

		public Client(Socket s) {
			this.s = s;
			executor.submit(this);
		}

		void close() {
			connected = false;
			containerClients.remove(Client.this);
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
				Object result = null;
				try {
					// Read long rsvcid
					long rsvcid = ois.readLong();
					org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerContainer.TCPRemoteServiceContainerAdapter.TCPRemoteServiceRegistration reg = TCPSocketServerContainer.this.containerAdapter.findRegistration(rsvcid);
					// We should have a registration by here, but if not then return error result
					if (reg == null)
						result = new IOException("Service object with ecf.rsvc.id=" + rsvcid + " not found");
					if (result == null) {
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
					}
					// Write result object or Throwable
					oos.writeObject(result);
					oos.flush();
				} catch (ClassNotFoundException | IOException e) {
					close();
				}
			}
		}
	}

	class TCPRemoteServiceContainerAdapter extends RSARemoteServiceContainerAdapter {

		public TCPRemoteServiceContainerAdapter(AbstractRSAContainer container) {
			super(container);
		}

		TCPRemoteServiceRegistration findRegistration(long rsId) {
			IRemoteServiceReference ref = getRemoteServiceReference(getRemoteServiceID(getLocalContainerID(), rsId));
			return (ref != null)?(TCPRemoteServiceRegistration) getRemoteServiceRegistrationImpl(ref):null;
		}
		
		@Override
		protected RemoteServiceRegistrationImpl createRegistration() {
			return new TCPRemoteServiceRegistration();
		}
		
		class TCPRemoteServiceRegistration extends RSARemoteServiceRegistration {
			private static final long serialVersionUID = -8413641702035404602L;
			
			@SuppressWarnings("unchecked")
			Dictionary<String,?> getProperties() {
				return properties;
			}
			Long convertPropValueToLong(Dictionary<?, ?> props) {
				Object val = props.get("ecf.socket.server.remoteserviceid");
				if (val == null) return null;
				if (val instanceof Long) return (Long) val;
				if (val instanceof String) {
					try {
						return Long.valueOf((String) val);
					} catch (Exception e) {}
				}
				return null;
			}
			
			@Override
			public void publish(RemoteServiceRegistryImpl registry, Object svc, String[] clzzes, @SuppressWarnings("rawtypes") Dictionary props) {
				synchronized (registry) {
					super.publish(registry, svc, clzzes, props);
					Long rsId = convertPropValueToLong(props);
					if (rsId != null && rsId > 0) {
						registry.unpublishService(this);
						this.remoteServiceID = registry.createRemoteServiceID(rsId);
						this.properties = createProperties(props);
						registry.publishService(this);
					}
				}
			}
		}
	}
	
	protected TCPRemoteServiceContainerAdapter containerAdapter;
	
	protected RSARemoteServiceContainerAdapter createContainerAdapter() {
		this.containerAdapter = new TCPRemoteServiceContainerAdapter(this);
		return this.containerAdapter;
	}

	private List<Client> containerClients = Collections.synchronizedList(new ArrayList<Client>());

	@Override
	protected Map<String, Object> exportRemoteService(RSARemoteServiceRegistration registration) {
		return null;
	}

	@Override
	protected void unexportRemoteService(RSARemoteServiceRegistration registration) {
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
