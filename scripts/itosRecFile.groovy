/******************************************************************************
 * Description: Output an ITOS record file
 *
 * This Groovy script generates an ITOS record file from the supplied table
 * and packet information
 *****************************************************************************/
import CCDD.CcddScriptDataAccessHandler

tlmFile = null
cmdFile = null
endianess = null

/** Functions ****************************************************************/

/******************************************************************************
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
 *****************************************************************************/
def isVariable(variableName, arraySize)
{
    // Only output non-array variables or array members (i.e., skip array
    // definitions)
    return variableName != null && arraySize != null && (arraySize.isEmpty() || variableName.endsWith("]"))
}

/******************************************************************************
 * Convert an array member variable name by replacing left square brackets with
 * underscores and removing right square brackets (example: a[2] becomes a_2)
 *
 * @param variableName
 *            variable name
 *
 * @return Variable name with the square brackets replaced
 *****************************************************************************/
def convertArrayMember(variableName)
{
    return variableName.replaceAll("[\\[]", "_").replaceAll("[\\]]", "")
}

/******************************************************************************
 * Build the command enumeration name
 * 
 * @param row
 *            row index in the command data table
 * 
 * @param argumentNum
 *            command argument number
 *            
 * @return Command enumeration name
 *****************************************************************************/
def getCommandEnumerationName(row, argumentNum)
{
    return ccdd.getCommandTableData("command name", row) +
           "_" +
           ccdd.getCommandTableData("arg " + argumentNum + " name", row) +
           "_ENUMERATION"
}

/******************************************************************************
 * Output a telemetry packet or prototype structure definition
 *
 * @param structureName
 *            structure name
 *
 * @param isPacket
 *            true if this is a telemetry packet definition; false for a
 *            prototype structure definition
 *****************************************************************************/
def outputStructureDefinition(structureName, isPacket)
{
    def termLine = false
    List<String> usedVariableNames = []

    // Step through each row in the table
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Check that this row references a variable in the prototype structure
        if (ccdd.getStructureTableNameByRow(row).equals(structureName))
        {
            // Get the variable name for this row
            def variableName = ccdd.getStructureTableData("variable name", row)

            // Check if the variable name hasn't already been processed
            // this is necessary to prevent duplicating the variables in
            // the prototype structure for a structure that is referenced
            // as an array
            if (!usedVariableNames.contains(variableName))
            {
                // Add the variable name to the list of those already processed
                usedVariableNames.push(variableName)

                // Get the array size for this row
                def arraySize = ccdd.getStructureTableData("array size", row)

                // Only output non-array variables or array members (i.e., skip
                // array definitions)
                if (isVariable(variableName, arraySize))
                {
                    // Get the ITOS encoded form of the data type
                    def itosEncode = ccdd.getITOSEncodedDataType(ccdd.getStructureTableData("data type", row), endianess)
        
                    // Check if this is not the first pass
                    if (termLine)
                    {
                        // Check if this is the packet definition
                        if (isPacket)
                        {
                            // Terminate the previous line with a comma
                            ccdd.writeToFileLn(tlmFile, ",")
                        }
                        // This is a prototype structure
                        else
                        {
                            // Terminate the previous line with a line feed
                            ccdd.writeToFileLn(tlmFile, "")
                        }
                    }
    
                    termLine = true
    
                    // In case this is an array member replace the square
                    // brackets
                    variableName = convertArrayMember(variableName)
    
                    // Get the length in bits for this row
                    def bitLength = ccdd.getStructureTableData("bit length", row)

                    def otherParameters = ""
                    
                    // Check if the length in bits is specified
                    if (bitLength != null && !bitLength.isEmpty())
                    {
                        // Add the length in bits parameter
                        otherParameters = "lengthInBits=" + bitLength
                    }
                    
                    // Check if this variable is a primitive data type and not a
                    // structure
                    if (itosEncode != null)
                    {
                        // Check if other parameters have been defined
                        if (otherParameters)
                        {
                            // Add a space to separate the parameters
                            otherParameters += " "
                        }
                        
                        // Add the 'no mnemonic' parameter
                        otherParameters += "generateMnemonic=\"no\""
                    }
                        
                    // Create the parameter definition
                    ccdd.writeToFile(tlmFile,
                                     "  " +
                                     (itosEncode == null ? ccdd.getStructureTableData("data type", row) : itosEncode) +
                                     " " +
                                     structureName +
                                     "_" +
                                     variableName +
                                     " {" +
                                     otherParameters +
                                     "}")
                }
            }
        }
    }
}

