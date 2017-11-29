/**
 * CFS Command & Data Dictionary padding variable handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PADDING_ALIGNMENT;
import static CCDD.CcddConstants.PAD_DATA_TYPE;
import static CCDD.CcddConstants.PAD_VARIABLE;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.BitPackRowIndex;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary padding variable handler class
 *****************************************************************************/
public class CcddPaddingVariableHandler
{
    // Class references
    private CcddMain ccddMain;
    private CcddDbTableCommandHandler dbTable;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddDataTypeHandler dataTypeHandler;

    // Variable padding byte alignment value
    int byteAlignment;

    // List containing the variable padding information for each structure
    // table
    private List<StructurePaddingHandler> paddingInformation;

    /**************************************************************************
     * Structure padding handler class
     *************************************************************************/
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

        // Structure's total size in bytes
        private int totalSize;

        // Counter used for creating unique padding variable names within the
        // structure
        private int padCounter;

        // Running total of bytes processed in a structure while adding padding
        // variables, limited to be within the byte alignment value
        private int byteCount;

        // Flag indicating if the structure's total size and largest member
        // variable have been calculated
        private boolean isSizesCalculated;

        /**********************************************************************
         * Structure padding handler class constructor
         *
         * @param structureName
         *            prototype structure table name
         *********************************************************************/
        StructurePaddingHandler(String structureName)
        {
            // Load the prototype structure table data from the project
            // database
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
                            // Replace the table's contents with the committed
                            // data. This accounts for any uncommitted changes
                            editor.getTable().loadDataArrayIntoTable(editor.getCommittedTableInformation().getData(),
                                                                     false);
                            editor.getTable().getUndoManager().discardAllEdits();
                            isOpen = true;
                            break;
                        }
                    }

                    if (isOpen)
                    {
                        break;
                    }
                }

                // Create a table editor handler for the structure table
                tableEditor = new CcddTableEditorHandler(ccddMain,
                                                         tableInfo,
                                                         (CcddTableEditorDialog) null);

                // Get the a reference to the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableEditor.getTableInformation().getType());

                // Get the column indices for the variable name, data type,
                // array size, and bit length
                varNameColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                dataTypeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                arraySizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                bitLengthColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);

                // Set the flag indicating that the structure's total size and
                // largest member variable haven't been calculated
                isSizesCalculated = false;
            }
        }

        /**********************************************************************
         * Check if the table loaded successfully
         *
         * @return true if the table successfully loaded
         *********************************************************************/
        protected boolean isLoaded()
        {
            return !tableInfo.isErrorFlag();
        }

        /**********************************************************************
         * Get the variable name for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Variable name for the specified structure table row
         *********************************************************************/
        protected String getVariableName(int row)
        {
            return tableEditor.getExpandedValueAt(row, varNameColumn).toString();
        }

        /**********************************************************************
         * Get the data type for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Data type for the specified structure table row
         *********************************************************************/
        protected String getDataType(int row)
        {
            return tableEditor.getTable().getModel().getValueAt(row, dataTypeColumn).toString();
        }

        /**********************************************************************
         * Get the array size for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Array size for the specified structure table row
         *********************************************************************/
        protected String getArraySize(int row)
        {
            return tableEditor.getExpandedValueAt(row, arraySizeColumn).toString();
        }

        /**********************************************************************
         * Get the bit length for the specified structure table row
         *
         * @param row
         *            structure table row index
         *
         * @return Bit length for the specified structure table row
         *********************************************************************/
        protected String getBitLength(int row)
        {
            return tableEditor.getExpandedValueAt(row, bitLengthColumn).toString();
        }

        /**********************************************************************
         * Remove any existing padding variable from the structure table
         *********************************************************************/
        protected void removePadding()
        {
            List<Integer> removedRows = new ArrayList<Integer>();

            // Step through each row in the table
            for (int row = 0; row < tableEditor.getTable().getModel().getRowCount(); row++)
            {
                // Check if this row contains a padding variable
                if (getVariableName(row).matches(PAD_VARIABLE + "[0-9]+$")
                    && getDataType(row).equals(PAD_DATA_TYPE))
                {
                    // Add the row index to the list of those to be removed
                    removedRows.add(tableEditor.getTable().convertRowIndexToView(row));
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

        /**********************************************************************
         * Add padding variables as needed to the structure table
         *********************************************************************/
        protected void addPadding()
        {
            padCounter = 0;
            byteCount = 0;
            BitPackRowIndex packIndex = null;

            // Step through each row in the table
            for (int row = 0; row < tableEditor.getTable().getModel().getRowCount(); row++)
            {
                // Extract the variable's data type
                String dataType = getDataType(row);

                int variableSize = 0;

                // Check if the data type is a primitive
                if (dataTypeHandler.isPrimitive(dataType))
                {
                    // Extract the variable's data type and array size
                    String variable = getVariableName(row);
                    String arraySize = getArraySize(row);

                    // Check if the variable isn't an array definition
                    if (arraySize.isEmpty() || ArrayVariable.isArrayMember(variable))
                    {
                        // Check if the variable has a bit length
                        if (!getBitLength(row).isEmpty())
                        {
                            // Get the start and stop row indices for any
                            // subsequent variables that are bit-packed with
                            // this variable
                            packIndex = tableEditor.getPackedVariables(tableEditor.getTable().getTableDataList(false), row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the variable in bytes
                        variableSize = dataTypeHandler.getSizeInBytes(dataType);

                        // Check if the variable is already positioned on the
                        // alignment point. This also accounts for variables
                        // with a size greater than the alignment value
                        if ((variableSize + byteCount) % byteAlignment != 0)
                        {
                            // Check if the variable doesn't fit within the
                            // remaining bytes
                            if (variableSize + byteCount > byteAlignment)
                            {
                                // Calculate the number of padding variables
                                // needed to align the variable to the next
                                // alignment value
                                int numPads = byteAlignment - byteCount;

                                // Add the padding variable(s), if needed
                                row = addPaddingVariable(row, numPads);
                            }
                            // The variable doesn't exceed the next alignment
                            // point
                            else
                            {
                                // Calculate the number of padding variables
                                // needed to align the variable within the
                                // current alignment value
                                int numPads = (byteAlignment - byteCount) % variableSize;

                                // Add the padding variable(s), if needed
                                row = addPaddingVariable(row, numPads);
                            }
                        }

                        // Check if the variable is bit-packed with one or more
                        // subsequent variables
                        if (packIndex != null
                            && packIndex.getFirstIndex() != packIndex.getLastIndex())
                        {
                            // Adjust the tree index to skip the other pack
                            // members
                            row = packIndex.getLastIndex();
                        }
                    }
                }
                // The variable's data type is a structure
                else
                {
                    variableSize = 0;

                    // Step through each successfully loaded table
                    for (StructurePaddingHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data
                        // type
                        if (dataType.equals(childPadInfo.structureName))
                        {
                            // Calculate the number of padding variables needed
                            // to align the child structure variable
                            int numPads = (byteAlignment - byteCount) % childPadInfo.largestDataType;

                            // Add the padding variable(s), if needed
                            row = addPaddingVariable(row, numPads);

                            // Store the child structure size as the variable
                            // size
                            variableSize = childPadInfo.totalSize;

                            // Stop searching since the matching table was
                            // found
                            break;
                        }
                    }
                }

                // Add the size of the variable to the byte counter, then
                // adjust the byte count if it equals or exceeds the alignment
                // point
                byteCount = (byteCount + variableSize) % byteAlignment;
            }

            // Calculate the number of padding variables needed to fill out the
            // structure to the alignment point
            int numPads = (byteAlignment - byteCount) % largestDataType;

            // Add the padding variable(s), if needed
            addPaddingVariable(tableEditor.getTable().getModel().getRowCount(), numPads);
        }

        /**********************************************************************
         * Add a padding variable at the specified row index
         *********************************************************************/
        private int addPaddingVariable(int row, int padSize)
        {
            // Check if any padding is needed
            if (padSize > 0)
            {
                // Create an empty row array, then set the padding variable
                // name and data type
                Object[] rowData = tableEditor.getTable().getEmptyRow();
                rowData[varNameColumn] = PAD_VARIABLE + padCounter;
                rowData[dataTypeColumn] = PAD_DATA_TYPE;

                // Check if multiple padding variables are to be added
                if (padSize > 1)
                {
                    // Set the array size value to match the number of padding
                    // variables needed
                    rowData[arraySizeColumn] = padSize;
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

                    // Load the array of data into the table to reflect the
                    // added array members
                    tableEditor.getTable().loadDataArrayIntoTable(tableData.toArray(new Object[0][0]),
                                                                  false);
                }

                // Adjust the row index past the padding variables
                row += padSize;

                // Update the padding name and byte counters
                padCounter++;
                byteCount += padSize;
            }

            return row;
        }

        /**********************************************************************
         * Update the structure table padding variables in the project database
         *********************************************************************/
        protected void updateTable()
        {
            // Build the table updates
            tableEditor.buildUpdates();

            // Check if any updates were made (padding variables added or
            // deleted)
            if (!tableEditor.getAdditions().isEmpty() || !tableEditor.getDeletions().isEmpty())
            {
                // Update the table in the database
                dbTable.modifyTableData(tableInfo,
                                        tableEditor.getAdditions(),
                                        tableEditor.getModifications(),
                                        tableEditor.getDeletions(),
                                        true,
                                        false,
                                        false,
                                        false,
                                        false,
                                        null,
                                        null,
                                        ccddMain.getMainFrame());
            }
        }
    }

    /**************************************************************************
     * Padding variable handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param addPadding
     *            true to add or update the variable padding; false to remove
     *            the padding variables
     *************************************************************************/
    CcddPaddingVariableHandler(CcddMain ccddMain, boolean addPadding)
    {
        // Check if there are uncommitted changes and if so, confirm discarding
        // the changes before proceeding
        if (ccddMain.ignoreUncommittedChanges("Alter Padding",
                                              "Discard changes?",
                                              false,
                                              null,
                                              ccddMain.getMainFrame()))
        {
            this.ccddMain = ccddMain;
            dbTable = ccddMain.getDbTableCommandHandler();
            tableTypeHandler = ccddMain.getTableTypeHandler();
            dataTypeHandler = ccddMain.getDataTypeHandler();

            paddingInformation = new ArrayList<StructurePaddingHandler>();

            // Get the current byte alignment value
            byteAlignment = Integer.valueOf(ccddMain.getProgPrefs().get(PADDING_ALIGNMENT, "4"));

            // Step through each prototype structure table
            for (String protoStruct : dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE))
            {
                // Load the table's data
                StructurePaddingHandler paddingInfo = new StructurePaddingHandler(protoStruct);

                // Check if the table loaded successfully
                if (paddingInfo.isLoaded())
                {
                    // Add the table's padding information to the list
                    paddingInformation.add(paddingInfo);

                    // Remove any existing padding variables from the table
                    paddingInfo.removePadding();
                }
            }

            // Check if padding variables should be added
            if (addPadding)
            {
                // Step through each successfully loaded table
                for (StructurePaddingHandler paddingInfo : paddingInformation)
                {
                    // Find largest primitive data type referenced in this
                    // table's hierarchy, including those in any child
                    // structures, and the structure's total size
                    setStructureSizes(paddingInfo);
                }

                // Step through each successfully loaded table
                for (StructurePaddingHandler paddingInfo : paddingInformation)
                {
                    // Add any padding variables to the table needed to align
                    // the variables
                    paddingInfo.addPadding();
                }
            }

            // Step through each successfully loaded table
            for (StructurePaddingHandler paddingInfo : paddingInformation)
            {
                // Update the table in the project database
                paddingInfo.updateTable();
            }
        }
    }

    /**************************************************************************
     * Determine the total size of a structure and the largest data type within
     * it
     *
     * @param paddingInformation
     *            list containing the padding variable information for every
     *            structure table
     *************************************************************************/
    protected void setStructureSizes(StructurePaddingHandler padInfo)
    {
        // Check if the sizes for this structure haven't already been
        // calculated
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
                            // Get the start and stop row indices for any
                            // subsequent variables that are bit-packed with
                            // this variable
                            packIndex = padInfo.tableEditor.getPackedVariables(padInfo.tableEditor.getTable().getTableDataList(false), row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the primitive data type in bytes
                        int size = dataTypeHandler.getSizeInBytes(dataType);

                        // Update the largest data type. Limit the size to no
                        // greater than the byte alignment value
                        padInfo.largestDataType = Math.min(byteAlignment,
                                                           Math.max(size,
                                                                    padInfo.largestDataType));

                        // Update the total size
                        padInfo.totalSize += size;

                        // Check if the variable is bit-packed with one or more
                        // subsequent variables
                        if (packIndex != null
                            && packIndex.getFirstIndex() != packIndex.getLastIndex())
                        {
                            // Adjust the tree index to skip the other pack
                            // members
                            row = packIndex.getLastIndex();
                        }
                    }
                }
                // The data type isn't a primitive; i.e., it's a structure
                // reference
                else
                {
                    // Step through each successfully loaded table
                    for (StructurePaddingHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data
                        // type
                        if (dataType.equals(childPadInfo.structureName))
                        {
                            // Set the sizes for this structure (if not already
                            // calculated)
                            setStructureSizes(childPadInfo);

                            // Update the largest data type
                            padInfo.largestDataType = Math.max(padInfo.largestDataType,
                                                               childPadInfo.largestDataType);

                            // Add the size of the child structure to this
                            // structure
                            padInfo.totalSize += childPadInfo.totalSize;

                            // Stop searching since a match was found
                            break;
                        }
                    }
                }
            }

            // Round up the total structure size to the next alignment point
            // (padding variables will be added as needed to meet this size)
            padInfo.totalSize += byteAlignment - (padInfo.totalSize % byteAlignment);

            // Set the flag indicating this structure's sizes are calculated
            padInfo.isSizesCalculated = true;
        }
    }
}
