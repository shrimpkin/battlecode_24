package v8;

import battlecode.common.*;
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
    static final int baseSeed = 6147;
    static final Random rng = new Random(baseSeed);

    public static void run(RobotController m_rc) throws GameActionException {
        String[] syms = { // debug printing
                "how?",
                "ROTATIONALLY SYMMETRIC",
                "HORIZONTALLY SYMMETRIC",
                "rot or horiz sym",
                "VERTICALLY SYMMETRIC",
                "vert or rot sym",
                "vert or horiz sym",
                "no clue all three are possible"
        };
        rc = m_rc;

        while(true) {
            //used for debugging, only value that should be passed through rc.setIndicator()
            //can be seen by hovering over robot
            indicator = ID + ": ";

            if(rc.getRoundNum() == 1) init();
            Combat.modeLog[rc.getRoundNum()] = Combat.CombatMode.NONE;
            Combat.actionLog[rc.getRoundNum()] = Combat.ActionMode.NONE;
            rc.writeSharedArray(SA.escort, SA.encode(SA.getLocation(SA.escort), 0));

            //tries to spawn in the robot if we can
            if(!rc.isSpawned()) {
                spawn();
                // sacrifice tiny bit of movement in order to ensure all 3 spawns have ducks spawn on them
                if (rc.getRoundNum() == 1 && ID < 9) Clock.yield();
            } 

            //actions to perform if we are spawned in, or just got spawned in
            if(rc.isSpawned()) {
                MapLocation init = rc.getLocation();
                globals();
                flagStuff();
                move();
                SA.updateMap();
                heal();
                defenderBuild(); // probably a better place to put this :/
                fill();
                MapLocation result = rc.getLocation();
                if (!init.equals(result)) MapRecorder.updateSurroundings();
            }
            for (int i = 0; i < 3; i++){
                if (rc.readSharedArray(i) != 0)
                    rc.setIndicatorDot(SA.getLocation(i), 0, 255, 0);
            }

            rc.setIndicatorString(indicator);
            Clock.yield();
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
        FlagReturn.init(rc);
        MapRecorder.init(rc);

        //assigning each duck an ID that is based off of when they move 
        //in a turn
        rc.writeSharedArray(SA.INDEXING, rc.readSharedArray(SA.INDEXING) + 1);
        ID = rc.readSharedArray(SA.INDEXING);
        rng.setSeed(baseSeed + ID); // add some variation so bots don't have the same targeting
        // write initial value for symmetry query (all symmetries possible)
        rc.writeSharedArray(SA.symmetry, 0b111);
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
                if(spawn.distanceSquaredTo(target) < 2) {
                    if(rc.canSpawn(spawn)) {
                        rc.spawn(spawn);
                        return true;
                    }    
                }
            }
        }

        MapLocation target = SA.getLocation(SA.TARGET_ENEMY_FLAG);
        int minDist = Integer.MAX_VALUE;
        MapLocation bestSpawn = null;

        //randomly spawning
        for(MapLocation spawn : spawnLocs) {
            if (rc.canSpawn(spawn) && target.distanceSquaredTo(spawn) < minDist) {
                bestSpawn = spawn;
                minDist = target.distanceSquaredTo(bestSpawn);
            }
        }

        if(bestSpawn != null) {
            rc.spawn(bestSpawn);
            return true;
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
        MapLocation TARGET_ENEMY_FLAGTarget = SA.getLocation(SA.TARGET_ENEMY_FLAG);
        //reseting enemy flags if not at SA location
        if(rc.canSenseLocation(TARGET_ENEMY_FLAGTarget) && nearbyFlags.length == 0) {
            rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
        }
        
        //writing enemy flags into shared array
        if(SA.getPrefix(SA.TARGET_ENEMY_FLAG) == 0 || SA.getLocation(SA.TARGET_ENEMY_FLAG).equals(new MapLocation(0,0))) {
            
            if(nearbyFlags.length > 0) {
                FlagInfo info = nearbyFlags[0];
                
                if(info.getTeam().equals(rc.getTeam().opponent())) {
                    RobotInfo robot = rc.senseRobotAtLocation(info.getLocation());

                    //only adds the flag to the shared array if we don't already possess it
                    if(robot == null || robot.getTeam().equals(rc.getTeam().opponent())) {
                        rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, SA.encode(info.getLocation(), 1));
                    }
                }

            } else if(rc.readSharedArray(SA.TARGET_ENEMY_FLAG) == 0 || rc.getRoundNum() % 100 == 0) {
                if(rc.senseBroadcastFlagLocations().length != 0) {
                    MapLocation loc = rc.senseBroadcastFlagLocations()[0];
                    rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, SA.encode(loc, 0));
                }
            }
        }

        //picks up enemy flags
        for(FlagInfo flag : nearbyFlags) {
            MapLocation flagLoc = flag.getLocation();
            if(rc.canSenseLocation(flagLoc) && rc.canPickupFlag(flagLoc)) {
                rc.pickupFlag(flagLoc);
                rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            }
        }
        

        //creating escort for returning flags
        if(rc.hasFlag() && !hasMyFlag()) {
            rc.writeSharedArray(SA.escort, SA.encode(rc.getLocation(), 1));
            if(SA.getLocation(SA.TARGET_ENEMY_FLAG).distanceSquaredTo(rc.getLocation()) <= 2) {
                rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
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
    public static void move() throws GameActionException {

        MapLocation target;
        //this will be where we attempt to move
        if(Utils.isEnemies() && !rc.hasFlag()) {

            if(ID <= 3) {
                //adding defenses if we sense enemy robots
                indicator += "HELP ";
                rc.writeSharedArray(SA.defend, SA.encode(getFlagDefense(), 1) );
                Combat.attack();
            } else {
                Combat.runCombat();
                indicator += "c: " + Combat.target + " " + Combat.indicator;
                return;
            }
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
            rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            rc.writeSharedArray(SA.escort, 0);
        }
        indicator += target + "\n";
    }

    /**
     * tries to generate an unexplored target location within MAX_TRIES. Failing that, a random non-null target
     * location is returned
     */
    public static MapLocation genExploreTarget(final int MAX_TRIES){
        MapLocation res = null;
        MapLocation cur = rc.getLocation();
        for (int i = 0; i < MAX_TRIES; i++){
            // generate random location w/ distance at least 5 away from the robot
            double dist = rng.nextDouble() * 4 + 6; // generate in range [6..9] (once floored), could expand later
            double angle = rng.nextDouble() * 2 * Math.PI; // generate a random angle
            res = new MapLocation( // combine into location to try
                    Utils.clamp((int)(cur.x + Math.cos(angle) * dist), 0, rc.getMapWidth()-1),
                    Utils.clamp((int)(cur.y + Math.sin(angle) * dist), 0, rc.getMapHeight()-1)
            );
            // got a valid unexplored node
            if (MapRecorder.get(res) == null) return res;
        }
        return res; //failed to make one, return random location instead
    }
    private static MapLocation explorationTarget = null;
    private static int lastChangeTurn = 0;
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
        //attempts to return flag to closest spawn location
        if(rc.hasFlag()) {
            target = FlagReturn.getReturnTarget();
            indicator += FlagReturn.indicator;
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

        // Sends robots to defend 
        if(ID <= NUM_ROBOTS_TO_DEFEND && SA.getPrefix(SA.defend) == 1) {
            target = SA.getLocation(SA.defend);
            if(rc.canSenseLocation(target) && rc.senseNearbyFlags(-1, rc.getTeam()).length == 0) {
                rc.writeSharedArray(SA.defend, 0);
            }
            return target;
        }

        //Escorts a robot with a flag 
        if(rc.getLocation().distanceSquaredTo(SA.getLocation(SA.escort)) <= 6           //is near flag carrier
                && SA.getPrefix(SA.escort) <= NUM_ROBOTS_TO_ESCORT                      //not too many already escorting
                && !SA.getLocation(SA.escort).equals(new MapLocation(0,0))) {   //makes sure we have a real target
            target = SA.getLocation(SA.escort);
            rc.writeSharedArray(SA.escort, SA.encode(target, SA.getPrefix(SA.escort) + 1));
            indicator += "Escorting " + SA.getPrefix(SA.escort);
            return target;
        }

        // Target crumbs if nearby
        // This covers the case crumbs are revealed after wall fall
        MapLocation[] crumLocs = rc.senseNearbyCrumbs(-1);
        if(crumLocs.length > 0) return crumLocs[0];

        //Grabs crumbs if we have no other goals in life
        if(rc.getRoundNum() < 200) {
            if(rc.getRoundNum() > 150)  // go to the center
                target =  new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            else { // randomly explore the map
                // if a target change is needed, set it, otherwise use the existing target
                if (Pathfinding.reachedTarget() || Pathfinding.invalidTarget() || rc.getRoundNum()-20 > lastChangeTurn) {
                    // change the target to something else
                    explorationTarget = genExploreTarget(10);
                    lastChangeTurn = rc.getRoundNum();
                }
                if (explorationTarget != null)
                    rc.setIndicatorDot(explorationTarget, 255, 0, 0);
                target = explorationTarget;
            }
            return target;
        } 
        
        //go aggresive and if not aggresive targets exists go middle
        target = SA.getLocation(SA.TARGET_ENEMY_FLAG);
        if(target.equals(new MapLocation(0,0))) {
            target = new MapLocation(rc.getMapWidth()/ 2, rc.getMapHeight() / 2);
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
     * very niche method that handles passive defender building behavior
     * namely : tries to place stun on corners whenever possible
     */
    public static void defenderBuild() throws GameActionException {
        if (ID >= 4) return; // not a defender
        if(rc.getRoundNum() < 200) return; //want to only place defensive traps if we can win center fight
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (rc.getActionCooldownTurns() > 0 || rc.getCrumbs() < 250) return; // can't build: on cool-down / no money
        // active defense - put bombs on direction closest to the nearest enemy (not in setup)
        if (enemies.length > 0 && rc.getRoundNum() > 200) {
            int minDist = Integer.MAX_VALUE; // min for all positions
            MapLocation target = null;
            // try and get the direction where the bomb will be closest to an enemy
            for (Direction dir : Utils.directions){
                // check minimum distance for the choice of direction
                MapLocation pos = rc.getLocation().add(dir);
                if (!rc.canBuild(TrapType.EXPLOSIVE, pos)) continue;
                int md = Integer.MAX_VALUE; // min dist local
                for (RobotInfo enemy : enemies) {
                    md = Math.min(md, enemy.getLocation().distanceSquaredTo(pos));
                }
                // better than current best? => update
                if (md < minDist) {
                    minDist = md;
                    target = pos;
                }
            }
            // if target has been chosen, then build a bomb there
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
     * Attempts to buy global upgrades
     * Buys action then healing then capturing
     */
    public static void globals() throws GameActionException {
        if(rc.canBuyGlobal(GlobalUpgrade.ACTION)) rc.buyGlobal(GlobalUpgrade.ACTION);
        if(rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);
        if(rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) rc.buyGlobal(GlobalUpgrade.CAPTURING);
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
        if (target != null) {
            rc.heal(target);
            indicator += "h: " + target + " ";
        }
    }
}
