package fr.shop.commands;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import fr.shop.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * Commande principale pour gérer les shops basés sur les zones
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final PlayerShops plugin;

    public ShopCommand(PlayerShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
            case "liste":
                handleListCommand(player);
                break;

            case "claim":
            case "revendiquer":
                handleClaimCommand(player);
                break;

            case "extend":
            case "prolonger":
                handleExtendCommand(player);
                break;

            case "info":
                handleInfoCommand(player);
                break;

            case "tp":
            case "teleport":
                handleTeleportCommand(player, args);
                break;

            case "member":
            case "membre":
                handleMemberCommand(player, args);
                break;

            case "customize":
            case "personnaliser":
                handleCustomizeCommand(player, args);
                break;

            case "remove":
            case "supprimer":
                handleRemoveCommand(player, args);
                break;

            case "ad":
            case "annonce":
                handleAdvertisementCommand(player, args);
                break;

            case "boost":
                handleBoostCommand(player);
                break;

            case "zone":
                handleZoneCommand(player, args);
                break;

            case "help":
            case "aide":
                sendHelp(player);
                break;

            default:
                player.sendMessage("§c§lSHOP §8» §cCommande inconnue! Utilisez §e/shop help §cpour voir l'aide.");
                break;
        }

        return true;
    }

    private void handleListCommand(Player player) {
        plugin.getShopGUI().openShopListGUI(player);
    }

    private void handleClaimCommand(Player player) {
        // Vérifier si le joueur est dans un shop
        Shop shop = plugin.getShopManager().getShopAtLocation(player.getLocation());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous devez être dans un shop pour le revendiquer!");
            return;
        }

        if (shop.isRented()) {
            if (shop.getStatus() == Shop.ShopStatus.GRACE_PERIOD) {
                if (shop.isMember(player.getUniqueId())) {
                    player.sendMessage("§e§lSHOP §8» §eCe shop est en période de grâce. Seuls les membres peuvent y accéder.");
                } else {
                    player.sendMessage("§c§lSHOP §8» §cCe shop est en période de grâce!");
                }
            } else {
                player.sendMessage("§c§lSHOP §8» §cCe shop est déjà loué par §e" + shop.getOwnerName() + "§c!");
            }
            return;
        }

        plugin.getShopManager().claimShop(player, shop.getId());
    }

    private void handleExtendCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        plugin.getShopManager().extendShopRent(player);
    }

    private void handleInfoCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        Zone zone = shop.getZone(plugin.getZoneManager());

        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬ SHOP INFO ▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7ID: §e" + shop.getId());
        player.sendMessage("§7Zone: §e" + shop.getZoneId());
        player.sendMessage("§7Propriétaire: §e" + shop.getOwnerName());
        player.sendMessage("§7Statut: " + getStatusColor(shop.getStatus()) + getStatusName(shop.getStatus()));

        if (zone != null) {
            player.sendMessage("§7Beacons: §e" + zone.getBeaconCount());
            player.sendMessage("§7Taille: §e" + zone.getBlockCount() + " §7blocs");
            if (zone.hasTeleportLocation()) {
                player.sendMessage("§7Téléportation: §aDisponible");
            }
        }

        if (shop.isRented()) {
            player.sendMessage("§7Expire dans: §e" + formatTimeRemaining(shop.getRentExpiry()));
            if (shop.getStatus() == Shop.ShopStatus.GRACE_PERIOD) {
                player.sendMessage("§7Fin de grâce: §c" + formatTimeRemaining(shop.getGraceExpiry()));
            }
        }

        player.sendMessage("§7Membres: §e" + shop.getMembers().size());
        player.sendMessage("§7Chest shops: §e" + shop.getChestShops().size());

        if (shop.getCustomMessage() != null) {
            player.sendMessage("§7Message: §f" + ChatColor.translateAlternateColorCodes('&', shop.getCustomMessage()));
        }

        if (!shop.getFloatingTexts().isEmpty()) {
            player.sendMessage("§7Textes flottants: §a" + shop.getFloatingTexts().size() + "§7/§a3");
        }

        if (shop.hasNPC()) {
            player.sendMessage("§7PNJ: §a" + ChatColor.translateAlternateColorCodes('&', shop.getNpcName()));
        }

        if (shop.hasBeacon()) {
            player.sendMessage("§7Beacon: §aPlacé");
        }

        if (shop.getAdvertisement() != null) {
            player.sendMessage("§7Annonce: §a" + (shop.getAdvertisement().isActive() ? "Active" : "Inactive"));
            if (shop.isAdvertisementBoosted()) {
                player.sendMessage("§7Boost: §a§lACTIF");
            }
        }

        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void handleTeleportCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop tp <joueur>");
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            // Chercher par nom de shop ou propriétaire
            Shop targetShop = null;
            for (Shop shop : plugin.getShopManager().getRentedShops()) {
                if (shop.getOwnerName().equalsIgnoreCase(targetName) || shop.getId().equalsIgnoreCase(targetName)) {
                    targetShop = shop;
                    break;
                }
            }

            if (targetShop == null) {
                player.sendMessage("§c§lSHOP §8» §cShop introuvable!");
                return;
            }

            teleportToShop(player, targetShop);
        } else {
            Shop targetShop = plugin.getShopManager().getPlayerShop(target.getUniqueId());
            if (targetShop == null) {
                player.sendMessage("§c§lSHOP §8» §cCe joueur n'a pas de shop!");
                return;
            }

            teleportToShop(player, targetShop);
        }
    }

    private void teleportToShop(Player player, Shop shop) {
        Zone zone = shop.getZone(plugin.getZoneManager());
        if (zone == null) {
            player.sendMessage("§c§lSHOP §8» §cErreur: Zone du shop introuvable!");
            return;
        }

        // Utiliser la téléportation de la zone si disponible, sinon le centre
        if (zone.hasTeleportLocation()) {
            player.teleport(zone.getTeleportLocation());
            player.sendMessage("§a§lSHOP §8» §aTéléporté au shop de §e" + shop.getOwnerName() + "§a! (Point de téléportation)");
        } else if (zone.getCenterLocation() != null) {
            player.teleport(zone.getCenterLocation());
            player.sendMessage("§a§lSHOP §8» §aTéléporté au shop de §e" + shop.getOwnerName() + "§a! (Centre)");
        } else {
            player.sendMessage("§c§lSHOP §8» §cImpossible de déterminer la position de téléportation!");
        }
    }

    private void handleMemberCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop member <add/remove> <joueur>");
            return;
        }

        String action = args[1].toLowerCase();
        String memberName = args[2];

        switch (action) {
            case "add":
            case "ajouter":
                plugin.getShopManager().addMember(player, memberName);
                break;

            case "remove":
            case "supprimer":
                plugin.getShopManager().removeMember(player, memberName);
                break;

            default:
                player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop member <add/remove> <joueur>");
                break;
        }
    }

    private void handleCustomizeCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendCustomizeHelp(player);
            return;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "message":
                if (args.length < 3) {
                    player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop customize message <texte>");
                    return;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getShopManager().addCustomMessage(player, message);
                break;

            case "text":
            case "texte":
                if (args.length < 3) {
                    player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop customize text <texte>");
                    return;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getShopManager().addFloatingText(player, text);
                break;

            case "npc":
                if (args.length < 3) {
                    player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop customize npc <nom>");
                    return;
                }
                String npcName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                plugin.getShopManager().addNPC(player, npcName);
                break;

            default:
                sendCustomizeHelp(player);
                break;
        }
    }

    private void handleRemoveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop remove <message/text/npc> [index]");
            return;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "message":
                plugin.getShopManager().removeCustomMessage(player);
                break;

            case "text":
            case "texte":
                if (args.length < 3) {
                    plugin.getShopManager().listFloatingTexts(player);
                    return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1; // L'utilisateur tape 1-3, on convertit en 0-2
                    plugin.getShopManager().removeFloatingText(player, index);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c§lSHOP §8» §cIndex invalide!");
                }
                break;

            case "npc":
                plugin.getShopManager().removeNPC(player);
                break;

            default:
                player.sendMessage("§c§lSHOP §8» §cUtilisation: §e/shop remove <message/text/npc> [index]");
                break;
        }
    }

    private void handleAdvertisementCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendAdvertisementHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create":
            case "creer":
                plugin.getShopGUI().openCreateAdvertisementGUI(player);
                break;

            case "edit":
            case "modifier":
                plugin.getShopGUI().openEditAdvertisementGUI(player);
                break;

            case "view":
            case "voir":
                plugin.getShopGUI().openAdvertisementListGUI(player);
                break;

            case "toggle":
                toggleAdvertisement(player);
                break;

            default:
                sendAdvertisementHelp(player);
                break;
        }
    }

    private void handleBoostCommand(Player player) {
        plugin.getShopManager().boostAdvertisement(player);
    }

    private void handleZoneCommand(Player player, String[] args) {
        if (args.length < 2) {
            sendZoneHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "info":
                handleZoneInfoCommand(player);
                break;

            case "tp":
            case "teleport":
                handleZoneTeleportCommand(player);
                break;

            case "center":
            case "centre":
                handleZoneCenterCommand(player);
                break;

            case "size":
            case "taille":
                handleZoneSizeCommand(player);
                break;

            default:
                sendZoneHelp(player);
                break;
        }
    }

    private void handleZoneInfoCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        Zone zone = shop.getZone(plugin.getZoneManager());
        if (zone == null) {
            player.sendMessage("§c§lSHOP §8» §cZone de votre shop introuvable!");
            return;
        }

        player.sendMessage("§6§l▬▬▬▬▬▬▬ ZONE INFO ▬▬▬▬▬▬▬");
        player.sendMessage("§7ID de zone: §e" + zone.getId());
        player.sendMessage("§7Monde: §e" + zone.getWorldName());
        player.sendMessage("§7Beacons: §e" + zone.getBeaconCount());
        player.sendMessage("§7Blocs totaux: §e" + zone.getBlockCount());

        if (zone.getCenterLocation() != null) {
            var center = zone.getCenterLocation();
            player.sendMessage("§7Centre: §e" + (int)center.getX() + ", " + (int)center.getY() + ", " + (int)center.getZ());
        }

        if (zone.hasTeleportLocation()) {
            var teleport = zone.getRawTeleportLocation();
            player.sendMessage("§7Téléportation: §a" + (int)teleport.getX() + ", " + (int)teleport.getY() + ", " + (int)teleport.getZ());
            player.sendMessage("§7Orientation: §e" + zone.getTeleportYaw() + "° (yaw)");
        } else {
            player.sendMessage("§7Téléportation: §cNon définie");
        }

        Zone.BoundingBox bounds = zone.getBoundingBox();
        if (bounds != null) {
            player.sendMessage("§7Limites: §e(" + bounds.minX + "," + bounds.minY + "," + bounds.minZ + ") à (" + bounds.maxX + "," + bounds.maxY + "," + bounds.maxZ + ")");
        }

        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void handleZoneTeleportCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        Zone zone = shop.getZone(plugin.getZoneManager());
        if (zone == null) {
            player.sendMessage("§c§lSHOP §8» §cZone de votre shop introuvable!");
            return;
        }

        if (zone.hasTeleportLocation()) {
            player.teleport(zone.getTeleportLocation());
            player.sendMessage("§a§lSHOP §8» §aTéléporté au point de téléportation de votre zone!");
        } else if (zone.getCenterLocation() != null) {
            player.teleport(zone.getCenterLocation());
            player.sendMessage("§a§lSHOP §8» §aTéléporté au centre de votre zone!");
        } else {
            player.sendMessage("§c§lSHOP §8» §cImpossible de déterminer la position de téléportation!");
        }
    }

    private void handleZoneCenterCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        Zone zone = shop.getZone(plugin.getZoneManager());
        if (zone == null) {
            player.sendMessage("§c§lSHOP §8» §cZone de votre shop introuvable!");
            return;
        }

        if (zone.getCenterLocation() != null) {
            player.teleport(zone.getCenterLocation());
            player.sendMessage("§a§lSHOP §8» §aTéléporté au centre de votre zone!");
        } else {
            player.sendMessage("§c§lSHOP §8» §cCentre de zone introuvable!");
        }
    }

    private void handleZoneSizeCommand(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        Zone zone = shop.getZone(plugin.getZoneManager());
        if (zone == null) {
            player.sendMessage("§c§lSHOP §8» §cZone de votre shop introuvable!");
            return;
        }

        Zone.BoundingBox bounds = zone.getBoundingBox();
        if (bounds != null) {
            int width = bounds.maxX - bounds.minX + 1;
            int height = bounds.maxY - bounds.minY + 1;
            int depth = bounds.maxZ - bounds.minZ + 1;

            player.sendMessage("§6§lSHOP §8» §6Dimensions de votre zone:");
            player.sendMessage("§7Largeur (X): §e" + width + " §7blocs");
            player.sendMessage("§7Hauteur (Y): §e" + height + " §7blocs");
            player.sendMessage("§7Profondeur (Z): §e" + depth + " §7blocs");
            player.sendMessage("§7Volume total: §e" + (width * height * depth) + " §7blocs");
            player.sendMessage("§7Beacons: §e" + zone.getBeaconCount());
        } else {
            player.sendMessage("§c§lSHOP §8» §cImpossible de calculer les dimensions de la zone!");
        }
    }

    private void toggleAdvertisement(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        if (shop.getAdvertisement() == null) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas d'annonce!");
            return;
        }

        boolean newState = !shop.getAdvertisement().isActive();
        shop.getAdvertisement().setActive(newState);

        player.sendMessage("§a§lSHOP §8» §aAnnonce " + (newState ? "activée" : "désactivée") + "!");
        plugin.getShopManager().saveAll();
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬ SHOP HELP ▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e/shop list §7- Liste tous les shops");
        player.sendMessage("§e/shop claim §7- Revendiquer le shop où vous êtes");
        player.sendMessage("§e/shop extend §7- Prolonger votre location");
        player.sendMessage("§e/shop info §7- Informations sur votre shop");
        player.sendMessage("§e/shop tp <joueur> §7- Se téléporter à un shop");
        player.sendMessage("§e/shop member <add/remove> <joueur> §7- Gérer les membres");
        player.sendMessage("§e/shop customize §7- Personnaliser votre shop");
        player.sendMessage("§e/shop remove §7- Supprimer des éléments");
        player.sendMessage("§e/shop ad §7- Gérer les annonces");
        player.sendMessage("§e/shop boost §7- Booster votre annonce");
        player.sendMessage("§e/shop zone §7- Informations sur votre zone");
        player.sendMessage("§7§o(Pour créer un chest shop: clic droit sur un coffre avec un item, prix en coins)");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendCustomizeHelp(Player player) {
        player.sendMessage("§6§l▬▬▬▬▬▬▬ SHOP CUSTOMIZE ▬▬▬▬▬▬▬");
        player.sendMessage("§e/shop customize message <texte> §7- Message d'approche");
        player.sendMessage("§e/shop customize text <texte> §7- Texte flottant (max 3)");
        player.sendMessage("§e/shop customize npc <nom> §7- Ajouter un PNJ");
        player.sendMessage("§7§oUtilisez § pour les codes couleur");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendAdvertisementHelp(Player player) {
        player.sendMessage("§6§l▬▬▬▬▬▬ SHOP ADVERTISEMENT ▬▬▬▬▬▬");
        player.sendMessage("§e/shop ad create §7- Créer une annonce");
        player.sendMessage("§e/shop ad edit §7- Modifier votre annonce");
        player.sendMessage("§e/shop ad view §7- Voir toutes les annonces");
        player.sendMessage("§e/shop ad toggle §7- Activer/désactiver votre annonce");
        player.sendMessage("§e/shop boost §7- Booster votre annonce (1h)");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendZoneHelp(Player player) {
        player.sendMessage("§6§l▬▬▬▬▬▬▬ SHOP ZONE ▬▬▬▬▬▬▬");
        player.sendMessage("§e/shop zone info §7- Informations détaillées sur votre zone");
        player.sendMessage("§e/shop zone tp §7- Se téléporter à votre zone");
        player.sendMessage("§e/shop zone center §7- Aller au centre de votre zone");
        player.sendMessage("§e/shop zone size §7- Voir les dimensions de votre zone");
        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private String getStatusColor(Shop.ShopStatus status) {
        return switch (status) {
            case AVAILABLE -> "§a";
            case RENTED -> "§2";
            case GRACE_PERIOD -> "§e";
            case EXPIRED -> "§c";
        };
    }

    private String getStatusName(Shop.ShopStatus status) {
        return switch (status) {
            case AVAILABLE -> "Disponible";
            case RENTED -> "Loué";
            case GRACE_PERIOD -> "Période de grâce";
            case EXPIRED -> "Expiré";
        };
    }

    private String formatTimeRemaining(long timestamp) {
        long remaining = timestamp - System.currentTimeMillis();
        if (remaining <= 0) {
            return "§cExpiré";
        }

        long days = remaining / (24 * 60 * 60 * 1000);
        long hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);

        if (days > 0) {
            return days + " jour" + (days > 1 ? "s" : "") + " " + hours + "h";
        } else if (hours > 0) {
            return hours + " heure" + (hours > 1 ? "s" : "") + " " + minutes + "min";
        } else {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("list", "claim", "extend", "info", "tp", "member", "customize", "remove", "ad", "boost", "zone", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "tp":
                case "teleport":
                    completions.addAll(plugin.getShopManager().getRentedShops().stream()
                            .map(Shop::getOwnerName)
                            .toList());
                    break;

                case "member":
                case "membre":
                    completions.addAll(Arrays.asList("add", "remove"));
                    break;

                case "customize":
                case "personnaliser":
                    completions.addAll(Arrays.asList("message", "text", "npc"));
                    break;

                case "remove":
                case "supprimer":
                    completions.addAll(Arrays.asList("message", "text", "npc"));
                    break;

                case "ad":
                case "annonce":
                    completions.addAll(Arrays.asList("create", "edit", "view", "toggle"));
                    break;

                case "zone":
                    completions.addAll(Arrays.asList("info", "tp", "center", "size"));
                    break;
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();

            if (("member".equals(subCommand) || "membre".equals(subCommand)) &&
                    ("add".equals(action) || "remove".equals(action))) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .toList());
            } else if (("remove".equals(subCommand) || "supprimer".equals(subCommand)) &&
                    ("text".equals(action) || "texte".equals(action))) {
                completions.addAll(Arrays.asList("1", "2", "3"));
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}