/**************************************************************************************************
 * /** \file CcddDataTypeHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling data type operations.
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

import static CCDD.CcddConstants.SIZEOF_DATATYPE;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.JTextComponent;

import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DataTypeEditorColumnInfo;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.SearchType;

/**************************************************************************************************
 * CFS Command and Data Dictionary data type handler class
 *************************************************************************************************/
public class CcddDataTypeHandler
{
    // Class references
    private CcddDbTableCommandHandler dbTable;
    private CcddDbCommandHandler dbCommand;
    private CcddTableTypeHandler tableTypeHandler;
    private CcddMacroHandler macroHandler;

    // List containing the data type names and associated data type definitions
    private List<String[]> dataTypes;

    // Contains a HashMap of the list variable "dataTypes". Used for faster lookups
    private HashMap<String, String[]> dataTypesMap;

    /**********************************************************************************************
     * Data type handler class constructor used when setting the data types from a source other
     * than those in the project database
     *
     * @param dataTypes List of string arrays containing data type names and the corresponding data
     *                  type definitions
     *
     * @param ccddMain  Main class
     *********************************************************************************************/
    CcddDataTypeHandler(List<String[]> dataTypes, CcddMain ccddMain)
    {
        this.dataTypes = dataTypes;

        // There is a new list of data types, generate the equivalent map
        buildDataTypesMap();
    }

    /**********************************************************************************************
     * Data type handler class constructor
     *
     * @param ccddMain Main class
     *********************************************************************************************/
    CcddDataTypeHandler(CcddMain ccddMain)
    {
        // Load the data types table from the project database
        this(ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.DATA_TYPES,
                                                                          true,
                                                                          ccddMain.getMainFrame()),
             ccddMain);

