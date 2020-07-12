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
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.exception.FaweException;
import com.boydti.fawe.util.MainUtil;
import com.boydti.rollback.block.Block;
import com.boydti.rollback.database.SQLDatabase;
import com.boydti.rollback.object.Session;
import com.boydti.rollback.util.Utils;
import com.google.common.collect.Maps;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

class CoreCommand extends Command {

    static final int MESSAGES_LIMIT = 5;

    private final Rollback plugin;

    CoreCommand(Rollback plugin) {
        super("rollback", "Default command constructor for rollback");
        this.plugin = plugin;

        registerCommandParams();
    }

    @Override
    public String[] getAliases() {
        return new String[]{"cp"};
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Check the player permission
        if (!sender.hasPermission("rollback.command")) {
            sender.sendMessage(getPrefix() + BBC.NO_PERM.format("rollback.command"));
            return true;
        }
        // Send the command helper.
        if (args.length == 0) {
            sender.sendMessage("§cUnknown command, use /" + label + " help for a list of command");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                sender.sendMessage("§f----- §eRollback Help §f-----");
                sender.sendMessage("§e/" + label + "§a rollback §d> §fRollback a certain area to its last state");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<radius>");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<radius> <time>");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<user>");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<user> <radius>");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<user> <radius> <time> ");
                sender.sendMessage("§7- §e/" + label + "§a rollback §7<user> <time> ");
                sender.sendMessage("§e/" + label + "§a log §7<radius> §d> §fLog all the actions when player interact with 'em.");
                sender.sendMessage("§e/" + label + "§a purge <time> §d> §fPurge some specific data in db by time.");
                sender.sendMessage("§e/" + label + "§a inspect §d> §fInspect the history of a block.");
                sender.sendMessage("§e/" + label + "§a status §d> §fGet the status of the plugin uptime.");
                break;
            case "restore":
            case "rollback":
                if (!sender.hasPermission("rollback.perform")) {
                    sender.sendMessage(getPrefix() + BBC.NO_PERM.format("rollback.perform"));
                    break;
                }

