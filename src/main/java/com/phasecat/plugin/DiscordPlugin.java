package com.phasecat.plugin;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.activity.Activity;

import javax.annotation.Nonnull;
import java.time.Instant;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class DiscordPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    //discord stuff
    static long discordID = 1461185613054087209L;
    private static final Activity activity = new Activity();
    private static Thread discordThread = null;
    private static Core discordCore = null;

    //hytale specific stuff below!

    //this will be set to the reference of the player once they join server
    private PlayerRef playerRef = null;

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
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, (event) ->
        {
            onPlayerAddedToWorld((AddPlayerToWorldEvent) event);
        });

        //so we can close the presence thread
        getEventRegistry().register(ShutdownEvent.class, (event) ->
        {
            onServerShutdown((ShutdownEvent) event);
        });

        //startDiscord();
    }

    //hook player up to the player reference once they join world
    public void onPlayerAddedToWorld(AddPlayerToWorldEvent event)
    {
        LOGGER.atInfo().log("A player joined the server, time to start connection!");

        //actually get player and store their data into player ref var
        Holder<EntityStore> temp = event.getHolder();
        playerRef = temp.getComponent(PlayerRef.getComponentType());
        if(playerRef != null)
        {
            //LOGGER.atInfo().log("Connected with player: " + playerRef.getUsername());

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

                while(!Thread.currentThread().isInterrupted()) {
                    activity.assets().setLargeImage("hytalelogo");
                    //activity.assets().setLargeText("hello from hytale!");
                    activity.setState("Playing Hytale");
                    activity.setDetails("Exploring Orbis");

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