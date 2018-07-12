#******************************************************************************
# Description: Output an ITOS record file
#
# This Ruby script generates an ITOS record file from the supplied
# telemetry and command information
#
# Assumptions: If the structure has a non-empty data field named "Message ID"
# then it is assumed to require a CCSDS header which is automatically added. If
# a table containing extra text to include is provided then its table type is
# "Includes" and has the column "Includes". The output file names are prepended
# with a name taken from a data field, "System", found either in the first
# group associated with the script, or, if not found there then in the first
# structure table associated with the script; if no "System" data field exists
# or is empty the name is blank. The project's data type definitions are output
# to the types header file
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

java_import Java::CCDD.CcddScriptDataAccessHandler

#* Functions ******************************************************************

#******************************************************************************
# Output the file creation details to the specified file
#
# @param file
#            reference to the output file
#******************************************************************************
def outputFileCreationInfo(file)
  # Add the build information and header to the output file
  $ccdd.writeToFileLn(file, "/* Created : " + $ccdd.getDateAndTime() + "\n   User    : " + $ccdd.getUser() + "\n   Project : " + $ccdd.getProject() + "\n   Script  : " + $ccdd.getScriptName())

  # Check if any table is associated with the script
  if $ccdd.getTableNumRows() != 0
      $ccdd.writeToFileLn(file, "   Table(s): " + $ccdd.getTableNames().sort.to_a.join(",\n             "))
  end

  # Check if any groups is associated with the script
  if $ccdd.getAssociatedGroupNames().length != 0
      $ccdd.writeToFileLn(file, "   Group(s): " + $ccdd.getAssociatedGroupNames().sort.to_a.join(",\n             "))
  end

  $ccdd.writeToFileLn(file, "*/")
end

#******************************************************************************
# Determine if the row containing the specified variable is not an array
# definition. A row in a table is an array definition if a value is present in
# the array size column but the variable name does not end with a ']'
#
# @param variableName
#            variable name
#
# @param arraySize
#            array size
#
# @return true if the variable is not an array definition
#******************************************************************************
def isVariable(variableName, arraySize)
    return variableName != nil && arraySize != nil && (arraySize.empty? || variableName.end_with?("]"))
end

#******************************************************************************
# Check if the variable on the specified row in the structure data has at least
# one non-blank rate value
#
# @param row
#            row index in the structure data table
#
# @return true if the variable in the specified row has at least one non-blank
#         rate value
#******************************************************************************
def isTelemetry(row)
    isTlm = false

    # Get the rate column values for all rate columns in the structure
    rates = $ccdd.getStructureRates(row)

    # Step through each rate column value
    for index in 0..rates.length - 1
        # Check if a rate value is present in the column
        if !rates[index].empty?
            # Set the flag to indicate the variable is telemetered and stop
            # searching
            isTlm = true
            break
        end
    end

    return isTlm
end

#******************************************************************************
# Build the command enumeration name
#
# @param row
#            row index in the command data table
#
# @param argumentNum
#            command argument number
#
# @return Command enumeration name
#******************************************************************************
def getCommandEnumerationName(row, argumentNum)
    return $ccdd.getCommandName(row) + "_" + $ccdd.getCommandArgName(argumentNum, row) + "_ENUMERATION"
end

#******************************************************************************
# Output an array of structure row indices that order the bit-packed variables
# in the structure table based on endianess
#
# @param endian
#            "BE" (big endian) or "LE" (little endian), depending on what byte
#            order is desired
#
# @return Array of structure row indices with the bit-packed variables ordered
#         in the structure table based on endianess
#******************************************************************************
def reorderRowsForByteOrder(endian)
    reOrdered = []

    # Create the reordered row array assuming the order is unchanged
    for row in 0..$numStructRows - 1
        # Default to ID=index
        reOrdered.push(row)
    end

    # Check if the order is little endian (there's no need to perform the
    # reordering if big endian)
    if endian == "LE"
        # Step through each structure row
        for tgtRow in 0..$numStructRows - 1
            # Counter to track the number of variables bit-packed with the
            # target variable
            packCount = 1

            # Get the name of the target structure
            tgtStructName = $ccdd.getStructureTableNameByRow(tgtRow)

            # Get the byte offset of the target variable
            tgtVarPath = $ccdd.getFullVariableNameRaw(tgtRow)
            tgtOffset = $ccdd.getVariableOffset(tgtVarPath)

            # Step through the remaining rows so that the structures can be
            # compared with the target
            for compRow in row + 1..$numStructRows - 1
                # Check if the target structure is the same as the comparison
                # structure
                if tgtStructName == $ccdd.getStructureTableNameByRow(compRow)
                    # Get the byte offset of the comparison variable
                    compVarPath = $ccdd.getFullVariableNameRaw(compRow)
                    compOffset = $ccdd.getVariableOffset(compVarPath)

                    # Check if the target and comparison variables have the
                    # same offset; i.e., they are bit-packed
                    if tgtOffset == compOffset
                        # Increment the bit-packed variables counter
                        packCount = packCount + 1
                    # The variables aren't bit-packed
                    else
                        # Stop searching since no further variables are packed
                        # with the target
                        break
                    end
                # The structures aren't the same
                else
                    # Stop searching since the all of the target structure has
                    # been checked
                    break
                end
            end

            # Check if the target variable is bit-packed with one or more
            # variables
            if packCount > 1
                # Step through the bit-packed variables
                for index in 0..packCount - 1
                    # Store the bit-packed variable's row, in reverse order of
                    # its original appearance, in the re-ordered row array
                    reOrdered[tgtRow + index] = tgtRow + packCount - index - 1
                end

                # Adjust the target row index to skip the bit-packed variables
                # so that they won't be checked again as the loop progresses
                tgtRow = tgtRow + packCount - 1
            end
        end
    end

    return reOrdered
end

