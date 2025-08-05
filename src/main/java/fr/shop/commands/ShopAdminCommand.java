package fr.shop.commands;

import fr.shop.PlayerShops;
import fr.shop.data.Zone;
import fr.shop.managers.ShopBackupManager;
import fr.shop.managers.ZoneManager;
import fr.shop.managers.ZoneScanner;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commandes d'administration pour la gestion des zones et sauvegardes
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter {

    private final PlayerShops plugin;
    private final ZoneManager zoneManager;
    private final ShopBackupManager backupManager;

    public ShopAdminCommand(PlayerShops plugin) {
        this.plugin = plugin;
        this.zoneManager = plugin.getZoneManager();
        this.backupManager = plugin.getShopBackupManager();
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

            case "backup":
                handleBackupCommand(sender, args);
                break;

            case "restore":
                handleRestoreCommand(sender, args);
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

            case "help":
                sendAdminHelp(sender);
                break;

            default:
                sender.sendMessage("§c§lSHOP §8» §cCommande administrative inconnue! Utilisez §e/shopadmin help");
                break;
        }

        return true;
    }

    // ===============================
    // COMMANDE SCAN
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

        zoneManager.getScanner().scanWorld(world, player).thenAccept(result -> {
            if (result != null) {
                plugin.getLogger().info("Scan terminé: " + result);
            }
        });
    }

    // ===============================
    // COMMANDE ZONES
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

        for (Zone zone : zones) {
            String backupStatus = backupManager.hasBackup(zone.getId()) ? "§a✓" : "§c✗";
            sender.sendMessage("§e" + zone.getId() + " §7- §f" + zone.getBeaconCount() + " §7beacons, §f" +
                    zone.getBlockCount() + " §7blocs §8[" + backupStatus + "§8]");
        }

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§7Total: §e" + zones.size() + " §7zones");
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
        sender.sendMessage("§7Blocs: §e" + zone.getBlockCount());

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
        }

        boolean hasBackup = backupManager.hasBackup(zoneId);
        sender.sendMessage("§7Sauvegarde: " + (hasBackup ? "§a✓ Disponible" : "§c✗ Aucune"));

        if (hasBackup) {
            long timestamp = backupManager.getBackupTimestamp(zoneId);
            long hoursAgo = (System.currentTimeMillis() - timestamp) / (1000 * 60 * 60);
            sender.sendMessage("§7Dernière sauvegarde: §e" + hoursAgo + " §7heures");
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
    // COMMANDE BACKUP
    // ===============================

    private void handleBackupCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin backup <zone/all>");
            return;
        }

        String target = args[1];

        if (target.equalsIgnoreCase("all")) {
            backupManager.backupAllZones(player).thenAccept(results -> {
                long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
                plugin.getLogger().info("Sauvegarde de toutes les zones terminée: " + successful + "/" + results.size() + " succès");
            });
        } else {
            backupManager.backupZone(target, player).thenAccept(result -> {
                plugin.getLogger().info("Sauvegarde de la zone " + target + ": " +
                        (result.isSuccess() ? "succès" : "échec - " + result.getErrorMessage()));
            });
        }
    }

    // ===============================
    // COMMANDE RESTORE
    // ===============================

    private void handleRestoreCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§lSHOP §8» §cCette commande ne peut être utilisée que par un joueur!");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            sender.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shopadmin restore <zoneId>");
            return;
        }

        String zoneId = args[1];

        backupManager.restoreZone(zoneId, player).thenAccept(result -> {
            plugin.getLogger().info("Restauration de la zone " + zoneId + ": " +
                    (result.isSuccess() ? "succès" : "échec - " + result.getErrorMessage()));
        });
    }

    // ===============================
    // AUTRES COMMANDES
    // ===============================

    private void handleValidateCommand(CommandSender sender) {
        sender.sendMessage("§a§lSHOP §8» §aValidation des zones en cours...");

        ZoneManager.ValidationResult result = zoneManager.validateZones();

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ VALIDATION DES ZONES ▬▬▬▬▬▬▬");
        sender.sendMessage("§7Zones valides: §a" + result.getValidZones());
        sender.sendMessage("§7Zones invalides: §c" + result.getInvalidZones());

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

        zoneManager.loadZones();
        plugin.getConfigManager().reloadConfig();

        sender.sendMessage("§a§lSHOP §8» §aRechargement terminé!");
    }

    private void handleStatsCommand(CommandSender sender) {
        ZoneManager.ZoneStats stats = zoneManager.getStats();

        sender.sendMessage("§6§l▬▬▬▬▬▬▬ STATISTIQUES ZONES ▬▬▬▬▬▬▬");
        sender.sendMessage("§7Total zones: §e" + stats.getTotalZones());
        sender.sendMessage("§7Total beacons: §e" + stats.getTotalBeacons());
        sender.sendMessage("§7Total blocs: §e" + stats.getTotalBlocks());

        if (!stats.getWorldCounts().isEmpty()) {
            sender.sendMessage("§7Par monde:");
            for (var entry : stats.getWorldCounts().entrySet()) {
                sender.sendMessage("§8  - §e" + entry.getKey() + "§7: §f" + entry.getValue() + " §7zones");
            }
        }

        int backedUp = (int) zoneManager.getAllZones().stream()
                .mapToLong(z -> backupManager.hasBackup(z.getId()) ? 1 : 0)
                .sum();
        sender.sendMessage("§7Zones sauvegardées: §a" + backedUp + "§7/§e" + stats.getTotalZones());

        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§6§l▬▬▬▬▬▬▬ SHOP ADMIN HELP ▬▬▬▬▬▬▬");
        sender.sendMessage("§e/shopadmin scan <monde> §7- Scanner les beacons d'un monde");
        sender.sendMessage("§e/shopadmin zones list [monde] §7- Lister les zones");
        sender.sendMessage("§e/shopadmin zones info <zoneId> §7- Info d'une zone");
        sender.sendMessage("§e/shopadmin zones delete <zoneId> §7- Supprimer une zone");
        sender.sendMessage("§e/shopadmin backup <zone/all> §7- Sauvegarder une zone");
        sender.sendMessage("§e/shopadmin restore <zoneId> §7- Restaurer une zone");
        sender.sendMessage("§e/shopadmin validate §7- Valider l'intégrité des zones");
        sender.sendMessage("§e/shopadmin reload §7- Recharger les configurations");
        sender.sendMessage("§e/shopadmin stats §7- Statistiques des zones");
        sender.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("playershops.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("scan", "zones", "backup", "restore", "validate", "reload", "stats", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "scan":
                    completions.addAll(Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .collect(Collectors.toList()));
                    break;

                case "zones":
                    completions.addAll(Arrays.asList("list", "info", "delete"));
                    break;

                case "backup":
                    completions.add("all");
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .collect(Collectors.toList()));
                    break;

                case "restore":
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .filter(id -> backupManager.hasBackup(id))
                            .collect(Collectors.toList()));
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();

            if ("zones".equals(subCommand)) {
                if ("list".equals(action)) {
                    completions.addAll(Bukkit.getWorlds().stream()
                            .map(World::getName)
                            .collect(Collectors.toList()));
                } else if ("info".equals(action) || "delete".equals(action)) {
                    completions.addAll(zoneManager.getAllZones().stream()
                            .map(Zone::getId)
                            .collect(Collectors.toList()));
                }
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}