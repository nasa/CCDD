/**
 * CFS Command and Data Dictionary common classes.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.MACRO_IDENTIFIER;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.SELECTION_ITEM_LIST_SEPARATOR;
import static CCDD.CcddConstants.TLM_SCH_SEPARATOR;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InputTypeFormat;

/**************************************************************************************************
 * CFS Command and Data Dictionary common classes class
 *************************************************************************************************/
public class CcddClassesDataTable
{
    // Main class reference
    private static CcddMain ccddMain;

    /**********************************************************************************************
     * Set the main class reference
     *
     * @param main
     *            main class reference
     *********************************************************************************************/
    protected static void setHandlers(CcddMain main)
    {
        ccddMain = main;
    }

    /**********************************************************************************************
     * CCDD exception class
     *********************************************************************************************/
    @SuppressWarnings("serial")
    protected static class CCDDException extends Exception
    {
        private final String message;
        private final int messageType;

        /******************************************************************************************
         * CCDD exception class constructor for user-defined error message types
         *
         * @param message
         *            exception message
         *
         * @param messageType
         *            JOptionPane message type
         *****************************************************************************************/
        protected CCDDException(String message, int messageType)
        {
            this.message = message;
            this.messageType = messageType;
        }

        /******************************************************************************************
         * CCDD exception class constructor for an error message type
         *
         * @param message
         *            exception message
         *****************************************************************************************/
        protected CCDDException(String message)
        {
            this(message, JOptionPane.ERROR_MESSAGE);
        }

        /******************************************************************************************
         * CCDD exception class constructor for empty error message
         *****************************************************************************************/
        protected CCDDException()
        {
            this("", JOptionPane.ERROR_MESSAGE);
        }

        /******************************************************************************************
         * Get CCDD exception message
         *
         * @return Exception message
         *****************************************************************************************/
        @Override
        public String getMessage()
        {
            return message;
        }

        /******************************************************************************************
         * Get CCDD exception message type
         *
         * @return Exception message type
         *****************************************************************************************/
        public int getMessageType()
        {
            return messageType;
        }
    }

    /**********************************************************************************************
     * Table information class
     *
     * A table can be one (or possibly more) of the following: prototype, root, parent, and child.
     * A prototype table is produced whenever the Data | New table(s) menu command is used to
     * create a table. The prototype becomes a 'rubber stamp' for creating other table instances
     * that initially contain the same information as the prototype. A root table is a prototype
     * that is not referenced from within another table (making it a top level table). Therefore a
     * root table is always a prototype table, but the reverse isn't necessarily true. Other tables
     * may be referenced within the root table - these referenced tables are known as child tables.
     * A table's parent table is the table in which the child table is directly referenced. The
     * parent table can itself be a child table, or the parent and root tables are the same in the
     * case where the child is referenced directly in the root table
     *********************************************************************************************/
    protected static class TableInformation
    {
        private String tableType;
        private String tablePath;
        private Object[][] tableData;
        private String columnOrder;
        private String description;
        private boolean isPrototype;
        private boolean errorFlag;
        private List<FieldInformation> fieldInformation;

        /******************************************************************************************
         * Table information class constructor
         *
         * @param tableType
         *            table type
         *
         * @param tablePath
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *
         * @param tableData
         *            two-dimensional table data array (rows x columns)
         *
         * @param columnOrder
         *            table column display order in the format
         *            column#0[:column#1[:...[:column#N]]]. The column numbers are based on the
         *            position of the column's definition in the table's type definition
         *
         * @param description
         *            table description
         *****************************************************************************************/
        TableInformation(String tableType,
                         String tablePath,
                         Object[][] tableData,
                         String columnOrder,
                         String description)
        {
            this.tableType = tableType;
            this.tablePath = tablePath;
            this.tableData = tableData;
            this.columnOrder = columnOrder;
            this.description = description;
            isPrototype = !tablePath.contains(".");
            errorFlag = false;
        }

        /******************************************************************************************
         * Table information class constructor. Used when the array of field definitions are
         * retrieved from the database. These are converted to a list of FieldInformation
         * references
         *
         * @param tableType
         *            table type
         *
         * @param tablePath
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *
         * @param tableData
         *            two-dimensional table data array (rows x columns)
         *
         * @param columnOrder
         *            table column display order in the format
         *            column#0[:column#1[:...[:column#N]]]. The column numbers are based on the
         *            position of the column's definition in the table's type definition
         *
         * @param description
         *            table description
         *
         * @param fieldInformation
         *            list of field information
         *****************************************************************************************/
        TableInformation(String tableType,
                         String tablePath,
                         Object[][] tableData,
                         String columnOrder,
                         String description,
                         List<FieldInformation> fieldInformation)
        {
            this(tableType, tablePath, tableData, columnOrder, description);

            // Check if the data field information is provided
            if (fieldInformation != null)
            {
                // Store the field information
                this.fieldInformation = fieldInformation;
            }
        }

        /******************************************************************************************
         * Table information class constructor for a table that couldn't be loaded from the
         * database
         *
         * @param tablePath
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *****************************************************************************************/
        TableInformation(String tablePath)
        {
            this.tablePath = tablePath;
            errorFlag = true;
        }

        /******************************************************************************************
         * Get the table type
         *
         * @return Table type
         *****************************************************************************************/
        protected String getType()
        {
            return tableType;
        }

        /******************************************************************************************
         * Set the table type
         *
         * @param tableType
         *            table type
         *****************************************************************************************/
        protected void setType(String tableType)
        {
            this.tableType = tableType;
        }

        /******************************************************************************************
         * Get the table name in the form prototypeName.variableName, where prototypeName is the
         * name of the prototype structure for this table, and variableName is the name of this
         * particular instantiation of the prototype table. variableName is blank for a top level
         * structure or non-structure table
         *
         * @return Table prototype name + variable name
         *****************************************************************************************/
        protected String getProtoVariableName()
        {
            return getProtoVariableName(getTablePath());
        }

        /******************************************************************************************
         * Get the table name from the supplied table path in the form prototypeName.variableName,
         * where prototypeName is the name of the prototype structure for this table, and
         * variableName is the name of this particular instantiation of the prototype table.
         * variableName is blank for a top level structure or non-structure table
         *
         * @param path
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]
         *
         * @return Table prototype name + variable name
         *****************************************************************************************/
        protected static String getProtoVariableName(String path)
        {
            return path.substring(path.lastIndexOf(',') + 1);
        }

        /******************************************************************************************
         * Get the table's prototype table name
         *
         * @return Table's prototype table name
         *****************************************************************************************/
        protected String getPrototypeName()
        {
            return getPrototypeName(getTablePath());
        }

        /******************************************************************************************
         * Get the table's prototype table name from the supplied path
         *
         * @param path
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]
         *
         * @return Table's prototype table name
         *****************************************************************************************/
        protected static String getPrototypeName(String path)
        {
            // Remove any path prior to the last data type and variable name
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

        /******************************************************************************************
         * Get the table's path in the format rootTable[,dataType1.variable1[,dataType2
         * .variable2[,...]]]. The table path for a non-structure table is simply the root table
         * name. For a structure table the root table is the top level structure table from which
         * this table descends. The first data type/variable name pair is from the root table, with
         * each succeeding pair coming from the next level down in the structure's hierarchy
         *
         * @return Table path
         *****************************************************************************************/
        protected String getTablePath()
        {
            return tablePath;
        }

        /******************************************************************************************
         * Set the table path
         *
         * @param tablePath
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *****************************************************************************************/
        protected void setTablePath(String tablePath)
        {
            this.tablePath = tablePath;
            isPrototype = !tablePath.contains(".");
        }

        /******************************************************************************************
         * Get the table's parent table name. The parent table is the structure table for which the
         * table represented by this class instance is a direct member. The parent and root tables
         * are the same if this table is a direct child of the root table
         *
         * @return Table's parent table name; blank if this is a prototype or root table
         *****************************************************************************************/
        protected String getParentTable()
        {
            return getParentTable(getTablePath());
        }

        /******************************************************************************************
         * Get the table's parent table name. The parent table is the structure table for which the
         * table represented by this class instance is an immediate descendant (child). The parent
         * and root tables are the same if this table is a child of a root table
         *
         * @param path
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *
         * @return Table's parent table name; blank if this is a prototype or root table
         *****************************************************************************************/
        protected static String getParentTable(String path)
        {
            String parent = "";

            // Get the last variable in the table path
            int index = path.lastIndexOf(",");

            // Check that a variable exists
            if (index != -1)
            {
                // Get the name of the table immediately above this table in the path hierarchy
                parent = getPrototypeName(path.substring(0, index));
            }

            return parent;
        }

        /******************************************************************************************
         * Get the table's root table name. For a structure table the root table is the top level
         * table from which the table represented by this class instance descends. For a
         * non-structure table the root table is the table name
         *
         * @return Table's root table name
         *****************************************************************************************/
        protected String getRootTable()
        {
            return getRootTable(tablePath);
        }

        /******************************************************************************************
         * Get the table's root table name. For a structure table the root table is the top level
         * table from which the table represented by this class instance descends. For a
         * non-structure table the root table is the table name
         *
         * @param path
         *            table path in the format rootTable[,dataType1.variable1[,dataType2
         *            .variable2[,...]]]. The table path for a non-structure table is simply the
         *            root table name. For a structure table the root table is the top level
         *            structure table from which this table descends. The first data type/variable
         *            name pair is from the root table, with each succeeding pair coming from the
         *            next level down in the structure's hierarchy
         *
         * @return Table's root table name
         *****************************************************************************************/
        protected static String getRootTable(String path)
        {
            return path.replaceFirst(",.*$", "");
        }

        /******************************************************************************************
         * Set the table's root table name. For a structure table the root table is the top level
         * table from which the table represented by this class instance descends. For a
         * non-structure table the root table is the table name
         *
         * @param rootTable
         *            root table name
         *****************************************************************************************/
        protected void setRootTable(String rootTable)
        {
            tablePath = tablePath.replaceFirst("^.*?(,|$)", rootTable + "$1");
        }

        /******************************************************************************************
         * Get the table data array
         *
         * @return Table data array
         *****************************************************************************************/
        protected Object[][] getData()
        {
            return tableData;
        }

        /******************************************************************************************
         * Set the table data array
         *
         * @param tableData
         *            table data array
         *****************************************************************************************/
        protected void setData(Object[][] tableData)
        {
            this.tableData = tableData;
        }

        /******************************************************************************************
         * Get the table column order
         *
         * @return Table column order
         *****************************************************************************************/
        protected String getColumnOrder()
        {
            return columnOrder;
        }

        /******************************************************************************************
         * Set the table column order
         *
         * @param columnOrder
         *            table column order
         *****************************************************************************************/
        protected void setColumnOrder(String columnOrder)
        {
            this.columnOrder = columnOrder;
        }

        /******************************************************************************************
         * Get the table description
         *
         * @return Table description
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Set the table description
         *
         * @param description
         *            table description
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            this.description = description.trim();
        }

        /******************************************************************************************
         * Get the flag indicating if this is a prototype table
         *
         * @return true if the table is a prototype
         *****************************************************************************************/
        protected boolean isPrototype()
        {
            return isPrototype;
        }

        /******************************************************************************************
         * Get the status of the error flag
         *
         * @return true if an error occurred obtaining the table data from the database
         *****************************************************************************************/
        protected boolean isErrorFlag()
        {
            return errorFlag;
        }

        /******************************************************************************************
         * Set the error flag to true
         *****************************************************************************************/
        protected void setErrorFlag()
        {
            errorFlag = true;
        }

        /******************************************************************************************
         * Get the table data field information
         *
         * @return Table data field information
         *****************************************************************************************/
        protected List<FieldInformation> getFieldInformation()
        {
            return fieldInformation;
        }

        /******************************************************************************************
         * Set the table data field information
         *
         * @param fieldInformation
         *            table data field information
         *****************************************************************************************/
        protected void setFieldInformation(List<FieldInformation> fieldInformation)
        {
            this.fieldInformation = fieldInformation;
        }
    }

