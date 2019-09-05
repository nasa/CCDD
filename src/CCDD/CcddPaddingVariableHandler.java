/**
 * CFS Command and Data Dictionary padding variable handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PAD_DATA_TYPE;
import static CCDD.CcddConstants.PAD_VARIABLE;
import static CCDD.CcddConstants.PAD_VARIABLE_MATCH;
import static CCDD.CcddConstants.EventLogMessageType.STATUS_MSG;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import CCDD.CcddBackgroundCommand.BackgroundCommand;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.BitPackRowIndex;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.PadOperationType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary padding variable handler class
 *************************************************************************************************/
public class CcddPaddingVariableHandler
{
    // Class references
    private CcddMain ccddMain;
    private CcddDbTableCommandHandler dbTable;
    private CcddDataTypeHandler dataTypeHandler;
    private CcddEventLogDialog eventLog;
    private CcddHaltDialog haltDlg;

    // List containing the variable padding information for each structure table
    private List<StructurePaddingHandler> paddingInformation;

    /**********************************************************************************************
     * Structure padding handler class
     *********************************************************************************************/
    private class StructurePaddingHandler
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
        private List<Integer> rateColumn;

        // Size in bytes of the largest element in a structure
        private int largestDataType;

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
                TypeDefinition typeDefn = tableEditor.getTableTypeDefinition();

