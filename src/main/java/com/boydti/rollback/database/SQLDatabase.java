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

package com.boydti.rollback.database;

import cn.nukkit.Server;
import cn.nukkit.math.Vector3;
import com.boydti.rollback.api.AbstractLogger;
import com.boydti.rollback.block.Block;
import com.boydti.rollback.block.BlockChange;
import com.boydti.rollback.block.SimpleBlockChange;
import com.boydti.rollback.database.thread.DatabaseThread;
import com.boydti.rollback.event.RollbackActionEvent;
import com.boydti.rollback.util.MathMan;
import com.boydti.rollback.util.Utils;
import com.nimbusds.jose.util.ArrayUtils;
import com.sk89q.jnbt.CompoundTag;

import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.boydti.rollback.util.Utils.*;

@SuppressWarnings("SqlResolve")
public abstract class SQLDatabase extends AbstractLogger {

    public final int tablePartition = 8;
    public final int timePartition = 4;

    // From table
    public final boolean useServer;
    public final String mainPrefix;

    // Table commands and execution
    private final String
            INSERT_BLOCKNBT,
            INSERT_BLOCKFT,
            INSERT_BLOCKF,
            INSERT_BLOCKT,
            INSERT_CHUNK,
            INSERT_PLAYER,
            GET_PLAYERS,
            GET_CHUNKS,
            GET_BLOCKNBT_POS,
            GET_BLOCKFT_POS,
            GET_BLOCKF_POS,
            GET_BLOCKT_POS,
            GET_BLOCKNBT_VARCHAR_TIME,
            GET_BLOCKFT_VARCHAR_TIME,
            GET_BLOCKF_VARCHAR_TIME,
            GET_BLOCKT_VARCHAR_TIME,
            GET_BLOCKNBT_TIME,
            GET_BLOCKFT_TIME,
            GET_BLOCKF_TIME,
            GET_BLOCKT_TIME;

    // Base time from the database
    public long baseTime;

    // Concurrency control.
    private final ConcurrentLinkedQueue<BlockChange> blockChange = new ConcurrentLinkedQueue<>();

    // These cannot initiate as static methods
    public final Map<Long, Integer> chunkLocId = new ConcurrentHashMap<>();
    public final Map<Integer, Long> chunkIdLoc = new ConcurrentHashMap<>();
    public final Map<String, Short> playerVarcharId = new ConcurrentHashMap<>();
    public final Map<Short, String> playerIdVarchar = new ConcurrentHashMap<>();

    // In order to avoid the common "race-condition"
    private final AtomicLong currentPingedConnection = new AtomicLong();
    private final AtomicLong lastPingedConnection = new AtomicLong();
    private final AtomicInteger lastBatchSize = new AtomicInteger();

    // Purge and stuff
    private final int purgeDays;

    // Thread for the database operations alike
    private DatabaseThread databaseThread;

    protected SQLDatabase(String worldName, String prefix, boolean mySql) {
        super(worldName);
        mainPrefix = (prefix + worldName);
        useServer = mySql;
        purgeDays = com.boydti.rollback.Rollback.get().getConfig().getInt("purge-days", 15);

        // Block data
        INSERT_BLOCKNBT = "INSERT INTO `" + mainPrefix + "blocksnbt$`   (`player`,`chunk`,`pos`,`change`,`time`,`nbtf`,`nbtt`, `reverted`)  VALUES(?,?,?,?,?,?,?,?)";
        INSERT_BLOCKFT = "INSERT INTO `" + mainPrefix + "blocks$`      (`player`,`chunk`,`pos`,`change`,`time`, `reverted`)                VALUES(?,?,?,?,?,?)";
        INSERT_BLOCKF = "INSERT INTO `" + mainPrefix + "blocksf$`     (`player`,`chunk`,`pos`,`change`,`time`, `reverted`)                VALUES(?,?,?,?,?,?)";
        INSERT_BLOCKT = "INSERT INTO `" + mainPrefix + "blockst$`     (`player`,`chunk`,`pos`,`change`,`time`, `reverted`)                VALUES(?,?,?,?,?,?)";

        // Chunk data and player (insert)
        INSERT_CHUNK = "INSERT INTO `" + mainPrefix + "chunks` (`x`,`z`) VALUES(?,?)";
        INSERT_PLAYER = "INSERT INTO `" + mainPrefix + "players` (`name`) VALUES(?)";

        // Chunk data and player (select)
        GET_PLAYERS = "SELECT `id`,`name` FROM `" + mainPrefix + "players`";
        GET_CHUNKS = "SELECT `id`,`x`,`z` FROM `" + mainPrefix + "chunks`";

        // Get block data (select)
        GET_BLOCKFT_POS = "SELECT `player`,`change`,`time`,`reverted`,`timeReverted`                   FROM `" + mainPrefix + "blocks$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKF_POS = "SELECT `player`,`change`,`time`,`reverted`,`timeReverted`                   FROM `" + mainPrefix + "blocksf$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKT_POS = "SELECT `player`,`change`,`time`,`reverted`,`timeReverted`                   FROM `" + mainPrefix + "blockst$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKNBT_POS = "SELECT `player`,`change`,`time`,`nbtf`,`nbtt`,`reverted`,`timeReverted`     FROM `" + mainPrefix + "blocksnbt$` WHERE `chunk`=? AND `pos`=?";

        // Get block data by player and time (select)
        GET_BLOCKNBT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time`,`nbtf`,`nbtt`,`reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocksnbt$` WHERE `player`=? AND `time`>=?";
        GET_BLOCKFT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocks$`    WHERE `player`=? AND `time`>=?";
        GET_BLOCKF_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocksf$`   WHERE `player`=? AND `time`>=?";
        GET_BLOCKT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blockst$`   WHERE `player`=? AND `time`>=?";

        // Get block data by time (select)
        GET_BLOCKNBT_TIME = "SELECT `chunk`,`pos`,`change`,`time`,`nbtf`,`nbtt`,`reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocksnbt$` WHERE `time`>=?";
        GET_BLOCKFT_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocks$`    WHERE `time`>=?";
        GET_BLOCKF_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blocksf$`   WHERE `time`>=?";
        GET_BLOCKT_TIME = "SELECT `chunk`,`pos`,`change`,`time`,              `reverted`,`player`,`timeReverted` FROM `" + mainPrefix + "blockst$`   WHERE `time`>=?";
    }

