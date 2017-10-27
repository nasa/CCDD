// Import the script data access method class
import CCDD.CcddScriptDataAccessHandlerStatic._

System.out.println("Test of a Scala script")

// Define the check boxes
val boxes:Array[Array[String]] = Array(Array("Box 1", " Box 1 description"), Array("Box 2", ""))

// Display the check box dialog and get the user's selection
var checked = getCheckBoxDialog("Check Box Dialog Test", boxes)

// Check if the Cancel button wasn't selected
if (checked != null)
{
    // Step through each check box
    for (index <- 0 to checked.length -1)
    {
        // Display the check box status
        System.out.println("Check box " + boxes(index)(0) +
                           " selection state is" +
                           (if (checked(index)) "" else " not") +
                           " checked")
    }
}

// Open the output file
var file = openOutputFile("myFileName")

// Get the array of structure names
var structNames = getStructureTableNames()

// Step through each name found
for (index <- 0 to structNames.length - 1)
{
    // Write the structure name to the output file
    writeToFileLn(file,
                  "structNames[" +
                  index +
                  "] = " +
                  structNames(index))
}

// Close the output file
closeFile(file)
