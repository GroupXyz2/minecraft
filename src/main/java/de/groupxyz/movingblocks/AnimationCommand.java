package de.groupxyz.movingblocks;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AnimationCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final AnimationManager animationManager;
    private final List<String> mainCommands = Arrays.asList(
            "stick", "create", "select", "play", "stop", "speed",
            "clear", "delete", "list", "enable", "disable", "frame",
            "mode", "preview", "duplicate", "rename", "deselect",
            "finalize", "startglobal", "stopglobal"
    );

    public AnimationCommand(Plugin plugin, AnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "stick":
                ItemStack stick = animationManager.createSelectionStick();
                player.getInventory().addItem(stick);
                player.sendMessage("§aYou received a Block Selection Stick, hold sneak to mass select!");
                return true;

            case "create":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb create <name>");
                    return true;
                }
                animationManager.createAnimation(player, args[1]);
                return true;

            case "select":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb select <name>");
                    return true;
                }
                animationManager.selectAnimation(player, args[1]);
                return true;

            case "play":
                animationManager.startAnimation(player);
                return true;

            case "stop":
                animationManager.stopAnimation(player);
                return true;

            case "speed":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb speed <ticks>");
                    return true;
                }

                try {
                    int speed = Integer.parseInt(args[1]);
                    animationManager.setAnimationSpeed(player, speed);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cPlease enter a valid number!");
                }
                return true;

            case "clear":
                animationManager.clearFrames(player);
                return true;

            case "delete":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb delete <name>");
                    return true;
                }
                animationManager.deleteAnimation(player, args[1]);
                return true;

            case "list":
                animationManager.listAnimations(player);
                return true;

            case "enable":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb enable <name>");
                    return true;
                }
                if (!animationManager.isAnimationEnabled(args[1])) {
                    animationManager.toggleAnimationStatus(player, args[1]);
                }
                return true;

            case "disable":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb disable <name>");
                    return true;
                }

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    String currentAnim = animationManager.getPlayerCurrentAnimation(p.getUniqueId());
                    if (args[1].equals(currentAnim) && animationManager.isAnimationRunning(p.getUniqueId())) {
                        animationManager.stopAnimation(p);
                    }
                }

                if (animationManager.isAnimationEnabled(args[1])) {
                    animationManager.toggleAnimationStatus(player, args[1]);
                }
                return true;

            case "frame":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb frame <add|delete|preview> [frame_number]");
                    return true;
                }

                if (args[1].equalsIgnoreCase("add")) {
                    animationManager.createFrame(player);
                } else if (args[1].equalsIgnoreCase("delete")) {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /mb frame delete <frame_number>");
                        return true;
                    }
                    try {
                        int frameIndex = Integer.parseInt(args[2]);
                        animationManager.deleteFrame(player, frameIndex);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cPlease enter a valid frame number!");
                    }
                } else if (args[1].equalsIgnoreCase("preview")) {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /mb frame preview <frame_number>");
                        return true;
                    }
                    try {
                        int frameIndex = Integer.parseInt(args[2]);
                        animationManager.previewFrame(player, frameIndex);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cPlease enter a valid frame number!");
                    }
                } else {
                    player.sendMessage("§cUnknown frame subcommand. Use add, delete, or preview.");
                }
                return true;

            case "mode":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb mode <animation_name>");
                    return true;
                }
                animationManager.toggleAnimationMode(player, args[1]);
                return true;

            case "duplicate":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /mb duplicate <source_name> <target_name>");
                    return true;
                }
                animationManager.duplicateAnimation(player, args[1], args[2]);
                return true;

            case "rename":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /mb rename <old_name> <new_name>");
                    return true;
                }
                animationManager.renameAnimation(player, args[1], args[2]);
                return true;

            case "deselect":
                animationManager.deselectAllBlocks(player);
                return true;

            case "finalize":
                animationManager.finalizeAnimation(player);
                return true;

            case "startglobal":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb startglobal <name>");
                    return true;
                }
                animationManager.startGlobalAnimation(args[1]);
                return true;

            case "stopglobal":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb stopglobal <name>");
                    return true;
                }
                animationManager.stopGlobalAnimation(args[1]);
                return true;

            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== MovingBlocks Commands ====");
        player.sendMessage("§e/mb stick §7- Get the selection stick");
        player.sendMessage("§e/mb create <name> §7- Create a new animation");
        player.sendMessage("§e/mb select <name> §7- Select an animation to work with");
        player.sendMessage("§e/mb frame add §7- Add a frame to current animation");
        player.sendMessage("§e/mb frame delete <number> §7- Delete a specific frame");
        player.sendMessage("§e/mb frame preview <number> §7- Preview a specific frame");
        player.sendMessage("§e/mb play §7- Start the current animation");
        player.sendMessage("§e/mb stop §7- Stop the animation");
        player.sendMessage("§e/mb speed <ticks> §7- Set animation speed");
        player.sendMessage("§e/mb clear §7- Clear all frames in current animation");
        player.sendMessage("§e/mb delete <name> §7- Delete an animation");
        player.sendMessage("§e/mb enable/disable <name> §7- Toggle animation status");
        player.sendMessage("§e/mb mode <name> §7- Toggle between Replace/Place+Remove mode");
        player.sendMessage("§e/mb list §7- List all available animations");
        player.sendMessage("§e/mb duplicate <source> <target> §7- Copy an animation");
        player.sendMessage("§e/mb rename <old> <new> §7- Rename an animation");
        player.sendMessage("§e/mb deselect §7- Deselect all blocks");
        player.sendMessage("§e/mb finalize §7- Finalize the current animation");
        player.sendMessage("§e/mb startglobal <name> §7- Start a global animation");
        player.sendMessage("§e/mb stopglobal <name> §7- Stop a global animation");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        if (args.length == 1) {
            return mainCommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("frame")) {
                return Arrays.asList("add", "delete", "preview").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("select") ||
                    args[0].equalsIgnoreCase("delete") ||
                    args[0].equalsIgnoreCase("enable") ||
                    args[0].equalsIgnoreCase("disable") ||
                    args[0].equalsIgnoreCase("mode") ||
                    args[0].equalsIgnoreCase("duplicate") ||
                    args[0].equalsIgnoreCase("rename")) {
                return animationManager.getAnimationNames().stream()
                        .filter(name -> name.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}