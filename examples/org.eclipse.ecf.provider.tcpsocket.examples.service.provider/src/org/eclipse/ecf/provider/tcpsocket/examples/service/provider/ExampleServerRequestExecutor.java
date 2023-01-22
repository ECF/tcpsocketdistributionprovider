/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.provider;

import org.eclipse.ecf.provider.remoteservice.generic.Response;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketRequest;
import org.eclipse.ecf.provider.tcpsocket.examples.service.api.ExampleRequest;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSockerServerRequestExecutor;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerConstants;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerRequestExecutorCustomizer;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistrationImpl;
import org.osgi.service.component.annotations.Component;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;

@Component(property = { TCPSocketServerConstants.SERVER_ID_FILTER_PROPNAME + "=localhost*" })
public class ExampleServerRequestExecutor extends TCPSockerServerRequestExecutor
		implements TCPSocketServerRequestExecutorCustomizer {

	@Override
	public Response execute(TCPSocketRequest request, RemoteServiceRegistrationImpl reg) {
		if (request instanceof ExampleRequest) {
			ExampleRequest exampleRequest = (ExampleRequest) request;

			// with that one could configure SESSION_NAME variable in Logger configuration
			// to log the calling username
			try (MDCCloseable closeable = MDC.putCloseable("SESSION_NAME", exampleRequest.getUsername())) {
				UserManager.setUser(exampleRequest.getUsername());
				return super.execute(exampleRequest, reg);
			} finally {
				UserManager.setUser(null);
			}

		} else
			throw new IllegalStateException();
	}

}
