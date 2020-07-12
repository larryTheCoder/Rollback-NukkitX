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

import cn.nukkit.Player;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.changeset.FaweChangeSet;
import com.boydti.fawe.regions.FaweMaskManager;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.WEManager;
import com.boydti.rollback.block.Block;
import com.boydti.rollback.util.Loggers;
import com.boydti.rollback.we.RollbackChangeSet;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.regions.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * This class stores all the data about its
 * Target and its state.
 * <p>
 * This is a useful class
 */
public class Session {

    private FawePlayer<Object> fp;
    // Store all the logs between the players
    private List<Block> playerStoreData;

    public Session(Player player) {
        this.fp = FawePlayer.wrap(player);
        this.playerStoreData = new ArrayList<>();
    }

    public void closeSession() {
        playerStoreData.clear();
        if (fp.getMeta("RollbackSession") != null) {
            fp.deleteMeta("RollbackSession");
        }
        fp = null;
        playerStoreData = null;
    }

    /**
     * Starts the world edit session for the player
     *
     * @return EditSession for the player
     */
    public EditSession startEditSession() {
        Region[] regions = WEManager.IMP.getMask(fp, FaweMaskManager.MaskType.OWNER);
        EditSession session = new EditSessionBuilder(fp.getWorld()).player(fp)
                .checkMemory(false)
                .fastmode(false)
                .limitUnlimited()
                .allowedRegions(regions)
                .build();

        // World edit loggers
        if (Loggers.WORLDEDIT.shouldBeEnabled()) {
            ChangeSet cs = session.getChangeSet();
            session.getQueue().setChangeTask(null);
            if (cs instanceof RollbackChangeSet) {
                session.setChangeSet(((RollbackChangeSet) cs).getParent());
            } else {
                session.setChangeSet((FaweChangeSet) cs);
            }
        }

        return session;
    }

    /**
     * Starts the session of the player
     * WorldEdit rollback command
     *
     * @return true if player didn't commit any session
     */
    public boolean startSession() {
        if (fp.getMeta("RollbackSession") != null) {
            return false;
        }
        fp.setMeta("RollbackSession", true);
        return true;
    }

    /**
     * Stops the player session
     */
    public void stopSession(EditSession session) {
        if (session != null) {
            session.flushQueue();
            fp.getSession().remember(session);
        }
        fp.deleteMeta("RollbackSession");
    }

    /**
     * @return The fawe object for the player itself
     */
    public FawePlayer<Object> getFawePlayer() {
        return fp;
    }

    /**
     * Store player search values in the array
     *
     * @param blockChanges Changes of the block
     */
    public void storeSearchValues(List<Block> blockChanges) {
        playerStoreData = blockChanges;
    }

    /**
     * Get all the search values in list
     * This will return the changelist of the
     * block that been made by command search
     *
     * @return Block array list.
     */
    public List<Block> getSearchValues() {
        return playerStoreData;
    }

    /**
     * Clear all the search value from the memory
     */
    public void clearSearchValues() {
        playerStoreData.clear();
    }
}
