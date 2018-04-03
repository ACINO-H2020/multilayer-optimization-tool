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

package es.upct.girtel.net2plan.plugins.onos;


import com.net2plan.gui.GUINet2Plan;
import com.net2plan.gui.tools.IGUINetworkViewer;
import com.net2plan.gui.utils.AdvancedJTable;
import com.net2plan.gui.utils.ButtonColumn;
import com.net2plan.gui.utils.CellRenderers;
import com.net2plan.gui.utils.ClassAwareTableModel;
import com.net2plan.gui.utils.ParameterValueDescriptionPanel;
import com.net2plan.gui.utils.RunnableSelector;
import com.net2plan.gui.utils.StringLabeller;
import com.net2plan.gui.utils.SwingUtils;
import com.net2plan.gui.utils.ThreadExecutionController;
import com.net2plan.gui.utils.WiderJComboBox;
import com.net2plan.interfaces.networkDesign.Configuration;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.IAlgorithm;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.internal.Constants;
import com.net2plan.internal.ErrorHandling;
import com.net2plan.internal.SystemUtils;
import com.net2plan.internal.plugins.IGUIModule;
import com.net2plan.internal.plugins.PluginSystem;
import com.net2plan.libraries.WDMUtils;
import com.net2plan.utils.Pair;
import com.net2plan.utils.StringUtils;
import com.net2plan.utils.Triple;
import com.wpl.xrapc.XrapDeleteReply;
import com.wpl.xrapc.XrapErrorReply;
import com.wpl.xrapc.XrapGetReply;
import com.wpl.xrapc.XrapPeer;
import com.wpl.xrapc.XrapPostReply;
import com.wpl.xrapc.XrapPutReply;
import com.wpl.xrapc.XrapReply;
import es.upct.girtel.net2plan.plugins.onos.algo.CACSim_AA_IPoverWDM_ApplicationAware_ReOptimization_Option1b;
import es.upct.girtel.net2plan.plugins.onos.utils.RouteList;
import es.upct.girtel.net2plan.plugins.onos.utils.Stopwatch;
import es.upct.girtel.net2plan.plugins.onos.utils.Utils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static es.upct.girtel.net2plan.plugins.onos.utils.Constants.ATTRIBUTE_NODE_TYPE;



public class ONOSPlugin extends IGUINetworkViewer implements ActionListener, ItemListener {
    private final static String NEW_LINE = StringUtils.getLineSeparator();
    private final static String TITLE = "ONOS planner";
    private WebTest webServer;
    private boolean DYNAMIC_LAYOUT = false;
    private JPanel executionPane;
    private RunnableSelector algorithmSelector;
    private ThreadExecutionController algorithmController;

    /* GUI Stuff */
    private JTextArea guilog;
    private JButton _clearButton;
    private JTextField _txt_ip, _txt_port, _txt_timeout, _txt_bandwidth, _txt_maxBifurcation, _txt_minBandwidthPerPath;
    private JTextField _txt_resource, _txt_body;
    private JComboBox _sourceNode, _destinationNode, _fiberFailureSelector;
    private JButton _buttonGotoServices, _buttonMakePCERequest, _buttonConnectToONOS, _buttonDisconnectFromONOS, _buttonFailFiber, _buttonViewFiber;
    private JButton _buttonXrapGet, _buttonXrapPost, _buttonXrapPut, _buttonXrapDelete;
    private JButton _buttonTestRoute;

    private JCheckBox _loadBalancingEnabled;
    private JPanel _loadBalancingPanel;


    private Socket _pcepSocket, _bgplsSocket;
    private Map<Long, Set<Long>> _routeOriginalLinks;
    private Logger logger;
    public XrapApi xrapApi;


    Map<String, String> cac_algorithmParameters = new HashMap<>();
    final Map<String, String> cac_simulationParameters = new HashMap<>();
    private NetworkLayer _wdmLayer, _ipLayer;
    private long start;
    CACSim_AA_IPoverWDM_ApplicationAware_ReOptimization_Option1b cacmod;

