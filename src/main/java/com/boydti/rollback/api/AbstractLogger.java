package com.boydti.rollback.api;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import com.boydti.fawe.FaweAPI;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.object.FaweQueue;
import com.boydti.fawe.util.TaskManager;
import com.boydti.rollback.config.Loggers;
import com.boydti.rollback.object.PhysicsTracker;
import com.sk89q.jnbt.CompoundTag;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractLogger {

    private final String world;
    private final FaweQueue queue;
    private final PhysicsTracker tracker;

    public AbstractLogger(String world) {
        this.world = world;
        this.queue = FaweAPI.createQueue(world, false);
        this.tracker = new PhysicsTracker(this);
        TaskManager.IMP.repeat(new Runnable() {
            @Override
            public void run() {
                tracker.clear();
            }
        }, 1);
    }

    public String getName(Block block) {
        return tracker.getName(block.getFloorX(), block.getFloorY(), block.getFloorZ());
    }

    public String getNameRelative(Block block) {
        return tracker.getNameRelative(block.getFloorX(), block.getFloorY(), block.getFloorZ());
    }

    public boolean logPhysics(Block block) {
        return tracker.logChange(block);
    }

    public void trackLiquid(String name, Block block) {
        int x = block.getFloorX();
        int y = block.getFloorY();
        int z = block.getFloorZ();
        tracker.store(name, x, y, z, queue.getCombinedId4Data(x, y, z));
    }

    public String getWorldName() {
        return world;
    }

    public FaweQueue getQueue() {
        return queue;
    }

    public void logTo(String name, int x, int y, int z, short combinedTo, CompoundTag nbtTo) {
        short combinedFrom = (short) queue.getCombinedId4Data(x, y, z, combinedTo);
        CompoundTag nbtFrom;
        if (Loggers.TILES.use() && FaweCache.hasNBT(combinedFrom >> 4)) {
            nbtFrom = queue.getTileEntity(x, y, z);
        } else {
            nbtFrom = null;
        }
        logBlock(name, x, y, z, combinedFrom, combinedTo, nbtFrom, nbtTo);
    }

    public void logPlayerPlace(Player player, Block block) {
        logPlace(player.getName(), block);
    }

    public void logPlace(String name, Block block) {
        int x = block.getFloorX();
        int y = block.getFloorY();
        int z = block.getFloorZ();
        int to = queue.getCombinedId4Data(x, y, z);
        logTo(name, block.getFloorX(), block.getFloorY(), block.getFloorZ(), (short) to, null);
    }

    public void logPlace(String name, int x, int y, int z, short combinedTo, CompoundTag nbtTo) {
        short combinedFrom = (short) queue.getCombinedId4Data(x, y, z);
        CompoundTag nbtFrom;
        if (FaweCache.hasNBT(FaweCache.getId(combinedFrom))) {
            nbtFrom = queue.getTileEntity(x, y, z);
        } else {
            nbtFrom = null;
        }
        logBlock(name, x, y, z, combinedFrom, combinedTo, nbtFrom, nbtTo);
        tracker.store(name, x, y, z, combinedTo);
    }

    public void logPlayerBreak(Player player, Block block, boolean track) {
        logBreak(player.getName(), block, player.getGamemode() == 1, track);
    }

    public void logBreak(String name, Block block, boolean creative, boolean track) {
        logBreak(name, block.getFloorX(), block.getFloorY(), block.getFloorZ(), creative, track);
    }

    public void logBreak(String name, int x, int y, int z, boolean creative, boolean track) {
        short combinedFrom = (short) queue.getCombinedId4Data(x, y, z, 0);
        CompoundTag nbtFrom;
        if (Loggers.TILES.use() && FaweCache.hasNBT(combinedFrom >> 4)) {
            nbtFrom = queue.getTileEntity(x, y, z);
        } else {
            if (!creative && combinedFrom >> 4 == 79) { // ICE
                logBlock(name, x, y, z, combinedFrom, (short) (9 << 4), null, null);
                return;
            }
            nbtFrom = null;
        }
        logBlock(name, x, y, z, combinedFrom, (short) 0, nbtFrom, null);
        if (track) {
            tracker.storeRelative(name, x, y, z);
        }

    }

    public void logFrom(String name, int x, int y, int z, short combinedFrom, CompoundTag nbtFrom) {
        short combinedTo = (short) queue.getCombinedId4Data(x, y, z, combinedFrom);
        CompoundTag nbtTo;
        if (Loggers.TILES.use() && FaweCache.hasNBT(combinedTo >> 4)) {
            nbtTo = queue.getTileEntity(x, y, z);
        } else {
            nbtTo = null;
        }
        logBlock(name, x, y, z, combinedFrom, combinedTo, nbtFrom, nbtTo);
    }

    public void logBlock(String name, int x, int y, int z, short from, short to) {
        logBlock(name, x, y, z, from, to, null, null);
    }

    public abstract void logBlock(String name, int x, int y, int z, short combinedFrom, short combinedTo, CompoundTag nbtFrom, CompoundTag nbtTo);

    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public void addTask(Runnable run) {
        this.tasks.add(run);
    }

    public void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
