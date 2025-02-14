package de.groupxyz.shopchest;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class Shopchest extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ShopChest plugin by GroupXyz loading...");
    }

    @Override
    public void onDisable() {
        getLogger().info("ShopChest plugin shutting down.");
    }

    private boolean isShopBlock(Block block) {
        return block != null && (
                block.getType() == Material.CHEST ||
                        block.getType() == Material.BARREL ||
                        block.getType().name().endsWith("SHULKER_BOX") ||
                        (block.getState() instanceof DoubleChest)
        );
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        blocks.removeIf(block -> {
            if (isShopBlock(block)) {
                Block aboveBlock = block.getRelative(0, 1, 0);
                if (aboveBlock.getState() instanceof Sign) {
                    Sign sign = (Sign) aboveBlock.getState();
                    return sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]");
                }
            }
            return false;
        });

        for (Block block : blocks) {
            if (isShopBlock(block)) {
                event.setCancelled(true);
                break;
            } else if (block.getState() instanceof Sign) {
                Sign sign = (Sign) block.getState();
                if (sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        InventoryHolder holder = event.getSource().getHolder();
        if (holder instanceof Chest || holder instanceof Barrel || holder instanceof ShulkerBox) {
            Container chest = (Container) event.getSource().getHolder();
            Block aboveBlock = chest.getBlock().getRelative(0, 1, 0);
            if (aboveBlock.getState() instanceof Sign) {
                Sign sign = (Sign) aboveBlock.getState();
                if (sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();

        if (event.getLine(1).equalsIgnoreCase("SHOP")) {
            Block chestBlock = block.getRelative(0, -1, 0);
            if (!isShopBlock(chestBlock)) {
                event.getPlayer().sendMessage(ChatColor.RED + "Platziere das Schild auf einem Inventarblock.");
                event.setCancelled(true);
                return;
            }

            if (block.getState() instanceof Sign) {
                Sign existingSign = (Sign) block.getState();
                if (existingSign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "Du kannst ein bestehendes Shop-Schild nicht bearbeiten.");
                    return;
                }
            }

            Player player = event.getPlayer();
            try {
                int price = Integer.parseInt(event.getLine(2));
                int itemCount = Integer.parseInt(event.getLine(3));
                event.setLine(1, ChatColor.GREEN + "[SHOP]");
                event.setLine(2, ChatColor.BLUE + "Preis: " + price + " Dias");
                event.setLine(3, ChatColor.AQUA + "Erhalt: " + itemCount);
                event.setLine(0, player.getName());
                player.sendMessage(ChatColor.GREEN + "Shop erstellt! Preis pro Item: " + price + " Dias, Erhalt: " + itemCount);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Gib eine gültige Zahl für den Preis und die Anzahl an.");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (isShopBlock(block)) {
            Block neighbor = block.getRelative(1, 0, 0);
            protectShopChest(neighbor, event);

            neighbor = block.getRelative(-1, 0, 0);
            protectShopChest(neighbor, event);

            neighbor = block.getRelative(0, 0, 1);
            protectShopChest(neighbor, event);

            neighbor = block.getRelative(0, 0, -1);
            protectShopChest(neighbor, event);
        }
    }

    private void protectShopChest(Block neighbor, BlockPlaceEvent event) {
        if (isShopBlock(neighbor) && neighbor.getRelative(0, 1, 0).getState() instanceof Sign) {
            Sign sign = (Sign) neighbor.getRelative(0, 1, 0).getState();
            if (sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                if (!(sign.getLine(0).equals(event.getPlayer().getName()))) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Du darfst keine Kiste hier platzieren.");
                    event.setCancelled(true);
                } else {
                    event.getPlayer().sendMessage(ChatColor.YELLOW + "Vergiss nicht, diese Seite der Doppelkiste auch mit einem Schild zu sichern!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock.getState() instanceof Sign) {
            Sign sign = (Sign) clickedBlock.getState();
            if (sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                Player player = event.getPlayer();

                int price;
                int itemCount;

                try {
                    price = Integer.parseInt(ChatColor.stripColor(sign.getLine(2)).replaceAll("[^0-9]", ""));
                    itemCount = Integer.parseInt(ChatColor.stripColor(sign.getLine(3)).replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Ungueltiger Preis oder Anzahl.");
                    return;
                }

                Block chestBlock = sign.getBlock().getRelative(0, -1, 0);
                Inventory chestInventory = null;
                if (isShopBlock(chestBlock)) {
                    chestInventory = ((Container) chestBlock.getState()).getInventory();
                } else {
                    Bukkit.getLogger().warning("Fatal Error during check for chestInvetory!");
                    player.sendMessage("Fatal Error during check for chestInvetory!");
                }

                if (chestInventory != null) {

                    if (isShopBlock(chestBlock)) {

                        ItemStack diamonds = new ItemStack(Material.DIAMOND, price);
                        if (player.getInventory().containsAtLeast(diamonds, price)) {
                            ItemStack itemForSale = null;
                            int itemSlot = -1;
                            for (int i = 0; i < chestInventory.getSize(); i++) {
                                ItemStack item = chestInventory.getItem(i);
                                if (item != null && item.getAmount() > 0 && item.getType() != Material.DIAMOND) {
                                    itemForSale = item;
                                    itemSlot = i;
                                    break;
                                }
                            }

                            if (itemForSale != null && itemSlot != -1) {
                                if (itemForSale.getAmount() >= itemCount) {

                                    if (player.getInventory().firstEmpty() == -1) {
                                        player.sendMessage(ChatColor.RED + "Dein Inventar ist voll.");
                                        return;
                                    }

                                    if (chestInventory.firstEmpty() == -1) {
                                        player.sendMessage(ChatColor.RED + "Die Kiste ist voll.");
                                        return;
                                    }

                                    player.getInventory().removeItem(diamonds);
                                    chestInventory.addItem(diamonds.clone());

                                    for (int i = 0; i < itemCount; i++) {
                                        ItemStack purchasedItem = itemForSale.clone();
                                        purchasedItem.setAmount(1);
                                        player.getInventory().addItem(purchasedItem);
                                        itemForSale.setAmount(itemForSale.getAmount() - 1);
                                    }

                                    chestInventory.setItem(itemSlot, itemForSale.getAmount() > 0 ? itemForSale : null);
                                    player.sendMessage(ChatColor.GREEN + "Kauf erfolgreich! Du hast " + itemCount + " " + itemForSale.getType() + " fuer " + price + " Dias erhalten.");
                                } else {
                                    player.sendMessage(ChatColor.RED + "Nicht genuegend Artikel in der Kiste. Verfügbar: " + itemForSale.getAmount());
                                }
                            } else {
                                player.sendMessage(ChatColor.RED + "Der Shop ist leider leer.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "Du hast nicht genug Dias.");
                        }
                    }
                }
                if (!sign.getLine(0).equals(player.getName())) {
                    if (!player.isOp()) {
                        event.setCancelled(true);
                    }
                }
            }
        }

        if (isShopBlock(clickedBlock)) {
            Block aboveBlock = clickedBlock.getRelative(0, 1, 0);
            if (aboveBlock.getState() instanceof Sign) {
                Sign sign = (Sign) aboveBlock.getState();
                Player player = event.getPlayer();

                if (!sign.getLine(0).equals(player.getName())) {
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "Du darfst diese Kiste nicht oeffnen.");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {


        if (!event.getPlayer().isOp()) {

            Block block = event.getBlock();

            if (block.getState() instanceof Sign) {
                Player player = event.getPlayer();
                Sign sign = (Sign) block.getState();

                if (!sign.getLine(0).equals(player.getName()) && sign.getLine(1).equals(ChatColor.GREEN + "[SHOP]")) {
                    player.sendMessage(ChatColor.RED + "Du darfst dieses Schild nicht abbauen.");
                    event.setCancelled(true);
               }
            } else if (block.getState() instanceof Chest || block.getState() instanceof Barrel || block.getState() instanceof ShulkerBox) {
                Block aboveBlock = block.getRelative(0, 1, 0);
                if (aboveBlock.getState() instanceof Sign) {
                    Sign sign = (Sign) aboveBlock.getState();
                    Player player = event.getPlayer();

                    if (!sign.getLine(0).equals(player.getName())) {
                        player.sendMessage(ChatColor.RED + "Du darfst diese Kiste nicht abbauen.");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}
