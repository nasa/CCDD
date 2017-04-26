/**
 * CFS Command & Data Dictionary common classes. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CELL_HORIZONTAL_PADDING;
import static CCDD.CcddConstants.CELL_VERTICAL_PADDING;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.TLM_SCH_SEPARATOR;
import static CCDD.CcddConstants.TOOL_TIP_MAXIMUM_LENGTH;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.tree.DefaultMutableTreeNode;

import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.InputDataType;

/******************************************************************************
 * CFS Command & Data Dictionary common classes class
 *****************************************************************************/
public class CcddClasses
{
    // Data type handler reference
    private static CcddDataTypeHandler dataTypeHandler;
    private static CcddMacroHandler macroHandler;

    /**************************************************************************
     * Set the data type and macro handler references
     * 
     * @param dtHandler
     *            reference to the data type handler
     * 
     * @param mHandler
     *            reference to the macro handler
     *************************************************************************/
    protected static void setHandlers(CcddDataTypeHandler dtHandler,
                                      CcddMacroHandler mHandler)
    {
        dataTypeHandler = dtHandler;
        macroHandler = mHandler;
    }

    /**************************************************************************
     * CCDD exception class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected static class CCDDException extends Exception
    {
        private final String message;
        private final int messageType;

        /**********************************************************************
         * CCDD exception class constructor for user-defined error message
         * types
         *
         * @param message
         *            exception message
         *
         * @param messageType
         *            JOptionPane message type
         *********************************************************************/
        CCDDException(String message, int messageType)
        {
            this.message = message;
            this.messageType = messageType;
        }

        /**********************************************************************
         * CCDD exception class constructor for an error message type
         *
         * @param message
         *            exception message
         *********************************************************************/
        CCDDException(String message)
        {
            this(message, JOptionPane.ERROR_MESSAGE);
        }

        /**********************************************************************
         * CCDD exception class constructor for empty error message
         *********************************************************************/
        CCDDException()
        {
            this("", JOptionPane.ERROR_MESSAGE);
        }

        /**********************************************************************
         * Get CCDD exception message
         *
         * @return Exception message
         *********************************************************************/
        @Override
        public String getMessage()
        {
            return message;
        }

