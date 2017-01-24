#******************************************************************************
# Description: Output a Types header file
# 
# This Ruby script generates a Types header file from the supplied table and
# packet information
#******************************************************************************
java_import Java::CCDD.CcddScriptDataAccessHandler

# Get the number of structure table rows
numStructRows = $ccdd.getStructureTableNumRows()

# Check if no structure data is supplied
if (numStructRows == 0)
    $ccdd.showErrorDialog("No structure data supplied to script " + $ccdd.getScriptName());
# Structure data is supplied
else
    # Get the 'System' data field value
    systemName =  $ccdd.getTableDataFieldValue($ccdd.getParentStructureTableName(), "System")
  
    # Check if the table doesn't have a 'System' data field
    if systemName == nil
        systemName = "unknown"
    end
    
    # Build the output file name
    outputFile = systemName + "_types.h"
    
    # Open the output file
    file = $ccdd.openOutputFile(outputFile)
    
    # Check if the output file successfully opened
    if file != nil
        # Add a header to the output file
        $ccdd.writeToFileLn(file,
                            "/* Created : " \
                            + $ccdd.getDateAndTime() \
                            + "\n   User    : " \
                            + $ccdd.getUser() \
                            + "\n   Project : " \
                            + $ccdd.getProject() \
                            + "\n   Script  : " \
                            + $ccdd.getScriptName() \
                            + "\n   Table(s): " \
                            + $ccdd.getStructureTablesByReferenceOrder().to_a.join(",\n             ") \
                            + " */\n")
                           
        $ccdd.writeToFileLn(file, "#ifndef _" + $ccdd.getParentStructureTableName() + "_types_H_")
        $ccdd.writeToFileLn(file, "#define _" + $ccdd.getParentStructureTableName() + "_types_H_")
        $ccdd.writeToFileLn(file, "")
        
        # Get the number of header files to include
        numInclude = $ccdd.getTableNumRows("header")
        
        # Check if there are any header files to include
        if numInclude != 0
            # Step through each header file name
            for row in 0..numInclude - 1
                # Output the include header statement
                $ccdd.writeToFileLn(file, "#include " + $ccdd.getTableData("header", "header file", row))
            end
            
            $ccdd.writeToFileLn(file, "")
        end
          
        # Get an array of the structures represented in the table data in the 
        # order in which they're referenced
        structureNames = $ccdd.getStructureTablesByReferenceOrder()
        
        # Step through each structure
        for struct in 0..structureNames.length - 1
            $ccdd.writeToFileLn(file, "typedef struct")
            $ccdd.writeToFileLn(file, "{")
            usedVariableNames = [];
    
            # Step through each row in the table
            for row in 0..numStructRows - 1
                # Check if the structure name in the row matches the current structure
                if structureNames[struct] == $ccdd.getStructureTableNameByRow(row)
                    # Get the variable name for this row in the structure
                    variableName = $ccdd.getStructureTableData("variable name", row)
                    
                    # Check if this is not an array member array definitions are
                    # output, but not members
                    if not variableName.end_with?("]")
                        # Check if the variable name hasn't already been processed;
                        # this is necessary to prevent duplicating the members in
                        # the type definition for a structure that is referenced
                        # as an array
                        if !usedVariableNames.include? variableName
                          # Add the variable name to the list of those already processed
                          usedVariableNames.push(variableName)

                          # Output the data type and variable name
                          $ccdd.writeToFile(file, "  " + $ccdd.getStructureTableData("data type", row) + " " + variableName)
                          
                          # Get the index of the array size column
                          arraySize = $ccdd.getStructureTableData("array size", row)
                          
                          # Check if the array size is provided
                          if arraySize != nil and !arraySize.empty?
                              # Output the array size
                              $ccdd.writeToFile(file, "[" + arraySize + "]")
                          # No array size for this row
                          else
                              # Get the index of the bit length column
                             bitLength = $ccdd.getStructureTableData("bit length", row)
                          
                             # Check if the bit length is provided
                             if bitLength != nil and !bitLength.empty?
                                  # Output the bit length
                                  $ccdd.writeToFile(file, ":" + bitLength)
                             end
                          end
                          
                          $ccdd.writeToFileLn(file, ";")
                        end
                    end
                end
            end
                   
            $ccdd.writeToFileLn(file, "} " + structureNames[struct] + ";")
            $ccdd.writeToFileLn(file, "")
        end
        
        $ccdd.writeToFileLn(file, "#endif")
        
        # Close the output file
        $ccdd.closeFile(file)
    # The output file cannot be opened
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'")
    end
end
                
