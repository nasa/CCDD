/**
 * CFS Command & Data Dictionary group manager dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CLEAR_ICON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.COPY_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.FIELD_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.LAF_CHECK_BOX_HEIGHT;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.RENAME_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;
import static CCDD.CcddConstants.STORE_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.CustomSplitPane;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.GroupInformation;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary group manager dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddGroupManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddFieldTableEditorDialog fldTblEditor;
    private CcddTableTreeHandler tableTree;
    private CcddGroupTreeHandler groupTree;
    private FieldPanel fieldPnl;
    private CcddUndoManager undoManager;

    // Component referenced by multiple methods
    private Border border;
    private Border emptyBorder;
    private JTextField groupNameFld;
    private JButton btnRenameGroup;
    private JButton btnCopyGroup;
    private JButton btnManageFields;
    private JButton btnClearValues;
    private JCheckBox applicationCb;

    // Group information and group fields stored in the database
    private List<String[]> committedGroups;
    private List<GroupInformation> committedGroupFields;

    // Current group field definitions
    private List<String[]> currentGroups;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Currently selected group in the group tree; null if none, or more than
    // one group is selected
    private GroupInformation selectedGroup;

    // List containing the data field list for groups with data field updates
    private List<List<FieldInformation>> updateFields;

    // List containing the names of any groups that are deleted
    private List<String> deletedGroups;

    // Dialog title
    private static final String DIALOG_TITLE = "Manage Groups";

    /**************************************************************************
     * Description and data field panel handler class
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    private class FieldPanel extends CcddEditorPanelHandler
    {
        /**********************************************************************
         * Return the application editor dialog reference
         *********************************************************************/
        @Override
        protected Component getTableEditor()
        {
            return CcddGroupManagerDialog.this;
        }

        /**********************************************************************
         * Update the group manager change indicator
         *********************************************************************/
        @Override
        protected void updateOwnerChangeIndicator()
        {
            updateChangeIndicator();
        }
    }

    /**************************************************************************
     * Group manager dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddGroupManagerDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create a reference to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        fldTblEditor = ccddMain.getFieldTableEditor();

        // Create the group selection dialog
        initialize();
    }

    /**************************************************************************
     * Perform the steps needed following execution of group updates to the
     * database
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doGroupUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Store the groups in the committed groups list
            copyGroupInformation();

            // Remove any group names from the deleted groups list
            deletedGroups.clear();

            // Check if the data field editor table dialog is open
            if (fldTblEditor != null && fldTblEditor.isShowing())
            {
                // Update the data field editor table
                fldTblEditor.reloadDataFieldTable();
            }

            // Remove the group dialog's change indicator
            setTitle(DIALOG_TITLE);
        }

        // Discard any store edits
        undoManager.discardAllEdits();
    }

    /**************************************************************************
     * Store a copy of the group information
     *************************************************************************/
    private void copyGroupInformation()
    {
        // Store the group definitions
        committedGroups = groupTree.createDefinitionsFromTree();

        committedGroupFields = new ArrayList<GroupInformation>();

        // Step through each group
        for (GroupInformation groupInfo : groupTree.getGroupHandler().getGroupInformation())
        {
            // Create a copy of the group information and store it in the
            // committed group list
            GroupInformation newInfo = new GroupInformation(groupInfo.getName(),
                                                            null,
                                                            null,
                                                            null,
                                                            CcddFieldHandler.getFieldInformationCopy(groupInfo.getFieldInformation()));
            committedGroupFields.add(newInfo);
        }
    }

    /**************************************************************************
     * Create the group manager dialog
     *************************************************************************/
    private void initialize()
    {
        // Create borders for the dialog components
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(2, 2, 2, 2));
        emptyBorder = BorderFactory.createEmptyBorder();

        selectedGroup = null;
        deletedGroups = new ArrayList<String>();

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Build the group tree
        groupTree = new CcddGroupTreeHandler(ccddMain, ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a node in the group tree.
             * This replaces the placeholder method in CcddTableTreeHandler
             *****************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Needed for the group manager dialog's size to be adjusted
                // for the data fields
                CcddGroupManagerDialog.this.setPreferredSize(null);

                // Store any changes to the currently selected group, if
                // applicable
                updateSelectedGroupInformation();

                // Get the name of the selected group(s)
                String[] selected = getSelectedNode();

                // If a single group is selected then set the selected group,
                // enable and populate the description field, and display the
                // group's data fields; otherwise clear the selected group,
                // disable and clear the description field, and remove any data
                // fields
                setGroupAndFields(selected.length == 1
                                                      ? selected[0]
                                                      : null);

                // Validate the dialog to redraw the description and field area
                // correctly
                CcddGroupManagerDialog.this.validate();

                // Needed so that any dialogs spawned from this one are
                // positioned relative to the group manager dialog
                CcddGroupManagerDialog.this.setPreferredSize(CcddGroupManagerDialog.this.getPreferredSize());
            }
        };

        // Store the initial group information
        copyGroupInformation();

        // Create panels to hold the components of the dialog
        JPanel managerPnl = new JPanel(new GridBagLayout());
        JPanel titlePnl = new JPanel(new GridBagLayout());
        JPanel treePnl = new JPanel(new GridBagLayout());
        managerPnl.setBorder(emptyBorder);
        titlePnl.setBorder(emptyBorder);
        treePnl.setBorder(emptyBorder);

        // Create the group manager dialog labels and fields
        JLabel dlgLabel = new JLabel("Assign tables to groups");
        dlgLabel.setFont(LABEL_FONT_BOLD);
        titlePnl.add(dlgLabel, gbc);

        // Add the upper panel components to the dialog panel
        managerPnl.add(titlePnl, gbc);

        // Build the table tree showing both table prototypes and table
        // instances; i.e., parent tables with their child tables (i.e.,
        // parents with children)
        tableTree = new CcddTableTreeHandler(ccddMain,
                                             null,
                                             TableTreeType.PROTOTYPE_AND_INSTANCE,
                                             false,
                                             true,
                                             ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a node in the table tree
             *****************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent variable tree updates
                    isNodeSelectionChanging = true;

                    // Deselect any nodes that don't represent a table or the
                    // level immediately above the table level
                    clearNonTableNodes(1);

                    // Reset the flag to allow variable tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Create a table tree panel and add it to another panel (in order to
        // control spacing)
        gbc.insets.top = 0;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.weighty = 1.0;
        treePnl.add(tableTree.createTreePanel("Tables",
                                              TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                              ccddMain.getMainFrame()),
                    gbc);
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.bottom = 0;

        // Create a split pane containing the table tree in the left pane and
        // the group tree in the right pane and add it to the panel. The arrow
        // button panel is used as the split pane divider
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.right = LABEL_HORIZONTAL_SPACING;
        gbc.gridy++;
        managerPnl.add(new CustomSplitPane(treePnl,
                                           groupTree.createTreePanel("Groups",
                                                                     TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                                     false,
                                                                     ccddMain.getMainFrame()),
                                           createArrowButtonPanel()),
                       gbc);

        // Create the field panel for the description and data fields
        fieldPnl = new FieldPanel();
        fieldPnl.createEditorPanel(CcddGroupManagerDialog.this,
                                   null,
                                   null,
                                   "",
                                   groupTree.getFieldHandler());

        // Add an undo edit manager and add it as a listener for undo/redo
        // changes
        undoManager = new CcddUndoManager()
        {
            /******************************************************************
             * Update the change indicator if the editor panel has changed
             *****************************************************************/
            @Override
            protected void ownerHasChanged()
            {
                updateChangeIndicator();
            }
        };

        // Set the undo/redo manager for the description and data field values
        fieldPnl.setEditPanelUndoManager(undoManager);

        // Set the undo manager in the keyboard handler while the group manager
        // is active
        ccddMain.getKeyboardHandler().setModalUndoManager(undoManager);

        // Add the field panel to the dialog
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.left = 0;
        gbc.insets.bottom = 0;
        gbc.insets.right = 0;
        gbc.weighty = 0.0;
        gbc.gridy++;
        managerPnl.add(fieldPnl.getEditorPanel(), gbc);

        // Create a check box for showing/changing the group CFS application
        // status
        applicationCb = new JCheckBox("Group represents a CFS application");
        applicationCb.setFont(LABEL_FONT_BOLD);
        applicationCb.setBorder(emptyBorder);
        applicationCb.setEnabled(false);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.gridy = 0;
        fieldPnl.getEditorPanel().add(applicationCb, gbc, 0);

        // Add a listener for the application check box
        applicationCb.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Handle a change in the application check box status
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the application check box is selected and a group
                // is selected
                if (((JCheckBox) ae.getSource()).isSelected()
                    && selectedGroup != null)
                {
                    // Get the field information for the group
                    GroupInformation groupInfo = groupTree.getGroupHandler().getGroupInformationByName(selectedGroup.getName());
                    List<FieldInformation> fieldInformation = groupInfo.getFieldInformation();

                    // Step through each default application data field
                    for (DefaultApplicationField field : DefaultApplicationField.values())
                    {
                        // Create a new data field
                        FieldInformation newField = field.createFieldInformation(CcddFieldHandler.getFieldGroupName(selectedGroup.getName()));

                        boolean isExists = false;

                        // Step through the group's existing data fields
                        for (FieldInformation fieldInfo : fieldInformation)
                        {
                            // Check if the data field already exists
                            if (newField.getFieldName().equals(fieldInfo.getFieldName()))
                            {
                                // Set the flag indicating the field exists and
                                // stop searching
                                isExists = true;
                                break;
                            }
                        }

                        // Check if the field doesn't exists
                        if (!isExists)
                        {
                            // Add the field to the group
                            fieldInformation.add(newField);
                        }
                    }

                    // Build the data field panel
                    fieldPnl.createDataFieldPanel();

                    // Force the table editor to redraw in order for the field
                    // updates to appear
                    fieldPnl.getTableEditor().validate();
                    fieldPnl.getTableEditor().repaint();
                }
            }
        });

        // Create the button panel
        JPanel buttonPnl = new JPanel();

        // Define the buttons for the lower panel:
        // New group button
        JButton btnNewGroup = CcddButtonPanelHandler.createButton("New",
                                                                  INSERT_ICON,
                                                                  KeyEvent.VK_N,
                                                                  "Create a new group");

        // Add a listener for the New button
        btnNewGroup.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Add a new group
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                newGroup();
            }
        });

        // Delete group button
        JButton btnDeleteGroup = CcddButtonPanelHandler.createButton("Delete",
                                                                     DELETE_ICON,
                                                                     KeyEvent.VK_D,
                                                                     "Delete an existing group");

        // Add a listener for the Delete button
        btnDeleteGroup.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Delete the selected group(s)
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                deleteGroup();
            }
        });

        // Rename group button
        btnRenameGroup = CcddButtonPanelHandler.createButton("Rename",
                                                             RENAME_ICON,
                                                             KeyEvent.VK_D,
                                                             "Rename an existing group");

        // Add a listener for the Rename button
        btnRenameGroup.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Rename the selected group
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                renameGroup();
            }
        });

        // Copy group button
        btnCopyGroup = CcddButtonPanelHandler.createButton("Copy",
                                                           COPY_ICON,
                                                           KeyEvent.VK_P,
                                                           "Copy an existing group");

        // Add a listener for the Copy button
        btnCopyGroup.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Copy the selected group
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                copyGroup();
            }
        });

        // Manage fields button
        btnManageFields = CcddButtonPanelHandler.createButton("Fields",
                                                              FIELD_ICON,
                                                              KeyEvent.VK_F,
                                                              "Manage the data fields");

        // Add a listener for the Manage Fields command
        btnManageFields.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Manage the data fields
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Create the field editor dialog showing the fields for this
                // group
                new CcddFieldEditorDialog(fieldPnl,
                                          CcddFieldHandler.getFieldGroupName(selectedGroup.getName()),
                                          false);
            }
        });

        // Clear fields button
        btnClearValues = CcddButtonPanelHandler.createButton("Clear",
                                                             CLEAR_ICON,
                                                             KeyEvent.VK_C,
                                                             "Clear the data fields");
        // Add a listener for the Clear values command
        btnClearValues.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Clear the table data field values
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if there are any data fields to clear
                if (!fieldPnl.getFieldHandler().getFieldInformation().isEmpty())
                {
                    // Confirm clearing the data field values
                    if (new CcddDialogHandler().showMessageDialog(CcddGroupManagerDialog.this,
                                                                  "<html><b>Clear the data field values?",
                                                                  "Clear Field Values",
                                                                  JOptionPane.QUESTION_MESSAGE,
                                                                  DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                    {
                        // Clear all of the data field values for the group
                        fieldPnl.getFieldHandler().clearFieldValues();
                    }
                }
            }
        });

        // Store groups button
        JButton btnStoreGroups = CcddButtonPanelHandler.createButton("Store",
                                                                     STORE_ICON,
                                                                     KeyEvent.VK_S,
                                                                     "Store group updates in the database");

        // Add a listener for the Store button
        btnStoreGroups.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Store the groups in the database
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if the groups have changed since the last database
                // commit, that the user confirms storing the groups, and, if
                // the data field table editor is open and has changes that the
                // user confirms discarding them
                if (isGroupsChanged()
                    && new CcddDialogHandler().showMessageDialog(CcddGroupManagerDialog.this,
                                                                 "<html><b>Store groups?",
                                                                 "Store Groups",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                    && ignoreFieldTableChanges()
                )
                {
                    // Store the group list into the database
                    dbTable.storeInformationTable(InternalTable.GROUPS,
                                                  currentGroups,
                                                  updateFields,
                                                  deletedGroups,
                                                  null,
                                                  CcddGroupManagerDialog.this);
                }
            }
        });

        // Close button
        JButton btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the group manager");

        // Add a listener for the Close button
        btnClose.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Close the group selection dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Check if there are no changes to the groups or if the user
                // elects to discard the changes
                if (!isGroupsChanged()
                    || new CcddDialogHandler().showMessageDialog(CcddGroupManagerDialog.this,
                                                                 "<html><b>Discard changes?",
                                                                 "Discard Changes",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Close the dialog
                    closeDialog();

                    // Clear the undo manager in the keyboard handler
                    ccddMain.getKeyboardHandler().setModalUndoManager(null);
                }
            }
        });

        // Set the initial enable status of the buttons
        setGroupButtonsEnabled(false);

        // Add buttons in the order in which they'll appear (left to right, top
        // to bottom)
        buttonPnl.add(btnNewGroup);
        buttonPnl.add(btnRenameGroup);
        buttonPnl.add(btnManageFields);
        buttonPnl.add(btnStoreGroups);
        buttonPnl.add(btnDeleteGroup);
        buttonPnl.add(btnCopyGroup);
        buttonPnl.add(btnClearValues);
        buttonPnl.add(btnClose);

        // Distribute the buttons across two rows
        setButtonRows(2);

        // Display the group management dialog
        showOptionsDialog(ccddMain.getMainFrame(),
                          managerPnl,
                          buttonPnl,
                          DIALOG_TITLE,
                          true);
    }

    /**************************************************************************
     * Set/clear the selected group, add/remove the data fields, enable/disable
     * the description field, and enable/disable the dialog's buttons that
     * apply only if a single group is selected based on if a valid group name
     * is provided
     * 
     * @param groupName
     *            name of the selected group; null if no group or more than one
     *            group is selected
     *************************************************************************/
    private void setGroupAndFields(String groupName)
    {
        // End any active edit sequence
        undoManager.endEditSequence();

        // Initialize the data field information and description assuming a
        // single group isn't selected
        List<FieldInformation> fieldInfo = null;
        String description = "";
        boolean isApplication = false;
        boolean enable = false;

        // Get the selected group's information
        selectedGroup = groupTree.getGroupHandler().getGroupInformationByName(groupName);

        // Check if the group exists
        if (selectedGroup != null)
        {
            // Get the data field information and description
            fieldInfo = selectedGroup.getFieldInformation();
            description = selectedGroup.getDescription();
            isApplication = selectedGroup.isApplication();
            enable = true;
        }

        // Get the data field information for this group and display the fields
        // (if any)
        groupTree.getFieldHandler().setFieldInformation(fieldInfo);
        fieldPnl.setTableEditorOwnerName(groupName);
        fieldPnl.createDataFieldPanel();

        // Update the dialog's minimum size to accommodate the change in the
        // size or number of data fields
        setDialogMinimumSize();

        // Display and enable the group's description
        fieldPnl.enableDescriptionField(enable, description);

        // Display and enable the group's CFS application status
        enableApplicationCheckBox(enable, isApplication);

        // Set the enable state of the buttons that apply only when one group
        // is selected
        setGroupButtonsEnabled(enable);
    }

    /**************************************************************************
     * Set the enable status of the CFS application indicator check box
     * 
     * @param enable
     *            true to enable the check box, false to disable
     * 
     * @param isApplication
     *            true if the group represents a CFS application
     *************************************************************************/
    private void enableApplicationCheckBox(boolean enable,
                                           boolean isApplication)
    {
        applicationCb.setEnabled(enable);

        applicationCb.setSelected(enable
                                        ? isApplication
                                        : false);
    }

    /**************************************************************************
     * Set the enable status of the buttons that apply only when one group is
     * selected
     * 
     * @param enable
     *            true to enable the buttons, false to disable
     *************************************************************************/
    private void setGroupButtonsEnabled(boolean enable)
    {
        btnRenameGroup.setEnabled(enable);
        btnCopyGroup.setEnabled(enable);
        btnManageFields.setEnabled(enable);
        btnClearValues.setEnabled(enable);
    }

    /**************************************************************************
     * Create a panel to contain a pair of buttons. Make all but the button
     * icons transparent
     * 
     * @return JPanel containing the buttons in a vertical layout
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
             * Remove the selected variable(s) from the group
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Remove the variable(s) from the group
                removeTableFromGroup();
            }
        });

        // Create the 'add item to the tree' button
        rightArrowBtn.setIcon(new ImageIcon(getClass().getResource(RIGHT_ICON)));
        rightArrowBtn.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        // Create a listener for the add item button
        rightArrowBtn.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Add the the selected variable(s) to the selected group
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Add the variable(s) to the group
                addTableToGroupDefinition();
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
        gbc.insets.bottom = LAF_CHECK_BOX_HEIGHT * 2 + LABEL_VERTICAL_SPACING;
        gbc.gridy++;
        buttonPnl.add(leftArrowBtn, gbc);

        return buttonPnl;
    }

    /**************************************************************************
     * Add the selected table(s) in the table tree to the selected group
     * definition(s) in the group tree
     *************************************************************************/
    private void addTableToGroupDefinition()
    {
        // Add the selected variable(s) to the group tree
        groupTree.addSourceNodesToTargetNode(tableTree.getSelectedVariables(false),
                                             tableTree.getTableNodeLevel(),
                                             false);

        // Update the group dialog's change indicator
        updateChangeIndicator();
    }

    /**************************************************************************
     * Remove the selected table(s) from the group tree
     *************************************************************************/
    private void removeTableFromGroup()
    {
        // Remove the selected tables from the groups in the group tree
        groupTree.removeSelectedChildNodes(false);

        // Update the group dialog's change indicator
        updateChangeIndicator();
    }

    /**************************************************************************
     * Add a new group to the group tree
     *************************************************************************/
    private void newGroup()
    {
        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create the group name input dialog label and field
        GridBagConstraints gbc = addGroupNameField("Enter new group name",
                                                   "",
                                                   dialogPnl);

        // Create the group description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionLbl.setForeground(Color.BLACK);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.weighty = 0.0;
        gbc.gridy++;
        dialogPnl.add(descriptionLbl, gbc);

        // Create the group description input field
        final JTextArea descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setFont(LABEL_FONT_PLAIN);
        descriptionFld.setEditable(true);
        descriptionFld.setLineWrap(true);
        descriptionFld.setForeground(Color.BLACK);
        descriptionFld.setBackground(Color.WHITE);
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);

        JScrollPane descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBorder(border);

        // Add the description field to the dialog panel
        gbc.insets.top = 0;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;
        dialogPnl.add(descScrollPane, gbc);

        // Create a check box for setting the group as an application
        JCheckBox appCb = new JCheckBox("Group represents a CFS application");
        appCb.setFont(LABEL_FONT_BOLD);
        appCb.setBorder(emptyBorder);
        gbc.insets.top = LABEL_VERTICAL_SPACING;
        gbc.gridy++;
        dialogPnl.add(appCb, gbc);

        // Create a dialog for the new group information
        CcddDialogHandler groupDlg = new CcddDialogHandler()
        {
            /******************************************************************
             * Verify that the dialog content is valid
             * 
             * @return true if the input values are valid
             *****************************************************************/
            @Override
            protected boolean verifySelection()
            {
                descriptionFld.setText(descriptionFld.getText().trim());
                return verifyGroupName(this);
            }
        };

        // Display a dialog for the user to provide a group name and
        // description
        if (groupDlg.showOptionsDialog(CcddGroupManagerDialog.this,
                                       dialogPnl,
                                       "New Group",
                                       DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Add the new group information
            groupTree.getGroupHandler().addGroupInformation(groupNameFld.getText(),
                                                            descriptionFld.getText(),
                                                            appCb.isSelected());

            // Insert the new group into the group tree
            groupTree.addInformationNode(groupNameFld.getText(),
                                         descriptionFld.getText(),
                                         appCb.isSelected());

            // Check if the check box indicating this group represents an
            // application is selected
            if (appCb.isSelected())
            {
                // Get the field information for the new group
                GroupInformation groupInfo = groupTree.getGroupHandler().getGroupInformationByName(groupNameFld.getText());
                List<FieldInformation> fieldInfo = groupInfo.getFieldInformation();

                // Step through each default application data field
                for (DefaultApplicationField field : DefaultApplicationField.values())
                {
                    // Add the field to the group
                    fieldInfo.add(field.createFieldInformation(CcddFieldHandler.getFieldGroupName(groupNameFld.getText())));
                }
            }

            // Update the group dialog's change indicator
            updateChangeIndicator();
        }
    }

    /**************************************************************************
     * Remove the selected group(s) from the group tree
     *************************************************************************/
    private void deleteGroup()
    {
        // Check that a node is selected in the group tree
        if (groupTree.getSelectionCount() != 0)
        {
            // Add the selected group(s) to the deleted groups list
            deletedGroups.addAll(Arrays.asList(groupTree.getSelectedNode()));

            // Remove the selected group(s) information
            groupTree.removeSelectedGroup();

            // Remove the selected group(s) from the group tree
            groupTree.removeSelectedTopLevelNodes();

            // Update the group dialog's change indicator
            updateChangeIndicator();
        }
    }

    /**************************************************************************
     * Rename the selected group
     *************************************************************************/
    private void renameGroup()
    {
        // Get the selected group(s)
        String[] selected = groupTree.getSelectedNode();

        // Check that a single node is selected in the group tree
        if (selected.length == 1)
        {
            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            // Create the group renaming dialog label and field
            addGroupNameField("Rename '"
                              + selected[0]
                              + "' to:",
                              selected[0],
                              dialogPnl);

            // Create the group renaming dialog
            CcddDialogHandler groupDlg = new CcddDialogHandler()
            {
                /**************************************************************
                 * Verify that the dialog content is valid
                 * 
                 * @return true if the input values are valid
                 *************************************************************/
                @Override
                protected boolean verifySelection()
                {
                    return verifyGroupName(this);
                }
            };

            // Display the group renaming dialog
            if (groupDlg.showOptionsDialog(CcddGroupManagerDialog.this,
                                           dialogPnl,
                                           "Rename Group",
                                           DialogOption.RENAME_OPTION,
                                           true) == OK_BUTTON)
            {
                // Rename the group
                groupTree.getGroupHandler().getGroupInformationByName(selected[0]).setName(groupNameFld.getText());
                groupTree.renameNode(selected[0], groupNameFld.getText());

                // Update the group dialog's change indicator
                updateChangeIndicator();
            }
        }
    }

    /**************************************************************************
     * Copy the selected group
     *************************************************************************/
    private void copyGroup()
    {
        // Get the selected group(s)
        String[] selected = groupTree.getSelectedNode();

        // Check that a single node is selected in the group tree
        if (selected.length == 1)
        {
            // Get the group information for the selected group
            GroupInformation groupInfo = groupTree.getGroupHandler().getGroupInformationByName(selected[0]);

            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            // Create the group copying dialog label and field
            addGroupNameField("Copy '"
                              + selected[0]
                              + "' to:",
                              selected[0]
                                  + "_copy",
                              dialogPnl);

            // Create the group copying dialog
            CcddDialogHandler groupDlg = new CcddDialogHandler()
            {
                /**************************************************************
                 * Verify that the dialog content is valid
                 * 
                 * @return true if the input values are valid
                 *************************************************************/
                @Override
                protected boolean verifySelection()
                {
                    return verifyGroupName(this);
                }
            };

            // Display the group copying dialog
            if (groupDlg.showOptionsDialog(CcddGroupManagerDialog.this,
                                           dialogPnl,
                                           "Copy Group",
                                           DialogOption.COPY_OPTION,
                                           true) == OK_BUTTON)
            {
                // Copy the group node in the group tree
                groupTree.copyNodeTree(groupInfo.getName(),
                                       groupNameFld.getText(),
                                       groupInfo);

                // Copy the target group's data fields to the copy of the group
                groupTree.getGroupHandler().getGroupInformationByName(groupNameFld.getText()).setFieldInformation(CcddFieldHandler.getFieldInformationCopy(groupInfo.getFieldInformation()));

                // Step through each data field in the copied group
                for (FieldInformation fieldInfo : groupTree.getGroupHandler().getGroupInformationByName(groupNameFld.getText()).getFieldInformation())
                {
                    // Set the data field owner to the copy's group name
                    fieldInfo.setOwnerName(CcddFieldHandler.getFieldGroupName(groupNameFld.getText()));
                }

                // Update the group dialog's change indicator
                updateChangeIndicator();
            }
        }
    }

    /**************************************************************************
     * Add a group name field to the dialog
     * 
     * @param fieldText
     *            text to display beside the input field
     * 
     * @param currentName
     *            name of the selected group
     * 
     * @param dialogPnl
     *            panel to which to add the input field
     * 
     * @return The GridBagConstraints used to arrange the dialog
     *************************************************************************/
    private GridBagConstraints addGroupNameField(String fieldText,
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
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create the group name label and add it to the dialog panel
        JLabel label = new JLabel(fieldText);
        label.setFont(LABEL_FONT_BOLD);
        dialogPnl.add(label, gbc);

        // Create the group name field and add it to the dialog panel
        groupNameFld = new JTextField(currentName, 20);
        groupNameFld.setFont(LABEL_FONT_PLAIN);
        groupNameFld.setEditable(true);
        groupNameFld.setForeground(Color.BLACK);
        groupNameFld.setBackground(Color.WHITE);
        groupNameFld.setBorder(border);
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
        gbc.insets.bottom = 0;
        dialogPnl.add(groupNameFld, gbc);

        return gbc;
    }

    /**************************************************************************
     * Verify that the contents of the group name field is valid
     * 
     * @param caller
     *            reference to the dialog that is being verified
     * 
     * @return true if the group name is valid; false otherwise
     *************************************************************************/
    private boolean verifyGroupName(CcddDialogHandler caller)
    {
        boolean isValid = true;

        try
        {
            // Get the group name; remove leading & trailing white space
            // characters and store the 'cleaned' name in the input field
            String groupName = groupNameFld.getText().trim();
            groupNameFld.setText(groupName);

            // Check if the group name is blank
            if (groupName.isEmpty())
            {
                // Inform the user that the group name/prefix is invalid
                throw new CCDDException("Group name must be entered");
            }

            // Check if other groups exist
            if (groupTree.getRowCount() != 0)
            {
                // Step through the currently defined group names
                for (String name : groupTree.getGroupHandler().getGroupNames(false))
                {
                    // Check if the new name matches an existing one
                    if (groupName.equals(name))
                    {
                        // Inform the user that the group name already is in
                        // use
                        throw new CCDDException("Group name is already in use");
                    }
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddGroupManagerDialog.this,
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
     * Update the currently selected group's application flag, description, and
     * data fields in the main group information list
     *************************************************************************/
    private void updateSelectedGroupInformation()
    {
        // Check if a group is selected
        if (selectedGroup != null)
        {
            // Set the group's CFS application status
            groupTree.getGroupHandler().setIsApplication(selectedGroup.getName(),
                                                         applicationCb.isSelected());

            // Store the rate and description is case these have changed
            groupTree.getGroupHandler().setDescription(selectedGroup.getName(),
                                                       fieldPnl.getDescription());

            // Store any changes made to the data field values
            fieldPnl.updateCurrentFields(selectedGroup.getFieldInformation());
        }
    }

    /**************************************************************************
     * Check if the groups differ from those last committed to the database
     * 
     * @return true if the group definitions have changed
     *************************************************************************/
    private boolean isGroupsChanged()
    {
        // Store any changes to the currently selected group, if applicable
        updateSelectedGroupInformation();

        // Get the current group definitions from the group tree
        currentGroups = groupTree.createDefinitionsFromTree();

        // Initialize the change flag to true if the number of current and
        // committed group definitions differ
        boolean hasChanges = currentGroups.size() != committedGroups.size();

        // Check if the number of groups is the same
        if (!hasChanges)
        {
            // Step through the current group list
            for (String[] curGrp : currentGroups)
            {
                boolean isFound = false;

                // Step through the committed group list
                for (String[] comGrp : committedGroups)
                {
                    // Check if the current group entry matches the committed
                    // group entry
                    if (Arrays.equals(curGrp, comGrp))
                    {
                        // Set the flag indicating a match and stop searching
                        isFound = true;
                        break;
                    }
                }

                // Check if no matching entry was found
                if (!isFound)
                {
                    // Set the flag indicating a group has changed and stop
                    // searching
                    hasChanges = true;
                    break;
                }
            }
        }

        // Get the reference to the updated group information
        List<GroupInformation> currentGroupFields = groupTree.getGroupHandler().getGroupInformation();

        // Create storage for the updated data fields
        updateFields = new ArrayList<List<FieldInformation>>();

        // Step through each current group
        for (GroupInformation currentInfo : currentGroupFields)
        {
            boolean isDiffers = false;

            // Get the group name
            String groupName = currentInfo.getName();

            // Get a reference to the group's committed information
            GroupInformation committedInfo = groupTree.getGroupHandler().getGroupInformationByName(committedGroupFields,
                                                                                                   groupName);

            // Check if the group isn't new
            if (committedInfo != null)
            {
                // Get the group's current and committed data field information
                List<FieldInformation> currentField = currentInfo.getFieldInformation();
                List<FieldInformation> committedField = committedInfo.getFieldInformation();

                // Check if the number of fields for this group changed
                if (currentField.size() != committedField.size())
                {
                    // Set the flag indicating a field changed
                    isDiffers = true;
                }

                // Check if the number of fields is the same between the
                // committed and current versions
                if (!isDiffers)
                {
                    // Step through each field
                    for (int index = 0; index < currentField.size(); index++)
                    {
                        // Check if the field information differs
                        if (!currentField.get(index).getFieldName().equals(committedField.get(index).getFieldName())
                            || !currentField.get(index).getDescription().equals(committedField.get(index).getDescription())
                            || !currentField.get(index).getInputType().equals(committedField.get(index).getInputType())
                            || currentField.get(index).getSize() != committedField.get(index).getSize()
                            || !currentField.get(index).getValue().equals(committedField.get(index).getValue())
                            || currentField.get(index).isRequired() != committedField.get(index).isRequired())
                        {
                            // Set the flag indicating a field changed and stop
                            // searching
                            isDiffers = true;
                            break;
                        }
                    }
                }
            }
            // This is a new group
            else
            {
                // Set the flag indicating that the new group's data fields
                // should be added to the change list
                isDiffers = true;
            }

            // Check if the group is new or an existing group's data field
            // changed
            if (isDiffers)
            {
                // Check if the group has any data fields
                if (!currentInfo.getFieldInformation().isEmpty())
                {
                    // Add the data fields for this group to the list of those
                    // changed
                    updateFields.add(currentInfo.getFieldInformation());
                }

                // Set the flag indicating a group has changes
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    /**************************************************************************
     * Check if the unsaved changes to an open data field table editor should
     * be ignored
     * 
     * @return true if the there are no unsaved data field table editor changes
     *         or if the user chooses to ignore the changes; false if the group
     *         has no data field changes, the data field table editor isn't
     *         open, or the user chooses to not ignore the unsaved changes
     *************************************************************************/
    private boolean ignoreFieldTableChanges()
    {
        boolean ignoreChanges = true;

        // Check if changes to the group data fields exist, that the data field
        // table editor is open and has unsaved changes, and that the user
        // elects to cancel the update
        if (!updateFields.isEmpty()
            && fldTblEditor != null
            && fldTblEditor.isShowing()
            && fldTblEditor.isFieldTableChanged()
            && new CcddDialogHandler().showMessageDialog(CcddGroupManagerDialog.this,
                                                         "<html><b>Discard data field table editor changes?",
                                                         "Discard Changes",
                                                         JOptionPane.QUESTION_MESSAGE,
                                                         DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
        {
            // Set the flag to indicate that the changes shouldn't be ignored
            ignoreChanges = false;
        }

        return ignoreChanges;
    }

    /**************************************************************************
     * Update the change indicator for the group manager
     *************************************************************************/
    private void updateChangeIndicator()
    {
        // Replace the dialog title, appending the change indicator if changes
        // exist
        setTitle(DIALOG_TITLE
                 + (isGroupsChanged()
                                     ? "*"
                                     : ""));
    }
}
