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

	String NAMESPACE_NAME = "ecf.namespace.socket"; //$NON-NLS-1$
	String SERVER_PROVIDER_CONFIG_TYPE = "ecf.socket.server"; //$NON-NLS-1$
	String CLIENT_PROVIDER_CONFIG_TYPE = "ecf.socket.client"; //$NON-NLS-1$
	String HOSTNAME_PROP = "hostname"; //$NON-NLS-1$
	String HOSTNAME_DEFAULT = "localhost"; //$NON-NLS-1$
	String PORT_PROP = "port"; //$NON-NLS-1$
	int PORT_PROP_DEFAULT = 3000;
	String BIND_ADDRESS_PROP = "bindAddress"; //$NON-NLS-1$
	String BACKLOG_PROP = "backlog"; //$NON-NLS-1$
	int BACKLOG_DEFAULT = 50;
}
