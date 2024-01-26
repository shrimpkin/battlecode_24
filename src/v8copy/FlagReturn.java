package v8copy;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class FlagReturn {
    static RobotController rc;
    static String indicator;

    public static void init(RobotController r) throws GameActionException {
        rc = r;
    }

    /**
     * Gets return target for robot with flag
     * Takes into account enemies and move away from them if possible
     */
    public static MapLocation getReturnTarget() throws GameActionException {
        indicator = "";
        if(Utils.isEnemies()) {
            Combat.reset();
            indicator += "ENM ";
            return enemies();
        } else {
            indicator += "NEN ";
            return noEnemies();
        }
    }

    public static MapLocation enemies() throws GameActionException {
        //this makes sure that if we are already on the other side of the enemies that we 
        if(noEnemies().distanceSquaredTo(rc.getLocation()) < Combat.averageEnemy.distanceSquaredTo(noEnemies())) {
            indicator += "NEN ";
            noEnemies();
        }
        
        indicator += "RAW ";
        return runAway();
    }

    /**
     * Attempting to avoid enemies
     */
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

    /**
     * If we sense no enemies then the robot will simply run straight towards the nearest spawn zone
     */
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