/******************************************************************************
 * Output the telemetry packet definition
 *
 * @param structureNames
 *            array of all structure table names
 *****************************************************************************/
def outputTelemetryPacket(structureNames)
{
    // Begin the packet definition
    ccdd.writeToFileLn(tlmFile, "CfeTelemetryPacket " + ccdd.getRootStructureTableNames()[0])
    ccdd.writeToFileLn(tlmFile, "{")
    ccdd.writeToFileLn(tlmFile,
                       "  applyWhen={FieldInRange{field = applicationId, range = " +
                       ccdd.getTableDataFieldValue(ccdd.getRootStructureTableNames()[0], "Message ID").replaceFirst("0x[0-9]{2}", "0x") +
                       "}},")
    
    // Build the packet definition
    outputStructureDefinition(structureNames[0], true)
    
    // End the packet definition
    ccdd.writeToFileLn(tlmFile, "")
    ccdd.writeToFileLn(tlmFile, "}")
}

/******************************************************************************
 * Output the prototype structures
 *
 * @param structureNames
 *            array of all structure table names
 *****************************************************************************/
def outputPrototypeStructures(structureNames)
{
    // Step through each structure name
    for (def index = 0; index < structureNames.length; index++)
    {
        // Check that this isn't the top-level structure (i.e., it's a
        // referenced structure)
        if (!structureNames[index].equals(ccdd.getRootStructureTableNames()[0]))
        {
            // Begin the prototype structure definition
            ccdd.writeToFileLn(tlmFile, "")
            ccdd.writeToFileLn(tlmFile, "prototype Structure " + structureNames[index])
            ccdd.writeToFileLn(tlmFile, "{")

            // Build the prototype structure definition
            outputStructureDefinition(structureNames[index], false)

            // End the prototype structure definition
            ccdd.writeToFileLn(tlmFile, "")
            ccdd.writeToFileLn(tlmFile, "}")
        }
    }
}

/******************************************************************************
 * Output the commands
 *****************************************************************************/
