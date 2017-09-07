/**
 * CFS Command & Data Dictionary variable conversion handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary variable conversion handler class
 *****************************************************************************/
public class CcddVariableConversionHandler
{
    // Table tree with table instances only and including primitive variables
    private CcddTableTreeHandler allVariableTree;

    // Lists that show all the original variables' full names, and the
    // variable's full name before and after converting any
    // commas and brackets to underscores. Only variable's where the converted
    // name matches another variable's are saved in the latter two lists
    private final List<String> allVariableNameList;
    private List<String> originalVariableNameList;
    private List<String> convertedVariableNameList;

    /**************************************************************************
     * Variable conversion handler class constructor for a list of supplied
     * variables
     * 
     * @param variableInformation
     *            list containing variable path and name pairs
     * 
     * @param allVariableNameList
     *            list of variable names to process
     *************************************************************************/
    CcddVariableConversionHandler(List<String> allVariableNameList)
    {
        this.allVariableNameList = allVariableNameList;
    }

    /**************************************************************************
     * Variable conversion handler class constructor for the tables and/or
     * variables in the project database as specified by the tree type, and
     * with the macros expanded and bit lengths removed based on the input flag
     * 
     * @param ccddMain
     *            main class reference
     * 
     * @param treeType
     *            type of tree contents to obtain as defined by TableTreeType
     * 
     * @param cleanName
     *            true to convert any macros in the path and to strip off the
     *            bit length, if present
     *************************************************************************/
    CcddVariableConversionHandler(CcddMain ccddMain,
                                  TableTreeType treeType,
                                  boolean cleanName)
    {
        // Create a tree containing all of the variables
        allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                   treeType,
                                                   ccddMain.getMainFrame());

        // Get the list of all variables
        allVariableNameList = allVariableTree.getTableTreePathList(null);

