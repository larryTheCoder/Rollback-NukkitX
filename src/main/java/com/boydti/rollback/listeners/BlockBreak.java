package com.boydti.rollback.listeners;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.player.PlayerBucketFillEvent;
import cn.nukkit.plugin.Plugin;
import com.boydti.rollback.LogAPI;
import com.boydti.rollback.config.Loggers;

public class BlockBreak extends BasicListener {
    
    public BlockBreak(Plugin parent) {
        super(Loggers.BLOCK_BREAK, parent);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        LogAPI.getLogger(player.getLevel()).logPlayerBreak(player, event.getBlock(), true);
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        LogAPI.getLogger(player.getLevel()).logPlayerBreak(player, event.getBlockClicked(), true);
    }
}
