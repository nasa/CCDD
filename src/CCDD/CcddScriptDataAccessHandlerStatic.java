/**
 * CFS Command and Data Dictionary static script data access handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.io.PrintWriter;

import org.omg.space.xtce.BaseDataType.UnitSet;
import org.omg.space.xtce.EntryListType;
import org.omg.space.xtce.EnumeratedDataType.EnumerationList;
import org.omg.space.xtce.NameDescriptionType;
import org.omg.space.xtce.SpaceSystemType;

import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddImportSupportHandler.BasePrimitiveDataType;

/**************************************************************************************************
 * CFS Command and Data Dictionary static script data access class. This class contains static
 * public methods that are accessible to the data output scripts
 *************************************************************************************************/
public class CcddScriptDataAccessHandlerStatic
{
    private static CcddScriptDataAccessHandler accessHandler;

    /**********************************************************************************************
     * Static script data access class constructor
     *
     * @param accessHandler
     *            reference to the script data access handler (non-static)
     *********************************************************************************************/
    CcddScriptDataAccessHandlerStatic(CcddScriptDataAccessHandler accessHandler)
    {
        CcddScriptDataAccessHandlerStatic.accessHandler = accessHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Following are the static calls to the non-static versions of the public access methods
    ///////////////////////////////////////////////////////////////////////////////////////////////

    public static String getScriptName()
    {
        return accessHandler.getScriptName();
    }

    public static String getUser()
    {
        return accessHandler.getUser();
    }

    public static String getDatabase()
    {
        return accessHandler.getDatabase();
    }

    public static String getProject()
    {
        return accessHandler.getProject();
    }

    public static String getProjectDescription()
    {
        return accessHandler.getProjectDescription();
    }

    public static String getOutputPath()
    {
        return accessHandler.getOutputPath();
    }

    public static Integer getLongestString(String[] strgArray, Integer minWidth)
    {
        return accessHandler.getLongestString(strgArray, minWidth);
    }

    public static Integer[] getLongestStrings(String[][] strgArray, Integer[] minWidths)
    {
        return accessHandler.getLongestStrings(strgArray, minWidths);
    }

    public static String getDateAndTime()
    {
        return accessHandler.getDateAndTime();
    }

    public static String[][] getDataTypeDefinitions()
    {
        return accessHandler.getDataTypeDefinitions();
    }

    public static boolean isDataTypePrimitive(String dataType)
    {
        return accessHandler.isDataTypePrimitive(dataType);
    }

    public static boolean isDataTypeInteger(String dataType)
    {
        return accessHandler.isDataTypeInteger(dataType);
    }

    public static boolean isDataTypeUnsignedInt(String dataType)
    {
        return accessHandler.isDataTypeUnsignedInt(dataType);
    }

    public static boolean isDataTypeFloat(String dataType)
    {
        return accessHandler.isDataTypeFloat(dataType);
    }

    public static boolean isDataTypeCharacter(String dataType)
    {
        return accessHandler.isDataTypeCharacter(dataType);
    }

    public static boolean isDataTypeString(String dataType)
    {
        return accessHandler.isDataTypeString(dataType);
    }

    public static String getCDataType(String dataType)
    {
        return accessHandler.getCDataType(dataType);
    }

    public static String getBaseDataType(String dataType)
    {
        return accessHandler.getBaseDataType(dataType);
    }

    public static int getDataTypeSizeInBytes(String dataType)
    {
        return accessHandler.getDataTypeSizeInBytes(dataType);
    }

    public static int getDataTypeSizeInBits(String dataType)
    {
        return accessHandler.getDataTypeSizeInBits(dataType);
    }

    public static String getITOSEncodedDataType(String dataType, String encoding)
    {
        return accessHandler.getITOSEncodedDataType(dataType, encoding);
    }

    public static String getITOSLimitName(int index)
    {
        return accessHandler.getITOSLimitName(index);
    }

    public static String[] getRootStructureTableNames()
    {
        return accessHandler.getRootStructureTableNames();
    }

    public static String[] getRootTableNames(String tableType)
    {
        return accessHandler.getRootTableNames(tableType);
    }

    public static int getStructureTableNumRows()
    {
        return accessHandler.getStructureTableNumRows();
    }

    public static int getCommandTableNumRows()
    {
        return accessHandler.getCommandTableNumRows();
    }

    public static int getTableNumRows(String tableType)
    {
        return accessHandler.getTableNumRows(tableType);
    }

    public static int getTableNumRows()
    {
        return accessHandler.getTableNumRows();
    }

    public static String getStructureTableNameByRow(int row)
    {
        return accessHandler.getStructureTableNameByRow(row);
    }

    public static String getCommandTableNameByRow(int row)
    {
        return accessHandler.getCommandTableNameByRow(row);
    }

    public static String getTableNameByRow(String tableType, int row)
    {
        return accessHandler.getTableNameByRow(tableType, row);
    }

    public static String[] getStructureTablePaths()
    {
        return accessHandler.getStructureTablePaths();
    }

    public static String[] getStructureTableNames()
    {
        return accessHandler.getStructureTableNames();
    }

    public static String[] getCommandTableNames()
    {
        return accessHandler.getCommandTableNames();
    }

    public static String[] getTableNames(String tableType)
    {
        return accessHandler.getTableNames(tableType);
    }

    public static String[] getTableNames(String tableType, boolean prototypeOnly)
    {
        return accessHandler.getTableNames(tableType, prototypeOnly);
    }

    public static String[] getTableNames()
    {
        return accessHandler.getTableNames();
    }

    public static String getStructureVariableName(int row)
    {
        return accessHandler.getStructureVariableName(row);
    }

    public static String getStructureVariableNameWithMacros(int row)
    {
        return accessHandler.getStructureVariableNameWithMacros(row);
    }

    public static String getStructureDataType(int row)
    {
        return accessHandler.getStructureDataType(row);
    }

    public static String getStructureArraySize(int row)
    {
        return accessHandler.getStructureArraySize(row);
    }

    public static String getStructureArraySizeWithMacros(int row)
    {
        return accessHandler.getStructureArraySizeWithMacros(row);
    }

    public static String getStructureBitLength(int row)
    {
        return accessHandler.getStructureBitLength(row);
    }

    public static String getStructureBitLengthWithMacros(int row)
    {
        return accessHandler.getStructureBitLengthWithMacros(row);
    }

    public static String getStructureDescription(int row)
    {
        return accessHandler.getStructureDescription(row);
    }

    public static String getStructureDescriptionWithMacros(int row)
    {
        return accessHandler.getStructureDescriptionWithMacros(row);
    }

    public static String getStructureUnits(int row)
    {
        return accessHandler.getStructureUnits(row);
    }

    public static String getStructureUnitsWithMacros(int row)
    {
        return accessHandler.getStructureUnitsWithMacros(row);
    }

    public static String[] getStructureEnumerations(int row)
    {
        return accessHandler.getStructureEnumerations(row);
    }

    public static String[] getStructureEnumerationsWithMacros(int row)
    {
        return accessHandler.getStructureEnumerationsWithMacros(row);
    }

    public static String[] getStructureRates(int row)
    {
        return accessHandler.getStructureRates(row);
    }

    public static String getCommandName(int row)
    {
        return accessHandler.getCommandName(row);
    }

    public static String getCommandNameWithMacros(int row)
    {
        return accessHandler.getCommandNameWithMacros(row);
    }

    public static String getCommandCode(int row)
    {
        return accessHandler.getCommandCode(row);
    }

    public static String getCommandCodeWithMacros(int row)
    {
        return accessHandler.getCommandCodeWithMacros(row);
    }

    public static int getNumCommandArguments(int row)
    {
        return accessHandler.getNumCommandArguments(row);
    }

    public static int getNumCommandArguments(String tableType)
    {
        return accessHandler.getNumCommandArguments(tableType);
    }

    public static String getCommandArgName(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgName(argumentNumber, row);
    }

    public static String getCommandArgNameWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgNameWithMacros(argumentNumber, row);
    }

