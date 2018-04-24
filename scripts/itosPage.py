# *****************************************************************************
# Description: Output an ITOS page file
# 
# This Python script generates an ITOS page file from the supplied telemetry
# information.
# 
# Assumptions: Arrays are limited to a single dimension
# 
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#*****************************************************************************/

from CCDD import CcddScriptDataAccessHandler
import re
import math

#  Functions ******************************************************************

# *****************************************************************************
# Output the file creation details to the specified file
# 
# @param file
#            reference to the output file
#*****************************************************************************/
def outputFileCreationInfo(file):
    # Add the build information and header to the output file
    ccdd.writeToFileLn(file, "# Created : " + ccdd.getDateAndTime() + "\n# User    : " + ccdd.getUser() + "\n# Project : " + ccdd.getProject() + "\n# Script  : " + ccdd.getScriptName())

    # Check if any table is associated with the script
    if ccdd.getTableNumRows() != 0:
        ccdd.writeToFileLn(file, "   Table(s): " + (",\n#            ").join(sorted(ccdd.getTableNames())))

    # Check if any groups is associated with the script
    if len(ccdd.getAssociatedGroupNames()) != 0:
        ccdd.writeToFileLn(file, "   Group(s): " + (",\n#            ").join(sorted(ccdd.getAssociatedGroupNames())))

    ccdd.writeToFileLn(file, "")

# *****************************************************************************
# Determine if the row containing the specified variable is not an array
# definition. A row in a table is an array definition if a value is present in
# the Array Size column but the variable name does not end with a ']'
# 
# @param variableName
#            variable name
# 
# @param arraySize
#            array size
# 
# @return true if the variable is not an array definition
#*****************************************************************************/
def isVariable(variableName, arraySize):
    # Only output non-array variables or array members (i.e., skip array
    # definitions)
    return variableName is not None and arraySize is not None and (arraySize == "" or variableName.endswith("]"))

# *****************************************************************************
# Convert an array member variable name by replacing left square brackets with
# underscores and removing right square brackets (example: a[2] becomes a_2)
# 
# @param variableName
#            variable name
# 
# @return Variable name with the square brackets replaced
#*****************************************************************************/
def convertArrayMember(variableName):
    return variableName.replace("[", "_").replace("]", "")

# *****************************************************************************
# Adjust the row counter to the next row. If the number of rows exceeds the
# maximum start a new column
#
# @param pageFile
#            reference to the output file
#
# @param variableName
#            variable name
#
# @param fullVariableName
#            variable name with structure path
#
# @param row
#            structure data row index
#*****************************************************************************/
def nextRow(pageFile, variableName, fullVariableName, row):
    global numStructRows
    global fcNames
    global numFlightComputers
    global nextColumnHeader
    global lastSubStructureName
    global columnStep
    global maxColumnLength
    global columnOffset
    global maxNumRows
    global rowCount
    global columnCount
    global headerNames
    global fullHeaderNames
    global inMiddleOfArray
    global numITOSDigits
    global modNumDefault
    global modNum

    # Check if the row counter is at the maximum
    if rowCount >= maxNumRows:
        # Step through the rows in the data that have been processed
        for row in range(row, -1, -1):
            # Get the header (structure) name for this row
            headerName = str(fullHeaderNames[row])
            
            # Check if the variable belongs to the same structure
            if headerName != "" and fullVariableName == headerName + "_" + variableName:
                # Update the column header
                lastSubStructureName = headerNames[row]

        # Go to the next column
        columnCount = columnCount + 1
        ccdd.writeToFileLn(pageFile, "## col_max_len going from " + str(maxColumnLength) + " back to " + str(columnStep))
        rowCount = 1
        columnOffset = columnOffset + maxColumnLength + 2
        maxColumnLength = columnStep

        # Check if the current variable is a member within an array
        if inMiddleOfArray:
            ccdd.writeToFileLn(pageFile, "array_fmt(1, " + str(columnOffset) + ",\"" + nextColumnHeader + "\")")
        # Not a variable within an array
        else:
            ccdd.writeToFileLn(pageFile, "(1, " + str(columnOffset) + ",\"" + lastSubStructureName + "\")")
    # Not at the maximum row
    else:
        rowCount = rowCount + 1

