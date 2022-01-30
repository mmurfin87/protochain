package com.markmurfin.blockchain;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ToString
@EqualsAndHashCode
public class Block
{
	public String hash;
	public int nonce;
	public String merkleRoot;
	public final String previousHash;
	public final long timestamp;
	public final List<Transaction> transactions;

	public Block(@NonNull final String previousHash)
	{
		this.timestamp = System.currentTimeMillis() / 1000L;
		this.previousHash = previousHash;
		this.transactions = new ArrayList<>();
		this.hash = calculateHash();
	}

	public boolean addTransaction(@NonNull final Transaction transaction)
	{
		if (merkleRoot != null)
			throw new RuntimeException("Block already mined");
		if (!Objects.equals(PrototypeChain.GENESIS_HASH, previousHash) && !transaction.process())
		{
			System.out.println("Transaction processing failed. Discarding.");
			return false;
		}
		transactions.add(transaction);
		System.out.println("Transaction added to block");
		return true;
	}

	public String calculateHash()
	{
		return BlockUtils.sha256Hex(previousHash, Long.toString(timestamp), Integer.toString(nonce), merkleRoot);
	}

	public void mineBlock(final int difficulty)
	{
		final Instant start = Instant.now();
		final String hashTarget = BlockUtils.hashTarget(difficulty);
		merkleRoot = BlockUtils.calculateMerkleRoot(transactions);
		while (!hash.substring(0, difficulty).equals(hashTarget))
		{
			nonce++;
			hash = calculateHash();
		}
		System.out.format("Blocked mined (%dms): %s%n", Duration.between(start, Instant.now()).toMillis(), hash);
	}

}