    @Override
    public void addRevert(String playerName, Vector3 vec, int timeDiff) {
        throw new RuntimeException("Reverting blocks changes are currently not supported by default");
    }

    @Override
    public void logBlock(String playerName, Vector3 vec, short combinedFrom, short combinedTo, CompoundTag nbtFrom, CompoundTag nbtTo) {
        BlockChange change = (BlockChange) new BlockChange(this, vec)
                .setChangeTime((int) ((int) (System.currentTimeMillis() >> timePartition) - baseTime))
                .setBlockCombined(combinedFrom, combinedTo)
                .setReverted(false, 0)
                .setNBTData(nbtFrom, nbtTo)
                .setPlayerName(playerName);

        blockChange.add(change);
        databaseThread.notifyThread();

        RollbackActionEvent actionEvent = new RollbackActionEvent(Server.getInstance().getPlayer(playerName), change);
        Server.getInstance().getPluginManager().callEvent(actionEvent);
    }

    @SuppressWarnings("SqlResolve")
    protected void init() {
        try {
            openConnection();
            DatabaseMetaData dbm = getConnection().getMetaData();
            Statement stmt = getConnection().createStatement();

            ResultSet tables = dbm.getTables(null, null, mainPrefix + "timestamp", null);
            if (tables.next()) {
                // Table exists
                ResultSet r = stmt.executeQuery("SELECT `time` from `" + mainPrefix + "timestamp`");
                if (!r.isClosed() && r.next()) {
                    baseTime = r.getLong(1);
                }

            } else {
                baseTime = (System.currentTimeMillis() >> timePartition);
                if (useServer) {
                    stmt.executeUpdate("SET GLOBAL innodb_file_per_table=1");
                    stmt.executeUpdate("SET GLOBAL innodb_file_format=Barracuda");
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "players` (`id` MEDIUMINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(16) NOT NULL, PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ROW_FORMAT=COMPRESSED");
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "chunks` (`id` MEDIUMINT NOT NULL AUTO_INCREMENT, `x` INT NOT NULL, `z` INT NOT NULL, PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ROW_FORMAT=COMPRESSED");
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "timestamp` (`time` BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
                } else {
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "players` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` VARCHAR(16) NOT NULL)");
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "chunks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `x` INT NOT NULL, `z` INT NOT NULL)");
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "timestamp` (`time` INTEGER NOT NULL)");
                }
                stmt.addBatch("INSERT INTO `" + mainPrefix + "timestamp` (`time`) VALUES(" + baseTime + ")");
                stmt.executeBatch();
                stmt.clearBatch();
            }
            stmt.close();
            stmt = getConnection().createStatement();

            ResultSet set1 = stmt.executeQuery(GET_CHUNKS);

            while (set1.next()) {
                long pair = MathMan.pairInt(set1.getInt(2), set1.getInt(3));
                chunkLocId.put(pair, set1.getInt(1));
                chunkIdLoc.put(set1.getInt(1), pair);
            }
            stmt.close();
            set1.close();
            stmt = getConnection().createStatement();
            set1 = stmt.executeQuery(GET_PLAYERS);

            while (set1.next()) {
                short id = (short) (set1.getShort(1) + Short.MIN_VALUE);
                String name = set1.getString(2);
                playerIdVarchar.put(id, name);
                playerVarcharId.put(name, id);
            }
            set1.close();
            stmt.close();

            createThreads();
        } catch (SQLException e) {
            printSQLException(e);
        } catch (ClassNotFoundException e) {
            Utils.logError("An unexpected error just occurred.", e);
        }
    }

    public void addTask(Runnable run) {
        super.addTask(run);

        databaseThread.notifyThread();
    }

    public void createThreads() {
        databaseThread = new DatabaseThread(this);
        databaseThread.start();
    }

    public boolean sendBatch() {
        PreparedStatement[] blockFtStats;
        PreparedStatement[] blockFStats;
        PreparedStatement[] blockTStats;
        PreparedStatement[] blockNbtStats;
        try {
            runTasks();
            commit(false);

            int size;
            lastBatchSize.set(size = Math.min(0xfffff, blockChange.size()));

            if (size == 0) return false;

            long millis = -1;
            lastPingedConnection.set(millis);
            currentPingedConnection.set(millis = System.currentTimeMillis());

            BlockChange[] copy = new BlockChange[size];
            for (int i = 0; i < size; i++) copy[i] = blockChange.poll();

            PreparedStatement playerStmt = null;
            for (BlockChange change : copy) {
                if (change.emptyPlayerId()) {
                    if (playerStmt == null) {
                        playerStmt = getConnection().prepareStatement(INSERT_PLAYER, Statement.RETURN_GENERATED_KEYS);
                    }

                    createPlayerId(playerStmt, change);
                    change.checkRequiredData();
                }
            }
            if (playerStmt != null) playerStmt.close();

            int minTable = Integer.MAX_VALUE;
            int maxTable = 0;

            PreparedStatement chunkStmt = null;
            for (BlockChange change : copy) {
                if (change.emptyChunkId()) {
                    if (chunkStmt == null) {
                        chunkStmt = getConnection().prepareStatement(INSERT_CHUNK, Statement.RETURN_GENERATED_KEYS);
                    }

                    createChunkId(chunkStmt, change);
                    change.checkRequiredData();
                }

                int table = change.getTableId();
                if (table < minTable) minTable = table;
                if (table > maxTable) maxTable = table;
            }
            if (chunkStmt != null) chunkStmt.close();

            blockFtStats = new PreparedStatement[maxTable - minTable + 1];
            blockFStats = new PreparedStatement[maxTable - minTable + 1];
            blockTStats = new PreparedStatement[maxTable - minTable + 1];
            blockNbtStats = new PreparedStatement[maxTable - minTable + 1];

            for (BlockChange change : copy) {
                int table = change.getChunkId() >> tablePartition;
                int stmtIndex = table - minTable;

                PreparedStatement blockStmt;
                // Creates a new block change
                if (change.hasNBT()) {
                    blockStmt = blockNbtStats[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockNbtStats[stmtIndex] = getConnection().prepareStatement(INSERT_BLOCKNBT.replace("$", table + ""));
                    }
                    blockStmt.setInt(4, change.getChangePair());
                    blockStmt.setBytes(6, Utils.toBytes(change.getTileFrom()));
                    blockStmt.setBytes(7, Utils.toBytes(change.getTileTo()));
                    blockStmt.setBoolean(8, false);
                } else if (change.getBlockTo() == 0) {
                    blockStmt = blockFStats[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockFStats[stmtIndex] = getConnection().prepareStatement(INSERT_BLOCKF.replace("$", table + ""));
                    }
                    blockStmt.setShort(4, change.getBlockFrom());
                    blockStmt.setBoolean(6, false);
                } else if (change.getBlockFrom() == 0) {
                    blockStmt = blockTStats[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockTStats[stmtIndex] = getConnection().prepareStatement(INSERT_BLOCKT.replace("$", table + ""));
                    }
                    blockStmt.setShort(4, change.getBlockTo());
                    blockStmt.setBoolean(6, false);
                } else {
                    blockStmt = blockFtStats[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockFtStats[stmtIndex] = getConnection().prepareStatement(INSERT_BLOCKFT.replace("$", table + ""));
                    }
                    blockStmt.setInt(4, change.getChangePair());
                    blockStmt.setBoolean(6, false);
                }

                blockStmt.setShort(1, change.getPlayerId());
                blockStmt.setByte(2, change.getReducedChunkId());
                blockStmt.setShort(3, getVectorPair(change));
                blockStmt.setInt(5, change.getTimeChanged());
                blockStmt.addBatch();
            }

            PreparedStatement[] res = ArrayUtils.concat(blockFtStats, blockFtStats, blockFStats, blockTStats);
            for (PreparedStatement blockStmt : res) {
                if (blockStmt != null) {
                    blockStmt.executeBatch();
                    blockStmt.close();
                }
            }

            // TODO: Revert blocks.
            // Note: During development of Rollback, I wasn't so sure about what is "race condition",
            //       concurrency, and thread-alike operations. Now, I as much understand the consequences of
            //       creating a code that are not "thread-safe" and perhaps crash in which makes me realise... Rewrite are needed.

            lastPingedConnection.set(System.currentTimeMillis() - millis);

            commit();
            return true;
        } catch (final SQLException e) {
            printSQLException(e);
        }

        return false;
    }

    public void purge() {
        purge((int) TimeUnit.DAYS.toMillis(purgeDays));
    }

    /**
     * Purges all the data inside the sql database
     * This releases all the stress in the server to
     * queries all the data of the block
     *
     * @param diff In milliseconds, the time that need to be deleted
     *             BEFORE 'diff' time
     */
    public void purge(int diff) {
        long now = System.currentTimeMillis() - (baseTime << timePartition);
        long then = System.currentTimeMillis() - diff;
        int deleteAfter = (int) (now - diff);
        if (deleteAfter < 0) {
            return;
        }
        long shift = deleteAfter >> timePartition;

        Set<Integer> tables = new HashSet<>();
        for (Entry<Long, Integer> entry : chunkLocId.entrySet()) {
            Integer chunkId = entry.getValue();
            tables.add(chunkId >> tablePartition);
        }

        for (int table : tables) {
            try {
                String updateTimeBlocks = "UPDATE `" + mainPrefix + "blocks" + table + "` SET time=time-" + shift;
                String updateTimeBlocksf = "UPDATE `" + mainPrefix + "blocks" + table + "` SET time=time-" + shift;
                String updateTimeBlockst = "UPDATE `" + mainPrefix + "blocks" + table + "` SET time=time-" + shift;
                String updateTimeBlocksnbt = "UPDATE `" + mainPrefix + "blocks" + table + "` SET time=time-" + shift;

                String purgeBlocks = "DELETE FROM `" + mainPrefix + "blocks" + table + "` WHERE `time`<?";
                String purgeBlocksf = "DELETE FROM `" + mainPrefix + "blocksf" + table + "` WHERE `time`<?";
                String purgeBlockst = "DELETE FROM `" + mainPrefix + "blockst" + table + "` WHERE `time`<?";
                String purgeBlocksnbt = "DELETE FROM `" + mainPrefix + "blocksnbt" + table + "` WHERE `time`<?";

                PreparedStatement block = getConnection().prepareStatement(updateTimeBlocks);
                PreparedStatement blockNbt = getConnection().prepareStatement(updateTimeBlocksf);
                PreparedStatement blockFromStmt = getConnection().prepareStatement(updateTimeBlockst);
                PreparedStatement blockToStmt = getConnection().prepareStatement(updateTimeBlocksnbt);

                PreparedStatement purgeBlock = getConnection().prepareStatement(purgeBlocks);
                PreparedStatement purgeBlockNbt = getConnection().prepareStatement(purgeBlocksf);
                PreparedStatement purgeBlockFromStmt = getConnection().prepareStatement(purgeBlockst);
                PreparedStatement purgeBlockToStmt = getConnection().prepareStatement(purgeBlocksnbt);

                purgeBlock.setInt(1, deleteAfter);
                purgeBlockNbt.setInt(1, deleteAfter);
                purgeBlockFromStmt.setInt(1, deleteAfter);
                purgeBlockToStmt.setInt(1, deleteAfter);

                block.executeUpdate();
                blockNbt.executeUpdate();
                blockFromStmt.executeUpdate();
                blockToStmt.executeUpdate();

                purgeBlock.executeUpdate();
                purgeBlockNbt.executeUpdate();
                purgeBlockFromStmt.executeUpdate();
                purgeBlockToStmt.executeUpdate();

                block.close();
                blockNbt.close();
                blockFromStmt.close();
                blockToStmt.close();

                purgeBlock.close();
                purgeBlockNbt.close();
                purgeBlockFromStmt.close();
                purgeBlockToStmt.close();
            } catch (SQLException e) {
                printSQLException(e);
            }
        }

        try (PreparedStatement stmt = getConnection().prepareStatement("UPDATE `" + mainPrefix + "timestamp` SET `time`=?")) {
            stmt.setLong(1, then >> timePartition);
            baseTime = then >> timePartition;
            stmt.executeUpdate();
        } catch (SQLException e) {
            printSQLException(e);
        }
        commit();
    }

    /**
     * Get all of the changes within the point of the location
     * The position is a little bit weird as I cannot find out why
     * For now, you must include the short of the position vector
     *
     * @param chunkX The vector side-x of cartesian angle
     * @param chunkZ The vector side-z of cartesian angle
     * @param pos    Compressed position,
     *               use {@link Utils#getVectorPair(Vector3)} for this operation
     * @return All of the list and its data about the changed block
     */
    public List<Block> getChanges(int chunkX, int chunkZ, short pos) {
        List<Block> changes = new ArrayList<>();
        long pair = MathMan.pairInt(chunkX, chunkZ);

        Integer chunkId = chunkLocId.get(pair);
        if (chunkId == null) {
            return changes;
        }

        byte xz = (byte) (pos & 255);
        int x = (MathMan.unpairIntX(pair) << 4) + MathMan.unpair16x(xz);
        int z = (MathMan.unpairIntY(pair) << 4) + MathMan.unpair16y(xz);

        Vector3 vec = new Vector3(x, (pos >> 8) & 255, z);

        int table = chunkId >> tablePartition;
        byte chunkLocalId = (byte) (chunkId & ((1 << tablePartition) - 1));

        try {
            String getBlockFromTo = GET_BLOCKFT_POS.replace("$", table + "");
            String getBlockNBT = GET_BLOCKNBT_POS.replace("$", table + "");
            String getBlockFrom = GET_BLOCKF_POS.replace("$", table + "");
            String getBlockTo = GET_BLOCKT_POS.replace("$", table + "");

            PreparedStatement fromToStmt = getConnection().prepareStatement(getBlockFromTo);
            PreparedStatement NBTStmt = getConnection().prepareStatement(getBlockNBT);
            PreparedStatement blockFromStmt = getConnection().prepareStatement(getBlockFrom);
            PreparedStatement blockToStmt = getConnection().prepareStatement(getBlockTo);

            fromToStmt.setByte(1, chunkLocalId);
            fromToStmt.setShort(2, pos);
            NBTStmt.setByte(1, chunkLocalId);
            NBTStmt.setShort(2, pos);
            blockFromStmt.setByte(1, chunkLocalId);
            blockFromStmt.setShort(2, pos);
            blockToStmt.setByte(1, chunkLocalId);
            blockToStmt.setShort(2, pos);

            ResultSet result1 = fromToStmt.executeQuery();
            ResultSet result2 = NBTStmt.executeQuery();
            ResultSet result3 = blockFromStmt.executeQuery();
            ResultSet result4 = blockToStmt.executeQuery();

            while (result1.next()) {
                int fromTo = result1.getInt(2);

                Block lock = new SimpleBlockChange(this, vec)
                        .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                        .setPlayerName(playerIdVarchar.get(result1.getShort(1)))
                        .setChangeTime(result1.getInt(3))
                        .setReverted(result1.getBoolean(4), result1.getInt(5));

                changes.add(lock);
            }

            while (result2.next()) {
                int fromTo = result2.getInt(2);

                changes.add(new SimpleBlockChange(this, vec)
                        .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                        .setPlayerName(playerIdVarchar.get(result2.getShort(1)))
                        .setChangeTime(result2.getInt(3))
                        .setNBTData(toTag(result2.getBytes(4)), toTag(result2.getBytes(5)))
                        .setReverted(result2.getBoolean(6), result2.getInt(7)));
            }

            while (result3.next()) {
                int from = result3.getInt(2);

                changes.add(new SimpleBlockChange(this, vec)
                        .setBlockCombined((short) from, (short) 0)
                        .setPlayerName(playerIdVarchar.get(result3.getShort(1)))
                        .setChangeTime(result3.getInt(3))
                        .setReverted(result3.getBoolean(4), result3.getInt(5)));
            }

            while (result4.next()) {
                int to = result4.getInt(2);

                changes.add(new SimpleBlockChange(this, vec)
                        .setBlockCombined((short) 0, (short) to)
                        .setPlayerName(playerIdVarchar.get(result4.getShort(1)))
                        .setChangeTime(result4.getInt(3))
                        .setReverted(result4.getBoolean(4), result4.getInt(5)));
            }

            changes.sort(Comparator.comparingInt(Block::getTimeChanged));
        } catch (SQLException e) {
            printSQLException(e);
        }
        return changes;
    }

    /**
     * Get all the blocks within the arguments that were given in this function.
     * This returns to the blocks with the specific amount of data, the results
     * should be all of the possible matching blocks of the specific data. This
     * will only return the least matched timing value of the blocks.
     *
     * @param originX    The player x location
     * @param originZ    The player z location
     * @param radius     Radius of the blocks that need to be queried
     * @param playerName The player name who need to be queried
     * @param timeDiff   Milliseconds time of the player who made that action
     * @return The blocks that been queried and placed.
     */
    public Map<Block, Integer> getBlocks(int originX, int originZ, int radius, String playerName, long timeDiff) {
        int timeMinRel = (int) (((System.currentTimeMillis() - timeDiff) >> timePartition) - baseTime);
        int chunkRadSqr = ((radius + 15) >> 4) * ((radius + 15) >> 4);

        HashSet<Integer> tables = new HashSet<>();
        HashMap<Block, Integer> changes = new HashMap<>();
        for (Entry<Long, Integer> entry : chunkLocId.entrySet()) {
            int dx = MathMan.unpairIntX(entry.getKey()) - (originX >> 4);
            int dz = MathMan.unpairIntY(entry.getKey()) - (originZ >> 4);
            int chunkDistSqr = dx * dx + dz * dz;

            if (chunkDistSqr <= chunkRadSqr) {
                tables.add(entry.getValue() >> tablePartition);
            }
        }

        if (tables.isEmpty()) {
            return changes;
        }

        int radiusSqr = radius * radius;

        for (int table : tables) {
            try {
                PreparedStatement fromToStmt;
                PreparedStatement NBTStmt;
                PreparedStatement blockFromStmt;
                PreparedStatement blockToStmt;

                if (playerName == null) {
                    // SqlDatabase (Get the area blocks by time)
                    // Put them all in one, so that they will query all in one time
                    String getBlockFromTo = GET_BLOCKFT_TIME.replace("$", table + "");
                    String getBlockNBT = GET_BLOCKNBT_TIME.replace("$", table + "");
                    String getBlockFrom = GET_BLOCKF_TIME.replace("$", table + "");
                    String getBlockTo = GET_BLOCKT_TIME.replace("$", table + "");

                    fromToStmt = getConnection().prepareStatement(getBlockFromTo);
                    NBTStmt = getConnection().prepareStatement(getBlockNBT);
                    blockFromStmt = getConnection().prepareStatement(getBlockFrom);
                    blockToStmt = getConnection().prepareStatement(getBlockTo);

                    // Set them
                    fromToStmt.setInt(1, timeMinRel);
                    NBTStmt.setInt(1, timeMinRel);
                    blockFromStmt.setInt(1, timeMinRel);
                    blockToStmt.setInt(1, timeMinRel);
                } else {
                    Short playerId = playerVarcharId.get(playerName);

                    // SqlDatabase (Get the area blocks by player and time)
                    // Put them all in one, so that they will query all in one time
                    String getBlockFromTo = GET_BLOCKFT_VARCHAR_TIME.replace("$", table + "");
                    String getBlockNBT = GET_BLOCKNBT_VARCHAR_TIME.replace("$", table + "");
                    String getBlockFrom = GET_BLOCKF_VARCHAR_TIME.replace("$", table + "");
                    String getBlockTo = GET_BLOCKT_VARCHAR_TIME.replace("$", table + "");

                    fromToStmt = getConnection().prepareStatement(getBlockFromTo);
                    NBTStmt = getConnection().prepareStatement(getBlockNBT);
                    blockFromStmt = getConnection().prepareStatement(getBlockFrom);
                    blockToStmt = getConnection().prepareStatement(getBlockTo);

                    // Set them
                    fromToStmt.setShort(1, playerId);
                    fromToStmt.setInt(2, timeMinRel);
                    NBTStmt.setShort(1, playerId);
                    NBTStmt.setInt(2, timeMinRel);
                    blockFromStmt.setShort(1, playerId);
                    blockFromStmt.setInt(2, timeMinRel);
                    blockToStmt.setShort(1, playerId);
                    blockToStmt.setInt(2, timeMinRel);
                }

                // Get the result of it
                ResultSet result1 = fromToStmt.executeQuery();
                ResultSet result2 = NBTStmt.executeQuery();
                ResultSet result3 = blockFromStmt.executeQuery();
                ResultSet result4 = blockToStmt.executeQuery();

                while (result1.next()) {
                    Vector3 vec = Utils.unpairVector(result1.getShort(2), chunkIdLoc.get((table << tablePartition) + (result1.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    int fromTo = result1.getInt(3);
                    String playerData = playerIdVarchar.get(result1.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                            .setPlayerName(playerData)
                            .setChangeTime(result1.getInt(4))
                            .setReverted(result1.getBoolean(5), result1.getInt(7));

                    Integer existing = changes.get(change);
                    if (existing != null) {
                        if (existing < change.getTimeChanged()) {
                            continue;
                        }
                        changes.remove(change);
                    }
                    changes.put(change, change.getTimeChanged());
                }

                while (result2.next()) {
                    Vector3 vec = Utils.unpairVector(result2.getShort(2), chunkIdLoc.get((table << tablePartition) + (result2.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    int fromTo = result2.getInt(3);
                    String playerData = playerIdVarchar.get(result2.getShort(8));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                            .setNBTData(toTag(result2.getBytes(5)), toTag(result2.getBytes(6)))
                            .setPlayerName(playerData)
                            .setChangeTime(result2.getInt(4))
                            .setReverted(result2.getBoolean(7), result2.getInt(9));

                    Integer existing = changes.get(change);
                    if (existing != null) {
                        if (existing < change.getTimeChanged()) {
                            continue;
                        }
                        changes.remove(change);
                    }
                    changes.put(change, change.getTimeChanged());
                }

                while (result3.next()) {
                    Vector3 vec = Utils.unpairVector(result3.getShort(2), chunkIdLoc.get((table << tablePartition) + (result3.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    short from = result3.getShort(3);
                    String playerData = playerIdVarchar.get(result3.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(from, (short) 0)
                            .setPlayerName(playerData)
                            .setChangeTime(result3.getInt(4))
                            .setReverted(result3.getBoolean(5), result3.getInt(7));

                    Integer existing = changes.get(change);
                    if (existing != null) {
                        if (existing < change.getTimeChanged()) {
                            continue;
                        }
                        changes.remove(change);
                    }
                    changes.put(change, change.getTimeChanged());
                }

                while (result4.next()) {
                    Vector3 vec = Utils.unpairVector(result4.getShort(2), chunkIdLoc.get((table << tablePartition) + (result4.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    short to = result4.getShort(3);
                    String playerData = playerIdVarchar.get(result4.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined((short) 0, to)
                            .setPlayerName(playerData)
                            .setChangeTime(result4.getInt(4))
                            .setReverted(result4.getBoolean(5), result4.getInt(7));

                    Integer existing = changes.get(change);
                    if (existing != null) {
                        if (existing < change.getTimeChanged()) {
                            continue;
                        }
                        changes.remove(change);
                    }
                    changes.put(change, change.getTimeChanged());
                }

                // Close them after finish the operation,
                // Don't pool them
                fromToStmt.close();
                NBTStmt.close();
                blockFromStmt.close();
                blockToStmt.close();

                result1.close();
                result2.close();
                result3.close();
                result4.close();
            } catch (SQLException e) {
                printSQLException(e);
            }
        }
        return changes;
    }

    /**
     * Get all the blocks within the arguments that were given in this function.
     * This returns to the blocks with the specific amount of data, the results
     * should be all of the possible matching blocks of the specific data. This
     * will return the latest blocks changes.
     *
     * @param originX    The player x location
     * @param originZ    The player z location
     * @param radius     Radius of the blocks that need to be queried
     * @param playerName The player name who need to be queried
     * @param timeDiff   Milliseconds time of the player who made that action
     * @return The blocks that been queried and placed.
     */
    public List<Block> getAllBlocks(int originX, int originZ, int radius, String playerName, long timeDiff) {
        int timeMinRel = (int) (((System.currentTimeMillis() - timeDiff) >> timePartition) - baseTime);
        int chunkRadSqr = ((radius + 15) >> 4) * ((radius + 15) >> 4);

        List<Block> changes = new ArrayList<>();
        HashSet<Integer> tables = new HashSet<>();
        for (Entry<Long, Integer> entry : chunkLocId.entrySet()) {
            int dx = MathMan.unpairIntX(entry.getKey()) - (originX >> 4);
            int dz = MathMan.unpairIntY(entry.getKey()) - (originZ >> 4);
            int chunkDistSqr = dx * dx + dz * dz;

            if (chunkDistSqr <= chunkRadSqr) {
                tables.add(entry.getValue() >> tablePartition);
            }
        }

        if (tables.isEmpty()) {
            return changes;
        }

        int radiusSqr = radius * radius;

        for (int table : tables) {
            try {
                PreparedStatement fromToStmt;
                PreparedStatement NBTStmt;
                PreparedStatement blockFromStmt;
                PreparedStatement blockToStmt;

                if (playerName == null) {
                    // SqlDatabase (Get the area blocks by time)
                    // Put them all in one, so that they will query all in one time
                    String getBlockFromTo = GET_BLOCKFT_TIME.replace("$", table + "");
                    String getBlockNBT = GET_BLOCKNBT_TIME.replace("$", table + "");
                    String getBlockFrom = GET_BLOCKF_TIME.replace("$", table + "");
                    String getBlockTo = GET_BLOCKT_TIME.replace("$", table + "");

                    fromToStmt = getConnection().prepareStatement(getBlockFromTo);
                    NBTStmt = getConnection().prepareStatement(getBlockNBT);
                    blockFromStmt = getConnection().prepareStatement(getBlockFrom);
                    blockToStmt = getConnection().prepareStatement(getBlockTo);

                    // Set them
                    fromToStmt.setInt(1, timeMinRel);
                    NBTStmt.setInt(1, timeMinRel);
                    blockFromStmt.setInt(1, timeMinRel);
                    blockToStmt.setInt(1, timeMinRel);
                } else {
                    Short playerId = playerVarcharId.get(playerName);

                    // SqlDatabase (Get the area blocks by player and time)
                    // Put them all in one, so that they will query all in one time
                    String getBlockFromTo = GET_BLOCKFT_VARCHAR_TIME.replace("$", table + "");
                    String getBlockNBT = GET_BLOCKNBT_VARCHAR_TIME.replace("$", table + "");
                    String getBlockFrom = GET_BLOCKF_VARCHAR_TIME.replace("$", table + "");
                    String getBlockTo = GET_BLOCKT_VARCHAR_TIME.replace("$", table + "");

                    fromToStmt = getConnection().prepareStatement(getBlockFromTo);
                    NBTStmt = getConnection().prepareStatement(getBlockNBT);
                    blockFromStmt = getConnection().prepareStatement(getBlockFrom);
                    blockToStmt = getConnection().prepareStatement(getBlockTo);

                    // Set them
                    fromToStmt.setShort(1, playerId);
                    fromToStmt.setInt(2, timeMinRel);
                    NBTStmt.setShort(1, playerId);
                    NBTStmt.setInt(2, timeMinRel);
                    blockFromStmt.setShort(1, playerId);
                    blockFromStmt.setInt(2, timeMinRel);
                    blockToStmt.setShort(1, playerId);
                    blockToStmt.setInt(2, timeMinRel);
                }

                // Get the result of it
                ResultSet result1 = fromToStmt.executeQuery();
                ResultSet result2 = NBTStmt.executeQuery();
                ResultSet result3 = blockFromStmt.executeQuery();
                ResultSet result4 = blockToStmt.executeQuery();

                while (result1.next()) {
                    Vector3 vec = Utils.unpairVector(result1.getShort(2), chunkIdLoc.get((table << tablePartition) + (result1.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    int fromTo = result1.getInt(3);
                    String playerData = playerIdVarchar.get(result1.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                            .setPlayerName(playerData)
                            .setChangeTime(result1.getInt(4))
                            .setReverted(result1.getBoolean(5), result1.getInt(7));

                    changes.add(change);
                }

                while (result2.next()) {
                    Vector3 vec = Utils.unpairVector(result2.getShort(2), chunkIdLoc.get((table << tablePartition) + (result2.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    int fromTo = result2.getInt(3);
                    String playerData = playerIdVarchar.get(result2.getShort(8));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(MathMan.unpairX(fromTo), MathMan.unpairY(fromTo))
                            .setNBTData(toTag(result2.getBytes(5)), toTag(result2.getBytes(6)))
                            .setPlayerName(playerData)
                            .setChangeTime(result2.getInt(4))
                            .setReverted(result2.getBoolean(7), result2.getInt(9));

                    changes.add(change);
                }

                while (result3.next()) {
                    Vector3 vec = Utils.unpairVector(result3.getShort(2), chunkIdLoc.get((table << tablePartition) + (result3.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    short from = result3.getShort(3);
                    String playerData = playerIdVarchar.get(result3.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined(from, (short) 0)
                            .setPlayerName(playerData)
                            .setChangeTime(result3.getInt(4))
                            .setReverted(result3.getBoolean(5), result3.getInt(7));

                    changes.add(change);
                }

                while (result4.next()) {
                    Vector3 vec = Utils.unpairVector(result4.getShort(2), chunkIdLoc.get((table << tablePartition) + (result4.getByte(1) & 255)));

                    // The distance that above this ignores it.
                    if (vec.distance(new Vector3(originX, vec.y, originZ)) > radiusSqr) {
                        continue;
                    }
                    short to = result4.getShort(3);
                    String playerData = playerIdVarchar.get(result4.getShort(6));

                    Block change = new SimpleBlockChange(this, vec)
                            .setBlockCombined((short) 0, to)
                            .setPlayerName(playerData)
                            .setChangeTime(result4.getInt(4))
                            .setReverted(result4.getBoolean(5), result4.getInt(7));

                    changes.add(change);
                }

                // Close them after finish the operation,
                // Don't pool them
                fromToStmt.close();
                NBTStmt.close();
                blockFromStmt.close();
                blockToStmt.close();

                result1.close();
                result2.close();
                result3.close();
                result4.close();
            } catch (SQLException e) {
                printSQLException(e);
            }
        }
        return changes;
    }

    public abstract void forceConnection() throws SQLException, ClassNotFoundException;

    /**
     * Opens a connection with the database
     *
     * @throws SQLException           if the connection can not be opened
     * @throws ClassNotFoundException if the driver cannot be found
     */
    public abstract void openConnection() throws SQLException, ClassNotFoundException;

    /**
     * Closes the connection with the database
     *
     * @throws SQLException if the connection cannot be closed
     */
    public abstract void closeConnection() throws SQLException;

    /**
     * Checks if a connection is open with the database
     *
     * @return true if the connection is open
     * @throws SQLException if the connection cannot be checked
     */
    public abstract boolean checkConnection() throws SQLException;

    /**
     * Gets the connection with the database.
     *
     * @return Connection with the database, null if none
     */
    public abstract Connection getConnection();

    /**
     * Automatically commit the action
     */
    protected abstract void commit();

    /**
     * Automatically commit an action by
     * their setAutoCommit.
     *
     * @param setAutoCommit Should the task be committed?
     */
    protected abstract void commit(boolean setAutoCommit);

    /**
     * Attempts to create a new player and updates its keys into the program.
     * This function is used internally.
     *
     * @param statement The statement where the sql will create a new player id.
     * @param block     The block change made by the player.
     * @throws SQLException self-explanatory.
     */
    private void createPlayerId(PreparedStatement statement, BlockChange block) throws SQLException {
        statement.setString(1, block.getPlayerName());
        statement.executeUpdate();
        ResultSet keys = statement.getGeneratedKeys();
        if (keys.next()) {
            block.setPlayerId((short) (keys.getShort(1) + Short.MIN_VALUE));
            playerIdVarchar.put(block.getPlayerId(), block.getPlayerName());
            playerVarcharId.put(block.getPlayerName(), block.getPlayerId());
        }
    }

    private void createChunkId(PreparedStatement stmt, BlockChange block) throws SQLException {
        long pair = MathMan.pairInt(block.getChunkX(), block.getChunkZ());

        stmt.setInt(1, block.getChunkX());
        stmt.setInt(2, block.getChunkZ());
        stmt.executeUpdate();

        ResultSet keys = stmt.getGeneratedKeys();
        if (keys.next()) {
            int chunkId = keys.getInt(1);
            if ((chunkId & ((1 << tablePartition) - 1)) == 0 || block.getDatabase().chunkIdLoc.size() == 0) {
                // Realign the numbers, checked this, its always return 0 if its not exist.
                int table = chunkId >> tablePartition;
                Statement create = getConnection().createStatement();
                if (useServer) {
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocks" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` INT NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocksf" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT  NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blockst" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocksnbt" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `nbtf` BLOB, `nbtt` BLOB, `time` INT NOT NULL, `timeReverted` INT, `reverted` INT DEFAULT FALSE) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                } else {
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocks" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` INT NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT)");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocksf" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT)");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blockst" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT)");
                    create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + mainPrefix + "blocksnbt" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `nbtf` BLOB, `nbtt` BLOB, `time` INT NOT NULL, `reverted` INT DEFAULT FALSE, `timeReverted` INT)");
                }
            }
            block.getDatabase().chunkIdLoc.put(chunkId, pair);
            block.getDatabase().chunkLocId.put(pair, chunkId);
        }
    }

    public int getLastBatchSize() {
        return lastBatchSize.get();
    }

    public long getPing() {
        return lastPingedConnection.get();
    }

    public boolean isBusy() {
        long systemTime = System.currentTimeMillis();
        long batchTime = currentPingedConnection.get();
        long nowTime = systemTime - batchTime;

        // Larger batches
        return nowTime >= 600 && lastPingedConnection.get() == -1;
    }
}
