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

import cn.nukkit.utils.TextFormat;
import com.boydti.rollback.Rollback;
import com.google.common.collect.Maps;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Map;

/**
 * Attempts to load the required libraries and inject its classpath
 * into the server/plugin.
 */
@Log4j2
public class Autoloader {

    private final String DIRECTORY;

    private final Map<String, String> reqLib = Maps.newHashMap();
    private final List<String> files;

    @SneakyThrows
    public Autoloader(String targetFolder) {
        DIRECTORY = Rollback.get().getDataFolder() + File.separator + targetFolder + File.separator;

        createDirectory0(DIRECTORY);

        reqLib.put("mysql-connector-java-8.0.16.jar", "https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.16/mysql-connector-java-8.0.16.jar");
        reqLib.put("sqlite-jdbc-3.27.2.1.jar", "https://bitbucket.org/xerial/sqlite-jdbc/downloads/sqlite-jdbc-3.27.2.1.jar");

        files = new FileLister(targetFolder, false).list();
        attemptAutoloadMerge();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createDirectory0(String dirName) {
        File pDir = new File(dirName);
        if (pDir.isDirectory()) return;

        pDir.mkdir();
    }

    private void attemptAutoloadMerge() {
        // Step 1: Attempts to check if database libraries are present.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Class.forName("org.sqlite.JDBC");

            log.debug("Found required libraries, no download needed.");
            return;
        } catch (ClassNotFoundException ignored) {
        }

        // Step 2: Attempt to download the required libraries from desired site.
        reqLib.keySet().stream().filter(lib -> files.stream().noneMatch(i -> i.equalsIgnoreCase(lib))).forEach(libs -> {
            log.info(Rollback.get().getPrefix() + TextFormat.YELLOW + "Attempting to download library: " + libs);

            try {
                final URL website = new URL(reqLib.get(libs));

                @Cleanup final ReadableByteChannel rbc = Channels.newChannel(website.openStream());

                @Cleanup final FileOutputStream fos = new FileOutputStream(DIRECTORY + libs);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (IOException exception) {
                throw new RuntimeException("Please check your internet connection before installing...", exception);
            }
        });

        File mysqlDriver = new File(DIRECTORY + "mysql-connector-java-8.0.16.jar");
        File sqliteDriver = new File(DIRECTORY + "sqlite-jdbc-3.27.2.1.jar");

        try {
            // Step 3: Merge these libraries
            injectClass(mysqlDriver, "com.mysql.cj.jdbc.Driver");
            injectClass(sqliteDriver, "org.sqlite.JDBC");

            log.debug("Injected database libraries.");
        } catch (Throwable err) {
            err.printStackTrace();
        }
    }

    @SneakyThrows
    private void injectClass(File file, String classLoader) {
        log.debug("Attempting to reflect " + classLoader + " classpath");

        URLClassLoader autoload = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        method.setAccessible(true);
        method.invoke(autoload, file.toURI().toURL());
    }
}
