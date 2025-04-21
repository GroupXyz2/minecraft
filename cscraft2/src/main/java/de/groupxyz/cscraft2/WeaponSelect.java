package de.groupxyz.cscraft2;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeaponSelect {
    private final Map<String, List<ItemStack>> teamWeapons = new HashMap<>();
    private final Map<String, Map<String, ItemStack>> teamAmmo = new HashMap<>();
    private final Map<String, Map<String, Integer>> ammoAmounts = new HashMap<>();
    private final Map<String, Integer> weaponPrices = new HashMap<>();
    private final Map<String, Integer> playerMoney = new HashMap<>();
    private final Map<ItemStack, String> weaponToConfigKey = new HashMap<>();
    private final Map<String, String> weaponIdToConfigKey = new HashMap<>();

    private final File weaponConfigFile;
    private FileConfiguration weaponConfig;
    private int defaultMoney;

    public WeaponSelect() {
        weaponConfigFile = new File(Cscraft2.getInstance().getDataFolder(), "weapons.yml");
        loadWeaponsConfig();
        logDebug("WeaponSelect initialisiert. Standard-Geld: $" +
                weaponConfig.getInt("default_money", defaultMoney));
    }

    private void loadWeaponsConfig() {
        if (!weaponConfigFile.exists()) {
            createEmptyWeaponsConfig();
        }

        weaponConfig = YamlConfiguration.loadConfiguration(weaponConfigFile);

        teamWeapons.clear();
        teamAmmo.clear();
        ammoAmounts.clear();
        weaponPrices.clear();
        weaponIdToConfigKey.clear();

        ConfigurationSection idMappings = weaponConfig.getConfigurationSection("id_mapping");
        if (idMappings != null) {
            for (String uniqueId : idMappings.getKeys(false)) {
                String configKey = idMappings.getString(uniqueId);
                if (configKey != null) {
                    weaponIdToConfigKey.put(uniqueId, configKey);
                    //logDebug("Lade ID-Mapping: " + uniqueId + " -> " + configKey);
                }
            }
        }

        loadTeamItems("CT");
        loadTeamItems("T");

        defaultMoney = weaponConfig.getInt("default_money", 800);

        debugWeapons();
        debugWeaponPrices();
    }

    private void loadTeamItems(String team) {
        List<ItemStack> weapons = new ArrayList<>();
        Map<String, ItemStack> ammoMap = new HashMap<>();
        Map<String, Integer> amountMap = new HashMap<>();

        ConfigurationSection teamSection = weaponConfig.getConfigurationSection("weapons." + team);
        if (teamSection != null) {
            for (String weaponId : teamSection.getKeys(false)) {
                ConfigurationSection weaponSection = teamSection.getConfigurationSection(weaponId);
                if (weaponSection != null) {
                    if (weaponSection.contains("item")) {
                        ItemStack weapon = weaponSection.getItemStack("item");
                        if (weapon != null) {
                            weapons.add(weapon);

                            String fullId = team + ":" + weaponId;
                            int price = weaponSection.getInt("price", 0);
                            weaponPrices.put(fullId, price);

                            weaponToConfigKey.put(weapon, weaponId);

                            //logDebug("Lade Waffe: " + fullId + " (" + getItemName(weapon) + ") für $" + price);

                            if (weaponSection.contains("ammo_item")) {
                                ItemStack ammoItem = weaponSection.getItemStack("ammo_item");
                                int ammoAmount = weaponSection.getInt("ammo_amount", 0);
                                if (ammoItem != null) {
                                    ammoMap.put(getItemName(weapon), ammoItem);
                                    amountMap.put(getItemName(weapon), ammoAmount);
                                    logDebug("  -> Munition: " + ammoAmount + "x " + getItemName(ammoItem));
                                }
                            }
                        }
                    }
                }
            }
        }

        teamWeapons.put(team, weapons);
        teamAmmo.put(team, ammoMap);
        ammoAmounts.put(team, amountMap);
    }

    private void createEmptyWeaponsConfig() {
        weaponConfig = new YamlConfiguration();
        weaponConfig.set("default_money", defaultMoney);
        try {
            weaponConfig.save(weaponConfigFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startWeaponSelect(Player player, String team, Boolean useDefaultMoney) {
        if (!useDefaultMoney) {
            resetPlayerMoney(player);
        } else {
            playerMoney.put(player.getName(), defaultMoney);
        }
        logDebug("Spieler " + player.getName() + " bekommt $" +
                playerMoney.get(player.getName()) + " Startgeld");
        openWeaponMenu(player, team);
    }

    private void resetPlayerMoney(Player player) {
        if (!playerMoney.containsKey(player.getName())) {
            int money = weaponConfig.getInt("default_money", defaultMoney);
            playerMoney.put(player.getName(), money);
            logDebug("Spieler " + player.getName() + " erhält $" + money + " Startgeld");
        }
    }

    public void openWeaponMenu(Player player, String team) {
        Inventory inventory = Bukkit.createInventory(player, 54,
                ChatColor.GOLD + "Waffen - " + (team.equals("CT") ? ChatColor.BLUE + "CT" : ChatColor.RED + "T"));

        List<ItemStack> weapons = teamWeapons.getOrDefault(team, new ArrayList<>());

        if (weapons.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Keine Waffen für Team " + team + " verfügbar!");
            logDebug("Keine Waffen für Team " + team + " gefunden!");
        }

        int slot = 0;
        for (ItemStack weapon : weapons) {
            if (weapon != null) {
                inventory.setItem(slot, createDisplayItem(weapon, player, team));
                slot++;
            }
        }

        ItemStack moneyItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta moneyMeta = moneyItem.getItemMeta();
        if (moneyMeta != null) {
            moneyMeta.setDisplayName(ChatColor.GOLD + "Geld");
            List<String> lore = new ArrayList<>();
            int currentMoney = playerMoney.getOrDefault(player.getName(), defaultMoney);
            lore.add(ChatColor.YELLOW + "$" + currentMoney);
            moneyMeta.setLore(lore);
            moneyItem.setItemMeta(moneyMeta);
        }
        inventory.setItem(53, moneyItem);

        player.openInventory(inventory);
    }

    private ItemStack createDisplayItem(ItemStack original, Player player, String team) {
        ItemStack display = original.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();

            String itemId = getItemId(team, original);
            int price = weaponPrices.getOrDefault(itemId, 0);

            //logDebug("Display für Waffe: " + getItemName(original) +
            //        ", ID: " + itemId +
            //        ", Preis: $" + price +
            //        ", Enthält Preis: " + weaponPrices.containsKey(itemId));

            lore.add(" ");
            lore.add(ChatColor.GRAY + "Preis: " + ChatColor.YELLOW + "$" + price);
            lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + itemId);

            String itemName = getItemName(original);
            if (teamAmmo.containsKey(team) && teamAmmo.get(team).containsKey(itemName)) {
                ItemStack ammoItem = teamAmmo.get(team).get(itemName);
                int amount = ammoAmounts.getOrDefault(team, new HashMap<>()).getOrDefault(itemName, 0);
                if (amount > 0) {
                    lore.add(ChatColor.GRAY + "Munition: " + ChatColor.AQUA + amount + "x " +
                            ChatColor.WHITE + getItemName(ammoItem));
                }
            }

            int money = playerMoney.getOrDefault(player.getName(), defaultMoney);
            if (money < price) {
                lore.add(ChatColor.RED + "Nicht genug Geld! ($" + money + ")");
            } else {
                lore.add(ChatColor.GREEN + "Klicken zum Kaufen ($" + money + ")");
            }

            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    public void buyWeapon(Player player, ItemStack clickedItem, String team) {
        if (!player.isOnline()) {
            return;
        }

        String clickedItemId = null;
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            for (String loreLine : clickedItem.getItemMeta().getLore()) {
                if (loreLine.startsWith(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY)) {
                    clickedItemId = ChatColor.stripColor(loreLine.substring(loreLine.indexOf(":") + 2));
                    logDebug("Extraktion der ID zum Kauf: " + clickedItemId);
                    break;
                }
            }
        }

        if (clickedItemId == null) {
            player.sendMessage(ChatColor.RED + "Keine gültige Waffen-ID gefunden!");
            return;
        }

        List<ItemStack> weapons = teamWeapons.getOrDefault(team, new ArrayList<>());
        ItemStack selectedWeapon = null;

        for (ItemStack weapon : weapons) {
            String itemId = getItemId(team, weapon);
            //logDebug("Vergleiche IDs: " + itemId + " mit " + clickedItemId);
            if (itemId != null && itemId.equals(clickedItemId)) {
                selectedWeapon = weapon;
                break;
            }
        }

        if (selectedWeapon == null) {
            player.sendMessage(ChatColor.RED + "Waffe nicht im Inventar gefunden!");
            return;
        }

        int price = weaponPrices.getOrDefault(clickedItemId, 0);
        int money = playerMoney.getOrDefault(player.getName(), defaultMoney);

        logDebug("Kauf von " + clickedItemId + " für $" + price + " (Spieler hat $" + money + ")");

        if (money < price) {
            player.sendMessage(ChatColor.RED + "Nicht genug Geld! Benötigt: $" + price);
            return;
        }

        playerMoney.put(player.getName(), money - price);
        logDebug("Geld aktualisiert: $" + money + " → $" + (money - price));

        try {
            ItemStack weaponToGive = selectedWeapon.clone();
            player.getInventory().addItem(weaponToGive);

            String exactName = getItemName(selectedWeapon);

            Map<String, ItemStack> ammoMap = teamAmmo.getOrDefault(team, new HashMap<>());
            if (ammoMap.containsKey(exactName)) {
                ItemStack ammo = ammoMap.get(exactName).clone();
                int ammoAmount = ammoAmounts.getOrDefault(team, new HashMap<>()).getOrDefault(exactName, 0);

                if (ammoAmount > 0) {
                    ammo.setAmount(ammoAmount);
                    player.getInventory().addItem(ammo);
                }
            }

            player.sendMessage(ChatColor.GREEN + "Du hast " + ChatColor.GOLD + selectedWeapon.getItemMeta().getDisplayName() +
                    ChatColor.GREEN + " für " + ChatColor.YELLOW + "$" + price + ChatColor.GREEN + " gekauft.");
        } catch (Exception e) {
            playerMoney.put(player.getName(), money);
            logDebug("Fehler beim Waffenkauf für " + player.getName() + ": " + e.getMessage());
        }

        openWeaponMenu(player, team);
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    private String getItemId(String team, ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String loreLine : item.getItemMeta().getLore()) {
                if (loreLine.startsWith(ChatColor.BLACK.toString())) {
                    String uniqueId = ChatColor.stripColor(loreLine);
                    if (weaponIdToConfigKey.containsKey(uniqueId)) {
                        String configKey = weaponIdToConfigKey.get(uniqueId);
                        //logDebug("ID gefunden für " + getItemName(item) + ": " + team + ":" + configKey);
                        return team + ":" + configKey;
                    }
                }
            }
        }

        String configKey = itemNameToConfigKey(getItemName(item));
        logDebug("Fallback ID für " + getItemName(item) + ": " + team + ":" + configKey);
        return team + ":" + configKey;
    }

    public void openAdminMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27,
                ChatColor.DARK_RED + "Waffen Admin-Menü");

        ItemStack ctEditor = new ItemStack(Material.BLUE_WOOL);
        ItemMeta ctMeta = ctEditor.getItemMeta();
        if (ctMeta != null) {
            ctMeta.setDisplayName(ChatColor.BLUE + "CT-Waffen bearbeiten");
            ctEditor.setItemMeta(ctMeta);
        }
        inventory.setItem(11, ctEditor);

        ItemStack tEditor = new ItemStack(Material.RED_WOOL);
        ItemMeta tMeta = tEditor.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName(ChatColor.RED + "T-Waffen bearbeiten");
            tEditor.setItemMeta(tMeta);
        }
        inventory.setItem(15, tEditor);

        ItemStack moneyEditor = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta moneyMeta = moneyEditor.getItemMeta();
        if (moneyMeta != null) {
            moneyMeta.setDisplayName(ChatColor.GOLD + "Start-Geld festlegen");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Aktuell: $" + weaponConfig.getInt("default_money", defaultMoney));
            moneyMeta.setLore(lore);
            moneyEditor.setItemMeta(moneyMeta);
        }
        inventory.setItem(13, moneyEditor);

        player.openInventory(inventory);
    }

    public void openTeamWeaponEditor(Player player, String team) {
        Inventory inventory = Bukkit.createInventory(player, 54,
                "Waffen Editor - " + (team.equals("CT") ? ChatColor.BLUE + "CT" : ChatColor.RED + "T"));

        List<ItemStack> weapons = teamWeapons.getOrDefault(team, new ArrayList<>());

        int slot = 0;
        for (ItemStack weapon : weapons) {
            ItemStack displayItem = weapon.clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();

                lore.removeIf(line ->
                        line.contains("Preis:") ||
                                line.contains("Klicke zum Entfernen"));

                String itemId = getItemId(team, weapon);
                int price = weaponPrices.getOrDefault(itemId, 0);

                lore.add(" ");
                lore.add(ChatColor.GRAY + "Preis: " + ChatColor.YELLOW + "$" + price);
                lore.add(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + itemId);
                lore.add(ChatColor.RED + "Klicke zum Entfernen");

                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            inventory.setItem(slot++, displayItem);
        }

        ItemStack addButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addButton.getItemMeta();
        if (addMeta != null) {
            addMeta.setDisplayName(ChatColor.GREEN + "Neue Waffe hinzufügen");
            addButton.setItemMeta(addMeta);
        }
        inventory.setItem(45, addButton);

        ItemStack saveButton = new ItemStack(Material.REDSTONE);
        ItemMeta saveMeta = saveButton.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GOLD + "Speichern & Schließen");
            saveButton.setItemMeta(saveMeta);
        }
        inventory.setItem(53, saveButton);

        player.openInventory(inventory);
    }

    public void removeWeapon(Player player, ItemStack clickedItem, String team) {
        String clickedItemId = null;
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            for (String loreLine : clickedItem.getItemMeta().getLore()) {
                if (loreLine.startsWith(ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY)) {
                    clickedItemId = loreLine.substring((ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY).length());
                    break;
                }
            }
        }

        if (clickedItemId == null) {
            player.sendMessage(ChatColor.RED + "Keine gültige Waffen-ID gefunden!");
            return;
        }

        List<ItemStack> weapons = teamWeapons.getOrDefault(team, new ArrayList<>());
        ItemStack toRemove = null;

        for (ItemStack w : weapons) {
            String itemId = getItemId(team, w);
            if (itemId != null && itemId.equals(clickedItemId)) {
                toRemove = w;
                break;
            }
        }

        if (toRemove != null) {
            weapons.remove(toRemove);

            teamWeapons.put(team, weapons);

            String configKey = null;
            if (clickedItemId.startsWith(team + ":")) {
                configKey = clickedItemId.substring((team + ":").length());
            }

            if (configKey != null) {
                weaponConfig.set("weapons." + team + "." + configKey, null);
                weaponPrices.remove(clickedItemId);

                List<String> keysToRemove = new ArrayList<>();
                for (String uniqueId : weaponIdToConfigKey.keySet()) {
                    if (weaponIdToConfigKey.get(uniqueId).equals(configKey)) {
                        keysToRemove.add(uniqueId);
                        weaponConfig.set("id_mapping." + uniqueId, null);
                    }
                }

                for (String keyToRemove : keysToRemove) {
                    weaponIdToConfigKey.remove(keyToRemove);
                }

                saveWeaponsConfig();

                try {
                    weaponConfig.save(weaponConfigFile);
                    player.sendMessage(ChatColor.GREEN + "Waffe \"" + getItemName(toRemove) + "\" erfolgreich entfernt.");
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Fehler beim Speichern der Konfiguration: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                player.sendMessage(ChatColor.RED + "Fehler beim Entfernen der Waffe: Ungültiger Config-Key.");
            }

            openTeamWeaponEditor(player, team);
        } else {
            player.sendMessage(ChatColor.RED + "Waffe mit ID " + clickedItemId + " nicht in der Waffenliste gefunden!");
        }
    }

    private String itemNameToConfigKey(String displayName) {
        String clean = ChatColor.stripColor(displayName);
        return clean.toLowerCase().replace(" ", "_");
    }

    private void saveWeaponsConfig() {
        try {
            weaponConfig.save(weaponConfigFile);
        } catch (IOException e) {
            Cscraft2.getInstance().getLogger().warning("Fehler beim Speichern der Waffenkonfiguration: " + e.getMessage());
        }
    }

    public void addWeapon(Player player, String team) {
        Inventory inventory = Bukkit.createInventory(player, 54,
                ChatColor.GREEN + "Waffe hinzufügen - " +
                        (team.equals("CT") ? ChatColor.BLUE + "CT" : ChatColor.RED + "T"));

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "Waffe hinzufügen");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "1. Platziere die Waffe in Slot 1");
            lore.add(ChatColor.GRAY + "2. Platziere die Munition in Slot 2 (optional)");
            lore.add(ChatColor.GRAY + "3. Stelle den Preis und die Menge ein");
            lore.add(ChatColor.GRAY + "4. Klicke auf Speichern");
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(0, info);

        ItemStack weaponSlot = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta weaponMeta = weaponSlot.getItemMeta();
        if (weaponMeta != null) {
            weaponMeta.setDisplayName(ChatColor.YELLOW + "Waffe hier platzieren");
            weaponSlot.setItemMeta(weaponMeta);
        }
        inventory.setItem(10, weaponSlot);

        ItemStack ammoSlot = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta ammoMeta = ammoSlot.getItemMeta();
        if (ammoMeta != null) {
            ammoMeta.setDisplayName(ChatColor.YELLOW + "Munition hier platzieren (optional)");
            ammoSlot.setItemMeta(ammoMeta);
        }
        inventory.setItem(19, ammoSlot);

        createPriceButton(inventory, 12, 100, "+$100");
        //createPriceButton(inventory, 13, 500, "+$500");
        //createPriceButton(inventory, 14, 1000, "+$1000");
        createPriceButton(inventory, 21, -100, "-$100");
        //createPriceButton(inventory, 22, -500, "-$500");
        //createPriceButton(inventory, 23, -1000, "-$1000");

        ItemStack priceDisplay = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceMeta = priceDisplay.getItemMeta();
        if (priceMeta != null) {
            priceMeta.setDisplayName(ChatColor.GOLD + "Aktueller Preis");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "$0");
            priceMeta.setLore(lore);
            priceDisplay.setItemMeta(priceMeta);
        }
        inventory.setItem(16, priceDisplay);

        //createAmmoButton(inventory, 25, 1, "+1");
        //createAmmoButton(inventory, 26, 10, "+10");
        //createAmmoButton(inventory, 34, -1, "-1");
        //createAmmoButton(inventory, 35, -10, "-10");

        ItemStack ammoDisplay = new ItemStack(Material.ARROW);
        ItemMeta ammoDisplayMeta = ammoDisplay.getItemMeta();
        if (ammoDisplayMeta != null) {
            ammoDisplayMeta.setDisplayName(ChatColor.AQUA + "Munitionsmenge");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.WHITE + "0");
            ammoDisplayMeta.setLore(lore);
            ammoDisplay.setItemMeta(ammoDisplayMeta);
        }
        inventory.setItem(28, ammoDisplay);

        ItemStack saveButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveButton.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ChatColor.GREEN + "Speichern");
            saveButton.setItemMeta(saveMeta);
        }
        inventory.setItem(49, saveButton);

        ItemStack cancelButton = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(ChatColor.RED + "Abbrechen");
            cancelButton.setItemMeta(cancelMeta);
        }
        inventory.setItem(45, cancelButton);

        player.openInventory(inventory);
    }

    private void createPriceButton(Inventory inventory, int slot, int amount, String label) {
        Material material = amount > 0 ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + label);
            button.setItemMeta(meta);
        }
        inventory.setItem(slot, button);
    }

    private void createAmmoButton(Inventory inventory, int slot, int amount, String label) {
        Material material = amount > 0 ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + label);
            button.setItemMeta(meta);
        }
        inventory.setItem(slot, button);
    }

    public void saveWeapon(Player player, Inventory inventory, String team) {
        ItemStack weapon = inventory.getItem(10);
        ItemStack ammoItem = inventory.getItem(19);

        if (weapon == null || weapon.getType() == Material.YELLOW_STAINED_GLASS_PANE) {
            player.sendMessage(ChatColor.RED + "Du musst eine Waffe platzieren!");
            return;
        }

        ItemStack priceDisplay = inventory.getItem(16);
        int price = 0;
        if (priceDisplay != null && priceDisplay.getItemMeta() != null &&
                priceDisplay.getItemMeta().hasLore()) {
            String priceText = priceDisplay.getItemMeta().getLore().get(0);
            priceText = ChatColor.stripColor(priceText).replace("$", "");
            try {
                price = Integer.parseInt(priceText);
            } catch (NumberFormatException e) {
                price = 0;
            }
        }

        int ammoAmount = 0;
        if (ammoItem != null && ammoItem.getType() != Material.YELLOW_STAINED_GLASS_PANE) {
            ItemStack ammoDisplay = inventory.getItem(28);
            if (ammoDisplay != null && ammoDisplay.getItemMeta() != null &&
                    ammoDisplay.getItemMeta().hasLore()) {
                String ammoText = ammoDisplay.getItemMeta().getLore().get(0);
                ammoText = ChatColor.stripColor(ammoText);
                try {
                    ammoAmount = Integer.parseInt(ammoText);
                } catch (NumberFormatException e) {
                    ammoAmount = 0;
                }
            }
        }

        String weaponName = getItemName(weapon);
        String baseConfigKey = itemNameToConfigKey(weaponName);

        String uniqueId = "weapon-" + System.currentTimeMillis();
        String configKey = baseConfigKey + "_" + System.currentTimeMillis();

        String path = "weapons." + team + "." + configKey;
        String fullId = team + ":" + configKey;

        weaponConfig.set("id_mapping." + uniqueId, configKey);

        weaponPrices.remove(fullId);

        weapon = weapon.clone();
        ItemMeta weaponMeta = weapon.getItemMeta();
        List<String> weaponLore = new ArrayList<>();
        weaponLore.add(ChatColor.BLACK + uniqueId);
        weaponMeta.setLore(weaponLore);
        weapon.setItemMeta(weaponMeta);

        weaponConfig.set(path + ".item", weapon);
        weaponConfig.set(path + ".price", price);
        weaponPrices.put(fullId, price);

        if (ammoItem != null && ammoItem.getType() != Material.YELLOW_STAINED_GLASS_PANE) {
            ammoItem = ammoItem.clone();
            weaponConfig.set(path + ".ammo_item", ammoItem);
            weaponConfig.set(path + ".ammo_amount", ammoAmount);
        }

        try {
            weaponConfig.save(weaponConfigFile);
        } catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Fehler beim Speichern der Waffe: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        loadWeaponsConfig();

        player.sendMessage(ChatColor.GREEN + "Waffe " + ChatColor.GOLD + weaponName +
                ChatColor.GREEN + " erfolgreich gespeichert!");
    }

    public void debugWeapons() {
        logDebug("=== Waffen-Debug ===");
        for (String team : new String[]{"CT", "T"}) {
            List<ItemStack> weapons = teamWeapons.getOrDefault(team, new ArrayList<>());
            logDebug(team + " Waffen: " + weapons.size());
            for (ItemStack weapon : weapons) {
                if (weapon != null) {
                    String name = getItemName(weapon);
                    String itemId = getItemId(team, weapon);
                    int price = weaponPrices.getOrDefault(itemId, 0);
                    //logDebug("- " + name + " ($" + price + ") [ID: " + itemId + "]");
                }
            }
        }
    }

    public void debugWeaponPrices() {
        logDebug("=== Waffen-Preise Debug ===");
        for (Map.Entry<String, Integer> entry : weaponPrices.entrySet()) {
            logDebug("ID: " + entry.getKey() + " = $" + entry.getValue());
        }
    }

    public void updatePrice(Inventory inventory, int amount) {
        ItemStack priceDisplay = inventory.getItem(16);
        if (priceDisplay != null && priceDisplay.getItemMeta() != null) {
            ItemMeta meta = priceDisplay.getItemMeta();
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String priceText = lore.get(0);
                priceText = ChatColor.stripColor(priceText).replace("$", "");
                int price;
                try {
                    price = Integer.parseInt(priceText);
                } catch (NumberFormatException e) {
                    price = 0;
                }

                price += amount;
                if (price < 0) price = 0;

                lore.set(0, ChatColor.YELLOW + "$" + price);
                meta.setLore(lore);
                priceDisplay.setItemMeta(meta);
            }
        }
    }

    public void updateAmmoAmount(Inventory inventory, int amount) {
        ItemStack ammoDisplay = inventory.getItem(28);
        if (ammoDisplay != null && ammoDisplay.getItemMeta() != null) {
            ItemMeta meta = ammoDisplay.getItemMeta();
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                String ammoText = lore.get(0);
                int ammo;
                try {
                    ammo = Integer.parseInt(ChatColor.stripColor(ammoText));
                } catch (NumberFormatException e) {
                    ammo = 0;
                }

                ammo += amount;
                if (ammo < 0) ammo = 0;

                lore.set(0, ChatColor.WHITE + String.valueOf(ammo));
                meta.setLore(lore);
                ammoDisplay.setItemMeta(meta);
            }
        }
    }

    public void setDefaultMoney(int amount) {
        defaultMoney = amount;
        weaponConfig.set("default_money", amount);
        try {
            weaponConfig.save(weaponConfigFile);
            logDebug("Startgeld auf $" + amount + " gesetzt");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateMoneyDisplay(Player player, Inventory inventory) {
        ItemStack moneyItem = inventory.getItem(53);
        if (moneyItem != null && moneyItem.getItemMeta() != null) {
            ItemMeta meta = moneyItem.getItemMeta();
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                lore.set(0, ChatColor.YELLOW + "$" + playerMoney.getOrDefault(player.getName(), defaultMoney));
                meta.setLore(lore);
                moneyItem.setItemMeta(meta);
            }
        }
    }

    public void logDebug(String message) {
        if (!Cscraft2.getInstance().isLogDebug()) return;
        Cscraft2.getInstance().getLogger().info("[DEBUG] " + message);
    }

    public void setPlayerMoney(String playerName, int money) {
        playerMoney.put(playerName, money);
        logDebug("Spielergeld für " + playerName + " auf $" + money + " gesetzt");
    }
}