package template;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import logist.simulation.Vehicle;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class PickupBestFs extends PickupAstar {
  
  public PickupBestFs(State start, Vehicle vehicle, TaskSet tasks, boolean alwaysPickup) {
    super(start, vehicle, tasks, alwaysPickup);
  }

  @Override
  public double heuristic(SearchNode<State> s) {

    double maxW = 0;
    double maxC = 0;
    int nbrP = s.getState().getPackagePositions().size();
    int delivered = 0;
    int waiting = 0;
    int inDelivery = 0;
    
    for(Position pos : s.getState().getPackagePositions().values()) {
     
      City goal = pos.getGoal();
      double valW = 0;
      double valC = 0;
      if(pos.isInDelivery()) {
        valC = ((InDelivery) pos).vehicle.getCurrentCity().distanceTo(goal);
        inDelivery++;
      }
      else if(pos.isWaiting()) {
        valW = ((Waiting) pos).city.distanceTo(goal);
        waiting++;
      }
      else {
        delivered++;
      }
      maxW = valW > maxW ? valW : maxW;
      maxC = valC > maxC ? valC : maxC;
      
    }
    if(nbrP - delivered == 0){
      return 0;
    }
//    double max = (inDelivery > 0 ? maxC : maxW);
//    double max = Math.max(maxC, maxW);
    double max = maxC+maxW;
    return Math.max(max - Math.pow(4, delivered), 0);
  }
  
//  /**
//   * 
//   * @param s
//   * @return the number of packages that are already delivered in the given state.
//   */
//  private double nbrDelivered(SearchNode<State> s){
//    int counter = 0;
//    for(Position p : s.getState().getPackagePositions().values()){
//      if(p.isDelivered()){
//        counter++;
//      }
//    }
//    return counter;
//  }
  
  
  
  @Override
  public void insertOpen(SearchNode<State> k, Queue<SearchNode<State>> openList) {
    openList.add(k);
  }

  @Override
  public Queue<SearchNode<State>> initOpenList() {
    return new PriorityQueue<SearchNode<State>>(200000);
  }
  
}
