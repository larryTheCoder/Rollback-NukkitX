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

package com.boydti.rollback.event;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.player.PlayerEvent;
import com.boydti.rollback.block.BlockChange;

/**
 * This event will be called when there
 * is an action or to be easy, place and
 * break event.
 */
public class RollbackActionEvent extends PlayerEvent {

    private static final HandlerList handlers = new HandlerList();
    private final BlockChange changed;

    public RollbackActionEvent(Player p, BlockChange changed) {
        this.player = p;
        this.changed = changed;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    /**
     * Get the chain data of the changed block
     * You will get all of the data from or to what
     * the block been changed or created.
     *
     * @return The chain data of the changed block
     */
    public BlockChange getChanged() {
        return changed;
    }
}
