package com.boydti.rollback.database;

import java.io.File;
import java.util.HashMap;

import com.boydti.rollback.Rollback;
import com.boydti.rollback.config.Storage;

public class DBHandler {
    private final HashMap<String, SQLDatabase> databases = new HashMap<>();
    
    public SQLDatabase getDatabase(String world) {
        SQLDatabase database = databases.get(world);
        if (database != null) {
            return database;
        }
        if (Storage.USE_MYSQL) {
            database = new MySQL(world, Storage.PREFIX, Storage.HOST_NAME, Storage.PORT, Storage.DATABASE, Storage.USER, Storage.PASSWORD);
        } else {
            database = new SQLite(world, Storage.PREFIX, new File(Rollback.get().getDirectory(), Storage.SQLITE_DB + File.separator + world + (Storage.SQLITE_DB.endsWith(".db") ? "" : ".db")));
        }
        database.init();
        databases.put(world, database);
        return database;
    }
}
