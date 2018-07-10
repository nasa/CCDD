/**
 * CFS Command & Data Dictionary input type handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PROTECTED_MSG_ID_IDENT;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.TableTypesColumn;
import CCDD.CcddConstants.SearchType;

/**************************************************************************************************
 * CFS Command & Data Dictionary input type handler class
 *
 * @param ccddMain
 *            main class
 *************************************************************************************************/
public class CcddInputTypeHandler
{
    // Class reference
    private CcddDbCommandHandler dbCommand;

    // List of input types, both default and custom ones defined by the user
    private final List<InputType> inputTypes;

    // Array containing the custom input type information. This is used by the input type editor
    private String[][] customInputTypes;

    // List of input type that have selection items
    private final List<InputType> selectionInputTypes;

    // Map used to locate an input type based on its name as the key
    private final Map<String, InputType> inputTypeMap;

    /**********************************************************************************************
     * Input type handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddInputTypeHandler(CcddMain ccddMain)
    {
        inputTypeMap = new HashMap<>();
        inputTypes = new ArrayList<InputType>(0);
        selectionInputTypes = new ArrayList<InputType>(0);
        dbCommand = ccddMain.getDbCommandHandler();

        // Set the input type list, combining the default types and the custom types stored in the
        // project database
        setInputTypeData(ccddMain.getDbTableCommandHandler()
                                 .retrieveInformationTable(InternalTable.INPUT_TYPES,
                                                           true,
                                                           ccddMain.getMainFrame())
                                 .toArray(new String[0][0]));
    }

    /**********************************************************************************************
     * Input type handler class constructor
     *
     * @param inputTypesArray
     *            array of custom input type definitions
     *********************************************************************************************/
    CcddInputTypeHandler(String[][] inputTypesArray)
    {
        inputTypeMap = new HashMap<>();
        inputTypes = new ArrayList<InputType>(0);
        selectionInputTypes = new ArrayList<InputType>(0);

        // Set the input type list, combining the default types and the supplied custom types
        setInputTypeData(inputTypesArray);
    }

    /**********************************************************************************************
     * Check if the an input type exists with the specified name
     *
     * @param inputTypeName
     *            input type name to match (case insensitive)
     *
     * @return true if an input exists with the specified name
     *********************************************************************************************/
    protected boolean isInputTypeValid(String inputTypeName)
    {
        return inputTypeMap.get(inputTypeName.toLowerCase()) != null;
    }

    /**********************************************************************************************
     * Check if the two input types differ
     *
     * @param inputTypeA
     *            first input type to compare
     *
     * @param inputTypeB
     *            second input type to compare
     *
     * @return true if the input types differ
     *********************************************************************************************/
    protected boolean isInputTypeChanged(InputType inputTypeA, InputType inputTypeB)
    {
        return !inputTypeA.getInputName().equals(inputTypeB.getInputName())
               || !inputTypeA.getInputDescription().equals(inputTypeB.getInputDescription())
               || !inputTypeA.getInputMatch().equals(inputTypeB.getInputMatch())
               || !inputTypeA.getInputFormat().equals(inputTypeB.getInputFormat())
               || (inputTypeA.getInputItems() != null
                   && inputTypeB.getInputItems() != null
                   && !CcddUtilities.isArraySetsEqual(inputTypeA.getInputItems().toArray(new String[0]),
                                                      inputTypeB.getInputItems().toArray(new String[0])));
    }

    /**********************************************************************************************
     * Get the custom input types
     *
     * return Array containing the custom input type definitions
     *********************************************************************************************/
    protected String[][] getCustomInputTypeData()
    {
        return customInputTypes;
    }

