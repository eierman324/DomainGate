package dev.domaingate;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionListener implements Listener {

    private final DomainGate plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** Player UUID -> redirect target, queued at login, consumed at join. */
    private final Map<UUID, String> pendingRedirects = new ConcurrentHashMap<>();

    public ConnectionListener(DomainGate plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Connection handling (PlayerLoginEvent works on standalone Paper servers)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        // getHostname() returns "host:port" — strip the port
        String raw = event.getHostname();
        int colon = raw.lastIndexOf(':');
        String hostname = (colon != -1 ? raw.substring(0, colon) : raw).toLowerCase();

        String playerName = event.getPlayer().getName();
        String playerIp   = event.getAddress().getHostAddress();

        List<Map<?, ?>> rules = plugin.getConfig().getMapList("rules");
        boolean allowUnmatched = plugin.getConfig().getBoolean("allow-unmatched", true);
        boolean matched = false;

        for (Map<?, ?> rule : rules) {
            Object domainObj = rule.get("domain");
            if (domainObj == null) continue;
            if (!matches(hostname, domainObj.toString())) continue;

            matched = true;

            // redirect takes priority over block
            Object redirectObj = rule.get("redirect");
            if (redirectObj != null) {
                pendingRedirects.put(event.getPlayer().getUniqueId(), redirectObj.toString());
                plugin.getLogger().info("[REDIRECT] " + playerName + " (" + playerIp + ")"
                        + " via " + hostname + " -> " + redirectObj);
                return;
            }

            boolean block = Boolean.TRUE.equals(rule.get("block"));
            if (block) {
                Object msgObj = rule.get("message");
                String message = msgObj != null ? msgObj.toString()
                        : plugin.getConfig().getString("default-block-message",
                                "<red>Verbindung ueber diese Domain ist nicht erlaubt.");
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, parse(message));
                plugin.getLogger().info("[BLOCKED] " + playerName + " (" + playerIp + ")"
                        + " via " + hostname + " (rule: " + domainObj + ")");
                return;
            }

            // rule matched but no block/redirect — allow (e.g. motd-only rule)
            plugin.getLogger().info("[JOIN] " + playerName + " (" + playerIp + ")"
                    + " via " + hostname + " (rule: " + domainObj + ", allowed)");
            return;
        }

        if (!matched && !allowUnmatched) {
            String msg = plugin.getConfig().getString("default-block-message",
                    "<red>Verbindung ueber diese Domain ist nicht erlaubt.");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, parse(msg));
            plugin.getLogger().info("[BLOCKED] " + playerName + " (" + playerIp + ")"
                    + " via " + hostname + " (no matching rule, whitelist mode)");
        } else if (!matched) {
            plugin.getLogger().info("[JOIN] " + playerName + " (" + playerIp + ")"
                    + " via " + hostname + " (no rule matched)");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        String target = pendingRedirects.remove(event.getPlayer().getUniqueId());
        if (target == null) return;

        String host;
        int port;
        int colon = target.lastIndexOf(':');
        if (colon != -1) {
            host = target.substring(0, colon);
            try {
                port = Integer.parseInt(target.substring(colon + 1));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid redirect port in '" + target + "', using 25565.");
                port = 25565;
            }
        } else {
            host = target;
            port = 25565;
        }

        final String fHost = host;
        final int fPort = port;
        plugin.getServer().getScheduler().runTask(plugin,
                () -> event.getPlayer().transfer(fHost, fPort));
        plugin.getLogger().info("[REDIRECT] " + event.getPlayer().getName()
                + " transferred to " + fHost + ":" + fPort);
    }

    // -------------------------------------------------------------------------
    // MOTD handling
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onServerListPing(PaperServerListPingEvent event) {
        InetSocketAddress virtualHost = event.getClient().getVirtualHost();
        if (virtualHost == null) return;

        String hostname = virtualHost.getHostString().toLowerCase();

        for (Map<?, ?> rule : plugin.getConfig().getMapList("rules")) {
            Object domainObj = rule.get("domain");
            if (domainObj == null) continue;
            if (!matches(hostname, domainObj.toString())) continue;

            Object motdObj = rule.get("motd");
            if (motdObj != null) {
                event.motd(parse(motdObj.toString()));
                plugin.getLogger().info("[MOTD] " + hostname
                        + " pinged from " + event.getClient().getAddress().getAddress().getHostAddress()
                        + " (rule: " + domainObj + ")");
            }
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Component parse(String text) {
        if (text.contains("<") && text.contains(">")) {
            return mm.deserialize(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
     * Matches a hostname against a domain pattern with optional wildcards:
     *   pelmeni.cc   → exact match
     *   pelmeni.cc*  → hostname starts with "pelmeni.cc"
     *   *pelmeni.cc  → hostname ends with "pelmeni.cc"
     *   *pelmeni.cc* → hostname contains "pelmeni.cc"
     */
    private boolean matches(String hostname, String rawPattern) {
        String pattern = rawPattern.toLowerCase();
        boolean prefixWild = pattern.startsWith("*");
        boolean suffixWild = pattern.endsWith("*");
        String core = pattern;
        if (prefixWild) core = core.substring(1);
        if (suffixWild) core = core.substring(0, core.length() - 1);

        if (prefixWild && suffixWild) return hostname.contains(core);
        if (prefixWild)               return hostname.endsWith(core);
        if (suffixWild)               return hostname.startsWith(core);
        return hostname.equals(core);
    }
}