    /**********************************************************************************************
     * Table members class
     *********************************************************************************************/
    protected static class TableMembers
    {
        private final String name;
        private final String type;
        private final List<String> dataTypes;
        private final List<String> variableNames;
        private final List<String> bitLengths;
        private final List<String[]> rates;

        /******************************************************************************************
         * Table members class constructor
         *
         * @param name
         *            table name
         *
         * @param type
         *            table type
         *
         * @param dataTypes
         *            list of non-primitive data types that are referenced by this table
         *
         * @param variableNames
         *            list of the variable names associated with the data types
         *
         * @param bitLengths
         *            list of bit lengths associated with the variables
         *
         * @param rates
         *            list of the rates associated with the variables
         *****************************************************************************************/
        TableMembers(String name,
                     String type,
                     List<String> dataTypes,
                     List<String> variableNames,
                     List<String> bitLengths,
                     List<String[]> rates)
        {
            this.name = name;
            this.type = type;
            this.dataTypes = dataTypes;
            this.variableNames = variableNames;
            this.bitLengths = bitLengths;
            this.rates = rates;
        }

        /******************************************************************************************
         * Get the table name
         *
         * @return Table name
         *****************************************************************************************/
        protected String getTableName()
        {
            return name;
        }

        /******************************************************************************************
         * Get the table type
         *
         * @return Table type
         *****************************************************************************************/
        protected String getTableType()
        {
            return type;
        }

        /******************************************************************************************
         * Get the list of this table's non-primitive data types (i.e., references to other tables)
         *
         * @return List of this table's data types
         *****************************************************************************************/
        protected List<String> getDataTypes()
        {
            return dataTypes;
        }

        /******************************************************************************************
         * Get the list of this table's non-primitive variable names associated with the data types
         *
         * @return List of this table's variable names
         *****************************************************************************************/
        protected List<String> getVariableNames()
        {
            return variableNames;
        }

        /******************************************************************************************
         * Get the list of this table's rates associated with the variables
         *
         * @return List of this table's rates
         *****************************************************************************************/
        protected List<String[]> getRates()
        {
            return rates;
        }

        /******************************************************************************************
         * Get the full variable name (in the format dataType.variableName) at the specified index
         *
         * @param index
         *            index into this member's variable information lists
         *
         * @return Full variable name at the specified index
         *****************************************************************************************/
        protected String getFullVariableName(int index)
        {
            String varName = dataTypes.get(index) + "." + variableNames.get(index);

            return varName;
        }

        /******************************************************************************************
         * Get the full variable name (in the format dataType.variableName:bitLength) at the
         * specified index, including the bit length, if present
         *
         * @param index
         *            index into this member's variable information lists
         *
         * @return Full variable name, including bit length, at the specified index
         *****************************************************************************************/
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

    /**********************************************************************************************
     * Table modification data class
     *********************************************************************************************/
    protected static class TableModification
    {
        private final Object[] rowData;
        private final Object[] originalRowData;
        private final int variableColumn;
        private final int dataTypeColumn;
        private final int arraySizeColumn;
        private final int bitLengthColumn;
        private final List<Integer> rateColumn;

        /******************************************************************************************
         * Table modification data class constructor for changes to existing rows
         *
         * @param rowData
         *            row of data from the table containing the changes
         *
         * @param originalRowData
         *            original contents of the row of data from the table containing the changes
         *
         * @param variableColumn
         *            index of the column containing the variable name; -1 if no variable name
         *            column exists
         *
         * @param dataTypeColumn
         *            index of the column containing the data type name; -1 if no data type column
         *            exists
         *
         * @param arraySizeColumn
         *            index of the column containing the array size; -1 if no array size column
         *            exists
         *
         * @param bitLengthColumn
         *            index of the column containing the bit length; -1 if no bit length column
         *            exists
         *
         * @param rateColumn
         *            indices of the columns containing the sample rates; null if no rate column
         *            exists
         *****************************************************************************************/
        protected TableModification(Object[] rowData,
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

        /******************************************************************************************
         * Table modification data class constructor for macro updates
         *
         * @param rowData
         *            row of data from the macro table
         *
         * @param originalRowData
         *            original contents of the row of data from the macro table
         *****************************************************************************************/
        protected TableModification(Object[] rowData, Object[] originalRowData)
        {
            this(rowData, originalRowData, -1, -1, -1, -1, null);
        }

        /******************************************************************************************
         * Table update data class constructor for additions and deletions
         *
         * @param rowData
         *            row of data from the macro table
         *
         * @param variableColumn
         *            index of the column containing the variable name; -1 if no variable name
         *            column exists
         *
         * @param dataTypeColumn
         *            index of the column containing the data type name; -1 if no data type column
         *            exists
         *
         * @param arraySizeColumn
         *            index of the column containing the array size; -1 if no array size column
         *            exists
         *
         * @param bitLengthColumn
         *            index of the column containing the bit length; -1 if no bit length column
         *            exists
         *****************************************************************************************/
        protected TableModification(Object[] rowData,
                                    int variableColumn,
                                    int dataTypeColumn,
                                    int arraySizeColumn,
                                    int bitLengthColumn)
        {
            this(rowData,
                 null,
                 variableColumn,
                 dataTypeColumn,
                 arraySizeColumn,
                 bitLengthColumn,
                 null);
        }

        /******************************************************************************************
         * Get an array of column values for the updated row
         *
         * @return Array of column values for the updated row
         *****************************************************************************************/
        protected Object[] getRowData()
        {
            return rowData;
        }

        /******************************************************************************************
         * Get an array of column values for the updated row's original content
         *
         * @return Array of column values for the updated row's original content
         *****************************************************************************************/
        protected Object[] getOriginalRowData()
        {
            return originalRowData;
        }

        /******************************************************************************************
         * Get the variable name column index
         *
         * @return Variable name column index; -1 if the column doesn't exist
         *****************************************************************************************/
        protected int getVariableColumn()
        {
            return variableColumn;
        }

        /******************************************************************************************
         * Get the data type column index
         *
         * @return Data type column index; -1 if the column doesn't exist
         *****************************************************************************************/
        protected int getDataTypeColumn()
        {
            return dataTypeColumn;
        }

        /******************************************************************************************
         * Get the array size column index
         *
         * @return Array size column index; -1 if the column doesn't exist
         *****************************************************************************************/
        protected int getArraySizeColumn()
        {
            return arraySizeColumn;
        }

        /******************************************************************************************
         * Get the bit length column index
         *
         * @return Bit length column index; -1 if the column doesn't exist
         *****************************************************************************************/
        protected int getBitLengthColumn()
        {
            return bitLengthColumn;
        }

        /******************************************************************************************
         * Get the rate column index
         *
         * @return List containing the rate column indices; null if no rate column exists
         *****************************************************************************************/
        protected List<Integer> getRateColumn()
        {
            return rateColumn;
        }
    }

    /**********************************************************************************************
     * Table definition class. Contains the information necessary to construct a data table from
     * information derived from a CSV, EDS, JSON, or XTCE import file
     *********************************************************************************************/
    protected static class TableDefinition
    {
        private String tableName;
        private String typeName;
        private String description;
        private final List<String> data;
        private final List<String[]> dataFields;

        /******************************************************************************************
         * Table definition class constructor
         *****************************************************************************************/
        TableDefinition()
        {
            typeName = "";

            // Initialize storage for the table information
            data = new ArrayList<String>();
            dataFields = new ArrayList<String[]>();
        }

        /******************************************************************************************
         * Table definition class constructor
         *
         * @param tableName
         *            table name, including the path (for a structure child table)
         *
         * @param description
         *            table description
         *
         * @throws CCDDException
         *             If the table name/path is not in the expected format
         *****************************************************************************************/
        TableDefinition(String tableName, String description) throws CCDDException
        {
            this();

            // Store the table name (including path, if applicable) and description
            this.tableName = tableName;
            this.description = description;

            // Validate the table name/path
            validatePathFormat(tableName);
        }

        /******************************************************************************************
         * Get the table name (including path, if applicable)
         *
         * @return Table name (including path, if applicable)
         *****************************************************************************************/
        protected String getName()
        {
            return tableName;
        }

        /******************************************************************************************
         * Set the table name (including path, if applicable)
         *
         * @param tableName
         *            table name (including path, if applicable)
         *
         * @throws CCDDException
         *             If the table name/path is not in the expected format
         *****************************************************************************************/
        protected void setName(String tableName) throws CCDDException
        {
            this.tableName = tableName;

            // Validate the table name/path
            validatePathFormat(tableName);
        }

        /******************************************************************************************
         * Get the table type name
         *
         * @return Table type name
         *****************************************************************************************/
        protected String getTypeName()
        {
            return typeName;
        }

        /******************************************************************************************
         * Set the table type name
         *
         * @param typeName
         *            table type name
         *****************************************************************************************/
        protected void setTypeName(String typeName)
        {
            this.typeName = typeName;
        }

        /******************************************************************************************
         * Get the table description
         *
         * @return Table description; return a blank if the description is null
         *****************************************************************************************/
        protected String getDescription()
        {
            return description == null ? "" : description;
        }

        /******************************************************************************************
         * Set the table description
         *
         * @param description
         *            table description
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }

        /******************************************************************************************
         * Get the table data
         *
         * @return Table data
         *****************************************************************************************/
        protected List<String> getData()
        {
            return data;
        }

        /******************************************************************************************
         * Add the specified row of data to the table data
         *
         * @param rowData
         *            row of table data to add to the table's data
         *****************************************************************************************/
        protected void addData(String[] rowData)
        {
            data.addAll(Arrays.asList(rowData));
        }

        /******************************************************************************************
         * Get the list containing the table data field definition
         *
         * @return List containing the table data field definition
         *****************************************************************************************/
        protected List<String[]> getDataFields()
        {
            return dataFields;
        }

        /******************************************************************************************
         * Add a data field definition to the list of table data fields
         *
         * @param fieldDefn
         *            data field definition
         *****************************************************************************************/
        protected void addDataField(String[] fieldDefn)
        {
            dataFields.add(fieldDefn);
        }

