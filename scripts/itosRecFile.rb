#******************************************************************************
# Description: Output an ITOS record file
# 
# This Ruby script generates an ITOS record file from the supplied table and
# packet information
#******************************************************************************
java_import Java::CCDD.CcddScriptDataAccessHandler

#** defs **********************************************************************

#******************************************************************************
# Determine if the row containing the specified variable is not an array
# definition. A row in a table is an array definition if a value is present in
# the Array Size column but the variable name does not end with a ']'
# 
# @param variableName variable name
# 
# @param arraySize array size
# 
# @return true if the variable is not an array definition
#******************************************************************************
def isVariable(variableName, arraySize)
    # Only output non-array variables or array members (i.e., skip array definitions)
    return variableName != nil && arraySize != nil && (arraySize.empty? || variableName.end_with?("]"))
end

#******************************************************************************
# Convert an array member variable name by replacing left square brackets with
# underscores and removing right square brackets (example: a[2] becomes a_2)
# 
# @param variableName variable name
# 
# @return Variable name with the square brackets replaced
#******************************************************************************
def convertArrayMember(variableName)
    return variableName.gsub(/[\[]/, "_").gsub(/[\]]/, "")
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
    return $ccdd.getCommandTableData("command name", row) \
           + "_" \
           + $ccdd.getCommandTableData("arg " + argumentNum.to_s + " name", row) \
           + "_ENUMERATION"
end

#******************************************************************************
# Output a telemetry packet or prototype structure definition
# 
# @param structureName structure name
# 
# @param isPacket true if this is a telemetry packet definition false for a
#                 prototype structure definition
#******************************************************************************
def outputStructureDefinition(structureName, isPacket)
    termLine = false
    usedVariableNames = []

    # Step through each row in the table
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Check that this row references a variable in the prototype structure
        if $ccdd.getStructureTableNameByRow(row) == structureName
            # Get the variable name for this row
            variableName = $ccdd.getStructureTableData("variable name", row)

            # Check if the variable name hasn't already been processed;
            # this is necessary to prevent duplicating the members in
            # the type definition for a structure that is referenced
            # as an array
            if !usedVariableNames.include? variableName
                # Add the variable name to the list of those already processed
                usedVariableNames.push(variableName)

                # Get the array size for this row
                arraySize = $ccdd.getStructureTableData("array size", row)
                
                # Only output non-array variables or array members (i.e., skip
                # array definitions)
                if isVariable(variableName, arraySize)
                    # Get the ITOS encoded form of the data type
                    itosEncode = $ccdd.getITOSEncodedDataType($ccdd.getStructureTableData("data type", row), $endianess)
    
                    # Check if this is not the first pass
                    if termLine
                        # Check if this is the packet definition
                        if isPacket
                            # Terminate the previous line with a comma
                            $ccdd.writeToFileLn($tlmFile, ",")
                        # This is a prototype structure
                        else
                            # Terminate the previous line with a line feed
                            $ccdd.writeToFileLn($tlmFile, "")
                        end
                    end
                      
                    termLine = true
    
                    # In case this is an array member replace the square brackets
                    variableName = convertArrayMember(variableName)
    
                    dataType = itosEncode
                    
                    # Check if this is a primitive data type
                    if itosEncode == nil
                        # Use the data type in place of the ITOS encoding
                        dataType = $ccdd.getStructureTableData("data type", row)
                    end
                    
                    # Get the length in bits for this row
                    bitLength = $ccdd.getStructureTableData("bit length", row)

                    otherParameters = ""
                  
                    # Check if the length in bits is specified
                    if bitLength != nil && !bitLength.empty?
                        # Add the length in bits parameter
                        otherParameters = "lengthInBits=" + bitLength
                    end
                    
                    # Check if this variable is a primitive data type and not a structure
                    if itosEncode != nil
                        # Check if other parameters have been defined
                        if !otherParameters.empty?
                            # Add a space to separate the parameters
                            otherParameters += " "
                        end
                        
                        # Add the 'no mnemonic' parameter
                        otherParameters += "generateMnemonic=\"no\""
                    end
                    
                    # Create the parameter definition
                    $ccdd.writeToFile($tlmFile,
                                     "  " \
                                     + dataType \
                                     + " " \
                                     + structureName \
                                     + "_" \
                                     + variableName \
                                     + " {" \
                                     + otherParameters \
                                     + "}")
                end
            end
        end
    end
