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
import org.eclipse.ecf.provider.remoteservice.generic.*;

public class TCPSocketRequest extends Request {

	private static final long serialVersionUID = 9079072289091819810L;

	public TCPSocketRequest(ID requestContainerID, long serviceId, RemoteCallImpl call) {
		super(requestContainerID, serviceId, call);
	}

	@Override
	public boolean isDone() {
		return super.isDone();
	}

	@Override
	public Response getResponse() {
		return super.getResponse();
	}

	@Override
	public void setResponse(Response response) {
		super.setResponse(response);
	}

	@Override
	public void setDone(boolean done) {
		super.setDone(done);
	}
}
