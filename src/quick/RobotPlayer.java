package quick;

import battlecode.common.*;
import quick.pathfinding.*;
import scala.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {
    static m_Map map = new m_Map();

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
                flagStuff();

                //runs the combat loop if there are enemies 
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
        SA.init(rc.getMapWidth(), rc.getMapHeight(), rc);
        Pathfinding.init(rc);
        
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
        if(SA.getPrefix(SA.enemyFlag) == 0) {
            if(rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
                rc.writeSharedArray(SA.enemyFlag, SA.encode(rc.senseNearbyFlags(-1, rc.getTeam().opponent())[0].getLocation(), 1));
            } else if(rc.readSharedArray(SA.enemyFlag) == 0 || rc.getRoundNum() % 100 == 0) {
                if(rc.senseBroadcastFlagLocations().length != 0) {
                    int index = rng.nextInt(rc.senseBroadcastFlagLocations().length);
                    MapLocation loc = rc.senseBroadcastFlagLocations()[index];
                    rc.writeSharedArray(SA.enemyFlag, SA.encode(loc, 0));
                }
            }
        }

        //picks up enemy flags
        if(rc.canPickupFlag(rc.getLocation()) && rc.senseNearbyFlags(-1, rc.getTeam().opponent()).length > 0) {
            rc.pickupFlag(rc.getLocation());
        }

        //creating escort for returning flags
        if(rc.hasFlag() && !hasMyFlag()) {
            rc.writeSharedArray(SA.escort, SA.encode(rc.getLocation(), 1));
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
        MapLocation target;
        //this will be where we attempt to move
        if(rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length != 0 && !rc.hasFlag()) {
            target = getCombatTarget();
        } else {
           target = getTarget();
        }
         

        //used as random movement if we don't have a target
        Direction dir = directions[rng.nextInt(directions.length)];
        boolean hasFlag = rc.hasFlag();

        if(target != null) {
            Direction towards = rc.getLocation().directionTo(target);

            if(rc.canFill(rc.getLocation().add(towards))) {
                rc.fill(rc.getLocation().add(towards));
            }

            Pathfinding.initTurn();
            Pathfinding.move(target);
        }
        
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
        
        //attempts to return flag to closest spawn location
        if(rc.hasFlag() && !hasMyFlag()) {
            target = SA.getLocation(SA.FLAG1);
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
        
        //TODO this if controls defense, maybe add more ducks, mess around with it
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
            
            target = SA.getLocation(targetFlag);

            if(rc.getRoundNum() < 150 && SA.getPrefix(targetFlag) == 0) {
                if(rc.canPickupFlag(target)) rc.pickupFlag(target);
            }
            
            //adding defenses if we sense enemy robots
            if(rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length != 0) {
                rc.writeSharedArray(SA.defend, SA.encode(SA.getLocation(targetFlag), 1) );
            } else if(SA.getPrefix(targetFlag) == 1) {
                rc.writeSharedArray(SA.defend, 0);
            }

            if(rc.hasFlag()) {
                //TODO implement some sort of flag condensing 
            }

            return target;
        } 

        if(ID <= 18 && SA.getPrefix(SA.defend) == 1) {
            target = SA.getLocation(SA.defend);
            return target;
        }
    
        
        if(rc.getRoundNum() < 200) {
            MapLocation[] locations = rc.senseNearbyCrumbs(-1);
            if(locations.length > 0) target = locations[0];

            if(target == null && rc.getRoundNum() > 150) {
                target =  new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            }
            return target;
        } 
        
        if(rc.readSharedArray(SA.escort) != 0 && ID <= 30) {
            target = SA.getLocation(SA.escort);
            return target;
        } 
        
        
        target = SA.getLocation(SA.enemyFlag);
        return target;
    }

    /**
     * Choosing movement target and attacking 
     * TODO: experiment with different micro patterns
     * @return
     * @throws GameActionException
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] friendlies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation target = null;
        
        double overallEnemyDx = 0;
        double overallEnemyDy = 0;

        for(RobotInfo enemy : enemies) {
            overallEnemyDx += enemy.getLocation().x;
            overallEnemyDy += enemy.getLocation().y;
        }

        overallEnemyDx /= enemies.length;
        overallEnemyDy /= enemies.length;

        indicator += "e(dx, dy): (" + (int) overallEnemyDx + "," + (int) overallEnemyDy + ")";

        
        if(friendlies.length <= enemies.length) {
            combat();
            if(friendlies.length == 0 || friendlies.length + 3 < enemies.length) {
                target = SA.getLocation(SA.FLAG1);
                return target;
            }

            double overallFriendlyDx = 0;
            double overallFriendlyDy = 0;

            for(RobotInfo friend : friendlies) {
                overallFriendlyDx += friend.getLocation().x;
                overallFriendlyDy += friend.getLocation().y;
            }

            overallFriendlyDx /= friendlies.length;
            overallFriendlyDy /= friendlies.length;

            indicator += "f(dx, dy): (" + (int) overallFriendlyDx + "," + (int) overallFriendlyDy + ")";

            target = new MapLocation((int)overallFriendlyDx, (int)overallFriendlyDy);
        } else {
            target = getTarget();
            combat();
        }
    
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
     * TODO: Mess around with this, try other traps or a mixture potentially 
     */
    public static void build() throws GameActionException {
        if(ID <= 18 && rc.canBuild(TrapType.STUN, rc.getLocation())) {
            rc.build(TrapType.STUN, rc.getLocation());
        }
    }
}
