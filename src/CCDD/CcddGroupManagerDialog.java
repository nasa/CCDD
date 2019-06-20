/**
 * CFS Command and Data Dictionary group manager dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLEAR_ICON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.COPY_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.FIELD_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LAF_CHECK_BOX_HEIGHT;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.RENAME_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;

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
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.CustomSplitPane;
import CCDD.CcddClassesComponent.ToolTipTreeNode;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddUndoHandler.UndoableCheckBox;
import CCDD.CcddUndoHandler.UndoableCheckBox.UndoableToggleButtonModel;
import CCDD.CcddUndoHandler.UndoableTreePathSelection;

/**************************************************************************************************
 * CFS Command and Data Dictionary group manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddGroupManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddGroupManagerDialog groupMgr;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddFieldTableEditorDialog fldTblEditor;
    private CcddTableTreeHandler tableTree;
    private CcddGroupTreeHandler groupTree;
    private final CcddFieldHandler fieldHandler;
    private CcddGroupHandler groupHandler;
    private CcddInputFieldPanelHandler fieldPnlHndlr;
    private final CcddInputTypeHandler inputTypeHandler;
    private CcddUndoManager undoManager;
    private CcddUndoHandler undoHandler;

    // Components referenced by multiple methods
    private Border border;
    private Border emptyBorder;
    private JTextField groupNameFld;
    private JButton btnRenameGroup;
    private JButton btnCopyGroup;
    private JButton btnManageFields;
    private JButton btnClearValues;
    private UndoableCheckBox applicationCb;

    private UndoableTreePathSelection nodeSelect;

    // Group definitions and group information stored in the database
    private List<String[]> committedGroupDefns;
    private List<GroupInformation> committedGroupInfo;

    // Current group definitions
    private List<String[]> currentGroupDefns;

    // Node selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Currently selected group in the group tree; null if none, or more than one group is selected
    private GroupInformation selectedGroup;

    // List containing the data field list for groups with data field updates
    private List<List<FieldInformation>> updateFields;

    // List containing the names of any groups that are deleted
    private List<String> deletedGroups;

    // Flag that indicates if the group manager dialog is undergoing initialization or the group
    // tree is rebuilding
    private boolean isInitializing;

    // Dialog title
    private static final String DIALOG_TITLE = "Manage Groups";

    /**********************************************************************************************
     * Group manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddGroupManagerDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        groupMgr = this;
        dbTable = ccddMain.getDbTableCommandHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        fldTblEditor = ccddMain.getFieldTableEditor();

        // Set the reference to this dialog in main
        ccddMain.setGroupManager(this);

        // Create the group manager dialog
        initialize();
    }

    /**********************************************************************************************
     * Get a reference to the editor panel handler
     *
     * @return Reference to the editor panel handler
     *********************************************************************************************/
    protected CcddInputFieldPanelHandler getEditorPanelHandler()
    {
        return fieldPnlHndlr;
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of group updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doGroupUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Store the groups in the committed groups list
            copyGroupInformation();

            // Step through each group
            for (GroupInformation groupInfo : committedGroupInfo)
            {
                // Update the group's data field information in the field handler
                fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(groupInfo.getName()),
                                                            groupInfo.getFieldInformation());
            }

            // Remove any group names from the deleted groups list
            deletedGroups.clear();

            // Check if the data field editor table dialog is open
            if (fldTblEditor != null && fldTblEditor.isShowing())
            {
                // Update the data field editor
                fldTblEditor.getTable().loadAndFormatData();
            }

            // Update the script associations manager and executive dialogs
            ccddMain.updateScriptAssociationsDialogs();

            // Remove the group dialog's change indicator
            setTitle(DIALOG_TITLE);

            // Update the message ID names combo boxes in any open table editors in case the group
            // has a message ID name or message ID data field that may have changed
            dbTable.updateInputTypeColumns(null, groupMgr);
        }

        // Discard any store edits
        undoManager.discardAllEdits();
    }

    /**********************************************************************************************
     * Store a copy of the group information
     *********************************************************************************************/
    private void copyGroupInformation()
    {
        committedGroupInfo = new ArrayList<GroupInformation>();

        // Store the group definitions
        committedGroupDefns = groupTree.createDefinitionsFromTree();

        // Step through each group
        for (GroupInformation groupInfo : groupHandler.getGroupInformation())
        {
            // Create a copy of the group information and store it in the committed group list
            committedGroupInfo.add(new GroupInformation(groupInfo.getName(),
                                                        null,
                                                        null,
                                                        null,
                                                        CcddFieldHandler.getFieldInformationCopy(groupInfo.getFieldInformation())));
        }
    }

    /**********************************************************************************************
     * Create the group manager dialog. This is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the group manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the group manager dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                isNodeSelectionChanging = false;
                selectedGroup = null;
                deletedGroups = new ArrayList<String>();

                // Set the flag to indicate the group manager dialog is being initialized
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

                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                1.0,
                                                                0.0,
                                                                GridBagConstraints.LINE_START,
                                                                GridBagConstraints.BOTH,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                           0,
                                                                           0),
                                                                0,
                                                                0);

                // Add an undo edit manager
                undoManager = new CcddUndoManager()
                {
                    /******************************************************************************
                     * Update the change indicator if the editor panel has changed
                     *****************************************************************************/
                    @Override
                    protected void ownerHasChanged()
                    {
                        // Check if the group manager dialog is not being initialized - changes
                        // during initialization are ignored
                        if (!isInitializing)
                        {
                            updateChangeIndicator();
                        }
                    }
                };

                // Create the undo handler for the components with undoable actions. Disable
                // storage of edit actions during dialog creation
                undoHandler = new CcddUndoHandler(undoManager);
                nodeSelect = undoHandler.new UndoableTreePathSelection();
                undoHandler.setAllowUndo(false);

                // Build the group tree
                groupTree = new CcddGroupTreeHandler(ccddMain,
                                                     undoHandler,
                                                     ccddMain.getMainFrame())
                {
                    /******************************************************************************
                     * Override building the group tree so that change updates can be inhibited
                     *****************************************************************************/
                    @Override
                    protected void buildTree(boolean filterByApp,
                                             String scheduleRate,
                                             boolean isApplicationOnly,
                                             Component parent)
                    {
                        // Set the flag to inhibit registering a group change due to the tree is
                        // being built
                        isInitializing = true;
                        super.buildTree(filterByApp,
                                        scheduleRate,
                                        isApplicationOnly,
                                        parent);
                        isInitializing = false;
                    }

                    /******************************************************************************
                     * Respond to changes in selection of a node in the group tree
                     *****************************************************************************/
                    @Override
                    protected void updateTableSelection()
                    {
                        // Check that a node selection change is not in progress
                        if (!isNodeSelectionChanging)
                        {
                            // Set the flag to inhibit registering the node selection as a group
                            // change
                            isInitializing = true;

                            // Set the flag to prevent link tree updates
                            isNodeSelectionChanging = true;

                            // Needed for the group manager dialog's size to be adjusted for the
                            // data fields
                            groupMgr.setPreferredSize(null);

                            // Store any changes to the currently selected group, if applicable
                            updateSelectedGroupInformation();

                            // Update the description field text so that it can be undone/redone.
                            // The focus change, which is usually used to perform the text update,
                            // occurs after the node selection edit action, causing the wrong
                            // description field to be changed. The description text is only placed
                            // on the stack if edit is active for the description field (i.e., it
                            // doesn't have the focus)
                            fieldPnlHndlr.updateDescriptionField(fieldPnlHndlr.isDescriptionFocusOwner());

                            // Get the name of the selected group(s)
                            String[] selected = getTopLevelSelectedNodeNames();

                            // If a single group is selected then set the selected group, enable
                            // and populate the description field, and display the group's data
                            // fields; otherwise clear the selected group, disable and clear the
                            // description field, and remove any data fields
                            setGroupAndFields(selected.length == 1
                                                                   ? selected[0]
                                                                   : null);

                            // Check if the node change isn't set to be ignored for undo/redo
                            // purposes. The undo handler sets the flag so that the undo/redo
                            // operation isn't recorded on the undo/redo stack
                            if (undoHandler.isAllowUndo())
                            {
                                // Add the node path selection change to the undo/redo stack and
                                // store the field information in the undo handler
                                nodeSelect.selectTreePath(getSelectedPaths());
                            }

                            // Reset the flag to allow group and group tree updates
                            isInitializing = false;
                            isNodeSelectionChanging = false;
                        }
                    }

                    /******************************************************************************
                     * Set the table type and application node enable enable state following an
                     * undo or redo operation that adds or removes a node
                     *****************************************************************************/
                    @Override
                    protected void nodeAddRemoveCleanup()
                    {
                        // Set the table type and application node enable status
                        groupTree.setHeaderNodeEnable();
                    }

                    /******************************************************************************
                     * Update the group name following a node name change undo or redo operation
                     *
                     * @param wasValue
                     *            group name prior to the undo/redo operation
                     *
                     * @param isValue
                     *            group name after the undo/redo operation
                     *****************************************************************************/
                    @Override
                    protected void nodeRenameCleanup(Object wasValue, Object isValue)
                    {
                        // Get the reference to the group and update the group name
                        GroupInformation renamedGroup = groupHandler.getGroupInformationByName(wasValue.toString());
                        renamedGroup.setName(isValue.toString());
                    }
                };

                // Get the references to the group and data field handlers created by the group
                // tree. These are used to shorten subsequent calls
                groupHandler = groupTree.getGroupHandler();

                // Set the data field handler and group tree references in the undo handler so that
                // data field and tree edits can be undone/redone
                undoHandler.setFieldHandler(fieldHandler);
                undoHandler.setTree(groupTree);

                // Store the initial group information
                copyGroupInformation();

                // Create panels to hold the components of the dialog
                JPanel upperPnl = new JPanel(new GridBagLayout());
                JPanel titlePnl = new JPanel(new GridBagLayout());
                JPanel tableTreePnl = new JPanel(new GridBagLayout());
                upperPnl.setBorder(emptyBorder);
                titlePnl.setBorder(emptyBorder);
                tableTreePnl.setBorder(emptyBorder);

                // Create the group manager dialog labels and fields
                JLabel dlgLabel = new JLabel("Assign tables to groups");
                dlgLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                titlePnl.add(dlgLabel, gbc);

                // Add the upper panel components to the dialog panel
                upperPnl.add(titlePnl, gbc);

                // Build the table tree showing both table prototypes and table instances; i.e.,
                // parent tables with their child tables (i.e., parents with children)
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     null,
                                                     TableTreeType.TABLES,
                                                     false,
                                                     true,
                                                     ccddMain.getMainFrame())
                {
                    /******************************************************************************
                     * Respond to changes in selection of a node in the table tree
                     *****************************************************************************/
                    @Override
                    protected void updateTableSelection()
                    {
                        // Select the associated group in the group tree if a table is selected in
                        // the table tree. Note that below any linked variables are deselected, so
                        // this call must occur first
                        selectGroupByTable();
                    }
                };

                // Create a table tree panel and add it to another panel (in order to control
                // spacing)
                gbc.insets.top = 0;
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.weighty = 1.0;
                tableTreePnl.add(tableTree.createTreePanel("Tables",
                                                           TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                           false,
                                                           ccddMain.getMainFrame()),
                                 gbc);
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.bottom = 0;

                // Create a split pane containing the table tree in the left pane and the group
                // tree in the right pane and add it to the panel. The arrow button panel is used
                // as the split pane divider
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridy++;
                upperPnl.add(new CustomSplitPane(tableTreePnl,
                                                 groupTree.createTreePanel("Groups",
                                                                           TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                                           false,
                                                                           ccddMain.getMainFrame()),
                                                 createArrowButtonPanel(),
                                                 JSplitPane.HORIZONTAL_SPLIT),
                             gbc);

                // Create the field panel for the description and data fields
                fieldPnlHndlr = new CcddInputFieldPanelHandler()
                {
                    /******************************************************************************
                     * Update the group manager change indicator
                     *****************************************************************************/
                    @Override
                    protected void updateOwnerChangeIndicator()
                    {
                        updateChangeIndicator();
                    }
                };

                // Set the undo/redo manager and handler for the description and data field values
                fieldPnlHndlr.setEditPanelUndo(undoManager, undoHandler);

                // Set the modal undo manager in the keyboard handler while the group manager is
                // active
                ccddMain.getKeyboardHandler().setModalDialogReference(undoManager, null);

                // Re-enable storage of edit actions
                undoHandler.setAllowUndo(true);

                // Create a check box for showing/changing the group CFS application status
                applicationCb = undoHandler.new UndoableCheckBox("Group represents a CFS application",
                                                                 false);
                applicationCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                applicationCb.setBorder(emptyBorder);
                applicationCb.setEnabled(false);

                // Create a listener for changes in selection of the application check box
                applicationCb.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a change to the application check box selection
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Rebuild the tree based on the application and filter selection
                        groupTree.buildTree(selectedGroup.getName(),
                                            applicationCb.isSelected(),
                                            CcddGroupManagerDialog.this);
                    }
                });

                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
                gbc.insets.right = 0;
                gbc.weighty = 0.0;
                gbc.gridy++;
                upperPnl.add(applicationCb, gbc);

                // Create the description and data field panel. Since no group is initially
                // selected the owner, description, and data fields are null
                fieldPnlHndlr.createDescAndDataFieldPanel(ccddMain,
                                                          groupMgr,
                                                          upperPnl,
                                                          null,
                                                          null,
                                                          null);

                // Add a listener for the application check box
                applicationCb.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a change in the application check box status
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the application check box is selected and a single group is
                        // selected
                        if (((JCheckBox) ae.getSource()).isSelected() && selectedGroup != null)
                        {
                            // The application check box selection and the added CFS application
                            // data fields should be a single edit action so that both are removed
                            // if an undo is performed. Remove the check box selection from the
                            // undo stack, disable automatic edit sequence ending, then perform the
                            // check box selection again so that it's added to the undo stack
                            // without ending the edit sequence
                            undoManager.undoRemoveEdit();
                            undoHandler.setAutoEndEditSequence(false);
                            applicationCb.setSelected(true);

                            // Get the field information for the group
                            GroupInformation groupInfo = groupHandler.getGroupInformationByName(selectedGroup.getName());
                            List<FieldInformation> fieldInformation = groupInfo.getFieldInformation();

                            // Step through each default application data field
                            for (DefaultApplicationField field : DefaultApplicationField.values())
                            {
                                // Create a new data field
                                FieldInformation newField = field.createFieldInformation(CcddFieldHandler.getFieldGroupName(selectedGroup.getName()),
                                                                                         inputTypeHandler);

                                boolean isExists = false;

                                // Step through the group's existing data fields
                                for (FieldInformation fieldInfo : fieldInformation)
                                {
                                    // Check if the data field already exists
                                    if (newField.getFieldName().equals(fieldInfo.getFieldName()))
                                    {
                                        // Set the flag indicating the field exists and stop
                                        // searching
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

                            // Needed for the group manager dialog's size to be adjusted for the
                            // data fields
                            groupMgr.setPreferredSize(null);

                            // Rebuild the data field panel and update the dialog's size
                            recreateDataFieldPanel(true);

                            // Re-enable automatic edit sequence ending, then end the edit sequence
                            // to group the check box and added data fields
                            undoHandler.setAutoEndEditSequence(true);
                            undoManager.endEditSequence();
                        }
                    }
                });

                // Define the buttons for the lower panel: New group button
                JButton btnNewGroup = CcddButtonPanelHandler.createButton("New",
                                                                          INSERT_ICON,
                                                                          KeyEvent.VK_N,
                                                                          "Create a new group");

                // Add a listener for the New button
                btnNewGroup.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Add a new group
                     *****************************************************************************/
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
                    /******************************************************************************
                     * Delete the selected group(s)
                     *****************************************************************************/
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
                    /******************************************************************************
                     * Rename the selected group
                     *****************************************************************************/
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
                    /******************************************************************************
                     * Copy the selected group
                     *****************************************************************************/
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
                    /******************************************************************************
                     * Manage the data fields
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Create the field editor dialog showing the fields for this group
                        new CcddFieldEditorDialog(ccddMain,
                                                  fieldPnlHndlr,
                                                  CcddFieldHandler.getFieldGroupName(selectedGroup.getName()),
                                                  selectedGroup.getFieldInformation(),
                                                  false,
                                                  ModifiableSizeInfo.MIN_DIALOG_WIDTH.getSize());

                        // Set the undo manager in the keyboard handler back to the group manager
                        ccddMain.getKeyboardHandler().setModalDialogReference(undoManager, null);

                        // Update the group's field information
                        selectedGroup.setFieldInformation(fieldPnlHndlr.getPanelFieldInformation());

                        // Enable/disable the Clear values button depending on if any data fields
                        // remain
                        btnClearValues.setEnabled(!fieldPnlHndlr.getPanelFieldInformation().isEmpty());
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
                    /******************************************************************************
                     * Clear the table data field values
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Clear all of the data field values for the group
                        fieldPnlHndlr.clearFieldValues();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit action");

                // Create a listener for the Undo command
                ActionListener undoAction = new ActionListener()
                {
                    /******************************************************************************
                     * Undo the last edit
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        undoManager.undo();

                        // Check if a single group is selected
                        if (selectedGroup != null)
                        {
                            // Restore the group's data field information to that currently
                            // displayed following the undo operation (in case there's a change)
                            selectedGroup.setFieldInformation(fieldPnlHndlr.getPanelFieldInformation());
                        }

                        // Update the data field background colors
                        fieldPnlHndlr.setFieldBackgound();
                    }
                };

                // Add the undo listener to the Undo button and menu command
                btnUndo.addActionListener(undoAction);

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit action");

                // Create a listener for the Redo command
                ActionListener redoAction = new ActionListener()
                {
                    /******************************************************************************
                     * Redo the last cell that was undone
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        undoManager.redo();

                        // Update the data field background colors
                        fieldPnlHndlr.setFieldBackgound();
                    }
                };

                // Add the redo listener to the Redo button and menu command
                btnRedo.addActionListener(redoAction);

                // Store groups button
                JButton btnStoreGroups = CcddButtonPanelHandler.createButton("Store",
                                                                             STORE_ICON,
                                                                             KeyEvent.VK_S,
                                                                             "Store group updates in the database");
                btnStoreGroups.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Add a listener for the Store button
                btnStoreGroups.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Store the groups in the database
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if the groups have changed since the last database commit, that
                        // the user confirms storing the groups, and, if the data field table
                        // editor is open and has changes that the user confirms discarding them
                        if (isGroupsChanged()
                            && new CcddDialogHandler().showMessageDialog(groupMgr,
                                                                         "<html><b>Store groups?",
                                                                         "Store Groups",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                            && ignoreFieldTableChanges())
                        {
                            // Store the group list into the database
                            dbTable.storeInformationTableInBackground(InternalTable.GROUPS,
                                                                      currentGroupDefns,
                                                                      updateFields,
                                                                      deletedGroups,
                                                                      null,
                                                                      null,
                                                                      groupMgr);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the group manager");

                // Add a listener for the Close button
                btnClose.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Close the group selection dialog
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Check if there are no changes to the groups or if the user elects to
                        // discard the changes
                        if (!isGroupsChanged()
                            || new CcddDialogHandler().showMessageDialog(groupMgr,
                                                                         "<html><b>Discard changes?",
                                                                         "Discard Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Close the dialog
                            closeDialog();

                            // Clear the modal dialog references in the keyboard handler
                            ccddMain.getKeyboardHandler().setModalDialogReference(null, null);
                        }
                    }
                });

                // Set the initial enable status of the buttons
                setGroupButtonsEnabled(false);

                // Add buttons in the order in which they'll appear (left to right, top to bottom)
                buttonPnl.add(btnNewGroup);
                buttonPnl.add(btnRenameGroup);
                buttonPnl.add(btnManageFields);
                buttonPnl.add(btnUndo);
                buttonPnl.add(btnStoreGroups);
                buttonPnl.add(btnDeleteGroup);
                buttonPnl.add(btnCopyGroup);
                buttonPnl.add(btnClearValues);
                buttonPnl.add(btnRedo);
                buttonPnl.add(btnClose);

                // Distribute the buttons across two rows
                setButtonRows(2);

                // Reset the flag now that initialization is complete
                isInitializing = false;
            }

            /**************************************************************************************
             * Group manager dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the group manager dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  fieldPnlHndlr.getFieldPanel(),
                                  buttonPnl,
                                  btnClose,
                                  DIALOG_TITLE,
                                  true);
            }
        });
    }

    /**********************************************************************************************
     * Set/clear the selected group, add/remove the data fields, enable/disable the description
     * field, and enable/disable the dialog's buttons that apply only if a single group is selected
     * based on if a valid group name is provided
     *
     * @param groupName
     *            name of the selected group; null if no group or more than one group is selected
     *********************************************************************************************/
    private void setGroupAndFields(String groupName)
    {
        // Check if the edit sequence is set to be automatically terminated. During an undo or redo
        // operation the edit sequence termination is handled manually in order to group the tree
        // and group information updates into a single compound edit
        if (undoHandler.isAutoEndEditSequence())
        {
            // End any active edit sequence
            undoManager.endEditSequence();
        }

        // Initialize the description and application status assuming a single group isn't selected
        String description = "";
        boolean isApplication = false;
        boolean enable = false;

        // Get the selected group's information
        selectedGroup = groupHandler.getGroupInformationByName(groupName);

        // Check if the group exists
        if (selectedGroup != null)
        {
            // Get the description and application status
            description = selectedGroup.getDescription();
            isApplication = selectedGroup.isApplication();
            enable = true;
        }

        // Rebuild the data field panel for the selected group (or remove any fields if no group is
        // selected), and update the dialog's size
        fieldPnlHndlr.setOwnerName(CcddFieldHandler.getFieldGroupName(groupName));
        recreateDataFieldPanel(false);

        // Display and enable the group's description
        fieldPnlHndlr.enableDescriptionField(enable, description);

        // Display and enable the group's CFS application status
        enableApplicationCheckBox(enable, isApplication);

        // Set the enable state of the buttons that apply only when one group is selected
        setGroupButtonsEnabled(enable);
    }

    /**********************************************************************************************
     * Update the group manager dialog's size following additions or deletions to the data field
     * panel
     *
     * @param undoable
     *            true if the update can be undone/redone
     *********************************************************************************************/
    private void recreateDataFieldPanel(boolean undoable)
    {
        // Rebuild the data field panel using the data fields for the selected group (display an
        // empty field panel if a single group isn't selected)
        fieldPnlHndlr.createDataFieldPanel(undoable,
                                           (selectedGroup != null
                                                                  ? selectedGroup.getFieldInformation()
                                                                  : null));
    }

    /**********************************************************************************************
     * Set the enable status of the CFS application indicator check box
     *
     * @param enable
     *            true to enable the check box, false to disable
     *
     * @param isApplication
     *            true if the group represents a CFS application
     *********************************************************************************************/
    private void enableApplicationCheckBox(boolean enable, boolean isApplication)
    {
        // Enable or disable the check box based on the input flag
        applicationCb.setEnabled(enable);

        // Use the model so that the check box selection action isn't stored on the undo/redo stack
        ((UndoableToggleButtonModel) applicationCb.getModel()).setSelected((enable
                                                                                   ? isApplication
                                                                                   : false),
                                                                           false);
    }

    /**********************************************************************************************
     * Set the enable status of the buttons that apply only when one group is selected
     *
     * @param enable
     *            true to enable the buttons, false to disable
     *********************************************************************************************/
    private void setGroupButtonsEnabled(boolean enable)
    {
        btnRenameGroup.setEnabled(enable);
        btnCopyGroup.setEnabled(enable);
        btnManageFields.setEnabled(enable);
        btnClearValues.setEnabled(enable && !fieldPnlHndlr.getPanelFieldInformation().isEmpty());
    }

    /**********************************************************************************************
     * Create a panel to contain a pair of buttons. Make all but the button icons transparent
     *
     * @return JPanel containing the buttons in a vertical layout
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
             * Remove the selected variable(s) from the group
             *************************************************************************************/
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
            /**************************************************************************************
             * Add the the selected variable(s) to the selected group
             *************************************************************************************/
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
        gbc.insets.bottom = LAF_CHECK_BOX_HEIGHT * 3
                            + ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        buttonPnl.add(leftArrowBtn, gbc);

        return buttonPnl;
    }

    /**********************************************************************************************
     * Add the selected table(s) in the table tree to the selected group definition(s) in the group
     * tree
     *********************************************************************************************/
    private void addTableToGroupDefinition()
    {
        // Set the flag to prevent calls to update the dialog change indicator while adding tables
        // to the group
        isInitializing = true;

        // Disable automatically ending the edit sequence. This allows all of the added group
        // members to be grouped into a single sequence so that if undone, all members are removed
        // together
        undoHandler.setAutoEndEditSequence(false);

        // Add the selected table(s) to the group tree
        groupTree.addSourceNodesToTargetNode(tableTree.getSelectedVariables(false),
                                             tableTree.getHeaderNodeLevel(),
                                             false);

        // Set the table type and application node enable status
        groupTree.setHeaderNodeEnable();

        // Force the root node to draw with the node additions
        groupTree.refreshTree();

        // Set the flag to so that the dialog change indicator can be updated
        isInitializing = false;

        // Update the group dialog's change indicator
        updateChangeIndicator();

        // Re-enable automatic edit sequence ending, then end the edit sequence to group the added
        // members
        undoHandler.setAutoEndEditSequence(true);
        undoManager.endEditSequence();
    }

    /**********************************************************************************************
     * Remove the selected table(s) from the group tree
     *********************************************************************************************/
    private void removeTableFromGroup()
    {
        // Set the flag to prevent calls to update the dialog change indicator while adding tables
        // to the group
        isInitializing = true;

        // Disable automatically ending the edit sequence. This allows all of the deleted group
        // members to be grouped into a single sequence so that if undone, all members are restored
        // together
        undoHandler.setAutoEndEditSequence(false);

        ToolTipTreeNode node = null;

        // Check if a single group is selected
        if (selectedGroup != null)
        {
            // Store the selected group's node
            node = (ToolTipTreeNode) groupTree.getSelectionPath().getPathComponent(1);
        }

        // Remove the selected tables from the group tree
        groupTree.removeSelectedItemNodes();

        // Set the table type and application node enable status
        groupTree.setHeaderNodeEnable();

        // Check if a single group was selected prior to removing the selected table(s)
        if (node != null)
        {
            // Select the node for the group that had the table(s) removed
            groupTree.setSelectionPath(new TreePath(node.getPath()));
        }

        // Force the root node to draw with the node removals
        groupTree.refreshTree();

        // Set the flag to so that the dialog change indicator can be updated
        isInitializing = false;

        // Update the group dialog's change indicator
        updateChangeIndicator();

        // Re-enable automatic edit sequence ending, then end the edit sequence to group the
        // removed members
        undoHandler.setAutoEndEditSequence(true);
        undoManager.endEditSequence();
    }

    /**********************************************************************************************
     * Add a new group to the group tree
     *********************************************************************************************/
    private void newGroup()
    {
        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create the group name input dialog label and field
        GridBagConstraints gbc = addGroupNameField("Enter new group name", "", dialogPnl);

        // Create the group description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.weighty = 0.0;
        gbc.gridy++;
        dialogPnl.add(descriptionLbl, gbc);

        // Create the group description input field
        final JTextArea descriptionFld = new JTextArea("", 3, 1);
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(true);
        descriptionFld.setLineWrap(true);
        descriptionFld.setWrapStyleWord(true);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        JScrollPane descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBorder(border);

        // Add the description field to the dialog panel
        gbc.insets.top = 0;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy++;
        dialogPnl.add(descScrollPane, gbc);

        // Create a check box for setting the group as an application
        JCheckBox appCb = new JCheckBox("Group represents a CFS application");
        appCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        appCb.setBorder(emptyBorder);
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        dialogPnl.add(appCb, gbc);

        // Create a dialog for the new group information
        CcddDialogHandler groupDlg = new CcddDialogHandler()
        {
            /**************************************************************************************
             * Verify that the dialog content is valid
             *
             * @return true if the input values are valid
             *************************************************************************************/
            @Override
            protected boolean verifySelection()
            {
                descriptionFld.setText(descriptionFld.getText().trim());
                return verifyGroupName(this);
            }
        };

        // Display a dialog for the user to provide a group name and description
        if (groupDlg.showOptionsDialog(groupMgr,
                                       dialogPnl,
                                       "New Group",
                                       DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Disable automatically ending the edit sequence. This allows the added group's edits
            // to be grouped into a single sequence so that if undone, all edits are removed
            // together
            undoHandler.setAutoEndEditSequence(false);

            // Add the new group information
            groupHandler.addGroupInformation(groupNameFld.getText(),
                                             descriptionFld.getText(),
                                             appCb.isSelected());

            // Insert the new group into the group tree
            groupTree.addInformationNode(groupNameFld.getText(),
                                         descriptionFld.getText(),
                                         appCb.isSelected(),
                                         true);

            // Hide the group's empty table type nodes
            groupTree.setHeaderNodeEnable();

            // Check if the check box indicating this group represents an application is selected
            if (appCb.isSelected())
            {
                // Get the field information and data fields for the new group
                GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupNameFld.getText());
                List<FieldInformation> fieldInfo = groupInfo.getFieldInformation();

                // Step through each default application data field
                for (DefaultApplicationField field : DefaultApplicationField.values())
                {
                    // Add the field to the group
                    fieldInfo.add(field.createFieldInformation(CcddFieldHandler.getFieldGroupName(groupNameFld.getText()),
                                                               inputTypeHandler));
                }
            }

            // Update the group dialog's change indicator
            updateChangeIndicator();

            // Re-enable automatic edit sequence ending, then end the edit sequence to group the
            // added group's edits
            undoHandler.setAutoEndEditSequence(true);
            undoManager.endEditSequence();
        }
    }

    /**********************************************************************************************
     * Remove the selected group(s) from the group tree
     *********************************************************************************************/
    private void deleteGroup()
    {
        // Check that a node is selected in the group tree
        if (groupTree.getSelectionCount() != 0)
        {
            // Disable automatically ending the edit sequence. This allows all of the deleted
            // groups to be grouped into a single sequence so that if undone, all groups are
            // restored together
            undoHandler.setAutoEndEditSequence(false);

            // Add the selected group(s) to the deleted groups list
            deletedGroups.addAll(Arrays.asList(groupTree.getTopLevelSelectedNodeNames()));

            // Remove the selected group(s) information
            groupTree.removeSelectedGroups();

            // Update the group dialog's change indicator
            updateChangeIndicator();

            // Re-enable automatic edit sequence ending, then end the edit sequence to group the
            // deleted groups
            undoHandler.setAutoEndEditSequence(true);
            undoManager.endEditSequence();
        }
    }

    /**********************************************************************************************
     * Rename the selected group
     *********************************************************************************************/
    private void renameGroup()
    {
        // Get the selected group(s)
        String[] selected = groupTree.getTopLevelSelectedNodeNames();

        // Check that a single node is selected in the group tree
        if (selected.length == 1)
        {
            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            // Create the group renaming dialog label and field
            addGroupNameField("Rename '" + selected[0] + "' to:", selected[0], dialogPnl);

            // Create the group renaming dialog
            CcddDialogHandler groupDlg = new CcddDialogHandler()
            {
                /**********************************************************************************
                 * Verify that the dialog content is valid
                 *
                 * @return true if the input values are valid
                 *********************************************************************************/
                @Override
                protected boolean verifySelection()
                {
                    return verifyGroupName(this);
                }
            };

            // Display the group renaming dialog
            if (groupDlg.showOptionsDialog(groupMgr,
                                           dialogPnl,
                                           "Rename Group",
                                           DialogOption.RENAME_OPTION,
                                           true) == OK_BUTTON)
            {
                // Disable automatically ending the edit sequence. This allows all of the renamed
                // group's edits to be grouped into a single sequence so that if undone, the edits
                // are restored together
                undoHandler.setAutoEndEditSequence(false);

                // Get a reference to the renamed group, then rename the group
                GroupInformation renamedGroup = groupHandler.getGroupInformationByName(selected[0]);
                renamedGroup.setName(groupNameFld.getText());
                groupTree.renameRootChildNode(selected[0], groupNameFld.getText());

                // Add the original name to the list of deleted groups so that the group's data
                // fields are removed from the internal data fields table
                deletedGroups.add(selected[0]);

                // Step through each data field in the renamed group
                for (FieldInformation fieldInfo : fieldPnlHndlr.getPanelFieldInformation())
                {
                    // Update the field's owner
                    fieldInfo.setOwnerName(CcddFieldHandler.getFieldGroupName(groupNameFld.getText()));
                }

                // Update the group dialog's change indicator
                updateChangeIndicator();

                // Re-enable automatic edit sequence ending, then end the edit sequence to group
                // the renamed group's edits
                undoHandler.setAutoEndEditSequence(true);
                undoManager.endEditSequence();
            }
        }
    }

    /**********************************************************************************************
     * Copy the selected group
     *********************************************************************************************/
    private void copyGroup()
    {
        // Get the selected group(s)
        String[] selected = groupTree.getTopLevelSelectedNodeNames();

        // Check that a single node is selected in the group tree
        if (selected.length == 1)
        {
            // Get the group information for the selected group
            GroupInformation groupInfo = groupHandler.getGroupInformationByName(selected[0]);

            // Create a panel to contain the dialog components
            JPanel dialogPnl = new JPanel(new GridBagLayout());

            // Create the group copying dialog label and field
            addGroupNameField("Copy '" + selected[0] + "' to:", selected[0] + "_copy", dialogPnl);

            // Create the group copying dialog
            CcddDialogHandler groupDlg = new CcddDialogHandler()
            {
                /**********************************************************************************
                 * Verify that the dialog content is valid
                 *
                 * @return true if the input values are valid
                 *********************************************************************************/
                @Override
                protected boolean verifySelection()
                {
                    return verifyGroupName(this);
                }
            };

            // Display the group copying dialog
            if (groupDlg.showOptionsDialog(groupMgr,
                                           dialogPnl,
                                           "Copy Group",
                                           DialogOption.COPY_OPTION,
                                           true) == OK_BUTTON)
            {
                // Disable automatically ending the edit sequence. This allows all of the copied
                // group's edits to be grouped into a single sequence so that if undone, all of the
                // group's edits are restored together
                undoHandler.setAutoEndEditSequence(false);

                // Copy the group node in the group tree
                groupTree.copyNodeTree(groupInfo.getName(), groupNameFld.getText(), groupInfo);

                // Get a reference to the copy of the group
                GroupInformation copiedGroup = groupHandler.getGroupInformationByName(groupNameFld.getText());

                // Copy the target group's data fields to the copy of the group
                copiedGroup.setFieldInformation(CcddFieldHandler.getFieldInformationCopy(groupInfo.getFieldInformation()));

                // Step through each data field in the copied group
                for (FieldInformation fieldInfo : copiedGroup.getFieldInformation())
                {
                    // Set the data field owner to the copy's group name
                    fieldInfo.setOwnerName(CcddFieldHandler.getFieldGroupName(groupNameFld.getText()));

                    // Add the field to the field handler
                    fieldHandler.getFieldInformation().add(fieldInfo);
                }

                // Update the group dialog's change indicator
                updateChangeIndicator();

                // Re-enable automatic edit sequence ending, then end the edit sequence to group
                // the renamed group's edits
                undoHandler.setAutoEndEditSequence(true);
                undoManager.endEditSequence();
            }
        }
    }

    /**********************************************************************************************
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
     *********************************************************************************************/
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2),
                                                        0,
                                                        0);

        // Create the group name label and add it to the dialog panel
        JLabel label = new JLabel(fieldText);
        label.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        dialogPnl.add(label, gbc);

        // Create the group name field and add it to the dialog panel
        groupNameFld = new JTextField(currentName, 20);
        groupNameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        groupNameFld.setEditable(true);
        groupNameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        groupNameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        groupNameFld.setBorder(border);
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.bottom = 0;
        dialogPnl.add(groupNameFld, gbc);

        return gbc;
    }

    /**********************************************************************************************
     * Verify that the contents of the group name field is valid
     *
     * @param caller
     *            reference to the dialog that is being verified
     *
     * @return true if the group name is valid; false otherwise
     *********************************************************************************************/
    private boolean verifyGroupName(CcddDialogHandler caller)
    {
        boolean isValid = true;

        try
        {
            // Get the group name; remove leading & trailing white space characters and store the
            // 'cleaned' name in the input field
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
                for (String name : groupHandler.getGroupNames(false, true))
                {
                    // Check if the new name matches an existing one
                    if (groupName.equals(name))
                    {
                        // Inform the user that the group name is already in use
                        throw new CCDDException("Group name is already in use");
                    }
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(groupMgr,
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

    /**********************************************************************************************
     * Update the currently selected group's application flag, description, and data fields in the
     * main group information list
     *********************************************************************************************/
    private void updateSelectedGroupInformation()
    {
        // Check if a single group is selected
        if (selectedGroup != null)
        {
            // Set the group's CFS application status
            groupHandler.setIsApplication(selectedGroup.getName(), applicationCb.isSelected());

            // Store the rate and description is case these have changed
            groupHandler.setDescription(selectedGroup.getName(), fieldPnlHndlr.getDescription());

            // Store the updated data field information in the group
            selectedGroup.setFieldInformation(CcddFieldHandler.getFieldInformationCopy(fieldPnlHndlr.getPanelFieldInformation()));

            // Store any changes made to the data field values
            fieldPnlHndlr.updateFieldValueFromComponent(selectedGroup.getFieldInformation());
        }
    }

    /**********************************************************************************************
     * Check if the groups differ from those last committed to the database
     *
     * @return true if the group definitions have changed
     *********************************************************************************************/
    private boolean isGroupsChanged()
    {
        // Store any changes to the currently selected group, if applicable
        updateSelectedGroupInformation();

        // Get the current group definitions from the group tree
        currentGroupDefns = groupTree.createDefinitionsFromTree();

        // Initialize the change flag to true if the number of current and committed group
        // definitions differ
        boolean hasChanges = currentGroupDefns.size() != committedGroupDefns.size();

        // Check if the number of groups is the same
        if (!hasChanges)
        {
            // Step through each group definition
            for (int index = 0; index < currentGroupDefns.size(); index++)
            {
                // Check if the current and committed definition differ
                if (!Arrays.equals(currentGroupDefns.get(index), committedGroupDefns.get(index)))
                {
                    // Set the flag indicating a match and stop searching
                    hasChanges = true;
                    break;
                }
            }
        }

        // Get the reference to the updated group information
        List<GroupInformation> currentGroupInfo = groupHandler.getGroupInformation();

        // Create storage for the updated data fields
        updateFields = new ArrayList<List<FieldInformation>>();

        // Step through each current group
        for (GroupInformation currentInfo : currentGroupInfo)
        {
            boolean isDiffers = false;

            // Get the group name
            String groupName = currentInfo.getName();

            // Get a reference to the group's committed information
            GroupInformation committedInfo = groupHandler.getGroupInformationByName(committedGroupInfo,
                                                                                    groupName);

            // Check if the group isn't new
            if (committedInfo != null)
            {
                // Set the flag if the number of fields, field attributes, or field contents have
                // changed
                isDiffers = fieldHandler.isFieldChanged(currentInfo.getFieldInformation(),
                                                        committedInfo.getFieldInformation(),
                                                        true);
            }
            // This is a new group
            else
            {
                // Set the flag indicating that the new group's data fields should be added to the
                // change list
                isDiffers = true;
            }

            // Check if the group is new or an existing group's data field changed
            if (isDiffers)
            {
                // Check if the group has any data fields
                if (!currentInfo.getFieldInformation().isEmpty())
                {
                    // Add the data fields for this group to the list of those changed
                    updateFields.add(currentInfo.getFieldInformation());
                }

                // Set the flag indicating a group has changes
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    /**********************************************************************************************
     * Check if the unsaved changes to an open data field table editor should be ignored
     *
     * @return true if the there are no unsaved data field table editor changes or if the user
     *         chooses to ignore the changes; false if the group has no data field changes, the
     *         data field table editor isn't open, or the user chooses to not ignore the unsaved
     *         changes
     *********************************************************************************************/
    private boolean ignoreFieldTableChanges()
    {
        boolean ignoreChanges = true;

        // Check if changes to the group data fields exist, that the data field table editor is
        // open and has unsaved changes, and that the user elects to cancel the update
        if (!updateFields.isEmpty()
            && fldTblEditor != null
            && fldTblEditor.isShowing()
            && fldTblEditor.isFieldTableChanged()
            && new CcddDialogHandler().showMessageDialog(groupMgr,
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

    /**********************************************************************************************
     * Update the change indicator for the group manager
     *********************************************************************************************/
    private void updateChangeIndicator()
    {
        // Replace the dialog title, appending the change indicator if changes exist
        setTitle(DIALOG_TITLE
                 + (isGroupsChanged()
                                      ? CHANGE_INDICATOR
                                      : ""));
    }

    /**********************************************************************************************
     * Select the group in the group tree for which the selected table in the table tree is a
     * member
     *********************************************************************************************/
    private void selectGroupByTable()
    {
        // Clear any currently selected group(s)
        groupTree.clearSelection();

        // Get the paths of the selected table tree nodes
        TreePath[] selectionPaths = tableTree.getSelectionPaths();

        // Check if a node is selected in the table tree
        if (selectionPaths != null)
        {
            List<String> selectedTables = new ArrayList<String>();
            List<TreePath> paths = new ArrayList<TreePath>();

            // Step through each selected node
            for (TreePath path : selectionPaths)
            {
                // Get the path for the node
                String tablePath = tableTree.removeExtraText(tableTree.getFullVariablePath(path.getPath()));

                // Check if the path is for a table and not a header or filter
                if (!tablePath.isEmpty())
                {
                    // Add the table path to the list of those to match
                    selectedTables.add(tablePath);
                }
            }

            // Check if a table is selected
            if (selectedTables.size() != 0)
            {
                int matchCount = -1;
                ToolTipTreeNode groupNode = null;

                // Step through each element and child of the group tree
                for (Enumeration<?> element = groupTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
                {
                    // Get the node reference
                    ToolTipTreeNode node = (ToolTipTreeNode) element.nextElement();

                    // Check if the node references a group (and not a filter or table)
                    if (node.getLevel() == groupTree.getGroupNodeLevel())
                    {
                        // Store the node referencing the group name and reset the matched tables
                        // counter
                        groupNode = node;
                        matchCount = 0;
                    }
                    // Check if the selected table(s) haven't already been detected in the group
                    // and the node references a table
                    else if (matchCount != -1 && node.getLevel() >= groupTree.getItemNodeLevel())
                    {
                        // Get the full path to the table reference in the group tree
                        String groupTable = groupTree.removeExtraText(groupTree.getFullVariablePath(node.getPath(), 2));

                        // Step through the selected tables
                        for (String selectedTable : selectedTables)
                        {
                            // Check if the group tree table matches the selected table
                            if (groupTable.equals(selectedTable))
                            {
                                // Increment the matched tables counter and stop searching
                                matchCount++;
                                break;
                            }
                        }
                    }

                    // Check if the previous group contains all of the selected tables
                    if (matchCount == selectedTables.size())
                    {
                        // Add the group to the list of those containing the selected table(s)
                        paths.add(CcddCommonTreeHandler.getPathFromNode(groupNode));

                        // Reset the matched tables counter so that any remaining tables in this
                        // group are ignored. When the next group's node is encountered the counter
                        // is reset
                        matchCount = -1;
                    }
                }

                // Check if the table belongs to any group(s)
                if (!paths.isEmpty())
                {
                    // Select the group(s) to which the table belongs
                    groupTree.setSelectionPaths(paths.toArray(new TreePath[0]));
                }
            }
        }
    }
}
