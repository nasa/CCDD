/**
 * CFS Command & Data Dictionary command line argument handler. Copyright 2017
 * United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DEFAULT_DATABASE;
import static CCDD.CcddConstants.LIST_TABLE_DESC_SEPARATOR;
import static CCDD.CcddConstants.LIST_TABLE_SEPARATOR;
import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddConstants.CommandLineType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.ModifiablePathInfo;

/******************************************************************************
 * CFS Command & Data Dictionary command line argument handler class
 *****************************************************************************/
public class CcddCommandLineHandler
{
    // Class reference
    private final CcddMain ccddMain;

    // Text describing the command line error
    private String errorMessage;

    // Array containing the command line arguments provided by the user
    private final String[] args;

    // List containing the valid command line argument handlers
    private final List<CommandHandler> argument;

    // Flag that indicates the application should exit once the command is
    // complete. Used by the script execution command
    private boolean shutdownWhenComplete;

    // Application exit value following script execution: = 0 if the script(s)
    // completed successfully; = 1 if a script fails to complete successfully
    private int scriptExitStatus;

    // Session event log file path command name
    private static final String LOG_PATH = "logPath";

    // Session script output file path command name
    private static final String SCRIPT_OUTPUT_PATH = "scriptOutPath";

    /**************************************************************************
     * Individual command line argument handler class
     *************************************************************************/
    private class CommandHandler
    {
        private final String command;
        private final String description;
        private final String value;
        private final CommandLineType type;
        private final String[] options;
        private final Object[] conditions;
        private final int priority;

        /**********************************************************************
         * Individual command line argument handler class constructor for
         * commands with a fixed set of valid options
         *
         * @param command
         *            command name
         *
         * @param description
         *            command description
         *
         * @param value
         *            permissible values for this command
         *
         *
         * @param type
         *            command type: CmdType.NAME, or CmdType.COLOR
         *
         * @param priority
         *            order in which the command line argument should be
         *            executed relative to the other commands (a command with a
         *            lower priority number is executed prior to one with a
         *            higher number; commands with the same priority are
         *            executed in the order they appear on the command line
         *
         * @param options
         *            array of valid input options
         *
         * @param conditions
         *            array of output parameters associated with each input
         *            option
         *********************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 Object[] conditions,
                                 String[] options)
        {
            this.command = command;
            this.description = description;
            this.value = value;
            this.type = type;
            this.priority = priority;
            this.conditions = conditions;
            this.options = options;
        }

        /**********************************************************************
         * Individual command line argument handler class constructor for
         * commands having no limit values or fixed set of options
         *
         * @param command
         *            command name
         *
         * @param description
         *            command description
         *
         * @param value
         *            permissible values for this command
         *
         * @param type
         *            command type: CmdType.NAME, or CmdType.COLOR
         *
         * @param priority
         *            order in which the command line argument should be
         *            executed relative to the other commands (a command with a
         *            lower priority number is executed prior to one with a
         *            higher number; commands with the same priority are
         *            executed in the order they appear on the command line
         *********************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority)
        {
            this(command,
                 description,
                 value,
                 type,
                 priority,
                 null,
                 null);
        }

        /**********************************************************************
         * Individual command line argument handler class constructor for
         * commands with value limits
         *
         * @param command
         *            command name
         *
         * @param description
         *            command description
         *
         * @param value
         *            permissible values for this command
         *
         * @param type
         *            command type: CmdType.MINMAX or CmdType.SIZE
         *
         * @param priority
         *            order in which the command line argument should be
         *            executed relative to the other commands (a command with a
         *            lower priority number is executed prior to one with a
         *            higher number; commands with the same priority are
         *            executed in the order they appear on the command line
         *
         * @param conditions
         *            array of limit values
         *********************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 Object[] conditions)
        {
            this(command,
                 description,
                 value,
                 type,
                 priority,
                 conditions,
                 null);
        }

        /**********************************************************************
         * Get the command priority
         *
         * @return Command priority
         *********************************************************************/
        protected int getPriority()
        {
            return priority;
        }

