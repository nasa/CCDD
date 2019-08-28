/**
 * CFS Command and Data Dictionary XTCE handler.
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.script.Invocable;
import javax.script.ScriptEngine;
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
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.ModifiableOtherSettingInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary XTCE handler class
 *************************************************************************************************/
public class CcddXTCEHandler extends CcddImportSupportHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddInputTypeHandler inputTypeHandler;

    // GUI component over which to center any error dialog
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
    private SpaceSystemType rootSystem;

    // Reference to the script engine as an Invocable interface; used if external (script) methods
    // are used for the export operation
    private Invocable invocable;

    // Attribute strings
    private String versionAttr;
    private String validationStatusAttr;
    private String classification1Attr;
    private String classification2Attr;
    private String classification3Attr;

    // Flag to indicate if the telemetry and command headers are big endian (as with CCSDS)
    private boolean isHeaderBigEndian;

    // Table type definitions
    private TypeDefinition structureTypeDefn;
    private TypeDefinition commandTypeDefn;
    private TypeDefinition commandAllTypeDefn;

    // Flags to indicate if a telemetry and command table is defined in the import file, and if a
    // command header is defined, which entails converting command information into a structure
    private boolean isTelemetry;
    private boolean isCommand;
    private boolean isCmdToTlm;

    // List of the associated command arguments
    private List<AssociatedColumns> commandArguments;

    // Maximum number of command arguments for all command tables defined in the import file (for
    // normal command tables and for the command header tables converted into structures)
    private int maxNumArguments;
    private int maxNumArgumentsAll;

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
    private int numCommandColumnsAll;

    // Text appended to the parameter and command type and array references
    private static String TYPE = "_Type";
    private static String ARRAY = "_Array";

    // Array member container reference parts
    private static enum ArrayContainerReference
    {
        CHILD_SPC_SYS,
        CHILD_SEQ_CONT,
        ARRAY_SIZE
    }

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
        int numArrayMembers;
        int seqIndex;

        /**********************************************************************************************
         * Parameter information class constructor for telemetry and argument references
         *
         * @param parameterName
         *            parameter name
         *
         * @param dataType
         *            data type
         *
         * @param arraySize
         *            parameter array size
         *
         * @param bitLength
         *            parameter bit length
         *
         * @param enumeration
         *            enumeration in the format {@literal <enum label>|<enum value>[|...][,...]}
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
         * @param numArrayMembers
         *            number of array members; 0 if not an array parameter
         *
         * @param seqIndex
         *            index of the next sequence entry list item to process
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
                             int numArrayMembers,
                             int seqIndex)
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
            this.numArrayMembers = numArrayMembers;
            this.seqIndex = seqIndex;
        }

        /******************************************************************************************
         * Parameter information class constructor for container references
         *
         * @param parameterName
         *            variable or argument name
         *
         * @param dataType
         *            parameter data type
         *
         * @param arraySize
         *            parameter array size
         *
         * @param description
         *            parameter description
         *
         * @param numArrayMembers
         *            number of array members; 0 if not an array parameter
         *
         * @param seqIndex
         *            index of the next sequence entry list item to process
         *****************************************************************************************/
        ParameterInformation(String parameterName,
                             String dataType,
                             String arraySize,
                             String description,
                             int numArrayMembers,
                             int seqIndex)
        {
            this(parameterName,
                 dataType,
                 arraySize,
                 null,
                 null,
                 null,
                 null,
                 null,
                 description,
                 numArrayMembers,
                 seqIndex);
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
         * Get the number of array members; 0 if not an array parameter
         *
         * @return Number of array members; 0 if not an array parameter
         *****************************************************************************************/
        protected int getNumArrayMembers()
        {
            return numArrayMembers;
        }

        /******************************************************************************************
         * Get the index of the next sequence entry list item to process
         *
         * @return Index of the next sequence entry list item to process
         *****************************************************************************************/
        protected int getSeqIndex()
        {
            return seqIndex;
        }
    }

    /**********************************************************************************************
     * XTCE handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param scriptEngine
     *            reference to the script engine so that the export methods can be overridden by
     *            the script methods; null if the internal methods are to be used
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @throws CCDDException
     *             If an error occurs creating the handler
     *********************************************************************************************/
    CcddXTCEHandler(CcddMain ccddMain,
                    ScriptEngine scriptEngine,
                    Component parent) throws CCDDException
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

        // Check if a reference to a script engine is provided
        if (scriptEngine != null)
        {
            // Check if the scripting language supports the Invocable interface
            if (!(scriptEngine instanceof Invocable))
            {
                // Inform the user that the scripting language doesn't support the Invocable
                // interface
                throw new CCDDException("XTCE conversion failed; cause '</b>"
                                        + "The scripting language '"
                                        + scriptEngine.getFactory().getLanguageName()
                                        + "' does not implement the Invocable interface"
                                        + "<b>'");
            }

            // Store the reference to the script engine as an Invocable interface
            invocable = (Invocable) scriptEngine;
        }
        else
        {
            invocable = null;
        }

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into XTCE XML format
            JAXBContext context = JAXBContext.newInstance("org.omg.space.xtce", org.omg.space.xtce.ObjectFactory.class.getClassLoader());
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
            throw new CCDDException("XTCE conversion setup failed; cause '</b>"
                                    + je.getMessage()
                                    + "<b>'");
        }
    }

    /**********************************************************************************************
     * XTCE handler class constructor when importing
     *
     * @param ccddMain
     *            main class
     *
     * @param parent
     *            GUI component instantiating this class
     *
     * @throws CCDDException
     *             If an error occurs creating the handler
     *********************************************************************************************/
    CcddXTCEHandler(CcddMain ccddMain, Component parent) throws CCDDException
    {
        this(ccddMain, null, parent);
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
     * @param replaceExistingMacros
     *            not used for XTCE import
     *
     * @param replaceExistingGroups
     *            not used for XTCE import
     *
     * @param ignoreErrors
     *            true to ignore all errors in the import file
     *
     * @throws CCDDException
     *             If data is missing, extraneous, or an error in the import file
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

            // Get the top-level space system
            rootSystem = (SpaceSystemType) jaxbElement.getValue();

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
                    if (data.getName().equals(DefaultInputType.XML_TLM_HDR.getInputName()))
                    {
                        // Store the item value as the telemetry header table name
                        tlmHeaderTable = data.getValue();
                    }
                    // Check if the item name matches that for the command header table name
                    // indicator
                    else if (data.getName().equals(DefaultInputType.XML_CMD_HDR.getInputName()))
                    {
                        // Store the item value as the command header table name
                        cmdHeaderTable = data.getValue();
                    }
                    // Check if the item name matches that for the application ID variable name
                    // indicator
                    else if (data.getName().equals(DefaultInputType.XML_APP_ID.getInputName()))
                    {
                        // Store the item value as the application ID variable name
                        applicationIDName = data.getValue();
                    }
                    // Check if the item name matches that for the command function code variable
                    // name indicator
                    else if (data.getName().equals(DefaultInputType.XML_FUNC_CODE.getInputName()))
                    {
                        // Store the item value as the command function code variable name
                        cmdFuncCodeName = data.getValue();
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
            createTableTypeDefinitions(rootSystem, importFile, importType, targetTypeDefn);

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

                // Check if the special command table type used for command-to-structure
                // conversions exists
                if (commandAllTypeDefn != null)
                {
                    // Remove the special command table type definition
                    tableTypeHandler.getTypeDefinitions().remove(commandAllTypeDefn);
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
     * Scan the import file in order to determine if any structure or command tables exist. If so,
     * create the structure and/or command table type definition that's used to build the new
     * tables
     *
     * @param rootSystem
     *            root space system
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
    private void createTableTypeDefinitions(SpaceSystemType rootSystem,
                                            FileEnvVar importFile,
                                            ImportType importType,
                                            TypeDefinition targetTypeDefn) throws CCDDException
    {
        isTelemetry = false;
        isCommand = false;
        maxNumArguments = 1;
        maxNumArgumentsAll = 1;

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
                List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>(1);
                String typeName = "XTCE Structure";
                int sequence = 2;

                // Create a table type definition for structure tables
                TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                            "XTCE import structure table type");

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
                    typeName = "XTCE Structure " + sequence;
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
                String typeName = "XTCE Command";
                int sequence = 2;

                // Create a table type definition for command tables
                TableTypeDefinition tableTypeDefn = new TableTypeDefinition(typeName,
                                                                            "XTCE import command table type");

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

                // Two XTCE command table types are created using the default command columns. The
                // first is for the 'normal' command table; i.e., those that don't represent the
                // command header. This table type is stored in the project database following the
                // import operation. The second command table type accounts for those commands that
                // represent the command header, which are translated into structures. These
                // potentially have more argument columns than the normal commands. During import
                // this larger table type is used to temporarily store the command information. The
                // table type is then deleted upon completion of the import operation, which
                // prevents storing an unused table type in the project
                commandAllTypeDefn = tableTypeHandler.createReplaceTypeDefinition(typeName + "_special",
                                                                                  "XTCE import special command table type",
                                                                                  DefaultColumn.getDefaultColumnDefinitions(TYPE_COMMAND));

                // Store the maximum number of command arguments (comparing the normal command
                // tables and command header tables)
                maxNumArgumentsAll = Math.max(maxNumArgumentsAll, maxNumArguments);

                // Step through each additional command argument column set
                for (int argIndex = 2; argIndex <= maxNumArgumentsAll; argIndex++)
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

                    // Add the default columns for this command argument to the overall command
                    // table type
                    commandAllTypeDefn.addCommandArgumentColumns(argIndex);
                }

                // Add the command table type definition
                tableTypeDefns.add(tableTypeDefn);

                // Continue to check while a table type with this name exists. This also adds the
                // tab for the new definition to the table type manager, if open
                while (tableTypeHandler.updateTableTypes(tableTypeDefns) != null)
                {
                    // Alter the name so that there isn't a duplicate
                    typeName = "XTCE Command " + sequence;
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
            commandArguments = commandAllTypeDefn == null
                                                          ? commandTypeDefn.getAssociatedCommandArgumentColumns(true)
                                                          : commandAllTypeDefn.getAssociatedCommandArgumentColumns(true);

            // Get the command table column indices
            commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME));
            cmdFuncCodeIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE));
            cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(commandTypeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION));

            // Store the number of columns defined in the command table types
            numCommandColumns = commandTypeDefn.getColumnCountVisible();
            numCommandColumnsAll = commandAllTypeDefn == null
                                                              ? numCommandColumns
                                                              : commandAllTypeDefn.getColumnCountVisible();
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
                        // Get the command container reference to shorten subsequent calls
                        CommandContainerType cmdContainer = ((MetaCommandType) metaCommand).getCommandContainer();

                        // Check if the command is flagged as abstract. This denotes a command
                        // header which is converted to a structure, and therefore necessitates
                        // creating a structure table type to contain it as well as a special
                        // command table type to handle the (possibly) extra command arguments
                        if (((MetaCommandType) metaCommand).isAbstract())
                        {
                            isCmdToTlm = true;

                            // Check if the command has any arguments
                            if (cmdContainer != null && cmdContainer.getEntryList() != null)
                            {
                                // Get the maximum number of arguments needed for the commands that
                                // are converted to structures (i.e., command header tables)
                                maxNumArgumentsAll = Math.max(maxNumArgumentsAll,
                                                              cmdContainer.getEntryList().getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().size());

                            }
                        }
                        // The command isn't flagged as abstract. This denotes a command table
                        else
                        {
                            isCommand = true;

                            // Check if the command has any arguments
                            if (cmdContainer != null && cmdContainer.getEntryList() != null)
                            {
                                // Get the maximum number of arguments needed for the commands that
                                // aren't converted to structures
                                maxNumArguments = Math.max(maxNumArguments,
                                                           cmdContainer.getEntryList().getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().size());
                            }
                        }
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
     *            space system to which the new system belongs
     *
     * @param systemPath
     *            full path name for this space system (based on its nesting within other space
     *            systems)
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
    private void unbuildSpaceSystems(SpaceSystemType system,
                                     String systemPath,
                                     ImportType importType,
                                     boolean onlyCmdToStruct) throws CCDDException
    {
        // The full table name, with path, should be stored in the space system's short description
        // (the space system name doesn't allow the commas and periods used by the table path so it
        // has to go elsewhere; the export operation does this). If the short description doesn't
        // exist, or isn't in the correct format, then the table name is extracted from the space
        // system name; however, this creates a 'flat' table reference, making it a prototype
        String tableName = system.getShortDescription() != null
                           && TableDefinition.isPathFormatValid(system.getShortDescription())
                                                                                              ? system.getShortDescription()
                                                                                              : system.getName();

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
                                                 + system.getName(),
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
     * @param tableName
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
        }

        // Check if the telemetry metadata container set exists
        if (tlmMetaData.getContainerSet() != null)
        {
            int rowIndex = 0;

            // Get the system under which the space systems in the container references are to be
            // found. Specific instance tables are a sub-space system of the parent tables' space
            // system, but If the table is a child of a non-root structure then the space system
            // for the child's prototype is used, which is located in the root space system
            SpaceSystemType ownerSystem = dbTable.isRootStructure(tableName)
                                                                             ? system
                                                                             : rootSystem;

            // Create a table definition for this structure table. If the name space also includes
            // a command metadata (which creates a command table) then ensure the two tables have
            // different names
            TableDefinition tableDefn = new TableDefinition(tableName
                                                            + (system.getCommandMetaData() == null
                                                                                                   ? ""
                                                                                                   : "_tlm"),
                                                            system.getLongDescription());

            // Set the new structure table's table type name
            tableDefn.setTypeName(structureTypeDefn.getName());

            // Step through each sequence container in the container set
            for (SequenceContainerType seqContainer : tlmMetaData.getContainerSet().getSequenceContainer())
            {
                // Get the reference to the sequence container's base container (if any). The base
                // container is assumed to reference the telemetry header
                BaseContainer baseContainer = seqContainer.getBaseContainer();

                // Check if the reference to the telemetry header table exists
                if (baseContainer != null
                    && baseContainer.getContainerRef() != null
                    && TableInformation.getPrototypeName(baseContainer.getContainerRef()).endsWith("/"
                                                                                                   + tlmHeaderTable
                                                                                                   + "/"
                                                                                                   + tlmHeaderTable))
                {
                    // Add a variable to the structure for the telemetry header table. Note that
                    // the telemetry header table name is used as the variable name since there's
                    // nowhere to store the variable name in the file
                    rowIndex = addVariableDefinitionToStructure(tableDefn,
                                                                rowIndex,
                                                                0,
                                                                tlmHeaderTable,
                                                                tlmHeaderTable,
                                                                null,
                                                                null,
                                                                "Telemetry header",
                                                                null,
                                                                null,
                                                                null,
                                                                null);

                    // Check if this is the comparison list for the telemetry header table
                    if (baseContainer.getRestrictionCriteria() != null
                        && baseContainer.getRestrictionCriteria().getComparisonList() != null)
                    {
                        // Step through each item in the comparison list
                        for (ComparisonType comparison : baseContainer.getRestrictionCriteria().getComparisonList().getComparison())
                        {
                            // Check if the comparison item's parameter reference matches the
                            // application ID name
                            if (comparison.getParameterRef().equals(applicationIDName))
                            {
                                // Create a data field for the table containing the application ID.
                                // Once a match is found the search is discontinued
                                tableDefn.addDataField(CcddFieldHandler.getFieldDefinitionArray(tableName,
                                                                                                comparison.getParameterRef(),
                                                                                                "Application name and ID",
                                                                                                inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                                                Math.min(Math.max(comparison.getValue().length(),
                                                                                                                  5),
                                                                                                         40),
                                                                                                false,
                                                                                                ApplicabilityType.ROOT_ONLY,
                                                                                                comparison.getValue(),
                                                                                                false));
                                break;
                            }
                        }
                    }
                }

                // Get the reference to the sequence container's entry list to shorten subsequent
                // calls
                List<SequenceEntryType> sequenceEntries = seqContainer.getEntryList().getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry();

                // Step through each entry in the sequence container
                for (int seqIndex = 0; seqIndex < sequenceEntries.size(); seqIndex++)
                {
                    ParameterInformation parmInfo = null;

                    // Get the reference to the sequence container entry to shorten subsequent
                    // calls
                    SequenceEntryType seqEntry = sequenceEntries.get(seqIndex);

                    // Check if the entry is for an array or non-array primitive data type
                    // parameter
                    if (seqEntry instanceof ParameterRefEntryType
                        || seqEntry instanceof ArrayParameterRefEntryType)
                    {
                        // Check if the telemetry information exists
                        if (parmSetType != null && parmTypeSetType != null)
                        {
                            // The sequence container entry parameter reference is the variable
                            // name. In order to get the variable's data type the corresponding
                            // parameter type entry must be found. Each parameter set entry is a
                            // parameter type name and its corresponding parameter (variable) name.
                            // The steps are (1) use the sequence container entry's parameter
                            // reference to get the parameter set entry's name, (2) locate the
                            // parameter set entry with the matching name and then use its
                            // parameter type reference to get the parameter type set name, (3)
                            // locate the parameter type set entry with the matching name and use
                            // it to extract the data type information

                            // Step through each parameter in the parameter set
                            for (int parmIndex = 0; parmIndex < parmSet.size(); parmIndex++)
                            {
                                // Get the reference to the parameter in the parameter set
                                Parameter parameter = (Parameter) parmSet.get(parmIndex);

                                // Check if this is the parameter set entry for the parameter being
                                // processed
                                if (parameter.getName().equals(seqEntry instanceof ParameterRefEntryType
                                                                                                         ? ((ParameterRefEntryType) seqEntry).getParameterRef()
                                                                                                         : ((ArrayParameterRefEntryType) seqEntry).getParameterRef()))
                                {
                                    // Get the parameter information referenced by the parameter
                                    // type
                                    parmInfo = processParameterReference(parameter,
                                                                         parmTypeSet,
                                                                         seqEntry,
                                                                         seqIndex);

                                    // Stop searching the parameter set since the matching entry
                                    // was found
                                    break;
                                }
                            }
                        }
                    }
                    // Check if this is a parameter with a structure as the data type
                    else if (seqEntry instanceof ContainerRefEntryType)
                    {
                        // Extract the structure reference
                        parmInfo = processContainerReference(ownerSystem,
                                                             sequenceEntries,
                                                             null,
                                                             seqEntry,
                                                             seqIndex);
                    }

                    // Check if this is a valid parameter or container reference
                    if (parmInfo != null)
                    {
                        // Update the sequence index. A container reference to an array causes this
                        // index to update in order to skip the array member container references;
                        // otherwise no change is made to the index
                        seqIndex = parmInfo.getSeqIndex();

                        // Add the row to the structure table. Multiple rows are added for an array
                        rowIndex = addVariableDefinitionToStructure(tableDefn,
                                                                    rowIndex,
                                                                    parmInfo.getNumArrayMembers(),
                                                                    parmInfo.getParameterName(),
                                                                    parmInfo.getDataType(),
                                                                    parmInfo.getArraySize(),
                                                                    parmInfo.getBitLength(),
                                                                    parmInfo.getDescription(),
                                                                    parmInfo.getUnits(),
                                                                    parmInfo.getEnumeration(),
                                                                    parmInfo.getMinimum(),
                                                                    parmInfo.getMaximum());
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
     * @param tableName
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
        // Get the command set information
        MetaCommandSet metaCmdSet = cmdMetaData.getMetaCommandSet();

        // Check if the command set information exists
        if (metaCmdSet != null)
        {
            int rowIndex = 0;
            TableDefinition structTableDefn = null;

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
                    String[] rowData = new String[numCommandColumnsAll];
                    Arrays.fill(rowData, null);
                    rowData[commandNameIndex] = metaCmd.getName();

                    // Get the base meta-command reference
                    BaseMetaCommand baseMetaCmd = metaCmd.getBaseMetaCommand();

                    // Check if the base meta-command exists
                    if (baseMetaCmd != null && baseMetaCmd.getArgumentAssignmentList() != null)
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
                                        if (fieldInfo[FieldsColumn.FIELD_NAME.ordinal()].equals(applicationIDName))
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
                                                                                                        applicationIDName,
                                                                                                        "Application name and ID",
                                                                                                        inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID),
                                                                                                        Math.min(Math.max(argAssn.getArgumentValue().length(),
                                                                                                                          5),
                                                                                                                 40),
                                                                                                        false,
                                                                                                        ApplicabilityType.ALL,
                                                                                                        argAssn.getArgumentValue(),
                                                                                                        false));
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

                    // Check if the command container entry list exists
                    if (metaCmd.getCommandContainer() != null
                        && metaCmd.getCommandContainer().getEntryList() != null)
                    {
                        int cmdArgIndex = 0;

                        // Get the reference to the sequence container's entry list to shorten
                        // subsequent calls
                        List<JAXBElement<? extends SequenceEntryType>> sequenceEntries = metaCmd.getCommandContainer()
                                                                                                .getEntryList()
                                                                                                .getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry();

                        // Step through each entry in the sequence container
                        for (int seqIndex = 0; seqIndex < sequenceEntries.size(); seqIndex++)
                        {
                            ParameterInformation argInfo = null;

                            // Get the entry from the entry element to shorten subsequent calls
                            SequenceEntryType seqEntry = sequenceEntries.get(seqIndex).getValue();

                            // Check if the entry if for an array and the parameter reference
                            // matches the target parameter
                            if (seqEntry instanceof ArgumentRefEntry
                                || seqEntry instanceof ArrayParameterRefEntryType)
                            {
                                // Check if the command has any arguments
                                if (metaCmd.getArgumentList() != null && argTypeSet != null)
                                {
                                    // Get the argument type reference
                                    String argTypeRef = seqEntry instanceof ArgumentRefEntry
                                                                                             ? ((ArgumentRefEntry) seqEntry).getArgumentRef()
                                                                                             : ((ArrayParameterRefEntryType) seqEntry).getParameterRef();

                                    // Step through each of the command's arguments
                                    for (Argument argument : metaCmd.getArgumentList().getArgument())
                                    {
                                        // Check if this is the argument list entry matching the
                                        // current argument by comparing the argument references
                                        if (argument.getName().equals(argTypeRef))
                                        {
                                            // Get the argument information referenced by the
                                            // argument type
                                            argInfo = processArgumentReference(argument,
                                                                               argTypeSet,
                                                                               seqEntry,
                                                                               seqIndex);

                                            // Stop searching the argument list set since the
                                            // matching entry was found
                                            break;
                                        }
                                    }
                                }
                            }
                            // Check if this is a reference to another command space system nested
                            // within the current one
                            else if (seqEntry instanceof ContainerRefEntryType)
                            {
                                // Extract the command reference
                                argInfo = processContainerReference(system,
                                                                    null,
                                                                    sequenceEntries,
                                                                    seqEntry,
                                                                    seqIndex);
                            }

                            // Check if this is a valid parameter or container reference
                            if (argInfo != null)
                            {
                                // Increment the argument index
                                cmdArgIndex++;

                                // Update the sequence index. A container reference to an array
                                // causes this index to update in order to skip the array member
                                // container references; otherwise no change is made to the index
                                seqIndex = argInfo.getSeqIndex();

                                // Check if the command argument index is within the range dictated
                                // by the table type definition
                                if (cmdArgIndex <= commandArguments.size())
                                {
                                    // Get the command argument reference
                                    AssociatedColumns acmdArg = commandArguments.get(cmdArgIndex - 1);

                                    // Check if the command argument name is present
                                    if (acmdArg.getName() != -1
                                        && argInfo.getParameterName() != null)
                                    {
                                        // Store the command argument name
                                        rowData[acmdArg.getName()] = argInfo.getParameterName();
                                    }

                                    // Check if the command argument data type is present
                                    if (acmdArg.getDataType() != -1
                                        && argInfo.getDataType() != null)
                                    {
                                        // Store the command argument data type
                                        rowData[acmdArg.getDataType()] = argInfo.getDataType();
                                    }

                                    // Check if the command argument array size is present
                                    if (acmdArg.getArraySize() != -1
                                        && argInfo.getArraySize() != null)
                                    {
                                        // Store the command argument array size
                                        rowData[acmdArg.getArraySize()] = argInfo.getArraySize();
                                    }

                                    // Check if the command argument bit length is present
                                    if (acmdArg.getBitLength() != -1
                                        && argInfo.getBitLength() != null)
                                    {
                                        // Store the command argument bit length
                                        rowData[acmdArg.getBitLength()] = argInfo.getBitLength();
                                    }

                                    // Check if the command argument enumeration is present
                                    if (acmdArg.getEnumeration() != -1
                                        && argInfo.getEnumeration() != null)
                                    {
                                        // Store the command argument enumeration
                                        rowData[acmdArg.getEnumeration()] = argInfo.getEnumeration();
                                    }

                                    // Check if the command argument description is present
                                    if (acmdArg.getDescription() != -1
                                        && argInfo.getDescription() != null)
                                    {
                                        // Store the command argument description
                                        rowData[acmdArg.getDescription()] = argInfo.getDescription();
                                    }

                                    // Check if the command argument units
                                    // is present
                                    if (acmdArg.getUnits() != -1 && argInfo.getUnits() != null)
                                    {
                                        // Store the command argument units
                                        rowData[acmdArg.getUnits()] = argInfo.getUnits();
                                    }

                                    // Check if the command argument minimum is present
                                    if (acmdArg.getMinimum() != -1 && argInfo.getMinimum() != null)
                                    {
                                        // Store the command argument minimum
                                        rowData[acmdArg.getMinimum()] = argInfo.getMinimum();
                                    }

                                    // Check if the command argument maximum is present
                                    if (acmdArg.getMaximum() != -1 && argInfo.getMaximum() != null)
                                    {
                                        // Store the command argument maximum
                                        rowData[acmdArg.getMaximum()] = argInfo.getMaximum();
                                    }
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
                            // Add the new row to the command table definition. Only the columns
                            // that apply to the command table type are added; the excess columns
                            // in the row data array for those commands translated to a structure
                            // (i.e., command header tables) are removed
                            tableDefn.addData(Arrays.copyOf(rowData, numCommandColumns));
                        }
                    }
                    // The command is a header type. Convert it to a structure unless importing
                    // only a single command table
                    else if (structureTypeDefn != null)
                    {
                        // Check if the structure table definition hasn't been created
                        if (structTableDefn == null)
                        {
                            // Create a structure table definition to contain this command header
                            structTableDefn = new TableDefinition(tableName,
                                                                  system.getLongDescription());

                            // Set the new structure table's table type name
                            structTableDefn.setTypeName(structureTypeDefn.getName());
                        }

                        // Step through each command argument in the command header
                        for (AssociatedColumns cmdArg : commandArguments)
                        {
                            // Get the total number of array members for the command argument; set
                            // to 0 if the argument isn't an array
                            int numArrayMembers = rowData[cmdArg.getArraySize()] != null
                                                  && !rowData[cmdArg.getArraySize()].isEmpty()
                                                                                               ? ArrayVariable.getNumMembersFromArraySize(rowData[cmdArg.getArraySize()])
                                                                                               : 0;

                            // Add the command argument as a variable to the command header
                            // structure table
                            rowIndex = addVariableDefinitionToStructure(structTableDefn,
                                                                        rowIndex,
                                                                        numArrayMembers,
                                                                        rowData[cmdArg.getName()],
                                                                        rowData[cmdArg.getDataType()],
                                                                        rowData[cmdArg.getArraySize()],
                                                                        rowData[cmdArg.getBitLength()],
                                                                        rowData[cmdArg.getDescription()],
                                                                        rowData[cmdArg.getUnits()],
                                                                        rowData[cmdArg.getEnumeration()],
                                                                        rowData[cmdArg.getMinimum()],
                                                                        rowData[cmdArg.getMaximum()]);

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
            }

            // Check if the command table definition contains any commands. If the entire table was
            // converted to a structure then there won't be any data rows, in which case the
            // command table doesn't get generated
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
    }

    /**********************************************************************************************
     * Add a variable definition's column values to a structure table
     *
     * @param tableDefn
     *            table definition reference
     *
     * @param rowIndex
     *            index of the row in which to insert the data
     *
     * @param numArrayMembers
     *            number of array members; 0 if not an array parameter
     *
     * @param variableName
     *            variable name; null to not specify
     *
     * @param dataType
     *            parameter data type; null to not specify
     *
     * @param arraySize
     *            parameter array size; null or blank if the parameter isn't an array
     *
     * @param bitLength
     *            parameter bit length; null or blank if not a bit-wise parameter
     *
     * @param description
     *            parameter description; null to not specify
     *
     * @param units
     *            parameter units; null to not specify
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
     *
     * @param minimum
     *            minimum parameter value
     *
     * @param maximum
     *            maximum parameter value
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
                                                 String maximum)
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
            || maximum != null)
        {
            String arrayDefnName = null;
            int[] currentIndices = null;
            int[] totalDims = null;

            // Create a new row of data in the table definition to contain this parameter's
            // information. Columns values are null if no value is specified (the table paste
            // method uses this to distinguish between a skipped cell and a pasted blank)
            String[] newRow = new String[numStructureColumns];
            Arrays.fill(newRow, null);
            tableDefn.addData(newRow);

            // Step through each parameter to add. A single pass is made for non-array parameters.
            // For array parameters a pass is made for the array definition plus for each array
            // member
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
                        variableName = arrayDefnName
                                       + ArrayVariable.formatArrayIndex(currentIndices);

                        // Check if this wasn't the last array member (no need to calculate the
                        // index for a member after the last one)
                        if (varIndex != numArrayMembers)
                        {
                            // Step through the array indices so that the next array index can be
                            // created
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
                                    // Exit the loop; the array index is set for the next member
                                    break;
                                }
                            }
                        }
                    }
                }

                // Store the variable definition's column values if the column exists in the
                // structure table type definition (all of these columns exist when the table type
                // is created during import, but certain ones may not exist when importing into an
                // existing structure)
                tableDefn.getData().set(rowIndex * numStructureColumns + variableNameIndex,
                                        variableName);
                tableDefn.getData().set(rowIndex * numStructureColumns + dataTypeIndex, dataType);
                tableDefn.getData().set(rowIndex * numStructureColumns + arraySizeIndex,
                                        arraySize);
                tableDefn.getData().set(rowIndex * numStructureColumns + bitLengthIndex,
                                        bitLength);

                if (enumerationIndex != -1)
                {
                    tableDefn.getData().set(rowIndex * numStructureColumns + enumerationIndex,
                                            enumeration);
                }

                if (descriptionIndex != -1)
                {
                    tableDefn.getData().set(rowIndex * numStructureColumns + descriptionIndex,
                                            description);
                }

                if (unitsIndex != -1)
                {
                    tableDefn.getData().set(rowIndex * numStructureColumns + unitsIndex, units);
                }

                if (minimumIndex != -1)
                {
                    tableDefn.getData().set(rowIndex * numStructureColumns + minimumIndex,
                                            minimum);
                }

                if (maximumIndex != -1)
                {
                    tableDefn.getData().set(rowIndex * numStructureColumns + maximumIndex,
                                            maximum);
                }

                rowIndex++;
            }
        }

        return rowIndex;
    }

    /**********************************************************************************************
     * Process the contents of telemetry sequence or command container entry list parameter or
     * array parameter reference to extract the parameter attributes
     *
     * @param parameter
     *            reference to the parameter in the parameter set
     *
     * @param parmTypeSet
     *            reference to the parameter type set list
     *
     * @param seqEntry
     *            reference to the sequence container's entry list item to process
     *
     * @param seqIndex
     *            Index of the sequence container's entry list item to process
     *
     * @return ParameterInformation for the container reference; null if the reference isn't valid
     *********************************************************************************************/
    private ParameterInformation processParameterReference(Parameter parameter,
                                                           List<NameDescriptionType> parmTypeSet,
                                                           SequenceEntryType seqEntry,
                                                           int seqIndex)
    {
        ParameterInformation parameterInfo = null;
        String matchParmType = null;

        // Initialize the array information, assuming the parameter isn't an array
        int numArrayMembers = 0;

        // Initialize the parameter attributes
        String variableName = null;
        String dataType = null;
        String arraySize = null;
        String bitLength = null;
        String enumeration = null;
        String minimum = null;
        String maximum = null;
        String description = null;
        String units = null;

        // Get the variable name, which is the parameter set entry's name field
        variableName = parameter.getName();

        // Check if this is a non-array parameter
        if (seqEntry instanceof ParameterRefEntryType)
        {
            // Store the parameter type name for this parameter
            matchParmType = parameter.getParameterTypeRef();
        }
        // This is an array parameter
        else
        {
            arraySize = "";

            // Step through each dimension for the array variable
            for (Dimension dim : ((ArrayParameterRefEntryType) seqEntry).getDimensionList().getDimension())
            {
                // Check if the fixed value exists
                if (dim.getEndingIndex().getFixedValue() != null)
                {
                    // Build the array size string
                    arraySize += String.valueOf(Integer.valueOf(dim.getEndingIndex().getFixedValue()) + 1) + ",";
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
                if (parameter.getParameterTypeRef().equals(type.getName()))
                {
                    // Store the name of the array parameter's type and stop searching
                    matchParmType = ((ArrayDataTypeType) type).getArrayTypeRef();
                    break;
                }
            }
        }

        // Check if a parameter type set entry name for the parameter is set (note that if the
        // parameter is an array the steps above locate the data type entry for the individual
        // array members)
        if (matchParmType != null)
        {
            boolean isInteger = false;
            boolean isUnsigned = false;
            boolean isFloat = false;
            boolean isString = false;

            // Step through each entry in the parameter type set
            for (NameDescriptionType parmType : parmTypeSet)
            {
                // Check if the parameters type set entry's name matches the parameter type name
                // being searched
                if (matchParmType.equals(parmType.getName()))
                {
                    long dataTypeBitSize = 0;
                    BigInteger parmBitSize = null;
                    UnitSet unitSet = null;

                    // Store the parameter's description
                    description = parmType.getLongDescription();

                    // Check if the parameter is an integer data type
                    if (parmType instanceof IntegerParameterType)
                    {
                        // The 'sizeInBits' references are the integer size for non-bit-wise
                        // parameters, but equal the number of bits assigned to the parameter for a
                        // bit-wise parameter. It doens't appear that the size of the integer used
                        // to contain the parameter is stored. The assumption is made that the
                        // smallest integer required to store the bits is used. However, this can
                        // alter the originally intended bit-packing (e.g., a 3-bit and a 9-bit fit
                        // within a single 16-bit integer, but the code below assigns the first to
                        // an 8-bit integer and the second to a 16-bit integer)

                        IntegerParameterType itlm = (IntegerParameterType) parmType;

                        // Get the number of bits occupied by the parameter
                        parmBitSize = itlm.getSizeInBits();

                        // Get the parameter units reference
                        unitSet = itlm.getUnitSet();

                        // Check if integer encoding is set to 'unsigned'
                        if (itlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                        {
                            isUnsigned = true;
                        }

                        // Determine the smallest integer size that contains the number of bits
                        // occupied by the parameter
                        dataTypeBitSize = 8;

                        while (parmBitSize.longValue() > dataTypeBitSize)
                        {
                            dataTypeBitSize *= 2;
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
                        dataTypeBitSize = ftlm.getSizeInBits().longValue();
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
                        dataTypeBitSize = Integer.valueOf(stlm.getStringDataEncoding().getSizeInBits().getFixed().getFixedValue());
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

                            parmBitSize = etlm.getIntegerDataEncoding().getSizeInBits();
                            unitSet = etlm.getUnitSet();

                            // Check if integer encoding is set to 'unsigned'
                            if (etlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                            {
                                isUnsigned = true;
                            }

                            // Determine the smallest integer size that contains the number of bits
                            // occupied by the parameter
                            dataTypeBitSize = 8;

                            while (parmBitSize.longValue() > dataTypeBitSize)
                            {
                                dataTypeBitSize *= 2;
                            }

                            isInteger = true;
                        }
                    }

                    // Get the name of the primitive data type from the data type table that
                    // matches the base type and size of the parameter
                    dataType = getMatchingDataType(dataTypeBitSize / 8,
                                                   isInteger,
                                                   isUnsigned,
                                                   isFloat,
                                                   isString,
                                                   dataTypeHandler);

                    // Check if the parameter bit size exists
                    if (parmBitSize != null
                        && parmBitSize.longValue() != dataTypeBitSize)
                    {
                        // Store the bit length
                        bitLength = parmBitSize.toString();
                    }

                    // Check if the units exists
                    if (unitSet != null
                        && !unitSet.getUnit().isEmpty())
                    {
                        // Store the units
                        units = unitSet.getUnit().get(0).getContent();
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
                                                             numArrayMembers,
                                                             seqIndex);

                    // Stop searching since a matching parameter type entry was found
                    break;
                }
            }
        }

        return parameterInfo;
    }

    /**********************************************************************************************
     * Process the contents of telemetry sequence or command container entry list parameter or
     * array parameter reference to extract the parameter attributes
     *
     * @param argument
     *            reference to the argument in the argument list
     *
     * @param argTypeSet
     *            reference to the argument type set list
     *
     * @param seqEntry
     *            reference to the sequence container's entry list item to process
     *
     * @param seqIndex
     *            Index of the sequence container's entry list item to process
     *
     * @return ParameterInformation for the container reference; null if the reference isn't valid
     *********************************************************************************************/
    private ParameterInformation processArgumentReference(Argument argument,
                                                          List<NameDescriptionType> argTypeSet,
                                                          SequenceEntryType seqEntry,
                                                          int seqIndex)
    {
        ParameterInformation argumentInfo = null;

        // Initialize the argument's attributes
        String argName = argument.getName();
        String dataType = null;
        String arraySize = null;
        String bitLength = null;
        BigInteger argBitSize = null;
        String enumeration = null;
        String description = null;
        UnitSet unitSet = null;
        String units = null;
        String minimum = null;
        String maximum = null;

        // Step through each command argument type
        for (NameDescriptionType argType : argTypeSet)
        {
            // Check if this is the same command argument referenced in the argument list (by
            // matching the command and argument names between the two)
            if (argument.getArgumentTypeRef().equals(argType.getName()))
            {
                boolean isInteger = false;
                boolean isUnsigned = false;
                boolean isFloat = false;
                boolean isString = false;

                // Check if this is an array parameter
                if (seqEntry instanceof ArrayParameterRefEntryType)
                {
                    arraySize = "";

                    // Store the reference to the array parameter type
                    ArrayDataTypeType arrayType = (ArrayDataTypeType) argType;
                    argType = null;

                    // Step through each dimension for the array variable
                    for (Dimension dim : ((ArrayParameterRefEntryType) seqEntry).getDimensionList().getDimension())
                    {
                        // Check if the fixed value exists
                        if (dim.getEndingIndex().getFixedValue() != null)
                        {
                            // Build the array size string
                            arraySize += String.valueOf(Integer.valueOf(dim.getEndingIndex().getFixedValue()) + 1) + ",";
                        }
                    }

                    arraySize = CcddUtilities.removeTrailer(arraySize, ",");

                    // The array parameter type references a non-array parameter type that
                    // describes the individual array members. Step through each data type in the
                    // parameter type set in order to locate this data type entry
                    for (NameDescriptionType type : argTypeSet)
                    {
                        // Check if the array parameter's array type reference matches the data
                        // type name
                        if (arrayType.getArrayTypeRef().equals(type.getName()))
                        {
                            // Store the reference to the array parameter's data type and stop
                            // searching
                            argType = type;
                            break;
                        }
                    }
                }

                // Check if a data type entry for the parameter exists in the parameter type set
                // (note that if the parameter is an array the steps above locate the data type
                // entry for the individual array members)
                if (argType != null)
                {
                    long dataTypeBitSize = 0;

                    // Check if the argument is an integer data
                    // type
                    if (argType instanceof IntegerArgumentType)
                    {
                        IntegerArgumentType icmd = (IntegerArgumentType) argType;

                        // Get the number of bits occupied by the argument
                        argBitSize = icmd.getSizeInBits();

                        // Get the argument units reference
                        unitSet = icmd.getUnitSet();

                        // Check if integer encoding is set to 'unsigned'
                        if (icmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                        {
                            isUnsigned = true;
                        }

                        // Determine the smallest integer size that contains the number of bits
                        // occupied by the argument
                        dataTypeBitSize = 8;

                        while (argBitSize.longValue() > dataTypeBitSize)
                        {
                            dataTypeBitSize *= 2;
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
                        dataTypeBitSize = fcmd.getSizeInBits().longValue();
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
                        dataTypeBitSize = Integer.valueOf(scmd.getStringDataEncoding().getSizeInBits().getFixed().getFixedValue());
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
                                    // Add the separator for the enumerations
                                    enumeration += ", ";
                                }

                                // Begin building this enumeration
                                enumeration += enumType.getValue()
                                               + " | "
                                               + enumType.getLabel();
                            }

                            argBitSize = ecmd.getIntegerDataEncoding().getSizeInBits();
                            unitSet = ecmd.getUnitSet();

                            // Check if integer encoding is set to 'unsigned'
                            if (ecmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                            {
                                isUnsigned = true;
                            }

                            // Determine the smallest integer size that contains the number of bits
                            // occupied by the argument
                            dataTypeBitSize = 8;

                            while (argBitSize.longValue() > dataTypeBitSize)
                            {
                                dataTypeBitSize *= 2;
                            }

                            isInteger = true;
                        }
                    }

                    // Get the name of the data type from the data type table that matches the base
                    // type and size of the parameter
                    dataType = getMatchingDataType(dataTypeBitSize / 8,
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

                    // Check if the argument bit size exists
                    if (argBitSize != null
                        && argBitSize.longValue() != dataTypeBitSize)
                    {
                        // Store the bit length
                        bitLength = argBitSize.toString();
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

                    argumentInfo = new ParameterInformation(argName,
                                                            dataType,
                                                            arraySize,
                                                            bitLength,
                                                            enumeration,
                                                            units,
                                                            minimum,
                                                            maximum,
                                                            description,
                                                            0,
                                                            seqIndex);
                }

                break;
            }
        }

        return argumentInfo;
    }

    /**********************************************************************************************
     * Process the contents of telemetry sequence or command container entry list container
     * reference to extract the parameter name, data type, array size, and description
     *
     * @param system
     *            space system to which the container reference belongs
     *
     * @param tlmSequenceEntries
     *            reference to the list of telemetry sequence container entries; null if processing
     *            a command container reference
     *
     * @param cmdSequenceEntries
     *            reference to the list of command container entries; null if processing a
     *            telemetry sequence container reference
     *
     * @param seqEntry
     *            reference to the sequence container's entry list item to process
     *
     * @param seqIndex
     *            Index of the sequence container's entry list item to process
     *
     * @return ParameterInformation for the container reference; null if the reference isn't valid
     *********************************************************************************************/
    private ParameterInformation processContainerReference(SpaceSystemType system,
                                                           List<SequenceEntryType> tlmSequenceEntries,
                                                           List<JAXBElement<? extends SequenceEntryType>> cmdSequenceEntries,
                                                           SequenceEntryType seqEntry,
                                                           int seqIndex)
    {
        ParameterInformation containerInfo = null;
        boolean isValidReference = false;
        String parameterName = null;
        String dataType = null;
        String arraySize = null;
        String description = null;
        int numArrayMembers = 0;

        // Separate the container reference path into the child space system name, the child's
        // sequence container name, and the total array size
        String[] path = ((ContainerRefEntryType) seqEntry).getContainerRef().split("/");

        // Get the reference to the child space system indicated by the container reference. The
        // first portion of the path is the name of the child space system
        SpaceSystemType childSystem = getSpaceSystemByName(path[0], system);

        // Check if the child space system exists and that the short description field contains the
        // table path in its original (application) format. The container is ignored if these
        // criteria aren't met
        if (childSystem != null
            && childSystem.getShortDescription() != null
            && TableDefinition.isPathFormatValid(childSystem.getShortDescription()))
        {
            // Store the table's description
            description = childSystem.getLongDescription();

            // Get the data type and variable name for the structure parameter
            String[] typeAndName = TableInformation.getProtoVariableName(childSystem.getShortDescription()).split("\\.");

            // Check if both the data type and variable name are present
            if (typeAndName.length == 2)
            {
                // Store the variable name and data type
                parameterName = typeAndName[1];
                dataType = typeAndName[0];

                // Check if this parameter is an array
                if (ArrayVariable.isArrayMember(parameterName))
                {
                    // Check if the expected parts are present
                    if (path.length == ArrayContainerReference.values().length)
                    {
                        // Each member of a structure array is listed as an individual entry in the
                        // sequence container. For import purposes the array definition must be
                        // created, and from this the individual array members are generated
                        // (similar to the primitive data type parameters). The array size
                        // information is extracted from the first container reference for the
                        // array

                        // Get the variable name without the array index portion
                        parameterName = ArrayVariable.removeArrayIndex(parameterName);

                        // Get variable's array size, which is stored as the last parameter in the
                        // container reference, and use it to calculate the total number of members
                        // in the array
                        arraySize = path[ArrayContainerReference.ARRAY_SIZE.ordinal()];
                        numArrayMembers = ArrayVariable.getNumMembersFromArraySize(arraySize);
                        isValidReference = true;

                        // Build the child space system name prefix for the members of this array
                        String prefixName = dataType + "_" + parameterName;

                        int seqIndex2 = seqIndex + 1;

                        // Step through the subsequent sequence container entries
                        for (; seqIndex2 < (tlmSequenceEntries != null
                                                                       ? tlmSequenceEntries.size()
                                                                       : (cmdSequenceEntries != null
                                                                                                     ? cmdSequenceEntries.size()
                                                                                                     : 0)); seqIndex2++)
                        {
                            // Get the entry reference to shorted subsequent calls
                            seqEntry = tlmSequenceEntries != null
                                                                  ? tlmSequenceEntries.get(seqIndex2)
                                                                  : (cmdSequenceEntries != null
                                                                                                ? cmdSequenceEntries.get(seqIndex2).getValue()
                                                                                                : null);

                            // Check if the entry references a container
                            if (seqEntry != null && seqEntry instanceof ContainerRefEntryType)
                            {
                                // Separate the container reference path into the child space
                                // system name and the child's sequence container name
                                String[] memPath = ((ContainerRefEntryType) seqEntry).getContainerRef().split("/");

                                // Check if this reference is to another member of the same array
                                // by comparing the child space system prefixes
                                if (!(memPath.length == ArrayContainerReference.values().length
                                      && memPath[ArrayContainerReference.CHILD_SPC_SYS.ordinal()].endsWith(memPath[ArrayContainerReference.CHILD_SEQ_CONT.ordinal()])
                                      && memPath[ArrayContainerReference.CHILD_SPC_SYS.ordinal()].matches(prefixName + "(?:_\\d+)+")))
                                {
                                    // Stop searching; all members have been located
                                    break;
                                }
                            }
                            // The entry isn't for a container
                            else
                            {
                                // Stop searching for array members
                                break;
                            }
                        }

                        // Update the sequence container index in order to skip the array members
                        seqIndex = seqIndex2 - 1;
                    }
                }
                // Not an array
                else
                {
                    // Set the flag to indicate this is a valid container reference
                    isValidReference = true;
                }
            }
            // Both the data type and variable name aren't present
            else
            {
                // Set the flag to indicate this is a valid container reference
                isValidReference = true;

                // Use whatever is in the short description as the type and variable name
                parameterName = typeAndName[0];
                dataType = typeAndName[0];
            }

            // Check if the parameter is valid
            if (isValidReference)
            {
                // Store the parameter information
                containerInfo = new ParameterInformation(parameterName,
                                                         dataType,
                                                         arraySize,
                                                         description,
                                                         numArrayMembers,
                                                         seqIndex);
            }
        }

        return containerInfo;
    }

    /**********************************************************************************************
     * Export the specified tables in XTCE XML format to the specified file
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
     *            * Not used for XTCE export (all macros are expanded) * true to replace any
     *            embedded macros with their corresponding values
     *
     * @param includeAllTableTypes
     *            * Not used for XTCE export * true to include the all table type definitions in
     *            the export file
     *
     * @param includeAllDataTypes
     *            * Not used for XTCE export * true to include the all data type definitions in the
     *            export file
     *
     * @param includeAllInputTypes
     *            * Not used for XTCE export * true to include the all user-defined input type
     *            definitions in the export file
     *
     * @param includeAllMacros
     *            * Not used for XTCE export * true to include the all macro definitions in the
     *            export file
     *
     * @param includeReservedMsgIDs
     *            * Not used for XTCE export * true to include the contents of the reserved message
     *            ID table in the export file
     *
     * @param includeProjectFields
     *            * Not used for XTCE export * true to include the project-level data field
     *            definitions in the export file
     *
     * @param includeGroups
     *            * Not used for XTCE export * true to include the groups and group data field
     *            definitions in the export file
     *
     * @param includeAssociations
     *            * Not used for XTCE export * true to include the script associations in the
     *            export file
     *
     * @param includeVariablePaths
     *            * Not used for XTCE export * true to include the variable path for each variable
     *            in a structure table, both in application format and using the user-defined
     *            separator characters
     *
     * @param variableHandler
     *            * Not used for XTCE export * variable handler class reference; null if
     *            includeVariablePaths is false
     *
     * @param separators
     *            * Not used for XTCE export * string array containing the variable path separator
     *            character(s), show/hide data types flag ('true' or 'false'), and data
     *            type/variable name separator character(s); null if includeVariablePaths is false
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
     * @throws JAXBException
     *             If an error occurs marshaling the project
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
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
                             Object... extraInfo) throws JAXBException,
                                                  MarshalException,
                                                  CCDDException,
                                                  Exception
    {
        // Convert the table data into XTCE XML format
        convertTablesToXTCE(tableNames,
                            includeBuildInformation,
                            (EndianType) extraInfo[0],
                            (boolean) extraInfo[1],
                            (String) extraInfo[2],
                            (String) extraInfo[3],
                            (String) extraInfo[4],
                            (String) extraInfo[5],
                            (String) extraInfo[6]);

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
     * Convert the project database contents to XTCE XML format
     *
     * @param tableNames
     *            array of table names to convert
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
     *
     * @throws CCDDException
     *             error occurs executing an external (script) method
     *********************************************************************************************/
    private void convertTablesToXTCE(String[] tableNames,
                                     boolean includeBuildInformation,
                                     EndianType endianess,
                                     boolean isHeaderBigEndian,
                                     String version,
                                     String validationStatus,
                                     String classification1,
                                     String classification2,
                                     String classification3) throws CCDDException
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
        rootSystem = addSpaceSystem(null,
                                    cleanSystemPath(dbControl.getProjectName()),
                                    dbControl.getDatabaseDescription(dbControl.getDatabaseName()),
                                    dbControl.getProjectName(),
                                    classification1Attr,
                                    validationStatusAttr,
                                    versionAttr);

        // Check if the build information is to be output
        if (includeBuildInformation)
        {
            // Set the project's build information
            AuthorSet author = factory.createHeaderTypeAuthorSet();
            author.getAuthor().add(dbControl.getUser());
            rootSystem.getHeader().setAuthorSet(author);
            NoteSet note = factory.createHeaderTypeNoteSet();
            note.getNote().add("CCDD Version: " + ccddMain.getCCDDVersionInformation());
            note.getNote().add("Date: " + new Date().toString());
            note.getNote().add("Project: " + dbControl.getProjectName());
            note.getNote().add("Host: " + dbControl.getServer());
            note.getNote().add("Endianess: " + (endianess == EndianType.BIG_ENDIAN
                                                                                   ? "big"
                                                                                   : "little"));
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
        AncillaryDataSet ancillarySet = factory.createDescriptionTypeAncillaryDataSet();

        // Check if the telemetry header table name is defined
        if (tlmHeaderTable != null && !tlmHeaderTable.isEmpty())
        {
            // Store the telemetry header table name
            AncillaryData tlmHdrTblValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
            tlmHdrTblValue.setName(DefaultInputType.XML_TLM_HDR.getInputName());
            tlmHdrTblValue.setValue(tlmHeaderTable);
            ancillarySet.getAncillaryData().add(tlmHdrTblValue);
        }

        // Check if the command header table name is defined
        if (cmdHeaderTable != null && !cmdHeaderTable.isEmpty())
        {
            // Store the command header table name
            AncillaryData cmdHdrTblValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
            cmdHdrTblValue.setName(DefaultInputType.XML_CMD_HDR.getInputName());
            cmdHdrTblValue.setValue(cmdHeaderTable);
            ancillarySet.getAncillaryData().add(cmdHdrTblValue);
        }

        // Store the application ID variable name
        AncillaryData appIDNameValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
        appIDNameValue.setName(DefaultInputType.XML_APP_ID.getInputName());
        appIDNameValue.setValue(applicationIDName);
        ancillarySet.getAncillaryData().add(appIDNameValue);

        // Store the command function code variable name
        AncillaryData cmdCodeNameValue = factory.createDescriptionTypeAncillaryDataSetAncillaryData();
        cmdCodeNameValue.setName(DefaultInputType.XML_FUNC_CODE.getInputName());
        cmdCodeNameValue.setValue(cmdFuncCodeName);
        ancillarySet.getAncillaryData().add(cmdCodeNameValue);
        project.getValue().setAncillaryDataSet(ancillarySet);

        // Add the project's space systems, parameters, and commands
        buildSpaceSystems(tableNames);
    }

    /**********************************************************************************************
     * Build the space systems
     *
     * @param tableNames
     *            array containing the names of the tables to export
     *
     * @throws CCDDException
     *             error occurred executing an external (script) method
     *********************************************************************************************/
    private void buildSpaceSystems(String[] tableNames) throws CCDDException
    {
        List<String> processedTables = new ArrayList<String>();

        // Get the telemetry and command header table system paths (if present)
        String tlmHdrSysPath = fieldHandler.getFieldValue(tlmHeaderTable, DefaultInputType.SYSTEM_PATH);
        String cmdHdrSysPath = fieldHandler.getFieldValue(cmdHeaderTable, DefaultInputType.SYSTEM_PATH);

        // Step through each table path+name
        for (String tablePath : tableNames)
        {
            String tableName = tablePath;
            String systemPath = null;
            boolean isTlmHdrTable = false;
            boolean isCmdHdrTable = false;

            // Store the table name as the one used for extracting data from the project database.
            // The two names differ when a descendant of the telemetry header is loaded
            String loadTableName = tableName;

            // Check if this table is a reference to the telemetry header table or one of its
            // descendant tables
            if (tablePath.matches("(?:[^,]+,)?" + tlmHeaderTable + "(?:\\..*|,.+|$)"))
            {
                // Only one telemetry header table is created even though multiple instances of it
                // may be referenced. The prototype is used to define the telemetry header; any
                // custom values in the instances are ignored. Descendants of the telemetry header
                // table are treated similarly

                isTlmHdrTable = true;

                // Set the system path to that for the telemetry header table
                systemPath = tlmHdrSysPath;

                // Check if this is a reference to the telemetry header table
                if (TableInformation.getPrototypeName(tablePath).equals(tlmHeaderTable))
                {
                    // Set the table name and path to the prototype
                    tableName = tlmHeaderTable;
                    loadTableName = tlmHeaderTable;
                    tablePath = tlmHeaderTable;
                }
                // This is a reference to a descendant of the telemetry header table
                else
                {
                    // Set the system path to point to the space system for the telemetry header
                    // child table's parent table. The system path is the one used by the telemetry
                    // header table. The parent is extracted from the child table's name by
                    // removing the root table and the telemetry header table's variable name (if
                    // this is an instance of the telemetry header table), then the child table's
                    // prototype and variable name are removed from the end. The commas separating
                    // the table's hierarchy are converted to '/' characters to complete the
                    // conversion to a system path
                    systemPath = cleanSystemPath((systemPath == null
                                                  || systemPath.isEmpty()
                                                                          ? ""
                                                                          : systemPath + "/")
                                                 + tablePath.replaceFirst("(?:.*,)?("
                                                                          + tlmHeaderTable
                                                                          + ")[^,]+,(.*)",
                                                                          "$1,$2")
                                                            .replaceFirst("(.*),.*", "$1")
                                                            .replaceAll(",", "/"));

                    // Store the table's prototype and variable name as the table name. The actual
                    // table to load doesn't need the variable name, so it's removed from the table
                    // name
                    tableName = TableInformation.getProtoVariableName(tablePath);
                    loadTableName = TableInformation.getPrototypeName(tablePath);

                    // Adjust the table path from the instance reference to a pseudo-prototype
                    // reference. This is used to populate the short description, which in turn is
                    // used during import to place the structure within the correct hierarchy
                    tablePath = tablePath.replaceFirst("(?:.*,)?("
                                                       + tlmHeaderTable
                                                       + ")[^,]+(,.*)",
                                                       "$1$2");
                }
            }
            // Check if this table is a reference to the command header table or one of its
            // descendant tables
            else if (tablePath.matches(cmdHeaderTable + "(?:,.+|$)"))
            {
                // The command header is a root structure table. The prototype tables for
                // descendants of the command header table are loaded instead of the specific
                // instances; any custom values in the instances are ignored

                isCmdHdrTable = true;

                // Set the system path to that for the command header table
                systemPath = cmdHdrSysPath;

                // Check if this is a reference to the command header table
                if (TableInformation.getPrototypeName(tablePath).equals(cmdHeaderTable))
                {
                    // Set the table name to the prototype
                    tableName = cmdHeaderTable;
                    loadTableName = cmdHeaderTable;
                }
                // This is a reference to a descendant of the command header table
                else
                {
                    // Set the system path to point to the space system for the command header
                    // child table's parent table. The system path is the one used by the command
                    // header table. The parent is extracted from the child table's name by
                    // removing the root table and the command header table's variable name (if
                    // this is an instance of the command header table), then the child table's
                    // prototype and variable name are removed from the end. The commas separating
                    // the table's hierarchy are converted to '/' characters to complete the
                    // conversion to a system path
                    systemPath = cleanSystemPath((systemPath == null
                                                  || systemPath.isEmpty()
                                                                          ? ""
                                                                          : systemPath + "/")
                                                 + tablePath.replaceFirst("("
                                                                          + cmdHeaderTable
                                                                          + ")[^,]+,(.*)",
                                                                          "$1,$2")
                                                            .replaceFirst("(.*),.*", "$1")
                                                            .replaceAll(",", "/"));

                    // Store the table's prototype and variable name as the table name. The actual
                    // table to load doesn't need the variable name, so it's removed from the table
                    // name
                    tableName = TableInformation.getProtoVariableName(tablePath);
                    loadTableName = TableInformation.getPrototypeName(tablePath);
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

                // Get the information from the database for the specified table
                TableInformation tableInfo = dbTable.loadTableData(loadTableName,
                                                                   true,
                                                                   false,
                                                                   parent);

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

                        // Get the application ID data field value, if present
                        String applicationID = CcddMessageIDHandler.getMessageID(fieldHandler.getFieldValue(loadTableName,
                                                                                                            DefaultInputType.MESSAGE_NAME_AND_ID));

                        // Check if the system path isn't already defined (this is the case for
                        // children of the telemetry header table)
                        if (systemPath == null)
                        {
                            // Get the path of the system to which this table belongs from the
                            // table'ss root table system path data field (if present)
                            systemPath = fieldHandler.getFieldValue(tableInfo.getRootTable(),
                                                                    DefaultInputType.SYSTEM_PATH);
                        }

                        // Initialize the parent system to be the root (top-level) system
                        SpaceSystemType parentSystem = project.getValue();

                        // Store the table name and get the index of the last instance table
                        // referenced in the table's path
                        String shortTableName = tableName;
                        int index = tableInfo.getTablePath().lastIndexOf(",");

                        // Check if the table is an instance table
                        if (index != -1)
                        {
                            // Get the name of the final table (dataType.varName) in the path. This
                            // shorter name is used to identify the space system (it's position in
                            // the space system hierarchy determines its parent table)
                            shortTableName = tableInfo.getTablePath().substring(index + 1);

                            // Check if the root table for this instance has a system path defined
                            if (systemPath == null)
                            {
                                systemPath = "";
                            }

                            // Add the table's path to its system path. Change each comma to a '/'
                            // so that this instance is placed correctly in its space system
                            // hierarchy
                            systemPath += "/" +
                                          macroHandler.getMacroExpansion(tableInfo.getTablePath()
                                                                                  .substring(0, index)
                                                                                  .replaceAll(",", "/"));
                        }

                        // Check if a system path exists (it always exists for an instance table,
                        // but not necessarily for a root/prototype table)
                        if (systemPath != null)
                        {
                            // Replace any invalid characters with an underscore so that the space
                            // system name complies with the XTCE schema
                            systemPath = cleanSystemPath(systemPath);

                            // Step through each system name in the path
                            for (String systemName : systemPath.split("\\s*/\\s*"))
                            {
                                // Check if the system name isn't blank (this ignores a beginning
                                // '/' if present)
                                if (!systemName.isEmpty())
                                {
                                    // Search the existing space systems for one with this system's
                                    // name (if none exists then use the root system's name)
                                    SpaceSystemType existingSystem = getSpaceSystemByName(systemName,
                                                                                          parentSystem);

                                    // Set the parent system to the existing system if found, else
                                    // create a new space system using the name from the table's
                                    // system path data field
                                    parentSystem = existingSystem == null
                                                                          ? addSpaceSystem(parentSystem,
                                                                                           systemName,
                                                                                           null,
                                                                                           null,
                                                                                           classification2Attr,
                                                                                           validationStatusAttr,
                                                                                           versionAttr)
                                                                          : existingSystem;
                                }
                            }
                        }

                        // Add the space system, if needed. It's possible it may already exist due
                        // to being referenced in the path of a child table. In this case the space
                        // system isn't created again, but the descriptions and attributes are
                        // updated to those for this table since these aren't supplied if the space
                        // system is created due t being in a child's path
                        parentSystem = addSpaceSystem(parentSystem,
                                                      cleanSystemPath(macroHandler.getMacroExpansion(shortTableName)),
                                                      tableInfo.getDescription(),
                                                      macroHandler.getMacroExpansion(tablePath),
                                                      classification3Attr,
                                                      validationStatusAttr,
                                                      versionAttr);

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

                            // Expand any macros in the table path
                            tableName = macroHandler.getMacroExpansion(tableName);

                            // Check if this is the command header structure or a descendant
                            // structure of the command header. In order for it to be referenced as
                            // the header by the command tables it must be converted into the same
                            // format as a command table, then rendered into XTCE XML as
                            // CommandMetaData
                            if (isCmdHdrTable)
                            {
                                // Set the number of argument columns per command argument
                                int columnsPerArg = CcddTableTypeHandler.commandArgumentColumns.length;

                                // Initialize the offset in the command row so that space if
                                // created for the command name and description, then created an
                                // array to contain the converted command table data
                                int argOffset = 2;
                                String[][] tableData = new String[1][tableInfo.getData().length
                                                                     * columnsPerArg
                                                                     + 2];

                                // Initialize the storage for the command argument column indices
                                commandArguments = new ArrayList<AssociatedColumns>();

                                // Store the command header table name (use only the variable name
                                // portion if this is a child table) and description as the command
                                // name and description
                                tableData[0][0] = tableName.replaceFirst("[^\\.]+\\.", "");
                                tableData[0][1] = tableInfo.getDescription();

                                // Step through each row in the command header table
                                for (String[] rowData : CcddUtilities.convertObjectToString(tableInfo.getData()))
                                {
                                    // Check if this isn't an array member (the array definition is
                                    // sufficient to define the array elements)
                                    if (!ArrayVariable.isArrayMember(rowData[varColumn]))
                                    {
                                        // Store the components of each variable within the command
                                        // header in the form of a command argument
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
                                }

                                // Add the command header or descendant arguments to the command
                                // header space system
                                addSpaceSystemCommands(parentSystem,
                                                       tableData,
                                                       0,
                                                       -1,
                                                       1,
                                                       true,
                                                       cmdHdrSysPath,
                                                       null);
                            }
                            // This is not the command header structure
                            else
                            {
                                // Add the structure table's variables to the space system's
                                // telemetry meta data
                                addSpaceSystemParameters(parentSystem,
                                                         tableName,
                                                         CcddUtilities.convertObjectToString(tableInfo.getData()),
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
                                                         dbTable.isRootStructure(loadTableName),
                                                         applicationID);
                            }
                        }
                        // This is a command table
                        else
                        {
                            // Get the list containing the associated column indices for each
                            // argument grouping
                            commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

                            // Get the first description type column in the command table type
                            // definition
                            int cmdDescColumn = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);

                            // Check if the description belongs to a command argument and not the
                            // overall command. All columns prior to the first argument name apply
                            // to the entire command
                            if (commandArguments.size() != 0
                                && commandArguments.get(0).getDescription() != -1
                                && cmdDescColumn == commandArguments.get(0).getDescription())
                            {
                                // There is no column for the command description, so reset its
                                // column index
                                cmdDescColumn = -1;
                            }

                            // Add the command(s) from this table to the parent system
                            addSpaceSystemCommands(parentSystem,
                                                   CcddUtilities.convertObjectToString(tableInfo.getData()),
                                                   typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME),
                                                   typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE),
                                                   cmdDescColumn,
                                                   false,
                                                   cmdHdrSysPath,
                                                   applicationID);
                        }
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
     * Create a new space system as a child of the specified space system, if it doesn't already
     * exist. If the system already exists then use the supplied description, full path, and
     * document attributes to update the system. If the specified system is null then this is the
     * root space system
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
     * @param fullPath
     *            full table path; null or blank if this space system doesn't describe a table
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
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    private SpaceSystemType addSpaceSystem(SpaceSystemType parentSystem,
                                           String systemName,
                                           String description,
                                           String fullPath,
                                           String classification,
                                           String validationStatus,
                                           String version) throws CCDDException
    {
        // Get the reference to the space system if it already exists
        SpaceSystemType childSystem = parentSystem == null
                                                           ? null
                                                           : getSpaceSystemByName(systemName,
                                                                                  parentSystem);

        // Check if the space system doesn't already exist
        if (childSystem == null)
        {
            // Create the new space system, store its name, and set the flag to indicate a new
            // space system exists
            childSystem = factory.createSpaceSystemType();
            childSystem.setName(systemName);

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

        // Check if the full table path is provided
        if (fullPath != null && !fullPath.isEmpty())
        {
            // Store the table name, with its full path, in the short description field. This is
            // used if the export file is used to import tables into a project
            childSystem.setShortDescription(fullPath);
        }

        // Set the new space system's header attributes
        addSpaceSystemHeader(childSystem,
                             classification,
                             validationStatus,
                             version,
                             (parentSystem == null
                                                   ? new Date().toString()
                                                   : null));

        return childSystem;
    }

    /**********************************************************************************************
     * Get the reference to the space system with the specified name, starting at the specified
     * space system
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @param startingSystem
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
     *            space system reference
     *
     * @param classification
     *            classification attribute
     *
     * @param validationStatus
     *            validation status attribute
     *
     * @param version
     *            version attribute
     *
     * @param date
     *            export creation time and date
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addSpaceSystemHeader(SpaceSystemType spaceSystem,
                                        String classification,
                                        String validationStatus,
                                        String version,
                                        String date) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addSpaceSystemHeader",
                                         factory,
                                         spaceSystem,
                                         classification,
                                         validationStatus,
                                         version,
                                         date);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addSpaceSystemHeader<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            // Add the header
            HeaderType header = factory.createHeaderType();
            header.setClassification(classification);
            header.setValidationStatus(validationStatus);
            header.setVersion(version);
            header.setDate(date);
            spaceSystem.setHeader(header);
        }
    }

    /**********************************************************************************************
     * Create the space system telemetry metadata
     *
     * @param spaceSystem
     *            space system reference
     *********************************************************************************************/
    protected void createTelemetryMetadata(SpaceSystemType spaceSystem)
    {
        spaceSystem.setTelemetryMetaData(factory.createTelemetryMetaDataType());
    }

    /**********************************************************************************************
     * Add a structure table's parameters to the telemetry meta data
     *
     * @param spaceSystem
     *            space system to which the table belongs
     *
     * @param tableName
     *            table name
     *
     * @param tableData
     *            array containing the table's data
     *
     * @param varColumn
     *            variable (parameter) name column index
     *
     * @param typeColumn
     *            parameter data type column index
     *
     * @param sizeColumn
     *            parameter array size column index
     *
     * @param bitColumn
     *            parameter bit length column index
     *
     * @param enumColumn
     *            parameter enumeration column index; -1 if no the table has no enumeration column
     *
     * @param descColumn
     *            parameter description column index; -1 if no the table has no description column
     *
     * @param unitsColumn
     *            parameter units column index; -1 if no the table has no units column
     *
     * @param minColumn
     *            minimum parameter value column index; -1 if no the table has no minimum column
     *
     * @param maxColumn
     *            maximum parameter value column index; -1 if no the table has no maximum column
     *
     * @param isTlmHdrTable
     *            true if this table represents the telemetry header or one of its descendants
     *
     * @param tlmHdrSysPath
     *            telemetry header table system path; null or blank is none
     *
     * @param isRootStructure
     *            true if the table is a root structure table
     *
     * @param applicationID
     *            telemetry header application ID
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addSpaceSystemParameters(SpaceSystemType spaceSystem,
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
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addSpaceSystemParameters",
                                         project,
                                         factory,
                                         endianess == EndianType.BIG_ENDIAN,
                                         isHeaderBigEndian,
                                         tlmHeaderTable,
                                         spaceSystem,
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
                                         applicationIDName,
                                         applicationID);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addSpaceSystemParameters<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            EntryListType entryList = factory.createEntryListType();
            boolean isTlmHdrRef = false;

            // Step through each row in the structure table
            for (String[] rowData : tableData)
            {
                // Add the variable, if it has a primitive data type, to the parameter set and
                // parameter type set. Variables with structure data types are defined in the
                // container set
                addParameterAndType(spaceSystem,
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
                                                                       ? Integer.valueOf(rowData[sizeColumn].replaceAll("^.*(\\d+)$",
                                                                                                                        "$1"))
                                                                       : 1));

                // Add the variable, with either a primitive or structure data type, to the
                // container set
                isTlmHdrRef = addParameterSequenceEntry(spaceSystem,
                                                        rowData[varColumn],
                                                        rowData[typeColumn],
                                                        rowData[sizeColumn],
                                                        entryList,
                                                        isTlmHdrRef);
            }

            // Check if any variables were added to the entry list for the container set
            if (!entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().isEmpty())
            {
                // Create the sequence container set
                ContainerSetType containerSet = factory.createContainerSetType();
                SequenceContainerType seqContainer = factory.createSequenceContainerType();
                seqContainer.setEntryList(entryList);
                containerSet.getSequenceContainer().add(seqContainer);

                // Use the last variable name in the table's path as the container name
                seqContainer.setName(cleanSystemPath(tableName.replaceFirst(".*\\.", "")));

                // Check if this is the telemetry header
                if (isTlmHdrTable)
                {
                    // Set the abstract flag to indicate the telemetry metadata represents a
                    // telemetry header
                    seqContainer.setAbstract(true);
                }
                // Not the telemetry header. Check if this is a root structure that references the
                // telemetry header table (child structures don't require a reference to the
                // telemetry header) and if the application ID information is provided
                else if (isRootStructure
                         && isTlmHdrRef
                         && applicationIDName != null
                         && !applicationIDName.isEmpty()
                         && applicationID != null
                         && !applicationID.isEmpty())
                {
                    // Create a base container reference to the telemetry header table so that the
                    // application ID can be assigned as a restriction criteria
                    BaseContainer baseContainer = factory.createSequenceContainerTypeBaseContainer();
                    baseContainer.setContainerRef("/" + project.getValue().getName()
                                                  + (tlmHdrSysPath == null
                                                     || tlmHdrSysPath.isEmpty()
                                                                                ? ""
                                                                                : "/"
                                                                                  + cleanSystemPath(tlmHdrSysPath))
                                                  + "/" + tlmHeaderTable
                                                  + "/" + tlmHeaderTable);
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

                // Check if the telemetry metadata doesn't exit for this system
                if (spaceSystem.getTelemetryMetaData() == null)
                {
                    // Create the telemetry metadata
                    createTelemetryMetadata(spaceSystem);
                }

                // Add the parameters to the system
                spaceSystem.getTelemetryMetaData().setContainerSet(containerSet);
            }
        }
    }

    /**********************************************************************************************
     * Add a parameter with a primitive data type to the parameter set and parameter type set
     *
     * @param spaceSystem
     *            space system reference
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
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addParameterAndType(SpaceSystemType spaceSystem,
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
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addParameterAndType",
                                         factory,
                                         endianess == EndianType.BIG_ENDIAN,
                                         isHeaderBigEndian,
                                         tlmHeaderTable,
                                         spaceSystem,
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

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addParameterAndType<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            // Check if a data type is provided, that it's a primitive, and this isn't an array
            // member. The array definition is sufficient to define the array elements. Structure
            // data types are handled as containers.
            if (dataType != null
                && dataTypeHandler.isPrimitive(dataType)
                && !ArrayVariable.isArrayMember(parameterName))
            {
                // Check if this system doesn't yet have its telemetry meta data created
                if (spaceSystem.getTelemetryMetaData() == null)
                {
                    // Create the telemetry meta data
                    createTelemetryMetadata(spaceSystem);
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

                // Set the parameter's data type information
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

                // Create the parameter. This links the parameter name with the parameter reference
                // type
                Parameter parameter = factory.createParameterSetTypeParameter();
                parameter.setName(parameterName);
                parameter.setParameterTypeRef(parameterName
                                              + (arraySize.isEmpty()
                                                                     ? TYPE
                                                                     : ARRAY));

                parameterSet.getParameterOrParameterRef().add(parameter);
            }
        }
    }

    /**********************************************************************************************
     * Add the parameter to the sequence container entry list
     *
     * @param spaceSystem
     *            reference to the space system to which the parameter belongs
     *
     * @param parameterName
     *            parameter name
     *
     * @param dataType
     *            data type
     *
     * @param arraySize
     *            array size
     *
     * @param entryList
     *            reference to the entry list into which to place the parameter (for a primitive
     *            data type) or container (for a structure data type) reference
     *
     * @param isTlmHdrRef
     *            true if this table represents the telemetry header or one of its descendants
     *
     * @return true if the parameter's data type references the telemetry header or one of its
     *         descendants; otherwise return the flag status unchanged
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected boolean addParameterSequenceEntry(SpaceSystemType spaceSystem,
                                                String parameterName,
                                                String dataType,
                                                String arraySize,
                                                EntryListType entryList,
                                                boolean isTlmHdrRef) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                isTlmHdrRef = (boolean) invocable.invokeFunction("addParameterSequenceEntry",
                                                                 factory,
                                                                 tlmHeaderTable,
                                                                 spaceSystem,
                                                                 parameterName,
                                                                 dataType,
                                                                 arraySize,
                                                                 entryList,
                                                                 isTlmHdrRef);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addParameterAndType<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            // Check if the parameter is an array definition or member
            if (!arraySize.isEmpty())
            {
                // Check if this is the array definition (array members are ignored; the definition
                // is sufficient to describe the array)
                if (!ArrayVariable.isArrayMember(parameterName))
                {
                    // Check if the data type for this parameter is a primitive
                    if (dataTypeHandler.isPrimitive(dataType))
                    {
                        DimensionList dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                        // Set the array dimension start index (always 0)
                        IntegerValueType startVal = factory.createIntegerValueType();
                        startVal.setFixedValue("0");

                        // Step through each array dimension
                        for (int arrayDim : ArrayVariable.getArrayIndexFromSize(arraySize))
                        {
                            // Create the dimension and set the start and end indices (the end
                            // index is the number of elements in this array dimension minus 1)
                            Dimension dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                            IntegerValueType endVal = factory.createIntegerValueType();
                            endVal.setFixedValue(String.valueOf(arrayDim - 1));
                            dim.setStartingIndex(startVal);
                            dim.setEndingIndex(endVal);
                            dimList.getDimension().add(dim);
                        }

                        // Store the array parameter array reference in the list
                        ArrayParameterRefEntryType arrayRef = factory.createArrayParameterRefEntryType();
                        arrayRef.setParameterRef(parameterName);
                        arrayRef.setDimensionList(dimList);
                        entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(arrayRef);
                    }
                    // The data type reference is to a structure table
                    else
                    {
                        // The XTCE aggregate data type would be used to define the structure
                        // reference, but a limitation in the XTCE schema doesn't allow an array of
                        // structures to be defined. In place of the aggregate data type, a
                        // sequence container is used to define the table's members (for both
                        // primitive and structure data types). Each individual structure array
                        // member has its own space system, and each of these has an entry in the
                        // container. Each container entry includes the overall dimensions of the
                        // structure array, which is used when importing to reconstruct the array

                        // Add container references to the space system in the sequence container
                        // entry list that defines each parameter array member
                        addContainerReference(parameterName, dataType, arraySize, entryList);
                    }
                }
            }
            // Not an array definition or member. Check if this parameter has a primitive data type
            // (i.e., it isn't an instance of a structure)
            else if (dataTypeHandler.isPrimitive(dataType))
            {
                // Store the non-array parameter reference in the list
                ParameterRefEntryType parameterRef = factory.createParameterRefEntryType();
                parameterRef.setParameterRef(parameterName);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(parameterRef);
            }
            // This is a non-array structure data type. Check if the reference isn't to the
            // telemetry header table
            else if (!dataType.equals(tlmHeaderTable))
            {
                // The XTCE aggregate data type would be used to define the structure reference,
                // but a limitation in the XTCE schema doesn't allow an array of structures to be
                // defined. In place of the aggregate data type, a sequence container is used to
                // define the table's members (for both primitive and structure data types). To be
                // consistent with the treatment of structure arrays, container references are also
                // used for non-array structure variables

                // Add a container reference to the space system in the sequence container entry
                // list that defines the parameter
                addContainerReference(parameterName, dataType, arraySize, entryList);
            }
            // This is a reference to the telemetry header table
            else
            {
                // Set the flag indicating that a reference is made to the telemetry header table
                isTlmHdrRef = true;
            }
        }

        return isTlmHdrRef;
    }

    /**********************************************************************************************
     * Create the telemetry parameter data type and set the specified attributes
     *
     * @param spaceSystem
     *            space system reference
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
     *            {@literal enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
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
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a string or character
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
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
                                        String description,
                                        int stringSize) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("setParameterDataType",
                                         factory,
                                         endianess == EndianType.BIG_ENDIAN,
                                         isHeaderBigEndian,
                                         tlmHeaderTable,
                                         spaceSystem,
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

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>setParameterDataType<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            NameDescriptionType parameterType = null;

            // Note: Each parameter has an associated size in bits equal to the size of its parent
            // data type. In addition to its parent size, a bit-wise parameter (valid for an
            // integer or enumeration) also has its bit length, the subset of bits it occupies in
            // its parent. The value stored in the parameter encoding type's sizeInBits field is
            // the bit length if a bit-wise parameter, else the parent data type size is used.
            // Ideally both the bit length and overall sizes would be preserved (one in the
            // parameter type's sizeInBits field and the other in the encoding type's sizeInBits
            // field). However, this isn't always possible since the enumerated parameter type
            // lacks the sizeInBits field. To prevent possible confusion of the values, for an
            // integer parameter the parameter type's sizeInBits field is set to match the encoding
            // type's sizeInBits field

            // Check if the parameter is an array
            if (arraySize != null && !arraySize.isEmpty())
            {
                // Create an array type and set its attributes
                ArrayDataTypeType arrayType = factory.createArrayDataTypeType();
                arrayType.setName(parameterName + ARRAY);
                arrayType.setArrayTypeRef((dataTypeHandler.isPrimitive(dataType)
                                                                                 ? parameterName
                                                                                 : dataType)
                                          + TYPE);
                arrayType.setNumberOfDimensions(BigInteger.valueOf(ArrayVariable.getArrayIndexFromSize(arraySize).length));

                // Set the parameter's array information
                spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(arrayType);
            }

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
                    // Create an enumeration type and enumeration list
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
                        // Set the encoding type to indicate an unsigned or signed integer
                        intEncodingType.setEncoding("unsigned");
                    }
                    // The data type is a signed integer
                    else
                    {
                        // Set the encoding type to indicate a signed integer
                        intEncodingType.setEncoding("signMagnitude");
                    }

                    // Set the bit order
                    intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                || (isHeaderBigEndian
                                                    && tlmHeaderTable.equals(TableInformation.getPrototypeName(spaceSystem.getName())))
                                                                                                                                        ? "mostSignificantBitFirst"
                                                                                                                                        : "leastSignificantBitFirst");

                    enumType.setIntegerDataEncoding(intEncodingType);

                    // Set the enumeration list and units
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
                            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                            BigInteger intSizeInBits;

                            // Check if the parameter has a bit length
                            if (bitLength != null && !bitLength.isEmpty())
                            {
                                // Get the bit length of the argument
                                intSizeInBits = BigInteger.valueOf(Integer.parseInt(bitLength));
                            }
                            // Not a bit-wise parameter
                            else
                            {
                                // Get the bit size of the integer type
                                intSizeInBits = BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType));
                            }

                            // Set the encoding type to indicate an unsigned integer
                            integerType.setSizeInBits(intSizeInBits);
                            intEncodingType.setSizeInBits(intSizeInBits);

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                integerType.setSigned(false);
                                intEncodingType.setEncoding("unsigned");
                            }
                            // The data type is a signed integer
                            else
                            {
                                // Set the encoding type to indicate a signed integer
                                integerType.setSigned(true);
                                intEncodingType.setEncoding("signMagnitude");
                            }

                            // Set the bit order
                            intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                        || (isHeaderBigEndian
                                                            && tlmHeaderTable.equals(TableInformation.getPrototypeName(spaceSystem.getName())))
                                                                                                                                                ? "mostSignificantBitFirst"
                                                                                                                                                : "leastSignificantBitFirst");

                            // Set the encoding type and units
                            integerType.setIntegerDataEncoding(intEncodingType);
                            integerType.setUnitSet(unitSet);

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
                            // Get the bit size of the float type
                            BigInteger floatSizeInBits = BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType));

                            // Create a float parameter and set its attributes
                            FloatParameterType floatType = factory.createParameterTypeSetTypeFloatParameterType();
                            floatType.setUnitSet(unitSet);
                            floatType.setSizeInBits(floatSizeInBits);
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(floatSizeInBits);
                            floatEncodingType.setEncoding("IEEE754_1985");
                            floatType.setFloatDataEncoding(floatEncodingType);
                            floatType.setUnitSet(unitSet);

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
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                            // Set the string's size in bits based on the number of characters in
                            // the string with each character occupying a single byte
                            IntegerValueType intValType = factory.createIntegerValueType();
                            intValType.setFixedValue(String.valueOf(stringSize * 8));
                            SizeInBits stringSizeInBits = factory.createStringDataEncodingTypeSizeInBits();
                            stringSizeInBits.setFixed(intValType);
                            stringEncodingType.setSizeInBits(stringSizeInBits);
                            stringEncodingType.setEncoding("UTF-8");
                            stringType.setStringDataEncoding(stringEncodingType);
                            stringType.setUnitSet(unitSet);
                            parameterType = stringType;
                            break;
                    }
                }
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
    }

    /**********************************************************************************************
     * Create the space system command metadata
     *
     * @param spaceSystem
     *            space system reference
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void createCommandMetadata(SpaceSystemType spaceSystem) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("createCommandMetadata", factory, spaceSystem);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>createCommandMetadata<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            spaceSystem.setCommandMetaData(factory.createCommandMetaDataType());
            spaceSystem.getCommandMetaData().setMetaCommandSet(factory.createCommandMetaDataTypeMetaCommandSet());
        }
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified space system
     *
     * @param spaceSystem
     *            space system reference
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
     * @param cmdHdrSysPath
     *            command header table system path
     *
     * @param applicationID
     *            application ID
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addSpaceSystemCommands(SpaceSystemType spaceSystem,
                                          String[][] tableData,
                                          int cmdNameColumn,
                                          int cmdCodeColumn,
                                          int cmdDescColumn,
                                          boolean isCmdHeader,
                                          String cmdHdrSysPath,
                                          String applicationID) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addSpaceSystemCommands",
                                         project,
                                         factory,
                                         (endianess == EndianType.BIG_ENDIAN),
                                         isHeaderBigEndian,
                                         cmdHeaderTable,
                                         commandArguments.toArray(new AssociatedColumns[0]),
                                         spaceSystem,
                                         tableData,
                                         cmdNameColumn,
                                         cmdCodeColumn,
                                         cmdDescColumn,
                                         isCmdHeader,
                                         cmdHdrSysPath,
                                         cmdFuncCodeName,
                                         applicationIDName,
                                         applicationID);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addSpaceSystemCommands<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            // Step through each row in the table
            for (String[] rowData : tableData)
            {
                // Check if the command name exists; if the argument name is missing then the
                // entire argument is ignored
                if (cmdNameColumn != -1 && !rowData[cmdNameColumn].isEmpty())
                {
                    // Store the command name
                    String commandName = cleanSystemPath(rowData[cmdNameColumn]);

                    // Initialize the command attributes and argument names list
                    String cmdFuncCode = null;
                    String commandDescription = null;
                    List<String> argumentNames = new ArrayList<String>();
                    List<String> argDataTypes = new ArrayList<String>();
                    List<String> argArraySizes = new ArrayList<String>();

                    // Check if this system doesn't yet have its command metadata created
                    if (spaceSystem.getCommandMetaData() == null)
                    {
                        // Create the command metadata
                        createCommandMetadata(spaceSystem);
                    }

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
                                if (dataTypeHandler.isString(rowData[cmdArg.getDataType()]))
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
                            argDataTypes.add(dataType);
                            argArraySizes.add(arraySize);

                            // Check if the data type is a primitive. The data type for the command
                            // can be a structure reference if this is the command header table or
                            // a descendant table of the command header table
                            if (dataTypeHandler.isPrimitive(dataType))
                            {
                                // get the reference to the argument type set
                                ArgumentTypeSetType argument = spaceSystem.getCommandMetaData().getArgumentTypeSet();

                                // Check if the argument type set doesn't exist
                                if (argument == null)
                                {
                                    // Create the argument type set
                                    argument = factory.createArgumentTypeSetType();
                                    spaceSystem.getCommandMetaData().setArgumentTypeSet(argument);
                                }

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

                                // Add the command argument type to the command space system
                                argument.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(type);
                            }
                        }
                    }

                    // Add the command metadata set information
                    addCommand(spaceSystem,
                               commandName,
                               cmdFuncCode,
                               applicationID,
                               isCmdHeader,
                               cmdHdrSysPath,
                               argumentNames.toArray(new String[0]),
                               argDataTypes.toArray(new String[0]),
                               argArraySizes.toArray(new String[0]),
                               commandDescription);
                }
            }
        }
    }

    /**********************************************************************************************
     * Add a command to the command metadata set
     *
     * @param spaceSystem
     *            space system reference
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
     * @param cmdHdrSysPath
     *            command header table system path
     *
     * @param argumentNames
     *            array of command argument names
     *
     * @param argDataTypes
     *            array of of command argument data types
     *
     * @param argArraySizes
     *            array of of command argument array sizes; the array item is null or blank if the
     *            corresponding argument isn't an array
     *
     * @param description
     *            description of the command
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addCommand(SpaceSystemType spaceSystem,
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
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addCommand",
                                         project,
                                         factory,
                                         cmdHeaderTable,
                                         spaceSystem,
                                         commandName,
                                         cmdFuncCodeName,
                                         cmdFuncCode,
                                         applicationIDName,
                                         applicationID,
                                         isCmdHeader,
                                         cmdHdrSysPath,
                                         argumentNames,
                                         argDataTypes,
                                         argArraySizes,
                                         description);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addCommand<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
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
            if (argumentNames.length != 0)
            {
                int index = 0;
                ArgumentList argList = null;
                CommandContainerType cmdContainer = factory.createCommandContainerType();
                cmdContainer.setName(commandName);
                CommandContainerEntryListType entryList = factory.createCommandContainerEntryListType();

                // Step through each argument
                for (String argumentName : argumentNames)
                {
                    String argDataType = argDataTypes[index];
                    String argArraySize = argArraySizes[index];

                    // Set the flag to indicate that the argument is an array
                    boolean isArray = argArraySize != null && !argArraySize.isEmpty();

                    // Check if the argument data type is a primitive
                    if (dataTypeHandler.isPrimitive(argDataType))
                    {
                        // Check if this is the first argument
                        if (argList == null)
                        {
                            argList = factory.createMetaCommandTypeArgumentList();
                        }

                        // Add the argument to the the command's argument list
                        Argument arg = factory.createMetaCommandTypeArgumentListArgument();
                        arg.setName(argumentName);
                        arg.setArgumentTypeRef(argumentName
                                               + (isArray
                                                          ? ARRAY
                                                          : TYPE));
                        argList.getArgument().add(arg);

                        // Check if the command argument is an array
                        if (isArray)
                        {
                            DimensionList dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                            // Set the array dimension start index (always 0)
                            IntegerValueType startVal = factory.createIntegerValueType();
                            startVal.setFixedValue("0");

                            // Step through each array dimension
                            for (int arrayDim : ArrayVariable.getArrayIndexFromSize(argArraySize))
                            {
                                // Create the dimension and set the start and end indices (the end
                                // index is the number of elements in this array dimension minus 1)
                                Dimension dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                                IntegerValueType endVal = factory.createIntegerValueType();
                                endVal.setFixedValue(String.valueOf(arrayDim - 1));
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
                        // Not an array
                        else
                        {
                            // Store the argument reference in the list
                            ArgumentRefEntry argumentRef = factory.createCommandContainerEntryListTypeArgumentRefEntry();
                            argumentRef.setArgumentRef(argumentName);
                            JAXBElement<ArgumentRefEntry> argumentRefElem = factory.createCommandContainerEntryListTypeArgumentRefEntry(argumentRef);
                            entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(argumentRefElem);
                        }
                    }
                    // The argument data type is a structure reference. This occurs if this is the
                    // command header table or a descendant table of the command header table
                    else
                    {
                        // Add a container reference (or references if the argument is an array) to
                        // the space system in the command container entry list that defines the
                        // argument
                        addContainerReference(argumentName, argDataType, argArraySize, entryList);
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
                // Not the command header. Check if the command application ID and command header
                // table name are provided
                else if (applicationID != null
                         && !applicationID.isEmpty()
                         && cmdHeaderTable != null
                         && !cmdHeaderTable.isEmpty())
                {
                    // Create the reference to the base meta-command and set it to the empty base,
                    // in case no command header is defined
                    BaseMetaCommand baseCmd = factory.createMetaCommandTypeBaseMetaCommand();
                    baseCmd.setMetaCommandRef(cleanSystemPath("/" + project.getValue().getName()
                                                              + (cmdHdrSysPath == null
                                                                 || cmdHdrSysPath.isEmpty()
                                                                                            ? ""
                                                                                            : "/"
                                                                                              + cmdHdrSysPath)
                                                              + "/" + cmdHeaderTable
                                                              + "/" + cmdHeaderTable));

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

                // Check if the command references any primitive data types
                if (argList != null)
                {
                    command.setArgumentList(argList);
                }

                cmdContainer.setEntryList(entryList);
                command.setCommandContainer(cmdContainer);
            }

            commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand().add(command);
        }
    }

    /**********************************************************************************************
     * Set the command argument data type and set the specified attributes
     *
     * @param spaceSystem
     *            space system reference
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
     *            command argument enumeration in the format
     *            {@literal <enum label>|<enum value>[|...][,...];} null to not specify
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
     * @param uniqueID
     *            text used to uniquely identify data types with the same name; blank if the data
     *            type has no name conflict
     *
     * @return Command description of the type corresponding to the primitive data type with the
     *         specified attributes set
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected NameDescriptionType setArgumentDataType(SpaceSystemType spaceSystem,
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
        BaseDataType commandDescription = null;

        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                commandDescription = (BaseDataType) invocable.invokeFunction("setArgumentDataType",
                                                                             factory,
                                                                             endianess == EndianType.BIG_ENDIAN,
                                                                             isHeaderBigEndian,
                                                                             tlmHeaderTable,
                                                                             spaceSystem,
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

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>setArgumentDataType<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
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
                // Set the command units
                UnitSet unitSet = createUnitSet(units);

                // Check if enumeration parameters are provided
                if (enumeration != null && !enumeration.isEmpty())
                {
                    // Create an enumeration type and enumeration list
                    EnumeratedDataType enumType = factory.createEnumeratedDataType();
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

                    // Set the enumeration list and units attributes
                    enumType.setEnumerationList(enumList);
                    enumType.setUnitSet(unitSet);

                    // Check if the data type is an unsigned integer
                    if (dataTypeHandler.isUnsignedInt(dataType))
                    {
                        // Set the encoding type to indicate an unsigned integer
                        intEncodingType.setEncoding("unsigned");
                    }
                    // The data type is a signed integer
                    else
                    {
                        // Set the encoding type to indicate a signed integer
                        intEncodingType.setEncoding("signMagnitude");
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

                            BigInteger intSizeInBits;
                            // Check if the parameter has a bit length
                            if (bitLength != null && !bitLength.isEmpty())
                            {
                                // Get the bit length of the argument
                                intSizeInBits = BigInteger.valueOf(Integer.parseInt(bitLength));
                            }
                            // Not a bit-wise parameter
                            else
                            {
                                // Get the bit size of the integer type
                                intSizeInBits = BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType));
                            }

                            // Set the size in bits to the full size of the data type
                            integerType.setSizeInBits(intSizeInBits);
                            intEncodingType.setSizeInBits(intSizeInBits);

                            // Check if the data type is an unsigned integer
                            if (dataTypeHandler.isUnsignedInt(dataType))
                            {
                                // Set the encoding type to indicate an unsigned integer
                                integerType.setSigned(false);
                                intEncodingType.setEncoding("unsigned");
                            }
                            // The data type is a signed integer
                            else
                            {
                                // Set the encoding type to indicate a signed integer
                                integerType.setSigned(true);
                                intEncodingType.setEncoding("signMagnitude");
                            }

                            // Set the bit order
                            intEncodingType.setBitOrder(endianess == EndianType.BIG_ENDIAN
                                                        || (isHeaderBigEndian
                                                            && cmdHeaderTable.equals(spaceSystem.getName()))
                                                                                                             ? "mostSignificantBitFirst"
                                                                                                             : "leastSignificantBitFirst");

                            // Set the encoding type and units
                            integerType.setIntegerDataEncoding(intEncodingType);
                            integerType.setUnitSet(unitSet);

                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                IntegerArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeIntegerArgumentTypeValidRangeSet();
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

                                validRange.getValidRange().add(range);
                                integerType.setValidRangeSet(validRange);
                            }

                            commandDescription = integerType;
                            break;

                        case FLOAT:
                            // Get the bit size of the float type
                            BigInteger floatSizeInBits = BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType));

                            // Create a float command argument and set its attributes
                            FloatArgumentType floatType = factory.createArgumentTypeSetTypeFloatArgumentType();
                            floatType.setSizeInBits(floatSizeInBits);
                            FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                            floatEncodingType.setSizeInBits(floatSizeInBits);
                            floatEncodingType.setEncoding("IEEE754_1985");
                            floatType.setFloatDataEncoding(floatEncodingType);
                            floatType.setUnitSet(unitSet);

                            // Check if a minimum or maximum value is specified
                            if ((minimum != null && !minimum.isEmpty())
                                || (maximum != null && !maximum.isEmpty()))
                            {
                                FloatArgumentType.ValidRangeSet validRange = factory.createArgumentTypeSetTypeFloatArgumentTypeValidRangeSet();
                                FloatRangeType range = factory.createFloatRangeType();

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
                            StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();

                            // Set the string's size in bits based on the number of characters in
                            // the string with each character occupying a single byte
                            IntegerValueType intValType = factory.createIntegerValueType();
                            intValType.setFixedValue(String.valueOf(stringSize * 8));
                            SizeInBits stringSizeInBits = factory.createStringDataEncodingTypeSizeInBits();
                            stringSizeInBits.setFixed(intValType);
                            stringEncodingType.setSizeInBits(stringSizeInBits);
                            stringEncodingType.setEncoding("UTF-8");

                            stringType.setStringDataEncoding(stringEncodingType);
                            stringType.setUnitSet(unitSet);
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
        }

        return commandDescription;
    }

    /**********************************************************************************************
     * Add a container reference(s) for the telemetry or command parameter or parameter array to
     * the specified entry list
     *
     * @param entryList
     *            reference to the telemetry or command entry list into which to place the
     *            parameter or parameter array container reference(s)
     *
     * @param parameterName
     *            parameter name
     *
     * @param dataType
     *            data type
     *
     * @param arraySize
     *            parameter array size; null or blank if the parameter isn't an array
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    protected void addContainerReference(String parameterName,
                                         String dataType,
                                         String arraySize,
                                         Object entryList) throws CCDDException
    {
        // Set the flag assuming the internal method is used
        boolean useInternal = true;

        // Check if an external method is to be used
        if (invocable != null)
        {
            try
            {
                // Execute the external method
                invocable.invokeFunction("addContainerReference",
                                         factory,
                                         parameterName,
                                         dataType,
                                         arraySize,
                                         entryList);

                // Set the flag to indicate the internal method is not used
                useInternal = false;
            }
            catch (NoSuchMethodException nsme)
            {
                // The script method couldn't be located in the script; use the internal method
                // instead
            }
            catch (Exception e)
            {
                throw new CCDDException("Error in script function '</b>addContainerReference<b>'; cause '</b>"
                                        + e.getMessage()
                                        + "<b>'");
            }
        }

        // Check if the internal method is used
        if (useInternal)
        {
            // Check if the parameter is an array definition or member
            if (arraySize != null && !arraySize.isEmpty())
            {
                // Get the array of array dimensions and create storage for the current indices
                int[] totalDims = ArrayVariable.getArrayIndexFromSize(arraySize);
                int[] currentIndices = new int[totalDims.length];

                do
                {
                    // Step through each index in the lowest level dimension
                    for (currentIndices[0] = 0; currentIndices[0] < totalDims[totalDims.length - 1]; currentIndices[0]++)
                    {
                        // Get the name of the array structure table
                        String arrayTablePath = dataType + "_" + parameterName;

                        // Step through the remaining dimensions
                        for (int subIndex = currentIndices.length - 1; subIndex >= 0; subIndex--)
                        {
                            // Append the current array index reference(s)
                            arrayTablePath += "_" + String.valueOf(currentIndices[subIndex]);
                        }

                        // Store the structure reference in the list. The sequence container
                        // reference components must be in the order specified by
                        // ArrayContainerReference, separated by '/'s
                        ContainerRefEntryType containerRefEntry = factory.createContainerRefEntryType();
                        containerRefEntry.setContainerRef(arrayTablePath
                                                          + "/"
                                                          + cleanSystemPath(parameterName
                                                                            + ArrayVariable.formatArrayIndex(currentIndices))
                                                          + "/" + arraySize);

                        // Check if this is a telemetry list
                        if (entryList instanceof EntryListType)
                        {
                            // Store the container reference into the specified telemetry entry
                            // list
                            ((EntryListType) entryList).getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefEntry);
                        }
                        // Check if this is a command list container
                        else if (entryList instanceof CommandContainerEntryListType)
                        {
                            // Store the container reference into the specified command entry list
                            JAXBElement<ContainerRefEntryType> containerRefElem = factory.createCommandContainerEntryListTypeContainerRefEntry(containerRefEntry);
                            ((CommandContainerEntryListType) entryList).getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefElem);
                        }
                    }

                    // Go to the next higher level dimension (if any)
                    for (int subIndex = currentIndices.length - 2; subIndex >= 0; subIndex--)
                    {
                        // Increment the index
                        currentIndices[subIndex]++;

                        // Check if the maximum index of this dimension is reached
                        if (currentIndices[subIndex] == totalDims[subIndex])
                        {
                            // Check if this isn't the highest (last) dimension
                            if (subIndex != 0)
                            {
                                // Reset the index for this dimension
                                currentIndices[subIndex] = 0;
                            }
                            // This is the highest dimension
                            else
                            {
                                // All array members have been covered; stop searching, leaving the
                                // the highest dimension set to its maximum index value
                                break;
                            }
                        }
                        // The maximum index for this dimension hasn't been reached
                        else
                        {
                            // Exit the loop so that this array member can be processed
                            break;
                        }
                    }
                } while (currentIndices[0] < totalDims[0]);
                // Check if the highest dimension hasn't reached its maximum value. The loop
                // continues until a container reference for every array member is added to the
                // entry list
            }
            // Not an array parameter
            else
            {
                // Create a container reference to the child command
                ContainerRefEntryType containerRefEntry = factory.createContainerRefEntryType();
                containerRefEntry.setContainerRef(dataType
                                                  + "_" + parameterName
                                                  + "/" + parameterName);

                // Check if this is a telemetry list
                if (entryList instanceof EntryListType)
                {
                    // Store the container reference into the specified telemetry entry list
                    ((EntryListType) entryList).getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefEntry);
                }
                // Check if this is a command list container
                else if (entryList instanceof CommandContainerEntryListType)
                {
                    // Store the container reference into the specified command entry list
                    JAXBElement<ContainerRefEntryType> containerRefElem = factory.createCommandContainerEntryListTypeContainerRefEntry(containerRefEntry);
                    ((CommandContainerEntryListType) entryList).getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefElem);
                }
            }
        }
    }

    /**********************************************************************************************
     * Build a unit set from the supplied units string
     *
     * @param units
     *            parameter or command argument units; null to not specify
     *
     * @return Unit set for the supplied units string; an empty unit set if no units are supplied
     *********************************************************************************************/
    protected UnitSet createUnitSet(String units)
    {
        UnitSet unitSet = factory.createBaseDataTypeUnitSet();

        // Check if units are provided
        if (units != null && !units.isEmpty())
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
     * @param spaceSystem
     *            space system reference
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    protected EnumerationList createEnumerationList(SpaceSystemType spaceSystem, String enumeration)
    {
        EnumerationList enumList = factory.createEnumeratedDataTypeEnumerationList();

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
                                                              + spaceSystem.getName()
                                                              + "<b>'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }
}
