//Class archived, not in use

package de.groupxyz.treasurehunt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.map.*;

public final class Mapcreator extends MapRenderer implements Listener {
    private final Location markerLocation;

    public Mapcreator(Location markerLocation) {
        this.markerLocation = markerLocation;
    }

    @Override
    public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
        if (player.getWorld() == markerLocation.getWorld()) {
            Bukkit.getLogger().info("Player's world matches marker location world");

            int centerX = markerLocation.getBlockX() * 2;
            int centerZ = markerLocation.getBlockZ() * 2;
            int cursorX = (centerX - mapView.getCenterX()) % 128;
            int cursorZ = (centerZ - mapView.getCenterZ()) % 128;

            MapCursor newCursor = new MapCursor((byte) cursorX, (byte) cursorZ, (byte) 0, MapCursor.Type.RED_POINTER, true);
            mapCanvas.getCursors().addCursor(newCursor);
        } else {
            Bukkit.getLogger().info("Player's world doesn't match marker location world");
        }
    }

    @Override
    public void initialize(MapView mapView) {
        mapView.setScale(MapView.Scale.NORMAL);
    }

    public static short createTreasureMap(World world, Location location) {
        try {
            MapView mapView = Bukkit.createMap(world);
            mapView.getRenderers().forEach(mapView::removeRenderer);

            Mapcreator mapRenderer = new Mapcreator(location);
            mapView.addRenderer(mapRenderer);

            return (short) mapView.getId();
        } catch (Exception e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("Error creating treasure map: " + e.getMessage());
            return -1;
        }
    }
}

