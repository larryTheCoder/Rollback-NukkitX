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

import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.util.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static com.boydti.rollback.util.Utils.printSQLException;

/**
 * Connects to and uses a MySQL database
 *
 * @author -_Husky_-
 * @author tips48
 * @author larryTheCoder
 */
public class MySQL extends SQLDatabase {

    private final String user;
    private final String database;
    private final String password;
    private final int port;
    private final String hostname;
    private Connection connection;

    /**
     * Creates a new MySQL instance
     *
     * @param hostname Name of the host
     * @param port     Port number
     * @param database Database name
     * @param username Username
     * @param password Password
     */
    public MySQL(final String world, final String prefix, final String hostname, final int port, final String database, final String username, final String password) {
        super(world, prefix, true);
        this.hostname = hostname;
        this.port = port;
        this.database = database;
        this.user = username;
        this.password = password;
        this.connection = null;
        this.init();
    }

    @Override
    public void forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        Utils.send("&7Connecting to: jdbc:mysql://" + hostname + ":" + port + "/" + database);
        connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + database, user, password);
    }

    @Override
    public void openConnection() throws SQLException, ClassNotFoundException {
        if (checkConnection()) {
            return;
        }
        Utils.send("&7Reconnecting to: jdbc:mysql://" + hostname + ":" + port + "/" + database);
        Class.forName("com.mysql.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + database, user, password);
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
