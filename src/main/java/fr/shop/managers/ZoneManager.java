package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des zones de shop basées sur les beacons
 */
public class ZoneManager {

    private final PlayerShops plugin;
    private final Map<String, Zone> zones;
    private final ZoneScanner scanner;

    private File zonesFile;
    private FileConfiguration zonesConfig;

    public ZoneManager(PlayerShops plugin) {
        this.plugin = plugin;
        this.zones = new ConcurrentHashMap<>();
        this.scanner = new ZoneScanner(plugin, this);

        initializeZonesFile();
        loadZones();
    }

    // ===============================
    // INITIALISATION
    // ===============================

    private void initializeZonesFile() {
        this.zonesFile = new File(plugin.getDataFolder(), "zones.yml");

        if (!zonesFile.exists()) {
            try {
                zonesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer zones.yml: " + e.getMessage());
            }
        }

        this.zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);

        // Créer la structure de base si elle n'existe pas
        if (!zonesConfig.contains("zones")) {
            zonesConfig.createSection("zones");
            saveZonesConfig();
        }
    }

    // ===============================
    // CHARGEMENT ET SAUVEGARDE
    // ===============================

    /**
     * Charge toutes les zones depuis le fichier zones.yml
     */
    public void loadZones() {
        zones.clear();

        ConfigurationSection zonesSection = zonesConfig.getConfigurationSection("zones");
        if (zonesSection == null) return;

        int loadedCount = 0;
        for (String zoneId : zonesSection.getKeys(false)) {
            ConfigurationSection zoneSection = zonesSection.getConfigurationSection(zoneId);
            if (zoneSection != null) {
                try {
                    Zone zone = Zone.loadFromConfig(zoneId, zoneSection);
                    if (zone != null) {
                        zones.put(zoneId, zone);
                        loadedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de la zone " + zoneId + ": " + e.getMessage());
                }
            }
        }

        plugin.getLogger().info("Chargé " + loadedCount + " zones");
    }

    /**
     * Sauvegarde toutes les zones dans le fichier zones.yml
     */
    public void saveZones() {
        ConfigurationSection zonesSection = zonesConfig.createSection("zones");

        for (Map.Entry<String, Zone> entry : zones.entrySet()) {
            ConfigurationSection zoneSection = zonesSection.createSection(entry.getKey());
            entry.getValue().saveToConfig(zoneSection);
        }

        saveZonesConfig();
        plugin.getLogger().info("Sauvegardé " + zones.size() + " zones");
    }

    private void saveZonesConfig() {
        try {
            zonesConfig.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder zones.yml: " + e.getMessage());
        }
    }

    // ===============================
    // GESTION DES ZONES
    // ===============================

    /**
     * Ajoute une zone
     */
    public void addZone(Zone zone) {
        zones.put(zone.getId(), zone);
    }

    /**
     * Supprime une zone
     */
    public void removeZone(String zoneId) {
        zones.remove(zoneId);
    }

    /**
     * Nettoie toutes les zones d'un monde
     */
    public void clearZonesForWorld(String worldName) {
        zones.entrySet().removeIf(entry -> entry.getValue().getWorldName().equals(worldName));
    }

    /**
     * Trouve la zone contenant une location
     */
    public Zone getZoneAtLocation(Location location) {
        if (location == null) return null;

        for (Zone zone : zones.values()) {
            if (zone.containsLocation(location)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Trouve toutes les zones d'un monde
     */
    public List<Zone> getZonesInWorld(String worldName) {
        return zones.values().stream()
                .filter(zone -> zone.getWorldName().equals(worldName))
                .sorted(Comparator.comparing(Zone::getId))
                .toList();
    }

    /**
     * Obtient une zone par son ID
     */
    public Zone getZone(String zoneId) {
        return zones.get(zoneId);
    }

    /**
     * Obtient toutes les zones
     */
    public Collection<Zone> getAllZones() {
        return new ArrayList<>(zones.values());
    }

    /**
     * Vérifie si une location est dans une zone
     */
    public boolean isInAnyZone(Location location) {
        return getZoneAtLocation(location) != null;
    }

    // ===============================
    // SCANNER
    // ===============================

    /**
     * Obtient le scanner de zones
     */
    public ZoneScanner getScanner() {
        return scanner;
    }

    // ===============================
    // STATISTIQUES ET INFORMATIONS
    // ===============================

    /**
     * Obtient des statistiques sur les zones
     */
    public ZoneStats getStats() {
        int totalZones = zones.size();
        int totalBeacons = 0;
        int totalBlocks = 0;
        Map<String, Integer> worldCounts = new HashMap<>();

        for (Zone zone : zones.values()) {
            totalBeacons += zone.getBeaconCount();
            totalBlocks += zone.getBlockCount();
            worldCounts.merge(zone.getWorldName(), 1, Integer::sum);
        }

        return new ZoneStats(totalZones, totalBeacons, totalBlocks, worldCounts);
    }

    /**
     * Trouve les zones proches d'une location
     */
    public List<Zone> getNearbyZones(Location location, double radius) {
        List<Zone> nearbyZones = new ArrayList<>();

        for (Zone zone : zones.values()) {
            if (!zone.getWorldName().equals(location.getWorld().getName())) continue;

            Location center = zone.getCenterLocation();
            if (center != null && center.distance(location) <= radius) {
                nearbyZones.add(zone);
            }
        }

        nearbyZones.sort(Comparator.comparing(zone -> {
            Location center = zone.getCenterLocation();
            return center != null ? center.distance(location) : Double.MAX_VALUE;
        }));

        return nearbyZones;
    }

    /**
     * Valide l'intégrité des zones (vérifie que les beacons existent toujours)
     */
    public ValidationResult validateZones() {
        List<String> issues = new ArrayList<>();
        int validZones = 0;
        int invalidZones = 0;

        for (Zone zone : zones.values()) {
            boolean isValid = true;

            // Vérifier que le monde existe
            if (org.bukkit.Bukkit.getWorld(zone.getWorldName()) == null) {
                issues.add("Zone " + zone.getId() + ": Monde '" + zone.getWorldName() + "' introuvable");
                isValid = false;
            } else {
                // Vérifier que les beacons existent toujours
                for (Location beaconLoc : zone.getBeaconLocations()) {
                    if (beaconLoc.getBlock().getType() != org.bukkit.Material.BEACON) {
                        issues.add("Zone " + zone.getId() + ": Beacon manquant à " +
                                beaconLoc.getBlockX() + "," + beaconLoc.getBlockY() + "," + beaconLoc.getBlockZ());
                        isValid = false;
                    }
                }
            }

            if (isValid) {
                validZones++;
            } else {
                invalidZones++;
            }
        }

        return new ValidationResult(validZones, invalidZones, issues);
    }

    // ===============================
    // CLASSES UTILITAIRES
    // ===============================

    public static class ZoneStats {
        private final int totalZones;
        private final int totalBeacons;
        private final int totalBlocks;
        private final Map<String, Integer> worldCounts;

        public ZoneStats(int totalZones, int totalBeacons, int totalBlocks, Map<String, Integer> worldCounts) {
            this.totalZones = totalZones;
            this.totalBeacons = totalBeacons;
            this.totalBlocks = totalBlocks;
            this.worldCounts = new HashMap<>(worldCounts);
        }

        public int getTotalZones() { return totalZones; }
        public int getTotalBeacons() { return totalBeacons; }
        public int getTotalBlocks() { return totalBlocks; }
        public Map<String, Integer> getWorldCounts() { return new HashMap<>(worldCounts); }

        @Override
        public String toString() {
            return "ZoneStats{" +
                    "zones=" + totalZones +
                    ", beacons=" + totalBeacons +
                    ", blocks=" + totalBlocks +
                    ", worlds=" + worldCounts +
                    '}';
        }
    }

    public static class ValidationResult {
        private final int validZones;
        private final int invalidZones;
        private final List<String> issues;

        public ValidationResult(int validZones, int invalidZones, List<String> issues) {
            this.validZones = validZones;
            this.invalidZones = invalidZones;
            this.issues = new ArrayList<>(issues);
        }

        public int getValidZones() { return validZones; }
        public int getInvalidZones() { return invalidZones; }
        public List<String> getIssues() { return new ArrayList<>(issues); }
        public boolean hasIssues() { return !issues.isEmpty(); }

        @Override
        public String toString() {
            return "ValidationResult{" +
                    "valid=" + validZones +
                    ", invalid=" + invalidZones +
                    ", issues=" + issues.size() +
                    '}';
        }
    }
}