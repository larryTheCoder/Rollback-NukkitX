package com.boydti.rollback.database;

import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.TaskManager;
import com.boydti.rollback.api.AbstractLogger;
import com.boydti.rollback.config.Config;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.NBTInputStream;
import com.sk89q.jnbt.NBTOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4InputStream;
import net.jpountz.lz4.LZ4SafeDecompressor;

public abstract class SQLDatabase extends AbstractLogger {
    
    private final String prefix;
    private Connection connection;
    private final boolean mySql;
    
    private static LZ4Factory factory = LZ4Factory.fastestInstance();
    private static LZ4Compressor compressor = factory.highCompressor(17);

    public ConcurrentLinkedQueue<Runnable> notify = new ConcurrentLinkedQueue<>();
    public ConcurrentLinkedQueue<BlockChange> blockChanges = new ConcurrentLinkedQueue<>();

    public final String INSERT_BLOCKNBT;
    public final String INSERT_BLOCKFT;
    public final String INSERT_BLOCKF;
    public final String INSERT_BLOCKT;
    public final String INSERT_CHUNK;
    public final String INSERT_PLAYER;
    
    public final String GET_PLAYERS;
    public final String GET_CHUNKS;
    
    public final String PURGE_BLOCKS;
    public final String PURGE_BLOCKSF;
    public final String PURGE_BLOCKST;
    public final String PURGE_BLOCKSNBT;
    
    private final String UPDATE_TIME;
    private final String UPDATE_TIME_BLOCKS;
    private final String UPDATE_TIME_BLOCKSF;
    private final String UPDATE_TIME_BLOCKST;
    private final String UPDATE_TIME_BLOCKSNBT;

    public final String GET_BLOCKNBT_POS;
    public final String GET_BLOCKFT_POS;
    public final String GET_BLOCKF_POS;
    public final String GET_BLOCKT_POS;
    public final String GET_BLOCKNBT_VARCHAR_TIME;
    public final String GET_BLOCKFT_VARCHAR_TIME;
    public final String GET_BLOCKF_VARCHAR_TIME;
    public final String GET_BLOCKT_VARCHAR_TIME;
    
    public final int TABLE_PARTITION = 8;
    public final int TIME_PARTITION = 0;

    private final HashMap<Long, Integer> CHUNK_LOC_ID = new HashMap<>();
    private final HashMap<String, Short> PLAYER_VARCHAR_ID = new HashMap<>();
    
    private final HashMap<Integer, Long> CHUNK_ID_LOC = new HashMap<>();
    private final HashMap<Short, String> PLAYER_ID_VARCHAR = new HashMap<>();
    
    // From table
    private long BASE_TIME;
    
