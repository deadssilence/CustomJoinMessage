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

    private Permission permission = null; // Vault permission system
    private boolean placeholderAPIEnabled = false; // PlaceholderAPI integration flag
    private File customMessagesFile; // File for custom join messages
    private FileConfiguration customMessagesConfig; // Configuration for custom join messages
    private File languageFile; // Language file based on the selected language
    private FileConfiguration languageConfig; // Configuration for the selected language

    @Override
    public void onEnable() {
        getConfig().options().copyDefaults(true); // Copy default configuration
        saveDefaultConfig(); // Save the default configuration
        getServer().getPluginManager().registerEvents(this, this); // Register event listeners

        setupPermissions(); // Initialize Vault permissions

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true; // Enable PlaceholderAPI support
        }

        // Initialize the custom_messages.yml file
        customMessagesFile = new File(getDataFolder(), "custom_messages.yml");
        if (!customMessagesFile.exists()) {
            try {
                customMessagesFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        customMessagesConfig = YamlConfiguration.loadConfiguration(customMessagesFile);

        // Initialize the language file based on the 'language' setting in config.yml
        String language = getConfig().getString("language", "en_en");
        languageFile = new File(getDataFolder(), "languages/" + language + ".yml");
        if (!languageFile.exists()) {
            saveResource("languages/" + language + ".yml", false); // Save the default language file if it doesn't exist
        }
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        getLogger().info("Custom Join Message plugin enabled!"); // Log plugin startup
    }

    @Override
    public void onDisable() {
        getLogger().info("Custom Join Message plugin disabled."); // Log plugin shutdown
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Check if the player has a custom join message
        String customMessage = getCustomMessage(playerName);
        if (customMessage != null) {
            event.setJoinMessage(processMessage(customMessage, player)); // Use the custom join message
        } else {
            // Get the player's primary group and load the join message from the main config
            String group = getPrimaryGroup(player);
            String joinMessage = getConfig().getString("join-message." + group, getConfig().getString("join-message.default"));

            if (placeholderAPIEnabled && joinMessage != null) {
                joinMessage = PlaceholderAPI.setPlaceholders(player, joinMessage); // Replace placeholders using PlaceholderAPI
            }

            event.setJoinMessage(joinMessage != null ? ChatColor.translateAlternateColorCodes('&', joinMessage.replace("%player%", playerName)) : null);
        }

        // Handle join statistics if enabled
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

            // Send the join statistics message to the player
            String statsMessage = getConfig().getString("stats-message").replace("%joins%", String.valueOf(joins)).replace("%player%", playerName);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', statsMessage));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();

        // Get the player's primary group and load the quit message from the main config
        String group = getPrimaryGroup(player);
        String leaveMessage = getConfig().getString("leave-message." + group, getConfig().getString("leave-message.default"));

        if (placeholderAPIEnabled && leaveMessage != null) {
            leaveMessage = PlaceholderAPI.setPlaceholders(player, leaveMessage); // Replace placeholders using PlaceholderAPI
        }

        event.setQuitMessage(leaveMessage != null ? ChatColor.translateAlternateColorCodes('&', leaveMessage.replace("%player%", playerName)) : null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("cjm")) {
            if (args.length == 0) {
                // Display the list of available commands
                sender.sendMessage(ChatColor.GREEN + getLocalizedMessage("command-help"));
                return true;
            }

            if (args[0].equalsIgnoreCase("reset")) {
                // Handle the /cjm reset command
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    removeCustomMessage(player.getName());
                    player.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-reset"));
                } else {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage"));
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("delete") && sender.hasPermission("cjm.admin")) {
                // Handle the /cjm delete <Nickname> command
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage"));
                    return false;
                }

                String targetPlayerName = args[1];
                removeCustomMessage(targetPlayerName);
                sender.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-deleted").replace("%player%", targetPlayerName));
                return true;
            }

            if (args.length == 2) {
                // Handle the /cjm <group> <message> command
                String group = args[0];
                String message = args[1];

                getConfig().set("join-message." + group, message);
                saveConfig();

                sender.sendMessage(ChatColor.GREEN + "&aJoin message for group " + group + " updated!");
                return true;
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("gui")) {
                // Handle the /cjm gui command
                if (sender instanceof Player) {
                    openGUIMenu((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage"));
                }
                return true;
            }

            // If the command is not recognized
            sender.sendMessage(ChatColor.RED + getLocalizedMessage("command-usage"));
            return false;
        }
        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ChatColor.GREEN + "Select Message")) {
            return;
        }

        event.setCancelled(true); // Cancel the inventory click event

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case 0:
                // Handle the "Default Message" button
                player.sendMessage(ChatColor.YELLOW + getLocalizedMessage("gui-select-default"));
                removeCustomMessage(player.getName());
                player.closeInventory();
                break;

            case 1:
                // Handle the "Set Custom Message" button
                player.sendMessage(ChatColor.BLUE + getLocalizedMessage("gui-set-custom"));
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
            event.setCancelled(true); // Cancel the chat message event
            player.sendMessage(ChatColor.GREEN + getLocalizedMessage("custom-message-set").replace("%message%", customMessage));
            stopWaitingForMessage(player);
        }
    }

    private String getPrimaryGroup(Player player) {
        if (permission != null) {
            return permission.getPrimaryGroup(player); // Get the player's primary group using Vault
        }
        return "default";
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        if (rsp != null) {
            permission = rsp.getProvider();
        }
        return (permission != null); // Return true if Vault permissions are successfully initialized
    }

    private void openGUIMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, ChatColor.GREEN + "Select Message");

        ItemStack defaultItem = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta defaultMeta = defaultItem.getItemMeta();
        defaultMeta.setDisplayName(ChatColor.YELLOW + "Default Message");
        defaultItem.setItemMeta(defaultMeta);

        ItemStack customItem = new ItemStack(org.bukkit.Material.BOOK);
        ItemMeta customMeta = customItem.getItemMeta();
        customMeta.setDisplayName(ChatColor.BLUE + "Set Custom Message");
        customItem.setItemMeta(customMeta);

        inventory.setItem(0, defaultItem);
        inventory.setItem(1, customItem);

        player.openInventory(inventory); // Open the GUI for the player
    }

    // Method to get a localized message from the language file
    private String getLocalizedMessage(String key) {
        return languageConfig.getString(key, "&cMissing translation for " + key);
    }

    // Method to get a custom join message for a player
    private String getCustomMessage(String playerName) {
        return customMessagesConfig.getString("players." + playerName);
    }

    // Method to set a custom join message for a player
    private void setCustomMessage(String playerName, String message) {
        customMessagesConfig.set("players." + playerName, message);
        saveCustomMessages();
    }

    // Method to remove a player's custom join message
    private void removeCustomMessage(String playerName) {
        customMessagesConfig.set("players." + playerName, null);
        saveCustomMessages();
    }

    // Method to save the custom_messages.yml file
    private void saveCustomMessages() {
        try {
            customMessagesConfig.save(customMessagesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Method to process messages (replace %player% and handle PlaceholderAPI)
    private String processMessage(String message, Player player) {
        if (placeholderAPIEnabled) {
            message = PlaceholderAPI.setPlaceholders(player, message); // Replace placeholders using PlaceholderAPI
        }
        return ChatColor.translateAlternateColorCodes('&', message.replace("%player%", player.getName()));
    }

    // Map to track players waiting for a custom message input
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