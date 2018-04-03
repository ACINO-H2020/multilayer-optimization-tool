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

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Triple;

import java.util.List;
import java.util.Map;

//import java.util.Collections;

public class
ip_opt_null implements IAlgorithm
{
   private boolean initialised = false;

   @Override
   public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters)
   {
      if (!initialised)
      {
         initialised = true;
         /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
         InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);
      }

      double cost = costFunc(netPlan);

      return Double.toString(cost);
   }

   @Override
   public String getDescription()
   {
      return "Empty IP topology optimisation algorithm";
   }

   @Override
   public List<Triple<String, String, String>> getParameters()
   {
      /* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
      return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
   }


   private double costFunc(NetPlan netPlan)
   {
      final NetworkLayer wdmLayer = netPlan.getNetworkLayer(0);
      final NetworkLayer ipLayer = netPlan.getNetworkLayer(1);
      int L0 = netPlan.getNumberOfLinks(wdmLayer);
      int ch = 80;
      double maxTx = netPlan.getVectorLinkCapacity(wdmLayer).getMaxLocation()[0];
      double C1 = L0*ch*maxTx*maxTx;
      double C0 = C1*L0*ch;
      final DoubleMatrix1D t = netPlan.getVectorLinkTotalCarriedTraffic(ipLayer);
      double x = netPlan.getVectorDemandBlockedTraffic(ipLayer).zSum();
      int y = 0;
      for (double q : t.toArray())
      {
         if (q > 1e-6)
         {
            y++;
         }
      }
      double z = t.zDotProduct(t);
      return C0*x + C1*y + z;
   }

}
