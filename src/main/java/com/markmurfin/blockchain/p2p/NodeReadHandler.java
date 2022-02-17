package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@AllArgsConstructor
public class NodeReadHandler implements ReadHandler
{
	@Override
	public void accept(@NonNull Peer peer, @NonNull ByteBuffer byteBuffer)
	{
		final String content = bufferToString(byteBuffer);
		System.out.format("Message: %s%n", content);

		// Broadcast
		final String[] parts = content.split("/|");
		if (parts.length == 3 && "broadcast".equalsIgnoreCase(parts[0]))
		{
			final String msg = String.format("broadcast|%s|%s", peer.address, content);
			final ByteBuffer broadcastBuffer = ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8));
			connectedPeers.entrySet().stream()
				.filter(e -> !Objects.equals(e.getKey(), peer))
				.forEach(p ->
				{
					final Protocol4Connection pc = p.getValue();
					try
					{
						if (!pc.write(broadcastBuffer.duplicate()))
							pc.socketChannel.register(selector, SelectionKey.OP_WRITE, pc);
					} catch (final IOException e)
					{
						throw new RuntimeException(e);
					}
				});
		}
	}

	public static String bufferToString(@NonNull final ByteBuffer bb)
	{
		final byte[] arr = new byte[bb.limit()];
		bb.get(arr);
		bb.flip();
		return new String(arr, StandardCharsets.UTF_8);
	}

	@NonNull
	private final Selector selector;
	@NonNull
	private final Map<Peer, Protocol4Connection> connectedPeers;
}
