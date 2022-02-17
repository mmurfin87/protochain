package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.ToString;

import java.nio.ByteBuffer;
import java.util.List;

@ToString
@AllArgsConstructor
public final class ConnectionState
{
	public final Peer peer;
	public final List<ByteBuffer> buffers;
}
