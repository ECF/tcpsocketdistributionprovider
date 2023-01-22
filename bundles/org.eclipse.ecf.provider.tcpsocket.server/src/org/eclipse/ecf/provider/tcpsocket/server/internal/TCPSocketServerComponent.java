package org.eclipse.ecf.provider.tcpsocket.server.internal;

import java.util.*;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerRequestExecutorCustomizer;
import org.osgi.framework.*;
import org.osgi.service.component.annotations.*;

@Component(immediate = true, service = TCPSocketServerComponent.class)
public class TCPSocketServerComponent {

	private static final String SERVER_ID_PROPNAME = "ecf.socket.serverid"; //$NON-NLS-1$
	private final List<ServiceReference<TCPSocketServerRequestExecutorCustomizer>> customizers;
	private static TCPSocketServerComponent comp;

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

	public TCPSocketServerComponent() {
		customizers = new ArrayList<>();
		comp = this;
	}

	void bindExecutorCustomizer(ServiceReference<TCPSocketServerRequestExecutorCustomizer> c) {
		synchronized (customizers) {
			customizers.add(c);
		}
	}

	void unbindExecutorCustomizer(ServiceReference<TCPSocketServerRequestExecutorCustomizer> c) {
		synchronized (customizers) {
			customizers.remove(c);
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
			// This looks for the TARGET_ID_FILTER_PROP on the TCPSocketRequestCustomizer
			Object o = ref.getProperty(TCPSocketServerRequestExecutorCustomizer.SERVER_ID_FILTER_PROPNAME);
			// If it's set/not null
			if (o instanceof String) {
				try {
					Dictionary<String, String> d = FrameworkUtil.asDictionary(Map.of(SERVER_ID_PROPNAME, serverId));

					// Use it to create a filter...i.e
					// "(ecf.socket.serverid=<server_id_filter_propvalue>)"
					if (Activator.getContext().createFilter("(" + SERVER_ID_PROPNAME + "=" + ((String) o) + ")") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							// Then it looks for a match against the Dictionary d...which has name->value:
							// ecf.socket.serverid=<server container id>"
							.match(d)) {
						// If this matches then we choose higher priority between ref and resultRef
						resultRef = chooseHigherPriority(ref, resultRef);
					}
				} catch (InvalidSyntaxException e) {
					// should not happen, but if it does (bad ecf.socket.targetidfilter) then
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
