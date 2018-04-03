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

import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

public class NetRapAction {


    private String action = null;
    private NetRapDemand demand = null;

    /**
     * Enumerator for the type of action, NEW, ROUTE, MOVE, UPDATE, or FAIL
     **/
    public NetRapAction action(String action) {
        this.action = action;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Enumerator for the type of action, NEW, ROUTE, MOVE, UPDATE, or FAIL")
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


    /**
     **/
    public NetRapAction demand(NetRapDemand demand) {
        this.demand = demand;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "")
    public NetRapDemand getDemand() {
        return demand;
    }

    public void setDemand(NetRapDemand demand) {
        this.demand = demand;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetRapAction netRapAction = (NetRapAction) o;
        return Objects.equals(action, netRapAction.action) &&
                Objects.equals(demand, netRapAction.demand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, demand);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapAction {\n");

        sb.append("    action: ").append(toIndentedString(action)).append("\n");
        sb.append("    demand: ").append(toIndentedString(demand)).append("\n");
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
