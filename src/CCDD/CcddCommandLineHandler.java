/**
 * CFS Command & Data Dictionary command line argument handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.SCRIPT_MEMBER_SEPARATOR;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddConstants.CommandLineType;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.ModifiablePathInfo;

/**************************************************************************************************
 * CFS Command & Data Dictionary command line argument handler class
 *************************************************************************************************/
public class CcddCommandLineHandler
{
    // Class reference
    private final CcddMain ccddMain;

    // Text describing the command line error
    private String errorMessage;

    // Array containing the command line arguments provided by the user
    private final String[] args;

    // Lists containing the valid command line argument handlers
    private final List<CommandHandler> argument;
    private final List<CommandHandler> importArgument;
    private final List<CommandHandler> exportArgument;

    // Flag that indicates the application should exit once the command is complete. Used by the
    // script execution command
    private boolean shutdownWhenComplete;

    // Application exit value following script execution: = 0 if the script(s) completed
    // successfully; = 1 if a script fails to complete successfully
    private int exitStatus;

    // Import command parameters
    private FileEnvVar[] dataFile;
    private boolean replaceExisting;
    private boolean appendExistingFields;
    private boolean useExistingFields;

    // Export command parameters
    private String filePath;
    private String[] tablePaths;
    private boolean overwriteFile;
    private boolean singleFile;
    private boolean replaceMacros;
    private boolean includeReservedMsgIDs;
    private boolean includeProjectFields;
    private boolean includeVariablePaths;
    private final String[] separators;
    private FileExtension fileExtn;
    private EndianType endianess;
    private boolean isHeaderBigEndian;
    private String version;
    private String validationStatus;
    private String classification1;
    private String classification2;
    private String classification3;
    private String scriptFileName;

    // Session event log file path command name
    private static final String LOG_PATH = "logPath";

    // Session script output file path command name
    private static final String SCRIPT_OUTPUT_PATH = "scriptOutPath";

    /**********************************************************************************************
     * Individual command line argument handler class
     *********************************************************************************************/
    private class CommandHandler
    {
        private final String command;
        private final String description;
        private final String value;
        private final CommandLineType type;
        private final String[] options;
        private final Object[] conditions;
        private final int priority;
        private final List<CommandHandler> subArgument;

        /******************************************************************************************
         * Individual command line argument handler class constructor for commands with a fixed set
         * of valid options
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
         *            command type: CommandLineType.NAME, CommandLineType.MINMAX,
         *            CommandLineType.SIZE, CommandLineType.COLOR, or CommandLineType.OPTION
         *
         * @param priority
         *            order in which the command line argument should be executed relative to the
         *            other commands (a command with a lower priority number is executed prior to
         *            one with a higher number; commands with the same priority are executed in the
         *            order they appear on the command line
         *
         * @param conditions
         *            array of output parameters associated with each input option
         *
         * @param options
         *            array of valid input options
         *
         * @param subArgument
         *            List of this commands sub-argument definitions
         *****************************************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 Object[] conditions,
                                 String[] options,
                                 List<CommandHandler> subArgument)
        {
            this.command = command;
            this.description = description;
            this.value = value;
            this.type = type;
            this.priority = priority;
            this.conditions = conditions;
            this.options = options;
            this.subArgument = subArgument;
        }

        /******************************************************************************************
         * Individual command line argument handler class constructor for commands with a fixed set
         * of valid options
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
         *            command type: CommandLineType.NAME, CommandLineType.MINMAX,
         *            CommandLineType.SIZE, CommandLineType.COLOR, or CommandLineType.OPTION
         *
         * @param priority
         *            order in which the command line argument should be executed relative to the
         *            other commands (a command with a lower priority number is executed prior to
         *            one with a higher number; commands with the same priority are executed in the
         *            order they appear on the command line
         *
         * @param conditions
         *            array of output parameters associated with each input option
         *
         * @param options
         *            array of valid input options
         *****************************************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 Object[] conditions,
                                 String[] options)
        {
            this(command, description, value, type, priority, conditions, options, null);
        }

        /******************************************************************************************
         * Individual command line argument handler class constructor for commands having no limit
         * values or fixed set of options
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
         *            order in which the command line argument should be executed relative to the
         *            other commands (a command with a lower priority number is executed prior to
         *            one with a higher number; commands with the same priority are executed in the
         *            order they appear on the command line
         *****************************************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority)
        {
            this(command, description, value, type, priority, null, null, null);
        }

        /******************************************************************************************
         * Individual command line argument handler class constructor for commands having no limit
         * values or fixed set of options
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
         *            order in which the command line argument should be executed relative to the
         *            other commands (a command with a lower priority number is executed prior to
         *            one with a higher number; commands with the same priority are executed in the
         *            order they appear on the command line
         *
         * @param subArguments
         *            List of this commands sub-argument definitions
         *****************************************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 List<CommandHandler> subArguments)
        {
            this(command, description, value, type, priority, null, null, subArguments);
        }

        /******************************************************************************************
         * Individual command line argument handler class constructor for commands with value
         * limits
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
         *            order in which the command line argument should be executed relative to the
         *            other commands (a command with a lower priority number is executed prior to
         *            one with a higher number; commands with the same priority are executed in the
         *            order they appear on the command line
         *
         * @param conditions
         *            array of limit values
         *****************************************************************************************/
        protected CommandHandler(String command,
                                 String description,
                                 String value,
                                 CommandLineType type,
                                 int priority,
                                 Object[] conditions)
        {
            this(command, description, value, type, priority, conditions, null, null);
        }

