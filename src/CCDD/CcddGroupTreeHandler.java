/**
 * CFS Command & Data Dictionary group tree handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.GROUP_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.InternalTable;

/******************************************************************************
 * CFS Command & Data Dictionary group tree handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddGroupTreeHandler extends CcddInformationTreeHandler
{
    // Class references
    private CcddDbTableCommandHandler dbTable;
    private CcddGroupHandler groupHandler;
    private CcddTableTypeHandler tableTypeHandler;
    private ToolTipTreeNode root;
    private DefaultTreeModel treeModel;
    private CcddFieldHandler fieldHandler;

    // Flags to indicate if the tree should be filtered by table type and
    // filtered by application
    private boolean isByType;
    private boolean isByApp;

    // Flag indicating if the group tree nodes are expanded or not
    private boolean isExpanded;

    // List to contain the group definitions (groups and variable paths)
    // retrieved from the database
    private List<String[]> groupDefinitions;

    // Flag to indicate if the group tree is being built
    private boolean isBuilding;

    // Array containing the comment parameters for each table
    private String[][] tableComments;

    // Tree nodes for the table types if filtering by type and for application
    // status if filtering by application
    private ToolTipTreeNode[] typeNodes;
    private ToolTipTreeNode[] appNodes;

    // Data field information
    private Object[][] fieldDefinitions;

    // Currently selected group's schedule rate
    private String scheduleRate;

    // Flag indication only application groups should be displayed
    boolean isApplicationOnly;

    /**************************************************************************
     * Tree cell renderer with group size display handling class
     *************************************************************************/
    private class GroupSizeCellRenderer extends DefaultTreeCellRenderer
    {
        /**********************************************************************
         * Display the group size beside the group name in the tree
         *********************************************************************/
        @Override
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus)
        {
            // Get the tree level for this node
            int level = ((ToolTipTreeNode) value).getLevel();

            // Display the node name
            super.getTreeCellRendererComponent(tree,
                                               value,
                                               sel,
                                               expanded,
                                               leaf,
                                               row,
                                               hasFocus);

            // Check if this node represents a group name
            if (level == 1)
            {
                // Display an icon indicating a variable
                setIcon(new ImageIcon(getClass().getResource(GROUP_ICON)));
            }

            return this;
        }
    }

    /**************************************************************************
     * Group tree handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param scheduleRate
     *            string value representing a schedule rate used to filter the
     *            groups that may be selected; null or blank if not filtering
     * 
     * @param isApplicationOnly
     *            true if only groups that represent CFS applications should be
     *            displayed
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    CcddGroupTreeHandler(CcddMain ccddMain,
                         String scheduleRate,
                         boolean isApplicationOnly,
                         Component parent)
    {
        super(ccddMain,
              InternalTable.GROUPS,
              scheduleRate,
              isApplicationOnly,
              parent);

        this.isApplicationOnly = isApplicationOnly;
    }

    /**************************************************************************
     * Group tree handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    CcddGroupTreeHandler(CcddMain ccddMain, Component parent)
    {
        this(ccddMain, null, false, parent);
    }

    /**************************************************************************
     * Perform initialization steps prior to building the group tree
     * 
     * @param ccddMain
     *            main class
     * 
     * @param groupDefinitions
     *            list containing the group definitions
     *************************************************************************/
    @Override
    protected void initialize(CcddMain ccddMain, List<String[]> groupDefinitions)
    {
        this.groupDefinitions = groupDefinitions;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        groupHandler = new CcddGroupHandler(groupDefinitions);
        fieldHandler = new CcddFieldHandler();

        // Set the tree to be collapsed initially
        isExpanded = false;

        // Set the selected schedule rate to a blank
        scheduleRate = "";

        // Get the array of table comments
        tableComments = ccddMain.getDbTableCommandHandler().queryDataTableComments(ccddMain.getMainFrame());

        // Set the table type and data type handlers, and table comments in the
        // information tree handler
        setHandlersAndComments(tableTypeHandler, tableComments);
    }

    /**************************************************************************
     * Get the reference to the group handler
     * 
     * @return Reference to the group handler
     *************************************************************************/
    protected CcddGroupHandler getGroupHandler()
    {
        return groupHandler;
    }

    /**************************************************************************
     * Get the reference to the data field handler
     * 
     * @return Reference to the data field handler
     *************************************************************************/
    protected CcddFieldHandler getFieldHandler()
    {
        return fieldHandler;
    }

    /**************************************************************************
     * Remove the selected group(s) from the group information class
     *************************************************************************/
    protected void removeSelectedGroup()
    {
        List<String> groups = new ArrayList<String>();

        // Step through each selected path
        for (TreePath path : getSelectionPaths())
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

    /**************************************************************************
     * Build the group tree from the database
     * 
     * @param filterByType
     *            true if the tree is filtered by table type
     * 
     * @param filterByType
     *            true if the tree is filtered by application status
     * 
     * @param scheduleRate
     *            schedule rate used to filter the groups; blank or null if not
     *            filtering by schedule rate
     * 
     * @param isApplicationOnly
     *            true to only display groups that represent a CFS application
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    @Override
    protected void buildTree(boolean filterByType,
                             boolean filterByApp,
                             String scheduleRate,
                             boolean isApplicationOnly,
                             Component parent)
    {
        isByType = filterByType;
        isByApp = filterByApp;
        this.scheduleRate = scheduleRate;

        super.buildTree(isByType,
                        isByApp,
                        scheduleRate,
                        isApplicationOnly,
                        parent);

        // Get the tree's root node
        root = getRootNode();

        // Get the tree model
        treeModel = (DefaultTreeModel) getModel();

        // Build the group information using the group definitions and group
        // data fields from the database
        groupHandler.buildGroupInformation(groupDefinitions);
        buildFieldInformation(parent);

        // Register the tool tip manager for the group tree (otherwise the
        // tool tips aren't displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Set the flag to indicate that the group tree is being built. This
        // flag is used to inhibit actions involving tree selection value
        // changes during the build process
        isBuilding = true;

        // Set the renderer for the tree so that custom icons can be used for
        // the various node types
        setCellRenderer(new GroupSizeCellRenderer());

        // Check if the table types are to be used to filter the table tree
        if (isByType)
        {
            // Create the node storage for the table types
            typeNodes = new ToolTipTreeNode[tableTypeHandler.getTypes().length];
        }

        // Check if the application statuses are to be used to filter the table
        // tree
        if (isByApp)
        {
            // Create the node storage for the application statuses
            appNodes = new ToolTipTreeNode[2];
            appNodes[0] = addInformationNode("Application",
                                             "Groups representing a CFS application");
            appNodes[1] = addInformationNode("Other",
                                             "Groups not representing a CFS application");
        }

        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Extract the link name and rate/description or member
            String groupName = groupInfo.getName();

            // Check if all groups should be displayed or only applications and
            // this is an application
            if (!isApplicationOnly || groupInfo.isApplication())
            {
                // Create a node for the group and add it to the group tree
                ToolTipTreeNode groupNode = addInformationNode(groupName,
                                                               groupInfo.getDescription(),
                                                               isByApp);

                // Check if the table nodes should be displayed (i.e., no
                // schedule rate is supplied)
                if (scheduleRate == null || scheduleRate.isEmpty())
                {
                    // Check if the table types are to be used to filter the
                    // table tree
                    if (isByType)
                    {
                        int index = 0;

                        // Step through each table type
                        for (String type : tableTypeHandler.getTypes())
                        {
                            // Create the node for this table type and add it
                            // to the tree model
                            typeNodes[index] = new ToolTipTreeNode(type,
                                                                   tableTypeHandler.getTypeDefinition(type).getDescription());
                            treeModel.insertNodeInto(typeNodes[index], groupNode, index);
                            index++;
                        }
                    }

                    // Step through each table belonging to the group
                    for (String table : groupInfo.getTables())
                    {
                        // Check if the groups are filtered by application
                        // status
                        if (isByApp)
                        {
                            boolean isFound = false;

                            // Step through the application and non-application
                            // nodes; exit if a match is found
                            for (int nodeIndex = 0; nodeIndex < appNodes.length && !isFound; nodeIndex++)
                            {
                                // Step through each current group node
                                for (int index = 0; index < appNodes[nodeIndex].getChildCount(); index++)
                                {
                                    // Check if the group name matches the node
                                    // name
                                    if (groupName.equals(appNodes[nodeIndex].getChildAt(index).toString()))
                                    {
                                        // Add the indicating a match is found,
                                        // and stop searching
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
                                // Check if the group name matches the node
                                // name
                                if (groupName.equals(root.getChildAt(index).toString()))
                                {
                                    // Add the table to the node and stop
                                    // searching
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

    /**************************************************************************
     * Build the group data field information
     *
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    private void buildFieldInformation(Component parent)
    {
        // Get the data field information from the database
        fieldDefinitions = dbTable.retrieveInformationTable(InternalTable.FIELDS,
                                                            parent).toArray(new Object[0][0]);

        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Build the field information list for this group
            fieldHandler.buildFieldInformation(fieldDefinitions,
                                               CcddFieldHandler.getFieldGroupName(groupInfo.getName()));

            // Set the field information in the group handler
            groupInfo.setFieldInformation(fieldHandler.getFieldInformationCopy());
        }

        // Reset the field handler field information reference so that no
        // fields are initially active
        fieldHandler.setFieldInformation(null);
    }

    /**************************************************************************
     * Add a copy of the specified group information object to the group
     * information list
     * 
     * @param groupToCopy
     *            group information object to copy
     * 
     * @param nameOfCopy
     *            name of the copy of the group
     *************************************************************************/
    @Override
    protected void addInformation(Object groupToCopy, String nameOfCopy)
    {
        // Store the group information for the copy
        groupHandler.getGroupInformation().add(new GroupInformation(nameOfCopy,
                                                                    ((GroupInformation) groupToCopy).getDescription(),
                                                                    ((GroupInformation) groupToCopy).isApplication(),
                                                                    ((GroupInformation) groupToCopy).getTables().toArray(new String[0]),
                                                                    ((GroupInformation) groupToCopy).getFieldInformation()));
    }

    /**************************************************************************
     * Initialize the group definition list with the group names and
     * descriptions
     * 
     * @return List containing the groups with their names and descriptions
     *************************************************************************/
    @Override
    protected List<String[]> createDefinitionsFromInformation()
    {
        // Initialize the group tree information list
        List<String[]> definitions = new ArrayList<String[]>();

        // Step through each group's information
        for (GroupInformation grpInfo : groupHandler.getGroupInformation())
        {
            // Add the group's name and description to the list. If the group
            // represents a CFS application it's definition begins with a
            // non-zero numeral
            definitions.add(new String[] {grpInfo.getName(),
                                          (grpInfo.isApplication()
                                                                  ? "1"
                                                                  : "0")
                                              + ","
                                              + grpInfo.getDescription()});
        }

        return definitions;
    }

    /**************************************************************************
     * Set the node text color based on the currently selected schedule rate
     * and the rate of the group to which the node belongs: black for a match
     * and gray for a mismatch
     * 
     * @param startNode
     *            starting node for which to adjust the text and color
     *************************************************************************/
    protected void adjustNodeText(ToolTipTreeNode startNode, List<String> excludes)
    {
        // Step through the node's children, if any
        for (Enumeration<?> element = startNode.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node reference
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Get the tree level for this node
            int level = node.getLevel();

            // Check that the tree has any levels. When the tree is first
            // created this method is called when no nodes exist
            if (level > 0)
            {
                // Get the group name from the node. The group name is the
                // second node in the path for this node
                String groupName = removeExtraText(node.getPath()[1].toString());

                // Get the reference to the group's information
                GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

                // Check that the node references a group
                if (groupInfo != null)
                {
                    // Get the node name
                    String nodeName = node.getUserObject().toString();

                    // Set to true if the group in this path is not excluded
                    // (as evidenced by having a HTML tag)
                    boolean wasExcluded = nodeName.contains(DISABLED_TEXT_COLOR);

                    // Remove any HTML tags or other extra text from the node
                    // name
                    nodeName = removeExtraText(nodeName);

                    // Get the reference to the schedule rate field information
                    fieldHandler.setFieldInformation(groupInfo.getFieldInformation());
                    FieldInformation rateInfo = fieldHandler.getFieldInformationByName(CcddFieldHandler.getFieldGroupName(groupName),
                                                                                       DefaultApplicationField.SCHEDULE_RATE.getFieldName());

                    // Set the flag indicating the group is excluded if it's in
                    // the exclusion list
                    boolean isExcluded = rateInfo == null
                                         || rateInfo.getValue().isEmpty()
                                         || !scheduleRate.equals(rateInfo.getValue())
                                         || excludes.contains(nodeName);

                    // Check if the group's exclusion state has changed
                    if (wasExcluded != isExcluded)
                    {
                        // Reset the node name to indicate its
                        // inclusion/exclusion state. If excluded, prepend the
                        // HTML tag to gray out the name. Indicate that the
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

    /**************************************************************************
     * Create a group tree panel. The table tree is placed in a scroll pane. A
     * check box is added that allows tree expansion/collapse
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
     *************************************************************************/
    protected JPanel createTreePanel(String label,
                                     int selectionMode,
                                     boolean noFilters,
                                     final Component parent)
    {
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
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   0),
                                                        0,
                                                        0);

        // Set the table tree font and number of rows to display
        setFont(LABEL_FONT_PLAIN);
        setVisibleRowCount(10);

        // Set the table tree selection mode
        getSelectionModel().setSelectionMode(selectionMode);

        // Create a panel to contain the table tree
        JPanel treePnl = new JPanel(new GridBagLayout());
        treePnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the tree labels
        JLabel treeLbl = new JLabel(label);
        treeLbl.setFont(LABEL_FONT_BOLD);
        treeLbl.setForeground(LABEL_TEXT_COLOR);
        treePnl.add(treeLbl, gbc);

        // Create the tree scroll pane
        JScrollPane treeScroll = new JScrollPane(this);
        treeScroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                Color.LIGHT_GRAY,
                                                                                                Color.GRAY),
                                                                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        // Set the preferred width of the tree's scroll pane
        treeScroll.setPreferredSize(new Dimension(Math.min(Math.max(treeScroll.getPreferredSize().width,
                                                                    200),
                                                           400),
                                                  treeScroll.getPreferredSize().height));
        treeScroll.setMinimumSize(treeScroll.getPreferredSize());

        // Check if this is the last component to add
        if (noFilters)
        {
            gbc.insets.bottom = 0;
        }

        // Add the tree to the panel
        gbc.weighty = 1.0;
        gbc.gridy++;
        treePnl.add(treeScroll, gbc);

        // Add a listener for changes to the group tree
        addTreeSelectionListener(new TreeSelectionListener()
        {
            /******************************************************************
             * Handle a change to the group tree selection
             *****************************************************************/
            @Override
            public void valueChanged(TreeSelectionEvent lse)
            {
                // Check that a group tree (re)build isn't in progress.
                // Building the tree triggers tree selection value changes that
                // should not be processed
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
            expandChkBx.setBorder(BorderFactory.createEmptyBorder());
            expandChkBx.setFont(LABEL_FONT_BOLD);
            expandChkBx.setSelected(false);
            gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
            gbc.weighty = 0.0;
            gbc.gridy++;
            treePnl.add(expandChkBx, gbc);

            // Create a listener for changes in selection of the tree expansion
            // check box
            expandChkBx.addActionListener(new ActionListener()
            {
                /**************************************************************
                 * Handle a change to the tree expansion check box selection
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Set the flag indicating if the tree is fully expanded
                    isExpanded = expandChkBx.isSelected();

                    // Set the tree expansion based on the check box status
                    setTreeExpansion(isExpanded);
                }
            });

            // Create a type filter check box
            final JCheckBox typeFilterChkBx = new JCheckBox("Filter by type");
            typeFilterChkBx.setBorder(BorderFactory.createEmptyBorder());
            typeFilterChkBx.setFont(LABEL_FONT_BOLD);
            typeFilterChkBx.setSelected(false);
            gbc.gridy++;
            treePnl.add(typeFilterChkBx, gbc);

            // Create an application filter check box
            final JCheckBox appFilterChkBx = new JCheckBox("Filter by application");
            appFilterChkBx.setBorder(BorderFactory.createEmptyBorder());
            appFilterChkBx.setFont(LABEL_FONT_BOLD);
            appFilterChkBx.setSelected(false);
            gbc.gridy++;
            treePnl.add(appFilterChkBx, gbc);

            // Create a listener for changes in selection of the type filter
            // or application filter check boxes
            ActionListener listener = new ActionListener()
            {
                /**************************************************************
                 * Handle a change to the type filter check box selection
                 *************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Recreate the group definitions based on the current tree
                    // members
                    groupDefinitions = createDefinitionsFromTree();

                    // Rebuild the tree based on the filter selection
                    buildTree(typeFilterChkBx.isSelected(),
                              appFilterChkBx.isSelected(),
                              scheduleRate,
                              isApplicationOnly,
                              parent);
                }
            };

            // Add the filter check box listener to the type and application
            // check boxes
            typeFilterChkBx.addActionListener(listener);
            appFilterChkBx.addActionListener(listener);
        }

        return treePnl;
    }
}
