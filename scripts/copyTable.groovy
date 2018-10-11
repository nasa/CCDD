/*******************************************************************************
 * Description: Output the CFS housekeeping (HK) application copy table
 * definition
 *
 * This Groovy script generates the HK copy table file from the supplied table
 * and packet information
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is claimed
 * in the United States under Title 17, U.S. Code. All Other Rights Reserved.
 ******************************************************************************/

import CCDD.CcddScriptDataAccessHandler

usedHKNames = []
usedHKValues = []

// Length of the CCSDS header in bytes
CCSDS_HEADER_LENGTH = 12

// Maximum number of lines for a copy table. Should match #define
// HK_COPY_TABLE_ENTRIES value (in hk_platform_cfg.h)
HK_COPY_TABLE_ENTRIES = 1800

// Copy table entry array indices
INPUT_MSG_ID = 0
INPUT_OFFSET = 1
OUTPUT_MSG_ID = 2
OUTPUT_OFFSET = 3
VARIABLE_BYTES = 4
VARIABLE_PARENT = 5
VARIABLE_NAME = 6

/*******************************************************************************
 * Output the file creation details to the specified file
 *
 * @param file
 *            reference to the output file
 ******************************************************************************/
def outputFileCreationInfo(file)
{
    // Add the build information and header to the output file
    ccdd.writeToFileLn(file, "/* Created : " + ccdd.getDateAndTime() + "\n   User    : " + ccdd.getUser() + "\n   Project : " + ccdd.getProject() + "\n   Script  : " + ccdd.getScriptName())

    // Check if any table is associated with the script
    if (ccdd.getTableNumRows() != 0)
    {
        ccdd.writeToFileLn(file, "   Table(s): " + ccdd.getTableNames().sort().join(",\n             "))
    }

    // Check if any group is associated with the script
    if (ccdd.getAssociatedGroupNames().length != 0)
    {
        ccdd.writeToFileLn(file, "   Group(s): " + ccdd.getAssociatedGroupNames().sort().join(",\n             "))
    }

    ccdd.writeToFileLn(file, "*/\n")
}

/*******************************************************************************
 * Output the copy table file
 ******************************************************************************/
