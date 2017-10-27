# Description: Ruby test script

java_import Java::CCDD.CcddScriptDataAccessHandler

puts "Test of a Ruby script using JRuby"

# Define the check boxes
boxes = [ [ "Box 1", " Box 1 description" ], [ "Box 2", "" ] ]

# Display the check box dialog and get the user's selection
checked = $ccdd.getCheckBoxDialog("Check Box Dialog Test", boxes)

# Check if the Cancel button wasn't selected
if checked != nil
    # Step through each check box
    for index in 0..checked.length - 1
        # Display the check box status
        puts "Check box " + boxes[index][0] \
                + " selection state is" \
                + (checked[index] ? "" : " not") \
                + " checked"
    end
end

# Open the output file
file = $ccdd.openOutputFile("myFileName")

# Get the array of structure names
structNames = $ccdd.getStructureTableNames()

index = 0

# Step through each structure name
structNames.each do |name|

    # Write the structure name to the output file
    $ccdd.writeToFileLn(file, "structNames[#{index}] = #{name}")

    index += 1

end

# Close the output file
$ccdd.closeFile(file)
