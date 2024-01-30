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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    public static ArrayList<String> rankList = new ArrayList<>() { { add("None"); add("Bronze"); add("Silver"); add("Gold"); add("Amethyst"); add("Onyx"); add("Diamond"); } };

    //These are for checking Kablooey's status
    String statusURL = dotenv.get("KABLOOEY_PING_LINK");
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    /**
     * This function runs when the bot is ready. It gets the Status URL of the bot, and then
     * gets the URL every 45 seconds. If this fails, another bot will alert us, so it can be fixed.
     *
     * @param e - The ReadyEvent listener. Activates when the bot is ready / starts up
     */
    public void onReady(@NotNull ReadyEvent e) {

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

        // Nitro and Rank / Name change stuff.
        scheduler.scheduleAtFixedRate(() -> {
            try { sendNames(e); }
            catch (IOException | ParseException ignored) { }

            try { updateUsers(e); }
            catch (IOException | ParseException ex) { throw new RuntimeException(ex); }
        }, 0, 1, TimeUnit.HOURS);
    }

    /** Initializes Kablooey. Sets up all the commands.
     *  @param e - The GuildReadyEvent listener. Activates when the bot is ready / starts up
     */
    public void onGuildReady(@NotNull GuildReadyEvent e) {
        assignRoles(e);

        List<CommandData> commandData = new ArrayList<>();
        OptionData channel = new OptionData(OptionType.CHANNEL, "channel", "The channel the message will appear in.", true).setChannelTypes(ChannelType.TEXT, ChannelType.NEWS, ChannelType.GUILD_PUBLIC_THREAD, ChannelType.GUILD_NEWS_THREAD, ChannelType.FORUM);
        OptionData messageId = new OptionData(OptionType.STRING, "message_id", "The ID of the message to edit.", true);
        OptionData message = new OptionData(OptionType.STRING, "message", "The message you want to send.", false);
        OptionData sendAsBot = new OptionData(OptionType.BOOLEAN, "send_as_bot", "Do you want the message to be from you or the bot?", false);
        OptionData mention = new OptionData(OptionType.MENTIONABLE, "mention", "The group you want to notify with the message.", false);
        OptionData attachment = new OptionData(OptionType.ATTACHMENT, "attachment", "The image / gif in the embed.", false);
        commandData.add(Commands.slash("announce", "Sends an announcement to a specified channel.").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VIEW_AUDIT_LOGS)).addOptions(channel, message, sendAsBot, mention, attachment));
        commandData.add(Commands.slash("edit_announcement", "Edits an announcement.").setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.VIEW_AUDIT_LOGS)).addOptions(messageId, message, attachment));
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
        if (Objects.requireNonNull(r.getUser()).isBot()) return;
        if (!r.getMessageId().equals(roleAnnounceMessageId)) return;
        String roleToAdd = getRole.get(":" + r.getReaction().getEmoji().getAsReactionCode());
        if (roleToAdd != null)
            r.getGuild().addRoleToMember(UserSnowflake.fromId(r.getUserId()), r.getGuild().getRolesByName(roleToAdd, true).getFirst()).queue();
    }

    /** The message remove reaction listener. This is used for letting users remove roles for themselves.
     *  @param r - onMessageReactionRemove listener. Activates when a user removes a reaction
     */
    public void onMessageReactionRemove(MessageReactionRemoveEvent r) {
        if (!r.getMessageId().equals(roleAnnounceMessageId)) return;
        String roleToRemove = getRole.get(":" + r.getReaction().getEmoji().getAsReactionCode());
        if (roleToRemove != null)
            r.getGuild().removeRoleFromMember(UserSnowflake.fromId(r.getUserId()), r.getGuild().getRolesByName(roleToRemove, true).getFirst()).queue();
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
            Objects.requireNonNull(e.getJDA().getTextChannelById(roleAnnounceMessageChannel)).retrieveMessageById(roleAnnounceMessageId).queue(m -> {
                for (MessageReaction r : m.getReactions()){
                    String rtaStr = getRole.get(":" + r.getEmoji().getAsReactionCode());
                    if (rtaStr != null){
                        Role roleToAdd = r.getGuild().getRolesByName(rtaStr, true).getFirst();
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
        catch (IOException | ParseException ignored) { }
    }

    /** Sends a list of all users with the Nitro Role to the server.
     *  This is used so users in the game can have the nitro emblem on SK-TV
     *  @param e - Event listener - Generic event listener.
     */
    public void sendNames(Event e) throws IOException, ParseException {

        // Create a list of all users who have boosted the server via their Nitro role.
        String boosters;
        List<Member> members = new ArrayList<>();
        for(Member m : e.getJDA().getGuilds().getFirst().loadMembers().get()){
            if (m.getRoles().contains(e.getJDA().getGuilds().getFirst().getBoostRole())){
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
        List<NameValuePair> params = new ArrayList<>(2);
        params.add(new BasicNameValuePair("token", dotenv.get("POST_TOKEN")));
        params.add(new BasicNameValuePair("ids", boosters));
        postRequest(dotenv.get("NITRO_LINK"), params, "Failed to get the list of Nitro Users");
    }

    public void updateUsers(Event e) throws IOException, ParseException {

        // Get the Sitekick channel
        Guild SK = e.getJDA().getGuildById("603580736250970144");

        Role verifiedRole = SK.getRolesByName("Verified", true).get(0);

        ArrayList<String> roleList = new ArrayList<>() { { add("Administrator" ); add("Developer" ); add("Moderator"); } };

        // Set up the first post
        List<NameValuePair> params = new ArrayList<>(1);
        params.add(new BasicNameValuePair("token", dotenv.get("POST_TOKEN")));
        JSONObject json = postRequest(dotenv.get("VERIFIED_MEMBERS_LINK"), params, "Failed to get the Verified Members List");
        JSONArray players = json.getJSONArray("players");
        System.out.println("\n" + Instant.now() + " Retrieved Page 1 of " + json.getInt("totalPages") + " from the verified members link successfully.");

        // Go through every page starting at 1 since we already got page 0 from the first thing
        for (int i = 1; i < json.getInt("totalPages"); i++){

            // Go through every player on this page.
            for (int j = 0; j < players.length(); j++){

                // Get the member from the json and check if they're still in the discord
                JSONObject member = players.getJSONObject(j);
                Member m = Objects.requireNonNull(SK).getMemberById(member.get("discordId").toString());
                if (m == null) {
                    System.out.println(Instant.now() + " Player '" + member.get("username") + "' is no longer in the discord");
                    continue;
                }

                //Set Discord name to name in game
                if (m.getRoles().stream().noneMatch(element -> roleList.contains(element.getName())) && (m.getNickname() == null || !Objects.equals(m.getNickname(), member.get("username").toString()))){
                    m.modifyNickname(member.get("username").toString()).queue(
                        (success) -> System.out.println(Instant.now() + " " + m.getUser().getName() + " Nickname changed to in game name: " + m.getNickname()),
                        (error) -> System.out.println(Instant.now() + " Failed to set new nickname for user: " + m.getUser().getName())
                    );
                }

                // Set Rank Stuff
                if (!member.get("rank").toString().equals("None")){
                    Role gameRank = SK.getRolesByName(member.get("rank").toString(), true).getFirst();

                    // If the player currently has any of the roles in the rank list and that role is not the same as their current one, remove it.
                    if (m.getRoles().stream().anyMatch(element -> rankList.contains(element.getName()))){
                        if (!m.getRoles().contains(gameRank)) {
                            for (Role r : m.getRoles()) {
                                if (rankList.contains(r.getName())) {
                                    SK.removeRoleFromMember(UserSnowflake.fromId(m.getUser().getId()), r).queue(successMessage -> System.out.println(Instant.now() + " Rank Role '" + gameRank.getName() + "' REMOVED from user: " + m.getEffectiveName()));
                                }
                            }
                        }
                    }

                    // Add their current rank from the game to the discord
                    if (!m.getRoles().contains(gameRank)) {
                        SK.addRoleToMember(m, gameRank).queue(successMessage -> System.out.println(Instant.now() + " Rank Role '" + gameRank.getName() + "' ADDED to user: " + m.getEffectiveName()));

                    }
                }

                // Add verified role if they don't have it.
                if (!m.getRoles().contains(verifiedRole)) {
                    SK.addRoleToMember(UserSnowflake.fromId(m.getUser().getId()), verifiedRole).queue(successMessage -> System.out.println(Instant.now() + " ADDED VERIFIED Role to user: " + m.getEffectiveName()));
                }
            }

            // Do another POST request for the next page.
            json = postRequest(dotenv.get("VERIFIED_MEMBERS_LINK") + "?page=" + i, params, "Failed to get the Verified Members List");
            players = json.getJSONArray("players");
            System.out.println("\n" + Instant.now() + " Retrieved Page " + (i + 1) + " of " + json.getInt("totalPages") + " from the verified members link successfully.");
        }

        System.out.println("\n" + Instant.now() + " Finished scanning the Verified Members List!");

    }

    /** The hub for all slash commands for Kablooey.
     *  @param e - The SlashCommandInteractionEvent listener. Activates this function whenever it hears a slash command
     */
    public void onSlashCommandInteraction(SlashCommandInteractionEvent e){
        String command = e.getName();

        // If the user types the command /announce or /role_assigner, we break down each component, then send it to the announceCommand function.
        if (command.equals("announce") || command.equals("role_assigner")) {
            String message = e.getOption("message") == null ? null : Objects.requireNonNull(e.getOption("message")).getAsString();
            boolean sendAsBot = e.getOption("send_as_bot") == null || Objects.requireNonNull(e.getOption("send_as_bot")).getAsBoolean();
            IMentionable mention = e.getOption("mention") == null ? null : Objects.requireNonNull(e.getOption("mention")).getAsMentionable();
            Message.Attachment attachment = e.getOption("attachment") == null ? null : Objects.requireNonNull(e.getOption("attachment")).getAsAttachment();
            Announce.announceCommand(e, command, Objects.requireNonNull(e.getOption("channel")).getAsChannel(), message, sendAsBot, mention, attachment, getRole);
        }

        // If the user types the command /edit_announcement, break down each component and send it to the edit_announcement function
        if (command.equals("edit_announcement")){
            String messageId = e.getOption("message_id") == null ? null : Objects.requireNonNull(e.getOption("message_id")).getAsString();
            String message = e.getOption("message") == null ? null : Objects.requireNonNull(e.getOption("message")).getAsString();
            Message.Attachment attachment = e.getOption("attachment") == null ? null : Objects.requireNonNull(e.getOption("attachment")).getAsAttachment();
            Announce.editAnnouncement(e, messageId, message, attachment);
        }

        // If the user types the command /metrics, we go to the metricsCommand function
        if (command.equals("metrics")){
            try {
                Metrics.metricsCommand(e, Objects.requireNonNull(e.getOption("channel")).getAsChannel());
            } catch (IOException | ParseException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        // If the user types the command /delete_metrics, then we get the message's channel and ID and delete it, then reset variables.
        if (command.equals("delete_metrics")){
            if (!metricsMessageChannel.isEmpty() && !metricsMessageId.isEmpty()){
                Objects.requireNonNull(Objects.requireNonNull(e.getGuild()).getTextChannelById(metricsMessageChannel)).retrieveMessageById(metricsMessageId).queue(m -> {
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

    /** Sends a post request to a link. Since the post request has no parameters / arguments, we just need to get the response.
     *  @param link - The link that the POST request will be sent to.
     *  @param params - The parameters to send to the HTTP request
     *  @param errorMessage - The error message to print if it fails.
     */
    public static JSONObject postRequest(String link, List<NameValuePair> params, String errorMessage) throws IOException, ParseException {

        // Setup the post request and send it.
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(link);
        httppost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        CloseableHttpResponse response = httpclient.execute(httppost);
        JSONObject json = null;

        // If we get the code 200 back (everything went OK), then populate the json variable with the information
        if (response.getCode() == 200){
            HttpEntity entity = response.getEntity();
            json = new JSONObject(EntityUtils.toString(entity, StandardCharsets.UTF_8));
            entity.close();
        }

        // Otherwise, print to the screen the code / why and where the request failed.
        else System.out.println(Instant.now() + " ERROR " + response.getCode() +": " + errorMessage);

        httpclient.close();
        response.close();

        return json;
    }
}
