package net.noruxtime.spy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoruxSpy extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> spyChat = new HashSet<>();
    private final Set<UUID> spyMsg = new HashSet<>();
    private final Set<UUID> spyCmd = new HashSet<>();

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("spy")).setExecutor(this);
        getLogger().info("NoruxSpy v1.0.0 успешно запущен для NoruxTime (MC 1.21.1)!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Команда только для игроков!");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("noruxspy.use")) {
            player.sendMessage(color(getConfig().getString("messages.prefix") + getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            player.sendMessage(color(getConfig().getString("messages.prefix") + getConfig().getString("messages.reloaded")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(color(getConfig().getString("messages.prefix") + getConfig().getString("messages.usage")));
            return true;
        }

        String mode = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        switch (mode) {
            case "chat":
                toggle(player, spyChat, "локальный чат");
                break;
            case "msg":
            case "message":
                toggle(player, spyMsg, "личные сообщения");
                break;
            case "cmd":
            case "command":
                toggle(player, spyCmd, "команды");
                break;
            case "all":
                boolean enable = !spyChat.contains(uuid);
                if (enable) {
                    spyChat.add(uuid);
                    spyMsg.add(uuid);
                    spyCmd.add(uuid);
                    sendMsg(player, "messages.enabled", "все режимы");
                } else {
                    spyChat.remove(uuid);
                    spyMsg.remove(uuid);
                    spyCmd.remove(uuid);
                    sendMsg(player, "messages.disabled", "все режимы");
                }
                break;
            default:
                player.sendMessage(color(getConfig().getString("messages.prefix") + getConfig().getString("messages.usage")));
                break;
        }
        return true;
    }

    private void toggle(Player player, Set<UUID> set, String modeName) {
        if (set.contains(player.getUniqueId())) {
            set.remove(player.getUniqueId());
            sendMsg(player, "messages.disabled", modeName);
        } else {
            set.add(player.getUniqueId());
            sendMsg(player, "messages.enabled", modeName);
        }
    }

    private void sendMsg(Player player, String path, String mode) {
        String msg = getConfig().getString(path, "").replace("%mode%", mode);
        player.sendMessage(color(getConfig().getString("messages.prefix") + msg));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String format = getConfig().getString("formats.chat", "")
                .replace("%player%", sender.getName())
                .replace("%message%", event.getMessage());

        String formattedMsg = color(format);
        boolean showOwn = getConfig().getBoolean("show-own", false);

        for (UUID uuid : spyChat) {
            Player admin = Bukkit.getPlayer(uuid);
            if (admin != null && admin.isOnline()) {
                if (!showOwn && admin.equals(sender)) continue;
                admin.sendMessage(formattedMsg);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        String fullCmd = event.getMessage();
        String cmdLower = fullCmd.toLowerCase();

        // Проверка на исключённые команды (пароли)
        List<String> excluded = getConfig().getStringList("excluded-commands");
        for (String ex : excluded) {
            if (cmdLower.startsWith(ex.toLowerCase())) return;
        }

        List<String> pmCmds = getConfig().getStringList("pm-commands");
        boolean isPM = false;
        String targetName = "Игрок";
        String pmMessage = fullCmd;

        for (String pm : pmCmds) {
            if (cmdLower.startsWith(pm.toLowerCase() + " ")) {
                isPM = true;
                String[] parts = fullCmd.split(" ", 3);
                if (parts.length >= 2) targetName = parts[1];
                if (parts.length >= 3) pmMessage = parts[2];
                break;
            }
        }

        boolean showOwn = getConfig().getBoolean("show-own", false);

        if (isPM) {
            String format = getConfig().getString("formats.msg", "")
                    .replace("%player%", sender.getName())
                    .replace("%target%", targetName)
                    .replace("%message%", pmMessage);
            String formattedMsg = color(format);

            for (UUID uuid : spyMsg) {
                Player admin = Bukkit.getPlayer(uuid);
                if (admin != null && admin.isOnline()) {
                    if (!showOwn && admin.equals(sender)) continue;
                    admin.sendMessage(formattedMsg);
                }
            }
        } else {
            String format = getConfig().getString("formats.cmd", "")
                    .replace("%player%", sender.getName())
                    .replace("%command%", fullCmd);
            String formattedMsg = color(format);

            for (UUID uuid : spyCmd) {
                Player admin = Bukkit.getPlayer(uuid);
                if (admin != null && admin.isOnline()) {
                    if (!showOwn && admin.equals(sender)) continue;
                    admin.sendMessage(formattedMsg);
                }
            }
        }
    }

    // Поддержка HEX и обычных Bukkit цветов (&a, &#FF5555)
    private String color(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}