end
                
#******************************************************************************
# Output the telemetry packet definition
# 
# @param structureNames array of all structure table names
#******************************************************************************
def outputTelemetryPacket(structureNames)
    # Begin the packet definition
    $ccdd.writeToFileLn($tlmFile, "CfeTelemetryPacket " + $ccdd.getRootStructureTableNames()[0])
    $ccdd.writeToFileLn($tlmFile, "{")
    $ccdd.writeToFileLn($tlmFile,
                       "  applyWhen={FieldInRange{field = applicationId, range = " \
                       + $ccdd.getTableDataFieldValue($ccdd.getRootStructureTableNames()[0], "Message ID").gsub(/0x[0-9]{2}/, "0x") \
                       + "}},")
    
    # Build the packet definition
    outputStructureDefinition(structureNames[0], true)
    
    # End the packet definition
    $ccdd.writeToFileLn($tlmFile, "")
    $ccdd.writeToFileLn($tlmFile, "}")
end
                
#******************************************************************************
# Output the prototype structures
# 
# @param structureNames array of all structure table names
#******************************************************************************
def outputPrototypeStructures(structureNames)
    # Step through each structure name
    for index in 0..structureNames.length - 1
        # Check that this isn't the top-level structure (i.e., it's a
        # referenced structure)
        if structureNames[index] != $ccdd.getRootStructureTableNames()[0]
            # Begin the prototype structure definition
            $ccdd.writeToFileLn($tlmFile, "")
            $ccdd.writeToFileLn($tlmFile, "prototype Structure " + structureNames[index])
            $ccdd.writeToFileLn($tlmFile, "{")

            # Build the prototype structure definition
            outputStructureDefinition(structureNames[index], false)

            # End the prototype structure definition
            $ccdd.writeToFileLn($tlmFile, "")
            $ccdd.writeToFileLn($tlmFile, "}")
        end
    end
end

