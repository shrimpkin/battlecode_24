package submit5;

import battlecode.common.*;

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

    public static void init(RobotController r) throws GameActionException {
        rc = r;
        indicator = "";
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
        indicator += "(f,e): (" + friendlies.length + " " + enemies.length + ")";
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
        for(RobotInfo enemy: enemies) {
            //this checks if an enemy could attack the current robot
            if(rc.getLocation().isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                numEnemiesAttackingUs++;
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
     * returns the direction that allows for hitting the lowest health enemy 
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
                            && (!(friendlies.length >= enemies.length * OUTNUMBER) || (rc.getRoundNum() > 190 && rc.getRoundNum() < 200)) 
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
}
