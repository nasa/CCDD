/**
 * CFS Command and Data Dictionary link manager handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
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
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.LinkInformation;
import CCDD.CcddConstants.InternalTable.LinksColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddUndoHandler.UndoableTextArea;
import CCDD.CcddUndoHandler.UndoableTreePathSelection;

/**************************************************************************************************
 * CFS Command and Data Dictionary link manager handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddLinkManagerHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddLinkManagerDialog linkDialog;
    private CcddTableTreeHandler variableTree;
    private CcddLinkTreeHandler linkTree;
    private CcddUndoManager undoManager;
    private CcddUndoHandler undoHandler;

    // Components referenced by multiple methods
    private Border border;
    private Border emptyBorder;
    private UndoableTextArea descriptionFld;
    private JTextField updateRateFld;
    private JTextField sizeInBytesFld;
    private PaddedComboBox rateFilter;
    private JScrollPane descScrollPane;
    private JPanel managerPnl;
    private UndoableTreePathSelection pathSelect;

    // Name of the data stream rate column associated with this link manager
    private final String rateName;

    // Link definitions stored in the database
    private List<String[]> committedLinks;

    // Current link definitions
    private List<String[]> currentLinks;

    // Currently selected link in the link tree; null if none, or more than one link is selected
    private LinkInformation selectedLink;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Currently selected link rate
    private String selectedRate;

    // Flag indicating the first rate selection change
    private boolean firstRateChange;

    // Flag that indicates if the link manager dialog is undergoing initialization or the link tree
    // is rebuilding
    private boolean isInitializing;

    /**********************************************************************************************
     * Link manager handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param linkDialog
     *            reference to the link manager dialog that created this link manager handler
     *
     * @param rateName
     *            data stream rate column name
     *
     * @param availableRates
     *            array of sample rates available to this stream
     *********************************************************************************************/
    CcddLinkManagerHandler(CcddMain ccddMain,
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

    /**********************************************************************************************
     * Set the link manager initialization flag. When true this prevents the link manager dialog's
     * update indicator from being updated
     *
     * @param enable
     *            true is the link manager is initializing
     *********************************************************************************************/
    protected void setInitializing(boolean enable)
    {
        isInitializing = enable;
    }

    /**********************************************************************************************
     * Set the rate filter
     *
     * @param rate
     *            rate filter
     *********************************************************************************************/
    protected void setRateFilter(String rate)
    {
        rateFilter.setSelectedItem(rate);
    }

    /**********************************************************************************************
     * Get the reference to the variable tree
     *
     * @return Reference to the variable tree
     *********************************************************************************************/
    protected CcddTableTreeHandler getVariableTree()
    {
        return variableTree;
    }

    /**********************************************************************************************
     * Get the reference to the link tree
     *
     * @return Reference to the link tree
     *********************************************************************************************/
    protected CcddLinkTreeHandler getLinkTree()
    {
        return linkTree;
    }

    /**********************************************************************************************
     * Get a reference to the link handler panel
     *
     * @return Reference to the link handler panel
     *********************************************************************************************/
    protected JPanel getHandlerPanel()
    {
        return managerPnl;
    }

    /**********************************************************************************************
     * Get a reference to the current links
     *
     * @return Reference to the current links
     *********************************************************************************************/
    protected List<String[]> getCurrentLinks()
    {
        return currentLinks;
    }

    /**********************************************************************************************
     * Get a reference to the data stream rate column
     *
     * @return Reference to the data stream rate column
     *********************************************************************************************/
    protected String getRateName()
    {
        return rateName;
    }

    /**********************************************************************************************
     * Update the committed links to the current links tree
     *********************************************************************************************/
    protected void updateCommittedLinks()
    {
        // Store the links in the committed links list
        committedLinks = linkTree.createDefinitionsFromTree();
    }

    /**********************************************************************************************
     * Get the reference to the link manager's undo manager
     *
     * @return Reference to the link manager's undo manager
     *********************************************************************************************/
    protected CcddUndoManager getUndoManager()
    {
        return undoManager;
    }

    /**********************************************************************************************
     * Get the reference to the link manager's undo handler
     *
     * @return Reference to the link manager's undo handler
     *********************************************************************************************/
    protected CcddUndoHandler getUndoHandler()
    {
        return undoHandler;
    }

    /**********************************************************************************************
     * Create the variable link manager dialog
     *
     * @param availableRates
     *            array of sample rates available to this stream
     *********************************************************************************************/
    private void initialize(String[] availableRates)
    {
        isNodeSelectionChanging = false;

        // Set the flag to indicate the link manager dialog is being initialized
        isInitializing = true;

        // Create borders for the dialog components
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                    ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Add an undo edit manager
        undoManager = new CcddUndoManager()
        {
            /**************************************************************************************
             * Update the change indicator if the link manager has changed
             *************************************************************************************/
            @Override
            protected void ownerHasChanged()
            {
                // Check if the update indicator should be refreshed
                if (!isInitializing)
                {
                    linkDialog.updateChangeIndicator();
                }
            }
        };

        // Create the undo handler for the components with undoable actions. Disable storage of
        // edit actions during dialog creation
        undoHandler = new CcddUndoHandler(undoManager);
        pathSelect = undoHandler.new UndoableTreePathSelection();
        undoHandler.setAllowUndo(false);

        // Build the link tree
        linkTree = new CcddLinkTreeHandler(ccddMain,
                                           undoHandler,
                                           rateName,
                                           ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a node in the link tree
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to inhibit registering the node selection as a link change
                    isInitializing = true;

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

                    // Update the description field text so that it can be undone/redone. The focus
                    // change, which is usually used to perform the update, occurs after the node
                    // selection edit and would cause the wrong description field to be changed
                    descriptionFld.updateText(descriptionFld.isFocusOwner());

                    // Get the name of the selected link(s)
                    String[] selected = getTopLevelSelectedNodeNames();

                    // If a single link is selected then set the selected link, enable and populate
                    // the description, rate, and size in bytes fields; otherwise clear the
                    // selected link, disable and clear the description, rate, and size in bytes
                    // fields
                    setLinkAndFields(selected.length == 1
                                                          ? removeExtraText(selected[0])
                                                          : null,
                                     selected.length != 0);

                    // Check if the node change isn't set to be ignored for undo/redo purposes. The
                    // undo handler sets the flag so that the undo/redo operation isn't recorded on
                    // the undo/redo stack
                    if (undoHandler.isAllowUndo())
                    {
                        // Add the node path selection change to the undo/redo stack
                        pathSelect.selectTreePath(getSelectedPaths());
                    }

                    // Reset the flag to allow link tree updates
                    isInitializing = false;
                    isNodeSelectionChanging = false;
                }
            }

            /******************************************************************************
             * Update the link name following a node name change undo or redo operation
             *
             * @param wasValue
             *            link name prior to the undo/redo operation
             *
             * @param isValue
             *            link name after the undo/redo operation
             *****************************************************************************/
            @Override
            protected void nodeRenameCleanup(Object wasValue, Object isValue)
            {
                // Get the link names by removing any extra text (rate) from the node names
                String wasValueS = removeExtraText(wasValue.toString());
                String isValueS = removeExtraText(isValue.toString());

                // Step through the link's definitions
                for (String[] linkDefn : getLinkHandler().getLinkDefinitionsByName(wasValueS,
                                                                                   getRateName()))
                {
                    // Update the link definition's link name
                    linkDefn[LinksColumn.LINK_NAME.ordinal()] = isValueS;
                }

                // Update the link's name in the link information
                getLinkInformation(wasValueS).setName(isValueS);
            }
        };

        // Set the link tree reference in the undo handler so that tree edits can be undone/redone
        undoHandler.setTree(linkTree);

        // Store the initial link definitions. These are filtered so that only those with the same
        // data stream rate are represented
        updateCommittedLinks();

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
        dlgLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        titlePnl.add(dlgLabel, gbc);

        // Add the upper panel components to the dialog panel
        managerPnl.add(titlePnl, gbc);

        // Initialize the currently selected rate to 1 Hz if present in the list of available
        // rates; otherwise choose the first rate if any rates exist, and if none exist set the
        // rate to a dummy value
        selectedRate = Arrays.asList(availableRates).contains("1")
                                                                   ? "1"
                                                                   : (availableRates.length != 0
                                                                                                 ? CcddUtilities.removeHTMLTags(availableRates[0])
                                                                                                 : "0");

        // Build the variable tree that shows tables and their variables for the selected rate. Use
        // the first rate in the available rates array to determine which variables to display in
        // the tree, or, if none, create the tree showing no variables
        variableTree = new CcddTableTreeHandler(ccddMain,
                                                new CcddGroupHandler(ccddMain,
                                                                     undoHandler,
                                                                     ccddMain.getMainFrame()),
                                                TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES_AND_RATES,
                                                rateName,
                                                selectedRate,
                                                linkTree.getLinkVariables(null),
                                                null, // Unused in link manager
                                                null,
                                                ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a node in the variable tree
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Select the associated link in the link tree if a linked variable is selected
                    // in the variable tree. Note that below any linked variables are deselected,
                    // so this call must occur first
                    selectLinkByVariable();

                    // Set the flag to prevent variable tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that are disabled
                    clearDisabledNodes();

                    // Reset the flag to allow variable tree updates
                    isNodeSelectionChanging = false;
                }
            }

            /**************************************************************************************
             * Override building the table tree in order to apply the rate filter and change the
             * instances node name
             *************************************************************************************/
            @Override
            protected void buildTableTree(Boolean isExpanded,
                                          String rateName,
                                          String rateFilter,
                                          Component parent)
            {
                // Set the flag to inhibit registering a link change due to the tree is being built
                isInitializing = true;
                super.buildTableTree(isExpanded,
                                     rateName,
                                     rateFilter,
                                     parent);
                isInitializing = false;

                // Clean up the links following rebuilding the tree
                variableTree = this;
                cleanUpLinks(null);
            }

            /**************************************************************************************
             * Override to account for the tree having no header node between the root and table
             * nodes
             *************************************************************************************/
            @Override
            protected int getHeaderNodeLevel()
            {
                return super.getHeaderNodeLevel() - 1;
            }
        };

        // Add the title panel components to the dialog panel
        managerPnl.add(titlePnl, gbc);

        // Create a table tree panel and add it to another panel (in order to control spacing)
        gbc.insets.top = 0;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.weighty = 1.0;
        treePnl.add(variableTree.createTreePanel("Structures & Variables",
                                                 TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                 false,
                                                 ccddMain.getMainFrame()),
                    gbc);
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = 0;

        // Create a split pane containing the variable tree in the left pane and the link tree in
        // the right pane and add it to the panel. The arrow button panel is used as the split pane
        // divider
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.gridy++;
        managerPnl.add(new CustomSplitPane(treePnl,
                                           linkTree.createTreePanel("Links",
                                                                    TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION),
                                           createArrowButtonPanel(),
                                           JSplitPane.HORIZONTAL_SPLIT),
                       gbc);

        // Create the link description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.weighty = 0.0;
        descPnl.add(descriptionLbl, gbc);

        // Create the link description input field
        descriptionFld = undoHandler.new UndoableTextArea("", 3, 1);
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(false);
        descriptionFld.setLineWrap(true);
        descriptionFld.setWrapStyleWord(true);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        // Add a listener to detect addition or deletion of text in the input field
        descriptionFld.getDocument().addDocumentListener(new DocumentListener()
        {
            /**************************************************************************************
             * Update the change indicator when text is added
             *************************************************************************************/
            @Override
            public void insertUpdate(DocumentEvent de)
            {
                undoManager.ownerHasChanged();
            }

            /**************************************************************************************
             * Update the change indicator when text is removed
             *************************************************************************************/
            @Override
            public void removeUpdate(DocumentEvent de)
            {
                undoManager.ownerHasChanged();
            }

            /**************************************************************************************
             * Handle updates to a attribute change (unused)
             *************************************************************************************/
            @Override
            public void changedUpdate(DocumentEvent de)
            {
            }
        });

        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
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
        rateLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        rateLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        rateAndSizePnl.add(rateLbl);

        updateRateFld = new JTextField(2);
        updateRateFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        updateRateFld.setEditable(false);
        updateRateFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        updateRateFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        updateRateFld.setBorder(border);
        updateRateFld.setHorizontalAlignment(SwingConstants.CENTER);
        rateAndSizePnl.add(updateRateFld);

        JLabel bytesLbl = new JLabel("   Size in bytes:");
        bytesLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        bytesLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
        rateAndSizePnl.add(bytesLbl);

        sizeInBytesFld = new JTextField(2);
        sizeInBytesFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        sizeInBytesFld.setEditable(false);
        sizeInBytesFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        sizeInBytesFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        sizeInBytesFld.setBorder(border);
        sizeInBytesFld.setHorizontalAlignment(SwingConstants.CENTER);
        rateAndSizePnl.add(sizeInBytesFld);

        // Add the rate panel to the link information panel
        gbc.weighty = 0.0;
        gbc.gridy++;
        infoPnl.add(rateAndSizePnl, gbc);

        // Add the link information panel to the dialog
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        managerPnl.add(infoPnl, gbc);

        // Create the rate selection label
        JLabel rateSelectLbl = new JLabel("Select rate:");
        rateSelectLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        rateSelectLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        rateSelectPnl.add(rateSelectLbl, gbc);

        // Create the combo box that displays the variable rates and add it to the dialog panel
        rateFilter = new PaddedComboBox(availableRates, ModifiableFontInfo.INPUT_TEXT.getFont())
        {
            /**************************************************************************************
             * Override so that items flagged as disabled (grayed out) can't be selected
             *************************************************************************************/
            @Override
            public void setSelectedItem(Object anObject)
            {
                // Check if the item isn't flagged as disabled
                if (!anObject.toString().startsWith(DISABLED_TEXT_COLOR))
                {
                    // Set the selected item to the specified item, if it exists in the list
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
            /**************************************************************************************
             * Rebuild the table tree using the selected rate filter
             *************************************************************************************/
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

                    // Rebuild the variable tree using the selected rate as a filter
                    variableTree.buildTableTree(null,
                                                rateName,
                                                selectedRate,
                                                linkDialog);
                }

                // Get the list of all variable tree paths in the variable tree and set these in
                // the links tree. This is used to maintain the correct variable order in the links
                // tree
                linkTree.setTreePathOrder(variableTree.getTableTreePathList(null,
                                                                            variableTree.getRootNode(),
                                                                            -1));

                // Check if this is the first time the rate selection occurs
                if (firstRateChange)
                {
                    // Force the link tree to be rebuilt now that the tree path order is
                    // established (via setting the rate filter). This forces the link variables to
                    // appear in the same order as they are listed in their prototype tables
                    linkTree.buildTree(false, rateName, false, linkDialog);

                    // Set the flag to prevent rebuilding the link tree when subsequent rate
                    // selection changes are made
                    firstRateChange = false;
                }

                // Set the rate in the link tree to flag compatible links
                linkTree.setSelectedRate(selectedRate);

                // Add the rate and size to the link nodes and set the color based on the selected
                // rate
                linkTree.adjustNodeText(linkTree.getRootNode());
            }
        });

        // Set the flag so that the rate change executed below triggers a rebuilding of the links
        // tree using the tree path order in the variables tree
        firstRateChange = true;

        // Set the rate filter to the selected rate. This initial setting updates the link tree,
        // but skips rebuilding the variable tree unnecessarily
        rateFilter.setSelectedItem(selectedRate);

        // Re-enable storage of edit actions now that dialog creation is complete
        undoHandler.setAllowUndo(true);

        // Create the rate units label and add it to the dialog panel
        JLabel rateUnitsLbl = new JLabel("samples/second");
        rateUnitsLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        rateUnitsLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
        gbc.gridx++;
        rateSelectPnl.add(rateUnitsLbl, gbc);

        // Add the rate selection panel to the dialog panel
        gbc.gridx = 0;
        gbc.gridy++;
        managerPnl.add(rateSelectPnl, gbc);

        // Reset the flag now that initialization is complete
        isInitializing = false;
    }

    /**********************************************************************************************
     * Set/clear the selected link, enable/disable the description, rate, and size in bytes fields,
     * and enable/disable the dialog's buttons that apply only if a single link is selected based
     * on if a valid link name is provided
     *
     * @param linkName
     *            name of the selected link; null if no link or more than one link is selected
     *
     * @param canCopy
     *            true if the copy operation is allowed (i.e., one or more links is selected)
     *********************************************************************************************/
    private void setLinkAndFields(String linkName, boolean canCopy)
    {
        // Check if the edit sequence is set to be automatically terminated. During an undo or redo
        // operation the edit sequence termination is handled manually in order to group the tree
        // and link information updates into a single compound edit
        if (undoHandler.isAutoEndEditSequence())
        {
            // End any active edit sequence
            undoManager.endEditSequence();
        }

        // Initialize the description, rate, and size in bytes assuming a single link isn't
        // selected
        boolean enable = false;
        selectedLink = null;
        Color fieldColor = ModifiableColorInfo.INPUT_DISABLE_BACK.getColor();
        String description = "";
        String rate = "";
        String size = "";

        // Check if a single link is selected
        if (linkName != null)
        {
            // Get the link's information
            selectedLink = linkTree.getLinkInformation(linkName);
        }

        // Check that the link exists. The selected link can be null if multiple links are deleted
        // at the same time
        if (selectedLink != null)
        {
            enable = true;
            fieldColor = ModifiableColorInfo.INPUT_BACK.getColor();

            // Get the link size in bytes, rate, and description. The rate is set to a blank if the
            // link contains no variables
            size = String.valueOf(linkTree.getLinkHandler().getLinkSizeInBytes(rateName,
                                                                               linkTree.removeExtraText(linkName)));
            rate = selectedLink.getSampleRate().equals("0") || size.equals("0")
                                                                                ? ""
                                                                                : selectedLink.getSampleRate();
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

        // Enable/disable the link manager buttons that apply only when one or more links is
        // selected
        linkDialog.setLinkButtonsEnabled(enable, canCopy);
    }

    /**********************************************************************************************
     * Create a panel to contain a pair of arrow buttons. Make all but the button icons transparent
     *
     * @return JPanel containing the arrow buttons in a vertical layout
     *********************************************************************************************/
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
            /**************************************************************************************
             * Remove the selected variable(s) from the link
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the selected node(s) in the tree
                String[] selected = linkTree.getTopLevelSelectedNodeNames();

                // Check that a node is selected
                if (selected.length != 0)
                {
                    // Disable automatically ending the edit sequence. This allows all of the added
                    // link members to be grouped into a single sequence so that if undone, all
                    // members are removed together
                    undoHandler.setAutoEndEditSequence(false);

                    // Remove the variable(s) from the link
                    removeVariableFromLink(selected);

                    // Toggle the link tree expansion for the selected link. This is necessary to
                    // get the node to display correctly when formatted as HTML
                    linkTree.fireTreeCollapsed(linkTree.getSelectionPath());
                    linkTree.fireTreeExpanded(linkTree.getSelectionPath());

                    // Re-enable automatic edit sequence ending, then end the edit sequence to
                    // group the added link members
                    undoHandler.setAutoEndEditSequence(true);
                    undoManager.endEditSequence();
                }
            }
        });

        // Create the 'add item to the tree' button
        rightArrowBtn.setIcon(new ImageIcon(getClass().getResource(RIGHT_ICON)));
        rightArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the add item button
        rightArrowBtn.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Add the the selected variable(s) to the selected link
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Get the selected node(s) in the tree (can only be one)
                String[] selected = linkTree.getTopLevelSelectedNodeNames();

                // Check that a single node is selected. Only valid link nodes (ones with the same
                // rate or empty ones) can be selected; invalid link nodes are grayed out
                if (selected.length == 1)
                {
                    // Set the flag to prevent calls to update the dialog change indicator while
                    // adding variables to the link
                    isInitializing = true;

                    // Disable automatically ending the edit sequence. This allows all of the
                    // deleted link members to be grouped into a single sequence so that if undone,
                    // all members are restored together
                    undoHandler.setAutoEndEditSequence(false);

                    // Add the variable(s) to the link
                    addVariableToLink(selected[0]);

                    // Toggle the link tree expansion for the selected link. This is necessary to
                    // get the node to display correctly when formatted as HTML
                    linkTree.fireTreeCollapsed(linkTree.getSelectionPath());
                    linkTree.fireTreeExpanded(linkTree.getSelectionPath());

                    // Set the flag to so that the dialog change indicator can be updated
                    isInitializing = false;

                    // Update the link dialog's change indicator
                    linkDialog.updateChangeIndicator();

                    // Re-enable automatic edit sequence ending, then end the edit sequence to
                    // group the deleted link members
                    undoHandler.setAutoEndEditSequence(true);
                    undoManager.endEditSequence();
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

        // Create a panel to contain the buttons. Arrange the buttons vertically
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
        gbc.insets.bottom = LAF_CHECK_BOX_HEIGHT * 2
                            + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        buttonPnl.add(leftArrowBtn, gbc);

        return buttonPnl;
    }

    /**********************************************************************************************
     * Add the selected variable(s) in the variable tree to the selected link definition(s) in the
     * link tree
     *
     * @param linkName
     *            name of the link to which to add the variable(s)
     *********************************************************************************************/
    private void addVariableToLink(String linkName)
    {
        // Add the selected variable(s) to the link tree. The start node is decremented to account
        // for there not being an instance node in the tree
        linkTree.addSourceNodesToTargetNode(variableTree.getSelectedVariables(true),
                                            variableTree.getHeaderNodeLevel(),
                                            true);

        // Set the link's sample rate to the currently selected rate. This only makes a change if
        // this is the first variable(s) added to the link
        linkTree.getLinkInformation(linkName).setSampleRate(selectedRate);

        // Clean up the links following addition of the variable
        cleanUpLinks(new String[] {linkName});
    }

    /**********************************************************************************************
     * Remove the selected variable(s) from the link and reenable them in the variable tree
     *
     * @param linkNames
     *            array containing the name(s) of the link(s) from which to remove the variable(s)
     *********************************************************************************************/
    private void removeVariableFromLink(String[] linkNames)
    {
        ToolTipTreeNode node = null;

        // Check if a single link is selected
        if (selectedLink != null)
        {
            // Store the selected group's node
            node = (ToolTipTreeNode) linkTree.getSelectionPath().getPathComponent(1);
        }

        // Remove the selected variable(s) from the links in the link tree
        linkTree.removeSelectedNodes();

        // Step through each on the links
        for (String linkName : linkNames)
        {
            // Check if the link has no member variables
            if (linkTree.getLinkVariables(linkName).size() == 0)
            {
                // Set the link's sample rate to zero (indicating it has no rate since it has no
                // members)
                linkTree.getLinkInformation(linkName).setSampleRate("0");
            }
        }

        // Clean up the links following removal of the variable
        cleanUpLinks(linkNames);

        // Check if a single link was selected prior to removing the selected variable(s)
        if (node != null)
        {
            // Select the node for the group that had the table(s) removed
            linkTree.setSelectionPath(new TreePath(node.getPath()));
        }
    }

    /**********************************************************************************************
     * Clean up the links following the addition or deletion of a variable
     *
     * @param linkNames
     *            array containing the name of the link(s) to be updated; null to perform the
     *            cleaning but without updating the link fields
     *********************************************************************************************/
    private void cleanUpLinks(String[] linkNames)
    {
        // Update the link definitions to account for the changes
        linkTree.updateLinkDefinitions();

        // Add the rate and size to the link nodes and set the color based on the selected rate
        linkTree.adjustNodeText(linkTree.getRootNode());

        // Get the list of all linked variables
        List<String> linkedVars = linkTree.getLinkVariables(null);

        // Set the linked variables list in the variable tree
        variableTree.setLinkedVariables(linkedVars);

        // Update the variable tree with the list of variables that are already linked
        variableTree.setExcludedVariables(linkedVars);

        // Check if the link names are provided
        if (linkNames != null)
        {
            // If a single link is selected then set the selected link, enable and populate the
            // description, rate, and size in bytes fields; otherwise clear the selected link,
            // disable and clear the description, rate, and size in bytes fields
            setLinkAndFields(linkNames.length == 1
                                                   ? linkNames[0]
                                                   : null,
                             linkNames.length != 0);
        }

        // Update the link dialog's change indicator
        undoManager.ownerHasChanged();
    }

    /**********************************************************************************************
     * Clean up the links following an undo/redo operation
     *********************************************************************************************/
    protected void cleanUpLinks()
    {
        cleanUpLinks(linkTree.getTopLevelSelectedNodeNames());
    }

    /**********************************************************************************************
     * Check if the links differ from those last committed to the database
     *
     * @return true if the link definitions have changed
     *********************************************************************************************/
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

        // Initialize the change flag to true if the number of current and committed link
        // definitions differ or if the maximum link byte value has changed
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
                    // Check if the current link entry matches the committed link entry
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
                    // Set the flag indicating a link has changed and stop searching
                    hasChanges = true;
                    break;
                }
            }
        }

        return hasChanges;
    }

    /**********************************************************************************************
     * Get a list of link member variables that are no longer valid for the telemetry scheduler
     * table due to the addition of one or more new member variables. If a telemetry message
     * references a linked variable, and a new variable is added to the link definition, then the
     * message no longer references all of the variables that are linked. Since the additional
     * variable(s) may not fit within the message's maximum size, all of the variable in the link
     * are removed from the message(s). The user must add the linked variables back to the messages
     * using the telemetry scheduler
     *
     * @return List of link member variables that are no longer valid for the telemetry scheduler
     *         table due to the addition of one or more new member variables; and empty list if no
     *         links are invalid
     *********************************************************************************************/
    protected List<String> getInvalidatedLinkMembers()
    {
        List<String> checkedLinks = new ArrayList<String>();
        List<String> invalidatedLinks = new ArrayList<String>();

        CcddLinkHandler oldLinkHndlr = new CcddLinkHandler(ccddMain, committedLinks);
        CcddLinkHandler newLinkHndlr = linkTree.getLinkHandler();

        // Step through each link by name
        for (String linkName : oldLinkHndlr.getLinkNamesByRate(rateName))
        {
            // Check if this link has already been processed. Two links can be checked during one
            // pass when a variable is transferred from one link to another
            if (!checkedLinks.contains(linkName))
            {
                boolean isLinkInvalid = false;

                // Add the link to the list of those checked so that is isn't checked again
                checkedLinks.add(linkName);

                // Get the link definitions for this link name
                ArrayListMultiple oldDefns = new ArrayListMultiple(LinksColumn.MEMBER.ordinal());
                oldDefns.addAll(oldLinkHndlr.getLinkDefinitionsByName(linkName, rateName));

                // Check if the link had any variables assigned to it
                if (!oldDefns.isEmpty())
                {
                    // Get the name of the link to which the variable now belongs (this may be
                    // another link)
                    String newLinkName = newLinkHndlr.getVariableLink(oldDefns.get(0)[LinksColumn.MEMBER.ordinal()],
                                                                      rateName);

                    // Check if the variable is still a member of a link
                    if (newLinkName != null)
                    {
                        // Check if this link hasn't already been added to the list of those
                        // processed (e.g., the variable is not within the original link)
                        if (!checkedLinks.contains(newLinkName))
                        {
                            // Add the link to the list of those checked so that is isn't checked
                            // again
                            checkedLinks.add(newLinkName);
                        }

                        // Get the link definitions of the link for which the variable is currently
                        // a member
                        List<String[]> newDefns = newLinkHndlr.getLinkDefinitionsByName(newLinkName,
                                                                                        rateName);

                        // Check if link to which the variable belongs has the same or fewer
                        // members than before
                        if (newDefns.size() <= oldDefns.size())
                        {
                            // Step through each of the link's original definitions
                            for (String[] newDefn : newDefns)
                            {
                                // Check if the link to which the variable belongs doesn't contain
                                // all of the same members it had before
                                if (!oldDefns.contains(newDefn[LinksColumn.MEMBER.ordinal()]))
                                {
                                    // Set the flag to indicate the link has new members and stop
                                    // searching
                                    isLinkInvalid = true;
                                    break;
                                }
                            }
                        }
                        // The link containing the variable has more members than before
                        else
                        {
                            // Set the flag to indicate the link has new members
                            isLinkInvalid = true;
                        }

                        // Check if the link has new members
                        if (isLinkInvalid)
                        {
                            // Step through the new link definitions
                            for (String[] newDefn : newDefns)
                            {
                                // Add the variable to the invalidated links list
                                invalidatedLinks.add(newDefn[LinksColumn.MEMBER.ordinal()]);
                            }
                        }
                    }
                }
            }
        }

        return invalidatedLinks;
    }

    /**********************************************************************************************
     * Select the link in the link tree for which the selected variable in the variable tree is a
     * member
     *********************************************************************************************/
    private void selectLinkByVariable()
    {
        // Get the array of selected paths in the variable tree
        TreePath[] selectedPaths = variableTree.getSelectionPaths();

        // Check if only a single node is selected
        if (selectedPaths != null && selectedPaths.length == 1)
        {
            // Clear any currently selected link(s)
            linkTree.clearSelection();

            // Get the first selected variable's path
            String variablePath = variableTree.getFullVariablePath(variableTree.getSelectionPath().getPath());

            // Check if the variable contains the HTML flags indicating it is in use; i.e., belongs
            // to a link
            if (variablePath.contains(DISABLED_TEXT_COLOR))
            {
                // Remove the HTML flags from the variable path
                variablePath = variableTree.removeExtraText(variablePath);

                // Step through the link tree nodes that show the link names
                for (int linkIndex = 0; linkIndex < linkTree.getRootNode().getChildCount(); linkIndex++)
                {
                    // Get the link name node from the link tree
                    ToolTipTreeNode linkNode = ((ToolTipTreeNode) linkTree.getRootNode().getChildAt(linkIndex));

                    // Step through the variables belonging to the link
                    for (String[] linkDefn : linkTree.getLinkHandler().getLinkDefinitionsByName(linkTree.removeExtraText(linkNode.getUserObject().toString()), rateName))
                    {
                        // Check if the selected variable matches the link variable
                        if (variablePath.equals(linkDefn[LinksColumn.MEMBER.ordinal()]))
                        {
                            // Select the link to which the variable belongs
                            linkTree.setSelectionPath(CcddCommonTreeHandler.getPathFromNode(linkNode));

                            // Set the index to the maximum value to force the outer loop to end,
                            // then exit the inner loop to stop searching this link
                            linkIndex = linkTree.getRootNode().getChildCount();
                            break;
                        }
                    }
                }
            }
        }
    }
}
