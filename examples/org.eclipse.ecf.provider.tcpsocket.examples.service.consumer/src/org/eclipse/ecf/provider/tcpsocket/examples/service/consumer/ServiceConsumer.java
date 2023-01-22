/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.consumer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.ecf.provider.tcpsocket.examples.service.api.IHello;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component
public class ServiceConsumer {

	@Reference(policy = ReferencePolicy.DYNAMIC)
	private volatile IHello hello;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	@Activate
	void onActivate() {
		System.err.println("Started Consumer");
		hello.sayHello();
		executor.scheduleWithFixedDelay(() -> hello.sayHello(), 0, 15, TimeUnit.SECONDS);
	}

	@Deactivate
	void onDeactivate() {
		executor.shutdown();
		System.err.println("Stopped Consumer");
	}

}