        /******************************************************************************************
         * Check if the table name/path is in the expected format. Macros aren't expanded; the
         * delimiters are simply removed
         *
         * @param tablePath
         *            table path in the format {@literal rootName<,childStructure.childName<,...>>}
         *
         * @return true if the table name/path is not in the expected format
         *****************************************************************************************/
        protected static boolean isPathFormatValid(String tablePath)
        {
            boolean isValid = true;
            boolean isChild = false;

            // Split the path into the root and children (if present) and step through each
            for (String child : tablePath.split(","))
            {
                // Split the table into the data type and variable name. The root table only has
                // the data type portion
                String[] dataTypeAndVariable = child.split("\\.");

                // Check if the data type and (if present) variable name are in the correct format.
                // Embedded macros are accounted for by simply removing the associated macro
                // identifiers prior to checking the name
                if (!dataTypeAndVariable[0].matches(DefaultInputType.VARIABLE.getInputMatch())
                    || (isChild
                        && !(dataTypeAndVariable.length == 2
                             && dataTypeAndVariable[1].replaceAll(MACRO_IDENTIFIER
                                                                  + "(.+?)"
                                                                  + MACRO_IDENTIFIER,
                                                                  "$1")
                                                      .matches(DefaultInputType.VARIABLE.getInputMatch()
                                                               + "(?:\\[[0-9]+\\])?"))))
                {
                    // Set the flag to indicate the path isn't valid and stop searching
                    isValid = false;
                    break;
                }

                isChild = true;
            }

            return isValid;
        }

        /******************************************************************************************
         * Check if the table name/path is in the expected format
         *
         * @param tablePath
         *            table path in the format {@literal rootName<,childStructure.childName<,...>>}
         *
         * @throws CCDDException
         *             If the table name/path is not in the expected format
         *****************************************************************************************/
        protected static void validatePathFormat(String tablePath) throws CCDDException
        {
            // Check if the table path is valid
            if (!isPathFormatValid(tablePath))
            {
                // Indicate that the table name/path doesn't match the valid pattern
                throw new CCDDException("Invalid table path '</b>" + tablePath + "<b>' format");
            }
        }
    }

    /**********************************************************************************************
     * Table type definition class. Contains the information necessary to construct a table type
     * from information derived from a CSV, EDS, JSON, or XTCE import file
     *********************************************************************************************/
    protected static class TableTypeDefinition
    {
        private String typeName;
        private final String description;
        private final List<Object[]> columns;
        private final List<String[]> dataFields;

        /******************************************************************************************
         * Table definition class constructor
         *
         * @param typeName
         *            table type name
         *
         * @param description
         *            table type description
         *****************************************************************************************/
        TableTypeDefinition(String typeName, String description)
        {
            this.typeName = typeName;
            this.description = description;

            // Initialize storage for the table type column and data field information
            columns = new ArrayList<Object[]>();
            dataFields = new ArrayList<String[]>();
        }

        /******************************************************************************************
         * Get the table type name
         *
         * @return Table type name
         *****************************************************************************************/
        protected String getTypeName()
        {
            return typeName;
        }

        /******************************************************************************************
         * Set the table type name
         *
         * @param typeName
         *            table type name
         *****************************************************************************************/
        protected void setTypeName(String typeName)
        {
            this.typeName = typeName;
        }

        /******************************************************************************************
         * Get the table type description
         *
         * @return Table type description
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Get the list containing the table type column information
         *
         * @return List containing the table type column information
         *****************************************************************************************/
        protected List<Object[]> getColumns()
        {
            return columns;
        }

        /******************************************************************************************
         * Add a table type column
         *
         * @param column
         *            array of table type column information
         *****************************************************************************************/
        protected void addColumn(Object[] column)
        {
            columns.add(column);
        }

        /******************************************************************************************
         * Get the list containing the table type data field information
         *
         * @return List containing the table type data field information
         *****************************************************************************************/
        protected List<String[]> getDataFields()
        {
            return dataFields;
        }

        /******************************************************************************************
         * Add a table type data field
         *
         * @param dataField
         *            array of table type data field information
         *****************************************************************************************/
        protected void addDataField(String[] dataField)
        {
            dataFields.add(dataField);
        }
    }

    /**********************************************************************************************
     * Input type class
     *********************************************************************************************/
    protected static class InputType
    {
        private final String inputName;
        private final String inputDescription;
        private final String inputMatch;
        private List<String> inputItems;
        private final InputTypeFormat inputFormat;
        private final boolean isInputCustom;

        /******************************************************************************************
         * Input type class constructor
         *
         * @param inputName
         *            input type name
         *
         * @param inputDescription
         *            input type description
         *
         * @param inputMatch
         *            regular expression match for the input type
         *
         * @param inputItems
         *            string containing the acceptable values for this input type, separated by the
         *            selection item list separator; null or blank if the input type doesn't
         *            constrain the inputs to items from a list. The list is used to create the
         *            contents of the combo box in the table column with this input type
         *
         * @param inputFormat
         *            input type format
         *
         * @param isInputCustom
         *            true if the input type is user-defined
         *****************************************************************************************/
        InputType(String inputName,
                  String inputDescription,
                  String inputMatch,
                  String inputItems,
                  InputTypeFormat inputFormat,
                  boolean isInputCustom)
        {
            this.inputName = inputName;
            this.inputDescription = inputDescription;
            this.inputMatch = inputMatch;
            setInputItems(convertItemStringToList(inputItems));
            this.inputFormat = inputFormat;
            this.isInputCustom = isInputCustom;
        }

        /******************************************************************************************
         * Get the input type name
         *
         * @return Input type name
         *****************************************************************************************/
        protected String getInputName()
        {
            return inputName;
        }

        /******************************************************************************************
         * Get the input type description
         *
         * @return Input type description
         *****************************************************************************************/
        protected String getInputDescription()
        {
            return inputDescription;
        }

        /******************************************************************************************
         * Get the input type matching regular expression
         *
         * @return Input type matching regular expression
         *****************************************************************************************/
        protected String getInputMatch()
        {
            return inputMatch;
        }

        /******************************************************************************************
         * Get the input type selection items
         *
         * @return List of input type selection items; null if the input type has no selection
         *         items
         *****************************************************************************************/
        protected List<String> getInputItems()
        {
            return inputItems;
        }

        /******************************************************************************************
         * Set the input type selection items
         *
         * @param inputItems
         *            list of input type selection items (an empty item is automatically prepended
         *            to the list to allow blanking the selection); null if the input type has no
         *            selection items
         *****************************************************************************************/
        protected void setInputItems(List<String> inputItems)
        {
            // Check if any items are in the list
            if (inputItems != null)
            {
                this.inputItems = new ArrayList<String>();

                // Add a blank item as the first in the list
                this.inputItems.add("");

                // Add the items to the selection list as is
                this.inputItems.addAll(inputItems);
            }
            // The list is null or empty
            else
            {
                this.inputItems = null;
            }
        }

        /******************************************************************************************
         * Get the input type format
         *
         * @return Input type format
         *****************************************************************************************/
        protected InputTypeFormat getInputFormat()
        {
            return inputFormat;
        }

        /******************************************************************************************
         * Check if the input type is user-defined
         *
         * @return true if the input type is defined by the user
         *****************************************************************************************/
        protected boolean isCustomInput()
        {
            return isInputCustom;
        }

        /******************************************************************************************
         * Get the input type format associated with the supplied input format name
         *
         * @param formatName
         *            input format name
         *
         * @return Input type format associated with the supplied input format name; 'Text' format
         *         if the name supplied is invalid
         *****************************************************************************************/
        protected static InputTypeFormat getInputFormatByName(String formatName)
        {
            // Set the default input type format
            InputTypeFormat inputFormat = InputTypeFormat.TEXT;

            // Step through each input type format
            for (InputTypeFormat format : InputTypeFormat.values())
            {
                // Check if the format names match (case insensitive)
                if (formatName.equals(format.getFormatName()))
                {
                    // Store the input type format and stop searching
                    inputFormat = format;
                    break;
                }
            }

            return inputFormat;
        }

        /******************************************************************************************
         * Reformat the input value for numeric types. This adds a leading zero to floating point
         * values if the first character is a decimal, and removes '+' signs and unneeded leading
         * zeroes from integer and floating point values. Leading zeroes are preserved for
         * hexadecimal values
         *
         * @param valueS
         *            value, represented as a string, to reformat
         *
         * @return Input value reformatted based on its input type
         *****************************************************************************************/
        protected String formatInput(String valueS)
        {
            return CcddInputTypeHandler.formatInput(valueS, inputFormat, true);
        }

        /******************************************************************************************
         * Reformat the input value for numeric types. This adds a leading zero to floating point
         * values if the first character is a decimal, and removes '+' signs and unneeded leading
         * zeroes from integer and floating point values
         *
         * @param valueS
         *            value, represented as a string, to reformat
         *
         * @param preserveZeroes
         *            true to preserve leading zeroes in hexadecimal values; false to eliminate the
         *            extra zeroes (this is useful when comparing the text representation of two
         *            hexadecimal values)
         *
         * @return Input value reformatted based on its input type
         *****************************************************************************************/
        protected String formatInput(String valueS, boolean preserveZeroes)
        {
            return CcddInputTypeHandler.formatInput(valueS, inputFormat, preserveZeroes);
        }

        /******************************************************************************************
         * Convert the input selection items from a single string to a list
         *
         * @param inputItemsString
         *            string containing the acceptable values for this input type, separated by the
         *            selection item list separator (a blank item is automatically prepended to the
         *            list); null or blank if the input type doesn't constrain the inputs to items
         *            from a list
         *
         * @return Input items, converted to a list; null if the input type has no items
         *****************************************************************************************/
        protected static List<String> convertItemStringToList(String inputItemsString)
        {
            return inputItemsString == null
                   || inputItemsString.isEmpty()
                                                 ? null
                                                 : Arrays.asList(inputItemsString.split(SELECTION_ITEM_LIST_SEPARATOR));
        }

        /******************************************************************************************
         * Convert the input selection items from a list to a single string
         *
         * @param inputItemsList
         *            list containing the acceptable values for this input type; null or blank if
         *            the input type doesn't constrain the inputs to items from a list
         *
         * @return Input items, converted to a string, separated by the selection item list
         *         separator; blank if the input type has no items
         *****************************************************************************************/
        protected static String convertItemListToString(List<String> inputItemsList)
        {
            String inputItemsString = "";

            // Check if any items are supplied
            if (inputItemsList != null && !inputItemsList.isEmpty())
            {
                inputItemsString = "";

                // Step through each item. Skip the first, empty item that's automatically
                // prepended to every non-empty list
                for (int index = 1; index < inputItemsList.size(); index++)
                {
                    // Add the item to the string, followed by the separator
                    inputItemsString += inputItemsList.get(index) + SELECTION_ITEM_LIST_SEPARATOR;
                }

                // Remove the trailing separator
                inputItemsString = CcddUtilities.removeTrailer(inputItemsString,
                                                               SELECTION_ITEM_LIST_SEPARATOR);
            }

            return inputItemsString;
        }
    }

    /**********************************************************************************************
     * Project definition class. Contains the information necessary to construct the project-level
     * data fields derived from a CSV, EDS, JSON, or XTCE import file, or group data fields derived
     * from a CSV or JSON import file
     *********************************************************************************************/
    protected static class ProjectDefinition
    {
        private final List<String[]> dataFields;

