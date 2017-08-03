/**
 * CFS Command & Data Dictionary macro editor dialog. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
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
import java.awt.event.KeyEvent;
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
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.TableModification;
import CCDD.CcddClasses.ValidateCellActionListener;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.MacroEditorColumnInfo;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.TableSelectionMode;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary macro editor dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddMacroEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddMacroHandler macroHandler;
    private CcddJTableHandler macroTable;

    // Table instance model data. Current copy is the table information as it
    // exists in the table editor and is used to determine what changes have
    // been made to the table since the previous field editor update
    private String[][] committedData;

    // List of macro table content changes to process
    private List<TableModification> modifications;

    // List of macro references already loaded from the database. This is used
    // to avoid repeated searches for a the same macro
    private List<MacroReference> loadedReferences;

    // Dialog title
    private static final String DIALOG_TITLE = "Macro Editor";

    /**************************************************************************
     * Macro data table references class
     *************************************************************************/
    class MacroReference
    {
        private final String macroName;
        private final String[] references;

        /**********************************************************************
         * Macro data table references class constructor
         * 
         * @param macroName
         *            macro name
         *********************************************************************/
        MacroReference(String macroName)
        {
            this.macroName = macroName;

            // Get the references to the specified macro in the data tables
            references = macroHandler.getMacroReferences(macroName,
                                                         CcddMacroEditorDialog.this);
        }

        /**********************************************************************
         * Get the macro name associated with the references
         * 
         * @return Macro name
         *********************************************************************/
        protected String getMacroName()
        {
            return macroName;
        }

        /**********************************************************************
         * Get the references in the data tables for this macro
         * 
         * @return References in the data tables for this macro
         *********************************************************************/
        protected String[] getReferences()
        {
            return references;
        }
    }

    /**************************************************************************
     * Macro editor dialog class constructor
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddMacroEditorDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        macroHandler = ccddMain.getMacroHandler();

        // Set the reference to this dialog in main
        ccddMain.setMacroEditor(this);

        // Create the macro editor dialog
        initialize();
    }

    /**************************************************************************
     * Get the reference to the macro table
     * 
     * @return Reference to the macro table
     *************************************************************************/
    protected CcddJTableHandler getTable()
    {
        return macroTable;
    }

    /**************************************************************************
     * Perform the steps needed following execution of macro updates to the
     * database
     * 
     * @param commandError
     *            false if the database commands successfully completed; true
     *            if an error occurred and the changes were not made
     *************************************************************************/
    protected void doMacroUpdatesComplete(boolean commandError)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Update the macro handler with the changes
            macroHandler.setMacroData(getUpdatedData());

            // Step through each open table editor dialog
            for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
            {
                // Step through each individual editor
                for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                {
                    // Force the table to redraw to update the macro
                    // highlighting
                    editor.getTable().repaint();
                }
            }

            // Update the copy of the macro data so it can be used to determine
            // if changes are made
            storeCurrentData();

            // Accept all edits for this table
            macroTable.getUndoManager().discardAllEdits();
        }
    }

    /**************************************************************************
     * Copy the macro data so it can be used to determine if changes are made
     *************************************************************************/
    protected void storeCurrentData()
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

    /**************************************************************************
     * Create the macro editor dialog. This is executed in a separate thread
     * since it can take a noticeable amount time to complete, and by using a
     * separate thread the GUI is allowed to continue to update. The GUI menu
     * commands, however, are disabled until the telemetry scheduler
     * initialization completes execution
     *************************************************************************/
    private void initialize()
    {
        // Build the macro editor dialog in the background
        CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
        {
            // Create panels to hold the components of the dialog
            JPanel editorPnl = new JPanel(new GridBagLayout());
            JPanel buttonPnl = new JPanel();
            JButton btnClose;

            /******************************************************************
             * Build the macro editor dialog
             *****************************************************************/
            @Override
            protected void execute()
            {
                modifications = new ArrayList<TableModification>();
                loadedReferences = new ArrayList<MacroReference>();

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

                // Create a copy of the macro data so it can be used to
                // determine if changes are made
                storeCurrentData();

                // Define the panel to contain the table and place it in the
                // editor
                JPanel tablePnl = new JPanel();
                tablePnl.setLayout(new BoxLayout(tablePnl, BoxLayout.X_AXIS));
                tablePnl.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
                tablePnl.add(createMacroTable());
                editorPnl.add(tablePnl, gbc);
                editorPnl.setBorder(BorderFactory.createEmptyBorder());

                // Set the modal undo manager and table references in the
                // keyboard handler while the macro editor is active
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
                    /**********************************************************
                     * Insert a new row into the table at the selected location
                     *********************************************************/
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
                    /**********************************************************
                     * Delete the selected row(s) from the table
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Step through each row selected for deletion
                        for (int row : macroTable.getSelectedRows())
                        {
                            // Get the macro name
                            String name = macroTable.getValueAt(row, MacrosColumn.MACRO_NAME.ordinal()).toString();

                            // Check if the macro is used in any of the data
                            // tables
                            if (!name.isEmpty()
                                && macroHandler.getMacroReferences(name, CcddMacroEditorDialog.this).length != 0)
                            {
                                // Deselect the macro
                                macroTable.removeRowSelectionInterval(row, row);

                                // Inform the user that the macro can't be
                                // deleted
                                new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                          "<html><b>Cannot delete macro '"
                                                                              + name
                                                                              + "'; macro is referenced by a data table",
                                                                          "Delete Macro",
                                                                          JOptionPane.QUESTION_MESSAGE,
                                                                          DialogOption.OK_OPTION);
                            }
                        }

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
                    /**********************************************************
                     * Move the selected row(s) up in the table
                     *********************************************************/
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
                    /**********************************************************
                     * Move the selected row(s) down in the table
                     *********************************************************/
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
                    /**********************************************************
                     * Undo the last cell edit
                     *********************************************************/
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
                    /**********************************************************
                     * Redo the last cell edit that was undone
                     *********************************************************/
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

                // Create a listener for the Store button
                btnStore.addActionListener(new ValidateCellActionListener(macroTable)
                {
                    /**********************************************************
                     * Store the macros
                     *********************************************************/
                    @Override
                    protected void performAction(ActionEvent ae)
                    {
                        // Only update the table in the database if a cell's
                        // content has changed, none of the required columns is
                        // missing a value, and the user confirms the action
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

                            // Update the tables affected by the changes to the
                            // macro(s)
                            dbTable.modifyTablePerDataTypeOrMacroChanges(modifications,
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
                    /**********************************************************
                     * Close the macro editor dialog
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
             * Macro editor dialog creation complete
             *****************************************************************/
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

    /**************************************************************************
     * Create the macro table
     *
     * @return Reference to the scroll pane in which the table is placed
     *************************************************************************/
    private JScrollPane createMacroTable()
    {
        // Define the macro editor JTable
        macroTable = new CcddJTableHandler()
        {
            /******************************************************************
             * Allow multiple line display in all columns
             *****************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return true;
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
             * Allow pasting data into the macro cells
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

                // Create a string version of the new value
                String newValueS = newValue.toString();

                try
                {
                    // Check if the value isn't blank
                    if (!newValueS.isEmpty())
                    {
                        // Check if the macro name has been changed and if the
                        // name isn't blank
                        if (column == MacroEditorColumnInfo.NAME.ordinal())
                        {
                            // Check if the macro name does not match the
                            // alphanumeric input type
                            if (!newValueS.matches(InputDataType.ALPHANUMERIC.getInputMatch()))
                            {
                                throw new CCDDException("Illegal character(s) in macro name");
                            }

                            // Compare this macro name to the others in the
                            // table in order to avoid creating a duplicate
                            for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                            {
                                // Check if this row isn't the one being
                                // edited, and if the macro name matches the
                                // one being added (case insensitive)
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
                            // Create a macro handler using the values
                            // currently displayed in the macro editor
                            CcddMacroHandler newMacroHandler = new CcddMacroHandler(getUpdatedData());

                            // Get the macro's index
                            String index = tableData.get(row)[MacroEditorColumnInfo.OID.ordinal()].toString();

                            // Step through the committed macros
                            for (int commRow = 0; commRow < committedData.length; commRow++)
                            {
                                // Check if the index matches that for the
                                // committed macro
                                if (index.equals(committedData[commRow][MacroEditorColumnInfo.OID.ordinal()]))
                                {
                                    List<String> tableNames = new ArrayList<String>();

                                    // Get the macro name. Use the committed
                                    // name (in place of the current name in
                                    // the editor, in case it's been changed)
                                    // since this is how the macro is
                                    // referenced in the data tables
                                    String macroName = committedData[commRow][MacroEditorColumnInfo.NAME.ordinal()];

                                    MacroReference macroRefs = null;

                                    // Step through the list of the macro
                                    // search references already loaded
                                    for (MacroReference loadedRef : loadedReferences)
                                    {
                                        // Check if the macro name matches that
                                        // for an already searched macro
                                        if (macroName.equals(loadedRef.getMacroName()))
                                        {
                                            // Store the macro search reference
                                            // and stop searching
                                            macroRefs = loadedRef;
                                            break;
                                        }
                                    }

                                    // Check if the macro references haven't
                                    // already been loaded
                                    if (macroRefs == null)
                                    {
                                        // Search for references to this macro
                                        macroRefs = new MacroReference(macroName);

                                        // Add the search results to the list
                                        // so that this search doesn't get
                                        // performed again
                                        loadedReferences.add(macroRefs);
                                    }

                                    // Step through each reference to the macro
                                    // in the tables
                                    for (String macroRef : macroRefs.getReferences())
                                    {
                                        // Split the reference into table name,
                                        // column name, table type, and context
                                        String[] tblColDescAndCntxt = macroRef.split(TABLE_DESCRIPTION_SEPARATOR, 4);
                                        String refComment = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()];

                                        // Check if the this is a reference to
                                        // a prototype data table
                                        if (!refComment.isEmpty())
                                        {
                                            // Extract the viewable name and
                                            // type of the table and the name
                                            // of the column containing the
                                            // data type, and separate the
                                            // column string into the
                                            // individual column values
                                            String[] refNameAndType = refComment.split(",");
                                            String refColumn = tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()];
                                            String[] refContext = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                                            // Use the type and column to get
                                            // the column's input data type
                                            TypeDefinition typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(refNameAndType[1]);
                                            int columnIndex = typeDefn.getColumnIndexByDbName(refColumn);
                                            InputDataType inputType = typeDefn.getInputTypes()[columnIndex];

                                            // Check if referenced column
                                            // value, with the new macro value
                                            // incorporated, doesn't match the
                                            // column's input type, and that
                                            // this table hasn't already been
                                            // found to have a conflict
                                            if (!newMacroHandler.getMacroExpansion(refContext[columnIndex]).matches(inputType.getInputMatch())
                                                && !tableNames.contains(refNameAndType[0]))
                                            {
                                                // Add the affected table name
                                                // to the list
                                                tableNames.add(refNameAndType[0]);
                                            }
                                        }
                                    }

                                    // Check if any tables with conflicts with
                                    // the new data type were found
                                    if (!tableNames.isEmpty())
                                    {
                                        throw new CCDDException("Macro value is not consistent with macro usage in table(s) '</b>"
                                                                + dbTable.getShortenedTableNames(tableNames.toArray(new String[0]))
                                                                + "<b>'");
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
                        new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                  "<html><b>"
                                                                      + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the cell contents to its original value and pop
                    // the edit from the stack
                    tableData.get(row)[column] = oldValue;
                    macroTable.getUndoManager().undoRemoveEdit();
                }

                return false;
            }

            /******************************************************************
             * Load the table macro definition values into the table and format
             * the table cells
             *****************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column
                // names, set up the editors and renderers for the table cells,
                // set up the table grid lines, and calculate the minimum width
                // required to display the table information
                setUpdatableCharacteristics(committedData,
                                            MacroEditorColumnInfo.getColumnNames(),
                                            null,
                                            new Integer[] {MacroEditorColumnInfo.OID.ordinal()},
                                            null,
                                            MacroEditorColumnInfo.getToolTips(),
                                            true,
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

                    // Check if the cell is required and is empty
                    if (MacroEditorColumnInfo.values()[macroTable.convertColumnIndexToModel(column)].isRequired()
                        && macroTable.getValueAt(row, column).toString().isEmpty())
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
                return MacroEditorColumnInfo.getEmptyRow();
            }

            /******************************************************************
             * Handle a change to the table's content
             *****************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether or not
                // any unstored changes exist
                setTitle(DIALOG_TITLE
                         + (macroTable.isTableChanged(committedData)
                                                                    ? "*"
                                                                    : ""));

                // Force the table to redraw so that changes to the cells are
                // displayed
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

        // Discard the edits created by adding the columns initially
        macroTable.getUndoManager().discardAllEdits();

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

    /**************************************************************************
     * Get the updated macro data
     * 
     * @return List containing the updated macro data
     *************************************************************************/
    protected List<String[]> getUpdatedData()
    {
        return Arrays.asList(CcddUtilities.convertObjectToString(macroTable.getTableData(true)));
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

                        // Inform the user that a row is missing required data.
                        // If Cancel is selected then do not perform checks on
                        // other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddMacroEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '"
                                                                          + macroTable.getColumnName(column)
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
     * Compare the current macro table data to the committed data and create
     * lists of the changed values necessary to update the table in the
     * database to match the current values
     *************************************************************************/
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
            boolean matchFound = false;

            // Step through each row in the committed version of the macro
            // table data
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
