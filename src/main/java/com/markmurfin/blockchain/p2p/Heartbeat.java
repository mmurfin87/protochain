package com.markmurfin.blockchain.p2p;

import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;

public class Heartbeat
{
	public Heartbeat()
	{
		tick = 0;
		last = Instant.EPOCH;
	}

	public Instant lastBeat()
	{
		return last;
	}

	public void skip()
	{
		last = Instant.now();
	}

	public String beat()
	{
		final String heartbeatMessage = String.format("Heartbeat #%d", tick++);
		last = Instant.now();
		return heartbeatMessage;
	}

	@Override
	public String toString()
	{
		return String.format("%.2f seconds ago on heartbeat #%d", (Duration.between(last, Instant.now()).toMillis() / 1000f), tick);
	}

	private int tick;
	@NonNull
	private Instant last;
}
