/**
 * CFS Command & Data Dictionary input type handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.PROTECTED_MSG_ID_IDENT;
import static CCDD.CcddConstants.SELECTION_ITEM_LIST_SEPARATOR;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final CcddDbCommandHandler dbCommand;

    // List of input types, both default and custom ones defined by the user
    private final List<InputType> inputTypes;

    // Array containing the custom input type information. This is used by the input type editor
    private String[][] customInputTypes;

    // Map used to locate an input type based on its name as the key
    private final Map<String, InputType> inputTypeMap;

    /**********************************************************************************************
     * Input type class
     *********************************************************************************************/
    protected class InputType
    {
        private final String inputName;
        private final String inputMatch;
        private final InputTypeFormat inputFormat;
        private final String inputDescription;
        private final String[] inputItems;

        /******************************************************************************************
         * Input type class constructor
         *
         * @param inputName
         *            input type name
         *
         * @param inputMatch
         *            regular expression match for the input type
         *
         * @param inputFormat
         *            input type format
         *
         * @param inputDescription
         *            input type description
         *
         * @param inputItems
         *            array of acceptable values for this input type; null if the input type
         *            doesn't constrain the inputs to items form a list. The list is used to create
         *            the contents of the combo box in the table column with this input type
         *****************************************************************************************/
        InputType(String inputName,
                  String inputMatch,
                  InputTypeFormat inputFormat,
                  String inputDescription,
                  String[] inputItems)
        {
            this.inputName = inputName;
            this.inputMatch = inputMatch;
            this.inputFormat = inputFormat;
            this.inputDescription = inputDescription;
            this.inputItems = inputItems;
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
         * Get the input type matching regular expression
         *
         * @return Input type matching regular expression
         *****************************************************************************************/
        protected String getInputMatch()
        {
            return inputMatch;
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
         * Get the input type description
         *
         * @return Input type description
         *****************************************************************************************/
        protected String getInputDescription()
        {
            return inputDescription;
        }

        /******************************************************************************************
         * Get the input type items
         *
         * @return Input type items
         *****************************************************************************************/
        protected String[] getInputItems()
        {
            return inputItems;
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
    }

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

        // Clear the existing input type list
        inputTypes.clear();

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
                                         customType[InputTypesColumn.ITEMS.ordinal()].split(SELECTION_ITEM_LIST_SEPARATOR)));
        }

        // Step through each input type (default and custom)
        for (InputType inputType : inputTypes)
        {
            // Add the input type to the map, using the name as the key (converted to lower case to
            // eliminate case sensitivity)
            inputTypeMap.put(inputType.inputName.toLowerCase(), inputType);
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
     * Get the list of input types that have an item array
     *
     * @return List of input types that have an item array
     *********************************************************************************************/
    protected List<InputType> getSelectionInputTypes()
    {
        List<InputType> selectionInputTypes = new ArrayList<InputType>();

        // Step through the input types
        for (InputType inputType : inputTypes)
        {
            // Check if the input type has an item array
            if (inputType.getInputItems() != null)
            {
                // Add the input type to the list
                selectionInputTypes.add(inputType);
            }
        }

        return selectionInputTypes;
    }

    /**********************************************************************************************
     * Get the item array for the specified input type
     *
     * @return Item array for the specified input type
     *********************************************************************************************/
    protected String[] getSelectionInputTypeItems(String inputTypeName)
    {
        String[] selectionTypeItems = new String[0];

        // Get the input type based on the supplied name
        InputType inputType = getInputTypeByName(inputTypeName);

        // Check if the input type exists and has an item array
        if (inputType != null && inputType.getInputItems() != null)
        {
            // STore the reference to the input type's item array
            selectionTypeItems = inputType.getInputItems();
        }

        return selectionTypeItems;
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
                valueS = valueS.replaceFirst("^0x|^0X", "").replaceFirst("\\s*" + PROTECTED_MSG_ID_IDENT, "");
                int value = Integer.valueOf(valueS, 16);

                // Get the leading zeroes, if any
                String leadZeroes = valueS.replaceFirst("(^0*)[a-fA-F0-9]*", "$1");

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

        return valueS;
    }

    /**********************************************************************************************
     * Get a list containing the tables in the project database that reference the specified input
     * type name. Only the columns containing input types in the table type and data field internal
     * tables are searched
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
