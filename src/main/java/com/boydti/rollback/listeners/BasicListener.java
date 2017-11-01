package com.boydti.rollback.listeners;

import cn.nukkit.Server;
import cn.nukkit.event.Listener;
import cn.nukkit.plugin.Plugin;
import com.boydti.rollback.config.Loggers;
import java.lang.instrument.Instrumentation;

public class BasicListener implements Listener {
    private final Plugin plugin;

    public BasicListener(Loggers logger, Plugin parent) {
        this.plugin = parent;
        if (logger.use()) {
            plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        }
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public Server getServer() {
        return getPlugin().getServer();
    }

    private static Instrumentation instrumentation;

    public static void premain(String args, Instrumentation inst) {
        instrumentation = inst;
    }

    public static long getObjectSize(Object o) {
        return instrumentation.getObjectSize(o);
    }

}
