/**
 * CFS Command & Data Dictionary EDS handler. Copyright 2017 United States
 * Government as represented by the Administrator of the National Aeronautics
 * and Space Administration. No copyright is claimed in the United States under
 * Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.CONTAINS_DESCRIPTION;
import static CCDD.CcddConstants.CONTAINS_UNITS;
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

import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
import org.ccsds.schema.sois.seds.DeviceType;
import org.ccsds.schema.sois.seds.EnumeratedDataType;
import org.ccsds.schema.sois.seds.EnumerationListType;
import org.ccsds.schema.sois.seds.FloatDataType;
import org.ccsds.schema.sois.seds.GenericTypeType;
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
import org.ccsds.schema.sois.seds.StringDataType;
import org.ccsds.schema.sois.seds.Unit;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClasses.ArrayListCaseInsensitive;
import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddClasses.TableTypeDefinition;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;
import CCDD.CcddConstants.TableTypeUpdate;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary EDS handler class
 *****************************************************************************/
public class CcddEDSHandler implements CcddImportExportInterface
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private TypeDefinition typeDefn;
    private final CcddMacroHandler macroHandler;
    private final CcddReservedMsgIDHandler msgIDHandler;

    // GUI component instantiating this class
    private final Component parent;

    // List containing the imported table, table type, data type, and macro
    // definitions
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

    // Flag indicating that macros should be replaced by their corresponding
    // values
    private boolean replaceMacros;

    // Conversion setup error flag
    private boolean errorFlag;

    // Lists to contain any references to table types, data types, and macros
    // in the exported tables
    private List<String> referencedTableTypes;
    private List<String> referencedDataTypes;
    private List<String> referencedMacros;

    /**************************************************************************
     * EDS data type tags
     *************************************************************************/
    private enum EDSTags
    {
        TABLE("Table"),
        TABLE_TYPE("Table type"),
        COLUMN("Column data"),
        DATA_FIELD("Data field"),
        ENUMERATION("Enumeration"),
        PRIMITIVE("Primitive"),
        STRUCTURE("Structure"),
        DATA_TYPE("Data type"),
        MACRO("Macro"),
        RESERVED_MSG_ID("Reserved Message ID");

        private String tag;

        /**********************************************************************
         * Additional EDS data type tags constructor
         * 
         * @param tag
         *            text describing the data
         *********************************************************************/
        EDSTags(String tag)
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
            return tag;
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
        protected String getColumnIdentifier(String columnName, int row)
        {
            return columnName + " : Row: " + String.valueOf(row);
        }

        /**********************************************************************
         * Get the index of the column name within the column tag string
         * 
         * @return Index of the column name within the column tag string
         *********************************************************************/
        protected static int getColumnNameIndex()
        {
            return 0;
        }

        /**********************************************************************
         * Get the index of the row index within the column tag string
         * 
         * @return Index of the row index within the column tag string
         *********************************************************************/
        protected static int getRowIndex()
        {
            return 2;
        }
    }

    /**************************************************************************
     * EDS handler class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param parent
     *            GUI component instantiating this class
     *************************************************************************/
    protected CcddEDSHandler(CcddMain ccddMain, Component parent)
    {
        this.ccddMain = ccddMain;
        this.parent = parent;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        macroHandler = ccddMain.getMacroHandler();
        msgIDHandler = ccddMain.getReservedMsgIDHandler();

        errorFlag = false;

        try
        {
            // Create the XML marshaller used to convert the CCDD project data
            // into EDS XML format
            JAXBContext context = JAXBContext.newInstance("org.ccsds.schema.sois.seds");
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION,
                                   "http://www.ccsds.org/schema/sois/seds");
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT,
                                   new Boolean(true));

            // Create the factory for building the data sheet objects
            factory = new ObjectFactory();

            // Create the XML unmarshaller used to convert EDS XML data into
            // CCDD project data format
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

    /**************************************************************************
     * Get the status of the conversion setup error flag
     * 
     * @return true if an error occurred setting up for the EDS conversion
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
     * Import the the table definitions from an EDS XML formatted file
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

            // Get the project's data sheet
            dataSheet = (DataSheetType) jaxbElement.getValue();

            // Step through the EDS-formatted data and extract the telemetry
            // and command information
            unbuildDataSheets(importType, importFile.getAbsolutePath());
        }
        catch (JAXBException je)
        {
            // Inform the user that the database import failed
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>Cannot import EDS XML from file<br>'</b>" +
                                                          importFile.getAbsolutePath()
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

    /**************************************************************************
     * Export the project in EDS XML format to the specified file
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
     * @param extraInfo
     *            [0] name of the data field containing the system name
     * 
     * @return true if an error occurred preventing exporting the project to
     *         the file
     *************************************************************************/
    @Override
    public boolean exportToFile(File exportFile,
                                String[] tableNames,
                                boolean replaceMacros,
                                boolean includeReservedMsgIDs,
                                String... extraInfo)
    {
        boolean errorFlag = false;

        try
        {
            // Convert the table data into EDS format
            convertTablesToEDS(tableNames,
                               replaceMacros,
                               includeReservedMsgIDs,
                               extraInfo[0]);

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
                                                      "<html><b>Cannot export as EDS XML to file<br>'</b>" +
                                                          exportFile.getAbsolutePath()
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
     * Convert the project database contents to EDS XML format
     * 
     * @param tableNames
     *            array of table names to convert to EDS format
     * 
     * @param replaceMacros
     *            true to replace any embedded macros with their corresponding
     *            values
     * 
     * @param includeReservedMsgIDs
     *            true to include the contents of the reserved message ID table
     *            in the export file
     * 
     * @param system
     *            name of the data field containing the system name
     *************************************************************************/
    private void convertTablesToEDS(String[] tableNames,
                                    boolean replaceMacros,
                                    boolean includeReservedMsgIDs,
                                    String system)
    {
        referencedTableTypes = new ArrayList<String>();
        referencedDataTypes = new ArrayList<String>();
        referencedMacros = new ArrayListCaseInsensitive();

        // Store the macro replacement flag and the system field name
        this.replaceMacros = replaceMacros;
        systemFieldName = system;

        // Create the project's data sheet and device
        dataSheet = factory.createDataSheetType();
        project = factory.createDataSheet(dataSheet);
        device = factory.createDeviceType();
        device.setName(dbControl.getDatabase());
        device.setShortDescription(dbControl.getDatabaseDescription(dbControl.getDatabase()));
        dataSheet.setDevice(device);

        // Add the project's name spaces, parameters, and commands
        buildNameSpaces(tableNames);

        // Build a name space for the table types
        buildTableTypesNameSpace();

        // Build a name space for the primitive data types
        buildDataTypesNameSpace();

        // Check if the macro names are to be retained
        if (!replaceMacros)
        {
            // Create a name space and populate it with the macro definitions
            buildMacrosNameSpace();
        }

        // Check if the user elected to store the reserved message IDs
        if (includeReservedMsgIDs)
        {
            // Build a name space for the reserved message IDs
            buildReservedMsgIDNameSpace();
        }
    }

    /**************************************************************************
     * Step through the EDS-formatted data and extract the telemetry and
     * command information
     * 
     * @param importAll
     *            ImportType.IMPORT_ALL to import the table type, data type,
     *            and macro definitions, and the data from all the table
     *            definitions; ImportType.FIRST_DATA_ONLY to load only the data
     *            for the first table defined
     * 
     * @param importFileName
     *            import file name
     *************************************************************************/
    private void unbuildDataSheets(ImportType importType,
                                   String importFileName) throws CCDDException
    {
        int variableNameIndex = -1;
        int dataTypeIndex = -1;
        int descriptionIndex = -1;
        int unitsIndex;
        int commandNameIndex = -1;
        int cmdDescriptionIndex = -1;
        int numColumns = 0;
        List<AssociatedColumns> commandArguments = null;
        tableDefinitions = new ArrayList<TableDefinition>();
        List<String[]> dataTypeDefns = new ArrayList<String[]>();
        List<String[]> macroDefns = new ArrayList<String[]>();
        List<String[]> reservedMsgIDDefns = new ArrayList<String[]>();

        // Flags indicating if importing should continue after an input
        // error is detected
        boolean continueOnTableTypeError = false;
        boolean continueOnDataTypeError = false;
        boolean continueOnMacroError = false;
        boolean continueOnReservedMsgIDError = false;
        boolean continueOnColumnError = false;
        boolean continueOnDataFieldError = false;

        // Get a list of defined name spaces
        List<NamespaceType> nameSpaces = dataSheet.getNamespace();

        // Check if a name space exists
        if (nameSpaces != null)
        {
            // Step through each name space
            for (NamespaceType nameSpace : nameSpaces)
            {
                // Check if this is the table type definitions name space and
                // if an interface set exists
                if (nameSpace.getName().equals(EDSTags.TABLE_TYPE.getTag())
                    && nameSpace.getDeclaredInterfaceSet() != null)
                {
                    // Step through the interfaces in order to locate the name
                    // space's parameter and command sets
                    for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                    {
                        // Check if this interface contains a generic type set
                        if (intfcDecType.getGenericTypeSet() != null
                            && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty()
                            && intfcDecType.getName().startsWith(EDSTags.TABLE_TYPE.getTag()))
                        {
                            // Step through each generic type data
                            for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                            {
                                // Check if the table type inputs are present
                                if (genType.getName() != null
                                    && genType.getShortDescription() != null)
                                {
                                    // Get the table type inputs. If not
                                    // present use a blank to prevent an error
                                    // when separating the inputs
                                    String inputs = genType.getShortDescription() != null
                                                                                         ? genType.getShortDescription()
                                                                                         : "";

                                    // Extract the table type information
                                    String[] definition = CcddUtilities.splitAndRemoveQuotes(("\""
                                                                                              + genType.getName()
                                                                                              + "\","
                                                                                              + inputs));

                                    // Check if the expected number of inputs
                                    // is present
                                    if ((definition.length - 2) % TableTypeEditorColumnInfo.values().length != 0)
                                    {
                                        // Create the table type definition,
                                        // supplying the name and description
                                        TableTypeDefinition tableTypeDefn = new TableTypeDefinition(definition[0],
                                                                                                    definition[1]);

                                        // Step through each column definition
                                        // (ignoring the primary key and row
                                        // index columns)
                                        for (int columnNumber = NUM_HIDDEN_COLUMNS, index = 2; index < definition.length; columnNumber++, index += TableTypeEditorColumnInfo.values().length - 1)
                                        {
                                            // Add the column definition to the
                                            // table type definition
                                            tableTypeDefn.addColumn(new Object[] {columnNumber,
                                                                                  definition[TableTypeEditorColumnInfo.NAME.ordinal() + index - 1],
                                                                                  definition[TableTypeEditorColumnInfo.DESCRIPTION.ordinal() + index - 1],
                                                                                  definition[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal() + index - 1],
                                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.UNIQUE.ordinal() + index - 1]),
                                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.REQUIRED.ordinal() + index - 1]),
                                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal() + index - 1]),
                                                                                  Boolean.valueOf(definition[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal() + index - 1])});
                                        }

                                        // Check if the table type isn't new
                                        // and doesn't match an existing one
                                        // with the same name
                                        if (tableTypeHandler.updateTableTypes(tableTypeDefn, true) == TableTypeUpdate.MISMATCH)
                                        {
                                            throw new CCDDException("table type '"
                                                                    + tableTypeDefn.getTypeName()
                                                                    + "' already exists and doesn't match the import definition");
                                        }
                                    }
                                    // Check if the user hasn't already elected
                                    // to ignore table type errors
                                    else if (!continueOnTableTypeError)
                                    {
                                        // Inform the user that the table type
                                        // name is incorrect
                                        int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                            "<html><b>Table type '"
                                                                                                                + genType.getName()
                                                                                                                + "' definition has missing or extra "
                                                                                                                + "input(s) in import file '</b>"
                                                                                                                + importFileName
                                                                                                                + "<b>'; continue?",
                                                                                                            "Table Type Error",
                                                                                                            "Ignore this table type",
                                                                                                            "Ignore this and any remaining invalid table types",
                                                                                                            "Stop importing");

                                        // Check if the Ignore All button was
                                        // pressed
                                        if (buttonSelected == IGNORE_BUTTON)
                                        {
                                            // Set the flag to ignore
                                            // subsequent column name errors
                                            continueOnTableTypeError = true;
                                        }
                                        // Check if the Cancel button was
                                        // pressed
                                        else if (buttonSelected == CANCEL_BUTTON)
                                        {
                                            // No error message is provided
                                            // since the user chose this action
                                            throw new CCDDException();
                                        }
                                    }
                                }
                                // Check if the user hasn't already elected to
                                // ignore table type errors
                                else if (!continueOnTableTypeError)
                                {
                                    // Inform the user that the table type name
                                    // is missing
                                    int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                        "<html><b>Missing table type "
                                                                                                            + "name in import file '</b>"
                                                                                                            + importFileName
                                                                                                            + "<b>'; continue?",
                                                                                                        "Table Type Error",
                                                                                                        "Ignore this table type",
                                                                                                        "Ignore this and any remaining invalid table types",
                                                                                                        "Stop importing");

                                    // Check if the Ignore All button was
                                    // pressed
                                    if (buttonSelected == IGNORE_BUTTON)
                                    {
                                        // Set the flag to ignore subsequent
                                        // column name errors
                                        continueOnTableTypeError = true;
                                    }
                                    // Check if the Cancel button was pressed
                                    else if (buttonSelected == CANCEL_BUTTON)
                                    {
                                        // No error message is provided since
                                        // the user chose this action
                                        throw new CCDDException();
                                    }
                                }
                            }
                        }
                    }
                }
                // Check if all definitions are to be loaded, this is the
                // primitive data type definitions name space, and an interface
                // set exists
                else if (importType == ImportType.IMPORT_ALL
                         && nameSpace.getName().equals(EDSTags.DATA_TYPE.getTag())
                         && nameSpace.getDataTypeSet() != null)
                {
                    // Get the data types defined in the data set
                    List<RootDataType> dataTypes = nameSpace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType();

                    // Step through each data type
                    for (RootDataType rDataType : dataTypes)
                    {
                        // Set the data type name and initialize the size and
                        // base type values
                        String dataType = rDataType.getName();
                        String sizeInBytes = "";
                        String baseType = "";

                        // Check if this is an integer data type
                        if (rDataType instanceof IntegerDataType)
                        {
                            IntegerDataType iDataType = (IntegerDataType) rDataType;
                            IntegerDataEncodingType intEncode = iDataType.getIntegerDataEncoding();

                            // Check if the size exists
                            if (intEncode.getSizeInBits() != null)
                            {
                                // Get the integer's size in bytes
                                sizeInBytes = String.valueOf(intEncode.getSizeInBits().intValue() / 8);
                            }

                            // Check if the integer is unsigned
                            if (intEncode.getEncoding() == IntegerEncodingType.UNSIGNED)
                            {
                                // Set the base type to indicate an unsigned
                                // integer
                                baseType = BaseDataTypeInfo.UNSIGNED_INT.getName();
                            }
                            // The integer is signed
                            else
                            {
                                // Set the base type to indicate a signed
                                // integer
                                baseType = BaseDataTypeInfo.SIGNED_INT.getName();
                            }
                        }
                        // Check if this is a floating point data type
                        else if (rDataType instanceof FloatDataType)
                        {
                            // Set the base type to indicate a floating point
                            baseType = BaseDataTypeInfo.FLOATING_POINT.getName();
                        }
                        // Check if this is a string data type
                        else if (rDataType instanceof StringDataType)
                        {
                            // Set the base type to indicate a character
                            baseType = BaseDataTypeInfo.CHARACTER.getName();
                        }

                        // Add the data type definition to the list (add a
                        // blank for the OID column)
                        dataTypeDefns.add(new String[] {dataType,
                                                        dataType,
                                                        sizeInBytes,
                                                        baseType,
                                                        ""});
                    }

                    // Step through the interfaces in order to locate the
                    // name space's parameter and command sets
                    for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                    {
                        // Check if this interface contains a generic type
                        // set
                        if (intfcDecType.getGenericTypeSet() != null
                            && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty())
                        {
                            // Step through each generic type data
                            for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                            {
                                // Check if the expected inputs are present
                                if (genType.getName() != null
                                    && genType.getShortDescription() != null)
                                {
                                    boolean isFound = false;

                                    // Build the data type definition from the
                                    // generic type data
                                    String[] typeDefn = (genType.getName()
                                                         + ","
                                                         + genType.getShortDescription()
                                                         + ",\"\"").split(",", -1);

                                    // Step through the data type definitions
                                    // already added
                                    for (int index = 0; index < dataTypeDefns.size(); index++)
                                    {
                                        // Check if the data type in the
                                        // generic set matches an existing one
                                        // from the data set
                                        if (CcddDataTypeHandler.getDataTypeName(typeDefn).equals(CcddDataTypeHandler.getDataTypeName(dataTypeDefns.get(index))))
                                        {
                                            // Set the flag to indicate a match
                                            // exists
                                            isFound = true;

                                            // Check if the user name is empty
                                            // in either the data set or
                                            // generic type set. This accounts
                                            // for definitions with a blank
                                            // user name (i.e., the C name is
                                            // used as the data type name)
                                            if (dataTypeDefns.get(index)[DataTypesColumn.USER_NAME.ordinal()].isEmpty()
                                                || typeDefn[DataTypesColumn.USER_NAME.ordinal()].isEmpty())
                                            {
                                                // Set the user name to the one
                                                // from the generic set
                                                dataTypeDefns.get(index)[DataTypesColumn.USER_NAME.ordinal()] = typeDefn[DataTypesColumn.USER_NAME.ordinal()];
                                            }

                                            // Check if the data set C name is
                                            // blank
                                            if (dataTypeDefns.get(index)[DataTypesColumn.C_NAME.ordinal()].isEmpty())
                                            {
                                                // Set the C name to the one
                                                // from the generic set
                                                dataTypeDefns.get(index)[DataTypesColumn.C_NAME.ordinal()] = typeDefn[DataTypesColumn.C_NAME.ordinal()];
                                            }

                                            // Check if the data set size is
                                            // blank
                                            if (dataTypeDefns.get(index)[DataTypesColumn.SIZE.ordinal()].isEmpty())
                                            {
                                                // Set the size to the one from
                                                // the generic set
                                                dataTypeDefns.get(index)[DataTypesColumn.SIZE.ordinal()] = typeDefn[DataTypesColumn.SIZE.ordinal()];
                                            }

                                            // Check if the data set base type
                                            // is blank
                                            if (dataTypeDefns.get(index)[DataTypesColumn.BASE_TYPE.ordinal()].isEmpty())
                                            {
                                                // Set the base type to the one
                                                // from the generic set
                                                dataTypeDefns.get(index)[DataTypesColumn.BASE_TYPE.ordinal()] = typeDefn[DataTypesColumn.BASE_TYPE.ordinal()];
                                            }

                                            break;
                                        }
                                    }

                                    // Check if the data type doesn't match one
                                    // already added from the data set
                                    if (!isFound)
                                    {
                                        // Add the data type definition to the
                                        // list (add a blank for the OID
                                        // column)
                                        dataTypeDefns.add(typeDefn);
                                    }
                                }
                                // Incorrect number of inputs. Check if the
                                // user hasn't already elected to ignore data
                                // type errors
                                else if (!continueOnDataTypeError)
                                {
                                    // Inform the user that the data type
                                    // inputs are incorrect
                                    int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                        "<html><b>Missing or extra data type definition input(s) in import file '</b>"
                                                                                                            + importFileName
                                                                                                            + "<b>'; continue?",
                                                                                                        "Data Type Error",
                                                                                                        "Ignore this data type",
                                                                                                        "Ignore this and any remaining invalid data types",
                                                                                                        "Stop importing");

                                    // Check if the Ignore All button was
                                    // pressed
                                    if (buttonSelected == IGNORE_BUTTON)
                                    {
                                        // Set the flag to ignore subsequent
                                        // column name errors
                                        continueOnDataTypeError = true;
                                    }
                                    // Check if the Cancel button was pressed
                                    else if (buttonSelected == CANCEL_BUTTON)
                                    {
                                        // No error message is provided since
                                        // the user chose this action
                                        throw new CCDDException();
                                    }
                                }
                            }
                        }
                    }
                }
                // Check if all definitions are to be loaded, this is the macro
                // definitions name space, and an interface set exists
                else if (importType == ImportType.IMPORT_ALL
                         && nameSpace.getName().equals(EDSTags.MACRO.getTag())
                         && nameSpace.getDeclaredInterfaceSet() != null)
                {
                    // Step through the interfaces in order to locate the name
                    // space's parameter and command sets
                    for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                    {
                        // Check if this interface contains a generic type set
                        if (intfcDecType.getGenericTypeSet() != null
                            && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty()
                            && intfcDecType.getName().startsWith(EDSTags.MACRO.getTag()))
                        {
                            // Step through each generic type data
                            for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                            {
                                // Check that the macro name is present
                                if (genType.getName() != null)
                                {
                                    // Add the macro definition to the list
                                    // (add a blank for the OID column)
                                    macroDefns.add(new String[] {genType.getName(),
                                                                 (genType.getShortDescription() != null
                                                                                                       ? genType.getShortDescription()
                                                                                                       : ""),
                                                                 ""});
                                }
                                // Incorrect number of inputs. Check if the
                                // user hasn't already elected to ignore macro
                                // errors
                                else if (!continueOnMacroError)
                                {
                                    // Inform the user that the macro inputs
                                    // are incorrect
                                    int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                        "<html><b>Missing or extra macro definition "
                                                                                                            + "input(s) in import file '</b>"
                                                                                                            + importFileName
                                                                                                            + "<b>'; continue?",
                                                                                                        "Macro Error",
                                                                                                        "Ignore this macro",
                                                                                                        "Ignore this and any remaining invalid macros",
                                                                                                        "Stop importing");

                                    // Check if the Ignore All button was
                                    // pressed
                                    if (buttonSelected == IGNORE_BUTTON)
                                    {
                                        // Set the flag to ignore subsequent
                                        // macro errors
                                        continueOnMacroError = true;
                                    }
                                    // Check if the Cancel button was pressed
                                    else if (buttonSelected == CANCEL_BUTTON)
                                    {
                                        // No error message is provided since
                                        // the user chose this action
                                        throw new CCDDException();
                                    }
                                }
                            }
                        }
                    }
                }
                // Check if all definitions are to be loaded, this is the
                // reserved message ID definitions name space, and an interface
                // set exists
                else if (importType == ImportType.IMPORT_ALL
                         && nameSpace.getName().equals(EDSTags.RESERVED_MSG_ID.getTag())
                         && nameSpace.getDeclaredInterfaceSet() != null)
                {
                    // Step through the interfaces in order to locate the name
                    // space's parameter and command sets
                    for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                    {
                        // Check if this interface contains a generic type set
                        if (intfcDecType.getGenericTypeSet() != null
                            && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty()
                            && intfcDecType.getName().startsWith(EDSTags.RESERVED_MSG_ID.getTag()))
                        {
                            // Step through each generic type data
                            for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                            {
                                // Check that the reserved message ID is
                                // present
                                if (genType.getName() != null)
                                {
                                    // Add the reserved message ID definition
                                    // to the list (add a blank for the OID
                                    // column)
                                    reservedMsgIDDefns.add(new String[] {genType.getName(),
                                                                      (genType.getShortDescription() != null
                                                                                                            ? genType.getShortDescription()
                                                                                                            : ""),
                                                                      ""});
                                }
                                // Incorrect number of inputs. Check if the
                                // user hasn't already elected to ignore
                                // reserved message ID errors
                                else if (!continueOnReservedMsgIDError)
                                {
                                    // Inform the user that the reserved
                                    // message ID inputs are incorrect
                                    int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                        "<html><b>Missing or extra reserved message ID "
                                                                                                            + "definition input(s) in import file '</b>"
                                                                                                            + importFileName
                                                                                                            + "<b>'; continue?",
                                                                                                        "Reserved Message ID Error",
                                                                                                        "Ignore this reserved message ID",
                                                                                                        "Ignore this and any remaining invalid reserved message IDs",
                                                                                                        "Stop importing");

                                    // Check if the Ignore All button was
                                    // pressed
                                    if (buttonSelected == IGNORE_BUTTON)
                                    {
                                        // Set the flag to ignore subsequent
                                        // reserved message ID errors
                                        continueOnReservedMsgIDError = true;
                                    }
                                    // Check if the Cancel button was pressed
                                    else if (buttonSelected == CANCEL_BUTTON)
                                    {
                                        // No error message is provided since
                                        // the user chose this action
                                        throw new CCDDException();
                                    }
                                }
                            }
                        }
                    }
                }
                // Check if all definitions are to be loaded or that this is
                // the first table, this is a table definition name space, an
                // interface set exists, and that the name space name is in the
                // correct format for a table (table identifier : table name< :
                // system name>)
                else if ((importType == ImportType.IMPORT_ALL
                         || tableDefinitions.size() == 0)
                         && nameSpace.getName().startsWith(EDSTags.TABLE.getTag() + ":")
                         && nameSpace.getDeclaredInterfaceSet() != null
                         && nameSpace.getName().matches("[^:]+?:[^:]+?(?::[^:]*)?$"))
                {
                    int cmdArgIndex = -1;
                    String command = "";
                    TableDefinition tableDefn = null;

                    // Separate the name space name into the tag, table name,
                    // and (optional) system name
                    String[] nameParts = nameSpace.getName().split(":");

                    // Create a table definition for this table. The
                    // nameSpace name begins with the table identifier followed
                    // by the system and the table name, separated by colons
                    tableDefn = new TableDefinition(nameParts[1].trim(),
                                                    nameSpace.getShortDescription());

                    // Make three passes through the name space interface. The
                    // first pass processes the telemetry and command
                    // interfaces, and the generic types not associated with
                    // table column data. The second pass processes the
                    // enumerations in the data set. The third pass processes
                    // the generic type column data, only using it if a cell
                    // value is still empty after being populated by the
                    // telemetry or command interface data
                    for (int loop = 1; loop <= 3; loop++)
                    {
                        // Check if this is the second pass, a data set exists,
                        // and the command name column is present in the data
                        // type
                        if (loop == 2
                            && nameSpace.getDataTypeSet() != null
                            && commandNameIndex != -1)
                        {
                            // Get the reference to the data type sets
                            List<RootDataType> dataTypes = nameSpace.getDataTypeSet().getArrayDataTypeOrBinaryDataTypeOrBooleanDataType();

                            // Step through each data type set
                            for (RootDataType rDataType : dataTypes)
                            {
                                // Check if this is an enumerated data type set
                                if (rDataType instanceof EnumeratedDataType)
                                {
                                    EnumeratedDataType eDataType = (EnumeratedDataType) rDataType;

                                    // Get the list of enumerated values and
                                    // associated labels
                                    EnumerationListType enumList = eDataType.getEnumerationList();

                                    // Check if any enumerations exist
                                    if (enumList != null)
                                    {
                                        String enumeration = null;

                                        // Step through each enumeration
                                        for (ValueEnumerationType enumType : enumList.getEnumeration())
                                        {
                                            // Check if this is the first value
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

                                        // Check if the command name changed
                                        if (!eDataType.getShortDescription().equals(command))
                                        {
                                            // Reset the argument index
                                            cmdArgIndex = -1;
                                        }

                                        // Increment the argument index and
                                        // store the command name for which
                                        // this argument is a member
                                        cmdArgIndex++;
                                        command = eDataType.getShortDescription();

                                        // Step through each row of table data
                                        for (int row = 0; row < tableDefn.getData().size(); row += numColumns)
                                        {
                                            // Check if the command name
                                            // matches the one in the table
                                            // data for this row
                                            if (tableDefn.getData().get(row + commandNameIndex) != null
                                                && tableDefn.getData().get(row + commandNameIndex).equals(command)
                                                && cmdArgIndex < commandArguments.size())
                                            {
                                                // Get the command argument
                                                // reference
                                                AssociatedColumns cmdArg = commandArguments.get(cmdArgIndex);

                                                // Check if the command
                                                // argument enumeration is
                                                // present
                                                if (cmdArg.getEnumeration() != -1
                                                    && enumeration != null)
                                                {
                                                    // Store the command
                                                    // argument enumeration
                                                    tableDefn.getData().set(row
                                                                            + cmdArg.getEnumeration(),
                                                                            enumeration);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Step through the interfaces in order to locate the
                        // name space's parameter, command, and generic sets
                        for (InterfaceDeclarationType intfcDecType : nameSpace.getDeclaredInterfaceSet().getInterface())
                        {
                            // Check if this is the first pass
                            if (loop == 1)
                            {
                                /**********************************************
                                 * Telemetry processing
                                 * *******************************************/
                                // Check if the interface contains a parameter
                                // set
                                if (intfcDecType.getParameterSet() != null
                                    && !intfcDecType.getParameterSet().getParameter().isEmpty())
                                {
                                    // Step through each parameter
                                    for (InterfaceParameterType parmType : intfcDecType.getParameterSet().getParameter())
                                    {
                                        // Create a new row of data in the
                                        // table definition to contain this
                                        // structure's information. Initialize
                                        // all columns to blanks except for the
                                        // variable name
                                        String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                        Arrays.fill(newRow, "");
                                        newRow[variableNameIndex] = parmType.getName();

                                        // Check if a data type exists
                                        if (parmType.getType() != null
                                            && !parmType.getType().isEmpty())
                                        {
                                            // Store the data type for this
                                            // variable
                                            newRow[dataTypeIndex] = parmType.getType().replaceFirst("^[^/]*/",
                                                                                                    "");
                                        }

                                        // Check if the description column
                                        // exists in the table type definition
                                        // and that a description exists
                                        if (descriptionIndex != -1
                                            && parmType.getShortDescription() != null)
                                        {
                                            // Store the description for this
                                            // variable
                                            newRow[descriptionIndex] = parmType.getShortDescription();
                                        }

                                        // Add the new row to the table
                                        // definition
                                        tableDefn.addData(newRow);
                                    }
                                }

                                /**********************************************
                                 * Command processing
                                 * *******************************************/
                                // Check if the interface contains a command
                                // set
                                if (intfcDecType.getCommandSet() != null
                                    && !intfcDecType.getCommandSet().getCommand().isEmpty())
                                {
                                    // Step through each command
                                    for (InterfaceCommandType cmdType : intfcDecType.getCommandSet().getCommand())
                                    {
                                        // Create a new row of data in the
                                        // table definition to contain this
                                        // command's information. Initialize
                                        // all columns to blanks except for the
                                        // command name
                                        String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                        Arrays.fill(newRow, "");
                                        newRow[commandNameIndex] = cmdType.getName();

                                        // Check if the command description is
                                        // present and the description column
                                        // exists in the table type definition
                                        if (cmdType.getShortDescription() != null
                                            && cmdDescriptionIndex != -1)
                                        {
                                            // Store the command description in
                                            // the row's description column
                                            newRow[cmdDescriptionIndex] = cmdType.getShortDescription();
                                        }

                                        // Step through each command argument
                                        for (int index = 0; index < commandArguments.size(); index++)
                                        {
                                            // Check if this argument is
                                            // applicable to this command (not
                                            // all commands may have the same
                                            // number of arguments)
                                            if (index < cmdType.getArgument().size())
                                            {
                                                // Store the command argument
                                                // name and data types
                                                newRow[commandArguments.get(index).getName()] = cmdType.getArgument().get(index).getName();

                                                // Check if a data type exists
                                                if (cmdType.getArgument().get(index).getType() != null)
                                                {
                                                    newRow[commandArguments.get(index).getDataType()] = cmdType.getArgument().get(index).getType();
                                                }

                                                // Step through the other
                                                // associated command argument
                                                // columns
                                                for (Integer column : commandArguments.get(index).getOther())
                                                {
                                                    // Check if a command
                                                    // argument description
                                                    // exists and if this is
                                                    // the command argument
                                                    // description column
                                                    if (cmdType.getArgument().get(index).getShortDescription() != null
                                                        && typeDefn.getColumnNamesVisible()[column].matches(CONTAINS_DESCRIPTION))
                                                    {
                                                        // Store the command
                                                        // argument description
                                                        // and stop searching
                                                        newRow[column] = cmdType.getArgument().get(index).getShortDescription();
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        // Add the new row to the table
                                        // definition
                                        tableDefn.addData(newRow);
                                    }
                                }
                            }

                            /**************************************************
                             * Generic type processing
                             * ***********************************************/
                            // Check if this interface contains a generic type
                            // set. The generic type set is used to column
                            // data, data fields, and enumeration parameters
                            // for tables that aren't structure or command
                            // tables, and to store extra columns, data fields,
                            // and extra enumeration parameters for structure
                            // and command tables
                            if (intfcDecType.getGenericTypeSet() != null
                                && !intfcDecType.getGenericTypeSet().getGenericType().isEmpty())
                            {
                                // Step through each generic type data
                                for (GenericTypeType genType : intfcDecType.getGenericTypeSet().getGenericType())
                                {
                                    // Check if this is the first pass
                                    if (loop == 1)
                                    {
                                        // Check if this is the table type
                                        if (intfcDecType.getName().equals(EDSTags.TABLE_TYPE.getTag()))
                                        {
                                            // Store the table's type name
                                            tableDefn.setType(genType.getShortDescription());

                                            // Get the table's type
                                            // definition based on the type
                                            // name
                                            typeDefn = tableTypeHandler.getTypeDefinition(tableDefn.getType());

                                            // Check if the table type
                                            // isn't recognized
                                            if (typeDefn == null)
                                            {
                                                throw new CCDDException("unknown table type '"
                                                                        + tableDefn.getType()
                                                                        + "'");
                                            }

                                            // Get the number of visible
                                            // columns for this table type
                                            numColumns = typeDefn.getColumnCountVisible();

                                            // Get the array of visible column
                                            // names
                                            String[] columnNames = typeDefn.getColumnNamesVisible();

                                            // Check if this is a structure
                                            // type table
                                            if (typeDefn.isStructure())
                                            {
                                                String descColName;

                                                // Get the structure column
                                                // indices, if this is a
                                                // structure type
                                                variableNameIndex = typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.VARIABLE));
                                                dataTypeIndex = typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.PRIM_AND_STRUCT));

                                                // Get the description column
                                                // name
                                                if ((descColName = typeDefn.getColumnNameByInputType(InputDataType.DESCRIPTION)) != null)
                                                {
                                                    // Get the variable
                                                    // description column, if
                                                    // present
                                                    descriptionIndex = typeDefn.getVisibleColumnIndexByUserName(descColName);
                                                }

                                                // Check if the column by the
                                                // default name isn't present
                                                if (descriptionIndex == -1)
                                                {
                                                    // Step through each column
                                                    for (int column = 0; column < numColumns; column++)
                                                    {
                                                        // Check if the column
                                                        // name contain
                                                        // 'description'
                                                        if (columnNames[column].matches(CONTAINS_DESCRIPTION))
                                                        {
                                                            // Set this column
                                                            // as the
                                                            // description
                                                            // column and stop
                                                            // searching
                                                            descriptionIndex = column;
                                                            break;
                                                        }
                                                    }
                                                }

                                                // Get the units column. If the
                                                // default units column name
                                                // isn't used then the first
                                                // column containing 'units' is
                                                // selected
                                                unitsIndex = typeDefn.getVisibleColumnIndexByUserName(DefaultColumn.UNITS.getName());

                                                // Check if the column by the
                                                // default name isn't present
                                                if (unitsIndex == -1)
                                                {
                                                    // Step through each column
                                                    for (int column = 0; column < numColumns; column++)
                                                    {
                                                        // Check if the column
                                                        // name contain 'units'
                                                        if (columnNames[column].matches(CONTAINS_UNITS))
                                                        {
                                                            // Set this column
                                                            // as the units
                                                            // column and stop
                                                            // searching
                                                            unitsIndex = column;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                            // Check if this is a command type
                                            // table
                                            else if (typeDefn.isCommand())
                                            {
                                                // Get the list containing
                                                // command argument name, data
                                                // type, enumeration, minimum,
                                                // maximum, and other
                                                // associated column indices
                                                // for each argument grouping
                                                commandArguments = typeDefn.getAssociatedCommandColumns(true);

                                                // Get the command name column
                                                commandNameIndex = typeDefn.getVisibleColumnIndexByUserName(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME));

                                                // Get the command description
                                                // column. If the default
                                                // command description column
                                                // name isn't used then the
                                                // first column containing
                                                // 'description' is selected
                                                // that doesn't refer to a
                                                // command argument
                                                cmdDescriptionIndex = typeDefn.getVisibleColumnIndexByUserName(DefaultColumn.DESCRIPTION_CMD.getName());

                                                // Check if the column by the
                                                // default name isn't present
                                                if (cmdDescriptionIndex == -1)
                                                {
                                                    // Step through each column
                                                    for (int column = 0; column < numColumns; column++)
                                                    {
                                                        // Check if the column
                                                        // name contain
                                                        // 'description'
                                                        if (columnNames[column].matches(CONTAINS_DESCRIPTION))
                                                        {
                                                            boolean isArgDesc = false;

                                                            // Step through the
                                                            // command argument
                                                            // columns
                                                            for (AssociatedColumns argCols : commandArguments)
                                                            {
                                                                // Check if
                                                                // this column
                                                                // is the
                                                                // description
                                                                // for a
                                                                // command
                                                                // argument
                                                                if (argCols.getOther().contains(column))
                                                                {
                                                                    // Set the
                                                                    // flag
                                                                    // indicating
                                                                    // this is
                                                                    // a
                                                                    // command
                                                                    // argument
                                                                    // description
                                                                    // and stop
                                                                    // searching
                                                                    isArgDesc = true;
                                                                    break;
                                                                }
                                                            }

                                                            // Check if the
                                                            // column isn't a
                                                            // command argument
                                                            // description
                                                            if (!isArgDesc)
                                                            {
                                                                // Set this
                                                                // column
                                                                // as the
                                                                // description
                                                                // column and
                                                                // stop
                                                                // searching
                                                                cmdDescriptionIndex = column;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Check if all definitions are to be
                                        // loaded and this is a data field
                                        // definition
                                        else if (importType == ImportType.IMPORT_ALL
                                                 && intfcDecType.getName().equals(EDSTags.DATA_FIELD.getTag()))
                                        {
                                            // Get the data field inputs. If
                                            // not present use a blank to
                                            // prevent an error when separating
                                            // the inputs
                                            String inputs = genType.getShortDescription() != null
                                                                                                 ? genType.getShortDescription()
                                                                                                 : "";

                                            // Parse data field. The values are
                                            // comma-separated; however, commas
                                            // within quotes are ignored - this
                                            // allows commas to be included in
                                            // the data values
                                            String[] fieldDefn = CcddUtilities.splitAndRemoveQuotes("\""
                                                                                                    + tableDefn.getName()
                                                                                                    + "\","
                                                                                                    + inputs);

                                            // Check if the expected number of
                                            // inputs is present
                                            if (fieldDefn.length == FieldEditorColumnInfo.values().length + 1)
                                            {
                                                // Add the data field
                                                // definition to the table
                                                tableDefn.addDataField(fieldDefn);
                                            }
                                            // Check that the user hasn't
                                            // elected to ignore data field
                                            // errors
                                            else if (!continueOnDataFieldError)
                                            {
                                                // Inform the user that the
                                                // data field name inputs are
                                                // incorrect
                                                int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                                                    "<html><b>Table '</b>"
                                                                                                                        + tableDefn.getName()
                                                                                                                        + "<b>' has missing or extra data field "
                                                                                                                        + "input(s) in import file '</b>"
                                                                                                                        + importFileName
                                                                                                                        + "<b>'; continue?",
                                                                                                                    "Data Field Error",
                                                                                                                    "Ignore this invalid data field",
                                                                                                                    "Ignore this and any remaining invalid data fields",
                                                                                                                    "Stop importing");

                                                // Check if the Ignore All
                                                // button was pressed
                                                if (buttonSelected == IGNORE_BUTTON)
                                                {
                                                    // Set the flag to ignore
                                                    // subsequent data field
                                                    // errors
                                                    continueOnDataFieldError = true;
                                                }
                                                // Check if the Cancel
                                                // button was pressed
                                                else if (buttonSelected == CANCEL_BUTTON)
                                                {
                                                    // No error message is
                                                    // provided since the
                                                    // user chose this
                                                    // action
                                                    throw new CCDDException();
                                                }
                                            }
                                        }
                                    }
                                    // Check if this is the third pass
                                    else if (loop == 3)
                                    {
                                        // Check if this is column data
                                        if (intfcDecType.getName().equals(EDSTags.COLUMN.getTag()))
                                        {
                                            // Extract the column name and row
                                            // number, and use the column name
                                            // to get the column index
                                            String[] parts = genType.getName().split(":");
                                            String columnName = parts[EDSTags.getColumnNameIndex()].trim();
                                            int row = Integer.valueOf(parts[EDSTags.getRowIndex()].trim());
                                            int column = typeDefn.getVisibleColumnIndexByUserName(columnName);

                                            // Check that the column exists in
                                            // the table
                                            if (column != -1)
                                            {
                                                // Add one or more rows until
                                                // the row is created
                                                // containing this column value
                                                while (row * numColumns >= tableDefn.getData().size())
                                                {
                                                    // Create a row with empty
                                                    // columns and add the new
                                                    // row to the table data
                                                    String[] newRow = new String[typeDefn.getColumnCountVisible()];
                                                    Arrays.fill(newRow, "");
                                                    tableDefn.addData(newRow);
                                                }

                                                // Check if the cell is empty
                                                // (i.e., don't replace the
                                                // cell value if it already is
                                                // present)
                                                if (tableDefn.getData().get(row
                                                                            * numColumns
                                                                            + column).isEmpty())
                                                {
                                                    // Replace the value for
                                                    // the specified column
                                                    tableDefn.getData().set(row
                                                                            * numColumns
                                                                            + column,
                                                                            genType.getShortDescription());
                                                }
                                            }
                                            // Check that the user hasn't
                                            // elected to ignore column name
                                            // errors
                                            else if (!continueOnColumnError)
                                            {
                                                // Inform the user that the
                                                // column name is invalid
                                                int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
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
                                                                                                                    "Stop importing");

                                                // Check if the Ignore All
                                                // button was pressed
                                                if (buttonSelected == IGNORE_BUTTON)
                                                {
                                                    // Set the flag to ignore
                                                    // subsequent column name
                                                    // errors
                                                    continueOnColumnError = true;
                                                }
                                                // Check if the Cancel button
                                                // was pressed
                                                else if (buttonSelected == CANCEL_BUTTON)
                                                {
                                                    // No error message is
                                                    // provided since the user
                                                    // chose this action
                                                    throw new CCDDException();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add the table definition to the list
                    tableDefinitions.add(tableDefn);
                }
            }

            // Check if all definitions are to be loaded
            if (importType == ImportType.IMPORT_ALL)
            {
                // Add the data type if it's new or match it to an existing one
                // with the same name if the type definitions are the same
                String badDefn = dataTypeHandler.updateDataTypes(dataTypeDefns);

                // Check if a data type isn't new and doesn't match an existing
                // one with the same name
                if (badDefn != null)
                {
                    throw new CCDDException("data type '"
                                            + badDefn
                                            + "' already exists and doesn't match the import definition");
                }

                // Add the macro if it's new or match it to an existing one
                // with the same name if the values are the same
                badDefn = macroHandler.updateMacros(macroDefns);

                // Check if a macro isn't new and doesn't match an existing one
                // with the same name
                if (badDefn != null)
                {
                    throw new CCDDException("macro '"
                                            + badDefn
                                            + "' already exists and doesn't match the import definition");
                }

                // Add the reserved message ID definition if it's new
                msgIDHandler.updateReservedMsgIDs(reservedMsgIDDefns);
            }
        }
    }

    /**************************************************************************
     * Create a name space to contain the referenced table type definitions
     *************************************************************************/
    private void buildTableTypesNameSpace()
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
                                                             + tableTypeDefn.getDescription()
                                                             + "\"");

                // Step through each column definition in the table type,
                // skipping the primary key and row index columns
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
                tableTypeDefinitions.add(new String[] {tableTypeDefn.getName(),
                                                       definition.toString()});
            }
        }

        // Check if a table type is defined
        if (!tableTypeDefinitions.isEmpty())
        {
            // Create a name space to contain the table type definitions
            NamespaceType tableTypesNameSpace = addNameSpace("",
                                                             "",
                                                             EDSTags.TABLE_TYPE.getTag(),
                                                             "Table type definitions");

            // Store the table type definitions as ancillary data
            storeOtherAttributes(tableTypesNameSpace,
                                 EDSTags.TABLE_TYPE,
                                 tableTypeDefinitions);
        }
    }

    /**************************************************************************
     * Create a name space to contain the referenced primitive data types
     *************************************************************************/
    private void buildDataTypesNameSpace()
    {
        // Check if any data types are referenced
        if (!referencedDataTypes.isEmpty())
        {
            List<String[]> dataTypeDefinitions = new ArrayList<String[]>();

            // Create a name space to contain the primitive data types
            NamespaceType dataTypeNameSpace = addNameSpace("",
                                                           "",
                                                           EDSTags.DATA_TYPE.getTag(),
                                                           "Data type definitions");

            dataTypeNameSpace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());

            // Create a data type set to contain the primitive data type
            // information
            DataTypeSetType dataTypeSet = factory.createDataTypeSetType();

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

                    // Add the data type to the name space
                    dataTypeSet.getArrayDataTypeOrBinaryDataTypeOrBooleanDataType().add(type);

                    // Parse the definition and add it to the list
                    dataTypeDefinitions.add(new String[] {dataType[DataTypesColumn.USER_NAME.ordinal()],
                                                          dataType[DataTypesColumn.C_NAME.ordinal()]
                                                              + ","
                                                              + dataType[DataTypesColumn.SIZE.ordinal()]
                                                              + ","
                                                              + dataType[DataTypesColumn.BASE_TYPE.ordinal()]});

                }
            }

            // Check if a data type is defined
            if (!dataTypeDefinitions.isEmpty())
            {
                // Store the data type definitions as ancillary data
                storeOtherAttributes(dataTypeNameSpace,
                                     EDSTags.DATA_TYPE,
                                     dataTypeDefinitions);
            }
        }
    }

    /**************************************************************************
     * Create a name space to contain the referenced macro definitions
     *************************************************************************/
    private void buildMacrosNameSpace()
    {
        List<String[]> macroDefinitions = new ArrayList<String[]>();

        // Step through each referenced macro
        for (String refMacro : referencedMacros)
        {
            // Get the macro definition
            String macroValue = macroHandler.getMacroValue(refMacro);

            // Check if the macro exists
            if (macroValue != null)
            {
                // Create the macro definition
                macroDefinitions.add(new String[] {refMacro, macroValue});
            }
        }

        // Check if a macro is defined
        if (!macroDefinitions.isEmpty())
        {
            // Create a name space to contain the macro definitions
            NamespaceType macroNameSpace = addNameSpace("",
                                                        "",
                                                        EDSTags.MACRO.getTag(),
                                                        "Macro definitions");

            // Store the macro definitions as ancillary data
            storeOtherAttributes(macroNameSpace,
                                 EDSTags.MACRO,
                                 macroDefinitions);
        }
    }

    /**************************************************************************
     * Create a name space to contain all reserved message IDs
     *************************************************************************/
    private void buildReservedMsgIDNameSpace()
    {
        // Check if a reserved message ID is defined
        if (!msgIDHandler.getReservedMsgIDData().isEmpty())
        {
            // Create a name space to contain the reserved message ID
            // definitions
            NamespaceType reservedMsgIDNameSpace = addNameSpace("",
                                                             "",
                                                             EDSTags.RESERVED_MSG_ID.getTag(),
                                                             "Reserved message ID definitions");

            // Store the reserved message ID definitions as ancillary data
            storeOtherAttributes(reservedMsgIDNameSpace,
                                 EDSTags.RESERVED_MSG_ID,
                                 msgIDHandler.getReservedMsgIDData());
        }
    }

    /**************************************************************************
     * Build the name spaces for the list of tables specified
     * 
     * @param tableName
     *            array of table names
     *************************************************************************/
    private void buildNameSpaces(String[] tableNames)
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

            // Get the table type and from the type get the type definition.
            // The type definition can be a global parameter since if the table
            // represents a structure, then all of its children are also
            // structures, and if the table represents commands or other table
            // type then it is processed within this nest level
            typeDefn = ccddMain.getTableTypeHandler().getTypeDefinition(tableInfo.getType());

            // Get the table's basic type - structure, command, or the original
            // table type if not structure or command table
            String tableType = typeDefn.isStructure()
                                                     ? TYPE_STRUCTURE
                                                     : typeDefn.isCommand()
                                                                           ? TYPE_COMMAND
                                                                           : tableInfo.getType();

            // Check if the table type is recognized and that the table's data
            // successfully loaded
            if (tableType != null && !tableInfo.isErrorFlag())
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

                // Get the table's system from the system name data field, if
                // it exists
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
                    String descColName;
                    String unitsColName;

                    // Get the default column indices
                    int varColumn = typeDefn.getColumnIndexByInputType(InputDataType.VARIABLE);
                    int typeColumn = typeDefn.getColumnIndexByInputType(InputDataType.PRIM_AND_STRUCT);
                    int sizeColumn = typeDefn.getColumnIndexByInputType(InputDataType.ARRAY_INDEX);
                    int bitColumn = typeDefn.getColumnIndexByInputType(InputDataType.BIT_LENGTH);
                    List<Integer> enumColumn = typeDefn.getColumnIndicesByInputType(InputDataType.ENUMERATION);
                    int descColumn = -1;
                    int unitsColumn = -1;

                    // Get the description column name
                    if ((descColName = typeDefn.getColumnNameByInputType(InputDataType.DESCRIPTION)) != null)
                    {
                        // Get the description column index
                        descColumn = typeDefn.getVisibleColumnIndexByUserName(descColName);
                    }

                    // Get the units column name
                    if ((unitsColName = typeDefn.getColumnNameByInputType(InputDataType.UNITS)) != null)
                    {
                        // Get the units column index
                        unitsColumn = typeDefn.getVisibleColumnIndexByUserName(unitsColName);
                    }

                    // Get the structure table description
                    String description = tableInfo.getDescription().isEmpty()
                                                                             ? null
                                                                             : tableInfo.getDescription();

                    // Add the structure to the telemetry data sheet
                    nameSpace = addNameSpace(systemName,
                                             tableInfo.getTablePath(),
                                             tableInfo.getProtoVariableName(),
                                             description);

                    // Create a list containing the table type
                    List<String[]> typeData = new ArrayList<String[]>();
                    typeData.add(new String[] {EDSTags.TABLE_TYPE.getTag(),
                                               tableInfo.getType()});

                    // Store the table type attribute information
                    storeOtherAttributes(nameSpace,
                                         EDSTags.TABLE_TYPE,
                                         typeData);

                    // Store the data field attribute information
                    storeOtherAttributes(nameSpace,
                                         EDSTags.DATA_FIELD,
                                         getDataFields(tableInfo));

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
                    }
                }
                // Not a structure table
                else
                {
                    // Get the structure table description
                    String description = tableInfo.getDescription().isEmpty()
                                                                             ? null
                                                                             : tableInfo.getDescription();

                    // Create a name space if not already present
                    nameSpace = addNameSpace(systemName,
                                             "",
                                             tableName,
                                             description);

                    // Create a list containing the table type
                    List<String[]> typeData = new ArrayList<String[]>();
                    typeData.add(new String[] {EDSTags.TABLE_TYPE.getTag(),
                                               tableInfo.getType()});

                    // Store the table type attribute information
                    storeOtherAttributes(nameSpace,
                                         EDSTags.TABLE_TYPE,
                                         typeData);

                    // Store the data field attribute information
                    storeOtherAttributes(nameSpace,
                                         EDSTags.DATA_FIELD,
                                         getDataFields(tableInfo));

                    // Check if this is a command table
                    if (tableType.equals(TYPE_COMMAND))
                    {
                        // Add the command(s) from this table to the data sheet
                        addNameSpaceCommands(nameSpace, tableInfo);
                    }
                    // Not a command (or structure) table; i.e., it's a
                    // user-defined table type
                    else
                    {
                        // Create a list to contain the table's data field
                        // names and associated values, description, column
                        // information (for a non-command table type), and
                        // table name and type
                        List<String[]> otherData = new ArrayList<String[]>();

                        // Store this table's data as additional data for the
                        // current data sheet. Step through each row of the
                        // table
                        for (int row = 0; row < tableInfo.getData().length; row++)
                        {
                            // Step through each column in the row
                            for (int column = 0; column < tableInfo.getData()[row].length; column++)
                            {
                                // Check that this isn't the primary key or row
                                // index column, and that the column value
                                // isn't blank
                                if (column != DefaultColumn.PRIMARY_KEY.ordinal()
                                    && column != DefaultColumn.ROW_INDEX.ordinal()
                                    && !tableInfo.getData()[row][column].isEmpty())
                                {
                                    // Store the data column name, value, and
                                    // row number
                                    otherData.add(new String[] {EDSTags.COLUMN.getColumnIdentifier(typeDefn.getColumnNamesUser()[column],
                                                                                                   row),
                                                                tableInfo.getData()[row][column]});
                                }
                            }
                        }

                        // Store the additional column attribute information
                        storeOtherAttributes(nameSpace,
                                             EDSTags.COLUMN,
                                             otherData);
                    }
                }
            }
        }
    }

    /**************************************************************************
     * Return an array containing the specified table's data field names and
     * values
     * 
     * @param tableInfo
     *            TableInformation reference for the current node
     * 
     * @return List containing the data field names and values
     *************************************************************************/
    private List<String[]> getDataFields(TableInformation tableInfo)
    {
        List<String[]> fieldData = new ArrayList<String[]>();

        // Step through the command table's data field information
        for (FieldInformation field : tableInfo.getFieldHandler().getFieldInformation())
        {
            // Store the data field information
            fieldData.add(new String[] {EDSTags.DATA_FIELD.getTag(),
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

        return fieldData;
    }

    /**************************************************************************
     * Create a new name space as a child within the specified name space. If
     * the specified name space is null then this is the root data sheet
     * 
     * @param systemName
     *            system name; null or blank if no system (e.g., macro
     *            definitions)
     * 
     * @param path
     *            table path
     * 
     * @param nameSpaceName
     *            name for the new name space
     * 
     * @param shortDescription
     *            data sheet description
     * 
     * @return Reference to the new name space
     *************************************************************************/
    private NamespaceType addNameSpace(String systemName,
                                       String path,
                                       String nameSpaceName,
                                       String shortDescription)
    {
        // Check if a path exists
        if (!path.isEmpty() && path.contains(","))
        {
            // Append the path to the system name
            nameSpaceName = path + "," + nameSpaceName;
        }

        // Check if a system name is provided
        if (systemName != null && !systemName.isEmpty())
        {
            // Prepend the system name to the name space name to get the full
            // name
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

            // Create an interface set for the name space
            nameSpace.setDeclaredInterfaceSet(factory.createInterfaceDeclarationSetType());

            // Add the new names space
            dataSheet.getNamespace().add(nameSpace);
        }

        return nameSpace;
    }

    /**************************************************************************
     * Search for the name space with the same name as the search name
     * 
     * @param nameSpaceName
     *            name of the name space to search for within the name space
     *            hierarchy
     * 
     * @return Reference to the name space with the same name as the search
     *         name; null if no name space name matches the search name
     *************************************************************************/
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

    /**************************************************************************
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
     *            list containing the current table's enumeration column
     *            indices; empty list if no enumeration columns exist
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
     *************************************************************************/
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

        // Check that a valid row index exists for this variable. Since the
        // table tree is built from the existing tables, a valid variable row
        // index is always returned
        if (row != -1)
        {
            // Check if the data type is a string and if the array size column
            // isn't empty
            if (dataTypeHandler.isCharacter(tableInfo.getData()[row][typeColumn])
                && dataTypeHandler.getSizeInBytes(tableInfo.getData()[row][typeColumn]) > 1
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
            }

            // Step through each column in the row
            for (int column = 0; column < tableInfo.getData()[row].length; column++)
            {
                // Check that this is not the primary key, row index, variable
                // or name column, or the bit length column and the variable
                // has no bit length, and that a value exists in the column
                if (((column != DefaultColumn.PRIMARY_KEY.ordinal()
                      && column != DefaultColumn.ROW_INDEX.ordinal()
                      && column != varColumn)
                      || (column == bitColumn && bitLength != null))
                    && !tableInfo.getData()[row][column].isEmpty())
                {
                    // Store the column value size. This is treated as
                    // ancillary data
                    otherCols.add(new String[] {EDSTags.COLUMN.getColumnIdentifier(typeDefn.getColumnNamesUser()[column],
                                                                                   row),
                                                tableInfo.getData()[row][column]});

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
                     description);

        // Add any additional column data
        storeOtherAttributes(nameSpace, EDSTags.COLUMN, otherCols);
    }

    /**************************************************************************
     * Add the command(s) from a table to the specified name space
     * 
     * @param nameSpace
     *            name space for this node
     * 
     * @param tableInfo
     *            TableInformation reference for the current node
     *************************************************************************/
    private void addNameSpaceCommands(NamespaceType nameSpace,
                                      TableInformation tableInfo)
    {
        List<String[]> otherCols = new ArrayList<String[]>();

        // Get the list containing command argument name, data type,
        // enumeration, minimum, maximum, and other associated column indices
        // for each argument grouping
        List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandColumns(false);

        // Step through each row in the table
        for (String[] rowData : tableInfo.getData())
        {
            // Initialize the command attributes and argument number list
            String commandName = null;
            String commandDescription = null;
            List<CommandArgumentType> arguments = new ArrayList<CommandArgumentType>();

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
                    // Get the column name
                    String colName = typeDefn.getColumnNamesUser()[colA];

                    // Check if this command name column
                    if (colName.equalsIgnoreCase(typeDefn.getColumnNameByInputType(InputDataType.COMMAND_NAME)))
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
                                        colName = typeDefn.getColumnNamesUser()[colB];

                                        // Store the column name and value.
                                        // This is treated as ancillary data
                                        // for this command argument set
                                        otherArgCols.add(new String[] {EDSTags.COLUMN.getColumnIdentifier(colName,
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
                                            // enumeration and column name
                                            enumeration = rowData[colB];

                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;
                                        }
                                        // Check if the column is among the
                                        // associated command argument columns
                                        else if (cmdArg.getOther().contains(colB))
                                        {
                                            // Set the flag indicating the
                                            // column is a command argument
                                            isCmdArg[colB] = true;

                                            // Check if the command argument
                                            // column name contains the text
                                            // "units"
                                            if (colName.matches(CONTAINS_UNITS))
                                            {
                                                // Assume this is the command
                                                // argument's units column
                                                units = rowData[colB];
                                            }
                                            // Check if the command argument
                                            // column name contains the text
                                            // "description"
                                            else if (colName.matches(CONTAINS_DESCRIPTION))
                                            {
                                                // Assume this is the command
                                                // argument's description
                                                // column
                                                description = rowData[colB];
                                            }
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
                                        else
                                        {
                                            // Remove the column information
                                            otherArgCols.remove(otherArgCols.size() - 1);
                                            colName = null;
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
                                    // Add the command argument to the list
                                    arguments.add(addCommandArgument(nameSpace,
                                                                     commandName,
                                                                     argName,
                                                                     dataType,
                                                                     enumeration,
                                                                     units,
                                                                     description,
                                                                     otherArgCols));
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
                    // generic data for this command
                    otherCols.add(new String[] {EDSTags.COLUMN.getColumnIdentifier(colName,
                                                                                   Integer.valueOf(rowData[DefaultColumn.ROW_INDEX.ordinal()]) - 1),
                                                rowData[col]});
                }
            }

            // Check if the command name exists
            if (commandName != null)
            {
                // Add the command information
                addCommand(nameSpace,
                           commandName,
                           arguments,
                           commandDescription);
            }
        }

        // Add any additional column data
        storeOtherAttributes(nameSpace, EDSTags.COLUMN, otherCols);
    }

    /**************************************************************************
     * Create the parameter set for the specified name space
     * 
     * @param nameSpace
     *            name space
     * 
     * @return Reference to the parameter set
     *************************************************************************/
    private InterfaceDeclarationType createParameterSet(NamespaceType nameSpace)
    {
        InterfaceDeclarationType intParmType = factory.createInterfaceDeclarationType();
        intParmType.setParameterSet(factory.createInterfaceParameterSetType());
        nameSpace.getDeclaredInterfaceSet().getInterface().add(intParmType);
        return intParmType;
    }

    /**************************************************************************
     * Create the command set for the specified name space
     * 
     * @param nameSpace
     *            name space
     * 
     * @return Reference to the command set
     *************************************************************************/
    private InterfaceDeclarationType createCommandSet(NamespaceType nameSpace)
    {
        InterfaceDeclarationType intCmdType = factory.createInterfaceDeclarationType();
        intCmdType.setCommandSet(factory.createInterfaceCommandSetType());
        nameSpace.getDeclaredInterfaceSet().getInterface().add(intCmdType);
        return intCmdType;
    }

    /**************************************************************************
     * Create a user-defined set for the specified name space
     * 
     * @param nameSpace
     *            name space
     * 
     * @return Reference to the user-defined set
     *************************************************************************/
    private InterfaceDeclarationType createUserSet(NamespaceType nameSpace)
    {
        InterfaceDeclarationType intUserType = factory.createInterfaceDeclarationType();
        intUserType.setGenericTypeSet(factory.createGenericTypeSetType());
        nameSpace.getDeclaredInterfaceSet().getInterface().add(intUserType);
        return intUserType;
    }

    /**************************************************************************
     * Add a telemetry parameter to the name space's parameter set. Create the
     * parameter set for the name space if it does not exist
     * 
     * @param nameSpace
     *            name space
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
     * @param enumerations
     *            list containing enumerations in the format <enum label>|<enum
     *            value>[|...][,...]; null to not specify
     * 
     * @param units
     *            parameter units
     * 
     * @param shortDescription
     *            short description of the parameter
     *************************************************************************/
    private void addParameter(NamespaceType nameSpace,
                              String systemPath,
                              String parameterName,
                              String dataType,
                              List<String> enumerations,
                              String units,
                              String shortDescription)
    {
        // Build the parameter attributes
        InterfaceParameterType parameter = factory.createInterfaceParameterType();
        parameter.setName(parameterName);
        parameter.setShortDescription(shortDescription);

        // Check if a data type is provided is a primitive type. If none is
        // provided then no entry for this parameter appears under the
        // ParameterTypeSet, but it will appear under the ParameterSet
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

                        // Check if the data type set doesn't exist, which is
                        // the case for the first enumerated parameter
                        if (dataTypeSet == null)
                        {
                            // Create the data type set
                            dataTypeSet = factory.createDataTypeSetType();
                        }

                        // Create an enumeration type and enumeration list
                        EnumeratedDataType enumType = factory.createEnumeratedDataType();
                        EnumerationListType enumList = createEnumerationList(nameSpace,
                                                                             enumeration);
                        // Set the integer encoding (the only encoding
                        // available for an enumeration) and the size in bits
                        IntegerDataEncodingType intEncodingType = factory.createIntegerDataEncodingType();
                        intEncodingType.setSizeInBits(BigInteger.valueOf(dataTypeHandler.getSizeInBits(dataType)));

                        // Check if the data type is an unsigned integer
                        if (dataTypeHandler.isUnsignedInt(dataType))
                        {
                            // Set the encoding type to indicate an unsigned
                            // integer
                            intEncodingType.setEncoding(IntegerEncodingType.UNSIGNED);
                        }

                        // Set the enumeration parameter name, encoding type,
                        // and enumeration list attribute
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
            // This throws an illegal argument exception if the unit is not one
            // of those in the Unit enum class
            Unit unit = Unit.fromValue(units);
            SemanticsType semType = factory.createSemanticsType();
            semType.setUnit(unit);
            parameter.setSemantics(semType);
        }
        catch (IllegalArgumentException iae)
        {
            // TODO User-supplied units don't match one of the hard-coded Unit
            // types, which are the only ones that can be supplied to the
            // semantics setUnit() method. The hard-coded unit types is a very
            // limited list (and metric only)
        }

        InterfaceDeclarationType intParmType = null;

        // Step through the interfaces in order to locate the name space's
        // parameter set
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
    }

    /**************************************************************************
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
     *************************************************************************/
    private void addCommand(NamespaceType nameSpace,
                            String commandName,
                            List<CommandArgumentType> arguments,
                            String shortDescription)
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

        // Step through the interfaces in order to locate the name space's
        // command set
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
     * @param otherArgCols
     *            list of string arrays containing other argument column data
     *            in the format [column name][data value]
     *************************************************************************/
    private CommandArgumentType addCommandArgument(NamespaceType nameSpace,
                                                   String commandName,
                                                   String argumentName,
                                                   String dataType,
                                                   String enumeration,
                                                   String units,
                                                   String shortDescription,
                                                   List<String[]> otherArgCols)
    {
        CommandArgumentType argType = factory.createCommandArgumentType();
        argType.setName(argumentName);
        argType.setShortDescription(shortDescription);

        if (enumeration != null)
        {
            // Get the data type set for this name space
            DataTypeSetType dataTypeSet = nameSpace.getDataTypeSet();

            // Check if the data type set doesn't exist, which is the case for
            // the first enumerated parameter
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

        // Set the command argument data type
        argType.setType(dataType.toString().toLowerCase());

        // Check if other column data exists
        if (!otherArgCols.isEmpty())
        {
            // Add any additional column data
            storeOtherAttributes(nameSpace,
                                 EDSTags.COLUMN,
                                 otherArgCols);
        }

        // Check if this data type hasn't already been referenced
        if (!referencedDataTypes.contains(dataType))
        {
            // Add the data type to the reference list
            referencedDataTypes.add(dataType);
        }

        return argType;
    }

    /**************************************************************************
     * Build an enumeration list from the supplied enumeration string
     *
     * @param nameSpace
     *            name space
     * 
     * @param enumeration
     *            enumeration in the format <enum value><enum value
     *            separator><enum label>[<enum value separator>...][<enum pair
     *            separator>...]
     * 
     * @return Enumeration list for the supplied enumeration string
     *************************************************************************/
    private EnumerationListType createEnumerationList(NamespaceType nameSpace,
                                                      String enumeration)
    {
        EnumerationListType enumList = factory.createEnumerationListType();

        try
        {
            // Get the character that separates the enumeration value from the
            // associated label
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
                                                          + nameSpace.getName()
                                                          + "'; "
                                                          + ce.getMessage(),
                                                      "Enumeration Error",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);
        }

        return enumList;
    }

    /**************************************************************************
     * Store any additional attribute information in the specified name space
     * under the generic interface declaration
     * 
     * @param nameSpace
     *            parent data sheet for the new system; null for the root data
     *            sheet
     * 
     * @param attrType
     *            description of the additional attributes
     * 
     * @param otherAttrs
     *            list containing other attribute data in the format [attribute
     *            name][attribute value]
     *************************************************************************/
    private void storeOtherAttributes(NamespaceType nameSpace,
                                      EDSTags attrType,
                                      List<String[]> otherAttrs)
    {
        // Check if other attribute data is provided
        if (otherAttrs != null && !otherAttrs.isEmpty())
        {
            // Create the user-defined set in this name space for this
            // parameter to store the other attribute information
            InterfaceDeclarationType intUserType = createUserSet(nameSpace);
            intUserType.setName(attrType.getTag());

            // Step through each other attribute
            for (String[] attr : otherAttrs)
            {
                // Build the attribute information
                GenericTypeType genType = factory.createGenericTypeType();
                genType.setName(attr[0]);
                genType.setShortDescription(attr[1]);

                // Add the attribute to the user-defined set
                intUserType.getGenericTypeSet().getGenericType().add(genType);
            }
        }
    }
}
