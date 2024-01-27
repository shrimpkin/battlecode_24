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
    boolean shouldRunAway;

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

    static int OUTNUMBER = 2;
    static int IS_STUCK_TURNS = 10;

    //these are constants used for controlling
    //the attack and defense directions
    //TODO: mess around with these 
    //the more positive this is the more the duck will attempt to be near friends
    static int NEAR_FRIEND_BONUS = 20;
    // the more negative this is the more the duck will attempt to avoid enemies
    static int NEAR_ENEMY_BONUS = -80;
    // the more positive this is the more the duck will attempt to kill an enemy
    static int KILL_ENEMY_BONUS = 1000;
    // the more positivie this is the more the duck will attempt to damage an enemy
    static int DAMAGE_ENEMY_BONUS = 200;
    // the more negative this is the more the duck will attempt to go towards the enemies
    static int APPROACH_ENEMY_BONUS = -50; 
    // avoid blocking
    static int BLOCKING_BONUS = -50;
    // the more negative this is the more the duck will attempt to not fill in water
    static int WATER_BONUS = -50;

    enum CombatMode {OFF, DEF, FLAG_DEF, FLAG_OFF, NONE};

    enum ActionMode {HEAL, ATT, NONE};

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
     * @return true if the robot has been in combat for the last three rounds
     *          and has not done anything during those three rounds
     */
    public static boolean isUseless() throws GameActionException {
        for(int i = 1; i <= IS_STUCK_TURNS; i++) {
            int index = rc.getRoundNum() - i;

            if(index < 0) return false;
            if(locations[index] == null) return false;
            if(!locations[index].equals(rc.getLocation())) return false;
            if(!actionLog[index].equals(ActionMode.NONE)) return false;
        }

        return true;
    }

    /**
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        return numEnemiesAttackingUs  > 0 
            || (numFriendlies < numEnemies) 
            || (rc.getHealth() < 800 && numFriendliesHealingUs > 0);
    }

    /**
     * Gets the direction that has the least potential attacking enemies
     */
    public static Direction getDefensiveDirection() throws GameActionException {
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int bestScore = -60000;

        for (Direction dir : dirsToConsider) {
            if (rc.canMove(dir) || dir.equals(Direction.CENTER)) {
                MapLocation targetLocation = rc.getLocation().add(dir);
                int currentScore = 0;
                for (RobotInfo enemy : enemies) {
                    //this checks if an enemy could attack the current robot
                    if (targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        currentScore += NEAR_ENEMY_BONUS; 
                    }
                }

                for(RobotInfo friend : friendlies) {
                    if (targetLocation.isWithinDistanceSquared(friend.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        currentScore += NEAR_FRIEND_BONUS; 
                    }
                }

                if (currentScore > bestScore) {
                    bestDirectionSoFar = dir;
                    bestScore = currentScore;
                } 
            }
        }
        indicator += "score: " + bestScore + " ";
        return bestDirectionSoFar;
    }

    /**
     * returns the direction that allows for hitting the lowest health enemy
     */
    public static Direction getOffensiveDirection() throws GameActionException {
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int bestScore = Integer.MIN_VALUE;
        boolean canKill;
        boolean canDamage;
        for (Direction dir : dirsToConsider) {
            MapLocation targetLocation = rc.getLocation().add(dir);
            boolean isWater = rc.canSenseLocation(targetLocation) && rc.senseMapInfo(targetLocation).isWater();
            if (rc.canMove(dir) || dir.equals(Direction.CENTER) || (isWater && rc.canFill(targetLocation))) {

                int numEnemies = 0;
                canKill = false;
                canDamage = false;
                int frie = 0;

                for (RobotInfo enemy : enemies) {
                    if (targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        numEnemies++;
                        canDamage = true;
                        if(enemy.getHealth() < rc.getAttackDamage()) {
                            canKill = true;
                        }
                    }
                }

                for(RobotInfo friendly : friendlies) {
                    if(targetLocation.isWithinDistanceSquared(friendly.getLocation(), 1)) {
                        frie++;
                    }
                }

                int currentScore = NEAR_ENEMY_BONUS * numEnemies; 
                if(canKill && rc.isActionReady() && !isWater) currentScore += KILL_ENEMY_BONUS;
                if(canDamage && rc.isActionReady() && !isWater) currentScore += DAMAGE_ENEMY_BONUS;

                currentScore += APPROACH_ENEMY_BONUS * targetLocation.distanceSquaredTo(averageEnemy);
                currentScore += frie * BLOCKING_BONUS;
                if(isWater) currentScore += WATER_BONUS;

                if(currentScore > bestScore) {
                    bestDirectionSoFar = dir;
                    bestScore = currentScore;
                }
            }
        }
        indicator += "score: " + bestScore + " ";

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
        int minHealth = Integer.MAX_VALUE;
        MapLocation target = null;
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo robot : enemyRobots) {
            //attacking flag carier if possible
            if (robot.hasFlag() && rc.canAttack(robot.getLocation())) {
                rc.attack(robot.getLocation());
                indicator += "a: " + target + " ";
                return;
            }

            if (rc.canAttack(robot.getLocation())
                    && rc.senseRobotAtLocation(robot.getLocation()).getHealth() < minHealth) {
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
     * magic constants where the come from i do not know
     * where they go who can say
     */
    public static boolean shouldBuild() throws GameActionException {
        boolean output = enemies.length >= 3
                && rc.getRoundNum() > 190 
                && numTraps * 2 <= enemies.length;

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
     *
     * @return
     */
    public static MapLocation buildTarget(TrapType trap) throws GameActionException {
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
        TrapType best = TrapType.NONE;

        // picking between stun or bomb if possible
        if(rc.getCrumbs() >= TrapType.EXPLOSIVE.buildCost) {
            // we want to multiply by damage dealt and divide by cost of trap
            // for stun, multiply by 150, divide by 100 = 1.5
            // for bomb, multiply by 750, divide by 250 = 3
            // idk why i multiplied stunEV by 3, if i didn't then it would always choose bomb
            double stunEV = numFriendlies * 1.5 * 3;
            int bombEV = numEnemies * 3;

            if(stunEV >= bombEV) {
                // System.out.println("stun");
                best = TrapType.STUN;
            } else {
                // System.out.println("bomb");
                best = TrapType.EXPLOSIVE;
            }
        } else {            
            best = TrapType.STUN;
        }
        if(rc.getRoundNum() == 208) {
            best = TrapType.EXPLOSIVE;
        }


        MapLocation buildTarget = buildTarget(best);
        boolean buildInSpawn = false;
        for(MapLocation spawnLoc : rc.getAllySpawnLocations()) {
            if(buildTarget.equals(spawnLoc)) {
                buildInSpawn = true;
                break;
            }
        }
        boolean canBuildTrap = rc.canBuild(best, buildTarget);

        // if(canBuildTrap && buildInSpawn) {
        //         rc.build(best, buildTarget);
        // } else if(canBuildTrap) {
        //     if(best == TrapType.STUN && buildTarget.x % 3 == 0 && buildTarget.y % 3 == 0) {
        //         rc.build(best, buildTarget);
        //     } else if(best == TrapType.EXPLOSIVE && buildTarget.x % 2 == 0 && buildTarget.y % 2 == 0) {
        //         rc.build(best, buildTarget);
        //     }
        // }

        // get nearby traps
        boolean shouldBuildTrap = true;
        int numNearbyTraps = 0;
        for(MapInfo adjacentCell : rc.senseNearbyMapInfos(rc.getLocation(), 2)) {
            if(adjacentCell.getTrapType() != TrapType.NONE) {
                if(best == TrapType.STUN) {
                    shouldBuildTrap = false;
                }
                numNearbyTraps += 1;
            }
        }

        if(best == TrapType.EXPLOSIVE) {
            if(numNearbyTraps > 4) {
                shouldBuildTrap = false;
            }
        }

        if(canBuildTrap && shouldBuildTrap) {
            rc.build(best, buildTarget);
        }    
    }

    /**
     * Choosing movement target and attacking
     */
    public static void runCombat() throws GameActionException {
        Combat.reset();
        CombatMode mode = CombatMode.OFF;

        if (shouldDefendFlag()) mode = CombatMode.FLAG_DEF;
        else if (shouldGrabFlag()) mode = CombatMode.FLAG_OFF;
        else if (shouldRunAway()) mode = CombatMode.DEF;

        Direction dir = Direction.CENTER;

        switch (mode) {
            case FLAG_OFF: dir = Combat.getFlagOffensiveDirection(); break;
            case FLAG_DEF: dir = Combat.getFlagProtectionDirection(); break;
            case DEF: dir = Combat.getDefensiveDirection(); break;
            case OFF: dir = Combat.getOffensiveDirection(); break;
            case NONE: break;
        }

        modeLog[rc.getRoundNum()] = mode;

        target = rc.getLocation().add(dir);
        if(rc.canFill(target)) {
            indicator += "fill ";
            rc.fill(target);
        }
        
        if (mode.equals(CombatMode.DEF)) {
            Combat.attack();
            if (rc.canMove(dir)) rc.move(dir);
        } else {
            if (rc.canMove(dir)) rc.move(dir);
            Combat.attack();
        }

        indicator += "mode: " + mode + " ";
        if (shouldBuild()) build();

        updateSA();
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

    public static void updateSA() throws GameActionException {
        int enc = SA.encode(rc.getLocation(), 0);
        rc.writeSharedArray(SA.ROBOT_COMBAT_INFO_START + ID - 1, enc);
    }
}