        // Get references to make subsequent calls shorter
        dbTable = ccddMain.getDbTableCommandHandler();
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        dataTypesMap = null;
    }

    /**********************************************************************************************
     * Generate the data types map from the variable "dataTypes" list
     *********************************************************************************************/
    private void buildDataTypesMap()
    {
        dataTypesMap = new HashMap<>(dataTypes.size());

        for (String[] type : dataTypes)
        {
            dataTypesMap.put(getDataTypeName(type), type);
        }
    }

    /**********************************************************************************************
     * Accessor function for the dataTypesMap variable
     *
     * @return HashMap&lt;String, String[]&gt;
     *********************************************************************************************/
    private HashMap<String, String[]> getDataTypesAsMap()
    {
        // Build the set if necessary
        if ((dataTypesMap == null) || (dataTypesMap.size() != dataTypes.size()))
        {
            buildDataTypesMap();
        }

        return dataTypesMap;
    }

    /**********************************************************************************************
     * Get the list of data type definitions
     *
     * @return List of string arrays containing the data type definitions
     *********************************************************************************************/
    protected List<String[]> getDataTypeData()
    {
        return dataTypes;
    }

    /**********************************************************************************************
     * Set/Replace the MacroHandler
     *
     * @param macroHandler Macro handler
     *********************************************************************************************/
    protected void setMacroHandler(CcddMacroHandler macroHandler)
    {
        this.macroHandler = macroHandler;
    }

    /**********************************************************************************************
     * Get a list of the data type names
     *
     * @return List of data type names, sorted alphabetically; an empty list if no data types are
     *         defined
     *********************************************************************************************/
    protected List<String> getDataTypeNames()
    {
        // Create a list to hold the data type names
        List<String> dataTypeNames = new ArrayList<String>();

        // Step through each data type
        for (String[] dataType : dataTypes)
        {
            // Store the data type name in the list
            dataTypeNames.add(getDataTypeName(dataType));
        }

        // Sort the data type names alphabetically
        Collections.sort(dataTypeNames, String.CASE_INSENSITIVE_ORDER);

        return dataTypeNames;
    }

    /**********************************************************************************************
     * Determine if the specified column contains integer values
     *
     * @param column Column index
     *
     * @return true if the specified column contains integer values; false otherwise
     *********************************************************************************************/
    private boolean isInteger(int column)
    {
        return column == DataTypeEditorColumnInfo.SIZE.ordinal();
    }

    /**********************************************************************************************
     * Get the data type definitions as an object array. The object array allows preserving the
     * column value's type (string, integer, etc.)
     *
     * @return Data type definitions as an object array
     *********************************************************************************************/
    protected Object[][] getDataTypeDataArray()
    {
        // Create storage for the data type definitions
        Object[][] dataTypesArray = new Object[dataTypes.size()][DataTypeEditorColumnInfo.values().length];

        // Step through each data type definition
        for (int row = 0; row < dataTypes.size(); row++)
        {
            // Step through each column in the data type definition
            for (int column = 0; column < DataTypeEditorColumnInfo.values().length; column++)
            {
                // Check to see if we are working with the SIZE column
                if (isInteger(column))
                {
                    // Check if the value being stored is a macro and if so store it as a string.
                    // If not then store it as an integer
                    if (dataTypes.get(row)[column].contains("##"))
                    {
                        dataTypesArray[row][column] = dataTypes.get(row)[column].toString();
                    }
                    else
                    {
                        dataTypesArray[row][column] = Integer.valueOf(dataTypes.get(row)[column].toString());
                    }
                }
                else
                {
                    // Store the column value as a string or integer
                    dataTypesArray[row][column] = dataTypes.get(row)[column].toString();
                }
            }
        }

        return dataTypesArray;
    }

    /**********************************************************************************************
     * Set the data types to the supplied array
     *
     * @param dataTypes List of string arrays containing data type names and the corresponding data
     *                  type definitions
     *********************************************************************************************/
    protected void setDataTypeData(List<String[]> dataTypes)
    {
        this.dataTypes = CcddUtilities.copyListOfStringArrays(dataTypes);

        // There is a new list, build the map again
        buildDataTypesMap();
    }

    /**********************************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank, in which case return
     * the C-language name
     *
     * @param dataType String array containing data type name and the corresponding data type
     *                 definition
     *
     * @return User-defined data type name; if blank then the C-language name
     *********************************************************************************************/
    protected static String getDataTypeName(String[] dataType)
    {
        return getDataTypeName(dataType[DataTypesColumn.USER_NAME.ordinal()],
                               dataType[DataTypesColumn.C_NAME.ordinal()]);
    }

    /**********************************************************************************************
     * Get the data type name. Return the user-defined name unless it's blank, in which case return
     * the C-language name
     *
     * @param userName User-defined data type name
     *
     * @param cName    C-language data type
     *
     * @return User-defined data type name; if blank then the C-language name
     *********************************************************************************************/
    protected static String getDataTypeName(String userName, String cName)
    {
        String dataTypeName = null;

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

    /**********************************************************************************************
     * Get the data type information associated with the specified data type name
     *
     * @param dataTypeName Data type name
     *
     * @return Data type information associated with the specified data type name; returns null if
     *         the data type doesn't exist
     *********************************************************************************************/
    protected String[] getDataTypeByName(String dataTypeName)
    {
        // Pull from the map and return the value. Return null if the key is not in the map. This
        // is a faster implementation than searching through the list and doing a string compare on
        // each item in the list
        return getDataTypesAsMap().get(dataTypeName);
    }

    /**********************************************************************************************
     * Get the base data type for the specified data type
     *
     * @param dataTypeName Data type name
     *
     * @return Base data type for the specified data type; returns null if the data type doesn't
     *         exist
     *********************************************************************************************/
    protected BaseDataTypeInfo getBaseDataType(String dataTypeName)
    {
        BaseDataTypeInfo baseDataType = null;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Get the associated base data type
            baseDataType = BaseDataTypeInfo.getBaseType(dataType[DataTypesColumn.BASE_TYPE.ordinal()]);
        }

        return baseDataType;
    }

    /**********************************************************************************************
     * Get the data type size for the specified data type
     *
     * @param dataTypeName Data type name
     *
     * @return Data type size for the specified data type; returns 0 if the data type doesn't exist
     *********************************************************************************************/
    protected int getDataTypeSize(String dataTypeName)
    {
        int dataTypeSize = 0;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Check if the size is defined by a macro
            if (dataType[DataTypesColumn.SIZE.ordinal()].contains("##"))
            {
                dataTypeSize = Integer.valueOf(macroHandler.getMacroExpansion(dataType[DataTypesColumn.SIZE.ordinal()],
                                                                              new ArrayList<String>()));
            }
            else
            {
                // Get the associated data type size
                dataTypeSize = Integer.valueOf(dataType[DataTypesColumn.SIZE.ordinal()]);
            }
        }

        return dataTypeSize;
    }

    /**********************************************************************************************
     * Get the data type size in bytes for the specified data type
     *
     * @param dataTypeName Data type name
     *
     * @return Data type size in bytes for the specified data type; returns 0 if the data type
     *         doesn't exist
     *********************************************************************************************/
    protected int getSizeInBytes(String dataTypeName)
    {
        // Get the data type size
        int dataTypeSize = getDataTypeSize(dataTypeName);

        // Check if this data type is a character string
        if (isString(dataTypeName))
        {
            // Force the size to 1 byte. This prevents the string pseudo-data type, which uses a
            // size other than 1 to indicate the data type is a string, from returning an incorrect
            // size
            dataTypeSize = 1;
        }

        return dataTypeSize;
    }

    /**********************************************************************************************
     * Get the data type size in bits for the specified data type
     *
     * @param dataTypeName Data type name
     *
     * @return Data type size in bits for the specified data type; returns 0 if the data type
     *         doesn't exist
     *********************************************************************************************/
    protected int getSizeInBits(String dataTypeName)
    {
        return getSizeInBytes(dataTypeName) * 8;
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a primitive type
     *
     * @param dataTypeName Name of data type to test
     *
     * @return true if the supplied data type is a primitive
     *********************************************************************************************/
    protected boolean isPrimitive(String dataTypeName)
    {
        boolean isPrimitive = false;

        // Get the data type information based on the type name
        String[] dataType = getDataTypeByName(dataTypeName);

        // Check if the data type exists
        if (dataType != null)
        {
            // Set the flag to indicate the data type is a primitive
            isPrimitive = true;
        }

        return isPrimitive;
    }

    /**********************************************************************************************
     * Determine if the specified data type is a signed or unsigned integer
     *
     * @param dataTypeName Data type name
     *
     * @return true if the specified data type is a signed or unsigned integer
     *********************************************************************************************/
    protected boolean isInteger(String dataTypeName)
    {
        boolean isInteger = false;

        // get the base data type for the specified data type
        BaseDataTypeInfo baseDataType = getBaseDataType(dataTypeName);

        // Set the flag to true if the base data type is an integer (signed or unsigned)
        isInteger = baseDataType == BaseDataTypeInfo.UNSIGNED_INT
                    || baseDataType == BaseDataTypeInfo.SIGNED_INT;

        return isInteger;
    }

    /**********************************************************************************************
     * Determine if the the specified data type is a signed integer
     *
     * @param dataTypeName Data type name
     *
     * @return true if the specified data type is a signed integer
     *********************************************************************************************/
    protected boolean isSignedInt(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.SIGNED_INT;
    }

    /**********************************************************************************************
     * Determine if the the specified data type is an unsigned integer
     *
     * @param dataTypeName Data type name
     *
     * @return true if the specified data type is an unsigned integer
     *********************************************************************************************/
    protected boolean isUnsignedInt(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.UNSIGNED_INT;
    }

    /**********************************************************************************************
     * Determine if the specified data type is a float or double
     *
     * @param dataTypeName Data type name
     *
     * @return true if the specified data type is a float or double
     *********************************************************************************************/
    protected boolean isFloat(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.FLOATING_POINT;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a character or string
     *
     * @param dataTypeName Data type name
     *
     * @return true if this data type is a character or string
     *********************************************************************************************/
    protected boolean isCharacter(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.CHARACTER;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a character string
     *
     * @param dataTypeName Data type name
     *
     * @return true if this data type is a character string
     *********************************************************************************************/
    protected boolean isString(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.CHARACTER
                                                && getDataTypeSize(dataTypeName) > 1;
    }

    /**********************************************************************************************
     * Determine if the this primitive data type is a pointer
     *
     * @param dataTypeName Data type name
     *
     * @return true if this data type is a pointer
     *********************************************************************************************/
    protected boolean isPointer(String dataTypeName)
    {
        return getBaseDataType(dataTypeName) == BaseDataTypeInfo.POINTER;
    }

    /**********************************************************************************************
     * Get the minimum possible value of the primitive type based on the data type and size in
     * bytes
     *
     * @param dataTypeName Data type name
     *
     * @return Minimum possible value of the primitive type based on the data type and size in
     *         bytes
     *********************************************************************************************/
    protected Object getMinimum(String dataTypeName)
    {
        Object minimum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsignedInt(dataTypeName))
        {
            minimum = (long) 0;
        }
        // Check if the data type is a signed integer (an unsigned integer was already accounted
        // for above)
        else if (isInteger(dataTypeName))
        {
            minimum = (long) (-(long) Math.pow(2, bytes * 8) / 2);
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double minimum values
            minimum = bytes == 4 ? (float) -Float.MAX_VALUE
                                 : bytes == 8 ? (double) -Double.MAX_VALUE
                                              : 0;
        }

        return minimum;
    }

    /**********************************************************************************************
     * Get the maximum possible value of the primitive type based on the data type and size in
     * bytes
     *
     * @param dataTypeName Data type name
     *
     * @return Maximum possible value of the primitive type based on the data type and size in
     *         bytes
     *********************************************************************************************/
    protected Object getMaximum(String dataTypeName)
    {
        Object maximum = 0;

        // Get the data type size in bytes
        int bytes = getSizeInBytes(dataTypeName);

        // Check if the data type is an unsigned integer
        if (isUnsignedInt(dataTypeName))
        {
            maximum = (long) Math.pow(2, bytes * 8);
        }
        // Check if the data type is a signed integer (an unsigned integer was already accounted
        // for above)
        else if (isInteger(dataTypeName))
        {
            long maxUnsigned = (long) Math.pow(2, bytes * 8);
            maximum = (long) (maxUnsigned - maxUnsigned / 2 + 1);
        }
        // Check if the data type is a floating point
        else if (isFloat(dataTypeName))
        {
            // Use the Java float and double maximum values
            maximum = bytes == 4 ? (float) Float.MAX_VALUE
                                 : bytes == 8 ? (double) Double.MAX_VALUE
                                              : 0;
        }

        return maximum;
    }

    /**********************************************************************************************
     * Get a list containing the tables in the project database that reference the specified data
     * type name. Only search for references in the prototype tables (any references in the custom
     * values table are automatically updated when the prototype is changed)
     *
     * @param dataTypeName Data type name for which to search
     *
     * @param parent       GUI component over which to center any error dialog
     *
     * @return List containing the tables in the database that reference the specified data type
     *         name
     *********************************************************************************************/
    protected String[] searchDataTypeReferences(String dataTypeName, Component parent)
    {
        String searchCriteria = dataTypeName;

        // Step through each macro with a value that is dependent on this data type
        for (String macroName : macroHandler.getDataTypeReferences(dataTypeName))
        {
            // Add the macro name to the search criteria
            searchCriteria += "|" + macroName;
        }

        // Get the references in the prototype tables that match the specified data type name
        List<String> matches = new ArrayList<String>(Arrays.asList(dbCommand.getList(DatabaseListCommand.SEARCH,
                                                                                     new String[][] {{"_search_text_", "(" + searchCriteria + ")"},
                                                                                                     {"_case_insensitive_", "true"},
                                                                                                     {"_allow_regex_", "true"},
                                                                                                     {"_selected_tables_", SearchType.PROTO.toString()},
                                                                                                     {"_columns_", ""}},
                                                                                     parent)));

        // Remove any references to the data type that appear in an array size column for an array
        // member (the reference in the array's definition is all that's needed)
        CcddSearchHandler.removeArrayMemberReferences(matches, tableTypeHandler);

        return matches.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Add new data types and check for matches with existing ones
     *
     * @param dataTypeDefinitions      List of data type definitions
     *
     * @param replaceExistingDataTypes True if any existing data types that share a name with an
     *                                 imported one should be replaced
     *
     * @throws CCDDException If an data type with the same same already exists and the imported
     *                       type doesn't match
     *********************************************************************************************/
    protected void updateDataTypes(List<String[]> dataTypeDefinitions,
                                   boolean replaceExistingDataTypes) throws CCDDException
    {
        boolean update = false;

        // Step through each imported data type definition
        for (String[] typeDefn : dataTypeDefinitions)
        {
            // Get the data type information associated with this data type name
            String[] dataType = getDataTypeByName(CcddDataTypeHandler.getDataTypeName(typeDefn));

            // Check if the data type doesn't already exist
            if (dataType == null)
            {
                // Add the data type
                dataTypes.add(typeDefn);
            }
            // The data type exists; check if the type information provided matches the existing
            // type information
            else if (!(dataType[DataTypesColumn.USER_NAME.ordinal()].equals(typeDefn[DataTypesColumn.USER_NAME.ordinal()])
                       && dataType[DataTypesColumn.C_NAME.ordinal()].equals(typeDefn[DataTypesColumn.C_NAME.ordinal()])
                       && dataType[DataTypesColumn.SIZE.ordinal()].equals(typeDefn[DataTypesColumn.SIZE.ordinal()])
                       && dataType[DataTypesColumn.BASE_TYPE.ordinal()].equals(typeDefn[DataTypesColumn.BASE_TYPE.ordinal()])))
            {
                // If it does not match then check if we should replace the existing data type
                if (replaceExistingDataTypes)
                {
                    dataTypes.set(dataTypes.indexOf(dataType), typeDefn);
                    update = true;
                }
                else
                {
                    throw new CCDDException("Imported data type '</b>"
                                            + CcddDataTypeHandler.getDataTypeName(typeDefn)
                                            + "<b>' doesn't match the existing definition");
                }
            }
        }

        // Update the data types if any were replaced
        if (update)
        {
            setDataTypeData(dataTypes);
        }
    }

    /**********************************************************************************************
     * Highlight any sizeof() calls in the the specified text component
     *
     * @param component       Reference to the table cell renderer component
     *
     * @param text            Cell value
     *
     * @param hightlightColor Color used for highlighting the sizeof() call
     *********************************************************************************************/
    protected static void highlightSizeof(Component component, String text, Color hightlightColor)
    {
        // Highlight 'sizeof(data type)' instances. Create a highlighter painter
        DefaultHighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(hightlightColor);

        // Create the match pattern
        Pattern pattern = Pattern.compile(SIZEOF_DATATYPE);

        // Create the pattern matcher from the pattern
        Matcher matcher = pattern.matcher(text);

        // Check if there is a match in the cell value
        while (matcher.find())
        {
            try
            {
                // Highlight the matching text. Adjust the highlight color to account for the cell
                // selection highlighting so that the sizeof() call is easily readable
                ((JTextComponent) component).getHighlighter().addHighlight(matcher.start(),
                                                                           matcher.end(),
                                                                           painter);
            }
            catch (BadLocationException ble)
            {
                // Ignore highlighting failure
            }
        }
    }

    /**********************************************************************************************
     * Get the list of items to display in the data type pop-up combo box
     *
     * @param includePrimitives True to include primitive data types in the list; false to include
     *                          only structures
     *
     * @return List of items to display in the data type pop-up combo box
     *********************************************************************************************/
    protected List<String> getDataTypePopUpItems(boolean includePrimitives)
    {
        String[] structures;

        // Get the array of prototype structure table names
        structures = dbTable.getPrototypeTablesOfType(TYPE_STRUCTURE);

        // Check if any structures exist
        if (structures.length != 0)
        {
            // Sort the structure names alphabetically, ignoring case
            Arrays.sort(structures, String.CASE_INSENSITIVE_ORDER);
        }

        // Check if primitive data types are to be included
        if (includePrimitives)
        {
            String[] primitives = new String[dataTypes.size()];

            // Step through each primitive data type
            for (int index = 0; index < dataTypes.size(); index++)
            {
                // Add the data type name to the array
                primitives[index] = getDataTypeName(dataTypes.get(index));
            }

            // Combine the primitive data types and structures arrays
            structures = CcddUtilities.concatenateArrays(primitives, structures);
        }

        return Arrays.asList(structures);
    }
}
