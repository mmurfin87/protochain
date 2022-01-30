package com.markmurfin.blockchain;

import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BlockUtils
{
	public static String hashTarget(final int difficulty)
	{
		return new String(new char[difficulty]).replace('\0', '0');
	}

	public static String sha256Hex(@NonNull final String... inputs)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(String.join("", inputs).getBytes(StandardCharsets.UTF_8));
			return hex(hash);
		}
		catch (final NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String hex(@NonNull final byte[] data)
	{
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < data.length; i++)
		{
			String hex = Integer.toHexString(0xff & data[i]);
			if (hex.length() == 1)
				sb.append('0');
			sb.append(hex);
		}
		return sb.toString();
	}

	public static String address(@NonNull final PublicKey publicKey)
	{
		return Base64.getEncoder().encodeToString(publicKey.getEncoded());
	}

	public static KeyPair generateKeyPair()
	{
		try
		{
			final KeyPairGenerator keygen = KeyPairGenerator.getInstance("ECDSA", "BC");
			final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			final ECGenParameterSpec ecspec = new ECGenParameterSpec("prime192v1");

			keygen.initialize(ecspec, random);
			return keygen.generateKeyPair();
		}
		catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static byte[] sign(@NonNull final PrivateKey privateKey, @NonNull final String input)
	{
		try
		{
			final Signature dsa = Signature.getInstance("ECDSA", "BC");
			dsa.initSign(privateKey);
			dsa.update(input.getBytes(StandardCharsets.UTF_8));
			return dsa.sign();
		}
		catch (final NoSuchAlgorithmException | SignatureException | NoSuchProviderException | InvalidKeyException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static boolean authenticate(@NonNull final PublicKey publicKey, @NonNull final String data, @NonNull final byte[] signature)
	{
		try
		{
			final Signature sig = Signature.getInstance("ECDSA", "BC");
			sig.initVerify(publicKey);
			sig.update(data.getBytes(StandardCharsets.UTF_8));
			return sig.verify(signature);
		}
		catch (final NoSuchAlgorithmException | SignatureException | NoSuchProviderException | InvalidKeyException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static String calculateMerkleRoot(@NonNull final List<Transaction> transactions)
	{
		int count = transactions.size();
		List<String> previousTreeLayer = transactions.stream().map(t -> t.transactionId).toList();
		List<String> treeLayer = previousTreeLayer;
		while (count > 1)
		{
			treeLayer = new ArrayList<>();
			for (int i = 1; i < previousTreeLayer.size(); i++)
				treeLayer.add(sha256Hex(previousTreeLayer.get(i-1), previousTreeLayer.get(i)));
			count = treeLayer.size();
			previousTreeLayer = treeLayer;
		}
		return (treeLayer.size() == 1) ? treeLayer.get(0) : "";
	}
}
