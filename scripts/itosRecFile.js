/*******************************************************************************
 * Description: Output an ITOS record file
 *
 * This JavaScript script generates an ITOS record file from the supplied
 * telemetry and command information
 *
 * Assumptions: If the structure has a non-empty data field named "Message ID"
 * then it is assumed to require a CCSDS header which is automatically added. If
 * a table containing extra text to include is provided then its table type is
 * "Includes" and has the column "Includes". The output file names are prepended
 * with a name taken from a data field, "System", found either in the first
 * group associated with the script, or, if not found there then in the first
 * structure table associated with the script; if no "System" data field exists
 * or is empty the name is blank. The project's data type definitions are output
 * to the types header file
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

    ccdd.writeToFileLn(file, "*/");
}

/*******************************************************************************
 * Determine if the row containing the specified variable is not an array
 * definition. A row in a table is an array definition if a value is present in
 * the array size column but the variable name does not end with a ']'
 *
 * @param variableName
 *            variable name
 *
 * @param arraySize
 *            array size
 *
 * @return true if the variable is not an array definition
 ******************************************************************************/
function isVariable(variableName, arraySize)
{
    return variableName && arraySize != null && (arraySize.isEmpty() || variableName.endsWith("]"));
}

/*******************************************************************************
 * Check if the variable on the specified row in the structure data has at least
 * one non-blank rate value
 *
 * @param row
 *            row index in the structure data table
 *
 * @return true if the variable in the specified row has at least one non-blank
 *         rate value
 ******************************************************************************/
function isTelemetry(row)
{
    var isTlm = false;

    // Get the rate column values for all rate columns in the structure
    var rates = ccdd.getStructureRates(row);

    // Step through each rate column value
    for (var index = 0; index < rates.length; index++)
    {
        // Check if a rate value is present in the column
        if (rates[index] != "")
        {
            // Set the flag to indicate the variable is telemetered and stop
            // searching
            isTlm = true;
            break;
        }
    }

    return isTlm;
}

/*******************************************************************************
 * Build the command enumeration name
 *
 * @param row
 *            row index in the command data table
 *
 * @param argumentNum
 *            command argument number
 *
 * @return Command enumeration name
 ******************************************************************************/
function getCommandEnumerationName(row, argumentNum)
{
    return ccdd.getCommandName(row) + "_" + ccdd.getCommandArgName(argumentNum, row) + "_ENUMERATION";
}

/*******************************************************************************
 * Output an array of structure row indices that order the bit-packed variables
 * in the structure table based on endianess
 *
 * @param endian
 *            "BE" (big endian) or "LE" (little endian), depending on what byte
 *            order is desired
 *
 * @return Array of structure row indices with the bit-packed variables ordered
 *         in the structure table based on endianess
 ******************************************************************************/
function reorderRowsForByteOrder(endian)
{
    var reOrdered = [];

    // Create the reordered row array assuming the order is unchanged
    for (var row = 0; row < numStructRows; row++)
    {
        // Default to ID=index
        reOrdered[row] = row;
    }

    // Check if the order is little endian (there's no need to perform the
    // reordering if big endian)
    if (endian.equals("LE"))
    {
        // Step through each structure row
        for (var tgtRow = 0; tgtRow < numStructRows; tgtRow++)
        {
            // Counter to track the number of variables bit-packed with the
            // target variable
            var packCount = 1;

            // Get the name of the target structure
            var tgtStructName = ccdd.getStructureTableNameByRow(tgtRow);

            // Get the byte offset of the target variable
            var tgtVarPath = ccdd.getFullVariableNameRaw(tgtRow);
            var tgtOffset = ccdd.getVariableOffset(tgtVarPath);

            // Step through the remaining rows so that the structures can be
            // compared with the target
            for (var compRow = row + 1; compRow < numStructRows; compRow++)
            {
                // Check if the target structure is the same as the comparison
                // structure
                if (tgtStructName.equals(ccdd.getStructureTableNameByRow(compRow)))
                {
                    // Get the byte offset of the comparison variable
                    var compVarPath = ccdd.getFullVariableNameRaw(compRow);
                    var compOffset = ccdd.getVariableOffset(compVarPath);

                    // Check if the target and comparison variables have the
                    // same offset; i.e., they are bit-packed
                    if (tgtOffset == compOffset)
                    {
                        // Increment the bit-packed variables counter
                        packCount++;
                    }
                    // The variables aren't bit-packed
                    else
                    {
                        // Stop searching since no further variables are packed
                        // with the target
                        break;
                    }
                }
                // The structures aren't the same
                else
                {
                    // Stop searching since the all of the target structure has
                    // been checked
                    break;
                }
            }

            // Check if the target variable is bit-packed with one or more
            // variables
            if (packCount > 1)
            {
                // Step through the bit-packed variables
                for (var index = 0; index < packCount; index++)
                {
                    // Store the bit-packed variable's row, in reverse order of
                    // its original appearance, in the re-ordered row array
                    reOrdered[tgtRow + index] = tgtRow + packCount - index - 1;
                }

                // Adjust the target row index to skip the bit-packed variables
                // so that they won't be checked again as the loop progresses
                tgtRow = tgtRow + packCount - 1;
            }
        }
    }

    return reOrdered;
}

/*******************************************************************************
 * Output a telemetry packet or prototype structure definition
 *
 * @param structureName
 *            structure name
 *
 * @param isPacket
 *            true if this is a telemetry packet definition; false for a
 *            prototype structure definition
 *
 * @param outFile
 *            output file
 ******************************************************************************/
