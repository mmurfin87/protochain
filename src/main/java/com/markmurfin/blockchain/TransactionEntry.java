package com.markmurfin.blockchain;

import lombok.NonNull;
import lombok.ToString;

import java.security.PublicKey;
import java.util.Objects;

@ToString
public class TransactionEntry
{
	public final String id;
	@ToString.Exclude
	public final PublicKey recipient;
	public final float value;
	public final String parentTransactionId;	// The id of the transaction this output was created in

	public TransactionEntry(@NonNull final PublicKey recipient, final float value, @NonNull final String parentTransactionId)
	{
		this.recipient = recipient;
		this.value = value;
		this.parentTransactionId = parentTransactionId;
		this.id = BlockUtils.sha256Hex(BlockUtils.hex(recipient.getEncoded()), Float.toString(value), parentTransactionId);
	}

	public boolean isOwnedBy(@NonNull final PublicKey publicKey)
	{
		return Objects.equals(publicKey, recipient);
	}
}
