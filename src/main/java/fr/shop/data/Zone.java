package fr.shop.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Représente une zone de shop définie par des beacons adjacents
 * Version optimisée : ne stocke que les beacons, calcule les blocs à la demande
 */
public class Zone {

    private final String id;
    private final Set<Location> beaconLocations;
    private String worldName;

    // Cache pour éviter les recalculs
    private Location centerLocation;
    private BoundingBox cachedBoundingBox;
    private Set<Location> cachedZoneBlocks;
    private boolean cacheValid = false;

    private Location teleportLocation;
    private float teleportYaw;
    private float teleportPitch;
    private boolean hasTeleportLocation = false;

    // Constantes pour la zone (optimisation)
    private static final int ZONE_BELOW = 1;  // 1 bloc en dessous
    private static final int ZONE_ABOVE = 20; // 20 blocs au-dessus

    public Zone(String id, String worldName) {
        this.id = id;
        this.worldName = worldName;
        this.beaconLocations = new HashSet<>();
    }

    // ===============================
    // MÉTHODES DE CONSTRUCTION
    // ===============================

    /**
     * Ajoute un beacon à la zone
     */
    public void addBeacon(Location beaconLocation) {
        beaconLocations.add(beaconLocation.clone());
        invalidateCache();
        updateCenter();
    }

    /**
     * Supprime un beacon de la zone
     */
    public void removeBeacon(Location beaconLocation) {
        beaconLocations.remove(beaconLocation);
        invalidateCache();
        updateCenter();
    }

    /**
     * Invalide le cache quand la zone change
     */
    private void invalidateCache() {
        cacheValid = false;
        cachedZoneBlocks = null;
        cachedBoundingBox = null;
    }

    /**
     * Met à jour le centre de la zone
     */
    private void updateCenter() {
        if (beaconLocations.isEmpty()) {
            centerLocation = null;
            return;
        }

        double totalX = 0, totalY = 0, totalZ = 0;
        for (Location beacon : beaconLocations) {
            totalX += beacon.getX();
            totalY += beacon.getY();
            totalZ += beacon.getZ();
        }

        int count = beaconLocations.size();
        this.centerLocation = new Location(
                Bukkit.getWorld(worldName),
                totalX / count,
                totalY / count,
                totalZ / count
        );
    }

    // ===============================
    // MÉTHODES DE CALCUL OPTIMISÉES
    // ===============================

    /**
     * Calcule tous les blocs de la zone (avec cache)
     */
    private Set<Location> calculateZoneBlocks() {
        if (cacheValid && cachedZoneBlocks != null) {
            return cachedZoneBlocks;
        }

        Set<Location> blocks = new HashSet<>();

        for (Location beacon : beaconLocations) {
            // Ajouter tous les blocs de cette colonne de beacon
            int beaconX = beacon.getBlockX();
            int beaconY = beacon.getBlockY();
            int beaconZ = beacon.getBlockZ();

            for (int y = beaconY - ZONE_BELOW; y <= beaconY + ZONE_ABOVE; y++) {
                blocks.add(new Location(beacon.getWorld(), beaconX, y, beaconZ));
            }
        }

        cachedZoneBlocks = blocks;
        cacheValid = true;
        return blocks;
    }