        /**********************************************************************
         * Command line argument handler
         *
         * @param parm
         *            command parameter
         *
         * @return true if the command does not succeed
         *********************************************************************/
        protected boolean handler(String parm)
        {
            Object parmVal = null;

            // Check and convert the command parameter value based on the
            // command type
            switch (type)
            {
                case NAME:
                    parmVal = parm;
                    break;

                case OPTION:
                    parmVal = handleOption(command, parm, options, conditions);
                    break;

                case MINMAX:
                    parmVal = handleMinMax(command, parm, conditions);
                    break;

                case COLOR:
                    parmVal = handleColor(command, parm);
                    break;

                case SIZE:
                    parmVal = handleSize(command, parm, conditions);
                    break;
            }

            // Check if a valid command line parameter was found
            if (parmVal != null)
            {
                // Implement the command
                doCommand(parmVal);
            }

            return parmVal == null;
        }

        /**********************************************************************
         * Placeholder for command type specific handler
         *
         * @param parmVal
         *            command parameter
         *********************************************************************/
        protected void doCommand(Object parmVal)
        {
        }
    }

    /**************************************************************************
     * Command line argument handler class constructor
     *
     * @param args
     *            array of command line arguments and associated parameters
     *************************************************************************/
    protected CcddCommandLineHandler(final CcddMain ccddMain, String[] args)
    {
        this.ccddMain = ccddMain;
        this.args = args;
        errorMessage = null;
        argument = new ArrayList<CommandHandler>();

        // Event log file path command. This command, if present, is executed
        // prior to all other commands, regardless of relative priorities
        argument.add(new CommandHandler(LOG_PATH,
                                        "Set event log file path",
                                        "file path",
                                        CommandLineType.NAME,
                                        1)
        {
            /******************************************************************
             * Set the event log file path
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                CcddFileIOHandler.storePath(ccddMain,
                                            (String) parmVal,
                                            false,
                                            ModifiablePathInfo.SESSION_LOG_FILE_PATH);
            }
        });

        // CCDD project command
        argument.add(new CommandHandler("project",
                                        "Select CCDD project",
                                        "project name",
                                        CommandLineType.NAME,
                                        1)
        {
            /******************************************************************
             * Set the project database
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setDatabase((String) parmVal);
            }
        });

        // Backup the project database on connecting command
        argument.add(new CommandHandler("backup",
                                        "Backup project on connecting",
                                        "backup file name",
                                        CommandLineType.NAME,
                                        3)
        {
            /******************************************************************
             * Backup the project database when first connected
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setBackupFileName((String) parmVal);
            }
        });

        // User name command
        argument.add(new CommandHandler("user",
                                        "Set user name",
                                        "user name",
                                        CommandLineType.NAME,
                                        2)
        {
            /******************************************************************
             * Set the user name
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setUser((String) parmVal);
            }
        });

        // User password command
        argument.add(new CommandHandler("password",
                                        "Set user password",
                                        "user password",
                                        CommandLineType.NAME,
                                        2)
        {
            /******************************************************************
             * Set the user password
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setPassword((String) parmVal);
            }
        });

        // PostgreSQL server host command
        argument.add(new CommandHandler("host",
                                        "Set PostgreSQL server host",
                                        "host name",
                                        CommandLineType.NAME,
                                        2)
        {
            /******************************************************************
             * Set the PostgreSQL server host
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setHost((String) parmVal);
            }
        });

        // PostgreSQL server port command
        argument.add(new CommandHandler("port",
                                        "Set PostgreSQL server port",
                                        "port number",
                                        CommandLineType.NAME,
                                        2)
        {
            /******************************************************************
             * Set the PostgreSQL server port
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setPort((String) parmVal);
            }
        });

        // 'All' event log filter
        argument.add(new CommandHandler("events",
                                        "Show events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show all events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.SELECT_ALL,
                                                        (Boolean) parmVal);
            }
        });

        // 'Command' event log filter
        argument.add(new CommandHandler("command",
                                        "Show command events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show command events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.COMMAND_MSG,
                                                        (Boolean) parmVal);
            }
        });

        // 'Success' event log filter
        argument.add(new CommandHandler("success",
                                        "Show success events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show success events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.SUCCESS_MSG,
                                                        (Boolean) parmVal);
            }
        });

        // 'Fail' event log filter
        argument.add(new CommandHandler("fail",
                                        "Show fail events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show fail events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.FAIL_MSG,
                                                        (Boolean) parmVal);
            }
        });

        // 'Status' event log filter
        argument.add(new CommandHandler("status",
                                        "Show status events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show status events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.STATUS_MSG,
                                                        (Boolean) parmVal);
            }
        });

        // 'Server' event log filter
        argument.add(new CommandHandler("server",
                                        "Show web server events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        4,
                                        new Object[] {true, false},
                                        new String[] {"true", "false"})
        {
            /******************************************************************
             * Set the show web server events flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getSessionEventLog().setFilter(EventLogMessageType.SERVER_MSG,
                                                        (Boolean) parmVal);
            }
        });

        // Look & feel command
        argument.add(new CommandHandler("laf",
                                        "Load look & feel",
                                        "look & feel",
                                        CommandLineType.NAME,
                                        6)
        {
            /******************************************************************
             * Set the look & feel
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.setLookAndFeel((String) parmVal);
            }
        });

        // Main window size command
        argument.add(new CommandHandler("mainSize",
                                        "Set main window size",
                                        "widthxheight",
                                        CommandLineType.SIZE,
                                        6,
                                        new Object[] {MIN_WINDOW_WIDTH,
                                                      MIN_WINDOW_HEIGHT})
        {
            /******************************************************************
             * Set the main window size
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getMainFrame().setSize((Dimension) parmVal);
            }
        });

        // Enable the web server, with or without the graphical user interface
        argument.add(new CommandHandler("webserver",
                                        "Enable web server",
                                        "nogui or gui",
                                        CommandLineType.NAME,
                                        5)
        {
            /******************************************************************
             * Enable the web server
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.setWebServer((String) parmVal);
            }
        });

        // Web server port command
        argument.add(new CommandHandler("webport",
                                        "Set web server port",
                                        "port number",
                                        CommandLineType.NAME,
                                        5)
        {
            /******************************************************************
             * Set the web server port
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.setWebServerPort((String) parmVal);
            }
        });

        // Script output file path command
        argument.add(new CommandHandler(SCRIPT_OUTPUT_PATH,
                                        "Set script output file path",
                                        "file path",
                                        CommandLineType.NAME,
                                        1)
        {
            /******************************************************************
             * Set the script output file path
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                CcddFileIOHandler.storePath(ccddMain,
                                            (String) parmVal,
                                            false,
                                            ModifiablePathInfo.SCRIPT_OUTPUT_PATH);
            }
        });

        // Execute script command
        argument.add(new CommandHandler("execute",
                                        "Execute script(s)",
                                        "script name["
                                                             + LIST_TABLE_DESC_SEPARATOR.trim()
                                                             + "table["
                                                             + LIST_TABLE_SEPARATOR.trim()
                                                             + "table2["
                                                             + LIST_TABLE_SEPARATOR.trim()
                                                             + "...["
                                                             + LIST_TABLE_SEPARATOR.trim()
                                                             + "tableN]]]][;...]",
                                        CommandLineType.NAME,
                                        10)
        {
            /******************************************************************
             * Execute a script. The application exits following completion of
             * this command
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Set the flag that hides the GUI so that dialog messages are
                // redirected to the command line
                ccddMain.setGUIHidden(true);

                // Check if a project database, user, and host are specified
                if (!ccddMain.getDbControlHandler().getDatabase().isEmpty()
                    && !ccddMain.getDbControlHandler().getDatabase().equals(DEFAULT_DATABASE)
                    && !ccddMain.getDbControlHandler().getUser().isEmpty()
                    && !ccddMain.getDbControlHandler().getHost().isEmpty())
                {
                    // Check if the database opens successfully and that the
                    // project database opened, as opposed to the server only
                    if (!ccddMain.getDbControlHandler().openDatabase(ccddMain.getDbControlHandler().getDatabase())
                        && ccddMain.getDbControlHandler().isDatabaseConnected())
                    {
                        List<Object[]> associations = new ArrayList<Object[]>();

                        // Break the supplied string of semicolon-separated
                        // associations into the individual associations
                        String[] associationsArray = parmVal.toString().split(";");

                        // Step through each association
                        for (String associationString : associationsArray)
                        {
                            // Break the supplied association into the script
                            // name and table(s) (if any)
                            String[] scriptAndTable = associationString.split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR.trim()), 2);

                            // Set the script and table in the association
                            // array (assumes no table(s))
                            String[] association = new String[] {"", scriptAndTable[0].trim(), " "};

                            // Check if one or more tables are provided
                            if (scriptAndTable.length == 2)
                            {
                                // Set the association tables. If multiple
                                // tables are provided then separate these with
                                // the expected separator string
                                association[2] = scriptAndTable[1].replaceAll(" ",
                                                                              "")
                                                                  .replaceAll(Pattern.quote("+"),
                                                                              LIST_TABLE_SEPARATOR);
                            }

                            // Add the association to the list
                            associations.add(association);
                        }

                        // Execute the script association(s) and log the result
                        boolean[] isBad = ccddMain.getScriptHandler().getDataAndExecuteScript(null,
                                                                                              null,
                                                                                              associations);
                        ccddMain.getScriptHandler().logScriptCompletionStatus(associations, isBad);

                        // Step through the script execution fail flags
                        for (boolean flag : isBad)
                        {
                            // Check if the script execution failed
                            if (flag)
                            {
                                // Set the application return value to indicate
                                // a failure and stop searching
                                scriptExitStatus = 1;
                                break;
                            }
                        }
                    }
                }
                // Missing project database, user, or host
                else
                {
                    // Set the application return value to indicate a failure
                    // and stop searching
                    scriptExitStatus = 1;

                    // Inform the user that one or more required parameters is
                    // missing
                    ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                               "Project database, user name, and/or host missing",
                                                               "<html><b>Project database, user name, and/or host missing");
                }

                // Set the flag that indicates the application should exit
                // following execution of the script association(s)
                shutdownWhenComplete = true;
            }
        });
    }

    /**************************************************************************
     * Parse and execute the command line argument(s)
     *
     * @param doLogPathOnly
     *            true to only parse and execute the session event log file
     *            path command (if present); false to parse and execute all
     *            commands
     *************************************************************************/
    protected void parseCommand(boolean doLogPathOnly)
    {
        try
        {
            // Check if there are an odd number of arguments
            if (args.length % 2 != 0)
            {
                throw new CCDDException();
            }

            List<Integer> priorities = new ArrayList<Integer>();
            shutdownWhenComplete = false;
            scriptExitStatus = 0;

            // Step through the valid commands
            for (CommandHandler cmd : argument)
            {
                // Check if the priority list doesn't contain the priority
                // value for this command
                if (!priorities.contains(cmd.getPriority()))
                {
                    // Add the priority value to the list
                    priorities.add(cmd.getPriority());
                }
            }

            // Sort the priority values in ascending order
            Collections.sort(priorities);

            // Step through each valid priority value
            for (int priority : priorities)
            {
                // Step through the command line arguments
                for (int index = 0; index < args.length; index += 2)
                {
                    // Get the next command line argument and associated
                    // parameter
                    String arg = args[index];
                    String parm = args[index + 1];

                    // Check if the command doesn't start with a recognized
                    // delimiter
                    if (!arg.startsWith("-") && arg.startsWith("/"))
                    {
                        throw new CCDDException();
                    }

                    // Remove the delimiter
                    arg = arg.replaceFirst("^[-/]", "");

                    boolean isValidCmd = false;

                    // Step through the valid commands
                    for (CommandHandler cmd : argument)
                    {
                        // Check if the command argument matches a valid
                        // command
                        if (arg.equalsIgnoreCase(cmd.command))
                        {
                            // Set the flag to indicate this is a recognized
                            // command
                            isValidCmd = true;

                            // Check if the command's priority matches the
                            // current priority (if processing all commands) or
                            // if this is the log path command (if only the log
                            // path command is to be processed)
                            if ((!doLogPathOnly && cmd.getPriority() == priority)
                                || (doLogPathOnly && arg.equals(LOG_PATH)))
                            {
                                // Handle the command and check if it results
                                // in an error condition
                                if (cmd.handler(parm))
                                {
                                    throw new CCDDException();
                                }

                                // Stop searching since a matching command was
                                // found
                                break;
                            }
                        }
                    }

                    // Check if the command wasn't recognized
                    if (!isValidCmd)
                    {
                        throw new CCDDException();
                    }
                }
            }

            // Check if a script association execution command was performed
            if (shutdownWhenComplete)
            {
                // Exit the application, supplying the script execution status
                ccddMain.exitApplication(false, scriptExitStatus);
            }
        }
        catch (CCDDException ce)
        {
            // Check if a bad parameter was detected
            if (errorMessage != null)
            {
                System.err.println("Error: " + errorMessage + "\n");
            }
            // Invalid command
            else
            {
                // Initialize the maximum usage parameter lengths
                int descLen = 0;
                int cmdLen = 0;
                int valLen = 0;

                // Step through each command
                for (CommandHandler cmd : argument)
                {
                    // Store the longest description, command, and value text
                    descLen = Math.max(descLen, cmd.description.length());
                    cmdLen = Math.max(cmdLen, cmd.command.length());
                    valLen = Math.max(valLen, cmd.value.length());
                }

                // Adjust the value text to account for the '<>' characters
                valLen += 2;

                // Build the format string using the maximum lengths
                String format = "  %-"
                                + descLen
                                + "."
                                + descLen
                                + "s  %-"
                                + cmdLen
                                + "."
                                + cmdLen
                                + "s  %-"
                                + valLen
                                + "."
                                + valLen
                                + "s\n";

                // Create a string filled with '-' characters
                char[] dashes = new char[Math.max(Math.max(descLen, cmdLen), valLen)];
                Arrays.fill(dashes, '-');
                String dash = new String(dashes);

                // Initialize the usage information
                String usage = String.format("usage:\n"
                                             + "java -classpath <class_paths> CCDD.CcddMain [[<- or />]<command> <value> [...]]\n"
                                             + " Command line arguments:\n"
                                             + format
                                             + format,
                                             "Description",
                                             "Command",
                                             "Value",
                                             dash,
                                             dash,
                                             dash);

                // Step through each command
                for (CommandHandler cmd : argument)
                {
                    // Append the command's usage text
                    usage += String.format(format,
                                           cmd.description,
                                           cmd.command,
                                           "<" + cmd.value + ">");
                }

                // Display the usage information
                System.out.println(usage + "\n");
            }

            // Exit the application
            System.exit(0);
        }
    }

    /**************************************************************************
     * Get the main window size parameter
     *
     * @param arg
     *            window size command line argument
     *
     * @param parm
     *            window dimensions
     *
     * @param min
     *            array containing the minimum main window width and minimum
     *            main window height
     *
     * @return Main window dimension value (null if an error is found)
     *************************************************************************/
    private Dimension handleSize(String arg, String parm, Object[] min)
    {
        Dimension val = null;

        // Split out the width and height strings
        String[] parts = parm.split("x");

        // Check that only two values are provided and that both contain only
        // decimal digits
        if (parts.length == 2
            && parts[0].matches("\\d+")
            && parts[1].matches("\\d+"))
        {
            // Convert the width and height strings to a Dimension variable
            val = new Dimension(Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]));

            // Check if the width is below the minimum allowed
            if (val.width < (int) min[0])
            {
                // Set the width to the minimum
                val.width = (int) min[0];
            }

            // Check if the height is below the minimum allowed
            if (val.height < (int) min[1])
            {
                // Set the height to the minimum
                val.height = (int) min[1];
            }
        }
        // Width or height string contains a non-numeral or the wrong number of
        // values
        else
        {
            errorMessage = arg
                           + " width or height not a number, or too many/few values";
        }

        return val;
    }

    /**************************************************************************
     * Get an integer value parameter that has minimum and maximum limits
     *
     * @param arg
     *            integer value command line argument
     *
     * @param parm
     *            integer value
     *
     * @param limit
     *            array containing the lower limit value (inclusive) and the
     *            upper limit value (inclusive)
     *
     * @return Integer value (null if an error is found)
     *************************************************************************/
    private Integer handleMinMax(String arg, String parm, Object[] limit)
    {
        Integer val = null;

        // Check if the parameter contains only decimal digits
        if (parm.matches("\\d+"))
        {
            // Convert the string to an integer
            val = Integer.parseInt(parm);

            // Check if the value falls outside the limits
            if (val < (int) limit[0] || val > (int) limit[1])
            {
                errorMessage = arg
                               + " must be >= "
                               + limit[0]
                               + " and <= "
                               + limit[1];
                val = null;
            }
        }
        // String contains a non-numeral
        else
        {
            errorMessage = arg + " not a number";
        }

        return val;
    }

    /**************************************************************************
     * Get a color parameter
     *
     * @param arg
     *            color command line argument
     *
     * @param parm
     *            color in hexadecimal format or a color name
     *
     * @return Selected color in hexadecimal format (null if an error is found)
     *************************************************************************/
    private Integer handleColor(String arg, String parm)
    {
        Integer val = null;

        // Remove the leading '0x' if present (not required)
        if (parm.startsWith("0x"))
        {
            parm = parm.substring(2);
        }

        // Check if the parameter has the allowed number of hexadecimal digits
        if (parm.length() == 6 && parm.matches("[0-9A-Fa-f]+"))
        {
            // Convert the string to an integer
            val = Integer.parseInt(parm, 16);
        }
        // Not a hexadecimal value
        else
        {
            try
            {
                // Check if the parameter is a valid color name; if so, get the
                // RGB value (mask out the alpha portion)
                val = ((Color) Color.class.getField(parm).get(null)).getRGB()
                      & 0xffffff;
            }
            catch (Exception e)
            {
                // Parameter is not a hexadecimal color or color name
                errorMessage = arg
                               + " must be a color name or in the format "
                               + "'0x######' where '#' is a hexadecimal digit";
            }
        }

        return val;
    }

    /**************************************************************************
     * Get the selected option which must match one of a predefined list of
     * options. The command argument if forced to lower case when comparing to
     * the acceptable options
     *
     * @param arg
     *            command line argument
     *
     * @param parm
     *            command option
     *
     * @param inputOptions
     *            array of valid inputs
     *
     * @param outputValues
     *            array of output values associated with each input option
     *
     * @return Output value for the option selected
     *************************************************************************/
    private Object handleOption(String arg,
                                String parm,
                                String[] inputOptions,
                                Object[] outputValues)
    {
        Object val = null;
        int index = 0;
        String valid = "";

        // Force the command option to lower case
        parm = parm.toLowerCase();

        // Step through the valid options
        for (Object option : inputOptions)
        {
            // Check if the command parameter matches the valid option
            if (parm.equals(option))
            {
                // Get the associated output value for this option
                val = outputValues[index];
                break;
            }

            // Build the option list for the error message text, in case it's
            // needed
            valid += (index != 0 ? " | " : " ") + inputOptions[index];

            index++;
        }

        // Check if no valid option was found
        if (index == inputOptions.length)
        {
            // Build the error message
            errorMessage = arg + " must be one of the following:" + valid;
        }

        return val;
    }
}
