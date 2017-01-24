/**
 * CFS Command & Data Dictionary data type handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary data type handler class
 *****************************************************************************/
public class CcddDataTypeHandler
{
    // Class references
    private CcddDbCommandHandler dbCommand;
    private CcddTableTypeHandler tableTypeHandler;

    // List containing the data type names and associated data type definitions
    private List<String[]> dataTypes;

    /**************************************************************************
     * Data type handler class constructor used when setting the data types
     * from a source other than those in the project database
     * 
     * @param dataTypes
     *            list of string arrays containing data type names and the
     *            corresponding data type definitions
     *************************************************************************/
    CcddDataTypeHandler(List<String[]> dataTypes)
    {
        this.dataTypes = dataTypes;
    }

    /**************************************************************************
     * Data type handler class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    CcddDataTypeHandler(CcddMain ccddMain)
    {
        // Load the data types table from the project database
        this(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.DATA_TYPES,
                                                                          true,
                                                                          ccddMain.getMainFrame()));
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
    }

    /**************************************************************************
     * Get the data type data
     * 
     * @return List of string arrays containing data type names and the
     *         corresponding data type definitions
     *************************************************************************/
    protected List<String[]> getDataTypeData()
    {
        return dataTypes;
    }

    /**************************************************************************
     * Set the data types to the supplied array
     * 
     * @param dataTypes
     *            list of string arrays containing data type names and the
     *            corresponding data type definitions
     *************************************************************************/
    protected void setDataTypeData(List<String[]> dataTypes)
    {
        this.dataTypes = new ArrayList<String[]>(dataTypes);
    }

    /**************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank,
     * in which case return the C-language name
     * 
     * @param dataType
     *            string array containing data type name and the corresponding
     *            data type definition
     * 
     * @return User-defined data type name; if blank then the C-language name
     *************************************************************************/
    protected static String getDataTypeName(String[] dataType)
    {
        return getDataTypeName(dataType[DataTypesColumn.USER_NAME.ordinal()],
                               dataType[DataTypesColumn.C_NAME.ordinal()]);
    }

    /**************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank,
     * in which case return the C-language name
     * 
     * @param userName
     *            user-defined data type name
     * 
     * @param cName
     *            C-language data type
     * 
     * @return User-defined data type name; if blank then the C-language name
     *************************************************************************/
    protected static String getDataTypeName(String userName, String cName)
    {
        String dataTypeName;

        // Check if the user-defined name is blank
        if (userName.isEmpty())
        {
            // Get the C-language name
            dataTypeName = cName;
        }
        // User-defined name isn't blank
        else
        {
            // Get the user-defined name
            dataTypeName = userName;
        }

        return dataTypeName;
    }

    /**************************************************************************
     * Get the data type information associated with the specified data type
     * name
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Data type information associated with the specified data type
     *         name; returns null if the data type doesn't exist
     *************************************************************************/
    protected String[] getDataTypeInfo(String dataTypeName)
    {
        String[] dataType = null;

        // Step through each defined data type
        for (String[] type : dataTypes)
        {
            // Check if the supplied name matches this data type's name
            if (dataTypeName.equalsIgnoreCase(getDataTypeName(type)))
            {
                // Store the data type information and stop searching
                dataType = type;
                break;
            }
        }

        return dataType;
    }

    /**************************************************************************
     * Get the base data type for the specified data type
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Base data type for the specified data type; returns null if the
     *         data type doesn't exist
     *************************************************************************/
    protected BaseDataTypeInfo getBaseDataType(String dataTypeName)
    {
        BaseDataTypeInfo baseDataType = null;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeInfo(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated base data type
            baseDataType = BaseDataTypeInfo.getBaseType(dataType[DataTypesColumn.BASE_TYPE.ordinal()]);
        }

        return baseDataType;
    }

    /**************************************************************************
     * Get the data type size in bytes for the specified data type
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Data type size in bytes for the specified data type; returns 0
     *         if the data type doesn't exist
     *************************************************************************/
    protected int getSizeInBytes(String dataTypeName)
    {
        int dataTypeSize = 0;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeInfo(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated data type size
            dataTypeSize = Integer.valueOf(dataType[DataTypesColumn.SIZE.ordinal()]);
        }

        return dataTypeSize;
    }

    /**************************************************************************
     * Get the data type size in bits for the specified data type
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Data type size in bits for the specified data type; returns 0 if
     *         the data type doesn't exist
     *************************************************************************/
    protected int getSizeInBits(String dataTypeName)
    {
        return getSizeInBytes(dataTypeName) * 8;
    }

