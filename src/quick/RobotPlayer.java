package quick;

import battlecode.common.*;
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
            if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
                System.out.println("Action upgraded!");
                rc.buyGlobal(GlobalUpgrade.ACTION);
            } 

            if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
                System.out.println("Healing Upgraded");
                rc.buyGlobal(GlobalUpgrade.HEALING);
            }

            if(rc.getRoundNum() == 1) {
                map.setDimension(rc.getMapWidth(), rc.getMapHeight()); 
            }

            if(!rc.isSpawned()) {
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                
                for(MapLocation spawn : spawnLocs) {
                    if (rc.canSpawn(spawn)) rc.spawn(spawn);
                }
            } else {
                
                MapInfo info = rc.senseMapInfo(rc.getLocation());
                if(info.isSpawnZone()) {
                    for(Direction dir : directions) {
                        if(rc.canMove(dir)) {
                            rc.move(dir);
                        }
                    }
                }

                rc.setIndicatorString("before attack");
                RobotInfo[] enemyRobots = rc.senseNearbyRobots(5, rc.getTeam().opponent());
                rc.setIndicatorString("after attack");

                for(RobotInfo robot : enemyRobots) {
                    if(rc.canAttack(robot.getLocation())) {
                        rc.attack(robot.getLocation());
                    }
                }

                RobotInfo[] friendlyRobots = rc.senseNearbyRobots(5, rc.getTeam());

                for(RobotInfo robot : friendlyRobots) {
                    if(rc.canHeal(robot.getLocation())) {
                        rc.heal(robot.getLocation());
                    }
                }


                updateMap(rc);
                rc.setIndicatorString("" + map.num);
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
}
