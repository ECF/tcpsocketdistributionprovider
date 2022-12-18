/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.client.internal;

import java.util.*;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.identity.Namespace;
import org.eclipse.ecf.provider.tcpsocket.client.TCPSocketClientContainer;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketConstants;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.remoteservice.Constants;
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
		// register xmlrpc client provider
		context.registerService(IRemoteServiceDistributionProvider.class,
				new RemoteServiceDistributionProvider.Builder().setName(TCPSocketConstants.CLIENT_PROVIDER_CONFIG_TYPE)
						.setInstantiator(
								new RemoteServiceContainerInstantiator(TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE,
										TCPSocketConstants.CLIENT_PROVIDER_CONFIG_TYPE) {
									@Override
									public IContainer createInstance(ContainerTypeDescription description,
											Map<String, ?> parameters) {
										return new TCPSocketClientContainer();
									}

									@Override
									public String[] getSupportedIntents(ContainerTypeDescription description) {
										List<String> supportedIntents = new ArrayList<>(
												Arrays.asList(super.getSupportedIntents(description)));
										supportedIntents.add(Constants.OSGI_ASYNC_INTENT);
										return supportedIntents.toArray(new String[supportedIntents.size()]);
									}

									@Override
									public String[] getImportedConfigs(ContainerTypeDescription description,
											String[] exporterSupportedConfigs) {
										if (exporterSupportedConfigs == null)
											return null;
										if (Arrays.asList(exporterSupportedConfigs)
												.contains(TCPSocketConstants.CLIENT_PROVIDER_CONFIG_TYPE)
												|| Arrays.asList(exporterSupportedConfigs)
														.contains(TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE)) {
											return new String[] { TCPSocketConstants.CLIENT_PROVIDER_CONFIG_TYPE };
										}
										return null;
									}

								})
						.setServer(false).setHidden(false).build(),
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
