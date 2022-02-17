package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor
public class Protocol4Connection
{
	public static final int CLOSED_GRACEFULLY = -1;
	public static final int CLOSED_RUDELY = -2;

	@NonNull
	public final Peer peer;
	@NonNull
	public final SocketChannel socketChannel;
	@NonNull
	public final Heartbeat heartbeat;
	private final List<ByteBuffer> buffers;

	/*
	public int read(@NonNull final SelectionKey sk, @NonNull final ReadHandler readHandler) throws IOException
	{
		final SocketChannel channel = (SocketChannel) sk.channel();
		final Protocol4Connection peerConnection = (Protocol4Connection) sk.attachment();
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
			return CLOSED_RUDELY;
		}
		if (read == -1)
		{
			channel.close();
			sk.cancel();
			return CLOSED_GRACEFULLY;
		}
		else
		{
			byteBuffer.flip();
			readHandler.accept(peerConnection.peer, byteBuffer);
			return read;
		}
	}
	*/

	public int read(@NonNull final ReadHandler readHandler) throws IOException
	{
		final ByteBuffer byteBuffer = ByteBuffer.allocate(1024);

		final int read;
		try
		{
			read = socketChannel.read(byteBuffer);
		}
		catch (final IOException e)
		{
			socketChannel.close();
			return CLOSED_RUDELY;
		}
		if (read == -1)
		{
			socketChannel.close();
			return CLOSED_GRACEFULLY;
		}
		else
		{
			byteBuffer.flip();
			readHandler.accept(peer, byteBuffer);
			return read;
		}
	}

	public boolean write(@NonNull final ByteBuffer byteBuffer) throws IOException
	{
		heartbeat.skip();
		System.out.println("Sending " + byteBuffer.remaining() + " bytes to " + peer.address);
		buffers.add(byteBuffer.duplicate());
		return flush();
	}

	public boolean flush() throws IOException
	{
		final Iterator<ByteBuffer> bbi = buffers.iterator();
		while (bbi.hasNext())
		{
			final ByteBuffer bb = bbi.next();
			while (bb.hasRemaining() && socketChannel.write(bb) > 0);   // write as long as there is more data to write in the buffer and the send buffer is accepting data (e.g., write() returns > 0)
			// If we have more to write but the send buffer is full, so register for when we make progress sending
			if (bb.hasRemaining())
				return false;

			bbi.remove();
		}

		return true;
	}

	@Override
	public String toString()
	{
		final long bytesRemaining = buffers.stream().reduce(0l, (sum, buffer) -> sum + buffer.remaining(), Long::sum);
		return String.format("%s %s | %s | %d bytes to send", peer.address, socketChannel.isOpen() ? "Connected" : "Disconnected", heartbeat, bytesRemaining);
	}
}