#******************************************************************************
# Output the commands
#******************************************************************************
def outputCommands()
    # Get the application ID data field value
    applicationID = $ccdd.getTableDataFieldValue($ccdd.getRootCommandTableNames()[0], "application id")
    
    # Check if the application ID data field doesn't exist
    if applicationID == nil
        # Set the application ID to a blank
        applicationID = ""
    end
    
    # Step through each row in the command table
    for row in 0..$ccdd.getCommandTableNumRows() - 1
        # Begin the command definition
        $ccdd.writeToFileLn($cmdFile, "")
        $ccdd.writeToFileLn($cmdFile, "CfeSoftwareCommand " + $ccdd.getCommandTableData("command name", row))
        $ccdd.writeToFileLn($cmdFile, "{")
        $ccdd.writeToFileLn($cmdFile, "  applicationID {range = " + applicationID + "}")
        $ccdd.writeToFileLn($cmdFile, "  commandCode {range = " + $ccdd.getCommandTableData("command code", row) + "}")

        argumentNum = 1
        name = " "
        
        # Process all of the command arguments for this command
        while name != nil && !name.empty?
            # Build the prefix for the command argument column names
            argument = "arg " + argumentNum.to_s + " "
        
            # Get the command argument's name, data type, and enumeration values
            name = $ccdd.getCommandTableData(argument + "name", row)
            dataType = $ccdd.getCommandTableData(argument + "data type", row)
            enumeration = $ccdd.getCommandTableData(argument + "enumeration", row)
            
            # Check if the parameter has an argument
            if name != nil && !name.empty? && dataType != nil && !dataType.empty?
                # Get the ITOS encoded form of the data type
                itosEncode = $ccdd.getITOSEncodedDataType(dataType, $endianess)

                # Get the single character ITOS encoded form of the data type
                itosEncodeChar = $ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR")

                argumentInfo = ""
                
                # Check if the parameter is an integer (signed or unsigned)
                if itosEncodeChar == "I" || itosEncodeChar == "U"
                    # Get the command argument's minimum and maximum values
                    minimumValue = $ccdd.getCommandTableData(argument + "minimum", row)
                    maximumValue = $ccdd.getCommandTableData(argument + "maximum", row)

                    # Check if this command has an enumeration
                    if enumeration != nil && !enumeration.empty?
                        # Add the associated enumeration definition
                        argumentInfo += "enumeration = " +  getCommandEnumerationName(row, argumentNum) + ", "
                    end
                    
                    # The get size in bytes based on the data type
                    sizeInBytes = $ccdd.getDataTypeSizeInBytes(dataType)
                    
                    # Check that the argument has a valid data type
                    if sizeInBytes != 0
                        # Check if a minimum value doesn't exist for this argument
                        if minimumValue == nil || minimumValue.empty?
                            # Set the minimum value to zero, assuming this is
                            # an unsigned integer
                            minimumValue = 0;
                            
                            # Check if the argument is a signed integer
                            if itosEncodeChar == "I"
                                # Set the minimum value to the largest negative
                                # value for this size integer
                                minimumValue = -(2 ** (sizeInBytes * 8)) / 2
                            end
                        end
                        
                        # Check if a maximum value doesn't exist for this argument
                        if maximumValue == nil || maximumValue.empty?
                            # Set the maximum value to the largest positive value
                            # for an unsigned integer
                            maximumValue = 2 ** (sizeInBytes * 8)
                            
                            # Check if the argument is a signed integer
                            if itosEncodeChar == "I"
                                # Adjust the maximum to the largest size for this
                                # size integer
                                maximumValue -= maximumValue / 2 + 1
                            end
                        end
                    end
                      
                    argumentInfo += "range = " + minimumValue.to_s + ".." + maximumValue.to_s                   
                # Check if the parameter is a floating point value
                elsif itosEncodeChar == "F"
                    # Nothing to show for a float or double
                # Check if the parameter is a string
                elsif itosEncodeChar == "S"
                    argumentInfo = "lengthInCharacters = "; # TODO Need # of characters
                end
            
                # Output the command argument to the file
                $ccdd.writeToFileLn($cmdFile, "  " + itosEncode + " " + name + " {" + argumentInfo + "}")
            end   
               
            # Advance to the next argument number
            argumentNum += 1
        end
        
        $ccdd.writeToFileLn($cmdFile, "}")
    end
end

