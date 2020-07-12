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

import cn.nukkit.math.Vector3;
import com.boydti.fawe.util.MathMan;
import com.boydti.rollback.database.SQLDatabase;

public class BlockChange extends Block {

    // The most required data to make this work
    private int chunkId = Integer.MAX_VALUE;
    private short playerId = Short.MAX_VALUE;

    public BlockChange(SQLDatabase db, Vector3 pos) {
        super(db, pos);
        this.checkRequiredData();
    }

    public void checkRequiredData() {
        // ChunkId
        Integer chunkRevId = getDatabase().chunkLocId.get(MathMan.pairInt(getChunkX(), getChunkZ()));
        if (chunkRevId != null) {
            chunkId = chunkRevId;
        }

        // PlayerId
        Short id1 = getDatabase().playerVarcharId.get(playerName);
        if (id1 != null) {
            playerId = id1;
        }
    }

    public boolean emptyChunkId() {
        checkRequiredData();
        return chunkId == Integer.MAX_VALUE;
    }

    public boolean emptyPlayerId() {
        checkRequiredData();
        return playerId == Short.MAX_VALUE;
    }

    /**
     * Gets the unique table ID
     * This been uses to store the array
     * if the block position and location.
     *
     * @return The tableID
     */
    public int getTableId() {
        checkRequiredData();
        return chunkId >> getDatabase().tablePartition;
    }

    public int getChunkId() {
        checkRequiredData();
        return chunkId;
    }

    public byte getReducedChunkId() {
        checkRequiredData();
        return (byte) (chunkId & ((1 << getDatabase().tablePartition) - 1));
    }

    public short getPlayerId() {
        checkRequiredData();
        return this.playerId;
    }

    public void setPlayerId(short playerId) {
        this.playerId = playerId;
    }
}
