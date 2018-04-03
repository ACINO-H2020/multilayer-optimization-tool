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

import java.util.HashMap;
import java.util.Objects;

/**
 * Shared risk group representation
 **/

@ApiModel(description = "Shared risk group representation")

public class NetRapSRG {

    private Double MTBF = null;
    private Double MTTR = null;
    private HashMap attributes = null;
    private String name = null;

    /**
     * Mean Time Between Failures in Hours
     **/
    public NetRapSRG MTBF(Double MTBF) {
        this.MTBF = MTBF;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Mean Time Between Failures in Hours")
    public Double getMTBF() {
        return MTBF;
    }

    public void setMTBF(Double MTBF) {
        this.MTBF = MTBF;
    }

    /**
     * Mean Time To Repair in Hours
     **/
    public NetRapSRG MTTR(Double MTTR) {
        this.MTTR = MTTR;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Mean Time To Repair in Hours")
    public Double getMTTR() {
        return MTTR;
    }

    public void setMTTR(Double MTTR) {
        this.MTTR = MTTR;
    }

    /**
     * Optional map of attributes
     **/
    public NetRapSRG attributes(HashMap attributes) {
        this.attributes = attributes;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Optional map of attributes")
    public HashMap getAttributes() {
        return attributes;
    }

    public void setAttributes(HashMap attributes) {
        this.attributes = attributes;
    }

    /**
     * Name of the shared risk group
     **/
    public NetRapSRG name(String name) {
        this.name = name;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Name of the shared risk group")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetRapSRG netRapSRG = (NetRapSRG) o;
        return Objects.equals(MTBF, netRapSRG.MTBF) &&
                Objects.equals(MTTR, netRapSRG.MTTR) &&
                Objects.equals(attributes, netRapSRG.attributes) &&
                Objects.equals(name, netRapSRG.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(MTBF, MTTR, attributes, name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapSRG {\n");

        sb.append("    MTBF: ").append(toIndentedString(MTBF)).append("\n");
        sb.append("    MTTR: ").append(toIndentedString(MTTR)).append("\n");
        sb.append("    attributes: ").append(toIndentedString(attributes)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
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
