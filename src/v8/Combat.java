package v8;

import battlecode.common.Direction;
import battlecode.common.FlagInfo;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;

public class Combat {
    static RobotController rc;
    boolean shouldRunAway;

    static int numEnemiesAttackingUs;
    static int numFriendlies;
    static int numEnemies;
    static int numTraps;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static MapLocation averageTrap;

    static int OUTNUMBER = 2;

    enum CombatMode {OFF, DEF, TRAP, FLAG_DEF, FLAG_OFF, NONE}

    ;

    enum ActionMode {HEAL, ATT, NONE}

    ;
    static CombatMode[] modeLog;
    static MapLocation[] locations;
    static ActionMode[] actionLog;

    static MapLocation target;

    public static void init(RobotController r) throws GameActionException {
        rc = r;
        indicator = "";

        modeLog = new CombatMode[2001];
        modeLog[0] = CombatMode.NONE;
        locations = new MapLocation[2001];
        actionLog = new ActionMode[2001];
        actionLog[0] = ActionMode.NONE;
    }

    /**
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        return numEnemiesAttackingUs > 0 || (numFriendlies + 1 < numEnemiesAttackingUs) || rc.getHealth() < 600;
    }

    /**
     * Should the robot attempt to make the enemies walk into the traps
     */
    public static boolean shouldTrap() throws GameActionException {
        return averageTrap != null                                          //make sure there is trap
                && enemies.length >= 3                                      //make sure there is enough enemies 
                && !(friendlies.length >= enemies.length * OUTNUMBER)      //make sure we don't already outnumber by a lot
                && numTraps <= 2;
    }

    public static void reset() throws GameActionException {
        indicator = "";
        resetShouldRunAway();
        resetShouldTrap();
    }

    /**
     * resets all constants used in the decision of whether to run away
     * <p> [bytecode usage: 252 to 328] </p>
     */
    public static void resetShouldRunAway() throws GameActionException {
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        friendlies = rc.senseNearbyRobots(-1, rc.getTeam());

        numEnemies = enemies.length;
        numFriendlies = friendlies.length;

        if (numEnemies < 6) { // a bit of micro-opt for sparse maps,
            numEnemiesAttackingUs = 0;
            for (RobotInfo enemy : enemies) {
                //this checks if an enemy could attack the current robot
                if (rc.getLocation().isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                    numEnemiesAttackingUs++;
                }
            }
        } else
            numEnemiesAttackingUs = rc.senseNearbyRobots(
                    GameConstants.ATTACK_RADIUS_SQUARED,
                    rc.getTeam().opponent()
            ).length;
    }


    /**
     * Updates average trap and enemy locations
     */
    public static void resetShouldTrap() throws GameActionException {
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();

        double averageTrap_x = 0;
        double averageTrap_y = 0;

        numTraps = 0;

        for (MapInfo info : mapInfo) {
            if (!info.getTrapType().equals(TrapType.NONE)) {
                averageTrap_x += info.getMapLocation().x;
                averageTrap_y += info.getMapLocation().y;
                numTraps++;
            }
        }

        if (numTraps == 0) {
            averageTrap = null;
        } else {
            averageTrap_x /= numTraps;
            averageTrap_y /= numTraps;
            averageTrap = new MapLocation((int) averageTrap_x, (int) averageTrap_y);
        }


        double averageEnemy_x = 0;
        double averageEnemy_y = 0;

        for (RobotInfo robot : enemies) {
            averageEnemy_x += robot.getLocation().x;
            averageEnemy_y += robot.getLocation().y;
        }

        averageEnemy_x /= enemies.length;
        averageEnemy_y /= enemies.length;

        averageEnemy = new MapLocation((int) averageEnemy_x, (int) averageEnemy_y);
    }

    /**
     * Gets the direction that result in the robot being behind traps
     */
    public static Direction getTrapDirection() throws GameActionException {
        return averageEnemy.directionTo(averageTrap);
    }

