#******************************************************************************
# Description: Output the CFS housekeeping (HK) application copy table
# definition
#
# This Python script generates the HK copy table file from the supplied table
# and packet information
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

from CCDD import CcddScriptDataAccessHandler

usedHKNames = []
usedHKValues = []

# Length of the CCSDS header in bytes
CCSDS_HEADER_LENGTH = 12

# Maximum number of lines for a copy table. Should match #define
# HK_COPY_TABLE_ENTRIES value (in hk_platform_cfg.h)
HK_COPY_TABLE_ENTRIES = 1800

# Copy table entry array indices
INPUT_MSG_ID = 0
INPUT_OFFSET = 1
OUTPUT_MSG_ID = 2
OUTPUT_OFFSET = 3
VARIABLE_BYTES = 4
VARIABLE_PARENT = 5
VARIABLE_NAME = 6

#** Functions *****************************************************************

#******************************************************************************
# Output the file creation details to the specified file
#
# @param file
#            reference to the output file
#******************************************************************************
def outputFileCreationInfo(file):
    # Add the build information and header to the output file
    ccdd.writeToFileLn(file, "/* Created : " + ccdd.getDateAndTime() + "\n   User    : " + ccdd.getUser() + "\n   Project : " + ccdd.getProject() + "\n   Script  : " + ccdd.getScriptName())

    # Check if any table is associated with the script
    if ccdd.getTableNumRows() != 0:
        ccdd.writeToFileLn(file, "   Table(s): " + [].slice.call(ccdd.getTableNames()).sort().join(",\n             "))

    # Check if any group is associated with the script
    if len(ccdd.getAssociatedGroupNames()) != 0:
        ccdd.writeToFileLn(file, "   Group(s): " + [].slice.call(ccdd.getAssociatedGroupNames()).sort().join(",\n             "))

    ccdd.writeToFileLn(file, "*/\n")

