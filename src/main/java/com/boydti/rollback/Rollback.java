/*
 * Rollback for Nukkit
 *
 * Copyright (C) 2017-2020 boy0001 and larryTheCoder
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.boydti.rollback;

import cn.nukkit.Server;
import cn.nukkit.utils.TextFormat;
import com.boydti.rollback.api.RollbackAPI;
import com.boydti.rollback.database.DBHandler;
import com.boydti.rollback.object.Session;
import com.boydti.rollback.util.Loggers;
import com.boydti.rollback.util.TaskManager;
import com.boydti.rollback.util.Utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Rollback extends RollbackAPI {

    private static final int CONFIG_VERSION = 1;

    private static com.boydti.rollback.Rollback INSTANCE;
    private final Map<String, Session> playerSession = new HashMap<>();
    private DBHandler db;
    private String state = "loaded.";

    // Get main instance
    public static com.boydti.rollback.Rollback get() {
        return INSTANCE;
    }

    public DBHandler getDatabase() {
        return db;
    }

    @Override
    public void onEnable() {
        Loggers.setup(getConfig());

        // Step 1: Register the database
        db = new DBHandler(getConfig());

        // Step 2: Register the plugin events
        new com.boydti.rollback.CoreEvent(this, getConfig().getString("wand-item", "347:0").split(":"));

        // Step 3: Register the plugin command maps
        getServer().getCommandMap().register("rollback", new com.boydti.rollback.CoreCommand(this));

        // Step 4: Check the players, worlds that been loaded
        checkStartup();
        Utils.send(TextFormat.colorize("&aRollback for Nukkit has been " + state));
    }

    private void checkStartup() {
        // Sometimes, these could be missed when on reload.
        Server.getInstance().getLevels().forEach((levelId, level) -> getDatabase().getDatabase(level.getName()));
        Server.getInstance().getOnlinePlayers().forEach((uuid, player) -> {
            if (player.hasPermission("rollback.perform")) {
                Session session = new Session(player);

                setSession(player.getName(), session);
                Utils.send("&7Created " + player.getName() + "'s Session");
                state = "reloaded.";
            }
        });
    }

    @Override
    public void onLoad() {
        INSTANCE = this;

        if (getResource("config.yml") != null) {
            saveResource("config.yml");
        }

        // Update the config, ease.
        File file = new File(getDataFolder(), "config.yml");
        if (getConfig().getInt("version") < CONFIG_VERSION) {
            Utils.send("&cOutdated config! Updating to a new one");
            Utils.send("&aYour old config will be renamed into config.old!");
            file.renameTo(new File(getDataFolder(), "config.old"));
            saveResource("config.yml");
            reloadConfig();
        }

        // Register TaskManager
        TaskManager.IMP = new TaskManager();
    }

    @Override
    public Session setSession(String player, Session session) {
        if (session == null) {
            playerSession.remove(player);
            return null;
        }
        return playerSession.put(player, session);
    }

    @Override
    public Session getSession(String player) {
        if (playerSession.containsKey(player)) {
            return playerSession.get(player);
        }

        Utils.send("&cNo session found for &e" + player + "&c and this is discouraged.");
        return null;
    }

    public String getPrefix() {
        return TextFormat.colorize(getConfig().getString("prefix"));
    }
}