def makeCopyTableFile()
{
    // Create the copy table output file name
    def copyTableFileName = ccdd.getOutputPath() + "hk_cpy_tbl.c"

    // Open the copy table output file
    def copyTableFile = ccdd.openOutputFile(copyTableFileName)

    // Check if the copy table file successfully opened
    if (copyTableFile != null)
    {
        def totalEntries = 0
        def entryIndex = 1
        def allTableEntries = []

        // Add the build information to the output file
        outputFileCreationInfo(copyTableFile)

        // Get an array containing the data stream names
        def copyTables = ccdd.getDataStreamNames()

        // Default column widths. The widths of the individual copy table
        // entries can increase these values
        def Integer[] columnWidth = [10, 6, 10, 6, 5, 0, 0]

        // Process the copy table for each data stream separately
        for (def copyTable = 0; copyTable < copyTables.length; copyTable++)
        {
            // Get the telemetry message IDs for this data stream
            def tlmMsgIDs = ccdd.getTelemetryMessageIDs(copyTables[copyTable])

            // Step through each of the telemetry message IDs
            for (def msgIndex = 0; msgIndex < tlmMsgIDs.length; msgIndex++)
            {
                def isFound = false

                // Step through the list of names already used
                for (def index = 0; index < usedHKNames.size(); index++)
                {
                    // Check if the message ID name is in the list
                    if (tlmMsgIDs[index][0] == usedHKNames[index])
                    {
                        // Set the flag to indicate the name is already in the
                        // list and stop searching
                        isFound = true
                        break
                    }
                }

                // Check if the message ID name isn't in the list
                if (!isFound)
                {
                    // Add the telemetry message ID name and ID to the lists
                    usedHKNames.push(tlmMsgIDs[msgIndex][0])
                    usedHKValues.push(tlmMsgIDs[msgIndex][1])
                }
            }

            // Get the copy table entries for this data stream
            def copyTableEntries = ccdd.getCopyTableEntries(copyTables[copyTable], CCSDS_HEADER_LENGTH, "Message ID Name", true)

            // Store the copy table entries so they won't have have to be
            // retrieved from CCDD again below
            allTableEntries.push(copyTableEntries)

            // Check if there are any entries in the copy table
            if (copyTableEntries.length > 0)
            {
                // Adjust the minimum column widths
                columnWidth = ccdd.getLongestStrings(copyTableEntries, columnWidth)
                
                // Update the total number of copy table entries
                totalEntries += copyTableEntries.length
            }
        }

        // Check if there are unused copy table entries 
        if (totalEntries < HK_COPY_TABLE_ENTRIES)
        {
            // Update the maximum width of the input message ID column
            if (columnWidth[INPUT_MSG_ID] < "HK_UNDEFINED_ENTRY".length())
            {
                columnWidth[INPUT_MSG_ID] = "HK_UNDEFINED_ENTRY".length()
            }
            
            // Update the maximum width of the output message ID column
            if (columnWidth[OUTPUT_MSG_ID] < "HK_UNDEFINED_ENTRY".length())
            {
                columnWidth[OUTPUT_MSG_ID] = "HK_UNDEFINED_ENTRY".length()
            }
        }
        
        // Write the standard include files to the copy table file
        ccdd.writeToFileLn(copyTableFile, "#include \"cfe.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_utils.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_app.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_msgids.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_tbldefs.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"cfe_tbl_filedef.h\"")
        ccdd.writeToFileLn(copyTableFile, "")
        
        // Get the number of rows for the Includes table data
        def numIncludeRows = ccdd.getTableNumRows("Includes")

        // Check if there are any data to include
        if (numIncludeRows > 0)
        {
            // Step through each row of Includes data
            for (def row = 0; row < numIncludeRows; row++)
            {
                // Output the Includes table's 'includes' column data
                ccdd.writeToFileLn(copyTableFile, ccdd.getTableData("Includes", "includes", row))
            }

            ccdd.writeToFileLn(copyTableFile, "")
        }

        // Build the format strings so that the columns in each row are aligned
        def formatHeader = "/* %-" + columnWidth[INPUT_MSG_ID] + "s| %-" + columnWidth[INPUT_OFFSET] + "s| %-" + columnWidth[OUTPUT_MSG_ID] + "s| %-" + columnWidth[OUTPUT_OFFSET] + "s| %-" + columnWidth[VARIABLE_BYTES] + "s */\n"
        def formatBody = "  {%-" + columnWidth[INPUT_MSG_ID] + "s, %" + columnWidth[INPUT_OFFSET] + "s, %-" + columnWidth[OUTPUT_MSG_ID] + "s, %" + columnWidth[OUTPUT_OFFSET] + "s, %" + columnWidth[VARIABLE_BYTES] + "s}%s  /* (%" + HK_COPY_TABLE_ENTRIES.toString().length() + "s) %s : %s */\n"
        
        // Write the copy table definition statement
        ccdd.writeToFileLn(copyTableFile, "hk_copy_table_entry_t HK_CopyTable[HK_COPY_TABLE_ENTRIES] =")
        ccdd.writeToFileLn(copyTableFile, "{")
        ccdd.writeToFileFormat(copyTableFile, formatHeader, "Input", "Input", "Output", "Output", "Num")
        ccdd.writeToFileFormat(copyTableFile, formatHeader, "Message ID", "Offset", "Message ID", "Offset", "Bytes")

        // Step through each entry in the copy table
        for (def copyTable = 0; copyTable < copyTables.length; copyTable++)
        {
            // Get the copy table entries for this data stream
            def copyTableEntries = allTableEntries[copyTable]

            // Check if any copy table entries exist; i.e., if any packets are
            // defined
            if (copyTableEntries.length != 0)
            {
                // Step through each copy table entry
                for (def row = 0; row < copyTableEntries.length; row++)
                {
                    // Set the value so that it will append a comma to all but
                    // the last row
                    def comma = (entryIndex == HK_COPY_TABLE_ENTRIES) ? " " : ","

                    // Write the entry to the copy table file
                    ccdd.writeToFileFormat(copyTableFile, formatBody, copyTableEntries[row][INPUT_MSG_ID], copyTableEntries[row][INPUT_OFFSET], copyTableEntries[row][OUTPUT_MSG_ID], copyTableEntries[row][OUTPUT_OFFSET], copyTableEntries[row][VARIABLE_BYTES], comma, entryIndex.toString(), copyTableEntries[row][VARIABLE_PARENT], copyTableEntries[row][VARIABLE_NAME])

                    // Check if no available rows remain in the copy table
                    if (entryIndex == HK_COPY_TABLE_ENTRIES)
                    {
                        // Exit the loop since no more entries can be added to
                        // the copy table
                        break
                    }
                                    
                    // Increment the copy table entry index
                    entryIndex++
                }
            }
        }

        // Check if there are any unfilled rows in the copy table
        if (entryIndex < HK_COPY_TABLE_ENTRIES)
        {
            // Build the format string for the empty entries so that the
            // columns in each row are aligned
            def emptyFormatBody = "  {%-" + columnWidth[INPUT_MSG_ID] + "s, %" + columnWidth[INPUT_OFFSET] + "s, %-" + columnWidth[OUTPUT_MSG_ID] + "s, %" + columnWidth[OUTPUT_OFFSET] + "s, %" + columnWidth[VARIABLE_BYTES] + "s}%s  /* (%" + HK_COPY_TABLE_ENTRIES.toString().length() + "s) */\n"

            // Step through the remaining, empty rows in the copy table
            for (def index = entryIndex; index <= HK_COPY_TABLE_ENTRIES; index++)
            {
                // Set the value so that it will append a comma to all but
                // the last row
                def comma = (entryIndex == HK_COPY_TABLE_ENTRIES) ? " " : ","

                // Add the blank entry to the copy table
                ccdd.writeToFileFormat(copyTableFile, emptyFormatBody, "HK_UNDEFINED_ENTRY", "0", "HK_UNDEFINED_ENTRY", "0", "0", comma, entryIndex.toString())

                // Increment the copy table entry index
                entryIndex++
            }
        }

        // Terminate the table definition statement
        ccdd.writeToFileLn(copyTableFile, "};")
        ccdd.writeToFileLn(copyTableFile, "")
        ccdd.writeToFileLn(copyTableFile, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")
        ccdd.closeFile(copyTableFile)
    }
    // The copy table file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening copy table output file '</b>" + copyTableFileName + "<b>'")
    }
}

