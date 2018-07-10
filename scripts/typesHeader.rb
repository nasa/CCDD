#******************************************************************************
# Description: Output a structure types header file
#
# This Ruby script generates a structure types header file from the
# supplied structure table(s). A companion source code file is also generated
# that provides byte- and bit-swapping functions for the structures
#
# Assumptions: The structure tables use "Description" for the description
# column (case insensitive). If the structure has a non-empty data field named
# "Message ID" then it is assumed to require a CCSDS header which is
# automatically added. If a table containing extra text to include is provided
# then its table type is "Includes" and has the column "Includes". The output
# file names are prepended with a name taken from a data field, "System", found
# either in the first group associated with the script, or, if not found there
# then in the first structure table associated with the script; if no "System"
# data field exists or is empty the name is blank. The project's data type
# definitions are output to the types header file
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

java_import Java::CCDD.CcddScriptDataAccessHandler

# Get the array of structure names by the order in which they are referenced
$structureNames = $ccdd.getStructureTablesByReferenceOrder()

# Get the total number of structure table rows
$numStructRows = $ccdd.getStructureTableNumRows()

# Get an array containing the data stream names
$dataStreams = $ccdd.getDataStreamNames()

#** Functions *****************************************************************

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

  $ccdd.writeToFileLn(file, "*/\n")
end

