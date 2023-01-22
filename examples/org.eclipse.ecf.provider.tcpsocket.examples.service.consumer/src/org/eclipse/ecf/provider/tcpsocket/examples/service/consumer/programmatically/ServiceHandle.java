/*******************************************************************************
 * Copyright (c) 2022 GODYO Business Solutions AG and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Peter Hermsdorf, GODYO Business Solutions AG - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.provider.tcpsocket.examples.service.consumer.programmatically;

import java.io.Closeable;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ServiceHandle<T> implements Closeable {

		private final BundleContext context;
		private ServiceReference<T> serviceReference;
		private final Class<T> target;

		public ServiceHandle(final BundleContext context, final Class<T> target) {
			this.context = context;
			this.target = target;
		}

		public T getService() {
			serviceReference = context.getServiceReference(target);
			if(serviceReference==null) {
				return null;
			}
			return context.getService(serviceReference);
		}

		@Override
		public void close() {
			if(serviceReference!=null) {
				context.ungetService(serviceReference);
			}
		}
	}
