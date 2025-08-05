package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.ChestShop;
import fr.shop.data.Shop;
import fr.shop.hooks.PrisonTycoonHook;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de commerce avec coffres et panneaux
 */
public class CommerceManager {

    private final PlayerShops plugin;
    private final PrisonTycoonHook hook;
    private final Map<Location, ChestShop> chestShops;
    private final Map<UUID, PendingShopCreation> pendingCreations;
    private final Map<UUID, PendingPriceEdit> pendingPriceEdits;

    // Keys pour les métadonnées
    private final NamespacedKey chestShopKey;
    private final NamespacedKey priceKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey sellModeKey;

    public CommerceManager(PlayerShops plugin) {
        this.plugin = plugin;
        this.hook = plugin.getPrisonTycoonHook();
        this.chestShops = new ConcurrentHashMap<>();
        this.pendingCreations = new ConcurrentHashMap<>();
        this.pendingPriceEdits = new ConcurrentHashMap<>();

        // Initialiser les keys
        this.chestShopKey = new NamespacedKey(plugin, "chestshop");
        this.priceKey = new NamespacedKey(plugin, "price");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.itemKey = new NamespacedKey(plugin, "item");
        this.sellModeKey = new NamespacedKey(plugin, "sellmode");

        loadChestShops();
    }

    /**
     * Parse le prix depuis le texte formaté
     */
    private static long parsePrice(String priceText) {
        priceText = priceText.trim().toUpperCase();

        if (priceText.endsWith("T")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000000000L;
        } else if (priceText.endsWith("B")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000000L;
        } else if (priceText.endsWith("M")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000000L;
        } else if (priceText.endsWith("K")) {
            return Long.parseLong(priceText.substring(0, priceText.length() - 1)) * 1000L;
        } else {
            return Long.parseLong(priceText);
        }
    }

    private void loadChestShops() {
        // Charger les chest shops depuis les shops existants
        ShopManager shopManager = plugin.getShopManager();
        for (Shop shop : shopManager.getAllShops()) {
            for (Location chestLoc : shop.getChestShops()) {
                loadChestShopFromSign(chestLoc);
            }
        }
    }

    // ===============================
    // CRÉATION DE CHEST SHOP
    // ===============================

    private void loadChestShopFromSign(Location chestLocation) {
        Block chestBlock = chestLocation.getBlock();
        if (chestBlock.getType() != Material.CHEST) return;

        // Chercher un panneau adjacent
        Block signBlock = findAdjacentSign(chestBlock);
        if (signBlock == null) return;

        Sign sign = (Sign) signBlock.getState();
        String[] lines = sign.getLines();

        try {
            ChestShop chestShop = ChestShop.fromSignLines(lines, chestLocation);
            if (chestShop != null) {
                chestShops.put(chestLocation, chestShop);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du chargement du chest shop à " + chestLocation + ": " + e.getMessage());
        }
    }

    /**
     * Démarre la création d'un chest shop directement sur le coffre cliqué
     */
    public void startChestShopCreation(Player player, ItemStack item, Location chestLocation) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c§lSHOP §8» §cVous devez tenir un item valide!");
            return;
        }

        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        if (!shop.containsLocation(chestLocation)) {
            player.sendMessage("§c§lSHOP §8» §cCe coffre doit être dans votre shop!");
            return;
        }

        if (chestShops.containsKey(chestLocation)) {
            player.sendMessage("§c§lSHOP §8» §cCe coffre a déjà un chest shop!");
            return;
        }

        // Vérifier qu'il y a de la place pour un panneau
        if (findBestSignLocation(chestLocation.getBlock()) == null) {
            player.sendMessage("§c§lSHOP §8» §cPas de place pour placer un panneau adjacent au coffre!");
            return;
        }

        // Vérifier si c'est une legendary pickaxe
        if (hook.isLegendaryPickaxe(item)) {
            player.sendMessage("§c§lSHOP §8» §cVous ne pouvez pas vendre une pioche légendaire!");
            return;
        }

        PendingShopCreation pending = new PendingShopCreation(player.getUniqueId(), item.clone());
        pending.setChestLocation(chestLocation); // Définir directement le coffre
        pendingCreations.put(player.getUniqueId(), pending);

        // Afficher le nom de l'item avec couleurs si renommé
        String itemDisplay = getItemDisplayName(item);

