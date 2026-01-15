package com.phasecat.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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
    static long discordID = 1460837855147462717L;

    private static final Activity activity = new Activity();

    public DiscordPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.getCommandRegistry().registerCommand(new ExampleCommand(this.getName(), this.getManifest().getVersion().toString()));
    }

    @Override
    protected void start()
    {
        LOGGER.atInfo().log("Plugin started!");
        startDiscord();
    }

    public static void startDiscord()
    {
        new Thread(() -> {
            LOGGER.atInfo().log("Attempting to connect to discord...");
            final CreateParams params = new CreateParams();
            params.setClientID(discordID);
            params.setFlags(CreateParams.Flags.NO_REQUIRE_DISCORD);
            activity.timestamps().setStart(Instant.now());
            try (final Core core = new Core(params)) {
                //core.setLogHook(LogLevel.DEBUG, (level, message) -> getLogger().at(Level.INFO).log("[Discord] ", u));

                while(true) {
                    activity.assets().setLargeImage("hytalelogo");
                    activity.assets().setLargeText("hello from hytale!");
                    try {
                        core.activityManager().updateActivity(activity);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        }).start();
    }

}