#******************************************************************************
# Output a telemetry packet or prototype structure definition
#
# @param structureName
#            structure name
#
# @param isPacket
#            true if this is a telemetry packet definition; false for a
#            prototype structure definition
#
# @param outFile
#            output file
#******************************************************************************
def outputStructureDefinition(structureName, isPacket, outFile)
    termLine = false
    usedVariableNames = []

    # Step through each row in the table
    for rowIndex in 0..$numStructRows - 1
        # Get the row index when swapped for LE bit fields
        row = $newRowOrder[rowIndex]

        # Check that this row references a variable in the prototype structure
        if $ccdd.getStructureTableNameByRow(row) == structureName
            # Get the variable name for this row
            variableName = $ccdd.getStructureVariableName(row)

            isFound = false

            # Step through each name in the array of already processed
            # variable names
            for index in 0..usedVariableNames.length - 1
                # Check if the target name matches the array name
                if usedVariableNames[index] == variableName
                    # Match found; set the flag and stop searching
                    isFound = true
                    break
                end
            end

            # Check if the variable name hasn't already been processed; this is
            # necessary to prevent duplicating the variables in the prototype
            # structure for a structure that is referenced as an array
            if !isFound
                # Add the variable name to the list of those already processed
                usedVariableNames.push(variableName)

                # Get the array size for this row
                arraySize = $ccdd.getStructureArraySize(row)

                # Only output non-array variables or array members (i.e., skip
                # array definitions)
                if isVariable(variableName, arraySize)
                    skipStringMembers = false

                    # Get the variable's data type
                    dataType = $ccdd.getStructureDataType(row)

                    # Check if the variable is a string; a string is handled as
                    # a single entity rather than an array of characters
                    if arraySize != nil && !arraySize.empty? && dataType == "string"
                        # Check if this is the first character in the string
                        if variableName.end_with?("_0")
                            # Remove the array size from the variable name
                            variableName = variableName.substring(0, variableName.length - 2)

                            # Add the string length information
                            otherParameters += "lengthInCharacters = " + arraySize + " , "
                        # This is a character other than the first one in the
                        # string
                        else
                            # Set the flag to skip the remaining string members
                            skipStringMembers = true
                        end
                    end

                    # Check that this isn't a member of a string (other than
                    # the first one)
                    if !skipStringMembers
                        # In case this is an array member replace the square brackets. This also
                        # prevents returning a duplicate name due to the conversion (e.g.,
                        # abc_0 and abc[0] would otherwise be converted to the same name, abc_0,
                        # if the brackets are simply replaced)
                        variablePath = $ccdd.getFullVariableName(rowIndex, ",")
                        varIndex = variablePath.rindex(",") + 1
                        variableName = variablePath[varIndex..-1]

                        # Check if this is not the first pass
                        if termLine
                            # Check if this is the packet definition
                            if isPacket
                                # Terminate the previous line with a comma
                                $ccdd.writeToFileLn(outFile, ",")
                            # This is a prototype structure
                            else
                                # Terminate the previous line with a line feed
                                $ccdd.writeToFileLn(outFile, "")
                            end
                        end

                        termLine = true

                        # Get the length in bits for this row
                        bitLength = $ccdd.getStructureBitLength(row)
                        otherParameters = ""

                        # Check if the length in bits is specified
                        if bitLength != nil && !bitLength.empty?
                            # Add the length in bits parameter
                            otherParameters = "lengthInBits=" + bitLength
                        end

                        # Get the ITOS encoded form of the data type as two
                        # characters (type + size)
                        itosEncode2Char = $ccdd.getITOSEncodedDataType(dataType, "TWO_CHAR")

                        # Check if variable is a primitive data type or a
                        # structure
                        if itosEncode2Char != nil
                            # Check if other parameters have been defined
                            if (!otherParameters.empty?)
                                # Add a space to separate the parameters
                                otherParameters += " "
                            end

                            # Check if the data type is a recognized primitive
                            if itosEncode2Char != dataType
                                # Add the 'no mnemonic' parameter
                                otherParameters += "generateMnemonic=\"no\""
                            end
                        end

                        # Create the parameter definition
                        $ccdd.writeToFile(outFile, "  " + itosEncode2Char + " " + variableName + " {" + otherParameters + "}")
                    end
                end
            end
        end
    end
end

#******************************************************************************
# Get the last 12 bits of a hexadecimal message ID
#
# @param msgID
#            message ID
#
# @return Last 12 bits of a message ID formatted as a 4 digit hex number (i.e.
#         0x031f); '0x0000' if the message ID isn't an integer or hexadecimal
#         value
#******************************************************************************
def extractMessageID(msgID)
    retVal = "0000"

    # Check if the ID is a hexadecimal number
    if msgID =~ /(0x)?[0-9a-fA-F]+/
        # Convert the message ID from a string to a value
        val = msgID.to_i(16)

        # Strip off all but the last 12 bits and convert the value to a
        # hexadecimal string
        retVal = "%04x" % (val & 0x7ff)
    end

    return "0x" + retVal
end

#******************************************************************************
# Get the last 12 bits of a hexadecimal message ID
#
# @param msgID
#            message ID
#
# @return Last 12 bits of a message ID formatted as a 3 digit hex number (i.e.
#         0x31f); '0x000' if the message ID isn't an integer or hexadecimal
#         value
#******************************************************************************
def extractCommandID(msgID)
    retVal = "000"

    # Check if the ID is a hexadecimal number
    if msgID =~ /(0x)?[0-9a-fA-F]+/
        # Convert the message ID from a string to a value
        val = msgID.to_i(16)

        # Strip off all but the last 12 bits and convert the value to a
        # hexadecimal string
        retVal = "%03x" % (val & 0x7ff)
    end

    return "0x" + retVal
end

