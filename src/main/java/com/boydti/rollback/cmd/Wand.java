package com.boydti.rollback.cmd;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import com.boydti.fawe.config.BBC;
import com.boydti.rollback.config.Config;

public class Wand extends Command {
    
    public Wand(String name) {
        super("inspect");
    }

    @Override
    public boolean execute(CommandSender commandSender, String label, String[] args) {
        Player player;
        if (commandSender instanceof Player) {
            player = (Player) commandSender;
        } else {
            player = null;
            return false;
        }
        if (!player.hasPermission("rollback.inspect")) {
            player.sendMessage(BBC.color(Config.PREFIX + BBC.NO_PERM.format("rollback.inspect")));
        }
        final PlayerInventory inv = player.getInventory();
        Item wandItem = new Item(Config.ITEM);
        inv.remove(wandItem);
        final Item item = inv.getItemInHand();
        inv.setItemInHand(wandItem);
        if (item != null) {
            inv.addItem(item);
        }
        inv.sendContents(player);
        player.sendMessage(BBC.color(Config.PREFIX + "Left click to inspect, right click to inspect the adjacent block."));
        return false;
    }
}
