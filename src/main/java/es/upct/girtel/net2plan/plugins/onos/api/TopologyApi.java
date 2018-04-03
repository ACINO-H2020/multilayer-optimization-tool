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


import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.SharedRiskGroup;
import com.wpl.xrapc.IXrap;
import es.upct.girtel.net2plan.plugins.onos.JSON;
import es.upct.girtel.net2plan.plugins.onos.ONOSPlugin;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapIdentifier;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapLink;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapMessage;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapNode;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapTopology;
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
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.net2plan.utils.Constants.RoutingType.SOURCE_ROUTING;
import static es.upct.girtel.net2plan.plugins.onos.utils.Constants.*;


@Path("/topology")

@Api(description = "the topology API")


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2016-11-14T14:11:38.242Z")

public class TopologyApi implements IXrap {
    private ONOSPlugin parent;
    private JSON json;

    public TopologyApi() {
        json = new JSON();
    }

    public void setParent(Object parent) {
        this.parent = (ONOSPlugin) parent;
    }

    private void logMsg(String msg){
        parent.updateOperationLog(msg);
        System.out.println(msg);
    }
    // look for the other end of this link and annotate both with
    // "bidirectionalCouple"
    public void updateBidir(Link linkA){
        NetPlan np = parent.getDesign();
        Node srcNodeA = linkA.getOriginNode();
        List<Link> potentialLinks = new ArrayList<>();
        //logMsg("\n#####################");
        //logMsg("Trying to find pair for link "+ linkA);
        //logMsg("Source node: " + linkA.getOriginNode().getId() + " destination " + linkA.getDestinationNode().getId());
        // In case there are multiple links between src/dst, find all potential ones
        Set<Link> incomingLinks = srcNodeA.getIncomingLinks(linkA.getLayer());
        //logMsg("Incomoing links to node " + srcNodeA.getId() + " are " + incomingLinks);
        for(Link linkB : incomingLinks){
//            logMsg("  Testing link " + linkB);
  //          logMsg("  Source node: " + linkB.getOriginNode().getId() + " destination " + linkB.getDestinationNode().getId());
            Node srcNodeB = linkB.getOriginNode();

            if(srcNodeB.getId() == linkA.getDestinationNode().getId()){
    //            logMsg("   Adding link to list of potentials");
                potentialLinks.add(linkB);
            }
        }

        if(potentialLinks.size() < 1){
            //logMsg(Utils.getCurrentMethodName() + " could not find link pair for " + linkA.getId());
            return;
        }
        /* else if (potentialLinks.size() == 1){
            logMsg(Utils.getCurrentMethodName() + " Found link pair " + linkA.getId() + " - " + potentialLinks.get(0).getId());
            linkA.setAttribute("bidirectionalCouple",Long.toString(potentialLinks.get(0).getId()));
            potentialLinks.get(0).setAttribute("bidirectionalCouple",Long.toString(linkA.getId()));
            return;
        } */
        else {
            // multiple links, look at their src/dst ports to find a match
            String srcPortA = linkA.getAttribute("srcPort");
            String dstPortA = linkA.getAttribute("dstPort");
            for(Link linkB: potentialLinks){
                String srcPortB = linkB.getAttribute("srcPort");
                String dstPortB = linkB.getAttribute("dstPort");
                if(srcPortA.equals(dstPortB) && dstPortA.equals(srcPortB)){
                   // logMsg(Utils.getCurrentMethodName() + " found matching link pair " + linkA.getId() + " - " + linkB.getId());
                    //linkA.setAttribute("bidirectionalCouple",Long.toString(linkB.getId()));
                    //linkB.setAttribute("bidirectionalCouple",Long.toString(linkA.getId()));
                    np.getLinkFromId(linkA.getId()).setAttribute("bidirectionalCouple",Long.toString(linkB.getId()));
                    np.getLinkFromId(linkB.getId()).setAttribute("bidirectionalCouple",Long.toString(linkA.getId()));

                    Link tmp = np.getLinkFromId(linkA.getId());
                    //logMsg("Set attributes: " + np.getLink(linkA.getIndex()).getAttributes());
                    //logMsg("Set attributes: " + np.getLink(linkB.getIndex()).getAttributes());
              //      logMsg("  Link A("+tmp.getLayer().getName()+") id: " + tmp.getId() + " attributes before updateBidir " + tmp.getAttributes());
                    tmp = np.getLinkFromId(linkB.getId());
               //     logMsg("  Link B("+tmp.getLayer().getName()+") id: " + tmp.getId() + " attributes before updateBidir " + tmp.getAttributes());
                    return;
                }
            }
        }
        logMsg(Utils.getCurrentMethodName() + "WARNING: no matching link pairs found for " + linkA.getId());
    }

