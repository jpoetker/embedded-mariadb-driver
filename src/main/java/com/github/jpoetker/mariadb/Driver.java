package com.github.jpoetker.mariadb;

import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import org.mariadb.jdbc.HostAddress;
import org.mariadb.jdbc.UrlParser;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientConnectionException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Driver that will connect to a local database instance if one is running, and start
 * an embedded MariaDB instance for connections if one is not running.
 * <p>
 * This driver assumes you are always connecting to "localhost", and is intended to be
 * used only for integration tests.
 *
 */
public class Driver implements java.sql.Driver {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Driver.class);

    private static final Map<String, DB> databases = new ConcurrentHashMap<>();

    private final org.mariadb.jdbc.Driver mariadbDriver;

    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
            throw new RuntimeException("Could not register driver", e);
        }
    }

    public Driver() {
        mariadbDriver = new org.mariadb.jdbc.Driver();
    }

    public Connection connect(String url, Properties info) throws SQLException {
        try {
            return mariadbDriver.connect(url, info);
        } catch (SQLNonTransientConnectionException e) {
            return startDbAndConnect(url, info);
        }
    }

    public boolean acceptsURL(String url) throws SQLException {
        return mariadbDriver.acceptsURL(url);
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return mariadbDriver.getPropertyInfo(url, info);
    }

    public int getMajorVersion() {
        return mariadbDriver.getMajorVersion();
    }

    public int getMinorVersion() {
        return mariadbDriver.getMinorVersion();
    }

    public boolean jdbcCompliant() {
        return mariadbDriver.jdbcCompliant();
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    /**
     * Starts a database using MariaDB4j on the port specified in the JDBC URL
     *
     * @param url
     * @param info
     * @return
     * @throws SQLException
     */
    private Connection startDbAndConnect(String url, Properties info) throws SQLException {
        UrlParser urlParser = UrlParser.parse(url, info);
        if (urlParser != null) {
            HostAddress local = localhost(urlParser.getHostAddresses());

            if (local != null) {
                String key = String.format("%s:%d", local.host, local.port);
                synchronized (databases) {
                    if (!databases.containsKey(key)) {
                        try {
                            log.info("Creating Embedded MariaDB : {}", key);
                            DB db = DB.newEmbeddedDB(local.port);

                            log.debug("Starting Embedded MariaDB : {}", key);
                            db.start();
                            databases.put(key, db);
                            Runtime.getRuntime().addShutdownHook(
                                    new Thread(new ShutdownDatabase(key), String.format("[%s] Shutdown Hook", key)));
                        } catch (ManagedProcessException e) {
                            throw new IllegalStateException("Unable to start database", e);
                        }
                    }
                }
            }
        }

        return mariadbDriver.connect(url, info);
    }

    private HostAddress localhost(List<HostAddress> hostAddresses) {
        for(HostAddress h : hostAddresses) {
            if ("localhost".equalsIgnoreCase(h.host)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Registered as a shutdown hook with Runtime to stop the database when the
     * JVM stops.
     *
     */
    private static class ShutdownDatabase implements Runnable {
        private final String key;

        public ShutdownDatabase(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            DB db = databases.remove(key);
            if (db != null) {
                try {
                    log.debug("Stopping Embedded MariaDB : {}", key);
                    db.stop();
                } catch (ManagedProcessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
