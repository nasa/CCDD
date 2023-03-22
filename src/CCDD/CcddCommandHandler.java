/**************************************************************************************************
 * /** \file CcddCommandHandler.java
 *
 * \author Kevin McCluney Bryan Willis
 *
 * \brief Class for building a list of project commands (including the command’s name, code,
 * command table, and argument variable names). This is used for populating the the Command
 * reference input type selection item list and by the script data access methods.
 *
 * \copyright MSC-26167-1, "Core Flight System (cFS) Command and Data Dictionary (CCDD)"
 *
 * Copyright (c) 2016-2021 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. All Rights Reserved.
 *
 * This software is governed by the NASA Open Source Agreement (NOSA) License and may be used,
 * distributed and modified only pursuant to the terms of that agreement. See the License for the
 * specific language governing permissions and limitations under the License at
 * https://software.nasa.gov/.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * expressed or implied.
 *
 * \par Limitations, Assumptions, External Events and Notes: - TBD
 *
 **************************************************************************************************/
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
        private final String commandArgument;

        /******************************************************************************************
         * Command information class constructor
         *
         * @param table           Command table in which the command is defined
         *
         * @param commandName     Command name
         *
         * @param commandCode     Command code
         *
         * @param commandArgument Command argument variable
         *****************************************************************************************/
        CommandInformation(String table,
                           String commandName,
                           String commandCode,
                           String commandArgument)
        {
            this.table = table;
            this.commandName = commandName;
            this.commandCode = commandCode;
            this.commandArgument = commandArgument;
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
         * Get the command argument variable
         *
         * @return Command argument variable
         *****************************************************************************************/
        protected String getCommandArgument()
        {
            return commandArgument;
        }
    }

    /**********************************************************************************************
     * Command handler class constructor
     *
     * @param ccddMain Main class
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
     * @param commandName Command name
     *
     * @param commandCode Command code
     *
     * @param commandArg  Command argument structure path
     *
     * @param table       Command table to which the command belongs
     *
     * @return Command reference string
     *********************************************************************************************/
    protected String buildCommandReference(String commandName,
                                           String commandCode,
                                           String commandArg,
                                           String table)
    {
        return commandName
               + " (code: "
               + commandCode
               + ", owner: "
               + table + ", arg: "
               + getCommandArgumentVariables(commandArg, ", ")
               + ")";
    }

    /**********************************************************************************************
     * Get the argument variable names for the specified command argument structure reference
     *
     * @param argumentStructRef Command argument structure reference path
     *
     * @param separator         Character(s) used to separate each command argument variable name
     *
     * @return The argument variable names for the specified command argument structure reference
     *         separated by the supplied separator character(s); and empty string if the structure
     *         has no variables
     *********************************************************************************************/
    protected String getCommandArgumentVariables(String argumentStructRef, String separator)
    {
        String commandArgs = "";

        // Get the path to the command's argument structure
        String commandArgumentStart = argumentStructRef + ",";

        // Step through each variable
        for (String argVariable : ccddMain.getVariableHandler().getStructureAndVariablePaths())
        {
            // Get the index in the variable path to the period preceding the variable name
            int variableStart = argVariable.lastIndexOf(".");

            // Check if this is a variable belonging to the command's argument structure (but not a
            // child of a child structure)
            if (argVariable.startsWith(commandArgumentStart))
            {
                // Ad the argument variable to the string
                commandArgs += argVariable.substring(variableStart + 1) + separator;
            }
        }

        return CcddUtilities.removeTrailer(commandArgs, separator);
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
                                                      cmdInfo.getCommandArgument(),
                                                      cmdInfo.getTable()));
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
        StringBuilder command = new StringBuilder();
        StringBuilder columnNames = new StringBuilder();
        TypeDefinition typeDefn;

        // Create the query command to obtain the command information from the project database
        command.append("SELECT * FROM (");

        // Step through each data table
        for (String[] namesAndType : dbTable.queryTableAndTypeList(ccddMain.getMainFrame()))
        {
            // Get the table's type definition
            typeDefn = tableTypeHandler.getTypeDefinition(namesAndType[2]);

            // Check if the table type represents a command
            if (typeDefn != null && typeDefn.isCommand())
            {
                // Set the flag to indicate the project has a command table
                hasCmdTable = true;

                // Begin building the column name string
                columnNames.setLength(0);
                columnNames.append(typeDefn.getColumnNamesDatabaseQuoted()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_NAME)]);
                columnNames.append(", ");
                columnNames.append(typeDefn.getColumnNamesDatabaseQuoted()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_CODE)]);
                columnNames.append(", ");
                columnNames.append(typeDefn.getColumnNamesDatabaseQuoted()[typeDefn.getColumnIndexByInputType(DefaultInputType.COMMAND_ARGUMENT)]);

                // Append the command to obtain the specified column information from the command
                // table
                command.append("(SELECT '"
                               + namesAndType[0]
                               + "' AS command_table, "
                               + columnNames
                               + " FROM "
                               + namesAndType[1]
                               + ") UNION ALL ");
            }
        }

        // Check if the project has a command table
        if (hasCmdTable)
        {
            // Clean up the command
            command.setLength(command.length() - 11); // removes the last " UNION ALL "
            command.append(") AS cmds;");

            try
            {
                // Perform the query to obtain the command information for all commands defined in
                // the project
                ResultSet commands = ccddMain.getDbCommandHandler().executeDbQuery(command, ccddMain.getMainFrame());

                // Check if a comment exists
                while (commands.next())
                {
                    // Add the command's information to the list
                    commandInformation.add(new CommandInformation(commands.getString(1),
                                                                  commands.getString(2),
                                                                  commands.getString(3),
                                                                  commands.getString(4)));
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
                                                                    : (Integer.decode(cmd1.getCommandCode()) > Integer.decode(cmd2.getCommandCode()) ? 1 : -1);

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
            dbTable.updateInputTypeColumns(null, ccddMain.getMainFrame());
        }
    }
}
