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

import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Triple;

import java.util.List;
import java.util.Map;

//import java.util.Collections;

public class makeBiDi implements IAlgorithm
{
 
   @Override
   public String executeAlgorithm(NetPlan netPlan, Map<String, String> algorithmParameters, Map<String, String> net2planParameters)
   {
      /* Initialize all InputParameter objects defined in this object (this uses Java reflection) */
      InputParameter.initializeAllInputParameterFieldsOfObject(this, algorithmParameters);

      List<Link> links = netPlan.getLinks();

      for (Link linkA : links)
      {
         for (Link linkB : links)
         {
            if (linkA.getIndex() >= linkB.getIndex()) continue;
            if (linkA.getOriginNode() != linkB.getDestinationNode()) continue;
            if (linkA.getDestinationNode() != linkB.getOriginNode()) continue;
            //if (linkA.getLengthInKm() != linkB.getLengthInKm()) continue;
            //if (linkA.getCapacity() != linkB.getCapacity()) continue;
            linkA.setAttribute("bidirectionalCouple", Long.toString(linkB.getId()));
            linkB.setAttribute("bidirectionalCouple", Long.toString(linkA.getId()));
         }
      }
 
      return "OK";
   }

   @Override
   public String getDescription()
   {
      return "Find bidirectional pairs and mark them";
   }

   @Override
   public List<Triple<String, String, String>> getParameters()
   {
      /* Returns the parameter information for all the InputParameter objects defined in this object (uses Java reflection) */
      return InputParameter.getInformationAllInputParameterFieldsOfObject(this);
   }

}
