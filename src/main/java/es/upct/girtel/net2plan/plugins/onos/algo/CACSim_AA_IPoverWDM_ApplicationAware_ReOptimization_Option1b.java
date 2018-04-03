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

import cern.colt.matrix.tdouble.DoubleMatrix2D;
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
import com.net2plan.interfaces.simulation.IEventProcessor;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.interfaces.simulation.SimEvent.DemandRemove;
import com.net2plan.libraries.GraphUtils;
import com.net2plan.libraries.WDMUtils;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Triple;
import es.upct.girtel.net2plan.plugins.onos.utils.RouteList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CACSim_AA_IPoverWDM_ApplicationAware_ReOptimization_Option1b extends IEventProcessor {
    /* Network data */
    private double propagationSpeed = 200000;
    private int K_wdmLayer;
    private WDMUtils.TransponderTypesInfo TRx;
    private String pathSelectionPolicy;
    private boolean AgUseDelay;
    private String oppAlgorithm;
    private FlexgridPolicy flexgridPolicy;
    private boolean removeEmptyIPLinks;
    private boolean executeIPOPT = true;
    private boolean executeIPP = true;
    private boolean executeOPP = true;
    private boolean IgnoreAvailabilityandLatency = false;
    private double routerDelay;
    private boolean negotiate;
    private NetworkLayer wdmLayer, ipLayer;
    private Map<Pair<Node, Node>, List<List<Link>>> cpl_wdmLayer = new LinkedHashMap<>();
    private DoubleMatrix2D wavelengthFiberOccupancy;
    private List<Node> nodes;
    private List<Node> ipNodes;
    /* Parameters */
    private int K_ipLayer;
    private double PRECISION_FACTOR;
    /* Input parameters */
    private InputParameter _executeIPOPT = new InputParameter("ExecuteIPOPT", true, "Whether to execute IPOPT");
    private InputParameter _executeIPP = new InputParameter("ExecuteIPP", true, "Whether to execute IPP");
    private InputParameter _executeOPP = new InputParameter("ExecuteOPP", true, "Whether to execute OPP");
    private InputParameter _K_ipLayer = new InputParameter("K_ipLayer", "50", "Maximum number of candidate paths to setup IP routes");
    private InputParameter _K_wdmLayer = new InputParameter("K_wdmLayer", "5", "Maximum number of candidate paths to setup lightpaths");
    private InputParameter _oppAlgorithm = new InputParameter("oppAlgorithm", "#select# pathSelectionPolicy IPOPTcost", "OPP algorithm");
    private InputParameter _wdmShortestPathType = new InputParameter("wdmShortestPathType", "#select# km hops", "Shortest path type in the optical layer: hops, or km");
    private InputParameter _IPOPT_run = new InputParameter("IPOPT_run", (int) -1, "Run IPOPT after this many succesful IPP runs (-1 to disable)", -1, 999999);
    private InputParameter _removeEmptyIPLinks = new InputParameter("removeEmptyIPLinks", true, "Whether to remove unused IP links");
   // private InputParameter _removeFrequency = new InputParameter("removeFrequency", (int) -1, "Try to remove IP links after that many IP_OPT calls (if 0 try even if IP_OPT wasn't called)");
    //private InputParameter _IPOPT_Algorithm = new InputParameter("IPOPT", "#algorithm#", "IPOPT algorithm (default ip_opt_simple_aa)");
    // private InputParameter _IgnoreAvailabilityandLatency = new InputParameter("IgnoreAvailabilityandLatency", false, "If checked, availability and latency will be ignored when finding paths.");
    private InputParameter _routerDelay = new InputParameter("routerDelay", (double) 0, "router processing delay (ms) per router per packet");
    private InputParameter _negotiate = new InputParameter("negotiate", true, "If a demand with availability cannot be accommodated, try protecting it.");
    private IAlgorithm IPOPT;
    private Map<String, String> IPOPT_AlgorithmParameters;
    private Map<String, String> init_net2planParameters;
    private int IPOPT_count = 0;
    private int IPOPT_run;
    private int remove_count = 0;
    private int removeFrequency = -1;
    private Map<Link, Pair<WDMUtils.RSA, Integer>> storedLightpaths;
    private RouteList netRapRouteList;

    @Override
    public String finish(StringBuilder output, double simTime) {
        return "";
    }

    @Override
    public void finishTransitory(double simTime) {
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public List<Triple<String, String, String>> getParameters() {
        return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
    }

    // initialize with just topology, ie. nodes and links
    @Override
    public void initialize(NetPlan currentNetPlan, Map<String, String> algorithmParameters, Map<String, String> simulationParameters, Map<String, String> net2planParameters) {
        InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

        if (currentNetPlan.getNumberOfLayers() < 2)
            throw new Net2PlanException("Bad - Number of layers must be at least two");


        /* Get layer objects */
        wdmLayer = currentNetPlan.getNetworkLayer("WDM");
        ipLayer = currentNetPlan.getNetworkLayer("IP");

        /* Get parameters of the WDM network */
        WDMUtils.checkResourceAllocationClashing(currentNetPlan, true, false, wdmLayer);
        wavelengthFiberOccupancy = WDMUtils.getNetworkSlotAndRegeneratorOcupancy(currentNetPlan, false, wdmLayer).getFirst();

        /* Parse input parameters */
        executeIPP = true;
        executeIPOPT = true;
        executeOPP = true;

        String K_ipLayerString = _K_ipLayer.getString();
        K_ipLayer = Integer.parseInt(K_ipLayerString);

        String K_wdmLayerString = _K_wdmLayer.getString();
        K_wdmLayer = Integer.parseInt(K_wdmLayerString);

        removeEmptyIPLinks = _removeEmptyIPLinks.getBoolean();
        negotiate = _negotiate.getBoolean();




        /* For the in-operation planning demo, set to 1? */

        String availableBitrates = "1,10";
        String reach = "3000,2000";
        String slots = "1,2";
        removeFrequency = 0;
        if(System.getProperty("demo") == null){
            System.out.println("Default settings");
        } else if (System.getProperty("demo").equalsIgnoreCase("inop")) {
            System.out.println("Applying settings for In-operation demo ONLY!");
            availableBitrates = "1";
            reach = "3000";
            slots = "1";
            removeFrequency = -1;
        }


        String[] TRxAvailableBitrates = availableBitrates.split(",");
        String[] TRxReach = reach.split(",");
        String[] TRxSlots = slots.split(",");

        if (TRxReach.length != TRxAvailableBitrates.length)
            throw new Net2PlanException("'TRxReach' should have the same number of elements with 'TRxAvailableBitrates'");


        if (TRxSlots.length != TRxAvailableBitrates.length)
            throw new Net2PlanException("'TRxSlots' should have the same number of elements with 'TRxAvailableBitrates'");


        String trx = "";
        for (int i = 0; i < TRxAvailableBitrates.length; i++) {
            trx += TRxAvailableBitrates[i].trim() + " 0 " + TRxSlots[i].trim();
            trx += " " + TRxReach[i].trim() + " -1;";
        }
        TRx = new WDMUtils.TransponderTypesInfo(trx);

        PRECISION_FACTOR = Double.parseDouble(net2planParameters.get("precisionFactor"));


        IPOPT_AlgorithmParameters = new HashMap<>();

            IPOPT = new ip_opt_8();

            IPOPT_AlgorithmParameters = new HashMap<>();
            List<Triple<String, String, String>> params = IPOPT.getParameters();
            for (Triple<String, String, String> param : params) {
                String name = param.getFirst();
                String val = param.getSecond();

                int select = val.indexOf("#select#");
                if (select != -1) {
                    String[] options = val.substring(select + "#select#".length()).split(" ");

                    for (String option : options) {
                        if (option.length() == 0)
                            continue;
                        val = option;
                        break;
                    }
                }
                IPOPT_AlgorithmParameters.put(name, val);
            }
        //}

        init_net2planParameters = net2planParameters;

        IPOPT_run = _IPOPT_run.getInt();

        routerDelay = _routerDelay.getDouble();
        if (routerDelay < 0)
            throw new Net2PlanException("router delay must be non-negative");

        // pathSelectionPolicy = _pathSelectionPolicy.getString();
        // PONTUS: Hardcoded to minimum latency
        pathSelectionPolicy = "minLat";
        if (!pathSelectionPolicy.equalsIgnoreCase("minLat") && !pathSelectionPolicy.equalsIgnoreCase("maxLat") &&
                !pathSelectionPolicy.equalsIgnoreCase("maxDisLat") && !pathSelectionPolicy.equalsIgnoreCase("minAvResBand") &&
                !pathSelectionPolicy.equalsIgnoreCase("maxAvResBand") && !pathSelectionPolicy.equalsIgnoreCase("minResBand") &&
                !pathSelectionPolicy.equalsIgnoreCase("maxResBand") && !pathSelectionPolicy.equalsIgnoreCase("minPathWiseResBand")
                && !pathSelectionPolicy.equalsIgnoreCase("maxPathWiseResBand"))
            throw new Net2PlanException("Path selection policy must be one among 'minLat','maxLat','maxDisLat','minAvResBand',"
                    + "'maxAvResBand','minResBand','maxResBand','minPathWiseResBand','maxPathWiseResBand'");
        /* Depending on the pathSelectionPolicy parameter, choose the right weights for the Auxiliary Graph */
        AgUseDelay = (pathSelectionPolicy.equalsIgnoreCase("minLat")) || (pathSelectionPolicy.equalsIgnoreCase("maxLat"))
                || (pathSelectionPolicy.equalsIgnoreCase("maxDisLat"));


        oppAlgorithm = _oppAlgorithm.getString();
        if (!oppAlgorithm.equalsIgnoreCase("IPOPTcost") && !oppAlgorithm.equalsIgnoreCase("pathSelectionPolicy"))
            throw new Net2PlanException("OPP algorithm must be either 'IPOPTcost' or 'pathSelectionPolicy'");

        // PONTUS: Hardcoded to minBW
        flexgridPolicy = FlexgridPolicy.minBW;

        /* Compute the lists of WDM nodes */
        nodes = currentNetPlan.getNodes();

        /* Compute the list of IP nodes */
        ipNodes = new LinkedList<>(); /*it is faster to start with an empty list and add nodes than to start with a full and then remove,
         and it's easier to form the if statement*/
        for (Node nodeIntermediate : nodes) {
            String isIPNodeString = nodeIntermediate.getAttribute("IPNode");
            if (isIPNodeString.equalsIgnoreCase("true") || isIPNodeString.equalsIgnoreCase("IPtransponder"))
                ipNodes.add(nodeIntermediate);
        }

        Map<Link, Double> fiberCostMap;
        String wdmShortestPathType = _wdmShortestPathType.getString();
        switch (wdmShortestPathType) {
            case "hops":
                fiberCostMap = null;
                break;

            case "km":
                fiberCostMap = new HashMap<>();
                for (Link l : currentNetPlan.getLinks(wdmLayer))
                    fiberCostMap.put(l, l.getLengthInKm());
                break;

            default:
                throw new Net2PlanException("Bad - Wrong WDM shortest path type");
        }

        /* Prepare the CandidatePathList for paths at the WDM layer */
        for (Node originNode : currentNetPlan.getNodes()) {
            /* Check whether it is a transponder in the demo topology, in which case also destination should
         * also be a transponder of matching color. */
            boolean sourceIsTransponder = false;
            String attr = originNode.getAttribute("IPNode");
            if (attr != null && attr.equalsIgnoreCase("IPtransponder"))
                sourceIsTransponder = true;
            if (attr != null && attr.equalsIgnoreCase("false")) continue;
            for (Node destinationNode : currentNetPlan.getNodes()) {
                int hops = 0;
                if (originNode == destinationNode) continue;
                if (sourceIsTransponder) {
                    attr = destinationNode.getAttribute("IPNode");
                    if (attr == null || !attr.equalsIgnoreCase("IPtransponder"))
                        continue;
                    /* the color attribute should be available for transponders */
                    if (!originNode.getAttribute("color").equals(destinationNode.getAttribute("color")))
                        continue;
                    /* Hack to restrict hop count to 7 - this is just enough for a point to point link
                 * This replaces the old (wrong) constraint for the ACINO testbed, where if the
				 *  endpoint was an IPtransponder it could only have up to two IP links */
                    //hops = 7; ACINO tesbed now has optical bypass, so we are removing this line
                }
                List<List<Link>> kPaths = GraphUtils.getKLooplessShortestPaths(nodes, currentNetPlan.getLinks(wdmLayer), originNode, destinationNode, fiberCostMap, K_wdmLayer, 0, hops, 0, 0, 0, 0);
                cpl_wdmLayer.put(Pair.of(originNode, destinationNode), kPaths);
            }
        }

        storedLightpaths = new HashMap<>();
        if(System.getProperty("debug") != null) {
            System.out.println("Running parameters");
            System.out.println("AgUseDelay = " + AgUseDelay);
            System.out.println("availableBitrates = " + availableBitrates);
            System.out.println("executeIPOPT = " + executeIPOPT);
            System.out.println("executeIPP = " + executeIPP);
            System.out.println("executeOPP = " + executeOPP);
            System.out.println("flexgridPolicy = " + flexgridPolicy);
            System.out.println("IgnoreAvailabilityandLatency = " + IgnoreAvailabilityandLatency);
            System.out.println("IPOPT_Algorithm = ip_opt_8");
            System.out.println("K_ipLayer = " + K_ipLayer);
            System.out.println("K_wdmLayer = " + K_wdmLayer);
            System.out.println("negotiate = " + negotiate);
            System.out.println("oppAlgorithm = " + oppAlgorithm);
            System.out.println("pathSelectionPolicy = " + pathSelectionPolicy);
            System.out.println("PRECISION_FACTOR = " + PRECISION_FACTOR);
            System.out.println("propagationSpeed = " + propagationSpeed);
            System.out.println("reach = " + reach);
            System.out.println("removeEmptyIPLinks = " + removeEmptyIPLinks);
            System.out.println("removeFrequency = " + removeFrequency);
            System.out.println("routerDelay = " + routerDelay);
            System.out.println("slots = " + slots);
            System.out.println("wdmShortestPathType = " + wdmShortestPathType);
            System.out.println("Cacsim initialized!");
        }
        finishTransitory(0);
    }

    public void reoptimize(NetPlan currentNetPlan) {
        if(System.getProperty("debug") != null)
            System.out.println("cacsim_reoptimze, calling IPOPT.executealgorithm");
        IPOPT.executeAlgorithm(currentNetPlan, IPOPT_AlgorithmParameters, init_net2planParameters);
    }

    @Override
    public void processEvent(NetPlan currentNetPlan, SimEvent event) {
        netRapRouteList = RouteList.getInstance();
        netRapRouteList.clearRoutes();

        /* Get layer objects */
        if(wdmLayer == null)
            wdmLayer = currentNetPlan.getNetworkLayer("WDM");
        if(ipLayer == null)
            ipLayer = currentNetPlan.getNetworkLayer("IP");

        try {
        /* Allocate an IP Layer demand */
            if (event.getEventObject() instanceof Demand) {

                System.out.println("cacsim:processEvents, got a Demand event!");
    		/* Store all the parameters related to the demand */
                Demand ipDemand = (Demand) event.getEventObject();
                NetworkLayer demandLayer = ipDemand.getLayer();
                if (!demandLayer.getName().equals(ipLayer.getName()))
                    throw new Net2PlanException("Bad - Connection requests allowed only in the IP layer");
                if (IgnoreAvailabilityandLatency)
    		/*store the generated latency and availability and then change them. The stored values will be compared against later
        		when a route is found.*/ {
                    ipDemand.setAttribute(ApplicationAwareConstants.GENERATED_LATENCY_MS_ATTRIBUTE, ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
                    ipDemand.setAttribute(ApplicationAwareConstants.GENERATED_AVAILABILITY_LEVEL_ATTRIBUTE, ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE));
                    ipDemand.setAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE, Double.toString(Double.MAX_VALUE));
                    ipDemand.setAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE, Double.toString(0));
                }

                double bandwidthInGbps = ipDemand.getOfferedTraffic();
                if (bandwidthInGbps <= PRECISION_FACTOR)
                    System.out.println("Demand bandwidth is only " + bandwidthInGbps + " Gbps. This may cause problems because precision is " + PRECISION_FACTOR + " Gbps. Change the precision factor or enlarge the demand.");

                boolean allocated = false;
                if (executeIPP) {
                    System.out.println("1) Calling ipProvisioningModule");
                    allocated = ipProvisioningModule(currentNetPlan, ipDemand, pathSelectionPolicy);
                }

                if (allocated) {
                    System.out.println("2) IP provisioning module found a path!");
                } else {
                    System.out.println("2) IP provisioning module did not find a path!");
                }

                if (executeIPOPT && (!allocated || (IPOPT_run >= 0 && IPOPT_count > IPOPT_run))) {
                    System.out.println("3) Calling IP_opt_7!");
                    IPOPT.executeAlgorithm(currentNetPlan, IPOPT_AlgorithmParameters, init_net2planParameters);
                    IPOPT_count = 0;
                    if (!allocated) {
                        allocated = !ipDemand.isBlocked();
                        if (!allocated) {
                            System.out.println("4) Demand still blocked");
                        } else if (currentNetPlan.getDemandsBlocked(ipLayer).size() > 0) {
                            ipDemand = currentNetPlan.getDemandsBlocked(ipLayer).get(0);
                            allocated = false;
                            System.out.println("4) Demand allocated, but another demand is blocked now: ID ." + ipDemand.getId());
                        }
                    }
                } else {
                    IPOPT_count++;
                }

                if (executeOPP && !allocated) {
                    System.out.println("5) Calling OPP");
                    allocated = opticalProvisioningModule(currentNetPlan, ipDemand, pathSelectionPolicy);

                    if (allocated) {
                        System.out.println("6) OPP success");
                    } else {
                        System.out.println("6) OPP failed");
                    }
                }

                double minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE)) / 100;
                boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));
                if (negotiate && !allocated && !Protected && minAvailability > 0) {
                    ipDemand.setAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE, "true");
                    //schedule a new demand with protection and modified availability
                    System.out.println("+++++++++++++Scheduling new event with protection");// and modified availability = " + newAvailability);
                    scheduleEvent(new SimEvent(event.getEventTime(), SimEvent.DestinationModule.EVENT_PROCESSOR, -1, ipDemand));
                } else {

                    /* IPOPT_count is zero only after an IP_OPT call */

                    if (IPOPT_count == 0) {
                        remove_count++; //remove_count is not initialized anywhere???
                    }
                    if (removeEmptyIPLinks && remove_count >= removeFrequency) {
                        remove_count = 0;
                        List<Link> allLinks = new ArrayList<>(currentNetPlan.getLinks(ipLayer));
                        for (Link link : allLinks) {
                            if (link.getNetPlan() != null && link.getCarriedTrafficNotIncludingProtectionSegments() + link.getReservedCapacityForProtection() < PRECISION_FACTOR) {
                                Link linkPair = link.getBidirectionalPair();
                                if (linkPair != null && linkPair.getCarriedTrafficNotIncludingProtectionSegments() + linkPair.getReservedCapacityForProtection() < PRECISION_FACTOR) {
                                    removeLightpath(link);
                                    removeLightpath(linkPair);
                                }
                            }
                        }
                    }
                    findSLAViolations(currentNetPlan, ipLayer);
                }
            }
            /* Remove an IP Layer demand */
            else if (event.getEventObject() instanceof DemandRemove) {
                System.out.println("cacsim:processEvents, got a DemandRemove event!");
                /* Store all the parameters related to the demand */
                SimEvent.DemandRemove removeDemand = (SimEvent.DemandRemove) event.getEventObject();
                Demand demandId = removeDemand.demand;
                /* Check if demand was removed earlier (was blocked) */
                if (demandId.getNetPlan() != null) {
                    NetworkLayer demandLayer = demandId.getLayer();
                    if (demandLayer != ipLayer) {
                        throw new Net2PlanException("Bad - Connection releases allowed only in the IP layer");
                    }
                    Set<Route> routesThisDemand = demandId.getRoutes();
                    for (Route route : routesThisDemand) {
                        /* remove the attached protection segment unconditionally*/
                        Set<ProtectionSegment> protectionSegmentsThisRoute = route.getPotentialBackupProtectionSegments();
                        for (ProtectionSegment protectionSegment : protectionSegmentsThisRoute)
                            protectionSegment.remove();

                        /* Remove associated idle IP links and routes at the IP layer */
                        /* If removeFrequency > 0 this will only happen after that many IP_OPT calls (and not here) */
                        if (removeEmptyIPLinks && removeFrequency == 0) {
                            List<Link> ipLinksThisRoute = route.getSeqLinksRealPath();
                            for (Link ipLink : ipLinksThisRoute) {
                                if (ipLink.getNetPlan() != null && ipLink.getCarriedTrafficNotIncludingProtectionSegments() + ipLink.getReservedCapacityForProtection() - demandId.getCarriedTraffic() < PRECISION_FACTOR) {
                                    Link ipLinkPair = ipLink.getBidirectionalPair();
                                    if (ipLinkPair != null && ipLinkPair.getCarriedTrafficNotIncludingProtectionSegments() + ipLinkPair.getReservedCapacityForProtection() < PRECISION_FACTOR) {
                                        removeLightpath(ipLink);
                                        removeLightpath(ipLinkPair);
                                    }
                                }
                            }
                        }
                    }
                    /* Remove the IP Layer demand. This will remove the route, too. */
                    demandId.remove();
                }
            }
        } catch (Exception e) {
            System.out.println("processEvent caught an exception!");
            System.out.println(e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    /* Function to find K sequences of links in the optical network and add as new lightpaths to the current NetPlan */
    private List<Link> findKLightpaths(NetPlan currentNetPlan, int k, Node originNode, Node destinationNode, int wdmClass, List<Integer> availTxKind) {
        /* Get all the possible paths at the WDM layer between the source and destination nodes of the IP demand (according to K shortest path) */
        List<List<Link>> candidateWdmPaths = cpl_wdmLayer.get(Pair.of(originNode, destinationNode));
        if (candidateWdmPaths != null) {
            if(System.getProperty("debug") != null)
                System.out.println("findLightPath found " + candidateWdmPaths.size() + " WDM paths");
        } else {
            if(System.getProperty("debug") != null)
                System.out.println("candidateWdmPaths = cpl_wdmLayer.get() -> null!");
            return new ArrayList<Link>();
        }
        WDMUtils.RSA rsa;
        int trxKind;
        double len;
        List<Link> ipLinks = new ArrayList<>();
        Map<String, String> lightpathAttributes = new LinkedHashMap<>();
        lightpathAttributes.put(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE, Integer.toString(wdmClass));

        /* Select the first k path (according to K-SP) with available resources (according to FirstFit WA algorithm) */
        for (List<Link> candidateWdmPath : candidateWdmPaths) {
            if(System.getProperty("debug") != null)
                System.out.println("  Evaluating path: " + pathToString(candidateWdmPath));
            len = 0;
            for (Link l : candidateWdmPath) {
                len += l.getLengthInKm();
            }
            /* availTxKind has either all transceiver types with sufficient reach or the minimum rate one depending on policy */
            trxKind = -1;
            for (int kind : availTxKind) {
                if (TRx.getOpticalReachKm(kind) > len && (trxKind == -1 || TRx.getNumSlots(kind) >= TRx.getNumSlots(trxKind))) {
                    trxKind = kind;
                }
            }
            if (trxKind == -1) {
                continue;
            }

            /* Get the colors of the nodes. Reminder: If both have a color, it is guaranteed to be the same. */
            /* Check if the color is available. */
            String colorNode = originNode.getAttribute("color");
            if (colorNode == null)
                colorNode = destinationNode.getAttribute("color");
            int wavelength;
            if (colorNode != null) {
                wavelength = Integer.parseInt(colorNode);
                WDMUtils.RSA rsa_check = new WDMUtils.RSA(candidateWdmPath, wavelength, TRx.getNumSlots(trxKind));
                if (!WDMUtils.isAllocatableRSASet(wavelengthFiberOccupancy, rsa_check)) {
                    wavelength = -1;
                }
            } else {
                wavelength = WDMUtils.spectrumAssignment_firstFit(candidateWdmPath, wavelengthFiberOccupancy, TRx.getNumSlots(trxKind));
            }
            if (wavelength < 0) {
                continue;
            }
            rsa = new WDMUtils.RSA(candidateWdmPath, wavelength, TRx.getNumSlots(trxKind));

            /* Add the new IP Link */
            Link ipLink = currentNetPlan.addLinkBidirectional(originNode, destinationNode, TRx.getLineRateGbps(trxKind), 0, propagationSpeed, lightpathAttributes, ipLayer).getFirst();

            /* store the IP link's RSA to be used later */
            storedLightpaths.put(ipLink, new Pair<>(rsa, trxKind, false));
            ipLink.setLengthInKm(len + routerDelay * 200); /*add end router delay*/
            ipLink.getBidirectionalPair().setLengthInKm(len + routerDelay * 200);
            ipLinks.add(ipLink);
            if (ipLinks.size() >= k) break;
        }
        return ipLinks;
    }

    /* Function to find all the SRGs for a lightpath */
    private Set<SharedRiskGroup> findLightpathSRGs(NetPlan currentNetPlan, Link ipLink) {
        Set<SharedRiskGroup> srg_thisLightpath = new LinkedHashSet<>();

        /* SRGs at the IP layer */
        Node ipOriginNode = ipLink.getOriginNode();
        Node ipDestinationNode = ipLink.getDestinationNode();
        srg_thisLightpath.addAll(ipOriginNode.getSRGs()); /* SRGs for source IP router */
        srg_thisLightpath.addAll(ipDestinationNode.getSRGs()); /* SRGs for destination IP router */
        srg_thisLightpath.addAll(ipLink.getSRGs()); /* SRGs for the IP link itself */

        /* SRGs at the WDM layer */
        Demand wdmDemand = ipLink.getCoupledDemand();
        String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
        if (wdmDemand != null) {
            Set<Route> wdmRoutes = wdmDemand.getRoutes();
            for (Route wdmRoute : wdmRoutes)
                srg_thisLightpath.addAll(wdmRoute.getSRGs());
        } else if (doNotDeleteMeString == null || !doNotDeleteMeString.equalsIgnoreCase("true")) {
            WDMUtils.RSA rsa = storedLightpaths.get(ipLink).getFirst();
            for (Link wdmLink : rsa.seqLinks) {
                srg_thisLightpath.addAll(wdmLink.getSRGs());
                /*.getSRGs() doesn't add link endpoints. The destination is enough since the source has been added above*/
                srg_thisLightpath.addAll(wdmLink.getDestinationNode().getSRGs());
            }
        }
        return srg_thisLightpath;
    }

    /* Function to remove a lightpath from the current NetPlan */
    private void removeLightpath(Link ipLink) {
        /*if this link must not be removed, skip it*/
        String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
        if (doNotDeleteMeString != null && doNotDeleteMeString.equalsIgnoreCase("true"))
            return;
        /* Get the WDM demand associated to the IP link to remove */
        Demand wdmDemand = ipLink.getCoupledDemand();

        if (wdmDemand.getLayer() != wdmLayer) throw new RuntimeException("Bad");

        /* Get the WDM routes associated to the WDM demand*/
        Set<Route> wdmRoutes = wdmDemand.getRoutes();

        /* Release the resources at the WDM layer for each WDM route and remove the route */
        for (Route wdmRoute : wdmRoutes) {
            WDMUtils.releaseResources(new WDMUtils.RSA(wdmRoute, false), wavelengthFiberOccupancy, null);
        }

        /* Remove the IP link and the associated WDM demand */
        ipLink.remove();
        wdmDemand.remove();
    }

    /* Function to find a set of candidate paths meeting all the application requirements */
    private List<List<Link>> findApplicationAwareCandidatePaths(NetPlan currentNetPlan, boolean addPotentialLightpaths, Demand ipDemand, List<Integer> availTxKind, boolean AgUseDelay) {
        Node ingressNode = ipDemand.getIngressNode();
        Node egressNode = ipDemand.getEgressNode();
        double bandwidthInGbps = ipDemand.getOfferedTraffic();
        int wdmClass = Integer.parseInt(ipDemand.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE));
        double maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
        double minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE));
        boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));
        if(System.getProperty("debug") != null)
            System.out.println("Demand from src: " + ingressNode + " to: " + egressNode + " bandwidth " + bandwidthInGbps + " maxlatency " + maxLatencyInMs + " minAvailbility " + minAvailability);
        /* Compute the list of IP links */
        List<Link> ipLinks = currentNetPlan.getLinks(ipLayer);

        /* Create the list of IP links that must be excluded in the Auxiliary Graph */
        List<Link> ipLinksToExclude = new ArrayList<>();

        /* Search and remove IP links (i.e., existing lightpaths) violating bandwidth or wdmClass constraints */
        for (Link ipLink : ipLinks) {
            /* Check bandwidth constraint */
            if (bandwidthInGbps > ipLink.getCapacity() - ipLink.getCarriedTrafficNotIncludingProtectionSegments() - ipLink.getReservedCapacityForProtection() + PRECISION_FACTOR) {
                if(System.getProperty("debug") != null)
                    System.out.println("Excluding candidate link " + ipLink + " due to bandwidth constraints");
                ipLinksToExclude.add(ipLink);
            } else if (ipLink.isDown()) {
                ipLinksToExclude.add(ipLink);
            } else {
                /* Check wdmClass constraint */
                String wdmClassString = ipLink.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE);
                if (wdmClassString == null) continue;
                int wdmClassIpLink = Integer.parseInt(wdmClassString);

                if (wdmClass != wdmClassIpLink) {
                    if(System.getProperty("debug") != null)
                        System.out.println("Excluding candidate link " + ipLink + " due to WDM CLASS");
                    ipLinksToExclude.add(ipLink);
                }
            }
        }

        /* Exclude the existing lightpaths not meeting bandwidth and wdmClass application requirements from the Auxiliary Graph */
        List<Link> ipLinksToInclude = new ArrayList<>(ipLinks);
        ipLinksToInclude.removeAll(ipLinksToExclude);

        /* Only if explicitly requested, add new IP links (i.e., potential lightpaths) */
        if (addPotentialLightpaths) {
            storedLightpaths.clear();
            /* Check if there is an existing lightpath between source and destination */
            /* do not do this for nodes that do not want it */
            String isIPNodeStringingressNode = ingressNode.getAttribute("do not attach IP links to me");
            String isIPNodeStringegressNode = egressNode.getAttribute("do not attach IP links to me");
            boolean existingLightpathGood = false;
            if (isIPNodeStringingressNode != null && isIPNodeStringingressNode.equalsIgnoreCase("true") || isIPNodeStringegressNode != null && isIPNodeStringegressNode.equalsIgnoreCase("true"))
                existingLightpathGood = true;
            else
                for (Link ipLink : ipLinksToInclude) {
                    if (ingressNode == ipLink.getOriginNode() && egressNode == ipLink.getDestinationNode()) {
                        existingLightpathGood = true;
                        break;
                    }
                }
            /* If not, add a potential lightpath to the Auxiliary Graph*/
            if (!existingLightpathGood || Protected) {	/* This will eventually make two lightpaths (one per direction) */
                ipLinksToInclude.addAll(findKLightpaths(currentNetPlan, Protected ? K_wdmLayer : 1, ingressNode, egressNode, wdmClass, availTxKind));
            }
            if(System.getProperty("debug") != null)
                System.out.println("Building IP Mesh");
            /* Add potential lightpaths between any node pair other than the source and the destination */
            for (Node nodeIntermediateSource : ipNodes) {
                String attachLinksStringIntermediateSource = nodeIntermediateSource.getAttribute("do not attach IP links to me");
                if (attachLinksStringIntermediateSource != null && attachLinksStringIntermediateSource.equalsIgnoreCase("true")) {
                    if(System.getProperty("debug") != null)
                        System.out.println(" - Ignoring node due to dnaIPltm " + nodeIntermediateSource.getAttribute("name"));
                    continue;
                }
                String colorStringIntermediateSource = nodeIntermediateSource.getAttribute("color");
                for (Node nodeIntermediateDestination : ipNodes) {
                    /* Check if between every node pair in the IP network there is a viable existing lightpath */
                    if (nodeIntermediateSource == nodeIntermediateDestination) {
                        continue;
                    }
                    String attachLinksStringIntermediateDestination = nodeIntermediateDestination.getAttribute("do not attach IP links to me");
                    if (attachLinksStringIntermediateDestination != null && attachLinksStringIntermediateDestination.equalsIgnoreCase("true")) {
                        if(System.getProperty("debug") != null)
                            System.out.println(" - Ignoring pair due to \"do not attach IP links to me\" " + nodeIntermediateSource.getAttribute("name") + " -> " + nodeIntermediateDestination.getAttribute("name"));
                        continue;
                    }

                    /*prevent transponder-transponder IP links in the same node.
         			 * This is effected by checking that the transponders are of the same color.*/
                    String colorStringIntermediateDestination = nodeIntermediateDestination.getAttribute("color");
                    if (colorStringIntermediateSource != null && colorStringIntermediateDestination != null && !colorStringIntermediateSource.equalsIgnoreCase(colorStringIntermediateDestination)) {
                        if(System.getProperty("debug") != null)
                            System.out.println(" - Ignoring pair due to different colors " + nodeIntermediateSource.getAttribute("name") + " -> " + nodeIntermediateDestination.getAttribute("name"));
                        continue;
                    }
                    boolean existingLightpathGoodIntermediate = false;
                    for (Link ipLink : ipLinksToInclude) {
                        if (nodeIntermediateSource == ipLink.getOriginNode() && nodeIntermediateDestination == ipLink.getDestinationNode()) {
                            existingLightpathGoodIntermediate = true;
                            if(System.getProperty("debug") != null)
                                System.out.println(" - Ignoring pair, already in list " + nodeIntermediateSource.getAttribute("name") + " -> " + nodeIntermediateDestination.getAttribute("name"));
                            break;
                        }
                    }
                    /* If not, add a potential lightpath to the Auxiliary Graph*/
                    if (!existingLightpathGoodIntermediate) {
                        if(System.getProperty("debug") != null)
                            System.out.println(" - Finding K lightPaths between " + nodeIntermediateSource.getAttribute("name") + " -> " + nodeIntermediateDestination.getAttribute("name"));
                        //ipLinksToInclude.addAll(findKLightpaths(currentNetPlan, 1, nodeIntermediateSource, nodeIntermediateDestination, wdmClass, availTxKind));
                        ipLinksToInclude.addAll(findKLightpaths(currentNetPlan, Protected ? K_wdmLayer : 1, nodeIntermediateSource, nodeIntermediateDestination, wdmClass, availTxKind));
                    }
                }
            }
        }

        /* Choose the right Auxiliary Graph weights */
        Map<Link, Double> a_g_weightsMap = new HashMap<>();

        /* Compute the maps of propagation delay or residual bandwidth of link, to be used as weights for the Auxiliary Graph */
        if (AgUseDelay) {
            /* Latency chosen as weight */
            for (Link ipLink : ipLinksToInclude)
                a_g_weightsMap.put(ipLink, ipLink.getPropagationDelayInMs());
        } else {
            /* Residual bandwidth chosen as weight */
            for (Link ipLink : ipLinksToInclude)
                a_g_weightsMap.put(ipLink, ipLink.getCapacity() - ipLink.getCarriedTrafficNotIncludingProtectionSegments() - ipLink.getReservedCapacityForProtection());
        }

        /* Compute the k-SP on the Auxiliary Graph to get a list of candidate paths */
        /* subtract one router delay from max latency to account for ingress router processing */
        if(System.getProperty("debug") != null) {
            System.out.println("getKshortest from : " + ingressNode.getAttributes().get("name") + " to " + egressNode.getAttributes().get("name"));
            System.out.println("getKshortest, links included: " + ipLinksToInclude.size());
            System.out.println("Auxiliary graph topology");
            // TODO: Modify this to print auxiliary graph to a file instead
            System.out.println("digraph augtop {");

            for (Link iplink : ipLinksToInclude) {
                System.out.println("{ edge [label=\"l: " + iplink.getPropagationDelayInMs() + " b: " + iplink.getCapacity() + "\"] " + iplink.getOriginNode().getAttribute("name") + " -> " + iplink.getDestinationNode().getAttribute("name") + "};");
            }
            for (Link iplink : ipLinksToExclude) {
                System.out.println("{ edge [color=\"#ff0000\", style=\"dotted\" label=\"l: " + iplink.getPropagationDelayInMs() + " b: " + iplink.getCapacity() + "\"] " + iplink.getOriginNode().getAttribute("name") + " -> " + iplink.getDestinationNode().getAttribute("name") + "};");
            }
            System.out.println("}");
        }
        List<List<Link>> candidatePaths = GraphUtils.getKLooplessShortestPaths(ipNodes, ipLinksToInclude, ingressNode, egressNode, a_g_weightsMap, K_ipLayer, 0, 0, maxLatencyInMs, 0, 0, 0);
        /* Remove candidate paths violating minimum availability */
        Iterator<List<Link>> candidatePathsIt = candidatePaths.iterator();

        while (candidatePathsIt.hasNext()) {
            List<Link> seqIPLinks_thisPath = candidatePathsIt.next();
            /* Check if candidate paths do not meet the availability requirement */
            double availability_thisPath = 1.0;
            if(System.getProperty("debug") != null) {
                System.out.println("Evaluating candidate IP path: " + seqIPLinks_thisPath);
                System.out.println("\t IP path: " + pathToString(seqIPLinks_thisPath));
            }

            if (!Protected) {
                /*not necessary but may save CPU time*/
                //if (!IgnoreAvailabilityandLatency)
                Set<SharedRiskGroup> srgs_thisCandidatePath = new LinkedHashSet<>();

                for (Link ipLink : seqIPLinks_thisPath) {
                    srgs_thisCandidatePath.addAll(findLightpathSRGs(currentNetPlan, ipLink));
                }
                for (SharedRiskGroup srg : srgs_thisCandidatePath) {
                    availability_thisPath *= srg.getAvailability();
                }
            }
            if (availability_thisPath < minAvailability / 100) {
                /* Remove the paths not meeting availability or latency */
                if(System.getProperty("debug") != null)
                System.out.println("Removing path, availabilty too low  (path:  " + availability_thisPath + " < min: " + minAvailability / 100 + " )");

                candidatePathsIt.remove();
            }
            /* Check for new lightpath conflict */
            else if (addPotentialLightpaths) {
                List<WDMUtils.RSA> RSAs = new ArrayList<>();
                for (Link ipLink : seqIPLinks_thisPath) {
                    String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
                    if (ipLink.getCoupledDemand() == null &&
                            (doNotDeleteMeString == null ||
                                    !doNotDeleteMeString.equalsIgnoreCase("true"))) {
                        RSAs.add(storedLightpaths.get(ipLink).getFirst());
                    }
                }
                int i, j;
                for (i = 0; i < RSAs.size(); i++) {
                    WDMUtils.RSA RSAi = RSAs.get(i);
                    int iFirst = RSAi.seqFrequencySlots_se.getMinLocation()[0];
                    int iLast = RSAi.seqFrequencySlots_se.getMaxLocation()[0];
                    List<Link> RSAiReverse = null;
                    for (j = i + 1; j < RSAs.size(); j++) {
                        WDMUtils.RSA RSAj = RSAs.get(j);
                        int jFirst = RSAj.seqFrequencySlots_se.getMinLocation()[0];
                        int jLast = RSAj.seqFrequencySlots_se.getMaxLocation()[0];
                        if (iLast < jFirst || jLast < iFirst) continue;
                        if (!Collections.disjoint(RSAi.seqLinks, RSAj.seqLinks)) break;
                        if (RSAiReverse == null) {
                            RSAiReverse = new ArrayList<>();
                            for (Link l : RSAi.seqLinks)
                                RSAiReverse.add(l.getBidirectionalPair());
                        }
                        if (!Collections.disjoint(RSAiReverse, RSAj.seqLinks)) break;
                    }
                    if (j < RSAs.size()) break;
                }
                if (i < RSAs.size()) {
                    candidatePathsIt.remove();
                    if(System.getProperty("debug") != null)
                        System.out.println("Removing candidate path (" + i + " < " + RSAs.size() + " ) - path " + seqIPLinks_thisPath);
                }
            }
        }

        /* Return the list of candidate paths, i.e., paths not violating latency and availability*/
        return candidatePaths;
    }

    private String pathToString(List<Link> path) {
        StringBuilder sb = new StringBuilder();
        sb.append(path.get(0).getOriginNode().getAttribute("name"));
        for (Link optolink : path) {
            sb.append(" -> ").append(optolink.getDestinationNode().getAttribute("name"));
        }
        return sb.toString();
    }

    /* Function to create a bidirectional lightpath */
    private void createLightpath(Link ipLink) {
        Demand wdmDemand = ipLink.coupleToNewDemandCreated(wdmLayer);
        Pair<WDMUtils.RSA, Integer> t = storedLightpaths.get(ipLink);
        if (t == null) {
            if(System.getProperty("debug") != null)
                System.out.println("createLightPath -> storedlightpaths.get(" + ipLink + ") returned null!");
            return;
        }
        WDMUtils.RSA rsa = t.getFirst();
        double rateInGbps = TRx.getLineRateGbps(t.getSecond());

        Route wdmroute1 = WDMUtils.addLightpath(wdmDemand, rsa, rateInGbps);
        netRapRouteList.addRoute(new Pair<>("NEW", wdmroute1, false));
        WDMUtils.allocateResources(rsa, wavelengthFiberOccupancy, null);

        /* Create the lightpath in the opposite direction */
        Link ipLink2 = ipLink.getBidirectionalPair();
        wdmDemand = ipLink2.coupleToNewDemandCreated(wdmLayer);
        List<Link> backPath = new ArrayList<>();
        List<Link> tList = new ArrayList<>(rsa.seqLinks);
        for (Link l : tList) {
            Node originNode = l.getOriginNode();
            Node destinationNode = l.getDestinationNode();
            for (Link link : destinationNode.getOutgoingLinks(wdmLayer)) {
                if (link.getDestinationNode() == originNode)
                    backPath.add(link);
            }
        }
        Collections.reverse(backPath);

        WDMUtils.RSA rsa2 = new WDMUtils.RSA(backPath, rsa.seqFrequencySlots_se, null);
        Route wdmroute2 = WDMUtils.addLightpath(wdmDemand, rsa2, rateInGbps);
        netRapRouteList.addRoute(new Pair<>("NEW", wdmroute2, false));
        WDMUtils.allocateResources(rsa2, wavelengthFiberOccupancy, null);
    }

    /* Function to get the sequence of links in a lightpath */
    private List<Link> getLightpathLinks(Link ipLink) {
        List<Link> seqLinks;
        Demand wdmDemand = ipLink.getCoupledDemand();
        if (wdmDemand == null) {
            Pair<WDMUtils.RSA, Integer> slp = storedLightpaths.get(ipLink);
            if (slp == null) {
          /*      System.out.println("getLightpathLinks, storedlightpaths returned NULL!");
                System.out.println("Looking for lightpath matching IP link: " + ipLink.toString());
                System.out.println("The storedLightpaths contains: ");
                Set<Link> iplinks = storedLightpaths.keySet();
                for( Link link : iplinks){
                    System.out.println("    " + link.toString());
                }
*/

                // TODO: Pontus modified oct 24, to avoid crash in checkDisjointWDM (line 1243)
                return null;
            }

            WDMUtils.RSA rsa = slp.getFirst();
            seqLinks = rsa.seqLinks;
        } else {
            Set<Route> wdmRoutes = wdmDemand.getRoutes();
            seqLinks = wdmRoutes.iterator().next().getSeqLinksRealPath();
        }
        return seqLinks;
    }

    /* Function that defines the policy for the selection of one path among the candidate paths */
    private List<Link> choosePath(List<List<Link>> candidatePaths, String pathSelectionPolicy) {
        if(System.getProperty("debug") != null)
            System.out.println("\tchoosePath called");
        List<Link> seqIPLinks = new ArrayList<>();
        switch (pathSelectionPolicy) {
            case "minLat": {
                /* Choose the latency shortest path */
                seqIPLinks = candidatePaths.get(0);
                break;
            }

            case "maxLat": {
                /* Choose the latency longest path */
                seqIPLinks = candidatePaths.get(candidatePaths.size() - 1);
                break;
            }

            case "maxDisLat": {
                /* Choose the latency longest path that is disjoint with the latency shortest path */
                List<Link> seqIPLinks_shortestPath = candidatePaths.get(0);
                java.util.Collections.reverse(candidatePaths);
                Iterator<List<Link>> candidatePathsIt = candidatePaths.iterator();
                boolean isDisjoint = false;
                while (candidatePathsIt.hasNext()) {
                    List<Link> seqIPLinks_thisPath = candidatePathsIt.next();
                    isDisjoint = Collections.disjoint(seqIPLinks_thisPath, seqIPLinks_shortestPath);
                    if (isDisjoint) {
                        seqIPLinks = seqIPLinks_thisPath;
                        isDisjoint = true;
                        break;
                    }
                }
                if (!isDisjoint) seqIPLinks = seqIPLinks_shortestPath;
                break;
            }

            case "minAvResBand": {
                /* Choose the path that in average (i.e., according to the number of hops)
            	leads to the minimum residual bandwidth on the links */
                Iterator<List<Link>> candidatePathsIt = candidatePaths.iterator();
                double avResBandwidth = Double.MAX_VALUE;
                while (candidatePathsIt.hasNext()) {
                    double avResBandwidthAux = 0;
                    List<Link> seqIPLinks_thisPath = candidatePathsIt.next();
                    for (Link IPLink : seqIPLinks_thisPath) {
                        avResBandwidthAux = avResBandwidthAux + (IPLink.getCapacity() - IPLink.getCarriedTrafficNotIncludingProtectionSegments() - IPLink.getReservedCapacityForProtection());
                    }
                    avResBandwidthAux = avResBandwidthAux / seqIPLinks_thisPath.size();
                    if (avResBandwidthAux < avResBandwidth) {
                        seqIPLinks = seqIPLinks_thisPath;
                        avResBandwidth = avResBandwidthAux;
                    }
                }
                break;
            }

            case "maxAvResBand": {
                /* Choose the path that in average (i.e., according to the number of hops)
           	 	leads to the maximum residual bandwidth on the links */
                Iterator<List<Link>> candidatePathsIt = candidatePaths.iterator();
                double avResBandwidth = 0;
                while (candidatePathsIt.hasNext()) {
                    double avResBandwidthAux = 0;
                    List<Link> seqIPLinks_thisPath = candidatePathsIt.next();
                    for (Link IPLink : seqIPLinks_thisPath)
                        avResBandwidthAux = avResBandwidthAux + (IPLink.getCapacity() - IPLink.getCarriedTrafficNotIncludingProtectionSegments() - IPLink.getReservedCapacityForProtection());
                    avResBandwidthAux = avResBandwidthAux / seqIPLinks_thisPath.size();
                    if (avResBandwidthAux > avResBandwidth) {
                        seqIPLinks = seqIPLinks_thisPath;
                        avResBandwidth = avResBandwidthAux;
                    }
                }
                break;
            }

            case "minResBand": {
                /* Choose the residual bandwidth shortest path <=> Minimum residual bandwidth path */
                seqIPLinks = candidatePaths.get(0);
                break;
            }

            case "maxResBand": {
                /* Choose the residual bandwidth longer path <=> Maximum residual bandwidth path */
                seqIPLinks = candidatePaths.get(candidatePaths.size() - 1);
                break;
            }

            case "minPathWiseResBand": {
                /* Find the path with highest bottleneck among the candidate paths */
                double bottleneckCapacity = Double.MAX_VALUE;
                for (List<Link> seqIPLinks_thisPath : candidatePaths) {
                    double bottleneckCapacityAux = getPathBottleneckCapacity(seqIPLinks_thisPath);
                    if (bottleneckCapacityAux < bottleneckCapacity) {
                        seqIPLinks = seqIPLinks_thisPath;
                        bottleneckCapacity = bottleneckCapacityAux;
                    }
                }
                break;
            }

            case "maxPathWiseResBand": {
                /* Find the path with lowest bottleneck among the candidate paths */
                double bottleneckCapacity = 0;
                Iterator<List<Link>> candidatePathsIt = candidatePaths.iterator();
                while (candidatePathsIt.hasNext()) {
                    List<Link> seqIPLinks_thisPath = candidatePathsIt.next();
                    double bottleneckCapacityAux = getPathBottleneckCapacity(seqIPLinks_thisPath);
                    if (bottleneckCapacityAux > bottleneckCapacity) {
                        seqIPLinks = seqIPLinks_thisPath;
                        bottleneckCapacity = bottleneckCapacityAux;
                    }
                }
                break;
            }
        }

        /* Return the chosen path */
        return seqIPLinks;
    }

    /* Function to compute the bottleneck capacity of a given path */
    private double getPathBottleneckCapacity(List<Link> path) {
        if(System.getProperty("debug") != null)
            System.out.println("\tgetPathBottleneckCapacity called");
        if (path.isEmpty()) throw new Net2PlanException("Path is empty");
        double bottleneckCapacity = Double.MAX_VALUE;
        for (Link link : path)
            bottleneckCapacity = Math.min(bottleneckCapacity, link.getCapacity() - link.getCarriedTrafficNotIncludingProtectionSegments() - link.getReservedCapacityForProtection());
        return bottleneckCapacity;
    }

    private boolean ipProvisioningModule(NetPlan currentNetPlan, Demand ipDemand, String pathSelectionPolicy) {
        double bandwidthInGbps = ipDemand.getOfferedTraffic();
        boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));

        /* Try to find a list of candidate paths by only considering existing lightpaths */
        if(System.getProperty("debug") != null)
            System.out.println("\tipProvisioningModule called");
        boolean addPotentialLightpaths = false;
        List<List<Link>> candidatePaths = findApplicationAwareCandidatePaths(currentNetPlan, addPotentialLightpaths, ipDemand, null, AgUseDelay);
        boolean allocated = !candidatePaths.isEmpty();

        /* In case of protection, find a disjoint path couple among the set of candidate paths */
        if (allocated && Protected) {
            Pair<List<Link>, List<Link>> pairOfDisjointCandidatePaths = findDisjointPathPair(candidatePaths, ipDemand);

            /* If no disjoint path pair is found, change "allocated" boolean to false */
            if (pairOfDisjointCandidatePaths == null) allocated = false;
                /* Otherwise, set the first path as primary route and the second one as protection segment */
            else {
                List<Link> seqLinkWorking = pairOfDisjointCandidatePaths.getFirst();
                List<Link> seqLinkBackup = pairOfDisjointCandidatePaths.getSecond();
                Route addedWorkingRoute = currentNetPlan.addRoute(ipDemand, bandwidthInGbps, bandwidthInGbps, seqLinkWorking, null);

                ProtectionSegment addedProtectionSegment;
                if (System.getProperty("noresv") != null)
                    addedProtectionSegment = currentNetPlan.addProtectionSegment(seqLinkBackup, 0.0, null);
                else
                    addedProtectionSegment = currentNetPlan.addProtectionSegment(seqLinkBackup, bandwidthInGbps, null);
                addedWorkingRoute.addProtectionSegment(addedProtectionSegment);
                netRapRouteList.addRoute(new Pair<>("ROUTE", addedWorkingRoute, false));
            }
        }

        /* In case of no protection, choose one candidate path and just allocate the route */
        if (allocated && !Protected) {
            /* Choose one path among the candidate paths according to the policy defined in choosePath */
            List<Link> seqIPLinks = choosePath(candidatePaths, pathSelectionPolicy);

            /* Allocate the route */
            Route route = currentNetPlan.addRoute(ipDemand, bandwidthInGbps, bandwidthInGbps, seqIPLinks, null);
            netRapRouteList.addRoute(new Pair<>("ROUTE", route, false));

        }
        return allocated;
    }

    private boolean opticalProvisioningModule(NetPlan currentNetPlan, Demand ipDemand, String pathSelectionPolicy) {
        double bandwidthInGbps = ipDemand.getOfferedTraffic();
        int wdmClass = Integer.parseInt(ipDemand.getAttribute(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE));
        boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));
        /* If no candidate path is found, try to find a list of candidate paths by also considering potential lightpaths */
        boolean addPotentialLightpaths = true;

        /* Available transceivers according to flexgrid policy */
        List<Integer> availTxKind = new ArrayList<>();

        if (flexgridPolicy == FlexgridPolicy.maxBW || (flexgridPolicy == FlexgridPolicy.mix && wdmClass > 0)) {	/* All with sufficient rate are included */
            for (int i = 0; i < TRx.getNumTypes(); i++)
                if (TRx.getLineRateGbps(i) >= bandwidthInGbps) {
                    availTxKind.add(i);
                }
        } else {	/* Only add the minimum sufficient rate (supposing it reaches farther) */
            double minRate = Double.MAX_VALUE;
            int trxKind = -1;
            for (int i = 0; i < TRx.getNumTypes(); i++)
                if (TRx.getLineRateGbps(i) >= bandwidthInGbps - PRECISION_FACTOR && TRx.getLineRateGbps(i) <= minRate) {
                    trxKind = i;
                    minRate = TRx.getLineRateGbps(i);
                }
            if (trxKind != -1) {
                availTxKind.add(trxKind);
            }
        }

        boolean oldFlag = IgnoreAvailabilityandLatency;
        if (!Protected && oppAlgorithm.equalsIgnoreCase("IPOPTcost")) { 
          IgnoreAvailabilityandLatency = true;
        }
        List<List<Link>> candidatePaths = findApplicationAwareCandidatePaths(currentNetPlan, addPotentialLightpaths, ipDemand, availTxKind, AgUseDelay);
        IgnoreAvailabilityandLatency = oldFlag;
        if(System.getProperty("debug") != null)
            System.out.println("OPP: findApplicationAwareCandidatePaths returned " + candidatePaths.size() + " potential paths");
        for (List<Link> p : candidatePaths) {
            if(System.getProperty("debug") != null)
                System.out.println(" Candidate path: " + pathToString(p));
        }
        boolean allocated = !candidatePaths.isEmpty();

        if (allocated && Protected) {
            Pair<List<Link>, List<Link>> pairOfDisjointCandidatePaths = findDisjointPathPair(candidatePaths, ipDemand);

            if (pairOfDisjointCandidatePaths == null) {
                allocated = false;
            } else {
                List<Link> seqLinkWorking = pairOfDisjointCandidatePaths.getFirst();
                List<Link> seqLinkBackup = pairOfDisjointCandidatePaths.getSecond();
                for (Link ipLink : seqLinkWorking) {
                    if (ipLink.getAttribute("do not delete me") != null) continue;
                    Demand wdmDemand = ipLink.getCoupledDemand();
                    if (wdmDemand == null) {
                        createLightpath(ipLink);
                    }
                }
                for (Link ipLink : seqLinkBackup) {
                    if (ipLink.getAttribute("do not delete me") != null) continue;
                    Demand wdmDemand = ipLink.getCoupledDemand();
                    if (wdmDemand == null) {
                        createLightpath(ipLink);
                    }
                }
                Route addedWorkingRoute = currentNetPlan.addRoute(ipDemand, bandwidthInGbps, bandwidthInGbps, seqLinkWorking, null);
                netRapRouteList.addRoute(new Pair<>("ROUTE", addedWorkingRoute, false));
                
                ProtectionSegment addedProtectionSegment;
                if (System.getProperty("noresv") != null)
                    addedProtectionSegment = currentNetPlan.addProtectionSegment(seqLinkBackup, 0.0, null);
                else
                    addedProtectionSegment = currentNetPlan.addProtectionSegment(seqLinkBackup, bandwidthInGbps, null);

                if(System.getProperty("debug") != null)
                    System.out.println("TODO: Handle protection segment!");
                addedWorkingRoute.addProtectionSegment(addedProtectionSegment);
            }

        }

        /* If at least one candidate path is found, choose one according to pathSelectionPolicy and allocate the route */
        if (allocated && !Protected) {
            if(System.getProperty("debug") != null)
                System.out.println("\tOPP:choosing a candidatePath");
            /* Choose one path among the candidate paths according to the policy defined in choosePath */
            List<Link> seqIPLinks = null;
            if (oppAlgorithm.equalsIgnoreCase("pathSelectionPolicy")) {
                seqIPLinks = choosePath(candidatePaths, pathSelectionPolicy);
            } else {
                double best = Double.MAX_VALUE;
                for (List<Link> candidate : candidatePaths) {
                    NetPlan working = currentNetPlan.copy();
                    List<Link> samePath = new ArrayList<>();
                    NetworkLayer wIP = working.getNetworkLayer(ipLayer.getIndex());
                    NetworkLayer wWDM = working.getNetworkLayer(wdmLayer.getIndex());
                    for (Link tLink : candidate) {
                        Link ipLink = working.getLink(tLink.getIndex(), wIP);
                        samePath.add(ipLink);
                        String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
                        Demand wdmDemand = ipLink.getCoupledDemand();
                        if (wdmDemand == null
                                && (doNotDeleteMeString == null || !doNotDeleteMeString.equalsIgnoreCase("true"))) {
                            wdmDemand = ipLink.coupleToNewDemandCreated(wWDM);
                            Pair<WDMUtils.RSA, Integer> t = storedLightpaths.get(tLink);
                            WDMUtils.RSA tRSA = t.getFirst();
                            int trxKind = t.getSecond();
                            List<Link> sameLightpath = new ArrayList<>();
                            for (Link tLP : tRSA.seqLinks) {
                                sameLightpath.add(working.getLink(tLP.getIndex(), wWDM));
                            }
                            WDMUtils.RSA rsa = new WDMUtils.RSA(sameLightpath, tRSA.seqFrequencySlots_se);
                            if(System.getProperty("debug") != null)
                                System.out.println("\tCalling WDMUtils.addLightPath");
                            WDMUtils.addLightpath(wdmDemand, rsa, TRx.getLineRateGbps(trxKind));
                        }
                    }
                    Demand wDemand = working.getDemand(ipDemand.getIndex(), wIP);         

                    List<Link> ipLinks = new ArrayList<>(working.getLinks(wIP));
                    for (Link ipLink : ipLinks) {
                        String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
                        if (ipLink.getCoupledDemand() == null
                                && (doNotDeleteMeString == null || !doNotDeleteMeString.equalsIgnoreCase("true")))
                            ipLink.remove();
                    }

                    double cost = Double.parseDouble(IPOPT.executeAlgorithm(working, IPOPT_AlgorithmParameters, init_net2planParameters));
                    if (working.getDemandsBlocked(wIP).size() == 0 && cost < best) {
                        seqIPLinks = candidate;
                        best = cost;
                    }
                }
            }

            if (seqIPLinks != null) {
            /* Allocate resources for the potential lightpaths of the IP path */
            for (Link ipLink : seqIPLinks) {
                if(System.getProperty("debug") != null)
                    System.out.println("\tOPP:allocating lightpath resourcesc");
                String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
                Demand wdmDemand = ipLink.getCoupledDemand();
                if (wdmDemand == null
                        && (doNotDeleteMeString == null || !doNotDeleteMeString.equalsIgnoreCase("true"))) {
                    createLightpath(ipLink);
                }
            }
            /* Allocate the route */
            if (oppAlgorithm.equalsIgnoreCase("pathSelectionPolicy")) {
              Route route = currentNetPlan.addRoute(ipDemand, bandwidthInGbps, bandwidthInGbps, seqIPLinks, null);
              netRapRouteList.addRoute(new Pair<>("ROUTE", route, false));
            }
        }
     }
        if(System.getProperty("debug") != null)
            System.out.println("\tOPP:not allocated, pruning ip links");
        /* Prune unused IP links (i.e., all the potential links added so far) */
        List<Link> ipLinks = new ArrayList<>(currentNetPlan.getLinks(ipLayer));
        for (Link ipLink : ipLinks) {
            String doNotDeleteMeString = ipLink.getAttribute("do not delete me");
            if (ipLink.getCoupledDemand() == null &&
                    (doNotDeleteMeString == null || !doNotDeleteMeString.equalsIgnoreCase("true")))
                ipLink.remove();
        }
        /* HERE THE CODE FOR THE IP OPTIMIZER MODULE */
        if(System.getProperty("debug") != null)
            System.out.println("OPP:calling IPOPT");
        if (oppAlgorithm.equalsIgnoreCase("IPOPTcost"))
            IPOPT.executeAlgorithm(currentNetPlan, IPOPT_AlgorithmParameters, init_net2planParameters);
        allocated = (currentNetPlan.getDemandsBlocked(ipLayer).size() == 0);
    

        return allocated;
    }

    private Pair<List<Link>, List<Link>> findDisjointPathPair(List<List<Link>> candidatePaths, Demand demand) {
        double minAvailability = Double.parseDouble(demand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE)) / 100;
        double NodeAvailability = 1.0;
        if (minAvailability > 0) {
            Set<SharedRiskGroup> srgs_Nodes = new HashSet<>();
            srgs_Nodes.addAll(demand.getIngressNode().getSRGs());
            srgs_Nodes.addAll(demand.getEgressNode().getSRGs());
            for (SharedRiskGroup srg : srgs_Nodes) NodeAvailability *= srg.getAvailability();
        }

        for (List<Link> workingPath : candidatePaths) {
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
                    srgs_Working.addAll(findLightpathSRGs(demand.getNetPlan(), ipLink));
                for (SharedRiskGroup srg : srgs_Working) availability_Working *= srg.getAvailability();
            }

            for (List<Link> backupPath : candidatePaths) {
                boolean disjointPaths = true;
                boolean firstLink = true;

                for (Link backupLink : backupPath) {
                    if (backupLink.getAttribute("originalNode") != null) continue;
                    if (workingPathLinks.contains(backupLink)) {
                        disjointPaths = false;
                        break;
                    }
                    if (firstLink) {
                        firstLink = false;
                    } else {
                        if (workingPathSequenceOfIntermediateNodes.contains(backupLink.getOriginNode())) {
                            disjointPaths = false;
                            break;
                        }
                    }
                }

                if (disjointPaths) {
                    if(System.getProperty("debug") != null) {
                        System.out.println("Checking for disjointness between working path " + pathToString(workingPath) +
                                " and backup " + pathToString(backupPath));
                    }
                    boolean disjointAtOpticalLayer = checkDisjointnessAtWDMLayer(workingPath, backupPath);
                    if (disjointAtOpticalLayer) {
                        if(System.getProperty("debug") != null)
                        System.out.println("  They are disjoint!");
                    } else {
                        if(System.getProperty("debug") != null)
                        System.out.println("  They are NOT disjoint!");
                    }
                    if (disjointAtOpticalLayer) {
                        /* check if Avl is satisfied */
                        /* get the path Avl */
                        double availability_Backup = 1.0;
                        if (minAvailability > 0) {
                            Set<SharedRiskGroup> srgs_Backup = new HashSet<>();
                            for (Link ipLink : backupPath)
                                srgs_Backup.addAll(findLightpathSRGs(demand.getNetPlan(), ipLink));
                            for (SharedRiskGroup srg : srgs_Backup) availability_Backup *= srg.getAvailability();
                        }
                        double total_path_availability = availability_Working + availability_Backup - availability_Working * availability_Backup / NodeAvailability;
                        if(System.getProperty("debug") != null)
                            System.out.println("Checking path availablity, total availability is: " + total_path_availability + " constraint is: " + minAvailability);

                        if (total_path_availability > minAvailability) {
                            if(System.getProperty("debug") != null)
                                System.out.println("W " + availability_Working + "B " + availability_Backup + "N " + NodeAvailability + "m " + minAvailability + "T " + total_path_availability);
                            return Pair.of(workingPath, backupPath);
                        }
                    }
                }
            }
        }
        if(System.getProperty("debug") != null)
            System.out.println("findDisjointPathPair returning NULL!");
        return null;
    }

    private boolean checkDisjointnessAtWDMLayer(List<Link> workingLinkList, List<Link> backupLinkList) {
        boolean disjointPathPairs = true;
        Set<NetworkElement> resourcesPath = new HashSet<>();
        for (Link ipLink : workingLinkList) {
            if (ipLink.getAttribute("do not delete me") != null) continue;
            List<Link> seqLinks = getLightpathLinks(ipLink);

            for (Link link : seqLinks) {
                if (link.getAttribute("originalNode") == null)
                    resourcesPath.add(link);
			/* We ignore node disjointness in the WDM layer, assuming ROADMs will not fail.
				if (firstLink) firstLink = false;
				else resourcesPath.add(link.getOriginNode());
			*/
            }
        }

        for (Link ipLink : backupLinkList) {
            if (ipLink.getAttribute("do not delete me") != null) continue;
            List<Link> seqLinks = getLightpathLinks(ipLink);

            for (Link link : seqLinks) {
                if (resourcesPath.contains(link)) {
                    disjointPathPairs = false;
                }
            }
        }
        return disjointPathPairs;

    }

    private void findSLAViolations(NetPlan netplan, NetworkLayer ipLayer)
        /*find how much a netplan violates the demanded service requirements*/ {
        double maxLatencyInMs, minAvailability;
        for (Demand ipDemand : netplan.getDemands(ipLayer)) {
            /*if the demand is already labeled as violating, skip it*/
            if (ipDemand.getAttribute("SLAViolating") != null) continue;
            /*get the demand's service requirements*/
            if (IgnoreAvailabilityandLatency) {
                maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.GENERATED_LATENCY_MS_ATTRIBUTE));
                minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.GENERATED_AVAILABILITY_LEVEL_ATTRIBUTE));
            } else {
                maxLatencyInMs = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE));
                minAvailability = Double.parseDouble(ipDemand.getAttribute(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE));
            }

            /*for each route, check if*/
            route_loop:
            for (Route r : ipDemand.getRoutes()) {
                /* the route propagation delay is less than the demand latency*/
                if (r.getPropagationDelayInMiliseconds() > maxLatencyInMs + PRECISION_FACTOR) {
                    ipDemand.setAttribute("SLAViolating", "true");
                    if (!IgnoreAvailabilityandLatency)
                        throw new Net2PlanException("r.getPropagationDelayInMiliseconds() " + r.getPropagationDelayInMiliseconds() + " > " + "maxLatencyInMs " + maxLatencyInMs);
                    break;
                }
                /* that the protection segments, if any, have propagation less than the demand latency*/
                Set<ProtectionSegment> protSegSet = r.getPotentialBackupProtectionSegments();
                for (ProtectionSegment protSeg : protSegSet)
                    if (protSeg.getPropagationDelayInMs() > maxLatencyInMs + PRECISION_FACTOR) {
                        ipDemand.setAttribute("SLAViolating", "true");
                        if (!IgnoreAvailabilityandLatency)
                            throw new Net2PlanException("protSeg.getPropagationDelayInMs() " + protSeg.getPropagationDelayInMs() + " > " + "maxLatencyInMs " + maxLatencyInMs);
                        break route_loop;
                    }
                /* that the route has sufficient availability */
                Set<SharedRiskGroup> srgs_RoutePath = new LinkedHashSet<>();
                for (Link ipLink : r.getSeqLinksRealPath())
                    srgs_RoutePath.addAll(findLightpathSRGs(netplan, ipLink));
                double availability_RoutePath = 1;
                for (SharedRiskGroup srg : srgs_RoutePath) availability_RoutePath *= srg.getAvailability();

                boolean Protected = Boolean.parseBoolean(ipDemand.getAttribute(ApplicationAwareConstants.PROTECTION_ATTRIBUTE));
                if (Protected) {
                    double NodeAvailability = 1.0;
                    Set<SharedRiskGroup> srgs_Nodes = new HashSet<>();
                    srgs_Nodes.addAll(ipDemand.getIngressNode().getSRGs());
                    srgs_Nodes.addAll(ipDemand.getEgressNode().getSRGs());
                    for (SharedRiskGroup srg : srgs_Nodes) NodeAvailability *= srg.getAvailability();
                    for (ProtectionSegment p : r.getPotentialBackupProtectionSegments()) {
                        double availability_Working = availability_RoutePath;
                        double availability_Backup = 1.0;
                        Set<SharedRiskGroup> srgs_Backup = new HashSet<>();
                        for (Link ipLink : p.getSeqLinks())
                            srgs_Backup.addAll(findLightpathSRGs(ipDemand.getNetPlan(), ipLink));
                        for (SharedRiskGroup srg : srgs_Backup) availability_Backup *= srg.getAvailability();
                        availability_RoutePath = availability_Working + availability_Backup - availability_Working * availability_Backup / NodeAvailability;
                    }
                }
                if (availability_RoutePath /*+ PRECISION_FACTOR*/ < minAvailability / 100) {
                    ipDemand.setAttribute("SLAViolating", "true");
                    if (!IgnoreAvailabilityandLatency)
                        throw new Net2PlanException("availability_RoutePath " + availability_RoutePath + " < minAvailability " + minAvailability);
                    break;
                }

                /* If not, label the demand as Violating and break out of the inner loop and continue in the outer
			 * (no need to explicitly continue in the outer)*/
            }
        }
    }

    private enum FlexgridPolicy {
        minBW, maxBW, mix
    }
}
