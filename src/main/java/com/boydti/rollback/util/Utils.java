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

import cn.nukkit.Server;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;
import com.boydti.fawe.util.MathMan;
import com.boydti.rollback.Rollback;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import lombok.extern.log4j.Log4j2;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

@Log4j2
public class Utils {

    public static boolean isInteger(String object) {
        try {
            Integer.parseInt(object);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean ignoreSQLException(String sqlState) {
        if (sqlState == null) {
            Server.getInstance().getLogger().notice("The SQL state is not defined!");
            return false;
        }
        // X0Y32: Jar file already exists in schema
        if (sqlState.equalsIgnoreCase("X0Y32")) {
            return true;
        }
        // 42Y55: Table already exists in schema
        return sqlState.equalsIgnoreCase("42Y55");
    }

    public static void printSQLException(SQLException ex) {
        for (Throwable e : ex) {
            if (!(e instanceof SQLException) || ignoreSQLException(((SQLException) e).getSQLState())) {
                continue;
            }
            Server.getInstance().getLogger().notice("SQLState: " + ((SQLException) e).getSQLState());
            Server.getInstance().getLogger().notice("Error Code: " + ((SQLException) e).getErrorCode());
            Server.getInstance().getLogger().notice("Message: " + e.getMessage());

            Server.getInstance().getLogger().logException(ex);
            Throwable t = ex.getCause();
            while (t != null) {
                Server.getInstance().getLogger().notice("Cause: " + t);
                t = t.getCause();
            }
        }
    }

    public static CompoundTag toTag(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        byte[] buffer = new byte[531441];
        try {
            LZ4SafeDecompressor compress = LZ4Factory.fastestInstance().safeDecompressor();
            int decompressedLength = compress.decompress(compressed, 0, compressed.length, buffer, 0);
            byte[] copy = new byte[decompressedLength];
            System.arraycopy(buffer, 0, copy, 0, decompressedLength);
            try (NBTInputStream nbt = new NBTInputStream(new ByteArrayInputStream(copy))) {
                return (CompoundTag) nbt.readNamedTag().getTag();
            }
        } catch (IOException e) {
            Utils.logError("An unexpected error just occurred.", e);
        }
        return null;
    }

    private static byte[] compress(byte[] src) {
        LZ4Compressor compressor = LZ4Factory.fastestInstance().highCompressor(17);
        int maxCompressedLength = compressor.maxCompressedLength(src.length);
        byte[] compressed = new byte[maxCompressedLength];
        int compressLen = compressor.compress(src, 0, src.length, compressed, 0, maxCompressedLength);
        return Arrays.copyOf(compressed, compressLen);
    }

    public static Vector3 unpairVector(short pos, long chunkLoc) {
        byte xz = (byte) (pos & 255);
        int x = (MathMan.unpairIntX(chunkLoc) << 4) + MathMan.unpair16x(xz);
        int z = (MathMan.unpairIntY(chunkLoc) << 4) + MathMan.unpair16y(xz);
        return new Vector3(x, (pos >> 8) & 255, z);
    }

    public static short getVectorPair(Vector3 vec) {
        return (short) ((MathMan.pair16((vec.getFloorX() & 15), vec.getFloorZ() & 15) & 255) + (vec.getFloorY() << 8));
    }

    public static byte[] toBytes(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                try (NBTOutputStream stream = new NBTOutputStream(bytes)) {
                    stream.writeNamedTag("1", tag);
                    return compress(bytes.toByteArray());
                }
            }
        } catch (IOException ex) {
            Utils.logError("An unexpected error just occurred.", ex);
        }
        return null;
    }

    public static void send(String msg) {
        log.info(Rollback.get().getPrefix() + TextFormat.GREEN + msg.replace("&", "§"));
    }

    public static void logError(String msg, Throwable err) {
        log.error(Rollback.get().getPrefix() + TextFormat.RED + msg.replace("&", "§"), err);
    }

    public static long timeToSec(String string) {
        if (isInteger(string)) return Long.parseLong(string);

        string = string.toLowerCase().trim().toLowerCase();
        if (string.equalsIgnoreCase("false")) {
            return 0;
        }
        String[] split = string.split(" ");
        long time = 0;
        for (String value : split) {
            int nums = Integer.parseInt(value.replaceAll("[^\\d]", ""));
            String letters = value.replaceAll("[^a-z]", "");
            switch (letters) {
                case "week":
                case "weeks":
                case "wks":
                case "w":

                    time += 604800 * nums;
                case "days":
                case "day":
                case "d":
                    time += 86400 * nums;
                case "hour":
                case "hr":
                case "hrs":
                case "hours":
                case "h":
                    time += 3600 * nums;
                case "minutes":
                case "minute":
                case "mins":
                case "min":
                case "m":
                    time += 60 * nums;
                case "seconds":
                case "second":
                case "secs":
                case "sec":
                case "s":
                    time += nums;
            }
        }
        return time;
    }
}
