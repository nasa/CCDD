/*******************************************************************************
 * Description: Output the CFS scheduler application's (SCH) message definition
 * and schedule definition tables
 *
 * This Groovy script generates the scheduler application's (SCH) message
 * definition and schedule definition tables.
 *
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is claimed
 * in the United States under Title 17, U.S. Code. All Other Rights Reserved.
 ******************************************************************************/

import CCDD.CcddScriptDataAccessHandler

/** Functions *************************************************************** */

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

    // Check if any groups is associated with the script
    if (ccdd.getAssociatedGroupNames().length != 0)
    {
        ccdd.writeToFileLn(file, "   Group(s): " + ccdd.getAssociatedGroupNames().sort().join(",\n             "))
    }

    ccdd.writeToFileLn(file, "*/")
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Scheduler message definition and schedule definition table entry array
// indices
def ENABLE_STATUS = 0
def TYPE = 1
def FREQUENCY = 2
def REMAINDER = 3
def MESSAGE_INDEX = 4
def GROUP_DATA = 5

// Message definition table command entries
def COMMAND1 = "0xC000"
def COMMAND2 = "0x0001"
def COMMAND3 = "0x0000"

// Create the message definition table *****************************************

// Define the initial minimum column widths
def Integer[] columnWidth = [10, 6, 6, 6]

// Create the message definition table output file name
def mdtFileName = ccdd.getOutputPath() + "sch_def_msgtbl.c"

// Open the message definition table output file
def mdtFile = ccdd.openOutputFile(mdtFileName)

// Check if the output file successfully opened
if (mdtFile != null)
{
    // Add a header to the output file
    outputFileCreationInfo(mdtFile)

    // Get the message definition table entries
    def mdtEntries = ccdd.getApplicationMessageDefinitionTable()

    // Check if there are any entries in the message definition table
    if (mdtEntries.length > 0)
    {
        // Adjust the minimum column widths
        columnWidth[ENABLE_STATUS] = ccdd.getLongestString(mdtEntries, columnWidth[ENABLE_STATUS])
    }

    // Build the format string for an occupied entry
    def formatUsed = "    { {%-" + columnWidth[ENABLE_STATUS] + "s, %" + columnWidth[TYPE] + "s, %" + columnWidth[FREQUENCY] + "s, %" + columnWidth[REMAINDER] + "s} } \n"

    // Write the include statements for the standard cFE and HK headers
    ccdd.writeToFileLn(mdtFile, "")
    ccdd.writeToFileLn(mdtFile, "/*")
    ccdd.writeToFileLn(mdtFile, "** Include Files")
    ccdd.writeToFileLn(mdtFile, "*/")
    ccdd.writeToFileLn(mdtFile, "#include \"cfe.h\"")
    ccdd.writeToFileLn(mdtFile, "#include \"cfe_tbl_filedef.h\"")
    ccdd.writeToFileLn(mdtFile, "#include \"sch_platform_cfg.h\"")
    ccdd.writeToFileLn(mdtFile, "#include \"sch_msgdefs.h\"")
    ccdd.writeToFileLn(mdtFile, "#include \"sch_tbldefs.h\"")
    ccdd.writeToFileLn(mdtFile, "")

    // Get the array containing the application names
    def applicationNames = ccdd.getApplicationNames()

    // Step through each application name
    for (def name = 0; name < applicationNames.length; name++)
    {
        // Write the application message ID include statements for the header
        // files
        ccdd.writeToFileLn(mdtFile, "#include \"" + applicationNames[name].toLowerCase() + "_msgids.h\"")
    }

    ccdd.writeToFileLn(mdtFile, "")
    ccdd.writeToFileLn(mdtFile, "/*")
    ccdd.writeToFileLn(mdtFile, "** Default message table data")
    ccdd.writeToFileLn(mdtFile, "*/")
    ccdd.writeToFileLn(mdtFile, "SCH_MessageEntry_t SCH_DefaultMessageTable[SCH_MAX_MESSAGES] =")
    ccdd.writeToFileLn(mdtFile, "{")
    ccdd.writeToFileLn(mdtFile, "    /*---------------------------------------------------------*/")
    ccdd.writeToFileLn(mdtFile, "    /* DO NOT USE -- Entry #0 reserved for \"unused\" command ID */")
    ccdd.writeToFile(mdtFile, "    /*---------------------------------------------------------*/")

    // Step through each message definition table entry
    for (def row = 0; row < mdtEntries.length; row++)
    {
        ccdd.writeToFileFormat(mdtFile, "\n    /* command ID #%s */\n", row.toString())
        def comma = ","

        // Check if this is the last entry
        if (row == mdtEntries.length - 1)
        {
            comma = " "
        }

        // Check if this slot is occupied
        if (!mdtEntries[row].equals("SCH_UNUSED_MID"))
        {
            ccdd.writeToFileFormat(mdtFile, formatUsed, mdtEntries[row], COMMAND1, COMMAND2, COMMAND3, comma)
        }
        // The slot is not used
        else
        {
            ccdd.writeToFileFormat(mdtFile, "    { { %s } } \n", mdtEntries[row])
        }
    }

    // Terminate the message definition table statement
    ccdd.writeToFileLn(mdtFile, "};")

    // Close the output file
    ccdd.closeFile(mdtFile)
}
// The output file cannot be opened
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + mdtFileName + "<b>'")
}

