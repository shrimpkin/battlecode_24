package submit5;

import battlecode.common.*;
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

    public static boolean isCardinal(Direction dir) {
        if (dir == Direction.NORTH || dir == Direction.EAST || dir == Direction.SOUTH || dir == Direction.WEST) return true;
        return false;
    }

    public static Direction toCardinalDirection(Direction dir, boolean preferHorizontal) {
        switch (dir) {
            case NORTHEAST: {
                if (preferHorizontal) {
                    return Direction.EAST;
                }
                return Direction.NORTH;
            }
            case SOUTHEAST: {
                if (preferHorizontal) {
                    return Direction.EAST;
                }
                return Direction.SOUTH;
            }
            case SOUTHWEST: {
                if (preferHorizontal) {
                    return Direction.WEST;
                }
                return Direction.SOUTH;
            }
            case NORTHWEST: {
                if (preferHorizontal) {
                    return Direction.WEST;
                }
                return Direction.NORTH;
            }
        }

        return dir;
    }

    public static boolean validPosition(MapLocation loc) {
        if (loc.y < 0 || loc.x < 0) return false;
        if (loc.y > rc.getMapHeight() - 1 || loc.x > rc.getMapWidth() - 1) return false;
        return true;
    }
}
