package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire de configuration pour les shops basés sur les zones
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

        // Créer la configuration par défaut si nécessaire
        createDefaultConfig();
    }

    private void createDefaultConfig() {
        if (!config.contains("settings")) {
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

            // Configuration du monde Market
            config.set("settings.world.market_world", "Market");
            config.set("settings.world.auto_create_shops", true);

            plugin.saveConfig();
        }
    }

    /**
     * Charge les shops depuis shops.yml (maintenant basés sur les zones)
     */
    public Map<String, Shop> loadShopsFromConfig() {
        Map<String, Shop> shops = new HashMap<>();

        ConfigurationSection shopsSection = shopsConfig.getConfigurationSection("shops");
        if (shopsSection != null) {
            for (String shopId : shopsSection.getKeys(false)) {
                ConfigurationSection shopSection = shopsSection.getConfigurationSection(shopId);
                if (shopSection != null) {
                    Shop shop = Shop.loadFromConfig(shopId, shopSection);
                    if (shop != null) {
                        shops.put(shopId, shop);
                    }
                }
            }
        }

        plugin.getLogger().info("Chargé " + shops.size() + " shop(s) depuis la configuration");
        return shops;
    }

    /**
     * Crée automatiquement des shops basés sur les zones détectées
     */
    public Map<String, Shop> createShopsFromZones(ZoneManager zoneManager) {
        Map<String, Shop> shops = new HashMap<>();

        String marketWorld = getMarketWorldName();

        // Obtenir toutes les zones du monde Market
        var marketZones = zoneManager.getZonesInWorld(marketWorld);

        plugin.getLogger().info("Création automatique de " + marketZones.size() + " shops depuis les zones du monde " + marketWorld);

        for (var zone : marketZones) {
            String shopId = zone.getId().replace("zone_" + marketWorld + "_", "shop_");
            Shop shop = new Shop(shopId, zone.getId());
            shops.put(shopId, shop);

            plugin.getLogger().info("Shop créé: " + shopId + " -> Zone: " + zone.getId());
        }

        return shops;
    }

    /**
     * Synchronise les shops avec les zones (crée les shops manquants)
     */
    public void synchronizeShopsWithZones(Map<String, Shop> currentShops, ZoneManager zoneManager) {
        String marketWorld = getMarketWorldName();
        var marketZones = zoneManager.getZonesInWorld(marketWorld);

        int newShops = 0;

        for (var zone : marketZones) {
            // Chercher un shop existant pour cette zone
            boolean shopExists = currentShops.values().stream()
                    .anyMatch(shop -> zone.getId().equals(shop.getZoneId()));

            if (!shopExists) {
                // Créer un nouveau shop pour cette zone
                String shopId = zone.getId().replace("zone_" + marketWorld + "_", "shop_");
                Shop newShop = new Shop(shopId, zone.getId());
                currentShops.put(shopId, newShop);
                newShops++;

                plugin.getLogger().info("Nouveau shop créé: " + shopId + " -> Zone: " + zone.getId());
            }
        }

        if (newShops > 0) {
            plugin.getLogger().info("Synchronisation terminée: " + newShops + " nouveaux shops créés");
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
    // GETTERS POUR LES PARAMÈTRES ZONES
    // ===============================

    public String getMarketWorldName() {
        return config.getString("settings.world.market_world", "Market");
    }

    public boolean isAutoCreateShopsEnabled() {
        return config.getBoolean("settings.world.auto_create_shops", true);
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