# *****************************************************************************
# Description: Output an ITOS page file
# 
# This Ruby script generates an ITOS page file from the supplied telemetry
# information.
# 
# Assumptions: Arrays are limited to a single dimension
# 
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#*****************************************************************************/

java_import Java::CCDD.CcddScriptDataAccessHandler

#  Functions ******************************************************************

# *****************************************************************************
# Output the file creation details to the specified file
# 
# @param file
#            reference to the output file
#*****************************************************************************/
def outputFileCreationInfo(file)
    # Add the build information and header to the output file
    $ccdd.writeToFileLn(file, "# Created : " + $ccdd.getDateAndTime() + "\n# User    : " + $ccdd.getUser() + "\n# Project : " + $ccdd.getProject() + "\n# Script  : " + $ccdd.getScriptName())

    # Check if any table is associated with the script
    if $ccdd.getTableNumRows() != 0
        $ccdd.writeToFileLn(file, "   Table(s): " + $ccdd.getTableNames().sort.to_a.join(",\n             "))
    end
  
    # Check if any groups is associated with the script
    if $ccdd.getAssociatedGroupNames().length != 0
        $ccdd.writeToFileLn(file, "   Group(s): " + $ccdd.getAssociatedGroupNames().sort.to_a.join(",\n             "))
    end
    
    $ccdd.writeToFileLn(file, "")
end

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
def isVariable(variableName, arraySize)
    # Only output non-array variables or array members (i.e., skip array
    # definitions)
return variableName != nil && arraySize != nil && (arraySize == "" || variableName.end_with?("]"))
end

# *****************************************************************************
# Convert an array member variable name by replacing left square brackets with
# underscores and removing right square brackets (example: a[2] becomes a_2)
# 
# @param variableName
#            variable name
# 
# @return Variable name with the square brackets replaced
#*****************************************************************************/
def convertArrayMember(variableName)
    return variableName.sub("[", "_").sub("]", "")
end

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
def nextRow(pageFile, variableName, fullVariableName, row)
    # Check if the row counter is at the maximum
    if $rowCount >= $maxNumRows
        # Step through the rows in the data that have been processed
        (0..row).reverse_each do |r|
            # Get the header (structure) name for this row
            headerName = $fullHeaderNames[r].to_s
            
            # Check if the variable belongs to the same structure
            if headerName != "" && fullVariableName == headerName + "_" + variableName
                # Update the column header
                $lastSubStructureName = $headerNames[r]
            end
        end
    
        # Go to the next column
        $columnCount = $columnCount + 1
        $ccdd.writeToFileLn(pageFile, "## col_max_len going from " + $maxColumnLength.to_s + " back to " + $columnStep.to_s)
        $rowCount = 1
        $columnOffset = $columnOffset + $maxColumnLength + 2
        $maxColumnLength = $columnStep

        # Check if the current variable is a member within an array
        if $inMiddleOfArray
            $ccdd.writeToFileLn(pageFile, "array_fmt(1, " + $columnOffset.to_s + ",\"" + $nextColumnHeader + "\")")
        # Not a variable within an array
        else
            $ccdd.writeToFileLn(pageFile, "(1, " + $columnOffset.to_s + ",\"" + $lastSubStructureName + "\")")
        end
    # Not at the maximum row
    else
        $rowCount = $rowCount + 1
    end
end

# *****************************************************************************
# Check if the supplied array size contains a value
# 
# @param array
#            size variable's array size value
# 
# @return true if the array size isn't empty
#*****************************************************************************/
def isArrayElement(arraySize)
    return arraySize != nil && arraySize != ""
end

# *****************************************************************************
# Get the array index value from the variable name
# 
# @param name
#            variable name
# 
# @return Array index value from the variable name
#*****************************************************************************/
def getIndex(name)
    # Split the variable name on the underscores and use the last part as the
    # array index
    parts = name.split("_")
    return parts[parts.length - 1]
end

# *****************************************************************************
# Selects the format arguments to use for a particular ITOS type, based on its
# type
# 
# @param itosEncode
#            data type in ITOS encoded form
# 
# @return ITOS output format string
#*****************************************************************************/
def setITOSFormatFlag(itosEncode)
    itosFormat = ""
    withSign = 0
    $modNum = $modNumDefault
    dataTypeChar = itosEncode[0..0]
        
    # Floating point
    if dataTypeChar == "F"
        # Get the number of bytes that define the floating point
        numBytes = itosEncode.length - 1

        # Check if the number of bytes is greater than 4 (i.e., it's a
        # double precision floating point)
        if numBytes > 4
            # Set the format string and parameters for a double
            itosFormat = "%13.3f"
            $numITOSDigits = 14
            $modNum = $modNumDefault / 2
        # The number of bytes is equal to or less than 4 (i.e., it's a
        # single precision floating point)
        else
            # Set the format string and parameters for a float
            itosFormat = "%6.3f"
            $numITOSDigits = 7
            $modNum = $modNumDefault / 2
        end
    # Signed or unsigned integer
    elsif dataTypeChar == "I" || dataTypeChar == "U"
        if dataTypeChar == "I"
            # Set the value to add a space for the sign
            withSign = 1
        end
        
        # Get the number of bytes that define the (unsigned) integer
        numBytes = itosEncode.length - 1

        # Determine the number of digits required to display the largest
        # possible value for the (unsigned) integer with the specified
        # number of bytes
        nDigits = (2 * numBytes + 1 + (numBytes / 4).floor).to_i

        # Add a digit for a +/- if signed integer
        nDigits += withSign

        # Set the format string and parameters for a (unsigned) integer
        itosFormat = "%" + nDigits.to_i.to_s + "d"
        $numITOSDigits = nDigits

        # Check if the number of bytes is greater than 2
        if numBytes > 2
            # Set the format parameter
            $modNum = $modNumDefault / 2
        end
    # Character or string
    elsif dataTypeChar == "S"
        # Set the format string and parameters for a character or string
        itosFormat = "%s"
        $numITOSDigits = 10
        $modNum = $modNumDefault / 2
    end
    
    return itosFormat
