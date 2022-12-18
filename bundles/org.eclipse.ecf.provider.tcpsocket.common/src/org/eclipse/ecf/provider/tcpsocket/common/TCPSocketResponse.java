/*******************************************************************************
 * Copyright (c) 2022 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.common;

import org.eclipse.ecf.provider.remoteservice.generic.Response;

public class TCPSocketResponse extends Response {

	private static final long serialVersionUID = -5506991908619641965L;

	public TCPSocketResponse(long requestId, Throwable exception) {
		super(requestId, exception);
	}

	public TCPSocketResponse(long requestId, Object response) {
		super(requestId, response);
	}

}
