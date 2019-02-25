package com.jabulba.discordmanager.exception;

public class UserException
		extends Exception
{
	public UserException(String message)
	{
		super(message);
	}

	public UserException(StringBuilder message)
	{
		super(message.toString());
	}
}
