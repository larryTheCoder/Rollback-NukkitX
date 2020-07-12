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

package com.boydti.rollback.api;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.object.PhysicsTracker;
import com.boydti.rollback.util.Loggers;
import com.sk89q.jnbt.CompoundTag;

/**
 * A logger system that logs every
 * Action of player interaction.
 */
public abstract class AbstractLogger extends RollbackRunnable {

    public final String world;
    private final FaweQueue queue;
    private final PhysicsTracker tracker;
    private final PhysicsTracker waterTracker;

    protected AbstractLogger(String world) {
        this.world = world;
        this.queue = FaweAPI.createQueue(world, false);
        this.tracker = new PhysicsTracker(this);
        this.waterTracker = new PhysicsTracker(this);
        Rollback.get().getServer().getScheduler().scheduleRepeatingTask(Rollback.get(), tracker::clear, 1);
        Rollback.get().getServer().getScheduler().scheduleRepeatingTask(Rollback.get(), waterTracker::clear, 15);
    }

    /**
     * Get the relative name of a block. This is typically used to get the minecraft-string
     * based name that, in which will be used when a block is being physically interacted
     * by a player.
     *
     * @param block The block that needs to be checked.
     * @return The name of the player, otherwise minecraft:vanilla will returned.
     */
    public String getNameRelative(Block block) {
        return tracker.getNameRelative(block.getLocation());
    }

    /**
     * Logs a physics reaction into the Physics Tracker pool.
     *
     * @param block The block that need to be checked
     */
    public void logPhysics(Block block) {
        tracker.logChange(block);
    }

    /**
     * Tracks water flows.
     *
     * @param block The block that need to be checked
     */
    public void trackLiquid(String name, Block block) {
        waterTracker.store(name, block.getLocation(), queue.getCombinedId4Data(block.getFloorX(), block.getFloorY(), block.getFloorZ()));
    }

    /**
     * Returns the relative name of a liquid block.
     *
     * @param block The block that need to be checked
     * @return The name of the player, otherwise minecraft:vanilla will become.
     */
    public String getTrackedLiquid(Block block) {
        return waterTracker.getNameRelative(block.getLocation());
    }

    /**
     * Returns the FaweQueue class.
     *
     * @return FaweQueue
     */
    public FaweQueue getQueue() {
        return queue;
    }

    /**
     * Logs a block placement and stores its data into a database.
     *
     * @param playerName The player-name who made the action.
     * @param pos        The 3-vector point of the location that been made.
     * @param combinedTo The combined into block.
     * @param nbtTo      The compound tag of the block entity if exists
     */
    public void logPlace(String playerName, Vector3 pos, short combinedTo, CompoundTag nbtTo) {
        short combinedFrom = (short) queue.getCombinedId4Data(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
        CompoundTag nbtFrom;
        if (FaweCache.hasNBT(FaweCache.getId(combinedFrom))) {
            nbtFrom = queue.getTileEntity(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
        } else {
            nbtFrom = null;
        }
        logBlock(playerName, pos, combinedFrom, combinedTo, nbtFrom, nbtTo);
        tracker.store(playerName, pos, combinedTo);
        waterTracker.store(playerName, pos, combinedTo);
    }

    /**
     * Log the block breaking. This typically
     * been used for the player who are using a
     * game-mode-1. Which its need to track if
     * There is an ice block or not.
     *
     * @param playerName The player who made the action
     * @param block      The 3-vector point of the location that been made.
     * @param track      Track its source from.
     */
    public void logPlayerBreak(Player playerName, Vector3 block, boolean track) {
        logBreak(playerName.getName(), block, playerName.getGamemode() == 1, track);
    }

    /**
     * Log the block breaking and save them into
     * the database. This checks if there is an ice
     * that need to be logged to if the player is
     * not creative.
     *
     * @param playerName The player who made the action
     * @param block      The 3-vector point of the location that been made.
     * @param track      Track its source from.
     * @param creative   If the player is using creative mode or not
     */
    private void logBreak(String playerName, Vector3 block, boolean creative, boolean track) {
        short combinedFrom = (short) queue.getCombinedId4Data(block.getFloorX(), block.getFloorY(), block.getFloorZ(), 0);
        CompoundTag nbtFrom;
        if (Loggers.TILES.shouldBeEnabled() && FaweCache.hasNBT(combinedFrom >> 4)) {
            nbtFrom = queue.getTileEntity(block.getFloorX(), block.getFloorY(), block.getFloorZ());
        } else {
            if (!creative && combinedFrom >> 4 == 79) { // ICE
                logBlock(playerName, block, combinedFrom, (short) (9 << 4), null, null);
                return;
            }
            nbtFrom = null;
        }
        logBlock(playerName, block, combinedFrom, (short) 0, nbtFrom, null);
        if (track) {
            tracker.storeRelative(playerName, block);
            waterTracker.storeRelative(playerName, block);
        }
    }

    /**
     * Logs the actions of the block that has been changed
     * to other block.
     *
     * @param playerName The player name who performs the action.
     * @param pos        The Vector3 position of the block.
     * @param blockFrom  A short of the variant of the block that was before.
     * @param blockTo    A short of the variant of the block that set into.
     */
    public void logBlock(String playerName, Vector3 pos, short blockFrom, short blockTo) {
        logBlock(playerName, pos, blockFrom, blockTo, null, null);
    }

    public abstract void logBlock(String name, Vector3 pos, short combinedFrom, short combinedTo, CompoundTag nbtFrom, CompoundTag nbtTo);

    public abstract void addRevert(String playerName, Vector3 vec, int timeDiff);
}
