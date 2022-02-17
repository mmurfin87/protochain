package com.markmurfin.blockchain.util;

public class Switch
{
	public Switch()
	{
		this(true);
	}

	public Switch(final boolean status)
	{
		this.status = status;
	}

	public boolean inspect()
	{
		return status;
	}

	public Switch switchOff()
	{
		this.status = false;
		return this;
	}

	private boolean status;
}
