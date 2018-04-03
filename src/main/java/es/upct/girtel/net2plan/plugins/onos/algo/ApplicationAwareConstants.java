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

public class ApplicationAwareConstants
{
    public static String WDM_CLASS_ATTRIBUTE = "wdmClass";
    public static String LATENCY_MS_ATTRIBUTE = "maxLatencyInMs";
    public static String AVAILABILITY_LEVEL_ATTRIBUTE = "minAvailability";
    public static String PROTECTION_ATTRIBUTE = "Protection";
    
    
    /*we want to, if the user selects this option, ignore certain service requirements.
     * In that case, we want to store the demand's real requirements so we can check if the computed
     * routes satisfy them, i.e. check for SLA violations.*/
    public static String GENERATED_LATENCY_MS_ATTRIBUTE = "generated_maxLatencyInMs";
    public static String GENERATED_AVAILABILITY_LEVEL_ATTRIBUTE = "generated_minAvailability";
}
