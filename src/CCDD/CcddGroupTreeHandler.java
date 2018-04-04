/**
 * CFS Command & Data Dictionary group tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.GROUP_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddUndoHandler.UndoableTreeModel;

/**************************************************************************************************
 * CFS Command & Data Dictionary group tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddGroupTreeHandler extends CcddInformationTreeHandler
{
    // Class references
    private CcddDbTableCommandHandler dbTable;
    private CcddGroupHandler groupHandler;
    private CcddTableTypeHandler tableTypeHandler;
    private ToolTipTreeNode root;
    private CcddFieldHandler fieldHandler;

    // Flags to indicate if the tree should be filtered by table type and filtered by application
    private boolean isFilterByType;
    private boolean isFilterByApp;

    // Flag indicating if the group tree nodes are expanded or not
    private boolean isExpanded;

    // List to contain the group definitions (groups and variable paths) retrieved from the
    // database
    private List<String[]> groupDefinitions;

    // Flag to indicate if the group tree is being built
    private boolean isBuilding;

    // Array containing the comment parameters for each table
    private String[][] tableComments;

    // Tree nodes for the table types if filtering by type and for application status if filtering
    // by application
    private ToolTipTreeNode[] typeNodes;
    private ToolTipTreeNode[] appNodes;

    // Currently selected group's schedule rate
    private String scheduleRate;

    // Flag indication only application groups should be displayed
    private final boolean isApplicationOnly;

    // Node names for use when filtering the tree by application
    private static String APP_NODE = "Application";
    private static String OTHER_NODE = "Other";

    /**********************************************************************************************
     * Group tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param scheduleRate
     *            string value representing a schedule rate used to filter the groups that may be
     *            selected; null or blank if not filtering
     *
     * @param isApplicationOnly
     *            true if only groups that represent CFS applications should be displayed
     *
     * @param parent
     *            GUI component calling this method
     *********************************************************************************************/
    CcddGroupTreeHandler(CcddMain ccddMain,
                         CcddUndoHandler undoHandler,
                         String scheduleRate,
                         boolean isApplicationOnly,
                         Component parent)
    {
        super(ccddMain,
              undoHandler,
              InternalTable.GROUPS,
              scheduleRate,
              isApplicationOnly,
              null,
              parent);

        this.isApplicationOnly = isApplicationOnly;
    }

    /**********************************************************************************************
     * Group tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param parent
     *            GUI component calling this method
     *********************************************************************************************/
    CcddGroupTreeHandler(CcddMain ccddMain, CcddUndoHandler undoHandler, Component parent)
    {
        this(ccddMain, undoHandler, null, false, parent);
    }

    /**********************************************************************************************
     * Perform initialization steps prior to building the group tree
     *
     * @param ccddMain
     *            main class
     *
     * @param groupDefinitions
     *            list containing the group definitions
     *********************************************************************************************/
    @Override
    protected void initialize(CcddMain ccddMain,
                              CcddUndoHandler undoHandler,
                              List<String[]> groupDefinitions)
    {
        this.groupDefinitions = groupDefinitions;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        groupHandler = new CcddGroupHandler(undoHandler);
        fieldHandler = new CcddFieldHandler(ccddMain);

        // Set the tree to be collapsed initially
        isExpanded = false;

        // Set the selected schedule rate to a blank
        scheduleRate = "";

        // Get the array of table comments
        tableComments = ccddMain.getDbTableCommandHandler().queryDataTableComments(ccddMain.getMainFrame());

        // Set the table type and data type handlers, and table comments in the information tree
        // handler
        setHandlersAndComments(tableTypeHandler, tableComments);
    }

    /**********************************************************************************************
     * Get the reference to the group handler
     *
     * @return Reference to the group handler
     *********************************************************************************************/
    protected CcddGroupHandler getGroupHandler()
    {
        return groupHandler;
    }

    /**********************************************************************************************
     * Get the reference to the data field handler
     *
     * @return Reference to the data field handler
     *********************************************************************************************/
    protected CcddFieldHandler getFieldHandler()
    {
        return fieldHandler;
    }

    /**********************************************************************************************
     * Remove the selected group(s) from the group information class and group tree
     *********************************************************************************************/
    protected void removeSelectedGroups()
    {
        List<String> groups = new ArrayList<String>();

        // Store the paths of the groups selected for deletion. The paths are 'lost' when the nodes
        // are removed in the next step
        TreePath[] paths = getSelectionPaths();

        // Remove the selected group(s) from the group tree. This is performed before removal of
        // the group information so that an undo operation restores the group information prior to
        // restoration of the tree node(s); this way if only a single group is restored via an undo
        // then the group's description and fields are displayed in the group manager
        removeSelectedTopLevelNodes();

        // Step through each selected path
        for (TreePath path : paths)
        {
            // Get the group node for this path
            String name = path.getPathComponent(1).toString();

            // Check if this group has already been removed
            if (!groups.contains(name))
            {
                // Add the group to the list of those removed
                groups.add(name);

                // Remove the group's information
                groupHandler.removeGroupInformation(name);
            }
        }
    }

    /**********************************************************************************************
     * Build the group tree from the database
     *
     * @param filterByType
     *            true if the tree is filtered by table type
     *
     * @param filterByApp
     *            true if the tree is filtered by application status
     *
     * @param scheduleRate
     *            schedule rate used to filter the groups; blank or null if not filtering by
     *            schedule rate
     *
     * @param isApplicationOnly
     *            true to only display groups that represent a CFS application
     *
     * @param parent
     *            GUI component calling this method
     *********************************************************************************************/
    @Override
    protected void buildTree(boolean filterByType,
                             boolean filterByApp,
                             String scheduleRate,
                             boolean isApplicationOnly,
                             Component parent)
    {
        this.isFilterByType = filterByType;
        this.isFilterByApp = filterByApp;
        this.scheduleRate = scheduleRate;

        super.buildTree(isFilterByType, isFilterByApp, scheduleRate, isApplicationOnly, parent);

        // Get the tree's root node
        root = getRootNode();

        // Build the group information using the group definitions and group data fields from the
        // database
        groupHandler.buildGroupInformation(groupDefinitions);
        buildFieldInformation(parent);

        // Register the tool tip manager for the group tree (otherwise the tool tips aren't
        // displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Set the flag to indicate that the group tree is being built. This flag is used to
        // inhibit actions involving tree selection value changes during the build process
        isBuilding = true;

        // Set the renderer for the tree so that custom icons can be used for the various node
        // types
        setCellRenderer(new TableTreeCellRenderer()
        {
            /**************************************************************************************
             * Display the variable nodes using a special icon in the tree
             *************************************************************************************/
            @Override
            public Component getTreeCellRendererComponent(JTree tree,
                                                          Object value,
                                                          boolean sel,
                                                          boolean expanded,
                                                          boolean leaf,
                                                          int row,
                                                          boolean hasFocus)
            {
                // Display the node name
                super.getTreeCellRendererComponent(tree,
                                                   value,
                                                   sel,
                                                   expanded,
                                                   leaf,
                                                   row,
                                                   hasFocus);

                // Get the tree level for this node
                int level = ((ToolTipTreeNode) value).getLevel();

                // Check if this node represents a group name
                if (level == 1)
                {
                    // Display an icon indicating a variable
                    setIcon(new ImageIcon(getClass().getResource(GROUP_ICON)));
                }

                return this;
            }
        });

        // Check if the table types are to be used to filter the table tree
        if (isFilterByType)
        {
            // Create the node storage for the table types
            typeNodes = new ToolTipTreeNode[tableTypeHandler.getTypes().length];
        }

        // Check if the application statuses are to be used to filter the group tree
        if (isFilterByApp)
        {
            // Create the node storage for the application statuses
            appNodes = new ToolTipTreeNode[2];
            appNodes[0] = addInformationNode(APP_NODE, "Groups representing a CFS application");
            appNodes[1] = addInformationNode(OTHER_NODE,
                                             "Groups not representing a CFS application");
        }

        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Extract the group name
            String groupName = groupInfo.getName();

            // Check if all groups should be displayed or only applications and this is an
            // application
            if (!isApplicationOnly || groupInfo.isApplication())
            {
                // Create a node for the group and add it to the group tree
                ToolTipTreeNode groupNode = addInformationNode(groupName,
                                                               groupInfo.getDescription(),
                                                               groupInfo.isApplication());

                // Check if the table nodes should be displayed (i.e., no schedule rate is
                // supplied)
                if (scheduleRate == null || scheduleRate.isEmpty())
                {
                    // Check if the table types are to be used to filter the table tree
                    if (isFilterByType)
                    {
                        int index = 0;

                        // Step through each table type
                        for (String type : tableTypeHandler.getTypes())
                        {
                            // Create the node for this table type and add it to the tree model
                            typeNodes[index] = new ToolTipTreeNode(type,
                                                                   tableTypeHandler.getTypeDefinition(type).getDescription());
                            ((UndoableTreeModel) getModel()).insertNodeInto(typeNodes[index], groupNode, index);
                            index++;
                        }
                    }

                    // Step through each table belonging to the group
                    for (String table : groupInfo.getTablesAndAncestors())
                    {
                        // Check if the groups are filtered by application status
                        if (isFilterByApp)
                        {
                            boolean isFound = false;

                            // Step through the application and non-application nodes; exit if a
                            // match is found
                            for (int nodeIndex = 0; nodeIndex < appNodes.length && !isFound; nodeIndex++)
                            {
                                // Step through each current group node
                                for (int index = 0; index < appNodes[nodeIndex].getChildCount(); index++)
                                {
                                    // Check if the group name matches the node name
                                    if (groupName.equals(appNodes[nodeIndex].getChildAt(index).toString()))
                                    {
                                        // Add the indicating a match is found, and stop searching
                                        addNodeToInfoNode((ToolTipTreeNode) appNodes[nodeIndex].getChildAt(index),
                                                          table.split(","),
                                                          0);
                                        isFound = true;
                                        break;
                                    }
                                }
                            }
                        }
                        // Groups are not filtered by application status
                        else
                        {
                            // Step through each current group node
                            for (int index = 0; index < root.getChildCount(); index++)
                            {
                                // Check if the group name matches the node name
                                if (groupName.equals(root.getChildAt(index).toString()))
                                {
                                    // Add the table to the node and stop searching
                                    addNodeToInfoNode((ToolTipTreeNode) root.getChildAt(index),
                                                      table.split(","),
                                                      0);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expand or collapse the tree based on the expansion flag
        setTreeExpansion(isExpanded);

        // Clear the flag that indicates the group tree is being built
        isBuilding = false;
    }

    /**********************************************************************************************
     * Build the group data field information
     *
     * @param parent
     *            GUI component calling this method
     *********************************************************************************************/
    private void buildFieldInformation(Component parent)
    {
        // Get the data field information from the database
        fieldHandler.setFieldDefinitions(dbTable.retrieveInformationTable(InternalTable.FIELDS,
                                                                          parent));

        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Build the field information list for this group
            fieldHandler.buildFieldInformation(CcddFieldHandler.getFieldGroupName(groupInfo.getName()));

            // Set the field information in the group handler
            groupInfo.setFieldInformation(fieldHandler.getFieldInformationCopy());
        }

        // Reset the field handler field information reference so that no fields are initially
        // active
        fieldHandler.getFieldInformation().clear();
    }

    /**********************************************************************************************
     * Add a copy of the specified group information object to the group information list
     *
     * @param groupToCopy
     *            group information object to copy
     *
     * @param nameOfCopy
     *            name of the copy of the group
     *********************************************************************************************/
    @Override
    protected void addInformation(Object groupToCopy, String nameOfCopy)
    {
        // Store the group information for the copy
        groupHandler.getGroupInformation().add(new GroupInformation(nameOfCopy,
                                                                    ((GroupInformation) groupToCopy).getDescription(),
                                                                    ((GroupInformation) groupToCopy).isApplication(),
                                                                    ((GroupInformation) groupToCopy).getTablesAndAncestors(),
                                                                    ((GroupInformation) groupToCopy).getFieldInformation()));
    }

    /**********************************************************************************************
     * Initialize the group definition list with the group names and descriptions
     *
     * @return List containing the groups with their names and descriptions
     *********************************************************************************************/
    @Override
    protected List<String[]> createDefinitionsFromInformation()
    {
        // Initialize the group tree information list
        List<String[]> definitions = new ArrayList<String[]>();

        // Step through each group's information
        for (GroupInformation grpInfo : groupHandler.getGroupInformation())
        {
            // Add the group's name and description to the list. If the group represents a CFS
            // application it's definition begins with a non-zero numeral
            definitions.add(new String[] {grpInfo.getName(),
                                          (grpInfo.isApplication()
                                                                   ? "1"
                                                                   : "0")
                                                             + ","
                                                             + grpInfo.getDescription()});
        }

        return definitions;
    }

    /**********************************************************************************************
     * Set the node text color based on the currently selected schedule rate and the rate of the
     * group to which the node belongs: black for a match and gray for a mismatch
     *
     * @param startNode
     *            starting node for which to adjust the text and color
     *********************************************************************************************/
    protected void adjustNodeText(ToolTipTreeNode startNode, List<String> excludes)
    {
        // Step through the node's children, if any
        for (Enumeration<?> element = startNode.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node reference
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Get the tree level for this node
            int level = node.getLevel();

            // Check that the tree has any levels. When the tree is first created this method is
            // called when no nodes exist
            if (level > 0)
            {
                // Get the group name from the node. The group name is the second node in the path
                // for this node
                String groupName = removeExtraText(node.getPath()[1].toString());

                // Get the reference to the group's information
                GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

                // Check that the node references a group
                if (groupInfo != null)
                {
                    // Get the node name
                    String nodeName = node.getUserObject().toString();

                    // Set to true if the group in this path is not excluded (as evidenced by
                    // having a HTML tag)
                    boolean wasExcluded = nodeName.contains(DISABLED_TEXT_COLOR);

                    // Remove any HTML tags or other extra text from the node name
                    nodeName = removeExtraText(nodeName);

                    // Get the reference to the schedule rate field information
                    fieldHandler.setFieldInformation(groupInfo.getFieldInformation());
                    FieldInformation rateInfo = fieldHandler.getFieldInformationByName(CcddFieldHandler.getFieldGroupName(groupName),
                                                                                       DefaultApplicationField.SCHEDULE_RATE.getFieldName());

                    // Set the flag indicating the group is excluded if it's in the exclusion list
                    boolean isExcluded = rateInfo == null
                                         || rateInfo.getValue().isEmpty()
                                         || !scheduleRate.equals(rateInfo.getValue())
                                         || excludes.contains(nodeName);

                    // Check if the group's exclusion state has changed
                    if (wasExcluded != isExcluded)
                    {
                        // Reset the node name to indicate its inclusion/exclusion state. If
                        // excluded, prepend the HTML tag to gray out the name. Indicate that the
                        // node changed so that the tree redraws the name
                        node.setUserObject((isExcluded
                                                       ? DISABLED_TEXT_COLOR
                                                       : "")
                                           + nodeName);
                        ((DefaultTreeModel) getModel()).nodeChanged(node);
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Create a group tree panel. The table tree is placed in a scroll pane. A check box is added
     * that allows tree expansion/collapse
     *
     * @param label
     *            group tree title
     *
     * @param selectionMode
     *            tree item selection mode (single versus multiple)
     *
     * @param noFilters
     *            true to not display the filter check boxes
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return JPanel containing the group tree components
     *********************************************************************************************/
    protected JPanel createTreePanel(String label,
                                     int selectionMode,
                                     boolean noFilters,
                                     final Component parent)
    {
        // Create an empty border
        Border emptyBorder = BorderFactory.createEmptyBorder();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(0,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   0),
                                                        0,
                                                        0);

        // Set the table tree selection mode
        getSelectionModel().setSelectionMode(selectionMode);

        // Create a panel to contain the table tree
        final JPanel treePnl = new JPanel(new GridBagLayout());
        treePnl.setBorder(emptyBorder);

        // Create the tree labels
        JLabel treeLbl = new JLabel(label);
        treeLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        treeLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        treePnl.add(treeLbl, gbc);

        // Create the tree scroll pane
        JScrollPane treeScroll = new JScrollPane(this);
        treeScroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                Color.LIGHT_GRAY,
                                                                                                Color.GRAY),
                                                                BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));

        // Check if this is the last component to add
        if (noFilters)
        {
            gbc.insets.bottom = 0;
        }

        // Add the tree to the panel
        gbc.weighty = 1.0;
        gbc.gridy++;
        treePnl.add(treeScroll, gbc);

        // Set the table tree font and number of rows to display
        setFont(ModifiableFontInfo.TREE_NODE.getFont());
        setVisibleRowCount(10);

        // Add a listener for changes to the group tree
        addTreeSelectionListener(new TreeSelectionListener()
        {
            /**************************************************************************************
             * Handle a change to the group tree selection
             *************************************************************************************/
            @Override
            public void valueChanged(TreeSelectionEvent lse)
            {
                // Check that a group tree (re)build isn't in progress. Building the tree triggers
                // tree selection value changes that should not be processed
                if (!isBuilding)
                {
                    // Update the group dialog based on the group(s) selected
                    updateTableSelection();
                }
            }
        });

        // Check if the filter check boxes should be displayed
        if (!noFilters)
        {
            // Create a tree expansion check box
            final JCheckBox expandChkBx = new JCheckBox("Expand all");
            expandChkBx.setBorder(emptyBorder);
            expandChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            expandChkBx.setSelected(false);
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.weighty = 0.0;
            gbc.gridy++;
            treePnl.add(expandChkBx, gbc);

            // Create a listener for changes in selection of the tree expansion check box
            expandChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the tree expansion check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Set the flag indicating if the tree is fully expanded
                    isExpanded = expandChkBx.isSelected();

                    // Set the tree expansion based on the check box status
                    setTreeExpansion(isExpanded);
                }
            });

            // Create a hide data type check box
            final JCheckBox hideTypeChkBx = new JCheckBox("Hide data type");
            hideTypeChkBx.setBorder(emptyBorder);
            hideTypeChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            hideTypeChkBx.setSelected(false);

            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.weighty = 0.0;
            gbc.gridy++;
            treePnl.add(hideTypeChkBx, gbc);

            // Create a listener for changes in selection of the hide data type check box
            hideTypeChkBx.addActionListener(new ActionListener()
            {
                /**************************************************************************************
                 * Handle a change to the hide data type check box selection
                 *************************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    setEnableDataType(!hideTypeChkBx.isSelected());

                    // Store the tree's current expansion state
                    String expState = getExpansionState();

                    // Force the root node to draw with the node additions
                    ((DefaultTreeModel) treeModel).nodeStructureChanged(getRootNode());

                    setExpansionState(expState);
                }
            });

            // Create a type filter check box
            final JCheckBox typeFilterChkBx = new JCheckBox("Filter by type");
            typeFilterChkBx.setBorder(emptyBorder);
            typeFilterChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            typeFilterChkBx.setSelected(false);
            gbc.gridy++;
            treePnl.add(typeFilterChkBx, gbc);

            // Create an application filter check box
            final JCheckBox appFilterChkBx = new JCheckBox("Filter by application");
            appFilterChkBx.setBorder(emptyBorder);
            appFilterChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            appFilterChkBx.setSelected(false);
            gbc.gridy++;
            treePnl.add(appFilterChkBx, gbc);

            // Create a listener for changes in selection of the type filter check box
            typeFilterChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the type filter check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Recreate the group definitions based on the current tree members
                    groupDefinitions = createDefinitionsFromTree();

                    // Store the tree's current expansion state
                    String expState = getExpansionState();

                    // Rebuild the tree based on the filter selection
                    buildTree(typeFilterChkBx.isSelected(),
                              appFilterChkBx.isSelected(),
                              scheduleRate,
                              isApplicationOnly,
                              parent);

                    final List<String> topNodePrefixes = new ArrayList<String>();

                    // Step through each node immediately below the root node
                    for (int index = 0; index < root.getChildCount(); index++)
                    {
                        // Add the node name to the list of prefixes
                        topNodePrefixes.add("["
                                            + root.getUserObject()
                                            + ", "
                                            + ((ToolTipTreeNode) root.getChildAt(index)).getUserObject());
                    }

                    // Adjust the expansion state to account for the change in filtering, then
                    // restore the expansion state
                    expState = adjustExpansionState(expState,
                                                    appFilterChkBx.isSelected(),
                                                    false,
                                                    typeFilterChkBx.isSelected(),
                                                    true,
                                                    true,
                                                    topNodePrefixes,
                                                    groupHandler,
                                                    tableTypeHandler);
                    setExpansionState(expState);
                }
            });

            // Create a listener for changes in selection of the application filter check box
            appFilterChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the type filter check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Recreate the group definitions based on the current tree members
                    groupDefinitions = createDefinitionsFromTree();

                    // Store the tree's current expansion state
                    String expState = getExpansionState();

                    // Rebuild the tree based on the filter selection
                    buildTree(typeFilterChkBx.isSelected(),
                              appFilterChkBx.isSelected(),
                              scheduleRate,
                              isApplicationOnly,
                              parent);

                    final List<String> topNodePrefixes = new ArrayList<String>();

                    // Check if filtering by application is in effect
                    if (appFilterChkBx.isSelected())
                    {
                        // Step through each node immediately below the root node; these are the
                        // Application and Other nodes
                        for (int index = 0; index < root.getChildCount(); index++)
                        {
                            // Step through each node immediately below the Application and Other
                            // nodes
                            for (int subIndex = 0; subIndex < root.getChildAt(index).getChildCount(); subIndex++)
                            {
                                // Add the node name to the list of prefixes
                                topNodePrefixes.add("["
                                                    + root.getUserObject()
                                                    + ", "
                                                    + ((ToolTipTreeNode) root.getChildAt(index)).getUserObject()
                                                    + ", "
                                                    + ((ToolTipTreeNode) root.getChildAt(index).getChildAt(subIndex)).getUserObject());
                            }
                        }

                        // Check if the tree is completely collapsed
                        if (expState.isEmpty())
                        {
                            // Set the expansion state to show the Application and Other nodes
                            expState = "[, " + APP_NODE + "], [, " + OTHER_NODE + "], ";
                        }
                        // The tree is expanded to some degree
                        else
                        {
                            // Insert Application and Other nodes names into the expansion paths
                            expState = expState.replaceAll("\\[, ", "[, " + APP_NODE + ", ")
                                       + " "
                                       + expState.replaceAll("\\[, ", "[, " + OTHER_NODE + ", ");
                        }
                    }
                    // Filtering by application is not in effect
                    else
                    {
                        // Remove Application and Other nodes names from the expansion paths
                        expState = expState.replaceAll("\\[, " + APP_NODE + ", ", "[, ")
                                           .replaceAll("\\[, " + OTHER_NODE + ", ", "[, ");
                    }

                    // Adjust the expansion state to account for the change in filtering
                    expState = adjustExpansionState(expState,
                                                    appFilterChkBx.isSelected(),
                                                    true,
                                                    typeFilterChkBx.isSelected(),
                                                    false,
                                                    true,
                                                    topNodePrefixes,
                                                    groupHandler,
                                                    tableTypeHandler);

                    // Check if filtering by application is in effect
                    if (appFilterChkBx.isSelected())
                    {
                        // Add the Application and Other nodes to the expansion path
                        expState = "[, " + APP_NODE + "], [, " + OTHER_NODE + "], " + expState;
                    }

                    // Restore the expansion state
                    setExpansionState(expState);
                }
            });
        }

        return treePnl;
    }
}
