/**
 * CFS Command and Data Dictionary data field editor dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.BREAK_ICON;
import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CHANGE_INDICATOR;
import static CCDD.CcddConstants.CLOSE_ICON;
import static CCDD.CcddConstants.DELETE_ICON;
import static CCDD.CcddConstants.DOWN_ICON;
import static CCDD.CcddConstants.INSERT_ICON;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.REDO_ICON;
import static CCDD.CcddConstants.SEPARATOR_ICON;
import static CCDD.CcddConstants.STORE_ICON;
import static CCDD.CcddConstants.UNDO_ICON;
import static CCDD.CcddConstants.UP_ICON;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
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

import CCDD.CcddClassesComponent.ComboBoxCellEditor;
import CCDD.CcddClassesComponent.PaddedComboBox;
import CCDD.CcddClassesComponent.ValidateCellActionListener;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSizeInfo;
import CCDD.CcddConstants.TableInsertionPoint;
import CCDD.CcddConstants.TableSelectionMode;

/**************************************************************************************************
 * CFS Command and Data Dictionary data field editor dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddFieldEditorDialog extends CcddDialogHandler
{
    // Class references
    private final CcddKeyboardHandler keyboardHandler;
    private final CcddInputFieldPanelHandler fieldPnlHndlr;
    private CcddJTableHandler fieldTable;
    private final CcddInputTypeHandler inputTypeHandler;
    private final CcddFieldHandler fieldHandler;

    // Components referenced by multiple methods
    private JPanel outerPanel;
    private PaddedComboBox inputTypeCbox;
    private PaddedComboBox applicabilityCBox;
    private JButton btnInsertRow;
    private JButton btnDeleteRow;
    private JButton btnMoveUp;
    private JButton btnMoveDown;
    private JButton btnSeparator;
    private JButton btnBreak;
    private JButton btnUndo;
    private JButton btnRedo;
    private JButton btnUpdate;
    private JButton btnClose;

    // Table name, including the path if this is a structure type table, or group name
    private final String ownerName;

    // Flag that indicates if the applicability column should be displayed
    private final boolean includeApplicability;

    // Index for the data field editor's input type column
    private int inputTypeIndex;

    // Table instance model data array. Current copy of the table information as it exists in the
    // table editor. This is used to determine what changes have been made to the table since the
    // previous field editor update
    private Object[][] currentData;

    // List of the owner's field information
    private final List<FieldInformation> fieldInformation;

    // Dialog title
    private static final String DIALOG_TITLE = "Data Field Editor";

    /**********************************************************************************************
     * Data field editor dialog class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param fieldPnlHandler
     *            description and data field panel reference
     *
     * @param ownerName
     *            table name, including the path if this is a structure type table, or group name
     *
     * @param fieldInformation
     *            list of the owner's current field information
     *
     * @param includeApplicability
     *            true to include the applicability column
     *
     * @param minimumWidth
     *            minimum pixel width of the caller
     *********************************************************************************************/
    CcddFieldEditorDialog(CcddMain ccddMain,
                          CcddInputFieldPanelHandler fieldPnlHandler,
                          String ownerName,
                          List<FieldInformation> fieldInformation,
                          boolean includeApplicability,
                          int minimumWidth)
    {
        this.fieldPnlHndlr = fieldPnlHandler;
        this.ownerName = ownerName;
        this.includeApplicability = includeApplicability;
        this.fieldInformation = CcddFieldHandler.getFieldInformationCopy(fieldInformation);
        keyboardHandler = ccddMain.getKeyboardHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();

        // Set the field information in the undo handler in case an undo or redo is performed
        fieldPnlHandler.getUndoFieldPanel()
                       .setUndoFieldInformation(CcddFieldHandler.getFieldInformationCopy(fieldInformation));

        // Create the data field editor dialog
        initialize(minimumWidth);
    }

    /**********************************************************************************************
     * Create the data field editor dialog
     *
     * @param minimumWidth
     *            minimum pixel width of the caller
     *********************************************************************************************/
    private void initialize(final int minimumWidth)
    {
        // Assign unique IDs to the data fields
        CcddFieldHandler.assignFieldIDs(fieldInformation);

        // Convert the owner's current field information into the format for the editor table
        currentData = CcddFieldHandler.getFieldEditorDefinition(fieldInformation);

        // Define the table data field editor JTable
        fieldTable = new CcddJTableHandler()
        {
            /**************************************************************************************
             * Allow resizing of all columns except the Size, Required, and Applicability columns
             *************************************************************************************/
            @Override
            protected boolean isColumnResizable(int column)
            {
                return column != FieldEditorColumnInfo.REQUIRED.ordinal()
                       && column != FieldEditorColumnInfo.CHAR_SIZE.ordinal()
                       && column != FieldEditorColumnInfo.APPLICABILITY.ordinal();
            }

            /**************************************************************************************
             * Allow multiple line display in all but the Required, Size, and Applicability columns
             *************************************************************************************/
            @Override
            protected boolean isColumnMultiLine(int column)
            {
                return column != FieldEditorColumnInfo.REQUIRED.ordinal()
                       && column != FieldEditorColumnInfo.CHAR_SIZE.ordinal()
                       && column != FieldEditorColumnInfo.APPLICABILITY.ordinal();
            }

            /**************************************************************************************
             * Hide the specified column(s)
             *************************************************************************************/
            @Override
            protected boolean isColumnHidden(int column)
            {
                return column == FieldEditorColumnInfo.INHERITED.ordinal()
                       || column == FieldEditorColumnInfo.VALUE.ordinal()
                       || (!includeApplicability
                           && column == FieldEditorColumnInfo.APPLICABILITY.ordinal())
                       || column == FieldEditorColumnInfo.ID.ordinal();
            }

            /**************************************************************************************
             * Display the specified column(s) as check boxes
             *************************************************************************************/
            @Override
            protected boolean isColumnBoolean(int column)
            {
                return column == FieldEditorColumnInfo.REQUIRED.ordinal();
            }

            /**************************************************************************************
             * Override isCellEditable so that all columns except the line separators and breaks
             * can be edited
             *************************************************************************************/
            @Override
            public boolean isCellEditable(int row, int column)
            {
                boolean isEditable = true;

                // Check that the table has rows
                if (fieldTable.getRowCount() != 0)
                {
                    // Check if ...
                    if (
                    // ... the row represents a separator or line break
                    (column != FieldEditorColumnInfo.APPLICABILITY.ordinal()
                     && inputTypeHandler.getInputTypeByName(fieldTable.getValueAt(row,
                                                                                  inputTypeIndex)
                                                                      .toString())
                                        .getInputFormat()
                                        .equals(InputTypeFormat.PAGE_FORMAT))

                        // ... the row represents a table's inherited field
                        || (CcddFieldHandler.isTableField(ownerName)
                            && Boolean.parseBoolean(fieldTable.getModel().getValueAt(convertRowIndexToModel(row),
                                                                                     FieldEditorColumnInfo.INHERITED.ordinal())
                                                              .toString())))
                    {
                        // Set the flag to indicate this cell is not editable
                        isEditable = false;
                    }
                }

                return isEditable;
            }

            /**************************************************************************************
             * Allow pasting data into the data field cells
             *************************************************************************************/
            @Override
            protected boolean isDataAlterable(Object[] rowData, int row, int column)
            {
                return isCellEditable(convertRowIndexToView(row),
                                      convertColumnIndexToView(column));
            }

            /**************************************************************************************
             * Override the CcddJTableHandler method to prevent deleting the contents of the cell
             * at the specified row and column
             *
             * @param row
             *            table row index in view coordinates
             *
             * @param column
             *            table column index in view coordinates
             *
             * @return false if the cell contains a combo box; true otherwise
             *************************************************************************************/
            @Override
            protected boolean isCellBlankable(int row, int column)
            {
                // Convert the column index to model coordinates
                column = convertColumnIndexToModel(column);

                return column != FieldEditorColumnInfo.INPUT_TYPE.ordinal()
                       && column != FieldEditorColumnInfo.APPLICABILITY.ordinal();
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
                    // Check if the field name has been changed and if the name isn't blank
                    if (column == FieldEditorColumnInfo.NAME.ordinal()
                        && !newValueS.isEmpty())
                    {
                        // Compare this field name to the others in the table in order to avoid
                        // creating a duplicate
                        for (int otherRow = 0; otherRow < getRowCount(); otherRow++)
                        {
                            // Check if this row isn't the one being edited, and if the field name
                            // matches the one being added (case insensitive)
                            if (otherRow != row
                                && newValueS.equalsIgnoreCase(tableData.get(otherRow)[column].toString()))
                            {
                                throw new CCDDException("Field name '</b>"
                                                        + newValueS
                                                        + "<b>' already in use");
                            }
                        }
                    }
                    // Check if this is the field size column
                    else if (column == FieldEditorColumnInfo.CHAR_SIZE.ordinal())
                    {
                        // Check if the field size is not a positive integer
                        if (!newValueS.matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
                        {
                            throw new CCDDException("Field size must be a positive integer");
                        }

                        // Check if the character width of the data field exceeds the maximum
                        // allowed. If the field is too wide the rendering of the table can make
                        // the interface unusable
                        if (Integer.valueOf(newValueS) > ModifiableSizeInfo.MAX_DATA_FIELD_CHAR_WIDTH.getSize())
                        {
                            throw new CCDDException("Field size must be less than or equal to </b>"
                                                    + ModifiableSizeInfo.MAX_DATA_FIELD_CHAR_WIDTH.getSize());
                        }

                        // Remove any unneeded characters and store the cleaned number
                        tableData.get(row)[column] = Integer.valueOf(newValueS.replaceAll(DefaultInputType.INT_POSITIVE.getInputMatch(),
                                                                                          "$1"));
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
                        new CcddDialogHandler().showMessageDialog(CcddFieldEditorDialog.this,
                                                                  "<html><b>" + ce.getMessage(),
                                                                  "Invalid Input",
                                                                  JOptionPane.WARNING_MESSAGE,
                                                                  DialogOption.OK_OPTION);
                    }

                    // Restore the column name to its original value and set the error message
                    tableData.get(row)[column] = oldValue;
                }

                return false;
            }

            /**************************************************************************************
             * Load the table data field definition values into the table and format the table
             * cells
             *************************************************************************************/
            @Override
            protected void loadAndFormatData()
            {
                // Place the data into the table model along with the column names, set up the
                // editors and renderers for the table cells, set up the table grid lines, and
                // calculate the minimum width required to display the table information
                setUpdatableCharacteristics(currentData,
                                            FieldEditorColumnInfo.getColumnNames(),
                                            null,
                                            FieldEditorColumnInfo.getToolTips(),
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

                    // Check if the row is for an inherited field
                    if (Boolean.parseBoolean(fieldTable.getModel().getValueAt(row,
                                                                              FieldEditorColumnInfo.INHERITED.ordinal())
                                                       .toString()))
                    {
                        // Indicate that the field can't be altered
                        comp.setForeground(ModifiableColorInfo.PROTECTED_TEXT.getColor());
                        comp.setBackground(ModifiableColorInfo.PROTECTED_BACK.getColor());

                        // Set the flag indicating that the cell value is valid
                        found = true;
                    }
                    // Check if the cell is required and is empty
                    else if (FieldEditorColumnInfo.values()[fieldTable.convertColumnIndexToModel(column)].isRequired()
                             && fieldTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the flag indicating that the cell value is invalid
                        found = false;
                    }
                    // Check if this is the input type column
                    else if (column == inputTypeIndex && inputTypeCbox != null)
                    {
                        found = false;

                        // Check if the type is a separator or line break
                        if (inputTypeHandler.getInputTypeByName(fieldTable.getValueAt(row,
                                                                                      column)
                                                                          .toString())
                                            .getInputFormat()
                                            .equals(InputTypeFormat.PAGE_FORMAT))
                        {
                            found = true;
                        }
                        // The type is not a separator or line break
                        else
                        {
                            // Step through each combo box item
                            for (int index = 0; index < inputTypeCbox.getItemCount() && !found; index++)
                            {
                                // Check if the cell matches the combo box item
                                if (inputTypeCbox.getItemAt(index).equals(fieldTable.getValueAt(row, column).toString()))
                                {
                                    // Set the flag indicating that the cell value is valid
                                    found = true;
                                }
                            }
                        }
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
                return FieldEditorColumnInfo.getEmptyRow();
            }

            /**************************************************************************************
             * Handle a change to the table's content
             *************************************************************************************/
            @Override
            protected void processTableContentChange()
            {
                // Add or remove the change indicator based on whether or not any unstored changes
                // exist
                setTitle(DIALOG_TITLE + ": " + ownerName
                         + (fieldTable.isTableChanged(currentData)
                                                                   ? CHANGE_INDICATOR
                                                                   : ""));

                // Force the table to redraw so that changes to the cells are displayed
                repaint();
            }
        };

        // Place the table into a scroll pane
        JScrollPane scrollPane = new JScrollPane(fieldTable);

        // Set common table parameters and characteristics
        fieldTable.setFixedCharacteristics(scrollPane,
                                           true,
                                           ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
                                           TableSelectionMode.SELECT_BY_CELL,
                                           false,
                                           ModifiableColorInfo.TABLE_BACK.getColor(),
                                           true,
                                           true,
                                           ModifiableFontInfo.DATA_TABLE_CELL.getFont(),
                                           true);

        // Create a drop-down combo box to display the available field input types
        setUpInputTypeColumn();

        // Create a drop-down combo box to display the available field applicability types
        setUpApplicabilityColumn();

        // Define the editor panel to contain the table
        JPanel editorPanel = new JPanel();
        editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.X_AXIS));
        editorPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        editorPanel.add(scrollPane);

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

        // Create an outer panel to put the editor panel in (the border doesn't appear without
        // this)
        outerPanel = new JPanel(new GridBagLayout());
        outerPanel.add(editorPanel, gbc);
        outerPanel.setBorder(BorderFactory.createEmptyBorder());

        // Create the lower (button) panel
        JPanel buttonPnl = new JPanel();

        // Insert button
        btnInsertRow = CcddButtonPanelHandler.createButton("Ins Row",
                                                           INSERT_ICON,
                                                           KeyEvent.VK_I,
                                                           "Insert a new row into the table");

        // Create a listener for the Insert Row button
        btnInsertRow.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Insert a new row into the table at the selected location
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.insertEmptyRow(true);
            }
        });

        // Delete button
        btnDeleteRow = CcddButtonPanelHandler.createButton("Del Row",
                                                           DELETE_ICON,
                                                           KeyEvent.VK_D,
                                                           "Delete the selected row(s) from the table");

        // Create a listener for the Delete row button
        btnDeleteRow.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Delete the selected row(s) from the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.deleteRow(true);
            }
        });

        // Move Up button
        btnMoveUp = CcddButtonPanelHandler.createButton("Up",
                                                        UP_ICON,
                                                        KeyEvent.VK_U,
                                                        "Move the selected row(s) up");

        // Create a listener for the Move Up button
        btnMoveUp.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Move the selected row(s) up in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.moveRowUp();
            }
        });

        // Move Down button
        btnMoveDown = CcddButtonPanelHandler.createButton("Down",
                                                          DOWN_ICON,
                                                          KeyEvent.VK_W,
                                                          "Move the selected row(s) down");

        // Create a listener for the Move Down button
        btnMoveDown.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Move the selected row(s) down in the table
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.moveRowDown();
            }
        });

        // Separator button
        btnSeparator = CcddButtonPanelHandler.createButton("Separator",
                                                           SEPARATOR_ICON,
                                                           KeyEvent.VK_S,
                                                           "Insert a horizontal separator");

        // Create a listener for the Separator button
        btnSeparator.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Insert a line separator
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.insertRow(true,
                                     TableInsertionPoint.SELECTION,
                                     new Object[] {DefaultInputType.SEPARATOR.getInputName(),
                                                   "Line separator",
                                                   0,
                                                   DefaultInputType.SEPARATOR.getInputName(),
                                                   false,
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   "",
                                                   false});
            }
        });

        // Break button
        btnBreak = CcddButtonPanelHandler.createButton("Break",
                                                       BREAK_ICON,
                                                       KeyEvent.VK_B,
                                                       "Insert a line break");

        // Create a listener for the Move Down button
        btnBreak.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Insert a line break
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.insertRow(true,
                                     TableInsertionPoint.SELECTION,
                                     new Object[] {DefaultInputType.BREAK.getInputName(),
                                                   "Line break",
                                                   0,
                                                   DefaultInputType.BREAK.getInputName(),
                                                   false,
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   "",
                                                   false});
            }
        });

        // Undo button
        btnUndo = CcddButtonPanelHandler.createButton("Undo",
                                                      UNDO_ICON,
                                                      KeyEvent.VK_Z,
                                                      "Undo the last edit");

        // Create a listener for the Undo button
        btnUndo.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Undo the last cell edit
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.getUndoManager().undo();
            }
        });

        // Redo button
        btnRedo = CcddButtonPanelHandler.createButton("Redo",
                                                      REDO_ICON,
                                                      KeyEvent.VK_Y,
                                                      "Redo the last undone edit");

        // Create a listener for the Redo button
        btnRedo.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Redo the last cell edit that was undone
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                fieldTable.getUndoManager().redo();
            }
        });

        // Update the data fields button
        btnUpdate = CcddButtonPanelHandler.createButton("Update",
                                                        STORE_ICON,
                                                        KeyEvent.VK_U,
                                                        "Update the table data field(s)");

        // Create a listener for the Update button
        btnUpdate.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Update the owner's data fields
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                // Check if no required columns are empty, the currently editing cell's contents is
                // valid (if a cell is being edited), and that there are changes to update
                if (!checkForMissingColumns()
                    && fieldTable.isLastCellValid()
                    && fieldTable.isTableChanged(currentData))
                {
                    // Rebuild the data field panel in the table editor using the current text
                    // field contents
                    recreateDataFieldPanel(minimumWidth);
                }
            }
        });

        // Close button
        btnClose = CcddButtonPanelHandler.createButton("Close",
                                                       CLOSE_ICON,
                                                       KeyEvent.VK_C,
                                                       "Close the data field editor");

        // Create a listener for the Close button
        btnClose.addActionListener(new ValidateCellActionListener(fieldTable)
        {
            /**************************************************************************************
             * Close the data field editor dialog
             *************************************************************************************/
            @Override
            protected void performAction(ActionEvent ae)
            {
                windowCloseButtonAction();
            }
        });

        // Add buttons in the order in which they'll appear (left to right, top to bottom)
        buttonPnl.add(btnInsertRow);
        buttonPnl.add(btnMoveUp);
        buttonPnl.add(btnSeparator);
        buttonPnl.add(btnUndo);
        buttonPnl.add(btnUpdate);
        buttonPnl.add(btnDeleteRow);
        buttonPnl.add(btnMoveDown);
        buttonPnl.add(btnBreak);
        buttonPnl.add(btnRedo);
        buttonPnl.add(btnClose);

        // Distribute the buttons across two rows
        setButtonRows(2);

        // Set the modal undo manager and table references in the keyboard handler while the data
        // field editor is active
        keyboardHandler.setModalDialogReference(fieldTable.getUndoManager(), fieldTable);

        // Display the table data field editor dialog
        showOptionsDialog(fieldPnlHndlr.getOwner(),
                          outerPanel,
                          buttonPnl,
                          btnClose,
                          DIALOG_TITLE + ": " + ownerName,
                          true);

        // Clear the modal dialog references in the keyboard handler since the data field editor is
        // no longer active. The component that called this editor, if a modal dialog, must set
        // these parameters again once control is returned to it from this editor
        keyboardHandler.setModalDialogReference(null, null);
    }

    /**********************************************************************************************
     * Recreate the data fields for display below the table
     *
     * @param minDialogWidth
     *            minimum pixel width of the caller
     *********************************************************************************************/
    private void recreateDataFieldPanel(int minDialogWidth)
    {
        // Get the data field information as it is currently displayed in the table editor and
        // update the current data array to reflect the updates so that subsequent changes are
        // correctly identified
        currentData = fieldTable.getTableData(true);

        // Clear the current field information. This is done so the the variable's reference isn't
        // changed
        fieldInformation.clear();

        // Step through each of the supplied data field's information
        for (FieldInformation fieldInfo : fieldHandler.getFieldInformationFromData(currentData,
                                                                                   ownerName))
        {
            // Add the data field information to the list
            fieldInformation.add(fieldInfo);
        }

        // Rebuild the data field panel in the table editor using the current text field contents
        fieldPnlHndlr.createDataFieldPanel(true, fieldInformation);

        // Update the dialog title to remove the change indicator
        setTitle(DIALOG_TITLE + ": " + ownerName);

        // Update the change indicator for the owner of this edit panel
        fieldPnlHndlr.updateOwnerChangeIndicator();

        // Update the undo manager so that all data field editor edits up to the press of the
        // Update button can be undone/redone
        fieldTable.getUndoManager().endEditSequence();
    }

    /**********************************************************************************************
     * Handle the dialog close button press event
     *********************************************************************************************/
    @Override
    protected void windowCloseButtonAction()
    {
        // Check if the contents of the last cell edited in the editor table is validated and that
        // there are changes that haven't been applied. If changes exist then confirm discarding
        // the changes
        if (fieldTable.isLastCellValid()
            && (!fieldTable.isTableChanged(currentData)
                || new CcddDialogHandler().showMessageDialog(CcddFieldEditorDialog.this,
                                                             "<html><b>Discard changes?",
                                                             "Discard Changes",
                                                             JOptionPane.QUESTION_MESSAGE,
                                                             DialogOption.OK_CANCEL_OPTION) == OK_BUTTON))
        {
            // Close the editor dialog
            closeDialog();
        }
    }

    /**********************************************************************************************
     * Set up the combo box containing the available field input input types for display in the
     * table's Input Type cells
     *********************************************************************************************/
    private void setUpInputTypeColumn()
    {
        // Step through each column in the editor
        for (inputTypeIndex = 0; inputTypeIndex < fieldTable.getColumnCount(); inputTypeIndex++)
        {
            // Check if the column name matches that for the field input type
            if (fieldTable.getColumnName(inputTypeIndex).equals(FieldEditorColumnInfo.INPUT_TYPE.getColumnName()))
            {
                // Stop searching
                break;
            }
        }

        // Create a combo box for displaying field input types
        inputTypeCbox = new PaddedComboBox(inputTypeHandler.getInputTypeNames(),
                                           inputTypeHandler.getDescriptions(),
                                           ModifiableFontInfo.DATA_TABLE_CELL.getFont());

        // Set the column table editor to the combo box
        fieldTable.getColumnModel().getColumn(inputTypeIndex).setCellEditor(new ComboBoxCellEditor(inputTypeCbox));

        // Enable item matching for the combo box
        inputTypeCbox.enableItemMatching(fieldTable);

        // Set the default selected type
        inputTypeCbox.setSelectedItem(DefaultInputType.TEXT.getInputName());
    }

    /**********************************************************************************************
     * Set up the combo box containing the available field applicability types for display in the
     * table's Applicability cells
     *********************************************************************************************/
    private void setUpApplicabilityColumn()
    {
        // Check if the applicability column is to be displayed
        if (includeApplicability)
        {
            int applicabilityIndex;

            // Step through each column in the editor
            for (applicabilityIndex = 0; applicabilityIndex < fieldTable.getColumnCount(); applicabilityIndex++)
            {
                // Check if the column name matches that for the field applicability
                if (fieldTable.getColumnName(applicabilityIndex).equals(FieldEditorColumnInfo.APPLICABILITY.getColumnName()))
                {
                    // Stop searching
                    break;
                }
            }

            // Create a combo box for displaying field applicability types
            applicabilityCBox = new PaddedComboBox(ApplicabilityType.getApplicabilityNames(),
                                                   ModifiableFontInfo.DATA_TABLE_CELL.getFont());

            // Set the column table editor to the combo box
            fieldTable.getColumnModel().getColumn(applicabilityIndex).setCellEditor(new ComboBoxCellEditor(applicabilityCBox));

            // Set the default selected type
            applicabilityCBox.setSelectedItem(ApplicabilityType.ALL.getApplicabilityName());
        }
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
        for (int row = 0; row < fieldTable.getRowCount() && !stopCheck; row++)
        {
            // Skip rows in the table that are empty
            row = fieldTable.getNextPopulatedRowNumber(row);

            // Check that the end of the table hasn't been reached
            if (row < fieldTable.getRowCount())
            {
                // Step through each column in the row
                for (int column = 0; column < fieldTable.getColumnCount() && !stopCheck; column++)
                {
                    // Check if the cell is required and is empty
                    if (FieldEditorColumnInfo.values()[column].isRequired()
                        && fieldTable.getValueAt(row, column).toString().isEmpty())
                    {
                        // Set the 'data is missing' flag
                        dataIsMissing = true;

                        // Inform the user that a row is missing required data. If Cancel is
                        // selected then do not perform checks on other columns and rows
                        if (new CcddDialogHandler().showMessageDialog(CcddFieldEditorDialog.this,
                                                                      "<html><b>Data must be provided for column '</b>"
                                                                                                  + fieldTable.getColumnName(column)
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
