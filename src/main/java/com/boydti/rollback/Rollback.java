package com.boydti.rollback;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import com.boydti.fawe.config.BBC;
import com.boydti.rollback.cmd.Wand;
import com.boydti.rollback.config.Config;
import com.boydti.rollback.config.Loggers;
import com.boydti.rollback.config.Storage;
import com.boydti.rollback.database.DBHandler;
import com.boydti.rollback.event.PlayerEvents;
import com.boydti.rollback.listeners.BlockBreak;
import com.boydti.rollback.listeners.BlockFallListener;
import com.boydti.rollback.listeners.BlockPlace;
import com.boydti.rollback.listeners.PhysicsEvent;
import com.boydti.rollback.we.WELogger;
import java.io.File;

public class Rollback extends PluginBase {
    
    private static Rollback INSTANCE;
    private DBHandler db;

    @Override
    public void onEnable() {
        INSTANCE = this;
        
        Storage.setup(new File(getDataFolder(), "storage.yml"));
        Config.setup(new File(getDataFolder(), "config.yml"));
        Loggers.setup(new File(getDataFolder(), "loggers.yml"));
        
        // Database
        db = new DBHandler();

        // register events
        new PlayerEvents(this);
        new BlockBreak(this);
        new BlockPlace(this);
        new BlockFallListener(this);
        new PhysicsEvent(this);
        new WELogger();
        getServer().getCommandMap().register("rollback", new Command("rollback") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (sender instanceof Player) {
                    new com.boydti.rollback.cmd.Rollback((Player) sender, args);
                    return true;
                }
                return false;
            }
        });
        Wand wand = new Wand("inspect");
        getServer().getCommandMap().register("inspect", wand);
        getServer().getCommandMap().register("rollback:inspect", wand);
        getServer().getCommandMap().register("??", wand);
    }
    
    // Get main instance
    public static Rollback get() {
        return INSTANCE;
    }
    
    // Implementation instace same as main instance
    public static Rollback imp() {
        return INSTANCE;
    }
    
    public static DBHandler db() {
        return INSTANCE.db;
    }

    public static void debug(String m) {
        m = BBC.color(m);
        try {
            get().getServer().getConsoleSender().sendMessage(m);
        } catch (Exception e) {
            System.out.println(m.replaceAll("(?i)" + '\u00A7' + "[0-9A-FK-OR]", ""));
        }
    }

    public File getDirectory() {
        return getDataFolder();
    }
}