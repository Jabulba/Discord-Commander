package com.jabulba.discordmanager;

import com.jabulba.discordmanager.commands.PurgeTextChannelCommand;
import com.jabulba.discordmanager.commands.flux.CountVoiceUsersFluxCommand;
import com.jabulba.discordmanager.commands.flux.MassMoveUsersFluxCommand;
import com.jabulba.discordmanager.exception.NoBotsAvailableException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;

import javax.annotation.Nonnull;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DiscordManagerApplication
{
	private static final Queue<BotEntry> bots = new ConcurrentLinkedDeque<>();

	//TODO: Move to config file
	public static final String CHAT_PREFIX = ">";

	public static void main(String[] args)
			throws Exception
	{
		//TODO: Load Name, Token and Listenters from config file
		bots.add(new BotEntry("Albaldah", new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MassMoveUsersFluxCommand())
				.addEventListener(new PurgeTextChannelCommand())
				.addEventListener(new CountVoiceUsersFluxCommand())
				.buildAsync()));

		bots.add(new BotEntry("Ascella", new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MassMoveUsersFluxCommand())
				.addEventListener(new PurgeTextChannelCommand())
				.addEventListener(new CountVoiceUsersFluxCommand())
				.buildAsync()));

		bots.add(new BotEntry("Nunki", new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MassMoveUsersFluxCommand())
				.addEventListener(new PurgeTextChannelCommand())
				.addEventListener(new CountVoiceUsersFluxCommand())
				.buildAsync()));

		bots.add(new BotEntry("Rukbat", new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MassMoveUsersFluxCommand())
				.addEventListener(new PurgeTextChannelCommand())
				.addEventListener(new CountVoiceUsersFluxCommand())
				.buildAsync()));

		bots.add(new BotEntry("Terebellum", new JDABuilder(AccountType.BOT)
				.setToken("")
				.addEventListener(new MassMoveUsersFluxCommand())
				.addEventListener(new PurgeTextChannelCommand())
				.addEventListener(new CountVoiceUsersFluxCommand())
				.buildAsync()));
	}

	public static synchronized BotEntry getNextAvailableBot(final String guildId)
			throws NoBotsAvailableException
	{
		//TODO: Check queue sizes and return the bot with smallest queue
		int botsCount = bots.size();
		BotEntry nextBot;
		do
		{
			nextBot = bots.poll();
			bots.add(nextBot);

			if (botsCount-- == 0)
			{
				throw new NoBotsAvailableException("No bots are available for guild" + guildId);
			}
		}
		while (nextBot != null && nextBot.getJda().getGuildById(guildId) == null);

		return nextBot;
	}

	public static class BotEntry
	{
		@Nonnull
		JDA jda;

		@Nonnull
		String name;

		BotEntry(String name, JDA jda)
		{
			this.jda = jda;
			this.name = name;
		}

		public JDA getJda()
		{
			return jda;
		}

		public String getName()
		{
			return name;
		}
	}
}
