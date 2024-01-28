import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

public class Announce {

    /** Makes an announcement to a specified channel with the pfp and user who sent said announcement.
     *  @param e - The SlashCommandInteractionEvent listener. Activates this function whenever it hears a slash command.
     *  @param command - The command that the user used - Either "announce" or "role_assigner".
     *  @param channel - The channel that we will send the message to.
     *  @param message - The message that will be sent to the channel.
     *  @param sendAsBot - Determines whether the message will be sent by Kablooey or as the user.
     *  @param mention - The mention associated with the message. Prints an @ above the embedded message.
     *  @param attachment - An image that will be put in the embedded message.
     *  @param roles - the HashMap for all the roles - This is primarily used for the role_assigner command
     */
    public static void announceCommand(SlashCommandInteractionEvent e, String command, GuildChannelUnion channel, String message, boolean sendAsBot, IMentionable mention, Message.Attachment attachment, HashMap<String, String> roles) {

        // If there was no message, alert the user and return.
        if (message == null && attachment == null){
            e.reply("You need to add a message or attachment!").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();

        //Set colour of sidebar (Kablooey's Blue)
        eb.setColor(0x007AFE);

        //If the user does not want to send it as a bot, then it will display their name and pfp
        if (!sendAsBot) {
            eb.setAuthor(e.getMember().getUser().getEffectiveName(), null, e.getMember().getUser().getAvatarUrl());
            eb.setColor(0x660199); //Set the colour to purple
        }

        // Set the description in the embed to the message
        if (message != null) {
            message = message.replace("\\n", "\n");
            eb.setDescription(message);
        }

        //Set up the date
        eb.setTimestamp(new Date().toInstant());

        //If the user typed a specific role, mention that role before sending the message
        if (mention != null) channel.asGuildMessageChannel().sendMessage(mention.getAsMention()).queue();

        //If the user added an attachment
        if (attachment != null) { eb.setImage(attachment.getUrl()); }

        // If the role_assigner command was used, we check if the role announce message exists
        // If it does alert the user, otherwise, mention everyone, then send the message and add reactions
        // Save the messageId and channel into the text file messageId1 too.
        if (command.equals("role_assigner")) {
            if (!CommandManager.roleAnnounceMessageId.equals("")) {
                e.reply("Hey, " + e.getUser().getAsMention() + "! This message already exists! Its ID is: " + CommandManager.roleAnnounceMessageId).setEphemeral(true).queue();
                return;
            }
            channel.asGuildMessageChannel().sendMessage("@everyone").queue();
            channel.asGuildMessageChannel().sendMessageEmbeds(eb.build()).queue(m -> {
                try {
                    CommandManager.save("src/messageId1.txt", channel.getId() + "-" + m.getId());
                    CommandManager.roleAnnounceMessageId = CommandManager.getTxt("src/messageId1");
                } catch (IOException ex) { throw new RuntimeException(ex); }
                for (String role : roles.keySet()) {
                    m.addReaction(Emoji.fromUnicode(role)).queue();
                }
            });
        }
        // Send the message if /announce was used instead.
        else  {
            channel.asGuildMessageChannel().sendMessageEmbeds(eb.build()).queue();
        }
        eb.clear(); //Clear the message at the end for next time
        e.deferReply().queue(m -> m.deleteOriginal().queue());
    }

    /** Makes an announcement to a specified channel with the pfp and user who sent said announcement.
     *  @param e - The SlashCommandInteractionEvent listener. Activates this function whenever it hears a slash command.
     *  @param channel - The channel of the message to edit,
     *  @param messageId - the ID of the embed that we want to edit
     *  @param message - The message to edit into the embed.
     *  @param attachment - An image that will be put in the embedded message.
     */
    public static void editAnnouncement(SlashCommandInteractionEvent e, GuildChannelUnion channel, String messageId, String message, Message.Attachment attachment){

        // Parse the message ID. Normally, IDs are in the form CHANNEL-MESSAGE, so we have to take out the CHANNEL part
        String[] tempArray = messageId.split("-");
        if (tempArray.length > 1)
            messageId = tempArray[1];

        // Get the message and check if it exists
        Message m = channel.asGuildMessageChannel().retrieveMessageById(messageId).complete();
        MessageEmbed me = m.getEmbeds().getFirst();
        if (me == null){
            e.reply("Failed to find the message. Please make sure you got the correct ID.").setEphemeral(true).queue();;
            return;
        }

        // If there was no message, alert the user and return.
        if (message == null && attachment == null){
            e.reply("You need to add a message or attachment!").setEphemeral(true).queue();
            return;
        }

        // Set the new embed to all the stuff that we're not going to edit.
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(me.getColor());

        if (me.getAuthor() != null){
            eb.setAuthor(me.getAuthor().getName(), me.getAuthor().getUrl(), me.getAuthor().getIconUrl());
        }

        // Set the description in the embed to the message
        if (message != null) {
            message = message.replace("\\n", "\n");
            eb.setDescription(message);
        }

        //Set up the date
        eb.setTimestamp(new Date().toInstant());

        //If the user added an attachment
        if (attachment != null) { eb.setImage(attachment.getUrl()); }

        // Edit the message by ID
        channel.asGuildMessageChannel().editMessageEmbedsById(messageId, eb.build()).queue();

        eb.clear(); //Clear the message at the end for next time
        e.deferReply().queue(m2 -> m2.deleteOriginal().queue());
    }
}