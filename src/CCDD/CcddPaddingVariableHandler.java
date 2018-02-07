/**
 * CFS Command & Data Dictionary padding variable handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PADDING_ALIGNMENT;
import static CCDD.CcddConstants.PAD_DATA_TYPE;
import static CCDD.CcddConstants.PAD_VARIABLE;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.STATUS_MSG;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.BitPackRowIndex;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary padding variable handler class
 *************************************************************************************************/
public class CcddPaddingVariableHandler
{
    // Class references
    private CcddMain ccddMain;
    private CcddDbTableCommandHandler dbTable;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddEventLogDialog eventLog;

    // Component referenced by multiple methods
    private JProgressBar progBar;

    // Variable padding byte alignment value
    private int byteAlignment;

    // List containing the variable padding information for each structure table
    private List<StructurePaddingHandler> paddingInformation;

    // Flag indicating that the user elected to cancel padding adjustment
    boolean canceled;

    /**********************************************************************************************
     * Structure padding handler class
     *********************************************************************************************/
    class StructurePaddingHandler
    {
        // Structure table's information
        private final TableInformation tableInfo;

        // Structure table editor
        private CcddTableEditorHandler tableEditor;

        // Structure name
        private String structureName;

        // Structure table column indices
        private int varNameColumn;
        private int dataTypeColumn;
        private int arraySizeColumn;
        private int bitLengthColumn;
        private int largestDataType;
        private List<Integer> rateColumn;

        // Structure's total size in bytes
        private int totalSize;

        // Counter used for creating unique padding variable names within the structure
        private int padCounter;

        // Running total of bytes processed in a structure while adding padding variables, limited
        // to be within the byte alignment value
        private int byteCount;

        // Flag indicating if the structure's total size and largest member variable have been
        // calculated
        private boolean isSizesCalculated;

        /******************************************************************************************
         * Structure padding handler class constructor
         *
         * @param structureName
         *            prototype structure table name
         *****************************************************************************************/
        StructurePaddingHandler(String structureName)
        {
            // Load the prototype structure table data from the project database
            tableInfo = dbTable.loadTableData(structureName,
                                              false,
                                              false,
                                              false,
                                              false,
                                              ccddMain.getMainFrame());

            // Check that the table loaded successfully
            if (!tableInfo.isErrorFlag())
            {
                this.structureName = structureName;
                boolean isOpen = false;

                // Step through each open table editor dialog
                for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                {
                    // Step through each individual editor
                    for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                    {
                        // Check if the editor is for this structure table
                        if (editor.getOwnerName().equals(structureName))
                        {
                            // Replace the table's contents with the committed data. This
                            // eliminates for any uncommitted changes
                            editor.getTable().loadDataArrayIntoTable(editor.getCommittedTableInformation().getData(),
                                                                     false);

                            editor.getTable().getUndoManager().discardAllEdits();
                            isOpen = true;
                            break;
                        }
                    }

                    // Check if the table's editor exists
                    if (isOpen)
                    {
                        // Stop searching
                        break;
                    }
                }

                // Create a table editor handler for the structure table
                tableEditor = new CcddTableEditorHandler(ccddMain, tableInfo, null);

                // Check if the arrays aren't expanded
                if (!tableEditor.isExpanded())
                {
                    // Expand the array members
                    tableEditor.showHideArrayMembers();
                }

                // Get the a reference to the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableEditor.getTableInformation().getType());

                // Get the column indices for the variable name, data type, array size, and bit
                // length
                varNameColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                dataTypeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                arraySizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                bitLengthColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                rateColumn = typeDefn.getColumnIndicesByInputType(InputDataType.RATE);

                // Set the flag indicating that the structure's total size and largest member
                // variable haven't been calculated
                isSizesCalculated = false;
            }
        }

        /******************************************************************************************
         * Check if the table loaded successfully
         *
         * @return true if the table successfully loaded
         *****************************************************************************************/
        protected boolean isLoaded()
        {
            return !tableInfo.isErrorFlag();
        }

        /******************************************************************************************
         * Get the variable name for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Variable name for the specified structure table row
         *****************************************************************************************/
        protected String getVariableName(int row)
        {
            return tableEditor.getExpandedValueAt(row, varNameColumn).toString();
        }

