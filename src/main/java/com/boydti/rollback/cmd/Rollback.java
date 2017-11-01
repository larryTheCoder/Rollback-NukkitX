package com.boydti.rollback.cmd;

import cn.nukkit.Player;
import cn.nukkit.level.Location;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.changeset.FaweChangeSet;
import com.boydti.fawe.object.exception.FaweException;
import com.boydti.fawe.regions.FaweMaskManager;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.WEManager;
import com.boydti.rollback.LogAPI;
import com.boydti.rollback.config.Config;
import com.boydti.rollback.config.Loggers;
import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.database.SimpleBlockChange;
import com.boydti.rollback.util.LogUser;
import com.boydti.rollback.we.RollbackChangeSet;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Rollback {
    public Rollback(final Player player, String[] args) {
        //rollback <user> <radius> <time> 
        final FawePlayer<Object> fp = FawePlayer.wrap(player);
        if (!player.hasPermission("rollback.perform")) {
            player.sendMessage(Config.PREFIX + BBC.NO_PERM.format("rollback.perform"));
            return;
        }
        if (args.length != 3) {
            player.sendMessage(Config.PREFIX + BBC.COMMAND_SYNTAX.format("/rollback <user> <radius> <time>"));
            return;
        }
        final String name = LogUser.getName(args[0]);
        if (name == null) {
            player.sendMessage(Config.PREFIX + BBC.NOT_PLAYER.format(args[0]));
            return;
        }
        if (!MathMan.isInteger(args[1])) {
            player.sendMessage(Config.PREFIX + BBC.COMMAND_SYNTAX.format("/rollback " + args[0] + " <radius> <time>"));
            return;
        }
        final int radius = Integer.parseInt(args[1]);
        if (radius > 500 || radius <= 0) {
            fp.sendMessage(BBC.color(Config.PREFIX + "&cRadius is limited between [1, 500]"));
            return;
        }
        
        final long time = MainUtil.timeToSec(args[2]) * 1000;
        if (time == 0) {
            player.sendMessage(BBC.color(Config.PREFIX + BBC.COMMAND_SYNTAX.format("/rollback " + args[0] + " " + args[1] + " <time>")));
            return;
        }
        final RegionWrapper[] regions = WEManager.IMP.getMask(fp, FaweMaskManager.MaskType.OWNER);
        // Rollback
        Location loc = player.getLocation();
        final int x = loc.getFloorX();
        int y = loc.getFloorY();
        final int z = loc.getFloorZ();
        
        if (fp.getMeta("fawe_action") != null) {
            fp.sendMessage(BBC.color(Config.PREFIX + BBC.WORLDEDIT_COMMAND_LIMIT.s()));
            return;
        }
        fp.setMeta("fawe_action", true);

        SQLDatabase db = (SQLDatabase) LogAPI.getLogger(player.getLevel());
        db.addTask(new Runnable() {
            @Override
            public void run() {
                final EditSession session = new EditSessionBuilder(fp.getWorld()).player(fp)
                .checkMemory(false)
                .fastmode(false)
                .limitUnlimited()
                .allowedRegions(regions)
                .build();
                if (Loggers.WORLDEDIT.use()) {
                    ChangeSet cs = session.getChangeSet();
                    session.getQueue().setChangeTask(null);
                    if (cs instanceof RollbackChangeSet) {
                        session.setChangeSet(((RollbackChangeSet) cs).getParent());
                    } else {
                        session.setChangeSet((FaweChangeSet) cs);
                    }
                }
                long start = System.currentTimeMillis();
                final Vector mutable = new Vector(0, 0, 0);
                try {
                    if ((com.boydti.rollback.Rollback.db().getDatabase(player.getLevel().getName()).getBlocks(x, z, radius, name, time, new RunnableVal<SimpleBlockChange>() {
                        @Override
                        public void run(SimpleBlockChange change) {
                            BaseBlock block = FaweCache.CACHE_BLOCK[change.combinedFrom];
                            mutable.x = change.x;
                            mutable.y = change.y;
                            mutable.z = change.z;
                            if (change.tileFrom != null) {
                                block = new BaseBlock(block.getId(), block.getData(), change.tileFrom);
                            }
                            session.setBlockFast(mutable, block);
                        }
                    })) == 0) {
                        fp.sendMessage(BBC.color(Config.PREFIX + "&cNo changes found."));
                        fp.deleteMeta("fawe_action");
                        return;
                    }
                } catch (FaweException e) {
                    BBC.WORLDEDIT_CANCEL_REASON.send(fp, e.getMessage());
                }
                session.flushQueue();
                fp.getSession().remember(session);
                player.sendMessage(BBC.getPrefix() + BBC.ACTION_COMPLETE.format((System.currentTimeMillis() - start) / 1000));
                fp.deleteMeta("fawe_action");
            }
        });
    }

    public static <T, U, V> int remove(Map<T, Map<U, V>> map, T key, U value) {
        Map<U, V> curMap = map.get(key);
        while(true) {
            if((curMap == null) || !curMap.containsKey(value))  {
                return -1;
            }
            if(curMap.size() <= 1) {
                if(!map.remove(key, curMap)) {
                    curMap = map.get(key);
                    continue;
                }
                return 0;
            } else {
                Map<U, V> newMap = new ConcurrentHashMap<>(curMap);
                newMap.remove(value);
                if(!map.replace(key, curMap, newMap)) {
                    curMap = map.get(key);
                    continue;
                }
                return newMap.size();
            }
        }
    }

    public static <T, U, V extends U> U getOrCreate(Map<T, U> map, Class<V> clazz, T key) {
        U existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            U toPut = clazz.newInstance();
            existing = map.putIfAbsent(key, toPut);
            if (existing == null) {
                return toPut;
            }
            return existing;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