def outputCommands()
{
    // Step through each row in the command table
    for (def row = 0; row < ccdd.getCommandTableNumRows(); row++)
    {
        // Get the application ID data field value
        def applicationID = ccdd.getTableDataFieldValue(ccdd.getRootCommandTableNames()[0], "application id")
        
        // Check if the application ID data field doesn't exist
        if (applicationID == null)
        {
            // Set the application ID to a blank
            applicationID = ""
        }
        
        // Begin the command definition
        ccdd.writeToFileLn(cmdFile, "")
        ccdd.writeToFileLn(cmdFile, "CfeSoftwareCommand " + ccdd.getCommandTableData("command name", row))
        ccdd.writeToFileLn(cmdFile, "{")
        ccdd.writeToFileLn(cmdFile, "  applicationID {range = " + applicationID + "}")
        ccdd.writeToFileLn(cmdFile, "  commandCode {range = " + ccdd.getCommandTableData("command code", row) + "}")

        def argumentNum = 1
        def name = " "
        
        // Process all of the command arguments for this command
        while (name != null)
        {
            // Build the prefix for the command argument column names
            def argument = "arg " + argumentNum + " "
        
            // Get the command argument's name, data type, and enumeration values
            name = ccdd.getCommandTableData(argument + "name", row)
            def dataType = ccdd.getCommandTableData(argument + "data type", row)
            def enumeration = ccdd.getCommandTableData(argument + "enumeration", row)
            
            // Check if the parameter has an argument
            if (name != null && !name.isEmpty()
                && dataType != null && !dataType.isEmpty())
            {
                // Get the ITOS encoded form of the data type
                def itosEncode = ccdd.getITOSEncodedDataType(dataType, endianess)

                // Get the single character ITOS encoded form of the data type
                def itosEncodeChar = ccdd.getITOSEncodedDataType(dataType, "SINGLE_CHAR")

                def argumentInfo = ""
                
                // Check if the parameter is an integer (signed or unsigned)
                if (itosEncodeChar.equals("I") || itosEncodeChar.equals("U"))
                {
                    // Get the command argument's minimum and maximum values
                    String minimumValueS = ccdd.getCommandTableData(argument + "minimum", row)
                    String maximumValueS = ccdd.getCommandTableData(argument + "maximum", row)

                    // Check if this command has an enumeration
                    if (enumeration != null && !enumeration.isEmpty())
                    {
                        // Add the associated enumeration definition
                        argumentInfo += "enumeration = " +  getCommandEnumerationName(row, argumentNum) + ", "
                    }
                    
                    // The get size in bytes based on the data type
                    def sizeInBytes = ccdd.getDataTypeSizeInBytes(dataType)
                    
                    // Check that the argument has a valid data type
                    if (sizeInBytes != 0)
                    {
                        // Check if a minimum value doesn't exist for this argument
                        if (minimumValueS == null || minimumValueS.isEmpty())
                        {
                            // Set the minimum value to zero, assuming this is
                            // an unsigned integer
                            Integer minimumValue = 0
                            
                            // Check if the argument is a signed integer
                            if (itosEncodeChar.equals("I"))
                            {
                                // Set the minimum value to the largest negative
                                // value for this size integer
                                minimumValue = -Math.pow(2, sizeInBytes * 8) / 2
                            }
                            
                            // Convert the minimum value to a string
                            minimumValueS = String.valueOf(minimumValue);
                        }
                        
                        // Check if a maximum value doesn't exist for this argument
                        if (maximumValueS == null || maximumValueS.isEmpty())
                        {
                            // Set the maximum value to the largest positive value
                            // for an unsigned integer
                            Integer maximumValue = Math.pow(2, sizeInBytes * 8)
                            
                            // Check if the argument is a signed integer
                            if (itosEncodeChar.equals("I"))
                            {
                                // Adjust the maximum to the largest size for this
                                // size integer
                                maximumValue -= maximumValue / 2 + 1
                            }
                            
                            // Convert the maximum value to a string
                            maximumValueS = String.valueOf(maximumValue);
                        }
                    }
                    
                    // Add the command argument range
                    argumentInfo += "range = " + minimumValueS + ".." + maximumValueS
                }
                // Check if the parameter is a floating point value
                else if (itosEncodeChar.equals("F"))
                {
                    // Nothing to show for a float or double
                }
                // Check if the parameter is a string
                else if (itosEncodeChar.equals("S"))
                {
                    argumentInfo = "lengthInCharacters = "; // TODO Need # of characters
                }
            
                // Output the command argument to the file
                ccdd.writeToFileLn(cmdFile, "  " + itosEncode + " " + name + " {" + argumentInfo + "}")
            }
            
            // Advance to the next argument number
            argumentNum++
        }
        
        ccdd.writeToFileLn(cmdFile, "}")
    }
}

/******************************************************************************
 * Output a single mnemonic definition
 *
 * @param row
 *            row index in the structure data table
 *****************************************************************************/
