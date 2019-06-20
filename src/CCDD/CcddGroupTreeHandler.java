/**
 * CFS Command and Data Dictionary group tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.GROUP_ICON;
import static CCDD.CcddConstants.INVALID_TEXT_COLOR;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
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

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableCommentIndex;

/**************************************************************************************************
 * CFS Command and Data Dictionary group tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddGroupTreeHandler extends CcddInformationTreeHandler
{
    // Class references
    private CcddGroupHandler groupHandler;
    private CcddTableTypeHandler tableTypeHandler;
    private ToolTipTreeNode root;
    private CcddFieldHandler fieldHandler;

    // Flag to indicate if the tree should be filtered by application
    private boolean isFilterByApp;

    // Flag indicating if the group tree nodes are expanded or not
    private boolean isExpanded;

    // List to contain the group definitions (groups and variable paths) retrieved from the
    // database
    private List<String[]> groupDefinitions;

    // Flag to indicate if the group tree is being built
    private boolean isBuilding;

    // Array containing the comment parameters for each table
    private ArrayListMultiple tableComments;

    // Currently selected group's schedule rate
    private String scheduleRate;

    // Flag indication only application groups should be displayed
    private boolean isApplicationOnly;

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
     *            GUI component over which to center any error dialog
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
     *            GUI component over which to center any error dialog
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
        tableTypeHandler = ccddMain.getTableTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        groupHandler = new CcddGroupHandler(undoHandler);

        // Set the tree to be collapsed initially
        isExpanded = false;

        // Set the selected schedule rate to a blank
        scheduleRate = "";

        // Get the list of table comments
        tableComments = new ArrayListMultiple(TableCommentIndex.NAME.ordinal());
        tableComments.addAll(Arrays.asList(ccddMain.getDbTableCommandHandler()
                                                   .queryDataTableComments(ccddMain.getMainFrame())));
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
     * Get the node level that skips any active filter nodes and accounts for the table type node
     * level
     *
     * @return Node level for tree nodes below the active filter nodes
     *********************************************************************************************/
    @Override
    protected int getHeaderNodeLevel()
    {
        return super.getHeaderNodeLevel() + 1;
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
     * Add a new node to the tree's root node, or the application node is filtering by application
     * is active. Add the table types as nodes to the new node depending on the supplied flag
     *
     * @param nodeName
     *            name of the node to add
     *
     * @param toolTipText
     *            tool tip text for the new node
     *
     * @param isApp
     *            true if the group represents a CFS application
     *
     * @param isAddTableTypes
     *            true if nodes with the names of the table types should be added to the new node
     *
     * @return Array of table type nodes added to the group node; null if the table type nodes
     *         aren't added
     *********************************************************************************************/
    protected ToolTipTreeNode[] addInformationNode(String nodeName,
                                                   String toolTipText,
                                                   boolean isApp,
                                                   boolean isAddTableTypes)
    {
        ToolTipTreeNode[] typeNodes = null;

        // Add the node to the tree
        ToolTipTreeNode groupNode = super.addInformationNode(nodeName, toolTipText, isApp);

        // Check if the table type nodes should be added
        if (isAddTableTypes)
        {
            int index = 0;
            typeNodes = new ToolTipTreeNode[tableTypeHandler.getTableTypeNames().length];

            // Step through each table type
            for (String type : tableTypeHandler.getTableTypeNames())
            {
                // Create the node for this table type and add it to the group node
                typeNodes[index] = new ToolTipTreeNode(type,
                                                       tableTypeHandler.getTypeDefinition(type)
                                                                       .getDescription());
                ((DefaultTreeModel) getModel()).insertNodeInto(typeNodes[index],
                                                               groupNode,
                                                               index);

                index++;
            }
        }

        return typeNodes;
    }

    /**********************************************************************************************
     * Build the group tree
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
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    @Override
    protected void buildTree(boolean filterByApp,
                             String scheduleRate,
                             boolean isApplicationOnly,
                             Component parent)
    {
        this.isFilterByApp = filterByApp;
        this.scheduleRate = scheduleRate;
        this.isApplicationOnly = isApplicationOnly;

        super.buildTree(isFilterByApp, scheduleRate, isApplicationOnly, parent);

        // Tree nodes for the table types if filtering by type and for application status if
        // filtering by application
        ToolTipTreeNode[] typeNodes = null;
        ToolTipTreeNode[] appNodes = null;

        // Get the tree's root node
        root = getRootNode();

        // Build the group information using the group definitions and the group data fields from
        // the database
        groupHandler.buildGroupInformation(groupDefinitions);
        buildFieldInformation(parent);

        // Register the tool tip manager for the group tree (otherwise the tool tips aren't
        // displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Set the flag to indicate that the group tree is being built. This flag is used to
        // inhibit actions involving tree selection value changes during the build process
        isBuilding = true;

        // Check if the application statuses are to be used to filter the group tree
        if (isFilterByApp)
        {
            // Create the node storage for the application statuses
            appNodes = new ToolTipTreeNode[] {addInformationNode(APP_NODE,
                                                                 "Groups representing a CFS application"),
                                              addInformationNode(OTHER_NODE,
                                                                 "Groups not representing a CFS application")};
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
                typeNodes = addInformationNode(groupName,
                                               groupInfo.getDescription(),
                                               groupInfo.isApplication(),
                                               scheduleRate == null || scheduleRate.isEmpty());

                // Check if the table nodes should be displayed (i.e., no schedule rate is
                // supplied)
                if (typeNodes != null)
                {
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
                                    if (groupName.equals(((ToolTipTreeNode) appNodes[nodeIndex].getChildAt(index)).getUserObject()
                                                                                                                  .toString()))
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
                            // Locate the index of this table's comment in the comment list
                            int commentIndex = tableComments.indexOf(TableInformation.getPrototypeName(table));

                            // Check if the table was located in the list. It's possible to import
                            // groups containing references to tables that don't exist in the
                            // database
                            if (commentIndex != -1)
                            {
                                // Get the table type for the current group table member
                                String tableType = tableComments.get(commentIndex)[TableCommentIndex.TYPE.ordinal()];

                                // Step through each table type node
                                for (int nodeIndex = 0; nodeIndex < typeNodes.length; nodeIndex++)
                                {
                                    // Check if the group table's type matches the type node name
                                    if (tableType.equals(typeNodes[nodeIndex].getUserObject().toString()))
                                    {
                                        // Separate the table path into each table reference
                                        // (dataType<.variableName>)
                                        String[] sourcePath = table.split(",");

                                        // Step through each table reference in the path
                                        for (int index = 0; index < sourcePath.length; index++)
                                        {
                                            // Get the comment for this table reference
                                            commentIndex = tableComments.indexOf(TableInformation.getPrototypeName(sourcePath[index]));

                                            // Check if the table type for this reference doesn't
                                            // match the current table type node or if the table
                                            // type couldn't be determined
                                            if (commentIndex == -1
                                                || !tableType.equals(tableComments.get(commentIndex)[TableCommentIndex.TYPE.ordinal()]))
                                            {
                                                // Flag the table reference as not belonging to
                                                // this table type (the node may still appear if
                                                // it's in the path of a table reference that does
                                                // belong to the table type)
                                                sourcePath[index] = INVALID_TEXT_COLOR
                                                                    + sourcePath[index];
                                            }
                                        }

                                        // Add the table reference to the table type node
                                        addNodeToInfoNode(typeNodes[nodeIndex],
                                                          sourcePath,
                                                          0);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Set the table type and application node enable status
        setHeaderNodeEnable();

        // Expand or collapse the tree based on the expansion flag
        setTreeExpansion(isExpanded);

        // Clear the flag that indicates the group tree is being built
        isBuilding = false;
    }

    /**********************************************************************************************
     * Build the group tree following a change in the group's application status
     *
     * @param groupName
     *            name of the group with the application status change
     *
     * @param isApplication
     *            true if the group represents a CFS application
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void buildTree(String groupName, boolean isApplication, Component parent)
    {
        // Check if the tree is filtered by application
        if (isFilterByApp)
        {
            // Get a reference to the group's information
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

            // Check if the group's information exists
            if (groupInfo != null)
            {
                // Get the tree's expansion state
                String expState = getExpansionState();

                // Change any references to the group from (to) the application node to (from) the
                // other node, and ensure the application and other nodes are expanded
                expState = expState.replaceAll((isApplication
                                                              ? OTHER_NODE
                                                              : APP_NODE)
                                               + ", "
                                               + groupName,
                                               (isApplication
                                                              ? APP_NODE
                                                              : OTHER_NODE)
                                                            + ", "
                                                            + groupName)
                           + (isApplication
                                            ? "[, " + APP_NODE + "],"
                                            : "[, " + OTHER_NODE + "],");

                // Step through each group definition
                for (int index = 0; index < groupDefinitions.size(); index++)
                {
                    // Get the group definition
                    String[] groupDefn = groupDefinitions.get(index);

                    // Check if this is the application status and description for the specified
                    // group
                    if (groupDefn[1].matches("\\d,.*") && groupName.equals(groupDefn[0]))
                    {
                        // Change the group's application status to the one supplied (but preserve
                        // the description, if any) and stop searching
                        groupDefinitions.set(index,
                                             new String[] {groupName,
                                                           (isApplication
                                                                          ? "1"
                                                                          : "0")
                                                                      + groupDefn[1].substring(1)});
                        break;
                    }
                }

                // Build the group tree with the specified group moved from/to the Application node
                buildTree(isFilterByApp, null, false, parent);

                // Restore the expansion state
                setExpansionState(expState);
            }
        }
    }

    /**********************************************************************************************
     * Flag the table type or application header nodes as disabled if the node has no child nodes
     *********************************************************************************************/
    protected void setHeaderNodeEnable()
    {
        // Step through the root node's children, if any
        for (Enumeration<?> element = root.preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the node referenced
            ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

            // Check if the node is a table type or application header
            if (node.getLevel() < getItemNodeLevel()
                && node.getLevel() != getGroupNodeLevel()
                && node != root)
            {
                // Get the node name with any HTML tags removed
                String nodeName = CcddUtilities.removeHTMLTags(node.getUserObject().toString());

                // Flag the node as disabled if it has no children
                node.setUserObject(node.getChildCount() == 0
                                                             ? DISABLED_TEXT_COLOR + nodeName
                                                             : nodeName);
            }
        }
    }

    /**********************************************************************************************
     * Add the specified table(s) to the specified group node(s)
     *
     * @param node
     *            parent information node for this table
     *
     * @param sourcePath
     *            array containing the source node path
     *
     * @param startIndex
     *            tree level at which the table names first appear in the array
     *********************************************************************************************/
    @Override
    protected void addNodeToInfoNode(ToolTipTreeNode node, Object[] sourcePath, int startIndex)
    {
        // Check if the tree is filtered by table type and the target node is not a table type
        // node. This occurs when transferring a node from the table tree to the group tree in the
        // group manager, but is not needed when building the group tree
        if (node.getLevel() <= getHeaderNodeLevel())
        {
            // Remove any HTML tags and convert the path to a string array
            String[] tablePath = cleanNodePath(sourcePath);

            // Step through each table type node
            for (int typeIndex = 0; typeIndex < node.getChildCount(); typeIndex++)
            {
                // Get the table type represented by this node
                String tableType = CcddUtilities.removeHTMLTags(((ToolTipTreeNode) node.getChildAt(typeIndex)).getUserObject().toString());

                // Step through each table reference in the table path
                for (int index = startIndex; index < tablePath.length; index++)
                {
                    // Check if the type for the table in the reference matches the table type
                    // represented by this node
                    if (tableType.equals(tableComments.get(tableComments.indexOf(TableInformation.getPrototypeName(tablePath[index])))[TableCommentIndex.TYPE.ordinal()]))
                    {
                        int firstValidIndex = -1;

                        // Copy the table path array so that HTML formatting can be applied without
                        // affected the original array
                        String[] tempPath = Arrays.copyOf(tablePath, tablePath.length);

                        // Step through each table reference in the table path
                        for (int pathIndex = startIndex; pathIndex < tempPath.length; pathIndex++)
                        {
                            // Check if the table type for this table reference doesn't match the
                            // node's table type
                            if (!tableType.equals(tableComments.get(tableComments.indexOf(TableInformation.getPrototypeName(tempPath[pathIndex])))[TableCommentIndex.TYPE.ordinal()]))
                            {
                                // Flag the table reference as not matching the node table type
                                tempPath[pathIndex] = INVALID_TEXT_COLOR + tempPath[pathIndex];
                            }
                            // The type for the table reference matches the node table type
                            else
                            {
                                // Store the (last) valid. This is used to prune the branch of all
                                // non-matching table references up to the first one that mat
                                firstValidIndex = pathIndex;
                            }
                        }

                        // Add the table path to the table type node
                        super.addNodeToInfoNode((ToolTipTreeNode) node.getChildAt(typeIndex),
                                                Arrays.copyOf(tempPath, firstValidIndex + 1),
                                                startIndex);
                    }
                }
            }
        }
        else
        {
            // Add the table path to the specified node
            super.addNodeToInfoNode(node, sourcePath, startIndex);
        }
    }

    /**********************************************************************************************
     * Build the group data field information
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    private void buildFieldInformation(Component parent)
    {
        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Set the field information in the group handler
            groupInfo.setFieldInformation(fieldHandler.getFieldInformationByOwnerCopy(CcddFieldHandler.getFieldGroupName(groupInfo.getName())));
        }
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
        List<String[]> definitions = new ArrayList<String[]>(groupHandler.getGroupInformation().size());

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
     * Override adding a group definition entry in order to look for and prune duplicates.
     * Duplicates can occur if the tree is filtered by table type
     *********************************************************************************************/
    @Override
    protected void addLeafDefinition(List<String[]> treeDefns,
                                     String[] leafDefn,
                                     String filterValue)
    {
        boolean isFound = false;
        int index = 0;

        // Step through the existing group definitions
        for (String[] treeDefn : treeDefns)
        {
            // Check if the group names are the same
            if (leafDefn[0].equals(treeDefn[0]))
            {
                // Check if the table path length differs and the path to add contains the
                // existing path (that is, the added path is a superset of the existing one)
                if (treeDefn[1].length() != leafDefn[1].length()
                    && leafDefn[1].startsWith(treeDefn[1] + ","))
                {
                    // Replace the existing definition with the added one, set the flag to
                    // indicate the added one has been handled, and stop searching
                    treeDefns.set(index, leafDefn);
                    isFound = true;
                    break;
                }
                // Check if this is an identical table path or a subset (due to a table
                // reference being pruned)
                else if (treeDefn[1].equals(leafDefn[1]) ||
                         treeDefn[1].startsWith(leafDefn[1] + ","))
                {
                    // Ignore the added definition, set the flag to indicate the added one has
                    // been handled, and stop searching
                    isFound = true;
                    break;
                }
            }

            index++;
        }

        // The added group entry doesn't match an existing one
        if (!isFound)
        {
            // Add the group entry to the definition list
            super.addLeafDefinition(treeDefns, leafDefn, filterValue);
        }
    }

    /**********************************************************************************************
     * Set the node text color based on the currently selected schedule rate and the rate of the
     * group to which the node belongs: black for a match and gray for a mismatch
     *
     * @param startNode
     *            starting node for which to adjust the text and color
     *
     * @param excludes
     *            list of groups names to be excluded (group name is grayed out in the tree)
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

                    // Set to true if the group in this path was excluded (as evidenced by having a
                    // HTML tag)
                    boolean wasExcluded = nodeName.startsWith(DISABLED_TEXT_COLOR);

                    // Remove any HTML tags or other extra text from the node name
                    nodeName = removeExtraText(nodeName);

                    // Get the reference to the schedule rate field information
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
     * Remove the currently selected item node(s) from the tree. If a header node is selected then
     * remove all of its item nodes
     *********************************************************************************************/
    protected void removeSelectedItemNodes()
    {
        List<ToolTipTreeNode> removeNodes = new ArrayList<ToolTipTreeNode>();

        // Step through each selected node
        for (TreePath path : getSelectionPaths())
        {
            // Get the selected node's item nodes (including the selected node if it's an item
            // node)
            getItemNodes((ToolTipTreeNode) path.getLastPathComponent(), removeNodes);
        }

        // Step through the list of item nodes to remove
        for (ToolTipTreeNode node : removeNodes)
        {
            // Remove the node (and any descendant nodes of the node) from the tree
            ((DefaultTreeModel) getModel()).removeNodeFromParent(node);
        }
    }

    /**********************************************************************************************
     * Add the specified node to the list of item nodes if it represents an item. If this is a
     * header node then get the item nodes from the header's child nodes. This is a recursive
     * method
     *
     * @param node
     *            node to add to the list if the node represents an item, or a header node
     *
     * @param itemNodes
     *            list containing the item nodes
     *
     * @return List containing the item nodes
     *********************************************************************************************/
    private List<ToolTipTreeNode> getItemNodes(ToolTipTreeNode node,
                                               List<ToolTipTreeNode> itemNodes)
    {
        // Check if the node represents a table
        if (node.getLevel() >= getItemNodeLevel())
        {
            // Add the node to the list of item nodes
            itemNodes.add(node);
        }
        // A header node is selected
        else
        {
            // Step through the node's child nodes
            for (int index = 0; index < node.getChildCount(); index++)
            {
                // Get the item nodes from the child node
                getItemNodes((ToolTipTreeNode) node.getChildAt(index), itemNodes);
            }
        }

        return itemNodes;
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
     *            GUI component over which to center any error dialog
     *
     * @return JPanel containing the group tree components
     *********************************************************************************************/
    protected JPanel createTreePanel(String label,
                                     int selectionMode,
                                     boolean noFilters,
                                     final Component parent)
    {
        // Set the renderer for the tree so that custom icons can be used for the various node
        // types
        setCellRenderer(new VariableTreeCellRenderer()
        {
            // Tree node row height storage. Setting a row's preferred height to 0 causes it to not
            // be displayed
            int rowHeight = 0;

            /**************************************************************************************
             * Display the group nodes using a special icon in the tree
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
                Component comp = super.getTreeCellRendererComponent(tree,
                                                                    value,
                                                                    sel,
                                                                    expanded,
                                                                    leaf,
                                                                    row,
                                                                    hasFocus);

                // Get the tree level for this node
                int level = ((ToolTipTreeNode) value).getLevel();

                // Check if this node represents a group name
                if (level == getGroupNodeLevel())
                {
                    // Display an icon indicating a group
                    setIcon(new ImageIcon(getClass().getResource(GROUP_ICON)));
                }

                // Check if the node row height hasn't been stored
                if (rowHeight == 0)
                {
                    // Store the normal node row height
                    rowHeight = comp.getPreferredSize().height;
                }

                ToolTipTreeNode node = (ToolTipTreeNode) value;

                // REset the preferred width so that the node's size is correctly calculated
                comp.setPreferredSize(null);

                // Check if the node should be hidden
                if (!(node.getLevel() != getGroupNodeLevel() + 1
                      || !node.getUserObject().toString().startsWith(DISABLED_TEXT_COLOR)))
                {
                    // Set the hidden node's row height to zero
                    comp.setPreferredSize(new Dimension(comp.getPreferredSize().width, 0));
                }
                // The node should be displayed
                else
                {
                    // Set the node's row height to the normal value
                    comp.setPreferredSize(new Dimension(comp.getPreferredSize().width, rowHeight));
                }

                return this;
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

                    // Force the root node to draw with the node additions
                    refreshTree();
                }
            });

            // Create an application filter check box
            final JCheckBox appFilterChkBx = new JCheckBox("Filter by application");
            appFilterChkBx.setBorder(emptyBorder);
            appFilterChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            appFilterChkBx.setSelected(false);
            gbc.gridy++;
            treePnl.add(appFilterChkBx, gbc);

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
                    buildTree(appFilterChkBx.isSelected(),
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
                                                    true,
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
