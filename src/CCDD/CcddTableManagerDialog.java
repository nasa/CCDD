/**
 * CFS Command & Data Dictionary table manager dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.EXPORT_ICON;
import static CCDD.CcddConstants.INTERNAL_TABLE_PREFIX;
import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.TABLE_EXPORT_PATH;

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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary table manager dialog class
 *****************************************************************************/
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

    /**************************************************************************
     * Table manager dialog class constructor (for all tables)
     * 
     * @param ccddMain
     *            main class
     * 
     * @param type
     *            table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     *************************************************************************/
    protected CcddTableManagerDialog(CcddMain ccddMain, ManagerDialogType type)
    {
        this(ccddMain, type, null);
    }

    /**************************************************************************
     * Table manager dialog class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param dialogType
     *            table manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     * 
     * @param callingEditorDialog
     *            reference to the table editor dialog that instantiated this
     *            table manager. Only used when called to open a table in an
     *            existing editor; null otherwise
     *************************************************************************/
    protected CcddTableManagerDialog(CcddMain ccddMain,
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

        isNodeSelectionChanging = false;

        // Create the table selection dialog
        initialize();
    }

    /**************************************************************************
     * Perform the steps needed following execution of a table management
     * operation
     *************************************************************************/
    protected void doTableOperationComplete()
    {
        // Perform the steps based on the dialog type
        switch (dialogType)
        {
            case NEW:
            case COPY:
                // Update any open editor's data type columns to include the
                // new table(s), if applicable
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
                        int tabIndex = 0;

                        // Step through each individual editor
                        for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                        {
                            // Update the table names in the open editors
                            CcddTableEditorDialog.updateTableNames(ccddMain,
                                                                   oldName,
                                                                   nameFld.getText(),
                                                                   null,
                                                                   null,
                                                                   tabIndex,
                                                                   editorDialog,
                                                                   editor);

                            tabIndex++;
                        }
                    }

                    // Update any open editor's data type columns to account
                    // for the renamed table(s), if applicable
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
    }

    /**************************************************************************
     * Create the table select dialog
     *************************************************************************/
    private void initialize()
    {
        // Create an empty border
        emptyBorder = BorderFactory.createEmptyBorder();

        // Create a border for the input fields
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
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   0,
                                                                   0),
                                                        0,
                                                        0);

        JPanel dialogPnl;

        // Create dialog based on supplied dialog type
        switch (dialogType)
        {
            case NEW:
                // Create a panel to contain the dialog components
                dialogPnl = new JPanel(new GridBagLayout());
                dialogPnl.setBorder(emptyBorder);

                // Create a panel containing a grid of radio buttons
                // representing the table types from which to choose
                if (addRadioButtons(null,
                                    false,
                                    tableTypeHandler.getTypeInformation(),
                                    null,
                                    "Select table type",
                                    dialogPnl,
                                    gbc))
                {
                    // Create the table creation dialog label and field, and
                    // add them to the dialog panel
                    addTableInputFields("Table name(s)", dialogPnl, true, gbc);
                    nameFld.setToolTipText("Delimit multiple names using commas");

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
                }
                // No table type exists to choose
                else
                {
                    // Inform the user that no table type exists in the
                    // database
                    new CcddDialogHandler().showMessageDialog(caller,
                                                              "<html><b>Project '"
                                                                  + dbControl.getDatabase()
                                                                  + "' has no table type defined",
                                                              "New Table",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }

                break;

            case EDIT:
                // Create a panel to contain the dialog components
                dialogPnl = createSelectionPanel("Select table(s) to edit",
                                                 gbc,
                                                 TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                 TableTreeType.PROTOTYPE_AND_INSTANCE);

                // Check that the panel was created; i.e., that there are
                // tables available for editing
                if (dialogPnl != null)
                {
                    // Add a listener to the table tree for mouse events
                    tableTree.addMouseListener(new MouseAdapter()
                    {
                        /******************************************************
                         * Handle mouse press events
                         *****************************************************/
                        @Override
                        public void mousePressed(MouseEvent me)
                        {
                            // Check if the right mouse button is double
                            // clicked
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

                                    // Check that a table was selected
                                    if (path.getPathCount() > tableTree.getTableNodeLevel())
                                    {
                                        // Load the selected table's data into
                                        // a table editor and close this dialog
                                        dbTable.loadTableDataInBackground(tableTree.getFullVariablePath(path.getPath()),
                                                                          callingEditorDialog);
                                        closeDialog();
                                    }
                                }
                            }
                        }
                    });

                    // Display the table selection dialog
                    if (showOptionsDialog(caller,
                                          dialogPnl,
                                          "Edit Table",
                                          DialogOption.OPEN_OPTION,
                                          true) == OK_BUTTON)
                    {
                        // Create store for the list of table paths and the
                        // root table names
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
                }

                break;

            case RENAME:
                // Create a panel to contain the dialog components
                dialogPnl = createSelectionPanel("Select a table to rename",
                                                 gbc,
                                                 TreeSelectionModel.SINGLE_TREE_SELECTION,
                                                 TableTreeType.PROTOTYPE_ONLY);

                // Check that the panel was created; i.e., that there are
                // tables available for renaming
                if (dialogPnl != null)
                {
                    // Create the table renaming dialog label and field
                    addTableInputFields("New name", dialogPnl, false, gbc);

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
                }

                break;

            case COPY:
                // Create a panel to contain the dialog components
                dialogPnl = createSelectionPanel("Select a table to copy",
                                                 gbc,
                                                 TreeSelectionModel.SINGLE_TREE_SELECTION,
                                                 TableTreeType.PROTOTYPE_ONLY);

                // Check that the panel was created; i.e., that there are
                // tables available for copying
                if (dialogPnl != null)
                {
                    // Create the table copying dialog label and field
                    addTableInputFields("Copy name", dialogPnl, false, gbc);

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
                }

                break;

            case DELETE:
                // Create a panel to contain the dialog components
                dialogPnl = createSelectionPanel("Select table(s) to delete",
                                                 gbc,
                                                 TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                 TableTreeType.PROTOTYPE_ONLY);

                // Check that the panel was created; i.e., that there are
                // tables available for deleting
                if (dialogPnl != null)
                {
                    // Display the table deletion dialog
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

                            // Add the pattern for the data type path of tables
                            // matching the deleted prototype table
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
                }

                break;

            case IMPORT:
                // Allow the user to select the data file path + name(s) from
                // which to import, and the import options
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
                                                 TABLE_EXPORT_PATH,
                                                 DialogOption.IMPORT_OPTION,
                                                 createImportPanel(gbc));

                // Check if a file was chosen
                if (filePath != null)
                {
                    // Export the contents of the selected table(s) in the
                    // specified format
                    fileIOHandler.importFile(filePath,
                                             backupFirstCb.isSelected(),
                                             replaceExistingTablesCb.isSelected(),
                                             appendExistingFieldsCb.isSelected(),
                                             useExistingFieldsCb.isSelected(),
                                             this);
                }

                break;

            case EXPORT_CSV:
            case EXPORT_XTCE:
            case EXPORT_EDS:
            case EXPORT_JSON:
                // Assume the file extension type is CSV
                FileExtension fileExtn = FileExtension.CSV;

                // Set the file extension based on the dialog type
                if (dialogType == ManagerDialogType.EXPORT_EDS)
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

                // Check if the export panel exists; if so display the dialog
                if (dialogPnl != null
                    && showOptionsDialog(caller,
                                         dialogPnl,
                                         "Export Table(s) in "
                                             + fileExtn.getExtensionName().toUpperCase()
                                             + " Format",
                                         DialogOption.EXPORT_OPTION,
                                         true) == OK_BUTTON)
                {
                    // Create storage for the list of table+variable names,
                    // table paths, and the parent table names
                    List<String> protoVarNames = new ArrayList<String>();
                    List<String> tablePaths = new ArrayList<String>();

                    // Check if the export command originated from the main
                    // menu
                    if (callingEditorDialog == null)
                    {
                        // Step through each selected table in the tree
                        for (TreePath path : tableTree.getSelectionPaths())
                        {
                            // Check if the table isn't already in the list
                            if (!protoVarNames.contains(path.getLastPathComponent().toString()))
                            {
                                // Add the table variable name and variable
                                // path to the lists
                                protoVarNames.add(path.getLastPathComponent().toString());
                                tablePaths.add(tableTree.getFullVariablePath(path.getPath()));
                            }
                        }
                    }
                    // The export command originated from a table editor dialog
                    // menu
                    else
                    {
                        // Add the table editor's table variable name and
                        // variable path to the lists
                        protoVarNames.add(callingEditorDialog.getTableEditor().getTableInformation().getProtoVariableName());
                        tablePaths.add(callingEditorDialog.getTableEditor().getTableInformation().getTablePath());
                    }

                    // Export the contents of the selected table(s) in the
                    // specified format
                    fileIOHandler.exportSelectedTables(pathFld.getText(),
                                                       protoVarNames.toArray(new String[0]),
                                                       tablePaths.toArray(new String[0]),
                                                       overwriteFileCb.isSelected(),
                                                       singleFileCb.isSelected(),
                                                       replaceMacrosCb.isSelected(),
                                                       includeReservedMsgIDsCb.isSelected(),
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

    /**************************************************************************
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
     *            table tree type: PROTOTYPE_ONLY to show only the prototype
     *            tables, INSTANCE_ONLY to show only the table instances
     *            (parent tables with child tables), or BOTH to show the
     *            prototypes and instances
     * 
     * @return JPanel containing the selection panel
     *************************************************************************/
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
                                                                  ccddMain.getMainFrame()),
                                             treeType,
                                             true,
                                             false,
                                             ccddMain.getMainFrame())
        {
            /******************************************************************
             * Respond to changes in selection of a table in the table tree
             *****************************************************************/
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
                            && tableTree.getSelectionPath().getPathCount()
                            > tableTree.getTableNodeLevel())
                        {
                            // Get the name of the table selected
                            String name = getSelectionPath().getLastPathComponent().toString();

                            // Get the table description
                            descriptionFld.setText(tableTree.getTableDescription(name, ""));

                            // Check if this is a copy table dialog
                            if (dialogType == ManagerDialogType.COPY)
                            {
                                // Append text to the name to differentiate the
                                // copy from the original
                                name += "_copy";
                            }

                            // Set the new name field to match the selected
                            // table's name
                            nameFld.setText(name);

                            // Enable and set the background color for the
                            // table name and description fields
                            nameFld.setEditable(true);
                            nameFld.setBackground(Color.WHITE);
                            descriptionFld.setEditable(true);
                            descriptionFld.setBackground(Color.WHITE);
                            descScrollPane.setBackground(Color.WHITE);
                        }
                        // Not a rename or copy dialog, or no table is selected
                        else
                        {
                            // Clear, disable, and set the background color for
                            // the table name and description fields
                            nameFld.setText("");
                            descriptionFld.setText("");
                            nameFld.setEditable(false);
                            nameFld.setBackground(Color.LIGHT_GRAY);
                            descriptionFld.setEditable(false);
                            descriptionFld.setBackground(Color.LIGHT_GRAY);
                            descScrollPane.setBackground(Color.LIGHT_GRAY);
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
            dlgLabel.setFont(LABEL_FONT_BOLD);
            upperPnl.add(dlgLabel, gbc);
            allPnl.add(upperPnl, gbc);

            // Create the table tree panel with selection check boxes and add
            // it to the dialog
            gbc.weighty = 1.0;
            gbc.insets.left = LABEL_HORIZONTAL_SPACING;
            lowerPnl.add(tableTree.createTreePanel("Tables",
                                                   tableSelect,
                                                   ccddMain.getMainFrame()),
                         gbc);
            gbc.insets.right = LABEL_HORIZONTAL_SPACING;
            gbc.gridy++;
            allPnl.add(lowerPnl, gbc);
        }
        // No tables are stored in the project database
        else
        {
            // Inform the user that no table exists for this database
            new CcddDialogHandler().showMessageDialog(caller,
                                                      "<html><b>Project '"
                                                          + dbControl.getDatabase()
                                                          + "' has no tables",
                                                      "No Tables",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return allPnl;
    }

    /**************************************************************************
     * Create the import option dialog panel
     * 
     * @return Import option dialog panel
     *************************************************************************/
    private JPanel createImportPanel(GridBagConstraints gbc)
    {
        // Create a panel to hold the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create a check box for indicating existing tables can be replaced
        replaceExistingTablesCb = new JCheckBox("Replace existing tables");
        replaceExistingTablesCb.setFont(LABEL_FONT_BOLD);
        replaceExistingTablesCb.setBorder(emptyBorder);
        replaceExistingTablesCb.setToolTipText("Replace data tables that already exist with the imported table");

        // Add a listener for changes to the Replace Existing Tables check box
        // selection status
        replaceExistingTablesCb.addActionListener(new ActionListener()
        {
            /**********************************************************
             * Handle a change to the Replace Existing Tables check box
             * selection status
             *********************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Set the Append Existing Fields check box status based on the
                // Replace Existing Tables check box status
                appendExistingFieldsCb.setEnabled(replaceExistingTablesCb.isSelected());
            }
        });

        dialogPnl.add(replaceExistingTablesCb, gbc);

        // Create a check box for indicating existing data fields are retained
        appendExistingFieldsCb = new JCheckBox("Append existing data fields");
        appendExistingFieldsCb.setFont(LABEL_FONT_BOLD);
        appendExistingFieldsCb.setBorder(emptyBorder);
        appendExistingFieldsCb.setToolTipText("Append existing data fields to those imported");
        appendExistingFieldsCb.setEnabled(false);

        // Add a listener for changes to the Append Original Fields check box
        // selection status
        appendExistingFieldsCb.addActionListener(new ActionListener()
        {
            /**********************************************************
             * Handle a change to the Append Existing Fields check box
             * selection status
             *********************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                // Set the Use Existing Fields check box status based on the
                // Append Existing Fields check box status
                useExistingFieldsCb.setEnabled(appendExistingFieldsCb.isSelected());
            }
        });

        gbc.insets.top = LABEL_VERTICAL_SPACING * 2;
        gbc.insets.left += LABEL_HORIZONTAL_SPACING * 2;
        gbc.gridy++;
        dialogPnl.add(appendExistingFieldsCb, gbc);

        // Create a check box for indicating existing tables can be replaced
        useExistingFieldsCb = new JCheckBox("Use existing field if duplicate");
        useExistingFieldsCb.setFont(LABEL_FONT_BOLD);
        useExistingFieldsCb.setBorder(emptyBorder);
        useExistingFieldsCb.setToolTipText("Use the existing data field definition if "
                                           + "a field with the same name is imported");
        useExistingFieldsCb.setEnabled(false);
        gbc.insets.left += LABEL_HORIZONTAL_SPACING * 2;
        gbc.gridy++;
        dialogPnl.add(useExistingFieldsCb, gbc);

        // Create a check box for indicating that the project should be backed
        // up prior to importing tables
        backupFirstCb = new JCheckBox("Backup project before importing");
        backupFirstCb.setFont(LABEL_FONT_BOLD);
        backupFirstCb.setBorder(emptyBorder);
        backupFirstCb.setToolTipText("Back up the project database prior to importing the table files");
        gbc.insets.bottom = 0;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.gridy++;
        dialogPnl.add(backupFirstCb, gbc);

        return dialogPnl;
    }

    /**************************************************************************
     * Create the export dialog panel
     * 
     * @param fileExtn
     *            file extension type
     * 
     * @param gbc
     *            GridBagLayout layout constraints
     * 
     * @return Export dialog panel
     *************************************************************************/
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
                                             TableTreeType.PROTOTYPE_AND_INSTANCE);
            gbc.insets.top = LABEL_VERTICAL_SPACING * 2;
        }
        // The export command originated from a table editor dialog menu
        else
        {
            // Create an empty panel for the dialog components
            dialogPnl = new JPanel(new GridBagLayout());
        }

        // Check that the panel was created; i.e., that there are tables
        // available for exporting
        if (dialogPnl != null)
        {
            // Add the export storage path components to the dialog
            gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
            gbc.weighty = 0.0;
            gbc.gridy++;
            dialogPnl.add(createPathSelectionPanel(fileExtn), gbc);

            // Create a check box for indicating existing files can be
            // replaced
            overwriteFileCb = new JCheckBox("Overwrite existing file(s)");
            overwriteFileCb.setFont(LABEL_FONT_BOLD);
            overwriteFileCb.setBorder(emptyBorder);
            overwriteFileCb.setToolTipText("Select to overwrite any file(s) with the same name");

            gbc.insets.top = LABEL_VERTICAL_SPACING * 2;
            gbc.gridy++;
            dialogPnl.add(overwriteFileCb, gbc);

            // Create a check box for indicating existing tables can be
            // replaced
            singleFileCb = new JCheckBox("Store tables in one file");
            singleFileCb.setFont(LABEL_FONT_BOLD);
            singleFileCb.setBorder(emptyBorder);
            singleFileCb.setToolTipText("Select to store multiple tables in a single file");

            // Add a listener for check box selection changes
            singleFileCb.addActionListener(new ActionListener()
            {
                /******************************************************
                 * Respond to changes in selection of a the single file check
                 * box
                 *****************************************************/
                @Override
                public void actionPerformed(ActionEvent ae)
                {
                    // Get the path of the last file saved
                    String filePath = ccddMain.getProgPrefs().get(TABLE_EXPORT_PATH, "");

                    // Check if the single file check box is selected
                    if (singleFileCb.isSelected())
                    {
                        // Get the location of the file extension
                        int index = filePath.lastIndexOf(".");

                        // Check if the file extension is present
                        if (index != -1)
                        {
                            // Remove the original file extension and append
                            // the current one
                            filePath = filePath.substring(0, index)
                                       + fileExtn.getExtension();
                        }

                        // Set the export label text and path field
                        exportLbl.setText("Enter or select an export file");
                        pathFld.setText(filePath);
                    }
                    // The single file check box isn't selected
                    else
                    {
                        // Set the export label text and path field
                        exportLbl.setText("Enter or select an export path");
                        pathFld.setText(filePath);
                    }
                }
            });

            gbc.gridy++;
            dialogPnl.add(singleFileCb, gbc);

            // Create the macro replacement check box
            replaceMacrosCb = new JCheckBox("Substitute macro values for macro names");
            replaceMacrosCb.setFont(LABEL_FONT_BOLD);
            replaceMacrosCb.setBorder(emptyBorder);
            replaceMacrosCb.setToolTipText("If checked, the macros are replaced with their "
                                           + "corresponding values prior to exporting the "
                                           + "table(s).  If not checked, the macro names are "
                                           + "retained and the macro information is stored "
                                           + "with the exported table(s)");
            gbc.gridy++;
            dialogPnl.add(replaceMacrosCb, gbc);

            // Create the reserved message ID inclusion check box
            includeReservedMsgIDsCb = new JCheckBox("Include reserved message IDs");
            includeReservedMsgIDsCb.setFont(LABEL_FONT_BOLD);
            includeReservedMsgIDsCb.setBorder(emptyBorder);
            includeReservedMsgIDsCb.setToolTipText("If checked, the contents of the reserved "
                                                   + "message ID table (IDs or ID ranges, and "
                                                   + "their corresponding descriptions) is "
                                                   + "included in each export file ");
            gbc.gridy++;
            dialogPnl.add(includeReservedMsgIDsCb, gbc);

            // Create the XTCE and EDS input fields with their default
            // values. XTCE uses all of these fields; CSV and EDS use only the
            // system input
            versionFld = new JTextField("1.0");
            validStatFld = new JTextField("Working");
            class1Fld = new JTextField("DOMAIN");
            class2Fld = new JTextField("SYSTEM");
            class3Fld = new JTextField("INTERFACE");

            // Create the panel to hold the system data field components of the
            // dialog
            JPanel systemPnl = new JPanel(new GridBagLayout());

            // Create the system data field label
            JLabel systemLbl = new JLabel("System data field name");
            systemLbl.setFont(LABEL_FONT_BOLD);
            gbc.insets.bottom = 0;
            gbc.weightx = 0.0;
            systemPnl.add(systemLbl, gbc);

            // Create the system data field input field
            systemFld = new JTextField("System");
            systemFld.setFont(LABEL_FONT_PLAIN);
            systemFld.setForeground(Color.BLACK);
            systemFld.setBackground(Color.WHITE);
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
                // Create the panels to hold the XTCE components of the
                // dialog
                JPanel infoPnl = new JPanel(new GridBagLayout());
                JPanel classPnl = new JPanel(new GridBagLayout());

                // Create the attributes label
                JLabel descriptionLbl = new JLabel("XTCE Attributes");
                descriptionLbl.setFont(LABEL_FONT_BOLD);
                descriptionLbl.setForeground(LABEL_TEXT_COLOR);
                gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
                gbc.insets.right = LABEL_HORIZONTAL_SPACING / 2;
                gbc.insets.top = LABEL_VERTICAL_SPACING * 2;
                gbc.gridy++;
                dialogPnl.add(descriptionLbl, gbc);

                // Create the version label
                JLabel versionLbl = new JLabel("Version");
                versionLbl.setFont(LABEL_FONT_BOLD);
                versionLbl.setForeground(Color.BLACK);
                gbc.insets.top = LABEL_VERTICAL_SPACING;
                gbc.weightx = 0.0;
                infoPnl.add(versionLbl, gbc);

                // Create the version input field
                versionFld.setFont(LABEL_FONT_PLAIN);
                versionFld.setForeground(Color.BLACK);
                versionFld.setBackground(Color.WHITE);
                versionFld.setBorder(border);
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weightx = 1.0;
                gbc.gridx++;
                infoPnl.add(versionFld, gbc);

                // Create the validation status label
                JLabel validStatLbl = new JLabel("Validation Status");
                validStatLbl.setFont(LABEL_FONT_BOLD);
                validStatLbl.setForeground(Color.BLACK);
                gbc.insets.bottom = LABEL_VERTICAL_SPACING;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                infoPnl.add(validStatLbl, gbc);

                // Create the validation status input field
                validStatFld.setFont(LABEL_FONT_PLAIN);
                validStatFld.setForeground(Color.BLACK);
                validStatFld.setBackground(Color.WHITE);
                validStatFld.setBorder(border);
                gbc.gridx++;
                infoPnl.add(validStatFld, gbc);

                // Create the classification label
                JLabel classLbl = new JLabel("Classification");
                classLbl.setFont(LABEL_FONT_BOLD);
                classLbl.setForeground(LABEL_TEXT_COLOR);
                gbc.insets.bottom = 0;
                gbc.gridx = 0;
                gbc.gridy++;
                infoPnl.add(classLbl, gbc);

                // Create the first level classification label
                JLabel class1Lbl = new JLabel("First level");
                class1Lbl.setFont(LABEL_FONT_BOLD);
                class1Lbl.setForeground(Color.BLACK);

                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class1Lbl, gbc);

                // Create the first classification input fields
                class1Fld.setFont(LABEL_FONT_PLAIN);
                class1Fld.setForeground(Color.BLACK);
                class1Fld.setBackground(Color.WHITE);
                class1Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class1Fld, gbc);

                // Create the second level classification label
                JLabel class2Lbl = new JLabel("Second level");
                class2Lbl.setFont(LABEL_FONT_BOLD);
                class2Lbl.setForeground(Color.BLACK);
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class2Lbl, gbc);

                // Create the second level classification input fields
                class2Fld.setFont(LABEL_FONT_PLAIN);
                class2Fld.setForeground(Color.BLACK);
                class2Fld.setBackground(Color.WHITE);
                class2Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class2Fld, gbc);

                // Create the third level classification label
                JLabel class3Lbl = new JLabel("Third level");
                class3Lbl.setFont(LABEL_FONT_BOLD);
                class3Lbl.setForeground(Color.BLACK);
                gbc.insets.bottom = LABEL_VERTICAL_SPACING;
                gbc.weightx = 0.0;
                gbc.gridx = 0;
                gbc.gridy++;
                classPnl.add(class3Lbl, gbc);

                // Create the third level classification input fields
                class3Fld.setFont(LABEL_FONT_PLAIN);
                class3Fld.setForeground(Color.BLACK);
                class3Fld.setBackground(Color.WHITE);
                class3Fld.setBorder(border);
                gbc.weightx = 1.0;
                gbc.gridx++;
                classPnl.add(class3Fld, gbc);

                // Add the classifications panel to the information
                // panel
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

    /**************************************************************************
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
     *************************************************************************/
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
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);
        // Create the table name label and field
        JLabel nameLbl = new JLabel(fieldText);
        nameLbl.setFont(LABEL_FONT_BOLD);
        nameFld = new JTextField("", 20);
        nameFld.setForeground(Color.BLACK);
        nameFld.setBackground(enabled
                                     ? Color.WHITE
                                     : Color.LIGHT_GRAY);
        nameFld.setFont(LABEL_FONT_PLAIN);
        nameFld.setEditable(true);
        nameFld.setBorder(border);

        // Create the description label and field
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionFld = new JTextArea("", 3, 20);
        descriptionFld.setForeground(Color.BLACK);
        descriptionFld.setBackground(enabled
                                            ? Color.WHITE
                                            : Color.LIGHT_GRAY);
        descriptionFld.setFont(LABEL_FONT_PLAIN);
        descriptionFld.setEditable(enabled);
        descriptionFld.setLineWrap(true);
        descriptionFld.setBorder(emptyBorder);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        descScrollPane = new JScrollPane(descriptionFld);
        descScrollPane.setBackground(enabled
                                            ? Color.WHITE
                                            : Color.LIGHT_GRAY);
        descScrollPane.setBorder(border);

        // Add the name and description labels and fields to a panel
        JPanel nameDescPnl = new JPanel(new GridBagLayout());
        nameDescPnl.setBorder(emptyBorder);
        nameDescPnl.add(nameLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING;
        nameDescPnl.add(nameFld, gbc);
        gbc.gridy++;
        gbc.insets.left = 0;
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        nameDescPnl.add(descriptionLbl, gbc);
        gbc.gridy++;
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = 0;
        nameDescPnl.add(descScrollPane, gbc);

        // Add the panel to the dialog panel
        dialogGbc.weighty = 0.0;
        dialogGbc.gridy++;
        dialogGbc.gridwidth = GridBagConstraints.REMAINDER;
        dialogGbc.fill = GridBagConstraints.HORIZONTAL;
        dialogGbc.insets.right = LABEL_HORIZONTAL_SPACING;
        dialogGbc.insets.bottom = 0;
        dialogPnl.add(nameDescPnl, dialogGbc);
    }

    /**************************************************************************
     * Create the path selection panel
     * 
     * @param fileExtn
     *            file extension type
     * 
     * @return JPanel containing the path selection panel
     *************************************************************************/
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
                                                        new Insets(LABEL_VERTICAL_SPACING / 2,
                                                                   0,
                                                                   LABEL_VERTICAL_SPACING / 2,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel for the path selection components
        JPanel pathPnl = new JPanel(new GridBagLayout());
        pathPnl.setBorder(emptyBorder);

        // Create the path selection dialog labels and fields
        exportLbl = new JLabel("Enter or select an export path");
        exportLbl.setFont(LABEL_FONT_BOLD);
        pathPnl.add(exportLbl, gbc);

        // Create a text field for entering & displaying the path
        pathFld = new JTextField(ccddMain.getProgPrefs().get(TABLE_EXPORT_PATH,
                                                             ""));
        pathFld.setFont(LABEL_FONT_PLAIN);
        pathFld.setEditable(true);
        pathFld.setForeground(Color.BLACK);
        pathFld.setBackground(Color.WHITE);
        pathFld.setBorder(border);
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
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
            /******************************************************************
             * Select a export storage path
             *****************************************************************/
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                File[] filePath;

                // Check if tables should be exported to a single file
                if (singleFileCb.isSelected())
                {
                    // Allow the user to select the export storage path
                    filePath = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                      CcddTableManagerDialog.this,
                                                                      null,
                                                                      null,
                                                                      new FileNameExtensionFilter[] {new FileNameExtensionFilter(fileExtn.getDescription(),
                                                                                                                                 fileExtn.getExtensionName())},
                                                                      false,
                                                                      false,
                                                                      "Select File for Exported Table(s)",
                                                                      TABLE_EXPORT_PATH,
                                                                      DialogOption.OK_CANCEL_OPTION);
                }
                // Export tables to individual files
                else
                {
                    // Allow the user to select the export storage path
                    filePath = new CcddDialogHandler().choosePathFile(ccddMain,
                                                                      CcddTableManagerDialog.this,
                                                                      null,
                                                                      null,
                                                                      null,
                                                                      true,
                                                                      false,
                                                                      "Select Location for Exported Table(s)",
                                                                      TABLE_EXPORT_PATH,
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

    /**************************************************************************
     * Verify that the supplied table name is valid
     * 
     * @param tableName
     *            table name
     *************************************************************************/
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

        // Check if the table starts with the characters designating an
        // internal table
        if (tableName.startsWith(INTERNAL_TABLE_PREFIX))
        {
            // Inform the user that the table name can't begin with the
            // internal table prefix
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
            // Check if the user-supplied name matches an existing table name.
            // If renaming, the new name may differ by capitalization or
            // characters; otherwise (if creating or copying) the names must
            // differ with the text forced to lower case
            if ((dialogType == ManagerDialogType.RENAME
                && nameInUse.equals(tableName))
                || (dialogType != ManagerDialogType.RENAME
                && nameInUse.toLowerCase().equals(tableName.toLowerCase())))
            {
                // Inform the user that the name is already in use
                throw new CCDDException("Table name '"
                                        + tableName
                                        + "' is already in use");
            }
        }
    }

    /**************************************************************************
     * Verify that the dialog content is valid
     * 
     * @return true if the input values are valid
     *************************************************************************/
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

                        // Check if the table name appears more than once in
                        // the list of new names. Force to lower case to remove
                        // case sensitivity
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
                    // Remove any leading or trailing white space characters
                    // from the file path/name
                    getFileNameField().setText(getFileNameField().getText().trim());

                    // Check if the name field is empty or contains no file
                    // name in the path
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

                        // Set the flag to indicate the dialog input is
                        // invalid
                        isValid = false;
                    }

                    break;

                case EXPORT_CSV:
                case EXPORT_EDS:
                case EXPORT_JSON:
                case EXPORT_XTCE:
                    // Remove any leading or trailing white space characters
                    // from the file path/name
                    pathFld.setText(pathFld.getText().trim());

                    // Check if the export command originated from the main
                    // menu and no table has been selected
                    if (callingEditorDialog == null
                        && tableTree.getSelectionCount() == 0)
                    {
                        // Inform the user that no table has been selected
                        throw new CCDDException("Must select a table from the tree");
                    }

                    // Check if the table(s) are to be stored in a single file
                    if (singleFileCb.isSelected())
                    {
                        // Check if the name field is empty or contains no file
                        // name in the path
                        if (pathFld.getText().isEmpty()
                            || pathFld.getText().matches(".*\\"
                                                         + File.separator
                                                         + "\\.*?$"))
                        {
                            // Inform the user that no file name has been
                            // selected
                            throw new CCDDException("Must select an export file name");
                        }

                        // Create a file reference from the file path/name
                        File file = new File(pathFld.getText());

                        // Check if the file already exists; if so, the name is
                        // valid
                        if (!file.exists())
                        {
                            try
                            {
                                // Attempt to create the file; an exception is
                                // thrown if unsuccessful
                                file.createNewFile();

                                // The file was successfully created; delete it
                                file.delete();
                            }
                            catch (Exception e)
                            {
                                // Inform the user that no file name has been
                                // selected
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
