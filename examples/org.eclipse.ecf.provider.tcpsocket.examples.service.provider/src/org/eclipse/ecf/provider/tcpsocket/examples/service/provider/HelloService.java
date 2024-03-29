/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.provider;

import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketConstants;
import org.eclipse.ecf.provider.tcpsocket.examples.service.api.IHello;
import org.osgi.service.component.annotations.Component;

@Component(immediate = true, property = { "my.id=myservice", "service.exported.interfaces=*",
		"service.exported.configs=" + TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE,
		TCPSocketConstants.SERVER_PROVIDER_CONFIG_TYPE+".port:Integer=5555"})
public class HelloService implements IHello {

	public HelloService() {
		System.err.println();
	}

	@Override
	public void sayHello() {
		System.err.println("Hello called from " + UserManager.getUser());
	}

}