    public SQLDatabase(final String world, String prefix, boolean mySql) {
        super(world);
        this.prefix = (prefix = prefix + world);
        this.mySql = mySql;
        INSERT_BLOCKNBT = "INSERT INTO `" + prefix + "blocksnbt$` (`player`,`chunk`,`pos`,`change`,`time`,`nbtf`,`nbtt`) VALUES(?,?,?,?,?,?,?)";
        INSERT_BLOCKFT = "INSERT INTO `" + prefix + "blocks$` (`player`,`chunk`,`pos`,`change`,`time`) VALUES(?,?,?,?,?)";
        INSERT_BLOCKF = "INSERT INTO `" + prefix + "blocksf$` (`player`,`chunk`,`pos`,`change`,`time`) VALUES(?,?,?,?,?)";
        INSERT_BLOCKT = "INSERT INTO `" + prefix + "blockst$` (`player`,`chunk`,`pos`,`change`,`time`) VALUES(?,?,?,?,?)";
        
        INSERT_CHUNK = "INSERT INTO `" + prefix + "chunks` (`x`,`z`) VALUES(?,?)";
        INSERT_PLAYER = "INSERT INTO `" + prefix + "players` (`name`) VALUES(?)";
        
        GET_PLAYERS = "SELECT `id`,`name` FROM `" + prefix + "players`";
        GET_CHUNKS = "SELECT `id`,`x`,`z` FROM `" + prefix + "chunks`";
        
        GET_BLOCKFT_POS = "SELECT `player`,`change`,`time` FROM `" + prefix + "blocks$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKF_POS = "SELECT `player`,`change`,`time` FROM `" + prefix + "blocksf$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKT_POS = "SELECT `player`,`change`,`time` FROM `" + prefix + "blockst$` WHERE `chunk`=? AND `pos`=?";
        GET_BLOCKNBT_POS = "SELECT `player`,`change`,`time`,`nbtf`,`nbtt` FROM `" + prefix + "blocksnbt$` WHERE `chunk`=? AND `pos`=?";
        
        UPDATE_TIME = "UPDATE `" + prefix + "timestamp` SET `time`=?";
        UPDATE_TIME_BLOCKS = "UPDATE `" + prefix + "blocks$` SET time=time-?";
        UPDATE_TIME_BLOCKSF = "UPDATE `" + prefix + "blocks$` SET time=time-?";
        UPDATE_TIME_BLOCKST = "UPDATE `" + prefix + "blocks$` SET time=time-?";
        UPDATE_TIME_BLOCKSNBT = "UPDATE `" + prefix + "blocks$` SET time=time-?";

        GET_BLOCKNBT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time`,`nbtf`,`nbtt` FROM `" + prefix + "blocksnbt$` WHERE `player`=? AND `time`>=?";
        GET_BLOCKFT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time` FROM `" + prefix + "blocks$` WHERE `player`=? AND `time`>=?";
        GET_BLOCKF_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time` FROM `" + prefix + "blocksf$` WHERE `player`=? AND `time`>=?";
        GET_BLOCKT_VARCHAR_TIME = "SELECT `chunk`,`pos`,`change`,`time` FROM `" + prefix + "blockst$` WHERE `player`=? AND `time`>=?";
        
        PURGE_BLOCKS = "DELETE FROM `" + prefix + "blocks$` WHERE `time`<?";
        PURGE_BLOCKSF = "DELETE FROM `" + prefix + "blocksf$` WHERE `time`<?";
        PURGE_BLOCKST = "DELETE FROM `" + prefix + "blockst$` WHERE `time`<?";
        PURGE_BLOCKSNBT = "DELETE FROM `" + prefix + "blocksnbt$` WHERE `time`<?";
    }
    
