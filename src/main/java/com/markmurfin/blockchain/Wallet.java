package com.markmurfin.blockchain;

import lombok.NonNull;

import java.security.*;
import java.util.*;

public class Wallet
{
	public PrivateKey privateKey;
	public PublicKey publicKey;
	public Map<String, TransactionEntry> utxos;

	public Wallet()
	{
		final KeyPair keyPair = BlockUtils.generateKeyPair();
		this.privateKey = keyPair.getPrivate();
		this.publicKey = keyPair.getPublic();
		this.utxos = new HashMap<>();
	}

	public float getBalance()
	{
		return PrototypeChain.utxos.values().stream()
			.filter(to -> to.isOwnedBy(publicKey))
			.reduce(
				0f,
				(sum, to) ->
				{
					utxos.put(to.id, to);
					return sum + to.value;
				},
				Float::sum);
	}

	public Optional<Transaction> send(@NonNull final PublicKey recipientPublicKey, float value)
	{
		final List<TransactionEntry> inputs = cover(value);
		if (inputs.isEmpty())
			return Optional.empty();

		final Transaction transaction = new Transaction(publicKey, recipientPublicKey, value, inputs);
		transaction.sign(privateKey);

		//inputs.forEach(ti -> utxos.remove(ti.transactionOutputId));
		inputs.forEach(ti -> utxos.remove(ti.id));

		return Optional.of(transaction);
	}

	public List<TransactionEntry> cover(float value)
	{
		if (getBalance() < value)
		{
			System.out.println("Insufficient funds");
			return List.of();
		}

		final List<TransactionEntry> inputs = new ArrayList<>();
		float total = 0f;
		for (var utxo : utxos.values())
		{
			total += utxo.value;
			inputs.add(utxo);
			if (total > value)
				break;
		}
		return inputs;
	}
}
