package com.codeandstrings.niohttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import com.codeandstrings.niohttp.data.IdealBlockSize;
import com.codeandstrings.niohttp.data.Parameters;
import com.codeandstrings.niohttp.enums.HttpProtocol;
import com.codeandstrings.niohttp.exceptions.http.HttpException;
import com.codeandstrings.niohttp.exceptions.http.RequestEntityTooLargeException;
import com.codeandstrings.niohttp.handlers.RequestHandler;
import com.codeandstrings.niohttp.handlers.StringRequestHandler;
import com.codeandstrings.niohttp.request.Request;
import com.codeandstrings.niohttp.request.RequestBodyFactory;
import com.codeandstrings.niohttp.request.RequestHeaderFactory;
import com.codeandstrings.niohttp.response.BufferContainer;
import com.codeandstrings.niohttp.response.ExceptionResponseFactory;
import com.codeandstrings.niohttp.response.Response;
import com.codeandstrings.niohttp.response.ResponseFactory;

class Session$Line {

	public String line;
	public int start;
	public int nextStart;

	public Session$Line(String line, int start, int nextStart) {
		this.line = line;
		this.start = start;
		this.nextStart = nextStart;
	}

	@Override
	public String toString() {
		return "Session$Line [line=" + line + ", start=" + start
				+ ", nextStart=" + nextStart + "]";
	}

}

public class Session {

	/*
	 * Our channel and selector
	 */
	private SocketChannel channel;
	private Selector selector;
	private Parameters parameters;

	// TODO: Eventually we want to migrate "maxRequestSize" to make it
	// "maxRequestHeaderSize" -
	// obviously a much larger POST is supported.

	/*
	 * Request acceptance data
	 */
	private int maxRequestSize;
	private byte[] requestHeaderData;
	private int requestHeaderMarker;
	private ArrayList<Session$Line> requestHeaderLines;
	private int lastHeaderByteLocation;
	private ByteBuffer readBuffer;
	private RequestHandler requestHandler;
	private RequestHeaderFactory headerFactory = new RequestHeaderFactory();
	private boolean bodyReadBegun;
	private RequestBodyFactory bodyFactory = new RequestBodyFactory();

	/*
	 * Response Management
	 */
	private ArrayList<BufferContainer> outputQueue;

	/**
	 * Constructor.
	 * 
	 * @param channel
	 * @param selector
	 */
	public Session(SocketChannel channel, Selector selector,
			RequestHandler handler, Parameters parameters) {

		this.channel = channel;
		this.selector = selector;
		this.maxRequestSize = IdealBlockSize.VALUE;
		this.readBuffer = ByteBuffer.allocate(128);
		this.outputQueue = new ArrayList<BufferContainer>();
		this.requestHandler = handler;
		this.parameters = parameters;

		this.reset();
	}

	private void reset() {
		this.requestHeaderData = new byte[maxRequestSize];
		this.requestHeaderMarker = 0;
		this.requestHeaderLines = new ArrayList<Session$Line>();
		this.lastHeaderByteLocation = 0;
		this.headerFactory = new RequestHeaderFactory();
		this.bodyReadBegun = false;
		this.bodyFactory = new RequestBodyFactory();
	}

	public SocketChannel getChannel() {
		return this.channel;
	}

	private void handleRequest(Request r) throws IOException {

		if (this.requestHandler instanceof StringRequestHandler) {

			StringRequestHandler casted = (StringRequestHandler) this.requestHandler;

			try {

				Response response = ResponseFactory.createResponse(
						casted.handleRequest(r), casted.getContentType(),
						r.getRequestProtocol(), r.getRequestMethod(),
						this.parameters);

				if (r.getRequestProtocol() == HttpProtocol.HTTP1_0) {
					this.outputQueue.add(new BufferContainer(response
							.getByteBuffer(), true));
				} else {
					this.outputQueue.add(new BufferContainer(response
							.getByteBuffer(), false));
				}

			} catch (HttpException e) {
				this.generateResponseException(e);
			}

			this.socketWriteEvent();

		}

	}

	private void generateAndHandleRequest() throws HttpException, IOException {

		InetSocketAddress remote = (InetSocketAddress) this.channel
				.getRemoteAddress();

		Request r = Request.generateRequest(remote.getHostString(),
				remote.getPort(), headerFactory.build(), bodyFactory.build());

		this.handleRequest(r);
		this.reset();
	}
	
