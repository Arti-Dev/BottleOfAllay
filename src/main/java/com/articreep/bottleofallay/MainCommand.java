package com.articreep.bottleofallay;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("bottleofallay.reload")) {
                BottleOfAllay.getInstance().reloadConfig();
                BottleListeners.loadConfig();
                sender.sendMessage(ChatColor.AQUA + "BottleOfAllay config reloaded");
            } else {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the config! " +
                        ChatColor.DARK_GRAY + "(missing bottleofallay.reload)");
            }
            return true;
        }
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "BottleOfAllay");
        sender.sendMessage(ChatColor.GRAY + "This plugin allows allays to be stored as items by bottling them.");
        sender.sendMessage(ChatColor.GRAY + "Simply right-click an allay and it will be placed in the bottle. " +
                "The item the allay was holding will be preserved.");
        sender.sendMessage(ChatColor.GRAY + "If you're really nifty, you can drink the Bottle of Allay.");
        sender.sendMessage(ChatColor.GRAY + "Right-click a block with the Bottle of Allay to release the allay.");
        sender.sendMessage(ChatColor.RED + "To reload config: /bottleofallay reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            ArrayList<String> strings = new ArrayList<>();
            strings.add("reload");
            StringUtil.copyPartialMatches(args[0], strings, completions);
        }
        return completions;
    }
}