                // Get the column indices for the variable name, data type, array size, and bit
                // length
                varNameColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
                dataTypeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
                arraySizeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
                bitLengthColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);
                rateColumn = typeDefn.getColumnIndicesByInputType(DefaultInputType.RATE);

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
                if (getVariableName(row).matches(PAD_VARIABLE_MATCH)
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
                            && (variableSize + byteCount) % largestDataType != 0)
                        {
                            // Check if the variable doesn't fit within the remaining bytes
                            if (variableSize + byteCount > largestDataType)
                            {
                                // Calculate the number of padding variables needed to align the
                                // variable to the next alignment value
                                numPads = largestDataType - byteCount;
                            }
                            // The variable doesn't exceed the next alignment point
                            else
                            {
                                // Calculate the number of padding variables needed to align the
                                // variable within the current alignment value
                                numPads = (largestDataType - byteCount) % variableSize;
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
                        byteCount = (byteCount + variableSize) % largestDataType;
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
                            // Check if the child has any variables
                            if (childPadInfo.largestDataType != 0)
                            {
                                // Calculate the number of padding variables needed to align the
                                // child structure variable
                                numPads = (largestDataType - byteCount) % childPadInfo.largestDataType;

                                // Add the padding variable(s), if needed
                                row = addPaddingVariable(row, numPads, 0);
                            }

                            // Set the byte count to the size of the child structure so that
                            // subsequent variables account for this when aligning
                            byteCount = childPadInfo.totalSize;

                            // Stop searching since the matching table was found
                            break;
                        }
                    }
                }
            }

            // Check if the byte count ended on the alignment boundary
            if (byteCount == 0)
            {
                // Set the byte count equal to the alignment value so that the calculation below
                // produces the correct result
                byteCount = largestDataType;
            }

            // Calculate the number of padding variables needed to fill out the structure to the
            // alignment point
            numPads = (largestDataType - byteCount) % (largestDataType * largestDataType);

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
                rowData[varNameColumn] = PAD_VARIABLE.replaceFirst(Pattern.quote("[0-9]+"),
                                                                   String.valueOf(padCounter));

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
     * @param padOperation
     *            PadOperationType: ADD_UPDATE to add or update the variable padding; REMOVE to
     *            remove the padding variables
     *
     * @param selectedProtoStructTables
     *            list of the prototype table names that will have the padding altered
     *
     * @param referencedProtoStructTables
     *            list of the prototype table names that are referenced by the selected tables
     *
     * @param parent
     *            GUI component on which to center the Halt dialog
     *********************************************************************************************/
    CcddPaddingVariableHandler(final CcddMain ccddMain,
                               final PadOperationType padOperation,
                               final List<String> selectedProtoStructTables,
                               final List<String> referencedProtoStructTables,
                               final Component parent)
    {
        // Check if there are uncommitted changes and if so, confirm discarding the changes before
        // proceeding
        if (ccddMain.ignoreUncommittedChanges("Alter Padding",
                                              "Discard changes?",
                                              false,
                                              null,
                                              parent))
        {
            this.ccddMain = ccddMain;
            this.eventLog = ccddMain.getSessionEventLog();

            // Execute the commands to add/update/remove the padding variables in the background
            CcddBackgroundCommand.executeInBackground(ccddMain, new BackgroundCommand()
            {
                /**********************************************************************************
                 * Update structure table padding variables command
                 *********************************************************************************/
                @Override
                protected void execute()
                {
                    paddingInformation = new ArrayList<StructurePaddingHandler>();

                    // Get references to shorten subsequent calls
                    dbTable = ccddMain.getDbTableCommandHandler();
                    dataTypeHandler = ccddMain.getDataTypeHandler();

                    // Create the padding adjustment cancellation dialog
                    haltDlg = new CcddHaltDialog((padOperation == PadOperationType.ADD_UPDATE
                                                                                              ? "Adding/updating"
                                                                                              : "Removing"),
                                                 "Loading prototype tables",
                                                 "padding adjustment",
                                                 referencedProtoStructTables.size()
                                                                       * (padOperation == PadOperationType.ADD_UPDATE
                                                                                                                      ? 2
                                                                                                                      : 1)
                                                                       + selectedProtoStructTables.size()
                                                                         * (padOperation == PadOperationType.ADD_UPDATE
                                                                                                                        ? 3
                                                                                                                        : 2),
                                                 1,
                                                 parent);

                    // Step through each referenced prototype structure table
                    for (String protoStruct : referencedProtoStructTables)
                    {
                        // Check if the user canceled padding adjustment
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Load the table's data
                        StructurePaddingHandler paddingInfo = new StructurePaddingHandler(protoStruct);

                        // Update the progress bar text
                        haltDlg.updateProgressBar(paddingInfo.structureName, -1);

                        // Check if the table loaded successfully
                        if (paddingInfo.isLoaded())
                        {
                            // Add the table's padding information to the list
                            paddingInformation.add(paddingInfo);

                            // Check if this table is selected for having its padding altered
                            if (selectedProtoStructTables.contains(paddingInfo.structureName))
                            {
                                // Remove any existing padding variables from the table
                                paddingInfo.removePadding();
                            }
                        }
                    }

                    // Change the dialog text to indicate the new padding phase
                    haltDlg.setLabel("Removing existing padding");

                    // Step through each successfully loaded table
                    for (StructurePaddingHandler paddingInfo : paddingInformation)
                    {
                        // Check if the user canceled padding adjustment
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Check if this table is selected for having its padding altered
                        if (selectedProtoStructTables.contains(paddingInfo.structureName))
                        {
                            // Update the progress bar text
                            haltDlg.updateProgressBar(paddingInfo.structureName, -1);

                            // Remove any existing padding variables from the table
                            paddingInfo.removePadding();
                        }
                    }

                    // Check if padding variables should be added
                    if (padOperation == PadOperationType.ADD_UPDATE)
                    {
                        // Change the dialog text to indicate the new padding phase
                        haltDlg.setLabel("Determining structure sizes");

                        // Step through each successfully loaded table
                        for (StructurePaddingHandler paddingInfo : paddingInformation)
                        {
                            // Check if the user canceled padding adjustment
                            if (haltDlg.isHalted())
                            {
                                break;
                            }

                            // Update the progress bar text
                            haltDlg.updateProgressBar(paddingInfo.structureName, -1);

                            // Find largest primitive data type referenced in this table's
                            // hierarchy, including those in any child structures, and the
                            // structure's total size
                            setStructureSizes(paddingInfo);
                        }

                        // Change the dialog text to indicate the new padding phase
                        haltDlg.setLabel("Adding padding");

                        // Step through each successfully loaded table
                        for (StructurePaddingHandler paddingInfo : paddingInformation)
                        {
                            // Check if the user canceled padding adjustment
                            if (haltDlg.isHalted())
                            {
                                break;
                            }

                            // Check if this table is selected for having its padding altered
                            if (selectedProtoStructTables.contains(paddingInfo.structureName))
                            {
                                // Update the progress bar text
                                haltDlg.updateProgressBar(paddingInfo.structureName, -1);

                                // Check if the structure contains a variable with a non-zero size
                                if (paddingInfo.largestDataType != 0)
                                {
                                    // Add any padding variables to the table needed to align the
                                    // variables
                                    paddingInfo.addPadding();
                                }
                            }
                        }
                    }

                    // Change the dialog text to indicate the new padding phase
                    haltDlg.setLabel("Updating project database");

                    // Step through each successfully loaded table
                    for (StructurePaddingHandler paddingInfo : paddingInformation)
                    {
                        // Check if the user canceled padding adjustment
                        if (haltDlg.isHalted())
                        {
                            break;
                        }

                        // Check if this table is selected for having its padding altered
                        if (selectedProtoStructTables.contains(paddingInfo.structureName))
                        {
                            // Update the progress bar text
                            haltDlg.updateProgressBar(paddingInfo.structureName, -1);

                            // Update the table in the project database
                            paddingInfo.updateTable();
                        }
                    }
                }

                /**********************************************************************************
                 * Padding adjustment complete
                 *********************************************************************************/
                @Override
                protected void complete()
                {
                    // Check if the user didn't cancel padding adjustment
                    if (!haltDlg.isHalted())
                    {
                        // Add a log entry indication the padding adjustment completed
                        eventLog.logEvent(STATUS_MSG,
                                          (padOperation == PadOperationType.ADD_UPDATE
                                                                                       ? "Adding/updating"
                                                                                       : "Removing")
                                                      + " padding variables completed");
                        // Close the cancellation dialog
                        haltDlg.closeDialog();
                    }
                    // Padding adjustment was canceled
                    else
                    {
                        eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                                          (padOperation == PadOperationType.ADD_UPDATE
                                                                                       ? "Adding/updating"
                                                                                       : "Removing")
                                                                          + " padding terminated by user");
                    }

                    haltDlg = null;
                }
            });
        }
    }

    /**********************************************************************************************
     * Determine the total size of a structure and the largest data type within it
     *
     * @param padInfo
     *            list containing the padding variable information for every structure table
     *********************************************************************************************/
    private void setStructureSizes(StructurePaddingHandler padInfo)
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
                            packIndex = padInfo.tableEditor
                                                           .getPackedVariables(padInfo.tableEditor
                                                                                                  .getTable()
                                                                                                  .getTableDataList(false),
                                                                               row);
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
                        padInfo.largestDataType = Math.max(size, padInfo.largestDataType);

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

            // Check if the structure has any non-zero size elements and does not end on the
            // alignment point
            if (padInfo.largestDataType != 0
                && (padInfo.totalSize % padInfo.largestDataType) != 0)
            {
                // Round up the total structure size to the next alignment point (padding variables
                // will be added as needed to meet this size)
                padInfo.totalSize += padInfo.largestDataType -
                                     (padInfo.totalSize % padInfo.largestDataType);
            }

            // Set the flag indicating this structure's sizes are calculated
            padInfo.isSizesCalculated = true;
        }
    }
}
