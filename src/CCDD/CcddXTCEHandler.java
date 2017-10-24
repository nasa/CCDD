/**
 * CFS Command & Data Dictionary XTCE handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is
 * claimed in the United States under Title 17, U.S. Code. All Other Rights
 * Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.IGNORE_BUTTON;
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

import org.omg.space.xtce.ArgumentTypeSetType;
import org.omg.space.xtce.ArgumentTypeSetType.FloatArgumentType;
import org.omg.space.xtce.ArgumentTypeSetType.IntegerArgumentType;
import org.omg.space.xtce.BaseDataType;
import org.omg.space.xtce.BaseDataType.UnitSet;
import org.omg.space.xtce.CommandMetaDataType;
import org.omg.space.xtce.CommandMetaDataType.MetaCommandSet;
import org.omg.space.xtce.DescriptionType.AncillaryDataSet;
import org.omg.space.xtce.DescriptionType.AncillaryDataSet.AncillaryData;
import org.omg.space.xtce.EnumeratedDataType;
import org.omg.space.xtce.EnumeratedDataType.EnumerationList;
import org.omg.space.xtce.FloatDataEncodingType;
import org.omg.space.xtce.HeaderType;
import org.omg.space.xtce.IntegerDataEncodingType;
import org.omg.space.xtce.MetaCommandType;
import org.omg.space.xtce.MetaCommandType.ArgumentList;
import org.omg.space.xtce.MetaCommandType.ArgumentList.Argument;
import org.omg.space.xtce.NameDescriptionType;
import org.omg.space.xtce.ObjectFactory;
import org.omg.space.xtce.ParameterPropertiesType;
import org.omg.space.xtce.ParameterSetType;
import org.omg.space.xtce.ParameterSetType.Parameter;
import org.omg.space.xtce.ParameterTypeSetType;
import org.omg.space.xtce.ParameterTypeSetType.EnumeratedParameterType;
import org.omg.space.xtce.ParameterTypeSetType.FloatParameterType;
import org.omg.space.xtce.ParameterTypeSetType.IntegerParameterType;
import org.omg.space.xtce.ParameterTypeSetType.StringParameterType;
import org.omg.space.xtce.SpaceSystemType;
import org.omg.space.xtce.StringDataEncodingType;
import org.omg.space.xtce.StringDataType;
import org.omg.space.xtce.TelemetryMetaDataType;
import org.omg.space.xtce.UnitType;
import org.omg.space.xtce.ValueEnumerationType;

import CCDD.CcddClasses.ArrayListCaseInsensitive;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableTypeDefinition;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DefaultPrimitiveTypeInfo;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.InternalTable.MacrosColumn;
import CCDD.CcddConstants.InternalTable.ReservedMsgIDsColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary XTCE handler class
 *****************************************************************************/