#******************************************************************************
# Output a telemetry packet definition
#
# @param prefix
#            structure name prefix; used to differentiate the same structure
#            when defined for multiple flight computers
#
# @param structureName
#            structure name
#
# @param msgID
#            message ID (hexadecimal)
#
# @param msgIDOffset
#            message ID offset (hexadecimal)
#******************************************************************************
def outputTelemetryPacket(prefix, structureName, msgID, msgIDOffset)
    # Add the offset to the message ID
    msgIDWithOffset = msgIDOffset.sub( "0x", "").to_i(16) + msgID.sub( "0x", "").to_i(16)

    # Output the packet definition
    $ccdd.writeToFileLn($tlmFile, "\nCfeTelemetryPacket " + prefix + structureName)
    $ccdd.writeToFileLn($tlmFile, "{")
    $ccdd.writeToFileLn($tlmFile, "  applyWhen={FieldInRange{field = applicationId, range = " + extractMessageID("%x" % msgIDWithOffset) + "}},")
    outputStructureDefinition(structureName, true, $tlmFile)
end

#******************************************************************************
# Output the telemetry structure prototype and packet definitions
#
# @param structureNames
#            array of all structure table names, sorted by order of reference
#******************************************************************************
def outputStructures(structureNames)
    # Step through each structure name
    for structIndex in 0..structureNames.length - 1
        # Get the structure name to make subsequent calls shorter
        structureName = structureNames[structIndex]

        # Get the value of the structure's message ID data field (if present)
        msgID = $ccdd.getTableDataFieldValue(structureName, "Message ID")

        # Check if the structure doesn't have a message ID
        if msgID == nil || msgID.empty?
            # Check if the structure is referenced by more than one structure
            if $ccdd.isStructureShared(structureName)
                # Output the structure prototype to the combined recs file
                $ccdd.writeToFileLn($combFile, "\nprototype Structure " + structureName)
                $ccdd.writeToFileLn($combFile, "{")
                outputStructureDefinition(structureName, false, $combFile)
                $ccdd.writeToFileLn($combFile, "\n}")
            # The structure isn't referenced by multiple structures
            else
                # Output the structure prototype to the rec file
                $ccdd.writeToFileLn($tlmFile, "\nprototype Structure " + structureName)
                $ccdd.writeToFileLn($tlmFile, "{")
                outputStructureDefinition(structureName, false, $tlmFile)
                $ccdd.writeToFileLn($tlmFile, "\n}")
            end
        # The structure has a message ID
        else
            # Check if there is more than one flight computer
            if $numFlightComputers > 1
                # Step through each flight computer
                for fcIndex in 0..$numFlightComputers - 1
                    # Output the telemetry packet definition
                    outputTelemetryPacket($fcNames[fcIndex], structureName, msgID, $fcOffset[fcIndex])
                    $ccdd.writeToFileLn($tlmFile, "\n}")
                end
            # There is a single flight computer
            else
                # Output the telemetry packet definition
                outputTelemetryPacket("", structureName, msgID, "0")
                $ccdd.writeToFileLn($tlmFile, "\n}")
            end
        end
    end
end

#******************************************************************************
# Output the commands
#
# @param prefix
#            command name prefix
#
# @param msgIDOffset
#            message ID offset
#
# @param system
#            system name
#******************************************************************************
def outputCommands(prefix, msgIDOffset, system)
    # Step through each row in the command table
    for row in 0..$numCommandRows - 1
        # Get the system with which he command is associated from the command
        # table's 'System' data field
        commandSystem = $ccdd.getTableDataFieldValue($ccdd.getCommandTableNameByRow(row), "System")

        # Check if the this command table's system matches the target system
        if system == nil || ( commandSystem != nil && system == commandSystem)
            # Get the command name and code, and the message ID for the command
            # table
            commandName = $ccdd.getCommandName(row)
            cmdCode = $ccdd.getCommandCode(row)
            msgID = $ccdd.getTableDataFieldValue($ccdd.getCommandTableNameByRow(row), "Message ID")
            msgIDWithOffset = msgIDOffset.sub("0x", "").to_i(16) + msgID.sub("0x", "").to_i(16)

            # Begin the command definition
            $ccdd.writeToFileLn($cmdFile, "")
            $ccdd.writeToFileLn($cmdFile, "CfeSoftwareCommand " + prefix + commandName)
            $ccdd.writeToFileLn($cmdFile, "{")
            $ccdd.writeToFileLn($cmdFile, "  applicationId {range=" + extractCommandID("%x" % msgIDWithOffset) + "}")
            $ccdd.writeToFileLn($cmdFile, "  commandCode {range=" + (cmdCode.sub("0x", "").to_i(16)).to_s + "}")

            # Process all of the command arguments for this command
            for argumentNum in 0..$ccdd.getNumCommandArguments(row) - 1
                # Get the command argument's name, data type, and array size
                name = $ccdd.getCommandArgName(argumentNum, row)
                dataType = $ccdd.getCommandArgDataType(argumentNum, row)

                # Get the size in bytes based on the data type
                sizeInBytes = $ccdd.getDataTypeSizeInBytes(dataType);

                # Check if the parameter has an argument
                if name != nil && !name.empty? && dataType != nil && !dataType.empty?
                    argumentInfo = ""

                    # Get the single character ITOS encoded form of the data
                    # type
                    itosEncode1Char = $ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR")

                    # Check if the parameter is an integer (signed or unsigned)
                    if itosEncode1Char == "I" || itosEncode1Char == "U"
                        # Get the command argument's enumeration value
                        enumeration = $ccdd.getCommandArgEnumeration(argumentNum, row)

                        # Check if this command has an enumeration
                        if enumeration != nil && !enumeration.empty?
                            # Add the associated enumeration definition
                            argumentInfo += "enumeration = " + getCommandEnumerationName(row, argumentNum) + ", "
                        end

                        # Check that the argument has a valid data type
                        if sizeInBytes != 0
                            # Get the command argument's minimum and maximum
                            # values
                            minimumValue = $ccdd.getCommandArgMinimum(argumentNum, row)
                            maximumValue = $ccdd.getCommandArgMaximum(argumentNum, row)

                            # Check if a minimum value doesn't exist for this
                            # argument
                            if minimumValue == nil || minimumValue.empty?
                                # Set the minimum value to zero, assuming this
                                # is an unsigned integer
                                minimumValue = 0

                                # Check if the argument is a signed integer
                                if itosEncode1Char == "I"
                                    # Set the minimum value to the largest
                                    # negative value for this size integer
                                    minimumValue = -(2 ** (sizeInBytes * 8)) / 2
                                end
                            end

                            # Check if a maximum value doesn't exist for this
                            # argument
                            if maximumValue == nil || maximumValue.empty?
                                # Set the maximum value to the largest positive
                                # value for an unsigned integer
                                maximumValue = (2 ** (sizeInBytes * 8)) - 1

                                # Check if the argument is a signed integer
                                if itosEncode1Char == "I"
                                    # Adjust the maximum to the largest size
                                    # for this size integer
                                    maximumValue -= maximumValue / 2 + 1
                                end
                            end

                            # Add the command argument range
                            argumentInfo += "range=" + minimumValue.to_s + ".." + maximumValue.to_s
                        end
                    # Check if the parameter is a string
                   elsif itosEncode1Char == "S"
                        # Get the command argument's array size value
                        arraySize = $ccdd.getCommandArgArraySize(argumentNum, row)

                        # Check if there is no array size provided
                        if arraySize == nil || arraySize.empty?
                            # Default to a single character
                            arraySize = "1"
                        # The array size exists
                        else
                            # Strip off all but the last array index - this is
                            # the string's length
                            arraySize = (arraySize + "").gsub("/.*, ", "")
                        end

                        # Set the 'lengthInCharacters' argument to capture the
                        # string's length
                        sizeInBytes = 1
                        argumentInfo = "lengthInCharacters = " + arraySize
                    end

                    # Output the command argument to the file
                    $ccdd.writeToFileLn($cmdFile, "  " + itosEncode1Char + sizeInBytes.to_s + " " + name + " {" + argumentInfo + "}")
                end
            end

            $ccdd.writeToFileLn($cmdFile, "}")
        end
    end