    public byte[] toBytes(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        try {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                try (NBTOutputStream stream = new NBTOutputStream(baos)) {
                    stream.writeNamedTag("1", tag);
                    return compress(baos.toByteArray());
                }
            }
        } catch (Exception ignore) {
            ignore.printStackTrace();
        }
        return null;
    }
    
    public static byte[] compress(byte[] src) {
        int maxCompressedLength = compressor.maxCompressedLength(src.length);
        byte[] compressed = new byte[maxCompressedLength];
        int compressLen = compressor.compress(src, 0, src.length, compressed, 0, maxCompressedLength);
        byte[] finalCompressedArray = Arrays.copyOf(compressed, compressLen);
        return finalCompressedArray;
    }
    
    private final byte[] buffer = new byte[Settings.HISTORY.BUFFER_SIZE];
    
    public CompoundTag toTag(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        try {
            LZ4SafeDecompressor decompressor = factory.safeDecompressor();
            int decompressedLength = decompressor.decompress(compressed, 0, compressed.length, buffer, 0);
            byte[] copy = new byte[decompressedLength];
            System.arraycopy(buffer, 0, copy, 0, decompressedLength);
            try (NBTInputStream nbt = new NBTInputStream(new ByteArrayInputStream(copy))) {
                CompoundTag value = (CompoundTag) nbt.readNamedTag().getTag();
                return value;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decompress(byte[] src) {
        try {
            try (LZ4InputStream lz4 = new LZ4InputStream(new ByteArrayInputStream(src))) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int i;
                while ((i = lz4.read()) != -1) {
                    baos.write(i);
                }
                return baos.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private class BlockChange {
        private final String name;
        private final int z;
        private final int y;
        private final int x;
        private final short from;
        private final short to;
        private final int time;
        private final CompoundTag nbtFrom;
        private final CompoundTag nbtTo;
        
        private short playerId = Short.MAX_VALUE;
        private int chunkId = Integer.MAX_VALUE;

        public BlockChange(String name, int x, int y, int z, short combinedFrom, short combinedTo, CompoundTag nbtFrom, CompoundTag nbtTo) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.from = combinedFrom;
            this.to = combinedTo;
            this.nbtFrom = nbtFrom;
            this.nbtTo = nbtTo;
            this.time = (int) ((System.currentTimeMillis() >> TIME_PARTITION) - BASE_TIME);
        }
        
        public int getTime() {
            return time;
        }
        
        public boolean hasNBT() {
            return nbtFrom != null || nbtTo != null;
        }
        
        public byte[] getNbtTo() {
            return toBytes(nbtTo);
        }
        
        public byte[] getNbtFrom() {
            return toBytes(nbtFrom);
        }

        public boolean hasPlayerId() {
            Short id1 = PLAYER_VARCHAR_ID.get(name);
            if (id1 != null) {
                playerId = id1;
            }
            return playerId != Short.MAX_VALUE;
        }

        public short getPlayerId() {
            return playerId;
        }
        
        public boolean hasChunkId() {
            long pair = MathMan.pairInt(x >> 4, z >> 4);
            Integer id2 = CHUNK_LOC_ID.get(pair);
            if (id2 != null) {
                chunkId = id2;
            }
            return chunkId != Integer.MAX_VALUE;
        }
        
        public int getChunkId() {
            return chunkId;
        }
        
        public int getTableId() {
            return chunkId >> TABLE_PARTITION;
        }
        
        public byte getReducedChunkId() {
            return (byte) (chunkId & ((1 << TABLE_PARTITION) - 1));
        }
        
        public int createChunkId(PreparedStatement stmt) throws SQLException {
            long pair = MathMan.pairInt(x >> 4, z >> 4);
            stmt.setInt(1, x >> 4);
            stmt.setInt(2, z >> 4);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                chunkId = keys.getInt(1);
                int table = chunkId >> TABLE_PARTITION;
                if ((chunkId & ((1 << TABLE_PARTITION) - 1)) == 0 || CHUNK_ID_LOC.size() == 0) {
                    try (Statement create = connection.createStatement()) {
                        if (mySql) {
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocks" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` INT NOT NULL, `time` INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocksf" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT  NOT NULL, `time` INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blockst" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocksnbt" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `nbtf` BLOB, `nbtt` BLOB, `time` INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=COMPRESSED");
                        } else {
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocks" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` INT NOT NULL, `time` INT NOT NULL)");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocksf" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL)");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blockst" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `time` INT NOT NULL)");
                            create.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "blocksnbt" + table + "` (`player` SMALLINT NOT NULL, `chunk` TINYINT NOT NULL, `pos` SMALLINT NOT NULL, `change` SMALLINT NOT NULL, `nbtf` BLOB, `nbtt` BLOB, `time` INT NOT NULL)");
                        }
                    }
                }
                CHUNK_ID_LOC.put(chunkId, pair);
                CHUNK_LOC_ID.put(pair, chunkId);
                return chunkId;
            }
            return Integer.MAX_VALUE;
        }
        
        public int createPlayerId(PreparedStatement stmt) throws SQLException {
            stmt.setString(1, name);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                playerId = (short) (keys.getShort(1) + Short.MIN_VALUE);
                PLAYER_ID_VARCHAR.put(playerId, name);
                PLAYER_VARCHAR_ID.put(name, playerId);
                return playerId;
            }
            return Integer.MAX_VALUE;
        }
        
        // `player`,`chunk`,`pos`,`change`,`time`
        
        public short getPos() {
            return (short) ((MathMan.pair16((byte) (x & 15), (byte) (z & 15)) & 255) + (y << 8));
        }
        
        public short getFrom() {
            return from;
        }
        
        public short getTo() {
            return to;
        }
        
        public int getChangePair() {
            return (from << 16) | (to & 0xFFFF);
        }
    }

    public void purge(int diff) {
        long now = System.currentTimeMillis() - (BASE_TIME << TIME_PARTITION);
        long then = System.currentTimeMillis() - diff;
        int deleteAfter = (int) (now - diff);
        if (deleteAfter < 0) {
            return;
        }
        long shift = deleteAfter >> TIME_PARTITION;
        Set<Integer> tables = new HashSet<>();
        for (Entry<Long, Integer> entry : CHUNK_LOC_ID.entrySet()) {
            Integer chunkId = entry.getValue();
            tables.add(chunkId >> TABLE_PARTITION);
        }
        int i = 0;
        int size = tables.size();
        for (int table : tables) {
            System.out.println("DELETE " + table);
            try {
                try (PreparedStatement stmt = connection.prepareStatement(PURGE_BLOCKS.replace("$", table + ""))) {
                    stmt.setInt(1, deleteAfter);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(PURGE_BLOCKSF.replace("$", table + ""))) {
                    stmt.setInt(1, deleteAfter);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(PURGE_BLOCKST.replace("$", table + ""))) {
                    stmt.setInt(1, deleteAfter);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(PURGE_BLOCKSNBT.replace("$", table + ""))) {
                    stmt.setInt(1, deleteAfter);
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TIME_BLOCKS.replace("$", table + "").replace("?", "" + shift))) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TIME_BLOCKSF.replace("$", table + "").replace("?", "" + shift))) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TIME_BLOCKST.replace("$", table + "").replace("?", "" + shift))) {
                    stmt.executeUpdate();
                }
                try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TIME_BLOCKSNBT.replace("$", table + "").replace("?", "" + shift))) {
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            System.out.println((double) (i / size) / 100 + "%");
            i += 10000;
        }
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_TIME)) {
            stmt.setLong(1, then >> TIME_PARTITION);
            BASE_TIME = then >> TIME_PARTITION;
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        commit();
        System.out.println("Done purging!");
    }

    public List<SimpleBlockChange> getChanges(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        long pair = MathMan.pairInt(cx, cz);
        Integer chunkId = CHUNK_LOC_ID.get(pair);
        if (chunkId == null) {
            return new ArrayList<>();
        }
        int table = chunkId >> TABLE_PARTITION;
        byte chunkLocalId = (byte) (chunkId & ((1 << TABLE_PARTITION) - 1));
        short pos = (short) ((MathMan.pair16((byte) (x & 15), (byte) (z & 15)) & 255) + (y << 8));
        try {
            ArrayList<SimpleBlockChange> changes = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKFT_POS.replace("$", table + ""))) {
                stmt.setByte(1, chunkLocalId);
                stmt.setShort(2, pos);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        int fromTo = r.getInt(2);
                        long time = (BASE_TIME + r.getInt(3)) << TIME_PARTITION;
                        String player = PLAYER_ID_VARCHAR.get(r.getShort(1));
                        changes.add(new SimpleBlockChange(x, y, z, MathMan.unpairX(fromTo), MathMan.unpairY(fromTo), null, null, player, time));
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKNBT_POS.replace("$", table + ""))) {
                stmt.setByte(1, chunkLocalId);
                stmt.setShort(2, pos);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        int fromTo = r.getInt(2);
                        long time = (BASE_TIME + r.getInt(3)) << TIME_PARTITION;
                        String player = PLAYER_ID_VARCHAR.get(r.getShort(1));
                        changes.add(new SimpleBlockChange(x, y, z, MathMan.unpairX(fromTo), MathMan.unpairY(fromTo), toTag(r.getBytes(4)), toTag(r.getBytes(5)), player, time));
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKF_POS.replace("$", table + ""))) {
                stmt.setByte(1, chunkLocalId);
                stmt.setShort(2, pos);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        long time = (BASE_TIME + r.getInt(3)) << TIME_PARTITION;
                        String player = PLAYER_ID_VARCHAR.get(r.getShort(1));
                        changes.add(new SimpleBlockChange(x, y, z, r.getShort(2), 0, null, null, player, time));
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKT_POS.replace("$", table + ""))) {
                stmt.setByte(1, chunkLocalId);
                stmt.setShort(2, pos);
                try (ResultSet r = stmt.executeQuery()) {
                    while (r.next()) {
                        long time = (BASE_TIME + r.getInt(3)) << TIME_PARTITION;
                        String player = PLAYER_ID_VARCHAR.get(r.getShort(1));
                        changes.add(new SimpleBlockChange(x, y, z, 0, r.getShort(2), null, null, player, time));
                    }
                }
            }
            Collections.sort(changes, new Comparator<SimpleBlockChange>() {
                @Override
                public int compare(SimpleBlockChange a, SimpleBlockChange b) {
                    return (int) (a.timestamp - b.timestamp);
                }
            });
            return changes;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public void logBlock(String name, int x, int y, int z, short combinedFrom, short combinedTo, CompoundTag nbtFrom, CompoundTag nbtTo) {
        BlockChange change = new BlockChange(name, x, y, z, combinedFrom, combinedTo, nbtFrom, nbtTo);
        blockChanges.add(change);
    }

    public void init() {
        try {
            connection = openConnection();
            DatabaseMetaData dbm = connection.getMetaData();
            ResultSet tables = dbm.getTables(null, null, prefix + "timestamp", null);
            if (tables.next()) {
                // Table exists
                try (PreparedStatement stmt = connection.prepareStatement("SELECT `time` from `" + prefix + "timestamp`")) {
                    ResultSet r = stmt.executeQuery();
                    BASE_TIME = r.getLong(1);
                }
            } else {
                BASE_TIME = (System.currentTimeMillis() >> TIME_PARTITION);
                try (Statement stmt = connection.createStatement()) {
                    if (this.mySql) {
                        stmt.executeUpdate("SET GLOBAL innodb_file_per_table=1");
                        stmt.executeUpdate("SET GLOBAL innodb_file_format=Barracuda");
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "players` (`id` MEDIUMINT NOT NULL AUTO_INCREMENT, `name` VARCHAR(16) NOT NULL, PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ROW_FORMAT=COMPRESSED");
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "chunks` (`id` MEDIUMINT NOT NULL AUTO_INCREMENT, `x` INT NOT NULL, `z` INT NOT NULL, PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ROW_FORMAT=COMPRESSED");
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "timestamp` (`time` BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
                        
                    } else {
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "players` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` VARCHAR(16) NOT NULL)");
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "chunks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `x` INT NOT NULL, `z` INT NOT NULL)");
                        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + prefix + "timestamp` (`time` BIGINT NOT NULL)");
                    }
                    stmt.executeUpdate("INSERT INTO `" + prefix + "timestamp` (`time`) VALUES(" + BASE_TIME + ")");
