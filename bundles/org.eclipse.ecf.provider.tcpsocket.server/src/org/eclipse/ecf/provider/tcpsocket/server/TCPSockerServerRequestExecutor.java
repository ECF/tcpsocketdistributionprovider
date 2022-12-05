package org.eclipse.ecf.provider.tcpsocket.server;

import java.lang.reflect.Method;

import org.eclipse.ecf.core.sharedobject.SharedObjectMsg;
import org.eclipse.ecf.core.status.SerializableStatus;
import org.eclipse.ecf.core.util.reflection.ClassUtil;
import org.eclipse.ecf.provider.remoteservice.generic.RemoteCallImpl;
import org.eclipse.ecf.provider.remoteservice.generic.Response;
import org.eclipse.ecf.provider.tcpsocket.common.TCPSocketRequest;
import org.eclipse.ecf.remoteservice.RemoteServiceRegistrationImpl;
import org.eclipse.ecf.remoteservice.asyncproxy.AsyncReturnUtil;
import org.eclipse.ecf.remoteservice.util.AsyncUtil;

public class TCPSockerServerRequestExecutor {

	public Response execute(TCPSocketRequest request, RemoteServiceRegistrationImpl reg) {
		long requestId = request.getRequestId();
		Response response;
		try {
			RemoteCallImpl call = request.getCall();
			Object[] callArgs = call.getParameters();
			Object[] args = (callArgs == null) ? SharedObjectMsg.nullArgs : callArgs;
			Object service = reg.getService();
			// Find appropriate method on service
			final Method method = ClassUtil.getMethod(service.getClass(), call.getMethod(),
					SharedObjectMsg.getTypesForParameters(args));
			// Actually invoke method on service object
			Object result = method.invoke(service, args);
			if (result != null) {
				@SuppressWarnings("rawtypes")
				Class returnType = method.getReturnType();
				// provider must expose osgi.async property and must be async return type
				if (AsyncUtil.isOSGIAsync(reg.getReference()) && AsyncReturnUtil.isAsyncType(returnType))
					result = AsyncReturnUtil.convertAsyncToReturn(result, returnType, call.getTimeout());
			}
			response = new Response(requestId, result);
		} catch (Exception | NoClassDefFoundError e) {
			response = createErrorResponse(request, e);
		}
		return response;
	}

	protected Response createErrorResponse(TCPSocketRequest request, Throwable e) {
		return new Response(request.getRequestId(),
				new SerializableStatus(0, "org.eclipse.ecf.provider.tcpsocket.server", null, e).getException());
	}
}
