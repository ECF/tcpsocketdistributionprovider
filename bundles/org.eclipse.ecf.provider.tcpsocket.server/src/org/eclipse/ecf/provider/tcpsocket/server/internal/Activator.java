/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.server.internal;

import java.net.*;
import java.util.Map;
import org.eclipse.ecf.core.*;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketConstants;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerContainer;
import org.eclipse.ecf.remoteservice.provider.*;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	private static BundleContext context;

	public static BundleContext getContext() {
		return context;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		// Register TCPSocketNamespace
		Activator.context.registerService(Namespace.class, new TCPSocketNamespace(), null);
		context.registerService(IRemoteServiceDistributionProvider.class,
				new RemoteServiceDistributionProvider.Builder().setName(TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE)
						.setInstantiator(
								new RemoteServiceContainerInstantiator(TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE,
										TCPSocketConstants.CLIENT_PROVIDER_CONFIG_TYPE) {
									@Override
									public IContainer createInstance(ContainerTypeDescription description,
											Map<String, ?> parameters) throws ContainerCreateException {
										int port = getParameterValue(parameters, TCPSocketConstants.PORT_PROP,
												Integer.class, TCPSocketConstants.PORT_PROP_DEFAULT);
										String hostname = getParameterValue(parameters,
												TCPSocketConstants.HOSTNAME_PROP, TCPSocketConstants.HOSTNAME_DEFAULT);
										URI uri = null;
										try {
											uri = new URI("tcp://" + hostname + ":" + String.valueOf(port)); //$NON-NLS-1$ //$NON-NLS-2$
										} catch (URISyntaxException e) {
											throw new ContainerCreateException("Could not create uri.  hostname=" //$NON-NLS-1$
													+ hostname + ",port=" + String.valueOf(port), e); //$NON-NLS-1$
										}
										checkOSGIIntents(description, uri, parameters);
										String bindAddressStr = getParameterValue(parameters,
												TCPSocketConstants.BIND_ADDRESS_PROP, null);
										InetAddress bindAddress = null;
										if (bindAddressStr != null)
											try {
												bindAddress = InetAddress.getByName(bindAddressStr);
											} catch (UnknownHostException e) {
												throw new ContainerCreateException(
														"Could not create bindAddress from bindAddressStr=" //$NON-NLS-1$
																+ bindAddressStr,
														e);
											}
										int backlog = getParameterValue(parameters, TCPSocketConstants.BACKLOG_PROP,
												Integer.class, TCPSocketConstants.BACKLOG_DEFAULT);
										return new TCPSocketServerContainer(uri, backlog, bindAddress);
									}

									@Override
									protected boolean supportsOSGIPrivateIntent(ContainerTypeDescription description) {
										return true;
									}

									@Override
									protected boolean supportsOSGIAsyncIntent(ContainerTypeDescription description) {
										return true;
									}
								})
						.setServer(true).setHidden(false).build(),
				null);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		Activator.context = null;
	}

}
