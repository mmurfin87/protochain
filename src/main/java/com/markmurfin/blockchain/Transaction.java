package com.markmurfin.blockchain;

import lombok.NonNull;
import lombok.ToString;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ToString
public class Transaction
{
	public String transactionId;	// hash of the transaction
	@ToString.Exclude
	public final PublicKey sender;
	@ToString.Exclude
	public final PublicKey recipient;
	public final float value;				// amount sent
	@ToString.Exclude
	public byte[] signature;		// prevents spending more funds from wallet

	public final List<TransactionEntry> inputs;
	public List<TransactionEntry> outputs;

	private static int sequence = 0;	// keep track of transaction count

	public Transaction(@NonNull final PublicKey sender, @NonNull final PublicKey recipient, final float value, @NonNull final List<TransactionEntry> inputs)
	{
		this.sender = sender;
		this.recipient = recipient;
		this.value = value;
		this.inputs = List.copyOf(inputs);
	}

	public void sign(@NonNull PrivateKey privateKey)
	{
		signature = BlockUtils.sign(privateKey, data());
	}

	public boolean authenticate()
	{
		return BlockUtils.authenticate(sender, data(), signature);
	}

	public boolean process()
	{
		// Ensure the transaction is authentic
		if (!authenticate())
		{
			System.out.format("Failed to authenticate%n");
			return false;
		}

		// Gather transaction input to make sure they are actually unspent
		// this step is probably unneeded
		if (!Objects.equals(inputs, inputs.stream().map(ti -> PrototypeChain.utxos.get(ti.id)).toList()))
		{
			System.out.format("Inputs cannot be verified%n");
			return false;
		}

		// Ensure transaction is above the minimium required size
		final float inputValue = calculateInputValue();
		if (inputValue < PrototypeChain.minimumTransactionSize)
		{
			System.out.format("Transaction Inputs too small: %f%n", inputValue);
			return false;
		}

		// Generate Transaction Outputs
		float leftOver = inputValue - value;
		transactionId = calculateHash();
		outputs = List.of(
			new TransactionEntry(this.recipient, value, transactionId),
			new TransactionEntry(this.sender, leftOver, transactionId)
		);

		updateUtxos(PrototypeChain.utxos);

		return true;
	}

	public void updateUtxos(@NonNull final Map<String, TransactionEntry> utxos)
	{
		inputs.forEach(ti -> utxos.remove(ti.id));
		outputs.forEach(to -> utxos.put(to.id, to));
	}

	public float calculateInputValue()
	{
		return (float)inputs.stream().mapToDouble(ti -> ti.value).sum();
	}

	public float calculateOutputValue()
	{
		return (float)outputs.stream().mapToDouble(to -> to.value).sum();
	}

	private String data()
	{
		return BlockUtils.address(sender) + BlockUtils.address(recipient) + Float.toString(value);
	}

	public String calculateHash()
	{
		sequence++;
		return BlockUtils.sha256Hex(BlockUtils.address(sender), BlockUtils.address(recipient), Float.toString(value), Integer.toString(sequence));
	}
}