end

#******************************************************************************
# Output a single mnemonic definition
#
# @param row
#            row index in the structure data table
#******************************************************************************
def outputMnemonicDefinition(row)
    # Get the variable data type
    dataType = $ccdd.getStructureDataType(row)

     # Get the single character ITOS encoded form of the data type
    itosEncode = $ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR")

    # Check if this data type is a recognized base type, and not a structure
    if itosEncode != nil && itosEncode != dataType
        # Check if the encoding is 'raw' (unrecognized)
        if itosEncode == "R"
            # Default to "unsigned"
            itosEncode = "U"
        end

        # Get the variable name and array size
        variableName = $ccdd.getStructureVariableName(row)
        arraySize = $ccdd.getStructureArraySize(row)

        # Check if the variable is not an array definition
        isVar = isVariable(variableName, arraySize)

        # Check if the variable is a string
        isString = itosEncode == "S" && arraySize

        # Set the output flag if this is a non-string variable
        isOutputMnemonic = isVar && !isString

        # Check if this is a string definition
        if isString && !isVar
            # Set the flag if the string is not telemetered (if the first
            # member has no non-blank rate)
            isOutputMnemonic = !isTelemetry(row + 1)
        end

        # Only output non-array variables or array members (i.e., skip array
        # definitions)
        if isOutputMnemonic
            structurePath = $ccdd.getFullVariableName(row, ".")

            # Get the full variable name for this variable, which includes all
            # of the variable names in its structure path
            fullVariableName = $ccdd.getFullVariableName(row)

            enumeration = nil

            # Get the enumeration(s)
            enumerations = $ccdd.getStructureEnumerations(row)

            # Check if any enumeration exists
            if enumerations != nil && enumerations.length != 0
                # Store the first enumeration
                enumeration = enumerations[0]
            end

            # Get the polynomial conversion and limit sets columns (if extant)
            polynomial = $ccdd.getStructureTableData("polynomial coefficients", row)
            limitSet = $ccdd.getStructureTableData("limit sets", row)

            # Step through each flight computer
            for fcIndex in 0..$numFlightComputers - 1
                # Output the mnemonic
                $ccdd.writeToFile($tlmFile, itosEncode + " " + $fcNames[fcIndex] + fullVariableName + " {sourceFields = {" + $fcNames[fcIndex] + structurePath + "}")

                isConversion = false
                isMultiple = false

                # Check if the parameter includes an enumeration
                if enumeration != nil && !enumeration.empty?
                    isConversion = true
                    isMultiple = false
                end

                # Check if this parameter includes a discrete or polynomial
                # conversion
                if polynomial != nil && !polynomial.empty?
                    isConversion = true
                    isMultiple = polynomial.split("\\;").length > 1
                end

                # Check if there is an enumeration or polynomial conversion
                if isConversion
                    # Check if there are conversions specific to each flight
                    # computer
                    if isMultiple
                        # Output the flight computer-specific conversion
                        # reference
                        $ccdd.writeToFile($tlmFile, " conversion = " + $fcNames[fcIndex] + fullVariableName + "_CONVERSION")
                    # There is only a single conversion
                    else
                        # Output the conversion reference
                        $ccdd.writeToFile($tlmFile, " conversion = " + fullVariableName + "_CONVERSION")
                    end
                end

                # Check if this parameter includes a limit or limit set
                if limitSet != nil && !limitSet.empty?
                    # Output the limit reference
                    $ccdd.writeToFile($tlmFile, " limits = " + fullVariableName + "_LIMIT")
                end

                $ccdd.writeToFileLn($tlmFile, "}")
            end
        end
    end
end

#******************************************************************************
# Output all of the mnemonic definitions
#******************************************************************************
def outputMnemonicDefinitions()
    $ccdd.writeToFileLn($tlmFile, "")
    $ccdd.writeToFileLn($tlmFile, "/* Mnemonic Definitions */")

    # Step through each row in the table
    for row in 0..$numStructRows - 1
        # Check if the variable is not telemetered
        if !isTelemetry(row)
            # Output the mnemonic definition for this row in the data table
            outputMnemonicDefinition(row)
        end
    end