function outputStructureDefinition(structureName, isPacket, outFile)
{
    var termLine = false;
    var usedVariableNames = [];

    // Step through each row in the table
    for (var rowIndex = 0; rowIndex < numStructRows; rowIndex++)
    {
        // Get the row index when swapped for LE bit fields
        row = newRowOrder[rowIndex];

        // Check that this row references a variable in the prototype structure
        if (ccdd.getStructureTableNameByRow(row).equals(structureName))
        {
            // Get the variable name for this row
            var variableName = ccdd.getStructureVariableName(row);

            var isFound = false;

            // Step through each name in the array of already processed
            // variable names
            for (var index = 0; index < usedVariableNames.length; index++)
            {
                // Check if the target name matches the array name
                if (usedVariableNames[index].equals(variableName))
                {
                    // Match found; set the flag and stop searching
                    isFound = true;
                    break;
                }
            }

            // Check if the variable name hasn't already been processed; this is
            // necessary to prevent duplicating the variables in the prototype
            // structure for a structure that is referenced as an array
            if (!isFound)
            {
                // Add the variable name to the list of those already processed
                usedVariableNames.push(variableName)

                // Get the array size for this row
                var arraySize = ccdd.getStructureArraySize(row);

                // Only output non-array variables or array members (i.e., skip
                // array definitions)
                if (isVariable(variableName, arraySize))
                {
                    var skipStringMembers = false;

                    // Get the variable's data type
                    var dataType = ccdd.getStructureDataType(row);

                    // Check if the variable is a string; a string is handled as
                    // a single entity rather than an array of characters
                    if (arraySize && arraySize != "" && dataType == "string")
                    {
                        // Check if this is the first character in the string
                        if (variableName.endsWith("_0"))
                        {
                            // Remove the array size from the variable name
                            variableName = variableName.substring(0, variableName.length() - 2);

                            // Add the string length information
                            otherParameters += "lengthInCharacters = " + arraySize + " , ";
                        }
                        // This is a character other than the first one in the
                        // string
                        else
                        {
                            // Set the flag to skip the remaining string members
                            skipStringMembers = true;
                        }
                    }

                    // Check that this isn't a member of a string (other than
                    // the first one)
                    if (!skipStringMembers)
                    {
                        // In case this is an array member replace the square brackets. This also
                        // prevents returning a duplicate name due to the conversion (e.g.,
                        // abc_0 and abc[0] would otherwise be converted to the same name, abc_0,
                        // if the brackets are simply replaced)
                        var variablePath = ccdd.getFullVariableName(rowIndex, ",");
                        var varIndex = variablePath.lastIndexOf(",") + 1;
                        variableName = variablePath.substring(varIndex);

                        // Check if this is not the first pass
                        if (termLine)
                        {
                            // Check if this is the packet definition
                            if (isPacket)
                            {
                                // Terminate the previous line with a comma
                                ccdd.writeToFileLn(outFile, ",");
                            }
                            // This is a prototype structure
                            else
                            {
                                // Terminate the previous line with a line feed
                                ccdd.writeToFileLn(outFile, "");
                            }
                        }

                        termLine = true;

                        // Get the length in bits for this row
                        var bitLength = ccdd.getStructureBitLength(row);
                        var otherParameters = "";

                        // Check if the length in bits is specified
                        if (bitLength && bitLength != "")
                        {
                            // Add the length in bits parameter
                            otherParameters = "lengthInBits=" + bitLength;
                        }

                        // Get the ITOS encoded form of the data type as two
                        // characters (type + size)
                        var itosEncode2Char = ccdd.getITOSEncodedDataType(dataType, "TWO_CHAR");

                        // Check if variable is a primitive data type or a
                        // structure
                        if (itosEncode2Char)
                        {
                            // Check if other parameters have been defined
                            if (otherParameters)
                            {
                                // Add a space to separate the parameters
                                otherParameters += " ";
                            }

                            // Check if the data type is a recognized primitive
                            if (!itosEncode2Char.equals(dataType))
                            {
                                // Add the 'no mnemonic' parameter
                                otherParameters += "generateMnemonic=\"no\"";
                            }
                        }

                        // Create the parameter definition
                        ccdd.writeToFile(outFile, "  " + itosEncode2Char + " " + variableName + " {" + otherParameters + "}");
                    }
                }
            }
        }
    }
}

/*******************************************************************************
 * Get the last 12 bits of a hexadecimal message ID
 *
 * @param msgID
 *            message ID
 *
 * @return Last 12 bits of a message ID formatted as a 4 digit hex number (i.e.
 *         0x031f); '0x0000' if the message ID isn't an integer or hexadecimal
 *         value
 ******************************************************************************/
function extractMessageID(msgID)
{
    var retVal = "0000";

    // Check if the ID is a hexadecimal number
    if (msgID.match(/(0x)?[0-9a-fA-F]+/g))
    {
        // Convert the message ID from a string to a value
        var val = parseInt(msgID.replace("0x", ""), 16);

        // Strip off all but the last 12 bits and convert the value to a
        // hexadecimal string
        retVal = (val & 0x7ff).toString(16);

        // Pad the value with leading zeroes if needed to bring the length to
        // four digits
        while (retVal.length < 4)
        {
            retVal = "0" + retVal;
        }
    }

    return "0x" + retVal;
}

/*******************************************************************************
 * Get the last 12 bits of a hexadecimal message ID
 *
 * @param msgID
 *            message ID
 *
 * @return Last 12 bits of a message ID formatted as a 3 digit hex number (i.e.
 *         0x31f); '0x000' if the message ID isn't an integer or hexadecimal
 *         value
 ******************************************************************************/
function extractCommandID(msgID)
{
    var retVal = "000";

    // Check if the ID is a hexadecimal number
    if (msgID.match(/(0x)?[0-9a-fA-F]+/g))
    {
        // Convert the message ID from a string to a value
        var val = parseInt(msgID.replace("0x", ""), 16);

        // Strip off all but the last 12 bits and convert the value to a
        // hexadecimal string
        retVal = (val & 0x7ff).toString(16);

        // Pad the value with leading zeroes if needed to bring the length to
        // three digits
        while (retVal.length < 3)
        {
            retVal = "0" + retVal;
        }
    }

    return "0x" + retVal;
}

/*******************************************************************************
 * Output a telemetry packet definition
 *
 * @param prefix
 *            structure name prefix; used to differentiate the same structure
 *            when defined for multiple flight computers
 *
 * @param structureName
 *            structure name
 *
 * @param msgID
 *            message ID (hexadecimal)
 *
 * @param msgIDOffset
 *            message ID offset (hexadecimal)
 ******************************************************************************/
