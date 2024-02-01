package v8c;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import scala.util.Random;

public class Utils {
    static RobotController rc; 

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
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

    static final Random rng = new Random(6147);
    
    public static void init(RobotController r) {
        rc = r;
    }

    /**
     * @return the number of enemies in vision range
     */
    public static int getNumEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
    }

    /**
     * @return if this robot can sense enemies
     */
    public static boolean isEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length > 0;
    }

    /**
     * @return a random direction from Utils.directions
     */
    public static Direction randomDirection() throws GameActionException {
        return directions[rng.nextInt(directions.length)]; 
    }

    public static MapLocation[] corners(MapLocation pos){
        return new MapLocation[]{
                pos.add(Direction.SOUTHEAST),
                pos.add(Direction.SOUTHWEST),
                pos.add(Direction.NORTHWEST),
                pos.add(Direction.NORTHEAST)
        };
    }

    public static MapLocation[] cardinals(MapLocation pos){
        return new MapLocation[]{
                pos.add(Direction.NORTH),
                pos.add(Direction.SOUTH),
                pos.add(Direction.EAST),
                pos.add(Direction.WEST)
        };
    }

    // ** misc **
    public static int clamp(int value, int min, int max){
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static boolean randomMove(int maxTries) throws GameActionException {
        int tries = 0;
        while (rc.getMovementCooldownTurns() == 0) {
            Direction rand = randomDirection();
            if (rc.canMove(rand)) {
                rc.move(rand);
                return true;
            }
            tries++;

            if (tries >= maxTries) break;
        }

        return false;
    }

    public static boolean isNearEnemyFlag(int squaredRadius) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        for (int flag: new int[]{SA.ENEMY_FLAG1, SA.ENEMY_FLAG2, SA.ENEMY_FLAG3, SA.TARGET_ENEMY_FLAG}) {
            MapLocation flagLoc = SA.getLocation(flag);
            int prefix = SA.getPrefix(flag);

            if (prefix == 1 && rc.onTheMap(flagLoc) && curLoc.distanceSquaredTo(flagLoc) < squaredRadius) {
                return true;
            }
        }

        // otherwise if we don't have resort to the broadcast location
        for (MapLocation flag: rc.senseBroadcastFlagLocations()) {
            if (curLoc.distanceSquaredTo(flag) < squaredRadius) {
                return true;
            }
        }
       
        return false;
    }

    public static boolean isNearOurFlag(int squaredRadius) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        for (int flag: new int[]{SA.FLAG1, SA.FLAG2, SA.FLAG3}) {
            MapLocation flagLoc = SA.getLocation(flag);
            if (rc.onTheMap(flagLoc) && curLoc.distanceSquaredTo(flagLoc) < squaredRadius) {
                return true;
            }
        }

        return false;
    }
}
