#******************************************************************************
# Description: Output a Types header file
# 
# This Python script generates a Types header file from the supplied table and
# packet information
#******************************************************************************
from CCDD import CcddScriptDataAccessHandler

# Get the number of structure table rows
numStructRows = ccdd.getStructureTableNumRows()

# Check if no structure data is supplied
if (numStructRows == 0):
    ccdd.showErrorDialog("No structure data supplied to script " + ccdd.getScriptName())
# Structure data is supplied
else:
    # Get the 'System' data field value
    systemName = ccdd.getTableDataFieldValue(ccdd.getParentStructureTableName(), "System")
    
    # Check if the table doesn't have a 'System' data field
    if systemName is None:
        systemName = "unknown"
        
    # Build the output file name
    outputFile = systemName + "_types.h"
    
    # Open the output file
    file = ccdd.openOutputFile(outputFile)
    
    # Check if the output file successfully opened
    if file is not None:
        # Add a header to the output file
        ccdd.writeToFileLn(file,
                           "/* Created : "
                           + ccdd.getDateAndTime()
                           + "\n   User    : "
                           + ccdd.getUser()
                           + "\n   Project : "
                           + ccdd.getProject()
                           + "\n   Script  : "
                           + ccdd.getScriptName()
                           + "\n   Table(s): "
                           + (",\n             ").join(ccdd.getStructureTablesByReferenceOrder())
                           + " */\n")
                           
                           
        ccdd.writeToFileLn(file, "#ifndef _" + ccdd.getParentStructureTableName() + "_types_H_")
        ccdd.writeToFileLn(file, "#define _" + ccdd.getParentStructureTableName() + "_types_H_")
        ccdd.writeToFileLn(file, "")
        
        # Get the number of header files to include
        numInclude = ccdd.getTableNumRows("header")
        
        # Check if there are any header files to include
        if numInclude != 0:
            # Step through each header file name
            for row in range(numInclude):
                # Output the include header statement
                ccdd.writeToFileLn(file, "#include " + ccdd.getTableData("header", "header file", row))
        
            ccdd.writeToFileLn(file, "")
        
        # Get an array of the structures represented in the table data in the 
        # order in which they're referenced
        structureNames = ccdd.getStructureTablesByReferenceOrder()
        
        # Step through each structure
        for struct in range(len(structureNames)):
            ccdd.writeToFileLn(file, "typedef struct")
            ccdd.writeToFileLn(file, "{")
            usedVariableNames = []
                
            # Step through each row in the table
            for row in range(numStructRows):
                # Check if the structure name in the row matches the current structure
                if structureNames[struct] == ccdd.getStructureTableNameByRow(row):
                    # Get the variable name for this row in the structure
                    variableName = ccdd.getStructureTableData("variable name", row)
                    
                    # Check if this is not an array member; array definitions are
                    # output, but not members
                    if not variableName.endswith("]"):
                        # Check if the variable name hasn't already been processed;
                        # this is necessary to prevent duplicating the members in
                        # the type definition for a structure that is referenced
                        # as an array
                        if variableName not in usedVariableNames:
                            # Add the variable name to the list of those already processed
                            usedVariableNames.append(variableName)

                            # Output the data type and variable name
                            ccdd.writeToFile(file, "  " + ccdd.getStructureTableData("data type", row) + " " + variableName)
                            
                            # Get the index of the array size column
                            arraySize = ccdd.getStructureTableData("array size", row)
                            
                            # Check if the array size is provided
                            if arraySize is not None and arraySize:
                                # Output the array size
                                ccdd.writeToFile(file, "[" + arraySize + "]")
                            # No array size for this row
                            else:
                                # Get the index of the bit length column
                               bitLength = ccdd.getStructureTableData("bit length", row)
                            
                               # Check if the bit length is provided
                               if bitLength is not None and bitLength:
                                    # Output the bit length
                                    ccdd.writeToFile(file, ":" + bitLength)
                            
                            ccdd.writeToFileLn(file, ";")
        
            ccdd.writeToFileLn(file, "} " + structureNames[struct] + ";")
            ccdd.writeToFileLn(file, "")
        
        ccdd.writeToFileLn(file, "#endif")
        
        # Close the output file
        ccdd.closeFile(file)
    # The output file cannot be opened
    else:
        # Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'")
