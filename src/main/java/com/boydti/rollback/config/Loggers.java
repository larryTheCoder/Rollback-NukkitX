package com.boydti.rollback.config;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import com.boydti.fawe.configuration.file.YamlConfiguration;

public enum Loggers {
    WORLDEDIT(false), 
    TILES(true), 
    BLOCK_BREAK(true), 
    BLOCK_PLACE(true),
    PHYSICS(true), 
    LIQUID(true), 
    FALLING_BLOCK(true),
    
    
    
    
    
    
    
    
    
    
    
    ;
    
    private boolean enabled;
    
    Loggers(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean use() {
        return enabled;
    }
    
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

    public static boolean load(YamlConfiguration yml) {
        try {
            EnumSet<Loggers> allEnums = EnumSet.allOf(Loggers.class);
            boolean changed = false;
            for (Loggers a : allEnums) {
                if (!yml.contains(a.name())) {
                    yml.set(a.name(), a.use());
                    changed = true;
                } else {
                    a.enabled = yml.getBoolean(a.name());
                }
            }
            return changed;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
