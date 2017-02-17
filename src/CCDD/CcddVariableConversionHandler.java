/**
 * CFS Command & Data Dictionary variable conversion handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.List;

/******************************************************************************
 * CFS Command & Data Dictionary variable conversion handler class
 *****************************************************************************/
public class CcddVariableConversionHandler
{
    private final CcddMacroHandler macroHandler;

    // Lists that show a variable's full name before and after converting any
    // commas and brackets to underscores. Only variable's where the converted
    // name matches another variable's are saved in the lists
    private List<String> originalVariableNameList;
    private List<String> convertedVariableNameList;

    /**************************************************************************
     * Variable conversion handler class constructor
     * 
     * @param variableInformation
     *            list containing variable path and name pairs
     * 
     * @param macroHandler
     *            macro handler class reference
     *************************************************************************/
    CcddVariableConversionHandler(List<String[]> variableInformation,
                                  CcddMacroHandler macroHandler)
    {
        this.macroHandler = macroHandler;

        // Create the conversion list
        createConvertedVariableNameList(variableInformation);
    }

    /**************************************************************************
     * Get a variable's full name which includes the variables in the structure
     * path separated by the specified separator character(s). In case there
     * are any array member variable names in the full name, replace left
     * square brackets with # underscores and remove right square brackets
     * (example: a[0],b[2] becomes a_0separatorb_2)
     * 
     * @param variablePath
     *            variable path in the format
     *            rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2[,...]]]
     * 
     * @param variableName
     *            variableName in the format primitiveDataType.variable
     * 
     * @param separator
     *            character(s) to place between variables names
     * 
     * @return The variable's full path and name with each variable in the path
     *         separated by the specified separator character(s); returns a
     *         blank is the row is invalid
     *************************************************************************/
    public String getFullVariableName(String variablePath,
                                      String variableName,
                                      String separator)
    {
        String fullName = "";

        // Check that the path and name are not blank
        if (!variablePath.isEmpty()
            && variableName != null
            && !variableName.isEmpty())
        {
            // Create the full name by prepending the path to the variable
            // name
            fullName = variablePath + "," + variableName;

            int index = -1;

            // Check if the separator character is an underscore
            if (separator.equals("_"))
            {
                // Get the index of the variable name from the list of
                // original names
                index = originalVariableNameList.indexOf(fullName);
            }

            // Check if the variable name was extracted from the list
            if (index != -1)
            {
                // Get the converted variable name for this variable. This
                // name has one or more underscores appended since it would
                // otherwise duplicate another variable's name
                fullName = convertedVariableNameList.get(index);
            }
            // The separator character isn't an underscore or the variable
            // name isn't in the list
            else
            {
                // Replace the commas in the path, which separate each
                // structure variable in the path, with underscores.
                // Replace any left brackets with underscores and right
                // brackets with blanks (in case there are any array
                // members in the path)
                fullName = fullName.replaceAll("[,\\[]",
                                               separator).replaceAll("\\]", "");
            }
        }

        return fullName;
    }

    /**************************************************************************
     * Create a pair of lists that show a variable's full name before and after
     * converting any commas and brackets to underscores. Check if duplicate
     * variable names result from the conversion; if a duplicate is found
     * append an underscore to the duplicate's name. Once all variable names
     * are processed trim the list to include only those variables that are
     * modified to prevent a duplicate. These lists are used by
     * getFullVariableName() so that it always returns a unique name
     *************************************************************************/
    private void createConvertedVariableNameList(List<String[]> variableInformation)
    {
        // Check if the lists aren't already created
        if (convertedVariableNameList == null)
        {
            originalVariableNameList = new ArrayList<String>();
            convertedVariableNameList = new ArrayList<String>();

            // Step through each variable
            for (String[] variableInfo : variableInformation)
            {
                // Get the variable path and name for this row
                String variablePath = variableInfo[0];
                String variableName = macroHandler.getMacroExpansion(variableInfo[1]);

                // Check that the path and name are not null or blank
                if (variablePath != null
                    && !variablePath.isEmpty()
                    && variableName != null
                    && !variableName.isEmpty())
                {
                    // Create the full name by prepending the path to the
                    // variable name
                    String fullName = variablePath + "," + variableName;

                    // Add the full variable name to the original variable name
                    // list
                    originalVariableNameList.add(fullName);

                    // Replace the commas in the path, which separate each
                    // structure variable in the path, with underscores.
                    // Replace any left brackets with underscores and right
                    // brackets with blanks (in case there are any array
                    // members in the path)
                    fullName = fullName.replaceAll("[,\\[]", "_").replaceAll("\\]", "");

                    // Compare the converted variable name to those already
                    // added to the list
                    while (convertedVariableNameList.contains(fullName))
                    {
                        // A matching name already exists; append an underscore
                        // to this variable's name
                        fullName += "_";
                    }

                    // Add the variable name to the converted variable name
                    // list
                    convertedVariableNameList.add(fullName);
                }
            }

            // Step through the converted variable name list
            for (int index = convertedVariableNameList.size() - 1; index >= 0; index--)
            {
                // Check if this variable isn't one that is modified
                if (!convertedVariableNameList.get(index).endsWith("_"))
                {
                    // Remove the variable from the list. This shortens the
                    // list and allows all other variables to have their full
                    // name built "on-the-fly"
                    originalVariableNameList.remove(index);
                    convertedVariableNameList.remove(index);
                }
            }
        }
    }
}
