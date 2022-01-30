package com.markmurfin.blockchain;

import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BlockBuilder
{
	private final String previousHash;
	private final long timestamp;
	private final List<Transaction> transactions;

	public BlockBuilder(@NonNull final String previousHash)
	{
		this.timestamp = System.currentTimeMillis() / 1000L;
		this.previousHash = previousHash;
		this.transactions = new ArrayList<>();
	}

	public boolean addTransaction(@NonNull final Transaction transaction)
	{
		if (!Objects.equals(PrototypeChain.GENESIS_HASH, previousHash) && !transaction.process())
		{
			System.out.println("Transaction processing failed. Discarding.");
			return false;
		}
		transactions.add(transaction);
		System.out.println("Transaction added to block");
		return true;
	}

	public String calculateHash(@NonNull final String merkleRoot, final int nonce)
	{
		return BlockUtils.sha256Hex(previousHash, Long.toString(timestamp), Integer.toString(nonce), merkleRoot);
	}

	public Block2 mineBlock(final int difficulty)
	{
		final Instant start = Instant.now();
		final String hashTarget = BlockUtils.hashTarget(difficulty);
		final String merkleRoot = BlockUtils.calculateMerkleRoot(transactions);
		int nonce = 0;
		String hash = calculateHash(merkleRoot, nonce);
		while (!hash.substring(0, difficulty).equals(hashTarget))
		{
			nonce++;
			hash = calculateHash(merkleRoot, nonce);
		}
		System.out.format("Blocked mined (%dms): %s%n", Duration.between(start, Instant.now()).toMillis(), hash);
		return new Block2(hash, merkleRoot, previousHash, nonce, timestamp, transactions);
	}
}
