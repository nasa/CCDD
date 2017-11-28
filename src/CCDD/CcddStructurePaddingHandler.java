/**
 * CFS Command & Data Dictionary structure variable padding handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PAD_DATA_TYPE;
import static CCDD.CcddConstants.PAD_VARIABLE;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddTableEditorHandler.PackIndex;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary structure padding handler class
 *****************************************************************************/
public class CcddStructurePaddingHandler
{
    // TODO This is adjustable: 32 bits (4 bytes), 64 bits (8 bytes), etc. IS
    // THIS NEEDED?
    int byteAlignment = 4;

    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;

    // List containing the variable padding information for each structure
    // table
    private final List<PaddingVariableHandler> paddingInformation;

    /**************************************************************************
     * Structure variable padding handler class
     *************************************************************************/
    class PaddingVariableHandler
    {
        private final TableInformation tableInfo;
        private CcddTableEditorHandler editor;

        // Column indices
        private int varNameColumn;
        private int dataTypeColumn;
        private int arraySizeColumn;
        private int bitLengthColumn;
        private int largestDataType;

        private int totalSize;
        private int padCounter;
        private int byteCount;

        private boolean isSizesCalculated;

        /**********************************************************************
         * Padding variable handler class constructor
         *
         * @param structTable
         *            prototype structure table name
         *********************************************************************/
        PaddingVariableHandler(String structTable)
        {
            // TODO loadTableData() could check if an editor is open and return
            // the reference to its tableInfo...?
            // Load the prototype structure table data from the project
            // database
            tableInfo = dbTable.loadTableData(structTable,
                                              false,
                                              false,
                                              false,
                                              false,
                                              ccddMain.getMainFrame());

            // Check that the table loaded successfully
            if (!tableInfo.isErrorFlag())
            {
                // Create a table editor handler for the structure table
                editor = new CcddTableEditorHandler(ccddMain,
                                                    tableInfo,
                                                    (CcddTableEditorDialog) null);

                // Get the a reference to the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(editor.getTableInformation().getType());

                // Get the column indices for the variable name, data type,
                // array size, and bit length
                varNameColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                dataTypeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                arraySizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                bitLengthColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);

                padCounter = 0;
                byteCount = 0;
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
         * Get the structure table name
         *
         * @return Structure table name
         *********************************************************************/
        protected String getTableName()
        {
            return tableInfo.getPrototypeName();
        }

        /**********************************************************************
         * Get the largest data type referenced in this structure, including
         * any primitives and child structures
         *
         * @return Largest data type referenced in this structure
         *********************************************************************/
        protected int getLargestDataType()
        {
            return largestDataType;
        }

        /**********************************************************************
         * Get the total size, in bytes, of this structure, including any
         * primitives and child structures
         *
         * @return Total size of this structure
         *********************************************************************/
        protected int getTotalSize()
        {
            return totalSize;
        }

        // TODO
        protected CcddTableEditorHandler getEditor()
        {
            return editor;
        }

        protected String getVariableName(int row)
        {
            return editor.getExpandedValueAt(row, varNameColumn).toString();
        }

        protected String getDataType(int row)
        {
            return editor.getTable().getModel().getValueAt(row, dataTypeColumn).toString();
        }

        protected String getArraySize(int row)
        {
            return editor.getExpandedValueAt(row, arraySizeColumn).toString();
        }

        protected String getBitLength(int row)
        {
            return editor.getExpandedValueAt(row, bitLengthColumn).toString();
        }

        protected boolean isSizesCalculated()
        {
            return isSizesCalculated;
        }

        protected void setSizesCalculated()
        {
            isSizesCalculated = true;
        }

        /**********************************************************************
         * Remove any existing padding variable from the structure table
         *********************************************************************/
        protected void removePadding()
        {
            List<Integer> removedRows = new ArrayList<Integer>();

            // Step through each row in the table
            for (int row = 0; row < editor.getTable().getModel().getRowCount(); row++)
            {
                // Check if this row contains a padding variable
                if (getVariableName(row).matches(PAD_VARIABLE + "[0-9]+$"))
                {
                    // Add the row index to the list of those to be removed
                    removedRows.add(editor.getTable().convertRowIndexToView(row));
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
                editor.getTable().removeRows(removedRowsArray);
            }
        }

        /**********************************************************************
         * Add padding variables as needed to the structure table
         *********************************************************************/
        protected void addPadding()
        {
            padCounter = 0;
            byteCount = 0;
            PackIndex packIndex = null;

            // Step through each row in the table
            for (int row = 0; row < editor.getTable().getModel().getRowCount(); row++)
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
                            packIndex = editor.getPackedVariables(editor.getTable().getTableDataList(false), row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the variable in bytes
                        variableSize = dataTypeHandler.getSizeInBytes(dataType);

                        // Check if the variable doesn't fit within the
                        // remaining bytes
                        if (variableSize + byteCount > byteAlignment)
                        {
                            // Add padding variables until the next alignment
                            // point is reached
                            while (byteCount < byteAlignment)
                            {
                                // Add a padding variable
                                addPaddingVariable(row);
                                row++;
                            }
                        }
                        // The variable doesn't exceed the next alignment point
                        else
                        {
                            // Add padding variables until the next variable is
                            // aligned for packing (e.g., an int16 following a
                            // char in a 32-bit architecture requires a 1-byte
                            // pad after the char to properly align the int16)
                            while ((byteAlignment - byteCount) % variableSize != 0)
                            {
                                // Add a padding variable
                                addPaddingVariable(row);
                                row++;
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
                    for (PaddingVariableHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data
                        // type
                        if (dataType.equals(childPadInfo.getTableName()))
                        {
                            // Add padding variables until the child structure
                            // is aligned for packing
                            while ((byteAlignment - byteCount) % childPadInfo.largestDataType != 0)
                            {
                                // Add a padding variable
                                addPaddingVariable(row);
                                row++;
                            }

                            // Store the child structure size as the variable
                            // size
                            variableSize = childPadInfo.totalSize;
                            break;
                        }
                    }
                }

                // Add the size of the variable to the byte counter, then
                // adjust the byte count if it equals or exceeds the alignment
                // point
                byteCount = (byteCount + variableSize) % byteAlignment;
            }
        }

        /**********************************************************************
         * Add a padding variable at the specified row index
         *********************************************************************/
        private void addPaddingVariable(int row)
        {
            // Create an empty row array, then set the padding variable name
            // and data type
            Object[] rowData = editor.getTable().getEmptyRow();
            rowData[varNameColumn] = PAD_VARIABLE + padCounter;
            rowData[dataTypeColumn] = PAD_DATA_TYPE;

            // Insert the padding variable row into the table
            editor.getTable().insertRowData(row - 1, rowData);

            // Update the padding name and byte counters
            padCounter++;
            byteCount++;
        }

        /**********************************************************************
         * Update the structure table padding variables in the project database
         *********************************************************************/
        protected void updateTable()
        {
            // Build the table updates
            editor.buildUpdates();

            // Check if any updates were made (padding variables added or
            // deleted)
            if (!editor.getAdditions().isEmpty() || !editor.getDeletions().isEmpty())
            {
                // Update the table in the database
                dbTable.modifyTableData(tableInfo,
                                        editor.getAdditions(),
                                        editor.getModifications(),
                                        editor.getDeletions(),
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
     * Structure variable padding handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param addPadding
     *            true to add or update the variable padding; false to remove
     *            the padding variables
     *************************************************************************/
    CcddStructurePaddingHandler(CcddMain ccddMain, boolean addPadding)
    {
        // TODO This is an all or nothing process - all data tables get padded
        // at the same time. Any new structures, deleted structures, or changes
        // to existing structures trigger the padding update if padding is
        // enabled. Disabling padding should remove all padding variables from
        // all structures (a structure change is likely best - and most easily-
        // handled by removing all of the existing padding and recalculating
        // the padding from scratch). Any open structure tables need their
        // editors updated when this process occurs. Need to be able to
        // enable/disable padding and set the bit architecture (byte alignment)

        // TODO Alignment of variables that have a structure as a data type is
        // dependent of the largest primitive variable in the child structure
        // (and if it references another child structure then its largest
        // primitive takes precedence, and so on)

        // TODO Can allow padding variable rows to be edited via the table
        // editor, but may want to color the pad variable backgrounds

        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();

        paddingInformation = new ArrayList<PaddingVariableHandler>();

        // Step through each prototype structure table
        for (String protoStruct : dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE))
        {
            // Load the table's data
            PaddingVariableHandler paddingInfo = new PaddingVariableHandler(protoStruct);

            // Check if the table loaded successfully
            if (paddingInfo.isLoaded())
            {
                // Check if padding variables should be added
                if (addPadding)
                {
                    // Add the table's padding information to the list
                    paddingInformation.add(paddingInfo);
                }

                // Remove any existing padding variables from the table
                paddingInfo.removePadding();
            }
        }

        // Check if padding variables should be added
        if (addPadding)
        {
            // Step through each successfully loaded table
            for (PaddingVariableHandler paddingInfo : paddingInformation)
            {
                // Find largest primitive data type referenced in this table's
                // hierarchy, including those in any child structures, and the
                // structure's total size
                setStructureSizes(paddingInfo);
            }

            // Step through each successfully loaded table
            for (PaddingVariableHandler paddingInfo : paddingInformation)
            {
                // Add any padding variables to the table needed to align the
                // variables
                paddingInfo.addPadding();
            }
        }

        // Step through each successfully loaded table
        for (PaddingVariableHandler paddingInfo : paddingInformation)
        {
            // Update the table in the project database
            paddingInfo.updateTable();
        }
    }

    /**********************************************************************
     * TODO This is a recursive method
     *
     * @param paddingInformation
     *            list containing the padding variable information for every
     *            structure table
     *********************************************************************/
    protected void setStructureSizes(PaddingVariableHandler padInfo)
    {
        // Check if the sizes for this structure haven't already been
        // calculated
        if (!padInfo.isSizesCalculated())
        {
            // Step through each row in the table
            for (int row = 0; row < padInfo.getEditor().getTable().getModel().getRowCount(); row++)
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
                        PackIndex packIndex = null;

                        // Check if the variable has a bit length
                        if (!padInfo.getBitLength(row).isEmpty())
                        {
                            // Get the start and stop row indices for any
                            // subsequent variables that are bit-packed with
                            // this variable
                            packIndex = padInfo.getEditor().getPackedVariables(padInfo.getEditor().getTable().getTableDataList(false), row);
                        }
                        // The variable doesn't have a bit length
                        else
                        {
                            packIndex = null;
                        }

                        // Get the size of the primitive data type in bytes
                        int size = dataTypeHandler.getSizeInBytes(dataType);

                        // Update the largest data type
                        padInfo.largestDataType = Math.max(size, padInfo.largestDataType);

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
                    for (PaddingVariableHandler childPadInfo : paddingInformation)
                    {
                        // Check if the table name matches the structure data
                        // type
                        if (dataType.equals(childPadInfo.getTableName()))
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

            // Set the flag indicating this structure's sizes are calculated
            padInfo.setSizesCalculated();
        }
    }
}
