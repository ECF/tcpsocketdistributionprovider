/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.server;

import java.io.*;
import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.sharedobject.SharedObjectMsg;
import org.eclipse.ecf.core.status.SerializableStatus;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.comm.*;
import org.eclipse.ecf.provider.comm.tcp.*;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;
import org.eclipse.ecf.provider.remoteservice.generic.Response;
import org.eclipse.ecf.provider.tcpsocket.common.*;
import org.eclipse.ecf.provider.tcpsocket.server.internal.TCPSocketServerComponent;
import org.eclipse.ecf.remoteservice.*;
import org.eclipse.ecf.remoteservice.RSARemoteServiceContainerAdapter.RSARemoteServiceRegistration;
import org.eclipse.equinox.concurrent.future.IProgressRunnable;
import org.eclipse.equinox.concurrent.future.ThreadsExecutor;

public class TCPSocketServerContainer extends AbstractRSAContainer implements ISocketAcceptHandler {

	private ContainerServerSocket serverSocket;

	private Map<ID, TCPRemoteServiceContainerAdapter.TCPClient> clients = new HashMap<>();

	class ContainerServerSocket extends Server {
		public ContainerServerSocket(int port, int backlog, InetAddress bindAddress) throws IOException {
			super(null, port, backlog, bindAddress, TCPSocketServerContainer.this);
		}
	}

	TCPRemoteServiceContainerAdapter getTCPAdapter() {
		return (TCPRemoteServiceContainerAdapter) getAdapter(IRemoteServiceContainerAdapter.class);
	}

	@Override
	public void handleAccept(Socket s) throws Exception {
		s.setTcpNoDelay(true);
		final ObjectOutputStream outs = new ObjectOutputStream(s.getOutputStream());
		outs.flush();
		final ObjectInputStream ins = new ObjectInputStream(s.getInputStream());
		// Assume that first thing read from stream is ConnectRequestMessage
		ConnectRequestMessage req = (ConnectRequestMessage) ins.readObject();
		// Assume that data in CRM is client ID (cast to ID)
		final ID clientID = (ID) req.getData();
		// Create client
		TCPRemoteServiceContainerAdapter.TCPClient tcpClient = getTCPAdapter().new TCPClient(clientID, s, ins, outs);
		synchronized (clients) {
			clients.put(clientID, tcpClient);
			tcpClient.start();
		}
	}

	public TCPSocketServerContainer(URI uri, int backlog, InetAddress bindAddress) throws ContainerCreateException {
		super(TCPSocketNamespace.createServerID(uri));
		try {
			this.serverSocket = new ContainerServerSocket(uri.getPort(), backlog, bindAddress);
		} catch (IOException e) {
			throw new ContainerCreateException("Could not open container server socket for tcpsocket server", e);
		}
	}

	class TCPRemoteServiceContainerAdapter extends RSARemoteServiceContainerAdapter {

		boolean removeClient(ID clientID) {
			synchronized (clients) {
				return clients.remove(clientID) != null;
			}
		}

		void logRemoteCallException(String string, Throwable e) {
			// TODO Auto-generated method stub
			System.out.println(string);
			e.printStackTrace();
		}

		class TCPClient {

			private Client client;

			public TCPClient(final ID clientID, Socket s, ObjectInputStream ins, ObjectOutputStream outs)
					throws IOException {
				client = new Client(s, ins, outs, new ISynchAsynchEventHandler() {
					@Override
					public Object handleSynchEvent(SynchEvent event) throws IOException {
						return null;
					}

					@Override
					public ID getEventHandlerID() {
						return clientID;
					}

					@Override
					public void handleConnectEvent(ConnectionEvent event) {
						// nothing to do
					}

					@Override
					public void handleDisconnectEvent(DisconnectEvent event) {
						IConnection c = event.getConnection();
						if (c != null) {
							c.disconnect();
							removeClient(clientID);
						}
					}

					@Override
					@SuppressWarnings({ "unchecked", "rawtypes" })
					public void handleAsynchEvent(AsynchEvent event) throws IOException {
						try {
							SharedObjectMsg msg = (SharedObjectMsg) event.getData();
							String msgName = msg.getMethod();
							if (msgName.equals("invokeRequest")) {
								TCPSocketRequest request = (TCPSocketRequest) msg.getParameters()[0];
								RemoteServiceRegistrationImpl reg = findRegistration(request.getServiceId());
								if (reg == null) {
									throw new ECFException(
											"Could not find remote service id=" + request.getServiceId());
								}
								getExecutor().execute(new IProgressRunnable() {
									@Override
									public Object run(IProgressMonitor monitor) throws Exception {
										final RemoteCallImpl call = request.getCall();
										long requestId = request.getRequestId();
										Response response = null;
										try {
											// Get remote service call policy
											IRemoteServiceCallPolicy callPolicy = getRemoteServiceCallPolicy();
											// If it's set, then check remote call *before* actual invocation
											if (callPolicy != null)
												callPolicy.checkRemoteCall(request.getRequestContainerID(), reg, call);
											response = createTCPSocketServerRequestExecutor(reg).execute(request, reg);
										} catch (Exception | NoClassDefFoundError e) {
											response = new Response(requestId,
													new SerializableStatus(0,
															"org.eclipse.ecf.provider.tcpsocket.server", null, e)
															.getException());
											logRemoteCallException(
													"Exception invoking remote service for request=" + request, e); //$NON-NLS-1$
										}
										// Then send response message back to client
										client.sendAsynch(clientID,
												SharedObjectMsg.createMsg("invokeResponse", response));
										return null;
									}

								}, new NullProgressMonitor());
							} else {
								throw new ProtocolException("Unsupported msg=" + msgName);
							}
						} catch (Exception e) {
							throw new IOException(
									"Exception handling async event from clientID=" + client.getLocalID());
						}
					}
				});
				synchronized (client.getOutputStreamLock()) {
					// Create connect response wrapper and send it back
					outs.writeObject(new ConnectResultMessage(SharedObjectMsg.createMsg("connectResponse",
							((TCPRemoteServiceRegistryImpl) registry).getAllRegistrations())));
					outs.flush();
				}
			}