# *****************************************************************************
# Check if the supplied array size contains a value
# 
# @param array
#            size variable's array size value
# 
# @return true if the array size isn't empty
#*****************************************************************************/
def isArrayElement(arraySize):
    return arraySize is not None and arraySize != ""

# *****************************************************************************
# Get the array index value from the variable name
# 
# @param name
#            variable name
# 
# @return Array index value from the variable name
#*****************************************************************************/
def getIndex(name):
    # Split the variable name on the underscores and use the last part as the
    # array index
    parts = name.split("_")
    return parts[len(parts) - 1]

# *****************************************************************************
# Selects the format arguments to use for a particular ITOS type, based on its
# type
# 
# @param itosEncode
#            data type in ITOS encoded form
# 
# @return ITOS output format string
#*****************************************************************************/
def setITOSFormatFlag(itosEncode):
    global numStructRows
    global fcNames
    global numFlightComputers
    global nextColumnHeader
    global lastSubStructureName
    global columnStep
    global maxColumnLength
    global columnOffset
    global maxNumRows
    global rowCount
    global columnCount
    global headerNames
    global fullHeaderNames
    global inMiddleOfArray
    global numITOSDigits
    global modNumDefault
    global modNum

    itosFormat = ""
    withSign = 0
    modNum = modNumDefault
    dataTypeChar = itosEncode[0:1]
    
    # Floating point
    if dataTypeChar == "F":
        # Get the number of bytes that define the floating point
        numBytes = len(itosEncode) - 1

        # Check if the number of bytes is greater than 4 (i.e., it's a
        # double precision floating point)
        if numBytes > 4:
            # Set the format string and parameters for a double
            itosFormat = "%13.3f"
            numITOSDigits = 14
            modNum = modNumDefault / 2
        # The number of bytes is equal to or less than 4 (i.e., it's a
        # single precision floating point)
        else:
            # Set the format string and parameters for a float
            itosFormat = "%6.3f"
            numITOSDigits = 7
            modNum = modNumDefault / 2
    # Signed or unsigned integer
    elif dataTypeChar == "I" or dataTypeChar == "U":
        if dataTypeChar == "I":
            # Set the value to add a space for the sign
            withSign = 1

        # Get the number of bytes that define the (unsigned) integer
        numBytes = len(itosEncode) - 1

        # Determine the number of digits required to display the largest
        # possible value for the (unsigned) integer with the specified
        # number of bytes
        nDigits = int(2 * numBytes + 1 + math.floor(numBytes / 4))

        # Add a digit for a +/- if signed integer
        nDigits += withSign

        # Set the format string and parameters for a (unsigned) integer
        itosFormat = "%" + str(int(nDigits)) + "d"
        numITOSDigits = nDigits

        # Check if the number of bytes is greater than 2
        if numBytes > 2:
            # Set the format parameter
            modNum = modNumDefault / 2
    # Character or string
    elif dataTypeChar == "S":
        # Set the format string and parameters for a character or string
        itosFormat = "%s"
        numITOSDigits = 10
        modNum = modNumDefault / 2

    return itosFormat

