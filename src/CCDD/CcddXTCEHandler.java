/**************************************************************************************************
 * /** \file CcddXTCEHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling import and export of data tables in XTCE XML format. This class
 * implements the CcddImportExportInterface class.
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

import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.omg.spec.xtce._20180204.AggregateArgumentType;
import org.omg.spec.xtce._20180204.AggregateParameterType;
import org.omg.spec.xtce._20180204.AncillaryDataSetType;
import org.omg.spec.xtce._20180204.AncillaryDataType;
import org.omg.spec.xtce._20180204.ArgumentType;
import org.omg.spec.xtce._20180204.ArgumentTypeSetType;
import org.omg.spec.xtce._20180204.ArrayParameterType;
import org.omg.spec.xtce._20180204.AuthorSetType;
import org.omg.spec.xtce._20180204.BasisType;
import org.omg.spec.xtce._20180204.BitOrderType;
import org.omg.spec.xtce._20180204.CommandMetaDataType;
import org.omg.spec.xtce._20180204.DimensionListType;
import org.omg.spec.xtce._20180204.DimensionType;
import org.omg.spec.xtce._20180204.EnumeratedParameterType;
import org.omg.spec.xtce._20180204.EnumerationListType;
import org.omg.spec.xtce._20180204.FloatDataEncodingType;
import org.omg.spec.xtce._20180204.FloatEncodingType;
import org.omg.spec.xtce._20180204.FloatParameterType;
import org.omg.spec.xtce._20180204.HeaderType;
import org.omg.spec.xtce._20180204.IntegerDataEncodingType;
import org.omg.spec.xtce._20180204.IntegerEncodingType;
import org.omg.spec.xtce._20180204.IntegerParameterType;
import org.omg.spec.xtce._20180204.IntegerValueType;
import org.omg.spec.xtce._20180204.MemberListType;
import org.omg.spec.xtce._20180204.MemberType;
import org.omg.spec.xtce._20180204.MetaCommandSetType;
import org.omg.spec.xtce._20180204.MetaCommandType;
import org.omg.spec.xtce._20180204.NameDescriptionType;
import org.omg.spec.xtce._20180204.NoteSetType;
import org.omg.spec.xtce._20180204.ObjectFactory;
import org.omg.spec.xtce._20180204.ParameterSetType;
import org.omg.spec.xtce._20180204.ParameterType;
import org.omg.spec.xtce._20180204.ParameterTypeSetType;
import org.omg.spec.xtce._20180204.RateInStreamType;
import org.omg.spec.xtce._20180204.SequenceContainerType;
import org.omg.spec.xtce._20180204.SizeInBitsType;
import org.omg.spec.xtce._20180204.SizeInBitsType.Fixed;
import org.omg.spec.xtce._20180204.SpaceSystemType;
import org.omg.spec.xtce._20180204.StringDataEncodingType;
import org.omg.spec.xtce._20180204.StringEncodingType;
import org.omg.spec.xtce._20180204.StringParameterType;
import org.omg.spec.xtce._20180204.TelemetryMetaDataType;
import org.omg.spec.xtce._20180204.UnitSetType;
import org.omg.spec.xtce._20180204.UnitType;
import org.omg.spec.xtce._20180204.ValidationStatusType;
import org.omg.spec.xtce._20180204.ValueEnumerationType;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary XTCE handler class
 *************************************************************************************************/
