/*******************************************************************************
 * Description: Output an ITOS page file
 * 
 * This Groovy script generates an ITOS page file from the supplied telemetry
 * information.
 * 
 * Assumptions: Arrays are limited to a single dimension
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
def outputFileCreationInfo(file) {
    // Add the build information and header to the output file
    ccdd.writeToFileLn(file, "# Created : " + ccdd.getDateAndTime() + "\n# User    : " + ccdd.getUser() + "\n# Project : " + ccdd.getProject() + "\n# Script  : " + ccdd.getScriptName())

    // Check if any table is associated with the script
    if (ccdd.getTableNumRows() != 0)
    {
        ccdd.writeToFileLn(file, "# Table(s): " + ccdd.getTableNames().sort().join(",\n#           "))
    }

    // Check if any groups is associated with the script
    if (ccdd.getAssociatedGroupNames().length != 0)
    {
        ccdd.writeToFileLn(file, "# Group(s): " + ccdd.getAssociatedGroupNames().sort().join(",\n#           "))
    }

    ccdd.writeToFileLn(file, "")
}

/*******************************************************************************
 * Determine if the row containing the specified variable is not an array
 * definition. A row in a table is an array definition if a value is present in
 * the Array Size column but the variable name does not end with a ']'
 * 
 * @param variableName
 *            variable name
 * 
 * @param arraySize
 *            array size
 * 
 * @return true if the variable is not an array definition
 ******************************************************************************/
def isVariable(variableName, arraySize)
{
    // Only output non-array variables or array members (i.e., skip array
    // definitions)
    return variableName != null && arraySize != null && (arraySize.isEmpty() || variableName.endsWith("]"))
}

/*******************************************************************************
 * Convert an array member variable name by replacing left square brackets with
 * underscores and removing right square brackets (example: a[2] becomes a_2)
 * 
 * @param variableName
 *            variable name
 * 
 * @return Variable name with the square brackets replaced
 ******************************************************************************/
def convertArrayMember(variableName)
{
    return variableName.replaceAll("[\\[]", "_").replaceAll("[\\]]", "")
}

/*******************************************************************************
 * Adjust the row counter to the next row. If the number of rows exceeds the
 * maximum start a new column
 * 
 * @param pageFile
 *            reference to the output file
 * 
 * @param variableName
 *            variable name
 * 
 * @param fullVariableName
 *            variable name with structure path
 * 
 * @param row
 *            structure data row index
 ******************************************************************************/
def nextRow(pageFile, variableName, fullVariableName, row)
{
    // Check if the row counter is at the maximum
    if (rowCount >= maxNumRows)
    {
        // Step through the rows in the data that have been processed
        for (; row >= 0; row--)
        {
            // Get the header (structure) name for this row
            def headerName = fullHeaderNames[row]

            // Check if the variable belongs to the same structure
            if (headerName != "" && fullVariableName.equals(headerName + "_" + variableName))
            {
                // Update the column header
                lastSubStructureName = headerNames[row]
            }
        }

        // Go to the next column
        columnCount++
        ccdd.writeToFileLn(pageFile, "## col_max_len going from " + maxColumnLength + " back to " + columnStep)
        rowCount = 1
        columnOffset = +columnOffset + +maxColumnLength + 2
        maxColumnLength = columnStep

        // Check if the current variable is a member within an array
        if (inMiddleOfArray)
        {
            ccdd.writeToFileLn(pageFile, "array_fmt(1, " + columnOffset + ",\"" + nextColumnHeader + "\")")
        }
        // Not a variable within an array
        else
        {
            ccdd.writeToFileLn(pageFile, "(1, " + columnOffset + ",\"" + lastSubStructureName + "\")")
        }
    }
    // Not at the maximum row
    else
    {
        rowCount++
    }
}

/*******************************************************************************
 * Check if the supplied array size contains a value
 * 
 * @param array
 *            size variable's array size value
 * 
 * @return true if the array size isn't empty
 ******************************************************************************/
def isArrayElement(arraySize)
{
    return arraySize != null && !arraySize.isEmpty()
}

