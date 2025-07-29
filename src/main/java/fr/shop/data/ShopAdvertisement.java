package fr.shop.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente une annonce pour un shop
 */
public class ShopAdvertisement {

    private String title;
    private String description;
    private List<String> details;
    private String category;
    private boolean active;
    private long createdAt;
    private long lastUpdated;

    public ShopAdvertisement() {
        this.details = new ArrayList<>();
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdated = System.currentTimeMillis();
    }

    public ShopAdvertisement(String title, String description, String category) {
        this();
        this.title = title;
        this.description = description;
        this.category = category;
    }

    // ===============================
    // GETTERS ET SETTERS
    // ===============================

    public static ShopAdvertisement loadFromConfig(ConfigurationSection section) {
        ShopAdvertisement ad = new ShopAdvertisement();

        ad.title = section.getString("title");
        ad.description = section.getString("description");
        ad.details.addAll(section.getStringList("details"));
        ad.category = section.getString("category");
        ad.active = section.getBoolean("active", true);
        ad.createdAt = section.getLong("createdAt", System.currentTimeMillis());
        ad.lastUpdated = section.getLong("lastUpdated", System.currentTimeMillis());

        return ad;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.lastUpdated = System.currentTimeMillis();
    }

    public List<String> getDetails() {
        return details;
    }

    public void addDetail(String detail) {
        this.details.add(detail);
        this.lastUpdated = System.currentTimeMillis();
    }

    public void removeDetail(int index) {
        if (index >= 0 && index < details.size()) {
            this.details.remove(index);
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public void clearDetails() {
        this.details.clear();
        this.lastUpdated = System.currentTimeMillis();
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.lastUpdated = System.currentTimeMillis();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    public long getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Formate l'annonce pour l'affichage
     */
    public List<String> getFormattedAd() {
        List<String> formatted = new ArrayList<>();

        if (title != null && !title.isEmpty()) {
            formatted.add("§6§l" + title);
        }

        if (description != null && !description.isEmpty()) {
            formatted.add("§7" + description);
        }

        if (!details.isEmpty()) {
            formatted.add("§8§m--------------------");
            for (String detail : details) {
                formatted.add("§f• " + detail);
            }
        }

        if (category != null && !category.isEmpty()) {
            formatted.add("§8Catégorie: §e" + category);
        }

        return formatted;
    }

    // ===============================
    // SÉRIALISATION
    // ===============================

    /**
     * Valide l'annonce
     */
    public boolean isValid() {
        return title != null && !title.trim().isEmpty() &&
                description != null && !description.trim().isEmpty();
    }

    public void saveToConfig(ConfigurationSection section) {
        section.set("title", title);
        section.set("description", description);
        section.set("details", details);
        section.set("category", category);
        section.set("active", active);
        section.set("createdAt", createdAt);
        section.set("lastUpdated", lastUpdated);
    }

    @Override
    public String toString() {
        return "ShopAdvertisement{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", active=" + active +
                '}';
    }
}