    /**********************************************************************************************
     * Set the input types by combining the default types with those in the supplied array
     *
     * @param customInputTypes
     *            array containing the custom input type definitions
     *********************************************************************************************/
    protected void setInputTypeData(String[][] customInputTypes)
    {
        // Store the custom input type definitions
        this.customInputTypes = customInputTypes;

        // Clear the existing input type lists
        inputTypeMap.clear();
        inputTypes.clear();
        selectionInputTypes.clear();

        // Step through the default input types
        for (DefaultInputType inputType : DefaultInputType.values())
        {
            // Add the default input type to the list of input types
            inputTypes.add(new InputType(inputType.getInputName(),
                                         inputType.getInputMatch(),
                                         inputType.getInputFormat(),
                                         inputType.getInputDescription(),
                                         null));
        }

        // Set through any custom input types defined for the project
        for (String[] customType : customInputTypes)
        {
            // Set the default input type format
            InputTypeFormat inputFormat = InputTypeFormat.TEXT;

            // Step through each input type format
            for (InputTypeFormat format : InputTypeFormat.values())
            {
                // Check if the format names match (case insensitive)
                if (customType[InputTypesColumn.FORMAT.ordinal()].equals(format.name().toLowerCase()))
                {
                    // Store the input type format and stop searching
                    inputFormat = format;
                    break;
                }
            }

            // Add the custom input type to the list of input types
            inputTypes.add(new InputType(customType[InputTypesColumn.NAME.ordinal()],
                                         customType[InputTypesColumn.MATCH.ordinal()],
                                         inputFormat,
                                         customType[InputTypesColumn.DESCRIPTION.ordinal()],
                                         customType[InputTypesColumn.ITEMS.ordinal()]));
        }

        // Step through each input type (default and custom)
        for (InputType inputType : inputTypes)
        {
            // Check if the input type has an item array
            if (inputType.getInputItems() != null)
            {
                // Add the input type to the list
                selectionInputTypes.add(inputType);
            }

            // Add the input type to the map, using the name as the key (converted to lower case to
            // eliminate case sensitivity)
            inputTypeMap.put(inputType.getInputName().toLowerCase(), inputType);
        }
    }

    /**********************************************************************************************
     * Get the input type with the name that matches the one specified
     *
     * @param inputTypeName
     *            input type name to match (case insensitive)
     *
     * @return Input type with the name that matches the one specified; returns the input type for
     *         text if the input type doesn't exist
     *********************************************************************************************/
    protected InputType getInputTypeByName(String inputTypeName)
    {
        // Locate the input type in the map using its name as the key
        InputType inputType = inputTypeMap.get(inputTypeName.toLowerCase());

        // Check if the input type isn't in the map
        if (inputType == null)
        {
            // Set in input type to the default (text)
            inputType = inputTypeMap.get(DefaultInputType.TEXT.getInputName().toLowerCase());
        }

        return inputType;
    }

    /**********************************************************************************************
     * Get the input type with the name that matches that of the specified default input type
     *
     * @param defaultInputType
     *            default input type to match (DefaultInputType)
     *
     * @return Input type with the name that matches the one specified; returns the input type for
     *         text if the input type doesn't exist
     *********************************************************************************************/
    protected InputType getInputTypeByDefaultType(DefaultInputType defaultInputType)
    {
        return getInputTypeByName(defaultInputType.getInputName().toLowerCase());
    }

    /**********************************************************************************************
     * Get the list of input types that have an item array
     *
     * @return List of input types that have an item array
     *********************************************************************************************/
    protected List<InputType> getSelectionInputTypes()
    {
        return selectionInputTypes;
    }

    /**********************************************************************************************
     * Get the item array for the specified input type
     *
     * @return Item list for the specified input type
     *********************************************************************************************/
    protected List<String> getSelectionInputTypeItems(String inputTypeName)
    {
        List<String> selectionTypeItems = new ArrayList<String>(0);

        // Get the input type based on the supplied name
        InputType inputType = getInputTypeByName(inputTypeName);

        // Check if the input type exists and has an item array
        if (inputType != null)
        {
            // Store the reference to the input type's item array
            selectionTypeItems = inputType.getInputItems();
        }

        return selectionTypeItems;
    }

