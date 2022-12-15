package org.eclipse.ecf.provider.tcpsocket.server.internal;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;

import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerRequestExecutorCustomizer;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component(immediate=true)
public class TCPSocketServerComponent {

	private static final String SERVER_ID_PROPNAME = "ecf.socket.serverid";
	private List<ServiceReference<TCPSocketServerRequestExecutorCustomizer>> customizers = new ArrayList<ServiceReference<TCPSocketServerRequestExecutorCustomizer>>();
	private static TCPSocketServerComponent comp;
	
	public TCPSocketServerComponent() {
		comp = this;
	}
	
	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
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
			refsCopy = new ArrayList<ServiceReference<TCPSocketServerRequestExecutorCustomizer>>(customizers);
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
					if (Activator.getContext().createFilter("(" + SERVER_ID_PROPNAME + "=" + ((String) o) + ")")
							// Then it looks for a match against the Dictionary d...which has name->value:
							// ecf.socket.serverid=<server container id>"
							.match(d)) {
						// If this matches then we choose higher priority between ref and resultRef
						resultRef = chooseHigherPriority(ref, resultRef);
					}
				} catch (InvalidSyntaxException e) {
					// should not happen, but if it does (bad ecf.socket.targetidfilter) then
					// we will report on System.err and ignore this service instance
					System.err.println("Could not create filter from value of ecf.socket.targetidfilter property=" + o);
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
			ServiceReference<TCPSocketServerRequestExecutorCustomizer> first, ServiceReference<TCPSocketServerRequestExecutorCustomizer> second) {
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