	private void copyExistingBytesToBody(int startPosition) {		
		this.bodyFactory.addBytes(this.requestHeaderData, startPosition, this.lastHeaderByteLocation);
	}

	private void analyzeForHeader() throws HttpException, IOException {

		// likely won't ever happen anyways, but just in case
		// don't do this - we're already in body mode
		if (this.bodyReadBegun) {			
			return;
		}
		
		// there is nothing to analyze
		if (this.requestHeaderLines.size() == 0)
			return;

		// reset our factory
		this.headerFactory.reset();

		// walk the received header lines.
		for (Session$Line sessionLine : this.requestHeaderLines) {

			headerFactory.addLine(sessionLine.line);

			if (headerFactory.shouldBuildRequestHeader()) {

				int requestBodySize = headerFactory.shouldExpectBody();

				if (requestBodySize > this.parameters.getMaximumPostSize()) {
					
					throw new RequestEntityTooLargeException(requestBodySize);
					
				} else if (requestBodySize != -1) {

					this.bodyFactory.resize(requestBodySize);
					this.bodyReadBegun = true;
					this.copyExistingBytesToBody(sessionLine.nextStart);
					
					if (this.bodyFactory.isFull()) {
						this.generateAndHandleRequest();
					}

				} else {
					
					this.generateAndHandleRequest();
					
				}
				
			}

		}

	}

	private void extractLines() {

		for (int i = this.lastHeaderByteLocation; i < this.requestHeaderMarker; i++) {

			if (i == 0) {
				continue;
			}

			if (this.requestHeaderData[i] == 10
					&& this.requestHeaderData[i - 1] == 13) {

				String line = null;

				if ((i - this.lastHeaderByteLocation - 1) == 0) {
					line = new String();
				} else {
					line = new String(this.requestHeaderData,
							this.lastHeaderByteLocation, i
									- this.lastHeaderByteLocation - 1);
				}

				this.requestHeaderLines.add(new Session$Line(line,
						this.lastHeaderByteLocation, i + 1));

				this.lastHeaderByteLocation = (i + 1);
			}

		}

	}

	private void closeChannel() throws IOException {
		this.channel.close();
	}

	private void setSelectionRequest(boolean write)
			throws ClosedChannelException {

		int ops;

		if (write) {
			ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
		} else {
			ops = SelectionKey.OP_READ;
		}

		this.channel.register(this.selector, ops, this);
	}

	public void socketWriteEvent() throws IOException {

		boolean closedConnection = false;

		if (this.outputQueue.size() > 0) {

			BufferContainer container = this.outputQueue.remove(0);

			this.channel.write(container.getBuffer());

			// kill the connection?
			if (container.isCloseOnTransmission()) {
				this.closeChannel();
				closedConnection = true;
			}
		}

		if (this.outputQueue.size() == 0 && !closedConnection) {
			this.setSelectionRequest(false);
		}

	}

	private void generateResponseException(HttpException e) throws IOException {

		Response r = (new ExceptionResponseFactory(e)).create(this.parameters);
		this.outputQueue.add(new BufferContainer(r.getByteBuffer(), true));
		this.setSelectionRequest(true);
		this.socketWriteEvent();

	}

	public void socketReadEvent() throws IOException {

		try {
			this.readBuffer.clear();

			if (!this.channel.isConnected() || !this.channel.isOpen()) {
				return;
			}

			int bytesRead = this.channel.read(this.readBuffer);

			if (bytesRead == -1) {
				this.closeChannel();
			} else {

				byte[] bytes = new byte[bytesRead];

				this.readBuffer.flip();
				this.readBuffer.get(bytes);
				
				if (this.bodyReadBegun) {					
					this.bodyFactory.addBytes(bytes);
					
					if (this.bodyFactory.isFull()) {
						this.generateAndHandleRequest();
					}					
				}
				else {

					for (int i = 0; i < bytesRead; i++) {
	
						if (this.requestHeaderMarker >= (this.maxRequestSize - 1)) {
							throw new RequestEntityTooLargeException();
						}
	
						this.requestHeaderData[this.requestHeaderMarker] = bytes[i];
						this.requestHeaderMarker++;
					}
	
					// header has been injested
					this.extractLines();
					this.analyzeForHeader();
					
				}

			}
		} catch (IOException e) {
			e.printStackTrace();
			this.closeChannel();
		} catch (HttpException e) {
			generateResponseException(e);
		}

	}

}
