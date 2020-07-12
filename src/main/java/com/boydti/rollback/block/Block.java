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

package com.boydti.rollback.block;

import cn.nukkit.Server;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import com.boydti.rollback.database.SQLDatabase;
import com.sk89q.jnbt.CompoundTag;

/**
 * A class represents the block changes of the
 * queried data from database.
 */
public abstract class Block extends Position {

    String playerName = "";
    // The simple things
    private final SQLDatabase database;
    // The blocks data
    private short combinedFrom = 0;
    private short combinedTo = 0;
    private CompoundTag nbtFrom = null;
    private CompoundTag nbtTo = null;
    // The data of this changes
    private boolean isReverted = false;
    private int timeChanged = 0;
    private int revertTime = 0;

    Block(SQLDatabase db, Vector3 pos) {
        super(pos.x, pos.y, pos.z, Server.getInstance().getLevelByName(db.world));
        this.database = db;
    }

    public boolean isReverted() {
        return isReverted;
    }

    /**
     * Get the unique change pair
     * This used to store the block
     * changes in one number
     *
     * @return The combined block data
     */
    public int getChangePair() {
        return (combinedFrom << 16) | (combinedTo & 0xFFFF);
    }

    /**
     * Get the player name or by who
     * made this change.
     *
     * @return The player name
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Set the player name and then return to this class
     * again. Useful and strict for formatting and
     * etc.
     *
     * @param playerName The player name to be set
     * @return The block change itself
     */
    public final Block setPlayerName(String playerName) {
        this.playerName = playerName;
        return this;
    }

    public boolean hasNBT() {
        return nbtFrom != null || nbtTo != null;
    }

    public CompoundTag getTileFrom() {
        return nbtFrom;
    }

    public CompoundTag getTileTo() {
        return nbtTo;
    }

    public short getBlockFrom() {
        return combinedFrom;
    }

    public short getBlockTo() {
        return combinedTo;
    }

    public SQLDatabase getDatabase() {
        return database;
    }

    public int getRevertTime() {
        return revertTime;
    }

    /**
     * Get a simplified time change from the db.
     * You must add the value with the base time
     * from the database to get the full time.
     *
     * @return A simplified time change
     */
    public int getTimeChanged() {
        return timeChanged;
    }

    /**
     * Return the timestamp of the changed block
     * Do not use {@link Block#getTimeChanged()}
     * to get the time. Its returned the SIMPLIFIED
     * time and this only return the MILLIS time.
     *
     * @return the timestamp of the block.
     */
    public long getChangedTimestamp() {
        return ((getDatabase().baseTime + getTimeChanged()) << getDatabase().timePartition);
    }

    /**
     * Set the revert state for the block.
     * Basically this just checks if the block just made
     * a changes by reverting it or something else
     * happens.
     *
     * @param reverted   The revert function
     * @param revertTime Exact time when the revert was being handled
     * @return The block change itself
     */
    public Block setReverted(boolean reverted, int revertTime) {
        this.isReverted = reverted;
        this.revertTime = reverted ? revertTime : 0;
        return this;
    }

    /**
     * Set the time for this block change.
     * Simplify your time with base time from
     * the database and pair them together to
     * make sure it will return a valid value.
     *
     * @return The block change itself
     */
    public final Block setChangeTime(int changeTime) {
        this.timeChanged = changeTime;
        return this;
    }

    /**
     * Set the block NBT data, could be chest
     * chest that being changed into
     *
     * @param nbtFrom The block NBT was before the change.
     * @param nbtTo   The block NBT after the change.
     * @return The block change itself
     */
    public final Block setNBTData(CompoundTag nbtFrom, CompoundTag nbtTo) {
        this.nbtFrom = nbtFrom;
        this.nbtTo = nbtTo;
        return this;
    }

    /**
     * Set all the block data that been made
     * This changes include from what the block
     * being and to what the block being.
     *
     * @param from The block that was before the change.
     * @param to   The block that being changed into.
     * @return The block change itself
     */
    public final Block setBlockCombined(short from, short to) {
        this.combinedFrom = from;
        this.combinedTo = to;
        return this;
    }
}
