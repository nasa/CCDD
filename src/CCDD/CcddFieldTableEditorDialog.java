/**
 * CFS Command and Data Dictionary data field table editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.OK_ICON;
import static CCDD.CcddConstants.PRINT_ICON;
import static CCDD.CcddConstants.PROJECT_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.TABLE_ICON;
import static CCDD.CcddConstants.TYPE_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.UNDO_ICON;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.TreeSelectionModel;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.CellSelectionHandler;
import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableOpener;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldTableEditorColumnInfo;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableCommentIndex;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddUndoHandler.UndoableCellSelection;

/**************************************************************************************************
 * CFS Command and Data Dictionary data field table editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddFieldTableEditorDialog extends CcddFrameHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddFieldHandler fieldHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private CcddDialogHandler selectDlg;
    private CcddTableTreeHandler tableTree;

    // Components that need to be accessed by multiple methods
    private CcddJTableHandler dataFieldTable;
    private UndoableCellSelection cellSelect;
    private final Border emptyBorder;

    // Array of data field owner names that aren't structure tables
    private List<String> nonStructureTableNames;

    // Table column names
    private String[] columnNames;

    // List of columns representing boolean data fields
    private List<Integer> checkBoxColumns;

    // Flag that indicates if any of the tables with data fields to display are children of another
    // table, and therefore have a structure path
    private boolean isPath;

    // Table instance model data. Committed copy is the table information as it exists in the
    // database and is used to determine what changes have been made to the table since the
    // previous database update
    private Object[][] committedData;

    // List of data field content changes to process
    private final List<String[]> fieldModifications;

    // List of data field deletions to process
    private final List<String[]> fieldDeletions;

    // Cell selection container
    private CellSelectionHandler selectedCells;

    // Row filter, used to show/hide event types
    private RowFilter<TableModel, Object> rowFilter;

    // Data field display filter flags
    private boolean isProjectFilter;
    private boolean isTableFilter;
    private boolean isGroupFilter;
    private boolean isTypeFilter;

    // Dialog title
    private static final String DIALOG_TITLE = "Show/Edit Data Fields";

    /**********************************************************************************************
     * Data field table editor dialog class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddFieldTableEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();

        selectDlg = null;

        // Initialize the list of table content changes and deletions
        fieldModifications = new ArrayList<String[]>();
        fieldDeletions = new ArrayList<String[]>();

        // Create an empty border to surround the components
        emptyBorder = BorderFactory.createEmptyBorder();

        // Set the initial filter selection states
        isProjectFilter = true;
        isTableFilter = true;
        isGroupFilter = true;
        isTypeFilter = true;

        // Allow the user to select the data fields to display in the table
        selectDataFields();
    }

    /**********************************************************************************************
     * Get a reference to the data field table editor's table
     *
     * @return Reference to the data field table editor table
     *********************************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return dataFieldTable;
    }

    /**********************************************************************************************
     * Build the list of non-structure table names
     *********************************************************************************************/
    protected void buildNonStructureTableList()
    {
        // Create a list to contain all non-structure table names
        nonStructureTableNames = new ArrayList<String>();

        // Step through each data table comment. The table comment contains the table's visible
        // name and type
        for (String[] tableComment : dbTable.queryDataTableComments(CcddFieldTableEditorDialog.this))
        {
            // Check if the table doesn't represent a structure
            if (!tableTypeHandler.getTypeDefinition(tableComment[TableCommentIndex.TYPE.ordinal()]).isStructure())
            {
                // Add all table name to the list of non-structure tables
                nonStructureTableNames.add(tableComment[TableCommentIndex.NAME.ordinal()]);
            }
        }
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of database table changes
     *
     * @param commandError
     *            false if the database commands successfully completed; true
     *********************************************************************************************/
    protected void doDataFieldUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Clear the cells selected for deletion
            selectedCells.clear();

            // Update the field handler information from the field definitions in the database
            fieldHandler.buildFieldInformation(CcddFieldTableEditorDialog.this);

            // Load the data field information into the data field editor table
            dataFieldTable.loadAndFormatData();

            // Step through the open editor dialogs
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Step through each individual editor in this editor dialog
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    List<String> redrawnTables = new ArrayList<String>();
                    TableInformation tableInfo = editor.getTableInformation();

                    // Step through the data field deletions
                    for (String[] del : fieldDeletions)
                    {
                        // Check if the table names and paths match, and that this table hasn't
                        // already had a deletion applied
                        if (del[0].equals(tableInfo.getTablePath())
                            && !redrawnTables.contains(del[0]))
                        {
                            // Add the table's name and path to the list of updated tables. This is
                            // used to prevent updating the data fields for a table multiple times
                            redrawnTables.add(del[0]);

                            // Update the field information in the current and committed lists so
                            // that this value change is ignored when updating or closing the table
                            editor.updateTableFieldInformationFromHandler();

                            // Rebuild the table's editor panel which contains the data fields
                            editor.createDataFieldPanel(false,
                                                        editor.getCommittedTableInformation().getFieldInformation());
                        }
                    }

                    // Step through the data field value modifications
                    for (String[] mod : fieldModifications)
                    {
                        // Check if the table names and paths match and that this table hasn't
                        // already been updated by having a deletion applied
                        if (mod[0].equals(tableInfo.getTablePath())
                            && !redrawnTables.contains(mod[0]))
                        {
                            // Update the field information in the current and committed lists so
                            // that this value change is ignored when updating or closing the table
                            editor.updateTableFieldInformationFromHandler();

                            // Rebuild the table's editor panel which contains the data fields
                            editor.createDataFieldPanel(false,
                                                        editor.getCommittedTableInformation().getFieldInformation());
                        }
                    }
                }
            }

            // Clear the undo/redo cell edits stack
            dataFieldTable.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Display a dialog for the user to select the data fields to display in the editor table. This
     * is executed in a separate thread since it can take a noticeable amount time to complete, and
     * by using a separate thread the GUI is allowed to continue to update. The GUI menu commands,
     * however, are disabled until the telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void selectDataFields()
    {
        // Build the data field selection dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel selectPnl = new JPanel(new GridBagLayout());

            /**************************************************************************************
             * Build the data field selection dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                1,
                                                                1,
                                                                1,
                                                                0.0,
                                                                0.0,
                                                                GridBagConstraints.LINE_START,
                                                                GridBagConstraints.BOTH,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                           ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                           0,
                                                                           0),
                                                                0,
                                                                0);

                // Build the list of non-structure tables
                buildNonStructureTableList();

                // Create a selection dialog, a panel for the table selection tree, and a panel to
                // contain a check box for each unique data field name
                selectDlg = new CcddDialogHandler()
                {
                    /******************************************************************************
                     * Verify input fields
                     *
                     * @return true if the dialog input is valid
                     *****************************************************************************/
                    @Override
                    protected boolean verifySelection()
                    {
                        // Assume the dialog input is valid
                        boolean isValid = true;

                        // Check if no data field check box is selected
                        if (selectDlg.getCheckBoxSelected().length == 0)
                        {
                            // Inform the user that a field must be selected
                            new CcddDialogHandler().showMessageDialog(this,
                                                                      "<html><b>Must select at least one data field",
                                                                      "No Data Field Selected",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                            isValid = false;
                        }

                        return isValid;
                    }
                };

                JPanel fieldPnl = new JPanel(new GridBagLayout());
                selectPnl.setBorder(emptyBorder);
                fieldPnl.setBorder(emptyBorder);

                // Create a panel containing a grid of check boxes representing the data fields
                // from which to choose
                if (selectDlg.addCheckBoxes(null,
                                            getDataFieldNames(),
                                            null,
                                            "Select data fields to display/edit",
                                            false,
                                            fieldPnl))
                {
                    // Check if more than one data field name check box exists
                    if (selectDlg.getCheckBoxes().length > 1)
                    {
                        // Create a Select All check box
                        final JCheckBox selectAllCb = new JCheckBox("Select all data fields",
                                                                    false);
                        selectAllCb.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                        selectAllCb.setBorder(emptyBorder);

                        // Create a listener for changes to the Select All check box selection
                        // status
                        selectAllCb.addActionListener(new ActionListener()
                        {
                            /**********************************************************************
                             * Handle a change to the Select All check box selection status
                             *********************************************************************/
                            @Override
                            public void actionPerformed(ActionEvent ae)
                            {
                                // Step through each data field name check box
                                for (JCheckBox fieldCb : selectDlg.getCheckBoxes())
                                {
                                    // Set the check box selection status to match the Select All
                                    // check box selection status
                                    fieldCb.setSelected(selectAllCb.isSelected());
                                }
                            }
                        });

                        // Add the Select All checkbox to the field name panel
                        gbc.gridy++;
                        fieldPnl.add(selectAllCb, gbc);

                        // Step through each data field name check box
                        for (JCheckBox columnCb : selectDlg.getCheckBoxes())
                        {
                            // Create a listener for changes to the data field name check box
                            // selection status
                            columnCb.addActionListener(new ActionListener()
                            {
                                /******************************************************************
                                 * Handle a change to the data field name check box selection
                                 * status
                                 *****************************************************************/
                                @Override
                                public void actionPerformed(ActionEvent ae)
                                {
                                    int columnCount = 0;

                                    // Step through each data field name check box
                                    for (int index = 0; index < selectDlg.getCheckBoxes().length; index++)
                                    {
                                        // Check if the check box is selected
                                        if (selectDlg.getCheckBoxes()[index].isSelected())
                                        {
                                            // Increment the counter to track the number of
                                            // selected data field name check boxes
                                            columnCount++;
                                        }
                                    }

                                    // Set the Select All check box status based on if all the data
                                    // field name check boxes are selected
                                    selectAllCb.setSelected(columnCount == selectDlg.getCheckBoxes().length);
                                }
                            });
                        }
                    }

                    // Check if data fields are selected already (i.e., this method is called via
                    // the Select button)
                    if (columnNames != null)
                    {
                        // Step through the list of check boxes representing the data fields
                        for (JCheckBox cb : selectDlg.getCheckBoxes())
                        {
                            // Select the data field check box if the field was displayed when the
                            // Select button was pressed
                            cb.setSelected(Arrays.asList(columnNames).contains(cb.getText()));
                        }
                    }

                    // Add the field panel to the selection panel
                    gbc.insets.top = 0;
                    gbc.insets.left = 0;
                    gbc.weighty = 0.0;
                    gbc.gridy = 0;
                    selectPnl.add(fieldPnl, gbc);

                    // Build the table tree showing both table prototypes and table instances;
                    // i.e., parent tables with their child tables (i.e., parents with children)
                    tableTree = new CcddTableTreeHandler(ccddMain,
                                                         new CcddGroupHandler(ccddMain,
                                                                              null,
                                                                              CcddFieldTableEditorDialog.this),
                                                         TableTreeType.TABLES,
                                                         true,
                                                         false,
                                                         CcddFieldTableEditorDialog.this);

                    // Add the tree to the selection panel
                    gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing();
                    gbc.weightx = 1.0;
                    gbc.weighty = 1.0;
                    gbc.gridx++;
                    selectPnl.add(tableTree.createTreePanel("Tables",
                                                            TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                                                            false,
                                                            ccddMain.getMainFrame()),
                                  gbc);
                }
                // No data field exists to choose
                else
                {
                    // Inform the user that no data field is defined
                    new CcddDialogHandler().showMessageDialog((isVisible()
                                                                           ? CcddFieldTableEditorDialog.this
                                                                           : ccddMain.getMainFrame()),
                                                              "<html><b>No data field exists",
                                                              "No Data Field",
                                                              JOptionPane.WARNING_MESSAGE,
                                                              DialogOption.OK_OPTION);
                }
            }

            /**************************************************************************************
             * Data field selection dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the data field selection dialog if any fields exist
                if (!fieldHandler.getFieldInformation().isEmpty()
                    && selectDlg.showOptionsDialog((isVisible()
                                                                ? CcddFieldTableEditorDialog.this
                                                                : ccddMain.getMainFrame()),
                                                   selectPnl,
                                                   "Select Data Field(s)",
                                                   DialogOption.OK_CANCEL_OPTION,
                                                   true) == OK_BUTTON)
                {
                    // Create a list for the column names. Add the default columns (table name and
                    // path)
                    List<String> columnNamesList = new ArrayList<String>();
                    columnNamesList.add(FieldTableEditorColumnInfo.OWNER.getColumnName());
                    columnNamesList.add(FieldTableEditorColumnInfo.PATH.getColumnName());

                    // Step through each selected data field name
                    for (String name : selectDlg.getCheckBoxSelected())
                    {
                        // Add the data field name to the column name list
                        columnNamesList.add(name);
                    }

                    // Convert the list of column names to an array
                    columnNames = columnNamesList.toArray(new String[0]);

                    // Create the cell selection container
                    selectedCells = new CellSelectionHandler();

                    // Clear any removal selections
                    selectedCells.clear();

                    // Check if this is the initial display of the selection dialog
                    if (dataFieldTable == null)
                    {
                        // Create the data field editor dialog
                        displayDataFieldTableEditor();
                    }
                    // The selection dialog is spawned from the data field table editor
                    else
                    {
                        // Reload the data field table
                        dataFieldTable.loadAndFormatData();

                        // Check if the field table editor dialog is already open
                        if (CcddFieldTableEditorDialog.this.isVisible())
                        {
                            // Reposition the field table editor dialog
                            positionFieldEditorDialog();
                        }
                    }
                }
            }
        });
    }

    /**********************************************************************************************
     * Create the data field table editor dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void displayDataFieldTableEditor()
    {
        // Build the data field editor table dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel dialogPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the data field table editor dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                // Set the initial layout manager characteristics
                GridBagConstraints gbc = new GridBagConstraints(0,
                                                                0,
                                                                1,
                                                                1,
                                                                1.0,
                                                                1.0,
                                                                GridBagConstraints.LINE_START,
                                                                GridBagConstraints.HORIZONTAL,
                                                                new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                           0,
                                                                           0,
                                                                           0),
                                                                ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                0);

                // Define the panel to contain the table
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createDataFieldTableEditorTable());

                // Add the table to the dialog
                gbc.gridx = 0;
                gbc.gridy++;
                gbc.gridwidth = GridBagConstraints.REMAINDER;
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weighty = 1.0;
                dialogPnl.add(tablePnl, gbc);

                // Add the field display filter label and a filter check box for each field owner
                // type
                JLabel fieldFilterLbl = new JLabel("Show fields belonging to:");
                fieldFilterLbl.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                fieldFilterLbl.setBorder(emptyBorder);
                gbc.gridwidth = 1;
                gbc.weightx = 0.0;
                gbc.weighty = 0.0;
                gbc.gridy++;
                dialogPnl.add(fieldFilterLbl, gbc);

                final JCheckBox projectFilterCbx = new JCheckBox("Project", isProjectFilter);
                projectFilterCbx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                projectFilterCbx.setBorder(emptyBorder);
                gbc.gridx++;
                dialogPnl.add(projectFilterCbx, gbc);

                final JCheckBox tableFilterCbx = new JCheckBox("Tables", isTableFilter);
                tableFilterCbx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                tableFilterCbx.setBorder(emptyBorder);
                gbc.gridx++;
                dialogPnl.add(tableFilterCbx, gbc);

                final JCheckBox groupFilterCbx = new JCheckBox("Groups", isGroupFilter);
                groupFilterCbx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                groupFilterCbx.setBorder(emptyBorder);
                gbc.gridx++;
                dialogPnl.add(groupFilterCbx, gbc);

                final JCheckBox typeFilterCbx = new JCheckBox("Table types", isTypeFilter);
                typeFilterCbx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                typeFilterCbx.setBorder(emptyBorder);
                gbc.gridx++;
                dialogPnl.add(typeFilterCbx, gbc);

                // Create a row filter for displaying the fields based on selected filter
                rowFilter = new RowFilter<TableModel, Object>()
                {
                    /******************************************************************************
                     * Override method that determines if a row should be displayed
                     *****************************************************************************/
                    @Override
                    public boolean include(Entry<? extends TableModel, ? extends Object> owner)
                    {
                        boolean isFilter = true;

                        // Get the data field owner's name
                        String ownerName = highlightFieldOwner(owner.getValue(FieldTableEditorColumnInfo.OWNER.ordinal()).toString(),
                                                               false);

                        // Check if this field belongs to the project
                        if (ownerName.startsWith(CcddFieldHandler.getFieldProjectName()))
                        {
                            // Show this row if the project filter check box is selected
                            isFilter = projectFilterCbx.isSelected();
                        }
                        // Check if this field belongs to a group
                        else if (ownerName.startsWith(CcddFieldHandler.getFieldGroupName("")))
                        {
                            // Show this row if the group filter check box is selected
                            isFilter = groupFilterCbx.isSelected();
                        }
                        // Check if this field belongs to a table type
                        else if (ownerName.startsWith(CcddFieldHandler.getFieldTypeName("")))
                        {
                            // Show this row if the table type filter check box is selected
                            isFilter = typeFilterCbx.isSelected();
                        }
                        // The field belongs to a table
                        else
                        {
                            // Show this row if the table filter check box is selected
                            isFilter = tableFilterCbx.isSelected();
                        }

                        return isFilter;
                    }
                };

                // Create a listener for check box selection changes
                ActionListener filterListener = new ActionListener()
                {
                    /******************************************************************************
                     * Handle check box selection changes
                     *****************************************************************************/
                    @Override
                    public void actionPerformed(ActionEvent ae)
                    {
                        // Set the table's row sorter based on whether or not any rows are visible
                        dataFieldTable.setRowSorter(null);
                        dataFieldTable.setTableSortable();

                        // Issue a table change event so the rows are filtered
                        ((DefaultTableModel) dataFieldTable.getModel()).fireTableDataChanged();
                        ((DefaultTableModel) dataFieldTable.getModel()).fireTableStructureChanged();
                    }
                };

                // Add the listener to the filter check boxes
                projectFilterCbx.addActionListener(filterListener);
                tableFilterCbx.addActionListener(filterListener);
                groupFilterCbx.addActionListener(filterListener);
                typeFilterCbx.addActionListener(filterListener);

                // Create a table opener for the Open tables command
                final TableOpener opener = new TableOpener()
                {
                    /******************************************************************************
                     * Include the structure path, is applicable, with the table name
                     *****************************************************************************/
                    @Override
                    protected String cleanUpTableName(String tableName, int row)
                    {
                        // Check if the data field for this row belongs to a table (versus the
                        // project, a group, or a table type)
                        if (!ownerIsNotTable(tableName))
                        {
                            // Get the structure path for this row
                            String path = dataFieldTable.getModel()
                                                        .getValueAt(row,
                                                                    FieldTableEditorColumnInfo.PATH.ordinal())
                                                        .toString();

                            // Add the table path to the list
                            tableName = getOwnerWithPath(tableName, path);
                        }

                        return tableName;
                    }
                };

                // Define the buttons for the lower panel: Select data fields button
                JButton btnSelect = CcddButtonPanelHandler.createButton("Select",
                                                                        OK_ICON,
                                                                        KeyEvent.VK_L,
                                                                        "Select new data fields");

                // Add a listener for the Select button
                btnSelect.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Select the data fields and update the data field editor table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Confirm discarding pending changes if any exist
                        if ((!isFieldTableChanged()
                             || new CcddDialogHandler().showMessageDialog(CcddFieldTableEditorDialog.this,
                                                                          "<html><b>Discard changes?",
                                                                          "Discard Changes",
                                                                          JOptionPane.QUESTION_MESSAGE,
                                                                          DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
                        {
                            // Store the current filter selections
                            isProjectFilter = projectFilterCbx.isSelected();
                            isTableFilter = tableFilterCbx.isSelected();
                            isGroupFilter = groupFilterCbx.isSelected();
                            isTypeFilter = typeFilterCbx.isSelected();

                            // Allow the user to select the data fields to display
                            selectDataFields();
                        }
                    }
                });

                // Delete data fields button
                JButton btnRemove = CcddButtonPanelHandler.createButton("Remove",
                                                                        DELETE_ICON,
                                                                        KeyEvent.VK_R,
                                                                        "Remove the selected data field(s) from their table(s)");

                // Add a listener for the Remove button
                btnRemove.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Toggle the removal state of the selected data field(s)
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        toggleRemoveFields();
                    }
                });

                // Open tables button
                JButton btnOpen = CcddButtonPanelHandler.createButton("Open",
                                                                      TABLE_ICON,
                                                                      KeyEvent.VK_O,
                                                                      "Open the table(s) associated with the selected data field(s)");

                // Add a listener for the Open button
                btnOpen.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Open the table(s) associated with the selected data field(s)
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        opener.openTables(dataFieldTable,
                                          FieldTableEditorColumnInfo.OWNER.ordinal());
                    }
                });

                // Print data field editor table button
                JButton btnPrint = CcddButtonPanelHandler.createButton("Print",
                                                                       PRINT_ICON,
                                                                       KeyEvent.VK_P,
                                                                       "Print the data field editor table");

                // Add a listener for the Print button
                btnPrint.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Print the data field editor table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataFieldTable.printTable("Data Field Contents",
                                                  null,
                                                  CcddFieldTableEditorDialog.this,
                                                  PageFormat.LANDSCAPE);
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit action");

                // Create a listener for the Undo command
                btnUndo.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Undo the last cell edit
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataFieldTable.getUndoManager().undo();
                    }
                });

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit action");

                // Create a listener for the Redo command
                btnRedo.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Redo the last cell edit that was undone
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataFieldTable.getUndoManager().redo();
                    }
                });

                // Store data field values button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store data field changes");
                btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Add a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Store changes to the data field values
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's content has changed
                        // and the user confirms the action
                        if (isFieldTableChanged()
                            && new CcddDialogHandler().showMessageDialog(CcddFieldTableEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Build the update lists
                            buildUpdates();

                            // Store the changes to the data fields in the database
                            dbTable.modifyDataFieldValues(fieldModifications,
                                                          fieldDeletions,
                                                          CcddFieldTableEditorDialog.this);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the data field editor dialog");

                // Add a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener(dataFieldTable)
                {
                    /******************************************************************************
                     * Close the data field editor dialog
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        windowCloseButtonAction();
                    }
                });

                // Add the buttons to the panel
                buttonPnl.add(btnSelect);
                buttonPnl.add(btnOpen);
                buttonPnl.add(btnUndo);
                buttonPnl.add(btnStore);
                buttonPnl.add(btnRemove);
                buttonPnl.add(btnPrint);
                buttonPnl.add(btnRedo);
                buttonPnl.add(btnClose);

                // Distribute the buttons across two rows
                setButtonRows(2);
            }

            /**************************************************************************************
             * Data field table editor dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the data field table editor dialog
                createFrame(ccddMain.getMainFrame(),
                            dialogPnl,
                            buttonPnl,
                            btnClose,
                            DIALOG_TITLE,
                            null);

                // Reposition the field table editor dialog
                positionFieldEditorDialog();
            }
        });
    }

    /**********************************************************************************************
     * Handle the frame close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the editor table is validated and that
        // the table has no uncommitted changes. If changes exist then confirm discarding the
        // changes
        if (dataFieldTable.isLastCellValid()
            && (!isFieldTableChanged()
                || new CcddDialogHandler().showMessageDialog(CcddFieldTableEditorDialog.this,
                                                             "<html><b>Discard changes?",
                                                             "Discard Changes",
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
        {
            // Close the dialog
            closeFrame();
        }
    }

    /**********************************************************************************************
     * Reposition the field table editor dialog relative to the location of the (now invisible)
     * field selection dialog
     *********************************************************************************************/
    private void positionFieldEditorDialog()
    {
        // Get the location of the field selection dialog
        Point p1 = selectDlg.getLocation();

        // Get the sizes of the editor and selection dialogs
        Dimension d1 = selectDlg.getSize();
        Dimension d2 = CcddFieldTableEditorDialog.this.getSize();

        // Move the editor dialog so that it's centered over the selection dialog's last location
        CcddFieldTableEditorDialog.this.setLocation(new Point(p1.x + d1.width / 2 - d2.width / 2,
                                                              p1.y + d1.height / 2 - d2.height / 2));
    }

    /**********************************************************************************************
     * Toggle the removal state of the selected data field cells in the editor table
     *********************************************************************************************/
    private void toggleRemoveFields()
    {
        // Step through each row in the table
        for (int row = 0; row < dataFieldTable.getRowCount(); row++)
        {
            // Step through each column in the table
            for (int column = 0; column < dataFieldTable.getColumnCount(); column++)
            {
                // Get the column index in model coordinates
                int columnMod = dataFieldTable.convertColumnIndexToModel(column);

                // Check if this is not the table name or path column and if the cell at these
                // coordinates is selected
                if (columnMod != FieldTableEditorColumnInfo.OWNER.ordinal()
                    && columnMod != FieldTableEditorColumnInfo.PATH.ordinal()
                    && dataFieldTable.isCellSelected(row, column))
                {
                    // Add (if selecting) or remove (if deselecting) the cell from the selection
                    // list
                    cellSelect.toggleCellSelection(row, column);
                }
            }
        }

        // End the editing sequence
        dataFieldTable.getUndoManager().endEditSequence();

        // Clear the cell selection so that the remove highlight is visible
        dataFieldTable.clearSelection();
    }

    /**********************************************************************************************
     * Create the data field table editor table
     *
     * @return Reference to the scroll pane in which the table is placed
     *********************************************************************************************/
    private JScrollPane createDataFieldTableEditorTable()
    {
        // Create the table to display the structure tables and their corresponding user-selected
        // data fields
        dataFieldTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow resizing of the any column unless it displays a check box
             *************************************************************************************/
            @Override
            protected boolean isColumnResizable(int column)
            {
                return !isColumnBoolean(column);
            }

            /**************************************************************************************
             * Allow multiple line display in all columns except those displaying check boxes
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return !isColumnBoolean(column);
            }

            /**************************************************************************************
             * Allow HTML-formatted text in the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHTML(int column)
            {
                return column == FieldTableEditorColumnInfo.OWNER.ordinal()
                       || column == FieldTableEditorColumnInfo.PATH.ordinal();
            }

            /**************************************************************************************
             * Hide the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return !isPath && column == FieldTableEditorColumnInfo.PATH.ordinal();
            }

            /**************************************************************************************
             * Display the specified column(s) as check boxes
             *************************************************************************************/
            @Override
            protected boolean isColumnBoolean(int column)
            {
                return checkBoxColumns.contains(column);
            }

            /**************************************************************************************
             * Allow editing of the table cells in the specified columns only
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                // Convert the coordinates from view to model
                row = convertRowIndexToModel(row);
                column = convertColumnIndexToModel(column);

                // Return true if this is not the owner or path column, or if the table does not
                // have the field specified by the column
                return column != FieldTableEditorColumnInfo.OWNER.ordinal()
                       && column != FieldTableEditorColumnInfo.PATH.ordinal()
                       && fieldHandler.getFieldInformationByName(getOwnerWithPath(getModel().getValueAt(row,
                                                                                                        FieldTableEditorColumnInfo.OWNER.ordinal())
                                                                                            .toString(),
                                                                                  getModel().getValueAt(row,
                                                                                                        FieldTableEditorColumnInfo.PATH.ordinal())
                                                                                            .toString()),
                                                                 columnNames[column]) != null;
            }

            /**************************************************************************************
             * Allow pasting data into the data field cells
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                return isCellEditable(convertRowIndexToView(row), convertColumnIndexToView(column));
            }

            /**************************************************************************************
             * Validate changes to the data field value cells; e.g., verify cell content and, if
             * found invalid, revert to the original value
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param row
             *            table model row number
             *
             * @param column
             *            table model column number
             *
             * @param oldValue
             *            original cell contents
             *
             * @param newValue
             *            new cell contents
             *
             * @param showMessage
             *            unused
             *
             * @param isMultiple
             *            unused
             *
             * @return Value of ShowMessage
             ************************************************************************************/
            @Override
            protected Boolean validateCellContent(List<Object[]> tableData,
                                                  int row,
                                                  int column,
                                                  Object oldValue,
                                                  Object newValue,
                                                  Boolean showMessage,
                                                  boolean isMultiple)
            {
                // Reset the flag that indicates the last edited cell's content is invalid
                setLastCellValid(true);

                // Create a string version of the new value
                String newValueS = newValue.toString();

                // Check that the new cell value isn't blank
                if (!newValueS.isEmpty())
                {
                    // Get the owner name, with path if applicable
                    String ownerAndPath = getOwnerWithPath(tableData.get(row)[FieldTableEditorColumnInfo.OWNER.ordinal()].toString(),
                                                           tableData.get(row)[FieldTableEditorColumnInfo.PATH.ordinal()].toString());

                    // Get the reference to the data field
                    FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerAndPath,
                                                                                        columnNames[column]);

                    // Check that the data field was found
                    if (fieldInfo != null)
                    {
                        // Check if the value entered matches the pattern selected by the user for
                        // this data field
                        if (fieldInfo.getInputType().getInputMatch().isEmpty()
                            || newValueS.matches(fieldInfo.getInputType().getInputMatch()))
                        {
                            // Check is the input type isn't a boolean. Booleans are displayed as a
                            // check box in the table
                            if (fieldInfo.getInputType().getInputFormat() != InputTypeFormat.BOOLEAN)
                            {
                                // Store the new value in the table data array after formatting the
                                // cell value per its input type. This is needed primarily to clean
                                // up numeric formatting
                                newValueS = fieldInfo.getInputType().formatInput(newValueS);
                                tableData.get(row)[column] = newValueS;
                            }
                        }
                        // The value doesn't match the pattern for this data field
                        else
                        {
                            // Set the flag that indicates the last edited cell's content is
                            // invalid
                            setLastCellValid(false);

                            // Inform the user that the data field contents is invalid
                            new CcddDialogHandler().showMessageDialog(CcddFieldTableEditorDialog.this,
                                                                      "<html><b>Invalid characters in field '</b>"
                                                                                                       + fieldInfo.getFieldName()
                                                                                                       + "<b>'; characters consistent with input type '</b>"
                                                                                                       + fieldInfo.getInputType().getInputName()
                                                                                                       + "<b>' expected",
                                                                      "Invalid "
                                                                                                                          + fieldInfo.getInputType().getInputName(),
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);

                            // Restore the cell contents to its original value
                            tableData.get(row)[column] = oldValue;
                            getUndoManager().undoRemoveEdit();
                        }
                    }
                }

                return showMessage;
            }

            /**************************************************************************************
             * Load the data field data into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Get the default column names and tool tip text for the data field editor table
                String[] toolTips = new String[columnNames.length];
                toolTips[FieldTableEditorColumnInfo.OWNER.ordinal()] = FieldTableEditorColumnInfo.OWNER.getToolTip();
                toolTips[FieldTableEditorColumnInfo.PATH.ordinal()] = FieldTableEditorColumnInfo.PATH.getToolTip();

                // Get the owners, paths, data field values values, and check box columns based on
                // the specified data fields
                committedData = getDataFieldsToDisplay();

                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(committedData,
                                            columnNames,
                                            null,
                                            toolTips,
                                            true,
                                            true,
                                            true);

                // Set up editors for the columns that display combo boxes (i.e., for fields with
                // selection item lists). This is done after the table is loaded since the number
                // of columns and their field contents change based on the user's field selections
                setSelectionCellEditors();
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method in order to show/hide the data fields based on
             * the selected field filters
             *************************************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Get the table's row sorter and add the event type filter
                TableRowSorter<?> sorter = (TableRowSorter<?>) getRowSorter();

                // Check if the table has a sorter (i.e., has at least one visible row), that the
                // filter hasn't been set, and that there is a field owner row filter
                if (sorter != null && sorter.getRowFilter() != rowFilter && rowFilter != null)
                {
                    // Apply the row filter that shows/hides the event types
                    sorter.setRowFilter(rowFilter);
                }
            }

            /**************************************************************************************
             * Override prepareRenderer to allow adjusting the background colors of table cells
             *************************************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
            {
                JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

                // Get the column index in model coordinates
                int columnModel = convertColumnIndexToModel(column);

                // Check if the cell doesn't have the focus or is selected (the focus and selection
                // highlight colors override the invalid highlight color) and if this is a column
                // for a data field
                if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                    && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor()
                    && columnModel != FieldTableEditorColumnInfo.OWNER.ordinal()
                    && columnModel != FieldTableEditorColumnInfo.PATH.ordinal())
                {
                    // Get the row index in model coordinates
                    int rowModel = convertRowIndexToModel(row);

                    // Get a reference to the table model to shorten subsequent calls
                    TableModel tableModel = getModel();

                    // Get the contents of the owner and path columns
                    String ownerName = tableModel.getValueAt(rowModel,
                                                             FieldTableEditorColumnInfo.OWNER.ordinal())
                                                 .toString();
                    String pathValue = tableModel.getValueAt(rowModel,
                                                             FieldTableEditorColumnInfo.PATH.ordinal())
                                                 .toString();

                    // Get the owner, including the path (if a child structure table), with any
                    // highlighting removed (this is the field owner as stored in the project's
                    // data field table)
                    String ownerPath = getOwnerWithPath(ownerName, pathValue);

                    // Check if this is the structure path column
                    if (columnModel == FieldTableEditorColumnInfo.PATH.ordinal())
                    {
                        // Check if the cell is blank and that the owner isn't a structure table
                        if (pathValue.isEmpty()
                            && (nonStructureTableNames.contains(ownerPath)
                                || ownerIsNotTable(ownerName)))
                        {
                            // Set the cell's background color to indicate the structure path isn't
                            // applicable for this table
                            comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                        }
                    }
                    // Check if this table has the data field identified by the column
                    else if (fieldHandler.getFieldInformationByName(ownerPath,
                                                                    columnNames[columnModel]) != null)
                    {
                        // Check if the cell is a data field selected for removal
                        if (selectedCells.contains(row, column))
                        {
                            // Change the cell's colors to indicate the data field represented by
                            // the cell is selected for removal
                            comp.setForeground(Color.GRAY);
                            comp.setBackground(Color.RED);
                        }
                        // The cell isn't selected for removal. Check if the cell doesn't represent
                        // a boolean value (these are not compared for duplicate values)
                        else if (!checkBoxColumns.contains(columnModel))
                        {
                            // Get the input type for this data field
                            InputType inputType = fieldHandler.getFieldInformationByName(ownerPath,
                                                                                         columnNames[columnModel])
                                                              .getInputType();

                            // Get the text in the cell, formatted per its input type, but without
                            // preserving the leading zeroes for hexadecimal values
                            String value = inputType.formatInput(tableModel.getValueAt(rowModel,
                                                                                       columnModel)
                                                                           .toString(),
                                                                 false);

                            // Check if the value isn't blank
                            if (!value.isEmpty())
                            {
                                // Check if the field's input type represents a message name & ID
                                if (inputType.equals(ccddMain.getInputTypeHandler().getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID)))
                                {
                                    // Separate the message name and ID
                                    String[] nameID = CcddMessageIDHandler.getMessageNameAndID(value);

                                    // Step through each row in the table
                                    for (int checkRow = 0; checkRow < tableModel.getRowCount(); checkRow++)
                                    {
                                        // Check if this isn't the same row as the one being
                                        // updated
                                        if (rowModel != checkRow)
                                        {
                                            // Separate the message name and ID
                                            String[] chkNameID = CcddMessageIDHandler.getMessageNameAndID(tableModel.getValueAt(checkRow,
                                                                                                                                columnModel)
                                                                                                                    .toString());

                                            // Check if the message names and/or IDs match (blanks
                                            // are ignored)
                                            if ((!nameID[0].isEmpty() && nameID[0].equals(chkNameID[0]))
                                                || (!nameID[1].isEmpty() && nameID[1].equals(chkNameID[1])))
                                            {
                                                // Change the cell's background color to indicate
                                                // it has the same value as another cell in the
                                                // same column
                                                comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                                                break;
                                            }
                                        }
                                    }
                                }
                                // The field is not for a message name & ID
                                else
                                {
                                    // Step through each row in the table
                                    for (int checkRow = 0; checkRow < tableModel.getRowCount(); checkRow++)
                                    {
                                        // Check if this isn't the same row as the one being
                                        // updated and if the text matches that in another row of
                                        // the same column
                                        if (rowModel != checkRow
                                            && inputType.formatInput(tableModel.getValueAt(checkRow,
                                                                                           columnModel)
                                                                               .toString(),
                                                                     false)
                                                        .equals(value))
                                        {
                                            // Change the cell's background color to indicate it
                                            // has the same value as another cell in the same
                                            // column
                                            comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // The table indicated by this row does not have a data field as identified by
                    // the column
                    else
                    {
                        // Set the cell's background color to indicate the data field doesn't exist
                        // for this table
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                    }
                }

                return comp;
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether any unstored changes exist
                setTitle(DIALOG_TITLE
                         + (isFieldTableChanged()
                                                  ? CHANGE_INDICATOR
                                                  : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(dataFieldTable);

        // Set up the field table parameters
        dataFieldTable.setFixedCharacteristics(scrollPane,
                                               false,
                                               ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                               TableSelectionMode.SELECT_BY_CELL,
                                               true,
                                               ModifiableColorInfo.TABLE_BACK.getColor(),
                                               true,
                                               true,
                                               ModifiableFontInfo.OTHER_TABLE_CELL.getFont(),
                                               true);

        // Set the reference to the cell selection container in the undo handler, then create a
        // cell selection object
        dataFieldTable.getUndoHandler().setSelectedCells(selectedCells);
        cellSelect = dataFieldTable.getUndoHandler().new UndoableCellSelection();

        return scrollPane;
    }

    /**********************************************************************************************
     * Build the data field array
     *
     * @return Array containing the data field owner names and corresponding user-selected data
     *         field values
     *********************************************************************************************/
    private Object[][] getDataFieldsToDisplay()
    {
        isPath = false;
        checkBoxColumns = new ArrayList<Integer>();
        List<Object[]> ownerDataFields = new ArrayList<Object[]>();
        List<String[]> ownersInTable = new ArrayList<String[]>();

        // Get the list of selected tables
        List<String> filterTables = tableTree.getSelectedTablesWithChildren();

        // Add the ancestors of the selected tables to the list of filter tables
        tableTree.addTableAncestors(filterTables, false);

        // Get the field information for all data fields
        List<FieldInformation> fieldInformation = fieldHandler.getFieldInformationCopy();

        // Sort the field information by owner name so that sequence order of the data field values
        // is based on the owners' alphabetical order
        Collections.sort(fieldInformation, new Comparator<FieldInformation>()
        {
            /**************************************************************************************
             * Compare the owner names of two field definitions, ignoring case
             *************************************************************************************/
            @Override
            public int compare(FieldInformation fld1, FieldInformation fld2)
            {
                return fld1.getOwnerName().compareToIgnoreCase(fld2.getOwnerName());
            }
        });

        // Step through each defined data field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Get the reference to the field information
            FieldInformation fieldInfo = fieldInformation.get(index);

            // Get the data field owner's name
            String ownerName = fieldInfo.getOwnerName();

            // Check if the field is in a table selected by the user (if no table is selected then
            // all tables are considered to match) and is currently applicable
            if ((filterTables.isEmpty() || filterTables.contains(ownerName))
                && fieldHandler.isFieldApplicable(ownerName,
                                                  fieldInfo.getApplicabilityType().getApplicabilityName(),
                                                  null))
            {
                String pathName = "";

                // Get the index of the last comma in the field table path & name
                int commaIndex = ownerName.lastIndexOf(",");

                // Check if a comma was found in the table path & name
                if (commaIndex != -1)
                {
                    // Extract the path name from the table path and name
                    pathName = ownerName.substring(0, commaIndex);

                    // Count the number of commas in the path name, which indicates the structure
                    // nest level
                    int depth = pathName.split(",").length;

                    // Set the indentation
                    String indent = "";

                    // Step through each nest level
                    for (int count = 0; count < depth; count++)
                    {
                        // Add spaces to the indentation. This aids in identifying the structure
                        // members
                        indent += "  ";
                    }

                    // Remove the path and leave only the table name
                    ownerName = indent + ownerName.substring(commaIndex + 1);

                    // Add spaces after any remaining commas in the path
                    pathName = pathName.replaceAll(",", ", ");
                }

                int dataFieldIndex = -1;

                // Step through each column
                for (int fieldIndex = 0; fieldIndex < columnNames.length; fieldIndex++)
                {
                    // Check if the column name matches the data field name
                    if (fieldInfo.getFieldName().equals(columnNames[fieldIndex]))
                    {
                        // Set the index to the matching data field column
                        dataFieldIndex = fieldIndex;

                        // Check if the data field input type is boolean and hasn't already been
                        // added to the list
                        if (fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN
                            && !checkBoxColumns.contains(fieldIndex))
                        {
                            // Store the column index in the check box column list
                            checkBoxColumns.add(fieldIndex);
                        }

                        break;
                    }
                }

                // Check if a target data field is present
                if (dataFieldIndex != -1)
                {
                    boolean isFound = false;
                    int row = 0;

                    // Step through the owners added to this point
                    for (String[] inTable : ownersInTable)
                    {
                        // Check if the owner name and path for the data field matches
                        if (ownerName.equals(inTable[0]) && pathName.equals(inTable[1]))
                        {
                            // Store the data field value in the existing list item and stop
                            // searching
                            ownerDataFields.get(row)[dataFieldIndex] = fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN
                                                                                                                                            ? Boolean.valueOf(fieldInfo.getValue())
                                                                                                                                            : fieldInfo.getValue();
                            isFound = true;
                            break;
                        }

                        row++;
                    }

                    // Check if the owner isn't already in the list
                    if (!isFound)
                    {
                        // Add the non-highlighted owner and path names to the list of owners
                        // already added to the table
                        ownersInTable.add(new String[] {ownerName, pathName});

                        // Create a new row for the owner
                        Object[] newTable = new Object[columnNames.length];
                        Arrays.fill(newTable, "");

                        // Check if the field owner isn't a table
                        if (ownerIsNotTable(ownerName))
                        {
                            // Highlight the field owner indicator
                            newTable[FieldTableEditorColumnInfo.OWNER.ordinal()] = highlightFieldOwner(ownerName, true);
                        }
                        // The field belongs to a data table
                        else
                        {
                            // Highlight the data type(s) in the table
                            newTable[FieldTableEditorColumnInfo.OWNER.ordinal()] = CcddUtilities.highlightDataType(ownerName);
                        }

                        // Insert the owner name, path, and the data field value into the new row
                        newTable[FieldTableEditorColumnInfo.PATH.ordinal()] = CcddUtilities.highlightDataType(pathName);
                        newTable[dataFieldIndex] = fieldInfo.getInputType().getInputFormat() == InputTypeFormat.BOOLEAN
                                                                                                                        ? Boolean.valueOf(fieldInfo.getValue())
                                                                                                                        : fieldInfo.getValue();

                        // Add the field row to the list
                        ownerDataFields.add(newTable);

                        // Check if this owner has a path (i.e., it's a structure table)
                        if (!pathName.isEmpty())
                        {
                            // Set the flag to indicate at least one of the owners has a path
                            isPath = true;
                        }
                    }
                }
            }
        }

        // Since all of the check box columns are now determined, step through each of them so that
        // any that have a blank value (i.e., a boolean cell in a row for which the field is not
        // applicable to the owner) can be set to false (a legal boolean value)
        for (int cbxCol : checkBoxColumns)
        {
            // Set through each row in the table
            for (Object[] ownerDataField : ownerDataFields)
            {
                // Check if the check box value is blank
                if (ownerDataField[cbxCol] == "")
                {
                    // Set the check box value to false
                    ownerDataField[cbxCol] = false;
                }
            }
        }

        return ownerDataFields.toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Set up combo box cell editors for the columns that display data fields with selection item
     * lists
     *********************************************************************************************/
    private void setSelectionCellEditors()
    {
        // Step through each table column, ignoring the owner and structure path columns
        for (int columnModel = 2; columnModel < dataFieldTable.getModel().getColumnCount(); columnModel++)
        {
            // Get the column's view index
            int column = dataFieldTable.convertColumnIndexToView(columnModel);

            // Step through each row in the table
            for (int row = 0; row < dataFieldTable.getRowCount(); row++)
            {
                // Get the field information based on the owner (the contents of the first column)
                // and the field name (the column name)
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(CcddUtilities.removeHTMLTags(dataFieldTable.getValueAt(row, 0)
                                                                                                                               .toString()),
                                                                                    columnNames[columnModel]);

                // Check if a field by the specified name exists for this owner, and if so, that
                // the field has a selection item list
                if (fieldInfo != null && fieldInfo.getInputType().getInputItems() != null)
                {
                    // Create a combo box for displaying selection lists
                    PaddedComboBox comboBox = new PaddedComboBox(dataFieldTable.getFont());

                    // Step through each item in the selection
                    for (String item : fieldInfo.getInputType().getInputItems())
                    {
                        // Add the selection item to the combo box list
                        comboBox.addItem(item);
                    }

                    // Enable item matching for the combo box
                    comboBox.enableItemMatching(dataFieldTable);

                    // Get the column reference for the selection column
                    TableColumn selectionColumn = dataFieldTable.getColumnModel().getColumn(column);

                    // Set the table column editor to the combo box
                    selectionColumn.setCellEditor(new ComboBoxCellEditor(comboBox));

                    // Stop searching since a valid owner was found for this column (the editor
                    // only needs to be set once to affect the entire column)
                    break;
                }
            }
        }
    }

    /**********************************************************************************************
     * Compare the current table data to the committed table data and create lists of the changed
     * values necessary to update the table data fields in the database to match the current values
     *********************************************************************************************/
    private void buildUpdates()
    {
        // TODO THIS MAY REQUIRE ALTERATIONS DUE TO INHERITANCE WHEN CHANGING TABLE TYPE FIELD
        // VALUES - THERE'S NO WAY TO SET (OR SEE) THE OVERWRITE BEHAVIOR IN THIS EDITOR. COULD (1)
        // DECIDE THAT CHANGES MADE HERE ARE NEVER PROPAGATED TO THE FIELDS BELONGING TO TABLES OF
        // THE CHANGED TYPE OR (2) NOT ALLOW CHANGES TO TABLE TYPE FIELD VALUES FROM THIS EDITOR.
        //
        // DID IMPLEMENT CHANGE SO THAT INHERITED FIELDS ARE DELETED IF THE DEFAULT IS DELETED.

        // Get the table data array
        Object[][] tableData = dataFieldTable.getTableData(true);

        // Remove existing changes, if any
        fieldModifications.clear();

        // Step through each row of the current data
        for (int row = 0; row < tableData.length; row++)
        {
            // Step through each row of the current data
            for (int column = 0; column < tableData[row].length; column++)
            {
                // Check that this isn't the field owner or path columns
                if (column != FieldTableEditorColumnInfo.OWNER.ordinal()
                    && column != FieldTableEditorColumnInfo.PATH.ordinal())
                {
                    // Get the field owner, with path if applicable
                    String ownerName = getOwnerWithPath(tableData[row][FieldTableEditorColumnInfo.OWNER.ordinal()].toString(),
                                                        tableData[row][FieldTableEditorColumnInfo.PATH.ordinal()].toString());

                    // Check if this field is selected for removal
                    if (selectedCells.contains(dataFieldTable.convertRowIndexToView(row),
                                               dataFieldTable.convertColumnIndexToView(column)))
                    {
                        // Add the field's removal information to the list
                        fieldDeletions.add(new String[] {ownerName,
                                                         dataFieldTable.getModel().getColumnName(column)});

                        // Check if the field belongs to a table type. If so, then all of the
                        // inherited versions of this field are removed as well
                        if (CcddFieldHandler.isTableTypeField(ownerName))
                        {
                            // Step through each table of this type
                            for (String tablePath : dbTable.getAllTablesOfType(ownerName.replace(TYPE_DATA_FIELD_IDENT,
                                                                                                 ""),
                                                                               null,
                                                                               CcddFieldTableEditorDialog.this))
                            {
                                // Add the inherited field's removal information to the list
                                fieldDeletions.add(new String[] {ownerName, tablePath});
                            }
                        }
                    }
                    // Check if the current and committed column values differ
                    else if (!tableData[row][column].equals(committedData[row][column]))
                    {
                        // Add the data field modification information to the list
                        fieldModifications.add(new String[] {ownerName,
                                                             columnNames[column],
                                                             tableData[row][column].toString()});
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Verify input fields
     *
     * @return Always return false so that the dialog doesn't close
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        return false;
    }

    /**********************************************************************************************
     * Get the unique project data field names in alphabetical order
     *
     * @return Array containing the unique project data field names in alphabetical order
     *********************************************************************************************/
    private String[][] getDataFieldNames()
    {
        ArrayListMultiple nameList = new ArrayListMultiple();

        // Step through each data field
        for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
        {
            // Check if the field name hasn't already been added to the list
            if (!nameList.contains(fieldInfo.getFieldName()))
            {
                // Add the data field name to the list. Set the description field to null
                nameList.add(new String[] {fieldInfo.getFieldName(), null});
            }
        }

        // Sort the list based on the data field name
        nameList.sort(ArrayListMultipleSortType.STRING);

        return nameList.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the owner name with path, if applicable (child tables of a structure table have a path)
     *
     * @param ownerName
     *            table or group owner name
     *
     * @param path
     *            table path; blank if none
     *
     * @return Table or group name with path, if applicable
     *********************************************************************************************/
    private String getOwnerWithPath(String ownerName, String path)
    {
        // Check if the owner has a path
        if (!path.isEmpty())
        {
            // Prepend the path to the table name
            ownerName = CcddUtilities.removeHTMLTags(path).replaceAll(" ", "") + ","
                        + CcddUtilities.removeHTMLTags(ownerName).replaceAll(" ", "");
        }
        // No path provided - either this is a root-level table or the owner is the project, a
        // group, or a table type
        else
        {
            // Remove any highlighting and extra spaces (such as that used for indenting child
            // structure names) from the owner name
            ownerName = highlightFieldOwner(ownerName, false);
        }

        return ownerName;
    }

    /**********************************************************************************************
     * Determine if the data field owner isn't a table. This is the case for project, group, and
     * table type fields
     *
     * @param ownerName
     *            name of the data field's owner
     *
     * @return true if the field owner isn't a table
     *********************************************************************************************/
    private boolean ownerIsNotTable(String ownerName)
    {
        boolean isNotTable = false;

        // Remove the HTML tags from the owner name and trim any leading/trailing spaces (such as
        // those used for indenting child structure names)
        ownerName = CcddUtilities.removeHTMLTags(ownerName).trim();

        // Check if this field belongs to the project
        if (ownerName.startsWith(CcddFieldHandler.getFieldProjectName()))
        {
            // Set the flag to indicate the owner isn't a table
            isNotTable = true;
        }
        // Check if this field belongs to a group
        else if (ownerName.startsWith(CcddFieldHandler.getFieldGroupName("")))
        {
            // Set the flag to indicate the owner isn't a table
            isNotTable = true;
        }
        // Check if this field belongs to a table type
        else if (ownerName.startsWith(CcddFieldHandler.getFieldTypeName("")))
        {
            // Set the flag to indicate the owner isn't a table
            isNotTable = true;
        }

        return isNotTable;
    }

    /**********************************************************************************************
     * Highlight the data field owner's indicator text (the indicator determines if the field
     * belongs to the project, a group, or a table type)
     *
     * @param ownerName
     *            name of the data field's owner
     *
     * @param enable
     *            true to highlight the data field indicator; false to remove any highlighting
     *
     * @return The data field owner with the indicator highlighted or not highlighted
     *********************************************************************************************/
    private String highlightFieldOwner(String ownerName, boolean enable)
    {
        String prepend = null;

        // Remove the HTML tags from the owner name and trim any leading/trailing spaces (such as
        // those used for indenting child structure names)
        ownerName = CcddUtilities.removeHTMLTags(ownerName).trim();

        // Check if this field belongs to the project
        if (CcddFieldHandler.isProjectField(ownerName))
        {
            // Get the project field indicator
            prepend = PROJECT_DATA_FIELD_IDENT;
        }
        // Check if this field belongs to a group
        else if (CcddFieldHandler.isGroupField(ownerName))
        {
            // Get the group field indicator
            prepend = GROUP_DATA_FIELD_IDENT;
        }
        // Check if this field belongs to a table type
        else if (CcddFieldHandler.isTableTypeField(ownerName))
        {
            // Get the table type field indicator
            prepend = TYPE_DATA_FIELD_IDENT;
        }

        // Check if the field belongs to the project, a group, or a table type
        if (prepend != null)
        {
            // Check if highlighting is to be applied
            if (enable)
            {
                // Highlight the field owner indicator and append a space to it
                ownerName = ownerName.replaceFirst("^(" + prepend + ")(.*)",
                                                   "<html><i>$1</i>&#160;$2");
            }
            // Highlighting is to be removed
            else
            {
                // Restore the field owner to its original form (remove the added space; the HTML
                // tags are removed above)
                ownerName = ownerName.replaceFirst(prepend + " ", prepend);
            }
        }

        return ownerName;
    }

    /**********************************************************************************************
     * Determine if any unsaved changes exist in the data field table editor
     *
     * @return true if changes exist that haven't been saved; false if there are no unsaved changes
     *********************************************************************************************/
    protected boolean isFieldTableChanged()
    {
        return !selectedCells.getSelectedCells().isEmpty()
               || dataFieldTable.isTableChanged(committedData);
    }
}