#******************************************************************************
# Output the copy table file
#******************************************************************************
def makeCopyTableFile():
    # Create the copy table output file name
    copyTableFileName = ccdd.getOutputPath() + "hk_cpy_tbl.c"

    # Open the copy table output file
    copyTableFile = ccdd.openOutputFile(copyTableFileName)

    # Check if the copy table file successfully opened
    if copyTableFile is not None:
        totalEntries = 0
        entryIndex = 1
        allTableEntries = []

        # Add the build information to the output file
        outputFileCreationInfo(copyTableFile)

        # Get an array containing the data stream names
        copyTables = ccdd.getDataStreamNames()

        # Default column widths. The widths of the individual copy table
        # entries can increase these values
        columnWidth = [10, 6, 10, 6, 5, 0, 0]

        # Process the copy table for each data stream separately
        for copyTable in range(len(copyTables)):
            # Get the telemetry message IDs for this data stream
            tlmMsgIDs = ccdd.getTelemetryMessageIDs(copyTables[copyTable])

            # Step through each of the telemetry message IDs
            for msgIndex in range(len(tlmMsgIDs)):
                isFound = False

                # Step through the list of names already used
                for index in range(len(usedHKNames)):
                    # Check if the message ID name is in the list
                    if tlmMsgIDs[index][0] == usedHKNames[index]:
                        # Set the flag to indicate the name is already in the
                        # list and stop searching
                        isFound = True
                        break

                # Check if the message ID name isn't in the list
                if not isFound:
                    # Add the telemetry message ID name and ID to the lists
                    usedHKNames.append(tlmMsgIDs[msgIndex][0])
                    usedHKValues.append(tlmMsgIDs[msgIndex][1])

            # Get the copy table entries for this data stream
            copyTableEntries = ccdd.getCopyTableEntries(copyTables[copyTable], CCSDS_HEADER_LENGTH, "Message ID Name", True)

            # Store the copy table entries so they won't have have to be
            # retrieved from CCDD again below
            allTableEntries.append(copyTableEntries)

            # Check if there are any entries in the copy table
            if len(copyTableEntries) > 0:
                # Adjust the minimum column widths
                columnWidth = ccdd.getLongestStrings(copyTableEntries, columnWidth)
                
                # Update the total number of copy table entries
                totalEntries += len(copyTableEntries)

        # Check if there are unused copy table entries 
        if totalEntries < HK_COPY_TABLE_ENTRIES:
            # Update the maximum width of the input message ID column
            if columnWidth[INPUT_MSG_ID] < len("HK_UNDEFINED_ENTRY"):
                columnWidth[INPUT_MSG_ID] = len("HK_UNDEFINED_ENTRY")
            
            # Update the maximum width of the output message ID column
            if columnWidth[OUTPUT_MSG_ID] < len("HK_UNDEFINED_ENTRY"):
                columnWidth[OUTPUT_MSG_ID] = len("HK_UNDEFINED_ENTRY")
        
        # Write the standard include files to the copy table file
        ccdd.writeToFileLn(copyTableFile, "#include \"cfe.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_utils.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_app.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_msgids.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"hk_tbldefs.h\"")
        ccdd.writeToFileLn(copyTableFile, "#include \"cfe_tbl_filedef.h\"")
        ccdd.writeToFileLn(copyTableFile, "")
 
        # Get the number of rows for the Includes table data
        numIncludeRows = ccdd.getTableNumRows("Includes")

        # Check if there are any data to include
        if numIncludeRows > 0:
            # Step through each row of Includes data
            for row in range(numIncludeRows):
                # Output the Includes table's 'includes' column data
                ccdd.writeToFileLn(copyTableFile, ccdd.getTableData("Includes", "includes", row))

            ccdd.writeToFileLn(copyTableFile, "")
        
        # Build the format strings so that the columns in each row are aligned
        formatHeader = "/* %-" + str(columnWidth[INPUT_MSG_ID]) + "s| %-" + str(columnWidth[INPUT_OFFSET]) + "s| %-" + str(columnWidth[OUTPUT_MSG_ID]) + "s| %-" + str(columnWidth[OUTPUT_OFFSET]) + "s| %-" + str(columnWidth[VARIABLE_BYTES]) + "s */\n"
        formatBody = "  {%-" + str(columnWidth[INPUT_MSG_ID]) + "s, %" + str(columnWidth[INPUT_OFFSET]) + "s, %-" + str(columnWidth[OUTPUT_MSG_ID]) + "s, %" + str(columnWidth[OUTPUT_OFFSET]) + "s, %" + str(columnWidth[VARIABLE_BYTES]) + "s}%s  /* (%" + str(len(str(HK_COPY_TABLE_ENTRIES))) + "s) %s : %s */\n"

        # Write the copy table definition statement
        ccdd.writeToFileLn(copyTableFile, "hk_copy_table_entry_t HK_CopyTable[HK_COPY_TABLE_ENTRIES] =")
        ccdd.writeToFileLn(copyTableFile, "{")
        ccdd.writeToFileFormat(copyTableFile, formatHeader, "Input", "Input", "Output", "Output", "Num")
        ccdd.writeToFileFormat(copyTableFile, formatHeader, "Message ID", "Offset", "Message ID", "Offset", "Bytes")

        # Step through each entry in the copy table
        for copyTable in range(len(copyTables)):
            # Get the copy table entries for this data stream
            copyTableEntries = allTableEntries[copyTable]

            # Check if any copy table entries exist; i.e., if any packets are
            # defined
            if len(copyTableEntries) != 0:
                # Step through each copy table entry
                for row in range(len(copyTableEntries)):
                    # Set the value so that it will append a comma to all but
                    # the last row
                    if entryIndex == HK_COPY_TABLE_ENTRIES:
                        comma = " "
                    else:
                        comma = ","
 
                    # Write the entry to the copy table file
                    ccdd.writeToFileFormat(copyTableFile, formatBody, copyTableEntries[row][INPUT_MSG_ID], copyTableEntries[row][INPUT_OFFSET], copyTableEntries[row][OUTPUT_MSG_ID], copyTableEntries[row][OUTPUT_OFFSET], copyTableEntries[row][VARIABLE_BYTES], comma, str(entryIndex), copyTableEntries[row][VARIABLE_PARENT], copyTableEntries[row][VARIABLE_NAME])

                    # Check if no available rows remain in the copy table
                    if entryIndex == HK_COPY_TABLE_ENTRIES:
                        # Exit the loop since no more entries can be added to
                        # the copy table
                        break
                
                    # Increment the copy table entry index
                    entryIndex += 1
    
        # Check if there are any unfilled rows in the copy table
        if entryIndex < HK_COPY_TABLE_ENTRIES:
            # Build the format string for the empty entries so that the
            # columns in each row are aligned
            emptyFormatBody = "  {%-" + str(columnWidth[INPUT_MSG_ID]) + "s, %" + str(columnWidth[INPUT_OFFSET]) + "s, %-" + str(columnWidth[OUTPUT_MSG_ID]) + "s, %" + str(columnWidth[OUTPUT_OFFSET]) + "s, %" + str(columnWidth[VARIABLE_BYTES]) + "s}%s  /* (%" + str(len(str(HK_COPY_TABLE_ENTRIES))) + "s) */\n"

            # Step through the remaining, empty rows in the copy table
            for index in range(entryIndex, HK_COPY_TABLE_ENTRIES + 1):
                # Set the value so that it will append a comma to all but
                # the last row
                if entryIndex == HK_COPY_TABLE_ENTRIES:
                    comma = " "
                else:
                    comma = ","

                # Add the blank entry to the copy table
                ccdd.writeToFileFormat(copyTableFile, emptyFormatBody, "HK_UNDEFINED_ENTRY", "0", "HK_UNDEFINED_ENTRY", "0", "0", comma, str(entryIndex))

                # Increment the copy table entry index
                entryIndex += 1

        # Terminate the table definition statement
        ccdd.writeToFileLn(copyTableFile, "};")
        ccdd.writeToFileLn(copyTableFile, "")
        ccdd.writeToFileLn(copyTableFile, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")
        ccdd.closeFile(copyTableFile)
    # The copy table file failed to open
    else:
        # Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening copy table output file '</b>" + copyTableFileName + "<b>'")

#******************************************************************************
# Output the ID definition file
#******************************************************************************
def makeIDDefinitionFile():
    # Build the ID definitions header output file name and include flag
    baseFileName = "combined_pkt_ids"
    idDefinesFileName = ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.upper() + "_H_"

    # Open the types header output file
    idDefinesFile = ccdd.openOutputFile(idDefinesFileName)

    # Check if the types header file successfully opened
    if idDefinesFile is not None:
        # Add the build information to the output file
        outputFileCreationInfo(idDefinesFile)

        # Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(idDefinesFile, "#ifndef " + headerIncludeFlag)
        ccdd.writeToFileLn(idDefinesFile, "#define " + headerIncludeFlag)
        ccdd.writeToFileLn(idDefinesFile, "")

        # Get the number of rows for the Includes table data
        numIncludeRows = ccdd.getTableNumRows("Includes")

        # Check if there are any data to include
        if numIncludeRows > 0:
            # Step through each row of Includes data
            for row in range(numIncludeRows):
                # Output the Includes table's 'includes' column data
                ccdd.writeToFileLn(idDefinesFile, ccdd.getTableData("Includes", "includes", row))

            ccdd.writeToFileLn(idDefinesFile, "")

        minimumLength = 1

        # Step through the list of names that are used
        for index in range(len(usedHKNames)):
            # Check if the length exceeds the minimum length found thus far
            if len(usedHKNames[index]) > minimumLength:
                # Store the new minimum length
                minimumLength = len(usedHKNames[index])

        # Step through the list of names that are used
        for index in range(len(usedHKNames)):
            # Output the ID name and ID to the file
            ccdd.writeToFileFormat(idDefinesFile, "#define %-" + str(minimumLength) + "s  (%7s + FC_OFFSET )\n", usedHKNames[index], usedHKValues[index])

        # Finish and close the ID definitions header output file
        ccdd.writeToFileLn(idDefinesFile, "")
        ccdd.writeToFileLn(idDefinesFile, "#endif  /* " + headerIncludeFlag + " */")

        # Close the output file
        ccdd.closeFile(idDefinesFile)
    # The combined ID file failed to open
    else:
        # Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening combined ID output file '</b>" + idDefinesFileName + "<b>'")

#** End functions *************************************************************

#** Main **********************************************************************

# Output the copy table file
makeCopyTableFile()

# Output the ID definitions file
makeIDDefinitionFile()
