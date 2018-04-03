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

package es.upct.girtel.net2plan.plugins.onos.utils;

public class Constants {
    public final static boolean DEBUG = true; //In true, some trace prints will occur in the PCE screen
    public final static int PCEP_SERVER_PORT = 4189;
    public final static int PCEP_MAX_LENGTH = 65535; // Length field has 16 bits, indicates total length including Header, expressed in bytes.
    public final static int BGP_SERVER_PORT = 179;
    public final static int BGP_MAX_LENGTH = 65535; // Length field has 16 bits, indicates total length including Header, expressed in bytes.
    public final static int W = 80; //Wavelengths per fiber
    public final static int LIGHTPATH_BINARY_RATE_GBPS = 40; //Binary rate per lightpath in Gbs
    public final static int WDM_LAYER_INDEX = 0;
    public final static int IP_LAYER_INDEX = 1;
    public final static String WDM_LAYER_NAME = "WDM";
    public final static String IP_LAYER_NAME = "IP";

    public final static String ATTRIBUTE_IP_ADDRESS = "ipAddress";
    public final static String ATTRIBUTE_NODE_TYPE = "type";
    public final static String ATTRIBUTE_REQUEST_ID = "requestId";
    public final static String ATTRIBUTE_SOURCE_INTERFACE = "srcIf";
    public final static String ATTRIBUTE_DESTINATION_INTERFACE = "dstIf";
    public final static String ATTRIBUTE_LSP_ID = "lspId";
    public final static String ATTRIBUTE_MAX_BIFURCATION = "allowedBifurcationDegree";
    public final static String ATTRIBUTE_MIN_BANDWIDTH = "minBandwidthPerPathInGbps";

    public final static String NODE_TYPE_IPROUTER = "ipRouter";
    public final static String NODE_TYPE_ROADM = "roadm";
}
