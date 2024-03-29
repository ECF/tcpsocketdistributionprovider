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
import java.net.ConnectException;
import java.net.ProtocolException;
import java.util.*;
import java.util.concurrent.Callable;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.events.ContainerConnectedEvent;
import org.eclipse.ecf.core.events.ContainerConnectingEvent;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.ecf.core.sharedobject.SharedObjectMsg;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.provider.comm.*;
import org.eclipse.ecf.provider.comm.tcp.Client;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;
import org.eclipse.ecf.provider.remoteservice.generic.Response;
import org.eclipse.ecf.provider.tcpsocket.client.internal.TCPSocketClientComponent;
import org.eclipse.ecf.provider.tcpsocket.common.*;
import org.eclipse.ecf.remoteservice.*;
import org.eclipse.ecf.remoteservice.client.*;
import org.eclipse.ecf.remoteservice.events.IRemoteCallCompleteEvent;
import org.eclipse.ecf.remoteservice.events.IRemoteServiceUnregisteredEvent;
import org.eclipse.equinox.concurrent.future.TimeoutException;
import org.osgi.framework.ServiceException;

public class TCPSocketClientContainer extends AbstractRSAClientContainer {

	private final List<RemoteServiceClientRegistration> registrations = new ArrayList<>();
	private final List<TCPSocketRequest> requests = new ArrayList<>();

	ISynchAsynchEventHandler handler = new ISynchAsynchEventHandler() {
		/**
		 * @throws IOException
		 */
		@Override
		public Object handleSynchEvent(SynchEvent event) throws IOException {
			return null;
		}

		@Override
		public ID getEventHandlerID() {
			return getID();
		}

		@SuppressWarnings("synthetic-access")
		@Override
		public void handleDisconnectEvent(DisconnectEvent event) {
			synchronized (connectLock) {
				disconnect();
			}
		}

		@Override
		public void handleConnectEvent(ConnectionEvent event) {
			// nothing to do
		}

		/**
		 * @throws IOException
		 */
		@SuppressWarnings("synthetic-access")
		@Override
		public void handleAsynchEvent(AsynchEvent event) throws IOException {
			try {
				SharedObjectMsg msg = (SharedObjectMsg) event.getData();
				String msgName = msg.getMethod();
				// First type of message/msgName handled by tcp socket client is
				// 'invokeResponse'
				// This is received in response to a remote 'invoke method' request
				if ("invokeResponse".equals(msgName)) { //$NON-NLS-1$
					Response response = (Response) msg.getParameters()[0];
					long requestId = response.getRequestId();
					TCPSocketRequest request = null;
					synchronized (requests) {
						request = requests.stream().filter(r -> r.getRequestId() == requestId).findAny().orElse(null);
					}
					if (request != null) {
						requests.remove(request);
						synchronized (request) {
							request.setResponse(response);
							request.setDone(true);
							request.notify();
						}
					}
					// Second is 'addRegistration' in case server dynamically adds/register a
					// another remote service registration
				} else if ("addRegistration".equals(msgName)) { //$NON-NLS-1$
					TCPSocketRemoteServiceRegistration reg = (TCPSocketRemoteServiceRegistration) msg
							.getParameters()[0];
					if (reg != null) {
						registerRemote(TCPSocketClientContainer.this.connectedID, reg.interfaces, reg.properties);
					}
					// Finally is 'removeRegistration' if server dynamically unregisters a remote
					// service
				} else if ("removeRegistration".equals(msgName)) { //$NON-NLS-1$
					TCPSocketRemoteServiceRegistration reg = (TCPSocketRemoteServiceRegistration) msg
							.getParameters()[0];
					if (reg != null) {
						RSAClientRegistration clientReg = findRemoteServiceRegistration(reg.rsvcId);
						if (clientReg != null) {
							unregisterEndpoint(clientReg);
						}
					}
				}
			} catch (Exception e) {
				// Log here
				e.printStackTrace();
				disconnect();
			}
		}
	};

	// This method should be added to superclass also
	RSAClientRegistration findRemoteServiceRegistration(long rsvcId) {
		IRemoteServiceReference ref = registry
				.findServiceReference(new RemoteServiceID(getConnectNamespace(), getConnectedID(), rsvcId));
		return ref == null ? null
				: (RSAClientRegistration) registry.findServiceRegistration((RemoteServiceClientReference) ref);
	}

	private Client client;

	public TCPSocketClientContainer() {
		super(TCPSocketNamespace.createClientID());
	}

	@Override
	public RemoteServiceClientRegistration registerEndpoint(ID targetID, String[] interfaces,
			Map<String, Object> endpointDescriptionProperties) {
		return null;
	}

	void registerRemote(ID targetID, String[] interfaces, Map<String, Object> endpointDescriptionProperties) {
		synchronized (registrations) {
			registrations.add(super.registerEndpoint(targetID, interfaces, endpointDescriptionProperties));
		}
	}

	void unregisterRemotes() {
		synchronized (registrations) {
			for (Iterator<RemoteServiceClientRegistration> r = registrations.iterator(); r.hasNext();) {
				unregisterEndpoint((RSAClientRegistration) r.next());
				r.remove();
			}
		}
	}

