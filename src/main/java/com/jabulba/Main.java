package com.jabulba;

import com.jabulba.commands.MoveCommand;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;

public class Main
{
	public static void main(String[] args)
			throws Exception
	{
		new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MoveCommand()).buildBlocking();
	}
}
