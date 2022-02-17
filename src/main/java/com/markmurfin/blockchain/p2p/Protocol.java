package com.markmurfin.blockchain.p2p;

import lombok.NonNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Protocol implements Runnable, Closeable, AutoCloseable
{
	public Protocol(final int port) throws IOException
	{
		if (port < 0)
			throw new IllegalArgumentException("port must be positive");
		this.closing = false;
		this.port = port;
		this.channels = new HashMap<>();
		this.serverSocketChannel = ServerSocketChannel.open();
		this.serverSocketChannel.configureBlocking(false);
		this.selector = Selector.open();
	}

	@Override
	public void run()
	{
		try
		{
			run2();
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}

	}

	public void peer(@NonNull final InetSocketAddress peerAddress) throws IOException
	{
		final SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		if (!socketChannel.connect(peerAddress) && !socketChannel.finishConnect())
			socketChannel.register(selector, SelectionKey.OP_CONNECT);
		else
		{
			this.channels.put(socketChannel, new ArrayList<>());
			socketChannel.register(selector, SelectionKey.OP_READ);
			logPeerAction(socketChannel, "connected");
		}
	}

	public void unpeer(@NonNull final InetSocketAddress peerAddress) throws IOException
	{
		final List<SocketChannel> unpeerChannels = this.channels.keySet().stream()
			.filter(sc ->
			{
				try
				{
					return Objects.equals(sc.getRemoteAddress(), peerAddress);
				}
				catch (final IOException e)
				{
					throw new RuntimeException(e);
				}
			})
			.toList();
		unpeerChannels.forEach(sc ->
		{
			try
			{
				channels.remove(sc);
				sc.close();
			}
			catch (IOException e)
			{
				System.out.format("%s: closing exception: %s%n", peerAddress, e.getMessage());
			}
		});
	}

	public void write(@NonNull final ByteBuffer byteBuffer) throws IOException, IllegalStateException
	{
		if (closing)
			throw new IllegalStateException("This node cannot accept writes because it has been instructed to close connections and is shutting down");
		final Iterator<Map.Entry<SocketChannel, List<ByteBuffer>>> ci = channels.entrySet().iterator();
		while (ci.hasNext())
		{
			final Map.Entry<SocketChannel, List<ByteBuffer>> entry = ci.next();
			final SocketChannel socketChannel = entry.getKey();
			final List<ByteBuffer> bbl = entry.getValue();
			bbl.add(byteBuffer);
			if (!write(socketChannel, bbl))
				socketChannel.register(selector, SelectionKey.OP_WRITE);
		}
	}

	public void close() throws IOException
	{
		forceClose();
	}

	public void forceClose() throws IOException
	{
		selector.close();
		for (var sc : channels.keySet())
			sc.close();
		channels.clear();
		serverSocketChannel.close();
	}

	private void run2() throws IOException
	{
		serverSocketChannel.socket().bind(new InetSocketAddress(port));
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		while (true)
		{
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
					this.channels.put(socketChannel, new ArrayList<>());
					socketChannel.register(selector, SelectionKey.OP_READ);
					logPeerAction(socketChannel, "connected");
				}
				if (sk.isAcceptable())
				{
					final SocketChannel socketChannel = serverSocketChannel.accept();
					this.channels.put(socketChannel, new ArrayList<>());
					socketChannel.configureBlocking(false);
					socketChannel.register(selector, SelectionKey.OP_READ);
					logPeerAction(socketChannel, "connected");
				}
				else if (sk.isReadable())
					read((SocketChannel) sk.channel());
				else if (sk.isWritable())
				{
					final SocketChannel socketChannel = (SocketChannel)sk.channel();
					if (!write(socketChannel, channels.get(socketChannel)))
						socketChannel.register(selector, SelectionKey.OP_WRITE);
				}
			}
			keys.clear();
		}
	}

	private void read(@NonNull final SocketChannel channel) throws IOException
	{
		final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

		final int read;
		try
		{
			read = channel.read(byteBuffer);
		}
		catch (final IOException e)
		{
			logPeerAction(channel, "closed connection rudely");
			channel.close();
			channels.remove(channel);
			return;
		}
		if (read == -1)
		{
			logPeerAction(channel, "closed connection gracefully");
			channel.close();
			channels.remove(channel);
		}
		else
		{
			byteBuffer.flip();
			logPeerAction(channel, "received: %s", bufferToString(byteBuffer));
		}
	}

	private boolean write(@NonNull final SocketChannel socketChannel, @NonNull final List<ByteBuffer> buffers) throws IOException
	{
		final Iterator<ByteBuffer> bbi = buffers.iterator();
		while (bbi.hasNext())
		{
			final ByteBuffer bb = bbi.next();
			while (bb.hasRemaining() && socketChannel.write(bb) > 0);   // write as long as there is more data to write in the buffer and the send buffer is accepting data (e.g., write() returns > 0)
			// If we have more to write but the send buffer is full, return false.
			if (bb.hasRemaining())
				return false;
			bbi.remove();
		}
		return true;
	}

	private static void logPeerAction(final SocketChannel sc, @NonNull final String format, @NonNull final Object... arguments) throws IOException
	{
		System.out.format("%s: %s%n", sc.getRemoteAddress(), String.format(format, arguments));
	}

	public static String bufferToString(@NonNull final ByteBuffer bb)
	{
		final byte[] arr = new byte[bb.limit()];
		bb.get(arr);
		bb.flip();
		return new String(arr, StandardCharsets.UTF_8);
	}

	private final Map<SocketChannel, List<ByteBuffer>> channels;
	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final int port;
	private boolean closing;
}
