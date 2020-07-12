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

package com.boydti.rollback.object;

import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.util.MathMan;
import com.boydti.rollback.api.AbstractLogger;
import com.sk89q.jnbt.CompoundTag;

import java.util.HashMap;

public class PhysicsTracker {

    private final AbstractLogger logger;

    private final PhysicsChange mutable = new PhysicsChange(new Vector3());
    private final PhysicsChange[] pool;

    private int poolIndex = 0;

    // Store 2 ticks worth of physics changes + the current tick
    private HashMap<PhysicsChange, PhysicsChange> tick2Changes = new HashMap<>();
    private HashMap<PhysicsChange, PhysicsChange> tick1Changes = new HashMap<>();
    private HashMap<PhysicsChange, PhysicsChange> changes = new HashMap<>();

    public PhysicsTracker(AbstractLogger logger) {
        this.logger = logger;
        pool = new PhysicsChange[100000];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new PhysicsChange(new Vector3());
        }
    }

    public void clear() {
        if (!changes.isEmpty() || !tick1Changes.isEmpty() || !tick2Changes.isEmpty()) {
            tick2Changes = tick1Changes;
            tick1Changes = changes;
            changes = new HashMap<>();
        }
    }

    public void store(String name, Vector3 vec, int combinedId) {
        PhysicsChange change = newChange(name, vec, combinedId, null);
        changes.put(change, change);
    }

    public void storeRelative(String name, Vector3 vec) {
        if (vec.getFloorY() < 255) {
            add(name, vec.add(0, 1));
        }

        add(name, vec.add(-1));
        add(name, vec.add(1));

        add(name, vec.add(0, 0, -1));
        add(name, vec.add(0, 0, 1));
    }

    private String getName(Vector3 vec) {
        mutable.set(vec);
        PhysicsChange change = tick2Changes.get(mutable);
        if (change != null) {
            return change.getPlayerName();
        }
        change = tick1Changes.get(mutable);
        if (change != null) {
            return change.getPlayerName();
        }
        change = changes.get(mutable);
        if (change != null) {
            return change.getPlayerName();
        }
        return null;
    }

    private void add(String name, Vector3 vec) {
        mutable.set(vec);
        if (changes.containsKey(mutable)) {
            return;
        }
        int combined = logger.getQueue().getCombinedId4Data(vec.getFloorX(), vec.getFloorY(), vec.getFloorZ());
        if (combined == 0) {
            return;
        }
        CompoundTag nbt;
        if (FaweCache.hasNBT(FaweCache.getId(combined))) {
            nbt = logger.getQueue().getTileEntity(vec.getFloorX(), vec.getFloorY(), vec.getFloorZ());
        } else {
            nbt = null;
        }
        PhysicsChange change = newChange(name, vec, combined, nbt);
        changes.put(change, change);
    }

    public void logChange(Block block) {
        int x = block.getFloorX();
        int y = block.getFloorY();
        int z = block.getFloorZ();
        if (y < 255) {
            logChange(new Vector3(x, y + 1, z));
        }
        logChange(new Vector3(x, y, z - 1));
        logChange(new Vector3(x, y, z + 1));
        logChange(new Vector3(x - 1, y, z));
        logChange(new Vector3(x + 1, y, z));
        logChange(new Vector3(x, y, z));
    }

    public String getNameRelative(Vector3 vec) {
        String name = getName(vec.add(1));
        if (name != null) return name;
        name = getName(vec.add(-1));
        if (name != null) return name;
        name = getName(vec.add(0, 0, 1));
        if (name != null) return name;
        name = getName(vec.add(0, 0, -1));
        if (name != null) return name;
        name = getName(vec.add(0, 1, 0));
        return name;
    }

    private void logChange(Vector3 vec) {
        mutable.set(vec);
        PhysicsChange change = changes.get(mutable);
        if (change == null) {
            return;
        }
        int combinedTo = logger.getQueue().getCombinedId4Data(vec.getFloorX(), vec.getFloorY(), vec.getFloorZ());
        if (change.combinedId != combinedTo) {
            changes.remove(mutable);
            logger.logBlock(change.getPlayerName(), vec, (short) change.combinedId, (short) combinedTo, change.nbt, null);
            storeRelative(change.getPlayerName(), vec);
        }
    }

    private PhysicsChange newChange(String name, Vector3 vec, int combinedId, CompoundTag nbt) {
        if (poolIndex == pool.length) {
            poolIndex = 0;
        }
        PhysicsChange value = pool[poolIndex++];
        value.set(name, vec, combinedId, nbt);
        return value;
    }

    private class PhysicsChange {
        private String playerName;
        private int x;
        private int y;
        private int z;
        private int combinedId;
        private CompoundTag nbt;
        private int hash;

        PhysicsChange(Vector3 vec) {
            if (logger != null) {
                combinedId = logger.getQueue().getCombinedId4Data(vec.getFloorX(), vec.getFloorY(), vec.getFloorZ());
                if (FaweCache.hasNBT(FaweCache.getId(combinedId))) {
                    nbt = logger.getQueue().getTileEntity(x, y, z);
                }
            }
            this.playerName = null;
            this.x = vec.getFloorX();
            this.y = vec.getFloorY();
            this.z = vec.getFloorZ();
        }

        void set(Vector3 vec) {
            this.x = vec.getFloorX();
            this.y = vec.getFloorY();
            this.z = vec.getFloorZ();
            hash = 0;
        }

        void set(String playerName, Vector3 vec, int combinedId, CompoundTag nbt) {
            this.x = vec.getFloorX();
            this.y = vec.getFloorY();
            this.z = vec.getFloorZ();
            this.playerName = playerName;
            this.combinedId = combinedId;
            this.nbt = nbt;
            hash = 0;
        }

        String getPlayerName() {
            return playerName;
        }

        @Override
        public String toString() {
            return ("Player=" + playerName + "," + x + "," + y + "," + z);
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
            if (!(obj instanceof PhysicsChange)) {
                return false;
            }
            PhysicsChange other = (PhysicsChange) obj;
            return other.x == x && other.z == z && other.y == y;
        }
    }
}