def outputMnemonicDefinition(row)
{
    // Get the single character ITOS encoded form of the data type
    def itosEncode = ccdd.getITOSEncodedDataType(ccdd.getStructureTableData("data type", row), "SINGLE_CHAR")

    // Check if this data type is a recognized type
    if (itosEncode != null)
    {
        // Get the variable name and array size
        def variableName = ccdd.getStructureTableData("variable name", row)
        def arraySize = ccdd.getStructureTableData("array size", row)

        // Only output non-array variables or array members (i.e., skip array
        // definitions)
        if (isVariable(variableName, arraySize))
        {
            // Get the variable's structure path
            def structurePath = ccdd.getStructureTableITOSPathByRow(row)
                        
            // In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            // Get the variable's structure name
            def structureName = ccdd.getStructureTableNameByRow(row)

            // Get the full variable name for this variable, which includes all
            // of the variable names in its structure path
            def fullVariableName = ccdd.getFullVariableName(row)

            // Create the mnemonic definition
            ccdd.writeToFile(tlmFile,
                             itosEncode +
                             " " +
                             fullVariableName +
                             " {sourceFields = {" +
                             ccdd.getRootStructureTableNames()[0] +
                             "." +
                             structurePath +
                             "_" +
                             variableName)
                              
            ccdd.writeToFile(tlmFile, "}")

            // Get the enumeration, polynomial conversion, and limit columns (if extant)
            def enumeration = ccdd.getStructureTableData("enumeration", row)
            def polynomial = ccdd.getStructureTableData("polynomial coefficients", row)
            def limitSet = ccdd.getStructureTableData("limit sets", row)
            
            // Check if this parameter includes a discrete or polynomial
            // conversion
            if ((enumeration != null && !enumeration.isEmpty())
                || (polynomial != null && !polynomial.isEmpty()))
            {
                // Output the conversion reference
                ccdd.writeToFile(tlmFile,
                                 " conversion = " + fullVariableName + "_CONVERSION")
            }

            // Check if this parameter includes a limit or limit set
            if (limitSet != null && !limitSet.isEmpty())
            {
                // Output the limit reference
                ccdd.writeToFile(tlmFile,
                                 " limits = " + fullVariableName + "_LIMIT")
            }

            ccdd.writeToFileLn(tlmFile, "}")
        }
    }
}

/******************************************************************************
 * Output all of the mnemonic definitions
 *****************************************************************************/
def outputMnemonicDefinitions()
{
    ccdd.writeToFileLn(tlmFile, "")
    ccdd.writeToFileLn(tlmFile, "/* Mnemonic Definitions */")
    
    // Step through each row in the table
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Output the mnemonic definition for this row in the data table
        outputMnemonicDefinition(row)
    }
}

/******************************************************************************
 * Output a single discrete conversion (enumeration)
 *
 * @param file
 *            file to which to write the discrete conversion
 *
 * @param discreteConversion
 *            discrete conversion information
 *
 * @param conversionName
 *            conversion name
 *****************************************************************************/
def outputDiscreteConversion(file, discreteConversion, conversionName)
{
    // Discrete conversion array indices
    def VALUE = 0
    def DISP_NAME = 1
    def TEXT_COLOR = 2
    def BACK_COLOR = 3

    // Separate the enumerated parameters into an array. The expected
    // format for the enumerated values is:
    // <discrete value> | <Name> | <Display Name> | <Text Color> |
    // <Background Color> [, repeat for each discrete value...]
    def enumerations = ccdd.getArrayFromString(discreteConversion, "|", ",")

    // Check if the variable has enumerations
    if (enumerations != null)
    {
        // Output the discrete conversion header
        ccdd.writeToFileLn(file,
                           "DiscreteConversion " + conversionName + "_CONVERSION")
        ccdd.writeToFileLn(file, "{")

        // Step through each enumerated value
        for (def discrete = 0; discrete < enumerations.length; discrete++)
        {
            // Output the discrete conversion
            ccdd.writeToFile(file,
                             "  Dsc " + enumerations[discrete][DISP_NAME] + " {range = " + enumerations[discrete][VALUE])
            
            // Check if a background color is supplied
            if (!enumerations[discrete][BACK_COLOR].isEmpty())
            {
                // Output the background color
                ccdd.writeToFile(file, ", bgColor = " + enumerations[discrete][BACK_COLOR])
            }
            
            // Check if a foreground (text) color is supplied
            if (!enumerations[discrete][TEXT_COLOR].isEmpty())
            {
                // Output the foreground color
                ccdd.writeToFile(file, ", fgColor = " + enumerations[discrete][TEXT_COLOR])
            }
            
            ccdd.writeToFileLn(file, "}")
        }

        ccdd.writeToFileLn(file, "}")
    }
}

/******************************************************************************
 * Output all of the telemetry discrete conversions
 *****************************************************************************/
