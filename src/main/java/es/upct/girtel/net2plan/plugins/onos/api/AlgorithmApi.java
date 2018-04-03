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
import es.upct.girtel.net2plan.plugins.onos.model.NetRapAlgo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/algorithm")

@Api(description = "the algorithm API")


@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJAXRSSpecServerCodegen", date = "2016-11-14T14:11:38.242Z")

public class AlgorithmApi implements IXrap {
    private ONOSPlugin parent;

    public void setParent(Object parent) {
        this.parent = (ONOSPlugin) parent;
    }

    @GET
    @Produces({"application/json"})
    @ApiOperation(value = "GetAllAlgorithms", notes = "Get a list of all algorithms", response = NetRapAlgo.class, responseContainer = "List", tags = {"Plans"})
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "List of supported planning algorithms", response = NetRapAlgo.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Status 404", response = NetRapAlgo.class, responseContainer = "List")})
    public Response algorithmGet() {
        return Response.ok().entity("magic!").build();
    }
}