end

#******************************************************************************
# Output a single discrete conversion (enumeration)
#
# @param file
#            file to which to write the discrete conversion
#
# @param discreteConversion
#            discrete conversion information
#
# @param conversionName
#            conversion name
#******************************************************************************
def outputDiscreteConversion(file, discreteConversion, conversionName)
    # Discrete conversion array indices
    value = 0
    disp_name = 1
    text_color = 2
    back_color = 3

    # Separate the enumerated parameters into an array. The expected format for
    # the enumerated values is
    # <Discrete Value> | <Display Name> | <Text Color> |
    # <Background Color> ... [, repeat for each discrete value...]
    enumerations = $ccdd.getArrayFromString(discreteConversion, "|", ",")

    # Check if the variable has enumerations and the required number of
    # parameters is provided
    if enumerations != nil && enumerations[0].length > 3
        # Output the discrete conversion header
        $ccdd.writeToFileLn(file, "DiscreteConversion " + $ccdd.getFullVariableName(conversionName, "_") + "_CONVERSION")
        $ccdd.writeToFileLn(file, "{")

        # Step through each enumerated value
        for discrete in 0..enumerations.length - 1
            # Output the discrete conversion
            $ccdd.writeToFile(file, "  Dsc " + enumerations[discrete][disp_name] + " {range = " + enumerations[discrete][value])

            # Check if a background color is supplied
            if enumerations[discrete][back_color] != nil && !enumerations[discrete][back_color].empty?
                # Output the background color
                $ccdd.writeToFile(file, ", bgColor = " + enumerations[discrete][back_color])
            end

            # Check if a foreground (text) color is supplied
            if enumerations[discrete][text_color] != nil && !enumerations[discrete][text_color].empty?
                # Output the foreground color
                $ccdd.writeToFile(file, ", fgColor = " + enumerations[discrete][text_color])
            end

            $ccdd.writeToFileLn(file, "}")
        end

        $ccdd.writeToFileLn(file, "}")
    end
end

#******************************************************************************
# Output all of the telemetry discrete conversions
#******************************************************************************
def outputTelemetryDiscreteConversions()
    isFirst = true

    # Step through each row in the structure table
    for row in 0..$numStructRows - 1
        discreteConversion = nil

        # Get the enumeration(s)
        enumerations = $ccdd.getStructureEnumerations(row)

        # Check if any enumeration exists
        if enumerations.length != 0
            # Store the first enumeration
            discreteConversion = enumerations[0]
        end

        # Check if the parameter has a discrete conversion
        if discreteConversion != nil && !discreteConversion.empty?
            # Check if this is the first discrete conversion
            if isFirst
                # Write the discrete conversion header to the file
                $ccdd.writeToFileLn($tlmFile, "")
                $ccdd.writeToFileLn($tlmFile, "/* Discrete Conversions */")
                isFirst = false
            end

            # Get the variable name and array size
            variableName = $ccdd.getStructureVariableName(row)
            arraySize = $ccdd.getStructureArraySize(row)

            # Only output non-array variables or array members (i.e., skip
            # array definitions)
            if isVariable(variableName, arraySize)
                # Get the full name and path for the variable on this row
                fullVariableName = $ccdd.getFullVariableName(row)

                # Output the discrete conversion for this row in the data table
                outputDiscreteConversion($tlmFile, discreteConversion, fullVariableName)
            end
        end
    end
end

#******************************************************************************
# Output all of the command discrete conversions
#******************************************************************************
def outputCommandDiscreteConversions()
    # Step through each row in the command table
    for row in range($numCommandRows)
        # Step through each of the commands arguments
        for argumentNum in 0..$ccdd.getNumCommandArguments(row) - 1
            # Get the discrete conversion for this command based on the
            # argument number. Null is returned if no match is found for the
            # column name; it's assumed that no more argument columns exists
            # for this command
            discreteConversion = $ccdd.getCommandArgEnumeration(argumentNum, row)

            # Check if the parameter has a discrete conversion
            if discreteConversion != nil && !discreteConversion.empty?
                # Check if this is the first discrete conversion
                if argumentNum == 0
                    # Write the discrete conversions header to the file
                    $ccdd.writeToFileLn($cmdFile, "")
                    $ccdd.writeToFileLn($cmdFile, "/* Discrete Conversions */")
                end

                # Build the name for the conversion using the command and
                # argument names
                fullCommandName = $ccdd.getCommandName(row) + "_" + $ccdd.getCommandArgName(argumentNum, row)

                # Output the discrete conversion for this row in the data table
                outputDiscreteConversion($cmdFile, discreteConversion, fullCommandName)
            end
        end
    end
end

#******************************************************************************
# Output a single enumeration
#
# @param enumeration
#            enumeration information
#
# @param conversionName
#            conversion name
#******************************************************************************
def outputCommandEnumeration(enumeration, enumerationName)
    # Enumeration array indices
    value = 0
    disp_name = 1

    # Separate the enumerated parameters into an array. The expected
    # format for the enumerated values is
    # <Discrete Value> | <Display Name> | <Text Color> |
    # <Background Color> ... [, repeat for each discrete value...]
    enumerations = $ccdd.getArrayFromString(enumeration, "|", ",")

    # Check if the variable has enumerations and the required number of
    # parameters is provided
    if enumerations != nil && enumerations[0].length > 1
        # Output the enumeration header
        $ccdd.writeToFileLn($cmdFile, "Enumeration " + enumerationName)
        $ccdd.writeToFileLn($cmdFile, "{")

        # Step through each enumerated value
        for discrete in 0..enumerations.length - 1
            # Output the enumerated value
            $ccdd.writeToFileLn($cmdFile, "  EnumerationValue " + enumerations[discrete][disp_name] + " {value = " + enumerations[discrete][value] + "}")
        end

        $ccdd.writeToFileLn($cmdFile, "}")
    end
