/*******************************************************************************
 * Description: Output an ITOS page file
 * 
 * This JavaScript script generates an ITOS page file from the supplied
 * telemetry information
 * 
 * Copyright 2017 United States Government as represented by the Administrator
 * of the National Aeronautics and Space Administration. No copyright is claimed
 * in the United States under Title 17, U.S. Code. All Other Rights Reserved.
 ******************************************************************************/

importClass(Packages.CCDD.CcddScriptDataAccessHandler);

/** Functions *************************************************************** */

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
function isVariable(variableName, arraySize)
{
    // Only output non-array variables or array members (i.e., skip array
    // definitions)
    return variableName != null && arraySize != null && (arraySize.isEmpty() || variableName.endsWith("]"));
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
function convertArrayMember(variableName)
{
    return variableName.replaceAll("[\\[]", "_").replaceAll("[\\]]", "");
}

/*******************************************************************************
 * 
 ******************************************************************************/
function checkRows(variableName, fullVariableName, row)
{
    if (rowCount >= maxNumRows)
    {
        for (; row >= 0; row--)
        {
            var headerName = fullHeaderNames[row];

            if (headerName != "")
            {
                if (fullVariableName.equals(headerName + "_" + variableName))
                {
                    lastSubStructureName = headerNames[row];
                }
            }
        }

        columnCount++;
        ccdd.writeToFileLn(pageFile, "## col_max_len going from " + maxColumnLength + " back to " + columnStep);
        rowCount = 1;
        columnOffset = +columnOffset + +maxColumnLength + 2;
        maxColumnLength = columnStep;

        if (inMiddleOfArray)
        {
            ccdd.writeToFileLn(pageFile, "array_fmt(1," + columnOffset + ",\"" + nextColumnHeader + "\")");
        }
        else
        {
            ccdd.writeToFileLn(pageFile, "(1," + columnOffset + ",\"" + lastSubStructureName + "\")");
        }
    }
    else
    {
        rowCount++;
    }
}

/*******************************************************************************
 * 
 ******************************************************************************/
function isArrayElement(arraySize)
{
    return arraySize != null && !arraySize.isEmpty();
}

/*******************************************************************************
 * 
 ******************************************************************************/
function getIndex(str)
{
    var s = str.split("_");
    return s[s.length - 1];
}

/*******************************************************************************
 * USE PRINTF TYPE FORMATTING INSTEAD; EASIER TO CONVERT TO OTHER LANGUAGES (AS
 * IN TYPESHEADER)
 ******************************************************************************/
String.prototype.paddingLeft = function(paddingValue)
{
    return String(paddingValue + this).slice(-paddingValue.length);
};

/*******************************************************************************
 * Selects the format arguments to use for a particular ITOS type, based on its
 * type
 * 
 * @param itosEncode
 *            data type in ITOS encoded form
 * 
 * @return ITOS output format string
 ******************************************************************************/
function setITOSFormatFlag(itosEncode)
{
    var itosFormat = "";
    var withSign = 0;
    modNum = modNumDefault;

    switch (itosEncode.substr(0, 1))
    {
        case 'F':
            var varSize = itosEncode.substr(1);

            if (varSize > 4)
            {
                itosFormat = "%13.3f";
                numITOSDigits = 14;
                modNum = modNumDefault / 2;
            }
            else
            {
                itosFormat = "%6.3f";
                numITOSDigits = 7;
                modNum = modNumDefault / 2;
            }

            break;

        case 'I':
            withSign = 1;

        case 'U':
            var varSize = itosEncode.substr(1);

            if (isNaN(varSize))
            {
                print("Err for " + itosEncode + "\n");
                break;
            }

            var bytes = Math.max(varSize[0], varSize[varSize.length - 1]);

            // fancy math valid for n=1/2/4 bytes
            var nDigits = 2 * bytes + 1 + Math.floor(bytes / 4);

            // Add a digit for a +/- if signed integer
            nDigits += +withSign;
            itosFormat = "%" + nDigits.toString() + "d";
            numITOSDigits = nDigits;

            if (bytes > 2)
            {
                modNum = modNumDefault / 2;
            }

            break;

        case 'S':
            itosFormat = "%s";
            numITOSDigits = 10;
            modNum = modNumDefault / 2;
            break;

        default:
            break;
    }

    return itosFormat;
}

/*******************************************************************************
 * Output a single mnemonic definition
 * 
 * @param row
 *            row index in the structure data table
 * 
 * @returns true is a mnemonic definition is output to the file
 ******************************************************************************/
function outputMnemonicDefinition(row)
{
    var isOutput = false;
    var itosFormat = "";

    var variableName = ccdd.getStructureVariableName(row);
    var dataType = ccdd.getStructureDataType(row);

    // Get the ITOS encoded form of the data type
    var itosEncode = ccdd.getITOSEncodedDataType(dataType, "BIG_ENDIAN");

    // Check if this data type is a recognized base type or structure
    if (itosEncode != null)
    {
        // Check if the data type is a primitive (not a structure)
        if (!itosEncode.equals(dataType))
        {
            // Get the ITOS output format string based on the encoding 
            itosFormat = setITOSFormatFlag(itosEncode);
        }

        // Get the variable name and array size
        var arraySize = ccdd.getStructureArraySize(row);
        var fullVariableName = ccdd.getFullVariableName(row);

        // See if this row would exceed max. If so start another column
        checkRows(variableName, fullVariableName, row);

        var tmp = ccdd.getFullVariableName(row, " ");

        // Find number of spaces (i.e. " ") in tmp and makes prepad a string
        // containing only that many spaces
        var prepad = new java.lang.String(String(tmp.match(/ /g))).replaceAll("[\\,]", "");
        var len = 0;

        // Only output non-array variables or array members (i.e., skip array
        // definitions)
        if (isVariable(variableName, arraySize))
        {
            // In case this is an array member replace the square brackets
            variableName = convertArrayMember(variableName);

            // Create the mnemonic definition
            var fullVariableName2 = prepad + prefix + fullVariableName;
            len = prepad.length() + variableName.length();

            // Check if the data type is a structure
            if (itosEncode.equals(dataType))
            {
                nextColumnHeader = prepad + variableName;
                lastSubStructureName = nextColumnHeader;
                headerNames[row] = lastSubStructureName;
                fullHeaderNames[row] = fullVariableName;
                ccdd.writeToFileLn(pageFile, "(+," + columnOffset + ", \"" + nextColumnHeader + "\")");
            }
            // Not a structure; it's a primitive type
            else
            {
                if (isArrayElement(arraySize))
                {
                    var maxDigits = Math.ceil(Math.log(arraySize) / Math.LN10);
                    var digitPad = new Array(maxDigits + 1).join(" ");

                    // Output number with leading spaces
                    var index = getIndex(variableName).toString().paddingLeft(digitPad);

                    inMiddleOfArray = 1;

                    var arrayPad = 13;

                    // This item is first on a row
                    if (index % modNum != 0)
                    {
                        // ccdd.writeToFileLn(pageFile, fullVariableName2+"(=,
                        // +, \" :v"+itosFormat+":\")");
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(=,  +, \" :v" + itosFormat + ":\", raw)");
                        len = 0; // = len+ (index % modNum)*+arrayPad;
                    }
                    // This array item is NOT first item on a row
                    else
                    {
                        var lastIndex = Math.min(arraySize - 1, +index + +modNum - 1).toString().paddingLeft(digitPad);
                        var arrayMessage = new java.lang.String(String(prepad + "[" + index + "-" + lastIndex + "]"));
                        len = arrayMessage.length() + (numITOSDigits + 1) * Math.min(modNum, arraySize);
                        ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+,  " + columnOffset + ", \"" + arrayMessage + "  :v" + itosFormat + ":\",raw)");
                    }

                    if (index != +arraySize - 1 && (+index + 1) % modNum != 0)
                    {
                        rowCount = +rowCount - 1;
                    }

                    if (index == (+arraySize - 1))
                    {
                        inMiddleOfArray = 0;
                        nextColumnHeader = lastSubStructureName;
                    } // done with array
                }
                // Not an array item, print normally
                else
                {
                    ccdd.writeToFileLn(pageFile, fullVariableName2 + "(+,  " + columnOffset + ", \"" + prepad + variableName + " :v" + itosFormat + ":\",raw)");

                    len = prepad.length() + variableName.length() + numITOSDigits + 2;
                }
            }

            isOutput = true;
        }
        else
        // (isVariable(variableName, arraySize))
        {
            nextColumnHeader = prepad + variableName + "[" + arraySize + "] - " + itosEncode;
            len = new java.lang.String(String(nextColumnHeader)).length();
            ccdd.writeToFileLn(pageFile, "array_fmt(+," + columnOffset + ", \"" + nextColumnHeader + "\")");
        }

        if (maxColumnLength < +len)
        {
            ccdd.writeToFileLn(pageFile, "## col_max_len is now= " + len + "  (was " + maxColumnLength + ")");
            maxColumnLength = +len;
        }
    }
    else
    {
        ccdd.writeToFileLn(pageFile, "#### NOT printing " + variableName + "*/");
    }

    return isOutput;
}

