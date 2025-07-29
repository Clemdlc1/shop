package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire de configuration pour les shops
 */
public class ConfigManager {

    // Configuration par défaut
    private static final long DEFAULT_RENT_PRICE = 1000;
    private static final long DEFAULT_RENT_DURATION = 3 * 24 * 60 * 60 * 1000L; // 3 jours
    private static final long DEFAULT_GRACE_DURATION = 24 * 60 * 60 * 1000L; // 1 jour
    private static final long DEFAULT_CUSTOM_MESSAGE_PRICE = 100;
    private static final long DEFAULT_MESSAGE_COOLDOWN = 5000L; // 5 secondes
    private static final long DEFAULT_FLOATING_TEXT_PRICE = 50;
    private static final int DEFAULT_MAX_FLOATING_TEXTS = 3;
    private static final long DEFAULT_NPC_PRICE = 500;
    private static final long DEFAULT_BOOST_PRICE = 200;
    private static final long DEFAULT_BOOST_DURATION = 60 * 60 * 1000L; // 1 heure
    private final PlayerShops plugin;
    private FileConfiguration config;
    private FileConfiguration shopsConfig;
    private File shopsFile;

    public ConfigManager(PlayerShops plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    private void loadConfigs() {
        // Configuration principale
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // Configuration des shops
        this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!shopsFile.exists()) {
            try {
                shopsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer shops.yml: " + e.getMessage());
            }
        }
        this.shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);

