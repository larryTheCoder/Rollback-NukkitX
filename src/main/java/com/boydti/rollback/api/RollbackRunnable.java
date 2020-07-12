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

import cn.nukkit.Server;
import com.boydti.rollback.util.Utils;

import java.util.concurrent.ConcurrentLinkedQueue;

public class RollbackRunnable {

    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /**
     * Attempts to task a rollback action inside the database runtime thread.
     * Use this only when you are attempting to avoid improper synchronization in main thread.
     * This method will only be ran inside an async/thread task.
     *
     * @param run Runnable object that will be run
     */
    public void addTask(Runnable run) {
        this.tasks.add(run);
    }

    protected void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                Utils.logError("An unexpected error just occurred.", e);
            }
        }
    }

}