#******************************************************************************
# Output a structure's type definition to the specified file
#
# @param file
#            reference to the types header output file
#
# @param structIndex
#            index of the structure in the structure name array
#******************************************************************************
def outputStructure(file, structIndex)
    firstPass = true
    isCCSDS = false
    lastBitFieldType = "none"
    maxBitsAvailable = 0
    curFilledBits = 0
    headerOffset = 0
    usedVariableNames = []
    structDescription = ""
    structSize = 0

    # Set the minimum length required to display the structure information
    # using the structure name as the initial value. This value is used to
    # align variable (offset, byte size, rate(s), and description) and
    # structure (total size) comment text
    minimumLength = ("} " + $structureNames[structIndex] + "; ").length

    # A pass is made through the structure rows in order to determine the
    # longest one, character-wise, so that the output can be formatted. Step
    # through each structure data row
    for row in 0..$numStructRows - 1
        # Check if the structure name in the row matches the current structure
        if $structureNames[structIndex] == $ccdd.getStructureTableNameByRow(row)
            # Check if this is the first pass through the structure data
            if firstPass
                firstPass = false

                # Get the value of the structure's message ID data field
                msgID = $ccdd.getTableDataFieldValue($structureNames[structIndex], "Message ID")

                # Check if the structure table has a message ID
                if msgID != nil && !msgID.empty?
                    # Set the minimum length to that of the CCSDS header
                    # variable which will be added if the structure has a
                    # message ID
                    minimumLength = ("   char CFS_PRI_HEADER[6]; ").length
                end
            end

            # Get the variable name for this row
            variableName = $ccdd.getStructureVariableName(row)

            # Check that this isn't an array member; only array definitions
            # appear in the type definition
            if !variableName.end_with?("]")
                # Get the variable's array size
                arraySize = $ccdd.getStructureArraySize(row)

                # Check if the variable is an array
                if !arraySize.empty?
                    # Add the brackets that will appear around the array size.
                    # Multi-dimensional arrays have the individual dimensions
                    # separated by ', '; in the type definition each ', ' is
                    # replaced with '][' which is the same number of
                    # characters, so no further padding adjustment needs to
                    # be made here to account for them
                    arraySize += "[]"
                end

                # Get the variable's bit length
                bitLength = $ccdd.getStructureBitLength(row)

                # Check if the variable has a bit length
                if !bitLength.empty?
                    # Add the colon that will appear before the bit length
                    bitLength += ":"
                end

                # Determine the length of the variable definition by adding up
                # the individual parts
                defnLength = ("   " + $ccdd.getStructureDataType(row) + " " + variableName + arraySize + bitLength + "; ").length

                # Check if the length exceeds the minimum length found thus far
                if defnLength > minimumLength
                    # Store the new minimum length
                    minimumLength = defnLength
                end
            end
        end
    end

    firstPass = true

    # Step through each structure data row
    for row in 0..$numStructRows - 1
        deltaSize = 0

        # Check if the structure name in the row matches the target structure
        if $structureNames[structIndex] == $ccdd.getStructureTableNameByRow(row)
            # Get the variable name for this row in the structure
            variableName = $ccdd.getStructureVariableName(row)

            # Check if this is the first pass through the structure data
            if firstPass
                firstPass = false

                # Get the description for the current structure
                structDescription = $ccdd.getTableDescriptionByRow("Structure", row)

                # Get the size of the entire structure, in bytes
                structSize = $ccdd.getDataTypeSizeInBytes($structureNames[structIndex])

                # Get the value of the structure's message ID data field
                msgID = $ccdd.getTableDataFieldValue($structureNames[structIndex], "Message ID")

                # Check if the structure table has a message ID
                if msgID != nil && !msgID.empty?
                    # Set the flag to add in CCSDS primary and secondary
                    # headers
                    isCCSDS = true
                    structSize = structSize + 12
                end

                # Display the structure name, size, and description prior to
                # the structure's type definition
                $ccdd.writeToFile(file, "/* Structure: " + $structureNames[structIndex] + " (" + structSize.to_s + " bytes total)")

                # Check if the structure has a description
                if !structDescription.empty?
                    # Display the structure's description
                    $ccdd.writeToFile(file, "\n   Description: " + structDescription)
                end

                $ccdd.writeToFileLn(file, " */")

                # Begin the structure type definition
                $ccdd.writeToFileLn(file, "typedef struct")
                $ccdd.writeToFileLn(file, "{")

                # Check if CCSDS headers should be added
                if isCCSDS
                    # Set the CCSDS header length, which is used as the byte
                    # offset for the subsequent variables
                    headerOffset = 12

                    # Output the variable array that contains the primary
                    # header values
                    offsetStr = "0"
                    ccsdsVar = "   char CFS_PRI_HEADER[6];"
                    comment = "#CCSDS_PriHdr_t"
                    sizeString = "(6 bytes)"
                    $ccdd.writeToFileFormat(file, "%-" + minimumLength.to_s + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr)

                    # Output the variable array that contains the secondary
                    # header values
                    offsetStr = "6"
                    ccsdsVar = "   char CFS_SEC_HEADER[6];"
                    comment = "#CCSDS_CmdSecHdr_t"
                    $ccdd.writeToFileFormat(file, "%-" + minimumLength.to_s + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr)
                # No CCSDS header should be added
                else
                    # Set the variable byte offset to zero
                    headerOffset = 0
                end
            end

            # Check if this is not an array member (only array definitions are
            # output), and if the variable name hasn't already been processed
            # (the first instance of the structure is used to obtain the
            # information to create the type definition, so this is necessary
            # to prevent duplicating the members in the type definition if
            # more than one instance of the structure is present in the data)
            if !variableName.end_with?("]") && usedVariableNames.count(variableName) == 0
                # Add the variable name to the list of those already processed
                usedVariableNames.push(variableName)

                # Get the variable's data type, array size, and description
                dataType = $ccdd.getStructureDataType(row)
                arraySize = $ccdd.getStructureArraySize(row)
                description = $ccdd.getStructureDescription(row)

                # Determine the size of the variable, in bytes
                byteSize = $ccdd.getDataTypeSizeInBytes(dataType)

                # Build the variable's full path; this will be used to get the
                # structure's byte offset
                variablePath = $structureNames[structIndex] + "," + dataType + "." + variableName
                varOffset = 0

                bitLength = ""
                sizeString = "(" + byteSize.to_s + " bytes)"
                variableMsg = "   " + dataType + " " + variableName

                # Check if the structure has no variable description column
                if description == nil
                    # Set the description to a blank
                    description = ""
                end

                # Check if the array size is provided; i.e., this is an array
                # definition
                if !arraySize.empty?
                    firstDim = ""
                    sizeMsg = ""
                    deltaSize = 1

                    # Separate the array size into the individual dimensions
                    dimensions = arraySize.split(", ")

                    # Step through each dimension in the array
                    for dim in 0..dimensions.length - 1
                        # Add a dimension for the first array member
                        firstDim += "[0]"

                        # Keep a running total of this dimension's byte
                        # requirements
                        deltaSize *= dimensions[dim].to_i

                        # Update the comment text that will follow the array
                        # definition
                        sizeMsg += dimensions[dim] + "x"
                    end

                    # Get the total byte size of the array
                    deltaSize *= byteSize

                    # Get the byte offset of the first member of this array
                    # variable within its structure
                    varOffset = $ccdd.getVariableOffset(variablePath + firstDim)

                    # Create the array variable definition, placing brackets
                    # around the array dimensions
                    variableMsg = variableMsg + "[" + arraySize.gsub(", ", "][") + "]"

                    # Build the comment that shows the array's byte size
                    sizeString = "(" + sizeMsg + byteSize.to_s + "=" + deltaSize.to_s + " bytes)"

                    lastBitFieldType = "none"
                # No array size for this row; i.e., the variable is not an
                # array definition
                else
                    deltaSize = byteSize

                    # Get the byte offset of the this variable within its
                    # structure
                    varOffset = $ccdd.getVariableOffset(variablePath)

                    # Get the variable's bit length
                    bitLength = $ccdd.getStructureBitLength(row)

                    # Check if the bit length is provided
                    if !bitLength.empty?
                        # Append the bit length to the variable
                        variableMsg = variableMsg + ":" + bitLength
                        sizeString = ""

                        # Check if the variable won't pack with the preceding
                        # variable(s) due to being a different data type or
                        # exceeding the bit length of the data type
                        if lastBitFieldType != dataType || (curFilledBits + bitLength.to_i > maxBitsAvailable)
                            # Reset the bit packing values
                            curFilledBits = bitLength.to_i
                            lastBitFieldType = dataType
                            maxBitsAvailable = 8 * byteSize
                        # The variable has the same data type and its bits
                        # will pack with the preceding variable(s)
                        else
                            # Add this variable's bits to the current pack
                            curFilledBits = curFilledBits + bitLength.to_i
                        end
                    # The variable has no bit length
                    else
                        lastBitFieldType = "none"
                    end
                end

                # Terminate the variable definition then pad it with spaces to
                # align the comment text
                variableMsg += ";"

                # Adjust the variable's byte offset within the structure to
                # include the header (if present)
                varOffset = varOffset + headerOffset

                rateInfo = ""

                # Step through each data stream
                for dataStream in 0..$dataStreams.length - 1
                    # Get the variable's rate for this data stream
                    rateValue = $ccdd.getStructureTableData($dataStreams[dataStream], row)

                    # Check if the variable has a rate assigned in this stream
                    if rateValue != nil && !rateValue.empty?
                        # Build the rate information
                        rateInfo += "{" + $dataStreams[dataStream] + " @" + rateValue + " Hz}"
                    end
                end

                # Build the full variable definition, along with the byte
                # offset, size, rate, and description information, then
                # output it to the types header file
                $ccdd.writeToFileFormat(file, "%-" + minimumLength.to_s + "s /* [%5s] " + (sizeString + rateInfo + "  " + description).strip() + " */\n", variableMsg, varOffset)
            end
        end
    end

    # Conclude the structure's type definition, pad it for length and add the
    # structure's total size, then output this to the types header file
    $ccdd.writeToFileFormat(file, "%-" + minimumLength.to_s + "s /* Total size of " + structSize.to_s + " bytes */\n", "} " + $structureNames[structIndex] + ";")
