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
 * Gestionnaire des zones de shop basées sur les beacons - Version optimisée
 */
public class ZoneManager {

    private final PlayerShops plugin;
    private final Map<String, Zone> zones;
    private final ZoneScanner scanner;

    private File zonesFile;
    private FileConfiguration zonesConfig;

    // Cache pour optimiser les recherches fréquentes
    private final Map<String, List<Zone>> zonesByWorld;
    private final Map<Location, Zone> locationCache;
    private static final int CACHE_SIZE_LIMIT = 10000;

    public ZoneManager(PlayerShops plugin) {
        this.plugin = plugin;
        this.zones = new ConcurrentHashMap<>();
        this.scanner = new ZoneScanner(plugin, this);
        this.zonesByWorld = new ConcurrentHashMap<>();
        this.locationCache = new ConcurrentHashMap<>();

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
    // CHARGEMENT ET SAUVEGARDE OPTIMISÉS
    // ===============================

    /**
     * Charge toutes les zones depuis le fichier zones.yml (optimisé)
     */
    public void loadZones() {
        long startTime = System.currentTimeMillis();

        zones.clear();
        zonesByWorld.clear();
        locationCache.clear();

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

                        // Indexer par monde pour optimiser les recherches
                        zonesByWorld.computeIfAbsent(zone.getWorldName(), k -> new ArrayList<>()).add(zone);

                        loadedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de la zone " + zoneId + ": " + e.getMessage());
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("Chargé " + loadedCount + " zones en " + duration + "ms");
    }

    /**
     * Sauvegarde toutes les zones (optimisé - seulement les beacons)
     */
    public void saveZones() {
        long startTime = System.currentTimeMillis();

        ConfigurationSection zonesSection = zonesConfig.createSection("zones");

        for (Map.Entry<String, Zone> entry : zones.entrySet()) {
            ConfigurationSection zoneSection = zonesSection.createSection(entry.getKey());
            entry.getValue().saveToConfig(zoneSection);
        }

        saveZonesConfig();

        long duration = System.currentTimeMillis() - startTime;
        plugin.getLogger().info("Sauvegardé " + zones.size() + " zones en " + duration + "ms");
    }

    private void saveZonesConfig() {
        try {
            zonesConfig.save(zonesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder zones.yml: " + e.getMessage());
        }
    }

    // ===============================
    // GESTION DES ZONES OPTIMISÉE
    // ===============================

    /**
     * Ajoute une zone et met à jour les index
     */
    public void addZone(Zone zone) {
        zones.put(zone.getId(), zone);

        // Mettre à jour l'index par monde
        zonesByWorld.computeIfAbsent(zone.getWorldName(), k -> new ArrayList<>()).add(zone);

        // Invalider le cache des locations pour cette zone
        invalidateLocationCacheForZone(zone);
    }

    /**
     * Supprime une zone et nettoie les index
     */
    public void removeZone(String zoneId) {
        Zone removedZone = zones.remove(zoneId);
        if (removedZone != null) {
            // Nettoyer l'index par monde
            List<Zone> worldZones = zonesByWorld.get(removedZone.getWorldName());
            if (worldZones != null) {
                worldZones.remove(removedZone);
                if (worldZones.isEmpty()) {
                    zonesByWorld.remove(removedZone.getWorldName());
                }
            }

            // Invalider le cache
            invalidateLocationCacheForZone(removedZone);
        }
    }

    /**
     * Nettoie toutes les zones d'un monde
     */
    public void clearZonesForWorld(String worldName) {
        // Supprimer du cache principal
        zones.entrySet().removeIf(entry -> entry.getValue().getWorldName().equals(worldName));

        // Nettoyer l'index par monde
        zonesByWorld.remove(worldName);

        // Nettoyer le cache de locations
        locationCache.entrySet().removeIf(entry ->
                entry.getKey().getWorld().getName().equals(worldName));
    }

    /**
     * Trouve la zone contenant une location (optimisé avec cache et index)
     */
    public Zone getZoneAtLocation(Location location) {
        if (location == null) return null;

        // Vérifier d'abord le cache
        Zone cachedZone = locationCache.get(location);
        if (cachedZone != null) {
            return cachedZone;
        }

        // Rechercher seulement dans les zones du bon monde (optimisation majeure)
        List<Zone> worldZones = zonesByWorld.get(location.getWorld().getName());
        if (worldZones == null || worldZones.isEmpty()) {
            return null;
        }

        for (Zone zone : worldZones) {
            if (zone.containsLocation(location)) {
                // Ajouter au cache (avec limite de taille)
                if (locationCache.size() < CACHE_SIZE_LIMIT) {
                    locationCache.put(location, zone);
                }
                return zone;
            }
        }

        return null;
    }

    /**
     * Trouve toutes les zones d'un monde (optimisé avec index)
     */
    public List<Zone> getZonesInWorld(String worldName) {
        List<Zone> worldZones = zonesByWorld.get(worldName);
        if (worldZones == null) {
            return new ArrayList<>();
        }

        // Retourner une copie triée
        return worldZones.stream()
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
     * Vérifie si une location est dans une zone (optimisé)
     */
    public boolean isInAnyZone(Location location) {
        return getZoneAtLocation(location) != null;
    }

    // ===============================
    // GESTION DU CACHE
    // ===============================

    /**
     * Invalide le cache de locations pour une zone spécifique
     */
    private void invalidateLocationCacheForZone(Zone zone) {
        locationCache.entrySet().removeIf(entry -> entry.getValue().equals(zone));
    }

    /**
     * Nettoie complètement le cache de locations
     */
    public void clearLocationCache() {
        locationCache.clear();
        plugin.getLogger().info("Cache de locations vidé");
    }

    /**
     * Obtient des statistiques sur le cache
     */
    public CacheStats getCacheStats() {
        return new CacheStats(locationCache.size(), CACHE_SIZE_LIMIT);
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
    // STATISTIQUES ET INFORMATIONS OPTIMISÉES
    // ===============================

    /**
     * Obtient des statistiques sur les zones (optimisé)
     */
    public ZoneStats getStats() {
        int totalZones = zones.size();
        int totalBeacons = 0;
        long totalBlocks = 0; // long car peut être très grand
        Map<String, Integer> worldCounts = new HashMap<>();

        for (Zone zone : zones.values()) {
            totalBeacons += zone.getBeaconCount();
            totalBlocks += zone.getBlockCount(); // Calculé, pas stocké
            worldCounts.merge(zone.getWorldName(), 1, Integer::sum);
        }

        return new ZoneStats(totalZones, totalBeacons, (int) Math.min(totalBlocks, Integer.MAX_VALUE), worldCounts);
    }

    /**
     * Trouve les zones proches d'une location (optimisé)
     */
    public List<Zone> getNearbyZones(Location location, double radius) {
        List<Zone> nearbyZones = new ArrayList<>();

        // Optimisation : chercher seulement dans le bon monde
        List<Zone> worldZones = zonesByWorld.get(location.getWorld().getName());
        if (worldZones == null) {
            return nearbyZones;
        }

        for (Zone zone : worldZones) {
            Location center = zone.getCenterLocation();
            if (center != null && center.distance(location) <= radius) {
                nearbyZones.add(zone);
            }
        }

        // Trier par distance
        nearbyZones.sort(Comparator.comparing(zone -> {
            Location center = zone.getCenterLocation();
            return center != null ? center.distance(location) : Double.MAX_VALUE;
        }));

        return nearbyZones;
    }

    /**
     * Valide l'intégrité des zones (optimisé)
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
    // MÉTHODES DE MAINTENANCE
    // ===============================

    /**
     * Optimise les structures de données (à appeler périodiquement)
     */
    public void optimize() {
        // Nettoyer le cache si il devient trop grand
        if (locationCache.size() > CACHE_SIZE_LIMIT * 0.8) {
            clearLocationCache();
        }

        // Recompacter les index
        for (List<Zone> worldZones : zonesByWorld.values()) {
            ((ArrayList<Zone>) worldZones).trimToSize();
        }

        plugin.getLogger().info("Optimisation des zones terminée");
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

    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;

        public CacheStats(int currentSize, int maxSize) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
        }

        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public double getUsagePercentage() { return (double) currentSize / maxSize * 100; }

        @Override
        public String toString() {
            return String.format("CacheStats{%d/%d (%.1f%%)}",
                    currentSize, maxSize, getUsagePercentage());
        }
    }
}