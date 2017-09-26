/**
 * CFS Command & Data Dictionary table editor handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.HIDE_DATA_TYPE;
import static CCDD.CcddConstants.IGNORE_BUTTON;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.REPLACE_INDICATOR;
import static CCDD.CcddConstants.TYPE_NAME_SEPARATOR;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.VARIABLE_PATH_SEPARATOR;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.MinMaxPair;
import CCDD.CcddClasses.PaddedComboBox;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableModification;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddUndoHandler.UndoableTableModel;

/******************************************************************************
 * CFS Command & Data Dictionary table editor handler class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddTableEditorHandler extends CcddInputFieldPanelHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddTableEditorDialog editorDialog;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDataTypeHandler newDataTypeHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddMacroHandler newMacroHandler;
    private CcddJTableHandler table;
    private final TableInformation tableInfo;
    private UndoableTableModel tableModel;
    private CcddTableTreeHandler tableTree;
    private TypeDefinition typeDefn;

    // Cell editor for data type cell in a row that has an enumeration
    private DefaultCellEditor enumDataTypeCellEditor;

    // List containing associated data type and enumeration pairings, and
    // command argument name, data type, enumeration, minimum, and maximum
    // column groupings
    private List<AssociatedColumns> associatedColumns;

    // List containing associated minimum and maximum column indices not
    // already associated with a command argument
    private List<MinMaxPair> minMaxPair;

    // Array containing the names of all structure tables
    private String[] allStructureTables;

    // Column header tool tip text
    private String[] toolTips;

    // Table information as it exists in the database and is used to determine
    // what changes have been made to the table since the previous database
    // update
    private TableInformation committedInfo;

    // Column indices for the variable name, data type, array size, bit length,
    // enumeration, rate columns, and variable path (in model coordinates), if
    // present. Also set the row index and primary key column indices
    private int variableNameIndex;
    private int dataTypeIndex;
    private int arraySizeIndex;
    private int bitLengthIndex;
    private List<Integer> enumerationIndex;
    private List<Integer> rateIndex;
    private final int rowIndex;
    private final int primaryKeyIndex;
    private int variablePathIndex;

    // Variable path separators and flag to show/hide the data type
    private String varPathSep;
    private String typeNameSep;
    private boolean hideDataType;

    // Start, end, and target row indices, in model coordinates, for moving
    // rows
    private int modelStartRow;
    private int modelEndRow;
    private int modelToRow;

    // Flag indicating if array members are to be displayed in the table
    private boolean isShowArrayMembers;

    // Flag indicating a rate value changed while checking packed bit-wise
    // variables
    private boolean isRateChange;

    // Row filter, used to show/hide array members
    private RowFilter<TableModel, Object> rowFilter;

    // Temporary table cell storage for when macro names are replaced by their
    // corresponding values so that the original cell contents can be restored
    private String[][] originalCellData;

    // Lists of table content changes to process
    private final List<TableModification> additions;
    private final List<TableModification> modifications;
    private final List<TableModification> deletions;

    // Flag indicating if the table can be edited
    private boolean isEditEnabled;

    /**************************************************************************
     * Table editor handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param newDataTypeHandler
     *            data type handler reference for when the data type
     *            definitions have changed; same as the main data type handler
     *            if this is not a data type change instance
     *
     * @param newMacroHandler
     *            macro handler reference for when the macro names and/or
     *            values have changed; same as the main macro handler if this
     *            is not a macro change instance
     *
     * @param editorDialog
     *            editor dialog from which this editor was created
     *************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddDataTypeHandler newDataTypeHandler,
                                     CcddMacroHandler newMacroHandler,
                                     CcddTableEditorDialog editorDialog)
    {
        this.ccddMain = ccddMain;
        this.tableInfo = tableInfo;
        this.newDataTypeHandler = newDataTypeHandler;
        this.newMacroHandler = newMacroHandler;
        this.editorDialog = editorDialog;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        rateHandler = ccddMain.getRateParameterHandler();

        // Initialize the lists of table content changes
        additions = new ArrayList<TableModification>();
        modifications = new ArrayList<TableModification>();
        deletions = new ArrayList<TableModification>();

        // Set the row index and primary key column indices
        rowIndex = DefaultColumn.ROW_INDEX.ordinal();
        primaryKeyIndex = DefaultColumn.PRIMARY_KEY.ordinal();

        // Initialize the structure table information
        tableTree = null;
        allStructureTables = null;

        // Set the flag to indicate that editing of the table is allowed
        isEditEnabled = true;

        // Create the table editor
        initialize();
    }

    /**************************************************************************
     * Table editor handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param editorDialog
     *            editor dialog from which this editor was created
     *************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddTableEditorDialog editorDialog)
    {
        this(ccddMain,
             tableInfo,
             ccddMain.getDataTypeHandler(),
             ccddMain.getMacroHandler(),
             editorDialog);
    }

    /**************************************************************************
     * Table editor handler class constructor for a primitive data type name,
     * size, and/or base type change
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param newDataTypeHandler
     *            data type handler reference reflecting updates to the data
     *            type definitions
     *************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddDataTypeHandler newDataTypeHandler)
    {
        this(ccddMain,
             tableInfo,
             newDataTypeHandler,
             ccddMain.getMacroHandler(),
             null);
    }

    /**************************************************************************
     * Table editor handler class constructor for a macro name and/or value
     * change
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param newMacroHandler
     *            macro handler reference reflecting updates to the macro names
     *            and/or values
     *************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddMacroHandler newMacroHandler)
    {
        this(ccddMain,
             tableInfo,
             ccddMain.getDataTypeHandler(),
             newMacroHandler,
             null);
    }

    /**************************************************************************
     * Get the table being edited by this dialog
     *
     * @return Table edited by the dialog
     *************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return table;
    }

    /**************************************************************************
     * Check if this table represents a structure
     *
     * @return true if the table represents a structure
     *************************************************************************/
    protected boolean isStructure()
    {
        return typeDefn.isStructure();
    }

    /**************************************************************************
     * Enable/disable editing of the table
     *
     * @param true to enable normal editing of the table, false to disable
     *        editing
     *************************************************************************/
    protected void setTableEditEnable(boolean enable)
    {
        isEditEnabled = enable;
    }

    /**************************************************************************
     * Update the table information (current and committed) if a change is made
     * to the prototype structure name or a variable name in the structure's
     * prototype
     *
     * @param oldPrototype
     *            original prototype name
     *
     * @param newPrototype
     *            updated prototype name
     *
     * @param oldVariableName
     *            original variable name; null if no variable name update
     *
     * @param newVariableName
     *            updated variable name; null if no variable name update
     *************************************************************************/
    protected void updateTableInformation(String oldPrototype,
                                          String newPrototype,
                                          String oldVariableName,
                                          String newVariableName)
    {
        // Update the table's parent name
        tableInfo.setRootTable(tableInfo.getRootTable().replaceAll("^"
                                                                   + Pattern.quote(oldPrototype)
                                                                   + "$",
                                                                   newPrototype));

        // Update the data type (prototype name) in the table path
        tableInfo.setTablePath(tableInfo.getTablePath().replaceAll(","
                                                                   + Pattern.quote(oldPrototype)
                                                                   + "\\.",
                                                                   ","
                                                                       + newPrototype
                                                                       + "."));

        // Check if a variable name changed
        if (oldVariableName != null)
        {
            // Update the variable name in the table path
            tableInfo.setTablePath(tableInfo.getTablePath().replaceAll("\\."
                                                                       + Pattern.quote(oldVariableName)
                                                                       + "($|,)", "."
                                                                                  + newVariableName
                                                                                  + "$1"));

            // Update the committed table information
            committedInfo.setTablePath(tableInfo.getTablePath());
        }

        // Update the committed table information
        committedInfo.setRootTable(tableInfo.getRootTable());
    }

    /**************************************************************************
     * Update the data type column to the new prototype structure name for any
     * references to the original prototype structure name, if this table
     * represents a structure. The update is treated as a committed change
     *
     * @param oldPrototype
     *            original prototype name
     *
     * @param newPrototype
     *            updated prototype name
     *************************************************************************/
    protected void updateDataTypeReferences(String oldPrototype,
                                            String newPrototype)
    {
        // Check if the table is a structure and has a data type column
        if (typeDefn.isStructure() && dataTypeIndex != -1)
        {
            // Step through each row in the table
            for (int row = 0; row < tableModel.getRowCount(); row++)
            {
                // Check if the data type matches the old prototype name
                if (tableModel.getValueAt(row, dataTypeIndex).toString().equals(oldPrototype))
                {
                    // Replace the data type with the new data type name. Treat
                    // the update as having been committed to the database
                    tableModel.setValueAt(newPrototype, row, dataTypeIndex, false);
                    committedInfo.getData()[row][dataTypeIndex] = newPrototype;
                }
            }
        }
    }

    /**************************************************************************
     * Set the table name to the table's prototype + variable name
     *************************************************************************/
    protected void setTableName()
    {
        // Get the table's prototype/variable name to shorten subsequent calls
        String name = tableInfo.getProtoVariableName();

        // Set the name of the table editor's owner
        setOwnerName(name.equals(tableInfo.getRootTable())
                                                          ? name
                                                          : tableInfo.getRootTable()
                                                            + ": "
                                                            + name);

        // Check that the table is open in a table editor (versus open for a
        // macro name and/or value change)
        if (editorDialog != null)
        {
            // Set the dialog name so that this dialog can be recognized as
            // being open by the table selection dialog
            editorDialog.setName(name);
        }

        // Set the JTable name so that table change events can be identified
        // with this table
        table.setName(getOwnerName());

        // Check if the table has uncommitted changes
        if (isTableChanged())
        {
            // Send a change event so that the editor tab name reflects that
            // the table has changed
            table.getUndoManager().ownerHasChanged();
        }
    }

    /**************************************************************************
     * Set the table type definition
     *************************************************************************/
    protected void setTypeDefinition()
    {
        // Get the table type definition
        typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

        // Set the column tool tip text
        toolTips = typeDefn.getColumnToolTips();
    }

    /**************************************************************************
     * Get the table's tab tool tip text, which consists of the table's type
     * and full path
     *
     * @return Table's tab tool tip text
     *************************************************************************/
    protected String getTableToolTip()
    {
        String pathTag = "<b>Full path:</b> ";

        // Build the tool tip text, showing the table type and its full path
        String toolTip = "<html><b>Table type:</b> "
                         + tableInfo.getType()
                         + "<br>"
                         + pathTag
                         + tableInfo.getTablePath();

        // Create the indentation text; hide this text by coloring it the same
        // as the background
        String indent = "<br>"
                        + CcddUtilities.colorHTMLText(pathTag,
                                                      ModifiableColorInfo.TOOL_TIP_BACK.getColor())
                        + "&#160;&#160;";

        // Step through each member in the table path
        while (toolTip.contains(","))
        {
            // Replace the separating comma with the indentation text
            toolTip = toolTip.replaceFirst(",", indent);

            // Increase the indent for the next member
            indent += "&#160;&#160;";
        }

        return toolTip;
    }

    /**************************************************************************
     * Get the table model
     *
     * @return Table model
     *************************************************************************/
    protected UndoableTableModel getTableModel()
    {
        return tableModel;
    }

    /**************************************************************************
     * Get the current table information
     *
     * NOTE: The table cell data does not reflect changes made to the table
     * contents
     *
     * @return Table information class for extracting the current table name,
     *         type, and column order
     *************************************************************************/
    protected TableInformation getTableInformation()
    {
        return tableInfo;
    }

    /**************************************************************************
     * Get the committed table information
     *
     * @return Committed table information class
     *************************************************************************/
    protected TableInformation getCommittedTableInformation()
    {
        return committedInfo;
    }

    /**************************************************************************
     * Set the committed table information
     *
     * @param info
     *            table information class for extracting the current table
     *            name, type, column order, and description
     *************************************************************************/
    private void setCommittedInformation(TableInformation info)
    {
        committedInfo = new TableInformation(info.getType(),
                                             info.getTablePath(),
                                             info.getData(),
                                             info.getColumnOrder(),
                                             info.getDescription(),
                                             info.isRootStructure(),
                                             (editorDialog != null
                                                                  ? info.getFieldHandler().getFieldInformationCopy()
                                                                  : null));

        // (Re)create the variable path column content, if present
        updateVariablePaths();

        // Check if the table has been created
        if (table != null)
        {
            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**************************************************************************
     * Update the variable path column, if present, to use the separators
     * currently stored in the program preferences
     *************************************************************************/
    protected void updateVariablePaths()
    {
        // Check if the variable path column is present and the table
        // represents a structure
        if (variablePathIndex != -1 && typeDefn.isStructure())
        {
            // Get the variable path separators and the show/hide data type
            // flag from the program preferences
            varPathSep = ccddMain.getProgPrefs().get(VARIABLE_PATH_SEPARATOR, "_");
            typeNameSep = ccddMain.getProgPrefs().get(TYPE_NAME_SEPARATOR, "_");
            hideDataType = Boolean.parseBoolean(ccddMain.getProgPrefs().get(HIDE_DATA_TYPE, "false"));

            // Check if the table has been created
            if (table != null)
            {
                // Step through each row of the data
                for (int row = 0; row < tableModel.getRowCount(); row++)
                {
                    // Get the variable name and data type from the table data
                    String variableName = tableModel.getValueAt(row,
                                                                variableNameIndex).toString();
                    String dataType = tableModel.getValueAt(row,
                                                            dataTypeIndex).toString();

                    // Check that the variable name and data type are present
                    if (!variableName.isEmpty() && !dataType.isEmpty())
                    {
                        // Build the variable path and store it in the table
                        // model's variable path column
                        tableModel.setValueAt(getVariablePath(variableName, dataType),
                                              row,
                                              variablePathIndex);
                    }
                }
            }
            // The table hasn't been created (e.g., when the editor is
            // initializing)
            else
            {
                // Step through each row of the data
                for (int row = 0; row < committedInfo.getData().length; row++)
                {
                    // Get the variable name and data type from the committed
                    // data
                    String variableName = committedInfo.getData()[row][variableNameIndex];
                    String dataType = committedInfo.getData()[row][dataTypeIndex];

                    // Check that the variable name and data type are present
                    if (!variableName.isEmpty() && !dataType.isEmpty())
                    {
                        // Build the variable path and store it in the
                        // committed data's variable path column
                        committedInfo.getData()[row][variablePathIndex] = getVariablePath(variableName,
                                                                                          dataType);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Get the variable path for the specified variable name and data type
     *
     * @param variableName
     *            variable name
     *
     * @param dataType
     *            data type
     *
     * @return Variable path for the specified variable name and data type
     *************************************************************************/
    private String getVariablePath(String variableName, String dataType)
    {
        return tableInfo.getTablePath()
               + varPathSep
               + (hideDataType
                              ? ""
                              : dataType
                                + typeNameSep)
               + variableName;
    }

    /**************************************************************************
     * Get the UndoManager for this table editor
     *
     * @return Table UndoManager
     *************************************************************************/
    @Override
    protected CcddUndoManager getFieldPanelUndoManager()
    {
        return table.getUndoManager();
    }

    /**************************************************************************
     * Get the table's row additions
     *
     * @return List of table additions
     *************************************************************************/
    protected List<TableModification> getAdditions()
    {
        return additions;
    }

    /**************************************************************************
     * Get the table's row modifications
     *
     * @return List of table modifications
     *************************************************************************/
    protected List<TableModification> getModifications()
    {
        return modifications;
    }

    /**************************************************************************
     * Get the table's row deletions
     *
     * @return List of table deletions
     *************************************************************************/
    protected List<TableModification> getDeletions()
    {
        return deletions;
    }

    /**************************************************************************
     * Get the table's array expansion flag state
     *
     * @return Table's array expansion flag state
     *************************************************************************/
    protected boolean isExpanded()
    {
        return isShowArrayMembers;
    }

    /**************************************************************************
     * Check if the table has variable name and array index columns
     *
     * @return true if the table has variable name and array index columns
     *************************************************************************/
    protected boolean isCanHaveArrays()
    {
        return variableNameIndex != -1 && arraySizeIndex != -1;
    }

    /**************************************************************************
     * Get the model column indices containing the variable name, data type,
     * array size, array index, enumeration(s), and sample rate(s), if extant.
     * This must be called when the table editor is first instantiated and
     * whenever the table's type definition is altered
     *************************************************************************/
    private void getSpecialColumnIndices()
    {
        // Check if the table represents a structure
        if (typeDefn.isStructure())
        {
            variableNameIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
            dataTypeIndex = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
            arraySizeIndex = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
            bitLengthIndex = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
            enumerationIndex = typeDefn.getColumnIndicesByInputType(InputDataType.ENUMERATION);
            rateIndex = typeDefn.getColumnIndicesByInputType(InputDataType.RATE);
        }
        // The table doesn't represent a structure
        else
        {
            // Set the special indices to invalid column values. Only structure
            // tables get special handling for these columns
            variableNameIndex = -1;
            dataTypeIndex = -1;
            arraySizeIndex = -1;
            bitLengthIndex = -1;
            enumerationIndex = new ArrayList<Integer>();
            rateIndex = new ArrayList<Integer>();
        }

        // Set the variable path column index. This column is only active for a
        // structure table, but can appear in other table types (if the column
        // is added to a structure type and then the type is altered to not be
        // a structure)
        variablePathIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE_PATH);
    }

    /**************************************************************************
     * Update the table editor following a change to the table's type
     * definition
     *
     * @param tableInfo
     *            table information
     *************************************************************************/
    protected void updateForTableTypeChange(TableInformation tableInfo)
    {
        // Update the editor's type definition reference
        setTypeDefinition();

        // Get the model column indices for columns with special input types
        getSpecialColumnIndices();

        // Update the committed table information
        setCommittedInformation(tableInfo);

        // Update the table editor contents
        table.loadAndFormatData();

        // Update the current field information
        getDataFieldHandler().setFieldInformation(tableInfo.getFieldHandler().getFieldInformation());

        // Update the editor data fields
        updateDataFields();

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /******************************************************************
             * Execute after all pending Swing events are finished
             *****************************************************************/
            @Override
            public void run()
            {
                // Force the table to redraw in case the number of columns
                // changed
                tableModel.fireTableStructureChanged();
                tableModel.fireTableDataChanged();
            }
        });
    }

    /**************************************************************************
     * Remove the custom value deletion flag in a cell, if present
     *************************************************************************/
    protected void clearCustomValueDeletionFlags()
    {
        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Step through each column of the updated row data
            for (int column = 0; column < tableModel.getColumnCount(); column++)
            {
                // Get the cell value
                String cellValue = tableModel.getValueAt(row, column).toString();

                // Check if the cell value begins with the flag that indicates
                // the custom value for this cell was deleted
                if (cellValue.startsWith(REPLACE_INDICATOR))
                {
                    // Get this cell's editor component
                    Component comp = ((DefaultCellEditor) table.getCellEditor(table.convertRowIndexToView(row),
                                                                              table.convertColumnIndexToView(column))).getComponent();

                    // Check if the cell contains a combo box
                    if (comp instanceof JComboBox)
                    {
                        // Remove the specially flagged item from the combo box
                        // list
                        ((JComboBox<?>) comp).removeItem(cellValue);
                    }

                    // Remove the flag from the cell value
                    cellValue = cellValue.replaceFirst("^" + REPLACE_INDICATOR, "");

                    // Store the value in the cell without the flag
                    tableModel.setValueAt(cellValue, row, column);
                    committedInfo.getData()[row][column] = cellValue;
                }
            }
        }
    }

    /**************************************************************************
     * Perform the steps needed following execution of database table changes
     *
     * @param keys
     *            list of primary key values for each row in the table
     *
     * @param applyToChild
     *            true if the table that was updated is a prototype and this
     *            table is a child of the updated table
     *
     * @param additions
     *            list of row addition information
     *
     * @param modifications
     *            list of row update information
     *
     * @param deletions
     *            list of row deletion information
     *************************************************************************/
    protected void doTableUpdatesComplete(List<Integer> keys,
                                          boolean applyToChild,
                                          List<TableModification> additions,
                                          List<TableModification> modifications,
                                          List<TableModification> deletions)
    {
        List<Object[]> committedData = null;

        // Check if a cell editor is active
        if (table.getCellEditor() != null)
        {
            // Terminate cell editing. If this is not done the cell with the
            // active editor isn't updated
            table.getCellEditor().stopCellEditing();
        }

        // Check if this is a child table to a prototype that has been updated;
        // if so this table needs to have the same changes applied
        if (applyToChild)
        {
            // Get the child table's committed data so that it can be updated
            // along with the table. This allows the changes made to the child
            // (as a result of the changes to its prototype) to be considered
            // as already stored in the database. Any other changes made
            // specifically to the child table are still indicated as not being
            // stored
            committedData = new ArrayList<Object[]>(Arrays.asList((Object[][]) committedInfo.getData()));

            // Step through each row added to the table
            for (TableModification add : additions)
            {
                // Get the index at which to insert the new row
                int row = Integer.valueOf(add.getRowData()[rowIndex].toString()) - 1;

                // Insert the new table row and stop searching
                tableModel.insertRow(row, add.getRowData(), false);
                committedData.add(row, add.getRowData());
            }

            // If the row order in the prototype changed then the row order of
            // the current table must be changed to match the new order prior
            // to comparing the column changes per the modifications. Step
            // through each row in the table
            for (int row = 0; row < tableModel.getRowCount(); row++)
            {
                // Step through each modification
                for (TableModification mod : modifications)
                {
                    // Check if the row isn't already in the correct order
                    // (from a previous pass), the row index changed, and that
                    // the primary key values match for the table row and the
                    // modification
                    if (!mod.getRowData()[rowIndex].equals(mod.getOriginalRowData()[rowIndex])
                        && mod.getOriginalRowData()[primaryKeyIndex].equals(tableModel.getValueAt(row,
                                                                                                  primaryKeyIndex)))
                    {
                        // Get the row's new row index
                        int newRow = Integer.valueOf(mod.getRowData()[rowIndex].toString()) - 1;

                        // Move the row from its current location to its new
                        // location
                        tableModel.moveRow(row, row, newRow, false);

                        // Move the row in the committed data as well
                        if (row <= newRow)
                        {
                            Collections.rotate(committedData.subList(row, newRow + 1), -1);
                        }
                        else
                        {
                            Collections.rotate(committedData.subList(newRow, row + 1), 1);
                        }
                    }
                }
            }

            // Step through each row modified in the table
            for (TableModification mod : modifications)
            {
                // Step through each row in the table
                for (int row = 0; row < tableModel.getRowCount(); row++)
                {
                    // Check if the primary keys match between the table row
                    // and the row to modify
                    if (tableModel.getValueAt(row, primaryKeyIndex).toString().equals(mod.getRowData()[primaryKeyIndex].toString()))
                    {
                        // Step through each column of the updated row data
                        for (int column = 0; column < tableModel.getColumnCount(); column++)
                        {
                            // Get the current value of the column in the
                            // updated row
                            Object currentValue = tableModel.getValueAt(row, column);

                            // Check if the modification's column value differs
                            // from this tables' current value, and if this
                            // table's column doesn't have a custom value
                            if (!currentValue.equals(mod.getRowData()[column])
                                && currentValue.equals(mod.getOriginalRowData()[column]))
                            {
                                // Replace the current column value with the
                                // updated column value
                                tableModel.setValueAt(mod.getRowData()[column],
                                                      row,
                                                      column,
                                                      false);
                                committedData.get(row)[column] = mod.getRowData()[column];
                            }
                        }

                        // Stop searching since the matching row was found
                        break;
                    }
                }
            }

            // Sort the row deletions in descending order
            Collections.sort(deletions, new Comparator<TableModification>()
            {
                /**********************************************************
                 * Compare the row numbers of two deletion actions in order to
                 * sort the row deletion from highest to lowest
                 *********************************************************/
                @Override
                public int compare(TableModification del1, TableModification del2)
                {
                    return Integer.compare(Integer.valueOf(del2.getRowData()[rowIndex].toString()),
                                           Integer.valueOf(del1.getRowData()[rowIndex].toString()));
                }
            });

            // Step through each row deleted from the table
            for (TableModification del : deletions)
            {
                // Step through each row in the table
                for (int row = 0; row < tableModel.getRowCount(); row++)
                {
                    // Check if the primary keys match between the table row
                    // and the row to delete
                    if (tableModel.getValueAt(row, primaryKeyIndex).toString().equals(del.getRowData()[primaryKeyIndex].toString()))
                    {
                        // Remove the deleted row and stop searching
                        tableModel.removeRow(row, false);
                        committedData.remove(row);
                        break;
                    }
                }
            }
        }
        // Not a child of a prototype
        else
        {
            // Set the committed data to the table's current data
            committedData = table.getTableDataList(true);
        }

        // Any added rows in the table model don't have the primary key value
        // set. These values are extracted from the database after the table is
        // updated, then used here to update the table model
        int keyRow = 0;

        // Step through each row in the table model
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Check if the primary key column if empty
            if (tableModel.getValueAt(row, primaryKeyIndex).toString().isEmpty())
            {
                // Step through each column in the table model
                for (int column = 0; column < tableModel.getColumnCount(); column++)
                {
                    // Check if the cell is not empty. This prevents assigning
                    // the primary key to an empty row
                    if (!tableModel.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the primary key value from the list
                        tableModel.setValueAt(keys.get(keyRow).toString(),
                                              row,
                                              primaryKeyIndex,
                                              false);
                        committedData.get(row)[primaryKeyIndex] = keys.get(keyRow).toString();

                        // Update the index to the next key value row and stop
                        // searching
                        keyRow++;
                        break;
                    }
                }
            }
            // The row has a primary key value
            else
            {
                // Update the index to the next key value row
                keyRow++;
            }
        }

        // Store the table data, column order, description, and data fields
        committedInfo.setData(CcddUtilities.convertObjectToString(committedData.toArray(new Object[0][0])));
        committedInfo.setColumnOrder(table.getColumnOrder());
        committedInfo.setDescription(getDescription());
        committedInfo.getFieldHandler().setFieldInformation(tableInfo.getFieldHandler().getFieldInformationCopy());
    }

    /**************************************************************************
     * Get the value of the cell in the table model with any macro name
     * replaced with its corresponding macro value
     *
     * @param row
     *            row index, model coordinates
     *
     * @param column
     *            column index, model coordinates
     *
     * @return Value of the cell in the table model with any macro name
     *         replaced with its corresponding macro value
     *************************************************************************/
    private String getExpandedValueAt(int row, int column)
    {
        return newMacroHandler.getMacroExpansion(tableModel.getValueAt(row, column).toString());
    }

    /**************************************************************************
     * Get the value of the cell in the row of table data supplied with any
     * macro name replaced with its corresponding macro value
     *
     * @param row
     *            row index, model coordinates
     *
     * @param column
     *            column index, model coordinates
     *
     * @return Value of the cell in the row of table data supplied with any
     *         macro name replaced with its corresponding macro value
     *************************************************************************/
    private String getExpandedValueAt(List<Object[]> tableData, int row, int column)
    {
        return newMacroHandler.getMacroExpansion(tableData.get(row)[column].toString());
    }

    /**************************************************************************
     * Create the table editor
     *************************************************************************/
    private void initialize()
    {
        // Set the table type definition
        setTypeDefinition();

        // Get the model column indices for columns with special input types
        getSpecialColumnIndices();

        // Create a copy of the table information
        setCommittedInformation(tableInfo);

        // Get the array size and index column indices and create a row filter
        // to show/hide the array member rows if an array size column exists
        setUpArraySizeColumn();

        // Define the table editor JTable
        table = new CcddJTableHandler(ModifiableSizeInfo.INIT_VIEWABLE_DATA_TABLE_ROWS.getSize())
        {
            /******************************************************************
             * Highlight any macros or special flags in the table cells
             *
             * @param component
             *            reference to the table cell renderer component
             *
             * @param text
             *            cell text
             *
             * @param isSelected
             *            true if the cell is to be rendered with the selection
             *            highlighted
             *
             * @param int row cell row, view coordinates
             *
             * @param column
             *            cell column, view coordinates
             *****************************************************************/
            @Override
            protected void doSpecialRendering(Component component,
                                              String text,
                                              boolean isSelected,
                                              int row,
                                              int column)
            {
                // Highlight any macro names in the table cell. Adjust the
                // highlight color to account for the cell selection
                // highlighting so that the macro is easily readable
                macroHandler.highlightMacro(component,
                                            text,
                                            isSelected
                                                      ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                      : ModifiableColorInfo.TEXT_HIGHLIGHT.getColor());

                // Highlight the flag that indicates the custom value for this
                // cell is to be removed and the prototype's value used
                // instead. Create a highlighter painter
                DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(isSelected
                                                                                                           ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                                                                           : Color.MAGENTA);

                // Create the match pattern
                Pattern pattern = Pattern.compile("^" + Pattern.quote(REPLACE_INDICATOR));

                // Create the pattern matcher from the pattern
                Matcher matcher = pattern.matcher(text);

                // Check if there is a match in the cell value
                if (matcher.find())
                {
                    try
                    {
                        // Highlight the matching text. Adjust the highlight
                        // color to account for the cell selection highlighting
                        // so that the search text is easily readable
                        ((JTextComponent) component).getHighlighter().addHighlight(matcher.start(),
                                                                                   matcher.end(),
                                                                                   painter);
                    }
                    catch (BadLocationException ble)
                    {
                        // Ignore highlighting failure
                    }
                }
            }

            /******************************************************************
             * Get the tool tip text for a table cell, showing any macro name
             * replaced with its corresponding macro value
             *****************************************************************/
            @Override
            public String getToolTipText(MouseEvent me)
            {
                String toolTipText = null;

                // Get the row and column of the cell over which the mouse
                // pointer is hovering
                Point point = me.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);

                // Check if a cell is beneath the mouse pointer
                if (row != -1 && column != -1)
                {
                    // Expand any macros in the cell text and display this as
                    // the cell's tool tip text
                    toolTipText = macroHandler.getMacroToolTipText(getValueAt(row,
                                                                              column).toString());
                }

                return toolTipText;
            }

            /**************************************************************************
             * Return true if the table data, column order, description, or a
             * data field changes. If the table isn't open in and editor (as
             * when a macro is changed) then the table description and data
             * fields are not applicable
             *************************************************************************/
            @Override
            protected boolean isTableChanged(Object[][] previousData,
                                             List<Integer> ignoreColumns)
            {
                boolean isFieldChanged = false;

                // Check that the table is open in a table editor (versus open
                // for a macro name and/or value change)
                if (editorDialog != null)
                {
                    // Update the field information with the current text
                    // field values
                    updateCurrentFields(tableInfo.getFieldHandler().getFieldInformation());

                    // Set the change flag if the number of fields in the
                    // committed version differs from the current version of
                    // the table
                    isFieldChanged = tableInfo.getFieldHandler().getFieldInformation().size() != committedInfo.getFieldHandler().getFieldInformation().size();

                    // Check if the number of fields is the same between the
                    // committed and current versions
                    if (!isFieldChanged)
                    {
                        // Create shortcut references to the current and
                        // committed field descriptions
                        Object[][] current = tableInfo.getFieldHandler().getFieldDefinitionArray(false);
                        Object[][] committed = committedInfo.getFieldHandler().getFieldDefinitionArray(false);

                        // Step through each field
                        for (int row = 0; row < current.length; row++)
                        {
                            // Step through each field member
                            for (int column = 0; column < current[row].length; column++)
                            {
                                // Check if the current and committed values
                                // differ
                                if (!current[row][column].equals(committed[row][column]))
                                {
                                    // Set the flag indicating a field is
                                    // changed and stop searching
                                    isFieldChanged = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                return super.isTableChanged(previousData, ignoreColumns)
                       || isFieldChanged
                       || !getColumnOrder().equals(committedInfo.getColumnOrder())
                       || (editorDialog != null
                       && !committedInfo.getDescription().equals(getDescription()));
            }

            /******************************************************************
             * Allow multiple line display in all columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /******************************************************************
             * Override isCellEditable to determine which cells can be edited
             *****************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                // Initialize the flag to the table edit flag (based on the
                // table edit flag this enables normal editing or disables
                // editing any cell)
                boolean isEditable = isEditEnabled;

                // Check if editing is enabled, the table is displayable (to
                // prevent corruption of the cell editor) or that the table
                // isn't open in an editor (as when a macro change is
                // processed), if the table model exists, and if the table has
                // at least one row
                if (isEditable
                    && (isDisplayable() || editorDialog == null)
                    && tableModel != null
                    && tableModel.getRowCount() != 0)
                {
                    // Create storage for the row of table data
                    Object[] rowData = new Object[tableModel.getColumnCount()];

                    // Convert the view row and column indices to model
                    // coordinates
                    int modelRow = convertRowIndexToModel(row);
                    int modelColumn = convertColumnIndexToModel(column);

                    // Step through each column in the row
                    for (int index = 0; index < rowData.length; index++)
                    {
                        // Store the column value into the row data array
                        rowData[index] = getExpandedValueAt(modelRow, index);
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
             *            list containing the table data row arrays
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
                boolean isAlterable = true;

                // Check if the table data has at least one row
                if (rowData != null && rowData.length != 0)
                {
                    // Flag that is true if the row represents an array
                    // definition
                    boolean isArrayDefinition = arraySizeIndex != -1
                                                && variableNameIndex != -1
                                                && !rowData[arraySizeIndex].toString().isEmpty()
                                                && !ArrayVariable.isArrayMember(rowData[variableNameIndex]);

                    // Check if the cell is non-alterable based on the
                    // following criteria:
                    if ((
                        // This is the variable name, data type, array size, or
                        // bit length cell and this is not a prototype table
                        (column == variableNameIndex
                         || column == dataTypeIndex
                         || column == arraySizeIndex
                         || column == bitLengthIndex)
                         && !tableInfo.isPrototype())

                        // This is the variable name, data type, or array size
                        // cell and the row displays an array member
                        || ((column == variableNameIndex
                             || column == dataTypeIndex
                             || column == arraySizeIndex)
                             && ArrayVariable.isArrayMember(rowData[variableNameIndex]))

                        // This data type is not a primitive (i.e., it's a
                        // structure) and the column isn't valid for structures
                        || (dataTypeIndex != -1
                            && !dataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString())
                            && !typeDefn.isStructureAllowed()[column])

                        // This data type is a pointer and the column isn't
                        // valid for pointers
                        || (dataTypeIndex != -1
                            && dataTypeHandler.isPointer(rowData[dataTypeIndex].toString())
                            && !typeDefn.isPointerAllowed()[column])

                        // This is the enumeration or rate cell and the row
                        // displays an array definition,
                        || ((enumerationIndex.contains(column)
                        && rateIndex.contains(column))
                        && isArrayDefinition)

                        // This is the bit length cell and either the array
                        // size is present or the data type is not an integer
                        // (signed or unsigned)
                        || (column == bitLengthIndex
                        && ((arraySizeIndex != -1
                        && !rowData[arraySizeIndex].toString().isEmpty())
                        || (dataTypeIndex != -1
                        && !dataTypeHandler.isInteger(rowData[dataTypeIndex].toString()))))

                        // This is the array size cell and either no variable
                        // name is present or a bit length is present
                        || (column == arraySizeIndex
                        && ((variableNameIndex != -1
                        && rowData[variableNameIndex].toString().isEmpty())
                        || (bitLengthIndex != -1
                        && !rowData[bitLengthIndex].toString().isEmpty())))

                        // This is a rate cell, and a data type exists that is
                        // not a primitive
                        || (rateIndex.contains(column)
                            && dataTypeIndex != -1
                            && !rowData[dataTypeIndex].toString().isEmpty()
                            && !dataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString()))

                        // This is any column in an array variable of type
                        // 'string' other than the first array member
                        || (variableNameIndex != -1
                            && dataTypeIndex != -1
                            && dataTypeHandler.isString(rowData[dataTypeIndex].toString())
                            && ArrayVariable.isArrayMember(rowData[variableNameIndex])
                            && !rowData[variableNameIndex].toString().endsWith("[0]"))

                        // This is an array definition and the column input
                        // type is 'message ID'
                        || (isArrayDefinition
                        && typeDefn.getInputTypes()[column].equals(InputDataType.MESSAGE_ID))

                        // This is the column that displays the variable path,
                        // which is non-editable
                        || column == variablePathIndex)
                    {
                        // Set the flag to prevent altering the data value
                        isAlterable = false;
                    }
                    // Check the column groupings
                    else
                    {
                        // Step through each column grouping
                        for (AssociatedColumns colGrp : associatedColumns)
                        {
                            // Check if the cell is non-alterable based on the
                            // following criteria:
                            if ((
                                // This is an enumeration column, and that the
                                // associated data type isn't an integer type
                                // (signed or unsigned)
                                column == colGrp.getEnumeration()
                                && !dataTypeHandler.isInteger(rowData[colGrp.getDataType()].toString()))

                                // This is a minimum or maximum value column
                                // and no data type is defined
                                || ((column == colGrp.getMinimum()
                                || column == colGrp.getMaximum())
                                && rowData[colGrp.getDataType()].toString().isEmpty()))
                            {
                                // Set the flag to prevent altering the data
                                // value and stop searching
                                isAlterable = false;
                                break;
                            }
                        }

                        // Check if no command argument pairing rest the flag
                        if (isAlterable)
                        {
                            // Step through each non-command argument
                            // minimum/maximum pairing
                            for (MinMaxPair minMax : minMaxPair)
                            {
                                // Check if this is the minimum or maximum
                                // column and the data type is missing, empty,
                                // or isn't a primitive type
                                if (dataTypeIndex != -1
                                    && (rowData[dataTypeIndex].toString().isEmpty()
                                    || !dataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString()))
                                    && (column == minMax.getMinimum()
                                    || column == minMax.getMaximum()))
                                {
                                    // Set the flag to prevent altering the
                                    // data value and stop searching
                                    isAlterable = false;
                                    break;
                                }
                            }
                        }
                    }
                }

                return isAlterable;
            }

            /******************************************************************
             * Override getCellEditor so that for a data type column cell in a
             * row where the enumeration cell isn't empty the combo box editor
             * that displays only integer data types (signed and unsigned) is
             * returned; for all other cells return the normal cell editor
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
                int modelRow = convertRowIndexToModel(row);
                int modelColumn = convertColumnIndexToModel(column);

                // Check if the column for which the cell editor is requested
                // is the data type column and the bit length cell is not empty
                if (modelColumn == dataTypeIndex
                    && bitLengthIndex != -1
                    && !getExpandedValueAt(modelRow, bitLengthIndex).isEmpty())
                {
                    // Select the combo box cell editor that displays only
                    // integer data types (signed and unsigned)
                    cellEditor = enumDataTypeCellEditor;
                }
                // Check if this is a data type and enumeration pairing or a
                // command argument column grouping
                else
                {
                    // Step through each column grouping
                    for (AssociatedColumns colGrp : associatedColumns)
                    {
                        // Check if the column for which the cell editor is
                        // requested is a data type column, and that the
                        // associated enumeration cell isn't blank
                        if (modelColumn == colGrp.getDataType()
                            && colGrp.getEnumeration() != -1
                            && !getExpandedValueAt(modelRow,
                                                   colGrp.getEnumeration()).isEmpty())
                        {
                            // Select the combo box cell editor that displays
                            // only integer data types (signed and unsigned)
                            // and stop searching
                            cellEditor = enumDataTypeCellEditor;
                            break;
                        }
                    }
                }

                return cellEditor;
            }

            /******************************************************************
             * Validate changes to the editable cells; e.g., verify cell
             * content and, if found invalid, revert to the original value.
             * Update array members if needed
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
             * @return true to indicate that subsequent errors should be
             *         displayed; false if subsequent errors should not be
             *         displayed; null if the operation should be canceled
             *****************************************************************/
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

                try
                {
                    // Set the parameters that govern recalculating packed
                    // variables to begin with the first row in the table and
                    // to use the first variable in the pack to set the rates
                    // for other variables in the same pack
                    int startRow = 0;
                    boolean useRowRate = false;

                    // Create a string version of the new value, replacing any
                    // macro in the text with its corresponding value
                    String newValueS = newMacroHandler.getMacroExpansion(newValue.toString());

                    // Check if the cell is flagged for replacement by the
                    // prototype value
                    if (newValueS.startsWith(REPLACE_INDICATOR))
                    {
                        // Remove the flag so that the updated value is stored
                        // as a custom value
                        newValueS = newValueS.replaceFirst("^" + REPLACE_INDICATOR, "");
                    }

                    // Check that the new value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the values in this column must not be
                        // duplicated
                        if (typeDefn.isRowValueUnique()[column])
                        {
                            // Step through each row in the table
                            for (int otherRow = 0; otherRow < tableData.size(); otherRow++)
                            {
                                // Check if this isn't the row being/ edited,
                                // and if the cell value matches the one being
                                // added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(getExpandedValueAt(tableData,
                                                                                     otherRow,
                                                                                     column)))
                                {
                                    throw new CCDDException("Invalid input value for column '</b>"
                                                            + typeDefn.getColumnNamesUser()[column]
                                                            + "<b>'; value must be unique");
                                }
                            }
                        }

                        // Step through each column grouping
                        for (AssociatedColumns colGrp : associatedColumns)
                        {
                            // Check if this is a name column
                            if (column == colGrp.getName())
                            {
                                // Step through the column groupings
                                for (AssociatedColumns otherColGrp : associatedColumns)
                                {
                                    // Check if this is not the argument being
                                    // checked and if the name matches the name
                                    // of another command argument
                                    if (!colGrp.equals(otherColGrp)
                                        && newValueS.equals(getExpandedValueAt(tableData,
                                                                               row,
                                                                               otherColGrp.getName())))
                                    {
                                        throw new CCDDException("Invalid input value for column '</b>"
                                                                + typeDefn.getColumnNamesUser()[column]
                                                                + "<b>'; command argument names must be unique for a command");
                                    }
                                }
                            }
                            // Check if this is the minimum or maximum value
                            // columns
                            else if (column == colGrp.getMinimum()
                                     || column == colGrp.getMaximum())
                            {
                                // Verify that the minimum/maximum value is
                                // valid for the argument's data type, and stop
                                // searching
                                validateMinMaxContent(tableData,
                                                      row,
                                                      column,
                                                      newValueS,
                                                      colGrp.getDataType(),
                                                      colGrp.getMinimum(),
                                                      colGrp.getMaximum());
                                break;
                            }
                        }

                        // Step through each minimum/maximum pairing
                        for (MinMaxPair minMax : minMaxPair)
                        {
                            // Check if this is the minimum or maximum value
                            // columns
                            if (column == minMax.getMinimum()
                                || column == minMax.getMaximum())
                            {
                                // Verify that the minimum/maximum value is
                                // valid for the argument's data type, and stop
                                // searching
                                validateMinMaxContent(tableData,
                                                      row,
                                                      column,
                                                      newValueS,
                                                      dataTypeIndex,
                                                      minMax.getMinimum(),
                                                      minMax.getMaximum());
                                break;
                            }
                        }

                        // Check if the value doesn't match the expected input
                        // type
                        if (!newValueS.matches(typeDefn.getInputTypes()[column].getInputMatch()))
                        {
                            throw new CCDDException("Invalid input type for column '</b>"
                                                    + typeDefn.getColumnNamesUser()[column]
                                                    + "<b>'; "
                                                    + typeDefn.getInputTypes()[column].getInputName().toLowerCase()
                                                    + " expected");
                        }
                    }

                    // Check if the new value doesn't contain a macro
                    // reference; this prevents the macro reference from being
                    // lost
                    if (!newMacroHandler.hasMacro(newValue.toString()))
                    {
                        // Store the new value in the table data array after
                        // formatting the cell value per its input type. This
                        // is needed primarily to clean up numeric formatting
                        newValueS = typeDefn.getInputTypes()[column].formatInput(newValueS);
                        newValue = newValueS;
                        tableData.get(row)[column] = newValueS;
                    }

                    // Replace any macro in the original text with its
                    // corresponding value
                    String oldValueS = macroHandler.getMacroExpansion(oldValue.toString());

                    // Check that the old and new values differ after replacing
                    // any macro in the original text with its corresponding
                    // value
                    if (!newValueS.equals(oldValueS))
                    {
                        String variableName = null;
                        String dataType = null;
                        String arraySize = null;
                        String bitLength = null;

                        // Check if the variable name column exists
                        if (variableNameIndex != -1)
                        {
                            // Get the variable name for the current row,
                            // expanding macros in the name (if present)
                            variableName = getExpandedValueAt(tableData, row, variableNameIndex);
                        }

                        // Check if the data type column exists
                        if (dataTypeIndex != -1)
                        {
                            // Get the data type for the current row
                            dataType = tableData.get(row)[dataTypeIndex].toString();
                        }

                        // Check if the array size column exists
                        if (arraySizeIndex != -1)
                        {
                            // Get the array size for the current row,
                            // expanding macros in the value (if present)
                            arraySize = getExpandedValueAt(tableData, row, arraySizeIndex);
                        }

                        // Check if the bit length column exists
                        if (bitLengthIndex != -1)
                        {
                            // Get the bit length for the current row,
                            // expanding macros in the value (if present)
                            bitLength = getExpandedValueAt(tableData, row, bitLengthIndex);
                        }

                        // Check if the variable name or data type has been
                        // changed
                        if (column == variableNameIndex || column == dataTypeIndex)
                        {
                            // Check if the variable is an array
                            if (arraySize != null)
                            {
                                // Check if this is the data type column, a bit
                                // length is present, and the bit length
                                // exceeds the size of the data type in bits
                                if (column == dataTypeIndex
                                    && !newValueS.isEmpty()
                                    && bitLength != null
                                    && !bitLength.isEmpty()
                                    && Integer.valueOf(bitLength) > newDataTypeHandler.getSizeInBits(dataType))
                                {
                                    throw new CCDDException("Bit length exceeds the size of the data type");
                                }

                                // Get the array index values from the array
                                // size column and update array members if this
                                // is an array definition
                                int[] arrayDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                                adjustArrayMember(tableData, arrayDims, arrayDims, row, column);
                            }

                            // Check if the variable path column is present
                            if (variablePathIndex != -1)
                            {
                                // Update the variable path with the new
                                // variable name and/or data type
                                tableData.get(row)[variablePathIndex] = getVariablePath(variableName,
                                                                                        dataType);
                            }
                        }
                        // Check if this is the array size column
                        else if (column == arraySizeIndex)
                        {
                            // Get the original and updated array index values
                            int[] arraySizeOld = ArrayVariable.getArrayIndexFromSize(oldValueS);
                            int[] arraySizeNew = ArrayVariable.getArrayIndexFromSize(newValueS);

                            // Set the flag that indicates the array index
                            // values changed based on the number of index
                            // values changing
                            boolean isDifferent = arraySizeOld.length != arraySizeNew.length;

                            // Check if the number of index values is the same,
                            // in which case the individual array index values
                            // must be compared
                            if (!isDifferent)
                            {
                                // Step through each index value
                                for (int index = 0; index < arraySizeOld.length; index++)
                                {
                                    // Check if the original and updated values
                                    // differ
                                    if (arraySizeOld[index] != arraySizeNew[index])
                                    {
                                        // Set the flag to indicate an index
                                        // value changed and stop searching
                                        isDifferent = true;
                                        break;
                                    }
                                }
                            }

                            // Check if the original and updated values differ
                            if (isDifferent)
                            {
                                // Add or remove array members to match the new
                                // array size
                                adjustArrayMember(tableData,
                                                  arraySizeOld,
                                                  arraySizeNew,
                                                  row,
                                                  column);
                            }
                        }
                        // Check if this is the rate column and the row is an
                        // array definition
                        else if (rateIndex.contains(column)
                                 && arraySize != null
                                 && variableName != null
                                 && !arraySize.isEmpty()
                                 && !ArrayVariable.isArrayMember(variableName))
                        {
                            // Get the array index value(s)
                            int[] arrayDims = ArrayVariable.getArrayIndexFromSize(arraySize);

                            // Update the array members with the new rate
                            adjustArrayMember(tableData, arrayDims, arrayDims, row, column);
                        }
                        // Check if this is the rate column and the variable
                        // has a bit length value
                        else if (rateIndex.contains(column)
                                 && bitLength != null
                                 && dataType != null
                                 && !bitLength.isEmpty())
                        {
                            // Adjust the rates of any other bit-wise variables
                            // that are packed together with this variable,
                            // using this row's rate
                            startRow = row;
                            useRowRate = true;
                        }
                        // Check if this is the bit length column
                        else if (column == bitLengthIndex)
                        {
                            // Check if a bit length is present and it exceeds
                            // the bit size of the data type
                            if (!bitLength.isEmpty()
                                && dataType != null
                                && Integer.valueOf(bitLength)
                                > newDataTypeHandler.getSizeInBits(dataType))
                            {
                                throw new CCDDException("Bit length exceeds the size of the data type");
                            }

                            // Adjust the rates of any other bit-wise variables
                            // that are packed together with this variable,
                            // using the first packed variable's rate
                            startRow = row;
                        }
                        // Check if a column other than the variable name, data
                        // type, or array size is changed for an array
                        // definition or for a string array
                        else if (variableName != null
                                 && dataType != null
                                 && arraySize != null
                                 && column != variableNameIndex
                                 && column != dataTypeIndex
                                 && column != arraySizeIndex
                                 && !arraySize.isEmpty()
                                 && (!ArrayVariable.isArrayMember(variableName)
                                 || newDataTypeHandler.isString(dataType)))
                        {
                            // Propagate the value to all members of this
                            // array/string
                            propagateArrayValues(tableData, row, column);
                        }

                        // Clear the contents of any cells that are no longer
                        // valid in this row
                        clearInvalidCells(tableData.get(row), row);

                        // Adjust the rates of the bit-wise variables that are
                        // packed together, beginning at the indicated row
                        setAllPackedVariableRates(tableData, startRow, useRowRate);

                        // Check if the new value contains any macros
                        if (newMacroHandler.hasMacro(newValue.toString()))
                        {
                            // Store the new value, with the macro(s) restored,
                            // into the table data array
                            tableData.get(row)[column] = newValue;
                        }
                    }
                    // The cell value is the same after macro expansion; check
                    // if it differed prior to macro expansion (e.g., a macro
                    // name is replaced with the actual value or vice versa)
                    else if (!newValue.equals(oldValue))
                    {
                        // Store the new value in the table data array
                        tableData.get(row)[column] = newValue;

                        // Check if the column is the array size
                        if (column == arraySizeIndex)
                        {
                            // Propagate the value to all members of this
                            // array/string
                            propagateArrayValues(tableData, row, column);
                        }
                    }
                    // The cell value didn't change
                    else
                    {
                        // Pop the edit from the stack
                        table.getUndoManager().undoRemoveEdit();
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's
                    // content is invalid
                    setLastCellValid(false);

                    // Check if the error message dialog should be displayed
                    if (showMessage)
                    {
                        // Check if this is a single cell insert
                        if (!isMultiple)
                        {
                            // Inform the user that the input value is invalid
                            new CcddDialogHandler().showMessageDialog(editorDialog,
                                                                      "<html><b>"
                                                                          + ce.getMessage(),
                                                                      "Invalid Input",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                        // This is one of multiple cells being inserted
                        else
                        {
                            // Inform the user that the input value is invalid
                            CcddDialogHandler validityDlg = new CcddDialogHandler();
                            int buttonSelected = validityDlg.showIgnoreCancelDialog(editorDialog,
                                                                                    "<html><b>"
                                                                                        + ce.getMessage(),
                                                                                    "Invalid Input",
                                                                                    "Ignore this invalid input",
                                                                                    "Ignore this and any remaining invalid inputs for this table",
                                                                                    "Cease inputting values");

                            // Check if the Ignore All button was pressed
                            if (buttonSelected == IGNORE_BUTTON)
                            {
                                // Set the flag to ignore subsequent input
                                // errors
                                showMessage = false;
                            }
                            // Check if the Cancel button was pressed
                            else if (buttonSelected == CANCEL_BUTTON)
                            {
                                // Set the flag to cancel updating the cells
                                showMessage = null;
                            }
                        }
                    }

                    // Restore the cell contents to its original value and pop
                    // the edit from the stack
                    tableData.get(row)[column] = oldValue;
                    table.getUndoManager().undoRemoveEdit();
                }

                return showMessage;
            }

            /******************************************************************
             * Clear the contents of cells in the specified row that are no
             * longer valid due to the contents of other cells
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param row
             *            table model row index
             *****************************************************************/
            private void clearInvalidCells(Object[] rowData, int row)
            {
                // Step through each visible column
                for (int column = 0; column < getColumnCount(); column++)
                {
                    // Get the column index in model coordinates
                    int modelColumn = convertColumnIndexToModel(column);

                    if ((
                        // Check if this isn't the variable name, data type,
                        // array size, bit length, rate, or variable path
                        // column, and that the cell is not alterable
                        (modelColumn != variableNameIndex
                         && modelColumn != dataTypeIndex
                         && modelColumn != arraySizeIndex
                         && modelColumn != bitLengthIndex
                         && modelColumn != variablePathIndex
                         && !rateIndex.contains(modelColumn)
                         && !isDataAlterable(rowData, row, modelColumn)))

                        // Check if the data type column exists, the data type
                        // is not a primitive (i.e., it's a structure), and
                        // structures are not allowed for this column
                        || (dataTypeIndex != -1
                            && !newDataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString())
                            && !typeDefn.isStructureAllowed()[modelColumn])

                        // Check if the data type column exists, the data type
                        // is a pointer, and pointers are not allowed for this
                        // column
                        || (dataTypeIndex != -1
                            && newDataTypeHandler.isPointer(rowData[dataTypeIndex].toString())
                            && !typeDefn.isPointerAllowed()[modelColumn])

                        // Check if a data type column exists, this is the bit
                        // length column, and the data type isn't a primitive
                        || (dataTypeIndex != -1
                            && modelColumn == bitLengthIndex
                            && !newDataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString())))
                    {
                        // Clear the contents of the cell
                        rowData[modelColumn] = "";
                    }
                }
            }

            /******************************************************************
             * Load the database values into the table and format the table
             * cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                int totalWidth = setUpdatableCharacteristics(committedInfo.getData(),
                                                             typeDefn.getColumnNamesUser(),
                                                             committedInfo.getColumnOrder(),
                                                             new Integer[] {primaryKeyIndex,
                                                                            rowIndex},
                                                             null,
                                                             toolTips,
                                                             true,
                                                             true,
                                                             true,
                                                             true);

                // Check that the table is open in a table editor (versus open
                // for a macro name and/or value change) and if this is the
                // widest editor table in this tabbed editor dialog
                if (editorDialog != null
                    && editorDialog.getTableWidth() < totalWidth)
                {
                    // Set the minimum table size based on the column widths
                    editorDialog.setTableWidth(totalWidth);
                }

                // Create the drop-down combo box for the column with the
                // name 'data type' that displays the available data types,
                // including primitive types and the names of tables that
                // represent structures, and add a mouse listener to handle
                // mouse click events. Set up any command argument data type,
                // argument name, enumeration, minimum, and maximum groupings
                setUpDataTypeColumns(null, null);

                // Set up any minimum and maximum pairings (excluding those
                // associated with command argument groupings)
                setUpMinMaxColumns();

                // Create drop-down combo boxes that display the available
                // sample rates for the "Rate" column
                setUpSampleRateColumn();

                // Create the mouse listener for the data type column
                createDataTypeColumnMouseListener();
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
                if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                    && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor())
                {
                    boolean found = true;

                    // Convert the column index to the model coordinate
                    int modelColumn = convertColumnIndexToModel(column);

                    // Get the cell contents
                    String value = getValueAt(row, column).toString();

                    // Check if the cell is required and empty
                    if (typeDefn.isRequired()[modelColumn] && value.isEmpty())
                    {
                        // Set the flag indicating that the cell value is
                        // invalid
                        found = false;
                    }
                    // Check if this is cell contains a combo box, and that the
                    // cell isn't empty or the cell is required
                    else if (((DefaultCellEditor) getCellEditor(row,
                                                                column)).getComponent() instanceof JComboBox
                             && (!value.isEmpty()
                             || typeDefn.isRequired()[modelColumn]))
                    {
                        // Get a reference to the cell's combo box
                        JComboBox<?> comboBox = (JComboBox<?>) ((DefaultCellEditor) getCellEditor(row,
                                                                                                  column)).getComponent();
                        found = false;

                        // Step through each combo box item
                        for (int index = 0; index < comboBox.getItemCount() && !found; index++)
                        {
                            // Check if the cell matches the combo box item
                            if (comboBox.getItemAt(index).equals(value))
                            {
                                // Set the flag indicating that the cell value
                                // is valid and stop searching
                                found = true;
                                break;
                            }
                        }
                    }

                    // Check if the cell value is invalid
                    if (!found)
                    {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                    }
                    // Check if this cell is protected from changes
                    else if (!isCellEditable(row, column))
                    {
                        // Shade the cell's background
                        comp.setForeground(ModifiableColorInfo.PROTECTED_TEXT.getColor());
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                    }
                }

                return comp;
            }

            /******************************************************************
             * Override the CcddJTableHandler method to handle right double
             * click events on the array size cells in order to show/hide the
             * array index column and array member rows
             *****************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Get the table's row sorter and add the array display filter
                TableRowSorter<?> sorter = (TableRowSorter<?>) getRowSorter();

                // Create a runnable object to be executed
                SwingUtilities.invokeLater(new Runnable()
                {
                    /**********************************************************
                     * Execute after all pending Swing events are finished.
                     * This allows the number of viewable columns to catch up
                     * with the column model when a column is removed
                     *********************************************************/
                    @Override
                    public void run()
                    {
                        // Issue a table change event so that the new row is
                        // displayed properly when the array view is collapsed.
                        // Can't use tableModel here since it isn't set when
                        // the first call to this method is made
                        ((UndoableTableModel) table.getModel()).fireTableStructureChanged();
                    }
                });

                // Check if the table has a sorter (i.e., has at least one
                // row), that the filter hasn't been set, and that there is an
                // array row filter
                if (sorter != null
                    && sorter.getRowFilter() == null
                    && rowFilter != null)
                {
                    // Apply the row filter that shows/hides the array members
                    sorter.setRowFilter(rowFilter);
                }
            }

            /******************************************************************
             * Adjust the start and end selections for a row move to encompass
             * the array members if the start or end selection falls within the
             * array's rows. If the table doesn't represent a structure then
             * only the selected row(s) are checked to determine if moving is
             * possible
             *
             * @param rowDelta
             *            row move direction
             *
             * @param selected
             *            cell selection class
             *
             * @return true if the row(s) can be moved
             *****************************************************************/
            private boolean encompassArray(int rowDelta, MoveCellSelection selected)
            {
                boolean isCanMove = false;

                // Convert the selected row coordinates from view to model
                modelStartRow = table.convertRowIndexToModel(selected.getStartRow());
                modelEndRow = table.convertRowIndexToModel(selected.getEndRow());

                // Check if the table array members are set to display
                if (isShowArrayMembers)
                {
                    // While the start row references an array member
                    while (ArrayVariable.isArrayMember(getExpandedValueAt(modelStartRow,
                                                                          variableNameIndex)))
                    {
                        // Decrement the start index to get to the array
                        // definition row
                        modelStartRow--;
                    }

                    // Check if the selected ending row references an array
                    // definition
                    if (!getExpandedValueAt(modelEndRow, arraySizeIndex).isEmpty()
                        && !ArrayVariable.isArrayMember(getExpandedValueAt(modelEndRow,
                                                                           variableNameIndex)))
                    {
                        // Increment the end row so that the members will be
                        // included below
                        modelEndRow++;
                    }

                    boolean isIncludeMember = false;

                    // While the end row references an array member and the end
                    // of the table model hasn't been reached
                    while (modelEndRow < tableModel.getRowCount()
                           && ArrayVariable.isArrayMember(getExpandedValueAt(modelEndRow,
                                                                             variableNameIndex)))
                    {
                        // Increment the end index to get to the end of the
                        // array
                        modelEndRow++;
                        isIncludeMember = true;
                    }

                    // Check if the ending row was adjusted to include an array
                    // member
                    if (isIncludeMember)
                    {
                        // Decrement the row index since the row selection is
                        // inclusive
                        modelEndRow--;
                    }
                }

                // Check if the selected row(s) are not at the top (if moving
                // up) or bottom (if moving down) of the table
                if ((rowDelta < 0 && modelStartRow > 0)
                    || (rowDelta > 0
                    && modelEndRow < tableModel.getRowCount() - 1))
                {
                    isCanMove = true;

                    // Calculate the row that the selected row(s) will be moved
                    // to
                    modelToRow = modelStartRow + rowDelta;

                    // Check if the table has an array size column
                    if (arraySizeIndex != -1)
                    {
                        // Get the array size column value for the start row
                        String arraySize = getExpandedValueAt(modelStartRow,
                                                              arraySizeIndex);

                        // Check if the array size is present on this row
                        if (!arraySize.isEmpty())
                        {
                            // Adjust the row index past the array definition
                            // and member rows
                            int arrayEndRow = modelStartRow
                                              + ArrayVariable.getNumMembersFromArraySize(arraySize);

                            // Check if the new ending row is below the
                            // selected row
                            if (modelEndRow < arrayEndRow)
                            {
                                // Set the end selection to the end of the
                                // array
                                modelEndRow = arrayEndRow;
                            }
                        }
                    }
                }

                return isCanMove;
            }

            /******************************************************************
             * Override the CcddJTableHandler method for moving the selected
             * row(s) up one row in order to prevent moving a row within an
             * array definition and its member rows; instead skip past the
             * array
             *****************************************************************/
            @Override
            protected void moveRowUp()
            {
                // Get the selected cells
                MoveCellSelection selected = new MoveCellSelection();

                // Check if at least one row is selected and it doesn't include
                // the topmost row
                if (selected.getStartRow() > 0)
                {
                    int rowDelta = -1;

                    // Convert the start and end rows to model coordinates and
                    // adjust to encompass the array members within the range,
                    // then check if the starting row isn't at the top of the
                    // table and if so adjust the end selection if the start
                    // selection falls on an array definition
                    if (encompassArray(rowDelta, selected))
                    {
                        // Check if the table can display arrays
                        if (isCanHaveArrays())
                        {
                            // While the target row contains an array member
                            while (ArrayVariable.isArrayMember(getExpandedValueAt(modelToRow,
                                                                                  variableNameIndex)))
                            {
                                // Decrement the target row
                                modelToRow--;

                                // Check if the array members are displayed
                                if (isShowArrayMembers)
                                {
                                    // Adjust the row delta to keep the correct
                                    // rows highlighted after the move
                                    rowDelta--;
                                }
                            }
                        }

                        // Move the row(s) up and update the cell selection
                        performRowMove(modelStartRow,
                                       modelEndRow,
                                       modelToRow,
                                       selected,
                                       rowDelta);
                    }
                }
            }

            /******************************************************************
             * Override the CcddJTableHandler method for moving the selected
             * row(s) down one row in order to prevent moving a row within an
             * array definition and its member rows; instead skip past the
             * array
             *****************************************************************/
            @Override
            protected void moveRowDown()
            {
                // Get the selected cells
                MoveCellSelection selected = new MoveCellSelection();

                // Check if at least one row is selected and it doesn't include
                // the bottom row
                if (selected.getStartRow() != -1
                    && selected.getEndRow() < table.getRowCount() - 1)
                {
                    int rowDelta = 1;

                    // Convert the start and end rows to model coordinates and
                    // adjust to encompass the array members within the range,
                    // then check if the end row isn't the bottom of the table
                    // and if not adjust the end selection if the start
                    // selection falls on an array definition
                    if (encompassArray(rowDelta, selected))
                    {
                        // Check if the table can display arrays
                        if (isCanHaveArrays())
                        {
                            // Get the array size column value for the target
                            // row
                            String arraySize = getExpandedValueAt(modelEndRow + 1,
                                                                  arraySizeIndex);

                            // Check if the array size is present on this row
                            if (!arraySize.isEmpty())
                            {
                                // Get the total number of array members
                                int totalSize = ArrayVariable.getNumMembersFromArraySize(arraySize);

                                // Adjust the target row and the number of rows
                                // to move based on the number of array members
                                // and the visibility of the array members
                                rowDelta += isShowArrayMembers
                                                              ? totalSize
                                                              : 0;
                                modelToRow += totalSize;
                            }
                        }

                        // Move the row(s) down and update the cell selection
                        performRowMove(modelStartRow,
                                       modelEndRow,
                                       modelToRow,
                                       selected,
                                       rowDelta);
                    }
                }
            }

            /******************************************************************
             * Override the CcddJTableHandler method for moving the selected
             * row(s) so that adjustments can be made to the rates for any
             * packed variables
             *
             * @param startRow
             *            selected starting row, in model coordinates
             *
             * @param endRow
             *            selected ending row, in model coordinates
             *
             * @param toRow
             *            target row to move the selected row(s) to, in model
             *            coordinates
             *
             * @param selected
             *            cell selection class
             *
             * @param rowDelta
             *            row move direction and magnitude
             *****************************************************************/
            @Override
            protected void performRowMove(int startRow,
                                          int endRow,
                                          int toRow,
                                          MoveCellSelection selected,
                                          int rowDelta)
            {
                // Move the row(s)
                super.performRowMove(startRow,
                                     endRow,
                                     toRow,
                                     selected,
                                     rowDelta);

                // Check if this is a parent structure table
                if (tableInfo.isRootStructure())
                {
                    // Load the table data into a list
                    List<Object[]> tableData = getTableDataList(false);

                    // Adjust the rate for any packed variables, beginning with
                    // the lowest affected row index
                    setAllPackedVariableRates(tableData,
                                              Math.min(startRow, toRow),
                                              false);

                    // Check if a rate value changed
                    if (isRateChange)
                    {
                        // Load the array of data into the table
                        loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                               true);
                    }
                }
            }

            /******************************************************************
             * Override the CcddJTableHandler method for putting data into a
             * new row inserted below the specified row in order to adjust the
             * insertion index based on the presence of array members
             *
             * @param targetRow
             *            index of the row in model coordinates below which to
             *            insert the new row
             *
             * @param data
             *            data to place in the inserted row
             *
             * @return The new row's index, in model coordinates, adjusted as
             *         needed to account for array member visibility
             *****************************************************************/
            @Override
            protected int insertRowData(int targetRow, Object[] data)
            {
                // Check if table has rows, and has variable name and array
                // size columns
                if (targetRow != -1 && isCanHaveArrays())
                {
                    // Get the array size value
                    String arraySize = getExpandedValueAt(targetRow,
                                                          arraySizeIndex);

                    // Check if the array size is present on this row but not
                    // an array member (i.e., this is the array definition row)
                    if (!arraySize.isEmpty()
                        && !ArrayVariable.isArrayMember(getExpandedValueAt(targetRow,
                                                                           variableNameIndex)))
                    {
                        // Adjust the row index past the array definition and
                        // member rows
                        targetRow += ArrayVariable.getNumMembersFromArraySize(arraySize);
                    }
                    // Check if the array members are set to be displayed
                    else if (isShowArrayMembers)
                    {
                        boolean isIndex = false;

                        // While the selection row is on an array member
                        while (targetRow < tableModel.getRowCount()
                               && ArrayVariable.isArrayMember(getExpandedValueAt(targetRow,
                                                                                 variableNameIndex)))
                        {
                            // Skip the array member row
                            targetRow++;
                            isIndex = true;
                        }

                        // Check if an array member was skipped
                        if (isIndex)
                        {
                            // Decrement the row index
                            targetRow--;
                        }
                    }
                }

                // Insert an empty row below the selected row
                return super.insertRowData(targetRow, null);
            }

            /******************************************************************
             * Override the CcddJTableHandler method for removing a row from
             * the table. Array member rows are ignored unless the array
             * definition row is also deleted; for this case the entire array
             * is removed
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
                boolean isArray = false;

                // Check if the table has array size and variable name columns
                if (isCanHaveArrays())
                {
                    // Extract the array size cell value
                    String arraySize = getExpandedValueAt(modelRow,
                                                          arraySizeIndex);

                    // Check if an array size is present
                    if (!arraySize.isEmpty())
                    {
                        // Set the flag indicating that an array row is being
                        // removed
                        isArray = true;

                        // Perform while this row is an array member
                        while (ArrayVariable.isArrayMember(tableData.get(modelRow)[variableNameIndex]))
                        {
                            // Move the row index up
                            modelRow--;
                        }

                        // Get the row index of the last array member
                        int arrayRow = modelRow
                                       + ArrayVariable.getNumMembersFromArraySize(arraySize);

                        // Step through each member of the array
                        while (arrayRow >= modelRow)
                        {
                            // Delete the row
                            tableData.remove(modelRow);

                            // Go to the next array member row to remove
                            arrayRow--;
                        }
                    }
                }

                // Check if the row does not represent an array definition or
                // member
                if (!isArray)
                {
                    // Delete the row
                    super.removeRow(tableData, modelRow);
                }

                // Adjust the rate for any packed variables, beginning with
                // this row
                setAllPackedVariableRates(tableData,
                                          convertRowIndexToView(modelRow),
                                          false);

                return modelRow - 1;
            }

            /******************************************************************
             * Override the CcddJTableHandler method for getting the special
             * replacement character when deleting the contents of a cell. Get
             * the corresponding cell value from the table's prototype
             *
             * @param row
             *            cell row index in model coordinates
             *
             * @param column
             *            cell column index in model coordinates
             *
             * @return The corresponding cell value from the tables' prototype
             *****************************************************************/
            @Override
            protected String getSpecialReplacement(int row, int column)
            {
                return dbTable.queryTableCellValue(tableInfo.getPrototypeName(),
                                                   committedInfo.getData()[row][primaryKeyIndex],
                                                   typeDefn.getColumnNamesDatabase()[column],
                                                   editorDialog);
            }

            /******************************************************************
             * Override the CcddJTableHandler method for deleting a cell. Set
             * the special character flag to false if the table is a prototype
             * - prototypes can't have an entry in the custom values table so
             * no special handling is needed for this case
             *
             * @param isReplaceSpecial
             *            false to replace the cell value with a blank; true to
             *            replace the cell contents with the prototype's
             *            corresponding cell value
             *****************************************************************/
            @Override
            protected void deleteCell(boolean isReplaceSpecial)
            {
                super.deleteCell(isReplaceSpecial && !tableInfo.isPrototype());
            }

            /******************************************************************
             * Adjust the starting row index to the next row during a paste
             * (insert) operation. If the insertion point falls within an
             * array, skip to the row immediately following the array's members
             *
             * @param startRow
             *            starting row index in view coordinates
             *
             * @return Starting row index, in model coordinates, at which to
             *         insert a new row
             *****************************************************************/
            protected int adjustPasteStartRow(int startRow)
            {
                // Check if the starting row index references a valid row
                if (startRow >= 0 && startRow < getRowCount())
                {
                    // Convert the row index to model coordinates and adjust
                    // the starting row index to the next row
                    startRow = convertRowIndexToModel(startRow) + 1;
                }
                // The starting index is not a valid row
                else
                {
                    // Set the starting row index to the end of the table
                    startRow = tableModel.getRowCount();
                }

                // Check if the starting row index is not at the end of the
                // table, the table has variable size and array name columns,
                // and an array size is present
                if (startRow < tableModel.getRowCount()
                    && isCanHaveArrays()
                    && !getExpandedValueAt(startRow, arraySizeIndex).isEmpty())
                {
                    // Perform while this row is an array member and the end of
                    // the table has not been reached
                    while (startRow < tableModel.getRowCount()
                           && ArrayVariable.isArrayMember(getExpandedValueAt(startRow,
                                                                             variableNameIndex)))
                    {
                        // Adjust the row index to the next row
                        startRow++;
                    }
                }

                return startRow;
            }

            /******************************************************************
             * Determine if a row insertion is required during a paste
             * operation. Array member rows are inserted automatically when an
             * array is defined, so if an array member is being inserted no row
             * needs to be inserted by the paste operation
             *
             * @param index
             *            current index into the cell data array
             *
             * @param cells
             *            array containing the cell data being inserted
             *
             * @param startColumn
             *            data insertion starting column index
             *
             * @param endColumn
             *            data insertion ending column index
             *
             * @return true if a row should be inserted; false otherwise
             *****************************************************************/
            protected boolean isInsertRowRequired(int index,
                                                  Object[] cells,
                                                  int startColumn,
                                                  int endColumn)
            {
                boolean isNotArrayMember = true;

                // Get the variable name column index in view coordinates
                int variableNameIndexView = convertColumnIndexToView(variableNameIndex);

                // Step through the row of data being inserted
                for (int column = startColumn; column <= endColumn; column++, index++)
                {
                    // Check if the column index matches the variable name
                    // column
                    if (column == variableNameIndexView)
                    {
                        // Check if the variable name is an array member
                        if (ArrayVariable.isArrayMember(cells[index]))
                        {
                            // Set the flag to indicate a new row doesn't need
                            // to be inserted
                            isNotArrayMember = false;
                        }

                        // Stop searching
                        break;
                    }
                }

                return isNotArrayMember;
            }

            /******************************************************************
             * Override the paste method so that hidden rows (array members)
             * are displayed prior to pasting in new data
             *****************************************************************/
            @Override
            protected boolean pasteData(Object[] cellData,
                                        int numColumns,
                                        boolean isInsert,
                                        boolean isAddIfNeeded,
                                        boolean startFirstColumn)
            {
                Boolean showMessage = true;

                // Get the table data array
                List<Object[]> tableData = getTableDataList(false);

                // Calculate the number of rows to be pasted in
                int numRows = cellData.length / numColumns;

                // Initialize the starting row to the first row, which is the
                // default if no row is selected
                int startRow = 0;

                // Check if no row is selected
                if (getSelectedRow() == -1)
                {
                    // Clear the column selection. The column selection can
                    // remain in effect after an undo action that clears the
                    // row selection. It needs to be cleared if invalid so that
                    // the starting column index is correctly calculated below
                    getColumnModel().getSelectionModel().clearSelection();
                }
                // A row is selected
                else
                {
                    // Determine the starting row for pasting the data based on
                    // the selected row
                    startRow = convertRowIndexToModel(getSelectedRow())
                               + getSelectedRowCount()
                               - 1;
                }

                // Determine the starting column and ending column for pasting
                // the data. If no column is selected then default to the first
                // column. Data pasted outside of the column range is ignored
                int startColumn = startFirstColumn
                                                  ? 0
                                                  : Math.max(Math.max(getSelectedColumn(), 0),
                                                             getSelectedColumn()
                                                                 + getSelectedColumnCount()
                                                                 - 1);
                int endColumn = startColumn + numColumns - 1;
                int endColumnSelect = Math.min(endColumn, getColumnCount() - 1);

                // Check if the the table is capable of displaying arrays and
                // that the array members are hidden
                if (isCanHaveArrays() && !isShowArrayMembers)
                {
                    // Show the array members. All rows must be visible in
                    // order for the pasted data to be inserted correctly. The
                    // model and view row coordinates are the same after
                    // expanding the array members. Note that this clears the
                    // row and column selection
                    showHideArrayMembers();
                }

                // Check if the data is to be inserted versus overwriting
                // existing cells
                if (isInsert)
                {
                    // Adjust the starting row index to the one after the
                    // selected row, and account for hidden rows, if applicable
                    startRow = adjustPasteStartRow(startRow);
                }
                // Overwrite the existing cells. Check if no row is selected or
                // if the table contains no rows
                else if (startRow == -1)
                {
                    // Set the start row to the first row
                    startRow = 0;
                }

                // Determine the ending row for pasting the data
                int endRow = startRow + numRows;

                // Clear the cell selection
                clearSelection();

                // Counters for the number of array member rows added (due to
                // pasting in an array definition) and the number of rows
                // ignored (due to the first pasted row(s) being an array
                // member)
                int arrayRowsAdded = 0;
                int totalAddedRows = 0;
                int skippedRows = 0;

                boolean isIgnoreRow = false;

                // Step through each new row
                for (int index = 0, row = startRow; row < endRow
                                                    && showMessage != null; row++)
                {
                    boolean skipRow = false;

                    // Calculate the row in the table data where the values are
                    // to be pasted. This must be adjusted to account for
                    // pasting in arrays
                    int adjustedRow = row + totalAddedRows - skippedRows;

                    // Step through the next row of data to be pasted to check
                    // if it is an array member
                    for (int column = startColumn; column <= endColumn
                                                   && column < getColumnCount()
                                                   && showMessage != null; column++)
                    {
                        // Get the index into the cell data for this column
                        int tempIndex = index + column - startColumn;

                        // Check if rows were removed due to an array size
                        // reduction or removal
                        if (arrayRowsAdded < 0)
                        {
                            // Set the flag indicating that this row of
                            // data is to be skipped and increment the
                            // skipped row counter
                            skipRow = true;
                            skippedRows++;

                            // Adjust the row counters so that all array member
                            // rows are skipped
                            arrayRowsAdded++;
                            totalAddedRows++;
                        }
                        // Check if this is the variable name column and that
                        // the cell to paste is an array member
                        else if (variableNameIndex == convertColumnIndexToModel(column)
                                 && tempIndex < cellData.length
                                 && ArrayVariable.isArrayMember(cellData[tempIndex]))
                        {
                            // Check if rows were added for the previous pasted
                            // row to accommodate the array members
                            if (arrayRowsAdded > 0)
                            {
                                // Move the row index back so that the array
                                // member data is pasted in the proper row
                                adjustedRow -= arrayRowsAdded;
                                arrayRowsAdded--;
                                totalAddedRows--;
                            }
                            // No rows were added for this array member
                            else
                            {
                                // Check if this is the first pasted row; if so
                                // then this array member has no definition
                                if (row == startRow)
                                {
                                    // Set the flag indicating that array
                                    // member rows are ignored
                                    isIgnoreRow = true;
                                }

                                // Set the flag indicating that this row of
                                // data is to be skipped and increment the
                                // skipped row counter
                                skipRow = true;
                                skippedRows++;

                                // Update the cell data index so that this row
                                // is skipped
                                index += numColumns;
                            }

                            break;
                        }
                    }

                    // Check that this row is not to be ignored
                    if (!skipRow)
                    {
                        // Check if a row needs to be inserted to contain the
                        // cell data
                        if ((isInsert
                            || (isAddIfNeeded
                            && adjustedRow == tableData.size()))
                            && isInsertRowRequired(index,
                                                   cellData,
                                                   startColumn,
                                                   endColumn))
                        {
                            // Insert a row at the selection point
                            tableData.add(adjustedRow, getEmptyRow());
                        }

                        // Store the index into the array of data to be pasted
                        int indexSave = index;

                        // If pasting values over existing ones it's possible
                        // that the check for a cell being alterable will
                        // return false due to other cells in the row that
                        // haven't yet been pasted over (e.g., a bit length in
                        // a subsequent column prevents pasting in the array
                        // size). To overcome this two passes for each row are
                        // made; first cells containing blanks in the pasted
                        // data are pasted, then the cells that are not empty
                        // are pasted
                        for (int pass = 1; pass <= 2; pass++)
                        {
                            // Check if this is the second pass through the
                            // row's columns
                            if (pass == 2)
                            {
                                // Reset the index into the array of data to be
                                // pasted so that the non-blank cells can be
                                // processed
                                index = indexSave;
                            }

                            // Step through the columns, beginning at the one
                            // with the focus
                            for (int column = startColumn; column <= endColumn
                                                           && showMessage != null; column++)
                            {
                                // Check that the column falls within the
                                // bounds of the table. If outside the bounds
                                // or protected then discard the value
                                if (column < getColumnCount())
                                {
                                    // Convert the column coordinate from view
                                    // to model
                                    int columnModel = convertColumnIndexToModel(column);

                                    // Get the value to be pasted into the
                                    // cell, cleaning up the value if needed.
                                    // If the number of cells to be filled
                                    // exceeds the stored values then insert a
                                    // blank
                                    Object newValue = index < cellData.length
                                                                             ? cleanUpCellValue(cellData[index],
                                                                                                adjustedRow,
                                                                                                columnModel)
                                                                             : "";

                                    // For the first pass through this row's
                                    // column process only blank cells; for the
                                    // second pass process only non-blank
                                    // cells. If one of these criteria is met
                                    // then check if the cell is alterable
                                    if (((pass == 1 && newValue.toString().isEmpty())
                                        || (pass == 2 && !newValue.toString().isEmpty()))
                                        && isDataAlterable(tableData.get(adjustedRow),
                                                           adjustedRow,
                                                           columnModel))
                                    {
                                        // Get the original cell value
                                        Object oldValue = tableData.get(adjustedRow)[columnModel];

                                        // Check if the value has changed and,
                                        // if this values are being inserted,
                                        // that the value isn't blank
                                        if (!oldValue.equals(newValue)
                                            && !(isInsert
                                            && newValue.toString().isEmpty()))
                                        {
                                            // Insert the value into the cell
                                            tableData.get(adjustedRow)[columnModel] = newValue;

                                            // Get the number of rows in the
                                            // table prior to inserting the new
                                            // value
                                            int previousRows = tableData.size();

                                            // Validate the new cell contents
                                            showMessage = validateCellContent(tableData,
                                                                              adjustedRow,
                                                                              columnModel,
                                                                              oldValue,
                                                                              newValue,
                                                                              showMessage,
                                                                              cellData.length > 1);

                                            // Check if the user selected the
                                            // Cancel button following an
                                            // invalid input
                                            if (showMessage == null)
                                            {
                                                // Stop pasting data
                                                continue;
                                            }

                                            // Get the number of rows added due
                                            // to pasting in the new value.
                                            // This is non-zero if an array
                                            // definition is pasted in or if an
                                            // existing array's size is altered
                                            int deltaRows = tableData.size()
                                                            - previousRows;

                                            // Check if the row count changed
                                            if (deltaRows > 0)
                                            {
                                                // Store the number of
                                                // added/deleted rows and
                                                // update the total number of
                                                // added/deleted rows
                                                arrayRowsAdded = deltaRows;
                                                totalAddedRows += arrayRowsAdded;
                                            }
                                        }
                                    }
                                }

                                // Increment the index to the next value to
                                // paste
                                index++;
                            }
                        }
                    }
                }

                // Check if the user hasn't selected the Cancel button
                // following an invalid input
                if (showMessage != null)
                {
                    // Load the array of data into the table
                    loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                           true);

                    // Flag the end of the editing sequence for undo/redo
                    // purposes
                    getUndoManager().endEditSequence();

                    // Check if there are rows left to be selected
                    if (endRow - 1 - skippedRows > 0)
                    {
                        // Select all of the rows into which the data was
                        // pasted
                        setRowSelectionInterval(startRow, endRow - 1 - skippedRows);
                    }

                    // Select all of the columns into which the data was pasted
                    setColumnSelectionInterval(startColumn, endColumnSelect);

                    // Select the pasted cells and force the table to be
                    // redrawn so that the changes are displayed
                    setSelectedCells(startRow, endRow - 1, startColumn, endColumnSelect);
                    repaint();

                    // Check if any rows were ignored
                    if (isIgnoreRow)
                    {
                        // Inform the user how many rows were skipped
                        new CcddDialogHandler().showMessageDialog(editorDialog,
                                                                  "<html><b>"
                                                                      + skippedRows
                                                                      + " array member row(s) ignored due to missing array definition(s)",
                                                                  "Rows Ignored",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }

                // Set the flag that indicates the last edited cell's content
                // is valid (if an invalid input set the flag to false then it
                // can prevent closing the editor)
                setLastCellValid(true);

                return showMessage == null;
            }

            /******************************************************************
             * Override the method for cleaning-up of the cell value. The
             * default is to remove any leading and trailing white space
             * characters. This method skips removal of white space characters
             * for cells having input types that allow it
             *
             * @param value
             *            new cell value
             *
             * @param row
             *            table row, model coordinates
             *
             * @param column
             *            table column, model coordinates
             *
             * @return Cell value following clean-up
             *****************************************************************/
            @Override
            protected Object cleanUpCellValue(Object value, int row, int column)
            {
                // Check if the cell value represents a string (i.e., it isn't
                // boolean, etc.)
                if (value instanceof String)
                {
                    // Get the input type for this column
                    InputDataType inputType = typeDefn.getInputTypes()[column];

                    // Check if the column's input type doesn't allow leading
                    // and trailing white space characters
                    if (inputType != InputDataType.TEXT_WHT_SPC
                        && inputType != InputDataType.TEXT_MULTI_WHT_SPC)
                    {
                        // Perform the default clean-up (remove leading and
                        // trailing white space characters)
                        value = super.cleanUpCellValue(value, row, column);
                    }
                }

                return value;
            }

            /******************************************************************
             * Handle a change to the table's content
             *****************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Check that the table is open in a table editor (versus open
                // for a macro name and/or value change)
                if (editorDialog != null)
                {
                    // Update the change indicator for the table
                    editorDialog.updateChangeIndicator(CcddTableEditorHandler.this);
                }
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);

        // Disable storage of edit operations during table creation
        table.getUndoHandler().setAllowUndo(false);

        // Set common table parameters and characteristics
        table.setFixedCharacteristics(scrollPane,
                                      tableInfo.isPrototype(),
                                      ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                      TableSelectionMode.SELECT_BY_CELL,
                                      true,
                                      ModifiableColorInfo.TABLE_BACK.getColor(),
                                      true,
                                      true,
                                      ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                      true);

        // Get a reference to the table model to shorten later calls
        tableModel = (UndoableTableModel) table.getModel();

        // Re-enable storage of edit operations
        table.getUndoHandler().setAllowUndo(true);

        // Set the reference to the editor's data field handler in the undo
        // handler so that data field value changes can be undone/redone
        // correctly
        table.getUndoHandler().setFieldHandler(tableInfo.getFieldHandler());

        // Set the undo/redo manager and handler for the description and data
        // field values
        setEditPanelUndo(table.getUndoManager(), table.getUndoHandler());

        // Set the mouse listener to expand and collapse arrays
        setArrayExpansionListener();

        // Check that the table is open in a table editor (versus open for a
        // macro name and/or value change)
        if (editorDialog != null)
        {
            // Create the input field panel to contain the table editor
            createDescAndDataFieldPanel(editorDialog,
                                        scrollPane,
                                        tableInfo.getProtoVariableName(),
                                        tableInfo.getDescription(),
                                        tableInfo.getFieldHandler());

            // Set the dialog name so that this dialog can be recognized as
            // being open by the table selection dialog, and the JTable name so
            // that table change events can be identified with this table
            setTableName();
        }
    }

    /**************************************************************************
     * Based on the input flag, display the macro values (names) in place of
     * the macro names (values)
     *
     * @param isExpand
     *            true to replace the macro names with the corresponding macro
     *            values; false to restore the macro names
     *
     * @param isVisibleOnly
     *            true to only expand the macros in those cells that are
     *            currently visible; false to expand the macros in all of the
     *            table cells
     *************************************************************************/
    protected void expandMacros(boolean isExpand, boolean isVisibleOnly)
    {
        int firstRow;
        int lastRow;

        // Check if all cells are to be expanded
        if (!isVisibleOnly)
        {
            // Get the first and last row indices
            firstRow = 0;
            lastRow = tableModel.getRowCount();
        }
        // Only expand the visible cells
        else
        {
            // Determine first and last rows that are currently visible. Get
            // the coordinates of the table's visible rectangle
            Rectangle visRect = table.getVisibleRect();

            // Determine the first (upper) row. If a column is being resized or
            // the table has no rows then set to 0 (the first table row),
            // otherwise set to the first visible row
            firstRow = Math.max(table.rowAtPoint(visRect.getLocation()), 0);

            // Move the rectangle down the height of the table, then back 1
            // pixel to remain within the table boundaries
            visRect.translate(0, visRect.height - 1);

            // Get the last visible row plus 1 (as required by
            // updateRowHeights). Defaults to 0 if the table has no rows
            lastRow = table.rowAtPoint(visRect.getLocation()) + 1;

            // Check if the calculated last row is greater than the total
            // number of rows in the table
            if (lastRow == 0)
            {
                // Set the last row to the table's visible row count
                lastRow = table.getRowCount();
            }

            // Convert the row indices to model coordinates. Model coordinates
            // are used to prevent the change in the table value from being
            // logged as undoable
            firstRow = table.convertRowIndexToModel(firstRow);
            lastRow = table.convertRowIndexToModel(lastRow - 1) + 1;
        }

        // Check if the macro values are being displayed
        if (isExpand)
        {
            // Create storage for the original cell values. Note he first two
            // columns are unused
            originalCellData = new String[lastRow - firstRow][tableModel.getColumnCount()];
        }

        // Step through the visible rows
        for (int row = firstRow; row < lastRow; row++)
        {
            // Step through each column, ignoring the primary key and row index
            // columns
            for (int column = NUM_HIDDEN_COLUMNS; column < tableModel.getColumnCount(); column++)
            {
                // Check if the macro values are being displayed
                if (isExpand)
                {
                    // Store the original cell values for when the macro names
                    // are restored
                    originalCellData[row][column] = tableModel.getValueAt(row, column).toString();
                }

                // Replace the table cells with their original contents (i.e.,
                // show macro names in place of their corresponding values)
                tableModel.setValueAt((isExpand
                                               ? macroHandler.getMacroExpansion(originalCellData[row][column])
                                               : originalCellData[row][column]),
                                      row,
                                      column,
                                      false);
            }
        }
    }

    /**************************************************************************
     * Create a mouse listener for expanding and collapsing arrays
     *************************************************************************/
    private void setArrayExpansionListener()
    {
        // Check if the variable name and array size columns are present
        if (isCanHaveArrays())
        {
            // Add a mouse listener to the table to handle mouse clicks on the
            // data type column
            table.addMouseListener(new MouseAdapter()
            {
                /**************************************************************
                 * Handle mouse press events
                 *************************************************************/
                @Override
                public void mousePressed(MouseEvent me)
                {
                    // Check if the right mouse button is double clicked
                    if (me.getClickCount() == 2
                        && SwingUtilities.isRightMouseButton(me))
                    {
                        // Get the table row and column that was selected;
                        // convert the column index to the model index
                        int modelColumn = table.convertColumnIndexToModel(table.columnAtPoint(me.getPoint()));
                        int row = table.rowAtPoint(me.getPoint());

                        // Check if the array size column was clicked and that
                        // the row is valid
                        if (modelColumn == arraySizeIndex && row != -1)
                        {
                            // Toggle between showing or hiding the array
                            // member rows
                            showHideArrayMembers();
                        }
                    }
                }
            });
        }
    }

    /**************************************************************************
     * Create the listener for handling mouse events in a table cell in order
     * to allow opening a child table by double right clicking the data type
     * column cell
     *************************************************************************/
    private void createDataTypeColumnMouseListener()
    {
        // Check if the table has a data type column
        if (dataTypeIndex != -1)
        {
            // Add a mouse listener to the table to handle mouse clicks on the
            // data type column
            table.addMouseListener(new MouseAdapter()
            {
                /**************************************************************
                 * Handle mouse button press events
                 *************************************************************/
                @Override
                public void mousePressed(MouseEvent me)
                {
                    // Check if the right mouse button is double clicked
                    if (me.getClickCount() == 2
                        && SwingUtilities.isRightMouseButton(me))
                    {
                        // Get the table column that was selected
                        int column = table.columnAtPoint(me.getPoint());

                        // Check if the data type column was clicked
                        if (table.convertColumnIndexToModel(column) == dataTypeIndex)
                        {
                            // Get the table row that was selected
                            int row = table.rowAtPoint(me.getPoint());

                            // Check if the row is valid
                            if (row != -1)
                            {
                                // Check if a cell if currently being edited
                                if (table.getCellEditor() != null)
                                {
                                    // Incorporate any cell changes and
                                    // terminate editing
                                    table.getCellEditor().stopCellEditing();
                                }

                                // Highlight the selected cell
                                table.setRowSelectionInterval(row, row);
                                table.setColumnSelectionInterval(column, column);

                                // Open the child table associated with the
                                // selected row's variable name and data type,
                                // if valid
                                openChildTable(row);
                            }
                        }
                    }
                }
            });
        }
    }

    /**************************************************************************
     * Open the child table indicated by the data type row and column. If the
     * variable and data type for the specified row doesn't represent a child
     * table then do nothing
     *
     * @param row
     *            table row number containing the variable name and data type;
     *            view coordinates
     *************************************************************************/
    private void openChildTable(int row)
    {
        // Convert the row to the model index
        int modelRow = table.convertRowIndexToModel(row);

        // Get the data type displayed in the combo box cell
        String dataType = getExpandedValueAt(modelRow, dataTypeIndex);

        // Check that the data type isn't a primitive (i.e., it's a structure)
        // and that this isn't an array definition row
        if (!dataTypeHandler.isPrimitive(dataType)
            && isCanHaveArrays()
            && (ArrayVariable.isArrayMember(getExpandedValueAt(modelRow,
                                                               variableNameIndex))
            || getExpandedValueAt(modelRow, arraySizeIndex).isEmpty()))
        {
            // Get the row's primary key, variable name, and array size
            String rowPrimaryKey = tableModel.getValueAt(modelRow,
                                                         primaryKeyIndex).toString();
            String variableName = getExpandedValueAt(modelRow,
                                                     variableNameIndex);
            String arraySize = getExpandedValueAt(modelRow,
                                                  arraySizeIndex);

            // Get the number of rows that have been committed to the database
            // for this table
            int numCommitted = committedInfo != null
                                                    ? committedInfo.getData().length
                                                    : 0;

            // Step through each row in the committed version of the table data
            for (int comRow = 0; comRow < numCommitted; comRow++)
            {
                // Check if the primary key values match for these rows,
                // indicating this row represents the same one in both the
                // committed and current table data
                if (rowPrimaryKey.equals(committedInfo.getData()[comRow][primaryKeyIndex]))
                {
                    // Check that the variable name isn't blank and if the
                    // variable name, data type, and array size values match
                    // for these rows. Only a child table with these parameters
                    // committed may be opened
                    if (!variableName.isEmpty()
                        && variableName.equals(committedInfo.getData()[comRow][variableNameIndex])
                        && dataType.equals(committedInfo.getData()[comRow][dataTypeIndex])
                        && arraySize.equals(committedInfo.getData()[comRow][arraySizeIndex]))
                    {
                        // Check if the table isn't a prototype (i.e., it's a
                        // child structure), or if it is a prototype that it's
                        // a top-level (root) structure
                        if (!tableInfo.isPrototype() || tableInfo.isRootStructure())
                        {
                            // Load the selected child table's data into a
                            // table editor
                            dbTable.loadTableDataInBackground(tableInfo.getTablePath()
                                                              + ","
                                                              + dataType
                                                              + "."
                                                              + variableName,
                                                              editorDialog);
                        }
                        // The selection is a child of a prototype, and the
                        // prototype isn't a top-level (root) structure
                        else
                        {
                            // Load the selected child table's prototype data
                            // into a table editor. Since the prototype table
                            // is itself a child table, it can't have its own
                            // child tables
                            dbTable.loadTableDataInBackground(dataType,
                                                              editorDialog);

                            // Inform the user that the prototype of the
                            // selected table is opened
                            new CcddDialogHandler().showMessageDialog(editorDialog,
                                                                      "<html><b>Since prototype table '</b>"
                                                                          + tableInfo.getPrototypeName()
                                                                          + "<b>' is a child of another table it "
                                                                          + "cannot have its own child tables; "
                                                                          + "therefore the <i>prototype</i>, "
                                                                          + "instead of an instance, of table '</b>"
                                                                          + dataType
                                                                          + "<b>' was opened",
                                                                      "Edit Table",
                                                                      JOptionPane.INFORMATION_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                    }

                    // Stop searching for a matching key
                    break;
                }
            }
        }
    }

    /**************************************************************************
     * Set up the combo box containing the available primitive data types and
     * existing structure tables for display in the table's "Data Type" column
     * cells. Set up the command argument groups and create combo box(es)
     * containing the available primitive data types for display in the table's
     * column cells with a 'data type' input type (excluding the "Data Type"
     * column), and associate enumeration, minimum, and maximum columns with
     * the data type column
     *
     * @param allStructTbls
     *            array containing all structure table names; null to load the
     *            table names from the database
     *
     * @param tblTree
     *            reference to the CcddTableTreeHandler table tree; null to
     *            create the table tree
     *************************************************************************/
    protected void setUpDataTypeColumns(String[] allStructTbls,
                                        CcddTableTreeHandler tblTree)
    {
        // (Re)create a list to contain data type and enumeration pairings, and
        // command argument column groupings
        associatedColumns = new ArrayList<AssociatedColumns>();

        // Set to null to force the enumeration cell editor and associated
        // combo box to be rebuilt
        enumDataTypeCellEditor = null;

        // Set up the structure table data type column
        setUpDataTypeColumn(allStructTbls, tblTree);

        // Set up the command table argument data type column(s)
        setUpCommandArgumentColumns();
    }

    /**************************************************************************
     * Set up or update the combo box containing the available primitive data
     * types and existing structure tables for display in the table's 'Data
     * Type' column cells
     *
     * @param allStructTbls
     *            array containing all structure table names; null to load the
     *            table names from the database
     *
     * @param tblTree
     *            reference to the CcddTableTreeHandler table tree; null to
     *            create the table tree
     *************************************************************************/
    private void setUpDataTypeColumn(String[] allStructTbls,
                                     CcddTableTreeHandler tblTree)
    {
        // Check if a cell is currently being edited
        if (table.getCellEditor() != null)
        {
            // Incorporate any cell changes and terminate editing
            table.getCellEditor().stopCellEditing();
        }

        // Check if this table has a data type column
        if (dataTypeIndex != -1)
        {
            // Get the array of structure tables, if any
            allStructureTables = (allStructTbls == null)
                                                        ? dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE)
                                                        : allStructTbls;

            // Get the table tree
            tableTree = (tblTree == null)
                                         ? new CcddTableTreeHandler(ccddMain,
                                                                    TableTreeType.INSTANCE_TABLES,
                                                                    editorDialog)
                                         : tblTree;

            // Get the column reference for the data type column
            TableColumn dataTypeColumn = table.getColumnModel().getColumn(table.convertColumnIndexToView(dataTypeIndex));

            // Create a combo box for displaying data types
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Step through each enumeration column
            for (int enumIndex : enumerationIndex)
            {
                // Add the data type and enumeration column index pair to the
                // list
                associatedColumns.add(new AssociatedColumns(dataTypeIndex,
                                                            enumIndex));

                // Create the cell editor for enumerated data types
                createEnumDataTypeCellEditor();
            }

            // Set the column table editor to the combo box
            dataTypeColumn.setCellEditor(new DefaultCellEditor(comboBox));

            // Step through each primitive data type
            for (String[] dataType : dataTypeHandler.getDataTypeData())
            {
                // Add the data type to the combo box list
                comboBox.addItem(CcddDataTypeHandler.getDataTypeName(dataType));
            }

            // Check if any structure tables exist
            if (allStructureTables != null && allStructureTables.length != 0)
            {
                // Sort the array of structure table names alphabetically,
                // ignoring case. This ordering should match the ordering in
                // the table tree (which is determined by a PostgreSQL sort)
                Arrays.sort(allStructureTables, String.CASE_INSENSITIVE_ORDER);

                // Step through each structure table
                for (String structure : allStructureTables)
                {
                    // Check that this structure is not referenced is the
                    // table's tree; otherwise use of the structure would
                    // constitute a recursive reference
                    if (!tableTree.isTargetInTablePath(tableInfo.getProtoVariableName(),
                                                       structure))
                    {
                        // Since the structure isn't in this table's tree path
                        // add the structure table name to the combo box list
                        comboBox.addItem(structure);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Set up the command argument groups. Create combo box(es) containing the
     * available primitive data types for display in the table's column cells
     * with a 'data type' input type (excluding the "Data Type" column), and
     * associate enumeration, minimum, and maximum columns with the data type
     * column
     *************************************************************************/
    private void setUpCommandArgumentColumns()
    {
        // Get the associated command argument columns
        associatedColumns.addAll(typeDefn.getAssociatedCommandColumns(false));

        PaddedComboBox comboBox = null;

        // Step through each column defined for this table's type
        for (int index = 0; index < typeDefn.getColumnCountDatabase(); index++)
        {
            // Check if the column expects a primitive data type input
            if (typeDefn.getInputTypes()[index] == InputDataType.PRIMITIVE)
            {
                // Check if the combo box hasn't been created
                if (comboBox == null)
                {
                    // Create a combo box and set its color and font
                    comboBox = new PaddedComboBox(table.getFont());

                    // Step through each primitive data type
                    for (String[] dataType : dataTypeHandler.getDataTypeData())
                    {
                        // Add the data type to the combo box list
                        comboBox.addItem(CcddDataTypeHandler.getDataTypeName(dataType));
                    }

                    // Create the cell editor for enumerated data types if it
                    // doesn't exist
                    createEnumDataTypeCellEditor();
                }

                // Get the column reference for this data type column
                TableColumn dataTypeColumn = table.getColumnModel().getColumn(table.convertColumnIndexToView(index));

                // Set the column table editor to the combo box
                dataTypeColumn.setCellEditor(new DefaultCellEditor(comboBox));
            }
        }
    }

    /**************************************************************************
     * Set up the minimum/maximum value groups and associate these columns with
     * the data type column. Only those minimum and maximum columns not already
     * associated with a command argument group are included in these pairings
     *************************************************************************/
    private void setUpMinMaxColumns()
    {
        // Create a list to contain minimum/maximum column associations
        minMaxPair = new ArrayList<MinMaxPair>();

        // Initialize the minimum and maximum columns and the index of the data
        // type/enumeration column already
        // paired (if any)
        int minIndex = 0;
        int maxIndex = 0;

        // Step through each column defined for this table's type
        for (int index = 0; index < typeDefn.getColumnCountDatabase(); index++)
        {
            // Initialize the minimum and maximum column indices
            int minColumn = -1;
            int maxColumn = -1;

            // Step through the remaining columns to find the next minimum
            // input type column
            for (; minIndex < table.getModel().getColumnCount(); minIndex++)
            {
                // Check that this is a minimum column
                if (typeDefn.getInputTypes()[minIndex] == InputDataType.MINIMUM)
                {
                    // Save the minimum column index, increment the index
                    // for matching up with the next pairing, and stop
                    // searching
                    minColumn = minIndex;
                    minIndex++;
                    break;
                }
            }

            // Step through the remaining columns to find the next maximum
            // input type column
            for (; maxIndex < table.getModel().getColumnCount(); maxIndex++)
            {
                // Check that this is a maximum column
                if (typeDefn.getInputTypes()[maxIndex] == InputDataType.MAXIMUM)
                {
                    // Save the maximum column index, increment the index
                    // for matching up with the next pairing, and stop
                    // searching
                    maxColumn = maxIndex;
                    maxIndex++;
                    break;
                }
            }

            // Step through each command argument column grouping
            for (AssociatedColumns cmdArg : associatedColumns)
            {
                // Check if the command argument's minimum column matches the
                // current minimum column index
                if (cmdArg.getMinimum() == minColumn)
                {
                    // Reset the minimum column index so that it isn't reused
                    minColumn = -1;
                }

                // Check if the command argument's maximum column matches the
                // current maximum column index
                if (cmdArg.getMaximum() == maxColumn)
                {
                    // Reset the maximum column index so that it isn't reused
                    maxColumn = -1;
                }
            }

            // Check if either a minimum or maximum column exists not already
            // associated with a command argument
            if (minColumn != -1 || maxColumn != -1)
            {
                // Add the new minimum/maximum column pairing to the list
                minMaxPair.add(new MinMaxPair(minColumn, maxColumn));
            }
        }
    }

    /**************************************************************************
     * Create the cell editor for enumerated data types if it doesn't already
     * exist
     *************************************************************************/
    private void createEnumDataTypeCellEditor()
    {
        // Check if the data type cell editor for enumerations hasn't been
        // created
        if (enumDataTypeCellEditor == null)
        {
            // Create a combo box for displaying data types for
            // enumerations (integer and unsigned integers only)
            PaddedComboBox enumComboBox = new PaddedComboBox(table.getFont());

            // Step through each primitive data type
            for (String[] dataType : dataTypeHandler.getDataTypeData())
            {
                // Check if the enumeration combo box exists and that this type
                // is an integer or unsigned integer
                if (enumComboBox != null
                    && dataTypeHandler.isInteger(CcddDataTypeHandler.getDataTypeName(dataType)))
                {
                    // Add the data type to the combo box list
                    enumComboBox.addItem(CcddDataTypeHandler.getDataTypeName(dataType));
                }
            }

            // Create the data type cell editor for enumerations
            enumDataTypeCellEditor = new DefaultCellEditor(enumComboBox);
        }
    }

    /**************************************************************************
     * Validate changes to the command argument minimum and maximum value
     * columns
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
     * @param newValueS
     *            new cell contents
     *
     * @param dataTypeColumn
     *            index of the data type column that governs the minimum and
     *            maximum values, model coordinates
     *
     * @param minColumn
     *            index of the minimum value column, model coordinates
     *
     * @param maxColumn
     *            index of the maximum value column, model coordinates
     *************************************************************************/
    private void validateMinMaxContent(List<Object[]> tableData,
                                       int row,
                                       int column,
                                       String newValueS,
                                       int dataTypeColumn,
                                       int minColumn,
                                       int maxColumn) throws CCDDException
    {
        // Get the data type
        String dataType = tableData.get(row)[dataTypeColumn].toString();

        // Check that the data type exists
        if (newDataTypeHandler.isPrimitive(dataType))
        {
            String minVal = "";
            String maxVal = "";

            // Check if the command argument has minimum and maximum values
            if (minColumn != -1 && maxColumn != -1)
            {
                // Store the minimum and maximum cell contents
                minVal = getExpandedValueAt(tableData, row, minColumn);
                maxVal = getExpandedValueAt(tableData, row, maxColumn);
            }

            // Check if the data type is an unsigned integer
            if (newDataTypeHandler.isUnsignedInt(dataType))
            {
                // Check if the value doesn't match the expected input type
                if (!newValueS.matches(InputDataType.INT_NON_NEGATIVE.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; input type '"
                                            + InputDataType.INT_NON_NEGATIVE.getInputName().toLowerCase()
                                            + "' expected");
                }

                // Convert the cell value to an integer
                int value = Integer.valueOf(newValueS);

                // Check if the value is outside the possible bounds for an
                // unsigned integer of this data type's size
                if (value < (int) newDataTypeHandler.getMinimum(dataType)
                    || value > (int) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; must be greater than "
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + " and less than "
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the
                // minimum is greater than the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Integer.valueOf(minVal) > Integer.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; the minimum must be less than or equal to the maximum");
                }
            }
            // Check if the data type is a signed integer (an unsigned integer
            // was already accounted for above)
            else if (newDataTypeHandler.isInteger(dataType))
            {
                // Check if the value doesn't match the expected input type
                if (!newValueS.matches(InputDataType.INTEGER.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; input type '"
                                            + InputDataType.INTEGER.getInputName().toLowerCase()
                                            + "' expected");
                }

                // Convert the cell value to an integer
                int value = Integer.valueOf(newValueS);

                // Check if the value is outside the possible bounds for a
                // signed integer of this data type's size
                if (value < (int) newDataTypeHandler.getMinimum(dataType)
                    || value > (int) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; must be greater than "
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + " and less than "
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the
                // minimum is greater than the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Integer.valueOf(minVal) > Integer.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; the minimum must be less than or equal to the maximum");
                }
            }
            // Check if the data type is a floating point
            else if (newDataTypeHandler.isFloat(dataType))
            {
                // Check if the value doesn't match the expected input type
                if (!newValueS.matches(InputDataType.FLOAT.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; input type '"
                                            + InputDataType.FLOAT.getInputName().toLowerCase()
                                            + "' expected");
                }

                // Convert the cell value to a floating point
                double value = Double.valueOf(newValueS);

                // Check if the value is outside the possible bounds for a
                // floating point of this data type's size
                if (value < (double) newDataTypeHandler.getMinimum(dataType)
                    || value > (double) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; must be greater than "
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + " and less than "
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the
                // minimum is greater than the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Double.valueOf(minVal) > Double.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value for column '"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "'; the minimum must be less than or equal to the maximum");
                }
            }
        }
    }

    /**************************************************************************
     * Store the variable name, array size, and bit number column indices if
     * the table contains these columns, and create a row filter to show/hide
     * the array member rows if an array size column exists
     *************************************************************************/
    private void setUpArraySizeColumn()
    {
        // Check if the variable name and array size columns are present
        if (isCanHaveArrays())
        {
            // Set the flag to initially display each array as a single group
            isShowArrayMembers = false;

            // Create a row filter for displaying the arrays
            rowFilter = new RowFilter<TableModel, Object>()
            {
                /**************************************************************
                 * Determine if the row should be displayed
                 *************************************************************/
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry)
                {
                    // Hide the row if it represents an array member and the
                    // display flag is false; otherwise display the row
                    return !(!isShowArrayMembers
                    && ArrayVariable.isArrayMember(entry.getValue(variableNameIndex)));
                }
            };
        }
    }

    /**************************************************************************
     * Update the array size, variable name, or data type, and add or remove
     * array members if needed
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param arraySizeOld
     *            array of original array sizes
     *
     * @param arraySizeNew
     *            array of updated array sizes
     *
     * @param definitionRow
     *            array size definition row index
     *
     * @param columnChanged
     *            table model index of the column being changed
     *************************************************************************/
    private void adjustArrayMember(List<Object[]> tableData,
                                   int[] arraySizeOld,
                                   int[] arraySizeNew,
                                   int definitionRow,
                                   int columnChanged)
    {
        // Initialize the total original and updated array size values to 1 so
        // that the size calculations below can be made
        int totalSizeOld = 1;
        int totalSizeNew = 1;

        // Check if the variable name is blank
        if (getExpandedValueAt(tableData, definitionRow, variableNameIndex).isEmpty())
        {
            // Set the updated array size to indicate no array
            arraySizeNew = new int[0];
            tableData.get(definitionRow)[arraySizeIndex] = "";
        }

        // Step through each original array index value
        for (int size : arraySizeOld)
        {
            // Multiply this index value by the running total
            totalSizeOld *= size;
        }

        // Step through each updated array index value
        for (int size : arraySizeNew)
        {
            // Multiply this index value by the running total
            totalSizeNew *= size;
        }

        // Check if the original array size had no array index value(s)
        if (totalSizeOld == 1)
        {
            // Set the total to zero
            totalSizeOld = 0;
        }

        // Check if the updated array size had no array index value(s)
        if (totalSizeNew == 1)
        {
            // Set the total to zero
            totalSizeNew = 0;
        }

        // Calculate the change in the number of array members and the initial
        // row model to act on
        int deltaRows = totalSizeNew - totalSizeOld;
        int lastRow = definitionRow + totalSizeOld;

        // Create storage for a copy of the original array values. This copy is
        // used to restore array member column values
        List<Object[]> arrayData = new ArrayList<Object[]>();

        // Step through each row currently in the array
        for (int row = definitionRow + 1; row <= lastRow; row++)
        {
            // Copy the row
            arrayData.add(Arrays.copyOf(tableData.get(row),
                                        tableData.get(row).length));
        }

        // Check if members are to be added
        if (deltaRows > 0)
        {
            // Create an empty row the new array member
            Object[] rowData = table.getEmptyRow();

            // Continue to add rows until the target value is reached
            while (deltaRows > 0)
            {
                // Insert the new array member row
                lastRow++;
                tableData.add(lastRow, Arrays.copyOf(rowData, rowData.length));
                deltaRows--;
            }
        }
        // Check if members are to be removed
        else if (deltaRows < 0)
        {
            // Continue to delete rows until the target value is reached
            while (deltaRows < 0)
            {
                // Remove the array member row
                tableData.remove(lastRow);
                lastRow--;
                deltaRows++;
            }
        }

        // Initialize an array index counter to all zeroes
        int[] counter = new int[arraySizeNew.length];

        // Copy the array definition row and blank the primary key and row
        // indices to prevent duplicate values for these columns
        Object[] definitionData = Arrays.copyOf(tableData.get(definitionRow),
                                                tableData.get(definitionRow).length);
        definitionData[primaryKeyIndex] = "";
        definitionData[rowIndex] = "";

        // Step through each array member
        for (int tableRow = definitionRow + 1; tableRow <= lastRow; tableRow++)
        {
            // Copy the array definition to the array member row
            tableData.set(tableRow, Arrays.copyOf(definitionData,
                                                  definitionData.length));

            // Append the array index value(s) to the variable name for display
            // as the variable name in the array member row
            tableData.get(tableRow)[variableNameIndex] += ArrayVariable.formatArrayIndex(counter);

            // Check if the number of array dimensions is unchanged. For this
            // case the existing values for the array members are restored for
            // those array members that still exist
            if (arraySizeOld.length == arraySizeNew.length)
            {
                // Step through each row in the original array
                for (int arrayRow = 0; arrayRow < arrayData.size(); arrayRow++)
                {
                    // Check if the new array variable member name matches the
                    // original array variable member name
                    if (ArrayVariable.getVariableArrayIndex(getExpandedValueAt(tableData,
                                                                               tableRow,
                                                                               variableNameIndex))
                                     .equals(ArrayVariable.getVariableArrayIndex(getExpandedValueAt(arrayData,
                                                                                                    arrayRow,
                                                                                                    variableNameIndex))))
                    {
                        // Step through each column in the row
                        for (int column = 0; column < arrayData.get(arrayRow).length; column++)
                        {
                            // Check that this isn't a column that should be
                            // skipped
                            if (column != columnChanged
                                && column != variableNameIndex
                                && column != dataTypeIndex
                                && column != arraySizeIndex
                                && column != variablePathIndex)
                            {
                                // Copy the column value from the original row
                                // to the updated row
                                tableData.get(tableRow)[column] = arrayData.get(arrayRow)[column];
                            }
                        }

                        break;
                    }
                }
            }

            // Step through the array index values, starting with the last and
            // working backward
            for (int index = counter.length - 1; index >= 0; index--)
            {
                // Increment the array index
                counter[index]++;

                // Check if the array index hasn't reached its limit
                if (counter[index] < arraySizeNew[index])
                {
                    // Stop adjusting the array index values
                    break;
                }

                // Reset this array index back to zero and continue looping to
                // adjust the next array index up the chain
                counter[index] = 0;
            }

            // Check if the variable path column is present
            if (variablePathIndex != -1)
            {
                // Update the array member variable paths
                tableData.get(tableRow)[variablePathIndex] = getVariablePath(tableData.get(tableRow)[variableNameIndex].toString(),
                                                                             tableData.get(tableRow)[dataTypeIndex].toString());
            }
        }

        // Check if the number of array dimensions is unchanged and that this
        // is a string array
        if (arraySizeOld.length == arraySizeNew.length
            && newDataTypeHandler.isString(getExpandedValueAt(tableData,
                                                              definitionRow,
                                                              dataTypeIndex)))
        {
            // Step through each array member
            for (int row = definitionRow + 1; row <= lastRow; row++)
            {
                // Check if this row is the initial member of a string
                if (tableData.get(row)[variableNameIndex].toString().endsWith("[0]"))
                {
                    // Step through each column in the string array
                    for (int column = 0; column < tableData.get(row).length; column++)
                    {
                        // Check that this isn't a column that should be
                        // skipped
                        if (column != primaryKeyIndex
                            && column != rowIndex
                            && column != variableNameIndex
                            && column != dataTypeIndex
                            && column != arraySizeIndex)
                        {
                            // Propagate the value to every member of the
                            // string
                            propagateArrayValues(tableData,
                                                 row,
                                                 column);
                        }
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Toggle the display of array member rows
     *************************************************************************/
    protected void showHideArrayMembers()
    {
        // Toggle the display of arrays as a group or as individual members
        isShowArrayMembers = !isShowArrayMembers;

        // Issue a table change event so the array member rows are shown/hidden
        tableModel.fireTableDataChanged();
        tableModel.fireTableStructureChanged();

        // Check that the table is open in a table editor (versus open for a
        // macro name and/or value change)
        if (editorDialog != null)
        {
            // Update the expand/collapse arrays check box
            editorDialog.updateExpandArrayCheckBox();
        }
    }

    /**************************************************************************
     * Set up or update the combo box containing the allowable sample rates for
     * display in the table's "Rate" column cells
     *************************************************************************/
    protected void setUpSampleRateColumn()
    {
        // Step through each rate column defined for this table
        for (int index = 0; index < rateIndex.size(); index++)
        {
            // Get the column reference for the rate column
            TableColumn rateColumn = table.getColumnModel().getColumn(table.convertColumnIndexToView(rateIndex.get(index)));

            // Set to true if this is an update to the combo box content update
            // and not the original combo box setup at table creation)
            boolean isUpdate = (DefaultCellEditor) rateColumn.getCellEditor() != null
                               && ((DefaultCellEditor) rateColumn.getCellEditor()).getComponent() instanceof PaddedComboBox;

            // Check if this is a combo box content update
            if (isUpdate)
            {
                // End editing on the rate cell in case a rate cell is
                // selected. This causes the selected cell to be updated with
                // the new rates
                ((DefaultCellEditor) rateColumn.getCellEditor()).stopCellEditing();
            }

            // Create a combo box and set the color and font
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Set the column table editor to the combo box
            rateColumn.setCellEditor(new DefaultCellEditor(comboBox));

            // Get the rate information for this rate column
            RateInformation rateInfo = rateHandler.getRateInformationByRateName(typeDefn.getColumnNamesUser()[rateIndex.get(index)]);

            // Check if the rate information exists for this column
            if (rateInfo != null)
            {
                // Make the first item a blank
                comboBox.addItem("");

                // Step through each sample rate
                for (String rate : rateInfo.getSampleRates())
                {
                    // Add the sample rate to the combo box list
                    comboBox.addItem(rate);
                }
            }

            // Check if this is a combo box content update
            if (isUpdate)
            {
                // Force the table to redraw in case the rates have been
                // altered such that a rate cell's validity changes and the
                // background color needs to be updated
                table.repaint();
            }
        }
    }

    /**************************************************************************
     * Update the rates for all bit-wise variables that are packed together
     * with the bit-wise variable in the table
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param startRow
     *            initial row to begin the check at, in model coordinates
     *
     * @param useRowRate
     *            true if the rate value for this row is used as the rate for
     *            the packed variables; false to use the rate of the first
     *            variable in the pack
     *************************************************************************/
    private void setAllPackedVariableRates(List<Object[]> tableData,
                                           int startRow,
                                           boolean useRowRate)
    {
        // Check if the table can have packed variables
        if (dataTypeIndex != -1
            && bitLengthIndex != -1
            && !rateIndex.isEmpty())
        {
            // Step through each table row
            for (int row = startRow; row < tableData.size(); row++)
            {
                // Check if the variable has a bit length
                if (!tableData.get(row)[bitLengthIndex].toString().isEmpty())
                {
                    // Set the rate for the packed variables adjacent to this
                    // variable
                    row = setPackedVariableRates(tableData, row, useRowRate);

                    // Set the flag so that all subsequent packing is based on
                    // the first variable in the pack
                    useRowRate = false;
                }
            }
        }
    }

    /**************************************************************************
     * Update the rates for any other bit-wise variables that are packed
     * together with the bit-wise variable in the specified row
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param row
     *            table model row index
     *
     * @param useRowRate
     *            true if the rate value for this row is used as the rate for
     *            the packed variables; false to use the rate of the first
     *            variable in the pack
     *
     * @return Index of the last packed row. In addition, the global
     *         isRateChange flag is set to true if a rate value is updated, and
     *         false otherwise
     *************************************************************************/
    private int setPackedVariableRates(List<Object[]> tableData,
                                       int row,
                                       boolean useRowRate)
    {
        isRateChange = false;

        // Get the number of bytes occupied by this variable
        String dataType = tableData.get(row)[dataTypeIndex].toString();
        int dataTypeBitSize = dataTypeHandler.getSizeInBits(dataType);

        int curRow = row - 1;

        // Step backwards through the table while a variable with a bit length
        // of the same data type is found
        while (curRow >= 0
               && !getExpandedValueAt(tableData, curRow, bitLengthIndex).isEmpty()
               && dataType.equals(tableData.get(curRow)[dataTypeIndex].toString()))
        {
            // Go to the previous row
            curRow--;
        }

        // Store the first row in the pack
        curRow++;
        int firstRow = curRow;

        int bitCount = 0;
        boolean isTargetPack = false;

        // Step forward, packing the bits, in order to determine the variables
        // in the target row's pack
        while (curRow < tableData.size()
               && !getExpandedValueAt(tableData, curRow, bitLengthIndex).isEmpty()
               && dataType.equals(tableData.get(curRow)[dataTypeIndex].toString()))
        {
            // Add the number of bits occupied by this variable to the running
            // count
            int numBits = Integer.valueOf(getExpandedValueAt(tableData,
                                                             curRow,
                                                             bitLengthIndex));
            bitCount += numBits;

            // Check if the bit count rolled over the maximum allowed
            if (bitCount > dataTypeBitSize)
            {
                // Check if the target variable is included
                if (isTargetPack)
                {
                    // Stop searching
                    break;
                }

                // Reset the bit count to the current row's value and store the
                // row index for the first variable in the pack
                bitCount = numBits;
                firstRow = curRow;
            }

            // Check if the target row is reached
            if (curRow == row)
            {
                // Set the flag indicating this pack includes the target
                // variable
                isTargetPack = true;
            }

            curRow++;
        }

        // Store the last row in the pack
        int lastRow = curRow - 1;

        // Set the row that has the governing rate
        int theRow = useRowRate ? row : firstRow;

        // Step through each rate column
        for (int column : rateIndex)
        {
            // Step through the rows for the variables that are packed together
            for (curRow = firstRow; curRow <= lastRow; curRow++)
            {
                // Check that this isn't the target's row
                if (tableData.get(curRow)[column] != tableData.get(theRow)[column])
                {
                    // Set the rate for this variable to match that of the
                    // target row's variable and set the flag indicating a rate
                    // value changed
                    tableData.get(curRow)[column] = tableData.get(theRow)[column];
                    isRateChange = true;
                }
            }
        }

        return lastRow;
    }

    /**************************************************************************
     * Propagate the value in the specified column of an array definition row
     * to each member of the array. For a string array if an array member is
     * changed then only propagate the value to the other members of that
     * string
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param row
     *            table model row index for the array definition or for the
     *            first member of a string array
     *
     * @param columnChanged
     *            table model index of the column being changed
     *************************************************************************/
    private void propagateArrayValues(List<Object[]> tableData,
                                      int firstRow,
                                      int columnChanged)
    {
        // Get the variable name
        String variableName = getExpandedValueAt(tableData,
                                                 firstRow,
                                                 variableNameIndex);

        // Set to true if the updated row is the array's definition. Set to
        // false to indicate that only the members of the string indicated by
        // the specified row are to be updated
        boolean isArrayDefn = !variableName.endsWith("]");

        // Check if this is an initial string member or if the flag is set that
        // prevents overwriting array members (and the table is open in an
        // editor)
        if (!isArrayDefn
            || editorDialog == null
            || !editorDialog.isArrayOverwriteNone())
        {
            // Get the variable name without the array index. If only one
            // string in an array of strings is having its value changed then
            // include the array index portions other than those that define
            // the string's length
            variableName = isArrayDefn
                                      ? ArrayVariable.removeArrayIndex(variableName)
                                      : ArrayVariable.removeStringSize(variableName);

            int lastRow = firstRow;

            // Step forward in order to determine the ending row for this
            // array/string by comparing the variable name in the current row
            // to that in the first row
            do
            {
                lastRow++;
            } while (lastRow < tableData.size()
                     && variableName.equals(isArrayDefn
                                                       ? ArrayVariable.removeArrayIndex(getExpandedValueAt(tableData,
                                                                                                           lastRow,
                                                                                                           variableNameIndex))
                                                       : ArrayVariable.removeStringSize(getExpandedValueAt(tableData,
                                                                                                           lastRow,
                                                                                                           variableNameIndex))));
            lastRow--;

            // Step through the array member rows
            for (int curRow = firstRow + 1; curRow <= lastRow; curRow++)
            {
                // Check if this is an initial string member or if the flag
                // that allows all array members to be overwritten is set (and
                // the table is open in an editor) or if the current row's
                // column is empty
                if (!isArrayDefn
                    || getExpandedValueAt(tableData, curRow, columnChanged).isEmpty()
                    || editorDialog == null
                    || editorDialog.isArrayOverwriteAll())
                {
                    // Set the value for this member to match that of the first
                    // row
                    tableData.get(curRow)[columnChanged] = tableData.get(firstRow)[columnChanged];
                }
            }
        }
    }

    /**************************************************************************
     * Update the row indices to match the current row order
     *************************************************************************/
    private void updateRowIndices()
    {
        int index = 1;

        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Skip any empty rows
            row = table.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < tableModel.getRowCount())
            {
                // Update the row index
                tableModel.setValueAt(String.valueOf(index), row, rowIndex);
                index++;
            }
        }

        // Flag the end of the editing sequence for undo/redo purposes
        table.getUndoManager().endEditSequence();
    }

    /**************************************************************************
     * Update the data fields to match the current field information
     *************************************************************************/
    protected void updateDataFields()
    {
        // Get the reference to the table's field handler to shorten subsequent
        // calls
        CcddFieldHandler fieldHandler = tableInfo.getFieldHandler();

        // Update the field information with the current text field values
        updateCurrentFields(fieldHandler.getFieldInformation());

        // Rebuild the data field panel in the table editor using the updated
        // fields
        createDataFieldPanel(true);
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
        boolean isChanged = false;

        // Check if table editing is disabled. If the editor dialog command to
        // show macros is selected then the table edit flag is false and the
        // macros are displayed
        if (!isEditEnabled)
        {
            // Display the macros in place of the macro values. If the macros
            // aren't restored then the comparison detects every expanded macro
            // as a change; restoring the macros prevents this erroneous change
            // indication
            expandMacros(false, false);
        }

        // Check if the table has changes, ignoring the variable path column
        // (if present)
        isChanged = table.isTableChanged(committedInfo.getData(),
                                         Arrays.asList(new Integer[] {variablePathIndex}));

        // Check if table editing is disabled. If the editor dialog command to
        // show macros is selected then the table edit flag is false and the
        // macros are displayed
        if (!isEditEnabled)
        {
            // Redisplay the macro values in place of the macros
            expandMacros(true, false);
        }

        return isChanged;
    }

    /**************************************************************************
     * Compare the current table data to the committed table data and create
     * lists of the changed values necessary to update the table in the
     * database to match the current values
     *************************************************************************/
    protected void buildUpdates()
    {
        // Note: The committed table information class is updated during the
        // commit procedure, which can produce a race condition if it's used
        // for change comparisons

        // Check that the table is open in a table editor (versus open for a
        // macro name and/or value change)
        if (editorDialog != null)
        {
            // Store the description into the table information class so that
            // it can be used to compare if a change occurred
            tableInfo.setDescription(getDescription());
        }

        // Store the most recently committed table data and column order into
        // the table information class so that it can be used to compare if a
        // change occurred
        tableInfo.setData(committedInfo.getData());
        tableInfo.setColumnOrder(table.getColumnOrder());

        // Remove change information from a previous commit, if any
        additions.clear();
        modifications.clear();
        deletions.clear();

        // Re-index the rows in case any have been added, moved, or deleted
        updateRowIndices();

        // Get the number of rows that have been committed to the database for
        // this table
        int numCommitted = committedInfo != null
                                                ? committedInfo.getData().length
                                                : 0;

        // Get the table cell values
        Object[][] tableData = table.getTableData(true);

        // Create storage for the array used to indicate if a row has a match
        // between the current and committed data
        boolean[] rowFound = new boolean[committedInfo.getData().length];

        // Step through each row in the table
        for (int tblRow = 0; tblRow < tableData.length; tblRow++)
        {
            boolean matchFound = false;

            // Check if the variable path column exists
            if (variablePathIndex != -1)
            {
                // Blank the data for this column so that it isn't stored in
                // the database (the path is constructed on-the-fly for display
                // in the table)
                tableData[tblRow][variablePathIndex] = "";
            }

            // Step through each row in the committed version of the table data
            for (int comRow = 0; comRow < numCommitted && !matchFound; comRow++)
            {
                // Check if the primary key values match for these rows
                if (tableData[tblRow][primaryKeyIndex].equals(committedInfo.getData()[comRow][primaryKeyIndex]))
                {
                    // Set the flags indicating this row has a match
                    rowFound[comRow] = true;
                    matchFound = true;

                    boolean isChangedColumn = false;

                    // Step through each column in the row
                    for (int column = 0; column < tableData[tblRow].length; column++)
                    {
                        // Check if the current and committed values don't
                        // match and this isn't the variable path column
                        if (!tableData[tblRow][column].equals(committedInfo.getData()[comRow][column])
                            && column != variablePathIndex)
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
                                                                committedInfo.getData()[comRow],
                                                                variableNameIndex,
                                                                dataTypeIndex,
                                                                arraySizeIndex,
                                                                bitLengthIndex,
                                                                rateIndex));
                    }
                }
            }

            // Check if no matching row was found with the current data
            if (!matchFound)
            {
                // Store the row addition information
                additions.add(new TableModification(tableData[tblRow],
                                                    variableNameIndex,
                                                    dataTypeIndex,
                                                    arraySizeIndex,
                                                    bitLengthIndex));
            }
        }

        // Step through each row of the committed data
        for (int comRow = 0; comRow < numCommitted; comRow++)
        {
            // Check if no matching row was found with the current data
            if (!rowFound[comRow])
            {
                // Store the row deletion information
                deletions.add(new TableModification(committedInfo.getData()[comRow],
                                                    variableNameIndex,
                                                    dataTypeIndex,
                                                    arraySizeIndex,
                                                    bitLengthIndex));
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

        // Check if the table represents a structure
        if (isStructure())
        {
            // Step through each row in the table
            for (int row = 0; row < table.getRowCount() && !stopCheck; row++)
            {
                // Skip rows in the table that are empty
                row = table.getNextPopulatedRowNumber(row);

                // Check that the end of the table hasn't been reached and if a
                // required column is empty
                if (row < table.getRowCount())
                {
                    // Step through the columns required for a structure
                    // variable
                    for (int column : new int[] {variableNameIndex, dataTypeIndex})
                    {
                        // Convert the column to view coordinates
                        column = table.convertColumnIndexToView(column);

                        // Check if the cell is empty
                        if (table.getValueAt(row, column).toString().isEmpty())
                        {
                            // Set the 'data is missing' flag
                            dataIsMissing = true;

                            // Inform the user that a row is missing required
                            // data. If Cancel is selected then do not perform
                            // checks on other columns and rows
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
        }

        return dataIsMissing;
    }

    /**************************************************************************
     * Reset the order of the columns to the default, as specified by the
     * table's type definition
     *************************************************************************/
    protected void resetColumnOrder()
    {
        // Set the column order to the default
        table.arrangeColumns(tableTypeHandler.getDefaultColumnOrder(typeDefn.getName()));
    }

    /**********************************************************************
     * Update the tab for this table in the table editor dialog change
     * indicator
     *********************************************************************/
    @Override
    protected void updateOwnerChangeIndicator()
    {
        editorDialog.updateChangeIndicator(this);
    }
}