#******************************************************************************
# Output a single mnemonic definition
# 
# @param row row index in the structure data table
#******************************************************************************
def outputMnemonicDefinition(row)
    # Get the single character ITOS encoded form of the data type
    itosEncode = $ccdd.getITOSEncodedDataType($ccdd.getStructureTableData("data type", row), "SINGLE_CHAR")

    # Check if this data type is a recognized type
    if itosEncode != nil
        # Get the variable name and array size
        variableName = $ccdd.getStructureTableData("variable name", row)
        arraySize = $ccdd.getStructureTableData("array size", row)

        # Only output non-array variables or array members (i.e., skip
        # array definitions)
        if isVariable(variableName, arraySize)
            # Get the variable's structure path
            structurePath = $ccdd.getStructureTableITOSPathByRow(row)
            
            # In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            # Get the variable's structure name
            structureName = $ccdd.getStructureTableNameByRow(row)
            
            # Get the full variable name for this variable, which includes all
            # of the variable names in its structure path
            fullVariableName = $ccdd.getFullVariableName(row)

            # Create the mnemonic definition
            $ccdd.writeToFile($tlmFile,
                             itosEncode \
                             + " " \
                             + fullVariableName \
                             + " {sourceFields = {" \
                             + $ccdd.getRootStructureTableNames()[0] \
                             + "." \
                             + structurePath \
                             + "_" \
                             + variableName)
                             
            $ccdd.writeToFile($tlmFile, "}")

            # Get the enumeration, polynomial conversion, and limit columns (if extant)
            enumeration = $ccdd.getStructureTableData("enumeration", row)
            polynomial = $ccdd.getStructureTableData("polynomial coefficients", row)
            limitSet = $ccdd.getStructureTableData("limit sets", row)
            
            # Check if this parameter includes a discrete or polynomial conversion
            if (enumeration != nil && !enumeration.empty?) \
                or (polynomial != nil && !polynomial.empty?)
                # Output the conversion reference
                $ccdd.writeToFile($tlmFile,
                                 " conversion = " + fullVariableName + "_CONVERSION")
            end
            
            # Check if this parameter includes a limit or limit set
            if limitSet != nil && !limitSet.empty?
                # Output the limit reference
                $ccdd.writeToFile($tlmFile,
                                 " limits = " + fullVariableName + "_LIMIT")
            end
            
            $ccdd.writeToFileLn($tlmFile, "}")
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
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        outputMnemonicDefinition(row)
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
    disp_name = 2
    text_color = 3
    back_color = 4

    # Separate the enumerated parameters into an array. The expected
    # format for the enumerated values is:
    # <discrete value> | <Name> | <Display Name> | <Text Color> |
    # <Background Color> [, repeat for each discrete value...]
    enumerations = $ccdd.getArrayFromString(discreteConversion, "|", ",")

    # Check if the variable has enumerations
    if enumerations != nil
        # Output the discrete conversion header
        $ccdd.writeToFileLn(file, 
                           "DiscreteConversion " + conversionName + "_CONVERSION")
        $ccdd.writeToFileLn(file, "{")

        # Step through each enumerated value
        for discrete in 0..enumerations.length - 1
            # Output the discrete conversion
            $ccdd.writeToFile(file, 
                             "  Dsc " \
                             + enumerations[discrete][disp_name] \
                             + " {range = " \
                             + enumerations[discrete][value])
            
            # Check if a background color is supplied
            if enumerations[discrete][back_color]
                # Output the background color
                $ccdd.writeToFile(file, ", bgColor = " + enumerations[discrete][back_color])
            end

            # Check if a foreground (text) color is supplied
            if enumerations[discrete][text_color]
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
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Get the discrete conversion for this parameter
        discreteConversion = $ccdd.getStructureTableData("enumeration", row)
        
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
            variableName = $ccdd.getStructureTableData("variable name", row)
            arraySize = $ccdd.getStructureTableData("array size", row)

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
    for row in 0..$ccdd.getCommandTableNumRows() - 1
        argumentNum = 1
        discreteConversion = " "
        
        # Process each discrete conversion
        while discreteConversion != nil
            # Get the discrete conversion for this command based on the
            # argument number. Null is returned if no match is found for the
            # column name; it's assumed that no more argument columns exists
            # for this command
            discreteConversion = $ccdd.getCommandTableData("arg " + argumentNum.to_s + " enumeration", row)
            
            # Check if the parameter has a discrete conversion
            if discreteConversion != nil && !discreteConversion.empty?
                # Check if this is the first discrete conversion
                if argumentNum == 1
                    # Write the discrete conversions header to the file
                    $ccdd.writeToFileLn($cmdFile, "")
                    $ccdd.writeToFileLn($cmdFile, "/* Discrete Conversions */")
                end
                
                # Build the name for the conversion using the command and
                # argument names
                fullCommandName = $ccdd.getCommandTableData("command name", row) \
                                                      + "_" \
                                                      + $ccdd.getCommandTableData("arg " + argumentNum.to_s \
                                                      + " name",
                                                      row)
                
                # Output the discrete conversion for this row in the data table
                outputDiscreteConversion($cmdFile, discreteConversion, fullCommandName)
            end
            
            # Advance to the next argument number
            argumentNum += 1
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
def outputEnumeration(enumeration, enumerationName)
    # Enumeration array indices
    value = 0
    disp_name = 2

    # Separate the enumerated parameters into an array. The expected
    # format for the enumerated values is:
    # <discrete value> | <Name> | <Display Name> | <Text Color> |
    # <Background Color> [, repeat for each discrete value...]
    enumerations = $ccdd.getArrayFromString(enumeration, "|", ",")

    # Check if the variable has enumerations
    if enumerations != nil
        # Output the enumeration header
        $ccdd.writeToFileLn($cmdFile, 
                           "Enumeration " + enumerationName)
        $ccdd.writeToFileLn($cmdFile, "{")

        # Step through each enumerated value
        for discrete in 0..enumerations.length - 1
            # Output the enumerated value
            $ccdd.writeToFile($cmdFile, 
                             "  EnumerationValue " \
                             + enumerations[discrete][disp_name] \
                             + " {value = " \
                             + enumerations[discrete][value])
            
            $ccdd.writeToFileLn($cmdFile, "}")
        end

        $ccdd.writeToFileLn($cmdFile, "}")
    end