        /******************************************************************************************
         * Project definition class constructor
         *****************************************************************************************/
        ProjectDefinition()
        {
            // Initialize storage for the data field information
            dataFields = new ArrayList<String[]>();
        }

        /******************************************************************************************
         * Get the list containing the project-level data field information
         *
         * @return List containing the project-level data field information
         *****************************************************************************************/
        protected List<String[]> getDataFields()
        {
            return dataFields;
        }

        /******************************************************************************************
         * Add a data field information to the list of project-level data fields
         *
         * @param fieldInfo
         *            data field information
         *****************************************************************************************/
        protected void addDataField(String[] fieldInfo)
        {
            dataFields.add(fieldInfo);
        }
    }

    /**********************************************************************************************
     * Command argument class. Contains associated command argument name, data type, enumeration,
     * minimum value, and maximum value column indices in view or model coordinates (as specified)
     *********************************************************************************************/
    public static class AssociatedColumns
    {
        private final int name;
        private final int dataType;
        private final int arraySize;
        private final int bitLength;
        private final int enumeration;
        private final MinMaxPair minMax;
        private final int description;
        private final int units;
        private final List<Integer> other;

        /******************************************************************************************
         * Command argument class constructor for setting associated command argument columns
         *
         * @param useViewIndex
         *            true to adjust the column indices to view coordinates; false to keep the
         *            coordinates in model coordinates
         *
         * @param name
         *            command argument name data type column index, model coordinates; -1 if none
         *
         * @param dataType
         *            command argument data type column index, model coordinates; -1 if none
         *
         * @param arraySize
         *            command argument array size column index, model coordinates; -1 if none
         *
         * @param bitLength
         *            command argument bit length column index, model coordinates; -1 if none
         *
         * @param enumeration
         *            command argument enumeration column index, model coordinates; -1 if none
         *
         * @param minimum
         *            command argument minimum value column index, model coordinates; -1 if none
         *
         * @param maximum
         *            command argument maximum value column index, model coordinates; -1 if none
         *
         * @param description
         *            command argument description column index, model coordinates; -1 if none
         *
         * @param units
         *            command argument units column index, model coordinates; -1 if none
         *
         * @param other
         *            list of other associated column indices, model coordinates; null if none
         *****************************************************************************************/
        protected AssociatedColumns(boolean useViewIndex,
                                    int name,
                                    int dataType,
                                    int arraySize,
                                    int bitLength,
                                    int enumeration,
                                    int minimum,
                                    int maximum,
                                    int description,
                                    int units,
                                    List<Integer> other)
        {
            // Store the column indices. Adjust to view coordinates based on the input flag
            this.name = name
                        - (useViewIndex
                                        ? NUM_HIDDEN_COLUMNS
                                        : 0);
            this.dataType = dataType
                            - (useViewIndex
                                            ? NUM_HIDDEN_COLUMNS
                                            : 0);
            this.arraySize = arraySize
                             - (useViewIndex
                                             ? NUM_HIDDEN_COLUMNS
                                             : 0);
            this.bitLength = bitLength
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
            this.description = description - (useViewIndex
                                                           ? NUM_HIDDEN_COLUMNS
                                                           : 0);
            this.units = units - (useViewIndex
                                               ? NUM_HIDDEN_COLUMNS
                                               : 0);

            // Check if other associated columns are provided
            if (other != null)
            {
                this.other = new ArrayList<Integer>();

                // Step through each associated column
                for (int column : other)
                {
                    // Store the column index. Adjust to view coordinates based on the input flag
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

        /******************************************************************************************
         * Command argument class constructor for setting structure table data type and enumeration
         * pairings
         *
         * @param dataType
         *            command argument data type column index, model coordinates
         *
         * @param enumeration
         *            command argument enumeration column index, model coordinates
         *****************************************************************************************/
        AssociatedColumns(int dataType, int enumeration)
        {
            this(false, -1, dataType, -1, -1, enumeration, -1, -1, -1, -1, null);
        }

        /******************************************************************************************
         * Get the command argument name column index
         *
         * @return Command argument name column index
         *****************************************************************************************/
        public int getName()
        {
            return name;
        }

        /******************************************************************************************
         * Get the command argument data type column index
         *
         * @return Command argument data type column index
         *****************************************************************************************/
        public int getDataType()
        {
            return dataType;
        }

        /******************************************************************************************
         * Get the command argument array size column index
         *
         * @return Command argument array size column index
         *****************************************************************************************/
        public int getArraySize()
        {
            return arraySize;
        }

        /******************************************************************************************
         * Get the command argument bit length column index
         *
         * @return Command argument bit length column index
         *****************************************************************************************/
        public int getBitLength()
        {
            return bitLength;
        }

        /******************************************************************************************
         * Get the command argument enumeration column index
         *
         * @return Command argument enumeration column index
         *****************************************************************************************/
        public int getEnumeration()
        {
            return enumeration;
        }

        /******************************************************************************************
         * Get the command argument minimum column index
         *
         * @return Command argument minimum column index
         *****************************************************************************************/
        public int getMinimum()
        {
            return minMax.getMinimum();
        }

        /******************************************************************************************
         * Get the command argument maximum column index
         *
         * @return Command argument maximum column index
         *****************************************************************************************/
        public int getMaximum()
        {
            return minMax.getMaximum();
        }

        /******************************************************************************************
         * Get the command argument description column index
         *
         * @return Command argument description column index
         *****************************************************************************************/
        public int getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Get the command argument units column index
         *
         * @return Command argument units column index
         *****************************************************************************************/
        public int getUnits()
        {
            return units;
        }

        /******************************************************************************************
         * Get the other command argument index(ices)
         *
         * @return Other command argument index(ices)
         *****************************************************************************************/
        protected List<Integer> getOther()
        {
            return other;
        }
    }

    /**********************************************************************************************
     * Minimum/maximum pairing class. Contains associated minimum value and maximum value column
     * indices
     *********************************************************************************************/
    protected static class MinMaxPair
    {
        private final int minColumn;
        private final int maxColumn;

        /******************************************************************************************
         * Minimum/maximum pairing class constructor
         *
         * @param minColumn
         *            minimum value column index
         *
         * @param maxColumn
         *            maximum value column index
         *****************************************************************************************/
        MinMaxPair(int minColumn, int maxColumn)
        {
            this.minColumn = minColumn;
            this.maxColumn = maxColumn;
        }

        /******************************************************************************************
         * Get the minimum column index
         *
         * @return Minimum column index
         *****************************************************************************************/
        protected int getMinimum()
        {
            return minColumn;
        }

        /******************************************************************************************
         * Get the maximum column index
         *
         * @return Maximum column index
         *****************************************************************************************/
        protected int getMaximum()
        {
            return maxColumn;
        }
    }

    /******************************************************************************************
     * Data field information class
     *****************************************************************************************/
    protected static class FieldInformation
    {
        private String ownerName;
        private String fieldName;
        private String description;
        private int charSize;
        private InputType inputType;
        private boolean isRequired;
        private ApplicabilityType applicability;
        private String value;
        private boolean isInherited;
        private Component inputFld;
        private int id;

        /******************************************************************************************
         * Data field information class constructor
         *
         * @param ownerName
         *            name of the table, including the path if this table represents a structure,
         *            or group for which the field is a member
         *
         * @param fieldName
         *            field name
         *
         * @param description
         *            data field description; used as the tool tip for this field
         *
         * @param charSize
         *            field display size in characters
         *
         * @param inputType
         *            field input type (InputType)
         *
         * @param isRequired
         *            true if a value is required for this field
         *
         * @param applicability
         *            field applicability type; all tables, parent tables only, or child tables
         *            only
         *
         * @param value
         *            field value
         *
         * @param isInherited
         *            true if the field is inherited from its owner table's type definition
         *            (ignored if the field does not belong to a table)
         *
         * @param inputFld
         *            reference to the field's UndoableTextField or UndoableCheckBox; null if
         *            creating the data field without specifying the input field
         *
         * @param id
         *            ID for the row defining the field; -1 for a new field
         *****************************************************************************************/
        FieldInformation(String ownerName,
                         String fieldName,
                         String description,
                         InputType inputType,
                         int charSize,
                         boolean isRequired,
                         ApplicabilityType applicability,
                         String value,
                         boolean isInherited,
                         Component inputFld,
                         int id)
        {
            this.ownerName = ownerName;
            this.fieldName = fieldName;
            this.description = description;
            this.inputType = inputType;
            this.charSize = charSize;
            this.isRequired = isRequired;
            this.applicability = applicability;
            this.value = value;
            this.isInherited = isInherited;
            this.inputFld = inputFld;
            this.id = id;
        }

        /******************************************************************************************
         * Get the name of the field's owner (table or group name)
         *
         * @return Name of the owning table/group to which the field belongs
         *****************************************************************************************/
        protected String getOwnerName()
        {
            return ownerName;
        }

        /******************************************************************************************
         * Get the name of the field's owner (table or group name)
         *
         * @param ownerName
         *            name of the table/group to which the field belongs
         *****************************************************************************************/
        protected void setOwnerName(String ownerName)
        {
            this.ownerName = ownerName;
        }

        /******************************************************************************************
         * Get the field name
         *
         * @return Field name
         *****************************************************************************************/
        protected String getFieldName()
        {
            return fieldName;
        }

        /******************************************************************************************
         * Set the field name
         *
         * @param fieldName
         *            field name
         *****************************************************************************************/
        protected void setFieldName(String fieldName)
        {
            this.fieldName = fieldName;
        }

        /******************************************************************************************
         * Get the field comment
         *
         * @return Field comment
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Set the field comment
         *
         * @param description
         *            field comment
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }

        /******************************************************************************************
         * Get the field display size in characters
         *
         * @return Field display size in characters
         *****************************************************************************************/
        protected int getSize()
        {
            return charSize;
        }

        /******************************************************************************************
         * set the field display size in characters
         *
         * @param charSize
         *            field display size in characters
         *****************************************************************************************/
        protected void setSize(int charSize)
        {
            this.charSize = charSize;
        }

        /******************************************************************************************
         * Check if the field value is required
         *
         * @return true if a value is required for this field
         *****************************************************************************************/
        protected boolean isRequired()
        {
            return isRequired;
        }

        /******************************************************************************************
         * Set the flag that indicates if the field value is required
         *
         * @param isRequired
         *            true if a value is required for this field
         *****************************************************************************************/
        protected void setRequired(boolean isRequired)
        {
            this.isRequired = isRequired;
        }

        /******************************************************************************************
         * Get the field applicability type
         *
         * @return Field applicability type
         *****************************************************************************************/
        protected ApplicabilityType getApplicabilityType()
        {
            return applicability;
        }

        /******************************************************************************************
         * Set the field applicability type
         *
         * @param applicability
         *            field applicability type
         *****************************************************************************************/
        protected void setApplicabilityType(ApplicabilityType applicability)
        {
            this.applicability = applicability;
        }

        /******************************************************************************************
         * Get the field value
         *
         * @return Field value
         *****************************************************************************************/
        protected String getValue()
        {
            return value;
        }

        /******************************************************************************************
         * Set the field value
         *
         * @param value
         *            field value
         *****************************************************************************************/
        protected void setValue(String value)
        {
            this.value = value;
        }

        /******************************************************************************************
         * Check if the field is inherited from the owner table's type definition
         *
         * @return true if the field is inherited from the owner table's type definition (ignored
         *         if the field does not belong to a table)
         *****************************************************************************************/
        protected boolean isInherited()
        {
            return isInherited;
        }

        /******************************************************************************************
         * Set the flag that indicates if the field is inherited from the owner table's type
         * definition
         *
         * @param isInherited
         *            true if the field is inherited from the owner table's type definition
         *            (ignored if the field does not belong to a table)
         *****************************************************************************************/
        protected void setInherited(boolean isInherited)
        {
            this.isInherited = isInherited;
        }

        /******************************************************************************************
         * Get a reference to the field's input component
         *
         * @return Field's input component reference
         *****************************************************************************************/
        protected Component getInputFld()
        {
            return inputFld;
        }

        /******************************************************************************************
         * Set the field's UndoableTextField, UndoableComboBox, or UndoableCheckBox
         *
         * @param inputFld
         *            field's UndoableTextField, UndoableComboBox, or UndoableCheckBox
         ****************************************************************************************/
        protected void setInputFld(Component inputFld)
        {
            this.inputFld = inputFld;
        }

        /******************************************************************************************
         * Get the field input type
         *
         * @return Field input type
         *****************************************************************************************/
        protected InputType getInputType()
        {
            return inputType;
        }

        /******************************************************************************************
         * Set the field input type
         *
         * @param inputType
         *            field input type (InputType)
         *****************************************************************************************/
        protected void setInputType(InputType inputType)
        {
            this.inputType = inputType;
        }

        /******************************************************************************************
         * Get the field ID
         *
         * @return Field ID
         *****************************************************************************************/
        protected int getID()
        {
            return id;
        }

        /******************************************************************************************
         * Set the field ID
         *
         * @param id
         *            field ID
         *****************************************************************************************/
        protected void setID(int id)
        {
            this.id = id;
        }
    }

    /**********************************************************************************************
     * Group information class. Associates group names in the database with their respective tables
     * and data fields
     *********************************************************************************************/
    protected static class GroupInformation
    {
        private String name;
        private String description;
        private Boolean isApplication;
        private final List<String> tablesAndAncestors;
        private final List<String> tableMembers;
        private List<FieldInformation> fieldInformation;

        /******************************************************************************************
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
         *****************************************************************************************/
        GroupInformation(String name,
                         String description,
                         Boolean isApplication,
                         List<FieldInformation> fieldInformation)
        {
            this(name, description, isApplication, null, fieldInformation);
        }

        /******************************************************************************************
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
         *            list of tables (with full paths( belonging to this group; null if creating a
         *            new, empty group
         *
         * @param fieldInformation
         *            data field information
         *****************************************************************************************/
        GroupInformation(String name,
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

        /******************************************************************************************
         * Get the group name
         *
         * @return Group name
         *****************************************************************************************/
        protected String getName()
        {
            return name;
        }

        /******************************************************************************************
         * Set the group name
         *
         * @param name
         *            group name
         *****************************************************************************************/
        protected void setName(String name)
        {
            this.name = name;
        }

        /******************************************************************************************
         * Get the group description
         *
         * @return Group description
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Set the group description
         *
         * @param description
         *            group description
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }

        /******************************************************************************************
         * Get the flag that indicates if the group represents a CFS application
         *
         * @return true if the group represents a CFS application
         *****************************************************************************************/
        protected Boolean isApplication()
        {
            return isApplication;
        }

        /******************************************************************************************
         * Set the flag that indicates if the group represents a CFS application
         *
         * @param isApplication
         *            true if the group represents a CFS application
         *****************************************************************************************/
        protected void setIsApplication(boolean isApplication)
        {
            this.isApplication = isApplication;
        }

        /******************************************************************************************
         * Get the group table list that includes the member tables and all of their ancestor
         * tables
         *
         * @return Group table list that includes the member tables and all of their ancestor
         *         tables
         *****************************************************************************************/
        protected List<String> getTablesAndAncestors()
        {
            return tablesAndAncestors;
        }

        /******************************************************************************************
         * Get the list of table members (root tables and parent.child table table pairs) belonging
         * to this group
         *
         * @return List of table members (parent.child table table pairs) belonging to this group
         *****************************************************************************************/
        protected List<String> getTableMembers()
        {
            return tableMembers;
        }

        /******************************************************************************************
         * Add the specified group table to the lists that include each parent.child table pair and
         * the member table along with its ancestor tables
         *
         * @param table
         *            group table member (full table path)
         *****************************************************************************************/
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

            // Store the index that points to the end of the list
            int index = tablesAndAncestors.size();

            // Check if this isn't a root table
            while (table.contains(","))
            {
                // Check if the table isn't already added to the list
                if (!tablesAndAncestors.contains(table))
                {
                    // Add the member/ancestor table to the list. Since the members are extracted
                    // from the end of the path and working up to its root, insert the ancestor
                    // ahead of the previously inserted child so that the table appear in the
                    // correct order (root->child1->child2...) in the list
                    tablesAndAncestors.add(index, table);
                }

                // Remove the parent.child
                table = table.substring(0, table.lastIndexOf(","));
            }

            // Check if the root table isn't already added to the list
            if (!tablesAndAncestors.contains(table))
            {
                // Add the root table to the list. Since the members are extracted from the end of
                // the path and working up the path, insert the root ahead of the previously
                // inserted child so that the table appear in the correct order
                // (root->child1->child2...) in the list
                tablesAndAncestors.add(index, table);
            }
        }

        /******************************************************************************************
         * Get the group data field information
         *
         * @return Group data field information
         *****************************************************************************************/
        protected List<FieldInformation> getFieldInformation()
        {
            return fieldInformation;
        }

        /******************************************************************************************
         * Set the group data field information
         *
         * @param fieldInformation
         *            group data field information
         *****************************************************************************************/
        protected void setFieldInformation(List<FieldInformation> fieldInformation)
        {
            this.fieldInformation = fieldInformation;
        }
    }

    /**********************************************************************************************
     * Link information class
     *********************************************************************************************/
    protected static class LinkInformation
    {
        private final String rateName;
        private String linkName;
        private String sampleRate;
        private String description;

        /******************************************************************************************
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
         *****************************************************************************************/
        LinkInformation(String rateName, String linkName, String description)
        {
            this(rateName, linkName, "0", description);
        }

        /******************************************************************************************
         * Link information class constructor
         *
         * @param rateName
         *            name of the data stream rate column associated with this link
         *
         * @param linkName
         *            link name
         *
         * @param sampleRate
         *            link rate in samples per second
         *
         * @param description
         *            link description
         *****************************************************************************************/
        LinkInformation(String rateName,
                        String linkName,
                        String sampleRate,
                        String description)
        {
            this.rateName = rateName;
            this.linkName = linkName;
            this.sampleRate = sampleRate;
            this.description = description;
        }

        /******************************************************************************************
         * Get the link data stream rate name
         *
         * @return Data stream rate name
         *****************************************************************************************/
        protected String getRateName()
        {
            return rateName;
        }

        /******************************************************************************************
         * Get the link name
         *
         * @return Link name
         *****************************************************************************************/
        protected String getName()
        {
            return linkName;
        }

        /******************************************************************************************
         * Set the link name
         *
         * @param linkName
         *            link name
         *****************************************************************************************/
        protected void setName(String linkName)
        {
            this.linkName = linkName;
        }

        /******************************************************************************************
         * Get the link rate in samples per second
         *
         * @return Link rate in samples per second; return "0" if no rate is assigned to this link
         *****************************************************************************************/
        protected String getSampleRate()
        {
            return sampleRate;
        }

        /******************************************************************************************
         * Set the link rate in samples per second
         *
         * @param sampleRate
         *            link rate in samples per second; "0" if no rate is assigned to this link
         *****************************************************************************************/
        protected void setSampleRate(String sampleRate)
        {
            this.sampleRate = sampleRate;
        }

        /******************************************************************************************
         * Get the link description
         *
         * @return Link description
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Set the link description
         *
         * @param description
         *            link description
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            this.description = description;
        }
    }

    /**********************************************************************************************
     * Array variable utility method class
     *********************************************************************************************/
    protected static class ArrayVariable
    {
        /******************************************************************************************
         * Remove the array variable index, if present, from the supplied variable name
         *
         * @param variableName
         *            variable name
         *
         * @return The variable name, with the array index removed
         *****************************************************************************************/
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

        /******************************************************************************************
         * Remove the string size array variable index, if present, from the supplied variable name
         *
         * @param variableName
         *            variable name
         *
         * @return The variable name, with the string size array index removed
         *****************************************************************************************/
        protected static String removeStringSize(String variableName)
        {
            // Get the index of the last array index, which represents the string size for a string
            // variable
            int index = variableName.lastIndexOf('[');

            // Check if an array index exists
            if (index != -1)
            {
                // Remove the string size array index from the variable name
                variableName = variableName.substring(0, index);
            }

            return variableName;
        }

        /******************************************************************************************
         * Get the array variable index, if present, from the supplied variable name
         *
         * @param variableName
         *            variable name
         *
         * @return The array index, with the variable name removed
         *****************************************************************************************/
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

        /******************************************************************************************
         * Check if a variable name represents an array member
         *
         * @param variableName
         *            variable name
         *
         * @return true if the variable is an array member
         *****************************************************************************************/
        protected static boolean isArrayMember(Object variableName)
        {
            return variableName.toString().endsWith("]");
        }

        /******************************************************************************************
         * Add brackets to encompass a variable array index value(s)
         *
         * @param arrayIndex
         *            array of array variable index value(s)
         *
         * @return Array index value(s), surrounded by brackets
         *****************************************************************************************/
        protected static String formatArrayIndex(int[] arrayIndex)
        {
            String arrayIndexS = "";

            // Step through each array dimension
            for (int index = 0; index < arrayIndex.length; index++)
            {
                // Surround the array index with brackets and add it to the array string
                arrayIndexS += "[" + arrayIndex[index] + "]";
            }

            return arrayIndexS;
        }

        /******************************************************************************************
         * Parse an array size into an array of integers representing each array index
         *
         * @param arrayString
         *            array size value in the format {@literal [#]<[#]<...>> or #<,#<,...>>}
         *
         * @return Array of integers representing each array index. An empty array is returned if
         *         the array size cell is blank
         *****************************************************************************************/
        protected static int[] getArrayIndexFromSize(String arrayString)
        {
            int[] arrayIndex = new int[0];

            // Check if the array size is in the format containing brackets
            if (arrayString.contains("["))
            {
                // Reformat the array size string without the brackets, replacing internal bracket
                // pairs (][) with commas and removing the outermost brackets
                arrayString = arrayString.replaceAll("\\]\\[", ",").replaceAll("[\\]\\[]", "");
            }

            // Check if the array size value isn't blank
            if (!arrayString.isEmpty())
            {
                // Split the array size into the separate array index values
                String[] indices = arrayString.split(",");
                arrayIndex = new int[indices.length];

                // Step through each array index value
                for (int index = 0; index < indices.length; index++)
                {
                    // Convert the index value to a number and store it in the index array
                    arrayIndex[index] = Integer.parseInt(indices[index].trim());
                }
            }

            return arrayIndex;
        }

        /******************************************************************************************
         * Get the total number of array members using the data tables array size cell text
         *
         * @param arraySizeS
         *            string representing the array variable dimension sizes
         *
         * @return Total number of members in the array; 0 if not an array variable
         *****************************************************************************************/
        protected static int getNumMembersFromArraySize(String arraySizeS)
        {
            return getNumMembersFromArrayDimension(getArrayIndexFromSize(arraySizeS));
        }

        /******************************************************************************************
         * Get the total number of array members using the array dimension values
         *
         * @param arraySize
         *            array of the array variable dimension sizes
         *
         * @return Total number of members in the array; 0 if not an array variable
         *****************************************************************************************/
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

        /******************************************************************************************
         * Compare two array variable members by array dimension value(s)
         *
         * @param arrayVariable1
         *            first array variable member to compare
         *
         * @param arrayVariable2
         *            second array variable member to compare
         *
         * @return 0 if the array members are the same, -1 if the first array member occurs prior
         *         to the second array member (based the their array dimension value(s), and 1 if
         *         the first array member occurs after the second array member
         *****************************************************************************************/
        protected static int compareTo(String arrayVariable1, String arrayVariable2)
        {
            int result = 0;

            // Get the array index value(s) for the array members
            int[] varIndex1 = getArrayIndexFromSize(getVariableArrayIndex(arrayVariable1));
            int[] varIndex2 = getArrayIndexFromSize(getVariableArrayIndex(arrayVariable2));

            // Step through each array dimension, beginning with the leftmost one (this accounts
            // for arrays with any number of dimensions)
            for (int index = 0; index < varIndex1.length; index++)
            {
                // Check if the value of the array dimension of the first variable is less than
                // that of the same dimension in the second variable
                if (varIndex1[index] < varIndex2[index])
                {
                    // Set the comparison result to indicate that the first variable should be
                    // placed prior to the second variable, and stop comparing array indices
                    result = -1;
                    break;
                }
                // Check if the value of the array dimension of the first variable is greater than
                // that of the same dimension in the second variable
                else if (varIndex1[index] > varIndex2[index])
                {
                    // Set the comparison result to indicate that the first variable should be
                    // placed after to the second variable, and stop comparing array indices
                    result = 1;
                    break;
                }
            }

            return result;
        }
    }

    /**********************************************************************************************
     * Data stream rate parameter information class
     *********************************************************************************************/
    protected static class RateInformation
    {
        private String rateName;
        private String streamName;
        private int maxMsgsPerCycle;
        private int maxBytesPerSec;
        private String[] sampleRates;
        private int numSharedTableTypes;

        /******************************************************************************************
         * Data stream rate parameter information class constructor
         *
         * @param rateName
         *            rate column name (viewable) for this data stream
         *****************************************************************************************/
        RateInformation(String rateName)
        {
            this.rateName = rateName;

            // Initialize the number of table types reference by this rate column
            numSharedTableTypes = 1;

            // Set default values for the rate parameters
            setDefaultValues();
        }

        /******************************************************************************************
         * Get the number of table types referencing this rate column name
         *
         * @return Number of table types referencing this rate column name
         *****************************************************************************************/
        protected int getNumSharedTableTypes()
        {
            return numSharedTableTypes;
        }

        /******************************************************************************************
         * Set the number of table types referencing this rate column name
         *
         * @param numSharedTableTypes
         *            number of table types referencing this rate column name
         *****************************************************************************************/
        protected void setNumSharedTableTypes(int numSharedTableTypes)
        {
            this.numSharedTableTypes = numSharedTableTypes;
        }

        /******************************************************************************************
         * Set default values for the stream name, maximum messages per cycle, and maximum bytes
         * per second rate parameters
         *****************************************************************************************/
        protected void setDefaultValues()
        {
            streamName = rateName;
            maxMsgsPerCycle = 1;
            maxBytesPerSec = 56000;
            sampleRates = new String[] {"1"};
        }

        /******************************************************************************************
         * Get the rate column name
         *
         * @return Rate column name
         *****************************************************************************************/
        protected String getRateName()
        {
            return rateName;
        }

        /******************************************************************************************
         * Set the rate column name
         *
         * @param rateName
         *            rate column name
         *****************************************************************************************/
        protected void setRateName(String rateName)
        {
            this.rateName = rateName;
        }

        /******************************************************************************************
         * Get the data stream name
         *
         * @return Data stream name
         *****************************************************************************************/
        protected String getStreamName()
        {
            return streamName;
        }

        /******************************************************************************************
         * Set the data stream name
         *
         * @param streamName
         *            data stream name
         *****************************************************************************************/
        protected void setStreamName(String streamName)
        {
            this.streamName = streamName;
        }

        /******************************************************************************************
         * Get the stream maximum messages per cycle
         *
         * @return Stream maximum messages per cycle
         *****************************************************************************************/
        protected int getMaxMsgsPerCycle()
        {
            return maxMsgsPerCycle;
        }

        /******************************************************************************************
         * Set the stream maximum messages per cycle
         *
         * @param maxMsgsPerCycle
         *            stream maximum messages per cycle
         *****************************************************************************************/
        protected void setMaxMsgsPerCycle(int maxMsgsPerCycle)
        {
            this.maxMsgsPerCycle = maxMsgsPerCycle;
        }

        /******************************************************************************************
         * Get the stream maximum bytes per second
         *
         * @return Stream maximum bytes per second
         *****************************************************************************************/
        protected int getMaxBytesPerSec()
        {
            return maxBytesPerSec;
        }

        /******************************************************************************************
         * Set the stream maximum bytes per second
         *
         * @param maxBytesPerSec
         *            stream maximum bytes per second
         *****************************************************************************************/
        protected void setMaxBytesPerSec(int maxBytesPerSec)
        {
            this.maxBytesPerSec = maxBytesPerSec;
        }

        /******************************************************************************************
         * Get the stream sample rate array
         *
         * @return Stream sample rate array
         *****************************************************************************************/
        protected String[] getSampleRates()
        {
            return sampleRates;
        }

        /******************************************************************************************
         * Set the stream sample rate array
         *
         * @param sampleRates
         *            stream sample rate array
         *****************************************************************************************/
        protected void setSampleRates(String[] sampleRates)
        {
            this.sampleRates = sampleRates;
        }
    }

    /**********************************************************************************************
     * Table row indices for bit-packed variables class
     *********************************************************************************************/
    protected static class BitPackRowIndex
    {
        private final int firstIndex;
        private final int lastIndex;

        /******************************************************************************************
         * Table row indices for bit-packed variables class constructor
         *
         * @param firstIndex
         *            table row index for the first member in a group of bit-packed variables,
         *            model coordinates
         *
         * @param lastIndex
         *            table row index for the last member in a group of bit-packed variables, model
         *            coordinates
         *****************************************************************************************/
        BitPackRowIndex(int firstIndex, int lastIndex)
        {
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
        }

        /******************************************************************************************
         * Get the table row index for the first member in a group of bit-packed variable
         *
         * @return Table row index for the first member in a group of bit-packed variable, model
         *         coordinates
         *****************************************************************************************/
        protected int getFirstIndex()
        {
            return firstIndex;
        }

        /******************************************************************************************
         * Get the table row index for the last member in a group of bit-packed variable
         *
         * @return Table row index for the last member in a group of bit-packed variable, model
         *         coordinates
         *****************************************************************************************/
        protected int getLastIndex()
        {
            return lastIndex;
        }
    }

    /**********************************************************************************************
     * Table tree node indices for bit-packed variables class
     *********************************************************************************************/
    protected static class BitPackNodeIndex
    {
        private final int firstIndex;
        private final int lastIndex;
        private final int tableIndex;

        /******************************************************************************************
         * Table tree node indices for bit-packed variables class constructor
         *
         * @param firstIndex
         *            tree node index for the first member in a group of bit-packed variables
         *
         * @param lastIndex
         *            tree node index for the last member in a group of bit-packed variables
         *
         * @param tableIndex
         *            tree node index for the selected member in a group of bit-packed variables
         *****************************************************************************************/
        BitPackNodeIndex(int firstIndex, int lastIndex, int tableIndex)
        {
            this.firstIndex = firstIndex;
            this.lastIndex = lastIndex;
            this.tableIndex = tableIndex;
        }

        /******************************************************************************************
         * Get the node index for the first member in a group of bit-packed variable
         *
         * @return Node index for the first member in a group of bit-packed variable
         *****************************************************************************************/
        protected int getFirstIndex()
        {
            return firstIndex;
        }

        /******************************************************************************************
         * Get the node index for the last member in a group of bit-packed variable
         *
         * @return Node index for the last member in a group of bit-packed variable
         *****************************************************************************************/
        protected int getLastIndex()
        {
            return lastIndex;
        }

        /******************************************************************************************
         * Get the node index for the selected member in a group of bit-packed variables
         *
         * @return Node index for the selected member in a group of bit-packed variable
         *****************************************************************************************/
        protected int getTableIndex()
        {
            return tableIndex;
        }
    }

    /**********************************************************************************************
     * Associated variables class
     *********************************************************************************************/
    protected static class AssociatedVariable
    {
        private final int totalSize;
        private final List<Variable> associates;

        /******************************************************************************************
         * Associated variables class constructor. Variables are considered 'associated' if they
         * are bit-wise variables that are packed together or are members of a string
         *
         * @param totalSize
         *            node index for the first member in a group of bit-packed variable
         *
         * @param associates
         *            list of associated variables
         *****************************************************************************************/
        AssociatedVariable(int totalSize, List<Variable> associates)
        {
            this.totalSize = totalSize;
            this.associates = associates;
        }

        /******************************************************************************************
         * Get the total size in bytes of the associated variables
         *
         * @return Total size in bytes of the associated variables
         *****************************************************************************************/
        protected int getTotalSize()
        {
            return totalSize;
        }

        /******************************************************************************************
         * Get the list of associated variables
         *
         * @return List of associated variables
         *****************************************************************************************/
        protected List<Variable> getAssociates()
        {
            return associates;
        }
    }

    /**********************************************************************************************
     * Variable generator class
     *********************************************************************************************/
    protected static class VariableGenerator
    {
        /******************************************************************************************
         * Generate a telemetry data object
         *
         * @param data
         *            member column value
         *
         * @return Telemetry data object based on the supplied data
         *****************************************************************************************/
        static Variable generateTelemetryData(String data)
        {
            String[] varData = data.split("\\" + TLM_SCH_SEPARATOR, 2);
            String name = varData[1];
            Float rate = Float.valueOf(varData[0]);
            varData = varData[1].split(",");
            String dataType = varData[varData.length - 1].split("\\.")[0];

            return new TelemetryData(dataType, name, rate);
        }

        /******************************************************************************************
         * Create list of variables based on the path and rate passed in. If the path is a unlinked
         * variable it will return a list of one, else it will return a list of the link's
         * variables
         *
         * @param path
         *            tree path to the variable
         *
         * @param rate
         *            rate of the variable
         *
         * @return Telemetry data object
         *****************************************************************************************/
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

        /******************************************************************************************
         * Generate an application data object
         *
         * @param data
         *            member column value
         *
         * @return Application data object based on the supplied data
         *****************************************************************************************/
        static Variable generateApplicationData(String data)
        {
            String[] varData = data.split(",", DefaultApplicationField.values().length + 1);

            return new ApplicationData(varData[0],
                                       Float.valueOf(varData[DefaultApplicationField.SCHEDULE_RATE.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.EXECUTION_TIME.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.PRIORITY.ordinal() + 1]),
                                       Integer.valueOf(varData[DefaultApplicationField.MESSAGE_RATE.ordinal() + 1]),
                                       varData[DefaultApplicationField.WAKE_UP_MESSAGE.ordinal() + 1],
                                       Integer.valueOf(varData[DefaultApplicationField.HK_SEND_RATE.ordinal() + 1]),
                                       varData[DefaultApplicationField.HK_WAKE_UP_MESSAGE.ordinal() + 1],
                                       varData[DefaultApplicationField.SCH_GROUP.ordinal() + 1]);
        }
    }

    /**********************************************************************************************
     * Variable class. Used to denote a variable that is downlinked
     *********************************************************************************************/
    protected static class Variable implements Comparable<Variable>
    {
        private int size;
        private final String pathName;
        private final float rate;

        // Indices of the messages in which the variable is contained
        private final List<Integer> messageIndices;

        /******************************************************************************************
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
         *****************************************************************************************/
        Variable(int size, String pathName, float rate)
        {
            this.size = size;
            this.pathName = pathName;
            this.rate = rate;
            messageIndices = new ArrayList<Integer>();
        }

        /******************************************************************************************
         * Get the size of the variable in bytes
         *
         * @return Size of the variable in bytes
         *****************************************************************************************/
        protected int getSize()
        {
            return size;
        }

        /******************************************************************************************
         * Set the size of the variable in bytes
         *
         * @param size
         *            size of the variable
         *****************************************************************************************/
        protected void setSize(int size)
        {
            this.size = size;
        }

        /******************************************************************************************
         * Get the full path and name of the variable. This includes the root structure and all of
         * the variables in this variable's path
         *
         * @return Variable's full path and name
         *****************************************************************************************/
        protected String getFullName()
        {
            return pathName;
        }

        /******************************************************************************************
         * Get the rate of the variable
         *
         * @return Rate of the variable, Hertz
         *****************************************************************************************/
        protected float getRate()
        {
            return rate;
        }

        /******************************************************************************************
         * Get the indices of the messages in which the variable is contained
         *
         * @return List containing the indices of the messages in which the variable is contained
         *****************************************************************************************/
        protected List<Integer> getMessageIndices()
        {
            return messageIndices;
        }

        /******************************************************************************************
         * Set the indices for the messages in which the variable is contained
         *
         * @param msgIndices
         *            array of indices in which the variable is contained
         *****************************************************************************************/
        protected void setMessageIndices(Integer msgIndices[])
        {
            // Step through each index
            for (int index = 0; index < msgIndices.length; index++)
            {
                // Add the message index
                addMessageIndex(msgIndices[index]);
            }
        }

        /******************************************************************************************
         * Add an index to the list of message indices. Duplicate indices are ignored
         *
         * @param index
         *            index to add
         *****************************************************************************************/
        protected void addMessageIndex(int index)
        {
            // Check if the message index hasn't already been added
            if (!(messageIndices.contains(index)))
            {
                // Add the index to the list
                messageIndices.add(index);
            }
        }

        /******************************************************************************************
         * Implement the compareTo method so a variable can be sorted based on byte size
         *
         * @param compVar
         *            variable to be compared
         *
         * @return 0 if the sizes are the same; -1 if the comparison variable's size if greater
         *         than this variable's size; 1 if this variable's size is greater than the
         *         comparison variable's size *
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the name of the link to which the item belongs. Override this function if the
         * sub-class can have linked data
         *
         * @return Link name; null if the item is not a link member
         *****************************************************************************************/
        protected String getLink()
        {
            return null;
        }
    }

    /**********************************************************************************************
     * Telemetry data class. Used for the telemetry scheduler
     *********************************************************************************************/
    protected static class TelemetryData extends Variable
    {
        private final String dataType;
        private String link;

        /******************************************************************************************
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
         *****************************************************************************************/
        TelemetryData(String dataType, String pathName, float rate)
        {
            super(ccddMain.getDataTypeHandler().getSizeInBytes(dataType), pathName, rate);
            this.dataType = dataType;

            // Initialize the link name to indicate no link membership; the link name is set
            // elsewhere to account for possible changes to link membership
            link = null;
        }

        /******************************************************************************************
         * Gets the variable data type
         *
         * @return Variable data type
         *****************************************************************************************/
        protected String getDataType()
        {
            return dataType;
        }

        /******************************************************************************************
         * Get the name of the link for which this variable is a member
         *
         * @return Name of the link for which this variable is a member; null if the variable is
         *         not a member of a link
         *****************************************************************************************/
        @Override
        protected String getLink()
        {
            return link;
        }

        /******************************************************************************************
         * Set the name of the link for which this variable is a member
         *
         * @param link
         *            name of the link for which this variable is a member; null if the variable is
         *            not a member of a link
         *****************************************************************************************/
        protected void setLink(String link)
        {
            this.link = link;
        }
    }

    /**********************************************************************************************
     * Application data class
     *********************************************************************************************/
    protected static class ApplicationData extends Variable
    {
        private int priority;
        private int messageRate;
        private String wakeUpMessage;
        private int hkSendRate;
        private String hkWakeUpMessage;
        private String schGroup;

        /******************************************************************************************
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
         * @param wakeUpMessage
         *            application wake-up message name and ID
         *
         * @param hkSendRate
         *            application housekeeping send rate
         *
         * @param hkWakeUpMessage
         *            application wake-up message name and ID
         *
         * @param schGroup
         *            application schedule group
         *****************************************************************************************/
        ApplicationData(String name,
                        float rate,
                        int runTime,
                        int priority,
                        int messageRate,
                        String wakeUpMessage,
                        int hkSendRate,
                        String hkWakeUpMessage,
                        String schGroup)
        {
            // Call the variable constructor
            super(runTime, name, rate);

            this.priority = priority;
            this.messageRate = messageRate;
            this.wakeUpMessage = wakeUpMessage;
            this.hkSendRate = hkSendRate;
            this.hkWakeUpMessage = hkWakeUpMessage;
            this.schGroup = schGroup;
        }

        /******************************************************************************************
         * Get the application priority
         *
         * @return Application priority
         *****************************************************************************************/
        protected int getPriority()
        {
            return priority;
        }

        /******************************************************************************************
         * Set the application priority
         *
         * @param priority
         *            new application priority
         *****************************************************************************************/
        protected void setPriority(int priority)
        {
            this.priority = priority;
        }

        /******************************************************************************************
         * Get the message rate
         *
         * @return Message rate
         *****************************************************************************************/
        protected int getMessageRate()
        {
            return messageRate;
        }

        /******************************************************************************************
         * Set the message rate
         *
         * @param messageRate
         *            message rate
         *****************************************************************************************/
        protected void setMessageRate(int messageRate)
        {
            this.messageRate = messageRate;
        }

        /******************************************************************************************
         * Get the application wake-up message name and ID
         *
         * @return Application wake-up message name and ID
         *****************************************************************************************/
        protected String getWakeUpMessage()
        {
            return wakeUpMessage;
        }

        /******************************************************************************************
         * Set the application wake-up message name and ID
         *
         * @param wakeUpMessage
         *            new application wake-up message name and ID
         *****************************************************************************************/
        protected void setWakeUpMessage(String wakeUpMessage)
        {
            this.wakeUpMessage = wakeUpMessage;
        }

        /******************************************************************************************
         * Get the application wake-up message ID from the message name and ID
         *
         * @return Application wake-up message ID
         *****************************************************************************************/
        protected String getWakeUpID()
        {
            return CcddMessageIDHandler.getMessageID(wakeUpMessage);
        }

        /******************************************************************************************
         * Get the application housekeeping send rate
         *
         * @return Application housekeeping send rate
         *****************************************************************************************/
        protected int getHkSendRate()
        {
            return hkSendRate;
        }

        /******************************************************************************************
         * Set the application housekeeping send rate
         *
         * @param hkSendRate
         *            new application housekeeping send rate
         *****************************************************************************************/
        protected void setHkSendRate(int hkSendRate)
        {
            this.hkSendRate = hkSendRate;
        }

        /******************************************************************************************
         * Get the application housekeeping wake-up message name and ID
         *
         * @return Application housekeeping wake-up message name and ID
         *****************************************************************************************/
        protected String getHkWakeUpMessage()
        {
            return hkWakeUpMessage;
        }

        /******************************************************************************************
         * Set the application housekeeping wake-up message name and ID
         *
         * @param hkWakeUpMessage
         *            new application housekeeping wake-up message name and ID
         *****************************************************************************************/
        protected void setHkWakeUpMessage(String hkWakeUpMessage)
        {
            this.hkWakeUpMessage = hkWakeUpMessage;
        }

        /******************************************************************************************
         * Get the application schedule group
         *
         * @return Application schedule group
         *****************************************************************************************/
        protected String getSchGroup()
        {
            return schGroup;
        }

        /******************************************************************************************
         * Set the application schedule group
         *
         * @param schGroup
         *            new application schedule group
         *****************************************************************************************/
        protected void setSchGroup(String schGroup)
        {
            this.schGroup = schGroup;
        }
    }

    /**********************************************************************************************
     * Message class store variable data
     *********************************************************************************************/
    protected static class Message
    {
        private String name;
        private String id;
        private int bytesRemaining;
        private final Message parentMessage;
        private List<Message> subMessages;
        private final List<Variable> variables;

        /******************************************************************************************
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
         *            parent of the sub-message; null if this is not a sub-message
         *
         * @param subMessages
         *            sub-message initialization; null if this is not a parent message
         *****************************************************************************************/
        Message(String messageName,
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

        /******************************************************************************************
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
         *****************************************************************************************/
        Message(String messageName, String messageID, int bytes)
        {
            this(messageName, messageID, bytes, null, new ArrayList<Message>());

            // Add a new sub-message. This will only happen if the message is a parent
            addNewSubMessage(messageID);
        }

        /******************************************************************************************
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
         *****************************************************************************************/
        Message(String subMessageName, String subMsgID, int bytes, Message msg)
        {
            this(subMessageName, subMsgID, bytes, msg, null);
        }

        /******************************************************************************************
         * Get the message name
         *
         * @return Message name
         *****************************************************************************************/
        protected String getName()
        {
            String messageName = name;

            return messageName;
        }

        /******************************************************************************************
         * Set the message name
         *
         * @param name
         *            message name
         *****************************************************************************************/
        protected void setName(String name)
        {
            // Check if this is a parent message
            if (subMessages != null)
            {
                // Step through the sub-messages
                for (Message subMsg : subMessages)
                {
                    // Update the sub-message name to match the parent name change
                    subMsg.setName(subMsg.getName().replaceFirst(this.name, name));
                }
            }

            this.name = name;
        }

        /******************************************************************************************
         * Get the message ID
         *
         * @return Message ID
         *****************************************************************************************/
        protected String getID()
        {
            return id;
        }

        /******************************************************************************************
         * Set the message ID
         *
         * @param messageID
         *            message ID
         *****************************************************************************************/
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
            else if (this.equals(parentMessage.getSubMessage(0)))
            {
                // Set the parent message ID to match
                parentMessage.id = messageID;
            }
        }

        /******************************************************************************************
         * Get the list of sub-messages
         *
         * @return List of sub-messages
         *****************************************************************************************/
        protected List<Message> getSubMessages()
        {
            return subMessages;
        }

        /******************************************************************************************
         * Get the parent message
         *
         * @return Parent message
         *****************************************************************************************/
        protected Message getParentMessage()
        {
            return parentMessage;
        }

        /******************************************************************************************
         * Create a new sub-message
         *
         * @param subMsgID
         *            sub-message ID
         *****************************************************************************************/
        protected void addNewSubMessage(String subMsgID)
        {
            // Check if the sub-messages doesn't exist
            if (subMessages == null)
            {
                subMessages = new ArrayList<Message>();
            }

            // Create the name of the sub-message
            String subName = name + "." + getNumberOfSubMessages();

            // Add a sub-message to the message list
            subMessages.add(new Message(subName, subMsgID, bytesRemaining, this));
        }

        /******************************************************************************************
         * Delete the sub-message at the specified index
         *
         * @param index
         *            sub-message index
         *****************************************************************************************/
        protected void removeSubMessage(int index)
        {
            // Check if the index is allowed
            if (subMessages.size() > index)
            {
                // Remove the message at the index
                subMessages.remove(index);
            }
        }

        /******************************************************************************************
         * Get the sub-message at the specified index
         *
         * @param index
         *            message index
         *
         * @return The sub-message at the specified index
         *****************************************************************************************/
        protected Message getSubMessage(int index)
        {
            return subMessages.get(index);
        }

        /******************************************************************************************
         * Get the number of sub-messages
         *
         * @return Number of sub-messages; 0 if there are no sub-messages (i.e., this isn't a
         *         parent message; parent messages always have at least one sub-message)
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the variable at the specified index
         *
         * @param index
         *            index of the variable
         *
         * @return Specified variable object
         *****************************************************************************************/
        protected Variable getVariable(int index)
        {
            return variables.get(index);
        }

        /******************************************************************************************
         * Get the index of a variable with the specified name
         *
         * @param name
         *            name of the variable
         *
         * @return Index of the variable with the specified name; -1 if the variable doesn't exist
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the variable with the specified name
         *
         * @param name
         *            name of the variable
         *
         * @return Variable object with the specified name; null if the variable is not a member of
         *         the message or any of its sub-messages
         *****************************************************************************************/
        protected Variable getVariable(String name)
        {
            Variable variable = null;

            // Check if the variable is in the message
            if (isVariableInMessage(name))
            {
                // Store the variable reference
                variable = variables.get(getVariableIndex(name));
            }
            else if (subMessages != null)
            {
                // Step through all the sub-messages to check if the variable is stored in a
                // sub-message
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

        /******************************************************************************************
         * Get a list of the variables for this message
         *
         * @return List of message's variables
         *****************************************************************************************/
        protected List<Variable> getVariables()
        {
            return variables;
        }

        /******************************************************************************************
         * Get the list of all the variables including those within the sub-messages (if
         * applicable)
         *
         * @return All variables including sub-message variables
         *****************************************************************************************/
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
                        // Check if the variable has already been added to the list. This can
                        // happen if a variable is assigned to two sub-messages
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

        /******************************************************************************************
         * Get all variables for the message including its parent's (if applicable)
         *
         * @return All variables for the message its parent
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the number of variables in the message
         *
         * @return Number of variables in the message
         *****************************************************************************************/
        protected int getNumberOfVariables()
        {
            return variables.size();
        }

        /******************************************************************************************
         * Add a variable to the message. Append the variable to the end of the list
         *
         * @param variable
         *            variable to add
         *****************************************************************************************/
        protected void addVariable(Variable variable)
        {
            addVariable(variable, -1);
        }

        /******************************************************************************************
         * Add a variable to the message. Insert the variable into the list at the specified index
         *
         * @param variable
         *            variable to add
         *
         * @param index
         *            position in the variable list to insert the variable; -1 to append the
         *            variable to the end of the list
         *****************************************************************************************/
        protected void addVariable(Variable variable, int index)
        {
            // Check if the index indicates that the variable should be added to the end of the
            // list
            if (index == -1)
            {
                // Set the index to the end of the list
                index = variables.size();
            }

            // Insert/append the variable to the list
            variables.add(index, variable);
        }

        /******************************************************************************************
         * Remove the variable with the specified name
         *
         * @param varName
         *            name of the variable to remove
         *
         * @return Variable object that is removed
         *****************************************************************************************/
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

        /******************************************************************************************
         * Get the number of bytes remaining in the message
         *
         * @return Number of bytes remaining
         *****************************************************************************************/
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

        /******************************************************************************************
         * Set the number of bytes remaining in the message
         *
         * @param bytesRemaining
         *            number of bytes remaining
         *****************************************************************************************/
        protected void setBytesRemaining(int bytesRemaining)
        {
            this.bytesRemaining = bytesRemaining;
        }

        /******************************************************************************************
         * Get the byte count for the sub-message with the least number of bytes remaining
         *
         * @return Number of bytes remaining
         *****************************************************************************************/
        private int getSmallestSubMessageBytesRemaining()
        {
            // Initialize the smallest value to the parent's bytes remaining - a sub-message's byte
            // count can't be greater than the parent's, only the same or smaller
            int smallest = bytesRemaining;

            // Step through each sub-message
            for (Message msg : subMessages)
            {
                // Check if the byte count is smaller than the smallest thus far
                if (msg.getBytesRemaining() < smallest)
                {
                    // Store the new smallest byte count
                    smallest = msg.getBytesRemaining();
                }
            }

            return smallest;
        }

        /******************************************************************************************
         * Check if a variable is in the message
         *
         * @param varName
         *            name of variable
         *
         * @return true if the message contains the specified variable
         *****************************************************************************************/
        protected boolean isVariableInMessage(String varName)
        {
            boolean isInMsg = false;

            // Step through each variable in the message
            for (Variable variable : variables)
            {
                // Check if the variable name matches the specified name
                if (variable.getFullName().equals(varName))
                {
                    // Set the flag indicating the variable is in the message and stop searching
                    isInMsg = true;
                    break;
                }
            }

            return isInMsg;
        }
    }

    /**********************************************************************************************
     * Data stream class. Used to store and retrieve the data from the scheduler handler
     *********************************************************************************************/
    protected static class DataStream
    {
        private final List<Message> messages;
        private final List<Variable> varList;
        private final String rateName;

        /******************************************************************************************
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
         *****************************************************************************************/
        DataStream(List<Message> messages, List<Variable> varList, String rateName)
        {
            this.messages = messages;
            this.varList = varList;
            this.rateName = rateName;
        }

        /******************************************************************************************
         * Data steam class constructor
         *
         * @param messages
         *            list of messages for the data stream
         *
         * @param varList
         *            list of variables in the data stream
         *****************************************************************************************/
        DataStream(List<Message> messages, List<Variable> varList)
        {
            this(messages, varList, "");
        }

        /******************************************************************************************
         * Data steam class constructor
         *
         * @param rateName
         *            rate column name
         *****************************************************************************************/
        DataStream(String rateName)
        {
            this(new ArrayList<Message>(), new ArrayList<Variable>(), rateName);
        }

        /******************************************************************************************
         * Data steam class constructor
         *
         * @param messages
         *            list of messages for the data stream
         *
         * @param rateName
         *            rate column name
         *****************************************************************************************/
        DataStream(List<Message> messages, String rateName)
        {
            this(messages, new ArrayList<Variable>(), rateName);
        }

        /******************************************************************************************
         * Get the variable list for the data stream
         *
         * @return List of variables in the data stream
         *****************************************************************************************/
        protected List<Variable> getVariableList()
        {
            return varList;
        }

        /******************************************************************************************
         * Get the messages in the data stream
         *
         * @return List of messages in the data stream
         *****************************************************************************************/
        protected List<Message> getMessages()
        {
            return messages;
        }

        /******************************************************************************************
         * Get the rate column name
         *
         * @return Rate column name
         *****************************************************************************************/
        protected String getRateName()
        {
            return rateName;
        }
    }

    /**********************************************************************************************
     * Table opener class. Used by dialogs with a table open button to open the selected table(s)
     * into a table editor
     *********************************************************************************************/
    protected static class TableOpener
    {
        /******************************************************************************************
         * Check if the table is applicable for opening. Override this method to include criteria
         * that the table must meet or to alter the specified table name
         *
         * @param tableName
         *            the raw value from the table cell specified as the table name column in the
         *            openTables() method
         *
         * @return true if the table should be added to the list of ones to open
         *****************************************************************************************/
        protected boolean isApplicable(String tableName)
        {
            return true;
        }

        /******************************************************************************************
         * Override this method to perform any clean up actions on the raw table name value
         *
         * @param tableName
         *            the raw value from the table cell specified as the table name column in the
         *            openTables() method
         *
         * @param row
         *            table row index containing the raw table name value
         *
         * @return true if the table should be added to the list of ones to open
         *****************************************************************************************/
        protected String cleanUpTableName(String tableName, int row)
        {
            return tableName;
        }

        /******************************************************************************************
         * Open the table(s) in the currently selected row(s)
         *
         * @param table
         *            reference to the table containing the table name
         *
         * @param tableNameColumn
         *            table column index containing the raw table name value
         *****************************************************************************************/
        protected void openTables(CcddJTableHandler table, int tableNameColumn)
        {
            List<String> tablePaths = new ArrayList<String>();

            // Step through each row in the table
            for (int row = 0; row < table.getRowCount(); row++)
            {
                // Check if the row is selected
                if (table.isRowSelected(row))
                {
                    // Get the variable path for this row with the HTML tag removed
                    String tableName = table.getValueAt(row, tableNameColumn).toString();

                    // Check if the table meets the criteria for inclusion in the list of tables to
                    // open
                    if (isApplicable(tableName))
                    {
                        // Clean up the table name, if needed
                        tableName = cleanUpTableName(tableName, row);

                        // Check if the table isn't already in the list of those to be opened
                        if (!tablePaths.contains(tableName))
                        {
                            // Add the table path to the list
                            tablePaths.add(tableName);
                        }
                    }
                }
            }

            // Check if any table is selected
            if (!tablePaths.isEmpty())
            {
                // Load the selected table's data into a table editor
                ccddMain.getDbTableCommandHandler().loadTableDataInBackground(tablePaths.toArray(new String[0]),
                                                                              null);
            }
        }
    }
}
