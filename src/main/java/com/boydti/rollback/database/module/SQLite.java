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

package com.boydti.rollback.database.module;

import cn.nukkit.Server;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.util.Utils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static com.boydti.rollback.util.Utils.printSQLException;

/**
 * Connects to the localhost sql server
 *
 * @author larryTheCoder
 */
public class SQLite extends SQLDatabase {

    private final File dbLocation;
    private Connection connection;

    /**
     * Creates a new SQLite instance
     *
     * @param dbLocation Location of the Database (Must end in .db)
     */
    public SQLite(final String world, final String prefix, final File dbLocation) {
        super(world, prefix, false);
        this.dbLocation = dbLocation;
        this.init();
    }

    @Override
    public void openConnection() throws SQLException, ClassNotFoundException {
        if (checkConnection()) {
            return;
        }
        if (!Rollback.get().getDataFolder().exists()) {
            Rollback.get().getDataFolder().mkdirs();
        }
        if (!(dbLocation.exists())) {
            try {
                dbLocation.getParentFile().mkdirs();
                dbLocation.createNewFile();
            } catch (final IOException e) {
                Utils.logError("An unexpected error just occurred.", e);
                Utils.send("&cUnable to create database!");
            }
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
    }

    @Override
    public void forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
    }

    @Override
    public void closeConnection() throws SQLException {
        if (connection == null) {
            return;
        }
        connection.close();
        connection = null;
    }

    @Override
    public boolean checkConnection() throws SQLException {
        return (getConnection() != null) && !getConnection().isClosed();
    }

    @Override
    public void commit() {
        commit(true);
    }

    @Override
    protected void commit(boolean setAutoCommit) {
        try {
            if (getConnection() == null) {
                return;
            }
            if (!getConnection().getAutoCommit()) {
                getConnection().commit();
                getConnection().setAutoCommit(setAutoCommit);
            }
        } catch (final SQLException e) {
            printSQLException(e);
        }
    }

    @Override
    public Connection getConnection() {
        return connection;
    }
}
