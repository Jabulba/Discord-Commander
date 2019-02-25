package com.jabulba.discordmanager.commands;

import com.jabulba.discordmanager.DiscordManagerApplication;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageHistory;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GenericGuildMessageEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.restaction.pagination.MessagePaginationAction;
import net.dv8tion.jda.core.utils.MiscUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PurgeTextChannelCommand
		extends ListenerAdapter
{
	private static final Pattern compile = Pattern.compile("(\".*?\"|\\S+)");

	@Override
	public void onGuildMessageUpdate(GuildMessageUpdateEvent event)
	{
		User author = event.getAuthor();
		String content = event.getMessage().getContentRaw();

		//TODO: make cancelable & use flux
		//		command(content, author, event);
	}

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		User author = event.getAuthor();
		String content = event.getMessage().getContentRaw();

		//TODO: make cancelable & use flux
		//		command(content, author, event);
	}

	public void command(String content, User author, GenericGuildMessageEvent event)
	{
		if (!author.isBot() && content.startsWith(DiscordManagerApplication.CHAT_PREFIX + "purge"))
		{
			return;
		}

		final TextChannel channel = event.getChannel();
		final Guild guild = event.getGuild();

		final Matcher matcher = compile.matcher(content);
		final List<String> parameters = new ArrayList<>();
		while (matcher.find())
		{
			parameters.add(matcher.group(1).replaceAll("\"", ""));
		}

		if (parameters.size() == 2 && parameters.get(1).equalsIgnoreCase("true"))
		{
			if (!guild.getMember(author).getPermissions(channel).contains(Permission.MESSAGE_MANAGE))
			{
				final StringBuilder channelListing = new StringBuilder("You don't have permission to manage messages in this channel!");
				channel.sendMessage(channelListing).submit();

				return;

			}

			bulkDeleteMessages(channel.getHistory(), channel, author);
		}
		else
		{
			sendHelpMessage(channel);
		}
	}

	private void bulkDeleteMessages(MessageHistory history, TextChannel channel, User author)
	{
		long twoWeeksAgo = ((System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000)) - MiscUtil.DISCORD_EPOCH) << MiscUtil.TIMESTAMP_OFFSET;

		history.retrievePast(100)
				.queue(messages ->
					   {
						   final List<Message> collect = messages.parallelStream()
								   .filter(message -> !message.isPinned())
								   .filter(message -> MiscUtil.parseSnowflake(message.getId()) > twoWeeksAgo)
								   .collect(Collectors.toList());

						   if (collect.isEmpty())
						   {
							   int i = 0;
							   final MessagePaginationAction iterableHistory = channel.getIterableHistory();
							   for (Message oldMessage : iterableHistory)
							   {
								   i += deleteMessage(oldMessage);
							   }

							   if (i == 0)
							   {
								   channel.sendMessage(author.getName() + " all messages have been removed!")
										   .queue(scheduleTotalMessage -> scheduleTotalMessage.delete().queueAfter(15, TimeUnit.SECONDS));
							   }
							   else
							   {
								   channel.sendMessage(author.getName() + " a total of " + i + " messages have been scheduled and are being removed!")
										   .queue(scheduleTotalMessage -> scheduleTotalMessage.delete().queueAfter(1, TimeUnit.MINUTES));
							   }
							   return;
						   }

						   channel.deleteMessages(collect)
								   .queue(aVoid -> bulkDeleteMessages(history, channel, author));
					   });

	}

	private int deleteMessage(Message message)
	{
		if (message.isPinned())
		{
			return 0;
		}

		message.delete().queue();
		return 1;
	}

	private void sendHelpMessage(TextChannel channel)
	{
		StringBuilder channelListing = new StringBuilder("For safety you must type '>purge true' and not only '!clear'.");
		channel.sendMessage(channelListing).queue();
	}
}
