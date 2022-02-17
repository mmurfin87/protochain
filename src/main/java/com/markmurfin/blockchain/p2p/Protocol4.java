package com.markmurfin.blockchain.p2p;

import lombok.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.stream.Collectors;

public class Protocol4 implements Closeable, AutoCloseable
{
	public Protocol4() throws IOException
	{
		this.serverSocketChannel = ServerSocketChannel.open();
		this.serverSocketChannel.configureBlocking(false);
		this.selector = Selector.open();
	}

	public Set<Peer> peers()
	{
		return selector.keys().stream()
			.map(SelectionKey::attachment)
			.filter(Objects::nonNull)   // Filter out the server's socket listening for new connections - it has no connection state
			.map(o -> ((ConnectionState)o).peer)
			.collect(Collectors.toSet());
	}

	public void peer(@NonNull final InetSocketAddress peerAddress) throws IOException
	{
		final SocketChannel socketChannel = SocketChannel.open();
		final ConnectionState connectionState = new ConnectionState(new Peer(peerAddress), new ArrayList<>());
		socketChannel.configureBlocking(false);
		if (!socketChannel.connect(peerAddress) && !socketChannel.finishConnect())
			socketChannel.register(selector, SelectionKey.OP_CONNECT, connectionState);
		else
		{
			socketChannel.register(selector, SelectionKey.OP_READ, connectionState);
			logPeerAction(peerAddress, "connected outgoing immediate");
		}
	}

	public void unpeer(@NonNull final InetSocketAddress peerAddress) throws IOException
	{
		for (final SelectionKey sk : selector.keys())
		{
			if (!Objects.equals(((SocketChannel)sk.channel()).getRemoteAddress(), peerAddress))
				continue;
			sk.cancel();
			sk.channel().close();
		};
	}

	public void send(@NonNull final Peer peer, @NonNull final ByteBuffer byteBuffer) throws IOException, IllegalStateException
	{
		for (final SelectionKey sk : selector.keys())
		{
			if (!sk.isValid() || sk.attachment() == null || !Objects.equals(peer, ((ConnectionState)sk.attachment()).peer))
				continue;
			System.out.println("Sending " + byteBuffer.remaining() + " bytes to " + peer.address);
			((ConnectionState)sk.attachment()).buffers.add(byteBuffer.duplicate());
			write(sk);
		}
	}

	public void close() throws IOException
	{
		serverSocketChannel.close();
		for (final SelectionKey sk : selector.keys())
		{
			sk.cancel();
			sk.channel().close();
		}
		selector.close();
	}

	public void run(final int port, @NonNull final ReadHandler readHandler) throws IOException
	{
		if (port < 0)
			throw new IllegalArgumentException("port must be positive");

		serverSocketChannel.socket().bind(new InetSocketAddress(port));
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		while (true)
		{
			if (!selector.isOpen())
				break;
			selector.select();
			final Set<SelectionKey> keys = selector.selectedKeys();
			for (var sk : keys)
			{
				System.out.println("Selection Event: " + sk.readyOps() + " " + sk.channel());
				if (sk.isConnectable())
				{
					final SocketChannel socketChannel = (SocketChannel) sk.channel();
					if (socketChannel.isConnectionPending())
						socketChannel.finishConnect();
					sk.interestOps(SelectionKey.OP_READ);
					logPeerAction(((ConnectionState)sk.attachment()).peer.address, "connected outgoing finished");
				}
				if (sk.isAcceptable())
				{
					final SocketChannel socketChannel = serverSocketChannel.accept();
					final ConnectionState connectionState = new ConnectionState(new Peer((InetSocketAddress) socketChannel.getRemoteAddress()), new ArrayList<>());
					socketChannel.configureBlocking(false);
					socketChannel.register(selector, SelectionKey.OP_READ, connectionState);
					logPeerAction(connectionState.peer.address, "connected incoming");
				}
				else if (sk.isReadable())
					read(sk, readHandler);
				else if (sk.isWritable())
					write(sk);
			}
			keys.clear();
		}
		System.out.println("Selector closed");
	}

	private void read(@NonNull final SelectionKey sk, @NonNull final ReadHandler readHandler) throws IOException
	{
		final SocketChannel channel = (SocketChannel) sk.channel();
		final ConnectionState connectionState = (ConnectionState) sk.attachment();
		final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

		final int read;
		try
		{
			read = channel.read(byteBuffer);
		}
		catch (final IOException e)
		{
			channel.close();
			sk.cancel();
			logPeerAction(connectionState.peer.address, "closed connection rudely");
			return;
		}
		if (read == -1)
		{
			channel.close();
			sk.cancel();
			logPeerAction(connectionState.peer.address, "closed connection gracefully");
		}
		else
		{
			byteBuffer.flip();
			readHandler.accept(connectionState.peer, byteBuffer);
			logPeerAction(connectionState.peer.address, "received: %d bytes", read);
		}
	}

	private void write(@NonNull final SelectionKey sk) throws IOException
	{
		final SocketChannel socketChannel = (SocketChannel) sk.channel();
		final ConnectionState connectionState = (ConnectionState) sk.attachment();
		final Iterator<ByteBuffer> bbi = connectionState.buffers.iterator();
		while (bbi.hasNext())
		{
			final ByteBuffer bb = bbi.next();
			while (bb.hasRemaining() && socketChannel.write(bb) > 0);   // write as long as there is more data to write in the buffer and the send buffer is accepting data (e.g., write() returns > 0)
			// If we have more to write but the send buffer is full, return false.
			if (bb.hasRemaining())
			{
				socketChannel.register(selector, SelectionKey.OP_WRITE, connectionState);
				return;
			}
			bbi.remove();
		}
	}

	private static void logPeerAction(final InetSocketAddress peer, @NonNull final String format, @NonNull final Object... arguments) throws IOException
	{
		System.out.format("%s: %s%n", peer, String.format(format, arguments));
	}

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
}