/*******************************************************************************
 * Get the array index value from the variable name
 * 
 * @param name
 *            variable name
 * 
 * @return Array index value from the variable name
 ******************************************************************************/
def getIndex(name)
{
    // Split the variable name on the underscores and use the last part as the
    // array index
    def parts = name.split("_")
    return parts[parts.length - 1]
}

/*******************************************************************************
 * Selects the format arguments to use for a particular ITOS type, based on its
 * type
 * 
 * @param itosEncode
 *            data type in ITOS encoded form
 * 
 * @return ITOS output format string
 ******************************************************************************/
def setITOSFormatFlag(itosEncode)
{
    def itosFormat = ""
    def withSign = 0
    modNum = modNumDefault

    // Select based on the data type character (F, I, U, S)
    switch (itosEncode.substring(0, 1))
    {
        // Floating point
        case 'F':
            // Get the number of bytes that define the floating point
            def numBytes = itosEncode.length() - 1

            // Check if the number of bytes is greater than 4 (i.e., it's a
            // double precision floating point)
            if (numBytes > 4)
            {
                // Set the format string and parameters for a double
                itosFormat = "%13.3f"
                numITOSDigits = 14
                modNum = modNumDefault / 2
            }
            // The number of bytes is equal to or less than 4 (i.e., it's a
            // single precision floating point)
            else
            {
                // Set the format string and parameters for a float
                itosFormat = "%6.3f"
                numITOSDigits = 7
                modNum = modNumDefault / 2
            }

            break

        // Signed integer
        case 'I':
        // Set the value to add a space for the sign
            withSign = 1

        // Unsigned integer
        case 'U':
            // Get the number of bytes that define the (unsigned) integer
            def numBytes = itosEncode.length() - 1

            // Determine the number of digits required to display the largest
            // possible value for the (unsigned) integer with the specified
            // number of bytes
            int nDigits = 2 * numBytes + 1 + Math.floor(numBytes / 4)

            // Add a digit for a +/- if signed integer
            nDigits += +withSign

            // Set the format string and parameters for a (unsigned) integer
            itosFormat = "%" + nDigits.toString() + "d"
            numITOSDigits = nDigits

            // Check if the number of bytes is greater than 2
            if (numBytes > 2)
            {
                // Set the format parameter
                modNum = modNumDefault / 2
            }

            break

        // Character or string
        case 'S':
            // Set the format string and parameters for a character or string
            itosFormat = "%s"
            numITOSDigits = 10
            modNum = modNumDefault / 2
            break

        default:
            break
    }

    return itosFormat
}

/*******************************************************************************
 * Output a single mnemonic definition
 * 
 * @param pageFile
 *            reference to the output file
 * 
 * @param row
 *            row index in the structure data table
 * 
 * @param fltCompName
 *            flight computer name
 * 
 * @returns true is a mnemonic definition is output to the file
 ******************************************************************************/
