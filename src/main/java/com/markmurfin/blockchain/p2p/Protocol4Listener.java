package com.markmurfin.blockchain.p2p;

import lombok.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class Protocol4Listener implements Closeable, AutoCloseable
{
	public Protocol4Listener(@NonNull final InetSocketAddress binding, @NonNull final Selector selector) throws IOException
	{
		this.serverSocketChannel = ServerSocketChannel.open();
		this.serverSocketChannel.configureBlocking(false);
		this.serverSocketChannel.socket().bind(binding);
		this.selectionKey = this.serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("Listening on address " + binding);
	}

	@Override
	public void close() throws IOException
	{
		this.selectionKey.cancel();
		this.serverSocketChannel.close();
	}

	private final SelectionKey selectionKey;
	private final ServerSocketChannel serverSocketChannel;
}
