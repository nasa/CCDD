/**
 * CFS Command and Data Dictionary command handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary command handler class
 *************************************************************************************************/
public class CcddCommandHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;

    // List containing each command's information
    private List<CommandInformation> commandInformation;

    /**********************************************************************************************
     * Command information class
     *********************************************************************************************/
    public class CommandInformation
    {
        private final String table;
        private final String commandName;
        private final String commandCode;
        private final String arguments;
        private final int numArgs;

        /******************************************************************************************
         * Command information class constructor
         *
         * @param table
         *            command table in which the command is defined
         *
         * @param commandName
         *            command name
         *
         * @param commandCode
         *            command code
         *
         * @param arguments
         *            names of the command's arguments, separated by line feeds
         *****************************************************************************************/
        CommandInformation(String table, String commandName, String commandCode, String arguments)
        {
            this.table = table;
            this.commandName = commandName;
            this.commandCode = commandCode;
            this.arguments = arguments;

            // Store the number of command arguments
            numArgs = arguments.isEmpty()
                                          ? 0
                                          : arguments.split("\\n").length;
        }

        /******************************************************************************************
         * Get the command table in which the command is defined
         *
         * @return Command table in which the command is defined
         *****************************************************************************************/
        protected String getTable()
        {
            return table;
        }

        /******************************************************************************************
         * Get the command name
         *
         * @return Command name
         *****************************************************************************************/
        protected String getCommandName()
        {
            return commandName;
        }

        /******************************************************************************************
         * Get the command code
         *
         * @return Command code
         *****************************************************************************************/
        protected String getCommandCode()
        {
            return commandCode;
        }

        /******************************************************************************************
         * Get the command arguments
         *
         * @return Command arguments
         *****************************************************************************************/
        protected String getArguments()
        {
            return arguments;
        }

        /******************************************************************************************
         * Get the number of arguments that are valid for this command
         *
         * @return The number of arguments that are valid for this command
         *****************************************************************************************/
        protected int getNumArgs()
        {
            return numArgs;
        }
    }

    /**********************************************************************************************
     * Command handler class constructor
     *
     * @param ccddMain
     *            main class
     *********************************************************************************************/
    CcddCommandHandler(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;

        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
    }

    /**********************************************************************************************
     * Get the list of command information
     *
     * @return List of command information; returns an empty list if no commands exist
     *********************************************************************************************/
    protected List<CommandInformation> getCommandInformation()
    {
        return commandInformation;
    }

    /**********************************************************************************************
     * Build the command reference string
     *
     * @param commandName
     *            command name
     *
     * @param commandCode
     *            command code
     *
     * @param table
     *            command table to which the command belongs
     *
     * @param numArgs
     *            number of arguments that are valid for this command
     *
     * @return Command reference string
     *********************************************************************************************/
    protected String buildCommandReference(String commandName,
                                           String commandCode,
                                           String table,
                                           int numArgs)
    {
        return commandName
               + " (code: "
               + commandCode
               + ", owner: "
               + table
               + ", args: "
               + numArgs
               + ")";
    }

    /**********************************************************************************************
     * Get the list of commands
     *
     * @return List of commands; returns an empty list if no commands exist
     *********************************************************************************************/
    protected List<String> getAllCommands()
    {
        List<String> allCommandNames = new ArrayList<String>();

        // Step through each command's information
        for (CommandInformation cmdInfo : commandInformation)
        {
            // Add the command information to the list
            allCommandNames.add(buildCommandReference(cmdInfo.getCommandName(),
                                                      cmdInfo.getCommandCode(),
                                                      cmdInfo.getTable(),
                                                      cmdInfo.getNumArgs()));
        }

        return allCommandNames;
    }

    /**********************************************************************************************
     * Build the list of command information for every command defined in the project database. A
     * single query is constructed to obtain the command information in order to reduce the number
     * of transactions with the database (as opposed to using loadTableInformation() for each
     * command table)
     *********************************************************************************************/
    protected void buildCommandList()
    {
        boolean hasCmdTable = false;
        commandInformation = new ArrayList<CommandInformation>();

        // Create the query command to obtain the command information from the project database
        String command = "SELECT * FROM (";

        // Step through each data table
        for (String[] namesAndType : dbTable.queryTableAndTypeList(ccddMain.getMainFrame()))
        {
            // Get the table's type definition
            TypeDefinition typeDefn = tableTypeHandler.getTypeDefinition(namesAndType[2]);

            // Check if the table type represents a command
            if (typeDefn != null && typeDefn.isCommand())
            {
                // Set the flag to indicate the project has a command table
                hasCmdTable = true;

                // Get the column indices for the command argument names
                List<Integer> argNameColumns = typeDefn.getColumnIndicesByInputType(DefaultInputType.ARGUMENT_NAME);

                // Begin building the column name string
                String columnNames = typeDefn.getColumnNamesDatabaseQuoted()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME)]
                                     + ", "
                                     + typeDefn.getColumnNamesDatabaseQuoted()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE)]
                                     + ", ";

                // Check if the table type has any argument name columns
                if (argNameColumns.size() != 0)
                {
                    // Step through each argument name column
                    for (Integer argNameCol : argNameColumns)
                    {
                        // Append the argument name column name to the column name string
                        columnNames += typeDefn.getColumnNamesDatabaseQuoted()[argNameCol]
                                       + " || E'\\n' || ";
                    }

                    // Clean up the column name string
                    columnNames = CcddUtilities.removeTrailer(columnNames, "|| E'\\n' || ")
                                  + "AS arg_names";
                }
                // The table type has no argument name columns
                else
                {
                    // Insert a blank for the argument name
                    columnNames += "''";
                }

                // Append the command to obtain the specified column information from the command
                // table
                command += "(SELECT '"
                           + namesAndType[0]
                           + "' AS command_table, "
                           + columnNames
                           + " FROM "
                           + namesAndType[1]
                           + ") UNION ALL ";
            }
        }

        // Check if the project has a command table
        if (hasCmdTable)
        {
            // Clean up the command
            command = CcddUtilities.removeTrailer(command, " UNION ALL ") + ") AS cmds;";

            try
            {
                // Perform the query to obtain the command information for all commands defined in
                // the project
                ResultSet commands = ccddMain.getDbCommandHandler().executeDbQuery(command,
                                                                                   ccddMain.getMainFrame());

                // Check if a comment exists
                while (commands.next())
                {
                    // Add the command's information to the list
                    commandInformation.add(new CommandInformation(commands.getString(1),
                                                                  commands.getString(2),
                                                                  commands.getString(3),
                                                                  commands.getString(4)
                                                                          .replaceAll("^\\s|\\s$",
                                                                                      "")));
                }
            }
            catch (SQLException se)
            {
                // Inform the user an error occurred obtaining the command information
                new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(),
                                                          "<html><b>Cannot obtain command information",
                                                          "Query Fail",
                                                          JOptionPane.ERROR_MESSAGE,
                                                          DialogOption.OK_OPTION);
            }

            // Sort the command information
            Collections.sort(commandInformation, new Comparator<CommandInformation>()
            {
                /**********************************************************************************
                 * Sort the command information by command name; if the same then by command code;
                 * if the same then by table name
                 *********************************************************************************/
                @Override
                public int compare(CommandInformation cmd1, CommandInformation cmd2)
                {
                    // Compare the command names
                    int result = cmd1.getCommandName().compareToIgnoreCase(cmd2.getCommandName());

                    // Check if the table names are the same
                    if (result == 0)
                    {
                        // Compare the command codes, converting them to integer values first
                        // (unless blank)
                        result = cmd1.getCommandCode().isEmpty()
                                 || cmd2.getCommandCode().isEmpty()
                                                                    ? cmd1.getCommandCode().compareToIgnoreCase(cmd2.getCommandCode())
                                                                    : (Integer.decode(cmd1.getCommandCode()) > Integer.decode(cmd2.getCommandCode())
                                                                                                                                                     ? 1
                                                                                                                                                     : -1);

                        // Check if the command codes are the same
                        if (result == 0)
                        {
                            // Compare the table names
                            result = cmd1.getTable().compareToIgnoreCase(cmd2.getTable());
                        }
                    }

                    return result;
                }
            });

            // Add the command information to the command references input type and refresh any
            // open editors
            ccddMain.getInputTypeHandler().updateCommandReferences();
            ccddMain.getDbTableCommandHandler().updateInputTypeColumns(null, ccddMain.getMainFrame());
        }
    }
}
