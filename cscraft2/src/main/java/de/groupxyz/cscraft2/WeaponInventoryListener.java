package de.groupxyz.cscraft2;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class WeaponInventoryListener implements Listener {
    private final WeaponSelect weaponSelect;
    private final Map<String, String> editingSessions = new HashMap<>();

    public WeaponInventoryListener(WeaponSelect weaponSelect) {
        this.weaponSelect = weaponSelect;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Waffen - ")) {
            event.setCancelled(true);
            handleWeaponShopClick(event);
        }
        else if (title.equals(ChatColor.DARK_RED + "Waffen Admin-Menü")) {
            event.setCancelled(true);
            handleAdminMenuClick(event);
        }
        else if (title.contains("Waffen Editor - ")) {
            handleWeaponEditorClick(event);
        }
        else if (title.contains("Waffe hinzufügen - ")) {
            handleWeaponAddClick(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Waffe hinzufügen - ") || title.contains("Waffen Editor - ")) {
            editingSessions.remove(event.getPlayer().getName());
        }
    }

    private void handleWeaponShopClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getCurrentItem() == null) return;

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem.getItemMeta() == null) return;

            if (clickedItem.getType() == Material.GOLD_INGOT &&
                    clickedItem.getItemMeta().getDisplayName().contains("Geld")) {
                return;
            }

            String team = "CT";
            if (event.getView().getTitle().contains(ChatColor.RED + "T")) {
                team = "T";
            }

            weaponSelect.buyWeapon(player, clickedItem, team);
        }
    }

    private void handleAdminMenuClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Du hast keine Berechtigung für dieses Menü!");
            player.closeInventory();
            return;
        }

        if (clickedItem.getItemMeta() != null) {
            String itemName = clickedItem.getItemMeta().getDisplayName();

            if (itemName.contains("CT-Waffen bearbeiten")) {
                editingSessions.put(player.getName(), "CT");
                weaponSelect.openTeamWeaponEditor(player, "CT");
            }
            else if (itemName.contains("T-Waffen bearbeiten")) {
                editingSessions.put(player.getName(), "T");
                weaponSelect.openTeamWeaponEditor(player, "T");
            }
            else if (itemName.contains("Start-Geld festlegen")) {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "Gib den neuen Startwert ein: /cs setmoney <betrag>");
            }
        }
    }

    private void handleWeaponEditorClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (!player.isOp()) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String team = editingSessions.getOrDefault(player.getName(), "CT");
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem.getType() == Material.EMERALD) {
            weaponSelect.addWeapon(player, team);
        }
        else if (clickedItem.getType() == Material.REDSTONE) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Waffen gespeichert!");
        }
        else if (clickedItem.getItemMeta() != null &&
                clickedItem.getItemMeta().getLore() != null &&
                clickedItem.getItemMeta().getLore().contains(ChatColor.RED + "Klicke zum Entfernen")) {
            weaponSelect.removeWeapon(player, clickedItem, team);
        }
    }

    private void handleWeaponAddClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (!player.isOp()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        if (slot == 10 || slot == 19) {
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem.getItemMeta() == null) return;

            String team = "CT";
            if (event.getView().getTitle().contains(ChatColor.RED + "T")) {
                team = "T";
            }

            String itemName = clickedItem.getItemMeta().getDisplayName();

            if (itemName.contains("+$100")) {
                weaponSelect.updatePrice(event.getInventory(), 100);
            }
            else if (itemName.contains("+$500")) {
                weaponSelect.updatePrice(event.getInventory(), 500);
            }
            else if (itemName.contains("+$1000")) {
                weaponSelect.updatePrice(event.getInventory(), 1000);
            }
            else if (itemName.contains("-$100")) {
                weaponSelect.updatePrice(event.getInventory(), -100);
            }
            else if (itemName.contains("-$500")) {
                weaponSelect.updatePrice(event.getInventory(), -500);
            }
            else if (itemName.contains("-$1000")) {
                weaponSelect.updatePrice(event.getInventory(), -1000);
            }

            else if (itemName.contains("+1")) {
                weaponSelect.updateAmmoAmount(event.getInventory(), 1);
            }
            else if (itemName.contains("+10")) {
                weaponSelect.updateAmmoAmount(event.getInventory(), 10);
            }
            else if (itemName.contains("-1")) {
                weaponSelect.updateAmmoAmount(event.getInventory(), -1);
            }
            else if (itemName.contains("-10")) {
                weaponSelect.updateAmmoAmount(event.getInventory(), -10);
            }

            else if (clickedItem.getType() == Material.EMERALD_BLOCK && itemName.contains("Speichern")) {
                weaponSelect.saveWeapon(player, event.getInventory(), team);
            }

            else if (clickedItem.getType() == Material.REDSTONE_BLOCK && itemName.contains("Abbrechen")) {
                weaponSelect.openTeamWeaponEditor(player, team);
            }
        }
    }
}