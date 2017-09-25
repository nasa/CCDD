/*******************************************************************************
 * Description: Output a message ID header file
 * 
 * This JavaScript script generates a shared structure types header file from
 * the supplied structure table(s).
 * 
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is claimed
 * in the United States under Title 17, U.S. Code. All Other Rights Reserved.
 ******************************************************************************/

try
{
    load("nashorn:mozilla_compat.js");
}
catch (e)
{
}

importClass(Packages.CCDD.CcddScriptDataAccessHandler);

// Get the array of structure names by the order in which they are referenced
var structureNames = ccdd.getStructureTablesByReferenceOrder();

// Get the number of structure table rows
var numStructRows = ccdd.getStructureTableNumRows();

// Get an array containing the data stream names
var dataStreams = ccdd.getDataStreamNames();

/** Functions *************************************************************** */

/*******************************************************************************
 * Output the file creation details to the specified file
 * 
 * @param file
 *            reference to the output file
 ******************************************************************************/
function outputFileCreationInfo(file)
{
    // Add the build information and header to the output file
    ccdd.writeToFileLn(file, "/* Created : " + ccdd.getDateAndTime() + "\n   User    : " + ccdd.getUser() + "\n   Project : " + ccdd.getProject() + "\n   Script  : " + ccdd.getScriptName());

    // Check if any table is associated with the script
    if (ccdd.getTableNumRows() != 0)
    {
        ccdd.writeToFileLn(file, "   Table(s): " + [].slice.call(ccdd.getTableNames()).sort().join(",\n             "));
    }

    // Check if any groups is associated with the script
    if (ccdd.getAssociatedGroupNames().length != 0)
    {
        ccdd.writeToFileLn(file, "   Group(s): " + [].slice.call(ccdd.getAssociatedGroupNames()).sort().join(",\n             "));
    }

    ccdd.writeToFileLn(file, "*/\n");
}

/*******************************************************************************
 * Output a structure's type definition to the specified file
 * 
 * @param file
 *            reference to the types header output file
 * 
 * @param structIndex
 *            index of the structure in the structure name array
 ******************************************************************************/