    public static String getCommandArgDataType(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgDataType(argumentNumber, row);
    }

    public static String getCommandArgArraySize(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgArraySize(argumentNumber, row);
    }

    public static String getCommandArgArraySizeWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgArraySizeWithMacros(argumentNumber, row);
    }

    public static String getCommandArgBitLength(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgBitLength(argumentNumber, row);
    }

    public static String getCommandArgBitLengthWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgBitLengthWithMacros(argumentNumber, row);
    }

    public static String getCommandArgEnumeration(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgEnumeration(argumentNumber, row);
    }

    public static String getCommandArgEnumerationWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgEnumerationWithMacros(argumentNumber, row);
    }

    public static String getCommandArgMinimum(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgMinimum(argumentNumber, row);
    }

    public static String getCommandArgMinimumWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgMinimumWithMacros(argumentNumber, row);
    }

    public static String getCommandArgMaximum(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgMaximum(argumentNumber, row);
    }

    public static String getCommandArgMaximumWithMacros(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgMaximumWithMacros(argumentNumber, row);
    }

    public static String getCommandArgByColumnName(int argumentNumber, int row, String columnName)
    {
        return accessHandler.getCommandArgByColumnName(argumentNumber, row, columnName);
    }

    public static String getCommandArgByColumnNameWithMacros(int argumentNumber,
                                                             int row,
                                                             String columnName)
    {
        return accessHandler.getCommandArgByColumnNameWithMacros(argumentNumber, row, columnName);
    }

    public static String[] getCommandArgColumnNames(int argumentNumber, int row)
    {
        return accessHandler.getCommandArgColumnNames(argumentNumber, row);
    }

    public static String getStructureTypeNameByRow(int row)
    {
        return accessHandler.getStructureTypeNameByRow(row);
    }

    public static String getCommandTypeNameByRow(int row)
    {
        return accessHandler.getCommandTypeNameByRow(row);
    }

    public static String getTypeNameByRow(String tableType, int row)
    {
        return accessHandler.getTypeNameByRow(tableType, row);
    }

    public static String getTypeNameByTable(String tableName)
    {
        return accessHandler.getTypeNameByTable(tableName);
    }

    public static String[] getStructureTableColumnNames(int row)
    {
        return accessHandler.getStructureTableColumnNames(row);
    }

    public static String[] getCommandTableColumnNames(int row)
    {
        return accessHandler.getCommandTableColumnNames(row);
    }

    public static String[] getTableColumnNames(String tableType, int row)
    {
        return accessHandler.getTableColumnNames(tableType, row);
    }

    public static String[] getTableColumnNamesByType(String typeName)
    {
        return accessHandler.getTableColumnNamesByType(typeName);
    }

    public static String[] getStructureTablesByReferenceOrder()
    {
        return accessHandler.getStructureTablesByReferenceOrder();
    }

    public static String getFullVariableNameRaw(int row)
    {
        return accessHandler.getFullVariableNameRaw(row);
    }

    public static String getFullVariableName(int row)
    {
        return accessHandler.getFullVariableName(row);
    }

    public static String getFullVariableName(int row, String varPathSeparator)
    {
        return accessHandler.getFullVariableName(row, varPathSeparator);
    }

    public static String getFullVariableName(int row,
                                             String varPathSeparator,
                                             boolean excludeDataTypes,
                                             String typeNameSeparator)
    {
        return accessHandler.getFullVariableName(row,
                                                 varPathSeparator,
                                                 excludeDataTypes,
                                                 typeNameSeparator);
    }

    public static String getFullVariableName(String variablePath,
                                             String variableName,
                                             String varPathSeparator)
    {
        return accessHandler.getFullVariableName(variablePath, variableName, varPathSeparator);
    }

    public static String getFullVariableName(String fullName, String varPathSeparator)
    {
        return accessHandler.getFullVariableName(fullName, varPathSeparator);
    }

    public static String getFullVariableName(String variablePath,
                                             String variableName,
                                             String varPathSeparator,
                                             boolean excludeDataTypes,
                                             String typeNameSeparator)
    {
        return accessHandler.getFullVariableName(variablePath,
                                                 variableName,
                                                 varPathSeparator,
                                                 excludeDataTypes,
                                                 typeNameSeparator);
    }

    public static String getFullVariableName(String fullName,
                                             String varPathSeparator,
                                             boolean excludeDataTypes,
                                             String typeNameSeparator)
    {
        return accessHandler.getFullVariableName(fullName,
                                                 varPathSeparator,
                                                 excludeDataTypes,
                                                 typeNameSeparator);
    }

    public static String getStructurePathByRow(int row)
    {
        return accessHandler.getStructurePathByRow(row);
    }

    public static String getStructurePathByRowWithMacros(int row)
    {
        return accessHandler.getStructurePathByRowWithMacros(row);
    }

    public static String getPathByRow(String tableType, int row)
    {
        return accessHandler.getPathByRow(tableType, row);
    }

    public static String getPathByRowWithMacros(String tableType, int row)
    {
        return accessHandler.getPathByRowWithMacros(tableType, row);
    }

    public static String getStructureTableVariablePathByRow(int row)
    {
        return accessHandler.getStructureTableVariablePathByRow(row);
    }

    public static String getStructureTableVariablePathByRowWithMacros(int row)
    {
        return accessHandler.getStructureTableVariablePathByRowWithMacros(row);
    }

    public static String getStructureTableITOSPathByRow(int row)
    {
        return accessHandler.getStructureTableITOSPathByRow(row);
    }

    public static String getStructureTableITOSPathByRowWithMacros(int row)
    {
        return accessHandler.getStructureTableITOSPathByRowWithMacros(row);
    }

    public static int getStructureParentRowByChildRow(int row)
    {
        return accessHandler.getStructureParentRowByChildRow(row);
    }

    public static boolean isStructureShared(String structureName)
    {
        return accessHandler.isStructureShared(structureName);
    }

    public boolean isStructureSharedExternally(String structureName)
    {
        return accessHandler.isStructureSharedExternally(structureName);
    }

    public static String[] getVariablePaths()
    {
        return accessHandler.getVariablePaths();
    }

    public static String[] getCommandInformation()
    {
        return accessHandler.getCommandInformation();
    }

    public static String[] getTableDataFieldNames(String tableName)
    {
        return accessHandler.getTableDataFieldNames(tableName);
    }

    public static String[] getGroupDataFieldNames(String groupName)
    {
        return accessHandler.getGroupDataFieldNames(groupName);
    }

    public static String[] getTypeDataFieldNames(String typeName)
    {
        return accessHandler.getTypeDataFieldNames(typeName);
    }

    public static String[] getProjectFieldNames()
    {
        return accessHandler.getProjectFieldNames();
    }

    public static String[][] getTableDataFieldValues(String fieldName)
    {
        return accessHandler.getTableDataFieldValues(fieldName);
    }

    public static String[][] getStructureTableDataFieldValues(String fieldName)
    {
        return accessHandler.getStructureTableDataFieldValues(fieldName);
    }

    public static String[][] getCommandTableDataFieldValues(String fieldName)
    {
        return accessHandler.getCommandTableDataFieldValues(fieldName);
    }

    public static String[][] getTableDataFieldValues(String tableType, String fieldName)
    {
        return accessHandler.getTableDataFieldValues(tableType, fieldName);
    }

    public static String getTableDataFieldValue(String tableName, String fieldName)
    {
        return accessHandler.getTableDataFieldValue(tableName, fieldName);
    }

    public static String getGroupDataFieldValue(String groupName, String fieldName)
    {
        return accessHandler.getGroupDataFieldValue(groupName, fieldName);
    }

    public static String getTypeDataFieldValue(String typeName, String fieldName)
    {
        return accessHandler.getTypeDataFieldValue(typeName, fieldName);
    }

    public static String getProjectDataFieldValue(String fieldName)
    {
        return accessHandler.getProjectDataFieldValue(fieldName);
    }

    public static String getTableDataFieldDescription(String tableName, String fieldName)
    {
        return accessHandler.getTableDataFieldDescription(tableName, fieldName);
    }

    public static String getGroupDataFieldDescription(String groupName, String fieldName)
    {
        return accessHandler.getGroupDataFieldDescription(groupName, fieldName);
    }

    public static String getTypeDataFieldDescription(String typeName, String fieldName)
    {
        return accessHandler.getTypeDataFieldDescription(typeName, fieldName);
    }

    public static String getProjectDataFieldDescription(String fieldName)
    {
        return accessHandler.getProjectDataFieldDescription(fieldName);
    }

    public static String getStructureTableData(String columnName, int row)
    {
        return accessHandler.getStructureTableData(columnName, row);
    }

    public static String getCommandTableData(String columnName, int row)
    {
        return accessHandler.getCommandTableData(columnName, row);
    }

    public static String getTableData(String tableType, String columnName, int row)
    {
        return accessHandler.getTableData(tableType, columnName, row);
    }

    public static String getStructureTableDataWithMacros(String columnName, int row)
    {
        return accessHandler.getStructureTableDataWithMacros(columnName, row);
    }

    public static String getCommandTableDataWithMacros(String columnName, int row)
    {
        return accessHandler.getCommandTableDataWithMacros(columnName, row);
    }

    public static String getTableDataWithMacros(String tableType, String columnName, int row)
    {
        return accessHandler.getTableDataWithMacros(tableType, columnName, row);
    }

    public static Integer[] getStructureTableRowIndices(String tablePath)
    {
        return accessHandler.getStructureTableRowIndices(tablePath);
    }

    public static Integer[] getCommandTableRowIndices(String tableName)
    {
        return accessHandler.getCommandTableRowIndices(tableName);
    }

    public static Integer[] getTableRowIndices(String tableType, String tablePath)
    {
        return accessHandler.getTableRowIndices(tableType, tablePath);
    }

    public static String getStructureDataByVariableName(String tablePath,
                                                        String variableName,
                                                        String columnName)
    {
        return accessHandler.getStructureDataByVariableName(tablePath, variableName, columnName);
    }

    public static String getTableDataByColumnName(String tableType,
                                                  String tablePath,
                                                  String matchColumnName,
                                                  String matchName,
                                                  String dataColumnName)
    {
        return accessHandler.getTableDataByColumnName(tableType,
                                                      tablePath,
                                                      matchColumnName,
                                                      matchName,
                                                      dataColumnName);
    }

    public static String getStructureDataByVariableNameWithMacros(String tablePath,
                                                                  String variableName,
                                                                  String columnName)
    {
        return accessHandler.getStructureDataByVariableNameWithMacros(tablePath,
                                                                      variableName,
                                                                      columnName);
    }

    public static String getTableDataByColumnNameWithMacros(String tableType,
                                                            String tablePath,
                                                            String matchColumnName,
                                                            String matchName,
                                                            String dataColumnName)
    {
        return accessHandler.getTableDataByColumnNameWithMacros(tableType,
                                                                tablePath,
                                                                matchColumnName,
                                                                matchName,
                                                                dataColumnName);
    }

    public static String getTableDescription(String tableName)
    {
        return accessHandler.getTableDescription(tableName);
    }

    public static String getTableDescriptionByRow(String tableType, int row)
    {
        return accessHandler.getTableDescriptionByRow(tableType, row);
    }

    public static String[][] getMacroDefinitions()
    {
        return accessHandler.getMacroDefinitions();
    }

    public static void showInformationDialog(String text)
    {
        accessHandler.showInformationDialog(text);
    }

    public static void showWarningDialog(String text)
    {
        accessHandler.showWarningDialog(text);
    }

    public static void showErrorDialog(String text)
    {
        accessHandler.showErrorDialog(text);
    }

    public static String getInputDialog(String labelText)
    {
        return accessHandler.getInputDialog(labelText);
    }

    public static String getRadioButtonDialog(String label, String[][] buttonInfo)
    {
        return accessHandler.getRadioButtonDialog(label, buttonInfo);
    }

    public static boolean[] getCheckBoxDialog(String label, String[][] boxInfo)
    {
        return accessHandler.getCheckBoxDialog(label, boxInfo);
    }

    public static String[][] getDatabaseQuery(String sqlCommand)
    {
        return accessHandler.getDatabaseQuery(sqlCommand);
    }

    public static void writeSuccessLogEntry(String logMessage)
    {
        accessHandler.writeSuccessLogEntry(logMessage);
    }

    public static void writeFailLogEntry(String logMessage)
    {
        accessHandler.writeFailLogEntry(logMessage);
    }

    public static void writeStatusLogEntry(String logMessage)
    {
        accessHandler.writeStatusLogEntry(logMessage);
    }

    public static String[][] parseEnumerationParameters(String enumeration)
    {
        return accessHandler.parseEnumerationParameters(enumeration);
    }

    public static String[] getArrayFromString(String text, String columnSeparator)
    {
        return accessHandler.getArrayFromString(text, columnSeparator);
    }

    public static String[][] getArrayFromString(String text,
                                                String columnSeparator,
                                                String rowSeparator)
    {
        return accessHandler.getArrayFromString(text, columnSeparator, rowSeparator);
    }

    public static PrintWriter openOutputFile(String outputFileName)
    {
        return accessHandler.openOutputFile(outputFileName);
    }

    public static void writeToFile(PrintWriter printWriter, String text)
    {
        accessHandler.writeToFile(printWriter, text);
    }

    public static void writeToFileLn(PrintWriter printWriter, String text)
    {
        accessHandler.writeToFileLn(printWriter, text);
    }

    public static void writeToFileFormat(PrintWriter printWriter, String format, Object... args)
    {
        accessHandler.writeToFileFormat(printWriter, format, args);
    }

    public static void closeFile(PrintWriter printWriter)
    {
        accessHandler.closeFile(printWriter);
    }

    public static String[][] getProjectFields()
    {
        return accessHandler.getProjectFields();
    }

    public static String getLinkDescription(String streamName, String linkName)
    {
        return accessHandler.getLinkDescription(streamName, linkName);
    }

    public static String getLinkRate(String streamName, String linkName)
    {
        return accessHandler.getLinkRate(streamName, linkName);
    }

    public static String[][] getVariableLinks(String variableName)
    {
        return accessHandler.getVariableLinks(variableName);
    }

    public static int getVariableOffset(String path)
    {
        return accessHandler.getVariableOffset(path);
    }

    public static String[] getLinkApplicationNames(String dataFieldName)
    {
        return accessHandler.getLinkApplicationNames(dataFieldName);
    }

    public static String[] getAssociatedGroupNames()
    {
        return accessHandler.getAssociatedGroupNames();
    }

    public static String[] getGroupNames(boolean applicationOnly)
    {
        return accessHandler.getGroupNames(applicationOnly);
    }

    public static String getGroupDescription(String groupName)
    {
        return accessHandler.getGroupDescription(groupName);
    }

    public static String[] getGroupTables(String groupName)
    {
        return accessHandler.getGroupTables(groupName);
    }

    public static String[][] getGroupFields(String groupName)
    {
        return accessHandler.getGroupFields(groupName);
    }

    public static String[] getCopyTableColumnNames()
    {
        return accessHandler.getCopyTableColumnNames();
    }

    public static String[][] getCopyTableEntries(String streamName,
                                                 int headerSize,
                                                 String messageIDNameField,
                                                 boolean optimize)
    {
        return accessHandler.getCopyTableEntries(streamName,
                                                 headerSize,
                                                 messageIDNameField,
                                                 optimize);
    }

    public static String[][] getCopyTableEntriesWithMacros(String streamName,
                                                           int headerSize,
                                                           String messageIDNameField,
                                                           boolean optimize)
    {
        return accessHandler.getCopyTableEntriesWithMacros(streamName,
                                                           headerSize,
                                                           messageIDNameField,
                                                           optimize);
    }

    public static String[][] getCopyTableEntries(String streamName,
                                                 int headerSize,
                                                 String[][] tlmMessageIDs,
                                                 boolean optimize)
    {
        return accessHandler.getCopyTableEntries(streamName,
                                                 headerSize,
                                                 tlmMessageIDs,
                                                 optimize);
    }

    public static String[][] getCopyTableEntriesWithMacros(String streamName,
                                                           int headerSize,
                                                           String[][] tlmMessageIDs,
                                                           boolean optimize)
    {
        return accessHandler.getCopyTableEntriesWithMacros(streamName,
                                                           headerSize,
                                                           tlmMessageIDs,
                                                           optimize);
    }

    public static String[][] getTelemetryMessageIDs(String streamName)
    {
        return accessHandler.getTelemetryMessageIDs(streamName);
    }

    public static String[][] getMessageOwnersNamesAndIDs()
    {
        return accessHandler.getMessageOwnersNamesAndIDs();
    }

    public static String[] getDataStreamNames()
    {
        return accessHandler.getDataStreamNames();
    }

    public static String[] getApplicationNames()
    {
        return accessHandler.getApplicationNames();
    }

    public static String[][] getApplicationScheduleDefinitionTableDefines()
    {
        return accessHandler.getApplicationScheduleDefinitionTableDefines();
    }

    public static String[][] getApplicationScheduleDefinitionTable(int row)
    {
        return accessHandler.getApplicationScheduleDefinitionTable(row);
    }

    public static String[] getApplicationMessageDefinitionTable()
    {
        return accessHandler.getApplicationMessageDefinitionTable();
    }

    public static int getNumberOfTimeSlots()
    {
        return accessHandler.getNumberOfTimeSlots();
    }

    public static String getPrototypeName(String tableName)
    {
        return TableInformation.getPrototypeName(tableName);
    }

    public static boolean isArrayMember(Object variableName)
    {
        return accessHandler.isArrayMember(variableName);
    }

    public static int[] getArrayIndexFromSize(String arrayString)
    {
        return accessHandler.getArrayIndexFromSize(arrayString);
    }

    public static String formatArrayIndex(int[] arrayIndex)
    {
        return accessHandler.formatArrayIndex(arrayIndex);
    }

    public static void xtceExport(String outputFileName,
                                  boolean isBigEndian,
                                  boolean isHeaderBigEndian,
                                  String version,
                                  String validationStatus,
                                  String classification1,
                                  String classification2,
                                  String classification3)
    {
        xtceExport(outputFileName,
                   isBigEndian,
                   isHeaderBigEndian,
                   version,
                   validationStatus,
                   classification1,
                   classification2,
                   classification3);
    }

    public static void xtceAddSpaceSystemHeader(SpaceSystemType spaceSystem,
                                                String classification,
                                                String validationStatus,
                                                String version,
                                                String date) throws CCDDException
    {
        accessHandler.xtceAddSpaceSystemHeader(spaceSystem,
                                               classification,
                                               validationStatus,
                                               version,
                                               date);
    }

    public static void xtceCreateTelemetryMetadata(SpaceSystemType spaceSystem)
    {
        accessHandler.xtceCreateTelemetryMetadata(spaceSystem);
    }

    public static void xtceAddSpaceSystemParameters(SpaceSystemType spaceSystem,
                                                    String tableName,
                                                    String[][] tableData,
                                                    int varColumn,
                                                    int typeColumn,
                                                    int sizeColumn,
                                                    int bitColumn,
                                                    int enumColumn,
                                                    int descColumn,
                                                    int unitsColumn,
                                                    int minColumn,
                                                    int maxColumn,
                                                    boolean isTlmHdrTable,
                                                    String tlmHdrSysPath,
                                                    boolean isRootStructure,
                                                    String applicationID) throws CCDDException
    {
        accessHandler.xtceAddSpaceSystemParameters(spaceSystem,
                                                   tableName,
                                                   tableData,
                                                   varColumn,
                                                   typeColumn,
                                                   sizeColumn,
                                                   bitColumn,
                                                   enumColumn,
                                                   descColumn,
                                                   unitsColumn,
                                                   minColumn,
                                                   maxColumn,
                                                   isTlmHdrTable,
                                                   tlmHdrSysPath,
                                                   isRootStructure,
                                                   applicationID);
    }

    public static void xtceAddParameterAndType(SpaceSystemType spaceSystem,
                                               String parameterName,
                                               String dataType,
                                               String arraySize,
                                               String bitLength,
                                               String enumeration,
                                               String units,
                                               String minimum,
                                               String maximum,
                                               String description,
                                               int stringSize) throws CCDDException
    {
        accessHandler.xtceAddParameterAndType(spaceSystem,
                                              parameterName,
                                              dataType,
                                              arraySize,
                                              bitLength,
                                              enumeration,
                                              units,
                                              minimum,
                                              maximum,
                                              description,
                                              stringSize);
    }

    public static boolean xtceAddParameterSequenceEntry(SpaceSystemType spaceSystem,
                                                        String parameterName,
                                                        String dataType,
                                                        String arraySize,
                                                        EntryListType entryList,
                                                        boolean isTlmHdrRef) throws CCDDException
    {
        return accessHandler.xtceAddParameterSequenceEntry(spaceSystem,
                                                           parameterName,
                                                           dataType,
                                                           arraySize,
                                                           entryList,
                                                           isTlmHdrRef);
    }

    public static void xtceSetParameterDataType(SpaceSystemType spaceSystem,
                                                String parameterName,
                                                String dataType,
                                                String arraySize,
                                                String bitLength,
                                                String enumeration,
                                                String units,
                                                String minimum,
                                                String maximum,
                                                String description,
                                                int stringSize) throws CCDDException
    {
        accessHandler.xtceSetParameterDataType(spaceSystem,
                                               parameterName,
                                               dataType,
                                               arraySize,
                                               bitLength,
                                               enumeration,
                                               units,
                                               minimum,
                                               maximum,
                                               description,
                                               stringSize);
    }

    public static void xtceCreateCommandMetadata(SpaceSystemType spaceSystem) throws CCDDException
    {
        accessHandler.xtceCreateCommandMetadata(spaceSystem);
    }

    protected static void xtceAddSpaceSystemCommands(SpaceSystemType spaceSystem,
                                                     String[][] tableData,
                                                     int cmdNameColumn,
                                                     int cmdCodeColumn,
                                                     int cmdDescColumn,
                                                     boolean isCmdHeader,
                                                     String cmdHdrSysPath,
                                                     String applicationID) throws CCDDException
    {
        accessHandler.xtceAddSpaceSystemCommands(spaceSystem,
                                                 tableData,
                                                 cmdNameColumn,
                                                 cmdCodeColumn,
                                                 cmdDescColumn,
                                                 isCmdHeader,
                                                 cmdHdrSysPath,
                                                 applicationID);
    }

    public static void xtceAddCommand(SpaceSystemType spaceSystem,
                                      String commandName,
                                      String cmdFuncCode,
                                      String applicationID,
                                      boolean isCmdHeader,
                                      String cmdHdrSysPath,
                                      String[] argumentNames,
                                      String[] argDataTypes,
                                      String[] argArraySizes,
                                      String description) throws CCDDException
    {
        accessHandler.xtceAddCommand(spaceSystem,
                                     commandName,
                                     cmdFuncCode,
                                     applicationID,
                                     isCmdHeader,
                                     cmdHdrSysPath,
                                     argumentNames,
                                     argDataTypes,
                                     argArraySizes,
                                     description);
    }

    public static NameDescriptionType xtceSetArgumentDataType(SpaceSystemType spaceSystem,
                                                              String argumentName,
                                                              String dataType,
                                                              String arraySize,
                                                              String bitLength,
                                                              String enumeration,
                                                              String units,
                                                              String minimum,
                                                              String maximum,
                                                              String description,
                                                              int stringSize,
                                                              String uniqueID) throws CCDDException
    {
        return accessHandler.xtceSetArgumentDataType(spaceSystem,
                                                     argumentName,
                                                     dataType,
                                                     arraySize,
                                                     bitLength,
                                                     enumeration,
                                                     units,
                                                     minimum,
                                                     maximum,
                                                     description,
                                                     stringSize,
                                                     uniqueID);
    }

    public static void xtceAddContainerReference(String parameterName,
                                                 String dataType,
                                                 String arraySize,
                                                 Object entryList) throws CCDDException
    {
        accessHandler.xtceAddContainerReference(parameterName, dataType, arraySize, entryList);
    }

    public static UnitSet xtceCreateUnitSet(String units)
    {
        return accessHandler.xtceCreateUnitSet(units);
    }

    public static EnumerationList xtceCreateEnumerationList(SpaceSystemType spaceSystem,
                                                            String enumeration)
    {
        return accessHandler.xtceCreateEnumerationList(spaceSystem, enumeration);
    }

    public static BasePrimitiveDataType xmlGetBaseDataType(String dataType)
    {
        return accessHandler.xmlGetBaseDataType(dataType);
    }

    public static String xmlCleanSystemPath(String path)
    {
        return accessHandler.xmlCleanSystemPath(path);
    }

    public static void showData()
    {
        accessHandler.showData();
    }
}
