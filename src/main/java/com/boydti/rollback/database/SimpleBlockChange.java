package com.boydti.rollback.database;

import com.boydti.fawe.util.MathMan;
import com.sk89q.jnbt.CompoundTag;

public class SimpleBlockChange {
    public final int combinedFrom;
    public final int combinedTo;
    public final CompoundTag tileFrom;
    public final CompoundTag tileTo;

    public final int x;
    public final int y;
    public final int z;
    public final String player;
    public final long timestamp;
    
    public SimpleBlockChange(int x, int y, int z, int from, int to, CompoundTag nbtf, CompoundTag nbtt, String player, long timestamp) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.combinedFrom = from;
        this.combinedTo = to;
        this.tileFrom = nbtf;
        this.tileTo = nbtt;
        this.player = player;
        this.timestamp = timestamp;
    }
    
    private int hash;

    @Override
    public int hashCode() {
        if (hash == 0) {
            hash = (int) MathMan.pairInt(MathMan.pair((short) x, (short) z), y);
        }
        return hash;
    }
    
    @Override
    public boolean equals(Object obj) {
        SimpleBlockChange other = (SimpleBlockChange) obj;
        return other.hash == hashCode() && other.x == x && other.y == y && other.z == z;
    }
}