def outputTelemetryDiscreteConversions()
{
    def isFirst = true

    // Step through each row in the structure table
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Get the discrete conversion for this parameter
        def discreteConversion = ccdd.getStructureTableData("enumeration", row)
        
        // Check if the parameter has a discrete conversion
        if (discreteConversion != null && !discreteConversion.isEmpty())
        {
            // Check if this is the first discrete conversion
            if (isFirst)
            {
                // Write the discrete conversion header to the file
                ccdd.writeToFileLn(tlmFile, "")
                ccdd.writeToFileLn(tlmFile, "/* Discrete Conversions */")
                isFirst = false
            }
            
            // Get the variable name and array size
            def variableName = ccdd.getStructureTableData("variable name", row)
            def arraySize = ccdd.getStructureTableData("array size", row)

            // Only output non-array variables or array members (i.e., skip
            // array definitions)
            if (isVariable(variableName, arraySize))
            {
                // Get the full name and path for the variable on this row
                def fullVariableName = ccdd.getFullVariableName(row)
                
                // Output the discrete conversion for this row in the data table
                outputDiscreteConversion(tlmFile, discreteConversion, fullVariableName)
            }
        }
    }
}

/******************************************************************************
 * Output all of the command discrete conversions
 *****************************************************************************/
def outputCommandDiscreteConversions()
{
    // Step through each row in the command table
    for (def row = 0; row < ccdd.getCommandTableNumRows(); row++)
    {
        def argumentNum = 1
        def discreteConversion = ""
        
        // Process each discrete conversion
        while (discreteConversion != null)
        {
            // Get the discrete conversion for this command based on the
            // argument number. Null is returned if no match is found for the
            // column name; it's assumed that no more argument columns exists
            // for this command
            discreteConversion = ccdd.getCommandTableData("arg " + argumentNum + " enumeration", row)
            
            // Check if the parameter has a discrete conversion
            if (discreteConversion != null && !discreteConversion.isEmpty())
            {
                // Check if this is the first discrete conversion
                if (argumentNum == 1)
                {
                    // Write the discrete conversions header to the file
                    ccdd.writeToFileLn(cmdFile, "")
                    ccdd.writeToFileLn(cmdFile, "/* Discrete Conversions */")
                }

                // Build the name for the conversion using the command and
                // argument names
                def fullCommandName = ccdd.getCommandTableData("command name", row) +
                                      "_" +
                                      ccdd.getCommandTableData("arg " + argumentNum + " name", row)
                
                // Output the discrete conversion for this row in the data table
                outputDiscreteConversion(cmdFile, discreteConversion, fullCommandName)
            }
            
            // Advance to the next argument number
            argumentNum++
        }
    }
}

/******************************************************************************
 * Output a single enumeration
 *
 * @param enumeration
 *            enumeration information
 *
 * @param conversionName
 *            conversion name
 *****************************************************************************/
def outputEnumeration(enumeration, enumerationName)
{
    // Enumeration array indices
    def VALUE = 0
    def DISP_NAME = 1

    // Separate the enumerated parameters into an array. The expected
    // format for the enumerated values is:
    // <discrete value> | <Name> | <Display Name> | <Text Color> |
    // <Background Color> [, repeat for each discrete value...]
    def enumerations = ccdd.getArrayFromString(enumeration, "|", ",")

    // Check if the variable has enumerations
    if (enumerations != null)
    {
        // Output the enumeration header
        ccdd.writeToFileLn(cmdFile,
                           "Enumeration " + enumerationName)
        ccdd.writeToFileLn(cmdFile, "{")

        // Step through each enumerated value
        for (def discrete = 0; discrete < enumerations.length; discrete++)
        {
            // Output the enumerated value
            ccdd.writeToFile(cmdFile,
                             "  EnumerationValue " + enumerations[discrete][DISP_NAME] + " {value = " + enumerations[discrete][VALUE])
            
            ccdd.writeToFileLn(cmdFile, "}")
        }

        ccdd.writeToFileLn(cmdFile, "}")
    }
}

/******************************************************************************
 * Output all of the command enumerations
 *****************************************************************************/
