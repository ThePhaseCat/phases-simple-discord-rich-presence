package com.phasecat.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import com.hypixel.hytale.math.vector.Vector3d;
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
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
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
    private static Field mapVisibleField;

    //this will be set to the reference of the player once they join server
    private static Player player = null;


    private static Vector3d playerLastPosition = null;
    private static long playerLastMovementTime = System.currentTimeMillis();

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
            try{
                WorldMapTracker tracker = player.getWorldMapTracker();
                if(tracker != null && mapVisibleField == null)
                {
                    mapVisibleField = tracker.getClass().getDeclaredField("clientHasWorldMapVisible");
                    mapVisibleField.setAccessible(true);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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


                String lastDetails = "";

                while(!Thread.currentThread().isInterrupted()) {
                    String newDetails = getDiscordDetails();
                    if(!newDetails.equals(lastDetails))
                    {
                        activity.assets().setLargeImage("hytalelogo");
                        //activity.assets().setLargeText("hello from hytale!");
                        activity.setState("Playing Hytale");
                        activity.setDetails(getDiscordDetails());

                        core.activityManager().updateActivity(activity);

                        core.runCallbacks();

                        lastDetails = newDetails;
                    }

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
            boolean playerAFK = false;

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
                if(playerMapTracker != null && mapVisibleField != null)
                {
                    playerOnMapScreen = mapVisibleField.getBoolean(playerMapTracker);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //riding check
            try{
                int mount = player.getMountEntityId();
                if(mount > 0 && mount != player.getNetworkId())
                {
                    playerRiding = true;
                }
            } catch (Exception e){
                throw new RuntimeException(e);
            }

            //afk check
            try {
                Vector3d playerCurrentPosition = player.getTransformComponent().getPosition();
                if (playerLastPosition != null) {
                    if (playerCurrentPosition.distanceTo(playerLastPosition) < 0.05D) {
                        if (System.currentTimeMillis() - playerLastMovementTime > 180000L) {
                            playerAFK = true;
                        }
                    } else {
                        playerLastMovementTime = System.currentTimeMillis();
                    }
                } else {
                    playerLastMovementTime = System.currentTimeMillis();
                }

                playerLastPosition = new Vector3d(playerCurrentPosition.x, playerCurrentPosition.y, playerCurrentPosition.z);
            } catch (Exception e) {
                //do nothing and hope this does not fail
            }

            //actual string construction part
            if(playerAFK)
            {
                return "AFK";
            }
            else if(playerCrafting)
            {
                return "Crafting";
            }
            else if(playerOnMapScreen)
            {
                return "Viewing Map";
            }
            else if(playerRiding)
            {
                return "Riding";
            }
            else //default case
            {
                return getPlayerZoneName();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //find and get the name of the zone the player is in
    public static String getPlayerZoneName()
    {
        try{
            WorldMapTracker playerMapTracker = player.getWorldMapTracker();
            if(playerMapTracker != null)
            {
                String biome = playerMapTracker.getCurrentBiomeName();
                if(biome != null && !biome.isEmpty())
                {
                    String biomeName = formatBiomeName(biome);
                    return "Exploring " + biomeName;
                }
            }

            WorldChunk chunk = player.getTransformComponent().getChunk();
            if(chunk != null)
            {
                String worldName = chunk.getWorld().getName();
                if("default".equals(worldName))
                {
                    return "Exploring Orbis";
                }

                return "Exploring " + worldName;
            }
        }
        catch (IllegalStateException e)
        {
            return "Loading, please wait...";
        }
        catch (Exception e)
        {
            LOGGER.atInfo().log("Caught exception in get player zone name...");
        }

        return "Playing Hytale";
    }

    //format the biome name since it doesn't look good by default
    public static String formatBiomeName(String originalName)
    {
        if(originalName == null)
        {
            return "";
        }
        else
        {
            String[] words = originalName.replace("_", " ").split(" ");
            StringBuilder sb = new StringBuilder();
            String[] stringBuilt = words;
            int length = words.length;

            for(int i = 0; i < length; i++)
            {
                String word = stringBuilt[i];
                if (word.length() > 0)
                {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                    sb.append(word.substring(1).toLowerCase());
                    sb.append(" ");
                }
            }

            return sb.toString().trim();
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