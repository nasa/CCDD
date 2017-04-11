/**
 * CFS Command & Data Dictionary variable conversion handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import CCDD.CcddClasses.ToolTipTreeNode;
import CCDD.CcddConstants.TableTreeType;

/******************************************************************************
 * CFS Command & Data Dictionary variable conversion handler class
 *****************************************************************************/
public class CcddVariableConversionHandler
{
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

        // Create the conversion list
        createConvertedVariableNameList(allVariableNameList);
    }

    /**************************************************************************
     * Variable conversion handler class constructor for all variables in the
     * project database
     * 
     * @param variableInformation
     *            list containing variable path and name pairs
     *************************************************************************/
    CcddVariableConversionHandler(CcddMain ccddMain)
    {
        allVariableNameList = new ArrayList<String>();

        // Create a tree containing all of the variables. This is used for
        // determining bit-packing and variable relative position
        CcddTableTreeHandler allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                                        TableTreeType.INSTANCE_WITH_PRIMITIVES,
                                                                        ccddMain.getMainFrame());

        // Expand the tree so that all nodes are 'visible'
        allVariableTree.setTreeExpansion(true);

        // TODO THIS IS INCLUDING BIT LENGTHS WITH THE VARIABLES< WHICH AREN'T
        // NEEDED

        // Step through all of the nodes in the variable tree
        for (Enumeration<?> element = allVariableTree.getRootNode().preorderEnumeration(); element.hasMoreElements();)
        {
            // Get the variable name from the node
            String variable = allVariableTree.getFullVariablePath(((ToolTipTreeNode) element.nextElement()).getPath());

            // Check if a variable name exists at this node
            if (!variable.isEmpty())
            {
                // Convert the variable path to a string and add it to the
                // list, expanding any macro(s) in the variable name
                allVariableNameList.add(ccddMain.getMacroHandler().getMacroExpansion(variable));
            }
        }

        // Create the conversion list
        createConvertedVariableNameList(allVariableNameList);
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
     * Remove the data types in the supplied variable path + name, replace the
     * commas in the (which separate each structure variable in the path) with
     * the specified separator character, and replace any left brackets with
     * underscores and right brackets with blanks (in case there are any array
     * members in the path)
     * 
     * @param fullName
     *            variable path + name
     * 
     * @param separator
     *            character(s) to replace commas in the variable path + name
     * 
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     * 
     * @return Variable path + name with the data types removed, commas
     *         replaced by the separator character(s), left brackets replaced
     *         by underscores, and right brackets removed
     *************************************************************************/
    private String convertVariableName(String fullName,
                                       String separator,
                                       boolean excludeDataTypes)
    {
        // Check if data types are to be excluded
        if (excludeDataTypes)
        {
            // Remove the data types from the variable path + name
            fullName = fullName.replaceAll(",[^\\.]*\\.", ".");
        }

        return fullName.replaceAll("[,]", separator)
                       .replaceAll("[\\[]", "_")
                       .replaceAll("\\]", "");
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
     * @param separator
     *            character(s) to place between variables names
     * 
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     * 
     * @return The variable's full path and name with each variable in the path
     *         separated by the specified separator character(s); returns a
     *         blank is the row is invalid
     *************************************************************************/
    protected String getFullVariableName(String fullName,
                                         String separator,
                                         boolean excludeDataTypes)
    {
        // Check if the full variable name is provided
        if (fullName != null && !fullName.isEmpty())
        {
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
                // Convert the variable path + name using the separator
                // character(s) to separate the variables in the path
                fullName = convertVariableName(fullName,
                                               separator,
                                               excludeDataTypes);
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
            String fullName = convertVariableName(variableName, "_", false);

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