/*******************************************************************************
 * Output the ID definition file
 ******************************************************************************/
def makeIDDefinitionFile()
{
    // Build the ID definitions header output file name and include flag
    def baseFileName = "combined_pkt_ids"
    def idDefinesFileName = ccdd.getOutputPath() + baseFileName + ".h"
    def headerIncludeFlag = "_" + baseFileName.toUpperCase() + "_H_"

    // Open the types header output file
    def idDefinesFile = ccdd.openOutputFile(idDefinesFileName)

    // Check if the types header file successfully opened
    if (idDefinesFile != null)
    {
        // Add the build information to the output file
        outputFileCreationInfo(idDefinesFile)

        // Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(idDefinesFile, "#ifndef " + headerIncludeFlag)
        ccdd.writeToFileLn(idDefinesFile, "#define " + headerIncludeFlag)
        ccdd.writeToFileLn(idDefinesFile, "")

        // Get the number of rows for the Includes table data
        def numIncludeRows = ccdd.getTableNumRows("Includes")

        // Check if there are any data to include
        if (numIncludeRows > 0)
        {
            // Step through each row of Includes data
            for (def row = 0; row < numIncludeRows; row++)
            {
                // Output the Includes table's 'includes' column data
                ccdd.writeToFileLn(idDefinesFile, ccdd.getTableData("Includes", "includes", row))
            }

            ccdd.writeToFileLn(idDefinesFile, "")
        }

        def minimumLength = 1

        // Step through the list of names that are used
        for (def index = 0; index < usedHKNames.size(); index++)
        {
            // Check if the length exceeds the minimum length found thus far
            if (usedHKNames[index].length() > minimumLength)
            {
                // Store the new minimum length
                minimumLength = usedHKNames[index].length()
            }
        }

        // Step through the list of names that are used
        for (def index = 0; index < usedHKNames.size(); index++)
        {
            // Output the ID name and ID to the file
            ccdd.writeToFileFormat(idDefinesFile, "#define %-" + minimumLength + "s  (%7s + FC_OFFSET )\n", usedHKNames[index], usedHKValues[index])
        }

        // Finish and close the ID definitions header output file
        ccdd.writeToFileLn(idDefinesFile, "")
        ccdd.writeToFileLn(idDefinesFile, "#endif  /* " + headerIncludeFlag + " */")

        // Close the output file
        ccdd.closeFile(idDefinesFile)
    }
    // The combined ID file failed to open
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening combined ID output file '</b>" + idDefinesFileName + "<b>'")
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Output the copy table file
makeCopyTableFile()

// Output the ID definitions file
makeIDDefinitionFile()