end

#******************************************************************************
# Output all of the command enumerations
#******************************************************************************
def outputEnumerations()
    # Step through each row in the command table
    for row in 0..$ccdd.getCommandTableNumRows() - 1
        argumentNum = 1
        enumeration = " "
    
        # Process each enumeration
        while enumeration != nil
            # Get the enumeration for this command based on the argument
            # number. Null is returned if no match is found for the column
            # name; it's assumed that no more argument columns exists
            # for this command
            enumeration = $ccdd.getCommandTableData("arg " + argumentNum.to_s + " enumeration", row)
            
            # Check if the parameter has an enumeration
            if enumeration != nil && !enumeration.empty?
                # Check if this is the first enumeration
                if argumentNum == 1
                    # Write the enumerations header to the file
                    $ccdd.writeToFileLn($cmdFile, "")
                    $ccdd.writeToFileLn($cmdFile, "/* Enumerations */")
                end
                
               # Build the name for the enumeration using the command and
               # argument names
               enumerationName = getCommandEnumerationName(row, argumentNum)
                
                # Output the enumeration for this row in the data table
                outputEnumeration(enumeration, enumerationName)
            end
            
            # Advance to the next argument number
            argumentNum += 1
        end
    end
end

#******************************************************************************
# Output a single limit or limit set definition
# 
# @param row row index in the structure data table
# 
# @param isFirst true if this is the first limit definition
# 
# @returns true is a limit definition is output to the file
#******************************************************************************
def outputLimitDefinition(row, isFirst)
    isOutput = false
    
    # Get the limits for this row
    limitSets = $ccdd.getStructureTableData("limit sets", row)

    # Check if the parameter has limits
    if limitSets != nil && !limitSets.empty?
        # Get the variable name and array size
        variableName = $ccdd.getStructureTableData("variable name", row)
        arraySize = $ccdd.getStructureTableData("array size", row)

        # Only output non-array variables or array members (i.e., skip
        # array definitions)
        if isVariable(variableName, arraySize)
            # In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

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
                    $ccdd.writeToFileLn($tlmFile, 
                                       "Limit " + $ccdd.getFullVariableName(row) + "_LIMIT")
                    $ccdd.writeToFileLn($tlmFile, "{")
    
                    # Step through each limit definition
                    for index in 0..limits[0].length - 1
                        #Check if this is is the red-low, yellow-low,
                        # yellow-high, or red-high limit
                        if index < 4 && !limits[0][index].empty?
                            # Output the limit
                            $ccdd.writeToFileLn($tlmFile,
                                               "  " \
                                               + $ccdd.getITOSLimitName(index) \
                                               + " = " \
                                               + limits[0][index])
                        end
                    end
    
                    $ccdd.writeToFileLn($tlmFile, "}")
                # Multiple limits are specified
                elsif limits.length > 1
                    # Output the limit set header
                    $ccdd.writeToFileLn($tlmFile, "")
                    $ccdd.writeToFileLn($tlmFile, 
                                       "LimitSet " + $ccdd.getFullVariableName(row) + "_LIMIT")
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
                        $ccdd.writeToFileLn($tlmFile, "  Limit limit" + String(set))
                        $ccdd.writeToFileLn($tlmFile, "  {")
        
                        limitIndex = 0
                            
                        # Step through each limit definition
                        for index in 0..limits[set].length - 1
                            # Check if the limit value exists
                            if !limits[set][index].empty?
                                # Check if this is the context range
                                if limits[set][index].include? ".."
                                    # Output the context range
                                    $ccdd.writeToFileLn($tlmFile,
                                                       "    contextRange = " + limits[set][index])
                                # Not the context range must be a limit value
                                else
                                    # Output the limit value
                                    $ccdd.writeToFileLn($tlmFile,
                                                       "    " \
                                                       + $ccdd.getITOSLimitName(limitIndex) \
                                                       + " = " \
                                                       + limits[set][index])
                                    
                                    limitIndex += 1
                                end
                            end
                        end
    
                        $ccdd.writeToFileLn($tlmFile, "  }")
                    end
    
                    $ccdd.writeToFileLn($tlmFile, "}")
                end
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
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Output the limit definition for this row in the data table
        isFirst = outputLimitDefinition(row, isFirst)
    end
