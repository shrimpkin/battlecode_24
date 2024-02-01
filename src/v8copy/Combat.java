package v8copy;

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
    static int ID;

    static int numEnemiesAttackingUs;
    static int numFriendliesHealingUs;
    static int numFriendlies;
    static int numEnemies;
    static int numTraps;
    static int numNearTraps;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static MapLocation averageTrap;
    static MapLocation averageNearTrap;

    //these are constants used for controlling
    //the attack and defense directions
    enum CombatMode {OFF, DEF, FLAG_DEF, FLAG_OFF, NONE}

    enum ActionMode {HEAL, ATT, NONE}

    static CombatMode[] modeLog;
    static MapLocation[] locations;
    static ActionMode[] actionLog;

    static MapLocation target;

    public static void init(RobotController r, int I) throws GameActionException {
        rc = r;
        indicator = "";
        ID = I;
        modeLog = new CombatMode[2001];
        modeLog[0] = CombatMode.NONE;
        locations = new MapLocation[2001];
        actionLog = new ActionMode[2001];
        actionLog[0] = ActionMode.NONE;
    }

    public static void reset() throws GameActionException {
        locations[rc.getRoundNum()] = rc.getLocation();
        indicator = "";

        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        friendlies = rc.senseNearbyRobots(-1, rc.getTeam());

        numEnemies = enemies.length;
        numFriendlies = friendlies.length;

        numEnemiesAttackingUs = rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED,rc.getTeam().opponent()).length;
        numFriendliesHealingUs = rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED,rc.getTeam()).length;

        MapInfo[] mapInfo = rc.senseNearbyMapInfos();

        double averageTrap_x = 0;
        double averageTrap_y = 0;

        double averageTrapNear_x = 0;
        double averageTrapNear_y = 0;

        numTraps = 0;
        numNearTraps = 0;

        for (MapInfo info : mapInfo) {
            if (!info.getTrapType().equals(TrapType.NONE)) {
                averageTrap_x += info.getMapLocation().x;
                averageTrap_y += info.getMapLocation().y;
                numTraps++;

                if(info.getMapLocation().isWithinDistanceSquared(rc.getLocation(), 4)) {
                    averageTrapNear_x += info.getMapLocation().x;
                    averageTrapNear_y += info.getMapLocation().y;
                    numNearTraps++;
                }
            }
        }

        if (numTraps == 0) {
            averageTrap = null;
        } else {
            averageTrap_x /= numTraps;
            averageTrap_y /= numTraps;
            averageTrap = new MapLocation((int) averageTrap_x, (int) averageTrap_y);
        }

        if(numNearTraps == 0) {
            averageNearTrap = null;
        } else {
            averageTrapNear_x /= numNearTraps;
            averageTrapNear_y /= numNearTraps;
            averageNearTrap = new MapLocation((int) averageTrapNear_x, (int) averageTrapNear_y);
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
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        return numEnemiesAttackingUs > 0
            || (numFriendlies < numEnemies) 
            || (rc.getHealth() < 600);
    }

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
        return Micro.getOffensiveDirection();
    }

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
        if (numShooting * 150 > rc.getHealth()) return false;

        return true;
    }

    /**
     * Attacks the enemy with the flag
     * Then the enemy with the lowest health
     */
    public static void attack() throws GameActionException {
        //attacks any enemy robots it can
        int minHealth = Integer.MAX_VALUE;
        MapLocation target = null;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo robot : enemyRobots) {
            //attacking flag carrier if possible
            if (robot.hasFlag() && rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
                indicator += "a: " + target + " ";
                return;
            }

            if (rc.canAttack(robot.getLocation()) && robot.getHealth() < minHealth) {
                minHealth = robot.getHealth();
                target = robot.getLocation();
            }

        }

        if (target != null && rc.canAttack(target)) {
            rc.attack(target);
            indicator += "a: " + target + " ";
        }
    }

    /**
     * magic constants where they come from I do not know
     * where they go who can say
     */
    public static boolean shouldBuild() {
        boolean output = enemies.length >= 3
                && rc.getRoundNum() > 190;

        if(195 <= rc.getRoundNum() && rc.getRoundNum() <= 205) {
            return true;
        }
                
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
            //if the enemy is closer to the flag then we are then defend it
            if (enemy.getLocation().isWithinDistanceSquared(flagLocation, rc.getLocation().distanceSquaredTo(flagLocation))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a MapLocation to build on that is best
     */
    public static MapLocation buildTarget(TrapType trap) {
        MapLocation bestLocationSoFar = rc.getLocation();
        int minDistance = Integer.MAX_VALUE;
        for (Direction dir : Utils.directions) {
            MapLocation target = rc.getLocation().add(dir);
            if (target.distanceSquaredTo(averageEnemy) < minDistance && rc.canBuild(trap, target)) {
                minDistance = target.distanceSquaredTo(averageEnemy);
                bestLocationSoFar = target;
            }
        }

        return bestLocationSoFar;
    }

    public static void build() throws GameActionException {
        MapLocation buildTarget = buildTarget(TrapType.STUN);
        boolean canBuildTrap = rc.canBuild(TrapType.STUN, buildTarget);

        boolean adjacentToTrap = false;
        for (Direction d: new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            MapLocation adjacent = buildTarget.add(d);
            if (rc.canSenseLocation(adjacent)) {
                MapInfo spot = rc.senseMapInfo(adjacent);
                if (spot.getTrapType() == TrapType.STUN) {
                    adjacentToTrap = true;
                    break;
                }
            }
        }

        if(canBuildTrap && !adjacentToTrap) {
            rc.build(TrapType.STUN, buildTarget);
        }    
    }

    /**
     * Choosing movement target and attacking
     */
    public static void runCombat() throws GameActionException {
        Combat.reset();
        Micro.initTurn(rc);
        CombatMode mode = CombatMode.OFF;

        if (shouldDefendFlag()) mode = CombatMode.FLAG_DEF;
        else if (shouldGrabFlag()) mode = CombatMode.FLAG_OFF;
        else if (shouldRunAway()) mode = CombatMode.DEF;

        Direction dir = Direction.CENTER;

        switch (mode) {
            case FLAG_OFF: dir = Combat.getFlagOffensiveDirection(); break;
            case FLAG_DEF: dir = Combat.getFlagProtectionDirection(); break;
            case DEF: dir = Micro.getDefensiveDirection(); break;
            case OFF: dir = Micro.getOffensiveDirection(); break;
            case NONE: break;
        }

        modeLog[rc.getRoundNum()] = mode;

        target = rc.getLocation().add(dir);
        if(rc.canFill(target)) {
            // System.out.println("FILLING");
            rc.fill(target);
        }
        
        if (mode.equals(CombatMode.FLAG_OFF) || mode.equals(CombatMode.FLAG_DEF) || Micro.shouldAttackFirst) {
            Combat.attack();
            if (rc.canMove(dir)) rc.move(dir);
        } else {
            if (rc.canMove(dir)) rc.move(dir);
            Combat.attack();
        }

        indicator += "mode: " + mode + " ";
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
