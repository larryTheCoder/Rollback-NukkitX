package com.boydti.rollback.object;

import java.util.HashMap;

import cn.nukkit.block.Block;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.util.MathMan;
import com.boydti.rollback.api.AbstractLogger;
import com.sk89q.jnbt.CompoundTag;

public class PhysicsTracker {
    
    private final AbstractLogger logger;
    private final PhysicsChange mutable = new PhysicsChange(null, 0, 0, 0, 0, null);
    private final PhysicsChange[] pool;
    private int poolIndex = 0;
    
    public PhysicsTracker(AbstractLogger logger) {
        this.logger = logger;
        pool = new PhysicsChange[100000];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new PhysicsChange(null, 0, 0, 0);
        }
    }
    
    // Store 2 ticks worth of physics changes + the current tick
    private HashMap<PhysicsChange, PhysicsChange> tick2Changes = new HashMap<>();
    private HashMap<PhysicsChange, PhysicsChange> tick1Changes = new HashMap<>();
    private HashMap<PhysicsChange, PhysicsChange> changes = new HashMap<>();

    public void clear() {
        if (!changes.isEmpty() || !tick1Changes.isEmpty() || !tick2Changes.isEmpty()) {
            tick2Changes = tick1Changes;
            tick1Changes = changes;
            changes = new HashMap<>();
        }
    }

    public void store(String name, int x, int y, int z, int combinedId) {
        PhysicsChange change = newChange(name, x, y, z, combinedId, null);
        changes.put(change, change);
    }

    public void storeRelative(String name, int x, int y, int z) {
        if (y < 255) {
            add(name, x, y + 1, z);
        }
        //        if (y > 0) {
        //            add(name, x, y - 1, z);
        //        }
        add(name, x - 1, y, z);
        add(name, x + 1, y, z);
        
        add(name, x, y, z - 1);
        add(name, x, y, z + 1);
    }
    
    public String getName(int x, int y, int z) {
        mutable.set(x, y, z);
        PhysicsChange change = tick2Changes.get(mutable);
        if (change != null) {
            return change.name;
        }
        change = tick1Changes.get(mutable);
        if (change != null) {
            return change.name;
        }
        change = changes.get(mutable);
        if (change != null) {
            return change.name;
        }
        return null;
    }
    
    private void add(String name, int x, int y, int z) {
        mutable.set(x, y, z);
        if (changes.containsKey(mutable)) {
            return;
        }
        int combined = logger.getQueue().getCombinedId4Data(x, y, z);
        if (combined == 0) {
            return;
        }
        CompoundTag nbt;
        if (FaweCache.hasNBT(FaweCache.getId(combined))) {
            nbt = logger.getQueue().getTileEntity(x, y, z);
        } else {
            nbt = null;
        }
        PhysicsChange change = newChange(name, x, y, z, combined, nbt);
        changes.put(change, change);
    }

    public boolean logChange(Block block) {
        boolean result = false;
        int x = block.getFloorX();
        int y = block.getFloorY();
        int z = block.getFloorZ();
        if (y < 255) {
            result = logChange(x, y + 1, z) || result;
        }
        result = logChange(x, y, z - 1) || result;
        result = logChange(x, y, z + 1) || result;
        result = logChange(x - 1, y, z) || result;
        result = logChange(x + 1, y, z) || result;
        result = logChange(x, y, z) || result;
        return result;
    }
    
    public String getNameRelative(int x, int y, int z) {
        String name = getName(x + 1, y, z);
        if (name != null) return name;
        name = getName(x - 1, y, z);
        if (name != null) return name;
        name = getName(x, y, z + 1);
        if (name != null) return name;
        name = getName(x, y, z - 1);
        if (name != null) return name;
        name = getName(x, y + 1, z);
        return name;
    }

    public boolean logChange(int x, int y, int z) {
        mutable.set(x, y, z);
        PhysicsChange change = changes.get(mutable);
        if (change == null) {
            return false;
        }
        int combinedTo = logger.getQueue().getCombinedId4Data(x, y, z);
        if (change.combinedId != combinedTo) {
            changes.remove(mutable);
            logger.logBlock(change.name, x, y, z, (short) change.combinedId, (short) combinedTo, change.nbt, null);
            storeRelative(change.name, x, y, z);
            return true;
        }
        return false;
    }
    
    private PhysicsChange newChange(String name, int x, int y, int z, int combinedId, CompoundTag nbt) {
        if (poolIndex == pool.length) {
            poolIndex = 0;
        }
        PhysicsChange value = pool[poolIndex++];
        value.set(name, x, y, z, combinedId, nbt);
        return value;
    }
    
    private class PhysicsChange {
        public int x;
        public int y;
        public int z;
        public int combinedId;
        public CompoundTag nbt;
        public String name;
        
        public PhysicsChange(String name, int x, int y, int z) {
            combinedId = logger.getQueue().getCombinedId4Data(x, y, z);
            if (FaweCache.hasNBT(FaweCache.getId(combinedId))) {
                nbt = logger.getQueue().getTileEntity(x, y, z);
            }
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public PhysicsChange(String name, int x, int y, int z, int combinedId, CompoundTag nbt) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.combinedId = combinedId;
            this.nbt = nbt;
        }
        
        private int hash;
        
        public void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            hash = 0;
        }
        
        public void set(String name, int x, int y, int z, int combinedId, CompoundTag nbt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.combinedId = combinedId;
            this.nbt = nbt;
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
        public String toString() {
            return (x + "," + y + "," + z);
        }
        
        @Override
        public int hashCode() {
            if (hash == 0) {
                long longHash = MathMan.pairInt(MathMan.pair((short) x, (short) z), y);
                hash = (int) (longHash ^ (longHash >>> 32));
            }
            return hash;
        }
        
        @Override
        public boolean equals(Object obj) {
            PhysicsChange other = (PhysicsChange) obj;
            return other.x == x && other.z == z && other.y == y;
        }
    }
}
