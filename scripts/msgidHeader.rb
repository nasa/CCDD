#******************************************************************************
# Description: Output a message ID header file
#
# This Ruby script generates a message ID header file from the supplied
# table information
#
# Copyright 2017 United States Government as represented by the Administrator
# of the National Aeronautics and Space Administration. No copyright is claimed
# in the United States under Title 17, U.S. Code. All Other Rights Reserved.
#******************************************************************************

java_import Java::CCDD.CcddScriptDataAccessHandler

# Get the array of structure names by the order in which they are referenced
$structureNames = $ccdd.getStructureTablesByReferenceOrder()

# Get the number of structure and command table rows
$numStructRows = $ccdd.getStructureTableNumRows()
$numCommandRows = $ccdd.getCommandTableNumRows()

# Get the name of the project database
$projectName = $ccdd.getProject()

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
# Output the telemetry message IDs file
#
# @param baseFileName
#            base for the telemetry IDs output file name
#******************************************************************************
def makeTelemetryFile(baseFileName)
    # Build the telemetry message IDs output file name and include flag
    tlmFileName = $ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.upcase + "_H_"

    # Open the telemetry message IDs output file
    tlmFile = $ccdd.openOutputFile(tlmFileName)

    # Check if the telemetry message IDs file successfully opened
    if tlmFile != nil
        # Add the build information to the output file
        outputFileCreationInfo(tlmFile)

        # Add the header include to prevent loading the file more than once
        $ccdd.writeToFileLn(tlmFile, "#ifndef " + headerIncludeFlag)
        $ccdd.writeToFileLn(tlmFile, "#define " + headerIncludeFlag)
        $ccdd.writeToFileLn(tlmFile, "")

        $ccdd.writeToFileLn(tlmFile, "#include \"" + $projectName + "_base_ids.h\"")
        $ccdd.writeToFileLn(tlmFile, "")

        # Get an array containing all group names
        groupNames = $ccdd.getGroupNames(false)

        minimumLength = 12

        # Step through each structure name
        for nameIndex in 0..$structureNames.length - 1
            # Get the value of the structure's message ID name data field
            msgIDName = $ccdd.getTableDataFieldValue($structureNames[nameIndex], "Message ID Name")

            # Check if the field exists and isn't empty, and the length exceeds
            # the minimum length found thus far
            if msgIDName != nil && msgIDName && msgIDName.length > minimumLength
                # Store the new minimum length
                minimumLength = msgIDName.length
            end
        end

        # Step through each group name
        for groupIndex in 0..groupNames.length - 1
            # Get the value of the group's message ID name data field
            msgIDName = $ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID Name")

            # Check if the field exists and isn't empty, and the length exceeds
            # the minimum length found thus far
            if msgIDName != nil && msgIDName && msgIDName.length > minimumLength
                # Store the new minimum length
                minimumLength = msgIDName.length
            end
        end

        # Build the format string used to align the message ID definitions
        format = "#define %-" + (minimumLength + 1).to_s + "s %s\n"

        $ccdd.writeToFileLn(tlmFile, "/* Structure message IDs: " + $structureNames.length.to_s + " structures */")

        # Step through each structure name
        for nameIndex in 0..$structureNames.length - 1
            # Get the values of the structure's message ID and ID name data
            # fields
            msgID = $ccdd.getTableDataFieldValue($structureNames[nameIndex], "Message ID")
            msgIDName = $ccdd.getTableDataFieldValue($structureNames[nameIndex], "Message ID Name")

            # Output the telemetry message ID to the file
            outputIDDefine(tlmFile, format, msgID, msgIDName)
        end

        $ccdd.writeToFileLn(tlmFile, "")
        $ccdd.writeToFileLn(tlmFile, "/* Group message IDs: " + groupNames.length.to_s + " groups */")

        # Step through each group
        for groupIndex in 0..groupNames.length - 1
            # Get the values of the group's message ID and ID name data fields
            msgID = $ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID")
            msgIDName = $ccdd.getGroupDataFieldValue(groupNames[groupIndex], "Message ID Name")

            # Output the telemetry message ID to the file
            outputIDDefine(tlmFile, format, msgID, msgIDName)
        end

        # Finish and close the telemetry message IDs output file
        $ccdd.writeToFileLn(tlmFile, "")
        $ccdd.writeToFileLn(tlmFile, "#endif /* #ifndef " + headerIncludeFlag + " */")
        $ccdd.closeFile(tlmFile)
    # The telemetry message IDs file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening telemetry message IDs output file '</b>" + tlmFileName + "<b>'")
    end
