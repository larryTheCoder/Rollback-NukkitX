package com.boydti.rollback.object;

import com.boydti.fawe.util.MathMan;

public class Position {
    private int x;
    private int y;
    private int z;
    
    public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    private int hash;
    
    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        hash = 0;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    @Override
    public int hashCode() {
        if (hash == 0) {
            int longHash = (int) MathMan.pairInt(MathMan.pair((short) x, (short) z), y);
            hash = longHash ^ (longHash >>> 32);
        }
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj.hashCode() != hashCode()) {
            return false;
        }
        Position other = (Position) obj;
        return other.x == x && other.z == z && other.y == y;
    }
}