def outputEnumerations()
{
    // Step through each row in the command table
    for (def row = 0; row < ccdd.getCommandTableNumRows(); row++)
    {
        def argumentNum = 1
        def enumeration = ""
        
        // Process each enumeration
        while (enumeration != null)
        {
            // Get the enumeration for this command based on the argument
            // number. Null is returned if no match is found for the column
            // name; it's assumed that no more argument columns exists
            // for this command
            enumeration = ccdd.getCommandTableData("arg " + argumentNum + " enumeration", row)
            
            // Check if the parameter has an enumeration
            if (enumeration != null && !enumeration.isEmpty())
            {
                // Check if this is the first enumeration
                if (argumentNum == 1)
                {
                    // Write the enumerations header to the file
                    ccdd.writeToFileLn(cmdFile, "")
                    ccdd.writeToFileLn(cmdFile, "/* Enumerations */")
                }
                
                // Build the name for the enumeration using the command and
                // argument names
                def enumerationName = getCommandEnumerationName(row, argumentNum);
                
                // Output the enumeration for this row in the data table
               outputEnumeration(enumeration, enumerationName)
            }
            
            // Advance to the next argument number
            argumentNum++
        }
    }
}

/******************************************************************************
 * Output a single limit or limit set definition
 *
 * @param row
 *            row index in the structure data table
 *
 * @param isFirst
 *            true if this is the first limit definition
 *
 * @returns false if a limit definition is output
 *****************************************************************************/
def outputLimitDefinition(row, isFirst)
{
    // Get the limits for this row
    def limitSets = ccdd.getStructureTableData("limit sets", row)

    // Check if the parameter has limits
    if (limitSets != null && !limitSets.isEmpty())
    {
        // Get the variable name and array size
        def variableName = ccdd.getStructureTableData("variable name", row)
        def arraySize = ccdd.getStructureTableData("array size", row)

        // Only output non-array variables or array members (i.e., skip
        // array definitions)
        if (isVariable(variableName, arraySize))
        {
            // In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName)

            // Separate the limits into an array
            def limits = ccdd.getArrayFromString(limitSets, "|", ",")

            // Check if the variable has limits
            if (limits != null)
            {
                // Check if this is the first limit definition
                if (isFirst)
                {
                    // Write the limit definitions header to the file
                    ccdd.writeToFileLn(tlmFile, "")
                    ccdd.writeToFile(tlmFile, "/* Limit Definitions */")
                    isFirst = false
                }

                // Check if a single limit is specified
                if (limits.length == 1)
                {
                    // Output the limit header
                    ccdd.writeToFileLn(tlmFile, "")
                    ccdd.writeToFileLn(tlmFile,
                                       "Limit " + ccdd.getFullVariableName(row) + "_LIMIT")
                    ccdd.writeToFileLn(tlmFile, "{")

                    // Step through each limit definition
                    for (def index = 0; index < limits[0].length; index ++)
                    {
                        // Check if this is is the red-low, yellow-low,
                        // yellow-high, or red-high limit
                        if (index < 4 && !limits[0][index].isEmpty())
                        {
                            // Output the limit
                            ccdd.writeToFileLn(tlmFile,
                                               "  " + ccdd.getITOSLimitName(index) + " = " + limits[0][index])
                        }
                    }
                    
                    ccdd.writeToFileLn(tlmFile, "}")
                }
                // Multiple limits are specified
                else if (limits.length > 1)
                {
                    // Output the limit set header
                    ccdd.writeToFileLn(tlmFile, "")
                    ccdd.writeToFileLn(tlmFile,
                                       "LimitSet " + ccdd.getFullVariableName(row) + "_LIMIT")
                    ccdd.writeToFileLn(tlmFile, "{")
                    ccdd.writeToFileLn(tlmFile, "  contextMnemonic = " + limits[0][0])
                    ccdd.writeToFileLn(tlmFile, "")
    
                    // Step through each limit set
                    for (def set = 1; set < limits.length; set++)
                    {
                        // Check if this is not the first limit value
                        if (set != 1)
                        {
                            // Output a line feed
                            ccdd.writeToFileLn(tlmFile, "")
                        }
                        
                        // Output the limit header
                        ccdd.writeToFileLn(tlmFile, "  Limit limit" + set)
                        ccdd.writeToFileLn(tlmFile, "  {")
        
                        def limitIndex = 0
                            
                        // Step through each limit definition
                        for (def index = 0; index < limits[set].length; index++)
                        {
                            // Check if the limit value exists
                            if (!limits[set][index].isEmpty())
                            {
                                // Check if this is the context range
                                if (limits[set][index].contains(".."))
                                {
                                    // Output the context range
                                    ccdd.writeToFileLn(tlmFile,
                                                       "    contextRange = " + limits[set][index])
                                }
                                // Not the context range; must be a limit value
                                else
                                {
                                    // Output the limit value
                                    ccdd.writeToFileLn(tlmFile,
                                                       "    " +
                                                       ccdd.getITOSLimitName(limitIndex) +
                                                       " = " +
                                                       limits[set][index])
                                    
                                    limitIndex++
                                }
                            }
                        }
                        
                        ccdd.writeToFileLn(tlmFile, "  }")
                    }
                    
                    ccdd.writeToFileLn(tlmFile, "}")
                }
            }
        }
    }

    return isFirst
}

