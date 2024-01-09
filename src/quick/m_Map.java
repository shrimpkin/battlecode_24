package quick;

import battlecode.common.MapLocation;

/**
 * Class to contain the information the robots know about the map
 */
public class m_Map {
    private int[][] terrain = new int[60][60];
    private int[][] crumb = new int[60][60];
    int width, height;
    int num = 0;

    public void setDimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getLocation(MapLocation location) {
        return terrain[location.x][location.y];
    }

    public void setTerrain(MapLocation location, int value) {
        terrain[location.x][location.y] = value;
        num++;
    }

    public void updateCrumbs(MapLocation location, int crumbs) {
        crumb[location.x][location.y] = crumbs;
    }

    public int getCrumbs(MapLocation location) {
        return crumb[location.x][location.y];
    }

    public MapLocation decodeLocation(int value) {
        value /= 10;

        int x = value % width;
        int y = value / width;

        return new MapLocation(x, y);
    }

    public int decodeTerrain(int value) {
        return value % 10;
    }

    public int encode(MapLocation location, int terrain) {
        return terrain + 10* (location.x + location.y * width);
    }

}
