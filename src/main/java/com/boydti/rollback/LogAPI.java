package com.boydti.rollback;

import cn.nukkit.level.Level;
import com.boydti.rollback.api.AbstractLogger;

public class LogAPI {
    public static AbstractLogger getLogger(String world) {
        return Rollback.db().getDatabase(world);
    }
    
    public static AbstractLogger getLogger(Level level) {
        return getLogger(level.getName());
    }
}
