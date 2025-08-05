package fr.shop.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Représente une zone de shop définie par des beacons adjacents
 */
public class Zone {

    private final String id;
    private final Set<Location> beaconLocations;
    private final Set<Location> zoneBlocks;
    private Location centerLocation;
    private String worldName;

    public Zone(String id, String worldName) {
        this.id = id;
        this.worldName = worldName;
        this.beaconLocations = new HashSet<>();
        this.zoneBlocks = new HashSet<>();
    }

    // ===============================
    // MÉTHODES DE CONSTRUCTION
    // ===============================

    /**
     * Ajoute un beacon à la zone et calcule les blocs affectés
     */
    public void addBeacon(Location beaconLocation) {
        beaconLocations.add(beaconLocation.clone());
        addBlocksFromBeacon(beaconLocation);
        updateCenter();
    }

    /**
     * Ajoute tous les blocs affectés par un beacon
     * Zone : 1 bloc en dessous jusqu'à 20 blocs au-dessus
     */
    private void addBlocksFromBeacon(Location beaconLocation) {
        int beaconX = beaconLocation.getBlockX();
        int beaconY = beaconLocation.getBlockY();
        int beaconZ = beaconLocation.getBlockZ();

        // De 1 bloc en dessous à 20 blocs au-dessus
        for (int y = beaconY - 1; y <= beaconY + 20; y++) {
            Location blockLoc = new Location(beaconLocation.getWorld(), beaconX, y, beaconZ);
            zoneBlocks.add(blockLoc);
        }
    }

    /**
     * Met à jour le centre approximatif de la zone
     */
    private void updateCenter() {
        if (beaconLocations.isEmpty()) return;

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
    // MÉTHODES DE VÉRIFICATION
    // ===============================

    /**
     * Vérifie si une location est dans cette zone
     */
    public boolean containsLocation(Location location) {
        if (location == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }

        Location blockLoc = new Location(location.getWorld(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return zoneBlocks.contains(blockLoc);
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
     * Vérifie si deux beacons sont adjacents (collés)
     */
    private boolean isAdjacent(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;

        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());

        // Adjacent si la distance est de 1 bloc dans au moins une direction
        // et 0 dans les autres (partage une face)
        return (dx == 1 && dy == 0 && dz == 0) ||
                (dx == 0 && dy == 1 && dz == 0) ||
                (dx == 0 && dy == 0 && dz == 1);
    }

    // ===============================
    // GETTERS
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

    public Set<Location> getZoneBlocks() {
        return new HashSet<>(zoneBlocks);
    }

    public Location getCenterLocation() {
        return centerLocation != null ? centerLocation.clone() : null;
    }

    public int getBeaconCount() {
        return beaconLocations.size();
    }

    public int getBlockCount() {
        return zoneBlocks.size();
    }

    // ===============================
    // SÉRIALISATION
    // ===============================

    /**
     * Sauvegarde la zone dans une section de configuration
     */
    public void saveToConfig(ConfigurationSection section) {
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

        // Sauvegarder les blocs de la zone (compressé)
        List<String> blockStrings = new ArrayList<>();
        for (Location block : zoneBlocks) {
            blockStrings.add(block.getBlockX() + "," + block.getBlockY() + "," + block.getBlockZ());
        }
        section.set("blocks", blockStrings);
    }

    /**
     * Charge une zone depuis une section de configuration
     */
    public static Zone loadFromConfig(String id, ConfigurationSection section) {
        String worldName = section.getString("world");
        if (worldName == null) return null;

        Zone zone = new Zone(id, worldName);

        // Charger le centre
        if (section.contains("center")) {
            zone.centerLocation = new Location(
                    Bukkit.getWorld(worldName),
                    section.getDouble("center.x"),
                    section.getDouble("center.y"),
                    section.getDouble("center.z")
            );
        }

        // Charger les beacons
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

        // Charger les blocs
        List<String> blockStrings = section.getStringList("blocks");
        for (String blockStr : blockStrings) {
            try {
                String[] parts = blockStr.split(",");
                Location blockLoc = new Location(
                        Bukkit.getWorld(worldName),
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
                zone.zoneBlocks.add(blockLoc);
            } catch (Exception e) {
                // Ignorer les blocs malformés
            }
        }

        return zone;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Obtient les limites de la zone (bounding box)
     */
    public BoundingBox getBoundingBox() {
        if (zoneBlocks.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (Location block : zoneBlocks) {
            minX = Math.min(minX, block.getBlockX());
            minY = Math.min(minY, block.getBlockY());
            minZ = Math.min(minZ, block.getBlockZ());
            maxX = Math.max(maxX, block.getBlockX());
            maxY = Math.max(maxY, block.getBlockY());
            maxZ = Math.max(maxZ, block.getBlockZ());
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "Zone{" +
                "id='" + id + '\'' +
                ", worldName='" + worldName + '\'' +
                ", beaconCount=" + beaconLocations.size() +
                ", blockCount=" + zoneBlocks.size() +
                '}';
    }

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
    // CLASSE INTERNE
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