end

#******************************************************************************
# Output all of the command enumerations
#
# @param systemName
#            system name
#******************************************************************************
def outputCommandEnumerations(systemName)
    # Step through each row in the command table
    for row in 0..$numCommandRows - 1
        # Get the system with which he command is associated from the command
        # table's 'System' data field
        commandSystem = $ccdd.getTableDataFieldValue($ccdd.getCommandTableNameByRow(row), "System")

        # Check if the this command table's system matches the target system
        if systemName == nil || (commandSystem != nil && systemName == commandSystem)
            # Step through each of the commands arguments
            for argumentNum in 0..$ccdd.getNumCommandArguments(row) - 1
                # Get the command argument's enumeration value
                enumeration = $ccdd.getCommandArgEnumeration(argumentNum, row)

                # Check if this command has an enumeration
                if enumeration != nil && !enumeration.empty?
                    # Check if this is the first enumeration for the command
                    if argumentNum == 0
                        # Write the enumerations header to the file
                        $ccdd.writeToFileLn($cmdFile, "")
                        $ccdd.writeToFileLn($cmdFile, "/* Enumerations */")
                    end

                    # Output the enumeration for this row in the data table
                    outputCommandEnumeration(enumeration, getCommandEnumerationName(row, argumentNum))
                end
            end
        end
    end
end

#******************************************************************************
# Output a single limit or limit set definition
#
# @param row
#            row index in the structure data table
#
# @param limitSets
#            limit set(s)
#
# @param isFirst
#            true if this is the first limit definition
#
# @return True if a limit definition is output
#******************************************************************************
def outputLimitDefinition(row, limitSets, isFirst)
    # Get the variable name and array size
    variableName = $ccdd.getStructureVariableName(row)
    arraySize = $ccdd.getStructureArraySize(row)

    # Only output non-array variables or array members (i.e., skip array
    # definitions)
    if isVariable(variableName, arraySize)
        # Separate the limits into an array
        limits = $ccdd.getArrayFromString(limitSets, "|", ",")

        # Check if the variable has limits
        if limits != nil
            # Check if this is the first limit definition
            if isFirst
                # Write the limit definitions header to the file
                $ccdd.writeToFileLn($tlmFile, "")
                $ccdd.writeToFile($tlmFile, "/* Limit Definitions */")
                isFirst = false
            end

            # Check if a single limit is specified
            if limits.length == 1
                # Output the limit header
                $ccdd.writeToFileLn($tlmFile, "")
                $ccdd.writeToFileLn($tlmFile, "Limit " + $ccdd.getFullVariableName(row) + "_LIMIT")
                $ccdd.writeToFileLn($tlmFile, "{")

                # Step through each limit definition
                for index in 0..limits[0].length - 1
                    # Check if this is is the red-low, yellow-low, yellow-high,
                    # or red-high limit
                    if index < 4 && !limits[0][index].empty?
                        # Output the limit
                        $ccdd.writeToFileLn($tlmFile, "  " + $ccdd.getITOSLimitName(index) + " = " + limits[0][index])
                    end
                end

                $ccdd.writeToFileLn($tlmFile, "}")
            # Multiple limits are specified
           elsif limits.length > 1
                # Output the limit set header
                $ccdd.writeToFileLn($tlmFile, "")
                $ccdd.writeToFileLn($tlmFile, "LimitSet " + $ccdd.getFullVariableName(row) + "_LIMIT")
                $ccdd.writeToFileLn($tlmFile, "{")
                $ccdd.writeToFileLn($tlmFile, "  contextMnemonic = " + limits[0][0])
                $ccdd.writeToFileLn($tlmFile, "")

                # Step through each limit set
                for set in 1..limits.length - 1
                    # Check if this is not the first limit value
                    if set != 1
                        # Output a line feed
                        $ccdd.writeToFileLn($tlmFile, "")
                    end

                    # Output the limit header
                    $ccdd.writeToFileLn($tlmFile, "  Limit limit" + set.to_s)
                    $ccdd.writeToFileLn($tlmFile, "  {")

                    limitIndex = 0

                    # Step through each limit definition
                    for index in 0..limits[set].length - 1
                        # Check if the limit value exists
                        if !limits[set][index].empty?
                            # Check if this is the context range
                            if limits[set][index].include? ".."
                                # Output the context range
                                $ccdd.writeToFileLn($tlmFile, "    contextRange = " + limits[set][index])
                            # Not the context range; must be a limit value
                            else
                                # Output the limit value
                                $ccdd.writeToFileLn($tlmFile, "    " + $ccdd.getITOSLimitName(limitIndex) + " = " + limits[set][index])

                                limitIndex = limitIndex + 1
                            end
                        end
                    end

                    $ccdd.writeToFileLn($tlmFile, "  }")
                end

                $ccdd.writeToFileLn($tlmFile, "}")
            end
        end
    end

    return isFirst
end

#******************************************************************************
# Output all of the limit and limit set definitions
#******************************************************************************
def outputLimitDefinitions()
    isFirst = true

    # Step through each row in the table
    for row in 0..$numStructRows - 1
        # Get the limits for this row
        limitSets = $ccdd.getStructureTableData("limit sets", row)

        # Check if the parameter has limits
        if limitSets != nil && !limitSets.empty?
            # Output the limit definition for this row in the data table
            isFirst = outputLimitDefinition(row, limitSets, isFirst)
        end
    end
end

