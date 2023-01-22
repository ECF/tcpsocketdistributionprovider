/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.consumer;

import static org.eclipse.ecf.remoteservice.Constants.OBJECTCLASS;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_PACKAGE_VERSION_;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_CONFIGS_SUPPORTED;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_INTENTS_SUPPORTED;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED;
import static org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED_CONFIGS;
import static org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent.IMPORT_ERROR;
import static org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent.IMPORT_UNREGISTRATION;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.osgi.services.remoteserviceadmin.EndpointDescription;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketConstants;
import org.eclipse.ecf.remoteservice.Constants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.remoteserviceadmin.ImportRegistration;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdmin;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminEvent;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;

import org.eclipse.ecf.provider.tcpsocket.examples.service.api.IHello;

@Component(immediate = true)
public class ServiceImporter {

	@Reference(policy = ReferencePolicy.DYNAMIC)
	volatile private RemoteServiceAdmin remoteServiceAdmin;

	@Reference(policy = ReferencePolicy.DYNAMIC)
	volatile private IContainerManager containerManager;

	private final Set<ImportRegistration> importedServices = Collections.synchronizedSet(new HashSet<>());
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private static final Duration RECONNECT_DELAY = Duration.ofSeconds(10);
	private RemoteServiceAdminListener adminListener;

	private ServiceRegistration<RemoteServiceAdminListener> serviceRegistration;

	@Activate
	void activate() {
		adminListener = new RemoteServiceAdminListener() {
			@Override
			public void remoteAdminEvent(RemoteServiceAdminEvent e) {
				final org.eclipse.ecf.osgi.services.remoteserviceadmin.RemoteServiceAdmin.RemoteServiceAdminEvent event = (org.eclipse.ecf.osgi.services.remoteserviceadmin.RemoteServiceAdmin.RemoteServiceAdminEvent) e;
				// If this is an import failure event then schedule retry in 10 seconds
				if (isImportFailedEvent(event.getType())) {
					executor.schedule(() -> importService(event.getEndpointDescription()), RECONNECT_DELAY.getSeconds(),
							TimeUnit.SECONDS);
				}
			}
		};
		serviceRegistration = getBundleContext().registerService(RemoteServiceAdminListener.class, adminListener, null);

		final EndpointDescription endpointDescription = getEndpointDescription(IHello.class);
		importService(endpointDescription);
	}

	private BundleContext getBundleContext() {
		return FrameworkUtil.getBundle(getClass()).getBundleContext();
	}

	@Deactivate
	void deactivate() {
		executor.shutdownNow();
		serviceRegistration.unregister();
		synchronized (importedServices) {
			importedServices.forEach(ImportRegistration::close);
		}
	}

	private void importService(final EndpointDescription endpointDescription) {
		final ImportRegistration registration = remoteServiceAdmin.importService(endpointDescription);
		importedServices.add(registration);
	}

	private EndpointDescription getEndpointDescription(Class<?> serviceInterface) {

		final Map<String, Object> props = new HashMap<>();
		props.put(ENDPOINT_ID, "tcp://localhost:3000");
		props.put(org.eclipse.ecf.osgi.services.remoteserviceadmin.RemoteConstants.ENDPOINT_CONTAINER_ID_NAMESPACE,
				TCPSocketConstants.NAMESPACE_NAME);
//		props.put(RemoteConstants.ENDPOINT_CONNECTTARGET_ID, "tcp://" + host + ":"+TCPSocketConstants.PORT_PROP_DEFAULT /*+"/server"*/);
		props.put(ENDPOINT_PACKAGE_VERSION_ + serviceInterface.getPackage().getName(), "1.0.0");
		props.put("objectClass", new String[] { serviceInterface.getName() });
		props.put(REMOTE_CONFIGS_SUPPORTED, new String[] { TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE });
		props.put(REMOTE_INTENTS_SUPPORTED, new String[] { "passByValue", "exactlyOnce", "ordered" });
		props.put(SERVICE_IMPORTED, "true");
		props.put(SERVICE_IMPORTED_CONFIGS, new String[] { TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE });

		props.put(Constants.SERVICE_ID, 0L);
		props.put(Constants.ENDPOINT_REMOTESERVICE_FILTER,
				"(&(" + OBJECTCLASS + "=" + serviceInterface.getName() + "))");
//		props.put(Constants.ENDPOINT_REMOTESERVICE_FILTER, "(&(my.id=myservice))");

//		props.put(Constants.ENDPOINT_REMOTESERVICE_FILTER, "(&(objectclass=" +serviceInterface.getName()+"))");
//		props.put("service.interface", "service.IHello");

//		props.put("my.id", 5);
//		props.put(Constants.ENDPOINT_REMOTESERVICE_FILTER, "(&(my.id=5))");

		// props.put(Constants.ENDPOINT_REMOTESERVICE_FILTER,
		// "(&("+Constants.SERVICE_ID+"=*))");

		return new EndpointDescription(props);
	}

	private boolean isImportFailedEvent(int type) {
		return type == IMPORT_ERROR || type == IMPORT_UNREGISTRATION;
	}

}
