/**
 * CFS Command and Data Dictionary table tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.ALL_TABLES_GROUP_NODE_NAME;
import static CCDD.CcddConstants.DEFAULT_INSTANCE_NODE_NAME;
import static CCDD.CcddConstants.DEFAULT_PROTOTYPE_NODE_NAME;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.INVALID_TEXT_COLOR;
import static CCDD.CcddConstants.LINKED_VARIABLES_NODE_NAME;
import static CCDD.CcddConstants.UNLINKED_VARIABLES_NODE_NAME;
import static CCDD.CcddConstants.TableMemberType.INCLUDE_PRIMITIVES;
import static CCDD.CcddConstants.TableMemberType.TABLES_ONLY;
import static CCDD.CcddConstants.TableTreeType.COMMAND_TABLES;
import static CCDD.CcddConstants.TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES;
import static CCDD.CcddConstants.TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES;
import static CCDD.CcddConstants.TableTreeType.INSTANCE_TABLES;
import static CCDD.CcddConstants.TableTreeType.INSTANCE_TABLES_WITH_PRIMITIVES;
import static CCDD.CcddConstants.TableTreeType.PROTOTYPE_STRUCTURES;
import static CCDD.CcddConstants.TableTreeType.PROTOTYPE_TABLES;
import static CCDD.CcddConstants.TableTreeType.STRUCTURES_WITH_PRIMITIVES;
import static CCDD.CcddConstants.TableTreeType.STRUCTURE_TABLES;
import static CCDD.CcddConstants.TableTreeType.TABLES_WITH_PRIMITIVES;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableMembers;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary table tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableTreeHandler extends CcddCommonTreeHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddGroupHandler groupHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;

    // Components referenced by multiple methods
    private List<TableMembers> tableMembers;
    private List<Object[]> tablePathList;
    private ToolTipTreeNode root;
    private final TableTreeType treeType;
    private JCheckBox expandChkBx;

    // Flag that indicates if the table tree child structures should be sorted by variable name
    private final boolean sortByName;

    // Names of the prototype and instance tree nodes
    private final String prototypeNodeName;
    private final String instanceNodeName;

    // Flag to indicate if the tree should be filtered by group
    private boolean isByGroup;

    // Flag to indicate if the tree should be filtered by table type
    private boolean isByType;

    // Flag indicating if the node descriptions should be obtained and added as tool tips
    private final boolean getDescriptions;

    // Flags indicating if the filter check boxes should be displayed
    private final boolean showGroupFilter;
    private final boolean showTypeFilter;

    // Data stream rate column name and rate value used to filter the table tree for variables with
    // rates
    private String rateName;
    private String rateFilter;

    // Index into the rate table member rate parameters
    private int rateIndex;

    // Table descriptions from the custom values table
    private String[][] tableDescriptions;

    // List containing the selected variable paths
    private List<Object[]> selectedVariablePaths;

    // Flag to indicate if the table tree is being built
    private boolean isBuilding;

    // Storage for a child table referenced in its parent path
    private String recursionTable;

    // List of nodes that don't meet the table filtering criteria
    private final List<ToolTipTreeNode> removeNodes;

    // List of variables to be excluded from the tree
    private List<String> excludedVariables;

    // List of linked variables in the link tree
    private List<String> linkedVariables;

    // Flag that indicates if a hidden check box should be placed under the filter check boxes for
    // alignment purposes with an adjacent tree
    private final boolean addHiddenCheckBox;

    // List containing variable paths from the custom values table that match the current rate
    // column name and rate value
    private ArrayListMultiple rateValues;

    // Flag to indicate if any errors when building the tree are annunciated via a warning dialog
    private final boolean isSilent;

    // Flag to indicate if the tree should display both prototype and instances nodes, only the
    // prototype node, or only the instance node
    private TableTreeNodeFilter nodeFilter;

    // Filter to indicate if the tree should display both prototype and instances nodes, only the
    // prototype node, or only the instance node
    private enum TableTreeNodeFilter
    {
        ALL,
        PROTOTYPE_ONLY,
        INSTANCE_ONLY
    }

    private final int GROUP_HEADER_LEVEL = 2;

    /**********************************************************************************************
     * Table tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param groupHandler
     *            group handler
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param getDescriptions
     *            true if the node descriptions are to be added as tool tips
     *
     * @param sortByName
     *            true to sort the child structures by variable name; false to show in the order
     *            defined in the structure
     *
     * @param showGroupFilter
     *            true to display the group filter check box
     *
     * @param showTypeFilter
     *            true to display the type filter check box
     *
     * @param addHiddenCheckbox
     *            true to add a hidden check box under the filter check boxes for alignment
     *            purposes
     *
     * @param rateName
     *            data stream rate column name used to filter the table tree for variables with
     *            rates
     *
     * @param rateFilter
     *            data rate used to filter the table tree for variables with rates
     *
     * @param excludedVariables
     *            list of node paths to be excluded from the table; null or empty list if no
     *            exclusions
     *
     * @param isSilent
     *            true if any errors when building the tree are not annunciated via a warning
     *            dialog
     *
     * @param prototypeNodeName
     *            name of the prototype node; null to set the node to which the prototype node
     *            would normally belong as the prototype node
     *
     * @param instanceNodeName
     *            name of the instance node; null to set node to which the instance node would
     *            normally belong as the instance node
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain,
                         CcddGroupHandler groupHandler,
                         TableTreeType treeType,
                         boolean getDescriptions,
                         boolean sortByName,
                         boolean showGroupFilter,
                         boolean showTypeFilter,
                         boolean addHiddenCheckbox,
                         String rateName,
                         String rateFilter,
                         List<String> excludedVariables,
                         boolean isSilent,
                         String prototypeNodeName,
                         String instanceNodeName,
                         Component parent)
    {
        super(ccddMain);

        this.ccddMain = ccddMain;
        this.groupHandler = groupHandler;
        this.treeType = treeType;
        this.getDescriptions = getDescriptions;
        this.sortByName = sortByName;
        this.showGroupFilter = showGroupFilter;
        this.showTypeFilter = showTypeFilter;
        this.addHiddenCheckBox = addHiddenCheckbox;
        this.rateName = rateName;
        this.rateFilter = rateFilter;
        this.excludedVariables = excludedVariables;
        this.isSilent = isSilent;
        this.prototypeNodeName = prototypeNodeName;
        this.instanceNodeName = instanceNodeName;
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();

        // Set the tree to be collapsed initially with no filters applied
        isByGroup = false;
        isByType = false;

        removeNodes = new ArrayList<ToolTipTreeNode>();

        // Get the table information from the database and use it to build the table tree
        buildTableTreeFromDatabase(parent);
    }

    /**********************************************************************************************
     * Table tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param groupHandler
     *            group handler
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param showGroupFilter
     *            true to display the group filter check box
     *
     * @param addHiddenCheckbox
     *            true to add a hidden check box under the filter check boxes for alignment
     *            purposes
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain,
                         CcddGroupHandler groupHandler,
                         TableTreeType treeType,
                         boolean showGroupFilter,
                         boolean addHiddenCheckbox,
                         Component parent)
    {
        // Build the table tree
        this(ccddMain,
             groupHandler,
             treeType,
             true,
             true,
             showGroupFilter,
             true,
             addHiddenCheckbox,
             null,
             null,
             null,
             false,
             DEFAULT_PROTOTYPE_NODE_NAME,
             DEFAULT_INSTANCE_NODE_NAME,
             parent);
    }

    /**********************************************************************************************
     * Table tree handler class constructor. Get just the tree information of the specified type
     *
     * @param ccddMain
     *            main class
     *
     * @param groupHandler
     *            group handler
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param rateName
     *            rate column name used to filter the table tree for variables with rates
     *
     * @param rateFilter
     *            data rate used to filter the table tree for variables with rates
     *
     * @param excludedVariables
     *            list of node paths to be excluded from the table; null or empty list if no
     *            exclusions
     *
     * @param prototypeNodeName
     *            name of the prototype node; null to set the node to which the prototype node
     *            would normally belong as the prototype node
     *
     * @param instanceNodeName
     *            name of the instance node; null to set node to which the instance node would
     *            normally belong as the instance node
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain,
                         CcddGroupHandler groupHandler,
                         TableTreeType treeType,
                         String rateName,
                         String rateFilter,
                         List<String> excludedVariables,
                         String prototypeNodeName,
                         String instanceNodeName,
                         Component parent)
    {
        // Build the table tree
        this(ccddMain,
             groupHandler,
             treeType,
             true,
             false,
             true,
             false,
             false,
             rateName,
             rateFilter,
             excludedVariables,
             false,
             prototypeNodeName,
             instanceNodeName,
             parent);
    }

    /**********************************************************************************************
     * Table tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param groupHandler
     *            group handler
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param prototypeNodeName
     *            name of the prototype node; null to set the node to which the prototype node
     *            would normally belong as the prototype node
     *
     * @param instanceNodeName
     *            name of the instance node; null to set node to which the instance node would
     *            normally belong as the instance node
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain,
                         CcddGroupHandler groupHandler,
                         TableTreeType treeType,
                         String prototypeNodeName,
                         String instanceNodeName,
                         Component parent)
    {
        // Build the table tree
        this(ccddMain,
             groupHandler,
             treeType,
             true,
             true,
             true,
             true,
             false,
             null,
             null,
             null,
             false,
             prototypeNodeName,
             instanceNodeName,
             parent);
    }

    /**********************************************************************************************
     * Table tree handler class constructor. Get just the tree information of the specified type.
     * Structure variable tables and primitives retain their order of appearance in the table's
     * definition
     *
     * @param ccddMain
     *            main class
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param isSilent
     *            true if any errors when building the tree are not annunciated via a warning
     *            dialog
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain,
                         TableTreeType treeType,
                         boolean isSilent,
                         Component parent)
    {
        // Build the table tree
        this(ccddMain,
             null,
             treeType,
             false,
             false,
             false,
             false,
             false,
             null,
             null,
             null,
             isSilent,
             DEFAULT_PROTOTYPE_NODE_NAME,
             DEFAULT_INSTANCE_NODE_NAME,
             parent);
    }

    /**********************************************************************************************
     * Table tree handler class constructor. Get just the tree information of the specified type.
     * Structure variable tables and primitives retain their order of appearance in the table's
     * definition
     *
     * @param ccddMain
     *            main class
     *
     * @param treeType
     *            table tree type (TableTreeType)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableTreeHandler(CcddMain ccddMain, TableTreeType treeType, Component parent)
    {
        // Build the table tree
        this(ccddMain,
             null,
             treeType,
             false,
             false,
             false,
             false,
             false,
             null,
             null,
             null,
             false,
             DEFAULT_PROTOTYPE_NODE_NAME,
             DEFAULT_INSTANCE_NODE_NAME,
             parent);
    }

    /**********************************************************************************************
     * Get the table tree root node
     *
     * @return Table tree root node
     *********************************************************************************************/
    protected ToolTipTreeNode getRootNode()
    {
        return root;
    }

    /**********************************************************************************************
     * Get the status of the group filter
     *
     * @return true if the group filter is enabled
     *********************************************************************************************/
    protected boolean isFilteredByGroup()
    {
        return isByGroup;
    }

    /**********************************************************************************************
     * Get the first node index that represents a table. This skips the database (root),
     * prototype/instance, group (if filtered by group), and type (if filtered by type) nodes
     *
     * @return First node index for a table
     *********************************************************************************************/
    @Override
    protected int getHeaderNodeLevel()
    {
        return 2 + (isByGroup ? 1 : 0) + (isByType ? 1 : 0);
    }

    /**********************************************************************************************
     * Replace the list of linked variables with the list provided
     *
     * @param linkedVars
     *            list of linked variables
     *********************************************************************************************/
    protected void setLinkedVariables(List<String> linkedVars)
    {
        linkedVariables.clear();
        linkedVariables.addAll(linkedVars);
    }

    /**********************************************************************************************
     * Set the list of excluded variables and update the node enable states
     *
     * @param excludedVariables
     *            list of variables to be excluded from the tree
     *********************************************************************************************/
    protected void setExcludedVariables(List<String> excludedVariables)
    {
        this.excludedVariables = excludedVariables;

        // Set the node enable state (by setting the node name color) based on whether or not the
        // name is in the exclusion list
        setNodeEnableByExcludeList();

        // Set the node enable state (by setting the node name color) based on whether or not all
        // of the children of the node are disabled
        setNodeEnableByChildState(root);
    }

    /**********************************************************************************************
     * Override the table tree's tool tip text handler to provide the descriptions of the nodes
     *********************************************************************************************/
    @Override
    public String getToolTipText(MouseEvent me)
    {
        String toolTip = null;

        // Check if the mouse pointer is over a node
        if (getRowForLocation(me.getX(), me.getY()) != -1)
        {
            // Get the tree path and the tool tip text associated with it
            TreePath curPath = getPathForLocation(me.getX(), me.getY());
            toolTip = ((ToolTipTreeNode) curPath.getLastPathComponent()).getToolTipText();

            // Check if the tool tip text is blank
            if (toolTip != null && toolTip.isEmpty())
            {
                // Set the tool tip text to null so that nothing is displayed
                toolTip = null;
            }
        }

        return toolTip;
    }

    /**********************************************************************************************
     * Set the group handler reference
     *
     * @param groupHandler
     *            reference to the group handler
     *********************************************************************************************/
    protected void setGroupHandler(CcddGroupHandler groupHandler)
    {
        this.groupHandler = groupHandler;
    }

    /**********************************************************************************************
     * Load the table information from the database and (re)build the table tree
     *
     * @param parent
     *            component building this table tree
     *********************************************************************************************/
    protected void buildTableTreeFromDatabase(Component parent)
    {
        // Get the tables and their members from the database, sorted by variable name
        tableMembers = dbTable.loadTableMembers((treeType == TABLES_WITH_PRIMITIVES
                                                 || treeType == STRUCTURES_WITH_PRIMITIVES
                                                 || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES
                                                 || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES
                                                 || treeType == INSTANCE_TABLES_WITH_PRIMITIVES)
                                                                                                 ? INCLUDE_PRIMITIVES
                                                                                                 : TABLES_ONLY,
                                                sortByName,
                                                parent);

        // Register the tool tip manager for the table tree (otherwise the tool tips aren't
        // displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Check that the table members loaded successfully
        if (tableMembers != null)
        {
            linkedVariables = new ArrayList<String>();

            // Store the tree's current expansion state
            String expState = getExpansionState();

            // Build the table tree
            buildTableTree(null, rateName, rateFilter, parent);

            // Restore the tree's expansion state
            setExpansionState(expState);
        }
    }

    /**********************************************************************************************
     * (Re)build the table tree from the currently table information
     *
     * @param isExpanded
     *            true if all tree nodes should be expanded, false to collapse all nodes, and null
     *            to use the current status of the expansion check box (if present; if not present
     *            then use false)
     *
     * @param rateName
     *            rate column name used to filter the table tree for variables with rates; null if
     *            the tree is not filtered by data rate
     *
     * @param rateFilter
     *            data rate used to filter the table tree for variables with rates; null if the
     *            tree is not filtered by data rate
     *
     * @param parent
     *            component building this table tree
     *********************************************************************************************/
    protected void buildTableTree(Boolean isExpanded,
                                  String rateName,
                                  String rateFilter,
                                  Component parent)
    {
        this.rateName = rateName;
        this.rateFilter = rateFilter;

        // Check if a rate filter is in effect and a filter name is provided
        if (rateFilter != null && rateName != null)
        {
            // Load all references to rate column values from the custom values table that match
            // the rate name
            rateValues = new ArrayListMultiple();
            rateValues.addAll(dbTable.getCustomValues(rateName, null, parent));
        }

        // Get the index into the table member rate array
        rateIndex = ccddMain.getRateParameterHandler().getRateInformationIndexByRateName(rateName);

        // Set the flag to indicate that the table tree is being built. This flag is used to
        // inhibit actions involving tree selection value changes during the build process
        isBuilding = true;

        // Create the tree's root node using the database name and hide the root node (project
        // database name). Since the root node isn't visible there is no need for a description
        root = new ToolTipTreeNode(dbControl.getDatabaseName(), null);
        setModel(new DefaultTreeModel(root));
        setRootVisible(false);

        // Set the flag to indicate if all nodes, only the prototype node, or only the instance
        // node should be built and displayed
        nodeFilter = ((treeType == PROTOTYPE_TABLES
                       || treeType == PROTOTYPE_STRUCTURES
                       || treeType == COMMAND_TABLES)
                                                      ? TableTreeNodeFilter.PROTOTYPE_ONLY
                                                      : (treeType == INSTANCE_TABLES
                                                         || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES
                                                         || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES
                                                         || treeType == INSTANCE_TABLES_WITH_PRIMITIVES
                                                                                                        ? TableTreeNodeFilter.INSTANCE_ONLY
                                                                                                        : TableTreeNodeFilter.ALL));

        // Check if both groups and table type are to be used to filter the table tree
        if (isByGroup && isByType)
        {
            // Step through the groups
            for (GroupInformation groupInfo : groupHandler.getGroupInformation())
            {
                // Create a node for the group and add it to the tree's root node
                ToolTipTreeNode groupNode = new ToolTipTreeNode(groupInfo.getName(),
                                                                getDescriptions
                                                                                ? groupInfo.getDescription()
                                                                                : null);
                root.add(groupNode);

                // Add the group member tables to the group node by table type
                addByType(groupNode, groupInfo, parent);
            }

            // Add the pseudo-group containing all tables to the prototype and instance nodes
            addAllTablesGroup(parent);
        }
        // Check if groups are to be used to filter the table tree
        else if (isByGroup)
        {
            // Step through the groups
            for (GroupInformation groupInfo : groupHandler.getGroupInformation())
            {
                ToolTipTreeNode prototype = null;
                ToolTipTreeNode instance = null;

                // Create a node for the group and add it to the tree's root node
                ToolTipTreeNode groupNode = new ToolTipTreeNode(groupInfo.getName(),
                                                                getDescriptions
                                                                                ? groupInfo.getDescription()
                                                                                : null);
                root.add(groupNode);

                // Check if the prototype node should be displayed
                if (nodeFilter != TableTreeNodeFilter.INSTANCE_ONLY)
                {
                    // Check if the prototype node name is provided
                    if (prototypeNodeName != null)
                    {
                        // Add the prototype node to the group's node
                        prototype = new ToolTipTreeNode(prototypeNodeName,
                                                        treeType == PROTOTYPE_TABLES
                                                                                     ? "Prototype tables"
                                                                                     : (treeType == COMMAND_TABLES
                                                                                                                   ? "Command tables"
                                                                                                                   : "Prototype structure tables"));
                        groupNode.add(prototype);
                    }
                    // No prototype node name is provided
                    else
                    {
                        // Assign the nodes that normally go in the prototype node to the group
                        // node
                        prototype = groupNode;
                    }
                }

                // Check if the instance node should be displayed
                if (nodeFilter != TableTreeNodeFilter.PROTOTYPE_ONLY)
                {
                    // Check if the instance node name is provided
                    if (instanceNodeName != null)
                    {
                        // Add the instance node to the group's node
                        instance = new ToolTipTreeNode(instanceNodeName,
                                                       treeType == INSTANCE_TABLES
                                                                                   ? "Parent and children tables"
                                                                                   : "Parent and children tables, with variables");
                        groupNode.add(instance);
                    }
                    // No instance node name is provided
                    else
                    {
                        // Assign the nodes that normally go in the instance node to the group node
                        instance = groupNode;
                    }
                }

                // Build the top-level nodes filtered by group
                buildTopLevelNodes(groupInfo.getTableMembers(), instance, prototype, parent);
            }

            // Add the pseudo-group containing all tables to the prototype and instance nodes
            addAllTablesGroup(parent);
        }
        // Check if the table types are to be used to filter the table tree
        else if (isByType)
        {
            // Add all tables to the prototype and instances nodes by table type
            addByType(null, null, parent);
        }
        // Do not use the groups or types to filter the tree
        else
        {
            ToolTipTreeNode prototype = null;
            ToolTipTreeNode instance = null;

            // Check if the prototype node should be displayed
            if (nodeFilter != TableTreeNodeFilter.INSTANCE_ONLY)
            {
                // Check if the prototype node name is provided
                if (prototypeNodeName != null)
                {
                    // Add the prototype node to the root node
                    prototype = new ToolTipTreeNode(prototypeNodeName,
                                                    treeType == PROTOTYPE_TABLES
                                                                                 ? "Prototype tables"
                                                                                 : (treeType == COMMAND_TABLES
                                                                                                               ? "Command tables"
                                                                                                               : "Prototype structure tables"));
                    root.add(prototype);
                }
                // No prototype node name is provided
                else
                {
                    // Assign the nodes that normally go in the prototype node to the root node
                    prototype = root;
                }
            }

            // Check if the instance node should be displayed
            if (nodeFilter != TableTreeNodeFilter.PROTOTYPE_ONLY)
            {
                // Check if the instance node name is provided
                if (instanceNodeName != null)
                {
                    // Add the instance node to the group's node
                    instance = new ToolTipTreeNode(instanceNodeName,
                                                   treeType == INSTANCE_TABLES
                                                                               ? "Parent and children tables"
                                                                               : "Parent and children tables, with variables");
                    root.add(instance);
                }
                // No instance node name is provided
                else
                {
                    // Assign the nodes that normally go in the instance node to the root node
                    instance = root;
                }
            }

            // Build the root's top-level nodes
            buildTopLevelNodes(null, instance, prototype, parent);
        }

        // Check if the expansion check box exists
        if (expandChkBx != null)
        {
            // Check is the expansion state is not specified
            if (isExpanded == null)
            {
                // Set the expansion state to the current expansion check box state
                isExpanded = expandChkBx.isSelected();
            }
            // The expansion state is specified
            else
            {
                // Update the expansion check box state to match the specified expansion state
                expandChkBx.setSelected(isExpanded);
            }
        }
        // Check is the expansion state is not specified
        else if (isExpanded == null)
        {
            // Set the state to collapse the tree
            isExpanded = false;
        }

        // Force the root node to draw with the node additions
        ((DefaultTreeModel) treeModel).nodeStructureChanged(root);

        // Expand or collapse the tree based on the expansion flag
        setTreeExpansion(isExpanded);

        // Set the node enable states based on the presence of child nodes
        setNodeEnableByChildState(root);

        // Check if groups are to be used to filter the table tree
        if (isByGroup)
        {
            // Step through the groups
            for (GroupInformation groupInfo : groupHandler.getGroupInformation())
            {
                // Check if the group has no tables,but does have at least one data field
                if (groupInfo.getTableMembers().size() == 0
                    && ccddMain.getFieldHandler().getFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(groupInfo.getName())).size() != 0)
                {
                    // The node is disabled due to having no children. Re-enable the node since it
                    // does have data fields
                    getNodeByNodeName(groupInfo.getName()).setUserObject(groupInfo.getName());
                }
            }
        }

        // Clear the flag that indicates the table tree is being built
        isBuilding = false;
    }

    /**********************************************************************************************
     * Add the pseudo-group containing all tables to the specified prototype and instance nodes
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void addAllTablesGroup(Component parent)
    {
        ToolTipTreeNode protoAllNode = null;
        ToolTipTreeNode instAllNode = null;

        // Add the 'All tables' group node to the root node
        ToolTipTreeNode allTablesNode = new ToolTipTreeNode("<html><i>"
                                                            + ALL_TABLES_GROUP_NODE_NAME,
                                                            "Group containing all tables");
        root.add(allTablesNode);

        // Check if the tree is filtered by table type
        if (isByType)
        {
            // Build the all tables node, filtered by table type
            addByType(allTablesNode, null, parent);
        }
        // The tree is only filtered by group
        else
        {
            // Check if the prototype node should be displayed
            if (nodeFilter != TableTreeNodeFilter.INSTANCE_ONLY)
            {
                // Add the prototype node to the all tables node
                protoAllNode = new ToolTipTreeNode(prototypeNodeName,
                                                   treeType == PROTOTYPE_TABLES
                                                                                ? "Prototype tables"
                                                                                : (treeType == COMMAND_TABLES
                                                                                                              ? "Command tables"
                                                                                                              : "Prototype structure tables"));
                allTablesNode.add(protoAllNode);
            }

            // Check if the instance node should be displayed
            if (nodeFilter != TableTreeNodeFilter.PROTOTYPE_ONLY)
            {
                // Check if the instance node name is provided
                if (instanceNodeName != null)
                {
                    // Add the instance node to the all tables node
                    instAllNode = new ToolTipTreeNode(instanceNodeName,
                                                      treeType == INSTANCE_TABLES
                                                                                  ? "Parent and children tables"
                                                                                  : "Parent and children tables, with variables");
                    allTablesNode.add(instAllNode);
                }
                // No instance node name is provided
                else
                {
                    // Assign the nodes that normally go in the instance node to the all tables
                    // node
                    instAllNode = allTablesNode;
                }
            }

            // Build the all tables node
            buildTopLevelNodes(null, instAllNode, protoAllNode, parent);
        }
    }

    /**********************************************************************************************
     * Add tables to the specified prototype and instance nodes based on table type
     *
     * @param groupNode
     *            group node if filtering by group; null if not filtering by group
     *
     * @param groupInfo
     *            reference to the group information is filtering also by group; null if not
     *            filtering by group
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void addByType(ToolTipTreeNode groupNode,
                           GroupInformation groupInfo,
                           Component parent)
    {
        // Step through each table type
        for (String type : tableTypeHandler.getTableTypeNames())
        {
            List<String> validTables = new ArrayList<String>();

            // Step through each table
            for (TableMembers member : tableMembers)
            {
                // Check if the table is of the current type
                if (type.equals(member.getTableType()))
                {
                    // Check if group filtering isn't in effect
                    if (groupInfo == null)
                    {
                        // Add the table name to the list for this type
                        validTables.add(member.getTableName());
                    }
                    // Group filtering is in effect
                    else
                    {
                        // Step through each table belonging to the group
                        for (String grpTbl : groupInfo.getTableMembers())
                        {
                            // Check if the prototype name of the table matching the current type
                            // matches the group table's prototype table name; that is, the group
                            // table is of the matching table type
                            if (member.getTableName().equals(TableInformation.getPrototypeName(grpTbl))
                                && !validTables.contains(grpTbl))
                            {
                                // Add the group table name to the list for this type
                                validTables.add(grpTbl);

                                // Check if the table's prototype isn't already in the list
                                if (!validTables.contains(member.getTableName()))
                                {
                                    // Add the prototype of the group member to the list
                                    validTables.add(member.getTableName());
                                }
                            }
                        }
                    }
                }
            }

            // Create the table type node
            ToolTipTreeNode typeNode = new ToolTipTreeNode(type,
                                                           getDescriptions
                                                                           ? tableTypeHandler.getTypeDefinition(type)
                                                                                             .getDescription()
                                                                           : null);

            ToolTipTreeNode prototype = null;
            ToolTipTreeNode instance = null;

            // Check if the prototype node should be displayed
            if (nodeFilter != TableTreeNodeFilter.INSTANCE_ONLY)
            {
                // Check if the prototype node name is provided
                if (prototypeNodeName != null)
                {
                    // Add the prototype node to the group's node
                    prototype = new ToolTipTreeNode(prototypeNodeName,
                                                    treeType == COMMAND_TABLES
                                                                               ? "Command tables"
                                                                               : "Prototype tables");
                    typeNode.add(prototype);
                }
                // No prototype node name is provided
                else
                {
                    // Assign the nodes that normally go in the prototype node to the type node
                    prototype = typeNode;
                }
            }

            // Check if the instance node should be displayed
            if (nodeFilter != TableTreeNodeFilter.PROTOTYPE_ONLY)
            {
                // Check if the instance node name is provided
                if (prototypeNodeName != null)
                {
                    // Add the instance node to the group's node
                    instance = new ToolTipTreeNode(instanceNodeName,
                                                   treeType == INSTANCE_TABLES
                                                                               ? "Parent and children tables"
                                                                               : "Parent and children tables, with variables");
                    typeNode.add(instance);
                }
                // No instance node name is provided
                else
                {
                    // Assign the nodes that normally go in the instance node to the type node
                    instance = typeNode;
                }
            }

            // Check if a group node is supplied (i.e., filtering by group is active)
            if (groupNode != null)
            {
                // Add the table type node to the group node, then add the group node to the root
                // node
                groupNode.add(typeNode);
            }
            // Not filtering by group
            else
            {
                // Add the table type node to the root node
                root.add(typeNode);
            }

            // Build the prototype and instance nodes filtered by type (and group, if applicable).
            buildTopLevelNodes(validTables, instance, prototype, parent);
        }
    }

    /**********************************************************************************************
     * Starting with the specified node, traverse down its branch and flag as invalid any node
     * where the name doesn't match one in the supplied list of tables. The match criteria accounts
     * for filtering by type, by group, or by both type and group. A list of nodes to remove is
     * created, based on the match criteria and the filter(s). This is a recursive method
     *
     * @param validTables
     *            list of table names belonging to the filtered selection; null if no filtering
     *
     * @param node
     *            node to test for validity
     *
     * @return true if the supplied node has a descendant that's in the valid tables list
     *********************************************************************************************/
    private boolean setInvalidNodesAndTrim(List<String> validTables, ToolTipTreeNode node)
    {
        boolean isValid = true;
        List<ToolTipTreeNode> removeChildNodes = new ArrayList<ToolTipTreeNode>();
        String nodeName = node.getUserObject().toString();

        // Check if this node represents a table and the root/parent isn't in the list of valid
        // tables
        if (node.getLevel() >= getHeaderNodeLevel()
            && ((isByType && !validTables.contains(nodeName.split("\\.")[0]))
                || (!isByType && isByGroup && !validTables.contains(nodeName))))
        {
            // Flag the node as invalid and set the invalid flag
            node.setUserObject(INVALID_TEXT_COLOR + nodeName);
            isValid = false;
        }

        // Check if the tree is filtered by type, or isn't filtered by group, or if it is filtered
        // by group that the node is valid (if invalid then all descendants are invalid for the
        // group as well)
        if (isByType || !isByGroup || isValid)
        {
            // Step through the node's children
            for (Enumeration<?> element = node.children(); element.hasMoreElements();)
            {
                // Get the reference to the child node
                ToolTipTreeNode childNode = (ToolTipTreeNode) element.nextElement();

                // Check if the child has no descendant in the valid tables list
                if (!setInvalidNodesAndTrim(validTables, childNode))
                {
                    // Add the child to the list of nodes to remove
                    removeChildNodes.add(childNode);
                }
                // The child has a valid descendant
                else
                {
                    // Set the flag to indicate the node has a descendant in the valid tables list
                    isValid = true;
                }
            }
        }

        // Check if the node has no descendant in the valid tables list and this node represents a
        // table
        if (!isValid && node.getLevel() >= getHeaderNodeLevel())
        {
            // Add the node to the list of nodes to remove (this removes all of its descendants as
            // well)
            removeNodes.add(node);
        }
        // Check if the node has any invalid descendants
        else if (!removeChildNodes.isEmpty())
        {
            // Add the invalid child nodes to the list of nodes to remove
            removeNodes.addAll(removeChildNodes);
        }

        return isValid;
    }

    /**********************************************************************************************
     * Build the top-level nodes for the table tree (based on the selected filters). Remove any
     * nodes that are flagged for removal based on the valid tables criteria and the type/group
     * filters (if these are enabled)
     *
     * @param validTables
     *            list of table names belonging to the filtered selection; null if no filtering
     *
     * @param instNode
     *            parent node for the top-level nodes
     *
     * @param protoNode
     *            parent node for the prototype nodes
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void buildTopLevelNodes(List<String> validTables,
                                    ToolTipTreeNode instNode,
                                    ToolTipTreeNode protoNode,
                                    Component parent)
    {
        // Check if the descriptions are needed (i.e., if building a visible table tree) and
        // haven't already been loaded
        if (getDescriptions && tableDescriptions == null)
        {
            // Get an array containing the tables and their variable paths, if any, for those
            // tables with descriptions
            tableDescriptions = dbTable.queryTableDescriptions(parent);
        }

        // Step through each table
        for (TableMembers member : tableMembers)
        {
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(member.getTableType());

            // Set the flag if the member represents a structure
            boolean isStructure = typeDefn.isStructure();

            // Set the flag if the member represents a command
            boolean isCommand = typeDefn.isCommand();

            // Check if the member meets the criteria for inclusion in the tree: (1) structures-
            // only or structures-with-primitives-only is specified and the member is a structure,
            // or (2) commands-only is specified and the member is a command
            if (((treeType != STRUCTURE_TABLES
                  && treeType != STRUCTURES_WITH_PRIMITIVES
                  && treeType != INSTANCE_STRUCTURES_WITH_PRIMITIVES
                  && treeType != INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES)
                 || isStructure)
                && (treeType != COMMAND_TABLES || isCommand))
            {
                // Check if the member meets the criteria for inclusion in the prototypes node: (1)
                // this isn't the special structures with primitives tree type (normal prototype
                // nodes are excluded if it is), (2) prototype-structures-only or structures-only
                // is specified and the member is a structure, or (3) commands-only is specified
                // and the member is a command
                if (treeType != STRUCTURES_WITH_PRIMITIVES
                    && nodeFilter != TableTreeNodeFilter.INSTANCE_ONLY
                    && ((treeType != PROTOTYPE_STRUCTURES && treeType != STRUCTURE_TABLES)
                        || isStructure)
                    && (treeType != COMMAND_TABLES || isCommand))
                {
                    // Add the table to the prototype node
                    protoNode.add(new ToolTipTreeNode(member.getTableName(),
                                                      getDescriptions
                                                                      ? getTableDescription(member.getTableName(),
                                                                                            "")
                                                                      : null));
                }

                // Check if the member meets the criteria for inclusion in the instances tree: the
                // tree type isn't only for prototype tables, only for prototype structures, or
                // only for commands
                if (treeType != PROTOTYPE_TABLES
                    && treeType != PROTOTYPE_STRUCTURES
                    && treeType != COMMAND_TABLES)
                {
                    boolean isRoot = true;

                    // Step through each table
                    for (TableMembers otherMember : tableMembers)
                    {
                        // Check if the current table has this table as a member, that the table
                        // isn't referencing itself, and, if the tree is filtered by group, that
                        // this table is a member of the group
                        if (otherMember.getDataTypes().contains(member.getTableName())
                            && !member.equals(otherMember))
                        {
                            // Clear the flag indicating this is a root table and stop searching
                            isRoot = false;
                            break;
                        }
                    }

                    // Check if this is a root table or the special structures with primitives tree
                    // type. For the latter child nodes are created for non-root tables and placed
                    // in the prototype node
                    if (isRoot || treeType == STRUCTURES_WITH_PRIMITIVES)
                    {
                        recursionTable = null;

                        // Build the nodes in the tree for this table and its member tables
                        buildNodes(member,
                                   (!isRoot && treeType == STRUCTURES_WITH_PRIMITIVES
                                                                                      ? protoNode
                                                                                      : instNode),
                                   new ToolTipTreeNode(member.getTableName(),
                                                       getDescriptions
                                                                       ? getTableDescription(member.getTableName(),
                                                                                             "")
                                                                       : null));

                        // Check if a recursive reference was detected and that warning dialogs
                        // aren't suppressed
                        if (recursionTable != null && !isSilent)
                        {
                            // Inform the user that the table has a recursive reference
                            new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>Table '</b>"
                                                                              + member.getTableName()
                                                                              + "<b>' contains a recursive reference to '</b>"
                                                                              + recursionTable
                                                                              + "<b>'",
                                                                      "Table Reference",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                    }
                }
            }
        }

        // Check if a list of table names is provided for filtering the tree's branches
        if (validTables != null)
        {
            removeNodes.clear();

            // Flag any nodes that aren't in the filter list as invalid. If a branch has no valid
            // nodes then remove it
            setInvalidNodesAndTrim(validTables,
                                   (protoNode != null
                                                      ? (ToolTipTreeNode) protoNode.getParent()
                                                      : (ToolTipTreeNode) instNode.getParent()));

            // Step through the list of nodes to remove (if any) - these are invalid nodes with no
            // children or nodes that have no valid descendants
            for (ToolTipTreeNode removeNode : removeNodes)
            {
                // Remove the node from the tree
                removeNode.removeFromParent();
            }
        }
    }

    /**********************************************************************************************
     * Build the table tree nodes. This is a recursive method. In order to prevent an infinite
     * loop, a check is made for a child node that exists in its own path; if found the recursion
     * is terminated for that node
     *
     * @param thisMember
     *            TableMember class
     *
     * @param parentNode
     *            current working node for the table tree
     *
     * @param childNode
     *            new child node to add to the working node
     *********************************************************************************************/
    private void buildNodes(TableMembers thisMember,
                            ToolTipTreeNode parentNode,
                            ToolTipTreeNode childNode)
    {
        // Step through each node in the parent's path
        for (TreeNode node : parentNode.getPath())
        {
            // Check if this isn't a header node
            if (((ToolTipTreeNode) node).getLevel() >= getHeaderNodeLevel())
            {
                // Check if the child is in the path
                if (CcddUtilities.removeHTMLTags(((ToolTipTreeNode) node).getUserObject().toString())
                                 .equals(childNode.getUserObject().toString()))
                {
                    // Store the name of the recursively referenced node
                    recursionTable = childNode.getUserObject().toString();
                    break;
                }
            }
        }

        // Check if a recursion error wasn't found (this prevents an infinite loop from occurring)
        if (recursionTable == null)
        {
            // Add the child node to its parent
            parentNode.add(childNode);

            // Get the parent table and variable path for this variable
            String fullTablePath = getFullVariablePath(childNode.getPath());

            // Step through each table/variable referenced by the table member
            for (int memIndex = 0; memIndex < thisMember.getDataTypes().size(); memIndex++)
            {
                // Check if this data type is a primitive
                if (dataTypeHandler.isPrimitive(thisMember.getDataTypes().get(memIndex)))
                {
                    String tablePath = fullTablePath;

                    // Set to true if the variable has a path (i.e., this is not a prototype's
                    // variable)
                    boolean isChildVariable = tablePath.contains(",");

                    // Check if the variable has a path
                    if (isChildVariable)
                    {
                        // Add the data type and variable name to the variable path
                        tablePath += ","
                                     + thisMember.getDataTypes().get(memIndex)
                                     + "."
                                     + thisMember.getVariableNames().get(memIndex);
                    }

                    String rate = null;

                    // Check if a rate filter is in effect
                    if (rateFilter != null)
                    {
                        // Get the rate value for this variable. Use the prototype's value if the
                        // variable doesn't have a specific rate assigned
                        int index = rateValues.indexOf(tablePath);
                        rate = isChildVariable
                               && index != -1
                                              ? rateValues.get(index)[2]
                                              : thisMember.getRates().get(memIndex)[rateIndex];
                    }

                    // Check if no rate filter is in effect or, if not, that the rate matches the
                    // specified rate filter
                    if (rateFilter == null || rate.equals(rateFilter))
                    {
                        // Get the full variable name in the form
                        // data_type.variable_name[:bit_length]
                        String variable = thisMember.getFullVariableNameWithBits(memIndex);

                        // Check that no exclusion list is supplied, or if one is in effect that
                        // the variable, using its full path and name, is not in the exclusion list
                        if (excludedVariables == null || !excludedVariables.contains(tablePath))
                        {
                            // Add the primitive as a node to this child node
                            childNode.add(new ToolTipTreeNode(variable, null));
                        }
                        // The variable is in the exclusion list
                        else
                        {
                            // Add the variable with the node text grayed out
                            childNode.add(new ToolTipTreeNode(DISABLED_TEXT_COLOR + variable, null));
                        }
                    }
                }
                // Data type is not a primitive, it's a structure
                else
                {
                    // Step through the other tables
                    for (TableMembers member : tableMembers)
                    {
                        // Check if the table is a member of the target table
                        if (thisMember.getDataTypes().get(memIndex).equals(member.getTableName()))
                        {
                            // Build the node name from the prototype and variable names
                            String nodeName = thisMember.getDataTypes().get(memIndex)
                                              + "."
                                              + thisMember.getVariableNames().get(memIndex);

                            // Get the variable name path to this node
                            String tablePath = fullTablePath + "," + nodeName;

                            // Add this table to the current table's node. The node name is in the
                            // format 'dataType.variableName<[arrayIndex]>'. If a specific
                            // description exists for the table then use it for the tool tip text;
                            // otherwise use the prototype's description
                            buildNodes(member,
                                       childNode,
                                       new ToolTipTreeNode(nodeName,
                                                           getDescriptions
                                                                           ? getTableDescription(tablePath,
                                                                                                 thisMember.getDataTypes()
                                                                                                           .get(memIndex))
                                                                           : null));
                        }
                    }
                }
            }

            // Check if primitive variables are included in the tree and this node has no children
            // (variables)
            if ((treeType == STRUCTURES_WITH_PRIMITIVES
                 || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES
                 || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES)
                && childNode.getChildCount() == 0)
            {
                // Remove the node
                parentNode.remove(childNode);
            }
        }
    }

    /**********************************************************************************************
     * Get the description for the specified table
     *
     * @param tablePath
     *            root table and variable path for the table
     *
     * @param dataType
     *            table data type
     *
     * @return The description for this table. If this is an instance of a table and no specific
     *         description exists for it then use the prototype table's description. If the
     *         prototype table has no description then return null
     *********************************************************************************************/
    protected String getTableDescription(String tablePath, String dataType)
    {
        String description = null;
        String protoDescription = null;

        // Check if descriptions are needed and that descriptions are available
        if (getDescriptions && tableDescriptions != null)
        {
            // Check if there's a table_name & var_name match and use that description; if no match
            // then find the table_name match
            for (int index = 0; index < tableDescriptions.length; index++)
            {
                // Check if this is this table's data type matches and the variable path in the
                // descriptions list is empty
                if (tableDescriptions[index][0].equals(dataType)
                    && tableDescriptions[index][1].isEmpty())
                {
                    // Store the prototype description for this table
                    protoDescription = tableDescriptions[index][1];
                }
                // Check if the table paths match
                else if (tableDescriptions[index][0].equals(tablePath))
                {
                    // Store the specific description for this table and stop searching
                    description = tableDescriptions[index][1];
                    break;
                }
            }
        }

        return description != null
                                   ? description
                                   : protoDescription;
    }

    /**********************************************************************************************
     * Search the entire table tree and get the list of paths where the node contains the search
     * table's name. If no search name is provided then a list of all nodes is returned. The paths
     * are in the form of comma-separated node names, with any HTML tags removed
     *
     * @param searchName
     *            name of table to search for in the node names; null to get all paths for the
     *            parent node
     *
     * @return List of paths to the nodes matching the search table's name, or all nodes if the
     *         search name is null
     *********************************************************************************************/
    protected List<String> getTableTreePathList(String searchName)
    {
        return getTableTreePathList(searchName, root, -1);
    }

    /**********************************************************************************************
     * Search the table tree starting at the specified node and get the list of paths where the
     * node contains the search table's name. If no search name is provided then a list of all
     * nodes is returned. The paths are in the form of comma-separated node names, with any HTML
     * tags removed
     *
     * @param searchName
     *            name of table to search for in the node names; null to get all paths for the
     *            parent node
     *
     * @param startNode
     *            starting node
     *
     * @param maxLevel
     *            only paths that are at a level less than or equal to this value can be added to
     *            the list; -1 to to ignore the path level
     *
     * @return List of paths to the nodes matching the search table's name, or all nodes if the
     *         search name is null
     *********************************************************************************************/
    protected List<String> getTableTreePathList(String searchName,
                                                ToolTipTreeNode startNode,
                                                int maxLevel)
    {
        // Get the paths from the tree matching the search criteria
        tablePathList = getTableTreePathArray(searchName, startNode, maxLevel);

        List<String> variablePaths = new ArrayList<String>();

        // Step through each path
        for (Object[] path : tablePathList)
        {
            // Convert the path array to a string, stripping off the nodes names prior to the start
            // index and the HTML tags
            String variable = removeExtraText(createNameFromPath(path, getHeaderNodeLevel()));

            // Check if the path is not already in the list and that the path isn't blank
            if (!variablePaths.contains(variable) && !variable.isEmpty())
            {
                // Add the path to the list
                variablePaths.add(variable);
            }
        }

        return variablePaths;
    }

    /**********************************************************************************************
     * Search the table tree starting at the specified node and get the list of path arrays where
     * the node contains the search table's name. If no search name is provided then a list of all
     * nodes is returned. The paths are in the form of arrays, with any HTML tags preserved
     *
     * @param searchName
     *            name of table to search for in the node names; null to get all paths for the
     *            parent node
     *
     * @param startNode
     *            starting node
     *
     * @param maxLevel
     *            only paths that are at a level less than or equal to this value can be added to
     *            the list; -1 to to ignore the path level
     *
     * @return List of paths to the nodes matching the search table's name, or all nodes if the
     *         search name is null
     *********************************************************************************************/
    protected List<Object[]> getTableTreePathArray(String searchName,
                                                   ToolTipTreeNode startNode,
                                                   int maxLevel)
    {
        // Initialize the path list
        tablePathList = new ArrayList<Object[]>();

        // Step through each element and child of this node
        for (Enumeration<?> element = startNode.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node reference
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Check if the node's table name matches the search table's name and that the node
            // name isn't empty, that the node meets the level requirement (if any), and that the
            // path isn't already in the list
            if ((searchName == null
                 || searchName.equals(getTableFromNodeName(node.getUserObject().toString())))
                && node.getUserObjectPath().length != 0
                && (maxLevel == -1 || node.getLevel() <= maxLevel))
            {
                // Add the table's path to the list
                tablePathList.add(node.getUserObjectPath());
            }
        }

        return tablePathList;
    }

    /**********************************************************************************************
     * Beginning with the root node, remove tree nodes that do not match the specified search text
     * and are not an ancestor of a node that does contain a match.
     *
     * @param searchText
     *            text for which to search (regular expression is allowed)
     *
     * @return List of paths for the nodes matching the search text; an empty list if there are no
     *         matches
     *********************************************************************************************/
    protected List<String> pruneTreeToSearchCriteria(String searchText)
    {
        List<String> variableList = new ArrayList<String>();
        List<ToolTipTreeNode> removeList = new ArrayList<ToolTipTreeNode>();

        // Step through each element and child of the root node
        for (Enumeration<?> element = root.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node reference
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Check if the node is for a table or variable and matches the search criteria
            if (node.getLevel() >= getHeaderNodeLevel()
                && getPathFromNode(node).getLastPathComponent().toString().matches(searchText))
            {
                // Get the node's full path
                String fullPath = getFullVariablePath(node.getUserObjectPath());

                // Add the full path for this node to the list of matching nodes
                variableList.add(fullPath);

                do
                {
                    // Check if the list of nodes to removes contains this node (which is either
                    // the node with a match or one of its ancestors)
                    if (removeList.contains(node))
                    {
                        // Remove the node from the list so that it isn't removed from the tree
                        // later
                        removeList.remove(node);
                    }

                    // Get the parent node for this node
                    node = (ToolTipTreeNode) node.getParent();
                } while (node != root);
                // Continue to add the ancestor nodes to the list of those containing a match until
                // the root node is reached. This ensures the node containing the match retains its
                // tree hierarchy
            }
            // Check if this is not the root node (which is always retained)
            else if (node != root)
            {
                // Add the node to the list of those to removed from the tree
                removeList.add(node);
            }
        }

        // Step through the list of nodes that don't contain a match and aren't ancestors of a node
        // containing a match
        for (ToolTipTreeNode node : removeList)
        {
            // Remove the node from the tree
            node.removeFromParent();
        }

        // Force the root node to acknowledge with the node removals, then fully expand the pruned
        // tree
        ((DefaultTreeModel) getModel()).nodeStructureChanged(root);
        setTreeExpansion(true);

        return variableList;
    }

    /**********************************************************************************************
     * Determine if the specified path exists in the table tree
     *
     * @param targetPath
     *            name of the node to search for, in the form
     *            rootTable[,dataType1.variable1[,dataType2.variable2[,...]]]
     *
     * @return true if the target path exists in in the tree
     *********************************************************************************************/
    protected boolean isNodeInTree(String targetPath)
    {
        boolean isInTree = false;

        // Step through the table tree
        for (Enumeration<?> element = getRootNode().preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the referenced node
            ToolTipTreeNode tableNode = (ToolTipTreeNode) element.nextElement();

            // Check if the target path matches the path in the tree (skipping header nodes such as
            // the project database and filter nodes)
            if (targetPath.equals(removeExtraText(getFullVariablePath(tableNode.getPath()))))
            {
                // Set the flag indicating the path is present in the tree and stop searching
                isInTree = true;
                break;
            }
        }

        return isInTree;
    }

    /**********************************************************************************************
     * Get the TreeNode for the node matching the specified node name (table name + variable name)
     *
     * @param nodeName
     *            name of the node to search for, in the form tableName.variableName
     *
     * @return TreeNode for the specified node name; null if the node name doesn't exist in the
     *         tree
     *********************************************************************************************/
    protected ToolTipTreeNode getNodeByNodeName(String nodeName)
    {
        ToolTipTreeNode node = null;

        // Step through the root node's children, if any
        for (Enumeration<?> element = root.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the referenced node
            ToolTipTreeNode tableNode = (ToolTipTreeNode) element.nextElement();

            // Check if the node matches the target node's name
            if (removeExtraText(tableNode.getUserObject().toString()).equals(nodeName))
            {
                // Store this node and stop searching
                node = tableNode;
                break;
            }
        }

        return node;
    }

    /**********************************************************************************************
     * Get the TreeNode for the first node matching the specified node path name (table path +
     * variable name), starting at the tree root node
     *
     * @param nodePath
     *            path of the node to search for, in the form
     *            rootTable,tableName.variableName(,...)
     *
     * @return TreeNode for the specified node path; null if the node path doesn't exist in the
     *         tree
     *********************************************************************************************/
    protected ToolTipTreeNode getNodeByNodePath(String nodePath)
    {
        return getNodeByNodePath(nodePath, getRootNode());
    }

    /**********************************************************************************************
     * Get the TreeNode for the first node matching the specified node path name (table path +
     * variable name), starting at the specified node
     *
     * @param nodePath
     *            path of the node to search for, in the form
     *            rootTable,tableName.variableName(,...)
     *
     * @param startNode
     *            node from which to begin
     *
     * @return TreeNode for the specified node path; null if the node path doesn't exist in the
     *         tree
     *********************************************************************************************/
    protected ToolTipTreeNode getNodeByNodePath(String nodePath, ToolTipTreeNode startNode)
    {
        ToolTipTreeNode node = null;

        // Step through the root node's children, if any
        for (Enumeration<?> element = startNode.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the referenced node
            ToolTipTreeNode tableNode = (ToolTipTreeNode) element.nextElement();

            // Check if the node matches the target node's path
            if (removeExtraText(getFullVariablePath(tableNode.getUserObjectPath())).equals(nodePath))
            {
                // Store this node and stop searching
                node = tableNode;
                break;
            }
        }

        return node;
    }

    /**********************************************************************************************
     * Get the table name from the node name, removing the variable name if present
     *
     * @param nodeName
     *            tree node name
     *
     * @return Table name portion of the node name
     *********************************************************************************************/
    private static String getTableFromNodeName(String nodeName)
    {
        // Get the index of a period, if present
        int index = nodeName.indexOf(".");

        // Check if the node name contains the variable name
        if (index != -1)
        {
            // Remove the variable name, leaving the table name
            nodeName = nodeName.substring(0, index);
        }

        return nodeName;
    }

    /**********************************************************************************************
     * Get the variable's root table from the specified node path
     *
     * @param nodePath
     *            node path from which to obtain the variable's root table
     *
     * @return The variable's root table for the specified node path
     *********************************************************************************************/
    protected String getVariableRootFromNodePath(Object[] nodePath)
    {
        String rootTable = "";

        // Step backwards through the node path
        for (int index = nodePath.length - 1; index > 0; index--)
        {
            // Check if the node name isn't a variable. Variable nodes are in the format
            // data_type.variable_name[:bit_length]
            if (!nodePath[index].toString().contains("."))
            {
                // Store the root table and stop searching
                rootTable = nodePath[index].toString();
                break;
            }
        }

        return rootTable;
    }

    /**********************************************************************************************
     * Add the ancestor instance tables for each table path in the supplied list. Based on the
     * input flag also add the prototype tables for every table and ancestor in the list. No
     * duplicate table references are included in the list
     *
     * @param tablePaths
     *            list containing the table paths in which to search
     *
     * @param includeAncestorPrototype
     *            true to also include the prototype for all instance tables
     *********************************************************************************************/
    protected void addTableAncestors(List<String> tablePaths, boolean includeAncestorPrototype)
    {
        // Check if any tables are provided
        if (!tablePaths.isEmpty())
        {
            List<String> ancestorTables = new ArrayList<String>();

            // Step through each table
            for (String tablePath : tablePaths)
            {
                // Find the beginning of the last child in the path
                int pathSeparator = tablePath.lastIndexOf(",");

                // Process every child and root in the table path
                while (pathSeparator != -1)
                {
                    // Remove the last child in the table path
                    tablePath = tablePath.substring(0, pathSeparator);

                    // Check if the table isn't in the lists
                    if (!ancestorTables.contains(tablePath) && !tablePaths.contains(tablePath))
                    {
                        // Add the table to the list
                        ancestorTables.add(tablePath);
                    }

                    // Check if prototypes of ancestor tables should be included
                    if (includeAncestorPrototype)
                    {
                        // Remove the last child in the table path
                        String prototypeTable = TableInformation.getPrototypeName(tablePath);

                        // Check if the table isn't in the lists
                        if (!ancestorTables.contains(prototypeTable)
                            && !tablePaths.contains(prototypeTable))
                        {
                            // Add the table to the list
                            ancestorTables.add(prototypeTable);
                        }
                    }

                    // Find the beginning of the last child in the path
                    pathSeparator = tablePath.lastIndexOf(",");
                }
            }

            // Add the ancestor tables to the table list
            tablePaths.addAll(ancestorTables);
        }
    }

    /**********************************************************************************************
     * Get a list of the tables (with their paths) represented by the selected nodes. If a header
     * node (i.e., a non-table node, such as a group or type node) is selected then all of its
     * child tables are added to the list. HTML tags are removed from the returned table path+names
     *
     * @return List containing the table path+names of the selected node(s) with no duplicate table
     *         references and with any HTML tags removed
     *********************************************************************************************/
    protected List<String> getSelectedTablesWithChildren()
    {
        // Create storage for the table names
        List<String> tables = new ArrayList<String>();

        // Check if any tables are selected in the table tree
        if (getSelectionPaths() != null)
        {
            // Step through each selected table in the tree
            for (TreePath path : getSelectionPaths())
            {
                // Get the node for this path
                ToolTipTreeNode node = (ToolTipTreeNode) path.getLastPathComponent();

                // Check that this node represents a header node
                if (node.getLevel() < getHeaderNodeLevel())
                {
                    // Check if the node has any children
                    if (node.getChildCount() != 0)
                    {
                        List<Object[]> childPaths = new ArrayList<Object[]>();

                        // Get the children of this header node. This returns a list of the nodes
                        // that have no children (the end nodes in each branch); it doesn't include
                        // the parent nodes leading to the children. The parents must be added
                        // afterwards
                        addChildNodes(node, childPaths, null, false);

                        // Step through each of the child table paths
                        for (Object[] childPath : childPaths)
                        {
                            // Get the table's full path (with the root table)
                            String fullPath = CcddUtilities.removeHTMLTags(getFullVariablePath(childPath));

                            // Check if the table isn't already in the list
                            if (!fullPath.isEmpty() && !tables.contains(fullPath))
                            {
                                // Add the table's full path (with the root table) to the full path
                                // list
                                tables.add(fullPath);
                            }
                        }

                        // Add all of the parents of the child tables
                        addTableAncestors(tables, false);
                    }
                }
                // The path represents a table
                else
                {
                    // Get the table's full path (with the root table)
                    String fullPath = CcddUtilities.removeHTMLTags(getFullVariablePath(node.getPath()));

                    // Check if the table isn't already in the list
                    if (!tables.contains(fullPath))
                    {
                        // Add the table full path list
                        tables.add(fullPath);
                    }
                }
            }
        }

        return tables;
    }

    /**********************************************************************************************
     * Get a list of the specified table name(s), and include all descendant tables for those
     * tables that are root structures. The node with the default instance node name is used to
     * search for root table descendants. HTML tags are removed from the returned table path+names
     *
     * @param tables
     *            list of tables for which the descendants are required
     *
     * @return List containing the table path+names of the specified table(s) with the descendants
     *         of these tables (if applicable and any exist) and with any HTML tags removed
     *********************************************************************************************/
    protected List<String> getTablesWithChildren(List<String> tables)
    {
        List<String> tablesWithChildren = new ArrayList<String>();

        // Step through each supplied table
        for (String table : tables)
        {
            List<Object[]> childPaths = new ArrayList<Object[]>();

            // Add the table to the list
            tablesWithChildren.add(table);

            // Get the node for the table
            ToolTipTreeNode node = getNodeByNodePath(table,
                                                     getNodeByNodeName(DEFAULT_INSTANCE_NODE_NAME));

            // Check if the table is in the tree
            if (node != null)
            {
                // Get the children of this table node
                addChildNodes(node, childPaths, null, false);

                // Step through each of the child table paths
                for (Object[] childPath : childPaths)
                {
                    // Get the table's full path (with the root table)
                    String fullPath = CcddUtilities.removeHTMLTags(getFullVariablePath(childPath));

                    // Check if the table isn't already in the list
                    if (!tablesWithChildren.contains(fullPath))
                    {
                        // Add the table's full path (with the root table) to the full path list
                        tablesWithChildren.add(fullPath);
                    }
                }
            }
        }

        return tablesWithChildren;
    }

    /**********************************************************************************************
     * Get a list of the tables (with their paths) represented by the selected nodes. If a header
     * node (i.e., a non-table node one level above a table node, such as a group or type node) is
     * selected then its descendants are checked, recursively, until the level containing tables is
     * found; these tables are added to the list. If a selected node isn't a header node then
     * ignore the node if it has a selected ancestor node. HTML tags are removed from the returned
     * table path+names
     *
     * @return List containing the table path+names of the selected node(s) with any HTML tags
     *         removed
     *********************************************************************************************/
    protected List<String> getSelectedTablesWithoutChildren()
    {
        // Create storage for the table names
        List<String> tables = new ArrayList<String>();

        // Check if any tables are selected in the table tree
        if (getSelectionPaths() != null)
        {
            // Step through each selected table in the tree
            for (TreePath path : getSelectionPaths())
            {
                // Get the selected node
                ToolTipTreeNode node = (ToolTipTreeNode) path.getLastPathComponent();

                // Check if the table isn't already in the list
                if (node.getLevel() >= getHeaderNodeLevel()
                    && !tables.contains(node.getUserObject().toString()))
                {
                    // Add the selected table to the list
                    tables.add(CcddUtilities.removeHTMLTags(getFullVariablePath(node.getPath())));
                }

                // Step through all of the tables that are descendants of this node (excluding any
                // duplicates and any children of a table already added to the list)
                for (String table : getTablesWithoutChildren(node))
                {
                    // Check if the table isn't already in the list
                    if (!tables.contains(table))
                    {
                        // Add the tables that are descendants of this node to the list
                        tables.add(table);
                    }
                }
            }
        }

        return tables;
    }

    /**********************************************************************************************
     * Get a list of the table names that are the descendants of the specified node (do not include
     * any duplicates or children of these descendants, if any). HTML tags are removed from the
     * returned table path+names. This is a recursive method
     *
     * @param node
     *            tree node for which to get the table names
     *
     * @return List containing the table path+names of the specified node, excluding any duplicates
     *         and children of these descendant tables, with any HTML tags removed
     *********************************************************************************************/
    protected List<String> getTablesWithoutChildren(ToolTipTreeNode node)
    {
        List<String> tables = new ArrayList<String>();

        // Check if the node has no children or if this node represents a table (i.e., isn't a
        // header node with no child nodes)
        if (node.getChildCount() == 0 || node.getLevel() >= getHeaderNodeLevel())
        {
            boolean isParentSelected = false;

            // Get the individual elements in the selected path
            Object[] pathElements = node.getPath();

            // Step through the node's ancestors
            for (int index = getHeaderNodeLevel(); index < node.getLevel() - 1; index++)
            {
                // Check of the ancestor node is selected
                if (tables.contains(pathElements[index].toString()))
                {
                    // Set the flag indicating that an ancestor of this node is selected and stop
                    // searching
                    isParentSelected = true;
                    break;
                }
            }

            // Check if no ancestor of this node is selected
            if (!isParentSelected)
            {
                // Get the table's full path
                String fullPath = CcddUtilities.removeHTMLTags(getFullVariablePath(node.getPath()));

                // Check if the list doesn't already contain the table
                if (!tables.contains(fullPath))
                {
                    // Add the table path+name to the list
                    tables.add(fullPath);
                }
            }
        }
        // The node is a header node
        else
        {
            // Step through each of the header node's child nodes
            for (int index = 0; index < node.getChildCount(); index++)
            {
                // Add the path+name of the tables in the child node (or its descendants, if the
                // child is a header node) to the table list
                tables.addAll(getTablesWithoutChildren(((ToolTipTreeNode) node.getChildAt(index))));
            }
        }

        return tables;
    }

    /**********************************************************************************************
     * Get a list of the group nodes that are selected. Deselect of the group's child nodes and the
     * group node itself
     *
     * @return List containing the selected group name(s)
     *********************************************************************************************/
    protected List<String> getSelectedGroups()
    {
        // Create storage for the group names
        List<String> groups = new ArrayList<String>();

        // Check if the table tree is filtered by group and if any nodes are selected
        if (isByGroup && getSelectionPaths() != null)
        {
            // Step through each selected table in the tree
            for (TreePath path : getSelectionPaths())
            {
                // Check that this node represents a group
                if (path.getPathCount() == GROUP_HEADER_LEVEL)
                {
                    // Get the node for this path
                    ToolTipTreeNode node = (ToolTipTreeNode) path.getLastPathComponent();

                    // Add the group name to the list. Remove the HTML tags in case the node is
                    // disabled
                    groups.add(removeExtraText(node.getUserObject().toString()));

                    // Deselect the group and any children of the group
                    removeDescendantSelectedPaths(path, true);
                }
            }
        }

        return groups;
    }

    /**********************************************************************************************
     * Get the root structure and variable path for the selected node(s)
     *
     * @param isVariable
     *            true if the tree contains variables
     *
     * @return List containing the path array(s) for the selected variable(s)
     *********************************************************************************************/
    protected List<Object[]> getSelectedVariables(boolean isVariable)
    {
        selectedVariablePaths = new ArrayList<Object[]>();

        // Check if at least one node is selected
        if (getSelectionCount() != 0)
        {
            // Step through each selected node
            for (TreePath path : getSelectionPaths())
            {
                // Check if the selected variable node has children
                addChildNodes((ToolTipTreeNode) path.getLastPathComponent(),
                              selectedVariablePaths,
                              excludedVariables,
                              isVariable);
            }
        }

        return selectedVariablePaths;
    }

    /**********************************************************************************************
     * Get a list of full node paths for all nodes that represent a primitive variable, starting at
     * the specified node. Disabled nodes may be ignored if desired
     *
     * @param startNode
     *            starting node from which to build the variable list
     *
     * @param ignoreDisabled
     *            true to ignore nodes that are flagged as disabled (via HTML color tag)
     *
     * @return List containing the full node paths for all nodes that represent a primitive
     *         variable, starting with the specified node
     *********************************************************************************************/
    protected List<String> getPrimitiveVariablePaths(DefaultMutableTreeNode startNode,
                                                     boolean ignoreDisabled)
    {
        // Create storage for the primitive variable paths
        List<String> allPrimitivePaths = new ArrayList<String>();

        // Step through each element and child of this node
        for (Enumeration<?> element = startNode.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Check if disabled nodes should be included, or if not, that the node isn't disabled
            if (!ignoreDisabled
                || !node.getUserObject().toString().startsWith(DISABLED_TEXT_COLOR))
            {
                // Get the data type for this node
                String dataType = getTableFromNodeName(ignoreDisabled
                                                                      ? node.getUserObject().toString()
                                                                      : removeExtraText(node.getUserObject().toString()));

                // Check if the data type is a primitive (versus a structure)
                if (dataTypeHandler.isPrimitive(dataType))
                {
                    // Convert the node path array to a string
                    String nodePath = CcddUtilities.convertArrayToString(node.getPath()).replaceAll(", ", ",");

                    // Add the variable's entire node path to the list
                    allPrimitivePaths.add(ignoreDisabled
                                                         ? nodePath
                                                         : removeExtraText(nodePath));
                }
            }
        }

        return allPrimitivePaths;
    }

    /**********************************************************************************************
     * Update the text color for the nodes that represent a primitive variable based on the
     * variable exclusion list
     *********************************************************************************************/
    private void setNodeEnableByExcludeList()
    {
        // Step through elements and children of this node
        for (Enumeration<?> element = root.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node reference
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Check if this is node has no children, which indicates it may be a variable, and
            // that the node is for a structure or variable
            if (node.isLeaf() && node.getLevel() >= getHeaderNodeLevel())
            {
                // Get the node name
                String nodeName = node.getUserObject().toString();

                // Set to true if the variable in this path is not excluded (as evidenced by having
                // a HTML tag)
                boolean wasExcluded = nodeName.startsWith(DISABLED_TEXT_COLOR);

                // Get the path for this node as a string array
                String[] nodes = CcddUtilities.convertObjectToString(node.getPath());

                // Step through each node in the path
                for (int index = 0; index < nodes.length; index++)
                {
                    // Remove the HTML tags from the node
                    nodes[index] = removeExtraText(nodes[index]);
                }

                // Get the variable path and name. Skip the link name node if present
                String variablePath = getFullVariablePath(nodes,
                                                          (nodes[1].equals(LINKED_VARIABLES_NODE_NAME)
                                                           && !isFilteredByGroup()
                                                                                   ? 1
                                                                                   : 0));

                // Set the flag indicating the variable is excluded if it's in the exclusion lists
                boolean isExcluded = excludedVariables.contains(variablePath)
                                     || (nodes[1].equals(UNLINKED_VARIABLES_NODE_NAME)
                                         && linkedVariables.contains(variablePath));

                // Check if the variable exclusion state has changed
                if (wasExcluded != isExcluded)
                {
                    // Reset the node name to indicate its inclusion/exclusion state. If excluded,
                    // prepend the HTML tag to gray out the name. Indicate that the node changed so
                    // that the tree redraws the name
                    node.setUserObject((isExcluded
                                                   ? DISABLED_TEXT_COLOR
                                                   : "")
                                       + nodes[nodes.length - 1]);
                    ((DefaultTreeModel) getModel()).nodeChanged(node);
                }
            }
        }
    }

    /**********************************************************************************************
     * Set the node text color based on the enable state of its child nodes. If all children are
     * disabled then disable the parent, otherwise enable the parent. This is a recursive method
     *
     * @param node
     *            node for which to adjust the text and color
     *
     * @return true if the node is enabled; false if disabled
     *********************************************************************************************/
    private boolean setNodeEnableByChildState(ToolTipTreeNode node)
    {
        boolean isEnabled;

        // Check if this node has any children
        if (node.getChildCount() != 0)
        {
            isEnabled = false;

            // Step through the node's children
            for (Enumeration<?> element = node.children(); element.hasMoreElements();)
            {
                // Check if any of the child's children are enabled
                if (setNodeEnableByChildState((ToolTipTreeNode) element.nextElement()))
                {
                    // Set the flag to indicate a child of this node is enabled
                    isEnabled = true;
                }
            }
        }
        // The node has no child nodes
        else
        {
            // Check if this isn't the root node and the node doesn't represent a table
            if (node.getLevel() != 0 && node.getLevel() < getHeaderNodeLevel())
            {
                isEnabled = false;
            }
            // This is the root node or the node represents a table
            else
            {
                // Initialize the enabled flag based on the node containing an HTML tag (hence it's
                // disabled)
                isEnabled = !node.getUserObject().toString().startsWith(DISABLED_TEXT_COLOR);
            }
        }

        // Check if the enable state changed and that this isn't the root node
        if (isEnabled != !node.getUserObject().toString().startsWith(DISABLED_TEXT_COLOR)
            && node.getLevel() != 0)
        {
            // Reset the node name to indicate its enabled/disabled state. If disabled, prepend the
            // HTML tag to gray out the name. Indicate that the node changed so that the tree
            // redraws the name
            node.setUserObject((isEnabled
                                          ? ""
                                          : DISABLED_TEXT_COLOR)
                               + removeExtraText(node.getUserObject().toString()));
            ((DefaultTreeModel) getModel()).nodeChanged(node);
        }

        return isEnabled;
    }

    /**********************************************************************************************
     * Create a table tree panel. The table tree is placed in a scroll pane. A check box is added
     * that allows tree expansion/collapse
     *
     * @param label
     *            table tree title; null to not display a title
     *
     * @param selectionMode
     *            tree item selection mode (single versus multiple)
     *
     * @param isAllowHighlight
     *            true to allow highlighting of text in the node names
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return JPanel containing the table tree components
     *********************************************************************************************/
    protected JPanel createTreePanel(String label,
                                     int selectionMode,
                                     boolean isAllowHighlight,
                                     final Component parent)
    {
        // Set the renderer for the tree so that the custom icons can be used for the various node
        // types
        setCellRenderer(new VariableTreeCellRenderer(isAllowHighlight)
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
                // Check if this node represents a variable
                if (leaf
                    && ((ToolTipTreeNode) value).getLevel() > ((CcddTableTreeHandler) tree).getHeaderNodeLevel()
                    && (treeType == STRUCTURES_WITH_PRIMITIVES
                        || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES
                        || treeType == INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES))
                {
                    // Set the icon for the variable node
                    setVariableNodeIcon(this,
                                        (ToolTipTreeNode) value,
                                        row,
                                        linkedVariables.contains(removeExtraText(getFullVariablePath(((ToolTipTreeNode) value).getPath()))));
                }

                // Get the component to display
                return super.getTreeCellRendererComponent(tree,
                                                          value,
                                                          sel,
                                                          expanded,
                                                          leaf,
                                                          row,
                                                          hasFocus);
            }
        });

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
        JPanel treePnl = new JPanel(new GridBagLayout());
        treePnl.setBorder(emptyBorder);

        // Check if a label is provided
        if (label != null && !label.isEmpty())
        {
            // Create the tree labels
            JLabel treeLbl = new JLabel(label);
            treeLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            treeLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
            treePnl.add(treeLbl, gbc);
            gbc.gridy++;
        }

        // Create the tree scroll pane
        JScrollPane treeScroll = new JScrollPane(this);
        treeScroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                Color.LIGHT_GRAY,
                                                                                                Color.GRAY),
                                                                BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));

        // Add the tree to the panel
        gbc.weighty = 1.0;
        treePnl.add(treeScroll, gbc);

        // Set the table tree font and number of rows to display
        setFont(ModifiableFontInfo.TREE_NODE.getFont());
        setVisibleRowCount(10);

        // Add a listener for changes to the table tree
        addTreeSelectionListener(new TreeSelectionListener()
        {
            /**************************************************************************************
             * Handle a change to the table tree selection
             *************************************************************************************/
            @Override
            public void valueChanged(TreeSelectionEvent lse)
            {
                // Check that a table tree (re)build isn't in progress. Building the tree triggers
                // tree selection value changes that should not be processed
                if (!isBuilding)
                {
                    // Update the groups based on the tables selected
                    updateTableSelection();
                }
            }
        });

        // Add a listener for table tree expand and collapse events
        addTreeExpansionListener(new TreeExpansionListener()
        {
            /**************************************************************************************
             * Handle an expansion of the table tree
             *************************************************************************************/
            @Override
            public void treeExpanded(TreeExpansionEvent tee)
            {
                // Update the table selection based on the selected group
                updateGroupSelection();
            }

            /**************************************************************************************
             * Handle a collapse of the table tree
             *************************************************************************************/
            @Override
            public void treeCollapsed(TreeExpansionEvent tee)
            {
                // Update the table selection based on the selected group
                updateGroupSelection();
            }
        });

        // Create a tree expansion check box
        expandChkBx = new JCheckBox("Expand all");
        expandChkBx.setBorder(emptyBorder);
        expandChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        expandChkBx.setSelected(false);

        // Check if this is the last component to add
        if (!showGroupFilter && !showTypeFilter && !addHiddenCheckBox)
        {
            gbc.insets.bottom = 0;
        }

        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.weighty = 0.0;
        gbc.gridy++;
        treePnl.add(expandChkBx, gbc);

        // Create a listener for changes in selection of the tree expansion check box
        expandChkBx.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change to the tree expansion check box selection
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                setTreeExpansion(expandChkBx.isSelected());
            }
        });

        // Check if instance tables are displayed in the tree
        if (treeType != TableTreeType.PROTOTYPE_TABLES && treeType != COMMAND_TABLES)
        {
            // Create a hide data type check box
            final JCheckBox hideTypeChkBx = new JCheckBox("Hide data type");
            hideTypeChkBx.setBorder(emptyBorder);
            hideTypeChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            hideTypeChkBx.setSelected(false);

            // Check if this is the last component to add
            if (!showGroupFilter && !showTypeFilter && !addHiddenCheckBox)
            {
                gbc.insets.bottom = 0;
            }

            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.weighty = 0.0;
            gbc.gridy++;
            treePnl.add(hideTypeChkBx, gbc);

            // Create a listener for changes in selection of the hide data type check box
            hideTypeChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the hide data type check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    setEnableDataType(!hideTypeChkBx.isSelected());

                    // Force the root node to draw with the node additions
                    refreshTree();
                }
            });
        }

        // Create the filtering node prefix storage and check boxes
        final List<String> prefixes = new ArrayList<String>();
        final JCheckBox groupFilterChkBx = new JCheckBox("Filter by group");
        final JCheckBox typeFilterChkBx = new JCheckBox("Filter by type");

        // Step through the child nodes of the root. These are the nodes that specify the
        // table/variable divisions (e.g., 'Prototype', 'Parents & Children')
        for (int index = 0; index < root.getChildCount(); index++)
        {
            // Add the child node name with its path to the prefix list
            prefixes.add("["
                         + root.getUserObject()
                         + ", "
                         + ((ToolTipTreeNode) root.getChildAt(index)).getUserObject());
        }

        // Check if the group check box is valid for this tree type
        if (showGroupFilter)
        {
            // Create a group filter check box
            groupFilterChkBx.setBorder(emptyBorder);
            groupFilterChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            groupFilterChkBx.setSelected(false);

            // Check if this is the last component to add
            if (!showTypeFilter && !addHiddenCheckBox)
            {
                gbc.insets.bottom = 0;
            }

            gbc.gridy++;
            treePnl.add(groupFilterChkBx, gbc);

            // Create a listener for changes in selection of the group filter check box
            groupFilterChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the group filter check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Set the filter by group flag based on the check box status
                    isByGroup = groupFilterChkBx.isSelected();

                    // Store the tree's current expansion state
                    String expState = getExpansionState();

                    // Rebuild the tree based on the filter selection
                    buildTableTree(expandChkBx.isSelected(), rateName, rateFilter, parent);

                    // Adjust the expansion state to account for the change in filtering, then
                    // restore the expansion state
                    expState = adjustExpansionState(expState,
                                                    groupFilterChkBx.isSelected(),
                                                    true,
                                                    typeFilterChkBx.isSelected(),
                                                    false,
                                                    false,
                                                    prefixes,
                                                    groupHandler,
                                                    tableTypeHandler);
                    setExpansionState(expState);
                }
            });
        }

        // Check if the type check box is valid for this tree type
        if (showTypeFilter)
        {
            // Create a type filter check box
            typeFilterChkBx.setBorder(emptyBorder);
            typeFilterChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            typeFilterChkBx.setSelected(false);

            // Check if this is the last component to add
            if (!addHiddenCheckBox)
            {
                gbc.insets.bottom = 0;
            }

            gbc.gridy++;
            treePnl.add(typeFilterChkBx, gbc);

            // Create a listener for changes in selection of the type filter check box
            typeFilterChkBx.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the type filter check box selection
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Set the filter by type flag based on the check box status
                    isByType = typeFilterChkBx.isSelected();

                    // Store the tree's current expansion state
                    String expState = getExpansionState();

                    // Rebuild the tree based on the filter selection
                    buildTableTree(expandChkBx.isSelected(), rateName, rateFilter, parent);

                    // Adjust the expansion state to account for the change in filtering, then
                    // restore the expansion state
                    expState = adjustExpansionState(expState,
                                                    groupFilterChkBx.isSelected(),
                                                    false,
                                                    typeFilterChkBx.isSelected(),
                                                    true,
                                                    false,
                                                    prefixes,
                                                    groupHandler,
                                                    tableTypeHandler);
                    setExpansionState(expState);
                }
            });
        }

        // In order to align the two adjacent trees a phantom check box may be needed for the table
        // tree panel. Check if the flag is set to add this check box
        if (addHiddenCheckBox)
        {
            // Create the hidden check box. To prevent display of the check box components an empty
            // panel is placed over it
            JPanel hiddenPnl = new JPanel(new FlowLayout(FlowLayout.LEFT));
            hiddenPnl.setBorder(emptyBorder);
            JCheckBox hiddenChkBx = new JCheckBox(" ");
            hiddenChkBx.setBorder(emptyBorder);
            gbc.insets.bottom = 0;
            gbc.gridy++;
            treePnl.add(hiddenPnl, gbc);
            hiddenChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            hiddenChkBx.setFocusable(false);
            hiddenChkBx.setDisabledIcon(null);
            hiddenChkBx.setEnabled(false);
            treePnl.add(hiddenChkBx, gbc);
        }

        return treePnl;
    }

    /**********************************************************************************************
     * Placeholder for method to respond to changes in selection of a group in the group list
     *********************************************************************************************/
    private void updateGroupSelection()
    {
    }
}
