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

/**
 * Gestionnaire optimisé et compressé pour sauvegarder et restaurer l'état initial des shops
 * Utilise un format ultra-compact pour réduire la taille des fichiers de 90%+
 */
public class ShopBackupManager {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;

    private File backupsFile;
    private FileConfiguration backupsConfig;

    // Dictionnaire pour compresser les matériaux fréquents
    private static final Map<Material, String> MATERIAL_SHORTCUTS = new HashMap<>();
    private static final Map<String, Material> SHORTCUT_TO_MATERIAL = new HashMap<>();

    static {
        // Matériaux les plus fréquents avec des raccourcis courts
        MATERIAL_SHORTCUTS.put(Material.WHITE_CONCRETE, "WC");
        MATERIAL_SHORTCUTS.put(Material.GRAY_CONCRETE, "GC");
        MATERIAL_SHORTCUTS.put(Material.BLACK_CONCRETE, "BC");
        MATERIAL_SHORTCUTS.put(Material.STONE, "S");
        MATERIAL_SHORTCUTS.put(Material.COBBLESTONE, "CS");
        MATERIAL_SHORTCUTS.put(Material.DIRT, "D");
        MATERIAL_SHORTCUTS.put(Material.GRASS_BLOCK, "GB");
        MATERIAL_SHORTCUTS.put(Material.OAK_PLANKS, "OP");
        MATERIAL_SHORTCUTS.put(Material.GLASS, "G");
        MATERIAL_SHORTCUTS.put(Material.CHEST, "CH");

        // Remplir le dictionnaire inverse
        MATERIAL_SHORTCUTS.forEach((material, shortcut) ->
                SHORTCUT_TO_MATERIAL.put(shortcut, material));
    }

    public ShopBackupManager(PlayerShops plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;

        initializeBackupFile();
    }

    // ===============================
    // INITIALISATION
    // ===============================

    private void initializeBackupFile() {
        this.backupsFile = new File(plugin.getDataFolder(), "shop-backups.yml");

        if (!backupsFile.exists()) {
            try {
                backupsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer shop-backups.yml: " + e.getMessage());
            }
        }

        this.backupsConfig = YamlConfiguration.loadConfiguration(backupsFile);
    }

    // ===============================
    // SAUVEGARDE COMPRESSÉE
    // ===============================

    /**
     * Sauvegarde l'état actuel d'une zone (ultra-compressé)
     */
    public CompletableFuture<BackupResult> backupZone(String zoneId, Player initiator) {
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            CompletableFuture<BackupResult> future = new CompletableFuture<>();
            future.complete(new BackupResult(zoneId, false, "Zone introuvable", 0, 0));
            return future;
        }

