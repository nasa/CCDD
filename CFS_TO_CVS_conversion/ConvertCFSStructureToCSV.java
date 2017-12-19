/**
 * CFS system and application telemetry and command structure table header to
 * comma separated values (CSV) file conversion handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */

package cfs_cvs_convert;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/******************************************************************************
 * CFS system and application telemetry and command structure table header to
 * comma separated values (CSV) file conversion handler class
 *****************************************************************************/
public class ConvertCFSStructureToCSV
{
    private List<String> structDataIn;
    private List<String> structDataOut;
    private List<String> macros;

    private String description;

    private int macroIndex;

    // Characters used to encompass a macro name
    private static final String MACRO_IDENTIFIER = "##";

    /**************************************************************************
     * CFS system and application telemetry and command structure table header
     * to CSV file conversion handler class constructor
     *
     * Notes & assumptions:
     *
     * 1) Data types must be single words (e.g., 'unsigned long' is invalid)
     *
     * 2) Multiple variables may be defined following a single data type
     * declaration, but must be on the same row in the file
     *
     * 3) A line continuation character ('\') is ignored; the text on the
     * following row is not appended to the current row's text TODO CHANGE
     * THIS?
     *
     * 4) Macro formulas used as variable array sizes are replaced by a
     * program-generated macro name. Any macros within the formula, along with
     * the program-generated macro, are added to the macro definitions in the
     * output file.
     *
     * 5) Compiler macros within structures are ignored. Any variable
     * definition within the bounds of the macro is added to the output
     * structure. The variable's description has text added to indicate it
     * falls within a compiler macro section.
     *
     * @param cfePath
     *            cfe folder path
     *
     * @param appsPath
     *            apps folder path
     *
     * @param outPath
     *            output folder path
     *************************************************************************/
    ConvertCFSStructureToCSV(String cfePath, String appsPath, String outPath)
    {
        // Check if the cfe path is provided
        if (cfePath != null)
        {
            // Convert the cFE systems
            convertFile("ES", "cFE", outPath + "cFE_ES_tables.csv", cfePath + "fsw/cfe-core/src/inc/cfe_es_msg.h");
            convertFile("EVS", "cFE", outPath + "cFE_EVS_tables.csv", cfePath + "fsw/cfe-core/src/inc/cfe_evs_msg.h");
            convertFile("SB", "cFE", outPath + "cFE_SB_tables.csv", cfePath + "fsw/cfe-core/src/inc/cfe_sb_msg.h");
            convertFile("TBL", "cFE", outPath + "cFE_TBL_tables.csv", cfePath + "fsw/cfe-core/src/inc/cfe_tbl_msg.h");
            convertFile("TIME", "cFE", outPath + "cFE_TIME_tables.csv", cfePath + "fsw/cfe-core/src/inc/cfe_time_msg.h");
        }

        // Check if the apps path is provided
        if (appsPath != null)
        {
            // Convert the cFS apps
            convertFile("CF", "cFS application", outPath + "cFS_CF_tables.csv", appsPath + "cf/fsw/src/cf_msg.h");
            convertFile("CI", "cFS application", outPath + "cFS_CI_tables.csv", appsPath + "ci/fsw/src/ci_app.h", appsPath + "ci/fsw/src/ci_hktlm.h");
            convertFile("CI_lab", "cFS application", outPath + "cFS_CI_lab_tables.csv", appsPath + "ci_lab/fsw/src/ci_lab_msg.h");
            convertFile("CS", "cFS application", outPath + "cFS_CS_tables.csv", appsPath + "cs/fsw/src/cs_msg.h");
            convertFile("DS", "cFS application", outPath + "cFS_DS_tables.csv", appsPath + "ds/fsw/src/ds_msg.h");
            convertFile("FM", "cFS application", outPath + "cFS_FM_tables.csv", appsPath + "fm/fsw/src/fm_msg.h");
            convertFile("HK", "cFS application", outPath + "cFS_HK_tables.csv", appsPath + "hk/fsw/src/hk_msg.h");
            convertFile("HS", "cFS application", outPath + "cFS_HS_tables.csv", appsPath + "hs/fsw/src/hs_msg.h");
            convertFile("LC", "cFS application", outPath + "cFS_LC_tables.csv", appsPath + "lc/fsw/src/lc_msg.h");
            convertFile("MD", "cFS application", outPath + "cFS_MD_tables.csv", appsPath + "md/fsw/src/md_msg.h");
            convertFile("MM", "cFS application", outPath + "cFS_MM_tables.csv", appsPath + "mm/fsw/src/mm_msg.h");
            convertFile("SC", "cFS application", outPath + "cFS_SC_tables.csv", appsPath + "sc/fsw/src/sc_msg.h");
            convertFile("SCH", "cFS application", outPath + "cFS_SCH_tables.csv", appsPath + "sch/fsw/src/sch_msg.h");
            convertFile("TO", "cFS application", outPath + "cFS_TO_tables.csv", appsPath + "to/fsw/src/to_app.h", appsPath + "to/fsw/src/to_hktlm.h");
            convertFile("TO_lab", "cFS application", outPath + "cFS_TO_lab_tables.csv", appsPath + "to_lab/fsw/src/to_lab_msg.h");
        }
    }

