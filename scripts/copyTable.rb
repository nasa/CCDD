#******************************************************************************
# Description: Output the CFS housekeeping (HK) application copy table
# definition
#
# This Ruby script generates the HK copy table file from the supplied table
# and packet information
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

java_import Java::CCDD.CcddScriptDataAccessHandler

$usedHKNames = []
$usedHKValues = []

# Length of the CCSDS header in bytes
$CCSDS_HEADER_LENGTH = 12

# Maximum number of lines for a copy table. Should match #define
# $HK_COPY_TABLE_ENTRIES value (in hk_platform_cfg.h)
$HK_COPY_TABLE_ENTRIES = 1800

# Copy table entry array indices
$INPUT_MSG_ID = 0
$INPUT_OFFSET = 1
$OUTPUT_MSG_ID = 2
$OUTPUT_OFFSET = 3
$VARIABLE_BYTES = 4
$VARIABLE_PARENT = 5
$VARIABLE_NAME = 6

#** Functions *****************************************************************

#******************************************************************************
# Output the file creation details to the specified file
#
# @param file
#            reference to the output file
#******************************************************************************
def outputFileCreationInfo(file)
  # Add the build information and header to the output file
  $ccdd.writeToFileLn(file, "/* Created : " + $ccdd.getDateAndTime() + "\n   User    : " + $ccdd.getUser() + "\n   Project : " + $ccdd.getProject() + "\n   Script  : " + $ccdd.getScriptName())

  # Check if any table is associated with the script
  if $ccdd.getTableNumRows() != 0
      $ccdd.writeToFileLn(file, "   Table(s): " + $ccdd.getTableNames().sort.to_a.join(",\n             "))
  end

  # Check if any group is associated with the script
  if $ccdd.getAssociatedGroupNames().length != 0
      $ccdd.writeToFileLn(file, "   Group(s): " + $ccdd.getAssociatedGroupNames().sort.to_a.join(",\n             "))
  end

  $ccdd.writeToFileLn(file, "*/\n")
end

