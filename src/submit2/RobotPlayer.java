package submit2;

import battlecode.common.*;
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

                //build();
                
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
        Combat.init(rc);

        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(3, rc.readSharedArray(3) + 1);
        ID = rc.readSharedArray(3);
        if(rc.readSharedArray(3) == 50) {
            rc.writeSharedArray(3, 0);
        }
    }

    public static void flagStuff() throws GameActionException {
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());

        if(rc.canSenseLocation(SA.getLocation(SA.enemyFlag)) && nearbyFlags.length == 0) {
            rc.writeSharedArray(SA.enemyFlag, 0);
        }
        //writes into the shared array the location of the target flag to get
        // ?potentially move into method that is just used for writing to shared array
        // ?if more things like this occur

        if(SA.getPrefix(SA.enemyFlag) == 0) {
            
            if(nearbyFlags.length > 0) {
                RobotInfo info = rc.senseRobotAtLocation(nearbyFlags[0].getLocation());
                
                if(info == null || info.getTeam().equals(rc.getTeam().opponent())) {
                    rc.writeSharedArray(SA.enemyFlag, SA.encode(rc.senseNearbyFlags(-1, rc.getTeam().opponent())[0].getLocation(), 1));
                }

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
            if(rc.getLocation().equals(SA.getLocation(SA.enemyFlag))) {
                rc.writeSharedArray(SA.enemyFlag, 0);
            }
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
            indicator += "c: " + target + "\n";
            return target;
        } 
        
        indicator += "t: ";
        target = getTarget();
        
         

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
        indicator += target + "\n";
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
        
        if(rc.readSharedArray(SA.escort) != 0 && ID >= 40) {
            target = SA.getLocation(SA.escort);
            return target;
        } 
        
        target = SA.getLocation(SA.enemyFlag);
        return target;
    }

    /**
     * Choosing movement target and attacking 
     * @return
     * @throws GameActionException
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        build();
        Combat.resetShouldRunAway();
        boolean shouldRun = Combat.shouldRunAway();
        if(rc.getRoundNum() <= 210 && rc.getRoundNum() >= 200) {
            shouldRun = true;
        }

        Direction dir;
        if(shouldRun) {
            dir = Combat.getDefensiveDirection();
        } else {
            dir = Combat.getOffensiveDirection();
        }

        if(dir == null) dir = Direction.CENTER;

        MapLocation targetLocation = rc.getLocation().add(dir);
        if(shouldRun) {
            Combat.attack();
            if(rc.canMove(dir)) rc.move(dir);
        } else {
            if(rc.canMove(dir)) rc.move(dir);
            Combat.attack();
        }

        return targetLocation;
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
        MapLocation[] allySpawnLocs = rc.getAllySpawnLocations();
        for(MapLocation allySpawnLoc : allySpawnLocs) {
            int numTrapsNearby = 0;
            MapInfo[] nearbyLocs = rc.senseNearbyMapInfos(allySpawnLoc, 4);
            for(MapInfo nearbyLoc : nearbyLocs) {
                if(nearbyLoc.getTrapType() != TrapType.NONE) {
                    numTrapsNearby += 1;
                }
            }
            if(numTrapsNearby >= 1) {
                continue;
            } else {
                if(rc.canBuild(TrapType.EXPLOSIVE, allySpawnLoc)) {
                    rc.build(TrapType.EXPLOSIVE, allySpawnLoc);
                    break;
                }
            }
        }

        // putting down bombs when there are lots of enemies nearby and fighting
        int numEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
        int numTrapsNearby = 0;
        MapInfo[] nearbyLocs = rc.senseNearbyMapInfos(rc.getLocation(), -1);
        for(MapInfo nearbyLoc : nearbyLocs) {
            if(nearbyLoc.getTrapType() != TrapType.NONE) {
                numTrapsNearby += 1;
            }
        }

        if(numEnemies >= 4 && rc.getHealth() <= 900 && numTrapsNearby <= 2) {
            if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
            }
        }       
         
        // int numTraps = 0;
        // MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        // for(MapInfo info : mapInfo) {
        //     if(info.getTrapType() != TrapType.NONE) {
        //         numTraps++;
        //     }
        // }

        // if(numTraps * ENEMIES_PER_TRAP <= Utils.getNumEnemies()) {
        //     if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
        //         rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        //     }
        // } 

        // if(numTraps * ENEMIES_PER_TRAP <= Utils.getNumEnemies()) {
        //     if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
        //         rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        //     }
        // } 

        // if(rc.getCrumbs() >= 1000 && rc.canBuild(TrapType.STUN, rc.getLocation())) {
        //     rc.build(TrapType.STUN, rc.getLocation());
        // }
    }
}
