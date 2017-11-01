package com.boydti.rollback.listeners;

import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.plugin.Plugin;
import com.boydti.rollback.LogAPI;
import com.boydti.rollback.api.AbstractLogger;
import com.boydti.rollback.config.Loggers;
import com.boydti.rollback.util.LogUser;

public class PhysicsEvent extends BasicListener {
    
    public PhysicsEvent(Plugin parent) {
        super(Loggers.PHYSICS, parent);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockUpdateEvent event) {
        if (event instanceof RedstoneUpdateEvent || event instanceof DoorToggleEvent) {
            // Ignore these, as no blocks are removed/placed
            return;
        }
        if (new Exception().getStackTrace().length > 48) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlock();
        switch (block.getId()) {
            case 8: // Liquids
            case 9:
            case 10:
            case 11:
                AbstractLogger logger = LogAPI.getLogger(block.getLevel());
                logger.trackLiquid(LogUser.LIQUID.ID, block);
                return;
            default:
                LogAPI.getLogger(block.getLevel()).logPhysics(block);
                return;
        }
    }
}
