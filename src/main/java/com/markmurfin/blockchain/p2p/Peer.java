package com.markmurfin.blockchain.p2p;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.net.InetSocketAddress;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class Peer
{
	public final InetSocketAddress address;
}
