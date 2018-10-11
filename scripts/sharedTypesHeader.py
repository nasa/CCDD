#******************************************************************************
# Description: Output a message ID header file
#
# This Python script generates a shared structure types header file from the
# supplied structure table(s).
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

from CCDD import CcddScriptDataAccessHandler
import re

# Get the array of structure names by the order in which they are referenced
structureNames = ccdd.getStructureTablesByReferenceOrder()

# Get the number of structure and command table rows
numStructRows = ccdd.getStructureTableNumRows()
numCommandRows = ccdd.getCommandTableNumRows()

# Get an array containing the data stream names
dataStreams = ccdd.getDataStreamNames()

#** Functions *****************************************************************

#******************************************************************************
# Output the file creation details to the specified file
#
# @param file
#            reference to the output file
#******************************************************************************
def outputFileCreationInfo(file):
    # Add the build information and header to the output file
    ccdd.writeToFileLn(file, "/* Created : " + ccdd.getDateAndTime() + "\n   User    : " + ccdd.getUser() + "\n   Project : " + ccdd.getProject() + "\n   Script  : " + ccdd.getScriptName())

    # Check if any table is associated with the script
    if ccdd.getTableNumRows() != 0:
        ccdd.writeToFileLn(file, "   Table(s): " + (",\n             ").join(sorted(ccdd.getTableNames())))

    # Check if any groups is associated with the script
    if len(ccdd.getAssociatedGroupNames()) != 0:
        ccdd.writeToFileLn(file, "   Group(s): " + (",\n             ").join(sorted(ccdd.getAssociatedGroupNames())))

    ccdd.writeToFileLn(file, "*/\n")