	// This should method be added to super class
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void unregisterEndpoint(final RSAClientRegistration r) {
		r.unregister();
		List toNotify = null;
		// Copy array
		synchronized (remoteServiceListeners) {
			toNotify = new ArrayList(remoteServiceListeners);
		}
		for (Iterator i = toNotify.iterator(); i.hasNext();) {
			((IRemoteServiceListener) i.next()).handleServiceEvent(new IRemoteServiceUnregisteredEvent() {
				@Override
				public IRemoteServiceReference getReference() {
					return r.getReference();
				}

				@Override
				public ID getLocalContainerID() {
					return getID();
				}

				@Override
				public ID getContainerID() {
					return r.getContainerID();
				}

				@Override
				public String[] getClazzes() {
					return r.getClazzes();
				}
			});
		}

	}

	@Override
	public void connect(ID targetID, IConnectContext connectContext1) throws ContainerConnectException {
		synchronized (connectLock) {
			if (connectedID == null) {
				fireContainerEvent(new ContainerConnectingEvent(containerID, targetID));
				try {
					int timeout = 30000;
					this.client = new Client(handler, timeout);
					this.connectContext = connectContext1;
					Object result = this.client.connect(targetID, getID(), timeout);
					this.connectedID = targetID;
					if (result instanceof SharedObjectMsg) {
						SharedObjectMsg msg = (SharedObjectMsg) result;
						String methodName = msg.getMethod();
						if (methodName.equals("connectResponse")) { //$NON-NLS-1$
							@SuppressWarnings("unchecked")
							List<TCPSocketRemoteServiceRegistration> serviceRegistrations = (List<TCPSocketRemoteServiceRegistration>) msg
									.getParameters()[0];
							for (TCPSocketRemoteServiceRegistration r : serviceRegistrations) {
								registerRemote(connectedID, r.interfaces, r.properties);
							}
						}
					} else if (result instanceof Exception) {
						throw (Exception) result;
					} else
						throw new ProtocolException("Bad server connect response"); //$NON-NLS-1$
					this.client.start();
				} catch (Exception e) {
					client.disconnect();
					this.connectedID = null;
					this.client = null;
					throw new ContainerConnectException("Cannot connect to targetID=" + targetID.getName(), e); //$NON-NLS-1$
				}
				fireContainerEvent(new ContainerConnectedEvent(containerID, targetID));
			} else if (!connectedID.equals(targetID)) {
				throw new ContainerConnectException(
						"Container already connected to targetID=" + this.connectedID.getName()); //$NON-NLS-1$
			}
		}
	}

	@Override
	protected IRemoteService createRemoteService(final RemoteServiceClientRegistration aRegistration) {
		return new AbstractRSAClientService(TCPSocketClientContainer.this, aRegistration) {
			@Override
			protected Callable<Object> getSyncCallable(final RSARemoteCall call) {
				return () -> {
					return invokeRemote(call.getReflectMethod().getName(), call.getParameters(), call.getTimeout());
				};
			}

			@Override
			protected Callable<IRemoteCallCompleteEvent> getAsyncCallable(final RSARemoteCall call) {
				return () -> {
					Object result = invokeRemote(call.getMethod(), call.getParameters(), call.getTimeout());
					if (result instanceof Throwable)
						return createRCCEFailure((Throwable) result);
					return createRCCESuccess(result);
				};
			}

			Object invokeRemote(String methodName, Object[] args, long timeout) throws Exception {
				if (client == null || !client.isConnected()) {
					return new ConnectException("Remote service not connected"); //$NON-NLS-1$
				}
				RemoteServiceClientRegistration reg = getRegistration();
				TCPSocketRequest r = createRequest(reg,
						RemoteCallImpl.createRemoteCall(null, methodName, args, timeout));
				try {
					requests.add(r);
					client.sendAsynch(reg.getContainerID(), SharedObjectMsg.createMsg("invokeRequest", r)); //$NON-NLS-1$
				} catch (IOException e) {
					requests.remove(r);
					throw e;
				}
				long requestId = r.getRequestId();
				// Then get the specified timeout and calculate when we should
				// timeout in real time
				final long waitTimeout = timeout + System.currentTimeMillis();
				boolean doneWaiting = false;
				Response response = null;
				// Now loop until timeout time has elapsed
				while ((waitTimeout - System.currentTimeMillis()) > 0 && !doneWaiting) {
					synchronized (r) {
						if (r.isDone()) {
							doneWaiting = true;
							response = r.getResponse();
							if (response == null)
								throw new ECFException("Invalid response for remote service requestId=" + requestId); //$NON-NLS-1$
						} else {
							r.wait(500);
						}
					}
				}
				if (!doneWaiting)
					throw new ServiceException("Request timed out after " + Long.toString(timeout) + "ms", //$NON-NLS-1$ //$NON-NLS-2$
							ServiceException.REMOTE, new TimeoutException(timeout));
				if (response.hadException())
					throw new ECFException("Exception in remote call", response.getException()); //$NON-NLS-1$
				// Success...now get values and return
				return response.getResponse();
			}
		};
	}

	protected TCPSocketRequest createRequest(RemoteServiceClientRegistration registration, RemoteCallImpl call) {
		// Then get/find appropriate customizer by asking the TCPSocketClientComponent
		TCPSocketRequestCustomizer customizer = TCPSocketClientComponent
				.getCustomizer(registration.getID().getContainerID().getName());
		if (customizer != null) {
			return customizer.createRequest(registration.getContainerID(),
					registration.getID().getContainerRelativeID(), call);
		}
		return new TCPSocketRequest(registration.getContainerID(), registration.getID().getContainerRelativeID(), call);
	}

	@Override
	public void disconnect() {
		synchronized (connectLock) {
			if (this.client != null) {
				super.disconnect();
				this.client.disconnect();
				this.client = null;
				requests.clear();
				this.connectedID = null;
				unregisterRemotes();
			}
		}
	}
}