/*******************************************************************************
 * Output all of the mnemonic definitions
 ******************************************************************************/
function outputMnemonicDefinitions()
{
    ccdd.writeToFileLn(pageFile, "");
    ccdd.writeToFileLn(pageFile, "# Mnemonic Definitions");

    for (var row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        fullHeaderNames[row] = "";
        headerNames[row] = "";
    }

    // Step through each row in the table
    for (row = 0; row < ccdd.getStructureTableNumRows(); row++)
    {
        // Output the mnemonic definition for this row in the data table
        var isOutput = outputMnemonicDefinition(row);

        if (isOutput)// Check if a mnemonic definition was output to the file
        {
            // Add an end of line to file to get ready for next line
            ccdd.writeToFileLn(pageFile, "");
        }
    }
}

/*******************************************************************************
 * 
 ******************************************************************************/
function writePageFile(fltCompName)
{
    nextColumnHeader = prefix + ccdd.getRootStructureTableNames()[0];

    lastSubStructureName = nextColumnHeader;
    columnStep = 20;
    maxColumnLength = columnStep;
    columnOffset = -columnStep - 1;
    maxNumRows = 46;
    rowCount = maxNumRows;
    columnCount = 0;
    inMiddleOfArray = 0;

    if (nextColumnHeader == "FC1_LAS_IO_Tlm_Out_t")
    {
        modNumDefault = 5;
        modNum = modNumDefault;
    }

    prefix = fltCompName;
    nextColumnHeader = prefix + ccdd.getRootStructureTableNames()[0];

    var dateAndTime = ccdd.getDateAndTime(); // Get the current date and
    // time

    if (numStructRows != 0) // Check if structure data is provided
    {
        var baseName = "auto_" + prefix + ccdd.getRootStructureTableNames()[0];
        var pageOutputFile = ccdd.getOutputPath() + baseName + ".page"; // The
        // telemetry
        // output
        // file
        // name

        pageFile = ccdd.openOutputFile(pageOutputFile);

        if (pageFile != null) // Check if the page output file
        // successfully opened
        {
            ccdd.writeToFileLn(pageFile, "page " + baseName);
            ccdd.writeToFileLn(pageFile, "# Created " + dateAndTime + "\n");
            ccdd.writeToFileLn(pageFile, "color default (orange, default)");
            ccdd.writeToFileLn(pageFile, "color mnedef (text (white, black) )");
            ccdd.writeToFileLn(pageFile, "color subpage (lightblue, blue)");
            ccdd.writeToFileLn(pageFile, "color array_fmt (royalblue, black)");
            ccdd.writeToFileLn(pageFile, "");

            outputMnemonicDefinitions();

            ccdd.closeFile(pageFile); // Close the output telemetry file
        }
        // The page output file cannot be opened
        else
        {
            // Display an error dialog
            ccdd.showErrorDialog("<html><b>Error opening telemetry output file '</b>" + pageOutputFile + "<b>'");
        }
    }
}

