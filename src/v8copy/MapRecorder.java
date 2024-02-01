package v8copy;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class MapRecorder {
    public static MapInfo[] map;
    private static RobotController rc;

    public static void init(RobotController rc){ // initialize map array and robot controller reference
        MapRecorder.rc = rc;
        map = new MapInfo[rc.getMapWidth()*rc.getMapHeight()];
    }


    // masks for symmetry queries
    private static final int VERT_MASK = 4, HORIZ_MASK = 2, ROT_MASK = 1;
    /**
     * update surrounding information for the current robot, including any symmetry information.
     * This method is hella expensive, maybe integrate with something else later on.
     * possibly also take direction and use that to limit search space.
     * <p> [bytecode cost: ~1600-3500] </p>
     */
    public static void updateSurroundings() throws GameActionException {
        int cur = SA.getSymmetry();
        for (MapInfo info : rc.senseNearbyMapInfos()){
            MapLocation loc = info.getMapLocation();
            int key = encode(loc.x, loc.y);
            // symmetry isn't known, new location isn't seen, and location type can't change (wall/dam)
            if (!SA.symmetryKnown(cur) && map[key] == null && (info.isDam() || info.isWall())){
                if ((cur & VERT_MASK) != 0) {
                    int nx = rc.getMapWidth()-1-loc.x, ny = loc.y; // mirror on the vertical
                    MapInfo opposite = map[encode(nx, ny)];
                    if (nonSymmetric(info, opposite)) cur &= 0b011; // b-and to mark as not vertically symmetrical
                }
                if ((cur & HORIZ_MASK) != 0){
                    int nx = loc.x, ny = rc.getMapHeight()-1-loc.y; // mirror on the horizontal
                    MapInfo opposite = map[encode(nx, ny)];
                    if (nonSymmetric(info, opposite)) cur &= 0b101; // b-and to mark as not horizontally symmetrical
                }
                if ((cur & ROT_MASK) != 0) {
                    int nx = rc.getMapWidth()-1-loc.x, ny = rc.getMapHeight()-1-loc.y; // mirror on diagonal
                    MapInfo opposite = map[encode(nx, ny)];
                    if (nonSymmetric(info, opposite)) cur &= 0b110; // b-and to mark as not rotationally symmetrical
                }
            }
            map[key] = info;
        }
        // update symmetry information
        rc.writeSharedArray(SA.symmetry, cur);
    }

    /**
     * gets the mirrored location on map with current information about map symmetry
     */
    public static MapLocation getCorresponding(MapLocation loc) throws GameActionException{
        if (!SA.symmetryKnown()) {
            System.err.println("note: symmetry is not known yet: result might be inaccurate");
        }
        int sym = SA.getSymmetry();
        if ((sym & ROT_MASK) != 0)   return new MapLocation(SA.width-1-loc.x, SA.height-1-loc.y);
        if ((sym & HORIZ_MASK) != 0) return new MapLocation(loc.x, SA.height-1-loc.y);
        if ((sym & VERT_MASK) != 0)  return new MapLocation(SA.width-1-loc.x, loc.y);
        return null; // somehow no symmetry
    }

    // helper methods
    private static int encode(int x, int y){
        return x + y * rc.getMapWidth();
    }

    // whether the two are known to be nonsymmetric
    private static boolean nonSymmetric(MapInfo a, MapInfo b){
        if (a == null || b == null) return false; // not enough info on either
        // return whether the terrain types are different
        return a.isDam() != b.isDam()
                || a.isWall() != b.isWall()
                || a.isSpawnZone() != b.isSpawnZone();
    }

    public static MapInfo get(MapLocation loc){
        // no location provided or invalid location provide
        if (loc == null) return null;
        if (!rc.onTheMap(loc)) return null;
        // valid location, query map
        return map[loc.x + loc.y*rc.getMapWidth()];
    }

}
