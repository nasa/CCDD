/**
 * // List containing the table paths for children of root tables not explicitly included in the //
 * array of tables to export private List<String> missingChildTables;
 *
 * CFS Command and Data Dictionary EDS handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.MarshalException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.ccsds.schema.sois.seds.ArrayDataType;
import org.ccsds.schema.sois.seds.ArrayDimensionsType;
import org.ccsds.schema.sois.seds.BaseTypeSetType;
import org.ccsds.schema.sois.seds.ByteOrderType;
import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.ContainerConstraintType;
import org.ccsds.schema.sois.seds.ContainerDataType;
import org.ccsds.schema.sois.seds.ContainerDataType.ConstraintSet;
import org.ccsds.schema.sois.seds.ContainerValueConstraintType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
import org.ccsds.schema.sois.seds.DerivedTypeRangeType;
import org.ccsds.schema.sois.seds.DescriptionType;
import org.ccsds.schema.sois.seds.DeviceType;
import org.ccsds.schema.sois.seds.DimensionSizeType;
import org.ccsds.schema.sois.seds.EntryListType;
import org.ccsds.schema.sois.seds.EntryType;
import org.ccsds.schema.sois.seds.EnumeratedDataType;
import org.ccsds.schema.sois.seds.EnumerationListType;
import org.ccsds.schema.sois.seds.FloatDataEncodingType;
import org.ccsds.schema.sois.seds.FloatDataType;
import org.ccsds.schema.sois.seds.FloatDataTypeRangeType;
import org.ccsds.schema.sois.seds.FloatEncodingAndPrecisionType;
import org.ccsds.schema.sois.seds.FloatPrecisionRangeType;
import org.ccsds.schema.sois.seds.IntegerDataEncodingType;
import org.ccsds.schema.sois.seds.IntegerDataType;
import org.ccsds.schema.sois.seds.IntegerDataTypeRangeType;
import org.ccsds.schema.sois.seds.IntegerEncodingType;
import org.ccsds.schema.sois.seds.InterfaceCommandType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationType;
import org.ccsds.schema.sois.seds.InterfaceParameterType;
import org.ccsds.schema.sois.seds.InterfaceRefType;
import org.ccsds.schema.sois.seds.MetadataType;
import org.ccsds.schema.sois.seds.MetadataValueSetType;
import org.ccsds.schema.sois.seds.MetadataValueType;
import org.ccsds.schema.sois.seds.MinMaxRangeType;
import org.ccsds.schema.sois.seds.NamespaceType;
import org.ccsds.schema.sois.seds.NumericDataType;
import org.ccsds.schema.sois.seds.ObjectFactory;
import org.ccsds.schema.sois.seds.RangeType;
import org.ccsds.schema.sois.seds.RootDataType;
import org.ccsds.schema.sois.seds.SemanticsType;
import org.ccsds.schema.sois.seds.StringDataEncodingType;
import org.ccsds.schema.sois.seds.StringDataType;
import org.ccsds.schema.sois.seds.StringEncodingType;
import org.ccsds.schema.sois.seds.StringMetadataValueType;
import org.ccsds.schema.sois.seds.Unit;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary EDS handler class
 *************************************************************************************************/
