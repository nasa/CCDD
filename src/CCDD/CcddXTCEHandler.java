/**
 * CFS Command & Data Dictionary XTCE handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.COL_MAXIMUM;
import static CCDD.CcddConstants.COL_MINIMUM;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.io.IOException;
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
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.omg.space.xtce.AggregateDataType;
import org.omg.space.xtce.AggregateDataType.MemberList;
import org.omg.space.xtce.AggregateDataType.MemberList.Member;
import org.omg.space.xtce.ArgumentTypeSetType;
import org.omg.space.xtce.ArgumentTypeSetType.FloatArgumentType;
import org.omg.space.xtce.ArgumentTypeSetType.IntegerArgumentType;
import org.omg.space.xtce.ArrayDataTypeType;
import org.omg.space.xtce.ArrayParameterRefEntryType;
import org.omg.space.xtce.ArrayParameterRefEntryType.DimensionList;
import org.omg.space.xtce.ArrayParameterRefEntryType.DimensionList.Dimension;
import org.omg.space.xtce.BaseDataType;
import org.omg.space.xtce.BaseDataType.UnitSet;
import org.omg.space.xtce.CommandContainerEntryListType;
import org.omg.space.xtce.CommandContainerEntryListType.ArgumentRefEntry;
import org.omg.space.xtce.CommandContainerType;
import org.omg.space.xtce.CommandMetaDataType;
import org.omg.space.xtce.CommandMetaDataType.MetaCommandSet;
import org.omg.space.xtce.ComparisonType;
import org.omg.space.xtce.ContainerRefEntryType;
import org.omg.space.xtce.ContainerSetType;
import org.omg.space.xtce.DescriptionType.AncillaryDataSet;
import org.omg.space.xtce.DescriptionType.AncillaryDataSet.AncillaryData;
import org.omg.space.xtce.EntryListType;
import org.omg.space.xtce.EnumeratedDataType;
import org.omg.space.xtce.EnumeratedDataType.EnumerationList;
import org.omg.space.xtce.FloatDataEncodingType;
import org.omg.space.xtce.FloatRangeType;
import org.omg.space.xtce.HeaderType;
import org.omg.space.xtce.HeaderType.AuthorSet;
import org.omg.space.xtce.HeaderType.NoteSet;
import org.omg.space.xtce.IntegerDataEncodingType;
import org.omg.space.xtce.IntegerRangeType;
import org.omg.space.xtce.IntegerValueType;
import org.omg.space.xtce.MatchCriteriaType.ComparisonList;
import org.omg.space.xtce.MetaCommandType;
import org.omg.space.xtce.MetaCommandType.ArgumentList;
import org.omg.space.xtce.MetaCommandType.ArgumentList.Argument;
import org.omg.space.xtce.MetaCommandType.BaseMetaCommand;
import org.omg.space.xtce.MetaCommandType.BaseMetaCommand.ArgumentAssignmentList;
import org.omg.space.xtce.MetaCommandType.BaseMetaCommand.ArgumentAssignmentList.ArgumentAssignment;
import org.omg.space.xtce.NameDescriptionType;
import org.omg.space.xtce.ObjectFactory;
import org.omg.space.xtce.ParameterRefEntryType;
import org.omg.space.xtce.ParameterSetType;
import org.omg.space.xtce.ParameterSetType.Parameter;
import org.omg.space.xtce.ParameterTypeSetType;
import org.omg.space.xtce.ParameterTypeSetType.EnumeratedParameterType;
import org.omg.space.xtce.ParameterTypeSetType.FloatParameterType;
import org.omg.space.xtce.ParameterTypeSetType.IntegerParameterType;
import org.omg.space.xtce.ParameterTypeSetType.StringParameterType;
import org.omg.space.xtce.SequenceContainerType;
import org.omg.space.xtce.SequenceContainerType.BaseContainer;
import org.omg.space.xtce.SequenceContainerType.BaseContainer.RestrictionCriteria;
import org.omg.space.xtce.SequenceEntryType;
import org.omg.space.xtce.SpaceSystemType;
import org.omg.space.xtce.StringDataEncodingType;
import org.omg.space.xtce.StringDataEncodingType.SizeInBits;
import org.omg.space.xtce.StringDataType;
import org.omg.space.xtce.TelemetryMetaDataType;
import org.omg.space.xtce.UnitType;
import org.omg.space.xtce.ValueEnumerationType;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary XTCE handler class
 *************************************************************************************************/