        /******************************************************************************************
         * Get the data type for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Data type for the specified structure table row
         *****************************************************************************************/
        protected String getDataType(int row)
        {
            return tableEditor.getTable().getModel().getValueAt(row, dataTypeColumn).toString();
        }

        /******************************************************************************************
         * Get the array size for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Array size for the specified structure table row
         *****************************************************************************************/
        protected String getArraySize(int row)
        {
            return tableEditor.getExpandedValueAt(row, arraySizeColumn).toString();
        }

        /******************************************************************************************
         * Get the bit length for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Bit length for the specified structure table row
         *****************************************************************************************/
        protected String getBitLength(int row)
        {
            return tableEditor.getExpandedValueAt(row, bitLengthColumn).toString();
        }

        /******************************************************************************************
         * Get the rate for the specified structure table row and rate column
         *
         * @param row
         *            structure table row index
         *
         * @param column
         *            structure table rate column index
         *
         * @return Rate for the specified structure table row and rate column
         *****************************************************************************************/
        protected String getRate(int row, int column)
        {
            return tableEditor.getTable().getModel().getValueAt(row, column).toString();
        }

        /******************************************************************************************
         * Set the rate for the specified structure table row and rate column
         *
         * @param rate
         *            rate for the specified structure table row and rate column
         *
         * @param row
         *            structure table row index
         *
         * @param column
         *            structure table rate column index
         *****************************************************************************************/
        protected void setRate(String rate, int row, int column)
        {
            tableEditor.getTable().getModel().setValueAt(rate, row, column);
        }

        /******************************************************************************************
         * Remove any existing padding variable(s) from the structure table
         *****************************************************************************************/
        protected void removePadding()
        {
            List<Integer> removedRows = new ArrayList<Integer>();

            // Step through each row in the table
            for (int row = 0; row < tableEditor.getTable().getModel().getRowCount(); row++)
            {
                // Check if this row contains a padding variable
                if (getVariableName(row).matches(PAD_VARIABLE + "[0-9]+$")
                    && (getDataType(row).equals(PAD_DATA_TYPE)
                        || !getBitLength(row).isEmpty()))
                {
                    // Add the row index to the list of those to be removed
                    removedRows.add(row);
                }
            }

            // Check if any rows are to be removed
            if (!removedRows.isEmpty())
            {
                // Create an array to hold the row indices from the list
                int[] removedRowsArray = new int[removedRows.size()];

                // Step through each row index to be removed
                for (int row = 0; row < removedRows.size(); row++)
                {
                    // Store the index in the array of rows to be removed
                    removedRowsArray[row] = removedRows.get(row);
                }

                // Remove the row(s) from the table
                tableEditor.getTable().removeRows(removedRowsArray);
            }
        }

