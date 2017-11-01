package com.boydti.rollback.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Connects to and uses a MySQL database
 *
 * @author -_Husky_-
 * @author tips48
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
        user = username;
        this.password = password;
        connection = null;
    }
    
    @Override
    public Connection forceConnection() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + database, user, password);
        return connection;
    }
    
    @Override
    public Connection openConnection() throws SQLException, ClassNotFoundException {
        if (checkConnection()) {
            return connection;
        }
        Class.forName("com.mysql.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + hostname + ":" + port + "/" + database, user, password);
        return connection;
    }
}
