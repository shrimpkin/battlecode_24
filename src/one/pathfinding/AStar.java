package one.pathfinding;

import battlecode.common.*;

public class AStar {
// A* Search Algorithm
// 1.  Initialize the open list
// 2.  Initialize the closed list
//     put the starting node on the open 
//     list (you can leave its f at zero)

// 3.  while the open list is not empty
//     a) find the node with the least f on 
//        the open list, call it "q"

//     b) pop q off the open list
  
//     c) generate q's 8 successors and set their 
//        parents to q
   
//     d) for each successor
//         i) if successor is the goal, stop search
        
//         ii) else, compute both g and h for successor
//           successor.g = q.g + distance between 
//                               successor and q
//           successor.h = distance from goal to 
//           successor (This can be done using many 
//           ways, we will discuss three heuristics- 
//           Manhattan, Diagonal and Euclidean 
//           Heuristics)
          
//           successor.f = successor.g + successor.h

//         iii) if a node with the same position as 
//             successor is in the OPEN list which has a 
//            lower f than successor, skip this successor

//         iV) if a node with the same position as 
//             successor  is in the CLOSED list which has
//             a lower f than successor, skip this successor
//             otherwise, add  the node to the open list
//      end (for loop)
  
//     e) push q on the closed list
//     end (while loop)



    public static Direction getBestDirection(RobotController rc, MapLocation target) throws GameActionException {
        if(target.equals(rc.getLocation())) {
            return Direction.CENTER;
        }

        // java.util.PriorityQueue<MapNode> open = new java.util.PriorityQueue<MapNode>();
        // java.util.PriorityQueue<MapNode> closed = new java.util.PriorityQueue<MapNode>();
        PriorityQueue open = new PriorityQueue();
        PriorityQueue closed = new PriorityQueue();

        open.add(new MapNode(rc.getLocation()));
        
        MapNode finalNode = null;

        while(open.size() >= 0 && finalNode == null) {
            MapNode q = open.poll();
            MapNode[] successors = new MapNode[1];
            try{
                successors = q.generateSuccesors(rc, target);
            } catch(NullPointerException e) {
                //System.out.println("bug nav times");
                return Direction.CENTER;
            }
            

            for(int i = 0; i < 5; i++) {
                if(successors[i] != null) {
                    if(successors[i].location.equals(target)) {
                        finalNode = successors[i];
                        break;
                    }
    
                    if(!open.contains(successors[i]) && !closed.contains(successors[i])) {
                        successors[i].computeF(target);
                        open.add(successors[i]);
                    }
                }
            }
            closed.add(q);
        }

        if(finalNode == null) {
            return Direction.CENTER;
        } else {
            while(finalNode.parent.parent != null) {
                finalNode = finalNode.parent;
            }
            return finalNode.parent.location.directionTo(finalNode.location);
        }
    }

}