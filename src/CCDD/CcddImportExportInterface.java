/**
 * CFS Command and Data Dictionary import/export interface.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.io.IOException;
import java.util.List;

import javax.xml.bind.JAXBException;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary import/export interface
 *************************************************************************************************/
public interface CcddImportExportInterface {
    // Import type: IMPORT_ALL to import the table type, data type, and macro
    // definitions, and the
    // data from all the table definitions; FIRST_DATA_ONLY to load only the data
    // for the first
    // table defined
    static enum ImportType {
        IMPORT_ALL, FIRST_DATA_ONLY
    }

    /**********************************************************************************************
     * Get the imported table definitions
     *
     * @return List of imported table definitions; an empty list if no table
     *         definitions exist in the import file
     *********************************************************************************************/
    abstract List<TableDefinition> getTableDefinitions();

    /**********************************************************************************************
     * Get the list of original and new script associations
     *
     * @return List of original and new script associations; null if no new
     *         associations have been added
     *********************************************************************************************/
    abstract List<String[]> getScriptAssociations();

    /**********************************************************************************************
     * Get the list of original and new telemetry scheduler messages
     *
     * @return List of original and new telemetry scheduler messages; null if no
     *         telemetry scheduler messages have been added
     * 
     *********************************************************************************************/
    abstract List<String[]> getTlmSchedulerData();

    /**********************************************************************************************
     * Get the list of original and new application scheduler data
     *
     * @return List of original and new application scheduler data; null if no new
     *         data has been added
     *********************************************************************************************/
    abstract List<String[]> getAppSchedulerData();

    /**********************************************************************************************
     * Build the information from the internal table in the current file
     *
     * @param importFile   import file reference
     *
     * @param importType   ImportType.IMPORT_ALL to import the table type, data
     *                     type, and macro definitions, and the data from all the
     *                     table definitions; ImportType.FIRST_DATA_ONLY to load
     *                     only the data for the first table defined
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     * 
     * @param replaceExistingAssociations true to overwrite internal associations with
     *                                    those from the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void importInternalTables(FileEnvVar importFile, ImportType importType, boolean ignoreErrors, boolean replaceExistingAssociations)
            throws CCDDException, IOException, Exception;

    /**********************************************************************************************
     * Build the information from the input and data type definition(s) in the
     * current file
     *
     * @param importFile   import file reference
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     * 
     * @param replaceExistingMacros true to replace existing macros
     * 
     * @param replaceExistingTables true to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void importTableInfo(FileEnvVar importFile, ImportType importType, boolean ignoreErrors,
            boolean replaceExistingMacros, boolean replaceExistingTables)
            throws CCDDException, IOException, Exception;

    /**********************************************************************************************
     * Build the information from the input and data type definition(s) in the
     * current file
     *
     * @param importFile   import file reference
     * 
     * @param ignoreErrors true to ignore all errors in the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void importInputTypes(FileEnvVar importFile, ImportType importType, boolean ignoreErrors)
            throws CCDDException, IOException, Exception;

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile            reference to the user-specified input file
     *
     * @param importType            ImportType.IMPORT_ALL to import the table type,
     *                              data type, and macro definitions, and the data
     *                              from all the table definitions;
     *                              ImportType.FIRST_DATA_ONLY to load only the data
     *                              for the first table defined
     *
     * @param targetTypeDefn        table type definition of the table in which to
     *                              import the data; ignored if importing all tables
     *
     * @param ignoreErrors          true to ignore all errors in the import file
     *
     * @param replaceExistingMacros true to replace the values for existing macros
     *
     * @param replaceExistingGroups true to replace existing group definitions
     * 
     * @param replaceExistingTables true to replace existing tables or table fields
     *
     * @throws CCDDException If data is missing, extraneous, or an error in the
     *                       import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void importFromFile(FileEnvVar importFile, ImportType importType, TypeDefinition targetTypeDefn,
            boolean ignoreErrors, boolean replaceExistingMacros, boolean replaceExistingGroups, boolean replaceExistingTables)
            throws CCDDException, IOException, Exception;

    /**********************************************************************************************
     * Export each table in the project to the specified file
     *
     * @param exportFile              reference to the user-specified output file
     *
     * @param tableNames              array of table names to convert
     *
     * @param includeBuildInformation true to include the CCDD version, project,
     *                                host, and user information
     *
     * @param replaceMacros           true to replace any embedded macros with their
     *                                corresponding values
     *
     * @param includeReservedMsgIDs   true to include the contents of the reserved
     *                                message ID table in the export file
     *
     * @param includeProjectFields    true to include the project-level data field
     *                                definitions in the export file
     *
     * @param includeVariablePaths    true to include the variable path for each
     *                                variable in a structure table, both in
     *                                application format and using the user-defined
     *                                separator characters
     *
     * @param variableHandler         variable handler class reference; null if
     *                                includeVariablePaths is false
     *
     * @param separators              string array containing the variable path
     *                                separator character(s), show/hide data types
     *                                flag ('true' or 'false'), and data
     *                                type/variable name separator character(s);
     *                                null if includeVariablePaths is false
     *
     * @param extraInfo               extra parameters dependent on the export
     *                                format
     *
     * @throws JAXBException If an error occurs marshaling the project
     *
     * @throws CCDDException If an error occurs executing an external (script)
     *                       method
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void exportTables(FileEnvVar exportFile, String[] tableNames, boolean includeBuildInformation,
            boolean replaceMacros, boolean includeReservedMsgIDs, boolean includeProjectFields,
            boolean includeVariablePaths, CcddVariableHandler variableHandler, String[] separators, String outputType,
            Object... extraInfo)
            throws JAXBException, CCDDException, Exception;
    
    /**********************************************************************************************
     * Export table type definitions to the specified folder
     * 
     * @param exportFile        reference to the user-specified output file
     * 
     * @param includeTableTypes Boolean representing if the table types should be
     *                          included
     * 
     * @param includeInputTypes Boolean representing if the input types should be
     *                          included
     * 
     * @param includeDataTypes  Boolean representing if the data types should be
     *                          included
     * 
     * @param outputType        String representing rather the output is going to a
     *                          single file or multiple files. Should be "Single" or
     *                          "Multiple"
     * 
     * @throws CCDDException If a file I/O or parsing error occurs
     * 
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void exportTableInfoDefinitions(FileEnvVar exportFile, boolean includeTableTypes,
            boolean includeInputTypes, boolean includeDataTypes, String outputType) throws CCDDException, Exception;
    
    /**********************************************************************************************
     * Export script association data, group data, macro data, telemetry scheduler
     * data or application scheduler data to the specified folder
     *
     * @param dataType   the data type that is about to be exported
     * 
     * @param exportFile reference to the user-specified output file
     * 
     * @param outputType String representing rather the output is going to a single
     *                   file or multiple files. Should be "Single" or "Multiple"
     * 
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
     * 
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    abstract void exportInternalCCDDData(boolean[] includes, CcddConstants.exportDataTypes[] dataTypes, FileEnvVar exportFile,
            String outputType) throws CCDDException, Exception;
}
