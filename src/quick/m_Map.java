package quick;

import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

/**
 * Class to contain the information the robots know about the map
 */
public class m_Map {
    //constants used for the map class
    public static int UNEXPLORED = 0;
    public static int GRASS = 1;
    public static int WATER = 2;
    public static int WALL = 3;
    public static int DAM = 4;

    private int[][] terrain = new int[60][60];
    int width, height;
    int num = 0;
    SA sa = new SA();

    public void setDimension(int width, int height) {
        this.width = width;
        this.height = height;
        sa.setDimension(width, height);
    }

    public int getTerrain(MapLocation location) {
        return terrain[location.x][location.y];
    }

    public void setTerrain(MapLocation location, int value) {
        if(terrain[location.x][location.y] == 0) num++;
        terrain[location.x][location.y] = value;
    }

    /**
     * adds all visible terrain to the robots personal map
     */
    public void updateMap(RobotController rc) throws GameActionException {
        if(rc == null) return;
        if(rc.senseNearbyMapInfos() == null) return;

        MapInfo[] locations = rc.senseNearbyMapInfos();

        for(MapInfo info : locations) {
            int terrain = 0; 
            if(info.isWall()) terrain = WALL;
            if(info.isWater()) terrain = WATER;
            if(info.isPassable()) terrain = GRASS;
            
            this.setTerrain(info.getMapLocation(), terrain);
        }

        if(rc.readSharedArray(0) == 0 || rc.readSharedArray(1) == 0 || rc.readSharedArray(2) == 0) {
            setFlags(rc);
        }

    }

    /**
     * helper method that will set symetry if can be determined
     * TODO
     */
    private void determineSymetry(RobotController rc) throws GameActionException {

    }

    /**
     * A helper method that will add flags to the shared array if they haven't been yet
     */
    private void setFlags(RobotController rc) throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1);
        for(FlagInfo flag : flags) {
            if(flag.getTeam() == rc.getTeam()) {
                boolean in = false;
                for(int i = 0; i <= 2; i++) {
                    if(flag.getLocation().equals( sa.decodeLocation(rc.readSharedArray(i)))) {
                        in = true;
                    }
                }

                if(!in) {
                    for(int i = 0; i <= 2; i++) {
                        if(rc.readSharedArray(i) == 0) {
                            rc.writeSharedArray(i, sa.encode(flag.getLocation(), 0));
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * reads all map locations from the shared array
     */
    public void readMapFromShared(RobotController rc) throws GameActionException {
        int index;

        if(rc.getRoundNum() < 200) index = SA.e_loc_start;
        else index = SA.f_loc_start;
        
        while(true) {
            int value = rc.readSharedArray(index);

            this.setTerrain(sa.decodeLocation(value), sa.decodePrefix(value));
            index++;
            if(index == 64) break;
        }
    }

    /**
     * writes all visible map location to the shared array
     */
    public void writeMapToShared(RobotController rc) throws GameActionException {
        if(rc == null) return;
        if(rc.senseNearbyMapInfos() == null) return;

        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        
        int index;
        if(rc.getRoundNum() < 200) index = SA.e_loc_start;
        else index = SA.f_loc_start;

        for(MapInfo info : mapInfo) {
            int terrain = 0; 
            if(info.isWall()) terrain = WALL;
            else if(info.isWater()) terrain = WATER;
            else if(info.isPassable()) terrain = GRASS;
            else terrain = DAM;
            
           int enc = sa.encode(info.getMapLocation(), terrain);

            rc.writeSharedArray(index, enc);
            index++;
            if(index == 64) break;
        }
    }
}