function outputTelemetryPacket(prefix, structureName, msgID, msgIDOffset)
{
    // Add the offset to the message ID
    var msgIDWithOffset = parseInt(msgIDOffset, 16) + parseInt(msgID, 16);

    // Output the packet definition
    ccdd.writeToFileLn(tlmFile, "\nCfeTelemetryPacket " + prefix + structureName);
    ccdd.writeToFileLn(tlmFile, "{");
    ccdd.writeToFileLn(tlmFile, "  applyWhen={FieldInRange{field = applicationId, range = " + extractMessageID(msgIDWithOffset.toString(16)) + "}},");
    outputStructureDefinition(structureName, true, tlmFile);
}

/*******************************************************************************
 * Output the telemetry structure prototype and packet definitions
 *
 * @param structureNames
 *            array of all structure table names, sorted by order of reference
 ******************************************************************************/
function outputStructures(structureNames)
{
    // Step through each structure name
    for (var structIndex = 0; structIndex < structureNames.length; structIndex++)
    {
        // Get the structure name to make subsequent calls shorter
        var structureName = structureNames[structIndex];

        // Get the value of the structure's message ID data field (if present)
        var msgID = ccdd.getTableDataFieldValue(structureName, "Message ID");

        // Check if the structure doesn't have a message ID
        if (!msgID)
        {
            // Check if the structure is referenced by more than one structure
            if (ccdd.isStructureShared(structureName))
            {
                // Output the structure prototype to the combined recs file
                ccdd.writeToFileLn(combFile, "\nprototype Structure " + structureName);
                ccdd.writeToFileLn(combFile, "{");
                outputStructureDefinition(structureName, false, combFile);
                ccdd.writeToFileLn(combFile, "\n}");
            }
            // The structure isn't referenced by multiple structures
            else
            {
                // Output the structure prototype to the rec file
                ccdd.writeToFileLn(tlmFile, "\nprototype Structure " + structureName);
                ccdd.writeToFileLn(tlmFile, "{");
                outputStructureDefinition(structureName, false, tlmFile);
                ccdd.writeToFileLn(tlmFile, "\n}");
            }
        }
        // The structure has a message ID
        else
        {
            // Check if there is more than one flight computer
            if (numFlightComputers > 1)
            {
                // Step through each flight computer
                for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
                {
                    // Output the telemetry packet definition
                    outputTelemetryPacket(fcNames[fcIndex], structureName, msgID, fcOffset[fcIndex]);
                    ccdd.writeToFileLn(tlmFile, "\n}");
                }
            }
            // There is a single flight computer
            else
            {
                // Output the telemetry packet definition
                outputTelemetryPacket("", structureName, msgID, "0");
                ccdd.writeToFileLn(tlmFile, "\n}");
            }
        }
    }
}

/*******************************************************************************
 * Output the commands
 *
 * @param prefix
 *            command name prefix
 *
 * @param msgIDOffset
 *            message ID offset
 *
 * @param system
 *            system name
 ******************************************************************************/
function outputCommands(prefix, msgIDOffset, system)
{
    // Step through each row in the command table
    for (var row = 0; row < numCommandRows; row++)
    {
        // Get the system with which he command is associated from the command
        // table's 'System' data field
        var commandSystem = ccdd.getTableDataFieldValue(ccdd.getCommandTableNameByRow(row), "System");

        // Check if the this command table's system matches the target system
        if (!system || (commandSystem && system.equals(commandSystem)))
        {
            // Get the command name and code, and the message ID for the command
            // table
            var commandName = ccdd.getCommandName(row);
            var cmdCode = ccdd.getCommandCode(row);
            var msgID = ccdd.getTableDataFieldValue(ccdd.getCommandTableNameByRow(row), "Message ID");
            var msgIDWithOffset = parseInt(msgIDOffset, 16);
            
            // Check if the message ID exists
            if (msgID)
            {
                msgIDWithOffset += parseInt(msgID, 16);
            }
            
            // Begin the command definition
            ccdd.writeToFileLn(cmdFile, "");
            ccdd.writeToFileLn(cmdFile, "CfeSoftwareCommand " + prefix + commandName);
            ccdd.writeToFileLn(cmdFile, "{");
            ccdd.writeToFileLn(cmdFile, "  applicationId {range=" + extractCommandID(msgIDWithOffset.toString(16)) + "}");
            ccdd.writeToFileLn(cmdFile, "  commandCode {range=" + parseInt(cmdCode, 16) + "}");

            // Process all of the command arguments for this command
            for (var argumentNum = 0; argumentNum < ccdd.getNumCommandArguments(row); argumentNum++)
            {
                // Get the command argument's name and data type
                var name = ccdd.getCommandArgName(argumentNum, row);
                var dataType = ccdd.getCommandArgDataType(argumentNum, row);

                // Get the size in bytes based on the data type
                var sizeInBytes = ccdd.getDataTypeSizeInBytes(dataType);

                // Check if the parameter has an argument
                if (name && name != "" && dataType && dataType != "")
                {
                     var argumentInfo = "";

                    // Get the single character ITOS encoded form of the data
                    // type
                    var itosEncode1Char = ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR");

                    // Check if the data type isn't recognized
                    if (!itosEncode1Char)
                    {
                        // Set the encoding character to a blank
                        itosEncode1Char = "";
                    }
                    
                    // Check if the parameter is an integer (signed or unsigned)
                    if (itosEncode1Char.equals("I") || itosEncode1Char.equals("U"))
                    {
                        // Get the command argument's enumeration value
                        var enumeration = ccdd.getCommandArgEnumeration(argumentNum, row);

                        // Check if this command has an enumeration
                        if (enumeration && enumeration != "")
                        {
                            // Add the associated enumeration definition
                            argumentInfo += "enumeration = " + getCommandEnumerationName(row, argumentNum) + ", ";
                        }

                        // Check that the argument has a valid data type
                        if (sizeInBytes != 0)
                        {
                            // Get the command argument's minimum and maximum
                            // values
                            var minimumValue = ccdd.getCommandArgMinimum(argumentNum, row);
                            var maximumValue = ccdd.getCommandArgMaximum(argumentNum, row);

                            // Check if a minimum value doesn't exist for this
                            // argument
                            if (!minimumValue || minimumValue == "")
                            {
                                // Set the minimum value to zero, assuming this
                                // is an unsigned integer
                                minimumValue = 0;

                                // Check if the argument is a signed integer
                                if (itosEncode1Char.equals("I"))
                                {
                                    // Set the minimum value to the largest
                                    // negative value for this size integer
                                    minimumValue = parseInt(-Math.pow(2, sizeInBytes * 8) / 2);
                                }
                            }

                            // Check if a maximum value doesn't exist for this
                            // argument
                            if (!maximumValue || maximumValue == "")
                            {
                                // Set the maximum value to the largest positive
                                // value for an unsigned integer
                                maximumValue = parseInt(Math.pow(2, sizeInBytes * 8) - 1);

                                // Check if the argument is a signed integer
                                if (itosEncode1Char.equals("I"))
                                {
                                    // Adjust the maximum to the largest size
                                    // for this size integer
                                    maximumValue = parseInt( (maximumValue - 1) / 2);
                                }
                            }

                            // Add the command argument range
                            argumentInfo += "range=" + minimumValue + ".." + maximumValue;
                        }
                    }
                    // Check if the parameter is a string
                    else if (itosEncode1Char.equals("S"))
                    {
                        // Get the command argument's array size value
                        var arraySize = ccdd.getCommandArgArraySize(argumentNum, row);

                        // Check if there is no array size provided
                        if (!arraySize || arraySize == "")
                        {
                            // Default to a single character
                            arraySize = "1";
                        }
                        // The array size exists
                        else
                        {
                            // Strip off all but the last array index - this is
                            // the string's length
                            arraySize = (arraySize + "").replace(/.*, /g, "");
                        }

                        // Set the 'lengthInCharacters' argument to capture the
                        // string's length
                        sizeInBytes = 1;
                        argumentInfo = "lengthInCharacters = " + arraySize;
                    }

                    // Output the command argument to the file
                    ccdd.writeToFileLn(cmdFile, "  " + itosEncode1Char + sizeInBytes + " " + name + " {" + argumentInfo + "}");
                }
            }

            ccdd.writeToFileLn(cmdFile, "}");
        }
    }
}

