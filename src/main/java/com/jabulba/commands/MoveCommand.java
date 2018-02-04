package com.jabulba.commands;

import com.jabulba.exception.ChannelNotFoundException;
import com.jabulba.util.EthemeralRequest;
import com.jabulba.util.Randomizer;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.GuildController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MoveCommand
		extends ListenerAdapter
{
	private static final Pattern compile = Pattern.compile("(\".*?\"|\\S+)");

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		User author = event.getAuthor();

		// Ignore bots
		if (author.isBot())
		{
			return;
		}

		Guild guild = event.getGuild();
		String content = event.getMessage().getContentRaw();

		boolean isMoveCommand = content.startsWith("!move");

		// Only answer to commands starting with '!move'
		if (!isMoveCommand)
		{
			return;
		}

		MessageChannel channel = event.getChannel();
		GuildController controller = guild.getController();

		Matcher matcher = compile.matcher(content);
		List<String> parameters = new ArrayList<>();
		while (matcher.find())
		{
			parameters.add(matcher.group(1).replaceAll("\"", ""));
		}

		try
		{
			final VoiceChannel originChannel;
			final VoiceChannel destinationChannel;

			if (parameters.size() == 2)
			{
				// !move <origin>
				destinationChannel = getVoiceChannel(event, parameters.get(1));

				Optional<VoiceChannel> authorsVoiceChannel = guild.getVoiceChannels()
																  .stream()
																  .filter(voiceChannel -> voiceChannel.getMembers()
																									  .stream()
																									  .anyMatch(member -> member.getUser().getId().equals(author.getId())))
																  .findFirst();
				if (authorsVoiceChannel.isPresent())
				{
					if (authorsVoiceChannel.get().equals(destinationChannel))
					{
						channel.sendMessage("Origin and Destination channels are the same! ").queue();
						return;
					}
					else
					{
						originChannel = authorsVoiceChannel.get();
					}
				}
				else
				{
					return;
				}
			}
			else if (parameters.size() == 3)
			{
				// !move <origin> <destination>
				originChannel = getVoiceChannel(event, parameters.get(1));

				destinationChannel = getVoiceChannel(event, parameters.get(2));
			}
			else
			{
				// Any other !move command
				sendHelpMessage(author, guild, channel);
				return;
			}

			moveMembers(channel, controller, originChannel, destinationChannel);
		}
		catch (ChannelNotFoundException e)
		{
			channel.sendMessage(e.getMessage()).queue();
		}
	}

	private void sendHelpMessage(User author, Guild guild, MessageChannel channel)
	{
		// User has requested a move but has not yet specified any channels. We will now list all channels available and the user must type both in chatMessage
		StringBuilder channelListing = new StringBuilder("You may use the bot with the following commands:\n\nView this help and the list of available channels\n\t!move\n\nMove all users from your channel to the <integer:destination channel>\n\t!move <integer:destination channel>\n\nMove all users from <integer:origin channel> to the <integer:destination channel>\n\t!move <integer:origin channel> <integer:destination channel>\n\nList of available channels:\n");
		guild.getVoiceChannels()
			 .stream()
			 .sorted(Comparator.comparingInt(Channel::getPosition))
			 .forEach(voiceChannel -> channelListing.append(voiceChannel.getPosition())
													.append(") ")
													.append(voiceChannel.getName())
													.append("\n"));

		channel.sendMessage(channelListing).queue(); // Important to call .queue() on the RestAction returned by sendMessage(...)
		EthemeralRequest.createUserRequest(author, guild);
	}

	private void moveMembers(MessageChannel channel, GuildController controller, VoiceChannel originChannel, VoiceChannel destinationChannel)
	{
		List<Member> originChannelMembers = originChannel.getMembers();
		StringBuilder moveMessage = new StringBuilder();

		if (!originChannelMembers.isEmpty())
		{
			int numberOfMembersBeingMoved = originChannelMembers.size();

			moveMessage.append("Moving ")
					   .append(numberOfMembersBeingMoved)
					   .append(" member");

			if (numberOfMembersBeingMoved == 1)
			{
				moveMessage.append("s");
			}

			moveMessage.append(" from \"")
					   .append(originChannel.getName())
					   .append("\" to \"")
					   .append(destinationChannel.getName())
					   .append("\" ASAP!");

			originChannelMembers.stream().forEach(member -> controller.moveVoiceMember(member, destinationChannel).queue());
		}
		else
		{
			moveMessage.append(Randomizer.nextInt(9000))
					   .append(" ghosts have been moved!");
		}

		channel.sendMessage(moveMessage).queue();
	}

	private VoiceChannel getVoiceChannel(GuildMessageReceivedEvent event, String parameter)
			throws ChannelNotFoundException
	{
		Optional<VoiceChannel> destinationChannel;
		try
		{
			int channelPosition = Integer.parseInt(parameter);
			destinationChannel = event.getGuild().getVoiceChannels()
									  .stream()
									  .filter(voiceChannel -> voiceChannel.getPosition() == channelPosition)
									  .findFirst();

			if (!destinationChannel.isPresent())
			{
				throw new ChannelNotFoundException("No channel found for position " + channelPosition);
			}
		}
		catch (NumberFormatException e)
		{
			destinationChannel = event.getGuild().getVoiceChannels()
									  .stream()
									  .filter(voiceChannel -> voiceChannel.getName().equals(parameter))
									  .findFirst();

			if (!destinationChannel.isPresent())
			{
				throw new ChannelNotFoundException("No channel found with name " + parameter);
			}
		}

		return destinationChannel.get();
	}
}