end

#******************************************************************************
# Create the types header file
#
# @param baseFileName
#            base for the types header output file name
#******************************************************************************
def makeHeaders(baseFileName)
    # Build the types header output file name and include flag
    typesFileName = $ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.upcase + "_H_"

    # Open the types header output file
    typesFile = $ccdd.openOutputFile(typesFileName)

    # Check if the types header file successfully opened
    if typesFile != nil
        # Add the build information to the output file
        outputFileCreationInfo(typesFile)

        $ccdd.writeToFileLn(typesFile, "*/\n")

        # Add the header include to prevent loading the file more than once
        $ccdd.writeToFileLn(typesFile, "#ifndef " + headerIncludeFlag)
        $ccdd.writeToFileLn(typesFile, "#define " + headerIncludeFlag)
        $ccdd.writeToFileLn(typesFile, "")

        # Get the number of rows for the Includes table data
        numIncludeRows = $ccdd.getTableNumRows("Includes")

        # Check if there are any data to include
        if numIncludeRows > 0
            # Step through each row of Includes data
            for row in 0..numIncludeRows - 1
                # Output the Includes table's 'includes' column data
                $ccdd.writeToFileLn(typesFile, $ccdd.getTableData("Includes", "includes", row))
            end

            $ccdd.writeToFileLn(typesFile, "")
        end

        # Get the data type definitions
        defns = $ccdd.getDataTypeDefinitions()

        # Step through each data type definition
        for index in 0..defns.length - 1
            # Check if the type and C names aren't blank and if they differ
            if defns[index][0] && defns[index][1] && defns[index][0] != defns[index][1]
                # Output the definition of the data type
                $ccdd.writeToFileLn(typesFile, "#define " + defns[index][0] + " " + defns[index][1])
            end
        end

        $ccdd.writeToFileLn(typesFile, "")

        # Step through each structure. This list is in reference order so that
        # base structures are created before being referenced in another
        # structure
        for structIndex in 0..$structureNames.length - 1
            # Output the structure definition to the types header file
            outputStructure(typesFile, structIndex)
            $ccdd.writeToFileLn(typesFile, "")
        end

        # Add the def prototypes for byte- and bit-swapping at the end of
        # the types header file
        $ccdd.writeToFileLn(typesFile, "/* Byte and bit swap function prototypes */")

        # Step through each structure name
        for structIndex in 0..$structureNames.length - 1
            structureName = $structureNames[structIndex]

            # Add the def prototype for the structure
            $ccdd.writeToFileLn(typesFile, "void byte_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction);")
            $ccdd.writeToFileLn(typesFile, "void bit_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction);")
        end

        # Finish and close the types header output file
        $ccdd.writeToFileLn(typesFile, "")
        $ccdd.writeToFileLn(typesFile, "#endif /* #ifndef " + headerIncludeFlag + " */")
        $ccdd.closeFile(typesFile)
    # The types header file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening types header output file '</b>" + typesFileName + "<b>'")
    end