# *****************************************************************************
# Output a single mnemonic definition
# 
# @param pageFile
#            reference to the output file
#
# @param row
#            row index in the structure data table
# 
# @param fltCompName
#            flight computer name
#
# @returns true is a mnemonic definition is output to the file
#*****************************************************************************/
def outputMnemonic(pageFile, row, fltCompName):
    global numStructRows
    global fcNames
    global numFlightComputers
    global nextColumnHeader
    global lastSubStructureName
    global columnStep
    global maxColumnLength
    global columnOffset
    global maxNumRows
    global rowCount
    global columnCount
    global headerNames
    global fullHeaderNames
    global inMiddleOfArray
    global numITOSDigits
    global modNumDefault
    global modNum

    isOutput = False
    itosFormat = ""

    variableName = ccdd.getStructureVariableName(row)
    dataType = ccdd.getStructureDataType(row)
 
    # Get the ITOS encoded form of the data type
    itosEncode = ccdd.getITOSEncodedDataType(dataType, "BIG_ENDIAN")

    # Check if this data type is a recognized base type or structure
    if itosEncode is not None:
        # Check if the data type is a primitive (not a structure)
        if itosEncode != dataType:
            # Get the ITOS output format string based on the encoding
            itosFormat = setITOSFormatFlag(itosEncode)

        # Get the variable name and array size
        arraySize = ccdd.getStructureArraySize(row)
        fullVariableName = ccdd.getFullVariableName(row)

        # See if this row would exceed the maximum. If so start another column
        nextRow(pageFile, variableName, fullVariableName, row)

        # Get the full variable name (including the variable's structure path)
        tmp = ccdd.getFullVariableName(row, " ")

        # Find number of spaces (i.e. " ") in tmp and makes prepad a string
        # containing only that many spaces
        prepad = " " * (len(tmp.split(" ")) - 1)
        lenAll = 0

        # Only output non-array variables or array members (i.e., skip array
        # definitions)
        if isVariable(variableName, arraySize):
            # In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            # Create the mnemonic definition
            fullVariableName2 = prepad + fltCompName + fullVariableName
            lenAll = len(prepad) + len(variableName)

            # Check if the data type is a structure
            if itosEncode == dataType:
                nextColumnHeader = prepad + variableName
                lastSubStructureName = nextColumnHeader
                headerNames[row] = lastSubStructureName
                fullHeaderNames[row] = fullVariableName
                ccdd.writeToFileLn(pageFile, "(+, " + str(columnOffset) + ", \"" + nextColumnHeader + "\")")
            # Not a structure; it's a primitive type
            else:
                if isArrayElement(arraySize):
                    maxDigits = int(math.ceil(math.log(int(arraySize)) / math.log(10)))

                    # Output number with leading spaces
                    index = getIndex(variableName)
                    indexPadded = str(index).ljust(maxDigits)

                    inMiddleOfArray = True
                    arrayPad = 13

                    # This item is first on a row
                    if int(index) % modNum != 0:
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(=, +, \" :v" + itosFormat + ":\", raw)")
                        lenAll = 0
                    # This array item is NOT first item on a row
                    else:
                        lastIndex = str(min(int(arraySize) - 1, int(index) + modNum - 1)).ljust(maxDigits)
                        arrayMessage = prepad + "[" + indexPadded + "-" + str(lastIndex) + "]"
                        lenAll = len(arrayMessage) + (numITOSDigits + 1) * min(modNum, int(arraySize))
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + str(columnOffset) + ", \"" + arrayMessage + "  :v" + itosFormat + ":\", raw)")

                    if int(index) != int(arraySize) - 1 and (int(index) + 1) % modNum != 0:
                        rowCount = rowCount - 1

                    if int(index) == int(arraySize) - 1:
                        inMiddleOfArray = False
                        nextColumnHeader = lastSubStructureName
                # Not an array item, print normally
                else:
                    ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + str(columnOffset) + ", \"" + prepad + variableName + " :v" + itosFormat + ":\", raw)")

                    lenAll = len(prepad) + len(variableName) + numITOSDigits + 2

            isOutput = True
        else:
            nextColumnHeader = prepad + variableName + "[" + arraySize + "] - " + itosEncode
            lenAll = len(nextColumnHeader)
            ccdd.writeToFileLn(pageFile, "array_fmt(+, " + str(columnOffset) + ", \"" + nextColumnHeader + "\")")

        if maxColumnLength < lenAll:
            ccdd.writeToFileLn(pageFile, "## col_max_len is now = " + str(lenAll) + " (was " + str(maxColumnLength) + ")")
            maxColumnLength = lenAll
    else:
        ccdd.writeToFileLn(pageFile, "#### NOT printing " + variableName)

    return isOutput

# *****************************************************************************
# Output all of the mnemonic for display
#
# @param pageFile
#            reference to the output file
#
# @param fltCompName
#            flight computer name
#*****************************************************************************/
def outputMnemonics(pageFile, fltCompName):
    global numStructRows
    global fcNames
    global numFlightComputers
    global nextColumnHeader
    global lastSubStructureName
    global columnStep
    global maxColumnLength
    global columnOffset
    global maxNumRows
    global rowCount
    global columnCount
    global headerNames
    global fullHeaderNames
    global inMiddleOfArray
    global numITOSDigits
    global modNumDefault
    global modNum
    
    ccdd.writeToFileLn(pageFile, "# Mnemonics")

    # Step through each of the structure table rows
    for row in range(ccdd.getStructureTableNumRows()):
        # Initialize the header name array values to blanks
        fullHeaderNames.append("")
        headerNames.append("")

    # Step through each row in the table
    for row in range(ccdd.getStructureTableNumRows()):
        # Output the mnemonic for this row in the data table
        isOutput = outputMnemonic(pageFile, row, fltCompName)
        
        # Check if a mnemonic definition was output to the file
        if isOutput:
            # Add an end of line to file to get ready for next line
            ccdd.writeToFileLn(pageFile, "")

