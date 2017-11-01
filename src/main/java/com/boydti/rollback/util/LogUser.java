package com.boydti.rollback.util;

public enum LogUser {
    // TODO UUIDS here
    LIQUID,
    FALLING_BLOCK,
    UNKNOWN;

    public final String ID;
    
    LogUser() {
        this.ID = name();
    }
    
    public static String getName(String name) {
        return name;
    }
}
