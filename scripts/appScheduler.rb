#******************************************************************************
# Description: Output the CFS scheduler application's (SCH) message definition
# and schedule definition tables
#
# This Ruby script generates the scheduler application's (SCH) message
# definition and schedule definition tables.
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
# *****************************************************************************

java_import Java::CCDD.CcddScriptDataAccessHandler

#* Functions ******************************************************************

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
        $ccdd.writeToFileLn(file, "   Table(s): " + (",\n             ").join(sorted($ccdd.getTableNames())))
    end

    # Check if any groups is associated with the script
    if $ccdd.getAssociatedGroupNames().length != 0
        $ccdd.writeToFileLn(file, "   Group(s): " + (",\n             ").join(sorted($ccdd.getAssociatedGroupNames())))
    end

    $ccdd.writeToFileLn(file, "*/")
end

#* End functions **************************************************************

#* Main ***********************************************************************

# Scheduler message definition and schedule definition table entry array
# indices
$ENABLE_STATUS = 0
$TYPE = 1
$FREQUENCY = 2
$REMAINDER = 3
$MESSAGE_INDEX = 4
$GROUP_DATA = 5

# Message definition table command entries
$COMMAND1 = "0xC000"
$COMMAND2 = "0x0001"
$COMMAND3 = "0x0000"

# Create the message definition table *****************************************

# Define the initial minimum column widths
columnWidth = [10, 6, 6, 6]

# Create the message definition table output file name
mdtFileName = $ccdd.getOutputPath() + "sch_def_msgtbl.c"

# Open the message definition table output file
mdtFile = $ccdd.openOutputFile(mdtFileName)

# Check if the output file successfully opened
if mdtFile != nil
    # Add a header to the output file
    outputFileCreationInfo(mdtFile)

    # Get the message definition table entries
    mdtEntries = $ccdd.getApplicationMessageDefinitionTable()

      # Check if there are any entries in the message definition table
    if mdtEntries.length > 0
        # Adjust the minimum column widths
        columnWidth[$ENABLE_STATUS] = $ccdd.getLongestString(mdtEntries, columnWidth[$ENABLE_STATUS])
    end

    # Build the format string for an occupied entry
    formatUsed = "    { {%-" + columnWidth[$ENABLE_STATUS].to_s + "s, %" + columnWidth[$TYPE].to_s + "s, %" + columnWidth[$FREQUENCY].to_s + "s, %" + columnWidth[$REMAINDER].to_s + "s} } \n"

    # Write the include statements for the standard cFE and HK headers
    $ccdd.writeToFileLn(mdtFile, "")
    $ccdd.writeToFileLn(mdtFile, "/*")
    $ccdd.writeToFileLn(mdtFile, "** Include Files")
    $ccdd.writeToFileLn(mdtFile, "*/")
    $ccdd.writeToFileLn(mdtFile, "#include \"cfe.h\"")
    $ccdd.writeToFileLn(mdtFile, "#include \"cfe_tbl_filedef.h\"")
    $ccdd.writeToFileLn(mdtFile, "#include \"sch_platform_cfg.h\"")
    $ccdd.writeToFileLn(mdtFile, "#include \"sch_msgdefs.h\"")
    $ccdd.writeToFileLn(mdtFile, "#include \"sch_tbldefs.h\"")
    $ccdd.writeToFileLn(mdtFile, "")

    # Get the array containing the application names
    applicationNames = $ccdd.getApplicationNames()

    # Step through each application name
    for name in 0..applicationNames.length - 1
        # Write the application message ID include statements for the header
        # files
        $ccdd.writeToFileLn(mdtFile, "#include \"" + applicationNames[name].downcase + "_msgids.h\"")
    end

    $ccdd.writeToFileLn(mdtFile, "")
    $ccdd.writeToFileLn(mdtFile, "/*")
    $ccdd.writeToFileLn(mdtFile, "** Default message table data")
    $ccdd.writeToFileLn(mdtFile, "*/")
    $ccdd.writeToFileLn(mdtFile, "SCH_MessageEntry_t SCH_DefaultMessageTable[SCH_MAX_MESSAGES] =")
    $ccdd.writeToFileLn(mdtFile, "{")
    $ccdd.writeToFileLn(mdtFile, "    /*---------------------------------------------------------*/")
    $ccdd.writeToFileLn(mdtFile, "    /* DO NOT USE -- Entry #0 reserved for \"unused\" command ID */")
    $ccdd.writeToFile(mdtFile, "    /*---------------------------------------------------------*/")

    # Step through each message definition table entry
    for row in 0..mdtEntries.length - 1
        $ccdd.writeToFileLn(mdtFile, "\n    /* command ID #%d */" % row)
        comma = ","

        # Check if this is the last entry
        if row == mdtEntries.length - 1
            comma = " "
        end

        # Check if this slot is occupied
        if mdtEntries[row] != "SCH_UNUSED_MID"
            $ccdd.writeToFileFormat(mdtFile,
                                   formatUsed,
                                   mdtEntries[row],
                                   $COMMAND1,
                                   $COMMAND2,
                                   $COMMAND3,
                                   comma)
        # The slot is not used
        else
            $ccdd.writeToFileFormat(mdtFile, "    { { %s } } \n", mdtEntries[row])
        end
    end

    # Terminate the message definition table statement
    $ccdd.writeToFileLn(mdtFile, "};")

    # Close the output file
    $ccdd.closeFile(mdtFile)
