package combat;

import battlecode.common.*;

public class Combat {
    static RobotController rc;
    boolean shouldRunAway;

    static int numEnemiesAttackingUs;
    static int numFriendlies;
    static int numEnemies;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public static void init(RobotController r) throws GameActionException {
        rc = r;
    }


    /**
     * Adjust the boolean runAway if the robot should run away
     */
    public static boolean shouldRunAway() throws GameActionException {
        return numEnemiesAttackingUs > 0 || (numFriendlies + 1 < numEnemiesAttackingUs);
    }

    /**
     * resets all constants used in the decision of whether to run away
     * @throws GameActionException
     */
    public static void resetShouldRunAway() throws GameActionException {
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        friendlies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

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
     * Gets the direction that has the least potential attacking enemies
     */
    public static Direction getDefensiveDirection() throws GameActionException{
        Direction[] dirsToConsider = directions;
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
        Direction[] dirsToConsider = directions;
        Direction bestDirectionSoFar = Direction.CENTER;

        for(Direction dir : dirsToConsider) {
            if(rc.canMove(dir)) {
                MapLocation targetLocation = rc.getLocation().add(dir);
                int minHealth = Integer.MAX_VALUE;

                for(RobotInfo enemy : enemies) {
                    if(targetLocation.isWithinDistanceSquared(enemy.getLocation(), GameConstants.ATTACK_RADIUS_SQUARED)) {
                        if(enemy.getHealth() < minHealth) {
                            minHealth = enemy.getHealth();
                            bestDirectionSoFar = dir;
                        }
                    }
                }
            }
        }

        if(bestDirectionSoFar == Direction.CENTER) {
            bestDirectionSoFar = rc.getLocation().directionTo(enemies[0].getLocation());
        }

        return bestDirectionSoFar;
    }

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
}
