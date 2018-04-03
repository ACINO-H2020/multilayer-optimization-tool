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

package es.upct.girtel.net2plan.plugins.onos.api;


import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.ProtectionSegment;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.utils.Pair;
import com.wpl.xrapc.IXrap;
import es.upct.girtel.net2plan.plugins.onos.ONOSPlugin;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapAction;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapDemand;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapLink;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapMessage;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapNode;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapRoute;
import es.upct.girtel.net2plan.plugins.onos.model.PortId;
import es.upct.girtel.net2plan.plugins.onos.utils.RouteList;
import es.upct.girtel.net2plan.plugins.onos.utils.Stopwatch;
import es.upct.girtel.net2plan.plugins.onos.utils.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static es.upct.girtel.net2plan.plugins.onos.api.RouteApi.routeToNetRapRoute;

@Path("/demand")

@Api(description = "The demand API")


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2016-11-14T14:11:38.242Z")

public class DemandApi implements IXrap {
    private ONOSPlugin parent;

    public void setParent(Object parent) {
        this.parent = (ONOSPlugin) parent;
    }

    public static NetRapRoute protectionSegmentToNetRapRoute(Route route, ProtectionSegment protectionSegment) throws Net2PlanException {

        NetRapRoute netRapRoute = new NetRapRoute();
        Demand coupledDemand = route.getDemand();
        netRapRoute.setLayer(route.getLayer().getIndex());

        if (coupledDemand == null) {
            throw new Net2PlanException("Found route without coupled demand!");
        }
        Map<String, String> attributes = route.getAttributes();
        HashMap<String, String> netRapAttributes = new HashMap<>();

        for (String key : attributes.keySet()) {
            if (key.equals("seqRegenerators"))
                continue;
            if (key.equals("seqRegeneratorsInitialRoute"))
                continue;
            netRapAttributes.put(key, attributes.get(key));
        }

        netRapRoute.setAttributes(netRapAttributes);
        long demandId = coupledDemand.getId();
        //     netRapRoute.setDemandId(Long.toString(demandId));
        //   netRapRoute.setOccupiedCapacity(route.getOccupiedCapacity());
        List<Link> links = protectionSegment.getSeqLinks();
        List<Node> nodes = protectionSegment.getSeqNodes();
        List<NetRapNode> netRapNodes = new ArrayList<>();
        List<NetRapLink> netRapLinks = new ArrayList<>();

        for (Link link : links) {
            NetRapLink netRapLink = TopologyApi.linkToNetRapLink(link);
            netRapLinks.add(netRapLink);
        }

        for (Node node : nodes) {
            NetRapNode netRapNode = TopologyApi.nodeToNetRapNode(node);
            netRapNodes.add(netRapNode);
        }

        netRapRoute.setLinks(netRapLinks);
        netRapRoute.setNodes(netRapNodes);

        return netRapRoute;
    }

    private NetRapDemand demandToNetRapDemand(Demand demand) {
        NetRapDemand netRapDemand = new NetRapDemand();

        netRapDemand.setIdentifier(demand.getId());
        PortId egress = new PortId();
        egress.setDevice(demand.getEgressNode().getName());
        egress.setPort(demand.getAttribute("dstPort"));
        netRapDemand.setEgressNode(egress);

        PortId ingress = new PortId();
        ingress.setDevice(demand.getIngressNode().getName());
        ingress.setPort(demand.getAttribute("srcPort"));
        netRapDemand.setIngressNode(ingress);

        netRapDemand.setOfferedTraffic(demand.getOfferedTraffic());

        Set<Route> routes = demand.getRoutes();

        // TODO: assumes that there's only one route per demand!
        for (Route route : routes) {
            netRapDemand.setRouteId(Long.toString(route.getId()));
            netRapDemand.setRoute(routeToNetRapRoute(route));
            Set<ProtectionSegment> pbps = route.getPotentialBackupProtectionSegments();
            for (ProtectionSegment ps : pbps) {
                NetRapRoute netrapRoute = protectionSegmentToNetRapRoute(route, ps);
                netRapDemand.setBackupRoute(netrapRoute);
                netRapDemand.setBackupRouteId(Long.toString(route.getId()));
            }
        }

        Map<String, String> attributes = demand.getAttributes();
        HashMap<String, String> netRapAttributes = new HashMap<>();

        for (String key : attributes.keySet()) {
            if (key.equals("srcPort"))
                continue;
            if (key.equals("dstPort"))
                continue;
            netRapAttributes.put(key, attributes.get(key));
        }
        netRapDemand.setAttributes(netRapAttributes);
        return netRapDemand;
    }


