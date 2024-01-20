package v9;

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

    // ** misc **
    public static int clamp(int value, int min, int max){
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /*
    Goal with this function is to decide if a bomb is likely at this position,
    Bombs do less damage if dug up, and this manages the heuristics of digging.

    From spec:
    Can be built on land or in water. When an opponent robot enters the cell containing this trap, it explodes dealing 750 damage to all opponent robots within a radius of 4‾√
 cells. When an opponent robot digs, fills, or builds on the trap, it explodes dealing 200 damage to all opponent robots within a radius of 2‾√
 cells. The build will fail when this happens, while dig and fill will succeed.

   No change on how stuns work though, and we're in stun meta.
     */
    public static boolean isBombLikely(MapLocation pos) throws GameActionException {
        FlagInfo[] flags = rc.senseNearbyFlags(2, rc.getTeam().opponent());
        return flags.length > 0;
    }
}
