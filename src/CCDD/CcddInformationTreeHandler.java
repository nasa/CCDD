/**
 * CFS Command & Data Dictionary generic information tree handler. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.TableCommentIndex;

/******************************************************************************
 * CFS Command & Data Dictionary generic information tree handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public abstract class CcddInformationTreeHandler extends CcddCommonTreeHandler
{
    // Class references
    private CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;

    // Components referenced by multiple methods
    private DefaultTreeModel infoTreeModel;
    private ToolTipTreeNode root;

    // List to contain the node names and their child paths
    private List<String[]> treeDefinitions;

    // Flags to indicate if the tree should be filtered by table type or by
    // application
    private boolean isFilterByType;
    private boolean isFilterByApp;

    // Array containing the comments parameters for each table
    private String[][] tableComments;

    // String value that may be used to modify the tree building method; null
    // or blank if not filtering
    private String filterValue;

    // List of all tree paths in the table tree in the order to be maintained
    // in the information tree
    private List<String> treePathOrder;

    // Information tree child node insertion order, relative to the sibling
    // nodes
    private enum TreeChildOrder
    {
        NATURAL, // Insert children below existing siblings
        ALPHABETICAL, // Insert children alphabetically
        PATH_ORDER; // Insert children based on a tree path order list
    }

    /**************************************************************************
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
     *            string value that may be used to modify the tree building
     *            method; null or blank if not filtering
     * 
     * @param filterFlag
     *            flag used to filter the tree content
     * 
     * @param treePathOrder
     *            list containing all of the items that potentially can appear
     *            in the tree in the order in which they appear when added to
     *            the tree; null if no order is specified (the order can be
     *            specified later, if needed)
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    CcddInformationTreeHandler(CcddMain ccddMain,
                               CcddUndoHandler undoHandler,
                               InternalTable infoType,
                               String filterValue,
                               boolean filterFlag,
                               List<String> treePathOrder,
                               Component parent)
    {
        super(ccddMain);

        dataTypeHandler = ccddMain.getDataTypeHandler();
        this.treePathOrder = treePathOrder;

        // Get the internal table definitions from the database
        List<String[]> infoDefinitions = ccddMain.getDbTableCommandHandler().retrieveInformationTable(infoType,
                                                                                                      parent);

        // Check that the information definitions loaded successfully
        if (infoDefinitions != null)
        {
            this.filterValue = filterValue;

            // Create the tree's root node
            root = new ToolTipTreeNode("", "");

            // Set the tree model and hide the root node from view. Use the
            // undoable tree model if an undo handler is provided; otherwise
            // use the default (non-undoable) tree model
            infoTreeModel = undoHandler != null
                                               ? undoHandler.new UndoableTreeModel(root)
                                               : new DefaultTreeModel(root);
            setModel(infoTreeModel);
            setRootVisible(false);

            // Perform any initialization steps needed for this information
            // table prior to building its tree
            initialize(ccddMain, infoDefinitions);

            // Build the information tree
            buildTree(false, false, filterValue, filterFlag, parent);
        }
    }

    /**************************************************************************
     * Get the node level that skips any active filter nodes
     * 
     * @return Node level for tree nodes below the active filter nodes
     *************************************************************************/
    @Override
    protected int getHeaderNodeLevel()
    {
        return (isFilterByApp ? 1 : 0) + (isFilterByType ? 1 : 0);
    }

    /**************************************************************************
     * Get the node level that skips any active filter nodes and other header
     * nodes. The default number of header nodes is 2; this method can be
     * overridden to adjust the header levels
     * 
     * @return Node level for tree nodes below the filter and header nodes
     *************************************************************************/
    protected int getItemNodeLevel()
    {
        return getHeaderNodeLevel() + 2;
    }

    /**************************************************************************
     * Get the node level that skips the group filter node
     * 
     * @return Node level for groups
     *************************************************************************/
    protected int getGroupNodeLevel()
    {
        return (isFilterByApp ? 2 : 1);
    }

    /**************************************************************************
     * Override the table tree's tool tip text handler to provide the
     * descriptions of the nodes
     *************************************************************************/
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

    /**************************************************************************
     * Placeholder for method to perform any initialization steps necessary
     * prior to building the information tree
     * 
     * @param ccddMain
     *            main class
     * 
     * @param infoDefinitions
     *            list containing the internal table definitions
     *************************************************************************/
    protected abstract void initialize(CcddMain ccddMain,
                                       List<String[]> infoDefinitions);

    /**************************************************************************
     * Create the information tree root node and set the tree model
     * 
     * @param isFilterByType
     *            true if the tree is filtered by table type
     * 
     * @param isFilterByApp
     *            true if the tree is filtered by application status
     * 
     * @param filterValue
     *            string value that may be used to modify the tree building
     *            method; null or blank if not filtering
     * 
     * @param filterFlag
     *            flag used to filter the tree content
     * 
     * @param parent
     *            GUI component calling this method
     *************************************************************************/
    protected void buildTree(boolean isFilterByType,
                             boolean isFilterByApp,
                             String filterValue,
                             boolean filterFlag,
                             Component parent)
    {
        this.isFilterByType = isFilterByType;
        this.isFilterByApp = isFilterByApp;
        this.filterValue = filterValue;

        // Clear the current tree in case this is a rebuild
        root.removeAllChildren();
    }

    /**************************************************************************
     * Get the information tree root node
     * 
     * @return Information tree root node
     *************************************************************************/
    protected ToolTipTreeNode getRootNode()
    {
        return root;
    }

    /**************************************************************************
     * Set the table type handler, and table comments array. This is only
     * required if the table can be filtered by table type
     * 
     * @param tableTypeHandler
     *            table type handler reference
     * 
     * @param tableComments
     *            array containing the comment parameters for each table
     *************************************************************************/
    protected void setHandlersAndComments(CcddTableTypeHandler tableTypeHandler,
                                          String[][] tableComments)
    {
        this.tableTypeHandler = tableTypeHandler;
        this.tableComments = tableComments;
    }

    /**************************************************************************
     * Set the list of all tree paths in the table tree in the order to be used
     * when placing a path in the information tree
     * 
     * @param treePathOrder
     *            list of all paths in the table tree in the order to be
     *            maintained in the information tree
     *************************************************************************/
    protected void setTreePathOrder(List<String> treePathOrder)
    {
        this.treePathOrder = treePathOrder;
    }

    /**************************************************************************
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
     * @param index
     *            index in the parent at which to insert the new node
     * 
     * @param order
     *            order in which the added node should be placed relative to
     *            the child nodes already preset
     * 
     * @return Reference to the newly added child node
     *************************************************************************/
    private ToolTipTreeNode addNode(ToolTipTreeNode parentNode,
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
                    // Check if the child node's name is alphabetically after
                    // the new child node's name
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

                // Get the path of the new child node and locate it within the
                // path order list
                int childIndex = treePathOrder.indexOf(createNameFromPath(path.toArray(new Object[0]),
                                                                          getItemNodeLevel()));

                // Step backwards through the existing sibling nodes
                for (int nodeIndex = parentNode.getChildCount() - 1; nodeIndex >= 0; nodeIndex--)
                {
                    // Get the path of the sibling node and locate it within
                    // the path order list
                    ToolTipTreeNode siblingPath = (ToolTipTreeNode) parentNode.getChildAt(nodeIndex);
                    int siblingIndex = treePathOrder.indexOf(createNameFromPath(siblingPath.getPath(),
                                                                                getItemNodeLevel()));

                    // Check if the sibling appears in the path order before
                    // the new child
                    if (siblingIndex < childIndex)
                    {
                        // Store the insertion index and stop searching
                        index = nodeIndex + 1;
                        break;
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

    /**************************************************************************
     * Add a new node to the tree's root node
     * 
     * @param nodeName
     *            name of the node to add
     * 
     * @param toolTipText
     *            tool tip text for the new node
     * 
     * @return Reference to the newly added node
     *************************************************************************/
    protected ToolTipTreeNode addInformationNode(String nodeName,
                                                 String toolTipText)
    {
        return addNode(root, nodeName, toolTipText, TreeChildOrder.ALPHABETICAL);
    }

    /**************************************************************************
     * Add a new node to the tree's root node
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
     *************************************************************************/
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

    /**************************************************************************
     * Add the specified nodes(s) from the source tree to the specified node(s)
     * in the information tree
     * 
     * @param sourcePaths
     *            list containing the node paths for the selected nodes in the
     *            source tree
     * 
     * @param startIndex
     *            tree level at which the desired node names first appear in
     *            the source tree
     * 
     * @param onlyIfPrimitive
     *            true to only include nodes that end with a reference to
     *            primitive data type; false to include all nodes
     *************************************************************************/
    protected void addSourceNodesToTargetNode(List<Object[]> sourcePaths,
                                              int startIndex,
                                              boolean onlyIfPrimitive)
    {
        // Check that at least one node is selected in the source tree and at
        // least one node is selected in the information tree
        if (sourcePaths != null && getSelectionCount() != 0)
        {
            List<ToolTipTreeNode> infoNodes = new ArrayList<ToolTipTreeNode>();

            // Step through each selected information node's path
            for (TreePath infoPath : getSelectionPaths())
            {
                // Get the top level node for this path
                ToolTipTreeNode infoNode = (ToolTipTreeNode) infoPath.getPathComponent(getGroupNodeLevel());

                // Check if this top level node has already been modified. This
                // can occur if more than one node in the top level node's tree
                // is selected
                if (!infoNodes.contains(infoNode))
                {
                    // Add the top level node to the list so that it won't be
                    // checked again
                    infoNodes.add(infoNode);

                    // Step through each selected node path in the source tree
                    for (Object[] sourcePath : sourcePaths)
                    {
                        boolean isPrimitive = false;

                        // Check if only nodes ending with a reference to a
                        // primitive data type should be included
                        if (onlyIfPrimitive)
                        {
                            // Get the name of the last node in the path
                            String lastNode = sourcePath[sourcePath.length - 1].toString();

                            // Get the index that bounds the variable's data
                            // type
                            int lastPeriod = lastNode.lastIndexOf(".");

                            // Check that a data type was found and that it is
                            // a primitive type. In case the tree allows HTML
                            // formatted nodes remove any HTML tags before
                            // checking the data type
                            if (lastPeriod != -1
                                && dataTypeHandler.isPrimitive(removeExtraText(lastNode.substring(0,
                                                                                                  lastPeriod))))
                            {
                                // Set the flag to indicate this node
                                // references a primitive data type
                                isPrimitive = true;
                            }
                        }

                        // Check if all nodes should be included, or if the
                        // node references a primitive data type
                        if (!onlyIfPrimitive || isPrimitive)
                        {
                            // Add the source node to this top level node in
                            // the information tree
                            addNodeToInfoNode(infoNode, sourcePath, startIndex);
                        }
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Add the specified variable(s) to the specified information node(s)
     * 
     * @param node
     *            parent information node for this variable
     * 
     * @param sourcePath
     *            array containing the source node path
     * 
     * @param startIndex
     *            tree level at which the variable names first appear in the
     *            array
     *************************************************************************/
    protected void addNodeToInfoNode(ToolTipTreeNode node,
                                     Object[] sourcePath,
                                     int startIndex)
    {
        // Check if the tree is filtered by table type
        if (isFilterByType)
        {
            // Get the source node's prototype table name
            String protoName = sourcePath[startIndex].toString().split(Pattern.quote("."))[0];

            // Step through each data table comment
            for (String[] tableComment : tableComments)
            {
                // Check if the table name matches the source node's prototype
                // name
                if (tableComment[TableCommentIndex.NAME.ordinal()].equals(protoName))
                {
                    int typeIndex = 0;

                    // Step through each table type
                    for (String type : tableTypeHandler.getTypes())
                    {
                        // Check if the table type matches the type represented
                        // by this prototype table
                        if (type.equals(tableComment[TableCommentIndex.TYPE.ordinal()]))
                        {
                            // Set the node to the table type node and stop
                            // searching
                            node = (ToolTipTreeNode) node.getChildAt(typeIndex);
                            break;
                        }

                        typeIndex++;
                    }

                    break;
                }
            }
        }

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
                    // Update the node to this child node and set the flag
                    // indicating the tree node already exists, then stop
                    // searching
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

    /**************************************************************************
     * Remove the specified child node, and its ancestors until a node with
     * other children or the top level node is reached
     * 
     * @param removeNode
     *            node at which to start the removal
     *************************************************************************/
    protected void removeNodeAndEmptyAncestors(ToolTipTreeNode removeNode)
    {
        // Check if the node has no siblings and is not the top level in the
        // sub-tree
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

    /**************************************************************************
     * Remove the currently selected top level node(s) and all of its child
     * nodes from the information tree
     *************************************************************************/
    protected void removeSelectedTopLevelNodes()
    {
        List<ToolTipTreeNode> removedNodes = new ArrayList<ToolTipTreeNode>();

        // Step through each selected path
        for (TreePath path : getSelectionPaths())
        {
            // Get the node for this path
            ToolTipTreeNode node = (ToolTipTreeNode) path.getPathComponent(1);

            // Check if this node has already been removed
            if (!removedNodes.contains(node))
            {
                // Add the node to the list of those removed
                removedNodes.add(node);

                // Remove the node from the tree
                infoTreeModel.removeNodeFromParent(node);
            }
        }
    }

    /**************************************************************************
     * Remove the currently selected child node(s) from the selected top level
     * node(s)
     * 
     * @param isVariable
     *            true if the this tree contains variables
     *************************************************************************/
    protected void removeSelectedChildNodes(boolean isVariable)
    {
        List<Object[]> selectedVariablePaths = new ArrayList<Object[]>();

        // Check if at least one node is selected
        if (getSelectionCount() != 0)
        {
            // Step through each selected node
            for (TreePath path : getSelectionPaths())
            {
                // Check if the selected variable node has children
                addChildNodes((ToolTipTreeNode)
                              path.getLastPathComponent(),
                              selectedVariablePaths,
                              new ArrayList<String>(),
                              isVariable);
            }

            // Step through the selected paths
            for (Object[] varPath : selectedVariablePaths)
            {
                // Get the node referenced by the path
                TreePath path = new TreePath(varPath);
                ToolTipTreeNode node = (ToolTipTreeNode) path.getLastPathComponent();

                // In order to remove all of a child's path that isn't shared
                // with another child, step back through the child's path to
                // find the ancestor node with only a single child node
                while (node.getParent().getChildCount() == 1
                       && node.getLevel() > 2 + getHeaderNodeLevel())
                {
                    // Get the parent node for the child(ren) to be removed
                    node = (ToolTipTreeNode) node.getParent();
                }

                // Remove the node from the information tree
                infoTreeModel.removeNodeFromParent(node);
            }
        }
    }

    /**************************************************************************
     * Get the top-level node name(s) associated with the selected node(s)
     * 
     * @return Array containing the top-level node name(s) associated with the
     *         selected node(s); an empty array if no node is selected
     *************************************************************************/
    protected String[] getTopLevelSelectedNodeNames()
    {
        List<String> selectedNodeNames = new ArrayList<String>();

        // Step through each selected top-level node
        for (ToolTipTreeNode selectedNode : getTopLevelSelectedNodes())
        {
            // Store the node name
            selectedNodeNames.add(selectedNode.getUserObject().toString());

        }

        return selectedNodeNames.toArray(new String[0]);
    }

    /**************************************************************************
     * Get the top-level node(s) associated with the selected node(s)
     * 
     * @return Array containing the top-level node(s) associated with the
     *         selected node(s); an empty array if no node is selected
     *************************************************************************/
    protected ToolTipTreeNode[] getTopLevelSelectedNodes()
    {
        List<ToolTipTreeNode> selectedNodes = new ArrayList<ToolTipTreeNode>();

        // Get the array of selected node indices
        int[] rows = getSelectionRows();

        // Check if at least one node is selected
        if (rows != null && rows.length != 0)
        {
            // Step through each selected row
            for (int row : rows)
            {
                // Get the path for this row
                TreePath path = getPathForRow(row);

                // Check if the node represents a top-level node or its
                // children
                if (path.getPathCount() > getGroupNodeLevel())
                {
                    // Get the name of the node
                    ToolTipTreeNode node = (ToolTipTreeNode) path.getPathComponent(getGroupNodeLevel());

                    // Check if a selected node has not yet been added to the
                    // list
                    if (!selectedNodes.contains(node))
                    {
                        // Store the node name
                        selectedNodes.add(node);
                    }
                }
            }
        }

        return selectedNodes.toArray(new ToolTipTreeNode[0]);
    }

    /**************************************************************************
     * Rename a top-level node (a node that is a direct child of the root node)
     * 
     * @param oldName
     *            current name of the node
     * 
     * @param newName
     *            new name for the node
     * 
     * @return Reference to the renamed node
     *************************************************************************/
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
                node.setUserObject(newName);
                infoTreeModel.nodeChanged(node);
                break;
            }
        }

        return node;
    }

    /**************************************************************************
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
     *************************************************************************/
    protected void copyNodeTree(String originalName,
                                String nameOfCopy,
                                Object infoToCopy)
    {
        // Step through each row in the information tree
        for (int row = 0; row < infoTreeModel.getChildCount(root); row++)
        {
            // Get the path for the row
            TreePath path = getPathForRow(row);

            // Check if this node represents a top level name and that the name
            // matches the target
            if (path.getPathCount() == 2
                && path.getLastPathComponent().toString().equals(originalName))
            {
                // Store the information for the copy
                addInformation(infoToCopy, nameOfCopy);

                // Create a node for the top level and add it to the
                // information tree
                ToolTipTreeNode newNode = addInformationNode(nameOfCopy, "");

                // Copy the source node's tree to the copy and stop searching
                copySubTree((ToolTipTreeNode) path.getLastPathComponent(),
                            newNode);
                break;
            }
        }
    }

    /**************************************************************************
     * Placeholder for method to add a copy of the specified information object
     * to the information list
     * 
     * @param information
     *            information object to copy
     * 
     * @param nameOfCopy
     *            name of the copy of the node
     *************************************************************************/
    protected abstract void addInformation(Object information,
                                           String nameOfCopy);

    /**************************************************************************
     * Recursively copy the tree of a source node to a target node
     * 
     * @param source
     *            source node
     * 
     * @param target
     *            target node
     *************************************************************************/
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

    /**************************************************************************
     * Create the information definitions from the tree for storage in the
     * database or to use as a comparison to a previously committed version of
     * the tree.
     * 
     * @return List containing the path array for each child node belonging to
     *         an information node
     *************************************************************************/
    protected List<String[]> createDefinitionsFromTree()
    {
        // Initialize the definitions list
        treeDefinitions = createDefinitionsFromInformation();

        // Start with the root node and step through the tree to find the child
        // nodes and their member variable paths and add these to the
        // definition list
        buildDefinitionFromTree(root);

        return treeDefinitions;
    }

    /**************************************************************************
     * Placeholder to initialize the definition list
     * 
     * @return Initialized definition list
     *************************************************************************/
    protected abstract List<String[]> createDefinitionsFromInformation();

    /**************************************************************************
     * Placeholder to remove any unwanted text from the node names. The object
     * array is converted to a string array
     * 
     * @param node
     *            path array
     * 
     * @return Cleaned node path array
     *************************************************************************/
    protected String[] cleanNodePath(Object[] nodePath)
    {
        return CcddUtilities.convertObjectToString(nodePath);
    }

    /**************************************************************************
     * Recursively step through the information tree and append the path to
     * each leaf node to the definition list
     * 
     * @param node
     *            node to check
     *************************************************************************/
    private void buildDefinitionFromTree(ToolTipTreeNode node)
    {
        // Check if this is the last node in this path (i.e., the 'leaf', which
        // is a table or primitive variable depending on the internal table
        // type)
        if (node.getChildCount() == 0
            && node.getLevel() > 1 + getHeaderNodeLevel())
        {
            // Create storage for the leaf definition and path
            List<String> leafDefinition = new ArrayList<String>();

            // Get the node path for the leaf
            String[] nodePath = cleanNodePath(node.getPath());

            // Store the top level name
            leafDefinition.add(nodePath[getGroupNodeLevel()]);

            // Get the leaf node's path, converted from an array to a string
            leafDefinition.add(convertLeafNodePath(nodePath));

            // Check if a leaf exists for this node
            if (!leafDefinition.get(1).isEmpty())
            {
                // Store the leaf node definition in the tree information list
                addLeafDefinition(treeDefinitions,
                                  leafDefinition,
                                  filterValue);
            }
        }
        // This node has child nodes (i.e., this node is in the path for a
        // leaf node)
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

    /**************************************************************************
     * Add the specified leaf definition to the tree definition list. Override
     * this method to insert other information into the leaf definition prior
     * to adding it to the tree definition list
     * 
     * @param treeDefns
     *            list to which to add the leaf definition
     * 
     * @param leafDefn
     *            leaf definition to add to the list
     * 
     * @param filterValue
     *            string value that may be used to modify the tree; null or
     *            blank if not used
     *************************************************************************/
    protected void addLeafDefinition(List<String[]> treeDefns,
                                     List<String> leafDefn,
                                     String filterValue)
    {
        // Store the leaf node definition in the tree information list
        treeDefns.add(leafDefn.toArray(new String[0]));
    }

    /**************************************************************************
     * Convert the array describing the leaf node path into a single string,
     * with the nodes separated by commas
     * 
     * @param path
     *            array describing the tree path to construct
     * 
     * @return Node names, separated by commas, in the specified path
     *************************************************************************/
    protected String convertLeafNodePath(Object[] path)
    {
        return convertLeafNodePath(path, 2);
    }

    /**************************************************************************
     * Convert the array describing the leaf node path into a single string,
     * with the nodes separated by commas
     * 
     * @param path
     *            array describing the tree path to construct
     * 
     * @param startAdjust
     *            starting node level adjustment
     * 
     * @return Node names, separated by commas, in the specified path
     *************************************************************************/
    protected String convertLeafNodePath(Object[] path, int startAdjust)
    {
        String leafPath = "";

        // Step through the nodes in the path. Skip the table type node if
        // present
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
