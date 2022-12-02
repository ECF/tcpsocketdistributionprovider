package org.eclipse.ecf.provider.tcpsocket.common;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;

public interface TCPSocketRequestCustomizer {
	
	TCPSocketRequest createRequest(ID requestContainerID, long serviceId, RemoteCallImpl call);

}
