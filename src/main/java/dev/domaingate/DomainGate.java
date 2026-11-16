package dev.domaingate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class DomainGate extends JavaPlugin implements CommandExecutor {

    private ConnectionListener connectionListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        connectionListener = new ConnectionListener(this);
        getServer().getPluginManager().registerEvents(connectionListener, this);

        var cmd = getCommand("domaingate");
        if (cmd != null) cmd.setExecutor(this);

        getLogger().info("Loaded " + getConfig().getMapList("rules").size() + " rule(s).");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aDomainGate config reloaded. " +
                    getConfig().getMapList("rules").size() + " rule(s) active.");
            return true;
        }
        sender.sendMessage("§eUsage: /domaingate reload");
        return true;
    }
}