#******************************************************************************
# Output a structure's type definition to the specified file
#
# @param file
#            reference to the types header output file
#
# @param structIndex
#            index of the structure in the structure name array
#******************************************************************************
def outputStructure(file, structIndex):
    firstPass = True
    isCCSDS = False
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
    minimumLength = len(" " + structureNames[structIndex] + "; ")

    # A pass is made through the structure rows in order to determine the
    # longest one, character-wise, so that the output can be formatted. Step
    # through each structure data row
    for row in range(numStructRows):
        # Check if the structure name in the row matches the current structure
        if structureNames[structIndex] == ccdd.getStructureTableNameByRow(row):
            # Check if this is the first pass through the structure data
            if firstPass:
                firstPass = False

                # Get the value of the structure's message ID data field
                msgID = ccdd.getTableDataFieldValue(structureNames[structIndex], "Message ID")

                # Check if the structure table has a message ID
                if msgID is not None and msgID:
                    # Set the minimum length to that of the CCSDS header
                    # variable which will be added if the structure has a
                    # message ID
                    minimumLength = len("   char CFS_PRI_HEADER[6]; ")

            # Get the variable name for this row
            variableName = ccdd.getStructureVariableName(row)

            # Check that this isn't an array member; only array definitions
            # appear in the type definition
            if not variableName.endswith("]"):
                # Get the variable's array size
                arraySize = ccdd.getStructureArraySize(row)

                # Check if the variable is an array
                if arraySize:
                    # Add the brackets that will appear around the array size.
                    # Multi-dimensional arrays have the individual dimensions
                    # separated by ', '; in the type definition each ', ' is
                    # replaced with '][' which is the same number of
                    # characters, so no further padding adjustment needs to
                    # be made here to account for them
                    arraySize += "[]"

                # Get the variable's bit length
                bitLength = ccdd.getStructureBitLength(row)

                # Check if the variable has a bit length
                if bitLength:
                    # Add the colon that will appear before the bit length
                    bitLength += ":"

                # Determine the length of the variable definition by adding up
                # the individual parts
                defnLength = len("   " + ccdd.getStructureDataType(row) + " " + variableName + arraySize + bitLength + "; ")

                # Check if the length exceeds the minimum length found thus far
                if defnLength > minimumLength:
                    # Store the new minimum length
                    minimumLength = defnLength

    firstPass = True

    # Step through each structure data row
    for row in range(numStructRows):
        deltaSize = 0

        # Check if the structure name in the row matches the target structure
        if structureNames[structIndex] == ccdd.getStructureTableNameByRow(row):
            # Get the variable name for this row in the structure
            variableName = ccdd.getStructureVariableName(row)

            # Check if this is the first pass through the structure data
            if firstPass:
                firstPass = False

                # Get the description for the current structure
                structDescription = ccdd.getTableDescriptionByRow("Structure", row)

                # Get the size of the entire structure, in bytes
                structSize = ccdd.getDataTypeSizeInBytes(structureNames[structIndex])

                # Get the value of the structure's message ID data field
                msgID = ccdd.getTableDataFieldValue(structureNames[structIndex], "Message ID")

                # Check if the structure table has a message ID
                if msgID is not None and msgID:
                    # Set the flag to add in CCSDS primary and secondary
                    # headers
                    isCCSDS = True
                    structSize = structSize + 12

                # Display the structure name, size, and description prior to
                # the structure's type definition
                ccdd.writeToFile(file, "/* Structure: " + structureNames[structIndex] + " (" + str(structSize) + " bytes total)")

                # Check if the structure has a description
                if structDescription:
                    # Display the structure's description
                    ccdd.writeToFile(file, "\n   Description: " + structDescription)

                ccdd.writeToFileLn(file, " */")

                # Begin the structure type definition
                ccdd.writeToFileLn(file, "typedef struct")
                ccdd.writeToFileLn(file, "{")

                # Check if CCSDS headers should be added
                if isCCSDS:
                    # Set the CCSDS header length, which is used as the byte
                    # offset for the subsequent variables
                    headerOffset = 12

                    # Output the variable array that contains the primary
                    # header values
                    offsetStr = "0"
                    ccsdsVar = "   char CFS_PRI_HEADER[6];"
                    comment = "#CCSDS_PriHdr_t"
                    sizeString = "(6 bytes)"
                    ccdd.writeToFileFormat(file, "%-" + str(minimumLength) + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr)

                    # Output the variable array that contains the secondary
                    # header values
                    offsetStr = "6"
                    ccsdsVar = "   char CFS_SEC_HEADER[6];"
                    comment = "#CCSDS_CmdSecHdr_t"
                    ccdd.writeToFileFormat(file, "%-" + str(minimumLength) + "s /* [%5s] " + sizeString + "  " + comment + " */\n", ccsdsVar, offsetStr)
                # No CCSDS header should be added
                else:
                    # Set the variable byte offset to zero
                    headerOffset = 0

            # Check if this is not an array member (only array definitions are
            # output), and if the variable name hasn't already been processed
            # (the first instance of the structure is used to obtain the
            # information to create the type definition, so this is necessary
            # to prevent duplicating the members in the type definition if
            # more than one instance of the structure is present in the data)
            if not variableName.endswith("]") and usedVariableNames.count(variableName) == 0:
                # Add the variable name to the list of those already processed
                usedVariableNames.append(variableName)

                # Get the variable's data type, array size, and description
                dataType = ccdd.getStructureDataType(row)
                arraySize = ccdd.getStructureArraySize(row)
                description = ccdd.getStructureDescription(row)

                # Determine the size of the variable, in bytes
                byteSize = ccdd.getDataTypeSizeInBytes(dataType)

                # Build the variable's full path; this will be used to get the
                # structure's byte offset
                variablePath = structureNames[structIndex] + "," + dataType + "." + variableName
                varOffset = 0

                bitLength = ""
                sizeString = "(" + str(byteSize) + " bytes)"
                variableMsg = "   " + dataType + " " + variableName

                # Check if the structure has no variable description column
                if description is None:
                    # Set the description to a blank
                    description = ""

                # Check if the array size is provided; i.e., this is an array
                # definition
                if arraySize:
                    firstDim = ""
                    sizeMsg = ""
                    deltaSize = 1

                    # Separate the array size into the individual dimensions
                    dimensions = arraySize.split(", ")

                    # Step through each dimension in the array
                    for dim in range(len(dimensions)):
                        # Add a dimension for the first array member
                        firstDim += "[0]"

                        # Keep a running total of this dimension's byte
                        # requirements
                        deltaSize *= int(dimensions[dim])

                        # Update the comment text that will follow the array
                        # definition
                        sizeMsg += dimensions[dim] + "x"

                    # Get the total byte size of the array
                    deltaSize *= byteSize

                    # Get the byte offset of the first member of this array
                    # variable within its structure
                    varOffset = ccdd.getVariableOffset(variablePath + firstDim)

                    # Create the array variable definition, placing brackets
                    # around the array dimensions
                    variableMsg = variableMsg + "[" + arraySize.replace(", ", "][") + "]"

                    # Build the comment that shows the array's byte size
                    sizeString = "(" + sizeMsg + str(byteSize) + "=" + str(deltaSize) + " bytes)"

                    lastBitFieldType = "none"
                # No array size for this row; i.e., the variable is not an
                # array definition
                else:
                    deltaSize = byteSize

                    # Get the byte offset of the this variable within its
                    # structure
                    varOffset = ccdd.getVariableOffset(variablePath)

                    # Get the variable's bit length
                    bitLength = ccdd.getStructureBitLength(row)

                    # Check if the bit length is provided
                    if bitLength:
                        # Append the bit length to the variable
                        variableMsg = variableMsg + ":" + bitLength
                        sizeString = ""

                        # Check if the variable won't pack with the preceding
                        # variable(s) due to being a different data type or
                        # exceeding the bit length of the data type
                        if lastBitFieldType != dataType or (curFilledBits + int(bitLength) > maxBitsAvailable):
                            # Reset the bit packing values
                            curFilledBits = int(bitLength)
                            lastBitFieldType = dataType
                            maxBitsAvailable = 8 * byteSize
                        # The variable has the same data type and its bits
                        # will pack with the preceding variable(s)
                        else:
                            # Add this variable's bits to the current pack
                            curFilledBits = curFilledBits + int(bitLength)
                    # The variable has no bit length
                    else:
                        lastBitFieldType = "none"

                # Terminate the variable definition then pad it with spaces to
                # align the comment text
                variableMsg += ";"

                # Adjust the variable's byte offset within the structure to
                # include the header (if present)
                varOffset = varOffset + headerOffset

                rateInfo = ""

                # Step through each data stream
                for dataStream in range(len(dataStreams)):
                    # Get the variable's rate for this data stream
                    rateValue = ccdd.getStructureTableData(dataStreams[dataStream], row)

                    # Check if the variable has a rate assigned in this stream
                    if rateValue:
                        # Build the rate information
                        rateInfo += "{" + dataStreams[dataStream] + " @" + rateValue + " Hz"

                # Build the full variable definition, along with the byte
                # offset, size, rate, and description information, then
                # output it to the types header file
                ccdd.writeToFileFormat(file, "%-" + str(minimumLength) + "s /* [%5s] " + (sizeString + rateInfo + "  " + description).strip() + " */\n", variableMsg, varOffset)

    # Conclude the structure's type definition, pad it for length and add the
    # structure's total size, then output this to the types header file
    ccdd.writeToFileFormat(file, "%-" + str(minimumLength) +"s /* Total size of " + str(structSize) + " bytes */\n", " " + structureNames[structIndex] + ";")