        /**********************************************************************
         * Get CCDD exception message type
         *
         * @return Exception message type
         *********************************************************************/
        public int getMessageType()
        {
            return messageType;
        }
    }

    /**************************************************************************
     * Table information class
     * 
     * A table can be one (or possibly more) of the following: prototype, root,
     * parent, and child. A prototype table is produced whenever the Data | New
     * table(s) menu command is used to create a table. The prototype becomes a
     * 'rubber stamp' for creating other table instances that initially contain
     * the same information as the prototype. A root table is a prototype that
     * is not referenced from within another table (making it a top level
     * table). Therefore a root table is always a prototype table, but the
     * reverse isn't necessarily true. Other tables may be referenced within
     * the root table - these referenced tables are known as child tables. A
     * table's parent table is the table in which the child table is directly
     * referenced. The parent table can itself be a child table, or the parent
     * and root tables are the same in the case where the child is referenced
     * directly in the root table
     *************************************************************************/
    protected static class TableInformation
    {
        private String tableType;
        private String tablePath;
        private String[][] tableData;
        private String columnOrder;
        private String description;
        private boolean isPrototype;
        private boolean isRootStructure;
        private boolean errorFlag;
        private CcddFieldHandler fieldHandler;

        /**********************************************************************
         * Table information class constructor
         *
         * @param tableType
         *            table type
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         *
         * @param tableData
         *            two-dimensional table data array (rows x columns)
         *
         * @param columnOrder
         *            table column display order in the format
         *            column#0[:column#1[:...[:column#N]]]. The column numbers
         *            are based on the position of the column's definition in
         *            the table's type definition
         *
         * @param description
         *            table description
         *
         * @param isRootStructure
         *            true if this table represents a root table of type
         *            'structure'
         *********************************************************************/
        private TableInformation(String tableType,
                                 String tablePath,
                                 String[][] tableData,
                                 String columnOrder,
                                 String description,
                                 boolean isRootStructure)
        {
            this.tableType = tableType;
            this.tablePath = tablePath;
            this.tableData = tableData;
            this.columnOrder = columnOrder;
            this.description = description;
            this.isRootStructure = isRootStructure;
            isPrototype = !tablePath.contains(".");
            errorFlag = false;
            fieldHandler = new CcddFieldHandler();
        }

        /**********************************************************************
         * Table information class constructor. Used when the array of field
         * definitions are retrieved from the database. These are converted to
         * a list of FieldInformation references
         *
         * @param tableType
         *            table type
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         *
         * @param tableData
         *            two-dimensional table data array (rows x columns)
         *
         * @param columnOrder
         *            table column display order in the format
         *            column#0[:column#1[:...[:column#N]]]. The column numbers
         *            are based on the position of the column's definition in
         *            the table's type definition
         *
         * @param description
         *            table description
         *
         * @param isRootStructure
         *            true if this table represents a root table of type
         *            'structure'
         *
         * @param fieldDefinition
         *            array of field definitions
         *********************************************************************/
        protected TableInformation(String tableType,
                                   String tablePath,
                                   String[][] tableData,
                                   String columnOrder,
                                   String description,
                                   boolean isRootStructure,
                                   Object[][] fieldDefinition)
        {
            this(tableType,
                 tablePath,
                 tableData,
                 columnOrder,
                 description,
                 isRootStructure);

            // Check if the data field definitions array is provided
            if (fieldDefinition != null)
            {
                // Build the data field information
                fieldHandler.buildFieldInformation(fieldDefinition,
                                                   getTablePath());
            }
        }

        /**********************************************************************
         * Table information class constructor. Used if the field definition
         * array has already been converted to FieldInformation references
         *
         * @param tableType
         *            table type
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         *
         * @param tableData
         *            two-dimensional table data array (rows x columns)
         *
         * @param columnOrder
         *            table column display order in the format
         *            column#0[:column#1[:...[:column#N]]]. The column numbers
         *            are based on the position of the column's definition in
         *            the table's type definition
         *
         * @param description
         *            table description
         *
         * @param isRootStructure
         *            true if this table represents a root table of type
         *            'structure'
         *
         * @param fieldInfo
         *            list of FieldInformation references
         *********************************************************************/
        protected TableInformation(String tableType,
                                   String tablePath,
                                   String[][] tableData,
                                   String columnOrder,
                                   String description,
                                   boolean isRootStructure,
                                   List<FieldInformation> fieldInfo)
        {
            this(tableType,
                 tablePath,
                 tableData,
                 columnOrder,
                 description,
                 isRootStructure);

            fieldHandler.setFieldInformation(fieldInfo);
        }

        /**********************************************************************
         * Table information class constructor for a table that couldn't be
         * loaded from the database
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         *********************************************************************/
        protected TableInformation(String tablePath)
        {
            this.tablePath = tablePath;
            errorFlag = true;
        }

        /**********************************************************************
         * Get the table type
         *
         * @return Table type
         *********************************************************************/
        protected String getType()
        {
            return tableType;
        }

        /**********************************************************************
         * Set the table type
         *
         * @param tableType
         *            table type
         *********************************************************************/
        protected void setType(String tableType)
        {
            this.tableType = tableType;
        }

        /**********************************************************************
         * Get the table name in the form prototypeName.variableName, where
         * prototypeName is the name of the prototype structure for this table,
         * and variableName is the name of this particular instantiation of the
         * prototype table. variableName is blank for a top level structure or
         * non-structure table
         *
         * @return Table prototype name + variable name
         *********************************************************************/
        protected String getProtoVariableName()
        {
            return getProtoVariableName(getTablePath());
        }

        /**********************************************************************
         * Get the table name from the supplied table path in the form
         * prototypeName.variableName, where prototypeName is the name of the
         * prototype structure for this table, and variableName is the name of
         * this particular instantiation of the prototype table. variableName
         * is blank for a top level structure or non-structure table
         *
         * @param path
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]
         *
         * @return Table prototype name + variable name
         *********************************************************************/
        protected static String getProtoVariableName(String path)
        {
            return path.substring(path.lastIndexOf(',') + 1);
        }

        /**********************************************************************
         * Get the table's prototype table name
         * 
         * @return Table's prototype table name
         *********************************************************************/
        protected String getPrototypeName()
        {
            return getPrototypeName(getTablePath());
        }

        /**********************************************************************
         * Get the table's prototype table name from the supplied path
         *
         * @param path
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]
         *
         * @return Table's prototype table name
         *********************************************************************/
        protected static String getPrototypeName(String path)
        {
            String prototype = getProtoVariableName(path);

            // Check for the location of the variable name
            int index = prototype.indexOf(".");

            // Check if the name contains a variable name
            if (index != -1)
            {
                // Remove the variable name portion
                prototype = prototype.substring(0, index);
            }

            return prototype;
        }

        /**********************************************************************
         * Get the table's path in the format
         * rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]]. The
         * table path for a non-structure table is simply the root table name.
         * For a structure table the root table is the top level structure
         * table from which this table descends. The first data type/variable
         * name pair is from the root table, with each succeeding pair coming
         * from the next level down in the structure's hierarchy
         * 
         *
         * @return Table path
         *********************************************************************/
        protected String getTablePath()
        {
            return tablePath;
        }

        /**********************************************************************
         * Set the table path
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         *********************************************************************/
        protected void setTablePath(String tablePath)
        {
            this.tablePath = tablePath;
        }

        /**********************************************************************
         * Get the table's parent table name. The parent table is the structure
         * table for which the table represented by this class instance is a
         * direct member. The parent and root tables are the same if this table
         * is a direct child of the root table
         *
         * @return Table's parent table name; blank if this is a prototype or
         *         parent table
         *********************************************************************/
        protected String getParentTable()
        {
            return getParentTable(getTablePath());
        }

        /**********************************************************************
         * Get the table's parent table name. The parent table is the structure
         * table for which the table represented by this class instance is an
         * immediate descendant (child). The parent and root tables are the
         * same if this table is a child of a root table
         *
         * @param tablePath
         *            table path in the format
         *            rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure
         *            table is simply the root table name. For a structure
         *            table the root table is the top level structure table
         *            from which this table descends. The first data
         *            type/variable name pair is from the root table, with each
         *            succeeding pair coming from the next level down in the
         *            structure's hierarchy
         * 
         * @return Table's parent table name; blank if this is a prototype or
         *         parent table
         *********************************************************************/
        protected static String getParentTable(String path)
        {
            String parent = "";

            // Get the last variable in the table path
            int index = path.lastIndexOf(",");

            // Check that a variable exists
            if (index != -1)
            {
                // Get the name of the table immediately above this table in
                // the path hierarchy
                parent = getPrototypeName(path.substring(0, index - 1));
            }

            return parent;
        }

        /**********************************************************************
         * Get the table's root table name. For a structure table the root
         * table is the top level table from which the table represented by
         * this class instance descends. For a non-structure table the root
         * table is the table name
         *
         * @return Table's root table name
         *********************************************************************/
        protected String getRootTable()
        {
            return tablePath.replaceFirst(",.*$", "");
        }

        /**********************************************************************
         * Set the table's root table name. For a structure table the root
         * table is the top level table from which the table represented by
         * this class instance descends. For a non-structure table the root
         * table is the table name
         *
         * @param rootTable
         *            root table name
         *********************************************************************/
        protected void setRootTable(String rootTable)
        {
            tablePath.replaceFirst("^.*(,|$)", rootTable);
        }

        /**********************************************************************
         * Get the table data array
         *
         * @return Table data array
         *********************************************************************/
        protected String[][] getData()
        {
            return tableData;
        }

        /**********************************************************************
         * Set the table data array
         *
         * @param tableData
         *            table data array
         *********************************************************************/
        protected void setData(String[][] tableData)
        {
            this.tableData = tableData;
        }

        /**********************************************************************
         * Get the table column order
         *
         * @return Table column order
         *********************************************************************/
        protected String getColumnOrder()
        {
            return columnOrder;
        }

        /**********************************************************************
         * Set the table column order
         *
         * @param columnOrder
         *            table column order
         *********************************************************************/
        protected void setColumnOrder(String columnOrder)
        {
            this.columnOrder = columnOrder;
        }

        /**********************************************************************
         * Get the table description
         *
         * @return Table description
         *********************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /**********************************************************************
         * Set the table description
         *
         * @param description
         *            table description
         *********************************************************************/
        protected void setDescription(String description)
        {
            this.description = description.trim();
        }

        /**********************************************************************
         * Get the flag indicating if this is a prototype table
         *
         * @return true if the table is a prototype
         *********************************************************************/
        protected boolean isPrototype()
        {
            return isPrototype;
        }

        /**********************************************************************
         * Get the flag indicating if this is a root structure table
         *
         * @return true if this is a top-level (root) table of type 'structure'
         *********************************************************************/
        protected boolean isRootStructure()
        {
            return isRootStructure;
        }

        /**********************************************************************
         * Get the status of the error flag
         *
         * @return true if an error occurred obtaining the table data from the
         *         database
         *********************************************************************/
        protected boolean isErrorFlag()
        {
            return errorFlag;
        }

        /**********************************************************************
         * Set the error flag to true
         *********************************************************************/
        protected void setErrorFlag()
        {
            errorFlag = true;
        }

        /**********************************************************************
         * Get the table data field handler
         *
         * @return Table data field handler
         *********************************************************************/
        protected CcddFieldHandler getFieldHandler()
        {
            return fieldHandler;
        }
    }

    /**************************************************************************
     * Table members class
     *************************************************************************/
    protected static class TableMembers
    {
        private final String name;
        private final String type;
        private final List<String> dataTypes;
        private final List<String> variableNames;
        private final List<String> bitLengths;
        private final List<String[]> rates;
        private final List<String[]> enumerations;

        /**********************************************************************
         * Table members class constructor
         *
         * @param name
         *            table name
         *
         * @param type
         *            table type
         *
         * @param dataTypes
         *            list of non-primitive data types that are referenced by
         *            this table
         *
         * @param variableNames
         *            list of the variable names associated with the data types
         *
         * @param bitLengths
         *            list of bit lengths associated with the variables
         *
         * @param rates
         *            list of the rates associated with the variables
         *
         * @param enumerations
         *            list of the enumerations associated with the variables
         *********************************************************************/
        protected TableMembers(String name,
                               String type,
                               List<String> dataTypes,
                               List<String> variableNames,
                               List<String> bitLengths,
                               List<String[]> rates,
                               List<String[]> enumerations)
        {
            this.name = name;
            this.type = type;
            this.dataTypes = dataTypes;
            this.variableNames = variableNames;
            this.bitLengths = bitLengths;
            this.rates = rates;
            this.enumerations = enumerations;
        }

        /**********************************************************************
         * Get the table name
         *
         * @return Table name
         *********************************************************************/
        protected String getTableName()
        {
            return name;
        }

        /**********************************************************************
         * Get the table type
         *
         * @return Table type
         *********************************************************************/
        protected String getTableType()
        {
            return type;
        }

        /**********************************************************************
         * Get the list of this table's non-primitive data types (i.e.,
         * references to other tables)
         *
         * @return List of this table's data types
         *********************************************************************/
        protected List<String> getDataTypes()
        {
            return dataTypes;
        }

        /**********************************************************************
         * Get the list of this table's non-primitive variable names associated
         * with the data types
         *
         * @return List of this table's variable names
         *********************************************************************/
        protected List<String> getVariableNames()
        {
            return variableNames;
        }

        /**********************************************************************
         * Get the list of this table's bit lengths associated with the
         * variables
         *
         * @return List of this table's variable bit lengths
         *********************************************************************/
        protected List<String> getBitLengths()
        {
            return bitLengths;
        }

        /**********************************************************************
         * Get the list of this table's rates associated with the variables
         *
         * @return List of this table's rates
         *********************************************************************/
        protected List<String[]> getRates()
        {
            return rates;
        }

        /**********************************************************************
         * Get the list of this table's enumerations associated with the
         * variables
         *
         * @return List of this table's enumerations
         *********************************************************************/
        protected List<String[]> getEnumerations()
        {
            return enumerations;
        }

        /**********************************************************************
         * Get the full variable name (in the format dataType.variableName) at
         * the specified index
         *
         * @param index
         *            index into this member's variable information lists
         *
         * @return Full variable name at the specified index
         *********************************************************************/
        protected String getFullVariableName(int index)
        {
            String varName = dataTypes.get(index) + "." + variableNames.get(index);

            return varName;
        }

        /**********************************************************************
         * Get the full variable name (in the format
         * dataType.variableName:bitLength) at the specified index, including
         * the bit length, if present
         *
         * @param index
         *            index into this member's variable information lists
         *
         * @return Full variable name, including bit length, at the specified
         *         index
         *********************************************************************/
        protected String getFullVariableNameWithBits(int index)
        {
            // Get the full variable name
            String varName = getFullVariableName(index);

            // Check if the variable has a bit length
            if (!bitLengths.get(index).isEmpty())
            {
                // Append the bit length to the variable name
                varName += ":" + bitLengths.get(index);
            }

            return varName;
        }
    }

    /**************************************************************************
     * Table addition data class
     *************************************************************************/
    protected static class TableAddition
    {
        private final Object[] rowData;

        /**********************************************************************
         * Table addition data class constructor. Rows may only be added to
         * prototype tables
         *
         * @param rowData
         *            row of data from the table containing the changes
         *********************************************************************/
        TableAddition(Object[] rowData)
        {
            this.rowData = rowData;
        }

        /**********************************************************************
         * Get an array of column values for the updated row
         *
         * @return Array of column values for the updated row
         *********************************************************************/
        protected Object[] getRowData()
        {
            return rowData;
        }
    }

    /**************************************************************************
     * Table modification data class
     *************************************************************************/
    protected static class TableModification
    {
        private final Object[] rowData;
        private final Object[] originalRowData;
        private final int variableColumn;
        private final int dataTypeColumn;
        private final int arraySizeColumn;
        private final int bitLengthColumn;
        private final List<Integer> rateColumn;

        /**********************************************************************
         * Table modification data class constructor
         *
         * @param rowData
         *            row of data from the table containing the changes
         *
         * @param originalRowData
         *            original contents of the row of data from the table
         *            containing the changes
         *
         * @param variableColumn
         *            index of the column containing the variable name; -1 if
         *            no variable name column exists
         *
         * @param dataTypeColumn
         *            index of the column containing the data type name; -1 if
         *            no data type column exists
         *
         * @param arraySizeColumn
         *            index of the column containing the array size; -1 if no
         *            array size column exists
         * 
         * @param bitLengthColumn
         *            index of the column containing the bit length; -1 if no
         *            bit length column exists
         * 
         * @param rateColumn
         *            indices of the columns containing the sample rates; null
         *            if no rate column exists
         *********************************************************************/
        TableModification(Object[] rowData,
                          Object[] originalRowData,
                          int variableColumn,
                          int dataTypeColumn,
                          int arraySizeColumn,
                          int bitLengthColumn,
                          List<Integer> rateColumn)
        {
            this.rowData = rowData;
            this.originalRowData = originalRowData;
            this.variableColumn = variableColumn;
            this.dataTypeColumn = dataTypeColumn;
            this.arraySizeColumn = arraySizeColumn;
            this.bitLengthColumn = bitLengthColumn;
            this.rateColumn = rateColumn;
        }

        /**********************************************************************
         * Table modification data class constructor for macro updates
         *
         * @param rowData
         *            row of data from the macro table
         *
         * @param originalRowData
         *            original contents of the row of data from the macro table
         *********************************************************************/
        TableModification(Object[] rowData, Object[] originalRowData)
        {
            this(rowData, originalRowData, -1, -1, -1, -1, null);
        }

        /**********************************************************************
         * Get an array of column values for the updated row
         *
         * @return Array of column values for the updated row
         *********************************************************************/
        protected Object[] getRowData()
        {
            return rowData;
        }

        /**********************************************************************
         * Get an array of column values for the updated row's original content
         *
         * @return Array of column values for the updated row's original
         *         content
         *********************************************************************/
        protected Object[] getOriginalRowData()
        {
            return originalRowData;
        }

        /**********************************************************************
         * Get the variable name column index
         *
         * @return Variable name column index; -1 if the column doesn't exist
         *********************************************************************/
        protected int getVariableColumn()
        {
            return variableColumn;
        }

        /**********************************************************************
         * Get the data type column index
         *
         * @return Data type column index; -1 if the column doesn't exist
         *********************************************************************/
        protected int getDataTypeColumn()
        {
            return dataTypeColumn;
        }

        /**********************************************************************
         * Get the array size column index
         *
         * @return Array size column index; -1 if the column doesn't exist
         *********************************************************************/
        protected int getArraySizeColumn()
        {
            return arraySizeColumn;
        }

        /**********************************************************************
         * Get the bit length column index
         *
         * @return Bit length column index; -1 if the column doesn't exist
         *********************************************************************/
        protected int getBitLengthColumn()
        {
            return bitLengthColumn;
        }

        /**********************************************************************
         * Get the rate column index
         *
         * @return List containing the rate column indices; null if no rate
         *         column exists
         *********************************************************************/
        protected List<Integer> getRateColumn()
        {
            return rateColumn;
        }
    }

    /**************************************************************************
     * Table deletion data class
     *************************************************************************/
    protected static class TableDeletion
    {
        private final Object[] rowData;
        private final int variableColumn;
        private final int dataTypeColumn;

        /**********************************************************************
         * Table deletion data class constructor. Rows may only be deleted from
         * prototype tables
         *
         * @param rowData
         *            row of data from the table containing the changes
         *
         * @param variableColumn
         *            index of the column containing the variable name; -1 if
         *            no variable name column exists
         *
         * @param dataTypeColumn
         *            index of the column containing the data type name; -1 if
         *            no data type column exists
         *********************************************************************/
        TableDeletion(Object[] rowData,
                      int variableColumn,
                      int dataTypeColumn)
        {
            this.rowData = rowData;
            this.variableColumn = variableColumn;
            this.dataTypeColumn = dataTypeColumn;
        }

        /**********************************************************************
         * Get an array of column values for the updated row
         *
         * @return Array of column values for the updated row
         *********************************************************************/
        protected Object[] getRowData()
        {
            return rowData;
        }

        /**********************************************************************
         * Get the variable name column index
         *
         * @return Variable name column index
         *********************************************************************/
        protected int getVariableColumn()
        {
            return variableColumn;
        }

        /**********************************************************************
         * Get the data type column index
         *
         * @return Data type column index
         *********************************************************************/
        protected int getDataTypeColumn()
        {
            return dataTypeColumn;
        }
    }

    /**************************************************************************
     * Table definition class. Contains the information necessary to construct
     * a data table from information derived from a CSV or XTCE/EDS XML file
     *************************************************************************/
    protected static class TableDefinition
    {
        private String tableName;
        private String type;
        private String description;
        private final List<String> data;
        private final List<String[]> dataFields;

        /**********************************************************************
         * Table definition class constructor
         *********************************************************************/
        TableDefinition()
        {
            // Initialize storage for the table information
            data = new ArrayList<String>();
            dataFields = new ArrayList<String[]>();
        }

        /**********************************************************************
         * Table definition class constructor
         * 
         * @param tableName
         *            table name, including the path (for a structure child
         *            table)
         * 
         * @param description
         *            table description
         *********************************************************************/
        TableDefinition(String tableName, String description)
        {
            this();

            // Store the table name (including path, if applicable) and
            // description
            this.tableName = tableName;
            this.description = description;
        }

        /**********************************************************************
         * Get the table name (including path, if applicable)
         * 
         * @return Table name (including path, if applicable)
         *********************************************************************/
        protected String getName()
        {
            return tableName;
        }

        /**********************************************************************
         * Set the table name (including path, if applicable)
         * 
         * @param tableName
         *            table name (including path, if applicable)
         *********************************************************************/
        protected void setName(String tableName)
        {
            this.tableName = tableName;
        }

        /**********************************************************************
         * Get the table type
         * 
         * @return Table type
         *********************************************************************/
        protected String getType()
        {
            return type;
        }

        /**********************************************************************
         * Set the table type
         * 
         * @param type
         *            table type
         *********************************************************************/
        protected void setType(String type)
        {
            this.type = type;
        }

        /**********************************************************************
         * Get the table description
         * 
         * @return Table description; return a blank if the description is null
         *********************************************************************/
        protected String getDescription()
        {
            return description == null ? "" : description;
        }

        /**********************************************************************
         * Set the table description
         * 
         * @param description
         *            table description
         *********************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }

        /**********************************************************************
         * Get the table data
         * 
         * @return Table data
         *********************************************************************/
        protected List<String> getData()
        {
            return data;
        }

        /**********************************************************************
         * Add the specified row of data to the table data
         * 
         * @param rowData
         *            row of table data to add to the table's data
         *********************************************************************/
        protected void addData(String[] rowData)
        {
            data.addAll(Arrays.asList(rowData));
        }

        /**********************************************************************
         * Get the list containing the table data field information
         * 
         * @return List containing the table data field information
         *********************************************************************/
        protected List<String[]> getDataFields()
        {
            return dataFields;
        }

        /**********************************************************************
         * Add a data field information to the list of table data fields
         * 
         * @param fieldInfo
         *            data field information
         *********************************************************************/
        protected void addDataField(String[] fieldInfo)
        {
            dataFields.add(fieldInfo);
        }
    }

    /**************************************************************************
     * Table type definition class. Contains the information necessary to
     * construct a table type from information derived from a CSV or XTCE/EDS
     * XML file
     *************************************************************************/
    protected static class TableTypeDefinition
    {
        private final String typeName;
        private final String description;
        private final List<Object[]> columns;

        /**********************************************************************
         * Table definition class constructor
         * 
         * @param typeName
         *            table type name
         * 
         * @param description
         *            table type description
         *********************************************************************/
        TableTypeDefinition(String typeName, String description)
        {
            this.typeName = typeName;
            this.description = description;

            // Initialize storage for the table type column information
            columns = new ArrayList<Object[]>();
        }

        /**********************************************************************
         * Get the table type name
         * 
         * @return Table type name
         *********************************************************************/
        protected String getTypeName()
        {
            return typeName;
        }

        /**********************************************************************
         * Get the table type description
         * 
         * @return Table type description
         *********************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /**********************************************************************
         * Get the list containing the table type column information
         * 
         * @return List containing the table type column information
         *********************************************************************/
        protected List<Object[]> getColumns()
        {
            return columns;
        }

        /**********************************************************************
         * Add a table type column
         * 
         * @param column
         *            array of table type column information
         *********************************************************************/
        protected void addColumn(Object[] column)
        {
            columns.add(column);
        }
    }

    /**************************************************************************
     * Command argument class. Contains associated command argument name, data
     * type, enumeration, minimum value, and maximum value column indices in
     * view or model coordinates (as specified)
     *************************************************************************/
    protected static class AssociatedColumns
    {
        private final int name;
        private final int dataType;
        private final int enumeration;
        private final MinMaxPair minMax;
        private final List<Integer> other;

        /**********************************************************************
         * Command argument class constructor for setting associated command
         * argument columns
         * 
         * @param useViewIndex
         *            true to adjust the column indices to view coordinates;
         *            false to keep the coordinates in model coordinates
         * 
         * @param name
         *            command argument name data type column index, model
         *            coordinates; -1 if none
         * 
         * @param dataType
         *            command argument data type column index, model
         *            coordinates; -1 if none
         * 
         * @param enumeration
         *            command argument enumeration column index, model
         *            coordinates; -1 if none
         * 
         * @param minimum
         *            command argument minimum value column index, model
         *            coordinates; -1 if none
         * 
         * @param maximum
         *            command argument maximum value column index, model
         *            coordinates; -1 if none
         * 
         * @param other
         *            list of other associated column indices, model
         *            coordinates; null if none
         *********************************************************************/
        AssociatedColumns(boolean useViewIndex,
                          int name,
                          int dataType,
                          int enumeration,
                          int minimum,
                          int maximum,
                          List<Integer> other)
        {
            // Store the column indices. Adjust to view coordinates based on
            // the input flag
            this.name = name
                        - (useViewIndex
                                       ? NUM_HIDDEN_COLUMNS
                                       : 0);
            this.dataType = dataType
                            - (useViewIndex
                                           ? NUM_HIDDEN_COLUMNS
                                           : 0);
            this.enumeration = enumeration
                               - (useViewIndex
                                              ? NUM_HIDDEN_COLUMNS
                                              : 0);
            this.minMax = new MinMaxPair(minimum
                                         - (useViewIndex
                                                        ? NUM_HIDDEN_COLUMNS
                                                        : 0),
                                         maximum
                                             - (useViewIndex
                                                            ? NUM_HIDDEN_COLUMNS
                                                            : 0));

            // Check if other associated columns are provided
            if (other != null)
            {
                this.other = new ArrayList<Integer>();

                // Step through each associated column
                for (int column : other)
                {
                    // Store the column index. Adjust to view coordinates based
                    // on the input flag
                    this.other.add(column
                                   - (useViewIndex
                                                  ? NUM_HIDDEN_COLUMNS
                                                  : 0));
                }
            }
            // No other columns are provided
            else
            {
                this.other = null;
            }
        }

        /**********************************************************************
         * Command argument class constructor for setting structure table data
         * type and enumeration pairings
         * 
         * @param dataType
         *            command argument data type column index, model
         *            coordinates
         * 
         * @param enumeration
         *            command argument enumeration column index, model
         *            coordinates
         *********************************************************************/
        AssociatedColumns(int dataType, int enumeration)
        {
            this(false, -1, dataType, enumeration, -1, -1, null);
        }

        /**********************************************************************
         * Get the command argument name column index
         * 
         * @return Command argument name column index
         *********************************************************************/
        protected int getName()
        {
            return name;
        }

        /**********************************************************************
         * Get the command argument data type column index
         * 
         * @return Command argument data type column index
         *********************************************************************/
        protected int getDataType()
        {
            return dataType;
        }

        /**********************************************************************
         * Get the command argument enumeration column index
         * 
         * @return Command argument enumeration column index
         *********************************************************************/
        protected int getEnumeration()
        {
            return enumeration;
        }

        /**********************************************************************
         * Get the command argument minimum column index
         * 
         * @return Command argument minimum column index
         *********************************************************************/
        protected int getMinimum()
        {
            return minMax.getMinimum();
        }

        /**********************************************************************
         * Get the command argument maximum column index
         * 
         * @return Command argument maximum column index
         *********************************************************************/
        protected int getMaximum()
        {
            return minMax.getMaximum();
        }

        /**********************************************************************
         * Get the other command argument index(ices)
         * 
         * @return Other command argument index(ices)
         *********************************************************************/
        protected List<Integer> getOther()
        {
            return other;
        }
    }

    /**************************************************************************
     * Minimum/maximum pairing class. Contains associated minimum value and
     * maximum value column indices
     *************************************************************************/
    protected static class MinMaxPair
    {
        private final int minColumn;
        private final int maxColumn;

        /**********************************************************************
         * Minimum/maximum pairing class constructor
         * 
         * @param minimum
         *            minimum value column index
         * 
         * @param maximum
         *            maximum value column index
         *********************************************************************/
        MinMaxPair(int minColumn, int maxColumn)
        {
            this.minColumn = minColumn;
            this.maxColumn = maxColumn;
        }

        /**********************************************************************
         * Get the minimum column index
         * 
         * @return Minimum column index
         *********************************************************************/
        protected int getMinimum()
        {
            return minColumn;
        }

        /**********************************************************************
         * Get the maximum column index
         * 
         * @return Maximum column index
         *********************************************************************/
        protected int getMaximum()
        {
            return maxColumn;
        }
    }

    /**************************************************************************
     * Tree node with tool tip handling class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected static class ToolTipTreeNode extends DefaultMutableTreeNode
    {
        private final String toolTipText;

        /**********************************************************************
         * Tree node with tool tip handling class constructor
         *
         * @param nodeName
         *            node name
         *
         * @param toolTipText
         *            text to display when mouse pointer hovers over the node
         *********************************************************************/
        protected ToolTipTreeNode(String nodeName, String toolTipText)
        {
            super(nodeName);

            // Get the node name with any macro(s) expanded
            String expanded = macroHandler.getMacroExpansion(nodeName);

            // Check if a node name contains a macro
            if (!expanded.equals(nodeName))
            {
                // Amend the tool tip text to include the macro expansion of
                // the node name
                toolTipText = toolTipText == null
                              || toolTipText.isEmpty()
                                                      ? expanded
                                                      : "("
                                                        + expanded
                                                        + ") "
                                                        + toolTipText;
            }

            this.toolTipText = CcddUtilities.wrapText(toolTipText,
                                                      TOOL_TIP_MAXIMUM_LENGTH);
        }

        /**********************************************************************
         * Display the tree node tool tip text
         *
         * @return Tree node tool tip text
         *********************************************************************/
        protected String getToolTipText()
        {
            return toolTipText;
        }
    }

    /**********************************************************************
     * Data field information class
     *********************************************************************/
    protected static class FieldInformation
    {
        private String ownerName;
        private final String fieldName;
        private final String description;
        private final int charSize;
        private InputDataType fieldType;
        private final boolean isRequired;
        private ApplicabilityType applicability;
        private String value;
        private Object inputFld;

        /**********************************************************************
         * Data field information class constructor
         *
         * @param ownerName
         *            name of the table, including the path if this table
         *            represents a structure, or group for which the field is a
         *            member
         *
         * @param fieldName
         *            field name
         *
         * @param description
         *            data field description; used as the tool tip for this
         *            field
         *
         * @param charSize
         *            field display size in characters
         *
         * @param fieldType
         *            field InputDataType
         *
         * @param isRequired
         *            true if a value is required for this field
         *
         * @param applicability
         *            field applicability type; all tables, parent tables only,
         *            or child tables only
         *
         * @param value
         *            field value
         *********************************************************************/
        protected FieldInformation(String ownerName,
                                   String fieldName,
                                   String description,
                                   int charSize,
                                   InputDataType fieldType,
                                   boolean isRequired,
                                   ApplicabilityType applicability,
                                   String value)
        {
            this.ownerName = ownerName;
            this.fieldName = fieldName;
            this.description = description;
            this.charSize = charSize;
            this.fieldType = fieldType;
            this.isRequired = isRequired;
            this.applicability = applicability;
            this.value = value;
        }

        /**********************************************************************
         * Data field information class constructor
         *
         * @param ownerName
         *            name of the table, including the path if this table
         *            represents a structure, or group for which the field is a
         *            member
         *
         * @param fieldName
         *            field name
         *
         * @param description
         *            data field description; used as the tool tip for this
         *            field
         *
         * @param charSize
         *            field display size in characters
         *
         * @param inputType
         *            field input type
         *
         * @param isRequired
         *            true if a value is required for this field
         *
         * @param applicability
         *            field applicability; all tables, parent tables only, or
         *            child tables only
         *
         * @param value
         *            field value
         *********************************************************************/
        protected FieldInformation(String ownerName,
                                   String fieldName,
                                   String description,
                                   int charSize,
                                   String inputType,
                                   boolean isRequired,
                                   String applicabilityType,
                                   String value)
        {
            this(ownerName,
                 fieldName,
                 description,
                 charSize,
                 InputDataType.TEXT, // Default
                 isRequired,
                 ApplicabilityType.ALL, // Default
                 value);

            // Step through each field input type
            for (InputDataType type : InputDataType.values())
            {
                // Check if the type matches this field's input type
                if (inputType.equals(type.getInputName()))
                {
                    // Store the field input type and stop searching
                    fieldType = type;
                    break;
                }
            }

            // Step through each field applicability type
            for (ApplicabilityType type : ApplicabilityType.values())
            {
                // Check if the type matches this field's applicability type
                if (applicabilityType.equals(type.getApplicabilityName()))
                {
                    // Store the field applicability type and stop searching
                    applicability = type;
                    break;
                }
            }
        }

        /**********************************************************************
         * Get the name of the field's owner (table or group name)
         *
         * @return Name of the owning table/group to which the field belongs
         *********************************************************************/
        protected String getOwnerName()
        {
            return ownerName;
        }

        /**********************************************************************
         * Get the name of the field's owner (table or group name)
         *
         * @param ownerName
         *            name of the table/group to which the field belongs
         *********************************************************************/
        protected void setOwnerName(String ownerName)
        {
            this.ownerName = ownerName;
        }

        /**********************************************************************
         * Get the field name
         *
         * @return Field name
         *********************************************************************/
        protected String getFieldName()
        {
            return fieldName;
        }

        /**********************************************************************
         * Get the field comment
         *
         * @return Field comment
         *********************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /**********************************************************************
         * Get the field display size in characters
         *
         * @return Field display size in characters
         *********************************************************************/
        protected int getSize()
        {
            return charSize;
        }

        /**********************************************************************
         * Get the field value required flag
         *
         * @return true if a value is required for this field
         *********************************************************************/
        protected boolean isRequired()
        {
            return isRequired;
        }

        /**********************************************************************
         * Get the field applicability type
         *
         * @return Field applicability type
         *********************************************************************/
        protected ApplicabilityType getApplicabilityType()
        {
            return applicability;
        }

        /**********************************************************************
         * Get the field value
         *
         * @return Field value
         *********************************************************************/
        protected String getValue()
        {
            return value;
        }

        /**********************************************************************
         * Set the field value
         *
         * @param value
         *            field value
         *********************************************************************/
        protected void setValue(String value)
        {
            this.value = value;
        }

        /**********************************************************************
         * Get a reference to the field's UndoableTextField or UndoableCheckBox
         * (if boolean)
         *
         * @return Field's UndoableTextField or UndoableCheckBox reference
         *********************************************************************/
        protected Object getInputFld()
        {
            return inputFld;
        }

        /**********************************************************************
         * Set the field's UndoableTextField or UndoableCheckBox
         *
         * @param inputFld
         *            field's UndoableTextField or UndoableCheckBox
         ********************************************************************/
        protected void setInputFld(Object inputFld)
        {
            this.inputFld = inputFld;
        }

        /**********************************************************************
         * Get the field input type
         *
         * @return Field input type
         *********************************************************************/
        protected InputDataType getInputType()
        {
            return fieldType;
        }
    }

    /**************************************************************************
     * Group information class. Associates group names in the database with
     * their respective tables and data fields
     *************************************************************************/
    protected static class GroupInformation
    {
        private String name;
        private String description;
        private Boolean isApplication;
        private final List<String> tablesAndAncestors;
        private final List<String> tableMembers;
        private List<FieldInformation> fieldInformation;

        /**********************************************************************
         * Group information class constructor
         *
         * @param name
         *            group name
         *
         * @param description
         *            group description
         * 
         * @param isApplication
         *            flag indicating if the group represents a CFS application
         * 
         * @param fieldInformation
         *            data field information
         *********************************************************************/
        protected GroupInformation(String name,
                                   String description,
                                   Boolean isApplication,
                                   List<FieldInformation> fieldInformation)
        {
            this(name, description, isApplication, null, fieldInformation);
        }

        /**********************************************************************
         * Group information class constructor
         *
         * @param name
         *            group name
         *
         * @param description
         *            group description
         *
         * @param isApplication
         *            flag indicating if the group represents a CFS application
         * 
         * @param tables
         *            list of tables (with full paths( belonging to this group;
         *            null if creating a new, empty group
         * 
         * @param fieldInformation
         *            data field information
         *********************************************************************/
        protected GroupInformation(String name,
                                   String description,
                                   Boolean isApplication,
                                   List<String> tables,
                                   List<FieldInformation> fieldInformation)
        {
            tablesAndAncestors = new ArrayList<String>();
            tableMembers = new ArrayList<String>();

            this.name = name;
            this.description = description;
            this.isApplication = isApplication;
            this.fieldInformation = new ArrayList<FieldInformation>();

            // Check if field information is provided
            if (fieldInformation != null)
            {
                // Add the field information to the list
                this.fieldInformation.addAll(fieldInformation);
            }

            // Check if the tables aren't supplied
            if (tables == null)
            {
                // Create an empty list for the tables
                tables = new ArrayList<String>();
            }

            // Step through each table path in the group
            for (String table : tables)
            {
                // Add the table to the lists
                addTable(table);
            }
        }

        /**********************************************************************
         * Get the group name
         *
         * @return Group name
         *********************************************************************/
        protected String getName()
        {
            return name;
        }

        /**********************************************************************
         * Set the group name
         *
         * @param name
         *            group name
         *********************************************************************/
        protected void setName(String name)
        {
            this.name = name;
        }

        /**********************************************************************
         * Get the group description
         *
         * @return Group description
         *********************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /**********************************************************************
         * Set the group description
         *
         * @param pathName
         *            group description
         *********************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }

        /**********************************************************************
         * Get the flag that indicates if the group represents a CFS
         * application
         *
         * @return true if the group represents a CFS application
         *********************************************************************/
        protected Boolean isApplication()
        {
            return isApplication;
        }

        /**********************************************************************
         * Set the flag that indicates if the group represents a CFS
         * application
         *
         * @param isApplication
         *            true if the group represents a CFS application
         *********************************************************************/
        protected void setIsApplication(boolean isApplication)
        {
            this.isApplication = isApplication;
        }

        /**********************************************************************
         * Get the group table list that includes the member tables and all of
         * their ancestor tables
         *
         * @return Group table list that includes the member tables and all of
         *         their ancestor tables
         *********************************************************************/
        protected List<String> getTablesAndAncestors()
        {
            return tablesAndAncestors;
        }

        /**********************************************************************
         * Get the list of table members (root tables and parent.child table
         * table pairs) belonging to this group
         *
         * @return List of table members (parent.child table table pairs)
         *         belonging to this group
         *********************************************************************/
        protected List<String> getTableMembers()
        {
            return tableMembers;
        }

        /**********************************************************************
         * Add the specified group table to the lists that include each
         * parent.child table pair and the member table along with its ancestor
         * tables
         * 
         * @param table
         *            group table member (full table path)
         *********************************************************************/
        protected void addTable(String table)
        {
            // Separate the root and parent.child tables in the path
            for (String member : table.split(","))
            {
                // Check if the table isn't in the list
                if (!tableMembers.contains(member))
                {
                    // Add the table to the group's table member list
                    tableMembers.add(member);
                }
            }

            // Check if this isn't a root table
            while (table.contains(","))
            {
                // Check if the table isn't already added to the list
                if (!tablesAndAncestors.contains(table))
                {
                    // Add the member/ancestor table to the list
                    tablesAndAncestors.add(table);
                }

                // Remove the parent.child
                table = table.substring(0, table.lastIndexOf(","));
            }

            // Check if the root table isn't already added to the list
            if (!tablesAndAncestors.contains(table))
            {
                // Add the root table to the list
                tablesAndAncestors.add(table);
            }
        }

        /**********************************************************************
         * Get the group data field information
         *
         * @return Group data field information
         *********************************************************************/
        protected List<FieldInformation> getFieldInformation()
        {
            return fieldInformation;
        }

        /**********************************************************************
         * Set the group data field information
         *
         * @param fieldInformation
         *            group data field information
         *********************************************************************/
        protected void setFieldInformation(List<FieldInformation> fieldInformation)
        {
            this.fieldInformation = fieldInformation;
        }
    }

    /**************************************************************************
     * Link information class
     *************************************************************************/
    protected static class LinkInformation
    {
        private final String rateName;
        private String linkName;
        private String sampleRate;
        private String description;

        /**********************************************************************
         * Link information class constructor
         *
         * @param rateName
         *            name of the data stream rate column
         *
         * @param linkName
         *            link name
         *
         * @param description
         *            link description
         *********************************************************************/
        protected LinkInformation(String rateName,
                                  String linkName,
                                  String description)
        {
            this(rateName, linkName, "0", description);
        }

        /**********************************************************************
         * Link information class constructor
         *
         * @param rateName
         *            name of the data stream rate column associated with this
         *            link
         *
         * @param linkName
         *            link name
         *
         * @param sampleRate
         *            link rate in samples per second
         * 
         * @param description
         *            link description
         *********************************************************************/
        protected LinkInformation(String rateName,
                                  String linkName,
                                  String sampleRate,
                                  String description)
        {
            this.rateName = rateName;
            this.linkName = linkName;
            this.sampleRate = sampleRate;
            this.description = description;
        }

        /**********************************************************************
         * Get the link data stream rate name
         *
         * @return Data stream rate name
         *********************************************************************/
        protected String getRateName()
        {
            return rateName;
        }

        /**********************************************************************
         * Get the link name
         *
         * @return Link name
         *********************************************************************/
        protected String getName()
        {
            return linkName;
        }

        /**********************************************************************
         * Set the link name
         *
         * @param pathName
         *            link name
         *********************************************************************/
        protected void setName(String linkName)
        {
            this.linkName = linkName;
        }

        /**********************************************************************
         * Get the link rate in samples per second
         *
         * @return Link rate in samples per second; return "0" if no rate is
         *         assigned to this link
         *********************************************************************/
        protected String getSampleRate()
        {
            return sampleRate;
        }

        /**********************************************************************
         * Set the link rate in samples per second
         *
         * @param sampleRate
         *            link rate in samples per second
         *********************************************************************/
        protected void setSampleRate(String sampleRate)
        {
            this.sampleRate = sampleRate;
        }

        /**********************************************************************
         * Get the link description
         *
         * @return Link description
         *********************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /**********************************************************************
         * Set the link description
         *
         * @param description
         *            link description
         *********************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }
    }

    /**************************************************************************
     * Array variable utility method class
     *************************************************************************/
    protected static class ArrayVariable
    {
        /**********************************************************************
         * Remove the array variable index, if present, from the supplied
         * variable name
         * 
         * @param variableName
         *            variable name
         * 
         * @return The variable name, with the array index removed
         *********************************************************************/
        protected static String removeArrayIndex(String variableName)
        {
            // Get the index of the array index
            int index = variableName.indexOf('[');

            // Check if an array index exists
            if (index != -1)
            {
                // Remove the array index from the variable name
                variableName = variableName.substring(0, index);
            }

            return variableName;
        }

        /**********************************************************************
         * Remove the string size array variable index, if present, from the
         * supplied variable name
         * 
         * @param variableName
         *            variable name
         * 
         * @return The variable name, with the string size array index removed
         *********************************************************************/
        protected static String removeStringSize(String variableName)
        {
            // Get the index of the last array index, which represents the
            // string size for a string variable
            int index = variableName.lastIndexOf('[');

            // Check if an array index exists
            if (index != -1)
            {
                // Remove the string size array index from the variable name
                variableName = variableName.substring(0, index);
            }

            return variableName;
        }

        /**********************************************************************
         * Get the array variable index, if present, from the supplied variable
         * name
         * 
         * @param variableName
         *            variable name
         * 
         * @return The array index, with the variable name removed
         *********************************************************************/
        protected static String getVariableArrayIndex(String variableName)
        {
            // Get the index of the array index
            int index = variableName.indexOf('[');

            // Check if an array index exists
            if (index != -1)
            {
                // Get the index portion of the array variable name
                variableName = variableName.substring(index);
            }

            return variableName;
        }

        /**********************************************************************
         * Check if a variable name represents an array member
         * 
         * @param variableName
         *            variable name
         * 
         * @return true if the variable is an array member
         *********************************************************************/
        protected static boolean isArrayMember(Object variableName)
        {
            return variableName.toString().endsWith("]");
        }

        /**********************************************************************
         * Add brackets to encompass a variable array index value(s)
         * 
         * @param arrayIndex
         *            array of array variable index value(s)
         * 
         * @return Array index value(s), surrounded by brackets
         *********************************************************************/
        protected static String formatArrayIndex(int[] arrayIndex)
        {
            String arrayIndexS = "";

            // Step through each array dimension
            for (int index = 0; index < arrayIndex.length; index++)
            {
                // Surround the array index with brackets and add it to the
                // array string
                arrayIndexS += "[" + arrayIndex[index] + "]";
            }

            return arrayIndexS;
        }

        /**********************************************************************
         * Parse an array size cell into an array of integers representing each
         * array index
         * 
         * @param arrayString
         *            array size cell value
         * 
         * @return Array of integers representing each array index. An empty
         *         array is returned if the array size cell is blank
         *********************************************************************/
        protected static int[] getArrayIndexFromSize(String arrayString)
        {
            int[] arrayIndex = new int[0];

            // Check if the array size value isn't blank
            if (!arrayString.isEmpty())
            {
                // Split the array size into the separate array index values
                String[] indices = arrayString.split(",");
                arrayIndex = new int[indices.length];

                // Step through each array index value
                for (int index = 0; index < indices.length; index++)
                {
                    // Convert the index value to a number and store it in the
                    // index array
                    arrayIndex[index] = Integer.parseInt(indices[index].trim());
                }
            }

            return arrayIndex;
        }

        /**********************************************************************
         * Get the total number of array members using the data tables array
         * size cell text
         * 
         * @param arraySizeS
         *            string representing the array variable dimension sizes
         * 
         * @return Total number of members in the array; 0 if not an array
         *         variable
         *********************************************************************/
        protected static int getNumMembersFromArraySize(String arraySizeS)
        {
            return getNumMembersFromArrayDimension(getArrayIndexFromSize(arraySizeS));
        }

        /**********************************************************************
         * Get the total number of array members using the array dimension
         * values
         * 
         * @param arraySize
         *            array of the array variable dimension sizes
         * 
         * @return Total number of members in the array; 0 if not an array
         *         variable
         *********************************************************************/
        protected static int getNumMembersFromArrayDimension(int[] arraySize)
        {
            int totalSize = 0;

            // Check if the variable is an array
            if (arraySize.length != 0)
            {
                // Set the total to the first dimension size
                totalSize = arraySize[0];

                // Step through each remaining array dimension, if any
                for (int index = 1; index < arraySize.length; index++)
                {
                    // Multiply the total size by the dimension size
                    totalSize *= arraySize[index];
                }
            }

            return totalSize;
        }

        /**********************************************************************
         * Get the index position of the specified array member
         * 
         * @param arrayIndex
         *            array giving the dimension indices for the specified
         *            array member
         * 
         * @param arraySize
         *            array giving the size of each of the array's dimensions
         * 
         * @return Index position of the array member
         *********************************************************************/
        protected static int getArrayMemberPosition(int[] arrayIndex,
                                                    int[] arraySize)
        {
            int position = 0;

            // Step through the array dimensions
            for (int indexA = 0; indexA < arraySize.length; indexA++)
            {
                int multiplier = 1;

                // Step though the remaining array dimensions
                for (int indexB = indexA + 1; indexB < arraySize.length; indexB++)
                {
                    // Update the multiplier with the current array dimension
                    // size
                    multiplier *= arraySize[indexB];
                }

                // Add the index value for the current dimension multiplied by
                // the subsequent dimension sizes
                position += arrayIndex[indexA] * multiplier;
            }

            return position;
        }
    }

    /**************************************************************************
     * Data stream rate parameter information class
     *************************************************************************/
    protected static class RateInformation
    {
        private String rateName;
        private String streamName;
        private int maxMsgsPerCycle;
        private int maxBytesPerSec;
        private String[] sampleRates;
        private int numSharedTableTypes;

        /**********************************************************************
         * Data stream rate parameter information class constructor
         * 
         * @param rateName
         *            rate column name (viewable) for this data stream
         *********************************************************************/
        RateInformation(String rateName)
        {
            this.rateName = rateName;

            // Initialize the number of table types reference by this rate
            // column
            numSharedTableTypes = 1;

            // Set default values for the rate parameters
            setDefaultValues();
        }

        /**********************************************************************
         * Get the number of table types referencing this rate column name
         * 
         * @return Number of table types referencing this rate column name
         *********************************************************************/
        protected int getNumSharedTableTypes()
        {
            return numSharedTableTypes;
        }

        /**********************************************************************
         * Set the number of table types referencing this rate column name
         * 
         * @param numSharedTableTypes
         *            number of table types referencing this rate column name
         *********************************************************************/
        protected void setNumSharedTableTypes(int numSharedTableTypes)
        {
            this.numSharedTableTypes = numSharedTableTypes;
        }

        /**********************************************************************
         * Set default values for the stream name, maximum messages per cycle,
         * and maximum bytes per second rate parameters
         *********************************************************************/
        protected void setDefaultValues()
        {
            streamName = rateName;
            maxMsgsPerCycle = 1;
            maxBytesPerSec = 56000;
            sampleRates = new String[] {"1"};
        }

        /**********************************************************************
         * Get the rate column name
         * 
         * @return Rate column name
         *********************************************************************/
        protected String getRateName()
        {
            return rateName;
        }

        /**********************************************************************
         * Set the rate column name
         * 
         * @param rateName
         *            rate column name
         *********************************************************************/
        protected void setRateName(String rateName)
        {
            this.rateName = rateName;
        }

        /**********************************************************************
         * Get the data stream name
         * 
         * @return Data stream name
         *********************************************************************/
        protected String getStreamName()
        {
            return streamName;
        }

        /**********************************************************************
         * Set the data stream name
         * 
         * @param streamName
         *            data stream name
         *********************************************************************/
        protected void setStreamName(String streamName)
        {
            this.streamName = streamName;
        }

        /**********************************************************************
         * Get the stream maximum messages per cycle
         * 
         * @return Stream maximum messages per cycle
         *********************************************************************/
        protected int getMaxMsgsPerCycle()
        {
            return maxMsgsPerCycle;
        }

        /**********************************************************************
         * Set the stream maximum messages per cycle
         * 
         * @param rateName
         *            stream maximum messages per cycle
         *********************************************************************/
        protected void setMaxMsgsPerCycle(int maxMsgsPerCycle)
        {
            this.maxMsgsPerCycle = maxMsgsPerCycle;
        }

        /**********************************************************************
         * Get the stream maximum bytes per second
         * 
         * @return Stream maximum bytes per second
         *********************************************************************/
        protected int getMaxBytesPerSec()
        {
            return maxBytesPerSec;
        }

        /**********************************************************************
         * Set the stream maximum bytes per second
         * 
         * @param rateName
         *            stream maximum bytes per second
         *********************************************************************/
        protected void setMaxBytesPerSec(int maxBytesPerSec)
        {
            this.maxBytesPerSec = maxBytesPerSec;
        }

        /**********************************************************************
         * Get the stream sample rate array
         * 
         * @return Stream sample rate array
         *********************************************************************/
        protected String[] getSampleRates()
        {
            return sampleRates;
        }

        /**********************************************************************
         * Set the stream sample rate array
         * 
         * @param rateName
         *            stream sample rate array
         *********************************************************************/
        protected void setSampleRates(String[] sampleRates)
        {
            this.sampleRates = sampleRates;
        }
    }

    /**************************************************************************
     * Node removal results class
     *************************************************************************/
    protected static class NodeCheckResults
    {
        private final boolean isRemoved;
        private boolean isRemoveAll;

        /**********************************************************************
         * Node removal results class constructor
         * 
         * @param isRemoved
         *            true if one or more nodes have been removed
         * 
         * @param isRemoveAll
         *            true if all nodes should be removed
         *********************************************************************/
        NodeCheckResults(boolean isRemoved, boolean isRemoveAll)
        {
            this.isRemoved = isRemoved;
            this.isRemoveAll = isRemoveAll;
        }

        /**********************************************************************
         * Get the status of the flag that indicates if one or more nodes have
         * been removed
         * 
         * @return true if one or more nodes have been removed
         *********************************************************************/
        protected boolean isRemoved()
        {
            return isRemoved;
        }

        /**********************************************************************
         * Get the status of the flag that indicates all nodes should be
         * removed
         * 
         * @return true if all nodes should be removed
         *********************************************************************/
        protected boolean isRemoveAll()
        {
            return isRemoveAll;
        }

        /**********************************************************************
         * Set the status of the flag that indicates all nodes should be
         * removed
         * 
         * @param isRemoveAll
         *            true if all nodes should be removed
         *********************************************************************/
        protected void setRemoveAll(boolean isRemoveAll)
        {
            this.isRemoveAll = isRemoveAll;
        }
    }

    /**************************************************************************
     * Node indices for bit-packed variables class
     *************************************************************************/
    protected static class NodeIndex
    {
        private final int firstIndex;
        private final int lastIndex;
        private final int tableIndex;

        /**********************************************************************
         * Node indices for bit-packed variables class constructor
         * 
         * @param firstIndex
         *            node index for the first member in a group of bit-packed
         *            variable
         * 
         * @param lastIndex
         *            node index for the last member in a group of bit-packed
         *            variables
         * 
         * @param tableIndex
         *            node index for the selected member in a group of
         *            bit-packed variables
         *********************************************************************/
        protected NodeIndex(int firstIndex, int lastIndex, int tableIndex)
        {
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
            this.tableIndex = tableIndex;
        }

        /**********************************************************************
         * Get the node index for the first member in a group of bit-packed
         * variable
         * 
         * @return Node index for the first member in a group of bit-packed
         *         variable
         *********************************************************************/
        protected int getFirstIndex()
        {
            return firstIndex;
        }

        /**********************************************************************
         * Get the node index for the last member in a group of bit-packed
         * variable
         * 
         * @return Node index for the last member in a group of bit-packed
         *         variable
         *********************************************************************/
        protected int getLastIndex()
        {
            return lastIndex;
        }

        /**********************************************************************
         * Get the node index for the selected member in a group of bit-packed
         * variables
         * 
         * @return Node index for the selected member in a group of bit-packed
         *         variable
         *********************************************************************/
        protected int getTableIndex()
        {
            return tableIndex;
        }
    }

    /**************************************************************************
     * Associated variables class
     *************************************************************************/
    protected static class AssociatedVariable
    {
        private final int totalSize;
        private final List<Variable> associates;

        /**********************************************************************
         * Associated variables class constructor. Variables are considered
         * 'associated' if they are bit-wise variables that are packed together
         * or are members of a string
         * 
         * @param totalSize
         *            node index for the first member in a group of bit-packed
         *            variable
         * 
         * @param associates
         *            list of associated variables
         *********************************************************************/
        protected AssociatedVariable(int totalSize, List<Variable> associates)
        {
            this.totalSize = totalSize;
            this.associates = associates;
        }

        /**********************************************************************
         * Get the total size in bytes of the associated variables
         * 
         * @return Total size in bytes of the associated variables
         *********************************************************************/
        protected int getTotalSize()
        {
            return totalSize;
        }

        /**********************************************************************
         * Get the list of associated variables
         * 
         * @return List of associated variables
         *********************************************************************/
        protected List<Variable> getAssociates()
        {
            return associates;
        }
    }

    /**************************************************************************
     * Variable generator class
     *************************************************************************/
    public static class VariableGenerator
    {
        /**********************************************************************
         * Generate a telemetry data object
         * 
         * @param data
         *            member column value
         * 
         * @return Telemetry data object based on the supplied data
         *********************************************************************/
        static Variable generateTelemetryData(String data)
        {
            String[] varData = data.split("\\" + TLM_SCH_SEPARATOR, 2);
            String name = varData[1];
            Float rate = Float.valueOf(varData[0]);
            varData = varData[1].split(",");
            String dataType = varData[varData.length - 1].split("\\.")[0];

            return new TelemetryData(dataType, name, rate);
        }

        /**********************************************************************
         * Create list of variables based on the path and rate passed in. If
         * the path is a unlinked variable it will return a list of one, else
         * it will return a list of the link's variables
         * 
         * @param path
         *            tree path to the variable
         * 
         * @param rate
         *            rate of the variable
         * 
         * @return Telemetry data object
         *********************************************************************/
        static TelemetryData generateTelemetryData(Object[] path, float rate)
        {
            String name = "";

            // Step through the node path
            for (int index = 0; index < path.length; index++)
            {
                // Add each level to the name separated by commas
                name += path[index].toString() + ",";
            }

            // Remove the extra comma
            name = CcddUtilities.removeTrailer(name, ",");

            // Get the variable's data type
            String[] data = path[path.length - 1].toString().split("\\.");

            // Create a variable based on the information
            return new TelemetryData(data[0], name, rate);
        }

        /**********************************************************************
         * Generate an application data object
         * 
         * @param data
         *            member column value
         * 
         * @return Application data object based on the supplied data
         *********************************************************************/
        static Variable generateApplicationData(String data)
        {
            String[] varData = data.split(",", DefaultApplicationField.values().length + 1);

            return new ApplicationData(varData[0],
                                       Float.valueOf(varData[DefaultApplicationField.SCHEDULE_RATE.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.EXECUTION_TIME.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.PRIORITY.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.MESSAGE_RATE.ordinal() + 1]),
                                       varData[DefaultApplicationField.WAKE_UP_NAME.ordinal() + 1],
                                       varData[DefaultApplicationField.WAKE_UP_ID.ordinal() + 1],
                                       Integer.valueOf(varData[DefaultApplicationField.HK_SEND_RATE.ordinal() + 1]),
                                       varData[DefaultApplicationField.HK_WAKE_UP_NAME.ordinal() + 1],
                                       varData[DefaultApplicationField.HK_WAKE_UP_ID.ordinal() + 1],
                                       varData[DefaultApplicationField.SCH_GROUP.ordinal() + 1]);
        }
    }

    /**************************************************************************
     * Variable class. Used to denote a variable that is downlinked
     *************************************************************************/
    public static class Variable implements Comparable<Variable>
    {
        private int size;
        private String pathName;
        private final float rate;

        // Indices of the messages in which the variable is contained
        private final List<Integer> messageIndices;

        /**********************************************************************
         * Variable class constructor
         * 
         * @param size
         *            variable size, bytes
         * 
         * @param pathName
         *            variable path and name
         * 
         * @param rate
         *            variable rate, Hertz
         *********************************************************************/
        protected Variable(int size, String pathName, float rate)
        {
            this.size = size;
            this.pathName = pathName;
            this.rate = rate;
            messageIndices = new ArrayList<Integer>();
        }

        /**********************************************************************
         * Get the size of the variable in bytes
         * 
         * @return Size of the variable in bytes
         *********************************************************************/
        protected int getSize()
        {
            return size;
        }

        /**********************************************************************
         * Set the size of the variable in bytes
         * 
         * @param size
         *            size of the variable
         *********************************************************************/
        protected void setSize(int size)
        {
            this.size = size;
        }

        /**********************************************************************
         * Get the full path and name of the variable. This includes the root
         * structure and all of the variables in this variable's path
         * 
         * @return Variable's full path and name
         *********************************************************************/
        protected String getFullName()
        {
            return pathName;
        }

        /**********************************************************************
         * Set the full path and the name of the variable
         * 
         * @param pathName
         *            variable's full path and name
         *********************************************************************/
        protected void setFullName(String pathName)
        {
            this.pathName = pathName;
        }

        /**********************************************************************
         * Get the variable name
         * 
         * @return Variable name
         *********************************************************************/
        protected String getName()
        {
            return pathName.replaceAll("[^\\.]*\\.|:.*$", "");
        }

        /**********************************************************************
         * Get the bit length of the variable
         * 
         * @return Bit length of the variable
         *********************************************************************/
        protected String getBitLength()
        {
            return pathName.replaceAll("^.*:(.*)$|^.*$", "$1");
        }

        /**********************************************************************
         * Get the rate of the variable
         * 
         * @return Rate of the variable, Hertz
         *********************************************************************/
        protected float getRate()
        {
            return rate;
        }

        /**********************************************************************
         * Get the indices of the messages in which the variable is contained
         * 
         * @return List containing the indices of the messages in which the
         *         variable is contained
         *********************************************************************/
        protected List<Integer> getMessageIndices()
        {
            return messageIndices;
        }

        /**********************************************************************
         * Set the indices for the messages in which the variable is contained
         * 
         * @param msgIndices
         *            array of indices in which the variable is contained
         *********************************************************************/
        protected void setMessageIndices(Integer msgIndices[])
        {
            // Step through each index
            for (int index = 0; index < msgIndices.length; index++)
            {
                // Add the message index
                addMessageIndex(msgIndices[index]);
            }
        }

        /**********************************************************************
         * Add an index to the list of message indices. Duplicate indices are
         * ignored
         * 
         * @param index
         *            index to add
         *********************************************************************/
        protected void addMessageIndex(int index)
        {
            // Check if the message index hasn't already been added
            if (!(messageIndices.contains(index)))
            {
                // Add the index to the list
                messageIndices.add(index);
            }
        }

        /**********************************************************************
         * Implement the compareTo method so a variable can be sorted based on
         * byte size
         * 
         * @param compVar
         *            variable to be compared
         * 
         * @return 0 if the sizes are the same; -1 if the comparison variable's
         *         size if greater than this variable's size; 1 if this
         *         variable's size is greater than the comparison variable's
         *         size *
         *********************************************************************/
        @Override
        public int compareTo(Variable compVar)
        {
            int result = 0;

            if (compVar.getSize() > getSize())
            {
                result = 1;
            }
            else if (compVar.getSize() < getSize())
            {
                result = -1;
            }

            return result;
        }

        /**********************************************************************
         * Get the name of the link to which the item belongs. Override this
         * function if the sub-class can have linked data
         * 
         * @return Link name; null if the item is not a link member
         *********************************************************************/
        protected String getLink()
        {
            return null;
        }
    }

    /**************************************************************************
     * Telemetry data class. Used for the telemetry scheduler
     *************************************************************************/
    protected static class TelemetryData extends Variable
    {
        private String dataType;
        private String link;

        /**********************************************************************
         * Telemetry data class constructor
         * 
         * @param dataType
         *            variable data type
         * 
         * @param pathName
         *            variable path and name
         * 
         * @param rate
         *            variable rate, Hertz
         *********************************************************************/
        protected TelemetryData(String dataType,
                                String pathName,
                                float rate)
        {
            super(dataTypeHandler.getSizeInBytes(dataType),
                  pathName,
                  rate);
            this.dataType = dataType;

            // Initialize the link name to indicate no link membership; the
            // link name is set elsewhere to account for possible changes to
            // link membership
            link = null;
        }

        /**********************************************************************
         * Gets the variable data type
         * 
         * @return Variable data type
         *********************************************************************/
        protected String getDataType()
        {
            return dataType;
        }

        /**********************************************************************
         * Set the variable data type
         * 
         * @param dataType
         *            variable data type
         *********************************************************************/
        protected void setDataType(String dataType)
        {
            // Update the data type in the variable name and path
            setFullName(getFullName().replaceFirst("^(.*,)*"
                                                   + this.dataType
                                                   + "\\.",
                                                   "$1" + dataType + "."));

            this.dataType = dataType;
        }

        /**********************************************************************
         * Get the name of the link for which this variable is a member
         * 
         * @return Name of the link for which this variable is a member; null
         *         if the variable is not a member of a link
         *********************************************************************/
        @Override
        protected String getLink()
        {
            return link;
        }

        /**********************************************************************
         * Set the name of the link for which this variable is a member
         * 
         * @param link
         *            name of the link for which this variable is a member;
         *            null if the variable is not a member of a link
         *********************************************************************/
        protected void setLink(String link)
        {
            this.link = link;
        }
    }

    /**************************************************************************
     * Application data class
     *************************************************************************/
    protected static class ApplicationData extends Variable
    {
        private int priority;
        private int messageRate;
        private String wakeUpName;
        private String wakeUpID;
        private int hkSendRate;
        private String hkWakeUpName;
        private String hkWakeUpID;
        private String schGroup;

        /**********************************************************************
         * Application data class constructor
         * 
         * @param name
         *            application name
         * 
         * @param rate
         *            application run rate
         * 
         * @param runTime
         *            application run time
         * 
         * @param priority
         *            application execution priority
         * 
         * @param messageRate
         *            message rate
         * 
         * @param wakeUpName
         *            application wake-up name
         * 
         * @param wakeUpID
         *            application wake up ID
         * 
         * @param hkSendRate
         *            application housekeeping send rate
         * 
         * @param hkWakeUpName
         *            application wake-up name
         * 
         * @param hkWakeUpID
         *            application wake up ID
         * 
         * @param schGroup
         *            application schedule group
         *********************************************************************/
        protected ApplicationData(String name,
                                  float rate,
                                  int runTime,
                                  int priority,
                                  int messageRate,
                                  String wakeUpname,
                                  String wakeUpID,
                                  int hkSendRate,
                                  String hkWakeUpName,
                                  String hkWakeUpID,
                                  String schGroup)
        {
            // Call the variable constructor
            super(runTime, name, rate);

            this.priority = priority;
            this.messageRate = messageRate;
            this.wakeUpName = wakeUpname;
            this.wakeUpID = wakeUpID;
            this.hkSendRate = hkSendRate;
            this.hkWakeUpName = hkWakeUpName;
            this.hkWakeUpID = hkWakeUpID;
            this.schGroup = schGroup;
        }

        /**********************************************************************
         * Get the application priority
         * 
         * @return Application priority
         *********************************************************************/
        protected int getPriority()
        {
            return priority;
        }

        /**********************************************************************
         * Set the application priority
         * 
         * @param priority
         *            new application priority
         *********************************************************************/
        protected void setPriority(int priority)
        {
            this.priority = priority;
        }

        /**********************************************************************
         * Get the application priority
         * 
         * @return Application priority
         *********************************************************************/
        protected int getMessageRate()
        {
            return messageRate;
        }

        /**********************************************************************
         * Set the application message rate
         * 
         * @param priority
         *            new application message rate
         *********************************************************************/
        protected void setMessageRate(int messageRate)
        {
            this.messageRate = messageRate;
        }

        /**********************************************************************
         * Get the application wake-up name
         * 
         * @return Application wake-up name
         *********************************************************************/
        protected String getWakeUpName()
        {
            return wakeUpName;
        }

        /**********************************************************************
         * Set the application wake-up name
         * 
         * @param schGroup
         *            new application wake-up name
         *********************************************************************/
        protected void setWakeUpName(String wakeUpName)
        {
            this.wakeUpName = wakeUpName;
        }

        /**********************************************************************
         * Get the application wake up ID
         * 
         * @return Application wake up ID
         *********************************************************************/
        protected String getWakeUpID()
        {
            return wakeUpID;
        }

        /**********************************************************************
         * Set the application wake up ID
         * 
         * @param wakeUpID
         *            new application wake up ID
         *********************************************************************/
        protected void setWakeUpID(String wakeUpID)
        {
            this.wakeUpID = wakeUpID;
        }

        /**********************************************************************
         * Get the application housekeeping send rate
         * 
         * @return Application housekeeping send rate
         *********************************************************************/
        protected int getHkSendRate()
        {
            return hkSendRate;
        }

        /**********************************************************************
         * Set the application housekeeping send rate
         * 
         * @param schGroup
         *            new application housekeeping send rate
         *********************************************************************/
        protected void setHkSendRate(int hkSendRate)
        {
            this.hkSendRate = hkSendRate;
        }

        /**********************************************************************
         * Get the application housekeeping wake-up name
         * 
         * @return Application housekeeping wake-up name
         *********************************************************************/
        protected String getHkWakeUpName()
        {
            return hkWakeUpName;
        }

        /**********************************************************************
         * Set the application housekeeping wake-up name
         * 
         * @param schGroup
         *            new application housekeeping wake-up name
         *********************************************************************/
        protected void setHkWakeUpName(String hkName)
        {
            this.hkWakeUpName = hkName;
        }

        /**********************************************************************
         * Get the application housekeeping wake-up ID
         * 
         * @return Application housekeeping wake-up ID
         *********************************************************************/
        protected String getHkWakeUpID()
        {
            return hkWakeUpID;
        }

        /**********************************************************************
         * Set the application housekeeping wake-up ID
         * 
         * @param schGroup
         *            new application housekeeping wake-up ID
         *********************************************************************/
        protected void setHkWakeUpID(String hkID)
        {
            this.hkWakeUpID = hkID;
        }

        /**********************************************************************
         * Get the application schedule group
         * 
         * @return Application schedule group
         *********************************************************************/
        protected String getSchGroup()
        {
            return schGroup;
        }

        /**********************************************************************
         * Set the application schedule group
         * 
         * @param schGroup
         *            new application schedule group
         *********************************************************************/
        protected void setSchGroup(String schGroup)
        {
            this.schGroup = schGroup;
        }
    }

    /**************************************************************************
     * Message class store variable data
     *************************************************************************/
    protected static class Message
    {
        private String name;
        private String id;
        private int bytesRemaining;
        private final Message parentMessage;
        private List<Message> subMessages;
        private final List<Variable> variables;

        /**********************************************************************
         * Constructor for the message class
         *
         * @param messageName
         *            message name
         * 
         * @param messageID
         *            message ID
         * 
         * @param totalBytes
         *            size of the initial number of bytes in the message
         * 
         * @param parentMessage
         *            parent of the sub-message; null if this is not a
         *            sub-message
         * 
         * @param subMessages
         *            sub-message initialization; null if this is not a parent
         *            message
         *********************************************************************/
        protected Message(String messageName,
                          String messageID,
                          int totalBytes,
                          Message parentMessage,
                          List<Message> subMessages)
        {
            name = messageName;
            id = messageID;
            bytesRemaining = totalBytes;
            this.parentMessage = parentMessage;
            this.subMessages = subMessages;
            variables = new ArrayList<Variable>();
        }

        /**********************************************************************
         * Message class constructor for a parent message
         *
         * @param messageName
         *            message name
         * 
         * @param messageID
         *            message ID
         * 
         * @param bytes
         *            size of the initial amount of bytes in the message
         *********************************************************************/
        protected Message(String messageName, String messageID, int bytes)
        {
            this(messageName, messageID, bytes, null, new ArrayList<Message>());

            // Add a new sub-message. This will only happen if the message is a
            // parent
            addNewSubMessage(messageID);
        }

        /**********************************************************************
         * Message class constructor for a sub-message
         *
         * @param subMessageName
         *            sub-message name
         * 
         * @param subMsgID
         *            sub-message ID
         * 
         * @param bytes
         *            size of the initial amount of bytes in the message
         * 
         * @param msg
         *            parent of the sub-message
         *********************************************************************/
        protected Message(String subMessageName,
                          String subMsgID,
                          int bytes,
                          Message msg)
        {
            this(subMessageName, subMsgID, bytes, msg, null);
        }

        /**********************************************************************
         * Get the message name
         * 
         * @return Message name
         *********************************************************************/
        protected String getName()
        {
            String messageName = name;

            return messageName;
        }

        /**********************************************************************
         * Set the message name
         * 
         * @param name
         *            message name
         *********************************************************************/
        protected void setName(String name)
        {
            // Check if this is a parent message
            if (subMessages != null)
            {
                // Step through the sub-messages
                for (Message subMsg : subMessages)
                {
                    // Update the sub-message name to match the parent name
                    // change
                    subMsg.setName(subMsg.getName().replaceFirst(this.name, name));
                }
            }

            this.name = name;
        }

        /**********************************************************************
         * Get the message ID
         * 
         * @return Message ID
         *********************************************************************/
        protected String getID()
        {
            return id;
        }

        /**********************************************************************
         * Set the message ID
         * 
         * @param messageID
         *            message ID
         *********************************************************************/
        protected void setID(String messageID)
        {
            id = messageID;

            // Check if this is a parent message
            if (parentMessage == null)
            {
                // Set the default sub-message ID to match
                getSubMessage(0).id = messageID;
            }
            // Check if this is the default sub-message
            else if (this == parentMessage.getSubMessage(0))
            {
                // Set the parent message ID to match
                parentMessage.id = messageID;
            }
        }

        /**********************************************************************
         * Get the list of sub-messages
         * 
         * @return List of sub-messages
         *********************************************************************/
        protected List<Message> getSubMessages()
        {
            return subMessages;
        }

        /**********************************************************************
         * Get the parent message
         * 
         * @return Parent message
         *********************************************************************/
        protected Message getParentMessage()
        {
            return parentMessage;
        }

        /**********************************************************************
         * Create a new sub-message
         * 
         * @param subMsgID
         *            sub-message ID
         *********************************************************************/
        protected void addNewSubMessage(String subMsgID)
        {
            // Check if the sub-messages doesn't exist
            if (subMessages == null)
            {
                subMessages = new ArrayList<>();
            }

            // Create the name of the sub-message
            String subName = name + "." + getNumberOfSubMessages();

            // Add a sub-message to the message list
            subMessages.add(new Message(subName, subMsgID, bytesRemaining, this));
        }

        /**********************************************************************
         * Delete the sub-message at the specified index
         * 
         * @param index
         *            sub-message index
         *********************************************************************/
        protected void removeSubMessage(int index)
        {
            // Check if the index is allowed
            if (subMessages.size() > index)
            {
                // Remove the message at the index
                subMessages.remove(index);
            }
        }

        /**********************************************************************
         * Get the sub-message at the specified index
         * 
         * @param index
         *            message index
         * 
         * @return The sub-message at the specified index
         *********************************************************************/
        protected Message getSubMessage(int index)
        {
            return subMessages.get(index);
        }

        /**********************************************************************
         * Get the number of sub-messages
         * 
         * @return Number of sub-messages; 0 if there are no sub-messages
         *********************************************************************/
        protected int getNumberOfSubMessages()
        {
            int numMsgs = 0;

            // Check if sub-messages exist
            if (subMessages != null)
            {
                // Store the number of sub-messages
                numMsgs = subMessages.size();
            }

            return numMsgs;
        }

        /**********************************************************************
         * Get the variable at the specified index
         * 
         * @param index
         *            index of the variable
         * 
         * @return Specified variable object
         *********************************************************************/
        protected Variable getVariable(int index)
        {
            return variables.get(index);
        }

        /**********************************************************************
         * Get the index of a variable with the specified name
         * 
         * @param name
         *            name of the variable
         * 
         * @return Index of the variable with the specified name; -1 if the
         *         variable doesn't exist
         *********************************************************************/
        private int getVariableIndex(String name)
        {
            int varIndex = -1;

            // Step through each variable in the message
            for (int index = 0; index < variables.size(); index++)
            {
                // Check if the specified name matches the variable name
                if (name.equals(variables.get(index).getFullName()))
                {
                    // Store the variable's index and stop searching
                    varIndex = index;
                    break;
                }
            }

            return varIndex;
        }

        /**********************************************************************
         * Get the variable with the specified name
         * 
         * @param name
         *            name of the variable
         * 
         * @return Variable object with the specified name
         *********************************************************************/
        protected Variable getVariable(String name)
        {
            Variable variable = null;

            // Check if the variable is in the message
            if (isVariableInMessage(name))
            {
                // Store the variable reference
                variable = variables.get(getVariableIndex(name));
            }
            else
            {
                // Step through all the sub-messages to check if the variable
                // is stored in a sub-message
                for (Message subMsg : subMessages)
                {
                    // Check if the variable is in the sub-message
                    if (subMsg.isVariableInMessage(name))
                    {
                        // Store the variable reference
                        variable = subMsg.getVariables().get(subMsg.getVariableIndex(name));
                    }
                }
            }

            return variable;
        }

        /**********************************************************************
         * Get a list of the variables for this message
         * 
         * @return List of message's variables
         *********************************************************************/
        protected List<Variable> getVariables()
        {
            return variables;
        }

        /**********************************************************************
         * Get the list of all the variables including those within the
         * sub-messages (if applicable)
         * 
         * @return All variables including sub-message variables
         *********************************************************************/
        protected List<Variable> getAllVariables()
        {
            List<Variable> allVar = new ArrayList<Variable>();

            // Add the message's variables
            allVar.addAll(variables);

            // Check if the message has sub-messages
            if (subMessages != null)
            {
                // Step through each sub-message
                for (Message subMessage : subMessages)
                {
                    // Step through each message for the sub-message
                    for (Variable var : subMessage.getAllVariables())
                    {
                        // Check if the variable has already been added to the
                        // list. This can happen if a variable is assigned to
                        // two sub-messages
                        if (!(allVar.contains(var)))
                        {
                            // Add the variable to the list
                            allVar.add(var);
                        }
                    }
                }
            }

            return allVar;
        }

        /**********************************************************************
         * Get all variables for the message including its parent's (if
         * applicable)
         * 
         * @return All variables for the message its parent
         *********************************************************************/
        protected List<Variable> getVariablesWithParent()
        {
            List<Variable> fullPacket = new ArrayList<Variable>();

            // Check if the message has a parent
            if (parentMessage != null)
            {
                // Add the parent's variables to the list
                fullPacket.addAll(parentMessage.getVariables());
            }

            // Add the message's variables to the list
            fullPacket.addAll(variables);

            return fullPacket;
        }

        /**********************************************************************
         * Get the number of variables in the message
         * 
         * @return Number of variables in the message
         *********************************************************************/
        protected int getNumberOfVariables()
        {
            return variables.size();
        }

        /**********************************************************************
         * Add a variable to the message. Append the variable to the end of the
         * list
         * 
         * @param variable
         *            variable to add
         *********************************************************************/
        protected void addVariable(Variable variable)
        {
            addVariable(variable, -1);
        }

        /**********************************************************************
         * Add a variable to the message. Insert the variable into the list at
         * the specified index
         * 
         * @param variable
         *            variable to add
         * 
         * @param index
         *            position in the variable list to insert the variable; -1
         *            to append the variable to the end of the list
         *********************************************************************/
        protected void addVariable(Variable variable, int index)
        {
            // Check if the index indicates that the variable should be added
            // to the end of the list
            if (index == -1)
            {
                // Set the index to the end of the list
                index = variables.size();
            }

            // Insert/append the variable to the list
            variables.add(index, variable);
        }

        /**********************************************************************
         * Remove the variable with the specified name
         * 
         * @param varName
         *            name of the variable to remove
         * 
         * @return Variable object that is removed
         *********************************************************************/
        protected Variable removeVariable(String varName)
        {
            Variable variable = null;

            // Check if the variable is in the message
            if (isVariableInMessage(varName))
            {
                // Remove the variable
                variable = variables.remove(getVariableIndex(varName));
            }
            // Check if the message has a sub-message
            else if (subMessages != null)
            {
                // Step through each message
                for (Message subMsg : subMessages)
                {
                    // Check if the variable is in the message
                    if (subMsg.isVariableInMessage(varName))
                    {
                        // Remove the variable
                        subMsg.removeVariable(varName);
                    }
                }
            }

            return variable;
        }

        /**********************************************************************
         * Get the number of bytes remaining in the message
         * 
         * @return Number of bytes remaining
         *********************************************************************/
        protected int getBytesRemaining()
        {
            // Set the bytes number to the message's bytes
            int numBytes = bytesRemaining;

            // Check if the message has sub-messages
            if (subMessages != null)
            {
                // Get the smallest byte count from the sub-messages
                numBytes = getSmallestSubMessageBytesRemaining();
            }

            return numBytes;
        }

        /**********************************************************************
         * Set the number of bytes remaining in the message
         * 
         * @param bytesRemaining
         *            number of bytes remaining
         *********************************************************************/
        protected void setBytesRemaining(int bytesRemaining)
        {
            this.bytesRemaining = bytesRemaining;
        }

        /**********************************************************************
         * Get the byte count for the sub-message with the least number of
         * bytes remaining
         * 
         * @return Number of bytes remaining
         *********************************************************************/
        private int getSmallestSubMessageBytesRemaining()
        {
            // Initialize the smallest value to the parent's bytes remaining -
            // a sub-message's byte count can't be greater than the parent's,
            // only the same or smaller
            int smallest = bytesRemaining;

            // Step through each sub-message
            for (Message msg : subMessages)
            {
                // Check if the byte count is smaller than the smallest thus
                // far
                if (msg.getBytesRemaining() < smallest)
                {
                    // Store the new smallest byte count
                    smallest = msg.getBytesRemaining();
                }
            }

            return smallest;
        }

        /**********************************************************************
         * Add bytes to the message
         * 
         * @param bytes
         *            number of bytes to add
         *********************************************************************/
        protected void addBytes(int bytes)
        {
            bytesRemaining += bytes;

            // Add the bytes to the sub-messages
            addBytesToSubMessages(bytes);
        }

        /**********************************************************************
         * Add bytes to the sub-messages
         * 
         * @param bytes
         *            bytes to add
         *********************************************************************/
        private void addBytesToSubMessages(int bytes)
        {
            // Check if the message has a sub-message
            if (subMessages != null)
            {
                // Step through each sub-message
                for (Message sub : subMessages)
                {
                    // Add the bytes to the sub-message
                    sub.addBytes(bytes);
                }
            }
        }

        /**********************************************************************
         * Check if a variable is in the message
         * 
         * @param varName
         *            name of variable
         * 
         * @return true if the message contains the specified variable
         *********************************************************************/
        private boolean isVariableInMessage(String varName)
        {
            boolean isInMsg = false;

            // Step through each variable in the message
            for (Variable variable : variables)
            {
                // Check if the variable name matches the specified name
                if (variable.getFullName().equals(varName))
                {
                    // Set the flag indicating the variable is in the message
                    // and stop searching
                    isInMsg = true;
                    break;
                }
            }

            return isInMsg;
        }

        /**********************************************************************
         * Swap the position of two variables
         * 
         * @param index1
         *            first index
         * 
         * @param index2
         *            second index
         *********************************************************************/
        protected void swapVariablePosition(int index1, int index2)
        {
            Collections.swap(variables, index1, index2);
        }
    }

    /**************************************************************************
     * Data stream class. Used to store and retrieve the data from the
     * scheduler handler
     *************************************************************************/
    protected static class DataStream
    {
        private final List<Message> messages;
        private final List<Variable> varList;
        private final String rateName;

        /**********************************************************************
         * Data steam class constructor
         * 
         * @param messages
         *            list of messages for the data stream
         * 
         * @param varList
         *            list of variables in the data stream
         * 
         * @param rateName
         *            rate column name
         *********************************************************************/
        protected DataStream(List<Message> messages,
                             List<Variable> varList,
                             String rateName)
        {
            this.messages = messages;
            this.varList = varList;
            this.rateName = rateName;
        }

        /**********************************************************************
         * Data steam class constructor
         * 
         * @param messages
         *            list of messages for the data stream
         * 
         * @param varList
         *            list of variables in the data stream
         *********************************************************************/
        protected DataStream(List<Message> messages, List<Variable> varList)
        {
            this(messages, varList, "");
        }

        /**********************************************************************
         * Data steam class constructor
         * 
         * @param rateName
         *            rate column name
         *********************************************************************/
        protected DataStream(String rateName)
        {
            this(new ArrayList<Message>(), new ArrayList<Variable>(), rateName);
        }

        /**********************************************************************
         * Data steam class constructor
         * 
         * @param messages
         *            list of messages for the data stream
         * 
         * @param rateName
         *            rate column name
         *********************************************************************/
        protected DataStream(List<Message> messages, String rateName)
        {
            this(messages, new ArrayList<Variable>(), rateName);
        }

        /**********************************************************************
         * Get the variable list for the data stream
         * 
         * @return List of variables in the data stream
         *********************************************************************/
        protected List<Variable> getVariableList()
        {
            return varList;
        }

        /**********************************************************************
         * Get the messages in the data stream
         * 
         * @return List of messages in the data stream
         *********************************************************************/
        protected List<Message> getMessages()
        {
            return messages;
        }

        /**********************************************************************
         * Get the rate column name
         * 
         * @return Rate column name
         *********************************************************************/
        protected String getRateName()
        {
            return rateName;
        }
    }

    /**************************************************************************
     * Action listener to validate the contents of the table cell currently
     * being edited prior to performing an action. This prevents the action
     * from being performed when the cell's contents is invalid (which causes a
     * warning dialog to appear), allowing the invalid value to be corrected
     *************************************************************************/
    abstract static class ValidateCellActionListener implements ActionListener
    {
        private final CcddJTableHandler table;

        /**********************************************************************
         * Validate cell action listener. getTable should be overridden to
         * provide the specific table to validate. If no table is specified
         * then the action is performed
         *********************************************************************/
        ValidateCellActionListener()
        {
            table = null;
        }

        /**********************************************************************
         * Validate cell action listener
         * 
         * @param table
         *            reference to the CcddJTableHandler table to check for
         *            cell validation; null perform the action with no
         *            validation check
         *********************************************************************/
        ValidateCellActionListener(CcddJTableHandler table)
        {
            this.table = table;
        }

        /**********************************************************************
         * Perform the specified action if no cell is currently being edited,
         * or if the contents of the cell currently being edited is valid
         *********************************************************************/
        @Override
        public void actionPerformed(ActionEvent ae)
        {
            // Check if no table is specified, or if the contents of the last
            // cell edited in the specified table is validated
            if (getTable() == null || getTable().isLastCellValid())
            {
                // Perform the action
                performAction(ae);
            }
        }

        /**********************************************************************
         * Placeholder for the action to be performed
         *********************************************************************/
        abstract protected void performAction(ActionEvent ae);

        /**********************************************************************
         * Get the table for which the action is performed
         * 
         * @return Table for which the action is performed
         *********************************************************************/
        protected CcddJTableHandler getTable()
        {
            return table;
        }
    }

    /**************************************************************************
     * Custom combo box with padding for the list items and tool tip text
     *************************************************************************/
    @SuppressWarnings("serial")
    public static class PaddedComboBox extends JComboBox<String>
    {
        private boolean inLayOut = false;

        /**********************************************************************
         * Padded combo box constructor with an empty list
         * 
         * @param font
         *            combo box list item font
         *********************************************************************/
        PaddedComboBox(Font font)
        {
            setListItemCharacteristics(null, font);
        }

        /**********************************************************************
         * Padded combo box constructor with initial list items and no tool tip
         * text
         * 
         * @param items
         *            combo box list items
         * 
         * @param font
         *            combo box list item font
         *********************************************************************/
        PaddedComboBox(String[] items, Font font)
        {
            super(items);

            setListItemCharacteristics(null, font);
        }

        /**********************************************************************
         * Padded combo box constructor with initial list items and tool tip
         * text
         * 
         * @param items
         *            combo box list items
         * 
         * @param toolTips
         *            combo box list items
         * 
         * @param font
         *            combo box list item font
         *********************************************************************/
        PaddedComboBox(String[] items, String[] toolTips, Font font)
        {
            super(items);

            setListItemCharacteristics(toolTips, font);
        }

        /**********************************************************************
         * Override in order to set the flag indicating a layout is in progress
         * for when getSize() is called
         *********************************************************************/
        @Override
        public void doLayout()
        {
            inLayOut = true;
            super.doLayout();
            inLayOut = false;
        }

        /**********************************************************************
         * Override in order to set the combo box width to that of the longest
         * list item
         * 
         * @return Combo box size
         *********************************************************************/
        @Override
        public Dimension getSize()
        {
            Dimension dim = super.getSize();

            // Check that the call to this method didn't originate during a
            // layout operation
            if (!inLayOut)
            {
                // Get the maximum of the width returned by the call to the
                // default getSize() and the preferred width
                dim.width = Math.max(dim.width, getPreferredSize().width);
            }

            return dim;
        }

        /**********************************************************************
         * Set up list item characteristics
         * 
         * @param toolTips
         *            combo box list items
         * 
         * @param font
         *            combo box list item font
         *********************************************************************/
        private void setListItemCharacteristics(final String[] toolTips,
                                                Font font)
        {
            // Set the foreground color, background color, and font for the
            // list items, and the maximum number of items to display
            setForeground(Color.BLACK);
            setBackground(Color.WHITE);
            setFont(font);
            setMaximumRowCount(15);

            // Set the renderer
            setRenderer(new DefaultListCellRenderer()
            {
                /**************************************************************
                 * Override so that tool tip text can be displayed for each
                 * list item and padding is added to the items
                 *************************************************************/
                @Override
                public Component getListCellRendererComponent(JList<?> list,
                                                              Object value,
                                                              int index,
                                                              boolean isSelected,
                                                              boolean cellHasFocus)
                {
                    JLabel lbl = (JLabel) super.getListCellRendererComponent(list,
                                                                             value,
                                                                             index,
                                                                             isSelected,
                                                                             cellHasFocus);

                    // Add padding to the list item
                    lbl.setBorder(BorderFactory.createEmptyBorder(CELL_VERTICAL_PADDING - 2,
                                                                  CELL_HORIZONTAL_PADDING - 2,
                                                                  CELL_VERTICAL_PADDING - 2,
                                                                  CELL_HORIZONTAL_PADDING - 2));

                    // Check if the list item is valid and if it has tool tip
                    // text associated with it
                    if (index > -1
                        && toolTips != null
                        && index < toolTips.length
                        && value != null)
                    {
                        // Set the item's tool tip text
                        list.setToolTipText(toolTips[index]);
                    }

                    return lbl;
                }
            });
        }

        /**********************************************************************
         * Get the list index of the specified list item
         * 
         * @param itemName
         *            name of the list item for which to search
         * 
         * @return the index of the specified item in the list; -1 if the item
         *         isn't in the list
         *********************************************************************/
        protected int getIndexOfItem(String itemName)
        {
            int itemIndex = -1;

            // Step through each item in the list
            for (int index = 0; index < getItemCount(); index++)
            {
                // Check if list item matches the specified item
                if (getItemAt(index).equals(itemName))
                {
                    // Store the list index and stop searching
                    itemIndex = index;
                    break;
                }
            }

            return itemIndex;
        }
    }

    /**************************************************************************
     * Custom split pane class
     *************************************************************************/
    @SuppressWarnings("serial")
    public static class CustomSplitPane extends JSplitPane
    {
        /**********************************************************************
         * Custom split pane class constructor. Create a horizontally divided
         * split pane using the specified components. If no divider component
         * is provided then the divider blends in with the dialog background.
         * If a component is specified then the divider is displayed using the
         * supplied component
         * 
         * @param leftComp
         *            component to display in the left side of the split pane
         * 
         * @param rightComp
         *            component to display in the right side of the split pane
         * 
         * @param dividerComp
         *            component to display in place of the divider; null to
         *            create a hidden divider
         *********************************************************************/
        CustomSplitPane(Component leftComp,
                        Component rightComp,
                        final Component dividerComp)
        {
            super(JSplitPane.HORIZONTAL_SPLIT, true, leftComp, rightComp);

            // Set the pane's UI so that the divider isn't displayed
            setUI(new BasicSplitPaneUI()
            {
                /**************************************************************
                 * Override so that the divider isn't displayed or is replaced
                 * with the specified component
                 *************************************************************/
                @Override
                public BasicSplitPaneDivider createDefaultDivider()
                {
                    // Create the divider
                    BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this)
                    {
                        /******************************************************
                         * Override so that the divider border isn't displayed
                         *****************************************************/
                        @Override
                        public void setBorder(Border border)
                        {
                        }

                        /******************************************************
                         * Override so that the divider size can be set to the
                         * divider component, if applicable
                         *****************************************************/
                        @Override
                        public int getDividerSize()
                        {
                            int size = LABEL_HORIZONTAL_SPACING / 2;

                            // Check if a component is provided to represent
                            // the divider
                            if (dividerComp != null)
                            {
                                // Set the size to the divider component's
                                // preferred width
                                size = dividerComp.getPreferredSize().width;
                            }

                            return size;
                        }

                    };

                    // Check if a component is provided to represent the
                    // divider
                    if (dividerComp != null)
                    {
                        // Add the divider component to the divider
                        divider.setLayout(new BorderLayout());
                        divider.add(dividerComp, BorderLayout.CENTER);
                    }

                    return divider;
                }
            });

            // Set the split pane characteristics
            setBorder(null);
            setResizeWeight(0.5);
        }
    }

    /**************************************************************************
     * Selected table cell class
     *************************************************************************/
    protected static class SelectedCell
    {
        private int row;
        private int column;

        /**********************************************************************
         * Selected table cell class constructor. An instance is created for
         * each selected cell in the table
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         *********************************************************************/
        protected SelectedCell(int row, int column)
        {
            this.row = row;
            this.column = column;
        }

        /**********************************************************************
         * Get the cell's row index
         * 
         * @return Cell's row index
         *********************************************************************/
        protected int getRow()
        {
            return row;
        }

        /**********************************************************************
         * Set the cell's row index
         * 
         * @param row
         *            cell's row index
         *********************************************************************/
        protected void setRow(int row)
        {
            this.row = row;
        }

        /**********************************************************************
         * Get the cell's column index
         * 
         * @return Cell's column index
         *********************************************************************/
        protected int getColumn()
        {
            return column;
        }

        /**********************************************************************
         * Set the cell's column index
         * 
         * @param column
         *            cell's column index
         *********************************************************************/
        protected void setColumn(int column)
        {
            this.column = column;
        }

        /**********************************************************************
         * Determine if the cell coordinates match the ones supplied
         * 
         * @param checkRow
         *            row index in view coordinates to compare to this cell's
         *            row index
         * 
         * @param checkColumn
         *            column index in view coordinates to compare to this
         *            cell's column index
         * 
         * @return true if the cell's row and column indices match the
         *         specified ones
         *********************************************************************/
        protected boolean isCell(int checkRow, int checkColumn)
        {
            return row == checkRow && column == checkColumn;
        }
    }

    /**************************************************************************
     * Table cell selection handler
     *************************************************************************/
    protected static class CellSelectionHandler
    {
        // List containing the selected cells
        private final ArrayList<SelectedCell> cells;

        /**********************************************************************
         * Table cell selection handler constructor
         *********************************************************************/
        protected CellSelectionHandler()
        {
            cells = new ArrayList<SelectedCell>();
        }

        /**********************************************************************
         * Get the reference for the specified selected cell
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         * 
         * @return Reference to the specified cell
         *********************************************************************/
        protected SelectedCell getSelectedCell(int row, int column)
        {
            SelectedCell selectedCell = null;

            // Step through each selected cell
            for (SelectedCell cell : cells)
            {
                // Check if the cell is already in the list
                if (cell.isCell(row, column))
                {
                    // Store the selected cell reference and stop searching
                    selectedCell = cell;
                    break;
                }
            }

            return selectedCell;
        }

        /**********************************************************************
         * Get the list of selected cells
         * 
         * @return List of selected cells
         *********************************************************************/
        protected ArrayList<SelectedCell> getSelectedCells()
        {
            return cells;
        }

        /**********************************************************************
         * Add the cell, specified by the supplied coordinates, to the list of
         * selected cells if it s not already in the list
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         *********************************************************************/
        protected void add(int row, int column)
        {
            // Check if the cell is not already in the selected cells list
            if (!contains(row, column))
            {
                // Add the cell to the list
                cells.add(new SelectedCell(row, column));
            }
        }

        /**********************************************************************
         * Remove the cell, specified by the supplied coordinates, from the
         * list of selected cells
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         *********************************************************************/
        protected void remove(int row, int column)
        {
            // Step through each selected cell
            for (SelectedCell cell : cells)
            {
                // Check if the cell is in the list
                if (cell.isCell(row, column))
                {
                    // Remove the cell from the list and stop searching
                    cells.remove(cell);
                    break;
                }
            }
        }

        /**********************************************************************
         * Remove any cells in the selected cells list, then add the cell,
         * specified by the supplied coordinates, to the list
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         *********************************************************************/
        protected void reset(int row, int column)
        {
            // Remove any cells currently in the list
            cells.clear();

            // Add the specified cell to the list
            cells.add(new SelectedCell(row, column));
        }

        /**********************************************************************
         * Clear the list of selected cells
         *********************************************************************/
        protected void clear()
        {
            cells.clear();
        }

        /**********************************************************************
         * Determine if the cell specified by the supplied coordinates is
         * already in the list
         * 
         * @param row
         *            cell row in view coordinates
         * 
         * @param column
         *            cell column in view coordinates
         * 
         * @return true if the cell is already in the selection list
         *********************************************************************/
        protected boolean contains(int row, int column)
        {
            return getSelectedCell(row, column) != null;
        }

        /**********************************************************************
         * Determine if only one or no cells are currently selected
         * 
         * @return true is one or no cells are currently selected
         *********************************************************************/
        protected boolean isOneOrNone()
        {
            return cells.size() <= 1;
        }
    }

    /**************************************************************************
     * Array list class with case insensitive contains method
     *************************************************************************/
    @SuppressWarnings("serial")
    protected static class ArrayListCaseInsensitive extends ArrayList<String>
    {
        /**********************************************************************
         * Override the contains method. Compare the input object to each
         * member of the list, ignoring case, and return true if a match is
         * found
         *********************************************************************/
        @Override
        public boolean contains(Object o)
        {
            boolean isMatch = false;
            String checkString = (String) o;

            // Step through each string in the list
            for (String listString : this)
            {
                // Check if the strings match (case insensitive)
                if (checkString.equalsIgnoreCase(listString))
                {
                    // Set the flag to indicate a match and stop searching
                    isMatch = true;
                    break;
                }
            }

            return isMatch;
        }
    }

    /**************************************************************************
     * Array list class with string arrays. The array column for use with the
     * indexOf and contains methods can be specified (defaults to column 0)
     *************************************************************************/
    @SuppressWarnings("serial")
    protected static class ArrayListMultiple extends ArrayList<String[]>
    {
        private final int compareColumn;

        /**********************************************************************
         * Array list class constructor with string arrays; sets the comparison
         * column
         * 
         * @param compareColumn
         *            index of the column for indexOf and contains comparisons
         **********************************************************************/
        ArrayListMultiple(int compareColumn)
        {
            this.compareColumn = compareColumn;
        }

        /**********************************************************************
         * Array list class constructor with string arrays; assumes the first
         * column is the comparison column
         **********************************************************************/
        ArrayListMultiple()
        {
            this(0);
        }

        /**********************************************************************
         * Override the contains method. Compare the input object to the first
         * string in each array member in the list and return true is a match
         * is found
         *********************************************************************/
        @Override
        public boolean contains(Object obj)
        {
            return indexOf(obj) != -1;
        }

        /**********************************************************************
         * Override the indexOf method. Compare the input object to the first
         * string in each array member in the list and return the index of the
         * matching array, or -1 if no match is found
         *********************************************************************/
        @Override
        public int indexOf(Object obj)
        {
            int matchIndex = -1;
            int index = 0;
            String checkString = (String) obj;

            // Step through each string array in the list
            for (String[] listString : this)
            {
                // Check if the input string matches the first one in the array
                if (checkString.equals(listString[compareColumn]))
                {
                    // Set the index to the matching one and stop searching
                    matchIndex = index;
                    break;
                }

                index++;
            }

            return matchIndex;
        }
    }

    /**************************************************************************
     * WrapLayout layout manager class (by Rob Camick; tips4java.wordpress.com;
     * public domain)
     *
     * This open source code modifies the standard FlowLayout manager to
     * include wrapping components to new lines if the container is resized
     * smaller than that needed to display the components. The original code is
     * reformatted and amended to account for a scroll pane sharing the
     * container in which the WrapLayout container is located; without the
     * update the wrapping is negated whenever the scroll pane's horizontal
     * scroll bar appears
     *************************************************************************/
    @SuppressWarnings("serial")
    protected static class WrapLayout extends FlowLayout
    {
        /**********************************************************************
         * WrapLayout constructor. Creates a new WrapLayout with a left
         * alignment and a default 5-unit horizontal and vertical gap.
         *********************************************************************/
        public WrapLayout()
        {
            super();
        }

        /**********************************************************************
         * Constructs a new FlowLayout with the specified alignment and a
         * default 5-unit horizontal and vertical gap. The value of the
         * alignment argument must be one of WrapLayout, WrapLayout, or
         * WrapLayout
         *
         * @param align
         *            the alignment value
         *********************************************************************/
        WrapLayout(int align)
        {
            super(align);
        }

        /**********************************************************************
         * Returns the preferred dimensions for this layout given the visible
         * components in the specified target container
         *
         * @param target
         *            the component which needs to be laid out
         *
         * @return The preferred dimensions to lay out the subcomponents of the
         *         specified container
         *********************************************************************/
        @Override
        public Dimension preferredLayoutSize(Container target)
        {
            return layoutSize(target, true);
        }

        /**********************************************************************
         * Returns the minimum dimensions needed to layout the visible
         * components contained in the specified target container
         *
         * @param target
         *            the component which needs to be laid out
         *
         * @return The minimum dimensions to lay out the subcomponents of the
         *         specified container
         *********************************************************************/
        @Override
        public Dimension minimumLayoutSize(Container target)
        {
            // CCDD: Updated the flag in this call to true in order to force
            // use of the preferred size
            Dimension minimum = layoutSize(target, true);
            minimum.width -= (getHgap() + 1);
            return minimum;
        }

        /**********************************************************************
         * Returns the minimum or preferred dimension needed to layout the
         * target container.
         *
         * @param target
         *            target to get layout size for
         *
         * @param preferred
         *            should preferred size be calculated
         *
         *            * @return The dimension to layout the target container
         *********************************************************************/
        private Dimension layoutSize(Container target, boolean preferred)
        {
            synchronized (target.getTreeLock())
            {
                // Each row must fit with the width allocated to the container.
                // When the container width = 0, the preferred width of the
                // container has not yet been calculated so get the maximum
                int targetWidth = target.getSize().width;
                Container container = target;

                while (container.getSize().width == 0
                       && container.getParent() != null)
                {
                    container = container.getParent();
                }

                targetWidth = container.getSize().width;

                if (targetWidth == 0)
                {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left
                                             + insets.right
                                             + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                // Fit components into the allowed width
                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;
                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++)
                {
                    Component m = target.getComponent(i);

                    if (m.isVisible())
                    {
                        // CCDD: Added separator handling
                        // Check if the component is a JSeparator
                        if (m instanceof JSeparator)
                        {
                            m.setPreferredSize(new Dimension(maxWidth,
                                                             ((JSeparator) m).getOrientation() == JSeparator.HORIZONTAL
                                                                                                                       ? 3
                                                                                                                       : -LABEL_VERTICAL_SPACING
                                                                                                                       * 2
                                                                                                                       / 3
                                                                                                                       - 1));
                        }
                        // CCDD: End modification

                        Dimension d = preferred
                                               ? m.getPreferredSize()
                                               : m.getMinimumSize();

                        // Can't add the component to current row. Start a new
                        // row
                        if (rowWidth + d.width > maxWidth)
                        {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        // Add a horizontal gap for all components after the
                        // first
                        if (rowWidth != 0)
                        {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                addRow(dim, rowWidth, rowHeight);
                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                // When using a scroll pane or the DecoratedLookAndFeel we need
                // to make sure the preferred size is less than the size of the
                // target container so shrinking the container size works
                // correctly. Removing the horizontal gap is an easy way to do
                // this
                Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class,
                                                                         target);

                if (scrollPane != null && target.isValid())
                {
                    dim.width -= (hgap + 1);
                }

                return dim;
            }
        }

        /**********************************************************************
         * A new row has been completed. Use the dimensions of this row to
         * update the preferred size for the container.
         *
         * @param dim
         *            update the width and height when appropriate
         *
         * @param rowWidth
         *            the width of the row to add
         *
         * @param rowHeight
         *            the height of the row to add
         *********************************************************************/
        private void addRow(Dimension dim, int rowWidth, int rowHeight)
        {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0)
            {
                dim.height += getVgap();
            }

            dim.height += rowHeight;
        }
    }
}
