/**
 * CFS Command & Data Dictionary EDS handler.
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

import org.ccsds.schema.sois.seds.CCDDDataFieldSet;
import org.ccsds.schema.sois.seds.CCDDDataFieldSet.CCDDDataFieldData;
import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDDataTypeSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDDataTypeSet.CCDDDataTypeData;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDMacroSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDMacroSet.CCDDMacroData;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDReservedMessageIDSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDReservedMessageIDSet.CCDDReservedMessageIDData;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDTableColumnSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDTableColumnSet.CCDDTableColumnData;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDTableTypeDefinitionSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDTableTypeDefinitionSet.CCDDTableTypeDefinitionData;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDTableTypeDefinitionSet.CCDDTableTypeDefinitionData.CCDDTableTypeDefinitionColumn;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDVariablePathSet;
import org.ccsds.schema.sois.seds.DescriptionType.CCDDVariablePathSet.CCDDVariablePathData;
import org.ccsds.schema.sois.seds.DeviceType;
import org.ccsds.schema.sois.seds.EnumeratedDataType;
import org.ccsds.schema.sois.seds.EnumerationListType;
import org.ccsds.schema.sois.seds.IntegerDataEncodingType;
import org.ccsds.schema.sois.seds.IntegerDataType;
import org.ccsds.schema.sois.seds.IntegerEncodingType;
import org.ccsds.schema.sois.seds.InterfaceCommandType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationType;
import org.ccsds.schema.sois.seds.InterfaceParameterType;
import org.ccsds.schema.sois.seds.NamespaceType;
import org.ccsds.schema.sois.seds.ObjectFactory;
import org.ccsds.schema.sois.seds.RootDataType;
import org.ccsds.schema.sois.seds.SemanticsType;
import org.ccsds.schema.sois.seds.Unit;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClasses.ArrayListCaseInsensitive;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableTypeDefinition;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
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
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddFieldHandler fieldHandler;

    // GUI component instantiating this class
    private final Component parent;

    // List containing the imported table, table type, data type, and macro definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and EDS object references
    private JAXBElement<DataSheetType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;
    private DeviceType device;
    private DataSheetType dataSheet;

    // Name of the data field containing the system name
    private String systemFieldName;

    // Flag indicating that macros should be replaced by their corresponding values
    private boolean replaceMacros;

    // Conversion setup error flag
    private boolean errorFlag;

    // Lists to contain any references to table types, data types, macros, and variable paths in
    // the exported tables
    private List<String> referencedTableTypes;
    private List<String> referencedDataTypes;
    private List<String> referencedMacros;
    private List<String[]> referencedVariablePaths;

    /**********************************************************************************************
     * EDS data type tags
     *********************************************************************************************/
    private enum EDSTags
    {
        TABLE("Table"),
        TABLE_TYPE("Table Type Definition"),
        DATA_TYPE("Data Type Definition"),
        MACRO("Macro Definition"),
        RESERVED_MSG_ID("Reserved Message ID"),
        VARIABLE_PATH("Variable Path");

        private String tag;

        /******************************************************************************************
         * Additional EDS data type tags constructor
         *
         * @param tag
         *            text describing the data
         *****************************************************************************************/
        EDSTags(String tag)
        {
            this.tag = tag;
        }

        /******************************************************************************************
         * Get the data type tag
         *
         * @return Text describing the data
         *****************************************************************************************/
        protected String getTag()
        {
            return tag;
        }
    }

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
        rsvMsgIDHandler = ccddMain.getReservedMsgIDHandler();

        errorFlag = false;

        try
        {
            // Create the XML marshaller used to convert the CCDD project data into EDS XML format
            JAXBContext context = JAXBContext.newInstance("org.ccsds.schema.sois.seds");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   "http://www.ccsds.org/schema/sois/seds");
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
     * @param importAll
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
    public void importFromFile(File importFile, ImportType importType) throws CCDDException,
                                                                       IOException,
                                                                       Exception
    {
        try
        {
            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            // Get the project's data sheet
            dataSheet = (DataSheetType) jaxbElement.getValue();

            // Step through the EDS-formatted data and extract the telemetry and command
            // information
            unbuildDataSheets(importType, importFile.getAbsolutePath());
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
        catch (CCDDException ce)
        {
            // Re-throw the error so it can be handled by the caller
            throw new CCDDException(ce.getMessage());
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
     *            [0] name of the data field containing the system name
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
            // Convert the table data into EDS format
            convertTablesToEDS(tableNames,
                               replaceMacros,
                               includeReservedMsgIDs,
                               includeVariablePaths,
                               variableHandler,
                               separators,
                               extraInfo[0]);

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
     * @param system
     *            name of the data field containing the system name
     *********************************************************************************************/
    private void convertTablesToEDS(String[] tableNames,
                                    boolean replaceMacros,
                                    boolean includeReservedMsgIDs,
                                    boolean includeVariablePaths,
                                    CcddVariableSizeAndConversionHandler variableHandler,
                                    String[] separators,
                                    String system)
    {
        referencedTableTypes = new ArrayList<String>();
        referencedDataTypes = new ArrayList<String>();
        referencedMacros = new ArrayListCaseInsensitive();
        referencedVariablePaths = new ArrayList<String[]>();

        // Store the macro replacement flag and the system field name
        this.replaceMacros = replaceMacros;
        systemFieldName = system;

        // Create the project's data sheet and device
        dataSheet = factory.createDataSheetType();
        project = factory.createDataSheet(dataSheet);
        device = factory.createDeviceType();
        device.setName(dbControl.getDatabaseName());
        device.setShortDescription(dbControl.getDatabaseDescription(dbControl.getDatabaseName()));
        dataSheet.setDevice(device);

        // Add the project's name spaces, parameters, and commands
        buildNameSpaces(tableNames,
                        includeVariablePaths,
                        variableHandler,
                        separators);

        // Build a name space for the table types
        exportTableTypesNameSpace();

        // Build a name space for the primitive data types
        exportDataTypesNameSpace();

        // Check if the macro names are to be retained
        if (!replaceMacros)
        {
            // Create a name space and populate it with the macro definitions
            exportMacrosNameSpace();
        }

        // Check if the user elected to store the reserved message IDs
        if (includeReservedMsgIDs)
        {
            // Build a name space for the reserved message IDs
            exportReservedMsgIDNameSpace();
        }

        // Check if the user elected to store the variable paths
        if (includeVariablePaths)
        {
            // Build a name space for the variable paths (if any)
            exportVariablePathNameSpace(variableHandler, separators);
        }
    }

    /**********************************************************************************************
     * Step through the EDS-formatted data and extract the telemetry and command information
     *
     * @param importAll
     *            ImportType.IMPORT_ALL to import the table type, data type, and macro definitions,
     *            and the data from all the table definitions; ImportType.FIRST_DATA_ONLY to load
     *            only the data for the first table defined
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void unbuildDataSheets(ImportType importType,
                                   String importFileName) throws CCDDException
    {
        List<AssociatedColumns> commandArguments = null;
        tableDefinitions = new ArrayList<TableDefinition>();
        List<String[]> dataTypeDefns = new ArrayList<String[]>();
        List<String[]> macroDefns = new ArrayList<String[]>();
        List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

        // Flags indicating if importing should continue after an input error is detected
        boolean continueOnColumnError = false;
        boolean continueOnDataFieldError = false;

        // Get a list of defined name spaces
        List<NamespaceType> nameSpaces = dataSheet.getNamespace();

        // Check if a name space exists
        if (nameSpaces != null)
        {
            // Make two passes; the first to create the any table types, data types, macros, and
            // reserved IDs, and the second to create the table(s)
            for (int pass = 1; pass <= 2; pass++)
            {
                // Step through each name space
                for (NamespaceType nameSpace : nameSpaces)
                {
                    // Check if this is the table type definitions name space
                    if (pass == 1 && nameSpace.getName().equals(EDSTags.TABLE_TYPE.getTag()))
                    {
                        // Import the table type definitions
                        importTableTypeDefinitions(nameSpace, importFileName);
                    }
                    // Check if all definitions are to be loaded and this is the primitive data
                    // type definitions name space
                    else if (pass == 1
                             && importType == ImportType.IMPORT_ALL
                             && nameSpace.getName().equals(EDSTags.DATA_TYPE.getTag()))
                    {
                        // Import the data type definitions
                        importDataTypeDefinitions(nameSpace, importFileName);
                    }
                    // Check if all definitions are to be loaded and this is the macro definitions
                    // name space
                    else if (pass == 1
                             && importType == ImportType.IMPORT_ALL
                             && nameSpace.getName().equals(EDSTags.MACRO.getTag()))
                    {
                        // Import the macro definitions
                        importMacroDefinitions(nameSpace, importFileName);
                    }
                    // Check if all definitions are to be loaded and this is the reserved message
                    // ID definitions name space
                    else if (pass == 1
                             && importType == ImportType.IMPORT_ALL
                             && nameSpace.getName().equals(EDSTags.RESERVED_MSG_ID.getTag()))
                    {
                        // Import the reserved message ID definitions
                        importReservedMsgIDDefinitions(nameSpace, importFileName);
                    }
                    // Check if all definitions are to be loaded or that this is the first table,
                    // this is a table definition name space, an interface set exists, and that the
                    // name space name is in the correct format for a table (table identifier :
                    // table name< : system name>)
                    else if (pass == 2
                             && (importType == ImportType.IMPORT_ALL
                                 || tableDefinitions.size() == 0)
                             && nameSpace.getName().startsWith(EDSTags.TABLE.getTag() + ":")
                             && nameSpace.getDeclaredInterfaceSet() != null
                             && nameSpace.getName().matches("[^:]+?:[^:]+?(?::[^:]*)?$"))
                    {
                        int numColumns = 0;
                        int variableNameIndex = -1;
                        int dataTypeIndex = -1;
                        int descriptionIndex = -1;
                        int commandNameIndex = -1;
                        int cmdDescriptionIndex = -1;
                        TableDefinition tableDefn = null;

                        // Separate the name space name into the tag, table name, and (optional)
                        // system name
                        String[] nameParts = nameSpace.getName().split(":");

                        // Create a table definition for this table. The nameSpace name begins with
                        // the table identifier followed by the system and the table name,
                        // separated by colons
                        tableDefn = new TableDefinition(nameParts[1].trim(),
                                                        nameSpace.getShortDescription());

                        /**************************************************************************
                         * Overall table information processing
                         *************************************************************************/
                        // Check if the table type name exists
                        if (nameSpace.getCCDDTableType() != null)
                        {
                            tableDefn.setTypeName(nameSpace.getCCDDTableType());

                            // Get the table's type definition based on the type name
                            typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getTypeName());

                            // Check if the table type isn't recognized
                            if (typeDefn == null)
                            {
                                throw new CCDDException("unknown table type '"
                                                        + tableDefn.getTypeName()
                                                        + "'");
                            }

                            // Get the number of visible columns for this table type
                            numColumns = typeDefn.getColumnCountVisible();

                            // Check if this is a structure type table
                            if (typeDefn.isStructure())
                            {
                                // Get the structure column indices, if this is a structure type
                                variableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE));
                                dataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT));
                                descriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));
                            }
                            // Check if this is a command type table
                            else if (typeDefn.isCommand())
                            {
                                // Get the list containing command argument name, data type,
                                // enumeration, minimum, maximum, and other associated column
                                // indices for each argument grouping
                                commandArguments = typeDefn.getAssociatedCommandArgumentColumns(true);

                                // Get the command name column
                                commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME));

                                // Get the command description column. If the default command
                                // description column name isn't used then the first column
                                // containing 'description' is selected that doesn't refer to a
                                // command argument
                                cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));

                                // Check if the description column belongs to a command argument
                                if (commandArguments.size() != 0
                                    && cmdDescriptionIndex > commandArguments.get(0).getName())
                                {
                                    // Reset the command description index to indicate no
                                    // description exists
                                    cmdDescriptionIndex = -1;
                                }
                            }
                        }

                        // Check if the table has any data field assigned
                        if (nameSpace.getCCDDTableDataFieldSet() != null)
                        {
                            // Step through the table type data field definitions
                            for (CCDDDataFieldData dataFieldData : nameSpace.getCCDDTableDataFieldSet().getCCDDDataFieldData())
                            {
                                // Check if the expected number of inputs is present
                                if (dataFieldData.getName() != null
                                    && dataFieldData.getDescription() != null
                                    && dataFieldData.getSize() != null
                                    && dataFieldData.getApplicability() != null
                                    && dataFieldData.getValue() != null)
                                {
                                    // Add the data field definition, checking for (and if
                                    // possible, correcting) errors
                                    continueOnDataFieldError = addImportedDataFieldDefinition(continueOnDataFieldError,
                                                                                              tableDefn,
                                                                                              new String[] {CcddFieldHandler.getFieldTypeName(tableDefn.getName()),
                                                                                                            dataFieldData.getName(),
                                                                                                            dataFieldData.getDescription(),
                                                                                                            dataFieldData.getSize().toString(),
                                                                                                            dataFieldData.getInputType(),
                                                                                                            Boolean.toString(dataFieldData.isRequired()),
                                                                                                            dataFieldData.getApplicability(),
                                                                                                            dataFieldData.getValue()},
                                                                                              importFileName,
                                                                                              parent);
                                }
                                // The number of inputs is incorrect
                                else
                                {
                                    // Check if the error should be ignored or the import canceled
                                    continueOnDataFieldError = getErrorResponse(continueOnDataFieldError,
                                                                                "<html><b>Table '</b>"
                                                                                                          + tableDefn.getName()
                                                                                                          + "<b>' has missing data "
                                                                                                          + "field input(s) in import file '</b>"
                                                                                                          + importFileName
                                                                                                          + "<b>'; continue?",
                                                                                "Data Field Error",
                                                                                "Ignore this invalid data field",
                                                                                "Ignore this and any remaining invalid data fields",
                                                                                "Stop importing",
                                                                                parent);
                                }
                            }
                        }

                        /**************************************************************************
                         * Non-telemetry and non-command table processing
                         *************************************************************************/
                        // Check if column data exists
                        if (nameSpace.getCCDDTableColumnSet() != null)
                        {
                            // Step through each column's data
                            for (CCDDTableColumnData columnData : nameSpace.getCCDDTableColumnSet().getCCDDTableColumnData())
                            {
                                // Get the row number and column name, and use the column name to
                                // get the column index
                                int row = Integer.valueOf(columnData.getRowNumber().toString());
                                String columnName = columnData.getColumnName();
                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                // Check that the column exists in the table
                                if (column != -1)
                                {
                                    // Add one or more rows until the row is created containing
                                    // this column value
                                    while (row * numColumns >= tableDefn.getData().size())
                                    {
                                        // Create a row with empty columns and add the new row to
                                        // the table data
                                        String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                        Arrays.fill(newRow, null);
                                        tableDefn.addData(newRow);
                                    }

                                    // Replace the value for the specified column
                                    tableDefn.getData().set(row * numColumns + column,
                                                            columnData.getValue());
                                }
                                // The column doesn't exist
                                else
                                {
                                    // Check if the error should be ignored or the import canceled
                                    continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                             "<html><b>Table '</b>"
                                                                                                    + tableDefn.getName()
                                                                                                    + "<b>' column name '</b>"
                                                                                                    + columnName
                                                                                                    + "<b>' unrecognized in import file '</b>"
                                                                                                    + importFileName
                                                                                                    + "<b>'; continue?",
                                                                             "Column Error",
                                                                             "Ignore this invalid column name",
                                                                             "Ignore this and any remaining invalid column names",
                                                                             "Stop importing",
                                                                             parent);
                                }
                            }
                        }

                        // Step through the interfaces in order to locate the name space's
                        // parameter, command, and generic sets
                        for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                        {
                            /**********************************************************************
                             * Telemetry processing
                             *********************************************************************/
                            // Check if the interface contains a parameter set
                            if (intfcDecType.getParameterSet() != null
                                && !intfcDecType.getParameterSet().getParameter().isEmpty())
                            {
                                // Step through each parameter
                                for (InterfaceParameterType parmType : intfcDecType.getParameterSet().getParameter())
                                {
                                    // Create a new row of data in the table definition to contain
                                    // this structure's information. Initialize all columns to
                                    // blanks except for the variable name
                                    String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(newRow, null);
                                    newRow[variableNameIndex] = parmType.getName();

                                    // Check if a data type exists
                                    if (parmType.getType() != null
                                        && !parmType.getType().isEmpty())
                                    {
                                        // Store the data type for this variable
                                        newRow[dataTypeIndex] = parmType.getType().replaceFirst("^[^/]*/",
                                                                                                "");
                                    }

                                    // Check if the description column exists in the table type
                                    // definition and that a description exists
                                    if (descriptionIndex != -1
                                        && parmType.getShortDescription() != null)
                                    {
                                        // Store the description for this variable
                                        newRow[descriptionIndex] = parmType.getShortDescription();
                                    }

                                    // Check if column data exists for this row
                                    if (parmType.getCCDDTableColumnSet() != null)
                                    {
                                        // Step through each column's data
                                        for (CCDDTableColumnData columnData : parmType.getCCDDTableColumnSet().getCCDDTableColumnData())
                                        {
                                            // Get the column name and use it to get the column
                                            // index
                                            String columnName = columnData.getColumnName();
                                            int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                            // Check that the column exists in the table
                                            if (column != -1)
                                            {
                                                // Store the column's value in the row data
                                                newRow[column] = columnData.getValue();
                                            }
                                            // The column doesn't exist
                                            else
                                            {
                                                // Check if the error should be ignored or the
                                                // import canceled
                                                continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                                         "<html><b>Table '</b>"
                                                                                                                + tableDefn.getName()
                                                                                                                + "<b>' column name '</b>"
                                                                                                                + columnName
                                                                                                                + "<b>' unrecognized in import file '</b>"
                                                                                                                + importFileName
                                                                                                                + "<b>'; continue?",
                                                                                         "Column Error",
                                                                                         "Ignore this invalid column name",
                                                                                         "Ignore this and any remaining invalid column names",
                                                                                         "Stop importing",
                                                                                         parent);
                                            }
                                        }
                                    }

                                    // Add the new row to the table definition
                                    tableDefn.addData(newRow);
                                }
                            }

                            /**********************************************************************
                             * Command processing
                             *********************************************************************/
                            // Check if the interface contains a command set
                            if (intfcDecType.getCommandSet() != null
                                && !intfcDecType.getCommandSet().getCommand().isEmpty())
                            {
                                // Step through each command
                                for (InterfaceCommandType cmdType : intfcDecType.getCommandSet().getCommand())
                                {
                                    // Create a new row of data in the table definition to contain
                                    // this command's information. Initialize all columns to blanks
                                    // except for the command name
                                    String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                    Arrays.fill(newRow, null);
                                    newRow[commandNameIndex] = cmdType.getName();

                                    // Check if the command description is present and the
                                    // description column exists in the table type definition
                                    if (cmdType.getShortDescription() != null
                                        && cmdDescriptionIndex != -1)
                                    {
                                        // Store the command description in the row's description
                                        // column
                                        newRow[cmdDescriptionIndex] = cmdType.getShortDescription();
                                    }

                                    // Check if any overall columns are defined for this row
                                    if (cmdType.getCCDDTableColumnSet() != null)
                                    {
                                        // Step through each column's data
                                        for (CCDDTableColumnData columnData : cmdType.getCCDDTableColumnSet().getCCDDTableColumnData())
                                        {
                                            // Get the column name
                                            String columnName = columnData.getColumnName();

                                            // Get the column index for the column described in the
                                            // column data
                                            int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                            // Check if the column exists in the table type
                                            // definition
                                            if (column != -1)
                                            {
                                                // Store the column's value in the row data
                                                newRow[column] = columnData.getValue();
                                            }
                                            // The column doesn't exist
                                            else
                                            {
                                                // Check if the error should be ignored or the
                                                // import canceled
                                                continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                                         "<html><b>Table '</b>"
                                                                                                                + tableDefn.getName()
                                                                                                                + "<b>' column name '</b>"
                                                                                                                + columnName
                                                                                                                + "<b>' unrecognized in import file '</b>"
                                                                                                                + importFileName
                                                                                                                + "<b>'; continue?",
                                                                                         "Column Error",
                                                                                         "Ignore this invalid column name",
                                                                                         "Ignore this and any remaining invalid column names",
                                                                                         "Stop importing",
                                                                                         parent);
                                            }
                                        }
                                    }

                                    int cmdArgIndex = 0;

                                    // Step through each of the command's arguments
                                    for (CommandArgumentType argList : cmdType.getArgument())
                                    {
                                        // Check if the command argument index is within the range
                                        // dictated by the table type definition
                                        if (cmdArgIndex < commandArguments.size())
                                        {
                                            String dataType = argList.getType();
                                            String description = argList.getShortDescription();
                                            String enumeration = null;
                                            String units = null;

                                            // Check if a data set exists and the command name
                                            // column is present in the data type
                                            if (nameSpace.getDataTypeSet() != null && commandNameIndex != -1)
                                            {
                                                // Get the reference to the data type sets
                                                List<RootDataType> dataTypes = nameSpace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType();

                                                // Step through each data type set
                                                for (RootDataType rDataType : dataTypes)
                                                {
                                                    // Check if this is the same command argument
                                                    // referenced in the root data (by matching the
                                                    // command and argument names between the two)
                                                    if (cmdType.getName().equals(rDataType.getShortDescription())
                                                        && argList.getName().equals(rDataType.getName()))
                                                    {
                                                        // Check if this is an enumerated data type
                                                        // set
                                                        if (rDataType instanceof EnumeratedDataType)
                                                        {
                                                            EnumeratedDataType eDataType = (EnumeratedDataType) rDataType;

                                                            // Get the list of enumerated values
                                                            // and associated labels
                                                            EnumerationListType enumList = eDataType.getEnumerationList();

                                                            // Check if any enumerations exist
                                                            if (enumList != null)
                                                            {
                                                                // Step through each enumeration
                                                                for (ValueEnumerationType enumType : enumList.getEnumeration())
                                                                {
                                                                    // Check if this is the first
                                                                    // value
                                                                    if (enumeration == null)
                                                                    {
                                                                        enumeration = "";
                                                                    }
                                                                    // Not the first value
                                                                    else
                                                                    {
                                                                        enumeration += ", ";
                                                                    }

                                                                    // Build the enumeration
                                                                    enumeration += enumType.getValue()
                                                                                   + " | "
                                                                                   + enumType.getLabel();
                                                                }
                                                            }
                                                        }

                                                        // Check if units exist
                                                        if (rDataType.getSemantics() != null
                                                            && rDataType.getSemantics().getUnit() != null)
                                                        {
                                                            // Store the units
                                                            units = rDataType.getSemantics().getUnit().value();
                                                        }

                                                        break;
                                                    }
                                                }
                                            }

                                            // Get the command argument reference
                                            AssociatedColumns acmdArg = commandArguments.get(cmdArgIndex);

                                            // Check if the command argument name is present
                                            if (acmdArg.getName() != -1
                                                && !argList.getName().isEmpty())
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

                                            // Check if the command argument enumeration is present
                                            if (acmdArg.getEnumeration() != -1
                                                && enumeration != null)
                                            {
                                                // Store the command argument enumeration
                                                newRow[acmdArg.getEnumeration()] = enumeration;
                                            }

                                            // Check if the command argument description is present
                                            if (acmdArg.getDescription() != -1
                                                && description != null)
                                            {
                                                // Store the command argument description
                                                newRow[acmdArg.getDescription()] = description;
                                            }

                                            // Check if the command argument units is present
                                            if (acmdArg.getUnits() != -1 && units != null)
                                            {
                                                // Store the command argument units
                                                newRow[acmdArg.getUnits()] = units;
                                            }

                                            // Increment the argument index
                                            cmdArgIndex++;
                                        }

                                        // Check if any argument columns are defined for this row
                                        if (argList.getCCDDTableColumnSet() != null)
                                        {
                                            // Step through each column's data
                                            for (CCDDTableColumnData columnData : argList.getCCDDTableColumnSet().getCCDDTableColumnData())
                                            {
                                                // Get column name
                                                String columnName = columnData.getColumnName();

                                                // Get the column index for the column described in
                                                // the command data
                                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                                // Check if the column exists in the table type
                                                // definition
                                                if (column != -1)
                                                {
                                                    // Check if the cell hasn't already been
                                                    // populated by other command metadata
                                                    if (newRow[column] == null
                                                        || newRow[column].isEmpty())
                                                    {
                                                        // Update the table data at the row and
                                                        // column specified with the value from the
                                                        // column data
                                                        newRow[column] = columnData.getValue();
                                                    }
                                                }
                                                // The column doesn't exist
                                                else
                                                {
                                                    // Check if the error should be ignored or the
                                                    // import canceled
                                                    continueOnColumnError = getErrorResponse(continueOnColumnError,
                                                                                             "<html><b>Table '</b>"
                                                                                                                    + tableDefn.getName()
                                                                                                                    + "<b>' column name '</b>"
                                                                                                                    + columnName
                                                                                                                    + "<b>' unrecognized in import file '</b>"
                                                                                                                    + importFileName
                                                                                                                    + "<b>'; continue?",
                                                                                             "Column Error",
                                                                                             "Ignore this invalid column name",
                                                                                             "Ignore this and any remaining invalid column names",
                                                                                             "Stop importing",
                                                                                             parent);
                                                }
                                            }
                                        }
                                    }

                                    // Add the new row to the table definition
                                    tableDefn.addData(newRow);
                                }
                            }
                        }

                        // Add the table definition to the list
                        tableDefinitions.add(tableDefn);
                    }
                }
            }

            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                // Add the data type if it's new or match it to an existing one with the same name
                // if the type definitions are the same
                String badDefn = dataTypeHandler.updateDataTypes(dataTypeDefns);

                // Check if a data type isn't new and doesn't match an existing one with the same
                // name
                if (badDefn != null)
                {
                    throw new CCDDException("data type '"
                                            + badDefn
                                            + "' already exists and doesn't match the import definition");
                }

                // Add the macro if it's new or match it to an existing one with the same name if
                // the values are the same
                badDefn = macroHandler.updateMacros(macroDefns);

                // Check if a macro isn't new and doesn't match an existing one with the same name
                if (badDefn != null)
                {
                    throw new CCDDException("macro '"
                                            + badDefn
                                            + "' already exists and doesn't match the import definition");
                }

                // Add the reserved message ID definition if it's new
                rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);
            }
        }

    }

    /**********************************************************************************************
     * Extract the table type definitions, if present, from the imported root system
     *
     * @param nameSpace
     *            top-level name space
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If an input error is detected
     *********************************************************************************************/
    private void importTableTypeDefinitions(NamespaceType nameSpace,
                                            String importFileName) throws CCDDException
    {
        // Get the table type definitions
        CCDDTableTypeDefinitionSet tableTypeSet = nameSpace.getCCDDTableTypeDefinitionSet();

        // Check if a table type exists
        if (tableTypeSet != null)
        {
            List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>();

            // Flags indicating if importing should continue after an input error is detected
            boolean continueOnTableTypeError = false;
            boolean continueOnDataFieldError = false;

            // Step through the table type data
            for (CCDDTableTypeDefinitionData tableTypeData : tableTypeSet.getCCDDTableTypeDefinitionData())
            {
                // Create the table type definition, supplying the name and description
                TableTypeDefinition tableTypeDefn = new TableTypeDefinition(tableTypeData.getTableType(),
                                                                            tableTypeData.getDescription());
                tableTypeDefns.add(tableTypeDefn);

                // Step through each column defined in the table type
                for (CCDDTableTypeDefinitionColumn tableTypeColumn : tableTypeData.getCCDDTableTypeDefinitionColumn())
                {
                    // Check if the expected inputs are present
                    if (tableTypeColumn.getColumnNumber() != null
                        && tableTypeColumn.getNameVisible() != null
                        && tableTypeColumn.getDescription() != null
                        && tableTypeColumn.getInputType() != null)
                    {
                        // Add the table type column definition, checking for (and if possible,
                        // correcting) errors
                        continueOnTableTypeError = addImportedTableTypeDefinition(continueOnTableTypeError,
                                                                                  tableTypeDefn,
                                                                                  new String[] {tableTypeColumn.getColumnNumber().toString(),
                                                                                                tableTypeColumn.getNameVisible(),
                                                                                                tableTypeColumn.getDescription(),
                                                                                                tableTypeColumn.getInputType(),
                                                                                                Boolean.toString(tableTypeColumn.isRowValueUnique()),
                                                                                                Boolean.toString(tableTypeColumn.isRequired()),
                                                                                                Boolean.toString(tableTypeColumn.isStructureAllowed()),
                                                                                                Boolean.toString(tableTypeColumn.isPointerAllowed())},
                                                                                  importFileName,
                                                                                  parent);
                    }
                    // An expected input is missing
                    else
                    {
                        // Check if the error should be ignored or the import canceled
                        continueOnTableTypeError = getErrorResponse(continueOnTableTypeError,
                                                                    "<html><b>Table type '"
                                                                                              + tableTypeData.getTableType()
                                                                                              + "' definition has missing input(s) in import file '</b>"
                                                                                              + importFileName
                                                                                              + "<b>'; continue?",
                                                                    "Table Type Error",
                                                                    "Ignore this table type",
                                                                    "Ignore this and any remaining invalid table types",
                                                                    "Stop importing",
                                                                    parent);
                    }
                }

                // Check if this is a table type data field definition
                if (tableTypeData.getCCDDTableTypeDefinitionDataFieldSet() != null)
                {
                    // Step through the table type data field definitions
                    for (CCDDDataFieldData dataFieldData : tableTypeData.getCCDDTableTypeDefinitionDataFieldSet().getCCDDDataFieldData())
                    {
                        // Check if the expected number of inputs is present
                        if (dataFieldData.getName() != null
                            && dataFieldData.getDescription() != null
                            && dataFieldData.getSize() != null
                            && dataFieldData.getApplicability() != null
                            && dataFieldData.getValue() != null)
                        {
                            // Add the data field definition, checking for (and if possible,
                            // correcting) errors
                            continueOnDataFieldError = addImportedDataFieldDefinition(continueOnDataFieldError,
                                                                                      tableTypeDefn,
                                                                                      new String[] {CcddFieldHandler.getFieldTypeName(tableTypeData.getTableType()),
                                                                                                    dataFieldData.getName(),
                                                                                                    dataFieldData.getDescription(),
                                                                                                    dataFieldData.getSize().toString(),
                                                                                                    dataFieldData.getInputType(),
                                                                                                    Boolean.toString(dataFieldData.isRequired()),
                                                                                                    dataFieldData.getApplicability(),
                                                                                                    dataFieldData.getValue()},
                                                                                      importFileName,
                                                                                      parent);
                        }
                        // The number of inputs is incorrect
                        else
                        {
                            // Check if the error should be ignored or the import canceled
                            continueOnDataFieldError = getErrorResponse(continueOnDataFieldError,
                                                                        "<html><b>Table type '</b>"
                                                                                                  + tableTypeData.getTableType()
                                                                                                  + "<b>' has missing data field "
                                                                                                  + "input(s) in import file '</b>"
                                                                                                  + importFileName
                                                                                                  + "<b>'; continue?",
                                                                        "Data Field Error",
                                                                        "Ignore this invalid data field",
                                                                        "Ignore this and any remaining invalid data fields",
                                                                        "Stop importing",
                                                                        parent);
                        }
                    }
                }
            }

            // Add the table type if it's new or match it to an existing one with the same name if
            // the type definitions are the same
            String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefns, fieldHandler);

            // Check if a table type isn't new and doesn't match an existing one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("Imported table type '"
                                        + badDefn
                                        + "' doesn't match the existing definition");
            }
        }
    }

    /**********************************************************************************************
     * Extract the data type definitions, if present, from the imported root system
     *
     * @param nameSpace
     *            top-level name space
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If the number of data type inputs is incorrect and the user cancels the import,
     *             or the data type doesn't match an existing one of the same name
     *********************************************************************************************/
    private void importDataTypeDefinitions(NamespaceType nameSpace,
                                           String importFileName) throws CCDDException
    {
        List<String[]> dataTypeDefns = new ArrayList<String[]>();

        // Get the data type definitions
        CCDDDataTypeSet dataTypeSet = nameSpace.getCCDDDataTypeSet();

        // Check if a data type definition exists
        if (dataTypeSet != null)
        {
            // Flag indicating if importing should continue after an input error is detected
            boolean continueOnDataTypeError = false;

            // Step through the data type definitions
            for (CCDDDataTypeData dataTypeData : dataTypeSet.getCCDDDataTypeData())
            {
                // Check if the expected inputs are present
                if (dataTypeData.getUserName() != null
                    && dataTypeData.getCName() != null
                    && dataTypeData.getSizeInBytes() != null
                    && dataTypeData.getBaseType() != null)
                {
                    // Add the data type definition to the list (add a blank for the OID
                    // column)
                    dataTypeDefns.add(new String[] {dataTypeData.getUserName(),
                                                    dataTypeData.getCName(),
                                                    dataTypeData.getSizeInBytes().toString(),
                                                    dataTypeData.getBaseType(),
                                                    ""});
                }
                // An expected input is missing
                else
                {
                    // Check if the error should be ignored or the import canceled
                    continueOnDataTypeError = getErrorResponse(continueOnDataTypeError,
                                                               "<html><b>Missing data type definition "
                                                                                        + "input(s) in import file '</b>"
                                                                                        + importFileName
                                                                                        + "<b>'; continue?",
                                                               "Data Type Error",
                                                               "Ignore this data type",
                                                               "Ignore this and any remaining invalid data types",
                                                               "Stop importing",
                                                               parent);
                }
            }

            // Add the data type if it's new or match it to an existing one with the same name if
            // the type definitions are the same
            String badDefn = dataTypeHandler.updateDataTypes(dataTypeDefns);

            // Check if a data type isn't new and doesn't match an existing one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("data type '"
                                        + badDefn
                                        + "' already exists and doesn't match the import definition");
            }
        }
    }

    /**********************************************************************************************
     * Extract the macro definitions, if present, from the imported root system
     *
     * @param nameSpace
     *            top-level name space
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If the number of macro inputs is incorrect and the user cancels the import, or
     *             the macro doesn't match an existing one of the same name
     *********************************************************************************************/
    private void importMacroDefinitions(NamespaceType nameSpace,
                                        String importFileName) throws CCDDException
    {
        List<String[]> macroDefns = new ArrayList<String[]>();

        // Get the macro definitions
        CCDDMacroSet macroSet = nameSpace.getCCDDMacroSet();

        // Check if a macro definition exists
        if (macroSet != null)
        {
            // Flag indicating if importing should continue after an input error is detected
            boolean continueOnMacroError = false;

            // Step through the extra data
            for (CCDDMacroData macroData : macroSet.getCCDDMacroData())
            {
                // Check if the expected inputs are present
                if (macroData.getName() != null && macroData.getMacroValue() != null)
                {
                    // Add the macro definition to the list (add a blank for the OID column)
                    macroDefns.add(new String[] {macroData.getName(), macroData.getMacroValue(), ""});
                }
                // An expected input is missing
                else
                {
                    // Check if the error should be ignored or the import canceled
                    continueOnMacroError = getErrorResponse(continueOnMacroError,
                                                            "<html><b>Missing macro definition "
                                                                                  + "input(s) in import file '</b>"
                                                                                  + importFileName
                                                                                  + "<b>'; continue?",
                                                            "Macro Error",
                                                            "Ignore this macro",
                                                            "Ignore this and any remaining invalid macros",
                                                            "Stop importing",
                                                            parent);
                }
            }

            // Add the macro if it's new or match it to an existing one with the same name if the
            // values are the same
            String badDefn = macroHandler.updateMacros(macroDefns);

            // Check if a macro isn't new and doesn't match an existing one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("macro '"
                                        + badDefn
                                        + "' already exists and doesn't match the import definition");
            }
        }
    }

    /**********************************************************************************************
     * Extract the reserved message ID definitions, if present, from the imported root system
     *
     * @param nameSpace
     *            top-level space system
     *
     * @param importFileName
     *            import file name
     *
     * @throws CCDDException
     *             If the number of reserved message inputs is incorrect and the user cancels the
     *             import
     *********************************************************************************************/
    private void importReservedMsgIDDefinitions(NamespaceType nameSpace,
                                                String importFileName) throws CCDDException
    {
        List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

        // Get the reserved message ID definitions
        CCDDReservedMessageIDSet msgIDSet = nameSpace.getCCDDReservedMessageIDSet();

        // Check if a reserved message ID exists
        if (msgIDSet != null)
        {
            // Flag indicating if importing should continue after an input error is detected
            boolean continueOnReservedMsgIDError = false;

            // Step through the extra data
            for (CCDDReservedMessageIDData msgIDData : msgIDSet.getCCDDReservedMessageIDData())
            {
                // Check if the expected inputs are present
                if (msgIDData.getMessageID() != null && msgIDData.getDescription() != null)
                {
                    // Add the reserved message ID definition to the list (add a blank for the OID
                    // column)
                    reservedMsgIDDefns.add(new String[] {msgIDData.getMessageID(),
                                                         msgIDData.getDescription(),
                                                         ""});
                }
                // An expected input is missing
                else
                {
                    // Check if the error should be ignored or the import canceled
                    continueOnReservedMsgIDError = getErrorResponse(continueOnReservedMsgIDError,
                                                                    "<html><b>Missing reserved message ID "
                                                                                                  + "definition input(s) in import file '</b>"
                                                                                                  + importFileName
                                                                                                  + "<b>'; continue?",
                                                                    "Reserved Message ID Error",
                                                                    "Ignore this reserved message ID",
                                                                    "Ignore this and any remaining invalid reserved message IDs",
                                                                    "Stop importing",
                                                                    parent);
                }
            }
        }

        // Add the reserved message ID definition if it's new
        rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);
    }

    /**********************************************************************************************
     * Create a name space to contain the referenced table type definitions
     *********************************************************************************************/
    private void exportTableTypesNameSpace()
    {
        CCDDTableTypeDefinitionSet tableTypeSet = new CCDDTableTypeDefinitionSet();

        // Step through each referenced table type
        for (String refTableType : referencedTableTypes)
        {
            // Get the table type definition
            TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(refTableType);

            // Check if the table type exists
            if (tableTypeDefn != null)
            {
                CCDDTableTypeDefinitionData tableTypeData = new CCDDTableTypeDefinitionData();
                tableTypeData.setTableType(tableTypeDefn.getName());
                tableTypeData.setDescription(tableTypeDefn.getDescription());

                // Step through each column definition in the table type, skipping the primary key
                // and row index columns
                for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                {
                    // Store the table type column definition
                    CCDDTableTypeDefinitionColumn tableTypeColumn = new CCDDTableTypeDefinitionColumn();
                    tableTypeColumn.setColumnNumber(BigInteger.valueOf(column - NUM_HIDDEN_COLUMNS));
                    tableTypeColumn.setNameVisible(tableTypeDefn.getColumnNamesUser()[column]);
                    tableTypeColumn.setNameDatabase(tableTypeDefn.getColumnNamesDatabase()[column]);
                    tableTypeColumn.setDescription(tableTypeDefn.getColumnToolTips()[column]);
                    tableTypeColumn.setInputType(tableTypeDefn.getInputTypes()[column].getInputName());
                    tableTypeColumn.setRowValueUnique(tableTypeDefn.isRowValueUnique()[column]);
                    tableTypeColumn.setRequired(tableTypeDefn.isRequired()[column]);
                    tableTypeColumn.setStructureAllowed(tableTypeDefn.isStructureAllowed()[column]);
                    tableTypeColumn.setPointerAllowed(tableTypeDefn.isPointerAllowed()[column]);
                    tableTypeData.getCCDDTableTypeDefinitionColumn().add(tableTypeColumn);
                }

                // Build the data field information for this table type and export it
                fieldHandler.buildFieldInformation(CcddFieldHandler.getFieldTypeName(tableTypeDefn.getName()));
                tableTypeData.setCCDDTableTypeDefinitionDataFieldSet(getDataFields(fieldHandler.getFieldInformation()));

                // Store the table type definition
                tableTypeSet.getCCDDTableTypeDefinitionData().add(tableTypeData);
            }
        }

        // Check if a table type is referenced
        if (!tableTypeSet.getCCDDTableTypeDefinitionData().isEmpty())
        {
            // Create a name space to contain the table type definitions
            NamespaceType tableTypesNameSpace = addNameSpace(null,
                                                             EDSTags.TABLE_TYPE.getTag(),
                                                             "Table type definitions",
                                                             null,
                                                             null,
                                                             null);

            // Store the table type definition(s)
            tableTypesNameSpace.setCCDDTableTypeDefinitionSet(tableTypeSet);
        }
    }

    /**********************************************************************************************
     * Create a name space to contain the referenced primitive data types
     *********************************************************************************************/
    private void exportDataTypesNameSpace()
    {
        // Check if any data types are referenced
        if (!referencedDataTypes.isEmpty())
        {
            CCDDDataTypeSet dataTypeSet = new CCDDDataTypeSet();

            // Create a name space to contain the primitive data types
            NamespaceType dataTypeNameSpace = addNameSpace(null,
                                                           EDSTags.DATA_TYPE.getTag(),
                                                           "Data type definitions",
                                                           null,
                                                           null,
                                                           null);

            // Step through each referenced primitive data type
            for (String refDataType : referencedDataTypes)
            {
                // Get the data type information
                String[] dataType = dataTypeHandler.getDataTypeInfo(refDataType);

                // Check if the data type exists
                if (dataType != null)
                {
                    RootDataType type = null;
                    String dataTypeName = CcddDataTypeHandler.getDataTypeName(dataType);

                    // Check if the primitive is a signed or unsigned integer
                    if (dataTypeHandler.isInteger(dataTypeName))
                    {
                        type = factory.createIntegerDataType();
                        IntegerDataEncodingType encodingType = factory.createIntegerDataEncodingType();

                        // Check if the primitive is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(dataTypeName))
                        {
                            // Set the encoding as unsigned
                            encodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                        }
                        // Primitive is a signed integer
                        else
                        {
                            encodingType.setEncoding(IntegerEncodingType.SIGN_MAGNITUDE);
                        }

                        encodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataTypeName)));
                        ((IntegerDataType) type).setIntegerDataEncoding(encodingType);
                    }
                    // Check if the primitive is a float or double
                    else if (dataTypeHandler.isFloat(dataTypeName))
                    {
                        type = factory.createFloatDataType();
                    }
                    // Check if the primitive is a character or string
                    else if (dataTypeHandler.isCharacter(dataTypeName))
                    {
                        type = factory.createStringDataType();
                    }

                    // Set the data type name
                    type.setName(CcddDataTypeHandler.getDataTypeName(dataType));

                    // Store the data type definition
                    CCDDDataTypeData dataTypeData = new CCDDDataTypeData();
                    dataTypeData.setUserName(dataType[DataTypesColumn.USER_NAME.ordinal()]);
                    dataTypeData.setCName(dataType[DataTypesColumn.C_NAME.ordinal()]);
                    dataTypeData.setSizeInBytes(BigInteger.valueOf(Integer.valueOf(dataType[DataTypesColumn.SIZE.ordinal()])));
                    dataTypeData.setBaseType(dataType[DataTypesColumn.BASE_TYPE.ordinal()]);
                    dataTypeSet.getCCDDDataTypeData().add(dataTypeData);
                }
            }

            // Check if a data type is defined
            if (!dataTypeSet.getCCDDDataTypeData().isEmpty())
            {
                // Store the data type definition(s)
                dataTypeNameSpace.setCCDDDataTypeSet(dataTypeSet);
            }
        }
    }

    /**********************************************************************************************
     * Create a name space to contain the referenced macro definitions
     *********************************************************************************************/
    private void exportMacrosNameSpace()
    {
        CCDDMacroSet macroSet = new CCDDMacroSet();

        // Step through each referenced macro
        for (String refMacro : referencedMacros)
        {
            // Get the macro definition
            String macroValue = macroHandler.getMacroValue(refMacro);

            // Check if the macro exists
            if (macroValue != null)
            {
                // Store the macro definition
                CCDDMacroData macroData = new CCDDMacroData();
                macroData.setName(refMacro);
                macroData.setMacroValue(macroValue);
                macroSet.getCCDDMacroData().add(macroData);
            }
        }

        // Check if a macro is referenced
        if (!macroSet.getCCDDMacroData().isEmpty())
        {
            // Create a name space to contain the macro definitions
            NamespaceType macroNameSpace = addNameSpace(null,
                                                        EDSTags.MACRO.getTag(),
                                                        "Macro definitions",
                                                        null,
                                                        null,
                                                        null);

            // Store the macro definition(s)
            macroNameSpace.setCCDDMacroSet(macroSet);
        }
    }

    /**********************************************************************************************
     * Create a name space to contain all reserved message IDs
     *********************************************************************************************/
    private void exportReservedMsgIDNameSpace()
    {
        CCDDReservedMessageIDSet msgIDSet = new CCDDReservedMessageIDSet();

        // Step through each reserved message ID definition
        for (String[] reservedMsgIDDefn : rsvMsgIDHandler.getReservedMsgIDData())
        {
            // Store the reserved message ID definition
            CCDDReservedMessageIDData msgIDData = new CCDDReservedMessageIDData();
            msgIDData.setMessageID(reservedMsgIDDefn[ReservedMsgIDsColumn.MSG_ID.ordinal()]);
            msgIDData.setDescription(reservedMsgIDDefn[ReservedMsgIDsColumn.DESCRIPTION.ordinal()]);
            msgIDSet.getCCDDReservedMessageIDData().add(msgIDData);
        }

        // Check if a reserved message ID exists
        if (!msgIDSet.getCCDDReservedMessageIDData().isEmpty())
        {
            // Create a name space to contain the reserved message ID definitions
            NamespaceType reservedMsgIDNameSpace = addNameSpace(null,
                                                                EDSTags.RESERVED_MSG_ID.getTag(),
                                                                "Reserved message ID definitions",
                                                                null,
                                                                null,
                                                                null);

            // Store the reserved message ID(s)
            reservedMsgIDNameSpace.setCCDDReservedMessageIDSet(msgIDSet);
        }
    }

    /**********************************************************************************************
     * Create a name space to contain all variable paths
     *
     * @param variableHandler
     *            variable handler class reference; null if includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator character(s), show/hide data
     *            types flag ('true' or 'false'), and data type/variable name separator
     *            character(s); null if includeVariablePaths is false
     *********************************************************************************************/
    private void exportVariablePathNameSpace(CcddVariableSizeAndConversionHandler variableHandler,
                                             String[] separators)
    {
        CCDDVariablePathSet variablePathSet = new CCDDVariablePathSet();

        // Step through each variable path
        for (String[] variablePath : referencedVariablePaths)
        {
            // Store the variable path
            CCDDVariablePathData variablePathData = new CCDDVariablePathData();
            variablePathData.setPath(variablePath[0]);
            variablePathData.setPathConverted(variablePath[1]);
            variablePathSet.getCCDDVariablePathData().add(variablePathData);
        }

        // Check if a variable path exists
        if (!variablePathSet.getCCDDVariablePathData().isEmpty())
        {
            // Create a name space to contain the variable paths
            NamespaceType variablePathNameSpace = addNameSpace(null,
                                                               EDSTags.VARIABLE_PATH.getTag(),
                                                               "Variable paths",
                                                               null,
                                                               null,
                                                               null);

            // Store the variable path(s)
            variablePathNameSpace.setCCDDVariablePathSet(variablePathSet);
        }
    }

    /**********************************************************************************************
     * Get the table data field definition set for export
     *
     * @param fieldInformation
     *            list containing data field information
     *
     * @return Table data field definition set
     *********************************************************************************************/
    private CCDDDataFieldSet getDataFields(List<FieldInformation> fieldInformation)
    {
        CCDDDataFieldSet dataFieldSet = new CCDDDataFieldSet();

        // Step through the command table's data field information
        for (FieldInformation field : fieldInformation)
        {
            // Store the data field definition
            CCDDDataFieldData dataField = new CCDDDataFieldData();
            dataField.setName(field.getFieldName());
            dataField.setDescription(field.getDescription());
            dataField.setSize(BigInteger.valueOf(field.getSize()));
            dataField.setInputType(field.getInputType().getInputName());
            dataField.setRequired(field.isRequired());
            dataField.setApplicability(field.getApplicabilityType().getApplicabilityName());
            dataField.setValue(field.getValue());
            dataFieldSet.getCCDDDataFieldData().add(dataField);
        }

        return dataFieldSet;
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
    private void buildNameSpaces(String[] tableNames,
                                 boolean includeVariablePaths,
                                 CcddVariableSizeAndConversionHandler variableHandler,
                                 String[] separators)
    {
        // Step through each table name
        for (String tableName : tableNames)
        {
            NamespaceType nameSpace;

            // Get the information from the database for the specified table
            TableInformation tableInfo = dbTable.loadTableData(tableName,
                                                               true,
                                                               true,
                                                               false,
                                                               true,
                                                               parent);

            // Get the table type and from the type get the type definition. The type definition
            // can be a global parameter since if the table represents a structure, then all of its
            // children are also structures, and if the table represents commands or other table
            // type then it is processed within this nest level
            typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());

            // Get the table's basic type - structure, command, or the original table type if not
            // structure or command table
            String tableType = typeDefn.isStructure()
                                                      ? TYPE_STRUCTURE
                                                      : typeDefn.isCommand()
                                                                             ? TYPE_COMMAND
                                                                             : tableInfo.getType();

            // Check if the table type is recognized and that the table's data successfully loaded
            if (tableType != null && !tableInfo.isErrorFlag())
            {
                // Check if this type hasn't already been referenced
                if (!referencedTableTypes.contains(tableInfo.getType()))
                {
                    // Add the table type to the reference list
                    referencedTableTypes.add(tableInfo.getType());
                }

                // Check if the flag is set that indicates macros should be replaced
                if (replaceMacros)
                {
                    // Replace all macro names with their corresponding values
                    tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));
                }
                // Macros are to be retained
                else
                {
                    // Step through each row of data in the table
                    for (String[] rowData : tableInfo.getData())
                    {
                        // Step through each column in the row
                        for (String columnData : rowData)
                        {
                            // Step through each macro referenced in the column
                            for (String macro : macroHandler.getReferencedMacros(columnData))
                            {
                                // Check if this macro asn't already been referenced
                                if (!referencedMacros.contains(macro))
                                {
                                    // Add the macro to the reference list
                                    referencedMacros.add(macro);
                                }
                            }
                        }
                    }
                }

                String systemName;

                // Get the table's system from the system name data field, if it exists
                FieldInformation fieldInfo = tableInfo.getFieldHandler().getFieldInformationByName(tableName,
                                                                                                   systemFieldName);

                // Check that the system data field exists and isn't empty
                if (fieldInfo != null && !fieldInfo.getValue().isEmpty())
                {
                    // Store the system name
                    systemName = fieldInfo.getValue();
                }
                // The field value doesn't exist
                else
                {
                    // Assign a default system name
                    systemName = "DefaultSystem";
                }

                // Check if this is a structure table
                if (tableType.equals(TYPE_STRUCTURE))
                {
                    // Get the default column indices
                    int varColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                    int typeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                    int sizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                    int bitColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                    List<Integer> enumColumn = typeDefn.getColumnIndicesByInputType(InputDataType.ENUMERATION);
                    int descColumn = typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION);
                    int unitsColumn = typeDefn.getColumnIndexByInputType(InputDataType.UNITS);

                    // Get the structure table description
                    String description = tableInfo.getDescription().isEmpty()
                                                                              ? null
                                                                              : tableInfo.getDescription();

                    // Add the structure to the telemetry data sheet
                    nameSpace = addNameSpace(systemName,
                                             tableName,
                                             description,
                                             tableInfo.getType(),
                                             null,
                                             getDataFields(tableInfo.getFieldHandler().getFieldInformation()));

                    // Step through each row in the table
                    for (int row = 0; row < tableInfo.getData().length; row++)
                    {
                        // Add the variable to the data sheet
                        addNameSpaceParameter(nameSpace,
                                              tableInfo,
                                              varColumn,
                                              typeColumn,
                                              sizeColumn,
                                              bitColumn,
                                              enumColumn,
                                              unitsColumn,
                                              descColumn,
                                              tableInfo.getData()[row][typeColumn],
                                              tableInfo.getData()[row][varColumn]);

                        // Check if variable paths are to be output
                        if (includeVariablePaths)
                        {
                            // Get the variable path
                            String variablePath = tableInfo.getTablePath()
                                                  + ","
                                                  + tableInfo.getData()[row][typeColumn]
                                                  + "."
                                                  + tableInfo.getData()[row][varColumn];

                            // Add the path, in both application and user-defined formats, to the
                            // list to be output
                            referencedVariablePaths.add(new String[] {variablePath,
                                                                      variableHandler.getFullVariableName(variablePath,
                                                                                                          separators[0],
                                                                                                          Boolean.parseBoolean(separators[1]),
                                                                                                          separators[2])});
                        }
                    }
                }
                // Not a structure table
                else
                {
                    // Get the structure table description
                    String description = tableInfo.getDescription().isEmpty()
                                                                              ? null
                                                                              : tableInfo.getDescription();

                    // Check if this is a command table
                    if (tableType.equals(TYPE_COMMAND))
                    {
                        // Create a name space if not already present
                        nameSpace = addNameSpace(systemName,
                                                 tableName,
                                                 description,
                                                 tableInfo.getType(),
                                                 null,
                                                 getDataFields(tableInfo.getFieldHandler().getFieldInformation()));

                        // Add the command(s) from this table to the data sheet
                        addNameSpaceCommands(nameSpace, tableInfo);
                    }
                    // Not a command (or structure) table; i.e., it's a user-defined table type
                    else
                    {
                        // Create storage for the column rows, names, and values
                        CCDDTableColumnSet tableColumnSet = new CCDDTableColumnSet();

                        // Store this table's data as column data for the current space system.
                        // Step through each row of the table
                        for (int row = 0; row < tableInfo.getData().length; row++)
                        {
                            // Step through each visible column in the row
                            for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData()[row].length; column++)
                            {
                                // Check that this column is visible and that the column value
                                // isn't blank
                                if (!tableInfo.getData()[row][column].isEmpty())
                                {
                                    // Store the row number, column name, and value
                                    CCDDTableColumnData columnData = new CCDDTableColumnData();
                                    columnData.setRowNumber(BigInteger.valueOf(row));
                                    columnData.setColumnName(typeDefn.getColumnNamesUser()[column]);
                                    columnData.setValue(tableInfo.getData()[row][column]);
                                    tableColumnSet.getCCDDTableColumnData().add(columnData);
                                }
                            }
                        }

                        // Create a name space if not already present
                        nameSpace = addNameSpace(systemName,
                                                 tableName,
                                                 description,
                                                 tableInfo.getType(),
                                                 tableColumnSet,
                                                 getDataFields(tableInfo.getFieldHandler().getFieldInformation()));
                    }
                }
            }
        }
    }

    /**********************************************************************************************
     * Create a new name space as a child within the specified name space. If the specified name
     * space is null then this is the root data sheet
     *
     * @param systemName
     *            system name; null or blank if no system (e.g., macro definitions)
     *
     * @param nameSpaceName
     *            name for the new name space
     *
     * @param shortDescription
     *            data sheet description
     *
     * @param tableType
     *            table's type name
     *
     * @param tableColumnSet
     *            list containing row number, column name, and value for each column
     *
     * @param dataFieldSet
     *            list containing the data field definitions
     *
     * @return Reference to the new name space
     *********************************************************************************************/
    private NamespaceType addNameSpace(String systemName,
                                       String nameSpaceName,
                                       String shortDescription,
                                       String tableType,
                                       CCDDTableColumnSet tableColumnSet,
                                       CCDDDataFieldSet dataFieldSet)
    {
        // Check if a system name is provided
        if (systemName != null && !systemName.isEmpty())
        {
            // Prepend the system name to the name space name to get the full name
            nameSpaceName = EDSTags.TABLE.getTag()
                            + ": "
                            + nameSpaceName
                            + " : "
                            + systemName;
        }

        // Search the existing name spaces for one with this name
        NamespaceType nameSpace = searchNameSpacesForName(nameSpaceName);

        // Check if the name space doesn't already exist
        if (nameSpace == null)
        {
            // Create the new name space and set the name attribute
            nameSpace = factory.createNamespaceType();

            // Set the name space name
            nameSpace.setName(nameSpaceName);

            // Check if a description is provided
            if (shortDescription != null)
            {
                // Set the description attribute
                nameSpace.setShortDescription(shortDescription);
            }

            // Check if a system name is provided (i.e., this is a table)
            if (systemName != null && !systemName.isEmpty())
            {
                // Create an interface set for the name space
                nameSpace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());

                // Check if the table's type name is supplied
                if (tableType != null)
                {
                    // Store the table type name
                    nameSpace.setCCDDTableType(tableType);
                }

                // Check if any table column data are supplied
                if (tableColumnSet != null && !tableColumnSet.getCCDDTableColumnData().isEmpty())
                {
                    // Store the table column data
                    nameSpace.setCCDDTableColumnSet(tableColumnSet);
                }

                // Check if any data field definitions are supplied
                if (dataFieldSet != null && !dataFieldSet.getCCDDDataFieldData().isEmpty())
                {
                    // Store the data field definitions
                    nameSpace.setCCDDTableDataFieldSet(dataFieldSet);
                }
            }

            // Add the new names space
            dataSheet.getNamespace().add(nameSpace);
        }

        return nameSpace;
    }

    /**********************************************************************************************
     * Search for the name space with the same name as the search name
     *
     * @param nameSpaceName
     *            name of the name space to search for within the name space hierarchy
     *
     * @return Reference to the name space with the same name as the search name; null if no name
     *         space name matches the search name
     *********************************************************************************************/
    private NamespaceType searchNameSpacesForName(String nameSpaceName)
    {
        NamespaceType foundNameSpace = null;

        for (NamespaceType nameSpace : dataSheet.getNamespace())
        {
            // Check if the current name space's name matches the search name
            if (nameSpace.getName().equals(nameSpaceName))
            {
                // Store the reference to the matching name space
                foundNameSpace = nameSpace;
                break;
            }
        }

        return foundNameSpace;
    }

    /**********************************************************************************************
     * Add a variable to the specified data sheet
     *
     * @param nameSpace
     *            parent data sheet for this node
     *
     * @param tableInfo
     *            TableInformation reference for the current node
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
     * @param enumColumns
     *            list containing the current table's enumeration column indices; empty list if no
     *            enumeration columns exist
     *
     * @param unitsColumn
     *            current table's units column index; -1 if none exists
     *
     * @param descColumn
     *            current table's description column index; -1 if none exists
     *
     * @param dataType
     *            parameter data type
     *
     * @param variableName
     *            variable name
     *********************************************************************************************/
    private void addNameSpaceParameter(NamespaceType nameSpace,
                                       TableInformation tableInfo,
                                       int varColumn,
                                       int typeColumn,
                                       int sizeColumn,
                                       int bitColumn,
                                       List<Integer> enumColumns,
                                       int unitsColumn,
                                       int descColumn,
                                       String dataType,
                                       String variableName)
    {
        // Set the system path to the table's path
        String systemPath = tableInfo.getTablePath();

        // Initialize the parameter attributes
        String bitLength = null;
        List<String> enumerations = null;
        String units = null;
        String description = null;
        CCDDTableColumnSet tableColumnSet = new CCDDTableColumnSet();

        // Separate the variable name and bit length (if present) and store the variable name
        String[] nameAndBit = variableName.split(":");
        variableName = nameAndBit[0];

        // Check if a bit length is present
        if (nameAndBit.length == 2)
        {
            // Store the bit length
            bitLength = nameAndBit[1];
        }

        // Get the index of the row in the table for this variable
        int row = typeDefn.getRowIndexByColumnValue(tableInfo.getData(), variableName, varColumn);

        // Check that a valid row index exists for this variable. Since the table tree is built
        // from the existing tables, a valid variable row index is always returned
        if (row != -1)
        {
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
            }

            // Step through each column in the row
            for (int column = NUM_HIDDEN_COLUMNS; column < tableInfo.getData()[row].length; column++)
            {
                // Check that this is not the variable name column, or the bit length column and
                // the variable has no bit length, and that a value exists in the column
                if ((column != varColumn
                     || (column == bitColumn && bitLength != null))
                    && !tableInfo.getData()[row][column].isEmpty())
                {
                    // Store the column name and value
                    CCDDTableColumnData columnData = new CCDDTableColumnData();
                    columnData.setColumnName(typeDefn.getColumnNamesUser()[column]);
                    columnData.setValue(tableInfo.getData()[row][column]);
                    tableColumnSet.getCCDDTableColumnData().add(columnData);

                    // Check if this is an enumeration column
                    if (enumColumns.contains(column))
                    {
                        // Check if the enumeration list doesn't exist
                        if (enumerations == null)
                        {
                            // Create a list to contain the enumeration(s)
                            enumerations = new ArrayList<String>();
                        }

                        // Get the enumeration text
                        enumerations.add(tableInfo.getData()[row][column]);
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
        }

        // Add the variable to the data sheet
        addParameter(nameSpace,
                     systemPath,
                     variableName,
                     dataType,
                     enumerations,
                     units,
                     description,
                     tableColumnSet);
    }

    /**********************************************************************************************
     * Add the command(s) from a table to the specified name space
     *
     * @param nameSpace
     *            name space for this node
     *
     * @param tableInfo
     *            TableInformation reference for the current node
     *********************************************************************************************/
    private void addNameSpaceCommands(NamespaceType nameSpace, TableInformation tableInfo)
    {
        // Get the list containing command argument name, data type, enumeration, minimum, maximum,
        // and other associated column indices for each argument grouping
        List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

        // Step through each row in the table
        for (String[] rowData : tableInfo.getData())
        {
            // Initialize the command attributes and argument number list
            String commandName = null;
            String commandDescription = null;
            List<CommandArgumentType> arguments = new ArrayList<CommandArgumentType>();
            CCDDTableColumnSet commandColumnSet = new CCDDTableColumnSet();

            // Create an array of flags to indicate if the column is a command argument that has
            // been processed
            boolean[] isCmdArg = new boolean[rowData.length];

            // Step through each column in the row, skipping the primary key and index columns
            for (int colA = NUM_HIDDEN_COLUMNS; colA < rowData.length; colA++)
            {
                // Check if the column value isn't blank
                if (!rowData[colA].isEmpty())
                {
                    // Get the column name
                    String colName = typeDefn.getColumnNamesUser()[colA];

                    // Check if this command name column
                    if (colName.equalsIgnoreCase(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME)))
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
                        CCDDTableColumnSet argColumnSet = new CCDDTableColumnSet();

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
                                        // Store the column row number, column name, and value
                                        CCDDTableColumnData columnData = new CCDDTableColumnData();
                                        columnData.setColumnName(typeDefn.getColumnNamesUser()[colB]);
                                        columnData.setValue(rowData[colB]);
                                        argColumnSet.getCCDDTableColumnData().add(columnData);

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
                                    }
                                }

                                // Check if the command argument has the minimum parameters
                                // required: a name and data type
                                if (argName != null && !argName.isEmpty() && dataType != null)
                                {
                                    // Add the command argument to the list
                                    arguments.add(addCommandArgument(nameSpace,
                                                                     commandName,
                                                                     argName,
                                                                     dataType,
                                                                     enumeration,
                                                                     units,
                                                                     description,
                                                                     argColumnSet));
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
                // Check that this is not one of the command argument columns and that the column
                // value isn't blank. This prevents adding command arguments to the overall command
                // information
                if (!isCmdArg[col] && !rowData[col].isEmpty())
                {
                    // Check if this column is for the command description
                    if (col == typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION))
                    {
                        // Store the command description
                        commandDescription = rowData[col];
                    }
                    // Check if this column isn't the command name
                    else if (col != typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME))
                    {
                        // Store the column column name and value
                        CCDDTableColumnData columnData = new CCDDTableColumnData();
                        columnData.setColumnName(typeDefn.getColumnNamesUser()[col]);
                        columnData.setValue(rowData[col]);
                        commandColumnSet.getCCDDTableColumnData().add(columnData);
                    }
                }
            }

            // Check if the command name exists
            if (commandName != null)
            {
                // Add the command information
                addCommand(nameSpace, commandName, arguments, commandDescription, commandColumnSet);
            }
        }
    }

    /**********************************************************************************************
     * Create the parameter set for the specified name space
     *
     * @param nameSpace
     *            name space
     *
     * @return Reference to the parameter set
     *********************************************************************************************/
    private InterfaceDeclarationType createParameterSet(NamespaceType nameSpace)
    {
        InterfaceDeclarationType intParmType = factory.createInterfaceDeclarationType();
        intParmType.setParameterSet(factory.createParameterSetType());
        nameSpace.getDeclaredInterfaceSet().getInterface().add(intParmType);
        return intParmType;
    }

    /**********************************************************************************************
     * Create the command set for the specified name space
     *
     * @param nameSpace
     *            name space
     *
     * @return Reference to the command set
     *********************************************************************************************/
    private InterfaceDeclarationType createCommandSet(NamespaceType nameSpace)
    {
        InterfaceDeclarationType intCmdType = factory.createInterfaceDeclarationType();
        intCmdType.setCommandSet(factory.createCommandSetType());
        nameSpace.getDeclaredInterfaceSet().getInterface().add(intCmdType);
        return intCmdType;
    }

    /**********************************************************************************************
     * Add a telemetry parameter to the name space's parameter set. Create the parameter set for
     * the name space if it does not exist
     *
     * @param nameSpace
     *            name space
     *
     * @param systemPath
     *            system path in the format <project name>.<system name>.<structure
     *            path>.<primitive data Type>.<variable name>
     *
     * @param parameterName
     *            parameter name
     *
     * @param dataType
     *            parameter primitive data type
     *
     * @param enumerations
     *            list containing enumerations in the format <enum label>|<enum value>[|...][,...];
     *            null to not specify
     *
     * @param units
     *            parameter units
     *
     * @param shortDescription
     *            short description of the parameter
     *
     * @param columnDataSet
     *            list containing column name and value for each of the parameter's columns
     *********************************************************************************************/
    private void addParameter(NamespaceType nameSpace,
                              String systemPath,
                              String parameterName,
                              String dataType,
                              List<String> enumerations,
                              String units,
                              String shortDescription,
                              CCDDTableColumnSet columnDataSet)
    {
        // Build the parameter attributes
        InterfaceParameterType parameter = factory.createInterfaceParameterType();
        parameter.setName(parameterName);
        parameter.setShortDescription(shortDescription);

        // Check if a data type is provided is a primitive type. If none is provided then no entry
        // for this parameter appears under the ParameterTypeSet, but it will appear under the
        // ParameterSet
        if (dataType != null)
        {
            // Set the parameter data type
            parameter.setType(dataType);

            // Check if the data type provided is a primitive type
            if (dataTypeHandler.isPrimitive(dataType))
            {
                // Check if enumeration parameters are provided
                if (enumerations != null)
                {
                    // Step through each enumeration
                    for (String enumeration : enumerations)
                    {
                        // Get the data type set for this name space
                        DataTypeSetType dataTypeSet = nameSpace.getDataTypeSet();

                        // Check if the data type set doesn't exist, which is the case for the
                        // first enumerated parameter
                        if (dataTypeSet == null)
                        {
                            // Create the data type set
                            dataTypeSet = factory.createDataTypeSetType();
                        }

                        // Create an enumeration type and enumeration list
                        EnumeratedDataType enumType = factory.createEnumeratedDataType();
                        EnumerationListType enumList = createEnumerationList(nameSpace,
                                                                             enumeration);
                        // Set the integer encoding (the only encoding available for an
                        // enumeration) and the size in bits
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                        intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(dataType))
                        {
                            // Set the encoding type to indicate an unsigned integer
                            intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                        }

                        // Set the enumeration parameter name, encoding type, and enumeration list
                        // attribute
                        enumType.setName(parameterName);
                        enumType.setIntegerDataEncoding(intEncodingType);
                        enumType.setEnumerationList(enumList);

                        // Add the enumeration information to this name space
                        dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(enumType);
                        nameSpace.setDataTypeSet(dataTypeSet);
                    }
                }
            }

            // Check if this data type hasn't already been referenced
            if (!referencedDataTypes.contains(dataType))
            {
                // Add the data type to the reference list
                referencedDataTypes.add(dataType);
            }
        }

        try
        {
            // This throws an illegal argument exception if the unit is not one of those in the
            // Unit enum class
            Unit unit = Unit.fromValue(units);
            SemanticsType semType = factory.createSemanticsType();
            semType.setUnit(unit);
            parameter.setSemantics(semType);
        }
        catch (IllegalArgumentException iae)
        {
            // TODO User-supplied units don't match one of the hard-coded Unit types (from
            // Units.java), which are the only ones that are accepted by the Unit fromValue()
            // method. The hard-coded unit types list is limited
        }

        InterfaceDeclarationType intParmType = null;

        // Step through the interfaces in order to locate the name space's parameter set
        for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
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
            intParmType = createParameterSet(nameSpace);
        }

        // Add the parameter to the parameter set
        intParmType.getParameterSet().getParameter().add(parameter);

        // Check if structure column row number, column name, and value data are supplied
        if (!columnDataSet.getCCDDTableColumnData().isEmpty())
        {
            // Store the column column name and value data
            parameter.setCCDDTableColumnSet(columnDataSet);
        }
    }

    /**********************************************************************************************
     * Add a command metadata set to the command metadata
     *
     * @param nameSpace
     *            name space
     *
     * @param commandName
     *            command name
     *
     * @param arguments
     *            list of command arguments
     *
     * @param shortDescription
     *            short description of the command
     *
     * @param commandColumnSet
     *            list containing column name and value for each of the command's columns
     *********************************************************************************************/
    private void addCommand(NamespaceType nameSpace,
                            String commandName,
                            List<CommandArgumentType> arguments,
                            String shortDescription,
                            CCDDTableColumnSet commandColumnSet)
    {
        // Build the command attributes
        InterfaceCommandType command = factory.createInterfaceCommandType();
        command.setName(commandName);
        command.setShortDescription(shortDescription);

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
        for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
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
            intCmdType = createCommandSet(nameSpace);
        }

        // Add the command to the command set
        intCmdType.getCommandSet().getCommand().add(command);

        // Check if command column row number, column name, and value data are supplied
        if (commandColumnSet != null && !commandColumnSet.getCCDDTableColumnData().isEmpty())
        {
            // Store the column row number, column name, and value data
            command.setCCDDTableColumnSet(commandColumnSet);
        }
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
     * @param enumColumnName
     *            name of the column containing the enumeration (if present)
     *
     * @param enumRow
     *            index of the row containing the enumeration (if present)
     *
     * @param units
     *            command argument units
     *
     * @param shortDescription
     *            short description of the command
     *
     * @param argColumnSet
     *            list containing column name and value for each of the command argument's columns
     *********************************************************************************************/
    private CommandArgumentType addCommandArgument(NamespaceType nameSpace,
                                                   String commandName,
                                                   String argumentName,
                                                   String dataType,
                                                   String enumeration,
                                                   String units,
                                                   String shortDescription,
                                                   CCDDTableColumnSet argColumnSet)
    {
        CommandArgumentType argType = factory.createCommandArgumentType();
        argType.setName(argumentName);
        argType.setShortDescription(shortDescription);

        if (enumeration != null)
        {
            // Get the data type set for this name space
            DataTypeSetType dataTypeSet = nameSpace.getDataTypeSet();

            // Check if the data type set doesn't exist, which is the case for the first enumerated
            // parameter
            if (dataTypeSet == null)
            {
                // Create the data type set
                dataTypeSet = factory.createDataTypeSetType();
            }

            // Create an enumeration type and enumeration list
            EnumeratedDataType enumType = factory.createEnumeratedDataType();
            EnumerationListType enumList = createEnumerationList(nameSpace,
                                                                 enumeration);

            // Set the integer encoding (the only encoding available for an enumeration) and the
            // size in bits
            IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
            intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));

            // Check if the data type is an unsigned integer
            if (dataTypeHandler.isUnsignedInt(dataType))
            {
                // Set the encoding type to indicate an unsigned integer
                intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
            }

            // Set the encoding type
            enumType.setIntegerDataEncoding(intEncodingType);

            // Set the command and command argument names
            enumType.setName(argumentName);
            enumType.setShortDescription(commandName);

            // Set the enumeration list attribute
            enumType.setEnumerationList(enumList);

            // Add the enumeration information to this name space
            dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(enumType);
            nameSpace.setDataTypeSet(dataTypeSet);
        }

        try
        {
            // This throws an illegal argument exception if the unit is not one of those in the
            // Unit enum class
            Unit unit = Unit.fromValue(units);
            SemanticsType semType = factory.createSemanticsType();
            semType.setUnit(unit);
            argType.setSemantics(semType);
        }
        catch (IllegalArgumentException iae)
        {
            // TODO User-supplied units don't match one of the hard-coded Unit types (from
            // Units.java), which are the only ones that are accepted by the Unit fromValue()
            // method. The hard-coded unit types list is limited
        }

        // Set the command argument data type
        argType.setType(dataType.toString().toLowerCase());

        // Check if column row number, column name, and value data is supplied
        if (argColumnSet != null && !argColumnSet.getCCDDTableColumnData().isEmpty())
        {
            // Store the column column name and value data
            argType.setCCDDTableColumnSet(argColumnSet);
        }

        // Check if this data type hasn't already been referenced
        if (!referencedDataTypes.contains(dataType))
        {
            // Add the data type to the reference list
            referencedDataTypes.add(dataType);
        }

        return argType;
    }

    /**********************************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param nameSpace
     *            name space
     *
     * @param enumeration
     *            enumeration in the format <enum value><enum value separator><enum label>[<enum
     *            value separator>...][<enum pair separator>...]
     *
     * @return Enumeration list for the supplied enumeration string
     *********************************************************************************************/
    private EnumerationListType createEnumerationList(NamespaceType nameSpace, String enumeration)
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
                                                              + nameSpace.getName()
                                                              + "'; "
                                                              + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }
}
