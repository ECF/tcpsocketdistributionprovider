/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.consumer.programmatically;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ecf.provider.tcpsocket.examples.service.api.IHello;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

public class SecondConsumer {

	public SecondConsumer() {
	}

	private void callService() {
		// that would be done by a utility class - just for the example
		try (ServiceHandle<IHello> serviceHandle = new ServiceHandle<IHello>(getBundleContext(), IHello.class)) {
			IHello service = serviceHandle.getService();
			if (service == null) {
				System.err.println("no service available");
			} else {
				service.sayHello();
			}
		}
	}

	public void start() {
		Job job = new Job("demo") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					System.out.println("calling service from " + getClass().getName());
					callService();
				} finally {
					schedule(5000L);
				}
				return Status.OK_STATUS;
			}
		};
		// give the ServiceImporter some time tu run
		job.schedule(5000L);
	}

	private BundleContext getBundleContext() {
		return FrameworkUtil.getBundle(getClass()).getBundleContext();
	}

}
