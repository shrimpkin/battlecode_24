package v8;

import battlecode.common.*;

public class Micro {
    static RobotController rc;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static int DPS = 150;
    static boolean shouldAttackFirst = false;

    public void initTurn(RobotController r) throws GameActionException {
        rc = r;
        enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        friendlies = rc.senseNearbyRobots(-1, rc.getTeam());

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

    public Direction getOffensiveDirection() throws GameActionException{
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

        return bestDirectionSoFar;
    }

    public Direction getDefensiveDirection() throws GameActionException{
        MicroInfo[] microInfo = new MicroInfo[9];
        for(int i = 0; i < 9; i++) {
            microInfo[i] = new MicroInfo(Utils.directions[i]);

            for(RobotInfo unit : enemies) {
                microInfo[i].updateEnemy(unit);
            }

            for(RobotInfo unit : friendlies) {
                microInfo[i].updateAlly(unit);
            }
        }

        MicroInfo best = microInfo[8];
        for(int i = 0; i < 8; i++) {
            if(microInfo[i].isBetterDefense(best)) {
                best = microInfo[i];
            }
        }

        return best.dir;
    }

    public class MicroInfo {
        Direction dir;
        MapLocation location;
        int minDistanceToEnemy = Integer.MAX_VALUE;
        double DPSreceived = 0;
        double enemiesTargeting = 0;
        double alliesTargeting = 0;
        boolean canMove = true;
        int minHealth = Integer.MAX_VALUE;
        boolean canAttack = true;

        public MicroInfo(Direction dir) {
            this.dir = dir;
            this.location = rc.getLocation().add(dir);
            if(!dir.equals(Direction.CENTER) && !rc.canMove(dir)) canMove = false;
            this.DPSreceived -= DPS;
            alliesTargeting++;
            canAttack = rc.isActionReady();
        }

        void updateEnemy(RobotInfo unit) {
            if(!canMove) return;
            int dist = unit.getLocation().distanceSquaredTo(location);
            if(dist < minDistanceToEnemy) minDistanceToEnemy = dist;
            if(dist <= GameConstants.ATTACK_RADIUS_SQUARED) DPSreceived += DPS;
            if(dist <= GameConstants.ATTACK_RADIUS_SQUARED) enemiesTargeting++;
            if(unit.getHealth() < minHealth) minHealth = unit.getHealth();
        }

        void updateAlly(RobotInfo unit) {
            if(!canMove) return;
            alliesTargeting++;
        }

        int safe() {
            if(!canMove) return -1;

            return 2;
        }

        boolean canAttack() {
            if(!canMove) return false;
            return minDistanceToEnemy <= GameConstants.ATTACK_RADIUS_SQUARED;
        }

        boolean canKill() {
            if(!canMove) return false;
            if(!canAttack) return false;

            return rc.getAttackDamage() >= minHealth;
        }

        boolean willDie() {
            if(!canMove) return true;
            
            return enemiesTargeting * DPS >= rc.getHealth();
        }

        boolean isBetterAttack(MicroInfo m) {
            if(canKill() && !m.canKill()) return true;
            if(!canKill() && m.canKill()) return false;

            if(canAttack() && !m.canAttack()) return true;
            if(!canAttack() && m.canAttack()) return false;

            return true;
        }

        boolean isBetterDefense(MicroInfo m) {
            if(willDie() && !m.willDie()) return false;
            if(!willDie() && m.willDie()) return true;

            return enemiesTargeting <= m.enemiesTargeting;
        }
    }
}