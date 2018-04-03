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
/**
 * Net2Plan - ONOS REST Interface
 * Interface description for Net2Plan to ONOS integration. This is the API presented by Net2Plan to ONOS, a separate description exists for the other direction.
 * <p>
 * OpenAPI spec version: 0.1.3
 * Contact: ponsko@acreo.se
 * <p>
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.HashMap;
import java.util.Objects;

/**
 * Description of a link
 **/

@ApiModel(description = "Description of a link")

public class NetRapLink {

    private Double MTBF = null;
    private Double MTTR = null;
    private HashMap attributes = null;
    private Double capacity = null;
    private Double occupiedCapacity = null;
    private String dst = null;
    private String identifier = null;
    private Integer layer = null;
    private Double lengthInKm = null;
    private Integer propagationSpeed = null;
    private String src = null;
    private String srg = null;
    private Boolean active = true;


    /**
     * Link up/down
     **/
    public NetRapLink active(Boolean active) {
        this.active = active;
        return this;
    }


    @ApiModelProperty(example = "true", value = "Link up/down")
    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }


    /**
     * Mean Time Between Failures in Hours
     **/
    public NetRapLink MTBF(Double MTBF) {
        this.MTBF = MTBF;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Mean Time Between Failures in Hours")
    public Double getMTBF() {
        return MTBF;
    }

    public void setMTBF(Double MTBF) {
        this.MTBF = MTBF;
    }

    /**
     * Mean Time To Repair in Hours
     **/
    public NetRapLink MTTR(Double MTTR) {
        this.MTTR = MTTR;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Mean Time To Repair in Hours")
    public Double getMTTR() {
        return MTTR;
    }

    public void setMTTR(Double MTTR) {
        this.MTTR = MTTR;
    }

    /**
     * Optional map of attributes, for ACINO two are required (srcPort, dstPort, both int)
     **/
    public NetRapLink attributes(HashMap attributes) {
        this.attributes = attributes;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Optional map of attributes, for ACINO two are required (srcPort, dstPort, both int)")
    public HashMap getAttributes() {
        return attributes;
    }

    public void setAttributes(HashMap attributes) {
        this.attributes = attributes;
    }

    /**
     * Capacity of the link If layer = 0, this is the amount of wavelength slots If layer=1, this is the Gbps of the link
     **/
    public NetRapLink capacity(Double capacity) {
        this.capacity = capacity;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Capacity of the link If layer = 0, this is the amount of wavelength slots If layer=1, this is the Gbps of the link")
    public Double getCapacity() {
        return capacity;
    }

    public void setCapacity(Double capacity) {
        this.capacity = capacity;
    }

    /**
     * Capacity of the link If layer = 0, this is the amount of wavelength slots If layer=1, this is the Gbps of the link
     **/
    public NetRapLink occupiedCapacity(Double occupiedCapacity) {
        this.occupiedCapacity = occupiedCapacity;
        return this;
    }


    @ApiModelProperty(example = "null", required = false, value = "Capacity of the link If layer = 0, this is the amount of wavelength slots If layer=1, this is the Gbps of the link")
    public Double getoccupiedCapacity() {
        return capacity;
    }

    public void setoOccupiedCapacity(Double occupiedCapacity) {
        this.occupiedCapacity = occupiedCapacity;
    }


    /**
     * Destination of the link. Should contain the ID of an existing node.
     **/
    public NetRapLink dst(String dst) {
        this.dst = dst;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Destination of the link. Should contain the ID of an existing node.")
    public String getDst() {
        return dst;
    }

    public void setDst(String dst) {
        this.dst = dst;
    }

    /**
     * Unique identifier, assigned by net2plan
     **/
    public NetRapLink identifier(String identifier) {
        this.identifier = identifier;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Unique identifier, assigned by net2plan")
    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * Which layer the node should be placed in 0 = Optical layer 1 = IP layer
     **/
    public NetRapLink layer(Integer layer) {
        this.layer = layer;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Which layer the node should be placed in 0 = Optical layer 1 = IP layer")
    public Integer getLayer() {
        return layer;
    }

    public void setLayer(Integer layer) {
        this.layer = layer;
    }

    /**
     * Length of the link in kilometers
     **/
    public NetRapLink lengthInKm(Double lengthInKm) {
        this.lengthInKm = lengthInKm;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Length of the link in kilometers")
    public Double getLengthInKm() {
        return lengthInKm;
    }

    public void setLengthInKm(Double lengthInKm) {
        this.lengthInKm = lengthInKm;
    }

    /**
     * Signal propagation speed on this link Given in kilometers per second
     **/
    public NetRapLink propagationSpeed(Integer propagationSpeed) {
        this.propagationSpeed = propagationSpeed;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Signal propagation speed on this link Given in kilometers per second")
    public Integer getPropagationSpeed() {
        return propagationSpeed;
    }

    public void setPropagationSpeed(Integer propagationSpeed) {
        this.propagationSpeed = propagationSpeed;
    }

    /**
     * Source of the link. Should contain the ID of an existing node.
     **/
    public NetRapLink src(String src) {
        this.src = src;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Source of the link. Should contain the ID of an existing node.")
    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    /**
     * Shared risk group name
     **/
    public NetRapLink srg(String srg) {
        this.srg = srg;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Shared risk group name")
    public String getSrg() {
        return srg;
    }

    public void setSrg(String srg) {
        this.srg = srg;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetRapLink netRapLink = (NetRapLink) o;
        return Objects.equals(active, netRapLink.active) &&
                Objects.equals(MTBF, netRapLink.MTBF) &&
                Objects.equals(MTTR, netRapLink.MTTR) &&
                Objects.equals(attributes, netRapLink.attributes) &&
                Objects.equals(capacity, netRapLink.capacity) &&
                Objects.equals(occupiedCapacity, netRapLink.occupiedCapacity) &&
                Objects.equals(dst, netRapLink.dst) &&
                Objects.equals(identifier, netRapLink.identifier) &&
                Objects.equals(layer, netRapLink.layer) &&
                Objects.equals(lengthInKm, netRapLink.lengthInKm) &&
                Objects.equals(propagationSpeed, netRapLink.propagationSpeed) &&
                Objects.equals(src, netRapLink.src) &&
                Objects.equals(srg, netRapLink.srg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(active, MTBF, MTTR, attributes, capacity, occupiedCapacity, dst, identifier, layer, lengthInKm, propagationSpeed, src, srg);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapLink {\n");
        sb.append("    active: ").append(toIndentedString(active)).append("\n");
        sb.append("    MTBF: ").append(toIndentedString(MTBF)).append("\n");
        sb.append("    MTTR: ").append(toIndentedString(MTTR)).append("\n");
        sb.append("    attributes: ").append(toIndentedString(attributes)).append("\n");
        sb.append("    capacity: ").append(toIndentedString(capacity)).append("\n");
        sb.append("    occupiedCapacity: ").append(toIndentedString(occupiedCapacity)).append("\n");
        sb.append("    dst: ").append(toIndentedString(dst)).append("\n");
        sb.append("    identifier: ").append(toIndentedString(identifier)).append("\n");
        sb.append("    layer: ").append(toIndentedString(layer)).append("\n");
        sb.append("    lengthInKm: ").append(toIndentedString(lengthInKm)).append("\n");
        sb.append("    propagationSpeed: ").append(toIndentedString(propagationSpeed)).append("\n");
        sb.append("    src: ").append(toIndentedString(src)).append("\n");
        sb.append("    srg: ").append(toIndentedString(srg)).append("\n");
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

