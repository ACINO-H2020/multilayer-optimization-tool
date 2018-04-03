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

package es.upct.girtel.net2plan.plugins.onos.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Object representing an assigned route
 **/

@ApiModel(description = "Object representing an assigned route")

public class NetRapRoute {

    private List<NetRapLink> links = new ArrayList<NetRapLink>();
    private List<NetRapNode> nodes = new ArrayList<NetRapNode>();
    private String demandId = null;
    private Integer layer = null;
    private Double occupiedCapacity = null;
    private HashMap attributes = null;

    /**
   * Links used by the route
     **/
    public NetRapRoute links(List<NetRapLink> links) {
        this.links = links;
        return this;
    }


  @ApiModelProperty(example = "null", required = true, value = "Links used by the route")
    public List<NetRapLink> getLinks() {
        return links;
    }

    public void setLinks(List<NetRapLink> links) {
        this.links = links;
    }

    /**
     * Nodes being passed by the route
     **/
    public NetRapRoute nodes(List<NetRapNode> nodes) {
        this.nodes = nodes;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Nodes being passed by the route")
    public List<NetRapNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<NetRapNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * Associated demand
     **/
    public NetRapRoute demandId(String demandId) {
        this.demandId = demandId;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Associated demand")
    public String getDemandId() {
        return demandId;
    }

    public void setDemandId(String demandId) {
        this.demandId = demandId;
    }

  /**
   * Layer in the network the route refers to
   **/
  public NetRapRoute layer(Integer layer) {
    this.layer = layer;
    return this;
  }

  
  @ApiModelProperty(example = "null", value = "Layer in the network the route refers to")
  public Integer getLayer() {
    return layer;
  }
  public void setLayer(Integer layer) {
    this.layer = layer;
  }

  /**
   * Occupied capacity in link units (e.g. 2 wdm slots)
   **/
  public NetRapRoute occupiedCapacity(Double occupiedCapacity) {
    this.occupiedCapacity = occupiedCapacity;
    return this;
  }

  
  @ApiModelProperty(example = "null", value = "Occupied capacity in link units (e.g. 2 wdm slots)")
  public Double getOccupiedCapacity() {
    return occupiedCapacity;
  }
  public void setOccupiedCapacity(Double occupiedCapacity) {
    this.occupiedCapacity = occupiedCapacity;
  }

  /**
   * Optional map of attributes, for ACINO optical layer routes should have (seqFrequencySlotsInitialRoute, seqFrequencySlots_se)
   **/
  public NetRapRoute attributes(HashMap attributes) {
    this.attributes = attributes;
    return this;
  }

  
  @ApiModelProperty(example = "null", value = "Optional map of attributes, for ACINO optical layer routes should have (seqFrequencySlotsInitialRoute, seqFrequencySlots_se)")
  public HashMap getAttributes() {
    return attributes;
  }
  public void setAttributes(HashMap attributes) {
    this.attributes = attributes;
  }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetRapRoute netRapRoute = (NetRapRoute) o;
        return Objects.equals(links, netRapRoute.links) &&
                Objects.equals(nodes, netRapRoute.nodes) &&
        Objects.equals(demandId, netRapRoute.demandId) &&
        Objects.equals(layer, netRapRoute.layer) &&
        Objects.equals(occupiedCapacity, netRapRoute.occupiedCapacity) &&
        Objects.equals(attributes, netRapRoute.attributes);
    }

    @Override
    public int hashCode() {
    return Objects.hash(links, nodes, demandId, layer, occupiedCapacity, attributes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapRoute {\n");

        sb.append("    links: ").append(toIndentedString(links)).append("\n");
        sb.append("    nodes: ").append(toIndentedString(nodes)).append("\n");
        sb.append("    demandId: ").append(toIndentedString(demandId)).append("\n");
        sb.append("    layer: ").append(toIndentedString(layer)).append("\n");
        sb.append("    occupiedCapacity: ").append(toIndentedString(occupiedCapacity)).append("\n");
        sb.append("    attributes: ").append(toIndentedString(attributes)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