    /**********************************************************************************************
     * Convert the supplied input selection item string to the corresponding input match regular
     * expression
     *
     * @param itemString
     *            string containing the acceptable values for this input type, separated by the
     *            selection item list separator; null or blank if the input type doesn't constrain
     *            the inputs to items from a list
     *
     * @return Regular expression that matches only the items in the selection list
     *********************************************************************************************/
    protected String convertItemsToRegEx(String itemString)
    {
        String itemRegEx = null;

        // Convert the selection item string to a list
        List<String> itemList = InputType.convertItemList(itemString.trim());

        // Check if the regular expression was created (null is returned if the item list string is
        // empty)
        if (itemList != null)
        {
            itemRegEx = "";

            // Step through each selection item
            for (String item : itemList)
            {
                // Append the selection item to the regular expression. Each item is flagged as a
                // literal string to allow special regular expression characters in the item text
                itemRegEx += Pattern.quote(item) + "|";
            }

            // Remove the trailing '|' character
            itemRegEx = CcddUtilities.removeTrailer(itemRegEx, "|");
        }

        return itemRegEx;
    }

    /**********************************************************************************************
     * Get an array of all of the input type names, excluding separators and breaks
     *
     * @param includeSpecialTypes
     *            true to include special input types (data type, enumeration, and variable path);
     *            false to exclude
     *
     * @return Array of all of the input type names
     *********************************************************************************************/
    protected String[] getNames(boolean includeSpecialTypes)
    {
        // Create an array to hold the input type names
        List<String> inputNames = new ArrayList<String>();

        // Step through each input type
        for (InputType inputType : inputTypes)
        {
            // Check that this isn't a page format type and, if special types are to be excluded,
            // that this isn't one of those types
            if (!inputType.getInputFormat().equals(InputTypeFormat.PAGE_FORMAT)
                && (includeSpecialTypes
                    || (!inputType.getInputFormat().equals(InputTypeFormat.DATA_TYPE)
                        && !inputType.getInputFormat().equals(InputTypeFormat.ENUMERATION)
                        && !inputType.getInputFormat().equals(InputTypeFormat.VARIABLE_PATH))))
            {
                // Store the input type name in the array
                inputNames.add(inputType.getInputName());
            }
        }

        // Sort the input type names alphabetically
        Collections.sort(inputNames);

        return inputNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get an array of all of the input type descriptions, sorted based on the alphabetically
     * sorted input names, excluding separators and breaks
     *
     * @param includeSpecialTypes
     *            true to include special input types (data type and enumeration); false to exclude
     *
     * @return Array of all of the input type descriptions
     *********************************************************************************************/
    protected String[] getDescriptions(boolean includeSpecialTypes)
    {
        // Get the list of input names, sorted alphabetically
        String[] inputNames = getNames(includeSpecialTypes);

        // Create an array to hold the input type descriptions
        String[] inputDescriptions = new String[inputNames.length];

        // Step through each input type name
        for (int nameIndex = 0; nameIndex < inputNames.length; nameIndex++)
        {
            // Step through each input type
            for (int index = 0; index < inputTypes.size(); index++)
            {
                // Check if the input type names match
                if (inputNames[nameIndex].equals(inputTypes.get(index).getInputName()))
                {
                    // Store the description corresponding to this input type name and stop
                    // searching
                    inputDescriptions[nameIndex] = inputTypes.get(index).getInputDescription();
                    break;
                }
            }
        }

        return inputDescriptions;
    }

    /******************************************************************************************
     * Reformat the input value for numeric types. This adds a leading zero to floating point
     * values if the first character is a decimal, and removes '+' signs and unneeded leading
     * zeroes from integer and floating point values
     *
     * @param valueS
     *            value, represented as a string, to reformat
     *
     * @param inputFormat
     *            input type format (InputTypeFormat)
     *
     * @param preserveZeroes
     *            true to preserve leading zeroes in hexadecimal values; false to eliminate the
     *            extra zeroes (this is useful when comparing the text representation of two
     *            hexadecimal values)
     *
     * @return Input value reformatted based on its input type
     *****************************************************************************************/
    protected static String formatInput(String valueS,
                                        InputTypeFormat inputFormat,
                                        boolean preserveZeroes)
    {
        // Check that the value is not blank
        if (!valueS.isEmpty())
        {
            try
            {
                // Check if the value is an integer
                if (inputFormat.equals(InputTypeFormat.INTEGER))
                {
                    // Format the string as an integer
                    valueS = Integer.valueOf(valueS).toString();
                }
                // Check if the value is a floating point
                else if (inputFormat.equals(InputTypeFormat.FLOAT))
                {
                    // Format the string as a floating point
                    valueS = Double.valueOf(valueS).toString();
                }
                // Check if the value is in hexadecimal
                else if (inputFormat.equals(InputTypeFormat.HEXADECIMAL))
                {
                    // Set the string to append that indicates if this is a protected message ID or
                    // not
                    String protect = valueS.endsWith(PROTECTED_MSG_ID_IDENT)
                                                                             ? PROTECTED_MSG_ID_IDENT
                                                                             : "";

                    // Remove leading hexadecimal identifier if present and the protection flag if
                    // present, then convert the value to an integer (base 16)
                    String valueSTemp = valueS.replaceFirst("^0x|^0X", "")
                                              .replaceFirst("\\s*" + PROTECTED_MSG_ID_IDENT, "");
                    int value = Integer.valueOf(valueSTemp, 16);

                    // Get the leading zeroes, if any
                    String leadZeroes = valueSTemp.replaceFirst("(^0*)[a-fA-F0-9]*", "$1");

                    // Check if the value is zero
                    if (value == 0)
                    {
                        // Remove the first leading zero so it isn't duplicated, but retain any
                        // extra zeroes added by the user so these can be restored
                        leadZeroes = leadZeroes.substring(0, leadZeroes.length() - 1);
                    }

                    // Format the string as a hexadecimal, adding the hexadecimal identifier, if
                    // needed, and preserving any leading zeroes
                    valueS = String.format("0x%s%x",
                                           (preserveZeroes
                                                           ? leadZeroes
                                                           : ""),
                                           value)
                             + protect;
                }
                // Check if the value is a boolean
                else if (inputFormat.equals(InputTypeFormat.BOOLEAN))
                {
                    // Format the string as a boolean
                    valueS = valueS.toLowerCase();
                }
                // Check if the values represents array index values
                else if (inputFormat.equals(InputTypeFormat.ARRAY))
                {
                    // Remove all spaces and replace any commas with a comma and space
                    valueS = valueS.replaceAll("\\s", "").replaceAll(",", ", ");
                }
            }
            catch (Exception e)
            {
                // An error occurred formatting the supplied value as the specified format type.
                // This is only possible for the user-defined input types. Ignore the error and
                // return the input string with the formatting unchanged
            }
        }

        return valueS;
    }

    /**********************************************************************************************
     * Get the references in the table type and data field internal tables that match the specified
     * input type name. Only the columns containing input types in the table type and data field
     * internal tables are searched
     *
     * @param inputTypeName
     *            input type name for which to search
     *
     * @param parent
     *            GUI component calling this method
     *
     * @return List containing the tables in the database that reference the specified input type
     *         name; an empty array if no matches are found
     *********************************************************************************************/
    protected String[] getInputTypeReferences(String inputTypeName, Component parent)
    {
        // Get the references in the table type and data field internal tables that match the
        // specified input type name
        List<String> matches = new ArrayList<String>(Arrays.asList(dbCommand.getList(DatabaseListCommand.SEARCH,
                                                                                     new String[][] {{"_search_text_",
                                                                                                      "^"
                                                                                                                       + CcddUtilities.escapePostgreSQLReservedChars(inputTypeName)
                                                                                                                       + "$"},
                                                                                                     {"_case_insensitive_",
                                                                                                      "true"},
                                                                                                     {"_allow_regex_",
                                                                                                      "true"},
                                                                                                     {"_selected_tables_",
                                                                                                      SearchType.INPUT.toString()},
                                                                                                     {"_columns_",
                                                                                                      TableTypesColumn.INPUT_TYPE.getColumnName()
                                                                                                                   + ", "
                                                                                                                   + FieldsColumn.FIELD_TYPE.getColumnName()}},
                                                                                     parent)));

        return matches.toArray(new String[0]);
    }
}
