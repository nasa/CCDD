# Description: Python test script
import os
import sys

from py4j.java_gateway import JavaGateway
from py4j.java_collections import SetConverter, MapConverter, ListConverter

gateway = JavaGateway()    # Connect to the JVM
main = gateway.entry_point # Get the CCDD instance (better not have two running a server simo)
ccdd = main.getCcdd()      # Get the script access method instance

print("Test of a Python script using Py4J")

# Define the check boxes
boxes = [ [ "Box 1", " Box 1 description" ], [ "Box 2", "" ] ]
boxes_list = []

for box in boxes:
    box_list = ListConverter().convert(box, gateway._gateway_client)
    boxes_list.append(box_list)
    
java_list = ListConverter().convert(boxes_list, gateway._gateway_client)

# Display the check box dialog and get the user's selection
checked = ccdd.getCheckBoxDialog("Check Box Dialog Test", java_list)

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
        print("Check box " \
              + boxes[index][0] \
              + " selection state is" \
              + status \
              + " checked ")
else:
    print("No box checked or Cancel selected")

# Open the output file
file = ccdd.openOutputFile(ccdd.getOutputPath() + "myFileName")

# Get the array of structure names
structNames = ccdd.getStructureTableNames()

# Step through each name found
for index in range(len(structNames)):

    # Write the structure name to the output file
    ccdd.writeToFileLn(file, "structNames[" + str(index) + "] = " + structNames[index])
    print("structNames[" + str(index) + "] = " + structNames[index])

# Close the output file
ccdd.closeFile(file)
