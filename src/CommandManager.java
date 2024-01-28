import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateBoostTimeEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.hc.core5.http.ParseException;
import org.jetbrains.annotations.NotNull;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CommandManager extends ListenerAdapter {

    Dotenv dotenv = Dotenv.configure().filename(".env").load();

    public static String roleAnnounceMessageId = "";
    public static String roleAnnounceMessageChannel = "";
    public static String metricsMessageChannel = "";
    public static String metricsMessageId = "";

    //These are for checking Kablooey's status
    String statusURL = dotenv.get("KABLOOEY_PING_LINK");
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    /**
     * This function runs when the bot is ready. It gets the Status URL of the bot, and then
     * gets the URL every 45 seconds. If this fails, another bot will alert us, so it can be fixed.
     *
     * @param e - The ReadyEvent listener. Activates when the bot is ready / starts up
     */
    public void onReady(ReadyEvent e) {

        // This try catch gets the information for the metrics channel message
        try {
            String[] temp2 = CommandManager.getTxt("src/messageId2.txt").split("-");
            if (temp2.length > 1){
                metricsMessageChannel = temp2[0];
                metricsMessageId = temp2[1];
            }
        }
        catch (IOException ex) { throw new RuntimeException(ex); }

        // This is for the ping to make sure the Bot is working
        scheduler.scheduleAtFixedRate(() -> {
            try {
                URLConnection conn = new URL(statusURL).openConnection();
                conn.setRequestProperty("Accept-Charset", "UTF-8");
                InputStream response = conn.getInputStream();
                response.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }, 0, 45, TimeUnit.SECONDS);

        // If we successfully got the metric channel message information, we start a scheduler that updates the info every minute
        if (!metricsMessageChannel.isEmpty()){
            scheduler.scheduleAtFixedRate(() -> {
                if (!CommandManager.metricsMessageChannel.isEmpty()) {
                    try {
                        Metrics.updateMetrics(e);
                    } catch (IOException | ParseException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                else scheduler.shutdownNow();
            }, 0, 60, TimeUnit.SECONDS);
        }

        scheduler.scheduleAtFixedRate(() -> {
            try { sendNames(e); }
            catch (IOException ignored) { }
        }, 0, 1, TimeUnit.HOURS);
    }

    /** Initializes Kablooey. Sets up all the commands.
     *  @param e - The GuildReadyEvent listener. Activates when the bot is ready / starts up
     */
    public void onGuildReady(GuildReadyEvent e) {
        assignRoles(e);

        List<CommandData> commandData = new ArrayList<>();
        OptionData channel = new OptionData(OptionType.CHANNEL, "channel", "The channel the message will appear in.", true).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.GUILD_NEWS_THREAD, ChannelType.FORUM);
        OptionData messageId = new OptionData(OptionType.STRING, "message_id", "The ID of the message to edit.", true);
        OptionData message = new OptionData(OptionType.STRING, "message", "The message you want to send.", false);
        OptionData sendAsBot = new OptionData(OptionType.BOOLEAN, "send_as_bot", "Do you want the message to be from you or the bot?", false);
        OptionData mention = new OptionData(OptionType.MENTIONABLE, "mention", "The group you want to notify with the message.", false);
        OptionData attachment = new OptionData(OptionType.ATTACHMENT, "attachment", "The image / gif in the embed.", false);
        commandData.add(Commands.slash("announce", "Sends an announcement to a specified channel.").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VIEW_AUDIT_LOGS)).addOptions(channel, message, sendAsBot, mention, attachment));
        commandData.add(Commands.slash("edit_announcement", "Edits an announcement.").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VIEW_AUDIT_LOGS)).addOptions(channel, messageId, message, attachment));
        commandData.add(Commands.slash("role_assigner", "An announcement that should only be made once. Lets people choose roles.").setDefaultPermissions(DefaultMemberPermissions.DISABLED).addOptions(channel, message, sendAsBot, mention, attachment));
        commandData.add(Commands.slash("metrics", "Creates a message that displays the current game metrics. Should only be made once.").setDefaultPermissions(DefaultMemberPermissions.DISABLED).addOptions(channel));
        commandData.add(Commands.slash("delete_metrics", "Deletes the metrics message and the messageId2.txt file so you can repost it.").setDefaultPermissions(DefaultMemberPermissions.DISABLED));
        e.getGuild().updateCommands().addCommands(commandData).queue();
    }

    HashMap<String, String> getRole = new HashMap<>() {{
        put(":sitekick:1024144049868898334", "Events");
        put(":mecharm:644016015377956874", "Collections");
        put(":excited:756931821794754691", "Updates");
        put(":frantic:644015153523851264", "Polls");
    }};

    /** The message reaction listener. This is used for letting users get roles for themselves.
     *  @param r - MessageReactionAddEvent listener. Activates when a user adds a reaction
     */
    public void onMessageReactionAdd(MessageReactionAddEvent r) {
        if (r.getUser().isBot()) return;
        if (!r.getMessageId().equals(roleAnnounceMessageId)) return;
        String roleToAdd = getRole.get(":" + r.getReaction().getEmoji().getAsReactionCode());
        if (roleToAdd != null)
            r.getGuild().addRoleToMember(UserSnowflake.fromId(r.getUserId()), r.getGuild().getRolesByName(roleToAdd, true).get(0)).queue();
    }

    /** The message remove reaction listener. This is used for letting users remove roles for themselves.
     *  @param r - onMessageReactionRemove listener. Activates when a user removes a reaction
     */
    public void onMessageReactionRemove(MessageReactionRemoveEvent r) {
        if (!r.getMessageId().equals(roleAnnounceMessageId)) return;
        String roleToRemove = getRole.get(":" + r.getReaction().getEmoji().getAsReactionCode());
        if (roleToRemove != null)
            r.getGuild().removeRoleFromMember(UserSnowflake.fromId(r.getUserId()), r.getGuild().getRolesByName(roleToRemove, true).get(0)).queue();
    }

    /** Gets the message id from a file.
     *  @param path - The path of the file to read.
     */
    public static String getTxt(String path) throws IOException {
        String returnLine = "";
        File messageId = new File(path);
        if (messageId.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(messageId));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isEmpty()) returnLine = line;
            }
            br.close();
        }
        return returnLine;
    }

    /** Assigns roles to a user from the reactions of the reaction message
     *  @param e - GuildReadyEvent listener - this activates whenever the bot successfully connects to the guild.
     */
    public void assignRoles(GuildReadyEvent e){

        // Try to get the reaction message information from the text file.
        try {
            String[] temp = getTxt("src/messageId1.txt").split("-");
            if (temp.length > 1){
                roleAnnounceMessageChannel = temp[0];
                roleAnnounceMessageId = temp[1];
            }
        }
        catch (IOException ex) { throw new RuntimeException(ex); }

        // If we have the message, loop through everyone who has reacted to that message.
        // Depending on the reaction, add the reaction if they don't already have it.
        // Role information is retrieved from the HashMap.
        if (!roleAnnounceMessageId.isEmpty()){
            e.getJDA().getTextChannelById(roleAnnounceMessageChannel).retrieveMessageById(roleAnnounceMessageId).queue(m -> {
                for (MessageReaction r : m.getReactions()){
                    String rtaStr = getRole.get(":" + r.getEmoji().getAsReactionCode());
                    if (rtaStr != null){
                        Role roleToAdd = r.getGuild().getRolesByName(rtaStr, true).get(0);
                        r.retrieveUsers().queue(users -> {
                            for (User u : users){
                                Member member = e.getGuild().getMemberById(u.getId());
                                if (member != null && !u.isBot() && roleToAdd != null && !member.getRoles().contains(roleToAdd)){
                                    e.getGuild().addRoleToMember(u, roleToAdd).queue();
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    /** Updates the nitro user roles when the server is boosted.
     *  @param e - GuildMemberUpdateBoostTimeEvent listener - Listens for when a user boosts the server.
     */
    public void onGuildMemberUpdateBoostTime(@NotNull GuildMemberUpdateBoostTimeEvent e) {
        try { sendNames(e); }
        catch (IOException ignored) { }
    }

    /** Sends a list of all users with the Nitro Role to the server.
     *  This is used so users in the game can have the nitro emblem on SK-TV
     *  @param e - Event listener - Generic event listener.
     */
    public void sendNames(Event e) throws IOException {

        // Create a list of all users who have boosted the server via their Nitro role.
        String boosters;
        List<Member> members = new ArrayList<>();
        for(Member m : e.getJDA().getGuilds().get(0).loadMembers().get()){
            if (m.getRoles().contains(e.getJDA().getGuilds().get(0).getBoostRole())){
                members.add(m);
            }
        }

        // If there are no members with the Nitro role, we make the send message 0.
        if (members.isEmpty()){
            boosters = "0";
        }

        // If there are members, we get each member's ID, then add it to the boosters String.
        else{
            List<String> tempList = new ArrayList<>();
            for(Member m : members){
                tempList.add(m.getUser().getId());
            }
            boosters = tempList.stream().map(Object::toString).collect(Collectors.joining(","));
        }

        // Send off the information to the server.
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost("https://game.sitekickremastered.com/admin/update_discord_nitro");
        List<NameValuePair> params = new ArrayList<>(2);
        params.add(new BasicNameValuePair("token", "3e83b13d99bf0de6c6bde5ac5ca4ae687a3d46db"));
        params.add(new BasicNameValuePair("ids", boosters));
        httppost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        CloseableHttpResponse response = httpclient.execute(httppost);
        httpclient.close();
        response.close();
    }

    /** The hub for all slash commands for Kablooey.
     *  @param e - The SlashCommandInteractionEvent listener. Activates this function whenever it hears a slash command
     */
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e){
        String command = e.getName();

        // If the user types the command /announce or /role_assigner, we break down each component, then send it
        // to the announceCommand function.
        if (command.equals("announce") || command.equals("role_assigner")) {
            String message = e.getOption("message") == null ? null : e.getOption("message").getAsString();
            boolean sendAsBot = e.getOption("send_as_bot") == null || e.getOption("send_as_bot").getAsBoolean();
            IMentionable mention = e.getOption("mention") == null ? null : e.getOption("mention").getAsMentionable();
            Message.Attachment attachment = e.getOption("attachment") == null ? null : e.getOption("attachment").getAsAttachment();
            Announce.announceCommand(e, command, e.getOption("channel").getAsChannel(), message, sendAsBot, mention, attachment, getRole);
        }
        if (command.equals("edit_announcement")){
            String messageId = e.getOption("message_id") == null ? null : e.getOption("message_id").getAsString();
            String message = e.getOption("message") == null ? null : e.getOption("message").getAsString();
            Message.Attachment attachment = e.getOption("attachment") == null ? null : e.getOption("attachment").getAsAttachment();
            Announce.editAnnouncement(e, e.getOption("channel").getAsChannel(), messageId, message, attachment);
        }

        // If the user types the command /metrics, we go to the metricsCommand function
        if (command.equals("metrics")){
            try {
                Metrics.metricsCommand(e, e.getOption("channel").getAsChannel());
            } catch (IOException | ParseException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        // If the user types the command /delete_metrics, then we get the message's channel and ID and delete it, then reset variables.
        if (command.equals("delete_metrics")){
            if (!metricsMessageChannel.isEmpty() && !metricsMessageId.isEmpty()){
                e.getGuild().getTextChannelById(metricsMessageChannel).retrieveMessageById(metricsMessageId).queue(m -> {
                    m.delete().queue();
                    metricsMessageChannel = "";
                    metricsMessageId = "";
                });
                File messageId2 = new File("src/messageId2.txt");
                if (messageId2.delete()) e.reply("Deleted metrics message and messageId2.txt successfully.").setEphemeral(true).queue();
                else e.reply("Failed to delete metrics file. Please delete it manually if the message was successfully deleted.").setEphemeral(true).queue();
            }
            else e.reply("Metrics message does not seem to exist!").setEphemeral(true).queue();
        }
    }

    /** Saves a string to a file.
     *  @param path - The path of the txt file where we will save the string n to.
     *  @param n - The string that we want to write to the file
     */
    public static void save(String path, String n) throws IOException {
        File messageId = new File(path);
        BufferedWriter log = new BufferedWriter(new FileWriter(messageId));
        log.write(n);
        log.close();
    }
}