/******************************************************************************
 * Output all of the limit and limit set definitions
 *****************************************************************************/
def outputLimitDefinitions()
{
    def isFirst = true
    
    // Step through each row in the table
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Output the limit definition for this row in the data table
        isFirst = outputLimitDefinition(row, isFirst)
    }
}

/******************************************************************************
 * Output a single polynomial conversion
 *
 * @param row
 *            row index in the structure data table
 *
 * @param isFirst
 *            true if this is the first polynomial conversion
 *
 * @returns false if a polynomial conversion is output
 *****************************************************************************/
def outputPolynomialConversion(row, isFirst)
{
    // Get the polynomial coefficients for this row
    def polynomialCoefficients = ccdd.getStructureTableData("polynomial coefficients", row)

    // Check if the parameter has polynomial coefficients
    if (polynomialCoefficients != null && !polynomialCoefficients.isEmpty())
    {
        // Check if this is the first polynomial conversion
        if (isFirst)
        {
            // Write the polynomial conversion header to the file
            ccdd.writeToFileLn(tlmFile, "")
            ccdd.writeToFile(tlmFile, "/* Polynomial Conversions */")
            isFirst = false
        }
        
        // Get the variable name and array size
        def variableName = ccdd.getStructureTableData("variable name", row)
        def arraySize = ccdd.getStructureTableData("array size", row)

        // Only output non-array variables or array members (i.e., skip
        // array definitions)
        if (isVariable(variableName, arraySize))
        {
            // Separate the polynomial coefficients into an array
            def coeffs = ccdd.getArrayFromString(polynomialCoefficients, "|")

            // Output the polynomial conversion header
            ccdd.writeToFileLn(tlmFile, "")
            ccdd.writeToFileLn(tlmFile,
                               "PolynomialConversion " + ccdd.getFullVariableName(row) + "_CONVERSION")
            ccdd.writeToFileLn(tlmFile, "{")
            ccdd.writeToFile(tlmFile, "  coefficients = {")

            addComma = false
            
            // Step through each coefficient value
            for (def index = 0; index < coeffs.length; index++)
            {
                // Check if this is not the first pass
                if (addComma)
                {
                    // Append a comma
                    ccdd.writeToFile(tlmFile, ", ")
                }

                addComma = true

                // Output the polynomial coefficient
                ccdd.writeToFile(tlmFile, coeffs[index])
            }

            ccdd.writeToFileLn(tlmFile, "}")
            ccdd.writeToFileLn(tlmFile, "}")
        }
    }
    
    return isFirst
}

/******************************************************************************
 * Output all of the polynomial conversions
 *****************************************************************************/
def outputPolynomialConversions()
{
    def isFirst = true
    
    // Step through each row in the table
    for (def row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Output the polynomial conversion for this row in the data table
        isFirst = outputPolynomialConversion(row, isFirst)
    }
}
/** End functions ************************************************************/

/** Main *********************************************************************/
// Get the number of structure and command table rows
def numStructRows = ccdd.getStructureTableNumRows()
def numCommandRows = ccdd.getCommandTableNumRows()

