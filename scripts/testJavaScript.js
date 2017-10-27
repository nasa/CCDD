// Description: JavaScript test script

try
{
    load("nashorn:mozilla_compat.js");
    print("Test of a JavaScript script using Nashorn");
}
catch (e)
{
    print("Test of a JavaScript script using Rhino\n");
}

importClass(Packages.CCDD.CcddScriptDataAccessHandler);

// Define the check boxes
var boxes = [ [ "Box 1", " Box 1 description" ], [ "Box 2", "" ] ];

// Display the check box dialog and get the user's selection
var checked = ccdd.getCheckBoxDialog("Check Box Dialog Test", boxes);

// Check if the Cancel button wasn't selected
if (checked != null)
{
    // Step through each check box
    for (var index = 0; index < checked.length; index++)
    {
        // Display the check box status
        print("Check box " + boxes[index][0]
                + " selection state is"
                + (checked[index] ? "" : " not")
                + " checked\n");
    }
}

// Open the output file
var file = ccdd.openOutputFile("myFileName");

// Get the array of structure names
var structNames = ccdd.getStructureTableNames();

// Step through each name found
for (var index = 0; index < structNames.length; index++)
{
    // Write the structure name to the output file
    ccdd.writeToFileLn(file,
                       "structNames["
                       + index
                       + "] = "
                       + structNames[index]);
}

// Close the output file
ccdd.closeFile(file);
