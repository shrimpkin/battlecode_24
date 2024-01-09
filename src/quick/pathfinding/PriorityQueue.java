package quick.pathfinding;


public class PriorityQueue {
    //there are only 69 total tiles
    //so the max size is 69
    MapNode[] queue = new MapNode[69];
    int size = 0;

    public PriorityQueue() { // what
        // 
        // so this doesn't work 
        // but it is the stuff i copied ish?
    }

    public void add(MapNode e) {
        if (e == null)
            throw new NullPointerException();
        int i = size; 
        size = i + 1;
        if (i == 0)
            queue[0] = e;
        else
            siftUp(i, e);
    }

    public MapNode poll() {
       if (size == 0)
            return null;
        int s = --size;
        MapNode result =  queue[0];
        MapNode x =  queue[s];
        queue[s] = null;
        if (s != 0)
            siftDown(0, x);
        return result;
    }

    public boolean contains(MapNode node) {
        for(int i = 0; i <= size - 1; i++) {
            if(queue[i].equals(node)) {
                return true;
            }
        }
        return false;
    }

    private void siftUp(int k, MapNode x) {
        while (k > 0) {
            //black magic
            int parent = (k - 1) >>> 1;
            MapNode e = queue[parent];
            if (x.compareTo(e) >= 0)
                break;
            
            queue[k] = e;
            k = parent;
        }
        queue[k] = x;
    }
        
    private void siftDown(int k, MapNode x) {
        //black magic
        int half = size >>> 1;
        
        while (k < half) {
            //more black magic
            int child = (k << 1) + 1;
            MapNode c = queue[child];
            int right = child + 1;
            if (right < size && x.compareTo(c) > 0)

                //what is this line
                c = queue[child = right];
                if (x.compareTo(c) <= 0)
                    break;
                    
                queue[k] = c;
                k = child;
        }
            queue[k] = x;
    }

    public int size() {
        return size;
    }

}
