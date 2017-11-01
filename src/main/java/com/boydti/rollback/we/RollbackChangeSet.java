package com.boydti.rollback.we;

import com.boydti.fawe.object.changeset.FaweChangeSet;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.database.SQLDatabase;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.history.change.Change;
import java.util.Iterator;

public class RollbackChangeSet extends FaweChangeSet {
    
    private final String name;
    private final SQLDatabase database;
    private final FaweChangeSet parent;

    public RollbackChangeSet(String name, FaweChangeSet fcs) {
        super(fcs.getWorld());
        this.name = name;
        this.database = Rollback.db().getDatabase(getWorld().getName());
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
        database.logBlock(name, x, y, z, (short) from, (short) to);
        parent.add(x, y, z, from, to);
    }
    
    @Override
    public void addEntityCreate(CompoundTag tag) {
        parent.addEntityCreate(tag);
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
