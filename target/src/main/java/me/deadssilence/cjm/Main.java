package me.deadssilence.cjm;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.milkbowl.vault.permission.Permission;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {

    private Permission permission = null;
    private boolean placeholderAPIEnabled = false;
    private File customMessagesFile;
    private FileConfiguration customMessagesConfig;
    private File languageFile;
    private FileConfiguration languageConfig;

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        setupPermissions();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true;
        }

        // Инициализация файла custom_messages.yml
        customMessagesFile = new File(getDataFolder(), "custom_messages.yml");
        if (!customMessagesFile.exists()) {
            try {
                customMessagesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create custom_messages.yml file!");
                e.printStackTrace();
            }
        }

        // Загрузка конфигурации custom_messages.yml
        customMessagesConfig = YamlConfiguration.loadConfiguration(customMessagesFile);

        // Проверка успешности загрузки
        if (customMessagesConfig == null) {
            getLogger().severe("Failed to load custom_messages.yml file!");
            Bukkit.getPluginManager().disablePlugin(this); // Отключаем плагин, если файл не загружен
            return;
        }

        // Инициализация языкового файла
        String language = getConfig().getString("language", "en_en");
        languageFile = new File(getDataFolder(), "languages/" + language + ".yml");
        if (!languageFile.exists()) {
            saveResource("languages/" + language + ".yml", false);
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        getLogger().info("Custom Join Message plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Custom Join Message plugin disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Проверяем, есть ли у игрока уникальное сообщение
        String customMessage = getCustomMessage(playerName);
        if (customMessage != null) {
            event.setJoinMessage(processMessage(customMessage, player));
        } else {
            // Если уникального сообщения нет, используем сообщение из группы
            String group = getPrimaryGroup(player);

            // Получаем сообщение для группы
            String joinMessage = getMessageForGroup("join-message." + group);

            // Если сообщение для группы отсутствует, используем дефолтное
            if (joinMessage == null) {
                joinMessage = getMessageForGroup("join-message.default");
            }

            // Обработка PlaceholderAPI
            if (placeholderAPIEnabled && joinMessage != null) {
                joinMessage = PlaceholderAPI.setPlaceholders(player, joinMessage);
            }

            // Отправляем сообщение
            event.setJoinMessage(joinMessage != null ? ChatColor.translateAlternateColorCodes('&', joinMessage.replace("%player%", playerName)) : null);
        }

        // Статистика входов
        if (getConfig().getBoolean("stats-enabled")) {
            File statsFile = new File(getDataFolder(), "player_stats.yml");
            FileConfiguration statsConfig = YamlConfiguration.loadConfiguration(statsFile);

            int joins = statsConfig.getInt("player-stats." + player.getUniqueId() + ".joins", 0) + 1;
            statsConfig.set("player-stats." + player.getUniqueId() + ".joins", joins);

            try {
                statsConfig.save(statsFile);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Загружаем сообщение о статистике
            String statsMessage = getLocalizedMessage("stats-message", "&cMissing translation for stats-message")
                    .replace("%joins%", String.valueOf(joins))
                    .replace("%player%", playerName);

            player.sendMessage(ChatColor.translateAlternateColorCodes('&', statsMessage));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        String group = getPrimaryGroup(player);
        String leaveMessage = getMessageForGroup("leave-messages." + group);

        if (placeholderAPIEnabled && leaveMessage != null) {
            leaveMessage = PlaceholderAPI.setPlaceholders(player, leaveMessage);
        }

        event.setQuitMessage(leaveMessage != null ? ChatColor.translateAlternateColorCodes('&', leaveMessage.replace("%player%", playerName)) : null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cjm") && sender.hasPermission("cjm.admin")) {
            if (args.length == 0) {
                // Вывод списка доступных команд
                sender.sendMessage(ChatColor.GREEN + getLocalizedMessage("command-help", "&aAvailable commands:"));
                return true;
            }

            if (args[0].equalsIgnoreCase("reset")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    removeCustomMessage(player.getName());
                    player.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-reset", "&aYour custom message has been reset."));
                } else {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage", "&cUsage: /cjm     or /cjm gui"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("delete") && sender.hasPermission("cjm.admin")) {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage", "&cUsage: /cjm delete  "));
                    return false;
                }

                String targetPlayerName = args[1];
                removeCustomMessage(targetPlayerName);
                sender.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-deleted", "&aThe custom message of %player% has been deleted.").replace("%player%", targetPlayerName));
                return true;
            }

            if (args.length == 2) {
                String group = args[0];
                String message = args[1];

                getConfig().set("join-message." + group, message);
                saveConfig();

                sender.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-set", "&aJoin message for group %group% updated!").replace("%group%", group).replace("%message%", message));
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
                if (sender instanceof Player) {
                    openGUIMenu((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage", "&cUsage: /cjm     or /cjm gui"));
                }
                return true;
            }

            // Если команда не распознана
            sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage", "&cUsage: /cjm     or /cjm gui"));
            return false;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GREEN + getLocalizedMessage("menu-title", "&aSelect Message"))) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case 0:
                player.sendMessage(ChatColor.YELLOW + getLocalizedMessage("gui-select-default", "&eDefault message selected!"));
                removeCustomMessage(player.getName());
                player.closeInventory();
                break;

            case 1:
                player.sendMessage(ChatColor.BLUE + getLocalizedMessage("gui-set-custom", "&bType your custom message in chat!"));
                waitForCustomMessage(player);
                player.closeInventory();
                break;

            default:
                break;
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isWaitingForMessage(player)) {
            String customMessage = event.getMessage();
            setCustomMessage(player.getName(), customMessage);
            event.setCancelled(true); // Отменяем отправку сообщения в чат
            player.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-set", "&aYour custom message has been set!").replace("%message%", customMessage));
            stopWaitingForMessage(player);
        }
    }

    private String getPrimaryGroup(Player player) {
        if (permission != null) {
            return permission.getPrimaryGroup(player);
        }
        return "default"; // Если Vault недоступен, используем дефолтную группу
    }

    private String getMessageForGroup(String key) {
        return getConfig().getString(key, getConfig().getString("join-message.default"));
    }
    
    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            permission = rsp.getProvider();
        }
        return (permission != null);
    }

    private String getLocalizedMessage(String key) {
        return getLocalizedMessage(key, "&cMissing translation for " + key);
    }

    private String getLocalizedMessage(String key, String defaultValue) {
        if (languageConfig.contains(key)) {
            return ChatColor.translateAlternateColorCodes('&', languageConfig.getString(key));
        }
        return ChatColor.translateAlternateColorCodes('&', defaultValue);
    }

    private void openGUIMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, ChatColor.GREEN + getLocalizedMessage("menu-title", "&aSelect Message"));

        ItemStack defaultItem = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta defaultMeta = defaultItem.getItemMeta();
        defaultMeta.setDisplayName(ChatColor.YELLOW + getLocalizedMessage("gui-select-default", "&eDefault Message"));
        defaultItem.setItemMeta(defaultMeta);

        ItemStack customItem = new ItemStack(org.bukkit.Material.BOOK);
        ItemMeta customMeta = customItem.getItemMeta();
        customMeta.setDisplayName(ChatColor.BLUE + getLocalizedMessage("gui-set-custom", "&bSet Custom Message"));
        customItem.setItemMeta(customMeta);

        inventory.setItem(0, defaultItem);
        inventory.setItem(1, customItem);

        player.openInventory(inventory);
    }

    private String getCustomMessage(String playerName) {
        if (customMessagesConfig == null) {
            getLogger().warning("Custom messages configuration is not initialized. Unable to get custom message for player " + playerName);
            return null;
        }
        return customMessagesConfig.getString("players." + playerName);
    }

    private void setCustomMessage(String playerName, String message) {
        if (customMessagesConfig == null) {
            getLogger().warning("Custom messages configuration is not initialized. Unable to set custom message for player " + playerName);
            return;
        }

        customMessagesConfig.set("players." + playerName, message);
        saveCustomMessages();
    }

    private void removeCustomMessage(String playerName) {
        if (customMessagesConfig == null) {
            getLogger().warning("Custom messages configuration is not initialized. Unable to remove custom message for player " + playerName);
            return;
        }

        customMessagesConfig.set("players." + playerName, null);
        saveCustomMessages();
    }

    private void saveCustomMessages() {
        if (customMessagesConfig == null || customMessagesFile == null) {
            getLogger().warning("Custom messages configuration or file is not initialized. Unable to save changes.");
            return;
        }

        try {
            customMessagesConfig.save(customMessagesFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save custom_messages.yml file!");
            e.printStackTrace();
        }
    }

    private String processMessage(String message, Player player) {
        if (placeholderAPIEnabled) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        return ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
    }

    private final Map<UUID, Boolean> waitingForMessage = new HashMap<>();

    private void waitForCustomMessage(Player player) {
        waitingForMessage.put(player.getUniqueId(), true);
    }

    private boolean isWaitingForMessage(Player player) {
        return waitingForMessage.getOrDefault(player.getUniqueId(), false);
    }

    private void stopWaitingForMessage(Player player) {
        waitingForMessage.remove(player.getUniqueId());
    }
}