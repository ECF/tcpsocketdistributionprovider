/*******************************************************************************
 * Copyright (c) 2018 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.common;

public interface TCPSocketConstants {

	String NAMESPACE_NAME = "ecf.namespace.socket";
	String SERVER_PROVIDER_CONFIG_TYPE = "ecf.socket.server";
	String CLIENT_PROVIDER_CONFIG_TYPE = "ecf.socket.client";
	String HOSTNAME_PROP = "hostname";
	String HOSTNAME_DEFAULT = "localhost";
	String PORT_PROP = "port";
	int PORT_PROP_DEFAULT = 3000;
	String BIND_ADDRESS_PROP = "bindAddress";
	String BACKLOG_PROP = "backlog";
	int BACKLOG_DEFAULT = 50;
}