        /******************************************************************************************
         * Add padding variables as needed to the structure table
         *****************************************************************************************/
        private void addPadding()
        {
            padCounter = 0;
            byteCount = 0;
            BitPackRowIndex packIndex = null;
            int numPads;

            // Step through each row in the table
            for (int row = 0; row < tableEditor.getTable().getModel().getRowCount(); row++)
            {
                // Extract the variable's data type
                String dataType = getDataType(row);

                int variableSize = 0;
                numPads = 0;

                // Check if the data type is a primitive
                if (dataTypeHandler.isPrimitive(dataType))
                {
                    // Extract the variable's data type and array size
                    String variableName = getVariableName(row);
                    String arraySize = getArraySize(row);

                    // Check if the variable isn't an array definition
                    if (arraySize.isEmpty() || ArrayVariable.isArrayMember(variableName))
                    {
                        // Check if the variable has a bit length
                        if (!getBitLength(row).isEmpty())
                        {
                            // Get the start and stop row indices for any subsequent variables that
                            // are bit-packed with this variable
                            packIndex = tableEditor.getPackedVariables(tableEditor.getTable().getTableDataList(false),
                                                                       row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the variable in bytes
                        variableSize = dataTypeHandler.getSizeInBytes(dataType);

                        // Check if the variable isn't already positioned on the alignment point.
                        // This also accounts for variables with a size greater than the alignment
                        // value
                        if (byteCount != 0
                            && variableSize != 1
                            && (variableSize + byteCount) % byteAlignment != 0)
                        {
                            // Check if the variable doesn't fit within the remaining bytes
                            if (variableSize + byteCount > byteAlignment)
                            {
                                // Calculate the number of padding variables needed to align the
                                // variable to the next alignment value
                                numPads = byteAlignment - byteCount;
                            }
                            // The variable doesn't exceed the next alignment point
                            else
                            {
                                // Calculate the number of padding variables needed to align the
                                // variable within the current alignment value
                                numPads = (byteAlignment - byteCount) % variableSize;
                            }

                            // Add the padding variable(s). If this is an array member then adjust
                            // the insertion row index to account for the array definition row
                            row = addPaddingVariable(row +
                                                     (arraySize.isEmpty()
                                                                          ? 0
                                                                          : -1),
                                                     numPads,
                                                     0);
                        }

                        // Check if this is a bit-wise variable
                        if (packIndex != null)
                        {
                            int bitCount = 0;

                            // Step through each of the bit-wise variables that are packed together
                            // (this includes single, unpacked bit-wise variables). Adjust the
                            // start and stop to account for any padding added above
                            for (int bitRow = packIndex.getFirstIndex() + numPads; bitRow <= packIndex.getLastIndex() + numPads; bitRow++)
                            {
                                // Add the variable's number of bits to the pack count
                                bitCount += Integer.valueOf(getBitLength(bitRow));
                            }

                            // Calculate the number of unused bits in the packed variables
                            int numPadBits = dataTypeHandler.getSizeInBits(dataType) - bitCount;

                            // Check if there are any unused bits in the packed variable
                            if (numPadBits != 0)
                            {
                                // Get the row at which to insert the bit-pack padding variable
                                int insertRow = packIndex.getLastIndex() + numPads + 1;

                                // Add a padding variable to fill in the unused bits
                                addPaddingVariable(insertRow, 1, numPadBits);

                                // Step through each of the bit-packed variable's rate columns
                                for (int column : rateColumn)
                                {
                                    // Assign the rate to the padding variable
                                    setRate(getRate(row, column), insertRow, column);
                                }
                            }

                            // Update the row index to skip the other variables packed with the
                            // current one
                            row += packIndex.getLastIndex() - packIndex.getFirstIndex() + 1;
                        }

                        // Add the size of the variable to the byte counter, then adjust the byte
                        // count if it equals or exceeds the alignment point
                        byteCount = (byteCount + variableSize) % byteAlignment;
                    }
                }
                // The variable's data type is a structure
                else
                {
                    // Step through each successfully loaded table
                    for (StructurePaddingHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data type
                        if (dataType.equals(childPadInfo.structureName))
                        {
                            // Calculate the number of padding variables needed to align the child
                            // structure variable
                            numPads = (byteAlignment - byteCount) % childPadInfo.largestDataType;

                            // Add the padding variable(s), if needed
                            row = addPaddingVariable(row, numPads, 0);

                            // Stop searching since the matching table was found
                            break;
                        }
                    }

                    // Reset the byte count since every structure is padded to the alignment
                    // boundary
                    byteCount = 0;
                }
            }

            // Check if the byte count ended on the alignment boundary
            if (byteCount == 0)
            {
                // Set the byte count equal to the alignment value so that the calculation below
                // produces the correct result
                byteCount = byteAlignment;
            }

            // Calculate the number of padding variables needed to fill out the structure to the
            // alignment point
            numPads = (byteAlignment - byteCount) % (byteAlignment * largestDataType);

            // Add the padding variable(s), if needed
            addPaddingVariable(tableEditor.getTable().getModel().getRowCount(), numPads, 0);
        }

        /******************************************************************************************
         * Add a padding variable at the specified row index
         *
         * @param row
         *            structure table row index at which to insert the padding variable(s)
         *
         * @param padSize
         *            number of padding variables to insert
         *
         * @param numPadBits
         *            number of bits to assign to the padding variable to fill in a sequence of
         *            bit-packed variables. 0 if the padding variable isn't associated with
         *            bit-packed variables
         *
         * @return Row index below the added padding variable(s)
         *****************************************************************************************/
        private int addPaddingVariable(int row, int padSize, int numPadBits)
        {
            // Check if any padding is needed
            if (padSize > 0)
            {
                // Create an empty row array and set the padding variable name
                Object[] rowData = tableEditor.getTable().getEmptyRow();
                rowData[varNameColumn] = PAD_VARIABLE + padCounter;

                // Check if the padding variable is not for filling up one or more bit-packed
                // variables
                if (numPadBits == 0)
                {
                    // Set the padding variable data type
                    rowData[dataTypeColumn] = PAD_DATA_TYPE;
                }
                // Padding is added to fill up one or more packed variables
                else
                {
                    // Set the padding variable data type to match that of the bit-wise variable(s)
                    // and set the bit length to the number of bits needed to fill up the packing
                    rowData[dataTypeColumn] = getDataType(row - 1);
                    rowData[bitLengthColumn] = String.valueOf(numPadBits);
                }

                // Check if multiple padding variables are to be added
                if (padSize > 1)
                {
                    // Set the array size value to match the number of padding variables needed
                    rowData[arraySizeColumn] = String.valueOf(padSize);
                }

                // Insert the padding variable row into the table
                tableEditor.getTable().insertRowData(row - 1, rowData);

                // Check if multiple padding variables are to be added
                if (padSize > 1)
                {
                    // Get the table data as a list
                    List<Object[]> tableData = tableEditor.getTable().getTableDataList(false);

                    // Add the padding variable array members
                    tableEditor.adjustArrayMember(tableData,
                                                  new int[] {0},
                                                  new int[] {padSize},
                                                  row,
                                                  arraySizeColumn);

                    // Load the array of data into the table to reflect the added array members
                    tableEditor.getTable().loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                                                  false);

                    // Account for the array definition row
                    row++;
                }

                // Adjust the row index past the padding variables
                row += padSize;

                // Update the padding name counter
                padCounter++;

                // Check if the padding variable is not for filling up one or more bit-packed
                // variables. The byte count is unaffected when adding padding for bit-packing
                if (numPadBits == 0)
                {
                    // Update the byte count to account for the number of added bytes
                    byteCount += padSize;
                }
            }

            return row;
        }

        /******************************************************************************************
         * Update the structure table padding variables in the project database
         *****************************************************************************************/
        protected void updateTable()
        {
            // Build the table updates
            tableEditor.buildUpdates();

            // Check if any updates were made (padding variables added or deleted)
            if (!tableEditor.getAdditions().isEmpty() || !tableEditor.getDeletions().isEmpty())
            {
                // Update the table in the database
                dbTable.modifyTableData(tableInfo,
                                        tableEditor.getAdditions(),
                                        tableEditor.getModifications(),
                                        tableEditor.getDeletions(),
                                        false,
                                        false,
                                        false,
                                        false,
                                        false,
                                        null,
                                        ccddMain.getMainFrame());
            }
        }
    }

