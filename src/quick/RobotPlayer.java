package quick;

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
    static SA sa = new SA();

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
            //used for debugging, only value that should be passed through rc.setIndicator
            //can be seen by hovering over robot
            String indicator = "";

            if(rc.getRoundNum() % 750 == 0) globals(rc);
            if(rc.getRoundNum() == 1) init(rc);
                
            if(!rc.isSpawned()) {
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                
                for(MapLocation spawn : spawnLocs) {
                    if (rc.canSpawn(spawn)) rc.spawn(spawn);
                }
            } else {
                MapInfo info = rc.senseMapInfo(rc.getLocation());

                MapLocation location = rc.senseNearbyCrumbs(-1)[0];

                Direction astar = AStar.getBestDirection(rc, location);
                if(rc.canMove(astar)) {
                    rc.move(astar);
                } else {
                    if(info.isSpawnZone()) {
                        for(Direction dir : directions) {
                            if(rc.canMove(dir)) {
                                rc.move(dir);
                            }
                        }
                    }
                }
        
                combat(rc);
                map.updateMap(rc);

                map.writeMapToShared(rc); 
                indicator += "wrote ";
                map.readMapFromShared(rc);
                indicator += "read ";

                indicator += map.num;
                rc.setIndicatorString(indicator);
            }

            Clock.yield();
        }
    }

    /**
     * handles initializing all static fields
     * @param rc
     * @throws GameActionException
     */
    public static void init(RobotController rc) throws GameActionException {
        map.setDimension(rc.getMapWidth(), rc.getMapHeight());
        sa.setDimension(rc.getMapWidth(), rc.getMapHeight()); 
    }

    /**
     * method for all fighting movement/ attack patterns
     * @param rc
     * @throws GameActionException
     */
    public static void combat(RobotController rc) throws GameActionException {
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                
        for(RobotInfo robot : enemyRobots) {
            if(rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
            } else {
                break;
            }
        }

        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());

        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) {
                rc.heal(robot.getLocation());
            } else {
                break;
            }
        }
    }

    /**
     * Attempts to buy global upgrades
     */
    public static void globals(RobotController rc) throws GameActionException {
        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) {
                System.out.println("Action upgraded!");
                rc.buyGlobal(GlobalUpgrade.ACTION);
            } 

        if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) {
            System.out.println("Healing Upgraded");
            rc.buyGlobal(GlobalUpgrade.HEALING);
        }
    }
}
