/**
 * CFS Command & Data Dictionary import/export interface.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.io.File;
import java.io.IOException;
import java.util.List;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.TableDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary import/export interface
 *************************************************************************************************/
public interface CcddImportExportInterface
{
    // Import type: IMPORT_ALL to import the table type, data type, and macro definitions, and the
    // data from all the table definitions; FIRST_DATA_ONLY to load only the data for the first
    // table defined
    static enum ImportType
    {
        IMPORT_ALL,
        FIRST_DATA_ONLY
    }

    /**********************************************************************************************
     * Get the status of the conversion setup error flag
     *
     * @return true if an error occurred setting up for the EDS conversion
     *********************************************************************************************/
    abstract boolean getErrorStatus();

    /**********************************************************************************************
     * Get the table definitions
     *
     * @return List of table definitions
     *********************************************************************************************/
    abstract List<TableDefinition> getTableDefinitions();

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile
     *            reference to the user-specified input file
     *
     * @param importAll
     *            ImportType.IMPORT_ALL to import the table type, data type, and macro definitions,
     *            and the data from all the table definitions; ImportType.FIRST_DATA_ONLY to load
     *            only the data for the first table defined
     *
     * @throws CCDDException
     *
     * @throws IOException
     *
     * @throws Exception
     *********************************************************************************************/
    abstract void importFromFile(File importFile, ImportType importType) throws CCDDException,
                                                                         IOException,
                                                                         Exception;

    /**********************************************************************************************
     * Export the project to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to convert
     *
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding values
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a structure table, both in
     *            application format and using the user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *
     * @param extraInfo
     *            extra parameters dependent on the export format
     *
     * @return true if an error occurred preventing exporting the project to the file
     *********************************************************************************************/
    abstract boolean exportToFile(File exportFile,
                                  String[] tableNames,
                                  boolean replaceMacros,
                                  boolean includeReservedMsgIDs,
                                  boolean includeVariablePaths,
                                  CcddVariableSizeAndConversionHandler variableHandler,
                                  String[] separators,
                                  String... extraInfo);
}