			void sendAddRegistration(RSARemoteServiceRegistration reg) {
				if (client != null && client.isConnected()) {
					try {
						client.sendAsynch(client.getLocalID(), SharedObjectMsg.createMsg("addRegistration",
								new TCPSocketRemoteServiceRegistration(reg)));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			void start() {
				if (client != null && !client.isStarted()) {
					client.start();
				}
			}

			void stop() {
				if (client != null && client.isConnected()) {
					client.disconnect();
				}
			}

			void sendRemoveRegistration(RSARemoteServiceRegistration reg) {
				if (client != null && client.isConnected()) {
					try {
						client.sendAsynch(client.getLocalID(), SharedObjectMsg.createMsg("removeRegistration",
								new TCPSocketRemoteServiceRegistration(reg)));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

		class TCPRemoteServiceRegistryImpl extends RemoteServiceRegistryImpl {

			private static final long serialVersionUID = -4893493364363506511L;

			public TCPRemoteServiceRegistryImpl(ID localContainerID) {
				super(localContainerID);
			}

			List<TCPSocketRemoteServiceRegistration> getAllRegistrations() {
				synchronized (this) {
					RemoteServiceRegistrationImpl[] impls = getRegistrations();
					List<TCPSocketRemoteServiceRegistration> result = new ArrayList<>();
					for (RemoteServiceRegistrationImpl impl : impls) {
						result.add(new TCPSocketRemoteServiceRegistration((RSARemoteServiceRegistration) impl));
					}
					return result;
				}
			}
		}

		public TCPRemoteServiceContainerAdapter(AbstractRSAContainer container) {
			super(container);
			setRegistry(new TCPRemoteServiceRegistryImpl(container.getID()));
			setExecutor(new ThreadsExecutor());
		}

		RemoteServiceRegistrationImpl findRegistration(long rsvcId) {
			for (IRemoteServiceReference ref : getRegistry().lookupServiceReferences()) {
				if (ref.getID().getContainerRelativeID() == rsvcId) {
					return getRemoteServiceRegistrationImpl(ref);
				}
			}
			return null;
		}
	}

	@Override
	protected RSARemoteServiceContainerAdapter createContainerAdapter() {
		return new TCPRemoteServiceContainerAdapter(this);
	}

	@Override
	protected Map<String, Object> exportRemoteService(RSARemoteServiceRegistration registration) {
		synchronized (clients) {
			clients.forEach((i, c) -> c.sendAddRegistration(registration));
		}
		return null;
	}

	@Override
	protected void unexportRemoteService(RSARemoteServiceRegistration registration) {
		synchronized (clients) {
			clients.forEach((i, c) -> c.sendRemoveRegistration(registration));
		}
	}

	TCPSockerServerRequestExecutor createTCPSocketServerRequestExecutor(RemoteServiceRegistrationImpl registration) {
		TCPSocketServerRequestExecutorCustomizer customizer = TCPSocketServerComponent
				.getCustomizer(registration.getContainerID().getName());
		if (customizer != null) {
			return customizer.createRequestExecutor();
		} else {
			return new TCPSockerServerRequestExecutor();
		}
	}

	@Override
	public void dispose() {
		if (this.serverSocket != null) {
			try {
				this.serverSocket.close();
			} catch (IOException e) {
			}
			this.serverSocket = null;
		}
		synchronized (clients) {
			clients.forEach((i, c) -> c.stop());
			clients.clear();
		}
		super.dispose();
	}

}