public class CcddEDSHandler extends CcddImportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private TypeDefinition typeDefn;
    private final CcddMacroHandler macroHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddInputTypeHandler inputTypeHandler;

    // GUI component over which to center any error dialog
    private final Component parent;

    // Export endian type
    private EndianType endianess;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and EDS object references
    private JAXBElement<DataSheetType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;
    private DeviceType device;
    private DataSheetType dataSheet;

    // Names of the system paths for the common header for all command tables
    private String cmdHeaderPath;

    // Table type definitions
    private TypeDefinition structureTypeDefn;
    private TypeDefinition commandTypeDefn;

    // Flags to indicate if a telemetry and command table is defined in the import file, and if a
    // command header is defined, which entails converting command information into a structure
    private boolean isTelemetry;
    private boolean isCommand;
    private boolean isCmdToTlm;

    // List of the associated command arguments
    private List<AssociatedColumns> commandArguments;

    // Maximum number of command arguments for all command tables defined in the import file
    private int maxNumArguments;

    // Structure column indices
    private int variableNameIndex;
    private int dataTypeIndex;
    private int arraySizeIndex;
    private int bitLengthIndex;
    private int enumerationIndex;
    private int minimumIndex;
    private int maximumIndex;
    private int descriptionIndex;
    private int unitsIndex;

    // Command column indices
    private int commandNameIndex;
    private int cmdFuncCodeIndex;
    private int cmdDescriptionIndex;

    // Number of visible structure and command table columns
    private int numStructureColumns;
    private int numCommandColumns;

    // Text appended to the parameter and command type and array references
    private static String TYPE = "_Type";
    private static String ARRAY = "_Array";

    // Telemetry and command interface names
    private static String TELEMETRY = "Telemetry";
    private static String COMMAND = "Command";

    /**********************************************************************************************
     * EDS handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If an error occurs creating the handler
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
        rateHandler = ccddMain.getRateParameterHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

        tableDefinitions = null;

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into EDS XML format
            JAXBContext context = JAXBContext.newInstance("org.ccsds.schema.sois.seds");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   ModifiableOtherSettingInfo.EDS_SCHEMA_LOCATION_URL.getValue());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));

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
     * Import the the table definitions from an EDS XML formatted file
     *
     * @param importFile
     *            reference to the user-specified XML input file
     *
     * @param importType
     *            ImportType.IMPORT_ALL to import the table type, data type, and macro definitions,
     *            and the data from all the table definitions; ImportType.FIRST_DATA_ONLY to load
     *            only the data for the first table defined
     *
     * @param targetTypeDefn
     *            table type definition of the table in which to import the data; ignored if
     *            importing all tables
     *
     * @param ignoreErrors
     *            true to ignore all errors in the import file
     *
     * @param replaceExistingMacros
     *            not used for EDS import
     *
     * @param replaceExistingGroups
     *            not used for EDS import
     *
     * @throws CCDDException
     *             If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException
     *             If an import file I/O error occurs
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile,
                               ImportType importType,
                               TypeDefinition targetTypeDefn,
                               boolean ignoreErrors,
                               boolean replaceExistingMacros,
                               boolean replaceExistingGroups) throws CCDDException,
                                                              IOException,
                                                              Exception
    {
        try
        {
            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            // Get the data sheet reference
            dataSheet = (DataSheetType) jaxbElement.getValue();

            tableDefinitions = new ArrayList<TableDefinition>();
            structureTypeDefn = null;
            commandTypeDefn = null;

            MetadataType metadata = dataSheet.getDevice().getMetadata();

            // Check if the device metadata exists
            if (metadata != null && metadata.getMetadataValueSet() != null)
            {
                // Step through the metadata entries
                for (MetadataValueType value : metadata.getMetadataValueSet().getDateValueOrFloatValueOrIntegerValue())
                {
                    // Check if the entry is for a string type
                    if (value instanceof StringMetadataValueType)
                    {
                        // Note: the name field contains the input type name, but with invalid
                        // characters replaced with underscores; the short description has the
                        // input type with no character replacement, so it's used for comparison

                        // Check if the item's short description matches that for the telemetry
                        // header table name indicator
                        if (value.getShortDescription().equals(DefaultInputType.XML_TLM_HDR.getInputName()))
                        {
                            // Store the entry value as the telemetry header table name
                            tlmHeaderTable = ((StringMetadataValueType) value).getValue();
                        }
                        // Check if the item name matches that for the command header table name
                        // indicator
                        else if (value.getShortDescription().equals(DefaultInputType.XML_CMD_HDR.getInputName()))
                        {
                            // Store the entry value as the command header table name
                            cmdHeaderTable = ((StringMetadataValueType) value).getValue();
                        }
                        // Check if the item name matches that for the application ID variable name
                        // indicator
                        else if (value.getShortDescription().equals(DefaultInputType.XML_APP_ID.getInputName()))
                        {
                            // Store the entry value as the application ID variable name
                            applicationIDName = ((StringMetadataValueType) value).getValue();
                        }
                        // Check if the item name matches that for the command function code
                        // variable name indicator
                        else if (value.getShortDescription().equals(DefaultInputType.XML_FUNC_CODE.getInputName()))
                        {
                            // Store the entry value as the command function code variable name
                            cmdFuncCodeName = ((StringMetadataValueType) value).getValue();
                        }
                    }
                }
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
            if (structureTypeDefn != null || commandTypeDefn != null)
            {
                // Set the flag if importing into an existing table to indicate that only a command
                // header, which is converted to structure table, is allowed when processing
                // commands
                boolean onlyCmdToStruct = importType == ImportType.FIRST_DATA_ONLY
                                          && targetTypeDefn.isStructure();

                // Step through each name space
                for (NamespaceType namespace : dataSheet.getNamespace())
                {
                    // Recursively step through the EDS-formatted data and extract the telemetry
                    // and command information
                    unbuildSpaceSystems(namespace, importType, onlyCmdToStruct);

                    // Check if only the data from the first table of the target table type is to
                    // be read
                    if (importType == ImportType.FIRST_DATA_ONLY && !tableDefinitions.isEmpty())
                    {
                        // Stop reading table definitions
                        break;
                    }
                }

                // Check if a command header was the table imported, but not a command table (the
                // command header in converted into a structure so for this case the command table
                // type is no longer needed)
                if (isCmdToTlm
                    && ((importType == ImportType.IMPORT_ALL && !isCommand)
                        || (importType == ImportType.FIRST_DATA_ONLY
                            && targetTypeDefn.isStructure())))
                {
                    // Remove the command table type definition
                    tableTypeHandler.getTypeDefinitions().remove(commandTypeDefn);
                }
            }
        }
        catch (JAXBException je)
        {
            // Inform the user that the file cannot be parsed
            throw new CCDDException("Parsing error; cause '</b>" + je.getMessage() + "<b>'");
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }
    }

    /**********************************************************************************************
     * Scan the import file in order to determine if any structure or command tables exist. If so,
     * create the structure and/or command table type definition that's used to build the new
     * tables
     *
     * @param importFile
     *            reference to the user-specified XML input file
     *
     * @param importType
     *            ImportType.IMPORT_ALL to import the table type, data type, and macro definitions,
     *            and the data from all the table definitions; ImportType.FIRST_DATA_ONLY to load
     *            only the data for the first table defined
     *
     * @param targetTypeDefn
     *            table type definition of the table in which to import the data; ignored if
     *            importing all tables
     *
     * @throws CCDDException
     *             Included due to calls to addImportedTableTypeColumnDefinition(); since default
     *             column definitions are used this error can't occur
     *********************************************************************************************/
    private void createTableTypeDefinitions(FileEnvVar importFile,
                                            ImportType importType,
                                            TypeDefinition targetTypeDefn) throws CCDDException
    {
        isTelemetry = false;
        isCommand = false;
        maxNumArguments = 1;

        // Set the flags to indicate if the target is a structure or command table
        boolean targetIsStructure = importType == ImportType.IMPORT_ALL
                                                                        ? true
                                                                        : targetTypeDefn.isStructure();
        boolean targetIsCommand = importType == ImportType.IMPORT_ALL
                                                                      ? true
                                                                      : targetTypeDefn.isCommand();

        // Step through each name space
        for (NamespaceType namespace : dataSheet.getNamespace())
        {
            // Recursively step through the EDS-formatted data and extract the telemetry and
            // command information
            findMetaData(namespace, importType, targetIsStructure, targetIsCommand);
        }

        // Check if a structure table type needs to be defined
        if ((isTelemetry || isCmdToTlm) && targetIsStructure)
        {
            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                String typeName = "EDS Structure";
                int sequence = 2;

                // Create a table type definition for structure tables
                TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                            "EDS import structure table type");

                // Step through each default structure column
                for (Object[] columnDefn : DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE))
                {
                    // Add the column to the table type definition
                    addImportedTableTypeColumnDefinition(true,
                                                         tableTypeDefn,
                                                         CcddUtilities.convertObjectToString(columnDefn),
                                                         importFile.getAbsolutePath(),
                                                         inputTypeHandler,
                                                         parent);
                }

                // Add the structure table type definition
                tableTypeDefns.add(tableTypeDefn);

                // Continue to check while a table type with this name exists. This also adds the
                // tab for the new definition to the table type manager, if open
                while (tableTypeHandler.updateTableTypes(tableTypeDefns) != null)
                {
                    // Alter the name so that there isn't a duplicate
                    typeName = "EDS Structure " + sequence;
                    tableTypeDefns.get(0).setTypeName(typeName);
                    sequence++;
                }

                // Store the reference to the structure table type definition
                structureTypeDefn = tableTypeHandler.getTypeDefinition(typeName);
            }
            // Only a single table is to be imported
            else
            {
                structureTypeDefn = targetTypeDefn;
            }

            // Get structure table column indices
            variableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE));
            dataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT));
            arraySizeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX));
            bitLengthIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH));
            enumerationIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.ENUMERATION));
            minimumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MINIMUM));
            maximumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MAXIMUM));
            descriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));
            unitsIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(DefaultInputType.UNITS));

            // Get the number of columns defined in the structure table type
            numStructureColumns = structureTypeDefn.getColumnCountVisible();

            // Update the database functions that collect structure table members and
            // structure-defining column data
            dbControl.createStructureColumnFunctions();

            // Check if the number of rate columns changed due to the type update
            if (rateHandler.setRateInformation())
            {
                // Store the rate parameters in the project database
                dbTable.storeRateParameters(parent);
            }
        }

        // Check if a command table type needs to be defined
        if ((isCommand && targetIsCommand) || (isCmdToTlm && targetIsStructure))
        {
            // Check if all tables are to be imported or the target is a structure table
            if (importType == ImportType.IMPORT_ALL || targetIsStructure)
            {
                List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                String typeName = "EDS Command";
                int sequence = 2;

                // Create a table type definition for command tables
                TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                            "EDS import command table type");

                // Step through each default command column
                for (Object[] columnDefn : DefaultColumn.getDefaultColumnDefinitions(TYPE_COMMAND))
                {
                    // Add the column to the table type definition
                    addImportedTableTypeColumnDefinition(true,
                                                         tableTypeDefn,
                                                         CcddUtilities.convertObjectToString(columnDefn),
                                                         importFile.getAbsolutePath(),
                                                         inputTypeHandler,
                                                         parent);
                }

                // Get the current number of columns defined for the command table type. The new
                // columns are appended to the existing ones
                int columnIndex = tableTypeDefn.getColumns().size();

                // Step through each additional command argument column set
                for (int argIndex = 2; argIndex <= maxNumArguments; argIndex++)
                {
                    // Check if the argument count is within that for a normal command table
                    if (argIndex <= maxNumArguments)
                    {
                        // Step through each command argument column to add
                        for (Object[] cmdArgCol : CcddTableTypeHandler.commandArgumentColumns)
                        {
                            // Update the argument name with the argument index
                            String argName = cmdArgCol[0].toString().replaceFirst("###",
                                                                                  String.valueOf(argIndex));

                            // Add the command argument column. The argument description is updated
                            // with the argument index
                            addImportedTableTypeColumnDefinition(true,
                                                                 tableTypeDefn,
                                                                 new String[] {String.valueOf(columnIndex),
                                                                               argName,
                                                                               cmdArgCol[1].toString().replaceFirst("###",
                                                                                                                    String.valueOf(argIndex)),
                                                                               ((DefaultInputType) cmdArgCol[2]).getInputName(),
                                                                               Boolean.toString(false),
                                                                               Boolean.toString(false),
                                                                               Boolean.toString(false),
                                                                               Boolean.toString(false)},
                                                                 importFile.getAbsolutePath(),
                                                                 inputTypeHandler,
                                                                 parent);

                            columnIndex++;
                        }
                    }
                }

                // Add the command table type definition. This also adds the tab for the new
                // definition to the table type manager, if open
                tableTypeDefns.add(tableTypeDefn);

                // Continue to check while a table type with this name exists. This also adds the
                // tab for the new definition to the table type manager, if open
                while (tableTypeHandler.updateTableTypes(tableTypeDefns) != null)
                {
                    // Alter the name so that there isn't a duplicate
                    typeName = "EDS Command " + sequence;
                    tableTypeDefns.get(0).setTypeName(typeName);
                    sequence++;
                }

                // Store the reference to the command table type definition
                commandTypeDefn = tableTypeHandler.getTypeDefinition(typeName);
            }
            // A single command table is to be imported into an existing command table
            else
            {
                commandTypeDefn = targetTypeDefn;
            }

            // Get the list containing the associated column indices for each argument grouping
            commandArguments = commandTypeDefn.getAssociatedCommandArgumentColumns(true);

            // Get the command table column indices
            commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME));
            cmdFuncCodeIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE));
            cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));

            // Get the number of columns defined in the command table type
            numCommandColumns = commandTypeDefn.getColumnCountVisible();
        }
    }

    /**********************************************************************************************
     * Scan the import file in order to determine if any structure or command tables exist. If a
     * command table determine the maximum number of command arguments its commands require
     *
     * @param namespace
     *            name space
     *
     * @param importType
     *            import type: ImportType.ALL to import all information in the import file;
     *            ImportType.FIRST_DATA_ONLY to import data from the first table defined in the
     *            import file
     *
     * @param targetIsStructure
     *            true if the table type definition of the table in which to import the data
     *            represents a structure; ignored if importing all tables
     *
     * @param targetIsCommand
     *            true if the table type definition of the table in which to import the data
     *            represents a command; ignored if importing all tables
     *********************************************************************************************/
    private void findMetaData(NamespaceType namespace,
                              ImportType importType,
                              boolean targetIsStructure,
                              boolean targetIsCommand)
    {
        // Check if the name space has a declared interface set
        if (namespace.getDeclaredInterfaceSet() != null)
        {
            // Step through the interfaces in order to locate the name space's parameter and
            // command sets
            for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
            {
                // Check if the interface contains a parameter set
                if (intfcDecType.getParameterSet() != null
                    && !intfcDecType.getParameterSet().getParameter().isEmpty())
                {
                    isTelemetry = true;
                }

                // Check if the interface contains a command set
                if (intfcDecType.getCommandSet() != null
                    && !intfcDecType.getCommandSet().getCommand().isEmpty())
                {
                    // Check if the interface isn't flagged as abstract. This denotes a command
                    // header which is converted to a structure, and therefore necessitates
                    // creating a structure table type to contain it
                    if (intfcDecType.isAbstract())
                    {
                        isCmdToTlm = true;
                    }
                    // The command isn't flagged as abstract. This denotes a command table
                    else
                    {
                        isCommand = true;
                    }

                    // Step through each command
                    for (InterfaceCommandType cmdType : intfcDecType.getCommandSet().getCommand())
                    {
                        // The number of entries in the meta-command type is the number of command
                        // arguments required by this command. Store the largest number of command
                        // arguments required by all commands in the import file
                        maxNumArguments = Math.max(maxNumArguments, cmdType.getArgument().size());
                    }
                }

                // Check if the data from only the first table is to be read and one of the target
                // table type has been found
                if (importType == ImportType.FIRST_DATA_ONLY
                    && ((targetIsStructure && (isTelemetry || isCmdToTlm))
                        || (targetIsCommand && isCommand)))
                {
                    break;
                }
            }
        }
    }

    /**********************************************************************************************
     * Extract the telemetry and/or command information from the name space. This is a recursive
     * method
     *
     * @param namespace
     *            name space
     *
     * @param importType
     *            import type: ImportType.ALL to import all information in the import file;
     *            ImportType.FIRST_DATA_ONLY to import data from the first table defined in the
     *            import file
     *
     * @param onlyCmdToStruct
     *            true to only allow a command header, converted to a structure, to be stored;
     *            false to store (non-header) command tables
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void unbuildSpaceSystems(NamespaceType namespace,
                                     ImportType importType,
                                     boolean onlyCmdToStruct) throws CCDDException
    {
        // Check if the name space has a declared interface set
        if (namespace.getDeclaredInterfaceSet() != null)
        {
            boolean hasParameter = false;
            boolean hasCommand = false;
            String systemPath = "";

            // The full table name, with path, should be stored in the name space's short
            // description (the name space name doesn't allow the commas and periods used by the
            // table path so it has to go elsewhere; the export operation does this). If the short
            // description doesn't exist, or isn't in the correct format, then the table name is
            // extracted from the name space name; however, this creates a 'flat' table reference,
            // making it a prototype
            String tableName = namespace.getShortDescription() != null
                               && TableDefinition.isPathFormatValid(namespace.getShortDescription())
                                                                                                     ? namespace.getShortDescription()
                                                                                                     : namespace.getName();

            // Get the end of the system path
            int index = namespace.getName().lastIndexOf("/");

            // Check if the system path exists
            if (index != -1)
            {
                // Extract the system path and remove it from the table name
                systemPath = namespace.getName().substring(0, index);

                // Check if the table name contains the system path (this is the case if the table
                // name is extracted from the name space name and a system path is present)
                if (tableName.contains("/"))
                {
                    // Get the table name portion. Note that the name in this case can't have a
                    // path so the table is treated as a prototype
                    tableName = tableName.substring(0, index);
                }
            }

            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                // Step through the interfaces in order to locate the name space's parameter and
                // command sets. If the interface contains both a parameter and command set then
                // the resulting tables created must have their names adjusted to prevent having a
                // duplicate
                for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
                {
                    // Check if the interface contains a parameter set
                    if (intfcDecType.getParameterSet() != null
                        && !intfcDecType.getParameterSet().getParameter().isEmpty())
                    {
                        hasParameter = true;
                    }

                    // Check if the interface contains a command set
                    if (intfcDecType.getCommandSet() != null
                        && !intfcDecType.getCommandSet().getCommand().isEmpty())
                    {
                        hasCommand = true;
                    }
                }
            }

            // Step through the interfaces in order to locate the name space's parameter and
            // command sets
            for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
            {
                // Check if the interface contains a parameter set and a structure table type
                // definition exists to define it (the structure table type won't exists if
                // importing into a single command table). If the interface contains a parameter
                // set the assumption is made that this is a structure table
                if (intfcDecType.getParameterSet() != null
                    && !intfcDecType.getParameterSet().getParameter().isEmpty()
                    && structureTypeDefn != null)
                {
                    // Build the structure table from the telemetry data
                    importStructureTable(namespace, intfcDecType, tableName, systemPath, hasCommand);
                }

                // Check if the interface contains a command set; if so, the assumption is made
                // that this is a command table
                if (intfcDecType.getCommandSet() != null
                    && !intfcDecType.getCommandSet().getCommand().isEmpty())
                {
                    // Build the command table from the telemetry data
                    importCommandTable(namespace,
                                       intfcDecType,
                                       tableName,
                                       systemPath,
                                       hasParameter,
                                       onlyCmdToStruct);
                }

                // Check if the data from all tables is to be read or no table of the target type
                // has been located yet
                if (importType == ImportType.IMPORT_ALL || tableDefinitions.isEmpty())
                {
                    break;
                }
            }
        }
    }

    /**********************************************************************************************
     * Build a structure table from the specified telemetry metadata
     *
     * @param namespace
     *            name space
     *
     * @param intfcDecType
     *            reference to the interface declaration type from which to build the structure
     *            table
     *
     * @param tableName
     *            name table name, including the full system path
     *
     * @param systemPath
     *            system path
     *
     * @param hasCommand
     *            true if the name space also has a command set
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importStructureTable(NamespaceType namespace,
                                      InterfaceDeclarationType intfcDecType,
                                      String tableName,
                                      String systemPath,
                                      boolean hasCommand) throws CCDDException
    {
        List<DescriptionType> memberList = null;

        // Create a table definition for this structure table. If the name space also includes a
        // command set (which creates a command table) then ensure the two tables have different
        // names
        TableDefinition tableDefn = new TableDefinition(tableName
                                                        + (hasCommand
                                                                      ? "_tlm"
                                                                      : ""),
                                                        namespace.getLongDescription());

        // Check if a description exists for this structure table
        if (namespace.getLongDescription() != null && !namespace.getLongDescription().isEmpty())
        {
            // Store the table's description
            tableDefn.setDescription(namespace.getLongDescription());
        }

        // Set the new structure table's table type name
        tableDefn.setTypeName(structureTypeDefn.getName());

        // Extract the table's name, minus the path, from the name space name
        String typeName = namespace.getName();
        int index = typeName.lastIndexOf("/");

        if (index != -1)
        {
            typeName = typeName.substring(index + 1);
        }

        typeName += TYPE;

        // The name space for a structure should contain in its data type set a container with all
        // of the variables as members. Those variables with minimum and maximum values should have
        // a valid range set with those values. Step through the parameter type set to find the
        // container for this structure
        for (RootDataType parmType : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
        {
            // Check if this is the container for the structure's members
            if (parmType instanceof ContainerDataType && parmType.getName().equals(typeName))
            {
                // Check if the member list exists
                if (((ContainerDataType) parmType).getEntryList() != null
                    && !((ContainerDataType) parmType).getEntryList().getEntryOrFixedValueEntryOrPaddingEntry().isEmpty())
                {
                    // Set the reference to the container's member list
                    memberList = ((ContainerDataType) parmType).getEntryList().getEntryOrFixedValueEntryOrPaddingEntry();
                }

                // Stop searching since the matching container was found
                break;
            }
        }

        // Step through each telemetry parameter
        for (int parmIndex = 0; parmIndex < intfcDecType.getParameterSet().getParameter().size(); parmIndex++)
        {
            // Get the reference to the parameter in the parameter set
            InterfaceParameterType parm = intfcDecType.getParameterSet().getParameter().get(parmIndex);

            // Create a new row of data in the table definition to contain this structure's
            // information. Initialize all columns to blanks except for the variable name
            String[] newRow = new String[numStructureColumns];
            Arrays.fill(newRow, null);
            newRow[variableNameIndex] = parm.getName();

            // Check if the description column exists in the table type definition and that a
            // description exists
            if (descriptionIndex != -1 && parm.getLongDescription() != null)
            {
                // Store the description for this variable
                newRow[descriptionIndex] = parm.getLongDescription();
            }

            // Add the new row to the table definition
            tableDefn.addData(newRow);

            // Step through the parameter type set to find the data type entry where the name
            // matches the parameter type reference from the parameter set
            for (RootDataType parmType : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
            {
                // Check if the parameter set's parameter type reference matches the parameter type
                // set's name
                if (parm.getType().equals(parmType.getName()))
                {
                    String dataType = null;
                    String arraySize = null;
                    BigInteger bitLength = null;
                    long sizeInBits = 0;
                    String enumeration = null;
                    String minimum = null;
                    String maximum = null;
                    Unit units = null;

                    // Check if the parameter is an array data type
                    if (parmType instanceof ArrayDataType)
                    {
                        arraySize = "";

                        // Store the reference to the array parameter type
                        ArrayDataType arrayType = (ArrayDataType) parmType;

                        // Step through each dimension for the array variable
                        for (DimensionSizeType dim : ((ArrayDataType) parmType).getDimensionList().getDimension())
                        {
                            // Build the array size string
                            arraySize += String.valueOf(dim.getSize().longValue()) + ",";
                        }

                        arraySize = CcddUtilities.removeTrailer(arraySize, ",");
                        parmType = null;

                        // The array parameter type references a non-array parameter type that
                        // describes the individual array members. Step through each data type in
                        // the parameter type set in order to locate this data type entry
                        for (RootDataType type : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                        {
                            // Check if the array parameter's array type reference matches the data
                            // type name
                            if (arrayType.getDataTypeRef().equals(type.getName()))
                            {
                                // Store the reference to the array parameter's data type and stop
                                // searching
                                parmType = type;
                                break;
                            }
                        }
                    }

                    // Check if a data type entry for the parameter exists in the parameter type
                    // set (note that if the parameter is an array the steps above locate the data
                    // type entry for the individual array members)
                    if (parmType != null)
                    {
                        boolean isInteger = false;
                        boolean isUnsigned = false;
                        boolean isFloat = false;
                        boolean isString = false;

                        // Check if the parameter is an integer data type
                        if (parmType instanceof IntegerDataType)
                        {
                            // The 'sizeInBits' references are the integer size for non-bit-wise
                            // parameters, but equal the number of bits assigned to the parameter
                            // for a bit-wise parameter. It doens't appear that the size of the
                            // integer used to contain the parameter is stored. The assumption is
                            // made that the smallest integer required to store the bits is used.
                            // However, this can alter the originally intended bit-packing (e.g., a
                            // 3-bit and a 9-bit fit within a single 16-bit integer, but the code
                            // below assigns the first to an 8-bit integer and the second to a
                            // 16-bit integer)

                            IntegerDataType itlm = (IntegerDataType) parmType;

                            // Get the number of bits occupied by the parameter
                            bitLength = itlm.getIntegerDataEncoding().getSizeInBits();

                            // Check if units exist for this parameter
                            if (itlm.getSemantics() != null
                                && itlm.getSemantics().getUnit() != null)
                            {
                                // Get the parameter units reference
                                units = itlm.getSemantics().getUnit();
                            }

                            // Check if integer encoding is set to 'unsigned'
                            if (itlm.getIntegerDataEncoding().getEncoding() == IntegerEncodingType.UNSIGNED)
                            {
                                isUnsigned = true;
                            }

                            // Determine the smallest integer size that contains the number of bits
                            // occupied by the parameter
                            sizeInBits = 8;

                            while (bitLength.longValue() > sizeInBits)
                            {
                                sizeInBits *= 2;
                            }

                            // Check if the table's member list exists
                            if (memberList != null)
                            {
                                // Step through each member in the member list
                                for (DescriptionType entry : memberList)
                                {
                                    // Check if this is the entry for this parameter
                                    if (((EntryType) entry).getName().equals(parm.getName() + TYPE))
                                    {
                                        // Get the minimum and maximum values, if present
                                        DerivedTypeRangeType range = ((EntryType) entry).getValidRange();

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

                                        // Stop searching since the matching parameter was located
                                        break;
                                    }
                                }
                            }

                            isInteger = true;
                        }
                        // Check if the parameter is a floating point data type
                        else if (parmType instanceof FloatDataType)
                        {
                            // Get the float parameter attributes
                            FloatDataType ftlm = (FloatDataType) parmType;

                            switch (ftlm.getFloatDataEncoding().getEncodingAndPrecision())
                            {
                                case IEEE_754_2008_SINGLE:
                                    sizeInBits = 32;
                                    break;

                                case IEEE_754_2008_DOUBLE:
                                    sizeInBits = 64;
                                    break;

                                case IEEE_754_2008_QUAD:
                                    sizeInBits = 128;
                                    break;

                                default:
                                    break;
                            }

                            // Check if units exist for this parameter
                            if (ftlm.getSemantics() != null && ftlm.getSemantics().getUnit() != null)
                            {
                                // Get the parameter units reference
                                units = ftlm.getSemantics().getUnit();
                            }

                            // Check if the table's member list exists
                            if (memberList != null)
                            {
                                // Step through each member in the member list
                                for (DescriptionType entry : memberList)
                                {
                                    // Check if this is the entry for this parameter
                                    if (((EntryType) entry).getName().equals(parm.getName()))
                                    {
                                        // Get the minimum and maximum values, if present
                                        DerivedTypeRangeType range = ((EntryType) entry).getValidRange();

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

                                        // Stop searching since the matching parameter was located
                                        break;
                                    }
                                }
                            }

                            isFloat = true;
                        }
                        // Check if the parameter is a string data type
                        else if (parmType instanceof StringDataType)
                        {
                            // Get the string parameter attributes
                            StringDataType stlm = (StringDataType) parmType;
                            sizeInBits = stlm.getLength().longValue() * 8;

                            // Check if units exist for this parameter
                            if (stlm.getSemantics() != null && stlm.getSemantics().getUnit() != null)
                            {
                                // Get the parameter units reference
                                units = stlm.getSemantics().getUnit();
                            }

                            isString = true;
                        }
                        // Check if the parameter is an enumerated data type
                        else if (parmType instanceof EnumeratedDataType)
                        {
                            // Get the enumeration parameters
                            EnumeratedDataType etlm = (EnumeratedDataType) parmType;
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
                                    enumeration += enumType.getValue()
                                                   + " | "
                                                   + enumType.getLabel();
                                }

                                bitLength = etlm.getIntegerDataEncoding().getSizeInBits();

                                // Check if units exist for this parameter
                                if (etlm.getSemantics() != null && etlm.getSemantics().getUnit() != null)
                                {
                                    // Get the parameter units reference
                                    units = etlm.getSemantics().getUnit();
                                }

                                // Check if integer encoding is set to 'unsigned'
                                if (etlm.getIntegerDataEncoding().getEncoding() == IntegerEncodingType.UNSIGNED)
                                {
                                    isUnsigned = true;
                                }

                                // Determine the smallest integer size that contains the number of
                                // bits occupied by the parameter
                                sizeInBits = 8;

                                while (bitLength.longValue() > sizeInBits)
                                {
                                    sizeInBits *= 2;
                                }

                                isInteger = true;
                            }
                        }
                        // Check if the parameter is a container data type, i.e., a structure
                        // reference
                        else if (parmType instanceof ContainerDataType)
                        {
                            // The container name is the prototype table name (with _Type
                            // appended). Extract the prototype table name from the parameter type
                            dataType = parmType.getName();

                            // Check that the type name has the type indicator appended
                            if (dataType.matches(".+" + TYPE + "[0-9]*"))
                            {
                                // Remove the type indicator
                                dataType = dataType.substring(0,
                                                              dataType.length() - TYPE.length());
                            }

                            ConstraintSet constraintSet = ((ContainerDataType) parmType).getConstraintSet();

                            // Check if this is the constraint set for the telemetry header table
                            if (constraintSet != null
                                && constraintSet.getConstraint() != null
                                && dataType.equals(tlmHeaderTable))
                            {
                                // Step through each constraint
                                for (ContainerConstraintType constraint : constraintSet.getConstraint())
                                {
                                    ContainerValueConstraintType constraintValue = constraint.getValueConstraint();

                                    // Check if the application ID is present
                                    if (constraint.getEntry() != null
                                        && constraintValue != null
                                        && constraintValue.getValue() != null
                                        && constraint.getEntry().equals(applicationIDName))
                                    {
                                        // Create a data field for the application ID name. Once a
                                        // match is found the search is discontinued
                                        tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                                        applicationIDName,
                                                                                                        "Application Name & ID",
                                                                                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                                                        Math.min(Math.max(systemPath.length(),
                                                                                                                          5),
                                                                                                                 40),
                                                                                                        false,
                                                                                                        ApplicabilityType.ROOT_ONLY,
                                                                                                        constraintValue.getValue(),
                                                                                                        false));
                                        break;
                                    }
                                }
                            }
                        }

                        // Check if the data type is a primitive
                        if (dataType == null)
                        {
                            // Get the name of the data type from the data type table that matches
                            // the base type and size of the parameter
                            dataType = getMatchingDataType(sizeInBits / 8,
                                                           isInteger,
                                                           isUnsigned,
                                                           isFloat,
                                                           isString,
                                                           dataTypeHandler);
                        }

                        // Check if a data type exists
                        if (dataType != null)
                        {
                            // Store the data type
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + dataTypeIndex,
                                                    dataType);
                        }

                        // Check if a array size exists
                        if (arraySize != null)
                        {
                            // Store the array size
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + arraySizeIndex,
                                                    arraySize);
                        }

                        // Check if a bit length exists and it doesn't match the data type size
                        if (bitLength != null && bitLength.longValue() != sizeInBits)
                        {
                            // Store the bit length
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + bitLengthIndex,
                                                    bitLength.toString());
                        }

                        // Check if a description exists and the table has a description column
                        if (parm.getLongDescription() != null && descriptionIndex != -1)
                        {
                            // Store the description
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + descriptionIndex,
                                                    parm.getLongDescription());
                        }

                        // Check if a units exists and the table has a units column
                        if (units != null && !units.value().isEmpty() && unitsIndex != -1)
                        {
                            // Store the units for this variable
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + unitsIndex,
                                                    units.value());
                        }
                    }

                    // Check if an enumeration exists and the table has an enumeration column
                    if (enumeration != null && enumerationIndex != -1)
                    {
                        // Store the enumeration parameters. This accounts only for the first
                        // enumeration for a variable
                        tableDefn.getData().set(parmIndex
                                                * numStructureColumns
                                                + enumerationIndex,
                                                enumeration);
                    }

                    // Check if a minimum value exists and the table has a minimum column
                    if (minimum != null && minimumIndex != -1)
                    {
                        // Store the minimum value
                        tableDefn.getData().set(parmIndex
                                                * numStructureColumns
                                                + minimumIndex,
                                                minimum);
                    }

                    // Check if a maximum value exists and the table has a maximum column
                    if (maximum != null && maximumIndex != -1)
                    {
                        // Store the maximum value
                        tableDefn.getData().set(parmIndex
                                                * numStructureColumns
                                                + maximumIndex,
                                                maximum);
                    }

                    break;
                }
            }
        }

        // Check if the structure table definition contains any variable definitions
        if (!tableDefn.getData().isEmpty())
        {
            // Create a data field for the system path
            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                            "System path",
                                                                            "System Path",
                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SYSTEM_PATH),
                                                                            Math.min(Math.max(systemPath.length(),
                                                                                              5),
                                                                                     40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            systemPath,
                                                                            false));

            // Add the structure table definition to the list
            tableDefinitions.add(tableDefn);
        }
    }

    /**********************************************************************************************
     * Build a command table from the specified command metadata
     *
     * @param namespace
     *            name space
     *
     * @param intfcDecType
     *            reference to the interface declaration type from which to build the command table
     *
     * @param tableName
     *            name table name, including the full system path
     *
     * @param systemPath
     *            system path
     *
     * @param hasParameter
     *            true if the name space also has a parameter set
     *
     * @param onlyCmdToStruct
     *            true to only allow a command header, converted to a structure, to be stored;
     *            false to store (non-header) command tables
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importCommandTable(NamespaceType namespace,
                                    InterfaceDeclarationType intfcDecType,
                                    String tableName,
                                    String systemPath,
                                    boolean hasParameter,
                                    boolean onlyCmdToStruct) throws CCDDException
    {
        int abstractCount = 0;

        // Create a table definition for this command table. If the name space also includes a
        // parameter set (which creates a structure table) then ensure the two tables have
        // different names
        TableDefinition tableDefn = new TableDefinition(tableName
                                                        + (hasParameter
                                                                        ? "_cmd"
                                                                        : ""),
                                                        namespace.getLongDescription());

        // Check if a description exists for this command table
        if (namespace.getLongDescription() != null && !namespace.getLongDescription().isEmpty())
        {
            // Store the table's description
            tableDefn.setDescription(namespace.getLongDescription());
        }

        // Set the new command table's table type name
        tableDefn.setTypeName(commandTypeDefn.getName());

        // Check if the description column belongs to a command argument
        if (commandArguments.size() != 0
            && cmdDescriptionIndex > commandArguments.get(0).getName())
        {
            // Reset the command description index to indicate no description exists
            cmdDescriptionIndex = -1;
        }

        // Step through each command
        for (InterfaceCommandType cmdType : intfcDecType.getCommandSet().getCommand())
        {
            // Create a new row of data in the table definition to contain this command's
            // information. Initialize all columns to blanks except for the command name
            String[] rowData = new String[numCommandColumns];
            Arrays.fill(rowData, null);
            rowData[commandNameIndex] = cmdType.getName();

            // Check if the command description is present and the description column exists in the
            // table type definition
            if (cmdType.getLongDescription() != null && cmdDescriptionIndex != -1)
            {
                // Store the command description in the row's description column
                rowData[cmdDescriptionIndex] = cmdType.getLongDescription();
            }

            // Check if the command name space has a data type set
            if (namespace.getDataTypeSet() != null)
            {
                // Step through each data type set
                for (RootDataType argType : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                {
                    // Check if this is a container data type
                    if (argType instanceof ContainerDataType)
                    {
                        ContainerDataType ccmd = (ContainerDataType) argType;

                        // Check if the container is for the current command and the container's
                        // base type matches the command header table name
                        if (ccmd.getName().equals(cmdType.getName())
                            && ccmd.getBaseType() != null
                            && ccmd.getBaseType().equals(cmdHeaderTable)
                            && ccmd.getConstraintSet() != null)
                        {
                            // Step through each container constraint
                            for (ContainerConstraintType constraint : ccmd.getConstraintSet().getConstraint())
                            {
                                // Check if the entry and constraint value exists
                                if (constraint.getEntry() != null
                                    && constraint.getValueConstraint() != null
                                    && constraint.getValueConstraint().getValue() != null)
                                {
                                    // Check if the argument name matches the application ID
                                    // variable name
                                    if (constraint.getEntry().equals(applicationIDName))
                                    {
                                        boolean isExists = false;

                                        // Step through the data fields already added to this table
                                        for (String[] fieldInfo : tableDefn.getDataFields())
                                        {
                                            // Check if a data field with the name matching the
                                            // application ID variable name already exists. This is
                                            // the case if the command table has multiple commands;
                                            // the first one causes the application ID field to be
                                            // created, so the subsequent ones are ignored to
                                            // prevent duplicates
                                            if (fieldInfo[FieldsColumn.FIELD_NAME.ordinal()].equals(applicationIDName))
                                            {
                                                // Set the flag indicating the field already exists
                                                // and stop searching
                                                isExists = true;
                                                break;
                                            }
                                        }

                                        // Check if the application ID data field doesn't exist
                                        if (!isExists)
                                        {
                                            // Create a data field for the table containing the
                                            // application ID and stop searching
                                            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                                            applicationIDName,
                                                                                                            "Application name and ID",
                                                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                                                            Math.min(Math.max(constraint.getValueConstraint().getValue().length(),
                                                                                                                              5),
                                                                                                                     40),
                                                                                                            false,
                                                                                                            ApplicabilityType.ALL,
                                                                                                            constraint.getValueConstraint().getValue(),
                                                                                                            false));
                                        }
                                    }
                                    // Check if the argument name matches the command function code
                                    // variable name
                                    else if (constraint.getEntry().equals(cmdFuncCodeName))
                                    {
                                        // Store the command function code
                                        rowData[cmdFuncCodeIndex] = constraint.getValueConstraint().getValue();
                                    }
                                }
                            }
                        }
                    }
                }
            }

            int cmdArgIndex = 0;

            // Step through each of the command's arguments
            for (CommandArgumentType argList : cmdType.getArgument())
            {
                // Check if a data set exists and the command name column is present in the data
                // type
                if (namespace.getDataTypeSet() != null && commandNameIndex != -1)
                {
                    // Step through each data type set
                    for (RootDataType argType : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                    {
                        // Check if this is the same command argument referenced in the argument
                        // list (by matching the command and argument names between the two)
                        if (argList.getType().equals(argType.getName()))
                        {
                            boolean isInteger = false;
                            boolean isUnsigned = false;
                            boolean isFloat = false;
                            boolean isString = false;

                            String dataType = null;
                            String arraySize = null;
                            BigInteger bitLength = null;
                            long sizeInBits = 0;
                            String enumeration = null;
                            String description = null;
                            Unit units = null;
                            String minimum = null;
                            String maximum = null;

                            // Check if the argument is an array data type
                            if (argType instanceof ArrayDataType)
                            {
                                arraySize = "";

                                // Store the reference to the array parameter type
                                ArrayDataType arrayType = (ArrayDataType) argType;
                                argType = null;

                                // Step through each dimension for the array variable
                                for (DimensionSizeType dim : arrayType.getDimensionList().getDimension())
                                {
                                    // Build the array size string
                                    arraySize += String.valueOf(dim.getSize().longValue()) + ",";
                                }

                                arraySize = CcddUtilities.removeTrailer(arraySize, ",");

                                // The array parameter type references a non-array parameter type
                                // that describes the individual array members. Step through each
                                // data type in the parameter type set in order to locate this data
                                // type entry Step through each data type set
                                for (RootDataType type : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                                {
                                    // Check if the array parameter's array type reference matches
                                    // the data type name
                                    if (arrayType.getDataTypeRef().equals(type.getName()))
                                    {
                                        // Store the reference to the array parameter's data type
                                        // and stop searching
                                        argType = type;
                                        break;
                                    }
                                }
                            }

                            // Check if a data type entry for the parameter exists in the parameter
                            // type set (note that if the parameter is an array the steps above
                            // locate the data type entry for the individual array members)
                            if (argType != null)
                            {
                                // Check if the argument is an integer data type
                                if (argType instanceof IntegerDataType)
                                {
                                    IntegerDataType icmd = (IntegerDataType) argType;

                                    // Get the number of bits occupied by the argument
                                    bitLength = icmd.getIntegerDataEncoding().getSizeInBits();

                                    // Check if units exist for this argument
                                    if (icmd.getSemantics() != null
                                        && icmd.getSemantics().getUnit() != null)
                                    {
                                        // Get the argument units reference
                                        units = icmd.getSemantics().getUnit();
                                    }

                                    // Check if integer encoding is set to 'unsigned'
                                    if (icmd.getIntegerDataEncoding().getEncoding() == IntegerEncodingType.UNSIGNED)
                                    {
                                        isUnsigned = true;
                                    }

                                    // Determine the smallest integer size that contains the number
                                    // of bits occupied by the argument
                                    sizeInBits = 8;

                                    while (bitLength.longValue() > sizeInBits)
                                    {
                                        sizeInBits *= 2;
                                    }

                                    // Get the argument range
                                    IntegerDataTypeRangeType range = icmd.getRange();

                                    // Check if the argument has a range
                                    if (range != null && range.getMinMaxRange() != null)
                                    {
                                        MinMaxRangeType minMax = range.getMinMaxRange();

                                        // Check if the argument has a minimum value
                                        if (minMax.getMin() != null)
                                        {
                                            // Store the minimum value
                                            minimum = minMax.getMin().toString();
                                        }

                                        // Check if the argument has a maximum value
                                        if (minMax.getMax() != null)
                                        {
                                            // Store the maximum value
                                            maximum = minMax.getMax().toString();
                                        }
                                    }

                                    isInteger = true;
                                }
                                // Check if the argument is a floating point data type
                                else if (argType instanceof FloatDataType)
                                {
                                    // Get the float argument attributes
                                    FloatDataType fcmd = (FloatDataType) argType;

                                    // Check if units exist for this argument
                                    if (fcmd.getSemantics() != null
                                        && fcmd.getSemantics().getUnit() != null)
                                    {
                                        // Get the argument units reference
                                        units = fcmd.getSemantics().getUnit();
                                    }

                                    switch (fcmd.getFloatDataEncoding().getEncodingAndPrecision())
                                    {
                                        case IEEE_754_2008_SINGLE:
                                            sizeInBits = 32;
                                            break;

                                        case IEEE_754_2008_DOUBLE:
                                            sizeInBits = 64;
                                            break;

                                        case IEEE_754_2008_QUAD:
                                            sizeInBits = 128;
                                            break;

                                        default:
                                            break;
                                    }

                                    // Get the argument range
                                    FloatDataTypeRangeType range = fcmd.getRange();

                                    // Check if the argument has a range
                                    if (range != null && range.getMinMaxRange() != null)
                                    {
                                        MinMaxRangeType minMax = range.getMinMaxRange();

                                        // Check if the argument has a minimum value
                                        if (minMax.getMin() != null)
                                        {
                                            // Store the minimum value
                                            minimum = minMax.getMin().toString();
                                        }

                                        // Check if the argument has a maximum value
                                        if (minMax.getMax() != null)
                                        {
                                            // Store the maximum value
                                            maximum = minMax.getMax().toString();
                                        }
                                    }

                                    isFloat = true;
                                }
                                // Check if the argument is a string data type
                                else if (argType instanceof StringDataType)
                                {
                                    // Get the string argument attributes
                                    StringDataType scmd = (StringDataType) argType;
                                    sizeInBits = scmd.getLength().longValue() * 8;

                                    // Check if units exist for this argument
                                    if (scmd.getSemantics() != null
                                        && scmd.getSemantics().getUnit() != null)
                                    {
                                        // Get the argument units reference
                                        units = scmd.getSemantics().getUnit();
                                    }
                                    isString = true;
                                }
                                // Check if the argument is an enumerated data type
                                else if (argType instanceof EnumeratedDataType)
                                {
                                    EnumeratedDataType ecmd = (EnumeratedDataType) argType;
                                    EnumerationListType enumList = ecmd.getEnumerationList();

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
                                            enumeration += enumType.getValue()
                                                           + " | "
                                                           + enumType.getLabel();
                                        }

                                        bitLength = ecmd.getIntegerDataEncoding().getSizeInBits();

                                        // Check if units exist for this argument
                                        if (ecmd.getSemantics() != null
                                            && ecmd.getSemantics().getUnit() != null)
                                        {
                                            // Get the argument units reference
                                            units = ecmd.getSemantics().getUnit();
                                        }

                                        // Check if integer encoding is set to 'unsigned'
                                        if (ecmd.getIntegerDataEncoding().getEncoding() == IntegerEncodingType.UNSIGNED)
                                        {
                                            isUnsigned = true;
                                        }

                                        // Determine the smallest integer size that contains the
                                        // number of bits occupied by the parameter
                                        sizeInBits = 8;

                                        while (bitLength.longValue() > sizeInBits)
                                        {
                                            sizeInBits *= 2;
                                        }

                                        isInteger = true;
                                    }
                                }

                                // Get the name of the data type from the data type table that
                                // matches the base type and size of the parameter
                                dataType = getMatchingDataType(sizeInBits / 8,
                                                               isInteger,
                                                               isUnsigned,
                                                               isFloat,
                                                               isString,
                                                               dataTypeHandler);

                                // Check if the description exists
                                if (argList.getLongDescription() != null)
                                {
                                    // Store the description
                                    description = argList.getLongDescription();
                                }

                                // Check if the command argument index is within the ranges
                                // dictated by the table type definition
                                if (cmdArgIndex < commandArguments.size())
                                {
                                    // Get the command argument reference
                                    AssociatedColumns acmdArg = commandArguments.get(cmdArgIndex);

                                    // Check if the command argument name is present
                                    if (acmdArg.getName() != -1)
                                    {
                                        // Store the command argument name
                                        rowData[acmdArg.getName()] = argList.getName();
                                    }

                                    // Check if the command argument data type is present
                                    if (acmdArg.getDataType() != -1 && dataType != null)
                                    {
                                        // Store the command argument data type
                                        rowData[acmdArg.getDataType()] = dataType;
                                    }

                                    // Check if the command argument array size is present
                                    if (acmdArg.getArraySize() != -1 && arraySize != null)
                                    {
                                        // Store the command argument array size
                                        rowData[acmdArg.getArraySize()] = arraySize;
                                    }

                                    // Check if the command argument bit length is present and it
                                    // doesn't match the data type size
                                    if (acmdArg.getBitLength() != -1
                                        && bitLength != null
                                        && bitLength.longValue() != sizeInBits)
                                    {
                                        // Store the command argument bit length
                                        rowData[acmdArg.getBitLength()] = bitLength.toString();
                                    }

                                    // Check if the command argument enumeration is present
                                    if (acmdArg.getEnumeration() != -1 && enumeration != null)
                                    {
                                        // Store the command argument enumeration
                                        rowData[acmdArg.getEnumeration()] = enumeration;
                                    }

                                    // Check if the command argument description is present
                                    if (acmdArg.getDescription() != -1 && description != null)
                                    {
                                        // Store the command argument description
                                        rowData[acmdArg.getDescription()] = description;
                                    }

                                    // Check if the command argument units is present
                                    if (acmdArg.getUnits() != -1 && units != null)
                                    {
                                        // Store the command argument units
                                        rowData[acmdArg.getUnits()] = units.toString();
                                    }

                                    // Check if the command argument minimum is present
                                    if (acmdArg.getMinimum() != -1 && minimum != null)
                                    {
                                        // Store the command argument minimum
                                        rowData[acmdArg.getMinimum()] = minimum;
                                    }

                                    // Check if the command argument maximum is present
                                    if (acmdArg.getMaximum() != -1 && maximum != null)
                                    {
                                        // Store the command argument maximum
                                        rowData[acmdArg.getMaximum()] = maximum;
                                    }
                                }
                            }

                            // Increment the argument index
                            cmdArgIndex++;
                            break;
                        }
                    }
                }
            }

            // Check if this isn't a command header type
            if (!intfcDecType.isAbstract())
            {
                // Check if (non-header) command tables are to be stored
                if (!onlyCmdToStruct)
                {
                    // Add the new row to the table definition
                    tableDefn.addData(rowData);
                }
            }
            // The command is a header type. Convert it to a structure unless importing only a
            // single command table
            else if (structureTypeDefn != null)
            {
                // Create a structure table definition to contain this command header
                TableDefinition structTableDefn = new TableDefinition(tableName
                                                                      + (abstractCount == 0
                                                                                            ? ""
                                                                                            : "_" + abstractCount),
                                                                      namespace.getLongDescription());
                abstractCount++;
                structTableDefn.setTypeName(structureTypeDefn.getName());

                // Step through each command argument in the command header
                for (AssociatedColumns cmdArg : commandArguments)
                {
                    // Create an empty row to store the variable definitions extracted from
                    // the command argument
                    String[] structRowData = new String[numStructureColumns];
                    Arrays.fill(structRowData, null);

                    // Check if the name exists
                    if (cmdArg.getName() != -1 && variableNameIndex != -1)
                    {
                        // Store the command argument name as the variable name
                        structRowData[variableNameIndex] = rowData[cmdArg.getName()];
                    }

                    // Check if the data type exists
                    if (cmdArg.getDataType() != -1 && dataTypeIndex != -1)
                    {
                        // Store the command argument data type as the variable data type
                        structRowData[dataTypeIndex] = rowData[cmdArg.getDataType()];
                    }

                    // Check if the array size exists
                    if (cmdArg.getArraySize() != -1 && arraySizeIndex != -1)
                    {
                        // Store the command argument array size as the variable array size
                        structRowData[arraySizeIndex] = rowData[cmdArg.getArraySize()];
                    }

                    // Check if the bit length exists
                    if (cmdArg.getBitLength() != -1 && bitLengthIndex != -1)
                    {
                        // Store the command argument bit length as the variable bit length
                        structRowData[bitLengthIndex] = rowData[cmdArg.getBitLength()];
                    }

                    // Check if the enumeration exists
                    if (cmdArg.getEnumeration() != -1 && enumerationIndex != -1)
                    {
                        // Store the command argument enumeration as the variable
                        // enumeration
                        structRowData[enumerationIndex] = rowData[cmdArg.getEnumeration()];
                    }

                    // Check if the minimum exists
                    if (cmdArg.getMinimum() != -1 && minimumIndex != -1)
                    {
                        // Store the command argument minimum value as the variable minimum
                        // value
                        structRowData[minimumIndex] = rowData[cmdArg.getMinimum()];
                    }

                    // Check if the maximum value exists
                    if (cmdArg.getMaximum() != -1 && maximumIndex != -1)
                    {
                        // Store the command argument maximum value as the variable maximum
                        // value
                        structRowData[maximumIndex] = rowData[cmdArg.getMaximum()];
                    }

                    // Check if the description exists
                    if (cmdArg.getDescription() != -1 && descriptionIndex != -1)
                    {
                        // Store the command argument description as the variable
                        // description
                        structRowData[descriptionIndex] = rowData[cmdArg.getDescription()];
                    }

                    // Check if the units exists
                    if (cmdArg.getUnits() != -1 && unitsIndex != -1)
                    {
                        // Store the command argument units as the variable units
                        structRowData[unitsIndex] = rowData[cmdArg.getUnits()];
                    }

                    // Store the variable definition in the structure definition
                    structTableDefn.addData(structRowData);
                }

                // Create a data field for the system path
                structTableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                      "System path",
                                                                                      "System Path",
                                                                                      inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SYSTEM_PATH),
                                                                                      Math.min(Math.max(systemPath.length(),
                                                                                                        5),
                                                                                               40),
                                                                                      false,
                                                                                      ApplicabilityType.ALL,
                                                                                      systemPath,
                                                                                      false));

                // Add the command header structure table definition to the list
                tableDefinitions.add(structTableDefn);
            }
        }

        // Check if the command table definition contains any commands. If the entire table was
        // converted to a structure then there won't be any data rows, in which case the command
        // table doesn't get generated
        if (!tableDefn.getData().isEmpty())
        {
            // Create a data field for the system path
            tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                            "System path",
                                                                            "System Path",
                                                                            inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.SYSTEM_PATH),
                                                                            Math.min(Math.max(systemPath.length(),
                                                                                              5),
                                                                                     40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            systemPath,
                                                                            false));

            // Add the command table definition to the list
            tableDefinitions.add(tableDefn);
        }
    }

    /**********************************************************************************************
     * Export the project in EDS XML format to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to convert
     *
     * @param includeBuildInformation
     *            true to include the CCDD version, project, host, and user information
     *
     * @param replaceMacros
     *            * Not used for EDS export (all macros are expanded) * true to replace any
     *            embedded macros with their corresponding values
     *
     * @param includeAllTableTypes
     *            * Not used for EDS export * true to include the all table type definitions in the
     *            export file
     *
     * @param includeAllDataTypes
     *            * Not used for EDS export * true to include the all data type definitions in the
     *            export file
     *
     * @param includeAllInputTypes
     *            * Not used for XTCE export * true to include the all user-defined input type
     *            definitions in the export file
     *
     * @param includeAllMacros
     *            * Not used for EDS export * true to include the all macro definitions in the
     *            export file
     *
     * @param includeReservedMsgIDs
     *            * Not used for EDS export * true to include the contents of the reserved message
     *            ID table in the export file
     *
     * @param includeProjectFields
     *            * Not used for EDS export * true to include the project-level data field
     *            definitions in the export file
     *
     * @param includeGroups
     *            * Not used for EDS export * true to include the groups and group data field
     *            definitions in the export file
     *
     * @param includeAssociations
     *            * Not used for EDS export * true to include the script associations in the export
     *            file
     *
     * @param includeVariablePaths
     *            * Not used for EDS export * true to include the variable path for each variable
     *            in a structure table, both in application format and using the user-defined
     *            separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            * Not used for EDS export * string array containing the variable path separator
     *            character(s), show/hide data types flag ('true' or 'false'), and data
     *            type/variable name separator character(s); null if includeVariablePaths is false
     *
     * @param extraInfo
     *            [0] endianess (EndianType.BIG_ENDIAN or EndianType.LITTLE_ENDIAN) <br>
     *            [1] are the telemetry and command headers big endian (true or false)
     *
     * @throws JAXBException
     *             If an error occurs marshaling the project
     *
     * @throws Exception
     *             If an unanticipated error occurs
     *********************************************************************************************/
    @Override
    public void exportToFile(FileEnvVar exportFile,
                             String[] tableNames,
                             boolean includeBuildInformation,
                             boolean replaceMacros,
                             boolean includeAllTableTypes,
                             boolean includeAllDataTypes,
                             boolean includeAllInputTypes,
                             boolean includeAllMacros,
                             boolean includeReservedMsgIDs,
                             boolean includeProjectFields,
                             boolean includeGroups,
                             boolean includeAssociations,
                             boolean includeVariablePaths,
                             CcddVariableHandler variableHandler,
                             String[] separators,
                             Object... extraInfo) throws JAXBException, MarshalException, Exception
    {
        // Convert the table data into EDS format
        convertTablesToEDS(tableNames,
                           includeBuildInformation,
                           (EndianType) extraInfo[0],
                           (boolean) extraInfo[1]);

        // Output the XML to the specified file. The Marshaller has a hard-coded limit of 8
        // levels; once exceeded it starts back at the first column. Therefore, a Transformer
        // is used to set the indentation amount (it doesn't have an indentation level limit)
        DOMResult domResult = new DOMResult();
        marshaller.marshal(project, domResult);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
        transformer.transform(new DOMSource(domResult.getNode()), new StreamResult(exportFile));
    }

    /**********************************************************************************************
     * Convert the project database contents to EDS XML format
     *
     * @param tableNames
     *            array of table names to convert to EDS format
     *
     * @param includeBuildInformation
     *            true to include the CCDD version, project, host, and user information
     *
     * @param endianess
     *            EndianType.BIG_ENDIAN for big endian, EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian
     *            true if the telemetry and command headers are always big endian (e.g., as with
     *            CCSDS)
     *********************************************************************************************/
    private void convertTablesToEDS(String[] tableNames,
                                    boolean includeBuildInformation,
                                    EndianType endianess,
                                    boolean isHeaderBigEndian)
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
                                      + "\n\nAuthor: " + dbControl.getUser()
                                      + "\nCCDD Version: " + ccddMain.getCCDDVersionInformation()
                                      + "\nDate: " + new Date().toString()
                                      + "\nProject: " + dbControl.getProjectName()
                                      + "\nHost: " + dbControl.getServer()
                                      + "\nEndianess: " + (endianess == EndianType.BIG_ENDIAN
                                                                                              ? "big"
                                                                                              : "little"));
        }

        dataSheet.setDevice(device);

        // Add the project's name spaces, parameters, and commands
        buildNamespaces(tableNames);
    }

    /**********************************************************************************************
     * Build the name spaces for the list of tables specified
     *
     * @param tableNames
     *            array of table names
     *********************************************************************************************/
    private void buildNamespaces(String[] tableNames)
    {
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
        MetadataValueSetType dataValue = factory.createMetadataValueSetType();

        // Check if the telemetry header table name is defined
        if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
        {
            // Store the telemetry header table name
            StringMetadataValueType tlmHdrTblValue = factory.createStringMetadataValueType();
            tlmHdrTblValue.setName(cleanSystemPath(DefaultInputType.XML_TLM_HDR.getInputName()));
            tlmHdrTblValue.setShortDescription(DefaultInputType.XML_TLM_HDR.getInputName());
            tlmHdrTblValue.setValue(tlmHeaderTable);
            dataValue.getDateValueOrFloatValueOrIntegerValue().add(tlmHdrTblValue);
        }

        // Check if the command header table name is defined
        if (cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Store the command header table name
            StringMetadataValueType cmdHdrTblValue = factory.createStringMetadataValueType();
            cmdHdrTblValue.setName(cleanSystemPath(DefaultInputType.XML_CMD_HDR.getInputName()));
            cmdHdrTblValue.setShortDescription(DefaultInputType.XML_CMD_HDR.getInputName());
            cmdHdrTblValue.setValue(cmdHeaderTable);
            dataValue.getDateValueOrFloatValueOrIntegerValue().add(cmdHdrTblValue);
        }

        // Store the application ID variable name
        StringMetadataValueType appIDNameValue = factory.createStringMetadataValueType();
        appIDNameValue.setName(cleanSystemPath(DefaultInputType.XML_APP_ID.getInputName()));
        appIDNameValue.setShortDescription(DefaultInputType.XML_APP_ID.getInputName());
        appIDNameValue.setValue(applicationIDName);
        dataValue.getDateValueOrFloatValueOrIntegerValue().add(appIDNameValue);

        // Store the command function code variable name
        StringMetadataValueType cmdCodeNameValue = factory.createStringMetadataValueType();
        cmdCodeNameValue.setName(cleanSystemPath(DefaultInputType.XML_FUNC_CODE.getInputName()));
        cmdCodeNameValue.setShortDescription(DefaultInputType.XML_FUNC_CODE.getInputName());
        cmdCodeNameValue.setValue(cmdFuncCodeName);
        dataValue.getDateValueOrFloatValueOrIntegerValue().add(cmdCodeNameValue);

        data.setMetadataValueSet(dataValue);
        device.setMetadata(data);

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the information from the database for the specified table
            TableInformation tableInfo = dbTable.loadTableData(tableName,
                                                               true,
                                                               false,
                                                               parent);

            // Check if the table's data successfully loaded
            if (!tableInfo.isErrorFlag())
            {
                // Get the table type and from the type get the type definition. The type
                // definition can be a global parameter since if the table represents a structure,
                // then all of its children are also structures, and if the table represents
                // commands or other table type then it is processed within this nest level
                typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                // Check if the table type represents a structure or command
                if (typeDefn != null && (typeDefn.isStructure() || typeDefn.isCommand()))
                {
                    // Replace all macro names with their corresponding values
                    tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));

                    // Get the application ID data field value, if present
                    String applicationID = CcddMessageIDHandler.getMessageID(fieldHandler.getFieldValue(tableName,
                                                                                                        DefaultInputType.MESSAGE_NAME_AND_ID));

                    // Get the name of the system to which this table belongs from the table's
                    // system path data field (if present)
                    String systemPath = cleanSystemPath(fieldHandler.getFieldValue(tableName,
                                                                                   DefaultInputType.SYSTEM_PATH));

                    // Add the name space
                    NamespaceType namespace = addNamespace(systemPath,
                                                           macroHandler.getMacroExpansion(tableName),
                                                           tableInfo.getDescription());

                    // Check if this is a structure table
                    if (typeDefn.isStructure())
                    {
                        // Get the default column indices
                        int varColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE);
                        int typeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT);
                        int sizeColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX);
                        int bitColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH);
                        int enumColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.ENUMERATION);
                        int descColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);
                        int unitsColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS);
                        int minColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MINIMUM);
                        int maxColumn = typeDefn.getColumnIndexByInputTypeFormat(InputTypeFormat.MAXIMUM);

                        // Check if this is the command header structure. In order for it to be
                        // referenced as the header for the command tables it must be converted
                        // into the same format as a command table, then rendered into EDS XML
                        if (tableName.equals(cmdHeaderTable))
                        {
                            // Store the command header's path
                            cmdHeaderPath = systemPath;

                            // Set the number of argument columns per command argument
                            int columnsPerArg = CcddTableTypeHandler.commandArgumentColumns.length;

                            // Initialize the offset in the command row so that space if created
                            // for the command name and description, then created an array to
                            // contain the converted command table data
                            int argOffset = 2;
                            String[][] tableData = new String[1][tableInfo.getData().length * columnsPerArg + 2];

                            // Initialize the storage for the command argument column indices
                            commandArguments = new ArrayList<AssociatedColumns>();

                            // Store the command header table name and description as the command
                            // name and description
                            tableData[0][0] = tableName;
                            tableData[0][1] = tableInfo.getDescription();

                            // Step through each row in the command header table
                            for (String[] rowData : CcddUtilities.convertObjectToString(tableInfo.getData()))
                            {
                                // Store the components of each variable within the command header
                                // in the form of a command argument
                                tableData[0][argOffset] = varColumn != -1
                                                                          ? rowData[varColumn]
                                                                          : null;
                                tableData[0][argOffset + 1] = typeColumn != -1
                                                                               ? rowData[typeColumn]
                                                                               : null;
                                tableData[0][argOffset + 2] = sizeColumn != -1
                                                                               ? rowData[sizeColumn]
                                                                               : null;
                                tableData[0][argOffset + 3] = bitColumn != -1
                                                                              ? rowData[bitColumn]
                                                                              : null;
                                tableData[0][argOffset + 4] = enumColumn != -1
                                                                               ? rowData[enumColumn]
                                                                               : null;
                                tableData[0][argOffset + 5] = minColumn != -1
                                                                              ? rowData[minColumn]
                                                                              : null;
                                tableData[0][argOffset + 6] = maxColumn != -1
                                                                              ? rowData[maxColumn]
                                                                              : null;
                                tableData[0][argOffset + 7] = descColumn != -1
                                                                               ? rowData[descColumn]
                                                                               : null;
                                tableData[0][argOffset + 8] = unitsColumn != -1
                                                                                ? rowData[unitsColumn]
                                                                                : null;

                                // Store the column indices for each of the command header
                                // arguments
                                commandArguments.add(new AssociatedColumns(false,
                                                                           (varColumn != -1
                                                                                            ? argOffset
                                                                                            : -1),
                                                                           (typeColumn != -1
                                                                                             ? argOffset + 1
                                                                                             : -1),
                                                                           (sizeColumn != -1
                                                                                             ? argOffset + 2
                                                                                             : -1),
                                                                           (bitColumn != -1
                                                                                            ? argOffset + 3
                                                                                            : -1),
                                                                           (enumColumn != -1
                                                                                             ? argOffset + 4
                                                                                             : -1),
                                                                           (minColumn != -1
                                                                                            ? argOffset + 5
                                                                                            : -1),
                                                                           (maxColumn != -1
                                                                                            ? argOffset + 6
                                                                                            : -1),
                                                                           (descColumn != -1
                                                                                             ? argOffset + 7
                                                                                             : -1),
                                                                           (unitsColumn != -1
                                                                                              ? argOffset + 8
                                                                                              : -1),
                                                                           null));

                                // Increment the offset for the next row
                                argOffset += columnsPerArg;
                            }

                            // Add the command header to the name space
                            addNamespaceCommands(namespace,
                                                 tableData,
                                                 0,
                                                 -1,
                                                 1,
                                                 true,
                                                 null);
                        }
                        // This is not the command header structure
                        else
                        {
                            int uniqueID = 0;
                            List<String> dataTypes = new ArrayList<String>();

                            // Export the parameter container for this structure
                            addParameterContainer(namespace,
                                                  tableInfo,
                                                  varColumn,
                                                  typeColumn,
                                                  sizeColumn,
                                                  minColumn,
                                                  maxColumn);

                            // Step through each row in the structure table
                            for (Object[] rowData : tableInfo.getData())
                            {
                                // Check if the variable isn't an array member (the array
                                // definition is used to define the array)
                                if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                                {
                                    // Check if the data type references a structure
                                    if (!dataTypeHandler.isPrimitive(rowData[typeColumn].toString()))
                                    {
                                        // Check if this structure reference has already been used
                                        // as a data type in this structure
                                        if (dataTypes.contains(rowData[typeColumn]))
                                        {
                                            // Increment the unique ID identifier. The identifier
                                            // prevents creating multiple parameter data types with
                                            // the same name
                                            uniqueID++;
                                        }
                                        // This is the first use of this structure data type
                                        else
                                        {
                                            // Add the structure name to the list of those in use
                                            dataTypes.add(rowData[typeColumn].toString());
                                        }
                                    }

                                    // Add the variable to the data sheet
                                    addParameter(namespace,
                                                 rowData[varColumn].toString(),
                                                 rowData[typeColumn].toString(),
                                                 rowData[sizeColumn].toString(),
                                                 rowData[bitColumn].toString(),
                                                 (enumColumn != -1 && !rowData[enumColumn].toString().isEmpty()
                                                                                                                ? rowData[enumColumn].toString()
                                                                                                                : null),
                                                 (unitsColumn != -1 && !rowData[unitsColumn].toString().isEmpty()
                                                                                                                  ? rowData[unitsColumn].toString()
                                                                                                                  : null),
                                                 (descColumn != -1 && !rowData[descColumn].toString().isEmpty()
                                                                                                                ? rowData[descColumn].toString()
                                                                                                                : null),
                                                 (dataTypeHandler.isString(rowData[typeColumn].toString()) && !rowData[sizeColumn].toString().isEmpty()
                                                                                                                                                        ? Integer.valueOf(rowData[sizeColumn].toString().replaceAll("^.*(\\d+)$", "$1"))
                                                                                                                                                        : 1),
                                                 (uniqueID == 0
                                                                ? ""
                                                                : String.valueOf(uniqueID)),
                                                 applicationID);
                                }
                            }
                        }
                    }
                    // This is a command table
                    else
                    {
                        // Get the list containing the associated column indices for each argument
                        // grouping
                        commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

                        // Add the command(s) from this table to the data sheet
                        addNamespaceCommands(namespace,
                                             CcddUtilities.convertObjectToString(tableInfo.getData()),
                                             typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME),
                                             typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE),
                                             typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION),
                                             false,
                                             applicationID);
                    }
                }
            }
        }

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the name of the system to which this table belongs from the table's
            // system path data field (if present)
            String systemPath = cleanSystemPath(fieldHandler.getFieldValue(tableName,
                                                                           DefaultInputType.SYSTEM_PATH));

            // Get the name space for this table
            NamespaceType namespace = searchNamespacesForName(systemPath, cleanSystemPath(tableName));

            // Check if the table's name space exists and it has a data set
            if (namespace != null && namespace.getDataTypeSet() != null)
            {
                // Step through the each parameter type
                for (RootDataType type : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                {
                    // Check if the type is a container for a structure; if the container has a
                    // base type it's a reference to a child structure
                    if (type instanceof ContainerDataType
                        && ((ContainerDataType) type).getBaseType() != null)
                    {
                        // Get the base type, which has the name space path for the child structure
                        String typeName = ((ContainerDataType) type).getBaseType();

                        // Get the beginning of the last portion of the path, which is the
                        // structure data type
                        int index = ((ContainerDataType) type).getBaseType().lastIndexOf("/");

                        // Check if the structure data type exists
                        if (index != -1)
                        {
                            // Remove the structure data type, leaving only the name space path to
                            // the child structure's definition
                            typeName = typeName.substring(0, index);
                        }

                        // Get the name space for the child structure's definition
                        NamespaceType nmspc = searchNamespacesForName(null, typeName);

                        // Check if the name space exists
                        if (nmspc != null)
                        {
                            DataTypeSetType dataTypeSet = nmspc.getDataTypeSet();

                            // Check if the child's data type set exists
                            if (dataTypeSet != null && !dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().isEmpty())
                            {
                                // Step through each data type
                                for (RootDataType rootData : dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
                                {
                                    // Check if this is the container with the structure's members
                                    if (rootData instanceof ContainerDataType
                                        && rootData.getName().equals(type.getName()))
                                    {
                                        // Set the parent structure table's list of members of the
                                        // child structure using the child structure definition's
                                        // list, and stop searching
                                        ((ContainerDataType) type).setEntryList(((ContainerDataType) rootData).getEntryList());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Create a new name space as a child within the specified name space
     *
     * @param systemPath
     *            system name; null or blank if no system
     *
     * @param tableName
     *            table name with full path
     *
     * @param description
     *            data sheet description
     *
     * @return Reference to the new name space
     *********************************************************************************************/
    private NamespaceType addNamespace(String systemPath, String tableName, String description)
    {
        // Create the new name space and set the name attribute
        NamespaceType childSpace = factory.createNamespaceType();

        // Set the name space name
        childSpace.setName((systemPath != null
                                               ? systemPath + "/"
                                               : "")
                           + cleanSystemPath(tableName));

        // Check if a description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            childSpace.setLongDescription(description);
        }

        // Store the table name, with its full path, in the short description field. This is used
        // if the export file is used to import tables into a project
        childSpace.setShortDescription(tableName);

        // Add the new names space
        dataSheet.getNamespace().add(childSpace);

        return childSpace;
    }

    /**********************************************************************************************
     * Search for the name space with the same name as the search name
     *
     * @param systemPath
     *            system name; null or blank if no system
     *
     * @param namespaceName
     *            name of the name space to search for within the name space hierarchy
     *
     * @return Reference to the name space with the same name as the search name; null if no name
     *         space name matches the search name
     *********************************************************************************************/
    private NamespaceType searchNamespacesForName(String systemPath, String namespaceName)
    {
        NamespaceType foundNamespace = null;

        // Prepend the system path, if present
        namespaceName = (systemPath != null
                                            ? systemPath + "/"
                                            : "")
                        + namespaceName;

        // Step through each name space
        for (NamespaceType namespace : dataSheet.getNamespace())
        {
            // Check if the current name space's name matches the search name
            if (namespace.getName().equals(namespaceName))
            {
                // Store the reference to the matching name space
                foundNamespace = namespace;
                break;
            }
        }

        return foundNamespace;
    }

    /**********************************************************************************************
     * Create the parameter set for the specified name space
     *
     * @param namespace
     *            name space
     *
     * @return Reference to the parameter set
     *********************************************************************************************/
    private InterfaceDeclarationType createParameterSet(NamespaceType namespace)
    {
        InterfaceDeclarationType intParmType = factory.createInterfaceDeclarationType();
        intParmType.setName(TELEMETRY);
        intParmType.setParameterSet(factory.createParameterSetType());
        namespace.getDeclaredInterfaceSet().getInterface().add(intParmType);
        return intParmType;
    }

    /**********************************************************************************************
     * Add the parameter container
     *
     * @param namespace
     *            name space
     *
     * @param tableInfo
     *            table information reference
     *
     * @param varColumn
     *            variable name column index (model coordinates)
     *
     * @param typeColumn
     *            data type column index (model coordinates)
     *
     * @param sizeColumn
     *            array size column index (model coordinates)
     *
     * @param minColumn
     *            minimum value column index (model coordinates)
     *
     * @param maxColumn
     *            maximum value column index (model coordinates)
     *********************************************************************************************/
    private void addParameterContainer(NamespaceType namespace,
                                       TableInformation tableInfo,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       int minColumn,
                                       int maxColumn)
    {
        ContainerDataType containerType = null;
        EntryListType entryList = factory.createEntryListType();

        // Step through each row of data in the structure table
        for (Object[] rowData : tableInfo.getData())
        {
            // Check if this is not an array member (non-array parameters and array definitions are
            // used to create the list)
            if (!ArrayVariable.isArrayMember(rowData[varColumn]))
            {
                // Store the parameter reference in the list
                EntryType entryType = factory.createEntryType();
                entryType.setName(rowData[varColumn].toString());
                entryType.setType(rowData[typeColumn]
                                  + getObjectIdentifier(rowData[sizeColumn].toString()));

                // Check if a minimum or maximum value exists
                if ((minColumn != -1 && !rowData[minColumn].toString().isEmpty())
                    || (maxColumn != -1 && !rowData[maxColumn].toString().isEmpty()))
                {
                    DerivedTypeRangeType range = factory.createDerivedTypeRangeType();
                    MinMaxRangeType minMaxRange = factory.createMinMaxRangeType();
                    minMaxRange.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);

                    // Set the flag if the parameter is in integer data type
                    boolean isInteger = dataTypeHandler.isInteger(rowData[typeColumn].toString());

                    // Check if a minimum value is specified
                    if (minColumn != -1 && !rowData[minColumn].toString().isEmpty())
                    {
                        // Set the minimum value
                        minMaxRange.setMin(isInteger
                                                     ? BigDecimal.valueOf(Integer.valueOf(rowData[minColumn].toString()))
                                                     : BigDecimal.valueOf(Float.valueOf(rowData[minColumn].toString())));
                    }

                    // Check if a maximum value is specified
                    if (maxColumn != -1 && !rowData[maxColumn].toString().isEmpty())
                    {
                        // Set the maximum value
                        minMaxRange.setMax(isInteger
                                                     ? BigDecimal.valueOf(Integer.valueOf(rowData[maxColumn].toString()))
                                                     : BigDecimal.valueOf(Float.valueOf(rowData[maxColumn].toString())));
                    }

                    // Set the range
                    range.setMinMaxRange(minMaxRange);
                    entryType.setValidRange(range);
                }

                entryList.getEntryOrFixedValueEntryOrPaddingEntry().add(entryType);
            }
        }

        // Check if any parameters exist
        if (!entryList.getEntryOrFixedValueEntryOrPaddingEntry().isEmpty())
        {
            // Check if the parameter sequence container set hasn't been created
            if (containerType == null)
            {
                // Create the parameter sequence container set
                containerType = factory.createContainerDataType();
            }

            containerType.setName(cleanSystemPath(macroHandler.getMacroExpansion(tableInfo.getTablePath()))
                                  + TYPE);

            // Store the parameters in the parameter sequence container
            containerType.setEntryList(entryList);
        }

        // Check if any parameters exist
        if (containerType != null)
        {
            // Get the data type set for this name space
            DataTypeSetType dataTypeSet = namespace.getDataTypeSet();

            // Check if the data type set doesn't exist, which is the case for the first
            // enumerated parameter
            if (dataTypeSet == null)
            {
                // Create the data type set
                dataTypeSet = factory.createDataTypeSetType();
            }

            // Add the parameters to the system
            dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(containerType);
            namespace.setDataTypeSet(dataTypeSet);
        }
    }

    /**********************************************************************************************
     * Add a telemetry parameter to the name space's parameter set. Create the parameter set for
     * the name space if it does not exist
     *
     * @param namespace
     *            name space
     *
     * @param parameterName
     *            parameter name
     *
     * @param dataType
     *            parameter primitive data type
     *
     * @param arraySize
     *            parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength
     *            parameter bit length; null or blank if not a bit-wise parameter
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
     *
     * @param units
     *            parameter units
     *
     * @param description
     *            parameter description
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *
     * @param uniqueID
     *            text used to uniquely identify data types with the same name; blank if the data
     *            type has no name conflict
     *
     * @param applicationID
     *            application ID
     *********************************************************************************************/
    private void addParameter(NamespaceType namespace,
                              String parameterName,
                              String dataType,
                              String arraySize,
                              String bitLength,
                              String enumeration,
                              String units,
                              String description,
                              int stringSize,
                              String uniqueID,
                              String applicationID)
    {
        // Check if a data type is provided. If none is provided then no entry for this parameter
        // appears under the ParameterTypeSet, but it will appear under the ParameterSet
        if (dataType != null)
        {
            // Get the parameter's data type information
            setDataType(namespace,
                        parameterName,
                        dataType,
                        arraySize,
                        bitLength,
                        enumeration,
                        units,
                        description,
                        stringSize,
                        uniqueID,
                        applicationID);

            // Build the parameter attributes
            InterfaceParameterType parameter = factory.createInterfaceParameterType();
            parameter.setName(parameterName);
            parameter.setType((dataTypeHandler.isPrimitive(dataType)
                                                                     ? parameterName
                                                                     : dataType)
                              + getObjectIdentifier(arraySize)
                              + uniqueID);

            // Check if a description is provided for this parameter
            if (description != null && !description.isEmpty())
            {
                // Set the parameter's description
                parameter.setLongDescription(description);
            }

            InterfaceDeclarationType intParmType = null;

            // Check if the declared interface set doesn't exist
            if (namespace.getDeclaredInterfaceSet() == null)
            {
                // Create an interface set for the name space
                namespace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());
            }

            // Step through the interfaces in order to locate the name space's parameter set
            for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
            {
                // Check if the interface contains a parameter set
                if (intfcDecType.getParameterSet() != null)
                {
                    // Get the parameter set reference and stop searching
                    intParmType = intfcDecType;
                    break;
                }
            }

            // Check if a parameter set exists
            if (intParmType == null)
            {
                // Create the parameter set for this name space
                intParmType = createParameterSet(namespace);
            }

            // Add the parameter to the parameter set
            intParmType.getParameterSet().getParameter().add(parameter);
        }
    }

    /**********************************************************************************************
     * Create the command set for the specified name space
     *
     * @param namespace
     *            name space
     *
     * @return Reference to the command set
     *********************************************************************************************/
    private InterfaceDeclarationType createCommandSet(NamespaceType namespace)
    {
        // Check if the declared interface set doesn't exist
        InterfaceDeclarationType intCmdType = factory.createInterfaceDeclarationType();
        intCmdType.setName(COMMAND);

        // Check if this is the interface for the command header
        if (namespace.getName().equals((cmdHeaderPath != null
                                        && !cmdHeaderPath.isEmpty()
                                                                    ? cmdHeaderPath + "/"
                                                                    : "")
                                       + cmdHeaderTable))
        {
            // Set the abstract flag to indicate the command set represents a command header
            intCmdType.setAbstract(true);
        }

        intCmdType.setCommandSet(factory.createCommandSetType());
        namespace.getDeclaredInterfaceSet().getInterface().add(intCmdType);
        return intCmdType;
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified name space
     *
     * @param namespace
     *            name space for this node
     *
     * @param tableData
     *            table data array
     *
     * @param cmdNameColumn
     *            command name column index
     *
     * @param cmdCodeColumn
     *            command code column index
     *
     * @param cmdDescColumn
     *            command description column index
     *
     * @param isCmdHeader
     *            true if this table represents the command header
     *
     * @param applicationID
     *            application ID
     *********************************************************************************************/
    private void addNamespaceCommands(NamespaceType namespace,
                                      String[][] tableData,
                                      int cmdNameColumn,
                                      int cmdCodeColumn,
                                      int cmdDescColumn,
                                      boolean isCmdHeader,
                                      String applicationID)
    {
        List<String> argumentNames = new ArrayList<String>();

        // Step through each command argument column grouping
        for (AssociatedColumns cmdArg : commandArguments)
        {
            // Check if the argument description column exists and it matches the index set for the
            // command description
            if (cmdArg.getDescription() != -1 && cmdArg.getDescription() == cmdDescColumn)
            {
                // There is no column for the command description, so reset its column index and
                // stop searching
                cmdDescColumn = -1;
                break;
            }
        }

        // Step through each row in the table
        for (String[] rowData : tableData)
        {
            // Check if the command name exists
            if (cmdNameColumn != -1 && !rowData[cmdNameColumn].isEmpty())
            {
                // Initialize the command attributes and argument names list
                String cmdFuncCode = null;
                String commandDescription = null;
                List<CommandArgumentType> arguments = new ArrayList<CommandArgumentType>();

                // Store the command name
                String commandName = rowData[cmdNameColumn];

                // Check if the command code exists
                if (cmdCodeColumn != -1 && !rowData[cmdCodeColumn].isEmpty())
                {
                    // Store the command code
                    cmdFuncCode = rowData[cmdCodeColumn];
                }

                // Check if the command description exists
                if (cmdDescColumn != -1 && !rowData[cmdDescColumn].isEmpty())
                {
                    // Store the command description
                    commandDescription = rowData[cmdDescColumn];
                }

                // Step through each command argument column grouping
                for (AssociatedColumns cmdArg : commandArguments)
                {
                    // Initialize the command argument attributes
                    String argumentName = null;
                    String dataType = null;
                    String arraySize = null;
                    String bitLength = null;
                    String enumeration = null;
                    String units = null;
                    int stringSize = 1;

                    // Check if the command argument name and data type exist
                    if (cmdArg.getName() != -1
                        && !rowData[cmdArg.getName()].isEmpty()
                        && cmdArg.getDataType() != -1 &&
                        !rowData[cmdArg.getDataType()].isEmpty())
                    {
                        int uniqueID = 0;

                        // Store the command argument name and data type
                        argumentName = rowData[cmdArg.getName()];
                        dataType = rowData[cmdArg.getDataType()];

                        // Add a command argument to the command metadata
                        CommandArgumentType argType = factory.createCommandArgumentType();
                        argType.setName(argumentName);

                        // Check if the description column exists
                        if (cmdArg.getDescription() != -1 && !rowData[cmdArg.getDescription()].isEmpty())
                        {
                            // Store the command argument description
                            argType.setLongDescription(rowData[cmdArg.getDescription()]);
                        }

                        // Check if the array size column exists
                        if (cmdArg.getArraySize() != -1 && !rowData[cmdArg.getArraySize()].isEmpty())
                        {
                            // Store the command argument array size value
                            arraySize = rowData[cmdArg.getArraySize()];

                            // Check if the command argument has a string data type
                            if (rowData[cmdArg.getDataType()].equals(DefaultPrimitiveTypeInfo.STRING.getUserName()))
                            {
                                // Separate the array dimension values and get the string size
                                int[] arrayDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                                stringSize = arrayDims[0];
                            }
                        }

                        // Check if the bit length column exists
                        if (cmdArg.getBitLength() != -1 && !rowData[cmdArg.getBitLength()].isEmpty())
                        {
                            // Store the command argument bit length value
                            bitLength = rowData[cmdArg.getBitLength()];
                        }

                        // Check if the enumeration column exists
                        if (cmdArg.getEnumeration() != -1 && !rowData[cmdArg.getEnumeration()].isEmpty())
                        {
                            // Store the command argument enumeration value
                            enumeration = rowData[cmdArg.getEnumeration()];
                        }

                        // Check if the units column exists
                        if (cmdArg.getUnits() != -1 && !rowData[cmdArg.getUnits()].isEmpty())
                        {
                            // Store the command argument units
                            units = rowData[cmdArg.getUnits()];
                        }

                        // Check if the command argument has a minimum or maximum value
                        if ((cmdArg.getMinimum() != -1 && !rowData[cmdArg.getMinimum()].isEmpty())
                            || (cmdArg.getMaximum() != -1 && !rowData[cmdArg.getMaximum()].isEmpty()))
                        {
                            DerivedTypeRangeType range = factory.createDerivedTypeRangeType();
                            MinMaxRangeType minMaxRange = factory.createMinMaxRangeType();
                            minMaxRange.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);

                            // Set the flag if the parameter is in integer data type
                            boolean isInteger = dataTypeHandler.isInteger(rowData[cmdArg.getDataType()]);

                            // Check if a minimum value is specified
                            if (cmdArg.getMinimum() != -1 && !rowData[cmdArg.getMinimum()].isEmpty())
                            {
                                // Set the minimum value
                                minMaxRange.setMin(isInteger
                                                             ? BigDecimal.valueOf(Integer.valueOf(rowData[cmdArg.getMinimum()]))
                                                             : BigDecimal.valueOf(Float.valueOf(rowData[cmdArg.getMinimum()])));
                            }

                            // Check if a maximum value is specified
                            if (cmdArg.getMaximum() != -1 && !rowData[cmdArg.getMaximum()].isEmpty())
                            {
                                // Set the maximum value
                                minMaxRange.setMax(isInteger
                                                             ? BigDecimal.valueOf(Integer.valueOf(rowData[cmdArg.getMaximum()]))
                                                             : BigDecimal.valueOf(Float.valueOf(rowData[cmdArg.getMaximum()])));
                            }

                            // Set the range
                            range.setMinMaxRange(minMaxRange);
                            argType.setValidRange(range);
                        }

                        // Check if the argument name has already been used
                        if (argumentNames.contains(argumentName))
                        {
                            // Increment the unique ID identifier
                            uniqueID++;
                        }
                        // This is the first occurrence of the argument name in this command table
                        else
                        {
                            // Add the name to the list
                            argumentNames.add(argumentName);
                        }

                        // Set the argument type reference
                        argType.setType(argumentName
                                        + (arraySize != null && !arraySize.isEmpty()
                                                                                     ? ARRAY
                                                                                     : TYPE)
                                        + (uniqueID == 0
                                                         ? ""
                                                         : String.valueOf(uniqueID)));

                        // Get the argument's data type information
                        setDataType(namespace,
                                    argumentName,
                                    dataType,
                                    arraySize,
                                    bitLength,
                                    enumeration,
                                    units,
                                    null,
                                    stringSize,
                                    (uniqueID == 0
                                                   ? ""
                                                   : String.valueOf(uniqueID)),
                                    applicationID);

                        // Add the command argument to the list
                        arguments.add(argType);
                    }
                }

                // Add the command information
                addCommand(namespace,
                           commandName,
                           cmdFuncCode,
                           applicationID,
                           isCmdHeader,
                           arguments,
                           commandDescription);
            }
        }
    }

    /**********************************************************************************************
     * Add a command metadata set to the command metadata
     *
     * @param namespace
     *            name space
     *
     * @param commandName
     *            command name
     *
     * @param cmdFuncCode
     *            command code
     *
     * @param applicationID
     *            application ID
     *
     * @param isCmdHeader
     *            true if this table represents the command header
     *
     * @param arguments
     *            list of command argument types
     *
     * @param description
     *            description of the command
     *********************************************************************************************/
    private void addCommand(NamespaceType namespace,
                            String commandName,
                            String cmdFuncCode,
                            String applicationID,
                            boolean isCmdHeader,
                            List<CommandArgumentType> arguments,
                            String description)
    {
        // Build the command attributes
        InterfaceCommandType command = factory.createInterfaceCommandType();
        command.setName(commandName);

        // Check if a command description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the command description
            command.setLongDescription(description);
        }

        // Check if any arguments are supplied for this command
        if (!arguments.isEmpty())
        {
            // Step through each argument type
            for (CommandArgumentType argType : arguments)
            {
                // Add the argument information to the command
                command.getArgument().add(argType);
            }
        }

        InterfaceDeclarationType intCmdType = null;

        // Check if the declared interface set doesn't exist
        if (namespace.getDeclaredInterfaceSet() == null)
        {
            // Create an interface set for the name space
            namespace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());
        }

        // Step through the interfaces in order to locate the name space's command set
        for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
        {
            // Check if the interface contains a command set
            if (intfcDecType.getCommandSet() != null)
            {
                // Get the command set reference and stop searching
                intCmdType = intfcDecType;
                break;
            }
        }

        // Check if a command set exists
        if (intCmdType == null)
        {
            // Create the command set for this name space
            intCmdType = createCommandSet(namespace);
        }

        // Check if this isn't the command header table
        if (!isCmdHeader && cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Set the command header as the base
            BaseTypeSetType baseType = factory.createBaseTypeSetType();
            InterfaceRefType intfcType = factory.createInterfaceRefType();
            intfcType.setType(cmdHeaderTable + "/" + COMMAND);
            baseType.getBaseType().add(intfcType);
            intCmdType.setBaseTypeSet(baseType);

            // Get the data type set for this name space
            DataTypeSetType dataTypeSet = namespace.getDataTypeSet();

            // Check if the data type set doesn't exist, which is the case for the first
            // enumerated parameter
            if (dataTypeSet == null)
            {
                // Create the data type set
                dataTypeSet = factory.createDataTypeSetType();
            }

            // Create a container for storing the application ID and command function code
            ContainerDataType containerType = factory.createContainerDataType();
            containerType.setName(commandName);
            containerType.setBaseType((cmdHeaderPath != null
                                       && !cmdHeaderPath.isEmpty()
                                                                   ? cmdHeaderPath + "/"
                                                                   : "")
                                      + cmdHeaderTable);
            ConstraintSet constraintSet = factory.createContainerDataTypeConstraintSet();

            // Check if the application ID exists
            if (applicationIDName != null
                && applicationID != null
                && !applicationIDName.isEmpty()
                && !applicationID.isEmpty())
            {
                // Set the application ID value
                ContainerConstraintType appIDType = factory.createContainerConstraintType();
                appIDType.setEntry(applicationIDName);
                ContainerValueConstraintType appIDValue = factory.createContainerValueConstraintType();
                appIDValue.setValue(applicationID);
                appIDType.setValueConstraint(appIDValue);
                constraintSet.getConstraint().add(appIDType);
            }

            // Check if the command function code exists
            if (cmdFuncCodeName != null
                && cmdFuncCode != null
                && !cmdFuncCodeName.isEmpty()
                && !cmdFuncCode.isEmpty())
            {
                // Set the command function code value
                ContainerConstraintType funcCodeType = factory.createContainerConstraintType();
                funcCodeType.setEntry(cmdFuncCodeName);
                ContainerValueConstraintType funcCodeValue = factory.createContainerValueConstraintType();
                funcCodeValue.setValue(cmdFuncCode);
                funcCodeType.setValueConstraint(funcCodeValue);
                constraintSet.getConstraint().add(funcCodeType);
            }

            // Check if the application ID and/or command function code are set
            if (!constraintSet.getConstraint().isEmpty())
            {
                // Store the constraint set containing the application ID and/or command function
                // code
                containerType.setConstraintSet(constraintSet);
            }

            dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(containerType);
        }

        // Add the command to the command set
        intCmdType.getCommandSet().getCommand().add(command);
    }

    /**********************************************************************************************
     * Create the parameter data type and set the specified attributes
     *
     * @param namespace
     *            name space
     *
     * @param parameterName
     *            parameter name; null to not specify
     *
     * @param dataType
     *            data type; null to not specify
     *
     * @param arraySize
     *            parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength
     *            parameter bit length; null or empty if not a bit-wise parameter
     *
     * @param enumeration
     *            enumeration in the format {@literal <enum label>|<enum value>[|...][,...]}; null
     *            to not specify
     *
     * @param units
     *            parameter units; null to not specify
     *
     * @param description
     *            parameter description; null or blank to not specify
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *
     * @param uniqueID
     *            text used to uniquely identify data types with the same name; blank if the data
     *            type has no name conflict
     *
     * @param applicationID
     *            application ID
     *********************************************************************************************/
    private void setDataType(NamespaceType namespace,
                             String parameterName,
                             String dataType,
                             String arraySize,
                             String bitLength,
                             String enumeration,
                             String units,
                             String description,
                             int stringSize,
                             String uniqueID,
                             String applicationID)
    {
        RootDataType parameterType = null;

        // Get the data type set for this name space
        DataTypeSetType dataTypeSet = namespace.getDataTypeSet();

        // Check if the data type set doesn't exist, which is the case for the first
        // enumerated parameter
        if (dataTypeSet == null)
        {
            // Create the data type set
            dataTypeSet = factory.createDataTypeSetType();
        }

        // Check if the parameter is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes
            ArrayDataType arrayType = factory.createArrayDataType();
            String name = dataTypeHandler.isPrimitive(dataType)
                                                                ? parameterName
                                                                : dataType;
            arrayType.setName(name + ARRAY + uniqueID);
            arrayType.setDataTypeRef(name + TYPE);
            ArrayDimensionsType dimList = factory.createArrayDimensionsType();

            // Step through each array dimension
            for (int dim : ArrayVariable.getArrayIndexFromSize(arraySize))
            {
                // Create a dimension entry for the array type. The dimension size is the number of
                // elements in this array dimension
                DimensionSizeType dimSize = factory.createDimensionSizeType();
                dimSize.setSize(BigInteger.valueOf(dim));
                dimList.getDimension().add(dimSize);
            }

            arrayType.setDimensionList(dimList);

            // Add the data type information to this name space
            dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(arrayType);
            namespace.setDataTypeSet(dataTypeSet);
        }

        // Check if the parameter has a primitive data type
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Get the base data type corresponding to the primitive data type
            BasePrimitiveDataType baseDataType = getBaseDataType(dataType, dataTypeHandler);

            // Check if the a corresponding base data type exists
            if (baseDataType != null)
            {
                // Check if enumeration parameters are provided
                if (enumeration != null && !enumeration.isEmpty())
                {
                    // Create an enumeration type and enumeration list
                    EnumeratedDataType enumType = factory.createEnumeratedDataType();
                    EnumerationListType enumList = createEnumerationList(namespace, enumeration);

                    // Set the integer encoding (the only encoding available for an enumeration)
                    // and the size in bits
                    IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                    // Check if the parameter has a bit length
                    if (bitLength != null && !bitLength.isEmpty())
                    {
                        // Set the size in bits to the value supplied
                        intEncodingType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                    }
                    // Not a bit-wise parameter
                    else
                    {
                        // Set the size in bits to the full size of the data type
                        intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                    }

                    // Check if the data type is an unsigned integer
                    if (dataTypeHandler.isUnsignedInt(dataType))
                    {
                        // Set the encoding type to indicate an unsigned integer
                        intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                    }
                    // The data type is a signed integer
                    else
                    {
                        // Set the encoding type to indicate a signed integer
                        intEncodingType.setEncoding(IntegerEncodingType.SIGN_MAGNITUDE);
                    }

                    intEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                    ? ByteOrderType.BIG_ENDIAN
                                                                                    : ByteOrderType.LITTLE_ENDIAN);
                    enumType.setIntegerDataEncoding(intEncodingType);
                    enumType.setEnumerationList(enumList);
                    parameterType = enumType;
                }
                // Not an enumeration
                else
                {
                    switch (baseDataType)
                    {
                        case INTEGER:
                            // Create an integer type
                            IntegerDataType integerType = factory.createIntegerDataType();
                            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                            IntegerDataTypeRangeType integerRange = factory.createIntegerDataTypeRangeType();

                            // Check if the parameter has a bit length
                            if (bitLength != null && !bitLength.isEmpty())
                            {
                                // Set the size in bits to the value supplied
                                intEncodingType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                            }
                            // Not a bit-wise parameter
                            else
                            {
                                // Set the size in bits to the full size of the data type
                                intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            }

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                            }
                            // The data type is a signed integer
                            else
                            {
                                // Set the encoding type to indicate a signed integer
                                intEncodingType.setEncoding(IntegerEncodingType.SIGN_MAGNITUDE);
                            }

                            // Set the minimum and maximum range. This section is required for
                            // validation, even if empty
                            MinMaxRangeType minMax = factory.createMinMaxRangeType();
                            minMax.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);
                            integerRange.setMinMaxRange(minMax);

                            integerType.setRange(integerRange);
                            intEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                            ? ByteOrderType.BIG_ENDIAN
                                                                                            : ByteOrderType.LITTLE_ENDIAN);
                            integerType.setIntegerDataEncoding(intEncodingType);
                            setUnits(units, integerType);
                            parameterType = integerType;
                            break;

                        case FLOAT:
                            // Create a float type
                            FloatDataType floatType = factory.createFloatDataType();
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            FloatDataTypeRangeType floatRange = factory.createFloatDataTypeRangeType();

                            // Set the encoding type based on the size in bytes
                            switch (dataTypeHandler.getSizeInBytes(dataType))
                            {
                                case 4:
                                    floatEncodingType.setEncodingAndPrecision(FloatEncodingAndPrecisionType.IEEE_754_2008_SINGLE);
                                    floatRange.setPrecisionRange(FloatPrecisionRangeType.SINGLE);
                                    break;

                                case 8:
                                    floatEncodingType.setEncodingAndPrecision(FloatEncodingAndPrecisionType.IEEE_754_2008_DOUBLE);
                                    floatRange.setPrecisionRange(FloatPrecisionRangeType.DOUBLE);
                                    break;

                                case 16:
                                    floatEncodingType.setEncodingAndPrecision(FloatEncodingAndPrecisionType.IEEE_754_2008_QUAD);
                                    break;

                                default:
                                    break;
                            }

                            floatType.setRange(floatRange);
                            floatEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                              ? ByteOrderType.BIG_ENDIAN
                                                                                              : ByteOrderType.LITTLE_ENDIAN);
                            floatType.setFloatDataEncoding(floatEncodingType);
                            setUnits(units, floatType);
                            parameterType = floatType;
                            break;

                        case STRING:
                            // Create a string type
                            StringDataType stringType = factory.createStringDataType();
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();
                            stringEncodingType.setEncoding(StringEncodingType.UTF_8);
                            stringEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                               ? ByteOrderType.BIG_ENDIAN
                                                                                               : ByteOrderType.LITTLE_ENDIAN);
                            stringType.setStringDataEncoding(stringEncodingType);
                            stringType.setLength(BigInteger.valueOf(stringSize));
                            parameterType = stringType;
                            break;
                    }
                }
            }
        }
        // Structure data type
        else
        {
            // Create a container type for the structure
            ContainerDataType containerType = factory.createContainerDataType();

            // Create the path name to this structure parameter's type
            String path = namespace.getName() + "_" + dataType + "_" + parameterName;

            int index = path.lastIndexOf("/");

            if (index != -1)
            {
                path += "/" + path.substring(index + 1);
            }

            // Set the parameter's base type
            containerType.setBaseType(path + getObjectIdentifier(arraySize));

            // Check if this is a reference to the telemetry header and the application ID exists
            if (dataType.equals(tlmHeaderTable)
                && applicationID != null
                && !applicationID.isEmpty())
            {
                // Set the application ID value
                ContainerConstraintType appIDType = factory.createContainerConstraintType();
                appIDType.setEntry(applicationIDName);
                ContainerValueConstraintType appIDValue = factory.createContainerValueConstraintType();
                appIDValue.setValue(applicationID);
                appIDType.setValueConstraint(appIDValue);
                ConstraintSet constraintSet = factory.createContainerDataTypeConstraintSet();
                constraintSet.getConstraint().add(appIDType);
                containerType.setConstraintSet(constraintSet);
            }

            parameterName = dataType;
            parameterType = containerType;
        }

        // Set the type name
        parameterType.setName(parameterName + TYPE + uniqueID);

        // Check is a description exists
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            parameterType.setLongDescription(description);
        }

        // Add the data type information to this name space
        dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(parameterType);
        namespace.setDataTypeSet(dataTypeSet);
    }

    /**********************************************************************************************
     * Set the supplied type's units from the supplied units string
     *
     * @param units
     *            parameter or command argument units; null to not specify
     *
     * @param type
     *            reference to the numeric data type in which to place the units information
     *********************************************************************************************/
    private void setUnits(String units, NumericDataType type)
    {
        try
        {
            // This throws an illegal argument exception if the unit is not one of those in the
            // Unit enum class
            Unit unit = Unit.fromValue(units);
            SemanticsType semType = factory.createSemanticsType();
            semType.setUnit(unit);
            type.setSemantics(semType);
        }
        catch (IllegalArgumentException iae)
        {
            // User-supplied units don't match one of the hard-coded Unit types (from Units.java),
            // which are the only ones that are accepted by the Unit fromValue() method. The
            // hard-coded unit types list is limited
        }
    }

    /**********************************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param namespace
     *            name space
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    private EnumerationListType createEnumerationList(NamespaceType namespace, String enumeration)
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
            String enumPairSep = CcddUtilities.getEnumerationPairSeparator(enumeration, enumValSep);

            // Check if the enumerated pair separator couldn't be located
            if (enumPairSep == null)
            {
                throw new CCDDException("separator character between enumerated pairs missing");
            }

            // Divide the enumeration string into the separate enumeration definitions
            String[] enumDefn = enumeration.split(Pattern.quote(enumPairSep));

            // Step through each enumeration definition
            for (int index = 0; index < enumDefn.length; index++)
            {
                // Split the enumeration definition into the name and label components
                String[] enumParts = enumDefn[index].split(Pattern.quote(enumValSep), 2);

                // Create a new enumeration value type and add the enumerated name and value to the
                // enumeration list
                ValueEnumerationType valueEnum = factory.createValueEnumerationType();
                valueEnum.setLabel(enumParts[1].trim());
                valueEnum.setValue(BigInteger.valueOf(Integer.valueOf(enumParts[0].trim())));
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
                                                              + namespace.getName()
                                                              + "<b>'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }

    /**********************************************************************************************
     * Get the object identifier based on the presence or absence of the array size
     *
     * @param arraySize
     *            array size; null or blank if not an array data type
     *
     * @return The object identifier: _Array if the supplied array size isn't null or blank, or
     *         _Type otherwise
     *********************************************************************************************/
    private String getObjectIdentifier(String arraySize)
    {
        return arraySize == null || arraySize.isEmpty()
                                                        ? TYPE
                                                        : ARRAY;
    }
}
