/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.io.Closeable;
import java.io.IOException;
import java.security.Provider;
import java.security.Security;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author memca
 */
public class Database implements Closeable, AutoCloseable {

    private Connection connection;

    public Database(Properties properties, CountDownLatch latch) throws IOException, SQLException {
        Thread connectthread = new Thread(() -> {
            try {
                try {
                    Class.forName(properties.getProperty("driver")).newInstance();
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | ExceptionInInitializerError ex) {
                    throw new IllegalStateException("Database driver is invalid");
                }

                connection = DriverManager.getConnection(String.format("%s://%s/%s?user=%s&password=%s",
                        properties.getProperty("provider"), properties.getProperty("server"), properties.getProperty("database"),
                        properties.getProperty("user"), properties.getProperty("password")));

                Logger.getLogger(Database.class.toString()).log(Level.INFO, "Successfully connected to database");

                latch.countDown();

            } catch (SQLException ex) {
                Logger.getLogger(Database.class.toString()).log(Level.SEVERE, "Can not connect to database", ex);
                connection = null;
            }
        }, "DB Connector");

        connectthread.start();
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
    }

    static {
        // A hack to speed up SSL certificate creation
        // From https://stackoverflow.com/a/49322949/11489951
        
        String provider = "SunMSCAPI"; // original provider
        String type = "SecureRandom"; // service type
        String alg = "Windows-PRNG"; // algorithm
        String name = String.format("%s.%s", provider, type); // our provider name
        if (Security.getProvider(name) == null) { // don't register twice
            Optional.ofNullable(Security.getProvider(provider)) // only on Windows
                    .ifPresent(p -> Optional.ofNullable(p.getService(type, alg)) // should exist but who knows?
                    .ifPresent(svc -> Security.insertProviderAt( // insert our provider with single SecureRandom service
                    new Provider(name, p.getVersion(), null) {
                {
                    setProperty(String.format("%s.%s", type, alg), svc.getClassName());
                }
            }, 1)));
        }
    }

}
