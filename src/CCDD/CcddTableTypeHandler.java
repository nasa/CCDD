/**
 * CFS Command and Data Dictionary table type handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.COL_ARGUMENT;
import static CCDD.CcddConstants.COL_ARRAY_SIZE;
import static CCDD.CcddConstants.COL_BIT_LENGTH;
import static CCDD.CcddConstants.COL_DATA_TYPE;
import static CCDD.CcddConstants.COL_DESCRIPTION;
import static CCDD.CcddConstants.COL_ENUMERATION;
import static CCDD.CcddConstants.COL_MAXIMUM;
import static CCDD.CcddConstants.COL_MINIMUM;
import static CCDD.CcddConstants.COL_UNITS;
import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_COMMAND_V2;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ArrayListMultipleSortType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
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

    // Flag indicating that a data field was created for a table type
    private boolean isNewField;

    // Command argument column definitions
    protected final static Object[][] commandArgumentColumns = new Object[][] {{COL_ARGUMENT + " ### Name",
                                                                                "Command argument ### name",
                                                                                DefaultInputType.ARGUMENT_NAME},
                                                                               {COL_ARGUMENT + " ### " + COL_DESCRIPTION,
                                                                                "Command argument ### description",
                                                                                DefaultInputType.DESCRIPTION},
                                                                               {COL_ARGUMENT + " ### " + COL_UNITS,
                                                                                "Command argument ### units",
                                                                                DefaultInputType.UNITS},
                                                                               {COL_ARGUMENT + " ### " + COL_DATA_TYPE,
                                                                                "Command argument ### data type",
                                                                                DefaultInputType.PRIM_AND_STRUCT},
                                                                               {COL_ARGUMENT + " ### " + COL_ARRAY_SIZE,
                                                                                "Command argument ### array size",
                                                                                DefaultInputType.ARRAY_INDEX},
                                                                               {COL_ARGUMENT + " ### " + COL_BIT_LENGTH,
                                                                                "Command argument ### bit length",
                                                                                DefaultInputType.BIT_LENGTH},
                                                                               {COL_ARGUMENT + " ### " + COL_ENUMERATION,
                                                                                "Command argument ### enumeration",
                                                                                DefaultInputType.ENUMERATION},
                                                                               {COL_ARGUMENT + " ### " + COL_MINIMUM,
                                                                                "Command argument ### minimum value",
                                                                                DefaultInputType.MINIMUM},
                                                                               {COL_ARGUMENT + " ### " + COL_MAXIMUM,
                                                                                "Command argument ### maximum value",
                                                                                DefaultInputType.MAXIMUM}};

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
         * @param tableType
         *            table type name
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
         * @param typeName
         *            table type name
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
         * @return table type description
         *****************************************************************************************/
        protected String getDescription()
        {
            return columnToolTip.get(0);
        }

        /******************************************************************************************
         * Get the index of the first column having the specified input type
         *
         * @param inputType
         *            column input type (InputType)
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
         * @param inputType
         *            column input type (DefaultInputType)
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
         * @param inputType
         *            column input type (InputType)
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
         * @param inputType
         *            column input type (DefaultInputType)
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
         * @param inputType
         *            column input type (InputType)
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
         * @param inputType
         *            column input type (DefaultInputType)
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
         * @param inputType
         *            column input type (InputType)
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
         * @param inputType
         *            column input type (DefaultInputType)
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
         * @param inputFormat
         *            column input type format (InputTypeFormat)
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
         * @param inputFormat
         *            column input type format (InputTypeFormat)
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
         * @param inputFormat
         *            column input type format (InputTypeFormat)
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
         * @param inputFormat
         *            column input type format (InputTypeFormat)
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
         * @param columnName
         *            column name (as seen by the user)
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
         * @param columnName
         *            column name (as seen by the user)
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
         * @param dbColumnName
         *            column name (as used by the database)
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
         * @param index
         *            column index
         *
         * @param databaseName
         *            name of the column as used in the database
         *
         * @param visibleName
         *            name of the column as seen by the user; e.g., as table editor column headers
         *
         * @param comment
         *            column description used as the table editor column header tool tip text
         *
         * @param inputType
         *            column input type (InputType)
         *
         * @param isRowValueUnique
         *            true if the each row value in the column must have a unique value
         *
         * @param isColumnRequired
         *            true if the column must have a value when the type is committed to the
         *            database
         *
         * @param isStructure
         *            true if the the column applies to structure data types
         *
         * @param isPointer
         *            true if the the column applies to pointer data types
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
         * CCDDv2 Command table type
         *
         * @return true if this table type contains all of the protected columns of the CCDDv2 
         *         Command table type (but not those of a Structure table type); i.e., this 
         *         table type represents a CCDDv2 Command table
         *****************************************************************************************/
        protected boolean isV2Command()
        {
            return isTargetType(TYPE_COMMAND_V2) && !isTargetType(TYPE_STRUCTURE);
        }

        /******************************************************************************************
         * Determine if this table type contains all of the default protected columns of the
         * specified table type
         *
         * @param typeName
         *            table type name of the default table type with which to compare this table's
         *            columns
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
         * @param tableData
         *            array of table data
         *
         * @param columnValue
         *            variable name
         *
         * @param columnIndex
         *            column index of the variable name column
         *
         * @return Row index for the specified variable name; -1 if the variable name is not found
         *****************************************************************************************/
        protected int getRowIndexByColumnValue(Object[][] tableData,
                                               Object columnValue,
                                               int columnIndex)
        {
            int varRow = -1;

            // Step through each row in the table data
            for (int row = 0; row < tableData.length; row++)
            {
                // Check if the variable name matches the name in this row
                if (columnValue.equals(tableData[row][columnIndex]))
                {
                    // Store the row index and stop searching
                    varRow = row;
                    break;
                }
            }

            return varRow;
        }

        /******************************************************************************************
         * Get the list of groupings of associated command argument columns
         *
         * @param useViewIndex
         *            true to adjust the column indices to view coordinates; false to keep the
         *            coordinates in model coordinates
         *
         * @return List of groupings of associated command argument columns; empty list if no
         *         command arguments exist
         *****************************************************************************************/
        protected List<AssociatedColumns> getAssociatedCommandArgumentColumns(boolean useViewIndex)
        {
            List<AssociatedColumns> associatedColumns = new ArrayList<AssociatedColumns>();

            // Initialize the starting command argument name, data type, array size, bit length,
            // enumeration, minimum, maximum, and other columns
            int nameColumn = -1;
            int dataTypeColumn = -1;
            int arrayColumn = -1;
            int bitColumn = -1;
            int enumColumn = -1;
            int minColumn = -1;
            int maxColumn = -1;
            int descColumn = -1;
            int unitsColumn = -1;
            List<Integer> otherColumn = null;

            // Get the column input types
            InputType[] inputTypes = getInputTypes();

            // Step through each column defined for this table's type
            for (int index = 0; index < getColumnCountDatabase(); index++)
            {
                // Check if the column expects a command argument name
                if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ARGUMENT_NAME)))
                {
                    // Save the name column index and initialize the associated column indices
                    nameColumn = index;
                    dataTypeColumn = -1;
                    arrayColumn = -1;
                    bitColumn = -1;
                    enumColumn = -1;
                    minColumn = -1;
                    maxColumn = -1;
                    descColumn = -1;
                    unitsColumn = -1;
                    otherColumn = new ArrayList<Integer>();

                    // Step through the remaining columns defined for this table's type
                    for (index++; index < getColumnCountDatabase(); index++)
                    {
                        // Check if the column expects a command argument name
                        if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ARGUMENT_NAME)))
                        {
                            // Adjust the loop index and stop searching since this is the beginning
                            // of the next argument's columns
                            index--;
                            break;
                        }

                        // Check that this isn't the command name or command code column (these are
                        // never part of an argument grouping)
                        if (!inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.COMMAND_NAME))
                            && !inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.COMMAND_CODE)))
                        {
                            // Check that this is a data type column
                            if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.PRIMITIVE))
                                || inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.PRIM_AND_STRUCT)))
                            {
                                // Save the data type column index
                                dataTypeColumn = index;
                            }
                            // Check that this is an array size column
                            else if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ARRAY_INDEX)))
                            {
                                // Save the array size column index
                                arrayColumn = index;
                            }
                            // Check that this is a bit length column
                            else if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.BIT_LENGTH)))
                            {
                                // Save the bit length column index
                                bitColumn = index;
                            }
                            // Check that this is an enumeration column
                            else if (inputTypes[index].getInputFormat()
                                                      .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.ENUMERATION).getInputFormat()))
                            {
                                // Save the enumeration column index
                                enumColumn = index;
                            }
                            // Check that this is a minimum column
                            else if (inputTypes[index].getInputFormat()
                                                      .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MINIMUM).getInputFormat()))
                            {
                                // Save the minimum column index
                                minColumn = index;
                            }
                            // Check that this is a maximum column
                            else if (inputTypes[index].getInputFormat()
                                                      .equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MAXIMUM).getInputFormat()))
                            {
                                // Save the maximum column index
                                maxColumn = index;
                            }
                            // Check that this is a description column
                            else if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.DESCRIPTION)))
                            {
                                // Save the description column index
                                descColumn = index;
                            }
                            // Check that this is a units column
                            else if (inputTypes[index].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.UNITS)))
                            {
                                // Save the units column index
                                unitsColumn = index;
                            }
                            // Not one of the recognized column input types, so treat as an 'other'
                            // column
                            else
                            {
                                // Add the column to the list of other columns associated with this
                                // command argument
                                otherColumn.add(index);
                            }
                        }
                    }

                    // Add the name, data type, array size, bit length, enumeration, minimum,
                    // maximum, and associated columns column index group to the list
                    associatedColumns.add(new AssociatedColumns(useViewIndex,
                                                                nameColumn,
                                                                dataTypeColumn,
                                                                arrayColumn,
                                                                bitColumn,
                                                                enumColumn,
                                                                minColumn,
                                                                maxColumn,
                                                                descColumn,
                                                                unitsColumn,
                                                                otherColumn));
                }
            }

            return associatedColumns;
        }

        /**********************************************************************************************
         * Add a set of default command argument columns to the table type definition using the
         * specified argument index to determine the column names
         *
         * @param argumentIndex
         *            argument index for the argument column names
         *********************************************************************************************/
        protected void addCommandArgumentColumns(int argumentIndex)
        {
            // Get the current number of columns defined for the table type. The new columns are
            // appended to the existing ones
            int columnIndex = getColumnCountDatabase();

            // Step through each command argument column to add
            for (Object[] cmdArgCol : commandArgumentColumns)
            {
                // Update the argument name with the argument index
                String argName = cmdArgCol[0].toString().replaceFirst("###",
                                                                      String.valueOf(argumentIndex));

                // Add the command argument column. The argument description is updated with the
                // argument index
                addColumn(columnIndex,
                          convertVisibleToDatabase(argName,
                                                   ((DefaultInputType) cmdArgCol[2]).getInputName(),
                                                   false),
                          argName,
                          cmdArgCol[1].toString().replaceFirst("###",
                                                               String.valueOf(argumentIndex)),
                          inputTypeHandler.getInputTypeByDefaultType((DefaultInputType) cmdArgCol[2]),
                          false,
                          false,
                          false,
                          false);

                columnIndex++;
            }
        }
    }

    /**********************************************************************************************
     * Table type handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param inputTypeHandler
     *            reference to the input type handler
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
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddTableTypeHandler(CcddMain ccddMain)
    {
        this(ccddMain, ccddMain.getInputTypeHandler());
    }

    /**********************************************************************************************
     * Get the visible column index based on the database column index
     *
     * @param columnIndex
     *            database column index
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
     * @param typeDefinitions
     *            list of table type definitions
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
            if (!typeData[0].equals(TYPE_COMMAND_V2)) {
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
    }

    /**********************************************************************************************
     * Create a new table type definition and add it to the list of type definitions. If the
     * definition exists then replace it with the new one
     *
     * @param typeName
     *            table type name
     *
     * @param description
     *            table type description
     *
     * @param typeData
     *            array of table type data
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
     * Create a new table type definition
     *
     * @param typeName
     *            table type name
     *
     * @param description
     *            table type description
     *
     * @param typeData
     *            array of table type data
     *
     * @return Reference to the type definition created
     *********************************************************************************************/
    private TypeDefinition createTypeDefinition(String typeName,
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
            typeDefn.addColumn(row,
                               (String) typeData[row][TableTypeEditorColumnInfo.NAME.ordinal()],
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
     * @param typeName
     *            table type name
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
     * @param columnName
     *            column name (as seen by the user)
     *
     * @param inputTypeName
     *            column input type name
     *
     * @param isStructure
     *            true if the column belongs to a structure
     *
     * @return Database column name corresponding to the visible column name
     *********************************************************************************************/
    protected String convertVisibleToDatabase(String columnName,
                                              String inputTypeName,
                                              boolean isStructure)
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
                // Replace any characters that aren't allowed in a database column name
                // with underscores
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
     * @param typeName
     *            table type name
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
     * @param typeName
     *            table type name
     *
     * @param inputType
     *            column input type (InputType)
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
     * @param typeName
     *            table type name
     *
     * @param inputType
     *            column input type (DefaultInputType)
     *
     * @return Name (as seen by the user) of the first column in the specified table type's
     *         definition that matches the specified default input type; null if the input type
     *         doesn't exist in the table type definition
     *********************************************************************************************/
    protected String getColumnNameByInputType(String typeName, DefaultInputType inputType)
    {
        return getColumnNameByInputType(typeName,
                                        inputTypeHandler.getInputTypeByDefaultType(inputType));
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
     * @param useDbName
     *            true to use the database column name; false to use the user column name
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
                    String name = useDbName
                                            ? typeDefn.getColumnNamesDatabase()[enumIndex]
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
     * @param inputTypeNames
     *            list of the input type names, before and after the changes; null if none of the
     *            input type names changed
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
                typeDefn.getInputTypesList().set(index,
                                                 inputTypeHandler.getInputTypeByName(inputTypeName));
            }
        }
    }

    /**********************************************************************************************
     * Check if the specified table types are new or match an existing one. If new then add the
     * table type. If the table type name matches then compare the type definitions to ensure the
     * two are the same (ignoring the column descriptions)
     *
     * @param tableTypeDefinitions
     *            list of table type definitions
     *
     * @return null if all of the table types are created or match existing ones; the name of the
     *         table type that matches an existing one but the type definitions differ
     *
     * @throws CCDDException
     *             If an imported data field name will cause an existing table's field to be
     *             renamed and the user elects to cancel the update
     *********************************************************************************************/
    protected String updateTableTypes(List<TableTypeDefinition> tableTypeDefinitions) throws CCDDException
    {
        boolean isNewStruct = false;
        String badType = null;
        isNewField = false;

        // Step through each table type definition
        for (TableTypeDefinition tableTypeDefn : tableTypeDefinitions)
        {
            // Determine if the table type is new or matches an existing one with the same name
            TableTypeUpdate typeUpdate = updateTableTypes(tableTypeDefn);

            // Check if the type name matches an existing one but the type definition differs
            if (typeUpdate == TableTypeUpdate.MISMATCH)
            {
                // Store the type name that mismatched and stop processing the table type
                // definitions and stop checking table types
                badType = tableTypeDefn.getTypeName();
                break;
            }

            // Check if the table type is new and represents a structure
            if (typeUpdate == TableTypeUpdate.NEW
                && getTypeDefinition(tableTypeDefn.getTypeName()).isStructure())
            {
                // Set the flag to indicate a structure table type was added
                isNewStruct = true;
            }
        }

        // Clear the table type definitions since they have been incorporated
        tableTypeDefinitions.clear();

        // Check if no mismatches occurred
        if (badType == null)
        {
            // Check if the deleted type represents a structure
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

            // Check if a data field was created for a table type
            if (isNewField)
            {
                // Store the data field table with the additional fields
                dbTable.storeInformationTable(InternalTable.FIELDS,
                                              fieldHandler.getFieldDefnsFromInfo(),
                                              null,
                                              ccddMain.getMainFrame());
            }
        }

        return badType;
    }

    /**********************************************************************************************
     * Check if specified table type is new or matches an existing one. If new then add the table
     * type. If the table type name matches then compare the type definitions to ensure the two are
     * the same (ignoring the column descriptions)
     *
     * @param tableTypeDefn
     *            table type definition
     *
     * @return TableTypeUpdate.NEW if the table type is new, TableTypeUpdate.MATCH if the table
     *         type matches an existing one, or TableTypeUpdate.MISMATCH if the table type name
     *         matches an existing one but the type definition differs
     *
     * @throws CCDDException
     *             If an imported data field name will cause an existing table's field to be
     *             renamed and the user elects to cancel the update
     *********************************************************************************************/
    private TableTypeUpdate updateTableTypes(TableTypeDefinition tableTypeDefn) throws CCDDException
    {
        boolean isAddField = false;
        boolean isExistingTypeFieldChanged = false;
        TableTypeUpdate typeUpdate = TableTypeUpdate.MATCH;

        // Get the type definition based on the type name
        TypeDefinition typeDefn = getTypeDefinition(tableTypeDefn.getTypeName());

        // Check if the table type doesn't already exist, or if no tables exist in the database for
        // this type (the existing type will be replaced)
        if (typeDefn == null
            || dbTable.queryTablesOfTypeList(typeDefn.getName(),
                                             ccddMain.getMainFrame()).length == 0)
        {
            // Set the flag indicating the table type is new
            typeUpdate = TableTypeUpdate.NEW;

            // Add the new table type
            createReplaceTypeDefinition(tableTypeDefn.getTypeName(),
                                        tableTypeDefn.getDescription(),
                                        tableTypeDefn.getColumns().toArray(new Object[0][0]));

            // Check if a data field is associated with the new table type
            if (tableTypeDefn.getDataFields().size() != 0)
            {
                // Set the flag to indicate a new data field is added
                isAddField = true;
            }

            // Check if the table type editor is open
            if (ccddMain.getTableTypeEditor() != null && ccddMain.getTableTypeEditor().isShowing())
            {
                // Add the new table type tab to the editor
                ccddMain.getTableTypeEditor().addTypePanes(new String[] {tableTypeDefn.getTypeName()});
            }
        }
        // A table type with this name already exists
        else
        {
            // Add the table type with a different name and get a reference to it
            TypeDefinition altTypeDefn = createReplaceTypeDefinition(tableTypeDefn.getTypeName() + "_TEMP",
                                                                     tableTypeDefn.getDescription(),
                                                                     tableTypeDefn.getColumns().toArray(new Object[0][0]));

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

            // Delete the added type definition
            getTypeDefinitions().remove(altTypeDefn);

            // Check if no mismatch was detected
            if (typeUpdate != TableTypeUpdate.MISMATCH)
            {
                // Step through each table type data field
                for (String[] dataField : tableTypeDefn.getDataFields())
                {
                    // Get the reference to the data field from the existing field information
                    FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(dataField[FieldsColumn.OWNER_NAME.ordinal()],
                                                                                        dataField[FieldsColumn.FIELD_NAME.ordinal()]);

                    // Check if this is a new field
                    if (fieldInfo == null)
                    {
                        // Set the flag to indicate a new data field is added, and to an existing
                        // table type
                        isAddField = true;
                        isExistingTypeFieldChanged = true;
                    }
                    // Check if the existing field's input type, required state, applicability, or
                    // value don't match (the description and size are allowed to differ)
                    else if (!dataField[FieldsColumn.FIELD_TYPE.ordinal()].equals(fieldInfo.getInputType().getInputName())
                             || !dataField[FieldsColumn.FIELD_REQUIRED.ordinal()].equalsIgnoreCase(Boolean.toString(fieldInfo.isRequired()))
                             || !dataField[FieldsColumn.FIELD_APPLICABILITY.ordinal()].equals(fieldInfo.getApplicabilityType().getApplicabilityName())
                             || !dataField[FieldsColumn.FIELD_VALUE.ordinal()].equals(fieldInfo.getValue()))
                    {
                        // Set the flag indicating a mismatch exists and stop searching
                        typeUpdate = TableTypeUpdate.MISMATCH;
                        break;
                    }
                    // Check if the field's description or size is changed
                    else if (!dataField[FieldsColumn.FIELD_DESC.ordinal()].equals(fieldInfo.getDescription())
                             || Integer.parseInt(dataField[FieldsColumn.FIELD_SIZE.ordinal()]) != fieldInfo.getSize())
                    {
                        // Update the field's description and size. This updates the field
                        // information in the field handler (pass by reference)
                        fieldInfo.setDescription(dataField[FieldsColumn.FIELD_DESC.ordinal()]);
                        fieldInfo.setSize(Integer.parseInt(dataField[FieldsColumn.FIELD_SIZE.ordinal()]));

                        // Set the flag to indicate a data field is changed for an existing table
                        // type
                        isExistingTypeFieldChanged = true;
                    }
                }
            }
        }

        // Check if no mismatch was detected
        if (typeUpdate != TableTypeUpdate.MISMATCH)
        {
            // Check if a data field was created
            if (isAddField)
            {
                // Add the table type's data field definitions, if any, to the existing field
                // definitions
                List<String[]> fieldDefns = fieldHandler.getFieldDefnsFromInfo();
                fieldDefns.addAll(tableTypeDefn.getDataFields());
                fieldHandler.setFieldInformationFromDefinitions(fieldDefns);

                // Set the flag to indicate a new data field is added
                isNewField = true;
            }

            // Check if a field was added to or changed for an existing table type
            if (isExistingTypeFieldChanged)
            {
                boolean continueOnDuplicate = false;

                // Get the list of table of this table type
                List<String> tablesOfType = dbTable.getAllTablesOfType(tableTypeDefn.getTypeName(),
                                                                       null,
                                                                       ccddMain.getMainFrame());

                // Step through each of the table type's data fields
                for (FieldInformation typeFldInfo : fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldTypeName(tableTypeDefn.getTypeName())))
                {
                    // Check if the modified default field's name causes a table's existing field
                    // to be renamed, unless the user has elected to allow renaming
                    if (!continueOnDuplicate
                        && fieldHandler.checkForDuplicateField(tablesOfType,
                                                               typeFldInfo.getFieldName(),
                                                               typeFldInfo.getInputType().getInputName()))
                    {
                        // Inform the user that using the field name results in renaming a table's
                        // field
                        if (new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                                      "<html><b>Imported table type '</b>"
                                                                                               + tableTypeDefn.getTypeName()
                                                                                               + "<b>' data field name '</b>"
                                                                                               + typeFldInfo.getFieldName()
                                                                                               + "<b>' will cause an existing table's field to be renamed; continue?",
                                                                      "Duplicate Field Name",
                                                                      JOptionPane.WARNING_MESSAGE,
                                                                      DialogOption.OK_CANCEL_OPTION) == CANCEL_BUTTON)
                        {
                            throw new CCDDException();
                        }

                        // Set the flag to ignore further duplicates
                        continueOnDuplicate = true;
                    }

                    // Step through each table of this type
                    for (String tablePath : tablesOfType)
                    {
                        // Add or update the table type field to the table, depending on whether or
                        // not the table already has the field
                        fieldHandler.addUpdateInheritedField(fieldHandler.getFieldInformation(),
                                                             tablePath,
                                                             typeFldInfo);
                    }
                }
            }
        }

        return typeUpdate;
    }

    /**********************************************************************************************
     * Get the array of command argument column definitions
     *
     * @return Array of command argument column definitions
     *********************************************************************************************/
    protected static Object[][] getCommandArgumentColumns()
    {
        return commandArgumentColumns;
    }
}
