package submit6;

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

    public static void handleException(String s) {
        System.out.println(s + "at turn " + rc.getRoundNum() + " at " + rc.getLocation());
        Clock.yield();
    }
}
