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
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.LinkInformation;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;

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
        }
    }

    /**************************************************************************
     * Create the variable link manager dialog
     *************************************************************************/
    private void initialize()
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

        // Create panels to hold the components of the dialog
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create a tabbed pane to contain the rate parameters that are
        // stream-specific
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(LABEL_FONT_BOLD);

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener()
        {
            /******************************************************************
             * Update the handler to the one associated with the selected tab
             *****************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                // Set the active handler to the one indicated by the currently
                // selected tab
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

        // Create the button panel
        JPanel buttonPanel = new JPanel();

        // Define the buttons for the lower panel:
        // New link button
        JButton btnNewLink = CcddButtonPanelHandler.createButton("New",
                                                                 INSERT_ICON,
                                                                 KeyEvent.VK_N,
                                                                 "Create a new link");

        // Add a listener for the New button
        btnNewLink.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Add a new link
             *****************************************************************/
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
            /******************************************************************
             * Delete the selected link(s)
             *****************************************************************/
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
            /******************************************************************
             * Rename the selected link
             *****************************************************************/
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
            /******************************************************************
             * Copy the selected link
             *****************************************************************/
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
            /******************************************************************
             * Store the links in the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the links have changed since the last database
                // commit and that the user confirms storing the links
                if (isLinksChanged()
                    && new CcddDialogHandler().showMessageDialog(CcddLinkManagerDialog.this,
                                                                 "<html><b>Store links?",
                                                                 "Store Links",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Current link definitions
                    List<String[]> currentLinks = new ArrayList<String[]>();

                    // Step through each data stream
                    for (CcddLinkManagerHandler linkHandler : linkMgrs)
                    {
                        // Add the links for this stream to the list containing
                        // the links for all data streams
                        currentLinks.addAll(linkHandler.getCurrentLinks());
                    }

                    // Store the link list into the database
                    dbTable.storeInformationTable(InternalTable.LINKS,
                                                  currentLinks,
                                                  null,
                                                  CcddLinkManagerDialog.this);
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
            /******************************************************************
             * Close the link selection dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if there are no changes to the links or if the user
                // elects to discard the changes
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

        // Add buttons in the order in which they'll appear (left to right, top
        // to bottom)
        buttonPanel.add(btnNewLink);
        buttonPanel.add(btnRenameLink);
        buttonPanel.add(btnStoreLinks);
        buttonPanel.add(btnDeleteLink);
        buttonPanel.add(btnCopyLink);
        buttonPanel.add(btnClose);

        // Distribute the buttons across two rows
        setButtonRows(2);

        // Add the data stream link handlers
        addLinkHandlerPanes();

        // Display the link management dialog
        showOptionsDialog(ccddMain.getMainFrame(),
                          dialogPnl,
                          buttonPanel,
                          "Manage Links",
                          true);
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
        // Get the rate information for all data streams
        List<RateInformation> rateInfo = rateHandler.getRateInformation();

        // Create storage for the link manager handlers
        linkMgrs = new ArrayList<CcddLinkManagerHandler>();

        // Step through each stream
        for (int index = 0; index < rateInfo.size(); index++)
        {
            // Create a link manager for this stream
            CcddLinkManagerHandler linkMgr = new CcddLinkManagerHandler(ccddMain,
                                                                        this,
                                                                        rateInfo.get(index).getRateName(),
                                                                        rateInfo.get(index).getSampleRates());

            // Add the link manager to the list of managers
            linkMgrs.add(linkMgr);

            // Create a tab for each table type
            tabbedPane.addTab(rateInfo.get(index).getStreamName(),
                              null,
                              linkMgr.getHandlerPanel(),
                              rateInfo.get(index).getRateName());
        }

        // Check if only a single link manager was added
        if (rateInfo.size() == 1)
        {
            // Select the tab for the newly added type
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
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
                                                           newLinkDescription);

            // Insert the new link into the link tree
            ToolTipTreeNode newNode = activeHandler.getLinkTree().addInformationNode(newLinkName,
                                                                                     newLinkDescription);

            // Update the link tree node name to show that it's empty
            activeHandler.getLinkTree().adjustNodeText(newNode);
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
            activeHandler.getLinkTree().removeLinkInformation();

            // Remove the selected link(s) from the link tree
            activeHandler.getLinkTree().removeSelectedTopLevelNodes();
        }
    }

    /**************************************************************************
     * Rename the selected link
     *************************************************************************/
    private void renameLink()
    {
        // Get the selected link(s)
        String[] selected = activeHandler.getLinkTree().getSelectedNode();

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
            String[] selectedLink = currentTree.getSelectedNode();

            // Create a panel to contain the dialog components
            JPanel streamPnl = new JPanel(new GridBagLayout());

            // Step through each rate column name
            for (int index = 0; index < rateHandler.getNumRateColumns(); index++)
            {
                // Add the associated data stream name to the array
                arrayItemData[index][0] = rateHandler.getDataStreamNames()[index];
            }

            // Add the index of the current data stream to the list of disabled
            // selections
            disabledItems.add(tabbedPane.getSelectedIndex());

            // Get the name(s) of the link(s) to be copied, minus any HTML tags
            // and rate/size information
            String linkNames = currentTree.removeExtraText(Arrays.toString(selectedLink).replaceAll("^\\[|\\]$", ""));

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
                    DefaultListModel<String> notCopyModel = new DefaultListModel<String>();

                    // Step through each link to be copied
                    for (String copyLink : selectedLink)
                    {
                        // Remove any HTML tags and parenthetical text from the
                        // selected link name
                        String nameOnly = activeHandler.getLinkTree().removeExtraText(copyLink);

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

                                        // Create a node for the new link and
                                        // copy the child nodes from the copied
                                        // link to the new link
                                        ToolTipTreeNode newLinkNode = new ToolTipTreeNode(nameOnly,
                                                                                          linkInfo.getDescription());
                                        targetTree.copySubTree((ToolTipTreeNode) currentTree.getSelectionPath().getLastPathComponent(),
                                                               newLinkNode);

                                        // Update the target stream's variable
                                        // tree to the copied link's rate. The
                                        // variable tree is used to validate
                                        // the variables in the new link
                                        linkMgr.setRateFilter(linkInfo.getSampleRate());

                                        // Step through each member of the new
                                        // link
                                        for (Enumeration<?> element = newLinkNode.depthFirstEnumeration(); element.hasMoreElements();)
                                        {
                                            // Get the node for this variable
                                            // and convert it to a string,
                                            // removing the link name
                                            ToolTipTreeNode subNode = (ToolTipTreeNode) element.nextElement();
                                            String varPath = targetTree.convertLeafNodePath(subNode.getPath(), 1);

                                            // Check that the variable path
                                            // isn't blank and that the
                                            // variable path isn't present in
                                            // the target stream's variable
                                            // tree (i.e., the variable's rate
                                            // differs between the current
                                            // stream and the target stream)
                                            if (!varPath.isEmpty()
                                                && !linkMgr.getVariableTree().isNodeInTree(varPath))
                                            {
                                                // Add this node to the list of
                                                // those to be removed from the
                                                // new link
                                                removedNodes.add(subNode);
                                            }
                                        }

                                        // Step through the list of variable
                                        // nodes to be removed
                                        for (ToolTipTreeNode removeNode : removedNodes)
                                        {
                                            // Remove the variable node from
                                            // the new link
                                            removeNode.removeFromParent();
                                        }

                                        // Create the new link in the target
                                        // data stream
                                        targetTree.addLinkInformation(rateInfo.getRateName(),
                                                                      linkInfo.getName(),
                                                                      linkInfo.getDescription());

                                        // Insert the new link into the link
                                        // tree
                                        ToolTipTreeNode targetNode = targetTree.addInformationNode(nameOnly,
                                                                                                   linkInfo.getDescription());

                                        // Copy the variable members from the
                                        // link being copied to the copy in the
                                        // target data stream
                                        targetTree.copySubTree(newLinkNode, targetNode);

                                        // Update the link tree node names in
                                        // the target stream (e.g., add the
                                        // rate/size information)
                                        targetTree.adjustNodeText(targetNode);
                                    }
                                    // The stream does not support the rate of
                                    // the copied link
                                    else
                                    {
                                        // Add the link and data stream to the
                                        // list
                                        notCopyModel.addElement("<html><b>"
                                                                + nameOnly
                                                                + "</b> to stream <b>"
                                                                + arrayItemData[index][0]
                                                                + "</b>; rate unsupported");
                                    }
                                }
                                // The data stream already contains a link with
                                // this name
                                else
                                {
                                    // Add the link and data stream
                                    notCopyModel.addElement("<html><b>"
                                                            + nameOnly
                                                            + "</b> to stream <b>"
                                                            + arrayItemData[index][0]
                                                            + "</b>; name already exists");
                                }
                            }
                        }
                    }

                    // Check if any link failed to copy
                    if (!notCopyModel.isEmpty())
                    {
                        // Create a panel for the link copy failure dialog
                        JPanel notCopyPnl = new JPanel(new GridBagLayout());

                        // Create the list label and add it to the dialog
                        JLabel notCopyLbl = new JLabel("Following link(s) not copied:");
                        notCopyLbl.setFont(LABEL_FONT_BOLD);
                        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
                        gbc.insets.right = LABEL_HORIZONTAL_SPACING / 2;
                        gbc.gridy = 0;
                        notCopyPnl.add(notCopyLbl, gbc);

                        // Add the list to a scroll pane that is placed below
                        // the label
                        JList<String> notCopyList = new JList<String>(notCopyModel);
                        notCopyList.setFont(LABEL_FONT_PLAIN);
                        notCopyList.setVisibleRowCount(Math.min(notCopyModel.size(), 10));
                        JScrollPane notCopyScroll = new JScrollPane(notCopyList);
                        notCopyScroll.setBorder(border);
                        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
                        gbc.gridy++;
                        notCopyPnl.add(notCopyScroll, gbc);

                        // Inform the user that the link(s) can't be copied for
                        // the reason provided
                        new CcddDialogHandler().showOptionsDialog(CcddLinkManagerDialog.this,
                                                                  notCopyPnl,
                                                                  "Link Copy Failure",
                                                                  DialogOption.OK_OPTION,
                                                                  true);
                    }
                }
            }
        }
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
                // are detected
                isChanged = true;
            }
        }

        return isChanged;
    }
}