# The output file cannot be opened
else
    # Display an error dialog
    $ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + mdtFileName + "<b>'")
end

# Create the schedule definition table output file name
sdtFileName = $ccdd.getOutputPath() + "sch_def_schtbl.c"

# Create the schedule definition table ****************************************

# Open the schedule definition table output file
sdtFile = $ccdd.openOutputFile(sdtFileName)

# Check if the output file successfully opened
if sdtFile != nil
    # Add a header to the output files
    outputFileCreationInfo(sdtFile)

    # Write the include statements for the standard cFE and HK headers
    $ccdd.writeToFileLn(sdtFile, "")
    $ccdd.writeToFileLn(sdtFile, "/*")
    $ccdd.writeToFileLn(sdtFile, "** Include Files")
    $ccdd.writeToFileLn(sdtFile, "*/")
    $ccdd.writeToFileLn(sdtFile, "#include \"cfe.h\"")
    $ccdd.writeToFileLn(sdtFile, "#include \"cfe_tbl_filedef.h\"")
    $ccdd.writeToFileLn(sdtFile, "#include \"sch_platform_cfg.h\"")
    $ccdd.writeToFileLn(sdtFile, "#include \"sch_msgdefs.h\"")
    $ccdd.writeToFileLn(sdtFile, "#include \"sch_tbldefs.h\"")
    $ccdd.writeToFileLn(sdtFile, "")

    # Get the list of defined parameters
    defines = $ccdd.getApplicationScheduleDefinitionTableDefines()

    # Build the format for the defined parameters
    formatDefines = "#define %-" + columnWidth[$ENABLE_STATUS].to_s + "s  %s \n"

    # Step through each defined parameter
    for define in 0..defines.length - 1
        # Output the define statement to the file
        $ccdd.writeToFileFormat(sdtFile, formatDefines, defines[define][0], defines[define][1])
    end

    # Output the table file header
    $ccdd.writeToFileLn(sdtFile, "")
    $ccdd.writeToFileLn(sdtFile, "/*")
    $ccdd.writeToFileLn(sdtFile, "** Table file header")
    $ccdd.writeToFileLn(sdtFile, "*/")
    $ccdd.writeToFileLn(sdtFile, "static CFE_TBL_FileDef_t CFE_TBL_FileDef =")
    $ccdd.writeToFileLn(sdtFile, "{")
    $ccdd.writeToFileLn(sdtFile, "    \"SCH_DefaultScheduleTable\",")
    $ccdd.writeToFileLn(sdtFile, "    \"SCH.SCHED_DEF\",")
    $ccdd.writeToFileLn(sdtFile, "    \"SCH schedule table\",")
    $ccdd.writeToFileLn(sdtFile, "    \"sch_def_schtbl.tbl\",")
    $ccdd.writeToFileLn(sdtFile, "    (sizeof (SCH_ScheduleEntry_t) * SCH_TABLE_ENTRIES)")
    $ccdd.writeToFileLn(sdtFile, "};")
    $ccdd.writeToFileLn(sdtFile, "")

    # Output the schedule definition table header
    $ccdd.writeToFileLn(sdtFile, "/*")
    $ccdd.writeToFileLn(sdtFile, "** Default schedule table data")
    $ccdd.writeToFileLn(sdtFile, "*/")
    $ccdd.writeToFileLn(sdtFile, "SCH_ScheduleEntry_t SCH_DefaultScheduleTable[SCH_TABLE_ENTRIES] =")
    $ccdd.writeToFileLn(sdtFile, "{")
    $ccdd.writeToFileLn(sdtFile, "    /*")
    $ccdd.writeToFileLn(sdtFile, "    **    uint8     EnableState  -- SCH_UNUSED, SCH_ENABLED")
    $ccdd.writeToFileLn(sdtFile, "    **    uint8     Type         -- 0 or SCH_ACTIVITY_SEND_MSG")
    $ccdd.writeToFileLn(sdtFile, "    **    uint16    Frequency    -- how many seconds between Activity execution")
    $ccdd.writeToFileLn(sdtFile, "    **    uint16    Remainder    -- seconds offset to perform Activity")
    $ccdd.writeToFileLn(sdtFile, "    **    uint16    MessageIndex -- Message index into Message Definition table")
    $ccdd.writeToFileLn(sdtFile, "    **    uint32    GroupData    -- Group and Multi-Group membership definitions")
    $ccdd.writeToFileLn(sdtFile, "    */")

    # Step through each schedule definition table time slot
    for timeSlot in 0..$ccdd.getNumberOfTimeSlots() - 1
        $ccdd.writeToFileLn(sdtFile, "")
        $ccdd.writeToFileFormat(sdtFile, "    /* Slot #%s */\n", (timeSlot + 1).to_s)
    
        # Get the schedule definition table entries
        sdtEntries = $ccdd.getApplicationScheduleDefinitionTable(timeSlot)
    
        # Define the initial minimum column widths
        columnWidth = [1, 1, 1, 1, 1, 1]
    
        # Check if there are any entries in the schedule definition table
        if sdtEntries.length > 0
            # Adjust the minimum column widths
            columnWidth = $ccdd.getLongestStrings(sdtEntries, columnWidth)
        end
        
        # Build the format string
        formatBody = "    {%-" + columnWidth[$ENABLE_STATUS].to_s + "s, %" + columnWidth[$TYPE].to_s + "s, %" + columnWidth[$FREQUENCY].to_s + "s, %" + columnWidth[$REMAINDER].to_s + "s, %" + columnWidth[$MESSAGE_INDEX].to_s + "s, %-" + columnWidth[$GROUP_DATA].to_s + "s}%s\n"
    
        # Step through each schedule definition table entry
        for row in 0..sdtEntries.length - 1
            comma = ","
    
            # Check if this is the last row
            if timeSlot == $ccdd.getNumberOfTimeSlots() - 1 && row == sdtEntries.length - 1
                # Don't append a comma
                comma = " "
            end
            
            # Output the entry to the schedule definition table file
            $ccdd.writeToFileFormat(sdtFile, formatBody, sdtEntries[row][$ENABLE_STATUS], sdtEntries[row][$TYPE], sdtEntries[row][$FREQUENCY], sdtEntries[row][$REMAINDER], sdtEntries[row][$MESSAGE_INDEX], sdtEntries[row][$GROUP_DATA], comma)
        end
    end

    # Terminate the schedule definition table
    $ccdd.writeToFileLn(sdtFile, "};")

    # Close the output file
    $ccdd.closeFile(sdtFile)
# The output file cannot be opened
else
    # Display an error dialog
    $ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + sdtFileName + "<b>'")
end
