package quick;

import battlecode.common.*;
import battlecode.schema.GameFooter;
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
    static int ID = 0;

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
            String indicator = ID + ": ";

            if(rc.getRoundNum() % 750 == 0) globals(rc);
            if(rc.getRoundNum() == 1) init(rc);
                
            if(!rc.isSpawned()) {
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                
                for(MapLocation spawn : spawnLocs) {
                    if (rc.canSpawn(spawn)) rc.spawn(spawn);
                }
            } else {
                
                flagStuff(rc);
                indicator += sa.decodePrefix(rc.readSharedArray(SA.enemyFlag)) + " ";
                
                indicator += move(rc);
                combat(rc);
                build(rc);

                
                map.updateMap(rc);
                
                indicator += map.num;
            }

            for(int i = 0; i <= 2; i++) {
                indicator += "(" + sa.decodePrefix(rc.readSharedArray(i)) + ", " + sa.decodeLocation(rc.readSharedArray(i)) + ")";
            }

            rc.setIndicatorString(indicator);
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
        
        
        rc.writeSharedArray(3, rc.readSharedArray(3) + 1);
        ID = rc.readSharedArray(3);
        if(rc.readSharedArray(3) == 50) {
            rc.writeSharedArray(3, 0);
        }
    }

    public static void flagStuff(RobotController rc) throws GameActionException {
        //writes into the shared array the location of the target flag to get
        if(sa.decodePrefix(rc.readSharedArray(SA.enemyFlag)) == 0) {
            if(rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
                rc.writeSharedArray(SA.enemyFlag, sa.encode(rc.senseNearbyFlags(-1, rc.getTeam().opponent())[0].getLocation(), 1));
            } else if(rc.readSharedArray(SA.enemyFlag) == 0) {
                MapLocation loc = rc.senseBroadcastFlagLocations()[0];
                rc.writeSharedArray(SA.enemyFlag, sa.encode(loc, 0));
            }
        }

        
        if(rc.canPickupFlag(rc.getLocation())) {
            if(rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
                 rc.pickupFlag(rc.getLocation());
            } else {
                for(int i = SA.FLAG1; i <= SA.FLAG3; i++) {
                    if(sa.decodeLocation(rc.readSharedArray(i)).equals(rc.getLocation())
                        && sa.decodePrefix(rc.readSharedArray(i)) == 0) {
                            rc.pickupFlag(rc.getLocation());
                    }
                }
            }
        }
    }

    /**
     * true if carrying a flag for the team the robot is on
     * @param rc
     * @return
     * @throws GameActionException
     */
    private static boolean hasMyFlag(RobotController rc) throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1);
        for(FlagInfo flag : flags) {
            if(flag.getLocation().equals(rc.getLocation())) {
                return flag.getTeam() == rc.getTeam();
            }
        }

        return false;
    }

    public static MapLocation move(RobotController rc) throws GameActionException {
        
        MapLocation target = null;

        Direction dir = directions[rng.nextInt(directions.length)];
        boolean hasFlag = rc.hasFlag();
        
        if(hasMyFlag(rc)) {
            if(sa.decodePrefix(rc.readSharedArray(SA.FLAG1)) == 0) {
                target = sa.decodeLocation(rc.readSharedArray(0));
            } else if(sa.decodePrefix(rc.readSharedArray(SA.FLAG2)) == 0) {
                MapLocation loc = sa.decodeLocation(rc.readSharedArray(SA.FLAG1));
                MapLocation flag1 = loc;
                while(loc.distanceSquaredTo(flag1) <= 6) {
                    loc.add(Direction.NORTH);
                }
            } else if(sa.decodePrefix(rc.readSharedArray(SA.FLAG3)) == 0) {
                MapLocation loc = sa.decodeLocation(rc.readSharedArray(SA.FLAG2));
                MapLocation flag1 = loc;
                while(loc.distanceSquaredTo(flag1) <= 6) {
                    loc.add(Direction.WEST);
                }
            }
        } else if(hasFlag) {
            target = sa.decodeLocation(rc.readSharedArray(0));
        } else if(ID <= 6) {
            target = sa.decodeLocation(rc.readSharedArray(SA.FLAG1));
            if(rc.getLocation().distanceSquaredTo(target) <= 1) {
                return target;
            }
        } else if(ID <= 12) {
            target = sa.decodeLocation(rc.readSharedArray(SA.FLAG2));
            if(rc.getLocation().distanceSquaredTo(target) <= 1) {
                return target;
            }
        } else if(ID <= 18) {
            target = sa.decodeLocation(rc.readSharedArray(SA.FLAG3));
            if(rc.getLocation().distanceSquaredTo(target) <= 1) {
                return target; 
            }
        } else if(rc.getRoundNum() < 200) {
            MapLocation[] locations = rc.senseNearbyCrumbs(-1);
            if(locations.length > 0) target = locations[0];
        } else {
            target = sa.decodeLocation(rc.readSharedArray(SA.enemyFlag));
        }

        Direction astar = Direction.CENTER;
        if(target != null) {
            Direction towards = rc.getLocation().directionTo(target);
            if(rc.canFill(rc.getLocation().add(towards))) {
                rc.fill(rc.getLocation().add(towards));
            }

            astar = AStar.getBestDirection(rc, target);
        }

        if(astar != Direction.CENTER) {
            if(rc.canMove(astar)) {
                rc.move(astar);
            } 
        }  
            
        if(rc.canMove(dir)) {
            rc.move(dir);
        }
        
        if(rc.hasFlag() != hasFlag) {
            rc.writeSharedArray(SA.enemyFlag, 0);
        }

        if(rc.hasFlag() && rc.getLocation().equals(target)) {
            rc.dropFlag(target);
            if(rc.readSharedArray(sa.decodePrefix(SA.FLAG1)) == 0) {
                rc.writeSharedArray(SA.FLAG1, sa.encode(target, 1));
            } else if(rc.readSharedArray(sa.decodePrefix(SA.FLAG2)) == 0) {
                rc.writeSharedArray(SA.FLAG2, sa.encode(target, 1));
            } else {
                rc.writeSharedArray(SA.FLAG3, sa.encode(target, 1));
            }
        }

        return target;
    }

    /**
     * method for all fighting movement/ attack patterns
     * @param rc
     * @throws GameActionException
     */
    public static void combat(RobotController rc) throws GameActionException {
        if(!rc.isSpawned()) {
            return;
        }
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                
        for(RobotInfo robot : enemyRobots) {
            if(rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
            } 
        }

        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());

        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) {
                rc.heal(robot.getLocation());
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

    public static void build(RobotController rc) throws GameActionException {
        if( ID <= 18 && rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
            rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        }
    }
}
