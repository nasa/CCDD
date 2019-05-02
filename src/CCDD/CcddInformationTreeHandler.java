/**
 * CFS Command and Data Dictionary generic information tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddConstants.InternalTable;

/**************************************************************************************************
 * CFS Command and Data Dictionary generic information tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public abstract class CcddInformationTreeHandler extends CcddCommonTreeHandler
{
    // Class references
    private final CcddDataTypeHandler dataTypeHandler;

    // Components referenced by multiple methods
    private DefaultTreeModel infoTreeModel;
    private ToolTipTreeNode root;

    // List to contain the node names and their child paths
    private List<String[]> treeDefinitions;

    // Flags to indicate if the tree should be filtered by application
    private boolean isFilterByApp;

    // String value that may be used to modify the tree building method; null or blank if not
    // filtering
    private String filterValue;

    // List of all tree paths in the table tree in the order to be maintained in the information
    // tree
    private List<String> treePathOrder;

    // Information tree child node insertion order, relative to the sibling nodes
    private enum TreeChildOrder
    {
        NATURAL, // Insert children below existing siblings
        ALPHABETICAL, // Insert children alphabetically
        PATH_ORDER; // Insert children based on a tree path order list
    }

    /**********************************************************************************************
     * Generic information tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param infoType
     *            internal table type
     *
     * @param infoDefinitions
     *            list of string arrays containing each node name and associated tool tip
     *
     * @param filterValue
     *            string value that may be used to modify the tree building method; null or blank
     *            if not filtering
     *
     * @param filterFlag
     *            flag used to filter the tree content
     *
     * @param treePathOrder
     *            list containing all of the items that potentially can appear in the tree in the
     *            order in which they appear when added to the tree; null if no order is specified
     *            (the order can be specified later, if needed)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddInformationTreeHandler(CcddMain ccddMain,
                               CcddUndoHandler undoHandler,
                               InternalTable infoType,
                               List<String[]> infoDefinitions,
                               String filterValue,
                               boolean filterFlag,
                               List<String> treePathOrder,
                               Component parent)
    {
        super(ccddMain);

        dataTypeHandler = ccddMain.getDataTypeHandler();
        this.treePathOrder = treePathOrder;

        // Check that the information definitions loaded successfully
        if (infoDefinitions != null)
        {
            this.filterValue = filterValue;

            // Create the tree's root node
            root = new ToolTipTreeNode("", "");

            // Set the tree model and hide the root node from view. Use the undoable tree model if
            // an undo handler is provided; otherwise use the default (non-undoable) tree model
            infoTreeModel = undoHandler != null
                                                ? undoHandler.new UndoableTreeModel(root)
                                                {
                                                    /***********************************************
                                                     * Perform any actions needed following an undo
                                                     * or redo operation that adds or removes a
                                                     * node
                                                     *********************************************/
                                                    @Override
                                                    protected void nodeAddRemoveCleanup()
                                                    {
                                                        CcddInformationTreeHandler.this.nodeAddRemoveCleanup();
                                                    }

                                                    /**********************************************
                                                     * Perform any actions needed following an undo
                                                     * or redo operation that affects a node's user
                                                     * object (name) filter nodes
                                                     *
                                                     * @param wasValue
                                                     *            node user object value prior to
                                                     *            the undo/redo operation
                                                     *
                                                     * @param isValue
                                                     *            node user object value after the
                                                     *            undo/redo operation
                                                     *********************************************/
                                                    @Override
                                                    protected void nodeRenameCleanup(Object wasValue,
                                                                                     Object isValue)
                                                    {
                                                        CcddInformationTreeHandler.this.nodeRenameCleanup(wasValue,
                                                                                                          isValue);
                                                    }
                                                }
                                                : new DefaultTreeModel(root);
            setModel(infoTreeModel);
            setRootVisible(false);

            // Perform any initialization steps needed for this information table prior to building
            // its tree
            initialize(ccddMain, undoHandler, infoDefinitions);

            // Build the information tree
            buildTree(false, filterValue, filterFlag, parent);
        }
    }

    /**********************************************************************************************
     * Generic information tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param infoType
     *            internal table type
     *
     * @param filterValue
     *            string value that may be used to modify the tree building method; null or blank
     *            if not filtering
     *
     * @param filterFlag
     *            flag used to filter the tree content
     *
     * @param treePathOrder
     *            list containing all of the items that potentially can appear in the tree in the
     *            order in which they appear when added to the tree; null if no order is specified
     *            (the order can be specified later, if needed)
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddInformationTreeHandler(CcddMain ccddMain,
                               CcddUndoHandler undoHandler,
                               InternalTable infoType,
                               String filterValue,
                               boolean filterFlag,
                               List<String> treePathOrder,
                               Component parent)
    {
        // Get the internal table definitions from the database
        this(ccddMain,
             undoHandler,
             infoType,
             ccddMain.getDbTableCommandHandler().retrieveInformationTable(infoType,
                                                                          false,
                                                                          parent),
             filterValue,
             filterFlag,
             treePathOrder,
             parent);
    }

    /**********************************************************************************************
     * Generic information tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param infoDefinitions
     *            list of string arrays containing each node name and associated tool tip
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddInformationTreeHandler(CcddMain ccddMain,
                               List<String[]> infoDefinitions,
                               Component parent)
    {
        this(ccddMain, null, null, infoDefinitions, null, false, null, parent);
    }

    /**********************************************************************************************
     * Placeholder for performing any actions needed following an undo or redo operation that adds
     * or removes a node
     *********************************************************************************************/
    protected void nodeAddRemoveCleanup()
    {
    }

    /**********************************************************************************************
     * Placeholder for performing any actions needed following an undo or redo operation that
     * affects a node's user object (name) filter nodes
     *
     * @param wasValue
     *            node user object value prior to the undo/redo operation
     *
     * @param isValue
     *            node user object value after the undo/redo operation
     *********************************************************************************************/
    protected void nodeRenameCleanup(Object wasValue, Object isValue)
    {
    }

    /**********************************************************************************************
     * Get the node level that skips any active filter nodes
     *
     * @return Node level for tree nodes below the active filter nodes
     *********************************************************************************************/
    @Override
    protected int getHeaderNodeLevel()
    {
        return (isFilterByApp ? 1 : 0);
    }

    /**********************************************************************************************
     * Get the node level that skips any active filter nodes and other header nodes. The default
     * number of header nodes is 2; this method can be overridden to adjust the header levels
     *
     * @return Node level for tree nodes below the filter and header nodes
     *********************************************************************************************/
    protected int getItemNodeLevel()
    {
        return getHeaderNodeLevel() + 2;
    }

    /**********************************************************************************************
     * Get the node level that skips the group filter node
     *
     * @return Node level for groups
     *********************************************************************************************/
    protected int getGroupNodeLevel()
    {
        return (isFilterByApp ? 2 : 1);
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
     * Placeholder for method to perform any initialization steps necessary prior to building the
     * information tree
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param infoDefinitions
     *            list containing the internal table definitions
     *********************************************************************************************/
    protected abstract void initialize(CcddMain ccddMain,
                                       CcddUndoHandler undoHandler,
                                       List<String[]> infoDefinitions);

    /**********************************************************************************************
     * Create the information tree root node and set the tree model
     *
     * @param isFilterByApp
     *            true if the tree is filtered by application status
     *
     * @param filterValue
     *            string value that may be used to modify the tree building method; null or blank
     *            if not filtering
     *
     * @param filterFlag
     *            flag used to filter the tree content
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected void buildTree(boolean isFilterByApp,
                             String filterValue,
                             boolean filterFlag,
                             Component parent)
    {
        this.isFilterByApp = isFilterByApp;
        this.filterValue = filterValue;

        // Clear the current tree in case this is a rebuild
        root.removeAllChildren();
    }

    /**********************************************************************************************
     * Get the information tree root node
     *
     * @return Information tree root node
     *********************************************************************************************/
    protected ToolTipTreeNode getRootNode()
    {
        return root;
    }

    /**********************************************************************************************
     * Set the list of all tree paths in the table tree in the order to be used when placing a path
     * in the information tree
     *
     * @param treePathOrder
     *            list of all paths in the table tree in the order to be maintained in the
     *            information tree
     *********************************************************************************************/
    protected void setTreePathOrder(List<String> treePathOrder)
    {
        this.treePathOrder = treePathOrder;
    }

    /**********************************************************************************************
     * Add a node to the tree
     *
     * @param parentNode
     *            parent node for the new node
     *
     * @param nodeName
     *            name of the node to add
     *
     * @param toolTipText
     *            tool tip text for the new node
     *
     * @param order
     *            order in which the added node should be placed relative to the child nodes
     *            already preset
     *
     * @return Reference to the newly added child node
     *********************************************************************************************/
    private ToolTipTreeNode addNode(final ToolTipTreeNode parentNode,
                                    String nodeName,
                                    String toolTipText,
                                    TreeChildOrder order)
    {
        // Create a node for the new child node
        ToolTipTreeNode childNode = new ToolTipTreeNode(nodeName, toolTipText);

        int index = 0;

        switch (order)
        {
            case NATURAL:
                // Insert the child node below any existing sibling nodes
                index = parentNode.getChildCount();

                break;

            case ALPHABETICAL:
                // Step through the parent node's children
                for (; index < parentNode.getChildCount(); index++)
                {
                    // Check if the child node's name is alphabetically after the new child node's
                    // name
                    if (removeExtraText(((ToolTipTreeNode) parentNode.getChildAt(index)).getUserObject().toString()).compareToIgnoreCase(nodeName) > 0)
                    {
                        // Stop searching the node
                        break;
                    }
                }

                break;

            case PATH_ORDER:
                // Combine the parent and child paths
                List<TreeNode> path = new ArrayList<TreeNode>();
                path.addAll(Arrays.asList(parentNode.getPath()));
                path.addAll(Arrays.asList(childNode.getPath()));

                // Set the insertion point to the end of the tree in the event the child isn't in
                // the path order list
                index = parentNode.getChildCount();

                // Get the path of the new child node and locate it within the path order list
                int childIndex = treePathOrder.indexOf(createNameFromPath(path.toArray(new Object[0]),
                                                                          getItemNodeLevel()));

                // Check if the child is present in the path order list
                if (childIndex != -1)
                {
                    // Step backwards through the existing sibling nodes
                    for (int nodeIndex = parentNode.getChildCount() - 1; nodeIndex >= 0; nodeIndex--)
                    {
                        // Get the path of the sibling node and locate it within the path order
                        // list
                        ToolTipTreeNode siblingPath = (ToolTipTreeNode) parentNode.getChildAt(nodeIndex);
                        int siblingIndex = treePathOrder.indexOf(createNameFromPath(siblingPath.getPath(),
                                                                                    getItemNodeLevel()));

                        // Check if the sibling appears in the path order before the new child
                        if (siblingIndex < childIndex)
                        {
                            // Store the insertion index and stop searching
                            index = nodeIndex + 1;
                            break;
                        }
                    }
                }

                break;
        }

        // Insert the new child node at the specified index
        infoTreeModel.insertNodeInto(childNode, parentNode, index);

        // Check if this is the first node added to the tree
        if (parentNode.equals(root) && parentNode.getChildCount() == 1)
        {
            // Force the root node to draw with the node addition
            infoTreeModel.nodeStructureChanged(root);
        }

        return childNode;
    }

    /**********************************************************************************************
     * Add a new node to the tree's root node
     *
     * @param nodeName
     *            name of the node to add
     *
     * @param toolTipText
     *            tool tip text for the new node
     *
     * @return Reference to the newly added node
     *********************************************************************************************/
    protected ToolTipTreeNode addInformationNode(String nodeName, String toolTipText)
    {
        return addNode(root, nodeName, toolTipText, TreeChildOrder.ALPHABETICAL);
    }

    /**********************************************************************************************
     * Add a new node to the tree's root node, or the application node is filtering by application
     * is active
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
     * @return Reference to the newly added node
     *********************************************************************************************/
    protected ToolTipTreeNode addInformationNode(String nodeName,
                                                 String toolTipText,
                                                 boolean isApp)
    {
        return addNode(isFilterByApp
                                     ? (ToolTipTreeNode) root.getChildAt(isApp
                                                                               ? 0
                                                                               : 1)
                                     : root,
                       nodeName,
                       toolTipText,
                       TreeChildOrder.ALPHABETICAL);
    }

    /**********************************************************************************************
     * Add the specified nodes(s) from the source tree to the specified node(s) in the information
     * tree
     *
     * @param sourcePaths
     *            list containing the node paths for the selected nodes in the source tree
     *
     * @param startIndex
     *            tree level at which the desired node names first appear in the source tree
     *
     * @param onlyIfPrimitive
     *            true to only include nodes that end with a reference to primitive data type;
     *            false to include all nodes
     *********************************************************************************************/
    protected void addSourceNodesToTargetNode(List<Object[]> sourcePaths,
                                              int startIndex,
                                              boolean onlyIfPrimitive)
    {
        // Check that at least one node is selected in the source tree and at least one node is
        // selected in the information tree
        if (sourcePaths != null && getSelectionCount() != 0)
        {
            List<ToolTipTreeNode> infoNodes = new ArrayList<ToolTipTreeNode>();

            // Step through each selected information node's path
            for (TreePath infoPath : getSelectionPaths())
            {
                // Get the top level node for this path
                ToolTipTreeNode infoNode = (ToolTipTreeNode) infoPath.getPathComponent(getGroupNodeLevel());

                // Check if this top level node has already been modified. This can occur if more
                // than one node in the top level node's tree is selected
                if (!infoNodes.contains(infoNode))
                {
                    // Add the top level node to the list so that it won't be checked again
                    infoNodes.add(infoNode);

                    // Step through each selected node path in the source tree
                    for (Object[] sourcePath : sourcePaths)
                    {
                        boolean isPrimitive = false;

                        // Check if only nodes ending with a reference to a primitive data type
                        // should be included
                        if (onlyIfPrimitive)
                        {
                            // Get the name of the last node in the path
                            String lastNode = sourcePath[sourcePath.length - 1].toString();

                            // Get the index that bounds the variable's data type
                            int lastPeriod = lastNode.lastIndexOf(".");

                            // Check that a data type was found and that it is a primitive type. In
                            // case the tree allows HTML formatted nodes remove any HTML tags
                            // before checking the data type
                            if (lastPeriod != -1
                                && dataTypeHandler.isPrimitive(removeExtraText(lastNode.substring(0,
                                                                                                  lastPeriod))))
                            {
                                // Set the flag to indicate this node references a primitive data
                                // type
                                isPrimitive = true;
                            }
                        }

                        // Check if all nodes should be included, or if the node references a
                        // primitive data type
                        if (!onlyIfPrimitive || isPrimitive)
                        {
                            // Add the source node to this top level node in the information tree
                            addNodeToInfoNode(infoNode, sourcePath, startIndex);
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Add the specified table(s)/variable(s) to the specified information node(s)
     *
     * @param node
     *            parent information node for this variable
     *
     * @param sourcePath
     *            array containing the source node path
     *
     * @param startIndex
     *            tree level at which the variable names first appear in the array
     *********************************************************************************************/
    protected void addNodeToInfoNode(ToolTipTreeNode node, Object[] sourcePath, int startIndex)
    {
        // Step through the source path
        for (int index = startIndex; index < sourcePath.length; index++)
        {
            boolean isFound = false;

            // Get the node name at this tree level
            String nodeName = sourcePath[index].toString();

            // Step through the current node's children
            for (int childIndex = 0; childIndex < node.getChildCount(); childIndex++)
            {
                // Check if the node already contains this source tree's node
                if (node.getChildAt(childIndex).toString().equals(nodeName))
                {
                    // Update the node to this child node and set the flag indicating the tree node
                    // already exists, then stop searching
                    node = (ToolTipTreeNode) node.getChildAt(childIndex);
                    isFound = true;
                    break;
                }
            }

            // Check if this node doesn't exist in the information tree
            if (!isFound)
            {
                // Add the node to the information tree
                node = addNode(node,
                               nodeName,
                               "",
                               treePathOrder == null
                                                     ? TreeChildOrder.NATURAL
                                                     : TreeChildOrder.PATH_ORDER);

                // Expand the tree to display the added node
                expandPath(new TreePath(((ToolTipTreeNode) node.getParent()).getPath()));
            }
        }
    }

    /**********************************************************************************************
     * Remove the specified child node, and its ancestors until a node with other children or the
     * top level node is reached
     *
     * @param removeNode
     *            node at which to start the removal
     *********************************************************************************************/
    protected void removeNodeAndEmptyAncestors(ToolTipTreeNode removeNode)
    {
        // Check if the node has no siblings and is not the top level in the sub-tree
        while (removeNode.getParent() != null
               && removeNode.getParent().getChildCount() == 1
               && ((ToolTipTreeNode) removeNode.getParent()).getLevel() > getHeaderNodeLevel())
        {
            // Set the node to remove to the child node's parent node
            removeNode = (ToolTipTreeNode) removeNode.getParent();
        }

        // Remove the node(s)
        removeNode.removeFromParent();
    }

    /**********************************************************************************************
     * Remove the currently selected top level node(s) and all of its child nodes from the
     * information tree
     *********************************************************************************************/
    protected void removeSelectedTopLevelNodes()
    {
        List<ToolTipTreeNode> removedNodes = new ArrayList<ToolTipTreeNode>();

        // Step through each selected path
        for (TreePath path : getSelectionPaths())
        {
            // Get the top-level node for this path
            ToolTipTreeNode node = (ToolTipTreeNode) path.getPathComponent(1);

            // Check if this node hasn't already been removed
            if (!removedNodes.contains(node))
            {
                // Add the node to the list of those removed
                removedNodes.add(node);

                // Remove the node from the tree
                infoTreeModel.removeNodeFromParent(node);
            }
        }
    }

    /**********************************************************************************************
     * Get the top-level node name(s) associated with the selected node(s)
     *
     * @return Array containing the top-level node name(s) associated with the selected node(s); an
     *         empty array if no node is selected
     *********************************************************************************************/
    protected String[] getTopLevelSelectedNodeNames()
    {
        List<String> selectedNodeNames = new ArrayList<String>();

        // Step through each selected top-level node path
        for (TreePath selectedPath : getTopLevelSelectedPaths())
        {
            // Store the node name
            selectedNodeNames.add(((ToolTipTreeNode) selectedPath.getLastPathComponent()).getUserObject().toString());
        }

        return selectedNodeNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get an array containing the selected node path(s)
     *
     * @return Array containing the selected node path(s); an empty array if no node is selected
     *********************************************************************************************/
    protected TreePath[] getSelectedPaths()
    {
        List<TreePath> selectedPaths = new ArrayList<TreePath>();

        // Get the array of selected tree node indices
        int[] rows = getSelectionRows();

        // Check if at least one node is selected
        if (rows != null && rows.length != 0)
        {
            // Step through each selected row
            for (int row : rows)
            {
                // Get the path for this row
                TreePath path = getPathForRow(row);

                // Check if the node represents a node that's not a a header or filter, and that
                // the selected node has not yet been added to the list
                if (path.getPathCount() > getGroupNodeLevel() && !selectedPaths.contains(path))
                {
                    // Store the node path
                    selectedPaths.add(path);
                }
            }
        }

        return selectedPaths.toArray(new TreePath[0]);
    }

    /**********************************************************************************************
     * Get the top-level node path(s) associated with the selected node path(s)
     *
     * @return Array containing the top-level node path(s) associated with the selected node
     *         path(s); an empty array if no node is selected
     *********************************************************************************************/
    protected TreePath[] getTopLevelSelectedPaths()
    {
        List<TreePath> selectedPaths = new ArrayList<TreePath>();

        // Get the array of selected node paths
        TreePath[] paths = getSelectionPaths();

        // Check if at least one node is selected
        if (paths != null && paths.length != 0)
        {
            // Step through each selected node
            for (TreePath path : paths)
            {
                // Check if the path represents a top-level node or its children
                if (path.getPathCount() > getGroupNodeLevel())
                {
                    // Get the top-level path for this node path
                    TreePath topPath = getPathFromNode((ToolTipTreeNode) path.getPathComponent(getGroupNodeLevel()));

                    // Check if a selected node has not yet been added to the list
                    if (!selectedPaths.contains(topPath))
                    {
                        // Store the node name
                        selectedPaths.add(topPath);
                    }
                }
            }
        }

        return selectedPaths.toArray(new TreePath[0]);
    }

    /**********************************************************************************************
     * Rename a top-level node (a node that is a direct child of the root node)
     *
     * @param oldName
     *            current name of the node
     *
     * @param newName
     *            new name for the node
     *
     * @return Reference to the renamed node; null if no node with the name oldName exists
     *********************************************************************************************/
    protected ToolTipTreeNode renameRootChildNode(Object oldName, Object newName)
    {
        ToolTipTreeNode node = null;

        // Step through each row in the information tree
        for (int row = 0; row < root.getChildCount(); row++)
        {
            // Check if this node's name matches the target
            if (((ToolTipTreeNode) root.getChildAt(row)).getUserObject().toString().equals((oldName)))
            {
                // Get the node from the path
                node = (ToolTipTreeNode) root.getChildAt(row);

                // Rename the node and update the tree, then stop searching
                getModel().valueForPathChanged(getPathForRow(row), newName);
                break;
            }
        }

        return node;
    }

    /**********************************************************************************************
     * Copy the tree of a source node to a target node
     *
     * @param originalName
     *            name of the node to copy
     *
     * @param nameOfCopy
     *            name of the copy of the node
     *
     * @param infoToCopy
     *            information object to copy
     *********************************************************************************************/
    protected void copyNodeTree(String originalName, String nameOfCopy, Object infoToCopy)
    {
        // Step through each row in the information tree
        for (int row = 0; row < infoTreeModel.getChildCount(root); row++)
        {
            // Get the path for the row
            TreePath path = getPathForRow(row);

            // Check if this node represents a top level name and that the name matches the target
            if (path.getPathCount() == 2
                && path.getLastPathComponent().toString().equals(originalName))
            {
                // Store the information for the copy
                addInformation(infoToCopy, nameOfCopy);

                // Create a node for the top level and add it to the information tree
                ToolTipTreeNode newNode = addInformationNode(nameOfCopy, "");

                // Copy the source node's tree to the copy and stop searching
                copySubTree((ToolTipTreeNode) path.getLastPathComponent(), newNode);
                break;
            }
        }
    }

    /**********************************************************************************************
     * Placeholder for method to add a copy of the specified information object to the information
     * list
     *
     * @param information
     *            information object to copy
     *
     * @param nameOfCopy
     *            name of the copy of the node
     *********************************************************************************************/
    protected abstract void addInformation(Object information, String nameOfCopy);

    /**********************************************************************************************
     * Recursively copy the tree of a source node to a target node
     *
     * @param source
     *            source node
     *
     * @param target
     *            target node
     *********************************************************************************************/
    protected void copySubTree(ToolTipTreeNode source, ToolTipTreeNode target)
    {
        // Step through each child of the source node
        for (int index = 0; index < source.getChildCount(); index++)
        {
            // Get the child's node and create a copy of it
            ToolTipTreeNode child = (ToolTipTreeNode) source.getChildAt(index);
            ToolTipTreeNode clone = new ToolTipTreeNode(child.getUserObject().toString(), "");

            // Add the copy of the node to the target sub-tree
            target.add(clone);

            // Copy this child's sub-tree to the copy
            copySubTree(child, clone);
        }
    }

    /**********************************************************************************************
     * Create the information definitions from the tree for storage in the database or to use as a
     * comparison to a previously committed version of the tree.
     *
     * @return List containing the path array for each child node belonging to an information node
     *********************************************************************************************/
    protected List<String[]> createDefinitionsFromTree()
    {
        // Initialize the definitions list
        treeDefinitions = createDefinitionsFromInformation();

        // TODO TIME AN OPERATION USING THESE STEPS:
        // DateTimeFormatter dtf = DateTimeFormatter.ofPattern("mm:ss.SSS");// TODO
        // System.out.println("\nStart: " + dtf.format(LocalDateTime.now()));// TODO
        // ...perform operation
        // System.out.println(" ...done: " + dtf.format(LocalDateTime.now()));// TODO

        // Start with the root node and step through the tree to find the child nodes and their
        // member variable paths and add these to the definition list
        buildDefinitionFromTree(root);

        return treeDefinitions;
    }

    /**********************************************************************************************
     * Placeholder to initialize the definition list
     *
     * @return Initialized definition list
     *********************************************************************************************/
    protected abstract List<String[]> createDefinitionsFromInformation();

    /**********************************************************************************************
     * Remove any unwanted text from the node names. The object array is converted to a string
     * array
     *
     * @param nodePath
     *            path array
     *
     * @return Cleaned node path array
     *********************************************************************************************/
    protected String[] cleanNodePath(Object[] nodePath)
    {
        // Convert the object array to a string array
        String path[] = CcddUtilities.convertObjectToString(nodePath);

        // Step through each node path
        for (int index = 0; index < nodePath.length; index++)
        {
            // Remove the HTML tags and rate/size text
            path[index] = removeExtraText(path[index]);
        }

        return path;
    }

    /**********************************************************************************************
     * Recursively step through the information tree and append the path to each leaf node to the
     * definition list
     *
     * @param node
     *            node to check
     *********************************************************************************************/
    private void buildDefinitionFromTree(ToolTipTreeNode node)
    {
        // Check if this is the last node in this path (i.e., the 'leaf', which is a table or
        // primitive variable depending on the internal table type)
        if (node.getChildCount() == 0 && node.getLevel() > 1 + getHeaderNodeLevel())
        {
            // Get the node path for the leaf
            String[] nodePath = cleanNodePath(node.getPath());

            String[] leafDefinition = new String[2];

            // Get the leaf node's path, converted from an array to a string
            leafDefinition[1] = convertLeafNodePath(nodePath);

            // Check if a leaf exists for this node
            if (!leafDefinition[1].isEmpty())
            {
                // Store the top level name
                leafDefinition[0] = nodePath[getGroupNodeLevel()];

                // Store the leaf node definition in the tree information list
                addLeafDefinition(treeDefinitions, leafDefinition, filterValue);
            }
        }
        // This node has child nodes (i.e., this node is in the path for a leaf node)
        else
        {
            // Step through the each child node on this level
            for (int index = 0; index < node.getChildCount(); index++)
            {
                // Get the information for this child node
                buildDefinitionFromTree((ToolTipTreeNode) node.getChildAt(index));
            }
        }
    }

    /**********************************************************************************************
     * Add the specified leaf definition to the tree definition list. Override this method to
     * insert other information into the leaf definition prior to adding it to the tree definition
     * list
     *
     * @param treeDefns
     *            list to which to add the leaf definition
     *
     * @param leafDefn
     *            leaf definition to add to the list
     *
     * @param filterValue
     *            string value that may be used to modify the tree; null or blank if not used
     *********************************************************************************************/
    protected void addLeafDefinition(List<String[]> treeDefns,
                                     String[] leafDefn,
                                     String filterValue)
    {
        // Store the leaf node definition in the tree information list
        treeDefns.add(leafDefn);
    }

    /**********************************************************************************************
     * Convert the array describing the leaf node path into a single string, with the nodes
     * separated by commas
     *
     * @param path
     *            array describing the tree path to construct
     *
     * @return Node names, separated by commas, in the specified path
     *********************************************************************************************/
    protected String convertLeafNodePath(Object[] path)
    {
        return convertLeafNodePath(path, 2);
    }

    /**********************************************************************************************
     * Convert the array describing the leaf node path into a single string, with the nodes
     * separated by commas
     *
     * @param path
     *            array describing the tree path to construct
     *
     * @param startAdjust
     *            starting node level adjustment
     *
     * @return Node names, separated by commas, in the specified path
     *********************************************************************************************/
    protected String convertLeafNodePath(Object[] path, int startAdjust)
    {
        String leafPath = "";

        // Step through the nodes in the path. Skip the table type node if present
        for (int index = startAdjust + getHeaderNodeLevel(); index < path.length; index++)
        {
            // Store the leaf node name in the path array
            leafPath += path[index].toString() + ",";
        }

        // Check if a node was added to the path
        if (!leafPath.isEmpty())
        {
            // Remove the trailing comma
            leafPath = CcddUtilities.removeTrailer(leafPath, ",");
        }

        return leafPath;
    }
}
