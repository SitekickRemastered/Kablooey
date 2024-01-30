import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import org.apache.hc.core5.http.ParseException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Metrics {

    static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** Makes an embed message that contains game metrics in a specific channel.
     *  @param e - The SlashCommandInteractionEvent listener. Activates this function whenever it hears a slash command.
     *  @param channel - The channel that we will send the metrics message to (usually just the #metrics channel).
     */
    public static void metricsCommand(SlashCommandInteractionEvent e, GuildChannelUnion channel) throws IOException, ParseException, InterruptedException {
        EmbedBuilder tempEmbed = new EmbedBuilder();
        createEmbed(tempEmbed);

        // If the message already exists, alert the user and return
        if (!CommandManager.metricsMessageId.isEmpty()) {
            e.reply("Hey, " + e.getUser().getAsMention() + "! This message already exists! Its ID is: " + CommandManager.metricsMessageId).setEphemeral(true).queue();
            return;
        }

        // If it doesn't exist, Send the message to the channel, and save the information into the CommandManager variables (and the messageId2 txt file).
        // Start the scheduler to update the message after.
        channel.asGuildMessageChannel().sendMessageEmbeds(tempEmbed.build()).queue(m -> {
            try {
                CommandManager.save("src/messageId2.txt", channel.getId() + "-" + m.getId());
                CommandManager.metricsMessageChannel = channel.getId();
                CommandManager.metricsMessageId = m.getId();

                scheduler.scheduleAtFixedRate(() -> {
                    if (!CommandManager.metricsMessageChannel.isEmpty()) {
                        try {
                            Metrics.updateMetrics(m);
                        } catch (IOException | ParseException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    else scheduler.shutdownNow();
                }, 0, 60, TimeUnit.SECONDS);
            } catch (IOException ex) { throw new RuntimeException(ex); }
        });
        tempEmbed.clear();
        e.deferReply().queue(m -> m.deleteOriginal().queue());
    }

    /** Updates the metrics message via a ReadyEvent. Used when the bot is ready
     *  @param e - ReadyEvent listener - Activates when the bot is active
     */
    public static void updateMetrics(ReadyEvent e) throws IOException, ParseException {

        // Get the message and create a new embed with the new information.
        Objects.requireNonNull(e.getJDA().getTextChannelById(CommandManager.metricsMessageChannel)).retrieveMessageById(CommandManager.metricsMessageId).queue(m -> {
            EmbedBuilder tempEmbed = new EmbedBuilder();
            try { createEmbed(tempEmbed); }
            catch (IOException | ParseException ex) {
                throw new RuntimeException(ex);
            }
            m.editMessageEmbeds(tempEmbed.build()).queue();
            tempEmbed.clear();
        });
    }

    /** Updates the metrics message via a message. Used when the metrics message is first created.
     *  @param m - The metrics message itself.
     */
    public static void updateMetrics(Message m) throws IOException, ParseException {
        EmbedBuilder tempEmbed = new EmbedBuilder();
        try { createEmbed(tempEmbed); }
        catch (IOException | ParseException ex) {
            throw new RuntimeException(ex);
        }
        m.editMessageEmbeds(tempEmbed.build()).queue();
        tempEmbed.clear();
    }

    /** Creates the metrics embed.
     *  @param eb - EmbedBuilder eb - The embed message that we edit for the message.
     */
    public static void createEmbed(EmbedBuilder eb) throws IOException, ParseException {
        eb.setTitle("__**Sitekick Remastered Game Metrics**__");
        eb.setColor(0x007AFE); // Kablooey's blue
        eb.setTimestamp(new Date().toInstant()); //Set up the date

        // Get the metrics information from the POSTRequest function
        JSONObject json = CommandManager.postRequest("https://game.sitekickremastered.com/metrics/generic?q=online_players,daily_online_players,daily_registrations,total_players,total_chips", new ArrayList<>(), "Failed to retrieve metrics from POST");

        // On success, edit the metrics
        if (json != null){
            eb.setDescription("**Online Players:** " + json.get("online_players") +
                            "\n**Players Today:** " + json.get("daily_online_players") +
                            "\n**Registrations Today:** " + json.get("daily_registrations") +
                            "\n**Total Players:** " + json.get("total_players") +
                            "\n**Total Active Chips:** " + json.get("total_chips") +
                            "\n\nBrought to you by me <:kablrury:1036832644631117954>"
            );
            System.out.println(Instant.now() + " Updated Metrics Successfully.");
        }
        // On fail, alert that metrics is broken.
        else {
            eb.setDescription("I'm unable to retrieve metrics at this time <:nootsad:747467956308410461>");
            System.out.println(Instant.now() + " failed to get the Metrics from the POST.");
        }
    }

}
