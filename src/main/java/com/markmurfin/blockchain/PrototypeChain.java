package com.markmurfin.blockchain;

import lombok.NonNull;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.*;

public class PrototypeChain
{
	public static final int hashDifficulty = 4;
	public static final float minimumTransactionSize = 0.1f;
	public static String GENESIS_HASH = "0";
	private static final List<Block> blockChain = new ArrayList<>();
	public static final Map<String, TransactionEntry> utxos = new HashMap<>();

	public static void main(final String[] args)
	{
		Security.addProvider(new BouncyCastleProvider());

		final Wallet coinbase = new Wallet(), walletA = new Wallet(), walletB = new Wallet();

		final Transaction testTransaction = new Transaction(walletA.publicKey, walletB.publicKey, 5, List.of());
		testTransaction.sign(walletA.privateKey);

		//System.out.format("A PrivateKey: %s%n", BlockUtils.hex(walletA.privateKey.getEncoded()));
		//System.out.format("A PublicKey:  %s%n", BlockUtils.hex(walletA.publicKey.getEncoded()));
		//System.out.format("Transaction Signature: %s%n", BlockUtils.hex(testTransaction.signature));
		//System.out.format("Is transaction authentic?: %s%n", testTransaction.authenticate());

		final Transaction genesisTransaction = new Transaction(coinbase.publicKey, walletA.publicKey, 100f, List.of());
		genesisTransaction.sign(coinbase.privateKey);
		genesisTransaction.transactionId = GENESIS_HASH;
		final TransactionEntry genesisTransactionEntry = new TransactionEntry(genesisTransaction.recipient, genesisTransaction.value, genesisTransaction.transactionId);
		genesisTransaction.outputs = List.of(genesisTransactionEntry);
		utxos.put(genesisTransactionEntry.id, genesisTransactionEntry);

		///////////////////
		// GENESIS BLOCK //
		///////////////////
		final Block genesisBlock = new Block(GENESIS_HASH);
		genesisBlock.addTransaction(genesisTransaction);
		addBlock(genesisBlock);

		System.out.format("WalletA's balance is: %f%n", walletA.getBalance());
		System.out.format("WalletB's balance is: %f%n", walletB.getBalance());

		//////////////////
		// TEST BLOCK 1 //
		//////////////////
		final Block block1 = new Block(lastHash());
		final float transfer1 = 40f;
		System.out.format("Sending %f coin from WalletA to WalletB%n", transfer1);
		walletA.send(walletB.publicKey, transfer1)
			.ifPresent(t ->
			{
				if (block1.addTransaction(t))
					addBlock(block1);
			});

		System.out.format("WalletA's balance is: %f%n", walletA.getBalance());
		System.out.format("WalletB's balance is: %f%n", walletB.getBalance());

		//////////////////
		// Test Block 2 //
		//////////////////
		final Block block2 = new Block(lastHash());
		final float transfer2 = 100f;

		System.out.format("Sending %f coin from WalletA to WalletB%n", transfer2);
		walletA.send(walletB.publicKey, transfer2)
			.ifPresent(t ->
			{
				System.out.println("Transaction in block 2!");
				if (block2.addTransaction(t))
					addBlock(block2);
			});

		System.out.format("WalletA's balance is: %f%n", walletA.getBalance());
		System.out.format("WalletB's balance is: %f%n", walletB.getBalance());

		//////////////////
		// Test Block 3 //
		//////////////////
		final Block block3 = new Block(lastHash());
		final float transfer3 = 20f;

		System.out.format("Sending %f coin from WalletB to WalletA%n", transfer3);
		walletB.send(walletA.publicKey, transfer3)
			.ifPresent(t ->
			{
				if (block3.addTransaction(t))
					addBlock(block3);
			});

		System.out.format("WalletA's balance is: %f%n", walletA.getBalance());
		System.out.format("WalletB's balance is: %f%n", walletB.getBalance());

		//////////////////
		// Test Block 4 //
		//////////////////
		final Block block4 = new Block(lastHash());
		final float transfer4 = 10f;
		System.out.format("Sending %f coin from WalletA to WalletB%n", transfer4);
		final Transaction malformedTransaction = new Transaction(walletA.publicKey, walletB.publicKey, transfer4, walletA.cover(transfer4));
		malformedTransaction.sign(walletA.privateKey);
		malformedTransaction.process();
		final TransactionEntry malformedTransactionEntry = new TransactionEntry(malformedTransaction.recipient, malformedTransaction.value, malformedTransaction.transactionId);
		malformedTransaction.outputs = List.of(malformedTransactionEntry);
		block4.transactions.add(malformedTransaction);
		addBlock(block4);

		for (int i = 0; i < blockChain.size(); i++)
			System.out.format("Block %d: %s%n", i, blockChain.get(i));

		System.out.format("Is blockchain valid?: %s%n", isChainValid());
	}