#******************************************************************************
# Output a single polynomial conversion
#
# @param prefix
#            conversion name prefix
#
# @param variableName
#            variable name
#
# @param coeffs
#            polynomial coefficient array
#******************************************************************************
def outputPolynomial(prefix, variableName, coeffs)
    # Output the polynomial conversion header
    $ccdd.writeToFileLn($tlmFile, "")
    $ccdd.writeToFile($tlmFile, "PolynomialConversion " + prefix + variableName + "_CONVERSION")
    $ccdd.writeToFileLn($tlmFile, "{")
    $ccdd.writeToFile($tlmFile, "  coefficients = {")

    # Output the first coefficient (with no preceding comma)
    $ccdd.writeToFile($tlmFile, coeffs[0])

    # Step through each remaining coefficient value
    for index in 1..coeffs.length - 1
        # Output the coefficient, preceded by a comma
        $ccdd.writeToFile($tlmFile, ", " + coeffs[index])
    end

    $ccdd.writeToFileLn($tlmFile, "}")
    $ccdd.writeToFileLn($tlmFile, "}")
end

#******************************************************************************
# Output a single polynomial conversion
#
# @param row
#            row index in the structure data table
#
# @param polynomialCoefficients
#            one or more polynomial coefficient sets from the data table
#******************************************************************************
def outputPolynomialConversion(row, polynomialCoefficients)
    # Get the variable name and array size
    variableName = $ccdd.getStructureVariableName(row)
    arraySize = $ccdd.getStructureArraySize(row)

    # Only output non-array variables or array members (i.e., skip array
    # definitions)
    if isVariable(variableName, arraySize)
        # Separate the sets of the coefficients (if there is more than one)
        polySets = polynomialCoefficients.split("\\;")

        # Get the number of coefficient sets
        numPolySets = polySets.length

        # Check if the number of flight computers is less than the number of
        # sets detected
        if $numFlightComputers < numPolySets
            # Reduce the number of sets to match the number of flight computers
            numPolySets = $numFlightComputers
        end

        prefix = ""
        polyIndex = 0

        # Step through each set of polynomial coefficients
        for polyIndex in 0..numPolySets - 1
            # Check if there is more than one set
            if numPolySets > 1
                # Set the prefix to the flight computer name
                prefix = $fcNames[polyIndex]
            end

            # Separate the polynomial coefficients into an array
            coeffs = $ccdd.getArrayFromString(polySets[polyIndex], "|")

            # Output the polynomial conversion definition
            outputPolynomial(prefix, $ccdd.getFullVariableName(row), coeffs)
        end

        # Check if there is more than one set
        if numPolySets > 1
            # Get the index of last valid set of coefficients
            lastPolyindex = (polyIndex - 1)

            # Separate the polynomial coefficients into an array
            coeffs = $ccdd.getArrayFromString(polySets[lastPolyindex], "|")

            # Step through any remaining flight computers that don't have a
            # polynomial coefficient set
            for remPolyIndex in polyIndex..$numFlightComputers - 1
                # Set the prefix to the flight computer name
                prefix = $fcNames[remPolyIndex]

                # Output the polynomial conversion definition using the
                # coefficients from the last defined set
                outputPolynomial(prefix, $ccdd.getFullVariableName(row), coeffs)
            end
        end
    end
end

#******************************************************************************
# Output all of the polynomial conversions
#******************************************************************************
def outputPolynomialConversions()
    isFirst = true

    # Step through each row in the table
    for row in 0..$numStructRows - 1
        # Get the polynomial coefficients for this row
        polynomialCoefficients = $ccdd.getStructureTableData("polynomial coefficients", row)

        # Check if the parameter has polynomial coefficients
        if polynomialCoefficients != nil && !polynomialCoefficients.empty?
            # Check if this is the first polynomial conversion
            if isFirst
                # Write the polynomial conversion header to the file
                $ccdd.writeToFileLn($tlmFile, "")
                $ccdd.writeToFileLn($tlmFile, "/* Polynomial Conversions  -- a list of constants  {a0,a1,a2,,,an}    ,  where  y= a0 + a1*x + a2*x^2 + ... an*x^n */")
                isFirst = false
            end

            # Output the polynomial conversion for this row in the data table
            outputPolynomialConversion(row, polynomialCoefficients)
        end
    end
end

#* End functions **************************************************************

#* Main ***********************************************************************

$newRowOrder = []
$fcNames = []
$fcOffset = []
$numFlightComputers = 0

# Get the number of structure and command table rows
$numStructRows = $ccdd.getStructureTableNumRows()
$numCommandRows = $ccdd.getCommandTableNumRows()

# Check if no structure or command data is supplied
if $numStructRows == 0 && $numCommandRows == 0
    showErrorDialog("No structure or command data supplied to script " + $ccdd.getScriptName())