    /**************************************************************************
     * Determine if the supplied data type is a primitive type
     * 
     * @param dataTypeName
     *            name of data type to test
     * 
     * @return true if the supplied data type is a primitive
     *************************************************************************/
    protected boolean isPrimitive(String dataTypeName)
    {
        boolean isPrimitive = false;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeInfo(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Set the flag to indicate the data type is a primitive
            isPrimitive = true;
        }

        return isPrimitive;
    }

    /**************************************************************************
     * Check if the supplied data type name is already in use (case
     * insensitive)
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if the supplied data type name is already in use
     *************************************************************************/
    protected boolean isDataTypeExists(String dataTypeName)
    {
        boolean isExists = false;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeInfo(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Set the flag to indicate the data type name already exists
            isExists = true;
        }

        return isExists;
    }

    /**************************************************************************
     * Determine if the specified data type is a signed or unsigned integer
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if the specified data type is a signed or unsigned integer
     *************************************************************************/
    protected boolean isInteger(String dataTypeName)
    {
        boolean isInteger = false;

        // get the base data type for the specified data type
        BaseDataTypeInfo baseDataType = getBaseDataType(dataTypeName);

        // Set the flag to true if the base data type is an integer (signed or
        // unsigned)
        isInteger = baseDataType == BaseDataTypeInfo.UNSIGNED_INT
                    || baseDataType == BaseDataTypeInfo.SIGNED_INT;

        return isInteger;
    }

    /**************************************************************************
     * Determine if the the specified data type is an unsigned integer
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if the specified data type is an unsigned integer
     *************************************************************************/
    protected boolean isUnsigned(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.UNSIGNED_INT;
    }

    /**************************************************************************
     * Determine if the specified data type is a float or double
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if the specified data type is a float or double
     *************************************************************************/
    protected boolean isFloat(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.FLOATING_POINT;
    }

    /**************************************************************************
     * Determine if the this primitive data type is a character or string
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if this data type is a character or string
     *************************************************************************/
    protected boolean isString(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.CHARACTER;
    }

    /**************************************************************************
     * Determine if the this primitive data type is not an integer, floating
     * point, or string
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return true if this data type is not an integer, floating point, or
     *         string
     *************************************************************************/
    protected boolean isOther(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.OTHER;
    }

    /**********************************************************************
     * Get the minimum possible value of the primitive type based on the data
     * type and size in bytes
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Minimum possible value of the primitive type based on the data
     *         type and size in bytes
     *********************************************************************/
    protected Object getMinimum(String dataTypeName)
    {
        Object minimum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsigned(dataTypeName))
        {
            minimum = 0;
        }
        // Check if the data type is a signed integer (an unsigned integer
        // was already accounted for above)
        else if (isInteger(dataTypeName))
        {
            minimum = -(int) Math.pow(2, bytes * 8) / 2;
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double minimum values
            minimum = bytes == 4
                                ? Float.MIN_VALUE
                                : bytes == 8
                                            ? Double.MIN_VALUE
                                            : 0;
        }

