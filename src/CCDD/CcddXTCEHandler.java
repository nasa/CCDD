/**
 * CFS Command & Data Dictionary XTCE handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.NUM_HIDDEN_COLUMNS;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
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

import org.omg.space.xtce.AggregateDataType;
import org.omg.space.xtce.ArgumentTypeSetType;
import org.omg.space.xtce.BaseDataType;
import org.omg.space.xtce.BaseDataType.UnitSet;
import org.omg.space.xtce.CommandMetaDataType.MetaCommandSet;
import org.omg.space.xtce.ContainerRefEntryType;
import org.omg.space.xtce.ContainerSetType;
import org.omg.space.xtce.EntryListType;
import org.omg.space.xtce.EnumeratedDataType;
import org.omg.space.xtce.EnumeratedDataType.EnumerationList;
import org.omg.space.xtce.FloatDataEncodingType;
import org.omg.space.xtce.HeaderType;
import org.omg.space.xtce.IntegerDataEncodingType;
import org.omg.space.xtce.IntegerValueType;
import org.omg.space.xtce.MetaCommandType;
import org.omg.space.xtce.MetaCommandType.ArgumentList;
import org.omg.space.xtce.MetaCommandType.ArgumentList.Argument;
import org.omg.space.xtce.NameDescriptionType;
import org.omg.space.xtce.ObjectFactory;
import org.omg.space.xtce.ParameterPropertiesType;
import org.omg.space.xtce.ParameterRefEntryType;
import org.omg.space.xtce.ParameterSetType;
import org.omg.space.xtce.ParameterSetType.Parameter;
import org.omg.space.xtce.ParameterTypeSetType.EnumeratedParameterType;
import org.omg.space.xtce.ParameterTypeSetType.FloatParameterType;
import org.omg.space.xtce.ParameterTypeSetType.IntegerParameterType;
import org.omg.space.xtce.ParameterTypeSetType.StringParameterType;
import org.omg.space.xtce.SequenceContainerType;
import org.omg.space.xtce.SpaceSystemType;
import org.omg.space.xtce.StringDataEncodingType;
import org.omg.space.xtce.StringDataEncodingType.SizeInBits;
import org.omg.space.xtce.StringDataType;
import org.omg.space.xtce.UnitType;
import org.omg.space.xtce.ValueEnumerationType;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
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

    // Lists containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and XTCE object references
    private JAXBElement<SpaceSystemType> project;
    private Marshaller marshaller;
    private ObjectFactory factory;

    // Attribute strings
    private String versionAttr;
    private String validationStatusAttr;
    private String classification1Attr;
    private String classification2Attr;
    private String classification3Attr;

    // Conversion setup error flag
    private boolean errorFlag;

    // XTCE data types
    private enum XTCEDataType
    {
        INTEGER,
        FLOAT,
        STRING
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
     * Importing data in XTCE format is not supported
     *********************************************************************************************/
    @Override
    public void importFromFile(File importFile, ImportType importType) throws CCDDException,
                                                                       IOException,
                                                                       Exception
    {
        // XTCE import is not supported
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
     *            [0] version attribute <br>
     *            [1] validation status attribute <br>
     *            [2] first level classification attribute <br>
     *            [3] second level classification attribute <br>
     *            [4] third level classification attribute
     *
     * @return true if an error occurred preventing exporting the project to the file
     *********************************************************************************************/
    @Override
    public boolean exportToFile(File exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                boolean includeReservedMsgIDs,
                                boolean includeVariablePaths,
                                CcddVariableSizeAndConversionHandler variableHandler,
                                String[] separators,
                                String... extraInfo)
    {
        boolean errorFlag = false;

        try
        {
            // Convert the table data into XTCE XML format
            convertTablesToXTCE(tableNames,
                                variableHandler,
                                separators,
                                extraInfo[0],
                                extraInfo[1],
                                extraInfo[2],
                                extraInfo[3],
                                extraInfo[4]);

            try
            {
                // Output the file creation information
                marshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders",
                                       "\n<!-- Created "
                                                                               + new Date().toString()
                                                                               + " : project = "
                                                                               + dbControl.getDatabaseName()
                                                                               + " : host = "
                                                                               + dbControl.getServer()
                                                                               + " : user = "
                                                                               + dbControl.getUser()
                                                                               + " -->");
            }
            catch (JAXBException je)
            {
                // Ignore the error if setting this property fails; the comment is not included
            }

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
                                     String version,
                                     String validationStatus,
                                     String classification1,
                                     String classification2,
                                     String classification3)
    {
        // Store the attributes
        versionAttr = version;
        validationStatusAttr = validationStatus;
        classification1Attr = classification1;
        classification2Attr = classification2;
        classification3Attr = classification3;

        // Create the root space system
        SpaceSystemType rootSystem = addSpaceSystem(null,
                                                    dbControl.getDatabaseName(),
                                                    dbControl.getDatabaseDescription(dbControl.getDatabaseName()),
                                                    classification1Attr,
                                                    validationStatusAttr,
                                                    versionAttr,
                                                    null);

        // Add the project's space systems, parameters, and commands
        buildSpaceSystems(tableNames, variableHandler, separators, rootSystem);
    }

    /**********************************************************************************************
     * Get the value of the system name data field for the specified table
     *
     * @param tableName
     *            table name, including path
     *
     * @return Value of the system name data field for the specified table; null if the table
     *         doesn't have a system name data field
     *********************************************************************************************/
    private String getSystemName(String tableName)
    {
        String systemName = null;

        // Build the data fields information for this table
        fieldHandler.buildFieldInformation(tableName);

        // Step through the table's data fields
        for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
        {
            // Check if this field contains the system name and isn't blank
            if (fieldInfo.getInputType() == InputDataType.SYSTEM_NAME
                && !fieldInfo.getValue().isEmpty())
            {
                // Store the system name and stop searching
                systemName = fieldInfo.getValue();
                break;
            }
        }

        return systemName;
    }

    /**********************************************************************************************
     * Export parameter container
     *
     * @param system
     *            space system
     *
     * @param tableData
     *            table data array
     *
     * @param varColumn
     *            variable name column index (model coordinates)
     *
     * @param typeColumn
     *            data type column index (model coordinates)
     *
     * @param sizeColumn
     *            array size column index (model coordinates)
     *********************************************************************************************/
    // TODO
    private void exportParameterContainer(SpaceSystemType system,
                                          String[][] tableData,
                                          int varColumn,
                                          int typeColumn,
                                          int sizeColumn)
    {
        ContainerSetType seqContainerSet = null;
        SequenceContainerType seqContainer = null;
        EntryListType entryList = new EntryListType();

        // Step through each row of data in the structure table
        for (String[] rowData : tableData)
        {
            // Check if this parameter has a primitive data type (i.e., it isn't an instance of a
            // structure)
            if (dataTypeHandler.isPrimitive(rowData[typeColumn]))
            {
                // Get the variable name, including its path
                String varName = macroHandler.getMacroExpansion(rowData[varColumn]);

                // Check if this isn't an array definition
                if (rowData[sizeColumn].isEmpty() || ArrayVariable.isArrayMember(varName))
                {
                    // Get the index of the variable's bit length, if present
                    int bitIndex = varName.indexOf(":");

                    // Check if this variable has a bit length
                    if (bitIndex != -1)
                    {
                        // Remove the bit length from the variable name
                        varName = varName.substring(0, bitIndex);
                    }

                    // Store the parameter name in the list
                    ParameterRefEntryType parameterRef = new ParameterRefEntryType();
                    parameterRef.setParameterRef(varName);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(parameterRef);
                }
            }
            // Structure data type
            else
            {
                // Get the name of the system to which this table belongs
                String systemName = getSystemName(rowData[typeColumn]);

                // Store the structure in the list
                ContainerRefEntryType containerType = new ContainerRefEntryType();
                containerType.setContainerRef("/" + project.getValue().getName()
                                              + (systemName == null
                                                                    ? ""
                                                                    : "/" + systemName)
                                              + "/" + rowData[typeColumn]
                                              + "/" + rowData[typeColumn]);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerType);
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
                    seqContainerSet = new ContainerSetType();
                }

                // Create the parameter sequence container and set the name
                seqContainer = new SequenceContainerType();
                seqContainer.setName(system.getName());
            }

            // Store the parameters in the parameter sequence container
            seqContainer.setEntryList(entryList);
            seqContainerSet.getSequenceContainer().add(seqContainer);
        }

        // Check if any parameters exist
        if (seqContainerSet != null)
        {
            // Check if the telemetry metadata doesn't exit for this system
            if (system.getTelemetryMetaData() == null)
            {
                // Create the telemetry metadata
                createTelemetryMetadata(system);
            }

            // Add the parameters to the system
            system.getTelemetryMetaData().setContainerSet(seqContainerSet);
        }
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
     *
     * @param parentSystem
     *            parent space system for this node
     *********************************************************************************************/
    private void buildSpaceSystems(String[] tableNames,
                                   CcddVariableSizeAndConversionHandler variableHandler,
                                   String[] separators,
                                   SpaceSystemType parentSystem)
    {
        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the information from the database for the specified table
            TableInformation tableInfo = dbTable.loadTableData(tableName,
                                                               true,
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
                typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());

                // Get the table's basic type - structure, command, or the original table type if
                // not structure or command table
                String tableType = typeDefn.isStructure()
                                                          ? TYPE_STRUCTURE
                                                          : typeDefn.isCommand()
                                                                                 ? TYPE_COMMAND
                                                                                 : tableInfo.getType();

                // Check if the table type is valid
                if (tableType != null)
                {
                    // Replace all macro names with their corresponding values
                    tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));

                    // Build the data fields information for this table
                    fieldHandler.buildFieldInformation(tableName);

                    // Get the name of the system to which this table belongs
                    String systemName = getSystemName(tableName);

                    // Check if the table has no system name defined
                    if (systemName == null)
                    {
                        // Set the system name to the root space system
                        systemName = project.getValue().getName();
                    }

                    // Search the existing space systems for one with this name
                    SpaceSystemType existingSystem = getSpaceSystemByName(systemName);

                    // Set the parent system to the existing system if found, else create a new
                    // space system using the system name from the table's data field
                    parentSystem = (existingSystem == null)
                                                            ? addSpaceSystem(parentSystem,
                                                                             systemName,
                                                                             null,
                                                                             classification2Attr,
                                                                             validationStatusAttr,
                                                                             versionAttr,
                                                                             null)
                                                            : existingSystem;

                    // Check if this is a node for a structure table
                    if (tableType.equals(TYPE_STRUCTURE))
                    {
                        String messageIDName = null;
                        String messageID = null;

                        // Step through each of the table's data fields
                        for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
                        {
                            // Check if this is the first non-empty field containing a message ID
                            // name
                            if (messageIDName == null
                                && fieldInfo.getInputType() == InputDataType.MESSAGE_ID_NAME
                                && !fieldInfo.getValue().isEmpty())
                            {
                                // Store the message ID name
                                messageIDName = fieldInfo.getValue();
                            }
                            // Check if this is the first non-empty field containing a message ID
                            // value
                            else if (messageID == null
                                     && fieldInfo.getInputType() == InputDataType.MESSAGE_ID
                                     && !fieldInfo.getValue().isEmpty())
                            {
                                // Store the message ID value
                                messageID = fieldInfo.getValue();
                            }

                            // Check if both the message ID name and value have been found
                            if (messageIDName != null && messageID != null)
                            {
                                // Stop searching
                                break;
                            }
                        }

                        // Get the default column indices
                        int varColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                        int typeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                        int sizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                        int bitColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                        int enumColumn = typeDefn.getColumnIndexByInputType(InputDataType.ENUMERATION);
                        int descColumn = typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION);
                        int unitsColumn = typeDefn.getColumnIndexByInputType(InputDataType.UNITS);

                        // Add the structure
                        parentSystem = addSpaceSystem(parentSystem,
                                                      tableName,
                                                      tableInfo.getDescription(),
                                                      classification3Attr,
                                                      validationStatusAttr,
                                                      versionAttr,
                                                      tableInfo.getType());

                        // Export the parameter container for this structure
                        exportParameterContainer(parentSystem,
                                                 tableInfo.getData(),
                                                 varColumn,
                                                 typeColumn,
                                                 sizeColumn);

                        // Step through each row in the table
                        for (int row = 0; row < tableInfo.getData().length; row++)
                        {
                            // Get the array size column value
                            String arraySize = tableInfo.getData()[row][sizeColumn];

                            // Check if the variable isn't an array definition
                            if (arraySize.isEmpty()
                                || ArrayVariable.isArrayMember(tableInfo.getData()[row][varColumn]))
                            {
                                boolean isTelemetered = false;

                                // Step through each rate column
                                for (int rateColumn : typeDefn.getColumnIndicesByInputType(InputDataType.RATE))
                                {
                                    // Check if the variable has a rate value
                                    if (!tableInfo.getData()[row][rateColumn].isEmpty())
                                    {
                                        // Set the flag to indicate the variable has a rate and
                                        // stop searching
                                        isTelemetered = true;
                                        break;
                                    }
                                }

                                // Add the variable to the data sheet
                                addSpaceSystemParameter(parentSystem,
                                                        tableInfo,
                                                        row,
                                                        varColumn,
                                                        typeColumn,
                                                        sizeColumn,
                                                        bitColumn,
                                                        enumColumn,
                                                        unitsColumn,
                                                        descColumn,
                                                        isTelemetered);
                            }
                        }
                    }
                    // Not a structure table node; i.e., it's a command or user-defined table type
                    else
                    {
                        // Check if this is a command table
                        if (tableType.equals(TYPE_COMMAND))
                        {
                            // Add the user-defined table to the space system
                            parentSystem = addSpaceSystem(parentSystem,
                                                          tableName,
                                                          tableInfo.getDescription(),
                                                          classification3Attr,
                                                          validationStatusAttr,
                                                          versionAttr,
                                                          tableInfo.getType());

                            // Add the command(s) from this table to the space system
                            addSpaceSystemCommands(parentSystem, tableInfo);
                        }
                        // TODO SHOULD NON-STRUCTURE/COMMAND TABLES BE DISCARDED?
                        // Not a command (or structure) table; i.e., it's a user-defined table type
                        else
                        {
                            // Add the user-defined table to the space system
                            parentSystem = addSpaceSystem(parentSystem,
                                                          tableName,
                                                          tableInfo.getDescription(),
                                                          classification3Attr,
                                                          validationStatusAttr,
                                                          versionAttr,
                                                          tableInfo.getType());
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
     * @param system
     *            parent space system for the new system; null for the root space system
     *
     * @param subsystemName
     *            name for the new space system
     *
     * @param shortDescription
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
     * @param tableType
     *            table's type name
     *
     * @return Reference to the new space system
     *********************************************************************************************/
    private SpaceSystemType addSpaceSystem(SpaceSystemType system,
                                           String subsystemName,
                                           String shortDescription,
                                           String classification,
                                           String validationStatus,
                                           String version,
                                           String tableType)
    {
        // Create the new space system and set the name attribute
        SpaceSystemType subsystem = factory.createSpaceSystemType();
        subsystem.setName(subsystemName);

        // Check if a description is provided
        if (shortDescription != null && !shortDescription.isEmpty())
        {
            // Set the description attribute
            subsystem.setShortDescription(shortDescription);
        }

        // Set the new space system's header attributes
        setHeader(subsystem,
                  classification,
                  validationStatus,
                  version,
                  (system == null ? new Date().toString() : null));

        // Check if this is the root space system
        if (system == null)
        {
            // Set this space system as the root system
            project = factory.createSpaceSystem(subsystem);
        }
        // Not the root space system
        else
        {
            // Add the new space system as a child of the specified system
            system.getSpaceSystem().add(subsystem);
        }

        return subsystem;
    }

    /**********************************************************************************************
     * Get the reference to the space system with the specified name, starting at the root space
     * system
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @return Reference to the space system with the same name as the search name; null if no
     *         space system name matches the search name
     *********************************************************************************************/
    private SpaceSystemType getSpaceSystemByName(String systemName)
    {
        // Search the space system hierarchy, beginning at the root system
        return searchSpaceSystemsForName(systemName, project.getValue(), null);
    }

    /**********************************************************************************************
     * Recursively search through the space system tree for the space system with the same name as
     * the search name
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @param system
     *            current space system to check
     *
     * @param foundSystem
     *            space system that matches the search name; null if no match has been found
     *
     * @return Reference to the space system with the same name as the search name; null if no
     *         space system name matches the search name
     *********************************************************************************************/
    private SpaceSystemType searchSpaceSystemsForName(String systemName,
                                                      SpaceSystemType system,
                                                      SpaceSystemType foundSystem)
    {
        // Check if the space system hasn't been found
        if (foundSystem == null)
        {
            // Check if the current system's name matches the search name
            if (system.getName().equals(systemName))
            {
                // Store the reference to the matching system
                foundSystem = system;
            }
            // Check if the space system has subsystems
            else if (!system.getSpaceSystem().isEmpty())
            {
                // Step through each subsystem
                for (SpaceSystemType sys : system.getSpaceSystem())
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
     * @param system
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
    private void setHeader(SpaceSystemType system,
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
        system.setHeader(header);
    }

    /**********************************************************************************************
     * Create the space system telemetry metadata
     *
     * @param system
     *            space system
     *********************************************************************************************/
    private void createTelemetryMetadata(SpaceSystemType system)
    {
        system.setTelemetryMetaData(factory.createTelemetryMetaDataType());
        system.getTelemetryMetaData().setParameterSet(factory.createParameterSetType());
        system.getTelemetryMetaData().setParameterTypeSet(factory.createParameterTypeSetType());
    }

    /**********************************************************************************************
     * Add a variable to the specified space system
     *
     * @param spaceSystem
     *            parent space system for this node
     *
     * @param tableInfo
     *            TableInformation reference for the current node
     *
     * @param row
     *            row index in the data table for the current variable
     *
     * @param varColumn
     *            variable name column index
     *
     * @param typeColumn
     *            data type column index
     *
     * @param sizeColumn
     *            array size column index
     *
     * @param bitColumn
     *            bit length column index
     *
     * @param enumColumn
     *            first enumeration column index; -1 if none exists
     *
     * @param unitsColumn
     *            units column index; -1 if none exists
     *
     * @param descColumn
     *            description column index; -1 if none exists
     *
     * @param isTelemetered
     *            true if the parameter has a downlink rate
     *********************************************************************************************/
    private void addSpaceSystemParameter(SpaceSystemType spaceSystem,
                                         TableInformation tableInfo,
                                         int row,
                                         int varColumn,
                                         int typeColumn,
                                         int sizeColumn,
                                         int bitColumn,
                                         int enumColumn,
                                         int unitsColumn,
                                         int descColumn,
                                         boolean isTelemetered)
    {
        // Initialize the parameter attributes
        String variableName = tableInfo.getData()[row][varColumn];
        String dataType = tableInfo.getData()[row][typeColumn];
        String arraySize = tableInfo.getData()[row][sizeColumn];
        String bitLength = tableInfo.getData()[row][bitColumn];
        String enumeration = null;
        String units = null;
        String description = null;
        int stringSize = 1;

        // Check if the data type is a string and if the array size column isn't empty
        if (dataTypeHandler.isString(tableInfo.getData()[row][typeColumn])
            && !tableInfo.getData()[row][sizeColumn].isEmpty())
        {
            int defnRow = row;

            // Check if the variable name is an array member
            while (tableInfo.getData()[defnRow][varColumn].endsWith("]"))
            {
                // Step back through the rows until the array definition is located
                defnRow--;
            }

            // Get the size of the string, which is the last array size value in the string array's
            // definition row
            stringSize = Integer.valueOf(tableInfo.getData()[defnRow][sizeColumn].replaceAll("^.*(\\d+)$", "$1"));
        }

        // Step through each column in the row
        for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData()[row].length; column++)
        {
            // Check that this is not the variable name column, or the bit length column and the
            // variable has no bit length, and that a value exists in the column
            if ((column != varColumn
                 || (column == bitColumn && bitLength != null))
                && !tableInfo.getData()[row][column].isEmpty())
            {
                // Check if this is an enumeration column
                if (column == enumColumn)
                {
                    // Get the enumeration text
                    enumeration = tableInfo.getData()[row][column];
                }
                // Check if this is the units column
                else if (column == unitsColumn)
                {
                    // Get the units text
                    units = tableInfo.getData()[row][unitsColumn];
                }
                // Check if this is the description column
                else if (column == descColumn)
                {
                    // Get the description text
                    description = tableInfo.getData()[row][descColumn];
                }
            }
        }

        // Add the variable to the space system
        addParameter(spaceSystem,
                     variableName,
                     dataType,
                     (typeColumn == -1
                                       ? null
                                       : typeDefn.getColumnNamesUser()[typeColumn]),
                     arraySize,
                     bitLength,
                     enumeration,
                     units,
                     description,
                     stringSize,
                     isTelemetered);
    }

    /**********************************************************************************************
     * Add a telemetry parameter to the telemetry metadata
     *
     * @param system
     *            space system
     *
     * @param parameterName
     *            parameter name
     *
     * @param dataType
     *            parameter primitive data type
     *
     * @param dataTypeColumn
     *            data type column index
     *
     * @param arraySize
     *            parameter array size; blank if not an array definition or member
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
     *
     * @param isTelemetered
     *            true if the parameter has a downlink rate
     *********************************************************************************************/
    private void addParameter(SpaceSystemType system,
                              String parameterName,
                              String dataType,
                              String dataTypeColumn,
                              String arraySize,
                              String bitLength,
                              String enumeration,
                              String units,
                              String description,
                              int stringSize,
                              boolean isTelemetered)
    {
        // Check if this system doesn't yet have its telemetry metadata created
        if (system.getTelemetryMetaData() == null)
        {
            // Create the telemetry metadata
            createTelemetryMetadata(system);
        }

        // Check if a data type is provided
        if (dataType != null)
        {
            // Get the parameter's data type information
            NameDescriptionType type = setDataType(system,
                                                   parameterName,
                                                   dataType,
                                                   bitLength,
                                                   enumeration,
                                                   units,
                                                   description,
                                                   stringSize);

            // Check if the type information was successfully created
            if (type != null)
            {
                // Set the parameter's data type information
                system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(type);
            }
        }

        // Create the parameter. This links the parameter name with the parameter reference type
        ParameterSetType parameterSet = system.getTelemetryMetaData().getParameterSet();
        Parameter parameter = factory.createParameterSetTypeParameter();
        parameter.setName(parameterName);
        parameter.setParameterTypeRef(parameterName + "Type");
        ParameterPropertiesType properties = factory.createParameterPropertiesType();
        properties.setDataSource(isTelemetered
                                               ? "telemetered"
                                               : "local");
        parameter.setParameterProperties(properties);
        parameterSet.getParameterOrParameterRef().add(parameter);
    }

    /**********************************************************************************************
     * Create the space system command metadata
     *
     * @param system
     *            space system
     *********************************************************************************************/
    private void createCommandMetadata(SpaceSystemType system)
    {
        system.setCommandMetaData(factory.createCommandMetaDataType());
        system.getCommandMetaData().setArgumentTypeSet(factory.createArgumentTypeSetType());
        system.getCommandMetaData().setMetaCommandSet(factory.createCommandMetaDataTypeMetaCommandSet());
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified space system
     *
     * @param spaceSystem
     *            parent space system for this node
     *
     * @param tableInfo
     *            TableInformation reference for the current node
     *********************************************************************************************/
    private void addSpaceSystemCommands(SpaceSystemType spaceSystem, TableInformation tableInfo)
    {
        // Get the list containing command argument name, data type, enumeration, minimum, maximum,
        // and other associated column indices for each argument grouping
        List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

        // Step through each row in the table
        for (String[] rowData : tableInfo.getData())
        {
            // Initialize the command attributes and argument number list
            String commandName = null;
            String commandCode = null;
            String commandDescription = null;
            List<String> argumentNames = new ArrayList<String>();

            // Create an array of flags to indicate if the column is a command argument that has
            // been processed
            boolean[] isCmdArg = new boolean[rowData.length];

            // Step through each column in the row
            for (int colA = NUM_HIDDEN_COLUMNS; colA < rowData.length; colA++)
            {
                // Check that this isn't the column containing the table's primary key or row
                // index, and that the column value isn't blank
                if (!rowData[colA].isEmpty())
                {
                    // Check if this is the command name column
                    if (typeDefn.getColumnNamesUser()[colA].equalsIgnoreCase(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME)))
                    {
                        // Store the command name
                        commandName = rowData[colA];
                    }
                    // Not the command name column; check for other overall command and command
                    // argument columns
                    else
                    {
                        // Initialize the command argument attributes
                        String argName = null;
                        String dataType = null;
                        String enumeration = null;
                        String units = null;
                        String description = null;
                        int stringSize = 1;

                        // Step through each command argument column grouping
                        for (AssociatedColumns cmdArg : commandArguments)
                        {
                            // Check if this is the command argument name column
                            if (colA == cmdArg.getName())
                            {
                                // Store the command argument name
                                argName = rowData[colA];

                                // Set the flag indicating the column is a command argument
                                isCmdArg[colA] = true;

                                // Step through the remaining columns in the row to look for the
                                // other members of this argument grouping
                                for (int colB = colA + 1; colB < rowData.length; colB++)
                                {
                                    // Set the flag to indicate if the column is associated with a
                                    // command argument
                                    isCmdArg[colB] = colB == cmdArg.getDataType()
                                                     || colB == cmdArg.getEnumeration()
                                                     || colB == cmdArg.getDescription()
                                                     || colB == cmdArg.getUnits()
                                                     || colB == cmdArg.getMinimum()
                                                     || colB == cmdArg.getMaximum()
                                                     || cmdArg.getOther().contains(colB);

                                    // Check if this is a command argument column and that the
                                    // value isn't blank
                                    if (isCmdArg[colB] && !rowData[colB].isEmpty())
                                    {
                                        // Check if this is the command argument data type column
                                        if (colB == cmdArg.getDataType())
                                        {
                                            // Store the command argument data type
                                            dataType = rowData[colB];
                                        }
                                        // Check if this is the command argument enumeration column
                                        else if (colB == cmdArg.getEnumeration())
                                        {
                                            // Store the command argument enumeration
                                            enumeration = rowData[colB];
                                        }
                                        // Check if this is the command argument description column
                                        else if (colB == cmdArg.getDescription())
                                        {
                                            // Store the command argument description
                                            description = rowData[colB];
                                        }
                                        // Check if this is the command argument units column
                                        else if (colB == cmdArg.getUnits())
                                        {
                                            // Store the command argument units
                                            units = rowData[colB];
                                        }
                                        // Check if this is the array size column for a command
                                        // argument with a string data type
                                        else if (typeDefn.getInputTypes()[colB] == InputDataType.ARRAY_INDEX
                                                 && rowData[cmdArg.getDataType()].equals(DefaultPrimitiveTypeInfo.STRING.getUserName()))
                                        {
                                            // Separate the array dimension values and get the
                                            // string size
                                            int[] arrayDims = ArrayVariable.getArrayIndexFromSize(rowData[colB]);
                                            stringSize = arrayDims[0];
                                        }
                                    }
                                }

                                // Check if the command argument has the minimum parameters
                                // required: a name and data type
                                if (argName != null && !argName.isEmpty() && dataType != null)
                                {
                                    // Add the name to the argument name list
                                    argumentNames.add(argName);

                                    // Add the command to the command space system
                                    addCommandArgument(spaceSystem,
                                                       commandName,
                                                       argName,
                                                       dataType,
                                                       enumeration,
                                                       units,
                                                       description,
                                                       stringSize);
                                }

                                // Stop searching since a match was found
                                break;
                            }
                        }
                    }
                }
            }

            // Step through each column in the row again in order to assign the overall command
            // information
            for (int col = NUM_HIDDEN_COLUMNS; col < rowData.length; col++)
            {
                // Check that this is not one of the command argument columns, the column
                // value isn't blank, and this column is for the command description
                if (!isCmdArg[col] && !rowData[col].isEmpty())
                    // Check if this is the first non-empty field containing a the command code
                    if (commandCode == null
                        && col == typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_CODE))
                    {
                        // Store the command code
                        commandCode = rowData[col];
                    }
                    // Check if this is the first non-empty field containing a the command
                    // description
                    else if (commandDescription == null
                             && col == typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION))
                    {
                        // Store the command description
                        commandDescription = rowData[col];
                    }

                // Check if both the command code and description have been found
                if (commandCode != null && commandDescription != null)
                {
                    // Stop searching
                    break;
                }
            }

            // Check if the command name exists
            if (commandName != null)
            {
                // Add the command metadata set information
                addCommand(spaceSystem,
                           commandName,
                           commandCode,
                           argumentNames,
                           commandDescription);
            }
        }
    }

    /**********************************************************************************************
     * Add a command metadata set to the command metadata
     *
     * @param system
     *            space system
     *
     * @param commandName
     *            command name
     *
     * @param argumentNames
     *            list of command argument names
     *
     * @param commandCode
     *            command code
     *
     * @param shortDescription
     *            short description of the command
     *********************************************************************************************/
    private void addCommand(SpaceSystemType system,
                            String commandName,
                            String commandCode,
                            List<String> argumentNames,
                            String shortDescription)
    {
        // TODO FIND PLACE TO STORE THE COMMAND CODE

        // Check if this system doesn't yet have its command metadata created
        if (system.getCommandMetaData() == null)
        {
            // Create the command metadata
            createCommandMetadata(system);
        }

        MetaCommandSet commandSet = system.getCommandMetaData().getMetaCommandSet();
        MetaCommandType command = factory.createMetaCommandType();

        // Set the command name attribute
        command.setName(commandName);

        // Check is a command description exists
        if (shortDescription != null)
        {
            // Set the command description attribute
            command.setShortDescription(shortDescription);
        }

        // Check if the command has any arguments
        if (!argumentNames.isEmpty())
        {
            ArgumentList argList = factory.createMetaCommandTypeArgumentList();

            // Step through each argument
            for (String argName : argumentNames)
            {
                // Add the argument to the the command's argument list
                Argument arg = new Argument();
                arg.setName(argName);
                argList.getArgument().add(arg);
            }

            command.setArgumentList(argList);
        }

        commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand().add(command);
    }

    /**********************************************************************************************
     * Add a command argument to the command metadata
     *
     * @param system
     *            space system
     *
     * @param commandName
     *            command name
     *
     * @param argumentName
     *            command argument name
     *
     * @param dataType
     *            command argument primitive data type
     *
     * @param enumeration
     *            command enumeration in the format <enum label>=<enum value>
     *
     * @param units
     *            command argument units
     *
     * @param shortDescription
     *            short description of the command
     *
     * @param stringSize
     *            string size in bytes; ignored if the command argument does not have a string data
     *            type
     *********************************************************************************************/
    private void addCommandArgument(SpaceSystemType system,
                                    String commandName,
                                    String argumentName,
                                    String dataType,
                                    String enumeration,
                                    String units,
                                    String shortDescription,
                                    int stringSize)
    {
        // Check if this system doesn't yet have its command metadata created
        if (system.getCommandMetaData() == null)
        {
            // Create the command metadata
            createCommandMetadata(system);
        }

        NameDescriptionType type = setArgumentType(system,
                                                   commandName,
                                                   argumentName,
                                                   dataType,
                                                   enumeration,
                                                   units,
                                                   shortDescription,
                                                   stringSize);
        ArgumentTypeSetType argument = system.getCommandMetaData().getArgumentTypeSet();
        argument.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(type);
    }

    /**********************************************************************************************
     * Convert the primitive data type into the XTCE equivalent
     *
     * @param dataType
     *            data type
     *
     * @return XTCE data type corresponding to the specified primitive data type; null if no match
     *********************************************************************************************/
    private XTCEDataType getXTCEDataType(String dataType)
    {
        XTCEDataType xtceDataType = null;

        // Check if the type is an integer (signed or unsigned)
        if (dataTypeHandler.isInteger(dataType))
        {
            xtceDataType = XTCEDataType.INTEGER;
        }
        // Check if the type is a floating point (float or double)
        else if (dataTypeHandler.isFloat(dataType))
        {
            xtceDataType = XTCEDataType.FLOAT;
        }
        // Check if the type is a string (character or string)
        else if (dataTypeHandler.isCharacter(dataType))
        {
            xtceDataType = XTCEDataType.STRING;
        }

        return xtceDataType;
    }

    /**********************************************************************************************
     * Create the telemetry data type and set the specified attributes
     *
     * @param system
     *            space system
     *
     * @param parameterName
     *            parameter name; null to not specify
     *
     * @param dataType
     *            data type; null to not specify
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
     *            parameter description; null or blank to not specify
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *
     * @return Parameter description of the type corresponding to the primitive data type with the
     *         specified attributes set
     *********************************************************************************************/
    private NameDescriptionType setDataType(SpaceSystemType system,
                                            String parameterName,
                                            String dataType,
                                            String bitLength,
                                            String enumeration,
                                            String units,
                                            String description,
                                            int stringSize)
    {
        NameDescriptionType parameterDescription = null;

        // Check if the parameter has a primitive data type
        if (dataTypeHandler.isPrimitive(dataType))
        {
            BaseDataType baseType = null;

            // Get the XTCE data type corresponding to the primitive data type
            XTCEDataType xtceDataType = getXTCEDataType(dataType);

            // Check if the a corresponding XTCE data type exists
            if (xtceDataType != null)
            {
                // Set the parameter units
                UnitSet unitSet = createUnitSet(units);

                // Check if enumeration parameters are provided
                if (enumeration != null)
                {
                    // Create an enumeration type and enumeration list, and add any extra
                    // enumeration parameters as column data
                    baseType = factory.createParameterTypeSetTypeEnumeratedParameterType();
                    EnumerationList enumList = createEnumerationList(system, enumeration);

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

                    ((EnumeratedParameterType) baseType).setIntegerDataEncoding(intEncodingType);

                    // Set the enumeration list and units attributes
                    ((EnumeratedParameterType) baseType).setEnumerationList(enumList);
                    ((EnumeratedParameterType) baseType).setUnitSet(unitSet);
                }
                // Not an enumeration
                else
                {
                    switch (xtceDataType)
                    {
                        case INTEGER:
                            // Create an integer parameter and set its attributes
                            baseType = factory.createParameterTypeSetTypeIntegerParameterType();
                            ((IntegerParameterType) baseType).setUnitSet(unitSet);
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
                                // Set the encoding type to indicate an unsigned integer
                                intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            }

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                intEncodingType.setEncoding("unsigned");
                                ((IntegerParameterType) baseType).setSigned(false);
                            }

                            ((IntegerParameterType) baseType).setIntegerDataEncoding(intEncodingType);
                            break;

                        case FLOAT:
                            // Create a float parameter and set its attributes
                            baseType = factory.createParameterTypeSetTypeFloatParameterType();
                            ((FloatParameterType) baseType).setUnitSet(unitSet);
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            floatEncodingType.setEncoding("IEEE754_1985");
                            ((FloatParameterType) baseType).setFloatDataEncoding(floatEncodingType);
                            break;

                        case STRING:
                            // Create a string parameter and set its attributes
                            baseType = factory.createParameterTypeSetTypeStringParameterType();
                            ((StringParameterType) baseType).setUnitSet(unitSet);
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                            // Set the string's size in bits based on the number of characters in
                            // the string with each character occupying a single byte
                            IntegerValueType intValType = new IntegerValueType();
                            intValType.setFixedValue(String.valueOf(stringSize * 8));
                            SizeInBits sizeInBits = new SizeInBits();
                            sizeInBits.setFixed(intValType);
                            stringEncodingType.setSizeInBits(sizeInBits);
                            // stringEncodingType.setEncoding("character array"); //TODO

                            ((StringParameterType) baseType).setStringDataEncoding(stringEncodingType);
                            ((StringParameterType) baseType).setCharacterWidth(BigInteger.valueOf(stringSize));
                            break;
                    }
                }

                // Set the parameter name attribute
                baseType.setName(parameterName + "Type");

                // Check is a description exists
                if (description != null && !description.isEmpty())
                {
                    // Set the description attribute
                    baseType.setShortDescription(description);
                }

                parameterDescription = baseType;
            }
        }
        // Structure data type
        else
        {
            // Create an aggregate type for the structure and set its attributes
            AggregateDataType aggregateType = new AggregateDataType();
            aggregateType.setName(parameterName + "Type");

            // Check is a description exists
            if (description != null && !description.isEmpty())
            {
                // Set the description attribute
                aggregateType.setShortDescription(description);
            }

            parameterDescription = aggregateType;
        }

        return parameterDescription;
    }

    /**********************************************************************************************
     * Set the command argument data type
     *
     * @param system
     *            space system
     *
     * @param commandName
     *            command name
     *
     * @param argumentName
     *            command argument name; null to not specify
     *
     * @param argumentType
     *            command data type; null to not specify
     *
     * @param enumeration
     *            command enumeration in the format <enum label>|<enum value>[|...][,...]; null to
     *            not specify
     *
     * @param units
     *            command argument units; null to not specify
     *
     * @param shortDescription
     *            short description of the parameter; null to not specify
     *
     * @param stringSize
     *            string size in bytes; ignored if the command argument does not have a string data
     *            type
     *
     * @return Command description of the type corresponding to the primitive data type with the
     *         specified attributes set
     *********************************************************************************************/
    private NameDescriptionType setArgumentType(SpaceSystemType system,
                                                String commandName,
                                                String argumentName,
                                                String argumentType,
                                                String enumeration,
                                                String units,
                                                String shortDescription,
                                                int stringSize)
    {
        BaseDataType commandDescription = null;

        // Get the XTCE data type corresponding to the primitive data type
        XTCEDataType xtceDataType = getXTCEDataType(argumentType);

        // Check if the a corresponding XTCE data type exists
        if (xtceDataType != null)
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
                commandDescription = factory.createEnumeratedDataType();
                EnumerationList enumList = createEnumerationList(system, enumeration);

                // Set the integer encoding (the only encoding available for an enumeration) and
                // the size in bits
                IntegerDataEncodingType intDataEnc = factory.createIntegerDataEncodingType();
                intDataEnc.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(argumentType)));
                ((EnumeratedDataType) commandDescription).setIntegerDataEncoding(intDataEnc);

                // Set the enumeration list and units attributes
                ((EnumeratedDataType) commandDescription).setEnumerationList(enumList);
                ((EnumeratedDataType) commandDescription).setUnitSet(unitSet);
                IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                // Check if the data type is an unsigned integer
                if (dataTypeHandler.isUnsignedInt(argumentType))
                {
                    // Set the encoding type to indicate an unsigned integer
                    intEncodingType.setEncoding("unsigned");
                }

                commandDescription.setIntegerDataEncoding(intEncodingType);
            }
            // This is not an enumerated command argument
            else
            {
                switch (xtceDataType)
                {
                    case INTEGER:
                        // Create an integer command argument and set its attributes
                        commandDescription = factory.createArgumentTypeSetTypeIntegerArgumentType();
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                        intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(argumentType)));

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(argumentType))
                        {
                            // Set the encoding type to indicate an unsigned integer
                            intEncodingType.setEncoding("unsigned");
                            ((IntegerParameterType) commandDescription).setSigned(false);
                        }

                        commandDescription.setIntegerDataEncoding(intEncodingType);
                        break;

                    case FLOAT:
                        // Create a float command argument and set its attributes
                        commandDescription = factory.createArgumentTypeSetTypeFloatArgumentType();
                        FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                        floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(argumentType)));
                        floatEncodingType.setEncoding("IEEE754_1985");
                        commandDescription.setFloatDataEncoding(floatEncodingType);
                        break;

                    case STRING:
                        // Create a string command argument and set its attributes
                        commandDescription = factory.createStringDataType();
                        ((StringDataType) commandDescription).setCharacterWidth(BigInteger.valueOf(stringSize));
                        StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                        // Set the string's size in bits based on the number of characters in the
                        // string with each character occupying a single byte
                        IntegerValueType intValType = new IntegerValueType();
                        intValType.setFixedValue(String.valueOf(stringSize * 8));
                        SizeInBits sizeInBits = new SizeInBits();
                        sizeInBits.setFixed(intValType);
                        stringEncodingType.setSizeInBits(sizeInBits);
                        // stringEncodingType.setEncoding("character array"); //TODO

                        commandDescription.setStringDataEncoding(stringEncodingType);
                        break;
                }
            }

            // Set the command name and argument name attributes
            commandDescription.setBaseType(commandName);
            commandDescription.setName(argumentName);

            // Check is a description exists
            if (shortDescription != null && !shortDescription.isEmpty())
            {
                // Set the command description attribute
                commandDescription.setShortDescription(shortDescription);
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
     * @param system
     *            space system
     *
     * @param enumeration
     *            enumeration in the format <enum value><enum value separator><enum label>[<enum
     *            value separator>...][<enum pair separator>...]
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    private EnumerationList createEnumerationList(SpaceSystemType system,
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
                                                              + system.getName()
                                                              + "'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }
}
