package v8;

import battlecode.common.*;
import scala.util.Random;

import java.util.Optional;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    static final int baseSeed = 6147;
    static final Random rng = new Random(baseSeed);
    //number that indicates when the robot move in the turn
    static public int ID = 0;
    static int NUM_ROBOTS_TO_DEFEND = 0;
    static int NUM_ROBOTS_TO_ESCORT = 3;
    static int ENEMIES_PER_TRAP = 3;
    //string used for debugging purposes
    static String indicator;
    static RobotController rc;
    private static MapLocation explorationTarget = null;
    private static int lastChangeTurn = 0;
    private static int myFlag;

    public static void run(RobotController m_rc) throws GameActionException {
        rc = m_rc;

        while (true) {
            if (rc.getRoundNum() == 1) init();
            indicator = ID + ": ";
            Combat.modeLog[rc.getRoundNum()] = Combat.CombatMode.NONE;
            Combat.actionLog[rc.getRoundNum()] = Combat.ActionMode.NONE;
            rc.writeSharedArray(SA.escort, SA.encode(SA.getLocation(SA.escort), 0));
            //tries to spawn in the robot if we can
            if (!rc.isSpawned()) {
                // update SA if it was a flag bearer
                if (myFlag != 0) {
                    int index = SA.findOpponentFlagSpawn(myFlag) + SA.EFSPAWN1;
                    rc.writeSharedArray(index, SA.encode(SA.getLocation(index), 1));
                    myFlag = 0;
                }
                spawn();
            }
            //actions to perform if we are spawned in, or just got spawned in
            if (rc.isSpawned()) {
                globals();
                flagStuff();
                move();
                MapRecorder.updateSurroundings();
                SA.updateMap();
                Combat.attack();
                heal();
                defenderBuild(); // probably a better place to put this :/
                if (rc.getRoundNum() <= 200 || rc.getCrumbs() >= 400) dig();
                if (rc.getRoundNum() >= 230) stunAroundSpawn();
                if (rc.getRoundNum() >= 190) fill();
            }
            if (ID == 1 || ID == 50) {
                Utils.showLoc(SA.BROADCAST1, 255, 0, 0);
                Utils.showLoc(SA.BROADCAST2, 0, 255, 0);
                Utils.showLoc(SA.BROADCAST3, 0, 0, 255);
                Utils.showLoc(SA.EFSPAWN1, 100, 0, 0);
                Utils.showLoc(SA.EFSPAWN2, 0, 100, 0);
                Utils.showLoc(SA.EFSPAWN3, 0, 0, 100);
                Utils.showLoc(SA.defend, 150, 0, 150);
                Utils.showLoc(SA.escort, 150, 150, 0);
                indicator += String.format("[eflag prefix: %d %d %d]", SA.getPrefix(SA.EFSPAWN1), SA.getPrefix(SA.EFSPAWN2), SA.getPrefix(SA.EFSPAWN3));
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
        Combat.init(rc, ID);
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
     * currently prioritizing the spawning the robot onto a flag that needs defense if such a flag exists
     * otherwise it will randomly spawn the robot
     */
    public static boolean spawn() throws GameActionException {
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        indicator += "SPAWN ";
        // round 1 spawning behavior: have bots ID 1,2,3 spawn on their respective flags:
        if (rc.getRoundNum() == 1 && ID <= 3){
            rc.spawn(spawnLocs[(ID-1)*9+4]);
            return true;
        }

        // target prioritization:
        // defend > target > //TODO: decide for the following: [escort, broadcasts]
        MapLocation target;
        if (rc.readSharedArray(SA.defend) != 0) {
            indicator += "DEF";
            target = SA.getLocation(SA.defend);
        } else if (rc.readSharedArray(SA.TARGET_ENEMY_FLAG) != 0) {
            indicator += "ATK-FLG";
            target = SA.getLocation(SA.TARGET_ENEMY_FLAG);
        } else {
            indicator += "RAND";
            target = null;
        }

        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (MapLocation spawn : spawnLocs) {
            if (!rc.canSpawn(spawn)) continue;
            if (target == null) {
                best = spawn;
                break;
            }
            if (spawn.distanceSquaredTo(target) < minDist) {
                minDist = spawn.distanceSquaredTo(target);
                best = spawn;
            }
        }
        if (best != null) {
            rc.spawn(best);
            return true;
        } else {
            return false;
        }
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
        if (nearbyFlags.length == 0) {
            if (rc.canSenseLocation(TARGET_ENEMY_FLAGTarget)) {
                rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            }
            for (int i = 0; i < 3; i++) {
                MapLocation loc = SA.getLocation(SA.BROADCAST1 + i);
                if (rc.canSenseLocation(loc)) {
                    rc.writeSharedArray(SA.BROADCAST1 + i, 0);
                }
            }
        }


        // writing broadcasts
        if (rc.getRoundNum() % 100 == 0) {
            MapLocation[] locs = rc.senseBroadcastFlagLocations();
            for (int i = 0; i < locs.length; i++)
                rc.writeSharedArray(SA.BROADCAST1 + i, SA.encode(locs[i], 1));
            for (int i = locs.length; i < 3; i++)
                rc.writeSharedArray(SA.BROADCAST1 + i, 0);
        }

        //writing enemy flags into shared array
        if (SA.getPrefix(SA.TARGET_ENEMY_FLAG) == 0) {
            if (nearbyFlags.length > 0) {
                FlagInfo info = nearbyFlags[0];
                if (info.getTeam().equals(rc.getTeam().opponent())) {
                    RobotInfo robot = rc.senseRobotAtLocation(info.getLocation());
                    //only adds the flag to the shared array if we don't already possess it
                    if (robot == null || robot.getTeam().equals(rc.getTeam().opponent())) {
                        rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, SA.encode(info.getLocation(), 1));
                    }
                }
            }
        }

        //picks up enemy flags
        for (FlagInfo flag : nearbyFlags) {
            MapLocation flagLoc = flag.getLocation();
            SA.addOpponentFlagSpawn(flag);
            if (rc.canSenseLocation(flagLoc) && rc.canPickupFlag(flagLoc)) {
                rc.pickupFlag(flagLoc);
                myFlag = flag.getID();
                int index = SA.findOpponentFlagSpawn(flag.getID()) + SA.EFSPAWN1;
                rc.writeSharedArray(index, SA.encode(SA.getLocation(index), 3));
                rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            }
        }


        //creating escort for returning flags
        if (hasEnemyFlag()) {
            rc.writeSharedArray(SA.escort, SA.encode(rc.getLocation(), 1));
            if (SA.getLocation(SA.TARGET_ENEMY_FLAG).distanceSquaredTo(rc.getLocation()) <= 2) {
                rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            }
        }

        // having defenders intercept flags in transit:
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());
        if (flags.length > 0 && !rc.senseMapInfo(flags[0].getLocation()).isSpawnZone()) {
            rc.writeSharedArray(SA.defend, SA.encode(flags[0].getLocation(), 1));
        }

        // un-flag unnecessary defense
        if (flags.length == 0 && rc.getLocation().equals(SA.getLocation(SA.defend)) && !Utils.isEnemies()) {
            rc.writeSharedArray(SA.defend, 0);
        }
    }

    /**
     * @return whether the duck carrying the opponents' flag
     */
    private static boolean hasEnemyFlag() throws GameActionException {
        if (!rc.hasFlag()) return false;
        return rc.senseNearbyFlags(0, rc.getTeam().opponent()).length > 0;
    }

    /**
     * Determines where the robot should move on the given turn
     * Also calls a combat method if there are visible enemies
     */
    public static void move() throws GameActionException {
        MapLocation target = null;

        // Only target crumbs if we don't have flag
        MapLocation[] crumLocs = rc.senseNearbyCrumbs(-1);
        if (!rc.hasFlag()) {
            for (MapLocation t : crumLocs) {
                if (!(rc.canSenseLocation(t) && rc.senseMapInfo(t).isWater())) {
                    target = t;
                    break;
                } else {
                    if (rc.canFill(t)) rc.fill(t);
                    target = t;
                    break;
                }
            }
        }

        // this will be where we attempt to move
        if (Utils.isEnemies() && !rc.hasFlag() && rc.getRoundNum() > 150) {
            if (ID <= 3 && rc.senseNearbyFlags(-1, rc.getTeam()).length > 0) {
                //adding defenses if we sense enemy robots
                indicator += "HELP ";
                rc.writeSharedArray(SA.defend, SA.encode(getFlagDefense(), 1));
                Combat.attack();
            } else {
                Combat.runCombat();
                indicator += "c: " + Combat.target + " " + Combat.indicator;
                return;
            }
        }

        indicator += "t: ";
        if (target == null) target = getTarget();

        boolean hasFlag = rc.hasFlag();
        FlagInfo myflag = null;
        if (hasFlag) {
            myflag =  rc.senseNearbyFlags(1, rc.getTeam().opponent())[0];
        }

        boolean flagPassed = false;
        if(target != null) {
            // flag passing logic
            // if there's a duck that's closer to the target than us we should drop flag and pass it to them
            // this should help ducks with flag getting stuck in a cluster
            if (rc.hasFlag()) {
                Pathfinding.initTurn();
                Pathfinding.move(target);
                // TODO: Also drop flag if we're stuck in like water

                // TODO: ducks can drop flags sqrt 2 away and can pick up sqrt 2 away
                // right now just sqrt2 dist
                RobotInfo[] friends = rc.senseNearbyRobots(2, rc.getTeam());
                RobotInfo bestFriend = null;
                int friendDistToFlag = Integer.MAX_VALUE;
                int myDist = rc.getLocation().distanceSquaredTo(target);

                // constants
                // good one are like disallowPassIfInDanger = true
                // minFriendsToPass = 7
                // trying it hyper aggressive
                int friendPassingDistance = 2;
                int friendSafetyDistance = 9;
                int minFriendsToPass = 0;
                boolean disallowPassIfInDanger = false;
                int dangerIfNumEnemies = 3;
                int dangerIfEnemiesWithinDist = 6;

                int numFriends = 0;
                int numEnemies = 0;
                MapLocation openLoc = null;
                for (RobotInfo nearby : rc.senseNearbyRobots(-1)) {
                    if (nearby.getTeam().equals(rc.getTeam())) {
                        int dist = nearby.getLocation().distanceSquaredTo(rc.getLocation());

                        if (dist <= friendSafetyDistance) numFriends++;
                        if (!(dist > friendPassingDistance)) continue;
                        //if (nearby.ID <= ID) continue; // if the bot already moved we dont want to risk dropping it (TODO: mess with this)
                        RobotInfo f = nearby;
                        int distTarget = f.getLocation().distanceSquaredTo(target);
                        Optional<MapLocation> openLocation = findOpenLocationForFlag(f.getLocation());
                        if (openLocation.isPresent() && rc.onTheMap(openLocation.get()) && rc.canDropFlag(openLocation.get()) && distTarget < friendDistToFlag) {
                            friendDistToFlag = distTarget;
                            bestFriend = f;
                            // Drop the flag at the open location
                            // rc.dropFlag(openLocation.get());
                            openLoc = openLocation.get();
                        }
                    } else {
                        RobotInfo e = nearby;
                        if (!(rc.getLocation().distanceSquaredTo(e.getLocation()) > dangerIfEnemiesWithinDist)) continue;
                        numEnemies++;
                    }
                }

                boolean shouldPass = true;
                if (numEnemies >= dangerIfNumEnemies && disallowPassIfInDanger) {shouldPass = false;};
                if (!(minFriendsToPass <= numFriends)) {shouldPass = false;}

                // passing is better
                if (bestFriend != null && openLoc != null && shouldPass && friendDistToFlag < myDist) {
                    //System.out.println("passing!");
                    rc.dropFlag(openLoc);
                }

            }


            // need to check if the target is obstructed first, and if so, fill it
            // but if the target is obstructed, check left and right is also obstructed
            // if left and right also obstructed, then we dig
            // otherwise go to whichever location is unobstructed

            // main issue: how do we get the two "left" and "right" directions??
            // solve: implementing clockwise and counterclockwise functions

            Direction towards = rc.getLocation().directionTo(target);
            Direction cw = towards.rotateRight();
            Direction ccw = towards.rotateLeft();
            MapLocation moveTarget = rc.getLocation().add(towards);

            if (!rc.hasFlag() && Utils.isNearEnemyFlag(25)) {
                if (rc.canFill(moveTarget)) rc.fill(moveTarget);
            } else if (!rc.hasFlag() && rc.onTheMap(moveTarget) && rc.canSenseLocation(moveTarget) && rc.senseMapInfo(moveTarget).isWater()) {
                MapLocation cwTarget = rc.getLocation().add(cw);
                MapLocation ccwTarget = rc.getLocation().add(ccw);
                if (rc.onTheMap(cwTarget) && !rc.senseMapInfo(cwTarget).isWater() && rc.canMove(cw)) {
                    target = cwTarget;
                } else if (rc.onTheMap(ccwTarget) && !rc.senseMapInfo(ccwTarget).isWater() && rc.canMove(ccw)) {
                    target = ccwTarget;
                } else {
                    if (rc.canFill(moveTarget)) rc.fill(moveTarget);
                }
            }

            Pathfinding.initTurn();
            Pathfinding.move(target);
        }

        // updating shared array that a flag was dropped off during
        // this robots movement
        if(rc.hasFlag() != hasFlag && !flagPassed) {
            int capInd = SA.findOpponentFlagSpawn(myflag.getID());
            MapLocation loc = SA.getLocation(SA.EFSPAWN1+capInd);
            RobotPlayer.myFlag = 0;
            rc.writeSharedArray(SA.TARGET_ENEMY_FLAG, 0);
            rc.writeSharedArray(SA.escort, 0);
            rc.writeSharedArray(SA.EFSPAWN1 + capInd, SA.encode(loc, 2)); // mark flag as captured
        }
        indicator += target + "\n";
    }

    /**
     * tries to generate an unexplored target location within MAX_TRIES. Failing that, a random non-null target
     * location is returned
     */
    public static MapLocation genExploreTarget(final int MAX_TRIES) {
        MapLocation res = null;
        MapLocation cur = rc.getLocation();
        for (int i = 0; i < MAX_TRIES; i++) {
            // generate random location w/ distance at least 5 away from the robot
            double dist = rng.nextDouble() * 4 + 6; // generate in range [6..9] (once floored), could expand later
            double angle = rng.nextDouble() * 2 * Math.PI; // generate a random angle
            res = new MapLocation( // combine into location to try
                    Utils.clamp((int) (cur.x + Math.cos(angle) * dist), 0, rc.getMapWidth() - 1),
                    Utils.clamp((int) (cur.y + Math.sin(angle) * dist), 0, rc.getMapHeight() - 1)
            );
            // got a valid unexplored node
            if (MapRecorder.get(res) == null) return res;
        }
        return res; //failed to make one, return random location instead
    }

    /**
     * Chooses a movement target in this priority:
     * -has flag: goes to closest spawn
     * -defense: defends corresponding flag
     * -escort: escorts corresponding flag
     * -scouting: finds crumbs (only rounds < 150)
     * -attacking: finds enemy flags
     */
    private static MapLocation getTarget() throws GameActionException {
        MapLocation target;
        //attempts to return flag to the closest spawn location
        if (rc.hasFlag()) {
            target = FlagReturn.getReturnTarget();
            indicator += FlagReturn.indicator;
            return target;
        }

        //controls defense
        if (ID <= 3) {
            target = getFlagDefense();

            //resets defense location if there are no enemies
            if (SA.getLocation(SA.defend).equals(target) && !Utils.isEnemies() && rc.canSenseLocation(target)) {
                rc.writeSharedArray(SA.defend, 0);
            }

            return target;
        }

        // Sends robots to defend 
        if (ID <= NUM_ROBOTS_TO_DEFEND && SA.getPrefix(SA.defend) == 1) {
            target = SA.getLocation(SA.defend);
            if (rc.canSenseLocation(target) && rc.senseNearbyFlags(-1, rc.getTeam()).length == 0) {
                rc.writeSharedArray(SA.defend, 0);
            }
            return target;
        }

        //Escorts a robot with a flag 
        if (rc.canSenseLocation(SA.getLocation(SA.escort))           //is near flag carrier
                && SA.getPrefix(SA.escort) <= NUM_ROBOTS_TO_ESCORT                      //not too many already escorting
                && !SA.getLocation(SA.escort).equals(new MapLocation(0, 0))) {   //makes sure we have a real target
            target = FlagReturn.getEscortDirection();
            int encode = SA.encode(target, SA.getPrefix(SA.escort) + 1);
            if (encode <= 0) {
                System.out.println("AUAUAAUA");
            } else {
                rc.writeSharedArray(SA.escort, encode);
            }

            indicator += "Escorting " + SA.getPrefix(SA.escort);
            return target;
        }

        // random exploration / middle positioning during setup
        if (rc.getRoundNum() < 200) {
            if (rc.getRoundNum() > 150)  // go to the center
                target = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            else { // randomly explore the map
                // if a target change is needed (there, unreachable, stuck), set it, otherwise use the existing target
                if (Pathfinding.reachedTarget() || Pathfinding.invalidTarget() || rc.getRoundNum() - 20 > lastChangeTurn) {
                    explorationTarget = genExploreTarget(10);  // change the target to something else
                    lastChangeTurn = rc.getRoundNum();
                }
                target = explorationTarget;
            }
            return target;
        }
        // no specific assigned role, find a target
        target = getGeneralTarget();
        rc.setIndicatorLine(rc.getLocation(), target, 255, 255, 255);
        return target;
    }

    /**
     * General target choosing function. priority as follows:
     * <ol>
     *     <li>specific flag target</li>
     *     <li>known flag spawn locations</li>
     *     <li>flag broadcast locations</li>
     *     <li>own flag defense</li>
     *     <li>escorting returning flags</li>
     *     <li>middle of map</li>
     * </ol>
     */
    private static MapLocation getGeneralTarget() throws GameActionException{
        MapLocation loc = rc.getLocation();
        if (rc.readSharedArray(SA.TARGET_ENEMY_FLAG) != 0) {
            MapLocation target = SA.getLocation(SA.TARGET_ENEMY_FLAG);
            if (loc.isWithinDistanceSquared(target, 225)) {
                indicator += "TARGET";
                return target;
            }
        }

        MapLocation known = getBestEFlagSpawn(loc);
        if (known != null) {
            indicator += "KNOWN";
            return known;
        }
        MapLocation broad = getBestBroadcast(loc);
        if (broad != null) {
            indicator += "BROADCAST";
            return broad;
        }

        MapLocation defense = SA.getLocation(SA.defend);
        if (rc.readSharedArray(SA.defend) != 0) {
            indicator += "DEFENSE";
            return defense;
        }

        MapLocation escort = SA.getLocation(SA.escort);
        if (rc.readSharedArray(SA.escort) != 0){
            indicator += "ESCORT";
            return escort;
        }

        return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
    }


    // get best broadcast location
    private static MapLocation getBestBroadcast(MapLocation cur) throws GameActionException{
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = SA.BROADCAST1; i <= SA.BROADCAST3; i++){
            if (rc.readSharedArray(i) == 0) continue;
            MapLocation broad = SA.getLocation(i);
            if (cur.distanceSquaredTo(broad) < minDist) {
                minDist = cur.distanceSquaredTo(broad);
                best = broad;
            }
        }
        return best;
    }

    // get best known and non-captured enemy flag spawn
    private static MapLocation getBestEFlagSpawn(MapLocation cur) throws GameActionException{
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = SA.EFSPAWN1; i <= SA.EFSPAWN3; i++){
            if (SA.getPrefix(i) != 1) continue; // 0 : not there, 2 : captured
            MapLocation broad = SA.getLocation(i);
            if (cur.distanceSquaredTo(broad) < minDist) {
                minDist = cur.distanceSquaredTo(broad);
                best = broad;
            }
        }
        return best;
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
        if (rc.getRoundNum() <= 200) return; //want to only place defensive traps if we can win center fight
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (rc.getActionCooldownTurns() > 0 || rc.getCrumbs() < 250) return; // can't build: on cool-down / no money
        // active defense - put bombs on direction closest to the nearest enemy (not in setup)
        if (enemies.length > 0 && rc.getRoundNum() > 200) {
            int minDist = Integer.MAX_VALUE; // min for all positions
            MapLocation target = null;
            // try and get the direction where the bomb will be closest to an enemy
            for (Direction dir : Utils.directions) {
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
            MapLocation[] locs = {rc.getLocation().add(Direction.NORTHEAST), rc.getLocation().add(Direction.SOUTHWEST)};
            for (MapLocation pos : locs) {
                MapInfo pinfo = rc.senseMapInfo(pos);
                if (pinfo.getTrapType() == TrapType.NONE && rc.canBuild(TrapType.STUN, pos)) {
                    rc.build(TrapType.STUN, pos);
                    return;
                }
            }

            MapLocation[] loc = {rc.getLocation().add(Direction.NORTHWEST), rc.getLocation().add(Direction.SOUTHEAST)};
            for (MapLocation pos : loc) {
                MapInfo pinfo = rc.senseMapInfo(pos);
                if (pinfo.getTrapType() == TrapType.NONE && rc.canBuild(TrapType.WATER, pos)) {
                    rc.build(TrapType.WATER, pos);
                    return;
                }
            }

        }
    }

    public static void stunAroundSpawn() throws GameActionException {
        if (Utils.isNearOurFlag(9)) {
            MapLocation target = rc.getLocation().add(Direction.NORTH);
            if (target.x % 2 != target.y % 2 && rc.onTheMap(target)) {
                if (rc.canBuild(TrapType.STUN, target)) {
                    // check adjacent
                    boolean adjacentToTrap = false;
                    /*for (Direction d: new Direction[]{Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.SOUTHWEST}) {
                        MapLocation trapLoc = target.add(d);
                        if (Utils.isValidMapLocation(trapLoc) && rc.canSenseLocation(trapLoc) && rc.senseMapInfo(trapLoc).getTrapType() == TrapType.STUN) {
                            adjacentToTrap = true;
                        }
                    }*/

                    if (!adjacentToTrap) rc.build(TrapType.STUN, target);
                }
            }
        }
    }

    /**
     * Attempts to buy global upgrades
     * Buys action then healing then capturing
     */
    public static void globals() throws GameActionException {
        if (rc.canBuyGlobal(GlobalUpgrade.ACTION)) rc.buyGlobal(GlobalUpgrade.ACTION);
        if (rc.canBuyGlobal(GlobalUpgrade.HEALING)) rc.buyGlobal(GlobalUpgrade.HEALING);
        if (rc.canBuyGlobal(GlobalUpgrade.CAPTURING)) rc.buyGlobal(GlobalUpgrade.CAPTURING);
    }

    /**
     * Fills in a random tiles if we have extra action and see no enemies
     *
     * @throws GameActionException
     */
    public static void fill() throws GameActionException {
        MapLocation targ = getTarget();
        if (targ == null) return;
        Direction towards = rc.getLocation().directionTo(targ);
        Direction cw = towards.rotateRight();
        Direction ccw = towards.rotateLeft();

        MapLocation moveTarget = rc.getLocation().add(towards);

        if (rc.senseMapInfo(moveTarget).isWater()) {
            MapLocation cwTarget = rc.getLocation().add(cw);
            MapLocation ccwTarget = rc.getLocation().add(ccw);
            if (rc.onTheMap(cwTarget) && !rc.senseMapInfo(cwTarget).isWater()) {
                moveTarget = cwTarget;
            } else if (rc.onTheMap(ccwTarget) && !rc.senseMapInfo(ccwTarget).isWater()) {
                moveTarget = ccwTarget;
            }

            if (rc.canFill(moveTarget)) rc.fill(moveTarget);
        }
    }

    public static void dig() throws GameActionException {
        if (Utils.isNearOurFlag(16)) {
            MapLocation target = rc.getLocation().add(Direction.NORTH);
            if (target.x % 2 == target.y % 2 && rc.onTheMap(target)) {
                if (rc.canDig(target)) {
                    if (rc.canSenseLocation(target) && rc.senseMapInfo(target).getCrumbs() == 0) rc.dig(target);
                }
            }
        }
    }

    public static void heal() throws GameActionException {
        //heals any friendly robots it can, again prioritize flag bearers and low HP units
        if (rc.getActionCooldownTurns() > 0) return; // on cooldown
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation target = null;
        int minHealth = 1001;

        // in an attempt to make not everyone end up specialized as healers
        // lets assign 15 healers ID [20,35] with a higher change to heal
        // todo: investigate this more
        boolean shouldHeal = true;

        // check if enemy in face
        RobotInfo[] enemiesNearby = rc.senseNearbyRobots(4, rc.getTeam().opponent());
        if (enemiesNearby.length > 0) {
            minHealth = 250;
            if (rng.nextDouble() > 0.5) shouldHeal = true;
        } else {
            shouldHeal = true;
        }

        /*if (ID >= 4 && ID <= 30) {
            shouldHeal = true;
        } else {
            // lets assign a chance for this to heal
            // these are attackers only heal if we're in "danger"
            // save our cooldown for more attacks
            minHealth = 750;
                shouldHeal = true;
        }*/

        if (!shouldHeal) return;

        for (RobotInfo robot : friendlyRobots) {
            if (rc.canHeal(robot.getLocation())) {
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

    private static Optional<MapLocation> findOpenLocationForFlag(MapLocation robotLocation) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation checkLocation = new MapLocation(robotLocation.x + dx, robotLocation.y + dy);
                if (rc.onTheMap(checkLocation) && rc.canSenseLocation(checkLocation) && rc.canDropFlag(checkLocation)) {
                    return Optional.of(checkLocation);
                }
            }
        }
        return Optional.empty();
    }
}