    private List<NetRapAction> reoptimize() throws Net2PlanException {
        Stopwatch timer = Stopwatch.createStarted();
        try {
            ArrayList<NetRapAction> nraList = new ArrayList<>();
            parent.updateOperationLog("Reoptimizing network");
            // remove spent routes
            RouteList.getInstance().clearRoutes();
            parent.reoptimize();
            for (Pair<String, Route> act : RouteList.getInstance().getRoutes()) {
                String type = act.getFirst();
                Route foundRoute = act.getSecond();
                NetRapDemand newDemand = demandToNetRapDemand(foundRoute.getDemand());
                newDemand.setRoute(routeToNetRapRoute(foundRoute));
                NetRapAction newAction = new NetRapAction();
                newAction.setAction(type);
                newAction.setDemand(newDemand);
                nraList.add(newAction);
            }
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return nraList;
        } catch (Exception e) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            System.out.println(Utils.getCurrentMethodName() + " caught exception!");
            System.out.println(e.toString());
            throw e;
        }
    }


    /*
    * 1. Create the n2p demand,
    *   2. If the demand has a route, resolve it, install it
    *    3. Return empty list
    *   4. else, no route
    *      5.
    * */

    private List<NetRapAction> addDemand(NetRapDemand demand, boolean update, boolean isWDM) throws Net2PlanException {
        Stopwatch timer = Stopwatch.createStarted();
        Demand dmd = null;
        Route r = null;
        List<Link> initialN2PLinkList = null;
        try {
            NetPlan netPlan = parent.getDesign();
        /* 1. Create n2p demand */
            Node ingress = netPlan.getNodeByName(demand.getIngressNode().getDevice());
            Node egress = netPlan.getNodeByName(demand.getEgressNode().getDevice());
            double offer = demand.getOfferedTraffic();
            NetworkLayer _ipLayer = netPlan.getNetworkLayer("IP");
            NetworkLayer _wdmLayer = netPlan.getNetworkLayer("WDM");
            HashMap attributes = demand.getAttributes();
      /*    TODO Uncomment to test protection
            System.out.println("###########################");
            System.out.println("TEST: Setting the Protection attribute!");
            attributes.put("Protection", "true");
            System.out.println("###########################");
*/
            NetRapRoute incomingRoute = demand.getRoute();
            NetRapRoute incomingBackupRoute = demand.getBackupRoute();
            ArrayList<NetRapAction> nraList = new ArrayList<>();

            if (ingress == null) {
                parent.updateOperationLog("Could not find ingress node: " + demand.getIngressNode().getDevice());
                throw new Net2PlanException("Could not find ingress node " + demand.getIngressNode().getDevice());
            }
            if (egress == null) {
                parent.updateOperationLog("Could not find egress node: " + demand.getEgressNode().getDevice());
                throw new Net2PlanException("Could not find egress node: " + demand.getEgressNode().getDevice());
            }

            if (_ipLayer == null)
                _ipLayer = netPlan.addLayer("IP", "IP layer obtained from ONOS", null, null, null);
            if (_wdmLayer == null)
                _wdmLayer = netPlan.addLayer("WDM", "WDM layer obtained from ONOS", null, null, null);

            attributes.put("srcPort", demand.getIngressNode().getPort());
            attributes.put("dstPort", demand.getEgressNode().getPort());

            String onosId = (String) demand.getAttributes().get("id");
            String onosKey = (String) demand.getAttributes().get("key");

            // Check if this demand is already stored, if so it's probably an attempt to restore it
            if (onosId != null && onosKey != null && !isWDM) {
                List<Demand> demands = netPlan.getDemands();
                for (Demand dmnd : demands) {
                    String n2pId = dmnd.getAttributes().get("id");
                    String n2pKey = dmnd.getAttributes().get("key");
                    if (onosId.equals(n2pId) && onosKey.equals(n2pKey)) {
                        dmd.remove();
                        parent.loadDesign(netPlan);
                        parent.updateOperationLog("Got duplicate key and id, removed local version.");
                        break;
                    }
                }
            }

        /* 2. Create the n2p demand */

            if (isWDM) {
                if(incomingRoute != null) {
                    for (Demand demandToCheck : netPlan.getDemands(_wdmLayer)) {
                        if (demandToCheck.getEgressNode().equals(egress) &&
                                demandToCheck.getIngressNode().equals(ingress)) {
                            //The same demand exists, so let's update it
                            System.out.println("Updating demand " + demandToCheck.getId() + " with attributes");
                            demandToCheck.setAttributeMap(attributes);
                            return nraList;

                        }
                    }
                }
                dmd = netPlan.addDemand(ingress, egress, offer, attributes, _wdmLayer);
                dmd.setOfferedTraffic(offer);
            } else {
                dmd = netPlan.addDemand(ingress, egress, offer, attributes, _ipLayer);
                dmd.setOfferedTraffic(offer);
            }
        /* if a route is already included, resolve, install */
            if (incomingRoute != null) {
                Integer nrLayer = incomingRoute.getLayer();
                HashMap nrAttributes = incomingRoute.getAttributes();
                String nrDemandId = incomingRoute.getDemandId();
                List<NetRapLink> nrLinks = incomingRoute.getLinks();
                List<NetRapNode> nrNodes = incomingRoute.getNodes();
                List<Link> newRoute = new ArrayList<>();

            /* resolve the different links */
                for (NetRapLink nrLink : nrLinks) {
                    Link l = resolveNetRapLink(nrLink, netPlan);
                    if (l == null) {
                        System.out.println("Could not find a link for " + nrLink);
                        if (System.getProperty("profile") != null)
                            parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                        return null;
                    }
                    newRoute.add(l);
                }
            /* create the route */
                initialN2PLinkList = newRoute;

                if(incomingRoute.getOccupiedCapacity() != null)
                    r = netPlan.addRoute(dmd, offer, incomingRoute.getOccupiedCapacity(), newRoute, nrAttributes);
                else
                    r = netPlan.addRoute(dmd, offer, offer, newRoute, nrAttributes);

                if (incomingBackupRoute != null) {
                    System.out.println("Creating protection segment");
                    Integer bnrLayer = incomingBackupRoute.getLayer();
                    HashMap bnrAttributes = incomingBackupRoute.getAttributes();
                    String bnrDemandId = incomingBackupRoute.getDemandId();
                    List<NetRapLink> bnrLinks = incomingBackupRoute.getLinks();
                    List<NetRapNode> bnrNodes = incomingRoute.getNodes();
                    List<Link> bnewRoute = new ArrayList<>();

            /* resolve the different links */
                    for (NetRapLink nrLink : bnrLinks) {
                        Link l = resolveNetRapLink(nrLink, netPlan);
                        if (l == null) {
                            System.out.println("Could not find a link for " + nrLink);
                            if (System.getProperty("profile") != null)
                                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                            return null;
                        }
                        bnewRoute.add(l);
                    }

                    ProtectionSegment ps;
                    if (System.getProperty("noresv") != null)
                        ps = netPlan.addProtectionSegment(bnewRoute, 0.0, bnrAttributes);
                    else
                        ps = netPlan.addProtectionSegment(bnewRoute, offer, bnrAttributes);

                    r.addProtectionSegment(ps);
                    System.out.println("Protection segement added to route");
                }
                System.out.println("Added route from demand!");
            /* if the link is wdm, create a coupled link on the higer layer */
                if (isWDM) {
                    Link couple = dmd.coupleToNewLinkCreated(_ipLayer);
                    double len = 0;
                    for (Link l : r.getInitialSequenceOfLinks()) {
                        len += l.getLengthInKm();
                    }
                    couple.setLengthInKm(len);
                    System.out.println("Coupled WDM demand to new link: " + couple);
                    System.out.println("From node " + couple.getOriginNode().getName() + " to node " + couple.getDestinationNode().getName());
                }
            /* create a route action and return */
            /*
            NetRapDemand newDemand = demandToNetRapDemand(dmd);
                NetRapAction newAction = new NetRapAction();
                newAction.setAction("ROUTE");
                newAction.setDemand(newDemand);
                nraList.add(newAction);
                */
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return nraList;
            }

         /* no route included, let cacsim find one */
            parent.cacsimNewDemand(dmd);
            RouteApi routeApi = new RouteApi();

        /* No solution found, return FAIL action */
            if (RouteList.getInstance().numActions() == 0) {
                System.out.println("Failed to find path for demand: " + dmd.toString());
                NetRapDemand newDemand = demandToNetRapDemand(dmd);
                NetRapAction newAction = new NetRapAction();
                newAction.setAction("FAIL");
                newAction.setDemand(newDemand);
                nraList.add(newAction);
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return nraList;
            }

        /* Solution(s) found! */
            System.out.println("Actions needed to fulfil demand " + dmd.toString());
            for (Pair<String, Route> act : RouteList.getInstance().getRoutes()) {
                String type = act.getFirst();
                Route foundRoute = act.getSecond();

                System.out.println("\t " + type + " Route: " + foundRoute);

                /* IP route for the incoming demand */
                if (foundRoute.getLayer().getName().equals("IP")) {
                    System.out.println("Demand in route matches sent demand!");
                    foundRoute.getDemand().setAttribute("srcPort", demand.getIngressNode().getPort());
                    foundRoute.getDemand().setAttribute("dstPort", demand.getEgressNode().getPort());
                    NetRapDemand newDemand = demandToNetRapDemand(foundRoute.getDemand());
                    newDemand.setRoute(routeToNetRapRoute(foundRoute));
                    NetRapAction newAction = new NetRapAction();
                    newAction.setAction("ROUTE");
                    if (type.equals("MOVE"))
                        newAction.setAction("MOVE");

                    newAction.setDemand(newDemand);
                    nraList.add(newAction);
                } else if (foundRoute.getLayer().getName().equals("WDM")) { /* New optical route */
                    System.out.println("Demand is new!");
                    NetRapDemand newDemand = demandToNetRapDemand(foundRoute.getDemand());
                    newDemand.setRoute(routeToNetRapRoute(foundRoute));
                    NetRapAction newAction = new NetRapAction();
                    newAction.setAction("NEW");
                    newAction.setDemand(newDemand);
                    boolean addAction = true;
                    // We should only send one of the optical demands to ONOS (they are bidirectional there)
                    for (NetRapAction nra : nraList) {
                        // if not a NEW, check next
                        if (!nra.getAction().equals(newAction.getAction()))
                            continue;
                        // if nra.src = new.dst, and nra.dst == new.src the action should not be added to the list
                        if (nra.getDemand().getEgressNode().equals(newAction.getDemand().getIngressNode()) &&
                                nra.getDemand().getEgressNode().equals(newAction.getDemand().getIngressNode()))
                            addAction = false;
                    }
                    if (addAction)
                        nraList.add(newAction);
                } else {
                    System.out.println("Action neither on IP nor WDM layer?!");
                }
            }
            // remove spent routes
            RouteList.getInstance().clearRoutes();
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            if (update) {
                parent.loadDesign(netPlan);
            }
            return nraList;
        } catch (Net2PlanException n2px) {
            if(n2px.getMessage().equals("The initial node of the sequence of links is not correct") && initialN2PLinkList != null){
                // Probably this due to being a reverse optical path, using the wrong links.
                // Replace the links with their bidirectionalCouple friends
                NetRapRoute route = demand.getRoute();
                NetPlan netPlan = parent.getDesign();
                // if the demand was "half-installed" try to remove it
                if (dmd != null)
                    dmd.remove();

                List<NetRapLink> newLinks = new LinkedList<>();
                System.out.println("Resolving the reverse links..");
                for(Link linkToRevert : initialN2PLinkList){
                    //String coupleId = (String) netRapLink.getAttributes().get("bidirectionalCouple");
                    Link revertedLink = linkToRevert.getBidirectionalPair();

                    if(revertedLink != null){
                        NetRapLink nrl = TopologyApi.linkToNetRapLink(revertedLink);

                        if(System.getProperty("debug") != null)
                            System.out.println("Found couple link: " + nrl.toString());

                        newLinks.add(nrl);

                    } else {
                        System.out.println("Failed to find couple link :(");
                    }
                }
                route.setLinks(newLinks);
                demand.setRoute(route);
                System.out.println("Trying to addDemand with updated links..");
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return addDemand(demand, update, isWDM);
            } else {
                System.out.println("addDemand caught exception!");
                System.out.println(n2px.toString());
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return null;
            }
        } catch (Exception e) {
            System.out.println("addDemand caught exception!");
            System.out.println(e.toString());
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return null;
        }
    }

    private Link resolveNetRapLink(NetRapLink nrLink, NetPlan netPlan) {
        Node srcNode = netPlan.getNodeByName(nrLink.getSrc());
        Node dstNode = netPlan.getNodeByName(nrLink.getDst());


        int layer = nrLink.getLayer();
        if (srcNode == null) {
            System.out.println("Could not resolve link with name: " + nrLink.getSrc());
            return null;
        }
        if (dstNode == null) {
            System.out.println("Could not resolve link with name: " + nrLink.getDst());
            return null;
        }
        if (System.getProperty("debug") != null)
            System.out.println("looking for link between " + srcNode.getName() + " and " + dstNode.getName());
        for (Link l : srcNode.getOutgoingLinksAllLayers()) {
            if (l.getDestinationNode().getName().equals(dstNode.getName())) {
                if (System.getProperty("debug") != null)
                    System.out.println("Found link: " + l.toString());
                return l;
            }
        }

        System.out.println("Could not find link between " + srcNode + " and " + dstNode);
        return null;
    }

    private void deleteDemand(NetRapDemand netRapDemand, boolean update) {
        NetPlan netPlan = parent.getDesign();
        Long identifier = netRapDemand.getIdentifier();
        if (identifier == null) {
            // TODO: handle this case nicer
            return;
        }
        Demand demand = netPlan.getDemandFromId(identifier);
        parent.cacsimDeleteDemand(demand);
        demand.remove();

        if (update)
            parent.loadDesign(netPlan);

    }


    @POST

    @Consumes({"application/json"})

    @ApiOperation(value = "CreateDemand", notes = "Add a new demand", response = NetRapAction.class, responseContainer = "List", authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand"})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns a list of unique identifiers for the created demands", response = NetRapAction.class, responseContainer = "List"),
            @ApiResponse(code = 200, message = "Returns an empty list", response = NetRapAction.class, responseContainer = "List")})

    public Response demandPost(NetRapDemand netRapDemand) {
        Stopwatch timer = Stopwatch.createStarted();

        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        try {
            List<NetRapAction> actionList;
            if (netRapDemand.getRoute() != null) {
                if (netRapDemand.getRoute().getLayer() == 0) {
                    actionList = addDemand(netRapDemand, true, true);
                    parent.updateCacsim();
                } else if (netRapDemand.getRoute().getLayer() == 1) {
                    actionList = addDemand(netRapDemand, true, false);
                } else {
                    throw new Net2PlanException("Got demand with route, but route is not in IP or WDM layer!");
                }
            } else {
                actionList = addDemand(netRapDemand, true, false);
            }

            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(actionList).type(MediaType.APPLICATION_JSON_TYPE).build();

        } catch (Net2PlanException e) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when creating demand " + e.toString()))
                    .build();
        }

    }

    @DELETE
    @ApiOperation(value = "DeleteAllDemands", notes = "Delete all the demands", response = NetRapMessage.class, authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapMessage.class)})
    public Response demandsDelete() {
        Stopwatch timer = Stopwatch.createStarted();
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        NetPlan netPlan = parent.getDesign();
        NetworkLayer networkLayer = netPlan.getNetworkLayer("IP");
        if (networkLayer == null) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(Utils.nrMsg("No demands found")).build();
        }

        List<Demand> demands = netPlan.getDemands(networkLayer);
        int count = 0;
        for (Demand demand : demands) {
            demand.remove();
            count++;
        }
        parent.loadDesign(netPlan);

        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("Deleted " + count + " demands")).build();
    }

    @DELETE
    @Path("/{demandId}")
    @ApiOperation(value = "DeleteDemand", notes = "Delete individual demand", response = NetRapMessage.class, authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapMessage.class)})
    public Response demandDemandIdDelete(@PathParam("demandId") String intentKey) {
        Stopwatch timer = Stopwatch.createStarted();
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        NetPlan netPlan = parent.getDesign();
        NetworkLayer _wdmLayer = netPlan.getNetworkLayer("WDM");
        NetworkLayer _ipLayer = netPlan.getNetworkLayer("IP");
         if (_ipLayer == null || _wdmLayer == null) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            parent.updateOperationLog(Utils.getCurrentMethodName() + " Could not get IP Network layer!");
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg(Utils.getCurrentMethodName() + " Could not get IP network layer"))
                    .build();
        }


        try {
            List<Demand> optoDemands = netPlan.getDemands(_wdmLayer);
            List<Demand> ipDemands = netPlan.getDemands(_ipLayer);
            List<Demand> demandsToBeRemoved = new ArrayList<>();
            List<Link> linksTobeRemoved = new ArrayList<>();

            boolean demandRemoved = false;
            for(Demand dmd : optoDemands) {
                String keyVal = dmd.getAttribute("key");
                if (keyVal == null)
                    continue;

                if (keyVal.equalsIgnoreCase(intentKey)) {
                    demandsToBeRemoved.add(dmd);
                    Link linkToRemove = netPlan.getLinkFromId(dmd.getCoupledLink().getId());
                    linksTobeRemoved.add(linkToRemove);
                }
            }

            for(Demand dmd : ipDemands) {
                String keyVal = dmd.getAttribute("key");
                if(keyVal == null)
                    continue;

                if (keyVal.equalsIgnoreCase(intentKey)) {
                    demandsToBeRemoved.add(dmd);
                }
            }

            //Avoiding concurrent modification exception
            if (!demandsToBeRemoved.isEmpty()) {
                demandsToBeRemoved.forEach(Demand::remove);
                if (!linksTobeRemoved.isEmpty()) {
                    linksTobeRemoved.forEach(Link::remove);
                }
                demandRemoved = true;
            }

            if (demandRemoved) {
                parent.updateCacsim();
                netPlan = parent.getDesign();
                parent.loadDesign(netPlan);

                if(System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName()  + " took: " + timer.stop());

                return Response.ok().entity(Utils.nrMsg("Demand removed")).build();
            }

            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg(Utils.getCurrentMethodName() + " Demand " + intentKey + " could not be found"))
                    .build();
        } catch (Net2PlanException e) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());

            parent.updateOperationLog(Utils.getCurrentMethodName() + " Exception when deleting demand " + e.toString());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg(Utils.getCurrentMethodName() + " Exception when deleting demand " + e.toString()))
                    .build();
        }
    }

    @GET
    @Path("/{demandId}")
    @Produces({"application/json"})
    @ApiOperation(value = "GetDemand", notes = "Get an individual demand", response = NetRapDemand.class, authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status 200", response = NetRapDemand.class),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapDemand.class)})
    public Response demandDemandIdGet(@PathParam("demandId") Long demandId) {
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        NetworkLayer networkLayer = netPlan.getNetworkLayer("IP");
        if (networkLayer == null) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Demand " + demandId + " could not be found"))
                    .build();
        }
        try {
            Demand demand = netPlan.getDemandFromId(demandId);
            if (demand == null) {
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return Response.serverError()
                        .status(Response.Status.NOT_FOUND)
                        .entity(Utils.nrMsg("Demand " + demandId + " could not be found"))
                        .build();
            }
            NetRapDemand netRapDemand = demandToNetRapDemand(demand);
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(netRapDemand).build();
        } catch (Net2PlanException e) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when creating demand " + e.toString()))
                    .build();
        }

    }

    @PUT
    @Path("/{demandId}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @ApiOperation(value = "UpdateDemand", notes = "Update a demand", response = NetRapDemand.class, authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status 200", response = NetRapDemand.class),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapDemand.class)})
    public Response demandDemandIdPut(@PathParam("demandId") Long demandId, NetRapDemand body) {
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        NetworkLayer networkLayer = netPlan.getNetworkLayer("IP");
        if (networkLayer == null) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Demand " + demandId + " could not be found"))
                    .build();
        }
        try {
            Demand demand = netPlan.getDemandFromId(demandId);
            if (demand == null) {
                if (System.getProperty("profile") != null)
                    parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
                return Response.serverError()
                        .status(Response.Status.NOT_FOUND)
                        .entity(Utils.nrMsg("Demand " + demandId + " could not be found"))
                        .build();
            }
            // TODO: can only update offered traffic and attributes
            demand.setOfferedTraffic(body.getOfferedTraffic());
            HashMap attributes = body.getAttributes();
            attributes.put("srcPort", body.getIngressNode().getPort());
            attributes.put("dstPort", body.getEgressNode().getPort());
            demand.setAttributeMap(attributes);
            NetRapDemand updated = demandToNetRapDemand(demand);
            parent.cacsimUpdateDemand(demand);
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(updated).build();
        } catch (Net2PlanException e) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when updating demand " + e.toString()))
                    .build();
        }
    }

    @GET
    @Produces({"application/json"})
    @ApiOperation(value = "GetAllDemands", notes = "Get all the demands in the topology", response = NetRapDemand.class, responseContainer = "List", authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "List of demands", response = NetRapDemand.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "No demands found", response = NetRapDemand.class, responseContainer = "List")})
    public Response demandGet() {
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        NetworkLayer networkLayer = netPlan.getNetworkLayer("IP");
        ArrayList<NetRapDemand> netRapDemands = new ArrayList<>();

        if (networkLayer == null) {
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(Utils.nrMsg("No demands found")).build();
        }

        List<Demand> demands = netPlan.getDemands(networkLayer);
        int count = 0;
        for (Demand demand : demands) {
            NetRapDemand netRapDemand = demandToNetRapDemand(demand);
            netRapDemands.add(netRapDemand);
        }
        if (System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
        return Response.ok().entity(netRapDemands).build();
    }

    @POST
    @Path("/reopt")
    @ApiOperation(value = "Reoptimize", notes = "Reoptimize the network", response = NetRapAction.class, responseContainer = "List", authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns a list of actions for reoptimizing the network", response = NetRapAction.class, responseContainer = "List")})
    public Response demandReoptimize() {
        Stopwatch timer = Stopwatch.createStarted();

        try {
            parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
            System.out.println(Utils.getCurrentMethodName() + " called");

            // Could order the demands here if needed
            List<NetRapAction> allActions = new ArrayList<>();

            allActions = reoptimize();

            if (allActions == null) {
                System.out.println("################################");
                System.out.println("Failed to reoptimize network!");
                System.out.println("################################");
            }


            parent.loadDesign(parent.getDesign());
            Utils.writeToFile(parent.getDesign());
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(allActions).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            System.out.println("demandReoptimize: caught error " + e.toString());
            System.out.println(e.getCause());
            e.printStackTrace(System.out);
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when creating demand " + e.toString()))
                    .build();
        }

    }

    @POST
    @Path("/list")
    @Consumes({"application/json"})
    @ApiOperation(value = "CreateListDemands", notes = "Create multiple demands at once", response = NetRapAction.class, responseContainer = "List", authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Demand",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Returns a list of unique identifiers for the created demands", response = NetRapAction.class, responseContainer = "List")})
    public Response demandListPost(List<NetRapDemand> body) {
        Stopwatch timer = Stopwatch.createStarted();
        try {
            parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
            //System.out.println(Utils.getCurrentMethodName() + " called");

        /* handle insertion in order
         * 1. OptoDemands with routes (creates links used by 2 and 3)
         * 2. IPDemands with routes
         * 3. Demands without routes
         */
            List<NetRapDemand> optoDemands = new ArrayList<>();
            List<NetRapDemand> ipDemands = new ArrayList<>();
            List<NetRapDemand> emptyDemands = new ArrayList<>();

            for (NetRapDemand netRapDemand : body) {
                if (netRapDemand.getRoute() != null) {
                    if (netRapDemand.getRoute().getLayer() == 0)
                        optoDemands.add(netRapDemand);
                    else if (netRapDemand.getRoute().getLayer() == 1)
                        ipDemands.add(netRapDemand);
                    else
                        emptyDemands.add(netRapDemand);
                } else {
                    emptyDemands.add(netRapDemand);
                }
            }

            // Could order the demands here if needed
            List<NetRapAction> allActions = new ArrayList<>();

            for (NetRapDemand optodmd : optoDemands) {
                List<NetRapAction> actions = addDemand(optodmd, true, true);
                if (actions == null) {
                    System.out.println("################################");
                    System.out.println("Failed to install OPTO demand: " + optodmd);
                    System.out.println("################################");
                } else {
                    for (NetRapAction action : actions) {
                        allActions.add(action);
                    }
                }
            }
            if (!optoDemands.isEmpty())
                parent.updateCacsim();

            for (NetRapDemand ipdmd : ipDemands) {
                List<NetRapAction> actions = addDemand(ipdmd, true, false);
                if (actions == null) {
                    System.out.println("################################");
                    System.out.println("Failed to install IP demand: " + ipdmd);
                    System.out.println("################################");
                } else {
                    for (NetRapAction action : actions) {
                        allActions.add(action);
                    }
                }
            }
            for (NetRapDemand emdmd : emptyDemands) {
                List<NetRapAction> actions = addDemand(emdmd, true, false);
                if (actions == null) {
                    System.out.println("################################");
                    System.out.println("Failed to install empty demand: " + emdmd);
                    System.out.println("################################");
                } else {
                    for (NetRapAction action : actions) {
                        allActions.add(action);
                    }
                }
            }

            parent.loadDesign(parent.getDesign());
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.ok().entity(allActions).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            System.out.println("DemandListPost: caught error " + e.toString());
            System.out.println(e.getCause());
            e.printStackTrace(System.out);
            // TODO: Delete any created demands if one of them fails
            /*
            for (NetRapDemand netRapDemand : createdDemands) {
                deleteDemand(netRapDemand, false);
            }
            parent.loadDesign(parent.getDesign());
            */
            if (System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() + " took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when creating demand " + e.toString()))
                    .build();
        }


    }

}
