/**************************************************************************************************
 * /** \file CcddImportExportSupportHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class containing support methods for classes based on the CcddImportExportInterface
 * class. The support methods handle validation and addition of table types and data fields, and
 * for obtaining the user’s response to a non-fatal error condition. Classes utilizing these
 * support methods must extend this class.
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

import static CCDD.CcddConstants.ASSN_TABLE_SEPARATOR;
import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.IGNORE_BUTTON;
import static CCDD.CcddConstants.STRUCT_CMD_ARG_REF;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.lang3.tuple.ImmutablePair;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.InternalTable.InputTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddConstants.TableTypeUpdate;
import CCDD.CcddImportExportInterface.ImportType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary import and export support handler class
 *************************************************************************************************/
public class CcddImportExportSupportHandler
{
    // Class references
    protected CcddTableTypeHandler tableTypeHandler;
    protected CcddInputTypeHandler inputTypeHandler;
    protected CcddFieldHandler fieldHandler;
    protected CcddMacroHandler macroHandler;
    protected CcddDbControlHandler dbControl;
    protected CcddDbTableCommandHandler dbTable;
    protected CcddHaltDialog haltDlg;

    // GUI component over which to center any error dialog
    protected Component parent;

    // Names of the structure tables that represent the common header for all telemetry and command
    // tables
    protected String tlmHeaderTable;
    protected String cmdHeaderTable;

    // Telemetry and command header variable names for the application ID, and command header
    // variable name for the command function code
    protected String applicationIDName;
    protected String cmdFuncCodeName;

    // Table type definitions
    protected TypeDefinition structureTypeDefn;
    protected TypeDefinition commandTypeDefn;
    protected TypeDefinition cmdArgStructTypeDefn;

    // Structure column indices
    protected int structVariableNameIndex;
    protected int structDataTypeIndex;
    protected int structArraySizeIndex;
    protected int structBitLengthIndex;
    protected int structEnumerationIndex;
    protected int structMinimumIndex;
    protected int structMaximumIndex;
    protected int structDescriptionIndex;
    protected int structUnitsIndex;
    protected Integer[] structRateIndices;
    protected int cmdArgVariableNameIndex;
    protected int cmdArgDataTypeIndex;
    protected int cmdArgArraySizeIndex;
    protected int cmdArgBitLengthIndex;
    protected int cmdArgEnumerationIndex;
    protected int cmdArgMinimumIndex;
    protected int cmdArgMaximumIndex;
    protected int cmdArgDescriptionIndex;
    protected int cmdArgUnitsIndex;
    protected Integer[] cmdArgRateIndices;

    // Structure table type total number of columns
    protected int structNumColumns;
    protected int cmdArgNumColumns;

    // Command column indices
    protected int commandNameIndex;
    protected int cmdFuncCodeIndex;
    protected int cmdArgumentIndex;
    protected int cmdDescriptionIndex;

    // Basic primitive data types
    protected static enum BasePrimitiveDataType
    {
        INTEGER, FLOAT, STRING
    }

    /**********************************************************************************************
     * Default application ID and command function code header table variable names
     *********************************************************************************************/
    protected enum DefaultHeaderVariableName
    {
        APP_ID("applicationID"),
        FUNC_CODE("functionCode");

        private final String defaultVariableName;

        /******************************************************************************************
         * Default application ID and command function code header table variable names constructor
         *
         * @param defaultVariableName Default variable name
         *****************************************************************************************/
        DefaultHeaderVariableName(String defaultVariableName)
        {
            this.defaultVariableName = defaultVariableName;
        }

        /******************************************************************************************
         * Get the default variable name
         *
         * @return Default variable name
         *****************************************************************************************/
        protected String getDefaultVariableName()
        {
            return defaultVariableName;
        }
    }

    /**********************************************************************************************
     * Export the project tables in XML format to the specified file
     *
     * @param project    SpaceSystem (XTCE) or DataSheetType (EDS)
     *
     * @param marshaller Reference to the XML marshaler
     *
     * @param exportFile Reference to the user-specified output file
     *
     * @throws JAXBException If an error occurs marshaling the project
     *
     * @throws TransformerException If an error occurs transforming the project
     *********************************************************************************************/
    protected void marshallXMLfile(JAXBElement<?> project,
                                   Marshaller marshaller,
                                   FileEnvVar exportFile) throws JAXBException,
                                                                 TransformerException
    {
        // Output the XML to the specified file. The marshaler has a hard-coded limit of 8 levels;
        // once exceeded it starts back at the first column. Therefore, a Transformer is used to
        // set the indentation amount (it doesn't have an indentation level limit)
        DOMResult domResult = new DOMResult();
        marshaller.marshal(project, domResult);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
        transformer.transform(new DOMSource(domResult.getNode()), new StreamResult(exportFile));
    }