    /**********************************************************************************************
     * Padding variable handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param addPadding
     *            true to add or update the variable padding; false to remove the padding variables
     *********************************************************************************************/
    CcddPaddingVariableHandler(final CcddMain ccddMain, final boolean addPadding)
    {
        // Check if there are uncommitted changes and if so, confirm discarding the changes before
        // proceeding
        if (ccddMain.ignoreUncommittedChanges("Alter Padding",
                                              "Discard changes?",
                                              false,
                                              null,
                                              ccddMain.getMainFrame()))
        {
            this.ccddMain = ccddMain;
            this.eventLog = ccddMain.getSessionEventLog();

            // Execute the commands to add/update/remove the padding variables in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                /**********************************************************************************
                 * Padding adjustment progress/cancellation dialog class
                 *********************************************************************************/
                @SuppressWarnings("serial")
                class HaltDialog extends CcddDialogHandler
                {
                    /******************************************************************************
                     * Handle the close dialog button action
                     *****************************************************************************/
                    @Override
                    protected void closeDialog(int button)
                    {
                        // Set the flag to cancel padding adjustment
                        canceled = true;

                        super.closeDialog(button);
                    };
                }

                HaltDialog cancelDialog = new HaltDialog();

                /**********************************************************************************
                 * Update structure table padding variables command
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    paddingInformation = new ArrayList<StructurePaddingHandler>();
                    int progress = 0;

                    dbTable = ccddMain.getDbTableCommandHandler();
                    tableTypeHandler = ccddMain.getTableTypeHandler();
                    dataTypeHandler = ccddMain.getDataTypeHandler();

                    // Get an array of the prototype structure names
                    String[] prototStructTables = dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE);

                    // Set the initial layout manager characteristics
                    GridBagConstraints gbc = new GridBagConstraints(0,
                                                                    0,
                                                                    1,
                                                                    1,
                                                                    1.0,
                                                                    0.0,
                                                                    GridBagConstraints.LINE_START,
                                                                    GridBagConstraints.BOTH,
                                                                    new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                               ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                               0,
                                                                               ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                                    0,
                                                                    0);

                    // Create the progress/cancellation dialog
                    JPanel dialogPnl = new JPanel(new GridBagLayout());
                    dialogPnl.setBorder(BorderFactory.createEmptyBorder());
                    JLabel textLbl = new JLabel("<html><b>Load tables and remove padding...<br><br>",
                                                SwingConstants.LEFT);
                    textLbl.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
                    gbc.gridy++;
                    dialogPnl.add(textLbl, gbc);
                    JLabel textLbl2 = new JLabel("<html><b>"
                                                 + CcddUtilities.colorHTMLText("*** Press </i>Halt<i> "
                                                                               + "to terminate padding adjustment ***",
                                                                               Color.RED)
                                                 + "</b><br><br>",
                                                 SwingConstants.CENTER);
                    textLbl2.setFont(ModifiableFontInfo.LABEL_PLAIN.getFont());
                    gbc.gridy++;
                    dialogPnl.add(textLbl2, gbc);

                    // Add a progress bar to the dialog
                    progBar = new JProgressBar(0,
                                               prototStructTables.length
                                                  * (addPadding
                                                                ? 4
                                                                : 2));
                    progBar.setValue(0);
                    progBar.setStringPainted(true);
                    progBar.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
                    gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
                    gbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing() * 2;
                    gbc.insets.bottom = 0;
                    gbc.gridy++;
                    dialogPnl.add(progBar, gbc);

                    // Display the padding adjustment progress/cancellation dialog
                    cancelDialog.showOptionsDialog(ccddMain.getMainFrame(),
                                                   dialogPnl,
                                                   (addPadding
                                                               ? "Adding/updating"
                                                               : "Removing")
                                                              + " padding",
                                                   DialogOption.HALT_OPTION,
                                                   false,
                                                   false);

                    // Get the current byte alignment value
                    byteAlignment = Integer.valueOf(ccddMain.getProgPrefs().get(PADDING_ALIGNMENT, "4"));

                    // Step through each prototype structure table
                    for (String protoStruct : prototStructTables)
                    {
                        // Check if the user canceled padding adjustment
                        if (canceled)
                        {
                            break;
                        }

                        // Load the table's data
                        StructurePaddingHandler paddingInfo = new StructurePaddingHandler(protoStruct);

                        // Update the progress bar text
                        progBar.setString(paddingInfo.structureName);

                        // Check if the table loaded successfully
                        if (paddingInfo.isLoaded())
                        {
                            // Add the table's padding information to the list
                            paddingInformation.add(paddingInfo);

                            // Remove any existing padding variables from the table
                            paddingInfo.removePadding();
                        }

                        // Update the padding progress
                        progBar.setValue(progress);
                        progress++;
                    }

                    // Check if padding variables should be added
                    if (addPadding)
                    {
                        // Change the dialog text to indicate the new padding phase
                        textLbl.setText("<html><b>Determine structure sizes...</b><br><br>");

                        // Step through each successfully loaded table
                        for (StructurePaddingHandler paddingInfo : paddingInformation)
                        {
                            // Check if the user canceled padding adjustment
                            if (canceled)
                            {
                                break;
                            }

                            // Update the progress bar text
                            progBar.setString(paddingInfo.structureName);

                            // Find largest primitive data type referenced in this table's
                            // hierarchy, including those in any child structures, and the
                            // structure's total size
                            setStructureSizes(paddingInfo);

                            // Update the padding progress
                            progBar.setValue(progress);
                            progress++;
                        }

                        // Change the dialog text to indicate the new padding phase
                        textLbl.setText("<html><b>Add padding...</b><br><br>");

                        // Step through each successfully loaded table
                        for (StructurePaddingHandler paddingInfo : paddingInformation)
                        {
                            // Check if the user canceled padding adjustment
                            if (canceled)
                            {
                                break;
                            }

                            // Update the progress bar text
                            progBar.setString(paddingInfo.structureName);

                            // Check if the structure contains a variable with a non-zero size
                            if (paddingInfo.largestDataType != 0)
                            {
                                // Add any padding variables to the table needed to align the
                                // variables
                                paddingInfo.addPadding();
                            }

                            // Update the padding progress
                            progBar.setValue(progress);
                            progress++;
                        }
                    }

                    // Change the dialog text to indicate the new padding phase
                    textLbl.setText("<html><b>Update project database...</b><br><br>");

                    // Step through each successfully loaded table
                    for (StructurePaddingHandler paddingInfo : paddingInformation)
                    {
                        // Check if the user canceled padding adjustment
                        if (canceled)
                        {
                            break;
                        }

                        // Update the progress bar text
                        progBar.setString(paddingInfo.structureName);

                        // Update the table in the project database
                        paddingInfo.updateTable();

                        // Update the padding progress
                        progBar.setValue(progress);
                        progress++;
                    }

                    // Close the progress/cancellation dialog
                    cancelDialog.closeDialog();

                    // Add a log entry indication the padding adjustment completed
                    eventLog.logEvent(STATUS_MSG,
                                      (addPadding
                                                  ? "Adding/updating"
                                                  : "Removing") +
                                                  " padding variables "
                                                  + (canceled
                                                              ? "terminated by user"
                                                              : "complete"));
                }
            });
        }
    }

    /**********************************************************************************************
     * Determine the total size of a structure and the largest data type within it
     *
     * @param paddingInformation
     *            list containing the padding variable information for every structure table
     *********************************************************************************************/
    protected void setStructureSizes(StructurePaddingHandler padInfo)
    {
        // Check if the sizes for this structure haven't already been calculated
        if (!padInfo.isSizesCalculated)
        {
            // Step through each row in the table
            for (int row = 0; row < padInfo.tableEditor.getTable().getModel().getRowCount(); row++)
            {
                // Extract the variable's data type
                String dataType = padInfo.getDataType(row);

                // Check if the data type is a primitive
                if (dataTypeHandler.isPrimitive(dataType))
                {
                    // Extract the variable's data type and array size
                    String variable = padInfo.getVariableName(row);
                    String arraySize = padInfo.getArraySize(row);

                    // Check if the variable isn't an array definition
                    if (arraySize.isEmpty() || ArrayVariable.isArrayMember(variable))
                    {
                        BitPackRowIndex packIndex = null;

                        // Check if the variable has a bit length
                        if (!padInfo.getBitLength(row).isEmpty())
                        {
                            // Get the start and stop row indices for any subsequent variables that
                            // are bit-packed with this variable
                            packIndex = padInfo.tableEditor.getPackedVariables(padInfo.tableEditor.getTable().getTableDataList(false), row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the primitive data type in bytes
                        int size = dataTypeHandler.getSizeInBytes(dataType);

                        // Update the largest data type. Limit the size to no greater than the byte
                        // alignment value
                        padInfo.largestDataType = Math.min(byteAlignment,
                                                           Math.max(size,
                                                                    padInfo.largestDataType));

                        // Update the total size
                        padInfo.totalSize += size;

                        // Check if the variable is bit-packed with one or more subsequent
                        // variables
                        if (packIndex != null
                            && packIndex.getFirstIndex() != packIndex.getLastIndex())
                        {
                            // Adjust the tree index to skip the other pack members
                            row = packIndex.getLastIndex();
                        }
                    }
                }
                // The data type isn't a primitive; i.e., it's a structure reference
                else
                {
                    // Step through each successfully loaded table
                    for (StructurePaddingHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data type
                        if (dataType.equals(childPadInfo.structureName))
                        {
                            // Set the sizes for this structure (if not already calculated)
                            setStructureSizes(childPadInfo);

                            // Update the largest data type
                            padInfo.largestDataType = Math.max(padInfo.largestDataType,
                                                               childPadInfo.largestDataType);

                            // Add the size of the child structure to this structure
                            padInfo.totalSize += childPadInfo.totalSize;

                            // Stop searching since a match was found
                            break;
                        }
                    }
                }
            }

            // Round up the total structure size to the next alignment point (padding variables
            // will be added as needed to meet this size)
            padInfo.totalSize += byteAlignment - (padInfo.totalSize % byteAlignment);

            // Set the flag indicating this structure's sizes are calculated
            padInfo.isSizesCalculated = true;
        }
    }
}
