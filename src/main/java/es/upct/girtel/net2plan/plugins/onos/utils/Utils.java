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

import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapIdentifier;
import es.upct.girtel.net2plan.plugins.onos.model.NetRapMessage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Utils {
    private final static String IP_ADDRESS_ATTRIBUTE_NAME = "ipAddress";
    private final static String SOURCE_INTERFACE_ATTRIBUTE_NAME = "srcIf";
    private final static String DESTINATION_INTERFACE_ATTRIBUTE_NAME = "dstIf";
    private static long reqIDCounter = 0;


    public static void writeToFile(NetPlan netPlan) {
        String sdf = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS").format(new Date());
        netPlan.saveToFile(new File(sdf + ".n2p"));
    }

    public static int byteArrayToInt(byte[] array) {
        switch (array.length) {
            case 1:
                return array[0] & 0x000000FF;

            case 2:
                return (array[0] & 0x0000FF00) | (array[1] & 0x000000FF);

            case 3:
                return (array[0] & 0x00FF0000) | (array[1] & 0x0000FF00) | (array[2] & 0x000000FF);

            case 4:
                return (array[0] & 0xFF000000) | (array[1] & 0x00FF0000) | (array[2] & 0x0000FF00) | (array[3] & 0x000000FF);

            default:
                throw new RuntimeException("Bad");
        }
    }

    public static byte[] intToByteArray(int value, int numBytes) {
        if (numBytes < 1 || numBytes > 4) throw new RuntimeException("Bad");

        byte[] actualByteArray = new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
        byte[] out = new byte[numBytes];
        System.arraycopy(actualByteArray, 4 - numBytes, out, 0, numBytes);

        return out;
    }

    public static String getCurrentMethodName() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    public static long getLinkByDestinationIPAddress(NetPlan netPlan, Inet4Address inet4Address) throws UnknownHostException {
        NetworkLayer defaultLayer = netPlan.getNetworkLayerDefault();
        return getLinkByDestinationIPAddress(netPlan, defaultLayer, inet4Address);
    }

    public static long getLinkByDestinationIPAddress(NetPlan netPlan, NetworkLayer layer, Inet4Address inet4Address) throws UnknownHostException {
        List<Link> links = netPlan.getLinks(layer);
        for (Link link : links)
            if (getLinkDestinationIPAddress(netPlan, link.getId()).equals(inet4Address))
                return link.getId();

        return -1;
    }

    public static long getLinkBySourceInterface(NetPlan netPlan, long nodeId, long interfaceId) {
        return getLinkBySourceInterface(netPlan, netPlan.getNetworkLayerDefault(), nodeId, interfaceId);
    }

    public static long getLinkBySourceInterface(NetPlan netPlan, NetworkLayer layer, long nodeId, long interfaceId) {
        Node node = netPlan.getNodeFromId(nodeId);
        Set<Link> links = node.getOutgoingLinks(layer);
        for (Link link : links)
            if (getLinkSourceInterface(netPlan, link.getId()) == interfaceId)
                return link.getId();
        return -1;
    }

    public static long getLinkBySourceIPAddress(NetPlan netPlan, Inet4Address inet4Address) throws UnknownHostException {
        return getLinkBySourceIPAddress(netPlan, netPlan.getNetworkLayerDefault(), inet4Address);
    }

    public static long getLinkBySourceIPAddress(NetPlan netPlan, NetworkLayer layer, Inet4Address inet4Address) throws UnknownHostException {
        List<Link> links = netPlan.getLinks(layer);
        for (Link link : links)
            if (getLinkSourceIPAddress(netPlan, link.getId()).equals(inet4Address))
                return link.getId();
        return -1;
    }

    public static long getLinkDestinationInterface(NetPlan netPlan, long linkId) {
        Link link = netPlan.getLinkFromId(linkId);
        return Long.parseLong(link.getAttribute(DESTINATION_INTERFACE_ATTRIBUTE_NAME));
    }

    public static Inet4Address getLinkDestinationIPAddress(NetPlan netPlan, long linkId) throws UnknownHostException {
        Link link = netPlan.getLinkFromId(linkId);
        return getNodeIPAddress(netPlan, link.getDestinationNode().getId());
    }

    public static long getLinkSourceInterface(NetPlan netPlan, long linkId) {
        Link link = netPlan.getLinkFromId(linkId);
        return Long.parseLong(link.getAttribute(SOURCE_INTERFACE_ATTRIBUTE_NAME));
    }

    public static Set<Link> getLinksBySourceDestinationIPAddresses(NetPlan netPlan, NetworkLayer layer, Inet4Address sourceIP, Inet4Address destinationIP) {
        Set<Link> nodesSet = new LinkedHashSet<>();
        List<Link> links = netPlan.getLinks(layer);
        for (Link link : links) {
            Node originNode = link.getOriginNode();
            Node destinationNode = link.getDestinationNode();
            if (originNode.getAttribute(Constants.ATTRIBUTE_IP_ADDRESS).equals(sourceIP.getHostAddress()) && destinationNode.getAttribute(Constants.ATTRIBUTE_IP_ADDRESS).equals(destinationIP
                    .getHostAddress()))
                nodesSet.add(link);
        }
        return nodesSet;

    }

    public static Inet4Address getLinkSourceIPAddress(NetPlan netPlan, long linkId) throws UnknownHostException {
        Link link = netPlan.getLinkFromId(linkId);
        return getNodeIPAddress(netPlan, link.getOriginNode().getId());
    }

    public static long getNodeByIPAddress(NetPlan netPlan, Inet4Address inet4Address) {
        try {
            List<Node> nodes = netPlan.getNodes();
            for (Node node : nodes)
                if (getNodeIPAddress(netPlan, node.getId()).equals(inet4Address))
                    return node.getId();

            return -1;
        } catch (Throwable e) {
            return -1;
        }
    }

    public static Inet4Address getNodeIPAddress(NetPlan netPlan, long nodeId) {
        try {
            Node node = netPlan.getNodeFromId(nodeId);
            Inet4Address r = (Inet4Address) Inet4Address.getByName(node.getAttribute(IP_ADDRESS_ATTRIBUTE_NAME));
            return r;
        } catch (Throwable e) {
            return null;
        }

    }

    public static NetRapMessage nrMsg(String message){
        NetRapMessage netRapMessage = new NetRapMessage();
        netRapMessage.setMessage(message);
        return netRapMessage;
    }
    public static NetRapIdentifier nrId(Long value){
        NetRapIdentifier netRapIdentifier = new NetRapIdentifier();
        netRapIdentifier.setIdentifier(value);
        return netRapIdentifier;
    }

    /**
     * Read a PCEP identifier from the given input stream. Due to the way TCP sends packets,
     * we may have a complete identifier, multiple messages, a partial identifier, etc.
     * Hence, read method code needs to identify when a identifier has begun and keep
     * reading until it has found the end of a identifier.
     *
     * @param in Input stream
     * @return Message
     * @since 1.0
     */
    public static byte[] readPCEPMsg(InputStream in) {
        int preambleLength_PCEP = 2;
        int headerLength_PCEP = 4;
        byte[] ret = null;
        byte[] hdr = new byte[headerLength_PCEP];
        byte[] temp = null;
        boolean endHdr = false;
        int r = 0;
        int length = 0;
        boolean endMsg = false;
        int offset = 0;

        while (!endMsg) {
            try {
                r = in.read(endHdr ? temp : hdr, offset, 1);
            } catch (IOException e) {
                throw new RuntimeException("Error reading PCEP data: " + e.getMessage());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (r > 0) {
                if (offset == preambleLength_PCEP) length = ((int) hdr[offset] & 0xFF) << 8;

                if (offset == preambleLength_PCEP + 1) {
                    length = length | (((int) hdr[offset] & 0xFF));
                    temp = new byte[length];
                    endHdr = true;
                    System.arraycopy(hdr, 0, temp, 0, headerLength_PCEP);
                }

                if ((length > 0) && (offset == length - 1)) endMsg = true;

                offset++;
            } else if (r == -1) {
                throw new RuntimeException("End of stream has been reached");
            }
        }

        if (length > 0) {
            ret = new byte[length];
            System.arraycopy(temp, 0, ret, 0, length);
        }

        return ret;
    }

    /**
     * Send a identifier through the given output stream.
     *
     * @param out Output stream
     * @param msg Message to be sent
     * @since 1.0
     */
    public static void writeMessage(OutputStream out, byte[] msg) {
        try {
            out.write(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized static long getNewReqIDCounter() {
        long newReqId;
        if (reqIDCounter == 0) {
            Calendar now = Calendar.getInstance();
            newReqId = now.get(Calendar.SECOND);
            reqIDCounter = newReqId;
        } else {
            newReqId = reqIDCounter >= 0xFFFFFFFDL ? 1 : (reqIDCounter + 1);
            reqIDCounter = newReqId;
        }
        return newReqId;
    }

}