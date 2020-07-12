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

import cn.nukkit.plugin.PluginBase;
import com.boydti.rollback.object.Session;

/**
 * The main rollback API.
 * Fully documented so you will no confusion while attempt
 * to use this plugin.
 */
@SuppressWarnings("ALL")
public abstract class RollbackAPI extends PluginBase {

    /**
     * Get the session for the player
     * This contains all the data of player
     * actions in the server of this command
     *
     * @param p       The player of the session
     * @param session The session to be put into list
     * @return Session of the player
     */
    public abstract Session setSession(String p, Session session);

    /**
     * Get the session for the player
     * This contains all the data of player
     * actions in the server of this command
     *
     * @param p The player of the session
     * @return Session for the player
     */
    public abstract Session getSession(String p);
}