    /**
     * Vérifie si une location est dans cette zone (optimisé avec bounding box)
     */
    public boolean containsLocation(Location location) {
        if (location == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }

        // Optimisation 1: Vérifier d'abord la bounding box
        BoundingBox bounds = getBoundingBox();
        if (bounds != null && !bounds.contains(location)) {
            return false;
        }

        // Optimisation 2: Vérifier directement si c'est dans une colonne de beacon
        Location blockLoc = new Location(location.getWorld(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

        return isInBeaconColumn(blockLoc);
    }

    /**
     * Vérifie rapidement si une location est dans une colonne de beacon
     */
    private boolean isInBeaconColumn(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        for (Location beacon : beaconLocations) {
            if (beacon.getBlockX() == x && beacon.getBlockZ() == z) {
                int beaconY = beacon.getBlockY();
                return y >= (beaconY - ZONE_BELOW) && y <= (beaconY + ZONE_ABOVE);
            }
        }

        return false;
    }

    /**
     * Vérifie si un beacon peut être ajouté à cette zone (adjacent à un beacon existant)
     */
    public boolean isAdjacentToZone(Location beaconLocation) {
        if (beaconLocations.isEmpty()) return true;

        for (Location existingBeacon : beaconLocations) {
            if (isAdjacent(beaconLocation, existingBeacon)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vérifie si deux beacons sont adjacents (partageant une face)
     */
    private boolean isAdjacent(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;

        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());

        // Adjacent si distance de 1 bloc dans exactement une direction
        return (dx == 1 && dy == 0 && dz == 0) ||
                (dx == 0 && dy == 1 && dz == 0) ||
                (dx == 0 && dy == 0 && dz == 1);
    }

    // ===============================
    // GETTERS OPTIMISÉS
    // ===============================

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public Set<Location> getBeaconLocations() {
        return new HashSet<>(beaconLocations);
    }

    /**
     * Retourne tous les blocs de la zone (calculés à la demande)
     */
    public Set<Location> getZoneBlocks() {
        return new HashSet<>(calculateZoneBlocks());
    }

    public Location getCenterLocation() {
        return centerLocation != null ? centerLocation.clone() : null;
    }

    public int getBeaconCount() {
        return beaconLocations.size();
    }

    /**
     * Calcule le nombre de blocs total (sans créer la Set complète)
     */
    public int getBlockCount() {
        return beaconLocations.size() * (ZONE_ABOVE + ZONE_BELOW + 1);
    }

    // ===============================
    // BOUNDING BOX OPTIMISÉE
    // ===============================

    /**
     * Obtient les limites de la zone (avec cache)
     */
    public BoundingBox getBoundingBox() {
        if (cacheValid && cachedBoundingBox != null) {
            return cachedBoundingBox;
        }

        if (beaconLocations.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Location beacon : beaconLocations) {
            int x = beacon.getBlockX();
            int y = beacon.getBlockY();
            int z = beacon.getBlockZ();

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);

            // Y inclut la zone d'extension
            minY = Math.min(minY, y - ZONE_BELOW);
            maxY = Math.max(maxY, y + ZONE_ABOVE);
        }

        cachedBoundingBox = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        return cachedBoundingBox;
    }

    // ===============================
    // SÉRIALISATION OPTIMISÉE
    // ===============================

    /**
     * Sauvegarde SEULEMENT les beacons (pas tous les blocs)
     */
    public void saveToConfig(ConfigurationSection section) {
        // Sauvegarder les données existantes
        section.set("world", worldName);
        section.set("beaconCount", beaconLocations.size());

        if (centerLocation != null) {
            section.set("center.x", centerLocation.getX());
            section.set("center.y", centerLocation.getY());
            section.set("center.z", centerLocation.getZ());
        }

        // Sauvegarder les beacons
        List<String> beaconStrings = new ArrayList<>();
        for (Location beacon : beaconLocations) {
            beaconStrings.add(beacon.getBlockX() + "," + beacon.getBlockY() + "," + beacon.getBlockZ());
        }
        section.set("beacons", beaconStrings);

        // Nouveau: Sauvegarder la téléportation
        if (hasTeleportLocation && teleportLocation != null) {
            section.set("teleport.x", teleportLocation.getX());
            section.set("teleport.y", teleportLocation.getY());
            section.set("teleport.z", teleportLocation.getZ());
            section.set("teleport.yaw", teleportYaw);
            section.set("teleport.pitch", teleportPitch);
            section.set("teleport.enabled", true);
        } else {
            section.set("teleport.enabled", false);
        }
    }

    /**
     * Chargement avec les données de téléportation
     */
    public static Zone loadFromConfig(String id, ConfigurationSection section) {
        String worldName = section.getString("world");
        if (worldName == null) return null;

        Zone zone = new Zone(id, worldName);

        // Charger les données existantes (centre, beacons)
        if (section.contains("center")) {
            zone.centerLocation = new Location(
                    Bukkit.getWorld(worldName),
                    section.getDouble("center.x"),
                    section.getDouble("center.y"),
                    section.getDouble("center.z")
            );
        }

        List<String> beaconStrings = section.getStringList("beacons");
        for (String beaconStr : beaconStrings) {
            try {
                String[] parts = beaconStr.split(",");
                Location beaconLoc = new Location(
                        Bukkit.getWorld(worldName),
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
                zone.beaconLocations.add(beaconLoc);
            } catch (Exception e) {
                // Ignorer les beacons malformés
            }
        }

        // Nouveau: Charger la téléportation
        if (section.getBoolean("teleport.enabled", false)) {
            try {
                Location teleportLoc = new Location(
                        Bukkit.getWorld(worldName),
                        section.getDouble("teleport.x"),
                        section.getDouble("teleport.y"),
                        section.getDouble("teleport.z")
                );
                float yaw = (float) section.getDouble("teleport.yaw", 0.0);
                float pitch = (float) section.getDouble("teleport.pitch", 0.0);

                zone.setTeleportLocation(teleportLoc, yaw, pitch);
            } catch (Exception e) {
                // Ignorer les erreurs de téléportation
                zone.hasTeleportLocation = false;
            }
        }

        zone.updateCenter();
        return zone;
    }

    // Mise à jour toString pour inclure la téléportation
    @Override
    public String toString() {
        return "Zone{" +
                "id='" + id + '\'' +
                ", worldName='" + worldName + '\'' +
                ", beaconCount=" + beaconLocations.size() +
                ", blockCount=" + getBlockCount() +
                ", hasTeleport=" + hasTeleportLocation() +
                '}';
    }

    /**
     * Définit la location de téléportation avec yaw et pitch
     */
    public void setTeleportLocation(Location location, float yaw, float pitch) {
        this.teleportLocation = location != null ? location.clone() : null;
        this.teleportYaw = yaw;
        this.teleportPitch = pitch;
        this.hasTeleportLocation = (location != null);
        invalidateCache(); // Invalider le cache si nécessaire
    }

    /**
     * Obtient la location de téléportation avec orientation
     */
    public Location getTeleportLocation() {
        if (teleportLocation == null) return null;

        Location loc = teleportLocation.clone();
        loc.setYaw(teleportYaw);
        loc.setPitch(teleportPitch);
        return loc;
    }

    /**
     * Obtient la location brute de téléportation (sans orientation)
     */
    public Location getRawTeleportLocation() {
        return teleportLocation != null ? teleportLocation.clone() : null;
    }

    /**
     * Vérifie si cette zone a une téléportation définie
     */
    public boolean hasTeleportLocation() {
        return hasTeleportLocation && teleportLocation != null;
    }

    /**
     * Obtient le yaw de téléportation
     */
    public float getTeleportYaw() {
        return teleportYaw;
    }

    /**
     * Obtient le pitch de téléportation
     */
    public float getTeleportPitch() {
        return teleportPitch;
    }

    /**
     * Supprime la téléportation
     */
    public void removeTeleportLocation() {
        this.teleportLocation = null;
        this.teleportYaw = 0.0f;
        this.teleportPitch = 0.0f;
        this.hasTeleportLocation = false;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Zone zone = (Zone) o;
        return Objects.equals(id, zone.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ===============================
    // CLASSE INTERNE - BOUNDING BOX
    // ===============================

    public static class BoundingBox {
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;

        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean contains(Location location) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
        }

        @Override
        public String toString() {
            return String.format("BoundingBox{min=(%d,%d,%d), max=(%d,%d,%d)}",
                    minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}