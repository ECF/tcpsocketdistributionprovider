/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.consumer;

import org.eclipse.ecf.provider.tcpsocket.examples.service.consumer.programmatically.SecondConsumer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	@Override
	public void start(BundleContext context) throws Exception {
		new SecondConsumer().start();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
	}

}
