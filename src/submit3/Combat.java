package submit3;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TrapType;

public class Combat {
    static RobotController rc;
    static boolean shouldRunAway;
    static boolean enemyHasFlag;
    static int numEnemiesAttackingUs;
    static int numFriendlies;
    static int numEnemies;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static MapLocation averageTrap;
    static MapLocation flagLocation; // closest of our flags being captured by the enemies

    static int OUTNUMBER = 2;

    public static void init(RobotController r) throws GameActionException {
        rc = r;
        indicator = "";
    }


    /**
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        if (enemyHasFlag) return false;
        return (numFriendlies + 1 < numEnemiesAttackingUs)  // we're outnumbered
                || rc.getHealth() < 600; // we're low health
    }

    /**
     * Should the robot attempt to make the enemies walk into the traps
     */
    public static boolean shouldTrap() throws GameActionException {
        if (enemyHasFlag) return false;
        indicator += "(f,e): (" + friendlies.length + " " + enemies.length + ")";
        indicator += !(friendlies.length - enemies.length >= OUTNUMBER);
        return averageTrap != null                                          //make sure there is trap
                && enemies.length >= 3                                      //make sure there is enough enemies 
                && !(friendlies.length >= enemies.length * OUTNUMBER);      //make sure we don't already outnumber by a lot
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
        
        numEnemiesAttackingUs = 0;
        int minDist2 = flagLocation == null ? Integer.MAX_VALUE : rc.getLocation().distanceSquaredTo(flagLocation);
        for(RobotInfo enemy: enemies) {
            //this checks if an enemy could attack the current robot
            if(rc.getLocation().isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                numEnemiesAttackingUs++;
            }
            if (enemy.hasFlag()){
                enemyHasFlag = true;
                int dist2 = rc.getLocation().distanceSquaredTo(enemy.getLocation());
                if (dist2 < minDist2) {
                    minDist2 = dist2;
                    flagLocation = enemy.getLocation();
                }
            }
        }
    }


    /**
     * Updates average trap and enemy locations
     * @throws GameActionException
     */
    public static void resetShouldTrap() throws GameActionException {
        MapInfo[] mapInfo = rc.senseNearbyMapInfos();
        
        double averageTrap_x = 0;
        double averageTrap_y = 0;

        double numTraps = 0;

        for(MapInfo info : mapInfo) {
            if(!info.getTrapType().equals(TrapType.NONE)) {
                averageTrap_x += info.getMapLocation().x;
                averageTrap_y += info.getMapLocation().y;
                numTraps++;
            }
        }

        if(numTraps == 0) {
            averageTrap = null;
            return;
        }

        averageTrap_x /= numTraps;
        averageTrap_y /= numTraps;

        double averageEnemy_x = 0;
        double averageEnemy_y = 0;

        for(RobotInfo robot : enemies) {
            averageEnemy_x += robot.getLocation().x;
            averageEnemy_y += robot.getLocation().y;
        }

        averageEnemy_x /= enemies.length;
        averageEnemy_y /= enemies.length;

        averageEnemy = new MapLocation((int) averageEnemy_x, (int) averageEnemy_y);
        averageTrap = new MapLocation((int) averageTrap_x, (int) averageTrap_y);
        
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
     * returns the direction that allows for hitting the lowest health enemy 
     */
    public static Direction getOffensiveDirection() throws GameActionException {
        if (enemyHasFlag) return  rc.getLocation().directionTo(flagLocation);
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
            if(rc.canAttack(robot.getLocation()) ){
                // target flag holders over everything else
                if(robot.hasFlag()) {
                    rc.attack(robot.getLocation());
                    return;
                }
                if (rc.senseRobotAtLocation(robot.getLocation()).getHealth() < minHealth) {
                    minHealth = rc.senseRobotAtLocation(robot.getLocation()).getHealth();
                    target = robot.getLocation();
                }
            }
        }

        if(target != null) {
            rc.attack(target);
            return;
        }

        //heals any friendly robots it can, again prioritize flag bearers and low HP units
        RobotInfo[] friendlyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
//        int cnt = 0;
        target = null;
        minHealth = 1001;
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
