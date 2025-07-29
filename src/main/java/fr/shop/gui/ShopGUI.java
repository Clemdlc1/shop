package fr.shop.gui;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import fr.shop.data.ShopAdvertisement;
import fr.shop.hooks.PrisonTycoonHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interface graphique pour les shops
 */
public class ShopGUI implements Listener {

    private final PlayerShops plugin;
    private final PrisonTycoonHook hook;

    // Tracking des GUIs ouverts
    private final Map<UUID, GUIType> openGUIs;
    private final Map<UUID, Map<String, Object>> guiData;

    public ShopGUI(PlayerShops plugin) {
        this.plugin = plugin;
        this.hook = plugin.getPrisonTycoonHook();
        this.openGUIs = new HashMap<>();
        this.guiData = new HashMap<>();

        // Enregistrer les listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ===============================
    // ÉNUMÉRATIONS
    // ===============================

    public void openShopListGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lListe des Shops");

        List<Shop> allShops = new ArrayList<>(plugin.getShopManager().getAllShops());

        // Trier les shops : disponibles en premier, puis par statut
        allShops.sort((a, b) -> {
            Shop.ShopStatus statusA = a.getStatus();
            Shop.ShopStatus statusB = b.getStatus();

            // Disponibles en premier
            if (statusA == Shop.ShopStatus.AVAILABLE && statusB != Shop.ShopStatus.AVAILABLE) return -1;
            if (statusA != Shop.ShopStatus.AVAILABLE && statusB == Shop.ShopStatus.AVAILABLE) return 1;

            // Puis par ordre de statut
            return statusA.compareTo(statusB);
        });

        // Ajouter les shops (maximum 45 pour laisser de la place aux boutons)
        int slot = 0;
        for (Shop shop : allShops) {
            if (slot >= 45) break;

            ItemStack item = createShopItem(shop);
            inv.setItem(slot, item);
            slot++;
        }

        // Remplir les slots vides avec des panneaux de verre
        for (int i = slot; i < 45; i++) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
            inv.setItem(i, glass);
        }

        // Ligne de séparation
        for (int i = 45; i < 54; i++) {
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sepMeta = separator.getItemMeta();
            sepMeta.setDisplayName(" ");
            separator.setItemMeta(sepMeta);
            inv.setItem(i, separator);
        }

        // Boutons de navigation
        ItemStack myShopButton = new ItemStack(Material.EMERALD);
        ItemMeta myShopMeta = myShopButton.getItemMeta();
        myShopMeta.setDisplayName("§a§lMon Shop");

        Shop playerShop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (playerShop != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Statut: " + getStatusColor(playerShop.getStatus()) + getStatusName(playerShop.getStatus()));
            lore.add("§7Cliquez pour vous téléporter");
            if (playerShop.getStatus() == Shop.ShopStatus.GRACE_PERIOD || playerShop.isRentExpired()) {
                lore.add("§e§lClic droit pour prolonger!");
            }
            myShopMeta.setLore(lore);
        } else {
            myShopMeta.setLore(Arrays.asList("§7Vous n'avez pas de shop"));
        }
        myShopButton.setItemMeta(myShopMeta);
        inv.setItem(49, myShopButton);

        ItemStack adsButton = new ItemStack(Material.PAPER);
        ItemMeta adsMeta = adsButton.getItemMeta();
        adsMeta.setDisplayName("§e§lAnnonces");
        adsMeta.setLore(Arrays.asList("§7Cliquez pour voir les annonces"));
        adsButton.setItemMeta(adsMeta);
        inv.setItem(48, adsButton);

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c§lFermer");
        closeButton.setItemMeta(closeMeta);
        inv.setItem(50, closeButton);