end

#******************************************************************************
# Output a single polynomial conversion
# 
# @param row row index in the structure data table
# 
# @param isFirst true if this is the first polynomial conversion
# 
# @returns true is a polynomial conversion is output to the file
#******************************************************************************
def outputPolynomialConversion(row, isFirst)
    # Get the polynomial coefficients for this row
    polynomialCoefficients = $ccdd.getStructureTableData("polynomial coefficients", row)

    # Check if the parameter has polynomial coefficients
    if polynomialCoefficients != nil && !polynomialCoefficients.empty?
        # Check if this is the first polynomial conversion
        if isFirst
            # Write the polynomial conversion header to the file
            $ccdd.writeToFileLn($tlmFile, "")
            $ccdd.writeToFile($tlmFile, "/* Polynomial Conversions */")
            isFirst = false
        end
      
        # Get the variable name and array size
        variableName = $ccdd.getStructureTableData("variable name", row)
        arraySize = $ccdd.getStructureTableData("array size", row)

        # Only output non-array variables or array members (i.e., skip
        # array definitions)
        if isVariable(variableName, arraySize)
            # Separate the polynomial coefficients into an array
            coeffs = $ccdd.getArrayFromString(polynomialCoefficients, "|")

            # Output the polynomial conversion header
            $ccdd.writeToFileLn($tlmFile, "")
            $ccdd.writeToFileLn($tlmFile, 
                               "PolynomialConversion " + $ccdd.getFullVariableName(row) + "_CONVERSION")
            $ccdd.writeToFileLn($tlmFile, "{")
            $ccdd.writeToFile($tlmFile, "  coefficients = {")

            addComma = false
            
            # Step through each coefficient value
            for index in 0..coeffs.length - 1
                # Check if this is not the first pass
                if addComma
                    # Append a comma
                    $ccdd.writeToFile($tlmFile, ", ")
                end
                
                addComma = true

                # Output the polynomial coefficient
                $ccdd.writeToFile($tlmFile, coeffs[index])
            end
        
            $ccdd.writeToFileLn($tlmFile, "}")
            $ccdd.writeToFileLn($tlmFile, "}")
        end
    end

    return isFirst
end
            
#****************************************************************************
# Output all of the polynomial conversions
#****************************************************************************/
def outputPolynomialConversions()
    isFirst = true
    
    # Step through each row in the table
    for row in 0..$ccdd.getStructureTableNumRows() - 1
        # Output the polynomial conversion for this row in the data table
        isFirst = outputPolynomialConversion(row, isFirst)
    end
end
# End defs ********************************************************************

# Main ************************************************************************
# Get the number of structure and command table rows
numStructRows = $ccdd.getStructureTableNumRows()
numCommandRows = $ccdd.getCommandTableNumRows()

# Check if no structure or command data is supplied
if numStructRows == 0 && numCommandRows == 0
    showErrorDialog("No structure or command data supplied to script " + $ccdd.getScriptName())
