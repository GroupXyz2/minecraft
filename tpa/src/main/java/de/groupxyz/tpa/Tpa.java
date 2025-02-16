package de.groupxyz.tpa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class Tpa extends JavaPlugin {

    private final HashMap<UUID, UUID> tpaRequests = new HashMap<>();
    private final HashMap<UUID, UUID> tpaHereRequests = new HashMap<>();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final HashMap<UUID, BukkitTask> expireTasks = new HashMap<>();
    private final HashMap<UUID, Long> tuvCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Tpa by GroupXyz starting...");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : expireTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        expireTasks.clear();
        tpaRequests.clear();
        tpaHereRequests.clear();
        cooldowns.clear();
        getLogger().info("Tpa shutting down.");
    }

    private static final String PREFIX = ChatColor.GOLD + "[TPA] " + ChatColor.RESET;

    private String addPrefix(String message) {
        return PREFIX + message;
    }

    private String formatMessage(String message, String sender, String receiver) {
        return ChatColor.translateAlternateColorCodes('&',
                addPrefix(message.replace("{sender}", sender).replace("{receiver}", receiver)));
    }

    private boolean hasPendingRequest(UUID playerUuid) {
        return tpaRequests.containsKey(playerUuid) || tpaHereRequests.containsKey(playerUuid);
    }

    private void clearPendingRequests(UUID playerUuid) {
        BukkitTask task = expireTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
        tpaRequests.remove(playerUuid);
        tpaHereRequests.remove(playerUuid);
    }

    private boolean handleCooldown(Player player) {
        if (cooldowns.containsKey(player.getUniqueId()) &&
                System.currentTimeMillis() - cooldowns.get(player.getUniqueId()) < getConfig().getInt("cooldown") * 1000L) {
            long timeLeft = (getConfig().getInt("cooldown") * 1000L - (System.currentTimeMillis() - cooldowns.get(player.getUniqueId()))) / 1000;
            player.sendMessage(addPrefix(ChatColor.RED + "Warte " + timeLeft + " Sekunden, bevor du erneut eine TPA senden kannst."));
            return true;
        }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(addPrefix(ChatColor.RED + "Nur Spieler können diesen Command nutzen!"));
            return true;
        }

        Player player = (Player) sender;
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("tpa") || commandName.equals("tpahere")) {
            boolean isTpaHere = commandName.equals("tpahere");
            String permission = isTpaHere ? "tpa.here" : "tpa.use";

            if (!player.hasPermission(permission)) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!"));
                return true;
            }

            if (args.length != 1) {
                String usage = isTpaHere ? "/tpahere <Spieler>" : "/tpa <Spieler>";
                player.sendMessage(addPrefix(ChatColor.RED + "Benutzung: " + usage));
                return true;
            }

            if (handleCooldown(player)) {
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(addPrefix(ChatColor.RED + "Spieler " + args[0] + " wurde nicht gefunden."));
                return true;
            }

            if (player.equals(target)) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du kannst dir selbst keine TPA-Anfrage senden!"));
                return true;
            }

            for (UUID key : tpaRequests.keySet()) {
                if (tpaRequests.get(key).equals(player.getUniqueId())) {
                    Player oldTarget = Bukkit.getPlayer(key);
                    if (oldTarget != null) {
                        oldTarget.sendMessage(addPrefix(ChatColor.RED + player.getName() + " hat die Teleportanfrage gecancellt."));
                    }
                    clearPendingRequests(key);
                    break;
                }
            }

            for (UUID key : tpaHereRequests.keySet()) {
                if (tpaHereRequests.get(key).equals(player.getUniqueId())) {
                    Player oldTarget = Bukkit.getPlayer(key);
                    if (oldTarget != null) {
                        oldTarget.sendMessage(addPrefix(ChatColor.RED + player.getName() + " hat die Teleportanfrage gecancellt."));
                    }
                    clearPendingRequests(key);
                    break;
                }
            }

            if (hasPendingRequest(target.getUniqueId())) {
                player.sendMessage(addPrefix(ChatColor.RED + "Der Spieler hat bereits eine ausstehende Anfrage."));
                return true;
            }

            // Store the request
            if (isTpaHere) {
                tpaHereRequests.put(target.getUniqueId(), player.getUniqueId());
            } else {
                tpaRequests.put(target.getUniqueId(), player.getUniqueId());
            }

            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

            BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
                boolean isRequestStillValid = false;

                if (isTpaHere) {
                    isRequestStillValid = tpaHereRequests.containsKey(target.getUniqueId()) &&
                            tpaHereRequests.get(target.getUniqueId()).equals(player.getUniqueId());
                } else {
                    isRequestStillValid = tpaRequests.containsKey(target.getUniqueId()) &&
                            tpaRequests.get(target.getUniqueId()).equals(player.getUniqueId());
                }

                if (isRequestStillValid) {
                    clearPendingRequests(target.getUniqueId());

                    if (player.isOnline()) {
                        player.sendMessage(addPrefix(ChatColor.RED + "Deine TPA-Anfrage an " + target.getName() + " ist abgelaufen."));
                    }

                    if (target.isOnline()) {
                        target.sendMessage(addPrefix(ChatColor.RED + "Die TPA-Anfrage von " + player.getName() + " ist abgelaufen."));
                    }
                }
            }, getConfig().getInt("request-expiration") * 20L);

            expireTasks.put(target.getUniqueId(), task);

            if (isTpaHere) {
                target.sendMessage(formatMessage(getConfig().getString("messages.tpahere-received",
                        "&6{sender} fordert dich auf, dich zu ihm zu teleportieren."), player.getName(), target.getName()));
                player.sendMessage(formatMessage(getConfig().getString("messages.tpahere-sent",
                        "&aAnfrage an {receiver} gesendet!"), player.getName(), target.getName()));
            } else {
                target.sendMessage(formatMessage(getConfig().getString("messages.tpa-received",
                        "&6{sender} will sich zu dir teleportieren."), player.getName(), target.getName()));
                player.sendMessage(formatMessage(getConfig().getString("messages.tpa-sent",
                        "&aAnfrage an {receiver} gesendet!"), player.getName(), target.getName()));
            }

            target.sendMessage(addPrefix(ChatColor.GREEN + "Nutze /tpaccept, um zu akzeptieren, oder /tpdeny, um abzulehnen."));

        } else if (commandName.equals("tpaccept")) {
            if (!player.hasPermission("tpa.accept")) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!"));
                return true;
            }

            boolean hasTpaRequest = tpaRequests.containsKey(player.getUniqueId());
            boolean hasTpaHereRequest = tpaHereRequests.containsKey(player.getUniqueId());

            if (!hasTpaRequest && !hasTpaHereRequest) {
                player.sendMessage(addPrefix(ChatColor.RED + "Es gibt keine ausstehende Anfrage."));
                return true;
            }

            UUID senderUUID = hasTpaRequest ? tpaRequests.get(player.getUniqueId()) : tpaHereRequests.get(player.getUniqueId());
            Player senderPlayer = Bukkit.getPlayer(senderUUID);

            if (senderPlayer == null || !senderPlayer.isOnline()) {
                player.sendMessage(addPrefix(ChatColor.RED + "Der Spieler ist nicht mehr online."));
                clearPendingRequests(player.getUniqueId());
                return true;
            }

            if (hasTpaRequest) {
                senderPlayer.teleport(player.getLocation());
                player.sendMessage(formatMessage(getConfig().getString("messages.tpa-accepted",
                        "&a{sender} wurde zu dir teleportiert!"), senderPlayer.getName(), player.getName()));
                senderPlayer.sendMessage(addPrefix(ChatColor.GREEN + "Du wurdest zu " + player.getName() + " teleportiert."));
            } else {
                player.teleport(senderPlayer.getLocation());
                senderPlayer.sendMessage(formatMessage(getConfig().getString("messages.tpahere-accepted",
                        "&a{receiver} hat sich zu dir teleportiert!"), senderPlayer.getName(), player.getName()));
                player.sendMessage(addPrefix(ChatColor.GREEN + "Du wurdest zu " + senderPlayer.getName() + " teleportiert."));
            }

            clearPendingRequests(player.getUniqueId());

        } else if (commandName.equals("tpdeny")) {
            if (!player.hasPermission("tpa.deny")) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!"));
                return true;
            }

            boolean hasTpaRequest = tpaRequests.containsKey(player.getUniqueId());
            boolean hasTpaHereRequest = tpaHereRequests.containsKey(player.getUniqueId());

            if (!hasTpaRequest && !hasTpaHereRequest) {
                player.sendMessage(addPrefix(ChatColor.RED + "Es gibt keine ausstehende Anfrage."));
                return true;
            }

            UUID senderUUID = hasTpaRequest ? tpaRequests.get(player.getUniqueId()) : tpaHereRequests.get(player.getUniqueId());
            Player senderPlayer = Bukkit.getPlayer(senderUUID);

            if (senderPlayer != null && senderPlayer.isOnline()) {
                String msgKey = hasTpaRequest ? "messages.tpa-denied" : "messages.tpahere-denied";
                senderPlayer.sendMessage(formatMessage(getConfig().getString(msgKey,
                        "&c{receiver} hat deine Anfrage abgelehnt."), senderPlayer.getName(), player.getName()));
            }

            player.sendMessage(addPrefix(ChatColor.RED + "Du hast die Anfrage abgelehnt."));
            clearPendingRequests(player.getUniqueId());

        } else if (commandName.equals("tpcancel")) {
            if (!player.hasPermission("tpa.cancel")) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!"));
                return true;
            }

            UUID targetUUID = null;
            boolean isTpaHereRequest = false;

            for (UUID key : tpaRequests.keySet()) {
                if (tpaRequests.get(key).equals(player.getUniqueId())) {
                    targetUUID = key;
                    break;
                }
            }

            if (targetUUID == null) {
                for (UUID key : tpaHereRequests.keySet()) {
                    if (tpaHereRequests.get(key).equals(player.getUniqueId())) {
                        targetUUID = key;
                        isTpaHereRequest = true;
                        break;
                    }
                }
            }

            if (targetUUID == null) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine ausstehende Anfrage."));
                return true;
            }

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                target.sendMessage(addPrefix(ChatColor.RED + player.getName() + " hat die Teleportanfrage zurückgezogen."));
            }

            player.sendMessage(addPrefix(ChatColor.RED + "Anfrage gecancellt."));
            clearPendingRequests(targetUUID);

        } else if (commandName.equals("tpreload")) {
            if (!player.hasPermission("tpa.reload")) {
                player.sendMessage(addPrefix(ChatColor.RED + "Du hast keine Berechtigung für diesen Befehl!"));
                return true;
            }

            reloadConfig();
            player.sendMessage(ChatColor.GREEN + "Config neu geladen.");
        } else if (commandName.equals("tpinfo")) {
            player.sendMessage(addPrefix(ChatColor.AQUA + "Tpa plugin"));
            player.sendMessage(addPrefix(ChatColor.AQUA + "Version: 1.0"));
            player.sendMessage(addPrefix(ChatColor.AQUA + "Author: GroupXyz"));
        } else if (commandName.equals("tüv")) {
            Random random = new Random();
            int chance = random.nextInt(3);
            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long cooldownTime = 600000;

            if (chance == 0) {
                player.sendMessage(ChatColor.GREEN + "<TÜV Prüfer> " + ChatColor.AQUA + "Sieht gut aus! TÜV Plakette erhalten!");
            } else if (chance == 1) {
                player.sendMessage(ChatColor.YELLOW + "<TÜV Prüfer> " + ChatColor.AQUA + "Da müssen wir nochmal drüber schauen!");
            } else {
                player.sendMessage(ChatColor.RED + "<TÜV Prüfer> " + ChatColor.RED + "NEIN, EINFACH NEIN!!!!!!!!!!!!!!!!!!!!!!");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!(tuvCooldowns.containsKey(playerUUID) && (currentTime - tuvCooldowns.get(playerUUID)) < cooldownTime)) {
                        p.sendMessage(ChatColor.RED + "!!! " + player.getName() + " ist durch den TÜV gefallen!" + " !!!");
                        tuvCooldowns.put(playerUUID, currentTime);
                        if (player.getUniqueId().equals(playerUUID)) {
                            if (p.getInventory().getHelmet() == null) {
                                ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
                                pumpkin.addEnchantment(Enchantment.BINDING_CURSE, 1);
                                p.getInventory().setHelmet(pumpkin);
                            }
                        }
                    }
                }
            }
        } else if (commandName.equals("coin")) {
            Random random = new Random();
            int chance = random.nextInt(2);

            if (chance == 0) {
                player.sendMessage(ChatColor.GREEN + "Kopf");
            } else {
                player.sendMessage(ChatColor.RED + "Zahl");
            }
        }

        return true;
    }
}