package submit6;

import battlecode.common.*;

public class FlagReturn {
    static RobotController rc;

    public static void init(RobotController r) throws GameActionException {
        rc = r;
    }

    public static MapLocation getReturnTarget() throws GameActionException {
        if(Utils.isEnemies()) return enemies();
        else return noEnemies();
    }

    public static MapLocation enemies() throws GameActionException {
        if(Combat.averageFriend != null) {
            return towardsFriends();
        }
        
        return runAway();

    }

    public static MapLocation towardsFriends() throws GameActionException {
        int minDistance = Integer.MAX_VALUE;
        MapLocation bestLocationSoFar = rc.getLocation();

        for(Direction dir : Utils.directions) {

            if(rc.canMove(dir)) {
                MapLocation target = rc.getLocation().add(dir);

                if(target.distanceSquaredTo(Combat.averageFriend) < minDistance) {
                    bestLocationSoFar = target;
                    minDistance = target.distanceSquaredTo(Combat.averageFriend);
                }
            }
        }

        return bestLocationSoFar;
    }

    public static MapLocation runAway() throws GameActionException {
        int maxDistance = 0;
        MapLocation bestLocationSoFar = rc.getLocation();

        for(Direction dir : Utils.directions) {
            if(rc.canMove(dir)) {
                MapLocation target = rc.getLocation().add(dir);

                if(target.distanceSquaredTo(Combat.averageEnemy) > maxDistance) {
                    maxDistance = target.distanceSquaredTo(Combat.averageEnemy);
                    bestLocationSoFar = target;
                }
            }
        }

        return bestLocationSoFar;
    }

    public static MapLocation noEnemies() throws GameActionException {
        MapLocation target = SA.getLocation(SA.FLAG1);
        MapLocation[] spawnLocs = rc.getAllySpawnLocations();
        int min = Integer.MAX_VALUE;
        for(MapLocation spawn : spawnLocs) {
            if(rc.getLocation().distanceSquaredTo(spawn) < min) {
                min = rc.getLocation().distanceSquaredTo(spawn);
                target = spawn;
            }
        }
        return target;
    }
}
