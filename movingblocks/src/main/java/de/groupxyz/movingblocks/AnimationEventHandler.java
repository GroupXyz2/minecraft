package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AnimationEventHandler implements Listener {
    private final Plugin plugin;
    private final AnimationManager animationManager;
    
    private final Map<String, AnimationEvent> registeredEvents;
    
    private final Map<UUID, Map<String, Long>> playerCooldowns;
    
    private static final long DEFAULT_COOLDOWN = 2000; 
    
    public AnimationEventHandler(Plugin plugin, AnimationManager animationManager) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.registeredEvents = new HashMap<>();
        this.playerCooldowns = new HashMap<>();
        
        loadEvents();
    }

    public static class AnimationEvent {
        private final String id;
        private final String animationName;
        private final EventType eventType;
        private final Map<String, Object> parameters;
        private final boolean runOnce;
        private final long cooldown;
        
        public AnimationEvent(String id, String animationName, EventType eventType, 
                boolean runOnce, long cooldown) {
            this.id = id;
            this.animationName = animationName;
            this.eventType = eventType;
            this.parameters = new HashMap<>();
            this.runOnce = runOnce;
            this.cooldown = cooldown;
        }
        
        public String getId() {
            return id;
        }
        
        public String getAnimationName() {
            return animationName;
        }
        
        public EventType getEventType() {
            return eventType;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
        
        public Object getParameter(String key) {
            return parameters.get(key);
        }
        
        public void setParameter(String key, Object value) {
            parameters.put(key, value);
        }
        
        public boolean isRunOnce() {
            return runOnce;
        }
        
        public long getCooldown() {
            return cooldown;
        }
    }
    
    public enum EventType {
        BUTTON_PRESS,       
        REGION_ENTER,      
        REGION_LEAVE,      
        BLOCK_WALK,         
        LEVER_TOGGLE,
        STOP_ANIMATION       // New event type to stop animations
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) return;
        
        for (AnimationEvent animEvent : registeredEvents.values()) {
            if (animEvent.getEventType() != EventType.BUTTON_PRESS && 
                animEvent.getEventType() != EventType.LEVER_TOGGLE &&
                animEvent.getEventType() != EventType.STOP_ANIMATION) {
                continue;
            }
            
            Material blockType = block.getType();
            boolean isButton = blockType.name().endsWith("_BUTTON");
            boolean isLever = blockType == Material.LEVER;
            boolean isPressurePlate = blockType.name().endsWith("_PRESSURE_PLATE");

            if ((animEvent.getEventType() == EventType.BUTTON_PRESS && 
                 !(isButton || isPressurePlate)) ||
                (animEvent.getEventType() == EventType.LEVER_TOGGLE && !isLever) ||
                (animEvent.getEventType() == EventType.STOP_ANIMATION && 
                 !(isButton || isPressurePlate || isLever))) {
                continue;
            }
    
            if ((isButton || isLever) && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
                continue;
            }
            
            Location location = (Location) animEvent.getParameter("location");
            if (location == null || !isSameBlock(location, block.getLocation())) {
                continue;
            }
            
            if (isPlayerOnCooldown(player, animEvent)) {
                continue;
            }
            
            if (animEvent.getEventType() == EventType.STOP_ANIMATION) {
                stopAnimation(animEvent, player);
            } else {
                executeAnimation(animEvent, player);
            }
            
            setPlayerCooldown(player, animEvent);
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        
        for (AnimationEvent animEvent : registeredEvents.values()) {
            if (animEvent.getEventType() == EventType.BLOCK_WALK) {
                Location blockLoc = (Location) animEvent.getParameter("location");
                if (blockLoc != null && 
                    isSameBlock(blockLoc, to) && 
                    !isSameBlock(blockLoc, from)) {
                    
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
                    
                    executeAnimation(animEvent, player);
                    
                    setPlayerCooldown(player, animEvent);
                }
            }
            else if (animEvent.getEventType() == EventType.STOP_ANIMATION && 
                     animEvent.getParameter("location") != null) {
                Location blockLoc = (Location) animEvent.getParameter("location");
                if (blockLoc != null && 
                    isSameBlock(blockLoc, to) && 
                    !isSameBlock(blockLoc, from)) {
                    
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
                    
                    stopAnimation(animEvent, player);
                    
                    setPlayerCooldown(player, animEvent);
                }
            }
            else if (animEvent.getEventType() == EventType.REGION_ENTER) {
                Location min = (Location) animEvent.getParameter("min");
                Location max = (Location) animEvent.getParameter("max");
                
                if (min != null && max != null && 
                    isInRegion(to, min, max) && 
                    !isInRegion(from, min, max)) {
                    
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
                    
                    executeAnimation(animEvent, player);
                    
                    setPlayerCooldown(player, animEvent);
                }
            }
            else if (animEvent.getEventType() == EventType.STOP_ANIMATION && 
                     animEvent.getParameter("min") != null) {
                Location min = (Location) animEvent.getParameter("min");
                Location max = (Location) animEvent.getParameter("max");
                
                if (min != null && max != null && 
                    isInRegion(to, min, max) && 
                    !isInRegion(from, min, max)) {
                    
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
                    
                    stopAnimation(animEvent, player);
                    
                    setPlayerCooldown(player, animEvent);
                }
            }
            else if (animEvent.getEventType() == EventType.REGION_LEAVE) {
                Location min = (Location) animEvent.getParameter("min");
                Location max = (Location) animEvent.getParameter("max");
                
                if (min != null && max != null && 
                    !isInRegion(to, min, max) && 
                    isInRegion(from, min, max)) {
         
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
    
                    executeAnimation(animEvent, player);
          
                    setPlayerCooldown(player, animEvent);
                }
            }
            else if (animEvent.getEventType() == EventType.STOP_ANIMATION && 
                     animEvent.getParameter("min") != null) {
                Location min = (Location) animEvent.getParameter("min");
                Location max = (Location) animEvent.getParameter("max");
                
                if (min != null && max != null && 
                    !isInRegion(to, min, max) && 
                    isInRegion(from, min, max)) {
         
                    if (isPlayerOnCooldown(player, animEvent)) {
                        continue;
                    }
    
                    stopAnimation(animEvent, player);
          
                    setPlayerCooldown(player, animEvent);
                }
            }
        }
    }
    
    private void executeAnimation(AnimationEvent event, Player player) {
        String animationName = event.getAnimationName();
        
        if (!animationManager.isAnimationExists(animationName)) {
            plugin.getLogger().warning("Animation '" + animationName + "' does not exist (Event: " + event.getId() + ")");
            return;
        }
        
        boolean runOnce = event.isRunOnce();
        
        if (runOnce) {
            plugin.getLogger().info("Event '" + event.getId() + "' runs animation '" + 
                    animationName + "' once (triggered by " + player.getName() + ")");
            
            animationManager.playAnimationOnce(animationName);
        } else {
            if (!animationManager.isAnimationRunningGlobally(animationName)) {
                plugin.getLogger().info("Event '" + event.getId() + "' starts animation '" + 
                        animationName + "' globally (triggered by " + player.getName() + ")");
                        
                animationManager.startGlobalAnimation(animationName, player);
            } else {
                plugin.getLogger().info("Animation '" + animationName + "' is already running, event ignored");
            }
        }
    }
    
    private void stopAnimation(AnimationEvent event, Player player) {
        String animationName = event.getAnimationName();
        
        if (!animationManager.isAnimationExists(animationName)) {
            plugin.getLogger().warning("Animation '" + animationName + "' does not exist (Event: " + event.getId() + ")");
            return;
        }
        
        if (animationManager.isAnimationRunningGlobally(animationName)) {
            plugin.getLogger().info("Event '" + event.getId() + "' stops animation '" + 
                    animationName + "' (triggered by " + player.getName() + ")");
                    
            animationManager.stopAnimation(player, animationName);
        } else {
            plugin.getLogger().info("Animation '" + animationName + "' is not running, stop event ignored");
        }
    }
    
    private boolean isSameBlock(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
               loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }
    
    private boolean isInRegion(Location loc, Location min, Location max) {
        if (!loc.getWorld().equals(min.getWorld())) return false;
        
        return loc.getX() >= min.getX() && loc.getX() <= max.getX() &&
               loc.getY() >= min.getY() && loc.getY() <= max.getY() &&
               loc.getZ() >= min.getZ() && loc.getZ() <= max.getZ();
    }
    
    public void registerEvent(AnimationEvent event) {
        registeredEvents.put(event.getId(), event);
    }
    
    public boolean unregisterEvent(String eventId) {
        return registeredEvents.remove(eventId) != null;
    }
    
    public void saveEvents() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        for (AnimationEvent event : registeredEvents.values()) {
            String id = event.getId();
            config.set(id + ".animation", event.getAnimationName());
            config.set(id + ".type", event.getEventType().name());
            config.set(id + ".runOnce", event.isRunOnce());
            config.set(id + ".cooldown", event.getCooldown());
            
            switch (event.getEventType()) {
                case BUTTON_PRESS:
                case BLOCK_WALK:
                case LEVER_TOGGLE:
                case STOP_ANIMATION:  // Added support for stop events
                    Location loc = (Location) event.getParameter("location");
                    if (loc != null) {
                        config.set(id + ".world", loc.getWorld().getName());
                        config.set(id + ".x", loc.getX());
                        config.set(id + ".y", loc.getY());
                        config.set(id + ".z", loc.getZ());
                    }
                    break;
                    
                case REGION_ENTER:
                case REGION_LEAVE:
                    Location min = (Location) event.getParameter("min");
                    Location max = (Location) event.getParameter("max");
                    if (min != null && max != null) {
                        config.set(id + ".world", min.getWorld().getName());
                        config.set(id + ".min.x", min.getX());
                        config.set(id + ".min.y", min.getY());
                        config.set(id + ".min.z", min.getZ());
                        config.set(id + ".max.x", max.getX());
                        config.set(id + ".max.y", max.getY());
                        config.set(id + ".max.z", max.getZ());
                    }
                    break;
            }
        }
        
        try {
            config.save(file);
            plugin.getLogger().info("Saved: " + registeredEvents.size() + " events");
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving events: " + e.getMessage());
        }
    }
    
    private void loadEvents() {
        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) {
            plugin.getLogger().info("No events file found, creating new one");
            return;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int count = 0;
        
        for (String id : config.getKeys(false)) {
            try {
                String animName = config.getString(id + ".animation");
                String typeStr = config.getString(id + ".type");
                boolean runOnce = config.getBoolean(id + ".runOnce", false);
                long cooldown = config.getLong(id + ".cooldown", DEFAULT_COOLDOWN);
                
                EventType type = EventType.valueOf(typeStr);
                AnimationEvent event = new AnimationEvent(id, animName, type, runOnce, cooldown);
                
                switch (type) {
                    case BUTTON_PRESS:
                    case BLOCK_WALK:
                    case LEVER_TOGGLE:
                    case STOP_ANIMATION:  // Added support for stop events
                        String world = config.getString(id + ".world");
                        double x = config.getDouble(id + ".x");
                        double y = config.getDouble(id + ".y");
                        double z = config.getDouble(id + ".z");
                        
                        if (world != null && Bukkit.getWorld(world) != null) {
                            Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                            event.setParameter("location", loc);
                        } else {
                            plugin.getLogger().warning("World not found for event: " + id);
                            continue;
                        }
                        break;
                        
                    case REGION_ENTER:
                    case REGION_LEAVE:
                        String regionWorld = config.getString(id + ".world");
                        double minX = config.getDouble(id + ".min.x");
                        double minY = config.getDouble(id + ".min.y");
                        double minZ = config.getDouble(id + ".min.z");
                        double maxX = config.getDouble(id + ".max.x");
                        double maxY = config.getDouble(id + ".max.y");
                        double maxZ = config.getDouble(id + ".max.z");
                        
                        if (regionWorld != null && Bukkit.getWorld(regionWorld) != null) {
                            Location min = new Location(Bukkit.getWorld(regionWorld), minX, minY, minZ);
                            Location max = new Location(Bukkit.getWorld(regionWorld), maxX, maxY, maxZ);
                            event.setParameter("min", min);
                            event.setParameter("max", max);
                        } else {
                            plugin.getLogger().warning("World not found for region event: " + id);
                            continue;
                        }
                        break;
                }
                
                registerEvent(event);
                count++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading event '" + id + "': " + e.getMessage());
            }
        }
        
        plugin.getLogger().info("Loaded: " + count + " events");
    }
    
    public AnimationEvent createBlockEvent(String id, String animationName, EventType type, 
            Location location, boolean runOnce, long cooldown) {
        
        if (type != EventType.BUTTON_PRESS && 
            type != EventType.BLOCK_WALK && 
            type != EventType.LEVER_TOGGLE &&
            type != EventType.STOP_ANIMATION) {  // Added support for stop events
            throw new IllegalArgumentException("Invalid event type for block event");
        }
        
        AnimationEvent event = new AnimationEvent(id, animationName, type, runOnce, cooldown);
        event.setParameter("location", location.clone());
        
        registerEvent(event);
        return event;
    }
    
    public AnimationEvent createRegionEvent(String id, String animationName, EventType type,
            Location min, Location max, boolean runOnce, long cooldown) {

        if (type != EventType.REGION_ENTER && 
            type != EventType.REGION_LEAVE &&
            type != EventType.STOP_ANIMATION) {  // Added support for stop events
            throw new IllegalArgumentException("Invalid event type for region event");
        }
        
        if (!min.getWorld().equals(max.getWorld())) {
            throw new IllegalArgumentException("Min and max locations must be in the same world");
        }
       
        Location minLoc = new Location(min.getWorld(),
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        
        Location maxLoc = new Location(min.getWorld(),
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ()));
        
        AnimationEvent event = new AnimationEvent(id, animationName, type, runOnce, cooldown);
        event.setParameter("min", minLoc.clone());
        event.setParameter("max", maxLoc.clone());
        
        registerEvent(event);
        return event;
    }
    
    public Collection<AnimationEvent> getEvents() {
        return registeredEvents.values();
    }
    
    public AnimationEvent getEvent(String id) {
        return registeredEvents.get(id);
    }
    
    private boolean isPlayerOnCooldown(Player player, AnimationEvent event) {
        UUID playerUuid = player.getUniqueId();
        String eventId = event.getId();
        
        Map<String, Long> cooldowns = playerCooldowns.get(playerUuid);
        if (cooldowns == null) {
            return false;
        }
        
        Long lastTrigger = cooldowns.get(eventId);
        if (lastTrigger == null) {
            return false;
        }
        
        return System.currentTimeMillis() - lastTrigger < event.getCooldown();
    }
    
    private void setPlayerCooldown(Player player, AnimationEvent event) {
        UUID playerUuid = player.getUniqueId();
        String eventId = event.getId();
        
        Map<String, Long> cooldowns = playerCooldowns.computeIfAbsent(playerUuid, k -> new HashMap<>());
        cooldowns.put(eventId, System.currentTimeMillis());
    }
}