public class CcddXTCEHandler implements CcddImportExportInterface
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

    // Lists containing the imported table, table type, data type, and macro
    // definitions
    private List<TableDefinition> tableDefinitions;

    // JAXB and XTCE object references
    private JAXBElement<SpaceSystemType> project;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private ObjectFactory factory;

    // Name of the data field containing the system name
    private String systemFieldName;

    // Attribute strings
    private String versionAttr;
    private String validationStatusAttr;
    private String classification1Attr;
    private String classification2Attr;
    private String classification3Attr;

    // Conversion setup error flag
    private boolean errorFlag;

    // Flag indicating that macros should be replaced by their corresponding
    // values
    private boolean replaceMacros;

    // Lists to contain any references to table types, data types, macros, and
    // variable paths in the exported tables
    private List<String> referencedTableTypes;
    private List<String> referencedDataTypes;
    private List<String> referencedMacros;
    private List<String[]> referencedVariablePaths;

    // XTCE data types
    private enum XTCEDataType
    {
        INTEGER,
        FLOAT,
        STRING
    }

    /**************************************************************************
     * XTCE data type tags
     *************************************************************************/
    private enum XTCETags
    {
        TABLE_NAME("Table name"),
        TABLE_TYPE("Table type"),
        TABLE_DESCRIPTION("Table description"),
        COLUMN("Column data"),
        DATA_FIELD("Data field"),
        ENUMERATION("Enumeration"),
        DATA_TYPE("Data type"),
        MACRO("Macro"),
        RESERVED_MSG_ID("Reserved Message ID"),
        VARIABLE_PATH("Variable Path");

        private String tag;

        /**********************************************************************
         * Additional XTCE data type tags constructor
         *
         * @param tag
         *            text describing the data
         *********************************************************************/
        XTCETags(String tag)
        {
            this.tag = tag;
        }

        /**********************************************************************
         * Get the data type tag
         *
         * @return Text describing the data
         *********************************************************************/
        protected String getTag()
        {
            return tag + ": ";
        }

        /**********************************************************************
         * Get the column tag
         *
         * @param columnName
         *            column name
         *
         * @param row
         *            row number for which the column data applies
         *
         * @return Text describing the column, using the column name and row
         *         number
         *********************************************************************/
        protected String getColumnTag(String columnName, int row)
        {
            return tag
                   + ": "
                   + columnName
                   + " : Row: "
                   + String.valueOf(row);
        }

        /**********************************************************************
         * Get the index of the column name within the column tag string
         *
         * @return Index of the column name within the column tag string
         *********************************************************************/
        protected static int getColumnNameIndex()
        {
            return 1;
        }

        /**********************************************************************
         * Get the index of the row index within the column tag string
         *
         * @return Index of the row index within the column tag string
         *********************************************************************/
        protected static int getRowIndex()
        {
            return 3;
        }
    }

    /**************************************************************************
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
     *************************************************************************/
    CcddXTCEHandler(CcddMain ccddMain,
                    CcddFieldHandler fieldHandler,
                    Component parent)
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
            // Create the XML marshaller used to convert the CCDD project data
            // into XTCE XML format
            JAXBContext context = JAXBContext.newInstance("org.omg.space.xtce");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   "http://www.omg.org/spec/XTCE/20061101/06-11-06.xsd");
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
                                   new Boolean(true));

            // Create the factory for building the space system objects
            factory = new ObjectFactory();

            // Create the XML unmarshaller used to convert XTCE XML data into
            // CCDD project data format
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

    /**************************************************************************
     * Get the status of the conversion setup error flag
     *
     * @return true if an error occurred setting up for the XTCE conversion
     *************************************************************************/
    @Override
    public boolean getErrorStatus()
    {
        return errorFlag;
    }

    /**************************************************************************
     * Get the table definitions
     *
     * @return List of table definitions
     *************************************************************************/
    @Override
    public List<TableDefinition> getTableDefinitions()
    {
        return tableDefinitions;
    }

    /**************************************************************************
     * Import the the table definitions from an XTCE XML formatted file
     *
     * @param importFile
     *            reference to the user-specified XML input file
     *
     * @param importAll
     *            ImportType.IMPORT_ALL to import the table type, data type,
     *            and macro definitions, and the data from all the table
     *            definitions; ImportType.FIRST_DATA_ONLY to load only the data
     *            for the first table defined
     *************************************************************************/
    @Override
    public void importFromFile(File importFile,
                               ImportType importType) throws CCDDException,
                                                      IOException,
                                                      Exception
    {
        try
        {
            // Import the XML from the specified file
            JAXBElement<?> jaxbElement = (JAXBElement<?>) unmarshaller.unmarshal(importFile);

            // Get the top-level space system
            SpaceSystemType rootSystem = (SpaceSystemType) jaxbElement.getValue();

            // Import the table type, if present
            importTableTypeDefinitions(rootSystem, importFile.getAbsolutePath());

            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                // Import the data type and macro definitions, if present
                importDataTypeDefinitions(rootSystem, importFile.getAbsolutePath());
                importMacroDefinitions(rootSystem, importFile.getAbsolutePath());
                importReservedMsgIDDefinitions(rootSystem, importFile.getAbsolutePath());
            }

            tableDefinitions = new ArrayList<TableDefinition>();

            // Step through each system name (tables not assigned to a system
            // should be placed under DefaultSystem)
            for (SpaceSystemType spaceSystem : rootSystem.getSpaceSystem())
            {
                // Recursively step through the XTCE-formatted data and extract
                // the telemetry and command information
                unbuildSpaceSystems(spaceSystem,
                                    spaceSystem.getName(),
                                    importType,
                                    importFile.getAbsolutePath());

                // Check if only the data from the first table is to be read
                if (importType == ImportType.FIRST_DATA_ONLY)
                {
                    // Stop reading table definitions
                    break;
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

    /**************************************************************************
     * Export the project in XTCE XML format to the specified file
     *
     * @param exportFile
     *            reference to the user-specified output file
     *
     * @param tableNames
     *            array of table names to convert
     *
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding
     *            values
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table
     *            in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a
     *            structure table, both in application format and using the
     *            user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if
     *            includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator
     *            character(s), show/hide data types flag ('true' or 'false'),
     *            and data type/variable name separator character(s); null if
     *            includeVariablePaths is false
     *
     * @param extraInfo
     *            [0] name of the data field containing the system name <br>
     *            [1] version attribute <br>
     *            [2] validation status attribute <br>
     *            [3] first level classification attribute <br>
     *            [4] second level classification attribute <br>
     *            [5] third level classification attribute
     *
     * @return true if an error occurred preventing exporting the project to
     *         the file
     *************************************************************************/
    @Override
    public boolean exportToFile(File exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                boolean includeReservedMsgIDs,
                                boolean includeVariablePaths,
                                CcddVariableConversionHandler variableHandler,
                                String[] separators,
                                String... extraInfo)
    {
        boolean errorFlag = false;

        try
        {
            // Convert the table data into XTCE XML format
            convertTablesToXTCE(tableNames,
                                replaceMacros,
                                includeReservedMsgIDs,
                                includeVariablePaths,
                                variableHandler,
                                separators,
                                extraInfo[0],
                                extraInfo[1],
                                extraInfo[2],
                                extraInfo[3],
                                extraInfo[4],
                                extraInfo[5]);

            try
            {
                // Output the file creation information
                marshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders",
                                       "\n<!-- Created "
                                                                               + new Date().toString()
                                                                               + " : project = "
                                                                               + dbControl.getDatabase()
                                                                               + " : host = "
                                                                               + dbControl.getServer()
                                                                               + " : user = "
                                                                               + dbControl.getUser()
                                                                               + " -->");
            }
            catch (JAXBException je)
            {
                // Ignore the error if setting this property fails; the comment
                // is not included
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

    /**************************************************************************
     * Convert the project database contents to XTCE XML format
     *
     * @param tableNames
     *            array of table names to convert
     *
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding
     *            values
     *
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table
     *            in the export file
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a
     *            structure table, both in application format and using the
     *            user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if
     *            includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator
     *            character(s), show/hide data types flag ('true' or 'false'),
     *            and data type/variable name separator character(s); null if
     *            includeVariablePaths is false
     *
     * @param system
     *            name of the data field containing the system name
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
     *************************************************************************/
    private void convertTablesToXTCE(String[] tableNames,
                                     boolean replaceMacros,
                                     boolean includeReservedMsgIDs,
                                     boolean includeVariablePaths,
                                     CcddVariableConversionHandler variableHandler,
                                     String[] separators,
                                     String system,
                                     String version,
                                     String validationStatus,
                                     String classification1,
                                     String classification2,
                                     String classification3)
    {
        referencedTableTypes = new ArrayList<String>();
        referencedDataTypes = new ArrayList<String>();
        referencedMacros = new ArrayListCaseInsensitive();
        referencedVariablePaths = new ArrayList<String[]>();

        // Store the macro replacement flag, the system field name, and
        // attributes
        this.replaceMacros = replaceMacros;
        systemFieldName = system;
        versionAttr = version;
        validationStatusAttr = validationStatus;
        classification1Attr = classification1;
        classification2Attr = classification2;
        classification3Attr = classification3;

        // Create the root space system
        SpaceSystemType rootSystem = addSpaceSystem(null,
                                                    dbControl.getDatabase(),
                                                    dbControl.getDatabaseDescription(dbControl.getDatabase()),
                                                    classification1Attr,
                                                    validationStatusAttr,
                                                    versionAttr,
                                                    null);

        // Add the project's space systems, parameters, and commands
        buildSpaceSystems(tableNames,
                          includeVariablePaths,
                          variableHandler,
                          separators,
                          rootSystem);

        List<String[]> otherAttrs = new ArrayList<String[]>();

        // Add the table type and data type definitions to list
        otherAttrs.addAll(exportTableTypeDefinitions());
        otherAttrs.addAll(exportDataTypeDefinitions());

        // Check if the macro names are to be retained
        if (!replaceMacros)
        {
            // Add the macro definitions to the list
            otherAttrs.addAll(exportMacroDefinitions());
        }

        // Check if the user elected to store the reserved message IDs
        if (includeReservedMsgIDs)
        {
            // Add the reserved message ID definitions to the list
            otherAttrs.addAll(exportReservedMsgIDDefinitions());
        }

        // Check if the user elected to store the variable paths
        if (includeVariablePaths)
        {
            // Add the variable paths to the list
            otherAttrs.addAll(exportVariablePaths());
        }

        // Check if other attributes exist
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            // Store the other data as ancillary data
            rootSystem.setAncillaryDataSet(storeOtherAttributes(otherAttrs));
        }
    }

    /**************************************************************************
     * Recursively step through the XTCE-formatted data and extract the
     * telemetry and command information
     *
     * @param parentSystem
     *            parent space system nest level
     *
     * @param systemName
     *            system name
     *
     * @param importAll
     *            ImportType.IMPORT_ALL to import the table data fields along
     *            with the data from the table; ImportType.FIRST_DATA_ONLY to
     *            load only the data for the table
     *
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void unbuildSpaceSystems(SpaceSystemType parentSystem,
                                     String systemName,
                                     ImportType importType,
                                     String importFileName) throws CCDDException
    {
        // Get the list of this parent system's child systems
        List<SpaceSystemType> childSystems = parentSystem.getSpaceSystem();

        int numColumns = 0;

        // Flags indicating if importing should continue after an input
        // error is detected
        boolean continueOnColumnError = false;
        boolean continueOnDataFieldError = false;

        // Step through each child system, if any. Only structure tables can
        // have child tables, and all child tables are structure tables
        for (SpaceSystemType childSystem : childSystems)
        {
            // Create a table definition for this table
            TableDefinition tableDefn = new TableDefinition(childSystem.getName(),
                                                            childSystem.getShortDescription());

            // Structure table description
            if (childSystem.getShortDescription() != null
                && !childSystem.getShortDescription().isEmpty())
            {
                tableDefn.setDescription(childSystem.getShortDescription());
            }

            /******************************************************************
             * Other data processing
             ***************************************************************/
            // Get the table type and data fields for this table. Also, if this
            // table's type isn't a structure or a command then get the table's
            // column information
            AncillaryDataSet ancillarySet = childSystem.getAncillaryDataSet();

            // Check if any extra data exists
            if (ancillarySet != null)
            {
                // Step through the extra data
                for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
                {
                    // Check if this is the table type
                    if (ancillaryData.getName().equals(XTCETags.TABLE_TYPE.getTag()))
                    {
                        // Store the table's type name
                        tableDefn.setType(ancillaryData.getValue());

                        // Get the table's type definition based on the type
                        // name
                        typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getType());

                        // Check if the table type isn't recognized
                        if (typeDefn == null)
                        {
                            throw new CCDDException("unknown table type '"
                                                    + tableDefn.getType()
                                                    + "'");
                        }

                        // Get the number of columns defined in this table
                        // type
                        numColumns = typeDefn.getColumnCountVisible();
                    }
                    // Check if data fields are to be imported and this is a
                    // data field definition
                    else if (importType == ImportType.IMPORT_ALL
                             && ancillaryData.getName().startsWith(XTCETags.DATA_FIELD.getTag()))
                    {
                        // Get the data field inputs. If not present use a
                        // blank to prevent an error when separating the inputs
                        String inputs = ancillaryData.getValue() != null
                                                                         ? ancillaryData.getValue()
                                                                         : "";

                        // Parse data field. The values are comma-separated;
                        // however, commas within quotes are ignored - this
                        // allows commas to be included in the data values
                        String[] fieldDefn = CcddUtilities.splitAndRemoveQuotes("\""
                                                                                + tableDefn.getName()
                                                                                + "\","
                                                                                + inputs);

                        // Check if the expected number of inputs is present
                        if (fieldDefn.length == FieldEditorColumnInfo.values().length + 1)
                        {
                            // Add the data field definition to the table
                            tableDefn.addDataField(fieldDefn);
                        }
                        // Check that the user hasn't elected to ignore data
                        // field errors
                        else if (!continueOnDataFieldError)
                        {
                            // Inform the user that the data field name inputs
                            // are incorrect
                            int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                "<html><b>Table '</b>"
                                                                                                        + tableDefn.getName()
                                                                                                        + "<b>' has missing or extra data "
                                                                                                        + "field input(s) in import file '</b>"
                                                                                                        + importFileName
                                                                                                        + "<b>'; continue?",
                                                                                                "Data Field Error",
                                                                                                "Ignore this invalid data field",
                                                                                                "Ignore this and any remaining invalid data fields",
                                                                                                "Stop importing");

                            // Check if the Ignore All button was pressed
                            if (buttonSelected == IGNORE_BUTTON)
                            {
                                // Set the flag to ignore subsequent data field
                                // errors
                                continueOnDataFieldError = true;
                            }
                            // Check if the Cancel button was pressed
                            else if (buttonSelected == CANCEL_BUTTON)
                            {
                                // No error message is provided since the user
                                // chose this action
                                throw new CCDDException();
                            }
                        }
                    }
                    // Check if this is a table column value. This is for
                    // tables that aren't structure or command tables
                    else if (ancillaryData.getName().startsWith(XTCETags.COLUMN.getTag()))
                    {
                        // Extract the column name and row number, and use the
                        // column name to get the column index
                        String[] parts = ancillaryData.getName().split(":");
                        String columnName = parts[1].trim();
                        int row = Integer.valueOf(parts[3].trim());
                        int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                        // Check that the column exists in the table
                        if (column != -1)
                        {
                            // Add one or more rows until the row is created
                            // containing this column value
                            while (row * numColumns >= tableDefn.getData().size())
                            {
                                // Create a row with empty columns and add the
                                // new row to the table data
                                String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                Arrays.fill(newRow, "");
                                tableDefn.addData(newRow);
                            }

                            // Replace the value for the specified column
                            tableDefn.getData().set(row
                                                    * numColumns
                                                    + column,
                                                    ancillaryData.getValue());
                        }
                        // The column doesn't exist
                        else
                        {
                            // Get user confirmation to continue or stop the
                            // import
                            continueOnColumnError = confirmContinue(continueOnColumnError,
                                                                    columnName,
                                                                    tableDefn.getName(),
                                                                    importFileName);
                        }
                    }
                }
            }

            /******************************************************************
             * Telemetry processing
             ***************************************************************/
            // Get the child system's telemetry metadata information
            TelemetryMetaDataType tlmMetaData = childSystem.getTelemetryMetaData();

            // Check if the telemetry metadata information exists and that the
            // table type is recognized
            if (tlmMetaData != null && typeDefn != null)
            {
                // Get variable name, data type, enumeration, description, and
                // units column indices
                int variableNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE));
                int dataTypeIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT));
                int enumerationIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.ENUMERATION));
                int descriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));
                int unitsIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.UNITS));

                // Get the telemetry information
                ParameterSetType parmSetType = tlmMetaData.getParameterSet();

                // Check if the telemetry information exists
                if (parmSetType != null)
                {
                    // Get the list of telemetry parameters
                    List<Object> tlms = parmSetType.getParameterOrParameterRef();

                    // Step through each telemetry parameter
                    for (Object tlm : tlms)
                    {
                        Parameter parm = (Parameter) tlm;

                        // Create a new row of data in the table definition to
                        // contain this structures's information. Initialize
                        // all columns to blanks except for the variable name
                        String[] newRow = new String[typeDefn.getColumnCountVisible()];
                        Arrays.fill(newRow, "");
                        newRow[variableNameIndex] = parm.getName();

                        // Get a reference to any extra data for this parameter
                        ancillarySet = parm.getAncillaryDataSet();

                        // Check if the parameter set ancillary data exists
                        if (ancillarySet != null)
                        {
                            // Step through each ancillary data entry
                            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
                            {
                                // Split the ancillary data into the column
                                // name and row number (each with an
                                // identifier, which are ignored)
                                String[] parts = ancillaryData.getName().split(":");
                                String columnName = parts[XTCETags.getColumnNameIndex()].trim();

                                // Get the column index for the column
                                // described in the ancillary data
                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                // Check if the column exists in the table type
                                // definition
                                if (column != -1)
                                {
                                    newRow[column] = ancillaryData.getValue();
                                }
                                // The column doesn't exist
                                else
                                {
                                    // Get user confirmation to continue or
                                    // stop the import
                                    continueOnColumnError = confirmContinue(continueOnColumnError,
                                                                            columnName,
                                                                            tableDefn.getName(),
                                                                            importFileName);
                                }
                            }
                        }

                        // Add the new row to the table definition
                        tableDefn.addData(newRow);
                    }
                }

                ParameterTypeSetType parmTypeSetType = tlmMetaData.getParameterTypeSet();

                // Check if the telemetry information exists
                if (parmTypeSetType != null)
                {
                    // Get the list of telemetry parameters
                    List<NameDescriptionType> tlms = parmTypeSetType.getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType();

                    // Step through each telemetry parameter
                    for (NameDescriptionType tlm : tlms)
                    {
                        String dataType = "";
                        String sizeInBits = null;
                        String enumeration = null;
                        UnitSet unitSet = null;

                        // Based on the parameter data type get the size in
                        // bits and unit attributes, and reconstruct the
                        // original data type from the parameter type, encoding
                        // type, and/or bit size or width. If the ancillary
                        // data contains the data type then it overwrites the
                        // data type constructed here

                        // Integer data type
                        if (tlm instanceof IntegerParameterType)
                        {
                            IntegerParameterType itlm = (IntegerParameterType) tlm;
                            sizeInBits = itlm.getSizeInBits().toString();
                            unitSet = itlm.getUnitSet();

                            // Check if integer encoding is set to 'unsigned'
                            if (itlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                            {
                                dataType = "u";
                            }

                            dataType += "int" + sizeInBits;
                        }
                        // Floating point data type
                        else if (tlm instanceof FloatParameterType)
                        {
                            FloatParameterType ftlm = (FloatParameterType) tlm;
                            BigInteger size = ftlm.getSizeInBits();
                            sizeInBits = size.toString();
                            unitSet = ftlm.getUnitSet();
                            dataType = "float";
                        }
                        // String data type
                        else if (tlm instanceof StringParameterType)
                        {
                            StringParameterType stlm = (StringParameterType) tlm;
                            BigInteger size = stlm.getCharacterWidth();
                            sizeInBits = size.toString();
                            unitSet = stlm.getUnitSet();

                            // Use the character width to determine character
                            // versus string
                            if (size.intValue() == 1)
                            {
                                dataType = DefaultPrimitiveTypeInfo.CHAR.getUserName();
                            }
                            else
                            {
                                dataType = DefaultPrimitiveTypeInfo.STRING.getUserName();
                            }
                        }
                        // Enumerated data type
                        else if (tlm instanceof EnumeratedParameterType)
                        {
                            // Get the enumeration parameters
                            EnumeratedParameterType etlm = (EnumeratedParameterType) tlm;
                            EnumerationList enumList = etlm.getEnumerationList();

                            // Check if any enumeration parameters are defined
                            if (enumList != null)
                            {
                                ancillarySet = etlm.getAncillaryDataSet();

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
                                        enumeration += ",";
                                    }

                                    // Begin building this enumeration
                                    enumeration += enumType.getValue()
                                                   + " | "
                                                   + enumType.getLabel();
                                }

                                sizeInBits = etlm.getIntegerDataEncoding().getSizeInBits().toString();
                                unitSet = etlm.getUnitSet();

                                // Check if integer encoding is set to
                                // 'unsigned'
                                if (etlm.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                                {
                                    dataType = "u";
                                }

                                dataType += "int" + sizeInBits;
                            }
                        }

                        // Get the row index that refers to this variable
                        int row = typeDefn.getRowIndexByColumnValue(tableDefn.getData(),
                                                                    numColumns,
                                                                    tlm.getName(),
                                                                    variableNameIndex);

                        // Check if the variable exists
                        if (row != -1)
                        {
                            // Check if a data type exists and isn't already
                            // extracted from the ancillary data
                            if (dataTypeIndex != -1
                                && !dataType.isEmpty()
                                && tableDefn.getData().get(row
                                                           * numColumns
                                                           + dataTypeIndex)
                                            .isEmpty())
                            {
                                // Store the data type
                                tableDefn.getData().set(row
                                                        * numColumns
                                                        + dataTypeIndex,
                                                        dataType);
                            }

                            // Check if a description exists and isn't already
                            // extracted from the ancillary data
                            if (descriptionIndex != -1
                                && tlm.getShortDescription() != null
                                && tableDefn.getData().get(row
                                                           * numColumns
                                                           + descriptionIndex)
                                            .isEmpty())
                            {
                                // Store the description
                                tableDefn.getData().set(row
                                                        * numColumns
                                                        + descriptionIndex,
                                                        tlm.getShortDescription());
                            }

                            // Check if a units exists and isn't already
                            // extracted from the ancillary data
                            if (unitsIndex != -1
                                && unitSet != null
                                && tableDefn.getData().get(row
                                                           * numColumns
                                                           + unitsIndex)
                                            .isEmpty())
                            {
                                List<UnitType> unitType = unitSet.getUnit();

                                // Check if the units exist
                                if (!unitType.isEmpty())
                                {
                                    // Store the units for this variable
                                    tableDefn.getData().set(row
                                                            * numColumns
                                                            + unitsIndex,
                                                            unitType.get(0).getContent());
                                }
                            }

                            // Check if an enumeration exists and isn't already
                            // extracted from the ancillary data
                            if (enumerationIndex != -1
                                && enumeration != null
                                && tableDefn.getData().get(row
                                                           * numColumns
                                                           + enumerationIndex)
                                            .isEmpty())
                            {
                                // Store the enumeration parameters. This
                                // accounts only for the first enumeration for
                                // a variable. If the variable has more than
                                // one enumeration column then the ancillary
                                // data contains the other enumeration
                                // column(s) parameters
                                tableDefn.getData().set(row
                                                        * numColumns
                                                        + enumerationIndex,
                                                        enumeration);
                            }
                        }
                    }
                }
            }

            /******************************************************************
             * Command processing
             ***************************************************************/
            // Get the child system's command metadata information
            CommandMetaDataType cmdMetaData = childSystem.getCommandMetaData();

            // Check if the command metadata information exists and that the
            // table type is recognized
            if (cmdMetaData != null && typeDefn != null)
            {
                // Get the list containing command argument name, data type,
                // enumeration, minimum, maximum, and other associated column
                // indices for each argument grouping
                List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(true);

                // Get the command name and description columns
                int commandNameIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.COMMAND_NAME));
                int cmdDescriptionIndex = CcddTableTypeHandler.getVisibleColumnIndex(typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION));

                // Check if the description column belongs to a command
                // argument
                if (commandArguments.size() != 0
                    && cmdDescriptionIndex > commandArguments.get(0).getName())
                {
                    // Reset the command description index to indicate no
                    // description exists
                    cmdDescriptionIndex = -1;
                }

                // Get the description column name
                // Get the command set information
                MetaCommandSet metaCmdSet = cmdMetaData.getMetaCommandSet();

                // Check if the command set information exists
                if (metaCmdSet != null)
                {
                    // Get the list of command sets
                    List<Object> cmds = metaCmdSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand();

                    // Step through each command set
                    for (Object cmd : cmds)
                    {
                        MetaCommandType cmdType = (MetaCommandType) cmd;

                        // Create a new row of data in the table definition to
                        // contain this command's information. Initialize all
                        // columns to blanks except for the command name
                        String[] newRow = new String[typeDefn.getColumnCountVisible()];
                        Arrays.fill(newRow, "");
                        newRow[commandNameIndex] = cmdType.getName();

                        // Check if the command description is present and the
                        // description column exists in the table type
                        // definition
                        if (cmdType.getShortDescription() != null
                            && cmdDescriptionIndex != -1)
                        {
                            // Store the command description in the row's
                            // description column
                            newRow[cmdDescriptionIndex] = cmdType.getShortDescription();
                        }

                        // Add the new row to the table definition
                        tableDefn.addData(newRow);

                        // Get a reference to the command metadata ancillary
                        // data
                        ancillarySet = cmdType.getAncillaryDataSet();

                        // Check if the command metadata ancillary data exists
                        if (ancillarySet != null)
                        {
                            // Step through each ancillary data entry
                            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
                            {
                                // Split the ancillary data into the column
                                // name and row number (each with an
                                // identifier, which are ignored)
                                String[] parts = ancillaryData.getName().split(":");
                                String columnName = parts[XTCETags.getColumnNameIndex()].trim();
                                int row = Integer.valueOf(parts[XTCETags.getRowIndex()].trim());

                                // Get the column index for the column
                                // described in the ancillary data
                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                // Check if the column exists in the table type
                                // definition
                                if (column != -1)
                                {
                                    // Check if the cell hasn't already been
                                    // populated by other command metadata
                                    if (tableDefn.getData().get(row
                                                                * numColumns
                                                                + column)
                                                 .isEmpty())
                                    {
                                        // Update the table data at the row and
                                        // column specified with the value from
                                        // the ancillary data
                                        tableDefn.getData().set(row
                                                                * numColumns
                                                                + column,
                                                                ancillaryData.getValue());
                                    }
                                }
                                // The column doesn't exist
                                else
                                {
                                    // Get user confirmation to continue or
                                    // stop the import
                                    continueOnColumnError = confirmContinue(continueOnColumnError,
                                                                            columnName,
                                                                            tableDefn.getName(),
                                                                            importFileName);
                                }
                            }
                        }
                    }
                }

                // Get the command argument information
                ArgumentTypeSetType argSetType = cmdMetaData.getArgumentTypeSet();

                // Check if the command argument information exists
                if (argSetType != null)
                {
                    // Get the list of command arguments
                    List<NameDescriptionType> cmds = argSetType.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType();

                    int cmdArgIndex = -1;
                    String command = "";

                    // Step through each command argument
                    for (NameDescriptionType cmd : cmds)
                    {
                        String dataType = "";
                        String sizeInBits = null;
                        String description = null;
                        String enumeration = null;
                        String units = null;
                        UnitSet unitSet = null;

                        // Check if the command name changed
                        if (!((BaseDataType) cmd).getBaseType().equals(command))
                        {
                            // Reset the argument index
                            cmdArgIndex = -1;
                        }

                        // Increment the argument index and store the command
                        // name for which this argument is a member
                        cmdArgIndex++;
                        command = ((BaseDataType) cmd).getBaseType();

                        // Based on the command argument data type get the size
                        // in bits and unit attributes, and reconstruct the
                        // original data type from the parameter type, encoding
                        // type, and/or bit size or width
                        // Integer data type
                        if (cmd instanceof IntegerArgumentType)
                        {
                            IntegerArgumentType icmd = (IntegerArgumentType) cmd;
                            sizeInBits = icmd.getSizeInBits().toString();
                            unitSet = icmd.getUnitSet();

                            // Check if integer encoding is set to 'unsigned'
                            if (icmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                            {
                                dataType = "u";
                            }

                            dataType += "int" + sizeInBits;
                        }
                        // Floating point data type
                        else if (cmd instanceof FloatArgumentType)
                        {
                            FloatArgumentType fcmd = (FloatArgumentType) cmd;
                            BigInteger size = fcmd.getSizeInBits();
                            sizeInBits = size.toString();
                            unitSet = fcmd.getUnitSet();
                            dataType = "float";
                        }
                        // String data type
                        else if (cmd instanceof StringDataType)
                        {
                            StringDataType scmd = (StringDataType) cmd;
                            BigInteger size = scmd.getCharacterWidth();
                            sizeInBits = size.toString();
                            unitSet = scmd.getUnitSet();

                            // Use the character width to determine character
                            // versus string
                            if (size.intValue() == 1)
                            {
                                dataType = DefaultPrimitiveTypeInfo.CHAR.getUserName();
                            }
                            else
                            {
                                dataType = DefaultPrimitiveTypeInfo.STRING.getUserName();
                            }
                        }
                        // Enumerated data type
                        else if (cmd instanceof EnumeratedDataType)
                        {
                            EnumeratedDataType ecmd = (EnumeratedDataType) cmd;
                            EnumerationList enumList = ecmd.getEnumerationList();

                            // Check if any enumeration parameters are defined
                            if (enumList != null)
                            {
                                ancillarySet = ecmd.getAncillaryDataSet();

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

                                sizeInBits = ecmd.getIntegerDataEncoding().getSizeInBits().toString();
                                unitSet = ecmd.getUnitSet();

                                // Check if integer encoding is set to
                                // 'unsigned'
                                if (ecmd.getIntegerDataEncoding().getEncoding().equalsIgnoreCase("unsigned"))
                                {
                                    dataType = "u";
                                }

                                dataType += "int" + sizeInBits;
                            }
                        }

                        // Check if the description exists
                        if (cmd.getShortDescription() != null)
                        {
                            // Store the description
                            description = cmd.getShortDescription();
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

                        // Step through each row of table data
                        for (int row = 0; row < tableDefn.getData().size(); row += numColumns)
                        {
                            // Check if the command name matches the one in the
                            // table data for this row
                            if (tableDefn.getData().get(row + commandNameIndex) != null
                                && tableDefn.getData().get(row + commandNameIndex).equals(command)
                                && cmdArgIndex < commandArguments.size())
                            {
                                // Get the command argument reference
                                AssociatedColumns cmdArg = commandArguments.get(cmdArgIndex);

                                // Check if the command argument name is
                                // present
                                if (cmdArg.getName() != -1
                                    && !cmd.getName().isEmpty())
                                {
                                    // Store the command argument name
                                    tableDefn.getData().set(row
                                                            + cmdArg.getName(),
                                                            cmd.getName());
                                }

                                // Check if the command argument data type is
                                // present
                                if (cmdArg.getDataType() != -1
                                    && !dataType.isEmpty())
                                {
                                    // Store the command argument data type
                                    tableDefn.getData().set(row
                                                            + cmdArg.getDataType(),
                                                            dataType);
                                }

                                // Check if the command argument enumeration is
                                // present
                                if (cmdArg.getEnumeration() != -1
                                    && enumeration != null)
                                {
                                    // Store the command argument enumeration
                                    tableDefn.getData().set(row
                                                            + cmdArg.getEnumeration(),
                                                            enumeration);
                                }

                                // Check if the command argument description is
                                // present
                                if (cmdArg.getDescription() != -1
                                    && description != null)
                                {
                                    // Store the command argument description
                                    tableDefn.getData().set(row
                                                            + cmdArg.getDescription(),
                                                            description);
                                }

                                // Check if the command argument units is
                                // present
                                if (cmdArg.getUnits() != -1
                                    && units != null)
                                {
                                    // Store the command argument units
                                    tableDefn.getData().set(row
                                                            + cmdArg.getUnits(),
                                                            units);
                                }
                            }
                        }

                        // Get the other command argument columns
                        ancillarySet = cmd.getAncillaryDataSet();

                        // Check if there are any command argument columns
                        if (ancillarySet != null)
                        {
                            // Step though the command argument columns stored
                            // in the ancillary data
                            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
                            {
                                // Split the ancillary data into the column
                                // name and row number (each with an
                                // identifier, which are ignored)
                                String[] parts = ancillaryData.getName().split(":");
                                String columnName = parts[1].trim();
                                int row = Integer.valueOf(parts[3].trim());

                                // Get the column index for the column
                                // described in the ancillary data
                                int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                // Check if the column exists in the table type
                                // definition
                                if (column != -1)
                                {
                                    // Check if the cell hasn't already been
                                    // populated by other command metadata
                                    // (except for the data type)
                                    if (tableDefn.getData().get(row
                                                                * numColumns
                                                                + column)
                                                 .isEmpty()
                                        || column == commandArguments.get(cmdArgIndex).getDataType())
                                    {
                                        // Update the table data at the row and
                                        // column specified with the value from
                                        // the ancillary data
                                        tableDefn.getData().set(row
                                                                * numColumns
                                                                + column,
                                                                ancillaryData.getValue());
                                    }
                                }
                                // The column doesn't exist
                                else
                                {
                                    // Get user confirmation to continue or
                                    // stop the import
                                    continueOnColumnError = confirmContinue(continueOnColumnError,
                                                                            columnName,
                                                                            tableDefn.getName(),
                                                                            importFileName);
                                }
                            }
                        }
                    }
                }
            }

            // Add the table definition to the list
            tableDefinitions.add(tableDefn);

            // Check if the data from all tables is to be read
            if (importType == ImportType.IMPORT_ALL)
            {
                // Process this child system's children, if any
                unbuildSpaceSystems(childSystem,
                                    systemName,
                                    importType,
                                    importFileName);
            }
        }
    }

    /**************************************************************************
     * Confirm if the user wishes to continue or stop the import following
     * detection of an unrecognized column name
     *
     * @param continueOnColumnError
     *            true if the user has already elected to continue following an
     *            unknown column name
     *
     * @param columnName
     *            column name (as seen by the user)
     *
     * @param tableName
     *            table name
     *
     * @param importFileName
     *            import file name
     *
     * @return false if the user chooses to ignore this unrecognized column
     *         name, true if the user chooses to ignore any subsequent
     *         unrecognized column names, and throws an exception if the user
     *         elects to stop the import
     *************************************************************************/
    private boolean confirmContinue(boolean continueOnColumnError,
                                    String columnName,
                                    String tableName,
                                    String importFileName) throws CCDDException
    {
        // Check that the user hasn't elected to ignore column name errors
        if (!continueOnColumnError)
        {
            // Inform the user that the column name is invalid
            int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                "<html><b>Table '</b>"
                                                                                        + tableName
                                                                                        + "<b>' column name '</b>"
                                                                                        + columnName
                                                                                        + "<b>' unrecognized in import file '</b>"
                                                                                        + importFileName
                                                                                        + "<b>'; continue?",
                                                                                "Column Error",
                                                                                "Ignore this invalid column name",
                                                                                "Ignore this and any remaining invalid column names",
                                                                                "Stop importing");

            // Check if the Ignore All button was pressed
            if (buttonSelected == IGNORE_BUTTON)
            {
                // Set the flag to ignore subsequent column name errors
                continueOnColumnError = true;
            }
            // Check if the Cancel button was pressed
            else if (buttonSelected == CANCEL_BUTTON)
            {
                // No error message is provided since the user chose this
                // action
                throw new CCDDException();
            }
        }

        return continueOnColumnError;
    }

    /**************************************************************************
     * Extract the table type definitions, if present, from the imported root
     * system
     *
     * @param spaceSystem
     *            top-level space system
     *
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void importTableTypeDefinitions(SpaceSystemType spaceSystem,
                                            String importFileName) throws CCDDException
    {
        // Get the table type definitions
        AncillaryDataSet ancillarySet = spaceSystem.getAncillaryDataSet();

        // Check if any extra data exists
        if (ancillarySet != null)
        {
            List<TableTypeDefinition> tableTypeDefns = new ArrayList<TableTypeDefinition>();

            // Flag indicating if importing should continue after an input
            // error is detected
            boolean continueOnTableTypeError = false;

            // Step through the extra data
            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
            {
                // Check if this is a table type definition
                if (ancillaryData.getName().startsWith(XTCETags.TABLE_TYPE.getTag()))
                {
                    // Extract the table type information
                    String[] definition = CcddUtilities.splitAndRemoveQuotes(ancillaryData.getValue());

                    // Check if the expected number of inputs is present
                    if ((definition.length - 2) % TableTypeEditorColumnInfo.values().length != 0)
                    {
                        // Create the table type definition, supplying the name
                        // and description
                        TableTypeDefinition tableTypeDefn = new TableTypeDefinition(definition[0],
                                                                                    definition[1]);
                        tableTypeDefns.add(tableTypeDefn);

                        // Step through each column definition (ignoring the
                        // primary key and row index columns)
                        for (int columnNumber = NUM_HIDDEN_COLUMNS, index = 2; index < definition.length; columnNumber++, index += TableTypeEditorColumnInfo.values().length - 1)
                        {
                            // Add the column definition to the table type
                            // definition
                            tableTypeDefn.addColumn(new Object[] {columnNumber,
                                                                  definition[TableTypeEditorColumnInfo.NAME.ordinal() + index - 1],
                                                                  definition[TableTypeEditorColumnInfo.DESCRIPTION.ordinal() + index - 1],
                                                                  definition[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal() + index - 1],
                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.UNIQUE.ordinal() + index - 1]),
                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.REQUIRED.ordinal() + index - 1]),
                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() + index - 1]),
                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() + index - 1])});
                        }
                    }
                    // Check if the user hasn't already elected to ignore table
                    // type errors
                    else if (!continueOnTableTypeError)
                    {
                        // Inform the user that the table type name is
                        // incorrect
                        int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                            "<html><b>Table type '"
                                                                                                    + definition[0]
                                                                                                    + "' definition has missing or extra input(s) in import file '</b>"
                                                                                                    + importFileName
                                                                                                    + "<b>'; continue?",
                                                                                            "Table Type Error",
                                                                                            "Ignore this table type",
                                                                                            "Ignore this and any remaining invalid table types",
                                                                                            "Stop importing");

                        // Check if the Ignore All button was pressed
                        if (buttonSelected == IGNORE_BUTTON)
                        {
                            // Set the flag to ignore subsequent table type
                            // errors
                            continueOnTableTypeError = true;
                        }
                        // Check if the Cancel button was pressed
                        else if (buttonSelected == CANCEL_BUTTON)
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException();
                        }
                    }
                }
                // Check if this is a table type data field definition
                else if (ancillaryData.getName().startsWith(XTCETags.DATA_FIELD.getTag()))
                {
                    // Extract the table type owner of this data field
                    String tableTypeName = ancillaryData.getName().replaceFirst(".*: ", "");

                    // Step through the table type definitions
                    for (TableTypeDefinition tableTypeDefn : tableTypeDefns)
                    {
                        // Check if the table type name matches
                        if (tableTypeName.equals(tableTypeDefn.getTypeName()))
                        {
                            // Get the data field inputs. If not present use a
                            // blank to prevent an error when separating the
                            // inputs
                            String inputs = ancillaryData.getValue() != null
                                                                             ? ancillaryData.getValue()
                                                                             : "";

                            // Parse data field. The values are
                            // comma-separated; however, commas within quotes
                            // are ignored - this allows commas to be included
                            // in the data values
                            String[] fieldDefn = CcddUtilities.splitAndRemoveQuotes("\""
                                                                                    + CcddFieldHandler.getFieldTypeName(tableTypeName)
                                                                                    + "\","
                                                                                    + inputs);

                            // Check if the expected number of inputs is
                            // present
                            if (fieldDefn.length == FieldEditorColumnInfo.values().length + 1)
                            {
                                // Add the data field definition to the table
                                tableTypeDefn.addDataField(fieldDefn);
                            }
                            // Check that the user hasn't elected to ignore
                            // data field errors
                            else if (!continueOnTableTypeError)
                            {
                                // Inform the user that the data field name
                                // inputs are incorrect
                                int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                    "<html><b>Table type '</b>"
                                                                                                            + tableTypeName
                                                                                                            + "<b>' has missing or extra data field "
                                                                                                            + "input(s) in import file '</b>"
                                                                                                            + importFileName
                                                                                                            + "<b>'; continue?",
                                                                                                    "Data Field Error",
                                                                                                    "Ignore this invalid data field",
                                                                                                    "Ignore this and any remaining invalid data fields",
                                                                                                    "Stop importing");

                                // Check if the Ignore All button was pressed
                                if (buttonSelected == IGNORE_BUTTON)
                                {
                                    // Set the flag to ignore subsequent data
                                    // field errors
                                    continueOnTableTypeError = true;
                                }
                                // Check if the Cancel button was pressed
                                else if (buttonSelected == CANCEL_BUTTON)
                                {
                                    // No error message is provided since the
                                    // user chose this action
                                    throw new CCDDException();
                                }
                            }
                        }
                    }
                }
            }

            // Add the table type if it's new or match it to an existing one
            // with the same name if the type definitions are the same
            String badDefn = tableTypeHandler.updateTableTypes(tableTypeDefns,
                                                               fieldHandler);

            // Check if a table type isn't new and doesn't match an existing
            // one with the same name
            if (badDefn != null)
            {
                throw new CCDDException("Imported table type '"
                                        + badDefn
                                        + "' doesn't match the existing definition");
            }
        }
    }

    /**************************************************************************
     * Extract the data type definitions, if present, from the imported root
     * system
     *
     * @param spaceSystem
     *            top-level space system
     *
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void importDataTypeDefinitions(SpaceSystemType spaceSystem,
                                           String importFileName) throws CCDDException
    {
        List<String[]> dataTypeDefns = new ArrayList<String[]>();

        // Get the data type definitions
        AncillaryDataSet ancillarySet = spaceSystem.getAncillaryDataSet();

        // Check if any extra data exists
        if (ancillarySet != null)
        {
            // Flag indicating if importing should continue after an input
            // error is detected
            boolean continueOnDataTypeError = false;

            // Step through the extra data
            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
            {
                // Check if this is a data type definition
                if (ancillaryData.getName().startsWith(XTCETags.DATA_TYPE.getTag()))
                {
                    // Extract the data type information
                    String[] definition = ancillaryData.getValue().split(",");

                    // Check if the number of expected inputs is present
                    if (definition.length == DataTypesColumn.values().length - 1)
                    {
                        // Add the data type definition to the list (add a
                        // blank for the OID column)
                        dataTypeDefns.add(new String[] {definition[DataTypesColumn.USER_NAME.ordinal()],
                                                        definition[DataTypesColumn.C_NAME.ordinal()],
                                                        definition[DataTypesColumn.SIZE.ordinal()],
                                                        definition[DataTypesColumn.BASE_TYPE.ordinal()],
                                                        ""});
                    }
                    // Incorrect number of inputs. Check if the user hasn't
                    // already elected to ignore data type errors
                    else if (!continueOnDataTypeError)
                    {
                        // Inform the user that the data type inputs are
                        // incorrect
                        int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                            "<html><b>Missing or extra data type definition "
                                                                                                    + "input(s) in import file '</b>"
                                                                                                    + importFileName
                                                                                                    + "<b>'; continue?",
                                                                                            "Data Type Error",
                                                                                            "Ignore this data type",
                                                                                            "Ignore this and any remaining invalid data types",
                                                                                            "Stop importing");

                        // Check if the Ignore All button was pressed
                        if (buttonSelected == IGNORE_BUTTON)
                        {
                            // Set the flag to ignore subsequent data type
                            // errors
                            continueOnDataTypeError = true;
                        }
                        // Check if the Cancel button was pressed
                        else if (buttonSelected == CANCEL_BUTTON)
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException();
                        }
                    }
                }
            }

            // Add the data type if it's new or match it to an existing one
            // with the same name if the type definitions are the same
            String badDefn = dataTypeHandler.updateDataTypes(dataTypeDefns);

            // Check if a data type isn't new and doesn't match an existing one
            // with the same name
            if (badDefn != null)
            {
                throw new CCDDException("data type '"
                                        + badDefn
                                        + "' already exists and doesn't match the import definition");
            }
        }
    }

    /**************************************************************************
     * Extract the macro definitions, if present, from the imported root system
     *
     * @param spaceSystem
     *            top-level space system
     *
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void importMacroDefinitions(SpaceSystemType spaceSystem,
                                        String importFileName) throws CCDDException
    {
        List<String[]> macroDefns = new ArrayList<String[]>();

        // Get the macro definitions
        AncillaryDataSet ancillarySet = spaceSystem.getAncillaryDataSet();

        // Check if any extra data exists
        if (ancillarySet != null)
        {
            // Flag indicating if importing should continue after an input
            // error is detected
            boolean continueOnMacroError = false;

            // Step through the extra data
            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
            {
                // Check if this is a macro definition
                if (ancillaryData.getName().startsWith(XTCETags.MACRO.getTag()))
                {
                    // Extract the macro information
                    String[] definition = ancillaryData.getValue().split(",", 2);

                    // Check if the number of expected inputs is present
                    if (definition.length == MacrosColumn.values().length - 1)
                    {
                        // Add the macro definition to the list (add a blank
                        // for the OID column)
                        macroDefns.add(new String[] {definition[MacrosColumn.MACRO_NAME.ordinal()],
                                                     definition[MacrosColumn.VALUE.ordinal()],
                                                     ""});
                    }
                    // Incorrect number of inputs. Check if the user hasn't
                    // already elected to ignore macro errors
                    else if (!continueOnMacroError)
                    {
                        // Inform the user that the macro inputs are incorrect
                        int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                            "<html><b>Missing or extra macro definition "
                                                                                                    + "input(s) in import file '</b>"
                                                                                                    + importFileName
                                                                                                    + "<b>'; continue?",
                                                                                            "Macro Error",
                                                                                            "Ignore this macro",
                                                                                            "Ignore this and any remaining invalid macros",
                                                                                            "Stop importing");

                        // Check if the Ignore All button was pressed
                        if (buttonSelected == IGNORE_BUTTON)
                        {
                            // Set the flag to ignore subsequent macro errors
                            continueOnMacroError = true;
                        }
                        // Check if the Cancel button was pressed
                        else if (buttonSelected == CANCEL_BUTTON)
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException();
                        }
                    }
                }
            }

            // Add the macro if it's new or match it to an existing one with
            // the same name if the values are the same
            String badDefn = macroHandler.updateMacros(macroDefns);

            // Check if a macro isn't new and doesn't match an existing one
            // with the same name
            if (badDefn != null)
            {
                throw new CCDDException("macro '"
                                        + badDefn
                                        + "' already exists and doesn't match the import definition");
            }
        }
    }

    /**************************************************************************
     * Extract the reserved message ID definitions, if present, from the
     * imported root system
     *
     * @param spaceSystem
     *            top-level space system
     *
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void importReservedMsgIDDefinitions(SpaceSystemType spaceSystem,
                                                String importFileName) throws CCDDException
    {
        List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

        // Get the macro definitions
        AncillaryDataSet ancillarySet = spaceSystem.getAncillaryDataSet();

        // Check if any extra data exists
        if (ancillarySet != null)
        {
            // Flag indicating if importing should continue after an input
            // error is detected
            boolean continueOnReservedMsgIDError = false;

            // Step through the extra data
            for (AncillaryData ancillaryData : ancillarySet.getAncillaryData())
            {
                // Check if this is a reserved message ID definition
                if (ancillaryData.getName().startsWith(XTCETags.RESERVED_MSG_ID.getTag()))
                {
                    // Extract the reserved message ID information
                    String[] definition = ancillaryData.getValue().split(",", 2);

                    // Check if the number of expected inputs is present
                    if (definition.length == ReservedMsgIDsColumn.values().length - 1)
                    {
                        // Add the reserved message ID definition to the list
                        // (add a blank for the OID column)
                        reservedMsgIDDefns.add(new String[] {definition[ReservedMsgIDsColumn.MSG_ID.ordinal()],
                                                             definition[ReservedMsgIDsColumn.DESCRIPTION.ordinal()],
                                                             ""});
                    }
                    // Incorrect number of inputs. Check if the user hasn't
                    // already elected to ignore reserved message ID errors
                    else if (!continueOnReservedMsgIDError)
                    {
                        // Inform the user that the reserved message ID inputs
                        // are incorrect
                        int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                            "<html><b>Missing or extra reserved message ID "
                                                                                                    + "definition input(s) in import file '</b>"
                                                                                                    + importFileName
                                                                                                    + "<b>'; continue?",
                                                                                            "Reserved Message ID Error",
                                                                                            "Ignore this reserved message ID",
                                                                                            "Ignore this and any remaining invalid reserved message IDs",
                                                                                            "Stop importing");

                        // Check if the Ignore All button was pressed
                        if (buttonSelected == IGNORE_BUTTON)
                        {
                            // Set the flag to ignore subsequent reserved
                            // message ID errors
                            continueOnReservedMsgIDError = true;
                        }
                        // Check if the Cancel button was pressed
                        else if (buttonSelected == CANCEL_BUTTON)
                        {
                            // No error message is provided since the user
                            // chose this action
                            throw new CCDDException();
                        }
                    }
                }
            }

            // Add the reserved message ID definition if it's new
            rsvMsgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);
        }
    }

    /**************************************************************************
     * Get a list of the referenced table type definitions
     *
     * @return List of the table type definitions
     *************************************************************************/
    private List<String[]> exportTableTypeDefinitions()
    {
        List<String[]> tableTypeDefinitions = new ArrayList<String[]>();

        // Step through each referenced table type
        for (String refTableType : referencedTableTypes)
        {
            // Get the table type definition
            TypeDefinition tableTypeDefn = tableTypeHandler.getTypeDefinition(refTableType);

            // Check if the table type exists
            if (tableTypeDefn != null)
            {
                // Create the type definition string beginning with the name,
                // description, and number of columns
                StringBuilder definition = new StringBuilder("\""
                                                             + tableTypeDefn.getName()
                                                             + "\",\""
                                                             + tableTypeDefn.getDescription()
                                                             + "\"");

                // Step through each column definition in the table type,
                // skipping
                // the primary key and row index columns
                for (int column = NUM_HIDDEN_COLUMNS; column < tableTypeDefn.getColumnCountDatabase(); column++)
                {
                    // Add the column information to the definition
                    definition.append(",\""
                                      + tableTypeDefn.getColumnNamesUser()[column]
                                      + "\",\""
                                      + tableTypeDefn.getColumnToolTips()[column]
                                      + "\",\""
                                      + tableTypeDefn.getInputTypes()[column].getInputName()
                                      + "\",\""
                                      + tableTypeDefn.isRowValueUnique()[column]
                                      + "\",\""
                                      + tableTypeDefn.isRequired()[column]
                                      + "\",\""
                                      + tableTypeDefn.isStructureAllowed()[column]
                                      + "\",\""
                                      + tableTypeDefn.isPointerAllowed()[column]
                                      + "\"");
                }

                // Add the table type definition to the list
                tableTypeDefinitions.add(new String[] {XTCETags.TABLE_TYPE.getTag(),
                                                       definition.toString()});

                // Build the data field information for this table type and add
                // it to the list
                fieldHandler.buildFieldInformation(CcddFieldHandler.getFieldTypeName(tableTypeDefn.getName()));

                // Store any table type data field names and values
                storeDataFields(fieldHandler.getFieldInformation(),
                                tableTypeDefn.getName(),
                                tableTypeDefinitions);
            }
        }

        return tableTypeDefinitions;
    }

    /**************************************************************************
     * Get a list of the referenced data type definitions
     *
     * @return List of the data type definitions
     *************************************************************************/
    private List<String[]> exportDataTypeDefinitions()
    {
        List<String[]> dataTypeDefinitions = new ArrayList<String[]>();

        // Step through each referenced primitive data type
        for (String refDataType : referencedDataTypes)
        {
            // Get the data type information
            String[] dataType = dataTypeHandler.getDataTypeInfo(refDataType);

            // Check if the data type exists
            if (dataType != null)
            {
                // Add the data type definition to the list
                dataTypeDefinitions.add(new String[] {XTCETags.DATA_TYPE.getTag(),
                                                      dataType[DataTypesColumn.USER_NAME.ordinal()]
                                                                                   + ","
                                                                                   + dataType[DataTypesColumn.C_NAME.ordinal()]
                                                                                   + ","
                                                                                   + dataType[DataTypesColumn.SIZE.ordinal()]
                                                                                   + ","
                                                                                   + dataType[DataTypesColumn.BASE_TYPE.ordinal()]});
            }
        }

        return dataTypeDefinitions;
    }

    /**************************************************************************
     * Get a list of the referenced macro definitions
     *
     * @return List of the macro definitions
     *************************************************************************/
    private List<String[]> exportMacroDefinitions()
    {
        List<String[]> macros = new ArrayList<String[]>();

        // Step through each referenced macro
        for (String refMacro : referencedMacros)
        {
            // Get the macro definition
            String macroValue = macroHandler.getMacroValue(refMacro);

            // Check if the macro exists
            if (macroValue != null)
            {
                // Add the macro definition to the list
                macros.add(new String[] {XTCETags.MACRO.getTag(),
                                         refMacro + "," + macroValue});
            }
        }

        return macros;
    }

    /**************************************************************************
     * Get a list of the reserved message ID definitions
     *
     * @return List of the reserved message ID definitions
     *************************************************************************/
    private List<String[]> exportReservedMsgIDDefinitions()
    {
        List<String[]> reservedMsgIDs = new ArrayList<String[]>();

        // Step through each reserved message ID definition
        for (String[] reservedMsgIDDefn : rsvMsgIDHandler.getReservedMsgIDData())
        {
            // Add the reserved message ID definition to the list
            reservedMsgIDs.add(new String[] {XTCETags.RESERVED_MSG_ID.getTag(),
                                             reservedMsgIDDefn[ReservedMsgIDsColumn.MSG_ID.ordinal()]
                                                                                + ","
                                                                                + reservedMsgIDDefn[ReservedMsgIDsColumn.DESCRIPTION.ordinal()]});
        }

        return reservedMsgIDs;
    }

    /**************************************************************************
     * Get a list of variable paths
     *
     * @return List of the variable paths
     *************************************************************************/
    private List<String[]> exportVariablePaths()
    {
        List<String[]> variablePaths = new ArrayList<String[]>();

        // Step through each referenced variable path
        for (String[] variablePath : referencedVariablePaths)
        {
            // Add the variable path to the list
            variablePaths.add(new String[] {XTCETags.VARIABLE_PATH.getTag(),
                                            "\""
                                                                             + variablePath[0]
                                                                             + "\",\""
                                                                             + variablePath[1]
                                                                             + "\""});
        }

        return variablePaths;
    }

    /**************************************************************************
     * Build the space systems
     *
     * @param node
     *            current tree node
     *
     * @param includeVariablePaths
     *            true to include the variable path for each variable in a
     *            structure table, both in application format and using the
     *            user-defined separator characters
     *
     * @param variableHandler
     *            variable handler class reference; null if
     *            includeVariablePaths is false
     *
     * @param separators
     *            string array containing the variable path separator
     *            character(s), show/hide data types flag ('true' or 'false'),
     *            and data type/variable name separator character(s); null if
     *            includeVariablePaths is false
     *
     * @param parentSystem
     *            parent space system for this node
     *************************************************************************/
    private void buildSpaceSystems(String[] tableNames,
                                   boolean includeVariablePaths,
                                   CcddVariableConversionHandler variableHandler,
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
                // Get the table type and from the type get the type
                // definition. The type definition can be a global parameter
                // since if the table represents a structure, then all of its
                // children are also structures, and if the table represents
                // commands or other table type then it is processed within
                // this nest level
                typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());

                // Get the table's basic type - structure, command, or the
                // original table type if not structure or command table
                String tableType = typeDefn.isStructure()
                                                          ? TYPE_STRUCTURE
                                                          : typeDefn.isCommand()
                                                                                 ? TYPE_COMMAND
                                                                                 : tableInfo.getType();

                // Check if the table type is valid
                if (tableType != null)
                {
                    // Check if this type hasn't already been referenced
                    if (!referencedTableTypes.contains(tableInfo.getType()))
                    {
                        // Add the table type to the reference list
                        referencedTableTypes.add(tableInfo.getType());
                    }

                    // Check if the flag is set that indicates macros should be
                    // replaced
                    if (replaceMacros)
                    {
                        // Replace all macro names with their corresponding
                        // values
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
                                // Step through each macro referenced in the
                                // column
                                for (String macro : macroHandler.getReferencedMacros(columnData))
                                {
                                    // Check if this macro asn't already been
                                    // referenced
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

                    // Get the table's system from the system name data field,
                    // if it exists
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

                    // Search the existing space systems for one with this name
                    SpaceSystemType existingSystem = getSpaceSystemByName(systemName);

                    // Set the parent system to the existing system if found,
                    // else create a new space system using the system name
                    // from the table's data field
                    parentSystem = (existingSystem == null)
                                                            ? addSpaceSystem(parentSystem,
                                                                             systemName,
                                                                             null,
                                                                             classification2Attr,
                                                                             validationStatusAttr,
                                                                             versionAttr,
                                                                             null)
                                                            : existingSystem;

                    // Create a list to contain the additional table
                    // information; e.g., the table's type and data field names
                    List<String[]> otherData = new ArrayList<String[]>();

                    // Add the table type to the list of extra data - this is
                    // the actual table type name (i.e., not reduced to its
                    // basic type, such as 'structure' or 'command')
                    otherData.add(new String[] {XTCETags.TABLE_TYPE.getTag(),
                                                tableInfo.getType()});

                    // Store any data field names and values
                    storeDataFields(tableInfo.getFieldHandler().getFieldInformation(),
                                    null,
                                    otherData);

                    // Check if this is a node for a structure table
                    if (tableType.equals(TYPE_STRUCTURE))
                    {
                        // Get the default column indices
                        int varColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                        int typeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                        int sizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                        int bitColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                        int enumColumn = typeDefn.getColumnIndexByInputType(InputDataType.ENUMERATION);
                        int descColumn = typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION);
                        int unitsColumn = typeDefn.getColumnIndexByInputType(InputDataType.UNITS);

                        // Get the variable description column. If the default
                        // structure description column name isn't used then
                        // the first column containing 'description' is
                        // selected

                        // Add the structure
                        parentSystem = addSpaceSystem(parentSystem,
                                                      tableName,
                                                      tableInfo.getDescription(),
                                                      classification3Attr,
                                                      validationStatusAttr,
                                                      versionAttr,
                                                      otherData);

                        // Step through each row in the table
                        for (int row = 0; row < tableInfo.getData().length; row++)
                        {
                            // Add the variable to the data sheet
                            addSpaceSystemParameter(parentSystem,
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

                                // Add the path, in both application and
                                // user-defined formats, to the list to be
                                // output
                                referencedVariablePaths.add(new String[] {variablePath,
                                                                          variableHandler.getFullVariableName(variablePath,
                                                                                                              separators[0],
                                                                                                              Boolean.parseBoolean(separators[1]),
                                                                                                              separators[2])});
                            }
                        }
                    }
                    // Not a structure table node; i.e., it's a command or
                    // user-defined table type
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
                                                          otherData);

                            // Add the command(s) from this table to the space
                            // system
                            addSpaceSystemCommands(parentSystem, tableInfo);
                        }
                        // Not a command (or structure) table; i.e., it's a
                        // user-defined table type
                        else
                        {
                            // Store this table's data as ancillary data for
                            // the current space system. Step through each row
                            // of the table
                            for (int row = 0; row < tableInfo.getData().length; row++)
                            {
                                // Step through each column in the row
                                for (int column = 0; column < tableInfo.getData()[row].length; column++)
                                {
                                    // Check that this isn't the primary key or
                                    // row index column, and that the column
                                    // value isn't blank
                                    if (column != DefaultColumn.PRIMARY_KEY.ordinal()
                                        && column != DefaultColumn.ROW_INDEX.ordinal()
                                        && !tableInfo.getData()[row][column].isEmpty())
                                    {
                                        // Store the data column name and value
                                        otherData.add(new String[] {XTCETags.COLUMN.getColumnTag(typeDefn.getColumnNamesUser()[column],
                                                                                                 row),
                                                                    tableInfo.getData()[row][column]});
                                    }
                                }
                            }

                            // Add the user-defined table to the space system
                            parentSystem = addSpaceSystem(parentSystem,
                                                          tableName,
                                                          tableInfo.getDescription(),
                                                          classification3Attr,
                                                          validationStatusAttr,
                                                          versionAttr,
                                                          otherData);
                        }
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Store the specified table's data field names and values into the
     * supplied list
     *
     * @param fieldInformation
     *            list containing data field information
     *
     * @param identifier
     *            string to append to the data field tag used to identify the
     *            table type to which a data field belongs; null if the data
     *            type doesn't belong to a table type
     *
     * @param fieldData
     *            reference to the list in which the data field names and
     *            values are to be stored
     *************************************************************************/
    private void storeDataFields(List<FieldInformation> fieldInformation,
                                 String identifier,
                                 List<String[]> fieldData)
    {
        // Step through the command table's data field information
        for (FieldInformation field : fieldInformation)
        {
            // Store the data field information
            fieldData.add(new String[] {XTCETags.DATA_FIELD.getTag()
                                        + (identifier == null
                                                              ? ""
                                                              : identifier),
                                        "\""
                                                                             + field.getFieldName()
                                                                             + "\",\""
                                                                             + field.getDescription()
                                                                             + "\",\""
                                                                             + field.getSize()
                                                                             + "\",\""
                                                                             + field.getInputType().getInputName()
                                                                             + "\",\""
                                                                             + field.isRequired()
                                                                             + "\",\""
                                                                             + field.getApplicabilityType().getApplicabilityName()
                                                                             + "\",\""
                                                                             + field.getValue()
                                                                             + "\""});
        }
    }

    /**************************************************************************
     * Create a new space system as a child of the specified space system. If
     * the specified system is null then this is the root space system
     *
     * @param system
     *            parent space system for the new system; null for the root
     *            space system
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
     * @param otherAttrs
     *            list containing other attribute data in the format [attribute
     *            name][attribute value]
     *
     * @return Reference to the new space system
     *************************************************************************/
    private SpaceSystemType addSpaceSystem(SpaceSystemType system,
                                           String subsystemName,
                                           String shortDescription,
                                           String classification,
                                           String validationStatus,
                                           String version,
                                           List<String[]> otherAttrs)
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

        // Check if other attributes exist
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            // Store the other data as ancillary data
            subsystem.setAncillaryDataSet(storeOtherAttributes(otherAttrs));
        }

        return subsystem;
    }

    /**************************************************************************
     * Get the reference to the space system with the specified name, starting
     * at the root space system
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @return Reference to the space system with the same name as the search
     *         name; null if no space system name matches the search name
     *************************************************************************/
    private SpaceSystemType getSpaceSystemByName(String systemName)
    {
        // Search the space system hierarchy, beginning at the root system
        return searchSpaceSystemsForName(systemName,
                                         project.getValue(),
                                         null);
    }

    /**************************************************************************
     * Recursively search through the space system tree for the space system
     * with the same name as the search name
     *
     * @param systemName
     *            name to search for within the space system hierarchy
     *
     * @param system
     *            current space system to check
     *
     * @param foundSystem
     *            space system that matches the search name; null if no match
     *            has been found
     *
     * @return Reference to the space system with the same name as the search
     *         name; null if no space system name matches the search name
     *************************************************************************/
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
                    // Search the subsystem (and its subsystems, if any) for a
                    // match
                    foundSystem = searchSpaceSystemsForName(systemName, sys, null);

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

    /**************************************************************************
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
     *************************************************************************/
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

    /**************************************************************************
     * Add a variable to the specified space system
     *
     * @param spaceSystem
     *            parent space system for this node
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
     * @param enumColumn
     *            first enumeration column index; -1 if none exists
     *
     * @param unitsColumn
     *            units column index; -1 if none exists
     *
     * @param descColumn
     *            description column index; -1 if none exists
     *
     * @param dataType
     *            parameter data type
     *
     * @param variableName
     *            variable name
     *************************************************************************/
    private void addSpaceSystemParameter(SpaceSystemType spaceSystem,
                                         TableInformation tableInfo,
                                         int varColumn,
                                         int typeColumn,
                                         int sizeColumn,
                                         int bitColumn,
                                         int enumColumn,
                                         int unitsColumn,
                                         int descColumn,
                                         String dataType,
                                         String variableName)
    {
        // Set the system path to the table's path
        String systemPath = tableInfo.getTablePath();

        // Initialize the parameter attributes
        String bitLength = null;
        String enumeration = null;
        String units = null;
        String description = null;
        int stringSize = 1;
        List<String[]> otherCols = new ArrayList<String[]>();

        // Separate the variable name and bit length (if present) and store the
        // variable name
        String[] nameAndBit = variableName.split(":");
        variableName = nameAndBit[0];

        // Check if a bit length is present
        if (nameAndBit.length == 2)
        {
            // Store the bit length
            bitLength = nameAndBit[1];
        }

        // Get the index of the row in the table for this variable
        int row = typeDefn.getRowIndexByColumnValue(tableInfo.getData(),
                                                    variableName,
                                                    varColumn);

        // Check if the data type is a string and if the array size column
        // isn't empty
        if (dataTypeHandler.isString(tableInfo.getData()[row][typeColumn])
            && !tableInfo.getData()[row][sizeColumn].isEmpty())
        {
            int defnRow = row;

            // Check if the variable name is an array member
            while (tableInfo.getData()[defnRow][varColumn].endsWith("]"))
            {
                // Step back through the rows until the array definition is
                // located
                defnRow--;
            }

            // Get the size of the string, which is the last array size value
            // in the string array's definition row
            stringSize = Integer.valueOf(tableInfo.getData()[defnRow][sizeColumn].replaceAll("^.*(\\d+)$", "$1"));
        }

        // Step through each column in the row
        for (int column = 0; column < tableInfo.getData()[row].length; column++)
        {
            // Check that this is not the primary key, row index, variable or
            // name column, or the bit length column and the variable has no
            // bit length, and that a value exists in the column
            if (((column != DefaultColumn.PRIMARY_KEY.ordinal()
                  && column != DefaultColumn.ROW_INDEX.ordinal()
                  && column != varColumn)
                 || (column == bitColumn && bitLength != null))
                && !tableInfo.getData()[row][column].isEmpty())
            {
                // Store the column value size. This is treated as ancillary
                // data
                otherCols.add(new String[] {XTCETags.COLUMN.getColumnTag(typeDefn.getColumnNamesUser()[column],
                                                                         row),
                                            tableInfo.getData()[row][column]});

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
                     systemPath,
                     variableName,
                     dataType,
                     (typeColumn == -1
                                       ? null
                                       : typeDefn.getColumnNamesUser()[typeColumn]),
                     enumeration,
                     units,
                     description,
                     stringSize,
                     otherCols);
    }

    /**************************************************************************
     * Add the command(s) from a table to the specified space system
     *
     * @param spaceSystem
     *            parent space system for this node
     *
     * @param tableInfo
     *            TableInformation reference for the current node
     *************************************************************************/
    private void addSpaceSystemCommands(SpaceSystemType spaceSystem,
                                        TableInformation tableInfo)
    {
        // Get the list containing command argument name, data type,
        // enumeration, minimum, maximum, and other associated column indices
        // for each argument grouping
        List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

        // Step through each row in the table
        for (String[] rowData : tableInfo.getData())
        {
            // Initialize the command attributes and argument number list
            String commandName = null;
            String commandDescription = null;
            List<String> argumentNames = new ArrayList<String>();
            List<String[]> otherCols = new ArrayList<String[]>();

            // Create an array of flags to indicate if the column is a command
            // argument that has been processed
            boolean[] isCmdArg = new boolean[rowData.length];

            // Step through each column in the row
            for (int colA = NUM_HIDDEN_COLUMNS; colA < rowData.length; colA++)
            {
                // Check that this isn't the column containing the table's
                // primary key or row index, and that the column value isn't
                // blank
                if (!rowData[colA].isEmpty())
                {
                    // Check if this is the command name column
                    if (typeDefn.getColumnNamesUser()[colA].equalsIgnoreCase(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME)))
                    {
                        // Store the command name
                        commandName = rowData[colA];
                    }
                    // Not the command name column; check for other overall
                    // command and command argument columns
                    else
                    {
                        // Initialize the command argument attributes
                        String argName = null;
                        String dataType = null;
                        String enumeration = null;
                        String units = null;
                        String description = null;
                        List<String[]> otherArgCols = new ArrayList<String[]>();

                        // Step through each command argument column grouping
                        for (AssociatedColumns cmdArg : commandArguments)
                        {
                            // Check if this is the command argument name
                            // column
                            if (colA == cmdArg.getName())
                            {
                                // Store the command argument name
                                argName = rowData[colA];

                                // Set the flag indicating the column is a
                                // command argument
                                isCmdArg[colA] = true;

                                // Step through each column in the row again to
                                // look for the remaining members of this
                                // argument grouping
                                for (int colB = NUM_HIDDEN_COLUMNS; colB < rowData.length; colB++)
                                {
                                    // Check if a value is present
                                    if (!rowData[colB].isEmpty())
                                    {
                                        // Get the column name
                                        String colName = typeDefn.getColumnNamesUser()[colB];

                                        // Store the column name and value.
                                        // This is treated as ancillary data
                                        // for this command argument set
                                        otherArgCols.add(new String[] {XTCETags.COLUMN.getColumnTag(colName,
                                                                                                    Integer.valueOf(rowData[DefaultColumn.ROW_INDEX.ordinal()]) - 1),
                                                                       rowData[colB]});

                                        // Check if this is the command
                                        // argument data type column
                                        if (colB == cmdArg.getDataType())
                                        {
                                            // Store the command argument data
                                            // type
                                            dataType = rowData[colB];

                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // Check if this is the command
                                        // argument enumeration column
                                        else if (colB == cmdArg.getEnumeration())
                                        {
                                            // Store the command argument
                                            // enumeration
                                            enumeration = rowData[colB];

                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // Check if this is the command
                                        // argument description column
                                        else if (colB == cmdArg.getDescription())
                                        {
                                            // Store the command argument
                                            // description
                                            description = rowData[colB];

                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // Check if this is the command
                                        // argument units column
                                        else if (colB == cmdArg.getUnits())
                                        {
                                            // Store the command argument units
                                            units = rowData[colB];

                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // Check if this is the command
                                        // argument minimum or maximum column
                                        else if (colB == cmdArg.getMinimum()
                                                 || colB == cmdArg.getMaximum())
                                        {
                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // The column isn't associated with
                                        // this command argument
                                        else if (!cmdArg.getOther().contains(colB))
                                        {
                                            // Remove the column information
                                            otherArgCols.remove(otherArgCols.size() - 1);
                                        }
                                    }
                                }

                                // Check if the command argument has the
                                // minimum parameters required: a name and data
                                // type
                                if (argName != null
                                    && !argName.isEmpty()
                                    && dataType != null)
                                {
                                    // Add the name to the argument name list
                                    argumentNames.add(argName);

                                    // Add the command to the command space
                                    // system
                                    addCommandArgument(spaceSystem,
                                                       commandName,
                                                       argName,
                                                       dataType,
                                                       enumeration,
                                                       units,
                                                       description,
                                                       otherArgCols);
                                }

                                // Stop searching since a match was found
                                break;
                            }
                        }
                    }
                }
            }

            // Step through each column in the row again in order to assign the
            // overall command information
            for (int col = NUM_HIDDEN_COLUMNS; col < rowData.length; col++)
            {
                // Check that this is not one of the command argument columns
                // and that the column value isn't blank. This prevents adding
                // command arguments to the overall command information
                if (!isCmdArg[col] && !rowData[col].isEmpty())
                {
                    // Get the column name
                    String colName = typeDefn.getColumnNamesUser()[col];

                    // Check if this column is for the command description
                    if (col == typeDefn.getColumnIndexByInputType(InputDataType.DESCRIPTION))
                    {
                        // Store the command description
                        commandDescription = rowData[col];
                    }

                    // Store the column name and value. This is treated as
                    // ancillary data for this command
                    otherCols.add(new String[] {XTCETags.COLUMN.getColumnTag(colName,
                                                                             Integer.valueOf(rowData[DefaultColumn.ROW_INDEX.ordinal()]) - 1),
                                                rowData[col]});
                }
            }

            // Check if the command name exists
            if (commandName != null)
            {
                // Add the command metadata set information
                addCommand(spaceSystem,
                           commandName,
                           argumentNames,
                           commandDescription,
                           otherCols);
            }
        }
    }

    /**************************************************************************
     * Create the space system telemetry metadata
     *
     * @param system
     *            space system
     *************************************************************************/
    private void createTelemetryMetadata(SpaceSystemType system)
    {
        system.setTelemetryMetaData(factory.createTelemetryMetaDataType());
        system.getTelemetryMetaData().setParameterSet(factory.createParameterSetType());
        system.getTelemetryMetaData().setParameterTypeSet(factory.createParameterTypeSetType());
    }

    /**************************************************************************
     * Create the space system command metadata
     *
     * @param system
     *            space system
     *************************************************************************/
    private void createCommandMetadata(SpaceSystemType system)
    {
        system.setCommandMetaData(factory.createCommandMetaDataType());
        system.getCommandMetaData().setArgumentTypeSet(factory.createArgumentTypeSetType());
        system.getCommandMetaData().setMetaCommandSet(factory.createCommandMetaDataTypeMetaCommandSet());
    }

    /**************************************************************************
     * Add a telemetry parameter to the telemetry metadata
     *
     * @param system
     *            space system
     *
     * @param systemPath
     *            system path in the format <project name>.<system
     *            name>.<structure path>.<primitive data Type>.<variable name>
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
     * @param enumeration
     *            enumeration in the format <enum label>|<enum
     *            value>[|...][,...]; null to not specify
     *
     * @param units
     *            parameter units
     *
     * @param shortDescription
     *            short description of the parameter
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a
     *            string or character
     *
     * @param otherAttrs
     *            list containing other attribute data in the format [attribute
     *            name][attribute value]
     *************************************************************************/
    private void addParameter(SpaceSystemType system,
                              String systemPath,
                              String parameterName,
                              String dataType,
                              String dataTypeColumn,
                              String enumeration,
                              String units,
                              String shortDescription,
                              int stringSize,
                              List<String[]> otherAttrs)
    {
        // Check if this system doesn't yet have its telemetry metadata created
        if (system.getTelemetryMetaData() == null)
        {
            // Create the telemetry metadata
            createTelemetryMetadata(system);
        }

        // Check if a data type is provided is a primitive type. If none is
        // provided then no entry for this parameter appears under the
        // ParameterTypeSet, but it will appear under the ParameterSet
        if (dataType != null)
        {
            // Check if the data type provided is a primitive type
            if (dataTypeHandler.isPrimitive(dataType))
            {
                // Set the parameter's data type information
                NameDescriptionType type = setDataType(system,
                                                       parameterName,
                                                       dataType,
                                                       enumeration,
                                                       units,
                                                       shortDescription,
                                                       stringSize);
                system.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(type);
            }
        }

        ParameterSetType parameterSet = system.getTelemetryMetaData().getParameterSet();
        Parameter parameter = factory.createParameterSetTypeParameter();
        parameter.setName(parameterName);
        parameter.setParameterTypeRef(parameterName);

        ParameterPropertiesType properties = factory.createParameterPropertiesType();
        properties.setSystemName(systemPath);
        parameter.setParameterProperties(properties);
        parameterSet.getParameterOrParameterRef().add(parameter);

        // Check if other attribute data is provided
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            // Store the other data as ancillary data
            parameter.setAncillaryDataSet(storeOtherAttributes(otherAttrs));
        }
    }

    /**************************************************************************
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
     * @param shortDescription
     *            short description of the command
     *
     * @param otherAttrs
     *            list containing other attribute data in the format [attribute
     *            name][attribute value]
     *************************************************************************/
    private void addCommand(SpaceSystemType system,
                            String commandName,
                            List<String> argumentNames,
                            String shortDescription,
                            List<String[]> otherAttrs)
    {
        // Check if this system doesn't yet have its command metadata created
        if (system.getCommandMetaData() == null)
        {
            // Create the command metadata
            createCommandMetadata(system);
        }

        MetaCommandSet commandSet = system.getCommandMetaData().getMetaCommandSet();
        MetaCommandType cmd = factory.createMetaCommandType();

        // Set the command name attribute
        cmd.setName(commandName);

        // Check is a command description exists
        if (shortDescription != null)
        {
            // Set the command description attribute
            cmd.setShortDescription(shortDescription);
        }

        // Set the command's system name attribute
        cmd.setSystemName(system.getName());

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

            cmd.setArgumentList(argList);
        }

        commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand().add(cmd);

        // Check if other attributes exist
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            // Store the other data as ancillary data
            cmd.setAncillaryDataSet(storeOtherAttributes(otherAttrs));
        }
    }

    /**************************************************************************
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
     * @param otherArgCols
     *            list of string arrays containing other argument column data
     *            in the format [column name][data value]
     *************************************************************************/
    private void addCommandArgument(SpaceSystemType system,
                                    String commandName,
                                    String argumentName,
                                    String dataType,
                                    String enumeration,
                                    String units,
                                    String shortDescription,
                                    List<String[]> otherArgCols)
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
                                                   otherArgCols);
        ArgumentTypeSetType argument = system.getCommandMetaData().getArgumentTypeSet();
        argument.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(type);
    }

    /**************************************************************************
     * Convert the primitive data type into the XTCE equivalent
     *
     * @param dataType
     *            data type
     *
     * @return XTCE data type corresponding to the specified primitive data
     *         type; null if no match
     *************************************************************************/
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

    /**************************************************************************
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
     * @param enumeration
     *            enumeration in the format <enum label>|<enum
     *            value>[|...][,...]; null to not specify
     *
     * @param units
     *            parameter units; null to not specify
     *
     * @param shortDescription
     *            short description of the parameter; null to not specify
     *
     * @param stringSize
     *            size, in characters, of a string parameter; ignored if not a
     *            string or character
     *
     * @return Parameter description of the type corresponding to the primitive
     *         data type with the specified attributes set
     *************************************************************************/
    private NameDescriptionType setDataType(SpaceSystemType system,
                                            String parameterName,
                                            String dataType,
                                            String enumeration,
                                            String units,
                                            String shortDescription,
                                            int stringSize)
    {
        BaseDataType parameterDescription = null;

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
                // Create an enumeration type and enumeration list, and add any
                // extra enumeration parameters as ancillary data
                parameterDescription = factory.createParameterTypeSetTypeEnumeratedParameterType();
                EnumerationList enumList = createEnumerationList(system, enumeration);

                // Set the integer encoding (the only encoding available for an
                // enumeration) and the size in bits
                IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));

                // Check if the data type is an unsigned integer
                if (dataTypeHandler.isUnsignedInt(dataType))
                {
                    // Set the encoding type to indicate an unsigned integer
                    intEncodingType.setEncoding("unsigned");
                }

                ((EnumeratedParameterType) parameterDescription).setIntegerDataEncoding(intEncodingType);

                // Set the enumeration list and units attributes
                ((EnumeratedParameterType) parameterDescription).setEnumerationList(enumList);
                ((EnumeratedParameterType) parameterDescription).setUnitSet(unitSet);
            }
            // Not an enumeration
            else
            {
                switch (xtceDataType)
                {
                    case INTEGER:
                        // Create an integer parameter and set its attributes
                        parameterDescription = factory.createParameterTypeSetTypeIntegerParameterType();
                        ((IntegerParameterType) parameterDescription).setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        ((IntegerParameterType) parameterDescription).setUnitSet(unitSet);
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(dataType))
                        {
                            // Set the encoding type to indicate an unsigned
                            // integer
                            intEncodingType.setEncoding("unsigned");
                        }

                        ((IntegerParameterType) parameterDescription).setIntegerDataEncoding(intEncodingType);
                        break;

                    case FLOAT:
                        // Create a float parameter and set its attributes
                        parameterDescription = factory.createParameterTypeSetTypeFloatParameterType();
                        ((FloatParameterType) parameterDescription).setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));
                        ((FloatParameterType) parameterDescription).setUnitSet(unitSet);
                        FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                        ((FloatParameterType) parameterDescription).setFloatDataEncoding(floatEncodingType);
                        break;

                    case STRING:
                        // Create a string parameter and set its attributes
                        parameterDescription = factory.createParameterTypeSetTypeStringParameterType();
                        ((StringParameterType) parameterDescription).setUnitSet(unitSet);
                        StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();
                        ((StringParameterType) parameterDescription).setStringDataEncoding(stringEncodingType);
                        ((StringParameterType) parameterDescription).setCharacterWidth(BigInteger.valueOf(stringSize));
                        break;
                }
            }

            // Set the parameter name attribute
            parameterDescription.setName(parameterName);

            // Check is a description exists
            if (shortDescription != null)
            {
                // Set the description attribute
                parameterDescription.setShortDescription(shortDescription);
            }
        }

        // Check if this data type hasn't already been referenced
        if (!referencedDataTypes.contains(dataType))
        {
            // Add the data type to the reference list
            referencedDataTypes.add(dataType);
        }

        return parameterDescription;
    }

    /**************************************************************************
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
     *            command enumeration in the format <enum label>|<enum
     *            value>[|...][,...]; null to not specify
     *
     * @param units
     *            command argument units; null to not specify
     *
     * @param shortDescription
     *            short description of the parameter; null to not specify
     *
     * @param otherArgCols
     *            list of string arrays containing other argument column data
     *            in the format [column name][data value]
     *
     * @return Command description of the type corresponding to the primitive
     *         data type with the specified attributes set
     *************************************************************************/
    private NameDescriptionType setArgumentType(SpaceSystemType system,
                                                String commandName,
                                                String argumentName,
                                                String argumentType,
                                                String enumeration,
                                                String units,
                                                String shortDescription,
                                                List<String[]> otherArgCols)
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
                // Create an enumeration type and enumeration list, and add any
                // extra enumeration parameters as ancillary data
                commandDescription = factory.createEnumeratedDataType();
                EnumerationList enumList = createEnumerationList(system, enumeration);

                // Set the integer encoding (the only encoding available for an
                // enumeration) and the size in bits
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
                    // Set the encoding type to indicate an unsigned
                    // integer
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
                        // Create an integer command argument and set its
                        // attributes
                        commandDescription = factory.createArgumentTypeSetTypeIntegerArgumentType();
                        ((IntegerArgumentType) commandDescription).setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(argumentType)));
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(argumentType))
                        {
                            // Set the encoding type to indicate an unsigned
                            // integer
                            intEncodingType.setEncoding("unsigned");
                        }

                        commandDescription.setIntegerDataEncoding(intEncodingType);
                        break;

                    case FLOAT:
                        // Create a float command argument and set its
                        // attributes
                        commandDescription = factory.createArgumentTypeSetTypeFloatArgumentType();
                        ((FloatArgumentType) commandDescription).setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(argumentType)));
                        FloatDataEncodingType floatEncodingType = factory.createFloatDataEncodingType();
                        commandDescription.setFloatDataEncoding(floatEncodingType);
                        break;

                    case STRING:
                        // Create a string command argument and set its
                        // attributes. A character width is set to
                        // differentiate a 'char' from a 'string'; 1 for a
                        // 'char' and 2 for a 'string'
                        commandDescription = factory.createStringDataType();
                        ((StringDataType) commandDescription).setCharacterWidth(BigInteger.valueOf(dataTypeHandler.isString(argumentType)
                                                                                                                                          ? 2
                                                                                                                                          : 1));
                        StringDataEncodingType stringEncodingType = factory.createStringDataEncodingType();
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

            // Check if other column data exists
            if (!otherArgCols.isEmpty())
            {
                // Store the other data as ancillary data
                commandDescription.setAncillaryDataSet(storeOtherAttributes(otherArgCols));
            }
        }

        // Check if this data type hasn't already been referenced
        if (!referencedDataTypes.contains(argumentType))
        {
            // Add the data type to the reference list
            referencedDataTypes.add(argumentType);
        }

        return commandDescription;
    }

    /**************************************************************************
     * Build a unit set from the supplied units string
     *
     * @param units
     *            parameter or command argument units; null to not specify
     *
     * @return Unit set for the supplied units string
     *************************************************************************/
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

    /**************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param system
     *            space system
     *
     * @param enumeration
     *            enumeration in the format <enum value><enum value
     *            separator><enum label>[<enum value separator>...][<enum pair
     *            separator>...]
     *
     * @return Enumeration list for the supplied enumeration string
     *************************************************************************/
    private EnumerationList createEnumerationList(SpaceSystemType system,
                                                  String enumeration)
    {
        EnumerationList enumList = factory.createEnumeratedDataTypeEnumerationList();

        try
        {
            // Get the character that separates the enumeration value from the
            // associated label
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

            // Divide the enumeration string into the separate enumeration
            // definitions
            String[] enumDefn = enumeration.split(Pattern.quote(enumPairSep));

            // Step through each enumeration definition
            for (int index = 0; index < enumDefn.length; index++)
            {
                // Split the enumeration definition into the name and label
                // components
                String[] enumParts = enumDefn[index].split(Pattern.quote(enumValSep), 2);

                // Create a new enumeration value type and add the enumerated
                // name and value to the enumeration list
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

    /**************************************************************************
     * Store any additional attribute information as ancillary data
     *
     * @param otherAttrs
     *            list containing other attribute data in the format [attribute
     *            name][attribute value]
     *
     * @return Ancillary data set containing the additional attribute
     *         information
     *************************************************************************/
    private AncillaryDataSet storeOtherAttributes(List<String[]> otherAttrs)
    {
        AncillaryDataSet ancillaryDataSet = null;

        // Check if other attributes exist
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            ancillaryDataSet = new AncillaryDataSet();

            // Step through each other attribute
            for (String[] attr : otherAttrs)
            {
                // Add the attribute as ancillary data
                AncillaryData ancillaryData = new AncillaryData();
                ancillaryData.setName(attr[0]);
                ancillaryData.setValue(attr[1]);
                ancillaryDataSet.getAncillaryData().add(ancillaryData);
            }
        }

        return ancillaryDataSet;
    }
}
