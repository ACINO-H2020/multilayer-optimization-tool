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

import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.utils.Pair;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by ponsko on 2017-01-31.
 */
public class RouteList {
    private static RouteList instance = null;
    LinkedList<Pair<String,Route>> routes;

    protected RouteList() {
        routes = new LinkedList<>();
    }
    public static RouteList getInstance() {
        if(instance == null) {
            instance = new RouteList();
        }
        return instance;
    }
    public void addRoute(Pair<String, Route> action){
        routes.push(action);
    }
    public void clearRoutes(){
        routes.clear();
    }
    public List<Pair<String,Route>> getRoutes(){
        return routes;
    }
    public Pair<String,Route> popAction(){
        return routes.pop();
    }
    public int numActions(){
        return routes.size();
    }
}
