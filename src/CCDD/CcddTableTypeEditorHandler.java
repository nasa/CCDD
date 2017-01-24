/**
 * CFS Command & Data Dictionary table type editor handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CELL_FONT;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.FOCUS_COLOR;
import static CCDD.CcddConstants.PROTECTED_BACK_COLOR;
import static CCDD.CcddConstants.PROTECTED_TEXT_COLOR;
import static CCDD.CcddConstants.SELECTED_BACK_COLOR;
import static CCDD.CcddConstants.TABLE_BACK_COLOR;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellRenderer;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.PaddedComboBox;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary table type editor handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddTableTypeEditorHandler extends CcddEditorPanelHandler
{
    // Class references
    private final CcddMain ccddMain;
    private String tableType;
    private final CcddTableTypeEditorDialog editorDialog;
    private final CcddFieldHandler fieldHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private final TypeDefinition typeDefinition;

    // Components referenced by multiple methods
    private CcddJTableHandler table;
    private PaddedComboBox comboBox;

    // Index for the table type editor's data type column
    private int inputTypeIndex;

    // Flag that indicates if the primitive only column should be displayed
    private final boolean includePrimitiveOnly;

    // Table instance model data. Committed copy is the table information as it
    // exists in the database and is used to determine what changes have been
    // made to the table since the previous database update
    private Object[][] committedData;

    // Storage for the most recent committed field information
    private CcddFieldHandler committedInfo;

    // Type description
    private String committedDescription;

    // Lists of type content changes to process
    private final List<String[]> typeAdditions;
    private final List<String[]> typeModifications;
    private final List<String[]> typeDeletions;

    // Flag indicating if a change in column order occurred
    private boolean columnOrderChange;

    /**************************************************************************
     * Table type editor handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param tableType
     *            table type name
     * 
     * @param fieldDefinitions
     *            data field definitions
     * 
     * @param includePrimitiveOnly
     *            true if the primitive only column should be displayed
     * 
     * @param editorDialog
     *            editor dialog from which this editor was created
     *************************************************************************/
    protected CcddTableTypeEditorHandler(CcddMain ccddMain,
                                         String tableType,
                                         Object[][] fieldDefinitions,
                                         boolean includePrimitiveOnly,
                                         CcddTableTypeEditorDialog editorDialog)
    {
        this.ccddMain = ccddMain;
        this.tableType = tableType;
        this.includePrimitiveOnly = includePrimitiveOnly;
        this.editorDialog = editorDialog;
        this.tableTypeHandler = ccddMain.getTableTypeHandler();

        // Create the field information for this table type
        fieldHandler = new CcddFieldHandler();
        fieldHandler.buildFieldInformation(fieldDefinitions,
                                           CcddFieldHandler.getFieldTypeName(tableType));

        // Get the type definition for the specified type name
        typeDefinition = tableTypeHandler.getTypeDefinition(tableType);

        // Check if the type definition exists
        if (typeDefinition != null)
        {
            // Store the type's data and description
            committedData = typeDefinition.getData();
            committedDescription = typeDefinition.getDescription();
        }
        // This is a new type
        else
        {
            // Initialize the type data and description
            committedData = new Object[0][0];
            committedDescription = "";
        }

        // Create a copy of the field information
        setCommittedInformation(fieldHandler);

        // Initialize the lists of type content changes
        typeAdditions = new ArrayList<String[]>();
        typeModifications = new ArrayList<String[]>();
        typeDeletions = new ArrayList<String[]>();

        // Create the table type editor
        initialize();
    }

    /**************************************************************************
     * Get the table editor dialog
     * 
     * @return Table editor dialog
     *************************************************************************/
    @Override
    protected CcddFrameHandler getTableEditor()
    {
        return editorDialog;
    }

    /**************************************************************************
     * Get the table handler
     * 
     * @return Table handler
     *************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return table;
    }

    /**************************************************************************
     * Get the table type name
     * 
     * @return Table type name
     *************************************************************************/
    protected String getTypeName()
    {
        return tableType;
    }

    /**************************************************************************
     * Set the table type name
     * 
     * @param table
     *            type name
     *************************************************************************/
    protected void setTypeName(String name)
    {
        tableType = name;

        // Set the JTable name so that table change events can be identified
        // with this table
        table.setName(name);

        // Check if the table type has uncommitted changes
        if (isTableChanged())
        {
            // Send a change event so that the editor tab name reflects that
            // the table type has changes
            table.getUndoManager().ownerHasChanges(true);
        }
    }

    /**************************************************************************
     * Set the committed table information
     * 
     * @param info
     *            table information class for extracting the current table
     *            name, type, column order, and description
     *************************************************************************/
    private void setCommittedInformation(CcddFieldHandler handler)
    {
        // Create a new field handler and copy the current field information
        // into it
        committedInfo = new CcddFieldHandler();
        committedInfo.setFieldInformation(handler.getFieldInformationCopy());

        // Check if the table has been created
        if (table != null)
        {
            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**************************************************************************
     * Get the UndoManager for this table editor
     * 
     * @return Table UndoManager
     *************************************************************************/
    @Override
    protected CcddUndoManager getEditPanelUndoManager()
    {
        return table.getUndoManager();
    }

    /**************************************************************************
     * Get the column additions for this table type
     * 
     * @return List of column additions
     *************************************************************************/
    protected List<String[]> getTypeAdditions()
    {
        return typeAdditions;
    }

    /**************************************************************************
     * Get the column changes for this table type
     * 
     * @return List of column changes
     *************************************************************************/
    protected List<String[]> getTypeModifications()
    {
        return typeModifications;
    }

    /**************************************************************************
     * Get the column deletions for this table type
     * 
     * @return List of column deletions
     *************************************************************************/
    protected List<String[]> getTypeDeletions()
    {
        return typeDeletions;
    }

    /**************************************************************************
     * Get the column order change status
     * 
     * @return true if the table type's column order changed
     *************************************************************************/
    protected boolean getColumnOrderChange()
    {
        return columnOrderChange;
    }

    /**************************************************************************
     * Perform the steps needed following execution of table type changes
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doTypeUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Store the current table data and description as the last
            // committed
            committedData = table.getTableData(true);
            committedDescription = getDescription();
            setCommittedInformation(fieldHandler);

            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**************************************************************************
     * Create the table type editor
     *************************************************************************/
    private void initialize()
    {
        // Define the table type editor JTable
        table = new CcddJTableHandler()
        {
            /******************************************************************
             * Return true if the type data, description, or data field changes
             *****************************************************************/
            @Override
            protected boolean isTableChanged(Object[][] data)
            {
                // Update the field information with the current text field
                // values
                updateCurrentFields(fieldHandler.getFieldInformation());

                // Set the change flag if the number of fields in the committed
                // version differs from the current version of the table
                boolean isFieldChanged = fieldHandler.getFieldInformation().size() != committedInfo.getFieldInformation().size();

                // Check if the number of fields is the same between the
                // committed and current versions
                if (!isFieldChanged)
                {
                    // Get the current and committed field descriptions
                    Object[][] current = fieldHandler.getFieldDefinitionArray(true);
                    Object[][] committed = committedInfo.getFieldDefinitionArray(true);

                    // Step through each field
                    for (int row = 0; row < current.length; row++)
                    {
                        // Step through each field member
                        for (int column = 0; column < current[row].length; column++)
                        {
                            // Check if the current and committed values differ
                            if (!current[row][column].equals(committed[row][column]))
                            {
                                // Set the flag indicating a field is changed
                                // and stop searching
                                isFieldChanged = true;
                                break;
                            }
                        }
                    }
                }

                return isFieldChanged
                       || !committedDescription.equals(getDescription())
                       || super.isTableChanged(data);
            }

            /******************************************************************
             * Allow resizing of all columns except the Unique and Required
             * columns
             *****************************************************************/
            @Override
            protected boolean isColumnResizable(int column)
            {
                return column != TableTypeEditorColumnInfo.UNIQUE.ordinal()
                       && column != TableTypeEditorColumnInfo.REQUIRED.ordinal();
            }

            /******************************************************************
             * Allow multiple line display in all but the Unique and Required
             * columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column != TableTypeEditorColumnInfo.UNIQUE.ordinal()
                       && column != TableTypeEditorColumnInfo.REQUIRED.ordinal();
            }

            /******************************************************************
             * Override isCellEditable so that all columns can be edited
             *****************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                boolean isEditable = true;

                // Check if the table is displayable (to prevent corruption of
                // the cell editor), if the table model exists, and if the
                // table has at least one row
                if (isDisplayable()
                    && getModel() != null
                    && getModel().getRowCount() != 0)
                {
                    // Create storage for the row of table data
                    Object[] rowData = new Object[getModel().getColumnCount()];

                    // Convert the view row and column indices to model
                    // coordinates
                    int modelRow = convertRowIndexToModel(row);
                    int modelColumn = convertColumnIndexToModel(column);

                    // Step through each column in the row
                    for (int index = 0; index < rowData.length; index++)
                    {
                        // Store the column value into the row data array
                        rowData[index] = getModel().getValueAt(modelRow, index);
                    }

                    // Check if the cell is editable
                    isEditable = isDataAlterable(rowData, modelRow, modelColumn);
                }

                return isEditable;
            }

            /******************************************************************
             * Override isDataAlterable to determine which table data values
             * can be changed
             * 
             * @param rowData
             *            array containing the table row data
             * 
             * @param row
             *            table row index in model coordinates
             * 
             * @param column
             *            table column index in model coordinates
             * 
             * @return true if the data value can be changed
             *****************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData,
                                              int row,
                                              int column)
            {
                // Allow editing if this is the column name or description
                // column, or if this column isn't protected (unless this is
                // the Primitive column and the input type is a rate or
                // enumeration)
                return column == TableTypeEditorColumnInfo.NAME.ordinal()
                       || column == TableTypeEditorColumnInfo.COMMENT.ordinal()
                       || typeDefinition == null
                       || (!DefaultColumn.isProtectedColumn(typeDefinition.getName(),
                                                            rowData[TableTypeEditorColumnInfo.NAME.ordinal()].toString())
                       && (column != TableTypeEditorColumnInfo.PRIMITIVE_ONLY.ordinal()
                       || (!rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].equals(InputDataType.RATE.getInputName())
                       && !rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].equals(InputDataType.ENUMERATION.getInputName()))));
            }

            /******************************************************************
             * Override the CcddJTableHandler method to prevent deleting the
             * contents of the cell at the specified row and column
             * 
             * @param row
             *            table row index in view coordinates
             * 
             * @param column
             *            table column index in view coordinates
             * 
             * @return false if the cell contains a combo box; true otherwise
             *****************************************************************/
            @Override
            protected boolean isCellBlankable(int row, int column)
            {
                return convertColumnIndexToModel(column) != TableTypeEditorColumnInfo.INPUT_TYPE.ordinal();
            }

            /******************************************************************
             * Validate changes to the editable cells
             * 
             * @param tableData
             *            list containing the table data row arrays
             * 
             * @param row
             *            table model row index
             * 
             * @param column
             *            table model column index
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

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try
                {
                    // Check if the column name has been changed and if the
                    // name isn't blank
                    if (column == TableTypeEditorColumnInfo.NAME.ordinal()
                        && !newValueS.isEmpty())
                    {
                        // Check if the column name matches a default name
                        // (case insensitive)
                        if (newValueS.equalsIgnoreCase(DefaultColumn.PRIMARY_KEY.getDbName())
                            || newValueS.equalsIgnoreCase(DefaultColumn.ROW_INDEX.getDbName()))
                        {
                            throw new CCDDException("Column name '"
                                                    + newValueS
                                                    + "' already in use (hidden)");
                        }

                        // Get the database form of the column name
                        String dbName = DefaultColumn.convertVisibleToDatabase(newValueS,
                                                                               InputDataType.getInputTypeByName(tableData.get(row)[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()));

                        // Compare this column name to the others in the table
                        // in order to avoid creating a duplicate
                        for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                        {
                            // Check if this row isn't the one being edited,
                            if (otherRow != row)
                            {
                                // Check if the column name matches the one
                                // being added (case insensitive)
                                if (newValueS.equalsIgnoreCase(tableData.get(otherRow)[column].toString()))
                                {
                                    throw new CCDDException("Column name '"
                                                            + newValueS
                                                            + "' already in use");
                                }

                                // Check if the database form of the column
                                // name matches matches the database form of
                                // the one being added
                                if (dbName.equalsIgnoreCase(DefaultColumn.convertVisibleToDatabase(tableData.get(otherRow)[TableTypeEditorColumnInfo.NAME.ordinal()].toString(),
                                                                                                   InputDataType.getInputTypeByName(tableData.get(otherRow)[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()))))
                                {
                                    throw new CCDDException("Column name '"
                                                            + newValueS
                                                            + "' already in use (database)");
                                }
                            }

                        }
                    }
                    // Check if a non-boolean value is being put into a cell
                    // that expects a boolean value
                    else if ((column == TableTypeEditorColumnInfo.UNIQUE.ordinal()
                             || column == TableTypeEditorColumnInfo.REQUIRED.ordinal())
                             && !newValue.equals(true)
                             && !newValue.equals(false))
                    {
                        throw new CCDDException("Column '"
                                                + TableTypeEditorColumnInfo.getColumnNames()[column]
                                                + "' expects a boolean value");
                    }
                    // Check if this is the input type column
                    else if (column == TableTypeEditorColumnInfo.INPUT_TYPE.ordinal())
                    {
                        // Check if the input type is disabled
                        if (newValueS.startsWith(DISABLED_TEXT_COLOR))
                        {
                            throw new CCDDException("");
                        }
                        // Check if the input type is invalid
                        else if (InputDataType.getInputTypeByName(newValueS) == null)
                        {
                            throw new CCDDException("Unknown input type '"
                                                    + newValueS
                                                    + "'");
                        }
                        // Check if the table type represents a structure and
                        // the input type is either an enumeration or a sample
                        // rate
                        else if (typeDefinition != null
                                 && typeDefinition.isStructure()
                                 && (newValueS.equals(InputDataType.ENUMERATION.getInputName())
                                 || newValueS.equals(InputDataType.RATE.getInputName())))
                        {
                            // Set the 'primitive only' flag
                            tableData.get(row)[TableTypeEditorColumnInfo.PRIMITIVE_ONLY.ordinal()] = true;
                        }
                        // Check if this is the first selection of an input
                        // type that only allows one column of this type in a
                        // table definition
                        else if (DefaultColumn.isInputTypeUnique(TYPE_STRUCTURE, newValueS)
                                 || DefaultColumn.isInputTypeUnique(TYPE_COMMAND, newValueS))
                        {
                            // Display the input type as disabled so that it
                            // can't be selected again
                            int index = comboBox.getSelectedIndex();
                            comboBox.removeItem(newValueS);
                            comboBox.insertItemAt(DISABLED_TEXT_COLOR + newValueS, index);
                        }

                        // Convert the original value to a string
                        String oldValueS = oldValue.toString();

                        // Check if the previous input type was one that only
                        // allows one column of this type in a table definition
                        if (DefaultColumn.isInputTypeUnique(TYPE_STRUCTURE, oldValueS)
                            || DefaultColumn.isInputTypeUnique(TYPE_COMMAND, oldValueS))
                        {
                            // Step through each item in the combo box
                            for (int index = 0; index < comboBox.getItemCount(); index++)
                            {
                                // Check if the previous, disabled selection is
                                // located
                                if (oldValueS.equals(CcddUtilities.removeHTMLTags(comboBox.getItemAt(index))))
                                {
                                    // Display the input type as enabled so
                                    // that it can be selected again, and stop
                                    // searching
                                    comboBox.removeItemAt(index);
                                    comboBox.insertItemAt(oldValueS, index);
                                    break;
                                }
                            }
                        }

                        // Set the value to the original cell value for the
                        // first loop
                        String value = oldValueS;

                        // Check both the old and new cell values
                        for (int loop = 0; loop < 2; loop++)
                        {
                            // Get the two input types that represent data
                            // types. A table can only use one of these two
                            // types
                            InputDataType inputType1 = InputDataType.PRIM_AND_STRUCT;
                            InputDataType inputType2 = InputDataType.PRIMITIVE;

                            // Check if the input type name matches either of
                            // the two target types
                            if (value.equals(inputType1.getInputName())
                                || value.equals(inputType2.getInputName()))
                            {
                                // Check if the input type matches the second
                                // target type
                                if (value.equals(inputType2.getInputName()))
                                {
                                    // Swap the target types
                                    InputDataType temp = inputType1;
                                    inputType1 = inputType2;
                                    inputType2 = temp;
                                }

                                // Step through each input type in the combo
                                // box
                                for (int index = 0; index < comboBox.getItemCount(); index++)
                                {
                                    // Set the flag to true if the input type
                                    // is disabled
                                    boolean isDisabled = comboBox.getItemAt(index).startsWith(DISABLED_TEXT_COLOR);

                                    // Check if this is the the second input
                                    // type. Ignore the HTML tags if disabled
                                    if (inputType2.getInputName().equals(isDisabled
                                                                                   ? CcddUtilities.removeHTMLTags(comboBox.getItemAt(index))
                                                                                   : comboBox.getItemAt(index)))
                                    {
                                        // Toggle display of the second input
                                        // type between enabled and disabled,
                                        // and stop searching
                                        comboBox.removeItemAt(index);
                                        comboBox.insertItemAt(isDisabled
                                                                        ? inputType2.getInputName()
                                                                        : DISABLED_TEXT_COLOR
                                                                          + inputType2.getInputName(),
                                                              index);
                                        break;
                                    }
                                }
                            }

                            // Set the value to the new cell value for the
                            // second loop
                            value = newValueS;
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's
                    // content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage && !ce.getMessage().isEmpty())
                    {
                        // Inform the user that input value is invalid
                        new CcddDialogHandler().showMessageDialog(editorDialog,
                                                                  "<html><b>"
                                                                      + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value
                    tableData.get(row)[column] = oldValue;
                    table.getUndoManager().undoRemoveEdit();
                }

                return showMessage;
            }

            /******************************************************************
             * Load the table type definition values into the table and format
             * the table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Create a list for any columns to be hidden
                List<Integer> hiddenColumns = new ArrayList<Integer>();

                // Hide the index column
                hiddenColumns.add(TableTypeEditorColumnInfo.INDEX.ordinal());

                // Check if the primitive only column should be hidden
                if (!includePrimitiveOnly)
                {
                    // Hide the primitive only column
                    hiddenColumns.add(TableTypeEditorColumnInfo.PRIMITIVE_ONLY.ordinal());
                }

                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                int totalWidth = setUpdatableCharacteristics(committedData,
                                                             TableTypeEditorColumnInfo.getColumnNames(),
                                                             null,
                                                             hiddenColumns.toArray(new Integer[0]),
                                                             TableTypeEditorColumnInfo.getToolTips(),
                                                             true,
                                                             true,
                                                             true,
                                                             true);

                // Set the minimum table size based on the column widths
                editorDialog.setTableWidth(totalWidth);
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

                // Check if the cell doesn't have the focus or is selected. The
                // focus and selection highlight colors override the invalid
                // highlight color
                if (comp.getBackground() != FOCUS_COLOR
                    && comp.getBackground() != SELECTED_BACK_COLOR)
                {
                    boolean found = true;

                    // Check if the cell is required and is empty
                    if (TableTypeEditorColumnInfo.values()[table.convertColumnIndexToModel(column)].isRequired()
                        && table.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is
                        // invalid
                        found = false;
                    }
                    // Check if this is the input type column
                    else if (column == inputTypeIndex && comboBox != null)
                    {
                        found = false;

                        // Step through each combo box item
                        for (int index = 0; index < comboBox.getItemCount() && !found; index++)
                        {
                            // Check if the cell matches the combo box item.
                            // Remove any HTML tags in case this item is
                            // displayed as disabled
                            if (CcddUtilities.removeHTMLTags(comboBox.getItemAt(index)).equals(table.getValueAt(row, column).toString()))
                            {
                                // Set the flag indicating that the cell value
                                // is valid
                                found = true;
                            }
                        }
                    }

                    // Check if the cell value is invalid
                    if (!found)
                    {
                        // Change the cell's background color
                        comp.setBackground(Color.YELLOW);
                    }
                    // Check if this cell is protected from changes
                    else if (!isCellEditable(row, column))
                    {
                        // Shade the cell's background
                        comp.setForeground(PROTECTED_TEXT_COLOR);
                        comp.setBackground(PROTECTED_BACK_COLOR);
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
                return TableTypeEditorColumnInfo.getEmptyRow();
            }

            /******************************************************************
             * Override the CcddJTableHandler method for removing a row from
             * the table. Don't allow deletion of a row that represents a
             * protected column definition for this table type
             * 
             * @param tableData
             *            list containing the table data row arrays
             * 
             * @param modelRow
             *            row to remove (model coordinates)
             * 
             * @return The index of the row prior to the last deleted row's
             *         index
             *****************************************************************/
            @Override
            protected int removeRow(List<Object[]> tableData, int modelRow)
            {
                // Check if this row doesn't represent a protected column
                // definition (i.e., isn't a default column for the table type)
                if (typeDefinition == null
                    || !DefaultColumn.isProtectedColumn(typeDefinition.getName(),
                                                        tableData.get(table.convertRowIndexToView(modelRow))[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()))
                {
                    // Remove the row from the table
                    modelRow = super.removeRow(tableData, modelRow);
                }
                // This row represents a protected column definition
                else
                {
                    // Adjust the row index, but don't delete the row
                    modelRow--;
                }

                return modelRow;
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);

        // Create a list for any columns to be hidden
        List<Integer> checkBoxColumns = new ArrayList<Integer>();

        // Display the unique and required columns
        checkBoxColumns.add(TableTypeEditorColumnInfo.UNIQUE.ordinal());
        checkBoxColumns.add(TableTypeEditorColumnInfo.REQUIRED.ordinal());

        // Check if the primitive only column should be displayed
        if (includePrimitiveOnly)
        {
            // Display the primitive only column
            checkBoxColumns.add(TableTypeEditorColumnInfo.PRIMITIVE_ONLY.ordinal());
        }

        // Set common table parameters and characteristics
        table.setFixedCharacteristics(scrollPane,
                                      true,
                                      ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                      TableSelectionMode.SELECT_BY_CELL,
                                      false,
                                      TABLE_BACK_COLOR,
                                      true,
                                      true,
                                      CELL_FONT,
                                      checkBoxColumns.toArray(new Integer[0]),
                                      true);

        // Discard the edits created by adding the columns initially
        table.getUndoManager().discardAllEdits();

        // Create a drop-down combo box to display the available table type
        // input data types
        setUpInputTypeColumn();

        // Set the undo/redo manager for the description and data field values
        setEditPanelUndoManager(table.getUndoManager());

        // Create the editor panel to contain the type editor
        createEditorPanel(editorDialog,
                          scrollPane,
                          tableType,
                          committedDescription,
                          fieldHandler);

        // Set the JTable name so that table change events can be identified
        // with this table
        setTypeName(tableType);
    }

    /**************************************************************************
     * Set up the combo box containing the available table type input data
     * types for display in the table's Input Type cells
     *************************************************************************/
    private void setUpInputTypeColumn()
    {
        // Get the list of all input data types
        String[] inputNames = InputDataType.getInputNames(true);

        // Step through each row in the table type
        for (int row = 0; row < table.getRowCount(); row++)
        {
            // Step through each input type
            for (int index = 0; index < inputNames.length; index++)
            {
                // Get the input type for this row
                String inputType = committedData[row][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();

                // Check if the input type should be disabled based on the
                // following criteria:
                if ((
                    // The input type matches the one on this row and this
                    // input type can only be used once in a table type
                    (inputType.equals(inputNames[index])
                    && (DefaultColumn.isInputTypeUnique(TYPE_STRUCTURE, inputNames[index])
                    || DefaultColumn.isInputTypeUnique(TYPE_COMMAND, inputNames[index]))))

                    // The input type is for a primitive+structure data type
                    // and this is the primitive data type input type
                    || (inputType.equals(InputDataType.PRIM_AND_STRUCT.getInputName())
                    && inputNames[index].equals(InputDataType.PRIMITIVE.getInputName()))

                    // The input type is for a primitive data type and this is
                    // the primitive+structure data type input type
                    || (inputType.equals(InputDataType.PRIMITIVE.getInputName())
                    && inputNames[index].equals(InputDataType.PRIM_AND_STRUCT.getInputName())))
                {
                    // Set the input type so that it appears disabled in the
                    // list and stop searching
                    inputNames[index] = DISABLED_TEXT_COLOR + inputNames[index];
                    break;
                }
            }
        }

        // Create a combo box for displaying table type input types
        comboBox = new PaddedComboBox(inputNames,
                                      InputDataType.getDescriptions(true),
                                      CELL_FONT);

        // Get the index of the input data type column in view coordinates
        inputTypeIndex = table.convertColumnIndexToView(TableTypeEditorColumnInfo.INPUT_TYPE.ordinal());

        // Set the column table editor to the combo box
        table.getColumnModel().getColumn(inputTypeIndex).setCellEditor(new DefaultCellEditor(comboBox));

        // Set the default selected type
        comboBox.setSelectedItem(InputDataType.TEXT.getInputName());
    }

    /**************************************************************************
     * Determine if any changes have been made compared to the most recently
     * committed table data
     * 
     * @return true if any cell in the table has been changed, if the column
     *         order has changed, or if the table description has changed
     *************************************************************************/
    protected boolean isTableChanged()
    {
        return table.isTableChanged(committedData);
    }

    /**************************************************************************
     * Compare the current table type data to the committed table type data and
     * create lists of the changed values necessary to update the table
     * definitions table in the database to match the current values
     *************************************************************************/
    protected void buildUpdates()
    {
        // Get the table type data array
        Object[][] typeData = table.getTableData(true);

        // Create/update the type definition
        ccddMain.getTableTypeHandler().createTypeDefinition(tableType,
                                                            typeData,
                                                            getDescription());

        // Remove existing changes, if any
        typeAdditions.clear();
        typeModifications.clear();
        typeDeletions.clear();

        // Initialize the column order change status
        columnOrderChange = false;

        // Create storage for flags that indicate if a row has been matched
        boolean[] rowFound = new boolean[committedData.length];

        // Step through each row of the current data
        for (int tblRow = 0; tblRow < typeData.length; tblRow++)
        {
            boolean matchFound = false;

            // Get the original column name
            String currColumnName = typeData[tblRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

            // Step through each row of the committed data
            for (int comRow = 0; comRow < committedData.length; comRow++)
            {
                // Replace any spaces in the column name with an underscore and
                // convert upper case to lower case
                String prevColumnName = committedData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

                // Check if the committed row hasn't already been matched and
                // if the current and committed column indices are the same
                if (!rowFound[comRow]
                    && typeData[tblRow][TableTypeEditorColumnInfo.INDEX.ordinal()].equals(committedData[comRow][TableTypeEditorColumnInfo.INDEX.ordinal()]))
                {
                    // Set the flags indicating a matching row has been found
                    rowFound[comRow] = true;
                    matchFound = true;

                    // Check if the previous and current column definition row
                    // is different
                    if (tblRow != comRow)
                    {
                        // Set the flag indicating the column order changed
                        columnOrderChange = true;
                    }

                    // Check if the column name changed
                    if (!prevColumnName.equals(currColumnName))
                    {
                        // The column name is changed. Add the new and old
                        // column names to the list
                        typeModifications.add(new String[] {prevColumnName,
                                                            currColumnName,
                                                            committedData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()});
                    }

                    // Stop searching since a match exists
                    break;
                }
            }

            // Check if no match was made with the committed data for the
            // current table row
            if (!matchFound)
            {
                // The column definition is being added; add the column name to
                // the list
                typeAdditions.add(new String[] {currColumnName,
                                                typeData[tblRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()});
            }
        }

        // Step through each row of the committed data
        for (int comRow = 0; comRow < committedData.length; comRow++)
        {
            // Check if no matching row was found with the current data
            if (!rowFound[comRow])
            {
                // The column definition has been deleted; add the column name
                // and input type to the list
                typeDeletions.add(new String[] {committedData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString(),
                                                committedData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()});
            }
        }
    }

    /**************************************************************************
     * Check that a row with contains data in the required columns
     * 
     * @return true if a row is missing data in a required column
     *************************************************************************/
    protected boolean checkForMissingColumns()
    {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Step through each row in the table
        for (int row = 0; row < table.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = table.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < table.getRowCount())
            {
                // Step through each column in the row
                for (int column = 0; column < table.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (column == table.convertColumnIndexToView(TableTypeEditorColumnInfo.NAME.ordinal())
                        && table.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data.
                        // If Cancel is selected then do not perform checks on
                        // other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(editorDialog,
                                                                      "<html><b>Data must be provided for column '"
                                                                          + table.getColumnName(column)
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
}
