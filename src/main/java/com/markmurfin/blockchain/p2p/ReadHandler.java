package com.markmurfin.blockchain.p2p;

import lombok.NonNull;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

@FunctionalInterface
public interface ReadHandler
{
	void accept(@NonNull Peer peer, @NonNull ByteBuffer byteBuffer);
}