end

#******************************************************************************
# Create the byte and bit swapping def source code file
#
# @param baseFileName
#            base for the swap output file name
#******************************************************************************
def makeSwapFile(baseFileName)
    swapFileName = $ccdd.getOutputPath() + baseFileName + ".c"

    # Open the swap output file
    swapFile = $ccdd.openOutputFile(swapFileName)

    # Check if the output file successfully opened
    if swapFile != nil
        hasBitField = []

        # Add the build information to the output file
        outputFileCreationInfo(swapFile)

        # Output the source for the bit field swap and bit reversal functions
        $ccdd.writeToFileLn(swapFile, "#include <byteswap.h>")
        $ccdd.writeToFileLn(swapFile, "#include <string.h>")
        $ccdd.writeToFileLn(swapFile, "#include \"" + baseFileName + ".h" + "\"")
        $ccdd.writeToFileLn(swapFile, "")
        $ccdd.writeToFileLn(swapFile, "uint32 *p32, tmp_32;")
        $ccdd.writeToFileLn(swapFile, "uint64 *p64, tmp_64;")
        $ccdd.writeToFileLn(swapFile, "#define swap_float(pIn, pOut) p32 = (uint32*) pIn; tmp_32 = bswap_32(*p32); memcpy(pOut, &tmp_32, 4)")
        $ccdd.writeToFileLn(swapFile, "#define swap_double(pIn, pOut) p64 = (uint64*) pIn; tmp_64 = bswap_64(*p64); memcpy(pOut, &tmp_64, 8)")
        $ccdd.writeToFileLn(swapFile, "#define swap_pointer_8(pIn, pOut) p64 = (uint64*) pIn; tmp_64 = bswap_64(*p64); memcpy(pOut, &tmp_64, 8)")
        $ccdd.writeToFileLn(swapFile, "#define swap_pointer_4(pIn, pOut) p32 = (uint32*) pIn; tmp_32 = bswap_32(*p32); memcpy(pOut, &tmp_32, 4)")
        $ccdd.writeToFileLn(swapFile, "")
        $ccdd.writeToFileLn(swapFile, "/* Swaps a bit field of value 'val' containing 'num' bits, and returns the resulting 'mirrored' value */")
        $ccdd.writeToFileLn(swapFile, "static int bit_field_swap(int val, int num)")
        $ccdd.writeToFileLn(swapFile, "{")
        $ccdd.writeToFileLn(swapFile, "   int ret_val = 0, n_1 = num - 1, i = 0;")
        $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < num; i++)")
        $ccdd.writeToFileLn(swapFile, "   {")
        $ccdd.writeToFileLn(swapFile, "      ret_val|= (((val>>i) &1) << (n_1 - i));")
        $ccdd.writeToFileLn(swapFile, "   }")
        $ccdd.writeToFileLn(swapFile, "   return ret_val;")
        $ccdd.writeToFileLn(swapFile, "}")
        $ccdd.writeToFileLn(swapFile, "")
        $ccdd.writeToFileLn(swapFile, "/* Reverses the order of the bits in a n-byte object (only supports 1 <= n <= 8) */")
        $ccdd.writeToFileLn(swapFile, "static void reflect_bits(char *data, int n)")
        $ccdd.writeToFileLn(swapFile, "{")
        $ccdd.writeToFileLn(swapFile, "   if (n > 8) return;")
        $ccdd.writeToFileLn(swapFile, "   int i = 0;")
        $ccdd.writeToFileLn(swapFile, "   /* Need to go through all the bytes, since 2 nibbles (1 byte) are reflected each iteration of the 'for' loop */")
        $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < n; i++)")
        $ccdd.writeToFileLn(swapFile, "   {")
        $ccdd.writeToFileLn(swapFile, "      unsigned char *t1 = (unsigned char *) &(data[i]);")
        $ccdd.writeToFileLn(swapFile, "      unsigned char *t2 = (unsigned char *) &(data[n - 1 - i]);")
        $ccdd.writeToFileLn(swapFile, "      unsigned char v1 = ((*t1 & 1)  << 7) + ((*t1 &  2) << 5) + ((*t1 &  4)<<3) + ((*t1 &   8)<<1);")
        $ccdd.writeToFileLn(swapFile, "      unsigned char v2 = ((*t2 & 16) >> 1) + ((*t2 & 32) >> 3) + ((*t2 & 64)>>5) + ((*t2 & 128)>>7);")
        $ccdd.writeToFileLn(swapFile, "      *t1 = ((*t1) & 0xF0) | v2;")
        $ccdd.writeToFileLn(swapFile, "      *t2 = ((*t2) & 0x0F) | v1;")
        $ccdd.writeToFileLn(swapFile, "   }")
        $ccdd.writeToFileLn(swapFile, "}")

        # Step through each structure name
        for structIndex in 0..$structureNames.length - 1
            usedVariableNames = []
            isIDefined = false
            structureName = $structureNames[structIndex]
            hasBitField.push(false)

            # Begin building the def to byte swap the structure's
            # variables
            $ccdd.writeToFileLn(swapFile, "")
            $ccdd.writeToFileLn(swapFile, "/* inPtr and outPtr are pointers to the input and output data. 'direction' is a flag for if the conversion ")
            $ccdd.writeToFileLn(swapFile, "   is from foreign to local endian (direction = 1), or from native to foreign byte order (direction = 0) */")
            $ccdd.writeToFileLn(swapFile, "inline void byte_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction)")
            $ccdd.writeToFileLn(swapFile, "{")

            # Step through each row in the table
            for row in 0..$numStructRows - 1
                # Check if the structure name in the row matches the current
                # structure
                if $structureNames[structIndex] == $ccdd.getStructureTableNameByRow(row)
                    # Get the variable name for this row in the structure
                    variableName = $ccdd.getStructureVariableName(row)

                    # Check if this is not an array member; array definitions
                    # are output, but not members
                    if !variableName.end_with?("]")
                        isFound = false

                        # Step through each name in the array of already
                        # processed variable names
                        for index in 0..usedVariableNames.length - 1
                            # Check if the target name matches the array name
                            if usedVariableNames[index] == variableName
                                # Match found; set the flag and stop searching
                                isFound = true
                                break
                            end
                        end

                        byteSwap = "bswap_16"

                        # Get the variable's data type, bit length, and array
                        # size
                        dataType = $ccdd.getStructureDataType(row)
                        bitLength = $ccdd.getStructureBitLength(row)
                        arraySize = $ccdd.getStructureArraySize(row)

                        # Flag that's 'true' if the variable is an array
                        isArray = !arraySize.empty?

                        # Get the variable's base data type ('signed integer',
                        # 'character', etc.) and size in bytes
                        baseType = $ccdd.getBaseDataType(dataType)
                        variableSize = $ccdd.getDataTypeSizeInBytes(dataType)

                        # Check if the variable has a bit length
                        if !bitLength.empty?
                            # Set the flag to indicate the variable has been
                            # processed and that the structure includes a bit
                            # field variable, and add the variable to the list
                            # of those processed
                            isFound = true
                            hasBitField[structIndex] = true
                            usedVariableNames.push(variableName)
                        # Check if the type is a character or integer (signed
                        # or unsigned)
                        elsif baseType == "character" || baseType == "signed integer" || baseType == "unsigned integer"
                            # Use the size of the variable (in bytes) to
                            # determine the swap function. A single byte
                            # doesn't require a swap, so is simply marked
                            # as processed
                            if variableSize == 1
                                isFound = true
                                usedVariableNames.push(variableName)
                            elsif variableSize == 2
                                byteSwap = "bswap_16"
                            elsif variableSize == 4
                                byteSwap = "bswap_32"
                            elsif variableSize == 8
                                byteSwap = "bswap_64"
                            # Unrecognized size
                            else
                                # Ignore this size
                                isFound = true
                            end
                        # Check if the variable is a 'float'
                        elsif baseType == "floating point" && variableSize == 4
                            byteSwap = "swap_float"
                        # Check if the variable is a 'double'
                        elsif baseType == "floating point" && variableSize == 8
                            byteSwap = "swap_double"
                        # Check if the variable is a pointer
                        elsif baseType == "pointer"
                            # Use the pointer's size to determine the swap
                            # function
                            if variableSize == 8
                                byteSwap = "swap_pointer_8"
                            else
                                byteSwap = "swap_pointer_4"
                            end
                        end

                        # Check if the variable name hasn't already been
                        # processed; this is necessary to prevent duplicating
                        # the members in the type definition for a structure
                        # that is referenced as an array
                        if !isFound
                            # Add the variable name to the list of those
                            # already processed
                            usedVariableNames.push(variableName)

                            # Check if the type is a character or integer
                            # (signed or unsigned)
                            if baseType == "character" || baseType == "signed integer" || baseType == "unsigned integer"
                                # Check if the variable is an array
                                if isArray
                                    # Check if the variable 'i' hasn't already
                                    # been defined in the file
                                    if !isIDefined
                                        # Set the flag indicating 'i' has been
                                        # defined and output its definition to
                                        # the file
                                        isIDefined = true
                                        $ccdd.writeToFileLn(swapFile, "   int i = 0;")
                                    end

                                    # Add the source code to call the
                                    # appropriate def to swap the bytes in
                                    # each of the variable's array members
                                    $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < " + arraySize + "; i++)")
                                    $ccdd.writeToFileLn(swapFile, "   {")
                                    $ccdd.writeToFileLn(swapFile, "      outPtr->" + variableName + "[i] = " + byteSwap + "(inPtr->" + variableName + "[i]);")
                                    $ccdd.writeToFileLn(swapFile, "   }")
                                # The variable isn't an array
                                else
                                    # Add the source code to call the
                                    # appropriate
                                    # def to swap the variable's bytes
                                    $ccdd.writeToFileLn(swapFile, "   outPtr->" + variableName + " = " + byteSwap + "(inPtr->" + variableName + ");")
                                end
                            # Check if the variable is a 'float, 'double', or
                            # pointer
                            elsif baseType == "floating point" || baseType == "pointer"
                                # Check if the variable is an array
                                if isArray
                                    # Check if the variable 'i' hasn't already
                                    # been defined in the file
                                    if !isIDefined
                                        # Set the flag indicating 'i' has been
                                        # defined and output its definition to
                                        # the file
                                        isIDefined = true
                                        $ccdd.writeToFileLn(swapFile, "   int i = 0;")
                                    end

                                    # Add the source code to call the
                                    # appropriate def to swap the bytes in
                                    # each of the variable's array members
                                    $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < " + arraySize + "; i++)")
                                    $ccdd.writeToFileLn(swapFile, "   {")
                                    $ccdd.writeToFileLn(swapFile, "      " + byteSwap + "(&(inPtr->" + variableName + "[i]), &(outPtr->" + variableName + "[i]));")
                                    $ccdd.writeToFileLn(swapFile, "   }")
                                # The variable isn't an array
                                else
                                    # Add the source code to call the
                                    # appropriate def to swap the
                                    # variable's bytes
                                    $ccdd.writeToFileLn(swapFile, "   " + byteSwap + "(&(inPtr->" + variableName + "), &(outPtr->" + variableName + "));")
                                end
                            # The variable is a structure
                            else
                                # Check if the variable is an array
                                if isArray
                                    # Check if the variable 'i' hasn't already
                                    # been defined in the file
                                    if !isIDefined
                                        # Set the flag indicating 'i' has been
                                        # defined and output its definition to
                                        # the file
                                        isIDefined = true
                                        $ccdd.writeToFileLn(swapFile, "   int i = 0;")
                                    end

                                    # Add the source code to call the
                                    # appropriate def to swap the bytes in
                                    # each of the variable's array members
                                    $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < " + arraySize + "; i++)")
                                    $ccdd.writeToFileLn(swapFile, "   {")
                                    $ccdd.writeToFileLn(swapFile, "      byte_swap_" + dataType + "(&(inPtr->" + variableName + "[i]), &(outPtr->" + variableName + "[i]),direction);")
                                    $ccdd.writeToFileLn(swapFile, "   }")
                                # The variable isn't an array
                                else
                                    # Add the source code to call the
                                    # appropriate def to swap the
                                    # variable's bytes
                                    $ccdd.writeToFileLn(swapFile, "   byte_swap_" + dataType + "(&(inPtr->" + variableName + "), &(outPtr->" + variableName + "), direction);")
                                end
                            end
                        end
                    end
                end
            end

            # Check if the structure has a bit field variable
            if hasBitField[structIndex]
                # Add the source code to call the def to swap the bit
                # field(s) within the structure
                $ccdd.writeToFileLn(swapFile, "   bit_swap_" + structureName + "(inPtr, outPtr, direction); /* Swap all bit fields in this structure */")
            end

            # Add the source code to terminate this structure's byte swap
            # function
            $ccdd.writeToFileLn(swapFile, "} /* End of byte_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction) */")
        end

        # Step through each structure name
        for structIndex in 0..$structureNames.length - 1
            usedVariableNames = []
            lastBitFieldType = "none"
            curFilledBits = 0
            maxBitsAvailable = 0
            isIDefined = false
            structureName = $structureNames[structIndex]
            lastBitFieldString = nil

            # Check if the structure includes a bit field variable
            if hasBitField[structIndex]
                # Begin building the def to bit swap the structure's bit
                # field variables
                $ccdd.writeToFileLn(swapFile, "")
                $ccdd.writeToFileLn(swapFile, "/* inPtr and outPtr are pointers to the input and output data. 'direction' is a flag for if the conversion ")
                $ccdd.writeToFileLn(swapFile, "   is from foreign-to-local endian (direction = 1), or from native to foreign byte order (direction = 0) */")
                $ccdd.writeToFileLn(swapFile, "inline void bit_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction)")
                $ccdd.writeToFileLn(swapFile, "{")

                # Step through each row in the table
                for row in 0..$numStructRows - 1
                    # Check if the structure name in the row matches the
                    # current structure
                    if $structureNames[structIndex] == $ccdd.getStructureTableNameByRow(row)
                        # Get the variable name for this row in the structure
                        variableName = $ccdd.getStructureVariableName(row)

                        # Get the variable's path and use it to get the byte
                        # offset
                        variablePath = $structureNames[structIndex] + "," + $ccdd.getStructureDataType(row) + "." + $ccdd.getStructureVariableName(row)
                        varOffset = $ccdd.getVariableOffset(variablePath)

                        # Check if this is not an array member; array
                        # definitions are output, but not members
                        if !variableName.end_with?("]")
                            isFound = false

                            # Step through each name in the array of already
                            # processed variable names
                            for index in 0..usedVariableNames.length - 1
                                # Check if the target name matches the array
                                # name
                                if usedVariableNames[index] == variableName
                                    # Match found; set the flag and stop
                                    # searching
                                    isFound = true
                                    break
                                end
                            end

                            # Get the variable's data type, bit length, and
                            # array size
                            dataType = $ccdd.getStructureDataType(row)
                            bitLength = $ccdd.getStructureBitLength(row)
                            arraySize = $ccdd.getStructureArraySize(row)

                            # Flag that's 'true' if it's an array
                            isArray = !arraySize.empty?

                            for index in 0..usedVariableNames.length - 1
                                # Check if the target name matches the array
                                # name
                                if usedVariableNames[index] == variableName
                                    # Match found; set the flag and stop
                                    # searching
                                    isFound = true
                                    break
                                end
                            end

                            # Check if the variable name hasn't already been
                            # processed; this is necessary to prevent
                            # duplicating the members in the type definition
                            # for a structure that is referenced as an array
                            if !isFound
                                # Add the variable to the list of those
                                # processed
                                usedVariableNames.push(variableName)

                                # Check if the variable has a bit length
                                if !bitLength.empty?
                                    # Check if the data type of the variable
                                    # differs from the previous one or if the
                                    # variable's bits won't fit within the
                                    # current packing space
                                    if lastBitFieldType != dataType || (curFilledBits + bitLength.to_i > maxBitsAvailable)
                                        # Get the variable's size in bytes
                                        byteSize = $ccdd.getDataTypeSizeInBytes(dataType)

                                        # Check if a bit swap call was made in
                                        # the structure
                                        if lastBitFieldString != nil
                                            # Create the code to reverse the
                                            # packed bits following a bit swap
                                            # if the direction is from native
                                            # to foreign byte order
                                            $ccdd.writeToFileLn(swapFile, "   if (!direction)")
                                            $ccdd.writeToFileLn(swapFile, "   {")
                                            $ccdd.writeToFileLn(swapFile, "      " + lastBitFieldString)
                                            $ccdd.writeToFileLn(swapFile, "   }")
                                        end

                                        # Create the code to reverse the packed
                                        # bits prior to a bit swap if the
                                        # direction is from foreign to local
                                        # endian
                                        lastBitFieldString = "reflect_bits(&(((char*)(inPtr))[" + varOffset.to_s + "]), " + byteSize.to_s + ");"
                                        curFilledBits = bitLength.to_i
                                        lastBitFieldType = dataType
                                        maxBitsAvailable = 8 * byteSize
                                        $ccdd.writeToFileLn(swapFile, "   if (direction)")
                                        $ccdd.writeToFileLn(swapFile, "   {")
                                        $ccdd.writeToFileLn(swapFile, "      " + lastBitFieldString)
                                        $ccdd.writeToFileLn(swapFile, "   }")
                                    # The data types match and the bits will
                                    # pack together
                                    else
                                        # Add this variable's bits to the
                                        # current pack
                                        curFilledBits = curFilledBits + bitLength.to_i
                                    end

                                    # Check if the bit length is greater than 1
                                    if bitLength != "1"
                                        $ccdd.writeToFileLn(swapFile, "   outPtr->" + variableName + " = bit_field_swap(inPtr->" + variableName + ", " + bitLength + ");")
                                    end
                                # Check if the data type is a primitive
                                elsif $ccdd.isDataTypePrimitive(dataType)
                                    lastBitFieldType = "none"
                                # The data type is a structure
                                else
                                    lastBitFieldType = "none"

                                    # Step through each structure name
                                    for index in 0..$structureNames.length - 1
                                        # Check if the structure names match
                                        if dataType == $structureNames[index]
                                            # Check if the target structure
                                            # includes a bit field variable
                                            if hasBitField[index]
                                                # Check if the variable is an
                                                # array
                                                if isArray
                                                    # Add the source code to
                                                    # call the appropriate
                                                    # def to swap the bits
                                                    # in each of the variable's
                                                    # array members
                                                    $ccdd.writeToFileLn(swapFile, "   for (i = 0; i < " + arraySize + "; i++)")
                                                    $ccdd.writeToFileLn(swapFile, "   {")
                                                    $ccdd.writeToFileLn(swapFile, "      bit_swap_" + dataType + "(&(inPtr->" + variableName + "[i]), &(outPtr->" + variableName + "[i]));")
                                                    $ccdd.writeToFileLn(swapFile, "   }")
                                                # The variable isn't an array
                                                else
                                                    # Add the source code to
                                                    # call the appropriate
                                                    # def to swap the
                                                    # variable's bits
                                                    $ccdd.writeToFileLn(swapFile, "   bit_swap_" + dataType + "(&(inPtr->" + variableName + "), &(outPtr->" + variableName + "));")
                                                end
                                            end

                                            # Stop searching since the target
                                            # structure has found
                                            break
                                        end
                                    end
                                end
                            end
                        end
                    end
                end

                # Check if a bit swap call was made in the structure
                if lastBitFieldString != nil
                    # Create the code to reverse the packed bits following a
                    # bit swap if the direction is from native to foreign byte
                    # order
                    $ccdd.writeToFileLn(swapFile, "   if (!direction)")
                    $ccdd.writeToFileLn(swapFile, "   {")
                    $ccdd.writeToFileLn(swapFile, "      " + lastBitFieldString)
                    $ccdd.writeToFileLn(swapFile, "   }")
                end

                # Add the source code to terminate this structure's bit swap
                # function
                $ccdd.writeToFileLn(swapFile, "} /* End of bit_swap_" + structureName + "(" + structureName + " *inPtr, " + structureName + " *outPtr, int direction) */")
            end
        end

        $ccdd.closeFile(swapFile)
    # The swap file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening byte/bit swap output file '</b>" + swapFileName + "<b>'")
    end
