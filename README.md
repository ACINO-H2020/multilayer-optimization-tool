# Application-Centric Multi-layer Network Optimization Tool
The multi-layer network optimization tool provides the ACINO application-aware algorithms for calculating paths in multi-layer, packet/optical, networks. It interacts with the [ACINO multi-layer orchestrator](https://github.com/ACINO-H2020/network-orchestrator) to provision and optimize application-centric connectivity services.

This tool is implemented as a plugin for [Net2Plan](http://www.net2plan.com/), an open-source network planning tool. It allows Net2Plan to interact with the orchestrator, importing its topology and services already provisioned.

![Alt text](/net2plan-gui.png?raw=true " ")

## Installation
The plugin is based on Maven and to generate its artifacts the following command is required:

`$ mvn clean compile assembly:single`

The Net2Plan sources are provided in the file `net2plan-0.4.9.zip`. Then, uncrompress the folder and copy the plugin artifact:

`$ cp target/net2plan-ONOS-jar-with-dependencies.jar net2plan-0.4.9/plugins/.`

In order to execute Net2Plan, enter the `net2plan-0.4.9` folder and execute the following command:

`$ java -jar Net2Plan.jar`

The plugin can be executed by selecting SDN->ONOS planner in the GUI menu.