    private void logMsg(String message){
        updateOperationLog(message);
        logger.info(message);
    }
    public void updateCacsim(){
        Stopwatch timer = Stopwatch.createStarted();
        cac_algorithmParameters.put("K_ipLayer", "50");
        cac_algorithmParameters.put("K_wdmLayer", "5");
        cac_algorithmParameters.put("TRxAvailableBitrates", "10");
        cac_algorithmParameters.put("TRxReach", "3000");
        cac_algorithmParameters.put("TRxSlots", "1");
        cac_algorithmParameters.put("oppAlgorithm", "pathSelectionPolicy");
        cac_algorithmParameters.put("flexgridPolicy", "maxBW");
        cac_algorithmParameters.put("wdmShortestPathType", "km");
        cac_algorithmParameters.put("pathSelectionPolicy", "minLat");
        cac_algorithmParameters.put("IPOPT_run", "-1");
        cac_algorithmParameters.put("removeEmptyIPLinks", "false");
        cac_algorithmParameters.put("removeFrequency", "0");
        cac_algorithmParameters.put("IPOPT", "ip_opt_null");
        cac_algorithmParameters.put("routerDelay", "0.0");

        cacmod.initialize(getDesign(),cac_algorithmParameters,cac_simulationParameters,Configuration.getNet2PlanOptions());
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public void cacsimNewDemand(Demand dmd){
        Stopwatch timer = Stopwatch.createStarted();
        SimEvent se = new SimEvent(0.0, null, 0, dmd);
        cacmod.processEvent(getDesign(),se);
        logMsg("actions to take: " + RouteList.getInstance().getRoutes());
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public void reoptimize(){
        Stopwatch timer = Stopwatch.createStarted();
        logMsg("ONOSPlugin reoptimize called");
        cacmod.reoptimize(getDesign());
        logMsg("actions to take: " + RouteList.getInstance().getRoutes());
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public void cacsimUpdateDemand(Demand dmd){
        Stopwatch timer = Stopwatch.createStarted();
        logMsg("cacsimUpdateDemand called");
        SimEvent.DemandModify dmdm = new SimEvent.DemandModify(dmd,dmd.getOfferedTraffic(),false);
        SimEvent se = new SimEvent(0.0, null, 0, dmdm);
        cacmod.processEvent(getDesign(),se);
        logMsg("actions to take: " + RouteList.getInstance().getRoutes());
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public void cacsimDeleteDemand(Demand dmd){
        Stopwatch timer = Stopwatch.createStarted();
        logMsg("cacsimDeleteDemand called");
        SimEvent.DemandRemove dmdr = new SimEvent.DemandRemove(dmd);
        SimEvent se = new SimEvent(0.0, null, 0, dmdr);
        cacmod.processEvent(getDesign(),se);
        logMsg("actions to take: " + RouteList.getInstance().getRoutes());
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public ONOSPlugin() {
        super(TITLE.toUpperCase(Locale.getDefault()));
        logger = LoggerFactory.getLogger(XrapPeer.class.getName());
        algorithmController = new ThreadExecutionController(this);
        cacmod = new CACSim_AA_IPoverWDM_ApplicationAware_ReOptimization_Option1b();
    }

    public static void main(String[] args) {
        GUINet2Plan.main(args);
        PluginSystem.addPlugin(IGUIModule.class, ONOSPlugin.class);
        PluginSystem.loadExternalPlugins();
        GUINet2Plan.refreshMenu();
    }

    @Override
    protected boolean allowLoadTrafficDemands() {
        return true;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == _clearButton) {
            guilog.setText("");
        } else if (src == _buttonGotoServices) {
            selectNetPlanViewItem(Constants.NetworkElementType.DEMAND, null);
        } else if (src == _buttonMakePCERequest) {
            //performPCERequest();
        } else if (src == _buttonConnectToONOS) {
            connect();
        } else if (src == _buttonDisconnectFromONOS) {
            shutdown();
        } else if (src == _buttonXrapDelete) {
            String resource = _txt_resource.getText();
            XrapReply rep = xrapApi.delete(resource);

            if (rep == null) {
                logMsg("Error deleteing " + resource);
            } else if (rep instanceof XrapDeleteReply) {
                logMsg(rep.toString());
            } else if (rep instanceof XrapErrorReply) {
                logMsg(rep.toString());
            } else {
                logMsg("Couldn't decide what type of reply:");
                logMsg(rep.toString());
            }
        } else if (src == _buttonXrapGet) {
            String resource = _txt_resource.getText();
            String body = _txt_body.getText();

            XrapReply rep = xrapApi.get(resource, body);
            if (rep == null) {
                logMsg("Error getting " + resource);
            } else if (rep instanceof XrapGetReply) {
                logMsg(rep.toString());
            } else if (rep instanceof XrapErrorReply) {
                logMsg(rep.toString());
            } else {
                logMsg("Couldn't decide what type of reply:");
                logMsg(rep.toString());
            }


        } else if (src == _buttonXrapPost) {
            String resource = _txt_resource.getText();
            String body = _txt_body.getText();
            XrapReply rep = xrapApi.post(resource, body);

            if (rep == null) {
                logMsg("Error posting " + resource);
            } else if (rep instanceof XrapPostReply) {
                logMsg(rep.toString());
            } else if (rep instanceof XrapErrorReply) {
                logMsg(rep.toString());
            } else {
                logMsg("Couldn't decide what type of reply:");
                logMsg(rep.toString());
            }
        } else if (src == _buttonXrapPut) {
            String resource = _txt_resource.getText();
            String body = _txt_body.getText();

            XrapReply rep = xrapApi.put(resource, body);
            if (rep == null) {
                logMsg("Error putting " + resource);
            } else if (rep instanceof XrapPutReply) {
                logMsg(rep.toString());
            } else if (rep instanceof XrapErrorReply) {
                logMsg(rep.toString());
            } else {
                logMsg("Couldn't decide what type of reply:");
                logMsg(rep.toString());
            }
        } else if (src == _buttonTestRoute) {
            runAlgorithm();
        }
    }

    @Override
    public void configure(JPanel contentPane) {
        _routeOriginalLinks = new HashMap<>();

        _txt_ip = new JTextField(getCurrentOptions().get("onos.defaultIP"));
        _txt_port = new JTextField(getCurrentOptions().get("onos.defaultPort"));
        _txt_timeout = new JTextField(getCurrentOptions().get("onos.defaultTimeout"));
        _txt_resource = new JTextField("/resource");
        _txt_body = new JTextField("{}");
        _sourceNode = new WiderJComboBox();
        _destinationNode = new WiderJComboBox();
        _txt_bandwidth = new JTextField();
        _loadBalancingEnabled = new JCheckBox("Load-balancing");
        _loadBalancingEnabled.addItemListener(this);
        _loadBalancingPanel = new JPanel(new MigLayout("", "[][grow]", "[][]"));
        _buttonGotoServices = new JButton("Go to existing services");
        _txt_maxBifurcation = new JTextField();
        _txt_minBandwidthPerPath = new JTextField();
        _buttonMakePCERequest = new JButton("Make request");
        _buttonConnectToONOS = new JButton("Connect");
        _buttonConnectToONOS.setBackground(Color.RED);
        _buttonConnectToONOS.setContentAreaFilled(false);
        _buttonConnectToONOS.setOpaque(true);
        _buttonDisconnectFromONOS = new JButton("Disconnect");
        _buttonDisconnectFromONOS.setEnabled(false);
        _buttonFailFiber = new JButton("Simulate failure");
        _buttonViewFiber = new JButton("View fiber");
        _buttonViewFiber.setEnabled(false);


        _buttonXrapGet = new JButton("GET");
        _buttonXrapPost = new JButton("POST");
        _buttonXrapPut = new JButton("PUT");
        _buttonXrapDelete = new JButton("DEL");

        _buttonTestRoute = new JButton("ROUTE");

        super.configure(contentPane);

        JPanel controllerTab = new JPanel();
        controllerTab.setLayout(new MigLayout("fill, insets 0 0 0 0", "[grow]", "[][][grow]"));
        controllerTab.setBorder(new LineBorder(Color.BLACK));

        JPanel whatifTab = new JPanel();
        whatifTab.setLayout(new MigLayout("fill, insets 0 0 0 0", "[grow]", "[][][grow]"));
        whatifTab.setBorder(new LineBorder(Color.BLACK));

        JPanel connectionHandler = new JPanel(new MigLayout("insets 0 0 0 0", "[][grow]", "[][]"));
        connectionHandler.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "ONOS connection controller"));
        connectionHandler.add(new JLabel("ONOS IP"));
        connectionHandler.add(_txt_ip, "growx, wrap");
        connectionHandler.add(new JLabel("ONOS Port"));
        connectionHandler.add(_txt_port, "growx, wrap");
        connectionHandler.add(new JLabel("ONOS Op Timeout"));
        connectionHandler.add(_txt_timeout, "growx, wrap");
        JPanel connectionHandlerButton = new JPanel(new FlowLayout());
        connectionHandlerButton.add(_buttonConnectToONOS);
        connectionHandlerButton.add(_buttonDisconnectFromONOS);
        connectionHandler.add(connectionHandlerButton, "center, spanx 2, wrap");
        _buttonConnectToONOS.addActionListener(this);
        _buttonDisconnectFromONOS.addActionListener(this);

        JPanel xrapTab = new JPanel();
        xrapTab.setLayout(new MigLayout("fill, insets 0 0 0 0", "[grow]", "[][][grow]"));
        xrapTab.setBorder(new LineBorder(Color.BLACK));
        JPanel xrapHandler = new JPanel(new MigLayout("insets 0 0 0 0", "[][grow]", "[][]"));
        xrapHandler.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "ONOS connection controller"));
        xrapHandler.add(new JLabel("XRAP Resource"));
        xrapHandler.add(_txt_resource, "growx, wrap");
        xrapHandler.add(new JLabel("XRAP Body"));
        xrapHandler.add(_txt_body, "growx, wrap");
        JPanel xrapHandlerButton = new JPanel(new FlowLayout());
        xrapHandlerButton.add(_buttonXrapGet);
        xrapHandlerButton.add(_buttonXrapPost);
        xrapHandlerButton.add(_buttonXrapPut);
        xrapHandlerButton.add(_buttonXrapDelete);

        xrapHandlerButton.add(_buttonTestRoute);


        xrapHandler.add(xrapHandlerButton, "center, spanx 2, wrap");
        _buttonXrapGet.addActionListener(this);
        _buttonXrapPost.addActionListener(this);
        _buttonXrapPut.addActionListener(this);
        _buttonXrapDelete.addActionListener(this);

        _buttonTestRoute.addActionListener(this);


        JPanel serviceProvisioning = new JPanel();
        serviceProvisioning.setLayout(new MigLayout("insets 0 0 0 0", "[][grow]", "[][][][][grow]"));
        serviceProvisioning.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "Service provisioning"));
        serviceProvisioning.add(new JLabel("Source node"));
        serviceProvisioning.add(_sourceNode, "growx, wrap");
        serviceProvisioning.add(new JLabel("Destination node"));
        serviceProvisioning.add(_destinationNode, "growx, wrap");
        serviceProvisioning.add(new JLabel("Requested bandwidth"));
        serviceProvisioning.add(_txt_bandwidth, "growx, wrap");
        serviceProvisioning.add(new JLabel("Additional constraints"), "top");

        JPanel additionalConstraints = new JPanel(new MigLayout("fill, insets 0 0 0 0", "[grow]", "[][]"));

        _loadBalancingPanel.add(new JLabel("Maximum number of paths"));
        _loadBalancingPanel.add(_txt_maxBifurcation, "growx, wrap");
        _loadBalancingPanel.add(new JLabel("Minimum bandwidth per path"));
        _loadBalancingPanel.add(_txt_minBandwidthPerPath, "growx, wrap");

        additionalConstraints.add(_loadBalancingEnabled, "growx, wrap");
        additionalConstraints.add(_loadBalancingPanel, "growx, wrap");
        serviceProvisioning.add(additionalConstraints, "growx, wrap");
        serviceProvisioning.add(_buttonMakePCERequest, "center, spanx 2, wrap");
        _buttonMakePCERequest.addActionListener(this);

        _buttonGotoServices.addActionListener(this);
        serviceProvisioning.add(_buttonGotoServices, "dock south");

        JPanel failureReparationSimulator = new JPanel();
        _fiberFailureSelector = new WiderJComboBox();
        failureReparationSimulator.setLayout(new MigLayout("insets 0 0 0 0", "[][grow]", "[][grow]"));
        failureReparationSimulator.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "Fiber failure/reparation simulator"));
        failureReparationSimulator.add(new JLabel("Select fiber to fail"));
        failureReparationSimulator.add(_fiberFailureSelector, "growx, wrap");

        JPanel failureSimulatorButtonPanel = new JPanel(new FlowLayout());
        failureSimulatorButtonPanel.add(_buttonFailFiber);
        failureReparationSimulator.add(failureSimulatorButtonPanel, "center, spanx 2, wrap");

        final DefaultTableModel model = new ClassAwareTableModel(new Object[1][6], new String[]{"Id", "Origin node", "Destination node", ""}) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex == 3;
            }
        };

        final JTable table = new AdvancedJTable(model);
        table.setEnabled(false);

        _buttonFailFiber.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int linkIndex = (int) ((StringLabeller) _fiberFailureSelector.getSelectedItem()).getObject();
                NetPlan netPlan = getDesign();
                Link link = netPlan.getLink(linkIndex, _wdmLayer);
                link.setFailureState(false);
                if (netPlan.getNetworkLayerDefault().getIndex() == 0) {
                    getTopologyPanel().getCanvas().showLink(link, Color.RED, false);
                }

                logMsg("Simulating failure");

                try {
                    if (_bgplsSocket == null) {
                        throw new Net2PlanException("PCC not connected to PCE");
                    }
                    //BGP4Update updateMessage = createLinkMessage(netPlan, netPlan.getLink(linkIndex, _wdmLayer).getId(), false);

                    OutputStream outBGP = _bgplsSocket.getOutputStream();
                    //Utils.writeMessage(outBGP, updateMessage.getBytes());
                } catch (Throwable ex) {
                    ErrorHandling.addErrorOrException(ex, ONOSPlugin.class);
                    ErrorHandling.showErrorDialog(ex.getMessage(), "Unable to simulate failure");
                    return;
                }

                if (_fiberFailureSelector.getItemCount() == 0) {
                    _buttonFailFiber.setVisible(false);
                }

                if (!table.isEnabled()) {
                    model.removeRow(0);
                }
                model.addRow(new Object[]{linkIndex, link.getOriginNode().getIndex(), link.getDestinationNode().getIndex(), "Repair"});

                table.setEnabled(true);
                updateNetPlanView();
            }
        });

        Action repair = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    JTable table = (JTable) e.getSource();
                    int modelRow = Integer.parseInt(e.getActionCommand());

                    int linkIndex = (int) table.getModel().getValueAt(modelRow, 0);
                    NetPlan netPlan = getDesign();

                    try {
                        if (_bgplsSocket == null) {
                            throw new Net2PlanException("PCC not connected to PCE");
                        }
                        //BGP4Update updateMessage = createLinkMessage(netPlan, netPlan.getLink(linkIndex, _wdmLayer).getId(), true);

                        OutputStream outBGP = _bgplsSocket.getOutputStream();
                        //Utils.writeMessage(outBGP, updateMessage.getBytes());
                    } catch (Throwable ex) {
                        ErrorHandling.addErrorOrException(ex, ONOSPlugin.class);
                        ErrorHandling.showErrorDialog(ex.getMessage(), "Unable to simulate reparation");
                        return;
                    }

                    Link link = netPlan.getLink(linkIndex, _wdmLayer);
                    link.setFailureState(true);
                    if (netPlan.getNetworkLayerDefault().getIndex() == 0) {
                        getTopologyPanel().getCanvas().showLink(link, Color.BLACK, false);
                    }

                    updateNetPlanView();

                    Node originNode = link.getOriginNode();
                    Node destinationNode = link.getDestinationNode();
                    _fiberFailureSelector.addItem(StringLabeller.unmodifiableOf(linkIndex, "e" + linkIndex + " [n" + originNode.getIndex() + " (" + originNode.getName() + ") -> n" + destinationNode
                            .getIndex() + " (" + destinationNode.getName() + ")]"));
                    _buttonFailFiber.setVisible(true);

                    ((DefaultTableModel) table.getModel()).removeRow(modelRow);

                    table.setEnabled(true);

                    if (table.getModel().getRowCount() == 0) {
                        ((DefaultTableModel) table.getModel()).addRow(new Object[6]);
                        table.setEnabled(false);
                    }
                } catch (Throwable e1) {
                    throw new RuntimeException(e1);
                }
            }
        };

        new ButtonColumn(table, repair, 3);

        final JScrollPane scrollPane = new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "Current failed fibers"));
        scrollPane.setAlignmentY(JScrollPane.TOP_ALIGNMENT);
        failureReparationSimulator.add(scrollPane, "spanx, grow, wrap");

        table.setDefaultRenderer(Long.class, new CellRenderers.UnfocusableCellRenderer());
        table.setDefaultRenderer(Integer.class, new CellRenderers.UnfocusableCellRenderer());
        table.setDefaultRenderer(String.class, new CellRenderers.UnfocusableCellRenderer());

        controllerTab.add(connectionHandler, "growx, wrap");
        controllerTab.add(xrapHandler, "growx, wrap");
        whatifTab.add(serviceProvisioning, "growx, wrap");
        whatifTab.add(failureReparationSimulator, "grow, wrap");
        JPanel eventGenerator = new JPanel(new MigLayout("insets 0 0 0 0", "[][grow]", "[]"));
        controllerTab.add(eventGenerator, "grow, wrap");

        addTab("NetRap", controllerTab, 2);
        addTab("WhatIf?", whatifTab, 2);

        File ALGORITHMS_DIRECTORY = new File(CURRENT_DIR + SystemUtils.getDirectorySeparator() + "workspace");
        ALGORITHMS_DIRECTORY = ALGORITHMS_DIRECTORY.isDirectory() ? ALGORITHMS_DIRECTORY : CURRENT_DIR;

        ParameterValueDescriptionPanel algorithmParameters = new ParameterValueDescriptionPanel();
        algorithmSelector = new RunnableSelector("Algorithm", null, IAlgorithm.class, ALGORITHMS_DIRECTORY, algorithmParameters);
        algorithmController = new ThreadExecutionController(this);
        JPanel pnl_buttons = new JPanel(new MigLayout("", "[center, grow]", "[]"));

        final JButton btn_solve = new JButton("Execute");
        pnl_buttons.add(btn_solve);
        btn_solve.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                algorithmController.execute();
            }
        });


        executionPane = new JPanel();
        executionPane.setLayout(new MigLayout("insets 0 0 0 0", "[grow]", "[grow]"));
        executionPane.add(algorithmSelector, "grow");
        executionPane.add(pnl_buttons, "dock south");

        addTab("Algorithm execution", executionPane, 1);

        webServer = new WebTest();
        webServer.startServer(this);

        logMsg("webserver started: ");
        logMsg("HttpHandler: " + webServer.server.getHttpHandler());
        logMsg("Service config: " + webServer.server.getServerConfiguration());

    }

    @Override
    public JPanel configureLeftBottomPanel() {
        _clearButton = new JButton("Clear");
        _clearButton.addActionListener(this);
        JToolBar toolbar = new JToolBar();
        toolbar.add(_clearButton);
        toolbar.setFloatable(false);
        toolbar.setRollover(true);

        guilog = new JTextArea();
        guilog.setLineWrap(true);
        guilog.setFont(new JLabel().getFont());
        DefaultCaret caret = (DefaultCaret) guilog.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JPanel leftBottomPanel = new JPanel(new MigLayout("fill, insets 0 0 0 0"));
        leftBottomPanel.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "Operation log"));
        leftBottomPanel.add(toolbar, "dock north");
        leftBottomPanel.add(new JScrollPane(guilog), "grow");

        return leftBottomPanel;
    }

    @Override
    public String getDescription() {
        return TITLE + " (GUI)";
    }

    @Override
    public KeyStroke getKeyStroke() {

        return KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK);

    }

    @Override
    public String getMenu() {
        return "SDN|" + TITLE;
    }

    @Override
    public String getName() {
        return TITLE + " (GUI)";
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    protected void reset_internal() {
        super.reset_internal();
        algorithmSelector.reset();
    }

    @Override
    public List<Triple<String, String, String>> getParameters() {

        List<Triple<String, String, String>> parameters = new LinkedList<>();
        String ip = "127.0.0.1";
        String port = "7777";
        String timeout = "5";
        parameters.add(Triple.of("onos.defaultIP", ip, "Default IP address of the machine where ONOS is running"));
        parameters.add(Triple.of("onos.defaultPort", port, "Default port of the machine where ONOS is running"));
        parameters.add(Triple.of("onos.defaultTimeout", timeout, "Default timeout for replies"));
        return parameters;
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object src = e.getSource();
        if (src == _loadBalancingEnabled) {
            SwingUtils.setEnabled(_loadBalancingPanel, _loadBalancingEnabled.isSelected());
        }
    }

    @Override
    public void loadDesign(NetPlan netPlan) throws Net2PlanException, NullPointerException {
        Stopwatch timer = Stopwatch.createStarted();
        super.loadDesign(netPlan);

        if (netPlan.hasNodes() && netPlan.getNumberOfLayers() == 2) {
            logMsg("Design loaded");
            if (netPlan.getNetworkLayers().size() != 2) {
                throw new Net2PlanException("Design should have 2 layers");
            }

            _wdmLayer = netPlan.getNetworkLayer(0);
            _ipLayer = netPlan.getNetworkLayer(1);

            List<Node> nodes = netPlan.getNodes();
            _sourceNode.removeAllItems();
            _destinationNode.removeAllItems();

            WDMUtils.setFibersNumFrequencySlots(netPlan, es.upct.girtel.net2plan.plugins.onos.utils.Constants.W, _wdmLayer);

            for (Node node : nodes) {
                Inet4Address ip = Utils.getNodeIPAddress(netPlan, node.getId());
                String ipAddress = null;
                if (ip != null) {
                    ipAddress = ip.getHostAddress();
                }
                if (ipAddress != null){
                    if (node.getAttribute(ATTRIBUTE_NODE_TYPE) != null) {
                        if (node.getAttribute(ATTRIBUTE_NODE_TYPE).equals(es.upct.girtel.net2plan.plugins.onos.utils
                                .Constants.NODE_TYPE_ROADM)) {
                            continue;
                        }
                    }
                }

                _sourceNode.addItem(StringLabeller.unmodifiableOf(node.getIndex(), ipAddress + " [n" + node.getIndex() + ", " + node.getName() + "]"));
                _destinationNode.addItem(StringLabeller.unmodifiableOf(node.getIndex(), ipAddress + " [n" + node.getIndex() + ", " + node.getName() + "]"));
            }

            _fiberFailureSelector.removeAllItems();
            for (Link link : netPlan.getLinks(_wdmLayer)) {
                Node originNode = link.getOriginNode();
                Node destinationNode = link.getDestinationNode();

                String sourceNodeType = originNode.getAttribute(ATTRIBUTE_NODE_TYPE);
                String destinationNodeType = destinationNode.getAttribute(ATTRIBUTE_NODE_TYPE);
                if(sourceNodeType != null && destinationNodeType != null){
                    if (sourceNodeType.equals(destinationNodeType) && sourceNodeType.equals("roadm")) {
                        _fiberFailureSelector.addItem(StringLabeller.unmodifiableOf(link.getIndex(), "e" + link.getIndex() + " [n" + originNode.getIndex() + " (" + originNode.getName() + ") -> n" +
                                destinationNode.getIndex() + " (" + destinationNode.getName() + ")]"));
                    }
                }
            }
        }
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }
    public void updateGUIandPos(){
        Stopwatch timer = Stopwatch.createStarted();

        NetPlan netPlan = getDesign();
        getTopologyPanel().getCanvas().updateTopology(netPlan);
        getTopologyPanel().getCanvas().refresh();
        updateNetPlanView();
        getTopologyPanel().updateLayerChooser();
        getTopologyPanel().getCanvas().zoomAll();
        if(DYNAMIC_LAYOUT) {
            for (Node n : netPlan.getNodes()) {
                Point2D pt = getTopologyPanel().getCanvas().getNodeXYPosition(n);
                n.setXYPositionMap(pt);
                netPlan.setNodeXY(n);
            }
        }
        if(System.getProperty("profile") != null)
            updateOperationLog("  " + Utils.getCurrentMethodName() + " took: " + timer.stop());
    }

    private void connect() {
        _txt_ip.setEnabled(false);
        _buttonConnectToONOS.setEnabled(false);
        _buttonConnectToONOS.setBackground(Color.GREEN);
        _buttonDisconnectFromONOS.setEnabled(true);
        startXRAP();
    }

    private void shutdown() {
        _txt_ip.setEnabled(true);
        _buttonConnectToONOS.setEnabled(true);
        _buttonConnectToONOS.setBackground(Color.RED);
        _buttonDisconnectFromONOS.setEnabled(false);
        xrapApi.terminate();

    }


    private void startXRAP() {
        boolean isServer = true;
        String host = _txt_ip.getText();
        Integer port = Integer.getInteger(_txt_port.getText());
        if (port == null) {
            port = 7777;
        }

        logMsg("Starting XrapPeer(" + host + "," + port + "," + isServer + ")");
        xrapApi = new XrapApi(host, port, "n2p-1");
        if (xrapApi == null) {
            logMsg("Error starting XRAP");
            return;
        }
        // let XrapPeer look for classes annotated with JAX-RS annotations
        // it will scan the es.upct.. package, and add a reference to this object as parent in the instances
        xrapApi.addJaxRs("es.upct.girtel.net2plan.plugins.onos",this);
        logMsg("XRAP started, adding handler");


        /* TODO: uncomment this!
        xrapApi.addHandler(new DemandResource());
        xrapApi.addHandler(new TopoResource());
*/
        logMsg("Registering with ONOS");
        String ret = xrapApi.register();
        if (ret.equals("")) {
            logMsg("Error registering with ONOS");
            return;
        } else {
            if(System.getProperty("debug") != null)
                logMsg("Got reply : " + ret);
            else
                logMsg("OK");
        }



        /* logMsg("getting onos topology");
        Topology topo = xrapApi.getTopology();
        if (topo == null) {
            logMsg("Error getting onos topology");
            return;
        }
        logMsg("Got topology:" + topo);
        updateTopology(topo);

        logMsg("getting onos demands");
        List<io.swagger.client.model.Demand> demands = xrapApi.getDemands();
        if (demands == null) {
            logMsg("Error getting onos demands");
            return;
        }
        logMsg("Got demands:" + demands);
        updateDemands(demands);
        */
        //runAlgorithm();
    }

    // adapdet from GUINetworkDesign.java#L132 , execute()
    public void runAlgorithm() {
        start = System.nanoTime();





        /*
        private InputParameter _K_ipLayer = new InputParameter("K_ipLayer", "50", "Maximum number of candidate paths to setup IP routes");
        // "5"
        private InputParameter _K_wdmLayer = new InputParameter("K_wdmLayer", "5", "Maximum number of candidate paths to setup lightpaths");
        // "10"
        private InputParameter _TRxAvailableBitrates = new InputParameter("TRxAvailableBitrates", "10, 100, 200, 400", "Array of transceiver binary rates (in Gbps)");
        // "3000"
        private InputParameter _TRxReach = new InputParameter("TRxReach", "3000, 2000, 1000, 500", "Array of transceiver reach (in km)");
        // "1"
        private InputParameter _TRxSlots = new InputParameter("TRxSlots", "2, 3, 4, 6", "Array of spectral slots needed for respective bitrate");
        // "pathSelectionPolicy" for the moment, ignore IPOPT for the moment
        private InputParameter _oppAlgorithm = new InputParameter("oppAlgorithm", "#select# IPOPTcost pathSelectionPolicy", "OPP algorithm");
        // "maxBW"
        private InputParameter _flexgridPolicy = new InputParameter("flexgridPolicy", "#select# maxBW minBW mix", "Transceiver selection policy: mix is maxBW for wdmClass > 0, minBW otherwise");
        // "km"
        private InputParameter _wdmShortestPathType = new InputParameter("wdmShortestPathType", "#select# km hops", "Shortest path type in the optical layer: hops, or km");
        // "minLat"
        private InputParameter _pathSelectionPolicy = new InputParameter("pathSelectionPolicy", "#select# minLat maxLat maxDisLat minAvResBand maxAvResBand minResBand maxResBand minPathWiseResBand maxPathWiseResBand", "Path selection policy");
        // -1
        private InputParameter _IPOPT_run = new InputParameter("IPOPT_run", (int) -1, "Run IPOPT after this many succesful IPP runs (-1 to disable)", -1, 999999);
        // false
        private InputParameter _removeEmptyIPLinks = new InputParameter("removeEmptyIPLinks", true, "Whether to remove unused IP links");
        // "0"
        private InputParameter _removeFrequency = new InputParameter("removeFrequency", (int) 0, "Try to remove IP links after that many IP_OPT calls (if 0 try even if IP_OPT wasn't called)");
        // "ip_opt_simple_aa" or "ip_opt_null" for the moment, shouldn't be needed at the moment
        private InputParameter _IPOPT_Algorithm = new InputParameter("IPOPT", "#algorithm#", "IPOPT algorithm");
*/






        /*
        // Prepare algorithm object
        File algoFile = new File("/home/eponsko/n2p-plugins-ONOS/temp/Net2Plan/target/net2plan-0.4.2/workspace/BuiltInExamples.jar");
        String algoName = "com.net2plan.examples.ocnbook.offline.Offline_fa_xdeFormulations";
        Class algoClass = com.net2plan.interfaces.networkDesign.IAlgorithm.class;
        Triple<File, String, Class> algorithm = new Triple<>(algoFile, algoName, algoClass, false);

        // Prepare algorithm parameters
        final Map<String, String> algorithmParameters = new HashMap<>();
        algorithmParameters.put("averagePacketLengthInBytes", "500.0");
        algorithmParameters.put("binaryRatePerTrafficUnit_bps", "1000000.0");
        algorithmParameters.put("maxLengthInKm", "-1.0");
        algorithmParameters.put("maxSolverTimeInSeconds", "-1.0");
        algorithmParameters.put("nonBifurcatedRouting", "false");
        algorithmParameters.put("optimizationTarget", "min-av-num-hops");
        algorithmParameters.put("solverLibraryName", "");
        algorithmParameters.put("solverName", "glpk");


        // Get n2p configuration
        final Map<String, String> net2planParameters = Configuration.getNet2PlanOptions();


        logMsg("Got algorithm: " + algorithm);
        logMsg("Got algorithmParameters: " + algorithmParameters);
        logMsg("Got net2planParamaters: " + net2planParameters);

        NetPlan netPlanCopy = getDesign().copy();
        IAlgorithm instance = new ip_opt_simple_aa();
        //IAlgorithm instance = ClassLoaderUtils.getInstance(algorithm.getFirst(), algorithm.getSecond(), IAlgorithm.class);
        algorithmParameters.put("K","5");
        algorithmParameters.put("demandOrder","increasing");
        algorithmParameters.put("seed","89383");
        logMsg("Got algortithm instance: " + instance);
        logMsg("Executing algorithm");
        String out;
        try {
            out = instance.executeAlgorithm(netPlanCopy, algorithmParameters, net2planParameters);
        } catch(Net2PlanException e){
            logMsg("Error executing algorithm:  " + e.getMessage());
            return;
        }
        logMsg("Got output: " + out);

        for (Demand d : netPlanCopy.getDemands()) {
            logMsg("new netplan Demands: " + d);
        }
        for (Route route : netPlanCopy.getRoutes()) {
            logMsg("Route -> egress -> name: " + route.getDemand().getEgressNode().getName());
            logMsg("Route -> demand: " + route.getDemand());
            logMsg("Route -> carried traffic: " + route.getCarriedTraffic());
            logMsg("Route -> path sequence: " + route.getSeqLinksRealPath());
            logMsg("Route -> attributes: " + route.getAttributes());
        }
        logMsg("Updating netplan.." + netPlanCopy);
        netPlanCopy.setModifiableState(true);

        getDesign().assignFrom(netPlanCopy);
        getDesign().setModifiableState(true); */

        // updateNetPlanView();
    }




    private void getRoutes() {
        NetPlan netPlan = getDesign();

        _wdmLayer = netPlan.getNetworkLayer("WDM");
        if (_wdmLayer == null) {
            logMsg("Could not fetch WDM layer");
            return;
        }
        List<Route> wdmRoutes = netPlan.getRoutes(_wdmLayer);
        if (wdmRoutes == null) {
            logMsg("Could not fetch WDM routes");
            return;

        }
        logMsg("WDM routes: ");
        int i = 0;
        for (Route r : wdmRoutes) {
            logMsg("\nWDM Route " + i++);
            logMsg(" Demand: " + r.getDemand() + " attr: " + r.getDemand().getAttributes());
            logMsg(" attr: " + r.getAttributes());
            /*logMsg("  Nodes");
            List<Node> nodes = r.getSeqNodesRealPath();
            for (Node n : nodes) {
                logMsg("    Node: " + n.getName() + " attr: " + n.getAttributes());

            }*/
            List<Link> links = r.getSeqLinksRealPath();
            logMsg("  Links");
            for (Link l : links) {
                logMsg("    Link src: " + l.getOriginNode().getName() + " dst: " + l.getDestinationNode().getName() + " attr: " + l.getAttributes());
            }

        }

        _ipLayer = netPlan.getNetworkLayer("IP");
        if (_wdmLayer == null) {
            logMsg("Could not fetch IP layer");
            return;
        }
        List<Route> ipRoutes = netPlan.getRoutes(_ipLayer);
        if (ipRoutes == null) {
            logMsg("Could not fetch IP routes");
            return;

        }
        logMsg("IP routes: ");
        for (Route r : ipRoutes) {
            logMsg("\nIP Route " + i++);
            logMsg(" Demand: " + r.getDemand() + " attr: " + r.getDemand().getAttributes());

            logMsg(" attr: " + r.getAttributes());
            /*logMsg("  Nodes");
            List<Node> nodes = r.getSeqNodesRealPath();
            for (Node n : nodes) {
                logMsg("    Node: " + n.getName() + " attr: " + n.getAttributes());
            }*/
            List<Link> links = r.getSeqLinksRealPath();
            logMsg("  Links");
            for (Link l : links) {
                logMsg("    Link src: " + l.getOriginNode().getName() + " dst: " + l.getDestinationNode().getName() + " attr: " + l.getAttributes());
            }

        }
    }


/*
    public boolean updateTopology(NetRapTopology topo) {
        NetPlan netPlan = getDesign();

        _wdmLayer = netPlan.getNetworkLayer("WDM");
        if (_wdmLayer == null) {
            _wdmLayer = netPlan.getNetworkLayerDefault();
            _wdmLayer.setName("WDM");
            _wdmLayer.setDescription("WDM layer obtained from ONOS");
            netPlan.setNetworkLayerDefault(_wdmLayer);
            netPlan.setRoutingType(com.net2plan.utils.Constants.RoutingType.SOURCE_ROUTING, _wdmLayer);
        }
        _ipLayer = netPlan.getNetworkLayer("IP");
        if (_ipLayer == null) {
            //_ipLayer = netPlan.addLayer("IP", "IP layer obtained from ONOS", "Mbps", "Mbps", null);
            _ipLayer = netPlan.addLayer("IP", "IP layer obtained from ONOS", null, null, null);
        }
        netPlan.setRoutingType(com.net2plan.utils.Constants.RoutingType.SOURCE_ROUTING, _ipLayer);


        HashMap<String, Long> nodeIds = new HashMap<>();
        HashMap<String, Long> linkIds = new HashMap<>();


        netPlan.removeAllNodes();
        NetworkLayer nl = netPlan.getNetworkLayerDefault();
        netPlan.setDescription("NetPlan obtained from ONOS");
        netPlan.setDescription("IP layer obtained from ONOS", _ipLayer);
        netPlan.setDescription("WDM layer obtained from ONOS", _wdmLayer);
        //netPlan.setLinkCapacityUnitsName("Wavelength slots", _wdmLayer);
        //netPlan.setDemandTrafficUnitsName("Wavelength slots", _wdmLayer);
        netPlan.setNetworkName("ONOS network");


        for (NetRapNode netRapNode : topo.getNodes()) {

            double x = netRapNode.getLatitude();
            double y = netRapNode.getLongitude();

            Node newnode = netPlan.addNode(x, y, netRapNode.getName(),
                    null); // attribute map
            if (netRapNode.getMTBF() != null && netRapNode.getMTTR() != null) {
                double mttr = netRapNode.getMTTR();
                double mtbf = netRapNode.getMTBF();
                SharedRiskGroup srg = netPlan.addSRG(mtbf, mttr, null);
                srg.addNode(newnode);
            }
            Map<String, String> attributes = (Map<String, String>) netRapNode.getAttributes();
            if (attributes != null) {
                for (String key : attributes.keySet()) {
                    newnode.setAttribute(key, attributes.get(key));
                }
            }

            logMsg("added node " + netRapNode.getName() + " assigned id " + newnode.getId());
            nodeIds.put(netRapNode.getName(), newnode.getId());
        }

        for (NetRapLink netRapLink : topo.getLinks()) {

            NetworkLayer layer = _wdmLayer;
            Node orig = netPlan.getNodeByName(netRapLink.getDst());
            Node dest = netPlan.getNodeByName(netRapLink.getSrc());
            logMsg("Adding link between " + netRapLink.getSrc() + " and " + netRapLink.getDst());
            logMsg("Adding link between " + orig + " and " + dest);

            switch (netRapLink.getLayer()) {

                case WDM_LAYER_INDEX:
                    layer = _wdmLayer;
                    break;
                case es.upct.girtel.net2plan.plugins.onos.utils.Constants.IP_LAYER_INDEX:
                    layer = _ipLayer;
                    break;
                default:
                    logMsg("ERROR: Could not determine layer, got value: " + netRapLink.getLayer());
                    continue;
            }

            Link newLink = netPlan.addLink(orig, dest,
                    netRapLink.getCapacity(), // Capacity, get from port type?
                    netRapLink.getLengthInKm(), // Length, no value
                    netRapLink.getPropagationSpeed(), // Propagation speed, 200000km/s
                    null, // attribute map
                    layer); // Optional layer
            Map<String, String> attributes = (Map<String, String>) netRapLink.getAttributes();
            if (attributes != null) {
                for (String key : attributes.keySet()) {
                    newLink.setAttribute(key, attributes.get(key));
                }
            }
            if (netRapLink.getMTBF() != null && netRapLink.getMTTR() != null) {
                double mttr = netRapLink.getMTTR();
                double mtbf = netRapLink.getMTBF();
                SharedRiskGroup srg = netPlan.addSRG(mtbf, mttr, null);
                srg.addLink(newLink);
            }

            newLink.setFailureState(true);
            linkIds.put(netRapLink.getDst() + " - " + netRapLink.getSrc(), newLink.getId());
        }
        this.getTopologyPanel().getCanvas().updateTopology(netPlan);
        this.getTopologyPanel().getCanvas().refresh();
        updateNetPlanView();
        topologyPanel.updateLayerChooser();
        topologyPanel.getCanvas().zoomAll();
        logMsg("Getting calculated node positions..");
        for(Node n : netPlan.getNodes()) {
            Point2D pt = topologyPanel.getCanvas().getNodeXYPosition(n);
            n.setXYPositionMap(pt);
            netPlan.setNodeXY(n);
        }
        return true;
    }
*/


    public void updateOperationLog(String message) {
        guilog.append(message);
        guilog.append(NEW_LINE);
    }

    @Override
    public void showRoute(long routeId) {
        NetPlan netPlan = getDesign();
        Route route = netPlan.getRouteFromId(routeId);
        NetworkLayer layer = route.getLayer();
        selectNetPlanViewItem(layer.getId(), Constants.NetworkElementType.ROUTE, routeId);
        Map<Link, Pair<Color, Boolean>> coloredLinks = new HashMap<>();

        for (Link link : route.getSeqLinksRealPath())
            coloredLinks.put(link, Pair.of(Color.BLUE, false));
        if (!route.getInitialSequenceOfLinks().equals(route.getSeqLinksRealPath())) {
            for (Link link : route.getInitialSequenceOfLinks()) {
                coloredLinks.put(link, Pair.of(Color.BLUE, true));
                if (link.isDown()) {
                    coloredLinks.put(link, Pair.of(Color.RED, true));
                }
            }
        }
        topologyPanel.getCanvas().showAndPickNodesAndLinks(null, coloredLinks);
        topologyPanel.getCanvas().refresh();
    }



   /* private class TopoResource extends XrapResource {
        public TopoResource() {
            setRoute("/topology");
        }

        public XrapReply POST(XrapPostRequest request) {
            String errormsg = "Unknown error";

            acceptTopology:
            {
                NetRapTopology topo = xrapApi.parseTopology(request.getContentBody());
                if (topo == null) {
                    errormsg = "Could not decode JSON";
                    break acceptTopology;
                }
                updateTopology(topo);
                updateOperationLog("Got new topology " + topo);
                XrapPostReply rep = new XrapPostReply(request);
                return rep;
            }
            XrapErrorReply rep = new XrapErrorReply();
            rep.setErrorText(errormsg);
            rep.setStatusCode(com.wpl.xrapc.Constants.BadRequest_400);
            rep.setRequestId(request.getRequestId());
            rep.setRouteid(request.getRouteid());
            return rep;
        }

    }*/
/*
    private class DemandResource extends XrapResource {
        public DemandResource() {
            setRoute("/demands/");
        }

        public XrapReply POST(XrapPostRequest request) {
            String errormsg = "Unknown error";

            acceptDemands:
            {
                List<NetRapDemand> demands = xrapApi.parseDemands(request.getContentBody());
                if (demands == null) {
                    errormsg = "Could not decode JSON";
                    break acceptDemands;
                }
                updateDemands(demands);
                updateOperationLog("Got new demands " + demands);
                XrapPostReply rep = new XrapPostReply(request);
                return rep;
            }
            XrapErrorReply rep = new XrapErrorReply();
            rep.setErrorText(errormsg);
            rep.setStatusCode(com.wpl.xrapc.Constants.BadRequest_400);
            rep.setRequestId(request.getRequestId());
            rep.setRouteid(request.getRouteid());
            return rep;
        }

    }
    */

}
