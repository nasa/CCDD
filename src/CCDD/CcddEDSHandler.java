/**************************************************************************************************
 * /** \file CcddEDSHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for handling import and export of data tables in EDS XML format. This class
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

import java.awt.Component;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.ccsds.schema.sois.seds.ArrayDimensionsType;
import org.ccsds.schema.sois.seds.ByteOrderType;
import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.ContainerDataType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
import org.ccsds.schema.sois.seds.DerivedTypeRangeType;
import org.ccsds.schema.sois.seds.DeviceType;
import org.ccsds.schema.sois.seds.DimensionSizeType;
import org.ccsds.schema.sois.seds.EnumeratedDataType;
import org.ccsds.schema.sois.seds.EnumerationListType;
import org.ccsds.schema.sois.seds.FloatDataEncodingType;
import org.ccsds.schema.sois.seds.FloatDataType;
import org.ccsds.schema.sois.seds.IntegerDataEncodingType;
import org.ccsds.schema.sois.seds.IntegerDataType;
import org.ccsds.schema.sois.seds.InterfaceCommandType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationSetType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationType;
import org.ccsds.schema.sois.seds.InterfaceParameterType;
import org.ccsds.schema.sois.seds.MetadataType;
import org.ccsds.schema.sois.seds.MetadataValueSetType;
import org.ccsds.schema.sois.seds.MetadataValueType;
import org.ccsds.schema.sois.seds.MinMaxRangeType;
import org.ccsds.schema.sois.seds.ObjectFactory;
import org.ccsds.schema.sois.seds.PackageType;
import org.ccsds.schema.sois.seds.RangeType;
import org.ccsds.schema.sois.seds.RootDataType;
import org.ccsds.schema.sois.seds.StringDataEncodingType;
import org.ccsds.schema.sois.seds.StringDataType;
import org.ccsds.schema.sois.seds.StringMetadataValueType;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInfo;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary EDS handler class
 *************************************************************************************************/
