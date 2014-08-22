package com.codeandstrings.niohttp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

import com.codeandstrings.niohttp.data.Parameters;
import com.codeandstrings.niohttp.exceptions.EngineInitException;
import com.codeandstrings.niohttp.exceptions.InsufficientConcurrencyException;
import com.codeandstrings.niohttp.exceptions.InvalidHandlerException;

public class Server implements Runnable {

	private Parameters parameters;
	private InetSocketAddress socketAddress;
	private ServerSocketChannel serverSocketChannel;
    private Engine[] engineSchedule;
    private ByteBuffer singleByteNotification;
    private int engineConcurrency;
    private int enginePointer;
    private int outstandingConnections;

	public Server(int concurrency) throws InsufficientConcurrencyException, EngineInitException {

        if (concurrency < 1) {
            throw new InsufficientConcurrencyException();
        }

        this.parameters = Parameters.getDefaultParameters();
        this.engineSchedule = new Engine[concurrency];
        this.outstandingConnections = 0;

        this.singleByteNotification = ByteBuffer.allocateDirect(1);
        this.singleByteNotification.put((byte)0x1);
        this.singleByteNotification.flip();

        this.engineConcurrency = concurrency;
        this.enginePointer = 0;

        for (int i = 0; i < this.engineConcurrency; i++) {
            this.engineSchedule[i] = new Engine(this.parameters);
        }

        Thread.currentThread().setName("NIO-HTTP Server Thread");

    }

	public void setParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	private void configureSocketAddress() {
		if (this.parameters.getServerIp() != null) {
			this.socketAddress = new InetSocketAddress(this.parameters.getServerIp(), this.parameters.getPort());
		} else {
			this.socketAddress = new InetSocketAddress(this.parameters.getPort());
		}
	}

	public void addRequestHandler(String path, Class handler) throws InvalidHandlerException {
        for (Engine engine : this.engineSchedule) {
            engine.addRequestHandler(path, handler);
        }
	}

	private void configureServerSocketChannel() throws IOException {
		this.serverSocketChannel = ServerSocketChannel.open();
		this.serverSocketChannel.configureBlocking(false);
		this.serverSocketChannel.bind(this.socketAddress, this.parameters.getConnectionBacklog());
	}

	@Override
	public void run() {

        /* start engine threads */
        for (Engine engine : this.engineSchedule) {
            engine.start();
        }

        try {

			this.configureSocketAddress();
			this.configureServerSocketChannel();

			Selector selector = Selector.open();

			this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			while (true) {

				int keys = selector.select();

				if (keys == 0) {
					continue;
				}

				Iterator<SelectionKey> ki = selector.selectedKeys().iterator();

				while (ki.hasNext()) {

					SelectionKey key = ki.next();

					if (key.isAcceptable()) {

						/*
						 * Accept a connection...
						 */
						SocketChannel connection = ((ServerSocketChannel) key.channel()).accept();
                        Engine nextEngine = this.engineSchedule[this.enginePointer];
                        Pipe.SinkChannel engineChannel = nextEngine.getEngineNotificationChannel();

                        this.singleByteNotification.rewind();
                        this.outstandingConnections++;

                        nextEngine.getSocketQueue().add(connection);

                        if (engineChannel.write(singleByteNotification) == 0) {
                            engineChannel.register(selector, SelectionKey.OP_WRITE);
                        } else {
                            this.outstandingConnections--;
                        }

                        this.enginePointer++;

                        if (this.enginePointer==this.engineConcurrency) {
                            this.enginePointer=0;
                        }

					}
                    else if (key.isWritable()) {

                        Pipe.SinkChannel engineChannel = (Pipe.SinkChannel)key.channel();
                        int j = 0;

                        for (int i = 0; i < this.outstandingConnections; i++) {
                            this.singleByteNotification.rewind();
                            if (engineChannel.write(singleByteNotification) == 1) {
                                j++;
                            }
                        }

                        this.outstandingConnections = this.outstandingConnections - j;

                        if (this.outstandingConnections < 0) {
                            System.err.println("Outstanding connection number under 0 (fatal/strange)");
                            System.exit(0);
                        } else if (this.outstandingConnections == 0) {
                            engineChannel.register(selector, 0);
                        }

                    }

					ki.remove();

				}

			}

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

	}

}
