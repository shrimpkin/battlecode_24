package tweakedCombat;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GlobalUpgrade;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;
import battlecode.world.Trap;
import scala.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    //number that indicates when the robot move in the turn
    static int ID = 0;
    static int NUM_ROBOTS_TO_DEFEND = 5;
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
                bombIfAboutToDie();
                flagStuff();
                defenderBuild(); // probably a better place to put this :/
                move();
                SA.updateMap();
                heal();
                fill();
            }
            
            rc.setIndicatorString(indicator);
            Clock.yield();
        }
    }

    public static void bombIfAboutToDie() {
        if (rc.getHealth() < 100) {
            MapLocation cur = rc.getLocation();
            if (rc.canBuild(TrapType.EXPLOSIVE, cur)) {
                try {
                    rc.build(TrapType.EXPLOSIVE, cur);
                } catch (GameActionException e) {
                    // just continue, dont want to explicit err
                }
            }
        }
    }

    /**
     * handles initializing all static fields and assigning each robot an ID
     */
    public static void init() throws GameActionException {        
        SA.init(rc.getMapWidth(), rc.getMapHeight(), rc);
        Pathfinding.init(rc);
        Combat.init(rc);
        Utils.init(rc);

        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(SA.INDEXING, rc.readSharedArray(SA.INDEXING) + 1);
        ID = rc.readSharedArray(SA.INDEXING);
        if(rc.readSharedArray(SA.INDEXING) == 50) {
            rc.writeSharedArray(SA.INDEXING, 0);
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
        if(rc.readSharedArray(SA.defend) != 0) {
            indicator += "DEF";
            MapLocation target = SA.getLocation(SA.defend);
            for(MapLocation spawn : spawnLocs) {
                //2 makes sure it is in the square around the flag
                if(spawn.isWithinDistanceSquared(target, 2)) {
                    if(rc.canSpawn(spawn)) {
                        rc.spawn(spawn);
                        return true;
                    }    
                }
            }
        }
        // TODO: maybe choose the closest to enemy flag / escort also?
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
     *
     */
    public static void flagStuff() throws GameActionException {
        FlagInfo[] nearbyFlags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());
        MapLocation enemyFlagTarget = SA.getLocation(SA.enemyFlag);
        //reset enemy flags if not at SA location
        if(rc.canSenseLocation(enemyFlagTarget) && nearbyFlags.length == 0) {
            rc.writeSharedArray(SA.enemyFlag, 0);
        }
        
        //writing enemy flags into shared array
        if(SA.getPrefix(SA.enemyFlag) == 0) {
            
            if(nearbyFlags.length > 0) {
                FlagInfo info = nearbyFlags[0];
                
                if(info.getTeam().equals(rc.getTeam().opponent())) {
                    RobotInfo robot = rc.senseRobotAtLocation(info.getLocation());

                    //only adds the flag to the shared array if we don't already possess it
                    if(robot == null || robot.getTeam().equals(rc.getTeam().opponent())) {
                        rc.writeSharedArray(SA.enemyFlag, SA.encode(info.getLocation(), 1));
                    }
                }

            } else if(rc.readSharedArray(SA.enemyFlag) == 0 || rc.getRoundNum() % 100 == 0) {
                if(rc.senseBroadcastFlagLocations().length != 0) {
                    MapLocation loc = rc.senseBroadcastFlagLocations()[0];
                    rc.writeSharedArray(SA.enemyFlag, SA.encode(loc, 0));
                }
            }
        }

        //picks up enemy flags
        for(FlagInfo flag : nearbyFlags) {
            MapLocation flagLoc = flag.getLocation();
            if(rc.canSenseLocation(flagLoc) && rc.canPickupFlag(flagLoc)) {
                rc.pickupFlag(flagLoc);
                rc.writeSharedArray(SA.enemyFlag, 0);
            }
        }
        

        //creating escort for returning flags
        if(rc.hasFlag() && !hasMyFlag()) {
            rc.writeSharedArray(SA.escort, SA.encode(rc.getLocation(), 1));
            if(SA.getLocation(SA.enemyFlag).distanceSquaredTo(rc.getLocation()) <= 2) {
                rc.writeSharedArray(SA.enemyFlag, 0);
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
        // TODO: fixed half-assed patch for bots trying to do combat during setup
        if(Utils.isEnemies() && !rc.hasFlag() && rc.getRoundNum() >= 200) {
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
        // stay in place if it reached the target
        if(rc.canMove(dir) && !rc.getLocation().equals(target)) rc.move(dir);
        
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
        //TODO: avoid enemies - idea: possibly track approximation of how much enemies are near the flag
        // maybe have each duck within radius N of our flag query enemy count and track the highest # in SA?
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

        //controls defense
        if(ID <= 3) {
            target = getFlagDefense();

            //resets defense location if there are no enemies
            if(SA.getLocation(SA.defend).equals(target) && !Utils.isEnemies() && rc.canSenseLocation(target)) {
                rc.writeSharedArray(SA.defend, 0);
            }

            return target;
        }

        //Grabs crumbs if we have no other goals in setup -- also allocate a few turns for crumbs blocked by dams
        if(rc.getRoundNum() < 210) {
            MapLocation[] locations = rc.senseNearbyCrumbs(-1);
            if(locations.length > 0) target = locations[0];

            if(target == null && rc.getRoundNum() > 150) {
                target =  new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            }
            return target;
        }

        // Sends robots to defend - adding amt from perma-defenders
        if(ID <= NUM_ROBOTS_TO_DEFEND + 3 && SA.getPrefix(SA.defend) == 1) {
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
        
        //go aggressive and if not aggressive targets exists go to either middle, defense, or maybe escort
        // TODO: theres probably a way better labor splitting scheme
        target = SA.getLocation(SA.enemyFlag);
        if(target.equals(new MapLocation(0,0))) {
            // 2 to 1 split to defend/escort if applicable to avoid over-stacking the pathfinding
            // otherwise just go to middle
            if (ID % 3 < 2 && rc.readSharedArray(SA.defend) != 0) target = SA.getLocation(SA.defend);
            else if (ID % 3 == 2 && rc.readSharedArray(SA.escort) != 0) target = SA.getLocation(SA.escort);
            else target = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        }
        return target;
    }

    /**
     * @return the flag that the robot should be defending
     * should only be called with robots 1,2,3
     */
    public static MapLocation getFlagDefense() throws GameActionException {
        switch (ID) {
            case 1: return SA.getLocation(SA.FLAG1);
            case 2: return SA.getLocation(SA.FLAG2);
            case 3: return SA.getLocation(SA.FLAG3);
        }
        return null;
    }

    /**
     * Choosing movement target and attacking 
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        //attempting to build if enough enemies to lure into trap or defend from flag captures
        int enemies = Utils.getNumEnemies();
        if(enemies >= ENEMIES_PER_TRAP || (ID <= 3 && enemies > 0)) {
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
     * very niche method that handles passive defender building behavior
     * namely : tries to place stun on corners whenever possible
     */
    public static void defenderBuild() throws GameActionException {
        if (ID >= 4) return; // not a defender
        RobotInfo[] enems = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (rc.getActionCooldownTurns() > 0 || rc.getCrumbs() < 250) return; // can't build: on cool-down / no money
        // active defense - put bombs on cardinal direction closest to the nearest enemy in actual round
        if (enems.length > 0 && rc.getRoundNum() > 200) {
            int minDist = Integer.MAX_VALUE; // min for all positions
            MapLocation target = null;
            for (Direction dir : Utils.directions){
                MapLocation pos = rc.getLocation().add(dir);
                if (!rc.canBuild(TrapType.EXPLOSIVE, pos)) continue;
                int md = Integer.MAX_VALUE; // min dist local
                for (RobotInfo enemy : enems) {
                    md = Math.min(md, enemy.getLocation().distanceSquaredTo(pos));
                }
                if (md < minDist) {
                    minDist = md;
                    target = pos;
                }
            }
            if (target != null) {
                rc.build(TrapType.EXPLOSIVE, target);
                return;
            }
        }
        if (rc.getActionCooldownTurns() > 0 || rc.getCrumbs() < 100) return; // can't build: on cool-down / no money
        // passive defense - put stun trap on corners to buy time
        if (rc.getLocation().equals(getFlagDefense())) { // on flag, passive defense
            for (MapLocation pos : Utils.corners(rc.getLocation())) {
                MapInfo pinfo = rc.senseMapInfo(pos);
                if (pinfo.getTrapType() == TrapType.NONE && rc.canBuild(TrapType.STUN, pos)){
                    rc.build(TrapType.STUN, pos);
                    return;
                }
            }
        }
    }

    /**
     * Determines if the robot should build, and what it should build
     * Currently just goes for explosive traps if there are a lot of nearby
     */
    public static void build() throws GameActionException {
        int enemyCnt = Utils.getNumEnemies();
        int numTraps = 0;
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        for(MapInfo info : mapInfo) {
            if(info.getTrapType() != TrapType.NONE) {
                numTraps++;
            }
        }

        if(numTraps * ENEMIES_PER_TRAP <= enemyCnt) {
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
        //heals any friendly robots it can, again prioritize flag bearers and low HP units
        if (rc.getActionCooldownTurns() > 0) return; // on cooldown
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation target = null;
        int minHealth = 1001;
        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) {
                if (robot.hasFlag()) {
                    rc.heal(robot.getLocation());
                    return;
                } else {
                    if (robot.getHealth() < minHealth) {
                        minHealth = robot.getHealth();
                        target = robot.getLocation();
                    }
                }
            }
        }
        if (target != null) rc.heal(target);
    }
}
