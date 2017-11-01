package com.boydti.rollback.config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.boydti.fawe.configuration.file.YamlConfiguration;

public class Storage {

    //DATABASE
    public static boolean USE_SQLITE = true;
    public static boolean USE_MYSQL = false;
    public static String SQLITE_DB = "storage";
    public static String HOST_NAME = "localhost";
    public static int PORT = 3306;
    public static String DATABASE = "log_db";
    public static String USER = "root";
    public static String PASSWORD = "password";
    public static String PREFIX = "";
    
    public static void setup(File file) {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (load(yml)) {
            try {
                yml.save(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public static boolean load(YamlConfiguration config) {
        Map<String, Object> options = new HashMap<>();
        // Add options
        options.put("database.sqlite.enabled", USE_SQLITE);
        options.put("database.sqlite.file", SQLITE_DB);
        options.put("database.mysql.enabled", USE_MYSQL);
        options.put("database.mysql.hostname", HOST_NAME);
        options.put("database.mysql.port", PORT);
        options.put("database.mysql.database", DATABASE);
        options.put("database.mysql.user", USER);
        options.put("database.mysql.password", PASSWORD);
        options.put("database.prefix", PREFIX);
        
        // Create
        boolean modified = false;
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey();
            if (!config.contains(key)) {
                config.set(key, entry.getValue());
                modified = true;
            }
        }
        USE_SQLITE = config.getBoolean("database.sqlite.enabled");
        SQLITE_DB = config.getString("database.sqlite.file");
        USE_MYSQL = config.getBoolean("database.mysql.enabled");
        HOST_NAME = config.getString("database.mysql.hostname");
        PORT = config.getInt("database.mysql.port");
        DATABASE = config.getString("database.mysql.database");
        USER = config.getString("database.mysql.user");
        PASSWORD = config.getString("database.mysql.password");
        PREFIX = config.getString("database.prefix");
        return modified;
    }
}
