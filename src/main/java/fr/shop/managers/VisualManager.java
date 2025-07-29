package fr.shop.managers;

import fr.shop.PlayerShops;
import fr.shop.data.Shop;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour l'affichage des NPCs et textes flottants
 */
public class VisualManager {

    private final PlayerShops plugin;
    private final Map<String, List<ArmorStand>> floatingTexts;
    private final Map<String, Villager> npcs;

    public VisualManager(PlayerShops plugin) {
        this.plugin = plugin;
        this.floatingTexts = new ConcurrentHashMap<>();
        this.npcs = new ConcurrentHashMap<>();

        startUpdateTask();
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllVisuals();
            }
        }.runTaskTimer(plugin, 20L, 20L * 30L); // Mise à jour toutes les 30 secondes
    }

    // ===============================
    // TEXTES FLOTTANTS
    // ===============================

    public void updateFloatingTexts(Shop shop) {
        String shopId = shop.getId();

        // Supprimer les anciens textes
        removeFloatingTexts(shopId);

        if (!shop.isRented() || shop.getFloatingTexts().isEmpty()) {
            return;
        }

        List<ArmorStand> armorStands = new ArrayList<>();

        for (Shop.FloatingText floatingText : shop.getFloatingTexts()) {
            Location loc = floatingText.getLocation().clone();

            if (loc.getWorld() == null) continue;

            ArmorStand armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setCanPickupItems(false);
            armorStand.setCustomNameVisible(true);
            armorStand.setMarker(true);
            armorStand.setSmall(true);
            armorStand.setInvulnerable(true);

            String text = ChatColor.translateAlternateColorCodes('&', floatingText.getText());
            armorStand.setCustomName(text);

            armorStands.add(armorStand);
        }

        if (!armorStands.isEmpty()) {
            floatingTexts.put(shopId, armorStands);
        }
    }

    public void removeFloatingTexts(String shopId) {
        List<ArmorStand> stands = floatingTexts.remove(shopId);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }
    }

    // ===============================
    // NPCs
    // ===============================

    public void updateNPC(Shop shop) {
        String shopId = shop.getId();

        // Supprimer l'ancien NPC
        removeNPC(shopId);

        if (!shop.isRented() || !shop.hasNPC() || shop.getNpcLocation() == null) {
            return;
        }

        Location npcLoc = shop.getNpcLocation().clone();
        if (npcLoc.getWorld() == null) return;

        Villager villager = (Villager) npcLoc.getWorld().spawnEntity(npcLoc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCustomNameVisible(true);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5);

        String npcName = ChatColor.translateAlternateColorCodes('&', shop.getNpcName());
        villager.setCustomName("§e" + npcName);

        // Empêcher le commerce
        villager.setRecipes(new ArrayList<>());

        npcs.put(shopId, villager);
    }

    public void removeNPC(String shopId) {
        Villager villager = npcs.remove(shopId);
        if (villager != null && !villager.isDead()) {
            villager.remove();
        }
    }

    // ===============================
    // MISE À JOUR GLOBALE
    // ===============================

    public void updateAllVisuals() {
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            if (shop.isRented()) {
                // Vérifier si les visuels existent toujours
                boolean updateFloating = false;
                boolean updateNPC = false;

                // Vérifier les textes flottants
                List<ArmorStand> stands = floatingTexts.get(shop.getId());
                if (stands != null) {
                    for (ArmorStand stand : stands) {
                        if (stand == null || stand.isDead()) {
                            updateFloating = true;
                            break;
                        }
                    }
                } else if (!shop.getFloatingTexts().isEmpty()) {
                    updateFloating = true;
                }

                // Vérifier le NPC
                Villager villager = npcs.get(shop.getId());
                if (villager != null) {
                    if (villager.isDead()) {
                        updateNPC = true;
                    }
                } else if (shop.hasNPC()) {
                    updateNPC = true;
                }

                // Mettre à jour si nécessaire
                if (updateFloating) {
                    updateFloatingTexts(shop);
                }
                if (updateNPC) {
                    updateNPC(shop);
                }
            } else {
                // Shop pas loué, supprimer les visuels
                removeFloatingTexts(shop.getId());
                removeNPC(shop.getId());
            }
        }
    }

    public void updateShopVisuals(Shop shop) {
        updateFloatingTexts(shop);
        updateNPC(shop);
    }

    public void removeShopVisuals(String shopId) {
        removeFloatingTexts(shopId);
        removeNPC(shopId);
    }

    // ===============================
    // NETTOYAGE
    // ===============================

    public void shutdown() {
        // Supprimer tous les visuels
        for (List<ArmorStand> stands : floatingTexts.values()) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }
        floatingTexts.clear();

        for (Villager villager : npcs.values()) {
            if (villager != null && !villager.isDead()) {
                villager.remove();
            }
        }
        npcs.clear();
    }

    // ===============================
    // GETTERS
    // ===============================

    public boolean hasFloatingTexts(String shopId) {
        return floatingTexts.containsKey(shopId);
    }

    public boolean hasNPC(String shopId) {
        return npcs.containsKey(shopId);
    }

    public int getFloatingTextCount(String shopId) {
        List<ArmorStand> stands = floatingTexts.get(shopId);
        return stands != null ? stands.size() : 0;
    }
}