	private static boolean isChainValid()
	{
		if (blockChain.isEmpty())
			return true;
		final String hashTarget = BlockUtils.hashTarget(hashDifficulty);
		final Map<String, TransactionEntry> tmpUtxos = new HashMap<>();
		blockChain.get(0).transactions.forEach(t -> t.updateUtxos(tmpUtxos));
		Block curBlock = null, prevBlock = null;
		for (int i = 1; i < blockChain.size(); i++)
		{
			curBlock = blockChain.get(i);
			prevBlock = blockChain.get(i-1);

			// Compare the stored and calculated hashes
			if (!Objects.equals(curBlock.hash, curBlock.calculateHash()))
			{
				System.out.format("Block #%d %s current hashes not equal%n", i, curBlock.hash);
				return false;
			}

			// Compare the chain hashes
			if (!Objects.equals(prevBlock.hash, curBlock.previousHash))
			{
				System.out.format("Block #%d %s previous hashes not equal", i, curBlock.hash);
				return false;
			}

			// Ensure block hash is solved
			if (!Objects.equals(curBlock.hash.substring(0, hashDifficulty), hashTarget))
			{
				System.out.format("Block #%d %s hasn't been mined%n", i, curBlock.hash);
				return false;
			}

			// Check transactions
			for (int t = 0; t < curBlock.transactions.size(); t++)
			{
				final Transaction curTransaction = curBlock.transactions.get(t);

				// Ensure transaction is authentic
				if (!curTransaction.authenticate())
				{
					System.out.format("Invalid Transaction %d in Block %d %s%n", t, i, curBlock.hash);
					return false;
				}

				// Ensure transaction is balanced
				if (curTransaction.calculateInputValue() != curTransaction.calculateOutputValue())
				{
					System.out.format("Inputs don't equal outputs in Transaction %d in Block %d %s%n", t, i, curBlock.hash);
					return false;
				}

				for (var input : curTransaction.inputs)
				{
					final TransactionEntry tmpo = tmpUtxos.get(input.id);
					if (tmpo == null)
					{
						System.out.format("Missing input entry %s on Transaction %d in Block %d %s%n", input.id, t, i, curBlock.hash);
						return false;
					}

					if (input.value != tmpo.value)
					{
						System.out.format("Invalid value on input entry %s on Transaction %d in Block %d %s%n", input.id, t, i, curBlock.hash);
						return false;
					}

					tmpUtxos.remove(input.id);
				}

				curTransaction.outputs.forEach(to -> tmpUtxos.put(to.id, to));

				/*
				These two checks referencing recipient 0 and 1 look like hacks.
				They're related to how transaction outputs are created in Transaction#process
				Probably in the future remove
				 */
				if (!Objects.equals(curTransaction.outputs.get(0).recipient, curTransaction.recipient))
				{
					System.out.format("Output Recipient is not who it should be in Transaction %d in Block %d %s%n", t, i, curBlock.hash);
					return false;
				}

				if (!Objects.equals(curTransaction.outputs.get(1).recipient, curTransaction.sender))
				{
					System.out.format("Change recipient is not sender in Transaction %d in Block %d %s%n", t, i, curBlock.hash);
					return false;
				}
			}
		}

		return true;
	}

	public static void addBlock(@NonNull final Block block)
	{
		blockChain.add(block);
		block.mineBlock(hashDifficulty);
	}

	private static String lastHash()
	{
		if (blockChain.isEmpty())
			return GENESIS_HASH;
		return blockChain.get(blockChain.size()-1).hash;
	}
}
