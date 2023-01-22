/*******************************************************************************
 * Copyright (c) 2022 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.provider.tcpsocket.server.internal;

import java.util.*;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketNamespace;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerConstants;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerRequestExecutorCustomizer;
import org.osgi.framework.*;
import org.osgi.service.component.annotations.*;

@Component(immediate = true)
public class TCPSocketServerComponent {

	private final List<ServiceReference<TCPSocketServerRequestExecutorCustomizer>> customizers;
	private static TCPSocketServerComponent comp;

	public TCPSocketServerComponent() {
		customizers = new ArrayList<>();
		comp = this;
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	void addTCPSocketServerRequestExecutorCustomizer(
			ServiceReference<TCPSocketServerRequestExecutorCustomizer> customizer) {
		synchronized (customizers) {
			customizers.add(customizer);
		}
	}

	void removeTCPSocketServerRequestExecutorCustomizer(
			ServiceReference<TCPSocketServerRequestExecutorCustomizer> customizer) {
		synchronized (customizers) {
			customizers.add(customizer);
		}
	}

	public static TCPSocketServerRequestExecutorCustomizer getCustomizer(String serverId) {
		return comp.getCustomizer0(serverId);
	}

	TCPSocketServerRequestExecutorCustomizer getCustomizer0(String serverId) {
		List<ServiceReference<TCPSocketServerRequestExecutorCustomizer>> refsCopy = null;
		ServiceReference<TCPSocketServerRequestExecutorCustomizer> resultRef = null;
		synchronized (customizers) {
			refsCopy = new ArrayList<>(customizers);
		}
		for (ServiceReference<TCPSocketServerRequestExecutorCustomizer> ref : refsCopy) {
			// This looks for the TARGET_ID_FILTER_PROPNAME on the
			// TCPSocketServerRequestExecutorCustomizer instance
			Object o = ref.getProperty(TCPSocketServerConstants.SERVER_ID_FILTER_PROPNAME);
			// If it's set/not null
			if (o instanceof String) {
				String v = (String) o;
				String protocol = TCPSocketNamespace.INSTANCE.getScheme() + "://"; //$NON-NLS-1$
				if (!v.startsWith(protocol)) {
					v = protocol + v;
				}
				try {
					Dictionary<String, String> d = FrameworkUtil
							.asDictionary(Map.of(TCPSocketServerConstants.SERVER_ID_FILTER_PROPNAME, serverId));
					// Use it to create a filter...i.e
					// "(ecf.tcpsocket.targetserverid=<server_id_filter_propvalue>)"
					if (Activator.getContext()
							.createFilter("(" + TCPSocketServerConstants.SERVER_ID_FILTER_PROPNAME + "=" + v + ")") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							// Then it looks for a match against the Dictionary d...which has name->value:
							// ecf.socket.serverid=<server container id>"
							.match(d)) {
						// If this matches then we choose higher priority between ref and resultRef
						resultRef = chooseHigherPriority(ref, resultRef);
					}
				} catch (InvalidSyntaxException e) {
					// should not happen, but if it does (bad ecf.tcpsocket.targetidfilter) then
					// we will report on System.err and ignore this service instance
					System.err.println("Could not create filter from value of ecf.socket.targetidfilter property=" + o); //$NON-NLS-1$
					e.printStackTrace(System.err);
				}
				// If no property to filter on, then we just use it
			} else {
				resultRef = chooseHigherPriority(ref, resultRef);
			}
		}
		return (resultRef == null) ? null : Activator.getContext().getService(resultRef);
	}

	private ServiceReference<TCPSocketServerRequestExecutorCustomizer> chooseHigherPriority(
			ServiceReference<TCPSocketServerRequestExecutorCustomizer> first,
			ServiceReference<TCPSocketServerRequestExecutorCustomizer> second) {
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
