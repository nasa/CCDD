/**
 * CFS Command & Data Dictionary data type editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.PaddedComboBox;
import CCDD.CcddClasses.TableModification;
import CCDD.CcddClasses.ValidateCellActionListener;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary data type editor dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddDataTypeEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDataTypeHandler dataTypeHandler;
    private CcddJTableHandler dataTypeTable;

    // Cell editor for the base data type column
    private DefaultCellEditor baseTypeCellEditor;

    // Table instance model data. Current copy is the table information as it
    // exists in the table editor and is used to determine what changes have
    // been made to the table since the previous field editor update
    private String[][] committedData;

    // List of data type table content changes to process
    private List<TableModification> modifications;

    // List of data type references already loaded from the database. This is
    // used to avoid repeated searches for a the same data type
    private List<DataTypeReference> loadedReferences;

    // Dialog title
    private static final String DIALOG_TITLE = "Data Type Editor";

    /**************************************************************************
     * Data type data table references class
     *************************************************************************/
    private class DataTypeReference
    {
        private final String dataTypeName;
        private final String[] references;

        /**********************************************************************
         * Data type data table references class constructor
         *
         * @param dataTypeName
         *            data type name
         *********************************************************************/
        DataTypeReference(String dataTypeName)
        {
            this.dataTypeName = dataTypeName;

            // Get the references to the specified data type in the data tables
            references = dataTypeHandler.getDataTypeReferences(dataTypeName,
                                                               CcddDataTypeEditorDialog.this);
        }

        /**********************************************************************
         * Get the data type name associated with the references
         *
         * @return Data type name
         *********************************************************************/
        protected String getDataTypeName()
        {
            return dataTypeName;
        }

        /**********************************************************************
         * Get the references in the data tables for this data type
         *
         * @return References in the data tables for this data type
         *********************************************************************/
        protected String[] getReferences()
        {
            return references;
        }
    }

    /**************************************************************************
     * Data type editor dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddDataTypeEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();

        // Set the reference to this dialog in main
        ccddMain.setDataTypeEditor(this);

        // Create the data type editor dialog
        initialize();
    }

    /**************************************************************************
     * Get the reference to the data type table
     *
     * @return Reference to the data type table
     *************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return dataTypeTable;
    }

    /**************************************************************************
     * Perform the steps needed following execution of data type updates to the
     * database
     *
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doDataTypeUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Update the data type handler with the changes
            dataTypeHandler.setDataTypeData(getUpdatedData());

            // Update the data type columns
            dbTable.updateDataTypeColumns(CcddDataTypeEditorDialog.this);

            // Update the copy of the data type data so it can be used to
            // determine if changes are made
            storeCurrentData();

            // Accept all edits for this table
            dataTypeTable.getUndoManager().discardAllEdits();
        }
    }

    /**************************************************************************
     * Copy the data type data so it can be used to determine if changes are
     * made
     *************************************************************************/
    private void storeCurrentData()
    {
        // Check if the table has fields
        if (!dataTypeHandler.getDataTypeData().isEmpty())
        {
            // Store the data type information
            committedData = dataTypeHandler.getDataTypeData().toArray(new String[0][0]);
        }
        // The table has no date types
        else
        {
            // Initialize the fields
            committedData = new String[0][0];
        }
    }

    /**************************************************************************
     * Create the data type editor dialog. This is executed in a separate
     * thread since it can take a noticeable amount time to complete, and by
     * using a separate thread the GUI is allowed to continue to update. The
     * GUI menu commands, however, are disabled until the telemetry scheduler
     * initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the data type editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /******************************************************************
             * Build the data type editor dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
                modifications = new ArrayList<TableModification>();
                loadedReferences = new ArrayList<DataTypeReference>();

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

                // Create a copy of the data type data so it can be used to
                // determine if changes are made
                storeCurrentData();

                // Define the panel to contain the table and place it in the
                // editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createDataTypeTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());

                // Create the cell editor for base data types
                createBasePrimitiveTypeCellEditor();

                // Set the modal undo manager and table references in the
                // keyboard handler while the data type editor is active
                ccddMain.getKeyboardHandler().setModalDialogReference(dataTypeTable.getUndoManager(),
                                                                      dataTypeTable);

                // New button
                JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                           INSERT_ICON,
                                                                           KeyEvent.VK_I,
                                                                           "Insert a new row into the table");

                // Create a listener for the Insert Row button
                btnInsertRow.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Insert a new row into the table at the selected location
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataTypeTable.insertEmptyRow(true);
                    }
                });

                // Delete button
                JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                           DELETE_ICON,
                                                                           KeyEvent.VK_D,
                                                                           "Delete the selected row(s) from the table");

                // Create a listener for the Delete row button
                btnDeleteRow.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Delete the selected row(s) from the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Step through each row selected for deletion
                        for (int row : dataTypeTable.getSelectedRows())
                        {
                            // Get the data type name
                            String name = CcddDataTypeHandler.getDataTypeName(dataTypeTable.getValueAt(row,
                                                                                                       DataTypesColumn.USER_NAME.ordinal())
                                                                                           .toString(),
                                                                              dataTypeTable.getValueAt(row,
                                                                                                       DataTypesColumn.C_NAME.ordinal())
                                                                                           .toString());

                            // Check if the data type is used in any of the
                            // data tables
                            if (!name.isEmpty()
                                && dataTypeHandler.getDataTypeReferences(name, CcddDataTypeEditorDialog.this).length != 0)
                            {
                                // Deselect the data type
                                dataTypeTable.removeRowSelectionInterval(row, row);

                                // Inform the user that the data type can't be
                                // deleted
                                new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                                          "<html><b>Cannot delete data type '"
                                                                                                         + name
                                                                                                         + "'; data type is referenced by a data table",
                                                                          "Delete Data Type",
                                                                          JOptionPane.QUESTION_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                            }
                        }

                        dataTypeTable.deleteRow(true);
                    }
                });

                // Move Up button
                JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                        UP_ICON,
                                                                        KeyEvent.VK_U,
                                                                        "Move the selected row(s) up");

                // Create a listener for the Move Up button
                btnMoveUp.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Move the selected row(s) up in the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataTypeTable.moveRowUp();
                    }
                });

                // Move Down button
                JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                          DOWN_ICON,
                                                                          KeyEvent.VK_W,
                                                                          "Move the selected row(s) down");

                // Create a listener for the Move Down button
                btnMoveDown.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Move the selected row(s) down in the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataTypeTable.moveRowDown();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit");

                // Create a listener for the Undo button
                btnUndo.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Undo the last cell edit
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataTypeTable.getUndoManager().undo();
                    }
                });

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit");

                // Create a listener for the Redo button
                btnRedo.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Redo the last cell edit that was undone
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        dataTypeTable.getUndoManager().redo();
                    }
                });

                // Store the data types button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store the data type(s)");

                // Create a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Store the data types
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's
                        // content has changed, none of the required columns is
                        // missing a value, and the user confirms the action
                        if (dataTypeTable.isTableChanged(committedData)
                            && !checkForMissingColumns()
                            && new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Get a list of the data type modifications
                            buildUpdates();

                            // Update the tables affected by the changes to the
                            // data type(s)
                            dbTable.modifyTablePerDataTypeOrMacroChanges(modifications,
                                                                         getUpdatedData(),
                                                                         CcddDataTypeEditorDialog.this);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the data type editor");

                // Create a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener(dataTypeTable)
                {
                    /**********************************************************
                     * Close the data type editor dialog
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        windowCloseButtonAction();
                    }
                });

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
            }

            /******************************************************************
             * Data type editor dialog creation complete
             *****************************************************************/
            @Override
            protected void complete()
            {
                // Display the data type editor dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  editorPnl,
                                  buttonPnl,
                                  btnClose,
                                  DIALOG_TITLE,
                                  true);
            }
        });
    }

    /**************************************************************************
     * Create the data type table
     *
     * @return Reference to the scroll pane in which the table is placed
     *************************************************************************/
    private JScrollPane createDataTypeTable()
    {
        // Define the data type editor JTable
        dataTypeTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
        {
            /******************************************************************
             * Allow multiple line display in user name and C name columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column == DataTypeEditorColumnInfo.USER_NAME.ordinal()
                       || column == DataTypeEditorColumnInfo.C_NAME.ordinal();
            }

            /******************************************************************
             * Hide the specified column(s)
             *****************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == DataTypeEditorColumnInfo.OID.ordinal();
            }

            /******************************************************************
             * Override isCellEditable so that all columns can be edited
             *****************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return true;
            }

            /******************************************************************
             * Allow pasting data into the data type cells
             *****************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData,
                                              int row,
                                              int column)
            {
                return isCellEditable(convertRowIndexToView(row),
                                      convertColumnIndexToView(column));
            }

            /******************************************************************
             * Override getCellEditor so that for a base data type column cell
             * the base data type combo box cell editor is returned; for all
             * other cells return the normal cell editor
             *
             * @param row
             *            table view row number
             *
             * @param column
             *            table view column number
             *
             * @return The cell editor for the specified row and column
             *****************************************************************/
            @Override
            public TableCellEditor getCellEditor(int row, int column)
            {
                // Get the editor for this cell
                TableCellEditor cellEditor = super.getCellEditor(row, column);

                // Convert the row and column indices to the model coordinates
                int modelColumn = convertColumnIndexToModel(column);

                // Check if the column for which the cell editor is requested
                // is the base data type column
                if (modelColumn == DataTypeEditorColumnInfo.BASE_TYPE.ordinal())
                {
                    // Select the combo box cell editor that displays the base
                    // data types
                    cellEditor = baseTypeCellEditor;
                }

                return cellEditor;
            }

            /******************************************************************
             * Validate changes to the editable cells
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
             *            true to display the invalid input dialog, if
             *            applicable
             *
             * @param isMultiple
             *            true if this is one of multiple cells to be entered
             *            and checked; false if only a single input is being
             *            entered
             *
             * @return Always returns false
             ****************************************************************/
            @Override
            protected Boolean validateCellContent(List<Object[]> tableData,
                                                  int row,
                                                  int column,
                                                  Object oldValue,
                                                  Object newValue,
                                                  Boolean showMessage,
                                                  boolean isMultiple)
            {
                // Reset the flag that indicates the last edited cell's content
                // is invalid
                setLastCellValid(true);

                // Create a string version of the old and new values
                String oldValueS = oldValue.toString();
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the data type user name or C type has been
                        // changed
                        if (column == DataTypeEditorColumnInfo.USER_NAME.ordinal()
                            || column == DataTypeEditorColumnInfo.C_NAME.ordinal())
                        {
                            // Compare this data type name to the others in the
                            // table in order to avoid creating a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being
                                // edited, and if the data type name matches
                                // the one being added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(CcddDataTypeHandler.getDataTypeName(tableData.get(otherRow)[DataTypeEditorColumnInfo.USER_NAME.ordinal()].toString(),
                                                                                                      tableData.get(otherRow)[DataTypeEditorColumnInfo.C_NAME.ordinal()].toString())))
                                {
                                    throw new CCDDException("Data type name already in use");
                                }
                            }

                            // Check if the data type user name has been
                            // changed
                            if (column == DataTypeEditorColumnInfo.USER_NAME.ordinal())
                            {
                                // Check if the data type name does not match
                                // the alphanumeric input type
                                if (!newValueS.matches(InputDataType.ALPHANUMERIC.getInputMatch()))
                                {
                                    throw new CCDDException("Illegal character(s) in data type name");
                                }
                            }
                            // The data type C type has been changed
                            else
                            {
                                // Initialize the C type name matching regular
                                // expression
                                String match = InputDataType.ALPHANUMERIC_MULTI.getInputMatch();

                                // Check if the base data type is a pointer
                                if (tableData.get(row)[DataTypeEditorColumnInfo.BASE_TYPE.ordinal()].equals(BaseDataTypeInfo.POINTER.getName()))
                                {
                                    // Check if the C type name doesn't already
                                    // end with an asterisk
                                    if (!newValueS.endsWith("*"))
                                    {
                                        // Append an asterisk to the C type
                                        // name
                                        newValueS += "*";
                                        tableData.get(row)[column] = newValueS;
                                    }

                                    // Add the ending asterisk to the matching
                                    // regular expression
                                    match += "\\*+";
                                }
                                // Check if the base type is blank
                                else if (tableData.get(row)[DataTypeEditorColumnInfo.BASE_TYPE.ordinal()].toString().isEmpty())
                                {
                                    // Add the optional ending asterisk to the
                                    // matching regular expression
                                    match += "\\*?+";
                                }

                                // Check if the data type name does not match
                                // the multiple alphanumeric input type (with
                                // an asterisk if this is a pointer)
                                if (!newValueS.matches(match))
                                {
                                    throw new CCDDException("Illegal character(s) in data type C type name");
                                }
                            }
                        }
                        // Check if this is the data type size column
                        else if (column == DataTypeEditorColumnInfo.SIZE.ordinal())
                        {
                            // Check if the data type size is not a positive
                            // integer
                            if (!newValueS.matches(InputDataType.INT_POSITIVE.getInputMatch()))
                            {
                                throw new CCDDException("Data type size must be a positive integer");
                            }

                            // Remove any unneeded characters and store the
                            // cleaned number
                            tableData.get(row)[column] = newValueS.replaceAll(InputDataType.INT_POSITIVE.getInputMatch(), "$1");
                        }
                        // Check if this is the data type base type column
                        else if (column == DataTypeEditorColumnInfo.BASE_TYPE.ordinal())
                        {
                            // Get the C type name
                            String cType = tableData.get(row)[DataTypeEditorColumnInfo.C_NAME.ordinal()].toString();

                            // Check if the base type changed from a
                            // non-pointer to a pointer
                            if (newValueS.equals(BaseDataTypeInfo.POINTER.getName()))
                            {
                                // Check that a C type name is present and
                                // doesn't already end with an asterisk
                                if (!cType.isEmpty() && !cType.endsWith("*"))
                                {
                                    // Append an asterisk to the C type name
                                    tableData.get(row)[DataTypeEditorColumnInfo.C_NAME.ordinal()] += "*";
                                }
                            }
                            // Check if the base type changed from a pointer to
                            // a non-pointer or if the base type had been empty
                            else if (oldValueS.equals(BaseDataTypeInfo.POINTER.getName())
                                     || oldValueS.isEmpty())
                            {
                                // Remove any asterisks from the C type name
                                tableData.get(row)[DataTypeEditorColumnInfo.C_NAME.ordinal()] = cType.replaceAll("\\*", "");
                            }
                        }

                        // Check if the size was reduced for an integer (signed
                        // or unsigned) type or if the base data type changed
                        // from an integer (signed or unsigned) to a
                        // non-integer
                        if (!oldValueS.isEmpty()
                            && ((column == DataTypeEditorColumnInfo.SIZE.ordinal()
                                 && Integer.valueOf(newValueS) < Integer.valueOf(oldValueS)
                                 && (tableData.get(row)[DataTypeEditorColumnInfo.BASE_TYPE.ordinal()].equals(BaseDataTypeInfo.SIGNED_INT.getName())
                                     || tableData.get(row)[DataTypeEditorColumnInfo.BASE_TYPE.ordinal()].equals(BaseDataTypeInfo.UNSIGNED_INT.getName())))

                                || (column == DataTypeEditorColumnInfo.BASE_TYPE.ordinal()
                                    && (oldValueS.equals(BaseDataTypeInfo.SIGNED_INT.getName())
                                        || oldValueS.equals(BaseDataTypeInfo.UNSIGNED_INT.getName()))
                                    && !(newValueS.equals(BaseDataTypeInfo.SIGNED_INT.getName())
                                         || newValueS.equals(BaseDataTypeInfo.UNSIGNED_INT.getName())))))
                        {
                            // Get the data type's index
                            String index = tableData.get(row)[DataTypeEditorColumnInfo.OID.ordinal()].toString();

                            // Step through the committed data types
                            for (int commRow = 0; commRow < committedData.length; commRow++)
                            {
                                // Check if the index matches that for the
                                // committed data type
                                if (index.equals(committedData[commRow][DataTypeEditorColumnInfo.OID.ordinal()]))
                                {
                                    List<String> tableNames = new ArrayList<String>();

                                    // Get the data type name. Use the
                                    // committed name (in place of the current
                                    // name in the editor, in case it's been
                                    // changed) since this is how the data type
                                    // is referenced in the data tables
                                    String dataTypeName = CcddDataTypeHandler.getDataTypeName(committedData[commRow]);

                                    DataTypeReference dataTypeRefs = null;

                                    // Step through the list of the data type
                                    // search references already loaded
                                    for (DataTypeReference loadedRef : loadedReferences)
                                    {
                                        // Check if the data type name matches
                                        // that for an already searched data
                                        // type
                                        if (dataTypeName.equals(loadedRef.getDataTypeName()))
                                        {
                                            // Store the data type search
                                            // reference and stop searching
                                            dataTypeRefs = loadedRef;
                                            break;
                                        }
                                    }

                                    // Check if the data type references
                                    // haven't already been loaded
                                    if (dataTypeRefs == null)
                                    {
                                        // Search for references to this data
                                        // type
                                        dataTypeRefs = new DataTypeReference(dataTypeName);

                                        // Add the search results to the list
                                        // so that this search doesn't get
                                        // performed again
                                        loadedReferences.add(dataTypeRefs);
                                    }

                                    // Step through each reference to the data
                                    // type in the tables
                                    for (String dataTypeRef : dataTypeRefs.getReferences())
                                    {
                                        // Split the reference into table name,
                                        // column name, table type, and context
                                        String[] tblColDescAndCntxt = dataTypeRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);
                                        String refComment = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];

                                        // Check if the this is a reference to
                                        // a prototype data table
                                        if (!refComment.isEmpty())
                                        {
                                            // Extract the viewable name and
                                            // type of the table, and the name
                                            // of the column containing the
                                            // data type and separate the
                                            // column string into the
                                            // individual column values
                                            String[] refNameAndType = refComment.split(",");
                                            String[] refColumns = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                                            // Use the type and column to get
                                            // the column's input data type
                                            TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(refNameAndType[1]);

                                            // Get the index of the bit length
                                            // column, if present
                                            int bitLengthIndex = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);

                                            // Check if the byte size changed
                                            if (column == DataTypeEditorColumnInfo.SIZE.ordinal())
                                            {
                                                // Check if the referenced
                                                // table row's bit length value
                                                // exceeds the capacity of the
                                                // new data type byte size, and
                                                // that this table hasn't
                                                // already been found to have a
                                                // conflict
                                                if (bitLengthIndex != -1
                                                    && !refColumns[bitLengthIndex].isEmpty()
                                                    && Integer.valueOf(newValueS) * 8 < Integer.valueOf(refColumns[bitLengthIndex])
                                                    && !tableNames.contains(refNameAndType[0]))
                                                {
                                                    // The bit length is now
                                                    // too large; add the
                                                    // affected table name to
                                                    // the list
                                                    tableNames.add(refNameAndType[0]);
                                                }
                                            }
                                            // The base type changed
                                            else
                                            {
                                                // Check if the referenced
                                                // table row has a bit length
                                                // value, and that this table
                                                // hasn't already been found to
                                                // have a conflict
                                                if (bitLengthIndex != -1
                                                    && !refColumns[bitLengthIndex].isEmpty()
                                                    && !tableNames.contains(refNameAndType[0]))
                                                {
                                                    // A bit length is not
                                                    // valid with the new data
                                                    // type; add the affected
                                                    // table name to the list
                                                    tableNames.add(refNameAndType[0]);
                                                }

                                                // Get the enumeration column
                                                // index(ices), if present
                                                List<Integer> enumerationIndices = typeDefn.getColumnIndicesByInputType(InputDataType.ENUMERATION);

                                                // Step through each
                                                // enumeration column
                                                for (int enumIndex : enumerationIndices)
                                                {
                                                    // Check if the referenced
                                                    // table's enumeration
                                                    // column isn't empty, and
                                                    // that this table hasn't
                                                    // already been found to
                                                    // have a conflict
                                                    if (!refColumns[enumIndex].isEmpty()
                                                        && !tableNames.contains(refNameAndType[0]))
                                                    {
                                                        // An enumeration is
                                                        // not valid with the
                                                        // new data type; add
                                                        // the affected table
                                                        // name to the list
                                                        tableNames.add(refNameAndType[0]);
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Check if any tables with conflicts with
                                    // the new data type were found
                                    if (!tableNames.isEmpty())
                                    {
                                        // Check if the byte size changed
                                        if (column == DataTypeEditorColumnInfo.SIZE.ordinal())
                                        {
                                            throw new CCDDException("Bit length exceeds the size of the data type in table(s) '</b>"
                                                                    + dbTable.getShortenedTableNames(tableNames.toArray(new String[0]))
                                                                    + "<b>'");
                                        }
                                        // The base type changed
                                        else
                                        {
                                            throw new CCDDException("Base data type inconsistent with data type usage in table(s) '</b>"
                                                                    + dbTable.getShortenedTableNames(tableNames.toArray(new String[0]))
                                                                    + "<b>'");
                                        }
                                    }

                                    break;
                                }
                            }
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's
                    // content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                                  "<html><b>"
                                                                                                 + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop
                    // the edit from the stack
                    tableData.get(row)[column] = oldValue;
                    dataTypeTable.getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /******************************************************************
             * Load the table data types into the table and format the table
             * cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(committedData,
                                            DataTypeEditorColumnInfo.getColumnNames(),
                                            null,
                                            DataTypeEditorColumnInfo.getToolTips(),
                                            true,
                                            true,
                                            true);
            }

            /******************************************************************
             * Override prepareRenderer to allow adjusting the background
             * colors of table cells
             *****************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer,
                                             int row,
                                             int column)
            {
                JComponent comp = (JComponent) super.prepareRenderer(renderer,
                                                                     row,
                                                                     column);

                // Check if the cell isn't already selected (selection
                // highlighting overrides the invalid highlighting, if
                // applicable)
                if (!(isFocusOwner()
                      && isRowSelected(row)
                      && (isColumnSelected(column) || !getColumnSelectionAllowed())))
                {
                    boolean found = true;

                    // Convert the column to model coordinates
                    int modelColumn = dataTypeTable.convertColumnIndexToModel(column);

                    // Check if both the user name and C type columns are blank
                    if ((modelColumn == DataTypeEditorColumnInfo.USER_NAME.ordinal()
                         || modelColumn == DataTypeEditorColumnInfo.C_NAME.ordinal())
                        && dataTypeTable.getValueAt(row,
                                                    DataTypeEditorColumnInfo.USER_NAME.ordinal())
                                        .toString().isEmpty()
                        && dataTypeTable.getValueAt(row,
                                                    DataTypeEditorColumnInfo.C_NAME.ordinal())
                                        .toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is
                        // invalid
                        found = false;
                    }
                    // Check if the cell is required and is empty
                    else if (DataTypeEditorColumnInfo.values()[modelColumn].isRequired()
                             && dataTypeTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is
                        // invalid
                        found = false;
                    }

                    // Check if the cell value is invalid
                    if (!found)
                    {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                    }
                }

                return comp;
            }

            /******************************************************************
             * Override the CcddJTableHandler method to produce an array
             * containing empty values for a new row in this table
             *
             * @return Array containing blank cell values for a new row
             *****************************************************************/
            @Override
            protected Object[] getEmptyRow()
            {
                return DataTypeEditorColumnInfo.getEmptyRow();
            }

            /******************************************************************
             * Handle a change to the table's content
             *****************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether any
                // unstored changes exist
                setTitle(DIALOG_TITLE
                         + (dataTypeTable.isTableChanged(committedData)
                                                                        ? "*"
                                                                        : ""));

                // Force the table to redraw so that changes to the cells are
                // displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(dataTypeTable);

        // Disable storage of edit operations during creation of the table
        dataTypeTable.getUndoHandler().setAllowUndo(false);

        // Set common table parameters and characteristics
        dataTypeTable.setFixedCharacteristics(scrollPane,
                                              true,
                                              ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                              TableSelectionMode.SELECT_BY_CELL,
                                              false,
                                              ModifiableColorInfo.TABLE_BACK.getColor(),
                                              true,
                                              true,
                                              ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                              true);

        // Re-enable storage of edit operations
        dataTypeTable.getUndoHandler().setAllowUndo(true);

        return scrollPane;
    }

    /**************************************************************************
     * Handle the dialog close button press event
     *************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the editor table is
        // validated and that there are changes that haven't been stored. If
        // changes exist then confirm discarding the changes
        if (dataTypeTable.isLastCellValid()
            && (!dataTypeTable.isTableChanged(committedData)
                || new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                             "<html><b>Discard changes?",
                                                             "Discard Changes",
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
        {
            // Close the dialog
            closeDialog();

            // Clear the modal dialog references in the keyboard handler
            ccddMain.getKeyboardHandler().setModalDialogReference(null, null);
        }
    }

    /**************************************************************************
     * Create the cell editor for base data types
     *************************************************************************/
    private void createBasePrimitiveTypeCellEditor()
    {
        // Create a combo box for displaying the base data types
        final PaddedComboBox baseComboBox = new PaddedComboBox(dataTypeTable.getFont());

        // Step through each base data type
        for (BaseDataTypeInfo type : BaseDataTypeInfo.values())
        {
            // Add the data type to the list
            baseComboBox.addItem(type.getName().toLowerCase());
        }

        // Add a listener to the combo box for focus changes
        baseComboBox.addFocusListener(new FocusAdapter()
        {
            /******************************************************************
             * Handle a focus gained event so that the combo box automatically
             * expands when selected
             *****************************************************************/
            @Override
            public void focusGained(FocusEvent fe)
            {
                baseComboBox.showPopup();
            }
        });

        // Create the data type cell editor for enumerations
        baseTypeCellEditor = new DefaultCellEditor(baseComboBox);
    }

    /**************************************************************************
     * Get the updated data types
     *
     * @return List containing the updated data types
     *************************************************************************/
    private List<String[]> getUpdatedData()
    {
        return Arrays.asList(CcddUtilities.convertObjectToString(dataTypeTable.getTableData(true)));
    }

    /**************************************************************************
     * Check that a row with contains data in the required columns
     *
     * @return true if a row is missing data in a required column
     *************************************************************************/
    private boolean checkForMissingColumns()
    {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Step through each row in the table
        for (int row = 0; row < dataTypeTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = dataTypeTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < dataTypeTable.getRowCount())
            {
                // Check if both the user-defined name and the C-language name
                // is blank
                if (dataTypeTable.getValueAt(row,
                                             DataTypeEditorColumnInfo.USER_NAME.ordinal())
                                 .toString().isEmpty()
                    && dataTypeTable.getValueAt(row,
                                                DataTypeEditorColumnInfo.C_NAME.ordinal())
                                    .toString().isEmpty())
                {
                    // Set the 'data is missing' flag
                    dataIsMissing = true;

                    // Inform the user that a row is missing required data. If
                    // Cancel is selected then do not perform checks on other
                    // columns and rows
                    if (new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                                  "<html><b>Data must be provided for column '"
                                                                                                 + dataTypeTable.getColumnName(DataTypeEditorColumnInfo.USER_NAME.ordinal())
                                                                                                 + "' or '"
                                                                                                 + dataTypeTable.getColumnName(DataTypeEditorColumnInfo.C_NAME.ordinal())
                                                                                                 + "' [row "
                                                                                                 + (row + 1)
                                                                                                 + "]",
                                                                  "Missing Data",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
                    {
                        // Set the stop flag to prevent further error checking
                        stopCheck = true;
                    }
                }

                // Step through each column in the row
                for (int column = 0; column < dataTypeTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (DataTypeEditorColumnInfo.values()[column].isRequired()
                        && dataTypeTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data.
                        // If Cancel is selected then do not perform checks on
                        // other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddDataTypeEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '"
                                                                                                     + dataTypeTable.getColumnName(column)
                                                                                                     + "' [row "
                                                                                                     + (row + 1)
                                                                                                     + "]",
                                                                      "Missing Data",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
                        {
                            // Set the stop flag to prevent further error
                            // checking
                            stopCheck = true;
                        }

                        break;
                    }
                }
            }
        }

        return dataIsMissing;
    }

    /**************************************************************************
     * Compare the current data type table data to the committed data and
     * create lists of the changed values necessary to update the table in the
     * database to match the current values
     *************************************************************************/
    private void buildUpdates()
    {
        // Remove change information from a previous commit, if any
        modifications.clear();

        // Get the number of rows that have been committed to the database
        int numCommitted = committedData.length;

        // Get the data type table cell values
        String[][] tableData = CcddUtilities.convertObjectToString(dataTypeTable.getTableData(true));

        // Step through each row in the data type table
        for (int tblRow = 0; tblRow < tableData.length; tblRow++)
        {
            boolean matchFound = false;

            // Step through each row in the committed version of the data type
            // table data
            for (int comRow = 0; comRow < numCommitted && !matchFound; comRow++)
            {
                // Check if the index values match for these rows
                if (tableData[tblRow][DataTypesColumn.OID.ordinal()].equals(committedData[comRow][DataTypesColumn.OID.ordinal()]))
                {
                    // Set the flags indicating this row has a match
                    matchFound = true;

                    boolean isChangedColumn = false;

                    // Step through each column in the row
                    for (int column = 0; column < tableData[tblRow].length; column++)
                    {
                        // Check if the current and committed values don't
                        // match
                        if (!tableData[tblRow][column].equals(committedData[comRow][column]))
                        {
                            // Set the flag to indicate a column value changed
                            // and stop searching
                            isChangedColumn = true;
                            break;
                        }
                    }

                    // Check if any columns were changed
                    if (isChangedColumn)
                    {
                        // Store the row modification information
                        modifications.add(new TableModification(tableData[tblRow],
                                                                committedData[comRow]));
                    }
                }
            }
        }
    }
}
