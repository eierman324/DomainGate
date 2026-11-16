> [!WARNING]
> Every file in this repository – code, data, README, everything – was written by Claude (Anthropic's AI). No line was written by a human. Use at your own risk.

# DomainGate

Paper plugin that looks at the **domain a player connects with** (the address typed in the server list) and, per domain, blocks the connection, sends the player to another server or shows its own MOTD.

Example: `play.example.com` lets players in, `old.example.com` transfers them to the new address, `blocked.example.com` kicks them with a message.

## Features

- **block**: kick with a message of your own
- **redirect**: transfer the player to another server (`host:port`, port 25565 if omitted); takes priority over `block`
- **motd**: a separate MOTD in the server list when the server is pinged through that domain
- **allow-unmatched**: `true` lets connections without a matching rule in (blacklist), `false` blocks them (whitelist)
- Messages and MOTDs support MiniMessage tags (`<red>`, `<bold>`, `<newline>`…) and legacy colour codes (`&c`, `&l`…)
- `/domaingate reload` reloads the config (permission `domaingate.reload`, op by default)

A rule matches when the hostname **contains** its `domain`: `abc.de` matches `mc.abc.de`, `play.abc.de:8765` and `abc.de`.

## Configuration

`plugins/DomainGate/config.yml`:

```yaml
rules:
  - domain: "blocked.example.com"
    block: true
    message: "<red><bold>Access denied!</bold>\n<gray>Please use play.example.com."
    motd: "<red>This domain is blocked."

  - domain: "vip.example.com"
    motd: "<gold><bold>VIP server"

  - domain: "old.example.com"
    redirect: "play.example.com:25565"
    motd: "<yellow>Server moved!"

allow-unmatched: true
default-block-message: "<red>Connecting through this domain is not allowed."
```

The default config that is created on first start explains every option (in German).

## Requirements

- Paper 1.21.4 or newer, Java 21
- **redirect** uses Minecraft's transfer packet: the target server must accept transfers (`accepts-transfers=true` in its `server.properties`) and the player needs a client that supports transfers (1.20.5+).
- The connection domain only reaches the server directly. Behind a proxy (Velocity, BungeeCord) the proxy has to forward it.

## Build

```bash
mvn package
```

The jar lands in `target/`. Put it in the server's `plugins` folder and restart.
