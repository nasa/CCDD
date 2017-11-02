/*******************************************************************************
 * Description: CCDD tutorial
 * 
 * This JavaScript script is for use with the CCDD tutorial
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

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Get the array containing the table names, including paths for structure
// tables
var tableNames = ccdd.getTableNames();

// Check if a table is associated with the script
if (tableNames.length != 0)
{
    // Set the tutorial output file name
    var outputFileName = "tutorial.output";

    // Open the output file
    var outputFile = ccdd.openOutputFile(outputFileName);

    // Check if the output file successfully opened
    if (outputFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(outputFile);

        // Step through each table name
        for (var index = 0; index < tableNames.length; index++)
        {
            var tableData = [];

            // Get the table's type
            var tableType = ccdd.getTypeNameByTable(tableNames[index]);

            // Get the names of the table columns
            var columnNames = ccdd.getTableColumnNamesByType(tableType);

            // Add the array of column names as the first row of table data.
            // This allows the column names to be included when determining the
            // the minimum column widths
            tableData.push(columnNames);

            // Step through each table row
            for (var row = 0; row < ccdd.getStructureTableNumRows(); row++)
            {
                // Check if the name in the row matches the current name
                if (tableNames[index].equals(ccdd.getPathByRow(tableType, row)))
                {
                    var rowData = [];

                    // Step through each column in the row
                    for (var column = 0; column < columnNames.length; column++)
                    {
                        // Add the column data to the row array
                        rowData.push(ccdd.getTableData(tableType, columnNames[column], row));
                    }

                    // Add the row of data to the table data array
                    tableData.push(rowData);
                }
            }

            // Adjust the minimum column widths based on the column names and
            // data
            var columnWidths = ccdd.getLongestStrings(tableData, null);

            // Output the table name, type, and description to the output file
            ccdd.writeToFileLn(outputFile, "");
            ccdd.writeToFileLn(outputFile, "Table : " + tableNames[index]);
            ccdd.writeToFileLn(outputFile, "  Type       : " + tableType);
            ccdd.writeToFileLn(outputFile, "  Description: " + ccdd.getTableDescription(tableNames[index]));
            ccdd.writeToFileLn(outputFile, "");
            ccdd.writeToFile(outputFile, "  ");

            // Step through each column
            for (var column = 0; column < columnNames.length; column++)
            {
                // Output the column names
                ccdd.writeToFileFormat(outputFile, "| %-" + columnWidths[column] + "s ", columnNames[column]);
            }

            ccdd.writeToFileLn(outputFile, "|");
            ccdd.writeToFile(outputFile, "  ");

            // Step through each column
            for (var column = 0; column < columnNames.length; column++)
            {
                // Output dashes to separate the column name row from that table
                // data row(s)
                ccdd.writeToFile(outputFile, "| " + Array(parseInt(columnWidths[column]) + 1).join("-") + " ");
            }

            ccdd.writeToFileLn(outputFile, "|");

            // Step through each row of table data. Skip the first row, which
            // contains the column names, since it's already been output
            for (var row = 1; row < tableData.length; row++)
            {
                ccdd.writeToFile(outputFile, "  ");

                // Step through each column
                for (var column = 0; column < columnNames.length; column++)
                {
                    // Output the table value for the current row and column
                    ccdd.writeToFileFormat(outputFile, "| %-" + columnWidths[column] + "s ", tableData[row][column]);
                }

                ccdd.writeToFileLn(outputFile, "|");
            }

            ccdd.writeToFileLn(outputFile, "");

            // Get the name(s) of data field(s) associated with this table
            var fieldNames = ccdd.getTableDataFieldNames(tableNames[index]);

            // Check if the table has any data fields
            if (fieldNames.length != 0)
            {
                // Adjust the minimum column width based on the column names
                var columnWidth = ccdd.getLongestString(fieldNames, null);

                ccdd.writeToFileLn(outputFile, "  Data Fields:");

                // Step through each data field name
                for (var field = 0; field < fieldNames.length; field++)
                {
                    // Output the data field contents
                    ccdd.writeToFileFormat(outputFile, "    Name: %-" + columnWidth + "s  Value: %s\n", fieldNames[field], ccdd.getTableDataFieldValue(tableNames[index], fieldNames[field]));
                }

                ccdd.writeToFileLn(outputFile, "");
            }
        }

        // Check if any command data is present
        if (ccdd.getCommandTableNumRows() > 0)
        {
            // Step through each command definition
            for (var row = 0; row < ccdd.getCommandTableNumRows(); row++)
            {
                // Output the command name and code
                ccdd.writeToFileLn(outputFile, "");
                ccdd.writeToFileLn(outputFile, "Command: " + ccdd.getCommandName(row) + "\n  Code : " + ccdd.getCommandCode(row));

                // Step through each of the command's arguments
                for (var arg = 0; arg < ccdd.getNumCommandArguments(row); arg++)
                {
                    var argColNames = ccdd.getCommandArgColumnNames(arg, row);

                    // Adjust the minimum column width based on the column names
                    var columnWidth = ccdd.getLongestString(argColNames, null);

                    // Output the argument number
                    ccdd.writeToFileLn(outputFile, "  Arg " + (arg + 1) + ": ");

                    // Step through each column name belonging to the command argument
                    for (var index = 0; index < argColNames.length; index++)
                    {
                        // Get the value for the command argument column
                        var argValue = ccdd.getCommandArgByColumnName(arg, row, argColNames[index]);

                        // Check if the value isn't blank
                        if (!argValue.isEmpty())
                        {
                            // Output the argument column name and value
                            ccdd.writeToFileFormat(outputFile, "    %-" + columnWidth + "s  Value: %s\n", argColNames[index], argValue);
                        }
                    }
                }
            }
        }

        // Close the output file
        ccdd.closeFile(outputFile);
    }
    // The output file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening tutorial output file '</b>" + outputFileName + "<b>'");
    }
}
// No tables are associated with the script
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>No tables are associated with script '</b>" + ccdd.getScriptName() + "<b>'");
}
