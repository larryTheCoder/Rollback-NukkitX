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

package com.boydti.rollback;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFallingBlock;
import cn.nukkit.entity.item.EntityPrimedTNT;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.block.DoorToggleEvent;
import cn.nukkit.event.entity.EntityBlockChangeEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.event.entity.EntitySpawnEvent;
import cn.nukkit.event.level.LevelLoadEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.utils.TextFormat;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.util.MainUtil;
import com.boydti.rollback.api.AbstractLogger;
import com.boydti.rollback.block.BlockChange;
import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.event.RollbackActionEvent;
import com.boydti.rollback.object.Session;
import com.boydti.rollback.util.Explosion;
import com.boydti.rollback.util.Loggers;
import com.boydti.rollback.util.Utils;
import com.boydti.rollback.we.RollbackChangeSet;
import com.sk89q.worldedit.blocks.BaseBlock;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The main class to listen to all the event
 * in the server and save them to database
 */
public class CoreEvent implements Listener {

    private static final Map<Player, Integer> playerList = new ConcurrentHashMap<>();
    static Item wandItem;
    private final Rollback plugin;

    CoreEvent(Rollback pl, String[] idMeta) {
        plugin = pl;
        Item item = Item.get(Integer.parseInt(idMeta[0]), Integer.parseInt(idMeta[1]));
        item.setCustomName(TextFormat.colorize("&eInspector tool"));
        wandItem = item;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Adds a player that need to be logged into their
     * chat channel, inspection module
     *
     * @param player The player class itself
     * @param range  The range of the block to be inspected
     */
    static void addPlayerLog(Player player, int range) {
        playerList.put(player, range);
    }

    /**
     * Remove the player from the inspection checkup.
     *
     * @param player The player class itself
     */
    static void removePlayerLog(Player player) {
        playerList.remove(player);
    }

    static boolean isPlayerLogRegistered(Player player) {
        return playerList.containsKey(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        Item hand = event.getItem();
        if (hand == null || !hand.equals(wandItem) || !player.hasPermission("rollback.inspect")) {
            return;
        }
        Vector3 block;
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK:
                block = event.getBlock().getSide(event.getFace());
                break;
            case LEFT_CLICK_BLOCK:
                block = event.getBlock();
                break;
            default:
                return;
        }
        if (player.isSneaking()) {
            block = block.getSide(player.getHorizontalFacing().getOpposite());
        }
        event.setCancelled(true);
        interact(player, block, Utils.getVectorPair(event.getBlock()));
    }

    private void interact(Player player, Vector3 block, short posID) {
        Session session = plugin.getSession(player.getName());
        final SQLDatabase database = plugin.getDatabase().getDatabase(player.getLevel().getName());
        session.startSession();
        database.addTask(() -> {
            try {
                List<com.boydti.rollback.block.Block> changes = database.getChanges(block.getChunkX(), block.getChunkZ(), posID);
                session.clearSearchValues();
                if (changes.isEmpty()) {
                    session.getFawePlayer().sendMessage(Rollback.get().getPrefix() + "No block data found for this location.");
                    session.stopSession(null);
                    return;
                }
                changes.sort(Comparator.comparingInt(com.boydti.rollback.block.Block::getTimeChanged).reversed());
                session.storeSearchValues(changes);

                int i = 0;
                for (com.boydti.rollback.block.Block change : changes) {
                    if (i <= CoreCommand.MESSAGES_LIMIT) {
                        long timeChanged = (System.currentTimeMillis() - change.getChangedTimestamp()) / 1000; // (Now - Past) / 1000 (Milliseconds)

                        String timeNow = MainUtil.secToTime(timeChanged);
                        String playerUser = change.getPlayerName();

                        BaseBlock blockFrom = FaweCache.CACHE_BLOCK[change.getBlockFrom()];
                        BaseBlock blockTo = FaweCache.CACHE_BLOCK[change.getBlockTo()];

                        String from = Item.get(blockFrom.getId(), blockFrom.getData()).getName();
                        String to = Item.get(blockTo.getId(), blockTo.getData()).getName();

                        if (blockTo.getId() == 0) {
                            player.sendMessage("§e" + "" + playerUser + "§f removed §e#" + blockFrom.getId() + " (" + from + ") §7" + timeNow);
                        } else {
                            player.sendMessage("§e" + "" + playerUser + "§f placed §e#" + blockTo.getId() + " (" + to + ") §7" + timeNow);
                        }
                        player.sendMessage("§7^ §o(x" + change.getFloorX() + "/y" + change.getFloorY() + "/z" + change.getFloorZ() + "/" + change.getLevel().getName() + ")");
                    }
                    i++;
                }

                if (i >= CoreCommand.MESSAGES_LIMIT) {
                    player.sendMessage(TextFormat.colorize("&eUse /cp search p:<page> to see next page"));
                }
                session.stopSession(null);
            } catch (Exception e) {
                session.stopSession(null);
                Utils.logError("An unexpected error just occurred.", e);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void playerJoinEvent(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("rollback.perform")) {
            Session session = new Session(p);

            plugin.setSession(p.getName(), session);
            Utils.send("&7Created " + p.getName() + "'s Session");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerQuitEvent(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        Session session = plugin.getSession(p.getName());

        // Close the session after leave.
        if (session != null) {
            session.closeSession();
            plugin.setSession(p.getName(), null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLevelInit(LevelLoadEvent e) {
        plugin.getDatabase().getDatabase(e.getLevel().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void rollbackActionEvent(RollbackActionEvent event) {
        BlockChange change = event.getChanged();

        for (Map.Entry<Player, Integer> entry : playerList.entrySet()) {
            Player p = entry.getKey();
            if (!p.getLevel().getName().equalsIgnoreCase(change.getLevel().getName())) {
                p.sendMessage(TextFormat.colorize(Rollback.get().getPrefix() + "&cYou just leave the world, now disabling logging system."));
                removePlayerLog(p);
                continue;
            }

            // Only send the specific area of the block
            if (p.distance(change) <= entry.getValue()) {
                String playerName = change.getPlayerName();

                BaseBlock blockFrom = FaweCache.CACHE_BLOCK[change.getBlockFrom()];
                BaseBlock blockTo = FaweCache.CACHE_BLOCK[change.getBlockTo()];

                String from = Item.get(blockFrom.getId(), blockFrom.getData()).getName();
                String to = Item.get(blockTo.getId(), blockTo.getData()).getName();

                if (blockTo.getId() == 0) {
                    p.sendMessage("§e" + "" + playerName + "§f removed §e#" + blockFrom.getId() + ":" + blockFrom.getData() + " (" + from + ")");
                } else {
                    p.sendMessage("§e" + "" + playerName + "§f placed §e#" + blockTo.getId() + ":" + blockFrom.getData() + " (" + to + ")");
                }
                p.sendMessage("§7^ §o(x" + change.getFloorX() + "/y" + change.getFloorY() + "/z" + change.getFloorZ() + "/" + change.getLevel().getName() + ")");
            }
        }
    }

    /**
     * Tracks all the block breaks in the server.
     * Also its checks the wand for the player
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        Item hand = event.getItem();
        if (hand != null && hand.equals(wandItem) && player.hasPermission("rollback.inspect")) {
            Block block = event.getBlock();
            interact(player, block, Utils.getVectorPair(block));
            event.setCancelled(true);
            return;
        }

        if (!Loggers.BLOCK_BREAK.shouldBeEnabled()) {
            return;
        }

        plugin.getDatabase().getDatabase(player.getLevel().getName()).logPlayerBreak(player, event.getBlock(), true);
    }

    /**
     * Explode the specific area.
     * This uses a hack to make sure the blocks
     * got logged into database.
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockExplode(EntityExplosionPrimeEvent event) {
        if (!Loggers.BLOCK_EXPLOSION.shouldBeEnabled() || event.isCancelled()) {
            return;
        }
        event.setCancelled();

        Player player = null;
        Entity explodedEntity = event.getEntity();
        if (((EntityPrimedTNT) event.getEntity()).getSource() instanceof Player) {
            player = (Player) ((EntityPrimedTNT) event.getEntity()).getSource();
        }
        String playerName = player == null ? "minecraft:native" : player.getName();

        // A Hack
        Explosion explosion = new Explosion(explodedEntity, event.getForce(), explodedEntity);
        if (event.isBlockBreaking()) {
            explosion.explodeA();
        }
        explosion.explodeB();

        // Then stores the block explosions in the database
        // A little bit cocky but we could use them =,=
        List<Block> affected = explosion.getAffectedBlocks();
        Block blockTo = Block.get(0); // Air
        for (Block blockFrom : affected) {
            // Compare them
            short combinedFrom = (short) FaweCache.getCombined(blockFrom.getId(), blockFrom.getDamage());
            short combinedTo = (short) FaweCache.getCombined(blockTo.getId(), blockTo.getDamage());

            // Save them into db
            plugin.getDatabase().getDatabase(event.getEntity().getLevel().getName()).logBlock(playerName, blockFrom, combinedFrom, combinedTo);
        }
    }


    /**
     * Identify the suspect who triggers the
     * falling block.
     *
     * @param event The lord event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFall(EntitySpawnEvent event) {
        if (!Loggers.FALLING_BLOCK.shouldBeEnabled()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof EntityFallingBlock) {
            AbstractLogger logger = plugin.getDatabase().getDatabase(entity.getLevel().getName());
            EntityFallingBlock falling = (EntityFallingBlock) entity;
            int id = falling.getBlock();
            int data = falling.getDamage();
            Location loc = entity.getLocation();
            Level level = loc.getLevel();
            Block block = level.getBlock(loc);
            String name = logger.getNameRelative(block);
            if (name == null) {
                AxisAlignedBB bb = new SimpleAxisAlignedBB(loc.getX() - 1, loc.getY() - 1, loc.getZ() - 1, loc.getX() + 1, loc.getY() + 1, loc.getZ() + 1);
                Entity[] nearby = level.getNearbyEntities(bb, entity);
                name = "minecraft:native";
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
            logger.logBlock(name, block.getLocation(), (short) FaweCache.getCombined(id, data), (short) 0);
            final String finalName = name;
            entity.setMetadata("LogUser", new MetadataValue(Rollback.get()) {
                @Override
                public Object value() {
                    return finalName;
                }

                @Override
                public void invalidate() {
                }
            });
        }
    }


    /**
     * Logs the falling block by the user.
     * This event should be checked by onBlockFall
     * so the user could be identified
     *
     * @param event The lord event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockLand(EntityBlockChangeEvent event) {
        if (!Loggers.FALLING_BLOCK.shouldBeEnabled()) {
            return;
        }

        SQLDatabase db = plugin.getDatabase().getDatabase(event.getEntity().getLevel().getName());
        List<MetadataValue> meta = event.getEntity().getMetadata("LogUser");
        // Default name for native blocks.
        String name = "minecraft:native";
        if (!meta.isEmpty()) {
            name = (String) meta.get(0).value();
        }

        short combinedFrom = (short) FaweCache.getCombined(event.getFrom().getId(), event.getFrom().getDamage());
        short combinedTo = (short) FaweCache.getCombined(event.getTo().getId(), event.getTo().getDamage());

        db.logBlock(name, event.getFrom().getLocation(), combinedFrom, combinedTo);
    }

    /**
     * Tracks all the block places in the server.
     * Also its checks the wand for the player
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!Loggers.BLOCK_PLACE.shouldBeEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        AbstractLogger db = plugin.getDatabase().getDatabase(player.getLevel().getName());

        Block state = event.getBlock();
        int combinedTo = FaweCache.getCombined(state.getId(), state.getDamage());

        db.logPlace(player.getName(), state, (short) combinedTo, null);
    }

    /**
     * Logs the place event made by a bucket
     * This bucket is placing a water or a lava
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!Loggers.BLOCK_PLACE.shouldBeEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        AbstractLogger db = plugin.getDatabase().getDatabase(player.getLevel().getName());

        Block block = event.getBlockClicked();

        db.trackLiquid(player.getName(), block);
        db.logBlock(player.getName(), block, (short) 0, (short) FaweCache.getCombined(block.getId(), block.getDamage()));
    }

    /**
     * Checks if a bucket is trying to remove
     * the block, this is also a block break
     * event
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!Loggers.BLOCK_BREAK.shouldBeEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockClicked();
        SQLDatabase db = plugin.getDatabase().getDatabase(player.getLevel().getName());

        db.trackLiquid(player.getName(), block);
        db.logBlock(player.getName(), block, (short) 0, (short) FaweCache.getCombined(block.getId(), block.getDamage()));
    }

    /**
     * A physics event that could just calculate
     * and store the changes into database.
     *
     * @param event The lord event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockUpdateEvent event) {
        if (!Loggers.PHYSICS.shouldBeEnabled()) {
            return;
        }
        if (event instanceof RedstoneUpdateEvent || event instanceof DoorToggleEvent) {
            // Ignore these, as no blocks are removed/placed
            return;
        }

        Block blockFrom = event.getBlock().getLevel().getBlock(event.getBlock());
        Block blockTo = event.getBlock();
        SQLDatabase db = plugin.getDatabase().getDatabase(blockTo.getLevel().getName());
        switch (blockTo.getId()) {
            case Block.WATER: // Liquids
            case Block.STILL_WATER:
            case Block.STILL_LAVA:
            case Block.LAVA:
                String playerName = db.getTrackedLiquid(blockTo);
                // Get if the player is stored.
                if (playerName == null) {
                    return;
                }
                short combinedFrom = (short) FaweCache.getCombined(blockFrom.getId(), blockFrom.getDamage());
                short combinedTo = (short) FaweCache.getCombined(blockTo.getId(), blockTo.getDamage());

                db.trackLiquid(playerName, blockTo);
                db.logBlock(playerName, blockTo, combinedFrom, combinedTo);
                break;
            default:
                db.logPhysics(blockTo);
                break;
        }
    }
}
