package org.eclipse.ecf.provider.tcpsocket.client;

import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketRequest;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketRequestCustomizer;
import org.osgi.service.component.annotations.Component;

@Component(service=TCPSocketRequestCustomizer.class, property = { "ecf.socket.targetidfilter=tcp://loca*" })
public class TestTCPSocketRequestCustomizer implements TCPSocketRequestCustomizer {

	@Override
	public TCPSocketRequest createRequest(ID requestContainerID, long serviceId, RemoteCallImpl call) {
		return new TCPSocketRequest(requestContainerID, serviceId, call);
	}

}