# *****************************************************************************
# Output the page file
# 
# @param fltCompName
#            flight computer name
#*****************************************************************************/
def outputPageFile(fltCompName):
    global numStructRows
    global fcNames
    global numFlightComputers
    global nextColumnHeader
    global lastSubStructureName
    global columnStep
    global maxColumnLength
    global columnOffset
    global maxNumRows
    global rowCount
    global columnCount
    global headerNames
    global fullHeaderNames
    global inMiddleOfArray
    global numITOSDigits
    global modNumDefault
    global modNum

    # Initialize the name, row, and column parameters
    nextColumnHeader = fltCompName + ccdd.getRootStructureTableNames()[0]
    lastSubStructureName = nextColumnHeader
    columnStep = 20
    maxColumnLength = columnStep
    columnOffset = -columnStep - 1
    maxNumRows = 46
    rowCount = maxNumRows
    columnCount = 0
    inMiddleOfArray = False

    # Check if structure data is provided
    if numStructRows != 0:
        # Build the page file name and open the page output file
        baseName = "auto_" + fltCompName + ccdd.getRootStructureTableNames()[0]
        pageFileName = ccdd.getOutputPath() + baseName + ".page"
        pageFile = ccdd.openOutputFile(pageFileName)

        # Check if the page output file successfully opened
        if pageFile is not None:
            # Begin building the page display. The "page" statement must be on
            # the first row
            ccdd.writeToFileLn(pageFile, "page " + baseName)
            ccdd.writeToFileLn(pageFile, "")
            outputFileCreationInfo(pageFile)
            ccdd.writeToFileLn(pageFile, "color default (orange, default)")
            ccdd.writeToFileLn(pageFile, "color mnedef (text (white, black) )")
            ccdd.writeToFileLn(pageFile, "color subpage (lightblue, blue)")
            ccdd.writeToFileLn(pageFile, "color array_fmt (royalblue, black)")
            ccdd.writeToFileLn(pageFile, "")

            # Output the telemetry display definitions
            outputMnemonics(pageFile, fltCompName)

            # Close the page output file
            ccdd.closeFile(pageFile)
        # The page output file cannot be opened
        else:
            # Display an error dialog
            ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + pageFileName + "<b>'")

#  End functions **************************************************************

#  Main ***********************************************************************

# Get the number of structure and command table rows
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
headerNames = [numStructRows]
fullHeaderNames = [numStructRows]
inMiddleOfArray = False
numITOSDigits = 8
modNumDefault = 4
modNum = modNumDefault

# Check if no structure or command data is supplied
if numStructRows == 0:
    ccdd.showErrorDialog("No structure or command data supplied to script " + ccdd.getScriptName())
# Structure and/or command data is supplied
else:
    # Get the value of the data field specifying the flight computer base value
    fcBase = ccdd.getGroupDataFieldValue("globals", "prefix")

    # Check if the data field exists or is empty
    if fcBase is None or fcBase == "":
        # Use the default base value
        fcBase = "FC"

    # Get the value of the data field specifying the number of flight computers
    numFC = ccdd.getGroupDataFieldValue("globals", "NumComputers")

    # Check if the data field exists, is empty, or isn't an integer value
    if numFC is None or not re.match("[0-9]+", numFC):
        # Use the default number of flight computers
        numFlightComputers = 1
    # The value is an integer
    else:
        # Store the number of flight computers
        numFlightComputers = int(numFC)

    # Check if there is more than one flight computer
    if numFlightComputers > 1:
        # Step through each flight computer
        for fcIndex in range(numFlightComputers):
            # Store the flight computer name prefix and offset value
            fcNames.append(fcBase + str(fcIndex + 1) + "_")
    # Only one flight computer
    else:
        # No prefix for a single computer
        fcNames.append("")

    # Step through each flight computer
    for fcIndex in range(numFlightComputers):
        # Output the page file
        outputPageFile(fcNames[fcIndex])
