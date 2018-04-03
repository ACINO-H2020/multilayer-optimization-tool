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
import com.net2plan.interfaces.networkDesign.NetworkElement;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.ProtectionSegment;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.interfaces.networkDesign.SharedRiskGroup;
import com.net2plan.libraries.GraphUtils;
import com.net2plan.utils.Constants.OrderingType;
import com.net2plan.utils.DoubleUtils;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.RandomUtils;
import com.net2plan.utils.Triple;
import es.upct.girtel.net2plan.plugins.onos.utils.RouteList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class ip_opt_8 implements IAlgorithm {
    private InputParameter kParm = new InputParameter("K", (int) 5, "alternative paths to try for each demand");
    private InputParameter iterParm = new InputParameter("ITER", (int) 1, "number of passes through the routes, -1 to repeat as long as a pass improves the cost", -1, 999999);
    private InputParameter rParm = new InputParameter("R", (int) 1, "number of restarts in the search", 1, 1000);
    private InputParameter demandOrder = new InputParameter("demandOrder", "#select# initial increasing decreasing random", "The order to go through the demands");
    private InputParameter seedParm = new InputParameter("seed", (long) -1, "Seed of the random number generator (-1 for an arbitrary one)");
    private InputParameter hardOptParm = new InputParameter("hardOpt", false, "remove all IP routes before optimizing");

    /* From CN code */
    private int K_ipLayer, ITER_ipLayer, R_ipLayer;
    private double PRECISION_FACTOR;
    private int numberOfInitialRoutes; /*we want this global so we can check it in findApplicationAwarePaths()*/

    @Override
    public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters) {
      /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
        InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

        K_ipLayer = kParm.getInt();
        ITER_ipLayer = iterParm.getInt();
        R_ipLayer = rParm.getInt();
        Boolean hardOpt = hardOptParm.getBoolean();
        PRECISION_FACTOR = 1e-6;

        double initialCost = costFunc(netPlan);
        double restartCost;
        List<Pair<Route, List<Link>>> orderedListOfChanges = new ArrayList<>(); /*the list of changes*/
        List<Pair<Route, List<Link>>> bestOrderedListOfChanges = new ArrayList<>(); /*the best list of changes (after multiple restarts)*/
        Queue<List<Link>> ProtSegChanges = new ArrayDeque<>();
        Queue<List<Link>> bestProtSegChanges = new ArrayDeque<>();
        Map<Route, List<Link>> bestEmptyRouteChanges = new HashMap<>();

        System.out.println("initial cost = " + initialCost);

        DoubleMatrix1D demandMatrix1D = netPlan.getVectorDemandOfferedTraffic(netPlan.getNetworkLayer(1));
        int[] demandIndexesOrdered = new int[netPlan.getDemands(netPlan.getNetworkLayer(1)).size()];
        for (int i = 0; i < demandIndexesOrdered.length; i++)
            demandIndexesOrdered[i] = i;

        NetPlan working = new NetPlan();
        double bestCost = initialCost;

        if (demandOrder.getString().equals("increasing"))
            demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.ASCENDING);
        else if (demandOrder.getString().equals("decreasing"))
            demandIndexesOrdered = DoubleUtils.sortIndexes(demandMatrix1D.toArray(), OrderingType.DESCENDING);
        else if (!demandOrder.getString().equals("initial") && !demandOrder.getString().equals("random"))
            throw new Net2PlanException("order of demands not specified correctly");

        Random rnd;
        long seed = seedParm.getLong();
        rnd = (seed == -1) ? new Random() : new Random(seed);
      
      /*for each blocked demand, set up a route with zero traffic. We will move its path around*/
        NetworkLayer inIpLayer = netPlan.getNetworkLayer(1);
      /*remove any empty routes from netPlan, in case the netPlan has them by accident/bug or because they have failed after a link failure*/
        // TODO: Pontus, this is probably not expected behaviour?
        // Or ar these "floating" routes without associated demands?
        netPlan.removeAllRoutesUnused(PRECISION_FACTOR, inIpLayer);

        Map<Link, Double> linkCostMap_electronic = new HashMap<>();
        for (Link link : netPlan.getLinksUp(inIpLayer)) linkCostMap_electronic.put(link, 1.0); /*set all link weights to 1*/
        List<Demand> blockedDemands = netPlan.getDemandsBlocked(inIpLayer);
        System.out.println("blocked demands: " + blockedDemands.size() + " traffic: " + netPlan.getDemandTotalBlockedTraffic(inIpLayer));

        numberOfInitialRoutes = netPlan.getRoutes(inIpLayer).size();

        for (Demand blockedDemand : blockedDemands) {
          /* Ignore demands requiring protection */
            if (Boolean.parseBoolean(blockedDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE))) {
                if (hardOpt) {
                    throw new Net2PlanException("IPOPT cannot yet create new protected routes, so do not drop all routes!");
                } else continue;
            }
            List<Link> IPpath = GraphUtils.getShortestPath(
                    netPlan.getNodes(),
                    netPlan.getLinksUp(inIpLayer),
                    blockedDemand.getIngressNode(),
                    blockedDemand.getEgressNode(),
                    linkCostMap_electronic);
            if (!IPpath.isEmpty())
                netPlan.addRoute(blockedDemand, 0, 0, IPpath, blockedDemand.getAttributes());
            else
                System.out.println("no path in the IP topology for this node pair!");
        }
        for (int restart = 1; restart <= R_ipLayer; restart++) {
    	  /*restart: copy netPlan to working*/
            working.copyFrom(netPlan);
            NetworkLayer wIpLayer = working.getNetworkLayer(1);
    	  
    	  /*remove all IP routes if this is hard optimization.
    	   * But we need a shortest path for each route, so we will simply set the route traffic to 0
    	   * and keep the current path*/
            if (hardOpt) {
                for (Route r : working.getRoutes(wIpLayer))
                    r.setCarriedTraffic(0, 0);
                numberOfInitialRoutes = 0;
            }

            if (demandOrder.getString().equals("random"))
                RandomUtils.shuffle(demandIndexesOrdered, rnd);
    	  
    	  /*accommodate empty routes in a suboptimal fashion, so that we can move them around inside the iteration*/
            Set<Route> emptyRoutes = new HashSet<>();
            for (int aDemandIndexesOrdered1 : demandIndexesOrdered) {
                Demand wDemand = working.getDemand(aDemandIndexesOrdered1, wIpLayer);
                for (Route wRoute : wDemand.getRoutes())
                    if (wRoute.getCarriedTraffic() == 0)
                        emptyRoutes.add(wRoute);
            }
            System.out.println("W0 blocked demands: " + working.getDemandsBlocked(wIpLayer).size() + " traffic: " + working.getDemandTotalBlockedTraffic(wIpLayer));
            AccommodateEmptyRoutes(working, emptyRoutes);
            System.out.println("W1 blocked demands: " + working.getDemandsBlocked(wIpLayer).size() + " traffic: " + working.getDemandTotalBlockedTraffic(wIpLayer));

            if (hardOpt) restartCost = costFunc(working);
            else restartCost = initialCost;
            orderedListOfChanges.clear();
            ProtSegChanges.clear();
            for (int iter = 1; iter <= ITER_ipLayer || ITER_ipLayer == -1; iter++) {
                boolean at_least_one_route_changed = false;
                for (int aDemandIndexesOrdered : demandIndexesOrdered) {
                    Demand wDemand = working.getDemand(aDemandIndexesOrdered, wIpLayer);
                    for (Route wRoute : wDemand.getRoutes()) {
                        if (wRoute.getCarriedTraffic() <= PRECISION_FACTOR) continue;
                     /* Path to restore if no better found */
                        List<Link> initialPath = wRoute.getSeqLinksRealPath();
                        List<Link> initialBackupPath = null;
                        ProtectionSegment initialProtSeg = null;
                        ProtectionSegment tempProtSeg = null;
                        if (wRoute.getPotentialBackupProtectionSegments().size() > 0) {
		        		 /*get the backup path from the protection segment*/
                            Set<ProtectionSegment> routeProtSegSet = wRoute.getPotentialBackupProtectionSegments();
                            initialProtSeg = routeProtSegSet.iterator().next();
                            initialBackupPath = initialProtSeg.getSeqLinks();
                        }
                        double initialTraffic = wRoute.getCarriedTraffic();
                        double initialOccupiedCapacity = wRoute.getOccupiedCapacity();
                        for (boolean swapped : new boolean[]{false, true}) {
                            wRoute.setCarriedTraffic(0, 0); /*we need to remove the traffic to find K shortest paths*/
				         /* Find K alternative paths */
                            final List<List<Link>> path_list = findApplicationAwareCandidatePaths
                                    (working, wDemand, initialTraffic, K_ipLayer);

                            path_list.remove(initialPath); /*we don't need the initial path*/

                            wRoute.setCarriedTraffic(initialTraffic, initialOccupiedCapacity); /*put the traffic back on*/

                            if (wRoute.getPotentialBackupProtectionSegments().size() > 0)
                                findPathsDisjointFromMe(initialBackupPath, path_list, wDemand);
				         
				         /* Check if any of the alternatives is better */
                            boolean changeFound = false;
                            List<Link> bestPath = initialPath;
                            Map<Route, List<Link>> bestNewPaths = new HashMap<>();
				         /*get the empty routes*/
                            emptyRoutes = new HashSet<Route>();
                            for (Route r : working.getRoutes(wIpLayer))
                                if (r.getCarriedTraffic() == 0)
                                    emptyRoutes.add(r);
                            for (List<Link> candidate : path_list) {
				        	/*activate this path*/
                                wRoute.setSeqLinksAndProtectionSegments(candidate);
                                AccommodateEmptyRoutes(working, emptyRoutes);
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
                                if (wRoute.getPotentialBackupProtectionSegments().size() > 0)
                                    System.out.println("protected route changed (index:) " + wRoute.getIndex());
                                at_least_one_route_changed = true;
				        	 /*if this route is initially unrouted*/
                                if (wRoute.getIndex() >= numberOfInitialRoutes) {
				        		 /*update the list of newly added paths
				        		 EmptyRouteChanges.put(wRoute, bestPath);
				        		 NO NEED, we will get these at after this restart
				        		 */
                                } else /*add to the list of Changes*/ {
                                    if (!swapped) {
                                        Pair<Route, List<Link>> pair = new Pair<>(wRoute, bestPath, false);
                                        orderedListOfChanges.add(pair);
                                    } else { /*this is a protection segment*/
                                        Pair<Route, List<Link>> pair = new Pair<>(wRoute, null, false);
                                        ProtSegChanges.add(bestPath);
                                        orderedListOfChanges.add(pair);
                                    }
                                }
				        	 /*add any newly added (bestNewPaths) to the list of newly added paths (EmptyRouteChanges)
				        	 EmptyRouteChanges.putAll(bestNewPaths);
				        	 NO NEED, we will get these at after this restart*/
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
				         /*do the swap*/
                            if (wRoute.getPotentialBackupProtectionSegments().size() > 0) {
				        	 /*swap the primary and backup paths*/
                                if (!swapped) {
				        		 /*update path pointers*/
                                    initialPath = initialBackupPath;
                                    initialBackupPath = wRoute.getSeqLinksAndProtectionSegments();
			        			 /*set the Route and ProtSeg correctly*/
                                    initialProtSeg.setReservedCapacity(0);
                                    tempProtSeg = working.addProtectionSegment(wRoute.getSeqLinksAndProtectionSegments(), initialOccupiedCapacity, null);
                                    wRoute.setSeqLinksAndProtectionSegments(initialProtSeg.getSeqLinks());
                                    wRoute.addProtectionSegment(tempProtSeg);
                                } else {
				        		 /*restore the situation*/
                                    if (changeFound) {
                                        initialProtSeg.remove();
                                        ProtectionSegment newProtSeg = working.addProtectionSegment(wRoute.getSeqLinksAndProtectionSegments(), initialOccupiedCapacity, null);
                                        wRoute.setSeqLinksAndProtectionSegments(tempProtSeg.getSeqLinks());
                                        wRoute.addProtectionSegment(newProtSeg);
                                    } else {
                                        initialProtSeg.setReservedCapacity(initialOccupiedCapacity);
                                        wRoute.setSeqLinksAndProtectionSegments(tempProtSeg.getSeqLinks());
                                    }
                                    tempProtSeg.remove();
                                }
                            } else break; /*this Route is not protected, go on to the next Route without swapping*/
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
                bestProtSegChanges.clear();
                bestProtSegChanges.addAll(ProtSegChanges);
		      /*save the best newly created Routes*/
                bestEmptyRouteChanges.clear();
                //bestEmptyRouteChanges.putAll(EmptyRouteChanges);
                System.out.println("W2 blocked demands: " + working.getDemandsBlocked(wIpLayer).size() + " traffic: " + working.getDemandTotalBlockedTraffic(wIpLayer));
                System.out.println("working routes " + working.getRoutes(wIpLayer).size());
                for (Route wRoute : working.getRoutes(wIpLayer))
                    if (wRoute.getIndex() >= numberOfInitialRoutes)
                        if (wRoute.getCarriedTraffic() > PRECISION_FACTOR)
                            bestEmptyRouteChanges.put(wRoute, wRoute.getSeqLinksAndProtectionSegments());
                if (!bestEmptyRouteChanges.isEmpty())
                    System.out.println("bestEmptyRouteChanges: " + bestEmptyRouteChanges.size());
            }
            System.out.println("final cost for this restart = " + restartCost);
	      /*System.out.println("W blocked demands: " + working.getDemandsBlocked(wIpLayer).size()+ " traffic: " + working.getDemandTotalBlockedTraffic(wIpLayer));*/
        }
        if (working.getLinksOversubscribed(working.getNetworkLayer(1)).size() > 0) {
            for (Link oversubscribedLinks : working.getLinksOversubscribed(working.getNetworkLayer(1)))
                System.out.println("IP link utilization:" + oversubscribedLinks.getUtilizationNotIncludingProtectionSegments());
            throw new Net2PlanException("working netPlan is oversubscribed (1)");
        }
        System.out.println("final cost = " + bestCost);
        if (hardOpt)
            if (bestCost >= initialCost || working.getDemandsBlocked(working.getNetworkLayer(1)).size() > 0) /*we did not improve the network*/ {
                System.out.println("best cost is too high, so we leave the IP layer as it was");
                bestEmptyRouteChanges.clear(); /*so no changes will be implemented*/
            } else
                for (Route r : netPlan.getRoutes(inIpLayer))
                    r.setCarriedTraffic(0, 0); /*or else we may get an oversubscription exception below*/
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
            boolean iAmProtSeg = false;
            if (wLinks == null) { /*this is a protection segment*/
                wLinks = bestProtSegChanges.remove();
                iAmProtSeg = true;
            }
            List<Link> inLinks = new ArrayList<>();
            for (Link link : wLinks) {
                Link inLink = netPlan.getLinkFromId(link.getId());
                if (inLink == null)
                    throw new Net2PlanException("Link did not exist in the original netplan!");
                inLinks.add(inLink);
            }
            if (!iAmProtSeg){
                inRoute.setSeqLinksAndProtectionSegments(inLinks);
		System.out.println("######################");
	        System.out.println("Demand  " + inRoute.getDemand() + " should be MOVED to path " + inLinks);
                System.out.println("######################");
                 RouteList.getInstance().addRoute(new Pair("MOVE",inRoute,false));
            }
            else { // TODO: Pontus: need to handle this for the Backup case (?)
		 	  /*remove old protection segment*/
                Set<ProtectionSegment> inRouteProtSegSet = inRoute.getPotentialBackupProtectionSegments();
                ProtectionSegment inRouteProtSeg = inRouteProtSegSet.iterator().next();
                inRouteProtSeg.remove();
			  /*create a new Protection Segment and attach it inRoute*/
                inRouteProtSeg = netPlan.addProtectionSegment(inLinks, inRoute.getCarriedTraffic(), null);
                inRoute.addProtectionSegment(inRouteProtSeg);
            }
            if (netPlan.getLinksOversubscribed(inIpLayer).size() > 0) {
                for (Link l : netPlan.getLinksOversubscribed(inIpLayer))
                    System.out.println("linkID: " + l.getIndex() + " traffic " + l.getCarriedTrafficIncludingProtectionSegments());
                throw new Net2PlanException("" + netPlan.getLinksOversubscribed(inIpLayer).size() + " links have utilization > 1");
            }
        }
	  /*apply the newly found paths*/
        System.out.println("added new Paths = " + bestEmptyRouteChanges.size());
        for (Map.Entry<Route, List<Link>> entry : bestEmptyRouteChanges.entrySet()) {
            Route wRoute = entry.getKey();
            Route inRoute = netPlan.getRouteFromId(wRoute.getId());
		  /*System.out.println("now adding route " + wRoute.getIndex());*/
            if (inRoute == null)
                throw new Net2PlanException("(empty) Route does not exist");
            List<Link> wLinks = wRoute.getSeqLinksRealPath();
            List<Link> inLinks = new ArrayList<>();
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
        return "Simple IP topology optimisation algorithm. By Ciril Rozic and Chris Matrakidis, AIT, 2016-2017";
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

        final NetworkLayer ipLayer = currentNetPlan.getNetworkLayer(1);

      /* Application awareness properties */
        int wdmClass = Integer.parseInt(ipDemand.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE));
        double maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
        double minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE)) / 100;
        boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));

      /* Search IP links violating bandwidth constraint */
        final List<Node> nodes = currentNetPlan.getNodes();
        final Set<Link> links = currentNetPlan.getLinksUp(ipLayer);

      /* Build the auxiliary graph from existing IP layer */
        Map<Link, Double> linkCostMap = new HashMap<>();
        List<Link> aux = new ArrayList<>();
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

        double maxPropDelayInMs = -1;

      /* Disable other constraints */
        double maxLengthInKm = -1;
        int maxNumHops = -1;
        double maxRouteCost = -1;
        double maxRouteCostFactorRespectToShortestPath = -1;
        double maxRouteCostRespectToShortestPath = -1;

        List<List<Link>> candidatePaths = GraphUtils.getKLooplessShortestPaths(nodes, aux, ingressNode,
                egressNode, linkCostMap, K, maxLengthInKm, maxNumHops, maxPropDelayInMs, maxRouteCost,
                maxRouteCostFactorRespectToShortestPath, maxRouteCostRespectToShortestPath);

        List<List<Link>> filteredPaths = new ArrayList<>();
        for (final List<Link> path : candidatePaths) {
            double pathPropDelayInMs = 0;
            for (Link link : path) {
                pathPropDelayInMs += link.getPropagationDelayInMs();
            }

            if (pathPropDelayInMs > maxLatencyInMs) {
                //System.out.println("!!!!!!!! " + ++counterwhatever + " out of " + candidatePaths.size());
            } else
                filteredPaths.add(path);
        }
        candidatePaths = filteredPaths;

        for (Route wRoute : ipDemand.getRoutes())
            if (candidatePaths.size() == 0 && wRoute.getIndex() < numberOfInitialRoutes) /*only print if there was a path/route but it was not found again*/ {
                System.out.println("Request " + ingressNode.getIndex() + " to " + egressNode.getIndex());
                for (Link l : linkCostMap.keySet())
                    System.out.println("AG link: " + l.getIndex());
                List<Link> onePath = GraphUtils.getShortestPath(nodes, aux, ingressNode, egressNode, linkCostMap);
                if (onePath.size() > 0) System.out.println("found a path");
            }
        if (Protected)
            return candidatePaths;
        List<List<Link>> finalPaths = new ArrayList<List<Link>>();
        for (final List<Link> path : candidatePaths) {
            Set<SharedRiskGroup> srgs = new HashSet<SharedRiskGroup>();
            for (final Link tLink : path) {
                String doNotDeleteMeString = tLink.getAttribute("do not delete me");
                if (Boolean.parseBoolean(doNotDeleteMeString)) {
                    continue; /*these IP links have no coupled optical demands*/
                }
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
        double x = netPlan.getVectorDemandBlockedTraffic(ipLayer).zSum();

        final DoubleMatrix1D t = netPlan.getVectorLinkTotalCarriedTraffic(ipLayer);
        double z = t.zDotProduct(t);


        final DoubleMatrix1D p = netPlan.getVectorLinkCapacityReservedForProtection(ipLayer);
        /* add reserved capacity to traffic to get used link capacity*/
        int y = 0;
        for (int i = 0; i < t.size(); i++)
            if (t.get(i) + p.get(i) > 1e-6)
                y++;

        return C0 * x + C1 * y + z;
    }

    private void findPathsDisjointFromMe(List<Link> workingPath, List<List<Link>> candidatePaths, Demand demand) {
        double minAvailability = Double.parseDouble(demand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE)) / 100;
        double NodeAvailability = 1.0;
        if (minAvailability > 0) {
            Set<SharedRiskGroup> srgs_Nodes = new HashSet<>();
            srgs_Nodes.addAll(demand.getIngressNode().getSRGs());
            srgs_Nodes.addAll(demand.getEgressNode().getSRGs());
            for (SharedRiskGroup srg : srgs_Nodes) NodeAvailability *= srg.getAvailability();
        }
        Set<Link> workingPathLinks = new HashSet<>(workingPath);
        Set<Node> workingPathSequenceOfIntermediateNodes;

        List<Node> workingPathSequenceOfNodes = GraphUtils.convertSequenceOfLinksToSequenceOfNodes(workingPath);
        workingPathSequenceOfIntermediateNodes = new HashSet<>(workingPathSequenceOfNodes);
        workingPathSequenceOfIntermediateNodes.remove(demand.getIngressNode());
        workingPathSequenceOfIntermediateNodes.remove(demand.getEgressNode());

        double availability_Working = 1.0;
        if (minAvailability > 0) {
            // get the path Avl
            Set<SharedRiskGroup> srgs_Working = new HashSet<>();
            for (Link ipLink : workingPath)
                srgs_Working.addAll(findLightpathSRGs(ipLink));
            for (SharedRiskGroup srg : srgs_Working) availability_Working *= srg.getAvailability();
        }
        for (int i = candidatePaths.size() - 1; i >= 0; i--) {
            List<Link> backupPath = candidatePaths.get(i);
            boolean disjointPaths = true;
            boolean firstLink = true;

            for (Link backupLink : backupPath) {
                if (workingPathLinks.contains(backupLink)) {
                    disjointPaths = false;
                    candidatePaths.remove(i);
                    break;
                }
                if (firstLink) {
                    firstLink = false;
                } else {
                    if (workingPathSequenceOfIntermediateNodes.contains(backupLink.getOriginNode())) {
                        disjointPaths = false;
                        candidatePaths.remove(i);
                        break;
                    }
                }
            }

            if (disjointPaths) {
                boolean disjointAtOpticalLayer = checkDisjointnessAtWDMLayer(workingPath, backupPath);
                if (disjointAtOpticalLayer) {
	    			/*check if Avl is satisfied*/
	    			/*get the path Avl*/
                    double availability_Backup = 1.0;
                    if (minAvailability > 0) {
                        Set<SharedRiskGroup> srgs_Backup = new HashSet<>();
                        for (Link ipLink : backupPath)
                            srgs_Backup.addAll(findLightpathSRGs(ipLink));
                        for (SharedRiskGroup srg : srgs_Backup) availability_Backup *= srg.getAvailability();
                    }
                    double total_path_availability = availability_Working + availability_Backup - availability_Working * availability_Backup / NodeAvailability;
                    if (total_path_availability < minAvailability)
                        candidatePaths.remove(i);
                } else
                    candidatePaths.remove(i);
            }
        }
    }

    private boolean checkDisjointnessAtWDMLayer(List<Link> workingLinkList, List<Link> backupLinkList) {
        Set<NetworkElement> resourcesPath = new HashSet<>();
        boolean firstLink = true;
        for (Link ipLink : workingLinkList) {
            Demand wdmDemand = ipLink.getCoupledDemand();
            Set<Route> wdmRoutes = wdmDemand.getRoutes();
            for (Route wdmRoute : wdmRoutes)
                for (Link link : wdmRoute.getSeqLinksRealPath()) {
                    resourcesPath.add(link);
                    if (firstLink) firstLink = false;
                    else resourcesPath.add(link.getOriginNode());
                }
        }
        for (Link ipLink : backupLinkList) {
            Demand wdmDemand = ipLink.getCoupledDemand();
            Set<Route> wdmRoutes = wdmDemand.getRoutes();
            for (Route wdmRoute : wdmRoutes)
                for (Link link : wdmRoute.getSeqLinksRealPath())
                    if (resourcesPath.contains(link))
                        return false;
        }
        return true;

    }

    /* Function to find all the SRGs for a lightpath */
    private Set<SharedRiskGroup> findLightpathSRGs(Link ipLink) {
        Set<SharedRiskGroup> srg_thisLightpath = new LinkedHashSet<>();
        
        /* SRGs at the IP layer */
        Node ipOriginNode = ipLink.getOriginNode();
        Node ipDestinationNode = ipLink.getDestinationNode();
        srg_thisLightpath.addAll(ipOriginNode.getSRGs()); /* SRGs for source IP router */
        srg_thisLightpath.addAll(ipDestinationNode.getSRGs()); /* SRGs for destination IP router */
        srg_thisLightpath.addAll(ipLink.getSRGs()); /* SRGs for the IP link itself */
        
        /* SRGs at the WDM layer */
        Demand wdmDemand = ipLink.getCoupledDemand();
        if (wdmDemand != null) {
            Set<Route> wdmRoutes = wdmDemand.getRoutes();
            for (Route wdmRoute : wdmRoutes)
                srg_thisLightpath.addAll(wdmRoute.getSRGs());
        }
        return srg_thisLightpath;
    }

    private void AccommodateEmptyRoutes(NetPlan working, Set<Route> emptyRoutes) {
    	/*route the empty routes (change path). Take the first/any path found. Assign all blocked traffic for its demand to it.*/
        for (Route r : emptyRoutes) {
            List<List<Link>> emptyRoutesPathList = findApplicationAwareCandidatePaths
                    (working, r.getDemand(), r.getDemand().getBlockedTraffic(), 1); /*we only need 1 new route*/
            if (!emptyRoutesPathList.isEmpty()) {
                r.setSeqLinksAndProtectionSegments(emptyRoutesPathList.get(0));
                r.setCarriedTraffic(r.getDemand().getBlockedTraffic(), r.getDemand().getBlockedTraffic());
            }
        }

    }
}
