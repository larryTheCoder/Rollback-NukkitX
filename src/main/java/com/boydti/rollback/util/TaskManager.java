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
package com.boydti.rollback.util;


import cn.nukkit.Server;
import com.boydti.rollback.Rollback;

public class TaskManager {

    public static TaskManager IMP;

    public static void runTaskRepeatAsync(Runnable runnable, int interval) {
        if (runnable != null) {
            if (IMP == null) {
                throw new IllegalArgumentException("disabled");
            }
            IMP.taskRepeatAsync(runnable, interval);
        }
    }

    public static void runTaskAsync(Runnable runnable) {
        if (runnable != null) {
            if (IMP == null) {
                runnable.run();
                return;
            }
            IMP.taskAsync(runnable);
        }
    }

    public static void runTask(Runnable runnable) {
        if (runnable != null) {
            if (IMP == null) {
                runnable.run();
                return;
            }
            IMP.task(runnable);
        }
    }

    /**
     * Run task later.
     *
     * @param runnable The task
     * @param delay    The delay in ticks (milliseconds)
     */
    public static void runTaskLater(Runnable runnable, int delay) {
        if (runnable != null) {
            if (IMP == null) {
                runnable.run();
                return;
            }
            IMP.taskLater(runnable, delay);
        }
    }

    private void taskRepeatAsync(Runnable r, int interval) {
        Server.getInstance().getScheduler().scheduleRepeatingTask(Rollback.get(), r, interval, true);
    }

    private void taskAsync(Runnable r) {
        if (r == null) {
            return;
        }
        Server.getInstance().getScheduler().scheduleTask(Rollback.get(), r, true);
    }

    private void task(Runnable r) {
        if (r == null) {
            return;
        }
        Server.getInstance().getScheduler().scheduleTask(Rollback.get(), r, false);
    }

    private void taskLater(Runnable r, int delay) {
        if (r == null) {
            return;
        }
        Server.getInstance().getScheduler().scheduleDelayedTask(Rollback.get(), r, delay);
    }

}