        player.sendMessage("§a§lSHOP §8» §aCoffre sélectionné pour: " + itemDisplay);
        player.sendMessage("§7§lSHOP §8» §7Tapez maintenant le prix et mode:");
        player.sendMessage("§e§lSHOP §8» §eFormat: <prix> [buy/sell] - Exemple: §f1000 buy §8(Tapez §ccancel §8pour annuler)");
    }

    public void handlePriceInput(Player player, String input) {
        PendingShopCreation pending = pendingCreations.get(player.getUniqueId());
        if (pending == null) return;

        if (input.equalsIgnoreCase("cancel")) {
            pendingCreations.remove(player.getUniqueId());
            player.sendMessage("§c§lSHOP §8» §cCréation de chest shop annulée!");
            return;
        }

        // Vérifier que le coffre est toujours défini
        if (pending.getChestLocation() == null) {
            pendingCreations.remove(player.getUniqueId());
            player.sendMessage("§c§lSHOP §8» §cErreur: Coffre non défini! Veuillez recommencer.");
            return;
        }

        try {
            String[] parts = input.split(" ");
            long price = Long.parseLong(parts[0]);

            if (price <= 0) {
                player.sendMessage("§c§lSHOP §8» §cLe prix doit être positif!");
                return;
            }

            boolean sellMode = false;
            if (parts.length > 1 && parts[1].equalsIgnoreCase("sell")) {
                sellMode = true;
            }

            pending.setPrice(price);
            pending.setSellMode(sellMode);
            createChestShop(player, pending);
            pendingCreations.remove(player.getUniqueId());

        } catch (NumberFormatException e) {
            player.sendMessage("§c§lSHOP §8» §cFormat invalide! Utilisez: §e<prix en coins> [buy/sell]");
        }
    }

    // ===============================
    // MODIFICATION DE PRIX
    // ===============================

    private void createChestShop(Player player, PendingShopCreation pending) {
        Location chestLoc = pending.getChestLocation();

        // Vérification de sécurité
        if (chestLoc == null) {
            player.sendMessage("§c§lSHOP §8» §cErreur: Emplacement du coffre invalide!");
            return;
        }

        Block chestBlock = chestLoc.getBlock();

        if (chestBlock.getType() != Material.CHEST) {
            player.sendMessage("§c§lSHOP §8» §cErreur: Le bloc n'est plus un coffre!");
            return;
        }

        // Trouver le meilleur emplacement pour le panneau
        Location signLoc = findBestSignLocation(chestBlock);
        if (signLoc == null) {
            player.sendMessage("§c§lSHOP §8» §cImpossible de placer le panneau!");
            return;
        }

        // Créer le panneau
        Block signBlock = signLoc.getBlock();
        BlockFace attachedFace = getAttachedFace(signLoc, chestLoc);

        if (attachedFace != null) {
            // Panneau mural
            signBlock.setType(Material.OAK_WALL_SIGN);
            WallSign wallSign = (WallSign) signBlock.getBlockData();
            wallSign.setFacing(attachedFace.getOppositeFace());
            signBlock.setBlockData(wallSign);
        } else {
            // Panneau sur poteau (au-dessus du coffre)
            signBlock.setType(Material.OAK_SIGN);
        }

        // Configurer le panneau
        Sign sign = (Sign) signBlock.getState();
        ChestShop chestShop = new ChestShop(
                chestLoc,
                player.getUniqueId(),
                player.getName(),
                pending.getItem(),
                pending.getPrice(),
                pending.isSellMode()
        );

        String[] lines = chestShop.toSignLines();
        for (int i = 0; i < lines.length; i++) {
            sign.setLine(i, lines[i]);
        }
        sign.update();

        // Ajouter les métadonnées persistantes
        PersistentDataContainer container = sign.getPersistentDataContainer();
        container.set(chestShopKey, PersistentDataType.STRING, "true");
        container.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        container.set(priceKey, PersistentDataType.LONG, pending.getPrice());
        container.set(itemKey, PersistentDataType.STRING, itemToString(pending.getItem()));
        container.set(sellModeKey, PersistentDataType.BOOLEAN, pending.isSellMode());
        sign.update();

        // Enregistrer le chest shop
        chestShops.put(chestLoc, chestShop);

        // Ajouter à la liste du shop
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop != null) {
            shop.addChestShop(chestLoc);
            plugin.getShopManager().saveAll();
        }

        String mode = pending.isSellMode() ? "vente" : "achat";
        String itemDisplay = getItemDisplayName(pending.getItem());
        player.sendMessage("§a§lSHOP §8» §aChest shop de " + mode + " créé!");
        player.sendMessage("§7§lSHOP §8» §7Item: " + itemDisplay + " §7x§f" + pending.getItem().getAmount());
        player.sendMessage("§7§lSHOP §8» §7Prix: §e" + formatPrice(pending.getPrice()) + " §7coins");
        player.sendMessage("§7§lSHOP §8» §7Clic gauche = modifier, Clic droit = supprimer");
    }

    public void startPriceEdit(Player player, Block signBlock) {
        if (!(signBlock.getState() instanceof Sign)) return;

        Sign sign = (Sign) signBlock.getState();
        PersistentDataContainer container = sign.getPersistentDataContainer();

        if (!container.has(chestShopKey, PersistentDataType.STRING)) return;

        try {
            UUID ownerId = UUID.fromString(container.get(ownerKey, PersistentDataType.STRING));

            if (!player.getUniqueId().equals(ownerId)) {
                player.sendMessage("§c§lSHOP §8» §cVous ne pouvez modifier que vos propres chest shops!");
                return;
            }

            long currentPrice = container.get(priceKey, PersistentDataType.LONG);
            boolean currentSellMode = container.getOrDefault(sellModeKey, PersistentDataType.BOOLEAN, false);

            Block chestBlock = findAdjacentChest(signBlock);
            if (chestBlock == null) {
                player.sendMessage("§c§lSHOP §8» §cErreur: Coffre introuvable!");
                return;
            }

            PendingPriceEdit pending = new PendingPriceEdit(
                    chestBlock.getLocation(),
                    signBlock.getLocation(),
                    currentPrice,
                    currentSellMode
            );
            pendingPriceEdits.put(player.getUniqueId(), pending);

            String mode = currentSellMode ? "vente" : "achat";
            player.sendMessage("§a§lSHOP §8» §aModification du chest shop (§e" + mode + "§a):");
            player.sendMessage("§7§lSHOP §8» §7Prix actuel: §e" + formatPrice(currentPrice) + " §7coins");
            player.sendMessage("§7§lSHOP §8» §7Tapez le nouveau prix ou §eswitch §7pour changer le mode:");

        } catch (Exception e) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la lecture du chest shop!");
        }
    }

    public void handlePriceEdit(Player player, String input) {
        PendingPriceEdit pending = pendingPriceEdits.get(player.getUniqueId());
        if (pending == null) return;

        if (input.equalsIgnoreCase("cancel")) {
            pendingPriceEdits.remove(player.getUniqueId());
            player.sendMessage("§c§lSHOP §8» §cModification annulée!");
            return;
        }

        if (input.equalsIgnoreCase("switch")) {
            // Changer le mode achat/vente
            pending.setSellMode(!pending.isSellMode());
            updateChestShopSign(pending);
            pendingPriceEdits.remove(player.getUniqueId());

            String newMode = pending.isSellMode() ? "vente" : "achat";
            player.sendMessage("§a§lSHOP §8» §aMode changé en §e" + newMode + "§a!");
            return;
        }

        try {
            long newPrice = Long.parseLong(input);

            if (newPrice <= 0) {
                player.sendMessage("§c§lSHOP §8» §cLe prix doit être positif!");
                return;
            }

            pending.setPrice(newPrice);
            updateChestShopSign(pending);
            pendingPriceEdits.remove(player.getUniqueId());

            player.sendMessage("§a§lSHOP §8» §aPrix modifié: §e" + formatPrice(newPrice) + " §7coins");

        } catch (NumberFormatException e) {
            player.sendMessage("§c§lSHOP §8» §cPrix invalide! Utilisez §eswitch §cpour changer le mode ou tapez un prix en coins.");
        }
    }

    // ===============================
    // ACHAT/VENTE ET INFORMATIONS
    // ===============================

    private void updateChestShopSign(PendingPriceEdit pending) {
        Block signBlock = pending.getSignLocation().getBlock();
        if (!(signBlock.getState() instanceof Sign)) return;

        Sign sign = (Sign) signBlock.getState();
        ChestShop chestShop = chestShops.get(pending.getChestLocation());

        if (chestShop != null) {
            // Mettre à jour le chest shop
            ChestShop updatedShop = new ChestShop(
                    chestShop.getChestLocation(),
                    chestShop.getOwnerId(),
                    chestShop.getOwnerName(),
                    chestShop.getItem(),
                    pending.getPrice(),
                    pending.isSellMode()
            );

            chestShops.put(pending.getChestLocation(), updatedShop);

            // Mettre à jour le panneau
            String[] lines = updatedShop.toSignLines();
            for (int i = 0; i < lines.length; i++) {
                sign.setLine(i, lines[i]);
            }

            // Mettre à jour les métadonnées
            PersistentDataContainer container = sign.getPersistentDataContainer();
            container.set(priceKey, PersistentDataType.LONG, pending.getPrice());
            container.set(sellModeKey, PersistentDataType.BOOLEAN, pending.isSellMode());

            sign.update();
        }
    }

    public void handleChestShopInteraction(Player player, Block signBlock, boolean rightClick) {
        if (!(signBlock.getState() instanceof Sign)) return;

        Sign sign = (Sign) signBlock.getState();
        PersistentDataContainer container = sign.getPersistentDataContainer();

        if (!container.has(chestShopKey, PersistentDataType.STRING)) return;

        try {
            UUID ownerId = UUID.fromString(container.get(ownerKey, PersistentDataType.STRING));
            long price = container.get(priceKey, PersistentDataType.LONG);
            boolean sellMode = container.getOrDefault(sellModeKey, PersistentDataType.BOOLEAN, false);
            ItemStack item = itemFromString(container.get(itemKey, PersistentDataType.STRING));

            if (item == null) {
                player.sendMessage("§c§lSHOP §8» §cErreur: Item invalide!");
                return;
            }

            // Trouver le coffre adjacent
            Block chestBlock = findAdjacentChest(signBlock);
            if (chestBlock == null) {
                player.sendMessage("§c§lSHOP §8» §cErreur: Coffre introuvable!");
                return;
            }

            ChestShop chestShop = chestShops.get(chestBlock.getLocation());
            if (chestShop == null) {
                player.sendMessage("§c§lSHOP §8» §cErreur: Chest shop invalide!");
                return;
            }

            // Vérifier les permissions pour le shop
            Shop shop = plugin.getShopManager().getShopAtLocation(chestBlock.getLocation());
            if (shop != null && !shop.isMember(player.getUniqueId()) && !player.getUniqueId().equals(ownerId)) {
                // Seuls les membres du shop et le propriétaire peuvent interagir
                if (rightClick) {
                    // Clic droit = voir les informations (autorisé pour tous)
                    showChestShopInfo(player, chestShop);
                    return;
                } else {
                    // Clic gauche = acheter/vendre (autorisé pour tous)
                    if (sellMode) {
                        handleCustomerSell(player, chestShop);
                    } else {
                        handleCustomerPurchase(player, chestShop);
                    }
                    return;
                }
            }

            if (player.getUniqueId().equals(ownerId)) {
                // Le propriétaire gère son shop
                if (rightClick) {
                    // Clic droit = supprimer le chest shop
                    handleOwnerDelete(player, chestShop, signBlock);
                } else {
                    // Clic gauche = voir les informations
                    handleOwnerInteraction(player, chestShop);
                }
            } else {
                // Un client ou un membre du shop
                if (rightClick) {
                    // Clic droit = voir les informations détaillées
                    showChestShopInfo(player, chestShop);
                } else {
                    // Clic gauche = acheter/vendre
                    if (sellMode) {
                        handleCustomerSell(player, chestShop);
                    } else {
                        handleCustomerPurchase(player, chestShop);
                    }
                }
            }

        } catch (Exception e) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la lecture du chest shop!");
        }
    }

    private void handleOwnerDelete(Player owner, ChestShop chestShop, Block signBlock) {
        owner.sendMessage("§a§lSHOP §8» §aChest shop supprimé!");

        // Supprimer le panneau
        signBlock.setType(Material.AIR);

        // Supprimer des données
        removeChestShop(chestShop.getChestLocation());
    }

    private void showChestShopInfo(Player player, ChestShop chestShop) {
        String ownerName = chestShop.getOwnerName();
        String itemDisplay = getItemDisplayNameForInfo(chestShop.getItem());
        String mode = chestShop.isSellMode() ? "§cVente" : "§aAchat";
        String priceFormatted = formatPrice(chestShop.getPrice());

        player.sendMessage("§6§l▬▬▬▬▬▬▬ CHEST SHOP INFO ▬▬▬▬▬▬▬");
        player.sendMessage("§7Propriétaire: §e" + ownerName);
        player.sendMessage("§7Mode: " + mode);
        player.sendMessage("§7Prix: §e" + priceFormatted + " §7coins");

        // Envoyer l'item avec hover pour la lore
        if (chestShop.getItem().hasItemMeta() && chestShop.getItem().getItemMeta().hasLore()) {
            TextComponent itemComponent = new TextComponent("§7Item: " + itemDisplay + " §7x§f" + chestShop.getItem().getAmount() + " §8(Passez la souris)");

            StringBuilder hoverText = new StringBuilder();
            for (String lore : chestShop.getItem().getItemMeta().getLore()) {
                if (hoverText.length() > 0) hoverText.append("\n");
                hoverText.append(lore);
            }

            itemComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[]{new TextComponent(hoverText.toString())}));
            player.spigot().sendMessage(itemComponent);
        } else {
            player.sendMessage("§7Item: " + itemDisplay + " §7x§f" + chestShop.getItem().getAmount());
        }

        if (chestShop.isSellMode()) {
            player.sendMessage("§7§oVous pouvez vendre vos items ici");
        } else {
            player.sendMessage("§7§oVous pouvez acheter des items ici");
        }

        // Vérifier le stock
        Chest chest = (Chest) chestShop.getChestLocation().getBlock().getState();
        int stock = countItems(chest.getInventory(), chestShop.getItem());
        player.sendMessage("§7Stock disponible: §a" + stock + " §7items");

        player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void handleOwnerInteraction(Player owner, ChestShop chestShop) {
        Chest chest = (Chest) chestShop.getChestLocation().getBlock().getState();
        Inventory inv = chest.getInventory();

        int stock = countItems(inv, chestShop.getItem());
        String mode = chestShop.isSellMode() ? "vente" : "achat";
        String itemDisplay = getItemDisplayNameForInfo(chestShop.getItem());

        owner.sendMessage("§6§lSHOP §8» §6Informations sur votre chest shop (§e" + mode + "§6):");
        owner.sendMessage("§7§lSHOP §8» §7Item: " + itemDisplay + " §7x§f" + chestShop.getItem().getAmount());
        owner.sendMessage("§7§lSHOP §8» §7Prix: §e" + formatPrice(chestShop.getPrice()) + " §7coins");
        owner.sendMessage("§7§lSHOP §8» §7Stock: §a" + stock + " §7items");
        owner.sendMessage("§7§lSHOP §8» §7Clic gauche = modifier, Clic droit = supprimer");
    }

    private void handleCustomerPurchase(Player customer, ChestShop chestShop) {
        if (!hook.hasCoins(customer.getUniqueId(), chestShop.getPrice())) {
            customer.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de coins! §7(§e" + chestShop.getPrice() + " §7requis)");
            return;
        }

        Chest chest = (Chest) chestShop.getChestLocation().getBlock().getState();
        Inventory chestInv = chest.getInventory();

        // Trouver et récupérer les vrais items du coffre (avec leurs métadonnées)
        ItemStack actualItemFromChest = findAndRetrieveItemFromInventory(chestInv, chestShop.getItem());
        if (actualItemFromChest == null) {
            customer.sendMessage("§c§lSHOP §8» §cStock insuffisant!");
            return;
        }

        // Vérifier l'espace dans l'inventaire du client
        if (!hasEnoughSpace(customer.getInventory(), actualItemFromChest)) {
            customer.sendMessage("§c§lSHOP §8» §cVotre inventaire est plein!");
            // Remettre l'item dans le coffre
            chestInv.addItem(actualItemFromChest);
            return;
        }

        // Effectuer la transaction
        if (!hook.removeCoins(customer.getUniqueId(), chestShop.getPrice())) {
            customer.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            // Remettre l'item dans le coffre
            chestInv.addItem(actualItemFromChest);
            return;
        }

        // Donner les coins au propriétaire
        hook.addCoins(chestShop.getOwnerId(), chestShop.getPrice());

        // Donner l'item réel au client (avec toutes ses métadonnées)
        customer.getInventory().addItem(actualItemFromChest);

        customer.sendMessage("§a§lSHOP §8» §aAchat effectué! §7(§e" + chestShop.getPrice() + " §7coins)");

        // Notifier le propriétaire si il est en ligne
        Player owner = Bukkit.getPlayer(chestShop.getOwnerId());
        if (owner != null) {
            owner.sendMessage("§a§lSHOP §8» §e" + customer.getName() + " §aa acheté dans votre chest shop! §7(+§e" + chestShop.getPrice() + " §7coins)");
        }
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    private void handleCustomerSell(Player customer, ChestShop chestShop) {
        // Vérifier que le client a l'item
        if (!hasEnoughItems(customer.getInventory(), chestShop.getItem())) {
            customer.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez d'items!");
            return;
        }

        // Vérifier que le propriétaire a assez de coins
        if (!hook.hasCoins(chestShop.getOwnerId(), chestShop.getPrice())) {
            customer.sendMessage("§c§lSHOP §8» §cLe propriétaire n'a pas assez de coins!");
            return;
        }

        Chest chest = (Chest) chestShop.getChestLocation().getBlock().getState();
        Inventory chestInv = chest.getInventory();

        // Récupérer les vrais items du joueur (avec leurs métadonnées)
        ItemStack actualItemFromPlayer = findAndRetrieveItemFromInventory(customer.getInventory(), chestShop.getItem());
        if (actualItemFromPlayer == null) {
            customer.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez d'items!");
            return;
        }

        // Vérifier l'espace dans le coffre
        if (!hasEnoughSpace(chestInv, actualItemFromPlayer)) {
            customer.sendMessage("§c§lSHOP §8» §cLe coffre est plein!");
            // Remettre l'item au joueur
            customer.getInventory().addItem(actualItemFromPlayer);
            return;
        }

        // Effectuer la transaction
        if (!hook.removeCoins(chestShop.getOwnerId(), chestShop.getPrice())) {
            customer.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            // Remettre l'item au joueur
            customer.getInventory().addItem(actualItemFromPlayer);
            return;
        }

        // Donner les coins au client
        hook.addCoins(customer.getUniqueId(), chestShop.getPrice());

        // Ajouter l'item réel au coffre (avec toutes ses métadonnées)
        chestInv.addItem(actualItemFromPlayer);

        customer.sendMessage("§a§lSHOP §8» §aVente effectuée! §7(+§e" + chestShop.getPrice() + " §7coins)");

        // Notifier le propriétaire si il est en ligne
        Player owner = Bukkit.getPlayer(chestShop.getOwnerId());
        if (owner != null) {
            owner.sendMessage("§a§lSHOP §8» §e" + customer.getName() + " §aa vendu dans votre chest shop! §7(-§e" + chestShop.getPrice() + " §7coins)");
        }
    }

    private Block findAdjacentSign(Block chest) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};

        for (BlockFace face : faces) {
            Block relative = chest.getRelative(face);
            if (relative.getState() instanceof Sign) {
                return relative;
            }
        }
        return null;
    }

    private Block findAdjacentChest(Block sign) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN};

        for (BlockFace face : faces) {
            Block relative = sign.getRelative(face);
            if (relative.getType() == Material.CHEST) {
                return relative;
            }
        }
        return null;
    }

    private Location findBestSignLocation(Block chest) {
        // Priorités: Nord, Sud, Est, Ouest, puis au-dessus
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP};

        for (BlockFace face : faces) {
            Block relative = chest.getRelative(face);
            if (relative.getType() == Material.AIR) {
                // Vérifier que ce n'est pas derrière le coffre (éviter le BlockFace.SOUTH si possible)
                if (face != BlockFace.SOUTH || !hasAlternative(chest, faces)) {
                    return relative.getLocation();
                }
            }
        }
        return null;
    }

    private boolean hasAlternative(Block chest, BlockFace[] faces) {
        for (BlockFace face : faces) {
            if (face != BlockFace.SOUTH && face != BlockFace.UP) {
                Block relative = chest.getRelative(face);
                if (relative.getType() == Material.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Item renommé - garder les couleurs
            return item.getItemMeta().getDisplayName();
        } else {
            // Item normal - nom propre
            String name = item.getType().name().toLowerCase().replace("_", " ");
            String[] words = name.split(" ");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (result.length() > 0) result.append(" ");
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
            return "§f" + result.toString();
        }
    }

    private String getItemDisplayNameForInfo(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Item renommé - garder les couleurs sans afficher la lore
            return item.getItemMeta().getDisplayName();
        } else {
            // Item normal - nom propre
            String name = item.getType().name().toLowerCase().replace("_", " ");
            String[] words = name.split(" ");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (result.length() > 0) result.append(" ");
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
            return "§f" + result.toString();
        }
    }

    private BlockFace getAttachedFace(Location signLoc, Location chestLoc) {
        int dx = chestLoc.getBlockX() - signLoc.getBlockX();
        int dz = chestLoc.getBlockZ() - signLoc.getBlockZ();

        if (dx == 1) return BlockFace.EAST;
        if (dx == -1) return BlockFace.WEST;
        if (dz == 1) return BlockFace.SOUTH;
        if (dz == -1) return BlockFace.NORTH;

        return null; // Au-dessus ou en dessous
    }

    private int countItems(Inventory inventory, ItemStack targetItem) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && areItemsSimilarForShop(item, targetItem)) {
                count += item.getAmount();
            }
        }
        return count / targetItem.getAmount();
    }

    /**
     * Vérifie si deux items sont similaires pour le shop (compare material, nom et lore)
     */
    private boolean areItemsSimilarForShop(ItemStack item1, ItemStack item2) {
        if (item1.getType() != item2.getType()) return false;

        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        if (meta1 == null && meta2 == null) return true;
        if (meta1 == null || meta2 == null) return false;

        // Comparer les noms d'affichage
        String name1 = meta1.hasDisplayName() ? meta1.getDisplayName() : null;
        String name2 = meta2.hasDisplayName() ? meta2.getDisplayName() : null;
        if (!java.util.Objects.equals(name1, name2)) return false;

        // Comparer les lores
        List<String> lore1 = meta1.hasLore() ? meta1.getLore() : null;
        List<String> lore2 = meta2.hasLore() ? meta2.getLore() : null;
        return java.util.Objects.equals(lore1, lore2);
    }

    private boolean hasEnoughItems(Inventory inventory, ItemStack targetItem) {
        return countItems(inventory, targetItem) >= 1;
    }

    private boolean hasEnoughSpace(Inventory inventory, ItemStack item) {
        // Créer une copie de l'inventaire pour tester
        ItemStack[] contents = inventory.getContents().clone();

        // Essayer d'ajouter l'item
        int remaining = item.getAmount();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];

            if (slot == null) {
                // Slot vide
                remaining -= Math.min(remaining, item.getMaxStackSize());
            } else if (areItemsSimilarForShop(slot, item)) {
                // Slot avec le même item
                int space = slot.getMaxStackSize() - slot.getAmount();
                remaining -= Math.min(remaining, space);
            }
        }

        return remaining <= 0;
    }

    /**
     * Trouve et récupère un item spécifique du coffre en conservant toutes ses métadonnées
     */
    private ItemStack findAndRetrieveItemFromInventory(Inventory inventory, ItemStack targetItem) {
        int amountNeeded = targetItem.getAmount();
        ItemStack collectedItem = null;

        for (int i = 0; i < inventory.getSize() && amountNeeded > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && areItemsSimilarForShop(item, targetItem)) {
                int available = item.getAmount();

                if (collectedItem == null) {
                    // Premier item trouvé - créer la base avec ses métadonnées
                    collectedItem = item.clone();
                    collectedItem.setAmount(0);
                }

                if (available <= amountNeeded) {
                    // Prendre tout l'item
                    collectedItem.setAmount(collectedItem.getAmount() + available);
                    inventory.setItem(i, null);
                    amountNeeded -= available;
                } else {
                    // Prendre une partie
                    collectedItem.setAmount(collectedItem.getAmount() + amountNeeded);
                    ItemStack remainingItem = item.clone();
                    remainingItem.setAmount(available - amountNeeded);
                    inventory.setItem(i, remainingItem);
                    amountNeeded = 0;
                }
            }
        }

        // Vérifier qu'on a récupéré assez d'items
        if (collectedItem != null && collectedItem.getAmount() >= targetItem.getAmount()) {
            collectedItem.setAmount(targetItem.getAmount());
            return collectedItem;
        }

        return null; // Pas assez d'items trouvés
    }

    private void removeItems(Inventory inventory, ItemStack targetItem) {
        int amountToRemove = targetItem.getAmount();

        for (int i = 0; i < inventory.getSize() && amountToRemove > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && areItemsSimilarForShop(item, targetItem)) {
                int available = item.getAmount();
                if (available <= amountToRemove) {
                    inventory.setItem(i, null);
                    amountToRemove -= available;
                } else {
                    // IMPORTANT: Créer une copie pour conserver les métadonnées originales
                    ItemStack remainingItem = item.clone();
                    remainingItem.setAmount(available - amountToRemove);
                    inventory.setItem(i, remainingItem);
                    amountToRemove = 0;
                }
            }
        }
    }

    /**
     * Formate le prix pour l'affichage avec lettres
     */
    private String formatPrice(long price) {
        if (price >= 1000000000000L) { // Trillions
            return (price / 1000000000000L) + "T";
        } else if (price >= 1000000000L) { // Billions
            return (price / 1000000000L) + "B";
        } else if (price >= 1000000L) { // Millions
            return (price / 1000000L) + "M";
        } else if (price >= 1000L) { // Thousands
            return (price / 1000L) + "K";
        } else {
            return String.valueOf(price);
        }
    }

    private String itemToString(ItemStack item) {
        if (item == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(item.getType().name()).append(":").append(item.getAmount());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                sb.append(":name:").append(meta.getDisplayName());
            }
            if (meta.hasLore()) {
                sb.append(":lore:");
                for (String lore : meta.getLore()) {
                    sb.append(lore).append("|");
                }
            }
        }

        return sb.toString();
    }

    private ItemStack itemFromString(String itemString) {
        if (itemString == null || itemString.isEmpty()) return null;

        try {
            String[] parts = itemString.split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);

            ItemStack item = new ItemStack(material, amount);

            if (parts.length > 2) {
                ItemMeta meta = item.getItemMeta();

                for (int i = 2; i < parts.length; i += 2) {
                    if (i + 1 < parts.length) {
                        String key = parts[i];
                        String value = parts[i + 1];

                        if ("name".equals(key)) {
                            meta.setDisplayName(value);
                        } else if ("lore".equals(key)) {
                            List<String> lore = Arrays.asList(value.split("\\|"));
                            meta.setLore(lore);
                        }
                    }
                }

                item.setItemMeta(meta);
            }

            return item;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Vérifie si un joueur peut interagir avec un coffre (pour les chest shops)
     */
    public boolean canPlayerInteractWithChest(Player player, Location chestLocation) {
        if (isChestShop(chestLocation)) {
            // C'est un chest shop - autoriser la consultation des infos mais pas l'ouverture directe
            return false;
        }

        // Coffre normal - vérifier les permissions du shop
        Shop shop = plugin.getShopManager().getShopAtLocation(chestLocation);
        if (shop == null) return true; // Pas dans un shop

        return shop.isMember(player.getUniqueId());
    }

    public void removeChestShop(Location chestLocation) {
        ChestShop removed = chestShops.remove(chestLocation);
        if (removed != null) {
            // Retirer aussi du shop
            for (Shop shop : plugin.getShopManager().getAllShops()) {
                shop.removeChestShop(chestLocation);
            }
            plugin.getShopManager().saveAll();
        }
    }

    public boolean isChestShop(Location location) {
        return chestShops.containsKey(location);
    }

    public ChestShop getChestShop(Location location) {
        return chestShops.get(location);
    }

    public void clearPendingCreation(UUID playerId) {
        pendingCreations.remove(playerId);
        pendingPriceEdits.remove(playerId);
    }

    public boolean hasPendingCreation(UUID playerId) {
        return pendingCreations.containsKey(playerId);
    }

    public boolean hasPendingPriceEdit(UUID playerId) {
        return pendingPriceEdits.containsKey(playerId);
    }

    // ===============================
    // CLASSES INTERNES
    // ===============================

    private static class PendingShopCreation {
        private final UUID playerId;
        private final ItemStack item;
        private Location chestLocation;
        private long price;
        private boolean sellMode;

        public PendingShopCreation(UUID playerId, ItemStack item) {
            this.playerId = playerId;
            this.item = item;
            this.sellMode = false; // Achat par défaut
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public ItemStack getItem() {
            return item;
        }

        public Location getChestLocation() {
            return chestLocation;
        }

        public void setChestLocation(Location chestLocation) {
            this.chestLocation = chestLocation;
        }

        public long getPrice() {
            return price;
        }

        public void setPrice(long price) {
            this.price = price;
        }

        public boolean isSellMode() {
            return sellMode;
        }

        public void setSellMode(boolean sellMode) {
            this.sellMode = sellMode;
        }
    }

    private static class PendingPriceEdit {
        private final Location chestLocation;
        private final Location signLocation;
        private long price;
        private boolean sellMode;

        public PendingPriceEdit(Location chestLocation, Location signLocation, long price, boolean sellMode) {
            this.chestLocation = chestLocation;
            this.signLocation = signLocation;
            this.price = price;
            this.sellMode = sellMode;
        }

        public Location getChestLocation() {
            return chestLocation;
        }

        public Location getSignLocation() {
            return signLocation;
        }

        public long getPrice() {
            return price;
        }

        public void setPrice(long price) {
            this.price = price;
        }

        public boolean isSellMode() {
            return sellMode;
        }

        public void setSellMode(boolean sellMode) {
            this.sellMode = sellMode;
        }
    }
}