/** End functions *********************************************************** */

/** Main ******************************************************************** */
// Get the number of structure and command table rows
var numStructRows = ccdd.getStructureTableNumRows();

// Check if no structure or command data is supplied
if (numStructRows == 0)
{
    ccdd.showErrorDialog("No structure or command data supplied to script " + ccdd.getScriptName());
}
else
// Structure and/or command data is supplied
{
    var fcNames = [];
    var numFlightComputers = 0;
    var prefix = "FC1_";
    var nextColumnHeader;
    var lastSubStructureName;
    var pageFile;
    var columnStep;
    var maxColumnLength;
    var columnOffset;
    var maxNumRows;
    var rowCount;
    var columnCount;
    var headerNames = Array(ccdd.getStructureTableNumRows());
    var fullHeaderNames = Array(ccdd.getStructureTableNumRows());
    var inMiddleOfArray;

    var numITOSDigits = 8;
    var modNumDefault = 4;
    var modNum = modNumDefault;

    // Get the value of the data field specifying the flight computer base value
    var fcBase = ccdd.getGroupDataFieldValue("globals", "prefix");

    // Check if the data field exists or is empty
    if (fcBase == null || fcBase.isEmpty())
    {
        // Use the default base value
        fcBase = "FC";
    }

    // Get the value of the data field specifying the number of flight computers
    var numFC = ccdd.getGroupDataFieldValue("globals", "NumComputers");

    // Check if the data field exists, is empty, or isn't an integer value
    if (numFC == null || !numFC.match(/[0-9]+/g))
    {
        // Use the default number of flight computers
        numFlightComputers = 1;
    }
    // The value is an integer
    else
    {
        // Store the number of flight computers
        numFlightComputers = parseInt(numFC);
    }

    // Check if there is more than one flight computer
    if (numFlightComputers > 1)
    {
        // Step through each flight computer
        for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
        {
            // Store the flight computer name prefix and offset value
            fcNames[fcIndex] = fcBase + (fcIndex + 1) + "_";
        }
    }
    // Only one flight computer
    else
    {
        // No prefix for a single computer
        fcNames = [""];
    }

    // Step through each flight computer
    for (var fcIndex = 0; fcIndex < numFlightComputers; fcIndex++)
    {
        // Output the page file
        writePageFile(fcNames[fcIndex]);
    }
}
