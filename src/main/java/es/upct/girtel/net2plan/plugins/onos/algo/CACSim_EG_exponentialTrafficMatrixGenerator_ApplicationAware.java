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

import cern.jet.random.tdouble.Exponential;
import cern.jet.random.tdouble.engine.MersenneTwister64;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.simulation.IEventGenerator;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.RandomUtils;
import com.net2plan.utils.StringUtils;
import com.net2plan.utils.Triple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/*import com.net2plan.gui.utils.FileChooserNetworkDesign; - this is not part of the public library*/

public class CACSim_EG_exponentialTrafficMatrixGenerator_ApplicationAware extends IEventGenerator
{
   //Simulation attributes
   private boolean incrementalModel;
   private long numConnectionsSimulation, numConnectionsTransitory, currentNumConnections;
   private Random seedGenerator;
   private String simulationModel;
   private NetworkLayer trafficLayer;

   //Poisson process attributes
   private double averageHoldingTimeInSec;
   private double[] averageInterarrivalTimeInSecPerClass;

   //Application requirements attributes
   private int[] wdmClass;
   private long[] maxLatencyArrayInMs;
   private double[] minBandwidthArrayInGbps;
   private double[] maxBandwidthArrayInGbps;
   private double[] minAvailabilityArray;

   private NetworkLayer trafficMatrixLayer;

   @Override
   public String getDescription()
   {
      return "Connection generator in which connection requests "
            + "arrive according to a Poisson process, are "
            + "independent of each other and are characterized by "
            + "service-dependent application requirements";
   }

   private InputParameter _averageHoldingTimeInSec = new InputParameter("averageHoldingTimeInSec", "1000",
      "Average connection duration (in seconds) (1/mu)");
   private InputParameter _averageInterarrivalTimeInSec = new InputParameter("averageInterarrivalTimeInSec", "1",
      "Average connection interarrival time (in seconds) (1/lambda)");
   private InputParameter _numConnectionsSimulation = new InputParameter("numConnectionsSimulation", "1000",
      "Number of connections to finish the simulation (a negative or zero value means no limit)");
   private InputParameter _numConnectionsTransitory = new InputParameter("numConnectionsTransitory", "-1",
      "Number of connections to finish the transitory (a negative or zero value means no transitory)");
   private InputParameter _randomSeed = new InputParameter("randomSeed", "1", 
      "Seed for the random generator (-1 means random)");
   private InputParameter _trafficLayer = new InputParameter("trafficLayer", "ELECTRONIC",
      "Layer containing traffic demands");
   private InputParameter _simulationModel = new InputParameter("simulationModel", "#select# longRun incrementalModel",
      "Simulation model: 'longRun' (connections are established and released), 'incrementalModel' (connections are never released)");
   private InputParameter _minBandwidthArrayInGbps = new InputParameter("minBandwidthArrayInGbps", "10",
      "Possible minimum bandwidth for the connection requests (in Gbps)");
   private InputParameter _maxBandwidthArrayInGbps = new InputParameter("maxBandwidthArrayInGbps", "10",
      "Possible maximum bandwidth for the connection requests (in Gbps)");
   private InputParameter _maxLatencyArrayInMs = new InputParameter("maxLatencyArrayInMs", "5,100",
      "Possible maximum tolerated latencies for the connection requests (in milliseconds)");
   private InputParameter _minAvailabilityArray = new InputParameter("minAvailabilityArray", "90,99",
      "Possible minimum tolerated availabilities for the connection requests (in %)");
   private InputParameter _wdmClass = new InputParameter("wdmClass", "0,1", "Each wdmClass will be routed independently in the WDM layer");

