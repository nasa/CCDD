/**************************************************************************************************
/** \file CcddTableEditorDialog.java
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Class for handling data table editing; displays instances of CcddTableEditorHandler.
*     The dialog is built on the CcddEditorPanelHandler class.
*
*   \copyright
*     MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
*
*     Copyright (c) 2016-2021 United States Government as represented by the
*     Administrator of the National Aeronautics and Space Administration.  All Rights Reserved.
*
*     This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
*     distributed and modified only pursuant to the terms of that agreement.  See the License for
*     the specific language governing permissions and limitations under the
*     License at https://software.nasa.gov/.
*
*     Unless required by applicable law or agreed to in writing, software distributed under the
*     License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
*     either expressed or implied.
*
*   \par Limitations, Assumptions, External Events and Notes:
*     - TBD
*
**************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.CHANGE_INDICATOR;
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
import CCDD.CcddClassesComponent.DnDTabbedPane;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary data table editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableEditorDialog extends CcddFrameHandler {
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddFieldHandler fieldHandler;
    private CcddTableEditorHandler activeEditor;
    private final List<CcddTableEditorHandler> tableEditors;
    private CcddFindReplaceDialog searchDlg;

    // Components that need to be accessed by multiple methods
    private JMenu mnFile;
    private JMenuItem mntmEdit;
    private JMenuItem mntmEditPrototype;
    private JMenuItem mntmStore;
    private JMenuItem mntmStoreAll;
    private JMenuItem mntmImportJSON;
    private JMenuItem mntmImportCSV;
    private JMenuItem mntmImportXTCE;
    private JMenuItem mntmImportEDS;
    private JMenuItem mntmExportCSV;
    private JMenuItem mntmExportEDS;
    private JMenuItem mntmExportJSON;
    private JMenuItem mntmExportXTCE;
    private JMenuItem mntmPrint;
    private JMenuItem mntmFindReplace;
    private JMenuItem mntmCloseActive;
    private JMenuItem mntmCloseAll;
    private JMenuItem[] mntmRecentTables;
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
    private DnDTabbedPane tabbedPane;

    /**********************************************************************************************
     * Table editor dialog class constructor
     *
     * @param ccddMain         main class
     *
     * @param tableInformation list containing information for each table
     *
     * @param editor           reference to an existing table editor; null if adding
     *                         new tables to the editor dialog
     *********************************************************************************************/
    CcddTableEditorDialog(CcddMain ccddMain, List<TableInfo> tableInformation, CcddTableEditorHandler editor) {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        fieldHandler = ccddMain.getFieldHandler();
        tableEditors = new ArrayList<CcddTableEditorHandler>();

        // Create the data table editor dialog
        initialize(tableInformation, editor);
    }

    /**********************************************************************************************
     * Table editor dialog class constructor
     *
     * @param ccddMain         main class
     *
     * @param tableInformation list containing the information for each table
     *********************************************************************************************/
    CcddTableEditorDialog(CcddMain ccddMain, List<TableInfo> tableInformation) {
        this(ccddMain, tableInformation, null);
    }

    /**********************************************************************************************
     * Table editor dialog class constructor
     *
     * @param ccddMain main class
     *
     * @param editor   reference to an existing table editor
     *********************************************************************************************/
    CcddTableEditorDialog(CcddMain ccddMain, CcddTableEditorHandler editor) {
        this(ccddMain, null, editor);
        editor.setEditorDialog(this);
    }

    /**********************************************************************************************
     * Get the currently active table editor
     *
     * @return Currently active table editor
     *********************************************************************************************/
    protected CcddTableEditorHandler getTableEditor() {
        return activeEditor;
    }

    /**********************************************************************************************
     * Get the list of table editors
     *
     * @return List of table editors
     *********************************************************************************************/
    protected List<CcddTableEditorHandler> getTableEditors() {
        return tableEditors;
    }

    /**********************************************************************************************
     * Get the tabbed pane
     *
     * @return Tabbed pane
     *********************************************************************************************/
    protected DnDTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    /**********************************************************************************************
     * Set the specified tab's title and tool tip text
     *
     * @param tabIndex   index of the tab to change
     *
     * @param tabName    tab title
     *
     * @param tabToolTip tab tool tip text
     *********************************************************************************************/
    private void setTabText(int tabIndex, String tabName, String tabToolTip) {
        tabbedPane.setTitleAt(tabIndex, tabName);
        tabbedPane.setToolTipTextAt(tabIndex, CcddUtilities.wrapText(tabToolTip,
                ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
    }

    /**********************************************************************************************
     * Get the reference to the Files menu
     *
     * @return Reference to the Files menu
     *********************************************************************************************/
    protected JMenu getFilesMenu() {
        return mnFile;
    }

    /**********************************************************************************************
     * Get the reference to the array of recently opened table menu items
     *
     * @return Reference to the array of recently opened table menu items
     *********************************************************************************************/
    protected JMenuItem[] getRecentTableMenuItems() {
        return mntmRecentTables;
    }

    /**********************************************************************************************
     * Set the array of recently opened table menu items
     *
     * @param menuItems array of recently opened table menu items
     *********************************************************************************************/
    protected void setRecentTableMenuItems(JMenuItem[] menuItems) {
        mntmRecentTables = menuItems;
    }

    /**********************************************************************************************
     * Enable/disable the table editor dialog menu controls
     *
     * @param enable true to enable the controls; false to disable
     *********************************************************************************************/
    @Override
    protected void setControlsEnabled(boolean enable) {
        // Set the flags based on the show macros, can have arrays, table is prototype
        // or child, and user access level statuses
        boolean enableIfNotMacro = enable && !mntmShowMacros.isSelected();
        boolean enableIfArray = enableIfNotMacro && activeEditor.isCanHaveArrays();
        boolean enableIfPrototype = enableIfNotMacro && activeEditor.getTableInformation().isPrototype();
        boolean enableIfChild = enableIfNotMacro && !activeEditor.getTableInformation().isPrototype();
        boolean enableIfReadWrite = enable && ccddMain.getDbControlHandler().isAccessReadWrite();

        // Step through the menu bar items
        for (int index = 0; index < getJMenuBar().getComponentCount(); index++) {
            // Enable/disable the item based on the input flag
            getJMenuBar().getMenu(index).setEnabled(enable);
        }

        // Set the menu item enable status
        mntmEdit.setEnabled(enableIfNotMacro);
        mntmEditPrototype.setEnabled(enableIfChild);
        mntmStore.setEnabled(enableIfNotMacro && enableIfReadWrite);
        mntmStoreAll.setEnabled(enableIfNotMacro && enableIfReadWrite);
        mntmImportJSON.setEnabled(enableIfNotMacro);
        mntmImportCSV.setEnabled(enableIfNotMacro);
        mntmImportXTCE.setEnabled(enableIfNotMacro);
        mntmImportEDS.setEnabled(enableIfNotMacro);
        mntmExportCSV.setEnabled(enableIfNotMacro);
        mntmExportEDS.setEnabled(enableIfNotMacro);
        mntmExportJSON.setEnabled(enableIfNotMacro);
        mntmExportXTCE.setEnabled(enableIfNotMacro);
        mntmPrint.setEnabled(enableIfNotMacro);
        mntmFindReplace.setEnabled(enableIfNotMacro);
        mntmCloseActive.setEnabled(enable);
        mntmCloseAll.setEnabled(enable);
        mntmUndo.setEnabled(enableIfNotMacro);
        mntmRedo.setEnabled(enableIfNotMacro);
        mntmCopy.setEnabled(enableIfNotMacro);
        mntmPaste.setEnabled(enableIfPrototype);
        mntmInsertMacro.setEnabled(enableIfNotMacro);
        mntmShowMacros.setEnabled(enable);
        mntmInsert.setEnabled(enableIfNotMacro);
        mntmInsertRow.setEnabled(enableIfPrototype);
        mntmDeleteRow.setEnabled(enableIfPrototype);
        mntmExpColArray.setEnabled(enableIfArray);
        mntmOverwriteAll.setEnabled(enableIfArray);
        mntmOverwriteEmpty.setEnabled(enableIfArray);
        mntmOverwriteNone.setEnabled(enableIfArray);
        mntmMoveUp.setEnabled(enableIfPrototype);
        mntmMoveDown.setEnabled(enableIfPrototype);
        mntmMoveLeft.setEnabled(enable);
        mntmMoveRight.setEnabled(enable);
        mntmResetOrder.setEnabled(enable);
        mntmWithBlanks.setEnabled(enableIfNotMacro);
        mntmWithPrototype.setEnabled(enableIfChild);
        mntmManageFields.setEnabled(enableIfNotMacro);
        mntmClearValues.setEnabled(
                enableIfNotMacro && !fieldHandler.getFieldInformationByOwner(activeEditor.getOwnerName()).isEmpty());

        // Check if a recent tables menu item exists
        if (mntmRecentTables != null && mntmRecentTables.length != 0) {
            // Step through each table in the recently opened tables list
            for (JMenuItem recent : mntmRecentTables) {
                recent.setEnabled(enableIfNotMacro);
            }
        }

        // Set the button enable status
        btnInsertRow.setEnabled(enableIfPrototype);
        btnDeleteRow.setEnabled(enableIfPrototype);
        btnMoveUp.setEnabled(enableIfPrototype);
        btnMoveDown.setEnabled(enableIfPrototype);
        btnMoveLeft.setEnabled(enable);
        btnMoveRight.setEnabled(enable);
        btnUndo.setEnabled(enableIfNotMacro);
        btnRedo.setEnabled(enableIfNotMacro);
        btnStore.setEnabled(enableIfNotMacro && enableIfReadWrite);
        btnCloseActive.setEnabled(enable);
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of a table modification. This
     * includes closing child table editors that are no longer valid and modifying
     * child tables when a prototype table is changed
     *
     * @param main              reference to CcddMain
     *
     * @param tableInfo         table information
     *
     * @param modifications     list of row update information
     *
     * @param deletions         list of row deletion information
     *
     * @param forceUpdate       true to make the changes to other tables (e.g.,
     *                          following a data type or macro change); false to
     *                          only make changes to tables other than the one in
     *                          which the changes originally took place
     *
     * @param isRefFieldChange  true is a data field has a variable (command,
     *                          message name and ID) reference input type
     *
     * @param isMsgNameIDChange true is a message name or ID changed and a cell or
     *                          field uses the message name and ID input type
     *********************************************************************************************/
    protected static void doTableModificationComplete(CcddMain main, TableInfo tableInfo,
            List<TableModification> modifications, List<TableModification> deletions, boolean forceUpdate,
            boolean isRefFieldChange, boolean isMsgNameIDChange) {
        // Get references to shorten subsequent calls. Can't use global references since this is
        // a static method
        CcddDataTypeHandler dtHandler = main.getDataTypeHandler();
        CcddDbTableCommandHandler dbTblCmdHndlr = main.getDbTableCommandHandler();

        // Create a list to store the names of tables that are no longer valid
        List<String[]> invalidatedEditors = new ArrayList<String[]>();

        // Step through each row modification
        for (TableModification mod : modifications) {
            // Check if the variable's original data type was a structure (meaning it could have a
            // table editor open) and if (1) it has been changed to an array or if (2) the data
            // type has been changed
            if (mod.getVariableColumn() != -1 && mod.getDataTypeColumn() != -1
                    && !dtHandler.isPrimitive(mod.getOriginalRowData()[mod.getDataTypeColumn()].toString())
                    && ((mod.getArraySizeColumn() != -1
                            && mod.getOriginalRowData()[mod.getArraySizeColumn()].toString().isEmpty()
                            && !mod.getRowData()[mod.getArraySizeColumn()].toString().isEmpty())
                            || !mod.getOriginalRowData()[mod.getDataTypeColumn()].toString()
                                    .equals(mod.getRowData()[mod.getDataTypeColumn()].toString()))) {
                // Add the pattern that matches the table editor tab names for the modified
                // structure. The pattern is [parent structure].__,[original structure data
                // type].[original structure variable name][,__]
                invalidatedEditors.add(new String[] { tableInfo.getPrototypeName(),
                        mod.getOriginalRowData()[mod.getDataTypeColumn()].toString() + "."
                                + mod.getOriginalRowData()[mod.getVariableColumn()].toString() });
            }
        }

        // Step through each row deletion
        for (TableModification del : deletions) {
            // Check if the original data type was for a structure
            if (del.getVariableColumn() != -1 && del.getDataTypeColumn() != -1
                    && !dtHandler.isPrimitive(del.getRowData()[del.getDataTypeColumn()].toString())) {
                // Add the pattern that matches the table editor tab names for the deleted structure.
                // The pattern is [parent structure].__,[structure data type].[structure variable name][,__]
                invalidatedEditors.add(new String[] { tableInfo.getPrototypeName(),
                        del.getRowData()[del.getDataTypeColumn()].toString() + "."
                                + del.getRowData()[del.getVariableColumn()].toString() });
            }
        }

        // Close the invalid table editors
        dbTblCmdHndlr.closeDeletedTableEditors(invalidatedEditors, main.getMainFrame());

        // Update the tables with message names & IDs columns
        dbTblCmdHndlr.updateInputTypeColumns(null, main.getMainFrame());

        // Step through the open editor dialogs
        for (CcddTableEditorDialog editorDialog : main.getTableEditorDialogs()) {
            // Step through each individual editor
            for (CcddTableEditorHandler editor : editorDialog.getTableEditors()) {
                // Flag that indicates if the updated table is a prototype and the editor is for an
                // instance of the updated table
                boolean applyToInstance = tableInfo.isPrototype() && !editor.getTableInformation().isPrototype()
                        && tableInfo.getPrototypeName().equals((editor.getTableInformation().getPrototypeName()));

                // Check if a data field exists that uses the variable (command) reference input
                // type and if table wasn't already updated above
                if (isRefFieldChange && !applyToInstance) {
                    // Update the current and committed field definitions and information so that
                    // the update isn't considered a change
                    editor.updateTableFieldInformationFromHandler();
                    editor.createDataFieldPanel(false, editor.getTableInformation().getFieldInformation(), false);
                }

                // Check if this is the table that was updated or an instance of it (if the updated table is a prototype)
                if (applyToInstance
                        || editor.getTableInformation().getProtoVariableName().equals(tableInfo.getProtoVariableName())
                        || (isMsgNameIDChange && editor.getTableTypeDefinition()
                                .getColumnIndexByInputType(DefaultInputType.MESSAGE_REFERENCE) != -1)) {
                    // Load the table from the database
                    TableInfo updateInfo = main.getDbTableCommandHandler()
                            .loadTableData(editor.getTableInformation().getTablePath(), true, true, false, editorDialog);

                    // Store the updates as the committed changes in the table (so that other
                    // changes are recognized)
                    editor.doTableUpdatesComplete(updateInfo, applyToInstance || forceUpdate);
                }

                // Step through each row modification
                for (TableModification mod : modifications) {
                    // Check if the modification contains a variable name and data type columns;
                    // this implies it could be a structure table reference
                    if (mod.getVariableColumn() != -1 && mod.getDataTypeColumn() != -1) {
                        // Update the table names in the open editors
                        updateTableNames(main, mod.getOriginalRowData()[mod.getDataTypeColumn()].toString(),
                                mod.getRowData()[mod.getDataTypeColumn()].toString(),
                                mod.getOriginalRowData()[mod.getVariableColumn()].toString(),
                                mod.getRowData()[mod.getVariableColumn()].toString(), editorDialog, editor);
                    }
                }
            }
        }

        // Check if the data field editor table dialog is open
        if (main.getFieldTableEditor() != null && main.getFieldTableEditor().isShowing()) {
            // Update the data field editor table
            main.getFieldTableEditor().getTable().loadAndFormatData();
        }
    }

    /**********************************************************************************************
     * Update the table name, editor dialog tab, and editor dialog frame when the
     * prototype name (data type for a structure table) or the variable name for a
     * structure type table is changed
     *
     * @param main            reference to CcddMain
     *
     * @param oldPrototype    original prototype name (same as the data type for a
     *                        structure table)
     *
     * @param newPrototype    current prototype name (same as the data type for a
     *                        structure table)
     *
     * @param oldVariableName original variable name if this change is for a
     *                        structure table
     *
     * @param newVariableName current variable name if this change is for a
     *                        structure table
     *
     * @param editorDialog    current editor dialog to which the changes are
     *                        compared
     *
     * @param editor          current editor to which the changes are compared
     *********************************************************************************************/
    protected static void updateTableNames(CcddMain main, String oldPrototype, String newPrototype,
            String oldVariableName, String newVariableName, CcddTableEditorDialog editorDialog,
            CcddTableEditorHandler editor) {
        CcddDataTypeHandler dtHandler = main.getDataTypeHandler();

        // Get the prototype + variable name from that editor owner name by stripping
        // off the root name and separator
        String protoVarName = editor.getOwnerName().substring(editor.getOwnerName().indexOf(" ") + 1);

        // Set flag to true if the prototype changed and isn't/wasn't a primitive variable
        boolean isRename = !oldPrototype.equals(newPrototype) && !dtHandler.isPrimitive(oldPrototype)
                && !dtHandler.isPrimitive(newPrototype);

        // Check if the prototype name changed
        if (isRename && oldVariableName == null) {
            // Update the variable data types to match the change, if this table represents a structure
            editor.updateDataTypeReferences(oldPrototype, newPrototype);
        }

        // Check if the prototype has changed and the table for the specified editor has the same prototype, or
        // if the variable name has changed and the table for the specified editor is an instance of this variable
        if ((isRename && ((protoVarName.equals(oldPrototype) && oldVariableName == null)
                || protoVarName.startsWith(oldPrototype + ".") || editor.getOwnerName().startsWith(oldPrototype + ":")))
                || (oldVariableName != null && !oldVariableName.equals(newVariableName)
                        && (protoVarName.equals(oldVariableName) || protoVarName.endsWith("." + oldVariableName)))) {
            // Update the table information to match the prototype/variable name change
            editor.updateTableInformation(oldPrototype, newPrototype, oldVariableName, newVariableName);

            // Change the table name referenced by the editor
            editor.setTableName();

            // Update the tab in the editor dialog for this table with the new name and tool tip text
            editorDialog.setTabText(editorDialog.getTableEditors().indexOf(editor), editor.getOwnerName(),
                    editor.getTableToolTip());

            // Check if this table's editor is the active one for this editor dialog (the active
            // editor's table name appears in the dialog frame)
            if (editor.equals(editorDialog.getTableEditor())) {
                // Change the editor dialog title
                editorDialog.setTitle(editor.getOwnerName());
            }
        }
    }

    /**********************************************************************************************
     * Create the data table editor dialog
     *
     * @param tableInformation list containing information for each table
     *
     * @param editor           reference to an existing table editor
     *********************************************************************************************/
    private void initialize(List<TableInfo> tableInformation, CcddTableEditorHandler editor) {
        // Create the data table menu bar
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        // Create the File menu and menu items
        mnFile = ccddMain.createMenu(menuBar, "File", KeyEvent.VK_F, 1, null);
        mntmEdit = ccddMain.createMenuItem(mnFile, "Edit table(s)", KeyEvent.VK_E, 1,
                "Open one or more data tables for editing");
        mntmEditPrototype = ccddMain.createMenuItem(mnFile, "Edit prototype", KeyEvent.VK_T, 2,
                "Open the prototype for the current table");
        mnFile.addSeparator();
        mntmStore = ccddMain.createMenuItem(mnFile, "Store current", KeyEvent.VK_U, 1,
                "Store changes to the current editor table");
        mntmStoreAll = ccddMain.createMenuItem(mnFile, "Store all", KeyEvent.VK_L, 1,
                "Store the changes to all tables in this editor");
        mnFile.addSeparator();
        JMenu mnImport = ccddMain.createSubMenu(mnFile, "Import data", KeyEvent.VK_I, 1, null);
        mntmImportJSON = ccddMain.createMenuItem(mnImport, "JSON", KeyEvent.VK_J, 1,
                "Import a JSON file into the current editor table");
        mntmImportCSV = ccddMain.createMenuItem(mnImport, "CSV", KeyEvent.VK_C, 1,
                "Import a CSV file into the current editor table");
        mntmImportXTCE = ccddMain.createMenuItem(mnImport, "XTCE", KeyEvent.VK_X, 1,
                "Import a XTCE file into the current editor table");
        mntmImportEDS = ccddMain.createMenuItem(mnImport, "EDS", KeyEvent.VK_E, 1,
                "Import a EDS file into the current editor table");
        JMenu mnExport = ccddMain.createSubMenu(mnFile, "Export table", KeyEvent.VK_X, 1, null);
        mntmExportCSV = ccddMain.createMenuItem(mnExport, "CSV", KeyEvent.VK_C, 1,
                "Export the current editor table in CSV format");
        mntmExportEDS = ccddMain.createMenuItem(mnExport, "EDS", KeyEvent.VK_E, 1,
                "Export the current editor table in EDS XML format");
        mntmExportJSON = ccddMain.createMenuItem(mnExport, "JSON", KeyEvent.VK_J, 1,
                "Export the current editor table in JSON format");
        mntmExportXTCE = ccddMain.createMenuItem(mnExport, "XTCE", KeyEvent.VK_X, 1,
                "Export the current editor table in XTCE XML format");
        mnFile.addSeparator();
        mntmPrint = ccddMain.createMenuItem(mnFile, "Print current", KeyEvent.VK_P, 1,
                "Print the current editor table information");
        mntmFindReplace = ccddMain.createMenuItem(mnFile, "Find/replace", KeyEvent.VK_F, 1,
                "Find/replace text in the active data table");
        mnFile.addSeparator();
        mntmCloseActive = ccddMain.createMenuItem(mnFile, "Close current", KeyEvent.VK_C, 2,
                "Close the current editor table");
        mntmCloseAll = ccddMain.createMenuItem(mnFile, "Close all", KeyEvent.VK_A, 1,
                "Close all tables in this editor");
        mnFile.addSeparator();

        // Create the Edit menu and menu items
        JMenu mnEdit = ccddMain.createMenu(menuBar, "Edit", KeyEvent.VK_E, 1, null);
        mntmCopy = ccddMain.createMenuItem(mnEdit, "Copy", KeyEvent.VK_C, 1,
                "Copy the selected cell(s) to the clipboard");
        mntmPaste = ccddMain.createMenuItem(mnEdit, "Paste (Ctrl-V)", KeyEvent.VK_V, 1,
                "Paste the clipboard contents at the current focus location");
        mntmInsert = ccddMain.createMenuItem(mnEdit, "Insert", KeyEvent.VK_I, 1,
                "Insert the clipboard contents at the current focus location");
        mnEdit.addSeparator();
        mntmUndo = ccddMain.createMenuItem(mnEdit, "Undo (Ctrl-Z)", KeyEvent.VK_Z, 1, "Undo the last edit operation");
        mntmRedo = ccddMain.createMenuItem(mnEdit, "Redo (Ctrl-Y)", KeyEvent.VK_Y, 1,
                "Redo the last undone edit operation");
        mnEdit.addSeparator();
        mntmInsertMacro = ccddMain.createMenuItem(mnEdit, "Insert macro", KeyEvent.VK_M, 1,
                "Insert a macro selected from the pop-up list");
        mntmShowMacros = ccddMain.createCheckBoxMenuItem(mnEdit, "Show macros", KeyEvent.VK_S, 1,
                "Temporarily replace macro(s) with the corresponding value(s)", false);
        mnEdit.addSeparator();
        JMenu mnClearSelected = ccddMain.createSubMenu(mnEdit, "Replace selected", KeyEvent.VK_L, 1, null);
        mntmWithBlanks = ccddMain.createMenuItem(mnClearSelected, "With blank", KeyEvent.VK_B, 1,
                "Replace the values in the selected cells with blanks");
        mntmWithPrototype = ccddMain.createMenuItem(mnClearSelected, "With prototype", KeyEvent.VK_P, 1,
                "Replace the values in the selected cells with the prototype's values");

        // Create the Row menu and menu items
        JMenu mnRow = ccddMain.createMenu(menuBar, "Row", KeyEvent.VK_R, 1, null);
        mntmInsertRow = ccddMain.createMenuItem(mnRow, "Insert row", KeyEvent.VK_I, 1,
                "Insert a row below the current focus location");
        mntmDeleteRow = ccddMain.createMenuItem(mnRow, "Delete row(s)", KeyEvent.VK_D, 1,
                "Delete the currently selected row(s)");
        mnRow.addSeparator();
        mntmMoveUp = ccddMain.createMenuItem(mnRow, "Move up", KeyEvent.VK_U, 1,
                "Move the currently selected row(s) up one row");
        mntmMoveDown = ccddMain.createMenuItem(mnRow, "Move down", KeyEvent.VK_N, 1,
                "Move the currently selected row(s) down one row");
        mnRow.addSeparator();
        mntmExpColArray = ccddMain.createCheckBoxMenuItem(mnRow, "Expand arrays", KeyEvent.VK_E, 1,
                "Expand/collapse display of array members", false);
        JMenu mnOverwrite = ccddMain.createSubMenu(mnRow, "Array overwrite", KeyEvent.VK_O, 1, null);
        mntmOverwriteAll = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite all", KeyEvent.VK_A, 1,
                "Copy array definition value change to all members", true);
        mntmOverwriteEmpty = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite empty", KeyEvent.VK_E, 3,
                "Copy array definition value change only to empty members", false);
        mntmOverwriteNone = ccddMain.createRadioButtonMenuItem(mnOverwrite, "Overwrite none", KeyEvent.VK_N, 1,
                "Do not copy definition value change to members", false);
        ButtonGroup rbtnGroup = new ButtonGroup();
        rbtnGroup.add(mntmOverwriteAll);
        rbtnGroup.add(mntmOverwriteEmpty);
        rbtnGroup.add(mntmOverwriteNone);

        // Create the Column menu and menu items
        JMenu mnColumn = ccddMain.createMenu(menuBar, "Column", KeyEvent.VK_M, 1, null);
        mntmMoveLeft = ccddMain.createMenuItem(mnColumn, "Move left", KeyEvent.VK_L, 1,
                "Move the currently selected column(s) left one column");
        mntmMoveRight = ccddMain.createMenuItem(mnColumn, "Move right", KeyEvent.VK_R, 1,
                "Move the currently selected column(s) right one column");
        mntmResetOrder = ccddMain.createMenuItem(mnColumn, "Reset order", KeyEvent.VK_O, 1,
                "Reset the column order to the default");

        // Create the Field menu and menu items
        JMenu mnField = ccddMain.createMenu(menuBar, "Field", KeyEvent.VK_L, 1, null);
        mntmManageFields = ccddMain.createMenuItem(mnField, "Manage fields", KeyEvent.VK_M, 1,
                "Open the data field manager");
        mntmClearValues = ccddMain.createMenuItem(mnField, "Clear values", KeyEvent.VK_C, 1,
                "Clear the data field values");

        // Add a listener for the Edit table(s) command
        mntmEdit.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Open a table in this editor dialog
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Open a table and add it to the tabbed pane. This calls TableSelectDialog, but
                // doesn't spawn a separate editor
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.EDIT, CcddTableEditorDialog.this);
            }
        });

        // Add a listener for the Edit prototype command
        mntmEditPrototype.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Open the currently displayed table's prototype table in this editor dialog
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Open the active table's prototype table
                dbTable.loadTableDataInBackground(activeEditor.getTableInformation().getPrototypeName(),
                        CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Import command
        mntmImportJSON.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Import a JSON file into the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.IMPORT_JSON, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Import command
        mntmImportCSV.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Import a CSV file into the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.IMPORT_CSV, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Import command
        mntmImportXTCE.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Import a XTCE file into the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.IMPORT_XTCE, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Import command
        mntmImportEDS.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Import a EDS XML file into the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.IMPORT_EDS, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - CSV command
        mntmExportCSV.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Export the table to a file in CSV format
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.EXPORT_CSV, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - EDS command
        mntmExportEDS.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Export the table to a file in EDS XML format
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.EXPORT_EDS, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - JSON command
        mntmExportJSON.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Export the table to a file in JSON format
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.EXPORT_JSON, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Export - XTCE command
        mntmExportXTCE.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Export the table to a file in XTCE XML format
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                new CcddTableManagerDialog(ccddMain, ManagerDialogType.EXPORT_XTCE, CcddTableEditorDialog.this);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Print command
        mntmPrint.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Output the table to the printer
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Print the table
                activeEditor.getTable().printTable("Table: " + activeEditor.getOwnerName(),
                        activeEditor.getPanelFieldInformation(), CcddTableEditorDialog.this, PageFormat.LANDSCAPE);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Find/replace menu item
        mntmFindReplace.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Display the find/replace dialog
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                findReplace();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Copy command
        mntmCopy.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Copy the selected table cell(s) contents into the clipboard
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Send a Ctrl-C key press
                controlKeyAction(KeyEvent.VK_C);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Paste command
        mntmPaste.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Paste the clipboard contents in the table, overwriting any existing data in
             * the target cells and adding new rows at the end of the table if needed
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Send a Ctrl-V key press
                controlKeyAction(KeyEvent.VK_V);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Insert command
        mntmInsert.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Insert the clipboard contents in the table, creating new rows to contain the
             * data
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Send a ctrl-I key press
                controlKeyAction(KeyEvent.VK_I);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear selected | With blanks command
        mntmWithBlanks.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Erase the data in the selected cell(s)
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Check if there are any rows to clear
                if (activeEditor.getTableModel().getRowCount() != 0) {
                    // Clear the selected cell(s)
                    activeEditor.getTable().deleteCell(false);
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear selected | With prototype command
        mntmWithPrototype.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Erase the data in the selected cell(s) and the corresponding entry(s) in the
             * custom values table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Check if there are any rows to clear
                if (activeEditor.getTableModel().getRowCount() != 0) {
                    // Clear the selected cell(s)
                    activeEditor.getTable().deleteCell(true);
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the insert macro command
        mntmInsertMacro.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Insert the macro chosen from the pop-up list into the current cell
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Check if a cell is being edited in the table by checking if the selection
                // start and end values are valid
                if (getTable().getLastSelectionStart() != -1 && getTable().getLastSelectionEnd() != -1) {
                    // Initiate editing in the selected cell
                    getTable().editCellAt(getTable().getSelectedRow(), getTable().getSelectedColumn());

                    // Get the cell's component
                    final Component comp = getTable().getEditorComponent();

                    // Check if the cell represents a text component (text area, text field, etc.)
                    if (comp instanceof JTextComponent) {
                        // Set the focus to the cell
                        comp.requestFocusInWindow();

                        // Execute the event after any pending events
                        EventQueue.invokeLater(new Runnable() {
                            /**********************************************************************
                             * Set the selected text start and end positions
                             *********************************************************************/
                            @Override
                            public void run() {
                                // Set the text selected text to the last known positions
                                ((JTextComponent) comp).setSelectionStart(getTable().getLastSelectionStart());
                                ((JTextComponent) comp).setSelectionEnd(getTable().getLastSelectionEnd());
                            }
                        });
                    }

                    // Send a Ctrl-M key press to display the insert macro pop-up
                    controlKeyAction(KeyEvent.VK_M);
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Show macros command
        mntmShowMacros.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Temporarily replace any macros with the corresponding values
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Update the controls based on if the show macros check box is selected or not,
                // and if the menus are enabled or not (the status of the File menu item is used
                // to determine the overall menu item status)
                setControlsEnabled(mnFile.isEnabled());

                // Step through each table opened in the editor dialog
                for (CcddTableEditorHandler editor : tableEditors) {
                    // Expand all macros in the table if the check box is selected and disable
                    // editing for the table, else restore all macros and enable editing
                    editor.expandMacros(mntmShowMacros.isSelected(), false);
                    editor.setTableEditEnable(!mntmShowMacros.isSelected());
                }

                // Redraw the visible table
                activeEditor.getTable().repaint();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Store All command
        mntmStoreAll.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Store the changes to all open table contents, if any, in the database
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Only update if one or more of the table's has changes and the user confirms the action
                if (isTablesChanged() && new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                        "<html><b>Store changes for all?", "Store Changes", JOptionPane.QUESTION_MESSAGE,
                        DialogOption.OK_CANCEL_OPTION) == OK_BUTTON) {
                    // Update the database for every table that has changes
                    storeAllChanges();
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Reset Order command
        mntmResetOrder.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Reset the column order to the default
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.resetColumnOrder();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Manage Fields command
        mntmManageFields.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Manage the data fields
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Set the field information so that the field information reference in the
                // table information is the same as the panel's field information
                activeEditor.getTableInformation()
                        .setFieldInformation(activeEditor.getInputFieldPanelHandler().getPanelFieldInformation());

                // Create the field editor dialog showing the fields for this table
                new CcddFieldEditorDialog(ccddMain, activeEditor, activeEditor.getTableInformation().getTablePath(),
                        activeEditor.getTableInformation().getFieldInformation(),
                        (activeEditor.getTableTypeDefinition().isStructure()
                                && !activeEditor.getTableInformation().getTablePath().contains(",")),
                        MIN_WINDOW_WIDTH);

                // Update the field information stored in the table information with the fields
                // as updated by the field editor
                activeEditor.getTableInformation()
                        .setFieldInformation(activeEditor.getInputFieldPanelHandler().getPanelFieldInformation());

                // Enable/disable the Clear values command depending on if any data fields remain
                mntmClearValues.setEnabled(!activeEditor.getTableInformation().getFieldInformation().isEmpty());
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add a listener for the Clear values command
        mntmClearValues.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Clear the table data field values
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Check if there are any data fields to clear
                if (!activeEditor.getTableInformation().getFieldInformation().isEmpty()) {
                    // Remove all of the data field values from the table
                    activeEditor.getInputFieldPanelHandler().clearFieldValues();
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Create the lower (button) panel
        JPanel buttonPnl = new JPanel();

        // Define the buttons for the lower panel: New button
        btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row", INSERT_ICON, KeyEvent.VK_I,
                "Insert a new row into the table");

        // Create a listener for the Insert Row command
        ActionListener insertAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Insert a new row into the table at the selected location
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().insertEmptyRow(true);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the insert listener to the Insert Row button and menu command
        btnInsertRow.addActionListener(insertAction);
        mntmInsertRow.addActionListener(insertAction);

        // Delete button
        btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row", DELETE_ICON, KeyEvent.VK_D,
                "Delete the selected row(s) from the table");

        // Create a listener for the Delete Row command
        ActionListener deleteAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Delete the selected row(s) from the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().deleteRow(true);
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the delete listener to the Delete Row button and menu command
        btnDeleteRow.addActionListener(deleteAction);
        mntmDeleteRow.addActionListener(deleteAction);

        // Create a listener for the Expand arrays command
        mntmExpColArray.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Expand or collapse the array members
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Toggle between showing and hiding the array member rows
                activeEditor.showHideArrayMembers();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Move Up button
        btnMoveUp = CcddButtonPanelHandler.createButton("Up", UP_ICON, KeyEvent.VK_U, "Move the selected row(s) up");

        // Create a listener for the Move Up command
        ActionListener moveUpAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Move the selected row(s) up in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().moveRowUp();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the move up listener to the Move Up button and menu command
        btnMoveUp.addActionListener(moveUpAction);
        mntmMoveUp.addActionListener(moveUpAction);

        // Move Down button
        btnMoveDown = CcddButtonPanelHandler.createButton("Down", DOWN_ICON, KeyEvent.VK_N,
                "Move the selected row(s) down");

        // Create a listener for the Move Down command
        ActionListener moveDownAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Move the selected row(s) down in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().moveRowDown();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the move down listener to the Move Down button and menu command
        btnMoveDown.addActionListener(moveDownAction);
        mntmMoveDown.addActionListener(moveDownAction);

        // Move Left button
        btnMoveLeft = CcddButtonPanelHandler.createButton("Left", LEFT_ICON, KeyEvent.VK_L,
                "Move the selected column(s) left");

        // Create a listener for the Move Left command
        ActionListener moveLeftAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Move the selected column(s) left in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().moveColumnLeft();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the move left listener to the Move Left button and menu command
        btnMoveLeft.addActionListener(moveLeftAction);
        mntmMoveLeft.addActionListener(moveLeftAction);

        // Move Right button
        btnMoveRight = CcddButtonPanelHandler.createButton("Right", RIGHT_ICON, KeyEvent.VK_R,
                "Move the selected column(s) right");

        // Create a listener for the Move Right command
        ActionListener moveRightAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Move the selected column(s) right in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getTable().moveColumnRight();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the move right listener to the Move Right button and menu command
        btnMoveRight.addActionListener(moveRightAction);
        mntmMoveRight.addActionListener(moveRightAction);

        // Undo button
        btnUndo = CcddButtonPanelHandler.createButton("Undo", UNDO_ICON, KeyEvent.VK_Z, "Undo the last edit action");

        // Create a listener for the Undo command
        ActionListener undoAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Undo the last cell edit
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getFieldPanelUndoManager().undo();

                // Update the data field background colors
                activeEditor.getInputFieldPanelHandler().setFieldBackgound();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the undo listener to the Undo button and menu command
        mntmUndo.addActionListener(undoAction);
        btnUndo.addActionListener(undoAction);

        // Redo button
        btnRedo = CcddButtonPanelHandler.createButton("Redo", REDO_ICON, KeyEvent.VK_Y,
                "Redo the last undone edit action");

        // Create a listener for the Redo command
        ActionListener redoAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Redo the last cell edit that was undone
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                activeEditor.getFieldPanelUndoManager().redo();

                // Update the data field background colors
                activeEditor.getInputFieldPanelHandler().setFieldBackgound();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the redo listener to the Redo button and menu command
        mntmRedo.addActionListener(redoAction);
        btnRedo.addActionListener(redoAction);

        // Store button
        btnStore = CcddButtonPanelHandler.createButton("Store", STORE_ICON, KeyEvent.VK_S,
                "Store the table updates in the database");

        // Create a listener for the Store command
        ActionListener storeAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Store the changes to the table contents, if any, in the database
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Only update the table in the database if a cell's content has changed, no
                // required columns are empty, and the user confirms the action
                if (activeEditor.isTableChanged() && !activeEditor.checkForMissingColumns()
                        && new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                "<html><b>Store changes in project database?", "Store Changes",
                                JOptionPane.QUESTION_MESSAGE, DialogOption.OK_CANCEL_OPTION) == OK_BUTTON) {
                    // Store the changes for the currently displayed editor in the database
                    storeChanges(activeEditor);
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the store listener to the Store button and menu command
        btnStore.addActionListener(storeAction);
        mntmStore.addActionListener(storeAction);

        // Close button
        btnCloseActive = CcddButtonPanelHandler.createButton("Close", CLOSE_ICON, KeyEvent.VK_C,
                "Close the table editor");

        // Add a listener for the Close active table command
        ActionListener closeAction = new ValidateCellActionListener() {
            /**************************************************************************************
             * Close the active editor
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                // Check if the contents of the last cell edited in the table is validated and that
                // none of the tables in the editor have uncommitted changes. If changes exist then
                // confirm discarding the changes
                if (activeEditor.getTable().isLastCellValid() && (!activeEditor.isTableChanged()
                        || new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                                "<html><b>Discard changes?", "Discard Changes", JOptionPane.QUESTION_MESSAGE,
                                DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)) {
                    // Close the active table. If this is the only table in the editor then close
                    // the editor
                    closeTableEditor(activeEditor.getOwnerName());
                }
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        };

        // Add the close listener to the Close button and menu command
        btnCloseActive.addActionListener(closeAction);
        mntmCloseActive.addActionListener(closeAction);

        // Create a listener for the Close All menu command
        mntmCloseAll.addActionListener(new ValidateCellActionListener() {
            /**************************************************************************************
             * Close the table editor
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae) {
                windowCloseButtonAction();
            }

            /**************************************************************************************
             * Get the reference to the currently displayed table
             *************************************************************************************/
            @Override
            protected CcddJTableHandler getTable() {
                return activeEditor.getTable();
            }
        });

        // Add buttons in the order in which they'll appear (left to right, top to bottom)
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

        // Create a tabbed pane for the editors to appear in
        tabbedPane = new DnDTabbedPane(JTabbedPane.TOP, CcddTableEditorDialog.class, true) {
            /**************************************************************************************
             * Update the table editor list order following a tab move
             *************************************************************************************/
            @Override
            protected Object tabMoveCleanup(int oldTabIndex, int newTabIndex, Object tabContents) {
                CcddTableEditorHandler editor = (CcddTableEditorHandler) tabContents;

                // Check if the tab originated in the editor dialog
                if (oldTabIndex != -1) {
                    // Get the reference to the moved tab's original location in the list
                    editor = tableEditors.get(oldTabIndex);

                    // Remove the table editor reference associated with the tab
                    tableEditors.remove(oldTabIndex);
                }
                // The tab originated in another editor dialog
                else {
                    // Bring the editor dialog to the foreground
                    CcddTableEditorDialog.this.toFront();

                    // Remove any search highlighting in case a search dialog is active
                    editor.getTable().highlightSearchText(null);
                }

                // Check if the tab is to be placed within this editor
                if (newTabIndex != -1) {
                    // Update the editor's reference to the dialog that owns it to this editor dialog
                    editor.setEditorDialog(CcddTableEditorDialog.this);

                    // Add the table editor reference at the specified location
                    tableEditors.add(newTabIndex - (oldTabIndex != -1 && newTabIndex > oldTabIndex ? 1 : 0), editor);
                }

                // Check if the last editor was removed from the dialog
                if (tableEditors.isEmpty()) {
                    // Close the editor dialog
                    CcddTableEditorDialog.this.closeFrame();
                }
                // An editor remains in the dialog
                else {
                    // Update the active tab pointer
                    activeEditor = tableEditors.get(tabbedPane.getSelectedIndex());
                }

                return editor;
            }

            /**************************************************************************************
             * Move the specified tab's table to a new table editor dialog
             *************************************************************************************/
            @Override
            protected void spawnContainer(int tabIndex, Object tabContents) {
                // Create a new table editor dialog and place the editor from the other dialog into it
                ccddMain.getTableEditorDialogs()
                        .add(new CcddTableEditorDialog(ccddMain, (CcddTableEditorHandler) tabContents));

                // Update the command menu items so that the new editor has the recent tables menu items
                ccddMain.updateRecentTablesMenu();
            }
        };

        tabbedPane.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());

        // Listen for tab selection changes
        tabbedPane.addChangeListener(new ChangeListener() {
            /**************************************************************************************
             * Update the editor to the one associated with the selected tab
             *************************************************************************************/
            @Override
            public void stateChanged(ChangeEvent ce) {
                // Check if the tab index is within bounds
                if (tabbedPane.getSelectedIndex() >= 0 && tabbedPane.getSelectedIndex() < tableEditors.size()) {
                    // Check if the search dialog is active
                    if (searchDlg != null && searchDlg.isShowing()) {
                        // Undo the search in the previous editor and enable it in the new one
                        searchDlg.setActiveEditor(tableEditors.get(tabbedPane.getSelectedIndex()).getTable());
                    }

                    // Set the active editor to the selected tab
                    activeEditor = tableEditors.get(tabbedPane.getSelectedIndex());

                    // Force the table row heights to update in case this is the first time this
                    // table is showing in its editor
                    activeEditor.getTable().tableChanged(null);

                    // Change the dialog's title to the active table's name
                    (CcddTableEditorDialog.this).setTitle(activeEditor.getOwnerName());

                    // Update the expand/collapse arrays check box
                    updateExpandArrayCheckBox();

                    // Update the editor controls state
                    setControlsEnabled(mnFile.isEnabled());
                }
            }
        });

        // Add each table as a tab in the editor dialog tabbed pane
        addTablePanes(tableInformation, editor);

        // Set the first tab as the active editor
        activeEditor = tableEditors.get(0);

        // Display the table editor dialog
        createFrame(ccddMain.getMainFrame(), tabbedPane, buttonPnl, null, activeEditor.getOwnerName(), null);

        // Enable the editor controls
        setControlsEnabled(true);
    }

    /**********************************************************************************************
     * Update the expand/collapse arrays check box to reflect the current state of
     * the active editor
     *********************************************************************************************/
    protected void updateExpandArrayCheckBox() {
        mntmExpColArray.setSelected(activeEditor.isExpanded());
    }

    /**********************************************************************************************
     * Get the selection state of the Array - Overwrite all radio button
     *
     * @return true if the Array - Overwrite all radio button is selected
     *********************************************************************************************/
    protected boolean isArrayOverwriteAll() {
        return mntmOverwriteAll.isSelected();
    }

    /**********************************************************************************************
     * Get the selection state of the Array - Overwrite none radio button
     *
     * @return true if the Array - Overwrite none radio button is selected
     *********************************************************************************************/
    protected boolean isArrayOverwriteNone() {
        return mntmOverwriteNone.isSelected();
    }

    /**********************************************************************************************
     * Perform a control key action
     *
     * @param key key to simulate pressing along with the control key
     *********************************************************************************************/
    private void controlKeyAction(int key) {
        try {
            // Create a robot to simulate key press events
            Robot robot = new Robot();

            // Send the equivalent Ctrl+<key> event
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(key);
            robot.keyRelease(key);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } catch (AWTException awte) {
            // Inform the user that key presses cannot be simulated
            new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this,
                    "<html><b>Platform does not allow key press simulation", "Invalid Input",
                    JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
        }
    }

    /**********************************************************************************************
     * Commit changes to the database for the specified table editor
     *
     * @param editor table editor to commit
     *********************************************************************************************/
    private void storeChanges(CcddTableEditorHandler editor) {
        // Build the addition, modification, and deletion command lists
        editor.buildUpdates();

        // Perform the changes to the table in the database
        dbTable.modifyTableDataInBackground(editor.getTableInformation(), editor.getAdditions(),
                editor.getModifications(), editor.getDeletions(), false, false, true, true, true, null,
                CcddTableEditorDialog.this);
    }

    /**********************************************************************************************
     * Commit changes to the database for every table in the editor that has
     * changes. This command is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled
     * until the database command completes execution
     *********************************************************************************************/
    private void storeAllChanges() {
        // Execute the command in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand() {
            /**************************************************************************************
             * Update the database for every table that has changes
             *************************************************************************************/
            @Override
            protected void execute() {
                // Step through each table in this editor dialog
                for (CcddTableEditorHandler editor : tableEditors) {
                    // Check if the table has changes and that no required columns are empty
                    if (editor.isTableChanged() && !activeEditor.checkForMissingColumns()) {
                        // Build the addition, modification, and deletion command lists
                        editor.buildUpdates();

                        // Perform the changes to the table in the database
                        dbTable.modifyTableData(editor.getTableInformation(), editor.getAdditions(),
                                editor.getModifications(), editor.getDeletions(), false, false, true, true, true, null,
                                true, true, false, CcddTableEditorDialog.this);
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Add one or more table tabs to the editor dialog tabbed pane using the
     * supplied table information list
     *
     * @param tableInformation list containing information for each table
     *********************************************************************************************/
    protected void addTablePanes(List<TableInfo> tableInformation) {
        addTablePanes(tableInformation, null);
    }

    /**********************************************************************************************
     * Add one or more table tabs to the editor dialog tabbed pane using the
     * supplied table information list and/or existing table editor tab contents
     *
     * @param tableInformation list containing information for each table
     *
     * @param editor           reference to an existing table editor
     *********************************************************************************************/
    protected void addTablePanes(List<TableInfo> tableInformation, CcddTableEditorHandler editor) {
        // Get the number of table editors already in the editor dialog
        int numExisting = tableEditors.size();

        // Check if a table editor is supplied
        if (editor != null) {
            // Add the editor to the list
            tableEditors.add(editor);

            // Create a tab for the editor
            tabbedPane.addTab(editor.getOwnerName(), null, editor.getFieldPanel(), editor.getTableToolTip());

            // Refresh the editor's change indicator, in case it's added while having unstored changes
            updateChangeIndicator(editor);
        }

        // Check if table information is provided
        if (tableInformation != null) {
            // Step through the tables
            for (TableInfo tableInfo : tableInformation) {
                // Create an editor for this table and add it to the list of editors
                editor = new CcddTableEditorHandler(ccddMain, tableInfo, this);
                tableEditors.add(editor);

                // Create a tab for each table
                tabbedPane.addTab(editor.getOwnerName(), null, editor.getFieldPanel(), editor.getTableToolTip());
            }
        }

        // Check if only a single table was added
        if (tableEditors.size() - numExisting == 1) {
            // Select the tab for the newly opened table
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        }
    }

    /**********************************************************************************************
     * Create and display the find/replace dialog
     *********************************************************************************************/
    protected void findReplace() {
        searchDlg = new CcddFindReplaceDialog(ccddMain, CcddTableEditorDialog.this, activeEditor.getTable()) {
            /**************************************************************************************
             * Display array member rows
             *************************************************************************************/
            @Override
            protected void showAllRows() {
                // Check if the array member rows are hidden
                if (!activeEditor.isExpanded()) {
                    // Show the array member rows
                    activeEditor.showHideArrayMembers();
                }
            }
        };
    }

    /**********************************************************************************************
     * Update the change indicator for the specified table editor
     *
     * @param tableEditor reference to the table editor for which the change
     *                    indicator is to be updated
     *********************************************************************************************/
    protected void updateChangeIndicator(CcddTableEditorHandler tableEditor) {
        // Get the index of the specified tab
        int index = tableEditors.indexOf(tableEditor);

        // Check that the tab index is valid
        if (index != -1) {
            // Replace the tab name, appending the change indicator if changes exist
            tabbedPane.setTitleAt(index, tabbedPane.getTitleAt(index).replaceAll("\\" + CHANGE_INDICATOR, "")
                    + (tableEditor.isTableChanged() ? CHANGE_INDICATOR : ""));
        }
    }

    /**********************************************************************************************
     * Determine if any of the tables represented in this editor dialog have
     * uncommitted changes
     *
     * @return true if any of the editors represented in this editor dialog have
     *         uncommitted changes
     *********************************************************************************************/
    protected boolean isTablesChanged() {
        boolean isChanged = false;

        // Step through each editor in the editor dialog
        for (CcddTableEditorHandler editor : tableEditors) {
            // Check if the table has changed
            if (editor.isTableChanged()) {
                // Set the flag indicated uncommitted changes and stop searching
                isChanged = true;
                break;
            }
        }

        return isChanged;
    }

    /**********************************************************************************************
     * Remove the tab for the specified table
     *
     * @param tableName name of the table to remove
     *********************************************************************************************/
    protected void closeTableEditor(String tableName) {
        // Check if there is only a single table editor is open
        if (tabbedPane.getTabCount() == 1) {
            // Close the table editor dialog
            closeFrame();
        }
        // More than one table editor is open
        else {
            int index = 0;

            // Step through each table editor
            for (CcddTableEditorHandler editor : tableEditors) {
                // Check if the table name matches the specified name
                if (tableName.equals(editor.getOwnerName())) {
                    // Remove the table's editor and tab, and stop searching
                    tableEditors.remove(editor);
                    tabbedPane.removeTabAt(index);
                    break;
                }

                index++;
            }
        }
    }

    /**********************************************************************************************
     * Remove the table editor from the table editor list
     *********************************************************************************************/
    @Override
    protected void windowClosedAction() {
        ccddMain.getTableEditorDialogs().remove(CcddTableEditorDialog.this);
    }

    /**********************************************************************************************
     * Handle the frame close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction() {
        // Check if the contents of the last cell edited in the specified table is validated and
        // that none of the tables in the editor have uncommitted changes. If changes exist then
        // confirm discarding the changes
        if (activeEditor.getTable().isLastCellValid() && (!isTablesChanged()
                || new CcddDialogHandler().showMessageDialog(CcddTableEditorDialog.this, "<html><b>Discard changes?",
                        "Discard Changes", JOptionPane.QUESTION_MESSAGE, DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)) {
            // Close the editor dialog
            closeFrame();
        }
    }

    /**********************************************************************************************
     * Close the table editor dialog
     *********************************************************************************************/
    @Override
    protected void closeFrame() {
        // Check if the search dialog is active
        if (searchDlg != null && searchDlg.isShowing()) {
            // Close the search dialog
            searchDlg.closeDialog();
        }

        super.closeFrame();
    }
}
