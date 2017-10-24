/**
 * CFS Command & Data Dictionary data table editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.LEFT_ICON;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.RIGHT_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.JTextComponent;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableModification;
import CCDD.CcddClasses.ValidateCellActionListener;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.SearchDialogType;

/******************************************************************************
 * CFS Command & Data Dictionary data table editor dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddTableEditorDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddFileIOHandler fileIOHandler;
    private CcddTableEditorHandler activeEditor;
    private final List<CcddTableEditorHandler> tableEditors;

    // Components that need to be accessed by multiple methods
    private JMenuItem mntmOpen;
    private JMenuItem mntmOpenPrototype;
    private JMenuItem mntmStore;
    private JMenuItem mntmStoreAll;
    private JMenuItem mntmImport;
    private JMenuItem mntmExportCSV;
    private JMenuItem mntmExportEDS;
    private JMenuItem mntmExportJSON;
    private JMenuItem mntmExportXTCE;
    private JMenuItem mntmPrint;
    private JMenuItem mntmSearchTable;
    private JMenuItem mntmCloseActive;
    private JMenuItem mntmCloseAll;
    private JMenuItem mntmUndo;
    private JMenuItem mntmRedo;
    private JMenuItem mntmCopy;
    private JMenuItem mntmPaste;
    private JMenuItem mntmInsertMacro;
    private JCheckBoxMenuItem mntmShowMacros;
    private JMenuItem mntmWithBlanks;
    private JMenuItem mntmWithPrototype;
    private JMenuItem mntmInsert;
    private JMenuItem mntmInsertRow;
    private JMenuItem mntmDeleteRow;
    private JCheckBoxMenuItem mntmExpColArray;
    private JRadioButtonMenuItem mntmOverwriteAll;
    private JRadioButtonMenuItem mntmOverwriteEmpty;
    private JRadioButtonMenuItem mntmOverwriteNone;
    private JMenuItem mntmMoveUp;
    private JMenuItem mntmMoveDown;
    private JMenuItem mntmMoveLeft;
    private JMenuItem mntmMoveRight;
    private JMenuItem mntmResetOrder;
    private JMenuItem mntmManageFields;
    private JMenuItem mntmClearValues;
    private JButton btnInsertRow;
    private JButton btnDeleteRow;
    private JButton btnMoveUp;
    private JButton btnMoveDown;
    private JButton btnMoveLeft;
    private JButton btnMoveRight;
    private JButton btnUndo;
    private JButton btnRedo;
    private JButton btnStore;
    private JButton btnCloseActive;
    private JTabbedPane tabbedPane;

    /**************************************************************************
     * Table editor dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInformation
     *            list containing the information for each table
     *************************************************************************/
    protected CcddTableEditorDialog(CcddMain ccddMain,
                                    List<TableInformation> tableInformation)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        fileIOHandler = ccddMain.getFileIOHandler();
        tableEditors = new ArrayList<CcddTableEditorHandler>();

        // Create the data table editor dialog
        initialize(tableInformation);
    }

    /**************************************************************************
     * Get the currently active table editor
     *
     * @return Currently active table editor
     *************************************************************************/
    protected CcddTableEditorHandler getTableEditor()
    {
        return activeEditor;
    }

    /**************************************************************************
     * Get the list of table editors
     *
     * @return List of table editors
     *************************************************************************/
    protected List<CcddTableEditorHandler> getTableEditors()
    {
        return tableEditors;
    }

    /**************************************************************************
     * Get the tabbed pane
     *
     * @return Tabbed pane
     *************************************************************************/
    protected JTabbedPane getTabbedPane()
    {
        return tabbedPane;
    }

    /**************************************************************************
     * Set the specified tab's title and tool tip text
     *
     * @param tabIndex
     *            index of the tab to change
     *
     * @param tabName
     *            tab title
     *
     * @param tabToolTip
     *            tab tool tip text
     *************************************************************************/
    private void setTabText(int tabIndex, String tabName, String tabToolTip)
    {
        tabbedPane.setTitleAt(tabIndex, tabName);
        tabbedPane.setToolTipTextAt(tabIndex,
                                    CcddUtilities.wrapText(tabToolTip,
                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
    }

    /**************************************************************************
     * Remove the tab for the specified table
     *
     * @param tableName
     *            name of the table to remove
     *************************************************************************/
    protected void closeTableEditor(String tableName)
    {
        // Check if there is only a single table editor is open
        if (tabbedPane.getTabCount() == 1)
        {
            // Close the table editor dialog
            closeFrame();
        }
        // More than one table editor is open
        else
        {
            int index = 0;

            // Step through each table editor
            for (CcddTableEditorHandler editor : tableEditors)
            {
                // Check if the table name matches the specified name
                if (tableName.equals(editor.getOwnerName()))
                {
                    // Remove the table's editor and tab, and stop searching
                    tableEditors.remove(editor);
                    tabbedPane.removeTabAt(index);
                    break;
                }

                index++;
            }
        }
    }

    /**************************************************************************
     * Enable/disable the table editor dialog menu controls
     *
     * @param enable
     *            true to enable the controls; false to disable
     *************************************************************************/
    @Override
    protected void setControlsEnabled(boolean enable)
    {
        // Set the flag for controls that are enabled only for a prototype or
        // parent table
        boolean enableParent = enable
                               && activeEditor.getTableInformation().isPrototype();

        // Set the flag for controls that are enabled only for a non-prototype
        // and non-parent table
        boolean enableChild = enable
                              && !activeEditor.getTableInformation().isPrototype();

        // Set the menu item enable status
        mntmOpen.setEnabled(enable);
        mntmOpenPrototype.setEnabled(enableChild);
        mntmStore.setEnabled(enable);
        mntmStoreAll.setEnabled(enable);
        mntmImport.setEnabled(enable);
        mntmExportCSV.setEnabled(enable);
        mntmExportEDS.setEnabled(enable);
        mntmExportJSON.setEnabled(enable);
        mntmExportXTCE.setEnabled(enable);
        mntmPrint.setEnabled(enable);
        mntmSearchTable.setEnabled(enable);
        mntmCloseActive.setEnabled(enable || mntmShowMacros.isSelected());
        mntmCloseAll.setEnabled(enable || mntmShowMacros.isSelected());
        mntmUndo.setEnabled(enable);
        mntmRedo.setEnabled(enable);
        mntmCopy.setEnabled(enable);
        mntmPaste.setEnabled(enableParent);
        mntmInsertMacro.setEnabled(enable);
        mntmShowMacros.setEnabled(enable || mntmShowMacros.isSelected());
        mntmInsert.setEnabled(enable);
        mntmInsertRow.setEnabled(enableParent);
        mntmDeleteRow.setEnabled(enableParent);
        mntmExpColArray.setEnabled(enable && activeEditor.isCanHaveArrays());
        mntmOverwriteAll.setEnabled(enable && activeEditor.isCanHaveArrays());
        mntmOverwriteEmpty.setEnabled(enable && activeEditor.isCanHaveArrays());
        mntmOverwriteNone.setEnabled(enable && activeEditor.isCanHaveArrays());
        mntmMoveUp.setEnabled(enableParent);
        mntmMoveDown.setEnabled(enableParent);
        mntmMoveLeft.setEnabled(enable || mntmShowMacros.isSelected());
        mntmMoveRight.setEnabled(enable || mntmShowMacros.isSelected());
        mntmResetOrder.setEnabled(enable || mntmShowMacros.isSelected());
        mntmWithBlanks.setEnabled(enable);
        mntmWithPrototype.setEnabled(enableChild);
        mntmManageFields.setEnabled(enable);
        mntmClearValues.setEnabled(enable
                                   && !activeEditor.getDataFieldHandler().getFieldInformation().isEmpty());

        // Set the button enable status
        btnInsertRow.setEnabled(enableParent);
        btnDeleteRow.setEnabled(enableParent);
        btnMoveUp.setEnabled(enableParent);
        btnMoveDown.setEnabled(enableParent);
        btnMoveLeft.setEnabled(enable || mntmShowMacros.isSelected());
        btnMoveRight.setEnabled(enable || mntmShowMacros.isSelected());
        btnUndo.setEnabled(enable);
        btnRedo.setEnabled(enable);
        btnStore.setEnabled(enable);
        btnCloseActive.setEnabled(enable || mntmShowMacros.isSelected());
    }

    /**************************************************************************
     * Perform the steps needed following execution of a table modification.
     * This includes closing child table editors that are no longer valid and
     * modifying child tables when a prototype table is changed
     *
     * @param main
     *            reference to CcddMain
     *
     * @param dbTblCmdHndlr
     *            reference to CcddDbTableCommandHandler
     *
     * @param newKeys
     *            list of primary keys for new rows in a prototype table
     *
     * @param tableInfo
     *            table information
     *
     * @param additions
     *            list of row addition information
     *
     * @param modifications
     *            list of row update information
     *
     * @param deletions
     *            list of row deletion information
     *
     * @param forceUpdate
     *            true to make the changes to other tables; false to only make
     *            changes to tables other than the one in which the changes
     *            originally took place
     ************************************************************************/
    protected static void doTableModificationComplete(CcddMain main,
                                                      List<Integer> newKeys,
                                                      TableInformation tableInfo,
                                                      List<TableModification> additions,
                                                      List<TableModification> modifications,
                                                      List<TableModification> deletions,
                                                      boolean forceUpdate)
    {
        CcddDataTypeHandler dtHandler = main.getDataTypeHandler();
        CcddDbTableCommandHandler dbTblCmdHndlr = main.getDbTableCommandHandler();

        // Create a list to store the names of tables that are no longer valid
        List<String[]> invalidatedEditors = new ArrayList<String[]>();

        // Step through each row modification
        for (TableModification mod : modifications)
        {
            // Check if the variable's original data type was a structure
            // (meaning it could have a table editor open) and if (1) it has
            // been changed to an array or if (2) the data type has been
            // changed
            if (mod.getVariableColumn() != -1
                && mod.getDataTypeColumn() != -1
                && !dtHandler.isPrimitive(mod.getOriginalRowData()[mod.getDataTypeColumn()].toString())
                && ((mod.getArraySizeColumn() != -1
                     && mod.getOriginalRowData()[mod.getArraySizeColumn()].toString().isEmpty()
                     && !mod.getRowData()[mod.getArraySizeColumn()].toString().isEmpty())
                    || !mod.getOriginalRowData()[mod.getDataTypeColumn()].toString().equals(mod.getRowData()[mod.getDataTypeColumn()].toString())))
            {
                // Add the pattern that matches the table editor tab names for
                // the modified structure. The pattern is [parent
                // structure].__,[original structure data type].[original
                // structure variable name][,__]
                invalidatedEditors.add(new String[] {tableInfo.getPrototypeName(),
                                                     mod.getOriginalRowData()[mod.getDataTypeColumn()].toString()
                                                                                   + "."
                                                                                   + mod.getOriginalRowData()[mod.getVariableColumn()].toString()});
            }
        }

        // Step through each row deletion
        for (TableModification del : deletions)
        {
            // Check if the original data type was for a structure
            if (del.getVariableColumn() != -1
                && del.getDataTypeColumn() != -1
                && !dtHandler.isPrimitive(del.getRowData()[del.getDataTypeColumn()].toString()))
            {
                // Add the pattern that matches the table editor tab names for
                // the deleted structure. The pattern is [parent
                // structure].__,[structure data type].[structure variable
                // name][,__]
                invalidatedEditors.add(new String[] {tableInfo.getPrototypeName(),
                                                     del.getRowData()[del.getDataTypeColumn()].toString()
                                                                                   + "."
                                                                                   + del.getRowData()[del.getVariableColumn()].toString()});
            }
        }

        // Close the invalid table editors
        dbTblCmdHndlr.closeDeletedTableEditors(invalidatedEditors, main.getMainFrame());

        // Update the tables with message names & IDs columns
        dbTblCmdHndlr.updateMessageIDNamesColumns(main.getMainFrame());

        // Step through the open editor dialogs
        for (CcddTableEditorDialog editorDialog : main.getTableEditorDialogs())
        {
            // Step through each individual editor
            for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
            {
                // Check if the prototype of the editor's table matches that of
                // the table that was updated
                if (editor.getTableInformation().getPrototypeName().equals(tableInfo.getPrototypeName()))
                {
                    // Flag that indicates true if a forced update is set (such
                    // as when a macro name or value is changed), or if the
                    // updated table is a prototype and the editor is for a
                    // child table of the updated table
                    boolean applyToChild = forceUpdate
                                           || (tableInfo.isPrototype()
                                               && !tableInfo.equals(editor.getTableInformation()));

                    // Check if no link handler is already created and this
                    // Perform the command completion steps for this table
                    editor.doTableUpdatesComplete(newKeys,
                                                  applyToChild,
                                                  additions,
                                                  modifications,
                                                  deletions);
                }

                // Check if this is the editor for the table that was changed
                if (tableInfo.equals(editor.getTableInformation()))
                {
                    // Replace any custom value deletion flags with blanks
                    editor.clearCustomValueDeletionFlags();

                    // Accept all edits for this table
                    editor.getTable().getUndoManager().discardAllEdits();
                }

                // Step through each row modification
                for (TableModification mod : modifications)
                {
                    // Check if the modification contains a variable name and
                    // data type columns; this implies it could be a structure
                    // table reference
                    if (mod.getVariableColumn() != -1
                        && mod.getDataTypeColumn() != -1)
                    {
                        // Update the table names in the open editors
                        updateTableNames(main,
                                         mod.getOriginalRowData()[mod.getDataTypeColumn()].toString(),
                                         mod.getRowData()[mod.getDataTypeColumn()].toString(),
                                         mod.getOriginalRowData()[mod.getVariableColumn()].toString(),
                                         mod.getRowData()[mod.getVariableColumn()].toString(),
                                         editorDialog,
                                         editor);
                    }
                }
            }
        }

        // Check if the data field editor table dialog is open
        if (main.getFieldTableEditor() != null
            && main.getFieldTableEditor().isShowing())
        {
            // Update the data field editor table
            main.getFieldTableEditor().reloadDataFieldTable();
        }
    }

    /**************************************************************************
     * Update the table name, editor dialog tab, and editor dialog frame when
     * the prototype name (data type for a structure table) or the variable
     * name for a structure type table is changed
     *
     * @param main
     *            reference to CcddMain
     *
     * @param oldPrototype
     *            original prototype name (same as the data type for a
     *            structure table)
     *
     * @param newPrototype
     *            current prototype name (same as the data type for a structure
     *            table)
     *
     * @param oldVariableName
     *            original variable name if this change is for a structure
     *            table
     *
     * @param newVariableName
     *            current variable name if this change is for a structure table
     *
     * @param editorDialog
     *            current editor dialog to which the changes are compared
     *
     * @param editor
     *            current editor to which the changes are compared
     *************************************************************************/
    protected static void updateTableNames(CcddMain main,
                                           String oldPrototype,
                                           String newPrototype,
                                           String oldVariableName,
                                           String newVariableName,
                                           CcddTableEditorDialog editorDialog,
                                           CcddTableEditorHandler editor)
    {
        CcddDataTypeHandler dtHandler = main.getDataTypeHandler();

        // Get the prototype + variable name from that editor owner name by
        // stripping off the root name and separator
        String protoVarName = editor.getOwnerName().substring(editor.getOwnerName().indexOf(" ") + 1);

        // Set flag to true if the prototype changed and isn't/wasn't a
        // primitive variable
        boolean isRename = !oldPrototype.equals(newPrototype)
                           && !dtHandler.isPrimitive(oldPrototype)
                           && !dtHandler.isPrimitive(newPrototype);

        // Check if the prototype name changed
        if (isRename && oldVariableName == null)
        {
            // Update the variable data types to match the change, if this
            // table represents a structure
            editor.updateDataTypeReferences(oldPrototype, newPrototype);
        }

        // Check if the prototype has changed and the table for the specified
        // editor has the same prototype, or if the variable name has changed
        // and the table for the specified editor is an instance of this
        // variable
        if ((isRename
             && ((protoVarName.equals(oldPrototype) && oldVariableName == null)
                 || protoVarName.startsWith(oldPrototype + ".")
                 || editor.getOwnerName().startsWith(oldPrototype + ":")))
            || (oldVariableName != null
                && !oldVariableName.equals(newVariableName)
                && (protoVarName.equals(oldVariableName)
                    || protoVarName.endsWith("." + oldVariableName))))
        {
            // Update the table information to match the prototype/variable
            // name change
            editor.updateTableInformation(oldPrototype,
                                          newPrototype,
                                          oldVariableName,
                                          newVariableName);

            // Change the table name referenced by the editor
            editor.setTableName();

            // Update the tab in the editor dialog for this table with the new
            // name and tool tip text
            editorDialog.setTabText(editorDialog.getTableEditors().indexOf(editor),
                                    editor.getOwnerName(),
                                    editor.getTableToolTip());

            // Check if this table's editor is the active one for this editor
            // dialog (the active editor's table name appears in the dialog
            // frame)
            if (editor.equals(editorDialog.getTableEditor()))
            {
                // Change the editor dialog title
                editorDialog.setTitle(editor.getOwnerName());
            }
        }
    }

    /**************************************************************************
     * Create the data table editor dialog
     *
     * @param tableInformation
     *            list containing the information for each table
     *************************************************************************/
    private void initialize(List<TableInformation> tableInformation)
    {
        // Menu ///////////////////////////////////////////////////////////////
        // Create the data table menu bar
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // Create the File menu and menu items
        JMenu mnFile = ccddMain.createMenu(menuBar, "File", KeyEvent.VK_F, 1, null);
        mntmOpen = ccddMain.createMenuItem(mnFile, "Edit table(s)", KeyEvent.VK_E, 1, "Open one or more data tables for editing");
        mntmOpenPrototype = ccddMain.createMenuItem(mnFile, "Edit prototype", KeyEvent.VK_T, 2, "Open the prototype for the current table");
        mnFile.addSeparator();
        mntmStore = ccddMain.createMenuItem(mnFile, "Store current", KeyEvent.VK_U, 1, "Store changes to the current editor table");
        mntmStoreAll = ccddMain.createMenuItem(mnFile, "Store all", KeyEvent.VK_L, 1, "Store the changes to all tables in this editor");
        mnFile.addSeparator();
        mntmImport = ccddMain.createMenuItem(mnFile, "Import data", KeyEvent.VK_I, 1, "Import data from a CSV, EDS XML, JSON, or XTCE XML file into the current editor table");
        JMenu mnExport = ccddMain.createSubMenu(mnFile, "Export table", KeyEvent.VK_X, 1, null);
        mntmExportCSV = ccddMain.createMenuItem(mnExport, "CSV", KeyEvent.VK_C, 1, "Export the current editor table in CSV format");
        mntmExportEDS = ccddMain.createMenuItem(mnExport, "EDS", KeyEvent.VK_E, 1, "Export the current editor table in EDS XML format");
        mntmExportJSON = ccddMain.createMenuItem(mnExport, "JSON", KeyEvent.VK_J, 1, "Export the current editor table in JSON format");
        mntmExportXTCE = ccddMain.createMenuItem(mnExport, "XTCE", KeyEvent.VK_X, 1, "Export the current editor table in XTCE XML format");
        mnFile.addSeparator();
        mntmPrint = ccddMain.createMenuItem(mnFile, "Print current", KeyEvent.VK_P, 1, "Print the current editor table information");
        mntmSearchTable = ccddMain.createMenuItem(mnFile, "Search tables", KeyEvent.VK_S, 1, "Search the project database tables");
        mnFile.addSeparator();
        mntmCloseActive = ccddMain.createMenuItem(mnFile, "Close current", KeyEvent.VK_C, 2, "Close the current editor table");
        mntmCloseAll = ccddMain.createMenuItem(mnFile, "Close all", KeyEvent.VK_A, 1, "Close all tables in this editor");

        // Create the Edit menu and menu items
        JMenu mnEdit = ccddMain.createMenu(menuBar, "Edit", KeyEvent.VK_E, 1, null);
        mntmCopy = ccddMain.createMenuItem(mnEdit, "Copy", KeyEvent.VK_C, 1, "Copy the selected cell(s) to the clipboard");
        mntmPaste = ccddMain.createMenuItem(mnEdit, "Paste", KeyEvent.VK_V, 1, "Paste the clipboard contents at the current focus location");
        mntmInsert = ccddMain.createMenuItem(mnEdit, "Insert", KeyEvent.VK_I, 1, "Insert the clipboard contents at the current focus location");
        mnEdit.addSeparator();
        mntmUndo = ccddMain.createMenuItem(mnEdit, "Undo", KeyEvent.VK_Z, 1, "Undo the last edit operation");
        mntmRedo = ccddMain.createMenuItem(mnEdit, "Redo", KeyEvent.VK_Y, 1, "Redo the last undone edit operation");
        mnEdit.addSeparator();
        mntmInsertMacro = ccddMain.createMenuItem(mnEdit, "Insert macro", KeyEvent.VK_M, 1, "Insert a macro selected from the pop-up list");
        mntmShowMacros = ccddMain.createCheckBoxMenuItem(mnEdit, "Show macros", KeyEvent.VK_S, 1, "Temporarily replace macro(s) with the corresponding value(s)", false);
        mnEdit.addSeparator();
        JMenu mnClearSelected = ccddMain.createSubMenu(mnEdit, "Replace selected", KeyEvent.VK_L, 1, null);
        mntmWithBlanks = ccddMain.createMenuItem(mnClearSelected, "With blank", KeyEvent.VK_B, 1, "Replace the values in the selected cells with blanks");
        mntmWithPrototype = ccddMain.createMenuItem(mnClearSelected, "With prototype", KeyEvent.VK_P, 1, "Replace the values in the selected cells with the prototype's values");

        // Create the Row menu and menu items
        JMenu mnRow = ccddMain.createMenu(menuBar, "Row", KeyEvent.VK_R, 1, null);
        mntmInsertRow = ccddMain.createMenuItem(mnRow, "Insert row", KeyEvent.VK_I, 1, "Insert a row below the current focus location");
        mntmDeleteRow = ccddMain.createMenuItem(mnRow, "Delete row(s)", KeyEvent.VK_D, 1, "Delete the currently selected row(s)");
        mnRow.addSeparator();
        mntmMoveUp = ccddMain.createMenuItem(mnRow, "Move up", KeyEvent.VK_U, 1, "Move the currently selected row(s) up one row");
        mntmMoveDown = ccddMain.createMenuItem(mnRow, "Move down", KeyEvent.VK_N, 1, "Move the currently selected row(s) down one row");
        mnRow.addSeparator();
        mntmExpColArray = ccddMain.createCheckBoxMenuItem(mnRow, "Expand arrays", KeyEvent.VK_E, 1, "Expand/collapse display of array members", false);
        JMenu mnOverwrite = ccddMain.createSubMenu(mnRow, "Array overwrite", KeyEvent.VK_O, 1, null);
        mntmOverwriteAll = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite all", KeyEvent.VK_A, 1, "Copy array definition value change to all members", true);
        mntmOverwriteEmpty = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite empty", KeyEvent.VK_E, 2, "Copy array definition value change only to empty members", false);
        mntmOverwriteNone = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite none", KeyEvent.VK_N, 1, "Do not copy definition value change to members", false);
        ButtonGroup rbtnGroup = new ButtonGroup();
        rbtnGroup.add(mntmOverwriteAll);
        rbtnGroup.add(mntmOverwriteEmpty);
        rbtnGroup.add(mntmOverwriteNone);

        // Create the Column menu and menu items
        JMenu mnColumn = ccddMain.createMenu(menuBar, "Column", KeyEvent.VK_C, 1, null);
        mntmMoveLeft = ccddMain.createMenuItem(mnColumn, "Move left", KeyEvent.VK_L, 1, "Move the currently selected column(s) left one column");
        mntmMoveRight = ccddMain.createMenuItem(mnColumn, "Move right", KeyEvent.VK_R, 1, "Move the currently selected column(s) right one column");
        mntmResetOrder = ccddMain.createMenuItem(mnColumn, "Reset order", KeyEvent.VK_O, 1, "Reset the column order to the default");

        // Create the Field menu and menu items
        JMenu mnField = ccddMain.createMenu(menuBar, "Field", KeyEvent.VK_L, 1, null);
        mntmManageFields = ccddMain.createMenuItem(mnField, "Manage fields", KeyEvent.VK_M, 1, "Open the data field manager");
        mntmClearValues = ccddMain.createMenuItem(mnField, "Clear values", KeyEvent.VK_C, 1, "Clear the data field values");

        // Add a listener for the Open Table command
        mntmOpen.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Open a table in this editor dialog
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Open a table and add it to the tabbed pane. This calls
                // TableSelectDialog, but doesn't spawn a separate editor
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EDIT,
                                           CcddTableEditorDialog.this);
            }
        });

        // Add a listener for the Open Prototype Table command
        mntmOpenPrototype.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Open the currently displayed table's prototype table in this
             * editor dialog
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Open the active table's prototype table
                dbTable.loadTableDataInBackground(activeEditor.getTableInformation().getPrototypeName(),
                                                  CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Import command
        mntmImport.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Import a file into the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fileIOHandler.importSelectedFileIntoTable(activeEditor);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - CSV command
        mntmExportCSV.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Export the table to a file in CSV format
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EXPORT_CSV,
                                           CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - EDS command
        mntmExportEDS.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Export the table to a file in EDS XML format
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EXPORT_EDS,
                                           CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - JSON command
        mntmExportJSON.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Export the table to a file in JSON format
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EXPORT_JSON,
                                           CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - XTCE command
        mntmExportXTCE.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Export the table to a file in XTCE XML format
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EXPORT_XTCE,
                                           CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - EDS command
        mntmExportEDS.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Export the table to a file in EDS format
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                new CcddTableManagerDialog(ccddMain,
                                           ManagerDialogType.EXPORT_EDS,
                                           CcddTableEditorDialog.this);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Print command
        mntmPrint.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Output the table to the printer
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Print the table
                activeEditor.getTable().printTable("Table: "
                                                   + activeEditor.getOwnerName(),
                                                   activeEditor.getDataFieldHandler(),
                                                   CcddTableEditorDialog.this,
                                                   PageFormat.LANDSCAPE);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Search tables menu item
        mntmSearchTable.addActionListener(new ActionListener()
        {
            /******************************************************************
             * Display the search tables dialog
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                ccddMain.showSearchDialog(SearchDialogType.TABLES);
            }
        });

        // Add a listener for the Copy command
        mntmCopy.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Copy the selected table cell(s) contents into the clipboard
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Send a Ctrl-C key press
                controlKeyAction(KeyEvent.VK_C);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Paste command
        mntmPaste.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Paste the clipboard contents in the table, overwriting any
             * existing data in the target cells and adding new rows at the end
             * of the table if needed
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Send a Ctrl-V key press
                controlKeyAction(KeyEvent.VK_V);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Insert command
        mntmInsert.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Insert the clipboard contents in the table, creating new rows to
             * contain the data
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Send a ctrl-I key press
                controlKeyAction(KeyEvent.VK_I);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear selected | With blanks command
        mntmWithBlanks.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Erase the data in the selected cell(s)
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if there are any rows to clear
                if (activeEditor.getTableModel().getRowCount() != 0)
                {
                    // Clear the selected cell(s)
                    activeEditor.getTable().deleteCell(false);
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear selected | With prototype command
        mntmWithPrototype.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Erase the data in the selected cell(s) and the corresponding
             * entry(s) in the custom values table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if there are any rows to clear
                if (activeEditor.getTableModel().getRowCount() != 0)
                {
                    // Clear the selected cell(s)
                    activeEditor.getTable().deleteCell(true);
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the insert macro command
        mntmInsertMacro.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Insert the macro chosen from the pop-up list into the current
             * cell
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if a cell is being edited in the table by checking if
                // the selection start and end values are valid
                if (getTable().getLastSelectionStart() != -1
                    && getTable().getLastSelectionEnd() != -1)
                {
                    // Initiate editing in the selected cell
                    getTable().editCellAt(getTable().getSelectedRow(),
                                          getTable().getSelectedColumn());

                    // Get the cell's component
                    final Component comp = getTable().getEditorComponent();

                    // Check if the cell represents a text component (text
                    // area, text field, etc.)
                    if (comp instanceof JTextComponent)
                    {
                        // Set the focus to the cell
                        comp.requestFocusInWindow();

                        // Execute the event after any pending events
                        EventQueue.invokeLater(new Runnable()
                        {
                            /**************************************************
                             * Set the selected text start and end positions
                             *************************************************/
                            @Override
                            public void run()
                            {
                                // Set the text selected text to the last
                                // known positions
                                ((JTextComponent) comp).setSelectionStart(getTable().getLastSelectionStart());
                                ((JTextComponent) comp).setSelectionEnd(getTable().getLastSelectionEnd());
                            }
                        });
                    }

                    // Send a Ctrl-M key press to display the insert macro
                    // pop-up
                    controlKeyAction(KeyEvent.VK_M);
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Show macros command
        mntmShowMacros.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Temporarily replace any macros with the corresponding values
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // If the check box is selected then disable the controls that
                // are not allowed while macros are shown, else enable the
                // controls
                setControlsEnabled(!mntmShowMacros.isSelected());

                // Step through each table opened in the editor dialog
                for (CcddTableEditorHandler editor : tableEditors)
                {
                    // Expand all macros in the table if the check box is
                    // selected and disable editing for the table, else restore
                    // all macros and enable editing
                    editor.expandMacros(mntmShowMacros.isSelected(), false);
                    editor.setTableEditEnable(!mntmShowMacros.isSelected());
                }

                // Redraw the visible table
                activeEditor.getTable().repaint();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Store All command
        mntmStoreAll.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Store the changes to all open table contents, if any, in the
             * database
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Only update if one or more of the table's has changes and
                // the user confirms the action
                if (isTablesChanged()
                    && new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                                                 "<html><b>Store changes for all?",
                                                                 "Store Changes",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Update the database for every table that has changes
                    storeAllChanges();
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Reset Order command
        mntmResetOrder.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Reset the column order to the default
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.resetColumnOrder();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Manage Fields command
        mntmManageFields.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Manage the data fields
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Create the field editor dialog showing the fields for this
                // table
                new CcddFieldEditorDialog(ccddMain,
                                          activeEditor,
                                          activeEditor.getTableInformation().getTablePath(),
                                          false,
                                          MIN_WINDOW_WIDTH);

                // Enable/disable the Clear values command depending on if any
                // data fields remain
                mntmClearValues.setEnabled(!activeEditor.getDataFieldHandler().getFieldInformation().isEmpty());
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear values command
        mntmClearValues.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Clear the table data field values
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if there are any data fields to clear
                if (!activeEditor.getDataFieldHandler().getFieldInformation().isEmpty())
                {
                    // Remove all of the data field values from the table
                    activeEditor.getInputFieldPanelHandler().clearFieldValues();
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Create the lower (button) panel
        JPanel buttonPnl = new JPanel();

        // Define the buttons for the lower panel:
        // New button
        btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                           INSERT_ICON,
                                                           KeyEvent.VK_I,
                                                           "Insert a new row into the table");

        // Create a listener for the Insert Row command
        ActionListener insertAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Insert a new row into the table at the selected location
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().insertEmptyRow(true);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the insert listener to the Insert Row button and menu command
        btnInsertRow.addActionListener(insertAction);
        mntmInsertRow.addActionListener(insertAction);

        // Delete button
        btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                           DELETE_ICON,
                                                           KeyEvent.VK_D,
                                                           "Delete the selected row(s) from the table");

        // Create a listener for the Delete Row command
        ActionListener deleteAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Delete the selected row(s) from the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().deleteRow(true);
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the delete listener to the Delete Row button and menu command
        btnDeleteRow.addActionListener(deleteAction);
        mntmDeleteRow.addActionListener(deleteAction);

        // Create a listener for the Expand arrays command
        mntmExpColArray.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Expand or collapse the array members
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Toggle between showing and hiding the array member rows
                activeEditor.showHideArrayMembers();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Move Up button
        btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                        UP_ICON,
                                                        KeyEvent.VK_U,
                                                        "Move the selected row(s) up");

        // Create a listener for the Move Up command
        ActionListener moveUpAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Move the selected row(s) up in the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().moveRowUp();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the move up listener to the Move Up button and menu command
        btnMoveUp.addActionListener(moveUpAction);
        mntmMoveUp.addActionListener(moveUpAction);

        // Move Down button
        btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                          DOWN_ICON,
                                                          KeyEvent.VK_W,
                                                          "Move the selected row(s) down");

        // Create a listener for the Move Down command
        ActionListener moveDownAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Move the selected row(s) down in the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().moveRowDown();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the move down listener to the Move Down button and menu command
        btnMoveDown.addActionListener(moveDownAction);
        mntmMoveDown.addActionListener(moveDownAction);

        // Move Left button
        btnMoveLeft = CcddButtonPanelHandler.createButton("Left",
                                                          LEFT_ICON,
                                                          KeyEvent.VK_L,
                                                          "Move the selected column(s) left");

        // Create a listener for the Move Left command
        ActionListener moveLeftAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Move the selected column(s) left in the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().moveColumnLeft();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the move left listener to the Move Left button and menu command
        btnMoveLeft.addActionListener(moveLeftAction);
        mntmMoveLeft.addActionListener(moveLeftAction);

        // Move Right button
        btnMoveRight = CcddButtonPanelHandler.createButton("Right",
                                                           RIGHT_ICON,
                                                           KeyEvent.VK_R,
                                                           "Move the selected column(s) right");

        // Create a listener for the Move Right command
        ActionListener moveRightAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Move the selected column(s) right in the table
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getTable().moveColumnRight();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the move right listener to the Move Right button and menu
        // command
        btnMoveRight.addActionListener(moveRightAction);
        mntmMoveRight.addActionListener(moveRightAction);

        // Undo button
        btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                      UNDO_ICON,
                                                      KeyEvent.VK_Z,
                                                      "Undo the last edit action");

        // Create a listener for the Undo command
        ActionListener undoAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Undo the last cell edit
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getFieldPanelUndoManager().undo();

                // Update the data field background colors
                activeEditor.getInputFieldPanelHandler().setFieldBackgound();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the undo listener to the Undo button and menu command
        mntmUndo.addActionListener(undoAction);
        btnUndo.addActionListener(undoAction);

        // Redo button
        btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                      REDO_ICON,
                                                      KeyEvent.VK_Y,
                                                      "Redo the last undone edit action");

        // Create a listener for the Redo command
        ActionListener redoAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Redo the last cell edit that was undone
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                activeEditor.getFieldPanelUndoManager().redo();

                // Update the data field background colors
                activeEditor.getInputFieldPanelHandler().setFieldBackgound();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the redo listener to the Redo button and menu command
        mntmRedo.addActionListener(redoAction);
        btnRedo.addActionListener(redoAction);

        // Store button
        btnStore = CcddButtonPanelHandler.createButton("Store",
                                                       STORE_ICON,
                                                       KeyEvent.VK_S,
                                                       "Store the table updates in the database");

        // Create a listener for the Store command
        ActionListener storeAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Store the changes to the table contents, if any, in the database
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Only update the table in the database if a cell's content
                // has changed, no required columns are empty, and the user
                // confirms the action
                if (activeEditor.isTableChanged()
                    && !activeEditor.checkForMissingColumns()
                    && new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                                                 "<html><b>Store changes in project database?",
                                                                 "Store Changes",
                                                                 JOptionPane.QUESTION_MESSAGE,
                                                                 DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                {
                    // Store the changes for the currently displayed editor in
                    // the database
                    storeChanges(activeEditor);
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the store listener to the Store button and menu command
        btnStore.addActionListener(storeAction);
        mntmStore.addActionListener(storeAction);

        // Close button
        btnCloseActive = CcddButtonPanelHandler.createButton("Close",
                                                             CLOSE_ICON,
                                                             KeyEvent.VK_C,
                                                             "Close the table editor");

        // Add a listener for the Close active table command
        ActionListener closeAction = new ValidateCellActionListener()
        {
            /******************************************************************
             * Close the active editor
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if the contents of the last cell edited in the table
                // is validated and that none of the tables in the editor have
                // uncommitted changes. If changes exist then confirm
                // discarding the changes
                if (activeEditor.getTable().isLastCellValid()
                    && (!activeEditor.isTableChanged()
                        || new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                                                     "<html><b>Discard changes?",
                                                                     "Discard Changes",
                                                                     JOptionPane.QUESTION_MESSAGE,
                                                                     DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
                {
                    // Close the active table. If this is the only table in the
                    // editor then close the editor
                    closeTableEditor(activeEditor.getOwnerName());
                }
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        };

        // Add the close listener to the Close button and menu command
        btnCloseActive.addActionListener(closeAction);
        mntmCloseActive.addActionListener(closeAction);

        // Create a listener for the Close All menu command
        mntmCloseAll.addActionListener(new ValidateCellActionListener()
        {
            /******************************************************************
             * Close the table editor
             *****************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                windowCloseButtonAction();
            }

            /******************************************************************
             * Get the reference to the currently displayed table
             *****************************************************************/
            @Override
            protected CcddJTableHandler getTable()
            {
                return activeEditor.getTable();
            }
        });

        // Add buttons in the order in which they'll appear (left to right, top
        // to bottom)
        buttonPnl.add(btnInsertRow);
        buttonPnl.add(btnMoveUp);
        buttonPnl.add(btnMoveLeft);
        buttonPnl.add(btnUndo);
        buttonPnl.add(btnStore);
        buttonPnl.add(btnDeleteRow);
        buttonPnl.add(btnMoveDown);
        buttonPnl.add(btnMoveRight);
        buttonPnl.add(btnRedo);
        buttonPnl.add(btnCloseActive);

        // Distribute the buttons across two rows
        setButtonRows(2);

        // Table Editors //////////////////////////////////////////////////////
        // Create a tabbed pane for the editors to appear in
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener()
        {
            /******************************************************************
             * Update the editor to the one associated with the selected tab
             *****************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce)
            {
                activeEditor = tableEditors.get(tabbedPane.getSelectedIndex());

                // Change the dialog's title to the active table's name
                (CcddTableEditorDialog.this).setTitle(activeEditor.getOwnerName());

                // Update the expand/collapse arrays check box
                updateExpandArrayCheckBox();

                // Check if the Show macros command is not in effect
                if (!mntmShowMacros.isSelected())
                {
                    // Update the editor controls state
                    setControlsEnabled(true);
                }
            }
        });

        // Add each table as a tab in the editor dialog tabbed pane
        addTablePanes(tableInformation);

        // Set the first tab as the active editor
        activeEditor = tableEditors.get(0);

        // Display the table editor dialog
        createFrame(ccddMain.getMainFrame(),
                    tabbedPane,
                    buttonPnl,
                    null,
                    activeEditor.getOwnerName(),
                    null);

        // Enable the editor controls
        setControlsEnabled(true);
    }

    /**************************************************************************
     * Update the expand/collapse arrays check box to reflect the current state
     * of the active editor
     *************************************************************************/
    protected void updateExpandArrayCheckBox()
    {
        mntmExpColArray.setSelected(activeEditor.isExpanded());
    }

    /**************************************************************************
     * Get the selection state of the Array - Overwrite all radio button
     *
     * @return true if the Array - Overwrite all radio button is selected
     *************************************************************************/
    protected boolean isArrayOverwriteAll()
    {
        return mntmOverwriteAll.isSelected();
    }

    /**************************************************************************
     * Get the selection state of the Array - Overwrite none radio button
     *
     * @return true if the Array - Overwrite none radio button is selected
     *************************************************************************/
    protected boolean isArrayOverwriteNone()
    {
        return mntmOverwriteNone.isSelected();
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
            new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                                      "<html><b>Platform does not allow key press simulation",
                                                      "Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**************************************************************************
     * Commit changes to the database for the specified table editor
     *
     * @param editor
     *            table editor to commit
     *************************************************************************/
    private void storeChanges(CcddTableEditorHandler editor)
    {
        // Build the addition, modification, and deletion command lists
        editor.buildUpdates();

        // Perform the changes to the table in the database
        dbTable.modifyTableDataInBackground(editor.getTableInformation(),
                                            editor.getAdditions(),
                                            editor.getModifications(),
                                            editor.getDeletions(),
                                            false,
                                            false,
                                            true,
                                            true,
                                            true,
                                            null,
                                            null,
                                            CcddTableEditorDialog.this);
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
                // Step through each table in this editor dialog
                for (CcddTableEditorHandler editor : tableEditors)
                {
                    // Check if the table has changes and that no required
                    // columns are empty
                    if (editor.isTableChanged()
                        && !activeEditor.checkForMissingColumns())
                    {
                        // Build the addition, modification, and deletion
                        // command lists
                        editor.buildUpdates();

                        // Perform the changes to the table in the database
                        dbTable.modifyTableData(editor.getTableInformation(),
                                                editor.getAdditions(),
                                                editor.getModifications(),
                                                editor.getDeletions(),
                                                false,
                                                false,
                                                true,
                                                true,
                                                true,
                                                null,
                                                null,
                                                CcddTableEditorDialog.this);
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Add one or more table tabs to the editor dialog tabbed pane
     *
     * @param tableInformation
     *            list containing information for each table
     *************************************************************************/
    protected void addTablePanes(List<TableInformation> tableInformation)
    {
        // Step through the tables
        for (TableInformation tableInfo : tableInformation)
        {
            // Create an editor for this table and add it to the list of
            // editors
            CcddTableEditorHandler editor = new CcddTableEditorHandler(ccddMain,
                                                                       tableInfo,
                                                                       this);
            tableEditors.add(editor);

            // Create a tab for each table
            tabbedPane.addTab(editor.getOwnerName(),
                              null,
                              editor.getFieldPanel(),
                              editor.getTableToolTip());
        }

        // Check if only a single table was added
        if (tableInformation.size() == 1)
        {
            // Select the tab for the newly opened table
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        }
    }

    /**************************************************************************
     * Update the change indicator for the specified table editor
     *
     * @param tableEditor
     *            reference to the table editor for which the change indicator
     *            is to be updated
     *************************************************************************/
    protected void updateChangeIndicator(CcddTableEditorHandler tableEditor)
    {
        // Get the index of the specified tab
        int index = tableEditors.indexOf(tableEditor);

        // Check that the tab index is valid
        if (index != -1)
        {
            // Replace the tab name, appending the change indicator if changes
            // exist
            tabbedPane.setTitleAt(index,
                                  tabbedPane.getTitleAt(index).replaceAll("\\*", "")
                                         + (tableEditor.isTableChanged()
                                                                         ? "*"
                                                                         : ""));
        }
    }

    /**************************************************************************
     * Determine if any of the tables represented in this editor dialog have
     * uncommitted changes
     *
     * @return true if any of the editors represented in this editor dialog
     *         have uncommitted changes
     *************************************************************************/
    protected boolean isTablesChanged()
    {
        boolean isChanged = false;

        // Step through each editor in the editor dialog
        for (CcddTableEditorHandler editor : tableEditors)
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
     * Remove the table editor from the table editor list
     *************************************************************************/
    @Override
    protected void windowClosedAction()
    {
        ccddMain.getTableEditorDialogs().remove(CcddTableEditorDialog.this);
    }

    /**************************************************************************
     * Handle the frame close button press event
     *************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the specified table
        // is validated and that none of the tables in the editor have
        // uncommitted changes. If changes exist then confirm discarding the
        // changes
        if (activeEditor.getTable().isLastCellValid()
            && (!isTablesChanged()
                || new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                                             "<html><b>Discard changes?",
                                                             "Discard Changes",
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
        {
            // Close the editor dialog
            closeFrame();
        }
    }
}
