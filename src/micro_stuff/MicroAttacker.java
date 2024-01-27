package micro_stuff;

import battlecode.common.*;

public class MicroAttacker {
    RobotController rc;
    final int INF = 1000000;
    boolean attacker = true;
    boolean shouldPlaySafe = false;
    boolean alwaysInRange = false;
    boolean hurt = false; 
    static int myVisionRange;
    static double myDPS;
    boolean severelyHurt = false;

    MicroAttacker(RobotController r){
        rc = r;
        myVisionRange = GameConstants.VISION_RADIUS_SQUARED;

        myDPS = rc.getAttackDamage();
    }

    final static double currentDPS = 150;
    static int ACTION_RADIUS = GameConstants.ATTACK_RADIUS_SQUARED;
    static boolean canAttack;

    static final Direction[] dirs = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
        Direction.CENTER
    };

    void doMicro() throws GameActionException{
        if(!rc.isMovementReady()) return;

        severelyHurt = rc.getHealth() < 500;
        RobotInfo[] units = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if(units.length == 0) return;

        canAttack = rc.isActionReady();
        alwaysInRange = false;
        if(!rc.isActionReady()) alwaysInRange = true;
        if(severelyHurt) alwaysInRange = true;

        myDPS = rc.getAttackDamage();
        MicroInfo[] microInfo = new MicroInfo[9];
        for(int i = 0; i < 9; ++i) microInfo[i] = new MicroInfo(dirs[i]);

        for(RobotInfo unit : units) {            
            for(int i = 0; i < 9; i++) {
                microInfo[i].updateEnemy(unit);
            }
        }

        units = rc.senseNearbyRobots(-1, rc.getTeam());
        for(RobotInfo unit : units) {
            for(int i = 0; i < 9; i++) {
                microInfo[i].updateAlly(unit);
            }
        }

        MicroInfo bestMicro = microInfo[8];
        for(int i = 0; i < 8; ++i) {
            if(microInfo[i].isBetter(bestMicro)) bestMicro = microInfo[i];
        }

        if(bestMicro.dir == Direction.CENTER) return;

        if(rc.canMove(bestMicro.dir)) {
            rc.move(bestMicro.dir);
        }
    }

    class MicroInfo{
        Direction dir;
        MapLocation location;
        int minDistanceToEnemy = Integer.MAX_VALUE;
        double DPSreceived = 0;
        double enemiesTargeting = 0;
        double alliesTargeting = 0;
        boolean canMove = true;

        public MicroInfo(Direction dir) throws GameActionException{
            this.dir = dir;
            this.location = rc.getLocation().add(dir);
            if (dir != Direction.CENTER && !rc.canMove(dir)) canMove = false;
            else{
                if (!hurt){
                    this.DPSreceived -= myDPS;
                    this.alliesTargeting += myDPS;
                    minDistanceToEnemy = ACTION_RADIUS;
                } else {
                    minDistanceToEnemy = Integer.MAX_VALUE;
                }
            }
        }

        void updateEnemy(RobotInfo unit){
            if (!canMove) return;
            int dist = unit.getLocation().distanceSquaredTo(location);
            if (dist < minDistanceToEnemy)  minDistanceToEnemy = dist;
            if (dist <= ACTION_RADIUS) DPSreceived += currentDPS;
            if (dist <= ACTION_RADIUS) enemiesTargeting += currentDPS;
        }

        void updateAlly(RobotInfo unit){
            if (!canMove) return;
            alliesTargeting += currentDPS;
        }

        int safe(){
            if (!canMove) return -1;
            if (DPSreceived > 0) return 0;
            if (enemiesTargeting > alliesTargeting) return 1;
            return 2;
        }

        boolean inRange(){
            if (alwaysInRange) return true;
            return minDistanceToEnemy <= ACTION_RADIUS;
        }

        //equal => true
        boolean isBetter(MicroInfo M){

            if (safe() > M.safe()) return true;
            if (safe() < M.safe()) return false;

            if (inRange() && !M.inRange()) return true;
            if (!inRange() && M.inRange()) return false;

            if (!severelyHurt) {
                if (alliesTargeting > M.alliesTargeting) return true;
                if (alliesTargeting < M.alliesTargeting) return false;
            }

            if (inRange()) return minDistanceToEnemy >= M.minDistanceToEnemy;
            else return minDistanceToEnemy <= M.minDistanceToEnemy;
        }
    }
}
