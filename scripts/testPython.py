# Description: Python test script

from CCDD import CcddScriptDataAccessHandler

print "Test of a Python script using Jython"

# Define the check boxes
boxes = [ [ "Box 1", " Box 1 description" ], [ "Box 2", "" ] ]

# Display the check box dialog and get the user's selection
checked = ccdd.getCheckBoxDialog("Check Box Dialog Test", boxes)

# Check if the Cancel button wasn't selected
if checked is not None:
    # Step through each check box
    for index in range(len(checked)):
        # Set the status text to indicate the check box is not checked
        status = " not"

        # Check if the check box is checked
        if checked[index]:
            # Set the status text to blank
            status = ""

        # Display the check box status
        print "Check box " \
              + boxes[index][0] \
              + " selection state is" \
              + status \
              + " checked "

# Open the output file
file = ccdd.openOutputFile("myFileName")

# Get the array of structure names
structNames = ccdd.getStructureTableNames()

# Step through each name found
for index in range(len(structNames)):

    # Write the structure name to the output file
    ccdd.writeToFileLn(file, "structNames[" + str(index) + "] = " + structNames[index])

# Close the output file
ccdd.closeFile(file)
