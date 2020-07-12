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

package com.boydti.rollback.database;

import cn.nukkit.utils.Config;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.database.module.MySQL;
import com.boydti.rollback.database.module.SQLite;
import com.boydti.rollback.util.Utils;

import java.io.File;
import java.util.HashMap;

public class DBHandler {
    private final HashMap<String, SQLDatabase> databases = new HashMap<>();

    private final String prefix;
    private final String dbLocation;
    private final String user;
    private final String databaseName;
    private final String password;
    private final int port;
    private final String hostname;
    private final boolean useMysql;

    public DBHandler(Config cfg) {
        this.prefix = cfg.getString("database.mainPrefix", "");
        this.dbLocation = cfg.getString("database.SQLite.file-name", "database");
        this.user = cfg.getString("database.MySQL.username", "root");
        this.databaseName = cfg.getString("database.MySQL.username", "root");
        this.password = cfg.getString("database.MySQL.password", "");
        this.hostname = cfg.getString("database.MySQL.hostname", "log_1");
        this.port = cfg.getInt("database.MySQL.port", 3306);
        this.useMysql = cfg.getBoolean("database.shouldBeEnabled-mysql", false);
    }

    public HashMap<String, SQLDatabase> getDatabases() {
        return databases;
    }

    public SQLDatabase getDatabase(String world) {
        SQLDatabase database = databases.get(world);
        if (database != null) {
            return database;
        }
        if (useMysql) {
            database = new MySQL(world, prefix, hostname, port, databaseName, user, password);
        } else {
            database = new SQLite(world, prefix, new File(Rollback.get().getDataFolder(), dbLocation + File.separator + world + ".db"));
        }
        Utils.send("&7Started database world: &e" + world);
        return databases.put(world, database);
    }
}