end

#** End functions *************************************************************

#** Main **********************************************************************

# Check if structure data is supplied
if $numStructRows > 0
    # The output file names are based in part on the value of the data field,
    # 'System', found in the first group or table associated with the script.
    # If the field can't be found in either then the value is set to a blank
    systemName = nil

    # Get the group(s) associated with the script (if any)
    groupNames = $ccdd.getAssociatedGroupNames()

    # Check if a group is associated with the script
    if groupNames.length != 0
        # Get the value of the first group's 'System' data field, if present
        systemName = $ccdd.getGroupDataFieldValue(groupNames[0], "System")
    end

    # Check if the system name wasn't found in the group data field
    if systemName == nil || systemName.empty?
        # Get the value of the first root structure's 'System' data field
        systemName = $ccdd.getTableDataFieldValue($ccdd.getRootStructureTableNames()[0], "System")
    end

    # Check if the data field doesn't exist in either a group or table
    if systemName == nil
        systemName = ""
    end

    # Create the base file name
    baseFileName = systemName + "_types"

    # Output the types header and byte-/bit-swap files
    makeHeaders(baseFileName)
    makeSwapFile(baseFileName)
# No structure data is supplied
else
    # Display an error dialog
    $ccdd.showErrorDialog("<html><b>No structure data supplied for script '</b>" + $ccdd.getScriptName() + "<b>'")
end
