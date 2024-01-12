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
    static SA sa = new SA();

    //number that indicates when the robot move in the turn
    static int ID = 0;

    static final Random rng = new Random(6147);

    //string used for debugging purposes
    static String indicator; 
    static RobotController rc;

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

    public static void run(RobotController m_rc) throws GameActionException {
        rc = m_rc;

        while(true) {
            //used for debugging, only value that should be passed through rc.setIndicator
            //can be seen by hovering over robot
            indicator = ID + ": ";

            if(rc.getRoundNum() == 1) init();
            if(rc.getRoundNum() % 750 == 0) globals();

            if(!rc.isSpawned()) {
                MapLocation[] spawnLocs = rc.getAllySpawnLocations();
                
                for(MapLocation spawn : spawnLocs) {
                    if (rc.canSpawn(spawn)) rc.spawn(spawn);
                }
            } else {
                //each turn we cycle through:  
                // 1: flagStuff() -> determines whether to pick up flags
                // 2: move() -> determines where the robot should move
                // 3: combat() -> attacks enemies
                // 4: build() -> building traps

                flagStuff();
                combat();
                move();
                build();
                
                //this method is currently only used to add flags to shared array
                map.updateMap();
            }

            rc.setIndicatorString(indicator);
            Clock.yield();
        }
    }

    /**
     * handles initializing all static fields
     */
    public static void init() throws GameActionException {
        map.setDimension(rc.getMapWidth(), rc.getMapHeight(), rc);
        sa.setDimension(rc.getMapWidth(), rc.getMapHeight(), rc);
        
        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(3, rc.readSharedArray(3) + 1);
        ID = rc.readSharedArray(3);
        if(rc.readSharedArray(3) == 50) {
            rc.writeSharedArray(3, 0);
        }
    }

    public static void flagStuff() throws GameActionException {
        //writes into the shared array the location of the target flag to get
        // ?potentially move into method that is just used for writing to shared array
        // ?if more things like this occur
        if(sa.decodePrefix(SA.enemyFlag) == 0) {
            if(rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
                rc.writeSharedArray(SA.enemyFlag, sa.encode(rc.senseNearbyFlags(-1, rc.getTeam().opponent())[0].getLocation(), 1));
            } else if(rc.readSharedArray(SA.enemyFlag) == 0) {
                if(rc.senseBroadcastFlagLocations().length != 0) {
                    MapLocation loc = rc.senseBroadcastFlagLocations()[rc.senseBroadcastFlagLocations().length - 1];
                    rc.writeSharedArray(SA.enemyFlag, sa.encode(loc, 0));
                }
            }
        }

        //picks up enemy flags
        if(rc.canPickupFlag(rc.getLocation()) && rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
            rc.pickupFlag(rc.getLocation());
        }

        //creating escort for returning flags
        if(rc.hasFlag() && !hasMyFlag()) {
            rc.writeSharedArray(SA.escort, sa.encode(rc.getLocation(), 1));
        }
    }

    /**
     * @return true if carrying a flag for the team the robot is on
     */
    private static boolean hasMyFlag() throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1);
        for(FlagInfo flag : flags) {
            if(flag.getLocation().equals(rc.getLocation())) {
                return flag.getTeam() == rc.getTeam();
            }
        }
        return false;
    }

    

    /**
     * Determines where the robot should move on the given turn
     * @param rc
     * @return
     * @throws GameActionException
     */
    public static MapLocation move() throws GameActionException {
        //this will be where we attempt to move
        MapLocation target = getTarget();

        //used as random movement if we don't have a target
        Direction dir = directions[rng.nextInt(directions.length)];
        boolean hasFlag = rc.hasFlag();

        Direction astar = Direction.CENTER;
        if(target != null) {
            Direction towards = rc.getLocation().directionTo(target);
            
            //how we deal with water currently
            if(rc.canFill(rc.getLocation().add(towards))) {
                rc.fill(rc.getLocation().add(towards));
            }

            astar = AStar.getBestDirection(rc, target);
        }

        if(astar != Direction.CENTER && rc.canMove(astar)) rc.move(astar);
        if(rc.canMove(dir)) rc.move(dir);
        
        //updating shared array that a flag was dropped off during 
        //this robots movement
        if(rc.hasFlag() != hasFlag) {
            rc.writeSharedArray(SA.enemyFlag, 0);
            rc.writeSharedArray(SA.escort, 0);
        }
        indicator += "t: " + target + "\n";
        return target;
    }

    /**
     * god this is a mess
     * need a decision tree without all these fucking iffs 
     */
    private static MapLocation getTarget() throws GameActionException {
        MapLocation target = null;

        if(hasMyFlag()) {
            //TODO: 
            target = rc.getLocation();
            return target;
        } 
        
        //attempts to return flag to closest spawn location
        if(rc.hasFlag()) {
            target = sa.decodeLocation(SA.FLAG1);
            MapLocation[] spawnLocs = rc.getAllySpawnLocations();
            int min = 900;
            for(MapLocation spawn : spawnLocs) {
                if(rc.getLocation().distanceSquaredTo(spawn) < min) {
                    min = rc.getLocation().distanceSquaredTo(spawn);
                    target = spawn;
                }
            }
            return target;
        } 

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if(enemies.length > 0) {
            if(rc.getHealth() < 300) {
                target = rc.getLocation().add(rc.getLocation().directionTo(enemies[0].location).opposite());
            } else {
                target = rc.getLocation().add(rc.getLocation().directionTo(enemies[0].location));
            }
        }
        
        //for ducks that are defending the flags 
        if(ID <= 3) {
            int temp = ID;
            int targetFlag = SA.FLAG1;
            switch (temp) {
                case 1:
                    targetFlag = SA.FLAG1;
                    break;
                case 2: 
                    targetFlag = SA.FLAG2;
                    break;
                case 3: 
                    targetFlag = SA.FLAG3; 
                    break;
            }
            
            //adding defenses if we sense enemey robots
            target = sa.decodeLocation(targetFlag);
            if(rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length != 0) {
                rc.writeSharedArray(SA.defend, sa.encode(sa.decodeLocation(targetFlag), 1) );
            } else if(sa.decodePrefix(targetFlag) == 1) {
                rc.writeSharedArray(SA.defend, 0);
            }

            return target;
        } 

        if(ID <= 18 && sa.decodePrefix(SA.defend) == 1) {
            target = sa.decodeLocation(SA.defend);
            return target;
        }
    
        
        if(rc.getRoundNum() < 200) {
            MapLocation[] locations = rc.senseNearbyCrumbs(-1);
            if(locations.length > 0) target = locations[0];

            if(target == null && rc.getRoundNum() > 175) {
                target =  new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            }
            return target;
        } 
        
        if(rc.readSharedArray(SA.escort) != 0 && ID <= 30) {
            target = sa.decodeLocation(SA.escort);
            return target;
        } 
        
        
        target = sa.decodeLocation(SA.enemyFlag);
        return target;
    }

    /**
     * method for all fighting movement/ attack patterns
     * @param rc
     * @throws GameActionException
     */
    public static void combat() throws GameActionException {
        //attacks any enemy robots it can
        int minHealth = 1001;
        MapLocation target = null;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());   
        for(RobotInfo robot : enemyRobots) {
            if(rc.canAttack(robot.getLocation()) 
                && rc.senseRobotAtLocation(robot.getLocation()).getHealth() < minHealth) {
                minHealth = rc.senseRobotAtLocation(robot.getLocation()).getHealth();
                target = robot.getLocation();
            }
        }

        if(target != null) {
            rc.attack(target);
        }

        //heals any friendly robots it  can
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) rc.heal(robot.getLocation());
        }
    }

    /**
     * Attempts to buy global upgrades
     * Buys Action at turn 750
     * Buys Healing at turn 1500
     */
    public static void globals() throws GameActionException {
        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) rc.buyGlobal(GlobalUpgrade.ACTION);
        if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);
    }

    /**
     * Determines if the robot should build, and what it should build
     * Currently just goes for explosive traps 
     */
    public static void build() throws GameActionException {
        if(ID <= 18 && rc.canBuild(TrapType.STUN, rc.getLocation())) {
            rc.build(TrapType.STUN, rc.getLocation());
        }
    }
}
