package com.boydti.rollback.listeners;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.entity.EntityBlockChangeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.plugin.Plugin;
import com.boydti.fawe.FaweCache;
import com.boydti.rollback.Rollback;
import com.boydti.rollback.LogAPI;
import com.boydti.rollback.api.AbstractLogger;
import com.boydti.rollback.config.Loggers;
import com.boydti.rollback.util.LogUser;
import java.util.List;

public class BlockFallListener extends BasicListener {
    
    public BlockFallListener(Plugin parent) {
        super(Loggers.FALLING_BLOCK, parent);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFall(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityFallingBlock) {
            AbstractLogger logger = LogAPI.getLogger(entity.getLevel());
            EntityFallingBlock falling = (EntityFallingBlock) entity;
            int id = falling.getBlock();
            int data = falling.getDamage();
            Location loc = entity.getLocation();
            Level level = loc.getLevel();
            Block block = level.getBlock(loc);
            String name = logger.getNameRelative(block);
            if (name == null) {
                AxisAlignedBB bb = new AxisAlignedBB(loc.getX() - 1, loc.getY() - 1, loc.getZ() - 1, loc.getX() + 1, loc.getY() + 1, loc.getZ() + 1);
                Entity[] nearby = level.getNearbyEntities(bb, entity);
                name = LogUser.FALLING_BLOCK.ID;
                for (Entity ent : nearby) {
                    if (ent instanceof EntityFallingBlock) {
                        List<MetadataValue> meta = ent.getMetadata("LogUser");
                        if (!meta.isEmpty()) {
                            name = (String) meta.get(0).value();
                            break;
                        }
                    }
                }
            }
            logger.logBlock(name, block.getFloorX(), block.getFloorY(), block.getFloorZ(), (short) FaweCache.getCombined(id, data), (short) 0);
            final String finalName = name;
            entity.setMetadata("LogUser", new MetadataValue(Rollback.get()) {
                @Override
                public Object value() {
                    return finalName;
                }

                @Override
                public void invalidate() {}
            });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockLand(EntityBlockChangeEvent event) {
        Entity entity = event.getEntity();
        List<MetadataValue> meta = entity.getMetadata("LogUser");
        String name;
        if (meta.isEmpty()) {
            name = LogUser.FALLING_BLOCK.ID;
        } else {
            name = (String) meta.get(0).value();
        }
        Block to = event.getTo();
        Block from = event.getFrom();
        short combinedFrom = (short) FaweCache.getCombined(from.getId(), from.getDamage());
        short combinedTo = (short) FaweCache.getCombined(to.getId(), to.getDamage());
        int x = from.getFloorX();
        int y = from.getFloorY();
        int z = from.getFloorZ();
        LogAPI.getLogger(entity.getLevel()).logBlock(name, x, y, z, combinedFrom, combinedTo);
    }


}