// Check if no structure or command data is supplied
if (numStructRows == 0 && numCommandRows == 0)
{
    showErrorDialog("No structure or command data supplied to script " + ccdd.getScriptName())
}
// Structure and/or command data is supplied
else
{
    // Define the radio button text and descriptions for the dialog
    String[][] buttons = [ [ "Big", "Big endian" ],
                    [ "Big (swap)", "Big endian (word swapped)" ],
                    [ "Little", "Little endian" ],
                    [ "Little (swap)", "Little endian (word swapped)" ]];
    
    // Get the endianess choice from the user
    def selected = ccdd.getRadioButtonDialog("Select endianess", buttons);
    
    // Check that an endianess was selected
    if (selected != null)
    {
        // Check if the output should be big endian
        if (selected.equals("Big"))
        {
            endianess = "BIG_ENDIAN"
            endian_ext = "BE"
        }
        // Check if the output should be big endian, word swapped
        else if (selected.equals("Big (swap)"))
        {
            endianess = "BIG_ENDIAN_SWAP"
            endian_ext = "BE"
        }
        // Check if the output is little endian
        else if (selected.equals("Little"))
        {
            endianess = "LITTLE_ENDIAN"
            endian_ext = "LE"
        }
        // Check if the output is little endian, word swapped
        else if (selected.equals("Little (swap)"))
        {
            endianess = "LITTLE_ENDIAN_SWAP"
            endian_ext = "LE"
        }

        // Get the current date and time
        def dateAndTime = ccdd.getDateAndTime()
        
        // Check if structure data is provided
        if (numStructRows > 0)
        {
            // Build the telemetry output file name
            def tlmOutputFile = ccdd.getRootStructureTableNames()[0] + "_" + endian_ext + ".rec"
            
            // Open the telemetry output file
            tlmFile = ccdd.openOutputFile(tlmOutputFile)
        
            // Check if the telemetry output file successfully opened
            if (tlmFile != null)
            {
                // Get the names of the top-level structure and all
                // sub-structures referenced within it
                def structureNames = ccdd.getStructureTableNames()
            
                // Add a header to the output file
                ccdd.writeToFileLn(tlmFile,
                                   "/* Created : "
                                   + dateAndTime
                                   + "\n   User    : "
                                   + ccdd.getUser()
                                   + "\n   Project : "
                                   + ccdd.getProject()
                                   + "\n   Script  : "
                                   + ccdd.getScriptName()
                                   + "\n   Table(s): "
                                   + structureNames.join(",\n             ")
                                   + " */\n")
                                   
                // Output the telemetry packet description
                outputTelemetryPacket(structureNames)
                
                // Output the prototype structures
                outputPrototypeStructures(structureNames)
            
                // Output the discrete conversions
                outputTelemetryDiscreteConversions()
                 
                // Output the limit definitions
                outputLimitDefinitions()
                 
                // Output the polynomial conversions
                outputPolynomialConversions()
                 
                // Output the mnemonic definitions
                outputMnemonicDefinitions()
            
                // Close the output telemetry file
                ccdd.closeFile(tlmFile)
            }
            // The telemetry output file cannot be opened
            else
            {
                // Display an error dialog
                ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + tlmOutputFile + "<b>'")
            }
        }
        
        // Check if command data is provided
        if (numCommandRows > 0)
        {
            // Build the command output file name
            def cmdOutputFile = ccdd.getTableDataFieldValue(ccdd.getRootCommandTableNames()[0], "system") + "_CMD" + "_" + endian_ext + ".rec"
            
            // Open the command output file
            cmdFile = ccdd.openOutputFile(cmdOutputFile)
        
            // Check if the command output file successfully opened
            if (cmdFile != null)
            {
                // Add a header to the output file
                ccdd.writeToFileLn(cmdFile,
                                   "/* Created : "
                                   + dateAndTime
                                   + "\n   User    : "
                                   + ccdd.getUser()
                                   + "\n   Project : "
                                   + ccdd.getProject()
                                   + "\n   Script  : "
                                   + ccdd.getScriptName()
                                   + "\n   Table(s): "
                                   + ccdd.getCommandTableNames().join(",\n            ")
                                   + " */\n")
                
                // Output the enumerations
                outputEnumerations()
                
                // Output the discrete conversions
                outputCommandDiscreteConversions()

                // Output the commands
                outputCommands()
                
                // Close the command output file
                ccdd.closeFile(cmdFile)
            }
            // The command output file cannot be opened
            else
            {
                // Display an error dialog
                ccdd.showErrorDialog("<html><b>Error opening command output file '</b>" + cmdOutputFile + "<b>'")
            }
        }
    }
}