end

#******************************************************************************
# Output the command codes file
#
# @param baseFileName
#            base for the command codes output file name \
#******************************************************************************
def makeCommandFile(baseFileName)
    # Build the command codes output file name and include flag
    cmdFileName = $ccdd.getOutputPath() + baseFileName + ".h"
    headerIncludeFlag = "_" + baseFileName.upcase + "_H_"

    # Open the command codes output file
    cmdFile = $ccdd.openOutputFile(cmdFileName)

    # Check if the command codes file successfully opened
    if cmdFile != nil
        # Add the build information to the output file
        outputFileCreationInfo(cmdFile)

        # Add the header include to prevent loading the file more than once
        $ccdd.writeToFileLn(cmdFile, "#ifndef " + headerIncludeFlag)
        $ccdd.writeToFileLn(cmdFile, "#define " + headerIncludeFlag)
        $ccdd.writeToFileLn(cmdFile, "")

        minimumLength = 10

        # Step through each command table row
        for row in 0..$numCommandRows - 1
            # Get the command name
            cmdName = $ccdd.getCommandName(row)

            # Check if the command name is present and the length exceeds the
            # minimum length found thus far
            if cmdName != nil && cmdName.length > minimumLength
                # Store the new minimum length
                minimumLength = cmdName.length
            end
        end

        # Build the format string used to align the command code definitions
        format = "#define %-" + (minimumLength + 1).to_s + "s %s\n"

        # Step through each command
        for row in 0..$numCommandRows - 1
            # Get the command ID name and ID value
            cmdName = $ccdd.getCommandName(row)
            cmdCode = $ccdd.getCommandCode(row)

            # Check if the name and ID exist
            if cmdCode != nil && cmdName != nil
                # Output the formatted command code definition to the file
                $ccdd.writeToFileFormat(cmdFile, format, cmdName, cmdCode)
            end
        end

        # Finish and close the command codes output file
        $ccdd.writeToFileLn(cmdFile, "")
        $ccdd.writeToFileLn(cmdFile, "#endif /* #ifndef " + headerIncludeFlag + " */")
        $ccdd.closeFile(cmdFile)
    # The command codes file failed to open
    else
        # Display an error dialog
        $ccdd.showErrorDialog("<html><b>Error opening command codes output file '</b>" + cmdFileName + "<b>'")
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
# Output the message ID definition to the specified file with a base value
# added
#
# @param file
#            reference to the file to which to output the message ID
#            information
#
# @param format
#            output format string
#
# @param msgId
#            message ID
#
# @param msgIDName
#            message ID name
#******************************************************************************
def outputIDDefine(file, format, msgID, msgIDName)
    # Check if the message ID and ID name are present
    if msgID != nil && msgID && msgIDName != nil && msgIDName
        # Remove all but the last 12 bits of the ID, format it, and output the
        # #define for the ID to the file with a base value added
        $ccdd.writeToFileFormat(file, format, msgIDName, "( " + $ccdd.getProject().upcase + "_TLM_MID_BASE_1 + " + extractMessageID(msgID) + " )")
    end
end

#** End functions *************************************************************

#** Main **********************************************************************

# Check if structure and/or command data is supplied
if $numStructRows != 0 || $numCommandRows != 0
    # Check if structure data is supplied
    if $numStructRows != 0
        # Output the telemetry message IDs file
        makeTelemetryFile($projectName + "_tlm_ids")
    end

    # Check if command data is supplied
    if $numCommandRows != 0
        # Output the command codes file
        makeCommandFile($projectName + "_cmd_codes")
    end
# No structure or command data is supplied
else
    # Display an error dialog
    $ccdd.showErrorDialog("<html><b>No structure or command data supplied for script '</b>" + $ccdd.getScriptName() + "<b>'")
end
