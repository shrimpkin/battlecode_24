package combat_aidan_sun;

import battlecode.common.*;
import combat_david_sun.Pathfinding;
import combat_david_sun.SA;
import combat_david_sun.Utils;
import scala.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

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
            //used for debugging, only value that should be passed through rc.setIndicator()
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
                combat_david_sun.SA.updateMap();
                heal();
                fill();
            }
            
            rc.setIndicatorString(indicator);
            Clock.yield();
        }
    }

    /**
     * handles initializing all static fields and assigning each robot an ID
     */
    public static void init() throws GameActionException {        
        combat_david_sun.SA.init(rc.getMapWidth(), rc.getMapHeight(), rc);
        combat_david_sun.Pathfinding.init(rc);
        combat_david_sun.Combat.init(rc);
        combat_david_sun.Utils.init(rc);

        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(combat_david_sun.SA.INDEXING, rc.readSharedArray(combat_david_sun.SA.INDEXING) + 1);
        ID = rc.readSharedArray(combat_david_sun.SA.INDEXING);
        if(rc.readSharedArray(combat_david_sun.SA.INDEXING) == 50) {
            rc.writeSharedArray(combat_david_sun.SA.INDEXING, 0);
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

        //attempting to find a location close to the flag that is being attacked
        if(rc.readSharedArray(combat_david_sun.SA.defend) != 0) {
            indicator += "DEF";
            MapLocation target = combat_david_sun.SA.getLocation(combat_david_sun.SA.defend);

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

        //randomly spawning
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
        MapLocation enemyFlagTarget = combat_david_sun.SA.getLocation(combat_david_sun.SA.enemyFlag);
        //reseting enemy flags if not at SA location
        if(rc.canSenseLocation(enemyFlagTarget) && nearbyFlags.length == 0) {
            rc.writeSharedArray(combat_david_sun.SA.enemyFlag, 0);
        }
        
        //writing enemy flags into shared array
        if(combat_david_sun.SA.getPrefix(combat_david_sun.SA.enemyFlag) == 0) {
            
            if(nearbyFlags.length > 0) {
                FlagInfo info = nearbyFlags[0];
                
                if(info.getTeam().equals(rc.getTeam().opponent())) {
                    RobotInfo robot = rc.senseRobotAtLocation(info.getLocation());

                    //only adds the flag to the shared array if we don't already possess it
                    if(robot == null || robot.getTeam().equals(rc.getTeam().opponent())) {
                        rc.writeSharedArray(combat_david_sun.SA.enemyFlag, combat_david_sun.SA.encode(info.getLocation(), 1));
                    }
                }

            } else if(rc.readSharedArray(combat_david_sun.SA.enemyFlag) == 0 || rc.getRoundNum() % 100 == 0) {
                if(rc.senseBroadcastFlagLocations().length != 0) {
                    MapLocation loc = rc.senseBroadcastFlagLocations()[0];
                    rc.writeSharedArray(combat_david_sun.SA.enemyFlag, combat_david_sun.SA.encode(loc, 0));
                }
            }
        }

        //picks up enemy flags
        for(FlagInfo flag : nearbyFlags) {
            MapLocation flagLoc = flag.getLocation();
            if(rc.canSenseLocation(flagLoc) && rc.canPickupFlag(flagLoc)) {
                rc.pickupFlag(flagLoc);
                rc.writeSharedArray(combat_david_sun.SA.enemyFlag, 0);
            }
        }
        

        //creating escort for returning flags
        if(rc.hasFlag() && !hasMyFlag()) {
            rc.writeSharedArray(combat_david_sun.SA.escort, combat_david_sun.SA.encode(rc.getLocation(), 1));
            if(combat_david_sun.SA.getLocation(combat_david_sun.SA.enemyFlag).distanceSquaredTo(rc.getLocation()) <= 2) {
                rc.writeSharedArray(combat_david_sun.SA.enemyFlag, 0);
            }
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
        if(combat_david_sun.Utils.isEnemies() && !rc.hasFlag()) {
            target = getCombatTarget();
            
            if(ID <= 3) {
                //adding defenses if we sense enemy robots
                indicator += "HELP ";
                rc.writeSharedArray(combat_david_sun.SA.defend, combat_david_sun.SA.encode(getFlagDefense(), 1) );
            }

            indicator += "c: " + target + "\n";
            return target;
        } 
        
        indicator += "t: ";
        target = getTarget();
        
        //used as random movement if we don't have a target
        Direction dir = combat_david_sun.Utils.randomDirection();
        boolean hasFlag = rc.hasFlag();

        if(target != null) {
            Direction towards = rc.getLocation().directionTo(target);

            if(rc.canFill(rc.getLocation().add(towards))) {
                rc.fill(rc.getLocation().add(towards));
            }

            combat_david_sun.Pathfinding.initTurn();
            Pathfinding.move(target);
        }
        
        if(rc.canMove(dir)) rc.move(dir);
        
        //updating shared array that a flag was dropped off during 
        //this robots movement
        if(rc.hasFlag() != hasFlag) {
            rc.writeSharedArray(combat_david_sun.SA.enemyFlag, 0);
            rc.writeSharedArray(combat_david_sun.SA.escort, 0);
        }
        indicator += target + "\n";
        return target;
    }

    /**
     * Chooses a movement target in this priority:
     *  -has flag: goes to closest spawn
     *  -defense: defends corresponding flag
     *  -escort: escorts corresponding flag
     *  -scouting: finds crumbs (only rounds < 150)
     *  -attacking: finds enemy flags
     */
    private static MapLocation getTarget() throws GameActionException {
        MapLocation target = null;
        
        //attempts to return flag to closest spawn location
        //TODO: avoid enemies
        if(rc.hasFlag() && !hasMyFlag()) {
            target = combat_david_sun.SA.getLocation(combat_david_sun.SA.FLAG1);
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
        
        //controls defense
        if(ID <= 3) {            
            target = getFlagDefense();
            
            //resets defense location if there are no enemies 
            if(combat_david_sun.SA.getLocation(combat_david_sun.SA.defend).equals(target) && !combat_david_sun.Utils.isEnemies() && rc.canSenseLocation(target)) {
                rc.writeSharedArray(combat_david_sun.SA.defend, 0);
            }

            return target;
        } 

        // Sends robots to defend 
        if(ID <= NUM_ROBOTS_TO_DEFEND && combat_david_sun.SA.getPrefix(combat_david_sun.SA.defend) == 1) {
            target = combat_david_sun.SA.getLocation(combat_david_sun.SA.defend);
            if(rc.canSenseLocation(target) && rc.senseNearbyFlags(-1, rc.getTeam()).length == 0) {
                rc.writeSharedArray(combat_david_sun.SA.defend, 0);
            }
            return target;
        }

        //Escorts a robot with a flag 
        if(rc.readSharedArray(combat_david_sun.SA.escort) != 0 && ID >= 51 - NUM_ROBOTS_TO_ESCORT) {
            target = combat_david_sun.SA.getLocation(combat_david_sun.SA.escort);
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
        
        //go aggresive and if not aggresive targets exists go middle
        target = combat_david_sun.SA.getLocation(combat_david_sun.SA.enemyFlag);
        if(target.equals(new MapLocation(0,0))) {
            target = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        }
        return target;
    }

    /**
     * @return the flag that the robot should be defending
     * should only be called with robots 1,2,3
     */
    public static MapLocation getFlagDefense() throws GameActionException {
        switch (ID) {
            case 1:
                return combat_david_sun.SA.getLocation(combat_david_sun.SA.FLAG1);
            case 2: 
                return combat_david_sun.SA.getLocation(combat_david_sun.SA.FLAG2);
            case 3: 
                return combat_david_sun.SA.getLocation(SA.FLAG3);
        }

        return null;
    }

    /**
     * Choosing movement target and attacking 
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        //attempting to build if enough enemies to lurn into trap
        if(combat_david_sun.Utils.getNumEnemies() >= ENEMIES_PER_TRAP) {
            build();
        }

        combat_david_sun.Combat.reset();
        boolean shouldRun = combat_david_sun.Combat.shouldRunAway();
        boolean shouldTrap = combat_david_sun.Combat.shouldTrap();

        Direction dir;

        if(shouldTrap) {
            indicator += "TRAP ";
            dir = combat_david_sun.Combat.getTrapDirection();
        } else if(shouldRun) {
            indicator += "DEF";
            dir = combat_david_sun.Combat.getDefensiveDirection();
        } else {
            indicator += "OFF";
            dir = combat_david_sun.Combat.getOffensiveDirection();
        }

        if(dir == null) dir = Direction.CENTER;

        MapLocation targetLocation = rc.getLocation().add(dir);
        if(shouldRun || shouldTrap) {
            combat_david_sun.Combat.attack();
            if(rc.canMove(dir)) rc.move(dir);
        } else {
            if(rc.canMove(dir)) rc.move(dir);
            combat_david_sun.Combat.attack();
        }

        indicator += combat_aidan_sun.Combat.indicator;

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
     * Currently just goes for explosive traps if there are a lot of nearby
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

        // if(numTraps * ENEMIES_PER_TRAP <= Utils.getNumEnemies()) {
        //     if(rc.canBuild(TrapType.EXPLOSIVE, rc.getLocation())) {
        //         rc.build(TrapType.EXPLOSIVE, rc.getLocation());
        //     }
        // } 

        // if(rc.getCrumbs() >= 1000 && rc.canBuild(TrapType.STUN, rc.getLocation())) {
        //     rc.build(TrapType.STUN, rc.getLocation());
        // }
    }

    /**
     * Fills in a random tiles if we have extra action and see no enemies
     * @throws GameActionException
     */
    public static void fill() throws GameActionException {
        if(!Utils.isEnemies()) {
            MapInfo[] mapInfo = rc.senseNearbyMapInfos();
            for(MapInfo info : mapInfo) {
                if(rc.canFill(info.getMapLocation())) rc.fill(info.getMapLocation());
            }
        }
    }

    public static void heal() throws GameActionException {
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) rc.heal(robot.getLocation());
        }
    }
}
