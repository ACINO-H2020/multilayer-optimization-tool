/*
 * Copyright (c) 2018 ACINO Consortium
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the Free Software Foundation License (version 3, or any later version).
 *
 * This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.libraries.GraphUtils;
import com.net2plan.utils.Constants.OrderingType;
import com.net2plan.utils.DoubleUtils;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.RandomUtils;
import com.net2plan.utils.Triple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class IP_opt_7_AA implements IAlgorithm
{
   private InputParameter kParm = new InputParameter ("K", (int) 5, "alternative paths to try for each demand", 1, 100);
   private InputParameter iterParm = new InputParameter ("ITER", (int) 1, "number of passes through the routes", 1, 1000);
   private InputParameter rParm = new InputParameter ("R", (int) 1, "number of restarts in the search", 1, 1000);
   private InputParameter demandOrder = new InputParameter ("demandOrder", "#select# initial increasing decreasing random", "The order to go through the demands");
   private InputParameter seedParm = new InputParameter ("seed", (long) -1 , "Seed of the random number generator (-1 for an arbitrary one)");
   
   /* From CN code */
   private int K_ipLayer, ITER_ipLayer, R_ipLayer;
   private double PRECISION_FACTOR;

   @Override
   public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters)
   {
      /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
      InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

      K_ipLayer = kParm.getInt();
      ITER_ipLayer = iterParm.getInt();
      R_ipLayer = rParm.getInt();
      PRECISION_FACTOR = 0;
      
      double initialCost = costFunc(netPlan);
      double restartCost;
      List<Pair <Route, List<Link>>> orderedListOfChanges = new ArrayList<Pair <Route, List<Link>>>(); /*the list of changes*/
      List<Pair <Route, List<Link>>> bestOrderedListOfChanges = new ArrayList<Pair <Route, List<Link>>>(); /*the best list of changes (after multiple restarts)*/
      Set<Route> emptyRoutes = new HashSet<Route> ();
      Map<Route, List<Link>> EmptyRouteChanges = new HashMap <Route, List<Link>>();
      Map<Route, List<Link>> bestEmptyRouteChanges = new HashMap <Route, List<Link>>();

      System.out.println("initial cost = " + initialCost);
      
      DoubleMatrix1D demandMatrix1D = netPlan.getVectorDemandOfferedTraffic(netPlan.getNetworkLayer(1));
      int [] demandIndexesOrdered = new int [netPlan.getDemands(netPlan.getNetworkLayer(1)).size()];
      for (int i = 0; i < demandIndexesOrdered.length; i++) 
    	  demandIndexesOrdered [i] = i;
            
      NetPlan working = new NetPlan();
      double bestCost = initialCost;
      
      if (demandOrder.getString ().equals("increasing"))
    	  demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.ASCENDING);
      else if (demandOrder.getString ().equals("decreasing"))
    	  demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.DESCENDING);
      else if (!demandOrder.getString ().equals("initial") && !demandOrder.getString ().equals("random")) 
    	  throw new Net2PlanException("order of demands not specified correctly");
      
      Random rnd;
      long seed = seedParm.getLong();
      rnd = (seed == -1) ? new Random() : new Random(seed);
      
      /*for each blocked demand, set up a route with zero traffic. We will move its path around*/
      NetworkLayer inIpLayer = netPlan.getNetworkLayer(1);
      
      /*remove any empty routes from netPlan, in case the netPlan has them by accident/bug*/
	  netPlan.removeAllRoutesUnused(0, inIpLayer);
      
      Map<Link, Double> linkCostMap_electronic = new HashMap<>();
      for (Link link : netPlan.getLinks(inIpLayer)) linkCostMap_electronic.put(link, 1.0); /*set all link weights to 1*/
      List<Demand> blockedDemands = netPlan.getDemandsBlocked(inIpLayer);
      System.out.println("blocked demands: " + blockedDemands.size()+ " traffic: " + netPlan.getDemandTotalBlockedTraffic(inIpLayer));
      
      final int numberOfInitialRoutes = netPlan.getRoutes(inIpLayer).size();
      
      for (Demand blockedDemand : blockedDemands)
      {	
    	  List<Link> IPpath = GraphUtils.getShortestPath(
				netPlan.getNodes(), 
				netPlan.getLinks(inIpLayer), 
				blockedDemand.getIngressNode(),
				blockedDemand.getEgressNode(), 
				linkCostMap_electronic);
    	  if (!IPpath.isEmpty())
			netPlan.addRoute(blockedDemand, 0, 0, IPpath, blockedDemand.getAttributes());
    	  else
    	  {
    		  System.out.println("no path in the IP topology for this node pair!");
    		  /*throw new Net2PlanException("IP topology is not connected!");*/
    	  }
    		  
      }
      for (int restart = 1 ; restart <= R_ipLayer; restart++)
      {
    	  /*restart: copy netPlan to working*/
    	  working.copyFrom(netPlan);
    	  NetworkLayer wIpLayer = working.getNetworkLayer(1);
    	  
    	  if (demandOrder.getString ().equals("random"))
    		  RandomUtils.shuffle(demandIndexesOrdered, rnd);
    	  	  
          restartCost = initialCost;
          orderedListOfChanges.clear();
	      for (int iter = 1 ; iter <= ITER_ipLayer; iter++)
	      {   
	    	  boolean at_least_one_route_changed = false;
			  for (int i = 0; i < demandIndexesOrdered.length; i++)
		      {
				 Demand wDemand = working.getDemand(demandIndexesOrdered [i], wIpLayer);
		         for (Route wRoute : wDemand.getRoutes())
		         {         
		        	 /* Path to restore if no better found */
			         List <Link> initialPath = wRoute.getSeqLinksRealPath();
			         /*remove this path by setting its traffic to zero*/
			         double initialTraffic = wRoute.getCarriedTraffic();
			         double initialOccupiedCapacity = wRoute.getOccupiedCapacity();
			         wRoute.setCarriedTraffic(0, 0); /*we need to remove the traffic to find K shortest paths*/
			         
			         /* Find K alternative paths */
			         final List<List<Link>> path_list = findApplicationAwareCandidatePaths
			        		 (working, wRoute.getIngressNode(), wRoute.getEgressNode(), initialTraffic);
			         
			         wRoute.setCarriedTraffic(initialTraffic, initialOccupiedCapacity); /*put the traffic back on*/
			         
			         /* Check if any of the alternatives is better */
			         boolean changeFound = false;
			         List <Link> bestPath = initialPath;
			         Map<Route, List<Link>> bestNewPaths = new HashMap <Route, List<Link>>();
			         /*get the empty routes*/
			         emptyRoutes.clear();
			         for (Route r : working.getRoutes(wIpLayer))
			        	 if (r.getCarriedTraffic() == 0 ) 
			        		 emptyRoutes.add(r);
			         for (List<Link> candidate : path_list)
			         {
			        	/*activate this path*/
			        	wRoute.setSeqLinksAndProtectionSegments(candidate);
			        	/*route the empty routes (change path). Take any path found. Assign all blocked traffic for its demand to it.*/	        	
			      	  	for (Route r : emptyRoutes)
			      	  	{
			      	  		List <List<Link>> emptyRoutesPathList = findApplicationAwareCandidatePaths
			      	  		 (working, r.getIngressNode(), r.getEgressNode(), r.getDemand().getBlockedTraffic());
			      	  		if (!emptyRoutesPathList.isEmpty())
			      	  		{
			      	  			r.setSeqLinksAndProtectionSegments(emptyRoutesPathList.get(0));
			      	  			r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
			      	  			/*System.out.println("new route found for route " + r.getIndex());*/
			      	  		}
			      	  	}
			      	  /*if (!emptyRoutes.isEmpty())
			    		  System.out.println("emptyRoutes: " + emptyRoutes.size());*/
			            double cost = costFunc(working);
			            if (cost < restartCost)
			            {
			               changeFound = true;
			               restartCost = cost;
			               bestPath = candidate;
			               /*store best new paths for empty Routes:
			               Add the paths of emptyRoutes that have nonzero traffic*/
							bestNewPaths.clear();
			               for (Route r : emptyRoutes)
			            	   if (r.getCarriedTraffic() > 0) 
			            		   bestNewPaths.put(r, r.getSeqLinksRealPath());
			            }   
			            /*set new Routes traffic to zero*/
			            for (Route r : emptyRoutes) r.setCarriedTraffic(0, 0);
			         }
			         if (changeFound) /*add this change to the list of changes*/
			         {
			        	 at_least_one_route_changed = true;
			        	 /*if this route is initially unrouted*/
			        	 if (wRoute.getIndex() >= numberOfInitialRoutes)
			        	 {
			        		 /*update the list of newly added paths*/
			        		 EmptyRouteChanges.put(wRoute, bestPath);
			        	 }
			        	 else /*add to the list of Changes*/
			        	 {
		  Route inRoute = netPlan.getRouteFromId(wRoute.getId());
		  /*look up this Route in netPlan*/
		  if (inRoute == null) 
			  throw new Net2PlanException("Route does not exist");
		  /*set the new path for this Route*/
		  List<Link> inLinks = new ArrayList<Link>();
		  for (Link link : bestPath) 
		  {
			  Link inLink = netPlan.getLinkFromId(link.getId());
			  if (inLink == null)
				  throw new Net2PlanException("Link did not exist in the original netplan!");
			  inLinks.add(inLink);
		  }
			        	 	Pair <Route, List<Link>> pair = new Pair <Route, List<Link>> (inRoute, inLinks, false);
			        	 	orderedListOfChanges.add(pair);
			        	 }
			        	 /*add any newly added (bestNewPaths) to the list of newly added paths (EmptyRouteChanges)*/
			        	 EmptyRouteChanges.putAll(bestNewPaths);
			         }
			         /*put the best path on*/
			         wRoute.setSeqLinksAndProtectionSegments(bestPath);
			         /*put best new paths on:
			         for each route in bestNewPaths, set its traffic equal to its demand blocked traffic*/
			         for (Map.Entry<Route, List<Link>> entry : bestNewPaths.entrySet())
			         {
			        	Route r = entry.getKey();
			        	List<Link> path = entry.getValue();
                                        r.setSeqLinksAndProtectionSegments(path);
			        	r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
			         }
		         }
		      }
			  if (!at_least_one_route_changed) break;
	      }
	      /*if this cost is better than the best so far, copy the changes to the best list of changes*/
	      if (restartCost < bestCost)
	      {
	    	  bestCost = restartCost;
	    	  bestOrderedListOfChanges.clear();
	    	  bestOrderedListOfChanges.addAll(orderedListOfChanges);
		      /*save the best newly created Routes*/
	    	  bestEmptyRouteChanges.clear();
	    	  bestEmptyRouteChanges.putAll(EmptyRouteChanges);
	    	  if (!bestEmptyRouteChanges.isEmpty())
	    		  System.out.println("bestEmptyRouteChanges: " + bestEmptyRouteChanges.size());
	      }
	      System.out.println("final cost for this restart = " + restartCost);
      }
      System.out.println("final cost = " + bestCost);
	  /*in the original NetPlan, make the changes of Routes*/
	  System.out.println("number of Routes to change = " + bestOrderedListOfChanges.size());
	  for (Pair <Route, List<Link>> p : bestOrderedListOfChanges)
	  {
		  Route inRoute = p.getFirst();
		  List<Link> inLinks = p.getSecond();
		  inRoute.setSeqLinksAndProtectionSegments(inLinks);
	  }
	  /*apply the newly found paths*/
