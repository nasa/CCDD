/**
 * CFS Command and Data Dictionary table editor handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.DEFAULT_HIDE_DATA_TYPE;
import static CCDD.CcddConstants.DEFAULT_TYPE_NAME_SEP;
import static CCDD.CcddConstants.DEFAULT_VARIABLE_PATH_SEP;
import static CCDD.CcddConstants.HIDE_DATA_TYPE;
import static CCDD.CcddConstants.IGNORE_BUTTON;
import static CCDD.CcddConstants.LAF_SCROLL_BAR_WIDTH;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.PAD_VARIABLE_MATCH;
import static CCDD.CcddConstants.REPLACE_INDICATOR;
import static CCDD.CcddConstants.TYPE_NAME_SEPARATOR;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.VARIABLE_PATH_SEPARATOR;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
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
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.BitPackRowIndex;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.MinMaxPair;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddTableTypeHandler.TypeDefinition;
import CCDD.CcddUndoHandler.UndoableTableModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary table editor handler class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableEditorHandler extends CcddInputFieldPanelHandler
{
    // Class references
    private final CcddMain ccddMain;
    private CcddTableEditorDialog editorDialog;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddDataTypeHandler newDataTypeHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddMacroHandler newMacroHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddVariableHandler variableHandler;
    private final CcddInputTypeHandler inputTypeHandler;
    private final CcddFieldHandler fieldHandler;
    private CcddJTableHandler table;
    private final TableInformation currentTableInfo;
    private TableInformation committedTableInfo;
    private UndoableTableModel tableModel;
    private TypeDefinition typeDefn;

    // GUI component over which to center any error dialog. This is the editor dialog to which the
    // editor belongs if the dialog is open
    private final Component parent;

    // Cell editor for data type cell in a row that has an enumeration
    private DefaultCellEditor enumDataTypeCellEditor;

    // List containing associated data type and enumeration pairings, and command argument name,
    // data type, enumeration, minimum, and maximum column groupings
    private List<AssociatedColumns> associatedColumns;

    // List containing associated minimum and maximum column indices not already associated with a
    // command argument
    private List<MinMaxPair> minMaxPair;

    // Array containing the names of all structure tables
    private String[] allPrototypeStructureTables;

    // Column header tool tip text
    private String[] toolTips;

    // Column indices for the primary key, row index, variable path, message ID name(s), and
    // variable path (in model coordinates)
    private final int primaryKeyIndex;
    private final int rowIndex;
    private List<Integer> msgIDNameIndex;
    private int variablePathIndex;

    // Column indices for the variable name, data type, array size, bit length, enumeration(s), and
    // rate(s) (in model coordinates). These are only set for structure tables representing
    // structures
    private int variableNameIndex;
    private int dataTypeIndex;
    private int arraySizeIndex;
    private int bitLengthIndex;
    private List<Integer> enumerationIndex;
    private List<Integer> rateIndex;

    // Column indices for user-defined selection columns
    private List<Integer> selectionIndex;

    // Variable path separators and flag to show/hide the data type
    private String varPathSeparator;
    private String typeNameSeparator;
    private boolean excludeDataTypes;

    // Flag indicating if array members are to be displayed in the table
    private boolean isShowArrayMembers;

    // Flag indicating a rate value changed while checking packed bit-wise variables
    private boolean isRateChange;

    // Row filter, used to show/hide array members
    private RowFilter<TableModel, Object> rowFilter;

    // Temporary table cell storage for when macro names are replaced by their corresponding values
    // so that the original cell contents can be restored
    private Object[][] originalCellData;

    // Lists of table content changes to process
    private final List<TableModification> additions;
    private final List<TableModification> modifications;
    private final List<TableModification> deletions;

    // Flag indicating if the table can be edited
    private boolean isEditEnabled;

    // List containing the primitive data types and structures that can be referenced by this table
    // (if it is a structure)
    private List<String> validDataTypes;

    // List containing the structures that can be referenced by this table (if it is a structure)
    private List<String> validStructureDataTypes;

    // List containing the structures that can't be referenced by this table (if it is a
    // structure). This includes the structure itself and all of its ancestor structures
    private List<String> invalidDataTypes;

    // Pattern for matching the replace indicator
    private final Pattern replacePattern;

    /**********************************************************************************************
     * Table editor handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param tableTypeHandler
     *            table type handler reference
     *
     * @param inputTypeHandler
     *            input type handler reference
     *
     * @param newDataTypeHandler
     *            data type handler reference for when the data type definitions have changed; same
     *            as the main data type handler if this is not a data type change instance
     *
     * @param newMacroHandler
     *            macro handler reference for when the macro names and/or values have changed; same
     *            as the main macro handler if this is not a macro change instance
     *
     * @param editorDialog
     *            editor dialog from which this editor was created
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    CcddTableEditorHandler(CcddMain ccddMain,
                           TableInformation tableInfo,
                           CcddTableTypeHandler tableTypeHandler,
                           CcddInputTypeHandler inputTypeHandler,
                           CcddDataTypeHandler newDataTypeHandler,
                           CcddMacroHandler newMacroHandler,
                           CcddTableEditorDialog editorDialog,
                           Component parent)
    {
        this.ccddMain = ccddMain;
        this.currentTableInfo = tableInfo;
        this.tableTypeHandler = tableTypeHandler;
        this.inputTypeHandler = inputTypeHandler;
        this.newDataTypeHandler = newDataTypeHandler;
        this.newMacroHandler = newMacroHandler;
        this.editorDialog = editorDialog;
        this.parent = parent == null
                                     ? (ccddMain.isGUIHidden()
                                                               ? null
                                                               : ccddMain.getMainFrame())
                                     : parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        variableHandler = ccddMain.getVariableHandler();

        // Initialize the lists of table content changes
        additions = new ArrayList<TableModification>();
        modifications = new ArrayList<TableModification>();
        deletions = new ArrayList<TableModification>();

        // Set the row index and primary key column indices
        rowIndex = DefaultColumn.ROW_INDEX.ordinal();
        primaryKeyIndex = DefaultColumn.PRIMARY_KEY.ordinal();

        // Initialize the structure table information
        allPrototypeStructureTables = null;

        // Set the flag to indicate that editing of the table is allowed
        isEditEnabled = true;

        // Create the replace match pattern
        replacePattern = Pattern.compile("^" + Pattern.quote(REPLACE_INDICATOR));

        // Create the table editor
        initialize();
    }

    /**********************************************************************************************
     * Table editor handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param newDataTypeHandler
     *            data type handler reference for when the data type definitions have changed; same
     *            as the main data type handler if this is not a data type change instance
     *
     * @param newMacroHandler
     *            macro handler reference for when the macro names and/or values have changed; same
     *            as the main macro handler if this is not a macro change instance
     *
     * @param editorDialog
     *            editor dialog from which this editor was created
     *********************************************************************************************/
    CcddTableEditorHandler(CcddMain ccddMain,
                           TableInformation tableInfo,
                           CcddDataTypeHandler newDataTypeHandler,
                           CcddMacroHandler newMacroHandler,
                           CcddTableEditorDialog editorDialog)
    {
        this(ccddMain,
             tableInfo,
             ccddMain.getTableTypeHandler(),
             ccddMain.getInputTypeHandler(),
             newDataTypeHandler,
             newMacroHandler,
             editorDialog,
             editorDialog);
    }

    /**********************************************************************************************
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
     *********************************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddTableEditorDialog editorDialog)
    {
        this(ccddMain,
             tableInfo,
             ccddMain.getTableTypeHandler(),
             ccddMain.getInputTypeHandler(),
             ccddMain.getDataTypeHandler(),
             ccddMain.getMacroHandler(),
             editorDialog,
             editorDialog);
    }

    /**********************************************************************************************
     * Table editor handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param tableTypeHandler
     *            table type handler reference reflecting updates to the input type definitions
     *
     * @param inputTypeHandler
     *            input type handler reference reflecting updates to the input type definitions
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddTableTypeHandler tableTypeHandler,
                                     CcddInputTypeHandler inputTypeHandler,
                                     Component parent)
    {
        this(ccddMain,
             tableInfo,
             tableTypeHandler,
             inputTypeHandler,
             ccddMain.getDataTypeHandler(),
             ccddMain.getMacroHandler(),
             null,
             parent);
    }

    /**********************************************************************************************
     * Table editor handler class constructor for a primitive data type name, size, and/or base
     * type change
     *
     * @param ccddMain
     *            main class
     *
     * @param tableInfo
     *            table information
     *
     * @param newDataTypeHandler
     *            data type handler reference reflecting updates to the data type definitions
     *
     * @param newMacroHandler
     *            macro handler reference reflecting updates to the macro values due to sizeof()
     *            calls
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *********************************************************************************************/
    protected CcddTableEditorHandler(CcddMain ccddMain,
                                     TableInformation tableInfo,
                                     CcddDataTypeHandler newDataTypeHandler,
                                     CcddMacroHandler newMacroHandler,
                                     Component parent)
    {
        this(ccddMain,
             tableInfo,
             ccddMain.getTableTypeHandler(),
             ccddMain.getInputTypeHandler(),
             newDataTypeHandler,
             newMacroHandler,
             null,
             parent);
    }

    /**********************************************************************************************
     * Get the table being edited by this editor
     *
     * @return Table edited by the editor
     *********************************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return table;
    }

    /**********************************************************************************************
     * Get the reference to the table's type definition
     *
     * @return Reference to the table's type definition
     *********************************************************************************************/
    protected TypeDefinition getTableTypeDefinition()
    {
        return typeDefn;
    }

    /**********************************************************************************************
     * Set the reference to the editor dialog to which this editor belongs
     *
     * @param editorDialog
     *            reference to the editor dialog to which this editor belongs
     *********************************************************************************************/
    protected void setEditorDialog(CcddTableEditorDialog editorDialog)
    {
        this.editorDialog = editorDialog;
    }

    /**********************************************************************************************
     * Enable/disable editing of the table
     *
     * @param enable
     *            true to enable normal editing of the table, false to disable editing
     *********************************************************************************************/
    protected void setTableEditEnable(boolean enable)
    {
        isEditEnabled = enable;
    }

    /**********************************************************************************************
     * Update the table information (current and committed) if a change is made to the prototype
     * structure name or a variable name in the structure's prototype
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
     *********************************************************************************************/
    protected void updateTableInformation(String oldPrototype,
                                          String newPrototype,
                                          String oldVariableName,
                                          String newVariableName)
    {
        // Update the table's parent name
        currentTableInfo.setRootTable(currentTableInfo.getRootTable().replaceAll("^"
                                                                                 + Pattern.quote(oldPrototype)
                                                                                 + "$",
                                                                                 newPrototype));

        // Update the data type (prototype name) in the table path
        currentTableInfo.setTablePath(currentTableInfo.getTablePath().replaceAll(","
                                                                                 + Pattern.quote(oldPrototype)
                                                                                 + "\\.",
                                                                                 "," + newPrototype + "."));

        // Check if a variable name changed
        if (oldVariableName != null)
        {
            // Update the variable name in the table path
            currentTableInfo.setTablePath(currentTableInfo.getTablePath().replaceAll("\\."
                                                                                     + Pattern.quote(oldVariableName)
                                                                                     + "($|,)",
                                                                                     "." + newVariableName + "$1"));

            // Update the committed table information
            committedTableInfo.setTablePath(currentTableInfo.getTablePath());
        }

        // Update the committed table information
        committedTableInfo.setRootTable(currentTableInfo.getRootTable());
    }

    /**********************************************************************************************
     * Update the data type column to the new prototype structure name for any references to the
     * original prototype structure name, if this table represents a structure. The update is
     * treated as a committed change
     *
     * @param oldPrototype
     *            original prototype name
     *
     * @param newPrototype
     *            updated prototype name
     *********************************************************************************************/
    protected void updateDataTypeReferences(String oldPrototype, String newPrototype)
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
                    // Replace the data type with the new data type name. Treat the update as
                    // having been committed to the database
                    tableModel.setValueAt(newPrototype, row, dataTypeIndex, false);
                    committedTableInfo.getData()[row][dataTypeIndex] = newPrototype;
                }
            }
        }
    }

    /**********************************************************************************************
     * Set the table name to the table's prototype + variable name
     *********************************************************************************************/
    protected void setTableName()
    {
        // Get the table's prototype/variable name to shorten subsequent calls
        String name = currentTableInfo.getProtoVariableName();

        // Set the name of the table editor's owner
        setOwnerName(name.equals(currentTableInfo.getRootTable())
                                                                  ? name
                                                                  : currentTableInfo.getRootTable() + ": " + name);

        // Check that the table is open in a table editor (versus open for a macro name and/or
        // value change, for example)
        if (editorDialog != null)
        {
            // Set the dialog name so that this dialog can be recognized as being open by the table
            // selection dialog
            editorDialog.setName(name);
        }

        // Set the JTable name so that table change events can be identified with this table
        table.setName(getOwnerName());

        // Check if the table has uncommitted changes
        if (isTableChanged())
        {
            // Send a change event so that the editor tab name reflects that the table has changed
            table.getUndoManager().ownerHasChanged();
        }
    }

    /**********************************************************************************************
     * Set the table type definition
     *********************************************************************************************/
    private void setTypeDefinition()
    {
        // Get the table type definition
        typeDefn = tableTypeHandler.getTypeDefinition(currentTableInfo.getType());

        // Set the column tool tip text
        toolTips = typeDefn.getColumnToolTips();
    }

    /**********************************************************************************************
     * Get the table's tab tool tip text, which consists of the table's type and full path
     *
     * @return Table's tab tool tip text
     *********************************************************************************************/
    protected String getTableToolTip()
    {
        String pathTag = "<b>Full path:</b> ";

        // Build the tool tip text, showing the table type and its full path with the data types
        // highlighted
        String toolTip = CcddUtilities.highlightDataType("<html><b>Table type:</b> "
                                                         + currentTableInfo.getType()
                                                         + "<br>"
                                                         + pathTag
                                                         + currentTableInfo.getTablePath());

        // Create the indentation text; hide this text by coloring it the same as the background
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

    /**********************************************************************************************
     * Get the table model
     *
     * @return Table model
     *********************************************************************************************/
    protected UndoableTableModel getTableModel()
    {
        return tableModel;
    }

    /**********************************************************************************************
     * Get the current table information
     *
     * NOTE: The table cell data does not reflect changes made to the table contents
     *
     * @return Table information class for extracting the current table name, type, and column
     *         order
     *********************************************************************************************/
    protected TableInformation getTableInformation()
    {
        return currentTableInfo;
    }

    /**********************************************************************************************
     * Get the committed table information
     *
     * @return Committed table information class
     *********************************************************************************************/
    protected TableInformation getCommittedTableInformation()
    {
        return committedTableInfo;
    }

    /**********************************************************************************************
     * Set the committed table information
     *
     * @param info
     *            table information class for extracting the current table name, type, column
     *            order, and description
     *********************************************************************************************/
    private void setCommittedInformation(TableInformation info)
    {
        committedTableInfo = new TableInformation(info.getType(),
                                                  info.getTablePath(),
                                                  info.getData(),
                                                  info.getColumnOrder(),
                                                  info.getDescription(),
                                                  (editorDialog != null
                                                                        ? CcddFieldHandler.getFieldInformationCopy(info.getFieldInformation())
                                                                        : null));

        // Check if the table has been created
        if (table != null)
        {
            // Clear the undo/redo cell edits stack
            table.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Set the current and committed the data field information for this table from the field
     * handler's field information
     *********************************************************************************************/
    protected void updateTableFieldInformationFromHandler()
    {
        currentTableInfo.setFieldInformation(fieldHandler.getFieldInformationByOwnerCopy(currentTableInfo.getTablePath()));
        committedTableInfo.setFieldInformation(CcddFieldHandler.getFieldInformationCopy(currentTableInfo.getFieldInformation()));
    }

    /**********************************************************************************************
     * Set the variable path separators to those currently stored in the program preferences and
     * update the variable path column, if present
     *********************************************************************************************/
    protected void updateVariablePaths()
    {
        // Get the variable path separators and the show/hide data type flag from the program
        // preferences
        varPathSeparator = ccddMain.getProgPrefs().get(VARIABLE_PATH_SEPARATOR,
                                                       DEFAULT_VARIABLE_PATH_SEP);
        typeNameSeparator = ccddMain.getProgPrefs().get(TYPE_NAME_SEPARATOR,
                                                        DEFAULT_TYPE_NAME_SEP);
        excludeDataTypes = Boolean.parseBoolean(ccddMain.getProgPrefs().get(HIDE_DATA_TYPE,
                                                                            DEFAULT_HIDE_DATA_TYPE));

        // Check if the variable path, variable name, and data type columns are present, the table
        // represents a structure, the table has been created, and the table is open in a table
        // editor (versus open for a macro or data type change, for example)
        if (variablePathIndex != -1
            && variableNameIndex != -1
            && dataTypeIndex != -1
            && typeDefn.isStructure()
            && table != null
            && editorDialog != null)
        {
            // Step through each row of the data
            for (int row = 0; row < tableModel.getRowCount(); row++)
            {
                // Get the variable name and data type from the table data
                String variableName = tableModel.getValueAt(row, variableNameIndex).toString();
                String dataType = tableModel.getValueAt(row, dataTypeIndex).toString();

                // Check that the variable name and data type are present, the variable path
                // isn't manually set, and this isn't an array definition
                if (!variableName.isEmpty()
                    && !dataType.isEmpty()
                    && (committedTableInfo.getData().length == 0
                        || (row < committedTableInfo.getData().length
                            && committedTableInfo.getData()[row][variablePathIndex].toString().isEmpty()))
                    && (tableModel.getValueAt(row, arraySizeIndex).toString().isEmpty()
                        || ArrayVariable.isArrayMember(tableModel.getValueAt(row, variableNameIndex))))
                {
                    // Build the variable path and store it in the table model's variable path
                    // column
                    tableModel.setValueAt(getVariablePath(variableName, dataType, true),
                                          row,
                                          variablePathIndex,
                                          false);
                }
            }
        }
    }

    /**********************************************************************************************
     * Get the variable path for the specified variable name and data type
     *
     * @param variableName
     *            variable name
     *
     * @param dataType
     *            data type
     *
     * @param includeCustom
     *            true to substitute the user-defined variable path (if present); false to ignore
     *            the user-defined path and use the auto-generated one based on the conversion
     *            flags
     *
     * @return Variable path for the specified variable name and data type
     *********************************************************************************************/
    private String getVariablePath(String variableName, String dataType, boolean includeCustom)
    {
        return variableHandler.getVariablePath(currentTableInfo.getTablePath(),
                                               variableName,
                                               dataType,
                                               varPathSeparator,
                                               excludeDataTypes,
                                               typeNameSeparator,
                                               includeCustom);
    }

    /**********************************************************************************************
     * Get the UndoManager for this table editor
     *
     * @return Table UndoManager
     *********************************************************************************************/
    @Override
    protected CcddUndoManager getFieldPanelUndoManager()
    {
        return table.getUndoManager();
    }

    /**********************************************************************************************
     * Get the table's row additions
     *
     * @return List of table additions
     *********************************************************************************************/
    protected List<TableModification> getAdditions()
    {
        return additions;
    }

    /**********************************************************************************************
     * Get the table's row modifications
     *
     * @return List of table modifications
     *********************************************************************************************/
    protected List<TableModification> getModifications()
    {
        return modifications;
    }

    /**********************************************************************************************
     * Get the table's row deletions
     *
     * @return List of table deletions
     *********************************************************************************************/
    protected List<TableModification> getDeletions()
    {
        return deletions;
    }

    /**********************************************************************************************
     * Get the table's array expansion flag state
     *
     * @return Table's array expansion flag state
     *********************************************************************************************/
    protected boolean isExpanded()
    {
        return isShowArrayMembers;
    }

    /**********************************************************************************************
     * Check if the table has variable name and array index columns
     *
     * @return true if the table has variable name and array index columns
     *********************************************************************************************/
    protected boolean isCanHaveArrays()
    {
        return variableNameIndex != -1 && arraySizeIndex != -1;
    }

    /**********************************************************************************************
     * Get the model column indices containing the variable name, data type, array size, array
     * index, enumeration(s), and sample rate(s), if extant. This must be called when the table
     * editor is first instantiated and whenever the table's type definition is altered
     *********************************************************************************************/
    private void getSpecialColumnIndices()
    {
        selectionIndex = new ArrayList<Integer>();

        // Check if the table represents a structure
        if (typeDefn.isStructure())
        {
            variableNameIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
            dataTypeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
            arraySizeIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
            bitLengthIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);
            enumerationIndex = typeDefn.getColumnIndicesByInputTypeFormat(InputTypeFormat.ENUMERATION);
            rateIndex = typeDefn.getColumnIndicesByInputType(DefaultInputType.RATE);
        }
        // The table doesn't represent a structure
        else
        {
            // Set the special indices to invalid column values. Only structure tables get special
            // handling for these columns
            variableNameIndex = -1;
            dataTypeIndex = -1;
            arraySizeIndex = -1;
            bitLengthIndex = -1;
            enumerationIndex = new ArrayList<Integer>();
            rateIndex = new ArrayList<Integer>();
        }

        // Get the list of message names & IDs column(s)
        msgIDNameIndex = typeDefn.getColumnIndicesByInputType(DefaultInputType.MESSAGE_REFERENCE);

        // Set the variable path column index. This column is only active for a structure table,
        // but can appear in other table types (if the column is added to a structure type and then
        // the type is altered to not be a structure)
        variablePathIndex = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE_PATH);

        // Step through each input type containing a selection array
        for (InputType inputType : inputTypeHandler.getSelectionInputTypes())
        {
            // Step through each column having the specified input type
            for (Integer column : typeDefn.getColumnIndicesByInputType(inputType))
            {
                // Add the column index to the list
                selectionIndex.add(column);
            }
        }
    }

    /**********************************************************************************************
     * Update the table editor following a change to the table's type definition
     *
     * @param tblInfo
     *            table information
     *********************************************************************************************/
    protected void updateForTableTypeChange(TableInformation tblInfo)
    {
        // Update the table type name (in case it changed) in the current table information
        currentTableInfo.setType(tblInfo.getType());

        // Update the editor's type definition reference
        setTypeDefinition();

        // Get the model column indices for columns with special input types
        getSpecialColumnIndices();

        // Update the current field information and store the supplied table information as the
        // committed information
        currentTableInfo.setFieldInformation(fieldHandler.getFieldInformationByOwnerCopy(tblInfo.getTablePath()));
        setCommittedInformation(tblInfo);

        // Get the variable path separators and (re)create the variable path column content, if
        // present
        updateVariablePaths();

        // Update the table editor contents
        table.loadAndFormatData();

        // Rebuild the data field panel in the table editor using the updated fields
        createDataFieldPanel(false, currentTableInfo.getFieldInformation());

        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Execute after all pending Swing events are finished
             *************************************************************************************/
            @Override
            public void run()
            {
                // Force the table to redraw in case the number of columns changed
                tableModel.fireTableDataChanged();
                tableModel.fireTableStructureChanged();
            }
        });
    }

    /**********************************************************************************************
     * Update the table editor following a change to an input type definition
     *
     * @param inputTypeNames
     *            list of the input type names, before and after the changes
     *********************************************************************************************/
    protected void updateForInputTypeChange(List<String[]> inputTypeNames)
    {
        // Update the editor's type definition reference
        setTypeDefinition();

        // Get the model column indices for columns with special input types
        getSpecialColumnIndices();

        // Update the input type combo box lists
        setUpSelectionColumns();

        // Check that the table is open in a table editor
        if (editorDialog != null)
        {
            // Update the input types in the field handlers (committed and active), then rebuild
            // the data field panel
            fieldHandler.updateFieldInputTypes(inputTypeNames,
                                               committedTableInfo.getFieldInformation());
            fieldHandler.updateFieldInputTypes(inputTypeNames,
                                               currentTableInfo.getFieldInformation());
            createDataFieldPanel(false, currentTableInfo.getFieldInformation());
        }
    }

    /**********************************************************************************************
     * Remove the custom value deletion flag in a cell, if present
     *********************************************************************************************/
    private void clearCustomValueDeletionFlags()
    {
        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Step through each column of the updated row data
            for (int column = 0; column < tableModel.getColumnCount(); column++)
            {
                // Get the cell value
                Object cellValue = tableModel.getValueAt(row, column);

                // Check if the cell value begins with the flag that indicates the custom value for
                // this cell was deleted
                if (cellValue.toString().startsWith(REPLACE_INDICATOR))
                {
                    // Get this cell's editor component
                    Component comp = ((DefaultCellEditor) table.getCellEditor(table.convertRowIndexToView(row),
                                                                              table.convertColumnIndexToView(column))).getComponent();

                    // Check if the cell contains a combo box
                    if (comp instanceof JComboBox)
                    {
                        // Remove the specially flagged item from the combo box list
                        ((JComboBox<?>) comp).removeItem(cellValue);
                    }

                    // Remove the flag from the cell value
                    cellValue = cellValue.toString().replaceFirst("^" + REPLACE_INDICATOR, "");

                    // Store the value in the cell without the flag
                    tableModel.setValueAt(cellValue, row, column);
                    committedTableInfo.getData()[row][column] = cellValue;
                }
            }
        }
    }

    /**********************************************************************************************
     * Get the indices of all empty rows in the table. When a table is stored the empty rows are
     * eliminated. By storing the row indices these empty rows can be reinserted. This allows the
     * user to insert rows, and periodically press the store button without having to manually
     * reinsert the empty rows
     *
     * @return Array of the empty row indices; an empty array if no empty rows exist
     *********************************************************************************************/
    private Integer[] getEmptyRows()
    {
        List<Integer> emptyRows = new ArrayList<Integer>();

        // Step through each row in the table
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Store the current row index
            int prevRow = row;

            // Adjust the row index to the next non-empty row
            row = table.getNextPopulatedRowNumber(row);

            // The number of empty rows equals the difference in the previous and current row
            // indices. Step through each empty row index
            while (prevRow < row)
            {
                // Add the row index to the list of empty row indices
                emptyRows.add(prevRow);
                prevRow++;
            }
        }

        return emptyRows.toArray(new Integer[0]);
    }

    /**********************************************************************************************
     * Restore empty rows in the table at the specified indices
     *
     * @param emptyRows
     *            array of the empty row indices
     *********************************************************************************************/
    private void restoreEmptyRows(Integer[] emptyRows)
    {
        // Get an empty row for the table
        Object[] emptyRow = table.getEmptyRow();

        // Step through each empty row index
        for (Integer row : emptyRows)
        {
            // Insert an empty row at the index
            table.insertRowData(row - 1, emptyRow);
        }
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of database table changes
     *
     * @param dbTableInfo
     *            table's information, as it currently exists in the database
     *
     * @param applyToInstance
     *            true if the table that was updated is a prototype and this table is an instance
     *            of the updated table
     *********************************************************************************************/
    protected void doTableUpdatesComplete(TableInformation dbTableInfo, boolean applyToInstance)
    {
        Object[][] originalCommData = null;
        Integer[] emptyRows = null;

        // Check if this is the editor for the table that was changed
        if (dbTableInfo.getTablePath().equals(currentTableInfo.getTablePath()))
        {
            // Store the indices for any empty rows; the empty rows are restored after the table
            // data is replaced
            emptyRows = getEmptyRows();

            // Replace any custom value deletion flags with blanks
            clearCustomValueDeletionFlags();
        }

        // Check if a cell editor is active
        if (table.getCellEditor() != null)
        {
            // Terminate cell editing. If this is not done the cell with the active editor isn't
            // updated
            table.getCellEditor().stopCellEditing();
        }

        // Check if the table contains any committed data
        if (committedTableInfo.getData().length != 0)
        {
            originalCommData = new Object[committedTableInfo.getData().length][committedTableInfo.getData()[0].length];

            // Step through the currently committed data rows
            for (int row = 0; row < committedTableInfo.getData().length; row++)
            {
                // Step through the currently committed data columns
                for (int column = 0; column < committedTableInfo.getData()[row].length; column++)
                {
                    // Store the currently committed data cell value. Note that this is what this
                    // editor considers the committed data and doesn't reflect changes that were
                    // made externally (such as a change to a prototype altering an instance's
                    // contents)
                    originalCommData[row][column] = committedTableInfo.getData()[row][column];
                }
            }
        }

        // Load the committed array of data into the table
        table.loadDataArrayIntoTable(dbTableInfo.getData(), false);

        // Store the table information that represents the currently committed table data
        setCommittedInformation(dbTableInfo);

        // Check if this is the editor for the table that was changed
        if (dbTableInfo.getTablePath().equals(currentTableInfo.getTablePath()))
        {
            // Restore any empty rows
            restoreEmptyRows(emptyRows);
        }

        // Get the variable path separators and (re)create the variable path column content, if
        // present
        updateVariablePaths();

        // Check if this is an instance table to a prototype that has been updated; if so this
        // table needs to have the same changes applied
        if (applyToInstance)
        {
            // Store the current array member display status
            boolean isShowArrayMembersOld = isShowArrayMembers;

            // Check if array members are currently hidden
            if (!isShowArrayMembers)
            {
                // Display the array members. All rows must be displayed in order for any
                // uncommitted changes to be re-applied since isCellEditable() (called below)
                // requires the view coordinates
                showHideArrayMembers();
            }

            // Get the table's currently displayed data
            Object[][] preChangeData = table.getTableData(true);

            // Disable automatic termination of edits so that all of the changes can be combined
            // into a single edit
            table.getUndoHandler().setAutoEndEditSequence(false);

            // Step through each row of the table (committed)
            for (int postRow = 0; postRow < tableModel.getRowCount(); postRow++)
            {
                // Step through each row of the table (uncommitted)
                for (int preRow = 0; preRow < preChangeData.length; preRow++)
                {
                    // Check if the rows are the same, based on the primary key value
                    if (tableModel.getValueAt(postRow, primaryKeyIndex).equals(preChangeData[preRow][primaryKeyIndex]))
                    {
                        // Step though each column in the row
                        for (int column = 0; column < tableModel.getColumnCount(); column++)
                        {
                            // Get the value of the cell prior to the change
                            Object preChangeCell = preChangeData[preRow][column].toString();

                            // Check if the values differ between the committed and uncommitted
                            // cell values
                            if (!tableModel.getValueAt(postRow, column).toString().equals(preChangeCell)
                                && (originalCommData == null
                                    || !originalCommData[preRow][column].toString().equals(preChangeCell)))
                            {
                                // Convert the row and column indices to view coordinates
                                int modelRow = table.convertRowIndexToView(postRow);
                                int modelColumn = table.convertColumnIndexToView(column);

                                // Check if this is an editable cell. This prevents retaining a
                                // value that was changed (and can only be changed) in the
                                // prototype (e.g., variable name or data type), or values in cells
                                // that are no longer allowed to be updated after changing the
                                // prototype (e.g., bit length after changing the data type to a
                                // non-integer)
                                if (modelRow != -1
                                    && modelColumn != -1
                                    && table.isCellEditable(table.convertRowIndexToView(postRow),
                                                            table.convertColumnIndexToView(column)))
                                {
                                    // Update the value in the cell
                                    tableModel.setValueAt(preChangeCell, postRow, column);
                                }
                            }
                        }

                        break;
                    }
                }
            }

            // Get the current table description
            String description = getDescription();

            // Check if the description doesn't match the one stored in the database
            if (!description.equals(dbTableInfo.getDescription()))
            {
                // Set the description so that when it is restored it's flagged as an undoable
                // change
                setDescription(dbTableInfo.getDescription());
                updateDescriptionField(false);

                // Restore the description, with the undo flag enabled
                setDescription(description);
                updateDescriptionField(true);
            }

            // Get the current table column order
            String columnOrder = table.getColumnOrder();

            // Check if the column order doesn't match the one stored in the database
            if (!columnOrder.equals(dbTableInfo.getColumnOrder()))
            {
                // Set the column order so that when it is restored it's flagged as an undoable
                // change
                table.getUndoHandler().setAllowUndo(false);
                table.arrangeColumns(dbTableInfo.getColumnOrder());

                // Restore the column order
                table.getUndoHandler().setAllowUndo(true);
                table.arrangeColumns(columnOrder);
            }

            // Check if the data fields don't match those stored in the database. This can occur,
            // for example, if the table has a reference input type (variable. command, or message)
            // and the reference changed
            if (fieldHandler.isFieldChanged(currentTableInfo.getFieldInformation(),
                                            dbTableInfo.getFieldInformation(),
                                            false))
            {
                // Update the data fields
                createDataFieldPanel(false, dbTableInfo.getFieldInformation());
            }

            // Enable automatic edit termination and end the edit sequence. Any uncommitted changes
            // are now combined into a single edit that can be undone/redone
            table.getUndoHandler().setAutoEndEditSequence(true);
            table.getUndoManager().endEditSequence();

            // Check if the array member visibility was changed above
            if (isShowArrayMembersOld != isShowArrayMembers)
            {
                // Restore the original array member visibility
                showHideArrayMembers();
            }
        }

        // Update the change indicator in the editor tab
        updateOwnerChangeIndicator();
    }

    /**********************************************************************************************
     * Get the value of the cell in the table model with any macro name replaced with its
     * corresponding macro value
     *
     * @param row
     *            row index, model coordinates
     *
     * @param column
     *            column index, model coordinates
     *
     * @return Value of the cell in the table model with any macro name replaced with its
     *         corresponding macro value
     *********************************************************************************************/
    protected String getExpandedValueAt(int row, int column)
    {
        return newMacroHandler.getMacroExpansion(tableModel.getValueAt(row, column).toString());
    }

    /**********************************************************************************************
     * Get the value of the cell in the row of table data supplied with any macro name replaced
     * with its corresponding macro value
     *
     * @param tableData
     *            list of table data row arrays
     *
     * @param row
     *            row index, model coordinates
     *
     * @param column
     *            column index, model coordinates
     *
     * @return Value of the cell in the row of table data supplied with any macro name replaced
     *         with its corresponding macro value
     *********************************************************************************************/
    private String getExpandedValueAt(List<Object[]> tableData, int row, int column)
    {
        return newMacroHandler.getMacroExpansion(tableData.get(row)[column].toString());
    }

    /**********************************************************************************************
     * Create the table editor
     *********************************************************************************************/
    private void initialize()
    {
        // Set the table type definition
        setTypeDefinition();

        // Get the model column indices for columns with special input types
        getSpecialColumnIndices();

        // Create a copy of the table information
        setCommittedInformation(currentTableInfo);

        // Get the array size and index column indices and create a row filter to show/hide the
        // array member rows if an array size column exists
        setUpArraySizeColumn();

        // Define the table editor JTable
        table = new CcddJTableHandler(ModifiableSizeInfo.INIT_VIEWABLE_DATA_TABLE_ROWS.getSize())
        {
            /**************************************************************************************
             * Highlight any macros or special flags in the table cells
             *
             * @param component
             *            reference to the table cell renderer component
             *
             * @param text
             *            cell text
             *
             * @param isSelected
             *            true if the cell is to be rendered with the selection highlighted
             *
             * @param int
             *            row cell row, view coordinates
             *
             * @param column
             *            cell column, view coordinates
             *************************************************************************************/
            @Override
            protected void doSpecialRendering(Component component,
                                              String text,
                                              boolean isSelected,
                                              int row,
                                              int column)
            {
                // Highlight any macro names in the table cell. Adjust the highlight color to
                // account for the cell selection highlighting so that the macro is easily readable
                macroHandler.highlightMacro(component,
                                            text,
                                            isSelected
                                                       ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                       : ModifiableColorInfo.TEXT_HIGHLIGHT.getColor());

                // Highlight 'sizeof(data type)' instances
                CcddDataTypeHandler.highlightSizeof(component,
                                                    text,
                                                    isSelected
                                                               ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                               : ModifiableColorInfo.TEXT_HIGHLIGHT.getColor());

                // Highlight the flag that indicates the custom value for this cell is to be
                // removed and the prototype's value used instead. Create a highlighter painter
                DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(isSelected
                                                                                                            ? ModifiableColorInfo.INPUT_TEXT.getColor()
                                                                                                            : Color.MAGENTA);

                // Create the pattern matcher from the pattern
                Matcher matcher = replacePattern.matcher(text);

                // Check if there is a match in the cell value
                if (matcher.find())
                {
                    try
                    {
                        // Highlight the matching text. Adjust the highlight color to account for
                        // the cell selection highlighting so that the search text is easily
                        // readable
                        ((JTextComponent) component).getHighlighter().addHighlight(matcher.start(),
                                                                                   matcher.end(),
                                                                                   painter);
                    }
                    catch (BadLocationException ble)
                    {
                        // Ignore highlighting failure
                    }
                }

                // Highlight any matching search text strings in the table cells
                super.doSpecialRendering(component, text, isSelected, row, column);
            }

            /**************************************************************************************
             * Get the tool tip text for a table cell, showing any macro name replaced with its
             * corresponding macro value
             *************************************************************************************/
            @Override
            public String getToolTipText(MouseEvent me)
            {
                String toolTipText = null;

                // Get the row and column of the cell over which the mouse pointer is hovering
                Point point = me.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);

                // Check if a cell is beneath the mouse pointer
                if (row != -1 && column != -1)
                {
                    // Expand any macros in the cell text and display this as the cell's tool tip
                    // text
                    toolTipText = macroHandler.getMacroToolTipText(getValueAt(row,
                                                                              column).toString());
                }

                return toolTipText;
            }

            /**************************************************************************************
             * Return true if the table data, column order, description, or a data field changes.
             * If the table isn't open in and editor (as when a macro is changed) then the table
             * description and data fields are not applicable
             *************************************************************************************/
            @Override
            protected boolean isTableChanged(Object[][] previousData, List<Integer> ignoreColumns)
            {
                boolean isFieldChanged = false;

                // Check that the table is open in a table editor (versus open for a macro name
                // and/or value change, for example)
                if (editorDialog != null)
                {
                    // Update the field information with the current text field values
                    updateFieldValueFromComponent(currentTableInfo.getFieldInformation());

                    // Set the flag if the number of fields, field attributes, or field contents
                    // have changed
                    isFieldChanged = fieldHandler.isFieldChanged(currentTableInfo.getFieldInformation(),
                                                                 committedTableInfo.getFieldInformation(),
                                                                 false);
                }

                return super.isTableChanged(previousData, ignoreColumns)
                       || isFieldChanged
                       || !getColumnOrder().equals(committedTableInfo.getColumnOrder())
                       || (editorDialog != null
                           && !committedTableInfo.getDescription().equals(getDescription()));
            }

            /**************************************************************************************
             * Allow multiple line display in the non-boolean columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return !isColumnBoolean(column);
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == primaryKeyIndex || column == rowIndex;
            }

            /**************************************************************************************
             * Display the columns with a boolean input type as check boxes
             *************************************************************************************/
            @Override
            protected boolean isColumnBoolean(int column)
            {
                return typeDefn.getInputTypes()[column].getInputFormat() == InputTypeFormat.BOOLEAN;
            }

            /**************************************************************************************
             * Allow resizing of the non-boolean columns
             *************************************************************************************/
            @Override
            protected boolean isColumnResizable(int column)
            {
                return !isColumnBoolean(column);
            }

            /**************************************************************************************
             * Override isCellEditable to determine which cells can be edited
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                // Initialize the flag to the table edit flag (based on the table edit flag this
                // enables normal editing or disables editing any cell)
                boolean isEditable = isEditEnabled;

                // Check if editing is enabled, the table is displayable (to prevent corruption of
                // the cell editor) or that the table isn't open in an editor (as when a macro
                // change is processed), if the table model exists, and if the table has at least
                // one row
                if (isEditable
                    && (isDisplayable() || editorDialog == null)
                    && tableModel != null
                    && tableModel.getRowCount() != 0)
                {
                    // Convert the view row and column indices to model coordinates
                    int modelRow = convertRowIndexToModel(row);
                    int modelColumn = convertColumnIndexToModel(column);

                    // Check if the cell is editable
                    isEditable = isDataAlterable(((List<?>) tableModel.getDataVector().elementAt(modelRow)).toArray(new Object[0]),
                                                 modelRow,
                                                 modelColumn);
                }

                return isEditable;
            }

            /**************************************************************************************
             * Override isDataAlterable to determine which table data values can be changed
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
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                boolean isAlterable = true;

                // Check if the table data has at least one row
                if (rowData != null && rowData.length != 0)
                {
                    // Copy the row of table data. This prevents the macro expansions for array
                    // size and bit length below from changing the cell contents
                    Object[] rowCopy = Arrays.copyOf(rowData, rowData.length);

                    // Check if the array size column is present in this table
                    if (arraySizeIndex != -1)
                    {
                        // Expand any macros in the array size column
                        rowCopy[arraySizeIndex] = newMacroHandler.getMacroExpansion(rowCopy[arraySizeIndex].toString());
                    }

                    // Check if the array size column is present in this table
                    if (bitLengthIndex != -1)
                    {
                        // Expand any macros in the array size column
                        rowCopy[bitLengthIndex] = newMacroHandler.getMacroExpansion(rowCopy[bitLengthIndex].toString());
                    }

                    // Flag that is true if the row represents an array definition
                    boolean isArrayDefinition = arraySizeIndex != -1
                                                && variableNameIndex != -1
                                                && !rowCopy[arraySizeIndex].toString().isEmpty()
                                                && !ArrayVariable.isArrayMember(rowCopy[variableNameIndex]);

                    // Check if the cell is non-alterable based on the following criteria:
                    if
                    // This is the variable name, data type, array size, or bit length cell and
                    // this is not a prototype table
                    (((column == variableNameIndex
                       || column == dataTypeIndex
                       || column == arraySizeIndex
                       || column == bitLengthIndex)
                      && !currentTableInfo.isPrototype())

                     // This is the variable name, data type, or array size cell and the row
                     // displays an array member
                     || ((column == variableNameIndex
                          || column == dataTypeIndex
                          || column == arraySizeIndex)
                         && ArrayVariable.isArrayMember(rowCopy[variableNameIndex]))

                    // This data type is not a primitive (i.e., it's a structure) and the column
                    // isn't valid for structures
                     || (dataTypeIndex != -1
                         && !dataTypeHandler.isPrimitive(rowCopy[dataTypeIndex].toString())
                         && !typeDefn.isStructureAllowed()[column])

                    // This data type is a pointer and the column isn't valid for pointers
                     || (dataTypeIndex != -1
                         && dataTypeHandler.isPointer(rowCopy[dataTypeIndex].toString())
                         && !typeDefn.isPointerAllowed()[column])

                    // This is an array definition, and the input type is for the message name & ID
                    // or is the variable path - the members of an array can have a message name &
                    // ID or variable path, but not the array's definition
                     || ((isArrayDefinition
                          && (typeDefn.getInputTypes()[column].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID))
                              || column == variablePathIndex)))

                    // This is the bit length cell and either the array size is present or the data
                    // type is not an integer (signed or unsigned)
                     || (column == bitLengthIndex
                         && ((arraySizeIndex != -1
                              && !rowCopy[arraySizeIndex].toString().isEmpty())
                             || (dataTypeIndex != -1
                                 && !dataTypeHandler.isInteger(rowCopy[dataTypeIndex].toString()))))

                    // This is the array size cell and either no variable name is present or a bit
                    // length is present
                     || (column == arraySizeIndex
                         && ((variableNameIndex != -1
                              && rowCopy[variableNameIndex].toString().isEmpty())
                             || (bitLengthIndex != -1
                                 && !rowCopy[bitLengthIndex].toString().isEmpty())))

                    // This is a rate cell, and a data type exists that is not a primitive
                     || (rateIndex.contains(column)
                         && dataTypeIndex != -1
                         && !rowCopy[dataTypeIndex].toString().isEmpty()
                         && !dataTypeHandler.isPrimitive(rowCopy[dataTypeIndex].toString()))

                    // This is any column in an array variable of type 'string' other than the
                    // first array member
                     || (variableNameIndex != -1
                         && dataTypeIndex != -1
                         && dataTypeHandler.isString(rowCopy[dataTypeIndex].toString())
                         && ArrayVariable.isArrayMember(rowCopy[variableNameIndex])
                         && !rowCopy[variableNameIndex].toString().endsWith("[0]"))

                    // This is the variable path column and the path is empty. This is the case for
                    // variables in a non-root prototype structure
                     || (column == variablePathIndex
                         && rowCopy[variablePathIndex].toString().isEmpty()))
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
                            // Check if the cell is non-alterable based on the following criteria:
                            if (
                            // This is an enumeration or bit length column, and the associated data
                            // type isn't an integer type (signed or unsigned)
                            (column == colGrp.getEnumeration() || column == colGrp.getBitLength())
                                && !dataTypeHandler.isInteger(rowCopy[colGrp.getDataType()].toString())

                                // This is a minimum or maximum value column, and no data type is
                                // defined or the data type isn't a primitive
                                || ((column == colGrp.getMinimum()
                                     || column == colGrp.getMaximum())
                                    && (rowCopy[colGrp.getDataType()].toString().isEmpty()
                                        || !dataTypeHandler.isPrimitive(rowCopy[colGrp.getDataType()].toString()))))
                            {
                                // Set the flag to prevent altering the data value and stop
                                // searching
                                isAlterable = false;
                                break;
                            }
                        }

                        // Check if no command argument pairing reset the flag
                        if (isAlterable)
                        {
                            // Step through each non-command argument minimum/maximum pairing
                            for (MinMaxPair minMax : minMaxPair)
                            {
                                // Check if this is the minimum or maximum column and the data type
                                // is missing, empty, or isn't a primitive type
                                if (dataTypeIndex != -1
                                    && (rowCopy[dataTypeIndex].toString().isEmpty()
                                        || !dataTypeHandler.isPrimitive(rowCopy[dataTypeIndex].toString()))
                                    && (column == minMax.getMinimum()
                                        || column == minMax.getMaximum()))
                                {
                                    // Set the flag to prevent altering the data value and stop
                                    // searching
                                    isAlterable = false;
                                    break;
                                }
                            }
                        }
                    }
                }

                return isAlterable;
            }

            /**************************************************************************************
             * Override getCellEditor so that for a data type column cell in a row where the
             * enumeration cell isn't empty the combo box editor that displays only integer data
             * types (signed and unsigned) is returned; for all other cells return the normal cell
             * editor
             *
             * @param row
             *            table view row number
             *
             * @param column
             *            table view column number
             *
             * @return The cell editor for the specified row and column
             *************************************************************************************/
            @Override
            public TableCellEditor getCellEditor(int row, int column)
            {
                // Get the editor for this cell
                TableCellEditor cellEditor = super.getCellEditor(row, column);

                // Convert the row and column indices to the model coordinates
                int modelRow = convertRowIndexToModel(row);
                int modelColumn = convertColumnIndexToModel(column);

                // Check if the column for which the cell editor is requested is the data type
                // column and the bit length cell is not empty
                if (modelColumn == dataTypeIndex
                    && bitLengthIndex != -1
                    && !getExpandedValueAt(modelRow, bitLengthIndex).isEmpty())
                {
                    // Select the combo box cell editor that displays only integer data types
                    // (signed and unsigned)
                    cellEditor = enumDataTypeCellEditor;
                }
                // Check if this is a data type and enumeration pairing or a command argument
                // column grouping
                else
                {
                    // Step through each column grouping
                    for (AssociatedColumns colGrp : associatedColumns)
                    {
                        // Check if the column for which the cell editor is requested is a data
                        // type column, and that the associated enumeration cell isn't blank
                        if (modelColumn == colGrp.getDataType()
                            && colGrp.getEnumeration() != -1
                            && !getExpandedValueAt(modelRow,
                                                   colGrp.getEnumeration()).isEmpty())
                        {
                            // Select the combo box cell editor that displays only integer data
                            // types (signed and unsigned) and stop searching
                            cellEditor = enumDataTypeCellEditor;
                            break;
                        }
                    }
                }

                return cellEditor;
            }

            /**************************************************************************************
             * Validate changes to the editable cells; e.g., verify cell content and, if found
             * invalid, revert to the original value. Update array members if needed
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
             *            true to display the invalid input dialog, if applicable
             *
             * @param isMultiple
             *            true if this is one of multiple cells to be entered and checked; false if
             *            only a single input is being entered
             *
             * @return true to indicate that subsequent errors should be displayed; false if
             *         subsequent errors should not be displayed; null if the operation should be
             *         canceled
             *************************************************************************************/
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

                try
                {
                    boolean hasMacroSizeof = false;

                    // Set the parameters that govern recalculating packed variables to begin with
                    // the first row in the table and to use the first variable in the pack to set
                    // the rates for other variables in the same pack
                    int startRow = 0;
                    boolean useRowRate = false;

                    // Create a string version of the new value, replacing any macro in the text
                    // with its corresponding value. If the macro is within an array size or bit
                    // length column in a structure table prototype then the valid data types are
                    // constrained to those that are not one of the table's ancestors. Note that an
                    // instance table can't have it's array size of bit length changed, and
                    // non-structure tables can't have children, so there's no need to constrain
                    // the data types for these cases
                    String newValueS = newMacroHandler.getMacroExpansion(newValue.toString(),
                                                                         typeDefn.isStructure()
                                                                                              && currentTableInfo.isPrototype()
                                                                                              && (column == arraySizeIndex
                                                                                                  || column != bitLengthIndex)
                                                                                                                               ? invalidDataTypes
                                                                                                                               : null);

                    // Check if a sizeof() call in the text makes a recursive reference. Example:
                    // If this is a structure table then this can occur if a sizeof() call refers
                    // to this structure's prototype or the prototype of one of its children
                    if (variableHandler.isInvalidReference())
                    {
                        throw new CCDDException("Invalid input value in table '</b>"
                                                + currentTableInfo.getTablePath()
                                                + "<b>' for column '</b>"
                                                + typeDefn.getColumnNamesUser()[column]
                                                + "<b>'; data type invalid or unknown in sizeof() call");
                    }

                    // Check if the cell is flagged for replacement by the prototype value
                    if (newValueS.startsWith(REPLACE_INDICATOR))
                    {
                        // Remove the flag so that the updated value is stored as a custom value
                        newValueS = newValueS.replaceFirst("^" + REPLACE_INDICATOR, "");
                    }

                    // Check that the new value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the values in this column must not be duplicated
                        if (typeDefn.isRowValueUnique()[column])
                        {
                            // Step through each row in the table
                            for (int otherRow = 0; otherRow < tableData.size(); otherRow++)
                            {
                                // Check if this isn't the row being/ edited, and if the cell value
                                // matches the one being added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(getExpandedValueAt(tableData,
                                                                                     otherRow,
                                                                                     column)))
                                {
                                    throw new CCDDException("Invalid input value in table '</b>"
                                                            + currentTableInfo.getTablePath()
                                                            + "<b>' for column '</b>"
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
                                    // Check if this is not the argument being checked and if the
                                    // name matches the name of another command argument
                                    if (!colGrp.equals(otherColGrp)
                                        && newValueS.equals(getExpandedValueAt(tableData,
                                                                               row,
                                                                               otherColGrp.getName())))
                                    {
                                        throw new CCDDException("Invalid input value in table '</b>"
                                                                + currentTableInfo.getTablePath()
                                                                + "<b>' for column '</b>"
                                                                + typeDefn.getColumnNamesUser()[column]
                                                                + "<b>'; command argument names must be unique for a command");
                                    }
                                }
                            }
                            // Check if this is the minimum or maximum value columns
                            else if (column == colGrp.getMinimum()
                                     || column == colGrp.getMaximum())
                            {
                                // Verify that the minimum/maximum value is valid for the
                                // argument's data type, and stop searching
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
                            // Check if this is the minimum or maximum value columns
                            if (column == minMax.getMinimum() || column == minMax.getMaximum())
                            {
                                // Verify that the minimum/maximum value is valid for the
                                // argument's data type, and stop searching
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

                        // Check if the value doesn't match the expected input type
                        if (!newValueS.matches(typeDefn.getInputTypes()[column].getInputMatch()))
                        {
                            throw new CCDDException("Invalid characters in table '</b>"
                                                    + currentTableInfo.getTablePath()
                                                    + "<b>' for column '</b>"
                                                    + typeDefn.getColumnNamesUser()[column]
                                                    + "<b>'; characters consistent with input type '</b>"
                                                    + typeDefn.getInputTypes()[column].getInputName()
                                                    + "<b>' expected");
                        }
                    }

                    // Check if the cell's input type isn't a boolean. Boolean values are
                    // represented by a check box and can't contain a macro
                    if (typeDefn.getInputTypes()[column].getInputFormat() != InputTypeFormat.BOOLEAN)
                    {
                        // Flag that indicates that the new cell value contains a macro and/or a
                        // sizeof() call
                        hasMacroSizeof = CcddMacroHandler.hasMacro(newValue.toString())
                                         || CcddVariableHandler.hasSizeof(newValue.toString());

                        // Check if the new value doesn't contain a macro or sizeof() reference;
                        // this prevents the macro reference from being lost
                        if (!hasMacroSizeof)
                        {
                            // Store the new value in the table data array after formatting the
                            // cell value per its input type. This is needed primarily to clean up
                            // numeric formatting
                            newValueS = typeDefn.getInputTypes()[column].formatInput(newValueS);
                            newValue = newValueS;
                            tableData.get(row)[column] = newValueS;
                        }
                    }

                    // Replace any macro in the original text with its corresponding value
                    String oldValueS = macroHandler.getMacroExpansion(oldValue.toString());

                    // Check that the old and new values differ after replacing any macro in the
                    // original text with its corresponding value
                    if (!newValueS.equals(oldValueS))
                    {
                        String variableName = null;
                        String dataType = null;
                        String arraySize = null;
                        String bitLength = null;

                        // Check if the variable name column exists
                        if (variableNameIndex != -1)
                        {
                            // Get the variable name for the current row, expanding macros in the
                            // name (if present)
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
                            // Get the array size for the current row, expanding macros in the
                            // value (if present)
                            arraySize = getExpandedValueAt(tableData, row, arraySizeIndex);
                        }

                        // Check if the bit length column exists
                        if (bitLengthIndex != -1)
                        {
                            // Get the bit length for the current row, expanding macros in the
                            // value (if present)
                            bitLength = getExpandedValueAt(tableData, row, bitLengthIndex);
                        }

                        // Check if the variable name or data type has been changed
                        if (column == variableNameIndex || column == dataTypeIndex)
                        {
                            // Check if the data type is a reference to this structure table or one
                            // of its ancestors, which causes a circular reference. This can occur
                            // if a data type is pasted into the cell
                            if (invalidDataTypes != null && invalidDataTypes.contains(dataType))
                            {
                                throw new CCDDException("Invalid data type '</b>"
                                                        + dataType
                                                        + "<b>' in table '</b>"
                                                        + currentTableInfo.getTablePath()
                                                        + "<b>'; structure cannot reference itself or an ancestor");
                            }

                            // Check if the variable is an array
                            if (arraySize != null)
                            {
                                // Check if this is the data type column, a bit length is present,
                                // and the bit length exceeds the size of the data type in bits
                                if (column == dataTypeIndex
                                    && !newValueS.isEmpty()
                                    && bitLength != null
                                    && !bitLength.isEmpty()
                                    && Integer.valueOf(bitLength) > newDataTypeHandler.getSizeInBits(dataType))
                                {
                                    throw new CCDDException("Invalid bit length in table '</b>"
                                                            + currentTableInfo.getTablePath()
                                                            + "<b>'; bit length exceeds the size of the data type");
                                }

                                // Get the array index values from the array size column and update
                                // array members if this is an array definition
                                int[] arrayDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                                adjustArrayMember(tableData, arrayDims, arrayDims, row, column);
                            }

                            // Check if the variable path column is present and the path isn't
                            // manually set
                            if (variablePathIndex != -1
                                && tableData.get(row)[variablePathIndex].toString().equals(getVariablePath((column == variableNameIndex
                                                                                                                                        ? oldValueS
                                                                                                                                        : variableName),
                                                                                                           (column == dataTypeIndex
                                                                                                                                    ? oldValueS
                                                                                                                                    : dataType),
                                                                                                           true)))
                            {
                                // Update the variable path with the new variable name and/or data
                                // type
                                tableData.get(row)[variablePathIndex] = getVariablePath(variableName,
                                                                                        dataType,
                                                                                        true);
                            }
                        }
                        // Check if this is the array size column
                        else if (column == arraySizeIndex)
                        {
                            // Get the original and updated array index values
                            int[] arraySizeOld = ArrayVariable.getArrayIndexFromSize(oldValueS);
                            int[] arraySizeNew = ArrayVariable.getArrayIndexFromSize(newValueS);

                            // Set the flag that indicates the array index values changed based on
                            // the number of index values changing
                            boolean isDifferent = arraySizeOld.length != arraySizeNew.length;

                            // Check if the number of index values is the same, in which case the
                            // individual array index values must be compared
                            if (!isDifferent)
                            {
                                // Step through each index value
                                for (int index = 0; index < arraySizeOld.length; index++)
                                {
                                    // Check if the original and updated values differ
                                    if (arraySizeOld[index] != arraySizeNew[index])
                                    {
                                        // Set the flag to indicate an index value changed and stop
                                        // searching
                                        isDifferent = true;
                                        break;
                                    }
                                }
                            }

                            // Check if the original and updated values differ
                            if (isDifferent)
                            {
                                // Add or remove array members to match the new array size
                                adjustArrayMember(tableData,
                                                  arraySizeOld,
                                                  arraySizeNew,
                                                  row,
                                                  column);
                            }
                        }
                        // Check if this is the rate column and the row is an array definition
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
                        // Check if this is the rate column and the variable has a bit length value
                        else if (rateIndex.contains(column)
                                 && bitLength != null
                                 && dataType != null
                                 && !bitLength.isEmpty())
                        {
                            // Adjust the rates of any other bit-wise variables that are packed
                            // together with this variable, using this row's rate
                            startRow = row;
                            useRowRate = true;
                        }
                        // Check if this is the bit length column
                        else if (column == bitLengthIndex)
                        {
                            // Check if a bit length is present and it exceeds the bit size of the
                            // data type
                            if (bitLength != null
                                && !bitLength.isEmpty()
                                && dataType != null
                                && Integer.valueOf(bitLength) > newDataTypeHandler.getSizeInBits(dataType))
                            {
                                throw new CCDDException("Invalid bit length in table '</b>"
                                                        + currentTableInfo.getTablePath()
                                                        + "<b>'; bit length exceeds the size of the data type");
                            }

                            // Adjust the rates of any other bit-wise variables that are packed
                            // together with this variable, using the first packed variable's rate
                            startRow = row;
                        }
                        // Check if this is the variable path column
                        else if (column == variablePathIndex
                                 && variableName != null
                                 && !variableName.isEmpty()
                                 && dataType != null
                                 && !dataType.isEmpty())
                        {
                            // Check if the variable path isn't empty; i.e. a name is manually
                            // entered
                            if (!newValueS.isEmpty())
                            {
                                // Check if the variable path entered matches one already in use in
                                // another structure table
                                if (variableHandler.isVariablePathInUse(currentTableInfo.getTablePath()
                                                                        + ","
                                                                        + dataType
                                                                        + "."
                                                                        + variableName,
                                                                        newValueS))
                                {
                                    throw new CCDDException("Invalid variable path in table '</b>"
                                                            + currentTableInfo.getTablePath()
                                                            + "<b>'; variable path already in use in another structure");
                                }
                            }
                            // The cell has been blanked
                            else
                            {
                                // Build the variable path from the variable name and data type
                                tableData.get(row)[variablePathIndex] = getVariablePath(variableName,
                                                                                        dataType,
                                                                                        false);
                            }
                        }
                        // Check if a column other than the variable name, data type, array size,
                        // or variable path is changed for an array definition or for a string
                        // array
                        else if (variableName != null
                                 && dataType != null
                                 && arraySize != null
                                 && column != variableNameIndex
                                 && column != dataTypeIndex
                                 && column != arraySizeIndex
                                 && column != variablePathIndex
                                 && !arraySize.isEmpty()
                                 && (!ArrayVariable.isArrayMember(variableName)
                                     || newDataTypeHandler.isString(dataType)))
                        {
                            // Propagate the value to all members of this array/string
                            propagateArrayValues(tableData, row, column);
                        }

                        // Clear the contents of any cells that are no longer valid in this row
                        clearInvalidCells(tableData.get(row), row);

                        // Adjust the rates of the bit-wise variables that are packed together,
                        // beginning at the indicated row
                        setAllPackedVariableRates(tableData, startRow, useRowRate);

                        // Check if the new value contains any macros
                        if (hasMacroSizeof)
                        {
                            // Store the new value, with the macro(s) restored, into the table data
                            // array
                            tableData.get(row)[column] = newValue;
                        }
                    }
                    // The cell value is the same after macro expansion; check if it differed prior
                    // to macro expansion (e.g., a macro name is replaced with the actual value or
                    // vice versa)
                    else if (!newValue.equals(oldValue))
                    {
                        // Store the new value in the table data array
                        tableData.get(row)[column] = newValue;

                        // Check if the column is the array size
                        if (column == arraySizeIndex)
                        {
                            // Propagate the value to all members of this array/string
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
                    // Set the flag that indicates the last edited cell's content is invalid
                    setLastCellValid(false);

                    // Check if the error message dialog should be displayed
                    if (showMessage)
                    {
                        // Check if this is a single cell insert
                        if (!isMultiple)
                        {
                            // Inform the user that the input value is invalid
                            new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>" + ce.getMessage(),
                                                                      "Invalid Input",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_OPTION);
                        }
                        // This is one of multiple cells being inserted
                        else
                        {
                            // Inform the user that the input value is invalid
                            CcddDialogHandler validityDlg = new CcddDialogHandler();
                            int buttonSelected = validityDlg.showIgnoreCancelDialog(parent,
                                                                                    "<html><b>" + ce.getMessage(),
                                                                                    "Invalid Input",
                                                                                    "Ignore this invalid input",
                                                                                    "Ignore this and any remaining invalid inputs for this table",
                                                                                    "Cease inputting values",
                                                                                    false);

                            // Check if the Ignore All button was pressed
                            if (buttonSelected == IGNORE_BUTTON)
                            {
                                // Set the flag to ignore subsequent input errors
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

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    table.getUndoManager().undoRemoveEdit();
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, parent);
                }

                return showMessage;
            }

            /**************************************************************************************
             * Clear the contents of cells in the specified row that are no longer valid due to the
             * contents of other cells
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param row
             *            table model row index
             *************************************************************************************/
            private void clearInvalidCells(Object[] rowData, int row)
            {
                // Step through each visible column
                for (int column = 0; column < getColumnCount(); column++)
                {
                    // Get the column index in model coordinates
                    int modelColumn = convertColumnIndexToModel(column);

                    if (
                    // Check if this isn't the variable name, data type, array size, bit length,
                    // rate, or variable path column, and that the cell is not alterable
                    (modelColumn != variableNameIndex
                     && modelColumn != dataTypeIndex
                     && modelColumn != arraySizeIndex
                     && modelColumn != bitLengthIndex
                     && modelColumn != variablePathIndex
                     && !rateIndex.contains(modelColumn)
                     && !isDataAlterable(rowData, row, modelColumn))

                        // Check if the data type column exists, the data type is not a primitive
                        // (i.e., it's a structure), and structures are not allowed for this column
                        || (dataTypeIndex != -1
                            && !newDataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString())
                            && !typeDefn.isStructureAllowed()[modelColumn])

                    // Check if the data type column exists, the data type is a pointer, and
                    // pointers are not allowed for this column
                        || (dataTypeIndex != -1
                            && newDataTypeHandler.isPointer(rowData[dataTypeIndex].toString())
                            && !typeDefn.isPointerAllowed()[modelColumn])

                    // Check if a data type column exists, this is the bit length column, and the
                    // data type isn't a primitive
                        || (dataTypeIndex != -1
                            && modelColumn == bitLengthIndex
                            && !newDataTypeHandler.isPrimitive(rowData[dataTypeIndex].toString())))
                    {
                        // Clear the contents of the cell
                        rowData[modelColumn] = "";
                    }
                }
            }

            /**************************************************************************************
             * Load the database values into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                int totalWidth = setUpdatableCharacteristics(committedTableInfo.getData(),
                                                             typeDefn.getColumnNamesUser(),
                                                             committedTableInfo.getColumnOrder(),
                                                             toolTips,
                                                             true,
                                                             true,
                                                             true);

                // Check that the table is open in a table editor (versus open for a macro name
                // and/or value change, for example)
                if (editorDialog != null)
                {
                    // Get the minimum width needed to display all columns, but no wider than the
                    // display
                    int width = Math.min(totalWidth + LAF_SCROLL_BAR_WIDTH,
                                         GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth());

                    // Check if the editor's width is less than the minimum
                    if (editorDialog.getTableWidth() < width)
                    {
                        // Set the initial and preferred editor size
                        editorDialog.setTableWidth(width);
                        editorDialog.setPreferredSize(new Dimension(width,
                                                                    editorDialog.getPreferredSize().height));
                    }
                }

                // Create the drop-down combo box for the column with the name 'data type' that
                // displays the available data types, including primitive types and the names of
                // tables that represent structures, and add a mouse listener to handle mouse click
                // events. Set up any command argument data type, argument name, enumeration,
                // minimum, and maximum groupings
                setUpDataTypeColumns(null, null);

                // Set up any minimum and maximum pairings (excluding those associated with command
                // argument groupings)
                setUpMinMaxColumns();

                // Create drop-down combo boxes that display the available sample rates for the
                // "Rate" column
                setUpSampleRateColumn();

                // Create drop-down combo boxes that display a list of selection items
                setUpSelectionColumns();

                // Create the mouse listener for the data type column
                createDataTypeColumnMouseListener();
            }

            /**************************************************************************************
             * Override prepareRenderer to allow adjusting the background colors of table cells
             *************************************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
            {
                JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

                // Check if the cell doesn't have the focus or is selected. The focus and selection
                // highlight colors override the invalid highlight color
                if (comp.getBackground() != ModifiableColorInfo.FOCUS_BACK.getColor()
                    && comp.getBackground() != ModifiableColorInfo.SELECTED_BACK.getColor())
                {
                    // Check if the cell value is invalid. This does not validate the contents, but
                    // only ensures that required cells are populated and that combo box cells
                    // contain an item in the list
                    if (!isCellValueFound(row, column))
                    {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.REQUIRED_BACK.getColor());
                    }
                    // Check if this cell is protected from changes
                    else if (!isCellEditable(row, column))
                    {
                        // Change the cell's text and background colors
                        comp.setForeground(ModifiableColorInfo.PROTECTED_TEXT.getColor());
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());
                    }
                    // Check if the row's variable name is present and matches that for a padding
                    // variable
                    else if (variableNameIndex != -1
                             && getExpandedValueAt(table.convertRowIndexToModel(row),
                                                   variableNameIndex).matches(PAD_VARIABLE_MATCH))
                    {
                        // Change the cell's background color
                        comp.setBackground(ModifiableColorInfo.PADDING_BACK.getColor());
                    }
                }

                return comp;
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method to handle right double click events on the
             * array size cells in order to show/hide the array index column and array member rows,
             * and to handle sorting of columns based on their input type
             *************************************************************************************/
            @Override
            protected void setTableSortable()
            {
                super.setTableSortable();

                // Check that the table is open in a table editor (versus open for a macro name
                // and/or value change, for example)
                if (editorDialog != null)
                {
                    // Get the table's row sorter
                    TableRowSorter<?> sorter = (TableRowSorter<?>) getRowSorter();

                    // Create a runnable object to be executed
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        /**************************************************************************
                         * Execute after all pending Swing events are finished. This allows the
                         * number of viewable columns to catch up with the column model when a
                         * column is removed
                         *************************************************************************/
                        @Override
                        public void run()
                        {
                            // Issue a table change event so that the new row is displayed properly
                            // when the array view is collapsed. Can't use tableModel here since it
                            // isn't set when the first call to this method is made
                            ((UndoableTableModel) table.getModel()).fireTableStructureChanged();
                        }
                    });

                    // Check if the table has a sorter (i.e., has at least one row)
                    if (sorter != null)
                    {
                        // Check if the row filter hasn't been set and that there is an array row
                        // filter
                        if (sorter.getRowFilter() == null && rowFilter != null)
                        {
                            // Apply the row filter that shows/hides the array members
                            sorter.setRowFilter(rowFilter);
                        }

                        // Step through each table column
                        for (int column = 0; column < table.getModel().getColumnCount(); column++)
                        {
                            // Get the input type format for this column
                            final InputTypeFormat inputFormat = typeDefn.getInputTypes()[column].getInputFormat();

                            // Add a column sort comparator
                            sorter.setComparator(column, new Comparator<Object>()
                            {
                                /******************************************************************
                                 * Override the comparison when sorting columns to account for the
                                 * column's input type format. Note that macros aren't expanded
                                 * when sorting. Though expansion provides an accurate sort,
                                 * visually it's confusing since the macro values aren't readily
                                 * apparent. For columns with a numeric input type that contain
                                 * macros then initial numeric portion (if any) if used when
                                 * sorting
                                 *****************************************************************/
                                @Override
                                public int compare(Object cell1Obj, Object cell2Obj)
                                {
                                    Integer result = 0;

                                    // Convert the cell values to strings for comparison purposes
                                    String cell1 = cell1Obj.toString();
                                    String cell2 = cell2Obj.toString();

                                    // Check if either cell is empty
                                    if (cell1.toString().isEmpty() || cell2.toString().isEmpty())
                                    {
                                        // Compare as text (alphabetically)
                                        result = cell1.compareTo(cell2);
                                    }
                                    // Neither cell is empty
                                    else
                                    {
                                        // Set the row sort comparator based on the column input
                                        // format type
                                        switch (inputFormat)
                                        {
                                            case TEXT:
                                            case DATA_TYPE:
                                            case ENUMERATION:
                                            case PAGE_FORMAT:
                                            case VARIABLE_PATH:
                                            case BOOLEAN:
                                                // Compare as text (alphabetically)
                                                result = cell1.compareTo(cell2);
                                                break;

                                            default:
                                                // Check if the initial portion of both cells is a
                                                // number
                                                if (cell1.matches(DefaultInputType.INTEGER.getInputMatch() + ".*")
                                                    && cell2.matches(DefaultInputType.INTEGER.getInputMatch() + ".*"))
                                                {
                                                    switch (inputFormat)
                                                    {
                                                        case INTEGER:
                                                            // Compare the two cell values as
                                                            // integers
                                                            result = Integer.compare(Integer.valueOf(cell1.replaceAll("("
                                                                                                                      + DefaultInputType.INTEGER.getInputMatch()
                                                                                                                      + ").*",
                                                                                                                      "$1")),
                                                                                     Integer.valueOf(cell2.replaceAll("("
                                                                                                                      + DefaultInputType.INTEGER.getInputMatch()
                                                                                                                      + ").*",
                                                                                                                      "$1")));
                                                            break;

                                                        case HEXADECIMAL:
                                                            // Compare the two cell values as
                                                            // integers
                                                            result = Integer.compare(Integer.decode(cell1.replaceAll("("
                                                                                                                     + DefaultInputType.HEXADECIMAL.getInputMatch()
                                                                                                                     + ").*",
                                                                                                                     "$1")),
                                                                                     Integer.decode(cell2.replaceAll("("
                                                                                                                     + DefaultInputType.HEXADECIMAL.getInputMatch()
                                                                                                                     + ").*",
                                                                                                                     "$1")));
                                                            break;

                                                        case FLOAT:
                                                        case MINIMUM:
                                                        case MAXIMUM:
                                                            // Compare the two cell values as
                                                            // floating points
                                                            result = Double.compare(Double.valueOf(cell1.replaceAll("("
                                                                                                                    + DefaultInputType.FLOAT.getInputMatch()
                                                                                                                    + ").*",
                                                                                                                    "$1")),
                                                                                    Double.valueOf(cell2.replaceAll("("
                                                                                                                    + DefaultInputType.FLOAT.getInputMatch()
                                                                                                                    + ").*",
                                                                                                                    "$1")));
                                                            break;

                                                        case RATE:
                                                            // Calculate the value of the cells'
                                                            // expressions, then compare the
                                                            // results as floating point values
                                                            result = Double.compare(CcddMathExpressionHandler.evaluateExpression(cell1),
                                                                                    CcddMathExpressionHandler.evaluateExpression(cell2));
                                                            break;

                                                        case ARRAY:
                                                            // Array sizes are in the format
                                                            // #<,#<...>. Each cell's array
                                                            // dimensions are first separated, then
                                                            // the first dimension is compared
                                                            // between the two cells, then the
                                                            // second, and so on until a mismatch
                                                            // is found; the sort is performed
                                                            // based on the mismatch (e.g., '1, 2'
                                                            // follows '1' when sorted in ascending
                                                            // order)
                                                            String[] dim1 = cell1.split("\\s*,\\s*");
                                                            String[] dim2 = cell2.split("\\s*,\\s*");

                                                            // Check if the first array size has
                                                            // the same dimensions than the second
                                                            if (dim1.length == dim2.length)
                                                            {
                                                                // Step through each array
                                                                // dimension as long as there's no
                                                                // mismatch
                                                                for (int index = 0; index < dim1.length && result == 0; index++)
                                                                {
                                                                    // Check if the dimension
                                                                    // values are integers (and not
                                                                    // a macro or sizeof() call)
                                                                    if (dim1[index].matches(DefaultInputType.INTEGER.getInputMatch())
                                                                        && dim2[index].matches(DefaultInputType.INTEGER.getInputMatch()))
                                                                    {
                                                                        // Compare the two array
                                                                        // dimensions
                                                                        result = Integer.compare(Integer.valueOf(dim1[index]),
                                                                                                 Integer.valueOf(dim2[index]));
                                                                    }
                                                                    // One or both dimension values
                                                                    // isn't a number
                                                                    else
                                                                    {
                                                                        // Compare as text
                                                                        // (alphabetically)
                                                                        result = dim1[index].compareTo(dim2[index]);
                                                                    }
                                                                }
                                                            }
                                                            // Check if the first array size has
                                                            // the fewer dimensions than the second
                                                            else if (dim1.length < dim2.length)
                                                            {
                                                                // Step through each array
                                                                // dimension as long as there's no
                                                                // mismatch
                                                                for (int index = 0; index < dim1.length && result == 0; index++)
                                                                {
                                                                    // Check if the dimension
                                                                    // values are integers (and not
                                                                    // a macro or sizeof() call)
                                                                    if (dim1[index].matches(DefaultInputType.INTEGER.getInputMatch())
                                                                        && dim2[index].matches(DefaultInputType.INTEGER.getInputMatch()))
                                                                    {
                                                                        // Compare the two array
                                                                        // dimensions
                                                                        result = Integer.compare(Integer.valueOf(dim1[index]),
                                                                                                 Integer.valueOf(dim2[index]));
                                                                    }
                                                                    // One or both dimension values
                                                                    // isn't a number
                                                                    else
                                                                    {
                                                                        // Compare as text
                                                                        // (alphabetically)
                                                                        result = dim1[index].compareTo(dim2[index]);
                                                                    }
                                                                }

                                                                // Check if the each pair of array
                                                                // dimension values are identical
                                                                if (result == 0)
                                                                {
                                                                    // Set the result to indicate
                                                                    // the first cell comes before
                                                                    // the second since the second
                                                                    // has more dimensions
                                                                    result = -1;
                                                                }
                                                            }
                                                            // The first array size has the more
                                                            // dimensions than the second
                                                            else
                                                            {
                                                                // Step through each array
                                                                // dimension as long as there's no
                                                                // mismatch
                                                                for (int index = 0; index < dim2.length && result == 0; index++)
                                                                {
                                                                    // Check if the dimension
                                                                    // values are integers (and not
                                                                    // a macro or sizeof() call)
                                                                    if (dim1[index].matches(DefaultInputType.INTEGER.getInputMatch())
                                                                        && dim2[index].matches(DefaultInputType.INTEGER.getInputMatch()))
                                                                    {
                                                                        // Compare the two array
                                                                        // dimensions
                                                                        result = Integer.compare(Integer.valueOf(dim1[index]),
                                                                                                 Integer.valueOf(dim2[index]));
                                                                    }
                                                                    // One or both dimension values
                                                                    // isn't a number
                                                                    else
                                                                    {
                                                                        // Compare as text
                                                                        // (alphabetically)
                                                                        result = dim1[index].compareTo(dim2[index]);
                                                                    }
                                                                }

                                                                // Check if the each pair of array
                                                                // dimension values are identical
                                                                if (result == 0)
                                                                {
                                                                    // Set the result to indicate
                                                                    // the first cell comes after
                                                                    // the second since the second
                                                                    // has fewer dimensions
                                                                    result = 1;
                                                                }
                                                            }

                                                            break;

                                                        default:
                                                            break;
                                                    }
                                                }
                                                // One or both cells doesn't begin with a number
                                                // (this is the case if the cell begins with a
                                                // macro or sizeof() call)
                                                else
                                                {
                                                    // Compare as text (alphabetically)
                                                    result = cell1.compareTo(cell2);
                                                }
                                        }
                                    }

                                    return result;
                                }
                            });
                        }
                    }
                }
            }

            /**************************************************************************************
             * Move the selected row(s) in the specified direction if possible. Account for if the
             * selection or target is an array definition or member
             *
             * @param rowDelta
             *            row move direction (-1 for up, +1 for down)
             *************************************************************************************/
            private void adjustAndMoveSelectedRows(int rowDelta)
            {
                int modelStartRow;
                int modelEndRow;
                boolean isCanMove = false;

                // Set the selected start and end rows
                MoveCellSelection selected = new MoveCellSelection();

                // Set the selected start and end rows (model coordinates), and the direction to
                // move
                modelStartRow = selected.getStartRow();
                modelEndRow = selected.getEndRow();

                // Check if the selected row(s) can be moved in the desired direction
                if ((rowDelta < 0 && modelStartRow > 0)
                    || (rowDelta > 0 && modelEndRow < tableModel.getRowCount() - 1))
                {
                    // Check if the table can display arrays
                    if (isCanHaveArrays())
                    {
                        // While the start row references an array member
                        while (ArrayVariable.isArrayMember(getExpandedValueAt(modelStartRow,
                                                                              variableNameIndex)))
                        {
                            // Decrement the start index to get to the array definition row
                            modelStartRow--;
                        }

                        // Check if the selected ending row references an array definition
                        if (!getExpandedValueAt(modelEndRow, arraySizeIndex).isEmpty()
                            && !ArrayVariable.isArrayMember(getExpandedValueAt(modelEndRow,
                                                                               variableNameIndex)))
                        {
                            // Increment the end row so that the members will be included below
                            modelEndRow++;
                        }

                        boolean isIncludeMember = false;

                        // While the end row references an array member and the end of the table
                        // model hasn't been reached
                        while (modelEndRow < tableModel.getRowCount()
                               && ArrayVariable.isArrayMember(getExpandedValueAt(modelEndRow,
                                                                                 variableNameIndex)))
                        {
                            // Increment the end index to get to the end of the array
                            modelEndRow++;
                            isIncludeMember = true;
                        }

                        // Check if the ending row was adjusted to include an array member
                        if (isIncludeMember)
                        {
                            // Decrement the row index since the row selection is inclusive
                            modelEndRow--;
                        }

                        // Check if the selected row(s) can be moved in the desired direction
                        if ((rowDelta < 0 && modelStartRow > 0)
                            || (rowDelta > 0 && modelEndRow < tableModel.getRowCount() - 1))
                        {
                            // Get the array size column value for the target row
                            String arraySize = getExpandedValueAt((rowDelta < 0
                                                                                ? modelStartRow
                                                                                : modelEndRow)
                                                                  + rowDelta,
                                                                  arraySizeIndex);

                            // Check if the array size is present on this row
                            if (!arraySize.isEmpty())
                            {
                                // Get the total number of array members
                                int totalSize = ArrayVariable.getNumMembersFromArraySize(arraySize);

                                // Adjust the number of rows to move based on the number of array
                                // members
                                rowDelta += totalSize * rowDelta;
                            }

                            // Set the flag to indicate the selected row(s) can be moved
                            isCanMove = true;
                        }
                    }
                    // The table can't have an array
                    else
                    {
                        // Set the flag to indicate the selected row(s) can be moved
                        isCanMove = true;
                    }

                    // Calculate the row that the selected row(s) will be moved to
                    int modelToRow = modelStartRow + rowDelta;

                    // Check if the selected row(s) can be moved
                    if (isCanMove)
                    {
                        // Move the row(s) in the specified direction and update the cell selection
                        performRowMove(modelStartRow, modelEndRow, modelToRow, selected, rowDelta);
                    }
                }
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for moving the selected row(s) up one row in
             * order to prevent moving a row within an array definition and its member rows;
             * instead skip past the array
             *************************************************************************************/
            @Override
            protected void moveRowUp()
            {
                // Move the selected row(s) up if possible
                adjustAndMoveSelectedRows(-1);
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for moving the selected row(s) down one row in
             * order to prevent moving a row within an array definition and its member rows;
             * instead skip past the array
             *************************************************************************************/
            @Override
            protected void moveRowDown()
            {
                // Move the selected row(s) down if possible
                adjustAndMoveSelectedRows(1);
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for moving the selected row(s) so that
             * adjustments can be made to the rates for any packed variables
             *
             * @param startRow
             *            selected starting row, in model coordinates
             *
             * @param endRow
             *            selected ending row, in model coordinates
             *
             * @param toRow
             *            target row to move the selected row(s) to, in model coordinates
             *
             * @param selected
             *            cell selection class
             *
             * @param rowDelta
             *            row move direction and magnitude
             *************************************************************************************/
            @Override
            protected void performRowMove(int startRow,
                                          int endRow,
                                          int toRow,
                                          MoveCellSelection selected,
                                          int rowDelta)
            {
                // Move the row(s)
                super.performRowMove(startRow, endRow, toRow, selected, rowDelta);

                // Check if this is a root structure table
                if (dbTable.isRootStructure(currentTableInfo.getTablePath()))
                {
                    // Load the table data into a list
                    List<Object[]> tableData = getTableDataList(false);

                    // Adjust the rate for any packed variables, beginning with the lowest affected
                    // row index
                    setAllPackedVariableRates(tableData, Math.min(startRow, toRow), false);

                    // Check if a rate value changed
                    if (isRateChange)
                    {
                        // Load the array of data into the table
                        loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);
                    }
                }
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for putting data into a new row inserted below
             * the specified row in order to adjust the insertion index based on the presence of
             * array members
             *
             * @param targetRow
             *            index of the row in model coordinates below which to insert the new row
             *
             * @param data
             *            data to place in the inserted row
             *
             * @return The new row's index, in model coordinates, adjusted as needed to account for
             *         array member visibility
             *************************************************************************************/
            @Override
            protected int insertRowData(int targetRow, Object[] data)
            {
                // Check if table has rows, and has variable name and array size columns
                if (targetRow != -1 && isCanHaveArrays())
                {
                    // Get the array size value
                    String arraySize = getExpandedValueAt(targetRow, arraySizeIndex);

                    // Check if the array size is present on this row but not an array member
                    // (i.e., this is the array definition row)
                    if (!arraySize.isEmpty()
                        && !ArrayVariable.isArrayMember(getExpandedValueAt(targetRow,
                                                                           variableNameIndex)))
                    {
                        // Adjust the row index past the array definition and member rows
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

                // Insert the supplied data below the selected row
                return super.insertRowData(targetRow, data);
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for removing a row from the table. Array
             * member rows are ignored unless the array definition row is also deleted; for this
             * case the entire array is removed
             *
             * @param tableData
             *            list containing the table data row arrays
             *
             * @param modelRow
             *            row to remove (model coordinates)
             *
             * @return The index of the row prior to the last deleted row's index
             *************************************************************************************/
            @Override
            protected int removeRow(List<Object[]> tableData, int modelRow)
            {
                boolean isArray = false;

                // Check if the table has array size and variable name columns
                if (isCanHaveArrays())
                {
                    // Extract the array size cell value
                    String arraySize = getExpandedValueAt(modelRow, arraySizeIndex);

                    // Check if an array size is present
                    if (!arraySize.isEmpty())
                    {
                        // Set the flag indicating that an array row is being removed
                        isArray = true;

                        // Perform while this row is an array member
                        while (ArrayVariable.isArrayMember(tableData.get(modelRow)[variableNameIndex]))
                        {
                            // Move the row index up
                            modelRow--;
                        }

                        // Get the row index of the last array member
                        int arrayRow = modelRow + ArrayVariable.getNumMembersFromArraySize(arraySize);

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

                // Check if the row does not represent an array definition or member
                if (!isArray)
                {
                    // Delete the row
                    super.removeRow(tableData, modelRow);
                }

                // Adjust the rate for any packed variables, beginning with this row
                setAllPackedVariableRates(tableData, convertRowIndexToView(modelRow), false);

                return modelRow - 1;
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for getting the special replacement character
             * when deleting the contents of a cell. Get the corresponding cell value from the
             * table's prototype
             *
             * @param row
             *            cell row index in model coordinates
             *
             * @param column
             *            cell column index in model coordinates
             *
             * @return The corresponding cell value from the tables' prototype
             *************************************************************************************/
            @Override
            protected String getSpecialReplacement(int row, int column)
            {
                return dbTable.queryTableCellValue(currentTableInfo.getPrototypeName(),
                                                   committedTableInfo.getData()[row][primaryKeyIndex].toString(),
                                                   typeDefn.getColumnNamesDatabaseQuoted()[column],
                                                   editorDialog);
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method for deleting a cell. Set the special character
             * flag to false if the table is a prototype - prototypes can't have an entry in the
             * custom values table so no special handling is needed for this case
             *
             * @param isReplaceSpecial
             *            false to replace the cell value with a blank; true to replace the cell
             *            contents with the prototype's corresponding cell value
             *************************************************************************************/
            @Override
            protected void deleteCell(boolean isReplaceSpecial)
            {
                super.deleteCell(isReplaceSpecial && !currentTableInfo.isPrototype());
            }

            /**************************************************************************************
             * Adjust the starting row index to the next row during a paste (insert) operation. If
             * the insertion point falls within an array, skip to the row immediately following the
             * array's members
             *
             * @param startRow
             *            starting row index in view coordinates
             *
             * @return Starting row index, in model coordinates, at which to insert a new row
             *************************************************************************************/
            protected int adjustPasteStartRow(int startRow)
            {
                // Check if the starting row index references a valid row
                if (startRow >= 0 && startRow < getRowCount())
                {
                    // Convert the row index to model coordinates and adjust the starting row index
                    // to the next row
                    startRow = convertRowIndexToModel(startRow) + 1;
                }
                // The starting index is not a valid row
                else
                {
                    // Set the starting row index to the end of the table
                    startRow = tableModel.getRowCount();
                }

                // Check if the starting row index is not at the end of the table, the table has
                // variable size and array name columns, and an array size is present
                if (startRow < tableModel.getRowCount()
                    && isCanHaveArrays()
                    && !getExpandedValueAt(startRow, arraySizeIndex).isEmpty())
                {
                    // Perform while this row is an array member and the end of the table has not
                    // been reached
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

            /**************************************************************************************
             * Determine if a row insertion is required during a paste operation. Array member rows
             * are inserted automatically when an array is defined, so if an array member is being
             * inserted no row needs to be inserted by the paste operation
             *
             * @param index
             *            current index into the cell data array
             *
             * @param cellData
             *            array containing the cell data being inserted
             *
             * @param startColumn
             *            data insertion starting column index
             *
             * @param endColumn
             *            data insertion ending column index
             *
             * @return true if a row should be inserted; false otherwise
             *************************************************************************************/
            protected boolean isInsertRowRequired(int index,
                                                  Object[] cellData,
                                                  int startColumn,
                                                  int endColumn)
            {
                boolean isNotArrayMember = true;

                // Get the variable name column index in view coordinates
                int variableNameIndexView = convertColumnIndexToView(variableNameIndex);

                // Step through the row of data being inserted
                for (int column = startColumn; column <= endColumn; column++, index++)
                {
                    // Check if the column index matches the variable name column
                    if (column == variableNameIndexView)
                    {
                        // Check if the variable name cell has a value and is an array member
                        if (cellData[index] == null || ArrayVariable.isArrayMember(cellData[index]))
                        {
                            // Set the flag to indicate a new row doesn't need to be inserted
                            isNotArrayMember = false;
                        }

                        // Stop searching
                        break;
                    }
                }

                return isNotArrayMember;
            }

            /**************************************************************************************
             * Override the paste method so that hidden rows (array members) are displayed prior to
             * pasting in new data. If an array member variable is pasted into a structure table
             * then the array definition is automatically generated under certain conditions
             *************************************************************************************/
            @Override
            protected boolean pasteData(Object[] cellData,
                                        int numColumns,
                                        boolean isInsert,
                                        boolean isAddIfNeeded,
                                        boolean startFirstColumn,
                                        boolean combineAsSingleEdit,
                                        boolean highlightPastedData)
            {
                Boolean showMessage = true;

                // Check if the pasted data should be combined into a single edit operation
                if (combineAsSingleEdit)
                {
                    // End any active edit sequence, then disable auto-ending so that the paste
                    // operation can be handled as a single edit for undo/redo purposes
                    getUndoManager().endEditSequence();
                    getUndoHandler().setAutoEndEditSequence(false);
                }

                // Get the table data array
                List<Object[]> tableData = getTableDataList(false);

                // Calculate the number of rows to be pasted in
                int numRows = cellData.length / numColumns;

                // Initialize the starting row to the first row, which is the default if no row is
                // selected
                int startRow = 0;

                // Check if no row is selected
                if (getSelectedRow() == -1)
                {
                    // Clear the column selection. The column selection can remain in effect after
                    // an undo action that clears the row selection. It needs to be cleared if
                    // invalid so that the starting column index is correctly calculated below
                    getColumnModel().getSelectionModel().clearSelection();
                }
                // A row is selected
                else
                {
                    // Determine the starting row for pasting the data based on the selected row
                    startRow = convertRowIndexToModel(getSelectedRow())
                               + getSelectedRowCount()
                               - 1;
                }

                // Determine the starting column and ending column for pasting the data. If no
                // column is selected then default to the first column. Data pasted outside of the
                // column range is ignored
                int startColumn = startFirstColumn
                                                   ? 0
                                                   : Math.max(Math.max(getSelectedColumn(), 0),
                                                              getSelectedColumn()
                                                                                                + getSelectedColumnCount()
                                                                                                - 1);
                int endColumn = startColumn + numColumns - 1;
                int endColumnSelect = Math.min(endColumn, getColumnCount() - 1);

                // Check if the the table is capable of displaying arrays and that the array
                // members are hidden
                if (isCanHaveArrays() && !isShowArrayMembers)
                {
                    // Show the array members. All rows must be visible in order for the pasted
                    // data to be inserted correctly. The model and view row coordinates are the
                    // same after expanding the array members. Note that this clears the row and
                    // column selection
                    showHideArrayMembers();
                }

                // Check if the data is to be inserted versus overwriting existing cells
                if (isInsert)
                {
                    // Adjust the starting row index to the one after the selected row, and account
                    // for hidden rows, if applicable
                    startRow = adjustPasteStartRow(startRow);
                }
                // Overwrite the existing cells. Check if no row is selected or if the table
                // contains no rows
                else if (startRow == -1)
                {
                    // Set the start row to the first row
                    startRow = 0;
                }

                // Determine the ending row for pasting the data
                int endRow = startRow + numRows;

                // Clear the cell selection
                clearSelection();

                // Counters for the number of array member rows added (due to pasting in an array
                // definition) and the number of rows ignored (due to the first pasted row(s) being
                // an array member)
                int arrayRowsAdded = 0;
                int totalAddedRows = 0;
                int skippedRows = 0;

                boolean isIgnoreRow = false;

                // Initialize the view column indices for the variable name and array size
                int variableNameView = -1;
                int arraySizeView = -1;

                // Check if the table has a variable name column
                if (variableNameIndex != -1)
                {
                    // Get the viewable column index of the variable name column
                    variableNameView = convertColumnIndexToView(variableNameIndex);

                    // Check if the variable name column is outside the paste column boundary
                    if (startColumn > variableNameView || endColumn < variableNameView)
                    {
                        // Reset the view column index so that the check for an array member
                        // variable isn't performed below
                        variableNameView = -1;
                    }
                    // The variable name index falls within the pasted cells; check if the table
                    // has an array size column
                    else if (arraySizeIndex != -1)
                    {
                        // Get the viewable column index of the array size column
                        arraySizeView = convertColumnIndexToView(arraySizeIndex);

                        // Check if the array size column is outside the paste column boundary
                        if (startColumn > arraySizeView || endColumn < arraySizeView)
                        {
                            // Reset the view column index so that an array definition isn't
                            // created from the first array member
                            arraySizeView = -1;
                        }
                    }
                }

                // Step through each new row
                for (int index = 0, row = startRow; row < endRow && showMessage != null; row++)
                {
                    boolean skipRow = false;

                    // Calculate the row in the table data where the values are to be pasted. This
                    // must be adjusted to account for pasting in arrays
                    int adjustedRow = row + totalAddedRows - skippedRows;

                    // Check if a variable name is within the data being pasted. If an array member
                    // is pasted then it requires an array definition in a row above. If there is
                    // no array definition, but this is the first array member, then the definition
                    // is created from the pasted member data if the pasted data also includes the
                    // array size. If the definition isn't created then the row of data is skipped
                    if (variableNameView != -1)
                    {
                        // Get the index into the cell data for this row's variable name
                        int varIndex = index + variableNameView - startColumn;

                        // Check if rows were removed due to an array size reduction or removal
                        if (arrayRowsAdded < 0)
                        {
                            // Set the flag indicating that this row of data is to be skipped and
                            // increment the skipped row counter
                            skipRow = true;
                            skippedRows++;

                            // Adjust the row counters so that all array member rows are skipped
                            arrayRowsAdded++;
                            totalAddedRows++;
                        }
                        // Check if a variable name value is present to paste and that the variable
                        // is an array member
                        else if (cellData[varIndex] != null
                                 && ArrayVariable.isArrayMember(cellData[varIndex]))
                        {
                            // Check if rows were added for the previous pasted row to accommodate
                            // the array members
                            if (arrayRowsAdded > 0)
                            {
                                // Move the row index back so that the array member data is pasted
                                // in the proper row
                                adjustedRow -= arrayRowsAdded;
                                arrayRowsAdded--;
                                totalAddedRows--;
                            }
                            // No rows were added for this array member
                            else
                            {
                                // Get the variable name for the current row of data
                                String varName = cellData[index + variableNameView - startColumn].toString();

                                // Check if the variable name is an array member and there is no
                                // corresponding array definition in a previous row
                                if (ArrayVariable.isArrayMember(varName)
                                    && (adjustedRow == 0
                                        || !ArrayVariable.removeArrayIndex(tableData.get(adjustedRow - 1)[variableNameIndex].toString())
                                                         .equals(ArrayVariable.removeArrayIndex(varName))))
                                {
                                    // Check if the array size is also pasted and this is the first
                                    // array member (the sum of its array index values equal zero)
                                    if (arraySizeView != -1
                                        && !cellData[index + arraySizeView - startColumn].toString().isEmpty()
                                        && ArrayVariable.getNumMembersFromArraySize(ArrayVariable.getVariableArrayIndex(varName)) == 0)
                                    {
                                        // Copy the row of data for the first array member, then
                                        // alter the name by removing the array index (indices) to
                                        // make this the array definition
                                        Object[] arrayDefn = Arrays.copyOfRange(cellData, index, index + numColumns);
                                        arrayDefn[index + variableNameView - startColumn] = ArrayVariable.removeArrayIndex(varName);

                                        // Step through each pasted column
                                        for (int defnCol = startColumn; defnCol <= endColumn; defnCol++)
                                        {
                                            // Check if the column's input type isn't one necessary
                                            // to define a structure
                                            if (!DefaultColumn.isTypeRequiredColumn(TYPE_STRUCTURE,
                                                                                    inputTypeHandler,
                                                                                    typeDefn.getInputTypesVisible()[defnCol]))
                                            {
                                                // Initialize the cell to a blank (false if a
                                                // boolean)
                                                arrayDefn[defnCol] = "";
                                            }
                                        }

                                        // Insert the new array definition row within the pasted
                                        // cell data array. This is the row that gets processed
                                        // next, which creates the array; then the array member(s)
                                        // is pasted. If no array size is included in the pasted
                                        // data then just the variable name (sans array index) is
                                        // pasted
                                        cellData = CcddUtilities.concatenateArrays(CcddUtilities.concatenateArrays(Arrays.copyOfRange(cellData,
                                                                                                                                      0,
                                                                                                                                      index),
                                                                                                                   arrayDefn),
                                                                                   Arrays.copyOfRange(cellData,
                                                                                                      index,
                                                                                                      cellData.length));

                                        // Adjust the end row index to account for the added row
                                        endRow++;
                                    }
                                    else
                                    {
                                        // Check if this is the first pasted row; if so then this
                                        // array member has no definition
                                        if (adjustedRow == startRow)
                                        {
                                            // Set the flag indicating that array member rows are
                                            // ignored
                                            isIgnoreRow = true;
                                        }

                                        // Set the flag indicating that this row of data is to be
                                        // skipped and increment the skipped row counter
                                        skipRow = true;
                                        skippedRows++;

                                        // Update the cell data index so that this row is skipped
                                        index += numColumns;
                                    }
                                }
                            }
                        }
                    }

                    // Check that this row is not to be ignored
                    if (!skipRow)
                    {
                        // Check if inserting is in effect and the cell value is null
                        if (isInsert && index < cellData.length && cellData[index] == null)
                        {
                            // Replace the null with a blank
                            cellData[index] = "";
                        }

                        // Check if a row needs to be inserted to contain the cell data
                        if ((isInsert
                             || (isAddIfNeeded && adjustedRow == tableData.size()))
                            && isInsertRowRequired(index, cellData, startColumn, endColumn))
                        {
                            // Insert a row at the selection point
                            tableData.add(adjustedRow, getEmptyRow());
                        }

                        // Store the index into the array of data to be pasted
                        int indexSave = index;

                        // If pasting values over existing ones it's possible that the check for a
                        // cell being alterable will return false due to other cells in the row
                        // that haven't yet been pasted over (e.g., a bit length in a subsequent
                        // column prevents pasting in the array size). To overcome this two passes
                        // for each row are made; first cells containing blanks in the pasted data
                        // are pasted, then the cells that are not empty are pasted
                        for (int pass = 1; pass <= 2; pass++)
                        {
                            // Check if this is the second pass through the row's columns
                            if (pass == 2)
                            {
                                // Reset the index into the array of data to be pasted so that the
                                // non-blank cells can be processed
                                index = indexSave;
                            }

                            // Step through the columns, beginning at the one with the focus
                            for (int column = startColumn; column <= endColumn
                                                           && showMessage != null; column++)
                            {
                                // Check that the column falls within the bounds of the table. If
                                // outside the bounds or protected then discard the value
                                if (column < getColumnCount())
                                {
                                    // Convert the column coordinate from view to model
                                    int columnModel = convertColumnIndexToModel(column);

                                    // Get the value to be pasted into the cell, cleaning up the
                                    // value if needed. If the number of cells to be filled exceeds
                                    // the stored values then insert a blank. A null paste value
                                    // indicates that the current cell's value won't be overwritten
                                    Object newValue = index < cellData.length
                                                                              ? (cellData[index] != null
                                                                                                         ? cleanUpCellValue(cellData[index],
                                                                                                                            adjustedRow,
                                                                                                                            columnModel)
                                                                                                         : (isInsert
                                                                                                                     ? ""
                                                                                                                     : null))
                                                                              : "";

                                    // Check if the paste value isn't null (a null value indicates
                                    // that the current cell's value won't be overwritten). For the
                                    // first pass through this row's column process only blank
                                    // cells; for the second pass process only non-blank cells. If
                                    // one of these criteria is met then check if the cell is
                                    // alterable
                                    if (newValue != null
                                        && ((pass == 1 && newValue.toString().isEmpty())
                                            || (pass == 2 && !newValue.toString().isEmpty()))
                                        && isDataAlterable(tableData.get(adjustedRow),
                                                           adjustedRow,
                                                           columnModel))
                                    {
                                        // Get the original cell value
                                        Object oldValue = tableData.get(adjustedRow)[columnModel];

                                        // Check if the value has changed and, if this values are
                                        // being inserted, that the value isn't blank
                                        if (!oldValue.equals(newValue)
                                            && !(isInsert && newValue.toString().isEmpty()))
                                        {
                                            // Insert the value into the cell
                                            tableData.get(adjustedRow)[columnModel] = newValue;

                                            // Get the number of rows in the table prior to
                                            // inserting the new value
                                            int previousRows = tableData.size();

                                            // Validate the new cell contents
                                            showMessage = validateCellContent(tableData,
                                                                              adjustedRow,
                                                                              columnModel,
                                                                              oldValue,
                                                                              newValue,
                                                                              showMessage,
                                                                              cellData.length > 1);

                                            // Check if the user selected the Cancel button
                                            // following an invalid input
                                            if (showMessage == null)
                                            {
                                                // Stop pasting data
                                                continue;
                                            }

                                            // Get the number of rows added due to pasting in the
                                            // new value. This is non-zero if an array definition
                                            // is pasted in or if an existing array's size is
                                            // altered
                                            int deltaRows = tableData.size() - previousRows;

                                            // Check if the row count changed
                                            if (deltaRows > 0)
                                            {
                                                // Store the number of added/deleted rows and
                                                // update the total number of added/deleted rows
                                                arrayRowsAdded = deltaRows;
                                                totalAddedRows += arrayRowsAdded;
                                            }
                                        }
                                    }
                                }

                                // Increment the index to the next value to paste
                                index++;
                            }
                        }
                    }
                }

                // Check if the user hasn't selected the Cancel button following an invalid input
                if (showMessage != null)
                {
                    // Load the array of data into the table
                    loadDataArrayIntoTable(tableData.toArray(new Object[0][0]), true);

                    // Check if automatic edit sequence ending is in effect
                    if (getUndoHandler().isAutoEndEditSequence())
                    {
                        // Flag the end of the editing sequence for undo/redo purposes
                        getUndoManager().endEditSequence();
                    }

                    // Check if the pasted data should be highlighted
                    if (highlightPastedData)
                    {
                        // Check if there are rows left to be selected
                        if (endRow - 1 - skippedRows > 0)
                        {
                            // Select all of the rows into which the data was pasted
                            setRowSelectionInterval(startRow, endRow - 1 - skippedRows);
                        }

                        // Select all of the columns into which the data was pasted
                        setColumnSelectionInterval(startColumn, endColumnSelect);

                        // Select the pasted cells and force the table to be redrawn so that the
                        // changes are displayed
                        setSelectedCells(startRow, endRow - 1, startColumn, endColumnSelect);
                        repaint();
                    }

                    // Check if any rows were ignored
                    if (isIgnoreRow)
                    {
                        // Inform the user how many rows were skipped
                        new CcddDialogHandler().showMessageDialog(parent,
                                                                  "<html><b>"
                                                                          + skippedRows
                                                                          + " array member row(s) ignored due "
                                                                          + "to missing array definition(s)",
                                                                  "Rows Ignored",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }
                }

                // Set the flag that indicates the last edited cell's content is valid (if an
                // invalid input set the flag to false then it can prevent closing the editor)
                setLastCellValid(true);

                // Check if the pasted data should be combined into a single edit operation
                if (combineAsSingleEdit)
                {
                    // Re-enable auto-ending of the edit sequence and end the sequence. The pasted
                    // data can be removed with a single undo if desired
                    getUndoHandler().setAutoEndEditSequence(true);
                    getUndoManager().endEditSequence();
                }

                return showMessage == null;
            }

            /**************************************************************************************
             * Override the method for cleaning-up of the cell value. The default is to remove any
             * leading and trailing white space characters. This method skips removal of white
             * space characters for cells having input types that allow it
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
             *************************************************************************************/
            @Override
            protected Object cleanUpCellValue(Object value, int row, int column)
            {
                // Check if the cell isn't being actively edited and if the cell value represents a
                // string (i.e., it isn't boolean, etc.)
                if (!table.isEditing() && value instanceof String)
                {
                    // Get the input type for this column
                    InputType inputType = typeDefn.getInputTypes()[column];

                    // Check if the column's input type doesn't allow leading and trailing white
                    // space characters
                    if (!inputType.equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_WHT_SPC))
                        && !inputType.equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.TEXT_MULTI_WHT_SPC)))
                    {
                        // Perform the default clean-up (remove leading and trailing white space
                        // characters)
                        value = super.cleanUpCellValue(value, row, column);
                    }
                }

                return value;
            }

            /**************************************************************************************
             * Handle a TableModelEvent event only if the table is open in a table editor
             *************************************************************************************/
            @Override
            public void tableChanged(final TableModelEvent tme)
            {
                // Check that the table is open in a table editor (versus open for a macro name
                // and/or value change, for example)
                if (editorDialog != null)
                {
                    super.tableChanged(tme);
                }
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Check that the table is open in a table editor (versus open for a macro name
                // and/or value change, for example)
                if (editorDialog != null)
                {
                    // Update the change indicator for the table
                    editorDialog.updateChangeIndicator(CcddTableEditorHandler.this);
                }
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);

        // Set common table parameters and characteristics
        table.setFixedCharacteristics(scrollPane,
                                      currentTableInfo.isPrototype(),
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

        // Set the reference to the editor's data field handler in the undo handler so that data
        // field value changes can be undone/redone correctly
        table.getUndoHandler().setFieldHandler(fieldHandler);

        // Set the undo/redo manager and handler for the description and data field values
        setEditPanelUndo(table.getUndoManager(), table.getUndoHandler());

        // Set the mouse listener to expand and collapse arrays
        setArrayExpansionListener();

        // Get the variable path separators and create the variable path column content, if present
        updateVariablePaths();

        // Check that the table is open in a table editor (versus open for a macro or data type
        // change, for example)
        if (editorDialog != null)
        {
            // Create the input field panel to contain the table editor
            createDescAndDataFieldPanel(ccddMain,
                                        editorDialog,
                                        scrollPane,
                                        committedTableInfo.getProtoVariableName(),
                                        committedTableInfo.getDescription(),
                                        committedTableInfo.getFieldInformation());

            // Set the current table information's field information to reference the input panel
            // field information
            currentTableInfo.setFieldInformation(getPanelFieldInformation());

            // Set the dialog name so that this dialog can be recognized as being open by the table
            // selection dialog, and the JTable name so that table change events can be identified
            // with this table
            setTableName();
        }
    }

    /**********************************************************************************************
     * Based on the input flag, display the macro values (names) in place of the macro names
     * (values)
     *
     * @param isExpand
     *            true to replace the macro names with the corresponding macro values; false to
     *            restore the macro names
     *
     * @param isVisibleOnly
     *            true to only expand the macros in those cells that are currently visible; false
     *            to expand the macros in all of the table cells
     *********************************************************************************************/
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
            // Determine first and last rows that are currently visible. Get the coordinates of the
            // table's visible rectangle
            Rectangle visRect = table.getVisibleRect();

            // Determine the first (upper) row. If a column is being resized or the table has no
            // rows then set to 0 (the first table row), otherwise set to the first visible row
            firstRow = Math.max(table.rowAtPoint(visRect.getLocation()), 0);

            // Move the rectangle down the height of the table, then back 1 pixel to remain within
            // the table boundaries
            visRect.translate(0, visRect.height - 1);

            // Get the last visible row plus 1 (as required by updateRowHeights). Defaults to 0 if
            // the table has no rows
            lastRow = table.rowAtPoint(visRect.getLocation()) + 1;

            // Check if the calculated last row is greater than the total number of rows in the
            // table
            if (lastRow == 0)
            {
                // Set the last row to the table's visible row count
                lastRow = table.getRowCount();
            }

            // Convert the row indices to model coordinates. Model coordinates are used to prevent
            // the change in the table value from being logged as undoable
            firstRow = table.convertRowIndexToModel(firstRow);
            lastRow = table.convertRowIndexToModel(lastRow - 1) + 1;
        }

        // Check if the macro values are being displayed
        if (isExpand)
        {
            // Create storage for the original cell values. Note the first two columns are unused
            originalCellData = new String[lastRow - firstRow][tableModel.getColumnCount()];
        }

        // Step through the visible rows
        for (int row = firstRow; row < lastRow; row++)
        {
            // Step through each column, ignoring the primary key and row index columns
            for (int column = NUM_HIDDEN_COLUMNS; column < tableModel.getColumnCount(); column++)
            {
                // Check if the macro values are being displayed
                if (isExpand)
                {
                    // Store the original cell values for when the macro names are restored
                    originalCellData[row][column] = tableModel.getValueAt(row, column).toString();
                }

                // Replace the table cells with their original contents (i.e., show macro names in
                // place of their corresponding values)
                tableModel.setValueAt((isExpand
                                                ? macroHandler.getMacroExpansion(originalCellData[row][column].toString())
                                                : originalCellData[row][column]),
                                      row,
                                      column,
                                      false);
            }
        }
    }

    /**********************************************************************************************
     * Create a mouse listener for expanding and collapsing arrays
     *********************************************************************************************/
    private void setArrayExpansionListener()
    {
        // Check if the variable name and array size columns are present
        if (isCanHaveArrays())
        {
            // Add a mouse listener to the table to handle mouse clicks on the data type column
            table.addMouseListener(new MouseAdapter()
            {
                /**********************************************************************************
                 * Handle mouse press events
                 *********************************************************************************/
                @Override
                public void mousePressed(MouseEvent me)
                {
                    // Check if the right mouse button is double clicked
                    if (me.getClickCount() == 2
                        && SwingUtilities.isRightMouseButton(me))
                    {
                        // Get the table row and column that was selected; convert the column index
                        // to the model index
                        int modelColumn = table.convertColumnIndexToModel(table.columnAtPoint(me.getPoint()));
                        int row = table.rowAtPoint(me.getPoint());

                        // Check if the array size column was clicked and that the row is valid
                        if (modelColumn == arraySizeIndex && row != -1)
                        {
                            // Toggle between showing or hiding the array member rows
                            showHideArrayMembers();
                        }
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Create the listener for handling mouse events in a table cell in order to allow opening a
     * child table by double right clicking the data type column cell
     *********************************************************************************************/
    private void createDataTypeColumnMouseListener()
    {
        // Check if the table has a data type column
        if (dataTypeIndex != -1)
        {
            // Add a mouse listener to the table to handle mouse clicks on the data type column
            table.addMouseListener(new MouseAdapter()
            {
                /**********************************************************************************
                 * Handle mouse button press events
                 *********************************************************************************/
                @Override
                public void mousePressed(MouseEvent me)
                {
                    // Check if the right mouse button is double clicked
                    if (me.getClickCount() == 2 && SwingUtilities.isRightMouseButton(me))
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
                                    // Incorporate any cell changes and terminate editing
                                    table.getCellEditor().stopCellEditing();
                                }

                                // Highlight the selected cell
                                table.setRowSelectionInterval(row, row);
                                table.setColumnSelectionInterval(column, column);

                                // Check if the data type is recognized. If the prototype structure
                                // is deleted the invalid structure reference remains in the cell
                                if (isCellValueFound(row, column))
                                {
                                    // Open the child table associated with the selected row's
                                    // variable name and data type, if valid
                                    openChildTable(row);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    /**********************************************************************************************
     * Check if the specified table cell is required and contains a value, or is a combo box and
     * the value matches one in the combo box list
     *
     * @param row
     *            table row number; view coordinates
     *
     * @param column
     *            table column number; view coordinates
     *
     * @return true if the cell is required and contains a value or is a combo box and the value is
     *         in the list
     *********************************************************************************************/
    private boolean isCellValueFound(int row, int column)
    {
        boolean found = true;

        // Convert the column index to the model coordinate
        int modelColumn = table.convertColumnIndexToModel(column);

        // Get the cell contents
        String value = table.getValueAt(row, column).toString();

        // Check if the cell is required and empty
        if (typeDefn.isRequired()[modelColumn] && value.isEmpty())
        {
            // Set the flag indicating that the cell value is invalid
            found = false;
        }
        // Check if this is cell contains a combo box, and that the cell isn't empty or the cell is
        // required
        else if (((DefaultCellEditor) table.getCellEditor(row, column)) != null
                 && ((DefaultCellEditor) table.getCellEditor(row, column)).getComponent() instanceof JComboBox
                 && (!value.isEmpty() || typeDefn.isRequired()[modelColumn]))
        {
            // Get a reference to the cell's combo box
            JComboBox<?> comboBox = (JComboBox<?>) ((DefaultCellEditor) table.getCellEditor(row,
                                                                                            column)).getComponent();
            found = false;

            // Step through each combo box item
            for (int index = 0; index < comboBox.getItemCount() && !found; index++)
            {
                // Check if the cell matches the combo box item, or if the column displays a
                // message ID name list that the name is present in the list item, ignoring the ID
                // value
                if (comboBox.getItemAt(index).equals(value)
                    || (msgIDNameIndex.contains(modelColumn)
                        && comboBox.getItemAt(index).toString().matches(value + " \\(.*")))
                {
                    // Set the flag indicating that the cell value is valid and stop searching
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    /**********************************************************************************************
     * Open the child table indicated by the data type row and column. If the variable and data
     * type for the specified row doesn't represent a child table then do nothing
     *
     * @param row
     *            table row number containing the variable name and data type; view coordinates
     *********************************************************************************************/
    private void openChildTable(int row)
    {
        // Convert the row to the model index
        int modelRow = table.convertRowIndexToModel(row);

        // Get the data type displayed in the combo box cell
        String dataType = getExpandedValueAt(modelRow, dataTypeIndex);

        // Check that the data type isn't a primitive (i.e., it's a structure) and that this isn't
        // an array definition row
        if (!dataTypeHandler.isPrimitive(dataType)
            && isCanHaveArrays()
            && (ArrayVariable.isArrayMember(getExpandedValueAt(modelRow, variableNameIndex))
                || getExpandedValueAt(modelRow, arraySizeIndex).isEmpty()))
        {
            // Get the row's primary key, variable name, and array size
            String rowPrimaryKey = tableModel.getValueAt(modelRow, primaryKeyIndex).toString();
            String variableName = getExpandedValueAt(modelRow, variableNameIndex);
            String arraySize = getExpandedValueAt(modelRow, arraySizeIndex);

            // Get the number of rows that have been committed to the database for this table
            int numCommitted = committedTableInfo != null
                                                          ? committedTableInfo.getData().length
                                                          : 0;

            // Step through each row in the committed version of the table data
            for (int comRow = 0; comRow < numCommitted; comRow++)
            {
                // Check if the primary key values match for these rows, indicating this row
                // represents the same one in both the committed and current table data
                if (rowPrimaryKey.equals(committedTableInfo.getData()[comRow][primaryKeyIndex]))
                {
                    // Check that the variable name isn't blank and if the variable name, data
                    // type, and array size values match for these rows. Only a child table with
                    // these parameters committed may be opened
                    if (!variableName.isEmpty()
                        && variableName.equals(newMacroHandler.getMacroExpansion(committedTableInfo.getData()[comRow][variableNameIndex].toString()))
                        && dataType.equals(newMacroHandler.getMacroExpansion(committedTableInfo.getData()[comRow][dataTypeIndex].toString()))
                        && arraySize.equals(newMacroHandler.getMacroExpansion(committedTableInfo.getData()[comRow][arraySizeIndex].toString())))
                    {
                        // Check if the table isn't a prototype (i.e., it's a child structure), or
                        // if it is a prototype that it's a top-level (root) structure
                        if (!currentTableInfo.isPrototype()
                            || dbTable.isRootStructure(currentTableInfo.getTablePath()))
                        {
                            // Load the selected child table's data into a table editor
                            dbTable.loadTableDataInBackground(currentTableInfo.getTablePath()
                                                              + ","
                                                              + dataType
                                                              + "."
                                                              + variableName,
                                                              editorDialog);
                        }
                        // The selection is a child of a prototype, and the prototype isn't a
                        // top-level (root) structure
                        else
                        {
                            // Load the selected child table's prototype data into a table editor.
                            // Since the prototype table is itself a child table, it can't have its
                            // own child tables
                            dbTable.loadTableDataInBackground(dataType, editorDialog);

                            // Inform the user that the prototype of the selected table is opened
                            new CcddDialogHandler().showMessageDialog(parent,
                                                                      "<html><b>Since prototype table '</b>"
                                                                              + currentTableInfo.getPrototypeName()
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

    /**********************************************************************************************
     * Set up the combo box containing the available primitive data types and existing structure
     * tables for display in the table's "Data Type" column cells. Set up the command argument
     * groups and create combo box(es) containing the available primitive data types for display in
     * the table's column cells with a 'data type' input type (excluding the "Data Type" column),
     * and associate enumeration, minimum, and maximum columns with the data type column
     *
     * @param allStructTbls
     *            array containing all structure table names; null to load the table names from the
     *            database
     *
     * @param tblTree
     *            reference to the CcddTableTreeHandler table tree; null to create the table tree
     *********************************************************************************************/
    protected void setUpDataTypeColumns(String[] allStructTbls, CcddTableTreeHandler tblTree)
    {
        // (Re)create a list to contain data type and enumeration pairings, and command argument
        // column groupings
        associatedColumns = new ArrayList<AssociatedColumns>();

        // Set to null to force the enumeration cell editor and associated combo box to be rebuilt
        enumDataTypeCellEditor = null;

        // Check if a cell is currently being edited
        if (table.getCellEditor() != null)
        {
            // Incorporate any cell changes and terminate editing
            table.getCellEditor().stopCellEditing();
        }

        // Set up the columns with an input type that displays primitive or primitive & structure
        // data types
        setDataTypeColumns(allStructTbls, tblTree);

        // Check if this is a structure table
        if (typeDefn.isStructure())
        {
            // Set the data type and enumeration column pairing(s) for structure tables
            setStructureDataTypeEnumAssociations();
        }
        // Check if this is a command table
        else if (typeDefn.isCommand())
        {
            // Set the associated command argument columns
            setCommandArgumentAssociations();
        }
    }

    /**********************************************************************************************
     * Set up or update the combo box(es) for table columns that display the available primitive
     * data types, or both the primitive data types and existing structure tables
     *
     * @param allStructTbls
     *            array containing all structure table names; null to load the table names from the
     *            database
     *
     * @param tblTree
     *            reference to the CcddTableTreeHandler table tree; null to create the table tree
     *********************************************************************************************/
    private void setDataTypeColumns(String[] allStructTbls, CcddTableTreeHandler tblTree)
    {
        validDataTypes = null;
        validStructureDataTypes = null;
        invalidDataTypes = null;

        // Get the lists of columns that display primitive data types and primitive & structure
        // data types
        List<Integer> primColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIMITIVE);
        List<Integer> primAndStructColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.PRIM_AND_STRUCT);

        // Check if any columns displaying primitive data types only exist
        if (!primColumns.isEmpty())
        {
            validDataTypes = new ArrayList<String>();

            // Create a combo box for displaying data types
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Add the primitive data types to the combo box list
            addPrimitivesToComboBox(comboBox);

            // Enable item matching for the combo box
            comboBox.enableItemMatching(table);

            // Step through each primitive column defined for this table's type
            for (int index : primColumns)
            {
                // Get the column reference for this data type column
                TableColumn dataTypeColumn = table.getColumnModel()
                                                  .getColumn(table.convertColumnIndexToView(index));

                // Set the column table editor to the combo box
                dataTypeColumn.setCellEditor(new ComboBoxCellEditor(comboBox));
            }

            // Create the enumerated data type cell editor
            createEnumDataTypeCellEditor();
        }

        // Check if any columns displaying both primitive & structure data types exist
        if (!primAndStructColumns.isEmpty())
        {
            invalidDataTypes = new ArrayList<String>();
            validStructureDataTypes = new ArrayList<String>();

            // Check if the list of valid data types wasn't already created above
            if (validDataTypes == null)
            {
                validDataTypes = new ArrayList<String>();
            }

            // Create a combo box for displaying data types
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Add the primitive data types to the combo box list
            addPrimitivesToComboBox(comboBox);

            // Get the array of prototype structure table names, if any
            allPrototypeStructureTables = (allStructTbls == null)
                                                                  ? dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE)
                                                                  : allStructTbls;

            // Add the structure data types to the combo box list
            addStructuresToComboBox(comboBox);

            // Enable item matching for the combo box
            comboBox.enableItemMatching(table);

            // Step through each primitive & structure column defined for this table's type
            for (int index : primAndStructColumns)
            {
                // Get the column reference for this data type column
                TableColumn dataTypeColumn = table.getColumnModel().getColumn(table.convertColumnIndexToView(index));

                // Set the column table editor to the combo box
                dataTypeColumn.setCellEditor(new ComboBoxCellEditor(comboBox));
            }

            // Create the enumerated data type cell editor
            createEnumDataTypeCellEditor();
        }
    }

    /**********************************************************************************************
     * Get the list of prototype structure table names that can be referenced in the current
     * column. If the current column is the array size or bit length then ancestors of this
     * structure are not included, otherwise all structures are returned
     *
     * @return List of valid prototype structure table names; null if no data type column exists
     *********************************************************************************************/
    protected List<String> getValidStructureDataTypes()
    {
        int editColumnModel = table.convertColumnIndexToModel(getTable().getEditingColumn());

        return typeDefn.isStructure()
               && currentTableInfo.isPrototype()
               && (editColumnModel == arraySizeIndex
                   || editColumnModel == bitLengthIndex)
                                                         ? validStructureDataTypes
                                                         : Arrays.asList(allPrototypeStructureTables);
    }

    /**********************************************************************************************
     * Add the primitive data types to the supplied combo box's item list
     *
     * @param comboBox
     *            reference to the combo box to which the primitive data types are added
     *********************************************************************************************/
    private void addPrimitivesToComboBox(PaddedComboBox comboBox)
    {
        // Step through each primitive data type
        for (String[] dataType : dataTypeHandler.getDataTypeData())
        {
            // get the data type's name
            String dataTypeName = CcddDataTypeHandler.getDataTypeName(dataType);

            // Add the data type to the combo box list
            comboBox.addItem(dataTypeName);

            // Add the primitive data type to the list of valid data types
            validDataTypes.add(dataTypeName);
        }
    }

    /**********************************************************************************************
     * Add the structure data types to the supplied combo box's item list and build the list of
     * invalid data types
     *
     * @param comboBox
     *            reference to the combo box to which the structure data types are added
     *********************************************************************************************/
    private void addStructuresToComboBox(PaddedComboBox comboBox)
    {
        // Check if any structure tables exist
        if (allPrototypeStructureTables != null && allPrototypeStructureTables.length != 0)
        {
            // Sort the array of structure table names alphabetically, ignoring case. This ordering
            // should match the ordering in the table tree (which is determined by a PostgreSQL
            // sort), and is the order displayed in the combo box
            Arrays.sort(allPrototypeStructureTables, String.CASE_INSENSITIVE_ORDER);

            // Create the list of invalid prototype structure table names by extracting every
            // prototype name from the table's path
            invalidDataTypes = new ArrayList<String>();

            // Check if this is a prototype structure table. The array size and bit length can only
            // be altered in a prototype, so there's no need to exclude any data types from the
            // list of valid types for an instance table
            if (currentTableInfo.isPrototype() && typeDefn.isStructure())
            {
                // Add the table to the invalid list - a table can't reference itself
                invalidDataTypes.add(currentTableInfo.getTablePath());

                // Check if the table is a child table
                if (!dbTable.isRootStructure(currentTableInfo.getTablePath()))
                {
                    // Build the prototype table name match string
                    String pathMatch = "," + currentTableInfo.getTablePath() + ".";

                    // Step through each variable path
                    for (String variablePath : variableHandler.getStructureAndVariablePaths())
                    {
                        // Get the index of the prototype name match in the variable path
                        int index = variablePath.indexOf(pathMatch);

                        // Check if the prototype is in the variable path
                        if (index != -1)
                        {
                            // Step through each ancestor prototype name in the variable path
                            for (String ancestorPrototype : variablePath.substring(0, index)
                                                                        .replaceAll("\\.[^,]+", "")
                                                                        .split(","))
                            {
                                // Check if the prototype name isn't already in the list
                                if (!invalidDataTypes.contains(ancestorPrototype))
                                {
                                    // This prototype table is an ancestor of the current structure
                                    // table. Add the prototype name to the invalid list
                                    invalidDataTypes.add(ancestorPrototype);
                                }
                            }
                        }
                    }
                }
            }

            // Step through each prototype structure table name
            for (String prototypeStructure : allPrototypeStructureTables)
            {
                // Check if the prototype structure is valid
                if (!invalidDataTypes.contains(prototypeStructure))
                {
                    // Add the prototype structure table name to the valid types (primitive &
                    // structure and structure only) and combo box lists
                    validStructureDataTypes.add(prototypeStructure);
                    validDataTypes.add(prototypeStructure);
                    comboBox.addItem(prototypeStructure);
                }
            }
        }
    }

    /**********************************************************************************************
     * Associate the structure table's data type column (the first column with the primitive and
     * structure input type) with any enumeration columns
     *********************************************************************************************/
    private void setStructureDataTypeEnumAssociations()
    {
        // Step through each enumeration column
        for (int enumIndex : enumerationIndex)
        {
            // Add the data type and enumeration column index pair to the list
            associatedColumns.add(new AssociatedColumns(dataTypeIndex, enumIndex));
        }
    }

    /**********************************************************************************************
     * Associate the command table's command argument columns
     *********************************************************************************************/
    private void setCommandArgumentAssociations()
    {
        associatedColumns.addAll(typeDefn.getAssociatedCommandArgumentColumns(false));
    }

    /**********************************************************************************************
     * Set up the minimum/maximum value groups and associate these columns with the data type
     * column. Only those minimum and maximum columns not already associated with a command
     * argument group are included in these pairings
     *********************************************************************************************/
    private void setUpMinMaxColumns()
    {
        // Create a list to contain minimum/maximum column associations
        minMaxPair = new ArrayList<MinMaxPair>();

        // Initialize the minimum and maximum columns and the index of the data type/enumeration
        // column already paired (if any)
        int minIndex = 0;
        int maxIndex = 0;

        // Step through each column defined for this table's type
        for (int index = 0; index < typeDefn.getColumnCountDatabase(); index++)
        {
            // Initialize the minimum and maximum column indices
            int minColumn = -1;
            int maxColumn = -1;

            // Step through the remaining columns to find the next minimum input type column
            for (; minIndex < table.getModel().getColumnCount(); minIndex++)
            {
                // Check that this is a minimum column
                if (typeDefn.getInputTypes()[minIndex].getInputFormat().equals(InputTypeFormat.MINIMUM))
                {
                    // Save the minimum column index, increment the index for matching up with the
                    // next pairing, and stop searching
                    minColumn = minIndex;
                    minIndex++;
                    break;
                }
            }

            // Step through the remaining columns to find the next maximum input type column
            for (; maxIndex < table.getModel().getColumnCount(); maxIndex++)
            {
                // Check that this is a maximum column
                if (typeDefn.getInputTypes()[maxIndex].getInputFormat().equals(InputTypeFormat.MAXIMUM))
                {
                    // Save the maximum column index, increment the index for matching up with the
                    // next pairing, and stop searching
                    maxColumn = maxIndex;
                    maxIndex++;
                    break;
                }
            }

            // Check if either a minimum or maximum column was found
            if (minColumn != -1 || maxColumn != -1)
            {
                // Step through each command argument column grouping
                for (AssociatedColumns cmdArg : associatedColumns)
                {
                    // Check if the command argument's minimum column matches the current minimum
                    // column index
                    if (cmdArg.getMinimum() == minColumn)
                    {
                        // Reset the minimum column index so that it isn't reused
                        minColumn = -1;
                    }

                    // Check if the command argument's maximum column matches the current maximum
                    // column index
                    if (cmdArg.getMaximum() == maxColumn)
                    {
                        // Reset the maximum column index so that it isn't reused
                        maxColumn = -1;
                    }
                }

                // Check if either a minimum or maximum column exists not already associated with a
                // command argument
                if (minColumn != -1 || maxColumn != -1)
                {
                    // Add the new minimum/maximum column pairing to the list
                    minMaxPair.add(new MinMaxPair(minColumn, maxColumn));
                }
            }
        }
    }

    /**********************************************************************************************
     * Create the cell editor for enumerated data types if it doesn't already exist
     *********************************************************************************************/
    private void createEnumDataTypeCellEditor()
    {
        // Check if the data type cell editor for enumerations hasn't been created
        if (enumDataTypeCellEditor == null)
        {
            // Create a combo box for displaying data types for enumerations (integer and unsigned
            // integers only)
            PaddedComboBox enumComboBox = new PaddedComboBox(table.getFont());

            // Step through each primitive data type
            for (String[] dataType : dataTypeHandler.getDataTypeData())
            {
                // Check if the enumeration combo box exists and that this type is an integer or
                // unsigned integer
                if (enumComboBox != null
                    && dataTypeHandler.isInteger(CcddDataTypeHandler.getDataTypeName(dataType)))
                {
                    // Add the data type to the combo box list
                    enumComboBox.addItem(CcddDataTypeHandler.getDataTypeName(dataType));
                }
            }

            // Enable item matching for the combo box
            enumComboBox.enableItemMatching(table);

            // Create the data type cell editor for enumerations
            enumDataTypeCellEditor = new DefaultCellEditor(enumComboBox);
        }
    }

    /**********************************************************************************************
     * Validate changes to an associated pair of minimum and maximum value columns
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
     *            index of the data type column that governs the minimum and maximum values, model
     *            coordinates
     *
     * @param minColumn
     *            index of the minimum value column, model coordinates
     *
     * @param maxColumn
     *            index of the maximum value column, model coordinates
     *
     * @throws CCDDException
     *             If a value doesn't match the expected input type, a value is outside the
     *             possible range of the data type, or the maximum value is less than the minimum
     *             value
     *********************************************************************************************/
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
                if (!newValueS.matches(DefaultInputType.INT_NON_NEGATIVE.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; input type '"
                                            + DefaultInputType.INT_NON_NEGATIVE.getInputName().toLowerCase()
                                            + "' expected");
                }

                // Convert the cell value to an integer
                long value = Long.valueOf(newValueS);

                // Check if the value is outside the possible bounds for an unsigned integer of
                // this data type's size
                if (value < (long) newDataTypeHandler.getMinimum(dataType)
                    || value > (long) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; must be greater than </b>"
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + "<b> and less than </b>"
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the minimum is greater than
                // the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Long.valueOf(minVal) > Long.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; the minimum must be less than or equal to the maximum");
                }
            }
            // Check if the data type is a signed integer (an unsigned integer was already
            // accounted for above)
            else if (newDataTypeHandler.isInteger(dataType))
            {
                // Check if the value doesn't match the expected input type
                if (!newValueS.matches(DefaultInputType.INTEGER.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; input type '</b>"
                                            + DefaultInputType.INTEGER.getInputName().toLowerCase()
                                            + "<b>' expected");
                }

                // Convert the cell value to an integer
                long value = Long.valueOf(newValueS);

                // Check if the value is outside the possible bounds for a signed integer of this
                // data type's size
                if (value < (long) newDataTypeHandler.getMinimum(dataType)
                    || value > (long) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; must be greater than </b>"
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + "<b> and less than </b>"
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the minimum is greater than
                // the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Long.valueOf(minVal) > Long.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; the minimum must be less than or equal to the maximum");
                }
            }
            // Check if the data type is a floating point
            else if (newDataTypeHandler.isFloat(dataType))
            {
                // Check if the value doesn't match the expected input type
                if (!newValueS.matches(DefaultInputType.FLOAT.getInputMatch()))
                {
                    throw new CCDDException("Invalid input type in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; input type '</b>"
                                            + DefaultInputType.FLOAT.getInputName().toLowerCase()
                                            + "<b>' expected");
                }

                // Convert the cell value to a floating point
                double value = Double.valueOf(newValueS);

                // Check if the value is outside the possible bounds for a floating point of this
                // data type's size
                if (value < (double) newDataTypeHandler.getMinimum(dataType)
                    || value > (double) newDataTypeHandler.getMaximum(dataType))
                {
                    throw new CCDDException("Input value out of range in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; must be greater than </b>"
                                            + newDataTypeHandler.getMinimum(dataType)
                                            + "<b> and less than </b>"
                                            + newDataTypeHandler.getMaximum(dataType));
                }

                // Check if a minimum and maximum value exist and if the minimum is greater than
                // the maximum
                if (!minVal.isEmpty()
                    && !maxVal.isEmpty()
                    && Double.valueOf(minVal) > Double.valueOf(maxVal))
                {
                    throw new CCDDException("Invalid input value in table '</b>"
                                            + currentTableInfo.getTablePath()
                                            + "<b>' for column '</b>"
                                            + typeDefn.getColumnNamesUser()[column]
                                            + "<b>'; the minimum must be less than or equal to the maximum");
                }
            }
        }
    }

    /**********************************************************************************************
     * Store the variable name, array size, and bit number column indices if the table contains
     * these columns, and create a row filter to show/hide the array member rows if an array size
     * column exists
     *********************************************************************************************/
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
                /**********************************************************************************
                 * Determine if the row should be displayed
                 *********************************************************************************/
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry)
                {
                    // Hide the row if it represents an array member and the display flag is false;
                    // otherwise display the row
                    return !(!isShowArrayMembers
                             && ArrayVariable.isArrayMember(entry.getValue(variableNameIndex)));
                }
            };
        }
    }

    /**********************************************************************************************
     * Update the array size, variable name, or data type, and add or remove array members if
     * needed
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
     *********************************************************************************************/
    protected void adjustArrayMember(List<Object[]> tableData,
                                     int[] arraySizeOld,
                                     int[] arraySizeNew,
                                     int definitionRow,
                                     int columnChanged)
    {
        // Initialize the total original and updated array size values to 1 so that the size
        // calculations below can be made
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
        if (arraySizeOld.length == 0)
        {
            // Set the total to zero
            totalSizeOld = 0;
        }

        // Check if the updated array size had no array index value(s)
        if (arraySizeNew.length == 0)
        {
            // Set the total to zero
            totalSizeNew = 0;
        }

        // Calculate the change in the number of array members and the initial row model to act on
        int deltaRows = totalSizeNew - totalSizeOld;
        int lastRow = definitionRow + totalSizeOld;

        // Create storage for a copy of the original array values. This copy is used to restore
        // array member column values
        List<Object[]> arrayData = new ArrayList<Object[]>();

        // Step through each row currently in the array
        for (int row = definitionRow + 1; row <= lastRow; row++)
        {
            // Copy the row
            arrayData.add(Arrays.copyOf(tableData.get(row), tableData.get(row).length));
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

        // Copy the array definition row and blank the primary key and row indices to prevent
        // duplicate values for these columns
        Object[] definitionData = Arrays.copyOf(tableData.get(definitionRow),
                                                tableData.get(definitionRow).length);
        definitionData[primaryKeyIndex] = "";
        definitionData[rowIndex] = "";

        // Step through each array member
        for (int tableRow = definitionRow + 1; tableRow <= lastRow; tableRow++)
        {
            // Copy the array definition to the array member row
            tableData.set(tableRow, Arrays.copyOf(definitionData, definitionData.length));

            // Append the array index value(s) to the variable name for display as the variable
            // name in the array member row
            tableData.get(tableRow)[variableNameIndex] += ArrayVariable.formatArrayIndex(counter);

            // Check if the number of array dimensions is unchanged. For this case the existing
            // values for the array members are restored for those array members that still exist
            if (arraySizeOld.length == arraySizeNew.length)
            {
                // Step through each row in the original array
                for (int arrayRow = 0; arrayRow < arrayData.size(); arrayRow++)
                {
                    // Check if the new array variable member name matches the original array
                    // variable member name
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
                            // Check that this isn't a column that should be skipped
                            if (column != columnChanged
                                && column != variableNameIndex
                                && column != dataTypeIndex
                                && column != arraySizeIndex
                                && column != variablePathIndex)
                            {
                                // Copy the column value from the original row to the updated row
                                tableData.get(tableRow)[column] = arrayData.get(arrayRow)[column];
                            }
                        }

                        break;
                    }
                }
            }

            // Step through the array index values, starting with the last and working backward
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

                // Reset this array index back to zero and continue looping to adjust the next
                // array index up the chain
                counter[index] = 0;
            }

            // Check if the variable path, variable name, and data type columns are present
            if (variablePathIndex != -1 && variableNameIndex != -1 && dataTypeIndex != -1)
            {
                // Update the array member variable paths
                tableData.get(tableRow)[variablePathIndex] = getVariablePath(tableData.get(tableRow)[variableNameIndex].toString(),
                                                                             tableData.get(tableRow)[dataTypeIndex].toString(),
                                                                             true);
            }
        }

        // Check if the number of array dimensions is unchanged and that this is a string array
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
                        // Check that this isn't a column that should be skipped
                        if (column != primaryKeyIndex
                            && column != rowIndex
                            && column != variableNameIndex
                            && column != dataTypeIndex
                            && column != arraySizeIndex)
                        {
                            // Propagate the value to every member of the string
                            propagateArrayValues(tableData, row, column);
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Toggle the display of array member rows
     *********************************************************************************************/
    protected void showHideArrayMembers()
    {
        // Toggle the display of arrays as a group or as individual members
        isShowArrayMembers = !isShowArrayMembers;

        // Issue a table change event so the array member rows are shown/hidden
        tableModel.fireTableDataChanged();
        tableModel.fireTableStructureChanged();

        // Check that the table is open in a table editor (versus open for a macro name and/or
        // value change, for example)
        if (editorDialog != null)
        {
            // Update the expand/collapse arrays check box
            editorDialog.updateExpandArrayCheckBox();
        }
    }

    /**********************************************************************************************
     * Set up or update the combo box containing the allowable sample rates for display in the
     * table's "Rate" column cells
     *********************************************************************************************/
    protected void setUpSampleRateColumn()
    {
        // Step through each rate column defined for this table
        for (int index = 0; index < rateIndex.size(); index++)
        {
            // Get the column reference for the rate column
            TableColumn rateColumn = table.getColumnModel().getColumn(table.convertColumnIndexToView(rateIndex.get(index)));

            // Set to true if this is an update to the combo box content update and not the
            // original combo box setup at table creation)
            boolean isUpdate = (DefaultCellEditor) rateColumn.getCellEditor() != null
                               && ((DefaultCellEditor) rateColumn.getCellEditor()).getComponent() instanceof PaddedComboBox;

            // Check if this is a combo box content update
            if (isUpdate)
            {
                // End editing on the rate cell in case a rate cell is selected. This causes the
                // selected cell to be updated with the new rates
                ((DefaultCellEditor) rateColumn.getCellEditor()).stopCellEditing();
            }

            // Create a combo box and set the color and font
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Set the column table editor to the combo box
            rateColumn.setCellEditor(new ComboBoxCellEditor(comboBox));

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

            // Enable item matching for the combo box
            comboBox.enableItemMatching(table);

            // Check if this is a combo box content update
            if (isUpdate)
            {
                // Force the table to redraw in case the rates have been altered such that a rate
                // cell's validity changes and the background color needs to be updated
                table.repaint();
            }
        }
    }

    /**********************************************************************************************
     * Update the rates for all bit-wise variables that are packed together with the bit-wise
     * variable in the table
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param startRow
     *            initial row to begin the check at, in model coordinates
     *
     * @param useRowRate
     *            true if the rate value for this row is used as the rate for the packed variables;
     *            false to use the rate of the first variable in the pack
     *********************************************************************************************/
    private void setAllPackedVariableRates(List<Object[]> tableData, int startRow, boolean useRowRate)
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
                    // Set the rate for the packed variables adjacent to this variable
                    row = setPackedVariableRates(tableData, row, useRowRate);

                    // Set the flag so that all subsequent packing is based on the first variable
                    // in the pack
                    useRowRate = false;
                }
            }
        }
    }

    /**********************************************************************************************
     * Update the rates for any other bit-wise variables that are packed together with the bit-wise
     * variable in the specified row
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param row
     *            table model row index
     *
     * @param useRowRate
     *            true if the rate value for this row is used as the rate for the packed variables;
     *            false to use the rate of the first variable in the pack
     *
     * @return Index of the last packed row. In addition, the global isRateChange flag is set to
     *         true if a rate value is updated, and false otherwise
     *********************************************************************************************/
    private int setPackedVariableRates(List<Object[]> tableData, int row, boolean useRowRate)
    {
        isRateChange = false;

        // Get the first and last row indices of the packed variables
        BitPackRowIndex packIndex = getPackedVariables(tableData, row);

        // Set the row that has the governing rate
        int theRow = useRowRate ? row : packIndex.getFirstIndex();

        // Step through each rate column
        for (int column : rateIndex)
        {
            // Step through the rows for the variables that are packed together
            for (int curRow = packIndex.getFirstIndex(); curRow <= packIndex.getLastIndex(); curRow++)
            {
                // Check that this isn't the target's row
                if (tableData.get(curRow)[column] != tableData.get(theRow)[column])
                {
                    // Set the rate for this variable to match that of the target row's variable
                    // and set the flag indicating a rate value changed
                    tableData.get(curRow)[column] = tableData.get(theRow)[column];
                    isRateChange = true;
                }
            }
        }

        return packIndex.getLastIndex();
    }

    /**********************************************************************************************
     * Determine the first and last row indices for bit-wise variables that are packed together
     * with the bit-wise variable in the specified row
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param row
     *            table model row index
     *
     * @return BitPackRowIndex instance containing the first and last row indices of the packed
     *         variables. The values are the same if no variables are packed with the variable in
     *         the target row
     *********************************************************************************************/
    protected BitPackRowIndex getPackedVariables(List<Object[]> tableData, int row)
    {
        // Get the number of bytes occupied by this variable
        String dataType = tableData.get(row)[dataTypeIndex].toString();
        int dataTypeBitSize = dataTypeHandler.getSizeInBits(dataType);

        int curRow = row - 1;

        // Step backwards through the table while a variable having a bit length and of the same
        // data type is found
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

        // Step forward, packing the bits, in order to determine the variables in the target row's
        // pack
        while (curRow < tableData.size()
               && !getExpandedValueAt(tableData, curRow, bitLengthIndex).isEmpty()
               && dataType.equals(tableData.get(curRow)[dataTypeIndex].toString()))
        {
            // Add the number of bits occupied by this variable to the running count
            int numBits = Integer.valueOf(getExpandedValueAt(tableData, curRow, bitLengthIndex));
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

                // Reset the bit count to the current row's value and store the row index for the
                // first variable in the pack
                bitCount = numBits;
                firstRow = curRow;
            }

            // Check if the target row is reached
            if (curRow == row)
            {
                // Set the flag indicating this pack includes the target variable
                isTargetPack = true;
            }

            curRow++;
        }

        return new BitPackRowIndex(firstRow, curRow - 1);
    }

    /**********************************************************************************************
     * Set up or update the combo box columns containing selection lists
     *********************************************************************************************/
    private void setUpSelectionColumns()
    {
        // Step through each selection column
        for (Integer column : selectionIndex)
        {
            // Get the column's input type
            InputType inputType = typeDefn.getInputTypes()[column];

            // Create a combo box for displaying selection lists
            PaddedComboBox comboBox = new PaddedComboBox(table.getFont());

            // Step through each item in the selection
            for (String item : inputType.getInputItems())
            {
                // Add the selection item to the combo box list
                comboBox.addItem(item);
            }

            // Enable item matching for the combo box
            comboBox.enableItemMatching(table);

            // Get the column reference for the selection column
            TableColumn selectionColumn = table.getColumnModel()
                                               .getColumn(table.convertColumnIndexToView(column));

            // Set the table column editor to the combo box
            selectionColumn.setCellEditor(new ComboBoxCellEditor(comboBox));
        }
    }

    /**********************************************************************************************
     * Propagate the value in the specified column of an array definition row to each member of the
     * array. If the row value is unique for the indicated column then the value isn't propagated.
     * For a string array if an array member is changed then only propagate the value to the other
     * members of that string
     *
     * @param tableData
     *            list containing the table data row arrays
     *
     * @param firstRow
     *            table model row index for the array definition or for the first member of a
     *            string array
     *
     * @param columnChanged
     *            table model index of the column being changed
     *********************************************************************************************/
    private void propagateArrayValues(List<Object[]> tableData, int firstRow, int columnChanged)
    {
        // Check if the value for this column isn't unique (propagating the value to the members
        // creates duplicate values)
        if (!typeDefn.isRowValueUnique()[columnChanged])
        {
            // Get the variable name
            String variableName = getExpandedValueAt(tableData, firstRow, variableNameIndex);

            // Set to true if the updated row is the array's definition. Set to false to indicate
            // that only the members of the string indicated by the specified row are to be updated
            boolean isArrayDefn = !ArrayVariable.isArrayMember(variableName);

            // Check if this is an initial string member or if the flag is set that prevents
            // overwriting array members (and the table is open in an editor)
            if (!isArrayDefn || editorDialog == null || !editorDialog.isArrayOverwriteNone())
            {
                // Get the variable name without the array index. If only one string in an array of
                // strings is having its value changed then include the array index portions other
                // than those that define the string's length
                variableName = isArrayDefn
                                           ? ArrayVariable.removeArrayIndex(variableName)
                                           : ArrayVariable.removeStringSize(variableName);

                int lastRow = firstRow;

                // Step forward in order to determine the ending row for this array/string by
                // comparing the variable name in the current row to that in the first row
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
                    // Check if this is an initial string member or if the flag that allows all
                    // array members to be overwritten is set (and the table is open in an editor)
                    // or if the current row's column is empty
                    if (!isArrayDefn
                        || getExpandedValueAt(tableData, curRow, columnChanged).isEmpty()
                        || editorDialog == null
                        || editorDialog.isArrayOverwriteAll())
                    {
                        // Set the value for this member to match that of the first row
                        tableData.get(curRow)[columnChanged] = tableData.get(firstRow)[columnChanged];
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Update the row indices to match the current row order
     *********************************************************************************************/
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

    /**********************************************************************************************
     * Determine if any changes have been made compared to the most recently committed table data
     *
     * @return true if any cell in the table has been changed, if the column order has changed, or
     *         if the table description has changed
     *********************************************************************************************/
    protected boolean isTableChanged()
    {
        boolean isChanged = false;

        // Check if table editing is disabled. If the editor dialog command to show macros is
        // selected then the table edit flag is false and the macros are displayed
        if (!isEditEnabled)
        {
            // Display the macros in place of the macro values. If the macros aren't restored then
            // the comparison detects every expanded macro as a change; restoring the macros
            // prevents this erroneous change indication
            expandMacros(false, false);
        }

        // Check if the table has changes, ignoring the variable path column (if present; this
        // column must be checked for changes separately due to how the values are maintained)
        isChanged = table.isTableChanged(committedTableInfo.getData(),
                                         Arrays.asList(new Integer[] {variablePathIndex}));

        // Check if a change wasn't detected above and that the variable path, variable name, and
        // data type columns are present
        if (!isChanged
            && variablePathIndex != -1
            && variableNameIndex != -1
            && dataTypeIndex != -1)
        {
            // Step through each row in the table
            for (int row = 0; row < tableModel.getRowCount() && !isChanged; row++)
            {
                // Get the primary key value for this row
                String primaryKey = tableModel.getValueAt(row, primaryKeyIndex).toString();

                // Check if the row has a primary key (indicates it exists in the committed data)
                if (!primaryKey.isEmpty())
                {
                    // Get the variable path as currently displayed in the table
                    String varPath = tableModel.getValueAt(row, variablePathIndex).toString();

                    // Check if the variable path isn't blank (the path is blank if the table row
                    // is an array definition, which doesn't allow a variable path, or an empty
                    // row)
                    if (!varPath.isEmpty())
                    {
                        // Step through each row in the committed data
                        for (int commRow = 0; commRow < committedTableInfo.getData().length; commRow++)
                        {
                            // Check if the primary key in the table matches the one in this row of
                            // the committed data
                            if (primaryKey.equals(committedTableInfo.getData()[commRow][primaryKeyIndex]))
                            {
                                // Set the flag to indicate if the variable path is automatically
                                // set
                                boolean isVarPathAuto = committedTableInfo.getData()[commRow][variablePathIndex].toString().isEmpty();

                                // Check if the variable path was manually set and has been changed
                                // (either to a new name or allowed to be automatically), or the
                                // path wasn't manually set but it is now
                                if ((!isVarPathAuto
                                     && !varPath.equals(committedTableInfo.getData()[commRow][variablePathIndex]))
                                    || (isVarPathAuto
                                        && !varPath.equals(getVariablePath(tableModel.getValueAt(row,
                                                                                                 variableNameIndex)
                                                                                     .toString(),
                                                                           tableModel.getValueAt(row,
                                                                                                 dataTypeIndex)
                                                                                     .toString(),
                                                                           true))))
                                {
                                    // Set the flag to indicate a change exists
                                    isChanged = true;
                                }

                                break;
                            }
                        }
                    }
                }
            }
        }

        // Check if table editing is disabled. If the editor dialog command to show macros is
        // selected then the table edit flag is false and the macros are displayed
        if (!isEditEnabled)
        {
            // Redisplay the macro values in place of the macros
            expandMacros(true, false);
        }

        return isChanged;
    }

    /**********************************************************************************************
     * Compare the current table data to the committed table data and create lists of the changed
     * values necessary to update the table in the database to match the current values
     *********************************************************************************************/
    protected void buildUpdates()
    {
        // Note: The committed table information class is updated during the commit procedure,
        // which can produce a race condition if it's used for change comparisons

        // Check that the table is open in a table editor (versus open for a macro name and/or
        // value change, for example)
        if (editorDialog != null)
        {
            // Store the description into the table information class so that it can be used to
            // compare if a change occurred
            currentTableInfo.setDescription(getDescription());
        }

        // Store the most recently committed table data and column order into the table information
        // class so that it can be used to compare if a change occurred
        currentTableInfo.setData(committedTableInfo.getData());
        currentTableInfo.setColumnOrder(table.getColumnOrder());

        // Remove change information from a previous commit, if any
        additions.clear();
        modifications.clear();
        deletions.clear();

        // Create an empty row of data for comparison purposes
        Object[] emptyRow = table.getEmptyRow();

        // Re-index the rows in case any have been added, moved, or deleted
        updateRowIndices();

        // Get the number of rows that have been committed to the database for this table
        int numCommitted = committedTableInfo != null
                                                      ? committedTableInfo.getData().length
                                                      : 0;

        // Get the table cell values
        Object[][] tableData = table.getTableData(true);

        // Create storage for the array used to indicate if a row has been modified
        boolean[] rowModified = new boolean[committedTableInfo.getData().length];

        // Step through each row in the table
        for (int tblRow = 0; tblRow < tableData.length; tblRow++)
        {
            boolean matchFound = false;

            // Check if the table is open in a table editor, the variable path, variable name, and
            // data type columns are present, and the path isn't manually set
            if (editorDialog != null
                && variablePathIndex != -1
                && variableNameIndex != -1
                && dataTypeIndex != -1
                && tableData[tblRow][variablePathIndex].toString().equals(getVariablePath(tableData[tblRow][variableNameIndex].toString(),
                                                                                          tableData[tblRow][dataTypeIndex].toString(),
                                                                                          true)))
            {
                // Blank the data for this column so that it isn't stored in the database (paths
                // not manually entered by the user are constructed on-the-fly for display in the
                // table)
                tableData[tblRow][variablePathIndex] = "";
            }

            // Step through each row in the committed version of the table data
            for (int comRow = 0; comRow < numCommitted && !matchFound; comRow++)
            {
                // Check if the primary key values match for these rows
                if (tableData[tblRow][primaryKeyIndex].equals(committedTableInfo.getData()[comRow][primaryKeyIndex]))
                {
                    // Set the flag indicating this row has a match
                    matchFound = true;

                    // Copy the current row's primary key and row index into the empty comparison
                    // row
                    emptyRow[primaryKeyIndex] = tableData[tblRow][primaryKeyIndex];
                    emptyRow[rowIndex] = tableData[tblRow][rowIndex];

                    // Check if the row is not now empty (if empty then the change is processed as
                    // a row deletion instead of a modification)
                    if (!Arrays.equals(tableData[tblRow], emptyRow))
                    {
                        // Set the flag indicating this row has a modification
                        rowModified[comRow] = true;

                        // Step through each column in the row
                        for (int column = 0; column < tableData[tblRow].length; column++)
                        {
                            // Check if the current and committed values don't match
                            if (!tableData[tblRow][column].equals(committedTableInfo.getData()[comRow][column]))
                            {
                                // Store the row modification information and stop searching
                                modifications.add(new TableModification(tableData[tblRow],
                                                                        committedTableInfo.getData()[comRow],
                                                                        variableNameIndex,
                                                                        dataTypeIndex,
                                                                        arraySizeIndex,
                                                                        bitLengthIndex,
                                                                        rateIndex));
                                break;
                            }
                        }
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
            if (!rowModified[comRow])
            {
                // Store the row deletion information
                deletions.add(new TableModification(committedTableInfo.getData()[comRow],
                                                    variableNameIndex,
                                                    dataTypeIndex,
                                                    arraySizeIndex,
                                                    bitLengthIndex));
            }
        }
    }

    /**********************************************************************************************
     * Check that a row with contains data in the required columns
     *
     * @return true if a row is missing data in a required column
     *********************************************************************************************/
    protected boolean checkForMissingColumns()
    {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Check if the table represents a structure
        if (typeDefn.isStructure())
        {
            // Step through each row in the table
            for (int row = 0; row < tableModel.getRowCount() && !stopCheck; row++)
            {
                // Skip rows in the table that are empty
                row = table.getNextPopulatedRowNumber(row);

                // Check that the end of the table hasn't been reached and if a required column is
                // empty
                if (row < tableModel.getRowCount())
                {
                    // Step through the columns required for a structure variable
                    for (int column : new int[] {variableNameIndex, dataTypeIndex})
                    {
                        // Check if the cell is empty
                        if (tableModel.getValueAt(row, column).toString().isEmpty())
                        {
                            // Set the 'data is missing' flag
                            dataIsMissing = true;

                            // Inform the user that a row is missing required data. If Cancel is
                            // selected then do not perform checks on other columns and rows
                            if (new CcddDialogHandler().showMessageDialog(parent,
                                                                          "<html><b>Data must be provided for column '</b>"
                                                                                  + tableModel.getColumnName(column)
                                                                                  + "<b>' [row </b>"
                                                                                  + (row + 1)
                                                                                  + "<b>]",
                                                                          "Missing Data",
                                                                          JOptionPane.WARNING_MESSAGE,
                                                                          DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
                            {
                                // Set the stop flag to prevent further error checking
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

    /**********************************************************************************************
     * Reset the order of the columns to the default, as specified by the table's type definition
     *********************************************************************************************/
    protected void resetColumnOrder()
    {
        // Set the column order to the default
        table.arrangeColumns(tableTypeHandler.getDefaultColumnOrder(typeDefn.getName()));
    }

    /**********************************************************************************************
     * Update the tab for this table in the table editor dialog change indicator
     *********************************************************************************************/
    @Override
    protected void updateOwnerChangeIndicator()
    {
        editorDialog.updateChangeIndicator(this);
    }
}
