/*******************************************************************************
 * Description: Output a message ID header file
 *
 * This JavaScript script generates a message ID header file from the supplied
 * table information
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

// Get the number of structure and command table rows
var numStructRows = ccdd.getStructureTableNumRows();
var numCommandRows = ccdd.getCommandTableNumRows();

// Get the name of the project database
var projectName = ccdd.getProject();

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
 * Output the telemetry message IDs file
 *
 * @param baseFileName
 *            base for the telemetry IDs output file name
 ******************************************************************************/
function makeTelemetryFile(baseFileName)
{
    // Build the telemetry message IDs output file name and include flag
    var tlmFileName = ccdd.getOutputPath() + baseFileName + ".h";
    var headerIncludeFlag = "_" + baseFileName.toUpperCase() + "_H_";

    // Open the telemetry message IDs output file
    var tlmFile = ccdd.openOutputFile(tlmFileName);

    // Check if the telemetry message IDs file successfully opened
    if (tlmFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(tlmFile);

        // Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(tlmFile, "#ifndef " + headerIncludeFlag);
        ccdd.writeToFileLn(tlmFile, "#define " + headerIncludeFlag);
        ccdd.writeToFileLn(tlmFile, "");

        ccdd.writeToFileLn(tlmFile, "#include \"" + projectName + "_base_ids.h\"");
        ccdd.writeToFileLn(tlmFile, "");

        // Get an array containing all group names
        var groupNames = ccdd.getGroupNames(false);

        var minimumLength = 12;

        // Step through each structure name
        for (var nameIndex = 0; nameIndex < structureNames.length; nameIndex++)
        {
            // Get the value of the structure's message ID name data field
            var msgIDName = ccdd.getTableDataFieldValue(structureNames[nameIndex], "Message ID Name");

            // Check if the field exists and isn't empty, and the length exceeds
            // the minimum length found thus far
            if (msgIDName != null && !msgIDName.isEmpty() && msgIDName.length() > minimumLength)
            {
                // Store the new minimum length
                minimumLength = msgIDName.length();
            }
        }

        // Step through each group name
        for (var groupIndex = 0; groupIndex < groupNames.length; groupIndex++)
        {
            // Get the value of the group's message ID name data field
            var msgIDName = ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID Name");

            // Check if the field exists and isn't empty, and the length exceeds
            // the minimum length found thus far
            if (msgIDName != null && !msgIDName.isEmpty() && msgIDName.length() > minimumLength)
            {
                // Store the new minimum length
                minimumLength = msgIDName.length();
            }
        }

        // Build the format string used to align the message ID definitions
        var format = "#define %-" + (minimumLength + 1) + "s %s\n";

        ccdd.writeToFileLn(tlmFile, "/* Structure message IDs: " + structureNames.length + " structures */");

        // Step through each structure name
        for (var nameIndex = 0; nameIndex < structureNames.length; nameIndex++)
        {
            // Get the values of the structure's message ID and ID name data
            // fields
            var msgID = ccdd.getTableDataFieldValue(structureNames[nameIndex], "Message ID");
            var msgIDName = ccdd.getTableDataFieldValue(structureNames[nameIndex], "Message ID Name");

            // Output the telemetry message ID to the file
            outputIDDefine(tlmFile, format, msgID, msgIDName);
        }

        ccdd.writeToFileLn(tlmFile, "");
        ccdd.writeToFileLn(tlmFile, "/* Group message IDs: " + groupNames.length + " groups */");

        // Step through each group
        for (var groupIndex = 0; groupIndex < groupNames.length; groupIndex++)
        {
            // Get the values of the group's message ID and ID name data fields
            var msgID = ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID");
            var msgIDName = ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID Name");

            // Output the telemetry message ID to the file
            outputIDDefine(tlmFile, format, msgID, msgIDName);
        }

        // Finish and close the telemetry message IDs output file
        ccdd.writeToFileLn(tlmFile, "");
        ccdd.writeToFileLn(tlmFile, "#endif /* #ifndef " + headerIncludeFlag + " */");
        ccdd.closeFile(tlmFile);
    }
    // The telemetry message IDs file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening telemetry message IDs output file '</b>" + tlmFileName + "<b>'");
    }
}

