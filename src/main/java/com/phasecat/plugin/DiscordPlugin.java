package com.phasecat.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.activity.Activity;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Iterator;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class DiscordPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    //discord stuff
    static long discordID = 1461185613054087209L;
    private static Activity activity = new Activity();
    private static Thread discordThread = null;
    private static Core discordCore = null;

    //hytale specific stuff below!

    //this will be set to the reference of the player once they join server
    private static Player player = null;

    //for notification system
    private static Message mainConnectedMessage = Message.raw("Discord Connected!").color("#9656ce");
    private static Message sideConnectedMessage = Message.raw("Phase's Simple Discord Rich Presence " +
            "has connected to Discord! Enjoy!").color("#cab2fb");

    public DiscordPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Phase's Simple Discord Rich Presence");
    }

    @Override
    protected void start()
    {
        LOGGER.atInfo().log("Phase's Simple Discord Rich Presence starting up");

        //when a player joins server, let's go hook them up to player reference
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, (event) ->
        {
            onPlayerReady((PlayerReadyEvent) event);
        });

        //so we can close the presence thread
        getEventRegistry().register(ShutdownEvent.class, (event) ->
        {
            onServerShutdown((ShutdownEvent) event);
        });

        //startDiscord();
    }

    //hook player up to the player reference once they join world
    public void onPlayerReady(PlayerReadyEvent event)
    {
        LOGGER.atInfo().log("A player joined the server, time to start connection!");

        //actually get player and store their data into player ref var
        player = event.getPlayer();
        if(player != null)
        {
            LOGGER.atInfo().log("Connected with player: " + player.getDisplayName());

            //connected with player, now we can do the fun stuff
            startDiscord();
        }
        else
        {
            LOGGER.atInfo().log("Failed to get player connection");
        }
    }

    public static void startDiscord()
    {
        discordThread = new Thread(() -> {
            LOGGER.atInfo().log("Attempting to connect to discord...");



            final CreateParams params = new CreateParams();
            params.setClientID(discordID);
            params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD);
            activity.timestamps().setStart(Instant.now());
            try (final Core core = new Core(params)) {
                //core.setLogHook(LogLevel.DEBUG, (level, message) -> getLogger().at(Level.INFO).log("[Discord] ", u));

                discordCore = core;

                //for player notification stuff
                PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
                PacketHandler packetHandler = playerRef.getPacketHandler();
                ItemWithAllMetadata icon = new ItemStack("Weapon_Sword_Thorium", 1).toPacket();
                NotificationUtil.sendNotification(
                        packetHandler,
                        mainConnectedMessage,
                        sideConnectedMessage,
                        icon
                );

                while(!Thread.currentThread().isInterrupted()) {
                    activity.assets().setLargeImage("hytalelogo");
                    //activity.assets().setLargeText("hello from hytale!");
                    activity.setState("Playing Hytale");
                    activity.setDetails(getDiscordDetails());

                    core.activityManager().updateActivity(activity);


                    //performance reasons
                    Thread.sleep(2000);
                }

            }
            //thread interrupted while asleep
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            //error with thread itself
            catch (Exception e)
            {
                LOGGER.atWarning().log(String.valueOf(e));
                LOGGER.atWarning().log("Error with Discord Thread!");
            }
            //terminate the discord thread
            finally
            {
                LOGGER.atInfo().log("Discord Rich Presence Stopped...");
                discordCore = null;
            }
        }, "discord-rpc-thread");

        discordThread.setDaemon(true);
        discordThread.start();
    }

    //let's see (and return) a string saying what the player is doing for the discord
    //details part of the rich presence
    public static String getDiscordDetails()
    {
        try {
            boolean playerCrafting = false;
            boolean playerOnMapScreen = false;
            boolean playerRiding = false;
            boolean playerInCombat = false;

            //crafting check
            try{
                Iterator windowIterator = player.getWindowManager().getWindows().iterator();

                String windowName = "";
                //basically keep checking the player's window until the window name is
                //either crafting or processing, then we can switch
                while(windowIterator.hasNext())
                {
                    Window window = (Window) windowIterator.next();
                    windowName = window.getClass().getSimpleName();

                    if(windowName.contains("Crafting") || windowName.contains("Processing"))
                    {
                        playerCrafting = true;
                        break; //get out of loop early
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //map check
            try{
                //basically get a reference to the player's world map window and check if it's open or not
                WorldMapTracker playerMapTracker = player.getWorldMapTracker();
                if(playerMapTracker != null)
                {
                    Field mapVisible = playerMapTracker.getClass().getDeclaredField("clientHasWorldMapVisible");
                    mapVisible.setAccessible(true);
                    playerOnMapScreen = mapVisible.getBoolean(playerMapTracker);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //actual string construction part
            if(playerCrafting)
            {
                return "Crafting";
            }
            else if(playerOnMapScreen)
            {
                return "Viewing Map";
            }
            else //default case
            {
                return "Playing Hytale";
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    //handle the closing of the discord rich presence, just to be safe
    public void onServerShutdown(ShutdownEvent event)
    {
        LOGGER.atWarning().log("Server is shutting down, terminating RPC connection!");

        //actually terminate the thread
        if(discordThread != null)
        {
            discordThread.interrupt();
            discordThread = null;
        }
    }
}