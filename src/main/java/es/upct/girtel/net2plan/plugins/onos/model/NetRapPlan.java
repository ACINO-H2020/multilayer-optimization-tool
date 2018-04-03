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
import java.util.List;
import java.util.Objects;

/**
 * Represents a plan containing topology, demands, routes, and the algorithm used to calculate it.
 **/

@ApiModel(description = "Represents a plan containing topology, demands, routes, and the algorithm used to calculate it.")

public class NetRapPlan {

    private List<NetRapDemand> demands = new ArrayList<NetRapDemand>();
  private Long identifier = null;
  private Object routes = null;
    private String state = null;
    private NetRapTopology topology = null;

    /**
     * Demands used in the planning
     **/
    public NetRapPlan demands(List<NetRapDemand> demands) {
        this.demands = demands;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Demands used in the planning")
    public List<NetRapDemand> getDemands() {
        return demands;
    }

    public void setDemands(List<NetRapDemand> demands) {
        this.demands = demands;
    }

    /**
     * Planning id, assigned by Net2Plan
     **/
  public NetRapPlan identifier(Long identifier) {
    this.identifier = identifier;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Planning id, assigned by Net2Plan")
  public Long getIdentifier() {
    return identifier;
    }
  public void setIdentifier(Long identifier) {
    this.identifier = identifier;
    }

    /**
   * Planned route in the topology
     **/
  public NetRapPlan routes(Object routes) {
        this.routes = routes;
        return this;
    }


  @ApiModelProperty(example = "null", value = "Planned route in the topology")
  public Object getRoutes() {
        return routes;
    }
  public void setRoutes(Object routes) {
        this.routes = routes;
    }

    /**
     * Status of the plan WAITING - calculation not started, RUNNING - calculation running,  FAILED - calculation failed,  COMPLETED - calculation complete
     **/
    public NetRapPlan state(String state) {
        this.state = state;
        return this;
    }


    @ApiModelProperty(example = "null", required = true, value = "Status of the plan WAITING - calculation not started, RUNNING - calculation running,  FAILED - calculation failed,  COMPLETED - calculation complete")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /**
     * Topology used in the planning
     **/
    public NetRapPlan topology(NetRapTopology topology) {
        this.topology = topology;
        return this;
    }


    @ApiModelProperty(example = "null", value = "Topology used in the planning")
    public NetRapTopology getTopology() {
        return topology;
    }

    public void setTopology(NetRapTopology topology) {
        this.topology = topology;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetRapPlan netRapPlan = (NetRapPlan) o;
        return Objects.equals(demands, netRapPlan.demands) &&
        Objects.equals(identifier, netRapPlan.identifier) &&
                Objects.equals(routes, netRapPlan.routes) &&
                Objects.equals(state, netRapPlan.state) &&
                Objects.equals(topology, netRapPlan.topology);
    }

    @Override
    public int hashCode() {
    return Objects.hash(demands, identifier, routes, state, topology);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NetRapPlan {\n");

        sb.append("    demands: ").append(toIndentedString(demands)).append("\n");
    sb.append("    identifier: ").append(toIndentedString(identifier)).append("\n");
        sb.append("    routes: ").append(toIndentedString(routes)).append("\n");
        sb.append("    state: ").append(toIndentedString(state)).append("\n");
        sb.append("    topology: ").append(toIndentedString(topology)).append("\n");
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
