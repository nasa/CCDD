/**
 * CFS Command & Data Dictionary table type editor dialog. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.ValidateCellActionListener;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.ManagerDialogType;

/******************************************************************************
 * CFS Command & Data Dictionary table type editor dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddTableTypeEditorDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final List<CcddTableTypeEditorHandler> typeEditors;
    private CcddTableTypeEditorHandler activeEditor;

    // Define globally so that it can be accessed by the action listeners, etc.
    private JMenu mnFile;
    private JMenuItem mntmNewType;
    private JMenuItem mntmCopyType;
    private JMenuItem mntmRenameType;
    private JMenuItem mntmDeleteType;
    private JMenuItem mntmStore;
    private JMenuItem mntmStoreAll;
    private JMenuItem mntmPrint;
    private JMenuItem mntmClose;
    private JMenuItem mntmUndo;
    private JMenuItem mntmRedo;
    private JMenuItem mntmCopy;
    private JMenuItem mntmPaste;
    private JMenuItem mntmInsert;
    private JMenuItem mntmInsertRow;
    private JMenuItem mntmDeleteRow;
    private JMenuItem mntmMoveUp;
    private JMenuItem mntmMoveDown;
    private JMenuItem mntmClear;
    private JMenuItem mntmManageFields;
    private JMenuItem mntmClearValues;
    private JCheckBoxMenuItem mntmOverwrite;
    private JButton btnClose;
    private JTabbedPane tabbedPane;

    // List containing the table type names for the types that are being
    // updated. If a table type change is stored, any tables of that type with
    // unsaved changes will have the changes removed. This list is used when
    // determining if the user is asked to confirm discarding the table changes
    private final List<String> changedTypes;

    /**************************************************************************
     * Table type editor dialog class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param typeNames
     *            array of table type names
     *************************************************************************/
    CcddTableTypeEditorDialog(CcddMain ccddMain, String[] typeNames)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();

        typeEditors = new ArrayList<CcddTableTypeEditorHandler>();

        // Initialize the list of changed table types
        changedTypes = new ArrayList<String>();

        // Create the table type editor dialog
        initialize(typeNames);
    }

    /**************************************************************************
     * Get the currently active type editor
     * 
     * @return Currently active type editor. Return null if no table types
     *         exist
     *************************************************************************/
    protected CcddTableTypeEditorHandler getTypeEditor()
    {
        return activeEditor;
    }

    /**************************************************************************
     * Get the currently active data field editor dialog
     * 
     * @return Currently active data field editor dialog. Return null if no
     *         table types exist
     *************************************************************************/
    protected CcddFieldEditorDialog getFieldEditorDialog()
    {
        CcddFieldEditorDialog fieldEditorDlg = null;

        // Check if a table type exists
        if (activeEditor != null)
        {
            // Get the table type's field editor dialog reference
            fieldEditorDlg = activeEditor.getFieldEditorDialog();
        }

        return fieldEditorDlg;
    }

    /**************************************************************************
     * Set the active table type editor's name
     * 
     * @param typeName
     *            type name
     *************************************************************************/
    protected void setActiveTypeName(String typeName)
    {
        tabbedPane.setTitleAt(tabbedPane.getSelectedIndex(), typeName);
        activeEditor.setTableTypeName(typeName);
    }

    /**************************************************************************
     * Remove the active tab
     *************************************************************************/
    protected void removeActiveTab()
    {
        // Remove the selected editor for the deleted type from the list, then
        // remove its tab
        typeEditors.remove(tabbedPane.getSelectedIndex());
        tabbedPane.removeTabAt(tabbedPane.getSelectedIndex());
    }

    /**************************************************************************
     * Enable/disable the type editor dialog buttons and commands
     * 
     * @param enable
     *            true to enable the buttons and commands; false to disable
     *************************************************************************/
    @Override
    protected void setControlsEnabled(boolean enable)
    {
        // Set the flag based on the input flag and if a table exists
        boolean enableIfType = enable && !typeEditors.isEmpty();

        // Enable/disable the buttons based on the input flag and if a table
        // exists
        super.setControlsEnabled(enableIfType);

        // Step through the menu bar items
        for (int index = 0; index < getJMenuBar().getComponentCount(); index++)
        {
            // Enable/disable the item based on the input flag and if a table
            // exists
            getJMenuBar().getMenu(index).setEnabled(enableIfType);
        }

        // Set the menu item/button based on the input flag for these items
        // since these are valid even when no table type exists
        mnFile.setEnabled(enable);
        mntmCopyType.setEnabled(enableIfType);
        mntmRenameType.setEnabled(enableIfType);
        mntmDeleteType.setEnabled(enableIfType);
        mntmStore.setEnabled(enableIfType);
        mntmStoreAll.setEnabled(enableIfType);
        mntmPrint.setEnabled(enableIfType);
        btnClose.setEnabled(enable);

        // Set the menu item based on the input flag and if there are any data
        // fields assigned to the table type
        mntmClearValues.setEnabled(enable
                                   && activeEditor != null
                                   && !activeEditor.getFieldHandler().getFieldInformation().isEmpty());
    }

    /**************************************************************************
     * Perform the steps needed following execution of table modification
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     * 
     * 
     * @param updatedEditor
     *            reference to the table type editor where the change(s)
     *            occurred
     * 
     * @param tableNames
     *            array of modified table names
     ************************************************************************/
    protected void doTypeModificationComplete(boolean commandError,
                                              CcddTableTypeEditorHandler updatedEditor,
                                              String[] tableNames)
    {
        // Update the table type editor in which the change(s) occurred
        updatedEditor.doTypeUpdatesComplete(commandError);

        // Check if an error occurred and that a table was modified
        if (!commandError && tableNames != null)
        {
            // If any table editors are open then the displayed columns and
            // data fields need to be updated to match the table type changes.
            // Both the currently displayed and committed values are updated so
            // that when the table editor is closed these changes aren't seen
            // as table changes since they're already committed to the database

            // Step through the open table editor dialogs
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Step through each individual editor
                for (final CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Check if the editor is for the table that was altered
                    if (Arrays.asList(tableNames).contains(editor.getTableInformation().getTablePath()))
                    {
                        // Load the data for the table
                        TableInformation tableInfo = dbTable.loadTableData(editor.getTableInformation().getTablePath(),
                                                                           editor.getTableInformation().isRootStructure(),
                                                                           true,
                                                                           true,
                                                                           true,
                                                                           CcddTableTypeEditorDialog.this);

                        // Check that no error occurred loading the table's
                        // data
                        if (!tableInfo.isErrorFlag())
                        {
                            // Update the table editor for the table type
                            // change
                            editor.updateForTableTypeChange(tableInfo);
                        }
                        // An error occurred loading the table's data
                        else
                        {
                            // Close the editor for this table since it can't
                            // be updated to the new information
                            editorDialog.closeTableEditor(editor.getTableEditorOwnerName());
                        }
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Create the table type editor dialog. This is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The
     * GUI menu commands, however, are disabled until the telemetry scheduler
     * initialization completes execution
     * 
     * @param typeNames
     *            array containing the table type names
     *************************************************************************/
    private void initialize(final String[] typeNames)
    {
        // Build the table type editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panel to hold the dialog buttons
            JPanel buttonPnl = new JPanel();

            /******************************************************************
             * Build the table type editor dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Menu
                // ////////////////////////////////////////////////////////////
                // Create the table menu bar
                JMenuBar menuBar = new JMenuBar();
                setJMenuBar(menuBar);

                // Create the File menu and menu items
                mnFile = ccddMain.createMenu(menuBar, "File", KeyEvent.VK_F, 1, null);
                mntmNewType = ccddMain.createMenuItem(mnFile, "New type", KeyEvent.VK_N, 1, "Create a new table type");
                mntmCopyType = ccddMain.createMenuItem(mnFile, "Copy type", KeyEvent.VK_O, 1, "Copy the current table type");
                mntmRenameType = ccddMain.createMenuItem(mnFile, "Rename type", KeyEvent.VK_R, 1, "Rename the current table type");
                mntmDeleteType = ccddMain.createMenuItem(mnFile, "Delete type", KeyEvent.VK_D, 1, "Delete the current table type");
                mnFile.addSeparator();
                mntmStore = ccddMain.createMenuItem(mnFile, "Store current", KeyEvent.VK_U, 1, "Store changes to the curent table type in the database");
                mntmStoreAll = ccddMain.createMenuItem(mnFile, "Store all", KeyEvent.VK_L, 1, "Store changes to all table types in the database");
                mnFile.addSeparator();
                mntmPrint = ccddMain.createMenuItem(mnFile, "Print current", KeyEvent.VK_P, 1, "Print the current table type information");
                mnFile.addSeparator();
                mntmClose = ccddMain.createMenuItem(mnFile, "Close", KeyEvent.VK_C, 1, "Close the table type editor");

                // Create the Edit menu and menu items
                JMenu mnEdit = ccddMain.createMenu(menuBar, "Edit", 1, KeyEvent.VK_E, null);
                mntmCopy = ccddMain.createMenuItem(mnEdit, "Copy", KeyEvent.VK_O, 1, "Copy the currently selected cell(s) to the clipboard");
                mntmPaste = ccddMain.createMenuItem(mnEdit, "Paste", KeyEvent.VK_V, 1, "Paste the clipboard contents at the current focus location");
                mntmInsert = ccddMain.createMenuItem(mnEdit, "Insert", KeyEvent.VK_I, 1, "Insert the clipboard contents at the current focus location");
                mnEdit.addSeparator();
                mntmUndo = ccddMain.createMenuItem(mnEdit, "Undo", KeyEvent.VK_Z, 1, "Undo the last edit operation");
                mntmRedo = ccddMain.createMenuItem(mnEdit, "Redo", KeyEvent.VK_Y, 1, "Redo the last undone edit operation");
                mnEdit.addSeparator();
                mntmClear = ccddMain.createMenuItem(mnEdit, "Clear data", KeyEvent.VK_L, 1, "Clear the current table type contents");

                // Create the Row menu and menu items
                JMenu mnRow = ccddMain.createMenu(menuBar, "Row", KeyEvent.VK_R, 1, null);
                mntmInsertRow = ccddMain.createMenuItem(mnRow, "Insert row", KeyEvent.VK_I, 1, "Insert a row below the current focus location");
                mntmDeleteRow = ccddMain.createMenuItem(mnRow, "Delete row(s)", KeyEvent.VK_D, 1, "Delete the currently selected row(s)");
                mnRow.addSeparator();
                mntmMoveUp = ccddMain.createMenuItem(mnRow, "Move up", KeyEvent.VK_U, 1, "Move the currently selected row(s) up one row");
                mntmMoveDown = ccddMain.createMenuItem(mnRow, "Move down", KeyEvent.VK_N, 1, "Move the currently selected row(s) down one row");

                // Create the Field menu and menu items
                JMenu mnField = ccddMain.createMenu(menuBar, "Field", KeyEvent.VK_L, 1, null);
                mntmManageFields = ccddMain.createMenuItem(mnField, "Manage fields", KeyEvent.VK_M, 1, "Open the data field manager");
                mntmClearValues = ccddMain.createMenuItem(mnField, "Clear values", KeyEvent.VK_C, 1, "Clear the data field values");
                mntmOverwrite = ccddMain.createCheckBoxMenuItem(mnField, "Overwrite values", KeyEvent.VK_O, 1, "Overwrite/don't overwrite existing data field values", false);

                // Add a listener for the New Type command
                mntmNewType.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Create a new table type
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddTableTypeManagerDialog(ccddMain,
                                                       CcddTableTypeEditorDialog.this,
                                                       ManagerDialogType.NEW);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Copy Type command
                mntmCopyType.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Copy a table type
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddTableTypeManagerDialog(ccddMain,
                                                       CcddTableTypeEditorDialog.this,
                                                       ManagerDialogType.COPY);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }

                });

                // Add a listener for the Rename Type command
                mntmRenameType.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Rename a table type
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddTableTypeManagerDialog(ccddMain,
                                                       CcddTableTypeEditorDialog.this,
                                                       ManagerDialogType.RENAME);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Delete Type command
                mntmDeleteType.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Delete a table type
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddTableTypeManagerDialog(ccddMain,
                                                       CcddTableTypeEditorDialog.this,
                                                       ManagerDialogType.DELETE);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Print command
                mntmPrint.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Output the type to the printer
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getTable().printTable("Table Type: "
                                                           + activeEditor.getTypeName(),
                                                           activeEditor.getFieldHandler(),
                                                           CcddTableTypeEditorDialog.this,
                                                           PageFormat.LANDSCAPE);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Copy command
                mntmCopy.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Copy the selected table cell(s) contents into the
                     * clipboard
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Send a Ctrl-C key press
                        controlKeyAction(KeyEvent.VK_C);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Paste command
                mntmPaste.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Paste the clipboard contents in the table, overwriting
                     * any existing data in the target cells and adding new
                     * rows at the end of the table if needed
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Send a Ctrl-V key press
                        controlKeyAction(KeyEvent.VK_V);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Insert command
                mntmInsert.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Insert the clipboard contents in the table, creating new
                     * rows to contain the data
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Send a ctrl-I key press
                        controlKeyAction(KeyEvent.VK_I);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Clear command
                mntmClear.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Clear the table contents
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Check if there are any rows to clear
                        if (activeEditor.getTable().getModel().getRowCount() != 0)
                        {
                            // Confirm clearing the table values
                            if (new CcddDialogHandler().showMessageDialog(CcddTableTypeEditorDialog.this,
                                                                          "<html><b>Clear the table contents?",
                                                                          "Clear Table",
                                                                          JOptionPane.QUESTION_MESSAGE,
                                                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                            {
                                // Remove all of the rows from the table
                                activeEditor.getTable().removeRows(0,
                                                                   activeEditor.getTable().getModel().getRowCount() - 1);
                            }
                        }
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Store All command
                mntmStoreAll.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Store the changes to all open table contents, if any, in
                     * the database
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update if one or more of the table's has
                        // changes and the user confirms the action
                        if (isTypesChanged()
                            && new CcddDialogHandler().showMessageDialog(CcddTableTypeEditorDialog.this,
                                                                         "<html><b>Store changes for all?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                            && ccddMain.ignoreUncommittedChanges("Store Changes",
                                                                 "Discard table changes?",
                                                                 false,
                                                                 changedTypes,
                                                                 CcddTableTypeEditorDialog.this))
                        {
                            // Commit the changes for all of the editors
                            storeAllChanges();
                        }
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Manage Fields command
                mntmManageFields.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Manage the data fields
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        new CcddFieldEditorDialog(activeEditor,
                                                  activeEditor.getTypeName(),
                                                  tableTypeHandler.getTypeDefinition(activeEditor.getTypeName()).isStructure());

                        // Enable/disable the Clear values command depending on
                        // if any data fields remain
                        mntmClearValues.setEnabled(!activeEditor.getFieldHandler().getFieldInformation().isEmpty());
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Add a listener for the Clear Values command
                mntmClearValues.addActionListener(new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Clear the table data field values
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Check if there are any data fields to clear
                        if (!activeEditor.getFieldHandler().getFieldInformation().isEmpty())
                        {
                            // Remove all of the data field values
                            activeEditor.getEditPanelHandler().clearFieldValues();
                        }
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                });

                // Insert new row button
                JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                           INSERT_ICON,
                                                                           KeyEvent.VK_I,
                                                                           "Insert a new row into the table");

                // Create a listener for the Insert Row command
                ActionListener insertAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Insert a new row into the table at the selected location
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getTable().insertEmptyRow(true);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the insert listener to the Insert Row button and menu
                // command
                btnInsertRow.addActionListener(insertAction);
                mntmInsertRow.addActionListener(insertAction);

                // Delete row button
                JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                           DELETE_ICON,
                                                                           KeyEvent.VK_D,
                                                                           "Delete the selected row(s) from the table");

                // Create a listener for the Delete Row command
                ActionListener deleteAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Delete the selected row(s) from the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {

                        activeEditor.getTable().deleteRow(true);
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the delete listener to the Delete Row button and menu
                // command
                btnDeleteRow.addActionListener(deleteAction);
                mntmDeleteRow.addActionListener(deleteAction);

                // Move Up button
                JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                        UP_ICON,
                                                                        KeyEvent.VK_U,
                                                                        "Move the selected row(s) up");

                // Create a listener for the Move Up command
                ActionListener moveUpAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Move the selected row(s) up in the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getTable().moveRowUp();
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the move up listener to the Move Up button and menu
                // command
                btnMoveUp.addActionListener(moveUpAction);
                mntmMoveUp.addActionListener(moveUpAction);

                // Move Down button
                JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                          DOWN_ICON,
                                                                          KeyEvent.VK_W,
                                                                          "Move the selected row(s) down");

                // Create a listener for the Move Down command
                ActionListener moveDownAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Move the selected row(s) down in the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getTable().moveRowDown();
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the move down listener to the Move Down button and menu
                // command
                btnMoveDown.addActionListener(moveDownAction);
                mntmMoveDown.addActionListener(moveDownAction);

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit action");

                // Create a listener for the Undo command
                ActionListener undoAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Undo the last cell edit
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getEditPanelUndoManager().undo();

                        // Update the data field background colors
                        activeEditor.getEditPanelHandler().setFieldBackgound();
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the undo listener to the Undo button and menu command
                mntmUndo.addActionListener(undoAction);
                btnUndo.addActionListener(undoAction);

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last udone edit action");

                // Create a listener for the Redo command
                ActionListener redoAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Redo the last cell edit that was undone
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        activeEditor.getEditPanelUndoManager().redo();

                        // Update the data field background colors
                        activeEditor.getEditPanelHandler().setFieldBackgound();
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the redo listener to the Redo button and menu command
                mntmRedo.addActionListener(redoAction);
                btnRedo.addActionListener(redoAction);

                // Store button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store the table type updates in the database");

                // Create a listener for the Store command
                ActionListener storeAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Store the changes to the table contents, if any, in the
                     * database
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Set the list of changed table types to the active
                        // editor's type
                        changedTypes.clear();
                        changedTypes.add(activeEditor.getTypeName());

                        // Only update the table in the database if a cell's
                        // content has changed, none of the required columns is
                        // missing a value, and the user confirms the action
                        if (activeEditor.isTableChanged()
                            && !activeEditor.checkForMissingColumns()
                            && new CcddDialogHandler().showMessageDialog(CcddTableTypeEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON
                            && ccddMain.ignoreUncommittedChanges("Store Changes",
                                                                 "Discard table changes?",
                                                                 false,
                                                                 changedTypes,
                                                                 CcddTableTypeEditorDialog.this))
                        {
                            // Store the changes for the currently displayed
                            // editor in the database
                            storeChanges(activeEditor);
                        }
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the store listener to the Store button and menu command
                btnStore.addActionListener(storeAction);
                mntmStore.addActionListener(storeAction);

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the table type editor");

                // Add a listener for the Close table type editor command
                ActionListener closeAction = new ValidateCellActionListener()
                {
                    /**********************************************************
                     * Close the type editor
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        windowCloseButtonAction();
                    }

                    /**********************************************************
                     * Get the table for which the action is performed
                     * 
                     * @return Table for which the action is performed
                     *********************************************************/
                    @Override
                    protected CcddJTableHandler getTable()
                    {
                        return getActiveTable();
                    }
                };

                // Add the close listener to the Close button and menu command
                btnClose.addActionListener(closeAction);
                mntmClose.addActionListener(closeAction);

                // Add buttons in the order in which they'll appear (left to
                // right, top to bottom)
                buttonPnl.add(btnInsertRow);
                buttonPnl.add(btnMoveUp);
                buttonPnl.add(btnUndo);
                buttonPnl.add(btnStore);
                buttonPnl.add(btnDeleteRow);
                buttonPnl.add(btnMoveDown);
                buttonPnl.add(btnRedo);
                buttonPnl.add(btnClose);

                // Distribute the buttons across two rows
                setButtonRows(2);

                // Table Editors
                // ////////////////////////////////////////////////////////////
                // Create a tabbed pane for the editors to appear in
                tabbedPane = new JTabbedPane(JTabbedPane.TOP);
                tabbedPane.setFont(LABEL_FONT_BOLD);

                // Listen for tab selection changes
                tabbedPane.addChangeListener(new ChangeListener()
                {
                    /**********************************************************
                     * Update the editor to the one associated with the
                     * selected tab
                     *********************************************************/
                    @Override
                    public void stateChanged(ChangeEvent ce)
                    {
                        // Check if a table type exists
                        if (!typeEditors.isEmpty())
                        {
                            // Set the active editor to the one indicated by
                            // the currently selected tab
                            activeEditor = typeEditors.get(tabbedPane.getSelectedIndex());
                        }
                        // No table type exists
                        else
                        {
                            // initialize the active editor to null
                            activeEditor = null;
                        }

                        // Update the editor controls state
                        setControlsEnabled(!typeEditors.isEmpty());
                    }
                });

                // Add each table as a tab in the editor dialog tabbed pane
                addTypePanes(typeNames,
                             dbTable.retrieveInformationTable(InternalTable.FIELDS,
                                                              CcddTableTypeEditorDialog.this));

                // Check if a table type exists
                if (!typeEditors.isEmpty())
                {
                    // Set the first tab as the active editor
                    activeEditor = typeEditors.get(0);
                }
                // No table type exists
                else
                {
                    // initialize the active editor to null
                    activeEditor = null;
                }
            }

            /******************************************************************
             * Table type editor dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Display the table type editor dialog
                createFrame(ccddMain.getMainFrame(),
                            tabbedPane,
                            buttonPnl,
                            "Table Type Editor",
                            null);

                // Enable the editor controls
                setControlsEnabled(true);
            }
        });
    }

    /**************************************************************************
     * Get a reference to the active table type's table handler
     * 
     * @return Reference to the active table type's table handler. Return null
     *         if no active editor (i.e., no table type) exists
     *************************************************************************/
    private CcddJTableHandler getActiveTable()
    {
        CcddJTableHandler table = null;

        // Check if a table type exists
        if (activeEditor != null)
        {
            // Get the active table type's table handler
            table = activeEditor.getTable();
        }

        return table;
    }

    /**************************************************************************
     * Determine if any of the table types represented in this editor dialog
     * have uncommitted changes
     * 
     * @return true if any of the editors represented in this editor dialog
     *         have uncommitted changes
     *************************************************************************/
    private boolean isTablesChanged()
    {
        boolean isChanged = false;

        // Step through each editor
        for (CcddTableTypeEditorHandler editor : typeEditors)
        {
            // Check if the table has changed
            if (editor.isTableChanged())
            {
                // Set the flag indicated uncommitted changes and stop
                // searching
                isChanged = true;
                break;
            }
        }

        return isChanged;
    }

    /**************************************************************************
     * Handle the frame close button press event
     *************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the specified table
        // is validated and that there are no uncommitted changes. If a change
        // exists then confirm discarding the changes
        if (activeEditor == null
            || (activeEditor.getTable().isLastCellValid()
            && (!isTablesChanged()
            || new CcddDialogHandler().showMessageDialog(CcddTableTypeEditorDialog.this,
                                                         "<html><b>Discard changes?",
                                                         "Discard Changes",
                                                         JOptionPane.QUESTION_MESSAGE,
                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)))
        {
            // Close the editor dialog
            closeFrame();
        }
    }

    /**************************************************************************
     * Perform a control key action
     * 
     * @param key
     *            key to simulate pressing along with the control key
     *************************************************************************/
    private void controlKeyAction(int key)
    {
        try
        {
            // Create a robot to simulate key press events
            Robot robot = new Robot();

            // Send the equivalent Ctrl+<key> event
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(key);
            robot.keyRelease(key);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }
        catch (AWTException awte)
        {
            // Inform the user that key presses cannot be simulated
            new CcddDialogHandler().showMessageDialog(CcddTableTypeEditorDialog.this,
                                                      "<html><b>Platform does not allow key press simulation",
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**************************************************************************
     * Commit changes to the database for the specified table type editor
     * 
     * @param editor
     *            table type editor to commit
     *************************************************************************/
    private void storeChanges(CcddTableTypeEditorHandler editor)
    {
        // Build the table updates based on the type definition changes
        editor.buildUpdates();

        // Recreate the table type definitions table in the database and update
        // the affected table(s)
        dbTable.modifyTableTypeInBackground(editor.getTypeName(),
                                            editor.getFieldHandler().getFieldInformation(),
                                            mntmOverwrite.isSelected(),
                                            editor.getTypeAdditions(),
                                            editor.getTypeModifications(),
                                            editor.getTypeDeletions(),
                                            editor.getColumnOrderChange(),
                                            editor.getTypeDefinition(),
                                            CcddTableTypeEditorDialog.this,
                                            activeEditor);
    }

    /**************************************************************************
     * Commit changes to the database for every table in the editor that has
     * changes. This command is executed in a separate thread since it can take
     * a noticeable amount time to complete, and by using a separate thread the
     * GUI is allowed to continue to update. The GUI menu commands, however,
     * are disabled until the database command completes execution
     *************************************************************************/
    private void storeAllChanges()
    {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            /******************************************************************
             * Update the database for every table that has changes
             *****************************************************************/
            @Override
            protected void execute()
            {
                // Step through each table type in this editor dialog
                for (CcddTableTypeEditorHandler editor : typeEditors)
                {
                    // Check if the table has changes
                    if (editor.isTableChanged())
                    {
                        // Build the addition, modification, and deletion
                        // command lists
                        editor.buildUpdates();

                        // Perform the changes to the table in the database
                        dbTable.modifyTableType(editor.getTypeName(),
                                                editor.getFieldHandler().getFieldInformation(),
                                                mntmOverwrite.isSelected(),
                                                editor.getTypeAdditions(),
                                                editor.getTypeModifications(),
                                                editor.getTypeDeletions(),
                                                editor.getColumnOrderChange(),
                                                editor.getTypeDefinition(),
                                                CcddTableTypeEditorDialog.this,
                                                editor);
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Add one or more table type tabs to the editor dialog tabbed pane
     * 
     * @param typeNames
     *            array of table type names
     * 
     * @param fieldDefinitions
     *            list of data field definitions
     *************************************************************************/
    protected void addTypePanes(String[] typeNames,
                                List<String[]> fieldDefinitions)
    {
        // Step through the table types
        for (String name : typeNames)
        {
            // Create an editor for this table type and add it to the list of
            // editors
            final CcddTableTypeEditorHandler editor = new CcddTableTypeEditorHandler(ccddMain,
                                                                                     name,
                                                                                     fieldDefinitions.toArray(new Object[0][0]),
                                                                                     this);
            typeEditors.add(editor);

            // Create a tab for each table type
            tabbedPane.addTab(editor.getTypeName(),
                              null,
                              editor.getEditorPanel(),
                              null);
        }

        // Check if only a single type was added
        if (typeNames.length == 1)
        {
            // Select the tab for the newly added type
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        }
    }

    /**************************************************************************
     * Update the change indicator for the current table type editor
     *************************************************************************/
    protected void updateChangeIndicator()
    {
        // Get the index of the currently displayed tab
        int index = tabbedPane.getSelectedIndex();

        // Check that the tab index is valid
        if (index != -1)
        {
            // Replace the tab name, appending the change indicator if changes
            // exist
            tabbedPane.setTitleAt(index,
                                  tabbedPane.getTitleAt(index).replaceAll("\\*", "")
                                      + (typeEditors.get(index).isTableChanged()
                                                                                ? "*"
                                                                                : ""));
        }
    }

    /**************************************************************************
     * Determine if any of the table types have uncommitted changes. Store the
     * names of the changed table types in a list
     * 
     * @return true if any of the table types have uncommitted changes
     *************************************************************************/
    protected boolean isTypesChanged()
    {
        boolean isChanged = false;
        changedTypes.clear();

        // Step through each editor
        for (CcddTableTypeEditorHandler editor : typeEditors)
        {
            // Check if the table has changed
            if (editor.isTableChanged())
            {
                // Set the flag indicated uncommitted changes
                isChanged = true;

                // Add the table type name to the list of changed types
                changedTypes.add(editor.getTypeName());
            }
        }

        return isChanged;
    }
}
