/*******************************************************************************
 * Copyright (c) 2022 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.common;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;

public interface TCPSocketRequestCustomizer {
	
	public static final String TARGET_ID_FILTER_PROPNAME = "ecf.socket.targetidfilter";
	public static final String TARGET_ID_PROPNAME = "ecf.socket.targetid";
	
	TCPSocketRequest createRequest(ID requestContainerID, long serviceId, RemoteCallImpl call);

}
