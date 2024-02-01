package v8c;

import battlecode.common.*;

public class Micro {
    static RobotController rc;

    static RobotInfo[] enemies;
    static RobotInfo[] friendlies;
    static String indicator;

    static MapLocation averageEnemy;
    static int DPS = 150;
    static boolean shouldAttackFirst = false;

    public static void initTurn(RobotController r) throws GameActionException {
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

    public static Direction getOffensiveDirection() throws GameActionException {
        MicroInfo[] microInfo = new MicroInfo[9];
        for (int i = 0; i < 9; i++) {
            microInfo[i] = new MicroInfo(Utils.directions[i]);

            for (RobotInfo unit : enemies) {
                microInfo[i].updateEnemy(unit);
            }

            for (RobotInfo unit : friendlies) {
                microInfo[i].updateAlly(unit);
            }
        }

        MicroInfo best = microInfo[8];
        for (int i = 0; i < 8; i++) {
            if (microInfo[i].isBetterOffense(best)) {
                best = microInfo[i];
            }
        }
        // microInfo[8] is center
        shouldAttackFirst = !best.isBetterAttack(microInfo[8]);

        return best.dir;

    }

    public static Direction getDefensiveDirection() throws GameActionException {
        MicroInfo[] microInfo = new MicroInfo[9];
        for (int i = 0; i < 9; i++) {
            microInfo[i] = new MicroInfo(Utils.directions[i]);

            for (RobotInfo unit : enemies) {
                microInfo[i].updateEnemy(unit);
            }

            for (RobotInfo unit : friendlies) {
                microInfo[i].updateAlly(unit);
            }
        }

        MicroInfo best = microInfo[8];
        for (int i = 0; i < 8; i++) {
            if (microInfo[i].isBetterDefense(best)) {
                best = microInfo[i];
            }
        }

        shouldAttackFirst = !best.isBetterAttack(microInfo[8]);

        return best.dir;
    }

    public static class MicroInfo {
        Direction dir;
        MapLocation location;
        int minDistanceToEnemy = Integer.MAX_VALUE;
        double enemiesTargeting = 0, enemiesSniping = 0;
        double alliesTargeting = 0;
        boolean canMove = true;
        int minHealth = Integer.MAX_VALUE;
        boolean actionReady;

        public MicroInfo(Direction dir) {
            this.dir = dir;
            this.location = rc.getLocation().add(dir);
            if (!dir.equals(Direction.CENTER) && !rc.canMove(dir)) canMove = false;
            alliesTargeting++;
            actionReady = rc.isActionReady();
        }

        void updateEnemy(RobotInfo unit) {
            if (!canMove) return;
            int dist = unit.getLocation().distanceSquaredTo(location);
            if (dist < minDistanceToEnemy) minDistanceToEnemy = dist;
            if (dist <= GameConstants.ATTACK_RADIUS_SQUARED && unit.getHealth() < minHealth)
                minHealth = unit.getHealth(); // track minimum health of attack-able enemy
            if (dist <= GameConstants.ATTACK_RADIUS_SQUARED) enemiesTargeting++; // enemies that can be attacked
            if (dist <= 10) enemiesSniping++; // one movement outside of attack radius
        }

        void updateAlly(RobotInfo unit) {
            if (!canMove) return;
            alliesTargeting++;
        }

        boolean canAttack() {
            if (!canMove) return false;
            if (!actionReady) return false;

            return minDistanceToEnemy <= GameConstants.ATTACK_RADIUS_SQUARED;
        }

        boolean canKill() {
            if (!canMove) return false;
            if (!actionReady) return false;

            return rc.getAttackDamage() >= minHealth;
        }

        boolean willDie() {
            if (!canMove) return true;

            return enemiesTargeting * DPS >= rc.getHealth();
        }

        boolean isBetterAttack(MicroInfo m) {
            // prioritize being able to kill enemies
            if (canKill() && !m.canKill()) return true;
            if (!canKill() && m.canKill()) return false;
            // prioritize being able to attack
            if (canAttack() && !m.canAttack()) return true;
            if (!canAttack() && m.canAttack()) return false;
            // the lowest health attack-able enemy
            if (minHealth < m.minHealth) return true;
            if (minHealth > m.minHealth) return false;

            return true;
        }

        boolean isBetterDefense(MicroInfo m) {
            // prioritize own life
            if (willDie() && !m.willDie()) return false;
            if (!willDie() && m.willDie()) return true;
            // fewer enemies that can attack us
            if (enemiesTargeting < m.enemiesTargeting) return true;
            if (enemiesTargeting > m.enemiesTargeting) return false;
            // if both on action cool-down, prioritize location w/ the fewest enemies that can drive-by us in 1 turn
            if (!actionReady && !m.actionReady) return enemiesSniping < m.enemiesSniping;
            // put distance between us and enemy
            return minDistanceToEnemy > m.minDistanceToEnemy;
        }

        boolean isBetterOffense(MicroInfo m) {
            if (canKill() && !m.canKill()) return true;
            if (!canKill() && m.canKill()) return false;

            if (willDie() && !m.willDie()) return false;
            if (!willDie() && m.willDie()) return true;

            if (canAttack() && !m.canAttack()) return true;
            if (!canAttack() && m.canAttack()) return false;

            // if both on action cool-down, prioritize location w/ the fewest enemies that can drive-by us in 1 turn
            if (!actionReady && !m.actionReady) return enemiesSniping < m.enemiesSniping;

            if (enemiesTargeting < m.enemiesTargeting) return true;
            if (enemiesTargeting > m.enemiesTargeting) return false;

            return minDistanceToEnemy < m.minDistanceToEnemy;
        }
    }
}
