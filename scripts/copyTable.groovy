/******************************************************************************
 * Description: Output the CFS housekeeping (HK) application copy table
 * definition
 *
 * This Groovy script generates the HK copy table file from the supplied table
 * and packet information
 *****************************************************************************/
import CCDD.CcddScriptDataAccessHandler


// Length of the CCSDS header in bytes
def CCSDS_HEADER_LENGTH = 12

// Copy table entry array indices
def INPUT_MSG_ID = 0
def INPUT_OFFSET = 1
def OUTPUT_MSG_ID = 2
def OUTPUT_OFFSET = 3
def VARIABLE_BYTES = 4
def VARIABLE_PARENT = 5
def VARIABLE_NAME = 6
                     
// Get an array containing the rate column names
def copyTables = ccdd.getDataStreamNames()

// Create a copy table for each rate
for (def copyTable = 0; copyTable < copyTables.length; copyTable++)
{
    // Create the output file name
    def outputFile = "hk_cpy_tbl_" + copyTables[copyTable].replaceAll(" ", "_") + ".c"

    // Open the output file
    def file = ccdd.openOutputFile(outputFile)

    // Check if the output file successfully opened
    if (file != null)
    {
        // Add a header to the output file
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
                           
        // Get the copy table entries. The name of the field containing the message
        // ID name must be provided, and must be consistent across all parent
        // tables
        def copyTableEntries = ccdd.getCopyTableEntries(copyTables[copyTable], 12, "Message ID name", true)
    
        // Check if any copy table entries exist; i.e., if any packets are defined
        if (copyTableEntries.length != 0)
        {
            // Define the initial minimum column widths
            def Integer[] columnWidth = [10, 6, 10, 6, 5, 0, 0]
      
            // Get the minimum column widths
            columnWidth = ccdd.getLongestStrings(copyTableEntries, columnWidth)
      
        // Build the format strings
        def formatBody = "  {%-" +
                         (columnWidth[INPUT_MSG_ID] + 1) +
                         "s, %" +
                         (columnWidth[INPUT_OFFSET] + 1) +
                         "s, %-" +
                         (columnWidth[OUTPUT_MSG_ID] + 1) +
                         "s, %" +
                         (columnWidth[OUTPUT_OFFSET] + 1) +
                         "s, %" +
                         (columnWidth[VARIABLE_BYTES] + 1) +
                         "s}%s  /* %s : %s */\n"
        def formatHeader = "/* %-" +
                           (columnWidth[INPUT_MSG_ID] + 1) +
                           "s| %-" +
                           (columnWidth[INPUT_OFFSET] + 1) +
                           "s| %-" +
                           (columnWidth[OUTPUT_MSG_ID] + 1) +
                           "s| %-" +
                           (columnWidth[OUTPUT_OFFSET] + 1) +
                           "s| %-" +
                           (columnWidth[VARIABLE_BYTES] + 1) +
                           "s */\n"
        
            // Write the include statements for the standard cFE and HK headers
            ccdd.writeToFileLn(file, "//include \"cfe.h\"")
            ccdd.writeToFileLn(file, "//include \"cfe_tbl_filedef.h\"")
            ccdd.writeToFileLn(file, "//include \"hk_utils.h\"")
            ccdd.writeToFileLn(file, "//include \"hk_app.h\"")
            ccdd.writeToFileLn(file, "//include \"hk_tbldefs.h\"")
            ccdd.writeToFileLn(file, "//include \"hk_msgids.h\"")
        
            // Get the array containing the packet application names
            def applicationNames = ccdd.getApplicationNames()
        
            // Step through each application name
            for (def name = 0; name < applicationNames.length; name++)
            {
                // Write the include statements for the header files
                ccdd.writeToFileLn(file,
                               "//include \""
                               + applicationNames[name].toLowerCase()
                               + "_msids.h\"")
            }
            
            ccdd.writeToFileLn(file, "")
        
            ccdd.writeToFileLn(file, "static CFE_TBL_FileDef_t CFE_TBL_FileDef =")
            ccdd.writeToFileLn(file, "{");
            ccdd.writeToFileLn(file, "  \"HK_M_CopyTable\",")
            ccdd.writeToFileLn(file, "  \"HK_M_APP.CopyTable\",")
            ccdd.writeToFileLn(file, "  \"HK_M Copy Tbl\",")
            ccdd.writeToFileLn(file, "  \"hk_M_cpy_tbl.tbl\",")
            ccdd.writeToFileLn(file, "  sizeof (hk_M_copy_table_entry_t) * HK_M_COPY_TABLE_ENTRIES")
            ccdd.writeToFileLn(file, "};")
            ccdd.writeToFileLn(file, "")
            ccdd.writeToFileLn(file, "")
        
            // Write the copy table definition statement
            ccdd.writeToFileLn(file, "hk_copy_table_entry_t HK_CopyTable[HK_COPY_TABLE_ENTRIES] =")
            ccdd.writeToFileLn(file, "{");
            ccdd.writeToFileFormat(file,
                               formatHeader,
                               "Input",
                               "Input",
                               "Output",
                               "Output",
                               "Num")
            ccdd.writeToFileFormat(file,
                               formatHeader,
                               "Message ID",
                               "Offset",
                               "Message ID",
                               "Offset",
                               "Bytes")
    
            // Step through each copy table entry
            for (def row = 0; row < copyTableEntries.length; row++)
            {
                def comma = ","
            
                // Check if this is the last row
                if (row == copyTableEntries.length)
                {
                    // Don't append a comma
                    comma = " "
                }
                
                // Write the entry to the copy table file
                ccdd.writeToFileFormat(file,
                                   formatBody,
                                   copyTableEntries[row][INPUT_MSG_ID],
                                   copyTableEntries[row][INPUT_OFFSET],
                                   copyTableEntries[row][OUTPUT_MSG_ID],
                                   copyTableEntries[row][OUTPUT_OFFSET],
                                   copyTableEntries[row][VARIABLE_BYTES],
                                   comma,
                                   copyTableEntries[row][VARIABLE_PARENT],
                                   copyTableEntries[row][VARIABLE_NAME])
            }
            
            // Terminate the table definition statement
            ccdd.writeToFileLn(file, "};")
        }
        
        ccdd.writeToFileLn(file, "")
        ccdd.writeToFileLn(file, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")

        // Close the output file
        ccdd.closeFile(file)
    }
    // The output file cannot be opened
    else
    {
        // Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'")
    }
}
