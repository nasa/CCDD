#******************************************************************************
# Description: Output the CFS housekeeping (HK) application copy table definition
# 
# This Python script generates the HK copy table file from the supplied table
# and packet information
# *****************************************************************************
from CCDD import CcddScriptDataAccessHandler

# Length of the CCSDS header in bytes
CCSDS_HEADER_LENGTH = 12

# Copy table entry array indices
ENABLE_STATUS = 0
TYPE = 1
FREQUENCY = 2
REMAINDER = 3
MESSAGE_INDEX = 4
GROUP_DATA = 5
#Command entries
COMMAND1 = "0xC000"
COMMAND2 = "0x0001"
COMMAND3 = "0x0000"
            
# Create the output file name
outputFile1 = "sch_def_msgtbl2.c"

# Open the output file
file1 = ccdd.openOutputFile(outputFile1)            
# Check if the output file successfully opened
if file1 is not None:                      
    
    columnWidth1 = [10, 20, 5, 5]
    
    # Build the format strings
    formatBody1 = "  { {%-" \
                 + str(columnWidth1[ENABLE_STATUS] + 1) \
                 + "s, %" \
                 + str(columnWidth1[TYPE] + 1) \
                 + "s, %" \
                 + str(columnWidth1[FREQUENCY] + 1) \
                 + "s, %" \
                 + str(columnWidth1[REMAINDER] + 1) \
                 + "s} } \n"
    
        # Build the format strings
    formatBody2 = "  { { %s } } \n"
    
    # Add a header to the output file
    ccdd.writeToFileLn(file1,
                       "/* Created: "
                       + ccdd.getDateAndTime()
                       + "\n   User   : "
                       + ccdd.getUser()
                       + "\n   Project: "
                       + ccdd.getProject()
                       + "\n   Script : "
                       + ccdd.getScriptName()
                       + " */\n")
    
    ccdd.writeToFileLn(file1, "")
    # Write the include statements for the standard cFE and HK headers
    ccdd.writeToFileLn(file1, "#include \"cfe.h\"")
    ccdd.writeToFileLn(file1, "#include \"cfe_tbl_filedef.h\"")
    ccdd.writeToFileLn(file1, "#include \"sch_platform_cfg.h\"")
    ccdd.writeToFileLn(file1, "#include \"sch_msgdefs.h\"")
    ccdd.writeToFileLn(file1, "#include \"sch_tbldefs.h\"")
    
    ccdd.writeToFileLn(file1, "")
    
    # Get the array containing the packet application names
    applicationNames = ccdd.getApplicationNames();
            
    # Step through each application name
    for name in range(len(applicationNames)):
        # Write the include statements for the header files
        ccdd.writeToFileLn( file1,
                            "#include \"" 
                            + applicationNames[name].lower() 
                            + "_msids.h\"")
    
    ccdd.writeToFileLn(file1, "")
    ccdd.writeToFileLn(file1, "/*")
    ccdd.writeToFileLn(file1, "** Default schedule table data")
    ccdd.writeToFileLn(file1, "*/")
    ccdd.writeToFileLn(file1, "SCH_MessageEntry_t SCH_DefaultMessageTable[SCH_MAX_MESSAGES] =")
    ccdd.writeToFileLn(file1, "{")

    commandTableEntries = ccdd.getApplicationCommandTable()

    # Step through each copy table entry
    for row in range(len(commandTableEntries)):
        ccdd.writeToFileLn(file1, "/* command ID #%2d  */" % row)
        comma = ","
        #Check if this is the last entry
        if row == len(commandTableEntries):
            comma = " "
        #Check if this is not an unused slot    
        if commandTableEntries[row] != "SCH_UNUSED_MID":
            ccdd.writeToFileFormat(file1,
                                   formatBody1,
                                   commandTableEntries[row], 
                                   COMMAND1,
                                   COMMAND2,
                                   COMMAND3,
                                   comma   
                                   )
        else:
            ccdd.writeToFileFormat(file1,
                                   formatBody2,
                                   commandTableEntries[row]
                                   )   
            
    # Terminate the table definition statement
    ccdd.writeToFileLn(file1, "};")
    
    # Close the output file
    ccdd.closeFile(file1)
# The output file cannot be opened
else:
    # Display an error dialog
    ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile1 + "<b>'")
                          
# Create the output file name
outputFile = "sch_def_schtbl2.c"

#Create the schedule table 
# Open the output file
file = ccdd.openOutputFile(outputFile)