function outputStructure(file, structIndex)
{
    var firstPass = true;
    var isCCSDS = false;
    var lastBitFieldType = "none";
    var maxBitsAvailable = 0;
    var curFilledBits = 0;
    var headerOffset = 0;
    var usedVariableNames = [];
    var structDescription = "";
    var structSize = 0;

    // Set the minimum length required to display the structure information
    // using the structure name as the initial value. This value is used to
    // align variable (offset, byte size, rate(s), and description) and
    // structure (total size) comment text
    var minimumLength = ("} " + structureNames[structIndex] + "; ").length;

    // A pass is made through the structure rows in order to determine the
    // longest one, character-wise, so that the output can be formatted. Step
    // through each structure data row
    for (var row = 0; row < numStructRows; row++)
    {
        // Check if the structure name in the row matches the current structure
        if (structureNames[structIndex].equals(ccdd.getStructureTableNameByRow(row)))
        {
            // Check if this is the first pass through the structure data
            if (firstPass)
            {
                firstPass = false;

                // Get the value of the structure's message ID data field
                var msgID = ccdd.getTableDataFieldValue(structureNames[structIndex], "Message ID");

                // Check if the structure table has a message ID
                if (msgID != null && !msgID.isEmpty())
                {
                    // Set the minimum length to that of the CCSDS header
                    // variable which will be added if the structure has a
                    // message ID
                    minimumLength = "   char CFS_PRI_HEADER[6]; ".length;
                }
            }

            // Get the variable name for this row
            var variableName = ccdd.getStructureVariableName(row);

            // Check that this isn't an array member; only array definitions
            // appear in the type definition
            if (!variableName.endsWith("]"))
            {
                // Get the variable's array size
                var arraySize = ccdd.getStructureArraySize(row);

                // Check if the variable is an array
                if (!arraySize.isEmpty())
                {
                    // Add the brackets that will appear around the array size.
                    // Multi-dimensional arrays have the individual dimensions
                    // separated by ', '; in the type definition each ', ' is
                    // replaced with '][' which is the same number of
                    // characters, so no further padding adjustment needs to
                    // be made here to account for them
                    arraySize += "[]";
                }

                // Get the variable's bit length
                var bitLength = ccdd.getStructureBitLength(row);

                // Check if the variable has a bit length
                if (!bitLength.isEmpty())
                {
                    // Add the colon that will appear before the bit length
                    bitLength += ":";
                }

                // Determine the length of the variable definition by adding up
                // the individual parts
                var defnLength = ("   " + ccdd.getStructureDataType(row) + " " + variableName + arraySize + bitLength + "; ").length;

                // Check if the length exceeds the minimum length found thus far
                if (defnLength > minimumLength)
                {
                    // Store the new minimum length
                    minimumLength = defnLength;
                }
            }
        }
    }

    firstPass = true;

    // Step through each structure data row
    for (var row = 0; row < numStructRows; row++)
    {
        var deltaSize = 0;

        // Check if the structure name in the row matches the target structure
        if (structureNames[structIndex].equals(ccdd.getStructureTableNameByRow(row)))
        {
            // Get the variable name for this row in the structure
            var variableName = ccdd.getStructureVariableName(row);

            // Check if this is the first pass through the structure data
            if (firstPass)
            {
                firstPass = false;

                // Get the description for the current structure
                structDescription = ccdd.getTableDescriptionByRow("Structure", row);

                // Get the size of the entire structure, in bytes
                structSize = ccdd.getDataTypeSizeInBytes(structureNames[structIndex]);

                // Get the value of the structure's message ID data field
                var msgID = ccdd.getTableDataFieldValue(structureNames[structIndex], "Message ID");

                // Check if the structure table has a message ID
                if (msgID != null && !msgID.isEmpty())
                {
                    // Set the flag to add in CCSDS primary and secondary
                    // headers
                    isCCSDS = true;
                    structSize = +structSize + 12;
                }

                // Display the structure name, size, and description prior to
                // the structure's type definition
                ccdd.writeToFile(file, "/* Structure: " + structureNames[structIndex] + " (" + structSize + " bytes total)");

                // Check if the structure has a description
                if (!structDescription.isEmpty())
                {
                    // Display the structure's description
                    ccdd.writeToFile(file, "\n   Description: " + structDescription);
                }

                ccdd.writeToFileLn(file, " */");

                // Begin the structure type definition
                ccdd.writeToFileLn(file, "typedef struct");
                ccdd.writeToFileLn(file, "{");

                // Check if CCSDS headers should be added
                if (isCCSDS)
                {
                    // Set the CCSDS header length, which is used as the byte
                    // offset for the subsequent variables
                    headerOffset = 12;

                    // Output the variable array that contains the primary
                    // header values
                    var offsetStr = "0";
                    var ccsdsVar = "   char CFS_PRI_HEADER[6];";
                    var comment = "#CCSDS_PriHdr_t";
                    var sizeString = "(6 bytes)";
                    ccdd.writeToFileFormat(file, "%-" + minimumLength + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr);

                    // Output the variable array that contains the secondary
                    // header values
                    offsetStr = "6";
                    ccsdsVar = "   char CFS_SEC_HEADER[6];";
                    comment = "#CCSDS_CmdSecHdr_t";
                    ccdd.writeToFileFormat(file, "%-" + minimumLength + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr);
                }
                // No CCSDS header should be added
                else
                {
                    // Set the variable byte offset to zero
                    headerOffset = 0;
                }
            }

            // Check if this is not an array member (only array definitions are
            // output), and if the variable name hasn't already been processed
            // (the first instance of the structure is used to obtain the
            // information to create the type definition, so this is necessary
            // to prevent duplicating the members in the type definition if
            // more than one instance of the structure is present in the data)
            if (!variableName.endsWith("]") && usedVariableNames.indexOf(String(variableName)) == -1)
            {
                // Add the variable name to the list of those already processed
                usedVariableNames.push(String(variableName));

                // Get the variable's data type, array size, and description
                var dataType = ccdd.getStructureDataType(row);
                var arraySize = ccdd.getStructureArraySize(row);
                var description = ccdd.getStructureDescription(row);

                // Determine the size of the variable, in bytes
                var byteSize = ccdd.getDataTypeSizeInBytes(dataType);

                // Build the variable's full path; this will be used to get the
                // structure's byte offset
                var variablePath = structureNames[structIndex] + "," + dataType + "." + variableName;
                var varOffset = 0;

                var bitLength = "";
                var sizeString = "(" + byteSize + " bytes)";
                var variableMsg = "   " + dataType + " " + variableName;

                // Check if the structure has no variable description column
                if (description == null)
                {
                    // Set the description to a blank
                    description = "";
                }

                // Check if the array size is provided; i.e., this is an array
                // definition
                if (!arraySize.isEmpty())
                {
                    var firstDim = "";
                    var sizeMsg = "";
                    deltaSize = 1;

                    // Separate the array size into the individual dimensions
                    var dimensions = arraySize.split(", ");

                    // Step through each dimension in the array
                    for (var dim = 0; dim < dimensions.length; dim++)
                    {
                        // Add a dimension for the first array member
                        firstDim += "[0]";

                        // Keep a running total of this dimension's byte
                        // requirements
                        deltaSize *= +dimensions[dim];

                        // Update the comment text that will follow the array
                        // definition
                        sizeMsg += dimensions[dim] + "x";
                    }

                    // Get the total byte size of the array
                    deltaSize *= +byteSize;

                    // Get the byte offset of the first member of this array
                    // variable within its structure
                    varOffset = ccdd.getVariableOffset(variablePath + firstDim);

                    // Create the array variable definition, placing brackets
                    // around the array dimensions
                    variableMsg = variableMsg + "[" + arraySize.replaceAll(", ", "][") + "]";

                    // Build the comment that shows the array's byte size
                    sizeString = "(" + sizeMsg + byteSize + "=" + deltaSize + " bytes)";

                    lastBitFieldType = "none";
                }
                // No array size for this row; i.e., the variable is not an
                // array definition
                else
                {
                    deltaSize = byteSize;

                    // Get the byte offset of the this variable within its
                    // structure
                    varOffset = ccdd.getVariableOffset(variablePath);

                    // Get the variable's bit length
                    bitLength = ccdd.getStructureBitLength(row);

                    // Check if the bit length is provided
                    if (!bitLength.isEmpty())
                    {
                        // Append the bit length to the variable
                        variableMsg = variableMsg + ":" + bitLength;
                        sizeString = "";

                        // Check if the variable won't pack with the preceding
                        // variable(s) due to being a different data type or
                        // exceeding the bit length of the data type
                        if (lastBitFieldType != dataType || (+curFilledBits + +bitLength > +maxBitsAvailable))
                        {
                            // Reset the bit packing values
                            curFilledBits = bitLength;
                            lastBitFieldType = dataType;
                            maxBitsAvailable = 8 * +byteSize;
                        }
                        // The variable has the same data type and its bits
                        // will pack with the preceding variable(s)
                        else
                        {
                            // Add this variable's bits to the current pack
                            curFilledBits = +curFilledBits + +bitLength;
                        }
                    }
                    // The variable has no bit length
                    else
                    {
                        lastBitFieldType = "none";
                    }
                }

                // Terminate the variable definition then pad it with spaces to
                // align the comment text
                variableMsg += ";";

                // Adjust the variable's byte offset within the structure to
                // include the header (if present)
                varOffset = +varOffset + +headerOffset;

                var rateInfo = "";

                // Step through each data stream
                for (var dataStream = 0; dataStream < dataStreams.length; dataStream++)
                {
                    // Get the variable's rate for this data stream
                    var rateValue = ccdd.getStructureTableData(dataStreams[dataStream], row);

                    // Check if the variable has a rate assigned in this stream
                    if (!rateValue.isEmpty())
                    {
                        // Build the rate information
                        rateInfo += "{" + dataStreams[dataStream] + " @" + rateValue + " Hz}";
                    }
                }

                // Build the full variable definition, along with the byte
                // offset, size, rate, and description information, then
                // output it to the types header file
                ccdd.writeToFileFormat(file, "%-" + minimumLength + "s /* [%5s] " + (sizeString + rateInfo + "  " + description).trim() + " */\n", variableMsg, varOffset);
            }
        }
    }

    // Conclude the structure's type definition, pad it for length and add the
    // structure's total size, then output this to the types header file
    ccdd.writeToFileFormat(file, "%-" + minimumLength + "s /* Total size of " + structSize + " bytes */\n", "} " + structureNames[structIndex] + ";");
}

