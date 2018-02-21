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
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.ccsds.schema.sois.seds.CommandArgumentType;
import org.ccsds.schema.sois.seds.DataSheetType;
import org.ccsds.schema.sois.seds.DataTypeSetType;
import org.ccsds.schema.sois.seds.DeviceType;
import org.ccsds.schema.sois.seds.EnumeratedDataType;
import org.ccsds.schema.sois.seds.EnumerationListType;
import org.ccsds.schema.sois.seds.IntegerDataEncodingType;
import org.ccsds.schema.sois.seds.IntegerEncodingType;
import org.ccsds.schema.sois.seds.InterfaceCommandType;
import org.ccsds.schema.sois.seds.InterfaceDeclarationType;
import org.ccsds.schema.sois.seds.InterfaceParameterType;
import org.ccsds.schema.sois.seds.NamespaceType;
import org.ccsds.schema.sois.seds.ObjectFactory;
import org.ccsds.schema.sois.seds.SemanticsType;
import org.ccsds.schema.sois.seds.Unit;
import org.ccsds.schema.sois.seds.ValueEnumerationType;

import CCDD.CcddClasses.AssociatedColumns;
import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.TableDefinition;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.DialogOption;
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
    private final CcddReservedMsgIDHandler rsvMsgIDHandler;
    private final CcddFieldHandler fieldHandler;

    // GUI component instantiating this class
    private final Component parent;

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
    public void importFromFile(File importFile, ImportType importType) throws CCDDException,
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
     *            unused
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
                               separators);

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
     *            system name
     *********************************************************************************************/
    private void convertTablesToEDS(String[] tableNames,
                                    boolean replaceMacros,
                                    boolean includeReservedMsgIDs,
                                    boolean includeVariablePaths,
                                    CcddVariableSizeAndConversionHandler variableHandler,
                                    String[] separators)
    {
        // Create the project's data sheet and device
        dataSheet = factory.createDataSheetType();
        project = factory.createDataSheet(dataSheet);
        device = factory.createDeviceType();
        device.setName(dbControl.getDatabaseName());
        device.setShortDescription(dbControl.getDatabaseDescription(dbControl.getDatabaseName()));
        dataSheet.setDevice(device);

        // Add the project's name spaces, parameters, and commands
        buildNameSpaces(tableNames, includeVariablePaths, variableHandler, separators);
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
                // Replace all macro names with their corresponding values
                tableInfo.setData(macroHandler.replaceAllMacros(tableInfo.getData()));

                String systemName = "DefaultSystem";

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
                                             tableInfo.getType());

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

                    // Check if this is a command table
                    if (tableType.equals(TYPE_COMMAND))
                    {
                        // Create a name space if not already present
                        nameSpace = addNameSpace(systemName,
                                                 tableName,
                                                 description,
                                                 tableInfo.getType());

                        // Add the command(s) from this table to the data sheet
                        addNameSpaceCommands(nameSpace, tableInfo);
                    }
                    // Not a command (or structure) table; i.e., it's a user-defined table type
                    else
                    {
                        // Create a name space if not already present
                        nameSpace = addNameSpace(systemName,
                                                 tableName,
                                                 description,
                                                 tableInfo.getType());
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
     * @return Reference to the new name space
     *********************************************************************************************/
    private NamespaceType addNameSpace(String systemName,
                                       String nameSpaceName,
                                       String shortDescription,
                                       String tableType)
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
     *********************************************************************************************/
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
                                                                     description));
                                }

                                // Stop searching since a match was found
                                break;
                            }
                        }
                    }
                }
            }

            // TODO NEEDS LOOKING AT
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
                }
            }

            // Check if the command name exists
            if (commandName != null)
            {
                // Add the command information
                addCommand(nameSpace, commandName, arguments, commandDescription);
            }
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
     *********************************************************************************************/
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
     *********************************************************************************************/
    private CommandArgumentType addCommandArgument(NamespaceType nameSpace,
                                                   String commandName,
                                                   String argumentName,
                                                   String dataType,
                                                   String enumeration,
                                                   String units,
                                                   String shortDescription)
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