    /**
     * Gets the direction that has the least potential attacking enemies
     */
    public static Direction getDefensiveDirection() throws GameActionException {
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int bestEnemiesSeen = Integer.MAX_VALUE;

        for (Direction dir : dirsToConsider) {
            if (rc.canMove(dir) || dir.equals(Direction.CENTER)) {
                MapLocation targetLocation = rc.getLocation().add(dir);
                int potentialEnemies = 0;
                for (RobotInfo enemy : enemies) {
                    //this checks if an enemy could attack the current robot
                    if (targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        potentialEnemies++;
                    }
                }

                if (potentialEnemies < bestEnemiesSeen) {
                    bestDirectionSoFar = dir;
                    bestEnemiesSeen = potentialEnemies;
                }
            }
        }

        return bestDirectionSoFar;
    }

    /**
     * returns the direction that allows for hitting the lowest health enemy
     */
    public static Direction getOffensiveDirection() throws GameActionException {
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int minEnemies = Integer.MAX_VALUE;

        for (Direction dir : dirsToConsider) {
            if (rc.canMove(dir) || dir.equals(Direction.CENTER)) {
                int numEnemies = 0;
                MapLocation targetLocation = rc.getLocation().add(dir);

                for (RobotInfo enemy : enemies) {
                    if (targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        numEnemies++;
                    }
                }

                if (numEnemies > 0 && numEnemies < minEnemies) {
                    bestDirectionSoFar = dir;
                    minEnemies = numEnemies;
                }
            }
        }

        // If we haven't found a direction lets just pick one that's valid.
        if (bestDirectionSoFar == Direction.CENTER) {
            for (RobotInfo x : enemies) {
                Direction d = rc.getLocation().directionTo(x.getLocation());
                if (rc.canMove(d)) {
                    bestDirectionSoFar = d;
                    break;
                }
            }
        }

        return bestDirectionSoFar;
    }

    /**
     * Makes the
     *
     * @return
     * @throws GameActionException
     */
    public static Direction getFlagProtectionDirection() throws GameActionException {
        //first check to see if the enemies have already grabbed our flag
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());

