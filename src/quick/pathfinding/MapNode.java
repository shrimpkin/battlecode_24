package quick.pathfinding;

import battlecode.common.*;

public class MapNode implements Comparable<MapNode>{
    public MapLocation location;
    public MapNode parent;
    public int g;
    public int h;
    public int f;

    public MapNode(MapLocation mapLocation, MapNode parent) {
        this.location = mapLocation;
        this.parent = parent;
        g = parent.g + 1;
        computeF(mapLocation);
    }

    public MapNode(MapLocation mapLocation) {
        this.location = mapLocation;
        this.parent = null;
        g = 0;
        computeF(mapLocation);

    }

    public int getF() {
        return g + h;
    }

    //takes about 1000
    public MapNode[] generateSuccesors(RobotController rc, MapLocation targetLocation) throws GameActionException {
        MapNode[] output = new MapNode[5];
        Direction targetDirection = rc.getLocation().directionTo(targetLocation);
        Direction[] smallerDirections = {targetDirection, targetDirection.rotateLeft(), targetDirection.rotateLeft().rotateLeft(), targetDirection.rotateRight(), targetDirection.rotateRight()};
        MapLocation updatedMapLocation;
        for(int i = 4; i >= 0; i--) {
            updatedMapLocation = location.add(smallerDirections[i]);
            
            //can sense is 5, is location occupied is 5, sensePassibility is 5
            if(updatedMapLocation.equals(targetLocation) || (rc.canSenseLocation(updatedMapLocation) 
                                                            && !rc.isLocationOccupied(updatedMapLocation) 
                                                            && rc.sensePassability(updatedMapLocation)
                                                            )) {
                output[i] = new MapNode(updatedMapLocation, this);
            } else {
                output[i] = null;
            }
        }

        return output;
    }

    public boolean isBadCurrent(RobotController rc, Direction directionTo, Direction currentDirection) {
       switch(directionTo) {
        case CENTER: return true;
        case EAST: return currentDirection.equals(Direction.WEST);
        case NORTH: return currentDirection.equals(Direction.SOUTH);
        case NORTHEAST: return currentDirection.equals(Direction.SOUTH) || currentDirection.equals(Direction.WEST);
        case NORTHWEST: return currentDirection.equals(Direction.SOUTH) || currentDirection.equals(Direction.EAST);
        case SOUTH: return currentDirection.equals(Direction.NORTH);
        case SOUTHEAST: return currentDirection.equals(Direction.NORTH) || currentDirection.equals(Direction.WEST);
        case SOUTHWEST: return currentDirection.equals(Direction.NORTH) || currentDirection.equals(Direction.EAST);
        case WEST: return currentDirection.equals(Direction.EAST);
        default: return true;
       }
    }

    /**
     * 
     * @param dir
     * @return
     */

    public Direction turn180Degrees(Direction dir) {
        return dir.rotateRight().rotateRight().rotateRight().rotateRight();
    }

   

    /**
     * computes F, 
     * @param targetLocation
     */
    public void computeF(MapLocation targetLocation) {
        h = Math.max(Math.abs(targetLocation.x - location.x), Math.abs(targetLocation.y - location.y));
        f = g + h;
    }

    public String toString() {
        return "(" + getF() + "," + location.toString() + ")";
    }

    @Override
    public int compareTo(MapNode o) {
        return this.f - o.f;
    }

    

    @Override
    public boolean equals(Object o) {
        MapNode node = (MapNode)o;
        return node.location.equals(this.location);
    }  
}
