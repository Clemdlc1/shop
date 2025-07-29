package fr.shop.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Représente un chest shop (commerce avec coffre et panneau)
 */
public class ChestShop {

    private final Location chestLocation;
    private final UUID ownerId;
    private final String ownerName;
    private final ItemStack item;
    private final long price;
    private final boolean sellMode; // true = vente, false = achat
    private final long createdAt;

    public ChestShop(Location chestLocation, UUID ownerId, String ownerName, ItemStack item, long price, boolean sellMode) {
        this.chestLocation = chestLocation;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.item = item.clone();
        this.price = price;
        this.sellMode = sellMode;
        this.createdAt = System.currentTimeMillis();
    }

    public ChestShop(Location chestLocation, UUID ownerId, String ownerName, ItemStack item, long price) {
        this(chestLocation, ownerId, ownerName, item, price, false); // Achat par défaut
    }

    // ===============================
    // GETTERS
    // ===============================

    /**
     * Crée un ChestShop à partir des lignes d'un panneau
     */
    public static ChestShop fromSignLines(String[] lines, Location chestLocation) {
        try {
            // Vérifier que c'est bien un ChestShop
            boolean isChestShop = lines[0].contains("[VENTE]") || lines[0].contains("[ACHAT]");
            if (!isChestShop) {
                return null;
            }

            boolean sellMode = lines[0].contains("[VENTE]");

            // Extraire le nom du propriétaire
            String ownerName = lines[1].replace("§9", "").trim();

            // Extraire l'item et la quantité
            String itemLine = lines[2].replace("§6", "").replace("§f", "");
            String[] itemParts = itemLine.split(" x");
            if (itemParts.length != 2) return null;

            String itemName = itemParts[0].trim().toUpperCase().replace(" ", "_");
            int amount = Integer.parseInt(itemParts[1]);

            // Extraire le prix
            String priceLine = lines[3].replace("§a", "").replace("§c", "").replace("§f", "").replace(" coins", "").trim();
            long price = parsePrice(priceLine);

            // Créer l'ItemStack
            org.bukkit.Material material = org.bukkit.Material.valueOf(itemName);
            ItemStack item = new ItemStack(material, amount);

            // Trouver l'UUID du propriétaire (approximatif)
            UUID ownerId = null;
            if (Bukkit.getPlayerExact(ownerName) != null) {
                ownerId = Bukkit.getPlayerExact(ownerName).getUniqueId();
            }

            return new ChestShop(chestLocation, ownerId, ownerName, item, price, sellMode);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse le prix depuis le texte formaté
     */
    private static long parsePrice(String priceText) {
        priceText = priceText.trim().toUpperCase();

        if (priceText.endsWith("T")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000000000L;
        } else if (priceText.endsWith("B")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000000L;
        } else if (priceText.endsWith("M")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000L;
        } else if (priceText.endsWith("K")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000L;
        } else {
            return Long.parseLong(priceText);
        }
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public long getPrice() {
        return price;
    }

    // ===============================
    // MÉTHODES POUR LES PANNEAUX
    // ===============================

    public boolean isSellMode() {
        return sellMode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Convertit le chest shop en lignes de panneau
     */
    public String[] toSignLines() {
        String[] lines = new String[4];

        // Ligne 1: [ChestShop] avec indicateur du mode
        lines[0] = sellMode ? "§4§l[VENTE]" : "§2§l[ACHAT]";

        // Ligne 2: Propriétaire
        lines[1] = "§9" + (ownerName.length() > 15 ? ownerName.substring(0, 12) + "..." : ownerName);

        // Ligne 3: Item et quantité
        String itemName = item.getType().name().toLowerCase().replace("_", " ");
        if (itemName.length() > 10) {
            itemName = itemName.substring(0, 10);
        }
        lines[2] = "§6" + itemName + " §fx" + item.getAmount();

        // Ligne 4: Prix avec indication du mode
        String priceText = formatPrice(price);
        if (sellMode) {
            lines[3] = "§c" + priceText + " §fcoins";
        } else {
            lines[3] = "§a" + priceText + " §fcoins";
        }

        return lines;
    }

    /**
     * Formate le prix pour l'affichage avec lettres étendues
     */
    private String formatPrice(long price) {
        if (price >= 1000000000000L) { // Trillions
            return (price / 1000000000000L) + "T";
        } else if (price >= 1000000000L) { // Billions
            return (price / 1000000000L) + "B";
        } else if (price >= 1000000L) { // Millions
            return (price / 1000000L) + "M";
        } else if (price >= 1000L) { // Thousands
            return (price / 1000L) + "K";
        } else {
            return String.valueOf(price);
        }
    }

    /**
     * Vérifie si le chest shop est valide
     */
    public boolean isValid() {
        return chestLocation != null &&
                ownerId != null &&
                ownerName != null &&
                item != null &&
                price > 0;
    }

    /**
     * Obtient une description du chest shop
     */
    public String getDescription() {
        String mode = sellMode ? "VENTE" : "ACHAT";
        return String.format("ChestShop{owner=%s, item=%s x%d, price=%d, mode=%s}",
                ownerName, item.getType().name(), item.getAmount(), price, mode);
    }

    /**
     * Obtient le mode en français
     */
    public String getModeText() {
        return sellMode ? "Vente" : "Achat";
    }

    /**
     * Obtient la couleur selon le mode
     */
    public String getModeColor() {
        return sellMode ? "§c" : "§a";
    }

    @Override
    public String toString() {
        return "ChestShop{" +
                "chestLocation=" + chestLocation +
                ", ownerId=" + ownerId +
                ", ownerName='" + ownerName + '\'' +
                ", item=" + item +
                ", price=" + price +
                ", sellMode=" + sellMode +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChestShop chestShop = (ChestShop) o;

        if (price != chestShop.price) return false;
        if (sellMode != chestShop.sellMode) return false;
        if (!chestLocation.equals(chestShop.chestLocation)) return false;
        if (!ownerId.equals(chestShop.ownerId)) return false;
        return item.equals(chestShop.item);
    }

    @Override
    public int hashCode() {
        int result = chestLocation.hashCode();
        result = 31 * result + ownerId.hashCode();
        result = 31 * result + item.hashCode();
        result = 31 * result + (int) (price ^ (price >>> 32));
        result = 31 * result + (sellMode ? 1 : 0);
        return result;
    }
}