/**
 * CFS Command & Data Dictionary link manager handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.LAF_CHECK_BOX_HEIGHT;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClasses.CustomSplitPane;
import CCDD.CcddClasses.LinkInformation;
import CCDD.CcddClasses.PaddedComboBox;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary link manager handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddLinkManagerHandler extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddLinkManagerDialog linkDialog;
    private CcddTableTreeHandler variableTree;
    private CcddLinkTreeHandler linkTree;

    // Components referenced by multiple methods
    private Border border;
    private Border emptyBorder;
    private JTextArea descriptionFld;
    private JTextField updateRateFld;
    private JTextField sizeInBytesFld;
    private PaddedComboBox rateFilter;
    private JScrollPane descScrollPane;
    private JPanel managerPnl;

    // Name of the data stream rate column associated with this link manager
    private final String rateName;

    // Link definitions stored in the database
    private List<String[]> committedLinks;

    // Current link definitions
    private List<String[]> currentLinks;

    // Currently selected link in the link tree; null if none, or more than
    // one link is selected
    private LinkInformation selectedLink;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Currently selected link rate
    private String selectedRate;

    /**************************************************************************
     * Link manager handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param linkDialog
     *            reference to the link manager dialog that created this link
     *            manager handler
     * 
     * @param rateName
     *            data stream rate column name
     * 
     * @param availableRates
     *            array of sample rates available to this stream
     *************************************************************************/
    protected CcddLinkManagerHandler(CcddMain ccddMain,
                                     CcddLinkManagerDialog linkDialog,
                                     String rateName,
                                     String[] availableRates)
    {
        this.ccddMain = ccddMain;
        this.linkDialog = linkDialog;
        this.rateName = rateName;

        // Create the link selection dialog
        initialize(availableRates);
    }

    /**************************************************************************
     * Set the rate filter
     * 
     * @param rate
     *            rate filter
     *************************************************************************/
    protected void setRateFilter(String rate)
    {
        rateFilter.setSelectedItem(rate);
    }

    /**************************************************************************
     * Get the reference to the variable tree
     * 
     * @return Reference to the variable tree
     *************************************************************************/
    protected CcddTableTreeHandler getVariableTree()
    {
        return variableTree;
    }

    /**************************************************************************
     * Get the reference to the link tree
     * 
     * @return Reference to the link tree
     *************************************************************************/
    protected CcddLinkTreeHandler getLinkTree()
    {
        return linkTree;
    }

    /**************************************************************************
     * Get a reference to the link handler panel
     * 
     * @return Reference to the link handler panel
     *************************************************************************/
    protected JPanel getHandlerPanel()
    {
        return managerPnl;
    }

    /**************************************************************************
     * Get a reference to the current links
     * 
     * @return Reference to the current links
     *************************************************************************/
    protected List<String[]> getCurrentLinks()
    {
        return currentLinks;
    }

    /**************************************************************************
     * Get a reference to the data stream rate column
     * 
     * @return Reference to the data stream rate column
     *************************************************************************/
    protected String getRateName()
    {
        return rateName;
    }

    /**************************************************************************
     * Update the committed links to the current links tree
     *************************************************************************/
    protected void updateCommittedLinks()
    {
        // Store the links in the committed links list
        committedLinks = linkTree.createDefinitionsFromTree();
    }

    /**************************************************************************
     * Create the variable link manager dialog
     * 
     * @param availableRates
     *            array of sample rates available to this stream
     *************************************************************************/
    private void initialize(String[] availableRates)
    {
        // Create borders for the dialog components
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(2, 2, 2, 2));
        emptyBorder = BorderFactory.createEmptyBorder();

        selectedLink = null;
        currentLinks = new ArrayList<String[]>();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Build the link tree
        linkTree = new CcddLinkTreeHandler(ccddMain,
                                           rateName,
                                           ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a node in the link tree
             *****************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent link tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Check if a link was selected
                    if (selectedLink != null)
                    {
                        // Store the description with the previous link
                        selectedLink.setDescription(descriptionFld.getText().trim());
                    }

                    // Get the name of the selected link (null if more than
                    // one link is selected)
                    String[] selected = getSelectedNode();

                    // If a single link is selected then set the selected
                    // link, enable and populate the description, rate, and
                    // size in bytes fields; otherwise clear the selected link,
                    // disable and clear the description, rate, and size in
                    // bytes fields
                    setLinkAndFields(selected.length == 1
                                                         ? selected[0]
                                                         : null,
                                     selected.length != 0);

                    // Reset the flag to allow link tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Store the initial link definitions. These are filtered so that only
        // those with the same data stream rate are represented
        committedLinks = linkTree.createDefinitionsFromTree();

        // Create panels to hold the components of the dialog
        managerPnl = new JPanel(new GridBagLayout());
        JPanel titlePnl = new JPanel(new GridBagLayout());
        JPanel treePnl = new JPanel(new GridBagLayout());
        JPanel infoPnl = new JPanel(new GridBagLayout());
        JPanel descPnl = new JPanel(new GridBagLayout());
        JPanel rateAndSizePnl = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel rateSelectPnl = new JPanel(new GridBagLayout());
        managerPnl.setBorder(emptyBorder);
        titlePnl.setBorder(emptyBorder);
        treePnl.setBorder(emptyBorder);
        infoPnl.setBorder(BorderFactory.createEtchedBorder());
        descPnl.setBorder(emptyBorder);
        rateSelectPnl.setBorder(emptyBorder);
        rateSelectPnl.setBorder(emptyBorder);

        // Create the link manager dialog labels and fields
        JLabel dlgLabel = new JLabel("Assign variables to links");
        dlgLabel.setFont(LABEL_FONT_BOLD);
        titlePnl.add(dlgLabel, gbc);

        // Add the upper panel components to the dialog panel
        managerPnl.add(titlePnl, gbc);

        // Initialize the currently selected rate to 1 Hz if present in the
        // list of available rates; otherwise choose the first rate if any
        // rates exist, and if none exist set the rate to a dummy value
        selectedRate = Arrays.asList(availableRates).contains("1")
                                                                  ? "1"
                                                                  : (availableRates.length != 0
                                                                                               ? CcddUtilities.removeHTMLTags(availableRates[0])
                                                                                               : "0");

        // Get the list off all linked variables
        List<String> linkedVars = linkTree.getLinkVariables(null);

        // Build the variable tree that shows tables and their variables
        // for the selected rate. Use the first rate in the available rates
        // array to determine which variables to display in the tree, or, if
        // none, create the tree showing no variables
        variableTree = new CcddTableTreeHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain,
                                                                     ccddMain.getMainFrame()),
                                                TableTreeType.INSTANCE_WITH_PRIMITIVES_AND_RATES,
                                                rateName,
                                                selectedRate,
                                                linkedVars,
                                                ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a node in the variable tree
             *****************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent variable tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Deselect any nodes that don't represent a table
                    clearNonTableNodes(1);

                    // Reset the flag to allow variable tree updates
                    isNodeSelectionChanging = false;
                }
            }

            /******************************************************************
             * Override building the table tree in order to apply the rate
             * filter and change the instances node name
             *****************************************************************/
            @Override
            protected void buildTableTree(Boolean isExpanded,
                                          String rateName,
                                          String rateFilter,
                                          Component parent)
            {
                super.buildTableTree(isExpanded, rateName, rateFilter, parent);

                // Rename the instances node. Indicate that the node changed so
                // that the tree redraws the name
                getInstancesNode().setUserObject("Structures & Variables");
                ((DefaultTreeModel) getModel()).nodeChanged(getInstancesNode());

                // Clean up the links following rebuilding the tree
                variableTree = this;
                cleanUpLinks();
            }
        };

        // Add the title panel components to the dialog panel
        managerPnl.add(titlePnl, gbc);

        // Create a table tree panel and add it to another panel (in order to
        // control spacing)
        gbc.insets.top = 0;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.weighty = 1.0;
        treePnl.add(variableTree.createTreePanel("Variables",
                                                 TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                 ccddMain.getMainFrame()),
                    gbc);
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.bottom = 0;

        // Create a split pane containing the variable tree in the left pane
        // and the link tree in the right pane and add it to the panel. The
        // arrow button panel is used as the split pane divider
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.right = LABEL_HORIZONTAL_SPACING;
        gbc.gridy++;
        managerPnl.add(new CustomSplitPane(treePnl,
                                           linkTree.createTreePanel("Links",
                                                                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION),
                                           createArrowButtonPanel()),
                       gbc);

        // Create the link description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionLbl.setForeground(LABEL_TEXT_COLOR);
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.right = LABEL_HORIZONTAL_SPACING / 2;
        gbc.weighty = 0.0;
        descPnl.add(descriptionLbl, gbc);

        // Create the link description input field
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setFont(LABEL_FONT_PLAIN);
        descriptionFld.setEditable(false);
        descriptionFld.setLineWrap(true);
        descriptionFld.setForeground(Color.BLACK);
        descriptionFld.setBackground(Color.LIGHT_GRAY);
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        // Add a listener to detect addition or deletion of text in the input
        // field
        descriptionFld.getDocument().addDocumentListener(new DocumentListener()
        {
            /******************************************************************
             * Update the change indicator when text is added
             *****************************************************************/
            @Override
            public void insertUpdate(DocumentEvent de)
            {
                linkDialog.updateChangeIndicator();
            }

            /******************************************************************
             * Update the change indicator when text is removed
             *****************************************************************/
            @Override
            public void removeUpdate(DocumentEvent de)
            {
                linkDialog.updateChangeIndicator();
            }

            /******************************************************************
             * Handle updates to a attribute change (unused)
             *****************************************************************/
            @Override
            public void changedUpdate(DocumentEvent de)
            {
            }
        });

        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(Color.LIGHT_GRAY);
        descScrollPane.setBorder(border);

        // Add the description field to the dialog panel
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;
        descPnl.add(descScrollPane, gbc);

        // Add the description panel to the link information panel
        gbc.gridy++;
        infoPnl.add(descPnl, gbc);

        // Create the link rate labels and fields
        JLabel rateLbl = new JLabel("Link rate (Hz):");
        rateLbl.setFont(LABEL_FONT_BOLD);
        rateLbl.setForeground(LABEL_TEXT_COLOR);
        rateAndSizePnl.add(rateLbl);

        updateRateFld = new JTextField(2);
        updateRateFld.setFont(LABEL_FONT_PLAIN);
        updateRateFld.setEditable(false);
        updateRateFld.setForeground(Color.BLACK);
        updateRateFld.setBackground(Color.LIGHT_GRAY);
        updateRateFld.setBorder(border);
        updateRateFld.setHorizontalAlignment(JTextField.CENTER);
        rateAndSizePnl.add(updateRateFld);

        JLabel bytesLbl = new JLabel("   Size in bytes:");
        bytesLbl.setFont(LABEL_FONT_BOLD);
        bytesLbl.setForeground(LABEL_TEXT_COLOR);
        rateAndSizePnl.add(bytesLbl);

        sizeInBytesFld = new JTextField(2);
        sizeInBytesFld.setFont(LABEL_FONT_PLAIN);
        sizeInBytesFld.setEditable(false);
        sizeInBytesFld.setForeground(Color.BLACK);
        sizeInBytesFld.setBackground(Color.LIGHT_GRAY);
        sizeInBytesFld.setBorder(border);
        sizeInBytesFld.setHorizontalAlignment(JTextField.CENTER);
        rateAndSizePnl.add(sizeInBytesFld);

        // Add the rate panel to the link information panel
        gbc.weighty = 0.0;
        gbc.gridy++;
        infoPnl.add(rateAndSizePnl, gbc);

        // Add the link information panel to the dialog
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        managerPnl.add(infoPnl, gbc);

        // Create the rate selection label
        JLabel rateSelectLbl = new JLabel("Select rate:");
        rateSelectLbl.setFont(LABEL_FONT_BOLD);
        rateSelectLbl.setForeground(Color.BLACK);
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        rateSelectPnl.add(rateSelectLbl, gbc);

        // Create the combo box that displays the variable rates and add it to
        // the dialog panel
        rateFilter = new PaddedComboBox(availableRates, LABEL_FONT_PLAIN)
        {
            /******************************************************************
             * Override so that items flagged as disabled (grayed out) can't be
             * selected
             *****************************************************************/
            @Override
            public void setSelectedItem(Object anObject)
            {
                // Check if the item isn't flagged as disabled
                if (!anObject.toString().startsWith(DISABLED_TEXT_COLOR))
                {
                    // Set the selected item to the specified item, if it
                    // exists in the list
                    super.setSelectedItem(anObject);
                }
            }
        };

        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx++;
        rateSelectPnl.add(rateFilter, gbc);

        // Add a listener for rate filter selection changes
        rateFilter.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Rebuild the table tree using the selected rate filter
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the rate selected in the combo box
                String newRate = ((JComboBox<?>) ae.getSource()).getSelectedItem().toString();

                // Check if the rate changed
                if (!selectedRate.equals(newRate))
                {
                    // Set the new rate as the selected rate
                    selectedRate = newRate;

                    // Rebuild the variable tree using the selected rate as a
                    // filter
                    variableTree.buildTableTree(null,
                                                rateName,
                                                selectedRate,
                                                CcddLinkManagerHandler.this);
                }

                // Set the rate in the link tree to flag compatible links
                linkTree.setSelectedRate(selectedRate);

                // Add the rate and size to the link nodes and set the
                // color based on the selected rate
                linkTree.adjustNodeText(linkTree.getRootNode());

                // Get the list of all variable tree paths in the variable tree
                // and set these in the links tree. Set the variable path list
                // in the links tree. This is used to compare variable
                // transfers to the links tree so that the correct variable
                // order is maintained
                linkTree.setTreePathOrder(variableTree.getTableTreePathList(null,
                                                                            variableTree.getNodeFromNodeName("Structures & Variables"),
                                                                            -1));
            }
        });

        // Set the rate filter to the selected rate. This initial setting
        // updates the link tree, but skips rebuilding the variable tree
        // unnecessarily
        rateFilter.setSelectedItem(selectedRate);

        // Create the rate units label and add it to the dialog panel
        JLabel rateUnitsLbl = new JLabel("samples/second");
        rateUnitsLbl.setFont(LABEL_FONT_BOLD);
        rateUnitsLbl.setForeground(Color.BLACK);
        gbc.gridx++;
        rateSelectPnl.add(rateUnitsLbl, gbc);

        // Add the rate selection panel to the dialog panel
        gbc.gridx = 0;
        gbc.gridy++;
        managerPnl.add(rateSelectPnl, gbc);
    }

    /**************************************************************************
     * Set/clear the selected link, enable/disable the description, rate, and
     * size in bytes fields, and enable/disable the dialog's buttons that apply
     * only if a single link is selected based on if a valid link name is
     * provided
     * 
     * @param linkName
     *            name of the selected link; null if no link or more than one
     *            link is selected
     * 
     * @param canCopy
     *            true if the copy operation is allowed (i.e., one or more
     *            links is selected)
     *************************************************************************/
    private void setLinkAndFields(String linkName, boolean canCopy)
    {
        // Initialize the description, rate, and size in bytes assuming a
        // single link isn't selected
        boolean enable = false;
        selectedLink = null;
        Color fieldColor = Color.LIGHT_GRAY;
        String description = "";
        String rate = "";
        String size = "";

        // Check if a single link is selected
        if (linkName != null)
        {
            // Get the link's information
            selectedLink = linkTree.getLinkInformation(linkName);
        }

        // Check that the link exists. The selected link can be null if
        // multiple links are deleted at the same time
        if (selectedLink != null)
        {
            enable = true;
            fieldColor = Color.WHITE;

            // Update the link size label
            updateLinkSizeField();

            // Get the link rate, size in bytes, and description
            rate = selectedLink.getSampleRate().equals("0")
                                                           ? ""
                                                           : selectedLink.getSampleRate();
            size = String.valueOf(linkTree.getLinkHandler().getLinkSizeInBytes(rateName,
                                                                               linkTree.removeExtraText(linkName)));
            description = selectedLink.getDescription();
        }

        // Enable/disable the description fields
        descriptionFld.setEditable(enable);

        // Update the link description, rate, and size in bytes fields
        descriptionFld.setText(description);
        descriptionFld.setBackground(fieldColor);
        descScrollPane.setBackground(fieldColor);
        updateRateFld.setText(rate);
        updateRateFld.setBackground(fieldColor);
        sizeInBytesFld.setText(size);
        sizeInBytesFld.setBackground(fieldColor);

        // Enable/disable the link manager buttons that apply only when one or
        // more links is selected
        linkDialog.setLinkButtonsEnabled(enable, canCopy);
    }

    /**************************************************************************
     * Create a panel to contain a pair of arrow buttons. Make all but the
     * button icons transparent
     * 
     * @return JPanel containing the arrow buttons in a vertical layout
     *************************************************************************/
    private JPanel createArrowButtonPanel()
    {
        // Create the left and right arrow buttons
        JButton leftArrowBtn = new JButton();
        JButton rightArrowBtn = new JButton();

        // Create the 'remove item from the tree' button
        leftArrowBtn.setIcon(new ImageIcon(getClass().getResource(LEFT_ICON)));
        leftArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the remove item button
        leftArrowBtn.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Remove the selected variable(s) from the link
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the selected node(s) in the tree (can only be one)
                String[] selectedNodes = linkTree.getSelectedNode();

                // Check that a node is selected
                if (selectedNodes.length != 0)
                {
                    // Get the link information for the selected node
                    LinkInformation selectedLink = linkTree.getLinkInformation(selectedNodes[0]);

                    // Remove the variable(s) from the link
                    removeVariableFromLink();

                    // Check if the link has no members. Remove any extra text
                    // from the link's node name so that the link comparison
                    // finds the correct link
                    if (linkTree.getLinkHandler().getLinkSizeInBytes(rateName,
                                                                     linkTree.removeExtraText(selectedNodes[0])) == 0)
                    {
                        // Blank the rate in the link and the rate field
                        selectedLink.setSampleRate("0");
                        updateRateFld.setText("");
                    }

                    // Toggle the link tree expansion for the selected link.
                    // This is necessary to get the node to display correctly
                    // when formatted as HTML
                    linkTree.fireTreeCollapsed(linkTree.getSelectionPath());
                    linkTree.fireTreeExpanded(linkTree.getSelectionPath());
                }
            }
        });

        // Create the 'add item to the tree' button
        rightArrowBtn.setIcon(new ImageIcon(getClass().getResource(RIGHT_ICON)));
        rightArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the add item button
        rightArrowBtn.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Add the the selected variable(s) to the selected link
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the selected node(s) in the tree (can only be one)
                String[] selectedNodes = linkTree.getSelectedNode();

                // Check that a single node is selected. Only valid link nodes
                // (ones with the same rate or empty ones) can be selected;
                // invalid link nodes are grayed out
                if (selectedNodes.length == 1)
                {
                    // Get the reference to the selected link's information
                    LinkInformation linkInfo = linkTree.getLinkInformation(selectedNodes[0]);

                    // Set the link's rate if this is its first member(s)
                    // and update the rate field
                    linkInfo.setSampleRate(selectedRate);
                    updateRateFld.setText(selectedRate);

                    // Add the variable(s) to the link
                    addVariableToLink();

                    // Toggle the link tree expansion for the selected
                    // link. This is necessary to get the node to display
                    // correctly when formatted as HTML
                    linkTree.fireTreeCollapsed(linkTree.getSelectionPath());
                    linkTree.fireTreeExpanded(linkTree.getSelectionPath());
                }
            }
        });

        // Set the layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.CENTER,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 0, 0),
                                                        0,
                                                        0);

        // Create a panel to contain the buttons. Arrange the buttons
        // vertically
        JPanel buttonPnl = new JPanel(new GridBagLayout());
        buttonPnl.setBorder(emptyBorder);

        // Hide everything but the button icons
        rightArrowBtn.setOpaque(false);
        rightArrowBtn.setContentAreaFilled(false);
        rightArrowBtn.setBorderPainted(false);
        leftArrowBtn.setOpaque(false);
        leftArrowBtn.setContentAreaFilled(false);
        leftArrowBtn.setBorderPainted(false);

        // Add the buttons to the panel
        buttonPnl.add(rightArrowBtn, gbc);
        gbc.insets.bottom = LAF_CHECK_BOX_HEIGHT + LABEL_VERTICAL_SPACING;
        gbc.gridy++;
        buttonPnl.add(leftArrowBtn, gbc);

        return buttonPnl;
    }

    /**************************************************************************
     * Add the selected variable(s) in the variable tree to the selected link
     * definition(s) in the link tree
     *************************************************************************/
    private void addVariableToLink()
    {
        // Add the selected variable(s) to the link tree
        linkTree.addSourceNodesToTargetNode(variableTree.getSelectedVariables(true),
                                            variableTree.getTableNodeLevel(),
                                            true);

        // Clean up the links following addition of the variable
        cleanUpLinks();
    }

    /**************************************************************************
     * Remove the selected variable(s) from the link and reenable them in the
     * variable tree
     *************************************************************************/
    private void removeVariableFromLink()
    {
        // Remove the selected variable(s) from the links in the link tree
        linkTree.removeSelectedChildNodes(true);

        // Clean up the links following removal of the variable
        cleanUpLinks();
    }

    /**************************************************************************
     * Clean up the links following the addition or deletion of a variable
     *************************************************************************/
    private void cleanUpLinks()
    {
        // Update the link size field
        updateLinkSizeField();

        // Update the link definitions to account for the deletions
        linkTree.updateLinkDefinitions();

        // Add the rate and size to the link nodes and set the color based on
        // the selected rate
        linkTree.adjustNodeText(linkTree.getRootNode());

        // Get the list off all linked variables
        List<String> linkedVars = linkTree.getLinkVariables(null);

        // Set the linked variables list in the variable tree
        variableTree.setLinkedVariables(linkedVars);

        // Update the variable tree with the list of variables that are already
        // linked
        variableTree.setExcludedVariables(linkedVars);

        // Update the link dialog's change indicator
        linkDialog.updateChangeIndicator();
    }

    /**************************************************************************
     * Update the link size in bytes field
     *************************************************************************/
    private void updateLinkSizeField()
    {
        // Check if a single link is currently selected
        if (selectedLink != null)
        {
            // Calculate the link size in bytes and update the text field
            sizeInBytesFld.setText(String.valueOf(linkTree.getLinkHandler().getLinkSizeInBytes(selectedLink.getRateName(),
                                                                                               selectedLink.getName())));
        }
    }

    /**************************************************************************
     * Check if the links differ from those last committed to the database
     * 
     * @return true if the link definitions have changed
     *************************************************************************/
    protected boolean isLinksChanged()
    {
        // Check if a link is selected
        if (selectedLink != null)
        {
            // Store the description is case it has changed
            selectedLink.setDescription(descriptionFld.getText().trim());
        }

        // Get the updated link definitions
        currentLinks = linkTree.createDefinitionsFromTree();

        // Initialize the change flag to true if the number of current and
        // committed link definitions differ or if the maximum link byte
        // value has changed
        boolean hasChanges = currentLinks.size() != committedLinks.size();

        // Check if the number of links is the same
        if (!hasChanges)
        {
            // Step through the current link list
            for (String[] curLink : currentLinks)
            {
                boolean isFound = false;

                // Step through the committed link list
                for (String[] comLink : committedLinks)
                {
                    // Check if the current link entry matches the committed
                    // link entry
                    if (Arrays.equals(curLink, comLink))
                    {
                        // Set the flag indicating a match and stop searching
                        isFound = true;
                        break;
                    }
                }

                // Check if no matching entry was found
                if (!isFound)
                {
                    // Set the flag indicating a link has changed and stop
                    // searching
                    hasChanges = true;
                    break;
                }
            }
        }

        return hasChanges;
    }
}
