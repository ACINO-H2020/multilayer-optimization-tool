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

import com.google.gson.reflect.TypeToken;
import com.wpl.xrapc.XrapDeleteRequest;
import com.wpl.xrapc.XrapErrorReply;
import com.wpl.xrapc.XrapException;
import com.wpl.xrapc.XrapGetReply;
import com.wpl.xrapc.XrapGetRequest;
import com.wpl.xrapc.XrapPeer;
import com.wpl.xrapc.XrapPostReply;
import com.wpl.xrapc.XrapPostRequest;
import com.wpl.xrapc.XrapPutRequest;
import com.wpl.xrapc.XrapReply;
import com.wpl.xrapc.XrapResource;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapDemand;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by eponsko on 2016-10-13.
 */
public class XrapApi {
    // Set default timeout to 5 seconds
    private static final int XRAP_TIMEOUT = 5;
    private XrapPeer _xrapClient;
    private Thread xrapThread;
    private JSON json;
    private Logger log;
    private String clientname;

    public XrapApi(String host, int port, String name) {
        log = LoggerFactory.getLogger(XrapPeer.class.getName());
        json = new JSON();
        this.clientname = name;
        _xrapClient = new XrapPeer(host, port, false);

        xrapThread = new Thread(_xrapClient);
        xrapThread.start();
        _xrapClient.setTimeout(XRAP_TIMEOUT);
        // let the client initialize properly before sending stuff
        _xrapClient.waitUntilReady();
    }

    public String register() {
        String json = "{\"name\":\"" + clientname + "\"}";
        XrapReply rep = post("/register", json);
        if (rep == null) {
            log.error("Error posting on /register");
            return "";
        } else if (rep instanceof XrapPostReply) {
            if (rep.getStatusCode() == com.wpl.xrapc.Constants.Created_201)
                return rep.toString();
        } else if (rep instanceof XrapErrorReply) {
            log.error("Got error " + rep.toString());
            return "";
        } else {
            log.error("Couldn't decide what type of reply:");
            log.error(rep.toString());
            return "";
        }
        return rep.toString();
    }


    public void addHandler(XrapResource handler) {
        _xrapClient.addHandler(handler);
    }
    public void addJaxRs(String packageToScan, ONOSPlugin plugin) {
        _xrapClient.registerJAXRS(packageToScan, plugin);
    }
    public void terminate() {
        _xrapClient.terminate();
    }

    public NetRapTopology getTopology() {
        XrapReply rep = null;

        rep = get("/topology", "");

        if (rep == null) {
            return null;
        }
        if (rep instanceof XrapGetReply) {
            return parseTopology(((XrapGetReply) rep).getBody());
        } else {
            log.error("Got error " + rep);
            return null;
        }
    }

    public NetRapTopology parseTopology(byte[] body) {
        String jsonData = new String(body);
        NetRapTopology top = json.deserialize(jsonData, NetRapTopology.class);
        return top;
    }
    public List<NetRapDemand> parseDemands(byte[] body) {
        String jsonData = new String(body);
        List<NetRapDemand> demands = json.deserialize(jsonData, new TypeToken<List<NetRapDemand>>(){}.getType());
        return demands;
    }
    public List<NetRapDemand> getDemands() {
        XrapReply rep = null;

        rep = get("/intents/", "");

        if (rep == null) {
            return null;
        }
        if (rep instanceof XrapGetReply) {
            return parseDemands(((XrapGetReply) rep).getBody());
        } else {
            log.error("Got error " + rep);
            return null;
        }
    }

    public XrapReply get(String resource, String body) {
        XrapGetRequest request = new XrapGetRequest(resource, body);
        return get(request);
    }

    public XrapReply get(XrapGetRequest req) {
        XrapReply rep = null;
        try {
            rep = _xrapClient.send(req);
        } catch (XrapException e) {
            log.error(e.toString());
            return null;
        } catch (InterruptedException e) {
            log.error(e.toString());
        }
        return rep;
    }

    public XrapReply post(String resource, String body) {
        XrapPostRequest request = new XrapPostRequest(resource, body);
        return post(request);
    }

    public XrapReply post(XrapPostRequest req) {
        XrapReply rep = null;
        try {
            rep = _xrapClient.send(req);
        } catch (XrapException e) {
            log.error(e.toString());
            return null;
        } catch (InterruptedException e) {
            log.error(e.toString());
        }
        return rep;
    }

    public XrapReply put(String resource, String body) {
        XrapPutRequest request = new XrapPutRequest(resource, body);
        return put(request);
    }

    public XrapReply put(XrapPutRequest req) {
        XrapReply rep = null;
        try {
            rep = _xrapClient.send(req);
        } catch (XrapException e) {
            log.error(e.toString());
            return null;
        } catch (InterruptedException e) {
            log.error(e.toString());
        }
        return rep;
    }

    public XrapReply delete(String resource) {
        XrapDeleteRequest request = new XrapDeleteRequest(resource);
        return delete(request);
    }

    public XrapReply delete(XrapDeleteRequest req) {
        XrapReply rep = null;
        try {
            rep = _xrapClient.send(req);
        } catch (XrapException e) {
            log.error(e.toString());
            return null;
        } catch (InterruptedException e) {
            log.error(e.toString());
        }
        return rep;
    }
}
