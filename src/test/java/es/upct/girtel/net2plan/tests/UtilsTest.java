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

package es.upct.girtel.net2plan.tests;

import com.net2plan.interfaces.networkDesign.NetPlan;
import es.upct.girtel.net2plan.plugins.onos.utils.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.Inet4Address;
import java.util.ArrayList;

public class UtilsTest
{
	private NetPlan netPlan;

	/*
	 * These tests use a valid Net2Plan Topology that has been configures with the appropiate values (node types, ip addresses, etc.)
	 */
	@Before
	public void setUp() throws Exception
	{
		this.netPlan = new NetPlan(new File("src/main/resources/NSFNet_N14_E42_multilayerPCE.n2p"));
	}

	@Test
	public void testNodeIpAddress()
	{
		ArrayList<Long> nodeIds = netPlan.getNodeIds();
		try
		{
			for(long nodeId : nodeIds)
				Utils.getNodeIPAddress(netPlan, nodeId);
		}catch(Exception e)
		{
			Assert.fail("All nodes should have a valid IP address");
		}
	}

	@Test
	public void testGetLinkByIpAddress()
	{
		ArrayList<Long> nodeIds = netPlan.getNodeIds();

		try
		{
		/* Should be a connected topology at layer 0 */
			for(long nodeId : nodeIds)
			{
				Inet4Address ipAddress = Utils.getNodeIPAddress(netPlan, nodeId);

				long link1 = Utils.getLinkByDestinationIPAddress(netPlan,  ipAddress);
				long link2 = Utils.getLinkBySourceIPAddress(netPlan,  ipAddress);

				Assert.assertNotEquals("Link by Destination IP should exist", - 1L, link1);
				Assert.assertNotEquals("Link by Source IP should exist", - 1L, link2);
			}
		}catch(Throwable e)
		{
			Assert.fail();
		}
	}
}
