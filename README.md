PaperCords
Papercords is a Paper plugin that runs one or more JDA Discord bot connections inside the Minecraft server process. Bot profiles and their selected slash commands are managed with /discordbots; connection and command activity is written to the Paper server log.

Requirements
Paper 1.21.11
Java 21 or newer
A Discord application and bot token for each profile (create these in the Discord Developer Portal)
The plugin creates and manages bot profiles; Discord requires each bot application to be created in the Developer Portal. Invite each bot with the bot and applications.commands OAuth scopes. Grant only the Discord permissions needed by the selected commands.

Build
Run mvn package. Copy target/DiscordBots-1.0.0.jar into the server's plugins folder and restart Paper.

Setup
Run /discordbots create <name> in-game or from the server console.
Put that bot's token in the generated plugins/DiscordBots/bots/<name>/token.txt file. Never put a token in chat, a command argument, or a public log.
Enable commands, for example /discordbots select <name> fun all, or select an individual command with /discordbots select <name> utility uppercase.
Start the profile with /discordbots start <name>.
Use /discordbots stop <name> before changing selections, then start it again to publish the updated command list.
Tokens are kept in per-profile files and are not written to the plugin's YAML configuration or normal plugin log messages. Protect the server's plugin data folder with appropriate operating-system file permissions.

Minecraft commands
/discordbots help
/discordbots create <name>
/discordbots list
/discordbots templates
/discordbots commands <bot> <template> [group]
/discordbots select <bot> <template> <command|all>
/discordbots unselect <bot> <template> <command|all>
/discordbots start <bot>
/discordbots stop <bot>
/discordbots status <bot>
The four built-in templates are moderation, fun, utility, and essentials. Each contains at least 75 selectable slash commands, grouped into three Discord-compatible subcommand groups. This keeps the number of top-level Discord application commands within Discord's command limits while allowing selections from multiple templates.

Command inputs and permissions
Utility and fun commands use an optional input field. Commands that need values require them when run (for example, /utility numbers add accepts two numbers). Essentials commands can accept an optional user lookup target. Moderation commands accept optional target, reason, and amount fields and check the invoking member's Discord permissions. The bot itself must also have the relevant Discord permission for actions such as bans and timeouts.

Bot connections are not started automatically after a server restart. Start profiles explicitly after checking that their tokens and selected commands are correct.