# Structure and/or command data is supplied
else
    # Define the radio button text and descriptions for the dialog
    buttons = [ [ "Big", "Big endian" ], \
                [ "Big (swap)", "Big endian (word swapped)" ], \
                [ "Little", "Little endian" ], \
                [ "Little (swap)", "Little endian (word swapped)" ] ]
    
    # Get the endianess choice from the user
    selected = $ccdd.getRadioButtonDialog("Select endianess", buttons)
    
    # Check that an endianess was selected
    if selected != nil
        # Check if the output should be big endian
        if selected == "Big"
            $endianess = "BIG_ENDIAN"
            endian_ext = "BE"
        # Check if the output should be big endian, word swapped
        elsif selected == "Big (swap)"
            $endianess = "BIG_ENDIAN_SWAP"
            endian_ext = "BE"
        # Check if the output is little endian
        elsif selected == "Little"
            $endianess = "LITTLE_ENDIAN"
            endian_ext = "LE"
        # Check if the output is little endian, word swapped
        elsif selected == "Little (swap)"
            $endianess = "LITTLE_ENDIAN_SWAP"
            endian_ext = "LE"
        end
        
        # Get the current date and time
        dateAndTime = $ccdd.getDateAndTime()
        
        # Check if structure data is provided
        if numStructRows > 0
            # Build the telemetry output file name
            tlmOutputFile = $ccdd.getRootStructureTableNames()[0] + "_" + endian_ext + ".rec"
            
            # Open the telemetry output file
            $tlmFile = $ccdd.openOutputFile(tlmOutputFile)
        
            # Check if the telemetry output file successfully opened
            if $tlmFile != nil
                # Get the names of the top-level structure and all
                # sub-structures referenced within it
                structureNames = $ccdd.getStructureTableNames()
            
                # Add a header to the output file
                $ccdd.writeToFileLn($tlmFile,
                                    "/* Created : " \
                                    + dateAndTime \
                                    + "\n   User    : " \
                                    + $ccdd.getUser() \
                                    + "\n   Project : " \
                                    + $ccdd.getProject() \
                                    + "\n   Script  : " \
                                    + $ccdd.getScriptName() \
                                    + "\n   Table(s): " \
                                    + structureNames.to_a.join(",\n             ") \
                                    + " */\n")
                                  
                # Output the telemetry packet description
                outputTelemetryPacket(structureNames)
                
                # Output the prototype structures
                outputPrototypeStructures(structureNames)
            
                # Output the discrete conversions
                outputTelemetryDiscreteConversions()
                 
                # Output the limit definitions
                outputLimitDefinitions()
                 
                # Output the polynomial conversions
                outputPolynomialConversions()
                 
                # Output the mnemonic definitions
                outputMnemonicDefinitions()
            
                # Close the output telemetry file
                $ccdd.closeFile($tlmFile)
            # The telemetry output file cannot be opened
            else
                # Display an error dialog
                $ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + tlmOutputFile + "<b>'")
            end
        end
        
        # Check if command data is provided
        if numCommandRows > 0
            # Build the command output file name
            cmdOutputFile = $ccdd.getTableDataFieldValue($ccdd.getRootCommandTableNames()[0], "system") + "_CMD" + "_" + endian_ext + ".rec"
            
            # Open the command output file
            $cmdFile = $ccdd.openOutputFile(cmdOutputFile)
        
            # Check if the command output file successfully opened
            if $cmdFile != nil
                # Add a header to the output file
                $ccdd.writeToFileLn($cmdFile,
                                   "/* Created : " \
                                   + dateAndTime \
                                   + "\n   User    : " \
                                   + $ccdd.getUser() \
                                   + "\n   Project : " \
                                   + $ccdd.getProject() \
                                   + "\n   Script  : " \
                                   + $ccdd.getScriptName() \
                                   + "\n   Table(s): " \
                                   + $ccdd.getCommandTableNames().to_a.join(",\n            ") \
                                   + " */\n")
                
                # Output the enumerations
                outputEnumerations()
                
                # Output the discrete conversions
                outputCommandDiscreteConversions()

                # Output the commands
                outputCommands()
                
                # Close the command output file
                $ccdd.closeFile($cmdFile)
            # The command output file cannot be opened
            else
                # Display an error dialog
                $ccdd.showErrorDialog("<html><b>Error opening command output file '</b>" + cmdOutputFile + "<b>'")
            end
        end
    end          
end