    /**********************************************************************************************
     * Scan the import file in order to determine if any structure or command tables exist. If so,
     * create the structure and/or command table type definition that's used to build the new
     * tables
     *
     * @param importFile     Reference to the user-specified XML input file
     *
     * @param importType     ImportType.IMPORT_ALL to import the table type, data type, and macro
     *                       definitions, and the data from all the table definitions;
     *                       ImportType.FIRST_DATA_ONLY to load only the data for the first table
     *                       defined
     *
     * @param targetTypeDefn Table type definition of the table in which to import the data;
     *                       ignored if importing all tables
     *
     * @throws CCDDException Included due to calls to addImportedTableTypeColumnDefinition(); since
     *                       default column definitions are used this error can't occur
     *********************************************************************************************/
    protected void createTableTypeDefinitions(FileEnvVar importFile,
                                              ImportType importType,
                                              TypeDefinition targetTypeDefn) throws CCDDException
    {
        // Set the flags to indicate if the target is a structure or command table
        boolean targetIsStructure = importType == ImportType.IMPORT_ALL ? true
                                                                        : targetTypeDefn.isStructure()
                                                                          && !targetTypeDefn.isCommandArgumentStructure();
        boolean targetIsCommand = importType == ImportType.IMPORT_ALL ? true
                                                                      : targetTypeDefn.isCommand();
        boolean targetIsCmdArgStruct = importType == ImportType.IMPORT_ALL ? true
                                                                           : targetTypeDefn.isCommandArgumentStructure();

        // Check if a structure table type needs to be defined
        if (targetIsStructure)
        {
            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                // Use the structure table type that has the default name, if present; otherwise
                // use the first structure table type found
                for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
                {
                    if (typeDefn.isStructure() && !typeDefn.isCommandArgumentStructure())
                    {
                        // Check if columns not required by this table type, but used when
                        // importing, are present in the type definition
                        if (CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.ENUMERATION)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.MINIMUM)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.MAXIMUM)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.RATE)) != -1)
                        {
                            if (structureTypeDefn == null)
                            {
                                structureTypeDefn = typeDefn;
                            }

                            if (typeDefn.getName().equals(TYPE_STRUCTURE))
                            {
                                break;
                            }
                        }
                    }
                }

                // Check if no structure table type exists
                if (structureTypeDefn == null)
                {
                    List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                    String typeName = TYPE_STRUCTURE;
                    int sequence = 2;

                    // Create a table type definition for structure tables
                    TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                                "0Import structure table type");

                    // Step through each default structure column
                    for (Object[] columnDefn : DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE, false))
                    {
                        // Add the column to the table type definition
                        addImportedTableTypeColumnDefinition(true,
                                                             tableTypeDefn,
                                                             CcddUtilities.convertObjectToString(columnDefn),
                                                             importFile.getAbsolutePath(),
                                                             inputTypeHandler,
                                                             parent);
                    }

                    // Check if a table type definition already exists with this name, but differing properties
                    if (tableTypeHandler.updateTableTypes(tableTypeDefn) == TableTypeUpdate.MISMATCH)
                    {
                        // Alter the name so that there isn't a duplicate
                        typeName = TYPE_STRUCTURE + " " + sequence;
                        tableTypeDefn.setTypeName(typeName);
                        sequence++;
                    }

                    // Add the structure table type definition
                    tableTypeDefns.add(tableTypeDefn);
                    tableTypeHandler.updateTableTypes(tableTypeDefns);

                    // Store the reference to the structure table type definition
                    structureTypeDefn = tableTypeHandler.getTypeDefinition(typeName);

                    // Update the database functions that collect structure table members and
                    // structure-defining column data
                    dbControl.createStructureColumnFunctions();
                }
            }
            // Only a single table is to be imported
            else
            {
                structureTypeDefn = targetTypeDefn;
            }

            structVariableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE));
            structDataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT));
            structArraySizeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX));
            structBitLengthIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH));
            structEnumerationIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.ENUMERATION));
            structMinimumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.MINIMUM));
            structMaximumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.MAXIMUM));
            structDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));
            structUnitsIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.UNITS));
            List<Integer> structRateIndexList = structureTypeDefn.getColumnIndicesByInputType(DefaultInputType.RATE);

            if (structRateIndexList.size() != 0)
            {
                structRateIndices = structRateIndexList.toArray(new Integer[0]);

                for (int index = 0; index < structRateIndices.length; ++index)
                {
                    structRateIndices[index] = CcddTableTypeHandler.getVisibleColumnIndex(structRateIndices[index]);
                }
            }
            else
            {
                structRateIndices = new Integer[0];
            }

            structNumColumns = structureTypeDefn.getColumnCountVisible();
        }

        // Check if a command argument structure table type needs to be defined
        if (targetIsCmdArgStruct)
        {
            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                // Use the command argument structure table type that has the default name, if
                // present; otherwise use the first command argument structure table type found
                for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
                {
                    if (typeDefn.isCommandArgumentStructure())
                    {
                        // Check if columns not required by this table type, but used when
                        // importing, are present in the type definition
                        if (CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.ENUMERATION)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.MINIMUM)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.MAXIMUM)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION)) != -1
                            && CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS)) != -1)
                        {
                            if (cmdArgStructTypeDefn == null)
                            {
                                cmdArgStructTypeDefn = typeDefn;
                            }

                            if (typeDefn.getName().equals(STRUCT_CMD_ARG_REF))
                            {
                                break;
                            }
                        }
                    }
                }

                if (cmdArgStructTypeDefn == null)
                {
                    List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                    String typeName = STRUCT_CMD_ARG_REF;
                    int sequence = 2;

                    // Create a table type definition for structure tables
                    TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                                "1Import command argument structure table type");

                    // Step through each default structure column
                    for (Object[] columnDefn : DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE, false))
                    {
                        // Add the column to the table type definition
                        addImportedTableTypeColumnDefinition(true,
                                                             tableTypeDefn,
                                                             CcddUtilities.convertObjectToString(columnDefn),
                                                             importFile.getAbsolutePath(),
                                                             inputTypeHandler,
                                                             parent);
                    }

                    // Check if a table type definition already exists with this name, but differing properties
                    if (tableTypeHandler.updateTableTypes(tableTypeDefn) == TableTypeUpdate.MISMATCH)
                    {
                        // Alter the name so that there isn't a duplicate
                        typeName = STRUCT_CMD_ARG_REF + " " + sequence;
                        tableTypeDefn.setTypeName(typeName);
                        sequence++;
                    }

                    // Add the command argument structure table type definition
                    tableTypeDefns.add(tableTypeDefn);
                    tableTypeHandler.updateTableTypes(tableTypeDefns);

                    // Store the reference to the command argument structure table type definition
                    cmdArgStructTypeDefn = tableTypeHandler.getTypeDefinition(typeName);

                    // Update the database functions that collect structure table members and
                    // structure-defining column data
                    dbControl.createStructureColumnFunctions();
                }
            }
            // Only a single table is to be imported
            else
            {
                cmdArgStructTypeDefn = targetTypeDefn;
            }

            cmdArgVariableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE));
            cmdArgDataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT));
            cmdArgArraySizeIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX));
            cmdArgBitLengthIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH));
            cmdArgEnumerationIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.ENUMERATION));
            cmdArgMinimumIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.MINIMUM));
            cmdArgMaximumIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.MAXIMUM));
            cmdArgDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));
            cmdArgUnitsIndex = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgStructTypeDefn.getColumnIndexByInputType(DefaultInputType.UNITS));
            List<Integer> cmdArgRateIndexList = cmdArgStructTypeDefn.getColumnIndicesByInputType(DefaultInputType.RATE);

            if (cmdArgRateIndexList.size() != 0)
            {
                cmdArgRateIndices = cmdArgRateIndexList.toArray(new Integer[0]);

                for (int index = 0; index < cmdArgRateIndices.length; ++index)
                {
                    cmdArgRateIndices[index] = CcddTableTypeHandler.getVisibleColumnIndex(cmdArgRateIndices[index]);
                }
            }
            else
            {
                cmdArgRateIndices = new Integer[0];
            }

            cmdArgNumColumns = cmdArgStructTypeDefn.getColumnCountVisible();
        }

        // Check if a command table type needs to be defined
        if (targetIsCommand)
        {
            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                // Use the command table type that has the default name, if present; otherwise use
                // the first command table type found
                for (TypeDefinition typeDefn : tableTypeHandler.getTypeDefinitions())
                {
                    if (typeDefn.isCommand())
                    {
                        // Check if columns not required by this table type, but used when
                        // importing, are present in the type definition
                        if (CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION)) != -1)
                        {
                            if (commandTypeDefn == null)
                            {
                                commandTypeDefn = typeDefn;
                            }

                            if (typeDefn.getName().equals(TYPE_COMMAND))
                            {
                                break;
                            }
                        }
                    }
                }

                if (commandTypeDefn == null)
                {
                    List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                    String typeName = TYPE_COMMAND;
                    int sequence = 2;

                    // Create a table type definition for command tables
                    TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                               "0Import command table type");

                    // Step through each default command column
                    for (Object[] columnDefn : DefaultColumn.getDefaultColumnDefinitions(TYPE_COMMAND, false))
                    {
                        // Add the column to the table type definition
                        addImportedTableTypeColumnDefinition(true,
                                                             tableTypeDefn,
                                                             CcddUtilities.convertObjectToString(columnDefn),
                                                             importFile.getAbsolutePath(),
                                                             inputTypeHandler,
                                                             parent);
                    }


                    // Check if a table type definition already exists with this name, but differing properties
                    if (tableTypeHandler.updateTableTypes(tableTypeDefn) == TableTypeUpdate.MISMATCH)
                    {
                        // Alter the name so that there isn't a duplicate
                        typeName = TYPE_COMMAND + " " + sequence;
                        tableTypeDefn.setTypeName(typeName);
                        sequence++;
                    }

                    // Add the structure table type definition
                    tableTypeDefns.add(tableTypeDefn);
                    tableTypeHandler.updateTableTypes(tableTypeDefns);

                    // Store the reference to the command table type definition
                    commandTypeDefn = tableTypeHandler.getTypeDefinition(typeName);
                }
            }
            // A single command table is to be imported into an existing command table
            else
            {
                commandTypeDefn = targetTypeDefn;
            }

            // Get the command table column indices
            commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME));
            cmdFuncCodeIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE));
            cmdArgumentIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_ARGUMENT));
            cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));
        }
    }

    /**********************************************************************************************
     * Load the table data. If the table is an instance (not a prototype) then load the prototype
     * and use it to flag inherited and overridden table values
     *
     * @param tablePath Table name and path
     *
     * @return Table information
     *********************************************************************************************/
    public TableInfo loadTableData(String tablePath)
    {
        // Load the table data
        TableInfo tableInfo = dbTable.loadTableData(tablePath, true, false, false, parent);

        // Check if the table's data successfully loaded
        if (!tableInfo.isErrorFlag())
        {
            // Get the table type and from the type get the type definition. The type definition
            // can be a global parameter since if the table represents a structure, then all of its
            // children are also structures, and if the table represents commands or other table
            // type then it is processed within this nest level
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

            // Check if this is an instance table
            if (!tableInfo.isPrototype())
            {
                // Load the data for the table's prototype
                TableInfo protoInfo = dbTable.loadTableData(tableInfo.getPrototypeName(),
                                                            true,
                                                            false,
                                                            false,
                                                            parent);

                // Check if the prototype table's data successfully loaded
                if (!protoInfo.isErrorFlag())
                {
                    List<Integer> protectedColumns = new ArrayList<Integer>();

                    // Add the column indices for columns that cannot have overridden values. An
                    // enumeration, if it has an inherited value, is blanked below like the other
                    // inheritable columns. The Enumerated type isn't created in the export file,
                    // but is replaced by an Integer type with the same characteristics. When
                    // importing, the enumeration is restored via/ inheritance
                    protectedColumns.add(typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE));
                    protectedColumns.add(typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT));
                    protectedColumns.add(typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX));
                    protectedColumns.add(typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH));

                    int numRows = tableInfo.getData().size();
                    int numColumns = typeDefn.getColumnCountDatabase();

                    // Step through each cell in the table
                    for (int row = 0; row < numRows; ++row)
                    {
                        for (int column = 0; column < numColumns; ++column)
                        {
                            // Check if the column can be inherited or overridden
                            if (!protectedColumns.contains(column))
                            {
                                // Check if the values in the instance and prototype match, which
                                // indicates the instance is inheriting the value. Since macro
                                // names are case insensitive any macros are expanded
                                if (macroHandler.getMacroExpansion(tableInfo.getData().get(row)[column].toString())
                                    .equals(macroHandler.getMacroExpansion(protoInfo.getData().get(row)[column].toString())))
                                {
                                    // Replace the value with a blank. This prevents storing the
                                    // inherited value in the export file, so when the table is
                                    // imported it inherits the cell value from the prototype
                                    tableInfo.getData().get(row)[column] = "";
                                }
                                // Check if the instance cell is empty, which prevents
                                // inheritance by overriding the prototype value
                                else if (tableInfo.getData().get(row)[column].toString().isEmpty())
                                {
                                    // Replace the value with null. This causes a blank to be
                                    // stored for the cell in the export file. When imported the
                                    // blank entry causes it to be treated an an override for the
                                    // prototype value
                                    tableInfo.getData().get(row)[column] = null;
                                }
                            }
                        }
                    }
                }
            }
        }

        return tableInfo;
    }

    /**********************************************************************************************
     * Load the table data for a JSON or CSV export
     *
     * @param tableNames Array of table names to convert
     *
     * @return List containing the definition(s) of the table(s) to export
     *********************************************************************************************/
    public List<TableInfo> loadTablesforJsonOrCsvExport(String[] tableNames)
    {
        // Add the prototype of each supplied table name (if not already in the array of supplied
        // table names)
        List<String> tableNamesWithProtos = new ArrayList<String>();

        for (String tableName : tableNames)
        {
            tableNamesWithProtos.add(tableName);
            String protoName = TableInfo.getPrototypeName(tableName);

            if (!tableNamesWithProtos.contains(protoName))
            {
                // Put the prototypes at the head of the list so that they're processed first below
                tableNamesWithProtos.add(0, protoName);
            }
        }

        // Initialize local variables
        List<TableInfo> tableDefs = new ArrayList<TableInfo>(tableNamesWithProtos.size());

        // Check if any tables are provided
        if (tableNamesWithProtos.size() != 0)
        {
            for (String tableName : tableNamesWithProtos)
            {
                // Load the table data, accounting for value inheritance and override
                tableDefs.add(loadTableData(tableName));
            }
        }

        return tableDefs;
    }

    /**********************************************************************************************
     * Build the supplied structure and command tables in XML format for export
     *
     * @param tableDefs List containing the definitions of the tables to export
     *
     * @throws CCDDException An error occurred loading or building a table
     *********************************************************************************************/
    protected void buildStructureAndCommandTableXML(List<TableInfo> tableDefs) throws CCDDException
    {
        List<String> processedTables = new ArrayList<String>();

        // Step through each table path+name
        for (TableInfo tableDef : tableDefs)
        {
            String tableName = tableDef.getTablePath();
            String tablePath = tableDef.getTablePath();

            // Update the export progress dialog
            updateExportProgress(tableName);

            // Store the table name as the one used for extracting data from the project database.
            // The two names differ when a descendant of the telemetry header is loaded
            String loadTableName = tableName;

            // Check if this table is a reference to the telemetry header table or one of its
            // descendant tables
            if (tlmHeaderTable != null
                && !tlmHeaderTable.isEmpty()
                && tablePath.matches("(?:[^,]+,)*" + tlmHeaderTable + "(?:\\..*|,.+|$)"))
            {
                // Only one telemetry header table is created even though multiple instances of it
                // may be referenced. The prototype is used to define the telemetry header; any
                // custom values in the instances are ignored. Descendants of the telemetry header
                // table are treated similarly

                // Check if this is a reference to the telemetry header table
                if (TableInfo.getPrototypeName(tablePath).equals(tlmHeaderTable))
                {
                    // Set the table name and path to the prototype
                    tableName = tlmHeaderTable;
                    loadTableName = tlmHeaderTable;
                    tablePath = tlmHeaderTable;
                }
                // This is a reference to a descendant of the telemetry header table
                else
                {
                    // Store the table's prototype and variable name as the table name. The actual
                    // table to load doesn't need the variable name, so it's removed from the table
                    // name
                    tableName = TableInfo.getProtoVariableName(tablePath);
                    loadTableName = TableInfo.getPrototypeName(tablePath);

                    // Adjust the table path from the instance reference to a pseudo-prototype
                    // reference. This is used to populate the short description, which in turn is
                    // used during import to place the structure within the correct hierarchy
                    tablePath = tablePath.replaceFirst("(?:.*,)*(" + tlmHeaderTable + ")[^,]+(,.*)", "$1$2");
                }
            }
            // Check if this table is a reference to the command header table or one of its
            // descendant tables
            else if (cmdHeaderTable != null
                     && !cmdHeaderTable.isEmpty()
                     && tablePath.matches(cmdHeaderTable + "(?:,.+|$)"))
            {
                // The command header is a root structure table. The prototype tables for
                // descendants of the command header table are loaded instead of the specific
                // instances; any custom values in the instances are ignored

                // Check if this is a reference to the command header table
                if (TableInfo.getPrototypeName(tablePath).equals(cmdHeaderTable))
                {
                    // Set the table name to the prototype
                    tableName = cmdHeaderTable;
                    loadTableName = cmdHeaderTable;
                }
                // This is a reference to a descendant of the command header table
                else
                {
                    // Store the table's prototype and variable name as the table name. The actual
                    // table to load doesn't need the variable name, so it's removed from the table
                    // name
                    tableName = TableInfo.getProtoVariableName(tablePath);
                    loadTableName = TableInfo.getPrototypeName(tablePath);
                }
            }

            // Check if this table has already been loaded and its space system built. This
            // prevents repeated references to the telemetry/command header and its children from
            // being from being reprocessed
            if (!processedTables.contains(tableName))
            {
                // Add the table name to the list of those already processed so that future
                // references are ignored
                processedTables.add(tableName);

                // Load the table data. If not a prototype then flag the values that are inherited
                // or overridden
                TableInfo tableInfo = loadTableData(loadTableName);

                // Check if the table's data successfully loaded
                if (!tableInfo.isErrorFlag())
                {
                    // Get the table type and from the type get the type definition. The type
                    // definition can be a global parameter since if the table represents a
                    // structure, then all of its children are also structures, and if the table
                    // represents commands or other table type then it is processed within this
                    // nest level
                    TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                    // Check if the table type represents a structure or command
                    if (typeDefn != null && (typeDefn.isStructure() || typeDefn.isCommand()))
                    {
                        // Replace all macro names with their corresponding values
                        tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));

                        // Get the message name and ID data field information, if present
                        FieldInformation msgFieldInfo = fieldHandler.getFieldInformationByInputType(loadTableName,
                                                                                                    inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID));
                        String messageFieldName = null;
                        String messageNameAndID = null;

                        if (msgFieldInfo != null && !msgFieldInfo.getValue().isEmpty())
                        {
                            messageFieldName = msgFieldInfo.getFieldName();
                            messageNameAndID = msgFieldInfo.getValue();
                        }

                        // Create the XML for the table for export
                        buildTableAsXML(tableInfo,
                                        typeDefn,
                                        tablePath,
                                        tableName,
                                        messageFieldName,
                                        messageNameAndID);
                    }
                }
                // An error occurred loading the table information
                else
                {
                    throw new CCDDException("Unable to load table '" + tableName + "'");
                }
            }
        }
    }

    /**********************************************************************************************
     * Build a structure or command table in XML format for export
     *
     * @param tableInfo        Table definition
     *
     * @param typeDefn         Table type definition
     *
     * @param tablePath        Table path and name
     *
     * @param tableName        Table name
     *
     * @param messageFieldName Message name and ID field name; null if not present or not applicable
     *
     * @param messageNameAndID Message name and ID; null if not present or not applicable
     *
     * @throws CCDDException An error occurred building the table
     *********************************************************************************************/
    protected void buildTableAsXML(TableInfo tableInfo,
                                   TypeDefinition typeDefn,
                                   String tablePath,
                                   String tableName,
                                   String messageFieldName,
                                   String messageNameAndID) throws CCDDException
    {
        // Placeholder
    }

    /**********************************************************************************************
     * Add a table type column definition after verifying the input parameters
     *
     * @param continueOnError  Current state of the flag that indicates if all table type errors
     *                         should be ignored
     *
     * @param tableTypeDefn    Reference to the TableTypeDefinition to which this column definition
     *                         applies
     *
     * @param columnDefn       Array containing the table type column definition
     *
     * @param fileName         Import file name
     *
     * @param inputTypeHandler Input type handler reference
     *
     * @param parent           GUI component over which to center any error dialog
     *
     * @return True if the user elected to ignore the column error
     *
     * @throws CCDDException If the column name is missing or the user elects to stop the import
     *                       operation due to an invalid input type
     *********************************************************************************************/
    protected boolean addImportedTableTypeColumnDefinition(boolean continueOnError,
                                                           TableTypeDefinition tableTypeDefn,
                                                           String[] columnDefn,
                                                           String fileName,
                                                           CcddInputTypeHandler inputTypeHandler,
                                                           Component parent) throws CCDDException
    {
        // Check if the column name is empty
        if (columnDefn[TableTypeEditorColumnInfo.NAME.ordinal()].isEmpty())
        {
            // Inform the user that the column name is missing
            throw new CCDDException("Table type '</b>" + tableTypeDefn.getTypeName()
                                    + "<b>' definition column name missing");
        }

        // Check if the input type is empty
        if (columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].isEmpty())
        {
            // Default to text
            columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }
        // Check if the input type name is invalid
        else if (!inputTypeHandler.isInputTypeValid(columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]))
        {
            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Table type '</b>"
                                               + tableTypeDefn.getTypeName()
                                               + "<b>' definition input type '</b>"
                                               + columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                               + "<b>' unrecognized in import file '</b>"
                                               + fileName
                                               + "<b>'; continue?",
                                               "Table Type Error", "Ignore this error (default to 'Text')",
                                               "Ignore this and any remaining invalid table types (use default "
                                               + "values where possible, or skip the affected table type)",
                                               "Stop importing",
                                               parent);

            // Default to text
            columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }

        // Add the table type column definition
        tableTypeDefn.addColumn(new Object[] {Integer.valueOf(columnDefn[TableTypeEditorColumnInfo.INDEX.ordinal()]),
                                              columnDefn[TableTypeEditorColumnInfo.NAME.ordinal()],
                                              columnDefn[TableTypeEditorColumnInfo.DESCRIPTION.ordinal()],
                                              columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()],
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.UNIQUE.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.REQUIRED.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()])});

        return continueOnError;
    }

    /**********************************************************************************************
     * Add a data field definition after verifying the input parameters are valid (use defaults for
     * field size, input type, or applicability if these parameters that are not supplied). For
     * project-level or table type fields, if the field already exists for this owner compare the
     * field definition's input type, required status, applicability, and value; if a mismatch is
     * found allow the user to determine how to proceed (this check is unnecessary for table fields
     * since the new ones either replace existing ones or are ignored, based on the import flags)
     *
     * @param continueOnError  Current state of the flag that indicates if all data field errors
     *                         should be ignored
     *
     * @param replaceExisting  Replace any existing fields
     *
     * @param defnContainer    TableDefinition, TableTypeDefinition, or projectDefinition object to
     *                         which this data field applies
     *
     * @param fieldDefn        Array containing the data field definition
     *
     * @param fileName         Import file name
     *
     * @param inputTypeHandler Input type handler reference
     *
     * @param fieldHandler     Data field handler reference
     *
     * @param parent           GUI component over which to center any error dialog
     *
     * @return True if the user elected to ignore the data field error
     *
     * @throws CCDDException If the data field name is missing or the user elects to stop the
     *                       import operation due to an invalid input type
     *********************************************************************************************/
    protected boolean addImportedDataFieldDefinition(boolean continueOnError,
                                                     boolean replaceExisting,
                                                     Object defnContainer,
                                                     String[] fieldDefn,
                                                     String fileName,
                                                     CcddInputTypeHandler inputTypeHandler,
                                                     CcddFieldHandler fieldHandler,
                                                     Component parent) throws CCDDException
    {
        boolean isError = false;

        // Check if the field name is empty
        if (fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].isEmpty())
        {
            // Inform the user that the field name is missing
            throw new CCDDException("Data field name missing");
        }

        // Check if the field size is empty
        if (fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()].isEmpty())
        {
            // Use the default value
            fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()] = "10";
        }

        // Check if the field required indicator is empty
        if (fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].isEmpty())
        {
            // Default to not required
            fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()] = "false";
        }

        // Check if the input type is empty
        if (fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].isEmpty())
        {
            // Default to text
            fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }
        // Check if the input type name is invalid
        else if (!inputTypeHandler.isInputTypeValid(fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]))
        {
            isError = true;

            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Data field '</b>"
                                               + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                               + "<b>' definition input type '</b>"
                                               + fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]
                                               + "<b>' for owner '</b>"
                                               + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                               + "<b>' unrecognized in import file '</b>"
                                               + fileName
                                               + "<b>'; continue?",
                                               "Data Field Error",
                                               "Ignore this data field error (default to 'Text')",
                                               "Ignore this and any remaining invalid data fields (use default "
                                               + "values where possible, or skip the affected data field)",
                                               "Stop importing",
                                               parent);

            // Default to text
            fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }

        // Check if the applicability is empty
        if (fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].isEmpty())
        {
            // Default to all tables being applicable
            fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = ApplicabilityType.ALL.getApplicabilityName();
        }
        // Check if the applicability is invalid
        else if (ApplicabilityType.getApplicabilityByName(fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()]) == null)
        {
            isError = true;

            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Data field '</b>"
                                               + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                               + "<b>' definition applicability type '</b>"
                                               + fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()]
                                               + "<b>' for owner '</b>"
                                               + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                               + "<b>' unrecognized in import file '</b>"
                                               + fileName
                                               + "<b>'; continue?",
                                               "Data Field Error",
                                               "Ignore this data field error (default to 'All tables')",
                                               "Ignore this and any remaining invalid data fields (use default values)",
                                               "Stop importing",
                                               parent);

            // Default to all tables being applicable
            fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = ApplicabilityType.ALL.getApplicabilityName();
        }

        // Check if the inherited status is empty
        if (fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()].isEmpty())
        {
            // Initialize the status to false (not inherited)
            fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = "false";
        }

        // Check if no error was detected or if the user elected to ignore an error
        if (!isError || continueOnError)
        {
            // Get the reference to the data field from the existing field information
            FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()],
                                                                                fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]);

            // Check if this field already exists
            if (fieldInfo != null)
            {
                // Check if not replacing an existing field and the field's input type, required
                // state, applicability, or value don't match (the description and size are allowed
                // to differ)
                if (!replaceExisting
                    && (!fieldDefn[FieldsColumn.FIELD_DESC.ordinal()].equals(fieldInfo.getDescription())
                        || !fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()].equals(Integer.toString(fieldInfo.getSize()))
                        || !fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].equals(fieldInfo.getInputType().getInputName())
                        || !fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].equalsIgnoreCase(Boolean.toString(fieldInfo.isRequired()))
                        || !fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].equals(fieldInfo.getApplicabilityType().getApplicabilityName())
                        || !fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()].equals(fieldInfo.getValue())))
                {
                    // Check if the error should be ignored or the import canceled
                    continueOnError = getErrorResponse(continueOnError,
                                                       "<html><b>Data field '</b>"
                                                       + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                       + "<b>' for owner '</b>"
                                                       + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                                       + "<b>' doesn't match the existing definition in import file '</b>"
                                                       + fileName
                                                       + "<b>'; continue?",
                                                       "Data Field Error",
                                                       "Ignore this data field (keep existing field)",
                                                       "Ignore this and any remaining invalid data fields "
                                                       + "(use default values or keep existing)",
                                                       "Stop importing",
                                                       parent);

                    // Keep the existing field info
                    fieldDefn[FieldsColumn.FIELD_DESC.ordinal()] = fieldInfo.getDescription();
                    fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()] = Integer.toString(fieldInfo.getSize());
                    fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = fieldInfo.getInputType().getInputName();
                    fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()] = Boolean.toString(fieldInfo.isRequired());
                    fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = fieldInfo.getApplicabilityType().getApplicabilityName();
                    fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()] = fieldInfo.getValue();
                    fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = Boolean.toString(fieldInfo.isInherited());
                }
            }

            // Check if the field belongs to the project
            if (defnContainer instanceof ProjectDefinition)
            {
                // Add the data field to the project
                ((ProjectDefinition) defnContainer).addDataField(fieldDefn);
            }
            // Check if the field belongs to a table
            else if (defnContainer instanceof TableDefinition)
            {
                // Add the data field to the table
                ((TableDefinition) defnContainer).addDataField(fieldDefn);
            }
            // Check if the field belongs to a table type
            else if (defnContainer instanceof TableTypeDefinition)
            {
                // Add the data field to the table type
                ((TableTypeDefinition) defnContainer).addDataField(fieldDefn);
            }
        }

        return continueOnError;
    }

    /**********************************************************************************************
     * Build the project-level and group data fields
     *
     * @param fieldHandler Data field handler reference
     *
     * @param dataFields   List containing the data field definitions from the import file
     *********************************************************************************************/
    protected void buildProjectAndGroupDataFields(CcddFieldHandler fieldHandler, List<String[]> dataFields)
    {
        // Check if any project-level or group data fields exist in the import file
        if (!dataFields.isEmpty())
        {
            boolean isNewField = false;

            // Get the current data field definitions
            List<String[]> fieldDefinitions = fieldHandler.getFieldDefnsFromInfo();

            // Step through each project data field
            for (String[] dataField : dataFields)
            {
                // Add the field definition to the list and set the flag to indicate a new field is
                // added
                fieldDefinitions.add(dataField);
                isNewField = true;
            }

            // Check if a new data field was added
            if (isNewField)
            {
                // Rebuild the field information with the new field(s)
                fieldHandler.setFieldInformationFromDefinitions(fieldDefinitions);
            }
        }
    }

    /**********************************************************************************************
     * Add a group's information from the supplied group definition after verifying the input
     * parameters
     *
     * @param groupDefn             Array containing the group definition
     *
     * @param fileName              Import file name
     *
     * @param replaceExistingGroups True to replace existing group definitions
     *
     * @param groupHandler          Group handler reference
     *
     * @throws CCDDException If the group name or member table list is missing
     *********************************************************************************************/
    protected void addImportedGroupDefinition(String[] groupDefn,
                                              String fileName,
                                              boolean replaceExistingGroups,
                                              CcddGroupHandler groupHandler) throws CCDDException
    {
        // Check if the group name is empty
        if (groupDefn[GroupDefinitionColumn.NAME.ordinal()].isEmpty())
        {
            // Inform the user that the group name is missing
            throw new CCDDException("Group name missing");
        }

        // Get the reference to the data field from the existing field information
        GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupDefn[GroupDefinitionColumn.NAME.ordinal()]);

        // Check if the group with this name already exists and the user has elected to replace
        // existing groups
        if (groupInfo != null && replaceExistingGroups)
        {
            // Remove the existing group
            groupHandler.removeGroupInformation(groupDefn[GroupDefinitionColumn.NAME.ordinal()]);
            groupInfo = null;
        }

        // Check if this is a new group
        if (groupInfo == null)
        {
            // Add the group information
            groupInfo = groupHandler.addGroupInformation(groupDefn[GroupDefinitionColumn.NAME.ordinal()],
                                                         groupDefn[GroupDefinitionColumn.DESCRIPTION.ordinal()],
                                                         Boolean.parseBoolean(groupDefn[GroupDefinitionColumn.IS_APPLICATION.ordinal()]));

            // Check if the group has any table members
            if (!groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].isEmpty())
            {
                // Step through each table member
                for (String member : groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].split(";"))
                {
                    // Add the member to the group
                    groupInfo.addTable(member);
                }
            }
        }
        // A group by this name already exists
        else
        {
            // Get the array of table members, if any
            String[] members = groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].isEmpty() ? new String[] {}
                                                                                            : groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].split(";");

            // Set the flag if the number of members differs
            boolean isMismatch = members.length != groupInfo.getTablesAndAncestors().size();

            // Check if the number of members is the same
            if (!isMismatch)
            {
                // Step through each member
                for (int index = 0; index < members.length; index++)
                {
                    // Check if the member isn't present in the existing group definition
                    if (!groupInfo.getTablesAndAncestors().contains(members[index]))
                    {
                        // Set the flag to indicate the group definitions differ and stop searching
                        isMismatch = true;
                        break;
                    }
                }
            }

            // Check if the existing group's table members or application status don't match (the
            // description is allowed to differ)
            if (isMismatch
                || !groupDefn[GroupDefinitionColumn.IS_APPLICATION.ordinal()].equals(Boolean.toString(groupInfo.isApplication())))
            {
                throw new CCDDException("Imported group '<b>"
                                        + groupDefn[0]
                                        + "</b>' doesn't match the existing definition");
            }
        }
    }

    /**********************************************************************************************
     * Add a script association after verifying the input parameters are valid (script file name is
     * provided and the association doesn't already exist). If an association with the same name
     * but different script file or members exists allow the user to determine how to proceed
     *
     * @param continueOnError             Current state of the flag that indicates if all script
     *                                    association errors should be ignored
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @param associations                List of the current script associations
     *
     * @param assnDefn                    Array containing the script association
     *
     * @param fileName                    Import file name
     *
     * @param scriptHandler               Script handler reference
     *
     * @param parent                      GUI component over which to center any error dialog
     *
     * @return True if the user elected to ignore the data field error
     *
     * @throws CCDDException If the script file name is missing, or an association with the same
     *                       name but different script file or members exists and the user elects
     *                       to stop the import operation
     *********************************************************************************************/
    protected boolean addImportedScriptAssociation(boolean continueOnError,
                                                   boolean replaceExistingAssociations,
                                                   List<String[]> associations,
                                                   String[] assnDefn,
                                                   String fileName,
                                                   CcddScriptHandler scriptHandler,
                                                   Component parent) throws CCDDException
    {
        boolean addAssn = true;

        // Check if the script file name is empty
        if (assnDefn[AssociationsColumn.SCRIPT_FILE.ordinal()].isEmpty())
        {
            // Inform the user that the script file name is missing
            throw new CCDDException("Script file name missing");
        }

        // Get the index of the association having the same script file and members (-1 if there is
        // no matching association)
        int matchingIndex = CcddScriptHandler.getMatchingAssociation(associations,
                                                                     assnDefn[AssociationsColumn.SCRIPT_FILE.ordinal()],
                                                                     assnDefn[AssociationsColumn.MEMBERS.ordinal()].split(Pattern.quote(ASSN_TABLE_SEPARATOR)),
                                                                     -1);

        // Set the index to indicate no name is provided
        int nameIndex = -2;

        // Check if an association name is provided
        if (!assnDefn[AssociationsColumn.NAME.ordinal()].isEmpty())
        {
            // Set the index to indicate a name is provided but doesn't match an existing one
            nameIndex = -1;

            // Step through the association definitions
            for (int index = 0; index < associations.size(); index++)
            {
                // Check if the new association's name matches an existing one's
                if (associations.get(index)[AssociationsColumn.NAME.ordinal()].equals(assnDefn[AssociationsColumn.NAME.ordinal()]))
                {
                    nameIndex = index;
                    break;
                }
            }
        }

        // Check if no association name is provided but an association with matching script file
        // and members already exists (the existing association may or may not have a name)
        if (nameIndex == -2 && matchingIndex != -1)
        {
            // Set the flag to not store this association since it exists
            addAssn = false;
        }
        // Check if the association name is in use or an association with the same script file and
        // members exists
        else if (nameIndex >= 0 || matchingIndex != -1)
        {
            if (replaceExistingAssociations)
            {
                // Find and then overwrite the association
                for (int i = 0; i < associations.size(); i++)
                {
                    if (associations.get(i)[0].equals(assnDefn[0]))
                    {
                        associations.set(i, assnDefn);

                        // Don't add it
                        addAssn = false;

                        // Stop searching
                        break;
                    }
                }
            }
            // Don't accept the new association
            else
            {
                // Set the flag to not store this association since it exists
                addAssn = false;

                // Check if the associations with the same name and script/members don't have the
                // same index
                if (nameIndex != matchingIndex)
                {
                    // Check if the error should be ignored or the import canceled
                    continueOnError = getErrorResponse(continueOnError,
                                                       "<html><b>Script association '</b>"
                                                       + assnDefn[AssociationsColumn.NAME.ordinal()]
                                                       + "<b>' doesn't match the existing association in import file '</b>"
                                                       + fileName
                                                       + "<b>'; continue?",
                                                       "Script Association Error",
                                                       "Ignore this association (keep existing association)",
                                                       "Ignore this and any remaining invalid associations (keep existing)",
                                                       "Stop importing",
                                                       parent);
                }
            }
        }

        // Check if the script association should be added
        if (addAssn)
        {
            // Add the script association
            associations.add(assnDefn);
        }

        return continueOnError;
    }

    /**********************************************************************************************
     * Set the telemetry header table name, command header table name, application ID variable
     * name, and command function code variable name from the project database fields or default
     * values, if not present in the import file. Based on the input flag build the project-level
     * data fields for these names
     *
     * @param fieldHandler  Data field handler reference
     *
     * @param isCreateField True if the project-level data fields are to be created
     *
     * @param tlmHdrTable   Name of the structure table that represents the common header for all
     *                      telemetry tables; null if not present in the import file
     *
     * @param cmdHdrTable   Name of the structure table that represents the common header for all
     *                      command tables; null if not present in the import file
     *
     * @param appIDName     Telemetry and command header variable names for the application ID;
     *                      null if not present in the import file
     *
     * @param funcCodeName  Command header variable name for the command function code; null if not
     *                      present in the import file
     *********************************************************************************************/
    protected void setProjectHeaderTablesAndVariables(CcddFieldHandler fieldHandler,
                                                      boolean isCreateField,
                                                      String tlmHdrTable,
                                                      String cmdHdrTable,
                                                      String appIDName,
                                                      String funcCodeName)
    {
        ProjectDefinition projectDefn = new ProjectDefinition();
        tlmHeaderTable = tlmHdrTable;
        cmdHeaderTable = cmdHdrTable;
        applicationIDName = appIDName;
        cmdFuncCodeName = funcCodeName;

        // Check if the telemetry table name isn't set in the project import file
        if (tlmHeaderTable == null)
        {
            // Get the name of the table representing the telemetry header from the project
            tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                        DefaultInputType.XML_TLM_HDR);
        }
        // The telemetry header table name is set in the import file. Check if the project-level
        // data fields are to be created and the telemetry header table name field doesn't already
        // exist
        else if (isCreateField && fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                             DefaultInputType.XML_TLM_HDR) == null)
        {
            // Add the telemetry header table name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Telemetry header table name",
                                                   "Name of the structure table representing the telemetry header",
                                                   String.valueOf(Math.min(Math.max(tlmHeaderTable.length(), 5), 40)),
                                                   DefaultInputType.XML_TLM_HDR.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   tlmHeaderTable,
                                                   "false"});
        }

        // Check if the command table name isn't set in the project import file
        if (cmdHeaderTable == null)
        {
            // Get the name of the table representing the command header from the project
            cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                        DefaultInputType.XML_CMD_HDR);
        }
        // The command header table name is set in the import file. Check if the project-level data
        // fields are to be created and the command header table name field doesn't already exist
        else if (isCreateField && fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                             DefaultInputType.XML_TLM_HDR) == null)
        {
            // Add the command header table name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Command header table name",
                                                   "Name of the structure table representing the command header",
                                                   String.valueOf(Math.min(Math.max(cmdHeaderTable.length(), 5), 40)),
                                                   DefaultInputType.XML_CMD_HDR.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   cmdHeaderTable,
                                                   "false"});
        }

        // Check if the application ID variable name isn't set in the project import file
        if (applicationIDName == null)
        {
            // Get the application ID variable name from the project field
            applicationIDName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                           DefaultInputType.XML_APP_ID);

            // Check if the application ID variable name isn't set in the project
            if (applicationIDName == null)
            {
                // Use the default application ID variable name
                applicationIDName = DefaultHeaderVariableName.APP_ID.getDefaultVariableName();
            }
        }
        // The application ID variable name is set in the import file. Check if the project-level
        // data fields are to be created and the application ID variable name field doesn't already
        // exist
        else if (isCreateField && fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                             DefaultInputType.XML_TLM_HDR) == null)
        {
            // Add the application ID variable name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Application ID",
                                                   "Name of the variable containing the application ID in the structure "
                                                   + "tables representing the telemetry and command headers",
                                                   String.valueOf(Math.min(Math.max(applicationIDName.length(), 5), 40)),
                                                   DefaultInputType.XML_APP_ID.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   applicationIDName,
                                                   "false"});
        }

        // Check if the command function code variable name isn't set in the import file
        if (cmdFuncCodeName == null)
        {
            // Get the command function code variable name from the project field
            cmdFuncCodeName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                         DefaultInputType.XML_FUNC_CODE);

            // Check if the command function code variable name isn't set in the project
            if (cmdFuncCodeName == null)
            {
                // Use the default command function code variable name
                cmdFuncCodeName = DefaultHeaderVariableName.FUNC_CODE.getDefaultVariableName();
            }
        }
        // The command function code variable name is set in the import file. Check if the
        // project-level data fields are to be created and the command function code variable name
        // field doesn't already exist
        else if (isCreateField && fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                             DefaultInputType.XML_TLM_HDR) == null)
        {
            // Add the application ID variable name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Command function code",
                                                   "Name of the variable containing the command function code in the "
                                                   + "structure table representing the command header",
                                                   String.valueOf(Math.min(Math.max(cmdFuncCodeName.length(), 5), 40)),
                                                   DefaultInputType.XML_FUNC_CODE.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   cmdFuncCodeName,
                                                   "false"});
        }

        // Check if the project-level data fields are to be created
        if (!projectDefn.getDataFields().isEmpty())
        {
            // Build the imported project-level data fields, if any
            buildProjectAndGroupDataFields(fieldHandler, projectDefn.getDataFields());
        }
    }

    /**********************************************************************************************
     * Display an Ignore/Ignore All/Cancel dialog in order to get the response to an error
     * condition. The user may elect to ignore the one instance of this type of error, all
     * instances of this type of error, or cancel the operation
     *
     * @param continueOnError  Current state of the flag that indicates if all errors of this type
     *                         should be ignored
     *
     * @param message          Text message to display
     *
     * @param title            Title to display in the dialog window frame
     *
     * @param ignoreToolTip    Ignore button tool tip text; null if no tool tip is to be displayed
     *
     * @param ignoreAllToolTip Ignore All button tool tip text; null if no tool tip is to be
     *                         displayed
     *
     * @param cancelToolTip    Cancel button tool tip text; null if no tool tip is to be displayed
     *
     * @param parent           GUI component over which to center any error dialog
     *
     * @return True if the user elected to ignore errors of this type
     *
     * @throws CCDDException If the user selects the Cancel button
     *********************************************************************************************/
    protected boolean getErrorResponse(boolean continueOnError,
                                       String message, String title,
                                       String ignoreToolTip,
                                       String ignoreAllToolTip,
                                       String cancelToolTip,
                                       Component parent) throws CCDDException
    {
        return getErrorResponse(continueOnError,
                                message,
                                title,
                                ignoreToolTip,
                                ignoreAllToolTip,
                                cancelToolTip,
                                false,
                                parent);
    }

    /**********************************************************************************************
     * Display an Ignore/Ignore All/Cancel or Ignore All/Cancel dialog in order to get the response
     * to an error condition. The user may elect to ignore the one instance of this type of error,
     * all instances of this type of error, or cancel the operation
     *
     * @param continueOnError  Current state of the flag that indicates if all errors of this type
     *                         should be ignored
     *
     * @param message          Text message to display
     *
     * @param title            Title to display in the dialog window frame
     *
     * @param ignoreToolTip    Ignore button tool tip text; null if no tool tip is to be displayed
     *
     * @param ignoreAllToolTip Ignore All button tool tip text; null if no tool tip is to be
     *                         displayed
     *
     * @param cancelToolTip    Cancel button tool tip text; null if no tool tip is to be displayed
     *
     * @param noIgnore         True to not display the Ignore button
     *
     * @param parent           GUI component over which to center any error dialog
     *
     * @return True if the user elected to ignore errors of this type
     *
     * @throws CCDDException If the user selects the Cancel button
     *********************************************************************************************/
    protected boolean getErrorResponse(boolean continueOnError,
                                       String message,
                                       String title,
                                       String ignoreToolTip,
                                       String ignoreAllToolTip,
                                       String cancelToolTip,
                                       boolean noIgnore,
                                       Component parent) throws CCDDException
    {
        // Check if the user hasn't already elected to ignore this type of error
        if (!continueOnError)
        {
            // Inform the user that the imported item is incorrect
            int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                message,
                                                                                title,
                                                                                ignoreToolTip,
                                                                                ignoreAllToolTip,
                                                                                cancelToolTip,
                                                                                noIgnore);

            // Check if the Ignore All button was pressed
            if (buttonSelected == IGNORE_BUTTON)
            {
                // Set the flag to ignore subsequent errors of this type
                continueOnError = true;
            }
            // Check if the Cancel button was pressed
            else if (buttonSelected == CANCEL_BUTTON)
            {
                // No error message is provided since the user chose this action
                throw new CCDDException();
            }
        }

        return continueOnError;
    }

    /**********************************************************************************************
     * Check input type definition parameters for validity
     *
     * @param inputTypeDefn Array containing the input type definition
     *
     * @return The input type definition, with the regular expression built if the definition
     *         contains a selection item list
     *
     * @throws CCDDException If an invalid input type parameter is detected
     *********************************************************************************************/
    protected String[] checkInputTypeDefinition(String[] inputTypeDefn) throws CCDDException
    {
        // Check if the input type name is empty
        if (inputTypeDefn[InputTypesColumn.NAME.ordinal()].isEmpty())
        {
            // Inform the user that the input type name is missing
            throw new CCDDException("Input type name missing");
        }

        // Check if the input type format is empty
        if (inputTypeDefn[InputTypesColumn.FORMAT.ordinal()].isEmpty())
        {
            // Inform the user that the input type format is missing
            throw new CCDDException("Input type '"
                                    + inputTypeDefn[InputTypesColumn.NAME.ordinal()]
                                    + "' format missing");
        }

        // Check if the input type selection item list is provided
        if (!inputTypeDefn[InputTypesColumn.ITEMS.ordinal()].isEmpty())
        {
            // Convert the items in the selection list to the corresponding regular expression
            inputTypeDefn[InputTypesColumn.MATCH.ordinal()] = CcddInputTypeHandler.convertItemsToRegEx(inputTypeDefn[InputTypesColumn.ITEMS.ordinal()]);
        }

        // Check if the input type name is empty
        if (inputTypeDefn[InputTypesColumn.MATCH.ordinal()].isEmpty())
        {
            // Inform the user that the input type regular expression is missing
            throw new CCDDException("Input type '"
                                    + inputTypeDefn[InputTypesColumn.NAME.ordinal()]
                                    + "' regular expression missing");
        }

        try
        {
            // Validate the regular expression by attempting to compile it
            Pattern.compile(inputTypeDefn[InputTypesColumn.MATCH.ordinal()]);
        }
        catch (PatternSyntaxException pse)
        {
            throw new CCDDException("Input type '"
                                    + inputTypeDefn[InputTypesColumn.NAME.ordinal()]
                                    + "' regular expression invalid; cause '</b>"
                                    + pse.getMessage()
                                    + "<b>'");
        }

        boolean isValid = false;

        // Step through each input type format
        for (InputTypeFormat type : InputTypeFormat.values())
        {
            // Check if the format is recognized
            if (type.getFormatName().equals(inputTypeDefn[InputTypesColumn.FORMAT.ordinal()]))
            {
                // Check if the format type is user-selectable (i.e., not an internal-only format),
                // and the format is valid with selection item if the items are provided
                if (type.isUserSelectable()
                    && (inputTypeDefn[InputTypesColumn.ITEMS.ordinal()].isEmpty()
                        || type.isValidWithItems()))
                {
                    // Set the flag to indicate the format is valid
                    isValid = true;
                }

                break;
            }
        }

        // Check if the input type format is invalid
        if (!isValid)
        {
            // Inform the user that the input type format is invalid
            throw new CCDDException("Input type '"
                                    + inputTypeDefn[InputTypesColumn.NAME.ordinal()]
                                    + "' format invalid");
        }

        return inputTypeDefn;
    }

    /**********************************************************************************************
     * Check the supplied macro definition parameters for validity
     *
     * @param macroDefn Array containing the macro definition
     *
     * @return result Should macro be imported?
     *
     * @throws CCDDException If an invalid macro parameter is detected
     *********************************************************************************************/
    protected boolean checkMacroDefinition(String[] macroDefn) throws CCDDException
    {
        boolean result = true;

        // Check if the macro name is empty
        if (macroDefn[MacrosColumn.MACRO_NAME.ordinal()].isEmpty())
        {
            // Inform the user that the macro name is missing
            throw new CCDDException("Macro name missing");
        }

        // Removing any spaces between the name and first left parenthesis
        macroDefn[MacrosColumn.MACRO_NAME.ordinal()] = macroDefn[MacrosColumn.MACRO_NAME.ordinal()].replaceFirst("\\s+\\(", "(");

        // Check if the macro name isn't valid
        if (!macroDefn[MacrosColumn.MACRO_NAME.ordinal()].matches(DefaultInputType.MACRO_NAME.getInputMatch()))
        {
            // Macro name is invalid
            result = false;
        }

        return result;
    }

    /**********************************************************************************************
     * Check the supplied data type definition parameters for validity
     *
     * @param dataTypeDefn Array containing the data type definition
     *
     * @throws CCDDException If an invalid data type parameter is detected
     *********************************************************************************************/
    protected void checkDataTypeDefinition(String[] dataTypeDefn) throws CCDDException
    {
        String size = dataTypeDefn[DataTypesColumn.SIZE.ordinal()];

        // Check if the data type names are both empty
        if (dataTypeDefn[DataTypesColumn.C_NAME.ordinal()].isEmpty()
            && dataTypeDefn[DataTypesColumn.USER_NAME.ordinal()].isEmpty())
        {
            // Inform the user that the data type name is missing
            throw new CCDDException("Data type user and C names missing");
        }

        // Check if the data type size is a macro and if it is valid
        if (size.contains("##"))
        {
            size = macroHandler.getMacroExpansion(dataTypeDefn[DataTypesColumn.SIZE.ordinal()]);
        }
        if (!size.matches(DefaultInputType.INT_POSITIVE.getInputMatch()))
        {
            // Inform the user that the data type size is invalid
            throw new CCDDException("Data type '"
                                    + CcddDataTypeHandler.getDataTypeName(dataTypeDefn[DataTypesColumn.C_NAME.ordinal()],
                                                                          dataTypeDefn[DataTypesColumn.USER_NAME.ordinal()])
                                    + "' size invalid");
        }

        // Check if the base type isn't valid
        if (BaseDataTypeInfo.getBaseType(dataTypeDefn[DataTypesColumn.BASE_TYPE.ordinal()]) == null)
        {
            // Inform the user that the base type is invalid
            throw new CCDDException("Data type '"
                                    + CcddDataTypeHandler.getDataTypeName(dataTypeDefn[DataTypesColumn.C_NAME.ordinal()],
                                                                          dataTypeDefn[DataTypesColumn.USER_NAME.ordinal()])
                                    + "' base type invalid");
        }
    }

    /**********************************************************************************************
     * Convert the primitive data type into the base equivalent
     *
     * @param dataType        Data type
     *
     * @param dataTypeHandler Reference to the data type handler
     *
     * @return Base primitive data type corresponding to the specified primitive data type; null if
     *         no match
     *********************************************************************************************/
    protected static BasePrimitiveDataType getBaseDataType(String dataType,
                                                           CcddDataTypeHandler dataTypeHandler)
    {
        BasePrimitiveDataType basePrimitiveDataType = null;

        // Check if the type is an integer (signed or unsigned)
        if (dataTypeHandler.isInteger(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.INTEGER;
        }
        // Check if the type is a floating point (float or double)
        else if (dataTypeHandler.isFloat(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.FLOAT;
        }
        // Check if the type is a string (character or string)
        else if (dataTypeHandler.isCharacter(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.STRING;
        }

        return basePrimitiveDataType;
    }

    /**********************************************************************************************
     * Get the data type name determined by the specified data type size and match criteria
     *
     * @param sizeInBytes     Data type size in bytes
     *
     * @param isInteger       True if the data type to match is an integer
     *
     * @param isUnsigned      True if the data type to match is an unsigned integer
     *
     * @param isFloat         True if the data type to match is a floating point
     *
     * @param isString        True if the data type to match is a character or string
     *
     * @param dataTypeHandler Reference to the data type handler
     *
     * @return The name of the data type from the existing data type definitions that matches the
     *         input criteria; null if there is no match
     *********************************************************************************************/
    protected String getMatchingDataType(long sizeInBytes,
                                         boolean isInteger,
                                         boolean isUnsigned,
                                         boolean isFloat,
                                         boolean isString, CcddDataTypeHandler dataTypeHandler)
    {
        String dataType = null;

        // Step through each defined data type
        for (String[] dataTypeDefn : dataTypeHandler.getDataTypeData())
        {
            String dataTypeName = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);

            // Check if the type to match is a string (vs a character)
            if (isString && sizeInBytes > 1 && dataTypeHandler.isString(dataTypeName))
            {
                // Store the matching string data type and stop searching
                dataType = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);
                break;
            }

            // Check if the size in bytes matches the one for this data type
            if (sizeInBytes == dataTypeHandler.getDataTypeSize(dataTypeName))
            {
                // Check if the type indicated by the input flags matches the data type
                if ((isInteger && !isUnsigned && dataTypeHandler.isInteger(dataTypeName))
                    || (isInteger && isUnsigned && dataTypeHandler.isUnsignedInt(dataTypeName))
                    || (isFloat && dataTypeHandler.isFloat(dataTypeName))
                    || (isString && dataTypeHandler.isCharacter(dataTypeName)))
                {
                    // Store the matching data type and stop searching
                    dataType = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);
                    break;
                }
            }
        }

        return dataType;
    }

    /**********************************************************************************************
     * Replace each invalid character with an underscore and move any leading underscores to the
     * end of each path segment
     *
     * @param path System path in the form {@literal <</>path1</path2<...>>}
     *
     * @return Path with each invalid character replaced with an underscore and any leading
     *         underscores moved to the end of each path segment
     *********************************************************************************************/
    protected static String cleanSystemPath(String path)
    {
        // Check if the path exists
        if (path != null)
        {
            // Replace each space with an underscore and move any leading underscores to the end of
            // each path segment
            path = path.replaceAll("\\]", "")
                       .replaceAll("[^A-Za-z0-9_\\-\\/]", "_")
                       .replaceAll("(^|/)_([^/]*)", "$1$2_");
        }

        return path;
    }

    /**********************************************************************************************
     * Convert a list to a unique list and detect if there are any duplicate entries
     *
     * @param NewMacroDefns A list of string arrays containing information
     *
     * @return Pair containing a list of unique values and a Boolean indicating if the set was
     *         unique
     *********************************************************************************************/
    protected ImmutablePair<Boolean, List<String[]>> convertToUniqueList(List<String[]> NewMacroDefns)
    {
        if (NewMacroDefns == null)
        {
            return null;
        }
        if (NewMacroDefns.isEmpty())
        {
            return null;
        }

        boolean isDuplicate = true;
        Set<String> uniqueSet = new HashSet<String>();
        List<String[]> uniqueMacroList = new ArrayList<>();

        // Go through each entry in the list
        for (String[] macros : NewMacroDefns)
        {
            // Bad input in the string array, exit
            if (macros == null)
            {
                return null;
            }
            String macroName = macros[0];
            // Check if it is in the unique set already
            if (uniqueSet.contains(macroName))
            {
                // Mark the flag if it is
                isDuplicate = false;
            }
            else
            {
                // Otherwise add this unique value to the list
                uniqueMacroList.add(macros);
                // And the set
                uniqueSet.add(macroName);
            }
        }
        return new ImmutablePair<>(isDuplicate, uniqueMacroList);
    }

    /**********************************************************************************************
     * Create and display the table export progress dialog
     *
     * @param numTables Number of tables being exported
     *
     * @param parent    Component over which to center the export progress dialog
     *********************************************************************************************/
    public void createExportProgressDialog(int numTables, Component parent)
    {
        haltDlg = new CcddHaltDialog("Export Data",
                                     "Exporting data",
                                     "export",
                                     1,
                                     numTables,
                                     parent);

        haltDlg.updateProgressBar("Exporting table(s)...");
    }

    /**********************************************************************************************
     * Update the table export progress dialog
     *
     * @param tableName Name of the table currently being exported
     *
     * @throws CCDDException if the user pressed the dialog's Halt button
     *********************************************************************************************/
    public void updateExportProgress(String tableName) throws CCDDException
    {
        // Check if the halt dialog is active (export operation is executed in the background)
        if (haltDlg != null)
        {
            // Check if the user canceled exporting
            if (haltDlg.isHalted())
            {
                throw new CCDDException("Export canceled by user");
            }

            // Update the progress bar
            haltDlg.updateProgressBar("Export table " + tableName);
        }
    }

    /**********************************************************************************************
     * Set the export dialog progress bar maximum value
     *
     * @param maximum Progress bar maximum value
     *********************************************************************************************/
    public void setProgressMaximum(int maximum)
    {
        // Check if the halt dialog is displayed
        if (haltDlg != null)
        {
            // Close the cancellation dialog
            haltDlg.setMaximum(maximum);
        }
    }

    /**********************************************************************************************
     * Close the table export progress dialog
     *********************************************************************************************/
    public void closeExportProgressDialog()
    {
        // Check if the halt dialog is displayed
        if (haltDlg != null)
        {
            // Close the cancellation dialog
            haltDlg.closeDialog();
        }
    }
}
