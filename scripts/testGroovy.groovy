// Description: Groovy test script

import CCDD.CcddScriptDataAccessHandler

println "Test of a Groovy script"

// Define the check boxes
String[][] boxes = [ [ "Box 1", " Box 1 description" ], [ "Box 2", "" ] ]

// Display the check box dialog and get the user's selection
def checked = ccdd.getCheckBoxDialog("Check Box Dialog Test", boxes)

// Check if the Cancel button wasn't selected
if (checked != null)
{
    // Step through each check box
    for (def index = 0; index < checked.length; index++)
    {
        // Display the check box status
        println "Check box " + boxes[index][0] +
                " selection state is" +
                (checked[index] ? "" : " not") +
                " checked"
    }
}

// Open the output file
def file = ccdd.openOutputFile("myFileName")

// Get the array of structure names
def structNames = ccdd.getStructureTableNames()

// Step through each name found
for (def index = 0; index < structNames.length; index++)
{
    // Write the structure name to the output file
    ccdd.writeToFileLn(file,
                       "structNames[" +
                       index +
                       "] = " +
                       structNames[index])
}

// Close the output file
ccdd.closeFile(file)
