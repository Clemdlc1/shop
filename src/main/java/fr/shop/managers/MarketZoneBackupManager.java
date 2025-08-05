package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Gestionnaire de backup ultra-optimisé spécialement pour les market zones
 * Compression maximale car seulement 2 types de blocs et 3 couches avec des blocs
 */
public class MarketZoneBackupManager {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;

    private File marketBackupsFile;
    private FileConfiguration marketBackupsConfig;

    // Constantes pour l'optimisation market zone
    private static final int MARKET_LAYERS = 3; // Seulement 3 couches avec des blocs
    private static final Set<Material> EXPECTED_MATERIALS = Set.of(
            Material.WHITE_CONCRETE, Material.GRAY_CONCRETE
    ); // Seulement 2 types de blocs attendus

    public MarketZoneBackupManager(PlayerShops plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;

        initializeMarketBackupFile();
    }

    // ===============================
    // INITIALISATION
    // ===============================

    private void initializeMarketBackupFile() {
        this.marketBackupsFile = new File(plugin.getDataFolder(), "market-backups.yml");

        if (!marketBackupsFile.exists()) {
            try {
                marketBackupsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer market-backups.yml: " + e.getMessage());
            }
        }

        this.marketBackupsConfig = YamlConfiguration.loadConfiguration(marketBackupsFile);
    }

    // ===============================
    // BACKUP ULTRA-OPTIMISÉ MARKET ZONE
    // ===============================

    /**
     * Sauvegarde ultra-compressée d'une market zone
     */
    public CompletableFuture<MarketBackupResult> backupMarketZone(String zoneId, Player initiator) {
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            return CompletableFuture.completedFuture(new MarketBackupResult(zoneId, false, "Zone introuvable", 0, 0, "0%"));
        }

