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
import java.util.List;
import java.util.regex.Pattern;

import CCDD.CcddConstants.CommandLineType;
import CCDD.CcddConstants.EventLogMessageType;

/******************************************************************************
 * CFS Command & Data Dictionary command line argument handler class
 *****************************************************************************/
public class CcddCommandLineHandler
{
    // Class reference
    private final CcddDbControlHandler dbControl;
    private final CcddScriptHandler scriptHandler;

    // Text describing the command line error
    private String errorMessage;

    /**************************************************************************
     * Individual command line argument handler class
     *************************************************************************/
    private class CommandHandler
    {
        private final String command;
        private final String description;
        private final String value;
        private final CommandLineType type;
        private String[] options;
        private Object[] conditions;

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
         *********************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type)
        {
            this.command = command;
            this.description = description;
            this.value = value;
            this.type = type;
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
         * @param conditions
         *            array of limit values
         *********************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 Object[] conditions)
        {
            this.command = command;
            this.description = description;
            this.value = value;
            this.type = type;
            this.conditions = conditions;
        }

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
                                 String[] options,
                                 Object[] conditions)
        {
            this.command = command;
            this.description = description;
            this.value = value;
            this.type = type;
            this.options = options;
            this.conditions = conditions;
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
        // Create reference to shorten subsequent calls
        dbControl = ccddMain.getDbControlHandler();
        scriptHandler = ccddMain.getScriptHandler();

        errorMessage = null;

        // List to hold the valid command line arguments
        List<CommandHandler> argument = new ArrayList<CommandHandler>();

        // CCDD project command
        argument.add(new CommandHandler("project",
                                        "Select CCDD project",
                                        "project name",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Set the project database
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setDatabase((String) parmVal);
            }
        });

        // Backup the project database on connecting command
        argument.add(new CommandHandler("backup",
                                        "Backup project on connecting",
                                        "backup file name",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Backup the project database when first connected
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setBackupFileName((String) parmVal);
            }
        });

        // User name command
        argument.add(new CommandHandler("user",
                                        "Set user name",
                                        "user name",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Set the user name
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setUser((String) parmVal);
            }
        });

        // User password command
        argument.add(new CommandHandler("password",
                                        "Set user password",
                                        "user password",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Set the user password
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setPassword((String) parmVal);
            }
        });

        // PostgreSQL server host command
        argument.add(new CommandHandler("host",
                                        "Set PostgreSQL server host",
                                        "host name",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Set the PostgreSQL server host
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setHost((String) parmVal);
            }
        });

        // PostgreSQL server port command
        argument.add(new CommandHandler("port",
                                        "Set PostgreSQL server port",
                                        "port number",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Set the PostgreSQL server port
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dbControl.setPort((String) parmVal);
            }
        });

        // 'All' event log filter
        argument.add(new CommandHandler("events",
                                        "Show events",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
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
                                        CommandLineType.NAME)
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

        // Automatically validate telemetry and application scheduler entries
        argument.add(new CommandHandler("validate",
                                        "Validate messages",
                                        "true or false",
                                        CommandLineType.OPTION,
                                        new String[] {"true", "false"},
                                        new Object[] {true, false})
        {
            /******************************************************************
             * Set the automatic validate flag
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.setAutoValidate((Boolean) parmVal);
            }
        });

        // Enable the web server, with or without the graphical user interface
        argument.add(new CommandHandler("webserver",
                                        "Enable web server",
                                        "nogui or gui",
                                        CommandLineType.NAME)
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
                                        CommandLineType.NAME)
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

        // Execute script command
        argument.add(new CommandHandler("execute",
                                        "Execute script(s)",
                                        "script_name["
                                            + LIST_TABLE_DESC_SEPARATOR.trim()
                                            + "table["
                                            + LIST_TABLE_SEPARATOR.trim()
                                            + "table2["
                                            + LIST_TABLE_SEPARATOR.trim()
                                            + "...["
                                            + LIST_TABLE_SEPARATOR.trim()
                                            + "tableN]]]][,...]",
                                        CommandLineType.NAME)
        {
            /******************************************************************
             * Execute a script. The application exits following completion of
             * this command
             *****************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Set the application return status, assuming failure
                int status = 1;

                // Set the flag that hides the GUI so that dialog messages are
                // redirected to the command line
                ccddMain.setGUIHidden(true);

                // Check if a project database, user, and host are specified
                if (!dbControl.getDatabase().isEmpty()
                    && !dbControl.getDatabase().equals(DEFAULT_DATABASE)
                    && !dbControl.getUser().isEmpty()
                    && !dbControl.getHost().isEmpty())
                {
                    // Check if the database opens successfully and that the
                    // project database opened, as opposed to the server only
                    if (!dbControl.openDatabase(dbControl.getDatabase())
                        && dbControl.isDatabaseConnected())
                    {
                        // Set the application return status, assuming success
                        status = 0;

                        List<String[]> associationsList = new ArrayList<String[]>();

                        // Break the supplied string of comma-separated
                        // associations into the individual associations
                        String[] associationsArray = parmVal.toString().split(",");

                        // Step through each association
                        for (String associationString : associationsArray)
                        {
                            // Break the supplied association into the script
                            // name and table(s) (if any)
                            String[] scriptAndTable = associationString.split(Pattern.quote(LIST_TABLE_DESC_SEPARATOR.trim()), 2);

                            // Set the script and table in the association
                            // array (assumes no table(s))
                            String[] association = new String[] {scriptAndTable[0].trim(), " "};

                            // Check if one or more tables are provided
                            if (scriptAndTable.length == 2)
                            {
                                // Set the association tables. If multiple
                                // tables are provided then separate these with
                                // the expected separator string
                                association[1] = scriptAndTable[1].replaceAll(" ",
                                                                              "").replaceAll(Pattern.quote("+"),
                                                                                             LIST_TABLE_SEPARATOR);
                            }

                            // Add the association to the list
                            associationsList.add(association);
                        }

                        // Convert the associations list to an array
                        String[][] associations = associationsList.toArray(new String[0][0]);

                        // Execute the script association(s) and log the result
                        boolean[] isBad = scriptHandler.getDataAndExecuteScript(null,
                                                                                null,
                                                                                associations);
                        scriptHandler.logScriptCompletionStatus(associations, isBad);

                        // Step through the script execution fail flags
                        for (boolean flag : isBad)
                        {
                            // Check if the script execution failed
                            if (flag)
                            {
                                // Set the application return, indicating a
                                // failure, status and stop searching
                                status = 1;
                                break;
                            }
                        }
                    }
                }
                // Missing project database, user, or host
                else
                {
                    // Inform the user that one or more required parameters is
                    // missing
                    ccddMain.getSessionEventLog().logFailEvent(ccddMain.getMainFrame(),
                                                               "Project database, user name, and/or host missing",
                                                               "<html><b>Project database, user name, and/or host missing");
                }

                // Exit the application, supplying the script execution status
                ccddMain.exitApplication(false, status);
            }
        });

        // Command line error detected flag
        boolean errorFlag = false;

        // Step through the command line arguments
        for (int index = 0; index < args.length && !errorFlag; index++)
        {
            // Assume an error exists
            errorFlag = true;

            // Get the next command line argument
            String arg = args[index];

            // Check if the command starts with a recognized delimiter
            if (arg.startsWith("-") || arg.startsWith("/"))
            {
                // Remove the delimiter
                arg = arg.substring(1);
            }

            // Increment the index to point at the argument's parameter
            index++;

            // Check that the argument's parameter is present
            if (index < args.length)
            {
                // Get the argument's parameter
                String parm = args[index];

                // Step through the valid commands
                for (CommandHandler cmd : argument)
                {
                    // Check if the command matches a valid command
                    if (arg.equalsIgnoreCase(cmd.command))
                    {
                        // Handle the command
                        errorFlag = cmd.handler(parm);
                        break;
                    }
                }
            }
        }

        // Check if an invalid command argument or parameter was provided, or
        // if the parameter was missing
        if (errorFlag)
        {
            // Check if a bad parameter was detected
            if (errorMessage != null)
            {
                System.err.println("Error: " + errorMessage + "\n");
            }
            // Invalid command
            else
            {
                // initialize the maximum usage parameter lengths
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
            if (val.width < (Integer) min[0])
            {
                // Set the width to the minimum
                val.width = (Integer) min[0];
            }

            // Check if the height is below the minimum allowed
            if (val.height < (Integer) min[1])
            {
                // Set the height to the minimum
                val.height = (Integer) min[1];
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
            if (val < (Integer) limit[0] || val > (Integer) limit[1])
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
     * options
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