/*******************************************************************************
 * Output the command codes file
 *
 * @param baseFileName
 *            base for the command codes output file name
 ******************************************************************************/
function makeCommandFile(baseFileName)
{
    // Build the command codes output file name and include flag
    var cmdFileName = ccdd.getOutputPath() + baseFileName + ".h";
    var headerIncludeFlag = "_" + baseFileName.toUpperCase() + "_H_";

    // Open the command codes output file
    var cmdFile = ccdd.openOutputFile(cmdFileName);

    // Check if the command codes file successfully opened
    if (cmdFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(cmdFile);

        // Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(cmdFile, "#ifndef " + headerIncludeFlag);
        ccdd.writeToFileLn(cmdFile, "#define " + headerIncludeFlag);
        ccdd.writeToFileLn(cmdFile, "");

        var minimumLength = 10;

        // Step through each command table row
        for (var row = 0; row < numCommandRows; row++)
        {
            // Get the command name
            var cmdName = ccdd.getCommandName(row);

            // Check if the command name is present and the length exceeds the
            // minimum length found thus far
            if (cmdName != null && cmdName.length() > minimumLength)
            {
                // Store the new minimum length
                minimumLength = cmdName.length();
            }
        }

        // Build the format string used to align the command code definitions
        var format = "#define %-" + (minimumLength + 1) + "s %s\n";

        // Step through each command
        for (var row = 0; row < numCommandRows; row++)
        {
            // Get the command ID name and ID value
            var cmdName = ccdd.getCommandName(row);
            var cmdCode = ccdd.getCommandCode(row);

            // Check if the name and ID exist
            if (cmdCode != null && cmdName != null)
            {
                // Output the formatted command code definition to the file
                ccdd.writeToFileFormat(cmdFile, format, cmdName, cmdCode);
            }
        }

        // Finish and close the command codes output file
        ccdd.writeToFileLn(cmdFile, "");
        ccdd.writeToFileLn(cmdFile, "#endif /* #ifndef " + headerIncludeFlag + " */");
        ccdd.closeFile(cmdFile);
    }
    // The command codes file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening command codes output file '</b>" + cmdFileName + "<b>'");
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
 * Output the message ID definition to the specified file with a base value
 * added
 *
 * @param file
 *            reference to the file to which to output the message ID
 *            information
 *
 * @param format
 *            output format string
 *
 * @param msgId
 *            message ID
 *
 * @param msgIDName
 *            message ID name
 ******************************************************************************/
function outputIDDefine(file, format, msgID, msgIDName)
{
    // Check if the message ID and ID name are present
    if (msgID != null && !msgID.isEmpty() && msgIDName != null && !msgIDName.isEmpty())
    {
        // Remove all but the last 12 bits of the ID, format it, and output the
        // #define for the ID to the file with a base value added
        ccdd.writeToFileFormat(file, format, msgIDName, "( " + ccdd.getProject().toUpperCase() + "_TLM_MID_BASE_1 + " + extractMessageID(msgID) + " )");
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Check if structure and/or command data is supplied
if (numStructRows != 0 || numCommandRows != 0)
{
    // Check if structure data is supplied
    if (numStructRows != 0)
    {
        // Output the telemetry message IDs file
        makeTelemetryFile(projectName + "_tlm_ids");
    }

    // Check if command data is supplied
    if (numCommandRows != 0)
    {
        // Output the command codes file
        makeCommandFile(projectName + "_cmd_codes");
    }
}
// No structure or command data is supplied
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>No structure or command data supplied for script '</b>" + ccdd.getScriptName() + "<b>'");
}
