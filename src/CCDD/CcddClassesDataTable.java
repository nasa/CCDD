/**************************************************************************************************
 * /** \file CcddClassesDataTable.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Collection of common classes used by other CCDD classes. These classes, in general, are
 * used to manipulate and contain information with respect to the data tables.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
package CCDD;

import static CCDD.CcddConstants.MACRO_IDENTIFIER;
import static CCDD.CcddConstants.SELECTION_ITEM_LIST_SEPARATOR;
import static CCDD.CcddConstants.TLM_SCH_SEPARATOR;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.apache.commons.lang3.StringUtils;

import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultApplicationField;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.FieldsColumn;

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
     * @param main Main class reference
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
         * @param message     Exception message
         *
         * @param messageType JOptionPane message type
         *****************************************************************************************/
        protected CCDDException(String message, int messageType)
        {
            this.message = message;
            this.messageType = messageType;
        }

        /******************************************************************************************
         * CCDD exception class constructor for an error message type
         *
         * @param message Exception message
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
    protected static class TableInfo
    {
        private String tableType;
        private String tablePath;
        private List<Object[]> tableData;
        private String columnOrder;
        private String description;
        private boolean isPrototype;
        private boolean errorFlag;
        private List<FieldInformation> fieldInformation;

        /******************************************************************************************
         * Table information class constructor
         *
         * @param tableType   Table type
         *
         * @param tablePath   Table path in the format rootTable[,dataType1.variable1[,dataType2
         *                    .variable2[,...]]]. The table path for a non-structure table is
         *                    simply the root table name. For a structure table the root table is
         *                    the top level structure table from which this table descends. The
         *                    first data type/variable name pair is from the root table, with each
         *                    succeeding pair coming from the next level down in the structure's
         *                    hierarchy
         *
         * @param data        Two-dimensional table data array (rows x columns)
         *
         * @param columnOrder Table column display order in the format
         *                    column#0[:column#1[:...[:column#N]]]. The column numbers are based on
         *                    the position of the column's definition in the table's type
         *                    definition
         *
         * @param description Table description
         *****************************************************************************************/
        TableInfo(String tableType,
                  String tablePath,
                  Object[][] data,
                  String columnOrder,
                  String description)
        {
            this.tableType = tableType;
            this.tablePath = tablePath;
            this.columnOrder = columnOrder;
            this.description = description;
            isPrototype = !tablePath.contains(".");
            errorFlag = false;
            tableData = Arrays.asList(data);
        }

        /******************************************************************************************
         * Table information class constructor
         *
         * @param tableType   Table type
         *
         * @param tablePath   Table path in the format rootTable[,dataType1.variable1[,dataType2
         *                    .variable2[,...]]]. The table path for a non-structure table is
         *                    simply the root table name. For a structure table the root table is
         *                    the top level structure table from which this table descends. The
         *                    first data type/variable name pair is from the root table, with each
         *                    succeeding pair coming from the next level down in the structure's
         *                    hierarchy
         *
         * @param data        Two-dimensional table data array (rows x columns)
         *
         * @param columnOrder Table column display order in the format
         *                    column#0[:column#1[:...[:column#N]]]. The column numbers are based on
         *                    the position of the column's definition in the table's type
         *                    definition
         *
         * @param description Table description
         *****************************************************************************************/
        TableInfo(String tableType,
                  String tablePath,
                  List<Object[]> data,
                  String columnOrder,
                  String description)
        {
            this.tableType = tableType;
            this.tablePath = tablePath;
            this.columnOrder = columnOrder;
            this.description = description;
            isPrototype = !tablePath.contains(".");
            errorFlag = false;
            tableData = data;
        }

        /******************************************************************************************
         * Table information class constructor
         *
         * @param tablePath   Table path in the format rootTable[,dataType1.variable1[,dataType2
         *                    .variable2[,...]]]. The table path for a non-structure table is
         *                    simply the root table name. For a structure table the root table is
         *                    the top level structure table from which this table descends. The
         *                    first data type/variable name pair is from the root table, with each
         *                    succeeding pair coming from the next level down in the structure's
         *                    hierarchy
         *
         * @param tableType   Table type
         *
         * @param description Table description
         *
         * @param data        List of String[] containing the table data
         *****************************************************************************************/
        TableInfo(String tablePath, String tableType, String description, List<Object[]> data)
        {
            this.tableType = tableType;
            this.tablePath = tablePath;
            this.description = description;
            isPrototype = !tablePath.contains(".");
            errorFlag = false;
            tableData = data;
        }

        /******************************************************************************************
         * Table information class constructor. Used when the array of field definitions are
         * retrieved from the database. These are converted to a list of FieldInformation
         * references
         *
         * @param tableType        Table type
         *
         * @param tablePath        Table path in the format
         *                         rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
         *                         The table path for a non-structure table is simply the root
         *                         table name. For a structure table the root table is the top
         *                         level structure table from which this table descends. The first
         *                         data type/variable name pair is from the root table, with each
         *                         succeeding pair coming from the next level down in the
         *                         structure's hierarchy
         *
         * @param tableData        Two-dimensional table data array (rows x columns)
         *
         * @param columnOrder      Table column display order in the format
         *                         column#0[:column#1[:...[:column#N]]]. The column numbers are
         *                         based on the position of the column's definition in the table's
         *                         type definition
         *
         * @param description      Table description
         *
         * @param fieldInformation List of field information
         *****************************************************************************************/
        TableInfo(String tableType,
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
         * Table information class constructor. Used when the array of field definitions are
         * retrieved from the database. These are converted to a list of FieldInformation
         * references
         *
         * @param tableType        Table type
         *
         * @param tablePath        Table path in the format
         *                         rootTable[,dataType1.variable1[,dataType2 .variable2[,...]]].
         *                         The table path for a non-structure table is simply the root
         *                         table name. For a structure table the root table is the top
         *                         level structure table from which this table descends. The first
         *                         data type/variable name pair is from the root table, with each
         *                         succeeding pair coming from the next level down in the
         *                         structure's hierarchy
         *
         * @param tableData        Two-dimensional table data array (rows x columns)
         *
         * @param columnOrder      Table column display order in the format
         *                         column#0[:column#1[:...[:column#N]]]. The column numbers are
         *                         based on the position of the column's definition in the table's
         *                         type definition
         *
         * @param description      Table description
         *
         * @param fieldInformation List of field information
         *****************************************************************************************/
        TableInfo(String tableType,
                  String tablePath,
                  List<Object[]> tableData,
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
         * @param tablePath Table path in the format rootTable[,dataType1.variable1[,dataType2
         *                  .variable2[,...]]]. The table path for a non-structure table is simply
         *                  the root table name. For a structure table the root table is the top
         *                  level structure table from which this table descends. The first data
         *                  type/variable name pair is from the root table, with each succeeding
         *                  pair coming from the next level down in the structure's hierarchy
         *****************************************************************************************/
        TableInfo(String tablePath)
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
         * @param tableType Table type
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
         * @param path Table path in the format rootTable[,dataType1.variable1[,dataType2
         *             .variable2[,...]]]
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
         * @param path Table path in the format rootTable[,dataType1.variable1[,dataType2
         *             .variable2[,...]]]
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
         * @param tablePath Table path in the format rootTable[,dataType1.variable1[,dataType2
         *                  .variable2[,...]]]. The table path for a non-structure table is simply
         *                  the root table name. For a structure table the root table is the top
         *                  level structure table from which this table descends. The first data
         *                  type/variable name pair is from the root table, with each succeeding
         *                  pair coming from the next level down in the structure's hierarchy
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
         * @param path Table path in the format rootTable[,dataType1.variable1[,dataType2
         *             .variable2[,...]]]. The table path for a non-structure table is simply the
         *             root table name. For a structure table the root table is the top level
         *             structure table from which this table descends. The first data type/variable
         *             name pair is from the root table, with each succeeding pair coming from the
         *             next level down in the structure's hierarchy
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
         * @param path Table path in the format rootTable[,dataType1.variable1[,dataType2
         *             .variable2[,...]]]. The table path for a non-structure table is simply the
         *             root table name. For a structure table the root table is the top level
         *             structure table from which this table descends. The first data type/variable
         *             name pair is from the root table, with each succeeding pair coming from the
         *             next level down in the structure's hierarchy
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
         * @param rootTable Root table name
         *****************************************************************************************/
        protected void setRootTable(String rootTable)
        {
            tablePath = tablePath.replaceFirst("^.*?(,|$)", rootTable + "$1");
        }

        /******************************************************************************************
         * Get the table data as a list of Object[]
         *
         * @return Table data array
         *****************************************************************************************/
        protected List<Object[]> getData()
        {
            return tableData;
        }

        /******************************************************************************************
         * Get the table data as a 2d object array
         *
         * @return Table data as a 2d object array
         *****************************************************************************************/
        protected Object[][] getDataArray()
        {
            Object[][] data = tableData.toArray(new Object[tableData.size()][]);
            return data;
        }

        /******************************************************************************************
         * Set the table data using the array
         *
         * @param data Table data array
         *****************************************************************************************/
        protected void setData(Object[][] data)
        {
            tableData = Arrays.asList(data);
        }

        /******************************************************************************************
         * Set the table data
         *
         * @param data Table data array
         *****************************************************************************************/
        protected void setData(List<Object[]> data)
        {
            tableData = data;
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
         * @param columnOrder Table column order
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
         * @param description Table description
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
         * @param fieldInformation Table data field information
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
         * @param name          Table name
         *
         * @param type          Table type
         *
         * @param dataTypes     List of non-primitive data types that are referenced by this table
         *
         * @param variableNames List of the variable names associated with the data types
         *
         * @param bitLengths    List of bit lengths associated with the variables
         *
         * @param rates         List of the rates associated with the variables
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
         * @param index Index into this member's variable information lists
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
         * @param index Index into this member's variable information lists
         *
         * @return Full variable name, including bit length, at the specified index
         *****************************************************************************************/
        protected String getFullVariableNameWithBits(int index)
        {
            // Get the full variable name
            StringBuilder varName = new StringBuilder(getFullVariableName(index));

            // Check if the variable has a bit length
            if (!bitLengths.get(index).isEmpty())
            {
                // Append the bit length to the variable name
                varName.append(":" + bitLengths.get(index));
            }

            return varName.toString();
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
         * @param rowData         Row of data from the table containing the changes
         *
         * @param originalRowData Original contents of the row of data from the table containing
         *                        the changes
         *
         * @param variableColumn  Index of the column containing the variable name; -1 if no
         *                        variable name column exists
         *
         * @param dataTypeColumn  Index of the column containing the data type name; -1 if no data
         *                        type column exists
         *
         * @param arraySizeColumn Index of the column containing the array size; -1 if no array
         *                        size column exists
         *
         * @param bitLengthColumn Index of the column containing the bit length; -1 if no bit
         *                        length column exists
         *
         * @param rateColumn      Indices of the columns containing the sample rates; null if no
         *                        rate column exists
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
         * @param rowData         Row of data from the macro table
         *
         * @param originalRowData Original contents of the row of data from the macro table
         *****************************************************************************************/
        protected TableModification(Object[] rowData, Object[] originalRowData)
        {
            this(rowData, originalRowData, -1, -1, -1, -1, null);
        }

        /******************************************************************************************
         * Table update data class constructor for additions and deletions
         *
         * @param rowData         Row of data from the macro table
         *
         * @param variableColumn  Index of the column containing the variable name; -1 if no
         *                        variable name column exists
         *
         * @param dataTypeColumn  Index of the column containing the data type name; -1 if no data
         *                        type column exists
         *
         * @param arraySizeColumn Index of the column containing the array size; -1 if no array
         *                        size column exists
         *
         * @param bitLengthColumn Index of the column containing the bit length; -1 if no bit
         *                        length column exists
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
         * @return List containing the rate column indices; null for an addition or deletion, and
         *         an empty list if no rate column exists for a modification
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
         * @param tableName   Table name, including the path (for a structure child table)
         *
         * @param description Table description
         *
         * @throws CCDDException If the table name/path is not in the expected format
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
         * @param tableName Table name (including path, if applicable)
         *
         * @throws CCDDException If the table name/path is not in the expected format
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
         * @param typeName Table type name
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
         * @param description Table description
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
         * @param rowData Row of table data to add to the table's data
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
         * @param fieldDefn Data field definition
         *****************************************************************************************/
        protected void addDataField(String[] fieldDefn)
        {
            dataFields.add(fieldDefn);
        }

        /******************************************************************************************
         * Add a data field definition to the list of table data fields
         *
         * @param fieldDefn Data field definition
         *****************************************************************************************/
        protected void addDataField(FieldInformation fieldDefn)
        {
            String[] fieldData = fieldDefn.toStringArray();

            addDataField(fieldData);
        }

        /******************************************************************************************
         * Add a data field definition to the list of table data fields in the given index
         *
         * @param fieldDefn Data field definition
         *
         * @param index     Location to add the definition
         *****************************************************************************************/
        protected void addDataFieldToIndex(String[] fieldDefn, int index)
        {
            dataFields.add(index, fieldDefn);
        }

        /******************************************************************************************
         * Remove a data field definition from the list of table data fields located at the given
         * index
         *
         * @param index Location to remove the field from
         *****************************************************************************************/
        protected void removeDataFieldByIndex(int index)
        {
            dataFields.remove(index);
        }

        /******************************************************************************************
         * Remove all data field definitions from the list of table data fields
         *****************************************************************************************/
        protected void removeDataFields()
        {
            dataFields.clear();
        }

        /******************************************************************************************
         * Replace a data field definition located at the given index
         *
         * @param fieldDefn Data field definition
         *
         * @param index     Location to replace the field
         *****************************************************************************************/
        protected void replaceDataFieldByIndex(String[] fieldDefn, int index)
        {
            dataFields.remove(index);
            dataFields.add(index, fieldDefn);
        }

        /******************************************************************************************
         * Replace a data field definition located at the given index
         *
         * @param fieldDefn Data field definition
         *
         * @param index     Location to replace the field
         *****************************************************************************************/
        protected void replaceDataFieldByIndex(FieldInformation fieldDefn, int index)
        {
            String[] fieldData = fieldDefn.toStringArray();

            replaceDataFieldByIndex(fieldData, index);
        }

        /******************************************************************************************
         * Does a field with the current name already exist? if so return the index of the field.
         * If not return -1
         *
         * @param fieldName Data field name
         *
         * @return Index of the data field. -1 if field not found
         *****************************************************************************************/
        protected int contains(String fieldName)
        {
            int result = -1;

            for (int index = 0; index < dataFields.size(); index++)
            {
                // Check that the name is not null
                if (dataFields.get(index)[FieldsColumn.FIELD_NAME.ordinal()] != null)
                {
                    if (dataFields.get(index)[FieldsColumn.FIELD_NAME.ordinal()].contentEquals(fieldName))
                    {
                        result = index;
                    }
                }
            }

            return result;
        }

        /******************************************************************************************
         * Check if the table name/path is in the expected format. Macros aren't expanded; the
         * delimiters are simply removed
         *
         * @param tablePath Table path in the format
         *                  {@literal rootName<,childStructure.childName<,...>>}
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
                                                                  "$1").matches(DefaultInputType.VARIABLE.getInputMatch()
                                                                                + "(?:\\[[0-9]+\\])*?"))))
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
         * @param tablePath Table path in the format
         *                  {@literal rootName<,childStructure.childName<,...>>}
         *
         * @throws CCDDException If the table name/path is not in the expected format
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
         * @param typeName    Table type name
         *
         * @param description Table type description
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
         * @param typeName Table type name
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
         * @param column Array of table type column information
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
         * @param dataField Array of table type data field information
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
         * @param inputName        Input type name
         *
         * @param inputDescription Input type description
         *
         * @param inputMatch       Regular expression match for the input type
         *
         * @param inputItems       String containing the acceptable values for this input type,
         *                         separated by the selection item list separator; null or blank if
         *                         the input type doesn't constrain the inputs to items from a
         *                         list. The list is used to create the contents of the combo box
         *                         in the table column with this input type
         *
         * @param inputFormat      Input type format
         *
         * @param isInputCustom    True if the input type is user-defined
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
         * @param inputItems List of input type selection items (an empty item is automatically
         *                   prepended to the list to allow blanking the selection); null if the
         *                   input type has no selection items
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
         * @param formatName Input format name
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
         * @param valueS Value, represented as a string, to reformat
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
         * @param valueS         Value, represented as a string, to reformat
         *
         * @param preserveZeroes True to preserve leading zeroes in hexadecimal values; false to
         *                       eliminate the extra zeroes (this is useful when comparing the text
         *                       representation of two hexadecimal values)
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
         * @param inputItemsString String containing the acceptable values for this input type,
         *                         separated by the selection item list separator (a blank item is
         *                         automatically prepended to the list); null or blank if the input
         *                         type doesn't constrain the inputs to items from a list
         *
         * @return Input items, converted to a list; null if the input type has no items
         *****************************************************************************************/
        protected static List<String> convertItemStringToList(String inputItemsString)
        {
            return inputItemsString == null
                   || inputItemsString.isEmpty() ? null
                                                 : Arrays.asList(inputItemsString.split(SELECTION_ITEM_LIST_SEPARATOR));
        }

        /******************************************************************************************
         * Convert the input selection items from a list to a single string
         *
         * @param inputItemsList List containing the acceptable values for this input type; null or
         *                       blank if the input type doesn't constrain the inputs to items from
         *                       a list
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
                inputItemsString = CcddUtilities.removeTrailer(inputItemsString, SELECTION_ITEM_LIST_SEPARATOR);
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
         * @param fieldInfo Data field information
         *****************************************************************************************/
        protected void addDataField(String[] fieldInfo)
        {
            dataFields.add(fieldInfo);
        }
    }

    /**********************************************************************************************
     * Data type and enumeration column pairing class. Contains associated data type and
     * enumeration column indices
     *********************************************************************************************/
    public static class DataTypeEnumPair
    {
        private final int dataType;
        private final int enumeration;

        /******************************************************************************************
         * Data type and enumeration column pairing class constructor
         *
         * @param dataType    Data type column index, model coordinates
         *
         * @param enumeration Enumeration column index, model coordinates
         *****************************************************************************************/
        protected DataTypeEnumPair(int dataType, int enumeration)
        {
            this.dataType = dataType;
            this.enumeration = enumeration;
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
         * Get the command argument enumeration column index
         *
         * @return Command argument enumeration column index
         *****************************************************************************************/
        public int getEnumeration()
        {
            return enumeration;
        }
    }

    /**********************************************************************************************
     * Minimum and maximum column pairing class. Contains associated minimum value and maximum
     * value column indices
     *********************************************************************************************/
    protected static class MinMaxPair
    {
        private final int minColumn;
        private final int maxColumn;

        /******************************************************************************************
         * Minimum/maximum column pairing class constructor
         *
         * @param minColumn Minimum value column index, model coordinates
         *
         * @param maxColumn Maximum value column index, model coordinates
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
         * @param ownerName     Name of the table, including the path if this table represents a
         *                      structure, or group for which the field is a member
         *
         * @param fieldName     Field name
         *
         * @param description   Data field description; used as the tool tip for this field
         *
         * @param charSize      Field display size in characters
         *
         * @param inputType     Field input type (InputType)
         *
         * @param isRequired    True if a value is required for this field
         *
         * @param applicability Field applicability type; all tables, parent tables only, or child
         *                      tables only
         *
         * @param value         Field value
         *
         * @param isInherited   True if the field is inherited from its owner table's type
         *                      definition (ignored if the field does not belong to a table)
         *
         * @param inputFld      Reference to the field's UndoableTextField or UndoableCheckBox;
         *                      null if creating the data field without specifying the input field
         *
         * @param id            ID for the row defining the field; -1 for a new field
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
         * @param ownerName Name of the table/group to which the field belongs
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
         * @param fieldName Field name
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
         * @param description Field comment
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
         * @param charSize Field display size in characters
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
         * @param isRequired True if a value is required for this field
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
         * @param applicability Field applicability type
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
         * @param value Field value
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
         * @param isInherited True if the field is inherited from the owner table's type definition
         *                    (ignored if the field does not belong to a table)
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
         * @param inputFld Field's UndoableTextField, UndoableComboBox, or UndoableCheckBox
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
         * @param inputType Field input type (InputType)
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
         * @param id Field ID
         *****************************************************************************************/
        protected void setID(int id)
        {
            this.id = id;
        }

        /******************************************************************************************
         * Store the field information in an array of strings
         *
         * @return an array of Strings representing the field information
         *****************************************************************************************/
        protected String[] toStringArray()
        {
            String[] fieldData = {ownerName,
                                  fieldName,
                                  description,
                                  Integer.toString(charSize),
                                  inputType.getInputName(),
                                  String.valueOf(isRequired),
                                  applicability.getApplicabilityName(),
                                  value,
                                  String.valueOf(isInherited)};

            return fieldData;
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
         * @param name             Group name
         *
         * @param description      Group description
         *
         * @param isApplication    Flag indicating if the group represents a CFS application
         *
         * @param fieldInformation Data field information
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
         * @param name             Group name
         *
         * @param description      Group description
         *
         * @param isApplication    Flag indicating if the group represents a CFS application
         *
         * @param tables           List of tables (with full paths( belonging to this group; null
         *                         if creating a new, empty group
         *
         * @param fieldInformation Data field information
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
         * @param name Group name
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
         * @param description Group description
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
         * @param isApplication True if the group represents a CFS application
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
         * @param table Group table member (full table path)
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
         * @param fieldInformation Group data field information
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
         * @param rateName    Name of the data stream rate column
         *
         * @param linkName    Link name
         *
         * @param description Link description
         *****************************************************************************************/
        LinkInformation(String rateName, String linkName, String description)
        {
            this(rateName, linkName, "0", description);
        }

        /******************************************************************************************
         * Link information class constructor
         *
         * @param rateName    Name of the data stream rate column associated with this link
         *
         * @param linkName    Link name
         *
         * @param sampleRate  Link rate in samples per second
         *
         * @param description Link description
         *****************************************************************************************/
        LinkInformation(String rateName, String linkName, String sampleRate, String description)
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
         * @param linkName Link name
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
         * @param sampleRate Link rate in samples per second; "0" if no rate is assigned to this
         *                   link
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
         * @param description Link description
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
         * @param variableName Variable name
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
         * @param variableName Variable name
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
         * @param variableName Variable name
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

                // Check if two indexes are included
                if (StringUtils.countMatches(variableName, "[") >= 2)
                {
                    // Check if any letter of the alphabet is included
                    if (Pattern.compile("[a-zA-Z]").matcher(variableName).find())
                    {
                        index = variableName.lastIndexOf("[");
                        // Get the last index portion of the array variable name
                        variableName = variableName.substring(index);
                    }
                }
            }

            return variableName;
        }

        /******************************************************************************************
         * Get the array variable index, if present, from the supplied variable name
         *
         * @param variableName Variable name
         *
         * @return The array index, with the variable name removed
         *****************************************************************************************/
        protected static int getVariableArrayIndexAsInt(String variableName)
        {
            int result = -1;
            // Get the index of the array index
            int index = variableName.indexOf('[') + 1;
            int index2 = variableName.indexOf(']');

            // Check if an array index exists
            if ((index != -1) && (index2 != -1))
            {
                // Get the index portion of the array variable name
                variableName = variableName.substring(index, index2);
                result = Integer.parseInt(variableName);
            }

            return result;
        }

        /******************************************************************************************
         * Get the array size of 1d, 2d or 3d arrays
         *
         * @param newMacroHandler Macro handler
         *
         * @param arrayInfo String representing the dimensions of the array. Example = "4" or "2,2"
         *
         * @return An array with 4 indexes. The first represents if the array is a 1d, 2d or 3d
         *         array. The second represents the number of indexes in the internal 1d arrays.
         *         The third represents the total number of 1d arrays. The fourth represents the
         *         number of 2d arrays.
         *
         *         Examples: If we have a single array with size 6 this function will return [1, 6,
         *         -1, -1]
         *
         *         If we have a 2d array of size 3 with each 1d array having a size of 6 the
         *         function will return [2, 6, 3, -1]
         *
         *         If we have a 3d array of size 2 with each index having a 2d array of size 3 with
         *         each 1d array having a size of 6 then the function will return [3, 6, 3, 2]
         *****************************************************************************************/
        protected static int[] getArraySizeAndDimensions(CcddMacroHandler newMacroHandler,
                                                         String arrayInfo)
        {
            int[] result = {-1, -1, -1, -1};

            // If a macro is being used then retrieve the value
            if (arrayInfo.contains("##"))
            {
                arrayInfo = newMacroHandler.getMacroExpansion(arrayInfo, new ArrayList<String>());
            }

            // Get rid of any whitespace
            arrayInfo = arrayInfo.replaceAll(" ", "");

            // Check that the String is not null or empty
            if (arrayInfo != null && !arrayInfo.isEmpty())
            {
                // First check to see if this is a 1d or 2d array. We do not support 3d or above
                int count = StringUtils.countMatches(arrayInfo, ",");

                if (count == 0)
                {
                    // This is a 1d array
                    result[0] = 1;

                    // Get the size. If this is a macro it will need to be expanded
                    result[1] = Integer.parseInt(newMacroHandler.getMacroExpansion(arrayInfo,
                                                                                   new ArrayList<String>()));
                }
                else if (count == 1)
                {
                    // This is a 2d array
                    String info[] = arrayInfo.split(",");

                    if ((info[0] != null && !info[0].isEmpty()) && (info[1] != null && !info[1].isEmpty()))
                    {
                        result[0] = 2;

                        // Get the size of each internal array ,If this is a macro it will need to
                        // be expanded
                        result[1] = Integer.parseInt(newMacroHandler.getMacroExpansion(info[1],
                                                                                       new ArrayList<String>()));

                        // Get the total number of 2d arrays. If this is a macro it will need to be
                        // expanded
                        result[2] = Integer.parseInt(newMacroHandler.getMacroExpansion(info[0],
                                                                                       new ArrayList<String>()));
                    }
                }
                else if (count == 2)
                {
                    // This is a 3d array
                    String info[] = arrayInfo.split(",");

                    if ((info[0] != null && !info[0].isEmpty())
                        && (info[1] != null && !info[1].isEmpty())
                        && (info[2] != null && !info[2].isEmpty()))
                    {
                        result[0] = 3;

                        // Get the size of each internal array. If this is a macro it will need to
                        // be expanded
                        result[1] = Integer.parseInt(newMacroHandler.getMacroExpansion(info[2],
                                                                                       new ArrayList<String>()));

                        // Get the total number of 2d arrays. If this is a macro it will need to be
                        // expanded
                        result[2] = Integer.parseInt(newMacroHandler.getMacroExpansion(info[1],
                                                                                       new ArrayList<String>()));

                        // Get the total number of 3d arrays. If this is a macro it will need to be
                        // expanded
                        result[3] = Integer.parseInt(newMacroHandler.getMacroExpansion(info[0],
                                                                                       new ArrayList<String>()));

                    }
                }

                // If it did not fall into the if or the else statement then the input was invalid
                // and [-1,-1,-1] will be returned
            }

            return result;
        }

        /******************************************************************************************
         * Check if a variable name represents an array member
         *
         * @param variableName Variable name
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
         * @param arrayIndex Array of array variable index value(s)
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
         * @param arrayString Array size value in the format
         *                    {@literal [#]<[#]<...>> or #<,#<,...>>}
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
         * @param arraySizeS String representing the array variable dimension sizes
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
         * @param arraySize Array of the array variable dimension sizes
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
         * @param arrayVariable1 First array variable member to compare
         *
         * @param arrayVariable2 Second array variable member to compare
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
         * @param rateName Rate column name (viewable) for this data stream
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
         * @param numSharedTableTypes Number of table types referencing this rate column name
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
         * @param rateName Rate column name
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
         * @param streamName Data stream name
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
         * @param maxMsgsPerCycle Stream maximum messages per cycle
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
         * @param maxBytesPerSec Stream maximum bytes per second
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
         * @param sampleRates Stream sample rate array
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
         * @param firstIndex Table row index for the first member in a group of bit-packed
         *                   variables, model coordinates
         *
         * @param lastIndex  Table row index for the last member in a group of bit-packed
         *                   variables, model coordinates
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
         * @param firstIndex Tree node index for the first member in a group of bit-packed
         *                   variables
         *
         * @param lastIndex  Tree node index for the last member in a group of bit-packed variables
         *
         * @param tableIndex Tree node index for the selected member in a group of bit-packed
         *                   variables
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
         * @param totalSize  Node index for the first member in a group of bit-packed variable
         *
         * @param associates List of associated variables
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
         * @param data Member column value
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
         * @param path Tree path to the variable
         *
         * @param rate Rate of the variable
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
         * @param data Member column value
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
         * @param size     Variable size, bytes
         *
         * @param pathName Variable path and name
         *
         * @param rate     Variable rate, Hertz
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
         * @param size Size of the variable
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
         * @param msgIndices Array of indices in which the variable is contained
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
         * @param index Index to add
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
         * @param compVar Variable to be compared
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
         * @param dataType Variable data type
         *
         * @param pathName Variable path and name
         *
         * @param rate     Variable rate, Hertz
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
         * @param link Name of the link for which this variable is a member; null if the variable
         *             is not a member of a link
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
         * @param name            Application name
         *
         * @param rate            Application run rate
         *
         * @param runTime         Application run time
         *
         * @param priority        Application execution priority
         *
         * @param messageRate     Message rate
         *
         * @param wakeUpMessage   Application wake-up message name and ID
         *
         * @param hkSendRate      Application housekeeping send rate
         *
         * @param hkWakeUpMessage Application wake-up message name and ID
         *
         * @param schGroup        Application schedule group
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
         * @param priority New application priority
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
         * @param messageRate Message rate
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
         * @param wakeUpMessage New application wake-up message name and ID
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
         * @param hkSendRate New application housekeeping send rate
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
         * @param hkWakeUpMessage New application housekeeping wake-up message name and ID
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
         * @param schGroup New application schedule group
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
         * @param messageName   Message name
         *
         * @param messageID     Message ID
         *
         * @param totalBytes    Size of the initial number of bytes in the message
         *
         * @param parentMessage Parent of the sub-message; null if this is not a sub-message
         *
         * @param subMessages   Sub-message initialization; null if this is not a parent message
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
         * @param messageName Message name
         *
         * @param messageID   Message ID
         *
         * @param bytes       Size of the initial amount of bytes in the message
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
         * @param subMessageName Sub-message name
         *
         * @param subMsgID       Sub-message ID
         *
         * @param bytes          Size of the initial amount of bytes in the message
         *
         * @param msg            Parent of the sub-message
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
         * @param name Message name
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
         * @param messageID Message ID
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
         * @param subMsgID Sub-message ID
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
         * @param index Sub-message index
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
         * @param index Message index
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
         * @param index Index of the variable
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
         * @param name Name of the variable
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
         * @param name Name of the variable
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
         * @param variable Variable to add
         *****************************************************************************************/
        protected void addVariable(Variable variable)
        {
            addVariable(variable, -1);
        }

        /******************************************************************************************
         * Add a variable to the message. Insert the variable into the list at the specified index
         *
         * @param variable Variable to add
         *
         * @param index    Position in the variable list to insert the variable; -1 to append the
         *                 variable to the end of the list
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
         * @param varName Name of the variable to remove
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
         * @param bytesRemaining Number of bytes remaining
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
         * @param varName Name of variable
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
         * @param messages List of messages for the data stream
         *
         * @param varList  List of variables in the data stream
         *
         * @param rateName Rate column name
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
         * @param messages List of messages for the data stream
         *
         * @param varList  List of variables in the data stream
         *****************************************************************************************/
        DataStream(List<Message> messages, List<Variable> varList)
        {
            this(messages, varList, "");
        }

        /******************************************************************************************
         * Data steam class constructor
         *
         * @param rateName Rate column name
         *****************************************************************************************/
        DataStream(String rateName)
        {
            this(new ArrayList<Message>(), new ArrayList<Variable>(), rateName);
        }

        /******************************************************************************************
         * Data steam class constructor
         *
         * @param messages List of messages for the data stream
         *
         * @param rateName Rate column name
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
         * @param tableName The raw value from the table cell specified as the table name column in
         *                  the openTables() method
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
         * @param tableName The raw value from the table cell specified as the table name column in
         *                  the openTables() method
         *
         * @param row       Table row index containing the raw table name value
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
         * @param table           Reference to the table containing the table name
         *
         * @param tableNameColumn Table column index containing the raw table name value
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

                        // The table name will sometimes have a variable name appended to the end.
                        // This needs to be trimmed off before attempting to open the table
                        if (StringUtils.countMatches(tableName, ",") >= 2)
                        {
                            tableName = tableName.substring(0, tableName.indexOf(",", tableName.indexOf(",") + 1));
                        }

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
                ccddMain.getDbTableCommandHandler().loadTableDataInBackground(tablePaths.toArray(new String[0]), null);
            }
        }
    }
}
