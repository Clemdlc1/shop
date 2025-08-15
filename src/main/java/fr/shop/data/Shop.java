package fr.shop.data;

import fr.shop.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Représente un shop de joueur basé sur une zone de beacons
 */
public class Shop {

    private final String id;
    private String zoneId; // Remplace les coordonnées fixes
    private UUID ownerId;
    private String ownerName;
    private boolean rented;
    private long rentExpiry;
    private boolean inGracePeriod;
    private long graceExpiry;
    private Set<UUID> members;
    private String customMessage;
    private List<FloatingText> floatingTexts;
    private boolean hasNPC;
    private String npcName;
    private Location npcLocation;
    private ShopAdvertisement advertisement;
    private long advertisementBoostExpiry;
    private Set<Location> chestShops;
    private Location beaconLocation;
    private boolean hasBeacon;

    public Shop(String id, String zoneId) {
        this.id = id;
        this.zoneId = zoneId;
        this.members = new HashSet<>();
        this.floatingTexts = new ArrayList<>();
        this.chestShops = new HashSet<>();
        this.rented = false;
        this.hasNPC = false;
        this.hasBeacon = false;
        this.inGracePeriod = false;
        this.advertisementBoostExpiry = 0;
    }

    // ===============================
    // GETTERS ET SETTERS ZONE
    // ===============================

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    /**
     * Obtient la zone associée à ce shop
     */
    public Zone getZone(fr.shop.managers.ZoneManager zoneManager) {
        return zoneManager.getZone(zoneId);
    }

    /**
     * Obtient la location principale du shop (centre de la zone)
     */
    public Location getLocation(fr.shop.managers.ZoneManager zoneManager) {
        Zone zone = getZone(zoneManager);
        if (zone != null) {
            // Utiliser la téléportation si disponible, sinon le centre
            if (zone.hasTeleportLocation()) {
                return zone.getTeleportLocation();
            } else {
                return zone.getCenterLocation();
            }
        }
        return null;
    }

    /**
     * Vérifie si une location est dans ce shop (via la zone)
     */
    public boolean containsLocation(Location location, fr.shop.managers.ZoneManager zoneManager) {
        Zone zone = getZone(zoneManager);
        return zone != null && zone.containsLocation(location);
    }

    /**
     * Vérifie si une location est proche du shop
     */
    public boolean isNearShop(Location location, double distance, fr.shop.managers.ZoneManager zoneManager) {
        Location shopLoc = getLocation(zoneManager);
        if (shopLoc == null || location == null) return false;
        if (!location.getWorld().equals(shopLoc.getWorld())) return false;

        return location.distance(shopLoc) <= distance;
    }

    /**
     * Calcule la taille du shop en blocs (via la zone)
     */
    public int getSize(fr.shop.managers.ZoneManager zoneManager) {
        Zone zone = getZone(zoneManager);
        return zone != null ? zone.getBlockCount() : 0;
    }

    // ===============================
    // GETTERS ET SETTERS EXISTANTS
    // ===============================

    public static Shop loadFromConfig(String id, ConfigurationSection section) {
        // Charger d'abord le zoneId
        String zoneId = section.getString("zoneId");
        if (zoneId == null) {
            // Migration depuis l'ancien format - créer un zoneId basé sur l'ID du shop
            zoneId = "zone_Market_" + id.replaceAll("[^a-zA-Z0-9]", "");
        }

        Shop shop = new Shop(id, zoneId);

        if (section.contains("owner")) {
            shop.setOwnerId(UUID.fromString(section.getString("owner")));
        }
        shop.setOwnerName(section.getString("ownerName"));

        shop.setRented(section.getBoolean("rented"));
        shop.setRentExpiry(section.getLong("rentExpiry"));
        shop.setInGracePeriod(section.getBoolean("inGracePeriod"));
        shop.setGraceExpiry(section.getLong("graceExpiry"));

        // Membres
        List<String> memberList = section.getStringList("members");
        for (String member : memberList) {
            try {
                shop.addMember(UUID.fromString(member));
            } catch (IllegalArgumentException ignored) {
            }
        }

        shop.setCustomMessage(section.getString("customMessage"));

        // Floating texts
        List<?> floatingTextList = section.getList("floatingTexts", new ArrayList<>());
        for (Object obj : floatingTextList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ftMap = (Map<String, Object>) obj;
                try {
                    String text = (String) ftMap.get("text");
                    String world = (String) ftMap.get("world");
                    double x = ((Number) ftMap.get("x")).doubleValue();
                    double y = ((Number) ftMap.get("y")).doubleValue();
                    double z = ((Number) ftMap.get("z")).doubleValue();

                    Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                    shop.addFloatingText(new FloatingText(text, loc));
                } catch (Exception ignored) {
                }
            }
        }