public class CcddEDSHandler extends CcddImportExportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDataTypeHandler dataTypeHandler;

    // Export endian type
    private EndianType endianess;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // List of exported/imported data types
    private List<String> dataTypes;

    // List of exported enumerations
    private List<String> enumerations;

    // JAXB and EDS object references
    private JAXBElement<DataSheetType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;
    private DeviceType device;
    private DataSheetType dataSheet;

    // Telemetry and command interface names
    private static final String STRUCTURE = "Structure";
    private static final String CMD_ARG_STRUCT = "Command Argument Structure";
    private static final String COMMAND = "Command";

    // Separators for key:value pairs, and keys and values
    private static final String KEY_VALUE_PAIR_SEPARATOR = ",,";
    private static final String KEY_VALUE_SEPARATOR = "==";

    // Key name for key:value pairings stored in container short descriptions
    private static final String MESSAGE_FIELD_KEY = "messageNameAndIdFieldName";
    private static final String MESSAGE_NAME_AND_ID_KEY = "messageNameAndId";
    private static final String APPLICATION_ID_KEY = "applicationID";
    private static final String ENUMERATION_KEY = "enumeration";
    private static final String RADIX_KEY = "radix";
    private static final String SEPARATORS_KEY = "separator";

    // Encoding types
    private static String IntegerEncodingTypeUnsigned = "unsigned";
    private static String IntegerEncodingTypeSignMagnitude = "signMagnitude";
    private static String StringEncodingTypeUTF_8 = "UTF-8";

    /**********************************************************************************************
     * EDS handler class constructor
     *
     * @param ccddMain Main class
     *
     * @param parent   GUI component over which to center any error dialog
     *
     * @throws CCDDException If an error occurs creating the handler
     *********************************************************************************************/
    CcddEDSHandler(CcddMain ccddMain, Component parent) throws CCDDException
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

        tableDefinitions = null;

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into EDS XML format
            JAXBContext context = JAXBContext.newInstance("org.ccsds.schema.sois.seds");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   ModifiableOtherSettingInfo.EDS_SCHEMA_LOCATION_URL.getValue());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            // Create the factory for building the data sheet objects
            factory = new ObjectFactory();

            // Create the XML unmarshaller used to convert EDS XML data into CCDD project data
            // format
            unmarshaller = context.createUnmarshaller();
        }
        catch (JAXBException je)
        {
            // Inform the user that the EDS/JAXB set up failed
            throw new CCDDException("EDS conversion setup failed; cause '</b>"
                                    + je.getMessage()
                                    + "<b>'");
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
     * Get the list of original and new telemetry scheduler data. Not used for EDS import
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
     * @param importFile              Import file reference
     *
     * @param ignoreErrors            True to ignore all errors in the import file
     *
     * @param replaceExistingMacros   True to replace existing macros
     *
     * @param replaceExistingTables   True to replace existing tables or table fields
     *
     * @param importingEntireDatabase True to replace existing database internal tables
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
        // Not implemented
        return;
    }

    /**********************************************************************************************
     * Build the information from the table definition(s) in the current file
     *
     * @param importFile              Import file reference
     *
     * @param importType              ImportType.IMPORT_ALL to import the table type, data type,
     *                                and macro definitions, and the data from all the table
     *                                definitions; ImportType.FIRST_DATA_ONLY to load only the data
     *                                for the first table defined
     *
     * @param targetTypeDefn          Table type definition of the table in which to import the
     *                                data; ignored if importing all tables
     *
     * @param ignoreErrors            True to ignore all errors in the import file
     *
     * @param replaceExistingMacros   True to replace the values for existing macros
     *
     * @param replaceExistingGroups   True to replace existing group definitions
     *
     * @param replaceExistingTables   True to replace existing tables or table fields
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
            tableDefinitions = new ArrayList<TableDefinition>();
            structureTypeDefn = null;
            commandTypeDefn = null;
            tlmHeaderTable = null;
            cmdHeaderTable = null;
            applicationIDName = null;
            cmdFuncCodeName = null;

            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            if (!(jaxbElement.getValue() instanceof DataSheetType))
            {
                throw new CCDDException("No dataSheet found in EDS file");
            }

            // Get the data sheet reference
            dataSheet = (DataSheetType) jaxbElement.getValue();

            if ((dataSheet.getDevice() != null)
                && (dataSheet.getDevice().getMetadata() != null)
                && (dataSheet.getDevice().getMetadata().getMetadataValueSet() != null))
            {
                MetadataValueSetType metadata = dataSheet.getDevice().getMetadata().getMetadataValueSet();

                // Get the telemetry header table name, if present
                tlmHeaderTable = getStringMetatdataValue(metadata, DefaultInputType.XML_TLM_HDR.getInputName());

                // Get the command header table name, if present
                cmdHeaderTable = getStringMetatdataValue(metadata, DefaultInputType.XML_CMD_HDR.getInputName());

                // Get the application ID variable name, if present
                applicationIDName = getStringMetatdataValue(metadata, DefaultInputType.XML_APP_ID.getInputName());

                // Get the command function code variable name, if present
                cmdFuncCodeName = getStringMetatdataValue(metadata, DefaultInputType.XML_FUNC_CODE.getInputName());
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
                dataTypes = new ArrayList<String>();

                // Step through each package
                for (PackageType packageType : dataSheet.getPackage())
                {
                    // Recursively step through the EDS-formatted data and extract the telemetry
                    // and command information
                    unbuildPackages(packageType, importType);

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
        catch (Exception e)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + e.getMessage() + "<b>'");
        }
    }

    /**********************************************************************************************
     * Extract the structure and/or command information from the package. This is a recursive
     * method
     *
     * @param packageType Table package
     *
     * @param importType  Import type: ImportType.ALL to import all information in the import
     *                    file; ImportType.FIRST_DATA_ONLY to import data from the first table
     *                    defined in the import file
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void unbuildPackages(PackageType packageType,
                                 ImportType importType) throws CCDDException
    {
        String tableType = packageType.getBase();

        // Check if the package has a valid table type name
        if (tableType != null
            && (tableType.equals(STRUCTURE)
                || tableType.equals(CMD_ARG_STRUCT)
                || tableType.equals(COMMAND)))
        {
            // Get the full table name, with path
            String tableName = packageType.getName();

            // Create a table definition for this structure table. If the package also includes a
            // command set (which creates a command table) then ensure the two tables have
            // different names
            TableDefinition tableDefn = new TableDefinition(tableName,
                                                            packageType.getLongDescription());

            // Check if a description exists for this structure table
            if (packageType.getLongDescription() != null
                && !packageType.getLongDescription().isEmpty())
            {
                // Store the table's description
                tableDefn.setDescription(packageType.getLongDescription());
            }

            // Get the application ID, if present, from the short description
            String applicationID = getValueByKey(packageType, APPLICATION_ID_KEY);

            // Check if the application ID is present
            if (applicationID != null && !applicationID.isEmpty())
            {
                boolean isExists = false;

                // Step through the data fields already added to this
                // table
                for (String[] fieldInfo : tableDefn.getDataFields())
                {
                    // Check if a data field with the name matching the application ID variable
                    // name already exists. This is the case if the command table has multiple
                    // commands; the first one causes the application ID field to be created, so
                    // the subsequent ones are ignored to prevent duplicates
                    if (fieldInfo[FieldsColumn.FIELD_NAME.ordinal()].equals(applicationIDName))
                    {
                        // Set the flag indicating the field already
                        // exists and stop searching
                        isExists = true;
                        break;
                    }
                }

                // Check if the application ID data field doesn't exist
                if (!isExists)
                {
                    // Create a data field for the table containing the application ID and stop
                    // searching
                    tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                    applicationIDName,
                                                                                    "Application name and ID",
                                                                                    inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                                    Math.min(Math.max(applicationID.length(), 5), 40),
                                                                                    false,
                                                                                    ApplicabilityType.ALL,
                                                                                    applicationID,
                                                                                    false));
                }
            }

            switch (tableType)
            {
                case STRUCTURE:
                case CMD_ARG_STRUCT:
                    // Extract the data types and create any that don't already exist
                    importDataTypes(packageType);

                    // Build the structure table from the telemetry data
                    importStructureTable(packageType, tableDefn);
                    break;

                case COMMAND:
                    // Build the command table from the telemetry data
                    importCommandTable(packageType, tableDefn);
                    break;
            }

            // Add the structure table definition to the list
            tableDefinitions.add(tableDefn);
        }
    }

    /**********************************************************************************************
     * Retrieve the data type(s) from the structure table
     *
     * @param structTblPkg Structure table package
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void importDataTypes(PackageType structTblPkg) throws CCDDException
    {
        // Check if the structure contains a data type (a structure with no variables will have no
        // data type set)
        if (structTblPkg.getDataTypeSet() != null)
        {
            // Step through the parameter type set to find the data type entry where the name
            // matches the parameter type reference from the parameter set
            for (RootDataType parmType : structTblPkg.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
            {
                String dataType = parmType.getName();

                // Check is this data type hasn't already been imported
                if (!dataTypes.contains(dataType))
                {
                    dataTypes.add(dataType);

                    // Structure data types (ContainerDataTypes) simply use the parmType name, so
                    // no further processing is needed
                    if (parmType instanceof ContainerDataType)
                    {
                        continue;
                    }

                    String baseDataType = null;
                    long dataTypeBitSize = 0;

                    // Check if the parameter is an integer data type
                    if (parmType instanceof IntegerDataType)
                    {
                        // The 'sizeInBits' references are the integer size for non-bit
                        // -wise parameters, but equal the number of bits assigned to the
                        // parameter for a bit-wise parameter. It doens't appear that the
                        // size of the integer used to contain the parameter is stored. The
                        // assumption is made that the smallest integer required to store
                        // the bits is used. However, this can alter the originally
                        // intended bit-packing (e.g., a 3-bit and a 9-bit fit within a
                        // single 16-bit integer, but the code below assigns the first to
                        // an 8-bit integer and the second to a 16-bit integer)

                        IntegerDataType itlm = (IntegerDataType) parmType;

                        // Check if integer encoding is set to 'unsigned'
                        if (itlm.getIntegerDataEncoding().getEncoding().equals(IntegerEncodingTypeUnsigned))
                        {
                            baseDataType = BaseDataTypeInfo.UNSIGNED_INT.getName();
                        }
                        else
                        {
                            baseDataType = BaseDataTypeInfo.SIGNED_INT.getName();
                        }

                        // Get the data type's size in bits
                        dataTypeBitSize = itlm.getIntegerDataEncoding().getSizeInBits().longValue();
                    }
                    // Check if the parameter is a floating point data type
                    else if (parmType instanceof FloatDataType)
                    {
                        // Get the float parameter attributes
                        FloatDataType ftlm = (FloatDataType) parmType;
                        dataTypeBitSize = ftlm.getFloatDataEncoding().getSizeInBits().longValue();
                        baseDataType = BaseDataTypeInfo.FLOATING_POINT.getName();
                    }
                    // Check if the parameter is a string data type
                    else if (parmType instanceof StringDataType)
                    {
                        // Get the string parameter attributes
                        StringDataType stlm = (StringDataType) parmType;
                        dataTypeBitSize = stlm.getLength().longValue() * 8;
                        baseDataType = BaseDataTypeInfo.CHARACTER.getName();
                    }

                    // Check if the primitive data type doesn't exist
                    if (baseDataType != null
                        && dataType != null
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
                }
            }
        }
    }

    /**********************************************************************************************
     * Build a structure table
     *
     * @param structTblPkg Structure table package
     *
     * @param tableDefn    Table definition
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void importStructureTable(PackageType structTblPkg,
                                      TableDefinition tableDefn) throws CCDDException
    {
        Integer[] rateIndices = null;
        TypeDefinition typeDefn = null;

        // Set the new structure table's table type name
        if (structTblPkg.getBase() != null && structTblPkg.getBase().equals(CMD_ARG_STRUCT))
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
        String messageFieldName = getValueByKey(structTblPkg, MESSAGE_FIELD_KEY);
        String messageNameAndID = getValueByKey(structTblPkg, MESSAGE_NAME_AND_ID_KEY);

        if (messageFieldName != null && messageNameAndID != null)
        {
            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(structTblPkg.getName(),
                                                                            messageFieldName,
                                                                            "Message name and ID",
                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                            Math.min(Math.max(messageNameAndID.length(), 5), 40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            messageNameAndID,
                                                                            false));
        }

        // Set the new structure table's table type name
        if (structTblPkg.getDeclaredInterfaceSet() != null
            && structTblPkg.getDeclaredInterfaceSet().getInterface() != null
            && structTblPkg.getDeclaredInterfaceSet().getInterface().size() != 0
            && structTblPkg.getDeclaredInterfaceSet().getInterface().get(0).getParameterSet() != null
            && structTblPkg.getDeclaredInterfaceSet().getInterface().get(0).getParameterSet().getParameter() != null)
        {
            int rowIndex = 0;

            for (InterfaceParameterType parameter : structTblPkg.getDeclaredInterfaceSet().getInterface().get(0).getParameterSet().getParameter())
            {
                String arraySize = null;
                String bitLength = null;
                String enumeration = null;
                String minimum = null;
                String maximum = null;
                String dataType = parameter.getType();
                String[] rates = new String[rateIndices.length];

                // Check if the data type is associated with an enumeration
                if (structTblPkg.getDataTypeSet() != null)
                {
                    // Step through the parameter type set to find the data type entry where the
                    // name matches the enumeration text
                    for (RootDataType parmType : structTblPkg.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                    {
                        // Check if the parameter is an enumerated data type
                        if (parmType instanceof EnumeratedDataType)
                        {
                            // Check if the enumeration data type short description contains the
                            // enumeration key
                            String enumText = getValueByKey(parameter, ENUMERATION_KEY);

                            if (enumText != null
                                && parmType.getName().equals(enumText))
                            {
                                // Get the enumeration parameters
                                EnumeratedDataType etlm = (EnumeratedDataType) parmType;
                                EnumerationListType enumList = etlm.getEnumerationList();

                                // Check if any enumeration parameters are defined
                                if (enumList != null)
                                {
                                    // Set the enumeration separator characters to the default
                                    // values
                                    String enumValSep = "|";
                                    String enumPairSep = ",";

                                    // Check if the separator character(s) are stored in the short
                                    // description
                                    String separators = getValueByKey(etlm, SEPARATORS_KEY);

                                    if (separators != null && !separators.isEmpty())
                                    {
                                        // Extract the separator character(s)
                                        String parts[] = separators.split(" ");
                                        enumValSep = parts[0];

                                        if (parts.length > 1)
                                        {
                                            enumPairSep = parts[1];
                                        }
                                    }

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
                                            enumeration += enumPairSep;
                                        }

                                        int base = 10;
                                        String radix = getValueByKey(enumType, RADIX_KEY);

                                        if (radix != null && radix.matches("\\d+"))
                                        {
                                            base = Integer.valueOf(radix);
                                            enumeration += "0x";
                                        }

                                        // Begin building this enumeration
                                        enumeration += enumType.getValue().toString(base)
                                                       + enumValSep
                                                       + enumType.getLabel();
                                    }
                                }

                                break;
                            }
                        }
                    }
                }

                // Extract the bit length, if present
                if (parameter.getIntegerDataEncoding() != null)
                {
                    bitLength = parameter.getIntegerDataEncoding().getSizeInBits().toString();
                }

                // Get the minimum and maximum values, if present
                DerivedTypeRangeType range = parameter.getNominalRangeSet();

                // Check if the range information exists
                if (range != null)
                {
                    // Get the minimum and maximum information
                    MinMaxRangeType minMaxRange = range.getMinMaxRange();

                    // Check if the minimum value is specified
                    if (minMaxRange.getMin() != null)
                    {
                        // Set the minimum value
                        minimum = minMaxRange.getMin().toString();
                    }

                    // Check if the maximum value is specified
                    if (minMaxRange.getMax() != null)
                    {
                        // Set the maximum value
                        maximum = minMaxRange.getMax().toString();
                    }
                }

                // Get the rate(s) from the parameter's short description, if present
                int index = 0;

                for (int rateIndex : rateIndices)
                {
                    // Get the rate value based on the rate column name
                    String rate = getValueByKey(parameter, typeDefn.getColumnNamesVisible()[rateIndex]);

                    if (rate != null)
                    {
                        rates[index] = rate;
                    }

                    ++index;
                }

                // Check if the array dimension is present
                if (parameter.getArrayDimensions() != null
                    && parameter.getArrayDimensions().getDimension() != null)
                {
                    arraySize = "";

                    // Step through each dimension for the array variable
                    for (DimensionSizeType dim : parameter.getArrayDimensions().getDimension())
                    {
                        // Build the array size string
                        arraySize += String.valueOf(dim.getSize().longValue()) + ",";
                    }

                    arraySize = CcddUtilities.removeTrailer(arraySize, ",");
                }

                // Get the total number of array members for the parameter; set to 0 if the
                // parameter isn't an array
                int numArrayMembers = arraySize != null
                                      && !arraySize.isEmpty() ? ArrayVariable.getNumMembersFromArraySize(arraySize)
                                                              : 0;

                // Add the row to the structure table. Multiple rows are added for an
                // array
                rowIndex = addVariableDefinitionToStructure(tableDefn,
                                                            rowIndex,
                                                            numArrayMembers,
                                                            parameter.getName(),
                                                            dataType,
                                                            arraySize,
                                                            bitLength,
                                                            parameter.getLongDescription(),
                                                            parameter.getUnit(),
                                                            enumeration,
                                                            minimum,
                                                            maximum,
                                                            rates);
            }
        }
    }

    /**********************************************************************************************
     * Add a variable definition's column values to a structure table
     *
     * @param tableDefn       Table definition reference
     *
     * @param rowIndex        Index of the row in which to insert the data
     *
     * @param numArrayMembers Number of array members; 0 if not an array parameter
     *
     * @param variableName    Variable name; null to not specify
     *
     * @param dataType        Parameter data type; null to not specify
     *
     * @param arraySize       Parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength       Parameter bit length; null or blank if not a bit-wise parameter
     *
     * @param description     Parameter description; null to not specify
     *
     * @param units           Parameter units; null to not specify
     *
     * @param enumeration     {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
     *
     * @param minimum         Minimum parameter value
     *
     * @param maximum         Maximum parameter value
     *
     * @param rates           Rate(s)
     *
     * @return Updated row index
     *********************************************************************************************/
    private int addVariableDefinitionToStructure(TableDefinition tableDefn,
                                                 int rowIndex,
                                                 int numArrayMembers,
                                                 String variableName,
                                                 String dataType,
                                                 String arraySize,
                                                 String bitLength,
                                                 String description,
                                                 String units,
                                                 String enumeration,
                                                 String minimum,
                                                 String maximum,
                                                 String[] rates)
    {
        // Check if at least one of the variable definition's column values is non-null
        if (variableName != null
            || dataType != null
            || arraySize != null
            || bitLength != null
            || description != null
            || units != null
            || enumeration != null
            || minimum != null
            || maximum != null
            || rates != null)
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
            if (tableDefn.getTypeName().equals(STRUCTURE))
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
                // occurs immediately prior to any of the array's members. The offset adjust the
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
                                variableName,
                                dataType,
                                arraySize,
                                bitLength,
                                description,
                                units,
                                enumeration,
                                minimum,
                                maximum,
                                rates,
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
                                    variableName,
                                    dataType,
                                    arraySize,
                                    bitLength,
                                    description,
                                    units,
                                    enumeration,
                                    minimum,
                                    maximum,
                                    rates,
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
     * @param variableName      Variable name; null to not specify
     *
     * @param dataType          Parameter data type; null to not specify
     *
     * @param arraySize         Parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength         Parameter bit length; null or blank if not a bit-wise parameter
     *
     * @param description       Parameter description; null to not specify
     *
     * @param units             Parameter units; null to not specify
     *
     * @param enumeration       {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
     *
     * @param minimum           Minimum parameter value
     *
     * @param maximum           Maximum parameter value
     *
     * @param rates             Rate(s)
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
                                 String variableName,
                                 String dataType,
                                 String arraySize,
                                 String bitLength,
                                 String description,
                                 String units,
                                 String enumeration,
                                 String minimum,
                                 String maximum,
                                 String[] rates,
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
        tableDefn.getData().set(offset + dataTypeIndex, dataType);
        tableDefn.getData().set(offset + arraySizeIndex, arraySize);
        tableDefn.getData().set(offset + bitLengthIndex, bitLength);

        if (enumerationIndex != -1)
        {
            tableDefn.getData().set(offset + enumerationIndex, enumeration);
        }

        if (descriptionIndex != -1)
        {
            tableDefn.getData().set(offset + descriptionIndex, description);
        }

        if (unitsIndex != -1)
        {
            tableDefn.getData().set(offset + unitsIndex, units);
        }

        if (minimumIndex != -1)
        {
            tableDefn.getData().set(offset + minimumIndex, minimum);
        }

        if (maximumIndex != -1)
        {
            tableDefn.getData().set(offset + maximumIndex, maximum);
        }

        for (int index = 0; index < rates.length; ++index)
        {
            tableDefn.getData().set(offset + rateIndices[index], rates[index]);
        }
    }

    /**********************************************************************************************
     * Build a command table from the specified command metadata
     *
     * @param cmdTablePkg Command table package
     *
     * @param tableDefn   Table definition
     *
     * @throws CCDDException If an input error is detected
     *********************************************************************************************/
    private void importCommandTable(PackageType cmdTablePkg,
                                    TableDefinition tableDefn) throws CCDDException
    {
        // Set the new command table's table type name
        tableDefn.setTypeName(commandTypeDefn.getName());

        // Create a data field for the table containing the message name and ID
        String messageFieldName = getValueByKey(cmdTablePkg, MESSAGE_FIELD_KEY);
        String messageNameAndID = getValueByKey(cmdTablePkg, MESSAGE_NAME_AND_ID_KEY);

        if (messageFieldName != null && messageNameAndID != null)
        {
            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(cmdTablePkg.getName(),
                                                                            messageFieldName,
                                                                            "Message name and ID",
                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                            Math.min(Math.max(messageNameAndID.length(), 5), 40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            messageNameAndID,
                                                                            false));
        }

        // Check if command information is present
        if (cmdTablePkg.getDeclaredInterfaceSet() != null
            && cmdTablePkg.getDeclaredInterfaceSet().getInterface() != null
            && cmdTablePkg.getDeclaredInterfaceSet().getInterface().size() != 0
            && cmdTablePkg.getDeclaredInterfaceSet().getInterface().get(0).getCommandSet() != null
            && cmdTablePkg.getDeclaredInterfaceSet().getInterface().get(0).getCommandSet().getCommand() != null)
        {
            // Step through each command in the table
            for (InterfaceCommandType command : cmdTablePkg.getDeclaredInterfaceSet().getInterface().get(0).getCommandSet().getCommand())
            {
                // Create a new row of data in the table definition to contain this command's
                // information. Initialize all columns to blanks except for the command name
                String[] cmdRowData = new String[commandTypeDefn.getColumnCountVisible()];
                Arrays.fill(cmdRowData, null);

                // Get the command name
                cmdRowData[commandNameIndex] = command.getName();

                // Check if the command description is present and the description column exists in
                // the table type definition
                if (command.getLongDescription() != null && cmdDescriptionIndex != -1)
                {
                    // Store the command description in the row's description column
                    cmdRowData[cmdDescriptionIndex] = command.getLongDescription();
                }

                // Check if a command argument is provided
                if (command.getArgument() != null && command.getArgument().size() != 0)
                {
                    CommandArgumentType argument =  command.getArgument().get(0);

                    // Store the command function code
                    cmdRowData[cmdFuncCodeIndex] = argument.getDefaultValue();

                    // Store the type as the command argument. The type also points to a
                    // DataTypeSet ContainerDataType of the same name. No other information is
                    // stored in the ContainerDataType so it is not accessed here
                    cmdRowData[cmdArgumentIndex] = argument.getType();
                }

                // Add the new row to the table definition
                tableDefn.addData(cmdRowData);
            }
        }
    }

    /**********************************************************************************************
     * Export the project tables in EDS XML format to the specified file
     *
     * @param exportFile              Reference to the user-specified output file
     *
     * @param tableDefs               List of table definitions to convert
     *
     * @param includeBuildInformation True to include the CCDD version, project, host, and user
     *                                information
     *
     * @param replaceMacros           * Not used for EDS export (all macros are expanded) * true to
     *                                replace any embedded macros with their corresponding values
     *
     * @param includeVariablePaths    * Not used for EDS export * true to include the variable path
     *                                for each variable in a structure table, both in application
     *                                format and using the user-defined separator characters
     *
     * @param variableHandler         Variable handler class reference; null if
     *                                includeVariablePaths is false
     *
     * @param separators              * Not used for EDS export * string array containing the
     *                                variable path separator character(s), show/hide data types
     *                                flag ('true' or 'false'), and data type/variable name
     *                                separator character(s); null if includeVariablePaths is false
     *
     * @param extraInfo               [0] endianess (EndianType.BIG_ENDIAN or
     *                                EndianType.LITTLE_ENDIAN) <br>
     *                                [1] are the telemetry and command headers big endian (true or
     *                                false)
     *
     * @throws JAXBException If an error occurs marshaling the project
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
        // Convert the table data into EDS format
        convertTablesToEDS(tableDefs,
                           includeBuildInformation,
                           (EndianType) extraInfo[0],
                           (boolean) extraInfo[1]);

        // Output the EDS XML file
        marshallXMLfile(project, marshaller, exportFile);
    }

    /**********************************************************************************************
     * Convert the project database contents to EDS XML format
     *
     * @param tableDefs               List of table definitions to convert to EDS format
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
     * @throws CCDDException If the user cancels the export
     *********************************************************************************************/
    private void convertTablesToEDS(List<TableInfo> tableDefs,
                                    boolean includeBuildInformation,
                                    EndianType endianess,
                                    boolean isHeaderBigEndian) throws CCDDException
    {
        this.endianess = endianess;

        // Create the project's data sheet and device
        dataSheet = factory.createDataSheetType();
        project = factory.createDataSheet(dataSheet);
        device = factory.createDeviceType();

        // The device name, built from the project name, is restricted as to format, so replace all
        // invalid characters in the project name with an underscore
        String deviceName = dbControl.getProjectName().replaceAll("[^a-zA-Z0-9_]", "_");

        // Check if the initial character is invalid (i.e., a numeral after the above replacement
        // is performed)
        if (deviceName.matches("[^a-zA-Z_].*"))
        {
            // Preface the device with an underscore to make it valid
            deviceName = "_" + deviceName;
        }

        // Store the modified (if needed) project name as the device name
        device.setName(deviceName);

        // Check if the build information is to be output
        if (includeBuildInformation)
        {
            // Set the device description field
            device.setLongDescription(dbControl.getDatabaseDescription(dbControl.getDatabaseName())
                                      + "\n\nAuthor: "
                                      + dbControl.getUser()
                                      + "\nCCDD Version: "
                                      + ccddMain.getCCDDVersionInformation()
                                      + "\nDate: "
                                      + new Date().toString()
                                      + "\nProject: "
                                      + dbControl.getProjectName()
                                      + "\nHost: "
                                      + dbControl.getServer()
                                      + "\nEndianess: "
                                      + (endianess == EndianType.BIG_ENDIAN ? "big" : "little"));
        }

        // Get the names of the tables representing the telemetry and command headers
        tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    DefaultInputType.XML_TLM_HDR);
        cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    DefaultInputType.XML_CMD_HDR);

        // Get the command header argument names for the application ID and the command function
        // code. These are stored as project-level data fields
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
        // code variable names are stored as metadata which is used if the export file is imported
        // into CCDD
        MetadataType data = factory.createMetadataType();
        MetadataValueSetType metadataSet = factory.createMetadataValueSetType();

        // Check if the telemetry header table name is defined
        if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
        {
            // Store the telemetry header table name
            createStringMetatdata(metadataSet, DefaultInputType.XML_TLM_HDR.getInputName(), tlmHeaderTable);
        }

        // Check if the command header table name is defined
        if (cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Store the command header table name
            createStringMetatdata(metadataSet, DefaultInputType.XML_CMD_HDR.getInputName(), cmdHeaderTable);
        }

        // Store the application ID variable name
        createStringMetatdata(metadataSet, DefaultInputType.XML_APP_ID.getInputName(), applicationIDName);

        // Store the command function code variable name
        createStringMetatdata(metadataSet, DefaultInputType.XML_FUNC_CODE.getInputName(), cmdFuncCodeName);

        data.setMetadataValueSet(metadataSet);
        device.setMetadata(data);

        dataSheet.setDevice(device);

        // Add the project's packages, parameters, and commands
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
        dataTypes = new ArrayList<String>();
        enumerations = new ArrayList<String>();

        // Add the table package
        PackageType tablePackage = addPackage(tableName,
                                              tableInfo.getDescription(),
                                              messageFieldName,
                                              messageNameAndID,
                                              typeDefn);

        // Check if this is a structure table
        if (typeDefn.isStructure())
        {
            // Add the structure table to the data sheet
            addParameterInterface(tablePackage,
                                  CcddUtilities.convertObjectToString(tableInfo.getDataArray()),
                                  typeDefn);
        }
        // This is a command table
        else
        {
            // Add the command(s) from this table to the data sheet
            addPackageCommands(tablePackage,
                               CcddUtilities.convertObjectToString(tableInfo.getDataArray()),
                               typeDefn);
        }
    }

    /**********************************************************************************************
     * Create a new table package
     *
     * @param tablePath        Table name with full path
     *
     * @param description      Data sheet description
     *
     * @param messageFieldName Message name and ID field name; null if not present or not applicable
     *
     * @param messageNameAndID Message name and ID; null if not present or not applicable
     *
     * @param typeDefn         Table type definition
     *
     * @return Reference to the new package
     *********************************************************************************************/
    private PackageType addPackage(String tablePath,
                                   String description,
                                   String messageFieldName,
                                   String messageNameAndID,
                                   TypeDefinition typeDefn)
    {
        // Create the new table package
        PackageType tablePackage = factory.createPackageType();

        // Set the table name with path as the package name
        tablePackage.setName(tablePath);

        // Check if a description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            tablePackage.setLongDescription(description);
        }

        // Set the base to indicate the table type
        tablePackage.setBase((typeDefn.isCommandArgumentStructure() ? CMD_ARG_STRUCT
                                                                    : typeDefn.isStructure() ? STRUCTURE
                                                                                             : typeDefn.isCommand() ? COMMAND
                                                                                                                    : typeDefn.getName()));

        // Store the message name and ID, if it exists, in the short description field as a
        // key:value pair
        if (messageFieldName != null && !messageFieldName.isEmpty())
        {
            setKeyValuePair(tablePackage, MESSAGE_FIELD_KEY, messageFieldName);
            setKeyValuePair(tablePackage, MESSAGE_NAME_AND_ID_KEY, messageNameAndID);
        }

        // Add the new names space
        dataSheet.getPackage().add(tablePackage);

        return tablePackage;
    }

    /**********************************************************************************************
     * Build a structure or command table in XML format for export
     *
     * @param structTblPkg Structure table package
     *
     * @param tableData    Table data array
     *
     * @param typeDefn     Table type definition
     *
     * @throws CCDDException If an enumeration cannot be parsed
     *********************************************************************************************/
    private void addParameterInterface(PackageType structTblPkg,
                                       String[][] tableData,
                                       TypeDefinition typeDefn) throws CCDDException
    {
        // Check if the structure table has any variables
        if (tableData.length != 0)
        {
            // Get the column indices
            int varColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
            int typeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
            int sizeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
            int bitColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);
            int enumColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.ENUMERATION);
            int descColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);
            int unitsColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS);
            int minColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MINIMUM);
            int maxColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MAXIMUM);
            Integer[] rateColumns = typeDefn.getColumnIndicesByInputTypeFormat(InputTypeFormat.RATE).toArray(new Integer[0]);

            InterfaceDeclarationSetType intrfcSet = factory.createInterfaceDeclarationSetType();
            InterfaceDeclarationType intrfcDecl = factory.createInterfaceDeclarationType();
            intrfcDecl.setParameterSet(factory.createParameterSetType());
            intrfcSet.getInterface().add(intrfcDecl);
            structTblPkg.setDeclaredInterfaceSet(intrfcSet);

            // Step through each row of data in the structure table
            for (String[] rowData : tableData)
            {
                // Check if this is not an array member (non-array parameters and array definitions
                // are used to create the list)
                if (!ArrayVariable.isArrayMember(rowData[varColumn])
                    || ((descColumn != -1 && rowData[descColumn] != null && !rowData[descColumn].isEmpty())
                        || (enumColumn != -1 && rowData[enumColumn] != null && !rowData[enumColumn].isEmpty())
                        || (unitsColumn != -1 && rowData[unitsColumn] != null && !rowData[unitsColumn].isEmpty())
                        || (minColumn != -1 && rowData[minColumn] != null && !rowData[minColumn].isEmpty())
                        || (maxColumn != -1 && rowData[maxColumn] != null && !rowData[maxColumn].isEmpty())))
                {
                    InterfaceParameterType parameter = factory.createInterfaceParameterType();

                    String dataType = rowData[typeColumn];
                    String dataTypeFull = dataType;
                    String arraySize = rowData[sizeColumn];
                    String[] enumerationInfo = null;

                    // Check is the array size is provided
                    if (!arraySize.isEmpty())
                    {
                        ArrayDimensionsType arrayDim = factory.createArrayDimensionsType();
                        List<DimensionSizeType> dimSizes = arrayDim.getDimension();

                        // Step through each array dimension
                        for (int dim : ArrayVariable.getArrayIndexFromSize(arraySize))
                        {
                            // Create a dimension entry for the array type. The dimension size is
                            // the number of elements in this array dimension
                            DimensionSizeType dimSize = factory.createDimensionSizeType();
                            dimSize.setSize(BigInteger.valueOf(dim));
                            dimSizes.add(dimSize);
                        }

                        parameter.setArrayDimensions(arrayDim);
                    }

                    // Check if the bit length is provided
                    if (!rowData[bitColumn].isEmpty())
                    {
                        IntegerDataEncodingType intData = factory.createIntegerDataEncodingType();
                        intData.setSizeInBits(new BigInteger(rowData[bitColumn]));
                        parameter.setIntegerDataEncoding(intData);
                    }

                    // Check if enumeration parameters are provided
                    if (enumColumn != -1
                        && (rowData[enumColumn] == null || !rowData[enumColumn].isEmpty()))
                    {
                        // Store the enumeration in the short description
                        enumerationInfo = rowData[enumColumn] == null ? new String[] {""} : cleanUpEnumeration(rowData[enumColumn]);
                        setKeyValuePair(parameter, ENUMERATION_KEY, enumerationInfo[0]);
                    }

                    parameter.setName(rowData[varColumn]);
                    parameter.setType(dataTypeFull);

                    // Check if the description is overridden by a blank (= null), or is not empty
                    if ((descColumn != -1
                        && (rowData[descColumn] == null || !rowData[descColumn].isEmpty())))
                    {
                        parameter.setLongDescription(rowData[descColumn] == null ? "" : rowData[descColumn]);
                    }

                    // Check if the units value is provided
                    if (unitsColumn != -1
                        && (rowData[unitsColumn] == null || !rowData[unitsColumn].isEmpty()))
                    {
                        parameter.setUnit(rowData[unitsColumn] == null ? "" : rowData[unitsColumn]);
                    }

                    DerivedTypeRangeType range = null;
                    MinMaxRangeType minMaxRange = null;

                    // Check if the minimum value is provided
                    if (minColumn != -1
                        && (rowData[minColumn] == null || !rowData[minColumn].isEmpty()))
                    {
                        range = factory.createDerivedTypeRangeType();
                        minMaxRange = factory.createMinMaxRangeType();
                        minMaxRange.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);
                        minMaxRange.setMin(rowData[minColumn] == null ? "" : rowData[minColumn]);
                    }

                    // Check if the maximum value is provided
                    if (maxColumn != -1
                        && (rowData[maxColumn] == null || !rowData[maxColumn].isEmpty()))
                    {
                        // Create the range type if not already created by the minimum value
                        if (range == null)
                        {
                            range = factory.createDerivedTypeRangeType();
                            minMaxRange = factory.createMinMaxRangeType();
                            minMaxRange.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);
                        }

                        minMaxRange.setMax(rowData[maxColumn] == null ? "" : rowData[maxColumn]);
                    }

                    // Check if the minimum or maximum value was set
                    if (range != null)
                    {
                        // Store the minimum and/or maximum value(s)
                        range.setMinMaxRange(minMaxRange);
                        parameter.setNominalRangeSet(range);
                    }

                    // Step through each rate column
                    for (int rateColumn : rateColumns)
                    {
                        if (rowData[rateColumn] == null || !rowData[rateColumn].isEmpty())
                        {
                            // Store each rate value for this rate column
                            setKeyValuePair(parameter,
                                            typeDefn.getColumnNamesUser()[rateColumn],
                                            rowData[rateColumn] == null ? ""
                                                                        : rowData[rateColumn]);
                        }
                    }

                    // Check if this is not an array member
                    if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                    {
                        // Add the data type to the data type set
                        addDataType(structTblPkg, dataType, arraySize, enumerationInfo);
                    }

                    // Add the parameter to the parameter set
                    intrfcSet.getInterface().get(0).getParameterSet().getParameter().add(parameter);
                }
            }
        }
    }

    /**********************************************************************************************
     * Build a structure or command table in XML format for export
     *
     * @param structTblPkg    Structure table package
     *
     * @param dataType        Parameter data type
     *
     * @param arraySize       Parameter array size
     *
     * @param enumerationInfo String array where the first member is the {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]},
     *                        the second member is the value separator character(s), and the third
     *                        member is the value pair separator character(s) (null if only a
     *                        single pair is provided)
     *********************************************************************************************/
    private void addDataType(PackageType structTblPkg,
                             String dataType,
                             String arraySize,
                             String[] enumerationInfo)
    {
        RootDataType parameterType = null;
        DataTypeSetType dataTypeSet = structTblPkg.getDataTypeSet();

        // Create the data type set if it doesn't exists
        if (dataTypeSet == null)
        {
            dataTypeSet = factory.createDataTypeSetType();
            structTblPkg.setDataTypeSet(dataTypeSet);
        }

        // Check if enumeration parameters are provided and this enumeration hasn't
        // already been added  (to prevent duplicate entries)
        if (enumerationInfo != null
            && enumerationInfo.length != 0
            && !enumerationInfo[0].isEmpty()
            && !enumerations.contains(enumerationInfo[0]))
        {
            String enumeration = enumerationInfo[0];
            enumerations.add(enumeration);

            // Create an enumeration type and enumeration list
            EnumeratedDataType enumType = factory.createEnumeratedDataType();
            createEnumerationList(structTblPkg, enumType, enumerationInfo);

            // Set the type name
            enumType.setName(enumeration);

            // Add the data type information to this package
            dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(enumType);
        }

        // Check if the data type hasn't already been added (to prevent duplicate entries)
        if (!dataTypes.contains(dataType))
        {
            dataTypes.add(dataType);

            // Check if the parameter is a primitive data type
            if (dataTypeHandler.isPrimitive(dataType))
            {
                // Get the base data type corresponding to the primitive data type
                BasePrimitiveDataType baseDataType = getBaseDataType(dataType, dataTypeHandler);

                // Check if the a corresponding base data type exists
                if (baseDataType != null)
                {
                    switch (baseDataType)
                    {
                        case INTEGER:
                            // Create an integer type
                            IntegerDataType integerType = factory.createIntegerDataType();
                            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                            intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            intEncodingType.setEncoding(dataTypeHandler.isUnsignedInt(dataType) ? IntegerEncodingTypeUnsigned
                                                                                                : IntegerEncodingTypeSignMagnitude);
                            intEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN ? ByteOrderType.BIG_ENDIAN
                                                                                            : ByteOrderType.LITTLE_ENDIAN);
                            integerType.setIntegerDataEncoding(intEncodingType);
                            parameterType = integerType;
                            break;

                        case FLOAT:
                            // Create a float type
                            FloatDataType floatType = factory.createFloatDataType();
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            floatEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN ? ByteOrderType.BIG_ENDIAN
                                                                                              : ByteOrderType.LITTLE_ENDIAN);
                            floatType.setFloatDataEncoding(floatEncodingType);
                            parameterType = floatType;
                            break;

                        case STRING:
                            // Create a string type
                            StringDataType stringType = factory.createStringDataType();
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();
                            stringEncodingType.setEncoding(StringEncodingTypeUTF_8);
                            stringEncodingType .setByteOrder(endianess == EndianType.BIG_ENDIAN ? ByteOrderType.BIG_ENDIAN
                                                                                                : ByteOrderType.LITTLE_ENDIAN);
                            stringType.setStringDataEncoding(stringEncodingType);
                            stringType.setLength(BigInteger.valueOf(!dataType.isEmpty()
                                                                    && dataTypeHandler.isString(dataType)
                                                                    && !arraySize.isEmpty() ? Integer.valueOf(arraySize.replaceAll("^.*(\\d+)$",
                                                                                                                                   "$1"))
                                                                                            : 1));
                            parameterType = stringType;
                            break;
                    }
                }
            }
            // Structure data type
            else
            {
                // Create a container type for the structure
                ContainerDataType containerType = factory.createContainerDataType();

                // Set the parameter's base type
                parameterType = containerType;
            }

            if (parameterType != null)
            {
                // Set the type name
                parameterType.setName(dataType);

                // Add the data type information to this package
                dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(parameterType);
            }
        }
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified package
     *
     * @param cmdTblPkg Command table package
     *
     * @param tableData Table data array
     *
     * @param typeDefn  Table type definition
     *********************************************************************************************/
    private void addPackageCommands(PackageType cmdTblPkg,
                                    String[][] tableData,
                                    TypeDefinition typeDefn)
    {
        // Check if the command table has any commands
        if (tableData.length != 0)
        {
            // Get the column indices
            int cmdNameColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME);
            int cmdCodeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE);
            int cmdArgumentColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_ARGUMENT);
            int cmdDescColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);

            InterfaceDeclarationSetType intrfcSet = factory.createInterfaceDeclarationSetType();
            InterfaceDeclarationType intrfcDecl = factory.createInterfaceDeclarationType();
            intrfcDecl.setCommandSet(factory.createCommandSetType());
            intrfcSet.getInterface().add(intrfcDecl);
            cmdTblPkg.setDeclaredInterfaceSet(intrfcSet);

            // Step through each row in the table
            for (String[] cmdRowData : tableData)
            {
                // Check if the command name exists
                if (cmdNameColumn != -1 && !cmdRowData[cmdNameColumn].isEmpty())
                {
                    // Check if the command name exists
                    if (cmdNameColumn != -1 && !cmdRowData[cmdNameColumn].isEmpty())
                    {
                        InterfaceCommandType command = factory.createInterfaceCommandType();
                        CommandArgumentType cmdArg = factory.createCommandArgumentType();

                        // Store the command name
                        command.setName(cmdRowData[cmdNameColumn]);

                        // Check if the command description exists
                        if (cmdDescColumn != -1 && !cmdRowData[cmdDescColumn].isEmpty())
                        {
                            // Store the command description
                            command.setLongDescription(cmdRowData[cmdDescColumn]);
                        }

                        // Check if the command code exists
                        if (cmdCodeColumn != -1 && !cmdRowData[cmdCodeColumn].isEmpty())
                        {
                            // Store the command code
                            cmdArg.setDefaultValue(cmdRowData[cmdCodeColumn]);
                        }

                        // Check if the command argument column and value exist
                        if (cmdArgumentColumn != -1 && !cmdRowData[cmdArgumentColumn].isEmpty())
                        {
                            // Store the command argument
                            cmdArg.setType(cmdRowData[cmdArgumentColumn]);
                            addDataType(cmdTblPkg, cmdRowData[cmdArgumentColumn], "", null);
                        }

                        // Check if the command code or the command argument structure is provided
                        if (cmdArg.getDefaultValue() != null || cmdArg.getType() != null)
                        {
                            // Store the argument information
                            command.getArgument().add(cmdArg);
                        }

                        // Add the command to the list
                        intrfcDecl.getCommandSet().getCommand().add(command);
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Build the enumeration information from the supplied enumeration string
     *
     * @param enumeration {@literal Enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return String array where the first member is the enumeration, the second member is the
     *         value separator character(s), and the third member is the value pair separator
     *         character(s) (null if only a single pair is provided)
     *
     * @throws CCDDException If an enumeration cannot be parsed
     *********************************************************************************************/
    private String[] cleanUpEnumeration(String enumeration) throws CCDDException
    {
        // Get the character that separates the enumeration value from the associated label
        String enumValSep = CcddUtilities.getEnumeratedValueSeparator(enumeration);

        // Check if the enumeration value is missing or the value separator couldn't be located
        if (enumValSep == null)
        {
            throw new CCDDException("Initial non-negative integer or separator character "
                                    + "between enumeration value and label missing");
        }

        // Remove spaces surrounding the value separator
        enumeration = enumeration.replaceAll("\\s*" + Pattern.quote(enumValSep) +"\\s*",
                                             enumValSep);

        // Get the character that separates the enumerated pairs
        String enumPairSep = CcddUtilities.getEnumerationGroupSeparator(enumeration, enumValSep);

        if (enumPairSep != null)
        {
            // Remove spaces surrounding the pair separator
            enumeration = enumeration.replaceAll("\\s*" + Pattern.quote(enumPairSep) +"\\s*",
                                                 enumPairSep);
        }

        return new String[] {enumeration, enumValSep, enumPairSep};
    }

    /**********************************************************************************************
     * Build an enumeration list from the supplied enumeration information
     *
     * @param structTblPkg    Structure table package
     *
     * @param enumType        Enumeration type
     *
     * @param enumerationInfo String array where the first member is the {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]},
     *                        the second member is the value separator character(s), and the third
     *                        member is the value pair separator character(s) (null if only a
     *                        single pair is provided)
     *********************************************************************************************/
    private void createEnumerationList(PackageType structTblPkg,
                                       EnumeratedDataType enumType,
                                       String[] enumerationInfo)
    {
        EnumerationListType enumList = factory.createEnumerationListType();
        String enumeration = enumerationInfo[0];
        String enumValSep = enumerationInfo[1];
        String enumPairSep = enumerationInfo[2];

        String[] enumDefn;

        // Check if the enumerated pair separator doesn't exists, which indicates that only a
        // single enumerated value is defined
        if (enumPairSep == null)
        {
            enumDefn = new String[] {enumeration};
        }
        // Multiple enumerated values are defined
        else
        {
            // Remove spaces surrounding the pair separator
            enumeration = enumeration.replaceAll("\\s*" + Pattern.quote(enumPairSep) +"\\s*",
                                                 enumPairSep);

            // Divide the enumeration string into the separate enumeration definitions
            enumDefn = enumeration.split(Pattern.quote(enumPairSep));
        }

        // Step through each enumeration definition
        for (int index = 0; index < enumDefn.length; index++)
        {
            // Split the enumeration definition into the number and label components
            String[] enumParts = enumDefn[index].split(Pattern.quote(enumValSep), 2);

            // Create a new enumeration value type and add the enumerated name and value to the
            // enumeration list
            ValueEnumerationType valueEnum = factory.createValueEnumerationType();
            valueEnum.setLabel(enumParts[1].trim());
            String enumValue = enumParts[0].trim();
            valueEnum.setValue(BigInteger.valueOf(Long.decode(enumValue)));

            // Store the base if the value is in hexadecimal. This is used during import to
            // restore the value to the original base
            if (enumValue.startsWith("0x"))
            {
                setKeyValuePair(valueEnum, RADIX_KEY, "16");
            }

            enumList.getEnumeration().add(valueEnum);
        }

        // Store the enumeration information
        setKeyValuePair(enumType, SEPARATORS_KEY, enumValSep + " " + enumPairSep);
        enumType.setEnumerationList(enumList);
    }

    /**********************************************************************************************
     * Create a name/value pair and add it to a MetadataValueSetType set
     *
     * @param mvst  The MetadataValueSetType object in which to place the data; null to create a
     *              new one
     *
     * @param name  Name of the metadata object
     *
     * @param value Value to store as metadata
     *
     * @return The MetadataValueSetType set containing the name/value pair
     *********************************************************************************************/
    protected MetadataValueSetType createStringMetatdata(MetadataValueSetType mvst, String name, String value)
    {
        if (mvst == null)
        {
            mvst = factory.createMetadataValueSetType();
        }

        StringMetadataValueType adt = factory.createStringMetadataValueType();
        adt.setName(name);
        adt.setValue(value);
        mvst.getDateValueOrFloatValueOrIntegerValue().add(adt);
        return mvst;
    }

    /**********************************************************************************************
     * Return the value of a metadata item based on the item name
     *
     * @param mvst  The MetadataValueSetType set in which to search for the name
     *
     * @param name  Name of the metadata object for which to search
     *
     * @return The value associated with the name; null if the name is not in the metadata set
     *********************************************************************************************/
    protected String getStringMetatdataValue(MetadataValueSetType mvst, String name)
    {
        String value = null;

        // Check if the MetadataValueSetType isn't null
        if (mvst != null)
        {
            // Find the first occurrence of a name/value pair with the specified name in the
            // metadata data list
            Optional<MetadataValueType> dataPair = mvst.getDateValueOrFloatValueOrIntegerValue()
                                                       .stream()
                                                       .filter(p -> p.getName().equals(name))
                                                       .findFirst();

            // Check if a name/value pair with the specified name exists
            if (dataPair.isPresent())
            {
                // Get the value associated with the name
                value = ((StringMetadataValueType) dataPair.get()).getValue();
            }
        }

        return value;
    }

    /**********************************************************************************************
     * Set the value of a key:value pair in a container's short description field in the format
     * {@literal key1==value1< ,, key2==value2<...>>}
     *
     * @param container The container into which to store the key:value pair
     *
     * @param key       Key string
     *
     * @param value     String value to associate with the key
     *********************************************************************************************/
    private void setKeyValuePair(Object container, String key, String value)
    {
        String keyValue = key + KEY_VALUE_SEPARATOR + value;

        if (container instanceof InterfaceParameterType)
        {
            String desc = ((InterfaceParameterType) container).getShortDescription();

            if (desc != null && !desc.isEmpty())
            {
                keyValue = desc + " " + KEY_VALUE_PAIR_SEPARATOR + " " + keyValue;
            }

            ((InterfaceParameterType) container).setShortDescription(keyValue);
        }
        else if (container instanceof EnumeratedDataType)
        {
            String desc = ((EnumeratedDataType) container).getShortDescription();

            if (desc != null && !desc.isEmpty())
            {
                keyValue = desc + " " + KEY_VALUE_PAIR_SEPARATOR + " " + keyValue;
            }

            ((EnumeratedDataType) container).setShortDescription(keyValue);
        }
        else if (container instanceof ValueEnumerationType)
        {
            String desc = ((ValueEnumerationType) container).getShortDescription();

            if (desc != null && !desc.isEmpty())
            {
                keyValue = desc + " " + KEY_VALUE_PAIR_SEPARATOR + " " + keyValue;
            }

            ((ValueEnumerationType) container).setShortDescription(keyValue);
        }
        else if (container instanceof PackageType)
        {
            String desc = ((PackageType) container).getShortDescription();

            if (desc != null && !desc.isEmpty())
            {
                keyValue = desc + " " + KEY_VALUE_PAIR_SEPARATOR + " " + keyValue;
            }

            ((PackageType) container).setShortDescription(keyValue);
        }
    }

    /**********************************************************************************************
     * Get the value of a key:value pair, based on the key, from a container's short description
     * field in the format {@literal key1==value1< ,, key2==value2<...>>}
     *
     * @param container The container in which to search for the key:value pair
     *
     * @param key       Key string
     *
     * @return The string value associated with the key; null if the key isn't found
     *********************************************************************************************/
    private String getValueByKey(Object container, String key)
    {
        String keyValuePairs = null;
        String value = null;

        if (container instanceof InterfaceParameterType)
        {
            keyValuePairs = ((InterfaceParameterType) container).getShortDescription();
        }
        else if (container instanceof EnumeratedDataType)
        {
            keyValuePairs = ((EnumeratedDataType) container).getShortDescription();
        }
        else if (container instanceof ValueEnumerationType)
        {
            keyValuePairs = ((ValueEnumerationType) container).getShortDescription();
        }
        else if (container instanceof PackageType)
        {
            keyValuePairs = ((PackageType) container).getShortDescription();
        }

        if (keyValuePairs != null)
        {
            for (String pair : keyValuePairs.split(KEY_VALUE_PAIR_SEPARATOR))
            {
                String parts[] = pair.split(KEY_VALUE_SEPARATOR);

                if (parts[0].trim().equals(key))
                {
                    value = parts.length == 1 ? "" : parts[1].trim();
                    break;
                }
            }
        }

        return value;
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
     * @throws CCDDException If a file I/O or JSON JavaScript parsing error occurs
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