    public Node addNode(NetRapNode body, boolean update) {
        double x = body.getLongitude();
        double y = body.getLatitude();
        NetPlan netPlan = parent.getDesign();
        Node newnode = netPlan.addNode(x, y, body.getName(), null); // attribute map
        if (body.getMTBF() != null && body.getMTTR() != null) {
            double mttr = body.getMTTR();
            double mtbf = body.getMTBF();
            SharedRiskGroup srg = netPlan.addSRG(mtbf, mttr, null);
            srg.addNode(newnode);
        }
        Map<String, String> attributes = (Map<String, String>) body.getAttributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                newnode.setAttribute(key, attributes.get(key));
            }
        }
        if(update)
            parent.loadDesign(netPlan);
        return newnode;
    }

    public static NetRapNode nodeToNetRapNode(Node node) {
        NetRapNode netRapNode = new NetRapNode();
        netRapNode.setName(node.getName());
        netRapNode.setLongitude(node.getXYPositionMap().getY());
        netRapNode.setLatitude(node.getXYPositionMap().getX());
        HashMap<String,String>  netRapAttributes = new HashMap<>();
        if(node.getAttributes() != null) {
            for (String key: node.getAttributes().keySet()){
                netRapAttributes.put(key, node.getAttribute(key));
            }
        }
        if(node.getSRGs() != null){
            for(SharedRiskGroup srg :node.getSRGs()){
                netRapNode.setMTBF(srg.getMeanTimeToFailInHours());
                netRapNode.setMTTR(srg.getMeanTimeToRepairInHours());
                netRapNode.setSRG(Long.toString(srg.getId()));
            }

        }

        netRapNode.setAttributes(netRapAttributes);
        return netRapNode;
    }

    public static NetRapLink linkToNetRapLink(Link link) {
        NetRapLink netRapLink = new NetRapLink();
        netRapLink.setCapacity(link.getCapacity());
        netRapLink.setDst(link.getDestinationNode().getName());
        netRapLink.setSrc(link.getOriginNode().getName());
        netRapLink.setCapacity(link.getCapacity());
        netRapLink.setIdentifier(Long.toString(link.getId()));
        netRapLink.setLayer(link.getLayer().getIndex());
        netRapLink.setLengthInKm(link.getLengthInKm());
        netRapLink.setPropagationSpeed((int)link.getPropagationSpeedInKmPerSecond());

        if(link.isUp())
            netRapLink.setActive(true);
        else
            netRapLink.setActive(false);

        Map<String, String> linkAttributes = link.getAttributes();
        HashMap<String, String> hashMap = new HashMap<String, String>();
        if(linkAttributes != null) {
            for (String key : linkAttributes.keySet()) {
                hashMap.put(key, linkAttributes.get(key));
            }
        }
        netRapLink.setAttributes(hashMap);

        if(link.getSRGs() != null){
            for(SharedRiskGroup srg :link.getSRGs()){
                netRapLink.setMTBF(srg.getMeanTimeToFailInHours());
                netRapLink.setMTTR(srg.getMeanTimeToRepairInHours());
                netRapLink.setSrg(Long.toString(srg.getId()));
            }

        }

        return netRapLink;
    }

    public Link addLink(NetRapLink netRapLink, boolean update) throws Net2PlanException {
        NetPlan netPlan = parent.getDesign();

        Node orig = netPlan.getNodeByName(netRapLink.getDst());
        Node dest = netPlan.getNodeByName(netRapLink.getSrc());
        if(orig == null){
            throw new Net2PlanException("Node " + netRapLink.getDst() + " not found!");
        }
        if(dest == null){
            throw new Net2PlanException("Node " + netRapLink.getSrc() + " not found!");
        }
        NetworkLayer layer, _wdmLayer, _ipLayer;

        try {
            _wdmLayer = netPlan.getNetworkLayer(WDM_LAYER_NAME);
            if (_wdmLayer == null) {
                _wdmLayer = netPlan.getNetworkLayerDefault();
                _wdmLayer.setName(WDM_LAYER_NAME);
                _wdmLayer.setDescription("WDM layer obtained from ONOS");
                netPlan.setNetworkLayerDefault(_wdmLayer);
                netPlan.setRoutingType(SOURCE_ROUTING, _wdmLayer);
            }
            _ipLayer = netPlan.getNetworkLayer(IP_LAYER_NAME);
            if (_ipLayer == null) {
                _ipLayer = netPlan.addLayer(IP_LAYER_NAME, "IP layer obtained from ONOS", null, null, null);
            }
            netPlan.setRoutingType(SOURCE_ROUTING, _ipLayer);

        } catch (Net2PlanException e){
            throw new Net2PlanException("Could not create layers!");
        }
        switch (netRapLink.getLayer()) {
            case WDM_LAYER_INDEX:
                layer = _wdmLayer;
                break;
            case IP_LAYER_INDEX:
                layer = _ipLayer;
                break;
            default:
                parent.updateOperationLog("ERROR: Could not determine layer, got value: " + netRapLink.getLayer());
                return null;
        }


        Link newLink = netPlan.addLink(orig, dest,
                netRapLink.getCapacity(), // Capacity, get from port type?
                netRapLink.getLengthInKm(), // Length, no value
                netRapLink.getPropagationSpeed(), // Propagation speed, 200000km/s
                null, // attribute map
                layer); // Optional layer
        Map<String, String> attributes = (Map<String, String>) netRapLink.getAttributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                newLink.setAttribute(key, attributes.get(key));
            }
        }
        if (netRapLink.getMTBF() != null && netRapLink.getMTTR() != null) {
            double mttr = netRapLink.getMTTR();
            double mtbf = netRapLink.getMTBF();
            SharedRiskGroup srg = netPlan.addSRG(mtbf, mttr, null);
            srg.addLink(newLink);
        }
        if(netRapLink.getActive())
            newLink.setFailureState(true);
        else
            newLink.setFailureState(false);

        //logMsg("\nLink("+newLink.getLayer().getName()+") id: " + newLink.getId() + " attributes before updateBidir " + newLink.getAttributes());
        updateBidir(newLink);
        //logMsg("Link("+newLink.getLayer().getName()+") id: " + newLink.getId() + " attributes after updateBidir " + newLink.getAttributes());
        if(update)
            parent.loadDesign(netPlan);
        return newLink;
    }
    public boolean updateTopology(NetRapTopology topo) {
        NetPlan netPlan = parent.getDesign();
        Stopwatch timer = Stopwatch.createStarted();
        netPlan.reset();
        if(System.getProperty("profile") != null)
            parent.updateOperationLog("  " + Utils.getCurrentMethodName() + " reset " + " took: " + timer.stop());
        NetworkLayer _wdmLayer, _ipLayer;
        try {
             _wdmLayer = netPlan.getNetworkLayer(WDM_LAYER_NAME);
            if (_wdmLayer == null) {
                _wdmLayer = netPlan.getNetworkLayerDefault();
                _wdmLayer.setName(WDM_LAYER_NAME);
                _wdmLayer.setDescription("WDM layer obtained from ONOS");
                netPlan.setNetworkLayerDefault(_wdmLayer);
                netPlan.setRoutingType(SOURCE_ROUTING, _wdmLayer);
            }
             _ipLayer = netPlan.getNetworkLayer(IP_LAYER_NAME);
            if (_ipLayer == null) {
                _ipLayer = netPlan.addLayer(IP_LAYER_NAME, "IP layer obtained from ONOS", null, null, null);
            }
            netPlan.setRoutingType(SOURCE_ROUTING, _ipLayer);

        } catch (Net2PlanException e){

            logMsg("updateTopology, error getting layers!");
            logMsg(e.toString());
            logMsg(e.getMessage());
            return false;
        }
        HashMap<String, Long> nodeIds = new HashMap<>();
        HashMap<String, Long> linkIds = new HashMap<>();

        try {


   //         netPlan.removeAllNodes();
            NetworkLayer nl = netPlan.getNetworkLayerDefault();
            netPlan.setDescription("NetPlan obtained from ONOS");
            netPlan.setDescription("IP layer obtained from ONOS", _ipLayer);
            netPlan.setDescription("WDM layer obtained from ONOS", _wdmLayer);
            netPlan.setNetworkName("ONOS network");
            parent.getTopologyPanel().getCanvas().updateTopology(netPlan);
            parent.getTopologyPanel().getCanvas().refresh();
            parent.updateNetPlanView();

        } catch (Net2PlanException e){
            logMsg("updateTopology, error removing existing nodes");
            logMsg(e.toString());
            logMsg(e.getMessage());
            return false;
        }
        int nodeCount = 0;
        try {
            timer = Stopwatch.createStarted();
            for (NetRapNode netRapNode : topo.getNodes()) {
                Node newnode = addNode(netRapNode,false);
                nodeCount++;
                nodeIds.put(netRapNode.getName(), newnode.getId());
            }
            if(System.getProperty("profile") != null)
                parent.updateOperationLog("  " + Utils.getCurrentMethodName() + " add nodes " + " took: " + timer.stop());
        } catch (Net2PlanException e){
            netPlan.removeAllNodes();
            logMsg("updateTopology, error adding nodes!");
            logMsg(e.toString());
            logMsg(e.getMessage());
            return false;
        }
        int linkCount = 0;
        try{
            timer = Stopwatch.createStarted();
            for (NetRapLink netRapLink : topo.getLinks()) {
                Link newLink = addLink(netRapLink, false);
                linkIds.put(netRapLink.getDst() + " - " + netRapLink.getSrc(), newLink.getId());
            }
            if(System.getProperty("profile") != null)
                parent.updateOperationLog("  " + Utils.getCurrentMethodName() + " add links " + " took: " + timer.stop());
        } catch(Net2PlanException e){
            netPlan.removeAllNodes();
            logMsg("updateTopology, error adding links!");
            logMsg(e.toString());
            logMsg(e.getMessage());
            return false;
        }
        try {

            parent.loadDesign(netPlan);
            parent.updateGUIandPos();

        } catch (Net2PlanException e){
            logMsg("updateTopology, error updating GUI!");
            logMsg(e.toString());
            logMsg(e.getMessage());
            return false;
        }

        return true;
    }




    @GET
    @ApiOperation(value = "GetTopology", notes = "Get the current topology", response = NetRapTopology.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Full topology", response = NetRapTopology.class),
        @ApiResponse(code = 404, message = "Status 404", response = NetRapTopology.class) })
    public Response topologyGet() {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();

        try {
            NetPlan np = parent.getDesign();
            NetRapTopology netRapTopology = new NetRapTopology();

            ArrayList<NetRapLink> netRapLinks = new ArrayList<NetRapLink>();

            NetworkLayer wdmLayer = np.getNetworkLayer("WDM");
            if(wdmLayer != null) {
                List<Link> wdmLinks = np.getLinks(wdmLayer);
                for (Link link : wdmLinks) {
                    NetRapLink nrlink = linkToNetRapLink(link);
                    nrlink.setLayer(0);
                    netRapLinks.add(nrlink);
                }
            }
            NetworkLayer ipLayer = np.getNetworkLayer("IP");
            if(ipLayer != null) {
                List<Link> IPLinks = np.getLinks(ipLayer);
                for (Link link : IPLinks) {
                    NetRapLink nrlink = linkToNetRapLink(link);
                    nrlink.setLayer(1);
                    netRapLinks.add(nrlink);
                }
            }

            netRapTopology.setLinks(netRapLinks);


            ArrayList<NetRapNode> netRapNodes = new ArrayList<>();
            List<Node> nodes = np.getNodes();
            for (Node node : nodes) {
                NetRapNode netRapNode = nodeToNetRapNode(node);
                if (netRapNode != null) {
                    netRapNodes.add(netRapNode);
                } else {
                    logMsg("Couldn't create NetRapNode from " + node);
                    if(System.getProperty("profile") != null)
                        parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
                    return Response.serverError().entity(Utils.nrMsg("Couldn't create NetRapNode from " + node)).build();
                }

            }
            netRapTopology.setNodes(netRapNodes);

            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.ok().entity(netRapTopology).build();

        } catch (Exception e) {
            logMsg("caught exception: " + e.toString());
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError().entity(Utils.nrMsg("Exception caught: " + e.toString())).build();
        }


    }

    @DELETE
    @Path("/link")
    
    
    @ApiOperation(value = "DeleteAllLinks", notes = "Delete all the links in the topology", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class) })
    public Response topologyLinkDelete() {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        for(long layerId: netPlan.getNetworkLayerIds()){
            NetworkLayer networkLayer = netPlan.getNetworkLayerFromId(layerId);
            netPlan.removeAllLinks(networkLayer);
        }

        parent.loadDesign(netPlan);
        parent.updateGUIandPos();
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("done")).build();
    }

    @GET
    @Path("/link")
    
    
    @ApiOperation(value = "GetAllLinks", notes = "Return all links in the topology", response = NetRapLink.class, responseContainer = "List", authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapLink.class, responseContainer = "List"),
        @ApiResponse(code = 404, message = "Status 404", response = NetRapLink.class, responseContainer = "List") })
    public Response topologyLinkGet() {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();

        NetPlan np = parent.getDesign();
        ArrayList<NetRapLink> netRapLinks = new ArrayList<NetRapLink>();

        NetworkLayer wdmLayer = np.getNetworkLayer("WDM");

        List<Link> wdmLinks = np.getLinks(wdmLayer);
        for (Link link : wdmLinks) {
            NetRapLink nrlink = linkToNetRapLink(link);
            nrlink.setLayer(0);
            netRapLinks.add(nrlink);
        }
        NetworkLayer ipLayer = np.getNetworkLayer("IP");
        List<Link> IPLinks = np.getLinks(ipLayer);
        for (Link link : IPLinks) {
            NetRapLink nrlink = linkToNetRapLink(link);
            nrlink.setLayer(1);
            netRapLinks.add(nrlink);
        }
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(netRapLinks).build();
    }

    @DELETE
    @Path("/link/{linkId}")
    
    
    @ApiOperation(value = "DeleteLink", notes = "Remove a particular link", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class) })
    public Response topologyLinkLinkIdDelete(@PathParam("linkId") String linkId) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Long linkLongId;
        try {
            linkLongId = Long.parseLong(linkId);
        } catch (NumberFormatException e){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Could not parse identifier " + linkId + ". Should be a long integer."))
                    .build();
        }


        Link link = netPlan.getLinkFromId(linkLongId);
        if(link == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Link " + linkId + " not found"))
                    .build();
        }

        link.remove();
        parent.loadDesign(netPlan);
        parent.updateGUIandPos();
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("done")).build();
    }

    @GET
    @Path("/link/{linkId}")
    
    
    @ApiOperation(value = "GetLink", notes = "Get a particular link", response = NetRapLink.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Found link", response = NetRapLink.class),
        @ApiResponse(code = 404, message = "Could not find link", response = NetRapLink.class) })
    public Response topologyLinkLinkIdGet(@PathParam("linkId") String linkId) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Long linkLongId;
        try {
            linkLongId = Long.parseLong(linkId);
        } catch (NumberFormatException e){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Could not parse identifier " + linkId + ". Should be a long integer."))
                    .build();
        }


        Link link = netPlan.getLinkFromId(linkLongId);
        if(link == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Link " + linkId + " not found"))
                    .build();
        }
        NetRapLink netRapLink = linkToNetRapLink(link);
        if(netRapLink == null) {
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg("Error converting link"))
                    .build();
        }

        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(netRapLink).build();

    }

    @PUT
    @Path("/link/{linkId}")


    @ApiOperation(value = "UpdateLink", notes = "Update a particular link", response = NetRapLink.class, tags = {"Topology",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Link succesfully updated", response = NetRapLink.class),
            @ApiResponse(code = 404, message = "Could not find link to update", response = NetRapLink.class)})
    public Response topologyLinkLinkIdPut(@PathParam("linkId") String linkId, NetRapLink body) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Long linkLongId;
        try {
            linkLongId = Long.parseLong(linkId);
        } catch (NumberFormatException e){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Could not parse identifier " + linkId + ". Should be a long integer."))
                    .build();
        }


        Link link = netPlan.getLinkFromId(linkLongId);
        if(link == null){
            if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Link " + linkId + " not found"))
                    .build();
        }

        if(body.getCapacity() != null)
            link.setCapacity(body.getCapacity());
        if(body.getLengthInKm() != null)
            link.setLengthInKm(body.getLengthInKm());
        if(body.getPropagationSpeed() != null)
            link.setPropagationSpeedInKmPerSecond(body.getPropagationSpeed());

        Map<String, String> attributes = (Map<String, String>) body.getAttributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                link.setAttribute(key, attributes.get(key));
            }
        }
        parent.loadDesign(netPlan);
        parent.updateCacsim();
        logMsg("Updated cacsim");
        NetRapLink netRapLink = linkToNetRapLink(link);
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(netRapLink).build();
    }

    @POST
    @Path("/link")

    @ApiOperation(value = "CreateLink", notes = "Create a link between existing nodes in the topology", response = NetRapIdentifier.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200, Unique identifier generated by Net2Plan", response = NetRapIdentifier.class) })
    public Response topologyLinkPost(NetRapLink body) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        Link newlink;

        try {
            newlink = addLink(body, true);
        } catch (Net2PlanException e) {
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg("Error adding link: " + e.toString()))
                    .build();
        } catch(Exception e){
            StackTraceElement[] trace = e.getStackTrace();
            String trStr = Arrays.toString(trace);
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg("Error adding link: " + e.toString() + " trace: " + trStr))
                    .build();
        }
        if(newlink == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg("Error creating new link"))
                    .build();
        }
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrId(newlink.getId())).build();
    }

    @DELETE
    @Path("/node")
    
    
    @ApiOperation(value = "DeleteAllNodes", notes = "Delete all nodes from the topology", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class) })
    public Response topologyNodeDelete() {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        netPlan.removeAllNodes();
        parent.loadDesign(netPlan);
        parent.updateGUIandPos();
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("done")).build();
    }

    @GET
    @Path("/node")
    
    
    @ApiOperation(value = "GetAllNodes", notes = "Get a list of all nodes", response = NetRapNode.class, responseContainer = "List", authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "List of nodes", response = NetRapNode.class, responseContainer = "List"),
        @ApiResponse(code = 404, message = "No nodes found", response = NetRapNode.class, responseContainer = "List") })
    public Response topologyNodeGet() {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan np = parent.getDesign();
        ArrayList<NetRapNode> netRapNodes = new ArrayList<NetRapNode>();

        List<Node> nodeList = np.getNodes();
        if(nodeList == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError().entity(Utils.nrMsg("No nodes")).build();
        }
        for (Node node : nodeList) {
            NetRapNode  netRapNode = nodeToNetRapNode(node);
            netRapNodes.add(netRapNode);
        }
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(netRapNodes).build();

    }

    @DELETE
    @Path("/node/{nodeId}")
    
    
    @ApiOperation(value = "DeleteNode", notes = "Delete a node", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Successfully deleted node", response = NetRapMessage.class),
        @ApiResponse(code = 404, message = "Could not find node to delete", response = NetRapMessage.class) })
    public Response topologyNodeNodeIdDelete(@PathParam("nodeId") String nodeId) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Node node = netPlan.getNodeByName(nodeId);
        try {
            node.remove();
        } catch (UnsupportedOperationException e){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg("Internal error removing node. " + e.toString()))
                    .build();
        }
        parent.loadDesign(netPlan);
        parent.updateGUIandPos();
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("done")).build();
    }

    @GET
    @Path("/node/{nodeId}")
    
    
    @ApiOperation(value = "GetNode", notes = "Get information about a node", response = NetRapNode.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "A single node", response = NetRapNode.class),
        @ApiResponse(code = 404, message = "Node not found", response = NetRapNode.class) })
    public Response topologyNodeNodeIdGet(@PathParam("nodeId") String nodeId) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Node node = netPlan.getNodeByName(nodeId);
        if (node == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Node " + nodeId + " not found"))
                    .build();
        }
        NetRapNode netRapNode = nodeToNetRapNode(node);
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(netRapNode).build();
    }

    @PUT
    @Path("/node/{nodeId}")
    
    
    @ApiOperation(value = "UpdateNode", notes = "Update the information on a single node", response = NetRapNode.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Node was successfully updated", response = NetRapNode.class),
        @ApiResponse(code = 404, message = "Node could not be found", response = NetRapNode.class) })
    public Response topologyNodeNodeIdPut(@PathParam("nodeId") String nodeId,NetRapNode body) {

        Stopwatch timer = Stopwatch.createStarted();

        logMsg(Utils.getCurrentMethodName() + " called");

        NetPlan netPlan = parent.getDesign();
        Node node = netPlan.getNodeByName(nodeId);
        if (node == null){
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.serverError()
                    .status(Response.Status.NOT_FOUND)
                    .entity(Utils.nrMsg("Node " + nodeId + " not found"))
                    .build();
        }

        // Only allows updating MTBF/MTTR and attributes!
        if (body.getMTBF() != null && body.getMTTR() != null) {
            double mttr = body.getMTTR();
            double mtbf = body.getMTBF();
            SharedRiskGroup srg = netPlan.addSRG(mtbf, mttr, null);
            srg.addNode(node);
        }
        Map<String, String> attributes = (Map<String, String>) body.getAttributes();
        if (attributes != null) {
            for (String key : attributes.keySet()) {
                node.setAttribute(key, attributes.get(key));
            }
        }
        parent.loadDesign(netPlan);
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrMsg("done")).build();
    }

    @POST
    @Path("/node")
    
    
    @ApiOperation(value = "CreateNode", notes = "Create a new Node", response = NetRapIdentifier.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200 Unique node identifier generated by n2p", response = NetRapIdentifier.class) })
    public Response topologyNodePost(NetRapNode body) {
        logMsg(Utils.getCurrentMethodName() + " called");
        Stopwatch timer = Stopwatch.createStarted();
        NetPlan netPlan = parent.getDesign();
        Node newnode = addNode(body,true);
        logMsg("added node " + body.getName() + " assigned id " + newnode.getId());
        parent.updateGUIandPos();
        parent.updateCacsim();
        logMsg("Updated cacsim");
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.ok().entity(Utils.nrId(newnode.getId())).build();
    }

    @POST
    @Consumes({ "application/json" })
    @Produces({ "application/json" })

    @ApiOperation(value = "CreateTopology", notes = "Create topology from scratch", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Topology" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Successfully created topology", response = NetRapMessage.class),
        @ApiResponse(code = 400, message = "Could not create Topology", response = NetRapMessage.class) })
    public Response topologyPost(NetRapTopology body) {
        Stopwatch timer = Stopwatch.createStarted();
        logMsg(Utils.getCurrentMethodName() + " called");

        String errormsg = "Unknown error";

        acceptTopology:
        {
            boolean successful = updateTopology(body);
            if (!successful)
                break acceptTopology;

            logMsg("Created topology with " + body.getLinks().size() + " links and " + body.getNodes().size() + " nodes");
            parent.updateCacsim();
            logMsg("Updated cacsim");
            if(System.getProperty("debug") != null)
                Utils.writeToFile(parent.getDesign());
            if(System.getProperty("profile") != null)
                parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
            return Response.ok(Utils.nrMsg("done")).build();
        }
        if(System.getProperty("profile") != null)
            parent.updateOperationLog(Utils.getCurrentMethodName() +" took: " + timer.stop());
        return Response.serverError().entity(Utils.nrMsg("Error updating topology")).build();
    }
}