        shop.setHasNPC(section.getBoolean("hasNPC"));
        shop.setNpcName(section.getString("npcName"));

        // NPC Location
        if (section.contains("npcLocation")) {
            shop.setNpcLocation(new Location(
                    Bukkit.getWorld(section.getString("npcLocation.world")),
                    section.getDouble("npcLocation.x"),
                    section.getDouble("npcLocation.y"),
                    section.getDouble("npcLocation.z"),
                    (float) section.getDouble("npcLocation.yaw"),
                    (float) section.getDouble("npcLocation.pitch")
            ));
        }

        // Advertisement
        if (section.contains("advertisement")) {
            shop.setAdvertisement(ShopAdvertisement.loadFromConfig(section.getConfigurationSection("advertisement")));
        }

        shop.setAdvertisementBoostExpiry(section.getLong("advertisementBoostExpiry"));

        // ChestShops
        List<String> chestShopList = section.getStringList("chestShops");
        for (String chestShopStr : chestShopList) {
            try {
                String[] parts = chestShopStr.split(",");
                Location chestLoc = new Location(
                        Bukkit.getWorld(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
                shop.addChestShop(chestLoc);
            } catch (Exception ignored) {
            }
        }

        // Beacon Location
        if (section.contains("beaconLocation")) {
            shop.setBeaconLocation(new Location(
                    Bukkit.getWorld(section.getString("beaconLocation.world")),
                    section.getInt("beaconLocation.x"),
                    section.getInt("beaconLocation.y"),
                    section.getInt("beaconLocation.z")
            ));
        }

        shop.setHasBeacon(section.getBoolean("hasBeacon"));

        return shop;
    }

    public String getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isRented() {
        return rented;
    }

    public void setRented(boolean rented) {
        this.rented = rented;
    }

    public long getRentExpiry() {
        return rentExpiry;
    }

    public void setRentExpiry(long rentExpiry) {
        this.rentExpiry = rentExpiry;
    }

    public boolean isRentExpired() {
        return System.currentTimeMillis() > rentExpiry;
    }

    public boolean isInGracePeriod() {
        return inGracePeriod;
    }

    public void setInGracePeriod(boolean inGracePeriod) {
        this.inGracePeriod = inGracePeriod;
    }

    public long getGraceExpiry() {
        return graceExpiry;
    }

    public void setGraceExpiry(long graceExpiry) {
        this.graceExpiry = graceExpiry;
    }

    public boolean isGraceExpired() {
        return System.currentTimeMillis() > graceExpiry;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId) || Objects.equals(ownerId, playerId);
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }

    public List<FloatingText> getFloatingTexts() {
        return floatingTexts;
    }

    public void addFloatingText(FloatingText floatingText) {
        if (floatingTexts.size() < 3) {
            floatingTexts.add(floatingText);
        }
    }

    public void removeFloatingText(int index) {
        if (index >= 0 && index < floatingTexts.size()) {
            floatingTexts.remove(index);
        }
    }

    public boolean hasNPC() {
        return hasNPC;
    }

    public void setHasNPC(boolean hasNPC) {
        this.hasNPC = hasNPC;
    }

    public String getNpcName() {
        return npcName;
    }

    public void setNpcName(String npcName) {
        this.npcName = npcName;
    }

    public Location getNpcLocation() {
        return npcLocation;
    }

    public void setNpcLocation(Location npcLocation) {
        this.npcLocation = npcLocation;
    }

    public ShopAdvertisement getAdvertisement() {
        return advertisement;
    }

    public void setAdvertisement(ShopAdvertisement advertisement) {
        this.advertisement = advertisement;
    }

    public long getAdvertisementBoostExpiry() {
        return advertisementBoostExpiry;
    }

    public void setAdvertisementBoostExpiry(long advertisementBoostExpiry) {
        this.advertisementBoostExpiry = advertisementBoostExpiry;
    }

