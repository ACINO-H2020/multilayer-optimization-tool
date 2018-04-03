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
import com.net2plan.interfaces.networkDesign.SharedRiskGroup;
import com.net2plan.libraries.GraphUtils;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Triple;
import es.upct.girtel.net2plan.plugins.onos.utils.RouteList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class IP_opt_7 implements IAlgorithm {

    //K = 5
    private InputParameter kParm = new InputParameter("K", (int) 5, "alternative paths to try for each demand", 1, 100);
    //iterParm = 10
    private InputParameter iterParm = new InputParameter("ITER", (int) 1, "number of passes through the routes", 1, 1000);
    // rParm = 1
    private InputParameter rParm = new InputParameter("R", (int) 1, "number of restarts in the search", 1, 1000);
    // demandOrder = initial
    private InputParameter demandOrder = new InputParameter("demandOrder", "#select# initial increasing decreasing random", "The order to go through the demands");
    // seed = -1
    private InputParameter seedParm = new InputParameter("seed", (long) -1, "Seed of the random number generator (-1 for an arbitrary one)");

    /* From CN code */
    private int K_ipLayer, ITER_ipLayer, R_ipLayer;
    private double PRECISION_FACTOR;

    @Override
    public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters) {
      /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
        //InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);
/*
        K_ipLayer = kParm.getInt();
        ITER_ipLayer = iterParm.getInt();
        R_ipLayer = rParm.getInt();
*/
        K_ipLayer = 5;
        ITER_ipLayer = 10;
        R_ipLayer = 1;
        PRECISION_FACTOR = 1e-6;

        double initialCost = costFunc(netPlan);
        double restartCost;
        List<Pair<Route, List<Link>>> orderedListOfChanges = new ArrayList<Pair<Route, List<Link>>>(); /*the list of changes*/
        List<Pair<Route, List<Link>>> bestOrderedListOfChanges = new ArrayList<Pair<Route, List<Link>>>(); /*the best list of changes (after multiple restarts)*/
        Set<Route> emptyRoutes = new HashSet<Route>();
        Map<Route, List<Link>> EmptyRouteChanges = new HashMap<Route, List<Link>>();
        Map<Route, List<Link>> bestEmptyRouteChanges = new HashMap<Route, List<Link>>();

        System.out.println("initial cost = " + initialCost);

        DoubleMatrix1D demandMatrix1D = netPlan.getVectorDemandOfferedTraffic(netPlan.getNetworkLayer(1));
        int[] demandIndexesOrdered = new int[netPlan.getDemands(netPlan.getNetworkLayer(1)).size()];
        for (int i = 0; i < demandIndexesOrdered.length; i++)
            demandIndexesOrdered[i] = i;

        NetPlan working = new NetPlan();
        double bestCost = initialCost;


        /*
        if (demandOrder.getString().equals("increasing"))
            demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.ASCENDING);
        else if (demandOrder.getString().equals("decreasing"))
            demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.DESCENDING);
        else if (!demandOrder.getString().equals("initial") && !demandOrder.getString().equals("random"))
            throw new Net2PlanException("order of demands not specified correctly");
    */


        Random rnd;
        long seed = -1;
        rnd = (seed == -1) ? new Random() : new Random(seed);
      
      /*for each blocked demand, set up a route with zero traffic. We will move its path around*/
        NetworkLayer inIpLayer = netPlan.getNetworkLayer(1);
      
      /*remove any empty routes from netPlan, in case the netPlan has them by accident/bug or because they have failed after a link failure*/
        netPlan.removeAllRoutesUnused(PRECISION_FACTOR, inIpLayer);

        Map<Link, Double> linkCostMap_electronic = new HashMap<>();
        for (Link link : netPlan.getLinksUp(inIpLayer)) linkCostMap_electronic.put(link, 1.0); /*set all link weights to 1*/
        List<Demand> blockedDemands = netPlan.getDemandsBlocked(inIpLayer);
        System.out.println("blocked demands: " + blockedDemands.size() + " traffic: " + netPlan.getDemandTotalBlockedTraffic(inIpLayer));

        final int numberOfInitialRoutes = netPlan.getRoutes(inIpLayer).size();

        for (Demand blockedDemand : blockedDemands) {
          /* Ignore demands requiring protection */
            if (Boolean.parseBoolean(blockedDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE)) == true)
                continue;
            List<Link> IPpath = GraphUtils.getShortestPath(
                    netPlan.getNodes(),
                    netPlan.getLinksUp(inIpLayer),
                    blockedDemand.getIngressNode(),
                    blockedDemand.getEgressNode(),
                    linkCostMap_electronic);
            if (!IPpath.isEmpty()){
                netPlan.addRoute(blockedDemand, 0, 0, IPpath, blockedDemand.getAttributes());
            } else {
                System.out.println("no path in the IP topology for this node pair!");
              /*throw new Net2PlanException("IP topology is not connected!");*/
            }

        }

        for (int restart = 1; restart <= R_ipLayer; restart++) {
          /*restart: copy netPlan to working*/
            working.copyFrom(netPlan);
            NetworkLayer wIpLayer = working.getNetworkLayer(1);

/*            if (demandOrder.getString().equals("random"))
                RandomUtils.shuffle(demandIndexesOrdered, rnd);
*/
            restartCost = initialCost;
            orderedListOfChanges.clear();
            for (int iter = 1; iter <= ITER_ipLayer || ITER_ipLayer == -1; iter++) {
                boolean at_least_one_route_changed = false;
                for (int i = 0; i < demandIndexesOrdered.length; i++) {
                    Demand wDemand = working.getDemand(demandIndexesOrdered[i], wIpLayer);
                    for (Route wRoute : wDemand.getRoutes()) {
                        if (wRoute.getCarriedTraffic() <= PRECISION_FACTOR) continue;
		        	 /* Ignore routes with protection */
                        if (wRoute.getPotentialBackupProtectionSegments().size() > 0) continue;

                     /* Path to restore if no better found */
                        List<Link> initialPath = wRoute.getSeqLinksRealPath();
                     /*remove this path by setting its traffic to zero*/
                        double initialTraffic = wRoute.getCarriedTraffic();
                        double initialOccupiedCapacity = wRoute.getOccupiedCapacity();
                        wRoute.setCarriedTraffic(0, 0); /*we need to remove the traffic to find K shortest paths*/
			         
			         /* Find K alternative paths */
                        final List<List<Link>> path_list = findApplicationAwareCandidatePaths
                                (working, wRoute.getDemand(), initialTraffic, K_ipLayer);

                        wRoute.setCarriedTraffic(initialTraffic, initialOccupiedCapacity); /*put the traffic back on*/
			         
			         /* Check if any of the alternatives is better */
                        boolean changeFound = false;
                        List<Link> bestPath = initialPath;
                        Map<Route, List<Link>> bestNewPaths = new HashMap<Route, List<Link>>();
			         /*get the empty routes*/
                        emptyRoutes.clear();
                        for (Route r : working.getRoutes(wIpLayer))
                            if (r.getCarriedTraffic() == 0)
                                emptyRoutes.add(r);
                        for (List<Link> candidate : path_list) {
			        	/*activate this path*/
                            wRoute.setSeqLinksAndProtectionSegments(candidate);
			        	/*route the empty routes (change path). Take any path found. Assign all blocked traffic for its demand to it.*/
                            for (Route r : emptyRoutes) {
                                List<List<Link>> emptyRoutesPathList = findApplicationAwareCandidatePaths
                                        (working, r.getDemand(), r.getDemand().getBlockedTraffic(), 1); /*we only need 1 new route*/
                                if (!emptyRoutesPathList.isEmpty()) {
                                    r.setSeqLinksAndProtectionSegments(emptyRoutesPathList.get(0));
                                    r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
                                    System.out.println("new route found for route " + r.getIndex());
                                }
                            }
                            if (!emptyRoutes.isEmpty())
                                System.out.println("emptyRoutes: " + emptyRoutes.size());
                            double cost = costFunc(working);
                            if (cost < restartCost) {
                                changeFound = true;
                                restartCost = cost;
                                bestPath = candidate;
			               /*store best new paths for empty Routes*/
                                bestNewPaths.clear();
                                for (Route r : emptyRoutes)
                                    if (r.getCarriedTraffic() > 0)
                                        bestNewPaths.put(r, r.getSeqLinksRealPath());
                            }
			            /*set new Routes traffic to zero*/
                            for (Route r : emptyRoutes) r.setCarriedTraffic(0, 0);
                        }
                        if (changeFound) /*add this change to the list of changes*/ {
                            at_least_one_route_changed = true;
			        	 /*if this route is initially unrouted*/
                            if (wRoute.getIndex() >= numberOfInitialRoutes) {
			        		 /*update the list of newly added paths*/
                                EmptyRouteChanges.put(wRoute, bestPath);
                            } else /*add to the list of Changes*/ {
                                Pair<Route, List<Link>> pair = new Pair<Route, List<Link>>(wRoute, bestPath, false);
                                orderedListOfChanges.add(pair);
                            }
			        	 /*add any newly added (bestNewPaths) to the list of newly added paths (EmptyRouteChanges)*/
                            EmptyRouteChanges.putAll(bestNewPaths);
                        }
			         /*put the best path on*/
                        wRoute.setSeqLinksAndProtectionSegments(bestPath);
			         /*put best new paths on:
			         for each route in bestNewPaths, set its traffic equal to its demand blocked traffic*/
                        for (Map.Entry<Route, List<Link>> entry : bestNewPaths.entrySet()) {
                            Route r = entry.getKey();
                            List<Link> path = entry.getValue(); /* we do need the path*/
                            r.setSeqLinksAndProtectionSegments(path);
                            r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
                        }
                    }
                }
                if (!at_least_one_route_changed) break;
            }
	      /*if this cost is better than the best so far, copy the changes to the best list of changes*/
            if (restartCost < bestCost) {
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
      /*debug if*/
        if (working.getLinksOversubscribed(working.getNetworkLayer(1)).size() > 0) {
            for (Link oversubscribedLinks : working.getLinksOversubscribed(working.getNetworkLayer(1)))
                System.out.println("IP link utilization:" + oversubscribedLinks.getUtilizationNotIncludingProtectionSegments());
            throw new Net2PlanException("working netPlan is oversubscribed (1)");
        }

        System.out.println("final cost = " + bestCost);
	  /*in the original NetPlan, make the changes of Routes*/
        System.out.println("number of Routes to change = " + bestOrderedListOfChanges.size());
        for (Pair<Route, List<Link>> p : bestOrderedListOfChanges) {
            Route wRoute = p.getFirst();
            Route inRoute = netPlan.getRouteFromId(wRoute.getId());
           /*look up this Route in netPlan*/
            if (inRoute == null)
                throw new Net2PlanException("Route does not exist");
		  /*set the new path for this Route*/
            List<Link> wLinks = p.getSecond();
            List<Link> inLinks = new ArrayList<Link>();
            for (Link link : wLinks) {
                Link inLink = netPlan.getLinkFromId(link.getId());
                if (inLink == null)
                    throw new Net2PlanException("Link did not exist in the original netplan!");
                inLinks.add(inLink);
            }
            inRoute.setSeqLinksAndProtectionSegments(inLinks);
	    System.out.println("######################");
            System.out.println("Demand  " + inRoute.getDemand() + " should be MOVED to path " + inLinks);
            System.out.println("######################");
            RouteList.getInstance().addRoute(new Pair("MOVE",inRoute,false));
           /*debug if*/
            if (netPlan.getLinksOversubscribed(inIpLayer).size() > 0) {
                for (Link l : netPlan.getLinksOversubscribed(inIpLayer))
                    System.out.println("linkID: " + l.getIndex() + " traffic " + l.getCarriedTrafficIncludingProtectionSegments());
                throw new Net2PlanException("" + netPlan.getLinksOversubscribed(inIpLayer).size() + " link has utilization > 1");
            }
        }
	  /*apply the newly found paths*/
        System.out.println("added new Paths = " + bestEmptyRouteChanges.size());
        for (Map.Entry<Route, List<Link>> entry : bestEmptyRouteChanges.entrySet()) {
            Route wRoute = entry.getKey();
            Route inRoute = netPlan.getRouteFromId(wRoute.getId());
            System.out.println("now adding route " + wRoute.getIndex());
       
          if (inRoute == null)
                throw new Net2PlanException("(empty) Route does not exist");
            if (working.getLinksOversubscribed(working.getNetworkLayer(1)).size() > 0)
                throw new Net2PlanException("working netPlan is oversubscribed");
            List<Link> wLinks = wRoute.getSeqLinksRealPath();
            List<Link> inLinks = new ArrayList<Link>();
            for (Link link : wLinks) {
                Link inLink = netPlan.getLinkFromId(link.getId());
                if (inLink == null)
                    throw new Net2PlanException("Link did not exist in the original netplan!");
                inLinks.add(inLink);
            }
            inRoute.setSeqLinksAndProtectionSegments(inLinks);
            System.out.println("######################");
            System.out.println("Demand  " + inRoute.getDemand() + " , ROUTE to path " + inLinks);
            System.out.println("######################");
            RouteList.getInstance().addRoute(new Pair("ROUTE",inRoute,false));

            /*set the traffic on*/
            inRoute.setCarriedTraffic(wRoute.getCarriedTraffic(), wRoute.getOccupiedCapacity());

            if (netPlan.getLinksOversubscribed(inIpLayer).size() > 0) {
                for (Link l : netPlan.getLinksOversubscribed(inIpLayer))
                    System.out.println("linkID: " + l.getIndex() + " traffic " + l.getCarriedTrafficIncludingProtectionSegments());
                throw new Net2PlanException("" + netPlan.getLinksOversubscribed(inIpLayer).size() + " link has utilization > 1");
            }
        }
	  /*remove routes that did not get a path*/
        netPlan.removeAllRoutesUnused(PRECISION_FACTOR, inIpLayer);
        if (netPlan.getLinksOversubscribed(inIpLayer).size() > 0)
            throw new Net2PlanException("IP_OPT: " + netPlan.getLinksOversubscribed(inIpLayer).size() + " links has utilization > 1");
        return Double.toString(bestCost);
    }

    @Override
    public String getDescription() {
        return "Simple IP topology optimisation algorithm. By Ciril Rozic and Chris Matrakidis, AIT, 2016";
    }

    @Override
    public List<Triple<String, String, String>> getParameters() {
      /* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
        return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
    }

    /* Function to find a set of candidate paths meeting all the application requirements */
    private List<List<Link>> findApplicationAwareCandidatePaths(NetPlan currentNetPlan,
                                                                Demand ipDemand, double bandwidthInGbps, int K) {
        final Node ingressNode = ipDemand.getIngressNode();
        final Node egressNode = ipDemand.getEgressNode();

        final NetworkLayer wdmLayer = currentNetPlan.getNetworkLayer(0);
        final NetworkLayer ipLayer = currentNetPlan.getNetworkLayer(1);

      /* Application awareness properties */
        int wdmClass = Integer.parseInt(ipDemand.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE));
        double maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
        double minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE)) / 100;

      /* Search IP links violating bandwidth constraint */
        final List<Node> nodes = currentNetPlan.getNodes();
        final Set<Link> links = currentNetPlan.getLinksUp(ipLayer);

      /* Build the auxiliary graph from existing IP layer */
        Map<Link, Double> linkCostMap = new HashMap<Link, Double>();
        List<Link> aux = new ArrayList<Link>();
        for (final Link ipLink : links) {
            String wdmClassString = ipLink.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE);
            if (wdmClassString != null) {
                int wdmClassIpLink = Integer.parseInt(wdmClassString);
                if (wdmClass != wdmClassIpLink) {
                    continue;
                }
            }
            if (bandwidthInGbps >= ipLink.getCapacity() - ipLink.getCarriedTrafficNotIncludingProtectionSegments() - ipLink.getReservedCapacityForProtection() + PRECISION_FACTOR) {
                continue;
            }
            aux.add(ipLink);
            linkCostMap.put(ipLink, 1.0);
        }

        double maxPropDelayInMs = maxLatencyInMs;

      /* Disable other constraints */
        double maxLengthInKm = -1;
        int maxNumHops = -1;
        double maxRouteCost = -1;
        double maxRouteCostFactorRespectToShortestPath = -1;
        double maxRouteCostRespectToShortestPath = -1;

        final List<List<Link>> candidatePaths = GraphUtils.getKLooplessShortestPaths(nodes, aux, ingressNode,
                egressNode, linkCostMap, K, maxLengthInKm, maxNumHops, maxPropDelayInMs, maxRouteCost,
                maxRouteCostFactorRespectToShortestPath, maxRouteCostRespectToShortestPath);
        List<List<Link>> finalPaths = new ArrayList<List<Link>>();
        for (final List<Link> path : candidatePaths) {
            Set<SharedRiskGroup> srgs = new HashSet<SharedRiskGroup>();
            for (final Link tLink : path) {
                String doNotDeleteMeString = tLink.getAttribute("do not delete me");
                if (Boolean.parseBoolean(doNotDeleteMeString)) continue; /*these IP links have no coupled optical demands*/
                //final Route tRoute_test = currentNetPlan.getRoute(tLink.getIndex(), wdmLayer);
                Demand d = tLink.getCoupledDemand();
                for (Route tRoute : d.getRoutes())
                    srgs.addAll(tRoute.getSRGs());
            }
            double availability_thisPath = 1;
            for (SharedRiskGroup srg : srgs) {
                availability_thisPath *= srg.getAvailability();
            }
            if (availability_thisPath >= minAvailability) {
                finalPaths.add(path);
            }
        }
        return finalPaths;
    }

    private double costFunc(NetPlan netPlan) {
        final NetworkLayer wdmLayer = netPlan.getNetworkLayer(0);
        final NetworkLayer ipLayer = netPlan.getNetworkLayer(1);
        int L0 = netPlan.getNumberOfLinks(wdmLayer);
        int ch = 80;
        double maxTx = netPlan.getVectorLinkCapacity(wdmLayer).getMaxLocation()[0];
        double C1 = L0 * ch * maxTx * maxTx;
        double C0 = C1 * L0 * ch;
        final DoubleMatrix1D t = netPlan.getVectorLinkTotalCarriedTraffic(ipLayer);
        final DoubleMatrix1D p = netPlan.getVectorLinkCapacityReservedForProtection(ipLayer);
      /*add reserved capacity to traffic to get used link capacity*/
        for (int i = 0; i < t.size(); i++)
            t.set(i, t.get(i) + p.get(i));
        double x = netPlan.getVectorDemandBlockedTraffic(ipLayer).zSum();
//      int y = t.cardinality();
        int y = 0;
        for (double q : t.toArray()) {
            if (q > 1e-6) {
                y++;
            }
        }
        double z = t.zDotProduct(t);

        return C0 * x + C1 * y + z;
    }

}