/*******************************************************************************
 * Output a single mnemonic definition
 *
 * @param row
 *            row index in the structure data table
 ******************************************************************************/
function outputMnemonicDefinition(row)
{
    // Get the variable data type
    var dataType = ccdd.getStructureDataType(row);

    // Get the single character ITOS encoded form of the data type
    var itosEncode = ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR");

    // Check if this data type is a recognized base type, and not a structure
    if (itosEncode && !itosEncode.equals(dataType))
    {
        // Check if the encoding is 'raw' (unrecognized)
        if (itosEncode.equals("R"))
        {
            // Default to "unsigned"
            itosEncode = "U";
        }

        // Get the variable name and array size
        var variableName = ccdd.getStructureVariableName(row);
        var arraySize = ccdd.getStructureArraySize(row);

        // Check if the variable is not an array definition
        var isVar = isVariable(variableName, arraySize);

        // Check if the variable is a string
        var isString = itosEncode.equals("S") && arraySize != "";

        // Set the output flag if this is a non-string variable
        var isOutputMnemonic = isVar && !isString;

        // Check if this is a string definition
        if (isString && !isVar)
        {
            // Set the flag if the string is not telemetered (if the first
            // member has no non-blank rate)
            isOutputMnemonic = !isTelemetry(+row + 1);
        }

        // Only output non-array variables or array members (i.e., skip array
        // definitions)
        if (isOutputMnemonic)
        {
            var structurePath = ccdd.getFullVariableName(row, ".");

            // Get the full variable name for this variable, which includes all
            // of the variable names in its structure path
            var fullVariableName = ccdd.getFullVariableName(row);

            var enumeration = null;

            // Get the enumeration(s)
            var enumerations = ccdd.getStructureEnumerations(row);

            // Check if any enumeration exists
            if (enumerations && enumerations.length != 0)
            {
                // Store the first enumeration
                enumeration = enumerations[0];
            }

            // Get the polynomial conversion and limit sets columns (if extant)
            var polynomial = ccdd.getStructureTableData("polynomial coefficients", row);
            var limitSet = ccdd.getStructureTableData("limit sets", row);

            // Step through each flight computer
            for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
            {
                // Output the mnemonic
                ccdd.writeToFile(tlmFile, itosEncode + " " + fcNames[fcIndex] + fullVariableName + " {sourceFields = {" + fcNames[fcIndex] + structurePath + "}");

                var isConversion = false;
                var isMultiple = false;

                // Check if the parameter includes an enumeration
                if (enumeration && enumeration != "")
                {
                    isConversion = true;
                    isMultiple = false;
                }

                // Check if this parameter includes a discrete or polynomial
                // conversion
                if (polynomial && polynomial != "")
                {
                    isConversion = true;
                    isMultiple = polynomial.split("\\;").length > 1;
                }

                // Check if there is an enumeration or polynomial conversion
                if (isConversion)
                {
                    // Check if there are conversions specific to each flight
                    // computer
                    if (isMultiple)
                    {
                        // Output the flight computer-specific conversion
                        // reference
                        ccdd.writeToFile(tlmFile, " conversion = " + fcNames[fcIndex] + fullVariableName + "_CONVERSION");
                    }
                    // There is only a single conversion
                    else
                    {
                        // Output the conversion reference
                        ccdd.writeToFile(tlmFile, " conversion = " + fullVariableName + "_CONVERSION");
                    }
                }

                // Check if this parameter includes a limit or limit set
                if (limitSet && limitSet != "")
                {
                    // Output the limit reference
                    ccdd.writeToFile(tlmFile, " limits = " + fullVariableName + "_LIMIT");
                }

                ccdd.writeToFileLn(tlmFile, "}");
            }
        }
    }
}

/*******************************************************************************
 * Output all of the mnemonic definitions
 ******************************************************************************/
function outputMnemonicDefinitions()
{
    ccdd.writeToFileLn(tlmFile, "");
    ccdd.writeToFileLn(tlmFile, "/* Mnemonic Definitions */");

    // Step through each row in the table
    for (var row = 0; row < numStructRows; row++)
    {
        // Check if the variable is not telemetered
        if (!isTelemetry(row))
        {
            // Output the mnemonic definition for this row in the data table
            outputMnemonicDefinition(row);
        }
    }
}