/*******************************************************************************
 * Output the shared structure type definitions header file
 * 
 * @param sharedFileName
 *            shared structure type definitions header file name
 ******************************************************************************/
function makeSharedHeaders(baseFileName)
{
    // Build the shared type definitions header output file name and include
    // flag
    var sharedFileName = ccdd.getOutputPath() + baseFileName + ".h";
    var headerIncludeFlag = "_" + baseFileName.toUpperCase() + "_H_";

    // Open the shared type definitions header output file
    var sharedFile = ccdd.openOutputFile(sharedFileName);

    // Check if the shared type definitions header file successfully opened
    if (sharedFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(sharedFile);

        // Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(sharedFile, "#ifndef " + headerIncludeFlag);
        ccdd.writeToFileLn(sharedFile, "#define " + headerIncludeFlag);
        ccdd.writeToFileLn(sharedFile, "#include <stdint.h>");
        ccdd.writeToFileLn(sharedFile, "");

        // Step through each structure. This list is in order so that base
        // structures are created before being referenced in another structure
        for (struct = 0; struct < structureNames.length; struct++)
        {
            // Check if the structure is referenced by more than one structure
            if (ccdd.isStructureShared(structureNames[struct]))
            {
                // Output the structure's type definition to the shared types
                // file
                outputStructure(sharedFile, struct);
            }
        }

        // Finish and close the shared type definitions header output file
        ccdd.writeToFileLn(sharedFile, "");
        ccdd.writeToFileLn(sharedFile, "#endif /* #ifndef " + headerIncludeFlag + " */");
        ccdd.closeFile(sharedFile);
    }
    // The shared type definitions header file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening types header output file '</b>" + sharedFileName + "<b>'");
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Check if structure data is supplied
if (numStructRows != 0)
{
    // Output the shared structure type definition header file
    makeSharedHeaders("shared_types");
}
// No structure data is supplied
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>No structure data supplied for script '</b>" + ccdd.getScriptName() + "<b>'");
}
