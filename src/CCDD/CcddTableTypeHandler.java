/**************************************************************************************************
 * /** \file CcddTableTypeHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling interactions with table types.
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

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.TYPE_ENUM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddConstants.TableTypeUpdate;

/**************************************************************************************************
 * CFS Command and Data Dictionary table type handler class. The table definition consists of one
 * or more table types, each with its associated column name(s) and file definition(s), and the
 * value required status of the columns
 *************************************************************************************************/
public class CcddTableTypeHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddFieldHandler fieldHandler;
    private final CcddInputTypeHandler inputTypeHandler;

    // Type definitions list
    private List<TypeDefinition> typeDefinitions;

    /**********************************************************************************************
     * Table type definition class
     *********************************************************************************************/
    protected class TypeDefinition
    {
        // Table type
        private String tableType;

        // Table column indices
        private final List<Integer> columnIndex;

        // Table column names used in the database
        private final List<String> columnNamesDatabase;

        // Table column names used in the database, bounded by double quotes if the name matches a
        // PostgreSQL reserved word
        private final List<String> columnNamesDatabaseQuoted;

        // Table column names seen by the user
        private final List<String> columnNamesUser;

        // Table column tool tip text
        private final List<String> columnToolTip;

        // Table column input type
        private final List<InputType> columnInputType;

        // Flag indicating if the column value must be unique for each row
        private final List<Boolean> isRowValueUnique;

        // Flag indicating if the column requires a value
        private final List<Boolean> isColumnRequired;

        // List for storage of flags indicating if the column applies to a specific data type
        private final List<Boolean> isStructureOk;
        private final List<Boolean> isPointerOk;

        /******************************************************************************************
         * Table type definition class constructor
         *
         * @param tableType Table type name
         *****************************************************************************************/
        private TypeDefinition(String tableType)
        {
            this.tableType = tableType;
            columnIndex = new ArrayList<Integer>();
            columnNamesDatabase = new ArrayList<String>();
            columnNamesDatabaseQuoted = new ArrayList<String>();
            columnNamesUser = new ArrayList<String>();
            columnToolTip = new ArrayList<String>();
            columnInputType = new ArrayList<InputType>();
            isColumnRequired = new ArrayList<Boolean>();
            isRowValueUnique = new ArrayList<Boolean>();
            isStructureOk = new ArrayList<Boolean>();
            isPointerOk = new ArrayList<Boolean>();
        }

        /******************************************************************************************
         * Get the table type name
         *
         * @return table type name
         *****************************************************************************************/
        protected String getName()
        {
            return tableType;
        }

        /******************************************************************************************
         * set the table type name
         *
         * @param typeName Table type name
         *****************************************************************************************/
        protected void setName(String typeName)
        {
            tableType = typeName;
        }

        /******************************************************************************************
         * Get the table type data for the type editor. The primary key and row index column
         * definitions (the first two rows) are ignored so that these aren't displayed in the type
         * editor
         *
         * @return table type data
         *****************************************************************************************/
        protected Object[][] getData()
        {
            // Create storage for the type data
            Object[][] data = new Object[getColumnCountVisible()][TableTypeEditorColumnInfo.values().length];

            // Step through each row
            for (int index = NUM_HIDDEN_COLUMNS; index < columnNamesDatabase.size(); index++)
            {
                // Calculate the visible column index
                int visibleIndex = getVisibleColumnIndex(index);

                // Store the column information
                data[visibleIndex][TableTypeEditorColumnInfo.INDEX.ordinal()] = columnIndex.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.NAME.ordinal()] = columnNamesUser.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.DESCRIPTION.ordinal()] = columnToolTip.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.UNIQUE.ordinal()] = isRowValueUnique.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.REQUIRED.ordinal()] = isColumnRequired.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()] = isStructureOk.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()] = isPointerOk.get(index);
                data[visibleIndex][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()] = columnInputType.get(index).getInputName();
            }

            return data;
        }

        /******************************************************************************************
         * Get the number of columns for this type, including hidden columns
         *
         * @return Number of columns for this type, including hidden columns
         *****************************************************************************************/
        protected int getColumnCountDatabase()
        {
            return columnNamesDatabase.size();
        }

        /******************************************************************************************
         * Get the number of visible columns for this type
         *
         * @return Number of visible columns for this type
         *****************************************************************************************/
        protected int getColumnCountVisible()
        {
            return getVisibleColumnIndex(getColumnCountDatabase());
        }

        /******************************************************************************************
         * Get the array of column names as used in the database
         *
         * @return Array of column names as used in the database
         *****************************************************************************************/
        protected String[] getColumnNamesDatabase()
        {
            return columnNamesDatabase.toArray(new String[0]);
        }

        /******************************************************************************************
         * Get the array of column names as used in the database, surrounded by double quotes if
         * the name matches a PostgreSQL key word
         *
         * @return Array of column names as used in the database, surrounded by double quotes if
         *         the name matches a PostgreSQL key word
         *****************************************************************************************/
        protected String[] getColumnNamesDatabaseQuoted()
        {
            return columnNamesDatabaseQuoted.toArray(new String[0]);
        }

        /******************************************************************************************
         * Convert the visible column names to their database equivalents. The database column name
         * is the visible name with any characters that are invalid in a database column name
         * replaced with an underscore; however, if the table type represents a structure then
         * certain column names use fixed values
         *****************************************************************************************/
        protected void setColumnNamesDatabase()
        {
            // Set the flag to true if the table type represents a structure
            boolean isStructure = isStructure();

            // Step through each visible column name
            for (int row = NUM_HIDDEN_COLUMNS; row < columnNamesDatabase.size(); row++)
            {
                // Convert the column name to the database equivalent. This bounds the name in
                // double quotes if it matches a PostgreSQL reserved word
                String dbColName = convertVisibleToDatabase(columnNamesDatabase.get(row),
                                                            columnInputType.get(row).getInputName(),
                                                            isStructure);

                // Store the name with quotes (if needed) and without quotes (if present)
                columnNamesDatabaseQuoted.set(row, dbColName);
                columnNamesDatabase.set(row, dbColName.replaceAll("\"", ""));
            }
        }

        /******************************************************************************************
         * Get the array of column names as seen by the user (includes names for hidden columns)
         *
         * @return Array of column names as seen by the user
         *****************************************************************************************/
        protected String[] getColumnNamesUser()
        {
            return columnNamesUser.toArray(new String[0]);
        }

        /******************************************************************************************
         * Get the array of column names that are not hidden
         *
         * @return Array of column names that are not hidden
         *****************************************************************************************/
        protected String[] getColumnNamesVisible()
        {
            return Arrays.copyOfRange(columnNamesUser.toArray(new String[0]),
                                      NUM_HIDDEN_COLUMNS,
                                      columnNamesUser.size());
        }

        /******************************************************************************************
         * Get the array of row value unique flags
         *
         * @return Array of row value unique flags
         *****************************************************************************************/
        protected Boolean[] isRowValueUnique()
        {
            return isRowValueUnique.toArray(new Boolean[0]);
        }

        /******************************************************************************************
         * Get the array of column required flags
         *
         * @return Array of column required flags
         *****************************************************************************************/
        protected Boolean[] isRequired()
        {
            return isColumnRequired.toArray(new Boolean[0]);
        }

        /******************************************************************************************
         * Get the array of structure data type allowed flags
         *
         * @return Array of structure data type allowed flags
         *****************************************************************************************/
        protected Boolean[] isStructureAllowed()
        {
            return isStructureOk.toArray(new Boolean[0]);
        }

        /******************************************************************************************
         * Get the array of pointer data type allowed flags
         *
         * @return Array of pointer data type allowed only flags
         *****************************************************************************************/
        protected Boolean[] isPointerAllowed()
        {
            return isPointerOk.toArray(new Boolean[0]);
        }

        /******************************************************************************************
         * Get the array of column input types
         *
         * @return Array of column input types
         *****************************************************************************************/
        protected InputType[] getInputTypes()
        {
            return columnInputType.toArray(new InputType[0]);
        }

        /******************************************************************************************
         * Get the list of column input types
         *
         * @return List of column input types
         *****************************************************************************************/
        protected List<InputType> getInputTypesList()
        {
            return columnInputType;
        }

        /******************************************************************************************
         * Get the array of column input types that are not hidden
         *
         * @return Array of column input types that are not hidden
         *****************************************************************************************/
        protected InputType[] getInputTypesVisible()
        {
            return Arrays.copyOfRange(columnInputType.toArray(new InputType[0]),
                                      NUM_HIDDEN_COLUMNS,
                                      columnInputType.size());
        }

        /******************************************************************************************
         * Get the array of column tool tip text
         *
         * @return Array of column tool tip text; null if no tool tips were found
         *****************************************************************************************/
        protected String[] getColumnToolTips()
        {
            return columnToolTip.toArray(new String[0]);
        }

        /******************************************************************************************
         * Get the table type description
         *
         * @return Table type description
         *****************************************************************************************/
        protected String getDescription()
        {
            return columnToolTip.get(0).length() < 2 ? "" : columnToolTip.get(0).substring(1);
        }

        /******************************************************************************************
         * Does the table represent a command?
         *
         * @return TRUE or FALSE as a string
         *****************************************************************************************/
        protected String representsCommandArg()
        {
            return isCommandArgumentStructure() ? "TRUE" : "FALSE";
        }

        /******************************************************************************************
         * Set the table type description
         *
         * @param description Table type description
         *****************************************************************************************/
        protected void setDescription(String description)
        {
            columnToolTip.set(0, description);
        }

        /******************************************************************************************
         * Check if the table type represents a command argument structure
         *
         * @return true if the table type represents a command argument structure
         *****************************************************************************************/
        protected boolean isCommandArgumentStructure()
        {
            return !columnToolTip.get(0).startsWith("0");
        }

        /******************************************************************************************
         * Get the index of the first column having the specified input type
         *
         * @param inputType Column input type (InputType)
         *
         * @return Index of the first column of the specified input type; -1 if no column of the
         *         specified type is found
         *****************************************************************************************/
        protected int getColumnIndexByInputType(InputType inputType)
        {
            int colIndex = -1;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type matches the type for this column
                if (columnInputType.get(column).getInputName().equals(inputType.getInputName()))
                {
                    // Store the column index and stop searching
                    colIndex = column;
                    break;
                }
            }

            return colIndex;
        }

        /******************************************************************************************
         * Get the index of the first column having the specified default input type
         *
         * @param inputType Column input type (DefaultInputType)
         *
         * @return Index of the first column of the specified default input type; -1 if no column
         *         of the specified type is found
         *****************************************************************************************/
        protected int getColumnIndexByInputType(DefaultInputType inputType)
        {
            return getColumnIndexByInputType(inputTypeHandler.getInputTypeByDefaultType(inputType));
        }

        /******************************************************************************************
         * Get the index or indices of the column(s) having the specified input type
         *
         * @param inputType Column input type (InputType)
         *
         * @return List containing the index (or indices) of the column(s) of the specified input
         *         type; an empty list if no column of the specified type is found
         *****************************************************************************************/
        protected List<Integer> getColumnIndicesByInputType(InputType inputType)
        {
            List<Integer> colIndex = new ArrayList<Integer>();

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type matches the type for this column
                if (columnInputType.get(column).getInputName().equals(inputType.getInputName()))
                {
                    // Store the column index
                    colIndex.add(column);
                }
            }

            return colIndex;
        }

        /******************************************************************************************
         * Get the index or indices of the column(s) having the specified default input type
         *
         * @param inputType Column input type (DefaultInputType)
         *
         * @return List containing the index (or indices) of the column(s) of the specified default
         *         input type; an empty list if no column of the specified type is found
         *****************************************************************************************/
        protected List<Integer> getColumnIndicesByInputType(DefaultInputType inputType)
        {
            return getColumnIndicesByInputType(inputTypeHandler.getInputTypeByDefaultType(inputType));
        }

        /******************************************************************************************
         * Get the visible name of the first column having the specified input type
         *
         * @param inputType Column input type (InputType)
         *
         * @return Visible name of the first column with the specified input type; null if no
         *         column of the specified type is found
         *****************************************************************************************/
        protected String getColumnNameByInputType(InputType inputType)
        {
            String colName = null;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type matches the type for this column
                if (columnInputType.get(column).getInputName().equals(inputType.getInputName()))
                {
                    // Store the column name and stop searching
                    colName = columnNamesUser.get(column);
                    break;
                }
            }

            return colName;
        }

        /******************************************************************************************
         * Get the visible name of the first column having the specified default input type
         *
         * @param inputType Column input type (DefaultInputType)
         *
         * @return Visible name of the first column with the specified default input type; null if
         *         no column of the specified type is found
         *****************************************************************************************/
        protected String getColumnNameByInputType(DefaultInputType inputType)
        {
            return getColumnNameByInputType(inputTypeHandler.getInputTypeByDefaultType(inputType));
        }

        /******************************************************************************************
         * Get the database name of the first column having the specified input type
         *
         * @param inputType Column input type (InputType)
         *
         * @return Database name of the first column with the specified input type; null if no
         *         column of the specified type is found
         *****************************************************************************************/
        protected String getDbColumnNameByInputType(InputType inputType)
        {
            String colName = null;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type matches the type for this column
                if (columnInputType.get(column).getInputName().equals(inputType.getInputName()))
                {
                    // Store the column name and stop searching
                    colName = columnNamesDatabase.get(column);
                    break;
                }
            }

            return colName;
        }

        /******************************************************************************************
         * Get the database name of the first column having the specified default input type
         *
         * @param inputType Column input type (DefaultInputType)
         *
         * @return Database name of the first column with the specified default input type; null if
         *         no column of the specified type is found
         *****************************************************************************************/
        protected String getDbColumnNameByInputType(DefaultInputType inputType)
        {
            return getDbColumnNameByInputType(inputTypeHandler.getInputTypeByDefaultType(inputType));
        }

        /******************************************************************************************
         * Get the index of the first column having the specified input type format
         *
         * @param inputFormat Column input type format (InputTypeFormat)
         *
         * @return Index of the first column of the specified input type format; -1 if no column of
         *         the specified format is found
         *****************************************************************************************/
        protected int getColumnIndexByInputTypeFormat(InputTypeFormat inputFormat)
        {
            int colIndex = -1;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type format matches the format for this column
                if (columnInputType.get(column).getInputFormat() == inputFormat)
                {
                    // Store the column index and stop searching
                    colIndex = column;
                    break;
                }
            }

            return colIndex;
        }

        /******************************************************************************************
         * Get the index or indices of the column(s) having the specified input type format
         *
         * @param inputFormat Column input type format (InputTypeFormat)
         *
         * @return List containing the index (or indices) of the column(s) of the specified input
         *         type format; an empty list if no column of the specified format is found
         *****************************************************************************************/
        protected List<Integer> getColumnIndicesByInputTypeFormat(InputTypeFormat inputFormat)
        {
            List<Integer> colIndex = new ArrayList<Integer>();

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type format matches the format for this column
                if (columnInputType.get(column).getInputFormat() == inputFormat)
                {
                    // Store the column index
                    colIndex.add(column);
                }
            }

            return colIndex;
        }

        /******************************************************************************************
         * Get the visible name of the first column having the specified input type format
         *
         * @param inputFormat Column input type format (InputTypeFormat)
         *
         * @return Visible name of the first column with the specified input type format; null if
         *         no column of the specified format is found
         *****************************************************************************************/
        protected String getColumnNameByInputTypeFormat(InputTypeFormat inputFormat)
        {
            String colName = null;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type format matches the format for this column
                if (columnInputType.get(column).getInputFormat() == inputFormat)
                {
                    // Store the column name and stop searching
                    colName = columnNamesDatabase.get(column);
                    break;
                }
            }

            return colName;
        }

        /******************************************************************************************
         * Get the database name of the first column having the specified input type format
         *
         * @param inputFormat Column input type format (InputTypeFormat)
         *
         * @return Database name of the first column with the specified input type format; null if
         *         no column of the specified format is found
         *****************************************************************************************/
        protected String getDbColumnNameByInputTypeFormat(InputTypeFormat inputFormat)
        {
            String colName = null;

            // Step through the column types
            for (int column = 0; column < columnInputType.size(); column++)
            {
                // Check if the input type format matches the format for this column
                if (columnInputType.get(column).getInputFormat() == inputFormat)
                {
                    // Store the column name and stop searching
                    colName = columnNamesDatabase.get(column);
                    break;
                }
            }

            return colName;
        }

        /******************************************************************************************
         * Get the index of the column having the specified name
         *
         * @param columnName Column name (as seen by the user)
         *
         * @return Column name index; -1 if no column of the specified name is found
         *****************************************************************************************/
        protected int getColumnIndexByUserName(String columnName)
        {
            int columnIndex = -1;

            // Step through the column names
            for (int column = 0; column < columnNamesUser.size(); column++)
            {
                // Check if the column name matches the column name; ignoring case sensitivity
                if (columnNamesUser.get(column).equalsIgnoreCase(columnName))
                {
                    // Store the column index and stop searching
                    columnIndex = column;
                    break;
                }
            }

            return columnIndex;
        }

        /******************************************************************************************
         * Get the viewable index of the column having the specified name. This index accounts for
         * the hidden columns, primary key and row index
         *
         * @param columnName Column name (as seen by the user)
         *
         * @return Column name view index; -1 if no column of the specified name is found
         *****************************************************************************************/
        protected int getVisibleColumnIndexByUserName(String columnName)
        {
            // Get the index of the column
            int columnIndex = getColumnIndexByUserName(columnName);

            // Check if the column exists
            if (columnIndex != -1)
            {
                // Adjust the index to account for the hidden columns
                columnIndex = getVisibleColumnIndex(columnIndex);
            }

            return columnIndex;
        }

        /******************************************************************************************
         * Get the index of the column having the specified name
         *
         * @param dbColumnName Column name (as used by the database)
         *
         * @return Column name index; -1 if no column of the specified name is found
         *****************************************************************************************/
        protected int getColumnIndexByDbName(String dbColumnName)
        {
            int columnIndex = -1;

            // Step through the column names
            for (int column = 0; column < columnNamesDatabase.size(); column++)
            {
                // Check if the column name matches the column name
                if (columnNamesDatabase.get(column).equals(dbColumnName))
                {
                    // Store the column index and stop searching
                    columnIndex = column;
                    break;
                }
            }

            return columnIndex;
        }

        /******************************************************************************************
         * Add the header and database column names, and the tool tip text to the list
         *
         * @param index            Column index
         *
         * @param databaseName     Name of the column as used in the database
         *
         * @param visibleName      Name of the column as seen by the user; e.g., as table editor
         *                         column headers
         *
         * @param comment          Column description used as the table editor column header tool
         *                         tip text
         *
         * @param inputType        Column input type (InputType)
         *
         * @param isRowValueUnique True if the each row value in the column must have a unique
         *                         value
         *
         * @param isColumnRequired True if the column must have a value when the type is committed
         *                         to the database
         *
         * @param isStructure      True if the the column applies to structure data types
         *
         * @param isPointer        True if the the column applies to pointer data types
         *****************************************************************************************/
        protected void addColumn(int index,
                                 String databaseName,
                                 String visibleName,
                                 String comment,
                                 InputType inputType,
                                 Boolean isRowValueUnique,
                                 Boolean isColumnRequired,
                                 Boolean isStructure,
                                 Boolean isPointer)
        {
            columnIndex.add(index);
            columnNamesDatabase.add(databaseName);
            columnNamesDatabaseQuoted.add(dbControl.getQuotedName(databaseName));
            columnNamesUser.add(visibleName);
            columnToolTip.add(comment);
            columnInputType.add(inputType);
            this.isRowValueUnique.add(isRowValueUnique);
            this.isColumnRequired.add(isColumnRequired);
            this.isStructureOk.add(isStructure);
            this.isPointerOk.add(isPointer);
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the
         * Structure table type
         *
         * @return true if this table type contains all of the protected columns of the Structure
         *         table type; i.e., this table type represents a structure table
         *****************************************************************************************/
        protected boolean isStructure()
        {
            return isTargetType(TYPE_STRUCTURE);
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the ENUM
         * table type
         *
         * @return true if this table type contains all of the protected columns of the ENUM table
         *         type; i.e., this table type represents a ENUM table
         *****************************************************************************************/
        protected boolean isENUM()
        {
            return isTargetType(TYPE_ENUM) && !isTargetType(TYPE_STRUCTURE);
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the
         * Structure table type in addition to a column with the Rate input type
         *
         * @return true if this table type contains all of the protected columns of the Structure
         *         table type in addition to a column with the Rate input type; i.e., this table
         *         type represents a telemetry structure table
         *****************************************************************************************/
        protected boolean isTelemetryStructure()
        {
            return isStructure() && getColumnIndicesByInputType(DefaultInputType.RATE).size() != 0;
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the
         * Command table type
         *
         * @return true if this table type contains all of the protected columns of the Command
         *         table type (but not those of a Structure table type); i.e., this table type
         *         represents a Command table
         *****************************************************************************************/
        protected boolean isCommand()
        {
            return isTargetType(TYPE_COMMAND) && !isTargetType(TYPE_STRUCTURE);
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the
         * specified table type
         *
         * @param typeName Table type name of the default table type with which to compare this
         *                 table's columns
         *
         * @return true if this table type contains all of the protected columns of the specified
         *         table type
         *****************************************************************************************/
        private boolean isTargetType(String typeName)
        {
            boolean isTargetType = true;

            // Step through each of the default columns
            for (DefaultColumn column : DefaultColumn.values())
            {
                // Check if this column belongs to the target table type, that it is a protected
                // column, and the column input type doesn't exist in this table type
                if (column.getTableType().equals(typeName)
                    && column.isProtected()
                    && getColumnIndicesByInputType(inputTypeHandler.getInputTypeByDefaultType(column.getInputType())).isEmpty())
                {
                    // Set the flag to indicate that this table type doesn't have all of the target
                    // type's columns and stop searching
                    isTargetType = false;
                    break;
                }
            }

            return isTargetType;
        }

        /******************************************************************************************
         * Get the row index in the structure table for the specified variable name
         *
         * @param tableData   List of table data
         *
         * @param columnValue Variable name
         *
         * @param columnIndex Column index of the variable name column
         *
         * @return Row index for the specified variable name; -1 if the variable name is not found
         *****************************************************************************************/
        protected int getRowIndexByColumnValue(List<Object[]> tableData, Object columnValue, int columnIndex)
        {
            int varRow = -1;

            // Step through each row in the table data
            for (int row = 0; row < tableData.size(); row++)
            {
                // Check if the variable name matches the name in this row
                if (columnValue.equals(tableData.get(row)[columnIndex]))
                {
                    // Store the row index and stop searching
                    varRow = row;
                    break;
                }
            }

            return varRow;
        }
    }

    /**********************************************************************************************
     * Table type handler class constructor
     *
     * @param ccddMain         Main class
     *
     * @param inputTypeHandler Reference to the input type handler
     *********************************************************************************************/
    CcddTableTypeHandler(CcddMain ccddMain, CcddInputTypeHandler inputTypeHandler)
    {
        this.ccddMain = ccddMain;
        this.inputTypeHandler = inputTypeHandler;
        fieldHandler = ccddMain.getFieldHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        typeDefinitions = new ArrayList<TypeDefinition>();

        // Create the table type from the definitions stored in the database
        createTypesFromDatabase();
    }

    /**********************************************************************************************
     * Table type handler class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddTableTypeHandler(CcddMain ccddMain)
    {
        this(ccddMain, ccddMain.getInputTypeHandler());
    }

    /**********************************************************************************************
     * Get the visible column index based on the database column index
     *
     * @param columnIndex Database column index
     *
     * @return Visible column index based on the database column index
     *********************************************************************************************/
    protected static int getVisibleColumnIndex(int columnIndex)
    {
        return columnIndex - NUM_HIDDEN_COLUMNS;
    }

    /**********************************************************************************************
     * Get the list of table type definitions
     *
     * @return List of table type definitions
     *********************************************************************************************/
    protected List<TypeDefinition> getTypeDefinitions()
    {
        return typeDefinitions;
    }

    /**********************************************************************************************
     * Get a copy of the list of table type definitions
     *
     * @return Copy of the list of table type definitions
     *********************************************************************************************/
    protected List<TypeDefinition> getTypeDefinitionsCopy()
    {
        List<TypeDefinition> typeDefinitionCopy = new ArrayList<TypeDefinition>();

        // Step through each existing table type definition
        for (TypeDefinition typeDefn : typeDefinitions)
        {
            // Create and add the copy of the type definition to the list
            typeDefinitionCopy.add(createTypeDefinition(typeDefn.getName(),
                                                        typeDefn.getDescription(),
                                                        typeDefn.getData()));
        }

        return typeDefinitionCopy;
    }

    /**********************************************************************************************
     * Set the list of table type definitions
     *
     * @param typeDefinitions List of table type definitions
     *********************************************************************************************/
    protected void setTypeDefinitions(List<TypeDefinition> typeDefinitions)
    {
        this.typeDefinitions = typeDefinitions;
    }

    /**********************************************************************************************
     * Create the table type from definitions stored in the database
     *********************************************************************************************/
    private void createTypesFromDatabase()
    {
        // Check if any type definitions already exist
        if (!typeDefinitions.isEmpty())
        {
            // Remove the existing definitions
            typeDefinitions.clear();
        }

        // Read the stored types from the database
        List<String[]> committedTypes = dbTable.retrieveInformationTable(InternalTable.TABLE_TYPES,
                                                                         false,
                                                                         ccddMain.getMainFrame());

        // Step through each type entry
        for (String[] typeData : committedTypes)
        {
            // Create a new type definition
            TypeDefinition typeDefn = getTypeDefinition(typeData[TableTypesColumn.TYPE_NAME.ordinal()].toString());

            // Check if the type is not defined
            if (typeDefn == null)
            {
                // Create the type and add it to the list
                typeDefn = new TypeDefinition(typeData[TableTypesColumn.TYPE_NAME.ordinal()].toString());
                typeDefinitions.add(typeDefn);
            }

            // Add the column names, tool tip, column unique, column required, and column input
            // type information to the type definition
            typeDefn.addColumn(Integer.parseInt(typeData[TableTypesColumn.INDEX.ordinal()].toString()),
                               typeData[TableTypesColumn.COLUMN_NAME_DB.ordinal()].toString(),
                               typeData[TableTypesColumn.COLUMN_NAME_VISIBLE.ordinal()].toString(),
                               typeData[TableTypesColumn.COLUMN_DESCRIPTION.ordinal()].toString(),
                               inputTypeHandler.getInputTypeByName(typeData[TableTypesColumn.INPUT_TYPE.ordinal()].toString()),
                               typeData[TableTypesColumn.ROW_VALUE_UNIQUE.ordinal()].equals("t") ? true : false,
                               typeData[TableTypesColumn.COLUMN_REQUIRED.ordinal()].equals("t") ? true : false,
                               typeData[TableTypesColumn.STRUCTURE_ALLOWED.ordinal()].equals("t") ? true : false,
                               typeData[TableTypesColumn.POINTER_ALLOWED.ordinal()].equals("t") ? true : false);
        }
    }

    /**********************************************************************************************
     * Create a new table type definition and add it to the list of type definitions. If the
     * definition exists then replace it with the new one
     *
     * @param typeName    Table type name
     *
     * @param description Table type description
     *
     * @param typeData    Array of table type data
     *
     * @return Reference to the type definition created
     *********************************************************************************************/
    protected TypeDefinition createReplaceTypeDefinition(String typeName,
                                                         String description,
                                                         Object[][] typeData)
    {
        // Get the reference to the type definition
        TypeDefinition typeDefn = getTypeDefinition(typeName);

        // Check if this type already exists
        if (typeDefn != null)
        {
            // Delete the type definition
            typeDefinitions.remove(typeDefn);
        }

        // Create the type definition
        typeDefn = createTypeDefinition(typeName, description, typeData);

        // Add it to the list of type definitions
        typeDefinitions.add(typeDefn);

        return typeDefn;
    }

    /**********************************************************************************************
     * Remove an existing table type (if it is found) Add a new table type
     *
     * @param eTypeName   The name of the existing table type. If it is found it will be removed
     *
     * @param nTypeName   The name of the new table type. If this is null the table will not be
     *                    created
     *
     * @param description The description for the new table type
     *
     * @param typeData    Array of table type data
     *
     * @return Reference to the type definition created
     *********************************************************************************************/
    protected TypeDefinition addRemoveTypeDefinition(String eTypeName,
                                                     String nTypeName,
                                                     String description,
                                                     Object[][] typeData)
    {

        // Remove the Type Definition
        removeTypeDefinition(eTypeName);

        return addTypeDefinition(nTypeName, description, typeData);
    }

    /**********************************************************************************************
     * Add a new table type
     *
     * @param nTypeName   The name of the new table type. If this is null the table will not be
     *                    created
     *
     * @param description The description for the new table type
     *
     * @param typeData    Array of table type data
     *
     * @return Reference to the type definition created
     *********************************************************************************************/
    protected TypeDefinition addTypeDefinition(String nTypeName,
                                               String description,
                                               Object[][] typeData)
    {
        TypeDefinition typeDefn = null;

        if (nTypeName != null)
        {
            // Create the type definition
            typeDefn = createTypeDefinition(nTypeName, description, typeData);

            // Add it to the list of type definitions
            typeDefinitions.add(typeDefn);
        }

        return typeDefn;
    }

    /**********************************************************************************************
     * Remove an existing table type (if it is found) Add a new table type
     *
     * @param eTypeName The name of the existing table type. If it is found it will be removed from
     *                  the list
     *********************************************************************************************/
    protected void removeTypeDefinition(String eTypeName)
    {
        // Get the reference to the type definition
        TypeDefinition typeDefn = getTypeDefinition(eTypeName);

        // Check if this type already exists
        if (typeDefn != null)
        {
            // Delete the type definition
            typeDefinitions.remove(typeDefn);
        }
        return;
    }

    /**********************************************************************************************
     * Create a new table type definition
     *
     * @param typeName    Table type name
     *
     * @param description Table type description
     *
     * @param typeData    Array of table type data
     *
     * @return Reference to the type definition created
     *********************************************************************************************/
    protected TypeDefinition createTypeDefinition(String typeName,
                                                  String description,
                                                  Object[][] typeData)
    {
        // Create a new type definition
        TypeDefinition typeDefn = new TypeDefinition(typeName);

        // Add the primary key and row index column definitions
        typeDefn.addColumn(DefaultColumn.PRIMARY_KEY.ordinal(),
                           DefaultColumn.PRIMARY_KEY.getDbName(),
                           DefaultColumn.PRIMARY_KEY.getName(),
                           description,
                           inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.PRIMARY_KEY.getInputType()),
                           DefaultColumn.PRIMARY_KEY.isRowValueUnique(),
                           DefaultColumn.PRIMARY_KEY.isInputRequired(),
                           DefaultColumn.PRIMARY_KEY.isStructureAllowed(),
                           DefaultColumn.PRIMARY_KEY.isPointerAllowed());
        typeDefn.addColumn(DefaultColumn.ROW_INDEX.ordinal(),
                           DefaultColumn.ROW_INDEX.getDbName(),
                           DefaultColumn.ROW_INDEX.getName(),
                           DefaultColumn.ROW_INDEX.getDescription(),
                           inputTypeHandler.getInputTypeByDefaultType(DefaultColumn.ROW_INDEX.getInputType()),
                           DefaultColumn.ROW_INDEX.isRowValueUnique(),
                           DefaultColumn.ROW_INDEX.isInputRequired(),
                           DefaultColumn.PRIMARY_KEY.isStructureAllowed(),
                           DefaultColumn.PRIMARY_KEY.isPointerAllowed());

        // Step through each row in the type definition data
        for (int row = 0; row < typeData.length; row++)
        {
            // Add the column names, description, input type, and flags to the type definition
            typeDefn.addColumn(row, (String) typeData[row][TableTypeEditorColumnInfo.NAME.ordinal()],
                               (String) typeData[row][TableTypeEditorColumnInfo.NAME.ordinal()],
                               (String) typeData[row][TableTypeEditorColumnInfo.DESCRIPTION.ordinal()],
                               inputTypeHandler.getInputTypeByName(typeData[row][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                               (Boolean) typeData[row][TableTypeEditorColumnInfo.UNIQUE.ordinal()],
                               (Boolean) typeData[row][TableTypeEditorColumnInfo.REQUIRED.ordinal()],
                               (Boolean) typeData[row][TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()],
                               (Boolean) typeData[row][TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()]);
        }

        // Convert the visible column names to their database equivalents
        typeDefn.setColumnNamesDatabase();

        return typeDefn;
    }

    /**********************************************************************************************
     * Get the table type definition for the specified table type
     *
     * @param typeName Table type name
     *
     * @return Table type definition based on the supplied type name; null if no match is found
     *********************************************************************************************/
    protected TypeDefinition getTypeDefinition(String typeName)
    {
        TypeDefinition typeDefinition = null;

        // Step through each definition
        for (TypeDefinition typeDefn : typeDefinitions)
        {
            // Check if the supplied type name matches the type definition name; ignoring case
            // sensitivity
            if (typeName.equalsIgnoreCase(typeDefn.getName()))
            {
                // Store the type definition and stop searching
                typeDefinition = typeDefn;
                break;
            }
        }

        return typeDefinition;
    }

    /**********************************************************************************************
     * Get an array of the name of the defined table types
     *
     * @return Array of names of the table types, sorted alphabetically; an empty list if no table
     *         types are defined
     *********************************************************************************************/
    protected String[] getTableTypeNames()
    {
        List<String> tableTypeNames = new ArrayList<String>();

        // Step through each definition
        for (TypeDefinition typeDefn : typeDefinitions)
        {
            // Add the type name to the list
            tableTypeNames.add(typeDefn.getName());
        }

        // Sort the list alphabetically
        Collections.sort(tableTypeNames);

        return tableTypeNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Convert the visible column name to its database equivalent by replacing all characters that
     * are invalid in a database column name with underscores. If the column belongs to a table
     * representing a structure the specific input types use predefined names in place of the
     * conversion name. If the column name matches a PostgreSQL reserved word then the name is
     * bounded by double quotes
     *
     * @param columnName    Column name (as seen by the user)
     *
     * @param inputTypeName Column input type name
     *
     * @param isStructure   True if the column belongs to a structure
     *
     * @return Database column name corresponding to the visible column name
     *********************************************************************************************/
    protected String convertVisibleToDatabase(String columnName, String inputTypeName, boolean isStructure)
    {
        String dbColumnName = null;

        // Check if the column belongs to a structure type table
        if (isStructure)
        {
            if (inputTypeName.equals(DefaultInputType.VARIABLE.getInputName()))
            {
                // Use the default database name for the variable name column
                dbColumnName = DefaultColumn.VARIABLE_NAME.getDbName();
            }
            else if (inputTypeName.equals(DefaultInputType.ARRAY_INDEX.getInputName()))
            {
                // Use the default database name for the variable name column
                dbColumnName = DefaultColumn.ARRAY_SIZE.getDbName();
            }
            else if (inputTypeName.equals(DefaultInputType.BIT_LENGTH.getInputName()))
            {
                // Use the default database name for the variable name column
                dbColumnName = DefaultColumn.BIT_LENGTH.getDbName();
            }
            else if (inputTypeName.equals(DefaultInputType.PRIM_AND_STRUCT.getInputName()))
            {
                // Use the default database name for the variable name column
                dbColumnName = DefaultColumn.DATA_TYPE.getDbName();
            }
            else
            {
                // Replace any characters that aren't allowed in a database column name with
                // underscores
                dbColumnName = columnName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
            }
        }
        // The column doesn't belong to a structure type table
        else
        {
            // Replace any characters that aren't allowed in a database column name with
            // underscores
            dbColumnName = columnName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        }

        return dbControl.getQuotedName(dbColumnName);
    }

    /**********************************************************************************************
     * Get the default column order string
     *
     * @param typeName Table type name
     *
     * @return String containing the column indices separated by colons; blank if the type in
     *         invalid
     *********************************************************************************************/
    protected String getDefaultColumnOrder(String typeName)
    {
        String columnOrder = "";

        // Get the definition for the table type supplied
        TypeDefinition typeDefinition = getTypeDefinition(typeName);

        // Check if the type is valid
        if (typeDefinition != null)
        {
            // Step through each column
            for (int index = 0; index < typeDefinition.getColumnCountDatabase(); index++)
            {
                columnOrder += index + ":";
            }

            // Remove the trailing colon
            columnOrder = CcddUtilities.removeTrailer(columnOrder, ":");
        }

        return columnOrder;
    }

    /**********************************************************************************************
     * Get an array of the type names and descriptions, sorted by type name
     *
     * @return Array of the type names and descriptions, sorted by type name
     *********************************************************************************************/
    protected String[][] getTypeInformation()
    {
        ArrayListMultiple typeInfo = new ArrayListMultiple();

        // Step through each table type definition
        for (TypeDefinition typeDefn : typeDefinitions)
        {
            // Add the table type name and description to the list
            typeInfo.add(new String[] {typeDefn.getName(), typeDefn.getDescription()});
        }

        // Sort the list based on the table type name
        typeInfo.sort(ArrayListMultipleSortType.STRING);

        return typeInfo.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the name of the first column in the specified table type's definition that matches the
     * specified input type
     *
     * @param typeName  Table type name
     *
     * @param inputType Column input type (InputType)
     *
     * @return Name (as seen by the user) of the first column in the specified table type's
     *         definition that matches the specified input type; null if the input type doesn't
     *         exist in the table type definition
     *********************************************************************************************/
    protected String getColumnNameByInputType(String typeName, InputType inputType)
    {
        String columnName = null;

        // Get the type definition for this table type
        TypeDefinition typeDefinition = getTypeDefinition(typeName);

        // Check if the table type definition exists
        if (typeDefinition != null)
        {
            // Get the first column name that has the specified input type
            columnName = typeDefinition.getColumnNameByInputType(inputType);
        }

        return columnName;
    }

    /**********************************************************************************************
     * Get the name of the first column in the specified table type's definition that matches the
     * specified default input type
     *
     * @param typeName  Table type name
     *
     * @param inputType Column input type (DefaultInputType)
     *
     * @return Name (as seen by the user) of the first column in the specified table type's
     *         definition that matches the specified default input type; null if the input type
     *         doesn't exist in the table type definition
     *********************************************************************************************/
    protected String getColumnNameByInputType(String typeName, DefaultInputType inputType)
    {
        return getColumnNameByInputType(typeName, inputTypeHandler.getInputTypeByDefaultType(inputType));
    }

    /**********************************************************************************************
     * Get the list of structure table types
     *
     * @return List of structure table types
     *********************************************************************************************/
    protected String[] getStructureTableTypes()
    {
        List<String> structureTypes = new ArrayList<String>();

        // Step through each table type definition
        for (TypeDefinition typeDefn : getTypeDefinitions())
        {
            // Check if the type represents a structure
            if (typeDefn.isStructure())
            {
                // Add the table type name to the list
                structureTypes.add(typeDefn.getName());
            }
        }

        return structureTypes.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the list of unique structure table enumeration column names
     *
     * @param useDbName True to use the database column name; false to use the user column name
     *
     * @return List of unique structure table enumeration column names; an empty list if no
     *         enumeration columns exist
     *********************************************************************************************/
    protected List<String> getStructEnumColNames(boolean useDbName)
    {
        List<String> enumColumns = new ArrayList<String>();

        // Step through each table type definition
        for (TypeDefinition typeDefn : getTypeDefinitions())
        {
            // Check if the type represents a structure
            if (typeDefn.isStructure())
            {
                // Step through each of the table's enumeration columns
                for (int enumIndex : typeDefn.getColumnIndicesByInputTypeFormat(inputTypeHandler.getInputTypeByName(DefaultInputType.ENUMERATION.getInputName())
                                                                                                .getInputFormat()))
                {
                    // Get the name of the column
                    String name = useDbName ? typeDefn.getColumnNamesDatabase()[enumIndex]
                                            : typeDefn.getColumnNamesUser()[enumIndex];

                    // Check if the name hasn't already been added
                    if (!enumColumns.contains(name))
                    {
                        // Add the enumeration column name to the list
                        enumColumns.add(name);
                    }
                }
            }
        }

        return enumColumns;
    }

    /**********************************************************************************************
     * Update the input type for each table type definition column following a change to the input
     * type definitions
     *
     * @param inputTypeNames List of the input type names, before and after the changes; null if
     *                       none of the input type names changed
     *********************************************************************************************/
    protected void updateInputTypes(List<String[]> inputTypeNames)
    {
        // Step through each table type definition
        for (TypeDefinition typeDefn : getTypeDefinitions())
        {
            // Step through each column's input type
            for (int index = 0; index < typeDefn.getInputTypesList().size(); index++)
            {
                // Get the column's input type name before the change
                String inputTypeName = typeDefn.getInputTypesList().get(index).getInputName();

                // Check if a list of input type names is provided. If not, assume the names are
                // unchanged
                if (inputTypeNames != null)
                {
                    // Step through each input type that changed
                    for (String[] oldAndNewName : inputTypeNames)
                    {
                        // Check if the input type name changed
                        if (oldAndNewName[0].equals(inputTypeName))
                        {
                            // Set the column's input type name to the (possibly) new input type
                            // name and stop searching
                            inputTypeName = oldAndNewName[1];
                            break;
                        }
                    }
                }

                // Set the column's input type based on the input type name
                typeDefn.getInputTypesList().set(index, inputTypeHandler.getInputTypeByName(inputTypeName));
            }
        }
    }

    /**********************************************************************************************
     * Check if the specified table types are new or match an existing one. If new then add the
     * table type. If the table type name matches then compare the type definitions to ensure the
     * two are the same (ignoring the column descriptions)
     *
     * @param tableTypeDefinitions List of table type definitions
     *
     * @throws CCDDException If an imported data field name will cause an existing table's field to
     *                       be renamed and the user elects to cancel the update
     *********************************************************************************************/
    protected void updateTableTypes(List<TableTypeDefinition> tableTypeDefinitions) throws CCDDException
    {
        boolean isNewStruct = false;

        // Step through each table type definition
        for (TableTypeDefinition tableTypeDefn : tableTypeDefinitions)
        {
            // Determine if the table type is new or matches an existing one with the same name
            TableTypeUpdate typeUpdate = updateTableTypes(tableTypeDefn);

            if (typeUpdate == TableTypeUpdate.MISMATCH)
            {
                buildAndExecuteUpdates(tableTypeDefn);
            }

            // Check if the table type is new and represents a structure
            if (typeUpdate == TableTypeUpdate.NEW && getTypeDefinition(tableTypeDefn.getTypeName()).isStructure())
            {
                // Set the flag to indicate a structure table type was added
                isNewStruct = true;
            }
        }

        // Clear the table type definitions since they have been incorporated
        tableTypeDefinitions.clear();

        // Check if a new struct was added
        if (isNewStruct)
        {
            // Update the database functions that collect structure table members and
            // structure-defining column data
            dbControl.createStructureColumnFunctions();
        }

        // Check if the number of rate columns changed due to the type update
        if (ccddMain.getRateParameterHandler().setRateInformation())
        {
            // Store the rate parameters in the project database
            dbTable.storeRateParameters(ccddMain.getMainFrame());
        }

        // Store the data field table with the additional fields
        dbTable.storeInformationTable(InternalTable.FIELDS,
                                      fieldHandler.getFieldDefnsFromInfo(),
                                      null,
                                      ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Check if specified table type is new or matches an existing one. If new then add the table
     * type. If the table type name matches then compare the type definitions to see if they differ
     *
     * @param tableTypeDefn Table type definition
     *
     * @return TableTypeUpdate.NEW if the table type is new, TableTypeUpdate.MATCH if the table
     *         type matches an existing one, or TableTypeUpdate.MISMATCH if the table type name
     *         matches an existing one but the type definition differs
     *
     * @throws CCDDException If an imported data field name will cause an existing table's field to
     *                       be renamed and the user elects to cancel the update
     *********************************************************************************************/
    private TableTypeUpdate updateTableTypes(TableTypeDefinition tableTypeDefn) throws CCDDException
    {
        TableTypeUpdate typeUpdate = TableTypeUpdate.MATCH;

        // Get the type definition based on the type name
        TypeDefinition typeDefn = getTypeDefinition(tableTypeDefn.getTypeName());

        // Check if the table type doesn't already exist
        if (typeDefn == null)
        {
            // Set the flag indicating the table type is new
            typeUpdate = TableTypeUpdate.NEW;

            // Add the new table type
            createReplaceTypeDefinition(tableTypeDefn.getTypeName(),
                                        tableTypeDefn.getDescription(),
                                        tableTypeDefn.getColumns().toArray(new Object[0][0]));

            // Check if the table type editor is open
            if (ccddMain.getTableTypeEditor() != null && ccddMain.getTableTypeEditor().isShowing())
            {
                // Add the new table type tab to the editor
                ccddMain.getTableTypeEditor().addTypePanes(new String[] {tableTypeDefn.getTypeName()});
            }

            // Update the fieldHandler
            fieldHandler.replaceFieldInformationByOwner("Type:" + tableTypeDefn.getTypeName(),
                                                        fieldHandler.getFieldInformationFromDefinitions(tableTypeDefn.getDataFields()));
        }
        else
        {
            // A table type with this name already exists Get a list of all of the table type names
            // and descriptions
            String[][] tableTypeNamesAndDescriptions = dbTable.queryTableTypeNamesAndDescriptions(ccddMain.getMainFrame());

            // Check if the description differs
            for (String[] entry : tableTypeNamesAndDescriptions)
            {
                if (entry[0].equals(tableTypeDefn.getTypeName()))
                {
                    if (!entry[1].equals(tableTypeDefn.getDescription()))
                    {
                        // Set the flag indicating a mismatch exists and stop searching
                        typeUpdate = TableTypeUpdate.MISMATCH;
                        break;
                    }
                }
            }

            // Check each row of the table to see if any of the columns differ
            if (typeUpdate != TableTypeUpdate.MISMATCH)
            {
                // Add the table type with a different name and get a reference to it
                TypeDefinition altTypeDefn = createReplaceTypeDefinition(tableTypeDefn.getTypeName() + "_TEMP",
                                                                         tableTypeDefn.getDescription(),
                                                                         tableTypeDefn.getColumns().toArray(new Object[0][0]));

                // See if the same number of columns exists, but subtract 2 from typeDefn due to
                // the key and index being included
                if (tableTypeDefn.getColumns().size() == (typeDefn.getColumnNamesUser().length - 2))
                {
                    // Step through each column name
                    for (String columnName : typeDefn.getColumnNamesUser())
                    {
                        // Get the index for the column name in the alternate type definition
                        int altIndex = altTypeDefn.getColumnIndexByUserName(columnName);

                        // Check if the alternate definition doesn't have a column with this name
                        if (altIndex == -1)
                        {
                            // Set the flag indicating a mismatch exists and stop searching
                            typeUpdate = TableTypeUpdate.MISMATCH;
                            break;
                        }

                        // Get the index for the column name in the existing type definition
                        int index = typeDefn.getColumnIndexByUserName(columnName);

                        // Check if the column definitions differ
                        if (!typeDefn.getInputTypes()[index].getInputName().equals(altTypeDefn.getInputTypes()[altIndex].getInputName())
                            || !typeDefn.isRowValueUnique()[index].equals(altTypeDefn.isRowValueUnique()[altIndex])
                            || !typeDefn.isRequired()[index].equals(altTypeDefn.isRequired()[altIndex])
                            || !typeDefn.isStructureAllowed()[index].equals(altTypeDefn.isStructureAllowed()[altIndex])
                            || !typeDefn.isPointerAllowed()[index].equals(altTypeDefn.isPointerAllowed()[altIndex]))
                        {
                            // Set the flag indicating a mismatch exists and stop searching
                            typeUpdate = TableTypeUpdate.MISMATCH;
                            break;
                        }
                    }
                }
                else
                {
                    // Set the flag indicating a mismatch exists and stop searching
                    typeUpdate = TableTypeUpdate.MISMATCH;
                }

                // Delete the added type definition
                getTypeDefinitions().remove(altTypeDefn);
            }

            // Check that all of the fields match
            if (typeUpdate == TableTypeUpdate.MATCH)
            {
                // Get the existing data fields
                List<FieldInformation> currentDataFields = fieldHandler
                        .getFieldInformationByOwner("Type:" + tableTypeDefn.getTypeName());

                // Check to see if the same number of data fields exist for both the new and
                // existing table type definition
                if ((currentDataFields == null) || (currentDataFields.size() != tableTypeDefn.getDataFields().size()))
                {
                    // Set the flag indicating a mismatch exists and stop searching
                    typeUpdate = TableTypeUpdate.MISMATCH;
                }
                else
                {
                    // Step through each table type data field
                    for (int i = 0; i < tableTypeDefn.getDataFields().size(); i++)
                    {
                        String[] dataField = tableTypeDefn.getDataFields().get(i);

                        // Check if the existing field's input type, required state, applicability,
                        // or value don't match (the description and size are allowed to differ)
                        if (!dataField[FieldsColumn.FIELD_TYPE.ordinal()].equals(currentDataFields.get(i).getInputType().getInputName())
                            || !dataField[FieldsColumn.FIELD_REQUIRED.ordinal()].equalsIgnoreCase(Boolean.toString(currentDataFields.get(i).isRequired()))
                            || !dataField[FieldsColumn.FIELD_APPLICABILITY.ordinal()].equals(currentDataFields.get(i).getApplicabilityType().getApplicabilityName())
                            || !dataField[FieldsColumn.FIELD_VALUE.ordinal()].equals(currentDataFields.get(i).getValue())
                            || !dataField[FieldsColumn.FIELD_DESC.ordinal()].equals(currentDataFields.get(i).getDescription())
                            || Integer.parseInt(dataField[FieldsColumn.FIELD_SIZE.ordinal()]) != currentDataFields.get(i).getSize())
                        {
                            // Set the flag indicating a mismatch exists and stop searching
                            typeUpdate = TableTypeUpdate.MISMATCH;
                            break;
                        }
                    }
                }
            }
        }

        return typeUpdate;
    }

    /**********************************************************************************************
     * Compare the current table type data to the committed table type data and create lists of the
     * changed values necessary to update the table definitions table in the database to match the
     * current values
     *
     * @param newTableTypeDefn Table type definition
     *********************************************************************************************/
    private void buildAndExecuteUpdates(TableTypeDefinition newTableTypeDefn)
    {
        // Local variables for tracking changes
        List<String[]> typeAdditions = new ArrayList<String[]>();
        List<String[]> typeModifications = new ArrayList<String[]>();
        List<String[]> typeDeletions = new ArrayList<String[]>();

        // Get the type definition based on the type name
        TypeDefinition typeDefn = getTypeDefinition(newTableTypeDefn.getTypeName());

        // Create a 2d Object array for the new type data
        List<Object[]> typeDataList = newTableTypeDefn.getColumns();
        Object[][] newTypeData = new Object[typeDataList.size()][];
        newTypeData = typeDataList.toArray(newTypeData);

        // Create/replace the type definition. The description is prepended with a '0' is the table
        // type doesn't represent a command argument structure, and a '1' if it does
        createReplaceTypeDefinition(newTableTypeDefn.getTypeName(),
                                    newTableTypeDefn.getDescription(),
                                    newTypeData);

        // Create a 2d Object array for the old type data
        String[][] tempArray = dbTable.queryTableTypeDataList(newTableTypeDefn.getTypeName(),
                                                              ccddMain.getMainFrame());
        Object[][] oldTypeData = new Object[tempArray.length][tempArray[0].length];

        for (int i = 0; i < tempArray.length; i++)
        {
            Object[] row = new Object[tempArray[0].length];

            for (int x = 0; x < tempArray[0].length; x++)
            {
                if (newTypeData[0][x] instanceof Integer)
                {
                    row[x] = Integer.parseInt(tempArray[i][x]);
                }
                else if (newTypeData[0][x] instanceof Boolean)
                {
                    if (tempArray[i][x].equals("t"))
                    {
                        row[x] = true;
                    }
                    else
                    {
                        row[x] = false;
                    }
                }
                else
                {
                    row[x] = tempArray[i][x];
                }
            }

            oldTypeData[i] = row;
        }

        // Initialize the column order change status
        boolean columnOrderChange = false;

        // Create an empty row of data for comparison purposes
        Object[] emptyRow = FieldEditorColumnInfo.getEmptyRow();

        // Create storage for flags that indicate if a row has been modified
        boolean[] rowModified = new boolean[oldTypeData.length];

        // Step through each row of the current data
        for (int tblRow = 0; tblRow < newTypeData.length; tblRow++)
        {
            boolean matchFound = false;

            // Get the current column name
            String currColumnName = newTypeData[tblRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

            // Step through each row of the committed data
            for (int comRow = 0; comRow < oldTypeData.length; comRow++)
            {
                // Get the previous column name
                String prevColumnName = oldTypeData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString();

                // Check if the committed row hasn't already been matched and if the current and
                // committed column indices are the same
                if (!rowModified[comRow]
                    && newTypeData[tblRow][TableTypeEditorColumnInfo.INDEX.ordinal()].equals(oldTypeData[comRow][TableTypeEditorColumnInfo.INDEX.ordinal()]))
                {
                    // Set the flag indicating this row has a match
                    matchFound = true;

                    // Copy the current row's index into the empty comparison row so that the
                    // otherwise blank index doesn't register as a difference when comparing the
                    // rows below
                    emptyRow[TableTypeEditorColumnInfo.INDEX.ordinal()] = newTypeData[tblRow][TableTypeEditorColumnInfo.INDEX.ordinal()];

                    // Check if the row is not now empty (if empty then the change is processed as
                    // a row deletion instead of a modification)
                    if (!Arrays.equals(newTypeData[tblRow], emptyRow))
                    {
                        // Set the flag indicating this row has a modification
                        rowModified[comRow] = true;

                        // Check if the previous and current column definition row is different
                        if (tblRow != comRow)
                        {
                            // Set the flag indicating the column order changed
                            columnOrderChange = true;
                        }

                        // Get the original and current input type
                        String oldInputType = oldTypeData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();
                        String newInputType = newTypeData[tblRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString();

                        // Check if the column name changed or if the input type changed to/from a
                        // rate
                        if (!prevColumnName.equals(currColumnName)
                            || ((newInputType.equals(DefaultInputType.RATE.getInputName())
                                 || oldInputType.equals(DefaultInputType.RATE.getInputName()))
                                && !newInputType.equals(oldInputType)))
                        {
                            // The column name is changed. Add the old and new column names and
                            // input types to the list
                            typeModifications.add(new String[] {prevColumnName,
                                                                currColumnName,
                                                                oldInputType,
                                                                newInputType});
                        }

                        // Stop searching since a match exists
                        break;
                    }
                }
            }

            // Check if no match was made with the committed data for the current table row
            if (!matchFound)
            {
                // The column definition is being added; add the column name and input type to the
                // list
                typeAdditions.add(new String[] {currColumnName,
                                                newTypeData[tblRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()});
            }
        }

        // Step through each row of the committed data
        for (int comRow = 0; comRow < oldTypeData.length; comRow++)
        {
            // Check if no matching row was found with the current data
            if (!rowModified[comRow])
            {
                // The column definition has been deleted; add the column name and input type to
                // the list
                typeDeletions.add(new String[] {oldTypeData[comRow][TableTypeEditorColumnInfo.NAME.ordinal()].toString(),
                                                oldTypeData[comRow][TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].toString()});
            }
        }

        // ////////////////////////////////////////////////////////////////////////////////////////
        // Build the changes to the table type's data field definitions. To keep things simple we
        // delete all existing fields and replace them with the new ones.
        // ////////////////////////////////////////////////////////////////////////////////////////

        // Lists of table modifications that will be used to update the database
        List<TableModification> fieldAdditions = new ArrayList<TableModification>();
        List<TableModification> fieldDeletions = new ArrayList<TableModification>();
        List<TableModification> fieldModifications = new ArrayList<TableModification>();

        // Get the existing data fields information and convert it to a list of string arrays for
        // easier use
        List<FieldInformation> oldDataFieldInformation = fieldHandler
                .getFieldInformationByOwner("Type:" + newTableTypeDefn.getTypeName());

        // Get the new data fields
        List<String[]> newDataFields = newTableTypeDefn.getDataFields();

        // Convert the new data fields information into a list of FieldInformation objects that can
        // be passed to the modifyTable() function
        List<FieldInformation> newDataFieldInformation = new ArrayList<FieldInformation>();

        for (int i = 0; i < newDataFields.size(); i++)
        {
            // input_type offset by 1 to account for owner name
            InputType inputType = new InputType(newDataFields.get(i)[FieldEditorColumnInfo.INPUT_TYPE.ordinal() + 1],
                                                "", "", "", null, false);
            ApplicabilityType appType = ApplicabilityType.ALL;

            if (newDataFields.get(i)[6].equals(ApplicabilityType.ROOT_ONLY.getApplicabilityName()))
            {
                appType = ApplicabilityType.ROOT_ONLY;
            }
            else if (newDataFields.get(i)[6].equals(ApplicabilityType.CHILD_ONLY.getApplicabilityName()))
            {
                appType = ApplicabilityType.CHILD_ONLY;
            }

            // All FieldColumns offset by 1 to account for owner name which is the first index of
            // each row of newDataFields
            FieldInformation currentFieldInformation = new FieldInformation(newDataFields.get(i)[0],
                                                                            newDataFields.get(i)[FieldEditorColumnInfo.NAME.ordinal() + 1],
                                                                            newDataFields.get(i)[FieldEditorColumnInfo.DESCRIPTION.ordinal() + 1],
                                                                            inputType,
                                                                            Integer.parseInt(newDataFields.get(i)[FieldEditorColumnInfo.CHAR_SIZE.ordinal() + 1]),
                                                                            Boolean.parseBoolean(newDataFields.get(i)[FieldEditorColumnInfo.REQUIRED.ordinal() + 1]),
                                                                            appType,
                                                                            newDataFields.get(i)[FieldEditorColumnInfo.VALUE.ordinal() + 1],
                                                                            Boolean.parseBoolean(newDataFields.get(i)[FieldEditorColumnInfo.INHERITED .ordinal() + 1]),
                                                                            null,
                                                                            -1);

            newDataFieldInformation.add(currentFieldInformation);
        }

        Object[][] oldFieldData = CcddFieldHandler.getFieldEditorDefinition(oldDataFieldInformation);
        Object[][] newFieldData = CcddFieldHandler.getFieldEditorDefinition(newDataFieldInformation);
        boolean[] oldDataProcessed = new boolean[oldFieldData.length];
        boolean fieldProcessed = false;

        // Step through all of the new data fields
        for (int i = 0; i < newFieldData.length; i++)
        {
            // Step though all of the old data fields
            for (int x = 0; x < oldFieldData.length; x++)
            {
                // See if the new data field name matches any of the old data field names
                if (newFieldData[i][0].toString().equals(oldFieldData[x][0].toString()))
                {
                    if (!Arrays.equals(newFieldData[i], oldFieldData[x]))
                    {
                        fieldModifications.add(new TableModification(newFieldData[i], oldFieldData[x]));
                    }
                    oldDataProcessed[x] = true;
                    fieldProcessed = true;
                    break;
                }
            }

            if (fieldProcessed == false)
            {
                // if we reach this point no match was found and this is an addition
                fieldAdditions.add(new TableModification(newFieldData[i], null));
            }

            fieldProcessed = false;
        }

        // All of the old field data that was matched to a row in the new field data array has an
        // index within oldDataMatched set to true. If the given index is not true then the old
        // field data no longer exists and should be deleted
        for (int i = 0; i < oldFieldData.length; i++)
        {
            if (oldDataProcessed[i] != true)
            {
                // The field definition has been deleted; add the field definition to the list
                fieldDeletions.add(new TableModification(null, oldFieldData[i]));
            }
        }

        // Update the table types within the database
        dbTable.modifyTableType(newTableTypeDefn.getTypeName(),
                                newDataFieldInformation,
                                OverwriteFieldValueType.NONE,
                                typeAdditions,
                                typeModifications,
                                typeDeletions,
                                columnOrderChange,
                                typeDefn,
                                newDataFields,
                                null,
                                null);

        // Update the fieldHandler
        fieldHandler.replaceFieldInformationByOwner("Type:" + newTableTypeDefn.getTypeName(), newDataFieldInformation);
    }
}
