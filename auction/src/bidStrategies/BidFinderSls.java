package bidStrategies;

import static enemy_estimation.EstimateCategory.NoIdea;
import static enemy_estimation.EstimateCategory.Unsure;

import java.util.List;
import java.util.Map;

import enemy_estimation.SingleEnemyEstimator;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import planning.Assignment;
import planning.InsertionAssignment;
import planning.SLSPlanFinder;
import template.CityTuple;
import template.DistributionTable;
import java.util.Random;

public class BidFinderSls extends AbstractBidFinder {
private static final boolean VERBOSE = false;
	
	private final double[] p_array = { 0.2, 0.4, 0.8, 1.0, 1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.45, 0.4, 0.4, 0.35, 0.3, 0.28, 0.26, 0.24, 0.22, 0.2,
	    0.19, 0.18, 0.17, 0.16, 0.15, 0.14, 0.13, 0.12, 0.11, 0.1, 0.09, 0.08, 0.05 };
			
	private double auctionNbr = 1;
	private final DistributionTable dt;
	private CityTuple[] ptasks;
	public final SingleEnemyEstimator mEnemyEstimator;
	private Agent mAgent;
	
	public final SLSPlanFinder mSLSPlanFinder;
	private final long mLowerBound;
	private final int mMaxCapacity;
	private double mExpectedTaskCost;
	
	public Assignment mPlan;
	private Assignment mPlanWithNewTask = null;
	
	private boolean bidMaxValue = false;
	
	private final long mBidTimeout;
	
	public BidFinderSls(List<Vehicle> vehicles, Agent agent, Topology topology, TaskDistribution distribution,
	    long bid_timeout) {
		super(vehicles, agent, topology, distribution);
		dt = new DistributionTable(topology, distribution);
		ptasks = dt.sortedCities;
		mEnemyEstimator = new SingleEnemyEstimator(1 - agent_id);
		mAgent = agent;
		mExpectedTaskCost = calcExpectedCost();
		mSLSPlanFinder = new SLSPlanFinder(vehicles, 100000, 0.5, bid_timeout);
		mMaxCapacity = findMaxCapacity(agent.vehicles());
		mLowerBound = Math.round(mExpectedTaskCost * 0.6);
		mPlan = new Assignment(vehicles);
		mBidTimeout = bid_timeout;
	}
	@Override
	public Long howMuchForThisTask(Task task) {
		if (task.weight > mMaxCapacity) {
			// the task is too heavy to be handled by our company.
			return null;
		}
		
		Long bid;
		if (auctionNbr == 1) {
			printIfVerbose("First auction, returning the expected task cost... ");
			
			// update the assignment withTask.
			InsertionAssignment emptyinsA = new InsertionAssignment(new Assignment(mVehicles));
			emptyinsA.insertTask(task);
			mPlanWithNewTask = emptyinsA.toSlsAssignment();
			
			double estimate = mExpectedTaskCost;
			bid = Math.round(estimate);
		} else {
			double p = calcP();
			Long enemy_estim = mEnemyEstimator.estimateBidForTask(task);
			Long ownBid = Math.max(ownBid_slsPlan(task), mLowerBound);
			switch (mEnemyEstimator.category) {
			case Extreemly_precise:
				p = Math.min(2*p, 0.95);
				enemy_estim = Math.round(enemy_estim*0.95);
				enemy_estim = Math.round(Math.max(enemy_estim, ownBid*0.8)); // make sure we can afford to bid a bit lower than enemy
				break;
				
			case Over:
				enemy_estim = Math.round(enemy_estim*0.8);
				break;
				
			case Under:
				// nothing to do
				break;
				
			case Unsure:
				p = p*0.6;
				break;
				
			case NoIdea:
				p = 0;
				break;
				
			default: // should never happen
				break;
			}
			
			if(ownBid == null){
				ownBid = Math.round(mExpectedTaskCost*1.5);
			}else if(enemy_estim == null || p == 0){
				enemy_estim = ownBid;
			}
			
			printIfVerbose("Enemy estim (%s): %d ", mEnemyEstimator.category.name(),enemy_estim);
			printIfVerbose("Own Bid: "+ownBid);
			printIfVerbose("p: "+p);
			double ownBidPart = (1 - p) * ownBid;
			double enemyestimPart = p * enemy_estim;
			
			bid = Math.round(ownBidPart + enemyestimPart);
			
			// The first time the enemy estimation is unsure we bid very high to potentially disturb the enemy
			if(!bidMaxValue && (mEnemyEstimator.category == Unsure || mEnemyEstimator.category == NoIdea)){
					bid = Math.round(Long.MAX_VALUE*0.79);
					bidMaxValue = true;
			}
			
			
		}
		return Math.max(bid, mLowerBound);
	}
	
