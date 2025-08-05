package fr.shop.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Scanner pour détecter les zones de shop basées sur les beacons
 */
public class ZoneScanner {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;
    private boolean isScanning = false;

    public ZoneScanner(PlayerShops plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    // ===============================
    // MÉTHODE PRINCIPALE DE SCAN
    // ===============================

    public CompletableFuture<ScanResult> scanWorld(World world, Player initiator) {
        if (isScanning) {
            sendMessage(initiator, "§c§lSHOP §8» §cUn scan est déjà en cours!");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<ScanResult> future = new CompletableFuture<>();
        isScanning = true;

        sendMessage(initiator, "§a§lSHOP §8» §aDébut du scan des beacons dans le monde §e" + world.getName() + "§a...");
        plugin.getLogger().info("Début du scan des beacons dans le monde " + world.getName());

        long startTime = System.currentTimeMillis();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Phase 1: Chercher tous les beacons ET bamboo_mosaic
                    sendMessage(initiator, "§7§lSHOP §8» §7Phase 1: Recherche des beacons et téléportations...");
                    ScanResults scanResults = findAllBeacons(world); // Utiliser la nouvelle méthode

                    sendMessage(initiator, "§7§lSHOP §8» §7Trouvé §e" + scanResults.beacons.size() + " §7beacons et §e" + scanResults.bambooMosaics.size() + " §7téléportations.");

                    // Phase 2: Créer les zones avec téléportations
                    sendMessage(initiator, "§7§lSHOP §8» §7Phase 2: Création des zones avec téléportations...");
                    List<Zone> zones = createZonesFromBeacons(scanResults, world.getName());

                    // Phase 3: Sauvegarder les zones
                    sendMessage(initiator, "§7§lSHOP §8» §7Phase 3: Sauvegarde des zones...");
                    zoneManager.clearZonesForWorld(world.getName());
                    zones.forEach(zoneManager::addZone);
                    zoneManager.saveZones();

                    long zonesWithTeleport = zones.stream().mapToLong(z -> z.hasTeleportLocation() ? 1 : 0).sum();

                    long duration = System.currentTimeMillis() - startTime;
                    ScanResult result = new ScanResult(world.getName(), scanResults.beacons.size(), zones.size(), duration);

                    // Envoyer le message final depuis le thread principal
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            sendMessage(initiator, "§a§lSHOP §8» §aScan terminé!");
                            sendMessage(initiator, "§7§lSHOP §8» §7Beacons trouvés: §e" + scanResults.beacons.size());
                            sendMessage(initiator, "§7§lSHOP §8» §7Téléportations trouvées: §e" + scanResults.bambooMosaics.size());
                            sendMessage(initiator, "§7§lSHOP §8» §7Zones créées: §e" + zones.size());
                            sendMessage(initiator, "§7§lSHOP §8» §7Zones avec téléportation: §e" + zonesWithTeleport);
                            sendMessage(initiator, "§7§lSHOP §8» §7Durée: §e" + (duration / 1000.0) + "s");
                        }
                    }.runTask(plugin);

                    plugin.getLogger().info("Scan terminé - Beacons: " + result.getBeaconsFound() +
                            ", Téléportations: " + scanResults.bambooMosaics.size() +
                            ", Zones: " + result.getZonesCreated() +
                            ", Zones avec téléportation: " + zonesWithTeleport +
                            ", Durée: " + (duration / 1000.0) + "s");

                    isScanning = false;
                    future.complete(result);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur critique durant le scan asynchrone:", e);
                    isScanning = false;
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    // ===============================
    // RECHERCHE DES BEACONS (AVEC DEBUG)
    // ===============================
    private ScanResults findAllBeacons(org.bukkit.World bukkitWorld) {
        plugin.getLogger().info("[DEBUG] Début de la recherche de beacons et bamboo_mosaic avec FAWE...");

        ScanResults results = new ScanResults();
        com.sk89q.worldedit.world.World faweWorld = BukkitAdapter.adapt(bukkitWorld);

        int scanRadius = 300;
        BlockVector3 min = BlockVector3.at(-scanRadius, faweWorld.getMinY(), -scanRadius);
        BlockVector3 max = BlockVector3.at(scanRadius, faweWorld.getMaxY(), scanRadius);

        plugin.getLogger().info("[DEBUG] Scan de la région de " + min.toString() + " à " + max.toString());

        long volume = (long)(max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        plugin.getLogger().info("[DEBUG] Volume total de la région à scanner : " + String.format("%,d", volume) + " blocs.");

        CuboidRegion region = new CuboidRegion(faweWorld, min, max);

        long blocksScanned = 0;
        long lastLogTime = System.currentTimeMillis();
        int beaconsFoundCount = 0;
        int bambooMosaicFoundCount = 0;

        for (BlockVector3 point : region) {
            blocksScanned++;

            if (faweWorld.getBlock(point).getBlockType() == BlockTypes.BEACON) {
                results.beacons.add(BukkitAdapter.adapt(bukkitWorld, point));
                beaconsFoundCount++;
                plugin.getLogger().info("[DEBUG] Beacon trouvé à : " + point.toString());
            } else if (faweWorld.getBlock(point).getBlockType() == BlockTypes.BAMBOO_MOSAIC) {
                results.bambooMosaics.add(BukkitAdapter.adapt(bukkitWorld, point));
                bambooMosaicFoundCount++;
                plugin.getLogger().info("[DEBUG] Bamboo Mosaic trouvé à : " + point.toString());
            }

            if (System.currentTimeMillis() - lastLogTime > 5000) {
                plugin.getLogger().info("[DEBUG] Progression du scan : " + String.format("%,d", blocksScanned) + " / " + String.format("%,d", volume) + " blocs...");
                lastLogTime = System.currentTimeMillis();
            }
        }

        plugin.getLogger().info("[DEBUG] Fin de la recherche FAWE. " + beaconsFoundCount + " beacons et " + bambooMosaicFoundCount + " bamboo_mosaic trouvés sur " + String.format("%,d", blocksScanned) + " blocs scannés.");
        return results;
    }

    // Nouvelle classe pour les résultats de scan
    private static class ScanResults {
        final Set<Location> beacons = new HashSet<>();
        final Set<Location> bambooMosaics = new HashSet<>();
    }


    // ===============================
    // CRÉATION DES ZONES (inchangé)
    // ===============================
    private List<Zone> createZonesFromBeacons(ScanResults scanResults, String worldName) {
        List<Zone> zones = new ArrayList<>();
        Set<Location> processedBeacons = new HashSet<>();
        int zoneCounter = 1;

        for (Location beacon : scanResults.beacons) {
            if (processedBeacons.contains(beacon)) continue;

            String zoneId = "zone_" + worldName + "_" + zoneCounter;
            Zone zone = new Zone(zoneId, worldName);

            Set<Location> zoneBeacons = findConnectedBeacons(beacon, scanResults.beacons);
            for (Location zoneBeacon : zoneBeacons) {
                zone.addBeacon(zoneBeacon);
                processedBeacons.add(zoneBeacon);
            }

            // Trouver le bamboo_mosaic le plus proche pour cette zone
            Location teleportLocation = findClosestBambooMosaic(zone, scanResults.bambooMosaics);
            if (teleportLocation != null) {
                Location centeredTeleportLocation = teleportLocation.clone().add(0.5, 0, 0.5);
                float yaw = calculateYawTowardsZone(centeredTeleportLocation, zone.getCenterLocation());
                zone.setTeleportLocation(centeredTeleportLocation, yaw, 0.0f); // pitch = 0
            }

            zones.add(zone);
            zoneCounter++;
        }

        return zones;
    }

    private Location findClosestBambooMosaic(Zone zone, Set<Location> bambooMosaics) {
        Location zoneCenter = zone.getCenterLocation();
        if (zoneCenter == null || bambooMosaics.isEmpty()) return null;

        Location closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Location bamboo : bambooMosaics) {
            double distance = zoneCenter.distance(bamboo);
            if (distance < minDistance) {
                minDistance = distance;
                closest = bamboo;
            }
        }

        return closest;
    }

    private float calculateYawTowardsZone(Location bambooLocation, Location zoneCenter) {
        if (zoneCenter == null) return 0.0f;

        double dx = zoneCenter.getX() - bambooLocation.getX();
        double dz = zoneCenter.getZ() - bambooLocation.getZ();

        // Calculer l'angle en radians puis convertir en degrés
        double angle = Math.atan2(-dx, dz);
        float yaw = (float) Math.toDegrees(angle);


        float roundedYaw = Math.round(yaw / 90.0f) * 90.0f;

        if (roundedYaw == -180) {
            roundedYaw = 180;
        }

        return roundedYaw;
    }

    private Set<Location> findConnectedBeacons(Location startBeacon, Set<Location> allBeacons) {
        Set<Location> connected = new HashSet<>();
        Queue<Location> toProcess = new LinkedList<>();
        toProcess.add(startBeacon);
        connected.add(startBeacon);
        while (!toProcess.isEmpty()) {
            Location current = toProcess.poll();
            for (Location beacon : allBeacons) {
                if (!connected.contains(beacon) && isAdjacent(current, beacon)) {
                    connected.add(beacon);
                    toProcess.add(beacon);
                }
            }
        }
        return connected;
    }

    private boolean isAdjacent(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;
        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());
        return (dx == 1 && dy == 0 && dz == 0) || (dx == 0 && dy == 1 && dz == 0) || (dx == 0 && dy == 0 && dz == 1);
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    private void sendMessage(Player player, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    // ===============================
    // CLASSE DE RÉSULTAT
    // ===============================

    public static class ScanResult {
        private final String worldName;
        private final int beaconsFound;
        private final int zonesCreated;
        private final long duration;

        public ScanResult(String worldName, int beaconsFound, int zonesCreated, long duration) {
            this.worldName = worldName;
            this.beaconsFound = beaconsFound;
            this.zonesCreated = zonesCreated;
            this.duration = duration;
        }

        public String getWorldName() { return worldName; }
        public int getBeaconsFound() { return beaconsFound; }
        public int getZonesCreated() { return zonesCreated; }
        public long getDuration() { return duration; }

        @Override
        public String toString() {
            return "ScanResult{" +
                    "world='" + worldName + '\'' +
                    ", beacons=" + beaconsFound +
                    ", zones=" + zonesCreated +
                    ", duration=" + duration + "ms" +
                    '}';
        }
    }
}