/*******************************************************************************
 * Output a single discrete conversion (enumeration)
 *
 * @param file
 *            file to which to write the discrete conversion
 *
 * @param discreteConversion
 *            discrete conversion information
 *
 * @param conversionName
 *            conversion name
 ******************************************************************************/
function outputDiscreteConversion(file, discreteConversion, conversionName)
{
    // Discrete conversion array indices
    var VALUE = 0;
    var DISP_NAME = 1;
    var TEXT_COLOR = 2;
    var BACK_COLOR = 3;

    // Separate the enumerated parameters into an array. The expected format for
    // the enumerated values is:
    // <Discrete Value> | <Display Name> | <Text Color> |
    // <Background Color> ... [, repeat for each discrete value...]
    var enumerations = ccdd.getArrayFromString(discreteConversion, "|", ",");

    // Check if the variable has enumerations and the required number of
    // parameters is provided
    if (enumerations && enumerations[0].length > 3)
    {
        // Output the discrete conversion header
        ccdd.writeToFileLn(file, "DiscreteConversion " + ccdd.getFullVariableName(conversionName, "_") + "_CONVERSION");
        ccdd.writeToFileLn(file, "{");

        // Step through each enumerated value
        for (var discrete = 0; discrete < enumerations.length; discrete++)
        {
            // Output the discrete conversion
            ccdd.writeToFile(file, "  Dsc " + enumerations[discrete][DISP_NAME] + " {range = " + enumerations[discrete][VALUE]);

            // Check if a background color is supplied
            if (enumerations[discrete][BACK_COLOR])
            {
                // Output the background color
                ccdd.writeToFile(file, ", bgColor = " + enumerations[discrete][BACK_COLOR]);
            }

            // Check if a foreground (text) color is supplied
            if (enumerations[discrete][TEXT_COLOR])
            {
                // Output the foreground color
                ccdd.writeToFile(file, ", fgColor = " + enumerations[discrete][TEXT_COLOR]);
            }

            ccdd.writeToFileLn(file, "}");
        }

        ccdd.writeToFileLn(file, "}");
    }
}

/*******************************************************************************
 * Output all of the telemetry discrete conversions
 ******************************************************************************/
function outputTelemetryDiscreteConversions()
{
    var isFirst = true;

    // Step through each row in the structure table
    for (var row = 0; row < numStructRows; row++)
    {
        var discreteConversion = null;

        // Get the enumeration(s)
        var enumerations = ccdd.getStructureEnumerations(row);

        // Check if any enumeration exists
        if (enumerations.length != 0)
        {
            // Store the first enumeration
            discreteConversion = enumerations[0];
        }

        // Check if the parameter has a discrete conversion
        if (discreteConversion)
        {
            // Check if this is the first discrete conversion
            if (isFirst)
            {
                // Write the discrete conversion header to the file
                ccdd.writeToFileLn(tlmFile, "");
                ccdd.writeToFileLn(tlmFile, "/* Discrete Conversions */");
                isFirst = false;
            }

            // Get the variable name and array size
            var variableName = ccdd.getStructureVariableName(row);
            var arraySize = ccdd.getStructureArraySize(row);

            // Only output non-array variables or array members (i.e., skip
            // array definitions)
            if (isVariable(variableName, arraySize))
            {
                // Get the full name and path for the variable on this row
                var fullVariableName = ccdd.getFullVariableName(row);

                // Output the discrete conversion for this row in the data table
                outputDiscreteConversion(tlmFile, discreteConversion, fullVariableName);
            }
        }
    }
}

/*******************************************************************************
 * Output all of the command discrete conversions
 ******************************************************************************/
function outputCommandDiscreteConversions()
{
    // Step through each row in the command table
    for (var row = 0; row < numCommandRows; row++)
    {
        // Step through each of the commands arguments
        for (var argumentNum = 0; argumentNum < ccdd.getNumCommandArguments(row); argumentNum++)
        {
            // Get the discrete conversion for this command based on the
            // argument number. Null is returned if no match is found for the
            // column name; it's assumed that no more argument columns exists
            // for this command
            var discreteConversion = ccdd.getCommandArgEnumeration(argumentNum, row);

            // Check if the parameter has a discrete conversion
            if (discreteConversion && discreteConversion != "")
            {
                // Check if this is the first discrete conversion
                if (argumentNum == 0)
                {
                    // Write the discrete conversions header to the file
                    ccdd.writeToFileLn(cmdFile, "");
                    ccdd.writeToFileLn(cmdFile, "/* Discrete Conversions */");
                }

                // Build the name for the conversion using the command and
                // argument names
                var fullCommandName = ccdd.getCommandName(row) + "_" + ccdd.getCommandArgName(argumentNum, row);

                // Output the discrete conversion for this row in the data table
                outputDiscreteConversion(cmdFile, discreteConversion, fullCommandName);
            }
        }
    }
}

/*******************************************************************************
 * Output a single enumeration
 *
 * @param enumeration
 *            enumeration information
 *
 * @param conversionName
 *            conversion name
 ******************************************************************************/
function outputCommandEnumeration(enumeration, enumerationName)
{
    // Enumeration array indices
    var VALUE = 0;
    var DISP_NAME = 1;

    // Separate the enumerated parameters into an array. The expected
    // format for the enumerated values is:
    // <Discrete Value> | <Display Name> | ... 
    // [, repeat for each discrete value...]
    var enumerations = ccdd.getArrayFromString(enumeration, "|", ",");

    // Check if the variable has enumerations and the required number of
    // parameters is provided
    if (enumerations && enumerations[0].length > 1)
    {
        // Output the enumeration header
        ccdd.writeToFileLn(cmdFile, "Enumeration " + enumerationName);
        ccdd.writeToFileLn(cmdFile, "{");

        // Step through each enumerated value
        for (var discrete = 0; discrete < enumerations.length; discrete++)
        {
            // Output the enumerated value
            ccdd.writeToFileLn(cmdFile, "  EnumerationValue " + enumerations[discrete][DISP_NAME] + " {value = " + enumerations[discrete][VALUE] + "}");
        }

        ccdd.writeToFileLn(cmdFile, "}");
    }
}