# Structure and/or command data is supplied
else
    $endianess = ""
    endianExtn = ""
    tmpVal = 0

    # Get the value of the data field specifying the message ID skip value
    msgIDSkip = $ccdd.getGroupDataFieldValue("globals", "MID_delta")

    # Check if the data field exists or is empty
    if msgIDSkip == nil || msgIDSkip.empty?
        # Use the default skip value
        msgIDSkip = "0x600"
    end

    # Get the value of the data field specifying the flight computer offset
    # value
    fcOffsetVal = $ccdd.getGroupDataFieldValue("globals", "FC_Offset")

    # Check if the data field exists or is empty
    if fcOffsetVal == nil || fcOffsetVal.empty?
        # Use the default offset value
        $fcOffset.push("0x0000")
    end

    # Get the value of the data field specifying the flight computer base value
    fcBase = $ccdd.getGroupDataFieldValue("globals", "prefix")

    # Check if the data field exists or is empty
    if fcBase == nil || fcBase.empty?
        # Use the default base value
        fcBase = "FC"
    end

    # Get the value of the data field specifying the number of flight computers
    numFC = $ccdd.getGroupDataFieldValue("globals", "NumComputers")

    # Check if the data field exists, is empty, or isn't an integer value
    if numFC == nil || !(numFC =~ /[0-9]+/)
        # Use the default number of flight computers (based on the number of
        # offset values detected)
        $numFlightComputers = $fcOffset.length
    # The value is an integer
    else
        # Store the number of flight computers
        $numFlightComputers = numFC.to_i;
    end

    # Check if there is more than one flight computer
    if $numFlightComputers > 1
        # Step through each flight computer
        for fcIndex in 0..$numFlightComputers - 1
            # Store the flight computer name prefix and offset value
            $fcNames.push(fcBase + (fcIndex + 1).to_s + "_")
            $fcOffset.push(fcOffsetVal)

            # Calculate the next offset based on the current offset and the ID
            # skip values
            nextMsgID = fcOffsetVal.sub("0x", "").to_i(16) + msgIDSkip.sub("0x", "").to_i(16)
            fcOffsetVal = "0x" + ("%03x" % nextMsgID)
        end
    # Only one flight computer
    else
        # No prefix or offset for a single computer
        $fcOffset.push("0x0000")
        $fcNames.push("")
    end

    # Define the radio button text and descriptions for the dialog
    buttons = [ [ "Big", "Big endian" ],
                [ "Big (swap)", "Big endian (word swapped)" ],
                [ "Little", "Little endian" ],
                [ "Little (swap)", "Little endian (word swapped)" ] ]

    # Get the endianess choice from the user
    selected = $ccdd.getRadioButtonDialog("Select endianess", buttons)

    # Check that an endianess was selected
    if selected != nil
        # Check if the output should be big endian
        if selected == "Big"
            $endianess = "BIG_ENDIAN"
            endianExtn = "BE"
        # Check if the output should be big endian, word swapped
       elsif selected == "Big (swap)"
            $endianess = "BIG_ENDIAN_SWAP"
            endianExtn = "BE"
        # Check if the output is little endian
       elsif selected == "Little"
            $endianess = "LITTLE_ENDIAN"
            endianExtn = "LE"
        # Check if the output is little endian, word swapped
       elsif selected == "Little (swap)"
            $endianess = "LITTLE_ENDIAN_SWAP"
            endianExtn = "LE"
        end

        # Create the structure row order array that rearranges the bit-packed
        # variables based on endianess
        $newRowOrder = reorderRowsForByteOrder(endianExtn)

        # Get the current date and time
        dateAndTime = $ccdd.getDateAndTime()

        # Check if structure data is provided
        if $numStructRows > 0
            # The output file names are based in part on the value of the data
            # field, 'System', found in the first group or table associated
            # with the script. If the field can't be found in either then the
            # value is set to a blank
            systemName = nil

            # Get the group(s) associated with the script (if any)
            groupNames = $ccdd.getAssociatedGroupNames()

            # Check if a group is associated with the script
            if groupNames.length != 0
                # Get the value of the first group's 'System' data field, if
                # present
                systemName = $ccdd.getGroupDataFieldValue(groupNames[0], "System")
            end

            # Check if the system name wasn't found in the group data field
            if systemName == nil || systemName.empty?
                # Get the value of the first root structure's 'System' data
                # field
                systemName = $ccdd.getTableDataFieldValue($ccdd.getRootStructureTableNames()[0], "System")
            end

            # Check if the data field doesn't exist in either a group or table
            if systemName == nil
                systemName = ""
            end

            # Build the telemetry output file name
            tlmOutputFile = $ccdd.getOutputPath() + systemName + "_" + endianExtn + ".rec"

            # Open the telemetry output file
            $tlmFile = $ccdd.openOutputFile(tlmOutputFile)
            $combFile = $ccdd.openOutputFile($ccdd.getOutputPath() + "common.rec")

            # Check if the telemetry output file successfully opened
            if $tlmFile != nil || $combFile != nil
                # Get the names of all structures/sub-structures referenced in
                # tables
                structureNames = $ccdd.getStructureTablesByReferenceOrder()

                # Add a header to the output files
                outputFileCreationInfo($combFile)
                outputFileCreationInfo($tlmFile)

                # Output the structure prototypes and telemetry packet
                # definitions
                outputStructures(structureNames)

                # Output the discrete conversions
                outputTelemetryDiscreteConversions()

                # Output the limit definitions
                outputLimitDefinitions()

                # Output the polynomial conversions
                outputPolynomialConversions()

                # Output the mnemonic definitions
                outputMnemonicDefinitions()

                # Close the telemetry output files
                $ccdd.closeFile($tlmFile)
                $ccdd.closeFile($combFile)
            # The telemetry output files cannot be opened
            else
                # Display an error dialog
                $ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + tlmOutputFile + "<b>' or common.rec")
            end
        end

        # Check if command data is provided
        if $numCommandRows > 0
            # Get the value of the 'System' data field for first command table
            firstSystemName = $ccdd.getTableDataFieldValue($ccdd.getCommandTableNames()[0], "System")

            # If the system name doesn't exist then substitute a blank
            if firstSystemName == nil
                firstSystemName = ""
            end

            # Step through each flight computer
            for fcIndex in 0..$numFlightComputers - 1
                msgIDOffset = $fcOffset[fcIndex]
                prefix = $fcNames[fcIndex]

                # Build the command output file name and open the command
                # output file
                $cmdFileName = $ccdd.getOutputPath() + prefix + firstSystemName + "_CMD" + "_" + endianExtn + ".rec"
                $cmdFile = $ccdd.openOutputFile($cmdFileName)

                # Check if the command output file successfully opened
                if $cmdFile != nil
                    # Add a header to the output file
                    outputFileCreationInfo($cmdFile)

                    # Step through each command table
                    for cmdTblIndex in 0..$ccdd.getCommandTableNames().length - 1
                        # Get the value of the 'System' data field
                        systemName = $ccdd.getTableDataFieldValue($ccdd.getCommandTableNames()[cmdTblIndex], "System")

                        # Output the enumerations for this system
                        outputCommandEnumerations(systemName)

                        # Output the commands for this system
                        outputCommands(prefix, msgIDOffset, systemName)
                    end

                    # Close the command output file
                    $ccdd.closeFile($cmdFile)
                # The command output file cannot be opened
                else
                    # Display an error dialog
                    $ccdd.showErrorDialog("<html><b>Error opening command output file '</b>" + $cmdFileName + "<b>'")
                end
            end
        end
    end
end