        // Informations
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoButton.getItemMeta();
        infoMeta.setDisplayName("§b§lInformations");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Total de shops: §e" + allShops.size());
        infoLore.add("§7Disponibles: §a" + allShops.stream().mapToInt(s -> s.getStatus() == Shop.ShopStatus.AVAILABLE ? 1 : 0).sum());
        infoLore.add("§7Loués: §e" + allShops.stream().mapToInt(s -> s.getStatus() == Shop.ShopStatus.RENTED ? 1 : 0).sum());
        infoLore.add("§7En grâce: §6" + allShops.stream().mapToInt(s -> s.getStatus() == Shop.ShopStatus.GRACE_PERIOD ? 1 : 0).sum());
        infoMeta.setLore(infoLore);
        infoButton.setItemMeta(infoMeta);
        inv.setItem(46, infoButton);

        openGUIs.put(player.getUniqueId(), GUIType.SHOP_LIST);
        player.openInventory(inv);
    }

    // ===============================
    // MENU PRINCIPAL DES SHOPS
    // ===============================

    public void openAdvertisementListGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§e§lAnnonces des Shops");

        List<Shop> shopsWithAds = plugin.getShopManager().getShopsWithAdvertisements();

        int slot = 0;
        for (Shop shop : shopsWithAds) {
            if (slot >= 45) break;

            ItemStack item = createAdvertisementItem(shop);
            inv.setItem(slot, item);
            slot++;
        }

        // Remplir les slots vides
        for (int i = slot; i < 45; i++) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
            inv.setItem(i, glass);
        }

        // Ligne de séparation
        for (int i = 45; i < 54; i++) {
            ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sepMeta = separator.getItemMeta();
            sepMeta.setDisplayName(" ");
            separator.setItemMeta(sepMeta);
            inv.setItem(i, separator);
        }

        // Boutons
        ItemStack createAdButton = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta createMeta = createAdButton.getItemMeta();
        createMeta.setDisplayName("§a§lCréer une Annonce");
        createMeta.setLore(Arrays.asList("§7Cliquez pour créer votre annonce"));
        createAdButton.setItemMeta(createMeta);
        inv.setItem(48, createAdButton);

        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§7§lRetour");
        backButton.setItemMeta(backMeta);
        inv.setItem(49, backButton);

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c§lFermer");
        closeButton.setItemMeta(closeMeta);
        inv.setItem(50, closeButton);

        openGUIs.put(player.getUniqueId(), GUIType.ADVERTISEMENT_LIST);
        player.openInventory(inv);
    }

    // ===============================
    // MENU DES ANNONCES
    // ===============================

    public void openCreateAdvertisementGUI(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        if (shop.getAdvertisement() != null) {
            player.sendMessage("§c§lSHOP §8» §cVous avez déjà une annonce! Utilisez §e/shop ad edit §cpour la modifier.");
            return;
        }

        player.closeInventory();
        player.sendMessage("§a§lSHOP §8» §aCréation d'annonce:");
        player.sendMessage("§7§lSHOP §8» §7Tapez le titre de votre annonce §8(ou §ccancel §8pour annuler):");

        Map<String, Object> data = new HashMap<>();
        data.put("step", "title");
        data.put("advertisement", new ShopAdvertisement());

        openGUIs.put(player.getUniqueId(), GUIType.CREATE_ADVERTISEMENT);
        guiData.put(player.getUniqueId(), data);
    }

    // ===============================
    // CRÉATION D'ANNONCE
    // ===============================

    public void openEditAdvertisementGUI(Player player) {
        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        if (shop.getAdvertisement() == null) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas d'annonce!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§e§lModifier l'Annonce");

        // Titre
        ItemStack titleItem = new ItemStack(Material.NAME_TAG);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.setDisplayName("§6§lModifier le Titre");
        titleMeta.setLore(Arrays.asList(
                "§7Titre actuel: §f" + shop.getAdvertisement().getTitle(),
                "§7Cliquez pour modifier"
        ));
        titleItem.setItemMeta(titleMeta);
        inv.setItem(10, titleItem);

        // Description
        ItemStack descItem = new ItemStack(Material.BOOK);
        ItemMeta descMeta = descItem.getItemMeta();
        descMeta.setDisplayName("§6§lModifier la Description");
        descMeta.setLore(Arrays.asList(
                "§7Description actuelle: §f" + shop.getAdvertisement().getDescription(),
                "§7Cliquez pour modifier"
        ));
        descItem.setItemMeta(descMeta);
        inv.setItem(12, descItem);

        // Catégorie
        ItemStack catItem = new ItemStack(Material.BOOKSHELF);
        ItemMeta catMeta = catItem.getItemMeta();
        catMeta.setDisplayName("§6§lModifier la Catégorie");
        catMeta.setLore(Arrays.asList(
                "§7Catégorie actuelle: §f" + shop.getAdvertisement().getCategory(),
                "§7Cliquez pour modifier"
        ));
        catItem.setItemMeta(catMeta);
        inv.setItem(14, catItem);

        // Toggle actif/inactif
        ItemStack toggleItem = new ItemStack(shop.getAdvertisement().isActive() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta toggleMeta = toggleItem.getItemMeta();
        toggleMeta.setDisplayName("§6§lActiver/Désactiver");
        toggleMeta.setLore(Arrays.asList(
                "§7État: " + (shop.getAdvertisement().isActive() ? "§aActive" : "§cInactive"),
                "§7Cliquez pour changer"
        ));
        toggleItem.setItemMeta(toggleMeta);
        inv.setItem(16, toggleItem);

        // Retour
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§7§lRetour");
        backButton.setItemMeta(backMeta);
        inv.setItem(22, backButton);

        openGUIs.put(player.getUniqueId(), GUIType.EDIT_ADVERTISEMENT);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (!openGUIs.containsKey(playerId)) return;

        event.setCancelled(true);

        GUIType guiType = openGUIs.get(playerId);
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        boolean rightClick = event.getClick().isRightClick();

        switch (guiType) {
            case SHOP_LIST:
                handleShopListClick(player, clicked, event.getSlot(), rightClick);
                break;

            case ADVERTISEMENT_LIST:
                handleAdvertisementListClick(player, clicked, event.getSlot());
                break;

            case EDIT_ADVERTISEMENT:
                handleEditAdvertisementClick(player, clicked, event.getSlot());
                break;
        }
    }

    // ===============================
    // GESTION DES CLICS
    // ===============================

    private void handleShopListClick(Player player, ItemStack clicked, int slot, boolean rightClick) {
        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.contains("§a§lMon Shop")) {
            Shop playerShop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
            if (playerShop != null) {
                if (rightClick && (playerShop.getStatus() == Shop.ShopStatus.GRACE_PERIOD || playerShop.isRentExpired())) {
                    // Prolonger le shop
                    player.closeInventory();
                    plugin.getShopManager().extendShopRent(player);
                } else {
                    // Téléporter au shop
                    player.closeInventory();
                    player.teleport(playerShop.getLocation());
                    player.sendMessage("§a§lSHOP §8» §aTéléporté à votre shop!");
                }
            } else {
                player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            }
        } else if (displayName.contains("§e§lAnnonces")) {
            openAdvertisementListGUI(player);
        } else if (displayName.contains("§c§lFermer")) {
            player.closeInventory();
        } else if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            // Ignorer les clics sur les panneaux de verre
            return;
        } else if (slot < 45) {
            // Clic sur un shop
            Shop.ShopStatus status = getShopStatusFromItem(clicked);

            if (status == Shop.ShopStatus.AVAILABLE) {
                // Shop disponible - téléportation pour visiter
                String shopId = extractShopIdFromLore(clicked);
                if (shopId != null) {
                    Shop shop = plugin.getShopManager().getShop(shopId);
                    if (shop != null) {
                        player.closeInventory();
                        player.teleport(shop.getLocation());
                        player.sendMessage("§a§lSHOP §8» §aTéléporté au shop §e" + shopId + "§a! Faites §e/shop claim §apour le revendiquer.");
                    }
                }
            } else {
                // Shop loué - téléportation
                String ownerName = extractOwnerFromLore(clicked);
                if (ownerName != null) {
                    Shop shop = plugin.getShopManager().getAllShops().stream()
                            .filter(s -> s.getOwnerName() != null && s.getOwnerName().equals(ownerName))
                            .findFirst()
                            .orElse(null);

                    if (shop != null) {
                        player.closeInventory();
                        player.teleport(shop.getLocation());
                        player.sendMessage("§a§lSHOP §8» §aTéléporté au shop de §e" + ownerName + "§a!");
                    }
                }
            }
        }
    }

    private void handleAdvertisementListClick(Player player, ItemStack clicked, int slot) {
        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.contains("§a§lCréer une Annonce")) {
            openCreateAdvertisementGUI(player);
        } else if (displayName.contains("§7§lRetour")) {
            openShopListGUI(player);
        } else if (displayName.contains("§c§lFermer")) {
            player.closeInventory();
        } else if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            // Ignorer les clics sur les panneaux de verre
            return;
        } else if (slot < 45) {
            // Clic sur une annonce - téléportation
            String ownerName = extractOwnerFromLore(clicked);
            if (ownerName != null) {
                Shop shop = plugin.getShopManager().getAllShops().stream()
                        .filter(s -> s.getOwnerName() != null && s.getOwnerName().equals(ownerName))
                        .findFirst()
                        .orElse(null);

                if (shop != null) {
                    player.closeInventory();
                    player.teleport(shop.getLocation());
                    player.sendMessage("§a§lSHOP §8» §aTéléporté au shop de §e" + ownerName + "§a!");
                } else {
                    player.sendMessage("§c§lSHOP §8» §cShop introuvable!");
                }
            }
        }
    }

    private void handleEditAdvertisementClick(Player player, ItemStack clicked, int slot) {
        String displayName = clicked.getItemMeta().getDisplayName();

        if (displayName.contains("§7§lRetour")) {
            openAdvertisementListGUI(player);
            return;
        }

        Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
        if (shop == null || shop.getAdvertisement() == null) return;

        player.closeInventory();

        Map<String, Object> data = new HashMap<>();
        data.put("advertisement", shop.getAdvertisement());

        if (displayName.contains("Modifier le Titre")) {
            data.put("step", "edit_title");
            player.sendMessage("§a§lSHOP §8» §aTapez le nouveau titre:");
        } else if (displayName.contains("Modifier la Description")) {
            data.put("step", "edit_description");
            player.sendMessage("§a§lSHOP §8» §aTapez la nouvelle description:");
        } else if (displayName.contains("Modifier la Catégorie")) {
            data.put("step", "edit_category");
            player.sendMessage("§a§lSHOP §8» §aTapez la nouvelle catégorie:");
        } else if (displayName.contains("Activer/Désactiver")) {
            boolean newState = !shop.getAdvertisement().isActive();
            shop.getAdvertisement().setActive(newState);
            player.sendMessage("§a§lSHOP §8» §aAnnonce " + (newState ? "activée" : "désactivée") + "!");
            plugin.getShopManager().saveAll();
            return;
        }

        openGUIs.put(player.getUniqueId(), GUIType.EDIT_ADVERTISEMENT);
        guiData.put(player.getUniqueId(), data);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!openGUIs.containsKey(playerId)) return;

        GUIType guiType = openGUIs.get(playerId);
        Map<String, Object> data = guiData.get(playerId);

        if (data == null) return;

        event.setCancelled(true);
        String message = event.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            openGUIs.remove(playerId);
            guiData.remove(playerId);
            player.sendMessage("§c§lSHOP §8» §cAnnulé!");
            return;
        }

        // Traitement en sync
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            handleChatInput(player, guiType, data, message);
        });
    }

    // ===============================
    // GESTION DU CHAT
    // ===============================

    private void handleChatInput(Player player, GUIType guiType, Map<String, Object> data, String message) {
        if (guiType == GUIType.CREATE_ADVERTISEMENT) {
            handleCreateAdvertisementChat(player, data, message);
        } else if (guiType == GUIType.EDIT_ADVERTISEMENT) {
            handleEditAdvertisementChat(player, data, message);
        }
    }

    private void handleCreateAdvertisementChat(Player player, Map<String, Object> data, String message) {
        String step = (String) data.get("step");
        ShopAdvertisement ad = (ShopAdvertisement) data.get("advertisement");

        switch (step) {
            case "title":
                ad.setTitle(message);
                data.put("step", "description");
                player.sendMessage("§a§lSHOP §8» §aTitre défini! Tapez maintenant la description:");
                break;

            case "description":
                ad.setDescription(message);
                data.put("step", "category");
                player.sendMessage("§a§lSHOP §8» §aDescription définie! Tapez maintenant la catégorie:");
                break;

            case "category":
                ad.setCategory(message);

                // Finaliser la création
                Shop shop = plugin.getShopManager().getPlayerShop(player.getUniqueId());
                if (shop != null) {
                    shop.setAdvertisement(ad);
                    plugin.getShopManager().saveAll();
                    player.sendMessage("§a§lSHOP §8» §aAnnonce créée avec succès!");
                } else {
                    player.sendMessage("§c§lSHOP §8» §cErreur: Shop introuvable!");
                }

                openGUIs.remove(player.getUniqueId());
                guiData.remove(player.getUniqueId());
                break;
        }
    }

    private void handleEditAdvertisementChat(Player player, Map<String, Object> data, String message) {
        String step = (String) data.get("step");
        ShopAdvertisement ad = (ShopAdvertisement) data.get("advertisement");

        switch (step) {
            case "edit_title":
                ad.setTitle(message);
                player.sendMessage("§a§lSHOP §8» §aTitre modifié!");
                break;

            case "edit_description":
                ad.setDescription(message);
                player.sendMessage("§a§lSHOP §8» §aDescription modifiée!");
                break;

            case "edit_category":
                ad.setCategory(message);
                player.sendMessage("§a§lSHOP §8» §aCatégorie modifiée!");
                break;
        }

        plugin.getShopManager().saveAll();
        openGUIs.remove(player.getUniqueId());
        guiData.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Ne pas nettoyer si le joueur est en train de créer/modifier une annonce
        if (openGUIs.containsKey(playerId)) {
            GUIType type = openGUIs.get(playerId);
            if (type != GUIType.CREATE_ADVERTISEMENT && type != GUIType.EDIT_ADVERTISEMENT) {
                openGUIs.remove(playerId);
                guiData.remove(playerId);
            }
        }
    }

    // ===============================
    // NETTOYAGE
    // ===============================

    private ItemStack createShopItem(Shop shop) {
        Shop.ShopStatus status = shop.getStatus();
        Material material;
        String statusText;

        switch (status) {
            case AVAILABLE:
                material = Material.EMERALD_BLOCK;
                statusText = "§a(Disponible)";
                break;
            case RENTED:
                material = Material.GOLD_BLOCK;
                statusText = "§e(Loué)";
                break;
            case GRACE_PERIOD:
                material = Material.ORANGE_TERRACOTTA;
                statusText = "§6(Période de grâce)";
                break;
            case EXPIRED:
                material = Material.RED_TERRACOTTA;
                statusText = "§c(Expiré)";
                break;
            default:
                material = Material.GRAY_TERRACOTTA;
                statusText = "§7(Inconnu)";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (status == Shop.ShopStatus.AVAILABLE) {
            meta.setDisplayName("§a§lShop " + shop.getId() + " " + statusText);

            long basePrice = plugin.getConfigManager().getRentPrice();
            List<String> lore = new ArrayList<>();
            lore.add("§7Prix: §e" + basePrice + " §7beacons");
            lore.add("§7Taille: §f" + shop.getSize() + " §7blocs");
            lore.add("§8ID: " + shop.getId());
            lore.add("");
            lore.add("§a§lClic pour visiter!");
            lore.add("§7Ou entrez dans le shop et faites §e/shop claim");
            meta.setLore(lore);
        } else {
            meta.setDisplayName("§6§lShop " + shop.getId() + " " + statusText);

            List<String> lore = new ArrayList<>();

            if (shop.getOwnerName() != null) {
                lore.add("§7Propriétaire: §e" + shop.getOwnerName());
            }

            if (status == Shop.ShopStatus.RENTED) {
                lore.add("§7Expire dans: §f" + formatTimeRemaining(shop.getRentExpiry()));
            } else if (status == Shop.ShopStatus.GRACE_PERIOD) {
                lore.add("§7Fin de grâce: §c" + formatTimeRemaining(shop.getGraceExpiry()));
                lore.add("§e§lSeuls les membres peuvent accéder!");
            }

            if (shop.getOwnerName() != null) {
                lore.add("§8Propriétaire: " + shop.getOwnerName());
            }

            if (shop.getAdvertisement() != null && shop.getAdvertisement().isActive()) {
                lore.add("");
                lore.add("§6§lAnnonce:");
                lore.add("§f" + shop.getAdvertisement().getTitle());
                if (shop.isAdvertisementBoosted()) {
                    lore.add("§e§l⭐ BOOSTÉ ⭐");
                }
            }

            lore.add("");
            lore.add("§e§lClic pour visiter!");
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    private ItemStack createAdvertisementItem(Shop shop) {
        Material material = shop.isAdvertisementBoosted() ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6§l" + shop.getAdvertisement().getTitle());

        List<String> lore = new ArrayList<>();
        lore.add("§7Par: §e" + shop.getOwnerName());
        lore.add("§7Catégorie: §f" + shop.getAdvertisement().getCategory());
        lore.add("");
        lore.add("§f" + shop.getAdvertisement().getDescription());

        for (String detail : shop.getAdvertisement().getDetails()) {
            lore.add("§8• " + detail);
        }

        if (shop.isAdvertisementBoosted()) {
            lore.add("");
            lore.add("§e§l⭐ ANNONCE BOOSTÉE ⭐");
        }

        lore.add("");
        lore.add("§e§lClic pour visiter!");
        if (shop.getOwnerName() != null) {
            lore.add("§8Propriétaire: " + shop.getOwnerName());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String extractShopIdFromLore(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return null;

        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith("§8ID: ")) {
                return line.replace("§8ID: ", "");
            }
        }
        return null;
    }

    private String extractOwnerFromLore(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return null;

        for (String line : item.getItemMeta().getLore()) {
            if (line.startsWith("§8Propriétaire: ")) {
                return line.replace("§8Propriétaire: ", "");
            }
        }
        return null;
    }

    private Shop.ShopStatus getShopStatusFromItem(ItemStack item) {
        if (item.getType() == Material.EMERALD_BLOCK) {
            return Shop.ShopStatus.AVAILABLE;
        } else if (item.getType() == Material.GOLD_BLOCK) {
            return Shop.ShopStatus.RENTED;
        } else if (item.getType() == Material.ORANGE_TERRACOTTA) {
            return Shop.ShopStatus.GRACE_PERIOD;
        } else if (item.getType() == Material.RED_TERRACOTTA) {
            return Shop.ShopStatus.EXPIRED;
        }
        return Shop.ShopStatus.AVAILABLE;
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
        if (remaining <= 0) return "§cExpiré";

        long days = remaining / (24 * 60 * 60 * 1000);
        long hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);

        if (days > 0) {
            return days + "j " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + minutes + "min";
        } else {
            return minutes + "min";
        }
    }

    private enum GUIType {
        SHOP_LIST,
        ADVERTISEMENT_LIST,
        CREATE_ADVERTISEMENT,
        EDIT_ADVERTISEMENT
    }
}