        /******************************************************************************************
         * Check if the command requires an argument
         *
         * @return true if the command requires an argument
         *****************************************************************************************/
        protected boolean hasArgument()
        {
            return type != CommandLineType.NONE;
        }

        /******************************************************************************************
         * Get the list of sub-arguments for this command
         *
         * @return List of sub-arguments for this command; null if the command has no sub-arguments
         *****************************************************************************************/
        protected List<CommandHandler> getSubArgument()
        {
            return subArgument;
        }

        /******************************************************************************************
         * Command line argument handler
         *
         * @param parm
         *            command parameter
         *
         * @return true if the command does not succeed
         *****************************************************************************************/
        protected boolean handler(String parm)
        {
            Object parmVal = null;

            // Check and convert the command parameter value based on the command type
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

                case NONE:
                    parmVal = "";
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

        /******************************************************************************************
         * Placeholder for command type specific handler
         *
         * @param parmVal
         *            command parameter
         *****************************************************************************************/
        protected void doCommand(Object parmVal)
        {
        }
    }

    /**********************************************************************************************
     * Command line argument handler class constructor
     *
     * @param args
     *            array of command line arguments and associated parameters
     *********************************************************************************************/
    CcddCommandLineHandler(final CcddMain ccddMain, String[] args)
    {
        this.ccddMain = ccddMain;
        this.args = args;
        errorMessage = null;
        argument = new ArrayList<CommandHandler>();
        importArgument = new ArrayList<CommandHandler>();
        exportArgument = new ArrayList<CommandHandler>();
        dataFile = null;
        replaceExisting = false;
        appendExistingFields = false;
        useExistingFields = false;
        filePath = null;
        tablePaths = null;
        overwriteFile = false;
        singleFile = false;
        replaceMacros = false;
        includeReservedMsgIDs = false;
        includeProjectFields = false;
        includeVariablePaths = false;
        separators = new String[3];
        fileExtn = FileExtension.CSV;
        endianess = EndianType.BIG_ENDIAN;
        isHeaderBigEndian = true;
        version = null;
        validationStatus = null;
        classification1 = null;
        classification2 = null;
        classification3 = null;
        scriptFileName = null;
        shutdownWhenComplete = false;
        exitStatus = 0;

        // Display application version information command
        argument.add(new CommandHandler("version",
                                        "Display CCDD version",
                                        "",
                                        CommandLineType.NONE,
                                        0)
        {
            /**************************************************************************************
             * Display the application version and build date, then exit the program
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                System.out.println("CCDD " + ccddMain.getCCDDVersionInformation());
                System.exit(0);
            }
        });

        // Event log file path command. This command, if present, is executed prior to all other
        // commands, regardless of relative priorities
        argument.add(new CommandHandler(LOG_PATH,
                                        "Set event log file path",
                                        "file path",
                                        CommandLineType.NAME,
                                        1)
        {
            /**************************************************************************************
             * Set the event log file path
             *************************************************************************************/
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
                                        2)
        {
            /**************************************************************************************
             * Set the project database
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setProjectName((String) parmVal);
            }
        });

        // Backup the project database on connecting command
        argument.add(new CommandHandler("backup",
                                        "Backup project on connecting",
                                        "backup file name",
                                        CommandLineType.NAME,
                                        3)
        {
            /**************************************************************************************
             * Backup the project database when first connected
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the user name
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the user password
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the PostgreSQL server host
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the PostgreSQL server port
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setPort((String) parmVal);
            }
        });

        // PostgreSQL server secure socket layer command
        argument.add(new CommandHandler("ssl",
                                        "Enable/disable SSL",
                                        "on or off",
                                        CommandLineType.OPTION,
                                        2,
                                        new Object[] {true, false},
                                        new String[] {"on", "off"})
        {
            /**************************************************************************************
             * Enable/disable server secure socket for the PostgreSQL server
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ccddMain.getDbControlHandler().setSSL((Boolean) parmVal);
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
            /**************************************************************************************
             * Set the show all events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the show command events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the show success events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the show fail events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the show status events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the show web server events flag
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the look & feel
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the main window size
             *************************************************************************************/
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
            /**************************************************************************************
             * Enable the web server
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the web server port
             *************************************************************************************/
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
            /**************************************************************************************
             * Set the script output file path
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                CcddFileIOHandler.storePath(ccddMain,
                                            (String) parmVal,
                                            false,
                                            ModifiablePathInfo.SCRIPT_OUTPUT_PATH);
            }
        });

        // Shutdown the application following execution of the post-database connection commands
        argument.add(new CommandHandler("shutdown",
                                        "Shutdown the application after\n"
                                                    + "  completing the command line\n"
                                                    + "  commands (e.g., script\n"
                                                    + "  execution(s), table imports, or\n"
                                                    + "  table exports. The GUI is not\n"
                                                    + "  displayed",
                                        "",
                                        CommandLineType.NONE,
                                        0)
        {
            /**************************************************************************************
             * Set the flags to hide the user interface and shutdown the application following
             * completion of the command line commands
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Set the flag that hides the GUI so that dialog messages are redirected to the
                // command line
                ccddMain.setGUIHidden(true);

                // Set the flag that indicates the application should exit following execution of
                // the command line commands
                shutdownWhenComplete = true;
            }
        });

        // Execute script command
        argument.add(new CommandHandler("execute",
                                        "Execute script(s)",
                                        "[association name] or\n"
                                                             + "[\" or ']script file name["
                                                             + SCRIPT_MEMBER_SEPARATOR
                                                             + "table1\n"
                                                             + "  or Group:group1[+...[+tableN or\n"
                                                             + "  Group:groupN]]][;...][\" or ']",
                                        CommandLineType.NAME,
                                        10)
        {
            /**************************************************************************************
             * Execute a script. The application exits following completion of this command
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                List<Object[]> associations = new ArrayList<Object[]>();

                // Break the supplied string of semicolon-separated associations into the
                // individual associations
                String[] associationsArray = parmVal.toString().split(";");

                // Step through each association
                for (String associationString : associationsArray)
                {
                    String[] association;
                    String[] scriptAndTable;

                    // Get the index in the association string of the last occurrence of the file
                    // name separator character (e.g., '/' or '\')
                    int nameSepIndex = associationString.lastIndexOf(File.separator);

                    // The characters used in the script file path are operating system dependent.
                    // The script-member separator character can conflict with the path separators.
                    // The separation of the association's script file and members is handled based
                    // on if a potential conflict exists

                    // Check if the file name separator is present, and if so that it occurs before
                    // the script-member separator character (which may be a special character on
                    // the operating system path; e.g. the colon following a drive letter in a
                    // Windows path)
                    if (nameSepIndex == -1
                        || nameSepIndex < associationString.indexOf(SCRIPT_MEMBER_SEPARATOR))
                    {
                        // No special handling is required; break the supplied association into the
                        // script or script association name, and table(s)/group(s) (if any)
                        scriptAndTable = associationString.split(Pattern.quote(SCRIPT_MEMBER_SEPARATOR), 2);
                    }
                    // The script file path contains the script-member separator character
                    else
                    {
                        nameSepIndex++;

                        // Break the supplied association into the script or script association
                        // name, and table(s)/group(s) (if any), ignoring the first portion of the
                        // script path that contains the script-member separator character
                        scriptAndTable = associationString.substring(nameSepIndex)
                                                          .split(Pattern.quote(SCRIPT_MEMBER_SEPARATOR),
                                                                 2);

                        // Prepend the portion of the path that was skipped to the script path
                        scriptAndTable[0] = associationString.substring(0,
                                                                        nameSepIndex)
                                            + scriptAndTable[0];
                    }

                    // Remove any leading/trailing white space characters from the script name
                    String name = scriptAndTable[0].trim();

                    // Check if the name contains a file extension (it's a script file name)
                    if (name.contains("."))
                    {
                        // Set the script and table in the association array (assumes no
                        // table(s))
                        association = new String[] {"", "", name, " "};

                        // Check if one or more tables are provided
                        if (scriptAndTable.length == 2)
                        {
                            // Set the association tables. If multiple tables are provided then
                            // separate these with the expected separator string
                            association[AssociationsColumn.MEMBERS.ordinal()] = CcddScriptHandler.convertAssociationMembersFormat(scriptAndTable[1],
                                                                                                                                  true);
                        }

                        // Add the association to the list
                        associations.add(association);
                    }
                    // No file extension; assume this is an association name
                    else
                    {
                        // Get the association with this name
                        association = ccddMain.getScriptHandler().getScriptAssociationByName(name, null);

                        // Check if the association exists
                        if (association != null)
                        {
                            // Add the association to the list
                            associations.add(association);
                        }
                        // No association exists with this name
                        else
                        {
                            // Inform the user that the association name is invalid
                            ccddMain.getSessionEventLog().logFailEvent(null,
                                                                       "Unrecognized association name '"
                                                                             + name
                                                                             + "'",
                                                                       "<html><b>Unrecognized association name '"
                                                                                    + name
                                                                                    + "'");
                        }
                    }
                }

                // Check if the GUI isn't displayed
                if (ccddMain.isGUIHidden())
                {
                    // Execute the script association(s) and log the result
                    boolean[] isBad = ccddMain.getScriptHandler().getDataAndExecuteScript(null,
                                                                                          associations,
                                                                                          null);
                    ccddMain.getScriptHandler().logScriptCompletionStatus(associations, isBad);

                    // Step through the script execution fail flags
                    for (boolean flag : isBad)
                    {
                        // Check if the script execution failed
                        if (flag)
                        {
                            // Set the application return value to indicate a failure and stop
                            // searching
                            exitStatus = 1;
                            break;
                        }
                    }
                }
                // The GUI is displayed
                else
                {
                    ccddMain.getScriptHandler().getDataAndExecuteScriptInBackground(null,
                                                                                    associations,
                                                                                    ccddMain.getMainFrame());
                }
            }
        });

        // Import one or more tables
        argument.add(new CommandHandler("import",
                                        "Import tables, etc. from a\n"
                                                  + "  CSV, EDS, JSON, or XTCE file",
                                        "'<import sub-commands>'",
                                        CommandLineType.NAME,
                                        10,
                                        importArgument)
        {
            /**************************************************************************************
             * Import one or more tables from a file in CSV, EDS, JSON, or XTCE format
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Parse the import sub-commands
                parseCommand(-1,
                             -1,
                             CcddUtilities.parseCommandline(parmVal.toString()),
                             getSubArgument());

                // Check if a required sub-command is missing
                if (dataFile == null)
                {
                    // Display the error message
                    System.err.println("Error: Missing import file name\n");

                    // Display the command usage information and exit the application
                    displayUsageInformation();
                }

                // Check if the GUI isn't displayed
                if (ccddMain.isGUIHidden())
                {
                    // Import the table(s) from the specified file; check if the import operation
                    // fails
                    if (ccddMain.getFileIOHandler().importFile(dataFile,
                                                               false,
                                                               replaceExisting,
                                                               appendExistingFields,
                                                               useExistingFields,
                                                               null))
                    {
                        // Set the application return value to indicate a failure
                        exitStatus = 1;
                    }
                }
                // The GUI is displayed
                else
                {
                    // Import the table(s) from the specified file in a background thread
                    ccddMain.getFileIOHandler().importFile(dataFile,
                                                           false,
                                                           replaceExisting,
                                                           appendExistingFields,
                                                           useExistingFields,
                                                           ccddMain.getMainFrame());
                }
            }
        });

        // Export one or more tables
        argument.add(new CommandHandler("export",
                                        "Export tables, etc. in CSV,\n"
                                                  + "  EDS, JSON, or XTCE format",
                                        "'<export sub-commands>'",
                                        CommandLineType.NAME,
                                        10,
                                        exportArgument)
        {
            /**************************************************************************************
             * Export one or more tables to a file (or files) in CSV, EDS, JSON, or XTCE format
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Separate the export sub-commands
                // Parse the export sub-commands
                parseCommand(-1,
                             -1,
                             CcddUtilities.parseCommandline(parmVal.toString()),
                             getSubArgument());

                // Check if a required export sub-command is missing
                if ((filePath == null
                     && (fileExtn == FileExtension.EDS || fileExtn == FileExtension.XTCE))
                    || tablePaths == null)
                {
                    // Display the error message
                    System.err.println("Error: Missing export file name (EDS or XTCE) and/or table path(s)\n");

                    // Display the command usage information and exit the application
                    displayUsageInformation();
                }

                // Check if the GUI isn't displayed
                if (ccddMain.isGUIHidden())
                {
                    // Export the specified table(s); check if the export operation fails
                    if (ccddMain.getFileIOHandler().exportSelectedTables(filePath,
                                                                         tablePaths,
                                                                         true,
                                                                         singleFile,
                                                                         replaceMacros,
                                                                         includeReservedMsgIDs,
                                                                         includeProjectFields,
                                                                         includeVariablePaths,
                                                                         ccddMain.getVariableHandler(),
                                                                         separators,
                                                                         fileExtn,
                                                                         endianess,
                                                                         isHeaderBigEndian,
                                                                         version,
                                                                         validationStatus,
                                                                         classification1,
                                                                         classification2,
                                                                         classification3,
                                                                         scriptFileName != null,
                                                                         scriptFileName,
                                                                         null))
                    {
                        // Set the application return value to indicate a failure
                        exitStatus = 1;
                    }
                }
                // The GUI is displayed
                else
                {
                    // Export the specified table(s) in a background thread
                    ccddMain.getFileIOHandler().exportSelectedTablesInBackground(filePath,
                                                                                 tablePaths,
                                                                                 overwriteFile,
                                                                                 singleFile,
                                                                                 replaceMacros,
                                                                                 includeReservedMsgIDs,
                                                                                 includeProjectFields,
                                                                                 includeVariablePaths,
                                                                                 ccddMain.getVariableHandler(),
                                                                                 separators,
                                                                                 fileExtn,
                                                                                 endianess,
                                                                                 isHeaderBigEndian,
                                                                                 version,
                                                                                 validationStatus,
                                                                                 classification1,
                                                                                 classification2,
                                                                                 classification3,
                                                                                 scriptFileName != null,
                                                                                 scriptFileName,
                                                                                 ccddMain.getMainFrame());
                }
            }
        });

        // Import command - file path + name
        importArgument.add(new CommandHandler("fileName",
                                              "Import file name (required)",
                                              "import file name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the import file path + name
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                dataFile = new FileEnvVar[1];
                dataFile[0] = new FileEnvVar((String) parmVal);
            }
        });

        // Import command - replace existing tables
        importArgument.add(new CommandHandler("replaceExisting",
                                              "Replace existing table(s)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to replace existing tables
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                replaceExisting = (Boolean) parmVal;
            }
        });

        // Import command - append existing fields
        importArgument.add(new CommandHandler("appendExistingFields",
                                              "Append existing data field(s) if\n"
                                                                      + "  table exists. Only used if\n"
                                                                      + "  replaceExisting is true",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to append existing data fields
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                appendExistingFields = (Boolean) parmVal;
            }
        });

        // Import command - use existing fields
        importArgument.add(new CommandHandler("useExistingFields",
                                              "Use existing data field if\n"
                                                                   + "  imported one matches. Only used\n"
                                                                   + "  if replaceExisting and\n"
                                                                   + "  appendExistingFields are true",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to use existing data fields
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                useExistingFields = (Boolean) parmVal;
            }
        });

        // Export command - file path + name
        exportArgument.add(new CommandHandler("filePath",
                                              "Export file path + name (required\n"
                                                          + "  for EDS, XTCE)",
                                              "export file name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the export file path + name
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                filePath = (String) parmVal;
            }
        });

        // Export command - table path(s)
        exportArgument.add(new CommandHandler("tablePaths",
                                              "Table paths (required)",
                                              "<table1 or Group:group1[+...\n"
                                                                        + "  [+tableN or Group:groupN]]]\n"
                                                                        + "  [;...]>",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the table(s) to export
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Convert the table path list to the internal format used by script associations
                String internalFormat = CcddScriptHandler.convertAssociationMembersFormat((String) parmVal,
                                                                                          true);

                // Parse the converted table path list. The paths are handled the same as those for
                // script associations (hence the need for the conversion above); this includes
                // expanding group references
                tablePaths = ccddMain.getScriptHandler().getAssociationTablePaths(internalFormat,
                                                                                  new CcddGroupHandler(ccddMain,
                                                                                                       null,
                                                                                                       (ccddMain.isGUIHidden()
                                                                                                                               ? null
                                                                                                                               : ccddMain.getMainFrame())),
                                                                                  (ccddMain.isGUIHidden()
                                                                                                          ? null
                                                                                                          : ccddMain.getMainFrame()))
                                     .toArray(new String[0]);
            };
        });

        // Export command - overwrite existing file
        exportArgument.add(new CommandHandler("overwriteFile",
                                              "Overwrite existing file(s). If the\n"
                                                               + "  GUI is hidden then any existing\n"
                                                               + "  files are always overwritten",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to overwrite existing file(s)
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                overwriteFile = (Boolean) parmVal;
            }
        });

        // Export command - output to a single file
        exportArgument.add(new CommandHandler("singleFile",
                                              "Store in single file (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output all tables to a single file
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                singleFile = (Boolean) parmVal;
            }
        });

        // Export command - replace macros
        exportArgument.add(new CommandHandler("replaceMacros",
                                              "Replace macros with values",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to replace each macro with its corresponding value
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                replaceMacros = (Boolean) parmVal;
            }
        });

        // Export command - include reserved message IDs
        exportArgument.add(new CommandHandler("includeReservedMsgIDs",
                                              "Include reserved message ID list\n"
                                                                       + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the list of reserved message IDs
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeReservedMsgIDs = (Boolean) parmVal;
            }
        });

        // Export command - include reserved message IDs
        exportArgument.add(new CommandHandler("includeProjectFields",
                                              "Include project data fields",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the project data field definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeProjectFields = (Boolean) parmVal;
            }
        });

        // Export command - include variable paths
        exportArgument.add(new CommandHandler("includeVariablePaths",
                                              "Include variable path list (CSV,\n"
                                                                      + "  JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the list of variable paths
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeVariablePaths = (Boolean) parmVal;
            }
        });

        // Export command - file extension
        exportArgument.add(new CommandHandler("format",
                                              "Export file format",
                                              "csv, eds, json, or xtce\n"
                                                                    + "  (default: csv)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {FileExtension.CSV, FileExtension.EDS, FileExtension.JSON, FileExtension.XTCE},
                                              new String[] {"csv", "eds", "json", "xtce"})
        {
            /**************************************************************************************
             * Set the export file format
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                fileExtn = (FileExtension) parmVal;
            }
        });

        // Export command - data endianess
        exportArgument.add(new CommandHandler("endianess",
                                              "Endianess (EDS, XTCE)",
                                              "big or little (default: big)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {EndianType.BIG_ENDIAN, EndianType.LITTLE_ENDIAN},
                                              new String[] {"big", "little"})
        {
            /**************************************************************************************
             * Set the data endianess
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                endianess = (EndianType) parmVal;
            }
        });

        // Export command - telemetry and command headers endianess
        exportArgument.add(new CommandHandler("isHeaderBigEndian",
                                              "Force telemetry & command header\n"
                                                                   + "  to big endian (EDS, XTCE)",
                                              "true or false (default: true)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to force the telemetry and command headers to be big endian
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                isHeaderBigEndian = (Boolean) parmVal;
            }
        });

        // Export command - version number
        exportArgument.add(new CommandHandler("version",
                                              "Version (EDS, XTCE)",
                                              "text (default: 1.0)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the version number
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                version = (String) parmVal;
            }
        });

        // Export command - validation status
        exportArgument.add(new CommandHandler("validationStatus",
                                              "Validation status (EDS, XTCE)",
                                              "text (default: Working)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the validation status
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                validationStatus = (String) parmVal;
            }
        });

        // Export command - classification level 1
        exportArgument.add(new CommandHandler("classification1",
                                              "Classification level 1 (XTCE)",
                                              "text (default: DOMAIN)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the level 1 classification
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                classification1 = (String) parmVal;
            }
        });

        // Export command - classification level 2
        exportArgument.add(new CommandHandler("classification2",
                                              "Classification level 2 (XTCE)",
                                              "text (default: SYSTEM)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the level 2 classification
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                classification2 = (String) parmVal;
            }
        });

        // Export command - classification level 3
        exportArgument.add(new CommandHandler("classification3",
                                              "Classification level 3 (XTCE)",
                                              "text (default: INTERFACE)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the level 3 classification
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                classification3 = (String) parmVal;
            }
        });

        // Export command - external export script file name
        exportArgument.add(new CommandHandler("externalFileName",
                                              "External export script file name\n"
                                                                  + "  (EDS, XTCE)",
                                              "external export script file name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the external export script file name
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                scriptFileName = (String) parmVal;
            }
        });
    }

    /**********************************************************************************************
     * Parse and execute the main command line argument(s). The application exits if the shutdown
     * command is present and all command line commands have completed execution
     *
     * @param startPriority
     *            command priority boundary - ignore commands with a priority less than this value;
     *            -1 to start with the lowest priority command
     *
     * @param endPriority
     *            command priority boundary - ignore commands with a priority greater than this
     *            value; -1 to end with the highest priority command
     *********************************************************************************************/
    protected void parseCommand(int startPriority, int endPriority)
    {
        // Execute the commands that fall within the priority range
        parseCommand(startPriority, endPriority, args, argument);

        // Check if a script association execution command was performed
        if (endPriority == -1 && shutdownWhenComplete)
        {
            // Exit the application, supplying the execution status (= 1 if a failure occurred,
            // otherwise returns 0)
            ccddMain.exitApplication(false, exitStatus);
        }
    }

    /**********************************************************************************************
     * Parse and execute the command line argument(s)
     *
     * @param startPriority
     *            command priority boundary - ignore commands with a priority less than this value;
     *            -1 to start with the lowest priority command
     *
     * @param endPriority
     *            command priority boundary - ignore commands with a priority greater than this
     *            value; -1 to end with the highest priority command
     *
     * @param cmdLnArgs
     *            array of command line arguments to parse
     *
     * @param commandArgument
     *            list of valid command line commands
     *********************************************************************************************/
    private void parseCommand(int startPriority,
                              int endPriority,
                              String[] cmdLnArgs,
                              List<CommandHandler> commandArgument)
    {
        try
        {
            List<Integer> priorities = new ArrayList<Integer>();

            // Step through the valid commands
            for (CommandHandler cmd : commandArgument)
            {
                // Check if the priority list doesn't contain the priority value for this command
                if (!priorities.contains(cmd.priority))
                {
                    // Add the priority value to the list
                    priorities.add(cmd.priority);
                }
            }

            // Sort the priority values in ascending order
            Collections.sort(priorities);

            // Step through each valid priority value
            for (int priority : priorities)
            {
                // Step through the command line arguments
                for (int index = 0; index < cmdLnArgs.length; index++)
                {
                    // Get the next command line argument
                    String arg = cmdLnArgs[index];

                    // Check if the command doesn't start with a recognized delimiter
                    if (!(arg.startsWith("-") || arg.startsWith("/")))
                    {
                        throw new CCDDException("Unrecognized delimiter for command '"
                                                + arg
                                                + "'");
                    }

                    // Remove the delimiter
                    arg = arg.replaceFirst("^[-/]", "");

                    boolean isValidCmd = false;

                    // Step through the valid commands
                    for (CommandHandler cmd : commandArgument)
                    {
                        // Check if the command argument matches a valid command
                        if (arg.equalsIgnoreCase(cmd.command))
                        {
                            String parm = null;

                            // Set the flag to indicate this is a recognized command
                            isValidCmd = true;

                            // Check if the command requires a parameter
                            if (cmd.hasArgument())
                            {
                                // Increment the command line argument index to point to the
                                // command's parameter
                                index++;

                                // Check if the number of command line arguments isn't exceeded
                                if (index < cmdLnArgs.length)
                                {
                                    // Get the parameter associated with the command
                                    parm = cmdLnArgs[index];
                                }
                                // The end of the command line arguments is reached
                                else
                                {
                                    throw new CCDDException("Missing argument for command '"
                                                            + arg
                                                            + "'");
                                }
                            }

                            // Check if the command's priority matches the target priority and
                            // falls within the specified priority bounds
                            if (priority == cmd.priority
                                && (startPriority == -1 || cmd.priority >= startPriority)
                                && (endPriority == -1 || cmd.priority <= endPriority))
                            {
                                // Handle the command and check if it results in an error condition
                                if (cmd.handler(parm))
                                {
                                    throw new CCDDException();
                                }

                                // Stop searching since a matching command was found
                                break;
                            }
                        }
                    }

                    // Check if the command wasn't recognized
                    if (!isValidCmd)
                    {
                        throw new CCDDException("Unrecognized command '" + arg + "'");
                    }
                }
            }
        }
        catch (CCDDException ce)
        {
            // Check if a bad parameter was detected
            if (errorMessage != null)
            {
                // Display the error message
                System.err.println("Error: " + errorMessage + "\n");

                // Exit the application
                System.exit(0);
            }
            // Invalid command
            else
            {
                // Display the error message
                System.err.println("Error: " + ce.getMessage() + "\n");

                // Display the command usage information and exit the application
                displayUsageInformation();
            }
        }
    }

    /**********************************************************************************************
     * Display the command usage information and exit the application
     *********************************************************************************************/
    private void displayUsageInformation()
    {
        // Initialize the maximum usage parameter lengths
        int descLen = 0;
        int cmdLen = 0;
        int valLen = 0;

        // Sort the list of command arguments
        Collections.sort(argument, new Comparator<CommandHandler>()
        {
            /**************************************************************************************
             * Override the compare method to sort based on the command name
             *************************************************************************************/
            @Override
            public int compare(CommandHandler cmd1, CommandHandler cmd2)
            {
                return cmd1.command.compareTo(cmd2.command);
            }
        });

        // Step through each command
        for (CommandHandler cmd : argument)
        {
            // Store the longest command text
            cmdLen = Math.max(cmdLen, cmd.command.length());

            // Step through each portion of the value text
            for (String part : cmd.value.split("\n"))
            {
                // Store the longest value text
                valLen = Math.max(valLen, part.length());
            }

            // Step through each line of the description text
            for (String description : cmd.description.split("\n"))
            {
                // Store the longest description text
                descLen = Math.max(descLen, description.length());
            }

            // Check if the command has any sub-commands
            if (cmd.getSubArgument() != null)
            {
                // Sort the list of sub-command arguments
                Collections.sort(cmd.getSubArgument(), new Comparator<CommandHandler>()
                {
                    /******************************************************************************
                     * Override the compare method to sort based on the command name
                     *****************************************************************************/
                    @Override
                    public int compare(CommandHandler cmd1, CommandHandler cmd2)
                    {
                        return cmd1.command.compareTo(cmd2.command);
                    }
                });

                // Step through each sub-command
                for (CommandHandler subCmd : cmd.getSubArgument())
                {
                    // Store the longest command text
                    cmdLen = Math.max(cmdLen, subCmd.command.length() + 2);

                    // Step through each portion of the value text
                    for (String part : subCmd.value.split("\n"))
                    {
                        // Store the longest value text
                        valLen = Math.max(valLen, part.length());
                    }

                    // Step through each line of the description text
                    for (String description : subCmd.description.split("\n"))
                    {
                        // Store the longest description text
                        descLen = Math.max(descLen, description.length());
                    }
                }
            }
        }

        // Build the format strings using the maximum lengths
        String format = "  %-"
                        + cmdLen
                        + "."
                        + cmdLen
                        + "s  %-"
                        + valLen
                        + "."
                        + valLen
                        + "s  %-"
                        + descLen
                        + "."
                        + descLen
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
                                     "Command",
                                     "Value",
                                     "Description",
                                     dash,
                                     dash,
                                     dash);

        // Step through each command
        for (CommandHandler cmd : argument)
        {
            String[] description = cmd.description.split("\n");
            String[] value = cmd.value.split("\n");

            // Step through each line describing the command
            for (int index = 0; index < Math.max(description.length, value.length); index++)
            {
                // Append the command's usage text
                usage += String.format(format,
                                       (index == 0
                                                   ? cmd.command
                                                   : ""),
                                       (index < value.length
                                                             ? value[index]
                                                             : ""),
                                       (index < description.length
                                                                   ? description[index]
                                                                   : ""));
            }

            // Check if the command has any sub-commands
            if (cmd.getSubArgument() != null)
            {
                // Append the sub-command header
                String subCmdHdr = cmd.command + " sub-commands:";
                usage += "   "
                         + subCmdHdr
                         + "\n   "
                         + String.format("%-"
                                         + subCmdHdr.length()
                                         + "."
                                         + subCmdHdr.length()
                                         + "s\n",
                                         dash);

                // Step through each sub-command
                for (CommandHandler subCmd : cmd.getSubArgument())
                {
                    description = subCmd.description.split("\n");
                    value = subCmd.value.split("\n");

                    // Step through each line describing the sub-command
                    for (int index = 0; index < Math.max(description.length, value.length); index++)
                    {
                        // Append the command's usage text
                        usage += String.format(format,
                                               (index == 0
                                                           ? "  " + subCmd.command
                                                           : ""),
                                               (index < value.length
                                                                     ? value[index]
                                                                     : ""),
                                               (index < description.length
                                                                           ? description[index]
                                                                           : ""));
                    }
                }
            }
        }

        // Display the usage information
        System.out.println(usage);

        // Exit the application
        System.exit(0);
    }

    /**********************************************************************************************
     * Get the main window size parameter
     *
     * @param arg
     *            window size command line argument
     *
     * @param parm
     *            window dimensions
     *
     * @param min
     *            array containing the minimum main window width and minimum main window height
     *
     * @return Main window dimension value (null if an error is found)
     *********************************************************************************************/
    private Dimension handleSize(String arg, String parm, Object[] min)
    {
        Dimension val = null;

        // Split out the width and height strings
        String[] parts = parm.split("x");

        // Check that only two values are provided and that both contain only decimal digits
        if (parts.length == 2 && parts[0].matches("\\d+") && parts[1].matches("\\d+"))
        {
            // Convert the width and height strings to a Dimension variable
            val = new Dimension(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

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
        // Width or height string contains a non-numeral or the wrong number of values
        else
        {
            errorMessage = arg + " width or height not a number, or too many/few values";
        }

        return val;
    }

    /**********************************************************************************************
     * Get an integer value parameter that has minimum and maximum limits
     *
     * @param arg
     *            integer value command line argument
     *
     * @param parm
     *            integer value
     *
     * @param limit
     *            array containing the lower limit value (inclusive) and the upper limit value
     *            (inclusive)
     *
     * @return Integer value (null if an error is found)
     *********************************************************************************************/
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
                errorMessage = arg + " must be >= " + limit[0] + " and <= " + limit[1];
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

    /**********************************************************************************************
     * Get a color parameter
     *
     * @param arg
     *            color command line argument
     *
     * @param parm
     *            color in hexadecimal format or a color name
     *
     * @return Selected color in hexadecimal format (null if an error is found)
     *********************************************************************************************/
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
                // Check if the parameter is a valid color name; if so, get the RGB value (mask out
                // the alpha portion)
                val = ((Color) Color.class.getField(parm).get(null)).getRGB() & 0xffffff;
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

    /**********************************************************************************************
     * Get the selected option which must match one of a predefined list of options. The command
     * argument if forced to lower case when comparing to the acceptable options
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
     *********************************************************************************************/
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

            // Build the option list for the error message text, in case it's needed
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
