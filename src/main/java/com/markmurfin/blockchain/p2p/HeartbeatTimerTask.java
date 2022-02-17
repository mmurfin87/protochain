package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@AllArgsConstructor
public class HeartbeatTimerTask extends TimerTask
{
	@Override
	public void run()
	{
		if (Duration.between(peerConnection.heartbeat.lastBeat(), Instant.now()).minus(Duration.of(5, ChronoUnit.SECONDS)).isNegative())
			return;
		if (!peerConnection.socketChannel.isOpen())
		{
			this.cancel();
			return;
		}

		try
		{
			if (!peerConnection.write(ByteBuffer.wrap(peerConnection.heartbeat.beat().getBytes(StandardCharsets.UTF_8))))
				peerConnection.socketChannel.register(selector, SelectionKey.OP_WRITE, peerConnection);
		}
		catch (final IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@NonNull
	private final Selector selector;
	@NonNull
	private final Protocol4Connection peerConnection;
}
