package org.eclipse.ecf.provider.tcpsocket.server;

public interface TCPSocketServerRequestExecutorCustomizer {

	String SERVER_ID_FILTER_PROPNAME = "ecf.socket.serveridfilter";

	TCPSockerServerRequestExecutor createRequestExecutor();

}
