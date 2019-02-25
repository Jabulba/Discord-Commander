package com.jabulba.discordmanager.commands.flux;

import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jabulba.discordmanager.DiscordManagerApplication;
import com.jabulba.discordmanager.exception.ChannelNotFoundException;
import com.jabulba.discordmanager.exception.LackOfPermissionException;
import com.jabulba.discordmanager.exception.UserException;
import com.jabulba.discordmanager.util.Randomizer;
import io.vavr.control.Try;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Channel;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.GuildController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MassMoveUsersFluxCommand
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
							 if (!author.isBot() && event.getMessage().getContentRaw().startsWith(DiscordManagerApplication.CHAT_PREFIX + "move"))
							 {
								 MessageChannel channel = event.getChannel();

								 try
								 {
									 // Get the message
									 final String content = event.getMessage().getContentRaw();

									 final Matcher matcher = compile.matcher(content);
									 final List<String> parameters = new ArrayList<>();
									 while (matcher.find())
									 {
										 parameters.add(matcher.group(1).replaceAll("\"", ""));
									 }

									 Guild guild = event.getGuild();
									 GuildController controller = guild.getController();

									 long authorId = author.getIdLong();
									 final VoiceChannel originChannel;
									 final VoiceChannel destinationChannel;
									 if (parameters.size() == 2)
									 {
										 // move <destination>
										 destinationChannel = getVoiceChannel(event, parameters.get(1));

										 Optional<VoiceChannel> authorsVoiceChannel = guild.getVoiceChannels()
												 .stream()
												 .filter(voiceChannel -> voiceChannel.getMembers().stream().anyMatch(member -> member.getUser().getIdLong() == authorId))
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
											 //TODO: show warning that user was not found in a voice channel
											 return;
										 }
									 }
									 else if (parameters.size() == 3)
									 {
										 // move <origin> <destination>
										 originChannel = getVoiceChannel(event, parameters.get(1));

										 destinationChannel = getVoiceChannel(event, parameters.get(2));
									 }
									 else
									 {
										 // Any other move command
										 sendHelpMessage(guild, channel);
										 return;
									 }

									 final Member selfMember = guild.getSelfMember();
									 if (!selfMember.getPermissions(originChannel).contains(Permission.VOICE_MOVE_OTHERS))
									 {
										 throw new LackOfPermissionException(selfMember.getEffectiveName() + " does not have the permission to move members from " + originChannel.getName());
									 }
									 else if (!selfMember.getPermissions(destinationChannel).contains(Permission.VOICE_MOVE_OTHERS))
									 {
										 throw new LackOfPermissionException(selfMember.getEffectiveName() + " does not have the permission to move members to " + originChannel.getName());
									 }

									 final boolean hasOriginChannelPermission = guild.getMemberById(authorId).getPermissions(originChannel).contains(Permission.VOICE_MOVE_OTHERS);
									 final boolean hasDestinationChannelPermission = guild.getMemberById(authorId).getPermissions(destinationChannel).contains(Permission.VOICE_MOVE_OTHERS);
									 if (!hasOriginChannelPermission
										 || !hasDestinationChannelPermission)
									 {

										 if (Randomizer.chancePercent(10))
										 {
											 throwPermissionDeniedException("Dear me! You don't have permission to move others ", originChannel.getName(), destinationChannel.getName(), "?", hasOriginChannelPermission);
										 }
										 else if (Randomizer.chancePercent(4))
										 {
											 throwPermissionDeniedException("I'm trying real hard here, but you just fail and fail, again and again... Are you sure you have the necessary permission to move others ",
																			originChannel.getName(), destinationChannel.getName(), "?", hasOriginChannelPermission);
										 }

										 throwPermissionDeniedException("You lack permission to move others ",
																		originChannel.getName(), destinationChannel.getName(), "!", hasOriginChannelPermission);
									 }

									 StringBuilder moveMessage = new StringBuilder();
									 final List<Member> membersToMove = originChannel.getMembers();
									 if (!membersToMove.isEmpty())
									 {
										 int numberOfMembersBeingMoved = membersToMove.size();

										 moveMessage.append("Moving ").append(numberOfMembersBeingMoved).append(" member");

										 if (numberOfMembersBeingMoved == 1)
										 {
											 moveMessage.append("s");
										 }

										 moveMessage.append(" from \"").append(originChannel.getName()).append("\" to \"").append(destinationChannel.getName()).append("\" ASAP!");
									 }
									 else
									 {
										 moveMessage.append(Randomizer.nextInt(9000)).append(" ghosts have been moved!");
									 }
									 channel.sendMessage(moveMessage).queue();

									 final String guildId = controller.getGuild().getId();
									 Flux.fromStream(membersToMove.parallelStream())
											 .parallel()
											 .subscribe(member -> Try.ofCallable(() -> DiscordManagerApplication.getNextAvailableBot(guildId).getJda().getGuildById(guildId).getController())
													 .andThen(guildController -> guildController.moveVoiceMember(member, destinationChannel).submit())
													 .get());
								 }
								 catch (UserException e)
								 {
									 channel.sendMessage(e.getMessage()).queue();
								 }
							 }
						 })
				.andFinally(() -> Mono.delay(Duration.ofSeconds(15)).subscribe(aLong -> processingMessageLocks.invalidate(messageId)))
				.getOrNull();
	}

	private void throwPermissionDeniedException(String prefix, String originChannelName, String destinationChannelName, String sufix, boolean hasOriginChannelPermission)
			throws LackOfPermissionException
	{
		final StringBuilder message = new StringBuilder(prefix);
		if (!hasOriginChannelPermission)
		{
			message.append("from ").append(originChannelName);
		}
		else
		{
			message.append("to ").append(destinationChannelName);
		}

		message.append(sufix);

		throw new LackOfPermissionException(message.toString());
	}

	private void sendHelpMessage(Guild guild, MessageChannel channel)
	{
		// User has requested a move but has not yet specified any channels. List all channels available
		StringBuilder channelListing = new StringBuilder("You may use the bot with the following commands:\n" +
														 "\n" +
														 "View this help and the list of available channels\n" +
														 "\t!move\n" +
														 "\n" +
														 "Move all users from your channel to the <integer:destination channel>\n" +
														 "\t!move <integer:destination channel>\n" +
														 "\n" +
														 "Move all users from <integer:origin channel> to the <integer:destination channel>\n" +
														 "\t!move <integer:origin channel> <integer:destination channel>\n" +
														 "\n" +
														 "List of available channels:\n");

		guild.getVoiceChannels().stream().sorted(Comparator.comparingInt(Channel::getPosition)).forEach(voiceChannel -> channelListing.append(voiceChannel.getPosition()).append(") ").append(voiceChannel.getName()).append("\n"));

		channel.sendMessage(channelListing).queue();
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
