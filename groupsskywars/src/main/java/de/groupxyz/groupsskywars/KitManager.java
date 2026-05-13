package de.groupxyz.groupsskywars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class KitManager implements Listener {
    private final Plugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, String> playerKits = new HashMap<>();
    private final Map<UUID, Long> abilityCooldowns = new HashMap<>();
    private final Map<UUID, Integer> playerDoubleJumps = new HashMap<>();
    private final Map<UUID, Long> enderpearlThrowTime = new HashMap<>();

    public KitManager(Plugin plugin) {
        this.plugin = plugin;
        registerDefaultKits();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void registerDefaultKits() {
        kits.put("scout", new Kit(
                "Scout",
                "Schnell und wendig!",
                Material.LEATHER_BOOTS,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null),
                        Kit.createItem(Material.BOW, 1, null),
                        Kit.createItem(Material.ARROW, 8, null),
                        Kit.createItem(Material.SNOWBALL, 16, null)
                ),
                new ArrayList<>(), // No permanent speed effect
                new ScoutAbility(),
                0
        ));

        kits.put("archer", new Kit(
                "Archer",
                "Meister des Bogens!",
                Material.BOW,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createEnchantedItem(Material.BOW, 1, null, Enchantment.ARROW_DAMAGE, 1),
                        Kit.createItem(Material.ARROW, 16, null),
                        Kit.createItem(Material.LEATHER_HELMET, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null)
                ),
                new ArrayList<>(),
                new ArcherAbility(),
                0
        ));

        kits.put("knight", new Kit(
                "Knight",
                "Ausgeglichener Krieger!",
                Material.IRON_SWORD,
                Arrays.asList(
                        Kit.createItem(Material.STONE_SWORD, 1, null),
                        Kit.createItem(Material.CHAINMAIL_HELMET, 1, null),
                        Kit.createItem(Material.CHAINMAIL_CHESTPLATE, 1, null),
                        Kit.createItem(Material.BREAD, 8, null)
                ),
                new ArrayList<>(),
                new KnightAbility(),
                1
        ));

        kits.put("pyro", new Kit(
                "Pyro",
                "Meister des Feuers!",
                Material.FLINT_AND_STEEL,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.FLINT_AND_STEEL, 1, null),
                        Kit.createItem(Material.LAVA_BUCKET, 1, null),
                        Kit.createItem(Material.FIRE_CHARGE, 4, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null)
                ),
                Arrays.asList(
                        new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false)
                ),
                null,
                2
        ));

        kits.put("enderman", new Kit(
                "Enderman",
                "Teleportiere dich durch die Map!",
                Material.ENDER_PEARL,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.ENDER_PEARL, 3, null),
                        Kit.createItem(Material.LEATHER_HELMET, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null)
                ),
                new ArrayList<>(),
                new EndermanAbility(),
                3
        ));

        kits.put("assassin", new Kit(
                "Assassin",
                "Schnell und tödlich!",
                Material.DIAMOND_SWORD,
                Arrays.asList(
                        Kit.createEnchantedItem(Material.STONE_SWORD, 1, null, Enchantment.DAMAGE_ALL, 1),
                        Kit.createItem(Material.LEATHER_HELMET, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null),
                        Kit.createItem(Material.BREAD, 4, null)
                ),
                Arrays.asList(
                        new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false),
                        new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false)
                ),
                new AssassinAbility(),
                4
        ));

        kits.put("builder", new Kit(
                "Builder",
                "Baue deine Festung!",
                Material.BRICKS,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.BRICKS, 64, null),
                        Kit.createItem(Material.OAK_PLANKS, 32, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null),
                        Kit.createItem(Material.SHEARS, 1, null)
                ),
                new ArrayList<>(),
                new BuilderAbility(),
                1
        ));

        kits.put("fisherman", new Kit(
                "Fisherman",
                "Nutze die Kraft des Wassers!",
                Material.FISHING_ROD,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createEnchantedItem(Material.FISHING_ROD, 1, null, Enchantment.DURABILITY, 2),
                        Kit.createItem(Material.WATER_BUCKET, 2, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null),
                        Kit.createItem(Material.COD, 8, null)
                ),
                new ArrayList<>(),
                null,
                2
        ));

        kits.put("bomber", new Kit(
                "Bomber",
                "Explosiver Spaß!",
                Material.TNT,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.SNOWBALL, 16, null),
                        Kit.createItem(Material.EGG, 16, null),
                        Kit.createItem(Material.TNT, 3, null),
                        Kit.createItem(Material.FLINT_AND_STEEL, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null)
                ),
                new ArrayList<>(),
                new BomberAbility(),
                3
        ));

        kits.put("jumper", new Kit(
                "Jumper",
                "Springe höher als alle anderen!",
                Material.FEATHER,
                Arrays.asList(
                        Kit.createItem(Material.WOODEN_SWORD, 1, null),
                        Kit.createItem(Material.LEATHER_HELMET, 1, null),
                        Kit.createItem(Material.LEATHER_CHESTPLATE, 1, null),
                        Kit.createItem(Material.BREAD, 4, null)
                ),
                new ArrayList<>(),
                new JumperAbility(),
                2
        ));
    }

    public void openKitSelector(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Wähle dein Kit");

        int slot = 0;
        for (Kit kit : kits.values()) {
            if (slot < 27) {
                inv.setItem(slot, kit.getDisplayItem());
                slot++;
            }
        }

        player.openInventory(inv);
    }

    public void selectKit(Player player, String kitName) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit != null) {
            playerKits.put(player.getUniqueId(), kitName.toLowerCase());
            player.sendMessage(ChatColor.GREEN + "Kit ausgewählt: " + ChatColor.GOLD + kit.getName());
        }
    }

    public void giveKit(Player player) {
        String kitName = playerKits.getOrDefault(player.getUniqueId(), "scout");
        Kit kit = kits.get(kitName);
        if (kit != null) {
            kit.applyKit(player);
            player.sendMessage(ChatColor.GREEN + "Kit erhalten: " + ChatColor.GOLD + kit.getName());
        }
    }

    public Kit getPlayerKit(Player player) {
        String kitName = playerKits.getOrDefault(player.getUniqueId(), "scout");
        return kits.get(kitName);
    }

    public Map<String, Kit> getKits() {
        return kits;
    }

    public boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        if (!abilityCooldowns.containsKey(uuid)) {
            return false;
        }
        return System.currentTimeMillis() < abilityCooldowns.get(uuid);
    }

    public void setCooldown(Player player, int seconds) {
        abilityCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public void resetPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        abilityCooldowns.remove(uuid);
        playerDoubleJumps.remove(uuid);
        enderpearlThrowTime.remove(uuid);
    }

    private boolean isPlayerInGame(Player player) {
        if (!(plugin instanceof Groupsskywars)) return false;
        Groupsskywars gsw = (Groupsskywars) plugin;
        GameInstance instance = gsw.getGameManager().getPlayerInstance(player.getUniqueId());
        return instance != null && instance.isGameStarted();
    }

    private class ScoutAbility extends KitAbility {
        public ScoutAbility() {
            super("Sprint Boost", "Schleichen für kurzen Speed-Boost", 10);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false));
            player.sendMessage(ChatColor.AQUA + "⚡ Sprint Boost aktiviert!");
        }
    }

    private class ArcherAbility extends KitAbility {
        public ArcherAbility() {
            super("Pfeil-Rückgewinnung", "Erhalte Pfeile bei Tötungen zurück", 0);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, 3));
        }
    }

    private class KnightAbility extends KitAbility {
        public KnightAbility() {
            super("Eiserne Haut", "Widerstand bei wenig Leben", 30);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 1));
            player.sendMessage(ChatColor.GRAY + "🛡 Eiserne Haut aktiviert!");
        }
    }

    private class EndermanAbility extends KitAbility {
        public EndermanAbility() {
            super("Teleport-Meister", "Kein Enderpearl-Schaden", 0);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
        }
    }

    private class AssassinAbility extends KitAbility {
        public AssassinAbility() {
            super("Schattenklinge", "Kurze Unsichtbarkeit beim Schleichen", 25);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0));
            player.sendMessage(ChatColor.DARK_GRAY + "👁 Schattenklinge aktiviert!");
        }
    }

    private class BuilderAbility extends KitAbility {
        public BuilderAbility() {
            super("Schnellbau", "Erhalte zusätzliche Blöcke", 45);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.getInventory().addItem(new ItemStack(Material.BRICKS, 32));
            player.sendMessage(ChatColor.GOLD + "🧱 Zusätzliche Blöcke erhalten!");
        }
    }

    private class BomberAbility extends KitAbility {
        public BomberAbility() {
            super("Explosionskraft", "Mehr Rückstoß bei Würfen", 20);
        }

        @Override
        public void register(Player player) {
        }

        @Override
        public void unregister(Player player) {
        }

        @Override
        public void onActivate(Player player) {
            player.getInventory().addItem(new ItemStack(Material.SNOWBALL, 8));
            player.getInventory().addItem(new ItemStack(Material.EGG, 8));
            player.sendMessage(ChatColor.YELLOW + "💣 Explosionskraft aktiviert!");
        }
    }

    private class JumperAbility extends KitAbility {
        public JumperAbility() {
            super("Doppelsprung", "Drücke Space zweimal zum Doppelsprung", 5);
        }

        @Override
        public void register(Player player) {
            playerDoubleJumps.put(player.getUniqueId(), 1);
            player.setAllowFlight(true);
        }

        @Override
        public void unregister(Player player) {
            playerDoubleJumps.remove(player.getUniqueId());
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        @Override
        public void onActivate(Player player) {
            Vector velocity = player.getVelocity();
            velocity.setY(0.8);
            player.setVelocity(velocity);
            player.sendMessage(ChatColor.WHITE + "🦅 Doppelsprung!");
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.5f);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !isPlayerInGame(player) || ticks > 200) {
                        this.cancel();
                        return;
                    }

                    if (player.isOnGround()) {
                        playerDoubleJumps.put(player.getUniqueId(), 1);
                        player.setAllowFlight(true);
                        this.cancel();
                    }

                    ticks += 5;
                }
            }.runTaskTimer(plugin, 5L, 5L);
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) return;

        if (!isPlayerInGame(player)) return;

        Kit kit = getPlayerKit(player);
        if (kit == null || kit.getAbility() == null) return;

        String kitName = playerKits.get(player.getUniqueId());

        if ("scout".equals(kitName) && !isOnCooldown(player)) {
            kit.getAbility().onActivate(player);
            setCooldown(player, kit.getAbility().getCooldownSeconds());
        }

        if ("assassin".equals(kitName) && !isOnCooldown(player)) {
            kit.getAbility().onActivate(player);
            setCooldown(player, kit.getAbility().getCooldownSeconds());
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!isPlayerInGame(player)) return;

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        Kit kit = getPlayerKit(player);
        if (kit == null || kit.getAbility() == null) return;

        String kitName = playerKits.get(player.getUniqueId());

        if ("jumper".equals(kitName)) {
            if (playerDoubleJumps.getOrDefault(player.getUniqueId(), 0) > 0) {
                event.setCancelled(true);
                kit.getAbility().onActivate(player);
                playerDoubleJumps.put(player.getUniqueId(), 0);
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!isPlayerInGame(player)) return;

        Kit kit = getPlayerKit(player);
        if (kit == null || kit.getAbility() == null) return;

        String kitName = playerKits.get(player.getUniqueId());

        if ("enderman".equals(kitName)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.ENDER_PEARL &&
                    (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                enderpearlThrowTime.put(player.getUniqueId(), System.currentTimeMillis());
            }
        }

        if ("builder".equals(kitName) && event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.BRICKS && !isOnCooldown(player)) {
                kit.getAbility().onActivate(player);
                setCooldown(player, kit.getAbility().getCooldownSeconds());
            }
        }

        if ("bomber".equals(kitName) && event.getAction() == Action.RIGHT_CLICK_AIR) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if ((item.getType() == Material.SNOWBALL || item.getType() == Material.EGG) && !isOnCooldown(player)) {
                kit.getAbility().onActivate(player);
                setCooldown(player, kit.getAbility().getCooldownSeconds());
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        if (!isPlayerInGame(attacker) || !isPlayerInGame(victim)) return;

        String kitName = playerKits.get(victim.getUniqueId());
        if ("knight".equals(kitName)) {
            if (victim.getHealth() - event.getFinalDamage() <= 6.0 && !isOnCooldown(victim)) {
                Kit kit = getPlayerKit(victim);
                if (kit != null && kit.getAbility() != null) {
                    kit.getAbility().onActivate(victim);
                    setCooldown(victim, kit.getAbility().getCooldownSeconds());
                }
            }
        }

        if ("archer".equals(playerKits.get(attacker.getUniqueId()))) {
            if (victim.getHealth() - event.getFinalDamage() <= 0) {
                Kit kit = getPlayerKit(attacker);
                if (kit != null && kit.getAbility() != null) {
                    kit.getAbility().onActivate(attacker);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (!isPlayerInGame(player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            String kitName = playerKits.get(player.getUniqueId());
            if ("enderman".equals(kitName)) {
                Long lastThrow = enderpearlThrowTime.get(player.getUniqueId());
                if (lastThrow != null) {
                    long timeSinceThrow = System.currentTimeMillis() - lastThrow;

                    if (timeSinceThrow <= 3000) {
                        double reducedDamage = event.getDamage() * 0.2;
                        event.setDamage(reducedDamage);

                        if (reducedDamage < 1.0) {
                            event.setCancelled(true);
                        }

                        if (timeSinceThrow > 2000) {
                            enderpearlThrowTime.remove(player.getUniqueId());
                        }
                    } else {
                        enderpearlThrowTime.remove(player.getUniqueId());
                    }
                }
            }
        }
    }
}



