package v8;

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
    public static int TARGET_ENEMY_FLAG = 3;

    public static int ENEMY_FLAG1 = 4;
    public static int ENEMY_FLAG2 = 5;
    public static int ENEMY_FLAG3 = 6;

    //for the first turn the location is also used for indexing all robots
    public static int INDEXING = 7;
    public static int escort = 8;
    public static int defend = 9;
    public static int symmetry = 10; // symmetry query - use rightmost 3 as bitset (vert, horiz, rot)

    static RobotController rc;
    
    public static int ROBOT_COMBAT_INFO_START = 14;
    public static int ROBOT_COMBAT_INFO_END = 64;

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
     * @param index index of the shared array to access
     * @return the map location that was encoded
     */
    public static MapLocation getLocation(int index) throws GameActionException {
        int value = rc.readSharedArray(index) >> 4;

        int x = value % width;
        int y = value / width;
        
        return new MapLocation(x, y);
    }

    /**
     * @param index index of the shared array to access
     * @return the prefix that was encoded
     */
    public static int getPrefix(int index) throws GameActionException {
        int value = rc.readSharedArray(index);
        return value & 0b1111;
    }

    /**
     * Converts a location and prefix into a single 16-bit integer 
     * that can be accessed in the shared array
     * the prefix should be used for any additional information besides 
     * location that needs to be conveyed 
     * @param location location to encode
     * @param prefix prefix to encode in range [0,15]
     * @return the integer to be written into the shared array
     */
    public static int encode(MapLocation location, int prefix) {
        return prefix + (location.x + location.y * width << 4);
    }


    /**
     * returns a bitflag representing the possible symmetries at the moment
     *  <p>ret & 4 == 0 -> maybe vertical symmetry</p>
     *  <p>ret & 2 == 0 -> maybe horizontal symmetry</p>
     *  <p>ret & 1 == 0 -> maybe rotation symmetry</p>
     * if return val is power of two then we know for sure which it is
     */
    public static int getSymmetry() throws GameActionException{
        return rc.readSharedArray(symmetry);
    }

    /**
     * checks whether the map symmetry is known for sure (it's a power of 2)
     */
    public static boolean symmetryKnown() throws GameActionException{
        return symmetryKnown(rc.readSharedArray(symmetry));
    }
    public static boolean symmetryKnown(int curSet){
        return Integer.bitCount(curSet) == 1;
    }


    /**
     * A method that will add flags to the shared array if they haven't been yet
     * should fill up from L -> R w/o gaps
     */
    public static void updateMap() throws GameActionException {
        int firstEmpty;
        if (rc.readSharedArray(SA.FLAG3) == 0) firstEmpty = 2; else return; // return if already filled
        if (rc.readSharedArray(SA.FLAG2) == 0) firstEmpty = 1; // update if previous ones are also empty to get 1st ind
        if (rc.readSharedArray(SA.FLAG1) == 0) firstEmpty = 0;
        FlagInfo[] flags = rc.senseNearbyFlags(9, rc.getTeam());
        assert flags.length == 1; // if this is on first round, the robot should be adjacent to a flag
        FlagInfo flag = flags[0];
        for(int i = SA.FLAG1; i < firstEmpty; i++) // check if location is already present
            if(flag.getLocation().equals(SA.getLocation(i))) return;
        // empty space and flag not present: write to first empty location
        rc.writeSharedArray(firstEmpty, encode(flag.getLocation(), 0));
    }
}
