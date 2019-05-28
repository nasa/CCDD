/**
 * CFS Command and Data Dictionary command line argument handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.DATABASE_COMMENT_SEPARATOR;
import static CCDD.CcddConstants.DEFAULT_HIDE_DATA_TYPE;
import static CCDD.CcddConstants.DEFAULT_TYPE_NAME_SEP;
import static CCDD.CcddConstants.DEFAULT_VARIABLE_PATH_SEP;
import static CCDD.CcddConstants.HIDE_DATA_TYPE;
import static CCDD.CcddConstants.MIN_WINDOW_HEIGHT;
import static CCDD.CcddConstants.MIN_WINDOW_WIDTH;
import static CCDD.CcddConstants.SCRIPT_MEMBER_SEPARATOR;
import static CCDD.CcddConstants.TYPE_NAME_SEPARATOR;
import static CCDD.CcddConstants.VARIABLE_PATH_SEPARATOR;

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
import CCDD.CcddConstants.CommandLinePriority;
import CCDD.CcddConstants.CommandLineType;
import CCDD.CcddConstants.DatabaseComment;
import CCDD.CcddConstants.EndianType;
import CCDD.CcddConstants.EventLogMessageType;
import CCDD.CcddConstants.FileExtension;
import CCDD.CcddConstants.InternalTable.AssociationsColumn;
import CCDD.CcddConstants.ModifiablePathInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary command line argument handler class
 *************************************************************************************************/
public class CcddCommandLineHandler
{
    // Class reference
    private final CcddMain ccddMain;

    // Array containing the command line arguments provided by the user
    private final String[] args;

    // Lists containing the valid command line argument handlers
    private final List<CommandHandler> argument;
    private final List<CommandHandler> importArgument;
    private final List<CommandHandler> exportArgument;
    private final List<CommandHandler> createArgument;
    private final List<CommandHandler> deleteArgument;

    // Flag that indicates if further command line argument processing should not occur
    private boolean stopProcessingCommands;

    // Flag that indicates the application should exit once the command is complete. Used by the
    // script execution command
    private boolean shutdownWhenComplete;

    // Flag that indicates if the command line usage information should be displayed
    private boolean showUsage;

    // Import command parameters
    private final List<FileEnvVar> dataFile;
    private boolean replaceExistingTables;
    private boolean appendExistingFields;
    private boolean useExistingFields;
    private boolean openEditor;
    private boolean ignoreErrors;
    private boolean replaceExistingMacros;
    private boolean replaceExistingGroups;

    // Export command parameters
    private String filePath;
    private String[] tablePaths;
    private boolean overwriteFile;
    private boolean singleFile;
    private boolean includeAllTableTypes;
    private boolean includeAllDataTypes;
    private boolean includeAllInputTypes;
    private boolean includeAllMacros;
    private boolean includeBuildInformation;
    private boolean replaceMacros;
    private boolean includeReservedMsgIDs;
    private boolean includeProjectFields;
    private boolean includeVariablePaths;
    private boolean includeGroups;
    private boolean includeAssociations;
    private FileExtension fileExtn;
    private EndianType endianess;
    private boolean isHeaderBigEndian;
    private String version;
    private String validationStatus;
    private String classification1;
    private String classification2;
    private String classification3;
    private String scriptFileName;
    private String varPathSeparator;
    private String typeNameSeparator;
    private boolean excludeDataTypes;

    // Create project parameters
    private String createName;
    private String createOwner;
    private String createDescription;
    private String createRestore;

    // Delete project parameters
    private String deleteName;
    private boolean deleteContinueIfMissing;

