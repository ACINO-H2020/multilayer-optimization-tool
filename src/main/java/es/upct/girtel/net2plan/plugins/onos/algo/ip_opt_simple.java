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

package es.upct.girtel.net2plan.plugins.onos.algo;

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
import com.net2plan.utils.RandomUtils;
import com.net2plan.utils.Triple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

//import java.util.Collections;

public class ip_opt_simple implements IAlgorithm
{
   private InputParameter kParm = new InputParameter ("K", (int) 5, "Alternative paths to try for each route", 1, 100);
   private InputParameter routeOrder = new InputParameter ("demandOrder", 
      "#select# initial increasing decreasing random", "The order to go through the routes");
   private InputParameter seedParm = new InputParameter ("seed", (long) -1 , 
      "Seed of the random number generator (-1 for an arbitrary one)");
   /* From CN code */
   private int K_ipLayer;
   private double PRECISION_FACTOR;
   private boolean initialised = false;

   @Override
   public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, 
      Map<String, String> net2planParameters)
   {
      if (!initialised)
      {
         initialised = true;
         /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
         InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

         K_ipLayer = kParm.getInt();
         PRECISION_FACTOR = 0;
      }

      final NetworkLayer inIpLayer = netPlan.getNetworkLayer(1);

      /*remove any empty routes from netPlan, in case the netPlan has them by accident/bug*/
      netPlan.removeAllRoutesUnused(0, inIpLayer);

      int [] routeIndexesOrdered = new int [netPlan.getRoutes(inIpLayer).size()];
      for (int i = 0; i < routeIndexesOrdered.length; i++) 
         routeIndexesOrdered [i] = i;

      /* Change order of routes */
      if (routeOrder.getString ().equals("random"))
      {
         Random rnd;
         long seed = seedParm.getLong();
         rnd = (seed == -1) ? new Random() : new Random(seed);
         RandomUtils.shuffle(routeIndexesOrdered, rnd);
      }
      else if (routeOrder.getString().equals("increasing"))
      {
         DoubleMatrix1D routeMatrix1D = netPlan.getVectorRouteCarriedTraffic(inIpLayer);
         routeIndexesOrdered = DoubleUtils.sortIndexes(routeMatrix1D.toArray(), OrderingType.ASCENDING);
      }
      else if (routeOrder.getString().equals("decreasing"))
      {
         DoubleMatrix1D routeMatrix1D = netPlan.getVectorRouteCarriedTraffic(inIpLayer);
         routeIndexesOrdered = DoubleUtils.sortIndexes(routeMatrix1D.toArray(), OrderingType.DESCENDING);
      }
      else if (!routeOrder.getString().equals("initial"))
      {
        throw new Net2PlanException("Ordering of routes not specified correctly");
      }

      final int numberOfInitialRoutes = netPlan.getRoutes(inIpLayer).size();

      /*for each blocked demand, set up a route with zero traffic. We will move its path around*/
      List<Demand> blockedDemands = netPlan.getDemandsBlocked(inIpLayer);
      System.out.println("blocked demands: " + blockedDemands.size() + " traffic: " 
         + netPlan.getDemandTotalBlockedTraffic(inIpLayer));

      for (Demand blockedDemand : blockedDemands)
      {
         List<Link> IPpath = GraphUtils.getShortestPath(netPlan.getNodes(), netPlan.getLinks(inIpLayer), 
            blockedDemand.getIngressNode(), blockedDemand.getEgressNode(), null);
         if (!IPpath.isEmpty())
            netPlan.addRoute(blockedDemand, 0, 0, IPpath, blockedDemand.getAttributes());
         else
            System.out.println("no path in the IP topology for this node pair!");
      }

      NetPlan working = netPlan.copy();
      NetPlan best = null;
      double bestCost = costFunc(netPlan);
      final NetworkLayer wIpLayer = working.getNetworkLayer(1);

      System.out.println("initial cost = " + bestCost);

      /*get the empty routes*/
      Set<Route> emptyRoutes = new HashSet<Route>();
      for (Route r : working.getRoutes(wIpLayer))
         if (r.getCarriedTraffic() == 0) 
            emptyRoutes.add(r);

      for (int i = 0; i < routeIndexesOrdered.length; i++)
      {
         Route route = working.getRoute(routeIndexesOrdered[i], wIpLayer);
         /* Path to restore if no better found */
         List<Link> bestPath = route.getSeqLinksRealPath();

         Map<Route, List<Link>> EmptyRouteChanges = new HashMap <Route, List<Link>>();
         Map<Route, List<Link>> bestEmptyRouteChanges = new HashMap <Route, List<Link>>();

         final Demand current = route.getDemand();
         double currentSize = route.getCarriedTraffic();
         Set<Route> routes = current.getRoutes();

         double carriedTraffic = route.getCarriedTraffic();
         double occupiedCapacity = route.getOccupiedCapacity();
         route.setCarriedTraffic(0, 0);

         /* Find K alternative paths */
         final List<List<Link>> path_list = findApplicationAwareCandidatePaths(working, current, 
            carriedTraffic, K_ipLayer);

         /* Check if any of the alternatives is better */
         for (final List<Link> candidate : path_list)
         {
            route.setSeqLinksAndProtectionSegments(candidate);
            route.setCarriedTraffic(carriedTraffic, occupiedCapacity);

            /* Try to route the empty routes (change path). Greedyalgorithm.
               Take any path found. Assign all blocked traffic for its demand to it.*/
            EmptyRouteChanges.clear();
            for (Route r : emptyRoutes)
            {
               Demand d = r.getDemand();
               List <List<Link>> emptyRoutesPathList = findApplicationAwareCandidatePaths(working, d, 
                  d.getBlockedTraffic(), 1);
               if (!emptyRoutesPathList.isEmpty())
               {
                  r.setSeqLinksAndProtectionSegments(emptyRoutesPathList.get(0));
                  r.setCarriedTraffic(d.getBlockedTraffic(), d.getBlockedTraffic());
                  EmptyRouteChanges.put(r, emptyRoutesPathList.get(0));
                  /*System.out.println("new route found for route " + r.getIndex());*/
               }
            }

            double cost = costFunc(working);
            if (cost < bestCost)
            {
               best = working.copy();
               bestCost = cost;
               bestPath = candidate;
               /*save the best newly created Routes*/
               bestEmptyRouteChanges.clear();
               bestEmptyRouteChanges.putAll(EmptyRouteChanges);
            }
            route.setCarriedTraffic(0, 0);
            if (!EmptyRouteChanges.isEmpty())
               for (Route r : emptyRoutes)
                  r.setCarriedTraffic(0, 0);
         }

         /* Restore original path or better one */
         route.setSeqLinksAndProtectionSegments(bestPath);
         route.setCarriedTraffic(carriedTraffic, occupiedCapacity);
         for (Map.Entry <Route, List<Link>> entry : bestEmptyRouteChanges.entrySet())
         {
            Route r = entry.getKey();
            List<Link> path = entry.getValue();
            r.setSeqLinksAndProtectionSegments(path);
            r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
            emptyRoutes.remove(r);
         }
      }
      System.out.println("final cost = " + bestCost);

      /*in the original NetPlan, modify the routes accordingly*/
      if (best != null)
      {
         final NetworkLayer ipLayer = netPlan.getNetworkLayer(1);
         final NetworkLayer bIpLayer = best.getNetworkLayer(1);
         final Demand demands[] = netPlan.getDemands(ipLayer).toArray(new Demand[0]);
         final Link links[] = netPlan.getLinks(ipLayer).toArray(new Link[0]);
         netPlan.removeAllRoutes(ipLayer);
         best.removeAllRoutesUnused(0, bIpLayer);
         for (Route bRoute : best.getRoutes(bIpLayer))
         {
            List<Link> path = new ArrayList<Link>();
            for (Link bLink : bRoute.getSeqLinksRealPath())
            {
               path.add(links[bLink.getIndex()]);
            }
            // TODO?: copy attributes 
            int idx = bRoute.getDemand().getIndex();
            netPlan.addRoute(demands[idx], bRoute.getCarriedTraffic(), bRoute.getOccupiedCapacity(), path, null);
         }
      }

      /*remove any empty routes from netPlan*/
      netPlan.removeAllRoutesUnused(1e-6, inIpLayer);

      return Double.toString(bestCost);
   }

   @Override
   public String getDescription()
   {
      return "Simple IP topology optimisation algorithm. By Chris Matrakidis and Ciril Rozic, AIT, 2016";
   }

   @Override
   public List<Triple<String, String, String>> getParameters()
   {
      /* Returns the parameter information for all the InputParameter objects 
         defined in this object (uses Java reflection) */
      return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
   }

   /* Function to find a set of candidate paths meeting all the application requirements */
   private List<List<Link>> findApplicationAwareCandidatePaths(NetPlan currentNetPlan, 
      Demand ipDemand, double bandwidthInGbps, int K)
   {
      final Node ingressNode = ipDemand.getIngressNode(); 
      final Node egressNode = ipDemand.getEgressNode(); 

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
         if (bandwidthInGbps <= ipLink.getCapacity() - ipLink.getCarriedTrafficIncludingProtectionSegments()
            + PRECISION_FACTOR)
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
      final List<List<Link>> candidatePaths = GraphUtils.getKLooplessShortestPaths(nodes, aux, ingressNode, 
         egressNode, linkCostMap, K , maxLengthInKm, maxNumHops, maxPropDelayInMs, maxRouteCost,
         maxRouteCostFactorRespectToShortestPath, maxRouteCostRespectToShortestPath);

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
      double x = netPlan.getVectorDemandBlockedTraffic(ipLayer).zSum();
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