    /**************************************************************************
     * Convert the cFE system or CFS application telemetry and command
     * structures to CSV format
     *
     * @param sysAppName
     *            cFE system or CFS application acronym
     *
     * @param type
     *            system/application descriptor
     *
     * @param outputFileName
     *            output file path+name. If no path is provided the output file
     *            is written to the same folder from which the application is
     *            executed
     *
     * @param inputFileNames
     *            input file path+name. If more than one is provided then the
     *            structures are written to the single output file
     *************************************************************************/
    private void convertFile(String sysAppName, String type, String outputFileName, String... inputFileNames)
    {
        try
        {
            structDataIn = new ArrayList<String>();
            structDataOut = new ArrayList<String>();
            macros = new ArrayList<String>();
            List<String> variableNames = new ArrayList<String>();
            int formulaIndex = 1;

            // Step through each input file name
            for (String inputFileName : inputFileNames)
            {
                // Open the input file
                File inFile = new File(inputFileName);

                // Check if the input file exists
                if (inFile.exists())
                {
                    BufferedReader br = new BufferedReader(new FileReader(inFile));

                    // Read the first line in the file
                    String inLine = br.readLine();

                    // Continue to read the file until EOF is reached
                    while (inLine != null)
                    {
                        // Add the line from the input file
                        structDataIn.add(inLine.trim());

                        // Read the next line in the file
                        inLine = br.readLine();
                    }

                    // Close the input file
                    br.close();
                }
                // Can't locate the input file
                else
                {
                    System.out.println("Warning: can't locate input file: " + inputFileName);
                }
            }

            // Add the output file header
            structDataOut.add("# Copyright 2017 United States Government as represented by the Administrator\n"
                              + "# of the National Aeronautics and Space Administration. No copyright is claimed\n"
                              + "# in the United States under Title 17, U.S. Code. All Other Rights Reserved.\n"
                              + "\n\n"
                              + "# " + sysAppName + " " + type + " data tables\n"
                              + "#   Use the Data | Import table(s) command to import the " + sysAppName + "\n"
                              + "#   data table definitions into an existing project\n");

            // Create the macro tag and get its row index in the output file
            structDataOut.add("_macros_");
            macroIndex = structDataOut.size();
            structDataOut.add("");

            // Step through each row of the input file data
            for (int row = 0; row < structDataIn.size(); row++)
            {
                // Check if the row is a type definition
                if (structDataIn.get(row).startsWith("typedef"))
                {
                    // Check if the row isn't a structure type definition
                    if (!structDataIn.get(row).startsWith("typedef struct"))
                    {
                        // Inform the user and skip the typedef
                        System.out.println("Warning: unhandled typedef: " + structDataIn.get(row));
                        continue;
                    }

                    // Initialize the variable name list
                    variableNames.clear();

                    // Step through the remaining row in order to find the
                    // structure members
                    for (int lastRow = row + 1; lastRow < structDataIn.size(); lastRow++)
                    {
                        String compileMacro = "";

                        // Check if this is the last line in the structure
                        // definition
                        if (structDataIn.get(lastRow).startsWith("}"))
                        {
                            // Get the structure's name
                            String structName = structDataIn.get(lastRow).replaceAll("(?:^\\s*}\\s*(?:OS_PACK\\s+)?|\\s*;.*)", "");

                            // Build the structure and column tags for the CSV
                            // file
                            structDataOut.add("_name_type_\n\"" + structName + "\",\"Structure\"");
                            structDataOut.add("_column_data_\n\"Data Type\",\"Variable Name\",\"Array Size\",\"Bit Length\",\"Description\"");

                            String dataType = "";
                            String variableName = "";
                            String arraySize = "";
                            String bitLength = "";
                            description = "";

                            // Step through each structure member
                            for (row++; row < lastRow; row++)
                            {
                                // Check if the line is a compiler macro
                                if (structDataIn.get(row).startsWith("#if"))
                                {
                                    // Set the text prepended to the
                                    // description that flags the variable as
                                    // being influenced by a compiler macro
                                    compileMacro = "(WITHIN COMPILER MACRO)";
                                }
                                // Check if this is the end of the compiler
                                // macro
                                else if (structDataIn.get(row).startsWith("#endif"))
                                {
                                    // Clear the compiler macro text flag
                                    compileMacro = "";
                                }

                                // Check if this is a comment
                                if (structDataIn.get(row).startsWith("/*"))
                                {
                                    // Get the comment from this and (if
                                    // applicable) subsequent rows
                                    row = getDescription(row, structDataIn.get(row));

                                    // Check if a variable has been found for
                                    // this structure
                                    if (!variableName.isEmpty())
                                    {
                                        // Update the variable's description
                                        // with the additional comment text
                                        structDataOut.set(structDataOut.size() - 1,
                                                          "\"" + dataType
                                                                                    + "\",\"" + variableName
                                                                                    + "\",\"" + arraySize
                                                                                    + "\",\"" + bitLength
                                                                                    + "\",\"" + description + "\"");
                                    }
                                }
                                // Check if this is a variable definition
                                // within the structure typedef
                                else if (!structDataIn.get(row).isEmpty()
                                         && !structDataIn.get(row).startsWith("{")
                                         && !structDataIn.get(row).startsWith("**")
                                         && !structDataIn.get(row).startsWith("*/")
                                         && !structDataIn.get(row).startsWith("#")
                                         && !structDataIn.get(row).startsWith("\\"))
                                {
                                    // Separate the data type from the
                                    // remainder of the variable definition
                                    String[] dataTypeAndVarName = structDataIn.get(row).split("\\s+", 2);

                                    // Separate the variable name and
                                    // description (if any)
                                    String[] varNameAndDesc = dataTypeAndVarName[1].trim().split(";", 2);

                                    dataType = dataTypeAndVarName[0];
                                    arraySize = "";
                                    bitLength = "";
                                    description = compileMacro;

                                    // Check if a description exists for the
                                    // variable
                                    if (varNameAndDesc.length == 2 && !varNameAndDesc[1].trim().isEmpty())
                                    {
                                        // Get the variable's description from
                                        // this and (if applicable) subsequent
                                        // rows
                                        row = getDescription(row, varNameAndDesc[1].trim());
                                    }

                                    // Step through each variable defined on
                                    // this row
                                    for (String varName : varNameAndDesc[0].trim().split("\\s*,\\s*"))
                                    {
                                        // Get the variable name
                                        variableName = varName.trim();

                                        // Check if this is an array variable
                                        if (variableName.contains("["))
                                        {
                                            // Extract the array size and
                                            // remove it from the variable name
                                            arraySize = variableName.replaceAll("(.*\\[|\\])", "");
                                            variableName = variableName.substring(0, variableName.indexOf("["));

                                            // If the array size isn't a number
                                            if (!arraySize.matches("\\d+"))
                                            {
                                                // Get the macro name and
                                                // default value
                                                String macroName = arraySize;
                                                String macroValue = "2";

                                                // Check if the macro is a
                                                // formula
                                                if (macroName.matches(".*[\\\\+\\\\-\\\\*\\\\/].*"))
                                                {
                                                    // Store the macro formula
                                                    // as the macro's value
                                                    macroValue = arraySize;

                                                    // Separate the macro
                                                    // name(s) from the other
                                                    // formula components
                                                    String[] macroParts = macroName.split("[\\s+\\(\\)\\+\\-\\*\\/]");

                                                    // Step through each part
                                                    // of the macro formula
                                                    for (String macName : macroParts)
                                                    {
                                                        // Check if this is a
                                                        // macro name
                                                        if (macName.matches("[a-zA-Z_].*"))
                                                        {
                                                            // Add the macro to
                                                            // the list;
                                                            addMacro(macName, "2");

                                                            // Bound each
                                                            // instance of the
                                                            // macro's name
                                                            // with the macro
                                                            // identifier
                                                            macroValue = macroValue.replaceAll("(^|[^" + MACRO_IDENTIFIER + "])"
                                                                                               + macName
                                                                                               + "([^"
                                                                                               + MACRO_IDENTIFIER
                                                                                               + "]|$)",
                                                                                               "$1"
                                                                                                         + MACRO_IDENTIFIER
                                                                                                         + macName
                                                                                                         + MACRO_IDENTIFIER
                                                                                                         + "$2");
                                                        }
                                                    }

                                                    // Create a name for the
                                                    // macro formula
                                                    macroName = sysAppName.trim() + "_CCDD_MACRO_" + formulaIndex;
                                                    formulaIndex++;
                                                }

                                                // Add the macro to the list
                                                addMacro(macroName, macroValue);

                                                // Add the macro identifier to
                                                // the macro name
                                                arraySize = MACRO_IDENTIFIER + macroName + MACRO_IDENTIFIER;
                                            }
                                        }
                                        // Check if the variable has a bit
                                        // length
                                        else if (variableName.contains(":"))
                                        {
                                            // Separate the variable name and
                                            // bit length
                                            String[] nameAndBits = variableName.split("\\s*:\\s*");
                                            variableName = nameAndBits[0];
                                            bitLength = nameAndBits[1];
                                        }

                                        // Check if the variable hasn't already
                                        // been added to this structure
                                        if (!variableNames.contains(variableName))
                                        {
                                            // Check if the variable is a
                                            // pointer
                                            if (variableName.startsWith("*"))
                                            {
                                                // Remove the asterisk from the
                                                // variable name and append it
                                                // to the data type
                                                variableName = variableName.replaceFirst("\\*\\s*", "");
                                                dataType += " *";
                                            }

                                            // Ad the variable name to the
                                            // list. This prevents inclusion of
                                            // duplicate variables
                                            variableNames.add(variableName);

                                            // Add the variable's definition to
                                            // the output
                                            structDataOut.add("\"" + dataType
                                                              + "\",\"" + variableName
                                                              + "\",\"" + arraySize
                                                              + "\",\"" + bitLength
                                                              + "\",\"" + description + "\"");
                                        }
                                    }
                                }
                            }

                            // Add a blank line to separate the structure
                            // definitions. The structure's variables have all
                            // been found, so stop searching
                            structDataOut.add("");
                            break;
                        }
                    }
                }
            }

            // Check if any structure definitions were created
            if (!structDataOut.isEmpty())
            {
                // Remove any empty lines from the end of the output
                while (structDataOut.get(structDataOut.size() - 1).isEmpty())
                {
                    structDataOut.remove(structDataOut.size() - 1);
                }

                // Open the output file
                File outFile = new File(outputFileName);

                // Check if the file already exists
                if (outFile.exists())
                {
                    // Delete the existing file
                    outFile.delete();
                }

                // Create the output file
                if (outFile.createNewFile())
                {
                    PrintWriter pw = new PrintWriter(outFile);

                    // Step through each row in the output data
                    for (String line : structDataOut)
                    {
                        // Output the line contents
                        pw.println(line);
                    }

                    // Close the output file
                    pw.close();
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**************************************************************************
     * Get the variable's description from the current and subsequent line from
     * the input file
     *
     * @param row
     *            input file row index
     *
     * @param inputText
     *            text containing the start of the description
     *
     * @return Row index of the end of the description
     *************************************************************************/
    private int getDescription(int row, String inputText)
    {
        // Remove any comment delimiters and doxygen tags from the description
        // text
        description += inputText.replaceAll("(?:/\\*\\*<\\s*|/\\*|\\\\[^\\s]+\\s*|\\s*\\*/$)", "").trim();

        // Check if the end of the description hasn't been reached
        while (!structDataIn.get(row).trim().endsWith("*/"))
        {
            // Get the next row of text and remove any comment delimiters and
            // doxygen tags
            row++;
            String nextText = structDataIn.get(row).trim().replaceAll("(?:/\\*\\*<\\s*|/\\\\*|\\\\[^\\s]+\\s*|\\s*\\*/$)", "").trim();

            // Check if the next row isn't empty
            if (!nextText.isEmpty())
            {
                // Check if the description isn't empty
                if (!description.isEmpty())
                {
                    // Add a space to separate the existing text from the next
                    // row's text
                    description += " ";
                }

                // Append the next row of text to the description
                description += nextText;
            }
        }

        return row;
    }

    /**************************************************************************
     * Add a macro definition to the macro list if it does not already exist in
     * the list
     *
     * @param macroName
     *            macro name
     *
     * @param macroValue
     *            macro values are set to '2' by default; macro that are a
     *            formula are set to the formula
     *************************************************************************/
    private void addMacro(String macroName, String macroValue)
    {
        // Check if the macro hasn't already been defined
        if (!macros.contains(macroName))
        {
            // Add the macro to the list
            macros.add(macroName);

            // Add the macro definition. Set the default value to '2'
            structDataOut.add(macroIndex, "\"" + macroName + "\",\"" + macroValue + "\"");
            macroIndex++;
        }
    }

    /**************************************************************************
     * Launch the application
     *
     * @param args
     *            array of command line arguments
     *************************************************************************/
    public static void main(final String[] args)
    {
        EventQueue.invokeLater(new Runnable()
        {
            /******************************************************************
             * Execute the main class
             *****************************************************************/
            @Override
            public void run()
            {
                String cfePath = null;
                String appsPath = null;
                String outPath = "";

                // Step through the command arguments
                for (int index = 0; index < args.length; index++)
                {
                    // Check if this is the cFE path command
                    if (args[index].equalsIgnoreCase("-cfe"))
                    {
                        // Skip to the next argument
                        index++;

                        // Check if an argument exists
                        if (index < args.length)
                        {
                            // Set the cFE path
                            cfePath = args[index];

                            // Append a trailing separator if none is present
                            if (!cfePath.endsWith(File.separator))
                            {
                                cfePath += File.separator;
                            }
                        }
                    }
                    // Check if this is the applications path command
                    else if (args[index].equalsIgnoreCase("-apps"))
                    {
                        // Skip to the next argument
                        index++;

                        // Check if an argument exists
                        if (index < args.length)
                        {
                            // Set the apps path
                            appsPath = args[index];

                            // Append a trailing separator if none is present
                            if (!appsPath.endsWith(File.separator))
                            {
                                appsPath += File.separator;
                            }
                        }
                    }
                    // Check if this is the output path command
                    else if (args[index].equalsIgnoreCase("-output"))
                    {
                        // Skip to the next argument
                        index++;

                        // Check if an argument exists
                        if (index < args.length)
                        {
                            // Set the output path
                            outPath = args[index];

                            // Append a trailing separator if none is present
                            if (!outPath.endsWith(File.separator))
                            {
                                outPath += File.separator;
                            }
                        }
                    }
                }

                // Check if both the cFE and apps paths aren't set
                if (cfePath == null && appsPath == null)
                {
                    // Display the command usage text
                    System.out.println("Usage:\n"
                                       + " java -jar cfs_csv_convert [-cfe <cFE folder path>] [-apps <apps folder path>] [-output <output folder path>]\n"
                                       + "  Command line arguments:\n"
                                       + "   Description         Command  Value\n"
                                       + "   ------------------  -------  ------------------------------\n"
                                       + "   cFE folder path     cfe      path + name of the cfe folder\n"
                                       + "   apps folder path    apps     path + name of the apps folder\n"
                                       + "   output folder path  output   path + name of the output folder\n\n"
                                       + "  Example: java -jar cfs_csv_convert -cfe /home/username/workspace/cfe");
                }
                // A path is specified for cFE or apps
                else
                {
                    // Convert the structures to CSV files
                    new ConvertCFSStructureToCSV(cfePath, appsPath, outPath);
                }
            }
        });
    }
}