# Check if the output file successfully opened
if file is not None:
    # Add a header to the output file
    ccdd.writeToFileLn(file,
                       "/* Created: "
                       + ccdd.getDateAndTime()
                       + "\n   User   : "
                       + ccdd.getUser()
                       + "\n   Project: "
                       + ccdd.getProject()
                       + "\n   Script : "
                       + ccdd.getScriptName()
                       + " */\n")
                                                 
    # Define the initial minimum column widths
    columnWidth = [11, 20, 5, 5, 5, 15]
      
    slotNum =  ccdd.getNumberOfSlots();
      
    # Build the format strings
    formatBody = "  {%-" \
                 + str(columnWidth[ENABLE_STATUS] + 1) \
                 + "s, %" \
                 + str(columnWidth[TYPE] + 1) \
                 + "s, %" \
                 + str(columnWidth[FREQUENCY] + 1) \
                 + "s, %" \
                 + str(columnWidth[REMAINDER] + 1) \
                 + "s, %" \
                 + str(columnWidth[MESSAGE_INDEX] + 1) \
                 + "s, %" \
                 + str(columnWidth[GROUP_DATA] + 1) \
                 + "s}  \n"
    
    formatDefines = "#define %-" \
                 + str(columnWidth[ENABLE_STATUS] + 1) \
                 + "s %s \n" 
    
    # Write the include statements for the standard cFE and HK headers
    ccdd.writeToFileLn(file, "#include \"cfe.h\"")
    ccdd.writeToFileLn(file, "#include \"cfe_tbl_filedef.h\"")
    ccdd.writeToFileLn(file, "#include \"sch_platform_cfg.h\"")
    ccdd.writeToFileLn(file, "#include \"sch_msgdefs.h\"")
    ccdd.writeToFileLn(file, "#include \"sch_tbldefs.h\"")
    ccdd.writeToFileLn(file, "")
    
    defines = ccdd.getDefinesList()
    
    for define in xrange (len(defines)):
        ccdd.writeToFileFormat(file, 
                               formatDefines,
                               defines[define][0],
                               defines[define][1])
        
    ccdd.writeToFileLn(file, "")
    ccdd.writeToFileLn(file, "")
    
    ccdd.writeToFileLn(file, "/*")
    ccdd.writeToFileLn(file, "** Table file header")
    ccdd.writeToFileLn(file, "*/")    
    ccdd.writeToFileLn(file, "static CFE_TBL_FileDef_t CFE_TBL_FileDef =")
    ccdd.writeToFileLn(file, "{")
    ccdd.writeToFileLn(file, "  \"SCH_DefaultScheduleTable\",")
    ccdd.writeToFileLn(file, "  \"SCH_APP.SCHED_DEF\",")
    ccdd.writeToFileLn(file, "  \"SCH schedule table\",")
    ccdd.writeToFileLn(file, "  \"sch_def_schtbl.tbl\",")
    ccdd.writeToFileLn(file, "  sizeof (SCH_ScheduleEntry_t) * SCH_TABLE_ENTRIES")
    ccdd.writeToFileLn(file, "};")
    ccdd.writeToFileLn(file, "")
    ccdd.writeToFileLn(file, "")
    
    ccdd.writeToFileLn(file, "/*")
    ccdd.writeToFileLn(file, "** Default schedule table data")
    ccdd.writeToFileLn(file, "*/")
    ccdd.writeToFileLn(file, "SCH_ScheduleEntry_t SCH_DefaultScheduleTable[SCH_TABLE_ENTRIES] =")
    ccdd.writeToFileLn(file, "{")
    
    ccdd.writeToFileLn(file, "/*")
    ccdd.writeToFileLn(file, "**    uint8     EnableState  -- SCH_UNUSED, SC_ENABLED")
    ccdd.writeToFileLn(file, "**    uint8     Type         -- 0 or SCH_ACTIVITY_SEND_MSG")
    ccdd.writeToFileLn(file, "**    uint16    Frequency    -- how many seconds between Activity execution")
    ccdd.writeToFileLn(file, "**    uint16    Remainder    -- seconds offset to perform Activity")
    ccdd.writeToFileLn(file, "**    uint16    MessageIndex -- Message index into Message Definition table")
    ccdd.writeToFileLn(file, "**    uint32    GroupData    -- Group and Multi-Group membership definitions")
    ccdd.writeToFileLn(file, "*/")

    ccdd.createApplicationSchedulerTable()
    
     # Step through each copy table entry
    for slot in xrange(0, slotNum):
        ccdd.writeToFileLn(file, "")
        ccdd.writeToFileLn(file, "/* slot #%2d  */" % (slotNum + 1))
        copyTableEntries =  ccdd.getApplicationSchedulerEntry(slot)
        
        for  pos in range(len(copyTableEntries)):
            comma = ","
            
            # Check if this is the last row
            if pos == len(copyTableEntries):
                # Don't append a comma
                comma = " "
                
            # Write the entry to the copy table file
            ccdd.writeToFileFormat(file,
                                   formatBody,
                                   copyTableEntries[pos][ENABLE_STATUS],
                                   copyTableEntries[pos][TYPE],
                                   copyTableEntries[pos][FREQUENCY],
                                   copyTableEntries[pos][REMAINDER],
                                   copyTableEntries[pos][MESSAGE_INDEX],
                                   copyTableEntries[pos][GROUP_DATA],
                                   comma)

    # Terminate the table definition statement
    ccdd.writeToFileLn(file, "};")
    
    ccdd.writeToFileLn(file, "")
    ccdd.writeToFileLn(file, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")

    # Close the output file
    ccdd.closeFile(file)
# The output file cannot be opened
else:
    # Display an error dialog
    ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'")
