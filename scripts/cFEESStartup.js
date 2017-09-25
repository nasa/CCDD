/*******************************************************************************
 * Description: Output a cFE ES start-up script file
 *
 * This JavaScript script generates a cFE ES start-up file from the supplied
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

// cFE ES start-up script table type name
var ES_STARTUP_TYPE = "ES Start-up Script";

// cFE ES start-up script entry array indices
var MODULE_TYPE = 0;
var PATH_NAME = 1;
var ENTRY_POINT = 2;
var CFE_NAME = 3;
var PRIORITY = 4;
var STACK_SIZE = 5;
var EXCEPTION_ACTION = 6;

// Get the number of cFE ES start-up script table rows
var numRows = ccdd.getTableNumRows(ES_STARTUP_TYPE);

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
 * Output the cFE ES start-up script file
 *
 * @param baseFileName
 *            base for the cFE ES start-up script file name
 ******************************************************************************/
function makeESStartupFile(baseFileName)
{
    // Build the cFE ES start-up script file name and include flag
    var startupFileName = ccdd.getOutputPath() + baseFileName + ".scr";

    // Open the cFE ES start-up script file
    var startupFile = ccdd.openOutputFile(startupFileName);

    // Check if the cFE ES start-up script file successfully opened
    if (startupFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(startupFile);

        // Default column widths. The widths of the individual ES start-up
        // script entries can increase these values
        var columnWidth = [7, 6, 5, 4, 8, 5, 9];

        var startupEntries = [];

        // Step through each ES start-up script table row
        for (var row = 0; row < numRows; row++)
        {
            // Get the values of the start-up script table columns
            startupEntries.push([ccdd.getTableData(ES_STARTUP_TYPE, "Module Type", row), ccdd.getTableData(ES_STARTUP_TYPE, "Path & File", row), ccdd.getTableData(ES_STARTUP_TYPE, "Entry Point", row), ccdd.getTableData(ES_STARTUP_TYPE, "cFE Name", row), ccdd.getTableData(ES_STARTUP_TYPE, "Priority", row), ccdd.getTableData(ES_STARTUP_TYPE, "Stack Size", row), ccdd.getTableData(ES_STARTUP_TYPE, "Exception Action", row)]);
        }

        // Adjust the minimum column widths
        columnWidth = ccdd.getLongestStrings(startupEntries, columnWidth);

        // Build the format strings so that the columns in each row are aligned
        var formatHeader = "/* %-" + columnWidth[MODULE_TYPE] + "s | %-" + columnWidth[PATH_NAME] + "s | %-" + columnWidth[ENTRY_POINT] + "s | %-" + columnWidth[CFE_NAME] + "s | %-" + columnWidth[PRIORITY] + "s | %-" + columnWidth[STACK_SIZE] + "s | %-6s | %s */\n";
        var formatBody = "   %-" + columnWidth[MODULE_TYPE] + "s , %-" + columnWidth[PATH_NAME] + "s , %-" + columnWidth[ENTRY_POINT] + "s , %-" + columnWidth[CFE_NAME] + "s , %-" + columnWidth[PRIORITY] + "s , %-" + columnWidth[STACK_SIZE] + "s , %-6s , %s;\n";

        // Output the column titles
        ccdd.writeToFileFormat(startupFile, formatHeader, "Module", "Path &", "Entry", "cFE", "Priority", "Stack", "Unused", "Exception");
        ccdd.writeToFileFormat(startupFile, formatHeader, "Type", "File", "Point", "Name", "", "Size", "", "Action");

        // Step through each ES start-up script entry
        for (var row = 0; row < startupEntries.length; row++)
        {
            // Write the entry to the cFE ES start-up script file
            ccdd.writeToFileFormat(startupFile, formatBody, startupEntries[row][MODULE_TYPE], startupEntries[row][PATH_NAME], startupEntries[row][ENTRY_POINT], startupEntries[row][CFE_NAME], startupEntries[row][PRIORITY], startupEntries[row][STACK_SIZE], "0x0", startupEntries[row][EXCEPTION_ACTION]);
        }

        // Close the cFE ES start-up script file
        ccdd.closeFile(startupFile);
    }
    // The cFE ES start-up script file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening cFE ES start-up script file '</b>" + startupFileName + "<b>'");
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Check if ES start-up script data is supplied
if (numRows != 0)
{
    // Output the cFE ES start-up script file
    makeESStartupFile("cfe_es_startup");
}
// No ES start-up script data is supplied
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>No cFE ES start-up script data supplied for script '</b>" + ccdd.getScriptName() + "<b>'");
}
