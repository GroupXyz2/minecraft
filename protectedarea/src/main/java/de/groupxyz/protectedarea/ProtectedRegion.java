package de.groupxyz.protectedarea;

import org.bukkit.Location;

public class ProtectedRegion {

    private String name;
    private Location firstPoint;
    private Location secondPoint;

    public ProtectedRegion(String name, Location firstPoint, Location secondPoint) {
        this.name = name;
        this.firstPoint = firstPoint;
        this.secondPoint = secondPoint;
    }

    public String getName() {
        return name;
    }

    public Location getFirstPoint() {
        return firstPoint;
    }

    public Location getSecondPoint() {
        return secondPoint;
    }
}
