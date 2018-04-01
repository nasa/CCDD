/**
 * CFS Command & Data Dictionary EDS handler.
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
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.ccsds.schema.sois.seds.ArrayDataType;
import org.ccsds.schema.sois.seds.ArrayDimensionsType;
import org.ccsds.schema.sois.seds.BaseTypeSetType;
import org.ccsds.schema.sois.seds.ByteOrderType;
import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.ContainerDataType;
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
import org.ccsds.schema.sois.seds.GenericTypeType;
import org.ccsds.schema.sois.seds.IntegerDataEncodingType;
import org.ccsds.schema.sois.seds.IntegerDataType;
import org.ccsds.schema.sois.seds.IntegerDataTypeRangeType;
import org.ccsds.schema.sois.seds.IntegerEncodingType;
import org.ccsds.schema.sois.seds.InterfaceCommandType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationType;
import org.ccsds.schema.sois.seds.InterfaceParameterType;
import org.ccsds.schema.sois.seds.InterfaceRefType;
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
import org.ccsds.schema.sois.seds.Unit;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command & Data Dictionary EDS handler class
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

    // GUI component instantiating this class
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

    // Conversion setup error flag
    private boolean errorFlag;

    // Names of the tables that represent the common header for all telemetry and command tables
    private String tlmHeaderTable;
    private String cmdHeaderTable;

    // Names of the system paths for the common header for all telemetry and command tables
    private String tlmHeaderPath;
    private String cmdHeaderPath;

    // Telemetry and command header argument column names for the application ID, and command
    // header argument column names for the command function code
    private String ccsdsAppID;
    private String ccsdsFuncCode;

    // Table type definitions
    private TypeDefinition structureTypeDefn;
    private TypeDefinition commandTypeDefn;

    // Flags to indicate if a telemetry and command table is defined in the import file
    private boolean isTelemetry;
    private boolean isCommand;

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
    private int commandCodeIndex;
    private int cmdDescriptionIndex;

    // Number of visible structure and command table columns
    private int numStructureColumns;
    private int numCommandColumns;

    // EDS data types
    private enum EDSDataType
    {
        INTEGER,
        FLOAT,
        STRING
    }

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
     * @param fieldHandler
     *            reference to a data field handler
     *
     * @param parent
     *            GUI component instantiating this class
     *********************************************************************************************/
    CcddEDSHandler(CcddMain ccddMain, CcddFieldHandler fieldHandler, Component parent)
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

        errorFlag = false;

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
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>EDS conversion setup failed; cause '"
                                                              + je.getMessage()
                                                              + "'",
                                                      "EDS Error",
                                                      JOptionPane.ERROR_MESSAGE,
                                                      DialogOption.OK_OPTION);
            errorFlag = true;
        }
    }

    /**********************************************************************************************
     * Get the status of the conversion setup error flag
     *
     * @return true if an error occurred setting up for the EDS conversion
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
    public void importFromFile(FileEnvVar importFile, ImportType importType) throws CCDDException,
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

            // Get the telemetry and command header argument column names for the application ID
            // and the command function code. These are stored as project-level data fields
            ccsdsAppID = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    InputDataType.XML_APP_ID);
            ccsdsFuncCode = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                       InputDataType.XML_FUNC_CODE);

            // Step through each name space in the data sheet
            for (NamespaceType namespace : dataSheet.getNamespace())
            {
                // Step through the interfaces
                for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
                {
                    // Check if this interface contains a generic type set
                    if (intfcDecType.getGenericTypeSet() != null
                        && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty())
                    {
                        // TODO
                        // Step through each generic type data
                        for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                        {
                            // Check if the item name matches that for the application ID argument
                            // column name indicator
                            if (genType.getName().equals(ArgumentColumnName.APP_ID.getAncillaryName()))
                            {
                                // Store the item value as the application ID argument column name.
                                // Note that this overrides the value extracted from the project
                                // data field
                                ccsdsAppID = genType.getBaseType();
                            }
                            // Check if the item name matches that for the command function code
                            // argument column name indicator
                            else if (genType.getName().equals(ArgumentColumnName.FUNC_CODE.getAncillaryName()))
                            {
                                // Store the item value as the command function code argument
                                // column name. Note that this overrides the value extracted from
                                // the project data field
                                ccsdsFuncCode = genType.getBaseType();
                            }

                            // Check if the application ID argument column name isn't set in the
                            // project or the import file
                            if (ccsdsAppID == null)
                            {
                                // Use the default application ID argument column name
                                ccsdsAppID = ArgumentColumnName.APP_ID.getDefaultArgColName();
                            }

                            // Check if the command function code argument column name isn't set in
                            // the project or the import file
                            if (ccsdsFuncCode == null)
                            {
                                // Use the default command function code argument column name
                                ccsdsFuncCode = ArgumentColumnName.FUNC_CODE.getDefaultArgColName();
                            }
                        }
                    }
                }
            }

            // Create the table type definitions for any new structure and command tables
            createTableTypeDefinitions(importType);

            // Check if at least one structure or command table needs to be built
            if (structureTypeDefn != null || commandTypeDefn != null)
            {
                // Step through each space system
                for (NamespaceType namespace : dataSheet.getNamespace())
                {
                    // Recursively step through the EDS-formatted data and extract the telemetry
                    // and command information
                    unbuildSpaceSystems(namespace, importType);

                    // Check if only the data from the first table is to be read
                    if (importType == ImportType.FIRST_DATA_ONLY)
                    {
                        // Stop reading table definitions
                        break;
                    }
                }
            }
        }
        catch (JAXBException je)
        {
            // Inform the user that the database import failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot import EDS XML from file<br>'</b>"
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
     * @param importFileName
     *            import file name
     *********************************************************************************************/
    private void createTableTypeDefinitions(ImportType importType)
    {
        isTelemetry = false;
        isCommand = false;
        maxNumArguments = 1;

        // Step through each space system
        for (NamespaceType namespace : dataSheet.getNamespace())
        {
            // Recursively step through the EDS-formatted data and extract the telemetry and
            // command information
            findMetaData(namespace, importType);
        }

        // Check if a structure table is defined in the import file
        if (isTelemetry)
        {
            String typeName = "EDS Structure";
            int sequence = 2;

            // Continue to check while a table type with this name exists
            while (tableTypeHandler.getTypeDefinition(typeName) != null)
            {
                // Alter the name so that there isn't a duplicate
                typeName = "EDS Structure " + sequence;
                sequence++;
            }

            // Create the EDS structure table type using the default structure columns
            structureTypeDefn = tableTypeHandler.createTypeDefinition(typeName,
                                                                      DefaultColumn.getDefaultColumnDefinitions(TYPE_STRUCTURE),
                                                                      "EDS import structure table type");

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
        }

        // Check if a command table is defined in the import file
        if (isCommand)
        {
            String typeName = "EDS Command";
            int sequence = 2;

            // Continue to check while a table type with this name exists
            while (tableTypeHandler.getTypeDefinition(typeName) != null)
            {
                // Alter the name so that there isn't a duplicate
                typeName = "EDS Command " + sequence;
                sequence++;
            }

            // Create the EDS command table type using the default command columns
            commandTypeDefn = tableTypeHandler.createTypeDefinition(typeName,
                                                                    DefaultColumn.getDefaultColumnDefinitions(TYPE_COMMAND),
                                                                    "EDS import command table type");

            // Step through each additional command argument column set
            for (int argIndex = 2; argIndex < maxNumArguments; argIndex++)
            {
                // Add the default columns for this command argument
                commandTypeDefn.addCommandArgumentColumns(argIndex);
            }

            // Get the list containing the associated column indices for each argument grouping
            commandArguments = commandTypeDefn.getAssociatedCommandArgumentColumns(true);

            // Get the command table column indices
            commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME));
            commandCodeIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.COMMAND_CODE));
            cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));

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
     * @param importFileName
     *            import file name
     *********************************************************************************************/
    private void findMetaData(NamespaceType namespace, ImportType importType)
    {
        // Step through the interfaces in order to locate the name space's parameter and command
        // sets
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
                isCommand = true;

                // Step through each command
                for (InterfaceCommandType cmdType : intfcDecType.getCommandSet().getCommand())
                {
                    // The number of entries in the meta-command type is the number of command
                    // arguments required by this command. Store the largest number of command
                    // arguments required by all commands in the import file
                    maxNumArguments = Math.max(maxNumArguments, cmdType.getArgument().size());
                }
            }

            // Check if the data from only the first table is to be read and one has been found
            if (importType == ImportType.FIRST_DATA_ONLY && (isTelemetry || isCommand))
            {
                break;
            }
        }
    }

    /**********************************************************************************************
     * Extract the telemetry and/or command information from the space system. This is a recursive
     * method
     *
     * @param namespace
     *            name space
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void unbuildSpaceSystems(NamespaceType namespace,
                                     ImportType importType) throws CCDDException
    {
        // Get the table name based on the space system name and the path to this system. Convert
        // the name to be a valid table name
        String tableName = convertPathToTableName(namespace.getName());

        boolean hasParameter = false;
        boolean hasCommand = false;

        // Step through the interfaces in order to locate the name space's parameter and command
        // sets
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

        // Step through the interfaces in order to locate the name space's parameter and command
        // sets
        for (InterfaceDeclarationType intfcDecType : namespace.getDeclaredInterfaceSet().getInterface())
        {
            // Check if the interface contains a parameter set
            if (intfcDecType.getParameterSet() != null
                && !intfcDecType.getParameterSet().getParameter().isEmpty())
            {
                // Build the structure table from the telemetry data
                importStructureTable(namespace,
                                     intfcDecType.getParameterSet().getParameter(),
                                     tableName,
                                     hasCommand);
            }

            // Check if the interface contains a command set
            if (intfcDecType.getCommandSet() != null
                && !intfcDecType.getCommandSet().getCommand().isEmpty())
            {
                // Build the structure table from the telemetry data
                importCommandTable(namespace,
                                   intfcDecType.getCommandSet().getCommand(),
                                   tableName,
                                   hasParameter);
            }

            // Check if the data from only the first table is to be read and one has been found
            if (importType == ImportType.FIRST_DATA_ONLY && (isTelemetry || isCommand))
            {
                break;
            }
        }
    }

    /**********************************************************************************************
     * Build a structure table from the specified telemetry metadata
     *
     * @param namespace
     *            name space
     *
     * @param parameterSet
     *            reference to the parameter set from which to build the structure table
     *
     * @param table
     *            name table name, including the full system path
     *
     * @param hasCommand
     *            true if the name space also has a command set
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importStructureTable(NamespaceType namespace,
                                      List<InterfaceParameterType> parameterSet,
                                      String tableName,
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
        for (int parmIndex = 0; parmIndex < parameterSet.size(); parmIndex++)
        {
            // Get the reference to the parameter in the parameter set
            InterfaceParameterType parm = parameterSet.get(parmIndex);

            // Create a new row of data in the table definition to contain
            // this structure's information. Initialize all columns to
            // blanks except for the variable name
            String[] newRow = new String[structureTypeDefn.getColumnCountVisible()];
            Arrays.fill(newRow, null);
            newRow[variableNameIndex] = parm.getName();

            // Check if the description column exists in the table type
            // definition and that a description exists
            if (descriptionIndex != -1 && parm.getLongDescription() != null)
            {
                // Store the description for this variable
                newRow[descriptionIndex] = parm.getLongDescription();
            }

            // Add the new row to the table definition
            tableDefn.addData(newRow);

            // Step through the parameter type set to find the data type entry where the
            // name matches the parameter type reference from the parameter set
            for (RootDataType parmType : namespace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType())
            {
                // Check if the parameter set's parameter type reference matches the
                // parameter type set's name
                if (parm.getType().equals(parmType.getName()))
                {
                    String dataType = null;
                    String arraySize = null;
                    BigInteger bitLength = null;
                    long sizeInBytes = 0;
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

                    // Check if a data type entry for the parameter exists in the parameter
                    // type set (note that if the parameter is an array the steps above
                    // locate the data type entry for the individual array members)
                    if (parmType != null)
                    {
                        boolean isInteger = false;
                        boolean isUnsigned = false;
                        boolean isFloat = false;
                        boolean isString = false;

                        // Check if the parameter is an integer data type
                        if (parmType instanceof IntegerDataType)
                        {
                            // The 'sizeInBits' references are the integer size for
                            // non-bit-wise parameters, but equal the number of bits
                            // assigned to the parameter for a bit-wise parameter. It
                            // doens't appear that the size of the integer used to contain
                            // the parameter is stored. The assumption is made that the
                            // smallest integer required to store the bits is used.
                            // However, this can alter the originally intended bit-packing
                            // (e.g., a 3-bit and a 9-bit fit within a single 16-bit
                            // integer, but the code below assigns the first to an 8-bit
                            // integer and the second to a 16-bit integer)

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

                            // Determine the smallest integer size that contains the number
                            // of bits occupied by the parameter
                            sizeInBytes = 8;

                            while (bitLength.longValue() > sizeInBytes)
                            {
                                sizeInBytes *= 2;
                            }

                            sizeInBytes /= 8;

                            // Check if the table's member list exists
                            if (memberList != null)
                            {
                                // Get the reference to the container's member list
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
                                    sizeInBytes = 4;
                                    break;

                                case IEEE_754_2008_DOUBLE:
                                    sizeInBytes = 8;
                                    break;

                                case IEEE_754_2008_QUAD:
                                    sizeInBytes = 16;
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
                            sizeInBytes = stlm.getLength().longValue();

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

                                // Determine the smallest integer size that contains the
                                // number of bits occupied by the parameter
                                sizeInBytes = 8;

                                while (bitLength.longValue() > sizeInBytes)
                                {
                                    sizeInBytes *= 2;
                                }

                                sizeInBytes /= 8;
                                isInteger = true;
                            }
                        }
                        // Check if the parameter is a container data type, i.e., a structure
                        // reference
                        else if (parmType instanceof ContainerDataType
                                 && ((ContainerDataType) parmType).getEntryList() != null)
                        {
                            // Get the reference to the container's base type, which is the name
                            // space path
                            dataType = ((ContainerDataType) parmType).getBaseType();

                            // Check if the name space reference includes a multi-level path; if so
                            // it begins with a '/'. This beginning '/' is stripped off
                            if (dataType.startsWith("/"))
                            {
                                // Remove the initial '/'
                                dataType = dataType.substring(1);
                            }

                            // The variable name must be stripped from the name space path. Get the
                            // index of the beginning of the variable name
                            int end = dataType.lastIndexOf("/");

                            // Check if the beginning of the variable name was found
                            if (end != -1)
                            {
                                // Strip off the variable name from the path
                                dataType = dataType.substring(0, end);
                            }

                            // Convert the path to a valid structure name, replacing invalid
                            // characters with underscores
                            dataType = convertPathToTableName(dataType);
                        }

                        // Check if the data type isn't a structure reference
                        if (dataType == null)
                        {
                            // Get the name of the data type from the data type table that
                            // matches the base type and size of the parameter
                            dataType = getDataType(dataTypeHandler,
                                                   sizeInBytes,
                                                   isInteger,
                                                   isUnsigned,
                                                   isFloat,
                                                   isString);
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

                        // Check if a bit length exists
                        if (bitLength != null && bitLength.longValue() != sizeInBytes)
                        {
                            // Store the bit length
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + bitLengthIndex,
                                                    bitLength.toString());
                        }

                        // Check if a description exists
                        if (parm.getLongDescription() != null)
                        {
                            // Store the description
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + descriptionIndex,
                                                    parm.getLongDescription());
                        }

                        // Check if a units exists
                        if (units != null && !units.value().isEmpty())
                        {
                            // Store the units for this variable
                            tableDefn.getData().set(parmIndex
                                                    * numStructureColumns
                                                    + unitsIndex,
                                                    units.value());
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

                    break;
                }
            }
        }

        // Add the structure table definition to the list
        tableDefinitions.add(tableDefn);

    }

    /**********************************************************************************************
     * Build a command table from the specified command metadata
     *
     * @param namespace
     *            name space
     *
     * @param commandSet
     *            reference to the command set from which to build the command table
     *
     * @param table
     *            name table name, including the full system path
     *
     * @param hasParameter
     *            true if the name space also has a parameter set
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importCommandTable(NamespaceType namespace,
                                    List<InterfaceCommandType> commandSet,
                                    String tableName,
                                    boolean hasParameter) throws CCDDException
    {
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
        for (InterfaceCommandType cmdType : commandSet)
        {
            // Create a new row of data in the table definition to contain this command's
            // information. Initialize all columns to blanks except for the command name
            String[] newRow = new String[commandTypeDefn.getColumnCountVisible()];
            Arrays.fill(newRow, null);
            newRow[commandNameIndex] = cmdType.getName();

            // Check if the command description is present and the description column exists in the
            // table type definition
            if (cmdType.getLongDescription() != null && cmdDescriptionIndex != -1)
            {
                // Store the command description in the row's description
                // column
                newRow[cmdDescriptionIndex] = cmdType.getLongDescription();
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
                            long sizeInBytes = 0;
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
                                    arraySize += String.valueOf(dim.getSize().longValue())
                                                 + ",";
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
                                    sizeInBytes = 8;

                                    while (bitLength.longValue() > sizeInBytes)
                                    {
                                        sizeInBytes *= 2;
                                    }

                                    sizeInBytes /= 8;

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
                                            sizeInBytes = 4;
                                            break;

                                        case IEEE_754_2008_DOUBLE:
                                            sizeInBytes = 8;
                                            break;

                                        case IEEE_754_2008_QUAD:
                                            sizeInBytes = 16;
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
                                    sizeInBytes = scmd.getLength().longValue();

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
                                        sizeInBytes = 8;

                                        while (bitLength.longValue() > sizeInBytes)
                                        {
                                            sizeInBytes *= 2;
                                        }

                                        sizeInBytes /= 8;
                                        isInteger = true;
                                    }
                                }

                                // Get the name of the data type from the data type table that
                                // matches the base type and size of the parameter
                                dataType = getDataType(dataTypeHandler,
                                                       sizeInBytes,
                                                       isInteger,
                                                       isUnsigned,
                                                       isFloat,
                                                       isString);

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
                                        newRow[acmdArg.getName()] = argList.getName();
                                    }

                                    // Check if the command argument data type is present
                                    if (acmdArg.getDataType() != -1 && dataType != null)
                                    {
                                        // Store the command argument data type
                                        newRow[acmdArg.getDataType()] = dataType;
                                    }

                                    // Check if the command argument array size is present
                                    if (acmdArg.getArraySize() != -1 && arraySize != null)
                                    {
                                        // Store the command argument array size
                                        newRow[acmdArg.getArraySize()] = arraySize;
                                    }

                                    // Check if the command argument bit length is present
                                    if (acmdArg.getBitLength() != -1 && bitLength != null)
                                    {
                                        // Store the command argument bit length
                                        newRow[acmdArg.getBitLength()] = bitLength.toString();
                                    }

                                    // Check if the command argument enumeration is present
                                    if (acmdArg.getEnumeration() != -1 && enumeration != null)
                                    {
                                        // Store the command argument enumeration
                                        newRow[acmdArg.getEnumeration()] = enumeration;
                                    }

                                    // Check if the command argument description is present
                                    if (acmdArg.getDescription() != -1 && description != null)
                                    {
                                        // Store the command argument description
                                        newRow[acmdArg.getDescription()] = description;
                                    }

                                    // Check if the command argument units is present
                                    if (acmdArg.getUnits() != -1 && units != null)
                                    {
                                        // Store the command argument units
                                        newRow[acmdArg.getUnits()] = units.toString();
                                    }

                                    // Check if the command argument minimum is present
                                    if (acmdArg.getMinimum() != -1 && minimum != null)
                                    {
                                        // Store the command argument minimum
                                        newRow[acmdArg.getMinimum()] = minimum;
                                    }

                                    // Check if the command argument maximum is present
                                    if (acmdArg.getMaximum() != -1 && maximum != null)
                                    {
                                        // Store the command argument maximum
                                        newRow[acmdArg.getMaximum()] = maximum;
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

            // Add the new row to the table definition
            tableDefn.addData(newRow);
        }

        // Add the command table definition to the list
        tableDefinitions.add(tableDefn);
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
     *            [0] endianess (EndianType.BIG_ENDIAN or EndianType.LITTLE_ENDIAN)
     *
     * @return true if an error occurred preventing exporting the project to the file
     *********************************************************************************************/
    @Override
    public boolean exportToFile(FileEnvVar exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                boolean includeReservedMsgIDs,
                                boolean includeVariablePaths,
                                CcddVariableSizeAndConversionHandler variableHandler,
                                String[] separators,
                                Object... extraInfo)
    {
        boolean errorFlag = false;

        try
        {
            // Convert the table data into EDS format
            convertTablesToEDS(tableNames,
                               replaceMacros,
                               includeReservedMsgIDs,
                               includeVariablePaths,
                               variableHandler,
                               separators,
                               (EndianType) extraInfo[0]);

            // Output the XML to the specified file
            marshaller.marshal(project, exportFile);
        }
        catch (JAXBException je)
        {
            // Inform the user that the database export failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot export as EDS XML to file<br>'</b>"
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
     * Convert the project database contents to EDS XML format
     *
     * @param tableNames
     *            array of table names to convert to EDS format
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
     *            [0] endianess (EndianType.BIG_ENDIAN or EndianType.LITTLE_ENDIAN)
     *********************************************************************************************/
    private void convertTablesToEDS(String[] tableNames,
                                    boolean replaceMacros,
                                    boolean includeReservedMsgIDs,
                                    boolean includeVariablePaths,
                                    CcddVariableSizeAndConversionHandler variableHandler,
                                    String[] separators,
                                    EndianType endianess)
    {
        this.endianess = endianess;

        // Create the project's data sheet and device
        dataSheet = factory.createDataSheetType();
        project = factory.createDataSheet(dataSheet);
        device = factory.createDeviceType();
        device.setName(convertPathToTableName(dbControl.getProjectName()));
        device.setLongDescription(dbControl.getDatabaseDescription(dbControl.getDatabaseName()));
        device.setLongDescription("Author: " + dbControl.getUser()
                                  + "\nGenerated by CCDD " + ccddMain.getCCDDVersionInformation()
                                  + "\nDate: " + new Date().toString()
                                  + "\nProject: " + dbControl.getProjectName()
                                  + "\nHost: " + dbControl.getServer()
                                  + "\nEndianess: " + (endianess == EndianType.BIG_ENDIAN
                                                                                          ? "big"
                                                                                          : "little"));
        dataSheet.setDevice(device);

        // Add the project's name spaces, parameters, and commands
        buildNamespaces(tableNames, includeVariablePaths, variableHandler, separators);
    }

    /**********************************************************************************************
     * Build the name spaces for the list of tables specified
     *
     * @param tableNames
     *            array of table names
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
     *********************************************************************************************/
    private void buildNamespaces(String[] tableNames,
                                 boolean includeVariablePaths,
                                 CcddVariableSizeAndConversionHandler variableHandler,
                                 String[] separators)
    {
        // Build the data field information for all fields
        fieldHandler.buildFieldInformation(null);

        // Get the names of the tables representing the CCSDS telemetry and command headers
        tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    InputDataType.XML_TLM_HDR);
        cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                    InputDataType.XML_CMD_HDR);

        // Get the command header argument names for the application ID and the command function
        // code. These are stored as project-level data fields
        String ccsdsAppID = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                       InputDataType.XML_APP_ID);
        String ccsdsFuncCode = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                          InputDataType.XML_FUNC_CODE);

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Check if this is a child (instance) table
            if (!TableInformation.isPrototype(tableName))
            {
                // Get the prototype of the instance table. Only prototypes of the tables are
                // used to create the space systems
                tableName = TableInformation.getPrototypeName(tableName);

                // Get the name of the system to which this table belongs from the table's
                // system path data field (if present)
                String systemPath = cleanSystemPath(fieldHandler.getFieldValue(tableName,
                                                                               InputDataType.SYSTEM_PATH));

                // Check if the space system for the prototype of the table has already been
                // created
                if (searchNamespacesForName(systemPath, tableName) != null)
                {
                    // Skip this table since it's space system has already been created
                    continue;
                }
            }

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
                    String systemPath = cleanSystemPath(fieldHandler.getFieldValue(tableName,
                                                                                   InputDataType.SYSTEM_PATH));

                    // Add the name space
                    NamespaceType namespace = addNamespace(systemPath,
                                                           tableName,
                                                           tableInfo.getDescription());

                    // Check if this is a structure table
                    if (typeDefn.isStructure())
                    {
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

                        // Set the flag to indicate if this is the telemetry header table
                        boolean isTlmHeaderTable = tableName.equals(tlmHeaderTable);

                        // Check if this is the telemetry header table
                        if (isTlmHeaderTable)
                        {
                            // Store the telemetry header's path
                            tlmHeaderPath = systemPath;
                        }

                        // Export the parameter container for this structure
                        addParameterContainer(namespace,
                                              tableInfo,
                                              varColumn,
                                              typeColumn,
                                              sizeColumn,
                                              minColumn,
                                              maxColumn,
                                              isTlmHeaderTable,
                                              applicationID,
                                              ccsdsAppID);

                        // Step through each row in the structure table
                        for (String[] rowData : tableInfo.getData())
                        {
                            // Check if the variable isn't an array member (the array definition is
                            // used to define the array)
                            if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                            {
                                // Add the variable to the data sheet
                                addParameter(namespace,
                                             rowData[varColumn],
                                             rowData[typeColumn],
                                             rowData[sizeColumn],
                                             rowData[bitColumn],
                                             (enumColumn != -1 && !rowData[enumColumn].isEmpty()
                                                                                                 ? rowData[enumColumn]
                                                                                                 : null),
                                             (unitsColumn != -1 && !rowData[unitsColumn].isEmpty()
                                                                                                   ? rowData[unitsColumn]
                                                                                                   : null),
                                             (descColumn != -1 && !rowData[descColumn].isEmpty()
                                                                                                 ? rowData[descColumn]
                                                                                                 : null),
                                             (dataTypeHandler.isString(rowData[typeColumn]) && !rowData[sizeColumn].isEmpty()
                                                                                                                              ? Integer.valueOf(rowData[sizeColumn].replaceAll("^.*(\\d+)$", "$1"))
                                                                                                                              : 1));
                            }
                        }
                    }
                    // This is a command table
                    else
                    {
                        // Check if this is the command header table
                        if (tableName.equals(cmdHeaderTable))
                        {
                            // Store the command header's path
                            cmdHeaderPath = systemPath;
                        }

                        // Add the command(s) from this table to the data sheet
                        addNamespaceCommands(namespace,
                                             tableInfo,
                                             tableName.equals(cmdHeaderTable),
                                             applicationID,
                                             ccsdsAppID,
                                             ccsdsFuncCode);
                    }
                }
            }
        }

        // TODO IN ORDER TO GET INSTANCE INFORMATION (SUCH AS MIN/MAX) IT WOULD BE NECESSARY TO
        // LOAD THE INSTANCE OF THE TABLE REFERENCED BY THE PARENT
        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the prototype for the child
            tableName = TableInformation.getPrototypeName(tableName);

            // Get the name of the system to which this table belongs from the table's
            // system path data field (if present)
            String systemPath = cleanSystemPath(fieldHandler.getFieldValue(tableName,
                                                                           InputDataType.SYSTEM_PATH));

            // Get the name space for this table
            NamespaceType namespace = searchNamespacesForName(systemPath, tableName);

            // Check if the table's name space exists
            if (namespace != null)
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
     * Create a new name space as a child within the specified name space. If the specified name
     * space is null then this is the root data sheet
     *
     * @param systemPath
     *            system name; null or blank if no system
     *
     * @param namespaceName
     *            name for the new name space
     *
     * @param description
     *            data sheet description
     *
     * @return Reference to the new name space
     *********************************************************************************************/
    private NamespaceType addNamespace(String systemPath, String namespaceName, String description)
    {
        // Create the new name space and set the name attribute
        NamespaceType childSpace = factory.createNamespaceType();

        // Set the name space name
        childSpace.setName((systemPath != null
                                               ? systemPath + "/"
                                               : "")
                           + namespaceName);

        // Check if a description is provided
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            childSpace.setLongDescription(description);
        }

        // Create an interface set for the name space
        childSpace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());

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

        // Check if this is the interface for the telemetry header
        if (namespace.getName().equals((tlmHeaderPath != null
                                        && !tlmHeaderPath.isEmpty()
                                                                    ? tlmHeaderPath + "/"
                                                                    : "")
                                       + tlmHeaderTable))
        {
            intParmType.setAbstract(true);
        }

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
     *
     * @param isTlmHeader
     *            true if this table represents the CCSDS telemetry header
     *
     * @param applicationID
     *            application ID
     *
     * @param ccsdsAppID
     *            name of the command header argument containing the application ID
     *********************************************************************************************/
    private void addParameterContainer(NamespaceType namespace,
                                       TableInformation tableInfo,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       int minColumn,
                                       int maxColumn,
                                       boolean isTlmHeader,
                                       String applicationID, // TODO WHERE DOES THIS GET PUT?
                                       String ccsdsAppID) // TODO WHERE DOES THIS GET PUT?
    {
        ContainerDataType containerType = null;
        EntryListType entryList = factory.createEntryListType();

        // Step through each row of data in the structure table
        for (String[] rowData : tableInfo.getData())
        {
            // Check if this is not an array member (non-array parameters and array definitions are
            // used to create the list)
            if (!ArrayVariable.isArrayMember(rowData[varColumn]))
            {
                // TODO A REFERENCE IN A CONTAINER TO A STRUCTURE THAT CONTAINS AN ARRAY THROWS A
                // NULL POINTER EXCEPTION IN THE EDS VIEWER (UNDER THE DATA TYPES TAB WHEN THE
                // CONTAINER IS EXPANDED)

                // Store the parameter reference in the list
                EntryType entryType = factory.createEntryType();
                entryType.setName(rowData[varColumn]);
                entryType.setType(getReferenceByDataType(rowData[varColumn],
                                                         rowData[typeColumn],
                                                         !dataTypeHandler.isPrimitive(rowData[typeColumn]))
                                  + getObjectIdentifier(rowData[sizeColumn]));

                // TODO THE CONTAINERS FOR STRUCTS REFERENCED BY THIS STRUCT ARE ADDED IN
                // buildNamespaces(). HOWEVER, THOSE STRUCTS GET MIN/MAX INFO FROM THE PROTOTYPE.
                // THE INSTANCES WOULD NEED TO BE LOADED IN ORDER TO GET THE ACTUAL MIN/MAX VALUES
                // FOR THE INSTANCE VARIABLES
                // Check if a minimum or maximum value exists
                if ((minColumn != -1 && !rowData[minColumn].isEmpty())
                    || (maxColumn != -1 && !rowData[maxColumn].isEmpty()))
                {
                    DerivedTypeRangeType range = factory.createDerivedTypeRangeType();
                    MinMaxRangeType minMaxRange = factory.createMinMaxRangeType();
                    minMaxRange.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);

                    // Set the flag if the parameter is in integer data type
                    boolean isInteger = dataTypeHandler.isInteger(rowData[typeColumn]);

                    // Check if a minimum value is specified
                    if (minColumn != -1 && !rowData[minColumn].isEmpty())
                    {
                        // Set the minimum value
                        minMaxRange.setMin(isInteger
                                                     ? BigDecimal.valueOf(Integer.valueOf(rowData[minColumn]))
                                                     : BigDecimal.valueOf(Float.valueOf(rowData[minColumn])));
                    }

                    // Check if a maximum value is specified
                    if (maxColumn != -1 && !rowData[maxColumn].isEmpty())
                    {
                        // Set the maximum value
                        minMaxRange.setMax(isInteger
                                                     ? BigDecimal.valueOf(Integer.valueOf(rowData[maxColumn]))
                                                     : BigDecimal.valueOf(Float.valueOf(rowData[maxColumn])));
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

            // Check if this is the telemetry header
            if (isTlmHeader)
            {
                containerType.setName(tlmHeaderTable + TYPE);
                containerType.setAbstract(true);
            }
            // Not the telemetry header
            else
            {
                containerType.setName(tableInfo.getPrototypeName() + TYPE);
            }

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
     *            enumeration in the format <enum label>|<enum value>[|...][,...]; null to not
     *            specify
     *
     * @param units
     *            parameter units
     *
     * @param description
     *            parameter description
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *********************************************************************************************/
    private void addParameter(NamespaceType namespace,
                              String parameterName,
                              String dataType,
                              String arraySize,
                              String bitLength,
                              String enumeration,
                              String units,
                              String description,
                              int stringSize)
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
                        "");

            // Build the parameter attributes
            InterfaceParameterType parameter = factory.createInterfaceParameterType();
            parameter.setName(parameterName);
            parameter.setType(getReferenceByDataType(parameterName, dataType, false)
                              + getObjectIdentifier(arraySize));

            // Check if a description is provided for this parameter
            if (description != null && !description.isEmpty())
            {
                // Set the parameter's description
                parameter.setLongDescription(description);
            }

            InterfaceDeclarationType intParmType = null;

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
        InterfaceDeclarationType intCmdType = factory.createInterfaceDeclarationType();
        intCmdType.setName(COMMAND);

        // Check if this is the interface for the command header
        if (namespace.getName().equals((cmdHeaderPath != null
                                        && !cmdHeaderPath.isEmpty()
                                                                    ? cmdHeaderPath + "/"
                                                                    : "")
                                       + cmdHeaderTable))
        {
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
     * @param tableInfo
     *            TableInformation reference for the current node
     *
     * @param isCmdHeader
     *            true if this table represents the CCSDS command header
     *
     * @param applicationID
     *            application ID
     *
     * @param ccsdsAppID
     *            name of the command header argument containing the application ID
     *
     * @param ccsdsFuncCode
     *            name of the command header argument containing the command function code
     *********************************************************************************************/
    private void addNamespaceCommands(NamespaceType namespace,
                                      TableInformation tableInfo,
                                      boolean isCmdHeader,
                                      String applicationID,
                                      String ccsdsAppID,
                                      String ccsdsFuncCode)
    {
        List<String> argumentNames = new ArrayList<String>();

        // Get the column indices for the command name, code, and description
        int cmdNameCol = typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME);
        int cmdCodeCol = typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_CODE);
        int cmdDescCol = typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION);

        // Step through each command argument column grouping
        for (AssociatedColumns cmdArg : typeDefn.getAssociatedCommandArgumentColumns(false))
        {
            // Check if the argument description column exists and it matches the index set for the
            // command description
            if (cmdArg.getDescription() != -1 && cmdArg.getDescription() == cmdDescCol)
            {
                // There is no column for the command description, so reset its column index and
                // stop searching
                cmdDescCol = -1;
                break;
            }
        }

        // Step through each row in the table
        for (String[] rowData : tableInfo.getData())
        {
            // Check if the command name exists
            if (cmdNameCol != -1 && !rowData[cmdNameCol].isEmpty())
            {
                // Initialize the command attributes and argument names list
                String commandCode = null;
                String commandDescription = null;
                List<CommandArgumentType> arguments = new ArrayList<CommandArgumentType>();

                // Store the command name
                String commandName = rowData[cmdNameCol];

                // Check if the command code exists
                if (cmdCodeCol != -1 && !rowData[cmdCodeCol].isEmpty())
                {
                    // Store the command code
                    commandCode = rowData[cmdCodeCol];
                }

                // Check if the command description exists
                if (cmdDescCol != -1 && !rowData[cmdDescCol].isEmpty())
                {
                    // Store the command description
                    commandDescription = rowData[cmdDescCol];
                }

                // Step through each command argument column grouping
                for (AssociatedColumns cmdArg : typeDefn.getAssociatedCommandArgumentColumns(false))
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
                        String uniqueID = "";
                        int dupCount = 0;

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

                        // Check if the minimum column exists
                        if (cmdArg.getMinimum() != -1 && !rowData[cmdArg.getMinimum()].isEmpty())
                        {
                            // Store the command argument minimum value
                            DerivedTypeRangeType range = argType.getValidRange() == null
                                                                                         ? factory.createDerivedTypeRangeType()
                                                                                         : argType.getValidRange();
                            MinMaxRangeType minMax = range.getMinMaxRange() == null
                                                                                    ? factory.createMinMaxRangeType()
                                                                                    : range.getMinMaxRange();
                            minMax.setMin(BigDecimal.valueOf(Double.valueOf(rowData[cmdArg.getMinimum()])));
                            argType.setValidRange(range);
                        }

                        // Check if the maximum column exists
                        if (cmdArg.getMaximum() != -1 && !rowData[cmdArg.getMaximum()].isEmpty())
                        {
                            // Store the command argument maximum value
                            DerivedTypeRangeType range = argType.getValidRange() == null
                                                                                         ? factory.createDerivedTypeRangeType()
                                                                                         : argType.getValidRange();
                            MinMaxRangeType minMax = range.getMinMaxRange() == null
                                                                                    ? factory.createMinMaxRangeType()
                                                                                    : range.getMinMaxRange();
                            minMax.setMax(BigDecimal.valueOf(Double.valueOf(rowData[cmdArg.getMaximum()])));
                            argType.setValidRange(range);
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

                        argType.setType(argumentName + TYPE + uniqueID);

                        // Add the name to the list
                        argumentNames.add(argumentName);

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
                                    uniqueID);

                        // Add the command argument to the list
                        arguments.add(argType);
                    }
                }

                // Add the command information
                addCommand(namespace,
                           commandName,
                           commandCode,
                           isCmdHeader,
                           applicationID,
                           ccsdsAppID,
                           ccsdsFuncCode,
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
     * @param commandCode
     *            command code
     *
     * @param isCmdHeader
     *            true if this table represents the CCSDS command header
     *
     * @param applicationID
     *            application ID
     *
     * @param ccsdsAppID
     *            name of the command header argument containing the application ID
     *
     * @param ccsdsFuncCode
     *            name of the command header argument containing the command function code
     *
     * @param argumentNames
     *            list of command argument names
     *
     * @param description
     *            description of the command
     *********************************************************************************************/
    private void addCommand(NamespaceType namespace,
                            String commandName,
                            String commandCode, // TODO WHERE DOES THIS GET PUT?
                            boolean isCmdHeader,
                            String applicationID, // TODO WHERE DOES THIS GET PUT?
                            String ccsdsAppID, // TODO WHERE DOES THIS GET PUT?
                            String ccsdsFuncCode, // TODO WHERE DOES THIS GET PUT?
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
        if (!namespace.getName().equals(cmdHeaderTable))
        {
            // Set the command header as the base
            BaseTypeSetType baseType = factory.createBaseTypeSetType();
            InterfaceRefType intfcType = factory.createInterfaceRefType();
            intfcType.setType(cmdHeaderTable + "/" + COMMAND);
            baseType.getBaseType().add(intfcType);
            intCmdType.setBaseTypeSet(baseType);
        }

        // Add the command to the command set
        intCmdType.getCommandSet().getCommand().add(command);
    }

    /**********************************************************************************************
     * Convert the primitive data type into the EDS equivalent
     *
     * @param dataType
     *            data type
     *
     * @return EDS data type corresponding to the specified primitive data type; null if no match
     *********************************************************************************************/
    private EDSDataType getEDSDataType(String dataType)
    {
        EDSDataType edsDataType = null;

        // Check if the type is an integer (signed or unsigned)
        if (dataTypeHandler.isInteger(dataType))
        {
            edsDataType = EDSDataType.INTEGER;
        }
        // Check if the type is a floating point (float or double)
        else if (dataTypeHandler.isFloat(dataType))
        {
            edsDataType = EDSDataType.FLOAT;
        }
        // Check if the type is a string (character or string)
        else if (dataTypeHandler.isCharacter(dataType))
        {
            edsDataType = EDSDataType.STRING;
        }

        return edsDataType;
    }

    /**********************************************************************************************
     * Create the parameter data type and set the specified attributes
     *
     * @param namespace
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
     * @param description
     *            parameter description; null to not specify
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
                             String uniqueID)
    {
        RootDataType parameterDescription = null;

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
            String name = getReferenceByDataType(parameterName, dataType, false);
            arrayType.setName(name + ARRAY);
            arrayType.setDataTypeRef(name + TYPE);
            ArrayDimensionsType dimList = factory.createArrayDimensionsType();

            // Step through each array dimension
            for (int dim : ArrayVariable.getArrayIndexFromSize(arraySize))
            {
                // Create a dimension entry for the array type
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
            // Get the EDS data type corresponding to the primitive data type
            EDSDataType edsDataType = getEDSDataType(dataType);

            // Check if the a corresponding EDS data type exists
            if (edsDataType != null)
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

                    intEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                    ? ByteOrderType.BIG_ENDIAN
                                                                                    : ByteOrderType.LITTLE_ENDIAN);
                    enumType.setIntegerDataEncoding(intEncodingType);
                    enumType.setEnumerationList(enumList);
                    parameterDescription = enumType;
                }
                // Not an enumeration
                else
                {
                    switch (edsDataType)
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

                            integerType.setRange(integerRange);
                            intEncodingType.setByteOrder(endianess == EndianType.BIG_ENDIAN
                                                                                            ? ByteOrderType.BIG_ENDIAN
                                                                                            : ByteOrderType.LITTLE_ENDIAN);
                            integerType.setIntegerDataEncoding(intEncodingType);
                            setUnits(units, integerType);
                            parameterDescription = integerType;
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
                            parameterDescription = floatType;
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
                            parameterDescription = stringType;
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
            containerType.setBaseType(getReferenceByDataType(parameterName, dataType, true)
                                      + getObjectIdentifier(arraySize));
            parameterName = dataType;
            parameterDescription = containerType;
        }

        // Set the type name
        parameterDescription.setName(parameterName + TYPE + uniqueID);

        // Check is a description exists
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            parameterDescription.setLongDescription(description);
        }

        // Add the data type information to this name space
        dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(parameterDescription);
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
     *            enumeration in the format <enum value><enum value separator><enum label>[<enum
     *            value separator>...][<enum pair separator>...]
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

            // Check if the value separator couldn't be located
            if (enumValSep == null)
            {
                throw new CCDDException("separator character between enumeration value and label missing");
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
                                                              + namespace.getName()
                                                              + "'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }

    /**********************************************************************************************
     * Replace each space with an underscore and move any leading underscores to the end of each
     * path segment
     *
     * @param path
     *            system path in the form <</>path1</path2<...>>
     *
     * @return Path with each space replaced with an underscore and any leading underscores moved
     *         to the end of each path segment
     *********************************************************************************************/
    private String cleanSystemPath(String path)
    {
        // Check if the path exists
        if (path != null)
        {
            // Replace each space with an underscore and move any leading underscores to the end of
            // each path segment
            path = path.replaceAll(" ", "_").replaceAll("(^|/)_([^/]*)", "$1$2_");
        }

        return path;
    }

    /**********************************************************************************************
     * Get the full name space path to a structure object or the local name space reference to a
     * primitive or structure object
     *
     * @param parameterName
     *            parameter name; unused if returning the full path
     *
     * @param dataType
     *            data type
     *
     * @param getStructurePath
     *            true to return the full name space path to the structure object; false to get the
     *            local name space reference to a primitive or structure object
     *
     * @return For the local name space reference the parameter name is returned if the data type
     *         is primitive, or the data type name if the data type is a structure. The full name
     *         space path for a structure object is in the format : <br>
     *         <system path/><data type>/<data type>
     *********************************************************************************************/
    private String getReferenceByDataType(String parameterName,
                                          String dataType,
                                          boolean getStructurePath)
    {
        String path = null;

        // Check if the full path to the structure is required
        if (getStructurePath)
        {
            // Get the name of the system to which this referenced structure belongs
            String refSystemName = cleanSystemPath(fieldHandler.getFieldValue(dataType,
                                                                              InputDataType.SYSTEM_PATH));

            // Build the path from the system path, structure name, and array size
            path = (refSystemName == null || refSystemName.isEmpty()
                                                                     ? ""
                                                                     : refSystemName
                                                                       + "/")
                   + dataType
                   + "/"
                   + dataType;
        }
        // The full path isn't required
        else
        {
            // Get the path name based on if the data type is a primitive or a structure
            path = dataTypeHandler.isPrimitive(dataType)
                                                         ? parameterName
                                                         : dataType;
        }

        return path;
    }

    /**********************************************************************************************
     * Get the object identifier bas on the presence or absence of the array size
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
