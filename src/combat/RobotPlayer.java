package combat;

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
    static int NUM_ROBOTS_TO_DEFEND = 0;
    static int NUM_ROBOTS_TO_ESCORT = 0;
    static int ENEMIES_PER_TRAP = 3;


    //string used for debugging purposes
    static String indicator; 
    static RobotController rc;
    static final Random rng = new Random(6147);

    public static void run(RobotController m_rc) throws GameActionException {
        rc = m_rc;

        while(true) {
            //used for debugging, only value that should be passed through rc.setIndicator
            //can be seen by hovering over robot
            indicator = ID + ": ";

            if(rc.getRoundNum() == 1) init();
            if(rc.getRoundNum() % 750 == 0) globals();

            //tries to spawn in the robot if we can
            if(!rc.isSpawned()) {
                spawn();
                
            } 

            //actions to perform if we are spawned in, or just got spawned in
            if(rc.isSpawned()) {
                flagStuff();
                move();
                    
                //this method is currently only used to add flags to shared array   
                map.updateMap();
            }
            
            rc.setIndicatorString(indicator);
            Clock.yield();
        }
    }

    /**
     * handles initializing all static fields and assigning each robot an ID
     */
    public static void init() throws GameActionException {
        map.setDimension(rc.getMapWidth(), rc.getMapHeight(), rc);
        
        SA.init(rc.getMapWidth(), rc.getMapHeight(), rc);
        Pathfinding.init(rc);
        Combat.init(rc);
        Utils.init(rc);

        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(3, rc.readSharedArray(3) + 1);
        ID = rc.readSharedArray(3);
        if(rc.readSharedArray(3) == 50) {
            rc.writeSharedArray(3, 0);
        }
    }

    /**
     * spawns the robot in
     * currently priortizing the spawning the robot onto a flag that needs defense if such a flag exists 
     * otherwise it will randomly spawn the robot
     */
    public static boolean spawn() throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        indicator += "SPAWN ";

        if(rc.readSharedArray(SA.defend) != 0) {
            indicator += "DEF";
            MapLocation target = SA.getLocation(SA.defend);

            for(MapLocation spawn : spawnLocs) {
                //2 makes sure it is in the square around the flag
                if(spawn.distanceSquaredTo(target) < 2) {
                    if(rc.canSpawn(spawn)) {
                        rc.spawn(spawn);
                        return true;
                    }    
                }
            }
        }

        for(MapLocation spawn : spawnLocs) {
            if (rc.canSpawn(spawn)) {
                indicator += "RND ";
                rc.spawn(spawn);
                return true;
            }
        }

        return false;
    }

    
    /**
     * writes enemy flags into shared array
     * removes enemy flags from shared array if they aren't there
     * picks up enemy flags
     * creates escort for friendlies that are returning flags
     */
    public static void flagStuff() throws GameActionException {
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());

        if(rc.canSenseLocation(SA.getLocation(SA.enemyFlag)) && nearbyFlags.length == 0) {
            rc.writeSharedArray(SA.enemyFlag, 0);
        }
        

        if(SA.getPrefix(SA.enemyFlag) == 0) {
            
            if(nearbyFlags.length > 0) {
                FlagInfo info = nearbyFlags[0];
                
                if(info.getTeam().equals(rc.getTeam().opponent())) {
                    RobotInfo robot = rc.senseRobotAtLocation(info.getLocation());
                    if(robot == null || robot.getTeam().equals(rc.getTeam().opponent())) {
                        rc.writeSharedArray(SA.enemyFlag, SA.encode(rc.senseNearbyFlags(-1, rc.getTeam().opponent())[0].getLocation(), 1));
                    }
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
     * Also calls a combat method if there are visible enemies
     */
    public static MapLocation move() throws GameActionException {
        MapLocation target;
        //this will be where we attempt to move
        if(Utils.isEnemies() && !rc.hasFlag()) {
            target = getCombatTarget();
            
            if(ID <= 3) {
                //adding defenses if we sense enemy robots
                indicator += "HELP ";
                rc.writeSharedArray(SA.defend, SA.encode(getFlagDefense(), 1) );
            }

            indicator += "c: " + target + "\n";
            return target;
        } 
        
        indicator += "t: ";
        target = getTarget();
        
        //used as random movement if we don't have a target
        Direction dir = Utils.randomDirection();
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
            int min = Integer.MAX_VALUE;
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
            target = getFlagDefense();
            
            //resets defense location if there are no enemies 
            if(SA.getLocation(SA.defend).equals(target) && !Utils.isEnemies() && rc.canSenseLocation(target)) {
                rc.writeSharedArray(SA.defend, 0);
            }

            return target;
        } 

        // Sends robots to defend 
        if(ID <= NUM_ROBOTS_TO_DEFEND && SA.getPrefix(SA.defend) == 1) {
            target = SA.getLocation(SA.defend);
            if(rc.canSenseLocation(target) && rc.senseNearbyFlags(-1, rc.getTeam()).length == 0) {
                rc.writeSharedArray(SA.defend, 0);
            }
            return target;
        }

        //Escorts a robot with a flag 
        if(rc.readSharedArray(SA.escort) != 0 && ID >= 51 - NUM_ROBOTS_TO_ESCORT) {
            target = SA.getLocation(SA.escort);
            return target;
        } 

        //Grabs crumbs if we have no other goals in life
        if(rc.getRoundNum() < 200) {
            MapLocation[] locations = rc.senseNearbyCrumbs(-1);
            if(locations.length > 0) target = locations[0];

            if(target == null && rc.getRoundNum() > 150) {
                target =  new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            }
            return target;
        } 
        
        target = SA.getLocation(SA.enemyFlag);
        return target;
    }

    /**
     * @return the flag that the robot should be defending
     * should only be called with robots 1,2,3
     */
    public static MapLocation getFlagDefense() throws GameActionException {
        switch (ID) {
            case 1:
                return SA.getLocation(SA.FLAG1);
            case 2: 
                return SA.getLocation(SA.FLAG2);
            case 3: 
                return SA.getLocation(SA.FLAG3); 
        }

        return null;
    }

    /**
     * Choosing movement target and attacking 
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        //attempting to build if enough enemies to lurn into trap
        if(Utils.getNumEnemies() >= ENEMIES_PER_TRAP) {
            build();
        }

        Combat.reset();
        boolean shouldRun = Combat.shouldRunAway();
        boolean shouldTrap = Combat.shouldTrap();

        Direction dir;

        if(shouldTrap) {
            indicator += "TRAP ";
            dir = Combat.getTrapDirection();
        } else if(shouldRun) {
            indicator += "DEF";
            dir = Combat.getDefensiveDirection();
        } else {
            indicator += "OFF";
            dir = Combat.getOffensiveDirection();
        }

        if(dir == null) dir = Direction.CENTER;

        MapLocation targetLocation = rc.getLocation().add(dir);
        if(shouldRun || shouldTrap) {
            Combat.attack();
            if(rc.canMove(dir)) rc.move(dir);
        } else {
            if(rc.canMove(dir)) rc.move(dir);
            Combat.attack();
        }

        indicator += Combat.indicator;

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
        int numTraps = 0;
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        for(MapInfo info : mapInfo) {
            if(info.getTrapType() != TrapType.NONE) {
                numTraps++;
            }
        }

        if(numTraps * ENEMIES_PER_TRAP <= Utils.getNumEnemies()) {
            if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
                rc.build(TrapType.EXPLOSIVE, rc.getLocation());
            }
        } 
    }
}
