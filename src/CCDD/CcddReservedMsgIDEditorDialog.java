/**
 * CFS Command and Data Dictionary reserved message ID editor dialog.
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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
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
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ReservedMsgIDEditorColumnInfo;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary reserved message ID editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddReservedMsgIDEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private CcddJTableHandler msgIDTable;

    // Table instance model data. Current copy is the table information as it exists in the table
    // editor and is used to determine what changes have been made to the table since the previous
    // editor update
    private String[][] committedData;

    // Dialog title
    private static final String DIALOG_TITLE = "Reserved Message ID Editor";

    /**********************************************************************************************
     * Reserved message ID editor dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddReservedMsgIDEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();

        // Create the reserved message ID editor dialog
        initialize();
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of reserved message ID updates to the database
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *********************************************************************************************/
    protected void doMsgIDUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Update the reserved message ID handler with the changes
            rsvMsgIDHandler.setReservedMsgIDData(getUpdatedData());

            // Update the copy of the reserved message ID data so it can be used to determine if
            // changes are made
            storeCurrentData();

            // Accept all edits for this table
            msgIDTable.getUndoManager().discardAllEdits();
        }
    }

    /**********************************************************************************************
     * Copy the reserved message ID data so it can be used to determine if changes are made
     *********************************************************************************************/
    private void storeCurrentData()
    {
        // Check if the table has fields
        if (!rsvMsgIDHandler.getReservedMsgIDData().isEmpty())
        {
            // Store the reserved message ID information
            committedData = rsvMsgIDHandler.getReservedMsgIDData().toArray(new String[0][0]);
        }
        // The table has no reserved message IDs
        else
        {
            // Initialize the fields
            committedData = new String[0][0];
        }
    }

    /**********************************************************************************************
     * Create the reserved message ID editor dialog. This is executed in a separate thread since it
     * can take a noticeable amount time to complete, and by using a separate thread the GUI is
     * allowed to continue to update. The GUI menu commands, however, are disabled until the
     * telemetry scheduler initialization completes execution
     *********************************************************************************************/
    private void initialize()
    {
        // Build the data type editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /**************************************************************************************
             * Build the data type editor dialog
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
                                                                GridBagConstraints.BOTH,
                                                                new Insets(0, 0, 0, 0),
                                                                0,
                                                                0);

                // Create a copy of the reserved message ID data so it can be used to determine if
                // changes are made
                storeCurrentData();

                // Define the panel to contain the table and place it in the editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createReservedMsgIDTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());

                // Set the modal undo manager and table references in the keyboard handler while
                // the reserved message ID editor is active
                ccddMain.getKeyboardHandler().setModalDialogReference(msgIDTable.getUndoManager(),
                                                                      msgIDTable);

                // New button
                JButton btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                                           INSERT_ICON,
                                                                           KeyEvent.VK_I,
                                                                           "Insert a new row into the table");

                // Create a listener for the Insert Row button
                btnInsertRow.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Insert a new row into the table at the selected location
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.insertEmptyRow(true);
                    }
                });

                // Delete button
                JButton btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                                           DELETE_ICON,
                                                                           KeyEvent.VK_D,
                                                                           "Delete the selected row(s) from the table");

                // Create a listener for the Delete row button
                btnDeleteRow.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Delete the selected row(s) from the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.deleteRow(true);
                    }
                });

                // Move Up button
                JButton btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                                        UP_ICON,
                                                                        KeyEvent.VK_U,
                                                                        "Move the selected row(s) up");

                // Create a listener for the Move Up button
                btnMoveUp.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) up in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.moveRowUp();
                    }
                });

                // Move Down button
                JButton btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                                          DOWN_ICON,
                                                                          KeyEvent.VK_W,
                                                                          "Move the selected row(s) down");

                // Create a listener for the Move Down button
                btnMoveDown.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Move the selected row(s) down in the table
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.moveRowDown();
                    }
                });

                // Undo button
                JButton btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                                      UNDO_ICON,
                                                                      KeyEvent.VK_Z,
                                                                      "Undo the last edit");

                // Create a listener for the Undo button
                btnUndo.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Undo the last cell edit
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.getUndoManager().undo();
                    }
                });

                // Redo button
                JButton btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                                      REDO_ICON,
                                                                      KeyEvent.VK_Y,
                                                                      "Redo the last undone edit");

                // Create a listener for the Redo button
                btnRedo.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Redo the last cell edit that was undone
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        msgIDTable.getUndoManager().redo();
                    }
                });

                // Store the reserved message IDs button
                JButton btnStore = CcddButtonPanelHandler.createButton("Store",
                                                                       STORE_ICON,
                                                                       KeyEvent.VK_S,
                                                                       "Store the reserved message ID(s)");
                btnStore.setEnabled(ccddMain.getDbControlHandler().isAccessReadWrite());

                // Create a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Store the reserved message IDs
                     *****************************************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's content has changed,
                        // none of the required columns is missing a value, and the user confirms
                        // the action
                        if (msgIDTable.isTableChanged(committedData)
                            && !checkForMissingColumns()
                            && new CcddDialogHandler().showMessageDialog(CcddReservedMsgIDEditorDialog.this,
                                                                         "<html><b>Store changes in project database?",
                                                                         "Store Changes",
                                                                         JOptionPane.QUESTION_MESSAGE,
                                                                         DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
                        {
                            // Store the updated reserved message IDs table
                            dbTable.storeInformationTableInBackground(InternalTable.RESERVED_MSG_IDS,
                                                                      CcddUtilities.removeArrayListColumn(getUpdatedData(),
                                                                                                          ReservedMsgIDsColumn.OID.ordinal()),
                                                                      null,
                                                                      CcddReservedMsgIDEditorDialog.this);
                        }
                    }
                });

                // Close button
                btnClose = CcddButtonPanelHandler.createButton("Close",
                                                               CLOSE_ICON,
                                                               KeyEvent.VK_C,
                                                               "Close the reserved message ID editor");

                // Create a listener for the Close button
                btnClose.addActionListener(new ValidateCellActionListener(msgIDTable)
                {
                    /******************************************************************************
                     * Close the reserved message ID editor dialog
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
             * Reserved message ID editor dialog creation complete
             *************************************************************************************/
            @Override
            protected void complete()
            {
                // Display the reserved message ID editor dialog
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
     * Create the reserved message ID table
     *
     * @return Reference to the scroll pane in which the table is placed
     *********************************************************************************************/
    private JScrollPane createReservedMsgIDTable()
    {
        // Define the reserved message ID editor JTable
        msgIDTable = new CcddJTableHandler()
        {
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
                return column == ReservedMsgIDEditorColumnInfo.OID.ordinal();
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
             * Allow pasting data into the reserved message ID cells
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData,
                                              int row,
                                              int column)
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

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the message ID has been changed and if the ID isn't blank
                        if (column == ReservedMsgIDEditorColumnInfo.MSG_ID.ordinal())
                        {
                            // Check if the message ID does not match the hexadecimal range input
                            // type
                            if (!newValueS.matches(DefaultInputType.HEXADECIMAL_RANGE.getInputMatch()))
                            {
                                throw new CCDDException("Invalid message ID; "
                                                        + DefaultInputType.HEXADECIMAL_RANGE.getInputName().toLowerCase()
                                                        + " expected");
                            }

                            // Convert the lower and upper (if present) values into integers
                            int[] lowHigh = rsvMsgIDHandler.parseReservedMsgIDs(newValueS);

                            // Check if this is a range and if the lower value is greater than the
                            // upper value
                            if (lowHigh[1] != -1 && lowHigh[0] > lowHigh[1])
                            {
                                // inform the user that the values are invalid
                                throw new CCDDException("Invalid message ID range; lower value must be &lt;= upper value");
                            }

                            // Compare this message ID to the others in the table in order to avoid
                            // creating a duplicate
                            for (int otherRow = 0; otherRow < getModel().getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being edited
                                if (otherRow != row)
                                {
                                    // Get the message ID from this row
                                    String otherValue = tableData.get(otherRow)[column].toString();

                                    // Check if the message ID isn't blank and if if matches or
                                    // falls within the range of this row's ID(s)
                                    if (!otherValue.isEmpty()
                                        && rsvMsgIDHandler.isWithinRange(lowHigh, otherValue))
                                    {
                                        // Inform the user that the new ID matches or falls within
                                        // the range of an existing reserved message ID
                                        throw new CCDDException("Message ID(s) already reserved");
                                    }
                                }
                            }

                            // Clean up the lower ID text
                            String[] range = newValueS.split("\\s*+-\\s*+");
                            newValueS = DefaultInputType.HEXADECIMAL.formatInput(range[0]);

                            // Check if the ID is a range
                            if (range.length == 2)
                            {
                                // Clean up the upper ID text
                                newValueS += " - "
                                             + DefaultInputType.HEXADECIMAL.formatInput(range[1]);
                            }

                            // Store the new value in the table data array after formatting the
                            // cell value
                            newValue = newValueS;
                            tableData.get(row)[column] = newValueS;
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
                        new CcddDialogHandler().showMessageDialog(CcddReservedMsgIDEditorDialog.this,
                                                                  "<html><b>"
                                                                                                      + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop the edit from the
                    // stack
                    tableData.get(row)[column] = oldValue;
                    msgIDTable.getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /**************************************************************************************
             * Load the table reserved message ID definition values into the table and format the
             * table cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(committedData,
                                            ReservedMsgIDEditorColumnInfo.getColumnNames(),
                                            null,
                                            ReservedMsgIDEditorColumnInfo.getToolTips(),
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
                    if (ReservedMsgIDEditorColumnInfo.values()[msgIDTable.convertColumnIndexToModel(column)].isRequired()
                        && msgIDTable.getValueAt(row, column).toString().isEmpty())
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
                return ReservedMsgIDEditorColumnInfo.getEmptyRow();
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
                         + (msgIDTable.isTableChanged(committedData)
                                                                     ? CHANGE_INDICATOR
                                                                     : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(msgIDTable);

        // Set common table parameters and characteristics
        msgIDTable.setFixedCharacteristics(scrollPane,
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
        if (msgIDTable.isLastCellValid()
            && (!msgIDTable.isTableChanged(committedData)
                || new CcddDialogHandler().showMessageDialog(CcddReservedMsgIDEditorDialog.this,
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
     * Get the updated reserved message ID data
     *
     * @return List containing the updated reserved message ID data
     *********************************************************************************************/
    private List<String[]> getUpdatedData()
    {
        return Arrays.asList(CcddUtilities.convertObjectToString(msgIDTable.getTableData(true)));
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
        for (int row = 0; row < msgIDTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = msgIDTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < msgIDTable.getRowCount())
            {
                // Step through each column in the row
                for (int column = 0; column < msgIDTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (ReservedMsgIDEditorColumnInfo.values()[column].isRequired()
                        && msgIDTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddReservedMsgIDEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '</b>"
                                                                                                          + msgIDTable.getColumnName(column)
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
}