#******************************************************************************
# Output the shared structure type definitions header file
#
# @param baseFileName
#            base for the shared structure type definitions header output file
#            name
#******************************************************************************
def makeSharedHeaders(baseFileName):
    # Build the shared type definitions header output file name and include
    # flag
    sharedFileName = ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.toUpperCase() + "_H_"

    # Open the shared type definitions header output file
    sharedFile = ccdd.openOutputFile(sharedFileName)

    # Check if the shared type definitions header file successfully opened
    if sharedFile is not None:
        # Add the build information to the output file
        outputFileCreationInfo(sharedFile)

        # Add the header include to prevent loading the file more than once
        ccdd.writeToFileLn(sharedFile, "#ifndef " + headerIncludeFlag)
        ccdd.writeToFileLn(sharedFile, "#define " + headerIncludeFlag)
        ccdd.writeToFileLn(sharedFile, "#include <stdint.h>")
        ccdd.writeToFileLn(sharedFile, "")

        # Step through each structure. This list is in order so that base
        # structures are created before being referenced in another structure
        for struct in range(len(structureNames)):
            # Check if the structure is referenced by more than one structure
            if ccdd.isStructureShared(structureNames[struct]):
                # Output the structure's type definition to the shared types
                # file
                outputStructure(sharedFile, struct)

        # Finish and close the shared type definitions header output file
        ccdd.writeToFileLn(sharedFile, "")
        ccdd.writeToFileLn(sharedFile, "#endif /* #ifndef " + headerIncludeFlag + " */")
        ccdd.closeFile(sharedFile)
    # The shared type definitions header file failed to open
    else:
        # Display an error dialog
        ccdd.showErrorDialog("<html><b>Error opening types header output file '</b>" + sharedFileName + "<b>'")

#** Main **********************************************************************

# Check if structure data is supplied
if numStructRows != 0:
    # Output the shared structure type definition header file
    makeSharedHeaders("shared_types")
# No structure or command data is supplied
else:
    # Display an error dialog
    ccdd.showErrorDialog("<html><b>No structure data supplied for script '</b>" + ccdd.getScriptName() + "<b>'")
