/**
 * CFS Command & Data Dictionary script data access handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.PATH_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COLUMN_DELTA;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;
import static CCDD.CcddConstants.EventLogMessageType.SUCCESS_MSG;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;

import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.RateInformation;
import CCDD.CcddClasses.TableInformation;
import CCDD.CcddConstants.BaseDataTypeInfo;
import CCDD.CcddConstants.CopyTableEntry;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.TablePathType;
import CCDD.CcddConstants.TableTreeType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/******************************************************************************
 * CFS Command & Data Dictionary script data access class. THis class contains
 * public methods that are accessible to the data output scripts
 *****************************************************************************/
public class CcddScriptDataAccessHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbCommandHandler dbCommand;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddDbControlHandler dbControl;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddDataTypeHandler dataTypeHandler;
    private final CcddFileIOHandler fileIOHandler;
    private final CcddLinkHandler linkHandler;
    private final CcddFieldHandler fieldHandler;
    private final CcddGroupHandler groupHandler;
    private final CcddRateParameterHandler rateHandler;
    private final CcddMacroHandler macroHandler;
    private CcddTableTreeHandler tableTree;
    private final CcddEventLogDialog eventLog;
    private CcddApplicationSchedulerTable schTable;
    private CcddCopyTableHandler copyHandler;

    // Calling GUI component
    private final Component parent;

    // Name of the script file being executed
    private final String scriptFileName;

    // Data table information array
    private final TableInformation[] tableInformation;

    // Lists that show a variable's full name before and after converting any
    // commas and brackets to underscores. Only variable's where the converted
    // name matches another variable's are saved in the lists
    List<String> originalVariableNameList;
    List<String> convertedVariableNameList;

    /**************************************************************************
     * Script data access class constructor
     * 
     * @param ccddMain
     *            main class
     * 
     * @param tableInformation
     *            array of table information
     * 
     * @param linkHandler
     *            link handler reference
     * 
     * @param fieldHandler
     *            field handler reference
     * 
     * @param groupHandler
     *            group handler reference
     * 
     * @param scriptFileName
     *            name of the script file being executed
     * 
     * @param scriptDialog
     *            reference to the GUI component from which this class was
     *            generated (script dialog if executing from within the CCDD
     *            application; main window frame if executing from the command
     *            line)
     *************************************************************************/
    protected CcddScriptDataAccessHandler(CcddMain ccddMain,
                                          TableInformation[] tableInformation,
                                          CcddLinkHandler linkHandler,
                                          CcddFieldHandler fieldHandler,
                                          CcddGroupHandler groupHandler,
                                          String scriptFileName,
                                          Component scriptDialog)
    {
        this.ccddMain = ccddMain;
        this.tableInformation = tableInformation;
        this.linkHandler = linkHandler;
        this.fieldHandler = fieldHandler;
        this.groupHandler = groupHandler;
        this.scriptFileName = scriptFileName;
        this.parent = scriptDialog;
        dbCommand = ccddMain.getDbCommandHandler();
        dbTable = ccddMain.getDbTableCommandHandler();
        dbControl = ccddMain.getDbControlHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        dataTypeHandler = ccddMain.getDataTypeHandler();
        fileIOHandler = ccddMain.getFileIOHandler();
        rateHandler = ccddMain.getRateParameterHandler();
        macroHandler = ccddMain.getMacroHandler();
        eventLog = ccddMain.getSessionEventLog();
        tableTree = null;
        copyHandler = null;
        originalVariableNameList = null;
        convertedVariableNameList = null;
    }

    /**************************************************************************
     * Get the table information for the table type specified
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command". The table type is converted to the generic type
     *            ("Structure" or "Command") if the specified type is a
     *            representative of the generic type
     * 
     * @return Table information class for the type specified; return null if
     *         an instance of the table type doesn't exist
     *************************************************************************/
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
            // Check if the requested type matches the table information type;
            // ignore case sensitivity
            if (info.getType().equalsIgnoreCase(tableType))
            {
                // Store the table information reference and stop searching
                tableInfo = info;
                break;
            }
        }

        return tableInfo;
    }

    /**************************************************************************
     * Get the name of the script file being executed
     * 
     * @return Name of the script file being executed
     *************************************************************************/
    public String getScriptName()
    {
        return scriptFileName;
    }

    /**************************************************************************
     * Get the name of the user executing the script
     * 
     * @return Name of the user executing the script
     *************************************************************************/
    public String getUser()
    {
        return dbControl.getUser();
    }

    /**************************************************************************
     * Get the name of the project database
     * 
     * @return Name of the project database
     *************************************************************************/
    public String getProject()
    {
        return dbControl.getDatabase();
    }

    /**************************************************************************
     * Get the number of characters in longest string in an array of strings
     * 
     * @param strgArray
     *            array of strings
     * 
     * @param minWidth
     *            initial minimum widths; null to use zero as the minimum
     * 
     * @return Character length of the longest string in the supplied array;
     *         null if an input is invalid
     *************************************************************************/
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

    /**************************************************************************
     * Get the number of characters in longest string in each column of an
     * array of strings
     * 
     * @param strgArray
     *            array of string arrays
     * 
     * @param minWidths
     *            array of initial minimum widths; null to use zero as the
     *            minimum for each column
     * 
     * @return Character length of the longest string in each column of the
     *         supplied array; null if any of the inputs is invalid
     *************************************************************************/
    public Integer[] getLongestStrings(String[][] strgArray,
                                       Integer[] minWidths)
    {
        // Check if the string array contains at least one row and column, and
        // that either no initial minimum widths are specified or that the
        // number of minimum widths is greater than or equal to the number of
        // string columns
        if (strgArray.length != 0
            && strgArray[0].length != 0
            && (minWidths == null
            || minWidths.length >= strgArray[0].length))
        {
            // Check if no initial minimum widths are supplied
            if (minWidths == null)
            {
                // Create storage for the minimum widths
                minWidths = new Integer[strgArray[0].length];
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

    /**************************************************************************
     * Get the current date and time in the form:
     * 
     * dow mon dd hh:mm:ss zzz yyyy
     * 
     * where: dow is the day of the week (Sun, Mon, Tue, Wed, Thu, Fri, Sat);
     * mon is the month (Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov,
     * Dec); dd is the day of the month (01 through 31), as two decimal digits;
     * hh is the hour of the day (00 through 23), as two decimal digits; mm is
     * the minute within the hour (00 through 59), as two decimal digits; ss is
     * the second within the minute (00 through 61, as two decimal digits; zzz
     * is the time zone (and may reflect daylight saving time); yyyy is the
     * year, as four decimal digits
     * 
     * @return Current date and time
     *************************************************************************/
    public String getDateAndTime()
    {
        return new Date().toString();
    }

    /**************************************************************************
     * Get the array containing the user-defined data type names and their
     * corresponding C-language, size (in bytes), and base data type values
     * 
     * @return Array where each row contains a user-defined data type name and
     *         its corresponding C-language, size (in bytes), and base data
     *         type values
     *************************************************************************/
    public String[][] getDataTypeDefinitions()
    {
        return dataTypeHandler.getDataTypeData().toArray(new String[0][0]);
    }

    /**************************************************************************
     * Determine if the supplied data type is a primitive type
     * 
     * @param dataType
     *            data type to test
     * 
     * @return true if the supplied data type is a primitive; false otherwise
     *************************************************************************/
    public boolean isDataTypePrimitive(String dataType)
    {
        return dataTypeHandler.isPrimitive(dataType);
    }

    /**************************************************************************
     * Get the base type for the specified data type
     * 
     * @param dataType
     *            primitive data type
     * 
     * @return Base type for the specified data type; returns null if the data
     *         type doesn't exist or isn't a primitive type
     *************************************************************************/
    public String getBaseDataType(String dataType)
    {
        String baseType = null;

        // Get the base data type information based on the data type
        BaseDataTypeInfo baseTypeInfo = dataTypeHandler.getBaseDataType(dataType);

        // Check if the data type exists
        if (baseTypeInfo != null)
        {
            // Get the base type for the data type
            baseType = dataTypeHandler.getBaseDataType(dataType).getName();
        }

        return baseType;
    }

    /**************************************************************************
     * Get the number of bytes for the specified data type
     * 
     * @param dataType
     *            structure or primitive data type
     * 
     * @return Number of bytes required to store the data type; returns 0 if
     *         the data type doesn't exist
     *************************************************************************/
    public int getDataTypeSizeInBytes(String dataType)
    {
        return linkHandler.getDataTypeSizeInBytes(dataType);
    }

    /**************************************************************************
     * Convert a primitive data type into its ITOS encoded form
     * 
     * @param dataType
     *            data type (e.g., "uint16" or "double")
     * 
     * @param encoding
     *            "SINGLE_CHAR" to get the single character encoding (e.g., "I"
     *            for any integer type); "BIG_ENDIAN" to get the encoding as
     *            big endian; "BIG_ENDIAN_SWAP" to get the encoding as a big
     *            endian with byte swapping; "LITTLE_ENDIAN" to get the
     *            encoding as little endian; "LITTLE_ENDIAN_SWAP" to get the
     *            encoding as a little endian with byte swapping. The encoding
     *            parameter is case insensitive
     * 
     * @return ITOS encoded form of the data type in the format requested
     *         (e.g., "int32" and "LITTLE_ENDIAN" returns "I12345678"); returns
     *         the data type, unmodified, if the data type is not recognized
     *************************************************************************/
    public String getITOSEncodedDataType(String dataType, String encoding)
    {
        String encodedType;

        // Check if the data type is a recognized primitive
        if (dataTypeHandler.isPrimitive(dataType))
        {
            // Set the encoding character based on the data type's base type.
            // Check if the data type is in integer
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
                        // All string types (characters and strings) are set to
                        // 1
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
                                // Append the byte number for the next byte and
                                // the current byte to the encoding string
                                encodedType += String.valueOf(i + 1)
                                               + String.valueOf(i);
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
                                // Append the byte number for the previous byte
                                // and the current byte to the encoding string
                                encodedType += String.valueOf(i - 1)
                                               + String.valueOf(i);
                            }
                        }

                        break;

                    case "SINGLE_CHAR":
                    default:
                        break;
                }
            }
            // The data type is unrecognized; treat as 'raw'. Check if the
            // request is not for the single character encoding
            else if (!encoding.equalsIgnoreCase("SINGLE_CHAR"))
            {
                // Append a '0' to the encoding string
                encodedType += String.valueOf(0);
            }
        }
        // Not a primitive data type (i.e., it's a structure or unrecognized)
        else
        {
            // Use the supplied data type, unmodified, as the encoded type
            encodedType = dataType;
        }

        return encodedType;
    }

    /**************************************************************************
     * Get the ITOS limit name based on the supplied index value
     * 
     * @param index
     *            0 = redLow, 1 = yellowLow, 2 = yellowHigh, 3 = redHigh
     * 
     * @return ITOS limit name; returns blank if the index is invalid
     *************************************************************************/
    public String getITOSLimitName(int index)
    {
        String[] limitNames = new String[]
        {
         "redLow",
         "yellowLow",
         "yellowHigh",
         "redHigh",
         ""
        };

        // Check if the index is valid
        if (index < 0 || index > limitNames.length - 1)
        {
            // Set the index to the blank array member
            index = limitNames.length - 1;
        }

        return limitNames[index];
    }

    /**************************************************************************
     * Get the name of the parent structure table. Convenience method that
     * assumes the table type is a structure
     * 
     * @return Parent structure table's name; returns a blank if an instance of
     *         the structure table type doesn't exist
     *************************************************************************/
    public String getParentStructureTableName()
    {
        return getParentTableName(TYPE_STRUCTURE);
    }

    /**************************************************************************
     * Get the name of the parent command table. Convenience method that
     * assumes the table type is a command
     * 
     * @return Parent command table's name; returns a blank if an instance of
     *         the command table type doesn't exist
     *************************************************************************/
    public String getParentCommandTableName()
    {
        return getParentTableName(TYPE_COMMAND);
    }

    /**************************************************************************
     * Get the name of the parent table for the supplied table type
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @return Parent table's name for the type specified; returns a blank if
     *         an instance of the table type doesn't exist
     *************************************************************************/
    public String getParentTableName(String tableType)
    {
        String name = "";

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Store the table name
            name = tableInfo.getProtoVariableName();
        }

        return name;
    }

    /**************************************************************************
     * Get the array of parent structure table names. Convenience method that
     * assumes the table type is a structure
     * 
     * @return Array of parent structure table names; returns a blank if an
     *         instance of the structure table type doesn't exist
     *************************************************************************/
    public String[] getParentStructureTableNames()
    {
        return getParentTableNames(TYPE_STRUCTURE);
    }

    /**************************************************************************
     * Get the array of parent command table names. Convenience method that
     * assumes the table type is a command
     * 
     * @return Array of parent command table names; returns a blank if an
     *         instance of the command table type doesn't exist
     *************************************************************************/
    public String[] getParentCommandTableNames()
    {
        return getParentTableNames(TYPE_COMMAND);
    }

    /**************************************************************************
     * Get the array of the parent table names for the supplied table type
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @return Array of parent table names for the type specified; returns a
     *         blank if an instance of the table type doesn't exist
     *************************************************************************/
    public String[] getParentTableNames(String tableType)
    {
        List<String> name = new ArrayList<String>();

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Check that there is data for the specified table type
            if (tableInfo.getData().length != 0)
            {
                // Step through each row in the table data
                for (int row = 0; row < tableInfo.getData().length; row++)
                {
                    // Calculate the column index for the structure path
                    int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

                    // Split the table path into an array
                    String[] parts = tableInfo.getData()[row][pathColumn].split(Pattern.quote(","));

                    // Check if the list doesn't already contain the parent
                    // name
                    if (!name.contains(parts[0]))
                    {
                        // Add the parent name to the list
                        name.add(parts[0]);
                    }
                }
            }
        }

        return name.toArray(new String[0]);
    }

    /**************************************************************************
     * Get the number of rows of data in the structure table
     * 
     * @return Number of rows of data in the table of the type "structure";
     *         return -1 if an instance of the structure table type doesn't
     *         exist
     *************************************************************************/
    public int getStructureTableNumRows()
    {
        return getTableNumRows(TYPE_STRUCTURE);
    }

    /**************************************************************************
     * Get the number of rows of data in the command table
     * 
     * @return Number of rows of data in the table of the type "command";
     *         return -1 if an instance of the command table type doesn't exist
     *************************************************************************/
    public int getCommandTableNumRows()
    {
        return getTableNumRows(TYPE_COMMAND);
    }

    /**************************************************************************
     * Get the number of rows of data in the table
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @return Number of rows of data in the table of the type specified;
     *         return -1 if an instance of the table type doesn't exist
     *************************************************************************/
    public int getTableNumRows(String tableType)
    {
        int numRows = -1;

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Store the number of rows for the table
            numRows = tableInfo.getData().length;
        }

        return numRows;
    }

    /**************************************************************************
     * Get the structure table name to which the specified row's data belongs.
     * Convenience method that assumes the table type is "structure"
     * 
     * @param row
     *            table row index
     * 
     * @return Structure table name to which the current row's parameter
     *         belongs; returns a blank if an instance of the structure table
     *         type or the row doesn't exist
     *************************************************************************/
    public String getStructureTableNameByRow(int row)
    {
        return getTableNameByRow(TYPE_STRUCTURE, row);
    }

    /**************************************************************************
     * Get the command table name to which the specified row's data belongs.
     * Convenience method that assumes the table type is "command"
     * 
     * @param row
     *            table row index
     * 
     * @return Command table name to which the current row's parameter belongs;
     *         returns a blank if an instance of the command table type or the
     *         row doesn't exist
     *************************************************************************/
    public String getCommandTableNameByRow(int row)
    {
        return getTableNameByRow(TYPE_COMMAND, row);
    }

    /**************************************************************************
     * Get the table name for the type specified to which the specified row's
     * parameter belongs
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param row
     *            table row index
     * 
     * @return Table name to which the current row's parameter belongs; return
     *         a blank if an instance of the table type or the row doesn't
     *         exist
     *************************************************************************/
    public String getTableNameByRow(String tableType, int row)
    {
        String tableName = "";

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists and that the row index is valid
        if (tableInfo != null && row < tableInfo.getData().length)
        {
            // Store the structure name for the parameter at the specified row
            tableName = TableInformation.getPrototypeName(tableInfo.getData()[row][tableInfo.getData()[row].length
                                                                                   - PATH_COLUMN_DELTA]);
        }

        return tableName;
    }

    /**************************************************************************
     * Get array of all structure table names referenced in the table data.
     * Convenience method that specifies the table type as "structure"
     * 
     * @return Array of all structure table names; returns an empty array if an
     *         instance of the structure table type doesn't exist
     *************************************************************************/
    public String[] getStructureTableNames()
    {
        return getTableNames(TYPE_STRUCTURE);
    }

    /**************************************************************************
     * Get array of all command table names referenced in the table data.
     * Convenience method that specifies the table type as "command"
     * 
     * @return Array of all command table names; returns an empty array if an
     *         instance of the command table type doesn't exist
     *************************************************************************/
    public String[] getCommandTableNames()
    {
        return getTableNames(TYPE_COMMAND);
    }

    /**************************************************************************
     * Get array of all tables referenced in the table data of the specified
     * table type
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @return Array of all table names represented by the table type; returns
     *         an empty array if an instance of the table type doesn't exist
     *************************************************************************/
    public String[] getTableNames(String tableType)
    {
        List<String> names = new ArrayList<String>();

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that there is data for the specified table type
        if (tableInfo != null && tableInfo.getData().length != 0)
        {
            // Step through each row in the table
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Calculate the column index for the table path
                int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

                // Get the table's root name from the path
                String tableName = TableInformation.getPrototypeName(tableInfo.getData()[row][pathColumn]);

                // Check if the structure name hasn't been added to the list
                if (!names.contains(tableName))
                {
                    // Store the structure name
                    names.add(tableName);
                }
            }
        }

        return names.toArray(new String[0]);
    }

    /**************************************************************************
     * Get array of all tables referenced in the table data for all table types
     * 
     * @return Array of all table names referenced in the table data; empty
     *         array if no tables exists in the data
     *************************************************************************/
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

                    // Get the table's root name from the path
                    String tableName = TableInformation.getPrototypeName(tableInfo.getData()[row][pathColumn]);

                    // Check if the structure name hasn't been added to the
                    // list
                    if (!names.contains(tableName))
                    {
                        // Store the structure name
                        names.add(tableName);
                    }
                }
            }
        }

        return names.toArray(new String[0]);
    }

    /**************************************************************************
     * Get the table type name referenced in the specified row of the structure
     * table type data. Convenience method that specifies the table type as
     * "structure"
     * 
     * @return Type name referenced in the specified row of the structure table
     *         type data
     *************************************************************************/
    public String getStructureTypeNameByRow(int row)
    {
        return getTypeNameByRow(TYPE_STRUCTURE, row);
    }

    /**************************************************************************
     * Get the table type name referenced in the specified row of the command
     * table type data. Convenience method that specifies the table type as
     * "command"
     * 
     * @return Type name referenced in the specified row of the command table
     *         type data
     *************************************************************************/
    public String getCommandTypeNameByRow(int row)
    {
        return getTypeNameByRow(TYPE_COMMAND, row);
    }

    /**************************************************************************
     * Get the the table type name referenced in the specified row of the
     * specified table type data. The data for all structure (command) types
     * are combined. This method provides the means to retrieve the specific
     * table type to which the row data belongs based on its "generic" type
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param row
     *            row index
     * 
     * @return Type name referenced in the specified row of the specified table
     *         type data
     *************************************************************************/
    public String getTypeNameByRow(String tableType, int row)
    {
        String typeName = "";

        // Get the reference to the table information
        TableInformation tableInfo = getTableInformation(tableType);

        // Check if the row exists
        if (row < tableInfo.getData().length)
        {
            // Get the table type for the specified row
            typeName = tableInfo.getData()[row][tableInfo.getData()[row].length
                                                - TYPE_COLUMN_DELTA];
        }

        return typeName;
    }

    /**************************************************************************
     * Get an array containing the names of the structures in the order in
     * which they are referenced; that is, the structure array is arranged so
     * that a child structure appears in the array prior to the parent
     * structure(s) that reference it
     * 
     * @return Array containing the names of the structures in the order in
     *         which they are referenced; an empty array is returned if no
     *         structures tables are associated with the script
     *************************************************************************/
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
                                                                                      InputDataType.PRIM_AND_STRUCT);

                // Check that the data type column exists
                if (dataTypeColumnName != null)
                {
                    // Get the variable data type
                    String dataType = getStructureTableData(dataTypeColumnName, row);

                    // Check if this data type is one of the structures
                    if (dataType != null && structureNames.contains(dataType))
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

                // Step backwards through the list of all structures beginning
                // with the next to the last structure in the list
                for (int index = allStructs.size() - 2; index >= 0; index--)
                {
                    // Check if the ordered list doesn't already contain the
                    // structure name in order to eliminate duplicates
                    if (!orderedNames.contains(allStructs.get(index)))
                    {
                        // Add the structure name to the list
                        orderedNames.add(allStructs.get(index));
                    }
                }
            }

            // Step through the structure names
            for (String structureName : structureNames)
            {
                // Check if the structure isn't already in the list. This is
                // true for the top-level structure(s) without children
                if (!orderedNames.contains(structureName))
                {
                    // Add the structure to the list
                    orderedNames.add(structureName);
                }
            }
        }

        return orderedNames.toArray(new String[0]);
    }

    /**************************************************************************
     * Get a variable's full name which includes the variables in the structure
     * path separated by underscores. In case there are any array member
     * variable names in the full name, replace left square brackets with #
     * underscores and remove right square brackets (example: a[0],b[2] becomes
     * a_0_b_2)
     * 
     * @param row
     *            table row index
     * 
     * @return The variable's full path and name with each variable in the path
     *         separated by an underscore; returns a blank is the row is
     *         invalid
     *************************************************************************/
    public String getFullVariableName(int row)
    {
        return getFullVariableName(row, "_");
    }

    /**************************************************************************
     * Get a variable's full name which includes the variables in the structure
     * path separated by the specified separator character(s). In case there
     * are any array member variable names in the full name, replace left
     * square brackets with # underscores and remove right square brackets
     * (example: a[0],b[2] becomes a_0separatorb_2)
     * 
     * @param row
     *            table row index
     * 
     * @param separator
     *            character(s) to place between variables names
     * 
     * @return The variable's full path and name with each variable in the path
     *         separated by the specified separator character(s); returns a
     *         blank is the row is invalid
     *************************************************************************/
    public String getFullVariableName(int row, String separator)
    {
        String fullName = "";

        // Get the name of the variable name column
        String variableNameColumnName = tableTypeHandler.getColumnNameByInputType(getStructureTypeNameByRow(row),
                                                                                  InputDataType.VARIABLE);

        // Check that the variable name column exists
        if (variableNameColumnName != null)
        {
            // Get the variable path and variable name at the specified row
            String variablePath = getStructureTableVariablePathByRow(row);
            String variableName = macroHandler.getMacroExpansion(getStructureTableData(variableNameColumnName, row));

            // Check that the path and name are not blank
            if (!variablePath.isEmpty()
                && variableName != null
                && !variableName.isEmpty())
            {
                // Create the original and converted variable name lists if not
                // present
                createConvertedVariableNameList();

                // Create the full name by prepending the path to the variable
                // name
                fullName = variablePath + "," + variableName;

                int index = -1;

                // Check if the separator character is an underscore
                if (separator.equals("_"))
                {
                    // Get the index of the variable name from the list of
                    // original names
                    index = originalVariableNameList.indexOf(fullName);
                }

                // Check if the variable name was extracted from the list
                if (index != -1)
                {
                    // Get the converted variable name for this variable. This
                    // name has one or more underscores appended since it would
                    // otherwise duplicate another variable's name
                    fullName = convertedVariableNameList.get(index);
                }
                // The separator character isn't an underscore or the variable
                // name isn't in the list
                else
                {
                    // Replace the commas in the path, which separate each
                    // structure variable in the path, with underscores.
                    // Replace any left brackets with underscores and right
                    // brackets with blanks (in case there are any array
                    // members in the path)
                    fullName = fullName.replaceAll("[,\\[]",
                                                   separator).replaceAll("\\]", "");
                }
            }
        }

        return fullName;
    }

    /**************************************************************************
     * Create a pair of lists that show a variable's full name before and after
     * converting any commas and brackets to underscores. Check if duplicate
     * variable names result form the conversion; is a duplicate is found
     * append an underscore to the duplicate's name. Once all variable names
     * are processed trim the list to include only those variables that are
     * modified to prevent a duplicate. These lists are used by
     * getFullVariableName() to that it always returns a unique name
     *************************************************************************/
    private void createConvertedVariableNameList()
    {
        // Check if the lists aren't already created
        if (convertedVariableNameList == null)
        {
            originalVariableNameList = new ArrayList<String>();
            convertedVariableNameList = new ArrayList<String>();

            // Step through each structure table row
            for (int row = 0; row < getStructureTableNumRows(); row++)
            {
                // Get the name of the variable name column
                String variableNameColumnName = tableTypeHandler.getColumnNameByInputType(getStructureTypeNameByRow(row),
                                                                                          InputDataType.VARIABLE);
                // Check that the variable name column exists
                if (variableNameColumnName != null)
                {
                    // Get the variable path and name for this row
                    String variablePath = getStructureTableVariablePathByRow(row);
                    String variableName = macroHandler.getMacroExpansion(getStructureTableData(variableNameColumnName, row));

                    // Check that the path and name are not blank
                    if (!variablePath.isEmpty() && variableName != null && !variableName.isEmpty())
                    {
                        // Create the full name by prepending the path to the
                        // variable name
                        String fullName = variablePath + "," + variableName;

                        // Add the full variable name to the original variable
                        // name list
                        originalVariableNameList.add(fullName);

                        // Replace the commas in the path, which separate each
                        // structure variable in the path, with underscores.
                        // Replace any left brackets with underscores and right
                        // brackets with blanks (in case there are any array
                        // members in the path)
                        fullName = fullName.replaceAll("[,\\[]", "_").replaceAll("\\]", "");

                        // Compare the converted variable name to those already
                        // added to the list
                        while (convertedVariableNameList.contains(fullName))
                        {
                            // A matching name already exists; append an
                            // underscore to this variable's name
                            fullName += "_";
                        }

                        // Add the variable name to the converted variable name
                        // list
                        convertedVariableNameList.add(fullName);
                    }
                }
            }

            // Step through the converted variable name list
            for (int index = convertedVariableNameList.size() - 1; index >= 0; index--)
            {
                // Check if this variable isn't one that is modified
                if (!convertedVariableNameList.get(index).endsWith("_"))
                {
                    // Remove the variable from the list. This shortens the
                    // list and allows all other variables to have their full
                    // name built "on-the-fly"
                    originalVariableNameList.remove(index);
                    convertedVariableNameList.remove(index);
                }
            }
        }
    }

    /**************************************************************************
     * Get the path to which the specified row's data belongs
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param row
     *            table row index
     * 
     * @return The path to the current row's parameter; returns a blank if an
     *         instance of the table type doesn't exist. The path starts with
     *         the top-level table name. For structure tables the top-level
     *         name is followed by a comma and then the parent structure and
     *         variable name(s) that define(s) the table's path. Each parent
     *         and its associated variable name are separated by a period. Each
     *         parent/variable pair in the path is separated by a comma. The
     *         format is:
     * 
     *         top-level<,variable1.parent1<,variable2.parent2<...>>>
     *************************************************************************/
    public String getPathByRow(String tableType, int row)
    {
        return getTablePathByRow(tableType, row, TablePathType.VARIABLE_AND_PARENT);
    }

    /**************************************************************************
     * Get the structure path to which the specified row's data belongs,
     * showing only the top-level structure and variable names. This format is
     * used when referencing a structure table’s data fields
     * 
     * @param row
     *            table row index
     * 
     * @return The path to the current row's parameter; returns a blank if an
     *         instance of the table type doesn't exist. The path starts with
     *         the top-level table name. The top-level name is followed by a
     *         comma and then the variable name(s) that define(s) the table's
     *         path. Each variable in the path is separated by a comma. The
     *         format is:
     * 
     *         top-level<,variable1<,variable2<...>>>
     *************************************************************************/
    public String getStructureTableVariablePathByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.VARIABLE_ONLY);
    }

    /**************************************************************************
     * Get the structure path to which the specified row's data belongs,
     * formatted for use in an ITOS record statement
     * 
     * @param row
     *            table row index
     * 
     * @return The path to the current row's parameter formatted for use in an
     *         ITOS record statement; returns a blank if an instance of the
     *         table type doesn't exist. The path starts with the top-level
     *         table name. The top-level name is followed by a period and then
     *         the variable name(s) that define(s) the table's path. Each
     *         variable in the path is separated by an underscore. The format
     *         is:
     * 
     *         top-level<.variable1_parent1<.variable2_parent2<...>>>
     *************************************************************************/
    public String getStructureTableITOSPathByRow(int row)
    {
        return getTablePathByRow(TYPE_STRUCTURE, row, TablePathType.ITOS_RECORD);
    }

    /**************************************************************************
     * Get the variable path for the structure on the specified row in the
     * specified format
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param row
     *            table row index
     * 
     * @param pathType
     *            the format of the path to return:
     *            TablePathType.VARIABLE_AND_PARENT to return the path with the
     *            variable names and associated parent structure names,
     *            TablePathType.VARIABLE_ONLY to return the path with only the
     *            variable names (parent names removed), or
     *            TablePathType.ITOS_RECORD to return the path formatted for
     *            use in an ITOS recored file
     * 
     * @return The table path, for the structure table, to the current row's
     *         parameter; returns a blank if an instance of the structure table
     *         type doesn't exist
     *************************************************************************/
    private String getTablePathByRow(String tableType,
                                     int row,
                                     TablePathType pathType)
    {
        String structurePath = "";

        // Get the reference to the table information class for the specified
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check if table information exists for the specified type and if the
        // row is within the table data array size
        if (tableInfo != null && row < tableInfo.getData().length)
        {
            // Calculate the column index for the structure path
            int pathColumn = tableInfo.getData()[row].length - PATH_COLUMN_DELTA;

            // Check that the row index is valid
            if (tableInfo.getData().length != 0 && pathColumn > 0)
            {
                // Get the structure path for this row
                structurePath = tableInfo.getData()[row][pathColumn];

                switch (pathType)
                {
                    case VARIABLE_AND_PARENT:
                        break;

                    case VARIABLE_ONLY:
                        // TODO WHAT USES THIS FORMAT NOW?
                        // Remove the data types (parent structure names) from
                        // the path
                        structurePath = structurePath.replaceAll(",[^\\.]*\\.", ",");
                        break;

                    case ITOS_RECORD:
                        // Remove the data types (parent structure names) from
                        // the path and replace the commas with periods
                        structurePath = structurePath.replaceAll(",[^\\.]*\\.", ".");
                        break;
                }
            }
        }

        return structurePath;
    }

    /**************************************************************************
     * Determine if the specified structure is referenced by more than one
     * parent structure
     * 
     * @param structureName
     *            name of the structure to check
     * 
     * @return true if the specified structure is referenced by more than one
     *         table; false otherwise
     *************************************************************************/
    public boolean isStructureShared(String structureName)
    {
        boolean isShared = false;

        // Check if a structure name is provided
        if (structureName != null && !structureName.isEmpty())
        {
            // Check if no table tree is loaded
            if (tableTree == null)
            {
                // Build the table tree, including the primitive variables
                tableTree = new CcddTableTreeHandler(ccddMain,
                                                     TableTreeType.INSTANCE_WITH_PRIMITIVES,
                                                     parent);
            }

            // Get the list table tree paths for which the target structure is
            // a member
            List<Object[]> memberPaths = tableTree.getTableTreePathArray(structureName);

            // Check that the target structure appears in at least two paths
            if (memberPaths.size() > 1)
            {
                // Get the root table for the first path
                String target = tableTree.getVariableParentFromNodePath(memberPaths.get(0));

                // Step through the remaining paths
                for (int index = 1; index < memberPaths.size(); index++)
                {
                    // Check if the root table differs from the first path's
                    // root table
                    if (!target.equals(tableTree.getVariableParentFromNodePath(memberPaths.get(index))))
                    {
                        // Set the flag indicating that the target structure is
                        // referenced by more than one root table and stop
                        // searching
                        isShared = true;
                        break;
                    }
                }
            }
        }

        return isShared;
    }

    /**************************************************************************
     * Get an array containing the path to each parent structure and its
     * variables
     * 
     * @return Two-dimensional array containing the path for each structure
     *         variable. The parent structures are sorted alphabetically. The
     *         variables are displayed in the order of appearance within the
     *         structure (parent or child)
     * 
     *         TODO Should this return an array of strings (e.g., table path as
     *         string) instead of an array of arrays? AA2 scripts use this
     *         call, so must coordinate any changes
     *************************************************************************/
    public String[][] getVariablePaths()
    {
        List<Object[]> tableTreePaths = new ArrayList<Object[]>();

        // Check if no table tree is loaded
        if (tableTree == null)
        {
            // Build the table tree, including the primitive variables
            tableTree = new CcddTableTreeHandler(ccddMain,
                                                 TableTreeType.INSTANCE_WITH_PRIMITIVES,
                                                 parent);
        }

        // Get the variable paths from the table tree
        List<Object[]> pathsWithOther = tableTree.getTableTreePathArray(null);

        // Step through each variable path
        for (Object[] path : pathsWithOther)
        {
            // Check if the path contains a structure and/or variable
            if (path.length > tableTree.getTableNodeLevel())
            {
                // Get the last object in the path
                String var = path[path.length - 1].toString();

                // Get the index of the bit length separator, if present
                int index = var.indexOf(":");

                // Check if a bit length exists (i.e., this is a variable with
                // a bit length value appended)
                if (index != -1)
                {
                    // Remove the bit length and separator from the variable
                    // name
                    path[path.length - 1] = var.substring(0, index);
                }

                // Copy the portion of the path that references a structure
                // and/or variable
                tableTreePaths.add(Arrays.copyOfRange(path,
                                                      tableTree.getTableNodeLevel(),
                                                      path.length));
            }
        }

        return CcddUtilities.convertObjectToString(tableTreePaths.toArray(new Object[0][0]));
    }

    /**************************************************************************
     * Get the data field value for all tables that have the specified data
     * field
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Array of table names and the data field value; returns an empty
     *         array if the field name is invalid (i.e., no table has the data
     *         field)
     *************************************************************************/
    public String[][] getTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(null, fieldName);
    }

    /**************************************************************************
     * Get the data field value for all structure tables that have the
     * specified data field
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Array of structure table names and the data field value; returns
     *         an empty array if the field name is invalid (i.e., no structure
     *         table has the data field)
     *************************************************************************/
    public String[][] getStructureTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(TYPE_STRUCTURE, fieldName);
    }

    /**************************************************************************
     * Get the data field value for all command tables that have the specified
     * data field
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Array of command table names and the data field value; returns
     *         an empty array if the field name is invalid (i.e., no command
     *         table has the data field)
     *************************************************************************/
    public String[][] getCommandTableDataFieldValues(String fieldName)
    {
        return getTableDataFieldValues(TYPE_COMMAND, fieldName);
    }

    /**************************************************************************
     * Get the data field value for all tables of the specified type that have
     * the specified data field
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command". null to include tables of any type
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Array of table names of the specified type and the data field
     *         value; returns an empty array if the field name is invalid
     *         (i.e., no table has the data field)
     *************************************************************************/
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

    /**************************************************************************
     * Get the value for the specified table's specified data field
     * 
     * @param tableName
     *            name of the table, including the path if this table
     *            references a structure, to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field value; returns a null if the table type, table name,
     *         or field name is invalid
     *************************************************************************/
    public String getTableDataFieldValue(String tableName, String fieldName)
    {
        return getDataFieldValue(tableName, fieldName);
    }

    /**************************************************************************
     * Get the value for the specified group's specified data field
     * 
     * @param groupName
     *            name of the group to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field value; returns a null if the group name or field name
     *         is invalid
     *************************************************************************/
    public String getGroupDataFieldValue(String groupName, String fieldName)
    {
        return getDataFieldValue(CcddFieldHandler.getFieldGroupName(groupName),
                                 fieldName);
    }

    /**************************************************************************
     * Get the value for the specified table type's specified data field
     * 
     * @param typeName
     *            name of the table type to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field value; returns a null if the table type name or field
     *         name is invalid
     *************************************************************************/
    public String getTypeDataFieldValue(String typeName, String fieldName)
    {
        return getDataFieldValue(CcddFieldHandler.getFieldTypeName(typeName),
                                 fieldName);
    }

    /**************************************************************************
     * Get the contents of the data field for the specified table's specified
     * data field
     * 
     * @param ownerName
     *            name of the data field owner (table name, including the path
     *            if this table references a structure, group name, or table
     *            type name)
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field value; returns a null if the owner name or field name
     *         is invalid
     *************************************************************************/
    private String getDataFieldValue(String ownerName, String fieldName)
    {
        String fieldValue = null;

        // Get the reference to the data field information for the requested
        // owner and field names
        FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerName,
                                                                            fieldName);

        // Check if a field for this owner exists
        if (fieldInfo != null)
        {
            // Get the field value
            fieldValue = fieldInfo.getValue();
        }

        return fieldValue;
    }

    /**************************************************************************
     * Get the description for the specified table's specified data field
     * 
     * @param tableName
     *            name of the table, including the path if this table
     *            references a structure, to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field description; returns a blank if the table type, table
     *         name, or field name is invalid
     *************************************************************************/
    public String getTableDataFieldDescription(String tableName,
                                               String fieldName)
    {
        return getDataFieldDescription(tableName, fieldName);
    }

    /**************************************************************************
     * Get the description for the specified group's specified data field
     * 
     * @param groupName
     *            name of the group to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field description; returns a blank if the group name or
     *         field name is invalid
     *************************************************************************/
    public String getGroupDataFieldDescription(String groupName,
                                               String fieldName)
    {
        return getDataFieldDescription(CcddFieldHandler.getFieldGroupName(groupName),
                                       fieldName);
    }

    /**************************************************************************
     * Get the description for the specified table type's specified data field
     * 
     * @param typeName
     *            name of the table type to which the field is a member
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field description; returns a blank if the table type name
     *         or field name is invalid
     *************************************************************************/
    public String getTypeDataFieldDescription(String typeName, String fieldName)
    {
        return getDataFieldDescription(CcddFieldHandler.getFieldTypeName(typeName),
                                       fieldName);
    }

    /**************************************************************************
     * Get the description of the data field for the specified owner's
     * specified data field
     * 
     * @param ownerName
     *            name of the data field owner (table name, including the path
     *            if this table references a structure, group name, or table
     *            type name)
     * 
     * @param fieldName
     *            data field name
     * 
     * @return Data field description; returns a blank if the owner name or
     *         field name is invalid
     *************************************************************************/
    private String getDataFieldDescription(String ownerName, String fieldName)
    {
        String fieldDescription = "";

        // Get the reference to the data field information for the requested
        // owner and field names
        FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(ownerName,
                                                                            fieldName);

        // Check if a field for this owner exists
        if (fieldInfo != null)
        {
            // Get the field description
            fieldDescription = fieldInfo.getDescription();
        }

        return fieldDescription;
    }

    /**************************************************************************
     * Get the structure table data at the row and column indicated, with any
     * macro replaced by its corresponding value. The column is specified by
     * name. Convenience method that assumes the table type is "structure"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified structure table's array at the row and
     *         column name provided, with any macro replaced by its
     *         corresponding value; returns null if an instance of the
     *         structure table type doesn't exist
     *************************************************************************/
    public String getStructureTableData(String columnName, int row)
    {
        return getTableData(TYPE_STRUCTURE, columnName, row);
    }

    /**************************************************************************
     * Get the command table data at the row and column indicated, with any
     * macro replaced by its corresponding value. The column is specified by
     * name. Convenience method that assumes the table type is "command"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified command table's array at the row and
     *         column name provided, with any macro replaced by its
     *         corresponding value; returns null if an instance of the command
     *         table type doesn't exist
     *************************************************************************/
    public String getCommandTableData(String columnName, int row)
    {
        return getTableData(TYPE_COMMAND, columnName, row);
    }

    /**************************************************************************
     * Get the data at the row and column indicated, with any macro replaced by
     * its corresponding value, for the table type specified. The column is
     * specified by name
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified table's array at the row and column
     *         name provided, with any macro replaced by its corresponding
     *         value; returns null if an instance of the table type, the column
     *         name, or the row doesn't exist
     *************************************************************************/
    public String getTableData(String tableType, String columnName, int row)
    {
        return getTableData(tableType, columnName, row, true);
    }

    /**************************************************************************
     * Get the structure table data at the row and column indicated, with any
     * macro name(s) left in place. The column is specified by name.
     * Convenience method that assumes the table type is "structure"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified structure table's array at the row and
     *         column name provided, with any macro name(s) left in place;
     *         returns null if an instance of the structure table type doesn't
     *         exist
     *************************************************************************/
    public String getStructureTableDataWithMacros(String columnName, int row)
    {
        return getTableDataWithMacros(TYPE_STRUCTURE, columnName, row);
    }

    /**************************************************************************
     * Get the command table data at the row and column indicated, with any
     * macro name(s) left in place. The column is specified by name.
     * Convenience method that assumes the table type is "command"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified command table's array at the row and
     *         column name provided, with any macro name(s) left in place;
     *         returns null if an instance of the command table type doesn't
     *         exist
     *************************************************************************/
    public String getCommandTableDataWithMacros(String columnName, int row)
    {
        return getTableDataWithMacros(TYPE_COMMAND, columnName, row);
    }

    /**************************************************************************
     * Get the data at the row and column indicated, with any macro name(s)
     * left in place, for the table type specified. The column is specified by
     * name
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @return Contents of the specified table's array at the row and column
     *         name provided, with any macro name(s) left in place; returns
     *         null if an instance of the table type, the column name, or the
     *         row doesn't exist
     *************************************************************************/
    public String getTableDataWithMacros(String tableType, String columnName, int row)
    {
        return getTableData(tableType, columnName, row, false);
    }

    /**************************************************************************
     * Get the data at the row and column indicated for the table type
     * specified. The column is specified by name. Macro expansion is
     * controlled by the input flag
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @param row
     *            row index
     * 
     * @param expandMacros
     *            true to replace any macros with their corresponding value;
     *            false to return the data with any macro names in place
     * 
     * @return Contents of the specified table's array at the row and column
     *         name provided; returns null if an instance of the table type,
     *         the column name, or the row doesn't exist
     *************************************************************************/
    private String getTableData(String tableType,
                                String columnName,
                                int row,
                                boolean expandMacros)
    {
        String tableData = null;

        // Get the reference to the table information class for the requested
        // table type
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
                // Store the contents of the table at the specified row and
                // column
                tableData = tableInfo.getData()[row][column];

                // Check if any macros should be expanded
                if (expandMacros)
                {
                    // Expand any macros in the data
                    tableData = macroHandler.getMacroExpansion(tableData);
                }
            }
        }

        return tableData;
    }

    /**************************************************************************
     * Get the data from the specified "Structure" table in the specified
     * column for the row with the specified variable name, with any macro name
     * replaced by its corresponding value. Convenience method that assumes the
     * table type is "Structure" and the variable name column is
     * "Variable Name"
     * 
     * @param tablePath
     *            full table path, which includes the parent table name and the
     *            data type + variable name pairs
     * 
     * @param variableName
     *            variable name
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @return Contents of the table defined by the table path, variable name,
     *         and column name specified, with any macro replaced by its
     *         corresponding value; returns null if an instance of the table
     *         type, the column name, or the variable name doesn't exist
     *************************************************************************/
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

    /**************************************************************************
     * Get the data from the table in the specified column for the row in the
     * matching column name that contains the matching name, with any macro
     * name replaced by its corresponding value
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param tablePath
     *            full table path
     * 
     * @param matchColumnName
     *            name of the column containing that matching name (case
     *            insensitive)
     * 
     * @param matchName
     *            text to match in the matching column - this determines the
     *            row. The first row in the matching column that matches the
     *            matching name determines the row used to retrieve the data
     *            value
     * 
     * @param dataColumnName
     *            name of the column from which to retrieve the data value
     *            (case insensitive)
     * 
     * @return Contents of the table defined by the table type, table path,
     *         matching column name, matching name, and data column name
     *         specified, with any macro replaced by its corresponding value;
     *         returns null if an instance of the table type, the matching
     *         column, the data column, or the matching name doesn't exist
     *************************************************************************/
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

    /**************************************************************************
     * Get the data from the specified "Structure" table in the specified
     * column for the row with the specified variable name, with any macro
     * name(s) left in place. Convenience method that assumes the table type is
     * "Structure" and the variable name column is "Variable Name"
     * 
     * @param tablePath
     *            full table path, which includes the parent table name and the
     *            data type + variable name pairs
     * 
     * @param variableName
     *            variable name
     * 
     * @param columnName
     *            column name (case insensitive)
     * 
     * @return Contents of the table defined by the table path, variable name,
     *         and column name specified, with any macro name(s) left in place;
     *         returns null if an instance of the table type, the column name,
     *         or the variable name doesn't exist
     *************************************************************************/
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

    /**************************************************************************
     * Get the data from the table in the specified column for the row in the
     * matching column name that contains the matching name, with any macro
     * name(s) left in place
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param tablePath
     *            full table path
     * 
     * @param matchColumnName
     *            name of the column containing that matching name (case
     *            insensitive)
     * 
     * @param matchName
     *            text to match in the matching column - this determines the
     *            row. The first row in the matching column that matches the
     *            matching name determines the row used to retrieve the data
     *            value
     * 
     * @param dataColumnName
     *            name of the column from which to retrieve the data value
     *            (case insensitive)
     * 
     * @return Contents of the table defined by the table type, table path,
     *         matching column name, matching name, and data column name
     *         specified, with any macro name(s) left in place; returns null if
     *         an instance of the table type, the matching column, the data
     *         column, or the matching name doesn't exist
     *************************************************************************/
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

    /**************************************************************************
     * Get the data from the table in the specified column for the row in the
     * matching column name that contains the matching name. Macro expansion is
     * controlled by the input flag
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param tablePath
     *            full table path
     * 
     * @param matchColumnName
     *            name of the column containing that matching name (case
     *            insensitive)
     * 
     * @param matchName
     *            text to match in the matching column - this determines the
     *            row. The first row in the matching column that matches the
     *            matching name determines the row used to retrieve the data
     *            value
     * 
     * @param dataColumnName
     *            name of the column from which to retrieve the data value
     *            (case insensitive)
     * 
     * @param expandMacros
     *            true to replace any macros with their corresponding value;
     *            false to return the data with any macro names in place
     * 
     * @return Contents of the table defined by the table type, table path,
     *         matching column name, matching name, and data column name
     *         specified; returns null if an instance of the table type, the
     *         matching column, the data column, or the matching name doesn't
     *         exist
     *************************************************************************/
    private String getTableDataByColumnName(String tableType,
                                            String tablePath,
                                            String matchColumnName,
                                            String matchName,
                                            String dataColumnName,
                                            boolean expandMacros)
    {
        String tableData = null;

        // Get the reference to the table information class for the requested
        // table type
        TableInformation tableInfo = getTableInformation(tableType);

        // Check that the table type exists
        if (tableInfo != null)
        {
            // Step through the table data
            for (int row = 0; row < tableInfo.getData().length; row++)
            {
                // Get the type definition based on the table's specific type
                // name
                TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(getTypeNameByRow(tableType,
                                                                                              row));

                // Get the index for the matching and data columns
                int matchColumnIndex = typeDefn.getColumnIndexByUserName(matchColumnName);
                int dataColumnIndex = typeDefn.getColumnIndexByUserName(dataColumnName);

                // Check that the column names exist in the table
                if (matchColumnIndex != -1 && dataColumnIndex != -1)
                {
                    // Check if the table name matches the target table and the
                    // matching name matches that in the matching name column
                    if (tableInfo.getData()[row][tableInfo.getData()[row].length
                                                 - PATH_COLUMN_DELTA].equals(tablePath)
                        && tableInfo.getData()[row][matchColumnIndex].equals(matchName))
                    {
                        // Store the contents of the table at the specified row
                        // and column and stop searching
                        tableData = tableInfo.getData()[row][dataColumnIndex];

                        // Check if any macros should be expanded
                        if (expandMacros)
                        {
                            // Expand any macros in the data
                            tableData = macroHandler.getMacroExpansion(tableData);
                        }

                        break;
                    }
                }
            }
        }

        return tableData;
    }

    /**************************************************************************
     * Get the description of the table at the row indicated for the table type
     * specified
     * 
     * @param tableType
     *            table type. All structure table types are combined and are
     *            referenced by the type name "Structure", and all command
     *            table types are combined and are referenced by the type name
     *            "Command"
     * 
     * @param row
     *            row index
     * 
     * @return Description of the specified table at the row provided; returns
     *         a blank if an instance of the table type or the row doesn't
     *         exist
     *************************************************************************/
    public String getTableDescriptionByRow(String tableType, int row)
    {
        // Get the description for the table
        return dbTable.queryTableDescription(getPathByRow(tableType, row),
                                             ccddMain.getMainFrame());
    }

    /**************************************************************************
     * Get the array containing the macro names and their corresponding values
     * 
     * @return Array where each row contains a macro name and its corresponding
     *         value
     *************************************************************************/
    public String[][] getMacroDefinitions()
    {
        return macroHandler.getMacroData().toArray(new String[0][0]);
    }

    /**************************************************************************
     * Display an informational dialog showing the supplied text. The dialog’s
     * header and icon indicate that the text describes information useful to
     * the user; e.g., script status. The Okay button must be pressed before
     * the script can continue
     * 
     * @param text
     *            text to display in the dialog
     *************************************************************************/
    public void showInformationDialog(String text)
    {
        // Display the supplied text in an information dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>"
                                                      + text, "Script Message",
                                                  JOptionPane.INFORMATION_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**************************************************************************
     * Display a warning dialog showing the supplied text. The dialog’s header
     * and icon indicate that the text describes an warning condition. The Okay
     * button must be pressed before the script can continue
     * 
     * @param text
     *            text to display in the dialog
     *************************************************************************/
    public void showWarningDialog(String text)
    {
        // Display the supplied text in a warning dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>"
                                                      + text, "Script Warning",
                                                  JOptionPane.WARNING_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**************************************************************************
     * Display an error dialog showing the supplied text. The dialog’s header
     * and icon indicate that the text describes an error condition. The Okay
     * button must be pressed before the script can continue
     * 
     * @param text
     *            text to display in the dialog
     *************************************************************************/
    public void showErrorDialog(String text)
    {
        // Display the supplied text in an error dialog
        new CcddDialogHandler().showMessageDialog(parent,
                                                  "<html><b>"
                                                      + text, "Script Error",
                                                  JOptionPane.ERROR_MESSAGE,
                                                  DialogOption.OK_OPTION);
    }

    /**************************************************************************
     * Display a dialog for receiving text input. The user must select Okay to
     * accept the input, or Cancel to close the dialog without accepting the
     * input
     * 
     * @param labelText
     *            text to display in the dialog
     * 
     * @return The text entered in the dialog input field if the Okay button is
     *         pressed; returns null if no text or white space is entered, or
     *         if the Cancel button is pressed
     *************************************************************************/
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
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING,
                                                                   0,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel to hold the components of the dialog
        JPanel panel = new JPanel(new GridBagLayout());

        // Create the input label and field
        JLabel typeLabel = new JLabel(labelText);
        typeLabel.setFont(LABEL_FONT_BOLD);
        panel.add(typeLabel, gbc);

        JTextField typeField = new JTextField("", 15);
        typeField.setFont(LABEL_FONT_PLAIN);
        typeField.setEditable(true);
        typeField.setForeground(Color.BLACK);
        typeField.setBackground(Color.WHITE);
        typeField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                               Color.LIGHT_GRAY,
                                                                                               Color.GRAY),
                                                               BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        gbc.gridx++;
        panel.add(typeField, gbc);

        // Display the input dialog
        if (new CcddDialogHandler().showOptionsDialog(parent,
                                                      panel,
                                                      "Input",
                                                      DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Get the text from the input field and remove the leading and
            // trailing white space
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

    /**************************************************************************
     * Display a dialog containing radio buttons. The radio buttons are
     * mutually exclusive; only one can be selected at a time. The user must
     * press the Okay button to accept the radio button input, or Cancel to
     * close the dialog without accepting the input
     * 
     * @param label
     *            text to display above the radio buttons
     * 
     * @param buttonInfo
     *            array containing the text and optional descriptions for the
     *            radio buttons to display in the dialog
     * 
     * @return The text for the selected radio button if the Okay button is
     *         pressed; returns null if no radio button is selected or if the
     *         Cancel button is pressed
     *************************************************************************/
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
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING),
                                                        0,
                                                        0);

        // Create a panel to hold the components of the dialog
        JPanel panel = new JPanel(new GridBagLayout());

        // Create a panel containing a grid of radio buttons representing the
        // table types from which to choose and display the radio button dialog
        if (dialog.addRadioButtons(null, false, buttonInfo, null, label, panel, gbc)
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

    /**************************************************************************
     * Display a dialog containing one or more check boxes. The user must press
     * the Okay button to accept the check box input(s), or Cancel to close the
     * dialog without accepting the input
     * 
     * @param label
     *            text to display above the check boxes
     * 
     * @param boxInfo
     *            array containing the text and optional descriptions for the
     *            check boxes to display in the dialog
     * 
     * @return An array containing the status for the check box(es) if the Okay
     *         button is pressed; returns null if no check box information is
     *         supplied or if the Cancel button is pressed
     *************************************************************************/
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

            // Create a panel containing a grid of check boxes representing the
            // table types from which to choose and display the check box
            // dialog
            if (dialog.addCheckBoxes(null, boxInfo, null, label, panel)
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
                    // Check if the check box name is in the list of selected
                    // boxes
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

    /**************************************************************************
     * Perform a query on the currently open database
     * 
     * @param sqlCommand
     *            PostgreSQL-compatible database query statement
     * 
     * @return Two-dimensional array representing the rows and columns of data
     *         returned by the database query; returns null if the query
     *         produces an error, or an empty array if there are no results
     *************************************************************************/
    public String[][] getDatabaseQuery(String sqlCommand)
    {
        String[][] queryResults = null;

        try
        {
            // Execute the query command
            ResultSet infoData = dbCommand.executeDbQuery(sqlCommand,
                                                          ccddMain.getMainFrame());

            // Create a list to contain the row information
            List<String[]> tableData = new ArrayList<String[]>();

            // Step through each of the query results
            while (infoData.next())
            {
                // Create an array to contain the column values
                String[] columnValues = new String[infoData.getMetaData().getColumnCount()];

                // Step through each column in the row
                for (int column = 0; column < infoData.getMetaData().getColumnCount(); column++)
                {
                    // Add the column value to the array. Note that the first
                    // column's index in the database is 1, not 0
                    columnValues[column] = infoData.getString(column + 1);
                }

                // Add the row data to the list
                tableData.add(columnValues);
            }

            infoData.close();

            // Convert the list into an array
            queryResults = tableData.toArray(new String[0][0]);

            // Log that the query succeeded
            eventLog.logEvent(SUCCESS_MSG, "Script query completed");
        }
        catch (SQLException se)
        {
            // Inform the user that the query failed
            eventLog.logFailEvent(ccddMain.getMainFrame(),
                                  "Script query failed; cause '"
                                      + se.getMessage()
                                      + "'", "<html><b>Script query failed");
        }

        return queryResults;
    }

    /**************************************************************************
     * Divide the supplied enumeration string into the values and labels. The
     * enumeration value/label separator character and the enumerated pair
     * separator character are automatically determined. Any leading or
     * trailing white space characters are removed from each array member
     * 
     * @param enumeration
     *            enumeration in the format <enum value><enum value
     *            separator><enum label>[<enum value separator>...][<enum pair
     *            separator>...]
     * 
     * @return Two-dimensional array representing the enumeration parameters ;
     *         returns null if the input text is empty or the enumeration
     *         separator characters cannot be determined
     *************************************************************************/
    public String[][] parseEnumerationParameters(String enumeration)
    {
        String[][] pairs = null;

        // Get the character that separates the enumeration value from the
        // associated label
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
                pairs = getArrayFromString(enumeration,
                                           enumSeparator,
                                           pairSeparator);
            }
        }

        return pairs;
    }

    /**************************************************************************
     * Divide the supplied string into an array using the supplied separator
     * character or string, and trim any leading or trailing white space
     * characters from each array member
     * 
     * @param text
     *            string to separate into an array
     * 
     * @param columnSeparator
     *            character string to use to delineate the separation point(s)
     *            between columns. The separator is eliminated from the array
     *            members
     * 
     * @return Array representing the substrings in the supplied text after
     *         being parsed using the separator; returns null if the input text
     *         is empty
     *************************************************************************/
    public String[] getArrayFromString(String text,
                                       String columnSeparator)
    {
        return getArrayFromString(text, columnSeparator, null)[0];
    }

    /**************************************************************************
     * Divide the supplied string into a two-dimensional array (columns and
     * rows) using the supplied separator characters or strings, and trim any
     * leading or trailing white space characters from each array member
     * 
     * @param text
     *            string to separate into an array
     * 
     * @param columnSeparator
     *            character string to use to delineate the separation point(s)
     *            between columns. The separator is eliminated from the array
     *            members
     * 
     * @param rowSeparator
     *            character string to use to delineate the separation point(s)
     *            between rows. The separator is eliminated from the array
     *            members. Use null if only one row is supplied
     * 
     * @return Two-dimensional array representing the substrings in the
     *         supplied text after being parsed using the separator; returns
     *         null if the input text is empty
     *************************************************************************/
    public String[][] getArrayFromString(String text,
                                         String columnSeparator,
                                         String rowSeparator)
    {
        String[][] array = null;

        // Check if the supplied text string is not empty
        if (!text.isEmpty())
        {
            String[] rowArray;

            // Check if a row separator was provided
            if (rowSeparator != null)
            {
                // Split the text using the row separator, and remove leading
                // and trailing white space characters if present
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
                // Split the text using the column separator, and remove
                // leading and trailing white space characters if present
                array[row] = rowArray[row].split("\\s*[" + columnSeparator
                                                 + "]\\s*");
            }
        }

        return array;
    }

    /**************************************************************************
     * Open the specified file for writing. The PrintWriter object that is
     * returned is used by the file writing methods to specify the output file
     * 
     * @param outputFileName
     *            output file path + name
     * 
     * @return PrintWriter object; returns null if the file could not be opened
     *************************************************************************/
    public PrintWriter openOutputFile(String outputFileName)
    {
        return fileIOHandler.openOutputFile(outputFileName);
    }

    /**************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     * 
     * @param printWriter
     *            output file PrintWriter object obtained from the
     *            openOutputFile method
     * 
     * @param text
     *            text to write to the output file
     *************************************************************************/
    public void writeToFile(PrintWriter printWriter, String text)
    {
        fileIOHandler.writeToFile(printWriter, text);
    }

    /**************************************************************************
     * Write the supplied text to the specified output file PrintWriter object
     * and append a line feed character
     * 
     * @param printWriter
     *            output file PrintWriter object obtained from the
     *            openOutputFile method
     * 
     * @param text
     *            text to write to the output file
     *************************************************************************/
    public void writeToFileLn(PrintWriter printWriter, String text)
    {
        fileIOHandler.writeToFileLn(printWriter, text);
    }

    /**************************************************************************
     * Write the supplied formatted text in the indicated format to the
     * specified output file PrintWriter object
     * 
     * @param printWriter
     *            output file PrintWriter object obtained from the
     *            openOutputFile method
     * 
     * @param format
     *            print format string to write to the output file
     * 
     * @param args
     *            variable list of arguments referenced by the format
     *            specifiers in the format string
     *************************************************************************/
    public void writeToFileFormat(PrintWriter printWriter,
                                  String format,
                                  Object... args)
    {
        fileIOHandler.writeToFileFormat(printWriter, format, args);
    }

    /**************************************************************************
     * Close the specified output file
     * 
     * @param printWriter
     *            output file PrintWriter object
     *************************************************************************/
    public void closeFile(PrintWriter printWriter)
    {
        fileIOHandler.closeFile(printWriter);
    }

    /**************************************************************************
     * Get the description of the specified link
     * 
     * @param streamName
     *            data stream name
     * 
     * @param linkName
     *            link name
     * 
     * @return Link description; returns a blank if the data stream or link
     *         don't exist, or the link has no description
     *************************************************************************/
    public String getLinkDescription(String streamName, String linkName)
    {
        String description = "";

        // Get the rate information based on the supplied data stream name
        RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

        // Check if the rate information exists with this stream name
        if (rateInfo != null)
        {
            // Get the link description based on the rate column and link names
            description = linkHandler.getLinkDescription(rateInfo.getRateName(),
                                                         linkName);
        }

        return description;
    }

    /**************************************************************************
     * Return the sample rate for the specified link
     * 
     * @param streamName
     *            data stream name
     * 
     * @param linkName
     *            link name
     * 
     * @return Text representation of the sample rate, in samples per second,
     *         of the specified link. For rates equal to or faster than 1
     *         sample per second the string represents a whole number; for
     *         rates slower than 1 sample per second the string is in the form
     *         number of samples / number of seconds; returns a blank if the
     *         data stream or link don't exist
     *************************************************************************/
    public String getLinkRate(String streamName, String linkName)
    {
        String sampleRate = "";

        // Get the rate information based on the supplied data stream name
        RateInformation rateInfo = rateHandler.getRateInformationByStreamName(streamName);

        // Check if the rate information exists with this stream name
        if (rateInfo != null)
        {
            // Get the link sample rate based on the rate column and link names
            sampleRate = linkHandler.getLinkRate(rateInfo.getRateName(),
                                                 linkName);
        }

        return sampleRate;
    }

    /**************************************************************************
     * Get the array of data stream and link names to which the specified
     * variable belongs
     * 
     * @param variableName
     *            variable path and name
     * 
     * @return Array containing the data stream and link names to which the
     *         specified variable is a member; returns an empty array if the
     *         variable does not belong to a link
     *************************************************************************/
    public String[][] getVariableLinks(String variableName)
    {
        return linkHandler.getVariableLinks(variableName, true);
    }

    /**************************************************************************
     * Get the byte offset of the specified variable relative to its parent
     * structure. The variable's path, including parent structure and variable
     * name, is used to verify that the specified target has been located;
     * i.e., not another variable with the same name
     * 
     * @param path
     *            a comma separated string of the parent structure and each
     *            data type and variable name of each variable in the current
     *            search path
     * 
     * @return The byte offset to the target variable relative to its parent
     *         structure; returns -1 if the parent-variable path combination is
     *         invalid
     *************************************************************************/
    public int getVariableOffset(String path)
    {
        return linkHandler.getVariableOffset(path);
    }

    /**************************************************************************
     * Get the array representing the CFS application name data field values
     * associated with the link entries. Each application name appears only
     * once in the array
     * 
     * @param dataFieldName
     *            name of the application name data field
     * 
     * @return Array containing the contents of the specified CFS application
     *         name data field associated with each of the tables referenced by
     *         the link entries
     *************************************************************************/
    public String[] getLinkApplicationNames(String dataFieldName)
    {
        return linkHandler.getApplicationNames(tableInformation[0].getFieldHandler(),
                                               dataFieldName);
    }

    /**************************************************************************
     * Get the array of group names
     * 
     * @param applicationOnly
     *            true if only those groups that represent a CFS application
     *            should be returned
     * 
     * @return Array of group names (application groups only if the input flag
     *         is true); empty array if no groups are defined
     *************************************************************************/
    public String[] getGroupNames(boolean applicationOnly)
    {
        return groupHandler.getGroupNames(applicationOnly);
    }

    /**************************************************************************
     * Get the description for the specified group
     * 
     * @param groupName
     *            group name
     * 
     * @return Description for the specified group; blank if the group has no
     *         description or the group doesn't exist
     *************************************************************************/
    public String getGroupDescription(String groupName)
    {
        return groupHandler.getGroupDescription(groupName);
    }

    /**************************************************************************
     * Get the copy table column names
     * 
     * @return Array containing the copy table column names
     *************************************************************************/
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

    /**************************************************************************
     * Get the copy table for the messages of the specified data stream
     * 
     * @param streamName
     *            data stream name
     * 
     * @param headerSize
     *            size of the message header in bytes. For example, the CCSDS
     *            header size is 12
     * 
     * @param messageIDNameField
     *            name of the message ID name data field (e.g., 'Message ID
     *            name')
     * 
     * @param optimize
     *            true to combine memory copy calls for consecutive variables
     *            in the copy table
     * 
     * @return Array containing the copy table entries; returns blank if there
     *         are no entries for the specified data stream or if data stream
     *         name is invalid
     *************************************************************************/
    public String[][] getCopyTableEntries(String streamName,
                                          int headerSize,
                                          String messageIDNameField,
                                          boolean optimize)
    {
        String[][] entries = new String[0][0];

        // Check if the copy table handler doesn't exist
        if (copyHandler == null)
        {
            // Create the copy table handler
            copyHandler = new CcddCopyTableHandler(ccddMain);
        }

        // Check if this is a valid stream name
        if (rateHandler.getRateInformationIndexByStreamName(streamName) != -1)
        {
            // Create the copy table
            entries = copyHandler.createCopyTable(fieldHandler,
                                                  linkHandler,
                                                  streamName,
                                                  headerSize,
                                                  messageIDNameField,
                                                  optimize);
        }

        return entries;
    }

    /**************************************************************************
     * Get the messages ID names and their corresponding ID values for the
     * specified data stream
     * 
     * @param streamName
     *            data stream name
     * 
     * @return Array containing the message ID names and ID values; returns
     *         blank if there are no entries for the specified data stream or
     *         if data stream name is invalid
     *************************************************************************/
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
            messageIDs = copyHandler.getTelemetryMessageIDs(streamName);
        }

        return messageIDs;
    }

    /**************************************************************************
     * Get a string array containing all of the data stream names in the
     * project
     * 
     * @return Array containing the unique data stream names
     *************************************************************************/
    public String[] getDataStreamNames()
    {
        return rateHandler.getDataStreamNames();
    }

    /**************************************************************************
     * Get an array containing the application names in the project
     * 
     * @return Array of application names; the list is empty if no application
     *         names exist
     *************************************************************************/
    public String[] getApplicationNames()
    {
        return groupHandler.getGroupNames(true);
    }

    // TODO Remaining methods are for Nolan's application scheduler...
    /**************************************************************************
     * Get the list of defines for the scheduler table
     * 
     * @return Two-dimensional array containing the defines list
     *************************************************************************/
    public String[][] getDefinesList()
    {
        return schTable.getSchedulerTableDefines();
    }

    /**************************************************************************
     * Get the application scheduler groups TODO IS THIS USED?
     * 
     * @return Array containing the application scheduler groups
     *************************************************************************/
    public String[] getApplicationSchedulerGroups()
    {
        return schTable.getApplicationSchedulerGroups();
    }

    /**************************************************************************
     * Create the application scheduler table
     *************************************************************************/
    public void createApplicationSchedulerTable()
    {
        schTable.createApplicationSchedulerTable();
    }

    /**************************************************************************
     * Get the list of defines for the scheduler table TODO IS THUIS CORRECT?
     * 
     * @param entry
     *            TODO
     * 
     * @return Array containing the defines list
     *************************************************************************/
    public String[][] getApplicationSchedulerEntry(int entry)
    {
        return schTable.getApplicationScheduleTableIndex(entry);
    }

    /**************************************************************************
     * Get the application scheduler command table
     * 
     * @return Array containing the command table information
     *************************************************************************/
    public String[] getApplicationCommandTable()
    {
        // Initialize the scheduler table. This is the first function called by
        // the script
        schTable = new CcddApplicationSchedulerTable(ccddMain);

        return schTable.createSchedulerMessageTable();
    }

    /**************************************************************************
     * Get the number of time slots for the scheduler table
     * 
     * @return Number of time slots for the command table
     *************************************************************************/
    public int getNumberOfSlots()
    {
        return ccddMain.getApplicationParameterHandler().getNumberOfSlots();
    }

    // end TODO

    // TODO
    /**************************************************************************
     * *** FOR TESTING ***
     * 
     * Display the table information for each associated table type
     *************************************************************************/
    public void showData()
    {
        if (tableInformation == null
            || tableInformation[0].getType().isEmpty())
        {
            System.out.println("No table data associated with this script");
        }
        else
        {
            for (TableInformation tableInfo : tableInformation)
            {
                System.out.println("Table data for type '"
                                   + tableInfo.getType()
                                   + "'");

                String[][] data = tableInfo.getData();

                for (int i = 0; i < data.length; i++)
                {
                    System.out.println(i + ": " + Arrays.toString(data[i]));
                }

                System.out.println("");
            }
        }
    }
}
