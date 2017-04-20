/**
 * CFS Command & Data Dictionary link manager dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.COPY_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.RENAME_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.TABLE_BACK_COLOR;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.LinkInformation;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.LinkCopyErrorColumnInfo;
import CCDD.CcddConstants.TableSelectionMode;

/******************************************************************************
 * CFS Command & Data Dictionary link manager dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddLinkManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddRateParameterHandler rateHandler;
    private CcddLinkManagerHandler activeHandler;

    // Components referenced by multiple methods
    private Border border;
    private JTextField linkNameFld;
    private JTabbedPane tabbedPane;
    private JButton btnRenameLink;
    private JButton btnCopyLink;
    private List<CcddLinkManagerHandler> linkMgrs;

    // Storage for the link definition when a new link is being created
    private String newLinkName;
    private String newLinkDescription;

    // List of streams to which to copy a link
    private List<String> selectedStreams;

    // List containing the links or link members unable to be copied
    private List<Object[]> notCopiedList;

    /**************************************************************************
     * Link manager dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddLinkManagerDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        rateHandler = ccddMain.getRateParameterHandler();

        // Create the link selection dialog
        initialize();
    }

    /**************************************************************************
     * Perform the steps needed following execution of link updates to the
     * database
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doLinkUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Step through each data stream
            for (CcddLinkManagerHandler linkHandler : linkMgrs)
            {
                // Store the links in the committed links list
                linkHandler.updateCommittedLinks();
            }

            // Step through each data stream tab
            for (int index = 0; index < tabbedPane.getTabCount(); index++)
            {
                // Remove the change indicator from the tab title
                tabbedPane.setTitleAt(index,
                                      tabbedPane.getTitleAt(index).replaceAll("\\*", ""));
            }
        }
    }

    /**************************************************************************
     * Create the variable link manager dialog. This is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The
     * GUI menu commands, however, are disabled until the telemetry scheduler
     * initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the variable link manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();

            /******************************************************************
             * Build the variable link manager dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Create a border for the dialog components
                border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                            Color.LIGHT_GRAY,
                                                                                            Color.GRAY),
                                                            BorderFactory.createEmptyBorder(2, 2, 2, 2));

                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                1.0,
                                                                1.0,
                                                                GridBagConstraints.LINE_START,
                                                                GridBagConstraints.BOTH,
                                                                new Insets(0, 0, 0, 0),
                                                                0,
                                                                0);

                // Create a tabbed pane to contain the rate parameters that are
                // stream-specific
                tabbedPane = new JTabbedPane(JTabbedPane.TOP);
                tabbedPane.setFont(LABEL_FONT_BOLD);

                // Listen for tab selection changes
                tabbedPane.addChangeListener(new ChangeListener()
                {
                    /**********************************************************
                     * Update the handler to the one associated with the
                     * selected tab
                     *********************************************************/
                    @Override
                    public void stateChanged(ChangeEvent ce)
                    {
                        // Set the active handler to the one indicated by the
                        // currently selected tab
                        activeHandler = linkMgrs.get(tabbedPane.getSelectedIndex());

                        // Get the number of selected links
                        int numSelectedLinks = activeHandler.getLinkTree().getSelectionCount();

                        // Update the manager controls state
                        setLinkButtonsEnabled(numSelectedLinks == 1, numSelectedLinks != 0);
                    }
                });

                gbc.gridwidth = 2;
                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(tabbedPane, gbc);

                // Define the buttons for the lower panel:
                // New link button
                JButton btnNewLink = CcddButtonPanelHandler.createButton("New",
                                                                         INSERT_ICON,
                                                                         KeyEvent.VK_N,
                                                                         "Create a new link");

                // Add a listener for the New button
                btnNewLink.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Add a new link
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        createLink();
                    }
                });

                // Delete link button
                JButton btnDeleteLink = CcddButtonPanelHandler.createButton("Delete",
                                                                            DELETE_ICON,
                                                                            KeyEvent.VK_D,
                                                                            "Delete an existing link");

                // Add a listener for the Delete button
                btnDeleteLink.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Delete the selected link(s)
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        deleteLink();
                    }
                });

                // Rename link button
                btnRenameLink = CcddButtonPanelHandler.createButton("Rename",
                                                                    RENAME_ICON,
                                                                    KeyEvent.VK_D,
                                                                    "Rename an existing link");

                // Add a listener for the Rename button
                btnRenameLink.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Rename the selected link
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        renameLink();
                    }
                });

                // Copy link button
                btnCopyLink = CcddButtonPanelHandler.createButton("Copy",
                                                                  COPY_ICON,
                                                                  KeyEvent.VK_P,
                                                                  "Copy an existing link");

                // Add a listener for the Copy button
                btnCopyLink.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Copy the selected link
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        copyLink();
                    }
                });

                // Store links button
                JButton btnStoreLinks = CcddButtonPanelHandler.createButton("Store",
                                                                            STORE_ICON,
                                                                            KeyEvent.VK_S,
                                                                            "Store the link updates in the database");

                // Add a listener for the Store button
                btnStoreLinks.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Store the links in the database
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the links have changed since the last
                        // database commit and that the user confirms storing
                        // the links
                        if (isLinksChanged()
                            && new CcddDialogHandler().showMessageDialog(CcddLinkManagerDialog.this,
                                                                         "<html><b>Store links?",
                                                                         "Store Links",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Store the links in the project database
                            storeLinks();
                        }
                    }
                });

                // Close button
                JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                                       CLOSE_ICON,
                                                                       KeyEvent.VK_C,
                                                                       "Close the link manager");

                // Add a listener for the Close button
                btnClose.addActionListener(new ActionListener()
                {
                    /**********************************************************
                     * Close the link selection dialog
                     *********************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if there are no changes to the links or if the
                        // user elects to discard the changes
                        if (!isLinksChanged()
                            || new CcddDialogHandler().showMessageDialog(CcddLinkManagerDialog.this,
                                                                         "<html><b>Discard changes?",
                                                                         "Discard Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Close the dialog
                            closeDialog();
                        }
                    }
                });

                // Add buttons in the order in which they'll appear (left to
                // right, top to bottom)
                buttonPnl.add(btnNewLink);
                buttonPnl.add(btnRenameLink);
                buttonPnl.add(btnStoreLinks);
                buttonPnl.add(btnDeleteLink);
                buttonPnl.add(btnCopyLink);
                buttonPnl.add(btnClose);

                // Distribute the buttons across two rows
                setButtonRows(2);

                // Add the data stream link handlers
                addLinkHandlerPanes();
            }

            /******************************************************************
             * Variable link manager dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Display the link management dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  dialogPnl,
                                  buttonPnl,
                                  "Manage Links",
                                  true);
            }
        });
    }

    /**************************************************************************
     * Set the enable status of the buttons that apply only when one or more
     * links is selected
     * 
     * @param enableRename
     *            true to enable the Rename button, false to disable
     * 
     * @param enableCopy
     *            true to enable the Copy button, false to disable
     *************************************************************************/
    protected void setLinkButtonsEnabled(boolean enableRename,
                                         boolean enableCopy)
    {
        btnRenameLink.setEnabled(enableRename);
        btnCopyLink.setEnabled(enableCopy);
    }

    /**************************************************************************
     * Add a link handler for each data stream
     *************************************************************************/
    private void addLinkHandlerPanes()
    {
        // Create storage for the link manager handlers
        linkMgrs = new ArrayList<CcddLinkManagerHandler>();

        // Step through each stream
        for (RateInformation rateInfo : rateHandler.getRateInformation())
        {
            // Create a link manager for this stream
            CcddLinkManagerHandler linkMgr = new CcddLinkManagerHandler(ccddMain,
                                                                        this,
                                                                        rateInfo.getRateName(),
                                                                        rateHandler.getRatesInUse(rateInfo.getRateName(),
                                                                                                  CcddLinkManagerDialog.this));

            // Add the link manager to the list of managers
            linkMgrs.add(linkMgr);

            // Create a tab for each data stream
            tabbedPane.addTab(rateInfo.getStreamName(),
                              null,
                              linkMgr.getHandlerPanel(),
                              rateInfo.getRateName());
        }
    }

    /**************************************************************************
     * Add a new link to the link tree
     *************************************************************************/
    private void createLink()
    {
        // Create a panel for the link create components
        JPanel createPnl = new JPanel(new GridBagLayout());

        // Create the new link dialog label and field
        GridBagConstraints gbc = addLinkNameField("Enter new link name",
                                                  "",
                                                  createPnl);

        // Create the link description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionLbl.setForeground(Color.BLACK);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.weighty = 0.0;
        gbc.gridy++;
        createPnl.add(descriptionLbl, gbc);

        // Create the link description input field
        final JTextArea linkDescriptionFld = new JTextArea("", 3, 20);
        linkDescriptionFld.setFont(LABEL_FONT_PLAIN);
        linkDescriptionFld.setEditable(true);
        linkDescriptionFld.setLineWrap(true);
        linkDescriptionFld.setForeground(Color.BLACK);
        linkDescriptionFld.setBackground(Color.WHITE);
        linkDescriptionFld.setBorder(BorderFactory.createEmptyBorder());
        linkDescriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        linkDescriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        JScrollPane descScrollPane = new JScrollPane(linkDescriptionFld);
        descScrollPane.setBorder(border);

        // Add the description field to the dialog panel
        gbc.insets.top = 0;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;
        createPnl.add(descScrollPane, gbc);

        // Create a dialog for the new link information
        CcddDialogHandler linkInfoDialog = new CcddDialogHandler()
        {
            /******************************************************************
             * Verify that the dialog content is valid
             * 
             * @return true if the input values are valid
             *****************************************************************/
            @Override
            protected boolean verifySelection()
            {
                // Verify the link name is valid
                boolean isValid = verifyLinkName(false);

                // Check if the link name is valid
                if (isValid)
                {
                    // Get the name and remove leading & trailing white space
                    // characters
                    newLinkName = linkNameFld.getText();
                    newLinkDescription = linkDescriptionFld.getText().trim();
                }

                return isValid;
            }
        };

        // Display a dialog for the user to provide a link name
        if (linkInfoDialog.showOptionsDialog(CcddLinkManagerDialog.this,
                                             createPnl,
                                             "New Link",
                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Add the new link information
            activeHandler.getLinkTree().addLinkInformation(activeHandler.getRateName(),
                                                           newLinkName,
                                                           "0",
                                                           newLinkDescription);

            // Insert the new link into the link tree
            ToolTipTreeNode newNode = activeHandler.getLinkTree().addInformationNode(newLinkName,
                                                                                     newLinkDescription);

            // Update the link tree node name to show that it's empty
            activeHandler.getLinkTree().adjustNodeText(newNode);

            // Update the link dialog's change indicator
            updateChangeIndicator();
        }
    }

    /**************************************************************************
     * Remove the selected link(s) from the link tree
     *************************************************************************/
    private void deleteLink()
    {
        // Check that a node is selected in the link tree
        if (activeHandler.getLinkTree().getSelectionCount() != 0)
        {
            // Remove the selected link(s) information
            activeHandler.getLinkTree().removeSelectedLinks();

            // Update the variable tree to enable any variables no longer
            // assigned due to the deleted link
            activeHandler.getVariableTree().setExcludedVariables(activeHandler.getLinkTree().getLinkVariables(null));

            // Update the link dialog's change indicator
            updateChangeIndicator();
        }
    }

    /**************************************************************************
     * Rename the selected link
     *************************************************************************/
    private void renameLink()
    {
        // Get the selected link(s)
        String[] selected = activeHandler.getLinkTree().getTopLevelSelectedNodeNames();

        // Check that a single node is selected in the link tree
        if (selected.length == 1)
        {
            // Remove any HTML tags and parenthetical text from the selected
            // link name
            String nameOnly = activeHandler.getLinkTree().removeExtraText(selected[0]);

            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            // Create the link renaming dialog label and field
            addLinkNameField("Rename '"
                             + nameOnly
                             + "' to:",
                             nameOnly,
                             dialogPnl);

            // Create the link renaming dialog
            CcddDialogHandler linkDlg = new CcddDialogHandler()
            {
                /**************************************************************
                 * Verify that the dialog content is valid
                 * 
                 * @return true if the input values are valid
                 *************************************************************/
                @Override
                protected boolean verifySelection()
                {
                    return verifyLinkName(false);
                }
            };

            // Display the link renaming dialog
            if (linkDlg.showOptionsDialog(CcddLinkManagerDialog.this,
                                          dialogPnl,
                                          "Rename Link",
                                          DialogOption.RENAME_OPTION,
                                          true) == OK_BUTTON)
            {
                // Rename the link
                ToolTipTreeNode renamedNode = activeHandler.getLinkTree().renameNode(selected[0],
                                                                                     linkNameFld.getText());
                activeHandler.getLinkTree().adjustNodeText(renamedNode);
                activeHandler.getLinkTree().getLinkInformation(selected[0]).setName(linkNameFld.getText());

                // Update the link dialog's change indicator
                updateChangeIndicator();
            }
        }
    }

    /**************************************************************************
     * Copy the selected link(s) to one or more data streams. If a target
     * stream already has a link by the same name, or does not support the rate
     * of the copied link, then the link is not copied to that stream.
     * Additionally, if a variable's rate isn't the same in the target stream
     * then the variable is removed from the link
     *************************************************************************/
    private void copyLink()
    {
        // Check if there is more that one data stream
        if (rateHandler.getNumRateColumns() != 1)
        {
            List<Integer> disabledItems = new ArrayList<Integer>();
            String[][] arrayItemData = new String[rateHandler.getNumRateColumns()][2];

            // Get a reference to the current link tree in order to shorten
            // subsequent calls
            CcddLinkTreeHandler currentTree = activeHandler.getLinkTree();

            // Get the selected link(s)
            String[] selected = currentTree.getTopLevelSelectedNodeNames();

            // Create a panel to contain the dialog components
            JPanel streamPnl = new JPanel(new GridBagLayout());

            int rateIndex = 0;

            // Step through each data stream
            for (RateInformation rateInfo : rateHandler.getRateInformation())
            {
                // Add the associated data stream name to the array
                arrayItemData[rateIndex][0] = rateInfo.getStreamName();
                rateIndex++;
            }

            // Add the index of the current data stream to the list of disabled
            // selections
            disabledItems.add(tabbedPane.getSelectedIndex());

            // Get the name(s) of the link(s) to be copied, minus any HTML tags
            // and rate/size information
            String linkNames = currentTree.removeExtraText(Arrays.toString(selected).replaceAll("^\\[|\\]$", ""));

            // Create a panel containing a grid of check boxes representing the
            // data streams from which to choose
            if (addCheckBoxes(null,
                              arrayItemData,
                              disabledItems,
                              "Select target data stream(s)",
                              streamPnl))
            {
                // Create a panel to contain the dialog components
                JPanel dialogPnl = new JPanel(new GridBagLayout());

                // Create the link copying dialog label and field
                GridBagConstraints gbc = addLinkNameField("Link(s) to copy:",
                                                          linkNames,
                                                          dialogPnl);
                linkNameFld.setEditable(false);

                // Add the data stream selection panel to the dialog
                gbc.insets.left = 0;
                gbc.insets.right = 0;
                gbc.gridy++;
                dialogPnl.add(streamPnl, gbc);

                // Create the link copying dialog
                CcddDialogHandler linkDlg = new CcddDialogHandler()
                {
                    /**********************************************************
                     * Verify that the dialog content is valid
                     * 
                     * @return true if the input values are valid
                     *********************************************************/
                    @Override
                    protected boolean verifySelection()
                    {
                        return verifyLinkName(true);
                    }
                };

                selectedStreams = new ArrayList<String>();

                // Display the link copying dialog
                if (linkDlg.showOptionsDialog(CcddLinkManagerDialog.this,
                                              dialogPnl,
                                              "Copy Link(s)",
                                              DialogOption.COPY_OPTION,
                                              true) == OK_BUTTON)
                {
                    notCopiedList = new ArrayList<Object[]>();

                    // Get the node(s) that represent the links (skipping the
                    // link members)
                    ToolTipTreeNode[] selectedLinks = currentTree.getTopLevelSelectedNodes();

                    // Index of the next selected link node to copy
                    int selectionIndex = 0;

                    // Step through each link to be copied
                    for (ToolTipTreeNode copyLink : selectedLinks)
                    {
                        // Remove any HTML tags and parenthetical text from the
                        // selected link name
                        String nameOnly = activeHandler.getLinkTree().removeExtraText(copyLink.getUserObject().toString());

                        // Step through each selected data stream name
                        for (int index = 0; index < arrayItemData.length; index++)
                        {
                            // Check if this data stream is selected as a
                            // target
                            if (selectedStreams.contains(arrayItemData[index][0]))
                            {
                                // Get the reference to this stream's link
                                // manager handler to shorten subsequent calls
                                CcddLinkManagerHandler linkMgr = linkMgrs.get(index);

                                // Get a reference to the target's link tree to
                                // shorten subsequent calls
                                CcddLinkTreeHandler targetTree = linkMgr.getLinkTree();

                                // Check if the link with this name doesn't
                                // already exist in the target data stream
                                if (targetTree.getLinkInformation(nameOnly) == null)
                                {
                                    // Get the link information for the link
                                    // being copied
                                    LinkInformation linkInfo = currentTree.getLinkInformation(nameOnly);

                                    // Get the rate information for the copied
                                    // link's data stream
                                    RateInformation rateInfo = rateHandler.getRateInformationByStreamName(arrayItemData[index][0]);

                                    // Check if the target data stream supports
                                    // the copied link's sample rate
                                    if (linkInfo.getSampleRate().equals("0")
                                        || Arrays.asList(rateInfo.getSampleRates()).contains(linkInfo.getSampleRate()))
                                    {
                                        List<ToolTipTreeNode> removedNodes = new ArrayList<ToolTipTreeNode>();

                                        // Create a node for the new link
                                        ToolTipTreeNode newLinkNode = new ToolTipTreeNode(nameOnly,
                                                                                          linkInfo.getDescription());
                                        // Copy the top-level nodes from the
                                        // copied link to the new link
                                        targetTree.copySubTree(selectedLinks[selectionIndex], newLinkNode);

                                        // Update the target stream's variable
                                        // tree to the copied link's rate. The
                                        // variable tree is used to validate
                                        // the variables in the new link
                                        linkMgr.setRateFilter(linkInfo.getSampleRate());

                                        // Step through each member of the new
                                        // link
                                        for (Enumeration<?> element = newLinkNode.preorderEnumeration(); element.hasMoreElements();)
                                        {
                                            // Get the node for this variable
                                            // and convert it to a string,
                                            // removing the link name
                                            ToolTipTreeNode subNode = (ToolTipTreeNode) element.nextElement();
                                            String varPath = targetTree.convertLeafNodePath(subNode.getPath(), 1);

                                            // Check if the variable path isn't
                                            // blank, the path refers to a
                                            // variable (and not a structure),
                                            // and if the variable path isn't
                                            // present in the target stream's
                                            // variable tree (i.e., the
                                            // variable's rate differs between
                                            // the current stream and the
                                            // target stream or the structure
                                            // containing the variable doesn't
                                            // have the rate column associated
                                            // with the stream)
                                            if (!varPath.isEmpty()
                                                && varPath.contains(".")
                                                && !linkMgr.getVariableTree().isNodeInTree(varPath))
                                            {
                                                // Add this node to the list of
                                                // those to be removed from the
                                                // new link
                                                removedNodes.add(subNode);

                                                // Add the invalid link and
                                                // data stream to the list
                                                notCopiedList.add(new Object[] {nameOnly,
                                                                                subNode.getUserObject(),
                                                                                arrayItemData[index][0],
                                                                                "Sample rate differs or variable unavailable in target"});
                                            }
                                        }

                                        // Step through the list of variable
                                        // nodes to be removed
                                        for (ToolTipTreeNode removeNode : removedNodes)
                                        {
                                            // Remove the variable node from
                                            // the tree, and its ancestor nodes
                                            // (up to the link level) if these
                                            // have no siblings
                                            targetTree.removeNodeAndEmptyAncestors(removeNode);
                                        }

                                        // Insert the new link into the link
                                        // tree
                                        ToolTipTreeNode targetNode = targetTree.addInformationNode(nameOnly,
                                                                                                   linkInfo.getDescription());

                                        // Copy the link members from the link
                                        // being copied to the copy in the
                                        // target data stream
                                        targetTree.copySubTree(newLinkNode, targetNode);

                                        // Create the new link in the target
                                        // data stream
                                        targetTree.addLinkInformation(rateInfo.getRateName(),
                                                                      linkInfo.getName(),
                                                                      linkInfo.getSampleRate(),
                                                                      linkInfo.getDescription());

                                        // Update the link tree node names in
                                        // the target stream (e.g., add the
                                        // rate/size information)
                                        targetTree.adjustNodeText(targetNode);

                                        // Update the target stream's variable
                                        // tree to gray out any variables now
                                        // assigned due to the new link
                                        linkMgr.getVariableTree().setExcludedVariables(targetTree.getLinkVariables(null));

                                        // Update the link dialog's change
                                        // indicator
                                        updateChangeIndicator(index);
                                    }
                                    // The stream does not support the rate of
                                    // the copied link
                                    else
                                    {
                                        // Add the invalid link and data stream
                                        // to the list
                                        notCopiedList.add(new Object[] {nameOnly,
                                                                        "",
                                                                        arrayItemData[index][0],
                                                                        "Sample rate unsupported in target"});
                                    }
                                }
                                // The data stream already contains a link with
                                // this name
                                else
                                {
                                    // Add the invalid link and data stream to
                                    notCopiedList.add(new Object[] {nameOnly,
                                                                    "",
                                                                    arrayItemData[index][0],
                                                                    "Link name already exists in target"});
                                }
                            }
                        }

                        selectionIndex++;
                    }

                    // Check if any link or link member failed to copy
                    if (!notCopiedList.isEmpty())
                    {
                        // Create a panel for the link copy failure dialog
                        JPanel notCopyPnl = new JPanel(new GridBagLayout());

                        // Create the list label and add it to the dialog
                        JLabel notCopyLbl = new JLabel("Following link(s) and/or link "
                                                       + "member(s) were not copied:");
                        notCopyLbl.setFont(LABEL_FONT_BOLD);
                        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
                        gbc.insets.right = LABEL_HORIZONTAL_SPACING / 2;
                        gbc.gridy = 0;
                        notCopyPnl.add(notCopyLbl, gbc);

                        // Create the table to display the links & members not
                        // copied
                        CcddJTableHandler notCopiedTable = new CcddJTableHandler()
                        {
                            /**************************************************
                             * Allow multiple line display in the specified
                             * columns
                             *************************************************/
                            @Override
                            protected boolean isColumnMultiLine(int column)
                            {
                                return column == LinkCopyErrorColumnInfo.MEMBER.ordinal()
                                       || column == LinkCopyErrorColumnInfo.CAUSE.ordinal();
                            }

                            /**************************************************
                             * Load the link & members not copied data into the
                             * table and format the table cells
                             *************************************************/
                            @Override
                            protected void loadAndFormatData()
                            {
                                // Place the data into the table model along
                                // with the column names, set up the editors
                                // and renderers for the table cells, set up
                                // the table grid lines, and calculate the
                                // minimum width required to display the table
                                // information
                                setUpdatableCharacteristics(notCopiedList.toArray(new Object[0][0]),
                                                            LinkCopyErrorColumnInfo.getColumnNames(),
                                                            null,
                                                            null,
                                                            null,
                                                            LinkCopyErrorColumnInfo.getToolTips(),
                                                            true,
                                                            true,
                                                            true,
                                                            true);
                            }

                            /**************************************************
                             * Override the table layout so that extra width is
                             * apportioned unequally between the columns when
                             * the table is resized
                             *************************************************/
                            @Override
                            public void doLayout()
                            {
                                // Get a reference to the column being resized
                                if (getTableHeader() != null
                                    && getTableHeader().getResizingColumn() == null)
                                {
                                    // Get a reference to the event table's
                                    // column model to shorten subsequent calls
                                    TableColumnModel tcm = getColumnModel();

                                    // Calculate the change in the search
                                    // dialog's width
                                    int delta = getParent().getWidth() - tcm.getTotalColumnWidth();

                                    // Get the reference to the link copy error
                                    // table columns
                                    TableColumn linkCol = tcm.getColumn(LinkCopyErrorColumnInfo.LINK.ordinal());
                                    TableColumn memCol = tcm.getColumn(LinkCopyErrorColumnInfo.MEMBER.ordinal());
                                    TableColumn strmCol = tcm.getColumn(LinkCopyErrorColumnInfo.STREAM.ordinal());
                                    TableColumn errCol = tcm.getColumn(LinkCopyErrorColumnInfo.CAUSE.ordinal());

                                    // Set the columns' widths to its current
                                    // width plus a percentage of the the extra
                                    // width added to the dialog due to the
                                    // resize
                                    linkCol.setPreferredWidth(linkCol.getPreferredWidth()
                                                              + (int) (delta * 0.125));
                                    linkCol.setWidth(linkCol.getPreferredWidth());
                                    memCol.setPreferredWidth(memCol.getPreferredWidth()
                                                             + (int) (delta * 0.375));
                                    memCol.setWidth(memCol.getPreferredWidth());
                                    strmCol.setPreferredWidth(strmCol.getPreferredWidth()
                                                              + (int) (delta * 0.125));
                                    strmCol.setWidth(strmCol.getPreferredWidth());
                                    errCol.setPreferredWidth(errCol.getPreferredWidth()
                                                             + delta
                                                             - (int) (delta * 0.125) * 2
                                                             - (int) (delta * 0.375));
                                    errCol.setWidth(errCol.getPreferredWidth());
                                }
                                // Table header or resize column not available
                                else
                                {
                                    super.doLayout();
                                }
                            }
                        };

                        // Place the table into a scroll pane
                        JScrollPane scrollPane = new JScrollPane(notCopiedTable);

                        // Set up the link copy error table parameters
                        notCopiedTable.setFixedCharacteristics(scrollPane,
                                                               false,
                                                               ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                                               TableSelectionMode.SELECT_BY_CELL,
                                                               true,
                                                               TABLE_BACK_COLOR,
                                                               false,
                                                               true,
                                                               LABEL_FONT_PLAIN,
                                                               true);

                        // Define the panel to contain the table
                        JPanel resultsTblPnl = new JPanel();
                        resultsTblPnl.setLayout(new BoxLayout(resultsTblPnl, BoxLayout.X_AXIS));
                        resultsTblPnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                        resultsTblPnl.add(scrollPane);

                        // Add the table to the dialog
                        gbc.gridwidth = GridBagConstraints.REMAINDER;
                        gbc.fill = GridBagConstraints.BOTH;
                        gbc.weighty = 1.0;
                        gbc.gridx = 0;
                        gbc.gridy++;
                        notCopyPnl.add(resultsTblPnl, gbc);

                        // Inform the user that the link(s) can't be copied for
                        // the reason provided
                        new CcddDialogHandler().showOptionsDialog(CcddLinkManagerDialog.this,
                                                                  notCopyPnl,
                                                                  "Link Copy Failure",
                                                                  DialogOption.PRINT_OPTION,
                                                                  true);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Store the link definitions in the project database
     *************************************************************************/
    private void storeLinks()
    {
        // Current link definitions
        List<String[]> currentLinks = new ArrayList<String[]>();

        // Step through each data stream
        for (CcddLinkManagerHandler linkHandler : linkMgrs)
        {
            // Add the links for this stream to the list
            // containing the links for all data streams
            currentLinks.addAll(linkHandler.getCurrentLinks());
        }

        // Store the link list into the database
        dbTable.storeInformationTableInBackground(InternalTable.LINKS,
                                                  currentLinks,
                                                  null,
                                                  CcddLinkManagerDialog.this);
    }

    /**************************************************************************
     * Add a link name field to the dialog
     * 
     * @param fieldText
     *            text to display beside the input field
     * 
     * @param currentName
     *            name of the selected link
     * 
     * @param dialogPnl
     *            panel to which to add the input field
     * 
     * @return The GridBagConstraints used to arrange the dialog
     *************************************************************************/
    private GridBagConstraints addLinkNameField(String fieldText,
                                                String currentName,
                                                JPanel dialogPnl)
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create the link name label and add it to the dialog panel
        JLabel label = new JLabel(fieldText);
        label.setFont(LABEL_FONT_BOLD);
        dialogPnl.add(label, gbc);

        // Create the link name field and add it to the dialog panel
        linkNameFld = new JTextField(currentName, 20);
        linkNameFld.setFont(LABEL_FONT_PLAIN);
        linkNameFld.setEditable(true);
        linkNameFld.setForeground(Color.BLACK);
        linkNameFld.setBackground(Color.WHITE);
        linkNameFld.setBorder(border);
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        dialogPnl.add(linkNameFld, gbc);

        return gbc;
    }

    /**************************************************************************
     * Verify that the contents of the link name field is valid. For a link
     * copy operation also verify that a data stream is selected
     * 
     * @param isCopy
     *            true to if this verification is for a link copy operation
     * 
     * @return true if the link name is valid, and, for a copy operation, that
     *         a data stream is selected; false otherwise
     *************************************************************************/
    private boolean verifyLinkName(boolean isCopy)
    {
        boolean isValid = true;

        try
        {
            // Check if this is not a copy operation
            if (!isCopy)
            {
                // Get the link name, remove leading & trailing white space
                // characters, and store the 'cleaned' name back in the field
                String linkName = linkNameFld.getText().trim();
                linkNameFld.setText(linkName);

                // Check if the link name is blank
                if (linkName.isEmpty())
                {
                    // Inform the user that the link name is invalid
                    throw new CCDDException("Link name must be entered");
                }

                // Check if link name already is in use; skip this when copying
                // a link
                if (activeHandler.getLinkTree().getRowCount() != 0)
                {
                    // Get the updated link definitions
                    List<String[]> currentLinks = activeHandler.getLinkTree().createDefinitionsFromTree();

                    // Step through the currently defined links
                    for (String[] link : currentLinks)
                    {
                        // Check if the new name matches an existing one
                        if (linkName.equals(link[1]))
                        {
                            throw new CCDDException("Link name is already in use");
                        }
                    }
                }
            }
            // This is a copy operation
            else
            {
                // Create a list of the selected data streams
                selectedStreams = Arrays.asList(getCheckBoxSelected());

                // Check if no data stream is selected during a copy operation
                if (selectedStreams.isEmpty())
                {
                    throw new CCDDException("At least one data stream(s) must be selected");
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddLinkManagerDialog.this,
                                                      "<html><b>"
                                                          + ce.getMessage(),
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }

    /**************************************************************************
     * Check if the links for any stream differ from those last committed to
     * the database
     * 
     * @return true if any of the streams' link definitions have changed
     *************************************************************************/
    private boolean isLinksChanged()
    {
        boolean isChanged = false;

        // Step through each stream
        for (CcddLinkManagerHandler linkHandler : linkMgrs)
        {
            // Check if the links changed for this stream
            if (linkHandler.isLinksChanged())
            {
                // Set the flag indicating a change exists, but keep searching
                // so that the current links are updated for all data streams
                isChanged = true;
            }
        }

        return isChanged;
    }

    /**************************************************************************
     * Update the change indicator for the active link manager
     *************************************************************************/
    protected void updateChangeIndicator()
    {
        updateChangeIndicator(-1);
    }

    /**************************************************************************
     * Update the change indicator for the specified link manager
     * 
     * @param index
     *            index for the tab containing the data stream to update; an
     *            invalid tab number causes the active tab to be selected
     *************************************************************************/
    private void updateChangeIndicator(int index)
    {
        // Check if the specified index is invalid
        if (index < 0 || index >= tabbedPane.getTabCount())
        {
            // Get the index of the currently displayed tab
            index = tabbedPane.getSelectedIndex();
        }

        // Check that the tab index is valid
        if (index != -1)
        {
            // Replace the tab name, appending the change indicator if changes
            // exist
            tabbedPane.setTitleAt(index,
                                  tabbedPane.getTitleAt(index).replaceAll("\\*", "")
                                      + (linkMgrs.get(index).isLinksChanged()
                                                                             ? "*"
                                                                             : ""));
        }
    }
}
