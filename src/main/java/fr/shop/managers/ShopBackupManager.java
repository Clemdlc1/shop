package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
 * Gestionnaire pour sauvegarder et restaurer l'état initial des shops
 */
public class ShopBackupManager {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;

    private File backupsFile;
    private FileConfiguration backupsConfig;

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
    // SAUVEGARDE DES SHOPS
    // ===============================

    /**
     * Sauvegarde l'état actuel d'une zone (shop)
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
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la sauvegarde de la zone §e" + zoneId + "§a...");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    BackupResult result = performBackup(zone);

                    if (initiator != null) {
                        if (result.isSuccess()) {
                            initiator.sendMessage("§a§lSHOP §8» §aSauvegarde terminée!");
                            initiator.sendMessage("§7§lSHOP §8» §7Blocs sauvegardés: §e" + result.getBlocksSaved());
                            initiator.sendMessage("§7§lSHOP §8» §7Entités supprimées: §e" + result.getEntitiesRemoved());
                        } else {
                            initiator.sendMessage("§c§lSHOP §8» §cErreur lors de la sauvegarde: " + result.getErrorMessage());
                        }
                    }

                    future.complete(result);
                } catch (Exception e) {
                    BackupResult errorResult = new BackupResult(zoneId, false, e.getMessage(), 0, 0);
                    future.complete(errorResult);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Sauvegarde toutes les zones
     */
    public CompletableFuture<List<BackupResult>> backupAllZones(Player initiator) {
        Collection<Zone> allZones = zoneManager.getAllZones();

        if (initiator != null) {
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la sauvegarde de §e" + allZones.size() + " §azones...");
        }

        CompletableFuture<List<BackupResult>> future = new CompletableFuture<>();
        List<BackupResult> results = new ArrayList<>();

        new BukkitRunnable() {
            private int processed = 0;
            private final int total = allZones.size();

            @Override
            public void run() {
                try {
                    for (Zone zone : allZones) {
                        BackupResult result = performBackup(zone);
                        results.add(result);
                        processed++;

                        if (initiator != null && processed % 5 == 0) {
                            initiator.sendMessage("§7§lSHOP §8» §7Progression: §e" + processed + "§7/§e" + total);
                        }
                    }

                    if (initiator != null) {
                        long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
                        initiator.sendMessage("§a§lSHOP §8» §aSauvegarde terminée!");
                        initiator.sendMessage("§7§lSHOP §8» §7Succès: §e" + successful + "§7/§e" + total);
                    }

                    future.complete(results);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Effectue la sauvegarde d'une zone
     */
    private BackupResult performBackup(Zone zone) {
        try {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                return new BackupResult(zone.getId(), false, "Monde introuvable", 0, 0);
            }

            List<BlockData> blocks = new ArrayList<>();
            int entitiesRemoved = 0;

            // Sauvegarder tous les blocs de la zone
            for (Location blockLoc : zone.getZoneBlocks()) {
                Block block = blockLoc.getBlock();

                // Ignorer les beacons (ils font partie de la structure)
                if (block.getType() == Material.BEACON) continue;

                blocks.add(new BlockData(
                        blockLoc.getBlockX(),
                        blockLoc.getBlockY(),
                        blockLoc.getBlockZ(),
                        block.getType(),
                        block.getBlockData().getAsString()
                ));
            }

            // Supprimer les entités dans la zone (sauf les joueurs)
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

            // Sauvegarder dans le fichier config
            saveBackupToConfig(zone.getId(), blocks);

            return new BackupResult(zone.getId(), true, null, blocks.size(), entitiesRemoved);

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde de la zone " + zone.getId() + ": " + e.getMessage());
            return new BackupResult(zone.getId(), false, e.getMessage(), 0, 0);
        }
    }

    // ===============================
    // RESTAURATION DES SHOPS
    // ===============================

    /**
     * Restaure une zone à son état initial
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
            initiator.sendMessage("§a§lSHOP §8» §aDébut de la restauration de la zone §e" + zoneId + "§a...");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    RestoreResult result = performRestore(zone);

                    if (initiator != null) {
                        if (result.isSuccess()) {
                            initiator.sendMessage("§a§lSHOP §8» §aRestauration terminée!");
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
        }.runTask(plugin); // runTask car on modifie des blocs
        return future;
    }

    /**
     * Effectue la restauration d'une zone
     */
    private RestoreResult performRestore(Zone zone) {
        try {
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                return new RestoreResult(zone.getId(), false, "Monde introuvable", 0, 0);
            }

            List<BlockData> backupBlocks = loadBackupFromConfig(zone.getId());
            if (backupBlocks == null) {
                return new RestoreResult(zone.getId(), false, "Sauvegarde corrompue", 0, 0);
            }

            int blocksRestored = 0;
            int entitiesRemoved = 0;

            // Supprimer les entités dans la zone (sauf les joueurs)
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

            // Restaurer les blocs (d'abord nettoyer, puis restaurer)
            // 1. Nettoyer tous les blocs de la zone (sauf les beacons)
            for (Location blockLoc : zone.getZoneBlocks()) {
                Block block = blockLoc.getBlock();
                if (block.getType() != Material.BEACON) {
                    block.setType(Material.AIR);
                }
            }

            // 2. Restaurer les blocs sauvegardés
            for (BlockData blockData : backupBlocks) {
                Location loc = new Location(world, blockData.x, blockData.y, blockData.z);
                Block block = loc.getBlock();

                try {
                    block.setType(blockData.material);
                    if (blockData.blockDataString != null && !blockData.blockDataString.isEmpty()) {
                        block.setBlockData(Bukkit.createBlockData(blockData.blockDataString));
                    }
                    blocksRestored++;
                } catch (Exception e) {
                    // Ignorer les erreurs de blocs individuels
                    plugin.getLogger().warning("Erreur restauration bloc à " + loc + ": " + e.getMessage());
                }
            }

            return new RestoreResult(zone.getId(), true, null, blocksRestored, entitiesRemoved);

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la restauration de la zone " + zone.getId() + ": " + e.getMessage());
            return new RestoreResult(zone.getId(), false, e.getMessage(), 0, 0);
        }
    }

    // ===============================
    // GESTION DES FICHIERS DE CONFIG
    // ===============================

    /**
     * Sauvegarde une backup dans le fichier config
     */
    private void saveBackupToConfig(String zoneId, List<BlockData> blocks) {
        ConfigurationSection zoneSection = backupsConfig.createSection("backups." + zoneId);
        zoneSection.set("timestamp", System.currentTimeMillis());
        zoneSection.set("blockCount", blocks.size());

        List<Map<String, Object>> blocksList = new ArrayList<>();
        for (BlockData block : blocks) {
            Map<String, Object> blockMap = new HashMap<>();
            blockMap.put("x", block.x);
            blockMap.put("y", block.y);
            blockMap.put("z", block.z);
            blockMap.put("material", block.material.name());
            if (block.blockDataString != null && !block.blockDataString.isEmpty()) {
                blockMap.put("data", block.blockDataString);
            }
            blocksList.add(blockMap);
        }

        zoneSection.set("blocks", blocksList);
        saveBackupsConfig();
    }

    /**
     * Charge une backup depuis le fichier config
     */
    private List<BlockData> loadBackupFromConfig(String zoneId) {
        ConfigurationSection zoneSection = backupsConfig.getConfigurationSection("backups." + zoneId);
        if (zoneSection == null) return null;

        List<Map<?, ?>> blocksList = zoneSection.getMapList("blocks");
        List<BlockData> blocks = new ArrayList<>();

        for (Map<?, ?> blockMap : blocksList) {
            try {
                int x = (Integer) blockMap.get("x");
                int y = (Integer) blockMap.get("y");
                int z = (Integer) blockMap.get("z");
                Material material = Material.valueOf((String) blockMap.get("material"));
                String data = (String) blockMap.get("data");

                blocks.add(new BlockData(x, y, z, material, data));
            } catch (Exception e) {
                // Ignorer les blocs malformés
            }
        }

        return blocks;
    }

    private void saveBackupsConfig() {
        try {
            backupsConfig.save(backupsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder shop-backups.yml: " + e.getMessage());
        }
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Vérifie si une zone a une sauvegarde
     */
    public boolean hasBackup(String zoneId) {
        return backupsConfig.contains("backups." + zoneId);
    }

    /**
     * Obtient la date de la dernière sauvegarde d'une zone
     */
    public long getBackupTimestamp(String zoneId) {
        return backupsConfig.getLong("backups." + zoneId + ".timestamp", 0);
    }

    /**
     * Supprime la sauvegarde d'une zone
     */
    public void deleteBackup(String zoneId) {
        backupsConfig.set("backups." + zoneId, null);
        saveBackupsConfig();
    }

    /**
     * Obtient la liste de toutes les zones sauvegardées
     */
    public Set<String> getBackedUpZones() {
        ConfigurationSection backupsSection = backupsConfig.getConfigurationSection("backups");
        return backupsSection != null ? backupsSection.getKeys(false) : new HashSet<>();
    }

    // ===============================
    // CLASSES INTERNES
    // ===============================

    private static class BlockData {
        final int x, y, z;
        final Material material;
        final String blockDataString;

        BlockData(int x, int y, int z, Material material, String blockDataString) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.blockDataString = blockDataString;
        }
    }

    public static class BackupResult {
        private final String zoneId;
        private final boolean success;
        private final String errorMessage;
        private final int blocksSaved;
        private final int entitiesRemoved;

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