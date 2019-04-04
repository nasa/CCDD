/**
 * C-language structure and type definition to comma separated values (CSV) file conversion
 * handler. This utility extracts structure and structure type definitions from the files indicated
 * in the conversion_paths file and converts them into the CSV format that can be imported into the
 * CFS Command & Data Dictionary (CCDD) application
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */

package c_struct_to_csv_convert;

import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**************************************************************************************************
 * C-language structure and type definition to comma separated values (CSV) file conversion handler
 * class
 *************************************************************************************************/
public class ConvertCStructureToCSV
{
    private List<String> structDataIn;
    private List<String> structDataOut;
    private List<String> macros;

    private String description;

    private int macroIndex;

    // Characters used to encompass a macro name
    private static final String MACRO_IDENTIFIER = "##";

    // TODO THE DEFAULT MACRO VALUE OF '2' CAN LEAD TO ERRORS WHEN IMPORTING WHEN AN ARRAY SIZE IS
    // A FORMULA AND THE FORMULA EVALUATES TO < 1 (ARRAY SIZE MUST BE >= 1)

    /**********************************************************************************************
     * C-language structure and type definition to CSV file conversion handler class constructor
     *
     * Notes & assumptions:
     *
     * 1) A comment may not be placed between the data type and variable name.
     *
     * 2) Compiler macros within structures are ignored. Any variable definition within the bounds
     * of the macro is added to the output structure. The variable's description has text added to
     * indicate it falls within a compiler macro section.
     *********************************************************************************************/
    ConvertCStructureToCSV(String outputPath)
    {
        try
        {
            // Open the conversion path input file
            File pathFile = new File("conversion_paths");

            // Check if the input file exists
            if (pathFile.exists())
            {
                String[] parts;
                BufferedReader br = new BufferedReader(new FileReader(pathFile));

                // Read the first line in the file
                String inLine = br.readLine();

                // Continue to read the file until EOF is reached
                while (inLine != null)
                {
                    inLine = inLine.trim();

                    // Check if this isn't an empty or comment line
                    if (!inLine.isEmpty() && !inLine.startsWith("#"))
                    {
                        // Separate the short name, type, output file name, and input file name(s)
                        parts = inLine.split("\\s*,\\s*", 4);

                        // Separate the input file name(s)
                        String[] inputs = parts[3].split("\\s*,\\s*");

                        // Convert the input file to CSV format
                        convertFile(parts[0], parts[1], outputPath + parts[2], inputs);
                    }

                    // Read the next line in the file
                    inLine = br.readLine();
                }

                // Close the path file
                br.close();
            }
            // Can't locate the path file
            else
            {
                System.out.println("Warning: can't locate conversion_paths file");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**********************************************************************************************
     * Convert the cFE system or CFS application telemetry and command structures to CSV format
     *
     * @param sysAppName
     *            cFE system or CFS application acronym
     *
     * @param type
     *            system/application descriptor
     *
     * @param outputFileName
     *            output file path+name. If no path is provided the output file is written to the
     *            same folder from which the application is executed
     *
     * @param inputFileNames
     *            input file path+name. If more than one is provided then the structures are
     *            written to the single output file
     *********************************************************************************************/
    private void convertFile(String sysAppName,
                             String type,
                             String outputFileName,
                             String... inputFileNames)
    {
        try
        {
            structDataIn = new ArrayList<String>();
            structDataOut = new ArrayList<String>();
            macros = new ArrayList<String>();
            List<String> variableNames = new ArrayList<String>();

            // Step through each input file name
            for (String inputFileName : inputFileNames)
            {
                // Open the input file
                File inFile = new File(inputFileName);

                // Check if the input file exists
                if (inFile.exists())
                {
                    boolean continueLine = false;
                    BufferedReader br = new BufferedReader(new FileReader(inFile));

                    // Read the first line in the file
                    String inLine = br.readLine();

                    // Continue to read the file until EOF is reached
                    while (inLine != null)
                    {
                        // Remove any leading and trailing white space characters
                        inLine = inLine.trim();

                        // Check if this line is a continuation of the previous one
                        if (continueLine)
                        {
                            // Prepend the previous line to this line and remove the previous line
                            // from the input list
                            int index = structDataIn.size() - 1;
                            inLine = structDataIn.get(index).substring(0, structDataIn.get(index).length() - 1) + inLine;
                            structDataIn.remove(index);
                        }

                        // Add the line from the input file
                        structDataIn.add(inLine);

                        // Set the line continuation flag if the line ends with a backslash
                        continueLine = inLine.endsWith("\\");

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
            structDataOut.add("# Created by c_struct_to_csv_conversion on " + new Date().toString()
                              + "\n\n# Structures extracted from:"
                              + "\n#   " + Arrays.toString(inputFileNames).replaceAll("[\\[\\]]", "").replaceAll(", ", "\n#   ")
                              + "\n\n# " + sysAppName + " " + type + " data tables\n"
                              + "#   Use the CCDD Data | Import table(s) command to import the " + sysAppName + "\n"
                              + "#   data table definitions into an existing project\n");

            // Get the row index where any macros will be stored in the output file
            macroIndex = structDataOut.size();

            // Step through each row of the input file data
            for (int row = 0; row < structDataIn.size(); row++)
            {
                String structName = "";
                boolean isTypeDef = false;

                // Check if this is a type definition
                if (structDataIn.get(row).matches("\\s*typedef\\s+struct\\s*\\{?\\s*(?:$||/\\*.*|//.*)"))
                {
                    // Set the flag indicating this is a type definition
                    isTypeDef = true;
                }
                // Check if this is a structure
                else if (structDataIn.get(row).startsWith("struct"))
                {
                    // Get the structure's name
                    structName = structDataIn.get(row).replaceAll("struct\\s*([a-zA-Z_][a-zA-Z0-9_]*).*", "$1");
                }
                // Not a type definition or a structure
                else
                {
                    // Skip this row
                    continue;
                }

                // Initialize the variable name list
                variableNames.clear();

                // Step through the remaining rows in order to find the structure's members
                for (int lastRow = row + 1; lastRow < structDataIn.size(); lastRow++)
                {
                    boolean continueDataTypeOnNextRow = false;
                    String compileMacro = "";

                    // Check if this is the last line in the structure definition
                    if (structDataIn.get(lastRow).startsWith("}"))
                    {
                        // Check if this is a type definition
                        if (isTypeDef)
                        {
                            // Get the structure's name
                            structName = structDataIn.get(lastRow).replaceAll("(?:^\\s*}\\s*(?:OS_PACK\\s+)?|\\s*;.*)", "");
                        }

                        // Check if the structure name is missing
                        if (structName.isEmpty())
                        {
                            // Alert the user and skip this type definition/structure
                            System.out.println("Warning: missing structure name");
                            continue;
                        }

                        // Build the structure and column tags for the CSV file
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
                                // Set the text prepended to the description that flags the
                                // variable as being influenced by a compiler macro
                                compileMacro = "(WITHIN COMPILER MACRO)";
                            }
                            // Check if this is the end of the compiler macro
                            else if (structDataIn.get(row).startsWith("#endif"))
                            {
                                // Clear the compiler macro text flag
                                compileMacro = "";
                            }

                            // Check if this is a comment
                            if (structDataIn.get(row).startsWith("/*")
                                || structDataIn.get(row).startsWith("//"))
                            {
                                // Get the comment from this and (if applicable) subsequent rows
                                row = getDescription(row, structDataIn.get(row));

                                // Check if a variable has been found for this structure
                                if (!variableName.isEmpty())
                                {
                                    // Update the variable's description with the additional
                                    // comment text
                                    structDataOut.set(structDataOut.size() - 1,
                                                      "\"" + dataType
                                                                                + "\",\"" + variableName
                                                                                + "\",\"" + arraySize
                                                                                + "\",\"" + bitLength
                                                                                + "\",\"" + description + "\"");
                                }
                            }
                            // Check if this is a variable definition within the structure
                            else if (!structDataIn.get(row).isEmpty()
                                     && !structDataIn.get(row).startsWith("{")
                                     && !structDataIn.get(row).startsWith("**")
                                     && !structDataIn.get(row).startsWith("*/")
                                     && !structDataIn.get(row).startsWith("#")
                                     && !structDataIn.get(row).startsWith("\\"))
                            {
                                int varNameStart = -1;

                                // Store the data type continuation state
                                boolean continueDataTypeFromPreviousRow = continueDataTypeOnNextRow;

                                // Separate the data type + variable name and the description (if
                                // any)
                                String[] varDefnAndDesc = structDataIn.get(row).split("\\s*;\\s*", 2);

                                // Check if the semi-colon is contained within a comment, and so
                                // doesn't represent the end of the variable definition. An
                                // embedded /* */ comment could fool it under some circumstances
                                if (varDefnAndDesc.length == 2
                                    && (varDefnAndDesc[0].contains("/*")
                                        || varDefnAndDesc[0].contains("//")))
                                {
                                    // Reset the variable definition and description to the entire
                                    // row contents
                                    varDefnAndDesc = new String[] {structDataIn.get(row)};
                                }

                                // Remove excess white space characters around array and bit length
                                // designators, and every multiple white space instance with a
                                // space
                                varDefnAndDesc[0] = varDefnAndDesc[0].replaceAll("\\s*(\\[|:)\\s*", "$1")
                                                                     .replaceAll("\\s+", " ");

                                // Set the flag to indicate if the variable on the next row uses
                                // the data type from this row. This is determined by the presence
                                // or absence of a semi-colon
                                continueDataTypeOnNextRow = varDefnAndDesc.length == 1;

                                // Check if this row doesn't end the variable declarations for the
                                // current data type
                                if (continueDataTypeOnNextRow)
                                {
                                    // Separate the variable name(s) from the description (if any)
                                    varDefnAndDesc = structDataIn.get(row).split("\\s*,\\s*\\/\\s*", 2);

                                    // Check if the description exists
                                    if (varDefnAndDesc.length == 2)
                                    {
                                        // Add the forward slash that was removed above
                                        varDefnAndDesc[1] = "/" + varDefnAndDesc[1];
                                    }
                                }

                                // Check if this is a variable declaration for the data type on a
                                // previous row
                                if (continueDataTypeFromPreviousRow)
                                {
                                    // Set the variable name starting index to the beginning or the
                                    // row
                                    varNameStart = -1;
                                }
                                // The data type is included on this line
                                else
                                {
                                    // Get the index of the last space in the variable definition
                                    // (prior to the first comma, if multiple variables are defined
                                    // on this row)
                                    varNameStart = (varDefnAndDesc[0].indexOf(",") == -1
                                                                                         ? varDefnAndDesc[0]
                                                                                         : varDefnAndDesc[0].replaceAll("\\s*,.+", "")).lastIndexOf(" ");

                                    // Get the index of the first array definition or bit length
                                    // (the result is -1 if the row contains no array or bit-wise
                                    // variable)
                                    int arrayStart = varDefnAndDesc[0].indexOf("[");
                                    int bitStart = varDefnAndDesc[0].indexOf(":");
                                    int arrayOrBit = arrayStart > bitStart
                                                                           ? (bitStart == -1
                                                                                             ? arrayStart
                                                                                             : bitStart)
                                                                           : (arrayStart == -1
                                                                                               ? bitStart
                                                                                               : arrayStart);

                                    // Check if an array size of bit length variable is defined on
                                    // this row
                                    if (arrayOrBit != -1 && arrayOrBit < varNameStart)
                                    {
                                        // An array or bit length variable exists; these may
                                        // contain a macro formula which can contain white space
                                        // characters. Set the variable name starting index to the
                                        // first space, after first removing any macro formula
                                        varNameStart = varDefnAndDesc[0].substring(0, arrayOrBit).lastIndexOf(" ");
                                    }

                                    // Check if no variable name exists
                                    if (varNameStart == -1)
                                    {
                                        // Inform the user that the row can't be parsed and skip it
                                        System.out.println("Warning: structure '"
                                                           + structName
                                                           + "' has an invalid variable definition: "
                                                           + structDataIn.get(row));
                                        continue;
                                    }

                                    // Get the data type
                                    dataType = varDefnAndDesc[0].substring(0, varNameStart).trim();
                                }

                                // Initialize the description
                                description = compileMacro;

                                // Check if a description exists for the variable
                                if (varDefnAndDesc.length == 2 && !varDefnAndDesc[1].trim().isEmpty())
                                {
                                    // Get the variable's description from this and (if applicable)
                                    // subsequent rows
                                    row = getDescription(row, varDefnAndDesc[1].trim());
                                }

                                // Step through each variable defined on this row
                                for (String varName : varDefnAndDesc[0].substring(varNameStart + 1)
                                                                       .trim()
                                                                       .split("\\s*,\\s*"))
                                {
                                    String ptr = "";

                                    // Initialize the array size, bit length, and description
                                    arraySize = "";
                                    bitLength = "";

                                    // Get the variable name
                                    variableName = varName.trim();

                                    // Check if this is an array variable
                                    if (variableName.contains("["))
                                    {
                                        // Extract the array size and remove it from the variable
                                        // name. Remove any brackets, replacing back-to-back ending
                                        // + beginning brackets with a comma
                                        arraySize = variableName.substring(variableName.indexOf("["))
                                                                .replaceAll("\\]\\s*\\[", ",")
                                                                .replaceAll("\\[|\\]", "");
                                        variableName = variableName.substring(0, variableName.indexOf("["));

                                        // Evaluate and adjust the array size for macros
                                        arraySize = getMacros(arraySize);
                                    }
                                    // Check if the variable has a bit length
                                    else if (variableName.contains(":"))
                                    {
                                        // Separate the variable name and bit length
                                        String[] nameAndBits = variableName.split("\\s*:\\s*");

                                        // Set the variable name and bit length. Evaluate and
                                        // adjust the bit length for macros
                                        variableName = nameAndBits[0];
                                        bitLength = getMacros(nameAndBits[1]);
                                    }

                                    // Check if the variable hasn't already been added to this
                                    // structure
                                    if (!variableNames.contains(variableName))
                                    {
                                        // Check if the variable is a pointer
                                        if (variableName.startsWith("*"))
                                        {
                                            // Remove the asterisk from the variable name and
                                            // append it to the data type
                                            variableName = variableName.replaceFirst("\\*\\s*", "");
                                            ptr = " *";
                                        }

                                        // Add the variable name to the list. This prevents
                                        // inclusion of duplicate variables
                                        variableNames.add(variableName);

                                        // Add the variable's definition to the output
                                        structDataOut.add("\"" + dataType + ptr
                                                          + "\",\"" + variableName
                                                          + "\",\"" + arraySize
                                                          + "\",\"" + bitLength
                                                          + "\",\"" + description + "\"");
                                    }
                                }
                            }
                        }

                        // Add a blank line to separate the structure definitions. The structure's
                        // variables have all been found, so stop searching
                        structDataOut.add("");
                        break;
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

    /**********************************************************************************************
     * Get the variable's description from the current and subsequent line from the input file
     *
     * @param row
     *            input file row index
     *
     * @param inputText
     *            text containing the start of the description
     *
     * @return Row index of the end of the description
     *********************************************************************************************/
    private int getDescription(int row, String inputText)
    {
        // Check if the comment begins with double forward slashes
        if (inputText.startsWith("//"))
        {
            do
            {
                // Append this row's description text
                description += " " + inputText.replaceFirst("//\\s*", "");

                // Get the next row's text
                row++;
                inputText = structDataIn.get(row).trim();
            } while (inputText.startsWith("//"));
            // Continue to process rows beginning with double forward slashes

            // Adjust the row index to account for going past the last comment row
            row--;
        }
        // The comment begins with a forward slash and an asterisk
        else
        {
            // Remove any comment delimiters and doxygen tags from the description text
            description += inputText.replaceAll("(?:/\\*\\*<\\s*|/\\*|\\\\[^\\s]+\\s*|\\s*\\*/$)", "").trim();

            // Check if the end of the description hasn't been reached
            while (!structDataIn.get(row).trim().endsWith("*/"))
            {
                // Get the next row of text and remove any comment delimiters and doxygen tags
                row++;
                inputText = structDataIn.get(row)
                                        .trim()
                                        .replaceAll("(?:/\\*\\*<\\s*|/\\\\*|\\\\[^\\s]+\\s*|\\s*\\*/$)", "")
                                        .trim();

                // Check if the next row isn't empty
                if (!inputText.isEmpty())
                {
                    // Check if the description isn't empty
                    if (!description.isEmpty())
                    {
                        // Add a space to separate the existing text from the next row's text
                        description += " ";
                    }

                    // Append the next row of text to the description
                    description += inputText;
                }
            }
        }

        // Remove any leading and trailing white space characters from the description
        description = description.trim();

        return row;
    }

    /**********************************************************************************************
     * Detect each macro in the supplied string, bound it with the macro identifier, and add the
     * macro to the macro definitions
     *
     * @param inputString
     *            string potentially containing a macro or macro expression
     *
     * @return The supplied string with every macro bounded by the macro identifier
     *********************************************************************************************/
    private String getMacros(String inputString)
    {
        String outputString = "";

        // Step through each portion of the input string, splitting the input at each comma
        for (String part : inputString.split("\\s*,\\s*"))
        {
            // Check if this portion isn't a number (i.e., its a macro or macro expression)
            if (!part.matches("\\d+"))
            {
                // Check if the macro is a mathematical expression
                if (part.matches(".*[\\(\\)\\+\\-\\*\\/].*"))
                {
                    // Step through each macro name in the expression, splitting the expression at
                    // each operator
                    for (String macroName : part.split("\\s*[\\(\\)\\+\\-\\*\\/]\\s*"))
                    {
                        // Check if this is a macro name
                        if (macroName.matches("[a-zA-Z_].*"))
                        {
                            // Add the macro to the list
                            addMacro(macroName);

                            // Bound each instance of the macro's name with the macro identifier
                            part = part.replaceAll("(^|[^" + MACRO_IDENTIFIER + "])"
                                                   + macroName
                                                   + "([^"
                                                   + MACRO_IDENTIFIER
                                                   + "]|$)",
                                                   "$1"
                                                             + MACRO_IDENTIFIER
                                                             + macroName
                                                             + MACRO_IDENTIFIER
                                                             + "$2");
                        }
                    }

                    // Update the output string with the formatted macro names
                    outputString += part;
                }
                // The macro isn't a mathematical expression
                else
                {
                    // Add the macro to the list
                    addMacro(part);

                    // Add the macro identifier to the macro name
                    outputString += MACRO_IDENTIFIER + part + MACRO_IDENTIFIER;
                }
            }
            // This portion of the input is a number
            else
            {
                // Append the number
                outputString += part;
            }

            // Append a comma
            outputString += ",";
        }

        // Remove the trailing comma
        return outputString.substring(0, outputString.length() - 1);
    }

    /**********************************************************************************************
     * Add a macro definition to the macro list if it does not already exist in the list. Macro
     * values are set to '2' by default; macros that are a mathematical expression are set to the
     * expression
     *
     * @param macroName
     *            macro name
     *********************************************************************************************/
    private void addMacro(String macroName)
    {
        // Check if the macro hasn't already been defined
        if (!macros.contains(macroName))
        {
            // Check if this is the first macro
            if (macros.isEmpty())
            {
                // Create the macro tag
                structDataOut.add(macroIndex, "_macros_");
                macroIndex++;
                structDataOut.add(macroIndex, "");
            }

            // Add the macro to the list
            macros.add(macroName);

            // Add the macro definition. Set the default value to '2'
            structDataOut.add(macroIndex, "\"" + macroName + "\",\"2\"");
            macroIndex++;
        }
    }

    /**********************************************************************************************
     * Launch the application
     *
     * @param args
     *            array of command line arguments
     *********************************************************************************************/
    public static void main(final String[] args)
    {
        EventQueue.invokeLater(new Runnable()
        {
            /**************************************************************************************
             * Execute the main class
             *************************************************************************************/
            @Override
            public void run()
            {
                String outPath = "";

                // Step through the command arguments
                for (int index = 0; index < args.length; index++)
                {
                    // Check if this is the output path argument
                    if (args[index].equalsIgnoreCase("-output"))
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
                    // Unrecognized command line argument
                    else
                    {
                        // Display the command usage text
                        System.out.println("Usage:\n"
                                           + " java -jar c_struct_to_csv_convert [-output <output folder path>]\n");
                        System.exit(0);

                    }
                }

                // Convert the structures to CSV files
                new ConvertCStructureToCSV(outPath);
            }
        });
    }
}
