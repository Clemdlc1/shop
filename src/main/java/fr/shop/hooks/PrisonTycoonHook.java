package fr.shop.hooks;

import fr.prisontycoon.api.PrisonTycoonAPI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Hook pour interfacer avec l'API PrisonTycoon
 */
public class PrisonTycoonHook {

    private PrisonTycoonAPI api;
    private boolean enabled = false;

    public PrisonTycoonHook() {
        try {
            if (PrisonTycoonAPI.isAvailable()) {
                this.api = PrisonTycoonAPI.getInstance();
                this.enabled = true;
            }
        } catch (Exception e) {
            this.enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled && api != null;
    }

    // ===============================
    // MÉTHODES POUR LES COINS
    // ===============================

    public long getCoins(UUID playerId) {
        if (!isEnabled()) return 0;
        return api.getCoins(playerId);
    }

    public long getCoins(Player player) {
        if (!isEnabled()) return 0;
        return api.getCoins(player);
    }

    public boolean hasCoins(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.hasCoins(playerId, amount);
    }

    public boolean removeCoins(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.removeCoins(playerId, amount);
    }

    public boolean removeCoins(Player player, long amount) {
        if (!isEnabled()) return false;
        return api.removeCoins(player, amount);
    }

    public boolean addCoins(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.addCoins(playerId, amount);
    }

    public boolean addCoins(Player player, long amount) {
        if (!isEnabled()) return false;
        return api.addCoins(player, amount);
    }

    // ===============================
    // MÉTHODES POUR LES BEACONS
    // ===============================

    public long getBeacons(UUID playerId) {
        if (!isEnabled()) return 0;
        return api.getBeacons(playerId);
    }

    public long getBeacons(Player player) {
        if (!isEnabled()) return 0;
        return api.getBeacons(player);
    }

    public boolean hasBeacons(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.hasBeacons(playerId, amount);
    }

    public boolean removeBeacons(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.removeBeacons(playerId, amount);
    }

    public boolean removeBeacons(Player player, long amount) {
        if (!isEnabled()) return false;
        return api.removeBeacons(player, amount);
    }

    public boolean addBeacons(UUID playerId, long amount) {
        if (!isEnabled()) return false;
        return api.addBeacons(playerId, amount);
    }

    public boolean addBeacons(Player player, long amount) {
        if (!isEnabled()) return false;
        return api.addBeacons(player, amount);
    }

    // ===============================
    // MÉTHODES POUR LES PROFESSIONS
    // ===============================

    public String getActiveProfession(UUID playerId) {
        if (!isEnabled()) return "";
        return api.getActiveProfession(playerId);
    }

    public int getProfessionLevel(UUID playerId) {
        if (!isEnabled()) return 0;
        return api.getProfessionLevel(playerId);
    }

    /**
     * Vérifie si un joueur est commerçant niveau 5+
     */
    public boolean isMerchantLevel5Plus(UUID playerId) {
        if (!isEnabled()) return false;

        String profession = getActiveProfession(playerId);
        if (profession == null || !profession.equalsIgnoreCase("commerçant")) {
            return false;
        }

        return getProfessionLevel(playerId) >= 5;
    }

    // ===============================
    // MÉTHODES POUR LES PERMISSIONS
    // ===============================

    public boolean hasPermission(Player player, String permission) {
        if (!isEnabled()) return player.hasPermission(permission);
        return api.hasPermission(player, permission);
    }

    public boolean addPermission(UUID playerId, String permission) {
        if (!isEnabled()) return false;
        return api.addPermission(playerId, permission);
    }

    public boolean removePermission(UUID playerId, String permission) {
        if (!isEnabled()) return false;
        return api.removePermission(playerId, permission);
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Calcule le prix avec réduction pour les commerçants niveau 5+
     */
    public long calculatePrice(UUID playerId, long basePrice) {
        if (!isEnabled()) return basePrice;

        if (isMerchantLevel5Plus(playerId)) {
            return basePrice / 2; // Division par 2 pour les commerçants niveau 5+
        }

        return basePrice;
    }

    /**
     * Effectue une transaction en beacons avec vérification
     */
    public boolean processBeaconTransaction(UUID playerId, long amount, String reason) {
        if (!isEnabled()) return false;

        if (!hasBeacons(playerId, amount)) {
            return false;
        }

        return removeBeacons(playerId, amount);
    }

    /**
     * Effectue une transaction en beacons avec vérification (player en ligne)
     */
    public boolean processBeaconTransaction(Player player, long amount, String reason) {
        if (!isEnabled()) return false;

        if (!hasBeacons(player.getUniqueId(), amount)) {
            return false;
        }

        return removeBeacons(player, amount);
    }

    /**
     * Rembourse des beacons à un joueur
     */
    public boolean refundBeacons(UUID playerId, long amount, String reason) {
        if (!isEnabled()) return false;
        return addBeacons(playerId, amount);
    }

    /**
     * Vérifie si un item est une legendary pickaxe
     */
    public boolean isLegendaryPickaxe(ItemStack item) {
        if (!isEnabled()) return false;
        return api.isLegendaryPickaxe(item);
    }
}