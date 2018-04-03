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


import com.wpl.xrapc.IXrap;
import es.upct.girtel.net2plan.plugins.onos.ONOSPlugin;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapMessage;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapPlan;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/plans")

@Api(description = "the plans API")


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2016-11-14T13:53:37.062Z")

public class PlansApi implements IXrap {
    private ONOSPlugin parent;

    public void setParent(Object parent) {
        this.parent = (ONOSPlugin) parent;
    }


    @DELETE
    
    
    
    @ApiOperation(value = "DeleteAllPlans", notes = "Delete all the plans", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Plans",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "All plans deleted OK", response = NetRapMessage.class),
        @ApiResponse(code = 400, message = "Status 400", response = NetRapMessage.class) })
    public Response plansDelete() {
        return Response.ok().entity("magic!").build();
    }

    @GET
    
    
    @Produces({ "application/json" })
    @ApiOperation(value = "GetAllPlans", notes = "Get a list of all plans, regardless of state", response = NetRapPlan.class, responseContainer = "List", authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Plans",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapPlan.class, responseContainer = "List"),
        @ApiResponse(code = 400, message = "Status 400", response = NetRapPlan.class, responseContainer = "List") })
    public Response plansGet(@QueryParam("state") String state) {
        return Response.ok().entity("magic!").build();
    }

    @DELETE
    @Path("/{planId}")
    
    
    @ApiOperation(value = "DeletePlan", notes = "Delete a plan regardless of state", response = NetRapMessage.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Plans",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapMessage.class) })
    public Response plansPlanIdDelete(@PathParam("planId") String planId) {
        return Response.ok().entity("magic!").build();
    }

    @GET
    @Path("/{planId}")
    
    @Produces({ "application/json" })
    @ApiOperation(value = "GetPlan", notes = "Get a single plan", response = NetRapPlan.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Plans",  })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapPlan.class),
        @ApiResponse(code = 404, message = "Status 404", response = NetRapPlan.class) })
    public Response plansPlanIdGet(@PathParam("planId") String planId) {
        return Response.ok().entity("magic!").build();
    }

    @POST
    
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @ApiOperation(value = "CreatePlan", notes = "Create a new plan based on current topology and demands", response = NetRapPlan.class, authorizations = {
        @Authorization(value = "basic")
    }, tags={ "Plans" })
    @ApiResponses(value = { 
        @ApiResponse(code = 200, message = "Status 200", response = NetRapPlan.class),
        @ApiResponse(code = 400, message = "Status 400", response = NetRapPlan.class) })
    public Response plansPost(@QueryParam("algorithm") String algorithm,NetRapPlan body) {
    	return Response.ok().entity("magic!").build();
    }
}

