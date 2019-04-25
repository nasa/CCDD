/**
 * CFS Command and Data Dictionary macro editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.table.TableCellRenderer;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.MacroEditorColumnInfo;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddUndoHandler.UndoableTableModel;

/**************************************************************************************************
 * CFS Command and Data Dictionary macro editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddMacroEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddMacroHandler macroHandler;
    private CcddJTableHandler macroTable;

    // Table instance model data. Current copy is the table information as it exists in the table
    // editor and is used to determine what changes have been made to the table since the previous
    // field editor update
    private String[][] committedData;

    // List of macro table content changes to process
    private List<TableModification> modifications;

    // Temporary table cell storage for when macro names are replaced by their corresponding values
    // so that the original cell contents can be restored
    private String[][] originalCellData;

    // Temporary OID
    private int tempOID;

    // Dialog title
    private static final String DIALOG_TITLE = "Macro Editor";

    /**********************************************************************************************
     * Macro editor dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddMacroEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        macroHandler = ccddMain.getMacroHandler();

        // Create the macro editor dialog
        initialize();
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of macro updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doMacroUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Assign temporary OIDs to the added rows so that these can be matched when building
            // updates
            tempOID = macroTable.assignOIDsToNewRows(tempOID, MacrosColumn.OID.ordinal());

            // Update the macro handler with the changes
            macroHandler.setMacroData(getUpdatedData());

            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Step through each individual editor
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Force the table to redraw to update the macro highlighting
                    editor.getTable().repaint();
                }
            }

            // Update the copy of the macro data so it can be used to determine if changes are made
            storeCurrentData();

            // Initialize the list of macro references already loaded
            macroHandler.initializeReferences();

            // Accept all edits for this table
            macroTable.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Copy the macro data so it can be used to determine if changes are made
     *********************************************************************************************/
    private void storeCurrentData()
    {
        // Check if the table has fields
        if (!macroHandler.getMacroData().isEmpty())
        {
            // Store the macro information
            committedData = macroHandler.getMacroData().toArray(new String[0][0]);
        }
        // The table has no macros
        else
        {
            // Initialize the fields
            committedData = new String[0][0];
        }
    }

    /**********************************************************************************************
     * Create the macro editor dialog. This is executed in a separate thread since it can take a
     * noticeable amount time to complete, and by using a separate thread the GUI is allowed to
     * continue to update. The GUI menu commands, however, are disabled until the telemetry
     * scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the macro editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the macro editor dialog
             *************************************************************************************/
            @Override
            protected void execute()
            {
                modifications = new ArrayList<TableModification>();

                // Initialize the temporary OID
                tempOID = -1;

                // Initialize the list of macro references already loaded
                macroHandler.initializeReferences();

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

                // Create a copy of the macro data so it can be used to determine if changes are
                // made
                storeCurrentData();

                // Define the panel to contain the table and place it in the editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createMacroTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());

                // Set the modal undo manager and table references in the keyboard handler while
                // the macro editor is active
                ccddMain.getKeyboardHandler().setModalDialogReference(macroTable.getUndoManager(),
                                                                      macroTable);

                // New button
                JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                           INSERT_ICON,
                                                                           KeyEvent.VK_I,
                                                                           "Insert a new row into the table");

                // Create a listener for the Insert Row button
                btnInsertRow.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Insert a new row into the table at the selected location
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        macroTable.insertEmptyRow(true);
                    }
                });

                // Delete button
                JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                           DELETE_ICON,
                                                                           KeyEvent.VK_D,
                                                                           "Delete the selected row(s) from the table");

                // Create a listener for the Delete row button
                btnDeleteRow.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Delete the selected row(s) from the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Step through each row selected for deletion
                        for (int row : macroTable.getSelectedRows())
                        {
                            // Get the macro name
                            String macroName = macroTable.getValueAt(row,
                                                                     MacrosColumn.MACRO_NAME.ordinal())
                                                         .toString();

                            // Check if the macro is referenced in any of the data tables
                            if (!macroName.isEmpty())
                            {
                                // Get the list of tables using the macro. The list is empty if the
                                // macro isn't referenced by any table
                                List<String> tablePaths = macroHandler.getMacroUsage(macroName,
                                                                                     CcddMacroEditorDialog.this);

                                // Check if the macro is in use by a data table
                                if (!tablePaths.isEmpty())
                                {
                                    // Deselect the macro
                                    macroTable.removeRowSelectionInterval(row, row);

                                    // Inform the user that the macro can't be deleted
                                    new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                              "<html><b>Cannot delete macro '</b>"
                                                                                                          + macroName
                                                                                                          + "<b>'; macro is referenced by table(s) '</b>"
                                                                                                          + CcddUtilities.convertArrayToStringTruncate(tablePaths.toArray(new String[0]))
                                                                                                          + "<b>'",
                                                                              "Delete Macro",
                                                                              JOptionPane.ERROR_MESSAGE,
                                                                              DialogOption.OK_OPTION);
                                }
                            }
                        }

                        // Delete all row(s) (still) selected
                        macroTable.deleteRow(true);
                    }
                });

                // Move Up button
                JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                        UP_ICON,
                                                                        KeyEvent.VK_U,
                                                                        "Move the selected row(s) up");

                // Create a listener for the Move Up button
                btnMoveUp.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) up in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        macroTable.moveRowUp();
                    }
                });

                // Move Down button
                JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                          DOWN_ICON,
                                                                          KeyEvent.VK_W,
                                                                          "Move the selected row(s) down");

                // Create a listener for the Move Down button
                btnMoveDown.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) down in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        macroTable.moveRowDown();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit");

                // Create a listener for the Undo button
                btnUndo.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Undo the last cell edit
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        macroTable.getUndoManager().undo();
                    }
                });

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit");

                // Create a listener for the Redo button
                btnRedo.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Redo the last cell edit that was undone
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        macroTable.getUndoManager().redo();
                    }
                });

                // Store the macros button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store the macro(s)");
                btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Create a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Store the macros
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's content has changed,
                        // none of the required columns is missing a value, and the user confirms
                        // the action
                        if (macroTable.isTableChanged(committedData)
                            && !checkForMissingColumns()
                            && new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Get a list of the macro modifications
                            buildUpdates();

                            // Update the tables affected by the changes to the macro(s)
                            dbTable.modifyTablesPerDataTypeOrMacroChangesInBackground(modifications,
                                                                                      getUpdatedData(),
                                                                                      CcddMacroEditorDialog.this);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the macro editor");

                // Create a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /******************************************************************************
                     * Close the macro editor dialog
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
             * Macro editor dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the macro editor dialog
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
     * Create the macro table
     *
     * @return Reference to the scroll pane in which the table is placed
     *********************************************************************************************/
    private JScrollPane createMacroTable()
    {
        // Define the macro editor JTable
        macroTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Highlight any macros in the macro values column
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
                // Check if this is the macro values column
                if (column == MacroEditorColumnInfo.VALUE.ordinal())
                {
                    // Highlight any macro names in the table cell. Adjust the highlight color to
                    // account for the cell selection highlighting so that the macro is easily
                    // readable
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
                }
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
             * Allow multiple line display in all columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
            }

            /**************************************************************************************
             * Hide the the specified columns
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == MacroEditorColumnInfo.OID.ordinal();
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
             * Allow pasting data into the macro cells
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                return isCellEditable(convertRowIndexToView(row),
                                      convertColumnIndexToView(column));
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

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the macro name has been changed and if the name isn't blank
                        if (column == MacroEditorColumnInfo.NAME.ordinal())
                        {
                            // Store the new macro name in the table data array after removing any
                            // spaces between the name and first left parenthesis
                            newValueS = newValueS.replaceFirst("\\s+\\(", "(");
                            tableData.get(row)[column] = newValueS;

                            // Check if the macro name does not match the alphanumeric input type
                            if (!newValueS.matches(DefaultInputType.MACRO_NAME.getInputMatch()))
                            {
                                throw new CCDDException("Illegal character(s) in macro name");
                            }

                            // Compare this macro name to the others in the table in order to avoid
                            // creating a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being edited, and if the macro
                                // name matches the one being added (case insensitive)
                                if (otherRow != row
                                    && newValueS.equalsIgnoreCase(tableData.get(otherRow)[column].toString()))
                                {
                                    throw new CCDDException("Macro name already in use");
                                }
                            }
                        }
                        // Check if the macro value changed
                        else if (column == MacroEditorColumnInfo.VALUE.ordinal())
                        {
                            // Create a macro handler using the values currently displayed in the
                            // macro editor
                            CcddMacroHandler newMacroHandler = new CcddMacroHandler(ccddMain,
                                                                                    getUpdatedData());
                            newMacroHandler.setHandlers(ccddMain.getVariableHandler(),
                                                        ccddMain.getDataTypeHandler());

                            // Get the macro's index and name
                            String index = tableData.get(row)[MacroEditorColumnInfo.OID.ordinal()].toString();
                            String macroName = tableData.get(row)[MacroEditorColumnInfo.NAME.ordinal()].toString();

                            // Check if the macro has a name and if the macro value is valid
                            // (doesn't cause a recursive reference)
                            if (!macroName.isEmpty() && newMacroHandler.isMacroRecursive(macroName))
                            {
                                throw new CCDDException("Macro '</b>"
                                                        + macroName
                                                        + "<b>' contains a recursive reference");
                            }

                            // Step through the committed macros
                            for (int commRow = 0; commRow < committedData.length; commRow++)
                            {
                                // Check if the index matches that for the committed macro
                                if (index.equals(committedData[commRow][MacroEditorColumnInfo.OID.ordinal()]))
                                {
                                    // Validate the macro usage. Use the committed name (in place
                                    // of the current name in the editor, in case it's been
                                    // changed) since this is how the macro is referenced in the
                                    // data tables. Stop searching since a match was found
                                    macroHandler.validateMacroUsage(committedData[commRow][MacroEditorColumnInfo.NAME.ordinal()],
                                                                    newMacroHandler,
                                                                    CcddMacroEditorDialog.this);
                                    break;
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
                        new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                  "<html><b>" + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    macroTable.getUndoManager().undoRemoveEdit();
                }
                catch (Exception e)
                {
                    // Display a dialog providing details on the unanticipated error
                    CcddUtilities.displayException(e, CcddMacroEditorDialog.this);
                }

                return false;
            }

            /**************************************************************************************
             * Load the table macro definition values into the table and format the table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(committedData,
                                            MacroEditorColumnInfo.getColumnNames(),
                                            null,
                                            MacroEditorColumnInfo.getToolTips(),
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

                    // Check if the cell is required and is empty
                    if (MacroEditorColumnInfo.values()[macroTable.convertColumnIndexToModel(column)].isRequired()
                        && macroTable.getValueAt(row, column).toString().isEmpty())
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
                return MacroEditorColumnInfo.getEmptyRow();
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether or not any unstored changes
                // exist
                setTitle(DIALOG_TITLE
                         + (macroTable.isTableChanged(committedData)
                                                                     ? CHANGE_INDICATOR
                                                                     : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(macroTable);

        // Set common table parameters and characteristics
        macroTable.setFixedCharacteristics(scrollPane,
                                           true,
                                           ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                           TableSelectionMode.SELECT_BY_CELL,
                                           false,
                                           ModifiableColorInfo.TABLE_BACK.getColor(),
                                           true,
                                           true,
                                           ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                           true);

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
        if (macroTable.isLastCellValid()
            && (!macroTable.isTableChanged(committedData)
                || new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
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
     * Based on the input flag, display the macro values (names) in place of the macro names
     * (values)
     *
     * @param isExpand
     *            true to replace the macro names with the corresponding macro values; false to
     *            restore the macro names
     *********************************************************************************************/
    protected void expandMacros(boolean isExpand)
    {
        UndoableTableModel tableModel = (UndoableTableModel) macroTable.getModel();

        // Check if the macro values are being displayed
        if (isExpand)
        {
            // Create storage for the original cell values
            originalCellData = new String[tableModel.getRowCount()][tableModel.getColumnCount()];
        }

        // Step through the visible rows
        for (int row = 0; row < tableModel.getRowCount(); row++)
        {
            // Check if the macro values are being displayed
            if (isExpand)
            {
                // Store the original cell values for when the macro names are restored
                originalCellData[row][MacroEditorColumnInfo.VALUE.ordinal()] = tableModel.getValueAt(row, MacroEditorColumnInfo.VALUE.ordinal()).toString();
            }

            // Replace the table cells with their original contents (i.e., show macro names in
            // place of their corresponding values)
            tableModel.setValueAt((isExpand
                                            ? macroHandler.getMacroExpansion(originalCellData[row][MacroEditorColumnInfo.VALUE.ordinal()])
                                            : originalCellData[row][MacroEditorColumnInfo.VALUE.ordinal()]),
                                  row,
                                  MacroEditorColumnInfo.VALUE.ordinal(),
                                  false);
        }
    }

    /**********************************************************************************************
     * Get the updated macro data
     *
     * @return List containing the updated macro data
     *********************************************************************************************/
    private List<String[]> getUpdatedData()
    {
        return Arrays.asList(CcddUtilities.convertObjectToString(macroTable.getTableData(true)));
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
        for (int row = 0; row < macroTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = macroTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < macroTable.getRowCount())
            {
                // Step through each column in the row
                for (int column = 0; column < macroTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (MacroEditorColumnInfo.values()[column].isRequired()
                        && macroTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '</b>"
                                                                                                  + macroTable.getColumnName(column)
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

        return dataIsMissing;
    }

    /**********************************************************************************************
     * Compare the current macro table data to the committed data and create lists of the changed
     * values necessary to update the table in the database to match the current values
     *********************************************************************************************/
    private void buildUpdates()
    {
        // Remove change information from a previous commit, if any
        modifications.clear();

        // Get the number of rows that have been committed to the database
        int numCommitted = committedData.length;

        // Get the macro table cell values
        String[][] tableData = CcddUtilities.convertObjectToString(macroTable.getTableData(true));

        // Step through each row in the macro table
        for (int tblRow = 0; tblRow < tableData.length; tblRow++)
        {
            // Check if the OID isn't blank
            if (!tableData[tblRow][MacrosColumn.OID.ordinal()].toString().isEmpty())
            {
                boolean matchFound = false;

                // Step through each row in the committed version of the macro table data
                for (int comRow = 0; comRow < numCommitted && !matchFound; comRow++)
                {
                    // Check if the index values match for these rows
                    if (tableData[tblRow][MacrosColumn.OID.ordinal()].equals(committedData[comRow][MacrosColumn.OID.ordinal()]))
                    {
                        // Set the flags indicating this row has a match
                        matchFound = true;

                        boolean isChangedColumn = false;

                        // Step through each column in the row
                        for (int column = 0; column < tableData[tblRow].length; column++)
                        {
                            // Check if the current and committed values don't match
                            if (!tableData[tblRow][column].equals(committedData[comRow][column]))
                            {
                                // Set the flag to indicate a column value changed and stop
                                // searching
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
}