	private Long ownBid_slsPlan(Task t) {
		
		long oldCost = mPlan.computeCost();
		InsertionAssignment insAss = new InsertionAssignment(mPlan.copy());
		insAss.insertTask(t);
		
		mSLSPlanFinder.setTimeout(Math.round(mBidTimeout*0.7));
		Assignment assWithT = mSLSPlanFinder.computeBestPlan(insAss.toSlsAssignment(), null);
		if(assWithT == null){
			throw new RuntimeException("assWithT is null");
		}
		
//		printIfVerbose("******************************************************************** ...");
//		printIfVerbose("Plan with new Task: "+assWithT.toString());
//		printIfVerbose("... ********************************************************************");
		
		
		long withCost = assWithT.computeCost();
		mPlanWithNewTask = assWithT;
		
		long diff = withCost - oldCost;
		printIfVerbose("Plan cost with new Task: %d, cost without: %d -> difference: %d.", withCost, oldCost, diff);
		
		return diff;
		
	}
	
	/**
	 * Calculates the expected cost of a task. Can be used as a soft lower bound
	 * to our bids
	 * 
	 * @return the expected cost of any task
	 */
	private Double calcExpectedCost() { // TODO include the weight / capacity
		double sum = 0;
		// average (weighted) distance (in km)
		double tmp = 0;
		for (CityTuple ct : ptasks) {
			sum += ct.proba * ct.from.distanceTo(ct.to);
			tmp += ct.proba;
		}
		// times the average cost per km of a vehicle
		sum *= calcAvgVehicCost();
		printIfVerbose("Expected cost of a task: " + sum);
		return sum;
	}
	
	/**
	 * 
	 * @return average vehicle cost / km.
	 */
	private double calcAvgVehicCost() {
		double sum = 0;
		for (Vehicle v : mAgent.vehicles()) {
			sum += v.costPerKm();
		}
		double res = sum / mAgent.vehicles().size();
		printIfVerbose("Average cost per km (vehicle): " + res);
		return res;
	}
	
	/**
	 * @param map
	 * @return the min of the maps value set.
	 */
	private Long min(Map<Integer, Long> map) {
		Long min = Long.MAX_VALUE;
		for (Long l : map.values()) {
			if (l < min) {
				min = l;
			}
		}
		return min;
	}
	
	/**
	 * 
	 * @return 1/(auctionsWon+1).
	 */
	private double calcP() {
		double p = p_array[Math.min(mAuctionsWon.size(), p_array.length - 1)];
		printIfVerbose("p value: " + p);
		return p;
	}
	
	@Override
	public void auctionLost(Task t, Long[] bids) {
		super.auctionLost(t, bids);
		auctionNbr++;
		this.mEnemyEstimator.auctionResult(bids, t);
	}
	
	@Override
	public void auctionWon(Task t, Long[] bids) {
		super.auctionWon(t, bids);
		auctionNbr++;
		this.mEnemyEstimator.auctionResult(bids, t);
		printIfVerbose("Auction Won!!");
		mPlanWithNewTask.replace(t);
		
		mPlan = mPlanWithNewTask;
//		printIfVerbose("=================");
//		printIfVerbose("New Plan: "+mPlan.toString());
//		printIfVerbose("=================");
		
	}
	
	public void summarize(){
		mEnemyEstimator.summarize();
		
	}
	
	public void printIfVerbose(String str, Object... objects) {
		printIfVerbose(String.format(str, objects));
	}
	
	/**
	 * prints s if the VERBOSE flag is set to true: </br>
	 * if(VERBOSE){ System.out.println(s); }
	 * 
	 * @param s
	 */
	public void printIfVerbose(String str) {
		if (VERBOSE) {
			System.out.println(new StringBuilder().append("  ").append("(bid-finder) ").append("agent(").append(mAgent.id())
			    .append("): ").append(str).toString());
			System.out.flush();
		}
	}
}
