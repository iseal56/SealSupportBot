package dev.iseal.SSB.managers;

import de.leonhard.storage.Config;
import de.leonhard.storage.Json;
import dev.iseal.SSB.SSBMain;
import dev.iseal.SSB.listeners.ButtonClickListener;
import dev.iseal.SSB.systems.ads.modals.AdDenialModal;
import dev.iseal.SSB.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class AdDataManager {

    private static AdDataManager instance;
    public static AdDataManager getInstance() {
        if (instance == null) {
            instance = new AdDataManager();
        }
        return instance;
    }

    private final Json registeredAdUUIDs;
    private final Json cooldowns;
    private final Config config;

    private final long adChannelID;
    private final long pendingApprovalID;
    private final long adCooldownInHours;

    private AdDataManager() {
        cooldowns = new Json("adCooldowns.json", System.getProperty("user.dir")+ File.separator + "data" + File.separator + "ads");
        // scan through the cooldowns file and remove any that are older than the current time
        cooldowns.keySet().forEach(
                uuid -> {
                    long cooldown = cooldowns.getLong(uuid);
                    if (System.currentTimeMillis() > cooldown) {
                        cooldowns.remove(uuid);
                    }
                }
        );

        registeredAdUUIDs = new Json("registeredAdUUIDs.json",System.getProperty("user.dir")+ File.separator + "data" + File.separator + "ads");
        config = new Config("adConfig.yml",System.getProperty("user.dir")+ File.separator + "config");
        config.setDefault("adChannelId", "0");
        config.setDefault("pendingApprovalId", "0");
        config.setDefault("adCooldownInHours", 24);

        adChannelID = config.getLong("adChannelId");
        pendingApprovalID = config.getLong("pendingApprovalId");
        adCooldownInHours = config.getLong("adCooldownInHours");

        ButtonClickListener.getInstance().registerButtonConsumer("approveAd", this::approveAd);
        ButtonClickListener.getInstance().registerButtonConsumer("denyAd", this::denyAd);
    }


    /**
     * Adds a pending ad to the list of ads to be approved.
     * WARNING: This method assumes that userID is a valid user ID
     *
     * @param ad The ad to be added
     * @param sender the user advertising
     */
    public String addPendingAd(String ad, User sender) {

        long userID = sender.getIdLong();

        // check for pending ads
        if (registeredAdUUIDs.getData().containsValue(userID)) {
            return "You already have an ad registered. Please wait until it is approved or denied.";
        }

        // check for cooldown
        if (cooldowns.contains(String.valueOf(userID))) {
            long cooldown = cooldowns.getLong(String.valueOf(userID));
            if (System.currentTimeMillis() < cooldown) {
                return "You are on cooldown for this ad. Please wait " + (cooldown - System.currentTimeMillis()) / 1000 + " seconds. (" + (cooldown - System.currentTimeMillis()) / 1000 / 60 / 60+ " hours)";
            }
        }

        UUID adID = UUID.randomUUID();

        // add the ad to the pending ads
        registeredAdUUIDs.set(adID.toString(), userID);

        // set the cooldown
        long cooldown = System.currentTimeMillis() + adCooldownInHours * 60 * 60 * 1000;
        cooldowns.set(String.valueOf(userID), cooldown);

        // check if the channel is set
        if (pendingApprovalID == 0) {
            return "No channel set for pending ads. Please contact an admin.";
        }

        // send the ad to the channel
        TextChannel pendingChannel = SSBMain.getJDA().getTextChannelById(pendingApprovalID);

        if (pendingChannel == null) {
            return "The channel for pending ads is invalid. Please contact an admin.";
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("New ad by " + sender.getName() +" pending approval");
        embed.setDescription(ad);
        embed.setColor(Color.GRAY);
        embed.setFooter("Ad ID: " + adID);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("approveAd", "Approve"));
        buttons.add(Button.danger("denyAd", "Deny"));
        pendingChannel.sendMessageEmbeds(embed.build()).addActionRow(buttons).queue();

        return "Your ad has been sent for approval. Please wait for an admin to approve or deny it.";
    }

    private void approveAd(ButtonInteractionEvent event) {
        if (!checkForPermissions(event)) {
            event.reply("You do not have permission to approve ads.").setEphemeral(true).queue();
            return;
        }

        // this code is quite shit.
        MessageEmbed embed = event.getMessage().getEmbeds().get(0);
        String adID = embed.getFooter().getText().replace("Ad ID: ", "");
        String ad = embed.getDescription();
        User adCreator = Utils.getUserFromCacheOrFetch(getUserIDbyAdID(adID));

        // send ad to channel
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Ad by "+adCreator.getEffectiveName());
        embedBuilder.setDescription(ad);
        embedBuilder.setFooter("The ad is not official and is not endorsed by the server staff. For more info, contact an admin.\n"
        + "Actual sender username: "+adCreator.getName());
        TextChannel channel = event.getGuild().getTextChannelById(adChannelID);
        channel.sendMessageEmbeds(embedBuilder.build()).queue();

        // send confirmation to user
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Ad accepted");
        builder.setColor(Color.GREEN);
        builder.setDescription("Your ad in the "+event.getGuild().getName()+" server has been accepted!\n"
        + "Check in the "+channel.getName()+" channel for the posted ad.");
        Utils.sendEmbed(adCreator.getIdLong(), builder);

        event.getMessage().delete().queue();
        removeAdID(adID);
        event.reply("Ad sent successfully!").setEphemeral(true).queue();
    }

    private void denyAd(ButtonInteractionEvent event) {
        if (!checkForPermissions(event)) {
            event.reply("You do not have permission to deny ads.").setEphemeral(true).queue();
            return;
        }
        event.replyModal(AdDenialModal.getModal()).queue();
    }

    private boolean checkForPermissions(ButtonInteractionEvent event) {
        User user = event.getUser();
        Guild guild = event.getGuild();
        if (guild == null) {
            return false;
        }
        GuildChannel channel = guild.getTextChannelById(adChannelID);

        if (channel == null) {
            event.reply("The channel for ads is invalid. Please contact an admin.").setEphemeral(true).queue();
            return false;
        }

        boolean hasPerms = Utils.getGuildMemberFromCacheOrFetch(user, guild).hasPermission(channel, Permission.MESSAGE_SEND);

        //TODO: add further permissions checks in the future
        return hasPerms;
    }

    public long getUserIDbyAdID(String adID) {
        return registeredAdUUIDs.getLong(adID);
    }

    public void removeAdID(String adID) {
        registeredAdUUIDs.remove(adID);
    }

}
