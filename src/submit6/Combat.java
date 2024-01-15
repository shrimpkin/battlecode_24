package submit6;

import battlecode.common.*;

public class Combat {
    static RobotController rc;
    boolean shouldRunAway;

    static int numEnemiesAttackingUs;
    static int numFriendliesWithUs;
    static int numFriendlies;
    static int numEnemies;
    static int numTraps;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static MapLocation averageTrap;

    static int OUTNUMBER = 2;
    static int HEALTH_TO_RUNAWAY = 600;
    static int IS_STUCK_TURNS = 3;
    static enum combatMode {OFF, DEF, TRAP, NONE};
    static combatMode[] combatModeLog;
    static MapLocation[] combatLocations;



    public static void init(RobotController r) throws GameActionException {
        rc = r;
        indicator = "";

        combatModeLog = new combatMode[2001];
        combatModeLog[0] = combatMode.NONE;

        combatLocations = new MapLocation[2001];
    }

    /**
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        return numEnemiesAttackingUs > 0 || (numFriendlies + 1 < numEnemies) || rc.getHealth() < HEALTH_TO_RUNAWAY;
    }

    /**
     * Should the robot attempt to make the enemies walk into the traps
     */
    public static boolean shouldTrap() throws GameActionException {
        return averageTrap != null                                          //make sure there is trap
                && enemies.length >= 3                                      //make sure there is enough enemies 
                && !(friendlies.length >= enemies.length * OUTNUMBER);      //make sure we don't already outnumber by a lot
    }

    public static boolean shouldContinueTrap() throws GameActionException {
        return (combatModeLog[rc.getRoundNum() - 1].equals(combatMode.TRAP) && !combatModeLog[rc.getRoundNum() - 2].equals(combatMode.TRAP));
    }

    public static boolean isStuck() throws GameActionException {
        for(int i = 1; i <= IS_STUCK_TURNS; i++) {
            int index = rc.getRoundNum() - i;

            if(index < 0) return false;
            if(combatLocations[index] == null) return false;
            if(!combatLocations[index].equals(rc.getLocation())) return false;
        }

        return true;
    }

    public static void reset() throws GameActionException {
        indicator = "";
        resetShouldRunAway();
        resetShouldTrap();
    }

    /**
     * resets all constants used in the decision of whether to run away
     * @throws GameActionException
     */
    public static void resetShouldRunAway() throws GameActionException {
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        friendlies = rc.senseNearbyRobots(-1, rc.getTeam());

        numEnemies = enemies.length;
        numFriendlies = friendlies.length;
        
        RobotInfo[] nearEnemies = rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED, rc.getTeam().opponent());
        RobotInfo[] nearFriends = rc.senseNearbyRobots(GameConstants.ATTACK_RADIUS_SQUARED, rc.getTeam());
        
