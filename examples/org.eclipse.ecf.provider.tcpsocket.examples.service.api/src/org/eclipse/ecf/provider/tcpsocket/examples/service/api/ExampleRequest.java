/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.api;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketRequest;

public class ExampleRequest extends TCPSocketRequest{

	private static final long serialVersionUID = 1L;
	private String username;

	public ExampleRequest(ID requestContainerID, long serviceId, RemoteCallImpl call, String username) {
		super(requestContainerID, serviceId, call);
		this.username = username;
	}
	
	public String getUsername() {
		return username;
	}

}
