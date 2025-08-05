package fr.shop.commands;

import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import fr.shop.managers.MarketZoneBackupManager;
import fr.shop.managers.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Commandes d'administration optimisées pour la gestion des zones et sauvegardes
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;
    private final MarketZoneBackupManager marketBackupManager;

    public ShopAdminCommand(PlayerShops plugin) {
        this.plugin = plugin;
        this.zoneManager = plugin.getZoneManager();
        this.marketBackupManager = plugin.getMarketZoneBackupManager();

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playershops.admin")) {
            sender.sendMessage("§c§lSHOP §8» §cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "scan":
                handleScanCommand(sender, args);
                break;

            case "zones":
                handleZonesCommand(sender, args);
                break;

            case "validate":
                handleValidateCommand(sender);
                break;

            case "reload":
                handleReloadCommand(sender);
                break;

            case "stats":
                handleStatsCommand(sender);
                break;

            case "optimize":
                handleOptimizeCommand(sender);
                break;

            case "cache":
                handleCacheCommand(sender, args);
                break;

            case "help":
                sendAdminHelp(sender);
                break;

            case "backup":
                handleMarketBackupCommand(sender, args);
                break;

            case "restore":
                handleMarketRestoreCommand(sender, args);

            case "teleport":
            case "tp":
                handleTeleportCommand(sender, args);
                break;

            default:
                sender.sendMessage("§c§lSHOP §8» §cCommande administrative inconnue! Utilisez §e/shopadmin help");
                break;
        }

        return true;
    }

    // ===============================
    // COMMANDE SCAN (améliorée)
    // ===============================

    private void handleScanCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin scan <monde>");
            return;
        }

        String worldName = args[1];
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            sender.sendMessage("§c§lSHOP §8» §cMonde introuvable: " + worldName);
            return;
        }

        if (zoneManager.getScanner().isScanning()) {
            sender.sendMessage("§c§lSHOP §8» §cUn scan est déjà en cours!");
            return;
        }

        // Afficher les stats avant le scan
        ZoneManager.ZoneStats statsBefore = zoneManager.getStats();
        sender.sendMessage("§7§lSHOP §8» §7Zones actuelles: §e" + statsBefore.getTotalZones());

        zoneManager.getScanner().scanWorld(world, player).thenAccept(result -> {
            if (result != null) {
                ZoneManager.ZoneStats statsAfter = zoneManager.getStats();
                plugin.getLogger().info("Scan terminé: " + result);

                // Message de résumé avec comparaison avant/après
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage("§6§l▬▬▬▬▬▬▬ RÉSULTATS DU SCAN ▬▬▬▬▬▬▬");
                        sender.sendMessage("§7Beacons trouvés: §e" + result.getBeaconsFound());
                        sender.sendMessage("§7Zones créées: §e" + result.getZonesCreated());
                        sender.sendMessage("§7Total zones: §e" + statsAfter.getTotalZones() + " §7(§a+" +
                                (statsAfter.getTotalZones() - statsBefore.getTotalZones()) + "§7)");
                        sender.sendMessage("§7Durée: §e" + (result.getDuration() / 1000.0) + "s");
                        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    }
                }.runTask(plugin);
            }
        });
    }

    // ===============================
    // COMMANDE ZONES (améliorée)
    // ===============================

    private void handleZonesCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin zones <list/info/delete>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list":
                handleZonesListCommand(sender, args);
                break;

            case "info":
                handleZonesInfoCommand(sender, args);
                break;

            case "delete":
                handleZonesDeleteCommand(sender, args);
                break;

            default:
                sender.sendMessage("§c§lSHOP §8» §cAction inconnue. Utilisez: list, info, delete");
                break;
        }
    }

    private void handleZonesListCommand(CommandSender sender, String[] args) {
        String worldFilter = args.length > 2 ? args[2] : null;

        List<Zone> zones = worldFilter != null
                ? zoneManager.getZonesInWorld(worldFilter)
                : new ArrayList<>(zoneManager.getAllZones());

        if (zones.isEmpty()) {
            sender.sendMessage("§c§lSHOP §8» §cAucune zone trouvée" +
                    (worldFilter != null ? " dans le monde " + worldFilter : ""));
            return;
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ ZONES " + (worldFilter != null ? worldFilter.toUpperCase() : "TOUTES") + " ▬▬▬▬▬▬▬");

        // Grouper par taille pour un meilleur affichage
        zones.sort((a, b) -> Integer.compare(b.getBeaconCount(), a.getBeaconCount()));

        for (Zone zone : zones) {
            String efficiency = calculateEfficiency(zone);

            sender.sendMessage("§e" + zone.getId() + " §7- §f" + zone.getBeaconCount() + " §7beacons, " +
                    "§f" + zone.getBlockCount() + " §7blocs " + efficiency);
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§7Total: §e" + zones.size() + " §7zones");

        // Afficher les statistiques d'optimisation
        if (zones.size() > 0) {
            int totalBeacons = zones.stream().mapToInt(Zone::getBeaconCount).sum();
            int totalBlocks = zones.stream().mapToInt(Zone::getBlockCount).sum();
            sender.sendMessage("§7Économie mémoire: §a" + (totalBlocks - totalBeacons) + " §7blocs non stockés");
        }
    }

    private String calculateEfficiency(Zone zone) {
        int beacons = zone.getBeaconCount();
        int blocks = zone.getBlockCount();
        if (beacons == 0) return "";

        double ratio = (double) blocks / beacons;
        if (ratio > 20) return "§a(compact)";
        else if (ratio > 15) return "§e(normal)";
        else return "§c(dispersé)";
    }

    private void handleZonesInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin zones info <zoneId>");
            return;
        }

        String zoneId = args[2];
        Zone zone = zoneManager.getZone(zoneId);

        if (zone == null) {
            sender.sendMessage("§c§lSHOP §8» §cZone introuvable: " + zoneId);
            return;
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ INFO ZONE " + zoneId.toUpperCase() + " ▬▬▬▬▬▬▬");
        sender.sendMessage("§7ID: §e" + zone.getId());
        sender.sendMessage("§7Monde: §e" + zone.getWorldName());
        sender.sendMessage("§7Beacons: §e" + zone.getBeaconCount());
        sender.sendMessage("§7Blocs: §e" + zone.getBlockCount() + " §7(calculés à la demande)");

        if (zone.getCenterLocation() != null) {
            sender.sendMessage("§7Centre: §e" +
                    Math.round(zone.getCenterLocation().getX()) + ", " +
                    Math.round(zone.getCenterLocation().getY()) + ", " +
                    Math.round(zone.getCenterLocation().getZ()));
        }

        Zone.BoundingBox bounds = zone.getBoundingBox();
        if (bounds != null) {
            sender.sendMessage("§7Limites: §e(" + bounds.minX + "," + bounds.minY + "," + bounds.minZ +
                    ") §7à §e(" + bounds.maxX + "," + bounds.maxY + "," + bounds.maxZ + ")");

            // Afficher l'efficacité de la bounding box
            int boundingVolume = (bounds.maxX - bounds.minX + 1) * (bounds.maxY - bounds.minY + 1) * (bounds.maxZ - bounds.minZ + 1);
            double efficiency = (double) zone.getBlockCount() / boundingVolume * 100;
            sender.sendMessage("§7Efficacité: §e" + String.format("%.1f", efficiency) + "% §7du volume total");
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void handleZonesDeleteCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin zones delete <zoneId>");
            return;
        }

        String zoneId = args[2];
        Zone zone = zoneManager.getZone(zoneId);

        if (zone == null) {
            sender.sendMessage("§c§lSHOP §8» §cZone introuvable: " + zoneId);
            return;
        }

        zoneManager.removeZone(zoneId);
        zoneManager.saveZones();

        sender.sendMessage("§a§lSHOP §8» §aZone §e" + zoneId + " §asupprimée!");
    }

    // ===============================
    // COMMANDES DE MAINTENANCE (nouvelles)
    // ===============================

    private void handleOptimizeCommand(CommandSender sender) {
        sender.sendMessage("§a§lSHOP §8» §aOptimisation des structures en cours...");

        long startTime = System.currentTimeMillis();
        zoneManager.optimize();
        long duration = System.currentTimeMillis() - startTime;

        ZoneManager.CacheStats cacheStats = zoneManager.getCacheStats();

        sender.sendMessage("§a§lSHOP §8» §aOptimisation terminée en " + duration + "ms");
        sender.sendMessage("§7§lSHOP §8» §7Cache: " + cacheStats.toString());
    }

    private void handleCacheCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Afficher les stats du cache
            ZoneManager.CacheStats stats = zoneManager.getCacheStats();
            sender.sendMessage("§6§l▬▬▬▬▬▬▬ CACHE STATS ▬▬▬▬▬▬▬");
            sender.sendMessage("§7Taille actuelle: §e" + stats.getCurrentSize() + "§7/§e" + stats.getMaxSize());
            sender.sendMessage("§7Utilisation: §e" + String.format("%.1f", stats.getUsagePercentage()) + "%");
            sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        String action = args[1].toLowerCase();

        if ("clear".equals(action)) {
            zoneManager.clearLocationCache();
            sender.sendMessage("§a§lSHOP §8» §aCache vidé!");
        } else {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin cache [clear]");
        }
    }

    // ===============================
    // AUTRES COMMANDES (améliorées)
    // ===============================

    private void handleValidateCommand(CommandSender sender) {
        sender.sendMessage("§a§lSHOP §8» §aValidation des zones en cours...");

        long startTime = System.currentTimeMillis();
        ZoneManager.ValidationResult result = zoneManager.validateZones();
        long duration = System.currentTimeMillis() - startTime;

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ VALIDATION DES ZONES ▬▬▬▬▬▬▬");
        sender.sendMessage("§7Zones valides: §a" + result.getValidZones());
        sender.sendMessage("§7Zones invalides: §c" + result.getInvalidZones());
        sender.sendMessage("§7Durée: §e" + duration + "ms");

        if (result.hasIssues()) {
            sender.sendMessage("§c§lProblèmes détectés:");
            for (String issue : result.getIssues()) {
                sender.sendMessage("§c- " + issue);
            }
        } else {
            sender.sendMessage("§a§lAucun problème détecté!");
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void handleReloadCommand(CommandSender sender) {
        sender.sendMessage("§a§lSHOP §8» §aRechargement des zones...");

        long startTime = System.currentTimeMillis();
        zoneManager.loadZones();
        plugin.getConfigManager().reloadConfig();
        long duration = System.currentTimeMillis() - startTime;

        sender.sendMessage("§a§lSHOP §8» §aRechargement terminé en " + duration + "ms!");
    }

    private void handleStatsCommand(CommandSender sender) {
        ZoneManager.ZoneStats stats = zoneManager.getStats();
        ZoneManager.CacheStats cacheStats = zoneManager.getCacheStats();

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ STATISTIQUES ZONES ▬▬▬▬▬▬▬");
        sender.sendMessage("§7Total zones: §e" + stats.getTotalZones());
        sender.sendMessage("§7Total beacons: §e" + stats.getTotalBeacons());
        sender.sendMessage("§7Total blocs: §e" + stats.getTotalBlocks() + " §7(calculés)");

        // Calculs d'optimisation
        int savedBlocks = stats.getTotalBlocks() - stats.getTotalBeacons();
        sender.sendMessage("§7Économie mémoire: §a" + savedBlocks + " §7blocs non stockés");

        if (stats.getTotalBlocks() > 0) {
            double savings = (double) savedBlocks / stats.getTotalBlocks() * 100;
            sender.sendMessage("§7Réduction stockage: §a" + String.format("%.1f", savings) + "%");
        }

        if (!stats.getWorldCounts().isEmpty()) {
            sender.sendMessage("§7Par monde:");
            for (var entry : stats.getWorldCounts().entrySet()) {
                sender.sendMessage("§8  - §e" + entry.getKey() + "§7: §f" + entry.getValue() + " §7zones");
            }
        }
        sender.sendMessage("§7Cache: " + cacheStats.toString());
        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6§l▬▬▬▬▬▬▬ SHOP ADMIN HELP ▬▬▬▬▬▬▬");
        sender.sendMessage("§e/shopadmin scan <monde> §7- Scanner les beacons d'un monde");
        sender.sendMessage("§e/shopadmin zones list [monde] §7- Lister les zones");
        sender.sendMessage("§e/shopadmin zones info <zoneId> §7- Info d'une zone");
        sender.sendMessage("§e/shopadmin zones delete <zoneId> §7- Supprimer une zone");
        sender.sendMessage("§e/shopadmin tp <zoneId> §7- Se téléporter à une zone");
        sender.sendMessage("§e/shopadmin backup <zoneId|all> §7- Backup market zone(s)");
        sender.sendMessage("§e/shopadmin restore <zoneId> §7- Restaurer market zone");
        sender.sendMessage("§e/shopadmin marketlist §7- Lister les backups market");
        sender.sendMessage("§e/shopadmin validate §7- Valider l'intégrité des zones");
        sender.sendMessage("§e/shopadmin reload §7- Recharger les configurations");
        sender.sendMessage("§e/shopadmin stats §7- Statistiques des zones");
        sender.sendMessage("§e/shopadmin optimize §7- Optimiser les structures");
        sender.sendMessage("§e/shopadmin cache [clear] §7- Gestion du cache");
        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("playershops.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                    "scan", "zones", "validate", "reload", "stats", "optimize", "cache",
                    "marketbackup", "mbackup", "marketrestore", "mrestore", "marketlist", "mlist",
                    "teleport", "tp", "help"
            ));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "scan":
                    completions.addAll(Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .toList());
                    break;

                case "zones":
                    completions.addAll(Arrays.asList("list", "info", "delete"));
                    break;

                case "cache":
                    completions.add("clear");
                    break;

                case "marketbackup":
                case "mbackup":
                    completions.add("all");
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .toList());
                    break;

                case "marketrestore":
                case "mrestore":
                    completions.addAll(marketBackupManager.getMarketBackedUpZones());
                    break;

                case "teleport":
                case "tp":
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .toList());
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();

            if ("zones".equals(subCommand)) {
                if ("list".equals(action)) {
                    completions.addAll(Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .toList());
                } else if ("info".equals(action) || "delete".equals(action)) {
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .toList());
                }
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private void handleMarketBackupCommand(CommandSender sender, String[] args) {
        // La vérification du Player a été déplacée ici pour s'appliquer à 'all' également
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin backup <zoneId|all>");
            return;
        }

        String target = args[1].toLowerCase();
        Player player = (Player) sender;

        if ("all".equals(target)) {
            // Backup de toutes les zones
            Collection<Zone> allZones = zoneManager.getAllZones();
            if (allZones.isEmpty()) {
                sender.sendMessage("§c§lSHOP §8» §cAucune zone trouvée!");
                return;
            }

            sender.sendMessage("§a§lSHOP §8» §aDébut du backup séquentiel de §e" + allZones.size() + " §azones...");

            // Utiliser une file d'attente pour traiter les zones une par une
            Queue<Zone> zoneQueue = new LinkedList<>(allZones);
            processNextZoneInBackupQueue(player, zoneQueue, allZones.size());

        } else {
            // Backup d'une zone spécifique (le code existant est correct pour une seule zone)
            Zone zone = zoneManager.getZone(target);
            if (zone == null) {
                sender.sendMessage("§c§lSHOP §8» §cZone introuvable: " + target);
                return;
            }

            marketBackupManager.backupMarketZone(target, player);
        }
    }

    /**
     * Traite la prochaine zone dans la file d'attente de backup de manière séquentielle.
     */
    private void processNextZoneInBackupQueue(Player initiator, Queue<Zone> zoneQueue, int total) {
        if (zoneQueue.isEmpty()) {
            initiator.sendMessage("§a§lSHOP §8» §aBackup de toutes les zones terminé !");
            return;
        }

        Zone zone = zoneQueue.poll();
        int processedCount = total - zoneQueue.size();

        initiator.sendMessage("§7§lSHOP §8» §7Backup de §e" + zone.getId() + " §7(§e" + processedCount + "§7/§e" + total + "§7)...");

        marketBackupManager.backupMarketZone(zone.getId(), null).thenAccept(result -> {
            // Le résultat de chaque backup est loggué dans la console
            // On peut notifier le joueur à la fin de chaque étape
            if (result.isSuccess()) {
                plugin.getLogger().info("Backup market réussi pour zone " + zone.getId() + " - Compression: " + result.getCompressionRatio());
            } else {
                plugin.getLogger().warning("Backup market échoué pour zone " + zone.getId() + " - " + result.getErrorMessage());
                initiator.sendMessage("§c§lSHOP §8» §cÉchec backup pour §e" + zone.getId() + ": " + result.getErrorMessage());
            }

            // Planifier le traitement de la prochaine zone sur le thread principal
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    processNextZoneInBackupQueue(initiator, zoneQueue, total);
                }
            }.runTask(plugin);
        });
    }

    private void handleMarketRestoreCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin marketrestore <zoneId>");
            return;
        }

        String zoneId = args[1];
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            sender.sendMessage("§c§lSHOP §8» §cZone introuvable: " + zoneId);
            return;
        }

        if (!marketBackupManager.hasMarketBackup(zoneId)) {
            sender.sendMessage("§c§lSHOP §8» §cAucun backup market trouvé pour la zone: " + zoneId);
            return;
        }

        // Afficher les infos du backup avant restauration
        long backupTime = marketBackupManager.getMarketBackupTimestamp(zoneId);
        String timeAgo = formatTimeAgo(System.currentTimeMillis() - backupTime);

        sender.sendMessage("§e§lSHOP §8» §eRestauration du backup market créé il y a §f" + timeAgo);

        marketBackupManager.restoreMarketZone(zoneId, player).thenAccept(result -> {
            // Le résultat est déjà envoyé au joueur dans le MarketZoneBackupManager
        });
    }

    private void handleMarketListCommand(CommandSender sender) {
        Set<String> backedUpZones = marketBackupManager.getMarketBackedUpZones();

        if (backedUpZones.isEmpty()) {
            sender.sendMessage("§c§lSHOP §8» §cAucun backup market trouvé!");
            return;
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ MARKET BACKUPS ▬▬▬▬▬▬▬");

        List<String> sortedZones = new ArrayList<>(backedUpZones);
        sortedZones.sort(String::compareTo);

        for (String zoneId : sortedZones) {
            long timestamp = marketBackupManager.getMarketBackupTimestamp(zoneId);
            String timeAgo = formatTimeAgo(System.currentTimeMillis() - timestamp);

            Zone zone = zoneManager.getZone(zoneId);
            String status = zone != null ? "§aActive" : "§cZone supprimée";

            sender.sendMessage("§e" + zoneId + " §7- " + status + " §7- Backup: §f" + timeAgo);
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§7Total: §e" + backedUpZones.size() + " §7backups market");
    }

    private void handleTeleportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin tp <zoneId>");
            return;
        }

        String zoneId = args[1];
        Zone zone = zoneManager.getZone(zoneId);
        if (zone == null) {
            sender.sendMessage("§c§lSHOP §8» §cZone introuvable: " + zoneId);
            return;
        }

        if (zone.hasTeleportLocation()) {
            Location teleportLoc = zone.getTeleportLocation();
            player.teleport(teleportLoc);
            player.sendMessage("§a§lSHOP §8» §aTéléporté à la zone §e" + zoneId + " §a(téléportation automatique)!");
        } else if (zone.getCenterLocation() != null) {
            Location centerLoc = zone.getCenterLocation();
            // Ajuster la Y pour être au-dessus du sol
            centerLoc.setY(centerLoc.getY() + 2);
            player.teleport(centerLoc);
            player.sendMessage("§a§lSHOP §8» §aTéléporté au centre de la zone §e" + zoneId + "§a!");
        } else {
            sender.sendMessage("§c§lSHOP §8» §cImpossible de déterminer la position de téléportation pour cette zone!");
        }
    }

    private String formatTimeAgo(long millisAgo) {
        long seconds = millisAgo / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " jour" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " heure" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return seconds + " seconde" + (seconds > 1 ? "s" : "");
        }
    }
}