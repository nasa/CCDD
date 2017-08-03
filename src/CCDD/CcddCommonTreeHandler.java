/**
 * CFS Command & Data Dictionary common tree handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.BIT_VARIABLE_ICON;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LINKED_BIT_VARIABLE_ICON;
import static CCDD.CcddConstants.LINKED_PACKED_VARIABLE_ICON;
import static CCDD.CcddConstants.LINKED_VARIABLE_ICON;
import static CCDD.CcddConstants.PACKED_VARIABLE_ICON;
import static CCDD.CcddConstants.VARIABLE_ICON;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddClasses.NodeIndex;
import CCDD.CcddClasses.ToolTipTreeNode;

/******************************************************************************
 * CFS Command & Data Dictionary common tree handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddCommonTreeHandler extends JTree
{
    // Class references
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddMacroHandler macroHandler;

    // Tree icons depicting variables
    private final Icon variableIcon;
    private final Icon bitVariableIcon;
    private final Icon packedVariableIcon;
    private final Icon linkedVariableIcon;
    private final Icon linkedBitVariableIcon;
    private final Icon linkedPackedVariableIcon;

    // Index of the row containing the last variable in a group of bit-packed
    // variables
    private int lastPackRow;

    // Flag that indicates if table node selection changes should be ignored
    // for undo/redo consideration
    private boolean ignoreSelectionChange;

    /**************************************************************************
     * Common tree handler class constructor
     *************************************************************************/
    CcddCommonTreeHandler(CcddMain ccddMain)
    {
        super();

        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();

        // Create the tree icons depicting variables
        variableIcon = new ImageIcon(getClass().getResource(VARIABLE_ICON));
        bitVariableIcon = new ImageIcon(getClass().getResource(BIT_VARIABLE_ICON));
        packedVariableIcon = new ImageIcon(getClass().getResource(PACKED_VARIABLE_ICON));
        linkedVariableIcon = new ImageIcon(getClass().getResource(LINKED_VARIABLE_ICON));
        linkedBitVariableIcon = new ImageIcon(getClass().getResource(LINKED_BIT_VARIABLE_ICON));
        linkedPackedVariableIcon = new ImageIcon(getClass().getResource(LINKED_PACKED_VARIABLE_ICON));

        lastPackRow = -1;
        ignoreSelectionChange = false;
    }

    /**************************************************************************
     * Placeholder for method to respond to changes in selection of a table in
     * the table tree
     *************************************************************************/
    protected void updateTableSelection()
    {
    }

    /**************************************************************************
     * Set the flag that indicates if table node selection edits should be
     * ignored for undo/redo consideration
     * 
     * @param ignore
     *            true to not store table node selection edits
     *************************************************************************/
    protected void setIgnoreSelectionChange(boolean ignore)
    {
        ignoreSelectionChange = ignore;
    }

    /**************************************************************************
     * Get the flag that indicates if table node selection edits should be
     * ignored for undo/redo consideration
     * 
     * @return true if table node selection edits are to be ignored for
     *         undo/redo consideration
     *************************************************************************/
    protected boolean isIgnoreSelectionChange()
    {
        return ignoreSelectionChange;
    }

    /**************************************************************************
     * Remove HTML tag(s). Override to remove other text
     * 
     * @param text
     *            string from which to remove the extra text
     *
     * @return Input string minus any HTML tag(s)
     *************************************************************************/
    protected String removeExtraText(String text)
    {
        return CcddUtilities.removeHTMLTags(text);
    }

    /**************************************************************************
     * Expand or collapse all of the nodes in the tree
     * 
     * @param isExpanded
     *            true if all tree nodes should be expanded
     *************************************************************************/
    protected void setTreeExpansion(boolean isExpanded)
    {
        // Check if the tree should be fully expanded
        if (isExpanded)
        {
            // Expand the entire tree
            expandTreePath(getPathFromNode((TreeNode) getModel().getRoot()));
        }
        // The check box is deselected
        else
        {
            // Collapse the entire tree
            collapseTreePath(getPathFromNode((TreeNode) getModel().getRoot()));
        }
    }

    /**************************************************************************
     * Expand the specified tree path and all of its child paths. This is a
     * recursive method
     * 
     * @param path
     *            tree path to expand
     *************************************************************************/
    private void expandTreePath(TreePath path)
    {
        // Get the node for this path
        TreeNode node = (TreeNode) path.getLastPathComponent();

        // Check if the node has any child nodes
        if (node.getChildCount() >= 0)
        {
            // Step through each child node
            for (Enumeration<?> e = node.children(); e.hasMoreElements();)
            {
                // Get the child node's path and expand it
                TreeNode childNode = (TreeNode) e.nextElement();
                TreePath childPath = path.pathByAddingChild(childNode);
                expandTreePath(childPath);
            }
        }

        // Expand the current path
        expandPath(path);
    }

    /**************************************************************************
     * Collapse the specified tree path and all of its child paths. This is a
     * recursive method
     * 
     * @param path
     *            tree path to collapse
     *************************************************************************/
    private void collapseTreePath(TreePath path)
    {
        // Get the node for this path
        TreeNode node = (TreeNode) path.getLastPathComponent();

        // Check if the node has any child nodes
        if (node.getChildCount() >= 0)
        {
            // Step through each child node
            for (Enumeration<?> e = node.children(); e.hasMoreElements();)
            {
                // Get the child node's path and collapse it
                TreeNode childNode = (TreeNode) e.nextElement();
                TreePath childPath = path.pathByAddingChild(childNode);
                collapseTreePath(childPath);
            }
        }

        // Check if this path is not the root or if the root is visible. This
        // prevents collapsing the root's children when the root is invisible
        if (path.getParentPath() != null || isRootVisible())
        {
            // Collapse the current path
            collapsePath(path);
        }
    }

    /**************************************************************************
     * Get the tree path for the specified tree node
     * 
     * @param node
     *            tree node from which to create the tree path
     * 
     * @return Tree path corresponding to the specified tree node; null if the
     *         specified node is null
     *************************************************************************/
    protected static TreePath getPathFromNode(TreeNode node)
    {
        List<Object> nodes = new ArrayList<Object>();

        // Perform while the node has a parent
        while (node != null)
        {
            // Insert the node at the beginning of the list
            nodes.add(0, node);

            // Set the node reference to the node's parent
            node = node.getParent();
        }

        return nodes.isEmpty()
                              ? null
                              : new TreePath(nodes.toArray());
    }

    /**************************************************************************
     * Expand the selected node(s) if collapsed, or collapse the selected
     * node(s) if expanded
     *************************************************************************/
    protected void expandCollapseSelectedNodes()
    {
        // Check if a tree node is selected
        if (getSelectionPath() != null)
        {
            // Set the flag to true if the first selected node is expanded
            boolean isExpanded = isExpanded(getSelectionPath());

            // Step through each selected node
            for (TreePath path : getSelectionPaths())
            {
                // Check if the first selected node is expanded
                if (isExpanded)
                {
                    // Collapse the node
                    collapseTreePath(path);
                }
                // The first selected node is collapsed
                else
                {
                    // Expand the node
                    expandTreePath(path);
                }
            }
        }
    }

    /**************************************************************************
     * Store the current tree expansion state
     * 
     * @param tree
     *            reference to the tree for which to obtain the expansion state
     * 
     * @return String representing the current tree expansion state
     *************************************************************************/
    protected String getExpansionState()
    {
        String expState = "";

        // Step through each visible row in the tree
        for (int row = 0; row < getRowCount(); row++)
        {
            // Get the row's path
            TreePath path = getPathForRow(row);

            // Check if the row is expanded
            if (isExpanded(row))
            {
                // Store the expanded row's path
                expState += path + ",";
            }
        }

        return expState;
    }

    /**************************************************************************
     * Restore the tree expansion state
     * 
     * @param tree
     *            reference to the tree for which to restore the expansion
     *            state
     * 
     * @param expState
     *            string representing the desired tree expansion state
     *************************************************************************/
    protected void setExpansionState(String expState)
    {
        // Step through each visible row in the tree
        for (int row = 0; row < getRowCount(); row++)
        {
            // Get the row's path
            TreePath path = getPathForRow(row);

            // Check if the desired expansion state contains the path for the
            // row
            if (expState.contains(path.toString()))
            {
                // Expand the row
                expandRow(row);
            }
        }
    }

    /**************************************************************************
     * Adjust the tree expansion state to account for the tree filter selection
     * status
     * 
     * @param expState
     *            string representing the desired tree expansion state
     * 
     * @param isByGroup
     *            true if the tree is filtered by group/application
     * 
     * @param isByGroupChanged
     *            true if the group filter status changed
     * 
     * @param isByType
     *            true if the tree is filtered by table type
     * 
     * @param isByTypeChanged
     *            true if the table type filter status changed
     * 
     * @param isApp
     *            true if the groups are to be treated as possible
     *            applications; false if the application distinction doesn't
     *            apply
     * 
     * @param topNodePrefixes
     *            initial portion of the node path that provide the overall
     *            division of the tree
     * 
     * @param groupHandler
     *            reference to the group handler
     * 
     * @param tableTypeHandler
     *            reference to the table type handler
     * 
     * @return String representing the tree expansion state with adjustments
     *         made to account for the change in filter selection status
     *************************************************************************/
    protected String adjustExpansionState(String expState,
                                          boolean isByGroup,
                                          boolean isByGroupChanged,
                                          boolean isByType,
                                          boolean isByTypeChanged,
                                          boolean isApp,
                                          List<String> topNodePrefixes,
                                          CcddGroupHandler groupHandler,
                                          CcddTableTypeHandler tableTypeHandler)
    {
        // Break the expansion state into the separate visible nodes
        String[] paths = expState.split(Pattern.quote("],"));

        // Clear the expansion state
        expState = "";

        // Create the path termination regular expression
        String termPattern = "(\\],|,.*)";

        // Initialize the group name regular expression pattern assuming there
        // is no filtering by groups
        String groupPattern = "(())";

        // Check if the tree has changed to or is already being filtered by
        // groups
        if ((isByGroup && !isByGroupChanged) || (!isByGroup && isByGroupChanged))
        {
            // Initialize the group name regular expression pattern with group
            // filtering enabled
            groupPattern = "(, (";

            // Step through each group
            for (GroupInformation grpInfo : groupHandler.getGroupInformation())
            {
                // Add the group name to the pattern
                groupPattern += Pattern.quote(grpInfo.getName()) + "|";
            }

            // Finish the group name pattern
            groupPattern = CcddUtilities.removeTrailer(groupPattern, "|") + "))";
        }

        // Initialize the type name regular expression pattern assuming there
        // is no filtering by types
        String typePattern = "(())";

        // Check if the tree has changed to or is already being filtered by
        // types
        if ((isByType && !isByTypeChanged) || (!isByType && isByTypeChanged))
        {
            // Initialize the type name regular expression pattern with type
            // filtering enabled
            typePattern = "(, (";

            // Step through each table type
            for (String type : tableTypeHandler.getTypes())
            {
                // Add the type name to the pattern
                typePattern += Pattern.quote(type) + "|";
            }

            // Finish the type name pattern
            typePattern = CcddUtilities.removeTrailer(typePattern, "|") + "))";
        }

        // Step through each visible path in the tree
        for (String path : paths)
        {
            // Add the path terminator that was removed when the expansion
            // state was split
            path += "],";

            // Step through the prototype and instance nodes
            for (String prefix : topNodePrefixes)
            {
                // Set the flag to true if the path contains the group and/or a
                // type nodes
                boolean matchesEither = path.matches(Pattern.quote(prefix)
                                                     + groupPattern
                                                     + typePattern
                                                     + termPattern);

                // Set the flag to true if the path contains a group node but
                // no type node
                boolean matchesGroup = groupPattern.equals("(())")
                                                                  ? false
                                                                  : typePattern.equals("(())")
                                                                                              ? path.matches(Pattern.quote(prefix)
                                                                                                             + groupPattern
                                                                                                             + termPattern)
                                                                                                || path.matches(Pattern.quote(prefix)
                                                                                                                + termPattern)
                                                                                              : path.matches(Pattern.quote(prefix)
                                                                                                             + groupPattern
                                                                                                             + "[^"
                                                                                                             + typePattern
                                                                                                             + "]"
                                                                                                             + termPattern)
                                                                                                || path.matches(Pattern.quote(prefix)
                                                                                                                + typePattern
                                                                                                                + termPattern);

                // Set the flag to true if the path contains a type node but
                // no group node
                boolean matchesType = typePattern.equals("(())")
                                                                ? false
                                                                : groupPattern.equals("(())")
                                                                                             ? path.matches(Pattern.quote(prefix)
                                                                                                            + typePattern
                                                                                                            + termPattern)
                                                                                               || path.matches(Pattern.quote(prefix)
                                                                                                               + termPattern)
                                                                                             : path.matches(Pattern.quote(prefix)
                                                                                                            + "[^"
                                                                                                            + groupPattern
                                                                                                            + "]"
                                                                                                            + typePattern
                                                                                                            + termPattern)
                                                                                               || path.matches(Pattern.quote(prefix)
                                                                                                               + groupPattern
                                                                                                               + termPattern);
                // Check if the path contains a group or type node
                if (matchesEither)
                {
                    // Check if the group filter changed to enabled
                    if (isByGroup && isByGroupChanged)
                    {
                        // Check if the groups are to treated as possible
                        // applications
                        if (isApp)
                        {
                            // Update the node path with the group name
                            path = path.replaceAll(Pattern.quote(prefix)
                                                   + typePattern
                                                   + termPattern,
                                                   prefix
                                                       + "$1$3");
                        }
                        // All groups are to treated equally
                        else
                        {
                            String newPath = "";

                            // Step through each group
                            for (GroupInformation grpInfo : groupHandler.getGroupInformation())
                            {
                                // Check if the tree is filtered by type
                                if (isByType)
                                {
                                    // Update the node path with the group name
                                    // and append it to new path
                                    newPath += path.replaceAll(Pattern.quote(prefix)
                                                               + typePattern
                                                               + termPattern,
                                                               prefix
                                                                   + ", "
                                                                   + grpInfo.getName()
                                                                   + "$3");
                                }

                                // Update the node path with the group name and
                                // append it to new path
                                newPath += path.replaceAll(Pattern.quote(prefix)
                                                           + typePattern
                                                           + termPattern,
                                                           prefix
                                                               + ", "
                                                               + grpInfo.getName()
                                                               + "$1$3");
                            }

                            // Check if type filtering is enabled and that the
                            // path contains a type
                            if (isByType && matchesType)
                            {
                                // Blank the original path; only the new path
                                // items are applicable
                                path = "";
                            }

                            // Add the new group nodes to the path
                            path += newPath;
                        }
                    }
                    // Check if the group filter changed to disabled
                    else if (!isByGroup && isByGroupChanged)
                    {
                        // Remove the group name form the path
                        path = path.replaceAll((isApp ? groupPattern : "")
                                               + Pattern.quote(prefix)
                                               + (isApp ? "" : groupPattern)
                                               + typePattern
                                               + termPattern,
                                               prefix
                                                   + "$3$5");
                    }
                    // Check if the type filter changed to enabled
                    else if (isByType && isByTypeChanged)
                    {
                        String newPath = "";

                        // Step through each table type
                        for (String type : tableTypeHandler.getTypes())
                        {
                            // Modify the existing path to include the new
                            // type node
                            newPath += path.replaceAll(Pattern.quote(prefix)
                                                       + groupPattern
                                                       + termPattern,
                                                       prefix
                                                           + "$1, "
                                                           + type
                                                           + "$3");
                        }

                        // Add the new type nodes to the path
                        path += newPath;
                    }
                    // Check if the type filter changed to disabled
                    else if (!isByType && isByTypeChanged)
                    {
                        // Remove the type name form the path
                        path = path.replaceAll(Pattern.quote(prefix)
                                               + groupPattern
                                               + typePattern
                                               + termPattern,
                                               prefix
                                                   + "$1$5");
                    }

                    break;
                }
                // Check if group filtering is disabled and the path contains a
                // group
                else if (!isByGroup && matchesGroup)
                {
                    // Blank the path
                    path = "";
                }
                // Check if type filtering is disabled and the path contains a
                // type
                else if (!isByType && matchesType)
                {
                    // Blank the path
                    path = "";
                }
            }

            // Check that the path isn't already in the updated expansion state
            if (!expState.contains(path))
            {
                // Add the path to the expansion state
                expState += path;
            }
        }

        return expState;
    }

    /**************************************************************************
     * Convert the elements in tree path array to a single, comma-separated
     * string
     * 
     * @param path
     *            tree path array
     * 
     * @param startIndex
     *            index of the first array member to include in the output
     *            string
     * 
     * @return The tree path array as a single string with individual elements
     *         separated by commas
     *************************************************************************/
    protected String createNameFromPath(Object[] path, int startIndex)
    {
        String name = "";

        // Step through each element in the path, beginning at the specified
        // starting index
        for (int index = startIndex; index < path.length; index++)
        {
            // Add the path element the name
            name += path[index].toString().trim() + ",";
        }

        // Remove the trailing comma
        name = CcddUtilities.removeTrailer(name, ",");

        return name;
    }

    /**************************************************************************
     * Deselect any nodes that are disabled
     ************************************************************************/
    protected void clearDisabledNodes()
    {
        // Get the selected tables
        TreePath[] selectedPaths = getSelectionPaths();

        // Check that a table is selected
        if (selectedPaths != null)
        {
            // Step through each selected table
            for (TreePath path : selectedPaths)
            {
                // Check id the node is disabled
                if (path.toString().contains(DISABLED_TEXT_COLOR))
                {
                    // Clear the selected node
                    removeSelectionPath(path);
                }
            }
        }
    }

    /**************************************************************************
     * Recursively add the children of the specified node to the path list. If
     * these are variable paths and the node represents a bit-wise or string
     * variable then add the bit-packed/string members as well
     * 
     * @param node
     *            current child node to check
     * 
     * @param selectedPaths
     *            list containing the selected paths
     * 
     * @param excludedPaths
     *            list of paths to be excluded from the tree
     * 
     * @param isVariable
     *            true if the tree contains variables
     *************************************************************************/
    protected void addChildNodes(ToolTipTreeNode node,
                                 List<Object[]> selectedPaths,
                                 List<String> excludedPaths,
                                 boolean isVariable)
    {
        // Check if this node has no children
        if (node.getChildCount() == 0)
        {
            boolean isAdded = false;

            // Get the node name to shorten subsequent calls
            String nodeName = node.getUserObject().toString();

            // Check that no exclusion list is in effect or, if one is, that
            // the node is not marked as excluded (i.e., starts with an HTML
            // tag)
            if (excludedPaths == null || !nodeName.startsWith("<html>"))
            {
                // If this node is a bit-wise variable then all other variables
                // that are packed with it must be selected as well. Likewise,
                // if this is a string then all array members that comprise the
                // string must be selected. Check if this node has any siblings
                if (node.getSiblingCount() > 1)
                {
                    NodeIndex nodeIndex = null;

                    // Check if this is a variable tree
                    if (isVariable)
                    {
                        // Check if it represents a bit-wise variable
                        if (nodeName.contains(":"))
                        {
                            // Get the node indices that encompass the packed
                            // variables (if applicable)
                            nodeIndex = getBitPackedVariables(node);
                        }
                        // Not a bit-wise variable
                        else
                        {
                            // Extract the data type name form the node name
                            String dataType = nodeName.substring(0, nodeName.indexOf("."));

                            // Check if this is a string
                            if (dataTypeHandler.isString(dataType))
                            {
                                // Get the node indices that encompass the
                                // string array members
                                nodeIndex = getStringVariableMembers(node);
                            }
                        }
                    }

                    // Check if packed variables or string members are present
                    if (nodeIndex != null)
                    {
                        // Calculate the tree node index for the first
                        // packed/string variable
                        int treeIndex = node.getParent().getIndex(node)
                                        - (nodeIndex.getTableIndex()
                                        - nodeIndex.getFirstIndex());

                        // Step through each packed/string variable
                        for (int index = nodeIndex.getFirstIndex(); index <= nodeIndex.getLastIndex(); index++, treeIndex++)
                        {
                            boolean isInList = false;

                            // Get the path for the variable
                            Object[] path = ((ToolTipTreeNode) node.getParent().getChildAt(treeIndex)).getPath();

                            // Step through the paths already added
                            for (Object[] selPath : selectedPaths)
                            {
                                // Check if the path is already in the list
                                if (Arrays.equals(path, selPath))
                                {
                                    // Set the flag to indicate the path is
                                    // already in the list and stop searching
                                    isInList = true;
                                    break;
                                }
                            }

                            // Check if the variable wasn't already in the list
                            if (!isInList)
                            {
                                // Add the variable to the selected variables
                                // list
                                selectedPaths.add(path);
                            }
                        }

                        // Set the flag indicating the variable is added
                        isAdded = true;
                    }
                }

                // Check if the variable isn't already added above
                if (!isAdded)
                {
                    // Add the variable path to the list
                    selectedPaths.add(node.getPath());
                }
            }
        }
        // The node has child nodes
        else
        {
            // Step through each child node
            for (int index = 0; index < node.getChildCount(); index++)
            {
                // Check if the child node has children
                addChildNodes((ToolTipTreeNode) node.getChildAt(index),
                              selectedPaths,
                              excludedPaths,
                              isVariable);
            }
        }
    }

    /**************************************************************************
     * Get the first applicable node index. Override this method to skip node
     * levels that don't apply (e.g., root or filter nodes)
     * 
     * @return First applicable node index
     *************************************************************************/
    protected int getHeaderNodeLevel()
    {
        return 0;
    }

    /**************************************************************************
     * Convert the path array into a single string showing the full variable
     * path in the format
     * rootTable[,dataType1.variable1[,dataType2.variable2[,...]]]. This
     * excludes the database, prototype/instance, group (if filtered by group),
     * and type (if filtered by type) nodes
     * 
     * @param path
     *            array describing the variable's tree path
     * 
     * @return Root table name, followed by the variable names with the data
     *         types, separated by commas, from the specified tree path
     *************************************************************************/
    protected String getFullVariablePath(Object[] path)
    {
        return getFullVariablePath(path, 0);
    }

    /**************************************************************************
     * Convert the path array into a single string showing the full variable
     * path in the format
     * rootTable[,dataType1.variable1[,dataType2.variable2[,...]]]. This
     * excludes the database, prototype/instance, group (if filtered by group),
     * and type (if filtered by type) nodes
     * 
     * @param path
     *            array describing the variable's tree path
     * 
     * @param levelAdjust
     *            number of nodes (+/-) by which to adjust the starting the
     *            node level
     * 
     * @return Root table name, followed by the variable names with the data
     *         types, separated by commas, from the specified tree path
     *************************************************************************/
    protected String getFullVariablePath(Object[] path, int levelAdjust)
    {
        String variablePath = "";

        // Step through the nodes in the path. Calculate the index into the
        // tree path array so as to skip the database and prototype/instance
        // nodes, and the group and/or type nodes, if filtering is active, and
        // the parent table name
        for (int index = getHeaderNodeLevel() + levelAdjust; index < path.length; index++)
        {
            // Get the node name
            String variable = path[index].toString();

            // Check if the node represents a variable name
            if (!variable.contains(";"))
            {
                // Store the variable name (including the bit length, if
                // present) in the path array
                variablePath += variable + ",";
            }
        }

        // Remove the trailing comma
        variablePath = CcddUtilities.removeTrailer(variablePath, ",");

        return variablePath;
    }

    /**************************************************************************
     * Determine the node indices in the table tree that encompass a group of
     * bit-packed variables
     * 
     * @param node
     *            selected node for a (potentially) bit-packed variable
     * 
     * @return NodeIndex object containing the node indices bounding the
     *         bit-packed variables
     *************************************************************************/
    protected NodeIndex getBitPackedVariables(ToolTipTreeNode node)
    {
        // Get the variable name from the node
        String varName = removeExtraText(node.getUserObject().toString());

        // Get the corresponding node in the variable tree
        ToolTipTreeNode tblParent = (ToolTipTreeNode) node.getParent();

        // Get this variable tree node's index in the variable tree relative to
        // its parent node
        int tblIndex = tblParent.getIndex(node);
        int firstIndex = tblIndex;
        int lastIndex = tblIndex;

        // Check if the target variable has a bit length
        if (varName.contains(":"))
        {
            // Get the data type for this variable and calculate the number of
            // bits it occupies
            String dataType = varName.substring(0, varName.indexOf("."));
            int dataTypeBitSize = dataTypeHandler.getSizeInBits(dataType);

            // Set the current index in preparation for locating other
            // variables packed with this one. Note that this can result is
            // stepping backwards into another pack; this is accounted for
            // further down
            int curIndex = tblIndex - 1;

            // Step backwards through the child nodes as long as the bit-wise
            // variables of the same data type are found
            while (curIndex >= 0)
            {
                // Get the variable name from the node
                varName = removeExtraText(((ToolTipTreeNode) tblParent.getChildAt(curIndex)).getUserObject().toString());

                // Check if this variable doesn't have a bit length or isn't
                // the same data type as the target
                if (!varName.contains(":") || !varName.startsWith(dataType + "."))
                {
                    // Stop searching
                    break;
                }

                curIndex--;
            }

            // Adjust the index and save this as the starting index, and store
            // its associated tree node index
            curIndex++;
            firstIndex = curIndex;

            int bitCount = 0;
            boolean isTargetInPack = false;

            // Step forward, packing the bits, in order to determine the
            // variables in the target variable's pack
            while (curIndex < node.getSiblingCount())
            {
                // Get the variable name from the node
                varName = removeExtraText(((ToolTipTreeNode) tblParent.getChildAt(curIndex)).getUserObject().toString());

                // Check if this variable doesn't have a bit length or isn't
                // the same data type as the target
                if (!varName.contains(":") || !varName.startsWith(dataType + "."))
                {
                    // Check if the target variable is a member of this packed
                    // group
                    if (curIndex == tblIndex)
                    {
                        // Set the flag indicating this pack includes the
                        // target variable
                        isTargetInPack = true;
                    }

                    // Stop searching
                    break;
                }

                // Add the number of bits occupied by this variable to the
                // running count
                int bitLength = Integer.valueOf(macroHandler.getMacroExpansion(varName.substring(varName.indexOf(":") + 1)));
                bitCount += bitLength;

                // Check if the bit count rolled over the maximum allowed
                if (bitCount > dataTypeBitSize)
                {
                    // Check if the target variable is included in the range of
                    // packed variables
                    if (isTargetInPack)
                    {
                        // All variables packed with the target variable have
                        // been detected, so stop searching
                        break;
                    }

                    // Reset the bit count to the current row's value and store
                    // the row index for the first variable in the pack. This
                    // accounts for one or more bit-wise variables occurring
                    // immediately prior to the pack containing the target
                    bitCount = bitLength;
                    firstIndex = curIndex;
                }

                // Check if the target variable is a member of this packed
                // group
                if (curIndex == tblIndex)
                {
                    // Set the flag indicating this pack includes the target
                    // variable
                    isTargetInPack = true;
                }

                curIndex++;
            }

            // Store the last index in the pack. If the variable isn't
            // bit-packed (i.e., has no bit length or has no other pack
            // members) then the last index is the same as the first index
            lastIndex = curIndex - (isTargetInPack ? 1 : 0);
        }

        return new NodeIndex(firstIndex, lastIndex, tblIndex);
    }

    /**************************************************************************
     * Determine the node indices in the table tree that encompass the array
     * members that represent the individual bytes of a string variable
     * 
     * @param node
     *            selected node for a (potentially) bit-packed variable
     * 
     * @return NodeIndex object containing the node indices bounding the string
     *         variable
     *************************************************************************/
    protected NodeIndex getStringVariableMembers(ToolTipTreeNode node)
    {
        // Get the target variable's data type and name from the node without
        // the string size array index
        String variableName = ArrayVariable.removeStringSize(removeExtraText(node.getUserObject().toString()));

        // Get the corresponding node in the variable tree
        ToolTipTreeNode tblParent = (ToolTipTreeNode) node.getParent();

        // Get this variable tree node's index in the variable tree relative to
        // its parent node
        int tblIndex = tblParent.getIndex(node);

        // Set the current index in preparation for locating other variables
        // packed with this one
        int curIndex = tblIndex - 1;

        // Step backwards through the child nodes, matching the data type and
        // variable name (and array index or indices other than the string size
        // array index), in order to determine the array members that make up
        // the target string
        while (curIndex >= 0)
        {
            // Check if the variable at this node doesn't match the target
            // variable
            if (!variableName.equals(ArrayVariable.removeStringSize(removeExtraText(((ToolTipTreeNode) tblParent.getChildAt(curIndex)).getUserObject().toString()))))
            {
                // Stop searching
                break;
            }

            curIndex--;
        }

        // Adjust the index and save this as the starting index, and store its
        // associated tree node index
        curIndex++;
        int firstIndex = curIndex;

        // Step forward, matching the data type and variable name (and array
        // index or indices other than the string size array index), in order
        // to determine the array members that make up the target string
        while (curIndex < node.getSiblingCount())
        {
            // Check if this variable at this node doesn't match the target
            // variable
            if (!variableName.equals(ArrayVariable.removeStringSize(removeExtraText(((ToolTipTreeNode) tblParent.getChildAt(curIndex)).getUserObject().toString()))))
            {
                // Stop searching
                break;
            }

            curIndex++;
        }

        // Store the last index in the pack. If the variable isn't bit-packed
        // (i.e., has no bit length or has no other pack members) then the last
        // index is the same as the first index
        int lastIndex = curIndex - 1;

        return new NodeIndex(firstIndex, lastIndex, tblIndex);
    }

    /**************************************************************************
     * Set the tree icon for nodes representing a variable. The icon indicates
     * if the variable is or isn't bit-wise, is or isn't linked, and is or
     * isn't bit-packed
     * 
     * @param renderer
     *            reference to the tree's cell renderer
     * 
     * @param node
     *            node for which the icon is to be set
     * 
     * @param currentRow
     *            row index of the node in the tree
     * 
     * @param isLinked
     *            true if the variable is a member of a link
     *************************************************************************/
    protected void setVariableNodeIcon(DefaultTreeCellRenderer renderer,
                                       ToolTipTreeNode node,
                                       int currentRow,
                                       boolean isLinked)
    {
        // Assume this is a normal variable (not bit-wise, linked, or packed)
        Icon icon = variableIcon;

        // Check if this is a bit-wise variable (node name ends with ':#')
        if (node.toString().matches("^.+:\\d+$"))
        {
            // Check if this tree row falls within a group of bit-packed
            // variables determined from an earlier row
            if (currentRow <= lastPackRow)
            {
                // Check if the variable is a link member
                if (isLinked)
                {
                    // Set the icon to indicate this is a linked & bit-packed
                    // variable
                    icon = linkedPackedVariableIcon;
                }
                // The variable isn't a link member
                else
                {
                    // Set the icon to indicate this is a bit-packed variable
                    icon = packedVariableIcon;
                }
            }
            // The row is not within a known bit-packed group
            else
            {
                // Determine if this row's variable is bit-packed with other
                // variables
                NodeIndex nodeIndex = getBitPackedVariables(node);

                // Check if the variable is bit-packed with other variables
                if (nodeIndex.getFirstIndex() != nodeIndex.getLastIndex())
                {
                    // Check if the variable is a link member
                    if (isLinked)
                    {
                        // Set the icon to indicate this is a linked &
                        // bit-packed variable
                        icon = linkedPackedVariableIcon;
                    }
                    // The variable isn't a link member
                    else
                    {
                        // Set the icon to indicate this is a bit-packed
                        // variable
                        icon = packedVariableIcon;
                    }

                    // Store the row containing the last member of the pack
                    lastPackRow = nodeIndex.getLastIndex();
                }
                // The variable is not bit-packed
                else
                {
                    // Check if the variable is a link member
                    if (isLinked)
                    {
                        // Set the icon to indicate this is a linked & bit-wise
                        // variable
                        icon = linkedBitVariableIcon;
                    }
                    // The variable isn't a link member
                    else
                    {
                        // Set the icon to indicate a bit-wise variable
                        icon = bitVariableIcon;
                    }

                    // Reset the last pack member row
                    lastPackRow = -1;
                }
            }
        }
        // Not a bit-wise variable
        else
        {
            // Check if the variable is a link member
            if (isLinked)
            {
                // Set the icon to indicate this is a linked variable
                icon = linkedVariableIcon;
            }

            // Reset the last pack member row
            lastPackRow = -1;
        }

        // Display the icon for the variable
        renderer.setIcon(icon);
    }
}
