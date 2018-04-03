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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

public class CACSim_EG_exponentialConnectionGenerator_ApplicationAware extends IEventGenerator
{
	//Simulation attributes
	private String pathDirectory;
	private boolean incrementalModel;
	private long numConnectionsSimulation, numConnectionsTransitory, currentNumConnections;
	private Random seedGenerator;
	private String trafficGeneration;
	private String simulationModel;
	private NetworkLayer trafficLayer;
	
	//Poisson process attributes
	private double averageHoldingTimeInSec;
	private double averageInterarrivalTimeInSec;
	
	//Application requirements attributes
	private double[] wdmClassCumulativeProbability;
	private long[] maxLatencyArrayInMs;
	private double[] minBandwidthArrayInMbps;
	private double[] minAvailabilityArray;
	
	//Network attributes 
	private double lightpathBinaryRateInGbps = 100;
	
	@Override
	public String getDescription()
	{
		return "Connection generator in which connection requests "
				+ "arrive according to a Poisson process, are "
				+ "independent of each other and are characterized by "
				+ "service-dependent application requirements";
	}
	
	private InputParameter _pathDirectory = new InputParameter("pathDirectory", "C:/Users/admin/Documents/AA Algorithms 0.4.0/workspace/data/trafficMatrices", "Directory for external files");
	private InputParameter _averageHoldingTimeInSec = new InputParameter("averageHoldingTimeInSec", "1000", "Average connection duration (in seconds) (1/mu)");
	private InputParameter _averageInterarrivalTimeInSec = new InputParameter("averageInterarrivalTimeInSec", "1", "Average connection interarrival time (in seconds) (1/lambda)");
	private InputParameter _numConnectionsSimulation = new InputParameter("numConnectionsSimulation", "1000", "Number of connections to finish the simulation (a negative or zero value means no limit)");
	private InputParameter _numConnectionsTransitory = new InputParameter("numConnectionsTransitory", "-1", "Number of connections to finish the transitory (a negative or zero value means no transitory)");
	private InputParameter _randomSeed = new InputParameter("randomSeed", "1", "Seed for the random generator (-1 means random)");
	private InputParameter _trafficLayer = new InputParameter("trafficLayer", "ELECTRONIC", "Layer containing traffic demands");
	private InputParameter _simulationModel = new InputParameter("simulationModel", "#select# longRun incrementalModel", "Simulation model: 'longRun' (connections are established and released), 'incrementalModel' (connections are never released)");
	private InputParameter _minBandwidthArrayInMbps = new InputParameter("minBandwidthArrayInMbps", "1000", "Possible minimum bandwidth for the connection requests (in Mbps)");
	private InputParameter _maxLatencyArrayInMs = new InputParameter("maxLatencyArrayInMs", "5,100", "Possible maximum tolerated latencies for the connection requests (in milliseconds)");
	private InputParameter _minAvailabilityArray = new InputParameter("minAvailabilityArray", "90,99", "Possible minimum tolerated availabilities for the connection requests (in %)");
	private InputParameter _wdmClassRatio = new InputParameter("wdmClassRatio", "9:1", "Relative proportion to allocate demands in diffrerent WDM classes");
	private InputParameter _trafficGeneration = new InputParameter("trafficGeneration", "#select# uniform TIDtraffic", "Traffic distribution: 'uniform' (uniform distribution among all the nodes), 'TIDtraffic' (traffic distribution for the TID topology)");
	
