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
 * Error message
 **/
@ApiModel(description = "Error message")

public class NetRapMessage   {
  
  private String message = null;

  /**
   * Message string, typically error
   **/
  public NetRapMessage message(String message) {
    this.message = message;
    return this;
  }

  
  @ApiModelProperty(example = "null", required = true, value = "Message string, typically error")
  public String getMessage() {
    return message;
  }
  public void setMessage(String message) {
    this.message = message;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NetRapMessage netRapMessage = (NetRapMessage) o;
    return Objects.equals(message, netRapMessage.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class NetRapMessage {\n");
    
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
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
