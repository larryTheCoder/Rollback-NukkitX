package com.boydti.rollback.config;

import com.boydti.fawe.config.BBC;
import com.boydti.fawe.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config {

    
    //    public static boolean BLOCK_BREAK = true;
    //    public static boolean BLOCK_BREAK = true;
    //    public static boolean BLOCK_BREAK = true;
    //    public static boolean BLOCK_BREAK = true;
    //    public static boolean BLOCK_BREAK = true;
    //    public static boolean BLOCK_BREAK = true;
    
    public static int PURGE_DAYS = 14;
    public static int ITEM = 347;
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
        PREFIX = BBC.color("&2[&aRB&2] &b");
        Map<String, Object> options = new HashMap<>();
        // Add options
        options.put("purge-days", PURGE_DAYS);
        options.put("item", ITEM);
        // Create
        boolean modified = false;
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            String key = entry.getKey();
            if (!config.contains(key)) {
                config.set(key, entry.getValue());
                modified = true;
            }
        }
        PURGE_DAYS = config.getInt("purge-days");
        ITEM = config.getInt("item");
        return modified;
    }
}
