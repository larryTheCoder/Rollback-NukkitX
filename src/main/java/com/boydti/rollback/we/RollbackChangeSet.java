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

package com.boydti.rollback.we;

import cn.nukkit.math.Vector3;
import com.boydti.fawe.object.changeset.FaweChangeSet;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.database.SQLDatabase;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.world.biome.BaseBiome;

import java.util.Iterator;

public class RollbackChangeSet extends FaweChangeSet {

    private final String name;
    private final SQLDatabase database;
    private final FaweChangeSet parent;

    public RollbackChangeSet(String name, FaweChangeSet fcs) {
        super(fcs.getWorld());
        this.name = name;
        this.database = Rollback.get().getDatabase().getDatabase(getWorld().getName());
        this.parent = fcs;
    }

    public FaweChangeSet getParent() {
        return parent;
    }

    @Override
    public int size() {
        return parent.size();
    }

    @Override
    public void add(int x, int y, int z, int from, int to) {
        database.logBlock(name, new Vector3(x, y, z), (short) from, (short) to);
        parent.add(x, y, z, from, to);
    }

    @Override
    public void addEntityCreate(CompoundTag tag) {
        parent.addEntityCreate(tag);
    }

    @Override
    public void addBiomeChange(int x, int z, BaseBiome from, BaseBiome to) {
        parent.addBiomeChange(x, z, from, to);
    }

    @Override
    public void addEntityRemove(CompoundTag tag) {
        parent.addEntityRemove(tag);
    }

    @Override
    public void addTileCreate(CompoundTag tag) {
        parent.addTileCreate(tag);
    }

    @Override
    public void addTileRemove(CompoundTag tag) {
        parent.addTileRemove(tag);
    }

    @Override
    public Iterator<Change> getIterator(boolean dir) {
        return parent.getIterator(dir);
    }

}