	@Override
	public List<Triple<String, String, String>> getParameters()
	{
		/* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
		return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
	}
	
	//Initialization of the algorithm
	
	@Override
	public void initialize(NetPlan currentNetPlan, Map<String, String> algorithmParameters, Map<String, String> simulationParameters, Map<String, String> net2planParameters)
	{
		InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);
		
		//Parse pathDirectory
		pathDirectory = _pathDirectory.getString();
		
		//Parse seed generator
		String seedString = _randomSeed.getString();
		long seed = Long.parseLong(seedString);
		if (seed == -1)	seed = RandomUtils.random(0, Long.MAX_VALUE - 1);
		seedGenerator = new Random(seed);
		
		//Parse trafficLayer
		String trafficLayerString = _trafficLayer.getString();
		trafficLayer = currentNetPlan.getNetworkLayer(trafficLayerString);
		if (trafficLayer == null) throw new Net2PlanException ("Unknown layer");

		//Parse averageHoldingTime
		String averageHoldingTimeInSecString = _averageHoldingTimeInSec.getString();
		averageHoldingTimeInSec = Double.parseDouble(averageHoldingTimeInSecString);
		if (averageHoldingTimeInSec <= 0) throw new Net2PlanException("'averageHoldingTimeInSec' must be greater than zero");
		
		//Parse averageInterarrivalTime
		String averageInterarrivalTimeInSecString = _averageInterarrivalTimeInSec.getString();
		averageInterarrivalTimeInSec = Double.parseDouble(averageInterarrivalTimeInSecString);
		if (averageInterarrivalTimeInSec <= 0) throw new Net2PlanException("'averageInterarrivalTimeInSec' must be greater than zero");
		
		//Parse wdmClassRatio and compute wdmClassCumulativeProbability
		String[] wdmClassRatioString = _wdmClassRatio.getString().split(":");;
		double[] wdmClassAux = new double[wdmClassRatioString.length];
		double wdmClassRatioSum = 0;
		for (int i=0; i < wdmClassAux.length; i++)
		{
			wdmClassAux[i] = Double.parseDouble(wdmClassRatioString[i]);
			if (wdmClassAux[i] <= 0)
				throw new Net2PlanException("wdmClassRatio values must be > 0.0");
			wdmClassRatioSum += wdmClassAux[i];
		}
		wdmClassAux[0] /= wdmClassRatioSum;
		for (int i=1; i < wdmClassAux.length; i++)
		{
			wdmClassAux[i] /= wdmClassRatioSum;
			wdmClassAux[i] += wdmClassAux[i-1];
		}
		wdmClassCumulativeProbability = wdmClassAux;

		//Parse minBandwidthArray in the right form
		String minBandwidthArrayInMbpsString = _minBandwidthArrayInMbps.getString();
		String[] minBandwidthArrayInMbpsStringParse = minBandwidthArrayInMbpsString.split(",");
		double[] minBandwidthArrayInMbpsAux = new double[minBandwidthArrayInMbpsStringParse.length];
		
		for (int i=0; i < minBandwidthArrayInMbpsStringParse.length; i++)
		{
			minBandwidthArrayInMbpsAux[i] = Double.parseDouble(minBandwidthArrayInMbpsStringParse[i]);
		}
		minBandwidthArrayInMbps = minBandwidthArrayInMbpsAux;
		
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
		if (!simulationModel.equalsIgnoreCase("longRun") && !simulationModel.equalsIgnoreCase("incrementalModel")) throw new Net2PlanException("Simulation model must be either 'longRun' or 'incrementalModel'");
		incrementalModel = simulationModel.equalsIgnoreCase("incrementalModel");
		
		//Parse the Traffic Generation
		trafficGeneration = _trafficGeneration.getString();
		if (!trafficGeneration.equalsIgnoreCase("uniform") && !trafficGeneration.equalsIgnoreCase("TIDtraffic")) throw new Net2PlanException("Traffic generation must be either 'uniform' or 'TIDtraffic'");
		
		//Generate the first connection arrival
		scheduleNewConnectionArrival(0, currentNetPlan);
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
			double minBandwidth = connectionRequest.minBandwidthInMbps;
			long maxLatency = connectionRequest.maxLatencyInMs;
			double minAvailability = connectionRequest.minAvailability;
			int wdmClass = connectionRequest.wdmClass;

			/* Prepare the demand attributes that must be attached to the demand that it is going to be created */
			
			//Simulation attributes
			Map<String, String> demandAttributes = new LinkedHashMap<String, String>();
			demandAttributes.put("arrivalTime", StringUtils.secondsToYearsDaysHoursMinutesSeconds(simTime));
			if (!incrementalModel) demandAttributes.put("duration", StringUtils.secondsToYearsDaysHoursMinutesSeconds(duration));
			if (!incrementalModel) demandAttributes.put("departureTime", StringUtils.secondsToYearsDaysHoursMinutesSeconds(simTime + duration));
			
			//Application-aware demand attributes. The bandwidth is not included because it is treated as OfferedTraffic for the demand
			demandAttributes.put(ApplicationAwareConstants.LATENCY_MS_ATTRIBUTE, Double.toString(maxLatency));
			demandAttributes.put(ApplicationAwareConstants.AVAILABILITY_LEVEL_ATTRIBUTE, Double.toString(minAvailability));
			demandAttributes.put(ApplicationAwareConstants.WDM_CLASS_ATTRIBUTE, Integer.toString(wdmClass));
			
			//Add the demand
			//SimEvent.DemandAdd addDemand = new SimEvent.DemandAdd(originNode, destinationNode, trafficLayer, minBandwidth);
			
			double numLightpathsDemand = Math.ceil(minBandwidth/lightpathBinaryRateInGbps);
			
			double bandwidthToAllocate;
			for (int i=0; i<numLightpathsDemand; i++)
			{
				if (minBandwidth - (i+1) * lightpathBinaryRateInGbps > 0)
					bandwidthToAllocate = lightpathBinaryRateInGbps;
				else bandwidthToAllocate = minBandwidth % lightpathBinaryRateInGbps;
				
				Demand demand = currentNetPlan.addDemand(originNode, destinationNode, bandwidthToAllocate, demandAttributes, trafficLayer);
	
				//Pass the demand addition to the event processor
				scheduleEvent(new SimEvent(simTime, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, demand));
				
				//Pass the associated ConnectionRelease to the event generator 
				if (!incrementalModel) scheduleEvent(new SimEvent(simTime + duration, SimEvent.DestinationModule.EVENT_GENERATOR, -1, new ConnectionRelease(demand)));
				
				//Schedule a new connection arrival
				scheduleNewConnectionArrival(simTime, currentNetPlan);
				
				//Increase the counter of connection requests
				currentNumConnections++;
			}
			
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
	private void scheduleNewConnectionArrival(double simTime, NetPlan currentNetPlan)
	{
		Node originNode = null;
		Node destinationNode = null;
		
		ArrayList<Long> nodeIdsAux = currentNetPlan.getNodeIds();
		Set<Long> nodeIds = new LinkedHashSet<Long>(nodeIdsAux);
		List<Node> nodeList = currentNetPlan.getNodes();
		List<Node> nodeListIp = new LinkedList<Node>(nodeList);
		
		for(Node node : nodeList)
		{
			String isIPNodeString = node.getAttribute("IPNode");
			boolean isIPNode = Boolean.parseBoolean(isIPNodeString);
			if (!isIPNode) nodeListIp.remove(node);
		}
		
		if (trafficGeneration.equalsIgnoreCase("uniform")) {
			do
			{
				originNode = nodeListIp.get(seedGenerator.nextInt(nodeListIp.size()));
				destinationNode = nodeListIp.get(seedGenerator.nextInt(nodeListIp.size()));
			} while (originNode == destinationNode);
		}
		else if (trafficGeneration.equalsIgnoreCase("TIDtraffic")) {
			//Use the TID traffic pattern
			try {
				int[] sourceDest = generateNewConnectionNodesTIDtraffic(seedGenerator);
				int originNodeIdIndex = sourceDest[0];
				int destinationNodeIdIndex = sourceDest[1];
				
				//Convert from Set<Long> to Array 
				long[] nodeIdsArray = new long[nodeIds.size()];
				int i = 0;
				for (Long l : nodeIds) nodeIdsArray[i++] = l;
				
				long originNodeId = nodeIdsArray[originNodeIdIndex];
				long destinationNodeId = nodeIdsArray[destinationNodeIdIndex];
				
				originNode = currentNetPlan.getNodeFromId(originNodeId);
				destinationNode = currentNetPlan.getNodeFromId(destinationNodeId);
				
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	 
		}
		
		//Compute the Holding Time and the Interarrival Time for the connection request according to an exponential distribution
		//It is a death/birth process with Poisson arrivals at rate lambda=1/InterarrivalTime and service rate mu=1/averageHoldingTime 
		double ht_d = new Exponential(1/averageHoldingTimeInSec, new MersenneTwister64(seedGenerator.nextInt())).nextDouble();
		double iat_d = new Exponential(1/averageInterarrivalTimeInSec, new MersenneTwister64(seedGenerator.nextInt())).nextDouble();
		
		//Arrival time for the considered ConnectionRequest
		double nextArrivalTime = simTime + iat_d;
		
		//Duration for the considered Connection Request
		double nextDuration = incrementalModel ? Double.MAX_VALUE : ht_d;
		
		//Random choice of application requirements from the input application requirement values
		double minBandwidth;
		if (minBandwidthArrayInMbps.length == 1) minBandwidth = minBandwidthArrayInMbps[0]; 
		else 
			{ 	
				int minBandwidthInMbpsIndex = RandomUtils.select(minBandwidthArrayInMbps, seedGenerator);
				minBandwidth = minBandwidthArrayInMbps[minBandwidthInMbpsIndex];
			}
		
		long maxLatency;
		if (maxLatencyArrayInMs.length == 1) maxLatency = maxLatencyArrayInMs[0];
		else
			{
				int maxLatencyInMsIndex = RandomUtils.select(maxLatencyArrayInMs, seedGenerator);
				maxLatency = maxLatencyArrayInMs[maxLatencyInMsIndex];
			}
		
		double minAvailability;
		if (minAvailabilityArray.length == 1) minAvailability = minAvailabilityArray[0];
		else
			{
				int minAvailabilityIndex = RandomUtils.select(minAvailabilityArray, seedGenerator);
				minAvailability = minAvailabilityArray[minAvailabilityIndex];
			}
		
		double r = seedGenerator.nextDouble();
System.out.println("random = " + r);
		int wdmClass = wdmClassCumulativeProbability.length-1; // just in case the last element isn't 1 because of  rounding error 
		for (int i = 0; i < wdmClassCumulativeProbability.length; i++)
		{
			if (r < wdmClassCumulativeProbability[i])
			{
				wdmClass = i;
				break;
			}
		}
		
		//Generate the new connection request and schedule it in the calendar		
		scheduleEvent(new SimEvent(nextArrivalTime, SimEvent.DestinationModule.EVENT_GENERATOR, -1, new ConnectionRequest(originNode, destinationNode, nextDuration, minBandwidth/1000, maxLatency, minAvailability, wdmClass)));
	}
	
	//Generate connections according to the TID traffic matrix (valid only with TID topology)
	private int[] generateNewConnectionNodesTIDtraffic(Random seedGenerator) throws FileNotFoundException {
		/* numRows and numCol strictly depend on the input file */
		int numRows = 84;
		int numCol = 3;
		int maxValueProbDistribution = 10000; /* Maximum value taken by the probability distribution */
		int colProb = 2; /* Column in which I have the probability */
		
		int source = -1;
		int dest = -1;
		
		/* Needed parameters */
		int index = 0;
		String[][] matrixProb = new String[numRows][numCol];
		File file = new File(pathDirectory + "/probMatrixTIDNetwork.txt");
		Scanner input = new Scanner(file);
		
		/* Read the input file and save the values in the probability matrix */
		while (input.hasNextLine() && index < matrixProb.length) {
			matrixProb[index] = input.nextLine().split(" ");
			index++;
		}
		int[][] matrixProbInt = new int[numRows][numCol];
		for(int i=0; i < numRows; i++){
			for(int j=0; j < numCol; j++) {
				matrixProbInt[i][j] = Integer.parseInt(matrixProb[i][j]);
			}
		} 
		
		input.close();
		
		/* At this point I have a single matrix with all the int values I need */
		int answer = seedGenerator.nextInt(maxValueProbDistribution)+1;

		/* Find the couple source/dest according to the probability extraction */
		boolean found = false;
		int i=0;
		while (!found)
		{
			if (answer <= matrixProbInt[i][colProb]) {
				source = matrixProbInt[i][0];
				dest = matrixProbInt[i][1];
				found = true;
			}
			else i++;
		}
		
		/* Prepare the array to be returned */
		int[] sourceDest = new int[2];
		sourceDest[0] = source;
		sourceDest[1] = dest;
		
		return sourceDest;
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
		public final double minBandwidthInMbps;
		public final long maxLatencyInMs;
		public final double minAvailability;
		public final int wdmClass;
		
		public ConnectionRequest(Node originNode, Node destinationNode, double duration, double minBandwidthInMbps, long maxLatencyInMs, double minAvailability, int wdmClass)
		{
			this.originNode = originNode;
			this.destinationNode = destinationNode;
			this.duration = duration;
			this.minBandwidthInMbps = minBandwidthInMbps;
			this.maxLatencyInMs = maxLatencyInMs;
			this.minAvailability = minAvailability;
			this.wdmClass = wdmClass; 
		}	
	}
}