/*******************************************************************************
 * Output all of the command enumerations
 *
 * @param systemName
 *            system name
 ******************************************************************************/
function outputCommandEnumerations(systemName)
{
    // Step through each row in the command table
    for (var row = 0; row < numCommandRows; row++)
    {
        // Get the system with which the command is associated from the command
        // table's 'System' data field
        var commandSystem = ccdd.getTableDataFieldValue(ccdd.getCommandTableNameByRow(row), "System");

        // Check if the this command table's system matches the target system
        if (!systemName || (commandSystem && systemName.equals(commandSystem)))
        {
            // Step through each of the commands arguments
            for (var argumentNum = 0; argumentNum < ccdd.getNumCommandArguments(row); argumentNum++)
            {
                // Get the command argument's enumeration value
                var enumeration = ccdd.getCommandArgEnumeration(argumentNum, row);

                // Check if this command has an enumeration
                if (enumeration && enumeration != "")
                {
                    // Check if this is the first enumeration for the command
                    if (argumentNum == 0)
                    {
                        // Write the enumerations header to the file
                        ccdd.writeToFileLn(cmdFile, "");
                        ccdd.writeToFileLn(cmdFile, "/* Enumerations */");
                    }

                    // Output the enumeration for this row in the data table
                    outputCommandEnumeration(enumeration, getCommandEnumerationName(row, argumentNum));
                }
            }
        }
    }
}

/*******************************************************************************
 * Output a single limit or limit set definition
 *
 * @param row
 *            row index in the structure data table
 *
 * @param limitSets
 *            limit set(s)
 *
 * @param isFirst
 *            true if this is the first limit definition
 *
 * @return true if a limit definition is output
 ******************************************************************************/
function outputLimitDefinition(row, limitSets, isFirst)
{
    // Get the variable name and array size
    var variableName = ccdd.getStructureVariableName(row);
    var arraySize = ccdd.getStructureArraySize(row);

    // Only output non-array variables or array members (i.e., skip array
    // definitions)
    if (isVariable(variableName, arraySize))
    {
        // Separate the limits into an array
        var limits = ccdd.getArrayFromString(limitSets, "|", ",");

        // Check if the variable has limits
        if (limits)
        {
            // Check if this is the first limit definition
            if (isFirst)
            {
                // Write the limit definitions header to the file
                ccdd.writeToFileLn(tlmFile, "");
                ccdd.writeToFile(tlmFile, "/* Limit Definitions */");
                isFirst = false;
            }

            // Check if a single limit is specified
            if (limits.length == 1)
            {
                // Output the limit header
                ccdd.writeToFileLn(tlmFile, "");
                ccdd.writeToFileLn(tlmFile, "Limit " + ccdd.getFullVariableName(row) + "_LIMIT");
                ccdd.writeToFileLn(tlmFile, "{");

                // Step through each limit definition
                for (var index = 0; index < limits[0].length; index++)
                {
                    // Check if this is is the red-low, yellow-low, yellow-high,
                    // or red-high limit
                    if (index < 4 && limits[0][index] != "")
                    {
                        // Output the limit
                        ccdd.writeToFileLn(tlmFile, "  " + ccdd.getITOSLimitName(index) + " = " + limits[0][index]);
                    }
                }

                ccdd.writeToFileLn(tlmFile, "}");
            }
            // Multiple limits are specified
            else if (limits.length > 1)
            {
                // Output the limit set header
                ccdd.writeToFileLn(tlmFile, "");
                ccdd.writeToFileLn(tlmFile, "LimitSet " + ccdd.getFullVariableName(row) + "_LIMIT");
                ccdd.writeToFileLn(tlmFile, "{");
                ccdd.writeToFileLn(tlmFile, "  contextMnemonic = " + limits[0][0]);
                ccdd.writeToFileLn(tlmFile, "");

                // Step through each limit set
                for (var set = 1; set < limits.length; set++)
                {
                    // Check if this is not the first limit value
                    if (set != 1)
                    {
                        // Output a line feed
                        ccdd.writeToFileLn(tlmFile, "");
                    }

                    // Output the limit header
                    ccdd.writeToFileLn(tlmFile, "  Limit limit" + set);
                    ccdd.writeToFileLn(tlmFile, "  {");

                    var limitIndex = 0;

                    // Step through each limit definition
                    for (var index = 0; index < limits[set].length; index++)
                    {
                        // Check if the limit value exists
                        if (limits[set][index] != "")
                        {
                            // Check if this is the context range
                            if (limits[set][index].contains(".."))
                            {
                                // Output the context range
                                ccdd.writeToFileLn(tlmFile, "    contextRange = " + limits[set][index]);
                            }
                            // Not the context range; must be a limit value
                            else
                            {
                                // Output the limit value
                                ccdd.writeToFileLn(tlmFile, "    " + ccdd.getITOSLimitName(limitIndex) + " = " + limits[set][index]);

                                limitIndex++;
                            }
                        }
                    }

                    ccdd.writeToFileLn(tlmFile, "  }");
                }

                ccdd.writeToFileLn(tlmFile, "}");
            }
        }
    }

    return isFirst;
}

/*******************************************************************************
 * Output all of the limit and limit set definitions
 ******************************************************************************/
function outputLimitDefinitions()
{
    var isFirst = true;

    // Step through each row in the table
    for (var row = 0; row < numStructRows; row++)
    {
        // Get the limits for this row
        var limitSets = ccdd.getStructureTableData("limit sets", row);

        // Check if the parameter has limits
        if (limitSets && limitSets != "")
        {
            // Output the limit definition for this row in the data table
            isFirst = outputLimitDefinition(row, limitSets, isFirst);
        }
    }
}

/*******************************************************************************
 * Output a single polynomial conversion
 *
 * @param prefix
 *            conversion name prefix
 *
 * @param variableName
 *            variable name
 *
 * @param coeffs
 *            polynomial coefficient array
 ******************************************************************************/
