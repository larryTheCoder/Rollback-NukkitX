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

import cn.nukkit.plugin.PluginBase;
import com.boydti.rollback.Rollback;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author larryTheCoder
 * @author tastybento
 */
public final class FileLister {

    private final String folderPath;
    private final boolean filterYML;

    public FileLister(String folderPath, boolean filterYML) {
        this.filterYML = filterYML;
        this.folderPath = folderPath;
    }

    public List<String> list() throws IOException {
        Rollback plugin = Rollback.get();
        List<String> result = new ArrayList<>();

        // Check if the locale folder exists
        File localeDir = new File(plugin.getDataFolder(), folderPath);
        if (localeDir.exists()) {
            FilenameFilter ymlFilter = (File dir, String name) -> {
                String lowercaseName = name.toLowerCase();
                if (filterYML) {
                    return lowercaseName.endsWith(".yml");
                }

                return true;
            };

            for (String fileName : Objects.requireNonNull(localeDir.list(ymlFilter))) {
                result.add(fileName.replace(".yml", ""));
            }

            if (!result.isEmpty()) {
                return result;
            }
        }
        // Else look in the JAR
        File jarfile;

        try {
            Method method = PluginBase.class.getDeclaredMethod("getFile");
            method.setAccessible(true);

            jarfile = (File) method.invoke(Rollback.get());
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new IOException(e);
        }

        try (JarFile jar = new JarFile(jarfile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String path = entry.getName();

                if (!path.startsWith(folderPath)) {
                    continue;
                }

                if (entry.getName().endsWith(".yml")) {
                    result.add((entry.getName().replace(".yml", "")).replace("locale/", ""));
                }

            }
        }
        return result;
    }
}
