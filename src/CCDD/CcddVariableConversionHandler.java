/**
 * CFS Command & Data Dictionary variable conversion handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.TABLE_DESCRIPTION_SEPARATOR;

import java.util.ArrayList;
import java.util.List;

import CCDD.CcddConstants.DatabaseListCommand;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.ValuesColumn;
import CCDD.CcddConstants.SearchResultsQueryColumn;
import CCDD.CcddConstants.SearchType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary variable conversion handler class
 *************************************************************************************************/
public class CcddVariableConversionHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private final CcddTableTypeHandler tableTypeHandler;

    // Table tree with table instances only and including primitive variables
    private CcddTableTreeHandler allVariableTree;

    // Lists containing all of the program-formatted variable paths, and their corresponding name
    // after converting any commas and brackets based on the specified separator characters
    private List<String> programFormatVariablePath;
    private List<String> convertedVariableName;

    // Lists containing the program-formatted variable paths (key), and their corresponding
    // user-defined names. Only variable paths that have user-defined names are included in these
    // lists
    private List<String> userDefinedVariablePathKey;
    private List<String> userDefinedVariableName;

    /**********************************************************************************************
     * Variable conversion handler class constructor for the tables and/or variables in the project
     * database as specified by the tree type, and with the macros expanded and bit lengths removed
     * based on the input flag
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddVariableConversionHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbCommand = ccddMain.getDbCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();

        // Initialize the variable tree and lists
        initializeVariableConversion();
    }

    /**********************************************************************************************
     * Get the reference to the table tree of instance structure tables, including the primitive
     * variables
     *
     * @return Reference to the table tree of instance structure tables, including the primitive
     *         variables
     *********************************************************************************************/
    protected CcddTableTreeHandler getVariableTree()
    {
        return allVariableTree;
    }

    /**********************************************************************************************
     * Get the list of all variable names
     *
     * @return List of all variable names
     *********************************************************************************************/
    protected List<String> getAllVariableNames()
    {
        return programFormatVariablePath;
    }

    /**********************************************************************************************
     * Initialize the variable tree and lists
     *********************************************************************************************/
    protected void initializeVariableConversion()
    {
        convertedVariableName = null;
        userDefinedVariablePathKey = new ArrayList<String>();
        userDefinedVariableName = new ArrayList<String>();

        // Create a tree containing all of the variables
        allVariableTree = new CcddTableTreeHandler(ccddMain,
                                                   TableTreeType.INSTANCE_STRUCTURES_WITH_PRIMITIVES,
                                                   ccddMain.getMainFrame());

        // Get the list of all variables
        programFormatVariablePath = allVariableTree.getTableTreePathList(null);

        // Step through each variable
        for (int index = 0; index < programFormatVariablePath.size(); index++)
        {
            // Expand any macro(s) in the variable name and remove the bit length (if present)
            programFormatVariablePath.set(index,
                                          ccddMain.getMacroHandler()
                                                  .getMacroExpansion(programFormatVariablePath.get(index))
                                                  .replaceFirst("\\:\\d+$", ""));
        }
    }

    /**********************************************************************************************
     * Retain or remove the data types in the supplied variable path + name based on the input
     * flag, replace the commas in the (which separate each structure variable in the path) with
     * the specified separator character, replace any left brackets with underscores and right
     * brackets with blanks (in case there are any array members in the path), and remove the bit
     * length (if one is present)
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
     * @return Variable path + name with the data types retained or removed, commas replaced by the
     *         separator character(s), left brackets replaced by underscores, right brackets
     *         removed, and the bit length removed (if present)
     *********************************************************************************************/
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
            // Replace the data type/variable name separator with marker characters. These are used
            // to detect and replace the data type and variable name separator below, and prevents
            // collisions between the two separators and their original characters
            fullName = fullName.replaceAll("\\.", "@~~@");
        }

        return fullName.replaceAll("[,]", varPathSeparator)
                       .replaceAll("@~~@", typeNameSeparator)
                       .replaceAll("[\\[]", "_")
                       .replaceAll("\\]", "")
                       .replaceFirst("\\:\\d+$", "");
    }

    /**********************************************************************************************
     * Determine if the supplied variable path is already in use in a structure
     *
     * @param progDefinedPath
     *            variable path + name in the application's internal format
     *
     * @param alternateName
     *            variable path converted using the separator characters, or a user-defined name
     *
     * @return true if the supplied variable path is already in use in a structure
     *********************************************************************************************/
    protected boolean isVariablePathInUse(String progDefinedPath, String alternateName)
    {
        boolean isInUse = false;

        // Locate the alternate name in the list of user-defined variable names
        int index = userDefinedVariableName.indexOf(alternateName);

        // Check if the name was found (i.e., the name matches one manually set by the user)
        if (index != -1)
        {
            // Check if the supplied program-formatted path doesn't correspond with of the
            // user-defined name - if the program-formatted path is the 'key' for the user-defined
            // name then it's the legitimate owner and doesn't constitute a duplicate reference
            if (index != userDefinedVariablePathKey.indexOf(progDefinedPath))
            {
                // Set the flag to indicate the user-defined name is already in use
                isInUse = true;
            }
        }
        // The supplied alternate name is not a user-defined name
        else
        {
            // Locate the alternate name in the list of program-converted variable names
            index = convertedVariableName.indexOf(alternateName);

            // Check if the name was found (i.e., the name matches one manually generated by the
            // program)
            if (index != -1)
            {
                // Check if the supplied program-formatted path doesn't correspond with of the
                // converted name - if the program-formatted path is the 'key' for the converted
                // name then it's the legitimate owner and doesn't constitute a duplicate reference
                if (index != programFormatVariablePath.indexOf(progDefinedPath))
                {
                    // Set the flag to indicate the converted name is already in use
                    isInUse = true;
                }
            }
        }

        return isInUse;
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s). In case there are any array member variable names in
     * the full name, replace left square brackets with # underscores and remove right square
     * brackets (example: a[0],b[2] becomes a_0separatorb_2)
     *
     * @param fullName
     *            variable path + name in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2 [,...]]],primitiveDataType.variable
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
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s); if a user-defined path exists then it is returned
     *         in place of the auto-generated one. Returns a blank if fullName is null or empty
     *********************************************************************************************/
    protected String getFullVariableName(String fullName,
                                         String varPathSeparator,
                                         boolean excludeDataTypes,
                                         String typeNameSeparator)
    {
        return getFullVariableName(fullName,
                                   varPathSeparator,
                                   excludeDataTypes,
                                   typeNameSeparator,
                                   true);
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s). In case there are any array member variable names in
     * the full name, replace left square brackets with # underscores and remove right square
     * brackets (example: a[0],b[2] becomes a_0separatorb_2)
     *
     * @param fullName
     *            variable path + name in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2 [,...]]],primitiveDataType.variable
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
     * @param includeCustom
     *            true to substitute the user-defined variable name (if present); false to ignore
     *            the user-defined name and use the auto-generated one based on the conversion
     *            flags
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s); returns a blank if fullName is null or empty
     *********************************************************************************************/
    protected String getFullVariableName(String fullName,
                                         String varPathSeparator,
                                         boolean excludeDataTypes,
                                         String typeNameSeparator,
                                         boolean includeCustom)
    {
        String convertedFullName = "";

        // Check if the full variable name is provided
        if (fullName != null && !fullName.isEmpty())
        {
            // Check if the conversion list hasn't been created already
            if (convertedVariableName == null)
            {
                // Create the conversion list. The conversion list is needed since it's possible
                // that duplicate variable path + names can occur if underscores are part of the
                // names. The lists ensure that no duplicate is returned; instead, a unique name is
                // created by appending one or more underscores to the otherwise duplicate name.
                // Variable paths that are explicitly defined by the user are then added to the
                // lists
                createVariableNameList(varPathSeparator,
                                       excludeDataTypes,
                                       typeNameSeparator);
            }

            // Check if the user-defined variable name should be substituted, if present
            if (includeCustom)
            {
                // Get the index of the variable name from the list of original names
                int index = userDefinedVariablePathKey.indexOf(fullName);

                // Check if the variable name was extracted from the list
                if (index != -1)
                {
                    // Get the converted variable name for this variable. This name has one or more
                    // underscores appended since it would otherwise duplicate another variable's
                    // name
                    convertedFullName = userDefinedVariableName.get(index);
                }
            }

            if (convertedFullName.isEmpty())
            {
                // Get the index of the variable path from the list of program-formatted names
                int index = programFormatVariablePath.indexOf(fullName);

                // Check if the variable name was extracted from the list
                if (index != -1)
                {
                    // Get the converted variable name for this variable. This name has one or more
                    // underscores appended since it would otherwise duplicate another variable's
                    // name
                    convertedFullName = convertedVariableName.get(index);

                    // Check if data types are to be excluded
                    if (excludeDataTypes)
                    {
                        // Remove the data types from the variable path + name
                        convertedFullName = convertedFullName.replaceAll(",[^\\.]*\\.", ",");
                    }
                }
                // The separator character isn't an underscore or the variable name isn't in the
                // list
                else
                {
                    // Convert the variable path + name using the separator character(s) to
                    // separate the variables in the path
                    convertedFullName = convertVariableName(fullName,
                                                            varPathSeparator,
                                                            excludeDataTypes,
                                                            typeNameSeparator);
                }
            }
        }

        return convertedFullName;
    }

    /**********************************************************************************************
     * Create a pair of lists that show a variable's full name before and after converting any
     * commas and brackets to underscores. Check if duplicate variable names result from the
     * conversion; if a duplicate is found append an underscore to the duplicate's name. Once all
     * variable names are processed trim the list to include only those variables that are modified
     * to prevent a duplicate. These lists are used by getFullVariableName() so that it always
     * returns a unique name
     *
     * @param varPathSeparator
     *            character(s) to place between variables path members
     *
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     *
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     *********************************************************************************************/
    private void createVariableNameList(String varPathSeparator,
                                        boolean excludeDataTypes,
                                        String typeNameSeparator)
    {
        String varPathColumnsDb = "";
        String varPathColumnsUser = "";
        convertedVariableName = new ArrayList<String>();

        // Step through each variable
        for (String variableName : programFormatVariablePath)
        {
            // Convert the variable path + name using underscores to separate the variables in the
            // path, and retain the data types
            String fullName = convertVariableName(variableName,
                                                  varPathSeparator,
                                                  excludeDataTypes,
                                                  typeNameSeparator);

            // Compare the converted variable name to those already added to the list
            while (convertedVariableName.contains(fullName))
            {
                // A matching name already exists; append an underscore to this variable's name
                fullName += "_";
            }

            // Add the variable name to the converted variable name list
            convertedVariableName.add(fullName);
        }

        // Step through each table type definition
        for (TypeDefinition typeDefn : ccddMain.getTableTypeHandler().getTypeDefinitions())
        {
            // Check if the table type represents a structure
            if (typeDefn.isStructure())
            {
                // Get the index of the column containing the variable path
                int varPathIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE_PATH);

                // Check if the variable path column is present
                if (varPathIndex != -1)
                {
                    // Append the database and user column names to the search criteria
                    varPathColumnsDb += typeDefn.getColumnNamesDatabase()[varPathIndex] + ",";
                    varPathColumnsUser += ValuesColumn.COLUMN_NAME.getColumnName()
                                          + " = '"
                                          + typeDefn.getColumnNamesUser()[varPathIndex]
                                          + "' OR ";
                }
            }
        }

        // Remove the unneeded trailing text
        varPathColumnsDb = CcddUtilities.removeTrailer(varPathColumnsDb, ",");
        varPathColumnsUser = CcddUtilities.removeTrailer(varPathColumnsUser, " OR ");

        // Get the references in the prototype tables that contain user-defined (i.e., non-blank)
        // variable paths. This accounts for root tables with user-defined paths
        String[] matches = dbCommand.getList(DatabaseListCommand.SEARCH,
                                             new String[][] {{"_search_text_",
                                                              ".+"},
                                                             {"_case_insensitive_",
                                                              "false"},
                                                             {"_allow_regex_",
                                                              "true"},
                                                             {"_selected_tables_",
                                                              SearchType.DATA.toString()},
                                                             {"_columns_",
                                                              varPathColumnsDb}},
                                             ccddMain.getMainFrame());

        // Step through each variable path
        for (String match : matches)
        {
            // Split the reference into table name, column name, table type, and context
            String[] tblColDescAndCntxt = match.split(TABLE_DESCRIPTION_SEPARATOR, 4);

            // Create a reference to the search result's database table name and row data to
            // shorten comparisons below
            String[] rowData = CcddUtilities.splitAndRemoveQuotes(tblColDescAndCntxt[SearchResultsQueryColumn.CONTEXT.ordinal()]);

            // Set the viewable table name (with capitalization intact)
            String[] tableNameAndType = tblColDescAndCntxt[SearchResultsQueryColumn.COMMENT.ordinal()].split(",", 2);

            // Get the table's type definition and from that the variable name, data type, and
            // variable path column indices
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableNameAndType[1]);
            int variableNameIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
            int dataTypeIndex = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
            int variablePathIndex = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE_PATH);

            // Add the variable path to the lists (program- and user-defined)
            userDefinedVariablePathKey.add(tableNameAndType[0]
                                           + ","
                                           + rowData[dataTypeIndex]
                                           + "."
                                           + rowData[variableNameIndex]);
            userDefinedVariableName.add(rowData[variablePathIndex]);
        }

        // Get the references in the custom values table for all user-defined variable paths. This
        // accounts for child tables with user-defined paths
        matches = dbCommand.getList(DatabaseListCommand.VAR_PATH,
                                    new String[][] {{"_match_column_name_",
                                                     varPathColumnsUser}},
                                    ccddMain.getMainFrame());

        // Step through each variable path
        for (String match : matches)
        {
            // Split the reference into table name and variable path
            String[] tableNameAndPath = match.split(TABLE_DESCRIPTION_SEPARATOR, 2);

            // Add the variable path to the lists (program- and user-defined)
            userDefinedVariablePathKey.add(tableNameAndPath[0]);
            userDefinedVariableName.add(tableNameAndPath[1]);
        }
    }
}