    public boolean isAdvertisementBoosted() {
        return System.currentTimeMillis() < advertisementBoostExpiry;
    }

    public Set<Location> getChestShops() {
        return chestShops;
    }

    public void addChestShop(Location location) {
        chestShops.add(location);
    }

    public void removeChestShop(Location location) {
        chestShops.remove(location);
    }

    public Location getBeaconLocation() {
        return beaconLocation;
    }

    public void setBeaconLocation(Location beaconLocation) {
        this.beaconLocation = beaconLocation;
    }

    public boolean hasBeacon() {
        return hasBeacon;
    }

    public void setHasBeacon(boolean hasBeacon) {
        this.hasBeacon = hasBeacon;
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Vérifie le statut du shop
     */
    public ShopStatus getStatus() {
        if (!rented) return ShopStatus.AVAILABLE;
        if (isRentExpired()) {
            if (inGracePeriod && !isGraceExpired()) {
                return ShopStatus.GRACE_PERIOD;
            } else if (isGraceExpired()) {
                return ShopStatus.EXPIRED;
            }
        }
        return ShopStatus.RENTED;
    }

    public void saveToConfig(ConfigurationSection section) {
        // Sauvegarder le zoneId au lieu des coordonnées
        section.set("zoneId", zoneId);

        if (ownerId != null) {
            section.set("owner", ownerId.toString());
        }
        section.set("ownerName", ownerName);

        section.set("rented", rented);
        section.set("rentExpiry", rentExpiry);
        section.set("inGracePeriod", inGracePeriod);
        section.set("graceExpiry", graceExpiry);

        // Membres
        List<String> memberList = new ArrayList<>();
        for (UUID member : members) {
            memberList.add(member.toString());
        }
        section.set("members", memberList);

        section.set("customMessage", customMessage);

        // Floating texts
        List<Map<String, Object>> floatingTextList = new ArrayList<>();
        for (FloatingText ft : floatingTexts) {
            Map<String, Object> ftMap = new HashMap<>();
            ftMap.put("text", ft.getText());
            ftMap.put("world", ft.getLocation().getWorld().getName());
            ftMap.put("x", ft.getLocation().getX());
            ftMap.put("y", ft.getLocation().getY());
            ftMap.put("z", ft.getLocation().getZ());
            floatingTextList.add(ftMap);
        }
        section.set("floatingTexts", floatingTextList);

        section.set("hasNPC", hasNPC);
        section.set("npcName", npcName);

        if (npcLocation != null) {
            section.set("npcLocation.world", npcLocation.getWorld().getName());
            section.set("npcLocation.x", npcLocation.getX());
            section.set("npcLocation.y", npcLocation.getY());
            section.set("npcLocation.z", npcLocation.getZ());
            section.set("npcLocation.yaw", npcLocation.getYaw());
            section.set("npcLocation.pitch", npcLocation.getPitch());
        }

        if (advertisement != null) {
            ConfigurationSection adSection = section.createSection("advertisement");
            advertisement.saveToConfig(adSection);
        }

        section.set("advertisementBoostExpiry", advertisementBoostExpiry);

        // ChestShops
        List<String> chestShopList = new ArrayList<>();
        for (Location chestLoc : chestShops) {
            chestShopList.add(chestLoc.getWorld().getName() + "," +
                    chestLoc.getBlockX() + "," +
                    chestLoc.getBlockY() + "," +
                    chestLoc.getBlockZ());
        }
        section.set("chestShops", chestShopList);

        if (beaconLocation != null) {
            section.set("beaconLocation.world", beaconLocation.getWorld().getName());
            section.set("beaconLocation.x", beaconLocation.getBlockX());
            section.set("beaconLocation.y", beaconLocation.getBlockY());
            section.set("beaconLocation.z", beaconLocation.getBlockZ());
        }

        section.set("hasBeacon", hasBeacon);
    }

    // ===============================
    // CLASSES INTERNES
    // ===============================

    public enum ShopStatus {
        AVAILABLE,
        RENTED,
        GRACE_PERIOD,
        EXPIRED
    }

    public static class FloatingText {
        private String text;
        private Location location;

        public FloatingText(String text, Location location) {
            this.text = text;
            this.location = location;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }
    }
}