        // Check if the path should have the macros expanded and the bit length
        // removed
        if (cleanName)
        {
            // Step through each variable
            for (int index = 0; index < allVariableNameList.size(); index++)
            {
                // Expand any macro(s) in the variable name and remove the bit
                // length (if present)
                allVariableNameList.set(index,
                                        ccddMain.getMacroHandler().getMacroExpansion(allVariableNameList.get(index)).replaceFirst("\\:\\d+$",
                                                                                                                                  ""));
            }
        }
    }

    /**************************************************************************
     * Variable conversion handler class constructor for all variables in the
     * project database
     * 
     * @param ccddMain
     *            main class reference
     *************************************************************************/
    CcddVariableConversionHandler(CcddMain ccddMain)
    {
        this(ccddMain, TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES, true);
    }

    /**************************************************************************
     * Variable conversion handler class constructor for the tables and/or
     * variables in the project database as specified by the tree type
     * 
     * @param ccddMain
     *            main class reference
     * 
     * @param treeType
     *            type of tree contents to obtain as defined by TableTreeType
     *************************************************************************/
    CcddVariableConversionHandler(CcddMain ccddMain, TableTreeType treeType)
    {
        this(ccddMain, treeType, false);
    }

    /**************************************************************************
     * Get the reference to the table tree of instance tables, including the
     * primitive variables
     * 
     * @return Reference to the table tree of instance tables, including the
     *         primitive variables
     *************************************************************************/
    protected CcddTableTreeHandler getTableTree()
    {
        return allVariableTree;
    }

    /**************************************************************************
     * Get the list of all variable names
     * 
     * @return List of all variable names
     *************************************************************************/
    protected List<String> getAllVariableNameList()
    {
        return allVariableNameList;
    }

    /**************************************************************************
     * Retain or remove the data types in the supplied variable path + name
     * based on the input flag, replace the commas in the (which separate each
     * structure variable in the path) with the specified separator character,
     * replace any left brackets with underscores and right brackets with
     * blanks (in case there are any array members in the path), and remove the
     * bit length (if one is present)
     * 
     * @param fullName
     *            variable path + name
     * 
     * @param varPathSeparator
     *            character(s) to place between variables path members
     * 
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     * 
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     * 
     * @return Variable path + name with the data types retained or removed,
     *         commas replaced by the separator character(s), left brackets
     *         replaced by underscores, right brackets removed, and the bit
     *         length removed (if present)
     *************************************************************************/
    private String convertVariableName(String fullName,
                                       String varPathSeparator,
                                       boolean excludeDataTypes,
                                       String typeNameSeparator)
    {
        // Check if data types are to be excluded
        if (excludeDataTypes)
        {
            // Remove the data types from the variable path + name
            fullName = fullName.replaceAll(",[^\\.]*\\.", ",");
        }
        // Data types are retained
        else
        {
            // Replace the data type/variable name separator with marker
            // characters. These are used to detect and replace the data type
            // and variable name separator below, and prevents collisions
            // between the two separators and their original characters
            fullName = fullName.replaceAll("\\.", "@~~@");
        }

        return fullName.replaceAll("[,]", varPathSeparator)
                       .replaceAll("@~~@", typeNameSeparator)
                       .replaceAll("[\\[]", "_")
                       .replaceAll("\\]", "")
                       .replaceFirst("\\:\\d+$", "");
    }

    /**************************************************************************
     * Get a variable's full name which includes the variables in the structure
     * path separated by the specified separator character(s). In case there
     * are any array member variable names in the full name, replace left
     * square brackets with # underscores and remove right square brackets
     * (example: a[0],b[2] becomes a_0separatorb_2)
     * 
     * @param fullName
     *            variable path + name in the format
     *            rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2
     *            [,...]]],primitiveDataType.variable
     * 
     * @param varPathSeparator
     *            character(s) to place between variables path members
     * 
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     * 
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     * 
     * @return The variable's full path and name with each variable in the path
     *         separated by the specified separator character(s); returns a
     *         blank is the row is invalid
     *************************************************************************/
    protected String getFullVariableName(String fullName,
                                         String varPathSeparator,
                                         boolean excludeDataTypes,
                                         String typeNameSeparator)
    {
        // Check if the full variable name is provided
        if (fullName != null && !fullName.isEmpty())
        {
            int index = -1;

            // Check if the separator character is an underscore and that data
            // types are to be removed
            if (varPathSeparator.equals("_") && excludeDataTypes == true)
            {
                // Check if the conversion list hasn't been created already
                if (originalVariableNameList == null)
                {
                    // Create the conversion list. The conversion list is
                    // needed since it's possible that duplicate variable path
                    // + names can occur if underscores are part of the names.
                    // The lists ensure that no duplicate is returned; instead,
                    // a unique name is created by appending one or more
                    // underscores to the otherwise duplicate name
                    createConvertedVariableNameList(allVariableNameList);
                }

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

                // Check if data types are to be excluded
                if (excludeDataTypes)
                {
                    // Remove the data types from the variable path + name
                    fullName = fullName.replaceAll(",[^\\.]*\\.", ",");
                }

            }
            // The separator character isn't an underscore or the variable
            // name isn't in the list
            else
            {
                // Convert the variable path + name using the separator
                // character(s) to separate the variables in the path
                fullName = convertVariableName(fullName,
                                               varPathSeparator,
                                               excludeDataTypes,
                                               typeNameSeparator);
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
     * 
     * @param allVariableNameList
     *            list of variable names to process
     *************************************************************************/
    private void createConvertedVariableNameList(List<String> allVariableNameList)
    {
        originalVariableNameList = new ArrayList<String>();
        convertedVariableNameList = new ArrayList<String>();

        // Step through each variable
        for (String variableName : allVariableNameList)
        {
            // Convert the variable path + name using underscores to separate
            // the variables in the path, and retain the data types
            String fullName = convertVariableName(variableName, "_", false, ".");

            // Compare the converted variable name to those already added to
            // the list
            while (convertedVariableNameList.contains(fullName))
            {
                // A matching name already exists; append an underscore to this
                // variable's name
                fullName += "_";
            }

            // Add the variable name to the converted variable name list
            convertedVariableNameList.add(fullName);
        }

        // Step through the converted variable name list
        for (int index = convertedVariableNameList.size() - 1; index >= 0; index--)
        {
            // Check if this variable isn't one that is modified
            if (!convertedVariableNameList.get(index).endsWith("_"))
            {
                // Remove the variable from the list. This shortens the list
                // and allows all other variables to have their full name built
                // "on-the-fly"
                convertedVariableNameList.remove(index);
            }
            // This variable was modified
            else
            {
                // Add the variable to the list (add at the beginning to keep
                // the order consistent with the converted list)
                originalVariableNameList.add(0, convertedVariableNameList.get(index));
            }
        }
    }
}