#******************************************************************************
# Output the copy table file
#******************************************************************************
def makeCopyTableFile()
    # Create the copy table output file name
    copyTableFileName = $ccdd.getOutputPath() + "hk_cpy_tbl.c"

    # Open the copy table output file
    copyTableFile = $ccdd.openOutputFile(copyTableFileName)

    # Check if the copy table file successfully opened
    if copyTableFile != nil
        totalEntries = 0
        entryIndex = 1
        allTableEntries = []

        # Add the build information to the output file
        outputFileCreationInfo(copyTableFile)

        # Get an array containing the data stream names
        copyTables = $ccdd.getDataStreamNames()

        # Default column widths. The widths of the individual copy table
        # entries can increase these values
        columnWidth = [10, 6, 10, 6, 5, 0, 0]

        # Process the copy table for each data stream separately
        for copyTable in 0..copyTables.length - 1
            # Get the telemetry message IDs for this data stream
            tlmMsgIDs = $ccdd.getTelemetryMessageIDs(copyTables[copyTable])

            # Step through each of the telemetry message IDs
            for msgIndex in 0..tlmMsgIDs.length - 1
                isFound = false

                # Step through the list of names already used
                for index in 0..$usedHKNames.length - 1
                    # Check if the message ID name is in the list
                    if tlmMsgIDs[index][0] == $usedHKNames[index]
                        # Set the flag to indicate the name is already in the
                        # list and stop searching
                        isFound = true
                        break
                    end
                end

                # Check if the message ID name isn't in the list
                if !isFound
                    # Add the telemetry message ID name and ID to the lists
                    $usedHKNames.push(tlmMsgIDs[msgIndex][0])
                    $usedHKValues.push(tlmMsgIDs[msgIndex][1])
                end
            end

            # Get the copy table entries for this data stream
            copyTableEntries = $ccdd.getCopyTableEntries(copyTables[copyTable], $CCSDS_HEADER_LENGTH, "Message ID Name", true)

            # Store the copy table entries so they won't have have to be
            # retrieved from CCDD again below
            allTableEntries.push(copyTableEntries)

            # Check if there are any entries in the copy table
            if copyTableEntries.length > 0
                # Adjust the minimum column widths
                columnWidth = $ccdd.getLongestStrings(copyTableEntries, columnWidth)
             
                # Update the total number of copy table entries
                totalEntries += copyTableEntries.length
            end
        end

        # Check if there are unused copy table entries 
        if totalEntries < $HK_COPY_TABLE_ENTRIES
            # Update the maximum width of the input message ID column
            if columnWidth[$INPUT_MSG_ID] < "HK_UNDEFINED_ENTRY".length
                columnWidth[$INPUT_MSG_ID] = "HK_UNDEFINED_ENTRY".length
            end

            # Update the maximum width of the output message ID column
            if columnWidth[$OUTPUT_MSG_ID] < "HK_UNDEFINED_ENTRY".length
                columnWidth[$OUTPUT_MSG_ID] = "HK_UNDEFINED_ENTRY".length
            end
        end

        # Write the standard include files to the copy table file
        $ccdd.writeToFileLn(copyTableFile, "#include \"cfe.h\"")
        $ccdd.writeToFileLn(copyTableFile, "#include \"hk_utils.h\"")
        $ccdd.writeToFileLn(copyTableFile, "#include \"hk_app.h\"")
        $ccdd.writeToFileLn(copyTableFile, "#include \"hk_msgids.h\"")
        $ccdd.writeToFileLn(copyTableFile, "#include \"hk_tbldefs.h\"")
        $ccdd.writeToFileLn(copyTableFile, "#include \"cfe_tbl_filedef.h\"")
        $ccdd.writeToFileLn(copyTableFile, "")
        
        # Get the number of rows for the Includes table data
        numIncludeRows = $ccdd.getTableNumRows("Includes")
        
        # Check if there are any data to include
        if numIncludeRows > 0
            # Step through each row of Includes data
            for row in 0..numIncludeRows - 1
                # Output the Includes table's 'includes' column data
                $ccdd.writeToFileLn(copyTableFile, $ccdd.getTableData("Includes", "includes", row))
            end
            
            $ccdd.writeToFileLn(copyTableFile, "")
        end
        
        # Build the format strings so that the columns in each row are aligned
        formatHeader = "/* %-" + columnWidth[$INPUT_MSG_ID].to_s + "s| %-" + columnWidth[$INPUT_OFFSET].to_s + "s| %-" + columnWidth[$OUTPUT_MSG_ID].to_s + "s| %-" + columnWidth[$OUTPUT_OFFSET].to_s + "s| %-" + columnWidth[$VARIABLE_BYTES].to_s + "s */\n"
        formatBody = "  {%-" + columnWidth[$INPUT_MSG_ID].to_s + "s, %" + columnWidth[$INPUT_OFFSET].to_s + "s, %-" + columnWidth[$OUTPUT_MSG_ID].to_s + "s, %" + columnWidth[$OUTPUT_OFFSET].to_s + "s, %" + columnWidth[$VARIABLE_BYTES].to_s + "s}%s  /* (%" + $HK_COPY_TABLE_ENTRIES.to_s.length.to_s + "s) %s : %s */\n"

        # Write the copy table definition statement
        $ccdd.writeToFileLn(copyTableFile, "hk_copy_table_entry_t HK_CopyTable[$HK_COPY_TABLE_ENTRIES] =")
        $ccdd.writeToFileLn(copyTableFile, "{")
        $ccdd.writeToFileFormat(copyTableFile, formatHeader, "Input", "Input", "Output", "Output", "Num")
        $ccdd.writeToFileFormat(copyTableFile, formatHeader, "Message ID", "Offset", "Message ID", "Offset", "Bytes")

        # Set the counter for the number of entries remaining in the copy table
        rowsRemaining = $HK_COPY_TABLE_ENTRIES - 1

        # Step through each entry in the copy table
        for copyTable in 0..copyTables.length - 1
            # Get the copy table entries for this data stream
            copyTableEntries = allTableEntries[copyTable]

            # Check if any copy table entries exist; i.e., if any packets are
            # defined
            if copyTableEntries.length != 0
                # Step through each copy table entry
                for row in 0..copyTableEntries.length - 1
                    # Set the value so that it will append a comma to all but
                    # the last row
                    if entryIndex == $HK_COPY_TABLE_ENTRIES
                        comma = " "
                    else
                        comma = ","
                    end

                    # Write the entry to the copy table file
                    $ccdd.writeToFileFormat(copyTableFile, formatBody, copyTableEntries[row][$INPUT_MSG_ID], copyTableEntries[row][$INPUT_OFFSET], copyTableEntries[row][$OUTPUT_MSG_ID], copyTableEntries[row][$OUTPUT_OFFSET], copyTableEntries[row][$VARIABLE_BYTES], comma, entryIndex.to_s, copyTableEntries[row][$VARIABLE_PARENT], copyTableEntries[row][$VARIABLE_NAME])

                    # Check if no available rows remain in the copy table
                    if entryIndex == $HK_COPY_TABLE_ENTRIES
                        # Exit the loop since no more entries can be added to
                        # the copy table
                        break
                    end

                    # Increment the copy table entry index
                    entryIndex += 1
                end
            end
        end

        # Check if there are any unfilled rows in the copy table
        if entryIndex < $HK_COPY_TABLE_ENTRIES
            # Build the format string for the empty entries so that the
            # columns in each row are aligned
            emptyFormatBody = "  {%-" + columnWidth[$INPUT_MSG_ID].to_s + "s, %" + columnWidth[$INPUT_OFFSET].to_s + "s, %-" + columnWidth[$OUTPUT_MSG_ID].to_s + "s, %" + columnWidth[$OUTPUT_OFFSET].to_s + "s, %" + columnWidth[$VARIABLE_BYTES].to_s + "s}%s  /* (%" + $HK_COPY_TABLE_ENTRIES.to_s.length.to_s + "s) */\n"
            
            # Step through the remaining, empty rows in the copy table
            for index in entryIndex..$HK_COPY_TABLE_ENTRIES
                # Set the value so that it will append a comma to all but
                # the last row
                if entryIndex == $HK_COPY_TABLE_ENTRIES
                    comma = " "
                else
                    comma = ","
                end
  
                # Add the blank entry to the copy table
                $ccdd.writeToFileFormat(copyTableFile, emptyFormatBody, "HK_UNDEFINED_ENTRY", "0", "HK_UNDEFINED_ENTRY", "0", "0", comma, entryIndex.to_s)

                # Increment the copy table entry index
                entryIndex += 1
            end
        end

        # Terminate the table definition statement
        $ccdd.writeToFileLn(copyTableFile, "};")
        $ccdd.writeToFileLn(copyTableFile, "")
        $ccdd.writeToFileLn(copyTableFile, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")
        $ccdd.closeFile(copyTableFile)
    # The copy table file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening copy table output file '</b>" + copyTableFileName + "<b>'")
    end
end

#******************************************************************************
# Output the ID definition file
#******************************************************************************
def makeIDDefinitionFile()
    # Build the ID definitions header output file name and include flag
    baseFileName = "combined_pkt_ids"
    idDefinesFileName = $ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.upcase() + "_H_"

    # Open the types header output file
    idDefinesFile = $ccdd.openOutputFile(idDefinesFileName)

    # Check if the types header file successfully opened
    if idDefinesFile != nil
        # Add the build information to the output file
        outputFileCreationInfo(idDefinesFile)

        # Add the header include to prevent loading the file more than once
        $ccdd.writeToFileLn(idDefinesFile, "#ifndef " + headerIncludeFlag)
        $ccdd.writeToFileLn(idDefinesFile, "#define " + headerIncludeFlag)
        $ccdd.writeToFileLn(idDefinesFile, "")

        # Get the number of rows for the Includes table data
        numIncludeRows = $ccdd.getTableNumRows("Includes")

        # Check if there are any data to include
        if numIncludeRows > 0
            # Step through each row of Includes data
            for row in 0..numIncludeRows-1
                # Output the Includes table's 'includes' column data
                $ccdd.writeToFileLn(idDefinesFile, $ccdd.getTableData("Includes", "includes", row))
            end

            $ccdd.writeToFileLn(idDefinesFile, "")
        end

        minimumLength = 1

        # Step through the list of names that are used
        for index in  0..$usedHKNames.length - 1
            # Check if the length exceeds the minimum length found thus far
            if $usedHKNames[index].length > minimumLength
                # Store the new minimum length
                minimumLength = $usedHKNames[index].length
            end
        end

        # Step through the list of names that are used
        for index in 0..$usedHKNames.length - 1
            # Output the ID name and ID to the file
            $ccdd.writeToFileFormat(idDefinesFile, "#define %-" + minimumLength.to_s + "s  (%7s + FC_OFFSET )\n", $usedHKNames[index], $usedHKValues[index])
        end

        # Finish and close the ID definitions header output file
        $ccdd.writeToFileLn(idDefinesFile, "")
        $ccdd.writeToFileLn(idDefinesFile, "#endif  /* " + headerIncludeFlag + " */")

        # Close the output file
        $ccdd.closeFile(idDefinesFile)
    # The combined ID file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening combined ID output file '</b>" + idDefinesFileName + "<b>'")
    end
end

#** End functions *************************************************************

#** Main **********************************************************************

# Output the copy table file
makeCopyTableFile()

# Output the ID definitions file
makeIDDefinitionFile()
