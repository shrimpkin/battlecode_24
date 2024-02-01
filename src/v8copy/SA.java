package v8copy;

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
    public static int FLAG1 = 0, FLAG2 = 1, FLAG3 = 2;
    public static int TARGET_ENEMY_FLAG = 3;
    public static int BROADCAST1 = 4,BROADCAST2 = 5, BROADCAST3 = 6;
    public static final int EFSPAWN1 = 7, EFSPAWN2 = 8, EFSPAWN3 = 9;
    public static final int EFID1 = 11, EFID2 = 12, EFID3 = 13;

    //for the first turn the location is also used for indexing all robots
    public static int INDEXING = 60;
    public static int escort = 61;
    public static int defend = 62;
    public static int symmetry = 63; // symmetry query - use rightmost 3 as bitset (vert, horiz, rot)

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

    // adds opponent flag spawn to the shared array
    public static boolean addOpponentFlagSpawn(FlagInfo flag) throws GameActionException{
        if (findOpponentFlagSpawn(flag.getID()) != -1) return false; // already present
        int i;
        if (rc.readSharedArray(EFID1) == 0) i = 0;
        else if (rc.readSharedArray(EFID2) == 0) i = 1;
        else if (rc.readSharedArray(EFID3) == 0) i = 2;
        else return false;
        // prefix of 1 to show it was entered, prefix of 2 to show it was captured
        rc.writeSharedArray(EFID1 + i, flag.getID());
        rc.writeSharedArray(EFSPAWN1 + i, SA.encode(flag.getLocation(), 1));
        return true;
    }

    // finds the index (offset from 0-2) of the enemy's flag in the array (add to EFID1/EFSPAWN1 to get true position)
    public static int findOpponentFlagSpawn(int id) throws GameActionException{
        if (rc.readSharedArray(EFID1) == id) return 0;
        if (rc.readSharedArray(EFID2) == id) return 1;
        if (rc.readSharedArray(EFID3) == id) return 2;
        return -1;
    }
}
