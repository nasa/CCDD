/**
 * CFS Command & Data Dictionary input type editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
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
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputTypeEditorColumnInfo;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command & Data Dictionary input type editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddInputTypeEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddInputTypeHandler inputTypeHandler;
    private CcddJTableHandler inputTypeTable;

    // Cell editors for the input type format and items columns
    private DefaultCellEditor formatCellEditor;

    // Table instance model data. Current copy is the table information as it exists in the table
    // editor and is used to determine what changes have been made to the table since the previous
    // field editor update
    private Object[][] committedData;

    // List of input type references already loaded from the database. This is used to avoid
    // repeated searches for a the same input type
    private List<InputTypeReference> loadedReferences;

    // Dialog title
    private static final String DIALOG_TITLE = "Input Type Editor";

    /**********************************************************************************************
     * Input type data table references class
     *********************************************************************************************/
    private class InputTypeReference
    {
        private final String inputTypeName;
        private final String[] references;

        /******************************************************************************************
         * Input type data table references class constructor
         *
         * @param inputTypeName
         *            input type name
         *****************************************************************************************/
        InputTypeReference(String inputTypeName)
        {
            this.inputTypeName = inputTypeName;

            // Get the references to the specified input type in the table type an data field
            // tables
            references = inputTypeHandler.getInputTypeReferences(inputTypeName,
                                                                 CcddInputTypeEditorDialog.this);
        }

        /******************************************************************************************
         * Get the input type name associated with the references
         *
         * @return Input type name
         *****************************************************************************************/
        protected String getInputTypeName()
        {
            return inputTypeName;
        }

        /******************************************************************************************
         * Get the references in the data tables for this input type
         *
         * @return References in the data tables for this input type
         *****************************************************************************************/
        protected String[] getReferences()
        {
            return references;
        }
    }

    /**********************************************************************************************
     * Input type editor dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddInputTypeEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

        // Create the input type editor dialog
        initialize();
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of input type updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doInputTypeUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Update the input type handler with the changes
            inputTypeHandler.setInputTypeData(getUpdatedData());

            // Update the input type columns in the open table editors
            dbTable.updateInputTypeColumns(CcddInputTypeEditorDialog.this);

            // Update the copy of the input type data so it can be used to determine if changes are
            // made
            committedData = inputTypeTable.getTableData(true);

            // Accept all edits for this table
            inputTypeTable.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Create the input type editor dialog. This is executed in a separate thread since it can take
     * a noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the input type editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the input type editor dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                loadedReferences = new ArrayList<InputTypeReference>();

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

                // Create a copy of the input type data so it can be used to determine if changes
                // are made
                committedData = inputTypeHandler.getCustomInputTypeData();

                // Define the panel to contain the table and place it in the editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createInputTypeTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());

                // Create the cell editor for input type formats
                createInputTypeFormatCellEditor();

                // Set the modal undo manager and table references in the keyboard handler while
                // the input type editor is active
                ccddMain.getKeyboardHandler().setModalDialogReference(inputTypeTable.getUndoManager(),
                                                                      inputTypeTable);

                // New button
                JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                           INSERT_ICON,
                                                                           KeyEvent.VK_I,
                                                                           "Insert a new row into the table");

                // Create a listener for the Insert Row button
                btnInsertRow.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Insert a new row into the table at the selected location
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        inputTypeTable.insertEmptyRow(true);
                    }
                });

                // Delete button
                JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                           DELETE_ICON,
                                                                           KeyEvent.VK_D,
                                                                           "Delete the selected row(s) from the table");

                // Create a listener for the Delete row button
                btnDeleteRow.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Delete the selected row(s) from the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Step through each row selected for deletion
                        for (int row : inputTypeTable.getSelectedRows())
                        {
                            // Get the input type name
                            String inputTypeName = inputTypeTable.getValueAt(row,
                                                                             InputTypesColumn.NAME.ordinal())
                                                                 .toString();

                            // Check if the input type name is present and the input type is in use
                            // by a table type or data field
                            if (!inputTypeName.isEmpty()
                                && getInputTypeReferences(inputTypeName).getReferences().length != 0)
                            {
                                String inputTypeUsers = "";
                                List<String> tableTypes = new ArrayList<String>();
                                List<String> fields = new ArrayList<String>();

                                // Step through each reference to the input type name
                                for (String inputTypeRef : getInputTypeReferences(inputTypeName).getReferences())
                                {
                                    // Split the reference into table name, column name, comment,
                                    // and context
                                    String[] tblColCmtAndCntxt = inputTypeRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);

                                    // Extract the context from the reference
                                    String[] refColumns = CcddUtilities.splitAndRemoveQuotes(tblColCmtAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                                    // Check if the context is in a table type definition
                                    if (tblColCmtAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()].equals(InternalTable.TABLE_TYPES.getTableName()))
                                    {
                                        // Check if the table type name hasn't already been added
                                        // to the list
                                        if (!tableTypes.contains(refColumns[TableTypesColumn.TYPE_NAME.ordinal()]))
                                        {
                                            // Add the table type name to the list of those using
                                            // the input type
                                            tableTypes.add(refColumns[TableTypesColumn.TYPE_NAME.ordinal()]);
                                        }
                                    }
                                    // Check if the context is in a data field definition
                                    else if (tblColCmtAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()].equals(InternalTable.FIELDS.getTableName()))
                                    {
                                        // Check if the data field owner hasn't already been added
                                        // to the list
                                        if (!fields.contains(refColumns[FieldsColumn.OWNER_NAME.ordinal()]))
                                        {
                                            // Add the data field owner to the list of those using
                                            // the input type
                                            fields.add(refColumns[FieldsColumn.OWNER_NAME.ordinal()]);
                                        }
                                    }
                                }

                                // Check if any table types reference the input type
                                if (!tableTypes.isEmpty())
                                {
                                    // Add the table types to the user text
                                    inputTypeUsers = "table type(s) '</b>"
                                                     + CcddUtilities.convertArrayToStringTruncate(tableTypes.toArray(new String[0]))
                                                     + "<b>'";
                                }

                                // Check if any data fields reference the input type
                                if (!fields.isEmpty())
                                {
                                    // Check if any table types reference the input type
                                    if (!inputTypeUsers.isEmpty())
                                    {
                                        inputTypeUsers += " and ";
                                    }

                                    // Add the data field owners to the user text
                                    inputTypeUsers += "data fields owner(s) '</b>"
                                                      + CcddUtilities.convertArrayToStringTruncate(fields.toArray(new String[0]))
                                                      + "<b>'";
                                }

                                // Deselect the input type
                                inputTypeTable.removeRowSelectionInterval(row, row);

                                // Inform the user that the input type can't be deleted
                                new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
                                                                          "<html><b>Cannot delete input type '"
                                                                                                          + inputTypeName
                                                                                                          + "'; input type is referenced by "
                                                                                                          + inputTypeUsers,
                                                                          "Delete Input Type",
                                                                          JOptionPane.ERROR_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                            }
                        }

                        // Delete all row(s) (still) selected
                        inputTypeTable.deleteRow(true);
                    }
                });

                // Move Up button
                JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                        UP_ICON,
                                                                        KeyEvent.VK_U,
                                                                        "Move the selected row(s) up");

                // Create a listener for the Move Up button
                btnMoveUp.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) up in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        inputTypeTable.moveRowUp();
                    }
                });

                // Move Down button
                JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                          DOWN_ICON,
                                                                          KeyEvent.VK_W,
                                                                          "Move the selected row(s) down");

                // Create a listener for the Move Down button
                btnMoveDown.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) down in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        inputTypeTable.moveRowDown();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit");

                // Create a listener for the Undo button
                btnUndo.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Undo the last cell edit
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        inputTypeTable.getUndoManager().undo();
                    }
                });

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit");

                // Create a listener for the Redo button
                btnRedo.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Redo the last cell edit that was undone
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        inputTypeTable.getUndoManager().redo();
                    }
                });

                // Store the input types button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store the input type(s)");

                // Create a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Store the input types
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's content has changed,
                        // none of the required columns is missing a value, and the user confirms
                        // the action
                        if (inputTypeTable.isTableChanged(committedData)
                            && !checkForMissingColumns()
                            && new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // TODO Probably should do a buildUpdates() method, using the OID, to
                            // change the existing input type references if one is renamed

                            // Store the updated input types table
                            dbTable.storeInformationTableInBackground(InternalTable.INPUT_TYPES,
                                                                      CcddUtilities.removeArrayListColumn(Arrays.asList(getUpdatedData()),
                                                                                                          InputTypesColumn.OID.ordinal()),
                                                                      null,
                                                                      CcddInputTypeEditorDialog.this);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the input type editor");

                // Create a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener(inputTypeTable)
                {
                    /******************************************************************************
                     * Close the input type editor dialog
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        windowCloseButtonAction();
                    }
                });

                // Add buttons in the order in which they'll appear (left to right, top to bottom)
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

            /**************************************************************************************
             * Input type editor dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the input type editor dialog
                showOptionsDialog(ccddMain.getMainFrame(),
                                  editorPnl,
                                  buttonPnl,
                                  btnClose,
                                  DIALOG_TITLE,
                                  true);
            }
        });
    }

    /**********************************************************************************************
     * Get the references to the specified input type in the prototype tables
     *
     * @param inputTypeName
     *            input type name
     *
     * @return Reference to the specified input type in the prototype tables
     *********************************************************************************************/
    private InputTypeReference getInputTypeReferences(String inputTypeName)
    {
        InputTypeReference inputTypeRefs = null;

        // Step through the list of the input type search references already loaded
        for (InputTypeReference loadedRef : loadedReferences)
        {
            // Check if the input type name matches that for an already searched input type
            if (inputTypeName.equals(loadedRef.getInputTypeName()))
            {
                // Store the input type search reference and stop searching
                inputTypeRefs = loadedRef;
                break;
            }
        }

        // Check if the input type references haven't already been loaded
        if (inputTypeRefs == null)
        {
            // Search for references to this input type
            inputTypeRefs = new InputTypeReference(inputTypeName);

            // Add the search results to the list so that this search doesn't get performed again
            loadedReferences.add(inputTypeRefs);
        }

        return inputTypeRefs;
    }

    /**********************************************************************************************
     * Create the input type table
     *
     * @return Reference to the scroll pane in which the table is placed
     *********************************************************************************************/
    private JScrollPane createInputTypeTable()
    {
        // Define the input type editor JTable
        inputTypeTable = new CcddJTableHandler(DefaultPrimitiveTypeInfo.values().length)
        {
            /**************************************************************************************
             * Allow multiple line display in all but the format column
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column != InputTypeEditorColumnInfo.FORMAT.ordinal();
            }

            /**************************************************************************************
             * Hide the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == InputTypeEditorColumnInfo.OID.ordinal();
            }

            /**************************************************************************************
             * Override isCellEditable so that all columns can be edited
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return true;
            }

            /**************************************************************************************
             * Allow pasting data into the input type cells
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                return isCellEditable(convertRowIndexToView(row),
                                      convertColumnIndexToView(column));
            }

            /**************************************************************************************
             * Override getCellEditor so that for a input type format column cell the input type
             * format combo box cell editor is returned; for all other cells return the normal cell
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
                int modelColumn = convertColumnIndexToModel(column);

                // Check if the column for which the cell editor is requested is the base input
                // type column
                if (modelColumn == InputTypeEditorColumnInfo.FORMAT.ordinal())
                {
                    // Select the combo box cell editor that displays the input type formats
                    cellEditor = formatCellEditor;
                }

                return cellEditor;
            }

            /**************************************************************************************
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
             *            true to display the invalid input dialog, if applicable
             *
             * @param isMultiple
             *            true if this is one of multiple cells to be entered and checked; false if
             *            only a single input is being entered
             *
             * @return Always returns false
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

                // Create a string version of the new values
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the input type name has been changed
                        if (column == InputTypeEditorColumnInfo.NAME.ordinal())
                        {
                            // Compare this input type name to the others in the table in order to
                            // avoid creating a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being edited, and if the input
                                // type name matches the one being added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(tableData.get(otherRow)[InputTypeEditorColumnInfo.NAME.ordinal()].toString()))
                                {
                                    throw new CCDDException("Input type name already in use");
                                }
                            }

                            // Step through each default input type
                            for (DefaultInputType defaultType : DefaultInputType.values())
                            {
                                // Check if the default input type name matches the one being added
                                // (case insensitive)
                                if (newValueS.equalsIgnoreCase(defaultType.getInputName()))
                                {
                                    throw new CCDDException("Input type name already in use");
                                }
                            }
                        }
                    }
                }
                catch (CCDDException ce)
                {
                    // Set the flag that indicates the last edited cell's content is invalid
                    setLastCellValid(false);

                    // Check if the input error dialog should be displayed
                    if (showMessage)
                    {
                        // Inform the user that the input value is invalid
                        new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
                                                                  "<html><b>"
                                                                                                  + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    inputTypeTable.getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /**************************************************************************************
             * Load the table input types into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(committedData,
                                            InputTypeEditorColumnInfo.getColumnNames(),
                                            null,
                                            InputTypeEditorColumnInfo.getToolTips(),
                                            true,
                                            true,
                                            true);
            }

            /**************************************************************************************
             * Override prepareRenderer to allow adjusting the background colors of table cells
             *************************************************************************************/
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
            {
                JComponent comp = (JComponent) super.prepareRenderer(renderer, row, column);

                // Check if the cell isn't already selected (selection highlighting overrides the
                // invalid highlighting, if applicable)
                if (!(isFocusOwner()
                      && isRowSelected(row)
                      && (isColumnSelected(column) || !getColumnSelectionAllowed())))
                {
                    boolean found = true;

                    // Convert the column to model coordinates
                    int modelColumn = inputTypeTable.convertColumnIndexToModel(column);

                    // Check if input type name is blank
                    if ((modelColumn == InputTypeEditorColumnInfo.NAME.ordinal())
                        && inputTypeTable.getValueAt(row,
                                                     InputTypeEditorColumnInfo.NAME.ordinal())
                                         .toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is invalid
                        found = false;
                    }
                    // Check if the cell is required and is empty
                    else if (InputTypeEditorColumnInfo.values()[modelColumn].isRequired()
                             && inputTypeTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is invalid
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

            /**************************************************************************************
             * Override the CcddJTableHandler method to produce an array containing empty values
             * for a new row in this table
             *
             * @return Array containing blank cell values for a new row
             *************************************************************************************/
            @Override
            protected Object[] getEmptyRow()
            {
                return InputTypeEditorColumnInfo.getEmptyRow();
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether any unstored changes exist
                setTitle(DIALOG_TITLE
                         + (inputTypeTable.isTableChanged(committedData)
                                                                         ? "*"
                                                                         : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(inputTypeTable);

        // Disable storage of edit operations during creation of the table
        inputTypeTable.getUndoHandler().setAllowUndo(false);

        // Set common table parameters and characteristics
        inputTypeTable.setFixedCharacteristics(scrollPane,
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
        inputTypeTable.getUndoHandler().setAllowUndo(true);

        return scrollPane;
    }

    /**********************************************************************************************
     * Handle the dialog close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the editor table is validated and that
        // there are changes that haven't been stored. If changes exist then confirm discarding the
        // changes
        if (inputTypeTable.isLastCellValid()
            && (!inputTypeTable.isTableChanged(committedData)
                || new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
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

    /**********************************************************************************************
     * Create the cell editor for input type formats
     *********************************************************************************************/
    private void createInputTypeFormatCellEditor()
    {
        // Create a combo box for displaying the input type formats
        final PaddedComboBox formatComboBox = new PaddedComboBox(inputTypeTable.getFont());

        // Step through each input type format
        for (InputTypeFormat type : InputTypeFormat.values())
        {
            // Add the input type format to the list
            formatComboBox.addItem(type.toString().toLowerCase());
        }

        // Add a listener to the combo box for focus changes
        formatComboBox.addFocusListener(new FocusAdapter()
        {
            /**************************************************************************************
             * Handle a focus gained event so that the combo box automatically expands when
             * selected
             *************************************************************************************/
            @Override
            public void focusGained(FocusEvent fe)
            {
                formatComboBox.showPopup();
            }
        });

        // Create the input type format cell editor
        formatCellEditor = new DefaultCellEditor(formatComboBox);
    }

    /**********************************************************************************************
     * Get the updated input type data
     *
     * @return Array containing the updated input type data
     *********************************************************************************************/
    private String[][] getUpdatedData()
    {
        return CcddUtilities.convertObjectToString(inputTypeTable.getTableData(true));
    }

    /**********************************************************************************************
     * Check that a row with contains data in the required columns
     *
     * @return true if a row is missing data in a required column
     *********************************************************************************************/
    private boolean checkForMissingColumns()
    {
        boolean dataIsMissing = false;
        boolean stopCheck = false;

        // Step through each row in the table
        for (int row = 0; row < inputTypeTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = inputTypeTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < inputTypeTable.getRowCount())
            {
                // Check if both the input type name is blank
                if (inputTypeTable.getValueAt(row,
                                              InputTypeEditorColumnInfo.NAME.ordinal())
                                  .toString().isEmpty())
                {
                    // Set the 'data is missing' flag
                    dataIsMissing = true;

                    // Inform the user that a row is missing required data. If Cancel is selected
                    // then do not perform checks on other columns and rows
                    if (new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
                                                                  "<html><b>Data must be provided for column '"
                                                                                                  + inputTypeTable.getColumnName(InputTypeEditorColumnInfo.NAME.ordinal())
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
                for (int column = 0; column < inputTypeTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (InputTypeEditorColumnInfo.values()[column].isRequired()
                        && inputTypeTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddInputTypeEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '"
                                                                                                      + inputTypeTable.getColumnName(column)
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

                        break;
                    }
                }
            }
        }

        return dataIsMissing;
    }
}