end

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
def outputMnemonic(pageFile, row, fltCompName)
    isOutput = false
    itosFormat = ""

    variableName = $ccdd.getStructureVariableName(row)
    dataType = $ccdd.getStructureDataType(row)
 
    # Get the ITOS encoded form of the data type
    itosEncode = $ccdd.getITOSEncodedDataType(dataType, "BIG_ENDIAN")

    # Check if this data type is a recognized base type or structure
    if itosEncode != nil
        # Check if the data type is a primitive (not a structure)
        if itosEncode != dataType
            # Get the ITOS output format string based on the encoding
            itosFormat = setITOSFormatFlag(itosEncode)
        end
        
        # Get the variable name and array size
        arraySize = $ccdd.getStructureArraySize(row)
        fullVariableName = $ccdd.getFullVariableName(row)

        # See if this row would exceed the maximum. If so start another column
        nextRow(pageFile, variableName, fullVariableName, row)

        # Get the full variable name (including the variable's structure path)
        tmp = $ccdd.getFullVariableName(row, " ")

        # Find number of spaces (i.e. " ") in tmp and makes prepad a string
        # containing only that many spaces
        prepad = " " * (tmp.split(" ").length - 1)
        lenAll = 0

        # Only output non-array variables or array members (i.e., skip array
        # definitions)
        if isVariable(variableName, arraySize)
            # In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            # Create the mnemonic definition
            fullVariableName2 = prepad + fltCompName + fullVariableName
            lenAll = prepad.length + variableName.length

            # Check if the data type is a structure
            if itosEncode == dataType
                $nextColumnHeader = prepad + variableName
                $lastSubStructureName = $nextColumnHeader
                $headerNames[row] = $lastSubStructureName
                $fullHeaderNames[row] = fullVariableName
                $ccdd.writeToFileLn(pageFile, "(+, " + $columnOffset.to_s + ", \"" + $nextColumnHeader + "\")")
            # Not a structure; it's a primitive type
            else
                if isArrayElement(arraySize)
                    maxDigits = ((Math.log(arraySize.to_i).ceil / Math.log(10))).to_i

                    # Output number with leading spaces
                    index = getIndex(variableName)
                    indexPadded = index.to_s.ljust(maxDigits)

                    $inMiddleOfArray = true
                    arrayPad = 13

                    # This item is first on a row
                    if index.to_i % $modNum != 0
                        $ccdd.writeToFileLn(pageFile, fullVariableName2 + "(=, +, \" :v" + itosFormat + ":\", raw)")
                        lenAll = 0
                    # This array item is NOT first item on a row
                    else
                        lastIndex = ([arraySize.to_i - 1, index.to_i + $modNum - 1].min).to_s.ljust(maxDigits)
                        arrayMessage = prepad + "[" + indexPadded + "-" + lastIndex.to_s + "]"
                        lenAll = arrayMessage.length + ($numITOSDigits + 1) * ([$modNum, arraySize.to_i].min)
                        $ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + $columnOffset.to_s + ", \"" + arrayMessage + "  :v" + itosFormat + ":\", raw)")
                    end
                    
                    if index.to_i != arraySize.to_i - 1 && (index.to_i + 1) % $modNum != 0
                        $rowCount = $rowCount - 1
                    end
                    
                    if index.to_i == arraySize.to_i - 1
                        $inMiddleOfArray = false
                        $nextColumnHeader = $lastSubStructureName
                    end
                # Not an array item, print normally
                else
                    $ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+, " + $columnOffset.to_s + ", \"" + prepad + variableName + " :v" + itosFormat + ":\", raw)")

                    lenAll = prepad.length + variableName.length + $numITOSDigits + 2
                end
            end
    
            isOutput = true
        else
            $nextColumnHeader = prepad + variableName + "[" + arraySize + "] - " + itosEncode
            lenAll = $nextColumnHeader.length
            $ccdd.writeToFileLn(pageFile, "array_fmt(+, " + $columnOffset.to_s + ", \"" + $nextColumnHeader + "\")")
        end

        if $maxColumnLength < lenAll
            $ccdd.writeToFileLn(pageFile, "## col_max_len is now = " + lenAll.to_s + " (was " + $maxColumnLength.to_s + ")")
            $maxColumnLength = lenAll
        end
    else
        $ccdd.writeToFileLn(pageFile, "#### NOT printing " + variableName)
    end

    return isOutput
