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
                e.printStackTrace();
            }
        }
        customMessagesConfig = YamlConfiguration.loadConfiguration(customMessagesFile);

        getLogger().info("Плагин успешно загружен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Плагин выключен.");
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
            String group = getPrimaryGroup(player);
            String joinMessage = getMessageForGroup(group);

            if (placeholderAPIEnabled && joinMessage != null) {
                joinMessage = PlaceholderAPI.setPlaceholders(player, joinMessage);
            }

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

            String statsMessage = getConfig().getString("stats-message")
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
        String leaveMessage = getConfig().getString("leave-messages." + group);

        if (placeholderAPIEnabled && leaveMessage != null) {
            leaveMessage = PlaceholderAPI.setPlaceholders(player, leaveMessage);
        }

        event.setQuitMessage(leaveMessage != null ? ChatColor.translateAlternateColorCodes('&', leaveMessage.replace("%player%", playerName)) : null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cjm")) {
            if (args.length == 0) {
                // Вывод списка доступных команд
                sender.sendMessage(ChatColor.GREEN + "=== Доступные команды ===");
                sender.sendMessage(ChatColor.YELLOW + "/cjm <группа> <сообщение> - Установить сообщение для группы.");
                sender.sendMessage(ChatColor.YELLOW + "/cjm gui - Открыть GUI для выбора сообщения.");
                sender.sendMessage(ChatColor.YELLOW + "/cjm reset - Сбросить своё уникальное сообщение.");
                sender.sendMessage(ChatColor.YELLOW + "/cjm delete <Nickname> - Удалить уникальное сообщение игрока (только для администраторов).");
                return true;
            }

            if (args[0].equalsIgnoreCase("reset")) {
                // Команда /cjm reset
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    removeCustomMessage(player.getName());
                    player.sendMessage(ChatColor.GREEN + "Ваше уникальное сообщение успешно сброшено.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Эта команда доступна только в игре.");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("delete") && sender.hasPermission("cjm.admin")) {
                // Команда /cjm delete <Nickname>
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Использование: /cjm delete <Nickname>");
                    return false;
                }

                String targetPlayerName = args[1];
                removeCustomMessage(targetPlayerName);
                sender.sendMessage(ChatColor.GREEN + "Уникальное сообщение игрока " + targetPlayerName + " успешно удалено.");
                return true;
            }

            if (args.length == 2) {
                // Команда /cjm <группа> <сообщение>
                String group = args[0];
                String message = args[1];

                getConfig().set("messages." + group, message);
                saveConfig();

                sender.sendMessage(ChatColor.GREEN + "Сообщение для группы " + group + " успешно обновлено!");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
                if (sender instanceof Player) {
                    openGUIMenu((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "Эта команда доступна только в игре.");
                }
                return true;
            }

            // Если команда не распознана
            sender.sendMessage(ChatColor.RED + "Неизвестная команда. Используйте /cjm для просмотра доступных команд.");
            return false;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GREEN + "Выберите сообщение")) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case 0:
                player.sendMessage(ChatColor.YELLOW + "Вы выбрали стандартное сообщение.");
                removeCustomMessage(player.getName());
                player.closeInventory();
                break;

            case 1:
                player.sendMessage(ChatColor.BLUE + "Введите ваше уникальное сообщение в чат.");
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
            player.sendMessage(ChatColor.GREEN + "Ваше уникальное сообщение установлено: " + customMessage);
            stopWaitingForMessage(player);
        }
    }

    private String getPrimaryGroup(Player player) {
        if (permission != null) {
            return permission.getPrimaryGroup(player);
        }
        return "default";
    }

    private String getMessageForGroup(String group) {
        Map<String, String> messages = loadMessagesFromConfig();
        return messages.getOrDefault(group, messages.get("default"));
    }

    private Map<String, String> loadMessagesFromConfig() {
        Map<String, String> messages = new HashMap<>();
        getConfig().getConfigurationSection("messages").getKeys(false).forEach(key -> {
            messages.put(key, getConfig().getString("messages." + key));
        });
        return messages;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            permission = rsp.getProvider();
        }
        return (permission != null);
    }

    private void openGUIMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, ChatColor.GREEN + "Выберите сообщение");

        ItemStack defaultItem = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta defaultMeta = defaultItem.getItemMeta();
        defaultMeta.setDisplayName(ChatColor.YELLOW + "Стандартное сообщение");
        defaultItem.setItemMeta(defaultMeta);

        ItemStack customItem = new ItemStack(org.bukkit.Material.BOOK);
        ItemMeta customMeta = customItem.getItemMeta();
        customMeta.setDisplayName(ChatColor.BLUE + "Настроить сообщение");
        customItem.setItemMeta(customMeta);

        inventory.setItem(0, defaultItem);
        inventory.setItem(1, customItem);

        player.openInventory(inventory);
    }

    // Метод для получения уникального сообщения игрока
    private String getCustomMessage(String playerName) {
        return customMessagesConfig.getString("players." + playerName);
    }

    // Метод для установки уникального сообщения
    private void setCustomMessage(String playerName, String message) {
        customMessagesConfig.set("players." + playerName, message);
        saveCustomMessages();
    }

    // Метод для удаления уникального сообщения
    private void removeCustomMessage(String playerName) {
        customMessagesConfig.set("players." + playerName, null);
        saveCustomMessages();
    }

    // Сохранение custom_messages.yml
    private void saveCustomMessages() {
        try {
            customMessagesConfig.save(customMessagesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Обработка сообщения (замена %player% и PlaceholderAPI)
    private String processMessage(String message, Player player) {
        if (placeholderAPIEnabled) {
            message = PlaceholderAPI.setPlaceholders(player, message);
        }
        return ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
    }

    // Ожидание сообщения от игрока
    private final Map<UUID, Boolean> waitingForMessage = new HashMap<>();

    private boolean isWaitingForMessage(Player player) {
        return waitingForMessage.getOrDefault(player.getUniqueId(), false);
    }

    private void waitForCustomMessage(Player player) {
        waitingForMessage.put(player.getUniqueId(), true);
    }

    private void stopWaitingForMessage(Player player) {
        waitingForMessage.put(player.getUniqueId(), false);
    }
}