// Create the schedule definition table output file name
def sdtFileName = ccdd.getOutputPath() + "sch_def_schtbl.c"

// Create the schedule definition table ****************************************

// Open the schedule definition table output file
def sdtFile = ccdd.openOutputFile(sdtFileName)

// Check if the output file successfully opened
if (sdtFile != null)
{
    // Add a header to the output files
    outputFileCreationInfo(sdtFile)

    // Write the include statements for the standard cFE and HK headers
    ccdd.writeToFileLn(sdtFile, "")
    ccdd.writeToFileLn(sdtFile, "/*")
    ccdd.writeToFileLn(sdtFile, "** Include Files")
    ccdd.writeToFileLn(sdtFile, "*/")
    ccdd.writeToFileLn(sdtFile, "#include \"cfe.h\"")
    ccdd.writeToFileLn(sdtFile, "#include \"cfe_tbl_filedef.h\"")
    ccdd.writeToFileLn(sdtFile, "#include \"sch_platform_cfg.h\"")
    ccdd.writeToFileLn(sdtFile, "#include \"sch_msgdefs.h\"")
    ccdd.writeToFileLn(sdtFile, "#include \"sch_tbldefs.h\"")
    ccdd.writeToFileLn(sdtFile, "")

    // Get the list of defined parameters
    def defines = ccdd.getApplicationScheduleDefinitionTableDefines()

    // Build the format for the defined parameters
    def formatDefines = "#define %-" + columnWidth[ENABLE_STATUS] + "s  %s \n"

    // Step through each defined parameter
    for (def define = 0; define < defines.length; define++)
    {
        // Output the define statement to the file
        ccdd.writeToFileFormat(sdtFile, formatDefines, defines[define][0], defines[define][1])
    }

    // Output the table file header
    ccdd.writeToFileLn(sdtFile, "")
    ccdd.writeToFileLn(sdtFile, "/*")
    ccdd.writeToFileLn(sdtFile, "** Table file header")
    ccdd.writeToFileLn(sdtFile, "*/")
    ccdd.writeToFileLn(sdtFile, "static CFE_TBL_FileDef_t CFE_TBL_FileDef =")
    ccdd.writeToFileLn(sdtFile, "{")
    ccdd.writeToFileLn(sdtFile, "    \"SCH_DefaultScheduleTable\",")
    ccdd.writeToFileLn(sdtFile, "    \"SCH.SCHED_DEF\",")
    ccdd.writeToFileLn(sdtFile, "    \"SCH schedule table\",")
    ccdd.writeToFileLn(sdtFile, "    \"sch_def_schtbl.tbl\",")
    ccdd.writeToFileLn(sdtFile, "    (sizeof (SCH_ScheduleEntry_t) * SCH_TABLE_ENTRIES)")
    ccdd.writeToFileLn(sdtFile, "};")
    ccdd.writeToFileLn(sdtFile, "")

    // Output the schedule definition table header
    ccdd.writeToFileLn(sdtFile, "/*")
    ccdd.writeToFileLn(sdtFile, "** Default schedule table data")
    ccdd.writeToFileLn(sdtFile, "*/")
    ccdd.writeToFileLn(sdtFile, "SCH_ScheduleEntry_t SCH_DefaultScheduleTable[SCH_TABLE_ENTRIES] =")
    ccdd.writeToFileLn(sdtFile, "{")
    ccdd.writeToFileLn(sdtFile, "    /*")
    ccdd.writeToFileLn(sdtFile, "    **    uint8     EnableState  -- SCH_UNUSED, SCH_ENABLED")
    ccdd.writeToFileLn(sdtFile, "    **    uint8     Type         -- 0 or SCH_ACTIVITY_SEND_MSG")
    ccdd.writeToFileLn(sdtFile, "    **    uint16    Frequency    -- how many seconds between Activity execution")
    ccdd.writeToFileLn(sdtFile, "    **    uint16    Remainder    -- seconds offset to perform Activity")
    ccdd.writeToFileLn(sdtFile, "    **    uint16    MessageIndex -- Message index into Message Definition table")
    ccdd.writeToFileLn(sdtFile, "    **    uint32    GroupData    -- Group and Multi-Group membership definitions")
    ccdd.writeToFileLn(sdtFile, "    */")

    // Step through each schedule definition table time slot
    for (def timeSlot = 0; timeSlot < ccdd.getNumberOfTimeSlots(); timeSlot++)
    {
        ccdd.writeToFileLn(sdtFile, "")
        ccdd.writeToFileFormat(sdtFile, "    /* Slot #%s */\n", (timeSlot + 1).toString())

        // Get the schedule definition table entries
        def sdtEntries = ccdd.getApplicationScheduleDefinitionTable(timeSlot)

        // Define the initial minimum column widths
        columnWidth = [1, 1, 1, 1, 1, 1]

        // Check if there are any entries in the schedule definition table
        if (sdtEntries.length > 0)
        {
            // Adjust the minimum column widths
            columnWidth = ccdd.getLongestStrings(sdtEntries, columnWidth)
        }

        // Build the format string
        def formatBody = "    {%-" + columnWidth[ENABLE_STATUS] + "s, %" + columnWidth[TYPE] + "s, %" + columnWidth[FREQUENCY] + "s, %" + columnWidth[REMAINDER] + "s, %" + columnWidth[MESSAGE_INDEX] + "s, %-" + columnWidth[GROUP_DATA] + "s}%s\n"

        // Step through each schedule definition table entry
        for (def row = 0; row < sdtEntries.length; row++)
        {
            def comma = ","

            // Check if this is the last row
            if (timeSlot == ccdd.getNumberOfTimeSlots() - 1 && row == sdtEntries.length - 1)
            {
                // Don't append a comma
                comma = " "
            }

            // Output the entry to the schedule definition table file
            ccdd.writeToFileFormat(sdtFile, formatBody, sdtEntries[row][ENABLE_STATUS], sdtEntries[row][TYPE], sdtEntries[row][FREQUENCY], sdtEntries[row][REMAINDER], sdtEntries[row][MESSAGE_INDEX], sdtEntries[row][GROUP_DATA], comma)
        }
    }

    // Terminate the schedule definition table
    ccdd.writeToFileLn(sdtFile, "};")

    // Close the output file
    ccdd.closeFile(sdtFile)
}
// The output file cannot be opened
else
{
    // Display an error dialog
    ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + sdtFileName + "<b>'")
}