end

# *****************************************************************************
# Output all of the mnemonic for display
#
# @param pageFile
#            reference to the output file
#
# @param fltCompName
#            flight computer name
#*****************************************************************************/
def outputMnemonics(pageFile, fltCompName)
    $ccdd.writeToFileLn(pageFile, "# Mnemonics")

    # Step through each of the structure table rows
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Initialize the header name array values to blanks
        $fullHeaderNames.push("")
        $headerNames.push("")
    end
    
    # Step through each row in the table
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Output the mnemonic for this row in the data table
        isOutput = outputMnemonic(pageFile, row, fltCompName)
        
        # Check if a mnemonic definition was output to the file
        if isOutput
            # Add an end of line to file to get ready for next line
            $ccdd.writeToFileLn(pageFile, "")
        end
    end
end

# *****************************************************************************
# Output the page file
# 
# @param fltCompName
#            flight computer name
#*****************************************************************************/
def outputPageFile(fltCompName)
    # Initialize the name, row, and column parameters
    $nextColumnHeader = fltCompName + $ccdd.getRootStructureTableNames()[0]
    $lastSubStructureName = $nextColumnHeader
    $columnStep = 20
    $maxColumnLength = $columnStep
    $columnOffset = -$columnStep - 1
    $maxNumRows = 46
    $rowCount = $maxNumRows
    $columnCount = 0
    $inMiddleOfArray = false

    # Check if structure data is provided
    if $numStructRows != 0
        # Build the page file name and open the page output file
        baseName = "auto_" + fltCompName + $ccdd.getRootStructureTableNames()[0]
        pageFileName = $ccdd.getOutputPath() + baseName + ".page"
        pageFile = $ccdd.openOutputFile(pageFileName)

        # Check if the page output file successfully opened
        if pageFile != nil
            # Begin building the page display. The "page" statement must be on
            # the first row
            $ccdd.writeToFileLn(pageFile, "page " + baseName)
            $ccdd.writeToFileLn(pageFile, "")
            outputFileCreationInfo(pageFile)
            $ccdd.writeToFileLn(pageFile, "color default (orange, default)")
            $ccdd.writeToFileLn(pageFile, "color mnedef (text (white, black) )")
            $ccdd.writeToFileLn(pageFile, "color subpage (lightblue, blue)")
            $ccdd.writeToFileLn(pageFile, "color array_fmt (royalblue, black)")
            $ccdd.writeToFileLn(pageFile, "")

            # Output the telemetry display definitions
            outputMnemonics(pageFile, fltCompName)

            # Close the page output file
            $ccdd.closeFile(pageFile)
        # The page output file cannot be opened
        else
            # Display an error dialog
            $ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + pageFileName + "<b>'")
        end
    end
end

#  End functions **************************************************************

#  Main ***********************************************************************

# Get the number of structure and command table rows
$numStructRows = $ccdd.getStructureTableNumRows()

$fcNames = []
$numFlightComputers = 0
$nextColumnHeader = ""
$lastSubStructureName = ""
$columnStep = 0
$maxColumnLength = 0
$columnOffset = 0
$maxNumRows = 0
$rowCount = 0
$columnCount = 0
$headerNames = [$numStructRows]
$fullHeaderNames = [$numStructRows]
$inMiddleOfArray = false
$numITOSDigits = 8
$modNumDefault = 4
$modNum = $modNumDefault

# Check if no structure or command data is supplied
if $numStructRows == 0
    $ccdd.showErrorDialog("No structure or command data supplied to script " + $ccdd.getScriptName())
# Structure and/or command data is supplied
else
    # Get the value of the data field specifying the flight computer base value
    fcBase = $ccdd.getGroupDataFieldValue("globals", "prefix")

    # Check if the data field exists or is empty
    if fcBase == nil || fcBase == ""
        # Use the default base value
        fcBase = "FC"
    end
    
    # Get the value of the data field specifying the number of flight computers
    numFC = $ccdd.getGroupDataFieldValue("globals", "NumComputers")

    # Check if the data field exists, is empty, or isn't an integer value
    if numFC == nil || !(numFC =~ /[0-9]+/)
        # Use the default number of flight computers
        $numFlightComputers = 1
    # The value is an integer
    else
        # Store the number of flight computers
        $numFlightComputers = numFC.to_i
    end
    
    # Check if there is more than one flight computer
    if $numFlightComputers > 1
        # Step through each flight computer
        for fcIndex in 0..$numFlightComputers - 1
            # Store the flight computer name prefix and offset value
            $fcNames.push(fcBase + (fcIndex + 1).to_s + "_")
        end
    # Only one flight computer
    else
        # No prefix for a single computer
        $fcNames.push("")
    end
    
    # Step through each flight computer
    for fcIndex in 0..$numFlightComputers - 1
        # Output the page file
        outputPageFile($fcNames[fcIndex])
    end
end