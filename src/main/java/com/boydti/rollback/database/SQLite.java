package com.boydti.rollback.database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.boydti.rollback.Rollback;

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
    }

    @Override
    public Connection openConnection() throws SQLException, ClassNotFoundException {
        if (checkConnection()) {
            return connection;
        }
        if (!Rollback.get().getDirectory().exists()) {
            Rollback.get().getDirectory().mkdirs();
        }
        if (!(dbLocation.exists())) {
            try {
                dbLocation.getParentFile().mkdirs();
                dbLocation.createNewFile();
            } catch (final IOException e) {
                e.printStackTrace();
                Rollback.debug("&cUnable to create database!");
            }
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        return connection;
    }

    @Override
    public Connection forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbLocation);
        return connection;
    }
}