        numEnemiesAttackingUs = nearEnemies.length;
        numFriendliesWithUs = nearFriends.length;
    }


    /**
     * Updates average trap and enemy locations
     * @throws GameActionException
     */
    public static void resetShouldTrap() throws GameActionException {
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        
        double averageTrap_x = 0;
        double averageTrap_y = 0;

        numTraps = 0;

        for(MapInfo info : mapInfo) {
            if(!info.getTrapType().equals(TrapType.NONE)) {
                averageTrap_x += info.getMapLocation().x;
                averageTrap_y += info.getMapLocation().y;
                numTraps++;
            }
        }

        if(numTraps == 0) {
            averageTrap = null;
        } else {
            averageTrap_x /= numTraps;
            averageTrap_y /= numTraps;
            averageTrap = new MapLocation((int) averageTrap_x, (int) averageTrap_y);
        }

        double averageEnemy_x = 0;
        double averageEnemy_y = 0;

        for(RobotInfo robot : enemies) {
            averageEnemy_x += robot.getLocation().x;
            averageEnemy_y += robot.getLocation().y;
        }

        averageEnemy_x /= enemies.length;
        averageEnemy_y /= enemies.length;

        averageEnemy = new MapLocation((int) averageEnemy_x, (int) averageEnemy_y);
    }

    /**
     *  Gets the direction that result in the robot being behind traps
     */
    public static Direction getTrapDirection() throws GameActionException{
        return averageEnemy.directionTo(averageTrap);
    }

    /**
     * Gets the direction that has the least potential attacking enemies
     */
    public static Direction getDefensiveDirection() throws GameActionException{
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int bestEnemiesSeen = Integer.MAX_VALUE;

        for(Direction dir : dirsToConsider) {
            if(rc.canMove(dir)) {
                MapLocation targetLocation = rc.getLocation().add(dir);
                int potentialEnemies = 0;
                for(RobotInfo enemy: enemies) {
                    //this checks if an enemy could attack the current robot
                    if(targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        potentialEnemies++;
                    }
                }

                if(potentialEnemies < bestEnemiesSeen) {
                    bestDirectionSoFar = dir;
                    bestEnemiesSeen = potentialEnemies;
                } 
            }
        }

        return bestDirectionSoFar;
    }

    /**
     * returns the direction that allows for hitting an enemy and getting hit by the least enemies
     */
    public static Direction getOffensiveDirection() throws GameActionException {
        Direction[] dirsToConsider = Utils.directions;
        Direction bestDirectionSoFar = Direction.CENTER;
        int minEnemies = Integer.MAX_VALUE;

        for(Direction dir : dirsToConsider) {
            if(rc.canMove(dir)) {
                int numEnemies = 0;
                MapLocation targetLocation = rc.getLocation().add(dir);

                for(RobotInfo enemy : enemies) {
                    if(targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        numEnemies++;
                    }
                }

                if(numEnemies > 0 && numEnemies < minEnemies) {
                    bestDirectionSoFar = dir;
                    minEnemies = numEnemies;
                }
            }
        }

        if(bestDirectionSoFar == Direction.CENTER) {
            bestDirectionSoFar = rc.getLocation().directionTo(enemies[0].getLocation());
        }

        return bestDirectionSoFar;
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
        for(RobotInfo robot : enemyRobots) {
            if(rc.canAttack(robot.getLocation()) 
                && rc.senseRobotAtLocation(robot.getLocation()).getHealth() < minHealth) {
                

                /**
                 * target flag holders over everything else
                 */
                if(robot.hasFlag()) {
                    rc.attack(robot.getLocation());
                    return;
                } 

                minHealth = rc.senseRobotAtLocation(robot.getLocation()).getHealth();
                target = robot.getLocation();
            }
            
        }

        if(target != null && rc.canAttack(target)) {
            rc.attack(target);
        }

        
        //heals any friendly robots it  can
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo robot : friendlyRobots) {
            if(rc.canHeal(robot.getLocation())) rc.heal(robot.getLocation());
        }
    }

    /**
     * 
     */
    public static boolean shouldBuild() throws GameActionException {
        boolean output =    enemies.length >= 3 
                            && ((!(friendlies.length >= enemies.length * OUTNUMBER) && rc.getRoundNum() >= 200) || (rc.getRoundNum() > 190 && rc.getRoundNum() < 200)) 
                            && numTraps <= 2
                            && averageEnemy != null;

        if(output) indicator += "BUILD ";
        return output;
    }

    /**
     * Returns a MapLocation to build on that is best
     * @return
     */
    public static MapLocation buildTarget() throws GameActionException {
        MapLocation bestLocationSoFar = rc.getLocation();
        int minDistance = Integer.MAX_VALUE;

        for(Direction dir : Utils.directions) {
            MapLocation target = rc.getLocation().add(dir);

            if(target != null && target.distanceSquaredTo(averageEnemy) < minDistance && rc.canBuild(TrapType.EXPLOSIVE, target)) {
                minDistance = target.distanceSquaredTo(averageEnemy);
                bestLocationSoFar = target;
            }
        }

        return bestLocationSoFar;
    }

    /**
     * Choosing movement target and attacking 
     */
    public static MapLocation getCombatTarget() throws GameActionException {
        Combat.reset();
        
        boolean shouldBuild = Combat.shouldBuild();
        if(shouldBuild && rc.canBuild(TrapType.EXPLOSIVE, Combat.buildTarget())) {
            rc.build(TrapType.EXPLOSIVE, Combat.buildTarget());
        }

        boolean shouldRun = Combat.shouldRunAway();
        boolean shouldTrap = Combat.shouldTrap() || shouldContinueTrap();

        Direction dir;

        if(shouldTrap) {
            combatModeLog[rc.getRoundNum()] = combatMode.TRAP;
            dir = Combat.getTrapDirection();
        } else if(shouldRun) {
            combatModeLog[rc.getRoundNum()] = combatMode.DEF;
            dir = Combat.getDefensiveDirection();
        } else {
            combatModeLog[rc.getRoundNum()] = combatMode.OFF;
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
        for(int i = rc.getRoundNum(); i > 0; i--) {
            if(rc.getRoundNum() - i > 2) break;

            indicator += combatModeLog[i] + " ";
        }
        indicator += isStuck() + " ";
        return targetLocation;
    }
}
