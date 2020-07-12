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

import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

public enum Loggers {
    WORLDEDIT(false, "World Edit"),
    TILES(true, "Tiles"),
    BLOCK_BREAK(true, "Block break"),
    BLOCK_PLACE(true, "Block place"),
    PHYSICS(true, "Physics check"),
    FALLING_BLOCK(true, "Falling block"),
    BLOCK_EXPLOSION(true, "Block explosion");

    private final String loggerName;
    private boolean enabled;

    Loggers(boolean enabled, String loggerName) {
        this.enabled = enabled;
        this.loggerName = loggerName;
    }

    public static void setup(Config cfg) {
        for (String value : cfg.getStringList("loggers")) {
            switch (value) {
                case "tiles":
                    TILES.enabled = cfg.getBoolean("loggers." + value);
                    break;
                case "block-break":
                    BLOCK_BREAK.enabled = cfg.getBoolean("loggers." + value);
                    break;
                case "block-explosion":
                    BLOCK_EXPLOSION.enabled = cfg.getBoolean("loggers." + value);
                    break;
                case "block-place":
                    BLOCK_PLACE.enabled = cfg.getBoolean("loggers." + value);
                    break;
                case "physics":
                    PHYSICS.enabled = cfg.getBoolean("loggers." + value);
                    break;
                case "falling-block":
                    FALLING_BLOCK.enabled = cfg.getBoolean("loggers." + value);
                    break;
                default:
                    Utils.send(TextFormat.colorize("&cUnknown configuration for loggers: " + value));
                    break;
            }
        }
    }

    public boolean shouldBeEnabled() {
        return enabled;
    }
}
