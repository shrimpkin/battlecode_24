package submit6;

import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class SA {
    //shared array index uses
    // 0 - 2 location of flag, prefix with number of people defending 
    // 3, location of target enemy flag, prefix with number of people attacking 
    
    // for FLAG1, FLAG2, FLAG3
    // prefix of 0,  means that they are in default position
    // prefix of 1,  means they are in the chosen position
    public static int FLAG1 = 0;
    public static int FLAG2 = 1;
    public static int FLAG3 = 2;
    public static int enemyFlag = 3;

    //for the first turn the location is also used for indexing all robots
    public static int INDEXING = 4;

    public static int e_loc_start = 5;
    public static int escort = 6;
    public static int defend = 7;
    public static int f_loc_start = 56;

    public static int COMBAT_COM_START = 8;
    public static int COMBAT_COM_END = 18;
    
    static RobotController rc;
    

    static int width, height;

    /**
     * Init shared array
     * @param w width of the map
     * @param h height of the map
     * @param r robotcontroller
     */
    public static void init(int w, int h, RobotController r) {
        width = w;
        height = h;
        rc = r;
    }

    /**
     * @param value a number obtained from the shared array, by using the encode method
     * @return the map location that was encoded
     */
    public static MapLocation getLocation(int index) throws GameActionException {
        int value = rc.readSharedArray(index);

        value /= 10;

        int x = value % width;
        int y = value / width;
        
        return new MapLocation(x, y);
    }

    /**
     * @param value a number obtained from the shared array, by using the encode method
     * @return the prefix that was encoded
     */
    public static int getPrefix(int index) throws GameActionException {
        int value = rc.readSharedArray(index);
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
    public static int encode(MapLocation location, int prefix) {
        return prefix + 10* (location.x + location.y * width);
    }
    

    /**
     * adds flags into shared array
     */
    public static void updateMap() throws GameActionException {
        if(rc.readSharedArray(SA.FLAG1) == 0 
            || rc.readSharedArray(SA.FLAG2) == 0 
            || rc.readSharedArray(SA.FLAG3) == 0) {
            setFlags();
        }
    }

    /**
     * A method that will add flags to the shared array if they haven't been yet
     */
    public static void setFlags() throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1);
        for(FlagInfo flag : flags) {
            if(flag.getTeam() == rc.getTeam()) {
                boolean in = false;
                for(int i = 0; i <= 2; i++) {
                    if(flag.getLocation().equals(SA.getLocation(i))) {
                        in = true;
                    }
                }

                if(!in) {
                    for(int i = 0; i <= 2; i++) {
                        if(rc.readSharedArray(i) == 0) {
                            rc.writeSharedArray(i, SA.encode(flag.getLocation(), 0));
                            break;
                        }
                    }
                }
            }
        }
    }
}