                if (args.length >= 5) {
                    sender.sendMessage(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " <user> <radius> <time>"));
                    break;
                }

                if (!sender.isPlayer()) {
                    sender.sendMessage(getPrefix() + BBC.NOT_PLAYER.format(args[1]));
                    break;
                }

                Player player = (Player) sender;
                Session playerSession = plugin.getSession(player.getName());

                SQLDatabase db = plugin.getDatabase().getDatabase(player.getLevel().getName());

                if (db.isBusy()) {
                    sender.sendMessage(getPrefix() + "§eDatabase is querying a lot of data. This may take time");
                }

                // This section is by this configuration
                if (args.length == 1) {
                    sender.sendMessage(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " <user> <radius> <time>"));
                    break;
                }

                String playerNameN = null;
                int radiusN = 250;
                long timeN = Integer.MAX_VALUE;
                if (Utils.isInteger(args[1])) {
                    // /cp restore <radius>
                    // /cp restore <radius> <time>
                    radiusN = Integer.parseInt(args[1]);
                    if (radiusN > 500 || radiusN <= 0) {
                        sender.sendMessage(TextFormat.colorize(getPrefix() + "&cRadius is limited between [1, 500]"));
                        break;
                    }
                    if (args.length == 3) {
                        timeN = Utils.timeToSec(args[2]) * 1000;
                    }
                } else {
                    playerNameN = args[1];
                    if (args.length == 4) {
                        // /cp restore <user> <radius> <time>
                        if (!Utils.isInteger(args[2])) {
                            sender.sendMessage(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " <user> <radius> <time>"));
                            break;
                        }
                        radiusN = Integer.parseInt(args[2]);

                        if (radiusN > 500 || radiusN <= 0) {
                            sender.sendMessage(TextFormat.colorize(getPrefix() + "&cRadius is limited between [1, 500]"));
                            break;
                        }
                        timeN = Utils.timeToSec(args[3]) * 1000;
                    } else if (args.length == 3) {
                        if (Utils.isInteger(args[2])) {
                            radiusN = Integer.parseInt(args[2]);
                        } else if (Utils.timeToSec(args[2]) != 0) {
                            timeN = Utils.timeToSec(args[2]) * 1000;
                        } else {
                            sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + args[1] + " <time/radius>")));
                            break;
                        }
                    }
                }

                if (timeN == 0) {
                    player.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + args[1] + " " + args[2] + " <time>")));
                    break;
                }

                String playerName = playerNameN;
                int radius = radiusN;
                long time = timeN;

                final int x = player.getLocation().getFloorX();
                final int z = player.getLocation().getFloorZ();

                if (!playerSession.startSession()) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.WORLDEDIT_COMMAND_LIMIT.s()));
                    break;
                }

                db.addTask(() -> {
                    FawePlayer<Object> fp = playerSession.getFawePlayer();
                    EditSession session = playerSession.startEditSession();

                    long start = System.currentTimeMillis();
                    try {
                        Map<Block, Integer> blocks = db.getBlocks(x, z, radius, playerName, time);
                        if (blocks.isEmpty()) {
                            fp.sendMessage(TextFormat.colorize(getPrefix() + "&cNo changes found."));
                            playerSession.stopSession(session);
                            return;
                        } else {
                            blocks.forEach((change, timestamp) -> {
                                Vector mutable = new Vector(0, 0, 0);
                                BaseBlock block = FaweCache.CACHE_BLOCK[change.getBlockFrom()];
                                mutable.mutX(change.x);
                                mutable.mutY(change.y);
                                mutable.mutZ(change.z);
                                if (change.getTileFrom() != null) {
                                    block.setNbtData(change.getTileFrom());
                                }
                                session.setBlockFast(mutable, block);
                            });
                        }
                    } catch (FaweException e) {
                        BBC.WORLDEDIT_CANCEL_REASON.send(fp, e.getMessage());
                    }

                    playerSession.stopSession(session);
                    player.sendMessage(BBC.getPrefix() + BBC.ACTION_COMPLETE.format((System.currentTimeMillis() - start) / 1000));
                });
                break;
            case "i":
            case "inspect":
                if (!sender.isPlayer()) {
                    sender.sendMessage(getPrefix() + BBC.NOT_PLAYER.format());
                    return true;
                }
                if (!sender.hasPermission("rollback.inspect")) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.NO_PERM.format("rollback.log")));
                    break;
                }
                player = (Player) sender;
                db = plugin.getDatabase().getDatabase(player.getLevel().getName());

                if (db.isBusy()) {
                    sender.sendMessage(getPrefix() + "§eDatabase is querying a lot of data. This may take time");
                }

                Item wandItem = com.boydti.rollback.CoreEvent.wandItem;
                // Send the wand into the player inventory
                PlayerInventory inv = player.getInventory();
                inv.remove(wandItem);
                Item item = inv.getItemInHand();
                inv.setItemInHand(wandItem);
                if (item != null) {
                    inv.addItem(item);
                }
                inv.sendContents(player);
                player.sendMessage(TextFormat.colorize(getPrefix() + "&eLeft click to inspect, right click to inspect the adjacent block."));
                break;
            case "purge":
                if (!sender.hasPermission("rollback.purge")) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.NO_PERM.format("rollback.status")));
                    break;
                }
                if (args.length <= 1) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " purge <time>")));
                    break;
                }

                timeN = Utils.timeToSec(args[1]) * 1000;
                if (timeN == 0) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " purge <time>")));
                    break;
                }
                sender.sendMessage(TextFormat.colorize(getPrefix() + "Purging all data before: &e" + MainUtil.secToTime(timeN / 1000)));
                for (Map.Entry<String, SQLDatabase> database : plugin.getDatabase().getDatabases().entrySet()) {
                    SQLDatabase sql = database.getValue();
                    if (sql.isBusy()) {
                        sender.sendMessage(getPrefix() + "Database §e" + sql.world + "§f is busy. Please try again later.");
                        continue;
                    }
                    sql.purge((int) timeN);
                }
                sender.sendMessage(TextFormat.colorize(getPrefix() + "&cAll data has been purged"));
                break;
            case "status":
                if (!sender.hasPermission("rollback.status")) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.NO_PERM.format("rollback.status")));
                    break;
                }

                sender.sendMessage(TextFormat.colorize("&6Rollback&f current status:"));
                for (Map.Entry<String, SQLDatabase> database : plugin.getDatabase().getDatabases().entrySet()) {
                    SQLDatabase sql = database.getValue();
                    sender.sendMessage("---");
                    sender.sendMessage(TextFormat.colorize("Database name: &e" + sql.world));
                    sender.sendMessage(TextFormat.colorize("Ping with server: &e" + sql.getPing()));
                    sender.sendMessage(TextFormat.colorize("Blocks to be handled: &e" + sql.getLastBatchSize()));
                    if (sql.isBusy()) {
                        sender.sendMessage(TextFormat.colorize("Status: &cBusy"));
                    } else {
                        sender.sendMessage(TextFormat.colorize("Status: &aReady"));
                    }
                }
                break;
            case "log":
                if (!sender.isPlayer()) {
                    sender.sendMessage(getPrefix() + BBC.NOT_PLAYER.format());
                    return true;
                }
                if (!sender.hasPermission("rollback.log")) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.NO_PERM.format("rollback.log")));
                    break;
                }
                player = (Player) sender;
                db = plugin.getDatabase().getDatabase(player.getLevel().getName());

                if (db.isBusy()) {
                    sender.sendMessage(getPrefix() + "§eDatabase is querying a lot of data. This may take time");
                }

                if (com.boydti.rollback.CoreEvent.isPlayerLogRegistered(player)) {
                    player.sendMessage(TextFormat.colorize(getPrefix() + "Logging system is now disabled."));
                    com.boydti.rollback.CoreEvent.removePlayerLog(player);
                    break;
                } else {
                    if (args.length >= 2) {
                        if (args[1].isEmpty() || !Utils.isInteger(args[1])) {
                            player.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " log <radius>")));
                            break;
                        }
                        radiusN = Integer.parseInt(args[1]);
                        player.sendMessage(TextFormat.colorize(getPrefix() + "Logging system is now enabled for radius: &e" + radiusN));
                        com.boydti.rollback.CoreEvent.addPlayerLog(player, radiusN);
                        break;
                    } else {
                        player.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " log <radius>")));
                    }
                }
                break;
            case "search":
                if (!sender.hasPermission("rollback.search")) {
                    sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.NO_PERM.format("rollback.search")));
                    break;
                }

                if (!sender.isPlayer()) {
                    sender.sendMessage(getPrefix() + BBC.NOT_PLAYER.format());
                    break;
                }

                player = (Player) sender;
                playerSession = plugin.getSession(player.getName());
                List<Block> changes = playerSession.getSearchValues();

                if ((args.length == 1 || args[1].startsWith("p:")) && !changes.isEmpty()) {
                    int pageNumber;
                    if (Utils.isInteger(args[1].substring(2))) {
                        pageNumber = Integer.parseInt(args[1].substring(2));
                    } else {
                        sender.sendMessage(getPrefix() + "That is not a valid number.");
                        break;
                    }

                    int totalPage = changes.size() % MESSAGES_LIMIT == 0 ? changes.size() / MESSAGES_LIMIT : changes.size() / MESSAGES_LIMIT + 1;
                    pageNumber = Math.min(pageNumber, totalPage);
                    if (pageNumber < 1) {
                        pageNumber = 1;
                    }

                    sender.sendMessage("§7--- §6Rollback Lookup Results Page §e" + pageNumber + " §eof §a" + totalPage + " §7---");

                    int i = 0;
                    for (Block change : changes) {
                        if (i >= (pageNumber - 1) * MESSAGES_LIMIT + 1 && i <= Math.min(changes.size(), pageNumber * MESSAGES_LIMIT)) {
                            long timeChanged = (System.currentTimeMillis() - change.getChangedTimestamp()) / 1000;

                            String timeNow = MainUtil.secToTime(timeChanged);
                            String playerUser = change.getPlayerName();

                            BaseBlock blockFrom = FaweCache.CACHE_BLOCK[change.getBlockFrom()];
                            BaseBlock blockTo = FaweCache.CACHE_BLOCK[change.getBlockTo()];

                            String from = Item.get(blockFrom.getId(), blockFrom.getData()).getName();
                            String to = Item.get(blockTo.getId(), blockTo.getData()).getName();

                            if (blockTo.getId() == 0) {
                                player.sendMessage("§e" + "" + playerUser + "§f removed §e#" + blockFrom.getId() + ":" + blockFrom.getData() + " (" + from + ") §7" + timeNow);
                            } else {
                                player.sendMessage("§e" + "" + playerUser + "§f placed §e#" + blockTo.getId() + ":" + blockTo.getData() + " (" + to + ") §7" + timeNow);
                            }
                            player.sendMessage("§7^ §o(x" + change.getFloorX() + "/y" + change.getFloorY() + "/z" + change.getFloorZ() + "/" + change.getLevel().getName() + ")");
                        }
                        i++;
                    }
                    break;
                } else if (args.length != 1 && args[1].startsWith("p:")) {
                    sender.sendMessage(getPrefix() + "You doesn't made any search yet!");
                    break;
                }

                db = plugin.getDatabase().getDatabase(player.getLevel().getName());

                if (db.isBusy()) {
                    sender.sendMessage(getPrefix() + "§eDatabase is querying a lot of data. This may take time");
                }

                // This section is by this configuration
                if (args.length == 1) {
                    sender.sendMessage(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " search [<user> <radius> <time>] [p:<page>]"));
                    break;
                }

                playerNameN = null;
                radiusN = 250;
                timeN = Integer.MAX_VALUE;
                if (Utils.isInteger(args[1])) {
                    radiusN = Integer.parseInt(args[1]);
                    if (radiusN > 500 || radiusN <= 0) {
                        sender.sendMessage(TextFormat.colorize(getPrefix() + "&cRadius is limited between [1, 500]"));
                        break;
                    }
                    if (args.length == 3) {
                        timeN = Utils.timeToSec(args[2]) * 1000;
                    }
                } else {
                    playerNameN = args[1];
                    if (args.length == 4) {
                        // /cp restore <user> <radius> <time>
                        if (!Utils.isInteger(args[2])) {
                            sender.sendMessage(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + " <user> <radius> <time>"));
                            break;
                        }
                        radiusN = Integer.parseInt(args[2]);

                        if (radiusN > 500 || radiusN <= 0) {
                            sender.sendMessage(TextFormat.colorize(getPrefix() + "&cRadius is limited between [1, 500]"));
                            break;
                        }
                        timeN = Utils.timeToSec(args[3]) * 1000;
                    } else if (args.length == 3) {
                        if (Utils.isInteger(args[2])) {
                            radiusN = Integer.parseInt(args[2]);
                        } else if (Utils.timeToSec(args[2]) != 0) {
                            timeN = Utils.timeToSec(args[2]) * 1000;
                        } else {
                            sender.sendMessage(TextFormat.colorize(getPrefix() + BBC.COMMAND_SYNTAX.format("/" + label + args[1] + " <time/radius>")));
                            break;
                        }
                    }
                }

                playerName = playerNameN;
                radius = radiusN;
                time = timeN;

                final int x2 = player.getLocation().getFloorX();
                final int z2 = player.getLocation().getFloorZ();

                db.addTask(() -> {
                    List<Block> blockChanges = db.getAllBlocks(x2, z2, radius, playerName, time);
                    playerSession.clearSearchValues();

                    if (blockChanges.size() == 0) {
                        sender.sendMessage(TextFormat.colorize(getPrefix() + "&cNo changes been found on the area"));
                        return;
                    }
                    blockChanges.sort(Comparator.comparingInt(Block::getTimeChanged).reversed());
                    playerSession.storeSearchValues(blockChanges);

                    int totalPage = blockChanges.size() % MESSAGES_LIMIT == 0 ? blockChanges.size() / MESSAGES_LIMIT : blockChanges.size() / MESSAGES_LIMIT + 1;
                    int pageNumber = Math.min(1, totalPage);
                    if (pageNumber < 1) {
                        pageNumber = 1;
                    }

                    sender.sendMessage("§7--- §6Rollback Lookup Results Page §e" + pageNumber + " §eof §a" + totalPage + " §7---");

                    int i = 0;
                    for (Block change : blockChanges) {
                        if (i <= MESSAGES_LIMIT) {
                            long timeChanged = (System.currentTimeMillis() - change.getChangedTimestamp()) / 1000; // (Now - Past) / 1000 (Milliseconds)

                            String timeNow = MainUtil.secToTime(timeChanged);
                            String playerUser = change.getPlayerName();

                            BaseBlock blockFrom = FaweCache.CACHE_BLOCK[change.getBlockFrom()];
                            BaseBlock blockTo = FaweCache.CACHE_BLOCK[change.getBlockTo()];

                            String from = Item.get(blockFrom.getId(), blockFrom.getData()).getName();
                            String to = Item.get(blockTo.getId(), blockTo.getData()).getName();

                            if (blockTo.getId() == 0) {
                                player.sendMessage("§e" + "" + playerUser + "§f removed §e#" + blockFrom.getId() + ":" + blockFrom.getData() + " (" + from + ") §7" + timeNow);
                            } else {
                                player.sendMessage("§e" + "" + playerUser + "§f placed §e#" + blockTo.getId() + ":" + blockFrom.getData() + " (" + to + ") §7" + timeNow);
                            }
                            player.sendMessage("§7^ §o(x" + change.getFloorX() + "/y" + change.getFloorY() + "/z" + change.getFloorZ() + "/" + change.getLevel().getName() + ")");
                        }
                        i++;
                    }
                    if (totalPage > 1) {
                        sender.sendMessage(TextFormat.colorize("&eUse /cp search p:<page> to see next page"));
                    }
                });
                break;
            default:
                sender.sendMessage("§cUnknown command, use /" + label + " help for a list of command");
                break;
        }
        return true;
    }

    private String getPrefix() {
        return plugin.getPrefix();
    }

    private void registerCommandParams() {
        Map<String, CommandParameter[]> commandMap = Maps.newHashMap();

        // Help command
        commandMap.put("help", new CommandParameter[]{
                new CommandParameter("help", false)
        });

        // Purge by name
        // Get names by area
        //
        // /cp restore <radius>
        // /cp restore <radius> <time>
        // /cp restore <user> <time>
        // /cp restore <user> <radius>
        // /cp restore <user> <radius> <time>

        // Rollback command
        commandMap.put("rollback-1", new CommandParameter[]{
                new CommandParameter("rollback", CommandParamType.STRING, false),
                new CommandParameter("radius", CommandParamType.INT, true),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        commandMap.put("rollback-2", new CommandParameter[]{
                new CommandParameter("rollback", CommandParamType.STRING, false),
                new CommandParameter("player", CommandParamType.TARGET, true),
                new CommandParameter("radius", CommandParamType.INT, false),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        commandMap.put("restore-1", new CommandParameter[]{
                new CommandParameter("restore", CommandParamType.STRING, false),
                new CommandParameter("radius", CommandParamType.INT, true),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        commandMap.put("restore-2", new CommandParameter[]{
                new CommandParameter("restore", CommandParamType.STRING, false),
                new CommandParameter("player", CommandParamType.TARGET, true),
                new CommandParameter("radius", CommandParamType.INT, false),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        // Inspect command
        commandMap.put("inspect-1", new CommandParameter[]{
                new CommandParameter("inspect", CommandParamType.STRING, false)
        });

        commandMap.put("inspect-2", new CommandParameter[]{
                new CommandParameter("i", CommandParamType.STRING, false)
        });

        // Purge command
        commandMap.put("purge", new CommandParameter[]{
                new CommandParameter("purge", CommandParamType.STRING, false),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        // Status command
        commandMap.put("status", new CommandParameter[]{
                new CommandParameter("status", CommandParamType.STRING, false)
        });

        // Log command
        commandMap.put("log", new CommandParameter[]{
                new CommandParameter("log", CommandParamType.STRING, false),
                new CommandParameter("radius", CommandParamType.INT, true)
        });

        // Search command
        commandMap.put("search-1", new CommandParameter[]{
                new CommandParameter("search", CommandParamType.STRING, false),
                new CommandParameter("radius", CommandParamType.INT, true),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        commandMap.put("search-2", new CommandParameter[]{
                new CommandParameter("search", CommandParamType.STRING, false),
                new CommandParameter("player", CommandParamType.TARGET, true),
                new CommandParameter("radius", CommandParamType.INT, false),
                new CommandParameter("time", CommandParamType.INT, false)
        });

        setCommandParameters(commandMap);
    }
}
