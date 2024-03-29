package v8;

import battlecode.common.*;

import java.util.HashSet;

public class Pathfinding {

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
    static final double eps = 1e-5;
    static RobotController rc;
    static MapLocation target = null;
    static double avgRubbleCost = 0;
    static boolean[] impassable = null;

    public static void init(RobotController r) {
        rc = r;
    }

    static void setImpassable(boolean[] imp) {
        impassable = imp;
    }

    public static void initTurn() {
        impassable = new boolean[directions.length];
    }

    static boolean reachedTarget() {
        if (target == null) return true; // vacuously true
        return target.equals(rc.getLocation());
    }

    /**
     * @return whether the target is known to be wholly impassible: [enemy territory in setup, wall/dam generally]
     */
    static boolean invalidTarget() {
        if (target == null) return true; // vacuously true
        MapInfo info = MapRecorder.get(target);
        return info != null && (
                info.isWall() || info.isDam() // wall or dam
                || (rc.getRoundNum() < 200 && !info.getTeamTerritory().equals(rc.getTeam())) // other team land in setup
        );
    }

    static boolean canMove(Direction dir) {
        if (!rc.canMove(dir)) return false;
        if (impassable[dir.ordinal()]) return false;
        return true;
    }

    static double getEstimation(MapLocation loc) {
        try {
            if (loc.distanceSquaredTo(target) == 0) return 0;
            int d = distance(target, loc);
            double r = 1.0;
            return r + (d - 1) * avgRubbleCost;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 1e9;
    }

    static public void move(MapLocation loc) {
        if (!rc.isMovementReady()) return;
        target = loc;
        if (!BugNav.move()) greedyPath();
        //BugNav.move();
    }

    static void greedyPath() {
        try {
            MapLocation myLoc = rc.getLocation();
            Direction bestDir = null;
            double bestEstimation = 0;
            double firstStep = 1.0;
            int bestEstimationDist = 0;
            for (Direction dir : directions) {
                MapLocation newLoc = myLoc.add(dir);
                if (!rc.onTheMap(newLoc)) continue;
        
                if (!canMove(dir)) continue;
                if (!strictlyCloser(newLoc, myLoc, target)) continue;

                int newDist = newLoc.distanceSquaredTo(target);

                double estimation = firstStep + getEstimation(newLoc);
                if (bestDir == null || estimation < bestEstimation - eps || (Math.abs(estimation - bestEstimation) <= 2 * eps && newDist < bestEstimationDist)) {
                    bestEstimation = estimation;
                    bestDir = dir;
                    bestEstimationDist = newDist;
                }
            }
            
            if (bestDir != null) rc.move(bestDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static boolean strictlyCloser(MapLocation newLoc, MapLocation oldLoc, MapLocation target) {
        int dOld = distance(target, oldLoc), dNew = distance(target, newLoc);
        if (dOld < dNew) return false;
        if (dNew < dOld) return true;
        return target.distanceSquaredTo(newLoc) < target.distanceSquaredTo(oldLoc);
    }

    static int distance(MapLocation A, MapLocation B) {
        return Math.max(Math.abs(A.x - B.x), Math.abs(A.y - B.y));
    }

    static class BugNav {

        static final int INF = 1000000;
        static final int MAX_MAP_SIZE = GameConstants.MAP_MAX_HEIGHT;

        static boolean rotateRight = true; //if I should rotate right or left
        static MapLocation lastObstacleFound = null; //latest obstacle I've found in my way
        static int minDistToEnemy = INF; //minimum distance I've been to the enemy while going around an obstacle
        static MapLocation prevTarget = null; //previous target
        static HashSet<Integer> visited = new HashSet<>();

        static boolean move() {
            try {
                //different target? ==> previous data does not help!
                if (prevTarget == null || target.distanceSquaredTo(prevTarget) > 0) resetPathfinding();

                //If I'm at a minimum distance to the target, I'm free!
                MapLocation myLoc = rc.getLocation();
                int d = myLoc.distanceSquaredTo(target);
                if (d <= minDistToEnemy) resetPathfinding();

                int code = getCode();

                if (visited.contains(code)) resetPathfinding();
                visited.add(code);

                //Update data
                prevTarget = target;
                minDistToEnemy = Math.min(d, minDistToEnemy);

                //If there's an obstacle I try to go around it [until I'm free] instead of going to the target directly
                Direction dir = myLoc.directionTo(target);
                if (lastObstacleFound != null) dir = myLoc.directionTo(lastObstacleFound);
                if (canMove(dir)) {
                    resetPathfinding();
                    // if (rc.getType() != RobotType.SLANDERER) return false;
                }

                //I rotate clockwise or counterclockwise (depends on 'rotateRight'). If I try to go out of the map I change the orientation
                //Note that we have to try at most 16 times since we can switch orientation in the middle of the loop. (It can be done more efficiently)
                for (int i = 8; i-- > 0; ) {
                    if (canMove(dir)) {
                        rc.move(dir);
                        return true;
                    }
                    MapLocation newLoc = myLoc.add(dir);
                    if (!rc.onTheMap(newLoc)) rotateRight = !rotateRight;
                        //If I could not go in that direction and it was not outside of the map, then this is the latest obstacle found
                    else lastObstacleFound = myLoc.add(dir);
                    if (rotateRight) dir = dir.rotateRight();
                    else dir = dir.rotateLeft();
                }

                if (canMove(dir)) rc.move(dir);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        //clear some of the previous data
        static void resetPathfinding() {
            lastObstacleFound = null;
            minDistToEnemy = INF;
            visited.clear();
        }

        static int getCode() {
            int x = rc.getLocation().x;
            int y = rc.getLocation().y;
            Direction obstacleDir = rc.getLocation().directionTo(target);
            if (lastObstacleFound != null) obstacleDir = rc.getLocation().directionTo(lastObstacleFound);
            int bit = rotateRight ? 1 : 0;
            return (((((x << 6) | y) << 4) | obstacleDir.ordinal()) << 1) | bit;
        }
    }

}