        CompletableFuture<MarketBackupResult> future = new CompletableFuture<>();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la sauvegarde market zone §e" + zoneId + "§a...");
        }

        // Étape 1: Collecter les données des BLOCS sur un thread asynchrone (partie la plus lente)
        new BukkitRunnable() {
            @Override
            public void run() { // S'exécute en ASYNC
                try {
                    MarketZoneData marketData = collectMarketZoneData(zone);

                    // Étape 2: Planifier le reste des opérations sur le thread principal
                    new BukkitRunnable() {
                        @Override
                        public void run() { // S'exécute en SYNC (thread principal)
                            try {
                                World world = Bukkit.getWorld(zone.getWorldName());
                                if (world == null) {
                                    future.complete(new MarketBackupResult(zoneId, false, "Monde introuvable pour la collecte des entités", 0, 0, "0%"));
                                    return;
                                }
                                // Étape 2b: Traiter la sauvegarde (suppression d'entités, sauvegarde YAML)
                                MarketBackupResult result = processMarketBackup(zone, marketData);

                                // Notifier le joueur et compléter le future
                                if (initiator != null) {
                                    if (result.isSuccess()) {
                                        initiator.sendMessage("§a§lSHOP §8» §aBackup market zone terminé!");
                                        initiator.sendMessage("§7§lSHOP §8» §7Colonnes sauvegardées: §e" + result.getColumnsSaved());
                                        initiator.sendMessage("§7§lSHOP §8» §7Compression: §a" + result.getCompressionRatio());
                                        initiator.sendMessage("§7§lSHOP §8» §7Entités supprimées: §e" + result.getEntitiesRemoved());
                                    } else {
                                        initiator.sendMessage("§c§lSHOP §8» §cErreur backup: " + result.getErrorMessage());
                                    }
                                }
                                future.complete(result);

                            } catch (Exception e) {
                                plugin.getLogger().log(Level.SEVERE, "Erreur lors de la phase synchrone du backup pour " + zoneId, e);
                                future.complete(new MarketBackupResult(zoneId, false, e.getMessage(), 0, 0, "0%"));
                            }
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    // Si une erreur se produit pendant la collecte de blocs
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors de la phase asynchrone du backup pour " + zoneId, e);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            future.complete(new MarketBackupResult(zoneId, false, "Asynchronous " + e.getMessage(), 0, 0, "0%"));
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Collecte les données de market zone de manière ultra-optimisée
     */
    // Dans fr.shop.managers.MarketZoneBackupManager

    /**
     * Collecte les données de market zone (UNIQUEMENT LES BLOCS) de manière ultra-optimisée.
     * Cette méthode est maintenant sûre pour une exécution asynchrone.
     */
    private MarketZoneData collectMarketZoneData(Zone zone) throws Exception {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            throw new Exception("Monde introuvable");
        }

        MarketZoneData data = new MarketZoneData(zone.getId());

        // Analyser chaque colonne de beacon
        for (Location beaconLoc : zone.getBeaconLocations()) {
            int x = beaconLoc.getBlockX();
            int z = beaconLoc.getBlockZ();
            int beaconY = beaconLoc.getBlockY();

            MarketColumn column = new MarketColumn(x, z);

            // Scanner les 3 couches spécifiques aux market zones
            for (int layer = 0; layer < MARKET_LAYERS; layer++) {
                int y = calculateLayerY(beaconY, layer);
                Location blockLoc = new Location(world, x, y, z);
                Block block = blockLoc.getBlock();

                if (block.getType() != Material.AIR && block.getType() != Material.BEACON) {
                    column.setLayer(layer, block.getType());
                    data.totalBlocks++;
                }
            }

            // Seulement sauvegarder les colonnes non-vides
            if (column.hasBlocks()) {
                data.columns.put(x + "," + z, column);
            }
        }
        return data;
    }

    /**
     * Calcule la Y d'une couche spécifique
     */
    private int calculateLayerY(int beaconY, int layer) {
        return switch (layer) {
            case 0 -> beaconY - 1;  // Couche sous le beacon
            case 1 -> beaconY + 1;  // Couche au-dessus du beacon
            case 2 -> beaconY + 2;  // Couche encore au-dessus
            default -> beaconY;
        };
    }

    /**
     * Collecte les entités dans la zone
     */
    private void collectEntitiesInZone(Zone zone, MarketZoneData data, World world) {
        Zone.BoundingBox bounds = zone.getBoundingBox();
        if (bounds != null) {
            Collection<Entity> entities = world.getNearbyEntities(
                    new Location(world, (bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0, (bounds.minZ + bounds.maxZ) / 2.0),
                    (bounds.maxX - bounds.minX) / 2.0 + 1,
                    (bounds.maxY - bounds.minY) / 2.0 + 1,
                    (bounds.maxZ - bounds.minZ) / 2.0 + 1
            );

            for (Entity entity : entities) {
                if (!(entity instanceof Player) && zone.containsLocation(entity.getLocation())) {
                    data.entitiesToRemove.add(entity);
                }
            }
        }
    }

    /**
     * Traite le backup de market zone
     */
    private MarketBackupResult processMarketBackup(Zone zone, MarketZoneData marketData) {
        try {
            // Supprimer les entités
            int entitiesRemoved = 0;
            if (!marketData.entitiesToRemove.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Entity entity : marketData.entitiesToRemove) {
                            if (entity != null && !entity.isDead()) {
                                entity.remove();
                            }
                        }
                    }
                }.runTask(plugin);

                entitiesRemoved = marketData.entitiesToRemove.size();
            }

            // Sauvegarder de manière ultra-compressée
            saveMarketBackupToConfig(marketData);

            int originalBlocks = marketData.totalBlocks;
            int compressedEntries = marketData.columns.size() * MARKET_LAYERS;
            double compressionRatio = 0.0;
            if (compressedEntries > 0) {
                // La compression est le ratio des emplacements vides par rapport au total des emplacements
                compressionRatio = (1.0 - (double) originalBlocks / compressedEntries) * 100;
            }
            String compressionStr = String.format("%.1f%%", compressionRatio);

            return new MarketBackupResult(zone.getId(), true, null, marketData.columns.size(), entitiesRemoved, compressionStr);
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du backup market zone " + zone.getId() + ": " + e.getMessage());
            return new MarketBackupResult(zone.getId(), false, e.getMessage(), 0, 0, "0%");
        }
    }

    // ===============================
    // SAUVEGARDE ULTRA-COMPRESSÉE
    // ===============================

    /**
     * Sauvegarde ultra-compressée spécialement pour market zones
     */
    private void saveMarketBackupToConfig(MarketZoneData data) {
        marketBackupsConfig.set("market_backups." + data.zoneId, null);

        ConfigurationSection zoneSection = marketBackupsConfig.createSection("market_backups." + data.zoneId);
        zoneSection.set("timestamp", System.currentTimeMillis());
        zoneSection.set("format", "market_compressed_v1");
        zoneSection.set("column_count", data.columns.size());
        zoneSection.set("total_blocks", data.totalBlocks);

        // Format ultra-compact: "x,z:L0,L1,L2" où Lx = matériau ou 0 (air)
        List<String> compactColumns = new ArrayList<>();
        for (Map.Entry<String, MarketColumn> entry : data.columns.entrySet()) {
            String coords = entry.getKey();
            MarketColumn column = entry.getValue();

            StringBuilder columnStr = new StringBuilder(coords).append(":");

            for (int layer = 0; layer < MARKET_LAYERS; layer++) {
                if (layer > 0) columnStr.append(",");

                Material material = column.getLayer(layer);
                if (material == null || material == Material.AIR) {
                    columnStr.append("0"); // 0 = air
                } else if (material == Material.WHITE_CONCRETE) {
                    columnStr.append("C"); // C = concrete
                } else if (material == Material.BEACON) {
                    columnStr.append("B"); // B = beacon
                } else {
                    // Matériau inattendu - utiliser code complet
                    columnStr.append(material.name());
                }
            }

            compactColumns.add(columnStr.toString());
        }

        zoneSection.set("columns", compactColumns);

        // Statistiques
        zoneSection.set("stats.layers", MARKET_LAYERS);
        zoneSection.set("stats.expected_materials", EXPECTED_MATERIALS.size());

        saveMarketBackupsConfig();
    }

    // ===============================
    // RESTAURATION ULTRA-OPTIMISÉE
    // ===============================

    /**
     * Restaure une market zone depuis le format ultra-compressé
     */
    public CompletableFuture<MarketRestoreResult> restoreMarketZone(String zoneId, Player initiator) {
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            CompletableFuture<MarketRestoreResult> future = new CompletableFuture<>();
            future.complete(new MarketRestoreResult(zoneId, false, "Zone introuvable", 0, 0));
            return future;
        }

        if (!hasMarketBackup(zoneId)) {
            CompletableFuture<MarketRestoreResult> future = new CompletableFuture<>();
            future.complete(new MarketRestoreResult(zoneId, false, "Aucun backup market trouvé", 0, 0));
            return future;
        }

        CompletableFuture<MarketRestoreResult> future = new CompletableFuture<>();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la restauration market zone §e" + zoneId + "§a...");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    MarketZoneData backupData = loadMarketBackupFromConfig(zoneId);
                    if (backupData == null) {
                        MarketRestoreResult errorResult = new MarketRestoreResult(zoneId, false, "Backup corrompu", 0, 0);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                future.complete(errorResult);
                            }
                        }.runTask(plugin);
                        return;
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                MarketRestoreResult result = performMarketRestore(zone, backupData);

                                if (initiator != null) {
                                    if (result.isSuccess()) {
                                        initiator.sendMessage("§a§lSHOP §8» §aRestauration market zone terminée!");
                                        initiator.sendMessage("§7§lSHOP §8» §7Blocs restaurés: §e" + result.getBlocksRestored());
                                        initiator.sendMessage("§7§lSHOP §8» §7Entités supprimées: §e" + result.getEntitiesRemoved());
                                    } else {
                                        initiator.sendMessage("§c§lSHOP §8» §cErreur restauration: " + result.getErrorMessage());
                                    }
                                }

                                future.complete(result);
                            } catch (Exception e) {
                                MarketRestoreResult errorResult = new MarketRestoreResult(zoneId, false, e.getMessage(), 0, 0);
                                future.complete(errorResult);
                            }
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    MarketRestoreResult errorResult = new MarketRestoreResult(zoneId, false, e.getMessage(), 0, 0);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            future.complete(errorResult);
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Charge un backup market depuis le format ultra-compressé
     */
    private MarketZoneData loadMarketBackupFromConfig(String zoneId) {
        ConfigurationSection zoneSection = marketBackupsConfig.getConfigurationSection("market_backups." + zoneId);
        if (zoneSection == null) return null;

        MarketZoneData data = new MarketZoneData(zoneId);

        List<String> compactColumns = zoneSection.getStringList("columns");
        for (String compactColumn : compactColumns) {
            try {
                parseMarketColumn(compactColumn, data);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur parsing market column: " + compactColumn);
            }
        }

        return data;
    }

    /**
     * Parse une colonne market: "x,z:L0,L1,L2"
     */
    private void parseMarketColumn(String compactColumn, MarketZoneData data) {
        String[] parts = compactColumn.split(":");
        if (parts.length != 2) return;

        String[] coords = parts[0].split(",");
        int x = Integer.parseInt(coords[0]);
        int z = Integer.parseInt(coords[1]);

        MarketColumn column = new MarketColumn(x, z);

        String[] layers = parts[1].split(",");
        for (int layer = 0; layer < Math.min(layers.length, MARKET_LAYERS); layer++) {
            String layerData = layers[layer];

            Material material = switch (layerData) {
                case "0" -> Material.AIR;
                case "C" -> Material.WHITE_CONCRETE;
                case "B" -> Material.BEACON;
                default -> {
                    try {
                        yield Material.valueOf(layerData.toUpperCase());
                    } catch (Exception e) {
                        yield Material.AIR;
                    }
                }
            };

            if (material != Material.AIR) {
                column.setLayer(layer, material);
            }
        }

        if (column.hasBlocks()) {
            data.columns.put(x + "," + z, column);
        }
    }

    /**
     * Effectue la restauration market
     */
    private MarketRestoreResult performMarketRestore(Zone zone, MarketZoneData backupData) {
        try {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                return new MarketRestoreResult(zone.getId(), false, "Monde introuvable", 0, 0);
            }

            int blocksRestored = 0;
            int entitiesRemoved = 0;

            // Supprimer les entités dans la zone
            entitiesRemoved = removeEntitiesInZone(zone, world);

            // Nettoyer les colonnes existantes
            cleanExistingMarketColumns(zone, world);

            // Restaurer depuis le format ultra-compressé
            for (Map.Entry<String, MarketColumn> entry : backupData.columns.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int z = Integer.parseInt(coords[1]);

                MarketColumn column = entry.getValue();

                // Trouver le beacon Y pour cette colonne
                int beaconY = findBeaconY(zone, x, z);
                if (beaconY == -1) continue; // Pas de beacon à cette position

                for (int layer = 0; layer < MARKET_LAYERS; layer++) {
                    Material material = column.getLayer(layer);
                    if (material != null && material != Material.AIR) {
                        int y = calculateLayerY(beaconY, layer);
                        Location loc = new Location(world, x, y, z);
                        Block block = loc.getBlock();

                        try {
                            block.setType(material);
                            blocksRestored++;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur restauration bloc market à " + loc + ": " + e.getMessage());
                        }
                    }
                }
            }

            return new MarketRestoreResult(zone.getId(), true, null, blocksRestored, entitiesRemoved);

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la restauration market zone " + zone.getId() + ": " + e.getMessage());
            return new MarketRestoreResult(zone.getId(), false, e.getMessage(), 0, 0);
        }
    }

    /**
     * Trouve la Y du beacon pour une position X,Z donnée
     */
    private int findBeaconY(Zone zone, int x, int z) {
        for (Location beaconLoc : zone.getBeaconLocations()) {
            if (beaconLoc.getBlockX() == x && beaconLoc.getBlockZ() == z) {
                return beaconLoc.getBlockY();
            }
        }
        return -1; // Beacon non trouvé
    }

    /**
     * Nettoie les colonnes market existantes
     */
    private void cleanExistingMarketColumns(Zone zone, World world) {
        for (Location beaconLoc : zone.getBeaconLocations()) {
            int x = beaconLoc.getBlockX();
            int z = beaconLoc.getBlockZ();
            int beaconY = beaconLoc.getBlockY();

            for (int layer = 0; layer < MARKET_LAYERS; layer++) {
                int y = calculateLayerY(beaconY, layer);
                Location blockLoc = new Location(world, x, y, z);
                Block block = blockLoc.getBlock();

                if (block.getType() != Material.BEACON) {
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Supprime les entités dans la zone
     */
    private int removeEntitiesInZone(Zone zone, World world) {
        int entitiesRemoved = 0;
        Zone.BoundingBox bounds = zone.getBoundingBox();

        if (bounds != null) {
            Collection<Entity> entities = world.getNearbyEntities(
                    new Location(world, (bounds.minX + bounds.maxX) / 2.0, (bounds.minY + bounds.maxY) / 2.0, (bounds.minZ + bounds.maxZ) / 2.0),
                    (bounds.maxX - bounds.minX) / 2.0 + 1,
                    (bounds.maxY - bounds.minY) / 2.0 + 1,
                    (bounds.maxZ - bounds.minZ) / 2.0 + 1
            );

            for (Entity entity : entities) {
                if (!(entity instanceof Player) && zone.containsLocation(entity.getLocation())) {
                    entity.remove();
                    entitiesRemoved++;
                }
            }
        }

        return entitiesRemoved;
    }

    // ===============================
    // UTILITAIRES
    // ===============================

    private void saveMarketBackupsConfig() {
        try {
            marketBackupsConfig.save(marketBackupsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder market-backups.yml: " + e.getMessage());
        }
    }

    public boolean hasMarketBackup(String zoneId) {
        return marketBackupsConfig.contains("market_backups." + zoneId);
    }

    public long getMarketBackupTimestamp(String zoneId) {
        return marketBackupsConfig.getLong("market_backups." + zoneId + ".timestamp", 0);
    }

    public void deleteMarketBackup(String zoneId) {
        marketBackupsConfig.set("market_backups." + zoneId, null);
        saveMarketBackupsConfig();
    }

    public Set<String> getMarketBackedUpZones() {
        ConfigurationSection backupsSection = marketBackupsConfig.getConfigurationSection("market_backups");
        return backupsSection != null ? backupsSection.getKeys(false) : new HashSet<>();
    }

    // ===============================
    // CLASSES INTERNES OPTIMISÉES
    // ===============================

    /**
     * Données de market zone ultra-compressées
     */
    private static class MarketZoneData {
        final String zoneId;
        final Map<String, MarketColumn> columns = new HashMap<>();
        final List<Entity> entitiesToRemove = new ArrayList<>();
        int totalBlocks = 0;

        MarketZoneData(String zoneId) {
            this.zoneId = zoneId;
        }
    }

    /**
     * Colonne market ultra-compacte (seulement 3 couches)
     */
    private static class MarketColumn {
        final int x, z;
        private final Material[] layers = new Material[MARKET_LAYERS];

        MarketColumn(int x, int z) {
            this.x = x;
            this.z = z;
        }

        void setLayer(int layer, Material material) {
            if (layer >= 0 && layer < MARKET_LAYERS) {
                layers[layer] = material;
            }
        }

        Material getLayer(int layer) {
            if (layer >= 0 && layer < MARKET_LAYERS) {
                return layers[layer];
            }
            return null;
        }

        boolean hasBlocks() {
            for (Material material : layers) {
                if (material != null && material != Material.AIR) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Résultat de backup market
     */
    public static class MarketBackupResult {
        private final String zoneId;
        private final boolean success;
        private final String errorMessage;
        private final int columnsSaved;
        private final int entitiesRemoved;
        private final String compressionRatio;

        public MarketBackupResult(String zoneId, boolean success, String errorMessage, int columnsSaved, int entitiesRemoved, String compressionRatio) {
            this.zoneId = zoneId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.columnsSaved = columnsSaved;
            this.entitiesRemoved = entitiesRemoved;
            this.compressionRatio = compressionRatio;
        }

        public String getZoneId() { return zoneId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public int getColumnsSaved() { return columnsSaved; }
        public int getEntitiesRemoved() { return entitiesRemoved; }
        public String getCompressionRatio() { return compressionRatio; }
    }

    /**
     * Résultat de restauration market
     */
    public static class MarketRestoreResult {
        private final String zoneId;
        private final boolean success;
        private final String errorMessage;
        private final int blocksRestored;
        private final int entitiesRemoved;

        public MarketRestoreResult(String zoneId, boolean success, String errorMessage, int blocksRestored, int entitiesRemoved) {
            this.zoneId = zoneId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.blocksRestored = blocksRestored;
            this.entitiesRemoved = entitiesRemoved;
        }

        public String getZoneId() { return zoneId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public int getBlocksRestored() { return blocksRestored; }
        public int getEntitiesRemoved() { return entitiesRemoved; }
    }
}