def outputMnemonic(pageFile, row, fltCompName)
{
    def isOutput = false
    def itosFormat = ""

    def variableName = ccdd.getStructureVariableName(row)
    def dataType = ccdd.getStructureDataType(row)

    // Get the ITOS encoded form of the data type
    def itosEncode = ccdd.getITOSEncodedDataType(dataType, "BIG_ENDIAN")

    // Check if this data type is a recognized base type or structure
    if (itosEncode != null)
    {
        // Check if the data type is a primitive (not a structure)
        if (!itosEncode.equals(dataType))
        {
            // Get the ITOS output format string based on the encoding
            itosFormat = setITOSFormatFlag(itosEncode)
        }

        // Get the variable name and array size
        def arraySize = ccdd.getStructureArraySize(row)
        def fullVariableName = ccdd.getFullVariableName(row)

        // See if this row would exceed the maximum. If so start another column
        nextRow(pageFile, variableName, fullVariableName, row)

        // Get the full variable name (including the variable's structure path)
        def tmp = ccdd.getFullVariableName(row, " ")

        // Find number of spaces (i.e. " ") in tmp and makes prepad a string
        // containing only that many spaces
        def prepad = " ".multiply(tmp.split(" ").length - 1)
        int len = 0

        // Only output non-array variables or array members (i.e., skip array
        // definitions)
        if (isVariable(variableName, arraySize))
        {
            // In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            // Create the mnemonic definition
            def fullVariableName2 = prepad + fltCompName + fullVariableName
            len = prepad.length() + variableName.length()

            // Check if the data type is a structure
            if (itosEncode.equals(dataType))
            {
                nextColumnHeader = prepad + variableName
                lastSubStructureName = nextColumnHeader
                headerNames[row] = lastSubStructureName
                fullHeaderNames[row] = fullVariableName
                ccdd.writeToFileLn(pageFile, "(+, " + columnOffset + ", \"" + nextColumnHeader + "\")")
            }
            // Not a structure; it's a primitive type
            else
            {
                if (isArrayElement(arraySize))
                {
                    int maxDigits = (Integer) (Math.ceil(Math.log(Integer.parseInt(arraySize)) / Math.log(10)))

                    // Output number with leading spaces
                    def index = getIndex(variableName)
                    def indexPadded = index.toString().padLeft(maxDigits)

                    inMiddleOfArray = true
                    def arrayPad = 13

                    // This item is first on a row
                    if (Integer.parseInt(index) % modNum != 0)
                    {
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(=, +, \" :v" + itosFormat + ":\", raw)")
                        len = 0
                    }
                    // This array item is NOT first item on a row
                    else
                    {
                        def lastIndex = Math.min(Integer.parseInt(arraySize) - 1, Integer.parseInt(index) + modNum - 1).toString().padLeft(maxDigits)
                        def arrayMessage = prepad + "[" + indexPadded + "-" + lastIndex + "]"
                        len = arrayMessage.length() + (numITOSDigits + 1) * Math.min(modNum, Integer.parseInt(arraySize))
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + columnOffset + ", \"" + arrayMessage + "  :v" + itosFormat + ":\", raw)")
                    }

                    if (Integer.parseInt(index) != Integer.parseInt(arraySize) - 1 && (Integer.parseInt(index) + 1) % modNum != 0)
                    {
                        rowCount = rowCount - 1
                    }

                    if (Integer.parseInt(index) == Integer.parseInt(arraySize) - 1)
                    {
                        inMiddleOfArray = false
                        nextColumnHeader = lastSubStructureName
                    }
                }
                // Not an array item, print normally
                else
                {
                    ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + columnOffset + ", \"" + prepad + variableName + " :v" + itosFormat + ":\", raw)")

                    len = prepad.length() + variableName.length() + numITOSDigits + 2
                }
            }

            isOutput = true
        }
        else
        {
            nextColumnHeader = prepad + variableName + "[" + arraySize + "] - " + itosEncode
            len = nextColumnHeader.length()
            ccdd.writeToFileLn(pageFile, "array_fmt(+, " + columnOffset + ", \"" + nextColumnHeader + "\")")
        }

        if (maxColumnLength < len)
        {
            ccdd.writeToFileLn(pageFile, "## col_max_len is now = " + len + " (was " + maxColumnLength + ")")
            maxColumnLength = len
        }
    }
    else
    {
        ccdd.writeToFileLn(pageFile, "#### NOT printing " + variableName)
    }

    return isOutput
}

/*******************************************************************************
 * Output all of the mnemonic for display
 * 
 * @param pageFile
 *            reference to the output file
 * 
 * @param fltCompName
 *            flight computer name
 ******************************************************************************/
def outputMnemonics(pageFile, fltCompName)
{
    ccdd.writeToFileLn(pageFile, "# Mnemonics")

    // Step through each of the structure table rows
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Initialize the header name array values to blanks
        fullHeaderNames[row] = ""
        headerNames[row] = ""
    }

    // Step through each row in the table
    for (row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Output the mnemonic for this row in the data table
        def isOutput = outputMnemonic(pageFile, row, fltCompName)

        // Check if a mnemonic definition was output to the file
        if (isOutput)
        {
            // Add an end of line to file to get ready for next line
            ccdd.writeToFileLn(pageFile, "")
        }
    }
}

