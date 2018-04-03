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

package es.upct.girtel.net2plan.plugins.onos;


import es.upct.girtel.net2plan.plugins.onos.api.AlgorithmApi;
import es.upct.girtel.net2plan.plugins.onos.api.DemandApi;
import es.upct.girtel.net2plan.plugins.onos.api.PlansApi;
import es.upct.girtel.net2plan.plugins.onos.api.RouteApi;
import es.upct.girtel.net2plan.plugins.onos.api.TopologyApi;
import io.swagger.jaxrs.config.BeanConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.net.URI;

/**
 * Main class.
 *
 */
public class WebTest {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://0.0.0.0:7780/api/";
    public HttpServer server ;
    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public HttpServer startServer(ONOSPlugin parent) {

        // create a resource config that scans for JAX-RS resources and providers
        // in com.underdog.jersey.grizzly package
        //final ResourceConfig rc = new ResourceConfig().packages("es.upct.girtel.net2plan.plugins.onos");
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI



        server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), new ApiApplication(parent));
        return server;
    }
    @ApplicationPath("/api")
    public class ApiApplication extends ResourceConfig {

        public ApiApplication(ONOSPlugin parent) {
            super(MultiPartFeature.class);
            packages("es.upct.girtel.net2plan.plugins.onos.api", "es.upct.girtel.net2plan.plugins.onos.model");
            setApplicationName("NetRap interface");
            register(io.swagger.jaxrs.listing.ApiListingResource.class);
            register(io.swagger.jaxrs.listing.SwaggerSerializers.class);
            CORSResponseFilter cors = new CORSResponseFilter();
            PlansApi plans = new PlansApi();
            plans.setParent(parent);
            TopologyApi topo = new TopologyApi();
            topo.setParent(parent);
            AlgorithmApi algo = new AlgorithmApi();
            algo.setParent(parent);
            RouteApi route = new RouteApi();
            route.setParent(parent);
            DemandApi demands = new DemandApi();
            demands.setParent(parent);
            GsonMessageBodyHandler jsonProvider = new GsonMessageBodyHandler();
            registerInstances(plans, topo, algo, route, demands, cors, jsonProvider);

            register(LoggingFilter.class);
            //register(GsonMessageBodyHandler.class);
            BeanConfig beanConfig = new BeanConfig();
            beanConfig.setVersion("1.0.2");
            beanConfig.setTitle("swagger-app");
            beanConfig.setSchemes(new String[]{"http"});
            beanConfig.setHost("0.0.0.0:7780");
            beanConfig.setBasePath("/api");
            beanConfig.setResourcePackage("io.swagger.resources,es.upct.girtel.net2plan.plugins.onos.api,es.upct.girtel.net2plan.plugins.onos.model");
            beanConfig.setScan(true);
            beanConfig.setContact("ponsko");
            beanConfig.setPrettyPrint(true);
        }
    }


    public class CORSResponseFilter
            implements ContainerResponseFilter {

        public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
                throws IOException {

            MultivaluedMap<String, Object> headers = responseContext.getHeaders();

            headers.add("Access-Control-Allow-Origin", "*");
            //headers.add("Access-Control-Allow-Origin", "http://podcastpedia.org"); //allows CORS requests only coming from podcastpedia.org
            headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");
            headers.add("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, X-Codingpedia");
        }

    }
}