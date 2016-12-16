package org.briarproject.bramble.socks;

import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.bramble.util.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

class SocksSocket extends Socket {

	private final SocketAddress proxy;
	private final int connectToProxyTimeout;

	SocksSocket(SocketAddress proxy, int connectToProxyTimeout) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
	}

	@Override
	public void connect(SocketAddress endpoint, int timeout)
			throws IOException {

		// Validate the endpoint
		if (!(endpoint instanceof InetSocketAddress))
			throw new IllegalArgumentException();
		InetSocketAddress inet = (InetSocketAddress) endpoint;
		String host = inet.getHostName();
		if (host.length() > 255) throw new IllegalArgumentException();
		int port = inet.getPort();

		// Connect to the proxy
		super.connect(proxy, connectToProxyTimeout);
		OutputStream out = getOutputStream();
		InputStream in = getInputStream();

		// Request SOCKS 5 with no authentication
		sendMethodRequest(out);
		receiveMethodResponse(in);

		// Use the supplied timeout temporarily
		int oldTimeout = getSoTimeout();
		setSoTimeout(timeout);

		// Connect to the endpoint via the proxy
		sendConnectRequest(out, host, port);
		receiveConnectResponse(in);

		// Restore the old timeout
		setSoTimeout(oldTimeout);
	}

	private void sendMethodRequest(OutputStream out) throws IOException {
		byte[] methodRequest = new byte[] {
				5, // SOCKS version is 5
				1, // Number of methods is 1
				0  // Method is 0, no authentication
		};
		out.write(methodRequest);
		out.flush();
	}

	private void receiveMethodResponse(InputStream in) throws IOException {
		byte[] methodResponse = new byte[2];
		IoUtils.read(in, methodResponse);
		byte version = methodResponse[0];
		byte method = methodResponse[1];
		if (version != 5)
			throw new IOException("Unsupported SOCKS version: " + version);
		if (method == (byte) 255)
			throw new IOException("Proxy requires authentication");
		if (method != 0)
			throw new IOException("Unsupported auth method: " + method);
	}

	private void sendConnectRequest(OutputStream out, String host, int port)
			throws IOException {
		byte[] connectRequest = new byte[7 + host.length()];
		connectRequest[0] = 5; // SOCKS version is 5
		connectRequest[1] = 1; // Command is 1, connect
		connectRequest[3] = 3; // Address type is 3, domain name
		connectRequest[4] = (byte) host.length(); // Length of domain name
		for (int i = 0; i < host.length(); i++)
			connectRequest[5 + i] = (byte) host.charAt(i);
		ByteUtils.writeUint16(port, connectRequest, connectRequest.length - 2);
		out.write(connectRequest);
		out.flush();
	}

	private void receiveConnectResponse(InputStream in) throws IOException {
		byte[] connectResponse = new byte[4];
		IoUtils.read(in, connectResponse);
		byte version = connectResponse[0];
		byte reply = connectResponse[1];
		byte addressType = connectResponse[3];
		if (version != 5)
			throw new IOException("Unsupported SOCKS version: " + version);
		if (reply != 0)
			throw new IOException("Connection failed: " + reply);
		if (addressType == 1) IoUtils.read(in, new byte[4]); // IPv4
		else if (addressType == 4) IoUtils.read(in, new byte[16]); // IPv6
		else throw new IOException("Unsupported address type: " + addressType);
		IoUtils.read(in, new byte[2]); // Port number
	}
}