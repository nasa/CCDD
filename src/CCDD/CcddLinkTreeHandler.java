/**
 * CFS Command and Data Dictionary variable link tree handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_ICON;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.OK_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
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
import javax.swing.tree.TreePath;

import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.LinkInformation;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddUndoHandler.UndoableArrayList;
import CCDD.CcddUndoHandler.UndoableTreeModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary link tree handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddLinkTreeHandler extends CcddInformationTreeHandler
{
    // Class references
    private CcddLinkHandler linkHandler;
    private CcddUndoHandler undoHandler;

    // Tree icons depicting links
    private final Icon validLinkIcon;
    private final Icon invalidLinkIcon;

    // Flag indicating if the link tree nodes are expanded or not
    private boolean isExpanded;

    // List to contain the link definitions (links and variable paths) retrieved from the database
    private List<String[]> linkDefinitions;

    // List containing the information for each defined link (name, rate, size, and description).
    // Link member variables are stored in the linkDefinitions
    private UndoableArrayList<LinkInformation> linkInformation;

    // Flag to indicate if the link tree is being built
    private boolean isBuilding;

    // Currently selected link rate
    private String selectedRate;

    /**********************************************************************************************
     * Link tree handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param rateName
     *            data stream rate column name associated with the tree; used to filter the links
     *            added to the link tree
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddLinkTreeHandler(CcddMain ccddMain,
                        CcddUndoHandler undoHandler,
                        String rateName,
                        Component parent)
    {
        super(ccddMain, undoHandler, InternalTable.LINKS, rateName, false, null, parent);

        // Create the tree icons depicting links
        validLinkIcon = new ImageIcon(getClass().getResource(OK_ICON));
        invalidLinkIcon = new ImageIcon(getClass().getResource(CANCEL_ICON));
    }

    /**********************************************************************************************
     * Get the reference to the link handler
     *
     * @return Reference to the link handler
     *********************************************************************************************/
    protected CcddLinkHandler getLinkHandler()
    {
        return linkHandler;
    }

    /**********************************************************************************************
     * Set the currently selected sample rate and force the link tree to redraw
     *
     * @param rate
     *            sample rate in samples per second
     *********************************************************************************************/
    protected void setSelectedRate(String rate)
    {
        selectedRate = rate;
        repaint();
    }

    /**********************************************************************************************
     * Perform initialization steps prior to building the link tree
     *
     * @param ccddMain
     *            main class
     *
     * @param undoHandler
     *            reference to the undo handler
     *
     * @param linkDefinitions
     *            list containing the link definitions
     *********************************************************************************************/
    @Override
    protected void initialize(CcddMain ccddMain,
                              CcddUndoHandler undoHandler,
                              List<String[]> linkDefinitions)
    {
        // Check if no undo handler is specified
        if (undoHandler == null)
        {
            // Create a 'dummy' undo handler and set the flag to not allow undo operations
            undoHandler = new CcddUndoHandler(new CcddUndoManager());
            undoHandler.setAllowUndo(false);
        }

        this.undoHandler = undoHandler;
        this.linkDefinitions = linkDefinitions;

        // Get a reference to the link handler class
        linkHandler = new CcddLinkHandler(ccddMain, linkDefinitions);

        // Set the tree to be collapsed initially
        isExpanded = false;

        // Set the selected rate to a blank
        selectedRate = "";
    }

    /**********************************************************************************************
     * Update the link definitions to match the current link tree information
     *********************************************************************************************/
    protected void updateLinkDefinitions()
    {
        // Recreate the link definitions from the current link tree
        linkDefinitions = createDefinitionsFromTree();

        // Set the updated definitions in the link handler so that the link size can be calculated
        linkHandler.setLinkDefinitions(linkDefinitions);
    }

    /**********************************************************************************************
     * Create a list of variables, including their paths, that are referenced by the specified
     * link, or of any link if no link name is provided
     *
     * @param name
     *            link name; null to include all links
     *
     * @return List of variables, including their paths, that are referenced by the specified link,
     *         of any link if no link name is provided
     *********************************************************************************************/
    protected List<String> getLinkVariables(String name)
    {
        // Create a list to hold the link members
        List<String> linkVariables = new ArrayList<String>();

        // Step through each link definition
        for (String[] linkDefn : linkDefinitions)
        {
            // Check if this is a member and not the rate/description
            if (!linkDefn[LinksColumn.MEMBER.ordinal()].matches("\\d.*")
                && (name == null
                    || linkDefn[LinksColumn.LINK_NAME.ordinal()].equals(name)))
            {
                // Add the variable to the list
                linkVariables.add(linkDefn[LinksColumn.MEMBER.ordinal()]);
            }
        }

        return linkVariables;
    }

    /**********************************************************************************************
     * Get the reference to a specified link's information
     *
     * @param name
     *            link name
     *
     * @return Reference to the link's information; null if the link doesn't exist
     *********************************************************************************************/
    protected LinkInformation getLinkInformation(String name)
    {
        LinkInformation linkInfo = null;

        // Remove HTML tag(s) and rate/size information
        name = removeExtraText(name);

        // Step through each link's information
        for (LinkInformation info : linkInformation)
        {
            // Check if the link name matches the target name
            if (info.getName().equals(name))
            {
                // Store the link information reference and stop searching
                linkInfo = info;
                break;
            }
        }

        return linkInfo;
    }

    /**********************************************************************************************
     * Add a new link to the link information class
     *
     * @param rateName
     *            rate column name
     *
     * @param linkName
     *            link name
     *
     * @param sampleRate
     *            link rate in samples per second
     *
     * @param description
     *            link description
     *********************************************************************************************/
    protected void addLinkInformation(String rateName,
                                      String linkName,
                                      String sampleRate,
                                      String description)
    {
        // Add the new link information
        linkInformation.add(new LinkInformation(rateName, linkName, sampleRate, description));

        // Update the link definitions to account for the added link
        updateLinkDefinitions();
    }

    /**********************************************************************************************
     * Remove the selected link(s) from the link information class and links tree
     *********************************************************************************************/
    protected void removeSelectedLinks()
    {
        List<String> links = new ArrayList<String>();

        // Store the paths of the links selected for deletion. The paths are 'lost' when the nodes
        // are removed in the next step
        TreePath[] paths = getSelectionPaths();

        // Remove the selected links(s) from the link tree. This is performed before removal of the
        // link information so that an undo operation restores the link information prior to
        // restoration of the tree node(s); this way if only a single link is restored via an undo
        // then the link's description is displayed in the link manager
        removeSelectedTopLevelNodes();

        // Step through each selected path
        for (TreePath path : paths)
        {
            // Get the link node for this path. Extract the link name from the node (minus the HTML
            // tags and rate/size information
            String name = removeExtraText(path.getPathComponent(1).toString());

            // Check if this link has already been removed
            if (!links.contains(name))
            {
                // Add the link to the list of those removed
                links.add(name);

                // Step through each link's information
                for (int index = 0; index < linkInformation.size(); index++)
                {
                    // Check if the name matches the target name
                    if (linkInformation.get(index).getName().equals(name))
                    {
                        // Remove the link's information and stop searching
                        linkInformation.remove(index);
                        break;
                    }
                }
            }
        }

        // Update the link definitions to account for the deleted link
        updateLinkDefinitions();
    }

    /**********************************************************************************************
     * Build the link tree from the database
     *
     * @param filterByApp
     *            true if the tree is filtered by application. This is not applicable to the link
     *            tree, which can only contain structure references
     *
     * @param filterValue
     *            string value that may be used to modify the tree building method; null or blank
     *            if not filtering
     *
     * @param filterFlag
     *            flag used to filter the tree content. Not used for the link tree
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    @Override
    protected void buildTree(boolean filterByApp,
                             String filterValue,
                             boolean filterFlag,
                             Component parent)
    {
        super.buildTree(false, filterValue, filterFlag, parent);

        // Get the tree's root node
        ToolTipTreeNode root = getRootNode();

        // Create the storage for the link information
        linkInformation = undoHandler.new UndoableArrayList<LinkInformation>();

        // Register the tool tip manager for the link tree (otherwise the tool tips aren't
        // displayed)
        ToolTipManager.sharedInstance().registerComponent(this);

        // Set the flag to indicate that the link tree is being built. This flag is used to inhibit
        // actions involving tree selection value changes during the build process
        isBuilding = true;

        // Step through each link
        for (String[] linkDefn : linkDefinitions)
        {
            // Check if the link definition matches the target data stream rate column name
            if (linkDefn[LinksColumn.RATE_NAME.ordinal()].equals(filterValue))
            {
                // Extract the rate name, link name, and rate/description or member
                String linkRate = linkDefn[LinksColumn.RATE_NAME.ordinal()];
                String linkName = linkDefn[LinksColumn.LINK_NAME.ordinal()];
                String linkMember = linkDefn[LinksColumn.MEMBER.ordinal()];

                // Check if this is a link description entry. These are indicated if the first
                // character is a digit, which is the link rate
                if (linkMember.matches(DefaultInputType.RATE.getInputMatch() + ",.*"))
                {
                    // Split the entry into the rate and description
                    String[] rateAndDesc = linkMember.split(",", 2);

                    // Store the link information
                    linkInformation.add(new LinkInformation(linkRate,
                                                            linkName,
                                                            rateAndDesc[0],
                                                            rateAndDesc[1]));

                    // Create a node for the link and add it to the link tree
                    addInformationNode(linkName, rateAndDesc[1]);
                }
                // This is a variable path
                else
                {
                    // Step through each current link node
                    for (int index = 0; index < root.getChildCount(); index++)
                    {
                        // Check if the link name matches the node name
                        if (linkName.equals(root.getChildAt(index).toString()))
                        {
                            // Add the variable to the node and stop searching
                            addNodeToInfoNode((ToolTipTreeNode) root.getChildAt(index),
                                              linkMember.split(","),
                                              0);
                            break;
                        }
                    }
                }
            }
        }

        // Expand or collapse the tree based on the expansion flag
        setTreeExpansion(isExpanded);

        // Clear the flag that indicates the link tree is being built
        isBuilding = false;
    }

    /**********************************************************************************************
     * Add a copy of the specified link information object to the link information list
     *
     * @param linkToCopy
     *            link information object to copy
     *
     * @param nameOfCopy
     *            name of the copy of the link
     *********************************************************************************************/
    @Override
    protected void addInformation(Object linkToCopy, String nameOfCopy)
    {
        // Store the link information for the copy
        linkInformation.add(new LinkInformation(((LinkInformation) linkToCopy).getSampleRate(),
                                                nameOfCopy,
                                                ((LinkInformation) linkToCopy).getDescription()));
    }

    /**********************************************************************************************
     * Initialize the link definition list with the link names, rates, and descriptions
     *
     * @return List containing the links with their names, rates, and descriptions
     *********************************************************************************************/
    @Override
    protected List<String[]> createDefinitionsFromInformation()
    {
        // Initialize the link tree information list
        List<String[]> definitions = new ArrayList<String[]>(linkInformation.size());

        // Step through each link's information
        for (LinkInformation linkInfo : linkInformation)
        {
            // Add the link's name, rate, and description to the list
            definitions.add(new String[] {linkInfo.getRateName(),
                                          linkInfo.getName(),
                                          linkInfo.getSampleRate()
                                                              + ","
                                                              + linkInfo.getDescription()});
        }

        return definitions;
    }

    /**********************************************************************************************
     * Add the specified leaf definition to the tree definition list. Override this method to
     * insert the data stream rate column name into the leaf definition
     *
     * @param treeDefns
     *            list to which to add the leaf definition
     *
     * @param leafDefn
     *            leaf definition to add to the list
     *
     * @param filterValue
     *            data stream rate name
     *********************************************************************************************/
    @Override
    protected void addLeafDefinition(List<String[]> treeDefns,
                                     String[] leafDefn,
                                     String filterValue)
    {
        // Store the leaf node definition in the tree information list, with the data stream rate
        // name prepended to the link definition
        treeDefns.add(new String[] {filterValue, leafDefn[0], leafDefn[1]});
    }

    /**********************************************************************************************
     * Override so that HTML tag(s) and text within parentheses (inclusive) preceded by a space
     * (the rate and size) is removed
     *
     * @param text
     *            string from which to remove the extra text
     *
     * @return Input string minus any HTML tag(s) and text within parentheses (inclusive) preceded
     *         by a space
     *********************************************************************************************/
    @Override
    protected String removeExtraText(String text)
    {
        return CcddUtilities.removeHTMLTags(text).replaceAll(" \\([^)]*\\)", "");
    }

    /**********************************************************************************************
     * Append the sample rate and size in bytes to the nodes representing a link name. Set the node
     * text color based on the currently selected sample rate and the rate of the link to which the
     * node belongs: black for a match and gray for a mismatch
     *
     * @param startNode
     *            starting node for which to adjust the text and color
     *********************************************************************************************/
    protected void adjustNodeText(ToolTipTreeNode startNode)
    {
        // Step through the elements and children of this node
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
                // Get the link name from the node path
                String linkName = removeExtraText(node.getPath()[1].toString());

                // Get the reference to the link's information. The link name is the second node in
                // the path for this node
                LinkInformation linkInfo = getLinkInformation(linkName);

                // Check that the node references a link
                if (linkInfo != null)
                {
                    // Get the node name
                    String nodeName = removeExtraText(node.getUserObject().toString());

                    // Check if this node represents a link name
                    if (level == 1)
                    {
                        // Assign the link name formatted as HTML
                        nodeName = "<html>" + linkName + " <i>(";

                        // Get the link size in bytes
                        int sizeInBytes = linkHandler.getLinkSizeInBytes(linkInfo.getRateName(),
                                                                         linkName);

                        // Check if the size is non-zero; i.e., variables are assigned to this link
                        if (sizeInBytes != 0)
                        {
                            // Append the link size to the link name
                            nodeName += linkInfo.getSampleRate()
                                        + " Hz, "
                                        + sizeInBytes
                                        + (sizeInBytes == 1
                                                            ? " byte)"
                                                            : " bytes)");
                        }
                        // No variables are assigned to this link
                        else
                        {
                            // Indicate that the link is empty
                            nodeName += "empty)";
                        }
                    }

                    // Check if the selected sample rate doesn't match the link's rate
                    if (!linkInfo.getSampleRate().equals("0")
                        && !selectedRate.equals(linkInfo.getSampleRate()))
                    {
                        // Gray out the node text
                        nodeName = DISABLED_TEXT_COLOR + nodeName;
                    }

                    // Update the node name. Indicate that the node changed so that the tree
                    // redraws the name
                    node.setUserObject(nodeName);
                    ((UndoableTreeModel) getModel()).nodeChanged(node);
                }
            }
        }
    }

    /**********************************************************************************************
     * Remove the currently selected node(s) from the tree. If the parent node of a removed node no
     * longer has any descendants after the removal then remove the parent node as well. Continue
     * this process of removing nodes without descendants all the way up the tree, but not
     * including the top level node
     *********************************************************************************************/
    protected void removeSelectedNodes()
    {
        List<Object[]> selectedPaths = new ArrayList<Object[]>();

        // Check if at least one node is selected
        if (getSelectionCount() != 0)
        {
            // Step through each selected node path
            for (TreePath selPath : getSelectionPaths())
            {
                // Add the selected node's descendant nodes to the selected path list
                addChildNodes((ToolTipTreeNode) selPath.getLastPathComponent(),
                              selectedPaths,
                              new ArrayList<String>(),
                              true);
            }

            // Step through the selected paths (which now includes all descendants)
            for (Object[] path : selectedPaths)
            {
                // Get the node referenced by the path
                ToolTipTreeNode node = (ToolTipTreeNode) new TreePath(path).getLastPathComponent();

                // In order to remove all of a child's path that isn't shared with another child,
                // step back through the child's path to find the ancestor node with only a single
                // child node
                while (node.getParent().getChildCount() == 1
                       && node.getLevel() > 2 + getHeaderNodeLevel())
                {
                    // Get the parent node for the child(ren) to be removed
                    node = (ToolTipTreeNode) node.getParent();
                }

                // Remove the node from the information tree
                ((UndoableTreeModel) getModel()).removeNodeFromParent(node);
            }
        }
    }

    /**********************************************************************************************
     * Create a subtree with only the links that contain variables with sample rates matching the
     * selected rate
     *
     * @param rootNodeName
     *            name of the root node for the matching links
     *
     * @param rootNodeDescription
     *            tool tip text for the root node
     *
     * @return Node with only the links that contain variables with sample rates matching the
     *         selected rate
     *********************************************************************************************/
    protected ToolTipTreeNode getLinksMatchingRate(String rootNodeName,
                                                   String rootNodeDescription)
    {
        // Create a node to contain the matching links
        ToolTipTreeNode validLinks = new ToolTipTreeNode(rootNodeName, rootNodeDescription);

        // Copy the current links tree to the new node
        copySubTree(getRootNode(), validLinks);

        // Step through each child node. This must be done in reverse order since node may be
        // removed during the processing
        for (int index = validLinks.getChildCount() - 1; index >= 0; index--)
        {
            // Get the link information for this link
            LinkInformation linkInfo = getLinkInformation(validLinks.getChildAt(index).toString());

            // Check if the link exists and if the link's rate doesn't match the selected rate
            if (linkInfo != null
                && !linkInfo.getSampleRate().equals(selectedRate))
            {
                // Remove this link's node from the valid links tree
                validLinks.remove(index);
            }
        }

        return validLinks;
    }

    /**********************************************************************************************
     * Create a link tree panel. The table tree is placed in a scroll pane. A check box is added
     * that allows tree expansion/collapse
     *
     * @param label
     *            link tree title
     *
     * @param selectionMode
     *            tree item selection mode (single versus multiple)
     *
     * @return JPanel containing the link tree components
     *********************************************************************************************/
    protected JPanel createTreePanel(String label, int selectionMode)
    {
        // Set the renderer for the tree so that the link information can be displayed, and so that
        // custom icons can be used for the various node types
        setCellRenderer(new VariableTreeCellRenderer()
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

                // Check that the tree has any levels. When the tree is first created this method
                // is called when no nodes exist
                if (level != 0)
                {
                    // Get the reference to the link's information. The link name is the second
                    // node in the path for this node
                    LinkInformation linkInfo = getLinkInformation(((ToolTipTreeNode) value).getPath()[1].toString());

                    // Check that the link information exists
                    if (linkInfo != null)
                    {
                        // Check if this node represents a link name
                        if (level == 1)
                        {
                            // Get the rate for this link
                            String linkRate = linkInfo.getSampleRate();

                            // Check if the link rate matches the currently selected rate or if it
                            // has no assigned rate
                            if (linkRate.equals("0") || selectedRate.equals(linkRate))
                            {
                                setIcon(validLinkIcon);
                            }
                            // The link rate doesn't match the selected rate
                            else
                            {
                                setIcon(invalidLinkIcon);
                            }
                        }
                        // Check if this node represents a variable
                        else if (leaf)
                        {
                            // Set the icon for the variable node
                            setVariableNodeIcon(this, (ToolTipTreeNode) value, row, true);
                        }
                    }
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
        JPanel treePnl = new JPanel(new GridBagLayout());
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

        // Add the tree to the panel
        gbc.weighty = 1.0;
        gbc.gridy++;
        treePnl.add(treeScroll, gbc);

        // Set the table tree font and number of rows to display
        setFont(ModifiableFontInfo.TREE_NODE.getFont());
        setVisibleRowCount(10);

        // Add a listener for changes to the link tree
        addTreeSelectionListener(new TreeSelectionListener()
        {
            /**************************************************************************************
             * Handle a change to the link tree selection
             *************************************************************************************/
            @Override
            public void valueChanged(TreeSelectionEvent lse)
            {
                // Check that a link tree (re)build isn't in progress. Building the tree triggers
                // tree selection value changes that should not be processed
                if (!isBuilding)
                {
                    // Update the link dialog based on the link(s) selected
                    updateTableSelection();
                }
            }
        });

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
            /**************************************************************************************
             * Handle a change to the tree expansion check box selection
             *************************************************************************************/
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

        // In order to align the link and variable trees a phantom check box must be added to the
        // link tree panel. To prevent display of the check box components an empty panel is placed
        // over it
        JPanel hiddenPnl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hiddenPnl.setBorder(emptyBorder);
        JCheckBox hiddenChkBx = new JCheckBox(" ");
        hiddenChkBx.setBorder(emptyBorder);
        gbc.gridy++;
        treePnl.add(hiddenPnl, gbc);
        hiddenChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        hiddenChkBx.setFocusable(false);
        hiddenChkBx.setDisabledIcon(null);
        hiddenChkBx.setEnabled(false);
        treePnl.add(hiddenChkBx, gbc);

        return treePnl;
    }
}
