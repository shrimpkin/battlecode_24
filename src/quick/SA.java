package quick;

import battlecode.common.MapLocation;

public class SA {
    //shared array index uses
    // 0 - 2 location of flag, prefix with number of people defending 
    // 3, location of target enemy flag, prefix with number of people attacking 
    public static int FLAG1 = 0;
    public static int FLAG2 = 1;
    public static int FLAG3 = 2;
    public static int enemyFlag = 3;

    public static int e_loc_start = 4;
    public static int f_loc_start = 56;

    int width, height;

    public void setDimension(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * @param value a number obtained from the shared array, by using the encode method
     * @return the map location that was encoded
     */
    public MapLocation decodeLocation(int value) {
        value /= 10;

        int x = value % width;
        int y = value / width;
        
        return new MapLocation(x, y);
    }

    /**
     * @param value a number obtained from the shared array, by using the encode method
     * @return the prefix that was encoded
     */
    public int decodePrefix(int value) {
        return value % 10;
    }

    /**
     * Converts a location and prefix into a single 16-bit integer 
     * that can be accessed in the shared array
     * the prefix should be used for any additional information besides 
     * location that needs to be conveyed 
     * @param location 
     * @param prefix
     * @return the integer to be written into the shared array
     */
    public int encode(MapLocation location, int prefix) {
        return prefix + 10* (location.x + location.y * width);
    }
}
