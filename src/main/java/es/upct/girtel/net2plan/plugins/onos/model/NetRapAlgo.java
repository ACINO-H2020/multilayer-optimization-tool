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

import java.util.Objects;

/**
 * Name and description of an algorithm
 **/

@ApiModel(description = "Name and description of an algorithm")

public class NetRapAlgo {

    private String description = null;
    private String name = null;

    /**
     * Description of the algorithm
     **/
    public NetRapAlgo description(String description) {
        this.description = description;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Description of the algorithm")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Name of the algorithm
     **/
    public NetRapAlgo name(String name) {
        this.name = name;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Name of the algorithm")
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
        NetRapAlgo netRapAlgo = (NetRapAlgo) o;
        return Objects.equals(description, netRapAlgo.description) &&
                Objects.equals(name, netRapAlgo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, name);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapAlgo {\n");

        sb.append("    description: ").append(toIndentedString(description)).append("\n");
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
