package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import fr.shop.hooks.PrisonTycoonHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestionnaire principal des shops
 */
public class ShopManager {

    private final PlayerShops plugin;
    private final PrisonTycoonHook hook;
    private final ConfigManager configManager;
    private final Map<String, Shop> shops;
    private final Map<UUID, Long> lastMessageTime;
    private final Set<UUID> playersNearShops;

    public ShopManager(PlayerShops plugin) {
        this.plugin = plugin;
        this.hook = plugin.getPrisonTycoonHook();
        this.configManager = plugin.getConfigManager();
        this.shops = new HashMap<>();
        this.lastMessageTime = new HashMap<>();
        this.playersNearShops = new HashSet<>();

        loadShops();
        startRentCheckTask();
        startProximityCheckTask();
    }

    private void loadShops() {
        shops.putAll(configManager.loadShopsFromConfig());
        plugin.getLogger().info("Chargé " + shops.size() + " shop(s)");
    }

    public void saveAll() {
        configManager.saveShopsToConfig(shops);
        plugin.getLogger().info("Sauvegardé " + shops.size() + " shop(s)");
    }

    private void startRentCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredRents();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60L * 5L); // Vérifier toutes les 5 minutes
    }

    private void startProximityCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerProximity();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Vérifier chaque seconde
    }

    private void checkExpiredRents() {
        for (Shop shop : shops.values()) {
            if (shop.isRented()) {
                if (shop.isRentExpired() && !shop.isInGracePeriod()) {
                    // Commencer la période de grâce
                    startGracePeriod(shop);
                } else if (shop.isInGracePeriod() && shop.isGraceExpired()) {
                    // Expirer définitivement le shop
                    expireShop(shop);
                }
            }
        }
    }

    private void startGracePeriod(Shop shop) {
        plugin.getLogger().info("Début de période de grâce pour le shop " + shop.getId() + " de " + shop.getOwnerName());

        shop.setInGracePeriod(true);
        shop.setGraceExpiry(System.currentTimeMillis() + (24 * 60 * 60 * 1000L)); // 1 jour

        // Informer le propriétaire si il est en ligne
        Player owner = shop.getOwnerId() != null ? Bukkit.getPlayer(shop.getOwnerId()) : null;
        if (owner != null) {
            owner.sendMessage("§e§lSHOP §8» §eVotre shop §e" + shop.getId() + " §eest entré en période de grâce!");
            owner.sendMessage("§e§lSHOP §8» §eVous avez 24h pour prolonger avant la fermeture définitive.");
        }

        saveAll();
    }

    private void expireShop(Shop shop) {
        plugin.getLogger().info("Expiration définitive du shop " + shop.getId() + " de " + shop.getOwnerName());

        // Informer le propriétaire si il est en ligne
        Player owner = shop.getOwnerId() != null ? Bukkit.getPlayer(shop.getOwnerId()) : null;
        if (owner != null) {
            owner.sendMessage("§c§lSHOP §8» §cVotre shop §e" + shop.getId() + " §ca été fermé définitivement!");
        }

        // TODO: Sauvegarder le contenu dans une database

        // Réinitialiser le shop
        resetShop(shop);
    }

    private void resetShop(Shop shop) {
        String shopId = shop.getId();

        // Supprimer les visuels
        plugin.getVisualManager().removeShopVisuals(shopId);

        shop.setRented(false);
        shop.setInGracePeriod(false);
        shop.setOwnerId(null);
        shop.setOwnerName(null);
        shop.getMembers().clear();
        shop.setCustomMessage(null);
        shop.getFloatingTexts().clear();
        shop.setHasNPC(false);
        shop.setNpcName(null);
        shop.setNpcLocation(null);
        shop.setAdvertisement(null);
        shop.setAdvertisementBoostExpiry(0);
        shop.getChestShops().clear();
        shop.setBeaconLocation(null);
        shop.setHasBeacon(false);

        saveAll();
    }

    private void checkPlayerProximity() {
        Set<UUID> currentlyNearShops = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean nearShop = false;

            for (Shop shop : shops.values()) {
                if (shop.isRented() && shop.getCustomMessage() != null &&
                        shop.isNearShop(player.getLocation(), 5.0)) {

                    nearShop = true;
                    currentlyNearShops.add(player.getUniqueId());

                    // Vérifier si le joueur n'était pas déjà proche
                    if (!playersNearShops.contains(player.getUniqueId())) {
                        // Vérifier le cooldown des messages (5 secondes)
                        long lastMsg = lastMessageTime.getOrDefault(player.getUniqueId(), 0L);
                        if (System.currentTimeMillis() - lastMsg > 5000) {
                            // Envoyer le message personnalisé
                            String message = ChatColor.translateAlternateColorCodes('&', shop.getCustomMessage());
                            player.sendMessage("§6§lSHOP §8» " + message);
                            lastMessageTime.put(player.getUniqueId(), System.currentTimeMillis());
                        }
                    }
                    break; // Un seul message par proximité
                }
            }

            if (!nearShop) {
                currentlyNearShops.remove(player.getUniqueId());
            }
        }

        playersNearShops.clear();
        playersNearShops.addAll(currentlyNearShops);
    }

    // ===============================
    // MÉTHODES PRINCIPALES
    // ===============================

    public boolean claimShop(Player player, String shopId) {
        Shop shop = shops.get(shopId);
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cShop introuvable!");
            return false;
        }

        if (shop.getStatus() != Shop.ShopStatus.AVAILABLE) {
            player.sendMessage("§c§lSHOP §8» §cCe shop n'est pas disponible!");
            return false;
        }

        // Vérifier si le joueur a déjà un shop
        Shop existingShop = getPlayerShop(player.getUniqueId());
        if (existingShop != null) {
            player.sendMessage("§c§lSHOP §8» §cVous possédez déjà le shop §e" + existingShop.getId() + "§c!");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getRentPrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "Location shop " + shopId)) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        // Louer le shop
        shop.setRented(true);
        shop.setOwnerId(player.getUniqueId());
        shop.setOwnerName(player.getName());
        shop.setRentExpiry(System.currentTimeMillis() + configManager.getRentDuration());

        player.sendMessage("§a§lSHOP §8» §aVous avez revendiqué le shop §e" + shopId + " §apour §e" + price + " §abeacons!");

        // Mettre à jour les visuels (même si vides au début)
        plugin.getVisualManager().updateShopVisuals(shop);

        saveAll();
        return true;
    }

    public boolean extendShopRent(Player player) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getRentPrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons pour prolonger! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "Extension shop " + shop.getId())) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        // Prolonger la location
        long currentExpiry = Math.max(shop.getRentExpiry(), System.currentTimeMillis());
        shop.setRentExpiry(currentExpiry + configManager.getRentDuration());

        // Sortir de la période de grâce si applicable
        if (shop.isInGracePeriod()) {
            shop.setInGracePeriod(false);
            shop.setGraceExpiry(0);
            player.sendMessage("§a§lSHOP §8» §aVotre shop sort de la période de grâce!");
        }

        player.sendMessage("§a§lSHOP §8» §aVotre shop a été prolongé de 3 jours pour §e" + price + " §abeacons!");

        saveAll();
        return true;
    }

    public boolean addCustomMessage(Player player, String message) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getCustomMessagePrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "Message personnalisé")) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        shop.setCustomMessage(message);
        player.sendMessage("§a§lSHOP §8» §aMessage d'approche défini!");
        player.sendMessage("§7§lSHOP §8» §7Aperçu: " + ChatColor.translateAlternateColorCodes('&', message));

        saveAll();
        return true;
    }

    public boolean removeCustomMessage(Player player) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (shop.getCustomMessage() == null) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas de message personnalisé!");
            return false;
        }

        shop.setCustomMessage(null);
        player.sendMessage("§a§lSHOP §8» §aMessage personnalisé supprimé!");

        saveAll();
        return true;
    }

    public boolean addFloatingText(Player player, String text) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (shop.getFloatingTexts().size() >= 3) {
            player.sendMessage("§c§lSHOP §8» §cVous avez atteint la limite de textes flottants! §7(3 max)");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getFloatingTextPrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "Texte flottant")) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        // Créer le texte flottant au niveau de la tête du joueur
        Location headLocation = player.getLocation().clone();
        headLocation.add(0, 1.7, 0); // Ajouter 1.7 blocs pour placer au niveau de la tête
        Shop.FloatingText floatingText = new Shop.FloatingText(text, headLocation);
        shop.addFloatingText(floatingText);

        player.sendMessage("§a§lSHOP §8» §aTexte flottant ajouté au niveau de votre tête!");
        player.sendMessage("§7§lSHOP §8» §7Aperçu: " + ChatColor.translateAlternateColorCodes('&', text));

        // Mettre à jour l'affichage visuel
        plugin.getVisualManager().updateFloatingTexts(shop);

        saveAll();
        return true;
    }

    public boolean removeFloatingText(Player player, int index) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (index < 0 || index >= shop.getFloatingTexts().size()) {
            player.sendMessage("§c§lSHOP §8» §cIndex invalide! Utilisez §e/shop remove text §cpour voir la liste.");
            return false;
        }

        shop.removeFloatingText(index);
        player.sendMessage("§a§lSHOP §8» §aTexte flottant supprimé!");

        // Mettre à jour l'affichage visuel
        plugin.getVisualManager().updateFloatingTexts(shop);

        saveAll();
        return true;
    }

    public void listFloatingTexts(Player player) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return;
        }

        if (shop.getFloatingTexts().isEmpty()) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez aucun texte flottant!");
            return;
        }

        player.sendMessage("§6§lSHOP §8» §6Vos textes flottants:");
        for (int i = 0; i < shop.getFloatingTexts().size(); i++) {
            Shop.FloatingText ft = shop.getFloatingTexts().get(i);
            player.sendMessage("§7" + (i + 1) + ". " + ChatColor.translateAlternateColorCodes('&', ft.getText()));
        }
        player.sendMessage("§7Utilisez §e/shop remove text <numéro> §7pour supprimer.");
    }

    public boolean addNPC(Player player, String npcName) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (shop.hasNPC()) {
            player.sendMessage("§c§lSHOP §8» §cVous avez déjà un PNJ dans votre shop!");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getNpcPrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "PNJ")) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        shop.setHasNPC(true);
        shop.setNpcName(npcName);
        shop.setNpcLocation(player.getLocation());

        player.sendMessage("§a§lSHOP §8» §aPNJ §e" + ChatColor.translateAlternateColorCodes('&', npcName) + " §aajouté!");

        // Mettre à jour l'affichage visuel
        plugin.getVisualManager().updateNPC(shop);

        saveAll();
        return true;
    }

    public boolean removeNPC(Player player) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (!shop.hasNPC()) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas de PNJ!");
            return false;
        }

        shop.setHasNPC(false);
        shop.setNpcName(null);
        shop.setNpcLocation(null);

        player.sendMessage("§a§lSHOP §8» §aPNJ supprimé!");

        // Mettre à jour l'affichage visuel
        plugin.getVisualManager().removeNPC(shop.getId());

        saveAll();
        return true;
    }

    public boolean addMember(Player owner, String memberName) {
        Shop shop = getPlayerShop(owner.getUniqueId());
        if (shop == null) {
            owner.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        Player member = Bukkit.getPlayer(memberName);
        if (member == null) {
            owner.sendMessage("§c§lSHOP §8» §cJoueur introuvable!");
            return false;
        }

        if (shop.isMember(member.getUniqueId())) {
            owner.sendMessage("§c§lSHOP §8» §cCe joueur est déjà membre de votre shop!");
            return false;
        }

        shop.addMember(member.getUniqueId());
        owner.sendMessage("§a§lSHOP §8» §e" + memberName + " §aa été ajouté à votre shop!");
        member.sendMessage("§a§lSHOP §8» §aVous avez été ajouté au shop de §e" + owner.getName() + "§a!");

        saveAll();
        return true;
    }

    public boolean removeMember(Player owner, String memberName) {
        Shop shop = getPlayerShop(owner.getUniqueId());
        if (shop == null) {
            owner.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        Player member = Bukkit.getPlayer(memberName);
        UUID memberId = member != null ? member.getUniqueId() : null;

        // Chercher par nom si le joueur n'est pas en ligne
        if (memberId == null) {
            for (UUID uuid : shop.getMembers()) {
                Player offlinePlayer = Bukkit.getPlayer(uuid);
                if (offlinePlayer != null && offlinePlayer.getName().equalsIgnoreCase(memberName)) {
                    memberId = uuid;
                    break;
                }
            }
        }

        if (memberId == null || !shop.isMember(memberId)) {
            owner.sendMessage("§c§lSHOP §8» §cCe joueur n'est pas membre de votre shop!");
            return false;
        }

        shop.removeMember(memberId);
        owner.sendMessage("§a§lSHOP §8» §e" + memberName + " §aa été retiré de votre shop!");

        if (member != null) {
            member.sendMessage("§c§lSHOP §8» §cVous avez été retiré du shop de §e" + owner.getName() + "§c!");
        }

        saveAll();
        return true;
    }

    public boolean boostAdvertisement(Player player) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (shop.getAdvertisement() == null) {
            player.sendMessage("§c§lSHOP §8» §cVous devez d'abord créer une annonce!");
            return false;
        }

        if (shop.isAdvertisementBoosted()) {
            player.sendMessage("§c§lSHOP §8» §cVotre annonce est déjà boostée!");
            return false;
        }

        long price = hook.calculatePrice(player.getUniqueId(), configManager.getBoostPrice());

        if (!hook.hasBeacons(player.getUniqueId(), price)) {
            player.sendMessage("§c§lSHOP §8» §cVous n'avez pas assez de beacons! §7(§e" + price + " §7beacons requis)");
            return false;
        }

        if (!hook.processBeaconTransaction(player, price, "Boost annonce")) {
            player.sendMessage("§c§lSHOP §8» §cErreur lors de la transaction!");
            return false;
        }

        shop.setAdvertisementBoostExpiry(System.currentTimeMillis() + configManager.getBoostDuration());
        player.sendMessage("§a§lSHOP §8» §aVotre annonce a été boostée pour 1 heure!");

        saveAll();
        return true;
    }

    public boolean placeBeacon(Player player, Location location) {
        Shop shop = getPlayerShop(player.getUniqueId());
        if (shop == null) {
            player.sendMessage("§c§lSHOP §8» §cVous ne possédez aucun shop!");
            return false;
        }

        if (!shop.containsLocation(location)) {
            player.sendMessage("§c§lSHOP §8» §cVous ne pouvez placer des beacons que dans votre shop!");
            return false;
        }

        if (shop.hasBeacon()) {
            player.sendMessage("§c§lSHOP §8» §cVous ne pouvez avoir qu'un seul beacon dans votre shop!");
            return false;
        }

        shop.setBeaconLocation(location);
        shop.setHasBeacon(true);

        saveAll();
        return true;
    }

    // ===============================
    // GETTERS ET UTILITAIRES
    // ===============================

    public Shop getShop(String shopId) {
        return shops.get(shopId);
    }

    public Shop getPlayerShop(UUID playerId) {
        return shops.values().stream()
                .filter(shop -> shop.isRented() && Objects.equals(shop.getOwnerId(), playerId))
                .findFirst()
                .orElse(null);
    }

    public Shop getShopAtLocation(Location location) {
        return shops.values().stream()
                .filter(shop -> shop.containsLocation(location))
                .findFirst()
                .orElse(null);
    }

    public List<Shop> getAllShops() {
        return new ArrayList<>(shops.values());
    }

    public List<Shop> getRentedShops() {
        return shops.values().stream()
                .filter(shop -> shop.isRented() && shop.getStatus() == Shop.ShopStatus.RENTED)
                .collect(Collectors.toList());
    }

    public List<Shop> getAvailableShops() {
        return shops.values().stream()
                .filter(shop -> shop.getStatus() == Shop.ShopStatus.AVAILABLE)
                .collect(Collectors.toList());
    }

    public List<Shop> getShopsWithAdvertisements() {
        return shops.values().stream()
                .filter(shop -> shop.isRented() && shop.getAdvertisement() != null && shop.getAdvertisement().isActive())
                .sorted((a, b) -> {
                    // Trier par boost puis par date de création
                    if (a.isAdvertisementBoosted() && !b.isAdvertisementBoosted()) return -1;
                    if (!a.isAdvertisementBoosted() && b.isAdvertisementBoosted()) return 1;
                    return Long.compare(b.getAdvertisement().getCreatedAt(), a.getAdvertisement().getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    public boolean canPlayerBuild(Player player, Location location) {
        Shop shop = getShopAtLocation(location);
        if (shop == null) return true; // Pas dans un shop

        Shop.ShopStatus status = shop.getStatus();

        if (status == Shop.ShopStatus.AVAILABLE) return false; // Shop libre
        if (status == Shop.ShopStatus.EXPIRED) return false; // Shop expiré

        if (status == Shop.ShopStatus.GRACE_PERIOD) {
            // En période de grâce, seuls les membres peuvent construire
            return shop.isMember(player.getUniqueId());
        }

        return shop.isMember(player.getUniqueId()); // Propriétaire ou membre
    }

    public boolean canPlayerBreak(Player player, Location location) {
        // Vérification spéciale pour les beacons
        if (location.getBlock().getType() == Material.BEACON) {
            Shop shop = getShopAtLocation(location);
            if (shop != null && shop.hasBeacon() && location.equals(shop.getBeaconLocation())) {
                if (shop.isMember(player.getUniqueId())) {
                    shop.setBeaconLocation(null);
                    shop.setHasBeacon(false);
                    saveAll();
                    return true;
                }
                return false;
            }
        }

        return canPlayerBuild(player, location);
    }

    public Map<String, Shop> getShops() {
        return new HashMap<>(shops);
    }
}