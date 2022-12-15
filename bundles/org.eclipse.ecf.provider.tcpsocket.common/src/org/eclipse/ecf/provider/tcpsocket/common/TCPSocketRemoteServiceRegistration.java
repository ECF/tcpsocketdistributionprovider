/*******************************************************************************
 * Copyright (c) 2022 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.ecf.remoteservice.RSARemoteServiceContainerAdapter.RSARemoteServiceRegistration;

public class TCPSocketRemoteServiceRegistration implements Serializable {

	private static final long serialVersionUID = -5024582300713451586L;
	public final long rsvcId;
	public final String[] interfaces;
	public final Map<String, Object> properties;

	public TCPSocketRemoteServiceRegistration(RSARemoteServiceRegistration reg) {
		this.rsvcId = reg.getServiceId();
		this.interfaces = reg.getInterfaces();
		String[] keys = reg.getPropertyKeys();
		this.properties = new HashMap<>(keys.length);
		for (String k : keys) {
			this.properties.put(k, reg.getProperty(k));
		}
	}
}
