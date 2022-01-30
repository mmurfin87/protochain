package com.markmurfin.blockchain;

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.List;

@AllArgsConstructor
public class Block2
{
	@NonNull
	public final String hash;
	@NonNull
	public final String merkleRoot;
	@NonNull
	public final String previousHash;
	public final int nonce;
	public final long timestamp;
	@NonNull
	public final List<Transaction>transactions;
}
