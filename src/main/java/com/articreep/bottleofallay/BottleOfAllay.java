package com.articreep.bottleofallay;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public final class BottleOfAllay extends JavaPlugin implements CommandExecutor {

    private static BottleOfAllay instance;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        instance = this;

        if (getServer().getPluginManager().getPlugin("Pocketknife") != null) {
            Bukkit.getLogger().severe(ChatColor.RED + "Do not run BottleOfAllay with Pocketknife present," +
                    "as Pocketknife contains a duplicate of this plugin which will interfere with each other.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new BottleListeners(), this);
        getCommand("bottleofallay").setExecutor(new MainCommand());
        Bukkit.getConsoleSender().sendMessage(ChatColor.AQUA + "BottleOfAllay loaded");
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.AQUA + "BottleOfAllay unloaded");
    }

    public static BottleOfAllay getInstance() {
        return instance;
    }
}