        return minimum;
    }

    /**********************************************************************
     * Get the maximum possible value of the primitive type based on the data
     * type and size in bytes
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return Maximum possible value of the primitive type based on the data
     *         type and size in bytes
     *********************************************************************/
    protected Object getMaximum(String dataTypeName)
    {
        Object maximum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsigned(dataTypeName))
        {
            maximum = (int) Math.pow(2, bytes * 8);
        }
        // Check if the data type is a signed integer (an unsigned integer
        // was already accounted for above)
        else if (isInteger(dataTypeName))
        {
            int maxUnsigned = (int) Math.pow(2, bytes * 8);
            maximum = maxUnsigned - maxUnsigned / 2 + 1;
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double maximum values
            maximum = bytes == 4
                                ? Float.MAX_VALUE
                                : bytes == 8
                                            ? Double.MAX_VALUE
                                            : 0;
        }

        return maximum;
    }

    /**************************************************************************
     * Get the OID value associated with the specified data type name
     * 
     * @param dataTypeName
     *            data type name
     * 
     * @return OID value associated with the specified data type name; returns
     *         null if the data type doesn't exist
     *************************************************************************/
    protected String getDataTypeIndex(String dataTypeName)
    {
        String dataTypeIndex = null;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeInfo(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated data type index
            dataTypeIndex = dataType[DataTypesColumn.OID.ordinal()];
        }

        return dataTypeIndex;
    }

    /**************************************************************************
     * Get a list containing the tables in the project database that reference
     * the specified data type name. Only search for references in the
     * prototype tables (any references in the custom values table are
     * automatically updated when the prototype is changed)
     * 
     * @param dataTypeName
     *            data type name for which to search
     * 
     * @return List containing the tables in the database that reference the
     *         specified data type name
     *************************************************************************/
    protected String[] getDataTypeReferences(String dataTypeName,
                                             Component parent)
    {
        // Get the references in the prototype tables that match the specified
        // data type name
        List<String> matches = new ArrayList<String>(Arrays.asList(dbCommand.getList(DatabaseListCommand.SEARCH,
                                                                                     new String[][] { {"_search_text_",
                                                                                                       dataTypeName},
                                                                                                     {"_case_insensitive_",
                                                                                                      "true"},
                                                                                                     {"_selected_tables_",
                                                                                                      SearchType.PROTO.toString()}},
                                                                                     parent)));

        // Step through each match (in reverse since an entry in the list may
        // need to be removed)
        for (int index = matches.size() - 1; index >= 0; index--)
        {
            // Separate the match components
            String[] tblColDescAndCntxt = matches.get(index).split(TABLE_DESCRIPTION_SEPARATOR, 4);

            // Separate the user-viewable table name and table type
            String[] tableAndType = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].split(",", 2);

            // Check if the table names match
            if (tblColDescAndCntxt[SearchResultsQueryColumn.TABLE.ordinal()].equals(tableAndType[0]))
            {
                // Get the table's type definition
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableAndType[1]);

                // Get the column index based on the column's database name
                int column = typeDefn.getColumnIndexByDbName(tblColDescAndCntxt[SearchResultsQueryColumn.COLUMN.ordinal()]);

                // Check if the column represents a primitive data type
                if (typeDefn.getInputTypes()[column] == InputDataType.PRIM_AND_STRUCT
                    || typeDefn.getInputTypes()[column] == InputDataType.PRIMITIVE)
                {
                    // Separate the location into the individual columns.
                    // Commas between double quotes are ignored so that an
                    // erroneous column separation doesn't occur
                    String[] columns = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

                    // Check if the column's contents doesn't match the
                    // specified data type name
                    if (!dataTypeName.equalsIgnoreCase(columns[column]))
                    {
                        // Remove the match
                        matches.remove(index);
                    }
                }
                // The column doesn't represent a primitive data type
                else
                {
                    // Remove the match
                    matches.remove(index);
                }

                // Stop searching since a matching table was found
                break;
            }
        }

        return matches.toArray(new String[0]);
    }

    /**************************************************************************
     * Add new data types and check for matches with existing ones
     * 
     * @param dataTypeDefinitions
     *            list of data type definitions
     * 
     * @return The name of the data type if its name matches an existing one
     *         but the type definition differs; return null if the data type is
     *         new or matches an existing one
     *************************************************************************/
    protected String updateDataTypes(List<String[]> dataTypeDefinitions)
    {
        String badType = null;

        // Step through each imported data type definition
        for (String[] typeDefn : dataTypeDefinitions)
        {
            // Get the data type information associated with this data type
            // name
            String[] dataType = getDataTypeInfo(CcddDataTypeHandler.getDataTypeName(typeDefn));

            // Check if the data type doesn't already exist
            if (dataType == null)
            {
                // Add the data type
                dataTypes.add(typeDefn);
            }
            // The data type exists; check if the type information provided
            // matches the existing type information
            else if (!(dataType[DataTypesColumn.USER_NAME.ordinal()].equals(typeDefn[DataTypesColumn.USER_NAME.ordinal()])
                       && dataType[DataTypesColumn.C_NAME.ordinal()].equals(typeDefn[DataTypesColumn.C_NAME.ordinal()])
                       && dataType[DataTypesColumn.SIZE.ordinal()].equals(typeDefn[DataTypesColumn.SIZE.ordinal()])
                       && dataType[DataTypesColumn.BASE_TYPE.ordinal()].equals(typeDefn[DataTypesColumn.BASE_TYPE.ordinal()])))
            {
                // Store the name of the mismatched data type and stop
                // searching
                badType = CcddDataTypeHandler.getDataTypeName(typeDefn);
                break;
            }
        }

        return badType;
    }
}