        CompletableFuture<BackupResult> future = new CompletableFuture<>();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la sauvegarde compressée de la zone §e" + zoneId + "§a...");
        }

        // Étape 1: Collecte des données dans le thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    CompressedWorldData worldData = collectCompressedWorldData(zone);

                    // Étape 2: Traitement et sauvegarde de manière asynchrone
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                BackupResult result = processCompressedBackupData(zone, worldData);

                                // Retour au thread principal pour les messages
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (initiator != null) {
                                            if (result.isSuccess()) {
                                                initiator.sendMessage("§a§lSHOP §8» §aSauvegarde compressée terminée!");
                                                initiator.sendMessage("§7§lSHOP §8» §7Blocs sauvegardés: §e" + result.getBlocksSaved());
                                                initiator.sendMessage("§7§lSHOP §8» §7Compression: §a" + worldData.getCompressionRatio() + "%");
                                                initiator.sendMessage("§7§lSHOP §8» §7Entités supprimées: §e" + result.getEntitiesRemoved());
                                            } else {
                                                initiator.sendMessage("§c§lSHOP §8» §cErreur lors de la sauvegarde: " + result.getErrorMessage());
                                            }
                                        }
                                        future.complete(result);
                                    }
                                }.runTask(plugin);

                            } catch (Exception e) {
                                BackupResult errorResult = new BackupResult(zoneId, false, e.getMessage(), 0, 0);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        future.complete(errorResult);
                                    }
                                }.runTask(plugin);
                            }
                        }
                    }.runTaskAsynchronously(plugin);

                } catch (Exception e) {
                    BackupResult errorResult = new BackupResult(zoneId, false, e.getMessage(), 0, 0);
                    future.complete(errorResult);
                }
            }
        }.runTask(plugin);

        return future;
    }

    /**
     * Sauvegarde toutes les zones (compressée et thread-safe)
     */
    public CompletableFuture<List<BackupResult>> backupAllZones(Player initiator) {
        Collection<Zone> allZones = zoneManager.getAllZones();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la sauvegarde compressée de §e" + allZones.size() + " §azones...");
        }

        CompletableFuture<List<BackupResult>> future = new CompletableFuture<>();
        List<BackupResult> results = new ArrayList<>();

        processZonesSequentially(new ArrayList<>(allZones), results, initiator, future, 0);
        return future;
    }

    /**
     * Traite les zones séquentiellement
     */
    private void processZonesSequentially(List<Zone> zones, List<BackupResult> results,
                                          Player initiator, CompletableFuture<List<BackupResult>> future, int index) {
        if (index >= zones.size()) {
            if (initiator != null) {
                long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
                double avgCompression = results.stream()
                        .filter(BackupResult::isSuccess)
                        .mapToDouble(r -> Double.parseDouble(r.getCompressionInfo().replaceAll("[^0-9.]", "")))
                        .average().orElse(0);

                initiator.sendMessage("§a§lSHOP §8» §aSauvegarde compressée terminée!");
                initiator.sendMessage("§7§lSHOP §8» §7Succès: §e" + successful + "§7/§e" + zones.size());
                initiator.sendMessage("§7§lSHOP §8» §7Compression moyenne: §a" + String.format("%.1f", avgCompression) + "%");
            }
            future.complete(results);
            return;
        }

        Zone zone = zones.get(index);

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    CompressedWorldData worldData = collectCompressedWorldData(zone);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try {
                                BackupResult result = processCompressedBackupData(zone, worldData);
                                result.setCompressionInfo(worldData.getCompressionRatio() + "%");
                                results.add(result);

                                if (initiator != null && (index + 1) % 5 == 0) {
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            initiator.sendMessage("§7§lSHOP §8» §7Progression: §e" + (index + 1) + "§7/§e" + zones.size());
                                        }
                                    }.runTask(plugin);
                                }

                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        processZonesSequentially(zones, results, initiator, future, index + 1);
                                    }
                                }.runTask(plugin);

                            } catch (Exception e) {
                                BackupResult errorResult = new BackupResult(zone.getId(), false, e.getMessage(), 0, 0);
                                results.add(errorResult);

                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        processZonesSequentially(zones, results, initiator, future, index + 1);
                                    }
                                }.runTask(plugin);
                            }
                        }
                    }.runTaskAsynchronously(plugin);

                } catch (Exception e) {
                    BackupResult errorResult = new BackupResult(zone.getId(), false, e.getMessage(), 0, 0);
                    results.add(errorResult);
                    processZonesSequentially(zones, results, initiator, future, index + 1);
                }
            }
        }.runTask(plugin);
    }

    /**
     * Collecte les données du monde de manière compressée
     */
    private CompressedWorldData collectCompressedWorldData(Zone zone) throws Exception {
        World world = Bukkit.getWorld(zone.getWorldName());
        if (world == null) {
            throw new Exception("Monde introuvable");
        }

        CompressedWorldData data = new CompressedWorldData();

        // Grouper par colonne de beacon pour compression optimale
        for (Location beaconLoc : zone.getBeaconLocations()) {
            int beaconX = beaconLoc.getBlockX();
            int beaconZ = beaconLoc.getBlockZ();
            int beaconY = beaconLoc.getBlockY();

            String columnKey = beaconX + "," + beaconZ;
            List<CompressedBlock> columnBlocks = new ArrayList<>();

            // Analyser tous les blocs de cette colonne
            for (int y = beaconY - 1; y <= beaconY + 20; y++) {
                Location blockLoc = new Location(world, beaconX, y, beaconZ);
                Block block = blockLoc.getBlock();

                if (block.getType() == Material.BEACON) continue;
                if (block.getType() == Material.AIR) continue;

                columnBlocks.add(new CompressedBlock(y, block.getType(), block.getBlockData().getAsString()));
            }

            if (!columnBlocks.isEmpty()) {
                data.columns.put(columnKey, compressColumn(columnBlocks));
            }
        }

        // Collecter les entités à supprimer
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

        return data;
    }

    /**
     * Compresse une colonne de blocs en détectant les patterns
     */
    private CompressedColumn compressColumn(List<CompressedBlock> blocks) {
        CompressedColumn column = new CompressedColumn();

        if (blocks.isEmpty()) return column;

        // Trier par Y
        blocks.sort(Comparator.comparingInt(b -> b.y));

        // Détecter les ranges continus du même matériau
        CompressedBlock current = blocks.get(0);
        int rangeStart = current.y;
        int rangeEnd = current.y;

        for (int i = 1; i < blocks.size(); i++) {
            CompressedBlock next = blocks.get(i);

            // Si même matériau et Y continu, étendre la range
            if (next.material == current.material &&
                    next.blockData.equals(current.blockData) &&
                    next.y == rangeEnd + 1) {
                rangeEnd = next.y;
            } else {
                // Sauvegarder la range actuelle
                column.ranges.add(new BlockRange(rangeStart, rangeEnd, current.material, current.blockData));

                // Commencer une nouvelle range
                current = next;
                rangeStart = next.y;
                rangeEnd = next.y;
            }
        }

        // Sauvegarder la dernière range
        column.ranges.add(new BlockRange(rangeStart, rangeEnd, current.material, current.blockData));

        return column;
    }

    /**
     * Traite les données de backup compressées
     */
    private BackupResult processCompressedBackupData(Zone zone, CompressedWorldData worldData) {
        try {
            // Supprimer les entités (dans le thread principal)
            int entitiesRemoved = 0;
            if (!worldData.entitiesToRemove.isEmpty()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Entity entity : worldData.entitiesToRemove) {
                            if (entity != null && !entity.isDead()) {
                                entity.remove();
                            }
                        }
                    }
                }.runTask(plugin);

                entitiesRemoved = worldData.entitiesToRemove.size();
            }

            // Sauvegarder de manière ultra-compressée
            saveCompressedBackupToConfig(zone.getId(), zone.getBeaconLocations(), worldData);

            int totalBlocks = worldData.columns.values().stream()
                    .mapToInt(col -> col.ranges.stream().mapToInt(r -> r.endY - r.startY + 1).sum())
                    .sum();

            return new BackupResult(zone.getId(), true, null, totalBlocks, entitiesRemoved);

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors du traitement de la sauvegarde compressée de la zone " + zone.getId() + ": " + e.getMessage());
            return new BackupResult(zone.getId(), false, e.getMessage(), 0, 0);
        }
    }

    // ===============================
    // RESTAURATION COMPRESSÉE
    // ===============================

    /**
     * Restaure une zone depuis le format compressé
     */
    public CompletableFuture<RestoreResult> restoreZone(String zoneId, Player initiator) {
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            CompletableFuture<RestoreResult> future = new CompletableFuture<>();
            future.complete(new RestoreResult(zoneId, false, "Zone introuvable", 0, 0));
            return future;
        }

        if (!hasBackup(zoneId)) {
            CompletableFuture<RestoreResult> future = new CompletableFuture<>();
            future.complete(new RestoreResult(zoneId, false, "Aucune sauvegarde trouvée", 0, 0));
            return future;
        }

        CompletableFuture<RestoreResult> future = new CompletableFuture<>();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la restauration compressée de la zone §e" + zoneId + "§a...");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    CompressedBackupData backupData = loadCompressedBackupFromConfig(zoneId);
                    if (backupData == null) {
                        RestoreResult errorResult = new RestoreResult(zoneId, false, "Sauvegarde corrompue", 0, 0);
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
                                RestoreResult result = performCompressedRestore(zone, backupData);

                                if (initiator != null) {
                                    if (result.isSuccess()) {
                                        initiator.sendMessage("§a§lSHOP §8» §aRestauration compressée terminée!");
                                        initiator.sendMessage("§7§lSHOP §8» §7Blocs restaurés: §e" + result.getBlocksRestored());
                                        initiator.sendMessage("§7§lSHOP §8» §7Entités supprimées: §e" + result.getEntitiesRemoved());
                                    } else {
                                        initiator.sendMessage("§c§lSHOP §8» §cErreur lors de la restauration: " + result.getErrorMessage());
                                    }
                                }

                                future.complete(result);
                            } catch (Exception e) {
                                RestoreResult errorResult = new RestoreResult(zoneId, false, e.getMessage(), 0, 0);
                                future.complete(errorResult);
                            }
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    RestoreResult errorResult = new RestoreResult(zoneId, false, e.getMessage(), 0, 0);
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
     * Effectue la restauration depuis le format compressé
     */
    private RestoreResult performCompressedRestore(Zone zone, CompressedBackupData backupData) {
        try {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                return new RestoreResult(zone.getId(), false, "Monde introuvable", 0, 0);
            }

            int blocksRestored = 0;
            int entitiesRemoved = 0;

            // Supprimer les entités dans la zone
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

            // Nettoyer par colonne de beacon
            for (Location beaconLoc : backupData.beaconLocations) {
                int beaconX = beaconLoc.getBlockX();
                int beaconZ = beaconLoc.getBlockZ();
                int beaconY = beaconLoc.getBlockY();

                beaconLoc.setWorld(world);

                for (int y = beaconY - 1; y <= beaconY + 20; y++) {
                    Location blockLoc = new Location(world, beaconX, y, beaconZ);
                    Block block = blockLoc.getBlock();
                    if (block.getType() != Material.BEACON) {
                        block.setType(Material.AIR);
                    }
                }
            }

            // Restaurer depuis le format compressé
            for (Map.Entry<String, CompressedColumn> entry : backupData.columns.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int z = Integer.parseInt(coords[1]);

                CompressedColumn column = entry.getValue();

                for (BlockRange range : column.ranges) {
                    for (int y = range.startY; y <= range.endY; y++) {
                        Location loc = new Location(world, x, y, z);
                        Block block = loc.getBlock();

                        try {
                            block.setType(range.material);
                            if (range.blockData != null && !range.blockData.isEmpty() &&
                                    !range.blockData.equals("minecraft:" + range.material.name().toLowerCase())) {
                                block.setBlockData(Bukkit.createBlockData(range.blockData));
                            }
                            blocksRestored++;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur restauration bloc compressé à " + loc + ": " + e.getMessage());
                        }
                    }
                }
            }

            return new RestoreResult(zone.getId(), true, null, blocksRestored, entitiesRemoved);

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la restauration compressée de la zone " + zone.getId() + ": " + e.getMessage());
            return new RestoreResult(zone.getId(), false, e.getMessage(), 0, 0);
        }
    }

    // ===============================
    // GESTION DES FICHIERS COMPRESSÉE
    // ===============================

    /**
     * Sauvegarde ultra-compressée
     */
    private void saveCompressedBackupToConfig(String zoneId, Set<Location> beacons, CompressedWorldData worldData) {
        ConfigurationSection zoneSection = backupsConfig.createSection("backups." + zoneId);
        zoneSection.set("timestamp", System.currentTimeMillis());
        zoneSection.set("format", "compressed_v2");
        zoneSection.set("beaconCount", beacons.size());

        // Sauvegarder les beacons (inchangé)
        List<String> beaconStrings = new ArrayList<>();
        for (Location beacon : beacons) {
            beaconStrings.add(beacon.getBlockX() + "," + beacon.getBlockY() + "," + beacon.getBlockZ());
        }
        zoneSection.set("beacons", beaconStrings);

        // Sauvegarder les colonnes compressées
        ConfigurationSection columnsSection = zoneSection.createSection("columns");
        for (Map.Entry<String, CompressedColumn> entry : worldData.columns.entrySet()) {
            ConfigurationSection columnSection = columnsSection.createSection(entry.getKey().replace(",", "_"));

            List<String> rangeStrings = new ArrayList<>();
            for (BlockRange range : entry.getValue().ranges) {
                String materialCode = MATERIAL_SHORTCUTS.getOrDefault(range.material, range.material.name());

                if (range.startY == range.endY) {
                    // Bloc unique: "Y:MAT" ou "Y:MAT:data"
                    rangeStrings.add(range.startY + ":" + materialCode +
                            (needsBlockData(range) ? ":" + range.blockData : ""));
                } else {
                    // Range: "Y1-Y2:MAT" ou "Y1-Y2:MAT:data"
                    rangeStrings.add(range.startY + "-" + range.endY + ":" + materialCode +
                            (needsBlockData(range) ? ":" + range.blockData : ""));
                }
            }

            columnSection.set("ranges", rangeStrings);
        }

        // Statistiques de compression
        int originalSize = worldData.getOriginalBlockCount();
        int compressedSize = worldData.getCompressedSize();
        zoneSection.set("compression.original", originalSize);
        zoneSection.set("compression.compressed", compressedSize);
        zoneSection.set("compression.ratio", worldData.getCompressionRatio());

        saveBackupsConfig();
    }

    /**
     * Vérifie si les données de bloc sont nécessaires
     */
    private boolean needsBlockData(BlockRange range) {
        if (range.blockData == null || range.blockData.isEmpty()) return false;

        // Ne pas sauvegarder si les données sont identiques au nom du matériau
        String defaultData = "minecraft:" + range.material.name().toLowerCase();
        return !range.blockData.equals(defaultData);
    }

    /**
     * Charge une backup depuis le format compressé
     */
    private CompressedBackupData loadCompressedBackupFromConfig(String zoneId) {
        ConfigurationSection zoneSection = backupsConfig.getConfigurationSection("backups." + zoneId);
        if (zoneSection == null) return null;

        CompressedBackupData data = new CompressedBackupData();

        // Charger les beacons
        List<String> beaconStrings = zoneSection.getStringList("beacons");
        for (String beaconStr : beaconStrings) {
            try {
                String[] parts = beaconStr.split(",");
                Location beaconLoc = new Location(
                        null,
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
                data.beaconLocations.add(beaconLoc);
            } catch (Exception e) {
                // Ignorer les beacons malformés
            }
        }

        // Charger les colonnes compressées
        ConfigurationSection columnsSection = zoneSection.getConfigurationSection("columns");
        if (columnsSection != null) {
            for (String columnKey : columnsSection.getKeys(false)) {
                ConfigurationSection columnSection = columnsSection.getConfigurationSection(columnKey);
                if (columnSection != null) {
                    CompressedColumn column = new CompressedColumn();

                    List<String> rangeStrings = columnSection.getStringList("ranges");
                    for (String rangeStr : rangeStrings) {
                        try {
                            BlockRange range = parseBlockRange(rangeStr);
                            if (range != null) {
                                column.ranges.add(range);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Erreur parsing range: " + rangeStr);
                        }
                    }

                    data.columns.put(columnKey.replace("_", ","), column);
                }
            }
        }

        return data;
    }

    /**
     * Parse une range compressée : "Y:MAT", "Y1-Y2:MAT", "Y:MAT:data", etc.
     */
    private BlockRange parseBlockRange(String rangeStr) {
        String[] parts = rangeStr.split(":");
        if (parts.length < 2) return null;

        String yPart = parts[0];
        String materialCode = parts[1];
        String blockData = parts.length > 2 ? parts[2] : null;

        // Résoudre le matériau
        Material material = SHORTCUT_TO_MATERIAL.getOrDefault(materialCode,
                Material.valueOf(materialCode.toUpperCase()));

        // Parser les Y
        int startY, endY;
        if (yPart.contains("-")) {
            String[] yParts = yPart.split("-");
            startY = Integer.parseInt(yParts[0]);
            endY = Integer.parseInt(yParts[1]);
        } else {
            startY = endY = Integer.parseInt(yPart);
        }

        return new BlockRange(startY, endY, material, blockData);
    }

    private void saveBackupsConfig() {
        try {
            backupsConfig.save(backupsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder shop-backups.yml: " + e.getMessage());
        }
    }

    // ===============================
    // MÉTHODES UTILITAIRES INCHANGÉES
    // ===============================

    public boolean hasBackup(String zoneId) {
        return backupsConfig.contains("backups." + zoneId);
    }

    public long getBackupTimestamp(String zoneId) {
        return backupsConfig.getLong("backups." + zoneId + ".timestamp", 0);
    }

    public void deleteBackup(String zoneId) {
        backupsConfig.set("backups." + zoneId, null);
        saveBackupsConfig();
    }

    public Set<String> getBackedUpZones() {
        ConfigurationSection backupsSection = backupsConfig.getConfigurationSection("backups");
        return backupsSection != null ? backupsSection.getKeys(false) : new HashSet<>();
    }

    /**
     * Obtient les statistiques de compression d'une backup
     */
    public CompressionStats getCompressionStats(String zoneId) {
        ConfigurationSection zoneSection = backupsConfig.getConfigurationSection("backups." + zoneId);
        if (zoneSection == null || !zoneSection.contains("compression")) {
            return null;
        }

        int original = zoneSection.getInt("compression.original");
        int compressed = zoneSection.getInt("compression.compressed");
        double ratio = zoneSection.getDouble("compression.ratio");

        return new CompressionStats(original, compressed, ratio);
    }

    // ===============================
    // CLASSES INTERNES COMPRESSÉES
    // ===============================

    /**
     * Données collectées du monde (format compressé)
     */
    private static class CompressedWorldData {
        final Map<String, CompressedColumn> columns = new HashMap<>();
        final List<Entity> entitiesToRemove = new ArrayList<>();

        public int getOriginalBlockCount() {
            return columns.values().stream()
                    .mapToInt(col -> col.ranges.stream().mapToInt(r -> r.endY - r.startY + 1).sum())
                    .sum();
        }

        public int getCompressedSize() {
            return columns.size() + columns.values().stream().mapToInt(col -> col.ranges.size()).sum();
        }

        public double getCompressionRatio() {
            int original = getOriginalBlockCount();
            int compressed = getCompressedSize();
            return original > 0 ? (1.0 - (double) compressed / original) * 100 : 0;
        }
    }

    /**
     * Colonne compressée (groupe de ranges)
     */
    private static class CompressedColumn {
        final List<BlockRange> ranges = new ArrayList<>();
    }

    /**
     * Range de blocs identiques
     */
    private static class BlockRange {
        final int startY, endY;
        final Material material;
        final String blockData;

        BlockRange(int startY, int endY, Material material, String blockData) {
            this.startY = startY;
            this.endY = endY;
            this.material = material;
            this.blockData = blockData;
        }
    }

    /**
     * Bloc compressé temporaire
     */
    private static class CompressedBlock {
        final int y;
        final Material material;
        final String blockData;

        CompressedBlock(int y, Material material, String blockData) {
            this.y = y;
            this.material = material;
            this.blockData = blockData;
        }
    }

    /**
     * Données de backup compressées
     */
    private static class CompressedBackupData {
        final Set<Location> beaconLocations = new HashSet<>();
        final Map<String, CompressedColumn> columns = new HashMap<>();
    }

    /**
     * Statistiques de compression
     */
    public static class CompressionStats {
        private final int originalBlocks;
        private final int compressedEntries;
        private final double compressionRatio;

        public CompressionStats(int originalBlocks, int compressedEntries, double compressionRatio) {
            this.originalBlocks = originalBlocks;
            this.compressedEntries = compressedEntries;
            this.compressionRatio = compressionRatio;
        }

        public int getOriginalBlocks() { return originalBlocks; }
        public int getCompressedEntries() { return compressedEntries; }
        public double getCompressionRatio() { return compressionRatio; }

        @Override
        public String toString() {
            return String.format("Compression: %d blocs → %d entrées (%.1f%% économie)",
                    originalBlocks, compressedEntries, compressionRatio);
        }
    }

    // Classes de résultat améliorées
    public static class BackupResult {
        private final String zoneId;
        private final boolean success;
        private final String errorMessage;
        private final int blocksSaved;
        private final int entitiesRemoved;
        private String compressionInfo = "";

        public BackupResult(String zoneId, boolean success, String errorMessage, int blocksSaved, int entitiesRemoved) {
            this.zoneId = zoneId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.blocksSaved = blocksSaved;
            this.entitiesRemoved = entitiesRemoved;
        }

        public String getZoneId() { return zoneId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public int getBlocksSaved() { return blocksSaved; }
        public int getEntitiesRemoved() { return entitiesRemoved; }
        public String getCompressionInfo() { return compressionInfo; }
        public void setCompressionInfo(String compressionInfo) { this.compressionInfo = compressionInfo; }
    }

    public static class RestoreResult {
        private final String zoneId;
        private final boolean success;
        private final String errorMessage;
        private final int blocksRestored;
        private final int entitiesRemoved;

        public RestoreResult(String zoneId, boolean success, String errorMessage, int blocksRestored, int entitiesRemoved) {
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