public class CcddXTCEHandler extends CcddImportSupportHandler implements CcddImportExportInterface
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

    // GUI component instantiating this class
    private final Component parent;

    // Export endian type
    private EndianType endianess;

    // Lists containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and XTCE object references
    private JAXBElement<SpaceSystemType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;

    // Attribute strings
    private String versionAttr;
    private String validationStatusAttr;
    private String classification1Attr;
    private String classification2Attr;
    private String classification3Attr;

    // Conversion setup error flag
    private boolean errorFlag;

    // Flag to indicate if the telemetry and command headers are big endian (as with CCSDS)
    private boolean isHeaderBigEndian;

    // Names of the tables that represent the common header for all telemetry and command tables
    private String tlmHeaderTable;
    private String cmdHeaderTable;

    // Names of the system paths for the common header for all command tables
    private String cmdHeaderPath;

    // Telemetry and command header variable names for the application ID, and command header
    // argument column name for the command function code
    private String applicationIDName;
    private String cmdFuncCodeName;

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

    /**********************************************************************************************
     * Structure member list
     *********************************************************************************************/
    private class StructureMemberList
    {
        private final String structureName;
        private final MemberList memberList;

        /******************************************************************************************
         * Structure member list constructor
         *
         * @param structureName
         *            structure table name
         *
         * @param memberList
         *            member list
         *****************************************************************************************/
        StructureMemberList(String structureName, MemberList memberList)
        {
            this.structureName = structureName;
            this.memberList = memberList;
        }
    }

    /**********************************************************************************************
     * XTCE handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param fieldHandler
     *            reference to a data field handler
     *
     * @param parent
     *            GUI component instantiating this class
     *********************************************************************************************/
    CcddXTCEHandler(CcddMain ccddMain, CcddFieldHandler fieldHandler, Component parent)
    {
        this.ccddMain = ccddMain;
        this.fieldHandler = fieldHandler;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        rateHandler = ccddMain.getRateParameterHandler();

        errorFlag = false;

        // Build the data field information for all fields
        this.fieldHandler.buildFieldInformation(null);

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into XTCE XML format
            JAXBContext context = JAXBContext.newInstance("org.omg.space.xtce");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   ModifiableOtherSettingInfo.XTCE_SCHEMA_LOCATION_URL.getValue());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, new Boolean(true));

            // Create the factory for building the space system objects
            factory = new ObjectFactory();

            // Create the XML unmarshaller used to convert XTCE XML data into CCDD project data
            // format
            unmarshaller = context.createUnmarshaller();
        }
        catch (JAXBException je)
        {
            // Inform the user that the XTCE/JAXB set up failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>XTCE conversion setup failed; cause '"
                                                              + je.getMessage()
                                                              + "'",
                                                      "XTCE Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
    }

    /**********************************************************************************************
     * Get the status of the conversion setup error flag
     *
     * @return true if an error occurred setting up for the XTCE conversion
     *********************************************************************************************/
    @Override
    public boolean getErrorStatus()
    {
        return errorFlag;
    }

    /**********************************************************************************************
     * Get the table definitions
     *
     * @return List of table definitions
     *********************************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
    }

    /**********************************************************************************************
     * Import the the table definitions from an XTCE XML formatted file
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
     *             If a data is missing, extraneous, or in error in the import file
     *
     * @throws IOException
     *             If an import file I/O error occurs
     *
     * @throws Exception
     *             For any unanticipated errors
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile,
                               ImportType importType,
                               TypeDefinition targetTypeDefn) throws CCDDException,
                                                              IOException,
                                                              Exception
    {
        try
        {
            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            // Get the top-level space system
            SpaceSystemType rootSystem = (SpaceSystemType) jaxbElement.getValue();

            tableDefinitions = new ArrayList<TableDefinition>();
            structureTypeDefn = null;
            commandTypeDefn = null;

            AncillaryDataSet ancillarySet = rootSystem.getAncillaryDataSet();

            // Check if the root system contains ancillary data
            if (ancillarySet != null)
            {
                // Step through each ancillary data item
                for (AncillaryData data : ancillarySet.getAncillaryData())
                {
                    // Check if the item name matches that for the telemetry header table name
                    // indicator
                    if (data.getName().equals(InputDataType.XML_TLM_HDR.getInputName()))
                    {
                        // Store the item value as the telemetry header table name
                        tlmHeaderTable = data.getValue();
                    }
                    // Check if the item name matches that for the command header table name
                    // indicator
                    else if (data.getName().equals(InputDataType.XML_CMD_HDR.getInputName()))
                    {
                        // Store the item value as the command header table name
                        cmdHeaderTable = data.getValue();
                    }
                    // Check if the item name matches that for the application ID variable name
                    // indicator
                    else if (data.getName().equals(InputDataType.XML_APP_ID.getInputName()))
                    {
                        // Store the item value as the application ID variable name
                        applicationIDName = data.getValue();
                    }
                    // Check if the item name matches that for the command function code variable
                    // name indicator
                    else if (data.getName().equals(InputDataType.XML_FUNC_CODE.getInputName()))
                    {
                        // Store the item value as the command function code variable name
                        cmdFuncCodeName = data.getValue();
                    }
                }
            }

            // Check if the telemetry table name isn't set in the project import file
            if (tlmHeaderTable == null)
            {
                // Get the name of the table representing the telemetry header from the project
                tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                            InputDataType.XML_TLM_HDR);
            }

            // Check if the command table name isn't set in the project import file
            if (cmdHeaderTable == null)
            {
                // Get the name of the table representing the command header from the project
                cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                            InputDataType.XML_CMD_HDR);
            }

            // Check if the application ID variable name isn't set in the project import file
            if (applicationIDName == null)
            {
                // Get the application ID variable name from the project field
                applicationIDName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                               InputDataType.XML_APP_ID);

                // Check if the application ID variable name isn't set in the project
                if (applicationIDName == null)
                {
                    // Use the default application ID variable name
                    applicationIDName = DefaultHeaderVariableName.APP_ID.getDefaultVariableName();
                }
            }

            // Check if the command function code variable name isn't set in the import file
            if (cmdFuncCodeName == null)
            {
                // Get the command function code variable name from the project field
                cmdFuncCodeName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                             InputDataType.XML_FUNC_CODE);

                // Check if the command function code variable name isn't set in the project
                if (cmdFuncCodeName == null)
                {
                    // Use the default command function code variable name
                    cmdFuncCodeName = DefaultHeaderVariableName.FUNC_CODE.getDefaultVariableName();
                }
            }

            // Create the table type definitions for any new structure and command tables
            createTableTypeDefinitions(rootSystem, importType, targetTypeDefn);

            // Check if at least one structure or command table needs to be built
            if (structureTypeDefn != null || commandTypeDefn != null)
            {
                // Set the flag if importing into an existing table to indicate that only a command
                // header, which is converted to structure table, is allowed when processing
                // commands
                boolean onlyCmdToStruct = importType == ImportType.FIRST_DATA_ONLY
                                          && targetTypeDefn.isStructure();

                // Set the flag to indicate if the target is a structure table
                // Step through each space system
                for (SpaceSystemType system : rootSystem.getSpaceSystem())
                {
                    // Recursively step through the XTCE-formatted data and extract the telemetry
                    // and command information
                    unbuildSpaceSystems(system, "", importType, onlyCmdToStruct);

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
            // Inform the user that the database import failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot import XTCE XML from file<br>'</b>"
                                                              + importFile.getAbsolutePath()
                                                              + "<b>'; cause '"
                                                              + je.getMessage()
                                                              + "'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }
    }

    /**********************************************************************************************
     * Scan the import file in order to determine if any structure or command tables exist. If so,
     * create the structure and/or command table type definition that's used to build the new
     * tables
     *
     * @param rootSystem
     *            root space system
     *
     * @param importFileName
     *            import file name
     *
     * @param targetTypeDefn
     *            table type definition of the table in which to import the data; ignored if
     *            importing all tables
     *********************************************************************************************/
    private void createTableTypeDefinitions(SpaceSystemType rootSystem,
                                            ImportType importType,
                                            TypeDefinition targetTypeDefn)
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

        // Step through each space system
        for (SpaceSystemType system : rootSystem.getSpaceSystem())
        {
            // Recursively step through the XTCE-formatted data and extract the telemetry and
            // command information
            findMetaData(system, importType, targetIsStructure, targetIsCommand);
        }

        // Check if a structure table type needs to be defined
        if ((isTelemetry || isCmdToTlm) && targetIsStructure)
        {
            // Check if all tables are to be imported
            if (importType == ImportType.IMPORT_ALL)
            {
                String typeName = "XTCE Structure";
                int sequence = 2;

                // Continue to check while a table type with this name exists
                while (tableTypeHandler.getTypeDefinition(typeName) != null)
                {
                    // Alter the name so that there isn't a duplicate
                    typeName = "XTCE Structure " + sequence;
                    sequence++;
                }

                // Create the XTCE structure table type using the default structure columns
                structureTypeDefn = tableTypeHandler.createTypeDefinition(typeName,
                                                                          DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE),
                                                                          "XTCE import structure table type");

                // Get the current number of columns defined for the structure table type. The new
                // columns are appended to the existing ones
                int columnIndex = structureTypeDefn.getColumnCountDatabase();

                // Add the minimum and maximum value columns
                structureTypeDefn.addColumn(columnIndex,
                                            structureTypeDefn.getColumnNameDatabase(COL_MINIMUM,
                                                                                    InputDataType.MINIMUM),
                                            COL_MINIMUM,
                                            "Minimum value",
                                            InputDataType.MINIMUM,
                                            false,
                                            false,
                                            false,
                                            true);
                structureTypeDefn.addColumn(columnIndex + 1,
                                            structureTypeDefn.getColumnNameDatabase(COL_MAXIMUM,
                                                                                    InputDataType.MAXIMUM),
                                            COL_MAXIMUM,
                                            "Maximum value",
                                            InputDataType.MAXIMUM,
                                            false,
                                            false,
                                            false,
                                            true);
            }
            // Only a single table is to be imported
            else
            {
                structureTypeDefn = targetTypeDefn;
            }

            // Get structure table column indices
            variableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.VARIABLE));
            dataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT));
            arraySizeIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX));
            bitLengthIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH));
            enumerationIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.ENUMERATION));
            minimumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.MINIMUM));
            maximumIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.MAXIMUM));
            descriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));
            unitsIndex = CcddTableTypeHandler.getVisibleColumnIndex(structureTypeDefn.getColumnIndexByInputType(InputDataType.UNITS));

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
                String typeName = "XTCE Command";
                int sequence = 2;

                // Continue to check while a table type with this name exists
                while (tableTypeHandler.getTypeDefinition(typeName) != null)
                {
                    // Alter the name so that there isn't a duplicate
                    typeName = "XTCE Command " + sequence;
                    sequence++;
                }

                // Create the XTCE command table type using the default command columns
                commandTypeDefn = tableTypeHandler.createTypeDefinition(typeName,
                                                                        DefaultColumn.getDefaultColumnDefinitions(TYPE_COMMAND),
                                                                        "XTCE import command table type");

                // Step through each additional command argument column set
                for (int argIndex = 2; argIndex < maxNumArguments; argIndex++)
                {
                    // Add the default columns for this command argument
                    commandTypeDefn.addCommandArgumentColumns(argIndex);
                }
            }
            // A single command table is to be imported into an existing command table
            else
            {
                commandTypeDefn = targetTypeDefn;
            }

            // Get the list containing the associated column indices for each argument grouping
            commandArguments = commandTypeDefn.getAssociatedCommandArgumentColumns(true);

            // Get the command table column indices
            commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME));
            cmdFuncCodeIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.COMMAND_CODE));
            cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));

            // Get the number of columns defined in the command table type
            numCommandColumns = commandTypeDefn.getColumnCountVisible();
        }
    }

    /**********************************************************************************************
     * Recursively scan the import file in order to determine if any structure or command tables
     * exist. If a command table determine the maximum number of command arguments its commands
     * require
     *
     * @param system
     *            space system
     *
     * @param importFileName
     *            import file name
     *
     * @param targetIsStructure
     *            true if the table type definition of the table in which to import the data
     *            represents a structure; ignored if importing all tables
     *
     * @param targetIsCommand
     *            true if the table type definition of the table in which to import the data
     *            represents a command; ignored if importing all tables
     *********************************************************************************************/
    private void findMetaData(SpaceSystemType system,
                              ImportType importType,
                              boolean targetIsStructure,
                              boolean targetIsCommand)
    {
        // Get the child system's telemetry metadata information
        TelemetryMetaDataType tlmMetaData = system.getTelemetryMetaData();

        // Check if the telemetry metadata information exists. If so, then the assumption is made
        // that this is a structure table
        if (tlmMetaData != null)
        {
            isTelemetry = true;
        }

        // Get the child system's command metadata information
        CommandMetaDataType cmdMetaData = system.getCommandMetaData();

        // Check if the command metadata information exists. If so, then the assumption is made
        // that this is a command table
        if (cmdMetaData != null)
        {
            // Get the reference to the meta-command set
            MetaCommandSet commandSet = cmdMetaData.getMetaCommandSet();

            // Check if the meta-command set exists
            if (commandSet != null)
            {
                // Step through each entry in the meta-command set
                for (Object metaCommand : commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand())
                {
                    // Check if the entry is a meta-command type
                    if (metaCommand instanceof MetaCommandType)
                    {
                        // Check if the command is flagged as abstract. This denotes a command
                        // header which is converted to a structure, and therefore necessitates
                        // creating a structure table type to contain it
                        if (((MetaCommandType) metaCommand).isAbstract())
                        {
                            isCmdToTlm = true;
                        }
                        // The command isn't flagged as abstract. This denotes a command table
                        else
                        {
                            isCommand = true;
                        }

                        // The number of entries in the meta-command type is the number of command
                        // arguments required by this command. Store the largest number of command
                        // arguments required by all commands in the import file
                        maxNumArguments = Math.max(maxNumArguments,
                                                   ((MetaCommandType) metaCommand).getArgumentList().getArgument().size());
                    }
                }
            }
        }

        // Check if the data from all tables is to be read or if only a single table is to be read
        // but one of the target table type hasn't been found
        if (importType == ImportType.IMPORT_ALL
            || (targetIsStructure && !isTelemetry && !isCmdToTlm)
            || (targetIsCommand && !isCommand))
        {
            // Step through each child system, if any
            for (SpaceSystemType childSystem : system.getSpaceSystem())
            {
                // Process this system's children, if any
                findMetaData(childSystem, importType, targetIsStructure, targetIsCommand);
            }
        }
    }

    /**********************************************************************************************
     * Extract the telemetry and/or command information from the space system. This is a recursive
     * method
     *
     * @param system
     *            space system
     *
     * @param systemPath
     *            full path name for this space system (based on its nesting within other space
     *            systems)
     *
     * @param importFileName
     *            import file name
     *
     * @param targetTypeName
     *            name of the target table type if importing a single table into an existing table;
     *            ignored if importing all tables
     *
     * @param onlyCmdToStruct
     *            true to only allow a command header, converted to a structure, to be stored;
     *            false to store (non-header) command tables
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void unbuildSpaceSystems(SpaceSystemType system,
                                     String systemPath,
                                     ImportType importType,
                                     boolean onlyCmdToStruct) throws CCDDException
    {
        // Get the table name based from the space system name
        String tableName = system.getName();

        // Get the child system's telemetry metadata information
        TelemetryMetaDataType tlmMetaData = system.getTelemetryMetaData();

        // Check if the telemetry metadata information is present and a structure table type
        // definition exists to define it (the structure table type won't exists if importing into
        // a single command table). If the telemetry metadata is present the assumption is made
        // that this is a structure table
        if (tlmMetaData != null && structureTypeDefn != null)
        {
            // Build the structure table from the telemetry data
            importStructureTable(system, tlmMetaData, tableName, systemPath);
        }

        // Get the child system's command metadata information
        CommandMetaDataType cmdMetaData = system.getCommandMetaData();

        // Check if the command metadata information exists; if so, the assumption is made that
        // this is a command table
        if (cmdMetaData != null)
        {
            // Build the command table from the telemetry data
            importCommandTable(system, cmdMetaData, tableName, systemPath, onlyCmdToStruct);
        }

        // Check if the data from all tables is to be read or no table of the target type has been
        // located yet
        if (importType == ImportType.IMPORT_ALL || tableDefinitions.isEmpty())
        {
            // Step through each child system, if any
            for (SpaceSystemType childSystem : system.getSpaceSystem())
            {
                // Process this system's children, if any
                unbuildSpaceSystems(childSystem,
                                    systemPath
                                                 + (systemPath.isEmpty()
                                                                         ? ""
                                                                         : "/")
                                                 + tableName,
                                    importType,
                                    onlyCmdToStruct);
            }
        }
    }

    /**********************************************************************************************
     * Build a structure table from the specified telemetry metadata
     *
     * @param system
     *            space system
     *
     * @param tlmMetaData
     *            reference to the telemetry metadata from which to build the structure table
     *
     * @param table
     *            name table name, including the full system path
     *
     * @param systemPath
     *            system path
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importStructureTable(SpaceSystemType system,
                                      TelemetryMetaDataType tlmMetaData,
                                      String tableName,
                                      String systemPath) throws CCDDException
    {
        // Create a table definition for this structure table. If the name space also includes a
        // command metadata (which creates a command table) then ensure the two tables have
        // different names
        TableDefinition tableDefn = new TableDefinition(tableName
                                                        + (system.getCommandMetaData() == null
                                                                                               ? ""
                                                                                               : "_tlm"),
                                                        system.getLongDescription());

        // Set the new structure table's table type name
        tableDefn.setTypeName(structureTypeDefn.getName());

        // Get the telemetry information
        ParameterSetType parmSetType = tlmMetaData.getParameterSet();
        ParameterTypeSetType parmTypeSetType = tlmMetaData.getParameterTypeSet();

        // Check if the telemetry information exists
        if (parmSetType != null && parmTypeSetType != null)
        {
            // Get the references to the parameter set and parameter type set
            List<Object> parmSet = parmSetType.getParameterOrParameterRef();
            List<NameDescriptionType> parmTypeSet = parmTypeSetType.getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType();

            // Step through each telemetry parameter
            for (int parmIndex = 0; parmIndex < parmSet.size(); parmIndex++)
            {
                // Get the reference to the parameter in the parameter set
                Parameter parm = (Parameter) parmSet.get(parmIndex);

                // Create a new row of data in the table definition to contain this structures's
                // information. Initialize all columns to blanks except for the variable name
                String[] newRow = new String[numStructureColumns];
                Arrays.fill(newRow, null);
                newRow[variableNameIndex] = parm.getName();
                tableDefn.addData(newRow);

                // Step through the parameter type set to find the data type entry where the name
                // matches the parameter type reference from the parameter set
                for (NameDescriptionType parmType : parmTypeSet)
                {
                    // Check if the parameter set's parameter type reference matches the parameter
                    // type set's name
                    if (parm.getParameterTypeRef().equals(parmType.getName()))
                    {
                        String dataType = null;
                        String arraySize = null;
                        BigInteger bitLength = null;
                        long sizeInBits = 0;
                        String enumeration = null;
                        String minimum = null;
                        String maximum = null;
                        UnitSet unitSet = null;

                        // Check if the parameter is an array data type
                        if (parmType instanceof ArrayDataTypeType)
                        {
                            // The size of each array dimension is located in a container set. The
                            // array parameter reference containing the dimensions for the
                            // parameter matches the parameter name. Get the container set
                            // reference
                            ContainerSetType containerSet = tlmMetaData.getContainerSet();

                            // Check if the container set exists
                            if (containerSet != null)
                            {
                                // Step through each sequence container in the container set
                                for (SequenceContainerType seqContainer : containerSet.getSequenceContainer())
                                {
                                    // Check if this is the sequence container for the target
                                    // system
                                    if (system.getName().equals(seqContainer.getName()))
                                    {
                                        // Step through each entry in the sequence
                                        for (SequenceEntryType entry : seqContainer.getEntryList().getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry())
                                        {
                                            // Check if the entry if for an array and the parameter
                                            // reference matches the target parameter
                                            if (entry instanceof ArrayParameterRefEntryType
                                                && parm.getName().equals(((ArrayParameterRefEntryType) entry).getParameterRef()))
                                            {
                                                arraySize = "";

                                                // Store the reference to the array parameter type
                                                ArrayDataTypeType arrayType = (ArrayDataTypeType) parmType;
                                                parmType = null;

                                                // Step through each dimension for the array
                                                // variable
                                                for (Dimension dim : ((ArrayParameterRefEntryType) entry).getDimensionList().getDimension())
                                                {
                                                    // Build the array size string
                                                    arraySize += String.valueOf(dim.getEndingIndex().getFixedValue()) + ",";
                                                }

                                                arraySize = CcddUtilities.removeTrailer(arraySize, ",");

                                                // The array parameter type references a non-array
                                                // parameter type that describes the individual
                                                // array members. Step through each data type in
                                                // the parameter type set in order to locate this
                                                // data type entry
                                                for (NameDescriptionType type : parmTypeSet)
                                                {
                                                    // Check if the array parameter's array type
                                                    // reference matches the data type name
                                                    if (arrayType.getArrayTypeRef().equals(type.getName()))
                                                    {
                                                        // Store the reference to the array
                                                        // parameter's data type and stop searching
                                                        parmType = type;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        break;
                                    }
                                }
                            }
                        }

                        // Check if a data type entry for the parameter exists in the parameter
                        // type set (note that if the parameter is an array the steps above locate
                        // the data type entry for the individual array members)
                        if (parmType != null)
                        {
                            boolean isInteger = false;
                            boolean isUnsigned = false;
                            boolean isFloat = false;
                            boolean isString = false;

                            // Check if the parameter is an integer data type
                            if (parmType instanceof IntegerParameterType)
                            {
                                // The 'sizeInBits' references are the integer size for
                                // non-bit-wise parameters, but equal the number of bits assigned
                                // to the parameter for a bit-wise parameter. It doens't appear
                                // that the size of the integer used to contain the parameter is
                                // stored. The assumption is made that the smallest integer
                                // required to store the bits is used. However, this can alter the
                                // originally intended bit-packing (e.g., a 3-bit and a 9-bit fit
                                // within a single 16-bit integer, but the code below assigns the
                                // first to an 8-bit integer and the second to a 16-bit integer)

                                IntegerParameterType itlm = (IntegerParameterType) parmType;

                                // Get the number of bits occupied by the parameter
                                bitLength = itlm.getSizeInBits();

                                // Get the parameter units reference
                                unitSet = itlm.getUnitSet();

                                // Check if integer encoding is set to 'unsigned'
                                if (itlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
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

                                // Get the parameter range
                                IntegerRangeType range = itlm.getValidRange();

                                // Check if the parameter has a range
                                if (range != null)
                                {
                                    // Check if the minimum value exists
                                    if (range.getMinInclusive() != null)
                                    {
                                        // Store the minimum
                                        minimum = range.getMinInclusive();
                                    }

                                    // Check if the maximum value exists
                                    if (range.getMaxInclusive() != null)
                                    {
                                        // Store the maximum
                                        maximum = range.getMaxInclusive();
                                    }
                                }

                                isInteger = true;
                            }
                            // Check if the parameter is a floating point data type
                            else if (parmType instanceof FloatParameterType)
                            {
                                // Get the float parameter attributes
                                FloatParameterType ftlm = (FloatParameterType) parmType;
                                sizeInBits = ftlm.getSizeInBits().longValue();
                                unitSet = ftlm.getUnitSet();

                                // Get the parameter range
                                FloatRangeType range = ftlm.getValidRange();

                                // Check if the parameter has a range
                                if (range != null)
                                {
                                    // Check if the minimum value exists
                                    if (range.getMinInclusive() != null)
                                    {
                                        // Store the minimum
                                        minimum = String.valueOf(range.getMinInclusive());
                                    }

                                    // Check if the maximum exists
                                    if (range.getMaxInclusive() != null)
                                    {
                                        // Store the maximum
                                        maximum = String.valueOf(range.getMaxInclusive());
                                    }
                                }

                                isFloat = true;
                            }
                            // Check if the parameter is a string data type
                            else if (parmType instanceof StringParameterType)
                            {
                                // Get the string parameter attributes
                                StringParameterType stlm = (StringParameterType) parmType;
                                sizeInBits = stlm.getCharacterWidth().longValue() * 8;
                                unitSet = stlm.getUnitSet();
                                isString = true;
                            }
                            // Check if the parameter is an enumerated data type
                            else if (parmType instanceof EnumeratedParameterType)
                            {
                                // Get the enumeration parameters
                                EnumeratedParameterType etlm = (EnumeratedParameterType) parmType;
                                EnumerationList enumList = etlm.getEnumerationList();

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
                                    unitSet = etlm.getUnitSet();

                                    // Check if integer encoding is set to 'unsigned'
                                    if (etlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                                    {
                                        isUnsigned = true;
                                    }

                                    // Determine the smallest integer size that contains the number
                                    // of bits occupied by the parameter
                                    sizeInBits = 8;

                                    while (bitLength.longValue() > sizeInBits)
                                    {
                                        sizeInBits *= 2;
                                    }

                                    isInteger = true;
                                }
                            }
                            // Check if the parameter is an aggregate data type, i.e., a structure
                            // reference
                            else if (parmType instanceof AggregateDataType)
                            {
                                // The aggregate name is the table path (with _Type appended,
                                // though this is ignored since the variable portion isn't needed).
                                // Extract the prototype table name from the table path
                                dataType = TableInformation.getPrototypeName(parmType.getName());
                            }

                            // Check if the data type is a primitive
                            if (dataType == null)
                            {
                                // Get the name of the data type from the data type table that
                                // matches the base type and size of the parameter
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

                            // Check if a description exists
                            if (parmType.getLongDescription() != null)
                            {
                                // Store the description
                                tableDefn.getData().set(parmIndex
                                                        * numStructureColumns
                                                        + descriptionIndex,
                                                        parmType.getLongDescription());
                            }

                            // Check if a units exists and
                            if (unitSet != null)
                            {
                                List<UnitType> unitType = unitSet.getUnit();

                                // Check if the units exist
                                if (!unitType.isEmpty())
                                {
                                    // Store the units for this variable
                                    tableDefn.getData().set(parmIndex
                                                            * numStructureColumns
                                                            + unitsIndex,
                                                            unitType.get(0).getContent());
                                }
                            }

                            // Check if an enumeration exists
                            if (enumeration != null)
                            {
                                // Store the enumeration parameters. This accounts only for the
                                // first enumeration for a variable
                                tableDefn.getData().set(parmIndex
                                                        * numStructureColumns
                                                        + enumerationIndex,
                                                        enumeration);
                            }

                            // Check if a minimum value exists
                            if (minimum != null)
                            {
                                // Store the minimum value
                                tableDefn.getData().set(parmIndex
                                                        * numStructureColumns
                                                        + minimumIndex,
                                                        minimum);
                            }

                            // Check if a maximum value exists
                            if (maximum != null)
                            {
                                // Store the maximum value
                                tableDefn.getData().set(parmIndex
                                                        * numStructureColumns
                                                        + maximumIndex,
                                                        maximum);
                            }
                        }

                        break;
                    }
                }
            }

            ContainerSetType containerSet;

            // Check if the application ID data field name and the container set exist
            if ((containerSet = tlmMetaData.getContainerSet()) != null)
            {
                // Step through each sequence container in the container set
                for (SequenceContainerType seqContainer : containerSet.getSequenceContainer())
                {
                    // Check if this is the sequence container for the target system
                    if (system.getName().equals(seqContainer.getName()))
                    {
                        BaseContainer baseContainer = seqContainer.getBaseContainer();

                        // Check if this is the comparison list for the telemetry header table
                        if (baseContainer != null
                            && baseContainer.getContainerRef() != null
                            && baseContainer.getRestrictionCriteria() != null
                            && baseContainer.getRestrictionCriteria().getComparisonList() != null
                            && baseContainer.getRestrictionCriteria().getComparisonList().getComparison() != null
                            && TableInformation.getPrototypeName(baseContainer.getContainerRef()).equals(tlmHeaderTable))
                        {
                            // Step through each item in the comparison list
                            for (ComparisonType comparison : baseContainer.getRestrictionCriteria().getComparisonList().getComparison())
                            {
                                // Check if the comparison item's parameter reference matches the
                                // application ID name
                                if (comparison.getParameterRef().equals(applicationIDName))
                                {
                                    // Create a data field for the table containing the application
                                    // ID. Once a match is found the search is discontinued
                                    tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                                    comparison.getParameterRef(),
                                                                                                    "Message ID",
                                                                                                    InputDataType.MESSAGE_ID,
                                                                                                    Math.min(Math.max(comparison.getValue().length(),
                                                                                                                      5),
                                                                                                             40),
                                                                                                    false,
                                                                                                    ApplicabilityType.ROOT_ONLY,
                                                                                                    comparison.getValue()));
                                    break;
                                }
                            }
                        }

                        break;
                    }
                }
            }
        }

        // Create a data field for the system path
        tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                        "System path",
                                                                        "System Path",
                                                                        InputDataType.SYSTEM_PATH,
                                                                        Math.min(Math.max(systemPath.length(),
                                                                                          5),
                                                                                 40),
                                                                        false,
                                                                        ApplicabilityType.ALL,
                                                                        systemPath));

        // Add the structure table definition to the list
        tableDefinitions.add(tableDefn);
    }

    /**********************************************************************************************
     * Build a command table from the specified command metadata
     *
     * @param system
     *            space system
     *
     * @param cmdMetaData
     *            reference to the command metadata from which to build the command table
     *
     * @param table
     *            name table name, including the full system path
     *
     * @param systemPath
     *            system path
     *
     * @param onlyCmdToStruct
     *            true to only allow a command header, converted to a structure, to be stored;
     *            false to store (non-header) command tables
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importCommandTable(SpaceSystemType system,
                                    CommandMetaDataType cmdMetaData,
                                    String tableName,
                                    String systemPath,
                                    boolean onlyCmdToStruct) throws CCDDException
    {
        int abstractCount = 0;

        // Create a table definition for this command table. If the name space also includes a
        // telemetry metadata (which creates a structure table) then ensure the two tables have
        // different names
        TableDefinition tableDefn = new TableDefinition(tableName
                                                        + (system.getTelemetryMetaData() == null
                                                                                                 ? ""
                                                                                                 : "_cmd"),
                                                        system.getLongDescription());

        // Set the new command table's table type name
        tableDefn.setTypeName(commandTypeDefn.getName());

        // Check if the description column belongs to a command argument
        if (commandArguments.size() != 0
            && cmdDescriptionIndex > commandArguments.get(0).getName())
        {
            // Reset the command description index to indicate no description exists
            cmdDescriptionIndex = -1;
        }

        // Get the command set information
        MetaCommandSet metaCmdSet = cmdMetaData.getMetaCommandSet();

        // Check if the command set information exists
        if (metaCmdSet != null)
        {
            // Get the command argument information
            ArgumentTypeSetType argTypeSetType = cmdMetaData.getArgumentTypeSet();
            List<NameDescriptionType> argTypeSet = null;

            // Check if there are any arguments for this command
            if (argTypeSetType != null)
            {
                // Get the list of this command's argument data types
                argTypeSet = argTypeSetType.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType();
            }

            // Step through each command set
            for (Object cmd : metaCmdSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand())
            {
                // Check if the command represents a meta command type (all of these should)
                if (cmd instanceof MetaCommandType)
                {
                    // Get the command type as a meta command type to shorten subsequent calls
                    MetaCommandType metaCmd = (MetaCommandType) cmd;

                    // Create a new row of data in the table definition to contain this command's
                    // information. Initialize all columns to blanks except for the command name
                    String[] rowData = new String[numCommandColumns];
                    Arrays.fill(rowData, null);
                    rowData[commandNameIndex] = metaCmd.getName();

                    // Get the base meta-command reference
                    BaseMetaCommand baseMetaCmd = metaCmd.getBaseMetaCommand();

                    // Check if the base meta-command exists
                    if (baseMetaCmd != null)
                    {
                        // Step through each argument assignment
                        for (ArgumentAssignment argAssn : baseMetaCmd.getArgumentAssignmentList().getArgumentAssignment())
                        {
                            // Check if the name and value exist
                            if (argAssn.getArgumentName() != null
                                && argAssn.getArgumentValue() != null)
                            {
                                // Check if the argument name matches the application ID variable
                                // name
                                if (argAssn.getArgumentName().equals(applicationIDName))
                                {
                                    boolean isExists = false;

                                    // Step through the data fields already added to this table
                                    for (String[] fieldInfo : tableDefn.getDataFields())
                                    {
                                        // Check if a data field with the name matching the
                                        // application ID variable name already exists. This is the
                                        // case if the command table has multiple commands; the
                                        // first one causes the application ID field to be created,
                                        // so the subsequent ones are ignored to prevent duplicates
                                        if (fieldInfo[FieldsColumn.FIELD_NAME.ordinal()].equals(argAssn.getArgumentName()))
                                        {
                                            // Set the flag indicating the field already exists and
                                            // stop searching
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
                                                                                                        argAssn.getArgumentName(),
                                                                                                        "Message ID",
                                                                                                        InputDataType.MESSAGE_ID,
                                                                                                        Math.min(Math.max(argAssn.getArgumentValue().length(),
                                                                                                                          5),
                                                                                                                 40),
                                                                                                        false,
                                                                                                        ApplicabilityType.ALL,
                                                                                                        argAssn.getArgumentValue()));
                                    }
                                }
                                // Check if the argument name matches the command function code
                                // variable name
                                else if (argAssn.getArgumentName().equals(cmdFuncCodeName))
                                {
                                    // Store the command function code
                                    rowData[cmdFuncCodeIndex] = argAssn.getArgumentValue();
                                }
                            }
                        }
                    }

                    // Check if the command description is present and the description column
                    // exists in the table type definition
                    if (metaCmd.getLongDescription() != null && cmdDescriptionIndex != -1)
                    {
                        // Store the command description in the row's description column
                        rowData[cmdDescriptionIndex] = metaCmd.getLongDescription();
                    }

                    // Check if the command has any arguments
                    if (metaCmd.getArgumentList() != null && argTypeSet != null)
                    {
                        int cmdArgIndex = 0;
                        CommandContainerType cmdContainer = metaCmd.getCommandContainer();

                        // Step through each of the command's arguments
                        for (Argument argList : metaCmd.getArgumentList().getArgument())
                        {
                            // Step through each command argument type
                            for (NameDescriptionType argType : argTypeSet)
                            {
                                // Check if this is the same command argument referenced in the
                                // argument list (by matching the command and argument names
                                // between the two)
                                if (argList.getArgumentTypeRef().equals(argType.getName()))
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
                                    UnitSet unitSet = null;
                                    String units = null;
                                    String minimum = null;
                                    String maximum = null;

                                    // Check if the argument is an array data type
                                    if (argType instanceof ArrayDataTypeType
                                        && metaCmd != null
                                        && cmdContainer != null)
                                    {
                                        // Step through each sequence container in the container
                                        // set
                                        for (JAXBElement<? extends SequenceEntryType> seqEntry : cmdContainer.getEntryList().getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry())
                                        {
                                            // Check if the entry if for an array and the parameter
                                            // reference matches the target parameter
                                            if (seqEntry.getValue() instanceof ArrayParameterRefEntryType
                                                && argList.getName().equals(((ArrayParameterRefEntryType) seqEntry.getValue()).getParameterRef()))
                                            {
                                                arraySize = "";

                                                // Store the reference to the array parameter type
                                                ArrayDataTypeType arrayType = (ArrayDataTypeType) argType;
                                                argType = null;

                                                // Step through each dimension for
                                                // the array variable
                                                for (Dimension dim : ((ArrayParameterRefEntryType) seqEntry.getValue()).getDimensionList().getDimension())
                                                {
                                                    // Build the array size string
                                                    arraySize += String.valueOf(dim.getEndingIndex().getFixedValue())
                                                                 + ",";
                                                }

                                                arraySize = CcddUtilities.removeTrailer(arraySize, ",");

                                                // The array parameter type references a non-array
                                                // parameter type that describes the individual
                                                // array members. Step through each data type in
                                                // the parameter type set in order to locate this
                                                // data type entry
                                                for (NameDescriptionType type : argTypeSet)
                                                {
                                                    // Check if the array parameter's array type
                                                    // reference matches the data type name
                                                    if (arrayType.getArrayTypeRef().equals(type.getName()))
                                                    {
                                                        // Store the reference to the array
                                                        // parameter's data type and stop searching
                                                        argType = type;
                                                        break;
                                                    }
                                                }

                                                break;
                                            }
                                        }
                                    }

                                    // Check if a data type entry for the parameter exists in the
                                    // parameter type set (note that if the parameter is an array
                                    // the steps above locate the data type entry for the
                                    // individual array members)
                                    if (argType != null)
                                    {
                                        // Check if the argument is an integer data type
                                        if (argType instanceof IntegerArgumentType)
                                        {
                                            IntegerArgumentType icmd = (IntegerArgumentType) argType;

                                            // Get the number of bits occupied by the argument
                                            bitLength = icmd.getSizeInBits();

                                            // Get the argument units reference
                                            unitSet = icmd.getUnitSet();

                                            // Check if integer encoding is set to 'unsigned'
                                            if (icmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                                            {
                                                isUnsigned = true;
                                            }

                                            // Determine the smallest integer size that contains
                                            // the number of bits occupied by the argument
                                            sizeInBits = 8;

                                            while (bitLength.longValue() > sizeInBits)
                                            {
                                                sizeInBits *= 2;
                                            }

                                            // Get the argument alarm
                                            IntegerArgumentType.ValidRangeSet alarmType = icmd.getValidRangeSet();

                                            // Check if the argument has an alarm
                                            if (alarmType != null)
                                            {
                                                // Get the alarm range
                                                List<IntegerRangeType> alarmRange = alarmType.getValidRange();

                                                // Check if the alarm range exists
                                                if (alarmRange != null)
                                                {
                                                    // Store the minimum alarm value
                                                    minimum = alarmRange.get(0).getMinInclusive();

                                                    // Store the maximum alarm value
                                                    maximum = alarmRange.get(0).getMaxInclusive();
                                                }
                                            }

                                            isInteger = true;
                                        }
                                        // Check if the argument is a floating point data type
                                        else if (argType instanceof FloatArgumentType)
                                        {
                                            // Get the float argument attributes
                                            FloatArgumentType fcmd = (FloatArgumentType) argType;
                                            sizeInBits = fcmd.getSizeInBits().longValue();
                                            unitSet = fcmd.getUnitSet();

                                            // Get the argument alarm
                                            FloatArgumentType.ValidRangeSet alarmType = fcmd.getValidRangeSet();

                                            // Check if the argument has an alarm
                                            if (alarmType != null)
                                            {
                                                // Get the alarm range
                                                List<FloatRangeType> alarmRange = alarmType.getValidRange();

                                                // Check if the alarm range exists
                                                if (alarmRange != null)
                                                {
                                                    // Get the minimum value
                                                    Double min = alarmRange.get(0).getMinInclusive();

                                                    // Check if a minimum value exists
                                                    if (min != null)
                                                    {
                                                        // Get the minimum alarm value
                                                        minimum = String.valueOf(min);
                                                    }

                                                    // Get the maximum value
                                                    Double max = alarmRange.get(0).getMaxInclusive();

                                                    // Check if a maximum value exists
                                                    if (max != null)
                                                    {
                                                        // Get the maximum alarm value
                                                        maximum = String.valueOf(max);
                                                    }
                                                }
                                            }

                                            isFloat = true;
                                        }
                                        // Check if the argument is a string data type
                                        else if (argType instanceof StringDataType)
                                        {
                                            // Get the string argument attributes
                                            StringDataType scmd = (StringDataType) argType;
                                            sizeInBits = scmd.getCharacterWidth().longValue() * 8;
                                            unitSet = scmd.getUnitSet();
                                            isString = true;
                                        }
                                        // Check if the argument is an enumerated data type
                                        else if (argType instanceof EnumeratedDataType)
                                        {
                                            EnumeratedDataType ecmd = (EnumeratedDataType) argType;
                                            EnumerationList enumList = ecmd.getEnumerationList();

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
                                                        // Add the separator for the
                                                        // enumerations
                                                        enumeration += ", ";
                                                    }

                                                    // Begin building this enumeration
                                                    enumeration += enumType.getValue()
                                                                   + " | "
                                                                   + enumType.getLabel();
                                                }

                                                bitLength = ecmd.getIntegerDataEncoding().getSizeInBits();
                                                unitSet = ecmd.getUnitSet();

                                                // Check if integer encoding is set to
                                                // 'unsigned'
                                                if (ecmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                                                {
                                                    isUnsigned = true;
                                                }

                                                // Determine the smallest integer size that
                                                // contains the number of bits occupied by the
                                                // argument
                                                sizeInBits = 8;

                                                while (bitLength.longValue() > sizeInBits)
                                                {
                                                    sizeInBits *= 2;
                                                }

                                                isInteger = true;
                                            }
                                        }

                                        // Get the name of the data type from the data type table
                                        // that matches the base type and size of the parameter
                                        dataType = getMatchingDataType(sizeInBits / 8,
                                                                       isInteger,
                                                                       isUnsigned,
                                                                       isFloat,
                                                                       isString,
                                                                       dataTypeHandler);

                                        // Check if the description exists
                                        if (argType.getLongDescription() != null)
                                        {
                                            // Store the description
                                            description = argType.getLongDescription();
                                        }

                                        // Check if the units exists
                                        if (unitSet != null)
                                        {
                                            List<UnitType> unitType = unitSet.getUnit();

                                            // Check if the units is set
                                            if (!unitType.isEmpty())
                                            {
                                                // Store the units
                                                units = unitType.get(0).getContent();
                                            }
                                        }

                                        // Check if the command argument index is within the range
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

                                            // Check if the command argument bit length is present
                                            // and it doesn't match the data type size
                                            if (acmdArg.getBitLength() != -1
                                                && bitLength != null
                                                && bitLength.longValue() != sizeInBits)
                                            {
                                                // Store the command argument bit length
                                                rowData[acmdArg.getBitLength()] = bitLength.toString();
                                            }

                                            // Check if the command argument enumeration is present
                                            if (acmdArg.getEnumeration() != -1
                                                && enumeration != null)
                                            {
                                                // Store the command argument enumeration
                                                rowData[acmdArg.getEnumeration()] = enumeration;
                                            }

                                            // Check if the command argument description is present
                                            if (acmdArg.getDescription() != -1
                                                && description != null)
                                            {
                                                // Store the command argument description
                                                rowData[acmdArg.getDescription()] = description;
                                            }

                                            // Check if the command argument units is present
                                            if (acmdArg.getUnits() != -1 && units != null)
                                            {
                                                // Store the command argument units
                                                rowData[acmdArg.getUnits()] = units;
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
                    if (!metaCmd.isAbstract())
                    {
                        // Check if (non-header) command tables are to be stored
                        if (!onlyCmdToStruct)
                        {
                            // Add the new row to the table definition
                            tableDefn.addData(rowData);
                        }
                    }
                    // The command is a header type. Convert it to a structure unless importing
                    // only a single command table
                    else if (structureTypeDefn != null)
                    {
                        // Create a structure table definition to contain this command header
                        TableDefinition structTableDefn = new TableDefinition(tableName
                                                                              + (abstractCount == 0
                                                                                                    ? ""
                                                                                                    : "_" + abstractCount),
                                                                              system.getLongDescription());
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
                                                                                              InputDataType.SYSTEM_PATH,
                                                                                              Math.min(Math.max(systemPath.length(),
                                                                                                                5),
                                                                                                       40),
                                                                                              false,
                                                                                              ApplicabilityType.ALL,
                                                                                              systemPath));

                        // Add the command header structure table definition to the list
                        tableDefinitions.add(structTableDefn);
                    }
                }
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
                                                                            InputDataType.SYSTEM_PATH,
                                                                            Math.min(Math.max(systemPath.length(),
                                                                                              5),
                                                                                     40),
                                                                            false,
                                                                            ApplicabilityType.ALL,
                                                                            systemPath));

            // Add the command table definition to the list
            tableDefinitions.add(tableDefn);
        }
    }

    /**********************************************************************************************
     * Export the project in XTCE XML format to the specified file
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
     * @param includeProjectFields
     *            true to include the project-level data field definitions in the export file
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
     *            [0] endianess (EndianType.BIG_ENDIAN or EndianType.LITTLE_ENDIAN) <br>
     *            [1] are the telemetry and command headers big endian (true or false) <br>
     *            [2] version attribute <br>
     *            [3] validation status attribute <br>
     *            [4] first level classification attribute <br>
     *            [5] second level classification attribute <br>
     *            [6] third level classification attribute
     *
     * @return true if an error occurred preventing exporting the project to the file
     *********************************************************************************************/
    @Override
    public boolean exportToFile(FileEnvVar exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                boolean includeReservedMsgIDs,
                                boolean includeProjectFields,
                                boolean includeVariablePaths,
                                CcddVariableSizeAndConversionHandler variableHandler,
                                String[] separators,
                                Object... extraInfo)
    {
        boolean errorFlag = false;

        try
        {
            // Convert the table data into XTCE XML format
            convertTablesToXTCE(tableNames,
                                variableHandler,
                                separators,
                                (EndianType) extraInfo[0],
                                (boolean) extraInfo[1],
                                (String) extraInfo[2],
                                (String) extraInfo[3],
                                (String) extraInfo[4],
                                (String) extraInfo[5],
                                (String) extraInfo[6]);

            // Output the XML to the specified file
            marshaller.marshal(project, exportFile);
        }
        catch (JAXBException je)
        {
            // Inform the user that the database export failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot export as XTCE XML to file<br>'</b>"
                                                              + exportFile.getAbsolutePath()
                                                              + "<b>'; cause '"
                                                              + je.getMessage()
                                                              + "'",
                                                      "File Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
        catch (Exception e)
        {
            // Display a dialog providing details on the unanticipated error
            CcddUtilities.displayException(e, parent);
            errorFlag = true;
        }

        return errorFlag;
    }

    /**********************************************************************************************
     * Convert the project database contents to XTCE XML format
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
     * @param endianess
     *            EndianType.BIG_ENDIAN for big endian, EndianType.LITTLE_ENDIAN for little endian
     *
     * @param isHeaderBigEndian
     *            true if the telemetry and command headers are always big endian (e.g., as with
     *            CCSDS)
     *
     * @param version
     *            version attribute
     *
     * @param validationStatus
     *            validation status attribute
     *
     * @param classification1
     *            first level classification attribute
     *
     * @param classification2
     *            second level classification attribute
     *
     * @param classification3
     *            third level classification attribute
     *********************************************************************************************/
    private void convertTablesToXTCE(String[] tableNames,
                                     CcddVariableSizeAndConversionHandler variableHandler,
                                     String[] separators,
                                     EndianType endianess,
                                     boolean isHeaderBigEndian,
                                     String version,
                                     String validationStatus,
                                     String classification1,
                                     String classification2,
                                     String classification3)
    {
        this.endianess = endianess;
        this.isHeaderBigEndian = isHeaderBigEndian;

        // Store the attributes
        versionAttr = version;
        validationStatusAttr = validationStatus;
        classification1Attr = classification1;
        classification2Attr = classification2;
        classification3Attr = classification3;

        // Create the root space system
        SpaceSystemType rootSystem = addSpaceSystem(null,
                                                    dbControl.getProjectName(),
                                                    dbControl.getDatabaseDescription(dbControl.getDatabaseName()),
                                                    classification1Attr,
                                                    validationStatusAttr,
                                                    versionAttr);

        // Set the project's build information
        AuthorSet author = factory.createHeaderTypeAuthorSet();
        author.getAuthor().add(dbControl.getUser());
        rootSystem.getHeader().setAuthorSet(author);
        NoteSet note = factory.createHeaderTypeNoteSet();
        note.getNote().add("Generated by CCDD " + ccddMain.getCCDDVersionInformation());
        note.getNote().add("Date: " + new Date().toString());
        note.getNote().add("Project: " + dbControl.getProjectName());
        note.getNote().add("Host: " + dbControl.getServer());
        note.getNote().add("Endianess: " + (endianess == EndianType.BIG_ENDIAN
                                                                               ? "big"
                                                                               : "little"));
        rootSystem.getHeader().setNoteSet(note);

        // Get the names of the tables representing the CCSDS telemetry and command headers
        tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    InputDataType.XML_TLM_HDR);
        cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    InputDataType.XML_CMD_HDR);

        // Get the telemetry and command header argument column names for the application ID and
        // the command function code. These are stored as project-level data fields
        applicationIDName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                       InputDataType.XML_APP_ID);
        cmdFuncCodeName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                     InputDataType.XML_FUNC_CODE);

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
        AncillaryDataSet ancillarySet = factory.createDescriptionTypeAncillaryDataSet();

        // Check if the telemetry header table name is defined
        if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
        {
            // Store the telemetry header table name
            AncillaryData tlmHdrTblValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
            tlmHdrTblValue.setName(InputDataType.XML_TLM_HDR.getInputName());
            tlmHdrTblValue.setValue(tlmHeaderTable);
            ancillarySet.getAncillaryData().add(tlmHdrTblValue);
        }

        // Check if the command header table name is defined
        if (cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Store the command header table name
            AncillaryData cmdHdrTblValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
            cmdHdrTblValue.setName(InputDataType.XML_CMD_HDR.getInputName());
            cmdHdrTblValue.setValue(cmdHeaderTable);
            ancillarySet.getAncillaryData().add(cmdHdrTblValue);
        }

        // Store the application ID variable name
        AncillaryData appIDNameValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
        appIDNameValue.setName(InputDataType.XML_APP_ID.getInputName());
        appIDNameValue.setValue(applicationIDName);
        ancillarySet.getAncillaryData().add(appIDNameValue);

        // Store the command function code variable name
        AncillaryData cmdCodeNameValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
        cmdCodeNameValue.setName(InputDataType.XML_FUNC_CODE.getInputName());
        cmdCodeNameValue.setValue(cmdFuncCodeName);
        ancillarySet.getAncillaryData().add(cmdCodeNameValue);
        project.getValue().setAncillaryDataSet(ancillarySet);

        // Add the project's space systems, parameters, and commands
        buildSpaceSystems(tableNames, variableHandler, separators);
    }

    /**********************************************************************************************
     * Build the space systems
     *
     * @param node
     *            current tree node
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *********************************************************************************************/
    private void buildSpaceSystems(String[] tableNames,
                                   CcddVariableSizeAndConversionHandler variableHandler,
                                   String[] separators)
    {
        List<StructureMemberList> memberLists = new ArrayList<StructureMemberList>();

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the information from the database for the specified table
            TableInformation tableInfo = dbTable.loadTableData(tableName,
                                                               true,
                                                               false,
                                                               true,
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
                    String applicationID = fieldHandler.getFieldValue(tableName,
                                                                      InputDataType.MESSAGE_ID);

                    // Get the name of the system to which this table belongs from the table's
                    // system path data field (if present)
                    String systemPath = fieldHandler.getFieldValue(tableName,
                                                                   InputDataType.SYSTEM_PATH);

                    // Initialize the parent system to be the root (top-level) system
                    SpaceSystemType parentSystem = project.getValue();

                    // Check if a system path exists
                    if (systemPath != null)
                    {
                        // Step through each system name in the path
                        for (String systemName : systemPath.split("\\s*/\\s*"))
                        {
                            // Check if the system name isn't blank (this ignores a beginning '/'
                            // if present)
                            if (!systemName.isEmpty())
                            {
                                // Search the existing space systems for one with this system's
                                // name (if none exists then use the root system's name)
                                SpaceSystemType existingSystem = getSpaceSystemByName(systemName,
                                                                                      parentSystem);

                                // Set the parent system to the existing system if found, else
                                // create a new space system using the name from the table's system
                                // path data field
                                parentSystem = existingSystem == null
                                                                      ? addSpaceSystem(parentSystem,
                                                                                       systemName,
                                                                                       null,
                                                                                       classification2Attr,
                                                                                       validationStatusAttr,
                                                                                       versionAttr)
                                                                      : existingSystem;
                            }
                        }
                    }

                    // Add the space system
                    parentSystem = addSpaceSystem(parentSystem,
                                                  tableName,
                                                  tableInfo.getDescription(),
                                                  classification3Attr,
                                                  validationStatusAttr,
                                                  versionAttr);

                    // Check if this is a structure table
                    if (typeDefn.isStructure())
                    {
                        MemberList memberList = factory.createAggregateDataTypeMemberList();

                        // Get the default column indices
                        int varColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                        int typeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                        int sizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                        int bitColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                        int enumColumn = typeDefn.getColumnIndexByInputType(InputDataType.ENUMERATION);
                        int descColumn = typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION);
                        int unitsColumn = typeDefn.getColumnIndexByInputType(InputDataType.UNITS);
                        int minColumn = typeDefn.getColumnIndexByInputType(InputDataType.MINIMUM);
                        int maxColumn = typeDefn.getColumnIndexByInputType(InputDataType.MAXIMUM);

                        // Check if this is the command header structure. In order for it to be
                        // referenced as the header for the command tables it must be converted
                        // into the same format as a command table, then rendered into XTCE XML
                        if (tableName.equals(cmdHeaderTable))
                        {
                            // Store the command header's path
                            cmdHeaderPath = systemPath;

                            // Set the number of argument columns per command argument
                            int columnsPerArg = 9;

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
                            for (String[] rowData : tableInfo.getData())
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

                            // Add the command header to the space system
                            addSpaceSystemCommands(parentSystem,
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
                            // Export the parameter container for this structure
                            addParameterContainer(parentSystem,
                                                  tableInfo,
                                                  varColumn,
                                                  typeColumn,
                                                  sizeColumn,
                                                  tableName.equals(tlmHeaderTable),
                                                  applicationID);

                            // Step through each row in the structure table
                            for (String[] rowData : tableInfo.getData())
                            {
                                // Check if the variable isn't an array member (the array
                                // definition is used to define the array)
                                if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                                {
                                    // Add the variable to the space system
                                    addParameter(parentSystem,
                                                 rowData[varColumn],
                                                 rowData[typeColumn],
                                                 rowData[sizeColumn],
                                                 rowData[bitColumn],
                                                 (enumColumn != -1
                                                  && !rowData[enumColumn].isEmpty()
                                                                                    ? rowData[enumColumn]
                                                                                    : null),
                                                 (unitsColumn != -1
                                                  && !rowData[unitsColumn].isEmpty()
                                                                                     ? rowData[unitsColumn]
                                                                                     : null),
                                                 (minColumn != -1
                                                  && !rowData[minColumn].isEmpty()
                                                                                   ? rowData[minColumn]
                                                                                   : null),
                                                 (maxColumn != -1
                                                  && !rowData[maxColumn].isEmpty()
                                                                                   ? rowData[maxColumn]
                                                                                   : null),
                                                 (descColumn != -1
                                                  && !rowData[descColumn].isEmpty()
                                                                                    ? rowData[descColumn]
                                                                                    : null),
                                                 (dataTypeHandler.isString(rowData[typeColumn])
                                                  && !rowData[sizeColumn].isEmpty()
                                                                                    ? Integer.valueOf(rowData[sizeColumn].replaceAll("^.*(\\d+)$", "$1"))
                                                                                    : 1));

                                    // Use the variable name to create an aggregate list member and
                                    // add it to the member list. The list is used if this
                                    // structure is referenced as a data type in another structure
                                    Member member = new Member();
                                    member.setName(rowData[varColumn]);
                                    member.setTypeRef("/" + project.getValue().getName()
                                                      + (systemPath == null || systemPath.isEmpty()
                                                                                                    ? ""
                                                                                                    : "/" + systemPath)
                                                      + "/" + tableName
                                                      + "/"
                                                      + (dataTypeHandler.isPrimitive(rowData[typeColumn])
                                                                                                          ? rowData[varColumn]
                                                                                                          : tableName
                                                                                                            + ","
                                                                                                            + rowData[typeColumn]
                                                                                                            + "."
                                                                                                            + rowData[varColumn])
                                                      + TYPE);
                                    memberList.getMember().add(member);
                                }
                            }

                            // Check if any variables exists in the structure table
                            if (!memberList.getMember().isEmpty())
                            {
                                // Create a structure member list using the table's aggregate
                                // member list
                                memberLists.add(new StructureMemberList(tableName, memberList));
                            }
                        }
                    }
                    // This is a command table
                    else
                    {
                        // Get the list containing the associated column indices for each argument
                        // grouping
                        commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

                        // Add the command(s) from this table to the parent system
                        addSpaceSystemCommands(parentSystem,
                                               tableInfo.getData(),
                                               typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME),
                                               typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_CODE),
                                               typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION),
                                               false,
                                               applicationID);
                    }
                }
            }
        }

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the space system for this table
            SpaceSystemType spaceSystem = getSpaceSystemByName(tableName, project.getValue());

            // Check if the system was found and it has telemetry data
            if (spaceSystem != null && spaceSystem.getTelemetryMetaData() != null)
            {
                // Step through each parameter type
                for (NameDescriptionType type : spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType())
                {
                    // Check if the type is an aggregate (i.e., a structure reference)
                    if (type instanceof AggregateDataType)
                    {
                        // Remove the trailing text added to the table name that flags it as a type
                        // or array
                        String typeName = type.getName().substring(0, type.getName().lastIndexOf("_"));

                        // Step through each structure member list
                        for (StructureMemberList structMemList : memberLists)
                        {
                            // Check if the aggregate refers to this structure
                            if (typeName.equals(structMemList.structureName))
                            {
                                // Set the aggregate's member list to this structure member list
                                // and stop searching
                                ((AggregateDataType) type).setMemberList(structMemList.memberList);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Create a new space system as a child of the specified space system. If the specified system
     * is null then this is the root space system
     *
     * @param parentSystem
     *            parent space system for the new system; null for the root space system
     *
     * @param systemName
     *            name for the new space system
     *
     * @param description
     *            space system description
     *
     * @param classification
     *            XML document classification
     *
     * @param validationStatus
     *            XML document validation status
     *
     * @param version
     *            XML document version
     *
     * @return Reference to the new space system
     *********************************************************************************************/
    private SpaceSystemType addSpaceSystem(SpaceSystemType parentSystem,
                                           String systemName,
                                           String description,
                                           String classification,
                                           String validationStatus,
                                           String version)
    {
        // Create the new space system and set the name attribute
        SpaceSystemType childSystem = factory.createSpaceSystemType();
        childSystem.setName(systemName);

        // Check if a description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            childSystem.setLongDescription(description);
        }

        // Set the new space system's header attributes
        setHeader(childSystem,
                  classification,
                  validationStatus,
                  version,
                  (parentSystem == null
                                        ? new Date().toString()
                                        : null));

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

        return childSystem;
    }

    /**********************************************************************************************
     * Get the reference to the space system with the specified name, starting at the specified
     * space system
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @param system
     *            space system in which to start the search
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
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @param spaceSystem
     *            current space system to check
     *
     * @param foundSystem
     *            space system that matches the search name; null if no match has been found
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
     * Set the space system header attributes
     *
     * @param spaceSystem
     *            space system
     *
     * @param classification
     *            XML document classification
     *
     * @param validationStatus
     *            XML document validation status
     *
     * @param version
     *            XML document version
     *
     * @param date
     *            XML document creation time and date
     *********************************************************************************************/
    private void setHeader(SpaceSystemType spaceSystem,
                           String classification,
                           String validationStatus,
                           String version,
                           String date)
    {
        HeaderType header = factory.createHeaderType();
        header.setClassification(classification);
        header.setValidationStatus(validationStatus);
        header.setVersion(version);
        header.setDate(date);
        spaceSystem.setHeader(header);
    }

    /**********************************************************************************************
     * Create the space system telemetry metadata
     *
     * @param spaceSystem
     *            space system
     *********************************************************************************************/
    private void createTelemetryMetadata(SpaceSystemType spaceSystem)
    {
        spaceSystem.setTelemetryMetaData(factory.createTelemetryMetaDataType());
        spaceSystem.getTelemetryMetaData().setParameterSet(factory.createParameterSetType());
        spaceSystem.getTelemetryMetaData().setParameterTypeSet(factory.createParameterTypeSetType());
    }

    /**********************************************************************************************
     * Add the parameter container
     *
     * @param spaceSystem
     *            space system
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
     * @param isTlmHeader
     *            true if this table represents the telemetry header
     *
     * @param applicationID
     *            application ID
     *********************************************************************************************/
    private void addParameterContainer(SpaceSystemType spaceSystem,
                                       TableInformation tableInfo,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       boolean isTlmHeader,
                                       String applicationID)
    {
        String tlmHeaderTableInstance = null;
        ContainerSetType seqContainerSet = null;
        SequenceContainerType seqContainer = null;
        EntryListType entryList = factory.createEntryListType();

        // Get the name of the system to which this referenced structure belongs
        String refSystemName = fieldHandler.getFieldValue(tableInfo.getTablePath(),
                                                          InputDataType.SYSTEM_PATH);

        // Step through each row of data in the structure table
        for (String[] rowData : tableInfo.getData())
        {
            // Check if the parameter is an array
            if (!rowData[sizeColumn].isEmpty())
            {
                // Check if this is the array definition (array members are ignored)
                if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                {
                    DimensionList dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                    // Set the array dimension start index (always 0)
                    IntegerValueType startVal = factory.createIntegerValueType();
                    startVal.setFixedValue(String.valueOf(0));

                    // Step through each array dimension
                    for (int arrayDim : ArrayVariable.getArrayIndexFromSize(rowData[sizeColumn]))
                    {
                        // Create the dimension and set the start and end indices (the end index is
                        // the number of elements in this array dimension)
                        Dimension dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                        IntegerValueType endVal = factory.createIntegerValueType();
                        endVal.setFixedValue(String.valueOf(arrayDim));
                        dim.setStartingIndex(startVal);
                        dim.setEndingIndex(endVal);
                        dimList.getDimension().add(dim);
                    }

                    // Store the array parameter array reference in the list
                    ArrayParameterRefEntryType arrayRef = factory.createArrayParameterRefEntryType();
                    arrayRef.setParameterRef(getTypeNameByDataType(rowData[varColumn],
                                                                   rowData[typeColumn],
                                                                   dataTypeHandler));
                    arrayRef.setDimensionList(dimList);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(arrayRef);
                }
            }
            // Check if this parameter has a primitive data type (i.e., it isn't an instance of a
            // structure)
            else if (dataTypeHandler.isPrimitive(rowData[typeColumn]))
            {
                // Check if this isn't an array definition
                if (rowData[sizeColumn].isEmpty() || ArrayVariable.isArrayMember(rowData[varColumn]))
                {
                    // Store the parameter reference in the list
                    ParameterRefEntryType parameterRef = factory.createParameterRefEntryType();
                    parameterRef.setParameterRef(rowData[varColumn]);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(parameterRef);
                }
            }
            // This is a structure data type. Check if the reference isn't to the telemetry header
            // table
            else if (!rowData[typeColumn].equals(tlmHeaderTable))
            {
                // Store the structure reference in the list
                ContainerRefEntryType containerType = factory.createContainerRefEntryType();
                containerType.setContainerRef("/" + project.getValue().getName()
                                              + (refSystemName == null
                                                 || refSystemName.isEmpty()
                                                                            ? ""
                                                                            : "/" + refSystemName)
                                              + "/" + spaceSystem.getName() + ","
                                              + rowData[typeColumn] + "." + rowData[varColumn]
                                              + "/" + spaceSystem.getName() + ","
                                              + rowData[typeColumn] + "." + rowData[varColumn]);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerType);
            }
            // This is a reference to the telemetry header table
            else
            {
                // Build the reference to this instance of the telemetry header table
                tlmHeaderTableInstance = "/" + project.getValue().getName()
                                         + (refSystemName == null
                                            || refSystemName.isEmpty()
                                                                       ? ""
                                                                       : "/" + refSystemName)
                                         + "/" + spaceSystem.getName() + ","
                                         + rowData[typeColumn] + "." + rowData[varColumn]
                                         + "/" + spaceSystem.getName() + ","
                                         + rowData[typeColumn] + "." + rowData[varColumn];
            }
        }

        // Check if any parameters exist
        if (!entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().isEmpty())
        {
            // Check if the parameter sequence container hasn't been created
            if (seqContainer == null)
            {
                // Check if the parameter sequence container set hasn't been created
                if (seqContainerSet == null)
                {
                    // Create the parameter sequence container set
                    seqContainerSet = factory.createContainerSetType();
                }

                // Create the parameter sequence container and set the name
                seqContainer = factory.createSequenceContainerType();

                // Check if this is the telemetry header
                if (isTlmHeader)
                {
                    seqContainer.setName(tlmHeaderTable);
                    seqContainer.setAbstract(true);
                }
                // Not the telemetry header
                else
                {
                    seqContainer.setName(spaceSystem.getName());

                    // Check if this is a root structure (instance structures don't require a
                    // reference to the telemetry header) and the telemetry header table name is
                    // defined
                    if (tableInfo.isRootStructure()
                        && tlmHeaderTableInstance != null
                        && !tlmHeaderTableInstance.isEmpty())
                    {
                        // Create a reference to the telemetry header table instance
                        BaseContainer baseContainer = factory.createSequenceContainerTypeBaseContainer();
                        baseContainer.setContainerRef(tlmHeaderTableInstance);
                        RestrictionCriteria restrictCriteria = factory.createSequenceContainerTypeBaseContainerRestrictionCriteria();
                        ComparisonList compList = factory.createMatchCriteriaTypeComparisonList();
                        ComparisonType compType = factory.createComparisonType();
                        compType.setParameterRef(applicationIDName);
                        compType.setValue(applicationID);
                        compList.getComparison().add(compType);
                        restrictCriteria.setComparisonList(compList);
                        baseContainer.setRestrictionCriteria(restrictCriteria);
                        seqContainer.setBaseContainer(baseContainer);
                    }
                }
            }

            // Store the parameters in the parameter sequence container
            seqContainer.setEntryList(entryList);
            seqContainerSet.getSequenceContainer().add(seqContainer);
        }

        // Check if any parameters exist
        if (seqContainerSet != null)
        {
            // Check if the telemetry metadata doesn't exit for this system
            if (spaceSystem.getTelemetryMetaData() == null)
            {
                // Create the telemetry metadata
                createTelemetryMetadata(spaceSystem);
            }

            // Add the parameters to the system
            spaceSystem.getTelemetryMetaData().setContainerSet(seqContainerSet);
        }
    }

    /**********************************************************************************************
     * Add a telemetry parameter to the telemetry metadata
     *
     * @param spaceSystem
     *            space system
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
     *            enumeration in the format <enum label>|<enum value>[|...][,...]; null to not
     *            specify
     *
     * @param units
     *            parameter units
     *
     * @param minimum
     *            minimum parameter value
     *
     * @param maximum
     *            maximum parameter value
     *
     * @param description
     *            parameter description
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *********************************************************************************************/
    private void addParameter(SpaceSystemType spaceSystem,
                              String parameterName,
                              String dataType,
                              String arraySize,
                              String bitLength,
                              String enumeration,
                              String units,
                              String minimum,
                              String maximum,
                              String description,
                              int stringSize)
    {
        // Check if this system doesn't yet have its telemetry metadata created
        if (spaceSystem.getTelemetryMetaData() == null)
        {
            // Create the telemetry metadata
            createTelemetryMetadata(spaceSystem);
        }

        // Check if a data type is provided
        if (dataType != null)
        {
            // Get the parameter's data type information
            setParameterDataType(spaceSystem,
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

        // Create the parameter. This links the parameter name with the parameter reference type
        ParameterSetType parameterSet = spaceSystem.getTelemetryMetaData().getParameterSet();
        Parameter parameter = factory.createParameterSetTypeParameter();
        parameter.setName(parameterName);
        parameter.setParameterTypeRef((dataTypeHandler.isPrimitive(dataType)
                                                                             ? parameterName
                                                                             : spaceSystem.getName()
                                                                               + ","
                                                                               + dataType
                                                                               + "."
                                                                               + parameterName)
                                      + (arraySize.isEmpty()
                                                             ? TYPE
                                                             : ARRAY));
        parameterSet.getParameterOrParameterRef().add(parameter);
    }

    /**********************************************************************************************
     * Create the space system command metadata
     *
     * @param spaceSystem
     *            space system
     *********************************************************************************************/
    private void createCommandMetadata(SpaceSystemType spaceSystem)
    {
        spaceSystem.setCommandMetaData(factory.createCommandMetaDataType());
        spaceSystem.getCommandMetaData().setArgumentTypeSet(factory.createArgumentTypeSetType());
        spaceSystem.getCommandMetaData().setMetaCommandSet(factory.createCommandMetaDataTypeMetaCommandSet());
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified space system
     *
     * @param spaceSystem
     *            parent space system for this node
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
    private void addSpaceSystemCommands(SpaceSystemType spaceSystem,
                                        String[][] tableData,
                                        int cmdNameColumn,
                                        int cmdCodeColumn,
                                        int cmdDescColumn,
                                        boolean isCmdHeader,
                                        String applicationID)
    {
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
                String commandName = null;
                String cmdFuncCode = null;
                String commandDescription = null;
                List<String> argumentNames = new ArrayList<String>();
                List<String> argArraySizes = new ArrayList<String>();

                // Check if this system doesn't yet have its command metadata created
                if (spaceSystem.getCommandMetaData() == null)
                {
                    // Create the command metadata
                    createCommandMetadata(spaceSystem);
                }

                // Store the command name
                commandName = rowData[cmdNameColumn];

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
                    String minimum = null;
                    String maximum = null;
                    String units = null;
                    String description = null;
                    int stringSize = 1;

                    // Check if the command argument name and data type exist
                    if (cmdArg.getName() != -1
                        && !rowData[cmdArg.getName()].isEmpty()
                        && cmdArg.getDataType() != -1 &&
                        !rowData[cmdArg.getDataType()].isEmpty())
                    {
                        String uniqueID = "";
                        int dupCount = 0;

                        // Store the command argument name and data type
                        argumentName = rowData[cmdArg.getName()];
                        dataType = rowData[cmdArg.getDataType()];

                        // Check if the description column exists
                        if (cmdArg.getDescription() != -1 && !rowData[cmdArg.getDescription()].isEmpty())
                        {
                            // Store the command argument description
                            description = rowData[cmdArg.getDescription()];
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

                        // Check if the minimum column exists
                        if (cmdArg.getMinimum() != -1 && !rowData[cmdArg.getMinimum()].isEmpty())
                        {
                            // Store the command argument minimum value
                            minimum = rowData[cmdArg.getMinimum()];
                        }

                        // Check if the maximum column exists
                        if (cmdArg.getMaximum() != -1 && !rowData[cmdArg.getMaximum()].isEmpty())
                        {
                            // Store the command argument maximum value
                            maximum = rowData[cmdArg.getMaximum()];
                        }

                        // Step through the list of argument names used so far
                        for (String argName : argumentNames)
                        {
                            // Check if the current argument name matches an existing one
                            if (argumentName.equals(argName))
                            {
                                // Increment the duplicate name count
                                dupCount++;
                            }
                        }

                        // Check if a duplicate argument name exists
                        if (dupCount != 0)
                        {
                            // Set the unique ID to the counter value
                            uniqueID = String.valueOf(dupCount + 1);
                        }

                        // Add the name and array status to the lists
                        argumentNames.add(argumentName);
                        argArraySizes.add(arraySize);

                        // Set the command argument data type information
                        NameDescriptionType type = setArgumentDataType(spaceSystem,
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

                        // Add the command to the command space system
                        ArgumentTypeSetType argument = spaceSystem.getCommandMetaData().getArgumentTypeSet();
                        argument.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(type);
                    }
                }

                // Add the command metadata set information
                addCommand(spaceSystem,
                           commandName,
                           cmdFuncCode,
                           applicationID,
                           isCmdHeader,
                           argumentNames,
                           argArraySizes,
                           commandDescription);
            }
        }
    }

    /**********************************************************************************************
     * Add a command metadata set to the command metadata
     *
     * @param spaceSystem
     *            space system
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
     * @param argumentNames
     *            list of command argument names
     *
     * @param argArraySizes
     *            list of of command argument array sizes; the list item is null or blank if the
     *            corresponding argument isn't an array
     *
     * @param description
     *            description of the command
     *********************************************************************************************/
    private void addCommand(SpaceSystemType spaceSystem,
                            String commandName,
                            String cmdFuncCode,
                            String applicationID,
                            boolean isCmdHeader,
                            List<String> argumentNames,
                            List<String> argArraySizes,
                            String description)
    {
        MetaCommandSet commandSet = spaceSystem.getCommandMetaData().getMetaCommandSet();
        MetaCommandType command = factory.createMetaCommandType();

        // Check is a command name exists
        if (commandName != null && !commandName.isEmpty())
        {
            // Set the command name attribute
            command.setName(commandName);
        }

        // Check is a command description exists
        if (description != null && !description.isEmpty())
        {
            // Set the command description attribute
            command.setLongDescription(description);
        }

        // Check if the command has any arguments
        if (!argumentNames.isEmpty())
        {
            int index = 0;
            ArgumentList argList = factory.createMetaCommandTypeArgumentList();
            CommandContainerType cmdContainer = factory.createCommandContainerType();
            CommandContainerEntryListType entryList = factory.createCommandContainerEntryListType();

            // Step through each argument
            for (String argumentName : argumentNames)
            {
                // Set the flag to indicate that the argument is an array
                boolean isArray = argArraySizes.get(index) != null
                                  && !argArraySizes.get(index).isEmpty();

                // Add the argument to the the command's argument list
                Argument arg = new Argument();
                arg.setName(argumentName);
                arg.setArgumentTypeRef(argumentName
                                       + (isArray
                                                  ? ARRAY
                                                  : TYPE));
                argList.getArgument().add(arg);

                // Store the argument reference in the list
                ArgumentRefEntry argumentRef = factory.createCommandContainerEntryListTypeArgumentRefEntry();
                argumentRef.setArgumentRef(argumentName);
                JAXBElement<ArgumentRefEntry> argumentRefElem = factory.createCommandContainerEntryListTypeArgumentRefEntry(argumentRef);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(argumentRefElem);

                // Check if the command argument is an array
                if (isArray)
                {
                    DimensionList dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                    // Set the array dimension start index (always 0)
                    IntegerValueType startVal = factory.createIntegerValueType();
                    startVal.setFixedValue(String.valueOf(0));

                    // Step through each array dimension
                    for (int arrayDim : ArrayVariable.getArrayIndexFromSize(argArraySizes.get(index)))
                    {
                        // Create the dimension and set the start and end indices (the end index is
                        // the number of elements in this array dimension)
                        Dimension dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                        IntegerValueType endVal = factory.createIntegerValueType();
                        endVal.setFixedValue(String.valueOf(arrayDim));
                        dim.setStartingIndex(startVal);
                        dim.setEndingIndex(endVal);
                        dimList.getDimension().add(dim);
                    }

                    // Store the array parameter array reference in the list
                    ArrayParameterRefEntryType arrayRef = factory.createArrayParameterRefEntryType();
                    arrayRef.setParameterRef(argumentName);
                    arrayRef.setDimensionList(dimList);
                    JAXBElement<ArrayParameterRefEntryType> arrayRefElem = factory.createCommandContainerEntryListTypeArrayArgumentRefEntry(arrayRef);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(arrayRefElem);
                }

                index++;
            }

            // Check if this table represents the command header
            if (isCmdHeader)
            {
                // Set the abstract flag to indicate the command metadata represents a command
                // header
                command.setAbstract(true);
            }
            // Not the command header. Check if the command application ID command header table
            // name is provided
            else if (applicationID != null
                     && !applicationID.isEmpty()
                     && cmdHeaderTable != null
                     && !cmdHeaderTable.isEmpty())
            {
                // Create the reference to the base meta-command and set it to the empty base, in
                // case no command header is defined
                BaseMetaCommand baseCmd = factory.createMetaCommandTypeBaseMetaCommand();
                baseCmd.setMetaCommandRef("/" + project.getValue().getName()
                                          + (cmdHeaderPath == null
                                             || cmdHeaderPath.isEmpty()
                                                                        ? ""
                                                                        : "/" + cmdHeaderPath)
                                          + "/" + cmdHeaderTable
                                          + "/" + cmdHeaderTable);

                // Create the argument assignment list and store the application ID
                ArgumentAssignmentList argAssnList = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentList();
                ArgumentAssignment argAssn = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentListArgumentAssignment();
                argAssn.setArgumentName(applicationIDName);
                argAssn.setArgumentValue(applicationID);
                argAssnList.getArgumentAssignment().add(argAssn);

                // Check if a command code is provided
                if (cmdFuncCode != null && !cmdFuncCode.isEmpty())
                {
                    // Store the command code
                    argAssn = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentListArgumentAssignment();
                    argAssn.setArgumentName(cmdFuncCodeName);
                    argAssn.setArgumentValue(cmdFuncCode);
                    argAssnList.getArgumentAssignment().add(argAssn);
                }

                baseCmd.setArgumentAssignmentList(argAssnList);
                command.setBaseMetaCommand(baseCmd);
            }

            command.setArgumentList(argList);
            cmdContainer.setEntryList(entryList);
            command.setCommandContainer(cmdContainer);
        }

        commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand().add(command);
    }

    /**********************************************************************************************
     * Create the telemetry parameter data type and set the specified attributes
     *
     * @param spaceSystem
     *            space system
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
     *            enumeration in the format <enum label>|<enum value>[|...][,...]; null to not
     *            specify
     *
     * @param units
     *            parameter units; null to not specify
     *
     * @param minimum
     *            minimum parameter value; null to not specify
     *
     * @param maximum
     *            maximum parameter value; null to not specify
     *
     * @param description
     *            parameter description; null to not specify
     *
     * @param description
     *            parameter description; null or blank to not specify
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *********************************************************************************************/
    private void setParameterDataType(SpaceSystemType spaceSystem,
                                      String parameterName,
                                      String dataType,
                                      String arraySize,
                                      String bitLength,
                                      String enumeration,
                                      String units,
                                      String minimum,
                                      String maximum,
                                      String description,
                                      int stringSize)
    {
        NameDescriptionType parameterType = null;

        // TODO SET SIZE IN BITS IN BOTH THE TYPE AND THE ENCODING. WOULD USE ONE FOR BIT LENGTH
        // AND OTHER FOR DATA TYPE SIZE EXCEPT ONLY INTEGER TYPE HAS BOTH; ENUM ONLY HAS THE
        // ENCODING SIZE IN BITS

        // Check if the parameter is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes
            ArrayDataTypeType arrayType = factory.createArrayDataTypeType();
            String name = getTypeNameByDataType(parameterName, dataType, dataTypeHandler);
            arrayType.setName(name + ARRAY);
            arrayType.setArrayTypeRef(name + TYPE);
            arrayType.setNumberOfDimensions(BigInteger.valueOf(ArrayVariable.getArrayIndexFromSize(arraySize).length));

            // Set the parameter's array information
            spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(arrayType);
        }

        // Check if the parameter has a primitive data type
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Get the base data type corresponding to the primitive data type
            BasePrimitiveDataType baseDataType = getBaseDataType(dataType, dataTypeHandler);

            // Check if the a corresponding base data type exists
            if (baseDataType != null)
            {
                // Set the parameter units
                UnitSet unitSet = createUnitSet(units);

                // Check if enumeration parameters are provided
                if (enumeration != null && !enumeration.isEmpty())
                {
                    // Create an enumeration type and enumeration list, and add any extra
                    // enumeration parameters as column data
                    EnumeratedParameterType enumType = factory.createParameterTypeSetTypeEnumeratedParameterType();
                    EnumerationList enumList = createEnumerationList(spaceSystem, enumeration);

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
                        intEncodingType.setEncoding("unsigned");
                    }

                    // Set the bit order
                    intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                || (isHeaderBigEndian
                                                    && tlmHeaderTable.equals(TableInformation.getPrototypeName(spaceSystem.getName())))
                                                                                                                                        ? "mostSignificantBitFirst"
                                                                                                                                        : "leastSignificantBitFirst");

                    enumType.setIntegerDataEncoding(intEncodingType);

                    // Set the enumeration list and units attributes
                    enumType.setEnumerationList(enumList);
                    enumType.setUnitSet(unitSet);

                    parameterType = enumType;
                }
                // Not an enumeration
                else
                {
                    switch (baseDataType)
                    {
                        case INTEGER:
                            // Create an integer parameter and set its attributes
                            IntegerParameterType integerType = factory.createParameterTypeSetTypeIntegerParameterType();
                            integerType.setUnitSet(unitSet);
                            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                            // Check if the parameter has a bit length
                            if (bitLength != null && !bitLength.isEmpty())
                            {
                                // Set the size in bits to the value supplied
                                integerType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                                intEncodingType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                            }
                            // Not a bit-wise parameter
                            else
                            {
                                // Set the encoding type to indicate an unsigned integer
                                integerType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                                intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            }

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                integerType.setSigned(false);
                                intEncodingType.setEncoding("unsigned");
                            }

                            // Set the bit order
                            intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                        || (isHeaderBigEndian
                                                            && tlmHeaderTable.equals(TableInformation.getPrototypeName(spaceSystem.getName())))
                                                                                                                                                ? "mostSignificantBitFirst"
                                                                                                                                                : "leastSignificantBitFirst");

                            integerType.setIntegerDataEncoding(intEncodingType);

                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                IntegerRangeType range = factory.createIntegerRangeType();

                                // Check if a minimum value is specified
                                if (minimum != null && !minimum.isEmpty())
                                {
                                    // Set the minimum value
                                    range.setMinInclusive(minimum);
                                }

                                // Check if a maximum value is specified
                                if (maximum != null && !maximum.isEmpty())
                                {
                                    // Set the maximum value
                                    range.setMaxInclusive(maximum);
                                }

                                integerType.setValidRange(range);
                            }

                            parameterType = integerType;
                            break;

                        case FLOAT:
                            // Create a float parameter and set its attributes
                            FloatParameterType floatType = factory.createParameterTypeSetTypeFloatParameterType();
                            floatType.setUnitSet(unitSet);
                            floatType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            floatEncodingType.setEncoding("IEEE754_1985");
                            floatType.setFloatDataEncoding(floatEncodingType);

                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                FloatRangeType range = factory.createFloatRangeType();

                                // Check if a minimum value is specified
                                if (minimum != null && !minimum.isEmpty())
                                {
                                    // Set the minimum value
                                    range.setMinInclusive(Double.valueOf(minimum));
                                }

                                // Check if a maximum value is specified
                                if (maximum != null && !maximum.isEmpty())
                                {
                                    // Set the maximum value
                                    range.setMaxInclusive(Double.valueOf(maximum));
                                }

                                floatType.setValidRange(range);
                            }

                            parameterType = floatType;
                            break;

                        case STRING:
                            // Create a string parameter and set its attributes
                            StringParameterType stringType = factory.createParameterTypeSetTypeStringParameterType();
                            stringType.setUnitSet(unitSet);
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                            // Set the string's size in bits based on the number of characters in
                            // the string with each character occupying a single byte
                            IntegerValueType intValType = new IntegerValueType();
                            intValType.setFixedValue(String.valueOf(stringSize * 8));
                            SizeInBits sizeInBits = new SizeInBits();
                            sizeInBits.setFixed(intValType);
                            stringEncodingType.setSizeInBits(sizeInBits);
                            stringEncodingType.setEncoding("UTF-8");

                            stringType.setStringDataEncoding(stringEncodingType);
                            stringType.setCharacterWidth(BigInteger.valueOf(stringSize));
                            parameterType = stringType;
                            break;
                    }
                }
            }
        }
        // Structure data type
        else
        {
            // Create an aggregate type for the structure
            AggregateDataType aggregateType = factory.createAggregateDataType();
            parameterName = spaceSystem.getName() + "," + dataType + "." + parameterName;
            parameterType = aggregateType;
        }

        // Set the parameter type name
        parameterType.setName(parameterName + TYPE);

        // Check is a description exists
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            parameterType.setLongDescription(description);
        }

        // Set the parameter's data type information
        spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(parameterType);
    }

    /**********************************************************************************************
     * Set the command argument data type and set the specified attributes
     *
     * @param spaceSystem
     *            space system
     *
     * @param argumentName
     *            command argument name; null to not specify
     *
     * @param dataType
     *            command argument data type; null to not specify
     *
     * @param arraySize
     *            command argument array size; null or blank if the argument isn't an array
     *
     * @param bitLength
     *            command argument bit length
     *
     * @param enumeration
     *            command argument enumeration in the format <enum label>|<enum value>[|...][,...];
     *            null to not specify
     *
     * @param units
     *            command argument units; null to not specify
     *
     * @param minimum
     *            minimum parameter value; null to not specify
     *
     * @param maximum
     *            maximum parameter value; null to not specify
     *
     * @param description
     *            command argument description ; null to not specify
     *
     * @param stringSize
     *            string size in bytes; ignored if the command argument does not have a string data
     *            type
     *
     * @return Command description of the type corresponding to the primitive data type with the
     *         specified attributes set
     *
     * @param uniqueID
     *            text used to uniquely identify data types with the same name; blank if the data
     *            type has no name conflict
     *********************************************************************************************/
    private NameDescriptionType setArgumentDataType(SpaceSystemType spaceSystem,
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
                                                    String uniqueID)
    {
        BaseDataType commandDescription = null;

        // Check if the argument is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes
            ArrayDataTypeType arrayType = factory.createArrayDataTypeType();
            arrayType.setName(argumentName + ARRAY);
            arrayType.setNumberOfDimensions(BigInteger.valueOf(ArrayVariable.getArrayIndexFromSize(arraySize).length));
            arrayType.setArrayTypeRef(argumentName + TYPE);

            // Set the argument's array information
            spaceSystem.getCommandMetaData().getArgumentTypeSet().getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(arrayType);
        }

        // Get the base data type corresponding to the primitive data type
        BasePrimitiveDataType baseDataType = getBaseDataType(dataType, dataTypeHandler);

        // Check if the a corresponding base data type exists
        if (baseDataType != null)
        {
            UnitSet unitSet = null;

            // Check if units is provided
            if (units != null && !units.isEmpty())
            {
                // Set the command units
                unitSet = createUnitSet(units);
            }

            // Check if enumeration parameters are provided
            if (enumeration != null && !enumeration.isEmpty())
            {
                // Create an enumeration type and enumeration list, and add any extra enumeration
                // parameters as column data
                EnumeratedDataType enumType = factory.createEnumeratedDataType();
                EnumerationList enumList = createEnumerationList(spaceSystem, enumeration);

                // Set the integer encoding (the only encoding available for an enumeration) and
                // the size in bits
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

                // Set the enumeration list and units attributes
                enumType.setEnumerationList(enumList);
                enumType.setUnitSet(unitSet);

                // Check if the data type is an unsigned integer
                if (dataTypeHandler.isUnsignedInt(dataType))
                {
                    // Set the encoding type to indicate an unsigned integer
                    intEncodingType.setEncoding("unsigned");
                }

                // Set the bit order
                intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                            || (isHeaderBigEndian
                                                && cmdHeaderTable.equals(spaceSystem.getName()))
                                                                                                 ? "mostSignificantBitFirst"
                                                                                                 : "leastSignificantBitFirst");

                enumType.setIntegerDataEncoding(intEncodingType);
                commandDescription = enumType;
            }
            // This is not an enumerated command argument
            else
            {
                switch (baseDataType)
                {
                    case INTEGER:
                        // Create an integer command argument and set its attributes
                        IntegerArgumentType integerType = factory.createArgumentTypeSetTypeIntegerArgumentType();
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                        // Check if the parameter has a bit length
                        if (bitLength != null && !bitLength.isEmpty())
                        {
                            // Set the size in bits to the value supplied
                            integerType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                            intEncodingType.setSizeInBits(BigInteger.valueOf(Integer.parseInt(bitLength)));
                        }
                        // Not a bit-wise parameter
                        else
                        {
                            // Set the size in bits to the full size of the data type
                            integerType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        }

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(dataType))
                        {
                            // Set the encoding type to indicate an unsigned integer
                            integerType.setSigned(false);
                            intEncodingType.setEncoding("unsigned");
                        }

                        // Set the bit order
                        intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                    || (isHeaderBigEndian
                                                        && cmdHeaderTable.equals(spaceSystem.getName()))
                                                                                                         ? "mostSignificantBitFirst"
                                                                                                         : "leastSignificantBitFirst");

                        integerType.setIntegerDataEncoding(intEncodingType);

                        // Check if a minimum or maximum value is specified
                        if ((minimum != null && !minimum.isEmpty())
                            || (maximum != null && !maximum.isEmpty()))
                        {
                            IntegerArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeIntegerArgumentTypeValidRangeSet();
                            IntegerRangeType range = new IntegerRangeType();

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                range.setMinInclusive(minimum);
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                range.setMaxInclusive(maximum);
                            }

                            validRange.getValidRange().add(range);
                            integerType.setValidRangeSet(validRange);
                        }

                        commandDescription = integerType;
                        break;

                    case FLOAT:
                        // Create a float command argument and set its attributes
                        FloatArgumentType floatType = factory.createArgumentTypeSetTypeFloatArgumentType();
                        floatType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                        floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        floatEncodingType.setEncoding("IEEE754_1985");
                        floatType.setFloatDataEncoding(floatEncodingType);

                        // Check if a minimum or maximum value is specified
                        if ((minimum != null && !minimum.isEmpty())
                            || (maximum != null && !maximum.isEmpty()))
                        {
                            FloatArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeFloatArgumentTypeValidRangeSet();
                            FloatRangeType range = new FloatRangeType();

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                range.setMinExclusive(Double.valueOf(minimum));
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                range.setMaxExclusive(Double.valueOf(maximum));
                            }

                            validRange.getValidRange().add(range);
                            floatType.setValidRangeSet(validRange);
                        }

                        commandDescription = floatType;
                        break;

                    case STRING:
                        // Create a string command argument and set its attributes
                        StringDataType stringType = factory.createStringDataType();
                        stringType.setCharacterWidth(BigInteger.valueOf(stringSize));
                        StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                        // Set the string's size in bits based on the number of characters in the
                        // string with each character occupying a single byte
                        IntegerValueType intValType = new IntegerValueType();
                        intValType.setFixedValue(String.valueOf(stringSize * 8));
                        SizeInBits sizeInBits = new SizeInBits();
                        sizeInBits.setFixed(intValType);
                        stringEncodingType.setSizeInBits(sizeInBits);
                        stringEncodingType.setEncoding("UTF-8");

                        stringType.setStringDataEncoding(stringEncodingType);
                        commandDescription = stringType;
                        break;
                }
            }

            // Set the command name and argument name attributes
            commandDescription.setName(argumentName + TYPE + uniqueID);

            // Check is a description exists
            if (description != null && !description.isEmpty())
            {
                // Set the command description attribute
                commandDescription.setLongDescription(description);
            }
        }

        return commandDescription;
    }

    /**********************************************************************************************
     * Build a unit set from the supplied units string
     *
     * @param units
     *            parameter or command argument units; null to not specify
     *
     * @return Unit set for the supplied units string
     *********************************************************************************************/
    private UnitSet createUnitSet(String units)
    {
        UnitSet unitSet = null;

        // Check if units are provided
        if (units != null)
        {
            // Set the parameter units
            UnitType unit = factory.createUnitType();
            unit.setContent(units);
            unitSet = factory.createBaseDataTypeUnitSet();
            unitSet.getUnit().add(unit);
        }

        return unitSet;
    }

    /**********************************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param spaceSystem
     *            space system
     *
     * @param enumeration
     *            enumeration in the format <enum value><enum value separator><enum label>[<enum
     *            value separator>...][<enum pair separator>...]
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    private EnumerationList createEnumerationList(SpaceSystemType spaceSystem,
                                                  String enumeration)
    {
        EnumerationList enumList = factory.createEnumeratedDataTypeEnumerationList();

        try
        {
            // Get the character that separates the enumeration value from the associated label
            String enumValSep = CcddUtilities.getEnumeratedValueSeparator(enumeration);

            // Check if the value separator couldn't be located
            if (enumValSep == null)
            {
                throw new CCDDException("initial non-negative integer or "
                                        + "separator character between "
                                        + "enumeration value and text missing");
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
                                                      "<html><b>Enumeration '"
                                                              + enumeration
                                                              + "' format invalid in table '"
                                                              + spaceSystem.getName()
                                                              + "'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }
}
