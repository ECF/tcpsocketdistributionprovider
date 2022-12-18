package org.eclipse.ecf.provider.tcpsocket.server.internal;

import org.eclipse.ecf.provider.tcpsocket.server.TCPSockerServerRequestExecutor;
import org.eclipse.ecf.provider.tcpsocket.server.TCPSocketServerRequestExecutorCustomizer;
import org.osgi.service.component.annotations.Component;

@Component(service = TCPSocketServerRequestExecutorCustomizer.class, property = {
		"ecf.socket.serveridfilter=tcp://loca*" })
public class TestTCPSocketServerRequestExecutorCustomizer implements TCPSocketServerRequestExecutorCustomizer {

	@Override
	public TCPSockerServerRequestExecutor createRequestExecutor() {
		return new TCPSockerServerRequestExecutor();
	}

}
