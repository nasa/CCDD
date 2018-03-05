/**
 * CFS Command & Data Dictionary XTCE handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

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
import org.omg.space.xtce.AggregateDataType.MemberList;
import org.omg.space.xtce.AggregateDataType.MemberList.Member;
import org.omg.space.xtce.AlarmRangesType;
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
import org.omg.space.xtce.CommandMetaDataType.MetaCommandSet;
import org.omg.space.xtce.ComparisonType;
import org.omg.space.xtce.ContainerRefEntryType;
import org.omg.space.xtce.ContainerSetType;
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
import org.omg.space.xtce.NumericAlarmType;
import org.omg.space.xtce.ObjectFactory;
import org.omg.space.xtce.ParameterRefEntryType;
import org.omg.space.xtce.ParameterSetType;
import org.omg.space.xtce.ParameterSetType.Parameter;
import org.omg.space.xtce.ParameterTypeSetType.EnumeratedParameterType;
import org.omg.space.xtce.ParameterTypeSetType.FloatParameterType;
import org.omg.space.xtce.ParameterTypeSetType.IntegerParameterType;
import org.omg.space.xtce.ParameterTypeSetType.StringParameterType;
import org.omg.space.xtce.SequenceContainerType;
import org.omg.space.xtce.SequenceContainerType.BaseContainer;
import org.omg.space.xtce.SequenceContainerType.BaseContainer.RestrictionCriteria;
import org.omg.space.xtce.SpaceSystemType;
import org.omg.space.xtce.StringDataEncodingType;
import org.omg.space.xtce.StringDataEncodingType.SizeInBits;
import org.omg.space.xtce.StringDataType;
import org.omg.space.xtce.UnitType;
import org.omg.space.xtce.ValueEnumerationType;

import CCDD.CcddClasses.ArrayVariable;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
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

    // Export endian type
    private EndianType endianess;

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

    // Names of the tables that represent the common header for all telemetry and command tables
    private String tlmHeaderTable;
    private String cmdHeaderTable;

    // Names of the system paths for the common header for all telemetry and command tables
    private String tlmHeaderPath;
    private String cmdHeaderPath;

    // XTCE data types
    private enum XTCEDataType
    {
        INTEGER,
        FLOAT,
        STRING
    }

    // Text appended to the parameter and command type and array references
    private static String TYPE = "_Type";
    private static String ARRAY = "_Array";

    /**********************************************************************************************
     * Structure member list
     *********************************************************************************************/
    class StructureMemberList
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
     *            [0] endianess (EndianType.BIG_ENDIAN or EndianType.LITTLE_ENDIAN) <br>
     *            [1] version attribute <br>
     *            [2] validation status attribute <br>
     *            [3] first level classification attribute <br>
     *            [4] second level classification attribute <br>
     *            [5] third level classification attribute
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
                                (String) extraInfo[1],
                                (String) extraInfo[2],
                                (String) extraInfo[3],
                                (String) extraInfo[4],
                                (String) extraInfo[5]);

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
                                     String version,
                                     String validationStatus,
                                     String classification1,
                                     String classification2,
                                     String classification3)
    {
        this.endianess = endianess;

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

                // Check if the space system for the prototype of the table has already been
                // created
                if (getSpaceSystemByName(tableName, project.getValue()) != null)
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
                // TODO GIVEN THAT INSTANCE INFORMATION DOESN'T APPEAR TO BE USED, IF A CHILD
                // TABLE IS REFERNCED THEN AUTOMATICALLY LOAD ITS PROTOTYPE AND DON'T CREATE A
                // SpaceSystem FOR THE INSTANCE. TREAT IT AS IF ONLY PROTOTYPES WERE SELECTED.

                // TODO IF RATE INFORMATION GETS USED THEN THE PROTOTYPE DATA IS REQUIRED. FOR THAT
                // MATTER, SEPARATE SPACE SYSTEMS FOR EACH INSTANCE MAY THEN BE NEEDED.

                // Get the table type and from the type get the type definition. The type
                // definition can be a global parameter since if the table represents a structure,
                // then all of its children are also structures, and if the table represents
                // commands or other table type then it is processed within this nest level
                typeDefn = tableTypeHandler.getTypeDefinition(tableInfo.getType());

                // Check if the table type is valid
                if (typeDefn != null)
                {
                    // Get the table's basic type - structure, command, or the original table type
                    // if not structure or command table
                    String tableType = typeDefn.isStructure()
                                                              ? TYPE_STRUCTURE
                                                              : typeDefn.isCommand()
                                                                                     ? TYPE_COMMAND
                                                                                     : tableInfo.getType();

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

                    // Check if this is a node for a structure table
                    if (tableType.equals(TYPE_STRUCTURE))
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

                        // Set the flag to indicate if this is the telemetry header table
                        boolean isTlmHeaderTable = tableName.equals(tlmHeaderTable);

                        // Check if this is the telemetry header table
                        if (isTlmHeaderTable)
                        {
                            // Store the telemetry header's path
                            tlmHeaderPath = systemPath;
                        }

                        // Export the parameter container for this structure
                        addParameterContainer(parentSystem,
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
                                // Add the variable to the space system
                                addParameter(parentSystem,
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

                                // Use the variable name to create an aggregate list member and add
                                // it to the member list. The list is used if this structure is
                                // referenced as a data type in another structure
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
                                                                                                      : rowData[typeColumn])
                                                  + TYPE);
                                memberList.getMember().add(member);
                            }
                        }

                        // Check if any variables exists in the structure table
                        if (!memberList.getMember().isEmpty())
                        {
                            // Create a structure member list using the table's aggregate member
                            // list
                            memberLists.add(new StructureMemberList(tableName, memberList));
                        }
                    }
                    // Check if this is a command table
                    else if (tableType.equals(TYPE_COMMAND))
                    {
                        // Check if this is the command header table
                        if (tableName.equals(cmdHeaderTable))
                        {
                            // Store the command header's path
                            cmdHeaderPath = systemPath;
                        }

                        // Add the command(s) from this table to the parent system
                        addSpaceSystemCommands(parentSystem,
                                               tableInfo,
                                               tableName.equals(cmdHeaderTable),
                                               applicationID,
                                               ccsdsAppID,
                                               ccsdsFuncCode);
                    }
                    // Not a structure or command table
                    else
                    {
                        // TODO WHAT SHOULD BE DONE WITH NON-STRUCTURE & NON-COMMAND TABLES -
                        // IGNORE, CREATE THE SPACE SYSTEM ONLY, OR CREATE A SYSTEM AND POPULATE
                        // WITH ANCILLARY DATA?

                        // IF STRUCTURE & COMMAND DATA THAT DOESN'T FIT XTCE IS STORED (ANCILLARY
                        // OR CUSTOM TAGS) THEN THIS TYPE OF TABLE WOULD BE STORED IN THE SAME
                        // FASHION. A CHECK BOX IN THE EXPORT DIALOG COULD BE USED TO TRIGGER
                        // STORING THIS 'EXTRA' INFORMATION ('INCLUDE EXTRA DATA'). IMPORT OF THIS
                        // TYPE OF EXPORT WOULD BE VIABLE.
                    }
                }
            }
        }

        // Step through each table name
        for (String tableName : tableNames)
        {
            // Get the prototype for the child
            tableName = TableInformation.getPrototypeName(tableName);

            // Get the space system for this table
            SpaceSystemType system = getSpaceSystemByName(tableName, project.getValue());

            // Check if the system was found and it has telemetry data
            if (system != null && system.getTelemetryMetaData() != null)
            {
                // Step through each parameter type
                for (NameDescriptionType type : system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType())
                {
                    // Check if the type is an aggregate (i.e., a structure reference)
                    if (type instanceof AggregateDataType)
                    {
                        // Step through each structure member list
                        for (StructureMemberList structMemList : memberLists)
                        {
                            // Check if the aggregate refers to this structure
                            if (type.getName().equals(structMemList.structureName + TYPE))
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
     * Add the parameter container
     *
     * @param system
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
     *            true if this table represents the CCSDS telemetry header
     *
     * @param applicationID
     *            application ID
     *
     * @param ccsdsAppID
     *            name of the command header argument containing the application ID
     *********************************************************************************************/
    private void addParameterContainer(SpaceSystemType system,
                                       TableInformation tableInfo,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       boolean isTlmHeader,
                                       String applicationID,
                                       String ccsdsAppID)
    {
        ContainerSetType seqContainerSet = null;
        SequenceContainerType seqContainer = null;
        EntryListType entryList = factory.createEntryListType();

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
                    arrayRef.setParameterRef(rowData[varColumn]);
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
                    // Get the index of the variable's bit length, if present
                    int bitIndex = rowData[varColumn].indexOf(":");

                    // Check if this variable has a bit length
                    if (bitIndex != -1)
                    {
                        // Remove the bit length from the variable name
                        rowData[varColumn] = rowData[varColumn].substring(0, bitIndex);
                    }

                    // Store the parameter reference in the list
                    ParameterRefEntryType parameterRef = factory.createParameterRefEntryType();
                    parameterRef.setParameterRef(rowData[varColumn]);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(parameterRef);
                }
            }
            // This is a structure data type. Check if the reference isn't to the telemetry header
            // table
            // TODO CAN tlmHeaderTable HAVE A PATH ATTACHED? THIS MAY BE ADDED ELSEWHERE...
            else if (!rowData[typeColumn].equals(tlmHeaderTable))
            {
                // Get the name of the system to which this referenced structure belongs
                String refSystemName = fieldHandler.getFieldValue(rowData[typeColumn],
                                                                  InputDataType.SYSTEM_PATH);

                // Store the structure reference in the list
                ContainerRefEntryType containerType = factory.createContainerRefEntryType();
                containerType.setContainerRef("/" + project.getValue().getName()
                                              + (refSystemName == null
                                                 || refSystemName.isEmpty()
                                                                            ? ""
                                                                            : "/" + refSystemName)
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
                else if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
                {
                    seqContainer.setName(system.getName());

                    // Check if this is a root structure (instance structures don't require a
                    // reference to the telemetry header)
                    if (tableInfo.isRootStructure())
                    {
                        // Create a reference to the telemetry header
                        BaseContainer baseContainer = factory.createSequenceContainerTypeBaseContainer();
                        baseContainer.setContainerRef("/" + project.getValue().getName()
                                                      + (tlmHeaderPath == null
                                                         || tlmHeaderPath.isEmpty()
                                                                                    ? ""
                                                                                    : "/" + tlmHeaderPath)
                                                      + "/" + tlmHeaderTable
                                                      + "/" + tlmHeaderTable);
                        RestrictionCriteria restrictCriteria = factory.createSequenceContainerTypeBaseContainerRestrictionCriteria();
                        ComparisonList compList = factory.createMatchCriteriaTypeComparisonList();
                        ComparisonType compType = factory.createComparisonType();
                        compType.setParameterRef(ccsdsAppID);
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
    private void addParameter(SpaceSystemType system,
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
        if (system.getTelemetryMetaData() == null)
        {
            // Create the telemetry metadata
            createTelemetryMetadata(system);
        }

        // Check if a data type is provided
        if (dataType != null)
        {
            // Get the parameter's data type information
            setDataType(system,
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
        ParameterSetType parameterSet = system.getTelemetryMetaData().getParameterSet();
        Parameter parameter = factory.createParameterSetTypeParameter();
        parameter.setName((dataTypeHandler.isPrimitive(dataType)
                                                                 ? parameterName
                                                                 : dataType)); // TODO WAS
                                                                               // parameterName);
        parameter.setParameterTypeRef((dataTypeHandler.isPrimitive(dataType)
                                                                             ? parameterName
                                                                             : dataType)
                                      + (arraySize.isEmpty()
                                                             ? TYPE
                                                             : ARRAY));
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
     * @param system
     *            parent space system for this node
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
    private void addSpaceSystemCommands(SpaceSystemType system,
                                        TableInformation tableInfo,
                                        boolean isCmdHeader,
                                        String applicationID,
                                        String ccsdsAppID,
                                        String ccsdsFuncCode)
    {
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
            // Initialize the command attributes and argument names list
            String commandName = null;
            String commandCode = null;
            String commandDescription = null;
            List<String> argumentNames = new ArrayList<String>();

            // Check if the command name exists
            if (cmdNameCol != -1 && !rowData[cmdNameCol].isEmpty())
            {
                // Store the command name
                commandName = rowData[cmdNameCol];

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
                    String argName = null;
                    String dataType = null;
                    String arraySize = null;
                    String bitLength = null;
                    String enumeration = null;
                    String minimum = null;
                    String maximum = null;
                    String units = null;
                    String description = null;
                    int stringSize = 1;

                    // Check if this is the name column exists
                    if (cmdArg.getName() != -1 && !rowData[cmdArg.getName()].isEmpty())
                    {
                        // Store the command argument name
                        argName = rowData[cmdArg.getName()];
                    }

                    // Check if the data type column exists
                    if (cmdArg.getDataType() != -1 && !rowData[cmdArg.getDataType()].isEmpty())
                    {
                        // Store the command argument data type
                        dataType = rowData[cmdArg.getDataType()];
                    }

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

                                // Separate the array dimension values and get the
                                // string size
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

                    // Check if the command argument has the minimum parameters
                    // required: a name and data type
                    if (argName != null && !argName.isEmpty() && dataType != null)
                    {
                        // Add the name to the argument name list
                        argumentNames.add(argName);

                        // Add the command to the command space system
                        addCommandArgument(system,
                                           commandName,
                                           argName,
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
                }

                // Add the command metadata set information
                addCommand(system,
                           commandName,
                           commandCode,
                           isCmdHeader,
                           applicationID,
                           ccsdsAppID,
                           ccsdsFuncCode,
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
     *            short description of the command
     *********************************************************************************************/
    private void addCommand(SpaceSystemType system,
                            String commandName,
                            String commandCode,
                            boolean isCmdHeader,
                            String applicationID,
                            String ccsdsAppID,
                            String ccsdsFuncCode,
                            List<String> argumentNames,
                            String description)
    {
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
        if (description != null && !description.isEmpty())
        {
            // Set the command description attribute
            command.setShortDescription(description);
        }

        // Check if the command has any arguments
        if (!argumentNames.isEmpty())
        {
            ArgumentList argList = factory.createMetaCommandTypeArgumentList();
            CommandContainerType cmdContainer = factory.createCommandContainerType();
            CommandContainerEntryListType entryList = factory.createCommandContainerEntryListType();

            // Step through each argument
            for (String argumentName : argumentNames)
            {
                // Add the argument to the the command's argument list
                Argument arg = new Argument();
                arg.setName(argumentName);
                arg.setArgumentTypeRef(argumentName + TYPE);
                argList.getArgument().add(arg);

                // Store the argument reference in the list
                ArgumentRefEntry argumentRef = factory.createCommandContainerEntryListTypeArgumentRefEntry();
                argumentRef.setArgumentRef(argumentName);
                JAXBElement<ArgumentRefEntry> argumentRefElement = factory.createCommandContainerEntryListTypeArgumentRefEntry(argumentRef);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(argumentRefElement);
            }

            // Check if this table represents the CCSDS command header
            if (isCmdHeader)
            {
                command.setAbstract(true);
            }
            // Not he command header. Check if the command ID is provided
            else if (!isCmdHeader
                     && applicationID != null
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
                argAssn.setArgumentName(ccsdsAppID);
                argAssn.setArgumentValue(applicationID);
                argAssnList.getArgumentAssignment().add(argAssn);

                // Check if a command code is provided
                if (commandCode != null && !commandCode.isEmpty())
                {
                    // Store the command code
                    argAssn = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentListArgumentAssignment();
                    argAssn.setArgumentName(ccsdsFuncCode);
                    argAssn.setArgumentValue(commandCode);
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
     * @param arraySize
     *            command argument array size
     *
     * @param bitLength
     *            command argument bit length
     *
     * @param enumeration
     *            command enumeration in the format <enum label>=<enum value>
     *
     * @param units
     *            command argument units
     *
     * @param description
     *            command description
     *
     * @param stringSize
     *            string size in bytes; ignored if the command argument does not have a string data
     *            type
     *********************************************************************************************/
    private void addCommandArgument(SpaceSystemType system,
                                    String commandName,
                                    String argumentName,
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
        // Check if this system doesn't yet have its command metadata created
        if (system.getCommandMetaData() == null)
        {
            // Create the command metadata
            createCommandMetadata(system);
        }

        // Set the command argument data type information
        NameDescriptionType type = setArgumentType(system,
                                                   commandName,
                                                   argumentName,
                                                   dataType,
                                                   arraySize,
                                                   bitLength,
                                                   enumeration,
                                                   units,
                                                   minimum,
                                                   maximum,
                                                   description,
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
    private void setDataType(SpaceSystemType system,
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
        NameDescriptionType parameterDescription = null;

        // TODO SET BITS IN BOTH PLACES (IS ONE THE BIT LENGTH AND THE OTHER THE OTHER THE DATA
        // TYPE SIZE?)

        // Check if the parameter is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes
            ArrayDataTypeType arrayType = factory.createArrayDataTypeType();
            arrayType.setName(parameterName + ARRAY);
            arrayType.setNumberOfDimensions(BigInteger.valueOf(ArrayVariable.getArrayIndexFromSize(arraySize).length));
            arrayType.setArrayTypeRef(parameterName + TYPE);

            // Set the parameter's array information
            system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(arrayType);
        }

        // Check if the parameter has a primitive data type
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Get the XTCE data type corresponding to the primitive data type
            XTCEDataType xtceDataType = getXTCEDataType(dataType);

            // Check if the a corresponding XTCE data type exists
            if (xtceDataType != null)
            {
                // Set the parameter units
                UnitSet unitSet = createUnitSet(units);

                // Check if enumeration parameters are provided
                if (enumeration != null && !enumeration.isEmpty())
                {
                    // Create an enumeration type and enumeration list, and add any extra
                    // enumeration parameters as column data
                    EnumeratedParameterType enumType = factory.createParameterTypeSetTypeEnumeratedParameterType();
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

                    // TODO ISSUE: THE CCSDS HEADER IS ALWAYS BIG ENDIAN - HOW AN THIS BE
                    // DETECTED? OR JUST ASSUME IT'S IGNORED BY THE USER FOR THAT CASE?
                    // Set the bit order
                    intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                                                   ? "mostSignificantBitFirst"
                                                                                   : "leastSignificantBitFirst");

                    enumType.setIntegerDataEncoding(intEncodingType);

                    // Set the enumeration list and units attributes
                    enumType.setEnumerationList(enumList);
                    enumType.setUnitSet(unitSet);

                    parameterDescription = enumType;
                }
                // Not an enumeration
                else
                {
                    switch (xtceDataType)
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
                                                                                           ? "mostSignificantBitFirst"
                                                                                           : "leastSignificantBitFirst");

                            integerType.setIntegerDataEncoding(intEncodingType);

                            // TODO NOTE THAT ONLY ONE ALARM TYPE IS COVERED.
                            // TODO HAVE TO DO THIS AS A FLOAT; INTEGER DOESN'T APPEAR TO BE
                            // DOABLE.
                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                NumericAlarmType alarm = factory.createNumericAlarmType();
                                AlarmRangesType alarmType = factory.createAlarmRangesType();
                                FloatRangeType floatRange = new FloatRangeType();

                                // Check if a minimum value is specified
                                if (minimum != null && !minimum.isEmpty())
                                {
                                    // Set the minimum value
                                    floatRange.setMinExclusive(Double.valueOf(minimum));
                                }

                                // Check if a maximum value is specified
                                if (maximum != null && !maximum.isEmpty())
                                {
                                    // Set the maximum value
                                    floatRange.setMaxExclusive(Double.valueOf(maximum));
                                }

                                alarmType.setWarningRange(floatRange);
                                alarm.setStaticAlarmRanges(alarmType);
                                integerType.setDefaultAlarm(alarm);
                            }

                            parameterDescription = integerType;
                            break;

                        case FLOAT:
                            // Create a float parameter and set its attributes
                            FloatParameterType floatType = factory.createParameterTypeSetTypeFloatParameterType();
                            floatType.setUnitSet(unitSet);
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                            floatEncodingType.setEncoding("IEEE754_1985");
                            floatType.setFloatDataEncoding(floatEncodingType);

                            // TODO NOTE THAT ONLY ONE ALARM TYPE IS COVERED
                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                NumericAlarmType alarm = factory.createNumericAlarmType();
                                AlarmRangesType alarmType = factory.createAlarmRangesType();
                                FloatRangeType floatRange = new FloatRangeType();

                                // Check if a minimum value is specified
                                if (minimum != null && !minimum.isEmpty())
                                {
                                    // Set the minimum value
                                    floatRange.setMinExclusive(Double.valueOf(minimum));
                                }

                                // Check if a maximum value is specified
                                if (maximum != null && !maximum.isEmpty())
                                {
                                    // Set the maximum value
                                    floatRange.setMaxExclusive(Double.valueOf(maximum));
                                }

                                alarmType.setWarningRange(floatRange);
                                alarm.setStaticAlarmRanges(alarmType);
                                floatType.setDefaultAlarm(alarm);
                            }

                            parameterDescription = floatType;
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
                            parameterDescription = stringType;
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
            parameterName = dataType;
            parameterDescription = aggregateType;
        }

        // Set the parameter type name
        parameterDescription.setName(parameterName + TYPE);

        // Check is a description exists
        if (description != null && !description.isEmpty())
        {
            // Set the description attribute
            parameterDescription.setShortDescription(description);
        }

        // Set the parameter's data type information
        system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(parameterDescription);
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
     * @param dataType
     *            command argument data type; null to not specify
     *
     * @param arraySize
     *            command argument array size; null or blank if the parameter isn't an array
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
     *********************************************************************************************/
    private NameDescriptionType setArgumentType(SpaceSystemType system,
                                                String commandName,
                                                String argumentName,
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
        // TODO SIMILAR TO setDataType() - CAN THESE BE COMBINED? THE MIN/MAX PART DIFFERS

        BaseDataType commandDescription = null;

        // Check if the parameter is an array
        if (arraySize != null && !arraySize.isEmpty())
        {
            // Create an array type and set its attributes
            ArrayDataTypeType arrayType = factory.createArrayDataTypeType();
            arrayType.setName(argumentName + ARRAY);
            arrayType.setNumberOfDimensions(BigInteger.valueOf(ArrayVariable.getArrayIndexFromSize(arraySize).length));
            arrayType.setArrayTypeRef(argumentName + TYPE);

            // Set the parameter's array information
            system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(arrayType);
        }

        // Get the XTCE data type corresponding to the primitive data type
        XTCEDataType xtceDataType = getXTCEDataType(dataType);

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
                EnumeratedDataType enumType = factory.createEnumeratedDataType();
                EnumerationList enumList = createEnumerationList(system, enumeration);

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
                                                                               ? "mostSignificantBitFirst"
                                                                               : "leastSignificantBitFirst");

                enumType.setIntegerDataEncoding(intEncodingType);
                commandDescription = enumType;
            }
            // This is not an enumerated command argument
            else
            {
                switch (xtceDataType)
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
                                                                                       ? "mostSignificantBitFirst"
                                                                                       : "leastSignificantBitFirst");

                        integerType.setIntegerDataEncoding(intEncodingType);

                        // Check if a minimum or maximum value is specified
                        if ((minimum != null && !minimum.isEmpty())
                            || (maximum != null && !maximum.isEmpty()))
                        {
                            IntegerArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeIntegerArgumentTypeValidRangeSet();
                            IntegerRangeType integerRange = new IntegerRangeType();

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                integerRange.setMinInclusive(minimum);
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                integerRange.setMaxInclusive(maximum);
                            }

                            validRange.getValidRange().add(integerRange);
                            integerType.setValidRangeSet(validRange);
                        }

                        commandDescription = integerType;
                        break;

                    case FLOAT:
                        // Create a float command argument and set its attributes
                        FloatArgumentType floatType = factory.createArgumentTypeSetTypeFloatArgumentType();
                        FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                        floatEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        floatEncodingType.setEncoding("IEEE754_1985");
                        floatType.setFloatDataEncoding(floatEncodingType);

                        // Check if a minimum or maximum value is specified
                        if ((minimum != null && !minimum.isEmpty())
                            || (maximum != null && !maximum.isEmpty()))
                        {
                            FloatArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeFloatArgumentTypeValidRangeSet();
                            FloatRangeType floatRange = new FloatRangeType();

                            // Check if a minimum value is specified
                            if (minimum != null && !minimum.isEmpty())
                            {
                                // Set the minimum value
                                floatRange.setMinExclusive(Double.valueOf(minimum));
                            }

                            // Check if a maximum value is specified
                            if (maximum != null && !maximum.isEmpty())
                            {
                                // Set the maximum value
                                floatRange.setMaxExclusive(Double.valueOf(maximum));
                            }

                            validRange.getValidRange().add(floatRange);
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
            commandDescription.setName(argumentName + TYPE);

            // Check is a description exists
            if (description != null && !description.isEmpty())
            {
                // Set the command description attribute
                commandDescription.setShortDescription(description);
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
