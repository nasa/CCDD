/**
 * CFS Command & Data Dictionary table manager dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.EXPORT_ICON;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.OK_BUTTON;

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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableTreeType;

/**************************************************************************************************
 * CFS Command & Data Dictionary table manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableEditorDialog callingEditorDialog;
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
    private JCheckBox appendExistingFieldsCb;
    private JCheckBox useExistingFieldsCb;
    private JCheckBox singleFileCb;
    private JCheckBox backupFirstCb;
    private JCheckBox replaceMacrosCb;
    private JCheckBox includeReservedMsgIDsCb;
    private JCheckBox includeVariablePaths;
    private JTextField varPathSepFld;
    private JTextField typeNameSepFld;
    private JCheckBox hideDataTypeCb;
    private JLabel exportLbl;
    private JTextField versionFld;
    private JTextField validStatFld;
    private JTextField class1Fld;
    private JTextField class2Fld;
    private JTextField class3Fld;
    private JTextField systemFld;
    private JTextField pathFld;

    // Group selection change in progress flag
    private boolean isNodeSelectionChanging;

    // Array of new table names to create
    private String[] tableNames;

    // Create a list to store the names of tables that are no longer valid
    private List<String[]> invalidatedEditors;

    /**********************************************************************************************
     * Table manager dialog class constructor (for all tables)
     *
     * @param ccddMain
     *            main class
     *
     * @param type
     *            table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     *********************************************************************************************/
    protected CcddTableManagerDialog(CcddMain ccddMain, ManagerDialogType type)
    {
        this(ccddMain, type, null);
    }

    /**********************************************************************************************
     * Table manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param dialogType
     *            table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     *
     * @param callingEditorDialog
     *            reference to the table editor dialog that instantiated this table manager. Only
     *            used when called to open a table in an existing editor; null otherwise
     *********************************************************************************************/
    CcddTableManagerDialog(CcddMain ccddMain,
                           ManagerDialogType dialogType,
                           CcddTableEditorDialog callingEditorDialog)
    {
        this.ccddMain = ccddMain;
        this.dialogType = dialogType;
        this.callingEditorDialog = callingEditorDialog;

        // Get a reference to the calling component
        caller = (callingEditorDialog == null)
                                               ? ccddMain.getMainFrame()
                                               : callingEditorDialog;

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
                        if (addRadioButtons(null,
                                            false,
                                            tableTypeHandler.getTypeInformation(),
                                            null,
                                            "Select table type",
                                            dialogPnl,
                                            gbc))
                        {
                            // Create the table creation dialog label and field, and add them to
                            // the dialog panel
                            addTableInputFields("Table name(s)", dialogPnl, true, gbc);
                            nameFld.setToolTipText(CcddUtilities.wrapText("Delimit multiple names using commas",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
                        }
                        // There are no table type available from which to choose
                        else
                        {
                            // Inform the user that no table type exists in the database
                            new CcddDialogHandler().showMessageDialog(caller,
                                                                      "<html><b>Project '"
                                                                              + dbControl.getDatabaseName()
                                                                              + "' has no table type defined",
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
                                    if (me.getClickCount() == 2
                                        && SwingUtilities.isRightMouseButton(me))
                                    {
                                        // Get the selected row in the tree
                                        int rowIndex = tableTree.getClosestRowForLocation(me.getX(),
                                                                                          me.getY());

                                        // Check if a valid row was selected
                                        if (rowIndex != -1)
                                        {
                                            // Get the path for the selected row
                                            TreePath path = tableTree.getPathForRow(rowIndex);

                                            // Check if a table was selected
                                            if (path.getPathCount() > tableTree.getHeaderNodeLevel())
                                            {
                                                // Load the selected table's data into a table
                                                // editor and close this dialog
                                                dbTable.loadTableDataInBackground(tableTree.getFullVariablePath(path.getPath()),
                                                                                  callingEditorDialog);
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

                    case IMPORT:
                        break;

                    case EXPORT_CSV:
                    case EXPORT_XTCE:
                    case EXPORT_EDS:
                    case EXPORT_JSON:
                        // Set the file extension based on the dialog type
                        if (dialogType == ManagerDialogType.EXPORT_CSV)
                        {
                            fileExtn = FileExtension.CSV;
                        }
                        else if (dialogType == ManagerDialogType.EXPORT_EDS)
                        {
                            fileExtn = FileExtension.EDS;
                        }
                        else if (dialogType == ManagerDialogType.EXPORT_XTCE)
                        {
                            fileExtn = FileExtension.XTCE;
                        }
                        else if (dialogType == ManagerDialogType.EXPORT_JSON)
                        {
                            fileExtn = FileExtension.JSON;
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
                                // Create store for the list of table paths and the root table
                                // names
                                List<String> tablePaths = new ArrayList<String>();

                                // Step through each selected table in the tree
                                for (TreePath path : tableTree.getSelectionPaths())
                                {
                                    // Add the table path to the list
                                    tablePaths.add(tableTree.getFullVariablePath(path.getPath()));
                                }

                                // Load the selected table's data into a table editor
                                dbTable.loadTableDataInBackground(tablePaths.toArray(new String[0]),
                                                                  callingEditorDialog);
                            }

                            break;

                        case RENAME:
                            // Display the table renaming dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Rename Table",
                                                  DialogOption.RENAME_OPTION,
                                                  true) == OK_BUTTON)
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
                                for (TreePath path : tableTree.getSelectionPaths())
                                {
                                    // Add the table name to the list
                                    tableNames.add(path.getLastPathComponent().toString());

                                    // Add the pattern for the data type path of tables matching
                                    // the deleted prototype table
                                    invalidatedEditors.add(new String[] {path.getLastPathComponent().toString(),
                                                                         null});
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

                        case IMPORT:
                            // Allow the user to select the data file path + name(s) from which to
                            // import, and the import options
                            File[] filePath = choosePathFile(ccddMain,
                                                             caller,
                                                             null,
                                                             "export",
                                                             new FileNameExtensionFilter[] {new FileNameExtensionFilter(FileExtension.CSV.getDescription(),
                                                                                                                        FileExtension.CSV.getExtensionName()),
                                                                                            new FileNameExtensionFilter(FileExtension.EDS.getDescription(),
                                                                                                                        FileExtension.EDS.getExtensionName()),
                                                                                            new FileNameExtensionFilter(FileExtension.JSON.getDescription(),
                                                                                                                        FileExtension.JSON.getExtensionName()),
                                                                                            new FileNameExtensionFilter(FileExtension.XTCE.getDescription(),
                                                                                                                        FileExtension.XTCE.getExtensionName())},
                                                             false,
                                                             true,
                                                             "Import Table(s)",
                                                             ccddMain.getProgPrefs().get(ModifiablePathInfo.TABLE_EXPORT_PATH.getPreferenceKey(), null),
                                                             DialogOption.IMPORT_OPTION,
                                                             createImportPanel(gbc));

                            // Check if a file was chosen
                            if (filePath != null)
                            {
                                // Export the contents of the selected table(s) in the specified
                                // format
                                fileIOHandler.importFile(filePath,
                                                         backupFirstCb.isSelected(),
                                                         replaceExistingTablesCb.isSelected(),
                                                         appendExistingFieldsCb.isSelected(),
                                                         useExistingFieldsCb.isSelected(),
                                                         CcddTableManagerDialog.this);
                            }

                            break;

                        case EXPORT_CSV:
                        case EXPORT_XTCE:
                        case EXPORT_EDS:
                        case EXPORT_JSON:
                            // Check if the export panel exists; if so display the dialog
                            if (showOptionsDialog(caller,
                                                  dialogPnl,
                                                  "Export Table(s) in "
                                                             + fileExtn.getExtensionName().toUpperCase()
                                                             + " Format",
                                                  DialogOption.EXPORT_OPTION,
                                                  true) == OK_BUTTON)
                            {
                                CcddVariableConversionHandler variableHandler = null;

                                // Create storage for the list of table paths
                                List<String> tablePaths = new ArrayList<String>();

                                // Check if the export command originated from the main menu
                                if (callingEditorDialog == null)
                                {
                                    // Step through each selected table in the tree
                                    for (TreePath path : tableTree.getSelectionPaths())
                                    {
                                        // Get the full path for the table
                                        String fullPath = tableTree.getFullVariablePath(path.getPath());

                                        // Check if the table isn't already in the list
                                        if (!tablePaths.contains(fullPath))
                                        {
                                            // Add the table variable path to the list
                                            tablePaths.add(fullPath);
                                        }
                                    }
                                }
                                // The export command originated from a table editor dialog menu
                                else
                                {
                                    // Add the table editor's table variable path to the list
                                    tablePaths.add(callingEditorDialog.getTableEditor().getTableInformation().getTablePath());
                                }

                                // Check if variable paths are to be included
                                if (includeVariablePaths.isSelected())
                                {
                                    // Create the variable handler
                                    variableHandler = new CcddVariableConversionHandler(ccddMain);
                                }

                                // Export the contents of the selected table(s) in the specified
                                // format
                                fileIOHandler.exportSelectedTables(pathFld.getText(),
                                                                   tablePaths.toArray(new String[0]),
                                                                   overwriteFileCb.isSelected(),
                                                                   singleFileCb.isSelected(),
                                                                   replaceMacrosCb.isSelected(),
                                                                   includeReservedMsgIDsCb.isSelected(),
                                                                   includeVariablePaths.isSelected(),
                                                                   variableHandler,
                                                                   new String[] {varPathSepFld.getText(),
                                                                                 Boolean.toString(hideDataTypeCb.isSelected()),
                                                                                 typeNameSepFld.getText()},
                                                                   fileExtn,
                                                                   systemFld.getText(),
                                                                   versionFld.getText(),
                                                                   validStatFld.getText(),
                                                                   class1Fld.getText(),
                                                                   class2Fld.getText(),
                                                                   class3Fld.getText(),
                                                                   CcddTableManagerDialog.this);
                            }

                            break;
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Create the table tree selection panel
     *
     * @param labeltext
     *            label to display above the table tree
     *
     * @param gbc
     *            GridBagLayout layout constraints
     *
     * @param tableSelect
     *            table tree item selection model
     *
     * @param treeType
     *            table tree type: PROTOTYPE_ONLY to show only the prototype tables, INSTANCE_ONLY
     *            to show only the table instances (parent tables with child tables), or BOTH to
     *            show the prototypes and instances
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

                    // Deselect any nodes that don't represent a table
                    tableTree.clearNonTableNodes(0);

                    // Check if this is a rename or copy dialog
                    if (dialogType == ManagerDialogType.RENAME
                        || dialogType == ManagerDialogType.COPY)
                    {
                        // Check if a table is selected
                        if (tableTree.getSelectionPath() != null
                            && tableTree.getSelectionPath().getPathCount() > tableTree.getHeaderNodeLevel())
                        {
                            // Get the name of the table selected
                            String name = getSelectionPath().getLastPathComponent().toString();

                            // Get the table description
                            descriptionFld.setText(tableTree.getTableDescription(name, ""));

                            // Check if this is a copy table dialog
                            if (dialogType == ManagerDialogType.COPY)
                            {
                                // Append text to the name to differentiate the copy from the
                                // original
                                name += "_copy";
                            }

                            // Set the new name field to match the selected table's name
                            nameFld.setText(name);

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
                                                      "<html><b>Project '"
                                                              + dbControl.getDatabaseName()
                                                              + "' has no tables",
                                                      "No Tables",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return allPnl;
    }

    /**********************************************************************************************
     * Create the import option dialog panel
     *
     * @return Import option dialog panel
     *********************************************************************************************/
    private JPanel createImportPanel(GridBagConstraints gbc)
    {
        // Create a panel to hold the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create a check box for indicating existing tables can be replaced
        replaceExistingTablesCb = new JCheckBox("Replace existing tables");
        replaceExistingTablesCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        replaceExistingTablesCb.setBorder(emptyBorder);
        replaceExistingTablesCb.setToolTipText(CcddUtilities.wrapText("Replace data tables that already exist with the imported table",
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

        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
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

        // Create a check box for indicating that the project should be backed up prior to
        // importing tables
        backupFirstCb = new JCheckBox("Backup project before importing");
        backupFirstCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        backupFirstCb.setBorder(emptyBorder);
        backupFirstCb.setToolTipText(CcddUtilities.wrapText("Back up the project database prior to importing the table files",
                                                            ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        gbc.insets.bottom = 0;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
        gbc.gridy++;
        dialogPnl.add(backupFirstCb, gbc);

        return dialogPnl;
    }

    /**********************************************************************************************
     * Create the export dialog panel
     *
     * @param fileExtn
     *            file extension type
     *
     * @param gbc
     *            GridBagLayout layout constraints
     *
     * @return Export dialog panel
     *********************************************************************************************/
    private JPanel createExportPanel(final FileExtension fileExtn,
                                     GridBagConstraints gbc)
    {
        JPanel dialogPnl = null;

        // Check if the export command originated from the main menu
        if (callingEditorDialog == null)
        {
            // Create a panel containing a table tree for the dialog components
            dialogPnl = createSelectionPanel("Select table(s) to export",
                                             gbc,
                                             TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                             TableTreeType.TABLES);
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() * 2;
        }
        // The export command originated from a table editor dialog menu
        else
        {
            // Create an empty panel for the dialog components
            dialogPnl = new JPanel(new GridBagLayout());
        }

        // Check that the panel was created; i.e., that there are tables available for exporting
        if (dialogPnl != null)
        {
            int yStore = gbc.gridy;

            // Create a panel to contain the separator character labels and inputs
            JPanel separatorPnl = new JPanel(new GridBagLayout());

            // Create the check box for inclusion of variable paths
            includeVariablePaths = new JCheckBox("Include variable paths");
            includeVariablePaths.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            includeVariablePaths.setBorder(emptyBorder);
            includeVariablePaths.setToolTipText(CcddUtilities.wrapText("If checked, each variable's path in a structure table "
                                                                       + "is included, both in the application format and "
                                                                       + "using the separator characters specified by the user",
                                                                       ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.insets.left = 0;
            gbc.insets.top = 0;
            gbc.gridy++;
            separatorPnl.add(includeVariablePaths, gbc);

            // Create the variable path separator label and input field, and add them to the dialog
            // panel
            final JLabel varPathSepLbl = new JLabel("Enter variable path separator character(s)");
            varPathSepLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            varPathSepLbl.setEnabled(false);
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.gridy++;
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

            // Create the data type/variable name separator label and input field, and add them to
            // the dialog panel
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
            gbc.insets.bottom = 0;
            gbc.gridx = 0;
            gbc.gridy++;
            separatorPnl.add(hideDataTypeCb, gbc);

            // Add the export storage path components to the dialog
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.weighty = 0.0;
            gbc.gridy = yStore + 1;
            dialogPnl.add(createPathSelectionPanel(fileExtn), gbc);

            // Create a check box for indicating existing files can be replaced
            overwriteFileCb = new JCheckBox("Overwrite existing file(s)");
            overwriteFileCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            overwriteFileCb.setBorder(emptyBorder);
            overwriteFileCb.setToolTipText(CcddUtilities.wrapText("Select to overwrite any file(s) with the same name",
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.gridy++;
            dialogPnl.add(overwriteFileCb, gbc);

            // Create a check box for indicating existing tables can be replaced
            singleFileCb = new JCheckBox("Store tables in one file");
            singleFileCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            singleFileCb.setBorder(emptyBorder);
            singleFileCb.setToolTipText(CcddUtilities.wrapText("Select to store multiple tables in a single file",
                                                               ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));

            // Add a listener for the single file check box selection changes
            singleFileCb.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Respond to changes in selection of a the single file check box
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Set the export label text and path field
                    exportLbl.setText(singleFileCb.isSelected()
                                                                ? "Enter or select an export file"
                                                                : "Enter or select an export path");
                }
            });

            gbc.gridy++;
            dialogPnl.add(singleFileCb, gbc);

            // Create the macro replacement check box
            replaceMacrosCb = new JCheckBox("Substitute macro values for macro names");
            replaceMacrosCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            replaceMacrosCb.setBorder(emptyBorder);
            replaceMacrosCb.setToolTipText(CcddUtilities.wrapText("If checked, the macros are replaced with their "
                                                                  + "corresponding values prior to exporting the "
                                                                  + "table(s).  If not checked, the macro names are "
                                                                  + "retained and the macro information is stored "
                                                                  + "with the exported table(s)",
                                                                  ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.gridy++;
            dialogPnl.add(replaceMacrosCb, gbc);

            // Create the reserved message ID inclusion check box
            includeReservedMsgIDsCb = new JCheckBox("Include reserved message IDs");
            includeReservedMsgIDsCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            includeReservedMsgIDsCb.setBorder(emptyBorder);
            includeReservedMsgIDsCb.setToolTipText(CcddUtilities.wrapText("If checked, the contents of the reserved "
                                                                          + "message ID table (IDs or ID ranges, and "
                                                                          + "their corresponding descriptions) is "
                                                                          + "included in each export file",
                                                                          ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
            gbc.gridy++;
            dialogPnl.add(includeReservedMsgIDsCb, gbc);

            // Add the variable path inclusion and separator character inputs panel
            JSeparator upperSep = new JSeparator();
            upperSep.setForeground(dialogPnl.getBackground().darker());
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.gridy++;
            dialogPnl.add(upperSep, gbc);
            gbc.fill = GridBagConstraints.VERTICAL;
            gbc.weightx = 0.0;
            gbc.gridy++;
            dialogPnl.add(separatorPnl, gbc);
            JSeparator lowerSep = new JSeparator();
            lowerSep.setForeground(dialogPnl.getBackground().darker());
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
            gbc.weightx = 1.0;
            gbc.gridy++;
            dialogPnl.add(lowerSep, gbc);

            // Add a listener for the hide data type check box
            hideDataTypeCb.addActionListener(new ActionListener()
            {
                /******************************************************************************
                 * Handle a change in the hide data type check box status
                 *****************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Enable/disable the data type/variable name separator input label and field
                    typeNameSepLbl.setEnabled(!((JCheckBox) ae.getSource()).isSelected());
                    typeNameSepFld.setEnabled(!((JCheckBox) ae.getSource()).isSelected());
                }
            });

            // Add a listener for the variable paths check box selection changes
            includeVariablePaths.addActionListener(new ActionListener()
            {
                /**********************************************************************************
                 * Respond to changes in selection of a the include variable paths check box
                 *********************************************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Enable/disable the separator inputs based on the inclusion check box state
                    varPathSepLbl.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                    varPathSepFld.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                    typeNameSepLbl.setEnabled(((JCheckBox) ae.getSource()).isSelected()
                                              && !hideDataTypeCb.isSelected());
                    typeNameSepFld.setEnabled(((JCheckBox) ae.getSource()).isSelected()
                                              && !hideDataTypeCb.isSelected());
                    hideDataTypeCb.setEnabled(((JCheckBox) ae.getSource()).isSelected());
                }
            });

            // Create the XTCE and EDS input fields with their default values. XTCE uses all of
            // these fields; CSV and EDS use only the system input
            versionFld = new JTextField("1.0");
            validStatFld = new JTextField("Working");
            class1Fld = new JTextField("DOMAIN");
            class2Fld = new JTextField("SYSTEM");
            class3Fld = new JTextField("INTERFACE");

            // Create the panel to hold the system data field components of the dialog
            JPanel systemPnl = new JPanel(new GridBagLayout());

            // Create the system data field label
            JLabel systemLbl = new JLabel("System data field name");
            systemLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
            gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
            gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
            gbc.insets.bottom = 0;
            gbc.weightx = 0.0;
            systemPnl.add(systemLbl, gbc);

            // Create the system data field input field
            systemFld = new JTextField("System");
            systemFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
            systemFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
            systemFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
            systemFld.setBorder(border);
            gbc.fill = GridBagConstraints.BOTH;
            gbc.weightx = 1.0;
            gbc.gridx++;
            systemPnl.add(systemFld, gbc);
            gbc.insets.top = 0;
            gbc.insets.left = 0;
            gbc.insets.right = 0;
            gbc.gridx = 0;
            gbc.gridy++;
            dialogPnl.add(systemPnl, gbc);

            // Check if exporting in XTCE XML format
            if (dialogType == ManagerDialogType.EXPORT_XTCE)
            {
                // Create the panels to hold the XTCE components of the dialog
                JPanel infoPnl = new JPanel(new GridBagLayout());
                JPanel classPnl = new JPanel(new GridBagLayout());

                // Create the attributes label
                JLabel descriptionLbl = new JLabel("XTCE Attributes");
                descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                descriptionLbl.setForeground(ModifiableColorInfo.SPECIAL_LABEL_TEXT.getColor());
                gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
                gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() / 2;
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
     * @param fieldText
     *            text to display beside the input field
     *
     * @param dialogPnl
     *            panel to which to add the input field
     *
     * @param enabled
     *            true if the fields are initially enabled, false if disabled
     *
     * @param dialogGbc
     *            dialog panel GridBagLayout layout constraints
     *********************************************************************************************/
    private void addTableInputFields(String fieldText,
                                     JPanel dialogPnl,
                                     boolean enabled,
                                     GridBagConstraints dialogGbc)
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
        nameFld.setBackground(enabled
                                      ? ModifiableColorInfo.INPUT_BACK.getColor()
                                      : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        nameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        nameFld.setEditable(true);
        nameFld.setBorder(border);

        // Create the description label and field
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        descriptionFld.setBackground(enabled
                                             ? ModifiableColorInfo.INPUT_BACK.getColor()
                                             : ModifiableColorInfo.INPUT_DISABLE_BACK.getColor());
        descriptionFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        descriptionFld.setEditable(enabled);
        descriptionFld.setLineWrap(true);
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(enabled
                                             ? ModifiableColorInfo.INPUT_BACK.getColor()
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
     * Create the path selection panel
     *
     * @param fileExtn
     *            file extension type
     *
     * @return JPanel containing the path selection panel
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
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel for the path selection components
        JPanel pathPnl = new JPanel(new GridBagLayout());
        pathPnl.setBorder(emptyBorder);

        // Create the path selection dialog labels and fields
        exportLbl = new JLabel("Enter or select an export path");
        exportLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        pathPnl.add(exportLbl, gbc);

        // Create a text field for entering & displaying the path
        pathFld = new JTextField(ModifiablePathInfo.TABLE_EXPORT_PATH.getPath());
        pathFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        pathFld.setEditable(true);
        pathFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        pathFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        pathFld.setBorder(border);
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.gridy++;
        pathPnl.add(pathFld, gbc);

        // Create a button for choosing an export path
        JButton btnSelectPath = CcddButtonPanelHandler.createButton("Select...",
                                                                    EXPORT_ICON,
                                                                    KeyEvent.VK_S,
                                                                    "Open the export path selection dialog");

        // Add a listener for the Select path button
        btnSelectPath.addActionListener(new ActionListener()
        {
            /**************************************************************************************
             * Select a export storage path
             *************************************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                File[] filePath;

                // Check if tables should be exported to a single file
                if (singleFileCb.isSelected())
                {
                    // Allow the user to select the export storage path+file
                    filePath = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                      CcddTableManagerDialog.this,
                                                                      null,
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

                // Check if a export path is selected
                if (filePath != null && filePath[0] != null)
                {
                    // Display the path name in the export path field
                    pathFld.setText(filePath[0].getAbsolutePath());
                }
            }
        });

        // Add the select export path button to the dialog
        gbc.weightx = 0.0;
        gbc.insets.right = 0;
        gbc.gridx++;
        pathPnl.add(btnSelectPath, gbc);

        return pathPnl;
    }

    /**********************************************************************************************
     * Verify that the supplied table name is valid
     *
     * @param tableName
     *            table name
     *
     * @throws CCDDException
     *             If the table name is invalid
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
            throw new CCDDException("Table name '"
                                    + tableName
                                    + "' too long ("
                                    + (MAX_SQL_NAME_LENGTH - 1)
                                    + " characters maximum)");
        }

        // Check if the name contains an illegal character
        if (!tableName.matches(InputDataType.ALPHANUMERIC.getInputMatch()))
        {
            // Inform the user that the name is invalid
            throw new CCDDException("Illegal character(s) in table name '"
                                    + tableName
                                    + "'");
        }

        // Check if the name matches that of a primitive data type
        if (dataTypeHandler.isPrimitive(tableName))
        {
            // Inform the user that the name matches a primitive type
            throw new CCDDException("Table name '"
                                    + tableName
                                    + "'matches a primitive data type");
        }

        // Step through the list of reserved words
        for (String keyWord : dbControl.getKeyWords())
        {
            // Check if the table name matches the reserved word
            if (tableName.equalsIgnoreCase(keyWord))
            {
                // Inform the user that the table name is a reserved word
                throw new CCDDException("Table name '"
                                        + tableName
                                        + "' matches a reserved word");
            }
        }

        // Check if the table starts with the characters designating an internal table
        if (tableName.startsWith(INTERNAL_TABLE_PREFIX))
        {
            // Inform the user that the table name can't begin with the internal table prefix
            throw new CCDDException("Table name '"
                                    + tableName
                                    + "' cannot begin with '"
                                    + INTERNAL_TABLE_PREFIX
                                    + "'");
        }

        // Get the list of available tables
        String[] namesInUse = dbTable.queryTableList(CcddTableManagerDialog.this);

        // Step through each of the table names
        for (String nameInUse : namesInUse)
        {
            // Check if the user-supplied name matches an existing table name. If renaming, the new
            // name may differ by capitalization or characters; otherwise (if creating or copying)
            // the names must differ with the text forced to lower case
            if ((dialogType == ManagerDialogType.RENAME
                 && nameInUse.equals(tableName))
                || (dialogType != ManagerDialogType.RENAME
                    && nameInUse.equalsIgnoreCase(tableName)))
            {
                // Inform the user that the name is already in use
                throw new CCDDException("Table name '"
                                        + tableName
                                        + "' is already in use");
            }
        }
    }

    /**********************************************************************************************
     * Verify that the dialog content is valid
     *
     * @return true if the input values are valid
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
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
                            throw new CCDDException("Table name '"
                                                    + tableName
                                                    + "' is a duplicate");
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
                    if (tableTree.getSelectionCount() == 0)
                    {
                        // Inform the user that no item has been selected
                        throw new CCDDException("Must select a table from the tree");
                    }

                    break;

                case IMPORT:
                    // Remove any leading or trailing white space characters from the file
                    // path/name
                    getFileNameField().setText(getFileNameField().getText().trim());

                    // Check if the name field is empty or contains no file name in the path
                    if (getFileNameField().getText().isEmpty()
                        || getFileNameField().getText().matches(".*\\"
                                                                + File.separator
                                                                + "\\.*?$"))
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

                    break;

                case EXPORT_CSV:
                case EXPORT_EDS:
                case EXPORT_JSON:
                case EXPORT_XTCE:
                    // Remove any leading or trailing white space characters from the file
                    // path/name
                    pathFld.setText(pathFld.getText().trim());

                    // Check if the export command originated from the main menu and no table has
                    // been selected
                    if (callingEditorDialog == null
                        && tableTree.getSelectionCount() == 0)
                    {
                        // Inform the user that no table has been selected
                        throw new CCDDException("Must select a table from the tree");
                    }

                    // Check if the table(s) are to be stored in a single file
                    if (singleFileCb.isSelected())
                    {
                        // Check if the name field is empty or contains no file name in the path
                        if (pathFld.getText().isEmpty()
                            || pathFld.getText().matches(".*\\"
                                                         + File.separator
                                                         + "\\.*?$"))
                        {
                            // Inform the user that no file name has been selected
                            throw new CCDDException("Must select an export file name");
                        }

                        // Create a file reference from the file path/name
                        File file = new File(pathFld.getText());

                        // Check if the file already exists; if so, the name is valid
                        if (!file.exists())
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
                                // Inform the user that no file name has been selected
                                throw new CCDDException("Invalid export file name");
                            }
                        }
                    }

                    // Check if exporting in XTCE XML format
                    if (dialogType == ManagerDialogType.EXPORT_XTCE)
                    {
                        // Remove any excess white space
                        systemFld.setText(systemFld.getText().trim());
                        versionFld.setText(versionFld.getText().trim());
                        validStatFld.setText(validStatFld.getText().trim());
                        class1Fld.setText(class1Fld.getText().trim());
                        class2Fld.setText(class2Fld.getText().trim());
                        class3Fld.setText(class3Fld.getText().trim());

                        // Check if any of the fields is blank
                        if (systemFld.getText().isEmpty()
                            || versionFld.getText().isEmpty()
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
                    }
                    // Check if exporting in EDS XML format
                    else if (dialogType == ManagerDialogType.EXPORT_EDS)
                    {
                        // Remove any excess white space
                        systemFld.setText(systemFld.getText().trim());

                        // Check if the system name field is blank
                        if (systemFld.getText().isEmpty())
                        {
                            // Inform the user that a required field is missing
                            throw new CCDDException("System data field name missing");
                        }
                    }

                    break;
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