/*******************************************************************************
 * Output the page file
 * 
 * @param fltCompName
 *            flight computer name
 ******************************************************************************/
def outputPageFile(fltCompName)
{
    // Initialize the name, row, and column parameters
    nextColumnHeader = fltCompName + ccdd.getRootStructureTableNames()[0]
    lastSubStructureName = nextColumnHeader
    columnStep = 20
    maxColumnLength = columnStep
    columnOffset = -columnStep - 1
    maxNumRows = 46
    rowCount = maxNumRows
    columnCount = 0
    inMiddleOfArray = false

    // Check if structure data is provided
    if (numStructRows != 0)
    {
        // Build the page file name and open the page output file
        def baseName = "auto_" + fltCompName + ccdd.getRootStructureTableNames()[0]
        def pageFileName = ccdd.getOutputPath() + baseName + ".page"
        def pageFile = ccdd.openOutputFile(pageFileName)

        // Check if the page output file successfully opened
        if (pageFile != null)
        {
            // Begin building the page display. The "page" statement must be on
            // the first row
            ccdd.writeToFileLn(pageFile, "page " + baseName)
            ccdd.writeToFileLn(pageFile, "")
            outputFileCreationInfo(pageFile)
            ccdd.writeToFileLn(pageFile, "color default (orange, default)")
            ccdd.writeToFileLn(pageFile, "color mnedef (text (white, black) )")
            ccdd.writeToFileLn(pageFile, "color subpage (lightblue, blue)")
            ccdd.writeToFileLn(pageFile, "color array_fmt (royalblue, black)")
            ccdd.writeToFileLn(pageFile, "")

            // Output the telemetry display definitions
            outputMnemonics(pageFile, fltCompName)

            // Close the page output file
            ccdd.closeFile(pageFile)
        }
        // The page output file cannot be opened
        else
        {
            // Display an error dialog
            ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + pageFileName + "<b>'")
        }
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */

// Get the number of structure and command table rows
numStructRows = ccdd.getStructureTableNumRows()

fcNames = []
numFlightComputers = 0
nextColumnHeader = ""
lastSubStructureName = ""
columnStep = 0
maxColumnLength = 0
columnOffset = 0
maxNumRows = 0
rowCount = 0
columnCount = 0
headerNames = new String[numStructRows]
fullHeaderNames = new String[numStructRows]
inMiddleOfArray = false
numITOSDigits = 8
modNumDefault = 4
modNum = modNumDefault

// Check if no structure or command data is supplied
if (numStructRows == 0)
{
    ccdd.showErrorDialog("No structure or command data supplied to script " + ccdd.getScriptName())
}
// Structure and/or command data is supplied
else
{
    // Get the value of the data field specifying the flight computer base value
    def fcBase = ccdd.getGroupDataFieldValue("globals", "prefix")

    // Check if the data field exists or is empty
    if (fcBase == null || fcBase.isEmpty())
    {
        // Use the default base value
        fcBase = "FC"
    }

    // Get the value of the data field specifying the number of flight computers
    def numFC = ccdd.getGroupDataFieldValue("globals", "NumComputers")

    // Check if the data field exists, is empty, or isn't an integer value
    if (numFC == null || !numFC.matches("[0-9]+"))
    {
        // Use the default number of flight computers
        numFlightComputers = 1
    }
    // The value is an integer
    else
    {
        // Store the number of flight computers
        numFlightComputers = Integer.parseInt(numFC)
    }

    // Check if there is more than one flight computer
    if (numFlightComputers > 1)
    {
        // Step through each flight computer
        for (def fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
        {
            // Store the flight computer name prefix and offset value
            fcNames[fcIndex] = fcBase + (fcIndex + 1) + "_"
        }
    }
    // Only one flight computer
    else
    {
        // No prefix for a single computer
        fcNames = [""]
    }

    // Step through each flight computer
    for (def fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
    {
        // Output the page file
        outputPageFile(fcNames[fcIndex])
    }
}
