/**
 * CFS Command & Data Dictionary EDS handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.awt.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.ccsds.schema.sois.seds.ArrayDataType;
import org.ccsds.schema.sois.seds.ArrayDimensionsType;
import org.ccsds.schema.sois.seds.BaseTypeSetType;
import org.ccsds.schema.sois.seds.ByteOrderType;
import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.ContainerDataType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
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

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FileEnvVar;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
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
     * Importing data in EDS format is not supported
     *********************************************************************************************/
    @Override
    public void importFromFile(FileEnvVar importFile, ImportType importType) throws CCDDException,
                                                                             IOException,
                                                                             Exception
    {
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
        device.setName(dbControl.getDatabaseName());
        device.setShortDescription(dbControl.getDatabaseDescription(dbControl.getDatabaseName()));
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
                                             (minColumn != -1 && !rowData[minColumn].isEmpty()
                                                                                               ? rowData[minColumn]
                                                                                               : null),
                                             (maxColumn != -1 && !rowData[maxColumn].isEmpty()
                                                                                               ? rowData[maxColumn]
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
            childSpace.setShortDescription(description);
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
                                       boolean isTlmHeader,
                                       String applicationID,
                                       String ccsdsAppID)
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
            // Not the telemetry header. Check if a telemetry header exists
            else if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
            {
                containerType.setName(tableInfo.getPrototypeName() + TYPE);

                // Check if this is a root structure (instance structures don't require a
                // reference to the telemetry header) and this isn't the command header table
                if (tableInfo.isRootStructure() && !namespace.getName().equals(tlmHeaderTable))
                {
                    InterfaceDeclarationType intParmType = null;

                    // Step through the interfaces in order to locate the name space's parameter
                    // set
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

                    // Check if a parameter set doesn't exist
                    if (intParmType == null)
                    {
                        // Create the parameter set for this name space
                        intParmType = createParameterSet(namespace);
                    }

                    // Set the parameter header as the base
                    BaseTypeSetType baseType = factory.createBaseTypeSetType();
                    InterfaceRefType intfcType = factory.createInterfaceRefType();
                    intfcType.setType(tlmHeaderTable + "/" + TELEMETRY);
                    baseType.getBaseType().add(intfcType);
                    intParmType.setBaseTypeSet(baseType);
                }
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
    private void addParameter(NamespaceType namespace,
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
                        minimum,
                        maximum,
                        description,
                        stringSize,
                        "");

            // Check if this is not the telemetry header table
            if (!dataType.equals(tlmHeaderTable))
            {
                // Build the parameter attributes
                InterfaceParameterType parameter = factory.createInterfaceParameterType();
                parameter.setName(parameterName);
                parameter.setType(getReferenceByDataType(parameterName, dataType, false)
                                  + getObjectIdentifier(arraySize));

                // Check if a description is provided for this parameter
                if (description != null && !description.isEmpty())
                {
                    // Set the parameter's description
                    parameter.setShortDescription(description);
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

                        // Step through the other associated command argument columns
                        for (int otherCol : cmdArg.getOther())
                        {
                            // Check if this is the array size column
                            if (typeDefn.getInputTypes()[otherCol] == InputDataType.ARRAY_INDEX)
                            {
                                // Store the command argument array size
                                arraySize = rowData[otherCol];

                                // Check if the command argument has a string data type
                                if (rowData[cmdArg.getDataType()].equals(DefaultPrimitiveTypeInfo.STRING.getUserName()))
                                {

                                    // Separate the array dimension values and get the string size
                                    int[] arrayDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                                    stringSize = arrayDims[0];
                                }
                            }
                            // Check if this is the bit length column
                            else if (typeDefn.getInputTypes()[otherCol] == InputDataType.BIT_LENGTH)
                            {
                                // Store the command argument bit length
                                bitLength = rowData[otherCol];
                            }
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
                                    minimum,
                                    maximum,
                                    description,
                                    stringSize,
                                    uniqueID);

                        // Add a command argument to the command metadata
                        CommandArgumentType argType = factory.createCommandArgumentType();
                        argType.setName(argumentName);
                        argType.setType(argumentName + TYPE + uniqueID);

                        // Check if the command argument description exists
                        if (description != null && !description.isEmpty())
                        {
                            // Set the description
                            argType.setShortDescription(description);
                        }

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
            command.setShortDescription(description);
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
                             String minimum,
                             String maximum,
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

            // Check if the a corresponding XTCE data type exists
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

                            // Set the minimum and/or maximum
                            MinMaxRangeType minMaxInteger = factory.createMinMaxRangeType();
                            minMaxInteger.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                minMaxInteger.setMin(BigDecimal.valueOf(Integer.valueOf(minimum)));
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                minMaxInteger.setMax(BigDecimal.valueOf(Integer.valueOf(maximum)));
                            }

                            // Set the range, byte order, and units
                            integerRange.setMinMaxRange(minMaxInteger);
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

                            // Set the minimum and/or maximum
                            MinMaxRangeType minMaxFloat = factory.createMinMaxRangeType();
                            minMaxFloat.setRangeType(RangeType.INCLUSIVE_MIN_INCLUSIVE_MAX);

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                minMaxFloat.setMin(BigDecimal.valueOf(Float.valueOf(minimum)));
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                minMaxFloat.setMax(BigDecimal.valueOf(Float.valueOf(maximum)));
                            }

                            // Set the range, byte order, and units
                            // TODO SETTING MinMaxRange FOR A FLOAT IS FLAGGED AS AN ERROR IN THE
                            // EDS VIEWER
                            floatRange.setMinMaxRange(minMaxFloat);
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
            parameterDescription.setShortDescription(description);
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