function outputPolynomial(prefix, variableName, coeffs)
{
    // Output the polynomial conversion header
    ccdd.writeToFileLn(tlmFile, "");
    ccdd.writeToFile(tlmFile, "PolynomialConversion " + prefix + variableName + "_CONVERSION");
    ccdd.writeToFileLn(tlmFile, "{");
    ccdd.writeToFile(tlmFile, "  coefficients = {");

    // Output the first coefficient (with no preceding comma)
    ccdd.writeToFile(tlmFile, coeffs[0]);

    // Step through each remaining coefficient value
    for (var index = 1; index < coeffs.length; index++)
    {
        // Output the coefficient, preceded by a comma
        ccdd.writeToFile(tlmFile, ", " + coeffs[index]);
    }

    ccdd.writeToFileLn(tlmFile, "}");
    ccdd.writeToFileLn(tlmFile, "}");
}

/*******************************************************************************
 * Output a single polynomial conversion
 *
 * @param row
 *            row index in the structure data table
 *
 * @param polynomialCoefficients
 *            one or more polynomial coefficient sets from the data table
 ******************************************************************************/
function outputPolynomialConversion(row, polynomialCoefficients)
{
    // Get the variable name and array size
    var variableName = ccdd.getStructureVariableName(row);
    var arraySize = ccdd.getStructureArraySize(row);

    // Only output non-array variables or array members (i.e., skip array
    // definitions)
    if (isVariable(variableName, arraySize))
    {
        // Separate the sets of the coefficients (if there is more than one)
        var polySets = polynomialCoefficients.split("\\;");

        // Get the number of coefficient sets
        var numPolySets = polySets.length;

        // Check if the number of flight computers is less than the number of
        // sets detected
        if (numFlightComputers < numPolySets)
        {
            // Reduce the number of sets to match the number of flight computers
            numPolySets = numFlightComputers;
        }

        var prefix = "";
        var polyIndex = 0;

        // Step through each set of polynomial coefficients
        for (polyIndex = 0; polyIndex < numPolySets; polyIndex++)
        {
            // Check if there is more than one set
            if (numPolySets > 1)
            {
                // Set the prefix to the flight computer name
                prefix = fcNames[polyIndex];
            }

            // Separate the polynomial coefficients into an array
            var coeffs = ccdd.getArrayFromString(polySets[polyIndex], "|");

            // Output the polynomial conversion definition
            outputPolynomial(prefix, ccdd.getFullVariableName(row), coeffs)
        }

        // Check if there is more than one set
        if (numPolySets > 1)
        {
            // Get the index of last valid set of coefficients
            var lastPolyindex = (polyIndex - 1);

            // Separate the polynomial coefficients into an array
            var coeffs = ccdd.getArrayFromString(polySets[lastPolyindex], "|");

            // Step through any remaining flight computers that don't have a
            // polynomial coefficient set
            for (; polyIndex < numFlightComputers; polyIndex++)
            {
                // Set the prefix to the flight computer name
                prefix = fcNames[polyIndex];

                // Output the polynomial conversion definition using the
                // coefficients from the last defined set
                outputPolynomial(prefix, ccdd.getFullVariableName(row), coeffs)
            }
        }
    }
}

/*******************************************************************************
 * Output all of the polynomial conversions
 ******************************************************************************/
function outputPolynomialConversions()
{
    var isFirst = true;

    // Step through each row in the table
    for (var row = 0; row < numStructRows; row++)
    {
        // Get the polynomial coefficients for this row
        var polynomialCoefficients = ccdd.getStructureTableData("polynomial coefficients", row);

        // Check if the parameter has polynomial coefficients
        if (polynomialCoefficients && polynomialCoefficients != "")
        {
            // Check if this is the first polynomial conversion
            if (isFirst)
            {
                // Write the polynomial conversion header to the file
                ccdd.writeToFileLn(tlmFile, "");
                ccdd.writeToFileLn(tlmFile, "/* Polynomial Conversions  -- a list of constants  {a0,a1,a2,,,an}    ,  where  y= a0 + a1*x + a2*x^2 + ... an*x^n  */");
                isFirst = false;
            }

            // Output the polynomial conversion for this row in the data table
            outputPolynomialConversion(row, polynomialCoefficients);
        }
    }
}
/** End functions *********************************************************** */

/** Main ******************************************************************** */

var newRowOrder = [];
var fcNames = [];
fcOffset = [];
var numFlightComputers = 0;

// Get the number of structure and command table rows
var numStructRows = ccdd.getStructureTableNumRows();
var numCommandRows = ccdd.getCommandTableNumRows();

