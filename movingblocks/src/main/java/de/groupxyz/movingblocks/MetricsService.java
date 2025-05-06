package de.groupxyz.movingblocks;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class MetricsService {

    private final Movingblocks plugin;
    private final AnimationManager animationManager;
    private final AnimationEventHandler animationEventHandler;
    private final Metrics metrics;

    public MetricsService(Movingblocks plugin, AnimationManager animationManager, AnimationEventHandler animationEventHandler) {
        this.plugin = plugin;
        this.animationManager = animationManager;
        this.animationEventHandler = animationEventHandler;
        this.metrics = plugin.getMetrics();
        
        registerAllMetrics();
    }
    
    private void registerAllMetrics() {
        registerAnimationCountChart();
        registerEventCountChart();
        registerActiveAnimationChart();
        registerEventTypesChart();
        registerAnimationModeChart();
        registerProtectedAnimationsChart();
        registerEventsPerAnimationChart();
        registerFrameCountChart();
        registerAnimationComplexityChart();
    }
    
    private void registerAnimationCountChart() {
        metrics.addCustomChart(new Metrics.SingleLineChart("animation_count", 
                () -> animationManager.getAnimationNames().size()));
    }
    
    private void registerEventCountChart() {
        metrics.addCustomChart(new Metrics.SingleLineChart("event_count", 
                () -> animationEventHandler.getEvents().size()));
    }
    
    private void registerActiveAnimationChart() {
        metrics.addCustomChart(new Metrics.SingleLineChart("active_animation_count", 
                () -> animationManager.getRunningGlobalAnimations().size()));
    }
    
    private void registerEventTypesChart() {
        metrics.addCustomChart(new Metrics.AdvancedPie("event_types", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() {
                Map<String, Integer> valueMap = new HashMap<>();
                
                for (AnimationEventHandler.AnimationEvent event : animationEventHandler.getEvents()) {
                    String typeName = event.getEventType().name();
                    valueMap.put(typeName, valueMap.getOrDefault(typeName, 0) + 1);
                }
                
                return valueMap;
            }
        }));
    }
    
    private void registerAnimationModeChart() {
        metrics.addCustomChart(new Metrics.SimplePie("animation_mode", () -> {
            int total = animationManager.getAnimationNames().size();
            if (total == 0) return "None";
            
            int replaceMode = 0;
            for (String name : animationManager.getAnimationNames()) {
                if (!animationManager.isAnimationRemoveBlocksAfterFrame(name)) {
                    replaceMode++;
                }
            }
            
            if (replaceMode == total) return "Replace Only";
            else if (replaceMode == 0) return "Place and Remove Only";
            else return "Mixed";
        }));
    }
    
    private void registerProtectedAnimationsChart() {
        metrics.addCustomChart(new Metrics.SimplePie("protected_animations", () -> {
            int total = animationManager.getAnimationNames().size();
            if (total == 0) return "No Animations";
            
            int protectedCount = 0;
            for (String name : animationManager.getAnimationNames()) {
                if (animationManager.isAnimationProtected(name)) {
                    protectedCount++;
                }
            }
            
            double percentage = (double)protectedCount / total * 100;
            if (percentage == 0) return "None Protected";
            else if (percentage < 25) return "Few Protected (<25%)";
            else if (percentage < 50) return "Some Protected (25-50%)";
            else if (percentage < 75) return "Most Protected (50-75%)";
            else return "Almost All Protected (>75%)";
        }));
    }
    
    private void registerEventsPerAnimationChart() {
        metrics.addCustomChart(new Metrics.DrilldownPie("events_per_animation", () -> {
            Map<String, Map<String, Integer>> map = new HashMap<>();
            
            Map<String, Integer> noEvents = new HashMap<>();
            Map<String, Integer> fewEvents = new HashMap<>();
            Map<String, Integer> manyEvents = new HashMap<>();
            
            Map<String, Integer> eventCounts = new HashMap<>();
            for (AnimationEventHandler.AnimationEvent event : animationEventHandler.getEvents()) {
                String animName = event.getAnimationName();
                eventCounts.put(animName, eventCounts.getOrDefault(animName, 0) + 1);
            }
            
            for (String animName : animationManager.getAnimationNames()) {
                int count = eventCounts.getOrDefault(animName, 0);
                if (count == 0) {
                    noEvents.put(animName, 1);
                } else if (count <= 3) {
                    fewEvents.put(animName, count);
                } else {
                    manyEvents.put(animName, count);
                }
            }
            
            map.put("No Events", noEvents);
            map.put("1-3 Events", fewEvents);
            map.put("4+ Events", manyEvents);
            
            return map;
        }));
    }
    
    private void registerFrameCountChart() {
        metrics.addCustomChart(new Metrics.SimpleBarChart("animation_frame_count", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() {
                Map<String, Integer> valueMap = new HashMap<>();
                
                int small = 0;
                int medium = 0;
                int large = 0; 
                
                for (String animName : animationManager.getAnimationNames()) {
                    int frameCount = animationManager.getAnimationFrameCount(animName);
                    if (frameCount <= 3) {
                        small++;
                    } else if (frameCount <= 8) {
                        medium++;
                    } else {
                        large++;
                    }
                }
                
                valueMap.put("Small (1-3 frames)", small);
                valueMap.put("Medium (4-8 frames)", medium);
                valueMap.put("Large (9+ frames)", large);
                
                return valueMap;
            }
        }));
    }
    
    private void registerAnimationComplexityChart() {
        metrics.addCustomChart(new Metrics.MultiLineChart("animation_complexity", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() {
                Map<String, Integer> valueMap = new HashMap<>();
                
                int totalFrames = 0;
                int totalBlocks = 0;
                int totalAnimations = animationManager.getAnimationNames().size();
                
                for (String animName : animationManager.getAnimationNames()) {
                    totalFrames += animationManager.getAnimationFrameCount(animName);
                    totalBlocks += animationManager.getAnimationTotalBlocks(animName);
                }
                
                valueMap.put("animations", totalAnimations);
                valueMap.put("frames", totalFrames);
                valueMap.put("blocks", totalBlocks);
                
                return valueMap;
            }
        }));
    }
}