//                    stmt.executeBatch();
                }
            }

            try (Statement stmt = connection.createStatement()) {
                try (ResultSet r = stmt.executeQuery(GET_CHUNKS)) {
                    while (r.next()) {
                        int id = r.getInt(1);
                        int x = r.getInt(2);
                        int z = r.getInt(3);
                        long pair = MathMan.pairInt(x, z);
                        CHUNK_LOC_ID.put(pair, id);
                        CHUNK_ID_LOC.put(id, pair);
                    }
                }
                
                try (ResultSet r = stmt.executeQuery(GET_PLAYERS)) {
                    while (r.next()) {
                        short id = (short) (r.getShort(1) + Short.MIN_VALUE);
                        String name = r.getString(2);
                        PLAYER_ID_VARCHAR.put(id, name);
                        PLAYER_VARCHAR_ID.put(name, id);
                    }
                }
            }
            TaskManager.IMP.async(new Runnable() {
                @Override
                public void run() {
                    long last = System.currentTimeMillis();
                    boolean purged = false;
                    while (true) {
                        if (connection == null) {
                            break;
                        }
                        if ((SQLDatabase.this instanceof MySQL) && ((System.currentTimeMillis() - last) > 550000)) {
                            last = System.currentTimeMillis();
                            try {
                                closeConnection();
                                connection = SQLDatabase.this.forceConnection();
                            } catch (SQLException | ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                        if (!purged) {
                            purge((int) TimeUnit.DAYS.toMillis(Config.PURGE_DAYS));
                            purged = true;
                        }
                        if (!SQLDatabase.this.sendBatch()) {
                            try {
                                if ((notify != null) && (notify.size() > 0)) {
                                    for (final Runnable runnable : notify) {
                                        runnable.run();
                                    }
                                    notify.clear();
                                }
                                Thread.sleep(50);
                            } catch (final InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean sendBatch() {
        try {
            runTasks();
            commit();
            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
            }
            int size = Math.min(1048572, blockChanges.size());
            
            if (size == 0) {
                return false;
            }

            BlockChange[] copy = new BlockChange[size];
            for (int i = 0; i < size; i++) {
                copy[i] = blockChanges.poll();
            }
            
            PreparedStatement playerStmt = null;
            for (BlockChange change : copy) {
                if (!change.hasPlayerId()) {
                    if (playerStmt == null) {
                        playerStmt = connection.prepareStatement(INSERT_PLAYER, Statement.RETURN_GENERATED_KEYS);
                    }
                    change.createPlayerId(playerStmt);
                }
            }
            if (playerStmt != null) {
                playerStmt.close();
            }

            int minTable = Integer.MAX_VALUE;
            int maxTable = 0;
            PreparedStatement chunkStmt = null;
            for (BlockChange change : copy) {
                if (!change.hasChunkId()) {
                    if (chunkStmt == null) {
                        chunkStmt = connection.prepareStatement(INSERT_CHUNK, Statement.RETURN_GENERATED_KEYS);
                    }
                    change.createChunkId(chunkStmt);
                }
                int table = change.getTableId();
                if (table < minTable) {
                    minTable = table;
                }
                if (table > maxTable) {
                    maxTable = table;
                }
            }
            if (chunkStmt != null) {
                chunkStmt.close();
            }
            
            PreparedStatement[] blockftStmts = new PreparedStatement[maxTable - minTable + 1];
            PreparedStatement[] blockfStmts = new PreparedStatement[maxTable - minTable + 1];
            PreparedStatement[] blocktStmts = new PreparedStatement[maxTable - minTable + 1];
            PreparedStatement[] blocknbtStmts = new PreparedStatement[maxTable - minTable + 1];

            for (BlockChange change : copy) {
                int table = change.getChunkId() >> TABLE_PARTITION;
                int stmtIndex = table - minTable;
                PreparedStatement blockStmt;
                if (change.hasNBT()) {
                    blockStmt = blocknbtStmts[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockfStmts[stmtIndex] = connection.prepareStatement(INSERT_BLOCKNBT.replace("$", table + ""));
                    }
                    blockStmt.setInt(4, change.getChangePair());
                    byte[] nbtf = change.getNbtFrom();
                    byte[] nbtt = change.getNbtTo();
                    blockStmt.setBytes(6, nbtf);
                    blockStmt.setBytes(7, nbtt);
                } else if (change.getTo() == 0) {
                    blockStmt = blockfStmts[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockfStmts[stmtIndex] = connection.prepareStatement(INSERT_BLOCKF.replace("$", table + ""));
                    }
                    blockStmt.setShort(4, change.getFrom());
                } else if (change.getFrom() == 0) {
                    blockStmt = blocktStmts[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blocktStmts[stmtIndex] = connection.prepareStatement(INSERT_BLOCKT.replace("$", table + ""));
                    }
                    blockStmt.setShort(4, change.getTo());
                } else {
                    blockStmt = blockftStmts[stmtIndex];
                    if (blockStmt == null) {
                        blockStmt = blockftStmts[stmtIndex] = connection.prepareStatement(INSERT_BLOCKFT.replace("$", table + ""));
                    }
                    blockStmt.setInt(4, change.getChangePair());
                }
                blockStmt.setShort(1, change.getPlayerId());
                blockStmt.setByte(2, change.getReducedChunkId());
                blockStmt.setShort(3, change.getPos());
                blockStmt.setInt(5, change.getTime());
                blockStmt.addBatch();
            }
            for (PreparedStatement blockStmt : blocknbtStmts) {
                if (blockStmt != null) {
                    blockStmt.executeBatch();
                    blockStmt.close();
                }
            }
            for (PreparedStatement blockStmt : blockftStmts) {
                if (blockStmt != null) {
                    blockStmt.executeBatch();
                    blockStmt.close();
                }
            }
            for (PreparedStatement blockStmt : blockfStmts) {
                if (blockStmt != null) {
                    blockStmt.executeBatch();
                    blockStmt.close();
                }
            }
            for (PreparedStatement blockStmt : blocktStmts) {
                if (blockStmt != null) {
                    blockStmt.executeBatch();
                    blockStmt.close();
                }
            }
            commit();
            return true;
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public void commit() {
        try {
            if (connection == null) {
                return;
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        } catch (final SQLException e) {
            e.printStackTrace();
        }
    }
    
    public int getBlocks(int originX, int originZ, int radius, String name, long timeDiff, RunnableVal<SimpleBlockChange> result) {
        Short playerId = PLAYER_VARCHAR_ID.get(name);
        if (playerId == null) {
            return 0;
        }
        int timeMinRel = (int) (((System.currentTimeMillis() - timeDiff) >> TIME_PARTITION) - BASE_TIME);
        int originChunkX = originX >> 4;
        int originChunkZ = originZ >> 4;
        int chunkRadius = (radius + 15) >> 4;
        int chunkRadSqr = chunkRadius * chunkRadius;
        HashSet<Integer> tables = new HashSet<>();
        for (Entry<Long, Integer> entry : CHUNK_LOC_ID.entrySet()) {
            long coord = entry.getKey();
            int cx = MathMan.unpairIntX(coord);
            int cz = MathMan.unpairIntY(coord);
            int dx = cx - originChunkX;
            int dz = cz - originChunkZ;
            int chunkDistSqr = dx * dx + dz * dz;
            if (chunkDistSqr <= chunkRadSqr) {
                int table = entry.getValue() >> TABLE_PARTITION;
                tables.add(table);
            }
        }
        if (tables.isEmpty()) {
            return 0;
        }
        int radiusSqr = radius * radius;
        int count = 0;
        for (int table : tables) {
            try {
                HashMap<SimpleBlockChange, Long> changes = new HashMap<>();
                try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKFT_VARCHAR_TIME.replace("$", table + ""))) {
                    stmt.setShort(1, playerId);
                    stmt.setInt(2, timeMinRel);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) {
                            short pos = r.getShort(2);
                            long chunkCoord = CHUNK_ID_LOC.get((table << TABLE_PARTITION) + (r.getByte(1) & 255));
                            byte xz = (byte) (pos & 255);
                            int x = (MathMan.unpairIntX(chunkCoord) << 4) + MathMan.unpair16x(xz);
                            int z = (MathMan.unpairIntY(chunkCoord) << 4) + MathMan.unpair16y(xz);
                            int dx = x - originX;
                            int dz = z - originZ;
                            if (dx * dx + dz * dz > radiusSqr) {
                                continue;
                            }
                            int fromTo = r.getInt(3);
                            SimpleBlockChange change = new SimpleBlockChange(x, (pos >> 8) & 255, z, MathMan.unpairX(fromTo), MathMan.unpairY(fromTo), null, null, name, r.getInt(4));
                            Long existing = changes.get(change);
                            if (existing != null) {
                                if (existing < change.timestamp) {
                                    continue;
                                }
                                changes.remove(change);
                            }
                            changes.put(change, change.timestamp);
                        }
                    }
                }
                try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKNBT_VARCHAR_TIME.replace("$", table + ""))) {
                    stmt.setShort(1, playerId);
                    stmt.setInt(2, timeMinRel);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) {
                            short pos = r.getShort(2);
                            long chunkCoord = CHUNK_ID_LOC.get((table << TABLE_PARTITION) + (r.getByte(1) & 255));
                            byte xz = (byte) (pos & 255);
                            int x = (MathMan.unpairIntX(chunkCoord) << 4) + MathMan.unpair16x(xz);
                            int z = (MathMan.unpairIntY(chunkCoord) << 4) + MathMan.unpair16y(xz);
                            int dx = x - originX;
                            int dz = z - originZ;
                            if (dx * dx + dz * dz > radiusSqr) {
                                continue;
                            }
                            int fromTo = r.getInt(3);
                            SimpleBlockChange change = new SimpleBlockChange(x, (pos >> 8) & 255, z, MathMan.unpairX(fromTo), MathMan.unpairY(fromTo), toTag(r.getBytes(5)), toTag(r.getBytes(6)),
                            name, r.getInt(4));
                            Long existing = changes.get(change);
                            if (existing != null) {
                                if (existing < change.timestamp) {
                                    continue;
                                }
                                changes.remove(change);
                            }
                            changes.put(change, change.timestamp);
                        }
                    }
                }
                try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKF_VARCHAR_TIME.replace("$", table + ""))) {
                    stmt.setShort(1, playerId);
                    stmt.setInt(2, timeMinRel);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) {
                            short pos = r.getShort(2);
                            long chunkCoord = CHUNK_ID_LOC.get((table << TABLE_PARTITION) + (r.getByte(1) & 255));
                            byte xz = (byte) (pos & 255);
                            int x = (MathMan.unpairIntX(chunkCoord) << 4) + MathMan.unpair16x(xz);
                            int z = (MathMan.unpairIntY(chunkCoord) << 4) + MathMan.unpair16y(xz);
                            int dx = x - originX;
                            int dz = z - originZ;
                            if (dx * dx + dz * dz > radiusSqr) {
                                continue;
                            }
                            SimpleBlockChange change = new SimpleBlockChange(x, (pos >> 8) & 255, z, r.getInt(3), 0, null, null, name, r.getInt(4));
                            Long existing = changes.get(change);
                            if (existing != null) {
                                if (existing < change.timestamp) {
                                    continue;
                                }
                                changes.remove(change);
                            }
                            changes.put(change, change.timestamp);
                        }
                    }
                }
                try (PreparedStatement stmt = connection.prepareStatement(GET_BLOCKT_VARCHAR_TIME.replace("$", table + ""))) {
                    stmt.setShort(1, playerId);
                    stmt.setInt(2, timeMinRel);
                    try (ResultSet r = stmt.executeQuery()) {
                        while (r.next()) {
                            short pos = r.getShort(2);
                            long chunkCoord = CHUNK_ID_LOC.get((table << TABLE_PARTITION) + (r.getByte(1) & 255));
                            byte xz = (byte) (pos & 255);
                            int x = (MathMan.unpairIntX(chunkCoord) << 4) + MathMan.unpair16x(xz);
                            int z = (MathMan.unpairIntY(chunkCoord) << 4) + MathMan.unpair16y(xz);
                            int dx = x - originX;
                            int dz = z - originZ;
                            if (dx * dx + dz * dz > radiusSqr) {
                                continue;
                            }
                            SimpleBlockChange change = new SimpleBlockChange(x, (pos >> 8) & 255, z, 0, r.getInt(3), null, null, name, r.getInt(4));
                            Long existing = changes.get(change);
                            if (existing != null) {
                                if (existing < change.timestamp) {
                                    continue;
                                }
                                changes.remove(change);
                            }
                            changes.put(change, change.timestamp);
                        }
                    }
                }
                for (Entry<SimpleBlockChange, Long> entry : changes.entrySet()) {
                    result.run(entry.getKey());
                }
                count += changes.size();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return count;
    }

    public abstract Connection forceConnection() throws SQLException, ClassNotFoundException;
    
    /**
     * Opens a connection with the database
     *
     * @return Opened connection
     * @throws java.sql.SQLException  if the connection can not be opened
     * @throws ClassNotFoundException if the driver cannot be found
     */
    public abstract Connection openConnection() throws SQLException, ClassNotFoundException;
    
    /**
     * Gets the connection with the database
     *
     * @return Connection with the database, null if none
     */
    public Connection getConnection() {
        if (connection == null) {
            try {
                forceConnection();
            } catch (final ClassNotFoundException e) {
                e.printStackTrace();
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }
    
    /**
     * Closes the connection with the database
     *
     * @return true if successful
     * @throws java.sql.SQLException if the connection cannot be closed
     */
    public boolean closeConnection() throws SQLException {
        if (connection == null) {
            return false;
        }
        connection.close();
        connection = null;
        return true;
    }
    
    /**
     * Checks if a connection is open with the database
     *
     * @return true if the connection is open
     * @throws java.sql.SQLException if the connection cannot be checked
     */
    public boolean checkConnection() {
        try {
            return (connection != null) && !connection.isClosed();
        } catch (final SQLException e) {
            return false;
        }
    }
}
