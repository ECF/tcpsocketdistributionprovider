/*******************************************************************************
 * Copyright (c) 2022 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.client.internal;

import java.util.*;
import org.eclipse.ecf.provider.tcpsocket.client.TCPSocketClientConstants;
import org.eclipse.ecf.provider.tcpsocket.client.TCPSocketRequestCustomizer;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.osgi.framework.*;
import org.osgi.service.component.annotations.*;

@Component
public class TCPSocketClientComponent {

	private static TCPSocketClientComponent c;

	private final List<ServiceReference<TCPSocketRequestCustomizer>> refs;

	public TCPSocketClientComponent() {
		refs = new ArrayList<>();
		c = this;
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void bindTCPSocketRequestCustomizer(ServiceReference<TCPSocketRequestCustomizer> ref) {
		synchronized (refs) {
			refs.add(ref);
		}
	}

	void unbindTCPSocketRequestCustomizer(ServiceReference<TCPSocketRequestCustomizer> ref) {
		synchronized (refs) {
			refs.remove(ref);
		}
	}

	public static TCPSocketRequestCustomizer getCustomizer(String targetId) {
		return c.getCustomizer0(targetId);
	}

	TCPSocketRequestCustomizer getCustomizer0(String targetId) {
		List<ServiceReference<TCPSocketRequestCustomizer>> refsCopy = null;
		ServiceReference<TCPSocketRequestCustomizer> resultRef = null;
		synchronized (refs) {
			refsCopy = new ArrayList<>(refs);
		}
		for (ServiceReference<TCPSocketRequestCustomizer> ref : refsCopy) {
			// This looks for the TARGET_SERVER_ID_FILTER_PROPNAME on the
			// TCPSocketRequestCustomizer
			Object o = ref.getProperty(TCPSocketClientConstants.TARGET_SERVER_ID_FILTER_PROPNAME);
			// If it's set/not null
			if (o instanceof String) {
				String v = (String) o;
				String protocol = TCPSocketNamespace.INSTANCE.getScheme() + "://"; //$NON-NLS-1$
				if (!v.startsWith(protocol)) {
					v = protocol + v;
				}
				try {
					Dictionary<String, String> d = FrameworkUtil
							.asDictionary(Map.of(TCPSocketClientConstants.TARGET_SERVER_ID_FILTER_PROPNAME, targetId));
					// Use it to create a filter...i.e
					// "(ecf.socket.targetid=<target_id_filter_propvalue>)"
					if (Activator.getContext()
							.createFilter(
									"(" + TCPSocketClientConstants.TARGET_SERVER_ID_FILTER_PROPNAME + "=" + v + ")") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							// Then it looks for a match against the Dictionary d...which has name->value:
							// ecf.socket.targetid=<container connectedid>"
							.match(d)) {
						// If this matches then we choose higher priority
						resultRef = chooseHigherPriority(ref, resultRef);
					}
				} catch (InvalidSyntaxException e) {
					// should not happen, but if it does (bad ecf.socket.targetidfilter) then
					// we will report on System.err and ignore this service instance
					System.err.println(
							"Could not create filter from value of ecf.tcpsocket.targetserveridfilter property=" + o); //$NON-NLS-1$
					e.printStackTrace(System.err);
				}
				// If no property to filter on, then we just use it
			} else {
				resultRef = chooseHigherPriority(ref, resultRef);
			}
		}
		return (resultRef == null) ? null : Activator.getContext().getService(resultRef);
	}

	private ServiceReference<TCPSocketRequestCustomizer> chooseHigherPriority(
			ServiceReference<TCPSocketRequestCustomizer> first, ServiceReference<TCPSocketRequestCustomizer> second) {
		if (second != null) {
			// If there is more than one, then take the one with the
			// highest priority
			if (second.compareTo(first) > 0) {
				return second;
			}
		}
		return first;
	}

}
