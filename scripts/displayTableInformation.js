/***************************************************************************************************
 * Description: Display a table's information: name, type, description, data, and associated data
 * fields
 * 
 * This JavaScript script displays a table's information: name, type, description, data, and
 * associated data fields
 * 
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 **************************************************************************************************/

try
{
    load("nashorn:mozilla_compat.js");
}
catch (e)
{
}

importClass(Packages.CCDD.CcddScriptDataAccessHandler);

// Get the array of associated table names
var tableNames = ccdd.getTableNames();

// Step through each table name found
for (var index = 0; index < tableNames.length; index++)
{
    // Get the table name, type, and description. The table type returned is the actual type name,
    // rather than the generic 'Structure' or 'Command' that denotes the combined data from all
    // structures or commands. The actual type name must be used in the call below to retrieve the
    // type's column names. It may be used in the other calls below requiring the type name, but
    // these can also use the generic names
    var tableName = tableNames[index];
    var tableType = ccdd.getTypeNameByTable(tableName);
    var tableDescription = ccdd.getTableDescription(tableName);

    // Get the column names for this table's type. The table's actual type name must be used, rather
    // than the generic 'Structure' or 'Command' that denotes the combined data from all structures
    // or commands
    var columnNames = ccdd.getTableColumnNamesByType(tableType);

    // Create an array to store the table data. Add the table column names as the first row in the
    // array
    var tableData = [];
    tableData.push(columnNames);

    // Get the total number of table rows for this type
    var numTableRows = ccdd.getTableNumRows(tableType);

    // Step through each row of data for this table type
    for (var row = 0; row < numTableRows; row++)
    {
        // Check if the table name in the row matches the current table's name
        if (tableName.equals(ccdd.getPathByRow(tableType, row)))
        {
            var tableRowData = [];

            // Step through each column in the row
            for (var column = 0; column < columnNames.length; column++)
            {
                // Get the value of the table cell for this row and column
                var tableCellValue = ccdd.getTableData(tableType, columnNames[column], row);

                // TODO A table cell may contain line-feeds! These should be wrapped

                // Add the column's data to the row array
                tableRowData.push(tableCellValue);
            }

            // Add the row of table column values to the data array
            tableData.push(tableRowData);
        }
    }

    // Get the maximum column width for each column. These values are used to pad the column values
    // so that the data is aligned vertically
    var columnWidths = [];
    columnWidths = ccdd.getLongestStrings(tableData, columnWidths);

    // Output the table name, type, and description
    print(Array(80).join("=") + "\nTable       : " + tableName + "\n Type       : " + tableType + "\n Description: " + tableDescription + "\n\n");

    // Step through each row of table data
    for (var row = 0; row < tableData.length; row++)
    {
        // Check if this is the row following the one containing the column names. The following
        // underlines the column using dashes
        if (row == 1)
        {
            // Step through each column in the row
            for (var sepColumn = 0; sepColumn < columnNames.length; sepColumn++)
            {
                // Check if this is the first column
                if (sepColumn == 0)
                {
                    // Indent the row of data
                    print("  ");
                }
                // This is not the first column
                else
                {
                    // Insert a character to separate the columns
                    print(" + ");
                }

                // Add dashes to underline the column name
                print(Array(+columnWidths[sepColumn] + 2).join("-"));
            }

            print("\n");
        }

        // Step through each column in the row
        for (var column = 0; column < tableData[row].length; column++)
        {
            // Check if this is the first column
            if (column == 0)
            {
                // Indent the row of data
                print("  ");
            }
            // This is not the first column
            else
            {
                // Insert a character to separate the columns
                print(" | ");
            }

            // Display the table value for this row and column. Add spaces as padding so that the
            // values are aligned vertically
            print(tableData[row][column] + Array(columnWidths[column] - String(tableData[row][column]).length + 2).join(" "));
        }

        print("\n");
    }

    // Display the data field header
    print("\n Data fields:\n");

    // Get the array of data field names for the fields belonging to this table
    var dataFieldNames = ccdd.getTableDataFieldNames(tableName);

    // Check if the table has any data fields
    if (dataFieldNames.length != 0)
    {
        // Create an array to hold the field names and values. Add column names as the first row
        var fieldData = [];
        fieldData.push(["Field Name", "Field Value"]);

        // Step through each of the table's data fields
        for (var field = 0; field < dataFieldNames.length; field++)
        {
            // Get the data field's name and value
            var fieldName = dataFieldNames[field];
            var fieldValue = ccdd.getTableDataFieldValue(tableName, fieldName);

            // Separate the field value by line feeds, in case the field allows multiple, line-feed
            // separated lines of text
            var valueParts = fieldValue.split("\n");

            // Step through each portion of the field value. There is only a single portion for
            // fields that have no line feeds
            for (var part = 0; part < valueParts.length; part++)
            {
                // Check if this is the first portion of the field value
                if (part == 0)
                {
                    // Add the field name and first portion of the value to the field data array
                    fieldData.push([fieldName, valueParts[part]]);
                }
                // This isn't the first portion of the field value
                else
                {
                    // Add the portion of the field value to the field data array without the field
                    // name
                    fieldData.push(["", valueParts[part]]);
                }
            }
        }

        print("\n");

        // Get the maximum column width for each column. These values are used to pad the column
        // values so that the data is aligned vertically
        columnWidths = [];
        columnWidths = ccdd.getLongestStrings(fieldData, columnWidths);

        // Step through each data field's data
        for (var row = 0; row < fieldData.length; row++)
        {
            // Check if this is the row following the one containing the column names. The following
            // underlines the column using dashes
            if (row == 1)
            {
                // Step through each column in the row
                for (var sepColumn = 0; sepColumn < 2; sepColumn++)
                {
                    // Check if this is the first column
                    if (sepColumn == 0)
                    {
                        // Indent the row of data
                        print("  ");
                    }
                    // This is not the first column
                    else
                    {
                        // Insert a character to separate the columns
                        print(" + ");
                    }

                    // Add dashes to underline the column name
                    print(Array(+columnWidths[sepColumn] + 2).join("-"));
                }

                print("\n");
            }

            // Step through each column in the field data
            for (var column = 0; column < 2; column++)
            {
                // Check if this is the first column
                if (column == 0)
                {
                    // Indent the row of data
                    print("  ");
                }
                // This is not the first column
                else
                {
                    // Check if the field name is present
                    if (fieldData[row][0])
                    {
                        // Insert a character to separate the columns
                        print(" | ");
                    }
                    // There is no column name; this is the continuation of a field value that
                    // contains line feeds
                    else
                    {
                        // Insert a space to separate the columns
                        print("   ");
                    }
                }

                // Display the field value for this row and column. Add spaces as padding so that
                // the values are aligned vertically
                print(fieldData[row][column] + Array(columnWidths[column] - String(fieldData[row][column]).length + 2).join(" "));
            }

            print("\n");
        }
    }
    // the table has no data fields
    else
    {
        print("  None\n");
    }

    print("\n\n");
}
