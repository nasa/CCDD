#******************************************************************************
# Description: Output the CFS housekeeping (HK) application copy table definition
# 
# This Ruby script generates the HK copy table file from the supplied table and
# packet information
#******************************************************************************
java_import Java::CCDD.CcddScriptDataAccessHandler

# Length of the CCSDS header in bytes
ccsds_header_length = 12

# Copy table entry array indices
input_msg_id = 0
input_offset = 1
output_msg_id = 2
output_offset = 3
variable_bytes = 4
variable_parent = 5
variable_name = 6
      
# Get an array containing the rate column names
copyTables = $ccdd.getDataStreamNames() 

# Create a copy table for each rate                    
for copyTable in copyTables do                     
    # Create the output file name
    outputFile = "hk_cpy_tbl_" + copyTable.gsub(" ", "_") + ".c"
                
  # Open the output file
  file = $ccdd.openOutputFile(outputFile)
  
  # Check if the output file successfully opened
  if file != nil
      # Add a header to the output file
      $ccdd.writeToFileLn(file,
                          "/* Created: " \
                          + $ccdd.getDateAndTime() \
                          + "\n   User   : " \
                          + $ccdd.getUser() \
                          + "\n   Project: " \
                          + $ccdd.getProject() \
                          + "\n   Script : " \
                          + $ccdd.getScriptName() \
                          + " */\n")
                       
      # Get the copy table entries. The name of the field containing the message
      # ID name must be provided, and must be consistent across all parent
      # tables
      copyTableEntries = $ccdd.getCopyTableEntries(copyTable, 12, "Message ID name", true)
      
      # Check if any copy table entries exist; i.e., if any packets are defined
      if copyTableEntries.length != 0
          # Define the initial minimum column widths
          columnWidth = [10, 6, 10, 6, 5, 0, 0]
        
          # Get the minimum column widths
          columnWidth = $ccdd.getLongestStrings(copyTableEntries, columnWidth)
        
          # Column widths
          columnWidth = [10, 6, 10, 6, 5]
          
          # Step through each copy table entry
          for row in 0..copyTableEntries.length - 1
              # Step through copy table entry each column
              for column in 0..4
                  # Check if the size of this element is the largest found for
                  # this column
                  if copyTableEntries[row][column].length() > columnWidth[column]
                      # Store the largest element width
                      columnWidth[column] = copyTableEntries[row][column].length()
                  end
              end
          end
          
          # Build the format strings
          formatBody = "  {%-" \
                       + (columnWidth[input_msg_id] + 1).to_s \
                       + "s, %" \
                       + (columnWidth[input_offset] + 1).to_s \
                       + "s, %-" \
                       + (columnWidth[output_msg_id] + 1).to_s \
                       + "s, %" \
                       + (columnWidth[output_offset] + 1).to_s \
                       + "s, %" \
                       + (columnWidth[variable_bytes] + 1).to_s \
                       + "s}%s  /* %s : %s */\n"
          formatHeader = "/* %-" \
                         + (columnWidth[input_msg_id] + 1).to_s \
                         + "s| %-" \
                         + (columnWidth[input_offset] + 1).to_s \
                         + "s| %-" \
                         + (columnWidth[output_msg_id] + 1).to_s \
                         + "s| %-" \
                         + (columnWidth[output_offset] + 1).to_s \
                         + "s| %-" \
                         + (columnWidth[variable_bytes] + 1).to_s \
                         + "s */\n"
  
          # Write the include statements for the standard cFE and HK headers
          $ccdd.writeToFileLn(file, "#include \"cfe.h\"")
          $ccdd.writeToFileLn(file, "#include \"cfe_tbl_filedef.h\"")
          $ccdd.writeToFileLn(file, "#include \"hk_utils.h\"")
          $ccdd.writeToFileLn(file, "#include \"hk_app.h\"")
          $ccdd.writeToFileLn(file, "#include \"hk_tbldefs.h\"")
          $ccdd.writeToFileLn(file, "#include \"hk_msgids.h\"")
          
          # Get the array containing the packet application names
          applicationNames = $ccdd.getApplicationNames()
          
          # Step through each application name
          for name in 0..applicationNames.length - 1
              # Write the include statements for the header files
              $ccdd.writeToFileLn(file, 
                                 "#include \""  \
                                 + applicationNames[name].downcase  \
                                 + "_msids.h\"")
          end
          
          $ccdd.writeToFileLn(file, "")
          
          # Write the copy table definition statement
          $ccdd.writeToFileLn(file, "hk_copy_table_entry_t HK_CopyTable[HK_COPY_TABLE_ENTRIES] =")
          $ccdd.writeToFileLn(file, "{")
          $ccdd.writeToFileFormat(file,
                                 formatHeader,
                                 "Input",
                                 "Input",
                                 "Output",
                                 "Output",
                                 "Num")
          $ccdd.writeToFileFormat(file,
                                 formatHeader,
                                 "Message ID",
                                 "Offset",
                                 "Message ID",
                                 "Offset",
                                 "Bytes")
      
          # Step through each copy table entry
          for row in 0..copyTableEntries.length - 1
              # Write the entry to the copy table file
              $ccdd.writeToFileFormat(file,
                                     formatBody,
                                     copyTableEntries[row][input_msg_id],
                                     copyTableEntries[row][input_offset],
                                     copyTableEntries[row][output_msg_id],
                                     copyTableEntries[row][output_offset],
                                     copyTableEntries[row][variable_bytes],
                                     (row == copyTableEntries.length - 1 ? " " : ","),
                                     copyTableEntries[row][variable_parent],
                                     copyTableEntries[row][variable_name])
          end
  
          # Terminate the table definition statement
          $ccdd.writeToFileLn(file, "};")
      end
      
      $ccdd.writeToFileLn(file, "")
      $ccdd.writeToFileLn(file, "CFE_TBL_FILEDEF(HK_CopyTable, HK.CopyTable, HK Copy Tbl, hk_cpy_tbl.tbl)")
  
      # Close the output file
      $ccdd.closeFile(file)
  # The output file cannot be opened
  else
      # Display an error dialog
      $ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'")
  end
end