        // Créer les shops par défaut si nécessaire
        createDefaultShops();
    }

    private void createDefaultShops() {
        if (!config.contains("shops")) {
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        // Paramètres de location
        config.set("settings.rent.price", DEFAULT_RENT_PRICE);
        config.set("settings.rent.duration", DEFAULT_RENT_DURATION);
        config.set("settings.rent.grace_duration", DEFAULT_GRACE_DURATION);

        // Paramètres de personnalisation
        config.set("settings.customization.custom-message.price", DEFAULT_CUSTOM_MESSAGE_PRICE);
        config.set("settings.customization.custom-message.cooldown", DEFAULT_MESSAGE_COOLDOWN);
        config.set("settings.customization.floating-text.price", DEFAULT_FLOATING_TEXT_PRICE);
        config.set("settings.customization.floating-text.max-per-shop", DEFAULT_MAX_FLOATING_TEXTS);
        config.set("settings.customization.npc.price", DEFAULT_NPC_PRICE);

        // Paramètres des annonces
        config.set("settings.advertisement.boost.price", DEFAULT_BOOST_PRICE);
        config.set("settings.advertisement.boost.duration", DEFAULT_BOOST_DURATION);

        // Shops d'exemple
        ConfigurationSection shopsSection = config.createSection("shops");

        // Shop 1
        ConfigurationSection shop1 = shopsSection.createSection("shop1");
        shop1.set("world", "world");
        shop1.set("location.x", 100);
        shop1.set("location.y", 64);
        shop1.set("location.z", 100);
        shop1.set("location.yaw", 0);
        shop1.set("location.pitch", 0);
        shop1.set("corner1.x", 95);
        shop1.set("corner1.y", 64);
        shop1.set("corner1.z", 95);
        shop1.set("corner2.x", 105);
        shop1.set("corner2.y", 74);
        shop1.set("corner2.z", 105);

        // Shop 2
        ConfigurationSection shop2 = shopsSection.createSection("shop2");
        shop2.set("world", "world");
        shop2.set("location.x", 120);
        shop2.set("location.y", 64);
        shop2.set("location.z", 100);
        shop2.set("location.yaw", 0);
        shop2.set("location.pitch", 0);
        shop2.set("corner1.x", 115);
        shop2.set("corner1.y", 64);
        shop2.set("corner1.z", 95);
        shop2.set("corner2.x", 125);
        shop2.set("corner2.y", 74);
        shop2.set("corner2.z", 105);

        plugin.saveConfig();
    }

    public Map<String, Shop> loadShopsFromConfig() {
        Map<String, Shop> shops = new HashMap<>();

        ConfigurationSection shopsSection = config.getConfigurationSection("shops");
        if (shopsSection != null) {
            for (String shopId : shopsSection.getKeys(false)) {
                ConfigurationSection shopSection = shopsSection.getConfigurationSection(shopId);
                if (shopSection != null) {
                    Shop shop = createShopFromConfig(shopId, shopSection);
                    if (shop != null) {
                        shops.put(shopId, shop);
                    }
                }
            }
        }

        // Charger les données des shops depuis shops.yml
        ConfigurationSection shopsDataSection = shopsConfig.getConfigurationSection("shops");
        if (shopsDataSection != null) {
            for (String shopId : shopsDataSection.getKeys(false)) {
                if (shops.containsKey(shopId)) {
                    ConfigurationSection shopDataSection = shopsDataSection.getConfigurationSection(shopId);
                    Shop existingShop = shops.get(shopId);
                    loadShopData(existingShop, shopDataSection);
                }
            }
        }

        return shops;
    }

    private Shop createShopFromConfig(String shopId, ConfigurationSection section) {
        try {
            Shop shop = new Shop(shopId);

            String worldName = section.getString("world");
            if (worldName == null || Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("Monde invalide pour le shop " + shopId + ": " + worldName);
                return null;
            }

            // Location principale
            Location location = new Location(
                    Bukkit.getWorld(worldName),
                    section.getDouble("location.x"),
                    section.getDouble("location.y"),
                    section.getDouble("location.z"),
                    (float) section.getDouble("location.yaw", 0),
                    (float) section.getDouble("location.pitch", 0)
            );
            shop.setLocation(location);

            // Coins du shop
            Location corner1 = new Location(
                    Bukkit.getWorld(worldName),
                    section.getDouble("corner1.x"),
                    section.getDouble("corner1.y"),
                    section.getDouble("corner1.z")
            );
            shop.setCorner1(corner1);

            Location corner2 = new Location(
                    Bukkit.getWorld(worldName),
                    section.getDouble("corner2.x"),
                    section.getDouble("corner2.y"),
                    section.getDouble("corner2.z")
            );
            shop.setCorner2(corner2);

            return shop;
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du chargement du shop " + shopId + ": " + e.getMessage());
            return null;
        }
    }

    private void loadShopData(Shop shop, ConfigurationSection section) {
        if (section != null) {
            Shop loadedData = Shop.loadFromConfig(shop.getId(), section);

            // Copier les données chargées
            shop.setOwnerId(loadedData.getOwnerId());
            shop.setOwnerName(loadedData.getOwnerName());
            shop.setRented(loadedData.isRented());
            shop.setRentExpiry(loadedData.getRentExpiry());
            shop.setInGracePeriod(loadedData.isInGracePeriod());
            shop.setGraceExpiry(loadedData.getGraceExpiry());
            shop.getMembers().clear();
            shop.getMembers().addAll(loadedData.getMembers());
            shop.setCustomMessage(loadedData.getCustomMessage());
            shop.getFloatingTexts().clear();
            shop.getFloatingTexts().addAll(loadedData.getFloatingTexts());
            shop.setHasNPC(loadedData.hasNPC());
            shop.setNpcName(loadedData.getNpcName());
            shop.setNpcLocation(loadedData.getNpcLocation());
            shop.setAdvertisement(loadedData.getAdvertisement());
            shop.setAdvertisementBoostExpiry(loadedData.getAdvertisementBoostExpiry());
            shop.getChestShops().clear();
            shop.getChestShops().addAll(loadedData.getChestShops());
            shop.setBeaconLocation(loadedData.getBeaconLocation());
            shop.setHasBeacon(loadedData.hasBeacon());
        }
    }

    public void saveShopsToConfig(Map<String, Shop> shops) {
        ConfigurationSection shopsSection = shopsConfig.createSection("shops");

        for (Map.Entry<String, Shop> entry : shops.entrySet()) {
            ConfigurationSection shopSection = shopsSection.createSection(entry.getKey());
            entry.getValue().saveToConfig(shopSection);
        }

        try {
            shopsConfig.save(shopsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder shops.yml: " + e.getMessage());
        }
    }

    // ===============================
    // GETTERS POUR LES PRIX ET DURÉES
    // ===============================

    public long getRentPrice() {
        return config.getLong("settings.rent.price", DEFAULT_RENT_PRICE);
    }

    public long getRentDuration() {
        return config.getLong("settings.rent.duration", DEFAULT_RENT_DURATION);
    }

    public long getGraceDuration() {
        return config.getLong("settings.rent.grace_duration", DEFAULT_GRACE_DURATION);
    }

    public long getCustomMessagePrice() {
        return config.getLong("settings.customization.custom-message.price", DEFAULT_CUSTOM_MESSAGE_PRICE);
    }

    public long getMessageCooldown() {
        return config.getLong("settings.customization.custom-message.cooldown", DEFAULT_MESSAGE_COOLDOWN);
    }

    public long getFloatingTextPrice() {
        return config.getLong("settings.customization.floating-text.price", DEFAULT_FLOATING_TEXT_PRICE);
    }

    public int getMaxFloatingTexts() {
        return config.getInt("settings.customization.floating-text.max-per-shop", DEFAULT_MAX_FLOATING_TEXTS);
    }

    public long getNpcPrice() {
        return config.getLong("settings.customization.npc.price", DEFAULT_NPC_PRICE);
    }

    public long getBoostPrice() {
        return config.getLong("settings.advertisement.boost.price", DEFAULT_BOOST_PRICE);
    }

    public long getBoostDuration() {
        return config.getLong("settings.advertisement.boost.duration", DEFAULT_BOOST_DURATION);
    }

    // ===============================
    // GETTERS POUR LES MESSAGES
    // ===============================

    public String getMessage(String key) {
        return config.getString("messages." + key, "§cMessage manquant: " + key);
    }

    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }

        return message;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        this.shopsConfig = YamlConfiguration.loadConfiguration(shopsFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getShopsConfig() {
        return shopsConfig;
    }
}