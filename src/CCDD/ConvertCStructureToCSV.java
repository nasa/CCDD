/**************************************************************************************************
 * /** \file ConvertCStructureToCSV.java
 *
 * \author Kevin Mccluney Bryan Willis
 *
 * \brief Class containing methods that can be used to convert a C header file to CSV format
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import CCDD.CcddClassesComponent.FileEnvVar;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.MacrosColumn;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.SNAP_SHOT_FILE_PATH_2;

/**************************************************************************************************
 * C-language structure and type definition to comma separated values (CSV) file conversion handler
 * class
 *************************************************************************************************/
public class ConvertCStructureToCSV
{
    private List<String> structDataIn;
    private List<String> structDataOut;
    private List<String> macrosFoundInHeaderFiles; // All macros found in the imported header files
    private List<String> currentMacros; // All macros currently defined in the database
    private List<String[]> newMacros; // Macros that were found and currently do not exist in this
                                      // database

    private String description;

    // Characters used to encompass a macro name
    private static final String MACRO_IDENTIFIER = "##";

    /**********************************************************************************************
     * Convert the provided C files to CSV format
     *
     * @param dataFiles List of C files that need to be converted
     *
     * @param parent    CCDD component that called this function
     *********************************************************************************************/
    public FileEnvVar[] convertFile(FileEnvVar[] dataFiles, CcddMain ccddMain)
    {
        CcddMacroHandler macroHandler = ccddMain.getMacroHandler();
        CcddDbTableCommandHandler dbTable = ccddMain.getDbTableCommandHandler();

        // Create a list to hold all file
        List<FileEnvVar> newDataFile = new ArrayList<FileEnvVar>();
        String message = "Macro values or formulae for a variable's array size or bit length are \n"
                         + "converted to CCDD macros. However, the value of these macros is set to \n"
                         + "\"2\" - the user may edit the CSV file to update the macro values \n"
                         + "(or edit them in CCDD after importing).  Note that the default macro \n"
                         + "value can lead to import errors if a macro formula used as an array \n"
                         + "size evaluates to less than 2 (array size must be >= 2).";

        if (!ccddMain.isGUIHidden())
        {
            if (!(new CcddDialogHandler().showMessageDialog(ccddMain.getMainFrame(), message, "Notice",
                                                            JOptionPane.QUESTION_MESSAGE,
                                                            DialogOption.OK_CANCEL_OPTION) != OK_BUTTON))
            {
                System.out.print(message + "\n");
            }
        }
        else
        {
            System.out.print(message + "\n");
        }

        try
        {
            structDataIn = new ArrayList<String>();
            structDataOut = new ArrayList<String>();
            List<String> variableNames = new ArrayList<String>();
            macrosFoundInHeaderFiles = new ArrayList<String>();
            currentMacros = macroHandler.getMacroNames();
            newMacros = new ArrayList<String[]>();

            // Step through each file
            for (FileEnvVar file : dataFiles)
            {
                boolean continueLine = false;
                BufferedReader br = new BufferedReader(new FileReader(file));

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
                        // Prepend the previous line to this line and remove the previous line from
                        // the input list
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

            structDataOut.add("c_struct_to_csv_conversion");

            boolean firstMacro = true;

            // Step through each row of the input file data
            for (int row = 0; row < structDataIn.size(); row++)
            {
                String structName = "";
                boolean isTypeDef = false;

                // Check if this is a type, structure or macro definition
                if (structDataIn.get(row).matches("\\s*typedef\\s+struct\\s*\\{?\\s*(?:$||/\\*.*|//.*)"))
                {
                    // Set the flag indicating this is a type definition
                    isTypeDef = true;

                    if (!firstMacro)
                    {
                        structDataOut.add("\n");
                        firstMacro = true;
                    }
                }
                else if (structDataIn.get(row).startsWith("struct"))
                {
                    // Get the structure's name
                    structName = structDataIn.get(row).replaceAll("struct\\s*([a-zA-Z_][a-zA-Z0-9_]*).*", "$1");

                    if (!firstMacro)
                    {
                        structDataOut.add("\n");
                        firstMacro = true;
                    }
                }
                else if (structDataIn.get(row).startsWith("#define"))
                {
                    String[] macroData = structDataIn.get(row).split(" ");
                    String name = "";
                    String value = "";

                    if (macroData.length == 3)
                    {
                        name = macroData[1];
                        value = macroData[2];

                        if (firstMacro)
                        {
                            structDataOut.add("\n_macro_");
                            firstMacro = false;
                        }
                        structDataOut.add("\"" + name + "\",\"" + value + "\"");
                        macrosFoundInHeaderFiles.add(name);
                    }

                    continue;
                }
                else
                {
                    if (!firstMacro)
                    {
                        structDataOut.add("\n");
                        firstMacro = true;
                    }
                    // Not a type definition or a structure so skip this row
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
                            structName = structDataIn.get(lastRow).replaceAll("(?:^\\s*}\\s*(?:OS_PACK\\s+)?|\\s*;.*)",
                                                                              "");
                        }

                        // Build the structure and column tags for the CSV file
                        structDataOut.add("_name_type_\n\"" + structName + "\",\"Structure\"");
                        structDataOut
                                .add("_column_data_\n\"Data Type\",\"Variable Name\",\"Array Size\",\"Bit Length\",\"Description\"");

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
                            if (structDataIn.get(row).startsWith("/*") || structDataIn.get(row).startsWith("//"))
                            {
                                // Get the comment from this and (if applicable) subsequent rows
                                row = getDescription(row, structDataIn.get(row));

                                // Check if a variable has been found for this structure
                                if (!variableName.isEmpty())
                                {
                                    // Update the variable's description with the additional
                                    // comment text
                                    structDataOut.set(structDataOut.size() - 1,
                                                      "\"" + dataType + "\",\"" + variableName + "\",\"" + arraySize
                                                                                + "\",\"" + bitLength + "\",\""
                                                                                + description + "\"");
                                }
                            }
                            // Check if this is a variable definition within the structure
                            else if (!structDataIn.get(row).isEmpty() && !structDataIn.get(row).startsWith("{")
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
                                    && (varDefnAndDesc[0].contains("/*") || varDefnAndDesc[0].contains("//")))
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
                                    varNameStart = (varDefnAndDesc[0]
                                            .indexOf(",") == -1 ? varDefnAndDesc[0]
                                                                : varDefnAndDesc[0].replaceAll("\\s*,.+", ""))
                                                                        .lastIndexOf(" ");

                                    // Get the index of the first array definition or bit length
                                    // (the result is -1 if the row contains no array or bit-wise
                                    // variable)
                                    int arrayStart = varDefnAndDesc[0].indexOf("[");
                                    int bitStart = varDefnAndDesc[0].indexOf(":");
                                    int arrayOrBit = arrayStart > bitStart ? (bitStart == -1 ? arrayStart : bitStart)
                                                                           : (arrayStart == -1 ? bitStart : arrayStart);

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
                                for (String varName : varDefnAndDesc[0].substring(varNameStart + 1).trim()
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
                                                .replaceAll("\\]\\s*\\[", ",").replaceAll("\\[|\\]", "");
                                        variableName = variableName.substring(0, variableName.indexOf("["));

                                        // Evaluate and adjust the array size for macros
                                        arraySize = getMacros(arraySize);

                                        if (arraySize.contains("##"))
                                        {
                                            String macroName = arraySize.replace("#", "");

                                            // Was the macro defined within the file?
                                            if (!macrosFoundInHeaderFiles.contains(macroName))
                                            {
                                                // If not was the macro already defined within the
                                                // database?
                                                if (!currentMacros.contains(macroName))
                                                {
                                                    // If not store the macro with a value of 2
                                                    newMacros.add(new String[] {macroName, "2", ""});
                                                }
                                            }
                                        }
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
                                        structDataOut
                                                .add("\"" + dataType + ptr + "\",\"" + variableName + "\",\""
                                                     + arraySize + "\",\"" + bitLength + "\",\"" + description + "\"");
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

                String filePath = SNAP_SHOT_FILE_PATH_2 + "/C_Header_Conversion_Data.csv";

                // Open the output file
                FileEnvVar outFile = new FileEnvVar(filePath);

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

                newDataFile.add(outFile);
            }
        }
        catch (Exception e)
        {
            CcddUtilities.displayException(e, ccddMain.getMainFrame());
        }

        // Update the macros
        macroHandler.initializeMacroUpdates();
        macroHandler.updateMacros(newMacros, true);
        macroHandler.setMacroData();

        // Store the macros in the database
        dbTable.storeInformationTable(InternalTable.MACROS,
                                      CcddUtilities.removeArrayListColumn(macroHandler.getMacroData(),
                                                                          MacrosColumn.OID.ordinal()),
                                      null,
                                      ccddMain.getMainFrame());

        return newDataFile.toArray(new FileEnvVar[0]);
    }

    /**********************************************************************************************
     * Get the variable's description from the current and subsequent line from the input file
     *
     * @param row       Input file row index
     *
     * @param inputText Text containing the start of the description
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
                inputText = structDataIn.get(row).trim()
                        .replaceAll("(?:/\\*\\*<\\s*|/\\\\*|\\\\[^\\s]+\\s*|\\s*\\*/$)", "").trim();

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
     * @param inputString String potentially containing a macro or macro expression
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
                            // Bound each instance of the macro's name with the macro identifier
                            part = part.replaceAll("(^|[^" + MACRO_IDENTIFIER + "])" + macroName + "([^"
                                                   + MACRO_IDENTIFIER + "]|$)",
                                                   "$1" + MACRO_IDENTIFIER + macroName + MACRO_IDENTIFIER + "$2");
                        }
                    }

                    // Update the output string with the formatted macro names
                    outputString += part;
                }
                // The macro isn't a mathematical expression
                else
                {
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
}
