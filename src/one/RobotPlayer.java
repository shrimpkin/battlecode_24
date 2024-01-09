package one;

import battlecode.common.*;
import quick.pathfinding.AStar;
import scala.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {
    static m_Map map = new m_Map();

    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public static void run(RobotController rc) throws GameActionException {
        while(true) {
            if(rc.getRoundNum() == 1) {
                map.setDimension(rc.getMapWidth(), rc.getMapHeight()); 
            }

            if(!rc.isSpawned() && rc.readSharedArray(0) < 2 && rc.getTeam() == Team.A) {
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                
                for(MapLocation spawn : spawnLocs) {
                    if (rc.canSpawn(spawn)) rc.spawn(spawn);
                }

                rc.writeSharedArray(0, rc.readSharedArray(0) + 1);
            } 
            
            
            if(rc.isSpawned()) {
                if(rc.readSharedArray(1) != rc.getRoundNum()) {
                    updateMap(rc);
                    Direction dir = directions[rng.nextInt(directions.length)];
                    
                    MapLocation[] locations = rc.senseNearbyCrumbs(-1);
                    MapLocation location = null;
                    if(locations.length > 0) location = locations[0];
                    
                    Direction astar = Direction.CENTER;
                    if(location != null) astar = AStar.getBestDirection(rc, location);
                    
                    if(astar != Direction.CENTER) if(rc.canMove(astar)) rc.move(astar);
                    else if(rc.canMove(dir)) rc.move(dir);
                    
                    rc.writeSharedArray(1, rc.getRoundNum());

                    writeMapToShared(rc);
                } else {
                    readMapFromShared(rc);
                }
                
                rc.setIndicatorString("total revealed: " + map.num);
            }
            Clock.yield();
        }
    }

    public static void updateMap(RobotController rc) throws GameActionException {
        MapInfo[] locations = rc.senseNearbyMapInfos();

        for(MapInfo info : locations) {
            int terrain = 0; 
            if(info.isWall()) terrain = Constants.WALL;
            if(info.isWater()) terrain = Constants.WATER;
            if(info.isPassable()) terrain = Constants.GRASS;
            
            map.setTerrain(info.getMapLocation(), terrain);
        }
    }

    public static void readMapFromShared(RobotController rc) throws GameActionException {
        int index = 2;
        
        while(true) {
            int value = rc.readSharedArray(index);
            if(value == 65535) break;

            map.setTerrain(map.decodeLocation(value), map.decodeTerrain(value));
            index++;
        }
    }

    public static void writeMapToShared(RobotController rc) throws GameActionException {
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        int index = 2;

        for(MapInfo info : mapInfo) {
            int terrain = 0; 
            if(info.isWall()) terrain = Constants.WALL;
            if(info.isWater()) terrain = Constants.WATER;
            if(info.isPassable()) terrain = Constants.GRASS;
            
           int enc = map.encode(info.getMapLocation(), terrain);

            rc.writeSharedArray(index, enc);
            index++;
            if(index > 62) break;
        }

        rc.writeSharedArray(index, 65535);
    }
    
}
