package quick;

import battlecode.common.MapLocation;

/**
 * Class to contain the information the robots know about the map
 */
public class m_Map {
    private int[][] terrain = new int[60][60];
    int width, height;
    int num = 0;

    public void setDimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getTerrain(MapLocation location) {
        return terrain[location.x][location.y];
    }

    public void setTerrain(MapLocation location, int value) {
        if(terrain[location.x][location.y] == 0) num++;
        terrain[location.x][location.y] = value;
    }
}
