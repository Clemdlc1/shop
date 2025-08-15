package fr.shop.listeners;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import fr.shop.managers.CommerceManager;
import fr.shop.managers.ShopManager;
import fr.shop.managers.ZoneManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listeners pour gérer les interactions avec les shops basés sur les zones
 */
public class ShopListeners implements Listener {

    private final PlayerShops plugin;
    private final ShopManager shopManager;
    private final CommerceManager commerceManager;
    private final ZoneManager zoneManager;

    public ShopListeners(PlayerShops plugin) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.commerceManager = plugin.getCommerceManager();
        this.zoneManager = plugin.getZoneManager();
    }

    // ===============================
    // PROTECTION DES SHOPS (ADAPTÉE ZONES)
    // ===============================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Vérifier si le joueur peut construire à cet emplacement
        if (!shopManager.canPlayerBuild(player, block.getLocation())) {
            event.setCancelled(true);

            Shop shop = shopManager.getShopAtLocation(block.getLocation());
            if (shop != null) {
                Shop.ShopStatus status = shop.getStatus();
                if (status == Shop.ShopStatus.AVAILABLE) {
                    player.sendMessage("§c§lSHOP §8» §cCe shop est libre! Utilisez §e/shop claim §cpour le revendiquer.");
                } else if (status == Shop.ShopStatus.GRACE_PERIOD) {
                    player.sendMessage("§c§lSHOP §8» §cCe shop est en période de grâce!");
                } else {
                    player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas construire ici!");
                }
            }
            return;
        }

        // Gestion spéciale des barrels
        if (block.getType() == Material.BARREL) {
            Shop shop = shopManager.getShopAtLocation(block.getLocation());
            if (shop != null && shop.isMember(player.getUniqueId())) {
                if (shop.hasBeacon()) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lSHOP §8» §cVous ne pouvez avoir qu'un seul Tank dans votre shop!");
                    return;
                } else {
                    // Permettre la pose et enregistrer
                    shopManager.placeBarrel(player, block.getLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Vérifier si le joueur peut détruire à cet emplacement
        if (!shopManager.canPlayerBreak(player, block.getLocation())) {
            event.setCancelled(true);

            Shop shop = shopManager.getShopAtLocation(block.getLocation());
            if (shop != null) {
                Shop.ShopStatus status = shop.getStatus();
                if (status == Shop.ShopStatus.AVAILABLE) {
                    player.sendMessage("§c§lSHOP §8» §cCe shop est libre! Utilisez §e/shop claim §cpour le revendiquer.");
                } else if (status == Shop.ShopStatus.GRACE_PERIOD) {
                    player.sendMessage("§c§lSHOP §8» §cCe shop est en période de grâce!");
                } else {
                    player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas détruire ici!");
                }
            }
            return;
        }

        // Gestion spéciale des coffres avec chest shop
        if (block.getType() == Material.CHEST) {
            if (commerceManager.isChestShop(block.getLocation())) {
                Shop shop = shopManager.getShopAtLocation(block.getLocation());
                if (shop != null && shop.isMember(player.getUniqueId())) {
                    commerceManager.removeChestShop(block.getLocation());
                    player.sendMessage("§a§lSHOP §8» §aChest shop supprimé!");
                } else {
                    event.setCancelled(true);
                    player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas détruire ce chest shop!");
                }
            }
        }

        // Gestion des panneaux de chest shop
        if (block.getState() instanceof Sign) {
            // Chercher un coffre adjacent
            Block adjacentChest = findAdjacentChest(block);
            if (adjacentChest != null && commerceManager.isChestShop(adjacentChest.getLocation())) {
                Shop shop = shopManager.getShopAtLocation(adjacentChest.getLocation());
                if (shop != null && shop.isMember(player.getUniqueId())) {
                    commerceManager.removeChestShop(adjacentChest.getLocation());
                    player.sendMessage("§a§lSHOP §8» §aChest shop supprimé!");
                } else {
                    event.setCancelled(true);
                    player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas détruire ce chest shop!");
                }
            }
        }
    }

    // ===============================
    // INTERACTIONS (ADAPTÉES ZONES)
    // ===============================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        Action action = event.getAction();

        // Gestion des chest shops
        if (block.getState() instanceof Sign) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true); // Empêcher la modification
                commerceManager.handleChestShopInteraction(player, block, true);
                return;
            } else if (action == Action.LEFT_CLICK_BLOCK) {
                // Clic gauche = interaction normale (achat/vente) ou modification (propriétaire)
                event.setCancelled(true); // Empêcher la casse du panneau
                commerceManager.handleChestShopInteraction(player, block, false);
                return;
            }
        }

        // Protection des coffres de chest shop contre l'ouverture directe
        if (action == Action.RIGHT_CLICK_BLOCK && block.getType() == Material.CHEST) {
            if (commerceManager.isChestShop(block.getLocation())) {
                Shop shop = shopManager.getShopAtLocation(block.getLocation());
                if (shop != null && !shop.isMember(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lSHOP §8» §cUtilisez le panneau pour interagir avec ce chest shop!");
                    return;
                }
            }
        }

        // Création de chest shop avec clic droit sur coffre + item
        if (action == Action.RIGHT_CLICK_BLOCK && block.getType() == Material.CHEST) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();

            if (itemInHand != null && itemInHand.getType() != Material.AIR) {
                // Vérifier que ce n'est pas une legendary pickaxe
                if (plugin.getPrisonTycoonHook().isLegendaryPickaxe(itemInHand)) {
                    return; // Ignorer silencieusement
                }

                // Vérifier si le joueur a un shop et est dans son shop
                Shop shop = shopManager.getPlayerShop(player.getUniqueId());
                if (shop != null && shop.containsLocation(block.getLocation(), zoneManager)) {
                    // Commencer la création si pas déjà un chest shop
                    if (!commerceManager.isChestShop(block.getLocation())) {
                        event.setCancelled(true);
                        // Appeler la nouvelle méthode avec le coffre directement
                        commerceManager.startChestShopCreation(player, itemInHand, block.getLocation());
                        return;
                    }
                }
            }
        }

        // Protection générale des coffres dans les shops
        if (action == Action.RIGHT_CLICK_BLOCK && block.getType() == Material.CHEST) {
            if (!commerceManager.canPlayerInteractWithChest(player, block.getLocation())) {
                // Vérifier si c'est un chest shop
                if (commerceManager.isChestShop(block.getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lSHOP §8» §cUtilisez le panneau pour interagir avec ce chest shop!");
                } else {
                    // Coffre normal dans un shop où le joueur n'a pas les permissions
                    Shop shop = shopManager.getShopAtLocation(block.getLocation());
                    if (shop != null && !shop.isMember(player.getUniqueId())) {
                        event.setCancelled(true);
                        player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas ouvrir ce coffre!");
                    }
                }
            }
        }
    }

    // ===============================
    // CHAT POUR PRIX ET MODIFICATIONS
    // ===============================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Gestion des créations de chest shop
        if (commerceManager.hasPendingCreation(player.getUniqueId())) {
            event.setCancelled(true);

            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                commerceManager.handlePriceInput(player, message);
            });
            return;
        }

        // Gestion des modifications de prix
        if (commerceManager.hasPendingPriceEdit(player.getUniqueId())) {
            event.setCancelled(true);

            String message = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                commerceManager.handlePriceEdit(player, message);
            });
        }
    }

    // ===============================
    // NETTOYAGE
    // ===============================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyer les créations en attente
        commerceManager.clearPendingCreation(player.getUniqueId());
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    private Block findAdjacentChest(Block sign) {
        org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.DOWN
        };

        for (org.bukkit.block.BlockFace face : faces) {
            Block relative = sign.getRelative(face);
            if (relative.getType() == Material.CHEST) {
                return relative;
            }
        }
        return null;
    }
}