   @Override
   public List<Triple<String, String, String>> getParameters()
   {
      /* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
      return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
   }

   //Initialization of the algorithm
   @Override
   public void initialize(NetPlan currentNetPlan, Map<String, String> algorithmParameters, 
      Map<String, String> simulationParameters, Map<String, String> net2planParameters)
   {
      InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

      //Parse seed generator
      String seedString = _randomSeed.getString();
      long seed = Long.parseLong(seedString);
      if (seed == -1)   seed = RandomUtils.random(0, Long.MAX_VALUE - 1);
      seedGenerator = new Random(seed);

      //Parse trafficLayer
      String trafficLayerString = _trafficLayer.getString();
      trafficLayer = currentNetPlan.getNetworkLayer(trafficLayerString);
      if (trafficLayer == null) throw new Net2PlanException ("Unknown layer");

      trafficMatrixLayer = currentNetPlan.getNetworkLayer("TRAFFIC MATRIX");
      if (trafficMatrixLayer == null)
      {
    	  throw new Net2PlanException ("Without a layer called TRAFFIC MATRIX I will not generate demands.");
    	  /*
         //Create a new layer to store the traffic matrix related demands
         trafficMatrixLayer = currentNetPlan.addLayer("TRAFFIC MATRIX", "Traffic matrix", "", "Gb/s", null);

         //Open the demands file and create demands
         File file = SystemUtils.getCurrentDir();

         FileChooserNetworkDesign fc_demands = new FileChooserNetworkDesign(file, DialogType.DEMANDS);
         int rc = fc_demands.showOpenDialog(null);
         if (rc != JFileChooser.APPROVE_OPTION) return;

         NetPlan aux = fc_demands.readDemands();

         for (Demand originalDemand : aux.getDemands())
         {
            currentNetPlan.addDemand(currentNetPlan.getNode(originalDemand.getIngressNode().getIndex()),
                  currentNetPlan.getNode(originalDemand.getEgressNode().getIndex()), originalDemand.getOfferedTraffic(),
                  originalDemand.getAttributes(),trafficMatrixLayer);
         }
         */
      }

      final int D = currentNetPlan.getNumberOfDemands(trafficMatrixLayer);

      //Parse averageHoldingTime
      String averageHoldingTimeInSecString = _averageHoldingTimeInSec.getString();
      averageHoldingTimeInSec = Double.parseDouble(averageHoldingTimeInSecString);
      if (averageHoldingTimeInSec <= 0) throw new Net2PlanException("'averageHoldingTimeInSec' must be greater than zero");

      //Parse averageInterarrivalTime
      String averageInterarrivalTimeInSecString = _averageInterarrivalTimeInSec.getString();

      //Parse wdmClass
      String wdmClassString = _wdmClass.getString();
      String[] wdmClassStringParse = wdmClassString.split(",");
      wdmClass = new int[wdmClassStringParse.length];

      for (int i=0; i < wdmClassStringParse.length; i++)
      {
        wdmClass[i] = Integer.parseInt(wdmClassStringParse[i].trim());
      }

      //Parse minBandwidthArray in the right form
      String minBandwidthArrayInGbpsString = _minBandwidthArrayInGbps.getString();
      String[] minBandwidthArrayInGbpsStringParse = minBandwidthArrayInGbpsString.split(",");
      minBandwidthArrayInGbps = new double[minBandwidthArrayInGbpsStringParse.length];

      for (int i=0; i < minBandwidthArrayInGbpsStringParse.length; i++)
      {
	  minBandwidthArrayInGbps[i] = Double.parseDouble(minBandwidthArrayInGbpsStringParse[i]);
      }

      //Parse maxBandwidthArray in the right form
      String maxBandwidthArrayInGbpsString = _maxBandwidthArrayInGbps.getString();
      String[] maxBandwidthArrayInGbpsStringParse = maxBandwidthArrayInGbpsString.split(",");
      maxBandwidthArrayInGbps = new double[maxBandwidthArrayInGbpsStringParse.length];

      for (int i=0; i < maxBandwidthArrayInGbpsStringParse.length; i++)
      {
	  maxBandwidthArrayInGbps[i] = Double.parseDouble(maxBandwidthArrayInGbpsStringParse[i]);
      }

      //Parse maxLatencyArray in the right form
      String maxLatencyArrayInMsString = _maxLatencyArrayInMs.getString();
      String[] maxLatencyArrayInMsStringParse = maxLatencyArrayInMsString.split(",");
      long[] maxLatencyArrayInMsAux = new long[maxLatencyArrayInMsStringParse.length];

      for (int i=0; i < maxLatencyArrayInMsStringParse.length; i++)
      {
         maxLatencyArrayInMsAux[i] = Long.parseLong(maxLatencyArrayInMsStringParse[i]);
      }
      maxLatencyArrayInMs = maxLatencyArrayInMsAux;

      //Parse minAvailabilityArray in the right form
      String minAvailabilityArrayString = _minAvailabilityArray.getString();
      String[] minAvailabilityArrayStringParse = minAvailabilityArrayString.split(",");
      double[] minAvailabilityArrayAux = new double[minAvailabilityArrayStringParse.length];

      for (int i=0; i < minAvailabilityArrayStringParse.length; i++)
      {
         minAvailabilityArrayAux[i] = Double.parseDouble(minAvailabilityArrayStringParse[i]);
      }
      minAvailabilityArray = minAvailabilityArrayAux;

