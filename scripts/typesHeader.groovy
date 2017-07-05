/******************************************************************************
 * Description: Output a Types header file
 * 
 * This Groovy script generates a Types header file from the supplied table and
 * packet information
 *****************************************************************************/
import CCDD.CcddScriptDataAccessHandler


// Get the number of structure table rows
def numStructRows = ccdd.getStructureTableNumRows()

// Check if no structure data is supplied
if (numStructRows == 0)
{
    ccdd.showErrorDialog("No structure data supplied to script " + ccdd.getScriptName())
}
// Structure data is supplied
else
{
    // Get the 'System' data field value
    def systemName = ccdd.getTableDataFieldValue(ccdd.getRootStructureTableNames()[0], "System")
    
    // Check if the table doesn't have a 'System' data field
    if (systemName == null)
    {
        systemName = "unknown";
    }
        
    // Build the output file name
    def outputFile = systemName + "_types.h"
    
    
    // Open the output file
    def file = ccdd.openOutputFile(outputFile)
    
    // Check if the output file successfully opened
    if (file != null)
    {
        // Add a header to the output file
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
                           + ccdd.getStructureTablesByReferenceOrder().join(",\n             ")
                           + " */\n")
                           
        ccdd.writeToFileLn(file, "#ifndef _" + ccdd.getRootStructureTableNames()[0] + "_types_H_")
        ccdd.writeToFileLn(file, "#define _" + ccdd.getRootStructureTableNames()[0] + "_types_H_")
        ccdd.writeToFileLn(file, "");
        
        // Get the number of header files to include
        numInclude = ccdd.getTableNumRows("header")
        
        // Check if there are any header files to include
        if (numInclude != 0)
        {
            // Step through each header file name
            for (row = 0; row < numInclude; row++)
            {
                // Output the include header statement
                ccdd.writeToFileLn(file, "#include " + ccdd.getTableData("header", "header file", row))
            }
        
            ccdd.writeToFileLn(file, "")
        }
        
        // Get an array of the structures represented in the table data in the 
        // order in which they're referenced
        structureNames = ccdd.getStructureTablesByReferenceOrder()
        
        // Step through each structure
        for (struct = 0; struct < structureNames.length; struct++)
        {
            ccdd.writeToFileLn(file, "typedef struct")
            ccdd.writeToFileLn(file, "{")
            List<String> usedVariableNames = []
            
            // Step through each row in the table
            for (row = 0; row < numStructRows; row++)
            {
                // Check if the structure name in the row matches the current structure
                if (structureNames[struct].equals(ccdd.getStructureTableNameByRow(row)))
                {
                    // Get the defiable name for this row in the structure
                    variableName = ccdd.getStructureTableData("variable name", row)
                    
                    // Check if this is not an array member; array definitions are
                    // output, but not members
                    if (!variableName.endsWith("]"))
                    {
                        // Check if the variable name hasn't already been processed;
                        // this is necessary to prevent duplicating the members in
                        // the type definition for a structure that is referenced
                        // as an array
                        if (!usedVariableNames.contains(variableName))
                        {
                            // Add the variable name to the list of those already processed
                            usedVariableNames.push(variableName)

                            // Output the data type and defiable name
                            ccdd.writeToFile(file, "  " + ccdd.getStructureTableData("data type", row) + " " + variableName)
                            
                            // Get the index of the array size column
                            arraySize = ccdd.getStructureTableData("array size", row)
                            
                            // Check if the array size is provided
                            if (arraySize != null && !arraySize.isEmpty())
                            {
                                // Output the array size
                                ccdd.writeToFile(file, "[" + arraySize +"]")
                            }
                            // No array size for this row
                            else 
                            {
                                // Get the index of the bit length column
                               bitLength = ccdd.getStructureTableData("bit length", row)
                            
                               // Check if the bit length is provided
                                if (bitLength != null && !bitLength.isEmpty())
                                {
                                    // Output the bit length
                                    ccdd.writeToFile(file, ":" + bitLength)
                                }
                            }
                            
                            ccdd.writeToFileLn(file, ";")
                        }
                    }
                }
            }
        
            ccdd.writeToFileLn(file, "} " + structureNames[struct] + ";")
            ccdd.writeToFileLn(file, "")
        }
        
        ccdd.writeToFileLn(file, "#endif")
        
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
