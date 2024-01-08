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
            MapCursor cursor = mapCanvas.getCursors().getCursor(0);

            // Entferne vorhandenen Cursor
            cursor.setVisible(false);

            int centerX = markerLocation.getBlockX() * 2;
            int centerZ = markerLocation.getBlockZ() * 2;
            int cursorX = centerX - mapView.getCenterX();
            int cursorZ = centerZ - mapView.getCenterZ();

            MapCursor newCursor = new MapCursor((byte) cursorX, (byte) cursorZ, (byte) 0, MapCursor.Type.RED_POINTER, true);
            mapCanvas.getCursors().addCursor(newCursor);
        }
    }






    @Override
    public void initialize(MapView mapView) {
        mapView.setScale(MapView.Scale.NORMAL); // Setzen Sie die Karten-Skala auf NORMAL
    }

    public static short createTreasureMap(World world, Location location) {
        MapView mapView = Bukkit.createMap(world);
        mapView.getRenderers().forEach(mapView::removeRenderer); // Lösche vorhandene Renderer

        Mapcreator mapRenderer = new Mapcreator(location);
        mapView.addRenderer(mapRenderer);

        return (short) mapView.getId();
    }
}