        for (FlagInfo flag : flags) {
            MapLocation flagLocation = flag.getLocation();
            RobotInfo robot = rc.senseRobotAtLocation(flagLocation);
            if (robot != null && robot.getTeam().equals(rc.getTeam().opponent())) {
                return rc.getLocation().directionTo(flagLocation);
            }
        }
        return getOffensiveDirection();

    }

    /**
     * @return
     * @throws GameActionException
     */
    public static Direction getFlagOffensiveDirection() throws GameActionException {
        return rc.getLocation().directionTo(getFlagTarget().getLocation());
    }

    public static FlagInfo getFlagTarget() throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam().opponent());

        for (FlagInfo flag : flags) {
            if (rc.getLocation().isWithinDistanceSquared(flag.getLocation(), 4)) {
                return flag;
            }
        }

        return null;
    }

    /**
     * @return
     * @throws GameActionException
     */
    public static boolean shouldGrabFlag() throws GameActionException {
        FlagInfo flagTarget = getFlagTarget();
        if (flagTarget == null) return false;

        MapLocation flagLocation = flagTarget.getLocation();
        Direction dir = rc.getLocation().directionTo(flagLocation);
        if (!rc.canMove(dir)) return false;

        MapLocation target = flagLocation.add(dir);
        int numShooting = 0;
        for (RobotInfo enemy : enemies) {
            if (enemy.getLocation().distanceSquaredTo(target) <= GameConstants.ATTACK_RADIUS_SQUARED) {
                numShooting++;
            }
        }
        if (numShooting > 2) return false;

        return true;
    }

    /**
     * Attacks the enemy with the flag
     * Then the enemy with the lowest health
     */
    public static void attack() throws GameActionException {

        //attacks any enemy robots it can
        int minHealth = 1001;
        MapLocation target = null;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo robot : enemyRobots) {
            if (rc.canAttack(robot.getLocation())
                    && rc.senseRobotAtLocation(robot.getLocation()).getHealth() < minHealth) {


                /**
                 * target flag holders over everything else
                 */
                if (robot.hasFlag()) {
                    rc.attack(robot.getLocation());
                    return;
                }

                minHealth = rc.senseRobotAtLocation(robot.getLocation()).getHealth();
                target = robot.getLocation();
            }

        }

        if (target != null && rc.canAttack(target)) {
            rc.attack(target);
            indicator += "a: " + target + " ";
        }
    }

    /**
     *
     */
    public static boolean shouldBuild() throws GameActionException {
        boolean output = enemies.length >= 3
                && ((friendlies.length < enemies.length * OUTNUMBER && rc.getRoundNum() >= 200) || (rc.getRoundNum() > 190 && rc.getRoundNum() < 200))
                && numTraps <= 2
                && averageEnemy != null;

        if (output) indicator += "BUILD ";
        return output;
    }

    /**
     * checks there is a flag to defend
     * and that enemies are near it
     */
    public static boolean shouldDefendFlag() throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(-1, rc.getTeam());
        if (flags.length == 0) return false;

        MapLocation flagLocation = flags[0].getLocation();

        for (RobotInfo enemy : enemies) {
            if (enemy.getLocation().isWithinDistanceSquared(flagLocation, 4)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a MapLocation to build on that is best
     *
     * @return
     */
    public static MapLocation buildTarget() throws GameActionException {
        MapLocation bestLocationSoFar = rc.getLocation();
        int minDistance = Integer.MAX_VALUE;
        for (Direction dir : Utils.directions) {
            MapLocation target = rc.getLocation().add(dir);
            if (target.distanceSquaredTo(averageEnemy) < minDistance && rc.canBuild(TrapType.EXPLOSIVE, target)) {
                minDistance = target.distanceSquaredTo(averageEnemy);
                bestLocationSoFar = target;
            }
        }

        return bestLocationSoFar;
    }

    public static void build() throws GameActionException {
        TrapType best = TrapType.NONE;
        if(numFriendlies >= 8) { //tune magic number
            best = TrapType.STUN;
        } else {
            best = TrapType.EXPLOSIVE;
        }

        MapLocation buildTarget = buildTarget();
        if (rc.canBuild(best, buildTarget)) rc.build(best, buildTarget);
    }

    /**
     * Choosing movement target and attacking
     */
    public static void runCombat() throws GameActionException {
        Combat.reset();
        CombatMode mode = CombatMode.OFF;

        if (shouldDefendFlag()) mode = CombatMode.FLAG_DEF;
        else if (shouldGrabFlag()) mode = CombatMode.FLAG_OFF;
        else if (shouldTrap()) mode = CombatMode.TRAP;
        else if (shouldRunAway()) mode = CombatMode.DEF;

        Direction dir = Direction.CENTER;

        switch (mode) {
            case FLAG_OFF:
                dir = Combat.getFlagOffensiveDirection();
                break;
            case FLAG_DEF:
                dir = Combat.getFlagProtectionDirection();
                break;
            case TRAP:
                dir = Combat.getTrapDirection();
                break;
            case DEF:
                dir = Combat.getDefensiveDirection();
                break;
            case OFF:
                dir = Combat.getOffensiveDirection();
                break;
            case NONE:
                break;
        }

        modeLog[rc.getRoundNum()] = mode;

        if (mode.equals(CombatMode.TRAP) || mode.equals(CombatMode.DEF)) {
            Combat.attack();
            if (rc.canMove(dir)) rc.move(dir);
        } else {
            if (rc.canMove(dir)) rc.move(dir);
            Combat.attack();
        }

        updateIndicator();
        target = rc.getLocation().add(dir);
        if (shouldBuild()) build();
    }

    /**
     * Adds in all relevant indicator stuff to the string
     */
    public static void updateIndicator() {
        for (int i = rc.getRoundNum(); i > 0; i--) {
            if (rc.getRoundNum() - i > 2) break;

            indicator += "(" + modeLog[i] + "," + actionLog[i] + ") ";
        }
    }
}
