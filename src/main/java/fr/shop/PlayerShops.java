package fr.shop;

import fr.shop.commands.ShopAdminCommand;
import fr.shop.commands.ShopCommand;
import fr.shop.gui.ShopGUI;
import fr.shop.hooks.PrisonTycoonHook;
import fr.shop.listeners.ShopListeners;
import fr.shop.managers.*;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerShops extends JavaPlugin {

    private static PlayerShops instance;

    private PrisonTycoonHook prisonTycoonHook;
    private ConfigManager configManager;
    private ShopManager shopManager;
    private CommerceManager commerceManager;
    private VisualManager visualManager;
    private ZoneManager zoneManager;
    private ZoneScanner zoneScanner;
    private ShopGUI shopGUI;
    private MarketZoneBackupManager marketZoneBackupManager;

    // Getters
    public static PlayerShops getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Initialisation du hook avec PrisonTycoon
        if (!initializePrisonTycoonHook()) {
            getLogger().severe("PrisonTycoon non trouvé ! Le plugin se désactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisation des managers
        this.configManager = new ConfigManager(this);
        this.shopManager = new ShopManager(this);
        this.commerceManager = new CommerceManager(this);
        this.visualManager = new VisualManager(this);
        this.shopGUI = new ShopGUI(this);
        this.zoneManager = new ZoneManager(this);
        this.zoneScanner = new ZoneScanner(this, zoneManager);
        this.marketZoneBackupManager = new MarketZoneBackupManager(this, zoneManager);

        // Enregistrement des commandes
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("shopadmin").setExecutor(new ShopAdminCommand(this));

        // Enregistrement des listeners
        getServer().getPluginManager().registerEvents(new ShopListeners(this), this);

        getLogger().info("PlayerShops activé avec succès!");
    }

    @Override
    public void onDisable() {
        if (shopManager != null) {
            shopManager.saveAll();
        }
        if (visualManager != null) {
            visualManager.shutdown();
        }
        getLogger().info("PlayerShops désactivé!");
    }

    private boolean initializePrisonTycoonHook() {
        if (!getServer().getPluginManager().isPluginEnabled("PrisonTycoon")) {
            return false;
        }

        try {
            this.prisonTycoonHook = new PrisonTycoonHook();
            return prisonTycoonHook.isEnabled();
        } catch (Exception e) {
            getLogger().severe("Erreur lors de l'initialisation du hook PrisonTycoon: " + e.getMessage());
            return false;
        }
    }

    public PrisonTycoonHook getPrisonTycoonHook() {
        return prisonTycoonHook;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public CommerceManager getCommerceManager() {
        return commerceManager;
    }

    public VisualManager getVisualManager() {
        return visualManager;
    }

    public ShopGUI getShopGUI() {
        return shopGUI;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public ZoneScanner getZoneScanner() {
        return zoneScanner;
    }

    public MarketZoneBackupManager getMarketZoneBackupManager() {
        return marketZoneBackupManager;
    }
}