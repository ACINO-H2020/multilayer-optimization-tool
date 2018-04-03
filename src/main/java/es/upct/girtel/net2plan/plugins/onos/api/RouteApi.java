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
import com.net2plan.interfaces.networkDesign.Route;
import com.wpl.xrapc.IXrap;
import es.upct.girtel.net2plan.plugins.onos.ONOSPlugin;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapIdentifier;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapLink;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapMessage;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapNode;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapRoute;
import es.upct.girtel.net2plan.plugins.onos.utils.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Path("/route")

@Api(description = "The route API")


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2016-11-14T14:11:38.242Z")

public class RouteApi implements IXrap {
    private ONOSPlugin parent;


    public static NetRapRoute routeToNetRapRoute(Route route) throws Net2PlanException{
        NetRapRoute netRapRoute = new NetRapRoute();
        Demand coupledDemand = route.getDemand();
        netRapRoute.setLayer(route.getLayer().getIndex());

        if(coupledDemand == null) {
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
        netRapRoute.setDemandId(Long.toString(demandId));
        netRapRoute.setOccupiedCapacity(route.getOccupiedCapacity());
        List<Link> links = route.getSeqLinksRealPath();
        List<Node> nodes = route.getSeqNodesRealPath();
        List<NetRapNode> netRapNodes = new ArrayList<>();
        List<NetRapLink> netRapLinks = new ArrayList<>();

        for(Link link: links){
            NetRapLink netRapLink = TopologyApi.linkToNetRapLink(link);
            netRapLinks.add(netRapLink);
        }

        for(Node node: nodes){
            NetRapNode netRapNode = TopologyApi.nodeToNetRapNode(node);
            netRapNodes.add(netRapNode);
        }

        netRapRoute.setLinks(netRapLinks);
        netRapRoute.setNodes(netRapNodes);

        return netRapRoute;
    }




    @GET

    @ApiOperation(value = "GetAllRoutes", notes = "", response = NetRapRoute.class, responseContainer = "List", authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Route",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapRoute.class, responseContainer = "List") })
    public Response routeGet() {
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");
        List<NetRapRoute> netRapRouteList = new ArrayList<>();
        NetPlan netPlan = parent.getDesign();
        NetworkLayer _ipLayer = netPlan.getNetworkLayer("IP");
        if (_ipLayer == null)
            return Response.ok().entity(Utils.nrMsg("No IP layer found")).build();

        NetworkLayer _wdmLayer = netPlan.getNetworkLayer("WDM");
        if (_ipLayer == null)
            return Response.ok().entity(Utils.nrMsg("No WDM layer found")).build();
        try {
            List<Route> iproutes = netPlan.getRoutes(_ipLayer);
            List<Route> wdmroutes = netPlan.getRoutes(_wdmLayer);

            for (Route iproute : iproutes) {
                NetRapRoute netRapRoute = routeToNetRapRoute(iproute);
                netRapRouteList.add(netRapRoute);
            }
            for (Route wdmroute : wdmroutes) {
                NetRapRoute netRapRoute = routeToNetRapRoute(wdmroute);
                netRapRouteList.add(netRapRoute);
            }

        } catch(Net2PlanException e){
            parent.updateOperationLog(Utils.getCurrentMethodName() + " exception " + e.toString());
            System.out.println(Utils.getCurrentMethodName() + " exception " + e.toString());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg(e.toString()))
                    .build();
        } catch (Exception e){
            parent.updateOperationLog(Utils.getCurrentMethodName() + " exception " + e.toString());
            System.out.println(Utils.getCurrentMethodName() + " exception " + e.toString());
            return Response.serverError()
                    .status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Utils.nrMsg(e.toString()))
                    .build();
        }

        return Response.ok().entity(netRapRouteList).build();

    }

    @POST
    
    
    
    @ApiOperation(value = "CreateRoute", notes = "", response = NetRapIdentifier.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Route" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapIdentifier.class) })
    public Response routePost() {
        return Response.ok().entity("magic!").build();
    }

    @GET
    @Path("/{routeId}")
    @Produces({"application/json"})
    @ApiOperation(value = "GetRoute", notes = "Get an individual route", response = NetRapRoute.class, authorizations = {
            @Authorization(value = "basic")
    }, tags = {"Route",})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Status 200", response = NetRapRoute.class),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapMessage.class)})
    public Response demandsDemandIdGet(@PathParam("routeId") Long routeId) {
        parent.updateOperationLog(Utils.getCurrentMethodName() + " called");
        System.out.println(Utils.getCurrentMethodName() + " called");

        NetPlan netPlan = parent.getDesign();
        try{
            Route route = netPlan.getRouteFromId(routeId);

            if (route == null)
                return Response.serverError()
                        .status(Response.Status.NOT_FOUND)
                        .entity(Utils.nrMsg("Route " + routeId + " could not be found"))
                        .build();

            NetRapRoute netRapRoute = routeToNetRapRoute(route);

            return Response.ok().entity(netRapRoute).build();
        } catch (Net2PlanException e) {
            return Response.serverError()
                    .status(Response.Status.BAD_REQUEST)
                    .entity(Utils.nrMsg("Exception when getting route " + e.toString()))
                    .build();
        }

    }

    @Override
    public void setParent(Object o) {
        parent = (ONOSPlugin) o;
    }
}