      //Parse Simulation and Transitory parameters
      String numConnectionsSimulationString = _numConnectionsSimulation.getString();
      numConnectionsSimulation = Long.parseLong(numConnectionsSimulationString);
      String numConnectionsTransitoryString = _numConnectionsTransitory.getString();
      numConnectionsTransitory = Long.parseLong(numConnectionsTransitoryString);
      if (numConnectionsSimulation <= 0) numConnectionsSimulation = Long.MAX_VALUE;
      if (numConnectionsTransitory <= 0) numConnectionsTransitory = Long.MAX_VALUE;

      //Parse the Simulation Model
      simulationModel = _simulationModel.getString();
      if (!simulationModel.equalsIgnoreCase("longRun") && !simulationModel.equalsIgnoreCase("incrementalModel")) 
         throw new Net2PlanException("Simulation model must be either 'longRun' or 'incrementalModel'");
      incrementalModel = simulationModel.equalsIgnoreCase("incrementalModel");

      //Schedule connections for all classes based on traffic matrix
      for (Demand originalDemand : currentNetPlan.getDemands(trafficMatrixLayer))
      {
         final String [] trafficClassStr = originalDemand.getAttribute("trafficClassPercentage").split(",");
         double trafficClassPercentage[] = new double [trafficClassStr.length];
         for (int i=0;i<trafficClassStr.length;i++)
            trafficClassPercentage[i] = Double.parseDouble(trafficClassStr[i]);
         
         Node originNode = originalDemand.getIngressNode();
         Node destinationNode = originalDemand.getEgressNode();
         double offeredLoad = originalDemand.getOfferedTraffic();
         for (int tclass=0;tclass<trafficClassPercentage.length;tclass++)
         {
            double offeredLoadPerClass = offeredLoad*trafficClassPercentage[tclass];
            double minBandwidth = minBandwidthArrayInGbps[tclass%minBandwidthArrayInGbps.length];
            double maxBandwidth = maxBandwidthArrayInGbps[tclass%maxBandwidthArrayInGbps.length];
	    double avgBandwidth = (minBandwidth+maxBandwidth)/2;
            double demandInterarrivalTime = averageHoldingTimeInSec*avgBandwidth/offeredLoadPerClass;
            scheduleNewConnectionArrival(currentNumConnections, 0, currentNetPlan, originNode, destinationNode,
               demandInterarrivalTime, tclass);
         }
      }
   }

   //Processing the events coming by some part of this event generator code
   @Override
   public void processEvent(NetPlan currentNetPlan, SimEvent event)
   {
      double simTime = event.getEventTime(); 
      Object eventObject = event.getEventObject();

      //The event is a ConnectionRequest event
      if (eventObject instanceof ConnectionRequest)
      {
         //Check if the transitory or the simulation must finish 
         if (numConnectionsTransitory != -1 && currentNumConnections >= numConnectionsTransitory)
         {
            endTransitory();
            numConnectionsTransitory = -1;
         }
         
         if (numConnectionsSimulation != -1 && currentNumConnections >= numConnectionsSimulation)
         {
            endSimulation();
         }

         //Cast for the generic eventObject
         ConnectionRequest connectionRequest = (ConnectionRequest) eventObject;

         //Get the parameters from the ConnectionRequest
         double duration = connectionRequest.duration;
         Node originNode = connectionRequest.originNode;
         Node destinationNode = connectionRequest.destinationNode;
         double bandwidth = connectionRequest.bandwidthInGbps;
         double demandAvgInterarrivalTime = connectionRequest.demandAvgInterarrivalTime;
         int tclass = connectionRequest.tclass;

         long maxLatency = maxLatencyArrayInMs[tclass % maxLatencyArrayInMs.length];
         double minAvailability = minAvailabilityArray[tclass % minAvailabilityArray.length];
         int WDMClass = wdmClass[tclass % wdmClass.length];

         /* Prepare the demand attributes that must be attached to the demand that it is going to be created */

         //Simulation attributes
         Map<String, String> demandAttributes = new LinkedHashMap<String, String>();
         demandAttributes.put("arrivalTime", StringUtils.secondsToYearsDaysHoursMinutesSeconds(simTime));
         if (!incrementalModel)
            demandAttributes.put("duration", StringUtils.secondsToYearsDaysHoursMinutesSeconds(duration));
         if (!incrementalModel) 
            demandAttributes.put("departureTime", StringUtils.secondsToYearsDaysHoursMinutesSeconds(simTime + duration));

         //Application-aware demand attributes.
         //The bandwidth is not included because it is treated as OfferedTraffic for the demand
         demandAttributes.put(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE, Double.toString(maxLatency));
         demandAttributes.put(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE, Double.toString(minAvailability));
         demandAttributes.put(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE, Integer.toString(WDMClass));

         //Add the demand     
         Demand demand = currentNetPlan.addDemand(originNode, destinationNode, bandwidth, demandAttributes, trafficLayer);

         //Pass the demand addition to the event processor
         scheduleEvent(new SimEvent(simTime, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, demand));

         //Pass the associated ConnectionRelease to the event generator 
         if (!incrementalModel) scheduleEvent(new SimEvent(simTime + duration, SimEvent.DestinationModule.EVENT_GENERATOR,
            -1, new ConnectionRelease(demand)));

         //Schedule a new connection arrival
         scheduleNewConnectionArrival(currentNumConnections, simTime,
            currentNetPlan,originNode,destinationNode,demandAvgInterarrivalTime,tclass);

         //Increase the counter of connection requests
         currentNumConnections++;
      }

      //The event is a ConnectionRelease event
      else if (eventObject instanceof ConnectionRelease)
      {
         if (incrementalModel) throw new Net2PlanException("In incremental model connections are never released");
         ConnectionRelease connectionRelease = (ConnectionRelease) eventObject;
         Demand currentDemand = connectionRelease.currentDemand;

         //Remove the considered demand
         SimEvent.DemandRemove removeDemand = new SimEvent.DemandRemove(currentDemand);

         //Pass the event to the event processor
         scheduleEvent(new SimEvent(simTime, SimEvent.DestinationModule.EVENT_PROCESSOR,-1, removeDemand));
      }
   }

   //Generate a new ConnectionRequest
   private void scheduleNewConnectionArrival(long currentNumConnections, double simTime, NetPlan currentNetPlan,
      Node originNode, Node destinationNode, double demandAvgInterarrivalTime, int tclass)
   {
      double minBandwidth = minBandwidthArrayInGbps[tclass%minBandwidthArrayInGbps.length];
      double maxBandwidth = maxBandwidthArrayInGbps[tclass%maxBandwidthArrayInGbps.length];
      double bandwidth;
      if (minBandwidth < maxBandwidth)
	bandwidth = RandomUtils.random(minBandwidth, maxBandwidth);
      else
	bandwidth = minBandwidth;
      //Compute the Holding Time and the Interarrival Time for the connection request according to an exponential distribution
      //It is a death/birth process with Poisson arrivals at rate lambda=1/InterarrivalTime 
      //and service rate mu=1/averageHoldingTime 
      double ht_d = new Exponential(1/averageHoldingTimeInSec, new MersenneTwister64(seedGenerator.nextInt())).nextDouble();
      double iat_d = new Exponential(1/demandAvgInterarrivalTime,
         new MersenneTwister64(seedGenerator.nextInt())).nextDouble();

      //Arrival time for the considered ConnectionRequest
      double nextArrivalTime = simTime + iat_d;

      //Duration for the considered Connection Request
      double nextDuration = incrementalModel ? Double.MAX_VALUE : ht_d;

      //Generate the new connection request and schedule it in the calendar      
      scheduleEvent(new SimEvent(nextArrivalTime, SimEvent.DestinationModule.EVENT_GENERATOR, -1,
         new ConnectionRequest(originNode, destinationNode, nextDuration, bandwidth, demandAvgInterarrivalTime, tclass)));
   }

   //ConnectionRelease class, it only has the DemandId to be terminated as attribute
   private static class ConnectionRelease
   {
      public final Demand currentDemand;
      
      public ConnectionRelease(Demand currentDemand)
      {
         this.currentDemand = currentDemand;
      }

      @Override
      public String toString() { return "Release connection " + currentDemand.getId(); }
   }

   //ConnectionRequest class, it has as attributes all the attributes that are needed to define the new ConnectionRequest
   private static class ConnectionRequest
   {
      public final Node originNode;
      public final Node destinationNode;
      public final double duration;
      public final double bandwidthInGbps;
      public final double demandAvgInterarrivalTime;
      public final int tclass;

      public ConnectionRequest(Node originNode, Node destinationNode, double duration, double bandwidthInGbps,
         double demandAvgInterarrivalTime, int tclass)
      {
         this.originNode = originNode;
         this.destinationNode = destinationNode;
         this.duration = duration;
         this.bandwidthInGbps = bandwidthInGbps;
         this.demandAvgInterarrivalTime = demandAvgInterarrivalTime;
         this.tclass = tclass;
      }   
   }
}