public class CcddXTCEHandler extends CcddImportExportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;

    // Export endian type
    private EndianType endianess;

    // Lists containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and XTCE object references
    private JAXBElement<SpaceSystemType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;
    private SpaceSystemType rootSystem;

    // Flag to indicate if the telemetry and command headers are big endian (as with CCSDS)
    private boolean isHeaderBigEndian;

    // Text appended to the parameter and command type and array references
    private static String TYPE = "_Type";
    private static String ARRAY = "_Array";

    // Command argument structure designator
    private static String CMD_ARG_STRUCT = "Command Argument Structure";

    // Ancillary data set key names
    private static String DATA_TYPE_NAME = "dataTypeName";
    private static String RANGE_MINIMUM = "rangeMinimum";
    private static String RANGE_MAXIMUM = "rangeMaximum";

    // Key name for Ancillary data key:value pairings
    private static final String MESSAGE_FIELD_KEY = "messageNameAndIdFieldName";
    private static final String MESSAGE_NAME_AND_ID_KEY = "messageNameAndId";

    // Separator between a variable name and rate column name
    private static String NAME_RATE_SEPARATOR = "-";

    /**********************************************************************************************
     * Parameter reference information class
     *********************************************************************************************/
    private class ParameterInformation
    {
        String parameterName;
        String dataType;
        String arraySize;
        String bitLength;
        String enumeration;
        String units;
        String minimum;
        String maximum;
        String description;
        String[] rates;
        int numArrayMembers;

        /**********************************************************************************************
         * Parameter information class constructor for telemetry and argument references
         *
         * @param parameterName   Parameter name
         *
         * @param dataType        Data type
         *
         * @param arraySize       Parameter array size
         *
         * @param bitLength       Parameter bit length
         *
         * @param enumeration     Enumeration in the format
         *                        {@literal <enum label>|<enum value>[|...][,...]}
         *
         * @param units           Parameter units
         *
         * @param minimum         Minimum parameter value
         *
         * @param maximum         Maximum parameter value
         *
         * @param description     Parameter description
         *
         * @param rates           Parameter rate(s)
         *
         * @param numArrayMembers Number of array members; 0 if not an array parameter
         *********************************************************************************************/
        ParameterInformation(String parameterName,
                             String dataType,
                             String arraySize,
                             String bitLength,
                             String enumeration,
                             String units,
                             String minimum,
                             String maximum,
                             String description,
                             String[] rates,
                             int numArrayMembers)
        {
            this.parameterName = parameterName;
            this.dataType = dataType;
            this.arraySize = arraySize;
            this.bitLength = bitLength;
            this.enumeration = enumeration;
            this.units = units;
            this.minimum = minimum;
            this.maximum = maximum;
            this.description = description;
            this.rates = rates;
            this.numArrayMembers = numArrayMembers;
        }

        /******************************************************************************************
         * Get the variable or argument name
         *
         * @return Variable or argument name
         *
         *****************************************************************************************/
        protected String getParameterName()
        {
            return parameterName;
        }

        /******************************************************************************************
         * Get the parameter data type
         *
         * @return Parameter data type
         *****************************************************************************************/
        protected String getDataType()
        {
            return dataType;
        }

        /******************************************************************************************
         * Get the parameter array size
         *
         * @return Parameter array size
         *****************************************************************************************/
        protected String getArraySize()
        {
            return arraySize;
        }

        /******************************************************************************************
         * Get the parameter bit length
         *
         * @return Parameter bit length
         *****************************************************************************************/
        protected String getBitLength()
        {
            return bitLength;
        }

        /******************************************************************************************
         * Get the parameter enumeration
         *
         * @return Parameter enumeration
         *****************************************************************************************/
        protected String getEnumeration()
        {
            return enumeration;
        }

        /******************************************************************************************
         * Get the parameter units
         *
         * @return Parameter units
         *****************************************************************************************/
        protected String getUnits()
        {
            return units;
        }

        /******************************************************************************************
         * Get the parameter minimum
         *
         * @return Parameter minimum
         *****************************************************************************************/
        protected String getMinimum()
        {
            return minimum;
        }

        /******************************************************************************************
         * Get the parameter maximum
         *
         * @return Parameter maximum
         *****************************************************************************************/
        protected String getMaximum()
        {
            return maximum;
        }

        /******************************************************************************************
         * Get the parameter description
         *
         * @return Parameter description
         *****************************************************************************************/
        protected String getDescription()
        {
            return description;
        }

        /******************************************************************************************
         * Get the parameter rate(s)
         *
         * @return Parameter rate(s) array
         *****************************************************************************************/
        protected String[] getRates()
        {
            return rates;
        }

        /******************************************************************************************
         * Get the number of array members; 0 if not an array parameter
         *
         * @return Number of array members; 0 if not an array parameter
         *****************************************************************************************/
        protected int getNumArrayMembers()
        {
            return numArrayMembers;
        }
    }

    /**********************************************************************************************
     * XTCE handler class constructor
     *
     * @param ccddMain     Main class
     *
     * @param parent       GUI component over which to center any error dialog
     *
     * @throws CCDDException If an error occurs creating the handler
     *********************************************************************************************/
    CcddXTCEHandler(CcddMain ccddMain, Component parent) throws CCDDException
    {
        this.ccddMain = ccddMain;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        macroHandler = ccddMain.getMacroHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

        structureTypeDefn = tableTypeHandler.getTypeDefinition(TYPE_STRUCTURE);
        commandTypeDefn = tableTypeHandler.getTypeDefinition(TYPE_COMMAND);

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into XTCE XML format
            JAXBContext context = JAXBContext.newInstance("org.omg.spec.xtce._20180204",
                                                           org.omg.spec.xtce._20180204.ObjectFactory.class.getClassLoader());
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   ModifiableOtherSettingInfo.XTCE_SCHEMA_LOCATION_URL.getValue());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // Create the factory for building the space system objects
            factory = new ObjectFactory();

            // Create the XML unmarshaller used to convert XTCE XML data into CCDD project data
            // format
            unmarshaller = context.createUnmarshaller();
        }
        catch (JAXBException je)
        {
            // Inform the user that the XTCE/JAXB set up failed
            throw new CCDDException("XTCE conversion setup failed; cause '</b>" + je.getMessage() + "<b>'");
        }
    }

    /**********************************************************************************************
     * Get the imported table definitions
     *
     * @return List of imported table definitions; an empty list if no table definitions exist in
     *         the import file
     *********************************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
    }

    /**********************************************************************************************
     * Get the list of original and new script associations. Not used for EDS import
     *
     * @return List of original and new script associations; null if no new associations have been
     *         added
     *********************************************************************************************/
    @Override
    public List<String[]> getScriptAssociations()
    {
        return null;
    }

    /**********************************************************************************************
     * Get the list of original and new telemetry scheduler data
     *
     * @return List of original and new telemetry scheduler data; null if no new associations have
     *         been added
     *********************************************************************************************/
    @Override
    public List<String[]> getTlmSchedulerData()
    {
        return null;
    }

    /**********************************************************************************************
     * Get the list of original and new application scheduler data
     *
     * @return List of original and new application scheduler data; null if no new associations
     *         have been added
     *********************************************************************************************/
    @Override
    public List<String[]> getAppSchedulerData()
    {
        return null;
    }

    /**********************************************************************************************
     * Build the information from the internal table in the current file
     *
     * @param importFile                  Import file reference
     *
     * @param importType                  ImportType.IMPORT_ALL to import the table type, data
     *                                    type, and macro definitions, and the data from all the
     *                                    table definitions; ImportType.FIRST_DATA_ONLY to load
     *                                    only the data for the first table defined
     *
     * @param ignoreErrors                True to ignore all errors in the import file
     *
     * @param replaceExistingAssociations True to overwrite internal associations with those from
     *                                    the import file
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importInternalTables(FileEnvVar importFile,
                                     ImportType importType,
                                     boolean ignoreErrors,
                                     boolean replaceExistingAssociations) throws CCDDException,
                                                                                 IOException,
                                                                                 Exception
    {
        // Not implemented
        return;
    }

    /**********************************************************************************************
     * Import the input types, table types, table type data fields and data types from the given
     * file
     *
     * @param importFile            Import file reference
     *
     * @param ignoreErrors          True to ignore all errors in the import file
     *
     * @param replaceExistingMacros True to replace existing macros
     *
     * @param replaceExistingTables True to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importTableInfo(FileEnvVar importFile,
                                ImportType importType,
                                boolean ignoreErrors,
                                boolean replaceExistingMacros,
                                boolean replaceExistingTables,
                                boolean importingEntireDatabase) throws CCDDException,
                                                                        IOException,
                                                                        Exception
    {
        // Not implemented
        return;
    }

    /**********************************************************************************************
     * Build the information from the input and data type definition(s) in the current file
     *
     * @param importFile               Import file reference
     *
     * @param ignoreErrors             True to ignore all errors in the import file
     *
     * @param replaceExistingDataTypes True to replace existing data types that share a name with
     *                                 an imported data type
     *
     * @param importingEntireDatabase  True to replace existing database internal tables
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importInputTypes(FileEnvVar importFile,
                                 ImportType importType,
                                 boolean ignoreErrors,
                                 boolean replaceExistingDataTypes,
                                 boolean importingEntireDatabase) throws CCDDException,
                                                                         IOException,
                                                                         Exception
    {
        // Will not implement
        return;
    }

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile            Import file reference
     *
     * @param importType            ImportType.IMPORT_ALL to import the table type, data type, and
     *                              macro definitions, and the data from all the table definitions;
     *                              ImportType.FIRST_DATA_ONLY to load only the data for the first
     *                              table defined
     *
     * @param targetTypeDefn        Table type definition of the table in which to import the data;
     *                              ignored if importing all tables
     *
     * @param ignoreErrors          True to ignore all errors in the import file
     *
     * @param replaceExistingMacros True to replace the values for existing macros
     *
     * @param replaceExistingGroups True to replace existing group definitions
     *
     * @param replaceExistingTables True to replace existing tables or table fields
     *
     * @throws CCDDException If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException   If an import file I/O error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile,
                               ImportType importType,
                               TypeDefinition targetTypeDefn,
                               boolean ignoreErrors,
                               boolean replaceExistingMacros,
                               boolean replaceExistingGroups,
                               boolean replaceExistingTables) throws CCDDException,
                                                                     IOException,
                                                                     Exception
    {
        try
        {
            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            // Get the top-level space system
            rootSystem = (SpaceSystemType) jaxbElement.getValue();

            tableDefinitions = new ArrayList<TableDefinition>();
            structureTypeDefn = null;
            commandTypeDefn = null;
            cmdArgStructTypeDefn = null;
            tlmHeaderTable = null;
            cmdHeaderTable = null;
            applicationIDName = null;
            cmdFuncCodeName = null;

            if (rootSystem.getAncillaryDataSet() != null)
            {
                AncillaryDataSetType ancillarySet = rootSystem.getAncillaryDataSet();

                // Get the telemetry header table name, if present
                tlmHeaderTable = getAncillaryDataValue(ancillarySet, DefaultInputType.XML_TLM_HDR.getInputName());

                // Get the command header table name, if present
                cmdHeaderTable = getAncillaryDataValue(ancillarySet, DefaultInputType.XML_CMD_HDR.getInputName());

                // Get the application ID variable name, if present
                applicationIDName = getAncillaryDataValue(ancillarySet, DefaultInputType.XML_APP_ID.getInputName());

                // Get the command function code variable name, if present
                cmdFuncCodeName = getAncillaryDataValue(ancillarySet, DefaultInputType.XML_FUNC_CODE.getInputName());
            }

            // Set the header table names and variables from the project database data fields or
            // default values, if not present in the import file. If importing all tables then add
            // these as project-level data fields to the database
            setProjectHeaderTablesAndVariables(fieldHandler,
                                               importType == ImportType.IMPORT_ALL,
                                               tlmHeaderTable,
                                               cmdHeaderTable,
                                               applicationIDName,
                                               cmdFuncCodeName);

            // Create the table type definitions for any new structure and command tables
            createTableTypeDefinitions(importFile, importType, targetTypeDefn);

            // Check if at least one structure or command table needs to be built
            if (structureTypeDefn != null || commandTypeDefn != null || cmdArgStructTypeDefn != null)
            {
                // Step through each space system
                for (SpaceSystemType system : rootSystem.getSpaceSystem())
                {
                    // Recursively step through the XTCE-formatted data and extract the telemetry
                    // and command information
                    unbuildSpaceSystems(system, importType);

                    // Check if only the data from the first table of the target table type is to
                    // be read
                    if (importType == ImportType.FIRST_DATA_ONLY && !tableDefinitions.isEmpty())
                    {
                        // Stop reading table definitions
                        break;
                    }
                }
            }
        }
        catch (JAXBException je)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + je.getMessage() + "<b>'");
        }
    }

    /**********************************************************************************************
     * Extract the telemetry and/or command information from the space system. This is a recursive
     * method
     *
     * @param spaceSystem     Space system to which the new system belongs
     *
     * @param importType      Import type: ImportType.ALL to import all information in the import
     *                        file; ImportType.FIRST_DATA_ONLY to import data from the first table
     *                        defined in the import file
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void unbuildSpaceSystems(SpaceSystemType spaceSystem,
                                     ImportType importType) throws CCDDException
    {
        // Check if the space system represents a structure or command argument structure
        if (spaceSystem.getBase().equals(TYPE_STRUCTURE)
            || spaceSystem.getBase().equals(CMD_ARG_STRUCT))
        {
            // Build the structure table from the telemetry data
            importStructureTable(spaceSystem);
        }
        // Check if this is a command table
        else if (spaceSystem.getBase().equals(TYPE_COMMAND))
        {
            // Build the command table from the telemetry data
            importCommandTable(spaceSystem);
        }

        // Check if the data from all tables is to be read or no table of the target type has been
        // located yet
        if (importType == ImportType.IMPORT_ALL || tableDefinitions.isEmpty())
        {
            // Step through each child system, if any
            for (SpaceSystemType childSystem : spaceSystem.getSpaceSystem())
            {
                // Process this system's children, if any
                unbuildSpaceSystems(childSystem, importType);
            }
        }
    }

    /**********************************************************************************************
     * Build a structure table from the specified telemetry metadata
     *
     * @param spaceSystem Space system
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void importStructureTable(SpaceSystemType spaceSystem) throws CCDDException
    {
        Integer[] rateIndices = null;
        TypeDefinition typeDefn = null;

        // Get the table name, including its full path
        String tablePath = convertSchemaNameToCcddName(spaceSystem.getName());

        // Create a table definition for this structure table. If the name space also includes a
        // command metadata (which creates a command table) then ensure the two tables have
        // different names
        TableDefinition tableDefn = new TableDefinition(tablePath,
                                                        spaceSystem.getLongDescription());

        // Set the new structure table's table type name
        if (spaceSystem.getBase().equals(CMD_ARG_STRUCT))
        {
            tableDefn.setTypeName(cmdArgStructTypeDefn.getName());
            rateIndices = cmdArgRateIndices;
            typeDefn = cmdArgStructTypeDefn;
        }
        else
        {
            tableDefn.setTypeName(structureTypeDefn.getName());
            rateIndices = structRateIndices;
            typeDefn = structureTypeDefn;
        }

        // Create a data field for the table containing the message name and ID
        String messageFieldName = getAncillaryDataValue(spaceSystem.getAncillaryDataSet(), MESSAGE_FIELD_KEY);
        String messageNameAndID = getAncillaryDataValue(spaceSystem.getAncillaryDataSet(), MESSAGE_NAME_AND_ID_KEY);

        if (messageFieldName != null && messageNameAndID != null)
        {
            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tablePath,
                                                                            messageFieldName,
                                                                            "Message name and ID",
                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                            Math.min(Math.max(messageNameAndID.length(), 5), 40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            messageNameAndID,
                                                                            false));
        }

        // Get the child system's telemetry metadata information
        TelemetryMetaDataType tlmMetaData = spaceSystem.getTelemetryMetaData();

        // Check if the telemetry metadata exists. An empty table will not have this section
        if (tlmMetaData != null)
        {
            // Get the telemetry information
            ParameterSetType parmSetType = tlmMetaData.getParameterSet();
            ParameterTypeSetType parmTypeSetType = tlmMetaData.getParameterTypeSet();
            List<Object> parmSet = null;
            List<NameDescriptionType> parmTypeSet = null;

            // Check if the telemetry information exists
            if (parmSetType != null && parmTypeSetType != null)
            {
                // Get the references to the parameter set and parameter type set
                parmSet = parmSetType.getParameterOrParameterRef();
                parmTypeSet = parmTypeSetType.getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType();

                if (parmSet != null && parmTypeSet != null)
                {
                    int rowIndex = 0;
                    List<SequenceContainerType> seqContSet = null;

                    // Get the sequence container list, if present
                    if (tlmMetaData.getContainerSet() != null
                        && tlmMetaData.getContainerSet().getSequenceContainer() != null)
                    {
                        seqContSet = tlmMetaData.getContainerSet().getSequenceContainer();
                    }

                    // Step through each parameter in the ParameterSet
                    for (Object parmObj : parmSet)
                    {
                        ParameterType parameter = (ParameterType) parmObj;

                        // Step through each parameter in the ParameterTypeSet
                        for (NameDescriptionType parmType : parmTypeSet)
                        {
                            ParameterInformation parmInfo = null;

                            // Check if the type matches the parameter's reference
                            if (parmType.getName().equals(parameter.getParameterTypeRef()))
                            {
                                // Get the parameter information referenced by the parameter type
                                parmInfo = processParameterReference(parameter,
                                                                     parmType,
                                                                     parmTypeSet,
                                                                     seqContSet,
                                                                     typeDefn,
                                                                     rateIndices);

                                // Check if the parameter is valid
                                if (parmInfo != null)
                                {
                                    // Add the row to the structure table. Multiple rows are added
                                    // for an array
                                    rowIndex = addVariableDefinitionToStructure(tableDefn,
                                                                                rowIndex,
                                                                                parmInfo);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add the structure table definition to the list
        tableDefinitions.add(tableDefn);
    }

    /**********************************************************************************************
     * Build a command table from the specified command metadata
     *
     * @param spaceSystem Space system
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void importCommandTable(SpaceSystemType spaceSystem) throws CCDDException
    {
        // Get the space system name
        String tableName = convertSchemaNameToCcddName(spaceSystem.getName());

        // Create a table definition for this command table. If the name space also includes a
        // telemetry metadata (which creates a structure table) then ensure the two tables have
        // different names
        TableDefinition cmdTableDefn = new TableDefinition(tableName,
                                                           spaceSystem.getLongDescription());
        // Set the new command table's table type name
        cmdTableDefn.setTypeName(commandTypeDefn.getName());

        // Create a data field for the table containing the message name and ID
        String messageFieldName = getAncillaryDataValue(spaceSystem.getAncillaryDataSet(), MESSAGE_FIELD_KEY);
        String messageNameAndID = getAncillaryDataValue(spaceSystem.getAncillaryDataSet(), MESSAGE_NAME_AND_ID_KEY);

        if (messageFieldName != null && messageNameAndID != null)
        {
            cmdTableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                               messageFieldName,
                                                                               "Message name and ID",
                                                                               inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                               Math.min(Math.max(messageNameAndID.length(), 5), 40),
                                                                               false,
                                                                               ApplicabilityType.ALL,
                                                                               messageNameAndID,
                                                                               false));
        }

        // Check if the command metadata and command set exist. An empty table will not have these
        // sections
        if (spaceSystem.getCommandMetaData() != null
            && spaceSystem.getCommandMetaData().getMetaCommandSet() != null)
        {
            // Get the child system's command metadata and command set information
            CommandMetaDataType cmdMetaData = spaceSystem.getCommandMetaData();
            MetaCommandSetType metaCmdSet = cmdMetaData.getMetaCommandSet();

            // Step through each command set
            for (Object cmd : metaCmdSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand())
            {
                // Check if the command represents a meta command type (all of these should)
                if (cmd instanceof JAXBElement<?>)
                {
                    // Get the command type as a meta command type to shorten subsequent calls
                    Object metaCmdType = ((JAXBElement<?>) cmd).getValue();
                    MetaCommandType metaCmd = (MetaCommandType) metaCmdType;

                    // Create a new row of data to contain this command's information. Each row is
                    // added as a command to the command table
                    String[] cmdRowData = new String[commandTypeDefn.getColumnCountVisible()];
                    Arrays.fill(cmdRowData, null);
                    cmdRowData[commandNameIndex] = metaCmd.getName();

                    // Check if the argument list and type exist
                    if (metaCmd.getArgumentList() != null
                        && metaCmd.getArgumentList().getArgument().size() != 0)
                    {
                        // There should be a single argument list entry that points to the argument
                        ArgumentType argument = metaCmd.getArgumentList().getArgument().get(0);

                        if (argument.getInitialValue() != null)
                        {
                            // Store the command function code
                            cmdRowData[cmdFuncCodeIndex] = argument.getInitialValue();
                        }

                        // Check if the argument list and type exist
                        if (cmdMetaData.getArgumentTypeSet() != null)
                        {
                            // Step through each argument in the list
                            for (NameDescriptionType nameDesc : cmdMetaData.getArgumentTypeSet().getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType())
                            {
                                // Check if the argument is for a structure and the argument type
                                // reference matches the argument name
                                if (nameDesc instanceof AggregateArgumentType
                                    && nameDesc.getName().equals(argument.getArgumentTypeRef()))
                                {
                                    // Store the command argument structure reference
                                    cmdRowData[cmdArgumentIndex] = convertSchemaNameToCcddName(nameDesc.getName());
                                }
                            }
                        }
                    }

                    // Check if the command description is present and the description column
                    // exists in the table type definition
                    if (metaCmd.getLongDescription() != null && cmdDescriptionIndex != -1)
                    {
                        // Store the command description in the row's description column
                        cmdRowData[cmdDescriptionIndex] = metaCmd.getLongDescription();
                    }

                    // Add the new row to the command table definition. Only the columns that apply
                    // to the command table type are added; the excess columns in the row data
                    // array for those commands translated to a structure (i.e., command header
                    // tables) are removed
                    cmdTableDefn.addData(Arrays.copyOf(cmdRowData, commandTypeDefn.getColumnCountVisible()));
                }
            }
        }

        // Add the command table definition to the list
        tableDefinitions.add(cmdTableDefn);
    }

    /**********************************************************************************************
     * Add a variable definition's column values to a structure table
     *
     * @param tableDefn Table definition reference
     *
     * @param rowIndex  Index of the row in which to insert the data
     *
     * @param parmInfo  Parameter information (variable name, data type, etc.)
     *
     * @return Updated row index
     *********************************************************************************************/
    private int addVariableDefinitionToStructure(TableDefinition tableDefn,
                                                 int rowIndex,
                                                 ParameterInformation parmInfo)
    {
        int numArrayMembers = parmInfo.getNumArrayMembers();
        String variableName = parmInfo.getParameterName();
        String arraySize = parmInfo.getArraySize();

        // Check if at least one of the variable definition's column values is non-null
        if (variableName != null
            || parmInfo.getDataType() != null
            || arraySize != null
            || parmInfo.getBitLength() != null
            || parmInfo.getDescription() != null
            || parmInfo.getUnits() != null
            || parmInfo.getEnumeration() != null
            || parmInfo.getMinimum() != null
            || parmInfo.getMaximum() != null
            || parmInfo.getRates() != null)
        {
            String arrayDefnName = null;
            int[] currentIndices = null;
            int[] totalDims = null;
            int numStructureColumns = 0;
            int variableNameIndex = 0;
            int dataTypeIndex = 0;
            int arraySizeIndex = 0;
            int bitLengthIndex = 0;
            int enumerationIndex = 0;
            int minimumIndex = 0;
            int maximumIndex = 0;
            int descriptionIndex = 0;
            int unitsIndex = 0;
            Integer[] rateIndices = null;

            // Check if this is a structure
            if (tableDefn.getTypeName().equals(TYPE_STRUCTURE))
            {
                variableNameIndex = structVariableNameIndex;
                dataTypeIndex = structDataTypeIndex;
                arraySizeIndex = structArraySizeIndex;
                bitLengthIndex = structBitLengthIndex;
                enumerationIndex = structEnumerationIndex;
                minimumIndex = structMinimumIndex;
                maximumIndex = structMaximumIndex;
                descriptionIndex = structDescriptionIndex;
                unitsIndex = structUnitsIndex;
                rateIndices = structRateIndices;
                numStructureColumns = structNumColumns;
            }
            // This is a command argument structure
            else
            {
                variableNameIndex = cmdArgVariableNameIndex;
                dataTypeIndex = cmdArgDataTypeIndex;
                arraySizeIndex = cmdArgArraySizeIndex;
                bitLengthIndex = cmdArgBitLengthIndex;
                enumerationIndex = cmdArgEnumerationIndex;
                minimumIndex = cmdArgMinimumIndex;
                maximumIndex = cmdArgMaximumIndex;
                descriptionIndex = cmdArgDescriptionIndex;
                unitsIndex = cmdArgUnitsIndex;
                rateIndices = cmdArgRateIndices;
                numStructureColumns = cmdArgNumColumns;
            }

            // Check if this is an array member
            if (ArrayVariable.isArrayMember(variableName))
            {
                // Calculate the offset to the array member data in the table definition. The array
                // members are automatically created when the array definition is added, which
                // occurs immediately prior to any of the array's members. The offset adjusts the
                // insertion point back to the start of the member's row of data
                int offset = numStructureColumns
                             * (rowIndex
                                - numArrayMembers
                                + ArrayVariable.getLinearArrayIndex(ArrayVariable.getArrayIndexFromSize(ArrayVariable.getVariableArrayIndex(variableName)),
                                                                    ArrayVariable.getArrayIndexFromSize(arraySize)));
                // Store the array member's definition's column values if the column exists in the
                // structure table type definition (all of these columns exist when the table type
                // is created during import, but certain ones may not exist when importing into an
                // existing structure)
                addVariableData(tableDefn,
                                parmInfo,
                                variableName,
                                offset,
                                variableNameIndex,
                                dataTypeIndex,
                                arraySizeIndex,
                                bitLengthIndex,
                                enumerationIndex,
                                minimumIndex,
                                maximumIndex,
                                descriptionIndex,
                                unitsIndex,
                                rateIndices);
            }
            // Not an array member
            else
            {
                // Create a new row of data in the table definition to contain this parameter's
                // information. Columns values are null if no value is specified (the table paste
                // method uses this to distinguish between a skipped cell and a pasted blank)
                String[] newRow = new String[numStructureColumns];
                Arrays.fill(newRow, null);
                tableDefn.addData(newRow);

                // Step through each parameter to add. A single pass is made for non-array
                // parameters. For array parameters a pass is made for the array definition plus
                // for each array member
                for (int varIndex = 0; varIndex <= numArrayMembers; varIndex++)
                {
                    // Check if this is an array parameter
                    if (numArrayMembers != 0)
                    {
                        // Check if this is the array definition
                        if (varIndex == 0)
                        {
                            totalDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                            currentIndices = new int[totalDims.length];
                            arrayDefnName = variableName;
                        }
                        // This is an array member
                        else
                        {
                            // Add a new row for the array member
                            tableDefn.addData(newRow);

                            // Set the array member's variable name by appending the current array
                            // index
                            variableName = arrayDefnName + ArrayVariable.formatArrayIndex(currentIndices);

                            // Check if this wasn't the last array member (no need to calculate the
                            // index for a member after the last one)
                            if (varIndex != numArrayMembers)
                            {
                                // Step through the array indices so that the next array index can
                                // be created
                                for (int subIndex = currentIndices.length - 1; subIndex >= 0; subIndex--)
                                {
                                    // Increment the index
                                    currentIndices[subIndex]++;

                                    // Check if the maximum index of this dimension is reached
                                    if (currentIndices[subIndex] == totalDims[subIndex])
                                    {
                                        // Reset the index for this dimension
                                        currentIndices[subIndex] = 0;
                                    }
                                    // The maximum index for this dimension hasn't been reached
                                    else
                                    {
                                        // Exit the loop; the array index is set for the next
                                        // member
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // Calculate the offset of the variable's row of data in the table definition
                    int offset = rowIndex * numStructureColumns;

                    // Store the variable definition's column values if the column exists in the
                    // structure table type definition (all of these columns exist when the table
                    // type is created during import, but certain ones may not exist when importing
                    // into an existing structure)
                    addVariableData(tableDefn,
                                    parmInfo,
                                    variableName,
                                    offset,
                                    variableNameIndex,
                                    dataTypeIndex,
                                    arraySizeIndex,
                                    bitLengthIndex,
                                    enumerationIndex,
                                    minimumIndex,
                                    maximumIndex,
                                    descriptionIndex,
                                    unitsIndex,
                                    rateIndices);

                    rowIndex++;
                }
            }
        }

        return rowIndex;
    }

    /**********************************************************************************************
     * Add a variable definition's column values to the specified row in the table definition data
     *
     * @param tableDefn         Table definition reference
     *
     * @param parmInfo          Parameter information (variable name, data type, etc.)
     *
     * @param variableName      Variable name
     *
     * @param offset            Offset of the variable's row of data in the table definition
     *
     * @param variableNameIndex Variable name column index
     *
     * @param dataTypeIndex     Data type column index
     *
     * @param arraySizeIndex    Array size column index
     *
     * @param bitLengthIndex    Bit length column index
     *
     * @param enumerationIndex  Enumeration column index
     *
     * @param minimumIndex      Minimum column index
     *
     * @param maximumIndex      Maximum column index
     *
     * @param descriptionIndex  Description column index
     *
     * @param unitsIndex        Units column index
     *
     * @param rateIndices       Rate column indices
     *********************************************************************************************/
    private void addVariableData(TableDefinition tableDefn,
                                 ParameterInformation parmInfo,
                                 String variableName,
                                 int offset,
                                 int variableNameIndex,
                                 int dataTypeIndex,
                                 int arraySizeIndex,
                                 int bitLengthIndex,
                                 int enumerationIndex,
                                 int minimumIndex,
                                 int maximumIndex,
                                 int descriptionIndex,
                                 int unitsIndex,
                                 Integer[] rateIndices)
    {
        tableDefn.getData().set(offset + variableNameIndex, variableName);
        tableDefn.getData().set(offset + dataTypeIndex, parmInfo.getDataType());
        tableDefn.getData().set(offset + arraySizeIndex, parmInfo.getArraySize());
        tableDefn.getData().set(offset + bitLengthIndex, parmInfo.getBitLength());

        if (enumerationIndex != -1)
        {
            tableDefn.getData().set(offset + enumerationIndex, parmInfo.getEnumeration());
        }

        if (descriptionIndex != -1)
        {
            tableDefn.getData().set(offset + descriptionIndex, parmInfo.getDescription());
        }

        if (unitsIndex != -1)
        {
            tableDefn.getData().set(offset + unitsIndex, parmInfo.getUnits());
        }

        if (minimumIndex != -1)
        {
            tableDefn.getData().set(offset + minimumIndex, parmInfo.getMinimum());
        }

        if (maximumIndex != -1)
        {
            tableDefn.getData().set(offset + maximumIndex, parmInfo.getMaximum());
        }

        for (int index = 0; index < parmInfo.getRates().length; ++index)
        {
            tableDefn.getData().set(offset + rateIndices[index], parmInfo.getRates()[index]);
        }
    }

    /**********************************************************************************************
     * Process the contents of telemetry parameter to extract the parameter attributes
     *
     * @param parameter   Reference to the parameter in the parameter set
     *
     * @param parmType    Reference to the parameter type
     *
     * @param parmTypeSet Reference to the parameter type set list
     *
     * @param seqContSet  Reference to the sequence container list
     *
     * @param typeDefn    Table type definition
     *
     * @param rateIndices Rate column name array
     *
     * @return ParameterInformation for the parameter; null if the reference isn't valid
     *
     * @throws CCDDException If the data type name matches an existing one, but the definition
     *                       differs
     *********************************************************************************************/
    private ParameterInformation processParameterReference(ParameterType parameter,
                                                           NameDescriptionType parmType,
                                                           List<NameDescriptionType> parmTypeSet,
                                                           List<SequenceContainerType> seqContSet,
                                                           TypeDefinition typeDefn,
                                                           Integer[] rateIndices) throws CCDDException
    {
        ParameterInformation parameterInfo = null;
        String matchParmType = null;

        // Initialize the array information, assuming the parameter isn't an array
        int numArrayMembers = 0;

        // Initialize the parameter attributes
        String variableName = convertSchemaNameToCcddName(parameter.getName());
        String dataType = null;
        String arraySize = null;
        String bitLength = null;
        String enumeration = null;
        String minimum = null;
        String maximum = null;
        String description = parameter.getLongDescription();
        String units = null;
        String[] rates = new String[rateIndices.length];

        // Check if this is a non-array parameter
        if (!(parmType instanceof ArrayParameterType))
        {
            // Store the parameter type name for this parameter
            matchParmType = parameter.getParameterTypeRef();
        }
        // This is an array parameter
        else
        {
            arraySize = "";
            ArrayParameterType arrayParmType = (ArrayParameterType) parmType;

            // Step through each dimension for the array variable
            for (DimensionType dim : arrayParmType.getDimensionList().getDimension())
            {
                // Check if the fixed value exists
                if (dim.getEndingIndex().getFixedValue() != null)
                {
                    // Build the array size string
                    arraySize += String.valueOf(dim.getEndingIndex().getFixedValue() + 1) + ",";
                }
            }

            arraySize = CcddUtilities.removeTrailer(arraySize, ",");

            // Store the total number of array members. This causes a row of data to be added for
            // each member of the array
            numArrayMembers = ArrayVariable.getNumMembersFromArraySize(arraySize);

            // The array parameter type entry references a non-array parameter type that describes
            // the individual array members' data type. Step through each parameter type in the
            // parameter type set in order to locate this data type entry
            for (NameDescriptionType type : parmTypeSet)
            {
                // Check if the array parameter's array type reference matches the parameter type
                // set entry name
                if (arrayParmType.getArrayTypeRef().equals(type.getName()))
                {
                    // Store the name of the array parameter's type and stop searching
                    matchParmType = arrayParmType.getArrayTypeRef();
                    break;
                }
            }
        }

        // Check if a parameter type set entry name for the parameter is set (note that if the
        // parameter is an array the steps above locate the data type entry for the individual
        // array members)
        if (matchParmType != null)
        {
            // Step through each entry in the parameter type set
            for (NameDescriptionType type : parmTypeSet)
            {
                // Check if the parameter's type set entry's name matches the parameter type name
                // being searched
                if (matchParmType.equals(type.getName()))
                {
                    String baseDataType = null;
                    long dataTypeBitSize = 0;
                    long parmBitSize = 0;
                    UnitSetType unitSet = null;

                    // Get the data type name from the ancillary data
                    dataType = getAncillaryDataValue(type.getAncillaryDataSet(), DATA_TYPE_NAME);

                    // Get the minimum and maximum
                    minimum = getAncillaryDataValue(type.getAncillaryDataSet(), RANGE_MINIMUM);
                    maximum = getAncillaryDataValue(type.getAncillaryDataSet(), RANGE_MAXIMUM);

                    // Check if the parameter is an integer data type
                    if (type instanceof IntegerParameterType)
                    {
                        // The 'sizeInBits' references are the integer size for non-bit-wise
                        // parameters, but equal the number of bits assigned to the parameter for a
                        // bit-wise parameter. It doens't appear that the size of the integer used
                        // to contain the parameter is stored. The assumption is made that the
                        // smallest integer required to store the bits is used. However, this can
                        // alter the originally intended bit-packing (e.g., a 3-bit and a 9-bit fit
                        // within a single 16-bit integer, but the code below assigns the first to
                        // an 8-bit integer and the second to a 16-bit integer)

                        IntegerParameterType itlm = (IntegerParameterType) type;

                        // Get the number of bits occupied by the parameter
                        parmBitSize = itlm.getSizeInBits();

                        // Get the parameter units reference
                        unitSet = itlm.getUnitSet();

                        // Check if integer encoding is set to 'unsigned'
                        if (itlm.getIntegerDataEncoding().getEncoding().equals(IntegerEncodingType.UNSIGNED))
                        {
                            baseDataType = BaseDataTypeInfo.UNSIGNED_INT.getName();
                        }
                        else
                        {
                            baseDataType = BaseDataTypeInfo.SIGNED_INT.getName();
                        }

                        // Get the data type's size in bits
                        dataTypeBitSize = itlm.getIntegerDataEncoding().getSizeInBits();
                    }
                    // Check if the parameter is a floating point data type
                    else if (type instanceof FloatParameterType)
                    {
                        // Get the float parameter attributes
                        FloatParameterType ftlm = (FloatParameterType) type;
                        baseDataType = BaseDataTypeInfo.FLOATING_POINT.getName();
                        dataTypeBitSize = ftlm.getSizeInBits();
                        parmBitSize = dataTypeBitSize;
                        unitSet = ftlm.getUnitSet();
                    }
                    // Check if the parameter is a string data type
                    else if (type instanceof StringParameterType)
                    {
                        // Get the string parameter attributes
                        StringParameterType stlm = (StringParameterType) type;
                        baseDataType = BaseDataTypeInfo.CHARACTER.getName();
                        dataTypeBitSize = stlm.getStringDataEncoding().getSizeInBits().getFixed().getFixedValue();
                        parmBitSize = dataTypeBitSize;
                        unitSet = stlm.getUnitSet();
                    }
                    // Check if the parameter is an enumerated data type
                    else if (type instanceof EnumeratedParameterType)
                    {
                        // Get the enumeration parameters
                        EnumeratedParameterType etlm = (EnumeratedParameterType) type;
                        EnumerationListType enumList = etlm.getEnumerationList();

                        // Check if any enumeration parameters are defined
                        if (enumList != null)
                        {
                            // Step through each enumeration parameter
                            for (ValueEnumerationType enumType : enumList.getEnumeration())
                            {
                                // Check if this is the first parameter
                                if (enumeration == null)
                                {
                                    // Initialize the enumeration string
                                    enumeration = "";
                                }
                                // Not the first parameter
                                else
                                {
                                    // Add the separator for the enumerations
                                    enumeration += ",";
                                }

                                // Begin building this enumeration
                                enumeration += enumType.getValue() + " | " + enumType.getLabel();
                            }

                            parmBitSize = etlm.getIntegerDataEncoding().getSizeInBits();
                            unitSet = etlm.getUnitSet();

                            // Check if integer encoding is set to 'unsigned'
                            if (etlm.getIntegerDataEncoding().getEncoding().equals(IntegerEncodingType.UNSIGNED))
                            {
                                baseDataType = BaseDataTypeInfo.UNSIGNED_INT.getName();
                            }
                            else
                            {
                                baseDataType = BaseDataTypeInfo.SIGNED_INT.getName();
                            }

                            // Unlike the IntegerParameterType, the EnumeratedParameterType
                            // only has storage for the size in bits in the IntegerDataEncoding.
                            // Therefore the bit length of the parameter is stored in the
                            // IntegerDataEncoding, but the data type length is not stored.
                            // Instead, it is either taken from the the size of the data type,
                            // if the data type exists, or else is calculated as the smallest
                            // integer that will contain it
                            if (dataTypeHandler.getDataTypeByName(dataType) != null)
                            {
                                // Get the data type's size in bits
                                dataTypeBitSize = dataTypeHandler.getSizeInBits(dataType);
                            }
                            else
                            {
                                // Determine the smallest integer size that contains the number of
                                // bits occupied by the parameter
                                dataTypeBitSize = 8;

                                while (parmBitSize > dataTypeBitSize)
                                {
                                    dataTypeBitSize *= 2;
                                }
                            }
                        }
                    }

                    // Check if this is a primitive data type that doesn't exist
                    if (dataType != null
                        && baseDataType != null
                        && (dataTypeHandler.getDataTypeByName(dataType) == null
                            || dataTypeHandler.getSizeInBits(dataType) != dataTypeBitSize))
                    {
                        // Add the new data type
                        List<String[]> newDataType = new ArrayList<String[]>(1);
                        newDataType.add(new String[] {dataType,
                                                      (baseDataType.equals(BaseDataTypeInfo.CHARACTER.getName()) ? DefaultPrimitiveTypeInfo.CHAR.getCType()
                                                                                                                 : ""),
                                                      Long.toString(dataTypeBitSize / 8),
                                                      baseDataType,
                                                      ""});
                        dataTypeHandler.updateDataTypes(newDataType, false);
                    }

                    // Check if the parameter bit size exists
                    if (parmBitSize != dataTypeBitSize)
                    {
                        // Store the bit length
                        bitLength = String.valueOf(parmBitSize);
                    }

                    // Check if the units exists
                    if (unitSet != null)
                    {
                        // Store the units. An empty Unit indicates that the units value is an
                        // instance value overriding the prototype's value with a blank
                        units = unitSet.getUnit().isEmpty() ? ""
                                                            : unitSet.getUnit().get(0).getContent();
                    }

                    // Check if the sequence container list exists
                    if (seqContSet != null)
                    {
                        // Step through each container in the list; each represents a variable with
                        // its rate column name and telemetry rate value
                        for (SequenceContainerType seqCont : seqContSet)
                        {
                            // Split the sequence container name into the variable name and rate
                            // column name
                            String parts[] = seqCont.getName().split("\\" + NAME_RATE_SEPARATOR);

                            // Check if the variable name matches
                            if (variableName.equals(convertSchemaNameToCcddName(parts[0])))
                            {
                                // Step through each rate column
                                for (int rateIndex = 0; rateIndex < rateIndices.length; ++rateIndex)
                                {
                                    // Check if the rate column name matches
                                    if (typeDefn.getColumnNamesVisible()[rateIndices[rateIndex]].equals(parts[1]))
                                    {
                                        // Check if a rate value is present
                                        if (seqCont.getDefaultRateInStream() != null
                                            && seqCont.getDefaultRateInStream().getMinimumValue() != null)
                                        {
                                            rates[rateIndex] = Integer.toString(new Double(seqCont.getDefaultRateInStream().getMinimumValue()).intValue());
                                        }
                                        // No rate value is supplied
                                        else
                                        {
                                            // Set the value to blank to indicate that the value is
                                            // overridden and blank
                                            rates[rateIndex] = "";
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Store the parameter information
                    parameterInfo = new ParameterInformation(variableName,
                                                             dataType,
                                                             arraySize,
                                                             bitLength,
                                                             enumeration,
                                                             units,
                                                             minimum,
                                                             maximum,
                                                             description,
                                                             rates,
                                                             numArrayMembers);

                    // Stop searching since a matching parameter type entry was found
                    break;
                }
            }
        }

        return parameterInfo;
    }

    /**********************************************************************************************
     * Export the specified tables in XTCE XML format to the specified file
     *
     * @param exportFile              Reference to the user-specified output file
     *
     * @param tableDefs               List of table definitions to convert
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param replaceMacros           * Not used for XTCE export (all macros are expanded) * true
     *                                to replace any embedded macros with their corresponding
     *                                values
     *
     * @param includeVariablePaths    * Not used for XTCE export * true to include the variable
     *                                path for each variable in a structure table, both in
     *                                application format and using the user-defined separator
     *                                characters
     *
     * @param variableHandler         * Not used for XTCE export * variable handler class
     *                                reference; null if includeVariablePaths is false
     *
     * @param separators              * Not used for XTCE export * string array containing the
     *                                variable path separator character(s), show/hide data types
     *                                flag ('true' or 'false'), and data type/variable name
     *                                separator character(s); null if includeVariablePaths is false
     *
     * @param extraInfo               [0] endianess (EndianType.BIG_ENDIAN or
     *                                EndianType.LITTLE_ENDIAN) <br>
     *                                [1] are the telemetry and command headers big endian (true or
     *                                false) <br>
     *                                [2] version attribute <br>
     *                                [3] validation status attribute <br>
     *                                [4] classification attribute
     *
     * @throws JAXBException If an error occurs marshaling the project
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportTables(FileEnvVar exportFile,
                             List<TableInfo> tableDefs,
                             boolean includeBuildInformation,
                             boolean replaceMacros,
                             boolean includeVariablePaths,
                             CcddVariableHandler variableHandler,
                             String[] separators,
                             String outputType,
                             Object... extraInfo) throws JAXBException,
                                                         MarshalException,
                                                         CCDDException,
                                                         Exception
    {
        // Convert the table data into XTCE XML format
        convertTablesToXTCE(tableDefs,
                            includeBuildInformation,
                            (EndianType) extraInfo[0],
                            (boolean) extraInfo[1],
                            (String) extraInfo[2],
                            (String) extraInfo[3],
                            (String) extraInfo[4]);

        // Output the XTCE XML file
        marshallXMLfile(project, marshaller, exportFile);
    }

    /**********************************************************************************************
     * Convert the project database contents to XTCE XML format
     *
     * @param tableDefs               List of table definitions to convert
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param endianess               EndianType.BIG_ENDIAN for big endian,
     *                                EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian       True if the telemetry and command headers are always big
     *                                endian (e.g., as with CCSDS)
     *
     * @param version                 Version attribute
     *
     * @param validationStatus        Validation status attribute
     *
     * @param classification          Classification attribute
     *
     * @throws CCDDException An error occurs converting the table(s) to XTCE
     *********************************************************************************************/
    private void convertTablesToXTCE(List<TableInfo> tableDefs,
                                     boolean includeBuildInformation,
                                     EndianType endianess,
                                     boolean isHeaderBigEndian,
                                     String version,
                                     String validationStatus,
                                     String classification) throws CCDDException
    {
        this.endianess = endianess;
        this.isHeaderBigEndian = isHeaderBigEndian;

        // Create the root space system
        rootSystem = addSpaceSystem(null,
                                    cleanSystemPath(dbControl.getProjectName()),
                                    dbControl.getDatabaseDescription(dbControl.getDatabaseName()),
                                    dbControl.getProjectName());

        // Set the root system's header attributes
        HeaderType header = factory.createHeaderType();
        header.setClassification(classification);
        header.setValidationStatus(ValidationStatusType.fromValue(validationStatus));
        header.setVersion(version);
        header.setDate(new Date().toString());
        rootSystem.setHeader(header);

        // Check if the build information is to be output
        if (includeBuildInformation)
        {
            // Set the project's build information
            AuthorSetType author = factory.createAuthorSetType();
            author.getAuthor().add(dbControl.getUser());
            rootSystem.getHeader().setAuthorSet(author);
            NoteSetType note = factory.createNoteSetType();
            note.getNote().add("Created: " + new Date().toString());
            note.getNote().add("CCDD Version: " + ccddMain.getCCDDVersionInformation());
            note.getNote().add("Date: " + new Date().toString());
            note.getNote().add("Project: " + dbControl.getProjectName());
            note.getNote().add("Host: " + dbControl.getServer());
            note.getNote().add("Endianess: " + (endianess == EndianType.BIG_ENDIAN ? "big" : "little"));
            rootSystem.getHeader().setNoteSet(note);
        }

        // Get the names of the tables representing the CCSDS telemetry and command headers
        tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    DefaultInputType.XML_TLM_HDR);
        cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    DefaultInputType.XML_CMD_HDR);

        // Get the telemetry and command header argument column names for the application ID and
        // the command function code. These are stored as project-level data fields
        applicationIDName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                       DefaultInputType.XML_APP_ID);
        cmdFuncCodeName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                     DefaultInputType.XML_FUNC_CODE);

        // Check if the application ID argument column name isn't set in the project
        if (applicationIDName == null)
        {
            // Use the default application ID argument column name
            applicationIDName = DefaultHeaderVariableName.APP_ID.getDefaultVariableName();
        }

        // Check if the command function code argument column name isn't set in the project
        if (cmdFuncCodeName == null)
        {
            // Use the default command function code argument column name
            cmdFuncCodeName = DefaultHeaderVariableName.FUNC_CODE.getDefaultVariableName();
        }

        // The telemetry and command header table names, and application ID and command function
        // code variable names are stored as ancillary data which is used if the export file is
        // imported into CCDD
        AncillaryDataSetType ancillarySet = factory.createAncillaryDataSetType();

        // Check if the telemetry header table name is defined
        if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
        {
            // Store the telemetry header table name
            createAncillaryData(ancillarySet, DefaultInputType.XML_TLM_HDR.getInputName(), tlmHeaderTable);
        }

        // Check if the command header table name is defined
        if (cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Store the command header table name
            createAncillaryData(ancillarySet, DefaultInputType.XML_CMD_HDR.getInputName(), cmdHeaderTable);
        }

        // Store the application ID variable name
        createAncillaryData(ancillarySet, DefaultInputType.XML_APP_ID.getInputName(), applicationIDName);

        // Store the command function code variable name
        createAncillaryData(ancillarySet, DefaultInputType.XML_FUNC_CODE.getInputName(), cmdFuncCodeName);

        project.getValue().setAncillaryDataSet(ancillarySet);

        // Add the project's space systems, parameters, and commands
        buildStructureAndCommandTableXML(tableDefs);
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
    @Override
    protected void buildTableAsXML(TableInfo tableInfo,
                                   TypeDefinition typeDefn,
                                   String tablePath,
                                   String tableName,
                                   String messageFieldName,
                                   String messageNameAndID) throws CCDDException
    {
        // Initialize the parent system to be the root (top-level) system
        SpaceSystemType parentSystem = project.getValue();

        // In order to place a child table within its parent tables' SpaceSystem, the parent must
        // be located. If the parent doesn't exist then it will be created (it may be populated
        // later). This is repeated for every parent in the table's path to create the SpaceSystem
        // hierarchy for the table

        // Get the index of the last instance table referenced in the table's path
        int index = tableInfo.getTablePath().lastIndexOf(",");

        // Check if the table is an instance table
        if (index != -1)
        {
            // Break the table path into the root and each instance
            String[] pathParts = tablePath.substring(0, index).split(",");

            // Set the parent path to the root table name
            String parentPath = pathParts[0]; // Remove this child

            // Step through each part of the parent table path, beginning with the root
            for (String pathPart : pathParts)
            {
                // Check if this part of the path is not the root, but one of the instances
                if (pathPart.contains("."))
                {
                    // Append this part to the path
                    parentPath += "," + pathPart;
                }

                // Search the existing space systems for one with this parent's name (if none
                // exists then use the root system's name)
                SpaceSystemType existingSystem = getSpaceSystemByName(convertCcddNameToSchemaName(parentPath),
                                                                      parentSystem);

                // Set the parent system to the existing system if found, else create a new space
                // system using the name from the table's system path data field. It may already
                // exist due to being referenced in the path of a child table. In this case the
                // space system isn't created again, but the descriptions and attributes are
                // updated to those for this table since these aren't supplied if the space system
                // is created due to being in a child's path
                parentSystem = existingSystem == null ? addSpaceSystem(parentSystem,
                                                                       parentPath,
                                                                       null,
                                                                       (typeDefn.isCommandArgumentStructure() ? CMD_ARG_STRUCT
                                                                                                              : typeDefn.isStructure() ? TYPE_STRUCTURE
                                                                                                                                       : typeDefn.isCommand() ? TYPE_COMMAND
                                                                                                                                                              : typeDefn.getName()))
                                                      : existingSystem;
            }
        }

        // Add the space system for the table
        parentSystem = addSpaceSystem(parentSystem,
                                      tablePath,
                                      tableInfo.getDescription(),
                                      (typeDefn.isCommandArgumentStructure() ? CMD_ARG_STRUCT
                                                                             : typeDefn.isStructure() ? TYPE_STRUCTURE
                                                                                                      : typeDefn.isCommand() ? TYPE_COMMAND
                                                                                                                             : typeDefn.getName()));

        // Check if this is a structure table
        if (typeDefn.isStructure())
        {
            // Expand any macros in the table path (a variable name may contain a macro, which
            // becomes part of the path name)
            tableName = macroHandler.getMacroExpansion(tableName);

            // Add the structure table's variables to the space system's telemetry meta data
            addSpaceSystemParameters(parentSystem,
                                     tableName,
                                     CcddUtilities.convertObjectToString(tableInfo.getDataArray()),
                                     typeDefn,
                                     messageFieldName,
                                     messageNameAndID);
        }
        // This is a command table
        else
        {
            // Add the command(s) from this table to the parent system
            addSpaceSystemCommands(parentSystem,
                                   CcddUtilities.convertObjectToString(tableInfo.getDataArray()),
                                   typeDefn,
                                   messageFieldName,
                                   messageNameAndID);
        }
    }

    /**********************************************************************************************
     * Create a new space system as a child of the specified space system, if it doesn't already
     * exist. If the system already exists then use the supplied description, full path, and
     * document attributes to update the system. If the specified system is null then this is the
     * root space system
     *
     * @param parentSystem Parent space system for the new system; null for the root space system
     *
     * @param tablePath    Table path for the new space system
     *
     * @param description  Space system description
     *
     * @param tableType    The table's type: Structure, Command, or Command Argument Structure
     *
     * @return Reference to the new space system
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    private SpaceSystemType addSpaceSystem(SpaceSystemType parentSystem,
                                           String tablePath,
                                           String description,
                                           String tableType) throws CCDDException
    {
        // Convert the table path to schema format
        String systemName = convertCcddNameToSchemaName(tablePath);

        // Get the reference to the space system if it already exists
        SpaceSystemType childSystem = parentSystem == null ? null
                                                           : getSpaceSystemByName(systemName,
                                                                                  parentSystem);

        // Check if the space system doesn't already exist
        if (childSystem == null)
        {
            // Create the new space system, store its name, and set the flag to indicate a new
            // space system exists
            childSystem = factory.createSpaceSystemType();
            childSystem.setName(systemName);

            // Check if this is a table and not the root system
            if (tableType != null)
            {
                // Store the table type name
                childSystem.setBase(tableType);
            }

            // Check if this is the root space system
            if (parentSystem == null)
            {
                // Set this space system as the root system
                project = factory.createSpaceSystem(childSystem);
            }
            // Not the root space system
            else
            {
                // Add the new space system as a child of the specified system
                parentSystem.getSpaceSystem().add(childSystem);
            }
        }

        // Check if a description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            childSystem.setLongDescription(description);
        }

        return childSystem;
    }

    /**********************************************************************************************
     * Get the reference to the space system with the specified name, starting at the specified
     * space system
     *
     * @param systemName     Name to search for within the space system hierarchy
     *
     * @param startingSystem Space system in which to start the search
     *
     * @return Reference to the space system with the same name as the search name; null if no
     *         space system name matches the search name
     *********************************************************************************************/
    private SpaceSystemType getSpaceSystemByName(String systemName, SpaceSystemType startingSystem)
    {
        // Search the space system hierarchy, beginning at the specified space system
        return searchSpaceSystemsForName(systemName, startingSystem, null);
    }

    /**********************************************************************************************
     * Recursively search through the space system tree for the space system with the same name as
     * the search name
     *
     * @param systemName  Name to search for within the space system hierarchy
     *
     * @param spaceSystem Current space system to check
     *
     * @param foundSystem Space system that matches the search name; null if no match has been
     *                    found
     *
     * @return Reference to the space system with the same name as the search name; null if no
     *         space system name matches the search name
     *********************************************************************************************/
    private SpaceSystemType searchSpaceSystemsForName(String systemName,
                                                      SpaceSystemType spaceSystem,
                                                      SpaceSystemType foundSystem)
    {
        // Check if the space system hasn't been found
        if (foundSystem == null)
        {
            // Check if the current system's name matches the search name
            if (spaceSystem.getName().equals(systemName))
            {
                // Store the reference to the matching system
                foundSystem = spaceSystem;
            }
            // Check if the space system has subsystems
            else if (!spaceSystem.getSpaceSystem().isEmpty())
            {
                // Step through each subsystem
                for (SpaceSystemType sys : spaceSystem.getSpaceSystem())
                {
                    // Search the subsystem (and its subsystems, if any) for a match
                    foundSystem = searchSpaceSystemsForName(systemName, sys, foundSystem);

                    // Check if a system with a matching name was found
                    if (foundSystem != null)
                    {
                        // Stop searching
                        break;
                    }
                }
            }
        }

        return foundSystem;
    }

    /**********************************************************************************************
     * Add a structure table's parameters to the telemetry meta data
     *
     * @param spaceSystem      Space system to which the table belongs
     *
     * @param tableName        Table name
     *
     * @param tableData        Array containing the table's data
     *
     * @param typeDefn         Table type definition
     *
     * @param messageFieldName Message name and ID field name; null if not present or not applicable
     *
     * @param messageNameAndID Message name and ID; null if not present or not applicable
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addSpaceSystemParameters(SpaceSystemType spaceSystem,
                                            String tableName,
                                            String[][] tableData,
                                            TypeDefinition typeDefn,
                                            String messageFieldName,
                                            String messageNameAndID) throws CCDDException
    {
        // Check if the table has any data
        if (tableData.length != 0)
        {
            String[] arrayDefn = new String[tableData[0].length];

            int varColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
            int typeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
            int sizeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
            int bitColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);
            int enumColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.ENUMERATION);
            int descColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);
            int unitsColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS);
            int minColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MINIMUM);
            int maxColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MAXIMUM);

            // Step through each row in the structure table
            for (String[] rowData : tableData)
            {
                // Save the row values if this is an array definition
                if (!rowData[sizeColumn].isEmpty()
                    && !ArrayVariable.isArrayMember(rowData[varColumn]))
                {
                    System.arraycopy(rowData, 0, arrayDefn, 0, rowData.length);
                }

                // Add the variable, if it has a primitive data type, to the parameter set and
                // parameter type set. Variables with structure data types are defined in the
                // container set. Note that a structure variable produces a ContainerRefEntry;
                // there is no place for the structure variable's description so it's discarded
                addParameterAndType(spaceSystem,
                                    typeDefn,
                                    varColumn,
                                    typeColumn,
                                    sizeColumn,
                                    bitColumn,
                                    enumColumn,
                                    descColumn,
                                    unitsColumn,
                                    minColumn,
                                    maxColumn,
                                    rowData,
                                    arrayDefn);

                // Add the parameter's rate information, if extant
                addParameterRates(spaceSystem,
                                  typeDefn,
                                  rowData,
                                  rowData[varColumn]);
            }
        }

        // Check if the telemetry message name and ID are provided
        if (messageNameAndID != null && !messageNameAndID.isEmpty())
        {
            spaceSystem.setAncillaryDataSet(createAncillaryData(spaceSystem.getAncillaryDataSet(),
                                                                MESSAGE_FIELD_KEY,
                                                                messageFieldName));
            spaceSystem.setAncillaryDataSet(createAncillaryData(spaceSystem.getAncillaryDataSet(),
                                                                MESSAGE_NAME_AND_ID_KEY,
                                                                messageNameAndID));
        }
    }

    /**********************************************************************************************
     * Add a parameter with a primitive data type to the parameter set and parameter type set
     *
     * @param spaceSystem Space system reference
     *
     * @param typeDefn    Table type definition
     *
     * @param varColumn   Variable name column index
     *
     * @param typeColumn  Data type column index
     *
     * @param sizeColumn  Array size column index
     *
     * @param bitColumn   Bit length column index
     *
     * @param enumColumn  Enumeration column index
     *
     * @param descColumn  Description column index
     *
     * @param unitsColumn Units column index
     *
     * @param minColumn   Minimum value column index
     *
     * @param maxColumn   Maximum value column index
     *
     * @param rowData     Array of the current table row values
     *
     * @param arrayDefn   Array of the current array definition row values
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addParameterAndType(SpaceSystemType spaceSystem,
                                       TypeDefinition typeDefn,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       int bitColumn,
                                       int enumColumn,
                                       int descColumn,
                                       int unitsColumn,
                                       int minColumn,
                                       int maxColumn,
                                       String[] rowData,
                                       String[] arrayDefn) throws CCDDException
    {
        // Get the current row's values
        String parameterName = rowData[varColumn];
        String dataType = rowData[typeColumn];
        String arraySize = rowData[sizeColumn];
        String bitLength = rowData[bitColumn];
        String enumeration = enumColumn != -1 && (rowData[enumColumn] == null || !rowData[enumColumn].isEmpty()) ? rowData[enumColumn] : "";
        String units = unitsColumn != -1 && (rowData[unitsColumn] == null || !rowData[unitsColumn].isEmpty()) ? rowData[unitsColumn] : "";
        String minimum = minColumn != -1 && (rowData[minColumn] == null || !rowData[minColumn].isEmpty()) ? rowData[minColumn] : "";
        String maximum = maxColumn != -1 && (rowData[maxColumn] == null || !rowData[maxColumn].isEmpty()) ? rowData[maxColumn] : "";
        String description = descColumn != -1 && (rowData[descColumn] == null || !rowData[descColumn].isEmpty()) ? rowData[descColumn] : "";
        int stringSize = dataTypeHandler.isCharacter(rowData[typeColumn]) ? dataTypeHandler.getDataTypeSize(rowData[typeColumn]) : 0;

        // Get the array definition row's values
        String arryDefnEnumeration = enumColumn != -1 && (arrayDefn[enumColumn] == null || !arrayDefn[enumColumn].isEmpty()) ? arrayDefn[enumColumn] : "";
        String arryDefnUnits = unitsColumn != -1 && (arrayDefn[unitsColumn] == null || !arrayDefn[unitsColumn].isEmpty()) ? arrayDefn[unitsColumn] : "";
        String arryDefnMinimum = minColumn != -1 && (arrayDefn[minColumn] == null || !arrayDefn[minColumn].isEmpty()) ? arrayDefn[minColumn] : "";
        String arryDefnMaximum = maxColumn != -1 && (arrayDefn[maxColumn] == null || !arrayDefn[maxColumn].isEmpty()) ? arrayDefn[maxColumn] : "";
        String arryDefnDescription = descColumn != -1 && (arrayDefn[descColumn] == null || !arrayDefn[descColumn].isEmpty()) ? arrayDefn[descColumn] : "";

        // Check if a data type is provided. If this is an array member only store the data if
        // it is not blank
        if (dataType != null
            && (!ArrayVariable.isArrayMember(parameterName)
                || (!description.equals(arryDefnDescription)
                    || !enumeration.equals(arryDefnEnumeration)
                    || !units.equals(arryDefnUnits)
                    || !minimum.equals(arryDefnMinimum)
                    || !maximum.equals(arryDefnMaximum))))
        {
            // Check if this system doesn't yet have its telemetry meta data created
            if (spaceSystem.getTelemetryMetaData() == null)
            {
                // Create the telemetry meta data
                spaceSystem.setTelemetryMetaData(factory.createTelemetryMetaDataType());
            }

            // Get the reference to the parameter set
            ParameterSetType parameterSet = spaceSystem.getTelemetryMetaData().getParameterSet();

            // Check if the parameter set doesn't exist
            if (parameterSet == null)
            {
                // Create the parameter set and its accompanying parameter type set
                parameterSet = factory.createParameterSetType();
                spaceSystem.getTelemetryMetaData().setParameterSet(parameterSet);
                spaceSystem.getTelemetryMetaData().setParameterTypeSet(factory.createParameterTypeSetType());
            }

            // Convert the parameter name to schema format
            String parameterNameConverted = convertCcddNameToSchemaName(parameterName);

            // Create the parameter. This links the parameter name with the parameter reference
            // type
            ParameterType parameterType = factory.createParameterType();
            parameterType.setName(parameterNameConverted);

            // Check if the description is overridden by a blank (= null), or is not empty
            if (description == null || !description.isEmpty())
            {
                parameterType.setLongDescription(description == null ? "" : description);
            }

            // Check if this is an array member and that a value that's stored in the data type
            // hasn't changed
            if (ArrayVariable.isArrayMember(parameterName)
                && (!description.equals(arryDefnDescription)
                    || !enumeration.equals(arryDefnEnumeration)
                    || !units.equals(arryDefnUnits)
                    || !minimum.equals(arryDefnMinimum)
                    || !maximum.equals(arryDefnMaximum)))
            {
                parameterType.setParameterTypeRef(convertCcddNameToSchemaName(ArrayVariable.removeArrayIndex(parameterName)) + ARRAY);
                parameterSet.getParameterOrParameterRef().add(parameterType);
            }
            // Not an array member, or a value that's stored in the data type changed
            else
            {
                parameterType.setParameterTypeRef(parameterNameConverted + (arraySize.isEmpty() ? TYPE : ARRAY));
                parameterSet.getParameterOrParameterRef().add(parameterType);

                // Set the parameter's data type information
                setParameterDataType(spaceSystem,
                                     parameterNameConverted,
                                     dataType,
                                     arraySize,
                                     bitLength,
                                     enumeration,
                                     units,
                                     minimum,
                                     maximum,
                                     stringSize);
            }
        }
    }

    /**********************************************************************************************
     * Create the telemetry parameter data type and set the specified attributes
     *
     * @param spaceSystem   Space system reference
     *
     * @param parameterName Parameter name; null to not specify
     *
     * @param dataType      Data type; null to not specify
     *
     * @param arraySize     Parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength     Parameter bit length; null or empty if not a bit-wise parameter
     *
     * @param enumeration   {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
     *
     * @param units         Parameter units; null to not specify
     *
     * @param minimum       Minimum parameter value; null to not specify
     *
     * @param maximum       Maximum parameter value; null to not specify
     *
     * @param stringSize    Size, in characters, of a string parameter; ignored if not a string or
     *                      character
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void setParameterDataType(SpaceSystemType spaceSystem,
                                        String parameterName,
                                        String dataType,
                                        String arraySize,
                                        String bitLength,
                                        String enumeration,
                                        String units,
                                        String minimum,
                                        String maximum,
                                        int stringSize) throws CCDDException
    {
        NameDescriptionType parameterType = null;

        // Get a reference to the ParameterTypeSet list
        List<NameDescriptionType> parameterTypeList = spaceSystem.getTelemetryMetaData()
                                                                 .getParameterTypeSet()
                                                                 .getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType();

        // Note: Each parameter has an associated size in bits equal to the size of its parent data
        // type. In addition to its parent size, a bit-wise parameter (valid for an integer or
        // enumeration) also has its bit length, the subset of bits it occupies in its parent. The
        // value stored in the parameter encoding type's sizeInBits field is the bit length if a
        // bit-wise parameter, else the parent data type size is used. Ideally both the bit length
        // and overall sizes would be preserved (one in the parameter type's sizeInBits field and
        // the other in the encoding type's sizeInBits field). However, this isn't always possible
        // since the enumerated parameter type lacks the sizeInBits field. To prevent possible
        // confusion of the values, for an integer parameter the parameter type's sizeInBits field
        // is set to match the encoding type's sizeInBits field

        // Check if the parameter is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes. The name is the one pointed to in the
            // ParameterSet entry. The array type reference points to the type entry that describes
            // the array's data type
            ArrayParameterType arrayType = factory.createArrayParameterType();
            arrayType.setName(parameterName + ARRAY);
            arrayType.setArrayTypeRef(parameterName + TYPE);

            DimensionListType dimList = factory.createDimensionListType();

            // Step through each array dimension and add it to the list
            for (int dim : ArrayVariable.getArrayIndexFromSize(arraySize))
            {
                DimensionType dimType = factory.createDimensionType();
                IntegerValueType startVal = factory.createIntegerValueType();
                startVal.setFixedValue(0L);
                IntegerValueType endVal = factory.createIntegerValueType();
                endVal.setFixedValue(Long.valueOf(dim - 1L));
                dimType.setStartingIndex(startVal);
                dimType.setEndingIndex(endVal);
                dimList.getDimension().add(dimType);
            }

            arrayType.setDimensionList(dimList);

            // Set the parameter's array information
            parameterTypeList.add(arrayType);
        }

        // Check if this parameter has a primitive data type
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Get the base data type corresponding to the primitive data type
            BasePrimitiveDataType baseDataType = getBaseDataType(dataType, dataTypeHandler);

            // Check if the a corresponding base data type exists
            if (baseDataType != null)
            {
                UnitSetType unitSet = null;

                // Check if the units value is overridden by a blank (= null), or is not empty
                if (units == null || !units.isEmpty())
                {
                    // Set the parameter units
                    unitSet = createUnitSet(units == null ? "" : units);
                }

                // Check if enumeration parameters are provided
                if (enumeration != null && !enumeration.isEmpty())
                {
                    // Create an enumeration type and enumeration list
                    EnumeratedParameterType enumType = factory.createEnumeratedParameterType();
                    EnumerationListType enumList = createEnumerationList(spaceSystem, enumeration);

                    // Set the integer encoding (the only encoding available for an enumeration)
                    // and the size in bits
                    IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                    // Check if the parameter has a bit length
                    if (bitLength != null && !bitLength.isEmpty())
                    {
                        // Set the size in bits to the value supplied
                        intEncodingType.setSizeInBits(Long.valueOf(bitLength));
                    }
                    // Not a bit-wise parameter
                    else
                    {
                        // Set the size in bits to the full size of the data type
                        intEncodingType.setSizeInBits(Long.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                    }

                    // Check if the data type is an unsigned integer
                    if (dataTypeHandler.isUnsignedInt(dataType))
                    {
                        // Set the encoding type to indicate an unsigned or signed integer
                        intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                    }
                    // The data type is a signed integer
                    else
                    {
                        // Set the encoding type to indicate a signed integer
                        intEncodingType.setEncoding(IntegerEncodingType.SIGN_MAGNITUDE);
                    }

                    // Set the bit order
                    intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                || (isHeaderBigEndian
                                                    && tlmHeaderTable.equals(TableInfo.getPrototypeName(convertSchemaNameToCcddName(spaceSystem.getName())))) ? BitOrderType.MOST_SIGNIFICANT_BIT_FIRST
                                                                                                                                                              : BitOrderType.LEAST_SIGNIFICANT_BIT_FIRST);

                    enumType.setIntegerDataEncoding(intEncodingType);
                    enumType.setEnumerationList(enumList);

                    if (unitSet != null)
                    {
                        enumType.setUnitSet(unitSet);
                    }

                    AncillaryDataSetType enumAnc = null;

                    // Check if a minimum value is specified
                    if (minimum == null || !minimum.isEmpty())
                    {
                        // Set the minimum value
                        enumAnc = createAncillaryData(enumAnc, RANGE_MINIMUM, minimum == null ? "" : minimum);
                    }

                    // Check if a maximum value is specified
                    if (maximum == null || !maximum.isEmpty())
                    {
                        // Set the maximum value
                        enumAnc = createAncillaryData(enumAnc, RANGE_MAXIMUM, maximum == null ? "" : maximum);
                    }

                    enumType.setAncillaryDataSet(createAncillaryData(enumAnc, DATA_TYPE_NAME, dataType));
                    parameterType = enumType;
                }
                // Not an enumeration
                else
                {
                    switch (baseDataType)
                    {
                        case INTEGER:
                            // Create an integer parameter and set its attributes
                            IntegerParameterType integerType = factory.createIntegerParameterType();
                            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                            long intSizeInBits;
                            long dataTypeSizeInBits = dataTypeHandler.getSizeInBits(dataType);

                            // Check if the parameter has a bit length
                            if (bitLength != null && !bitLength.isEmpty())
                            {
                                // Get the bit length of the argument
                                intSizeInBits = Long.valueOf(bitLength);
                            }
                            // Not a bit-wise parameter
                            else
                            {
                                // Get the bit size of the integer type
                                intSizeInBits = dataTypeSizeInBits;
                            }

                            // Set the encoding type to indicate an unsigned integer
                            integerType.setSizeInBits(intSizeInBits);
                            intEncodingType.setSizeInBits(dataTypeSizeInBits);

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                integerType.setSigned(false);
                                intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                            }
                            // The data type is a signed integer
                            else
                            {
                                // Set the encoding type to indicate a signed integer
                                integerType.setSigned(true);
                                intEncodingType.setEncoding(IntegerEncodingType.SIGN_MAGNITUDE);
                            }

                            // Set the bit order
                            intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                        || (isHeaderBigEndian
                                                            && tlmHeaderTable.equals(TableInfo.getPrototypeName(convertSchemaNameToCcddName(spaceSystem.getName())))) ? BitOrderType.MOST_SIGNIFICANT_BIT_FIRST
                                                                                                                                                                      : BitOrderType.LEAST_SIGNIFICANT_BIT_FIRST);

                            // Set the encoding type and units
                            integerType.setIntegerDataEncoding(intEncodingType);

                            if (unitSet != null)
                            {
                                integerType.setUnitSet(unitSet);
                            }

                            AncillaryDataSetType intAnc = null;

                            // Check if a minimum value is specified
                            if (minimum == null || !minimum.isEmpty())
                            {
                                // Set the minimum value
                                intAnc = createAncillaryData(intAnc,
                                                             RANGE_MINIMUM,
                                                             minimum == null ? ""
                                                                             : minimum);
                            }

                            // Check if a maximum value is specified
                            if (maximum == null || !maximum.isEmpty())
                            {
                                // Set the maximum value
                                intAnc = createAncillaryData(intAnc,
                                                             RANGE_MAXIMUM,
                                                             maximum == null ? ""
                                                                             : maximum);
                            }

                            integerType.setAncillaryDataSet(createAncillaryData(intAnc,
                                                                                DATA_TYPE_NAME,
                                                                                dataType));
                            parameterType = integerType;
                            break;

                        case FLOAT:
                            // Get the bit size of the float type
                            int floatSizeInBits = dataTypeHandler.getSizeInBits(dataType);

                            // Create a float parameter and set its attributes
                            FloatParameterType floatType = factory.createFloatParameterType();
                            floatType.setSizeInBits(Long.valueOf(floatSizeInBits));
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(floatSizeInBits);
                            floatEncodingType.setEncoding(FloatEncodingType.IEEE_754_1985);
                            floatType.setFloatDataEncoding(floatEncodingType);

                            if (unitSet != null)
                            {
                                floatType.setUnitSet(unitSet);
                            }

                            AncillaryDataSetType floatAnc = null;

                            // Check if a minimum value is specified
                            if (minimum == null || !minimum.isEmpty())
                            {
                                // Set the minimum value
                                floatAnc = createAncillaryData(floatAnc,
                                                               RANGE_MINIMUM,
                                                               minimum == null ? ""
                                                                               : minimum);
                            }

                            // Check if a maximum value is specified
                            if (maximum == null || !maximum.isEmpty())
                            {
                                // Set the maximum value
                                floatAnc = createAncillaryData(floatAnc,
                                                               RANGE_MAXIMUM,
                                                               maximum == null ? ""
                                                                               : maximum);
                            }

                            floatType.setAncillaryDataSet(createAncillaryData(floatAnc,
                                                                              DATA_TYPE_NAME,
                                                                              dataType));
                            parameterType = floatType;
                            break;

                        case STRING:
                            // Create a string parameter and set its attributes
                            StringParameterType stringType = factory.createStringParameterType();
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                            // Set the string's size in bits based on the number of characters in
                            // the string with each character occupying a single byte
                            IntegerValueType intValType = factory.createIntegerValueType();
                            intValType.setFixedValue(stringSize * 8L);
                            SizeInBitsType stringSizeInBits = factory.createSizeInBitsType();
                            Fixed fixed = new Fixed();
                            fixed.setFixedValue(intValType.getFixedValue());
                            stringSizeInBits.setFixed(fixed);
                            stringEncodingType.setSizeInBits(stringSizeInBits);
                            stringEncodingType.setEncoding(StringEncodingType.UTF_8);
                            stringType.setAncillaryDataSet(createAncillaryData(null,
                                                                               DATA_TYPE_NAME,
                                                                               dataType));
                            stringType.setStringDataEncoding(stringEncodingType);

                            if (unitSet != null)
                            {
                                stringType.setUnitSet(unitSet);
                            }

                            parameterType = stringType;
                            break;
                    }
                }
            }
        }
        // The parameter has a structure as the data type
        else
        {
            AggregateParameterType structType = factory.createAggregateParameterType();

            // Store the structure name that is the data type for this parameter
            structType.setAncillaryDataSet(createAncillaryData(structType.getAncillaryDataSet(),
                                                               DATA_TYPE_NAME,
                                                               dataType));

            // The member list is unused by CCDD; the list, with name and typeDef, must be present
            // to satisfy schema and cannot be blank
            MemberListType memberListType = factory.createMemberListType();
            MemberType memberType = factory.createMemberType();
            memberType.setName("unused");
            memberType.setTypeRef("unused");
            memberListType.getMember().add(memberType);
            structType.setMemberList(memberListType);

            parameterType = structType;
        }

        // In order to decrease the size and complexity of the XML data the parameter types are
        // shared, if possible. The parameter type is compared to the existing ones in the
        // ParameterTypeSet list. If the type information is identical then a shared parameter type
        // is used. The parameter type/reference name used is the one for the first matched
        // parameter; i.e., all subsequent matches use the first one's parameter type

        NameDescriptionType parmType = null;

        if (parameterType instanceof IntegerParameterType)
        {
            IntegerParameterType iPt = (IntegerParameterType) parameterType;
            String dt = getAncillaryDataValue(iPt.getAncillaryDataSet(), DATA_TYPE_NAME);

            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof IntegerParameterType)
                {
                    IntegerParameterType iPtTgt = (IntegerParameterType) pType;
                    String dtTgt = getAncillaryDataValue(iPtTgt.getAncillaryDataSet(),
                                                         DATA_TYPE_NAME);

                    // Check if the integer type attributes match
                    if ((iPt.isSigned() == iPtTgt.isSigned())
                        && (iPt.getSizeInBits() == iPtTgt.getSizeInBits())
                        && (((iPt.getIntegerDataEncoding() == null)
                             && (iPtTgt.getIntegerDataEncoding() == null))
                            || ((iPt.getIntegerDataEncoding() != null)
                                && (iPtTgt.getIntegerDataEncoding() != null)
                                && (iPt.getIntegerDataEncoding().getSizeInBits()
                                    == iPtTgt.getIntegerDataEncoding().getSizeInBits())
                                && (iPt.getIntegerDataEncoding().getBitOrder()
                                    == iPtTgt.getIntegerDataEncoding().getBitOrder()))
                        && (((dt == null) && (dtTgt == null))
                            || ((dt != null) && (dtTgt != null) && dt.equals(dtTgt)))
                        && ((iPt.getUnitSet() == null && iPtTgt.getUnitSet() == null)
                            || (iPt.getUnitSet() != null
                                && iPtTgt.getUnitSet() != null
                                && iPt.getUnitSet()
                                      .getUnit()
                                      .get(0)
                                      .getContent()
                                      .equals(iPtTgt.getUnitSet()
                                                    .getUnit()
                                                    .get(0)
                                                    .getContent())))))
                    {
                        parmType = pType;
                        break;
                    }
                }
            }
        }
        else if (parameterType instanceof FloatParameterType)
        {
            FloatParameterType fPt = (FloatParameterType) parameterType;
            String dt = getAncillaryDataValue(fPt.getAncillaryDataSet(),
                                              DATA_TYPE_NAME);

            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof FloatParameterType)
                {
                    FloatParameterType fPtTgt = (FloatParameterType) pType;
                    String dtTgt = getAncillaryDataValue(fPtTgt.getAncillaryDataSet(),
                                                         DATA_TYPE_NAME);

                    // Check if the float type attributes match
                    if ((fPt.getSizeInBits() == fPtTgt.getSizeInBits())
                        && (((fPt.getFloatDataEncoding() == null)
                             && (fPtTgt.getFloatDataEncoding() == null))
                            || ((fPt.getFloatDataEncoding() != null)
                                && (fPtTgt.getFloatDataEncoding() != null)
                                && (fPt.getFloatDataEncoding().getSizeInBits()
                                     == fPtTgt.getFloatDataEncoding().getSizeInBits())
                                && (fPt.getFloatDataEncoding().getEncoding().toString()
                                     == fPtTgt.getFloatDataEncoding().getEncoding().toString()))
                        && (((dt == null) && (dtTgt == null))
                            || ((dt != null) && (dtTgt != null) && dt.equals(dtTgt)))
                        && (((fPt.getUnitSet() == null) && (fPtTgt.getUnitSet() == null))
                            || ((fPt.getUnitSet() != null)
                                && (fPtTgt.getUnitSet() != null)
                                && fPt.getUnitSet()
                                      .getUnit()
                                      .get(0)
                                      .getContent()
                                      .equals(fPtTgt.getUnitSet()
                                                    .getUnit()
                                                    .get(0).getContent())))))
                    {
                        parmType = pType;
                        break;
                    }
                }
            }
        }
        else if (parameterType instanceof StringParameterType)
        {
            StringParameterType sPt = (StringParameterType) parameterType;
            String dt = getAncillaryDataValue(sPt.getAncillaryDataSet(),
                                              DATA_TYPE_NAME);

            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof StringParameterType)
                {
                    StringParameterType sPtTgt = (StringParameterType) pType;
                    String dtTgt = getAncillaryDataValue(sPtTgt.getAncillaryDataSet(),
                                                         DATA_TYPE_NAME);

                    // Check if the string type attributes match
                    if ((((sPt.getStringDataEncoding() == null)
                          && (sPtTgt.getStringDataEncoding() == null))
                        || ((sPt.getStringDataEncoding() != null)
                            && (sPtTgt.getStringDataEncoding() != null)
                            && (sPt.getStringDataEncoding().getSizeInBits().getFixed().getFixedValue()
                                == sPtTgt.getStringDataEncoding().getSizeInBits().getFixed().getFixedValue())
                            && (sPt.getStringDataEncoding().getEncoding()
                                == sPtTgt.getStringDataEncoding().getEncoding()))
                        && (((dt == null) && (dtTgt == null))
                            || ((dt != null) && (dtTgt != null) && dt.equals(dtTgt)))
                        && (((sPt.getUnitSet() == null) && (sPtTgt.getUnitSet() == null))
                            || ((sPt.getUnitSet() != null)
                                && (sPtTgt.getUnitSet() != null)
                                && (sPt.getUnitSet()
                                       .getUnit()
                                       .get(0)
                                       .getContent()
                                       .equals(sPtTgt.getUnitSet()
                                                     .getUnit()
                                                     .get(0)
                                                     .getContent()))))))
                    {
                        parmType = pType;
                        break;
                    }
                }
            }
        }
        else if (parameterType instanceof EnumeratedParameterType)
        {
            EnumeratedParameterType ePt = (EnumeratedParameterType) parameterType;
            String dt = getAncillaryDataValue(ePt.getAncillaryDataSet(),
                                              DATA_TYPE_NAME);

            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof EnumeratedParameterType)
                {
                    EnumeratedParameterType ePtTgt = (EnumeratedParameterType) pType;
                    String dtTgt = getAncillaryDataValue(ePtTgt.getAncillaryDataSet(),
                                                         DATA_TYPE_NAME);

                    // Check if the integer type attributes match
                    if ((((ePt.getIntegerDataEncoding() == null)
                          && (ePtTgt.getIntegerDataEncoding() == null))
                        || ((ePt.getIntegerDataEncoding() != null)
                             && (ePtTgt.getIntegerDataEncoding() != null)
                             && (ePt.getIntegerDataEncoding().getSizeInBits()
                                 == ePtTgt.getIntegerDataEncoding().getSizeInBits())
                             && (ePt.getIntegerDataEncoding().getBitOrder()
                                 == ePtTgt.getIntegerDataEncoding().getBitOrder()))
                        && (((dt == null) && (dtTgt == null))
                            || ((dt != null) && (dtTgt != null) && dt.equals(dtTgt)))
                        && ((ePt.getUnitSet() == null && ePtTgt.getUnitSet() == null)
                            || ((ePt.getUnitSet() != null)
                                && (ePtTgt.getUnitSet() != null)
                                && ePt.getUnitSet()
                                      .getUnit()
                                      .get(0)
                                      .getContent()
                                      .equals(ePtTgt.getUnitSet()
                                                    .getUnit()
                                                    .get(0)
                                                    .getContent()))))
                        && (((ePt.getEnumerationList() == null) && (ePtTgt.getEnumerationList() == null))
                            || ((ePt.getEnumerationList() != null)
                                && (ePtTgt.getEnumerationList() != null)
                                && (ePt.getEnumerationList().getEnumeration() != null)
                                && (ePtTgt.getEnumerationList().getEnumeration() != null)
                                && (ePt.getEnumerationList().getEnumeration().size()
                                    == ePtTgt.getEnumerationList().getEnumeration().size()))))
                    {
                        boolean isEnumMatch = true;

                        // Check if each enumeration name/value pair is identical
                        for (int index = 0; isEnumMatch && index < ePt.getEnumerationList().getEnumeration().size(); ++index)
                        {
                            if (!ePt.getEnumerationList()
                                    .getEnumeration()
                                    .get(index)
                                    .equals(ePtTgt.getEnumerationList()
                                                  .getEnumeration()
                                                  .get(index)))
                            {
                                isEnumMatch = false;
                            }
                        }

                        if (isEnumMatch)
                        {
                            parmType = pType;
                        }

                        break;
                    }
                }
            }
        }
        else if (parameterType instanceof AggregateParameterType)
        {
            AggregateParameterType aPt = (AggregateParameterType) parameterType;
            String dt = getAncillaryDataValue(aPt.getAncillaryDataSet(),
                                              DATA_TYPE_NAME);

            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof AggregateParameterType)
                {
                    AggregateParameterType aPtTgt = (AggregateParameterType) pType;
                    String dtTgt = getAncillaryDataValue(aPtTgt.getAncillaryDataSet(),
                                                         DATA_TYPE_NAME);

                   // Check if the aggregate type attributes match
                   if (((dt == null) && (dtTgt == null))
                        || ((dt != null) && (dtTgt != null) && dt.equals(dtTgt)))
                    {
                        parmType = pType;
                        break;
                    }
                }
            }
        }

        // Check if a matching parameter type was found
        if (parmType != null)
        {
            // Adjust any ArrayParameterTypes that point to this shared ParameterReference
            for (NameDescriptionType pType : parameterTypeList)
            {
                if (pType instanceof ArrayParameterType
                    && ((ArrayParameterType) pType).getArrayTypeRef().equals(parameterName + TYPE))
                {
                    ((ArrayParameterType) pType).setArrayTypeRef(parmType.getName());
                }
            }

            // Step through the existing parameters in the ParameterSet
            for (Object parmObj: spaceSystem.getTelemetryMetaData().getParameterSet().getParameterOrParameterRef())
            {
                ParameterType parameter = (ParameterType) parmObj;

                // Check if the parameter's type reference matches the new parameter or an
                // existing one
                if (parameter.getParameterTypeRef().equals(parameterName + TYPE)
                    || parameter.getParameterTypeRef().equals(parmType.getName()))
                {
                    // Change the parameter's type reference to use the matching parameter's
                    // type
                    parameter.setParameterTypeRef(parmType.getName());
                }
            }
        }
        // This is a unique parameter type
        else
        {
            // Set the parameter type name
            parameterType.setName(parameterName + TYPE);

            // Add the parameter's data type information
            spaceSystem.getTelemetryMetaData()
                       .getParameterTypeSet()
                       .getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType()
                       .add(parameterType);
        }
    }

    /**********************************************************************************************
     * Add the parameter rate(s) as sequence containers
     *
     * @param spaceSystem   Space system reference
     *
     * @param typeDefn      Table type definition
     *
     * @param rowData       Table row data
     *
     * @param parameterName Parameter name
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    private void addParameterRates(SpaceSystemType spaceSystem,
                                   TypeDefinition typeDefn,
                                   String[] rowData,
                                   String parameterName) throws CCDDException
    {
        Integer[] rateColumns = typeDefn.getColumnIndicesByInputTypeFormat(InputTypeFormat.RATE).toArray(new Integer[0]);

        // Step through each rate column
        for (int rateColumn = 0; rateColumn < rateColumns.length; ++rateColumn)
        {
            if (rowData[rateColumns[rateColumn]] == null || !rowData[rateColumns[rateColumn]].isEmpty())
            {
                // Add the rate for this telemetry parameter to the space system's container set
                // (created if needed) as a sequence container, named using the parameter's name
                // and rate column name, with a DefaultRateInStream containing the rate value
                SequenceContainerType seqCont = factory.createSequenceContainerType();
                seqCont.setName(convertCcddNameToSchemaName(parameterName)
                                + NAME_RATE_SEPARATOR
                                + typeDefn.getColumnNamesUser()[rateColumns[rateColumn]]);

                // The entry list is unused by CCDD; the list, with name and typeDef, must be
                // present to satisfy schema and cannot be blank
                seqCont.setEntryList(factory.createEntryListType());

                // Check that the rate value isn't overridden by a blank (as indicated by the
                // null). If it is then no DefaultRateInStream is added
                if (rowData[rateColumns[rateColumn]] != null)
                {
                    RateInStreamType rateIn = factory.createRateInStreamType();
                    rateIn.setMinimumValue(Double.valueOf(rowData[rateColumns[rateColumn]]));
                    rateIn.setBasis(BasisType.PER_SECOND);
                    seqCont.setDefaultRateInStream(rateIn);
                }

                // Add the container set to the space system if this is the first telemetered
                // parameter in this structure
                if (spaceSystem.getTelemetryMetaData().getContainerSet() == null)
                {
                    spaceSystem.getTelemetryMetaData().setContainerSet(factory.createContainerSetType());
                }

                spaceSystem.getTelemetryMetaData().getContainerSet().getSequenceContainer().add(seqCont);
            }
        }
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified space system
     *
     * @param spaceSystem      Space system reference
     *
     * @param tableData        Table data array
     *
     * @param typeDefn         Table type definition
     *
     * @param messageFieldName Message name and ID field name; null if not present or not applicable
     *
     * @param messageNameAndID Message name and ID; null if not present or not applicable
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addSpaceSystemCommands(SpaceSystemType spaceSystem,
                                          String[][] tableData,
                                          TypeDefinition typeDefn,
                                          String messageFieldName,
                                          String messageNameAndID) throws CCDDException
    {
        int cmdNameColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
        int cmdCodeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);
        int cmdArgumentColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_ARGUMENT);
        int cmdDescColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);

        // Check if the command message name and ID are provided
        if (messageNameAndID != null && !messageNameAndID.isEmpty())
        {
            spaceSystem.setAncillaryDataSet(createAncillaryData(spaceSystem.getAncillaryDataSet(),
                                                                MESSAGE_FIELD_KEY,
                                                                messageFieldName));
            spaceSystem.setAncillaryDataSet(createAncillaryData(spaceSystem.getAncillaryDataSet(),
                                                                MESSAGE_NAME_AND_ID_KEY,
                                                                messageNameAndID));
        }

        // Step through each row in the table
        for (String[] cmdRowData : tableData)
        {
            // Check if the command name exists; if the argument name is missing then the
            // entire argument is ignored
            if (cmdNameColumn != -1 && !cmdRowData[cmdNameColumn].isEmpty())
            {
                // Store the command name
                String commandName = cleanSystemPath(cmdRowData[cmdNameColumn]);

                // Initialize the command attributes and argument names list
                String commandFuncCode = null;
                String commandArgStruct = null;
                String commandDescription = null;

                // Check if this system doesn't yet have its command metadata created
                if (spaceSystem.getCommandMetaData() == null)
                {
                    // Create the command metadata
                    spaceSystem.setCommandMetaData(factory.createCommandMetaDataType());
                    spaceSystem.getCommandMetaData().setMetaCommandSet(factory.createMetaCommandSetType());
                }

                // Check if the command code column and value exist
                if (cmdCodeColumn != -1 && !cmdRowData[cmdCodeColumn].isEmpty())
                {
                    // Store the command code
                    commandFuncCode = cmdRowData[cmdCodeColumn];
                }

                // Check if the command argument column and value exist
                if (cmdArgumentColumn != -1 && !cmdRowData[cmdArgumentColumn].isEmpty())
                {
                    // Store the command argument
                    commandArgStruct = cmdRowData[cmdArgumentColumn];
                }

                // Check if the command description exists
                if (cmdDescColumn != -1 && !cmdRowData[cmdDescColumn].isEmpty())
                {
                    // Store the command description
                    commandDescription = cmdRowData[cmdDescColumn];
                }

                // Add the command metadata set information
                addCommand(spaceSystem,
                           commandName,
                           commandFuncCode,
                           commandArgStruct,
                           commandDescription);
            }
        }
    }

    /**********************************************************************************************
     * Add a command to the command metadata set
     *
     * @param spaceSystem     Space system reference
     *
     * @param commandName     Command name
     *
     * @param cmdFuncCode     Command code
     *
     * @param cmdArgStruct    Command argument structure
     *
     * @param cmdDescription  Description of the command
     *
     * @throws CCDDException If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addCommand(SpaceSystemType spaceSystem,
                              String commandName,
                              String cmdFuncCode,
                              String cmdArgStruct,
                              String cmdDescription) throws CCDDException
    {
        MetaCommandSetType commandSet = spaceSystem.getCommandMetaData().getMetaCommandSet();
        MetaCommandType command = factory.createMetaCommandType();

        // Check if a command name exists
        if (commandName != null && !commandName.isEmpty())
        {
            // Set the command name attribute
            command.setName(commandName);
        }

        // Check if a command description exists
        if (cmdDescription != null && !cmdDescription.isEmpty())
        {
            // Set the command description attribute
            command.setLongDescription(cmdDescription);
        }

        if ((cmdFuncCode != null && !cmdFuncCode.isEmpty())
            || (cmdArgStruct != null && !cmdArgStruct.isEmpty()))
        {
            command.setArgumentList(factory.createArgumentListType());
            ArgumentType argument = factory.createArgumentType();

            // The argument name is unused by CCDD; the list, with name and typeDef, must be
            // present to satisfy schema and cannot be blank
            argument.setName("unused");

            // Check if a command code is provided
            if (cmdFuncCode != null && !cmdFuncCode.isEmpty())
            {
                argument.setInitialValue(cmdFuncCode);
            }

            // Check if a command argument is provided
            if (cmdArgStruct != null && !cmdArgStruct.isEmpty())
            {
                // Store the command argument structure reference
                ArgumentTypeSetType argType = spaceSystem.getCommandMetaData().getArgumentTypeSet();

                if (argType == null)
                {
                    argType = factory.createArgumentTypeSetType();
                    spaceSystem.getCommandMetaData().setArgumentTypeSet(argType);
                }

                // Convert the table path to schema format
                cmdArgStruct = convertCcddNameToSchemaName(cmdArgStruct);

                AggregateArgumentType aggArgument = factory.createAggregateArgumentType();
                aggArgument.setName(cmdArgStruct);

                // The member list is unused by CCDD; the list, with name and typeDef, must be
                // present to satisfy schema and cannot be blank
                MemberListType memberListType = factory.createMemberListType();
                MemberType memberType = factory.createMemberType();
                memberType.setName("unused");
                memberType.setTypeRef("unused");
                memberListType.getMember().add(memberType);
                aggArgument.setMemberList(memberListType);

                boolean isFound = false;

                for (NameDescriptionType aggArg : argType.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType())
                {
                    if (aggArg.getName().equals(cmdArgStruct))
                    {
                        isFound = true;
                        break;
                    }
                }

                if (!isFound)
                {
                    argType.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(aggArgument);
                }

                argument.setArgumentTypeRef(cmdArgStruct);
            }

            command.getArgumentList().getArgument().add(argument);
        }

        List<JAXBElement<?>> metaCmdList = commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand();
        JAXBElement<MetaCommandType> metaCmdType = factory.createMetaCommandSetTypeMetaCommand(command);
        metaCmdList.add(metaCmdType);
    }

    /**********************************************************************************************
     * Build a unit set from the supplied units string
     *
     * @param units Parameter or command argument units; null to not specify
     *
     * @return Unit set for the supplied units string; an empty unit set if no units are supplied
     *********************************************************************************************/
    protected UnitSetType createUnitSet(String units)
    {
        UnitSetType unitSet = factory.createUnitSetType();

        // Check if units are provided (an blank is acceptable)
        if (units != null)
        {
            // Set the parameter units
            UnitType unit = factory.createUnitType();
            unit.setContent(units);
            unitSet.getUnit().add(unit);
        }

        return unitSet;
    }

    /**********************************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param spaceSystem Space system reference
     *
     * @param enumeration {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    protected EnumerationListType createEnumerationList(SpaceSystemType spaceSystem, String enumeration)
    {
        EnumerationListType enumList = factory.createEnumerationListType();

        try
        {
            // Get the character that separates the enumeration value from the associated label
            String enumValSep = CcddUtilities.getEnumeratedValueSeparator(enumeration);

            // Check if the enumeration value is missing or the value separator couldn't be located
            if (enumValSep == null)
            {
                throw new CCDDException("initial non-negative integer or separator character "
                                        + "between enumeration value and label missing");
            }

            // Get the character that separates the enumerated pairs
            String enumPairSep = CcddUtilities.getEnumerationGroupSeparator(enumeration, enumValSep);
            String[] enumDefn;

            // Check if the enumerated pair separator couldn't be located, which indicated that
            // only a single enumerated value is defined
            if (enumPairSep == null)
            {
                enumDefn = new String[] {enumeration};
            }
            // Multiple enumerated values are defined
            else
            {
                // Divide the enumeration string into the separate enumeration definitions
                enumDefn = enumeration.split(Pattern.quote(enumPairSep));
            }

            // Step through each enumeration definition
            for (int index = 0; index < enumDefn.length; index++)
            {
                // Split the enumeration definition into the name and label components
                String[] enumParts = enumDefn[index].split(Pattern.quote(enumValSep), 2);

                // Create a new enumeration value type and add the enumerated name and value to the
                // enumeration list
                ValueEnumerationType valueEnum = factory.createValueEnumerationType();
                valueEnum.setLabel(enumParts[1].trim());
                valueEnum.setValue(Long.decode(enumParts[0].trim()));
                enumList.getEnumeration().add(valueEnum);
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the enumeration format is invalid
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Enumeration '</b>"
                                                      + enumeration
                                                      + "<b>' format invalid in table '</b>"
                                                      + convertSchemaNameToCcddName(spaceSystem.getName())
                                                      + "<b>'; "
                                                      + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }

    /**********************************************************************************************
     * Create a data name/value pair and add it to an AncillaryDataSetType set
     *
     * @param adst  The AncillaryDataSetType set in which to place the data; null to create a new
     *              one
     *
     * @param name  Name of the ancillary data object
     *
     * @param value Value to store as ancillary data
     *
     * @return The ancillary data set containing the name/value pair
     *********************************************************************************************/
    protected AncillaryDataSetType createAncillaryData(AncillaryDataSetType adst, String name, String value)
    {
        if (adst == null)
        {
            adst = factory.createAncillaryDataSetType();
        }

        AncillaryDataType adt = factory.createAncillaryDataType();
        adt.setName(name);
        adt.setValue(value);
        adst.getAncillaryData().add(adt);
        return adst;
    }

    /**********************************************************************************************
     * Return the value of an ancillary data item based on the item name
     *
     * @param adst  The AncillaryDataSetType set in which to search for the name
     *
     * @param name  Name of the ancillary data object for which to search
     *
     * @return The value associated with the name; null if the name is not in the ancillary data
     *         set
     *********************************************************************************************/
    protected String getAncillaryDataValue(AncillaryDataSetType adst, String name)
    {
        String value = null;

        // Check if the AncillaryDataSetType isn't null
        if (adst != null)
        {
            // Find the first occurrence of a name/value pair with the specified name in the
            // ancillary data set
            Optional<AncillaryDataType> dataPair = adst.getAncillaryData()
                                                       .stream()
                                                       .filter(p -> p.getName().equals(name))
                                                       .findFirst();

            // Check if a name/value pair with the specified name exists
            if (dataPair.isPresent())
            {
                // Get the value associated with the name
                value = dataPair.get().getValue();
            }
        }

        return value;
    }

    /**********************************************************************************************
     * Convert a CCDD table path or parameter name to a string that is accepted by the XTCE schema
     * constraints. Periods and square brackets are not allowed by the XTCE schema, but are normal
     * parts of a table path and parameter name
     *
     * @param name Table path or parameter name in CCDD format
     *
     * @return Table path or parameter name in a format that is accepted by the schema constraints
      *********************************************************************************************/
    private String convertCcddNameToSchemaName(String name)
    {
        return name.replaceAll("\\.", "-")
                   .replaceAll("\\[", "(")
                   .replaceAll("\\]", ")");
    }

    /**********************************************************************************************
     * Convert a XTCE schema name that represents a table path or parameter name to CCDD format.
     * Periods and square brackets are not allowed by the XTCE schema, but are normal parts of a
     * table path and parameter name
     *
     * @param name Table path or parameter name in schema format
     *
     * @return Table path or parameter name in a format that is accepted by CCDD
      *********************************************************************************************/
    private String convertSchemaNameToCcddName(String name)
    {
        return name.replaceAll("-", ".")
                   .replaceAll("\\(", "[")
                   .replaceAll("\\)", "]");
    }

    /**********************************************************************************************
     * Export table type definitions to the specified folder
     *
     * @param exportFile        Reference to the user-specified output file
     *
     * @param includeTableTypes Boolean representing if the table types should be included
     *
     * @param includeInputTypes Boolean representing if the input types should be included
     *
     * @param includeDataTypes  Boolean representing if the data types should be included
     *
     * @param outputType        String representing rather the output is going to a single file or
     *                          multiple files. Should be "Single" or "Multiple"
     *
     * @param addEOFMarker      Is this the last data to be added to the file?
     *
     * @param addSOFMarker      Is this the first data to be added to the file?
     *
     * @throws CCDDException If a file I/O or parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportTableInfoDefinitions(FileEnvVar exportFile,
                                           boolean includeTableTypes,
                                           boolean includeInputTypes,
                                           boolean includeDataTypes,
                                           String outputType,
                                           boolean addEOFMarker,
                                           boolean addSOFMarker) throws CCDDException, Exception
    {
        // Placeholder
    }

    /**********************************************************************************************
     * Export script association data, group data, macro data, telemetry scheduler data or
     * application scheduler data to the specified folder
     *
     * @param includes   Array indicating internal file types to include
     *
     * @param dataTypes  The data type that is about to be exported
     *
     * @param exportFile Reference to the user-specified output file
     *
     * @param outputType String representing rather the output is going to a single file or
     *                   multiple files. Should be "Single" or "Multiple"
     *
     * @throws CCDDException If a file I/O or parsing error occurs
     *
     * @throws Exception     If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportInternalCCDDData(boolean[] includes,
                                       CcddConstants.exportDataTypes[] dataTypes,
                                       FileEnvVar exportFile,
                                       String outputType) throws CCDDException, Exception
    {
        // Placeholder
    }
}