// Check if no structure or command data is supplied
if (numStructRows == 0 && numCommandRows == 0)
{
    showErrorDialog("No structure or command data supplied to script " + ccdd.getScriptName());
}
// Structure and/or command data is supplied
else
{
    var endianess = "";
    var endianExtn = "";
    var tmpVal = 0;

    // Get the value of the data field specifying the message ID skip value
    var msgIDSkip = ccdd.getGroupDataFieldValue("globals", "MID_delta");

    // Check if the data field exists or is empty
    if (!msgIDSkip || msgIDSkip == "")
    {
        // Use the default skip value
        msgIDSkip = "0x600";
    }

    // Get the value of the data field specifying the flight computer offset
    // value
    var fcOffsetVal = ccdd.getGroupDataFieldValue("globals", "FC_Offset");

    // Check if the data field exists or is empty
    if (!fcOffsetVal || fcOffsetVal == "")
    {
        // Use the default offset value
        fcOffset = ["0x0000"];
    }

    // Get the value of the data field specifying the flight computer base value
    var fcBase = ccdd.getGroupDataFieldValue("globals", "prefix");

    // Check if the data field exists or is empty
    if (!fcBase|| fcBase == "")
    {
        // Use the default base value
        fcBase = "FC";
    }

    // Get the value of the data field specifying the number of flight computers
    numFC = ccdd.getGroupDataFieldValue("globals", "NumComputers");

    // Check if the data field exists, is empty, or isn't an integer value
    if (!numFC || !numFC.match(/[0-9]+/g))
    {
        // Use the default number of flight computers (based on the number of
        // offset
        // values detected)
        numFlightComputers = fcOffset.length;
    }
    // The value is an integer
    else
    {
        // Store the number of flight computers
        numFlightComputers = parseInt(numFC);
    }

    // Check if there is more than one flight computer
    if (numFlightComputers > 1)
    {
        // Step through each flight computer
        for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
        {
            // Store the flight computer name prefix and offset value
            fcNames[fcIndex] = fcBase + (fcIndex + 1) + "_";
            fcOffset[fcIndex] = fcOffsetVal;

            // Calculate the next offset based on the current offset and the ID
            // skip values
            var nextMsgID = parseInt(fcOffsetVal, 16) + parseInt(msgIDSkip, 16);
            fcOffsetVal = "0x" + nextMsgID.toString(16);
        }
    }
    // Only one flight computer
    else
    {
        // No prefix or offset for a single computer
        fcOffset = ["0x000"];
        fcNames = [""];
    }

    // Define the radio button text and descriptions for the dialog
    var buttons = [["Big", "Big endian"], ["Big (swap)", "Big endian (word swapped)"], ["Little", "Little endian"], ["Little (swap)", "Little endian (word swapped)"]];

    // Get the endianess choice from the user
    var selected = ccdd.getRadioButtonDialog("Select endianess", Java.to(buttons, "java.lang.String[][]"));

    // Check that an endianess was selected
    if (selected)
    {
        // Check if the output should be big endian
        if (selected.equals("Big"))
        {
            endianess = "BIG_ENDIAN";
            endianExtn = "BE";
        }
        // Check if the output should be big endian, word swapped
        else if (selected.equals("Big (swap)"))
        {
            endianess = "BIG_ENDIAN_SWAP";
            endianExtn = "BE";
        }
        // Check if the output is little endian
        else if (selected.equals("Little"))
        {
            endianess = "LITTLE_ENDIAN";
            endianExtn = "LE";
        }
        // Check if the output is little endian, word swapped
        else if (selected.equals("Little (swap)"))
        {
            endianess = "LITTLE_ENDIAN_SWAP";
            endianExtn = "LE";
        }

        // Create the structure row order array that rearranges the bit-packed
        // variables based on endianess
        newRowOrder = reorderRowsForByteOrder(endianExtn);

        // Get the current date and time
        var dateAndTime = ccdd.getDateAndTime();

        // Check if structure data is provided
        if (numStructRows > 0)
        {
            // The output file names are based in part on the value of the data
            // field, 'System', found in the first group or table associated
            // with the script. If the field can't be found in either then the
            // value is set to a blank
            var systemName = null;

            // Get the group(s) associated with the script (if any)
            var groupNames = ccdd.getAssociatedGroupNames();

            // Check if a group is associated with the script
            if (groupNames.length != 0)
            {
                // Get the value of the first group's 'System' data field, if
                // present
                systemName = ccdd.getGroupDataFieldValue(groupNames[0], "System");
            }

            // Check if the system name wasn't found in the group data field
            if (!systemName)
            {
                // Get the value of the first root structure's 'System' data
                // field
                systemName = ccdd.getTableDataFieldValue(ccdd.getRootStructureTableNames()[0], "System");
            }

            // Check if the data field doesn't exist in either a group or table
            if (!systemName)
            {
                systemName = "";
            }

            // Build the telemetry output file name
            var tlmOutputFile = ccdd.getOutputPath() + systemName + "_" + endianExtn + ".rec";

            // Open the telemetry output file
            var tlmFile = ccdd.openOutputFile(tlmOutputFile);
            var combFile = ccdd.openOutputFile(ccdd.getOutputPath() + "common.rec");

            // Check if the telemetry output file successfully opened
            if (tlmFile || combFile)
            {
                // Get the names of all structures/sub-structures referenced in
                // tables
                var structureNames = ccdd.getStructureTablesByReferenceOrder();

                // Add a header to the output files
                outputFileCreationInfo(combFile);
                outputFileCreationInfo(tlmFile);

                // Output the structure prototypes and telemetry packet
                // definitions
                outputStructures(structureNames);

                // Output the discrete conversions
                outputTelemetryDiscreteConversions();

                // Output the limit definitions
                outputLimitDefinitions();

                // Output the polynomial conversions
                outputPolynomialConversions();

                // Output the mnemonic definitions
                outputMnemonicDefinitions();

                // Close the telemetry output files
                ccdd.closeFile(tlmFile);
                ccdd.closeFile(combFile);
            }
            // The telemetry output files cannot be opened
            else
            {
                // Display an error dialog
                ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + tlmOutputFile + "<b>' or common.rec");
            }
        }

        // Check if command data is provided
        if (numCommandRows > 0)
        {
            // Get the value of the 'System' data field for first command table
            var firstSystemName = ccdd.getTableDataFieldValue(ccdd.getCommandTableNames()[0], "System");

            // If the system name doesn't exist then substitute a blank
            if (!firstSystemName)
            {
                firstSystemName = "";
            }

            // Step through each flight computer
            for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
            {
                var msgIDOffset = fcOffset[fcIndex];
                var prefix = fcNames[fcIndex];

                // Build the command output file name and open the command
                // output file
                var cmdFileName = ccdd.getOutputPath() + prefix + firstSystemName + "_CMD" + "_" + endianExtn + ".rec";
                var cmdFile = ccdd.openOutputFile(cmdFileName);

                // Check if the command output file successfully opened
                if (cmdFile)
                {
                    // Add a header to the output file
                    outputFileCreationInfo(cmdFile);

                    // Step through each command table
                    for (var cmdTblIndex = 0; cmdTblIndex < ccdd.getCommandTableNames().length; cmdTblIndex++)
                    {
                        // Get the value of the 'System' data field
                        var systemName = ccdd.getTableDataFieldValue(ccdd.getCommandTableNames()[cmdTblIndex], "System");

                        // Output the enumerations for this system
                        outputCommandEnumerations(systemName);

                        // Output the commands for this system
                        outputCommands(prefix, msgIDOffset, systemName);
                    }

                    // Close the command output file
                    ccdd.closeFile(cmdFile);
                }
                // The command output file cannot be opened
                else
                {
                    // Display an error dialog
                    ccdd.showErrorDialog("<html><b>Error opening command output file '</b>" + cmdFileName + "<b>'");
                }
            }
        }
    }
}