    // Storage for the session event log and script output paths
    private final String sessionLogPath;
    private String scriptOutPath;

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
         * @throws Exception
         *             If a command parameter is invalid
         *****************************************************************************************/
        protected void handler(String parm) throws Exception
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
        }

        /******************************************************************************************
         * Placeholder for command type specific handler
         *
         * @param parmVal
         *            command parameter
         *
         * @throws Exception
         *             If an error occurs executing a command
         *****************************************************************************************/
        protected void doCommand(Object parmVal) throws Exception
        {
        }
    }

    /**********************************************************************************************
     * Command line argument handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param args
     *            array of command line arguments and associated parameters
     *********************************************************************************************/
    CcddCommandLineHandler(final CcddMain ccddMain, String[] args)
    {
        this.ccddMain = ccddMain;
        this.args = args;

        // Step through each command line argument
        for (int index = 0; index < args.length; index++)
        {
            // Check if the argument is bounded by single or double quotes
            if (args[index].matches("^'.*'$|^\".*\"$"))
            {
                // Remove the bounding quotes. This allows the use of single quotes around command
                // line arguments in operating systems that don't recognize single (e.g., Windows)
                // or double quotes as bounding characters
                this.args[index] = args[index].replaceFirst("^['\"]", "")
                                              .replaceFirst("['\"]$", "");
            }
        }

        argument = new ArrayList<CommandHandler>();
        importArgument = new ArrayList<CommandHandler>();
        exportArgument = new ArrayList<CommandHandler>();
        createArgument = new ArrayList<CommandHandler>();
        deleteArgument = new ArrayList<CommandHandler>();
        dataFile = new ArrayList<FileEnvVar>();
        replaceExistingTables = false;
        appendExistingFields = false;
        useExistingFields = false;
        filePath = "";
        tablePaths = null;
        overwriteFile = false;
        singleFile = false;
        includeBuildInformation = true;
        replaceMacros = false;
        includeReservedMsgIDs = false;
        includeProjectFields = false;
        includeVariablePaths = false;
        fileExtn = FileExtension.CSV;
        endianess = EndianType.BIG_ENDIAN;
        isHeaderBigEndian = true;
        version = null;
        validationStatus = null;
        classification1 = null;
        classification2 = null;
        classification3 = null;
        scriptFileName = null;
        createName = null;
        createOwner = null;
        createDescription = "";
        createRestore = null;
        deleteName = null;
        deleteContinueIfMissing = false;
        stopProcessingCommands = false;
        shutdownWhenComplete = false;
        showUsage = false;

        // Get the variable path separators and the show/hide data type flag from the program
        // preferences
        varPathSeparator = ccddMain.getProgPrefs().get(VARIABLE_PATH_SEPARATOR,
                                                       DEFAULT_VARIABLE_PATH_SEP);
        typeNameSeparator = ccddMain.getProgPrefs().get(TYPE_NAME_SEPARATOR,
                                                        DEFAULT_TYPE_NAME_SEP);
        excludeDataTypes = Boolean.parseBoolean(ccddMain.getProgPrefs().get(HIDE_DATA_TYPE,
                                                                            DEFAULT_HIDE_DATA_TYPE));

        // Store the session event log and script output paths, in case these are modified by a
        // command line command
        sessionLogPath = ccddMain.getProgPrefs().get(ModifiablePathInfo.SESSION_LOG_FILE_PATH.getPreferenceKey(), "");
        scriptOutPath = ccddMain.getProgPrefs().get(ModifiablePathInfo.SCRIPT_OUTPUT_PATH.getPreferenceKey(), "");

        // Display application version information command
        argument.add(new CommandHandler("version",
                                        "Display CCDD version and exit",
                                        "",
                                        CommandLineType.NONE,
                                        CommandLinePriority.PRE_START.getStartPriority())
        {
            /**************************************************************************************
             * Display the application version and build date, then exit the program
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
            {
                System.out.println("CCDD " + ccddMain.getCCDDVersionInformation());
                System.exit(0);
            }
        });

        // Event log file path command. This command, if present, is executed prior to all other
        // commands (except the version command), regardless of relative priorities
        argument.add(new CommandHandler("logPath",
                                        "Set event log file path. This\n"
                                                   + "  path is in effect for the\n"
                                                   + "  current session only",
                                        "file path",
                                        CommandLineType.NAME,
                                        CommandLinePriority.PRE_START.getStartPriority() + 1)
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

        // Open project command
        argument.add(new CommandHandler("project",
                                        "Set the CCDD project to open",
                                        "project name",
                                        CommandLineType.NAME,
                                        CommandLinePriority.SET_UP.getStartPriority() + 6)
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
                                        CommandLinePriority.SET_UP.getStartPriority())
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
                                        CommandLinePriority.SET_UP.getStartPriority())
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
                                        CommandLinePriority.SET_UP.getStartPriority())
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
                                        CommandLinePriority.SET_UP.getStartPriority())
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
                                        CommandLinePriority.SET_UP.getStartPriority())
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
                                        CommandLinePriority.SET_UP.getStartPriority(),
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 1,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 3)
        {
            /**************************************************************************************
             * Set the look and feel
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 3,
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 2)
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
                                        CommandLinePriority.SET_UP.getStartPriority() + 2)
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
        argument.add(new CommandHandler("scriptOutPath",
                                        "Set the script output file path. This\n"
                                                         + "  command may be used more than once.\n"
                                                         + "  This path is in effect for the\n"
                                                         + "  current session only",
                                        "file path",
                                        CommandLineType.NAME,
                                        CommandLinePriority.DB_DEPENDENT.getStartPriority())
        {
            /**************************************************************************************
             * Store the script output file path
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                scriptOutPath = (String) parmVal;
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
                                        CommandLinePriority.PRE_START.getStartPriority())
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
                                        CommandLinePriority.DB_DEPENDENT.getStartPriority() + 2)
        {
            /**************************************************************************************
             * Execute a script
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
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
                        scriptAndTable[0] = associationString.substring(0, nameSepIndex)
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
                                                                       "<html><b>Unrecognized association name '</b>"
                                                                                    + name
                                                                                    + "<b>'");
                        }
                    }
                }

                // Set the script output path, in case it's been changed by a command line command.
                // If the script is executed in the background then when it completes execution the
                // script output path is restored to the program start-up value. Therefore it's set
                // to the value as updated via command line command prior to each execution command
                CcddFileIOHandler.storePath(ccddMain,
                                            scriptOutPath,
                                            false,
                                            ModifiablePathInfo.SCRIPT_OUTPUT_PATH);

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
                            throw new Exception();
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
                                        "Import tables, etc. from a CSV, EDS,\n"
                                                  + "  JSON, or XTCE file",
                                        "'<import sub-commands>'",
                                        CommandLineType.NAME,
                                        CommandLinePriority.DB_DEPENDENT.getStartPriority() + 1,
                                        importArgument)
        {
            /**************************************************************************************
             * Import one or more tables from a file in CSV, EDS, JSON, or XTCE format
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
            {
                // Check if the GUI is hidden
                if (ccddMain.isGUIHidden())
                {
                    // Get the lock status of the project. The project isn't locked by this
                    // instance of CCDD if the GUI is hidden, so if the lock status is set then
                    // another instance of CCDD has the database open
                    Boolean lockStatus = ccddMain.getDbControlHandler()
                                                 .getDatabaseLockStatus(ccddMain.getDbControlHandler()
                                                                                .getDatabaseName());

                    // Check if the project database status can't be obtained
                    if (lockStatus == null)
                    {
                        throw new Exception();
                    }

                    // Check if the project database is locked
                    if (lockStatus)
                    {
                        throw new Exception("Cannot import; project '"
                                            + ccddMain.getDbControlHandler().getProjectName()
                                            + "' is open in another CCDD instance");
                    }
                }

                // Check if the user has write access for the project
                if (ccddMain.getDbControlHandler().isAccessReadWrite())
                {
                    // Parse the import sub-commands
                    parseCommand(-1,
                                 -1,
                                 CcddUtilities.parseCommandLine(parmVal.toString()),
                                 getSubArgument());

                    // Check if a required sub-command is missing
                    if (dataFile == null || dataFile.isEmpty())
                    {
                        // Set the flag to display the command line usage information and the error
                        // message
                        showUsage = true;
                        throw new Exception("Missing import file name");
                    }

                    // Check if the GUI isn't displayed
                    if (ccddMain.isGUIHidden())
                    {
                        // Import the table(s) from the specified file; check if the import
                        // operation fails
                        if (ccddMain.getFileIOHandler().importFile(dataFile.toArray(new FileEnvVar[0]),
                                                                   false,
                                                                   replaceExistingTables,
                                                                   appendExistingFields,
                                                                   useExistingFields,
                                                                   false,
                                                                   ignoreErrors,
                                                                   replaceExistingMacros,
                                                                   replaceExistingGroups,
                                                                   null))
                        {
                            throw new Exception();
                        }
                    }
                    // The GUI is displayed
                    else
                    {
                        // Import the table(s) from the specified file in a background thread
                        ccddMain.getFileIOHandler().importFileInBackground(dataFile.toArray(new FileEnvVar[0]),
                                                                           false,
                                                                           replaceExistingTables,
                                                                           appendExistingFields,
                                                                           useExistingFields,
                                                                           openEditor,
                                                                           ignoreErrors,
                                                                           replaceExistingMacros,
                                                                           replaceExistingGroups,
                                                                           ccddMain.getMainFrame());
                    }
                }
                // The user doesn't have write access
                else
                {
                    // Display the error message
                    throw new Exception("Import disabled; user lacks write access for project '"
                                        + ccddMain.getDbControlHandler().getProjectName()
                                        + "'");
                }
            }
        });

        // Import command - file path + name
        importArgument.add(new CommandHandler("fileName",
                                              "Import path+file name(s)",
                                              "[path1]nameA[+nameB[+...]\n"
                                                                          + "  [;[path2]nameC[+...][;...]]\n"
                                                                          + "  (required)",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the import file path+name(s). Multiple files in the same path can be specified
             * by separating the first path+name from the subsequent names with a plus (+).
             *
             * Example: -fileName "/path/to/fileA+fileB+subPath/fileC" specifies files
             * /path/to/fileA, /path/to/fileB, and /path/to/subPath/fileC.
             *
             * Multiple path groups can be specified by separating each grouping with a semi-colon
             * (;).
             *
             * Example: -fileName "/path1/to/fileA+fileB;/path2/to/fileC" specifies files
             * /path1/to/fileA, /path1/to/fileB, and /path2/to/fileC.
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                // Step through each path group
                for (String filePath : ((String) parmVal).split(";"))
                {
                    String path = null;

                    // Step through each file name for this path group
                    for (String fileName : filePath.split("\\+"))
                    {
                        // Check if the path has been determined (i.e., this is the first file in
                        // the path group)
                        if (path == null)
                        {
                            // Create the file class and add it to the list
                            FileEnvVar file = new FileEnvVar(fileName);
                            dataFile.add(file);

                            // Extract the file's path
                            path = file.getAbsolutePath().substring(0,
                                                                    file.getAbsolutePath().length()
                                                                       - file.getName().length());
                        }
                        // The path has been determined by the first file
                        else
                        {
                            // Append the path to the file name and add the file to the list
                            dataFile.add(new FileEnvVar(path + File.separator + fileName));
                        }
                    }
                }
            }
        });

        // Import command - replace existing tables
        importArgument.add(new CommandHandler("replaceExistingTables",
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
                replaceExistingTables = (Boolean) parmVal;
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
                                              "Use existing data field if imported\n"
                                                                   + "  one matches. Only used if\n"
                                                                   + "  replaceExisting and\n"
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

        // Import command - open table editor
        importArgument.add(new CommandHandler("openEditor",
                                              "Open an editor for each imported table",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to open an editor for each imported table
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                openEditor = (Boolean) parmVal;
            }
        });

        // Import command - ignore errors
        importArgument.add(new CommandHandler("ignoreErrors",
                                              "Ignore all import file errors and\n"
                                                              + "  continue processing the file\n"
                                                              + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to ignore all errors in the import file
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                ignoreErrors = (Boolean) parmVal;
            }
        });

        // Import command - replace existing macro values
        importArgument.add(new CommandHandler("replaceExistingMacros",
                                              "Replace existing macros values",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to replace existing macro values
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                replaceExistingMacros = (Boolean) parmVal;
            }
        });

        // Import command - replace existing group definitions
        importArgument.add(new CommandHandler("replaceExistingGroups",
                                              "Replace existing group definitions",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to replace existing groups definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                replaceExistingGroups = (Boolean) parmVal;
            }
        });

        // Export one or more tables
        argument.add(new CommandHandler("export",
                                        "Export tables, etc. in CSV, EDS, JSON,\n"
                                                  + "  or XTCE format",
                                        "'<export sub-commands>'",
                                        CommandLineType.NAME,
                                        CommandLinePriority.DB_DEPENDENT.getStartPriority() + 1,
                                        exportArgument)
        {
            /**************************************************************************************
             * Export one or more tables to a file (or files) in CSV, EDS, JSON, or XTCE format
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
            {
                // Parse the export sub-commands
                parseCommand(-1,
                             -1,
                             CcddUtilities.parseCommandLine(parmVal.toString()),
                             getSubArgument());

                // Check if a required export sub-command is missing
                if ((filePath.isEmpty()
                     && (fileExtn == FileExtension.EDS || fileExtn == FileExtension.XTCE
                         || (singleFile
                             && (fileExtn == FileExtension.CSV || fileExtn == FileExtension.JSON))))
                    || tablePaths == null)
                {
                    // Set the flag to display the command line usage information and the error
                    // message
                    showUsage = true;
                    throw new Exception("Missing export file name and/or table path(s)");
                }

                // Create the variable path separator array
                String[] separators = new String[] {varPathSeparator,
                                                    typeNameSeparator,
                                                    String.valueOf(excludeDataTypes)};

                // Check if the GUI isn't displayed
                if (ccddMain.isGUIHidden())
                {
                    // Export the specified table(s); check if the export operation fails
                    if (ccddMain.getFileIOHandler().exportSelectedTables(filePath,
                                                                         tablePaths,
                                                                         overwriteFile,
                                                                         singleFile,
                                                                         includeBuildInformation,
                                                                         replaceMacros,
                                                                         includeAllTableTypes,
                                                                         includeAllDataTypes,
                                                                         includeAllInputTypes,
                                                                         includeAllMacros,
                                                                         includeReservedMsgIDs,
                                                                         includeProjectFields,
                                                                         includeGroups,
                                                                         includeAssociations,
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
                        throw new Exception();
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
                                                                                 includeBuildInformation,
                                                                                 replaceMacros,
                                                                                 includeAllTableTypes,
                                                                                 includeAllDataTypes,
                                                                                 includeAllInputTypes,
                                                                                 includeAllMacros,
                                                                                 includeReservedMsgIDs,
                                                                                 includeProjectFields,
                                                                                 includeGroups,
                                                                                 includeAssociations,
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

        // Export command - file path + name
        exportArgument.add(new CommandHandler("filePath",
                                              "Export file path + name (required for\n"
                                                          + "  EDS, XTCE, and for CSV, JSON if\n"
                                                          + "  exporting to a single file). This\n"
                                                          + "  path is in effect for the current\n"
                                                          + "  session only",
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
                                                                                  true,
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

        // Export command - include all table type definitions
        exportArgument.add(new CommandHandler("includeAllTableTypes",
                                              "Include all table type definitions\n"
                                                                      + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the table type definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeAllTableTypes = (Boolean) parmVal;
            }
        });

        // Export command - include all data type definitions
        exportArgument.add(new CommandHandler("includeAllDataTypes",
                                              "Include all data type definitions\n"
                                                                     + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the data type definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeAllDataTypes = (Boolean) parmVal;
            }
        });

        // Export command - include all user-defined input type definitions
        exportArgument.add(new CommandHandler("includeAllInputTypes",
                                              "Include all user-defined input type\n"
                                                                      + "  definitions (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the user-defined input type definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeAllInputTypes = (Boolean) parmVal;
            }
        });

        // Export command - include all macro definitions
        exportArgument.add(new CommandHandler("includeAllMacros",
                                              "Include all macro definitions\n"
                                                                  + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the list of all macro definitions
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeAllMacros = (Boolean) parmVal;
            }
        });

        // Export command - include build information
        exportArgument.add(new CommandHandler("includeBuildInformation",
                                              "Include the CCDD version, project,\n"
                                                                         + "  host, and user information",
                                              "true or false (default: true)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to include the build information
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeBuildInformation = (Boolean) parmVal;
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

        // Export command - include groups
        exportArgument.add(new CommandHandler("includeGroups",
                                              "Include group definitions and data\n"
                                                               + "  fields (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the group definitions and data fields
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeGroups = (Boolean) parmVal;
            }
        });

        // Export command - include script associations
        exportArgument.add(new CommandHandler("includeAssociations",
                                              "Include script associations\n"
                                                                     + "  (CSV, JSON)",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the flag to output the script associations
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                includeAssociations = (Boolean) parmVal;
            }
        });

        // Export command - include variable paths
        exportArgument.add(new CommandHandler("includeVariablePaths",
                                              "Include variable path list (CSV, JSON)",
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

        // Export command - variable path separator
        exportArgument.add(new CommandHandler("variableSep",
                                              "variable path separator character(s)",
                                              "default: ,",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the variable path separator
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                varPathSeparator = (String) parmVal;
            }
        });

        // Export command - data type/variable name separator
        exportArgument.add(new CommandHandler("typeNameSep",
                                              "Data type/variable name separator\n"
                                                             + "  character(s)",
                                              "default: .",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the data type/variable name separator
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                typeNameSeparator = (String) parmVal;
            }
        });

        // Export command - hide data type
        exportArgument.add(new CommandHandler("hideDataType",
                                              "Hide the data type in the variable\n"
                                                              + "  paths",
                                              "true or false (default: false)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {true, false},
                                              new String[] {"true", "false"})
        {
            /**************************************************************************************
             * Set the exclude data types flag
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                excludeDataTypes = Boolean.parseBoolean((String) parmVal);
            }
        });

        // Export command - file extension
        exportArgument.add(new CommandHandler("format",
                                              "Export file format",
                                              "csv, eds, json, or xtce\n"
                                                                    + "  (default: csv)",
                                              CommandLineType.OPTION,
                                              0,
                                              new Object[] {FileExtension.CSV,
                                                            FileExtension.EDS,
                                                            FileExtension.JSON,
                                                            FileExtension.XTCE},
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
                                              "Force telemetry & command header to\n"
                                                                   + "  big endian (EDS, XTCE)",
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

        // Create a new project database
        argument.add(new CommandHandler("create",
                                        "Create a new project database",
                                        "'<create sub-commands>'",
                                        CommandLineType.NAME,
                                        CommandLinePriority.SET_UP.getStartPriority() + 5,
                                        createArgument)
        {
            /**************************************************************************************
             * Create a new project database
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
            {
                // Parse the create project database sub-commands
                parseCommand(-1,
                             -1,
                             CcddUtilities.parseCommandLine(parmVal.toString()),
                             getSubArgument());

                // Check if a required create sub-command is missing or blank
                if (createName == null || createName.isEmpty())
                {
                    // Set the flag to display the command line usage information and the error
                    // message
                    showUsage = true;
                    throw new Exception("Missing project name");
                }

                // Check if no owner is specified
                if (createOwner == null || createOwner.isEmpty())
                {
                    // Set the owner and administrator to the current user
                    createOwner = ccddMain.getDbControlHandler().getUser();
                }

                // Check if a connection is made to the PostgreSQL server
                if (!ccddMain.getDbControlHandler().connectToServer())
                {
                    // Create the project database; check if creating the database fails. The
                    // project can't be created in a background thread since the operation may not
                    // be complete before subsequent database operations are commanded
                    if (!ccddMain.getDbControlHandler().createDatabase(createName,
                                                                       createOwner,
                                                                       createOwner,
                                                                       createDescription))
                    {
                        throw new Exception();
                    }

                    // Check if a backup file was chosen to restore
                    if (createRestore != null)
                    {
                        // Restore the backup file to the newly created project database
                        ccddMain.getFileIOHandler().restoreDatabaseFromFile(createRestore,
                                                                            createName,
                                                                            createOwner,
                                                                            createDescription);
                    }
                }
                // The attempt to connect to the PostgreSQL server failed
                else
                {
                    throw new Exception();
                }
            }
        });

        // Create project command - project name
        createArgument.add(new CommandHandler("name",
                                              "Name of project to create",
                                              "project name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the project name
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                createName = (String) parmVal;
            }
        });

        // Create project command - project owner
        createArgument.add(new CommandHandler("owner",
                                              "Owner of the new project",
                                              "project owner",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the project owner
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                createOwner = (String) parmVal;
            }
        });

        // Create project command - project description
        createArgument.add(new CommandHandler("description",
                                              "Description of the new project",
                                              "project description",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the project description
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                createDescription = (String) parmVal;
            }
        });

        // Restore project backup command - backup file name
        createArgument.add(new CommandHandler("restore",
                                              "Backup file to restore",
                                              "backup file name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the backup file name to restore
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                createRestore = (String) parmVal;
            }
        });

        // Delete an existing project database
        argument.add(new CommandHandler("delete",
                                        "Delete an existing project database",
                                        "<delete sub-commands>",
                                        CommandLineType.NAME,
                                        CommandLinePriority.SET_UP.getStartPriority() + 4,
                                        deleteArgument)
        {
            /**************************************************************************************
             * Delete an existing project database
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal) throws Exception
            {
                // Parse the delete project database sub-commands
                parseCommand(-1,
                             -1,
                             CcddUtilities.parseCommandLine(parmVal.toString()),
                             getSubArgument());

                // Check if a required create sub-command is missing or blank
                if (deleteName == null || deleteName.isEmpty())
                {
                    // Set the flag to display the command line usage information and the error
                    // message
                    showUsage = true;
                    throw new Exception("Missing project name");
                }

                CcddDbControlHandler dbControl = ccddMain.getDbControlHandler();

                // Check if a connection is made to the PostgreSQL server
                if (!ccddMain.getDbControlHandler().connectToServer())
                {
                    boolean isFound = false;

                    // Step through the information for each CCDD database in the server
                    for (String userDbInfo : dbControl.queryDatabaseList(ccddMain.getMainFrame()))
                    {
                        // Separate the information retrieved into the database name and its
                        // comment, then parse the comment into its separate fields
                        String[] nameAndComment = userDbInfo.split(DATABASE_COMMENT_SEPARATOR, 2);
                        String comment[] = dbControl.parseDatabaseComment(nameAndComment[0],
                                                                          nameAndComment[1]);

                        // Check if the project names match (i.e., the project exists)
                        if (comment[DatabaseComment.PROJECT_NAME.ordinal()].equals(deleteName))
                        {
                            // Check if the user has administrative level access for the project
                            if (dbControl.getUserAdminAccess().contains(dbControl.convertProjectNameToDatabase(deleteName)))
                            {
                                // Attempt to delete the specified project database. The user must
                                // be an administrator for the project to perform this operation
                                if (dbControl.deleteDatabase(deleteName))
                                {
                                    // The database exists, but can't be deleted (e.g., another
                                    // application has the database open)
                                    throw new Exception();
                                }
                            }
                            // The user doesn't have administrative access
                            else
                            {
                                throw new Exception("Delete disabled; user lacks write access for project '"
                                                    + deleteName);
                            }

                            // Set the flag to indicate a match was found and stop searching
                            isFound = true;
                            break;
                        }
                    }

                    // Check if no project with the supplied name exists in the server and the
                    // error is not suppressed
                    if (!isFound && !deleteContinueIfMissing)
                    {
                        throw new Exception("Delete project failed; project '"
                                            + deleteName
                                            + "' does not exist");
                    }
                }
                // The attempt to connect to the PostgreSQL server failed
                else
                {
                    throw new Exception();
                }
            }
        });

        // Delete project command - project name
        deleteArgument.add(new CommandHandler("name",
                                              "Name of the project to delete",
                                              "project name",
                                              CommandLineType.NAME,
                                              0)
        {
            /**************************************************************************************
             * Set the project name
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                deleteName = (String) parmVal;
            }
        });

        // Delete project command - continue executing commands if the project doesn't exist
        deleteArgument.add(new CommandHandler("continueIfMissing",
                                              "Continue to process commands if the\n"
                                                                   + "  project doesn't exist",
                                              "",
                                              CommandLineType.NONE,
                                              0)
        {
            /**************************************************************************************
             * Set the flag to indicate that command execution should continue if the project
             * doesn't exist
             *************************************************************************************/
            @Override
            protected void doCommand(Object parmVal)
            {
                deleteContinueIfMissing = true;
            }
        });
    }

    /**********************************************************************************************
     * Parse and execute the main command line argument(s). The application exits if the shutdown
     * command is present and all command line commands have completed execution
     *
     * @param priority
     *            command priority range - execute only those commands with a priority &gt;= to the
     *            start value (-1 to start with the lowest priority command) and &lt;= to the end
     *            value (-1 to end with the highest priority command)
     *********************************************************************************************/
    protected void parseCommand(CommandLinePriority priority)
    {
        // Check if no previous error has caused command line processing to cease
        if (!stopProcessingCommands)
        {
            // Execute the commands that fall within the priority range
            parseCommand(priority.getStartPriority(), priority.getEndPriority(), args, argument);

            // Check if the project-specific commands have been completed
            if (priority.getEndPriority() == -1)
            {
                // Perform any clean-up steps required after processing the command line commands
                postCommandCleanUp(0);
            }
        }
    }

    /**********************************************************************************************
     * Parse and execute the command line argument(s)
     *
     * @param startPriority
     *            command priority boundary - ignore commands with a priority less than this value;
     *            -1 starts with the lowest priority command
     *
     * @param endPriority
     *            command priority boundary - ignore commands with a priority greater than this
     *            value; -1 ends with the highest priority command
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
                        throw new Exception("Unrecognized delimiter for command '"
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
                                    throw new Exception("Missing argument for command '"
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
                                // Handle the command
                                cmd.handler(parm);

                                // Stop searching since a matching command was found
                                break;
                            }
                        }
                    }

                    // Check if the command wasn't recognized
                    if (!isValidCmd)
                    {
                        // Set the flag to display the command line usage information, display the
                        // error message, and exit
                        showUsage = true;
                        throw new Exception("Unrecognized command '" + arg + "'");
                    }
                }
            }
        }
        catch (Exception ce)
        {
            // Check if an error message is provided
            if (ce.getMessage() != null && !ce.getMessage().isEmpty())
            {
                // Display the error message
                System.err.println("Error: " + ce.getMessage() + "\n");
            }

            // Check if the command line usage information should be displayed
            if (showUsage)
            {
                // Display the command usage information and exit the application
                displayUsageInformation();
            }

            // Perform any clean-up steps and exit the program
            postCommandCleanUp(1);
        }
    }

    /**********************************************************************************************
     * Perform any clean-up steps following command line argument execution or failure
     *
     * @param exitStatus
     *            0 if all commands executed successfully; 1 if an error occurred
     *********************************************************************************************/
    protected void postCommandCleanUp(int exitStatus)
    {
        // Restore the original session event log path, in case it was changed via a command line
        // command
        CcddFileIOHandler.storePath(ccddMain,
                                    sessionLogPath,
                                    false,
                                    ModifiablePathInfo.SESSION_LOG_FILE_PATH);

        // Check if the application should be terminated following execution of the
        // project-specific commands (script execution, export, or import) (note that the GUI is
        // hidden if this flag is set), or if an error occurred and the GUI is hidden (if the GUI
        // is visible then the error is handled via error dialog and event log message)
        if (shutdownWhenComplete || (exitStatus == 1 && ccddMain.isGUIHidden()))
        {
            // Restore the original table export and script output paths (in case either of these
            // were changed via a command line command). If the GUI is visible then the script
            // execute and table export commands are performed as background operations. The
            // background operation is responsible for resetting the affected path once the
            // operation completes
            ccddMain.restoreTableExportPath();
            ccddMain.restoreScriptOutputPath();

            // Exit the application, supplying the execution status (= 1 if a failure occurred,
            // otherwise returns 0)
            ccddMain.exitApplication(false, exitStatus);
        }

        // Check if an error occurred
        if (exitStatus != 0)
        {
            // Set the flag to stop further command line argument processing. This is set here,
            // versus when the exception is thrown, since the main class can call this method
            // directly
            stopProcessingCommands = true;
        }
    }

    /**********************************************************************************************
     * Display the command usage information
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
     *
     * @throws Exception
     *             If the width or height value is not a number or a value is missing
     *********************************************************************************************/
    private Dimension handleSize(String arg, String parm, Object[] min) throws Exception
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
            throw new Exception(arg + " width or height not a number, or too many/few values");
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
     *
     * @throws Exception
     *             If the minimum or maximum value is not a number
     *********************************************************************************************/
    private Integer handleMinMax(String arg, String parm, Object[] limit) throws Exception
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
                throw new Exception(arg + " must be >= " + limit[0] + " and <= " + limit[1]);
            }
        }
        // String contains a non-numeral
        else
        {
            throw new Exception(arg + " not a number");
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
     *
     * @throws Exception
     *             If the color value is not in the expected hexadecimal format or is not a
     *             recognized color name
     *********************************************************************************************/
    private Integer handleColor(String arg, String parm) throws Exception
    {
        Integer val = null;

        try
        {
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
                // Check if the parameter is a valid color name; if so, get the RGB value (mask out
                // the alpha portion)
                val = ((Color) Color.class.getField(parm).get(null)).getRGB() & 0xffffff;
            }
        }
        catch (Exception e)
        {
            // Parameter is not a hexadecimal color or color name
            throw new Exception(arg
                                + " must be a color name or in the format "
                                + "'0x######' where '#' is a hexadecimal digit");
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
     *
     * @throws Exception
     *             If the supplied value is not one of those in the predefined list
     *********************************************************************************************/
    private Object handleOption(String arg,
                                String parm,
                                String[] inputOptions,
                                Object[] outputValues) throws Exception
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
            throw new Exception(arg + " must be one of the following:" + valid);
        }

        return val;
    }
}
