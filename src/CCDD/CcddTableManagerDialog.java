/**************************************************************************************************
 * /** \file CcddTableManagerDialog.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Dialog for the user create, edit, copy, rename, and delete data tables. The dialog is
 * built on the CcddDialogHandler class.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.EXPORT_ICON;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SCRIPTS_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command and Data Dictionary table manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableEditorDialog callingEditorDlg;
    private final CcddDbControlHandler dbControl;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFileIOHandler fileIOHandler;
    private CcddTableTreeHandler tableTree;

    // Components referenced by multiple methods
    private JTextField nameFld;
    private final Component caller;
    private final ManagerDialogType dialogType;
    private JTextArea descriptionFld;
    private Border emptyBorder;
    private Border border;
    private JScrollPane descScrollPane;
    private JCheckBox overwriteFileCb;
    private JCheckBox replaceExistingTablesCb;
    private JCheckBox replaceExistingDataTypesCb;
    private JCheckBox appendExistingFieldsCb;
    private JCheckBox useExistingFieldsCb;
    private JCheckBox replaceExistingMacrosCb;
    private JCheckBox replaceExistingGroupsCb;
    private JCheckBox replaceExistingAssociationsCb;
    private JRadioButton singleFileRBtn;
    private JCheckBox openEditorCb;
    private JCheckBox ignoreErrorsCb;
    private JCheckBox backupFirstCb;
    private JCheckBox deleteNonExistingTablesCb;
    private JCheckBox importEntireDatabaseCb;
    private JCheckBox replaceMacrosCb;
    private JCheckBox includeBuildInfoCb;
    private JCheckBox includeReservedMsgIDsCb;
    private JCheckBox includeProjectFieldsCb;
    private JCheckBox includeGroupsCb;
    private JCheckBox includeAssociationsCb;
    private JCheckBox includeTlmSchedCB;
    private JCheckBox includeAppSchedCB;
    private JCheckBox exportEntireDatabaseCb;
    private JCheckBox deleteTargetDirectoryCb;
    private JCheckBox includeAllTableTypesCb;
    private JCheckBox includeAllDataTypesCb;
    private JCheckBox includeAllInputTypesCb;
    private JCheckBox includeAllMacrosCb;
    private JCheckBox includeVariablePaths;
    private JTextField varPathSepFld;
    private JTextField typeNameSepFld;
    private JCheckBox hideDataTypeCb;
    private JLabel exportLbl;
    private JRadioButton bigRBtn;
    private JCheckBox headerBigCBox;
    private JTextField versionFld;
    private JTextField validStatFld;
    private JTextField class1Fld;
    private JTextField class2Fld;
    private JTextField class3Fld;
    private JCheckBox useExternalCBox;
    private JTextField scriptNameFld;
    private JTextField pathFld;

    // Group selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Name of the table selected from the table tree
    private String selectedTableName;

    // Array of new table names to create
    private String[] tableNames;

    // Create a list to store the names of tables that are no longer valid
    private List<String[]> invalidatedEditors;

    /**********************************************************************************************
     * Table manager dialog class constructor (for all tables)
     *
     * @param ccddMain Main class
     *
     * @param type     Table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     *********************************************************************************************/
    protected CcddTableManagerDialog(CcddMain ccddMain, ManagerDialogType type)
    {
        this(ccddMain, type, null);
    }

    /**********************************************************************************************
     * Table manager dialog class constructor
     *
     * @param ccddMain            Main class
     *
     * @param dialogType          Table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE,
     *                            IMPORT, EXPORT_CSV, EXPORT_XTCE, EXPORT_EDS, EXPORT_JSON
     *
     * @param callingEditorDialog Reference to the table editor dialog that instantiated this table
     *                            manager. Only used when called to open a table in, import into,
     *                            or export from an existing editor; null otherwise
     *********************************************************************************************/
    CcddTableManagerDialog(CcddMain ccddMain,
                           ManagerDialogType dialogType,
                           CcddTableEditorDialog callingEditorDialog)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;
        this.callingEditorDlg = callingEditorDialog;

        // Get a reference to the calling component
        caller = (callingEditorDialog == null) ? ccddMain.getMainFrame() : callingEditorDialog;

        // Create references to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fileIOHandler = ccddMain.getFileIOHandler();

        // Create the table manager dialog
        initialize();
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of a table management operation
     *********************************************************************************************/
    protected void doTableOperationComplete()
    {
        // Update the lists and references since the tables changed
        dbTable.updateListsAndReferences(caller);

        // Update the field handler's data field information from the field definitions stored in
        // the database
        ccddMain.getFieldHandler().buildFieldInformation(caller);

        // Perform the steps based on the dialog type
        switch (dialogType)
        {
            case NEW:
            case COPY:
                // Update any open editor's data type columns to include the new table(s), if
                // applicable
                dbTable.updateDataTypeColumns(caller);
                break;

            case RENAME:
                // Check if any editor dialogs are open
                if (!ccddMain.getTableEditorDialogs().isEmpty())
                {
                    // Get the table's original name
                    String oldName = tableTree.getSelectionPath().getLastPathComponent().toString();

                    // Step through the open table editor dialogs
                    for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                    {
                        // Step through each individual editor
                        for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                        {
                            // Update the table names in the open editors
                            CcddTableEditorDialog.updateTableNames(ccddMain,
                                                                   oldName,
                                                                   nameFld.getText(),
                                                                   null,
                                                                   null,
                                                                   editorDialog,
                                                                   editor);
                        }
                    }

                    // Update any open editor's data type columns to account for the renamed
                    // table(s), if applicable
                    dbTable.updateDataTypeColumns(caller);
                }

                break;

            case DELETE:
                // Close the table editor(s) for for the deleted table(s)
                dbTable.closeDeletedTableEditors(invalidatedEditors, caller);
                break;

            default:
                break;
        }

        // Update the script associations manager and executive dialogs
        ccddMain.updateScriptAssociationsDialogs();
    }

    /**********************************************************************************************
     * Create the table manager dialog. This is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the table manager dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            boolean errorFlag = false;
            JPanel dialogPnl;
            FileExtension fileExtn;

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

            /**************************************************************************************
             * Build the table manager dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                isNodeSelectionChanging = false;

                // Create an empty border
                emptyBorder = BorderFactory.createEmptyBorder();

                // Create a border for the input fields
                border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                            Color.LIGHT_GRAY,
                                                                                            Color.GRAY),
                                                            BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                            ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing()));

                // Create dialog based on supplied dialog type
                switch (dialogType)
                {
                    case NEW:
                        // Create a panel to contain the dialog components
                        dialogPnl = new JPanel(new GridBagLayout());
                        dialogPnl.setBorder(emptyBorder);

                        // Create a panel containing a grid of radio buttons representing the table
                        // types from which to choose
                        if (addRadioButtons(null, false, tableTypeHandler.getTypeInformation(), null,
                                            "Select table type", false, dialogPnl, gbc))
                        {
                            // Create the table creation dialog label and field, and add them to
                            // the dialog panel
                            addTableInputFields("Table name(s)", dialogPnl, true, gbc);
                            nameFld.setToolTipText(CcddUtilities.wrapText("Delimit multiple names using commas",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                        }
                        // There are no table types available from which to choose
                        else
                        {
                            // Inform the user that no table type exists in the database
                            new CcddDialogHandler().showMessageDialog(caller,
                                                                      "<html><b>Project '</b>"
                                                                      + dbControl.getDatabaseName()
                                                                      + "<b>' has no table type defined",
                                                                      "New Table",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                            errorFlag = true;
                        }

                        break;

                    case EDIT:
                        // Create a panel to contain the dialog components
                        dialogPnl = createSelectionPanel("Select table(s) to edit",
                                                         gbc,
                                                         TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                         TableTreeType.TABLES);

                        // Check that the panel was created; i.e., that there are tables available
                        // for editing
                        if (dialogPnl != null)
                        {
                            // Add a listener to the table tree for mouse events
                            tableTree.addMouseListener(new MouseAdapter()
                            {
                                /******************************************************************
                                 * Handle mouse press events
                                 *****************************************************************/
                                @Override
                                public void mousePressed(MouseEvent me)
                                {
                                    // Check if the right mouse button is double clicked
                                    if (me.getClickCount() == 2 && SwingUtilities.isRightMouseButton(me))
                                    {
                                        // Get the selected row in the tree
                                        int rowIndex = tableTree.getClosestRowForLocation(me.getX(), me.getY());

                                        // Check if a valid row was selected
                                        if (rowIndex != -1)
                                        {
                                            // Get the path for the selected row
                                            TreePath path = tableTree.getPathForRow(rowIndex);

                                            // Check if a table was selected
                                            if (path.getPathCount() > tableTree.getHeaderNodeLevel())
                                            {
                                                // Load the selected table's data into a table
                                                // editor and close this dialog; remove the HTML
                                                // tags from the table name
                                                dbTable.loadTableDataInBackground(CcddUtilities.removeHTMLTags(tableTree.getFullVariablePath(path.getPath())),
                                                                                  callingEditorDlg);
                                                closeDialog();
                                            }
                                        }
                                    }
                                }
                            });
                        }
                        // There are no tables available for editing
                        else
                        {
                            errorFlag = true;
                        }

                        break;

                    case RENAME:
                        // Create a panel to contain the dialog components
                        dialogPnl = createSelectionPanel("Select a table to rename",
                                                         gbc,
                                                         TreeSelectionModel.SINGLE_TREE_SELECTION,
                                                         TableTreeType.PROTOTYPE_TABLES);

                        // Check that the panel was created; i.e., that there are tables available
                        // for renaming
                        if (dialogPnl != null)
                        {
                            // Create the table renaming dialog label and field
                            addTableInputFields("New name", dialogPnl, false, gbc);
                        }
                        // There are no tables available for renaming
                        else
                        {
                            errorFlag = true;
                        }

                        break;

                    case COPY:
                        // Create a panel to contain the dialog components
                        dialogPnl = createSelectionPanel("Select a table to copy",
                                                         gbc,
                                                         TreeSelectionModel.SINGLE_TREE_SELECTION,
                                                         TableTreeType.PROTOTYPE_TABLES);

                        // Check that the panel was created; i.e., that there are tables available
                        // for copying
                        if (dialogPnl != null)
                        {
                            // Create the table copying dialog label and field
                            addTableInputFields("Copy name", dialogPnl, false, gbc);
                        }
                        // There are no tables available for copying
                        else
                        {
                            errorFlag = true;
                        }

                        break;

                    case DELETE:
                        // Create a panel to contain the dialog components
                        dialogPnl = createSelectionPanel("Select table(s) to delete",
                                                         gbc,
                                                         TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                         TableTreeType.PROTOTYPE_TABLES);

                        // Check if the no panel was created; i.e., that there are no tables
                        // available for deleting
                        if (dialogPnl == null)
                        {
                            errorFlag = true;
                        }

                        break;

                    case IMPORT_JSON:
                    case IMPORT_CSV:
                    case IMPORT_EDS:
                    case IMPORT_XTCE:
                    case IMPORT_C_HEADER:
                        break;

                    case EXPORT_CSV:
                    case EXPORT_XTCE:
                    case EXPORT_EDS:
                    case EXPORT_JSON:
                        switch (dialogType)
                        {
                            // Set the file extension based on the dialog type
                            case EXPORT_CSV:
                                fileExtn = FileExtension.CSV;
                                break;

                            case EXPORT_EDS:
                                fileExtn = FileExtension.EDS;
                                break;

                            case EXPORT_XTCE:
                                fileExtn = FileExtension.XTCE;
                                break;

                            case EXPORT_JSON:
                                fileExtn = FileExtension.JSON;
                                break;

                            default:
                                break;
                        }

                        // Create the export dialog
                        dialogPnl = createExportPanel(fileExtn, gbc);

                        // Check if the export panel doesn't exist
                        if (dialogPnl == null)
                        {
                            errorFlag = true;
                        }

                        break;
                }
            }

            /**************************************************************************************
             * Table manager dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Check that no error occurred creating the dialog
                if (!errorFlag)
                {
                    FileEnvVar[] filePath;
                    FileExtension fileExt = null;
                    boolean importing = false;

                    // Display the dialog based on supplied dialog type
                    switch (dialogType)
                    {
                        case NEW:
                            // Check if the Okay button was selected
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "New Table",
                                                  DialogOption.CREATE_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Create the table(s)
                                dbTable.createTableInBackground(tableNames,
                                                                descriptionFld.getText(),
                                                                getRadioButtonSelected(),
                                                                CcddTableManagerDialog.this);
                            }

                            break;

                        case EDIT:
                            // Display the table selection dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Edit Table",
                                                  DialogOption.OPEN_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Get the list of selected tables, including children
                                List<String> tablePaths = tableTree.getSelectedTablesWithChildren();

                                // Load the selected table's data into a table editor
                                dbTable.loadTableDataInBackground(tablePaths.toArray(new String[0]), callingEditorDlg);
                            }

                            break;

                        case RENAME:
                            // Display the table renaming dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Rename Table",
                                                  DialogOption.RENAME_OPTION,
                                                  true) == OK_BUTTON
                                && !selectedTableName.equals(nameFld.getText()))
                            {
                                // Rename the table
                                dbTable.renameTable(tableTree.getSelectionPath().getLastPathComponent().toString(),
                                                    nameFld.getText(),
                                                    descriptionFld.getText(),
                                                    CcddTableManagerDialog.this);
                            }

                            break;

                        case COPY:
                            // Display the table copying dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Copy Table",
                                                  DialogOption.COPY_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Copy the table
                                dbTable.copyTable(tableTree.getSelectionPath().getLastPathComponent().toString(),
                                                  nameFld.getText(),
                                                  descriptionFld.getText(),
                                                  CcddTableManagerDialog.this);
                            }

                            break;

                        case DELETE:
                            // Create a panel to contain the dialog components Display the table
                            // deletion dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Delete Table",
                                                  DialogOption.DELETE_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Create storage for the list of table names
                                List<String> tableNames = new ArrayList<String>();
                                invalidatedEditors = new ArrayList<String[]>();

                                // Step through each selected table in the tree
                                for (String path : tableTree.getSelectedTablesWithChildren())
                                {
                                    // Add the table name to the list
                                    tableNames.add(path);

                                    // Add the pattern for the data type path of tables matching
                                    // the deleted prototype table
                                    invalidatedEditors.add(new String[] {path, null});
                                }

                                // Check if a table is selected
                                if (!tableNames.isEmpty())
                                {
                                    // Delete the table(s)
                                    dbTable.deleteTableInBackground(tableNames.toArray(new String[0]),
                                                                    CcddTableManagerDialog.this,
                                                                    caller);
                                }
                            }

                            break;

                        case IMPORT_JSON:
                            fileExt = FileExtension.JSON;
                            importing = true;
                            break;

                        case IMPORT_CSV:
                            fileExt = FileExtension.CSV;
                            importing = true;
                            break;

                        case IMPORT_EDS:
                            fileExt = FileExtension.EDS;
                            importing = true;
                            break;

                        case IMPORT_XTCE:
                            fileExt = FileExtension.XTCE;
                            importing = true;
                            break;

                        case IMPORT_C_HEADER:
                            fileExt = FileExtension.C_HEADER;
                            importing = true;
                            break;

                        case EXPORT_CSV:
                        case EXPORT_XTCE:
                        case EXPORT_EDS:
                        case EXPORT_JSON:
                            // Check if the export panel exists; if so display the dialog. The
                            // export dialog called from a table editor is fixed in size due to
                            // initial size sizing issues
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Export Data in "
                                                  + fileExtn.getExtensionName().toUpperCase()
                                                  + " Format",
                                                  DialogOption.EXPORT_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                // Create storage for the list of table paths
                                List<String> tablePaths = new ArrayList<String>();

                                if (exportEntireDatabaseCb.isSelected())
                                {
                                    // Select all current tables in the database and prepare them
                                    // for export
                                    CcddTableTreeHandler TempTableTree = new CcddTableTreeHandler(ccddMain,
                                                                                                  TableTreeType.TABLES,
                                                                                                  null);
                                    TempTableTree.setSelectionInterval(0, TempTableTree.getRowCount());
                                    tablePaths = TempTableTree.getSelectedTablesWithChildren();
                                }
                                // Check if the export command originated from the main menu
                                else if (callingEditorDlg == null)
                                {
                                    // Get the list of selected tables, including children
                                    tablePaths = tableTree.getSelectedTablesWithChildren();

                                    // Add the ancestors of the selected tables to the list
                                    tableTree.addTableAncestors(tablePaths, false);
                                }
                                // The export command originated from a table editor dialog menu
                                else
                                {
                                    // Add the table editor's table variable path to the list
                                    tablePaths.add(callingEditorDlg.getTableEditor().getTableInformation().getTablePath());
                                }

                                // Store the export file path in the backing store
                                CcddFileIOHandler.storePath(ccddMain,
                                                            pathFld.getText(),
                                                            (singleFileRBtn != null ? singleFileRBtn.isSelected()
                                                                                    : (dialogType == ManagerDialogType.EXPORT_XTCE
                                                                                      || dialogType == ManagerDialogType.EXPORT_EDS)),
                                                            ModifiablePathInfo.TABLE_EXPORT_PATH);

                                // Export the contents of the selected table(s) in the specified
                                // format
                                fileIOHandler.exportSelectedTablesInBackground(pathFld.getText(),
                                                                               tablePaths.toArray(new String[0]),
                                                                               overwriteFileCb.isSelected(),
                                                                               (singleFileRBtn != null ? singleFileRBtn.isSelected() : true),
                                                                               (includeBuildInfoCb != null ? includeBuildInfoCb.isSelected() : true),
                                                                               (replaceMacrosCb != null ? replaceMacrosCb.isSelected() : true),
                                                                               (deleteTargetDirectoryCb != null ? deleteTargetDirectoryCb.isSelected() : false),
                                                                               (includeAllTableTypesCb != null ? includeAllTableTypesCb.isSelected() : false),
                                                                               (includeAllDataTypesCb != null ? includeAllDataTypesCb.isSelected() : false),
                                                                               (includeAllInputTypesCb != null ? includeAllInputTypesCb.isSelected() : false),
                                                                               (includeAllMacrosCb != null ? includeAllMacrosCb.isSelected() : false),
                                                                               (includeReservedMsgIDsCb != null ? includeReservedMsgIDsCb.isSelected() : false),
                                                                               (includeProjectFieldsCb != null ? includeProjectFieldsCb.isSelected() : false),
                                                                               (includeGroupsCb != null ? includeGroupsCb.isSelected() : false),
                                                                               (includeAssociationsCb != null ? includeAssociationsCb.isSelected() : false),
                                                                               (includeTlmSchedCB != null ? includeTlmSchedCB.isSelected() : false),
                                                                               (includeAppSchedCB != null ? includeAppSchedCB.isSelected() : false),
                                                                               (includeVariablePaths != null ? includeVariablePaths.isSelected() : false),
                                                                               (includeVariablePaths != null
                                                                                && includeVariablePaths.isSelected() ? ccddMain.getVariableHandler()
                                                                                                                     : null),
                                                                               (varPathSepFld != null ? new String[] {varPathSepFld.getText(),
                                                                                                                      Boolean.toString(hideDataTypeCb.isSelected()),
                                                                                                                      typeNameSepFld.getText()}
                                                                                                      : null),
                                                                               fileExtn,
                                                                               (bigRBtn != null ? (bigRBtn.isSelected() ? EndianType.BIG_ENDIAN
                                                                                                                        : EndianType.LITTLE_ENDIAN)
                                                                                                : null),
                                                                               (bigRBtn != null
                                                                                && headerBigCBox != null ? (bigRBtn.isSelected() || headerBigCBox.isSelected()) : false),
                                                                               (versionFld != null ? versionFld.getText()
                                                                                                   : null),
                                                                               (validStatFld != null ? validStatFld.getText()
                                                                                                     : null),
                                                                               (class1Fld != null ? class1Fld.getText()
                                                                                                  : null),
                                                                               (class2Fld != null ? class2Fld.getText()
                                                                                                  : null),
                                                                               (class3Fld != null ? class3Fld.getText()
                                                                                                  : null),
                                                                               (useExternalCBox != null ? useExternalCBox.isSelected() : false),
                                                                               (scriptNameFld != null ? scriptNameFld.getText() : null),
                                                                               CcddTableManagerDialog.this);
                            }

                            break;

                        default:
                            // Should never make it here
                            break;
                    }

                    // Check if data is being imported
                    if (importing)
                    {
                        // Check to see if the callingEditorDlg represents an open table editor or
                        // if it is null. If it is an open table editor then we are doing a single
                        // table import into the open table editor. If not the import was called
                        // from the main CCDD dialog window and is a normal import that could
                        // include a single file or multiple files with no open table editor
                        // needing updates
                        if (callingEditorDlg == null)
                        {
                            // Allow the user to select the data file path + name(s) from which to
                            // import, and the import options
                            filePath = choosePathFile(ccddMain,
                                                      caller,
                                                      null,
                                                      "export",
                                                      new FileNameExtensionFilter[] {new FileNameExtensionFilter(fileExt.getDescription(),
                                                                                                                 fileExt.getExtensionName())},
                                                      false,
                                                      true,
                                                      "Import Data in "
                                                      + (fileExt != FileExtension.C_HEADER ? fileExt.getExtensionName().toUpperCase()
                                                                                           : "C Header")
                                                      + " Format",
                                                      ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(),
                                                                                  null),
                                                      DialogOption.IMPORT_OPTION,
                                                      createImportPanel(gbc, dialogType));

                            // Check if a file was chosen
                            if (filePath != null)
                            {
                                // Store the import file path in the backing store
                                CcddFileIOHandler.storePath(ccddMain,
                                                            filePath[0].getAbsolutePath(),
                                                            true,
                                                            ModifiablePathInfo.TABLE_EXPORT_PATH);

                                // Export the contents of the selected table(s) in the specified
                                // format
                                fileIOHandler.importFileInBackground(filePath,
                                                                     (importEntireDatabaseCb != null ? importEntireDatabaseCb.isSelected() : false),
                                                                     (backupFirstCb != null ? backupFirstCb.isSelected() : false),
                                                                     (replaceExistingTablesCb != null ? replaceExistingTablesCb.isSelected() : false),
                                                                     (appendExistingFieldsCb != null ? appendExistingFieldsCb.isSelected() : false),
                                                                     (useExistingFieldsCb != null ? useExistingFieldsCb.isSelected() : false),
                                                                     (openEditorCb != null ? openEditorCb.isSelected() : false),
                                                                     (ignoreErrorsCb != null ? ignoreErrorsCb.isSelected() : false),
                                                                     (replaceExistingMacrosCb != null ? replaceExistingMacrosCb.isSelected()
                                                                                                      : (dialogType == ManagerDialogType.IMPORT_C_HEADER ? true : false)),
                                                                     (replaceExistingGroupsCb != null ? replaceExistingGroupsCb.isSelected() : false),
                                                                     (replaceExistingAssociationsCb != null ? replaceExistingAssociationsCb.isSelected() : false),
                                                                     (deleteNonExistingTablesCb != null ? deleteNonExistingTablesCb.isSelected() : false),
                                                                     (replaceExistingDataTypesCb != null ? replaceExistingDataTypesCb.isSelected() : false),
                                                                     fileExt,
                                                                     dialogType,
                                                                     null,
                                                                     CcddTableManagerDialog.this);
                            }
                        }
                        else
                        {
                            // Importing into an open table editor
                            JPanel dialogPanel = createTableEditorImportPanel(dialogType);
                            fileIOHandler.importSelectedFileIntoTable(dialogType,
                                                                      dialogPanel,
                                                                      callingEditorDlg.getTableEditor());
                        }
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Create the table tree selection panel
     *
     * @param labelText   Label to display above the table tree
     *
     * @param gbc         GridBagLayout layout constraints
     *
     * @param tableSelect Table tree item selection model
     *
     * @param treeType    Table tree type: PROTOTYPE_ONLY to show only the prototype tables,
     *                    INSTANCE_ONLY to show only the table instances (parent tables with child
     *                    tables), or BOTH to show the prototypes and instances
     *
     * @return JPanel containing the selection panel
     *********************************************************************************************/
    private JPanel createSelectionPanel(String labelText,
                                        GridBagConstraints gbc,
                                        int tableSelect,
                                        TableTreeType treeType)
    {
        // Create a panel to hold the components of the dialog
        JPanel allPnl = null;

        // Build the table tree
        tableTree = new CcddTableTreeHandler(ccddMain,
                                             new CcddGroupHandler(ccddMain,
                                                                  null,
                                                                  ccddMain.getMainFrame()),
                                             treeType,
                                             true,
                                             false,
                                             false,
                                             ccddMain.getMainFrame())
        {
            /**************************************************************************************
             * Respond to changes in selection of a table in the table tree
             *************************************************************************************/
            @Override
            protected void updateTableSelection()
            {
                // Check that a node selection change is not in progress
                if (!isNodeSelectionChanging)
                {
                    // Set the flag to prevent table tree updates
                    isNodeSelectionChanging = true;

                    // Check if this is a rename or copy dialog
                    if (dialogType == ManagerDialogType.RENAME || dialogType == ManagerDialogType.COPY)
                    {
                        // Check if a table is selected
                        if (getSelectionPath() != null && getSelectionPath().getPathCount() > getHeaderNodeLevel())
                        {
                            // Get the name of the table selected
                            selectedTableName = getSelectionPath().getLastPathComponent().toString();

                            // Get the table description
                            descriptionFld.setText(tableTree.getTableDescription(selectedTableName, ""));

                            // Check if this is a copy table dialog
                            if (dialogType == ManagerDialogType.COPY)
                            {
                                // Append text to the name to differentiate the copy from the
                                // original
                                selectedTableName += "_copy";
                            }

                            // Set the new name field to match the selected table's name
                            nameFld.setText(selectedTableName);

                            // Enable and set the background color for the table name and
                            // description fields
                            nameFld.setEditable(true);
                            nameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                            descriptionFld.setEditable(true);
                            descriptionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                            descScrollPane.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                        }
                        // Not a rename or copy dialog, or no table is selected
                        else
                        {
                            // Clear, disable, and set the background color for the table name and
                            // description fields
                            nameFld.setText("");
                            descriptionFld.setText("");
                            nameFld.setEditable(false);
                            nameFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
                            descriptionFld.setEditable(false);
                            descriptionFld.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
                            descScrollPane.setBackground(ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
                        }
                    }

                    // Reset the flag to allow table tree updates
                    isNodeSelectionChanging = false;
                }
            }
        };

        // Check if the project database contains any tables
        if (tableTree.getRootNode().getChildCount() != 0)
        {
            // Create panels to hold the components of the dialog
            allPnl = new JPanel(new GridBagLayout());
            JPanel upperPnl = new JPanel(new GridBagLayout());
            JPanel lowerPnl = new JPanel(new GridBagLayout());
            allPnl.setBorder(emptyBorder);
            upperPnl.setBorder(emptyBorder);
            lowerPnl.setBorder(emptyBorder);

            // Create the overall dialog label and add it to the dialog
            JLabel dlgLabel = new JLabel(labelText);
            dlgLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            upperPnl.add(dlgLabel, gbc);
            allPnl.add(upperPnl, gbc);

            // Create the table tree panel with selection check boxes and add it to the dialog
            gbc.weighty = 1.0;
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            lowerPnl.add(tableTree.createTreePanel("Tables",
                                                   tableSelect,
                                                   false,
                                                   ccddMain.getMainFrame()),
                         gbc);
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.gridy++;
            allPnl.add(lowerPnl, gbc);
        }
        // No tables are stored in the project database
        else
        {
            // Inform the user that no table exists for this database
            new CcddDialogHandler().showMessageDialog(caller,
                                                      "<html><b>Project '</b>"
                                                      + dbControl.getDatabaseName()
                                                      + "<b>' has no tables",
                                                      "No Tables",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return allPnl;
    }

    /**********************************************************************************************
     * Create the import option dialog panel
     *
     * @param gbc Reference to the panel's GridBagConstraints
     *
     * @param dialogType  Dialog type: IMPORT_CSV, IMPORT_EDS, IMPORT_JSON, or IMPORT_XTCE
     *
     * @return Import option dialog panel
     *********************************************************************************************/
    private JPanel createImportPanel(GridBagConstraints gbc, ManagerDialogType dialogType)
    {
        // Create a panel to hold the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        gbc.weightx = 0.0;
        gbc.insets.top = 0;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();

        // Check if this a CSV or JSON import dialog
        if ((dialogType == ManagerDialogType.IMPORT_CSV)
            || (dialogType == ManagerDialogType.IMPORT_JSON))
        {
            // Create a check box for indicating if an entire database is to be imported
            importEntireDatabaseCb = new JCheckBox("Import an entire database");
            importEntireDatabaseCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            importEntireDatabaseCb.setBorder(emptyBorder);
            importEntireDatabaseCb.setToolTipText(CcddUtilities.wrapText("Import an entire database (tables, table types, etc.)",
                                                                         ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            importEntireDatabaseCb.setEnabled(true);

            // Add a listener for changes to the i,port entire database check box selection status
            importEntireDatabaseCb.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Handle a change to the Replace Existing Tables check box selection status
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    replaceExistingMacrosCb.setEnabled(!importEntireDatabaseCb.isSelected());
                    replaceExistingMacrosCb.setSelected(importEntireDatabaseCb.isSelected());
                    replaceExistingTablesCb.setEnabled(!importEntireDatabaseCb.isSelected());
                    replaceExistingTablesCb.setSelected(importEntireDatabaseCb.isSelected());
                    replaceExistingGroupsCb.setEnabled(!importEntireDatabaseCb.isSelected());
                    replaceExistingGroupsCb.setSelected(importEntireDatabaseCb.isSelected());
                    deleteNonExistingTablesCb.setEnabled(!importEntireDatabaseCb.isSelected());
                    deleteNonExistingTablesCb.setSelected(importEntireDatabaseCb.isSelected());
                    appendExistingFieldsCb.setEnabled(importEntireDatabaseCb.isSelected());
                }
            });

            dialogPnl.add(importEntireDatabaseCb, gbc);
            gbc.gridy++;
        }

        // Create a check box for indicating existing tables can be replaced
        replaceExistingTablesCb = new JCheckBox("Replace existing table(s)");
        replaceExistingTablesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        replaceExistingTablesCb.setBorder(emptyBorder);
        replaceExistingTablesCb.setToolTipText(CcddUtilities.wrapText("Replace data table that already exists with the imported table",
                                                                      ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Add a listener for changes to the Replace Existing Tables check box selection status
        replaceExistingTablesCb.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change to the Replace Existing Tables check box selection status
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Set the Append Existing Fields check box status based on the Replace Existing
                // Tables check box status
                appendExistingFieldsCb.setEnabled(replaceExistingTablesCb.isSelected());
            }
        });

        dialogPnl.add(replaceExistingTablesCb, gbc);

        // Create a check box for indicating existing data fields are retained
        appendExistingFieldsCb = new JCheckBox("Append existing data fields");
        appendExistingFieldsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        appendExistingFieldsCb.setBorder(emptyBorder);
        appendExistingFieldsCb.setToolTipText(CcddUtilities.wrapText("Append existing data fields to those imported",
                                                                     ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        appendExistingFieldsCb.setEnabled(false);

        // Add a listener for changes to the Append Existing Fields check box selection status
        appendExistingFieldsCb.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change to the Append Existing Fields check box selection status
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Set the Use Existing Fields check box status based on the Append Existing Fields
                // check box status
                useExistingFieldsCb.setEnabled(appendExistingFieldsCb.isSelected());
            }
        });

        gbc.insets.left += ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.gridy++;
        dialogPnl.add(appendExistingFieldsCb, gbc);

        // Create a check box for indicating existing tables can be replaced
        useExistingFieldsCb = new JCheckBox("Use existing field if duplicate");
        useExistingFieldsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        useExistingFieldsCb.setBorder(emptyBorder);
        useExistingFieldsCb.setToolTipText(CcddUtilities.wrapText("Use the existing data field definition if "
                                                                  + "a field with the same name is imported",
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        useExistingFieldsCb.setEnabled(false);
        gbc.insets.left += ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
        gbc.gridy++;
        dialogPnl.add(useExistingFieldsCb, gbc);
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();

        // Check if this a CSV or JSON import dialog
        if ((dialogType == ManagerDialogType.IMPORT_CSV)
            || (dialogType == ManagerDialogType.IMPORT_JSON))
        {
            // Create a check box for indicating existing data types can be replaced
            replaceExistingDataTypesCb = new JCheckBox("Replace existing data types");
            replaceExistingDataTypesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            replaceExistingDataTypesCb.setBorder(emptyBorder);
            replaceExistingDataTypesCb.setToolTipText(CcddUtilities.wrapText("Replace data types that already exist which "
                                                                             + "share the name with an imported data type",
                                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.gridy++;
            dialogPnl.add(replaceExistingDataTypesCb, gbc);

            // Create a check box for indicating existing macro values can be replaced
            replaceExistingMacrosCb = new JCheckBox("Replace existing macros");
            replaceExistingMacrosCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            replaceExistingMacrosCb.setBorder(emptyBorder);
            replaceExistingMacrosCb.setToolTipText(CcddUtilities.wrapText("If a macro that is imported shares the same name as "
                                                                          + "an existing one, the existing one will be replaced",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            replaceExistingMacrosCb.setEnabled(true);
            gbc.gridy++;
            dialogPnl.add(replaceExistingMacrosCb, gbc);

            // Create a check box for indicating existing group definitions can be replaced
            replaceExistingGroupsCb = new JCheckBox("Replace existing groups");
            replaceExistingGroupsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            replaceExistingGroupsCb.setBorder(emptyBorder);
            replaceExistingGroupsCb.setToolTipText(CcddUtilities.wrapText("Replace group definitions for groups that already exist",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            replaceExistingGroupsCb.setEnabled(true);
            gbc.gridy++;
            dialogPnl.add(replaceExistingGroupsCb, gbc);

            // Create a check box for indicating existing group definitions can be replaced
            replaceExistingAssociationsCb = new JCheckBox("Replace existing associations");
            replaceExistingAssociationsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            replaceExistingAssociationsCb.setBorder(emptyBorder);
            replaceExistingAssociationsCb.setToolTipText(CcddUtilities.wrapText("Replace associations for groups that already exist",
                                                                                ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            replaceExistingAssociationsCb.setEnabled(true);
            gbc.gridy++;
            dialogPnl.add(replaceExistingAssociationsCb, gbc);
        }

        gbc.weightx = 1.0;
        gbc.gridx++;
        gbc.gridy = 0;

        // Create a check box for indicating that the project should be backed up prior to
        // importing tables
        backupFirstCb = new JCheckBox("Backup project before importing");
        backupFirstCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        backupFirstCb.setBorder(emptyBorder);
        backupFirstCb.setToolTipText(CcddUtilities.wrapText("Back up the project database prior to importing the table files",
                                                            ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        backupFirstCb.setEnabled(true);
        dialogPnl.add(backupFirstCb, gbc);

        // Create a check box for indicating that the a table editor should be opened for each
        // imported table
        openEditorCb = new JCheckBox("Open editor for each imported table");
        openEditorCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        openEditorCb.setBorder(emptyBorder);
        openEditorCb.setToolTipText(CcddUtilities.wrapText("Open a table editor for each imported table",
                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        openEditorCb.setEnabled(true);
        gbc.gridy++;
        dialogPnl.add(openEditorCb, gbc);

        // Create a check box for indicating that all errors in the import file should be ignored
        ignoreErrorsCb = new JCheckBox("Ignore all import file errors");
        ignoreErrorsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        ignoreErrorsCb.setBorder(emptyBorder);
        ignoreErrorsCb.setToolTipText(CcddUtilities.wrapText("Ignore all import file errors and continue importing "
                                                             + "(applies to CSV and JSON imports only",
                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        ignoreErrorsCb.setEnabled(true);
        gbc.gridy++;
        dialogPnl.add(ignoreErrorsCb, gbc);

        // Check if this a CSV or JSON import dialog
        if ((dialogType == ManagerDialogType.IMPORT_CSV)
            || (dialogType == ManagerDialogType.IMPORT_JSON))
        {
            // Create a check box for indicating if tables that are not in the import should be
            // deleted from the database
            deleteNonExistingTablesCb = new JCheckBox("Delete undefined tables");
            deleteNonExistingTablesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            deleteNonExistingTablesCb.setBorder(emptyBorder);
            deleteNonExistingTablesCb.setToolTipText(CcddUtilities.wrapText("Delete a table from the database if it is not defined in the import file(s)",
                                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            deleteNonExistingTablesCb.setEnabled(true);
            gbc.gridy++;
            dialogPnl.add(deleteNonExistingTablesCb, gbc);
        }

        return dialogPnl;
    }

    /**********************************************************************************************
     * Create the import via table editor option dialog panel
     *
     * @param dialogType Dialog type: IMPORT_CSV, IMPORT_EDS, IMPORT_JSON, or IMPORT_XTCE
     *
     * @return Import option dialog panel
     *********************************************************************************************/
    private JPanel createTableEditorImportPanel(ManagerDialogType dialogType)
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        // Create a panel to contain the check boxes
        JPanel dialogPanel = new JPanel(new GridBagLayout());
        dialogPanel.setBorder(emptyBorder);

        // Create overwrite check box
        final JCheckBox overwriteChkBx = new JCheckBox("Overwrite existing table data");
        overwriteChkBx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        overwriteChkBx.setBorder(emptyBorder);
        overwriteChkBx.setToolTipText(CcddUtilities.wrapText("Overwrite existing table data; if checked then all "
                                                             + "data will be replaced with the imported data",
                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        overwriteChkBx.setSelected(true);

        // Create a check box for indicating existing tables can be replaced
        final JCheckBox appendToExistingDataCb = new JCheckBox("Append data");
        appendToExistingDataCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        appendToExistingDataCb.setBorder(emptyBorder);
        appendToExistingDataCb.setToolTipText(CcddUtilities.wrapText("Imported data will be appended to the current "
                                                                     + "data. If duplicate variable name/field then "
                                                                     + "the existing data will be kept. If this is an "
                                                                     + "XTCE import then this only applies to "
                                                                     + "duplicate variable names.",
                                                                     ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        appendToExistingDataCb.setSelected(false);
        appendToExistingDataCb.setEnabled(false);

        // Create a check box for indicating that all errors in the import file should be ignored
        JCheckBox ignoreErrorsCb = new JCheckBox("Ignore all import file errors");
        ignoreErrorsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        ignoreErrorsCb.setBorder(emptyBorder);
        ignoreErrorsCb.setToolTipText(CcddUtilities.wrapText("Ignore all import file errors",
                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // Create a check box for indicating if data fields should be kept. This only applies to
        // XTCE
        JCheckBox keepDataFieldsCb = new JCheckBox("Keep all existing data fields.");
        keepDataFieldsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        keepDataFieldsCb.setBorder(emptyBorder);
        keepDataFieldsCb.setToolTipText(CcddUtilities.wrapText("Keep all existing data fields. if checked. XTCE export "
                                                               + "files do not store information related to data fields "
                                                               + "and all fields will be wiped if this checkbox is not selected.",
                                                               ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

        // If we are importing an XTCE file an additional check-box will be added to the panel
        if (dialogType == ManagerDialogType.IMPORT_XTCE)
        {
            keepDataFieldsCb.setVisible(true);
        }
        else
        {
            keepDataFieldsCb.setVisible(false);
        }

        // Add a listener for changes to the overwrite check box selection status
        overwriteChkBx.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Handle a change to the Replace Existing Tables check box selection status
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if (overwriteChkBx.isSelected())
                {
                    appendToExistingDataCb.setSelected(false);
                    appendToExistingDataCb.setEnabled(false);
                }
                else
                {
                    appendToExistingDataCb.setEnabled(true);
                }
            }
        });

        // The order in which these buttons are added is important to the values stored in
        // CcddConstants.java. By keeping up with the index of each component we are able to access
        // their state with only the JPanel object, dialogPanel, Which this function returns. The
        // constants in question are overwriteExistingCbIndex, appendToExistingDataCbIndex,
        // ignoreErrorsCbIndex and keepDataFieldsCbIndex
        dialogPanel.add(overwriteChkBx, gbc); // Index 0, overwriteExistingCbIndex
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        gbc.gridy++;
        dialogPanel.add(appendToExistingDataCb, gbc); // Index 1, appendToExistingDataCbIndex

        if (dialogType != ManagerDialogType.IMPORT_XTCE)
        {
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        }

        gbc.gridy++;
        dialogPanel.add(ignoreErrorsCb, gbc); // Index 2, ignoreErrorsCbIndex

        if (dialogType == ManagerDialogType.IMPORT_XTCE)
        {
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        }

        gbc.gridy++;
        dialogPanel.add(keepDataFieldsCb, gbc); // Index 3, keepDataFieldsCbIndex

        return dialogPanel;
    }

    /**********************************************************************************************
     * Create the export dialog panel
     *
     * @param fileExtn File extension type
     *
     * @param gbc      GridBagLayout layout constraints
     *
     * @return Export dialog panel
     *********************************************************************************************/
    private JPanel createExportPanel(final FileExtension fileExtn, GridBagConstraints gbc)
    {
        JPanel dialogPnl = null;
        boolean originatedFromTableEditor = false;

        // Check if the export command originated from the main menu
        if (callingEditorDlg == null)
        {
            // Create a panel containing a table tree for the dialog components
            dialogPnl = createSelectionPanel("Select table(s) to export",
                                             gbc,
                                             TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                             TableTreeType.TABLES);

            // Add a separator
            JSeparator upperSep = new JSeparator();
            upperSep.setForeground(dialogPnl.getBackground().darker());
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.insets.bottom = 0;
            gbc.weighty = 0.0;
            gbc.gridy++;
            dialogPnl.add(upperSep, gbc);
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;

            // Check if exporting in CSV or JSON format
            if (dialogType == ManagerDialogType.EXPORT_CSV
                || dialogType == ManagerDialogType.EXPORT_JSON)
            {
                // Create a panel to contain the store in file(s) radio buttons
                JPanel storeInPnl = new JPanel(new GridBagLayout());
                storeInPnl.setBorder(emptyBorder);

                // Create the label for the store in file(s) radio button panel
                JLabel storeInLbl = new JLabel("Store in");
                storeInLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.insets.left = 0;
                storeInPnl.add(storeInLbl, gbc);

                // Set up storage for the store in file(s) radio buttons
                ButtonGroup storeInRBtnGroup = new ButtonGroup();
                singleFileRBtn = new JRadioButton("Single file");
                singleFileRBtn.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                singleFileRBtn.setBorder(emptyBorder);
                storeInRBtnGroup.add(singleFileRBtn);
                gbc.gridx++;
                storeInPnl.add(singleFileRBtn, gbc);
                JRadioButton multipleFileRBtn = new JRadioButton("Multiple files");
                multipleFileRBtn.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                multipleFileRBtn.setBorder(emptyBorder);
                storeInRBtnGroup.add(multipleFileRBtn);
                gbc.gridx++;
                storeInPnl.add(multipleFileRBtn, gbc);

                // Add the store in file(s) selection components to the dialog
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.fill = GridBagConstraints.NONE;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(storeInPnl, gbc);
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.BOTH;

                // Add a listener for the single file radio button selection changes
                singleFileRBtn.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to changes in selection of a the store in single file radio button
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Set the file/path label text
                        exportLbl.setText("Enter or select an export file");

                        // Add the file name to the path in the file/path field
                        setFilePath(true, fileExtn);
                    }
                });

                // Add a listener for the multiple file radio button selection changes
                multipleFileRBtn.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to changes in selection of a the store in multiple file radio button
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        if (((JRadioButton) ae.getSource()).isSelected())
                        {
                            // Set the file/path label text
                            exportLbl.setText("Enter or select an export path");

                            // Remove the file name from the path in the file/path field
                            setFilePath(false, fileExtn);
                        }
                    }
                });

                // Set single file as the initial selection
                multipleFileRBtn.setSelected(true);
            }
        }
        // The export command originated from a table editor dialog menu
        else
        {
            // Create an empty panel for the dialog components
            dialogPnl = new JPanel(new GridBagLayout());

            gbc.insets.bottom = 0;
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            originatedFromTableEditor = true;
        }

        // Check that the panel was created; i.e., that there are tables available for exporting
        if (dialogPnl != null)
        {
            // Add the export storage path components to the dialog
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.gridy++;
            dialogPnl.add(createPathSelectionPanel(fileExtn), gbc);

            // Normally the single file or multi file buttons are used to set the file path, but in
            // this situation they do not exist and the function needs to be manually called
            if (originatedFromTableEditor)
            {
                setFilePath(true, fileExtn);
            }

            // Create a check box for indicating existing files can be replaced
            overwriteFileCb = new JCheckBox("Overwrite existing file"
                                            + (dialogType == ManagerDialogType.EXPORT_CSV
                                               || dialogType == ManagerDialogType.EXPORT_JSON ? "(s)" : ""));
            overwriteFileCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            overwriteFileCb.setBorder(emptyBorder);
            overwriteFileCb.setToolTipText(CcddUtilities.wrapText("Select to overwrite any file with the same name",
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.gridy++;
            dialogPnl.add(overwriteFileCb, gbc);

            // Overwrite files is set to checked by default
            overwriteFileCb.setSelected(true);

            // Create the export entire database check box
            exportEntireDatabaseCb = new JCheckBox("Export full database");
            exportEntireDatabaseCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            exportEntireDatabaseCb.setBorder(emptyBorder);

            // Check if exporting in CSV or JSON format
            if (dialogType == ManagerDialogType.EXPORT_CSV || dialogType == ManagerDialogType.EXPORT_JSON)
            {
                // Add a separator
                JSeparator lowerSep = new JSeparator();
                lowerSep.setForeground(dialogPnl.getBackground().darker());
                gbc.weightx = 1.0;
                gbc.gridy++;
                dialogPnl.add(lowerSep, gbc);

                // Create panels to contain the include labels and check boxes
                JPanel lowerPnl = new JPanel(new GridBagLayout());
                JPanel includePnl = new JPanel(new GridBagLayout());
                JPanel separatorPnl = new JPanel(new GridBagLayout());
                lowerPnl.setBorder(emptyBorder);
                includePnl.setBorder(emptyBorder);
                separatorPnl.setBorder(emptyBorder);
                gbc.weightx = 0.0;
                gbc.insets.top = 0;
                gbc.gridy++;
                dialogPnl.add(lowerPnl, gbc);
                gbc.insets.left = 0;
                gbc.gridy = 0;
                lowerPnl.add(includePnl, gbc);
                gbc.gridx = 0;
                gbc.gridy++;
                lowerPnl.add(separatorPnl, gbc);

                // Add a dummy label so that when the dialog width increases the lower panel
                // remains unchanged in size and position
                gbc.weightx = 1.0;
                gbc.gridx++;
                lowerPnl.add(new JLabel(""), gbc);

                // Add the "Include" label
                JLabel includeLbl = new JLabel("Include:");
                includeLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy = 0;
                includePnl.add(includeLbl, gbc);

                exportEntireDatabaseCb.setToolTipText(CcddUtilities.wrapText("Check this box if exporting the entire database",
                                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.insets.left += ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridy++;
                includePnl.add(exportEntireDatabaseCb, gbc);

                // Add a listener for changes to the export entire database check box selection
                // status
                exportEntireDatabaseCb.addActionListener(new ActionListener()
                {
                    /**************************************************************************************
                     * Handle a change to the Replace Existing Tables check box selection status
                     *************************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        deleteTargetDirectoryCb.setEnabled(exportEntireDatabaseCb.isSelected());

                        includeAllTableTypesCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAllTableTypesCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeAllDataTypesCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAllDataTypesCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeAllInputTypesCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAllInputTypesCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeAllMacrosCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAllMacrosCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeGroupsCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeGroupsCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeAssociationsCb.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAssociationsCb.setSelected(exportEntireDatabaseCb.isSelected());

                        includeTlmSchedCB.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeTlmSchedCB.setSelected(exportEntireDatabaseCb.isSelected());

                        includeAppSchedCB.setEnabled(!exportEntireDatabaseCb.isSelected());
                        includeAppSchedCB.setSelected(exportEntireDatabaseCb.isSelected());
                    }
                });

                // Create the include all table type definitions check box
                includeAllTableTypesCb = new JCheckBox("All table type definitions");
                includeAllTableTypesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAllTableTypesCb.setBorder(emptyBorder);
                includeAllTableTypesCb.setToolTipText(CcddUtilities.wrapText("If checked, all table types definitions "
                                                                             + "are included in each export file",
                                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(includeAllTableTypesCb, gbc);

                // Create the delete target directory contents check box
                deleteTargetDirectoryCb = new JCheckBox("Clean target directory");
                deleteTargetDirectoryCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                deleteTargetDirectoryCb.setBorder(emptyBorder);
                deleteTargetDirectoryCb.setToolTipText(CcddUtilities.wrapText("If checked, all target directory "
                                                                              + "contents will be deleted",
                                                                              ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                deleteTargetDirectoryCb.setEnabled(false);
                gbc.insets.left += ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(deleteTargetDirectoryCb, gbc);

                // Create the include all data type definitions check box
                includeAllDataTypesCb = new JCheckBox("All data type definitions");
                includeAllDataTypesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAllDataTypesCb.setBorder(emptyBorder);
                includeAllDataTypesCb.setToolTipText(CcddUtilities.wrapText("If checked, all data type definitions "
                                                                            + "are included in each export file",
                                                                            ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.gridx++;
                includePnl.add(includeAllDataTypesCb, gbc);

                // Create the include all user-defined input type definitions check box
                includeAllInputTypesCb = new JCheckBox("All user-defined input type definitions");
                includeAllInputTypesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAllInputTypesCb.setBorder(emptyBorder);
                includeAllInputTypesCb.setToolTipText(CcddUtilities.wrapText("If checked, all user-defined input type definitions "
                                                                             + "are included in each export file",
                                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeAllInputTypesCb, gbc);

                // Create the include all macro definitions check box
                includeAllMacrosCb = new JCheckBox("All macro definitions");
                includeAllMacrosCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAllMacrosCb.setBorder(emptyBorder);
                includeAllMacrosCb.setToolTipText(CcddUtilities.wrapText("If checked, all macro definitions "
                                                                         + "are included in each export file",
                                                                         ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(includeAllMacrosCb, gbc);

                // Create the reserved message ID inclusion check box
                includeReservedMsgIDsCb = new JCheckBox("Reserved message IDs");
                includeReservedMsgIDsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeReservedMsgIDsCb.setBorder(emptyBorder);
                includeReservedMsgIDsCb.setToolTipText(CcddUtilities.wrapText("If checked, the contents of the reserved "
                                                                              + "message ID table (IDs or ID ranges, and "
                                                                              + "their corresponding descriptions) is "
                                                                              + "included in each export file",
                                                                              ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeReservedMsgIDsCb, gbc);

                // Create the project-level data field inclusion check box
                includeProjectFieldsCb = new JCheckBox("Project data fields");
                includeProjectFieldsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeProjectFieldsCb.setBorder(emptyBorder);
                includeProjectFieldsCb.setToolTipText(CcddUtilities.wrapText("If checked, the project-level data field "
                                                                             + "definitions are included in each export file",
                                                                             ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(includeProjectFieldsCb, gbc);

                // Create the group definitions and data field inclusion check box
                includeGroupsCb = new JCheckBox("Group definitions and data fields");
                includeGroupsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeGroupsCb.setBorder(emptyBorder);
                includeGroupsCb.setToolTipText(CcddUtilities.wrapText("If checked, the group and group data field "
                                                                      + "definitions are included in each export file",
                                                                      ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeGroupsCb, gbc);

                // Create the script associations inclusion check box
                includeAssociationsCb = new JCheckBox("Script associations");
                includeAssociationsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAssociationsCb.setBorder(emptyBorder);
                includeAssociationsCb.setToolTipText(CcddUtilities.wrapText("If checked, the script associations "
                                                                            + " are included in each export file",
                                                                            ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(includeAssociationsCb, gbc);

                // Create the table definitions inclusion check box
                includeTlmSchedCB = new JCheckBox("Tlm Scheduler");
                includeTlmSchedCB.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeTlmSchedCB.setBorder(emptyBorder);

                // This checkbox is only usable during a JSON export
                if ((fileExtn != FileExtension.JSON) && (fileExtn != FileExtension.CSV))
                {
                    includeTlmSchedCB.setEnabled(false);
                }

                includeTlmSchedCB.setToolTipText(CcddUtilities.wrapText("If checked, the telemetry scheduler "
                                                                        + " table will be exported in a file",
                                                                        ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeTlmSchedCB, gbc);

                // Create the table definitions inclusion check box
                includeAppSchedCB = new JCheckBox("App Scheduler");
                includeAppSchedCB.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeAppSchedCB.setBorder(emptyBorder);

                // This checkbox is only usable during a JSON export
                if ((fileExtn != FileExtension.JSON) && (fileExtn != FileExtension.CSV))
                {
                    includeAppSchedCB.setEnabled(false);
                }

                includeAppSchedCB.setToolTipText(CcddUtilities.wrapText("If checked, the application scheduler "
                                                                        + " table will be exported in a file",
                                                                        ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(includeAppSchedCB, gbc);

                // Create the include build information check box
                includeBuildInfoCb = new JCheckBox("Build information");
                includeBuildInfoCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeBuildInfoCb.setBorder(emptyBorder);
                includeBuildInfoCb.setToolTipText(CcddUtilities.wrapText("If checked, the build information will be "
                                                                         + "added to each file",
                                                                         ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeBuildInfoCb, gbc);

                // Create the macro replacement check box
                replaceMacrosCb = new JCheckBox("Substitute macros");
                replaceMacrosCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                replaceMacrosCb.setBorder(emptyBorder);
                replaceMacrosCb.setToolTipText(CcddUtilities.wrapText("If checked, the macros are replaced with their "
                                                                      + "corresponding values prior to exporting the "
                                                                      + "table(s).  If not checked, the macro names are "
                                                                      + "retained and the macro information is stored "
                                                                      + "with the exported table(s)",
                                                                      ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx++;
                includePnl.add(replaceMacrosCb, gbc);

                // Create the check box for inclusion of variable paths
                includeVariablePaths = new JCheckBox("Variable paths");
                includeVariablePaths.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                includeVariablePaths.setBorder(emptyBorder);
                includeVariablePaths.setToolTipText(CcddUtilities.wrapText("If checked each variable's path in a structure table "
                                                                           + "is included, both in the application format and "
                                                                           + "using the separator characters specified by the user",
                                                                           ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                gbc.gridx = 0;
                gbc.gridy++;
                includePnl.add(includeVariablePaths, gbc);

                // Create the variable path separator label and input field, and add them to the
                // dialog panel
                final JLabel varPathSepLbl = new JLabel("Enter variable path separator character(s)");
                varPathSepLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                varPathSepLbl.setEnabled(false);
                gbc.insets.left += ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
                gbc.gridx = 0;
                gbc.gridy = 0;
                separatorPnl.add(varPathSepLbl, gbc);
                varPathSepFld = new JTextField("_", 5);
                varPathSepFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                varPathSepFld.setEditable(true);
                varPathSepFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                varPathSepFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                varPathSepFld.setBorder(border);
                varPathSepFld.setEnabled(false);
                gbc.gridx++;
                separatorPnl.add(varPathSepFld, gbc);

                // Create the data type/variable name separator label and input field, and add them
                // to the dialog panel
                final JLabel typeNameSepLbl = new JLabel("Enter data type/variable name separator character(s)");
                typeNameSepLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                typeNameSepLbl.setEnabled(false);
                gbc.gridx = 0;
                gbc.gridy++;
                separatorPnl.add(typeNameSepLbl, gbc);
                typeNameSepFld = new JTextField("_", 5);
                typeNameSepFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                typeNameSepFld.setEditable(true);
                typeNameSepFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                typeNameSepFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                typeNameSepFld.setBorder(border);
                typeNameSepFld.setEnabled(false);
                gbc.gridx++;
                separatorPnl.add(typeNameSepFld, gbc);

                // Create a check box for hiding data types
                hideDataTypeCb = new JCheckBox("Hide data types");
                hideDataTypeCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                hideDataTypeCb.setBorder(BorderFactory.createEmptyBorder());
                hideDataTypeCb.setEnabled(false);
                gbc.gridx = 0;
                gbc.gridy++;
                separatorPnl.add(hideDataTypeCb, gbc);

                // Add a listener for the hide data type check box
                hideDataTypeCb.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Handle a change in the hide data type check box status
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Enable/disable the data type/variable name separator input label and
                        // field
                        typeNameSepLbl.setEnabled(!((JCheckBox) ae.getSource()).isSelected());
                        typeNameSepFld.setEnabled(!((JCheckBox) ae.getSource()).isSelected());
                    }
                });

                // Add a listener for the variable paths check box selection changes
                includeVariablePaths.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to changes in selection of the include variable paths check box
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Enable/disable the separator inputs based on the inclusion check box
                        // state
                        varPathSepLbl.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                        varPathSepFld.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                        typeNameSepLbl.setEnabled(((JCheckBox) ae.getSource()).isSelected() && !hideDataTypeCb.isSelected());
                        typeNameSepFld.setEnabled(((JCheckBox) ae.getSource()).isSelected() && !hideDataTypeCb.isSelected());
                        hideDataTypeCb.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                    }
                });

                // All of these options only apply when exporting multiple tables. If an export
                // originated from a table type editor than only one table is being exported and
                // these options are disabled
                if (originatedFromTableEditor)
                {
                    exportEntireDatabaseCb.setEnabled(false);
                    deleteTargetDirectoryCb.setEnabled(false);
                    includeAllTableTypesCb.setEnabled(false);
                    includeAllDataTypesCb.setEnabled(false);
                    includeAllInputTypesCb.setEnabled(false);
                    includeAllMacrosCb.setEnabled(false);
                    includeReservedMsgIDsCb.setEnabled(false);
                    includeProjectFieldsCb.setEnabled(false);
                    includeGroupsCb.setEnabled(false);
                    includeAssociationsCb.setEnabled(false);
                    includeTlmSchedCB.setEnabled(false);
                    includeAppSchedCB.setEnabled(false);
                }
            }

            // Check if exporting in EDS or XTCE XML format
            if (dialogType == ManagerDialogType.EXPORT_EDS || dialogType == ManagerDialogType.EXPORT_XTCE)
            {
                // Create a panel to contain the endianess radio buttons
                JPanel endianessPnl = new JPanel(new GridBagLayout());
                endianessPnl.setBorder(emptyBorder);

                // Create the label for the endianess radio button panel
                JLabel endianessLbl = new JLabel("Endianess");
                endianessLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                gbc.insets.left = 0;
                endianessPnl.add(endianessLbl, gbc);

                // Set up storage for the endianess radio buttons
                ButtonGroup endianessRBtnGroup = new ButtonGroup();
                bigRBtn = new JRadioButton("Big", true);
                bigRBtn.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                bigRBtn.setBorder(emptyBorder);
                endianessRBtnGroup.add(bigRBtn);
                gbc.gridx = 0;
                gbc.gridy++;
                endianessPnl.add(bigRBtn, gbc);
                JRadioButton littleRBtn = new JRadioButton("Little", false);
                littleRBtn.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                littleRBtn.setBorder(emptyBorder);
                endianessRBtnGroup.add(littleRBtn);
                gbc.gridy++;
                endianessPnl.add(littleRBtn, gbc);

                // Add a listener for big endian radio button selection
                bigRBtn.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to a change in selection of the big endian radio button
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Disable the big endian header check box (since all of the output is big
                        // endian)
                        headerBigCBox.setEnabled(false);
                    }
                });

                // Add a listener for little endian radio button selection
                littleRBtn.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to a change in selection of the little endian radio button
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Enable the big endian header check box
                        headerBigCBox.setEnabled(true);
                    }
                });

                // Create the header endianess check box
                headerBigCBox = new JCheckBox("Headers are big endian");
                headerBigCBox.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                headerBigCBox.setBorder(emptyBorder);
                headerBigCBox.setSelected(true);
                headerBigCBox.setEnabled(false);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3;
                gbc.gridx++;
                endianessPnl.add(headerBigCBox, gbc);

                // Add the endianess selection components to the dialog
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.fill = GridBagConstraints.NONE;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(endianessPnl, gbc);
            }

            // Check if exporting in XTCE XML format
            if (dialogType == ManagerDialogType.EXPORT_XTCE)
            {
                // Create a panel to contain the script file components
                JPanel scriptPnl = new JPanel(new GridBagLayout());

                // Create the use external (script) methods check box
                useExternalCBox = new JCheckBox("Use external methods");
                useExternalCBox.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                useExternalCBox.setBorder(emptyBorder);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
                scriptPnl.add(useExternalCBox, gbc);

                final JLabel scriptNameLbl = new JLabel("Enter script file name");
                scriptNameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                scriptNameLbl.setEnabled(false);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 3;
                gbc.gridx++;
                scriptPnl.add(scriptNameLbl, gbc);
                scriptNameFld = new JTextField(ccddMain.getProgPrefs().get(ModifiablePathInfo.XTCE_EXPORT_SCRIPT.getPreferenceKey(), ""), 10);
                scriptNameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                scriptNameFld.setEditable(true);
                scriptNameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                scriptNameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                scriptNameFld.setBorder(border);
                scriptNameFld.setEnabled(false);
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weightx = 1.0;
                gbc.gridx++;
                scriptPnl.add(scriptNameFld, gbc);

                // Create a button for choosing an output script
                final JButton btnSelectScript = CcddButtonPanelHandler.createButton("Select...",
                                                                                    SCRIPTS_ICON,
                                                                                    KeyEvent.VK_S,
                                                                                    "Open the script selection dialog");
                btnSelectScript.setEnabled(false);

                // Add a listener for the Select script button
                btnSelectScript.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Select a script
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Allow the user to select the script file path + name
                        FileEnvVar[] scriptFile = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                                         CcddTableManagerDialog.this,
                                                                                         null,
                                                                                         "script",
                                                                                         ccddMain.getScriptHandler().getExtensions(),
                                                                                         false,
                                                                                         "Select Script",
                                                                                         ccddMain.getProgPrefs().get(ModifiablePathInfo.SCRIPT_PATH.getPreferenceKey(),
                                                                                                                     null),
                                                                                         DialogOption.OK_CANCEL_OPTION);

                        // Check if a script file is selected
                        if (scriptFile != null && scriptFile[0] != null)
                        {
                            // Display the file name in the script name field
                            scriptNameFld.setText(scriptFile[0].getAbsolutePathWithEnvVars());

                            // Store the XTCE export script file name
                            ccddMain.getProgPrefs().put(ModifiablePathInfo.XTCE_EXPORT_SCRIPT.getPreferenceKey(),
                                                        scriptNameFld.getText());
                        }
                    }
                });

                // Add the select script button to the dialog
                gbc.weightx = 0.0;
                gbc.gridx++;
                scriptPnl.add(btnSelectScript, gbc);

                // Add a listener for the variable paths check box selection changes
                useExternalCBox.addActionListener(new ActionListener()
                {
                    /******************************************************************************
                     * Respond to changes in selection of the include variable paths check box
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Enable/disable the separator inputs based on the inclusion check box
                        // state
                        scriptNameLbl.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                        scriptNameFld.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                        btnSelectScript.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                    }
                });

                gbc.gridx = 0;
                gbc.gridy++;
                dialogPnl.add(scriptPnl, gbc);

                // Create the panels to hold the XTCE components of the dialog
                JPanel infoPnl = new JPanel(new GridBagLayout());
                JPanel classPnl = new JPanel(new GridBagLayout());

                // Create the attributes label
                JLabel descriptionLbl = new JLabel("XTCE Attributes");
                descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                descriptionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
                gbc.gridy++;
                dialogPnl.add(descriptionLbl, gbc);

                // Create the version label
                JLabel versionLbl = new JLabel("Version");
                versionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                versionLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
                gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.weightx = 0.0;
                infoPnl.add(versionLbl, gbc);

                // Create the version input field
                versionFld = new JTextField("1.0");
                versionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                versionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                versionFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                versionFld.setBorder(border);
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weightx = 1.0;
                gbc.gridx++;
                infoPnl.add(versionFld, gbc);

                // Create the validation status label
                JLabel validStatLbl = new JLabel("Validation Status");
                validStatLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                validStatLbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                infoPnl.add(validStatLbl, gbc);

                // Create the validation status input field
                validStatFld = new JTextField("Working");
                validStatFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                validStatFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                validStatFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                validStatFld.setBorder(border);
                gbc.gridx++;
                infoPnl.add(validStatFld, gbc);

                // Create the classification label
                JLabel classLbl = new JLabel("Classification");
                classLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                classLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                gbc.insets.bottom = 0;
                gbc.gridx = 0;
                gbc.gridy++;
                infoPnl.add(classLbl, gbc);

                // Create the first level classification label
                JLabel class1Lbl = new JLabel("First level");
                class1Lbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                class1Lbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());

                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class1Lbl, gbc);

                // Create the first classification input fields
                class1Fld = new JTextField("DOMAIN");
                class1Fld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                class1Fld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                class1Fld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                class1Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class1Fld, gbc);

                // Create the second level classification label
                JLabel class2Lbl = new JLabel("Second level");
                class2Lbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                class2Lbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class2Lbl, gbc);

                // Create the second level classification input fields
                class2Fld = new JTextField("SYSTEM");
                class2Fld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                class2Fld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                class2Fld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                class2Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class2Fld, gbc);

                // Create the third level classification label
                JLabel class3Lbl = new JLabel("Third level");
                class3Lbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                class3Lbl.setForeground(ModifiableColorInfo.LABEL_TEXT.getColor());
                gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class3Lbl, gbc);

                // Create the third level classification input fields
                class3Fld = new JTextField("INTERFACE");
                class3Fld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
                class3Fld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
                class3Fld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
                class3Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class3Fld, gbc);

                // Add the classifications panel to the information panel
                classPnl.setBorder(BorderFactory.createEtchedBorder());
                gbc.gridwidth = 2;
                gbc.gridx = 0;
                gbc.gridy++;
                infoPnl.add(classPnl, gbc);

                // Add the information panel to the dialog panel
                infoPnl.setBorder(BorderFactory.createEtchedBorder());
                gbc.insets.bottom = 0;
                gbc.gridwidth = 1;
                gbc.gridy++;
                dialogPnl.add(infoPnl, gbc);
            }
        }

        return dialogPnl;
    }

    /**********************************************************************************************
     * Add a table name field to the dialog
     *
     * @param fieldText Text to display beside the input field
     *
     * @param dialogPnl Panel to which to add the input field
     *
     * @param enabled   True if the fields are initially enabled, false if disabled
     *
     * @param dialogGbc Dialog panel GridBagLayout layout constraints
     *********************************************************************************************/
    private void addTableInputFields(String fieldText, JPanel dialogPnl, boolean enabled, GridBagConstraints dialogGbc)
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        GridBagConstraints.REMAINDER,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.HORIZONTAL,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create the table name label and field
        JLabel nameLbl = new JLabel(fieldText);
        nameLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        nameFld = new JTextField("", 20);
        nameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        nameFld.setBackground(enabled ? ModifiableColorInfo.INPUT_BACK.getColor()
                                      : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        nameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        nameFld.setEditable(true);
        nameFld.setBorder(border);

        // Create the description label and field
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setBackground(enabled ? ModifiableColorInfo.INPUT_BACK.getColor()
                                             : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(enabled);
        descriptionFld.setLineWrap(true);
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(enabled ? ModifiableColorInfo.INPUT_BACK.getColor()
                                             : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descScrollPane.setBorder(border);

        // Add the name and description labels and fields to a panel
        JPanel nameDescPnl = new JPanel(new GridBagLayout());
        nameDescPnl.setBorder(emptyBorder);
        nameDescPnl.add(nameLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
        nameDescPnl.add(nameFld, gbc);
        gbc.gridy++;
        gbc.insets.left = 0;
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        nameDescPnl.add(descriptionLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        nameDescPnl.add(descScrollPane, gbc);

        // Add the panel to the dialog panel
        dialogGbc.weighty = 0.0;
        dialogGbc.gridy++;
        dialogGbc.gridwidth = GridBagConstraints.REMAINDER;
        dialogGbc.fill = GridBagConstraints.HORIZONTAL;
        dialogGbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        dialogGbc.insets.bottom = 0;
        dialogPnl.add(nameDescPnl, dialogGbc);
    }

    /**********************************************************************************************
     * Set the path/file selection field contents
     *
     * @param isAddFileName True to add the file name to the path (if not already present); false
     *                      to remove the file name (if present). The presence of a file name is
     *                      determined by checking if the field contents ends with a file extension
     *
     * @param fileExtn      File extension type
     *********************************************************************************************/
    private void setFilePath(boolean isAddFileName, FileExtension fileExtn)
    {
        // Get the contents of the file/path field
        String path = pathFld.getText().trim();

        // Set the flag if the field contains a file name. A name is detected if a period (the file
        // extension separator character) follows the last file separator character. Note that a
        // folder with a period in the name can fool this
        boolean hasFileName = path.lastIndexOf(File.separator) <= path.lastIndexOf(".");

        // Check if the file name should be added and it's not already present
        if (isAddFileName && !hasFileName)
        {
            // Add the file name to the path already present in the field. The file name defaults
            // to the database name when the export command is issued from the main window. If
            // issued from a table editor then the editor's table name is used to create the file
            // name
            path += File.separator
                    + (callingEditorDlg == null ? dbControl.getDatabaseName()
                                                : callingEditorDlg.getTableEditor()
                                                                  .getTableInformation()
                                                                  .getTablePath()
                                                                  .replaceAll("[^a-zA-Z0-9_]", "_"))
                    + fileExtn.getExtension();
        }
        // Check if the file name should be removed and it's present
        else if (!isAddFileName && hasFileName)
        {
            // Get the index of the last file separator character in the field
            int index = path.lastIndexOf(File.separator);

            // Check if no separator exists (only the file name is present)
            if (index == -1)
            {
                path = "";
            }
            // The separator exists
            else
            {
                // Remove the file name form the path
                path = path.substring(0, index);
            }
        }

        // Store the (updated) path (and file, if applicable) in the field
        pathFld.setText(path);
    }

    /**********************************************************************************************
     * Create the path/file selection panel
     *
     * @param fileExtn File extension type
     *
     * @return JPanel containing the path/file selection panel
     *********************************************************************************************/
    private JPanel createPathSelectionPanel(final FileExtension fileExtn)
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
                                                        new Insets(0,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel for the path selection components
        JPanel pathPnl = new JPanel(new GridBagLayout());
        pathPnl.setBorder(emptyBorder);

        // Create the file/path selection dialog labels and fields
        exportLbl = new JLabel("Enter or select an export path");
        exportLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        pathPnl.add(exportLbl, gbc);

        // Create a text field for entering & displaying the path/file. The initial file name is
        // based on the database name
        pathFld = new JTextField(ModifiablePathInfo.TABLE_EXPORT_PATH.getPath(), 20);
        pathFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        pathFld.setEditable(true);
        pathFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        pathFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        pathFld.setBorder(border);

        // EDS and XTCE can only export to a single file so add the file path by default. JSON and
        // CSV export to multiple files by default. Do not add the file path
        if (fileExtn == FileExtension.EDS || fileExtn == FileExtension.XTCE)
        {
            setFilePath(true, fileExtn);
        }
        else
        {
            setFilePath(false, fileExtn);
        }

        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.gridy++;
        pathPnl.add(pathFld, gbc);

        // Create a button for choosing an export path/file
        JButton btnSelectPath = CcddButtonPanelHandler.createButton("Select...",
                                                                    EXPORT_ICON,
                                                                    KeyEvent.VK_S,
                                                                    "Open the export path/file selection dialog");

        // Add a listener for the Select path/file button
        btnSelectPath.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select a export storage path/file
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                FileEnvVar[] filePath;

                // Check if tables should be exported to a single file
                if (singleFileRBtn == null || singleFileRBtn.isSelected())
                {
                    // Allow the user to select the export storage path+file
                    filePath = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                      CcddTableManagerDialog.this,
                                                                      pathFld.getText().trim(),
                                                                      null,
                                                                      new FileNameExtensionFilter[] {new FileNameExtensionFilter(fileExtn.getDescription(),
                                                                                                                                 fileExtn.getExtensionName())},
                                                                      false,
                                                                      "Select File for Exported Table(s)",
                                                                      ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(), null),
                                                                      DialogOption.OK_CANCEL_OPTION);
                }
                // Export tables to individual files
                else
                {
                    // Allow the user to select the export storage path
                    filePath = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                      CcddTableManagerDialog.this,
                                                                      "Select Location for Exported Table(s)",
                                                                      ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(),
                                                                                                  null),
                                                                      DialogOption.OK_CANCEL_OPTION);
                }

                // Check if an export path/file is selected
                if (filePath != null && filePath[0] != null)
                {
                    // Display the path name in the export path/file field
                    pathFld.setText(filePath[0].getAbsolutePathWithEnvVars());
                }
            }
        });

        // Add the select export path/file button to the dialog
        gbc.weightx = 0.0;
        gbc.insets.right = 0;
        gbc.gridx++;
        pathPnl.add(btnSelectPath, gbc);

        return pathPnl;
    }

    /**********************************************************************************************
     * Verify that the supplied table name is valid
     *
     * @param tableName Table name
     *
     * @throws CCDDException If the table name is invalid
     *********************************************************************************************/
    private void verifyTableName(String tableName) throws CCDDException
    {
        // Check if the database name is blank
        if (tableName.isEmpty())
        {
            // Inform the user that the name is invalid
            throw new CCDDException("Table name must be entered");
        }

        // Check if the name is too long
        if (tableName.length() >= MAX_SQL_NAME_LENGTH)
        {
            // Inform the user that the name is too long
            throw new CCDDException("Table name '</b>"
                                    + tableName
                                    + "<b>' too long ("
                                    + (MAX_SQL_NAME_LENGTH - 1)
                                    + " characters maximum)");
        }

        // Check if the name contains an illegal character
        if (!tableName.matches(DefaultInputType.ALPHANUMERIC.getInputMatch()))
        {
            // Inform the user that the name is invalid
            throw new CCDDException("Illegal character(s) in table name '</b>" + tableName + "<b>'");
        }

        // Check if the name matches that of a primitive data type
        if (dataTypeHandler.isPrimitive(tableName))
        {
            // Inform the user that the name matches a primitive type
            throw new CCDDException("Table name '</b>" + tableName + "<b>' matches a primitive data type");
        }

        // Check if the table starts with the characters designating an internal table
        if (tableName.startsWith(INTERNAL_TABLE_PREFIX))
        {
            // Inform the user that the table name can't begin with the internal table prefix
            throw new CCDDException("Table name '</b>"
                                    + tableName + "<b>' cannot begin with '</b>"
                                    + INTERNAL_TABLE_PREFIX
                                    + "<b>'");
        }

        // Get the list of existing data tables
        String[] namesInUse = dbTable.queryTableList(CcddTableManagerDialog.this);

        // Step through each of the table names
        for (String nameInUse : namesInUse)
        {
            // Check if the user-supplied name matches an existing table name. If renaming, the new
            // name may differ by capitalization or characters; otherwise (if creating or copying)
            // the names must differ with the text forced to lower case
            if ((dialogType == ManagerDialogType.RENAME && !selectedTableName.equals(nameInUse)
                 && nameInUse.equals(tableName))
                || (dialogType != ManagerDialogType.RENAME && nameInUse.equalsIgnoreCase(tableName)))
            {
                // Inform the user that the name is already in use
                throw new CCDDException("Table name '</b>" + tableName + "<b>' is already in use");
            }
        }
    }

    /**********************************************************************************************
     * Verify that the dialog content is valid
     *
     * @return True if the input values are valid
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            boolean importing = false;
            // Verify the dialog content based on the supplied dialog type
            switch (dialogType)
            {
                case NEW:
                    // Remove any excess white space
                    String names = nameFld.getText().trim();
                    descriptionFld.setText(descriptionFld.getText().trim());

                    // Store the cleaned table name
                    nameFld.setText(names);

                    // Check if a table type is selected
                    if (getRadioButtonSelected() == null)
                    {
                        // Inform the user that a table type must be selected
                        throw new CCDDException("Table type must be selected");
                    }

                    // Create a list to hold the new names already checked
                    List<String> newNames = new ArrayList<String>();

                    // Break the string of names into separate strings
                    tableNames = names.split(",");

                    // Step through each table name
                    for (int index = 0; index < tableNames.length; index++)
                    {
                        // Remove leading and trailing white space characters
                        String tableName = tableNames[index].trim();

                        // Check if the table name appears more than once in the list of new names.
                        // Force to lower case to remove case sensitivity
                        if (newNames.contains(tableName.toLowerCase()))
                        {
                            // Inform the user that a table name is a duplicate
                            throw new CCDDException("Table name '</b>" + tableName + "<b>' is a duplicate");
                        }

                        // Check if the name is valid
                        verifyTableName(tableName);

                        // Add the new table name to the comparison list
                        newNames.add(tableName.toLowerCase());

                        // Store the cleaned table name into the array
                        tableNames[index] = tableName;
                    }

                    break;

                case RENAME:
                case COPY:
                    // Remove any excess white space
                    String tableName = nameFld.getText().trim();
                    descriptionFld.setText(descriptionFld.getText().trim());

                    // Store the cleaned table name
                    nameFld.setText(tableName);

                    // Check if the name is valid
                    verifyTableName(tableName);
                    break;

                case EDIT:
                case DELETE:
                    // Check if no table has been selected
                    if (tableTree.getSelectedTablesWithChildren().size() == 0)
                    {
                        // Inform the user that no item has been selected
                        throw new CCDDException("Must select a table from the tree");
                    }

                    break;

                case IMPORT_JSON:
                    importing = true;
                    break;

                case IMPORT_CSV:
                    importing = true;
                    break;

                case IMPORT_EDS:
                    importing = true;
                    break;

                case IMPORT_XTCE:
                    importing = true;
                    break;

                case EXPORT_CSV:
                case EXPORT_JSON:
                    // Check if no tables are selected and none of the "include" check boxes are
                    // selected (this check doesn't apply if the export occurs from a table
                    // editor). At least one of these must be selected in order to perform an
                    // export
                    if (callingEditorDlg == null && tableTree.getSelectedTablesWithChildren().size() == 0
                        && !(deleteTargetDirectoryCb != null ? deleteTargetDirectoryCb.isSelected() : false)
                        && !(includeAllTableTypesCb != null ? includeAllTableTypesCb.isSelected() : false)
                        && !(includeAllDataTypesCb != null ? includeAllDataTypesCb.isSelected() : false)
                        && !(includeAllInputTypesCb != null ? includeAllInputTypesCb.isSelected() : false)
                        && !(includeAllMacrosCb != null ? includeAllMacrosCb.isSelected() : false)
                        && !(includeReservedMsgIDsCb != null ? includeReservedMsgIDsCb.isSelected() : false)
                        && !(includeProjectFieldsCb != null ? includeProjectFieldsCb.isSelected() : false)
                        && !(includeGroupsCb != null ? includeGroupsCb.isSelected() : false)
                        && !(includeAssociationsCb != null ? includeAssociationsCb.isSelected() : false)
                        && !(includeTlmSchedCB != null ? includeTlmSchedCB.isSelected() : false)
                        && !(includeAppSchedCB != null ? includeAppSchedCB.isSelected() : false)
                        && !(includeVariablePaths != null ? includeVariablePaths.isSelected() : false))
                    {
                        // Inform the user that no table or include option has been selected
                        throw new CCDDException("Must select a table or at least one include option");
                    }

                case EXPORT_EDS:
                case EXPORT_XTCE:
                    // Remove any leading or trailing white space characters from the file
                    // path/name
                    pathFld.setText(pathFld.getText().trim());

                    // Check if no tables are selected and this is an EDS or XTCE export
                    if ((dialogType == ManagerDialogType.EXPORT_EDS || dialogType == ManagerDialogType.EXPORT_XTCE)
                        && callingEditorDlg == null
                        && tableTree.getSelectedTablesWithChildren().size() == 0)
                    {
                        // Inform the user that no table has been selected
                        throw new CCDDException("Must select a table from the tree");
                    }

                    // Check if the table(s) are to be stored in a single file (if the button is
                    // applicable to the export type)
                    if (singleFileRBtn == null || singleFileRBtn.isSelected())
                    {
                        // Check if the name field is empty or contains no file name in the path
                        if (pathFld.getText().isEmpty()
                            || pathFld.getText().matches(".*\\" + File.separator + "\\.*?$"))
                        {
                            // Inform the user that no file name has been selected
                            throw new CCDDException("Must select an export file name");
                        }

                        // Create a file reference from the file path/name
                        FileEnvVar file = new FileEnvVar(pathFld.getText());

                        // Check if the selection is a directory instead of a file name
                        if (file.isDirectory())
                        {
                            // Inform the user that a directory (instead of a file name) has been
                            // selected
                            throw new CCDDException("Directory name cannot be selected as the file name");
                        }
                        // Check if the file already exists; if so, the name is valid
                        else if (!file.exists())
                        {
                            try
                            {
                                // Attempt to create the file; an exception is thrown if
                                // unsuccessful
                                file.createNewFile();

                                // The file was successfully created; delete it
                                file.delete();
                            }
                            catch (Exception e)
                            {
                                // Inform the user that no file name has been selected or that the
                                // file can't be created
                                throw new CCDDException("Invalid export file name");
                            }
                        }
                    }

                    // Check if exporting in XTCE XML format
                    if (dialogType == ManagerDialogType.EXPORT_XTCE)
                    {
                        // Remove any excess white space
                        versionFld.setText(versionFld.getText().trim());
                        validStatFld.setText(validStatFld.getText().trim());
                        class1Fld.setText(class1Fld.getText().trim());
                        class2Fld.setText(class2Fld.getText().trim());
                        class3Fld.setText(class3Fld.getText().trim());

                        // Check if any of the fields is blank
                        if (versionFld.getText().isEmpty()
                            || validStatFld.getText().isEmpty()
                            || class1Fld.getText().isEmpty()
                            || class2Fld.getText().isEmpty()
                            || class3Fld.getText().isEmpty())
                        {
                            // Inform the user that a required field is missing
                            throw new CCDDException("System data field name, version, "
                                                    + "validation status, and/or "
                                                    + "classification missing");
                        }

                        // Check if external (script) methods are to be used
                        if (useExternalCBox.isSelected())
                        {
                            // Remove any excess white space
                            scriptNameFld.setText(scriptNameFld.getText().trim());

                            // Check if the script file name field is blank
                            if (scriptNameFld.getText().isEmpty())
                            {
                                // Inform the user that the script file name is missing
                                throw new CCDDException("Script file name missing");
                            }
                        }
                    }
                    break;

                case IMPORT_C_HEADER:
                    break;
            }

            if (importing == true)
            {
                // Remove any leading or trailing white space characters from the file path/name
                getFileNameField().setText(getFileNameField().getText().trim());

                // Check if the name field is empty or contains no file name in the path
                if (getFileNameField().getText().isEmpty()
                    || getFileNameField().getText().matches(".*\\" + File.separator + "\\.*?$"))
                {
                    // Inform the user that the import file is missing
                    new CcddDialogHandler().showMessageDialog(CcddTableManagerDialog.this,
                                                              "<html><b>Must select an import file name",
                                                              "Missing/Invalid Input",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);

                    // Set the flag to indicate the dialog input is invalid
                    isValid = false;
                }
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddTableManagerDialog.this,
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
}
