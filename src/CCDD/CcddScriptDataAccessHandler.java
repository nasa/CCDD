/**
 * CFS Command and Data Dictionary script data access handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_INSTANCE_NODE_NAME;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBException;

import org.omg.space.xtce.BaseDataType.UnitSet;
import org.omg.space.xtce.EntryListType;
import org.omg.space.xtce.EnumeratedDataType.EnumerationList;
import org.omg.space.xtce.NameDescriptionType;
import org.omg.space.xtce.SpaceSystemType;

import CCDD.CcddClassesComponent.ArrayListMultiple;
import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.ArrayVariable;
import CCDD.CcddClassesDataTable.AssociatedColumns;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.RateInformation;
import CCDD.CcddClassesDataTable.TableInformation;
import CCDD.CcddCommandHandler.CommandInformation;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.CopyTableEntry;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.InputTypeFormat;
import CCDD.CcddConstants.InternalTable.DataTypesColumn;
import CCDD.CcddConstants.MessageIDSortOrder;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiablePathInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.TablePathType;
import CCDD.CcddImportSupportHandler.BasePrimitiveDataType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary script data access class. This class contains public methods
 * that are accessible to the data output scripts
 *************************************************************************************************/
public class CcddScriptDataAccessHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddEventLogDialog eventLog;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFileIOHandler fileIOHandler;
    private CcddLinkHandler linkHandler;
    private final CcddFieldHandler fieldHandler;
    private CcddGroupHandler groupHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddMacroHandler macroHandler;
    private final CcddTableTreeHandler tableTree;
    private CcddApplicationSchedulerTableHandler schTable;
    private CcddCopyTableHandler copyHandler;
    private final CcddVariableHandler variableHandler;
    private final CcddCommandHandler commandHandler;
    private CcddXTCEHandler xtceHandler;
    private final CcddInputTypeHandler inputTypeHandler;

    // Reference to the script engine
    private final ScriptEngine scriptEngine;

    // Calling GUI component
    private final Component parent;

    // Name of the script file being executed
    private final String scriptFileName;

    // List of group names references in the script association
    private final List<String> groupNames;

    // Data table information array
    private final TableInformation[] tableInformation;

    /**********************************************************************************************
     * Script data access class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param scriptEngine
     *            script engine reference
     *
     * @param tableInformation
     *            array of table information
     *
     * @param linkHandler
     *            link handler reference
     *
     * @param groupHandler
     *            group handler reference
     *
     * @param scriptFileName
     *            name of the script file being executed
     *
     * @param groupNames
     *            list containing the names of any groups referenced in the script association
     *
     * @param parent
     *            reference to the GUI component from which this class was generated (script dialog
     *            if executing from within the CCDD application; main window frame if executing
     *            from the command line)
     *********************************************************************************************/
    CcddScriptDataAccessHandler(CcddMain ccddMain,
                                ScriptEngine scriptEngine,
                                TableInformation[] tableInformation,
                                CcddLinkHandler linkHandler,
                                CcddGroupHandler groupHandler,
                                String scriptFileName,
                                List<String> groupNames,
                                Component parent)
    {
        this.ccddMain = ccddMain;
        this.scriptEngine = scriptEngine;
        this.tableInformation = tableInformation;
        this.linkHandler = linkHandler;
        this.groupHandler = groupHandler;
        this.scriptFileName = scriptFileName;
        this.groupNames = groupNames;
        this.parent = parent;
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        eventLog = ccddMain.getSessionEventLog();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fileIOHandler = ccddMain.getFileIOHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        macroHandler = ccddMain.getMacroHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();
        variableHandler = ccddMain.getVariableHandler();
        commandHandler = ccddMain.getCommandHandler();
        tableTree = variableHandler.getVariableTree();
        copyHandler = null;
        xtceHandler = null;
    }

    /**********************************************************************************************
     * Get the table information for the table type specified
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command". The table type is converted to the
     *            generic type ("Structure" or "Command") if the specified type is a representative
     *            of the generic type
     *
     * @return Table information class for the type specified; return null if an instance of the
     *         table type doesn't exist
     *********************************************************************************************/
    private TableInformation getTableInformation(String tableType)
    {
        TableInformation tableInfo = null;

        // Get the type definition based on the table type name
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

        // Check if the type exists
        if (typeDefn != null)
        {
            // Check if the specified table type represents a structure
            if (typeDefn.isStructure())
            {
                // Set the type name to indicate a structure
                tableType = TYPE_STRUCTURE;
            }
            // Check if the specified table type represents a command table
            else if (typeDefn.isCommand())
            {
                // Set the type name to indicate a command table
                tableType = TYPE_COMMAND;
            }
        }

        // Step through the available table information instances
        for (TableInformation info : tableInformation)
        {
            // Check if the requested type matches the table information type; ignore case
            // sensitivity
            if (info.getType().equalsIgnoreCase(tableType))
            {
                // Store the table information reference and stop searching
                tableInfo = info;
                break;
            }
        }

        return tableInfo;
    }

    /**********************************************************************************************
     * Get the name of the script file being executed
     *
     * @return Name of the script file being executed
     *********************************************************************************************/
    public String getScriptName()
    {
        return scriptFileName;
    }

    /**********************************************************************************************
     * Get the name of the user executing the script
     *
     * @return Name of the user executing the script
     *********************************************************************************************/
    public String getUser()
    {
        return dbControl.getUser();
    }

    /**********************************************************************************************
     * Get the name of the project database name
     *
     * @return Name of the project database
     *********************************************************************************************/
    public String getDatabase()
    {
        return dbControl.getDatabaseName();
    }

    /**********************************************************************************************
     * Get the project's name
     *
     * @return Name of the project
     *********************************************************************************************/
    public String getProject()
    {
        return dbControl.getProjectName();
    }

    /**********************************************************************************************
     * Get the project's description
     *
     * @return Description of the project
     *********************************************************************************************/
    public String getProjectDescription()
    {
        return dbControl.getDatabaseDescription(dbControl.getDatabaseName());
    }

    /**********************************************************************************************
     * Get the script output folder path
     *
     * @return Script output folder path
     *********************************************************************************************/
    public String getOutputPath()
    {
        return ModifiablePathInfo.SCRIPT_OUTPUT_PATH.getPath().isEmpty()
                                                                         ? ""
                                                                         : ModifiablePathInfo.SCRIPT_OUTPUT_PATH.getPath()
                                                                           + File.separator;
    }

    /**********************************************************************************************
     * Get the number of characters in longest string in an array of strings
     *
     * @param strgArray
     *            array of strings
     *
     * @param minWidth
     *            initial minimum widths; null to use zero as the minimum
     *
     * @return Character length of the longest string in the supplied array; null if an input is
     *         invalid
     *********************************************************************************************/
    public Integer getLongestString(String[] strgArray, Integer minWidth)
    {
        // Check if no initial minimum is specified
        if (minWidth == null)
        {
            // Set the initial minimum to zero
            minWidth = 0;
        }

        // Step through each string in the supplied array
        for (String strg : strgArray)
        {
            // Check if the string's length is the longest found
            if (strg.length() > minWidth)
            {
                // Store the string length
                minWidth = strg.length();
            }
        }

        return minWidth;
    }

    /**********************************************************************************************
     * Get the number of characters in longest string in each column of an array of strings
     *
     * @param strgArray
     *            array of string arrays
     *
     * @param minWidths
     *            array of initial minimum widths; null to use zero as the minimum for each column
     *
     * @return Character length of the longest string in each column of the supplied array; null if
     *         any of the inputs is invalid
     *********************************************************************************************/
    public Integer[] getLongestStrings(String[][] strgArray, Integer[] minWidths)
    {
        // Check if the string array contains at least one row and column, and that either no
        // initial minimum widths are specified or that the number of minimum widths is greater
        // than or equal to the number of string columns
        if (strgArray.length != 0
            && strgArray[0].length != 0
            && (minWidths == null
                || minWidths.length == 0
                || minWidths.length >= strgArray[0].length))
        {
            // Check if no initial minimum widths are supplied
            if (minWidths == null || minWidths.length == 0)
            {
                // Create storage for the minimum widths
                minWidths = new Integer[strgArray[0].length];
                Arrays.fill(minWidths, 1);
            }

            // Step through each string in the supplied array
            for (String[] strg : strgArray)
            {
                // Step through each column
                for (int column = 0; column < strg.length; column++)
                {
                    // Check if the string's length is the longest found
                    if (strg[column].length() > minWidths[column])
                    {
                        // Store the string length
                        minWidths[column] = strg[column].length();
                    }
                }
            }
        }
        // An input is invalid
        else
        {
            // Set the minimum width array to null
            minWidths = null;
        }

        return minWidths;
    }

    /**********************************************************************************************
     * Get the current date and time in the form:
     *
     * dow mon dd hh:mm:ss zzz yyyy
     *
     * where: dow is the day of the week (Sun, Mon, Tue, Wed, Thu, Fri, Sat); mon is the month
     * (Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec); dd is the day of the month (01
     * through 31), as two decimal digits; hh is the hour of the day (00 through 23), as two
     * decimal digits; mm is the minute within the hour (00 through 59), as two decimal digits; ss
     * is the second within the minute (00 through 61, as two decimal digits; zzz is the time zone
     * (and may reflect daylight saving time); yyyy is the year, as four decimal digits
     *
     * @return Current date and time
     *********************************************************************************************/
    public String getDateAndTime()
    {
        return new Date().toString();
    }

    /**********************************************************************************************
     * Get the array containing the user-defined data type names and their corresponding
     * C-language, size (in bytes), and base data type values
     *
     * @return Array where each row contains a user-defined data type name and its corresponding
     *         C-language, size (in bytes), and base data type values
     *********************************************************************************************/
    public String[][] getDataTypeDefinitions()
    {
        return dataTypeHandler.getDataTypeData().toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a primitive type
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is a primitive; false otherwise
     *********************************************************************************************/
    public boolean isDataTypePrimitive(String dataType)
    {
        return dataTypeHandler.isPrimitive(dataType);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a signed or unsigned integer
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is an integer (signed or unsigned); false otherwise
     *********************************************************************************************/
    public boolean isDataTypeInteger(String dataType)
    {
        return dataTypeHandler.isInteger(dataType);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is an unsigned integer
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is an unsigned integer; false otherwise
     *********************************************************************************************/
    public boolean isDataTypeUnsignedInt(String dataType)
    {
        return dataTypeHandler.isUnsignedInt(dataType);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a float or double
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is a float or double; false otherwise
     *********************************************************************************************/
    public boolean isDataTypeFloat(String dataType)
    {
        return dataTypeHandler.isFloat(dataType);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a character or string
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is a character or string; false otherwise
     *********************************************************************************************/
    public boolean isDataTypeCharacter(String dataType)
    {
        return dataTypeHandler.isCharacter(dataType);
    }

    /**********************************************************************************************
     * Determine if the supplied data type is a character string
     *
     * @param dataType
     *            name of the data type to test
     *
     * @return true if the supplied data type is a character string; false otherwise
     *********************************************************************************************/
    public boolean isDataTypeString(String dataType)
    {
        return dataTypeHandler.isString(dataType);
    }

    /**********************************************************************************************
     * Get the C type for the specified data type
     *
     * @param dataType
     *            name of the primitive data type
     *
     * @return C type for the specified data type; returns null if the data type doesn't exist or
     *         isn't a primitive type
     *********************************************************************************************/
    public String getCDataType(String dataType)
    {
        String cType = null;

        // Get the base data type information based on the data type
        String[] dataTypeInfo = dataTypeHandler.getDataTypeByName(dataType);

        // Check if the data type exists
        if (dataTypeInfo != null)
        {
            // Get the C type for the data type
            cType = dataTypeInfo[DataTypesColumn.C_NAME.ordinal()];
        }

        return cType;
    }

    /**********************************************************************************************
     * Get the base type for the specified data type
     *
     * @param dataType
     *            name of the primitive data type
     *
     * @return Base type for the specified data type; returns null if the data type doesn't exist
     *         or isn't a primitive type
     *********************************************************************************************/
    public String getBaseDataType(String dataType)
    {
        String baseType = null;

        // Get the base data type information based on the data type
        BaseDataTypeInfo baseTypeInfo = dataTypeHandler.getBaseDataType(dataType);

        // Check if the data type exists
        if (baseTypeInfo != null)
        {
            // Get the base type for the data type
            baseType = baseTypeInfo.getName();
        }

        return baseType;
    }

    /**********************************************************************************************
     * Get the number of bytes for the specified data type
     *
     * @param dataType
     *            name of the structure or primitive data type
     *
     * @return Number of bytes required to store the data type; returns 0 if the data type doesn't
     *         exist
     *********************************************************************************************/
    public int getDataTypeSizeInBytes(String dataType)
    {
        return variableHandler.getDataTypeSizeInBytes(dataType);
    }

    /**********************************************************************************************
     * Get the number of bits for the specified data type
     *
     * @param dataType
     *            name of the structure or primitive data type
     *
     * @return Number of bits required to store the data type; returns 0 if the data type doesn't
     *         exist
     *********************************************************************************************/
    public int getDataTypeSizeInBits(String dataType)
    {
        return variableHandler.getDataTypeSizeInBytes(dataType) * 8;
    }

    /**********************************************************************************************
     * Convert a primitive data type into its ITOS encoded form
     *
     * @param dataType
     *            name of the data type (e.g., "uint16" or "double")
     *
     * @param encoding
     *            "SINGLE_CHAR" to get the single character encoding (e.g., "I" for any integer
     *            type); "TWO_CHAR" to get the encoding character with the data type size (e.g.,
     *            "I4" for a 4-byte integer); "BIG_ENDIAN" to get the encoding as big endian;
     *            "BIG_ENDIAN_SWAP" to get the encoding as a big endian with byte swapping;
     *            "LITTLE_ENDIAN" to get the encoding as little endian; "LITTLE_ENDIAN_SWAP" to get
     *            the encoding as a little endian with byte swapping. The encoding parameter is
     *            case insensitive
     *
     * @return ITOS encoded form of the data type in the format requested (e.g., "int32" and
     *         "LITTLE_ENDIAN" returns "I12345678"); returns the data type, unmodified, if the data
     *         type is a table (i.e., it's a structure), or null if the data type is unrecognized
     *********************************************************************************************/
    public String getITOSEncodedDataType(String dataType, String encoding)
    {
        String encodedType = null;

        // Check if the data type is a recognized primitive
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Set the encoding character based on the data type's base type. Check if the data
            // type is in integer
            if (dataTypeHandler.isInteger(dataType))
            {
                // Check if the integer is unsigned
                if (dataTypeHandler.isUnsignedInt(dataType))
                {
                    encodedType = "U";
                }
                // The integer is signed
                else
                {
                    encodedType = "I";
                }
            }
            // Check if the data type is a floating point
            else if (dataTypeHandler.isFloat(dataType))
            {
                encodedType = "F";
            }
            // Check if the data type is a character or string
            else if (dataTypeHandler.isCharacter(dataType))
            {
                encodedType = "S";
            }
            // Check if the data type is a pointer
            else if (dataTypeHandler.isPointer(dataType))
            {
                encodedType = "U";
            }
            // The data type isn't recognized; set to 'raw'
            else
            {
                encodedType = "R";
            }

            // Check if the data type is recognized
            if (!encodedType.equals("R"))
            {
                int size = 0;

                switch (encodedType)
                {
                    case "U":
                    case "I":
                    case "F":
                        // Get the data type's size in bytes
                        size = dataTypeHandler.getSizeInBytes(dataType);
                        break;

                    case "S":
                        // All string types (characters and strings) are set to 1
                        size = 1;
                        break;
                }

                switch (encoding.toUpperCase())
                {
                    case "BIG_ENDIAN":
                        // Example byte order: 12345678
                        // Step through each byte
                        for (int i = 1; i <= size; i++)
                        {
                            // Append the byte number to the encoding string
                            encodedType += String.valueOf(i);
                        }

                        break;

                    case "BIG_ENDIAN_SWAP":
                        // Example byte order: 21436587
                        // Check if the data type is a single byte
                        if (size == 1)
                        {
                            // Append a '1' to the encoding string
                            encodedType += String.valueOf(1);
                        }
                        // The data type has multiple bytes
                        else
                        {
                            // Step through every other byte byte
                            for (int i = 1; i <= size; i += 2)
                            {
                                // Append the byte number for the next byte and the current byte to
                                // the encoding string
                                encodedType += String.valueOf(i + 1) + String.valueOf(i);
                            }
                        }

                        break;

                    case "LITTLE_ENDIAN":
                        // Example byte order: 87654321
                        // Step through each byte in reverse order
                        for (int i = size; i > 0; i--)
                        {
                            // Append the byte number to the encoding string
                            encodedType += String.valueOf(i);
                        }

                        break;

                    case "LITTLE_ENDIAN_SWAP":
                        // Example byte order: 78563412
                        // Check if the data type is a single byte
                        if (size == 1)
                        {
                            // Append a '1' to the encoding string
                            encodedType += String.valueOf(1);
                        }
                        // The data type has multiple bytes
                        else
                        {
                            // Step through every other byte in reverse order
                            for (int i = size; i > 0; i -= 2)
                            {
                                // Append the byte number for the previous byte and the current
                                // byte to the encoding string
                                encodedType += String.valueOf(i - 1) + String.valueOf(i);
                            }
                        }

                        break;

                    case "TWO_CHAR":
                        // Example: 8 byte unsigned integer = U8
                        encodedType += size;
                        break;

                    case "SINGLE_CHAR":
                        // Example: float = F
                    default:
                        break;
                }
            }
            // The data type is unrecognized; treat as 'raw'. Check if the request is not for the
            // single character encoding
            else if (!encoding.equalsIgnoreCase("SINGLE_CHAR"))
            {
                // Append a '0' to the encoding string
                encodedType += String.valueOf(0);
            }
        }
        // Check if the data type matches a table name (i.e., it's a structure)
        else if (dbTable.isTableExists(dataType, ccddMain.getMainFrame()))
        {
            // Use the supplied data type, unmodified, as the encoded type
            encodedType = dataType;
        }

        return encodedType;
    }

    /**********************************************************************************************
     * Get the ITOS limit name based on the supplied index value
     *
     * @param index
     *            0 = redLow, 1 = yellowLow, 2 = yellowHigh, 3 = redHigh
     *
     * @return ITOS limit name; returns blank if the index is invalid
     *********************************************************************************************/
    public String getITOSLimitName(int index)
    {
        String[] limitNames = new String[] {"redLow",
                                            "yellowLow",
                                            "yellowHigh",
                                            "redHigh",
                                            ""};

        // Check if the index is invalid
        if (index < 0 || index > limitNames.length - 1)
        {
            // Set the index to the blank array member
            index = limitNames.length - 1;
        }

        return limitNames[index];
    }

    /**********************************************************************************************
     * Get the array of root structure table names (child table names are excluded). Convenience
     * method that assumes the table type is a structure
     *
     * @return Array of root structure table names; returns a blank if an instance of the structure
     *         table type doesn't exist
     *********************************************************************************************/
    public String[] getRootStructureTableNames()
    {
        return getRootTableNames(TYPE_STRUCTURE);
    }

    /**********************************************************************************************
     * Get the array of the root table names for the supplied table type. Note that only structure
     * tables can have child tables so using this method for non-structure tables returns the same
     * list of tables as getTableNames(typeName)
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @return Array of root table names for the type specified; returns a blank if an instance of
     *         the table type doesn't exist
     *********************************************************************************************/
    public String[] getRootTableNames(String tableType)
    {
        List<String> name = new ArrayList<String>();

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists and that there is data for the specified table type
        if (tableInfo != null && tableInfo.getData().length != 0)
        {
            // Step through each row in the table data
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Calculate the column index for the structure path
                int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

                // Split the table path into an array
                String[] parts = tableInfo.getData()[row][pathColumn].toString()
                                                                     .split(Pattern.quote(","));

                // Check if the list doesn't already contain the parent name
                if (!name.contains(parts[0]))
                {
                    // Add the parent name to the list
                    name.add(parts[0]);
                }
            }
        }

        return name.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the number of rows of data in the structure table
     *
     * @return Number of rows of data in the table for the table type "structure"; -1 if an
     *         instance of the structure table type doesn't exist
     *********************************************************************************************/
    public int getStructureTableNumRows()
    {
        return getTableNumRows(TYPE_STRUCTURE);
    }

    /**********************************************************************************************
     * Get the number of rows of data in the command table
     *
     * @return Number of rows of data in the table of the type "command"; -1 if an instance of the
     *         command table type doesn't exist
     *********************************************************************************************/
    public int getCommandTableNumRows()
    {
        return getTableNumRows(TYPE_COMMAND);
    }

    /**********************************************************************************************
     * Get the number of rows of data in the table for the specified table type
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @return Number of rows of data in the table for the table type specified; -1 if an instance
     *         of the table type doesn't exist
     *********************************************************************************************/
    public int getTableNumRows(String tableType)
    {
        int numRows = -1;

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Store the number of rows for the table
            numRows = tableInfo.getData().length;
        }

        return numRows;
    }

    /**********************************************************************************************
     * Get the number of rows of data for all table types
     *
     * @return Number of rows of data for all table types; 0 if there is no table data
     *********************************************************************************************/
    public int getTableNumRows()
    {
        int numRows = 0;

        // Step through the available table information instances
        for (TableInformation info : tableInformation)
        {
            // Add the table's number of rows to the total
            numRows += info.getData().length;
        }

        return numRows;
    }

    /**********************************************************************************************
     * Get the prototype structure table name to which the specified row's data belongs.
     * Convenience method that assumes the table type is "structure"
     *
     * @param row
     *            table data row index
     *
     * @return Prototype structure table name to which the current row's parameter belongs; returns
     *         a blank if an instance of the structure table type or the row doesn't exist
     *********************************************************************************************/
    public String getStructureTableNameByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.PROTOTYPE, true);
    }

    /**********************************************************************************************
     * Get the command table name to which the specified row's data belongs. Convenience method
     * that assumes the table type is "command"
     *
     * @param row
     *            table data row index
     *
     * @return Command table name to which the current row's parameter belongs; returns a blank if
     *         an instance of the command table type or the row doesn't exist
     *********************************************************************************************/
    public String getCommandTableNameByRow(int row)
    {
        return getTablePathByRow(TYPE_COMMAND, row, TablePathType.PROTOTYPE, true);
    }

    /**********************************************************************************************
     * Get the prototype table name for the type specified to which the specified row's parameter
     * belongs
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return Prototype table name to which the current row's parameter belongs; return a blank if
     *         an instance of the table type or the row doesn't exist
     *********************************************************************************************/
    public String getTableNameByRow(String tableType, int row)
    {
        return getTablePathByRow(tableType, row, TablePathType.PROTOTYPE, true);
    }

    /**********************************************************************************************
     * Get array of all structure table names, including paths for child structure tables,
     * referenced in the table data. Convenience method that specifies the table type as
     * "structure"
     *
     * @return Array of all structure table names, including paths for child structure tables;
     *         returns an empty array if an instance of the structure table type doesn't exist
     *********************************************************************************************/
    public String[] getStructureTablePaths()
    {
        return getTableNames(TYPE_STRUCTURE, false);
    }

    /**********************************************************************************************
     * Get array of all prototype structure table names referenced in the table data. Convenience
     * method that specifies the table type as "structure"
     *
     * @return Array of all prototype structure table names; returns an empty array if an instance
     *         of the structure table type doesn't exist
     *********************************************************************************************/
    public String[] getStructureTableNames()
    {
        return getTableNames(TYPE_STRUCTURE, true);
    }

    /**********************************************************************************************
     * Get array of all command table names referenced in the table data. Convenience method that
     * specifies the table type as "command"
     *
     * @return Array of all command table names; returns an empty array if an instance of the
     *         command table type doesn't exist
     *********************************************************************************************/
    public String[] getCommandTableNames()
    {
        return getTableNames(TYPE_COMMAND);
    }

    /**********************************************************************************************
     * Get array of all table names, including paths for child structure tables, referenced in the
     * table data of the specified table type
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @return Array of all table names, including paths for child structure tables, represented by
     *         the table type; returns an empty array if an instance of the table type doesn't
     *         exist
     *********************************************************************************************/
    public String[] getTableNames(String tableType)
    {
        return getTableNames(tableType, false);
    }

    /**********************************************************************************************
     * Get array of all table names referenced in the table data of the specified table type
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param prototypeOnly
     *            true to return only the prototype name for any child structures; false to include
     *            the full path for child structures
     *
     * @return Array of all table names, with paths for child structure tables excluded based on
     *         the input flag, represented by the table type; returns an empty array if an instance
     *         of the table type doesn't exist
     *********************************************************************************************/
    public String[] getTableNames(String tableType, boolean prototypeOnly)
    {
        List<String> names = new ArrayList<String>();

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that there is data for the specified table type
        if (tableInfo != null && tableInfo.getData().length != 0)
        {
            // Step through each row in the table
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Calculate the column index for the table path
                int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

                // Get the table's prototype name from the path
                String tableName = tableInfo.getData()[row][pathColumn].toString();

                // Check if only prototype names should be returned for child structures
                if (prototypeOnly)
                {
                    // Get the prototype name form the table name
                    tableName = TableInformation.getPrototypeName(tableName);
                }

                // Check if the table name hasn't been added to the list
                if (!names.contains(tableName))
                {
                    // Store the table name
                    names.add(tableName);
                }
            }
        }

        return names.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get array of all table names, including paths for child structure tables, referenced in the
     * table data for all table types
     *
     * @return Array of all table names, including paths for child structure tables, referenced in
     *         the table data; empty array if no tables exists in the data
     *********************************************************************************************/
    public String[] getTableNames()
    {
        List<String> names = new ArrayList<String>();

        // Step through each table type's information
        for (TableInformation tableInfo : tableInformation)
        {
            // Check that there is data for the specified table type
            if (tableInfo.getData().length != 0)
            {
                // Step through each row in the table
                for (int row = 0; row < tableInfo.getData().length; row++)
                {
                    // Calculate the column index for the table path
                    int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

                    // Get the table's name, including the path (if it's a child structure)
                    String tableName = tableInfo.getData()[row][pathColumn].toString();

                    // Check if the table name hasn't been added to the list
                    if (!names.contains(tableName))
                    {
                        // Store the table name
                        names.add(tableName);
                    }
                }
            }
        }

        return names.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the variable name at the specified row in the structure data, with any macro name
     * replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Variable name at the specified row in the structure data, with any macro replaced by
     *         its corresponding value; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureVariableName(int row)
    {
        return getStructureVariableName(row, true);
    }

    /**********************************************************************************************
     * Get the variable name at the specified row in the structure data, with any embedded macro(s)
     * left in place
     *
     * @param row
     *            table data row index
     *
     * @return Variable name at the specified row in the structure data, with any embedded macro(s)
     *         left in place; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureVariableNameWithMacros(int row)
    {
        return getStructureVariableName(row, false);
    }

    /**********************************************************************************************
     * Get the variable name at the specified row in the structure data. Macro expansion is
     * controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Variable name at the specified row in the structure data; null if the row index is
     *         invalid
     *********************************************************************************************/
    private String getStructureVariableName(int row, boolean expandMacros)
    {
        String variableName = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the variable name
            variableName = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.VARIABLE)].toString();

            // Check if any macros should be expanded
            if (expandMacros)
            {
                // Expand any macros
                variableName = macroHandler.getMacroExpansion(variableName);
            }
        }

        return variableName;
    }

    /**********************************************************************************************
     * Get the variable data type at the specified row in the structure data
     *
     * @param row
     *            table data row index
     *
     * @return Variable data type at the specified row in the structure data; null if the row index
     *         is invalid
     *********************************************************************************************/
    public String getStructureDataType(int row)
    {
        String dataType = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the data type
            dataType = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.PRIM_AND_STRUCT)].toString();
        }

        return dataType;
    }

    /**********************************************************************************************
     * Get the variable array size at the specified row in the structure data, with any macro name
     * replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Variable array size at the specified row in the structure data, with any macro
     *         replaced by its corresponding value; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureArraySize(int row)
    {
        return getStructureArraySize(row, true);
    }

    /**********************************************************************************************
     * Get the variable array size at the specified row in the structure data, with any embedded
     * macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return Variable array size at the specified row in the structure data, with any embedded
     *         macro(s) left in place; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureArraySizeWithMacros(int row)
    {
        return getStructureArraySize(row, false);
    }

    /**********************************************************************************************
     * Get the variable array size at the specified row in the structure data. Macro expansion is
     * controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Variable array size at the specified row in the structure data; null if the row
     *         index is invalid
     *********************************************************************************************/
    private String getStructureArraySize(int row, boolean expandMacros)
    {
        String arraySize = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the array size
            arraySize = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.ARRAY_INDEX)].toString();

            // Check if any macros should be expanded
            if (expandMacros)
            {
                // Expand any macros
                arraySize = macroHandler.getMacroExpansion(arraySize);
            }
        }

        return arraySize;
    }

    /**********************************************************************************************
     * Get the variable bit length at the specified row in the structure data, with any macro name
     * replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Variable bit length at the specified row in the structure data, with any macro
     *         replaced by its corresponding value; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureBitLength(int row)
    {
        return getStructureBitLength(row, true);
    }

    /**********************************************************************************************
     * Get the variable bit length at the specified row in the structure data, with any embedded
     * macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return Variable bit length at the specified row in the structure data, with any embedded
     *         macro(s) left in place; null if the row index is invalid
     *********************************************************************************************/
    public String getStructureBitLengthWithMacros(int row)
    {
        return getStructureBitLength(row, false);
    }

    /**********************************************************************************************
     * Get the variable bit length at the specified row in the structure data. Macro expansion is
     * controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Variable bit length at the specified row in the structure data; null if the row
     *         index is invalid
     *********************************************************************************************/
    private String getStructureBitLength(int row, boolean expandMacros)
    {
        String bitLength = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the bit length
            bitLength = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.BIT_LENGTH)].toString();

            // Check if any macros should be expanded
            if (expandMacros)
            {
                // Expand any macros
                bitLength = macroHandler.getMacroExpansion(bitLength);
            }
        }

        return bitLength;
    }

    /**********************************************************************************************
     * Get the variable description at the specified row in the structure data, with any macro name
     * replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Variable description at the specified row in the structure data, with any macro
     *         replaced by its corresponding value; null if the row index is invalid or no column
     *         has the 'Description' input type
     *********************************************************************************************/
    public String getStructureDescription(int row)
    {
        return getStructureDescription(row, true);
    }

    /**********************************************************************************************
     * Get the variable description at the specified row in the structure data, with any embedded
     * macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return Variable description at the specified row in the structure data, with any embedded
     *         macro(s) left in place; null if the row index is invalid or no column has the
     *         'Description' input type
     *********************************************************************************************/
    public String getStructureDescriptionWithMacros(int row)
    {
        return getStructureDescription(row, false);
    }

    /**********************************************************************************************
     * Get the variable description at the specified row in the structure data. Macro expansion is
     * controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Variable description at the specified row in the structure data; null if the row
     *         index is invalid or no column has the 'Description' input type
     *********************************************************************************************/
    private String getStructureDescription(int row, boolean expandMacros)
    {
        String description = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the index of the first column with the 'Description' input type
            int column = typeDefn.getColumnIndexByInputType(DefaultInputType.DESCRIPTION);

            // Check if the column exists
            if (column != -1)
            {
                // Get the description
                description = tableInfo.getData()[row][column].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    description = macroHandler.getMacroExpansion(description);
                }
            }
        }

        return description;
    }

    /**********************************************************************************************
     * Get the variable units at the specified row in the structure data, with any macro name
     * replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Variable units at the specified row in the structure data, with any macro replaced
     *         by its corresponding value; null if the row index is invalid or no column has the
     *         'Units' input type
     *********************************************************************************************/
    public String getStructureUnits(int row)
    {
        return getStructureUnits(row, true);
    }

    /**********************************************************************************************
     * Get the variable units at the specified row in the structure data, with any embedded
     * macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return Variable units at the specified row in the structure data, with any embedded
     *         macro(s) left in place; null if the row index is invalid or no column has the
     *         'Units' input type
     *********************************************************************************************/
    public String getStructureUnitsWithMacros(int row)
    {
        return getStructureUnits(row, false);
    }

    /**********************************************************************************************
     * Get the variable units at the specified row in the structure data. Macro expansion is
     * controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Variable units at the specified row in the structure data; null if the row index is
     *         invalid or no column has the 'Units' input type
     *********************************************************************************************/
    private String getStructureUnits(int row, boolean expandMacros)
    {
        String units = null;

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Get the index of the first column with the 'Units' input type
            int column = typeDefn.getColumnIndexByInputType(DefaultInputType.UNITS);

            // Check if the column exists
            if (column != -1)
            {
                // Get the units
                units = tableInfo.getData()[row][column].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    units = macroHandler.getMacroExpansion(units);
                }
            }
        }

        return units;
    }

    /**********************************************************************************************
     * Get the variable enumeration(s) at the specified row in the structure data, with any macro
     * name replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Array containing the variable enumeration(s) at the specified row in the structure
     *         data, with any macro replaced by its corresponding value; null if the row index is
     *         invalid
     *********************************************************************************************/
    public String[] getStructureEnumerations(int row)
    {
        return getStructureEnumerations(row, true);
    }

    /**********************************************************************************************
     * Get the variable enumeration(s) at the specified row in the structure data, with any
     * embedded macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return Array containing the variable enumeration(s) at the specified row in the structure
     *         data, with any embedded macro(s) left in place; null if the row index is invalid
     *********************************************************************************************/
    public String[] getStructureEnumerationsWithMacros(int row)
    {
        return getStructureEnumerations(row, false);
    }

    /**********************************************************************************************
     * Get the variable enumeration(s) at the specified row in the structure data. Macro expansion
     * is controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Array containing the variable enumeration(s) at the specified row in the structure
     *         data; null if the row index is invalid
     *********************************************************************************************/
    private String[] getStructureEnumerations(int row, boolean expandMacros)
    {
        List<String> enumerations = new ArrayList<String>();

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Step through each enumeration column
            for (int enumIndex : typeDefn.getColumnIndicesByInputTypeFormat(InputTypeFormat.ENUMERATION))
            {
                // Get the enumeration
                String enumeration = tableInfo.getData()[row][enumIndex].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    enumeration = macroHandler.getMacroExpansion(enumeration);
                }
                // Add the enumeration to the list
                enumerations.add(enumeration);
            }
        }

        return enumerations.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the variable rate(s) at the specified row in the structure data
     *
     * @param row
     *            table data row index
     *
     * @return Array containing the variable rate(s) at the specified row in the structure data; an
     *         empty array if the row index is invalid
     *********************************************************************************************/
    public String[] getStructureRates(int row)
    {
        List<String> rates = new ArrayList<String>();

        // Get the table type definition for the structure table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getStructureTypeNameByRow(row));

        // Check if the table type exists and represents a structure
        if (typeDefn != null && typeDefn.isStructure())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

            // Step through each rate column
            for (int rateIndex : typeDefn.getColumnIndicesByInputType(DefaultInputType.RATE))
            {
                // Add the rate to the list
                rates.add(tableInfo.getData()[row][rateIndex].toString());
            }
        }

        return rates.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the command name at the specified row in the command data, with any macro name replaced
     * by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Command name at the specified row in the command data, with any macro replaced by
     *         its corresponding value; null if the row index is invalid
     *********************************************************************************************/
    public String getCommandName(int row)
    {
        return getCommandName(row, true);
    }

    /**********************************************************************************************
     * Get the command name at the specified row in the command data, with any embedded macro(s)
     * left in place
     *
     * @param row
     *            table data row index
     *
     * @return Command name at the specified row in the command data, with any embedded macro(s)
     *         left in place; null if the row index is invalid
     *********************************************************************************************/
    public String getCommandNameWithMacros(int row)
    {
        return getCommandName(row, false);
    }

    /**********************************************************************************************
     * Get the command name at the specified row in the command data. Macro expansion is controlled
     * by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Command name at the specified row in the command data; null if the row index is
     *         invalid
     *********************************************************************************************/
    private String getCommandName(int row, boolean expandMacros)
    {
        String commandName = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

            // Get the command name
            commandName = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME)].toString();

            // Check if any macros should be expanded
            if (expandMacros)
            {
                // Expand any macros
                commandName = macroHandler.getMacroExpansion(commandName);
            }
        }

        return commandName;
    }

    /**********************************************************************************************
     * Get the command code at the specified row in the command data, with any macro name replaced
     * by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return Command code at the specified row in the command data, with any macro replaced by
     *         its corresponding value; null if the row index is invalid
     *********************************************************************************************/
    public String getCommandCode(int row)
    {
        return getCommandCode(row, true);
    }

    /**********************************************************************************************
     * Get the command code at the specified row in the command data, with any embedded macro(s)
     * left in place
     *
     * @param row
     *            table data row index
     *
     * @return Command code at the specified row in the command data, with any embedded macro(s)
     *         left in place; null if the row index is invalid
     *********************************************************************************************/
    public String getCommandCodeWithMacros(int row)
    {
        return getCommandCode(row, false);
    }

    /**********************************************************************************************
     * Get the command code (as a string) at the specified row in the command data. Macro expansion
     * is controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Command code (as a string) at the specified row in the command data; null if the row
     *         index is invalid
     *********************************************************************************************/
    private String getCommandCode(int row, boolean expandMacros)
    {
        String commandCode = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the reference to the table information
            TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

            // Get the command code
            commandCode = tableInfo.getData()[row][typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE)].toString();

            // Check if any macros should be expanded
            if (expandMacros)
            {
                // Expand any macros
                commandCode = macroHandler.getMacroExpansion(commandCode);
            }
        }

        return commandCode;
    }

    /**********************************************************************************************
     * Get the number of arguments associated with the command table type at the specified row in
     * the command data. Macro expansion is controlled by the input flag
     *
     * @param row
     *            table data row index
     *
     * @return Number of arguments associated with the command table type at the specified row in
     *         the command data; -1 if the table type is invalid
     *********************************************************************************************/
    public int getNumCommandArguments(int row)
    {
        return getNumCommandArguments(getCommandTypeNameByRow(row));
    }

    /**********************************************************************************************
     * Get the number of arguments associated with the specified command table type
     *
     * @param tableType
     *            table type (case insensitive)
     *
     * @return Number of arguments associated with the specified command table type; -1 if the
     *         table type is invalid
     *********************************************************************************************/
    public int getNumCommandArguments(String tableType)
    {
        int numArguments = -1;

        // Get the table type definition based on the table type name
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(tableType);

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the number of arguments associated with this command table type
            numArguments = typeDefn.getAssociatedCommandArgumentColumns(false).size();
        }

        return numArguments;
    }

    /**********************************************************************************************
     * Get the argument name for the command argument specified at the specified row in the command
     * data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument name for the command argument specified at the specified row in the
     *         command data, with any macro replaced by its corresponding value; null if the
     *         argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgName(int argumentNumber, int row)
    {
        return getCommandArgName(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument name for the command argument specified at the specified row in the command
     * data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument name for the command argument specified at the specified row in the
     *         command data, with any embedded macro(s) left in place; null if the argument number
     *         or row index is invalid
     *********************************************************************************************/
    public String getCommandArgNameWithMacros(int argumentNumber, int row)
    {
        return getCommandArgName(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument name for the command argument specified at the specified row in the command
     * data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument name for the command argument specified at the specified row in the command
     *         data; null if the argument number or row index is invalid
     *********************************************************************************************/
    private String getCommandArgName(int argumentNumber, int row, boolean expandMacros)
    {
        String argName = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument name exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getName() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument name
                argName = tableInfo.getData()[row][commandArguments.get(argumentNumber).getName()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argName = macroHandler.getMacroExpansion(argName);
                }
            }
        }

        return argName;
    }

    /**********************************************************************************************
     * Get the argument data type for the command argument specified at the specified row in the
     * command data
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Argument data type for the command argument specified at the specified row in the
     *         command data; null if the argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgDataType(int argumentNumber, int row)
    {
        String argDataType = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument data type exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getDataType() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument data type
                argDataType = tableInfo.getData()[row][commandArguments.get(argumentNumber).getDataType()].toString();
            }
        }

        return argDataType;
    }

    /**********************************************************************************************
     * Get the argument array size for the command argument specified at the specified row in the
     * command data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument array size for the command argument specified at the specified row
     *         in the command data, with any macro replaced by its corresponding value; null if the
     *         argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgArraySize(int argumentNumber, int row)
    {
        return getCommandArgArraySize(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument array size for the command argument specified at the specified row in the
     * command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument array size for the command argument specified at the specified row
     *         in the command data, with any embedded macro(s) left in place; null if the argument
     *         number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgArraySizeWithMacros(int argumentNumber, int row)
    {
        return getCommandArgArraySize(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument array size for the command argument specified at the specified row in the
     * command data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument array size for the command argument specified at the specified row in the
     *         command data; null if the argument number or row index is invalid
     *********************************************************************************************/
    private String getCommandArgArraySize(int argumentNumber, int row, boolean expandMacros)
    {
        String argArraySize = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument array size exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getArraySize() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument array size
                argArraySize = tableInfo.getData()[row][commandArguments.get(argumentNumber).getArraySize()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argArraySize = macroHandler.getMacroExpansion(argArraySize);
                }
            }
        }

        return argArraySize;
    }

    /**********************************************************************************************
     * Get the argument bit length for the command argument specified at the specified row in the
     * command data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument bit length for the command argument specified at the specified row
     *         in the command data, with any macro replaced by its corresponding value; null if the
     *         argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgBitLength(int argumentNumber, int row)
    {
        return getCommandArgBitLength(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument bit length for the command argument specified at the specified row in the
     * command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument bit length for the command argument specified at the specified row
     *         in the command data, with any embedded macro(s) left in place; null if the argument
     *         number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgBitLengthWithMacros(int argumentNumber, int row)
    {
        return getCommandArgBitLength(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument bit length for the command argument specified at the specified row in the
     * command data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument bit length for the command argument specified at the specified row in the
     *         command data; null if the argument number or row index is invalid
     *********************************************************************************************/
    private String getCommandArgBitLength(int argumentNumber, int row, boolean expandMacros)
    {
        String argBitLength = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument bit length exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getBitLength() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument bit length
                argBitLength = tableInfo.getData()[row][commandArguments.get(argumentNumber).getBitLength()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argBitLength = macroHandler.getMacroExpansion(argBitLength);
                }
            }
        }

        return argBitLength;
    }

    /**********************************************************************************************
     * Get the argument enumeration for the command argument specified at the specified row in the
     * command data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument enumeration for the command argument specified at the specified row
     *         in the command data, with any macro replaced by its corresponding value; null if the
     *         argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgEnumeration(int argumentNumber, int row)
    {
        return getCommandArgEnumeration(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument enumeration for the command argument specified at the specified row in the
     * command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument enumeration for the command argument specified at the specified row
     *         in the command data, with any embedded macro(s) left in place; null if the argument
     *         number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgEnumerationWithMacros(int argumentNumber, int row)
    {
        return getCommandArgEnumeration(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument enumeration for the command argument specified at the specified row in the
     * command data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument enumeration for the command argument specified at the specified row in the
     *         command data; null if the argument number or row index is invalid
     *********************************************************************************************/
    private String getCommandArgEnumeration(int argumentNumber, int row, boolean expandMacros)
    {
        String argEnumeration = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument enumeration exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getEnumeration() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument enumeration
                argEnumeration = tableInfo.getData()[row][commandArguments.get(argumentNumber).getEnumeration()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argEnumeration = macroHandler.getMacroExpansion(argEnumeration);
                }
            }
        }

        return argEnumeration;
    }

    /**********************************************************************************************
     * Get the argument minimum value (as a string) for the command argument specified at the
     * specified row in the command data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument minimum value (as a string) for the command argument specified at
     *         the specified row in the command data, with any macro replaced by its corresponding
     *         value; null if the argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgMinimum(int argumentNumber, int row)
    {
        return getCommandArgMinimum(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument minimum value (as a string) for the command argument specified at the
     * specified row in the command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument minimum value (as a string) for the command argument specified at
     *         the specified row in the command data, with any embedded macro(s) left in place;
     *         null if the argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgMinimumWithMacros(int argumentNumber, int row)
    {
        return getCommandArgMinimum(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument minimum value (as a string) for the command argument specified at the
     * specified row in the command data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument minimum value (as a string) for the command argument specified at the
     *         specified row in the command data; null if the argument number or row index is
     *         invalid
     *********************************************************************************************/
    private String getCommandArgMinimum(int argumentNumber, int row, boolean expandMacros)
    {
        String argMinimum = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument minimum value exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getMinimum() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument minimum value
                argMinimum = tableInfo.getData()[row][commandArguments.get(argumentNumber).getMinimum()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argMinimum = macroHandler.getMacroExpansion(argMinimum);
                }
            }
        }

        return argMinimum;
    }

    /**********************************************************************************************
     * Get the argument maximum value (as a string) for the command argument specified at the
     * specified row in the command data, with any macro name replaced by its corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument maximum value (as a string) for the command argument specified at
     *         the specified row in the command data, with any macro replaced by its corresponding
     *         value; null if the argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgMaximum(int argumentNumber, int row)
    {
        return getCommandArgMaximum(argumentNumber, row, true);
    }

    /**********************************************************************************************
     * Get the argument maximum value (as a string) for the command argument specified at the
     * specified row in the command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Get the argument maximum value (as a string) for the command argument specified at
     *         the specified row in the command data, with any embedded macro(s) left in place;
     *         null if the argument number or row index is invalid
     *********************************************************************************************/
    public String getCommandArgMaximumWithMacros(int argumentNumber, int row)
    {
        return getCommandArgMaximum(argumentNumber, row, false);
    }

    /**********************************************************************************************
     * Get the argument maximum value (as a string) for the command argument specified at the
     * specified row in the command data. Macro expansion is controlled by the input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument maximum value (as a string) for the command argument specified at the
     *         specified row in the command data; null if the argument number or row index is
     *         invalid
     *********************************************************************************************/
    private String getCommandArgMaximum(int argumentNumber, int row, boolean expandMacros)
    {
        String argMaximum = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid and that the argument maximum value exists
            if (argumentNumber < commandArguments.size()
                && commandArguments.get(argumentNumber).getMaximum() != -1)
            {
                // Get the reference to the table information
                TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                // Get the argument maximum value
                argMaximum = tableInfo.getData()[row][commandArguments.get(argumentNumber).getMaximum()].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros
                    argMaximum = macroHandler.getMacroExpansion(argMaximum);
                }
            }
        }

        return argMaximum;
    }

    /**********************************************************************************************
     * Get the argument value (as a string) for the column belonging to the command argument
     * specified at the specified row in the command data, with any macro name replaced by its
     * corresponding value
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param columnName
     *            name of the argument column for which the value is requested
     *
     * @return Get the argument value (as a string) for the column belonging to the command
     *         argument specified at the specified row in the command data, with any macro replaced
     *         by its corresponding value; null if the argument number, row index, or column name
     *         is invalid
     *********************************************************************************************/
    public String getCommandArgByColumnName(int argumentNumber, int row, String columnName)
    {
        return getCommandArgByColumnName(argumentNumber, row, columnName, true);
    }

    /**********************************************************************************************
     * Get the argument value (as a string) for the column belonging to the command argument
     * specified at the specified row in the command data, with any embedded macro(s) left in place
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param columnName
     *            name of the argument column for which the value is requested
     *
     * @return Get the argument value (as a string) for the column belonging to the command
     *         argument specified at the specified row in the command data, with any embedded
     *         macro(s) left in place; null if the argument number, row index, or column name is
     *         invalid
     *********************************************************************************************/
    public String getCommandArgByColumnNameWithMacros(int argumentNumber, int row, String columnName)
    {
        return getCommandArgByColumnName(argumentNumber, row, columnName, false);
    }

    /**********************************************************************************************
     * Get the argument value (as a string) for the column belonging to the specified command
     * argument at the specified row in the command data. Macro expansion is controlled by the
     * input flag
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @param columnName
     *            name of the argument column for which the value is requested
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Argument value (as a string) for the column belonging to the specified command
     *         argument at the specified row in the command data; null if the argument number, row
     *         index, or column name is invalid
     *********************************************************************************************/
    private String getCommandArgByColumnName(int argumentNumber,
                                             int row,
                                             String columnName,
                                             boolean expandMacros)
    {
        String argValue = null;

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid
            if (argumentNumber < commandArguments.size())
            {
                AssociatedColumns cmdColumns = commandArguments.get(argumentNumber);

                // Get the index of the specified column
                int tgtColumn = typeDefn.getColumnIndexByUserName(columnName);

                // Check if the column belongs to the specified command argument
                if (tgtColumn == cmdColumns.getName()
                    || tgtColumn == cmdColumns.getDataType()
                    || tgtColumn == cmdColumns.getArraySize()
                    || tgtColumn == cmdColumns.getBitLength()
                    || tgtColumn == cmdColumns.getDescription()
                    || tgtColumn == cmdColumns.getUnits()
                    || tgtColumn == cmdColumns.getEnumeration()
                    || tgtColumn == cmdColumns.getMinimum()
                    || tgtColumn == cmdColumns.getMaximum()
                    || cmdColumns.getOther().contains(tgtColumn))
                {
                    // Get the reference to the table information
                    TableInformation tableInfo = getTableInformation(TYPE_COMMAND);

                    // Get the argument value
                    argValue = tableInfo.getData()[row][tgtColumn].toString();

                    // Check if any macros should be expanded
                    if (expandMacros)
                    {
                        // Expand any macros
                        argValue = macroHandler.getMacroExpansion(argValue);
                    }
                }
            }
        }

        return argValue;
    }

    /**********************************************************************************************
     * Get the array of column names belonging to the specified command argument at the specified
     * row in the command data
     *
     * @param argumentNumber
     *            command argument index. The first argument is 0
     *
     * @param row
     *            table data row index
     *
     * @return Array of column names belonging to the specified command argument at the specified
     *         row in the command data; null if the argument number or row index is invalid
     *********************************************************************************************/
    public String[] getCommandArgColumnNames(int argumentNumber, int row)
    {
        List<String> argColumns = new ArrayList<String>();

        // Get the table type definition for the command table referenced in the specified row
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getCommandTypeNameByRow(row));

        // Check if the table type exists and represents a command
        if (typeDefn != null && typeDefn.isCommand())
        {
            // Get the list of command arguments associated with this command table type
            List<AssociatedColumns> commandArguments = typeDefn.getAssociatedCommandArgumentColumns(false);

            // Check if the argument number is valid
            if (argumentNumber < commandArguments.size())
            {
                AssociatedColumns cmdColumns = commandArguments.get(argumentNumber);

                // Add the argument name column name (this column must be present)
                argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getName()]);

                // Check if the argument data type column is present
                if (cmdColumns.getDataType() != -1)
                {
                    // Add the argument data type column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getDataType()]);
                }

                // Check if the argument array size column is present
                if (cmdColumns.getArraySize() != -1)
                {
                    // Add the argument array size column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getArraySize()]);
                }

                // Check if the argument bit length column is present
                if (cmdColumns.getBitLength() != -1)
                {
                    // Add the argument bit length column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getBitLength()]);
                }

                // Check if the argument description column is present
                if (cmdColumns.getDescription() != -1)
                {
                    // Add the argument description column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getDescription()]);
                }

                // Check if the argument units column is present
                if (cmdColumns.getUnits() != -1)
                {
                    // Add the argument units column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getUnits()]);
                }

                // Check if the argument enumeration column is present
                if (cmdColumns.getEnumeration() != -1)
                {
                    // Add the argument enumeration column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getEnumeration()]);
                }

                // Check if the argument minimum column is present
                if (cmdColumns.getMinimum() != -1)
                {
                    // Add the argument minimum column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getMinimum()]);
                }

                // Check if the argument maximum column is present
                if (cmdColumns.getMaximum() != -1)
                {
                    // Add the argument maximum column name
                    argColumns.add(typeDefn.getColumnNamesUser()[cmdColumns.getMaximum()]);
                }

                // Step through the other columns associated with this command argument
                for (Integer otherColumn : cmdColumns.getOther())
                {
                    // Add the argument column name
                    argColumns.add(typeDefn.getColumnNamesUser()[otherColumn]);
                }
            }
        }

        return argColumns.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the table type name referenced in the specified row of the structure table type data.
     * Convenience method that specifies the table type as "structure"
     *
     * @param row
     *            table data row index
     *
     * @return Type name referenced in the specified row of the structure table type data
     *********************************************************************************************/
    public String getStructureTypeNameByRow(int row)
    {
        return getTypeNameByRow(TYPE_STRUCTURE, row);
    }

    /**********************************************************************************************
     * Get the table type name referenced in the specified row of the command table type data.
     * Convenience method that specifies the table type as "command"
     *
     * @param row
     *            table data row index
     *
     * @return Type name referenced in the specified row of the command table type data
     *********************************************************************************************/
    public String getCommandTypeNameByRow(int row)
    {
        return getTypeNameByRow(TYPE_COMMAND, row);
    }

    /**********************************************************************************************
     * Get the the table type name referenced in the specified row of the specified table type
     * data. The data for all structure (command) types are combined. This method provides the
     * means to retrieve the specific table type to which the row data belongs based on its
     * "generic" type
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return Type name referenced in the specified row of the specified table type data. This the
     *         table's actual type name and not the generic 'Structure' or 'Command' used to access
     *         combined structure or command table data. Returns a blank if the table type name or
     *         row is invalid
     *********************************************************************************************/
    public String getTypeNameByRow(String tableType, int row)
    {
        String typeName = "";

        // Get the reference to the table information
        TableInformation tableInfo = getTableInformation(tableType);

        // Check if the table type and the row exist
        if (tableInfo != null && row < tableInfo.getData().length)
        {
            // Get the table type for the specified row
            typeName = tableInfo.getData()[row][tableInfo.getData()[row].length
                                                - TYPE_COLUMN_DELTA].toString();
        }

        return typeName;
    }

    /**********************************************************************************************
     * Get the the table type name for the specified table
     *
     * @param tableName
     *            name of the table. For a child structure this includes the path
     *
     * @return Type name for the specified table. This the table's actual type name and not the
     *         generic 'Structure' or 'Command' used to access combined structure or command table
     *         data
     *********************************************************************************************/
    public String getTypeNameByTable(String tableName)
    {
        String typeName = "";

        // Step through the available table information instances
        for (TableInformation tableInfo : tableInformation)
        {
            // Step through each row of data for this table type
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Check if the supplied table name matches the one for this row
                if (tableName.equals(tableInfo.getData()[row][tableInfo.getData()[row].length
                                                              - PATH_COLUMN_DELTA]))
                {
                    // Store the table's type name and stop searching
                    typeName = tableInfo.getData()[row][tableInfo.getData()[row].length
                                                        - TYPE_COLUMN_DELTA].toString();
                    break;
                }
            }
        }

        return typeName;
    }

    /**********************************************************************************************
     * Get the column names for the table referenced on the specified row of the structure table
     * data
     *
     * @param row
     *            structure table data row index
     *
     * @return Array containing the names of the columns of the structure table referenced in the
     *         specified row of the structure table data
     *********************************************************************************************/
    public String[] getStructureTableColumnNames(int row)
    {
        return getTableColumnNames(TYPE_STRUCTURE, row);
    }

    /**********************************************************************************************
     * Get the column names for the table referenced on the specified row of the command table data
     *
     * @param row
     *            command table data row index
     *
     * @return Array containing the names of the columns of the command table referenced in the
     *         specified row of the command table data
     *********************************************************************************************/
    public String[] getCommandTableColumnNames(int row)
    {
        return getTableColumnNames(TYPE_COMMAND, row);
    }

    /**********************************************************************************************
     * Get the table column names for the table referenced on the specified row of the table data
     * for the table type specified
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return Array containing the names of the columns of the table type referenced in the
     *         specified row of the type's table data
     *********************************************************************************************/
    public String[] getTableColumnNames(String tableType, int row)
    {
        String[] columnNames = null;

        // Get the type definition based on the table type name
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getTypeNameByRow(tableType,
                                                                                      row));

        // Check if the table type exists
        if (typeDefn != null)
        {
            // Store the names of the columns for this table type definition
            columnNames = typeDefn.getColumnNamesVisible();
        }

        return columnNames;
    }

    /**********************************************************************************************
     * Get the table column names for the table type specified
     *
     * @param typeName
     *            table type name. This is the table's actual type name and not the generic
     *            'Structure' or 'Command' used to access combined structure or command table data
     *
     * @return Array containing the names of the columns of the table type specified
     *********************************************************************************************/
    public String[] getTableColumnNamesByType(String typeName)
    {
        String[] columnNames = null;

        // Get the type definition based on the table type name
        TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(typeName);

        // Check if the table type exists
        if (typeDefn != null)
        {
            // Store the names of the columns for this table type definition
            columnNames = typeDefn.getColumnNamesVisible();
        }

        return columnNames;
    }

    /**********************************************************************************************
     * Get an array containing the names of the prototype structures in the order in which they are
     * referenced; that is, the structure array is arranged so that a structure appears in the
     * array prior to a structure that references it
     *
     * @return Array containing the names of the prototype structures in the order in which they
     *         are referenced; an empty array is returned if no structures tables are associated
     *         with the script
     *********************************************************************************************/
    public String[] getStructureTablesByReferenceOrder()
    {
        List<String> allStructs = new ArrayList<String>();
        List<String> orderedNames = new ArrayList<String>();

        // Get the list of all referenced structure names
        List<String> structureNames = Arrays.asList(getStructureTableNames());

        // Check if any structures exist
        if (!structureNames.isEmpty())
        {
            // Step through the structure data rows
            for (int row = 0; row < getStructureTableNumRows(); row++)
            {
                // Get the name of the data type column
                String dataTypeColumnName = tableTypeHandler.getColumnNameByInputType(getStructureTypeNameByRow(row),
                                                                                      DefaultInputType.PRIM_AND_STRUCT);

                // Check that the data type column exists
                if (dataTypeColumnName != null)
                {
                    // Get the variable data type
                    String dataType = getStructureTableData(dataTypeColumnName, row);

                    // Check if the data type is a pointer to a structure
                    if (dataTypeHandler.isPointer(dataType))
                    {
                        // Extract the structure from the data type's C name
                        dataType = dataTypeHandler.getDataTypeByName(dataType)[DataTypesColumn.C_NAME.ordinal()].replaceFirst("\\s\\*", "");
                    }

                    // Check if this data type is one of the structures and not already added to
                    // the list
                    if (dataType != null
                        && structureNames.contains(dataType)
                        && !allStructs.contains(dataType))
                    {
                        // Add the structure to the structure list
                        allStructs.add(dataType);
                    }
                }
            }

            // Check if any child structures are referenced
            if (!allStructs.isEmpty())
            {
                // Add the last structure as the first one in the ordered list
                orderedNames.add(allStructs.get(allStructs.size() - 1));

                // Step backwards through the list of all structures beginning with the next to the
                // last structure in the list
                for (int index = allStructs.size() - 2; index >= 0; index--)
                {
                    // Add the structure name to the list
                    orderedNames.add(allStructs.get(index));
                }
            }

            // Step through the structure names
            for (String structureName : structureNames)
            {
                // Check if the structure isn't already in the list. This is true for the root
                // structure(s) without children
                if (!orderedNames.contains(structureName))
                {
                    // Add the structure to the list
                    orderedNames.add(structureName);
                }
            }
        }

        return orderedNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the full name of the variable in the specified row of the structure data in the
     * application’s native format, which includes the variables in the structure path separated by
     * commas, and with the data type and variable names separated by periods
     *
     * @param row
     *            table data row index
     *
     * @return The variable's full path and name with each variable in the path separated by a
     *         comma, and with each data type and variable name separated by a period; returns a
     *         blank if the row is invalid
     *********************************************************************************************/
    public String getFullVariableNameRaw(int row)
    {
        String fullName = "";

        // Get the name of the variable name column
        String tableType = getStructureTypeNameByRow(row);
        String variableNameColumnName = tableTypeHandler.getColumnNameByInputType(tableType,
                                                                                  DefaultInputType.VARIABLE);
        String dataTypeColumnName = tableTypeHandler.getColumnNameByInputType(tableType,
                                                                              DefaultInputType.PRIM_AND_STRUCT);

        // Check that the variable name and data type columns exist
        if (variableNameColumnName != null && dataTypeColumnName != null)
        {
            // Get the full variable name
            fullName = getPathByRow(TYPE_STRUCTURE, row)
                       + ","
                       + getStructureTableData(dataTypeColumnName, row)
                       + "."
                       + getStructureTableData(variableNameColumnName, row);
        }

        return fullName;
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * underscores, and with the data types removed. In case there are any array member variable
     * names in the full name, replace left square brackets with # underscores and remove right
     * square brackets (example: a[0],b[2] becomes a_0_b_2)
     *
     * @param row
     *            table data row index
     *
     * @return The variable's full path and name with each variable in the path separated by an
     *         underscore, and with the data types removed; returns a blank if the row is invalid
     *********************************************************************************************/
    public String getFullVariableName(int row)
    {
        return getFullVariableName(row, "_");
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s), and with the data types removed. In case there are any
     * array member variable names in the full name, replace left square brackets with #
     * underscores and remove right square brackets (example: a[0],b[2] becomes a_0separatorb_2)
     *
     * @param row
     *            table data row index
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s), and with the data types removed; returns a blank
     *         if the row is invalid
     *********************************************************************************************/
    public String getFullVariableName(int row, String varPathSeparator)
    {
        return getFullVariableName(row, varPathSeparator, true, "");
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s). In case there are any array member variable names in
     * the full name, replace left square brackets with # underscores and remove right square
     * brackets (example: a[0],b[2] becomes a_0separatorb_2). Data types may be excluded or
     * retained, based on the input flag
     *
     * @param row
     *            table data row index
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     *
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s); returns a blank if the row is invalid
     *********************************************************************************************/
    public String getFullVariableName(int row,
                                      String varPathSeparator,
                                      boolean excludeDataTypes,
                                      String typeNameSeparator)
    {
        String fullName = "";

        // Get the name of the variable name column
        String tableType = getStructureTypeNameByRow(row);
        String variableNameColumnName = tableTypeHandler.getColumnNameByInputType(tableType,
                                                                                  DefaultInputType.VARIABLE);
        String dataTypeColumnName = tableTypeHandler.getColumnNameByInputType(tableType,
                                                                              DefaultInputType.PRIM_AND_STRUCT);

        // Check that the variable name and data type columns exist
        if (variableNameColumnName != null && dataTypeColumnName != null)
        {
            // Get the full variable name
            fullName = getFullVariableName(getPathByRow(TYPE_STRUCTURE, row),
                                           getStructureTableData(dataTypeColumnName, row)
                                                                              + "."
                                                                              + getStructureTableData(variableNameColumnName,
                                                                                                      row),
                                           varPathSeparator,
                                           excludeDataTypes,
                                           typeNameSeparator);
        }

        return fullName;
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s) and with the data types removed. In case there are any
     * array member variable names in the full name, replace left square brackets with #
     * underscores and remove right square brackets (example: a[0],b[2] becomes a_0separatorb_2)
     *
     * @param variablePath
     *            variable path in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2[,...]]]
     *
     * @param variableName
     *            variable name in the format primitiveDataType.variable
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s) and with the data types removed; returns a blank if
     *         the variable path + name doesn't exist in the project database
     *********************************************************************************************/
    public String getFullVariableName(String variablePath,
                                      String variableName,
                                      String varPathSeparator)
    {
        return getFullVariableName(variablePath, variableName, varPathSeparator, true, "");
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s), and with the data types removed. In case there are any
     * array member variable names in the full name, replace left square brackets with #
     * underscores and remove right square brackets (example: a[0],b[2] becomes a_0separatorb_2)
     *
     * @param fullName
     *            variable path + name in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2 [,...]]],primitiveDataType.variable
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s), and with the data types removed; returns a blank
     *         if the variable path + name doesn't exist in the project database
     *********************************************************************************************/
    public String getFullVariableName(String fullName, String varPathSeparator)
    {
        return getFullVariableName(fullName, varPathSeparator, true, "");
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s). In case there are any array member variable names in
     * the full name, replace left square brackets with # underscores and remove right square
     * brackets (example: a[0],b[2] becomes a_0separatorb_2). Data types may be excluded or
     * retained, based on the input flag
     *
     * @param variablePath
     *            variable path in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2[,...]]]
     *
     * @param variableName
     *            variableName in the format primitiveDataType.variable
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     *
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s); returns a blank if the variable path + name
     *         doesn't exist in the project database
     *********************************************************************************************/
    public String getFullVariableName(String variablePath,
                                      String variableName,
                                      String varPathSeparator,
                                      boolean excludeDataTypes,
                                      String typeNameSeparator)
    {
        String fullName = "";

        // Check that the path and name are not blank
        if (!variablePath.isEmpty() && variableName != null && !variableName.isEmpty())
        {
            // Get the full variable name
            fullName = getFullVariableName(variablePath + "," + variableName,
                                           varPathSeparator,
                                           excludeDataTypes,
                                           typeNameSeparator);
        }

        return fullName;
    }

    /**********************************************************************************************
     * Get a variable's full name which includes the variables in the structure path separated by
     * the specified separator character(s). In case there are any array member variable names in
     * the full name, replace left square brackets with # underscores and remove right square
     * brackets (example: a[0],b[2] becomes a_0separatorb_2). Data types may be excluded or
     * retained, based on the input flag. Any macro embedded in the variable name is expanded
     *
     * @param fullName
     *            variable path + name in the format rootTable[,structureDataType1.variable1
     *            [,structureDataType2.variable2 [,...]]],primitiveDataType.variable
     *
     * @param varPathSeparator
     *            character(s) to place between variable path members
     *
     * @param excludeDataTypes
     *            true to remove the data types from the variable path + name
     *
     * @param typeNameSeparator
     *            character(s) to place between data types and variable names
     *
     * @return The variable's full path and name with each variable in the path separated by the
     *         specified separator character(s); returns a blank if the variable path + name
     *         doesn't exist in the project database. Any macro embedded in the variable name is
     *         expanded
     *********************************************************************************************/
    public String getFullVariableName(String fullName,
                                      String varPathSeparator,
                                      boolean excludeDataTypes,
                                      String typeNameSeparator)
    {
        return variableHandler.getFullVariableName(macroHandler.getMacroExpansion(fullName),
                                                   varPathSeparator,
                                                   excludeDataTypes,
                                                   typeNameSeparator);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs with any embedded macro
     * replaced by its corresponding value. Convenience method that assumes the table type is
     * "structure"
     *
     * @param row
     *            table data row index
     *
     * @return The structure path to the current row's parameter with any embedded macro replaced
     *         by its corresponding value; returns a blank if an instance of the table type doesn't
     *         exist or the row number is invalid. The path starts with the root table name and is
     *         followed by a comma and then the parent structure and variable name(s) that
     *         define(s) the table's path. Each parent and its associated variable name are
     *         separated by a period. Each parent/variable pair in the path is separated by a
     *         comma. The format is:
     *
     *         rootTable[,structureDataType1.variable1[,structureDataType2.variable2[...]]]
     *********************************************************************************************/
    public String getStructurePathByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.PARENT_AND_VARIABLE, true);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs with any embedded macro(s)
     * left in place. Convenience method that assumes the table type is "structure"
     *
     * @param row
     *            table data row index
     *
     * @return The structure path to the current row's parameter with any embedded macro(s) left in
     *         place; returns a blank if an instance of the table type doesn't exist or the row
     *         number is invalid. The path starts with the root table name and is followed by a
     *         comma and then the parent structure and variable name(s) that define(s) the table's
     *         path. Each parent and its associated variable name are separated by a period. Each
     *         parent/variable pair in the path is separated by a comma. The format is:
     *
     *         rootTable[,structureDataType1.variable1[,structureDataType2.variable2[...]]]
     *********************************************************************************************/
    public String getStructurePathByRowWithMacros(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.PARENT_AND_VARIABLE, false);
    }

    /**********************************************************************************************
     * Get the path to which the specified row's data belongs with any embedded macro replaced by
     * its corresponding value
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter with any embedded macro replaced by its
     *         corresponding value; returns a blank if an instance of the table type doesn't exist
     *         or the row number is invalid. The path starts with the root table name. For
     *         structure tables the root name is followed by a comma and then the parent structure
     *         and variable name(s) that define(s) the table's path. Each parent and its associated
     *         variable name are separated by a period. Each parent/variable pair in the path is
     *         separated by a comma. The format is:
     *
     *         rootTable[,structureDataType1.variable1[,structureDataType2.variable2[...]]]
     *********************************************************************************************/
    public String getPathByRow(String tableType, int row)
    {
        return getTablePathByRow(tableType, row, TablePathType.PARENT_AND_VARIABLE, true);
    }

    /**********************************************************************************************
     * Get the path to which the specified row's data belongs with any embedded macro(s) left in
     * place
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter with any embedded macro(s) left in place;
     *         returns a blank if an instance of the table type doesn't exist or the row number is
     *         invalid. The path starts with the root table name. For structure tables the root
     *         name is followed by a comma and then the parent structure and variable name(s) that
     *         define(s) the table's path. Each parent and its associated variable name are
     *         separated by a period. Each parent/variable pair in the path is separated by a
     *         comma. The format is:
     *
     *         rootTable[,structureDataType1.variable1[,structureDataType2.variable2[...]]]
     *********************************************************************************************/
    public String getPathByRowWithMacros(String tableType, int row)
    {
        return getTablePathByRow(tableType, row, TablePathType.PARENT_AND_VARIABLE, false);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs, showing only the root
     * structure and variable names and with any embedded macro replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter with any embedded macro replaced by its
     *         corresponding value; returns a blank if an instance of the table type doesn't exist
     *         or the row number is invalid. The path starts with the root table name. The root
     *         name is followed by a comma and then the variable name(s) that define(s) the table's
     *         path. Each variable in the path is separated by a comma. The format is:
     *
     *         rootTable[,variable1[,variable2[...]]]
     *********************************************************************************************/
    public String getStructureTableVariablePathByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.VARIABLE_ONLY, true);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs, showing only the root
     * structure and variable names and with any embedded macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter with any embedded macro(s) left in place;
     *         returns a blank if an instance of the table type doesn't exist or the row number is
     *         invalid. The path starts with the root table name. The root name is followed by a
     *         comma and then the variable name(s) that define(s) the table's path. Each variable
     *         in the path is separated by a comma. The format is:
     *
     *         rootTable[,variable1[,variable2[...]]]
     *********************************************************************************************/
    public String getStructureTableVariablePathByRowWithMacros(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.VARIABLE_ONLY, false);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs, formatted for use in an
     * ITOS record statement and with any embedded macro replaced by its corresponding value
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter formatted for use in an ITOS record
     *         statement and with any embedded macro replaced by its corresponding value; returns a
     *         blank if an instance of the table type doesn't exist or the row number is invalid.
     *         The path starts with the root table name. The root name is followed by a period and
     *         then the variable name(s) that define(s) the table's path. Each variable in the path
     *         is separated by a period. The format is:
     *
     *         rootTable[.variable1[.variable2[...]]]
     *********************************************************************************************/
    public String getStructureTableITOSPathByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.ITOS_RECORD, true);
    }

    /**********************************************************************************************
     * Get the structure path to which the specified row's data belongs, formatted for use in an
     * ITOS record statement, and with any embedded macro(s) left in place
     *
     * @param row
     *            table data row index
     *
     * @return The path to the current row's parameter formatted for use in an ITOS record
     *         statement and with any embedded macro(s) left in place; returns a blank if an
     *         instance of the table type doesn't exist or the row number is invalid. The path
     *         starts with the root table name. The root name is followed by a period and then the
     *         variable name(s) that define(s) the table's path. Each variable in the path is
     *         separated by an period. The format is:
     *
     *         rootTable[.variable1[.variable2[...]]]
     *********************************************************************************************/
    public String getStructureTableITOSPathByRowWithMacros(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.ITOS_RECORD, false);
    }

    /**********************************************************************************************
     * Get the path or prototype table name for the table on the specified row in the specified
     * format
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @param pathType
     *            the format of the path to return: TablePathType.VARIABLE_AND_PARENT to return the
     *            path with the variable names and associated parent structure names,
     *            TablePathType.PROTOTYPE to return the name of the prototype table,
     *            TablePathType.VARIABLE_ONLY to return the path with only the variable names
     *            (parent names removed), or TablePathType.ITOS_RECORD to return the path formatted
     *            for use in an ITOS record file
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return The table path (or prototype name), for the structure table, to the current row's
     *         parameter; returns a blank if an instance of the structure table type doesn't exist
     *         or the row number is invalid. Depending on the input flag, any macro is replaced by
     *         its corresponding value or left in place
     *********************************************************************************************/
    private String getTablePathByRow(String tableType,
                                     int row,
                                     TablePathType pathType,
                                     boolean expandMacros)
    {
        String structurePath = "";

        // Get the reference to the table information class for the specified table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check if table information exists for the specified type and if the row is within the
        // table data array size
        if (tableInfo != null && row >= 0 && row < tableInfo.getData().length)
        {
            // Calculate the column index for the structure path
            int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

            // Check that the row index is valid
            if (tableInfo.getData().length != 0 && pathColumn > 0)
            {
                // Get the structure path for this row
                structurePath = tableInfo.getData()[row][pathColumn].toString();

                switch (pathType)
                {
                    case PARENT_AND_VARIABLE:
                        // Get the path as stored, with the parent structures in place
                        break;

                    case PROTOTYPE:
                        // Get the table's prototype table name
                        structurePath = TableInformation.getPrototypeName(structurePath);
                        break;

                    case VARIABLE_ONLY:
                        // Remove the data types (parent structure names) from the path
                        structurePath = structurePath.replaceAll(",[^\\.]*\\.", ",");
                        break;

                    case ITOS_RECORD:
                        // Remove the data types (parent structure names) from the path and replace
                        // the commas with periods
                        structurePath = structurePath.replaceAll(",[^\\.]*\\.", ".");
                        break;
                }
            }
        }

        // Check if any macros should be expanded
        if (expandMacros)
        {
            // Expand any macros in the path
            structurePath = macroHandler.getMacroExpansion(structurePath);
        }

        return structurePath;
    }

    /**********************************************************************************************
     * Get the row index in the structure data for the first entry associated with the parent
     * structure of the entry on the specified row of the structure data. The subsequent rows of
     * the parent structure are not necessarily contiguous in the structure data. If a variable in
     * the structure has a structure data type then the child structure's rows are inserted within
     * the rows of the parent. Use getStructurePathByRow() (or variant) to determine the structure
     * to which a specific row belongs
     *
     * @param row
     *            table data row index
     *
     * @return The row index in the structure data for the first entry associated with the parent
     *         structure of the entry on the specified row of the structure data; -1 if there is no
     *         parent associated with the specified row or no structure data exists
     *********************************************************************************************/
    public int getStructureParentRowByChildRow(int row)
    {
        int parentRow = -1;

        // Get the path for the specified row
        String path = getPathByRow(TYPE_STRUCTURE, row);

        // Get the index of the last child in the path
        int index = path.lastIndexOf(",");

        // Check if the path has a child table
        if (index != -1)
        {
            // Remove the last child from the path, leaving the parent
            path = path.substring(0, index);
        }

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(TYPE_STRUCTURE);

        // Check that the table type exists and that there is data for the specified table type
        if (tableInfo != null && tableInfo.getData().length != 0)
        {
            // Step through each row in the table data
            for (int tableRow = 0; tableRow < tableInfo.getData().length; tableRow++)
            {
                // Calculate the column index for the structure path
                int pathColumn = tableInfo.getData()[tableRow].length - PATH_COLUMN_DELTA;

                // Check if the path of the child structure table at the supplied row matches the
                // path in the structure data
                if (path.equals(tableInfo.getData()[tableRow][pathColumn]))
                {
                    // Store the row index for the parent structure and stop searching
                    parentRow = tableRow;
                    break;
                }
            }
        }

        return parentRow;
    }

    /**********************************************************************************************
     * Determine if the specified structure is referenced by more than one root structure
     *
     * @param structureName
     *            prototype name of the structure to check
     *
     * @return true if the specified structure is referenced by more than one root structure; false
     *         otherwise
     *********************************************************************************************/
    public boolean isStructureShared(String structureName)
    {
        return isStructureShared(structureName, false);
    }

    /**********************************************************************************************
     * Determine if the specified structure is referenced by more than one root structure, and that
     * at least one of the structures is not associated with the script
     *
     * @param structureName
     *            prototype name of the structure to check
     *
     * @return true if the specified structure is referenced by more than one root structure and at
     *         least one of these structures is not associated with the script; false otherwise
     *********************************************************************************************/
    public boolean isStructureSharedExternally(String structureName)
    {
        return isStructureShared(structureName, true);
    }

    /**********************************************************************************************
     * Determine if the specified structure is referenced by more than one root structure. A flag
     * is used to confine the check to structures not associated with the script
     *
     * @param structureName
     *            prototype name of the structure to check
     *
     * @param isExternal
     *            true to only check if the structure is shared by at least one structure not
     *            associated with the script
     *
     * @return true if the specified structure is referenced by more than one root structure and,
     *         if the check is for external shared references, that the structure is shared with a
     *         root structure not associated with the script ; false otherwise
     *********************************************************************************************/
    private boolean isStructureShared(String structureName, boolean isExternal)
    {
        boolean isShared = false;

        // Check if a structure name is provided
        if (structureName != null && !structureName.isEmpty())
        {
            // Get the list root table paths for which the target structure is a member
            List<Object[]> memberPaths = tableTree.getTableTreePathArray(structureName,
                                                                         tableTree.getNodeByNodeName(DEFAULT_INSTANCE_NODE_NAME),
                                                                         -1);

            // Check that the target structure appears in at least two paths
            if (memberPaths.size() > 1)
            {
                List<String> structureTableNames = null;
                boolean isTargetExternal = false;

                // Get the root table for the first path
                String target = tableTree.getVariableRootFromNodePath(memberPaths.get(0));

                // Check if looking for externally shared references only
                if (isExternal)
                {
                    // Get the list of all structure table names associated with the script
                    structureTableNames = Arrays.asList(getStructureTableNames());

                    // Set the flag to true if the target is external
                    isTargetExternal = !structureTableNames.contains(target);
                }

                // Step through the remaining paths
                for (int index = 1; index < memberPaths.size(); index++)
                {
                    // Get the root table for the comparison path
                    String compare = tableTree.getVariableRootFromNodePath(memberPaths.get(index));

                    // Check if the comparison root table differs from the first path's root table
                    // and, if checking for externally shared structures, that the shared structure
                    // isn't referenced by a table associated with the script
                    if (!target.equals(compare)
                        && (!isExternal
                            || isTargetExternal
                            || !structureTableNames.contains(compare)))
                    {
                        // Set the flag indicating that the target structure is referenced by more
                        // than one root table and at least one is not among the associated
                        // structure tables, and stop searching
                        isShared = true;
                        break;
                    }
                }
            }
        }

        return isShared;
    }

    /**********************************************************************************************
     * Get an array containing the path to every root structure in the project database and its
     * variables
     *
     * @return Array containing the path for each structure variable. The root structures are
     *         sorted alphabetically. The variables are displayed in the order of appearance within
     *         the structure (parent or child). Any macro is replaced by its corresponding value
     *********************************************************************************************/
    public String[] getVariablePaths()
    {
        return variableHandler.getAllVariableNames().toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get an array containing the name, code, table, and argument name(s) for every command in the
     * project database
     *
     * @return Array containing the name, code, table, and argument name(s) for every command. The
     *         array is sorted by command name; if the same then by command code; if the same then
     *         by table name
     *********************************************************************************************/
    public String[] getCommandInformation()
    {
        List<String[]> commandList = new ArrayList<String[]>();

        // Step through each command in the project
        for (CommandInformation commandInfo : commandHandler.getCommandInformation())
        {
            // Add the command information to the list
            commandList.add(new String[] {commandInfo.getCommandName(),
                                          commandInfo.getCommandCode(),
                                          commandInfo.getTable(),
                                          commandInfo.getArguments()});
        }

        return commandList.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the name(s) of the data field(s) associated with the specified table
     *
     * @param tableName
     *            name of the table, including the path if this table references a structure, to
     *            which the field is a member
     *
     * @return Array of the data field names associated with the specified table; returns an empty
     *         array if the table name is invalid or the table has no data fields
     *********************************************************************************************/
    public String[] getTableDataFieldNames(String tableName)
    {
        return getDataFieldNames(tableName);
    }

    /**********************************************************************************************
     * Get the name(s) of the data field(s) associated with the specified group
     *
     * @param groupName
     *            name of the group to which the field is a member
     *
     * @return Array of the data field names associated with the specified group; returns an empty
     *         array if the group name is invalid or the group has no data fields
     *********************************************************************************************/
    public String[] getGroupDataFieldNames(String groupName)
    {
        return getDataFieldNames(CcddFieldHandler.getFieldGroupName(groupName));
    }

    /**********************************************************************************************
     * Get the name(s) of the data field(s) associated with the specified table type
     *
     * @param typeName
     *            name of the table type to which the field is a member
     *
     * @return Array of the data field names associated with the specified table type; returns an
     *         empty array if the table type name is invalid or the table type has no data fields
     *********************************************************************************************/
    public String[] getTypeDataFieldNames(String typeName)
    {
        return getDataFieldNames(CcddFieldHandler.getFieldTypeName(typeName));
    }

    /**********************************************************************************************
     * Get the name(s) of the data field(s) associated with the project
     *
     * @return Array of the data field names associated with the project; returns an empty array if
     *         the project has no data fields
     *********************************************************************************************/
    public String[] getProjectFieldNames()
    {
        return getDataFieldNames(CcddFieldHandler.getFieldProjectName());
    }

    /**********************************************************************************************
     * Get the name(s) of the data field(s) associated with the specified owner
     *
     * @param ownerName
     *            name of the table type to which the field is a member
     *
     * @return Array of the data field names associated with the specified owner; returns an empty
     *         array if the owner name is invalid or the owner has no data fields
     *********************************************************************************************/
    private String[] getDataFieldNames(String ownerName)
    {
        List<String> fieldNames = new ArrayList<String>();

        // Step through each data field associated with the owner
        for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(ownerName))
        {
            // Add the field name to the list
            fieldNames.add(fieldInfo.getFieldName());
        }

        return fieldNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the data field value for all tables that have the specified data field
     *
     * @param fieldName
     *            data field name
     *
     * @return Array of table names and the data field value; returns an empty array if the field
     *         name is invalid (i.e., no table has the data field)
     *********************************************************************************************/
    public String[][] getTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(null, fieldName);
    }

    /**********************************************************************************************
     * Get the data field value for all structure tables that have the specified data field
     *
     * @param fieldName
     *            data field name
     *
     * @return Array of structure table names and the data field value; returns an empty array if
     *         the field name is invalid (i.e., no structure table has the data field)
     *********************************************************************************************/
    public String[][] getStructureTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(TYPE_STRUCTURE, fieldName);
    }

    /**********************************************************************************************
     * Get the data field value for all command tables that have the specified data field
     *
     * @param fieldName
     *            data field name
     *
     * @return Array of command table names and the data field value; returns an empty array if the
     *         field name is invalid (i.e., no command table has the data field)
     *********************************************************************************************/
    public String[][] getCommandTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(TYPE_COMMAND, fieldName);
    }

    /**********************************************************************************************
     * Get the data field value for all tables of the specified type that have the specified data
     * field
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command". null to include tables of any type
     *
     * @param fieldName
     *            data field name
     *
     * @return Array of table names of the specified type and the data field value; returns an
     *         empty array if the field name is invalid (i.e., no table has the data field)
     *********************************************************************************************/
    public String[][] getTableDataFieldValues(String tableType, String fieldName)
    {
        List<String[]> fieldValues = new ArrayList<String[]>();

        // Step through every table of every type referenced in the table data
        for (String tableName : (tableType == null
                                                   ? getTableNames()
                                                   : getTableNames(tableType)))
        {
            // Get the data field value for the table
            String fieldValue = getDataFieldValue(tableName, fieldName);

            // Check if the data field exists for this table
            if (fieldValue != null)
            {
                // Add the table name and field value to the list
                fieldValues.add(new String[] {tableName, fieldValue});
            }
        }

        return fieldValues.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the value for the specified table's specified data field
     *
     * @param tableName
     *            name of the table, including the path if this table references a structure, for
     *            which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field value; returns a null if the table name or field name is invalid
     *********************************************************************************************/
    public String getTableDataFieldValue(String tableName, String fieldName)
    {
        return getDataFieldValue(tableName, fieldName);
    }

    /**********************************************************************************************
     * Get the value for the specified group's specified data field
     *
     * @param groupName
     *            name of the group for which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field value; returns a null if the group name or field name is invalid
     *********************************************************************************************/
    public String getGroupDataFieldValue(String groupName, String fieldName)
    {
        return getDataFieldValue(CcddFieldHandler.getFieldGroupName(groupName), fieldName);
    }

    /**********************************************************************************************
     * Get the value for the specified table type's specified data field
     *
     * @param typeName
     *            name of the table type for which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field value; returns a null if the table type name or field name is invalid
     *********************************************************************************************/
    public String getTypeDataFieldValue(String typeName, String fieldName)
    {
        return getDataFieldValue(CcddFieldHandler.getFieldTypeName(typeName), fieldName);
    }

    /**********************************************************************************************
     * Get the value for the specified project data field
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field value; returns a null if the project field name is invalid
     *********************************************************************************************/
    public String getProjectDataFieldValue(String fieldName)
    {
        return getDataFieldValue(CcddFieldHandler.getFieldProjectName(), fieldName);
    }

    /**********************************************************************************************
     * Get the contents of the data field for the specified table's specified data field
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field value; returns a null if the owner name or field name is invalid
     *********************************************************************************************/
    private String getDataFieldValue(String ownerName, String fieldName)
    {
        String fieldValue = null;

        // Get the reference to the data field information for the requested owner and field names
        FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerName, fieldName);

        // Check if a field for this owner exists
        if (fieldInfo != null)
        {
            // Get the field value
            fieldValue = fieldInfo.getValue();

            // Check if the data field contains a message name and ID
            if (fieldInfo.getInputType().equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID)))
            {
                // Remove the auto-assignment protection flag, if present
                fieldValue = CcddMessageIDHandler.removeProtectionFlag(fieldValue);
            }
        }

        return fieldValue;
    }

    /**********************************************************************************************
     * Get the description for the specified table's specified data field
     *
     * @param tableName
     *            name of the table, including the path if this table references a structure, to
     *            which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field description; returns a blank if the table type, table name, or field name
     *         is invalid
     *********************************************************************************************/
    public String getTableDataFieldDescription(String tableName, String fieldName)
    {
        return getDataFieldDescription(tableName, fieldName);
    }

    /**********************************************************************************************
     * Get the description for the specified group's specified data field
     *
     * @param groupName
     *            name of the group to which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field description; returns a blank if the group name or field name is invalid
     *********************************************************************************************/
    public String getGroupDataFieldDescription(String groupName, String fieldName)
    {
        return getDataFieldDescription(CcddFieldHandler.getFieldGroupName(groupName), fieldName);
    }

    /**********************************************************************************************
     * Get the description for the specified table type's specified data field
     *
     * @param typeName
     *            name of the table type to which the field is a member
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field description; returns a blank if the table type name or field name is
     *         invalid
     *********************************************************************************************/
    public String getTypeDataFieldDescription(String typeName, String fieldName)
    {
        return getDataFieldDescription(CcddFieldHandler.getFieldTypeName(typeName), fieldName);
    }

    /**********************************************************************************************
     * Get the description for the specified project data field
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field description; returns a blank if the project field name is invalid
     *********************************************************************************************/
    public String getProjectDataFieldDescription(String fieldName)
    {
        return getDataFieldDescription(CcddFieldHandler.getFieldProjectName(), fieldName);
    }

    /**********************************************************************************************
     * Get the description of the data field for the specified owner's specified data field
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param fieldName
     *            data field name
     *
     * @return Data field description; returns a blank if the owner name or field name is invalid
     *********************************************************************************************/
    private String getDataFieldDescription(String ownerName, String fieldName)
    {
        String fieldDescription = "";

        // Get the reference to the data field information for the requested owner and field names
        FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerName, fieldName);

        // Check if a field for this owner exists
        if (fieldInfo != null)
        {
            // Get the field description
            fieldDescription = fieldInfo.getDescription();
        }

        return fieldDescription;
    }

    /**********************************************************************************************
     * Get the structure table data at the row and column indicated, with any macro replaced by its
     * corresponding value. The column is specified by name. Convenience method that assumes the
     * table type is "structure"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified structure table's array at the row and column name
     *         provided, with any macro replaced by its corresponding value; returns null if an
     *         instance of the structure table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    public String getStructureTableData(String columnName, int row)
    {
        return getTableData(TYPE_STRUCTURE, columnName, row);
    }

    /**********************************************************************************************
     * Get the command table data at the row and column indicated, with any macro replaced by its
     * corresponding value. The column is specified by name. Convenience method that assumes the
     * table type is "command"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified command table's array at the row and column name provided,
     *         with any macro replaced by its corresponding value; returns null if an instance of
     *         the command table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    public String getCommandTableData(String columnName, int row)
    {
        return getTableData(TYPE_COMMAND, columnName, row);
    }

    /**********************************************************************************************
     * Get the data at the row and column indicated, with any macro replaced by its corresponding
     * value, for the table type specified. The column is specified by name
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified table's array at the row and column name provided, with
     *         any macro replaced by its corresponding value; returns null if an instance of the
     *         table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    public String getTableData(String tableType, String columnName, int row)
    {
        return getTableData(tableType, columnName, row, true);
    }

    /**********************************************************************************************
     * Get the structure table data at the row and column indicated, with any macro name(s) left in
     * place. The column is specified by name. Convenience method that assumes the table type is
     * "structure"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified structure table's array at the row and column name
     *         provided, with any macro name(s) left in place; returns null if an instance of the
     *         structure table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    public String getStructureTableDataWithMacros(String columnName, int row)
    {
        return getTableDataWithMacros(TYPE_STRUCTURE, columnName, row);
    }

    /**********************************************************************************************
     * Get the command table data at the row and column indicated, with any macro name(s) left in
     * place. The column is specified by name. Convenience method that assumes the table type is
     * "command"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified command table's array at the row and column name provided,
     *         with any macro name(s) left in place; returns null if an instance of the command
     *         table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    public String getCommandTableDataWithMacros(String columnName, int row)
    {
        return getTableDataWithMacros(TYPE_COMMAND, columnName, row);
    }

    /**********************************************************************************************
     * Get the data at the row and column indicated, with any macro name(s) left in place, for the
     * table type specified. The column is specified by name
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @return Contents of the specified table's array at the row and column name provided, with
     *         any macro name(s) left in place; returns null if an instance of the table type, the
     *         column name, or the row doesn't exist
     *********************************************************************************************/
    public String getTableDataWithMacros(String tableType, String columnName, int row)
    {
        return getTableData(tableType, columnName, row, false);
    }

    /**********************************************************************************************
     * Get the data at the row and column indicated for the table type specified. The column is
     * specified by name. Macro expansion is controlled by the input flag
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @param row
     *            table data row index
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Contents of the specified table's array at the row and column name provided; returns
     *         null if an instance of the table type, the column name, or the row doesn't exist
     *********************************************************************************************/
    private String getTableData(String tableType, String columnName, int row, boolean expandMacros)
    {
        String tableData = null;

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists and the row index is valid
        if (tableInfo != null && row < tableInfo.getData().length)
        {
            // Get the type definition based on the table's specific type name
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getTypeNameByRow(tableType,
                                                                                          row));

            // Get the column index matching the requested column name
            int column = typeDefn.getColumnIndexByUserName(columnName);

            // Check that the column name exists in the table
            if (column != -1)
            {
                // Store the contents of the table at the specified row and column
                tableData = tableInfo.getData()[row][column].toString();

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros in the data
                    tableData = macroHandler.getMacroExpansion(tableData);
                }

                // Check if the data field contains a message name and ID
                if (typeDefn.getInputTypes()[column].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID)))
                {
                    // Remove the auto-assignment protection flag, if present
                    tableData = CcddMessageIDHandler.removeProtectionFlag(tableData);
                }
            }
        }

        return tableData;
    }

    /**********************************************************************************************
     * Get an array of row numbers in the structure table data that belong to the specified
     * structure table. Convenience method that assumes the table type is "Structure"
     *
     * @param tablePath
     *            full table path
     *
     * @return Array of the structure table data row numbers that belong to the specified structure
     *         table; returns an empty array if the structure table path doesn't exist
     *********************************************************************************************/
    public Integer[] getStructureTableRowIndices(String tablePath)
    {
        return getTableRowIndices(TYPE_STRUCTURE, tablePath);
    }

    /**********************************************************************************************
     * Get an array of row numbers in the command table data that belong to the specified command
     * table. Convenience method that assumes the table type is "Command"
     *
     * @param tableName
     *            table name
     *
     * @return Array of the command table data row numbers that belong to the specified command
     *         table; returns an empty array if the command table name doesn't exist
     *********************************************************************************************/
    public Integer[] getCommandTableRowIndices(String tableName)
    {
        return getTableRowIndices(TYPE_COMMAND, tableName);
    }

    /**********************************************************************************************
     * Get an array of row numbers in the table data for the specified table type that belong to
     * the specified table
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param tablePath
     *            full table path
     *
     * @return Array of the specified table type's table data row numbers that belong to the
     *         specified table; returns an empty array if an instance of the table type or the
     *         table path doesn't exist
     *********************************************************************************************/
    public Integer[] getTableRowIndices(String tableType, String tablePath)
    {
        List<Integer> tableRows = new ArrayList<Integer>();

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Step through the table data
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Check if the table name matches the target table
                if (tableInfo.getData()[row][tableInfo.getData()[row].length
                                             - PATH_COLUMN_DELTA].equals(tablePath))
                {
                    // Add the row number to the list
                    tableRows.add(row);
                }
            }
        }

        return tableRows.toArray(new Integer[0]);
    }

    /**********************************************************************************************
     * Get the data from the specified "Structure" table in the specified column for the row with
     * the specified variable name, with any macro name replaced by its corresponding value.
     * Convenience method that assumes the table type is "Structure" and the variable name column
     * is "Variable Name"
     *
     * @param tablePath
     *            full table path, which includes the parent table name and the data type +
     *            variable name pairs
     *
     * @param variableName
     *            variable name
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @return Contents of the table defined by the table path, variable name, and column name
     *         specified, with any macro replaced by its corresponding value; returns null if an
     *         instance of the table type, the column name, or the variable name doesn't exist
     *********************************************************************************************/
    public String getStructureDataByVariableName(String tablePath,
                                                 String variableName,
                                                 String columnName)
    {
        return getTableDataByColumnName(TYPE_STRUCTURE,
                                        tablePath,
                                        "Variable Name",
                                        variableName,
                                        columnName);
    }

    /**********************************************************************************************
     * Get the data from the table in the specified column for the row in the matching column name
     * that contains the matching name, with any macro name replaced by its corresponding value
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param tablePath
     *            full table path
     *
     * @param matchColumnName
     *            name of the column containing that matching name (case insensitive)
     *
     * @param matchName
     *            text to match in the matching column - this determines the row. The first row in
     *            the matching column that matches the matching name determines the row used to
     *            retrieve the data value
     *
     * @param dataColumnName
     *            name of the column from which to retrieve the data value (case insensitive)
     *
     * @return Contents of the table defined by the table type, table path, matching column name,
     *         matching name, and data column name specified, with any macro replaced by its
     *         corresponding value; returns null if an instance of the table type, the matching
     *         column, the data column, or the matching name doesn't exist
     *********************************************************************************************/
    public String getTableDataByColumnName(String tableType,
                                           String tablePath,
                                           String matchColumnName,
                                           String matchName,
                                           String dataColumnName)
    {
        return getTableDataByColumnName(tableType,
                                        tablePath,
                                        matchColumnName,
                                        matchName,
                                        dataColumnName,
                                        true);
    }

    /**********************************************************************************************
     * Get the data from the specified "Structure" table in the specified column for the row with
     * the specified variable name, with any macro name(s) left in place. Convenience method that
     * assumes the table type is "Structure" and the variable name column is "Variable Name"
     *
     * @param tablePath
     *            full table path, which includes the parent table name and the data type +
     *            variable name pairs
     *
     * @param variableName
     *            variable name
     *
     * @param columnName
     *            column name (case insensitive)
     *
     * @return Contents of the table defined by the table path, variable name, and column name
     *         specified, with any macro name(s) left in place; returns null if an instance of the
     *         table type, the column name, or the variable name doesn't exist
     *********************************************************************************************/
    public String getStructureDataByVariableNameWithMacros(String tablePath,
                                                           String variableName,
                                                           String columnName)
    {
        return getTableDataByColumnNameWithMacros(TYPE_STRUCTURE,
                                                  tablePath,
                                                  "Variable Name",
                                                  variableName,
                                                  columnName);
    }

    /**********************************************************************************************
     * Get the data from the table in the specified column for the row in the matching column name
     * that contains the matching name, with any macro name(s) left in place
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param tablePath
     *            full table path
     *
     * @param matchColumnName
     *            name of the column containing that matching name (case insensitive)
     *
     * @param matchName
     *            text to match in the matching column - this determines the row. The first row in
     *            the matching column that matches the matching name determines the row used to
     *            retrieve the data value
     *
     * @param dataColumnName
     *            name of the column from which to retrieve the data value (case insensitive)
     *
     * @return Contents of the table defined by the table type, table path, matching column name,
     *         matching name, and data column name specified, with any macro name(s) left in place;
     *         returns null if an instance of the table type, the matching column, the data column,
     *         or the matching name doesn't exist
     *********************************************************************************************/
    public String getTableDataByColumnNameWithMacros(String tableType,
                                                     String tablePath,
                                                     String matchColumnName,
                                                     String matchName,
                                                     String dataColumnName)
    {
        return getTableDataByColumnName(tableType,
                                        tablePath,
                                        matchColumnName,
                                        matchName,
                                        dataColumnName,
                                        false);
    }

    /**********************************************************************************************
     * Get the data from the table in the specified column for the row in the matching column name
     * that contains the matching name. Macro expansion is controlled by the input flag
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param tablePath
     *            full table path
     *
     * @param matchColumnName
     *            name of the column containing that matching name (case insensitive)
     *
     * @param matchName
     *            text to match in the matching column - this determines the row. The first row in
     *            the matching column that matches the matching name determines the row used to
     *            retrieve the data value
     *
     * @param dataColumnName
     *            name of the column from which to retrieve the data value (case insensitive)
     *
     * @param expandMacros
     *            true to replace any macros with their corresponding value; false to return the
     *            data with any macro names in place
     *
     * @return Contents of the table defined by the table type, table path, matching column name,
     *         matching name, and data column name specified; returns null if an instance of the
     *         table type, the matching column, the data column, or the matching name doesn't exist
     *********************************************************************************************/
    private String getTableDataByColumnName(String tableType,
                                            String tablePath,
                                            String matchColumnName,
                                            String matchName,
                                            String dataColumnName,
                                            boolean expandMacros)
    {
        String tableData = null;

        // Get the reference to the table information class for the requested table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Step through the table data
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Get the type definition based on the table's specific type name
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getTypeNameByRow(tableType,
                                                                                              row));

                // Get the index for the matching and data columns
                int matchColumnIndex = typeDefn.getColumnIndexByUserName(matchColumnName);
                int dataColumnIndex = typeDefn.getColumnIndexByUserName(dataColumnName);

                // Check that the column names exist in the table
                if (matchColumnIndex != -1 && dataColumnIndex != -1)
                {
                    // Check if the table name matches the target table and the matching name
                    // matches that in the matching name column
                    if (tableInfo.getData()[row][tableInfo.getData()[row].length
                                                 - PATH_COLUMN_DELTA].equals(tablePath)
                        && tableInfo.getData()[row][matchColumnIndex].equals(matchName))
                    {
                        // Store the contents of the table at the specified row and column and stop
                        // searching
                        tableData = tableInfo.getData()[row][dataColumnIndex].toString();

                        // Check if any macros should be expanded
                        if (expandMacros)
                        {
                            // Expand any macros in the data
                            tableData = macroHandler.getMacroExpansion(tableData);
                        }

                        // Check if the data field contains a message name and ID
                        if (typeDefn.getInputTypes()[dataColumnIndex].equals(inputTypeHandler.getInputTypeByDefaultType(DefaultInputType.MESSAGE_NAME_AND_ID)))
                        {
                            // Remove the auto-assignment protection flag, if present
                            tableData = CcddMessageIDHandler.removeProtectionFlag(tableData);
                        }

                        break;
                    }
                }
            }
        }

        return tableData;
    }

    /**********************************************************************************************
     * Get the description of the specified table
     *
     * @param tableName
     *            table name, including the full path for child structure tables
     *
     * @return Description of the specified table; returns a blank the table doesn't exist
     *********************************************************************************************/
    public String getTableDescription(String tableName)
    {
        // Get the description for the table
        return dbTable.queryTableDescription(tableName, ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Get the description of the table at the row indicated for the table type specified
     *
     * @param tableType
     *            table type (case insensitive). All structure table types are combined and are
     *            referenced by the type name "Structure", and all command table types are combined
     *            and are referenced by the type name "Command"
     *
     * @param row
     *            table data row index
     *
     * @return Description of the specified table at the row provided; returns a blank if an
     *         instance of the table type or the row doesn't exist
     *********************************************************************************************/
    public String getTableDescriptionByRow(String tableType, int row)
    {
        // Get the description for the table
        return dbTable.queryTableDescription(getPathByRow(tableType, row),
                                             ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Get the array containing the macro names and their corresponding values
     *
     * @return Array where each row contains a macro name and its corresponding value
     *********************************************************************************************/
    public String[][] getMacroDefinitions()
    {
        return macroHandler.getMacroData().toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Check if the graphical user interface is not displayed
     *
     * @return true if the GUI is hidden; false if the GUI is visible
     *********************************************************************************************/
    public boolean isGUIHidden()
    {
        return ccddMain.isGUIHidden();
    }

    /**********************************************************************************************
     * Display an informational dialog showing the supplied text. The dialog’s header and icon
     * indicate that the text describes information useful to the user; e.g., script status. The
     * Okay button must be pressed before the script can continue
     *
     * @param text
     *            text to display in the dialog
     *********************************************************************************************/
    public void showInformationDialog(String text)
    {
        // Display the supplied text in an information dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>" + text,
                                                  "Script Message",
                                                  JOptionPane.INFORMATION_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
     * Display a warning dialog showing the supplied text. The dialog’s header and icon indicate
     * that the text describes an warning condition. The Okay button must be pressed before the
     * script can continue
     *
     * @param text
     *            text to display in the dialog
     *********************************************************************************************/
    public void showWarningDialog(String text)
    {
        // Display the supplied text in a warning dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>" + text,
                                                  "Script Warning",
                                                  JOptionPane.WARNING_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
     * Display an error dialog showing the supplied text. The dialog’s header and icon indicate
     * that the text describes an error condition. The Okay button must be pressed before the
     * script can continue
     *
     * @param text
     *            text to display in the dialog
     *********************************************************************************************/
    public void showErrorDialog(String text)
    {
        // Display the supplied text in an error dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>" + text,
                                                  "Script Error",
                                                  JOptionPane.ERROR_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**********************************************************************************************
     * Display a dialog for receiving text input. The user must select Okay to accept the input, or
     * Cancel to close the dialog without accepting the input
     *
     * @param labelText
     *            text to display in the dialog
     *
     * @return The text entered in the dialog input field if the Okay button is pressed; returns
     *         null if no text or white space is entered, or if the Cancel button is pressed
     *********************************************************************************************/
    public String getInputDialog(String labelText)
    {
        String input = null;

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        0.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel to hold the components of the dialog
        JPanel panel = new JPanel(new GridBagLayout());

        // Create the input label and field
        JLabel typeLabel = new JLabel(labelText);
        typeLabel.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        panel.add(typeLabel, gbc);

        JTextField typeField = new JTextField("", 15);
        typeField.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        typeField.setEditable(true);
        typeField.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        typeField.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        typeField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                               Color.LIGHT_GRAY,
                                                                                               Color.GRAY),
                                                               BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                               ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                               ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                               ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));
        gbc.gridx++;
        panel.add(typeField, gbc);

        // Display the input dialog
        if (new CcddDialogHandler().showOptionsDialog(parent,
                                                      panel,
                                                      "Input",
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Get the text from the input field and remove the leading and trailing white space
            input = typeField.getText().trim();

            // Check if the text field is empty
            if (input.isEmpty())
            {
                // Set the input to null
                input = null;
            }
        }

        return input;
    }

    /**********************************************************************************************
     * Display a dialog containing radio buttons. The radio buttons are mutually exclusive; only
     * one can be selected at a time. The user must press the Okay button to accept the radio
     * button input, or Cancel to close the dialog without accepting the input
     *
     * @param label
     *            text to display above the radio buttons
     *
     * @param buttonInfo
     *            array containing the text and optional descriptions for the radio buttons to
     *            display in the dialog
     *
     * @return The text for the selected radio button if the Okay button is pressed; returns null
     *         if no radio button is selected or if the Cancel button is pressed
     *********************************************************************************************/
    public String getRadioButtonDialog(String label, String[][] buttonInfo)
    {
        CcddDialogHandler dialog = new CcddDialogHandler();

        String selectedButton = null;

        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create a panel to hold the components of the dialog
        JPanel panel = new JPanel(new GridBagLayout());

        // Create a panel containing a grid of radio buttons representing the table types from
        // which to choose and display the radio button dialog
        if (dialog.addRadioButtons(null, false, buttonInfo, null, label, false, panel, gbc)
            && dialog.showOptionsDialog(parent,
                                        panel,
                                        "Select",
                                        DialogOption.OK_CANCEL_OPTION,
                                        true) == OK_BUTTON)
        {
            // Get the text associated with the selected radio button
            selectedButton = dialog.getRadioButtonSelected();
        }

        return selectedButton;
    }

    /**********************************************************************************************
     * Display a dialog containing one or more check boxes. The user must press the Okay button to
     * accept the check box input(s), or Cancel to close the dialog without accepting the input
     *
     * @param label
     *            text to display above the check boxes
     *
     * @param boxInfo
     *            array containing the text and optional descriptions for the check boxes to
     *            display in the dialog
     *
     * @return An array containing the status for the check box(es) if the Okay button is pressed;
     *         returns null if no check box information is supplied or if the Cancel button is
     *         pressed
     *********************************************************************************************/
    public boolean[] getCheckBoxDialog(String label, String[][] boxInfo)
    {
        boolean[] boxStatus = null;

        // Check if check box information is supplied
        if (boxInfo != null && boxInfo.length != 0)
        {
            // Create a panel to hold the components of the dialog
            JPanel panel = new JPanel(new GridBagLayout());

            // Create the dialog class
            CcddDialogHandler dialog = new CcddDialogHandler();

            // Create a panel containing a grid of check boxes representing the table types from
            // which to choose and display the check box dialog
            if (dialog.addCheckBoxes(null, boxInfo, null, label, false, panel)
                && dialog.showOptionsDialog(parent,
                                            panel,
                                            "Select",
                                            DialogOption.OK_CANCEL_OPTION,
                                            true) == OK_BUTTON)
            {
                // Create storage for the check box status(es)
                boxStatus = new boolean[boxInfo.length];

                // Step through each check box status
                for (int index = 0; index < boxInfo.length; index++)
                {
                    // initialize the status to false (unchecked)
                    boxStatus[index] = false;
                }

                // Get a list of the names of the selected check box(es)
                List<String> selected = Arrays.asList(dialog.getCheckBoxSelected());

                // Step through each check box
                for (int index = 0; index < boxStatus.length; index++)
                {
                    // Check if the check box name is in the list of selected boxes
                    if (selected.contains(boxInfo[index][0]))
                    {
                        // Set the check box status to true (checked)
                        boxStatus[index] = true;
                    }
                }
            }
        }

        return boxStatus;
    }

    /**********************************************************************************************
     * Perform a query on the currently open database
     *
     * @param sqlCommand
     *            PostgreSQL-compatible database query statement
     *
     * @return Two-dimensional array representing the rows and columns of data returned by the
     *         database query; returns null if the query produces an error, or an empty array if
     *         there are no results
     *********************************************************************************************/
    public String[][] getDatabaseQuery(String sqlCommand)
    {
        return dbTable.queryDatabase(sqlCommand,
                                     ccddMain.getMainFrame())
                      .toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Output a 'success' message to the event log
     *
     * @param logMessage
     *            text to output to the event log
     *********************************************************************************************/
    public void writeSuccessLogEntry(String logMessage)
    {
        // Output the message to the event log
        eventLog.logEvent(EventLogMessageType.SUCCESS_MSG,
                          "[script: " + scriptFileName + "] "
                                                           + (logMessage == null
                                                                                 ? ""
                                                                                 : logMessage));
    }

    /**********************************************************************************************
     * Output a 'fail' message to the event log
     *
     * @param logMessage
     *            text to output to the event log
     *********************************************************************************************/
    public void writeFailLogEntry(String logMessage)
    {
        // Output the message to the event log
        eventLog.logEvent(EventLogMessageType.FAIL_MSG,
                          "[script: " + scriptFileName + "] "
                                                        + (logMessage == null
                                                                              ? ""
                                                                              : logMessage));
    }

    /**********************************************************************************************
     * Output a 'status' message to the event log
     *
     * @param logMessage
     *            text to output to the event log
     *********************************************************************************************/
    public void writeStatusLogEntry(String logMessage)
    {
        // Output the message to the event log
        eventLog.logEvent(EventLogMessageType.STATUS_MSG,
                          "[script: " + scriptFileName + "] "
                                                          + (logMessage == null
                                                                                ? ""
                                                                                : logMessage));
    }

    /**********************************************************************************************
     * Divide the supplied enumeration string into the values and labels. The enumeration
     * value/label separator character and the enumerated pair separator character are
     * automatically determined. Any leading or trailing white space characters are removed from
     * each array member
     *
     * @param enumeration
     *            {@literal enumeration in the format <enum value><enum value separator><enum label>[<enum value separator>...][<enum pair separator>...]}
     *
     * @return Two-dimensional array representing the enumeration parameters ; returns null if the
     *         input text is empty or the enumeration separator characters cannot be determined
     *********************************************************************************************/
    public String[][] parseEnumerationParameters(String enumeration)
    {
        String[][] pairs = null;

        // Get the character that separates the enumeration value from the associated label
        String enumSeparator = CcddUtilities.getEnumeratedValueSeparator(enumeration);

        // Check if the value separator exists
        if (enumSeparator != null)
        {
            // Get the character that separates the enumerated pairs
            String pairSeparator = CcddUtilities.getEnumerationPairSeparator(enumeration,
                                                                             enumSeparator);

            // Check if the enumerated pair separator exists
            if (pairSeparator != null)
            {
                // Separate the enumeration parameters into an array
                pairs = getArrayFromString(enumeration, enumSeparator, pairSeparator);
            }
        }

        return pairs;
    }

    /**********************************************************************************************
     * Divide the supplied string into an array using the supplied separator character or string,
     * and trim any leading or trailing white space characters from each array member
     *
     * @param text
     *            string to separate into an array
     *
     * @param columnSeparator
     *            character string to use to delineate the separation point(s) between columns. The
     *            separator is eliminated from the array members
     *
     * @return Array representing the substrings in the supplied text after being parsed using the
     *         separator; returns null if the input text is empty
     *********************************************************************************************/
    public String[] getArrayFromString(String text, String columnSeparator)
    {
        return getArrayFromString(text, columnSeparator, null)[0];
    }

    /**********************************************************************************************
     * Divide the supplied string into a two-dimensional array (columns and rows) using the
     * supplied separator characters or strings, and trim any leading or trailing white space
     * characters from each array member
     *
     * @param text
     *            string to separate into an array
     *
     * @param columnSeparator
     *            character string to use to delineate the separation point(s) between columns. The
     *            separator is eliminated from the array members
     *
     * @param rowSeparator
     *            character string to use to delineate the separation point(s) between rows. The
     *            separator is eliminated from the array members. Use null if only one row is
     *            supplied
     *
     * @return Two-dimensional array representing the substrings in the supplied text after being
     *         parsed using the separator; returns null if the input text is empty
     *********************************************************************************************/
    public String[][] getArrayFromString(String text, String columnSeparator, String rowSeparator)
    {
        String[][] array = null;

        // Check if the supplied text string is not empty
        if (!text.isEmpty())
        {
            String[] rowArray;

            // Check if a row separator was provided
            if (rowSeparator != null)
            {
                // Split the text using the row separator, and remove leading and trailing white
                // space characters if present
                rowArray = text.split("\\s*[" + rowSeparator + "]\\s*");
            }
            // No row separator was provided; assume a single row exists
            else
            {
                rowArray = new String[] {text};
            }

            // Create storage for the columns in each row
            array = new String[rowArray.length][];

            // Step through each row
            for (int row = 0; row < rowArray.length; row++)
            {
                // Split the text using the column separator, and remove leading and trailing white
                // space characters if present
                array[row] = rowArray[row].split("\\s*[" + columnSeparator + "]\\s*");
            }
        }

        return array;
    }

    /**********************************************************************************************
     * Open the specified file for writing. The PrintWriter object that is returned is used by the
     * file writing methods to specify the output file
     *
     * @param outputFileName
     *            output file path + name
     *
     * @return PrintWriter object; returns null if the file could not be opened
     *********************************************************************************************/
    public PrintWriter openOutputFile(String outputFileName)
    {
        return fileIOHandler.openOutputFile(outputFileName);
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     *
     * @param printWriter
     *            output file PrintWriter object obtained from the openOutputFile method
     *
     * @param text
     *            text to write to the output file
     *********************************************************************************************/
    public void writeToFile(PrintWriter printWriter, String text)
    {
        fileIOHandler.writeToFile(printWriter, text);
    }

    /**********************************************************************************************
     * Write the supplied text to the specified output file PrintWriter object and append a line
     * feed character
     *
     * @param printWriter
     *            output file PrintWriter object obtained from the openOutputFile method
     *
     * @param text
     *            text to write to the output file
     *********************************************************************************************/
    public void writeToFileLn(PrintWriter printWriter, String text)
    {
        fileIOHandler.writeToFileLn(printWriter, text);
    }

    /**********************************************************************************************
     * Write the supplied formatted text in the indicated format to the specified output file
     * PrintWriter object
     *
     * @param printWriter
     *            output file PrintWriter object obtained from the openOutputFile method
     *
     * @param format
     *            print format string to write to the output file
     *
     * @param args
     *            variable list of arguments referenced by the format specifiers in the format
     *            string
     *********************************************************************************************/
    public void writeToFileFormat(PrintWriter printWriter, String format, Object... args)
    {
        fileIOHandler.writeToFileFormat(printWriter, format, args);
    }

    /**********************************************************************************************
     * Close the specified output file
     *
     * @param printWriter
     *            output file PrintWriter object
     *********************************************************************************************/
    public void closeFile(PrintWriter printWriter)
    {
        fileIOHandler.closeFile(printWriter);
    }

    /**********************************************************************************************
     * Get an array containing the data field information for the project
     *
     * @return Array containing the data field information for the project; an empty array if the
     *         project has no data fields. The array in is the format: field name, description,
     *         size, input type, required (true or false), applicability, value[,...]
     *********************************************************************************************/
    public String[][] getProjectFields()
    {
        List<String[]> projectFields = new ArrayList<String[]>();

        // Step through each data field belonging to the project
        for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldProjectName()))
        {
            // Add the data field information to the list
            projectFields.add(new String[] {fieldInfo.getFieldName(),
                                            fieldInfo.getDescription(),
                                            Integer.toString(fieldInfo.getSize()),
                                            fieldInfo.getInputType().getInputName(),
                                            Boolean.toString(fieldInfo.isRequired()),
                                            fieldInfo.getApplicabilityType().getApplicabilityName(),
                                            fieldInfo.getValue()});
        }

        return projectFields.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the description of the specified link
     *
     * @param streamName
     *            data stream name
     *
     * @param linkName
     *            link name
     *
     * @return Link description; returns a blank if the data stream or link don't exist, or the
     *         link has no description
     *********************************************************************************************/
    public String getLinkDescription(String streamName, String linkName)
    {
        // Check if a link handler isn't provided
        if (linkHandler == null)
        {
            // Create a link handler
            linkHandler = new CcddLinkHandler(ccddMain, parent);
        }

        String description = "";

        // Get the rate information based on the supplied data stream name
        RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

        // Check if the rate information exists with this stream name
        if (rateInfo != null)
        {
            // Get the link description based on the rate column and link names
            description = linkHandler.getLinkDescription(rateInfo.getRateName(), linkName);
        }

        return description;
    }

    /**********************************************************************************************
     * Return the sample rate for the specified link
     *
     * @param streamName
     *            data stream name
     *
     * @param linkName
     *            link name
     *
     * @return Text representation of the sample rate, in samples per second, of the specified
     *         link. For rates equal to or faster than 1 sample per second the string represents a
     *         whole number; for rates slower than 1 sample per second the string is in the form
     *         number of samples / number of seconds; returns a blank if the data stream or link
     *         don't exist
     *********************************************************************************************/
    public String getLinkRate(String streamName, String linkName)
    {
        // Check if a link handler isn't provided
        if (linkHandler == null)
        {
            // Create a link handler
            linkHandler = new CcddLinkHandler(ccddMain, parent);
        }

        String sampleRate = "";

        // Get the rate information based on the supplied data stream name
        RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

        // Check if the rate information exists with this stream name
        if (rateInfo != null)
        {
            // Get the link sample rate based on the rate column and link names
            sampleRate = linkHandler.getLinkRate(rateInfo.getRateName(), linkName);
        }

        return sampleRate;
    }

    /**********************************************************************************************
     * Get the array of data stream and link names to which the specified variable belongs
     *
     * @param variableName
     *            variable path and name
     *
     * @return Array containing the data stream and link names to which the specified variable is a
     *         member; returns an empty array if the variable does not belong to a link
     *********************************************************************************************/
    public String[][] getVariableLinks(String variableName)
    {
        // Check if a link handler isn't provided
        if (linkHandler == null)
        {
            // Create a link handler
            linkHandler = new CcddLinkHandler(ccddMain, parent);
        }

        return linkHandler.getVariableLinks(variableName, true);
    }

    /**********************************************************************************************
     * Get the byte offset of the specified variable relative to its parent structure. The
     * variable's path, including parent structure and variable name, is used to verify that the
     * specified target has been located; i.e., not another variable with the same name
     *
     * @param path
     *            a comma separated string of the parent structure and each data type and variable
     *            name of each variable in the current search path
     *
     * @return The byte offset to the target variable relative to its parent structure; returns -1
     *         if the parent-variable path combination is invalid
     *********************************************************************************************/
    public int getVariableOffset(String path)
    {
        return variableHandler.getVariableOffset(path);
    }

    /**********************************************************************************************
     * Get the array representing the CFS application name data field values associated with the
     * link entries. Each application name appears only once in the array
     *
     * @param dataFieldName
     *            name of the application name data field
     *
     * @return Array containing the contents of the specified CFS application name data field
     *         associated with each of the tables referenced by the link entries
     *********************************************************************************************/
    public String[] getLinkApplicationNames(String dataFieldName)
    {
        // Check if a link handler isn't provided
        if (linkHandler == null)
        {
            // Create a link handler
            linkHandler = new CcddLinkHandler(ccddMain, parent);
        }

        return linkHandler.getApplicationNames(dataFieldName);
    }

    /**********************************************************************************************
     * Get the array of group names referenced in the script association
     *
     * @return Array of group names referenced in the script association; empty array if no groups
     *         are referenced
     *********************************************************************************************/
    public String[] getAssociatedGroupNames()
    {
        return groupNames.toArray(new String[0]);
    }

    /**********************************************************************************************
     * Get the array of group names
     *
     * @param applicationOnly
     *            true if only those groups that represent a CFS application should be returned
     *
     * @return Array of group names (application groups only if the input flag is true); empty
     *         array if no groups are defined
     *********************************************************************************************/
    public String[] getGroupNames(boolean applicationOnly)
    {
        // Check if a group handler isn't provided
        if (groupHandler == null)
        {
            // Create a group handler
            groupHandler = new CcddGroupHandler(ccddMain, null, parent);
        }

        return groupHandler.getGroupNames(applicationOnly);
    }

    /**********************************************************************************************
     * Get the description for the specified group
     *
     * @param groupName
     *            group name
     *
     * @return Description for the specified group; blank if the group has no description or the
     *         group doesn't exist
     *********************************************************************************************/
    public String getGroupDescription(String groupName)
    {
        // Check if a group handler isn't provided
        if (groupHandler == null)
        {
            // Create a group handler
            groupHandler = new CcddGroupHandler(ccddMain, null, parent);
        }

        return groupHandler.getGroupDescription(groupName);
    }

    /**********************************************************************************************
     * Get an array containing the table members, including the member table ancestor tables, for
     * the specified group
     *
     * @param groupName
     *            group name
     *
     * @return Array containing the table members for the specified group; an empty array if the
     *         group has no table members or the group doesn't exist
     *********************************************************************************************/
    public String[] getGroupTables(String groupName)
    {
        // Check if a group handler isn't provided
        if (groupHandler == null)
        {
            // Create a group handler
            groupHandler = new CcddGroupHandler(ccddMain, null, parent);
        }

        String[] groupTables = new String[0];

        // Get a reference to the group's information
        GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

        // Check if the group exists
        if (groupInfo != null)
        {
            // Get the list of the group's tables
            groupTables = groupInfo.getTablesAndAncestors().toArray(new String[0]);
        }

        return groupTables;
    }

    /**********************************************************************************************
     * Get an array containing the data field information for the specified group
     *
     * @param groupName
     *            group name
     *
     * @return Array containing the data field information for the specified group; an empty array
     *         if the group has no data fields or the group doesn't exist, or null if the group
     *         handler isn't active. The array in is the format: field name, description, size,
     *         input type, required (true or false), applicability, value[,...]
     *********************************************************************************************/
    public String[][] getGroupFields(String groupName)
    {
        // Check if a group handler isn't provided
        if (groupHandler == null)
        {
            // Create a group handler
            groupHandler = new CcddGroupHandler(ccddMain, null, parent);
        }

        List<String[]> groupFields = new ArrayList<String[]>();

        // Get a reference to the group's information
        GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupName);

        // Check if the group exists
        if (groupInfo != null)
        {
            // Step through each data field belonging to the group
            for (FieldInformation fieldInfo : fieldHandler.getFieldInformationByOwner(CcddFieldHandler.getFieldGroupName(groupName)))
            {
                // Add the data field information to the list
                groupFields.add(new String[] {fieldInfo.getFieldName(),
                                              fieldInfo.getDescription(),
                                              Integer.toString(fieldInfo.getSize()),
                                              fieldInfo.getInputType().getInputName(),
                                              Boolean.toString(fieldInfo.isRequired()),
                                              fieldInfo.getApplicabilityType().getApplicabilityName(),
                                              fieldInfo.getValue()});
            }
        }

        return groupFields.toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Get the copy table column names
     *
     * @return Array containing the copy table column names
     *********************************************************************************************/
    public String[] getCopyTableColumnNames()
    {
        String[] columnNames = new String[CopyTableEntry.values().length];

        // Step through each copy table column
        for (int index = 0; index < CopyTableEntry.values().length; index++)
        {
            // Get the column name
            columnNames[index] = CopyTableEntry.values()[index].getColumnName();
        }

        return columnNames;
    }

    /**********************************************************************************************
     * Get the copy table for the messages of the specified data stream. Any macro embedded in a
     * variable name is replaced by its corresponding value
     *
     * @param streamName
     *            data stream name
     *
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS header size is 12
     *
     * @param messageIDNameField
     *            name of the message ID name data field (e.g., 'Message ID name')
     *
     * @param optimize
     *            true to combine memory copy calls for consecutive variables in the copy table
     *
     * @return Array containing the copy table entries; returns blank if there are no entries for
     *         the specified data stream or if data stream name is invalid. Any macro embedded in a
     *         variable name is replaced by its corresponding value
     *********************************************************************************************/
    public String[][] getCopyTableEntries(String streamName,
                                          int headerSize,
                                          String messageIDNameField,
                                          boolean optimize)
    {
        // Create the copy table with macros expanded
        return getCopyTableEntries(streamName,
                                   headerSize,
                                   messageIDNameField,
                                   null,
                                   optimize,
                                   true);
    }

    /**********************************************************************************************
     * Get the copy table for the messages of the specified data stream. Any macro embedded in a
     * variable name is left in place
     *
     * @param streamName
     *            data stream name
     *
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS header size is 12
     *
     * @param messageIDNameField
     *            name of the message ID name data field (e.g., 'Message ID name')
     *
     * @param optimize
     *            true to combine memory copy calls for consecutive variables in the copy table
     *
     * @return Array containing the copy table entries with any macro embedded in a variable name
     *         left in place; returns blank if there are no entries for the specified data stream
     *         or if data stream name is invalid
     *********************************************************************************************/
    public String[][] getCopyTableEntriesWithMacros(String streamName,
                                                    int headerSize,
                                                    String messageIDNameField,
                                                    boolean optimize)
    {
        // Create the copy table with macros unexpanded
        return getCopyTableEntries(streamName,
                                   headerSize,
                                   messageIDNameField,
                                   null,
                                   optimize,
                                   false);
    }

    /**********************************************************************************************
     * Get the copy table for the messages of the specified data stream. Any macro embedded in a
     * variable name is replaced by its corresponding value
     *
     * @param streamName
     *            data stream name
     *
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS header size is 12
     *
     * @param tlmMessageIDs
     *            array containing string array entries giving the structure table path+name and
     *            the table's associated message ID name
     *
     * @param optimize
     *            true to combine memory copy calls for consecutive variables in the copy table
     *
     * @return Array containing the copy table entries; returns blank if there are no entries for
     *         the specified data stream or if data stream name is invalid. Any macro embedded in a
     *         variable name is replaced by its corresponding value
     *********************************************************************************************/
    public String[][] getCopyTableEntries(String streamName,
                                          int headerSize,
                                          String[][] tlmMessageIDs,
                                          boolean optimize)
    {
        // Convert the array of structure tables and their message ID names to a list
        ArrayListMultiple tlmMessageIDsList = new ArrayListMultiple();
        tlmMessageIDsList.addAll(Arrays.asList(tlmMessageIDs));

        // Create the copy table with macros expanded
        return getCopyTableEntries(streamName,
                                   headerSize,
                                   null,
                                   tlmMessageIDsList,
                                   optimize,
                                   true);
    }

    /**********************************************************************************************
     * Get the copy table for the messages of the specified data stream. Any macro embedded in a
     * variable name is left in place
     *
     * @param streamName
     *            data stream name
     *
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS header size is 12
     *
     * @param tlmMessageIDs
     *            array containing string array entries giving the structure table path+name and
     *            the table's associated message ID name
     *
     * @param optimize
     *            true to combine memory copy calls for consecutive variables in the copy table
     *
     * @return Array containing the copy table entries with any macro embedded in a variable name
     *         left in place; returns blank if there are no entries for the specified data stream
     *         or if data stream name is invalid
     *********************************************************************************************/
    public String[][] getCopyTableEntriesWithMacros(String streamName,
                                                    int headerSize,
                                                    String[][] tlmMessageIDs,
                                                    boolean optimize)
    {
        // Convert the array of structure tables and their message ID names to a list
        ArrayListMultiple tlmMessageIDsList = new ArrayListMultiple();
        tlmMessageIDsList.addAll(Arrays.asList(tlmMessageIDs));

        // Create the copy table with macros unexpanded
        return getCopyTableEntries(streamName,
                                   headerSize,
                                   null,
                                   tlmMessageIDsList,
                                   optimize,
                                   false);
    }

    /**********************************************************************************************
     * Get the copy table for the messages of the specified data stream
     *
     * @param streamName
     *            data stream name
     *
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS header size is 12
     *
     * @param messageIDNameField
     *            name of the structure table data field containing the message ID name. If
     *            provided this is used instead of the tlmMessageIDs list
     *
     * @param tlmMessageIDs
     *            list containing string array entries giving the structure table path+name and the
     *            table's associated message ID name. Used if messageIDNameField is null
     *
     * @param optimize
     *            true to combine memory copy calls for consecutive variables in the copy table
     *
     * @param expandMacros
     *            true to expand any macro within the variable names
     *
     * @return Array containing the copy table entries; returns blank if there are no entries for
     *         the specified data stream or if data stream name is invalid
     *********************************************************************************************/
    private String[][] getCopyTableEntries(String streamName,
                                           int headerSize,
                                           String messageIDNameField,
                                           ArrayListMultiple tlmMessageIDs,
                                           boolean optimize,
                                           boolean expandMacros)
    {
        String[][] entries = new String[0][0];

        // Check if the copy table handler doesn't exist
        if (copyHandler == null)
        {
            // Create the copy table handler
            copyHandler = new CcddCopyTableHandler(ccddMain);
        }

        // Check if a link handler isn't provided
        if (linkHandler == null)
        {
            // Create a link handler
            linkHandler = new CcddLinkHandler(ccddMain, parent);
        }

        // Check if this is a valid stream name
        if (rateHandler.getRateInformationIndexByStreamName(streamName) != -1)
        {
            // Create the copy table
            entries = copyHandler.createCopyTable(linkHandler,
                                                  streamName,
                                                  headerSize,
                                                  messageIDNameField,
                                                  tlmMessageIDs,
                                                  optimize,
                                                  expandMacros);
        }

        return entries;
    }

    /**********************************************************************************************
     * Get the copy table message names and their corresponding ID values for the specified data
     * stream
     *
     * @param streamName
     *            data stream name
     *
     * @return Array containing the copy table message names and ID values; returns blank if there
     *         are no entries for the specified data stream or if data stream name is invalid
     *********************************************************************************************/
    public String[][] getTelemetryMessageIDs(String streamName)
    {
        String[][] messageIDs = new String[0][0];

        // Check if the copy table handler doesn't exist
        if (copyHandler == null)
        {
            // Create the copy table handler
            copyHandler = new CcddCopyTableHandler(ccddMain);
        }

        // Check if this is a valid stream name
        if (rateHandler.getRateInformationIndexByStreamName(streamName) != -1)
        {
            // Get the message names and IDs for this data stream
            messageIDs = copyHandler.getTelemetryMessageIDs(streamName);
        }

        return messageIDs;
    }

    /**********************************************************************************************
     * Get an array containing every message owner, name, and ID from every table cell, data field
     * (table or group), and telemetry message. Message names and IDs are determined by the input
     * type assigned to the table column or data field
     *
     * @return Two-dimensional array containing every message owner, name, and ID, sorted by the
     *         owner name. Each row in the array is an array in the form [owner name], [message
     *         name], [message ID]. The owner name is preceded by 'Group:' if the owner is a group,
     *         and by "Tlm:' if the owner is a telemetry message
     *********************************************************************************************/
    public String[][] getMessageOwnersNamesAndIDs()
    {
        return ccddMain.getMessageIDHandler().getMessageOwnersNamesAndIDs(MessageIDSortOrder.BY_OWNER,
                                                                          true,
                                                                          parent)
                       .toArray(new String[0][0]);
    }

    /**********************************************************************************************
     * Parse the supplied string containing a message name and ID into an array with the name in
     * index 0 and the ID in index 1 (depending on the input string either or both may be blank).
     * If only the name or ID is present in the supplied string the output is based on if the
     * string evaluates to a hexadecimal value (treated as the ID; name is blank) or not (treated
     * as the name; ID is blank)
     *
     * @param msgNameAndID
     *            message name and ID in the format [&lt;message name&gt;] [&lt;message ID&gt;]
     *
     * @return One-dimensional array containing the message name in index 0 and the ID in index 1
     *         (depending on the input string either or both may be blank)
     *********************************************************************************************/
    public String[] parseMessageNameAndID(String msgNameAndID)
    {
        return CcddMessageIDHandler.getMessageNameAndID(msgNameAndID);
    }

    /**********************************************************************************************
     * Get a string array containing all of the data stream names in the project
     *
     * @return Array containing the unique data stream names
     *********************************************************************************************/
    public String[] getDataStreamNames()
    {
        return rateHandler.getDataStreamNames();
    }

    /**********************************************************************************************
     * Get an array containing the application names in the project
     *
     * @return Array of application names; the list is empty if no application names exist
     *********************************************************************************************/
    public String[] getApplicationNames()
    {
        // Check if a group handler isn't provided
        if (groupHandler == null)
        {
            // Create a group handler
            groupHandler = new CcddGroupHandler(ccddMain, null, parent);
        }

        return groupHandler.getGroupNames(true);
    }

    /**********************************************************************************************
     * Get the array of defined parameters for the schedule definition table
     *
     * @return Two-dimensional array containing the defined parameters
     *********************************************************************************************/
    public String[][] getApplicationScheduleDefinitionTableDefines()
    {
        // Check if the scheduler table handler doesn't exist
        if (schTable == null)
        {
            // Initialize the scheduler table
            schTable = new CcddApplicationSchedulerTableHandler(ccddMain);
        }

        return schTable.getScheduleDefinitionTableDefines();
    }

    /**********************************************************************************************
     * Get the specified entry in the application scheduler schedule definition table
     *
     * @param row
     *            row index for the entry in the schedule definition table
     *
     * @return Array containing the specified entry in the schedule definition table
     *********************************************************************************************/
    public String[][] getApplicationScheduleDefinitionTable(int row)
    {
        // Check if the scheduler table handler doesn't exist
        if (schTable == null)
        {
            // Initialize the scheduler table
            schTable = new CcddApplicationSchedulerTableHandler(ccddMain);
        }

        return schTable.getScheduleDefinitionTableByRow(row);
    }

    /**********************************************************************************************
     * Get the application scheduler message definition table
     *
     * @return Array containing the message definition table information
     *********************************************************************************************/
    public String[] getApplicationMessageDefinitionTable()
    {
        // Check if the scheduler table handler doesn't exist
        if (schTable == null)
        {
            // Initialize the scheduler table
            schTable = new CcddApplicationSchedulerTableHandler(ccddMain);
        }

        return schTable.getMessageDefinitionTable();
    }

    /**********************************************************************************************
     * Get the number of time slots in the schedule definition table
     *
     * @return Number of time slots in schedule definition table
     *********************************************************************************************/
    public int getNumberOfTimeSlots()
    {
        // Check if the scheduler table handler doesn't exist
        if (schTable == null)
        {
            // Initialize the scheduler table
            schTable = new CcddApplicationSchedulerTableHandler(ccddMain);
        }

        return schTable.getNumberOfTimeSlots();
    }

    /**********************************************************************************************
     * Get the name of the prototype table for the specified table
     *
     * @param tableName
     *            table name
     *
     * @return The name of the prototype table for the specified table
     *********************************************************************************************/
    public String getPrototypeName(String tableName)
    {
        return TableInformation.getPrototypeName(tableName);
    }

    /**********************************************************************************************
     * Check if the supplied variable name represents an array member
     *
     * @param variableName
     *            variable name
     *
     * @return true if the variable name is an array member
     *********************************************************************************************/
    public boolean isArrayMember(Object variableName)
    {
        return ArrayVariable.isArrayMember(variableName);
    }

    /**********************************************************************************************
     * Get the integer array containing the size of each array dimension from the supplied array
     * size string
     *
     * @param arrayString
     *            array size in the format {@literal [#]<[#]<...>> or #<,#<...>>}
     *
     * @return Array of integers containing the size of each array dimension
     *********************************************************************************************/
    public int[] getArrayIndexFromSize(String arrayString)
    {
        return ArrayVariable.getArrayIndexFromSize(arrayString);
    }

    /**********************************************************************************************
     * Convert an integer array containing the size of each array dimension into a string in the
     * format {@literal [#]<[#]<...>>}
     *
     * @param arrayIndex
     *            array of integers containing the size of each array dimension
     *
     * @return Array size in the format {@literal [#]<[#]<...>>}
     *********************************************************************************************/
    public String formatArrayIndex(int[] arrayIndex)
    {
        return ArrayVariable.formatArrayIndex(arrayIndex);
    }

    /**********************************************************************************************
     * Export the tables in XTCE XML format to the specified file. This is the main entry point
     * when using a script association to perform the export. It calls the internal method to set
     * up and parse the tables for export
     *
     * @param outputFileName
     *            output file name
     *
     * @param isBigEndian
     *            true if the data is big endian
     *
     * @param isHeaderBigEndian
     *            true if the telemetry and command headers big endian
     *
     * @param version
     *            version attribute (for the space system headers)
     *
     * @param validationStatus
     *            validation status attribute (for the space system headers)
     *
     * @param classification1
     *            first level classification attribute (for the space system headers)
     *
     * @param classification2
     *            second level classification attribute (for the space system headers)
     *
     * @param classification3
     *            third level classification attribute (for the space system headers)
     *
     * @return true if an error occurred preventing exporting the project to the file
     *********************************************************************************************/
    public boolean xtceExport(String outputFileName,
                              boolean isBigEndian,
                              boolean isHeaderBigEndian,
                              String version,
                              String validationStatus,
                              String classification1,
                              String classification2,
                              String classification3)
    {
        boolean errorFlag = false;

        try
        {
            // Create the XTCE handler
            xtceHandler = new CcddXTCEHandler(ccddMain, scriptEngine, parent);

            // Export the specified tables to the specified output file in XTCE XML format
            xtceHandler.exportToFile(new FileEnvVar(outputFileName),
                                     getTableNames(),
                                     true,
                                     true, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     false, // unused for XTCE export
                                     null, // unused for XTCE export
                                     null, // unused for XTCE export
                                     (isBigEndian
                                                  ? EndianType.BIG_ENDIAN
                                                  : EndianType.LITTLE_ENDIAN),
                                     isHeaderBigEndian,
                                     version,
                                     validationStatus,
                                     classification1,
                                     classification2,
                                     classification3);
        }
        catch (JAXBException | CCDDException jce)
        {
            // Inform the user that an error occurred
            new CcddDialogHandler().showMessageDialog(parent,
                                                      "<html><b>"
                                                              + jce.getMessage(),
                                                      "Export Error",
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
    public void xtceAddSpaceSystemHeader(SpaceSystemType spaceSystem,
                                         String classification,
                                         String validationStatus,
                                         String version,
                                         String date) throws CCDDException
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addSpaceSystemHeader(spaceSystem,
                                             classification,
                                             validationStatus,
                                             version,
                                             date);
        }
    }

    /**********************************************************************************************
     * Create the space system telemetry metadata
     *
     * @param spaceSystem
     *            space system reference
     *********************************************************************************************/
    public void xtceCreateTelemetryMetadata(SpaceSystemType spaceSystem)
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.createTelemetryMetadata(spaceSystem);
        }
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
    public void xtceAddSpaceSystemParameters(SpaceSystemType spaceSystem,
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
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addSpaceSystemParameters(spaceSystem,
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
                                                 applicationID);
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
    public void xtceAddParameterAndType(SpaceSystemType spaceSystem,
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
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addParameterAndType(spaceSystem,
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
    public boolean xtceAddParameterSequenceEntry(SpaceSystemType spaceSystem,
                                                 String parameterName,
                                                 String dataType,
                                                 String arraySize,
                                                 EntryListType entryList,
                                                 boolean isTlmHdrRef) throws CCDDException
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            isTlmHdrRef = xtceHandler.addParameterSequenceEntry(spaceSystem,
                                                                parameterName,
                                                                dataType,
                                                                arraySize,
                                                                entryList,
                                                                isTlmHdrRef);
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
    public void xtceSetParameterDataType(SpaceSystemType spaceSystem,
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
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.setParameterDataType(spaceSystem,
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
    public void xtceCreateCommandMetadata(SpaceSystemType spaceSystem) throws CCDDException
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.createCommandMetadata(spaceSystem);
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
    protected void xtceAddSpaceSystemCommands(SpaceSystemType spaceSystem,
                                              String[][] tableData,
                                              int cmdNameColumn,
                                              int cmdCodeColumn,
                                              int cmdDescColumn,
                                              boolean isCmdHeader,
                                              String cmdHdrSysPath,
                                              String applicationID) throws CCDDException
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addSpaceSystemCommands(spaceSystem,
                                               tableData,
                                               cmdNameColumn,
                                               cmdCodeColumn,
                                               cmdDescColumn,
                                               isCmdHeader,
                                               cmdHdrSysPath,
                                               applicationID);
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
     *            list of command argument names
     *
     * @param argDataTypes
     *            list of of command argument data types
     *
     * @param argArraySizes
     *            list of of command argument array sizes; the list item is null or blank if the
     *            corresponding argument isn't an array
     *
     * @param description
     *            description of the command
     *
     * @throws CCDDException
     *             If an error occurs executing an external (script) method
     *********************************************************************************************/
    public void xtceAddCommand(SpaceSystemType spaceSystem,
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
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addCommand(spaceSystem,
                                   commandName,
                                   cmdFuncCode,
                                   applicationID,
                                   isCmdHeader,
                                   cmdHdrSysPath,
                                   argumentNames,
                                   argDataTypes,
                                   argArraySizes,
                                   description);
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
     *            {@literal command argument enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify}
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
    public NameDescriptionType xtceSetArgumentDataType(SpaceSystemType spaceSystem,
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
        NameDescriptionType commandDescription = null;

        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            commandDescription = xtceHandler.setArgumentDataType(spaceSystem,
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
    public void xtceAddContainerReference(String parameterName,
                                          String dataType,
                                          String arraySize,
                                          Object entryList) throws CCDDException
    {
        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            xtceHandler.addContainerReference(parameterName, dataType, arraySize, entryList);
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
    public UnitSet xtceCreateUnitSet(String units)
    {
        UnitSet unitSet = null;

        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            unitSet = xtceHandler.createUnitSet(units);
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
    public EnumerationList xtceCreateEnumerationList(SpaceSystemType spaceSystem,
                                                     String enumeration)
    {
        EnumerationList enumList = null;

        // Check if the XTCE handler exists
        if (xtceHandler != null)
        {
            enumList = xtceHandler.createEnumerationList(spaceSystem, enumeration);
        }

        return enumList;
    }

    /**********************************************************************************************
     * Convert the primitive data type into the base equivalent
     *
     * @param dataType
     *            data type
     *
     * @return Base primitive data type corresponding to the specified primitive data type; null if
     *         no match
     *********************************************************************************************/
    public BasePrimitiveDataType xmlGetBaseDataType(String dataType)
    {
        return CcddImportSupportHandler.getBaseDataType(dataType, dataTypeHandler);
    }

    /**********************************************************************************************
     * Replace each invalid character with an underscore and move any leading underscores to the
     * end of each path segment
     *
     * @param path
     *            {@literal system path in the form <</>path1</path2<...>>}
     *
     * @return Path with each invalid character replaced with an underscore and any leading
     *         underscores moved to the end of each path segment
     *********************************************************************************************/
    public String xmlCleanSystemPath(String path)
    {
        return CcddImportSupportHandler.cleanSystemPath(path);
    }

    /**********************************************************************************************
     * *** TODO INCLUDED FOR TESTING ***
     *
     * Display the table information for each associated table type
     *********************************************************************************************/
    public void showData()
    {
        // Check if no table is associated with the script
        if (tableInformation == null
            || tableInformation.length == 0
            || tableInformation[0].getType().isEmpty())
        {
            System.out.println("No table data associated with this script");
        }
        // A table is associated with the script
        else
        {
            // Step through the information for each table type
            for (TableInformation tableInfo : tableInformation)
            {
                // Display the table's type
                System.out.println("Table data for type '" + tableInfo.getType() + "'");

                // Get the table's data
                Object[][] data = tableInfo.getData();

                // Step through each row in the table
                for (int row = 0; row < data.length; row++)
                {
                    // Display the row of table data
                    System.out.println(row + ": " + Arrays.toString(data[row]));
                }

                System.out.println("");
            }
        }
    }
}
