/**************************************************************************************************
/** \file CcddTableTypeEditorHandler.java
*
*   \author Kevin Mccluney
*           Bryan Willis
*
*   \brief
*     Class that handles the commands associated with a specific table type editor. This class
*     is an extension of the CcddInputFieldPanelHandler class.
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

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.DISABLED_TEXT_COLOR;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.MAX_SQL_NAME_LENGTH;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_OTHER;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableCellRenderer;

import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddUndoHandler.UndoableTableModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary table type editor handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableTypeEditorHandler extends CcddInputFieldPanelHandler {
    // Class references
    private final CcddTableTypeEditorDialog editorDialog;
    private final CcddFieldHandler fieldHandler;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private TypeDefinition typeDefinition;

    // Components referenced by multiple methods
    private CcddJTableHandler table;
    private PaddedComboBox comboBox;
    private JCheckBox cmdArgStructureCbx;

    // Name of this editor's table type
    private String tableTypeName;

    // Index for the table type editor's data type column
    private int inputTypeIndex;

    // Table instance model data. Committed copy is the table information as it exists in the
    // database and is used to determine what changes have been made to the table since the
    // previous database update
    private Object[][] committedData;

    // Storage for the most recent committed field information
    private List<FieldInformation> committedFieldInfo;

    // Storage for the most recent committed type description
    private String committedDescription;

    // Storage for the most recent committed command argument structure status
    private boolean committedCmdArgStruct;

    // Lists of type content changes to process
    private final List<String[]> typeAdditions;
    private final List<String[]> typeModifications;
    private final List<String[]> typeDeletions;

    // Lists of field content changes to process
    private final List<TableModification> fieldAdditions;
    private final List<TableModification> fieldModifications;
    private final List<TableModification> fieldDeletions;

    // Flag indicating if a change in column order occurred
    private boolean columnOrderChange;

    // Table type (TYPE_STRUCTURE, TYPE_COMMAND, or TYPE_OTHER)
    private String typeOfTable;

    /**********************************************************************************************
     * Table type editor handler class constructor
     *
     * @param ccddMain      main class reference
     *
     * @param tableTypeName table type name
     *
     * @param editorDialog  editor dialog from which this editor was created
     *********************************************************************************************/
    protected CcddTableTypeEditorHandler(CcddMain ccddMain, String tableTypeName,
            CcddTableTypeEditorDialog editorDialog) {
        this.tableTypeName = tableTypeName;
        this.editorDialog = editorDialog;
        tableTypeHandler = ccddMain.getTableTypeHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();

        // Get the type definition for the specified type name
        typeDefinition = tableTypeHandler.getTypeDefinition(tableTypeName);

        // Check if the type definition exists
        if (typeDefinition != null) {
            // Store the type's data, description, and command argument reference status
            committedData = typeDefinition.getData();
            committedDescription = typeDefinition.getDescription();
            committedCmdArgStruct = typeDefinition.isCommandArgumentStructure();

            // Set the flag to indicate the current table type: structure, command, or other
            typeOfTable = typeDefinition.isStructure() ? TYPE_STRUCTURE
                    : (typeDefinition.isCommand() ? TYPE_COMMAND : TYPE_OTHER);
        }
        // This is a new type
        else {
            // Initialize the type data, description, and command argument reference status
            committedData = new Object[0][0];
            committedDescription = "";
            committedCmdArgStruct = false;

            // Set the flag to indicate that the table type currently represents neither a
            // structure or a command
            typeOfTable = TYPE_OTHER;
        }

        // Create a copy of the field information
        setCommittedInformation();

        // Initialize the lists of type content changes
        typeAdditions = new ArrayList<String[]>();
        typeModifications = new ArrayList<String[]>();
        typeDeletions = new ArrayList<String[]>();

        // Initialize the lists of field content changes
        fieldAdditions = new ArrayList<TableModification>();
        fieldModifications = new ArrayList<TableModification>();
        fieldDeletions = new ArrayList<TableModification>();

        // Create the table type editor
        initialize(ccddMain);
    }

    /**********************************************************************************************
     * Get the table handler
     *
     * @return Table handler
     *********************************************************************************************/
    protected CcddJTableHandler getTable() {
        return table;
    }

    /**********************************************************************************************
     * Get the table type name
     *
     * @return Table type name
     *********************************************************************************************/
    protected String getTypeName() {
        return tableTypeName;
    }

    /**********************************************************************************************
     * Get the reference to the table type definition as it exists prior to making
     * the updates
     *
     * @return Reference to the table type definition as it exists prior to making
     *         the updates
     *********************************************************************************************/
    protected TypeDefinition getTypeDefinition() {
        return typeDefinition;
    }

    /**********************************************************************************************
     * Set the table type name
     *
     * @param tableTypeName table type name
     *********************************************************************************************/
    protected void setTableTypeName(String tableTypeName) {
        this.tableTypeName = tableTypeName;

        // Set the JTable name so that table change events can be identified with this
        // table
        table.setName(tableTypeName);

        // Check if the table type has uncommitted changes
        if (isTableChanged()) {
            // Send a change event so that the editor tab name reflects that the table type
            // has
            // changed
            table.getUndoManager().ownerHasChanged();
        }
    }

    /**********************************************************************************************
     * Set the committed table information
     *********************************************************************************************/
    private void setCommittedInformation() {
        // Store the current field information
        committedFieldInfo = fieldHandler
                .getFieldInformationByOwnerCopy(CcddFieldHandler.getFieldTypeName(tableTypeName));

        // Check if the table has been created
        if (table != null) {
            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Get the UndoManager for this table editor
     *
     * @return Table UndoManager
     *********************************************************************************************/
    @Override
    protected CcddUndoManager getFieldPanelUndoManager() {
        return table.getUndoManager();
    }

    /**********************************************************************************************
     * Get the column additions for this table type
     *
     * @return List of column additions
     *********************************************************************************************/
    protected List<String[]> getTypeAdditions() {
        return typeAdditions;
    }

    /**********************************************************************************************
     * Get the column changes for this table type
     *
     * @return List of column changes
     *********************************************************************************************/
    protected List<String[]> getTypeModifications() {
        return typeModifications;
    }

    /**********************************************************************************************
     * Get the column deletions for this table type
     *
     * @return List of column deletions
     *********************************************************************************************/
    protected List<String[]> getTypeDeletions() {
        return typeDeletions;
    }

    /**********************************************************************************************
     * Get the data field additions
     *
     * @return List of data field additions
     *********************************************************************************************/
    protected List<TableModification> getFieldAdditions() {
        return fieldAdditions;
    }

    /**********************************************************************************************
     * Get the data field changes
     *
     * @return List of data field changes
     *********************************************************************************************/
    protected List<TableModification> getFieldModifications() {
        return fieldModifications;
    }

    /**********************************************************************************************
     * Get the data field deletions
     *
     * @return List of data field deletions
     *********************************************************************************************/
    protected List<TableModification> getFieldDeletions() {
        return fieldDeletions;
    }

    /**********************************************************************************************
     * Get the column order change status
     *
     * @return true if the table type's column order changed
     *********************************************************************************************/
    protected boolean isColumnOrderChange() {
        return columnOrderChange;
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of table type changes
     *
     * @param commandError false if the database commands successfully completed;
     *                     true if an error occurred and the changes were not made
     *********************************************************************************************/
    protected void doTypeUpdatesComplete(boolean commandError) {
        // Update the reference to the altered table type definition
        typeDefinition = tableTypeHandler.getTypeDefinition(tableTypeName);

        // Check that no error occurred performing the database commands
        if (!commandError) {
            // Update the table type's data field information in the field handler
            fieldHandler.replaceFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(tableTypeName),
                    getPanelFieldInformation());

            // Store the current table data and description as the last committed
            committedData = table.getTableData(true);
            committedDescription = getDescription();
            committedCmdArgStruct = cmdArgStructureCbx.isSelected();
            setCommittedInformation();

            // Send a change event so that the editor tab name reflects that the table has
            // changed
            table.getUndoManager().ownerHasChanged();

            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Update the table type editor data fields following a change to an input type
     * definition
     *
     * @param inputTypeNames list of the input type names, before and after the
     *                       changes
     *********************************************************************************************/
    protected void updateForInputTypeChange(List<String[]> inputTypeNames) {
        // Update the input types combo box list
        setUpInputTypeColumn();

        // Update the input types in the field handlers (committed and active)
        fieldHandler.updateFieldInputTypes(inputTypeNames, committedFieldInfo);
        fieldHandler.updateFieldInputTypes(inputTypeNames, getPanelFieldInformation());

        // Redraw the data field panel
        createDataFieldPanel(false, getPanelFieldInformation(), false);
    }

    /**********************************************************************************************
     * Create the table type editor
     *
     * @param ccddMain main class reference
     *********************************************************************************************/
    private void initialize(CcddMain ccddMain) {
        // Define the table type editor JTable
        table = new CcddJTableHandler() {
            /**************************************************************************************
             * Return true if the type data, description, or data field changes
             *************************************************************************************/
            @Override
            protected boolean isTableChanged(Object[][] data) {
                // Update the field information with the current text field values
                updateFieldValueFromComponent(getPanelFieldInformation());

                // Set the flag if the number of fields, field attributes, or field contents
                // have
                // changed
                boolean isFieldChanged = fieldHandler.isFieldChanged(getPanelFieldInformation(), committedFieldInfo,
                        true);

                return isFieldChanged || !committedDescription.equals(getDescription())
                        || committedCmdArgStruct != cmdArgStructureCbx.isSelected() || super.isTableChanged(data);
            }

            /**************************************************************************************
             * Allow resizing of the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnResizable(int column) {
                return column == TableTypeEditorColumnInfo.NAME.ordinal()
                        || column == TableTypeEditorColumnInfo.DESCRIPTION.ordinal()
                        || column == TableTypeEditorColumnInfo.INPUT_TYPE.ordinal();
            }

            /**************************************************************************************
             * Allow multiple line display in the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column) {
                return column == TableTypeEditorColumnInfo.NAME.ordinal()
                        || column == TableTypeEditorColumnInfo.DESCRIPTION.ordinal()
                        || column == TableTypeEditorColumnInfo.INPUT_TYPE.ordinal();
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column) {
                return column == TableTypeEditorColumnInfo.INDEX.ordinal() || (!typeOfTable.equals(TYPE_STRUCTURE)
                        && (column == TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()
                                || column == TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()));
            }

            /**************************************************************************************
             * Display the specified column(s) as check boxes
             *************************************************************************************/
            @Override
            protected boolean isColumnBoolean(int column) {
                return column == TableTypeEditorColumnInfo.UNIQUE.ordinal()
                        || column == TableTypeEditorColumnInfo.REQUIRED.ordinal()
                        || column == TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()
                        || column == TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal();
            }

            /**************************************************************************************
             * Override isCellEditable so that all columns can be edited
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column) {
                boolean isEditable = true;

                // Check if the table is displayable (to prevent corruption of the cell editor),
                // if the table model exists, and if the table has at least one row
                if (isDisplayable() && getModel() != null && getModel().getRowCount() != 0) {
                    // Create storage for the row of table data
                    Object[] rowData = new Object[getModel().getColumnCount()];

                    // Convert the view row and column indices to model coordinates
                    int modelRow = convertRowIndexToModel(row);
                    int modelColumn = convertColumnIndexToModel(column);

                    // Step through each column in the row
                    for (int index = 0; index < rowData.length; index++) {
                        // Store the column value into the row data array
                        rowData[index] = getModel().getValueAt(modelRow, index);
                    }

                    // Check if the cell is editable
                    isEditable = isDataAlterable(rowData, modelRow, modelColumn);
                }

                return isEditable;
            }

            /**************************************************************************************
             * Override isDataAlterable to determine which table data values can be changed
             *
             * @param rowData array containing the table row data
             *
             * @param row     table row index in model coordinates
             *
             * @param column  table column index in model coordinates
             *
             * @return true if the data value can be changed
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column) {
                // Set the flag to false if the column is one that doesn't allow changing the
                // structure or pointer allowed property
                boolean isAllowed = !(rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                        .equals(DefaultInputType.VARIABLE.getInputName())
                        || rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                .equals(DefaultInputType.PRIM_AND_STRUCT.getInputName())
                        || rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                .equals(DefaultInputType.ARRAY_INDEX.getInputName())
                        || rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                .equals(DefaultInputType.BIT_LENGTH.getInputName())
                        || inputTypeHandler
                                .getInputTypeByName(rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString())
                                .getInputFormat().equals(DefaultInputType.ENUMERATION.getInputFormat())
                        || rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                .equals(DefaultInputType.RATE.getInputName())
                        || rowData[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                .equals(DefaultInputType.VARIABLE_PATH.getInputName()));

                // Allow editing if:
                return
                // This is the column name or description column
                column == TableTypeEditorColumnInfo.NAME.ordinal()
                        || column == TableTypeEditorColumnInfo.DESCRIPTION.ordinal() || typeDefinition == null

                // This isn't the structure allowed column, or it is and the input type is one
                // that
                // allows the structure allowed property to be changed
                        || ((column != TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() || isAllowed)

                                // ... and this isn't the pointer allowed column, or it is and the input
                                // type is one that allows the pointer allowed property to be changed
                                && (column != TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() || isAllowed));
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method to prevent deleting the contents of the
             * cell at the specified row and column
             *
             * @param row    table row index in view coordinates
             *
             * @param column table column index in view coordinates
             *
             * @return false if the cell contains a combo box; true otherwise
             *************************************************************************************/
            @Override
            protected boolean isCellBlankable(int row, int column) {
                return convertColumnIndexToModel(column) != TableTypeEditorColumnInfo.INPUT_TYPE.ordinal();
            }

            /**************************************************************************************
             * Validate changes to the editable cells
             *
             * @param tableData   list containing the table data row arrays
             *
             * @param row         table model row index
             *
             * @param column      table model column index
             *
             * @param oldValue    original cell contents
             *
             * @param newValue    new cell contents
             *
             * @param showMessage true to display the invalid input dialog, if applicable
             *
             * @param isMultiple  true if this is one of multiple cells to be entered and
             *                    checked; false if only a single input is being entered
             *
             * @return Always returns false
             *************************************************************************************/
            @Override
            protected Boolean validateCellContent(List<Object[]> tableData, int row, int column, Object oldValue,
                    Object newValue, Boolean showMessage, boolean isMultiple) {
                // Reset the flag that indicates the last edited cell's content is invalid
                setLastCellValid(true);

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try {
                    // Check if the column name has been changed and if the name isn't blank
                    if (column == TableTypeEditorColumnInfo.NAME.ordinal() && !newValueS.isEmpty()) {
                        // Check if the name is too long
                        if (newValueS.length() >= MAX_SQL_NAME_LENGTH) {
                            // Inform the user that the name is too long
                            throw new CCDDException("Column name '</b>" + newValueS + "<b>' too long ("
                                    + (MAX_SQL_NAME_LENGTH - 1) + " characters maximum)");
                        }

                        // Check if the column name matches a default name (case insensitive)
                        if (newValueS.equalsIgnoreCase(DefaultColumn.PRIMARY_KEY.getDbName())
                                || newValueS.equalsIgnoreCase(DefaultColumn.ROW_INDEX.getDbName())) {
                            throw new CCDDException("Column name '</b>" + newValueS + "<b>' already in use (hidden)");
                        }

                        // Set the flag to true if the table type represents a structure
                        boolean isStructure = typeDefinition.isStructure();

                        // Get the database form of the column name
                        String dbName = tableTypeHandler.convertVisibleToDatabase(newValueS,
                                tableData.get(row)[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString(),
                                isStructure);

                        // Compare this column name to the others in the table in order to avoid
                        // creating a duplicate
                        for (int otherRow = 0; otherRow < getRowCount(); otherRow++) {
                            // Check if this row isn't the one being edited,
                            if (otherRow != row) {
                                // Check if the column name matches the one being added (case
                                // insensitive)
                                if (newValueS.equalsIgnoreCase(tableData.get(otherRow)[column].toString())) {
                                    throw new CCDDException("Column name '</b>" + newValueS + "<b>' already in use");
                                }

                                // Check if the database form of the column name matches matches
                                // the database form of the one being added
                                if (dbName.equalsIgnoreCase(tableTypeHandler.convertVisibleToDatabase(
                                        tableData.get(otherRow)[TableTypeEditorColumnInfo.NAME.ordinal()].toString(),
                                        tableData.get(otherRow)[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                                .toString(),
                                        isStructure))) {
                                    throw new CCDDException(
                                            "Column name '</b>" + newValueS + "<b>' already in use (database)");
                                }
                            }
                        }
                    }
                    // Check if a non-boolean value is being put into a cell that expects a boolean
                    // value
                    else if ((column == TableTypeEditorColumnInfo.UNIQUE.ordinal()
                            || column == TableTypeEditorColumnInfo.REQUIRED.ordinal()) && !newValue.equals(true)
                            && !newValue.equals(false)) {
                        throw new CCDDException("Column '</b>" + TableTypeEditorColumnInfo.getColumnNames()[column]
                                + "<b>' expects a boolean value");
                    }
                    // Check if this is the input type column
                    else if (column == TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()) {
                        // Check if the input type is disabled
                        if (newValueS.startsWith(DISABLED_TEXT_COLOR)) {
                            throw new CCDDException();
                        }

                        // Check if the input type is invalid
                        if (inputTypeHandler.getInputTypeByName(newValueS) == null) {
                            throw new CCDDException("Unknown input type '</b>" + newValueS + "<b>'");
                        }
                    }
                } catch (CCDDException ce) {
                    // Set the flag that indicates the last edited cell's content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage && !ce.getMessage().isEmpty()) {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(editorDialog, "<html><b>" + ce.getMessage(),
                                "Invalid Input", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value
                    tableData.get(row)[column] = oldValue;
                    table.getUndoManager().undoRemoveEdit();
                }

                return showMessage;
            }

            /**************************************************************************************
             * Load the table type definition values into the table and format the table
             * cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData() {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                int totalWidth = setUpdatableCharacteristics(committedData, TableTypeEditorColumnInfo.getColumnNames(),
                        null, TableTypeEditorColumnInfo.getToolTips(), true, true, true);

                // Get the minimum width needed to display all columns, but no wider than the
                // display
                int width = Math.min(totalWidth + LAF_SCROLL_BAR_WIDTH, GraphicsEnvironment
                        .getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth());

                // Check if this is the widest editor table in this tabbed editor dialog
                if (editorDialog.getTableWidth() < width) {
                    // Set the initial and preferred editor size
                    editorDialog.setTableWidth(width);
                    editorDialog.setPreferredSize(new Dimension(width, editorDialog.getPreferredSize().height));
                }
            }

            /**************************************************************************************
             * Override prepareRenderer to allow adjusting the background colors of table
             * cells
             *************************************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

                // Check if the cell doesn't have the focus or is selected. The focus and
                // selection
                // highlight colors override the invalid highlight color
                if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                        && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor()) {
                    boolean found = true;
                    String value = table.getValueAt(row, column).toString();

                    // Check if the cell is required and is empty
                    if (TableTypeEditorColumnInfo.values()[table.convertColumnIndexToModel(column)].isRequired()
                            && value.isEmpty()) {
                        // Set the flag indicating that the cell value is invalid
                        found = false;
                    }
                    // Check if this is the input type column
                    else if (column == inputTypeIndex && comboBox != null) {
                        found = false;

                        // Step through each combo box item
                        for (int index = 0; index < comboBox.getItemCount() && !found; index++) {
                            // Check if the cell matches the combo box item. Remove any HTML tags
                            // in case this item is displayed as disabled
                            if (CcddUtilities.removeHTMLTags(comboBox.getItemAt(index)).equals(value)) {
                                // Set the flag indicating that the cell value is valid and stop
                                // searching
                                found = true;
                                break;
                            }
                        }
                    }

                    // Check if the cell value is invalid
                    if (!found) {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                    }
                    // Check if this cell is protected from changes
                    else if (!isCellEditable(row, column)) {
                        // Change the cell's text and background colors
                        comp.setForeground(ModifiableColorInfo.PROTECTED_TEXT.getColor());
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                    }
                    // Check if the cell is in a column that is required in order to define the
                    // table type as a structure or command table
                    else if (DefaultColumn.isTypeRequiredColumn(typeOfTable, inputTypeHandler,
                            inputTypeHandler.getInputTypeByName(table.getValueAt(row, inputTypeIndex).toString()))) {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.TYPE_REQUIRED_BACK.getColor());
                    }
                }

                return comp;
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method to produce an array containing empty
             * values for a new row in this table
             *
             * @return Array containing blank cell values for a new row
             *************************************************************************************/
            @Override
            protected Object[] getEmptyRow() {
                return TableTypeEditorColumnInfo.getEmptyRow();
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange() {
                // Get the table type based on the column definition input types
                String typeOfTableNew = getTypeOfTable();

                // Check if the table type changed
                if (typeOfTableNew != typeOfTable) {
                    // Store the new table type
                    typeOfTable = typeOfTableNew;

                    // Show/hide the structure table type specific editor columns
                    table.updateHiddenColumns();
                }

                // Check if the table type represents a structure
                if (typeOfTableNew.equals(TYPE_STRUCTURE)) {
                    // Show the command argument selection check box
                    cmdArgStructureCbx.setVisible(true);

                    // Step through each row (column definition) in the table
                    for (int row = 0; row < table.getModel().getRowCount(); row++) {
                        // Get the reference to the table model to shorten subsequent calls
                        UndoableTableModel tableModel = (UndoableTableModel) table.getModel();

                        // Get the column definition's input type
                        InputType inputType = inputTypeHandler.getInputTypeByName(
                                tableModel.getValueAt(row, TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()).toString());

                        // Check if this column represents the variable name, data type, array
                        // size, bit length, enumeration, or variable path
                        if (inputType != null && (inputType
                                .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.VARIABLE))
                                || inputType.equals(
                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.PRIM_AND_STRUCT))
                                || inputType.equals(
                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ARRAY_INDEX))
                                || inputType
                                        .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.BIT_LENGTH))
                                || inputType.equals(
                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ENUMERATION))
                                || inputType.equals(
                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.VARIABLE_PATH))
                                || inputType
                                        .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.RATE)))) {
                            // Set the structure and pointer allowed check boxes based on the
                            // default column definition
                            tableModel.setValueAt(
                                    typeDefinition.isStructureAllowed()[typeDefinition
                                            .getColumnIndexByInputType(inputType)],
                                    row, TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal(), false);
                            tableModel.setValueAt(
                                    typeDefinition.isPointerAllowed()[typeDefinition
                                            .getColumnIndexByInputType(inputType)],
                                    row, TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal(), false);
                        }
                    }
                }
                // The table type doesn't represent a structure
                else {
                    // Hide the command argument selection check box
                    cmdArgStructureCbx.setVisible(false);
                }

                // Update the change indicator for the table
                editorDialog.updateChangeIndicator(CcddTableTypeEditorHandler.this);
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);

        // Set common table parameters and characteristics
        table.setFixedCharacteristics(scrollPane, true, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                TableSelectionMode.SELECT_BY_CELL, false, ModifiableColorInfo.TABLE_BACK.getColor(), true, true,
                ModifiableFontInfo.DATA_TABLE_CELL.getFont(), true);

        // Create a drop-down combo box to display the available table type input types
        setUpInputTypeColumn();

        // Set the reference to the editor's data field handler in the undo handler so
        // that data
        // field value changes can be undone/redone correctly
        table.getUndoHandler().setFieldHandler(fieldHandler);

        // Set the undo/redo manager and handler for the description and data field
        // values
        setEditPanelUndo(table.getUndoManager(), table.getUndoHandler());

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

        // Add a check box for selecting if a structure represents command arguments
        JPanel editorPnl = new JPanel(new GridBagLayout());
        cmdArgStructureCbx = new JCheckBox("Structure represents command arguments",
                typeDefinition.isCommandArgumentStructure());
        cmdArgStructureCbx.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        cmdArgStructureCbx.setBorder(BorderFactory.createEmptyBorder());
        cmdArgStructureCbx.setToolTipText(
                CcddUtilities.wrapText("Structures based on this type contain refernces to command arguments",
                        ModifiableSizeInfo.MAX_TOOL_TIP_LENGTH.getSize()));
        cmdArgStructureCbx.setVisible(getTypeOfTable().equals(TYPE_STRUCTURE));

        // Add the table and the checkbox to the panel
        editorPnl.add(scrollPane, gbc);
        gbc.insets.top = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2;
        gbc.weighty = 0.0;
        gbc.gridy++;
        editorPnl.add(cmdArgStructureCbx, gbc);

        // Create the input field panel to contain the type editor
        createDescAndDataFieldPanel(ccddMain, editorDialog, editorPnl, CcddFieldHandler.getFieldTypeName(tableTypeName),
                committedDescription, committedFieldInfo);

        // Set the JTable name so that table change events can be identified with this
        // table
        setTableTypeName(tableTypeName);
    }

    /**********************************************************************************************
     * Get the table type based on the column definition input types
     *
     * @return Table type: TYPE_STRUCTURE for a structure table, TYPE_COMMAND for a
     *         command table, or TYPE_OTHER for any other table type
     *********************************************************************************************/
    private String getTypeOfTable() {
        // Set the flags that indicate if the table type currently represents a
        // structure or a
        // command
        boolean isStructure = true;
        boolean isCommand = true;

        // Step through the target table types: Structure and Command
        for (String tableType : new String[] { TYPE_STRUCTURE, TYPE_COMMAND }) {
            // Step through each of the default columns
            for (DefaultColumn defColumn : DefaultColumn.values()) {
                // Check if this column belongs to the target table type and that it is a
                // protected
                // column
                if (defColumn.getTableType().equals(tableType) && defColumn.isProtected()) {
                    boolean isFound = false;

                    // Step through each row in the table
                    for (int tableRow = 0; tableRow < table.getModel().getRowCount(); tableRow++) {
                        // Check if the input type column value matches the target table type input
                        // type
                        if (table.getModel().getValueAt(tableRow, TableTypeEditorColumnInfo.INPUT_TYPE.ordinal())
                                .toString().equals(defColumn.getInputType().getInputName())) {
                            // Set the flag to indicate the target table type input type is in use
                            // and stop searching
                            isFound = true;
                            break;
                        }
                    }

                    // Check if a target table type input type is missing
                    if (!isFound) {
                        // Check if the target table type represents a structure
                        if (tableType.equals(TYPE_STRUCTURE)) {
                            // Set the flag to indicate that this table type doesn't have all of
                            // the structure type's columns
                            isStructure = false;
                        }
                        // The target table type represents a command
                        else {
                            // Set the flag to indicate that this table type doesn't have all of
                            // the command type's columns
                            isCommand = false;
                        }

                        break;
                    }
                }
            }
        }

        return isStructure ? TYPE_STRUCTURE : (isCommand ? TYPE_COMMAND : TYPE_OTHER);
    }

    /**********************************************************************************************
     * Get the input types that are defined as unique, but are referenced by more
     * than one column definition
     *
     * @return true if there are duplicated input types that are defined as unique
     *********************************************************************************************/
    protected boolean isInvalidInputTypes() {
        boolean isBadType = false;
        String invalidTypes = "";

        // Get the table type
        String tableType = getTypeOfTable();

        // Check if the table type currently represents a structure or command
        if (!tableType.equals(TYPE_OTHER)) {
            // Get the table type data array
            Object[][] typeData = table.getTableData(true);
            boolean[] checkedRow = new boolean[typeData.length];

            // Step through each row (column definition) in the table type
            for (int row = 0; row < typeData.length - 1; row++) {
                // Set the flag indicating this row has been checked
                checkedRow[row] = true;

                // Get the column definition's input type
                String inputType = typeData[row][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();

                // Check if the input type must be unique for the target table type
                if (DefaultColumn.isInputTypeUnique(tableType, inputType)) {
                    // Step through the remaining row (column definitions)
                    for (int remRow = row + 1; remRow < typeData.length; remRow++) {
                        // Check if the row hasn't already been found to be a duplicate and if the
                        // input type for the current row matches the input type for this row
                        if (!checkedRow[remRow] && inputType
                                .equals(typeData[remRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString())) {
                            // This is a duplicate of an input type that must be unique. Add the
                            // type to the list and set the flag indicating the row has been found
                            // to be invalid
                            invalidTypes += inputType + ", ";
                            checkedRow[remRow] = true;
                        }
                    }
                }
            }

            // Check if an invalid input type exists
            if (!invalidTypes.isEmpty()) {
                // Set the flag to indicate the table type definition has an invalid input type
                isBadType = true;

                // Inform the user that the input value is invalid
                new CcddDialogHandler().showMessageDialog(editorDialog,
                        "<html><b>" + "Tables of type '" + tableType + "' may not have more than one column with "
                                + "input type(s):</b><br>&#160;&#160;&#160;"
                                + CcddUtilities.removeTrailer(invalidTypes, ", "),
                        "Invalid Input", JOptionPane.WARNING_MESSAGE, DialogOption.OK_OPTION);

            }
        }

        return isBadType;
    }

    /**********************************************************************************************
     * Set up the combo box containing the available table type input types for
     * display in the table's Input Type cells
     *********************************************************************************************/
    private void setUpInputTypeColumn() {
        // Create a combo box for displaying table type input types
        comboBox = new PaddedComboBox(inputTypeHandler.getInputTypeNames(), inputTypeHandler.getDescriptions(),
                ModifiableFontInfo.DATA_TABLE_CELL.getFont());

        // Get the index of the input type column in view coordinates
        inputTypeIndex = table.convertColumnIndexToView(TableTypeEditorColumnInfo.INPUT_TYPE.ordinal());

        // Enable item matching for the combo box
        comboBox.enableItemMatching(table);

        // Set the column table editor to the combo box
        table.getColumnModel().getColumn(inputTypeIndex).setCellEditor(new ComboBoxCellEditor(comboBox));

        // Set the default selected type
        comboBox.setSelectedItem(DefaultInputType.TEXT.getInputName());
    }

    /**********************************************************************************************
     * Determine if any changes have been made compared to the most recently
     * committed table data
     *
     * @return true if any cell in the table has been changed, if the column order
     *         has changed, or if the table description has changed
     *********************************************************************************************/
    protected boolean isTableChanged() {
        return table.isTableChanged(committedData);
    }

    /**********************************************************************************************
     * Compare the current table type data to the committed table type data and
     * create lists of the changed values necessary to update the table definitions
     * table in the database to match the current values
     *
     * @throws CCDDException If a data field name will cause an existing table's
     *                       field to be renamed and the user elects to cancel the
     *                       update
     *********************************************************************************************/
    protected void buildUpdates() throws CCDDException {
        // ////////////////////////////////////////////////////////////////////////////////////////
        // Build the changes to the table type's column definitions
        // ////////////////////////////////////////////////////////////////////////////////////////
        // Get the table type data array
        Object[][] typeData = table.getTableData(true);

        // Create/replace the type definition. The description is prepended with a '0' is the table
        // type doesn't represent a command argument structure, and a '1' if it does
        tableTypeHandler.createReplaceTypeDefinition(tableTypeName,
                (cmdArgStructureCbx != null && cmdArgStructureCbx.isSelected() ? "1" : "0") + getDescription(),
                typeData);

        // Remove existing changes, if any
        typeAdditions.clear();
        typeModifications.clear();
        typeDeletions.clear();

        // Initialize the column order change status
        columnOrderChange = false;

        // Create an empty row of data for comparison purposes
        Object[] emptyRow = table.getEmptyRow();

        // Create storage for flags that indicate if a row has been modified
        boolean[] rowModified = new boolean[committedData.length];

        // Step through each row of the current data
        for (int tblRow = 0; tblRow < typeData.length; tblRow++) {
            boolean matchFound = false;

            // Get the current column name
            String currColumnName = typeData[tblRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

            // Step through each row of the committed data
            for (int comRow = 0; comRow < committedData.length; comRow++) {
                // Get the previous column name
                String prevColumnName = committedData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

                // Check if the committed row hasn't already been matched and if the current and
                // committed column indices are the same
                if (!rowModified[comRow] && typeData[tblRow][TableTypeEditorColumnInfo.INDEX.ordinal()]
                        .equals(committedData[comRow][TableTypeEditorColumnInfo.INDEX.ordinal()])) {
                    // Set the flag indicating this row has a match
                    matchFound = true;

                    // Copy the current row's index into the empty comparison row so that the otherwise
                    // blank index doesn't register as a difference when comparing the rows below
                    emptyRow[TableTypeEditorColumnInfo.INDEX.ordinal()] =
                            typeData[tblRow][TableTypeEditorColumnInfo.INDEX.ordinal()];

                    // Check if the row is not now empty (if empty then the change is processed as
                    // a row deletion instead of a modification)
                    if (!Arrays.equals(typeData[tblRow], emptyRow)) {
                        // Set the flag indicating this row has a modification
                        rowModified[comRow] = true;

                        // Check if the previous and current column definition row is different
                        if (tblRow != comRow) {
                            // Set the flag indicating the column order changed
                            columnOrderChange = true;
                        }

                        // Get the original and current input type
                        String oldInputType = committedData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();
                        String newInputType = typeData[tblRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();

                        // Check if the column name changed or if the input type changed to/from a rate
                        if (!prevColumnName.equals(currColumnName)
                                || ((newInputType.equals(DefaultInputType.RATE.getInputName())
                                        || oldInputType.equals(DefaultInputType.RATE.getInputName()))
                                        && !newInputType.equals(oldInputType))) {
                            // The column name is changed. Add the old and new column names and input types to the list
                            typeModifications.add(new String[] { prevColumnName, currColumnName, oldInputType, newInputType });
                        }

                        // Stop searching since a match exists
                        break;
                    }
                }
            }

            // Check if no match was made with the committed data for the current table row
            if (!matchFound) {
                // The column definition is being added; add the column name and input type to the list
                typeAdditions.add(new String[] { currColumnName,
                        typeData[tblRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString() });
            }
        }

        // Step through each row of the committed data
        for (int comRow = 0; comRow < committedData.length; comRow++) {
            // Check if no matching row was found with the current data
            if (!rowModified[comRow]) {
                // The column definition has been deleted; add the column name and input type to the list
                typeDeletions.add(new String[] { committedData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString(),
                                committedData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString() });
            }
        }
    }

    /**********************************************************************************************
     * Check that a row with contains data in the required columns
     *
     * @return true if a row is missing data in a required column
     *********************************************************************************************/
    protected boolean checkForMissingColumns() {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Step through each row in the table
        for (int row = 0; row < table.getRowCount() && !stopCheck; row++) {
            // Skip rows in the table that are empty
            row = table.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < table.getRowCount()) {
                // Step through each column in the row
                for (int column = 0; column < table.getColumnCount() && !stopCheck; column++) {
                    // Check if the cell is required and is empty
                    if (column == table.convertColumnIndexToView(TableTypeEditorColumnInfo.NAME.ordinal())
                            && table.getValueAt(row, column).toString().isEmpty()) {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(editorDialog,
                                "<html><b>Data must be provided for column '</b>" + table.getColumnName(column)
                                        + "<b>' [row </b>" + (row + 1) + "<b>]",
                                "Missing Data", JOptionPane.WARNING_MESSAGE,
                                DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON) {
                            // Set the stop flag to prevent further error checking
                            stopCheck = true;
                        }

                        break;
                    }
                }
            }
        }

        return dataIsMissing;
    }

    /**********************************************************************************************
     * Update the tab for this table in the table editor dialog change indicator
     *********************************************************************************************/
    @Override
    protected void updateOwnerChangeIndicator() {
        editorDialog.updateChangeIndicator(this);
    }
}
