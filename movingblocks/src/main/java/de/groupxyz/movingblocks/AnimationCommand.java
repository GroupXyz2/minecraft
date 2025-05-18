package de.groupxyz.movingblocks;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class AnimationCommand implements CommandExecutor, TabCompleter, Listener {
    private final Plugin plugin;
    private final AnimationManager animationManager;
    private final AnimationEventHandler animationEventHandler;
    private final UpdateChecker updateChecker;
    private final List<String> mainCommands = Arrays.asList(
            "stick", "create", "select", "play", "stop", "speed",
            "clear", "delete", "list", "enable", "disable", "frame",
            "mode", "preview", "duplicate", "rename", "deselect",
            "finalize", "paste", "pasteframe", "info", "protect",
            "event", "multiselect", "sound", "checkupdate"
    );

    private final Map<UUID, Location> firstPoint = new HashMap<>();
    private final Map<UUID, Location> secondPoint = new HashMap<>();
    private final Set<UUID> worldEditMode = new HashSet<>();

    public AnimationCommand(Plugin plugin, AnimationManager animationManager, AnimationEventHandler animationEventHandler, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.animationEventHandler = animationEventHandler;
        this.updateChecker = updateChecker;
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
            case "multiselect":
                if (worldEditMode.contains(player.getUniqueId())) {
                    worldEditMode.remove(player.getUniqueId());
                    player.sendMessage("§aMulti selection mode disabled.");
                } else {
                    worldEditMode.add(player.getUniqueId());
                    player.sendMessage("§aMulti selection mode enabled. Use the stick to select two points.");
                }
                return true;

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
                if (args.length > 1) {
                    String name = args[1];
                    if (args.length > 2 && args[2].equalsIgnoreCase("global")) {
                        animationManager.startGlobalAnimation(name, player);
                    } else {
                        animationManager.startAnimation(player, name);
                    }
                } else {
                    animationManager.startAnimation(player);
                }
                return true;

            case "stop":
                if (args.length > 1) {
                    String name = args[1];
                    animationManager.stopAnimation(player, name);
                } else {
                    animationManager.stopAnimation(player);
                }
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
                    player.sendMessage("§cUsage: /mb play <name> global");
                    return true;
                }
                animationManager.startGlobalAnimation(args[1], player);
                return true;

            case "stopglobal":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb stop <name>");
                    return true;
                }
                animationManager.stopAnimation(player, args[1]);
                return true;

            case "paste":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb paste <name> [x y z]");
                    return true;
                }
                
                String animName = args[1];
                Location targetLoc = null;

                if (args.length >= 5) {
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        targetLoc = new Location(player.getWorld(), x, y, z);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid coordinates! Use numbers for x, y, z.");
                        return true;
                    }
                }
                
                animationManager.pasteAnimation(player, animName, targetLoc);
                return true;

            case "pasteframe":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb pasteframe <name> [x y z]");
                    return true;
                }
                
                String frameName = args[1];
                Location frameTargetLoc = null;

                if (args.length >= 5) {
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        frameTargetLoc = new Location(player.getWorld(), x, y, z);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid coordinates! Use numbers for x, y, z.");
                        return true;
                    }
                }
                
                animationManager.pasteAnimationFrame(player, frameName, frameTargetLoc, true);
                return true;

            case "info":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb info <name>");
                    return true;
                }
                animationManager.showAnimationInfo(player, args[1]);
                return true;
                
            case "event":
                if (args.length < 2) {
                    sendEventHelp(player);
                    return true;
                }
                
                switch (args[1].toLowerCase()) {
                    case "create":
                        if (args.length < 4) {
                            player.sendMessage("§cUsage: /mb event create <block|region> <id>");
                            return true;
                        }
                        
                        String eventId = args[3];
                        
                        if (animationEventHandler.getEvent(eventId) != null) {
                            player.sendMessage("§cAn event with ID '" + eventId + "' already exists!");
                            return true;
                        }
                        
                        if (args[2].equalsIgnoreCase("block")) {
                            createBlockEvent(player, eventId, args);
                        } else if (args[2].equalsIgnoreCase("region")) {
                            createRegionEvent(player, eventId, args);
                        } else {
                            player.sendMessage("§cUnknown event type. Use 'block' or 'region'.");
                        }
                        return true;
                        
                    case "list":
                        listEvents(player);
                        return true;
                        
                    case "delete":
                        if (args.length < 3) {
                            player.sendMessage("§cUsage: /mb event delete <id>");
                            return true;
                        }
                        
                        String deleteId = args[2];
                        if (animationEventHandler.unregisterEvent(deleteId)) {
                            player.sendMessage("§aEvent '" + deleteId + "' deleted successfully.");
                        } else {
                            player.sendMessage("§cEvent '" + deleteId + "' does not exist!");
                        }
                        return true;
                        
                    case "info":
                        if (args.length < 3) {
                            player.sendMessage("§cUsage: /mb event info <id>");
                            return true;
                        }
                        
                        String infoId = args[2];
                        AnimationEventHandler.AnimationEvent event = animationEventHandler.getEvent(infoId);
                        
                        if (event == null) {
                            player.sendMessage("§cEvent '" + infoId + "' does not exist!");
                            return true;
                        }
                        
                        showEventInfo(player, event);
                        return true;
                        
                    default:
                        sendEventHelp(player);
                        return true;
                }
                
            case "protect":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb protect <name> [on|off]");
                    return true;
                }
                
                String protectName = args[1];
                boolean setProtection = true;
                
                if (args.length > 2) {
                    String option = args[2].toLowerCase();
                    if (option.equals("off") || option.equals("false") || option.equals("disable")) {
                        setProtection = false;
                    }
                }
                
                animationManager.toggleProtection(player, protectName, setProtection);
                return true;

            case "sound":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb sound <add|remove|list>");
                    return true;
                }                if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 4) {
                        player.sendMessage("§cUsage: /mb sound add <frame_number> <sound_name> [radius] [global]");
                        return true;
                    }
                    try {
                        int frameIndex = Integer.parseInt(args[2]);
                        String soundName = args[3];
                        float radius = 10.0f; 
                        boolean isGlobal = false; 
                        
                        if (args.length >= 5) {
                            try {
                                radius = Float.parseFloat(args[4]);
                            } catch (NumberFormatException ex) {
                                player.sendMessage("§cInvalid radius value! Using default: 10.0");
                            }
                        }
                        
                        if (args.length >= 6) {
                            isGlobal = args[5].equalsIgnoreCase("true") || 
                                       args[5].equalsIgnoreCase("1") ||
                                       args[5].equalsIgnoreCase("yes") ||
                                       args[5].equalsIgnoreCase("global");
                        }
                        
                        animationManager.addSoundToFrame(player, frameIndex, soundName, radius, isGlobal);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cPlease enter a valid frame number!");
                    }
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length < 3) {
                        player.sendMessage("§cUsage: /mb sound remove <frame_number>");
                        return true;
                    }
                    try {
                        int frameIndex = Integer.parseInt(args[2]);
                        animationManager.removeSoundFromFrame(player, frameIndex);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cPlease enter a valid frame number!");
                    }
                } else if (args[1].equalsIgnoreCase("list")) {
                    animationManager.listSounds(player);
                } else {
                    player.sendMessage("§cUnknown sound subcommand. Use add, remove, or list.");
                }
                return true;

            case "checkupdate":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /mb checkupdate");
                    return true;
                }

                if (updateChecker != null) {
                    updateChecker.checkForUpdates();
                } else {
                    player.sendMessage("§cUpdate checker is not enabled.");
                }
                return true;

            default:
                sendHelp(player);
                return true;
        }
    }
    
    private void createBlockEvent(Player player, String eventId, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cUsage: /mb event create block <id> <anim_name> <type> [runOnce] [cooldown]");
            player.sendMessage("§cTypes: BUTTON_PRESS, BLOCK_WALK, LEVER_TOGGLE");
            return;
        }
        
        String animName = args[4];
        if (!animationManager.isAnimationExists(animName)) {
            player.sendMessage("§cAnimation '" + animName + "' does not exist!");
            return;
        }
        
        AnimationEventHandler.EventType eventType;
        try {
            String typeStr = args[5].toUpperCase();
            eventType = AnimationEventHandler.EventType.valueOf(typeStr);

            if (eventType != AnimationEventHandler.EventType.BUTTON_PRESS &&
                eventType != AnimationEventHandler.EventType.BLOCK_WALK &&
                eventType != AnimationEventHandler.EventType.LEVER_TOGGLE &&
                eventType != AnimationEventHandler.EventType.STOP_ANIMATION) {
                player.sendMessage("§cInvalid event type for block event. Use: BUTTON_PRESS, BLOCK_WALK, LEVER_TOGGLE, or STOP_ANIMATION");
                return;
            }
        } catch (Exception e) {
            player.sendMessage("§cInvalid event type. Use: BUTTON_PRESS, BLOCK_WALK, LEVER_TOGGLE, or STOP_ANIMATION");
            return;
        }

        boolean runOnce = false;
        long cooldown = 2000;

        if (args.length > 6) {
            runOnce = Boolean.parseBoolean(args[6]);
        }
        
        if (args.length > 7) {
            try {
                cooldown = Long.parseLong(args[7]);
                if (cooldown < 0) cooldown = 0;
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid cooldown value. Using default: 2000ms");
                cooldown = 2000;
            }
        }

        Location location = player.getLocation().getBlock().getLocation();
        
        try {
            AnimationEventHandler.AnimationEvent event = animationEventHandler.createBlockEvent(
                    eventId, animName, eventType, location, runOnce, cooldown);
            
            player.sendMessage("§aCreated " + eventType + " event '" + eventId + 
                    "' at your location for animation '" + animName + "'");
            player.sendMessage("§aRunOnce: " + runOnce + " | Cooldown: " + cooldown + "ms");
        } catch (Exception e) {
            player.sendMessage("§cError creating event: " + e.getMessage());
        }
    }
    
    private void createRegionEvent(Player player, String eventId, String[] args) {
        if (args.length < 5) {
            player.sendMessage("§cUsage: /mb event create region <id> <anim_name> <type> [runOnce] [cooldown]");
            player.sendMessage("§cTypes: REGION_ENTER, REGION_LEAVE");
            return;
        }
        
        String animName = args[4];
        if (!animationManager.isAnimationExists(animName)) {
            player.sendMessage("§cAnimation '" + animName + "' does not exist!");
            return;
        }
        
        AnimationEventHandler.EventType eventType;
        try {
            String typeStr = args[5].toUpperCase();
            eventType = AnimationEventHandler.EventType.valueOf(typeStr);

            if (eventType != AnimationEventHandler.EventType.REGION_ENTER &&
                eventType != AnimationEventHandler.EventType.REGION_LEAVE &&
                eventType != AnimationEventHandler.EventType.STOP_ANIMATION) {
                player.sendMessage("§cInvalid event type for region event. Use: REGION_ENTER, REGION_LEAVE, or STOP_ANIMATION");
                return;
            }
        } catch (Exception e) {
            player.sendMessage("§cInvalid event type. Use: REGION_ENTER, REGION_LEAVE, or STOP_ANIMATION");
            return;
        }

        boolean runOnce = false;
        long cooldown = 2000;

        if (args.length > 6) {
            runOnce = Boolean.parseBoolean(args[6]);
        }
        
        if (args.length > 7) {
            try {
                cooldown = Long.parseLong(args[7]);
                if (cooldown < 0) cooldown = 0;
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid cooldown value. Using default: 2000ms");
                cooldown = 2000;
            }
        }

        Location playerLoc = player.getLocation();
        Location min = playerLoc.clone().subtract(2, 2, 2);
        Location max = playerLoc.clone().add(2, 2, 2);
        
        player.sendMessage("§eCreating region from " + formatLocation(min) + " to " + formatLocation(max));
        
        try {
            AnimationEventHandler.AnimationEvent event = animationEventHandler.createRegionEvent(
                    eventId, animName, eventType, min, max, runOnce, cooldown);
            
            player.sendMessage("§aCreated " + eventType + " event '" + eventId + 
                    "' for animation '" + animName + "'");
            player.sendMessage("§aRunOnce: " + runOnce + " | Cooldown: " + cooldown + "ms");
        } catch (Exception e) {
            player.sendMessage("§cError creating event: " + e.getMessage());
        }
    }
    
    private void listEvents(Player player) {
        Collection<AnimationEventHandler.AnimationEvent> events = animationEventHandler.getEvents();
        
        if (events.isEmpty()) {
            player.sendMessage("§eNo events have been created yet.");
            return;
        }
        
        player.sendMessage("§6§l==== Animation Events ====");
        for (AnimationEventHandler.AnimationEvent event : events) {
            String eventType = event.getEventType().name();
            String animName = event.getAnimationName();
            boolean runOnce = event.isRunOnce();
            long cooldown = event.getCooldown();
            
            player.sendMessage("§e" + event.getId() + " §7- §b" + eventType + 
                    " §7- §a" + animName + 
                    " §7- " + (runOnce ? "§cOnce" : "§aRepeating") + 
                    " §7- §e" + cooldown + "ms");
        }
    }
    
    private void showEventInfo(Player player, AnimationEventHandler.AnimationEvent event) {
        player.sendMessage("§6§l==== Event: §e" + event.getId() + " §6§l====");
        player.sendMessage("§6› §eAnimation: §f" + event.getAnimationName());
        player.sendMessage("§6› §eType: §f" + event.getEventType().name());
        player.sendMessage("§6› §eRun Once: §f" + (event.isRunOnce() ? "Yes" : "No"));
        player.sendMessage("§6› §eCooldown: §f" + event.getCooldown() + "ms");
        
        switch (event.getEventType()) {
            case BUTTON_PRESS:
            case BLOCK_WALK:
            case LEVER_TOGGLE:
            case STOP_ANIMATION:
                Location loc = (Location) event.getParameter("location");
                if (loc != null) {
                    player.sendMessage("§6› §eLocation: §f" + formatLocation(loc));
                }
                break;
            
            case REGION_ENTER:
            case REGION_LEAVE:
                Location min = (Location) event.getParameter("min");
                Location max = (Location) event.getParameter("max");
                if (min != null && max != null) {
                    player.sendMessage("§6› §eRegion: §ffrom " + formatLocation(min) + " to " + formatLocation(max));
                }
                break;
        }
    }
    
    private String formatLocation(Location loc) {
        return "§e[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]";
    }
    
    private void sendEventHelp(Player player) {
        player.sendMessage("§6§l==== Animation Event Commands ====");
        player.sendMessage("§e/mb event create block <id> <anim_name> <type> [runOnce] [cooldown] §7- Create a block event");
        player.sendMessage("§e/mb event create region <id> <anim_name> <type> [runOnce] [cooldown] §7- Create a region event");
        player.sendMessage("§e/mb event list §7- List all events");
        player.sendMessage("§e/mb event delete <id> §7- Delete an event");
        player.sendMessage("§e/mb event info <id> §7- Show event details");
        player.sendMessage(" ");
        player.sendMessage("§6Block event types: §fBUTTON_PRESS, BLOCK_WALK, LEVER_TOGGLE, STOP_ANIMATION");
        player.sendMessage("§6Region event types: §fREGION_ENTER, REGION_LEAVE, STOP_ANIMATION");
        player.sendMessage("§6Example: §f/mb event create block button1 my_door BUTTON_PRESS false 2000");
        player.sendMessage("§6Example: §f/mb event create region entrance room1 REGION_ENTER true 5000");
        player.sendMessage("§6Example: §f/mb event create block stop_btn my_door STOP_ANIMATION false 2000");
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l==== MovingBlocks Commands ====");
        player.sendMessage("§e/mb stick §7- Get the selection stick");
        player.sendMessage("§e/mb create <name> §7- Create a new animation");
        player.sendMessage("§e/mb select <name> §7- Select an animation to work with");
        player.sendMessage("§e/mb frame add §7- Add a frame to current animation");
        player.sendMessage("§e/mb frame delete <number> §7- Delete a specific frame");
        player.sendMessage("§e/mb frame preview <number> §7- Preview a specific frame");        
        player.sendMessage("§e/mb sound add <frame> <sound> [radius] [global] §7- Add a sound to a specific frame");
        player.sendMessage("§e/mb sound remove <frame> §7- Remove a sound from a frame");
        player.sendMessage("§e/mb sound list §7- List all sounds in the animation");
        player.sendMessage("§e/mb play §7- Start the current animation");
        player.sendMessage("§e/mb play <name> §7- Start a specific animation");
        player.sendMessage("§e/mb play <name> global §7- Start a specific animation globally");
        player.sendMessage("§e/mb stop §7- Stop your current animation");
        player.sendMessage("§e/mb stop <name> §7- Stop a specific animation (global or yours)");
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
        player.sendMessage("§e/mb paste <name> [x y z] §7- Paste animation at location");
        player.sendMessage("§e/mb pasteframe <name> [x y z] §7- Paste first frame only");
        player.sendMessage("§e/mb info <name> §7- Show detailed information about an animation");
        player.sendMessage("§e/mb protect <name> [on|off] §7- Protect animation blocks from being destroyed");
        player.sendMessage("§e/mb event §7- Manage animation trigger events");
        player.sendMessage("§e/mb toggleaxe §7- Toggle WorldEdit-like selection mode");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!worldEditMode.contains(player.getUniqueId())) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().equals(Material.STICK)) {
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            firstPoint.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            player.sendMessage("§aFirst point set at: " + formatLocation(event.getClickedBlock().getLocation()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            secondPoint.put(player.getUniqueId(), event.getClickedBlock().getLocation());
            player.sendMessage("§aSecond point set at: " + formatLocation(event.getClickedBlock().getLocation()));

            Location first = firstPoint.get(player.getUniqueId());
            Location second = secondPoint.get(player.getUniqueId());
            if (first != null && second != null) {
                animationManager.selectArea(player, first, second);
            }
        }

        event.setCancelled(true);
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
            if (args[0].equalsIgnoreCase("event")) {
                return Arrays.asList("create", "list", "delete", "info").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }            if (args[0].equalsIgnoreCase("sound")) {
                return Arrays.asList("add", "remove", "list").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
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
                    args[0].equalsIgnoreCase("rename") ||
                    args[0].equalsIgnoreCase("play") ||
                    args[0].equalsIgnoreCase("stop") ||
                    args[0].equalsIgnoreCase("paste") ||
                    args[0].equalsIgnoreCase("pasteframe") ||
                    args[0].equalsIgnoreCase("info") ||
                    args[0].equalsIgnoreCase("protect")) {
                return animationManager.getAnimationNames().stream()
                        .filter(name -> name.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("create")) {
                return Arrays.asList("block", "region").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } 
            else if (args[0].equalsIgnoreCase("event") && 
                    (args[1].equalsIgnoreCase("delete") || args[1].equalsIgnoreCase("info"))) {
                return animationEventHandler.getEvents().stream()
                        .map(AnimationEventHandler.AnimationEvent::getId)
                        .filter(id -> id.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("protect")) {
                return Arrays.asList("on", "off").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("play")) {
                if ("global".startsWith(args[2].toLowerCase())) {
                    List<String> options = new ArrayList<>();
                    options.add("global");
                    return options;
                }
            }            if (args[0].equalsIgnoreCase("sound")) {
                if (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove")) {
                    if (!(sender instanceof Player)) {
                        return new ArrayList<>();
                    }
                    Player cmdPlayer = (Player) sender;
                    return animationManager.getFrameNumbers(cmdPlayer).stream()
                            .map(String::valueOf)
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                }
            }
        }        if (args.length == 4 && args[0].equalsIgnoreCase("sound") && args[1].equalsIgnoreCase("add")) {
            return animationManager.getSuggestableSounds().stream()
                    .filter(s -> s.toLowerCase().contains(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("sound") && args[1].equalsIgnoreCase("add")) {
            return Arrays.asList("5", "10", "20", "30", "50", "100").stream()
                    .filter(s -> s.startsWith(args[4]))
                    .collect(Collectors.toList());
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("sound") && args[1].equalsIgnoreCase("add")) {
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(args[5].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("event") && 
                args[1].equalsIgnoreCase("create")) {
            return animationManager.getAnimationNames().stream()
                    .filter(name -> name.startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("event") && 
                args[1].equalsIgnoreCase("create")) {
            
            if (args[2].equalsIgnoreCase("block")) {
                return Arrays.asList("BUTTON_PRESS", "BLOCK_WALK", "LEVER_TOGGLE", "STOP_ANIMATION").stream()
                        .filter(s -> s.startsWith(args[5].toUpperCase()))
                        .collect(Collectors.toList());
            } 
            else if (args[2].equalsIgnoreCase("region")) {
                return Arrays.asList("REGION_ENTER", "REGION_LEAVE", "STOP_ANIMATION").stream()
                        .filter(s -> s.startsWith(args[5].toUpperCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 7 && args[0].equalsIgnoreCase("event") && 
                args[1].equalsIgnoreCase("create")) {
            return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(args[6].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 8 && args[0].equalsIgnoreCase("event") && 
                args[1].equalsIgnoreCase("create")) {
            return Arrays.asList("1000", "2000", "5000", "10000").stream()
                    .filter(s -> s.startsWith(args[7]))
                    .collect(Collectors.toList());
        }

        if ((args[0].equalsIgnoreCase("paste") || args[0].equalsIgnoreCase("pasteframe")) && 
                args.length >= 3 && args.length <= 5) {
            Player player = (Player) sender;
            Location loc = player.getLocation();
            int coordIndex = args.length - 3;
            
            if (coordIndex == 0) {
                return Arrays.asList(String.valueOf(loc.getBlockX()));
            } else if (coordIndex == 1) {
                return Arrays.asList(String.valueOf(loc.getBlockY()));
            } else if (coordIndex == 2) {
                return Arrays.asList(String.valueOf(loc.getBlockZ()));
            }
        }

        return new ArrayList<>();
    }
}