if (false)
{
	  System.out.println("added new Paths = " + bestEmptyRouteChanges.size());
	  for (Map.Entry <Route, List<Link>> entry : bestEmptyRouteChanges.entrySet())
	  {
		  Route wRoute = entry.getKey();
		  Route inRoute = netPlan.getRouteFromId(wRoute.getId());
		  if (inRoute == null) 
			  throw new Net2PlanException("(empty) Route does not exist");
		  /*set the traffic on*/
		  inRoute.setCarriedTraffic(inRoute.getDemand().getBlockedTraffic(), inRoute.getDemand().getBlockedTraffic());
	  }
}
	  /*remove any empty routes from netPlan*/
	  netPlan.removeAllRoutesUnused(0, inIpLayer);
		  
	  if (netPlan.getDemandTotalBlockedTraffic(inIpLayer) > 0) return "some demands not accommodated";
	  else return "all demands accommodated";
   }

   @Override
   public String getDescription()
   {
      return "Simple IP topology optimisation algorithm. By Ciril Rozic and Chris Matrakidis, AIT, 2016";
   }

   @Override
   public List<Triple<String, String, String>> getParameters()
   {
      /* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
      return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
   }

   /* Function to find a set of candidate paths meeting all the application requirements */
   private List<List<Link>> findApplicationAwareCandidatePaths(NetPlan currentNetPlan, Node ingressNode, Node egressNode, double bandwidthInGbps)
   {
      final NetworkLayer ipLayer = currentNetPlan.getNetworkLayer(1);

      /* Application awareness properties - ignored for now */
//      boolean encrypted = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.ENCRYPTED_ATTRIBUTE));
//      double maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
//      double minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE));

      /* Search IP links violating bandwidth constraint */
      final List<Node> nodes = currentNetPlan.getNodes();
      final List<Link> links = currentNetPlan.getLinks(ipLayer);

      /* Build the auxiliary graph from existing IP layer */
      Map<Link,Double> linkCostMap = new HashMap<Link,Double>();
      List<Link> aux  = new ArrayList<Link>();
      for (final Link ipLink : links)
      {
         if (bandwidthInGbps <= ipLink.getCapacity() - ipLink.getCarriedTrafficIncludingProtectionSegments() + PRECISION_FACTOR)
         {
            aux.add(ipLink);
            linkCostMap.put(ipLink, 1.0);
            continue;
         }
      }

      /* Dummy values for now */
      double maxLengthInKm = 10000;
      int maxNumHops = 100;
      double maxPropDelayInMs = 10000;
      double maxRouteCost = 10000;
      double maxRouteCostFactorRespectToShortestPath = 1000;
      double maxRouteCostRespectToShortestPath = 1000;
      final List<List<Link>> candidatePaths = GraphUtils.getKLooplessShortestPaths(nodes, aux, ingressNode, egressNode, linkCostMap, K_ipLayer , maxLengthInKm, maxNumHops, maxPropDelayInMs, maxRouteCost, maxRouteCostFactorRespectToShortestPath, maxRouteCostRespectToShortestPath);

      return candidatePaths;
   } 

   private double costFunc(NetPlan netPlan)
   {
      final NetworkLayer wdmLayer = netPlan.getNetworkLayer(0);
      final NetworkLayer ipLayer = netPlan.getNetworkLayer(1);
      int L0 = netPlan.getNumberOfLinks(wdmLayer);
      int ch = 80;
      double maxTx = netPlan.getVectorLinkCapacity(wdmLayer).getMaxLocation()[0];
      double C1 = L0*ch*maxTx*maxTx;
      double C0 = C1*L0*ch;
      final DoubleMatrix1D t = netPlan.getVectorLinkTotalCarriedTraffic(ipLayer);
      double x = netPlan.getDemandTotalBlockedTraffic(ipLayer);
//      int y = t.cardinality();
      int y = 0;
      for (double q : t.toArray())
      {
         if (q > 1e-6)
         {
            y++;
         }
      }
      double z = t.zDotProduct(t);
      
      return C0*x + C1*y + z;
   }
   
   
}
