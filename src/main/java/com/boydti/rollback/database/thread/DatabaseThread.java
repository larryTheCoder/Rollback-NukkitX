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

package com.boydti.rollback.database.thread;

import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.database.module.MySQL;
import com.boydti.rollback.util.TaskManager;
import com.boydti.rollback.util.Utils;

import java.sql.SQLException;

import static com.boydti.rollback.util.Utils.printSQLException;

/**
 * A database thread that is relying on notifications. Less CPU resources are being used
 * and more threads can get started in a server with a lot of worlds.
 */
public class DatabaseThread extends Thread {

    private final SQLDatabase database;

    public DatabaseThread(SQLDatabase database) {
        this.database = database;

        setName("Rollback Database Thread");
    }

    /**
     * Wake up this thread from being sleeping.
     */
    public void notifyThread() {
        synchronized (this) {
            notify();
        }
    }

    private boolean isPurged = false;
    private long lastExecution = System.currentTimeMillis();

    @Override
    public void run() {
        while (database.getConnection() != null) {
            if ((database instanceof MySQL) && ((System.currentTimeMillis() - lastExecution) > 550000)) {
                lastExecution = System.currentTimeMillis();
                try {
                    database.closeConnection();
                    database.forceConnection();
                } catch (SQLException e) {
                    printSQLException(e);
                } catch (ClassNotFoundException e) {
                    Utils.logError("An unexpected error just occurred.", e);
                }
            }

            if (!isPurged) {
                isPurged = true;

                database.purge();
            }

            if (database.sendBatch()) continue;

            try {
                synchronized (this) {
                    wait(15000);
                }
            } catch (InterruptedException e) {
                // Return to main thread.
                TaskManager.runTask(() -> Utils.send("Interrupted exception are raised while waiting for resources"));
            }
        }
    }
}
