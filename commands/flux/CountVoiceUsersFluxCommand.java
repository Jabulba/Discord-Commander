package com.jabulba.discordmanager.commands.flux;

import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jabulba.discordmanager.DiscordManagerApplication;
import com.jabulba.discordmanager.exception.ChannelNotFoundException;
import com.jabulba.discordmanager.exception.UserException;
import io.vavr.control.Try;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CountVoiceUsersFluxCommand
		extends ListenerAdapter
{
	private static final Pattern compile = Pattern.compile("(\".*?\"|\\S+)");

	private static final LoadingCache<String, ReentrantLock> processingMessageLocks = CacheBuilder.newBuilder()
			.build(CacheLoader.from((Supplier<ReentrantLock>) ReentrantLock::new));

	@Override
	public void onGuildMessageReceived(GuildMessageReceivedEvent event)
	{
		final String messageId = event.getMessageId();

		Try.ofCallable(() -> processingMessageLocks.get(messageId))
				.map(ReentrantLock::tryLock)
				.filter(Boolean::booleanValue)
				.andThen(() ->
						 {
							 final User author = event.getAuthor();
							 if (!author.isBot() && event.getMessage().getContentRaw().startsWith(DiscordManagerApplication.CHAT_PREFIX + "count"))
							 {
								 // Get the message
								 final String content = event.getMessage().getContentRaw();

								 final Matcher matcher = compile.matcher(content);
								 final List<String> parameters = new ArrayList<>();
								 while (matcher.find())
								 {
									 parameters.add(matcher.group(1).replaceAll("\"", ""));
								 }

								 String command = parameters.get(0);

								 if (command.equalsIgnoreCase(DiscordManagerApplication.CHAT_PREFIX + "countl"))
								 {
									 StringBuilder channelListing = new StringBuilder("List of available channels:\n\n");

									 sendHelpMessage(channelListing, event.getGuild());

									 event.getChannel().sendMessage(channelListing).queue();
								 }
								 else if (command.equalsIgnoreCase(DiscordManagerApplication.CHAT_PREFIX + "count"))
								 {
									 MessageChannel channel = event.getChannel();

									 try
									 {
										 Guild guild = event.getGuild();

										 long authorId = author.getIdLong();
										 final VoiceChannel destinationChannel;
										 if (parameters.size() == 2)
										 {
											 // move <destination>
											 destinationChannel = getVoiceChannel(event, parameters.get(1));
										 }
										 else
										 {
											 Optional<VoiceChannel> authorsVoiceChannel = guild.getVoiceChannels()
													 .stream()
													 .filter(voiceChannel -> voiceChannel.getMembers()
															 .stream()
															 .anyMatch(member -> member.getUser()
																						 .getIdLong() == authorId))
													 .findFirst();

											 if (authorsVoiceChannel.isPresent())
											 {
												 destinationChannel = authorsVoiceChannel.get();
											 }
											 else
											 {
												 // User has requested a move but has not yet specified any channels. List all channels available
												 StringBuilder channelListing = new StringBuilder("Hey, are you in a voice channel?\n" +
																								  "\n" +
																								  "I couldn't find you anywhere! Please specify the number or name for the channel after the command...\n" +
																								  "\n" +
																								  "List of available channels:\n");
												 sendHelpMessage(channelListing, guild);
												 throw new UserException(channelListing);
											 }
										 }

										 StringBuilder moveMessage = new StringBuilder();
										 final int numberOfMembersBeingMoved = destinationChannel.getMembers().size();

										 moveMessage.append("There are ").append(numberOfMembersBeingMoved).append(" member");

										 if (numberOfMembersBeingMoved == 1)
										 {
											 moveMessage.append("s");
										 }

										 moveMessage.append(" in \"").append(destinationChannel.getName());

										 channel.sendMessage(moveMessage).queue();
									 }
									 catch (UserException e)
									 {
										 channel.sendMessage(e.getMessage()).queue();
									 }
								 }
							 }
						 })
				.andFinally(() -> Mono.delay(Duration.ofSeconds(15)).subscribe(aLong -> processingMessageLocks.invalidate(messageId)))
				.getOrNull();
	}

	private void sendHelpMessage(StringBuilder stringBuilder, Guild guild)
	{
		guild.getVoiceChannels().stream()
				.sorted(Comparator.comparingInt(Channel::getPosition))
				.forEach(voiceChannel -> stringBuilder.append(voiceChannel.getPosition())
						.append(") ")
						.append(voiceChannel.getName())
						.append("\n"));
	}

	private VoiceChannel getVoiceChannel(GuildMessageReceivedEvent event, String parameter)
			throws ChannelNotFoundException
	{
		Optional<VoiceChannel> destinationChannel;
		try
		{
			int channelPosition = Integer.parseInt(parameter);
			destinationChannel = event.getGuild().getVoiceChannels().stream().filter(voiceChannel -> voiceChannel.getPosition() == channelPosition).findFirst();

			if (destinationChannel.isEmpty())
			{
				throw new ChannelNotFoundException("No channel found for position " + channelPosition);
			}
		}
		catch (NumberFormatException e)
		{
			destinationChannel = event.getGuild().getVoiceChannels().stream().filter(voiceChannel -> voiceChannel.getName().equals(parameter)).findFirst();

			if (destinationChannel.isEmpty())
			{
				throw new ChannelNotFoundException("No channel found with name " + parameter);
			}
		}

		return destinationChannel.get();
	}
}
