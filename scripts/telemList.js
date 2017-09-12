/******************************************************************************
 * Description: Output a CSV version of all telemetry items
 *
 * This JavaScript script generates a CSV file from  all table and packet information
 *****************************************************************************/

importClass(Packages.CCDD.CcddScriptDataAccessHandler);

var d = new Date(Date.now());

print("\n\n\nAt start for script parse:\n" + d.toString()+"\n");
print("Before getStructures()    " + Date.now() / 1000+"\n");

var structureNames = ccdd.getStructureTableNames();; // Get an array of the structures represented in the table data

print("After getStructures()     " + Date.now() / 1000+"\n");
//var numStructRows = ccdd.getStructureTableNumRows();
var outputFile ="";

print("Before getVariablePaths() " + Date.now() / 1000+"\n");
var csvTree = ccdd.getVariablePaths();
print("After getVariablePaths()  " + Date.now() / 1000+"\n");

function printLn(msg)
{
  print(msg+"\n");
}

function extract_var( str)
{
	var res = str.split("\\.");
	return res[1];
}

function extract_type( str)
{
	var res = str.split("\\.");
	return res[0];
}
function is_basetype( str)
{
    var res = str.split("\\.");
    var retval=res[0];
    if (res.length > 1)
    {
    	retval=res[1].split(":");
    }
//print("\n"+str+"   "+res[0]+"  "+res[1]);
if (res[0] == "float") return retval[0];
if (res[0] == "double") return retval[0];
if (res[0] == "int8") return retval[0];
if (res[0] == "int16") return retval[0];
if (res[0] == "int32") return retval[0];
if (res[0] == "int64") return retval[0];
if (res[0] == "uint8") return retval[0];
if (res[0] == "uint16") return retval[0];
if (res[0] == "uint32") return retval[0];
if (res[0] == "uint64") return retval[0];
if (res[0] == "char") return retval[0];
if (res[0] == "uchar") return retval[0];
return "NULL";
}

//is_basetype("float.v[2]");
make_csv();


function make_csv()
{
	// Get the number of structure table  rows

	//print(numStructRows);
	// Check if no structure data is supplied
//	if (numStructRows <= 0) { ccdd.showErrorDialog("No data supplied for script " + ccdd.getScriptName()); return;}

	    // Build the output file name
	outputFile = "CDD.csv";
	var file3 = ccdd.openOutputFile(outputFile); // Open the output file
	var file2 = ccdd.openOutputFile("raw"); // Open the output file
	var file  = ccdd.openOutputFile("foo3"); // Open the output file
	
	    // Check if the output file successfully opened
	if (file == null)
	  { ccdd.showErrorDialog("<html><b>Error opening output file '</b>" + outputFile + "<b>'"); return; } // Display an error dialog

	// Get the number of header files to include
	
	var num_rows=csvTree.length;
	var num_cols=0;
	var oneRow;

	var CSV_HEADER="System,BW,BitSize,Rate,Comment,DataType,Path";

   ccdd.writeToFileLn(file,CSV_HEADER);
   ccdd.writeToFileLn(file2,CSV_HEADER);
   ccdd.writeToFileLn(file3,CSV_HEADER);
print("Before CSV writting()     " + Date.now() / 1000+"\n");
   for (var row = 0; row < num_rows; row++)
	{
		oneRow=csvTree[row];
		var theSystem = ccdd.getTableDataFieldValue(oneRow[0], "System");


		if (theSystem==null) theSystem="";

		num_cols=oneRow.length;
		//printLn("row#"+row+":");
		//print("    ");
		var thisPath = oneRow[0];
		var tablePath = oneRow[0];
   	for (var col = 1; col < num_cols-1; col++)
		{
			thisPath += "," + is_basetype(oneRow[col]);
			tablePath += "," + oneRow[col];
		}

		var lastItem =  oneRow[num_cols-1];
		var baseType =  is_basetype(lastItem);
		var theSize = ccdd.getDataTypeSizeInBytes(extract_type(lastItem));
		if (theSize != null )
		{
			theSize = +theSize * 8;			
		}
		
			
//        var theComment = ccdd.getStructureDataByVariableName(tablePath, baseType,"description");
//		var theRate =    ccdd.getStructureDataByVariableName(tablePath, baseType, "cha");
//		var theRate2 =   ccdd.getStructureDataByVariableName(tablePath, baseType, "chb");
       var theRate = ccdd.getStructureTableData("ChA", row);
       var theRate2 = ccdd.getStructureTableData("ChB", row);
       var theComment = ccdd.getStructureTableData("Description",row);
   	

		var bitLength =  ccdd.getStructureDataByVariableName(tablePath, baseType,"bit length");

		if (bitLength != null && !bitLength.isEmpty())
		{
			theSize = bitLength;
		}

		if (theComment == null) { theComment=""; }
		theComment = theComment.split(',').join(';');
		//theComment.replace(/,/g, ";");
		if (theRate==null) { theRate=""; }
		if (theRate2==null) { theRate2=""; }
		if (theRate=="") { theRate=theRate2; }

		if (row < 150) print(tablePath+" +++++  " +baseType+ "   :::     bl=" +  bitLength+ "  " +extract_type(lastItem)+ " : "+ theRate +", "+theRate2+"\n");

		if (baseType == "NULL") { lastItem = ""; }


//ccdd.writeToFile(file3,row+";"); ccdd.writeToFile(file2,row+";"); ccdd.writeToFile(file,row+";");   // add in row number to Output files
		
		ccdd.writeToFileLn(file,theSystem+","+theSize+","+theRate+","+theComment+","+extract_type(lastItem)+","+thisPath+","+lastItem+",");
		


   	for (var col = 0; col < num_cols; col++)
			ccdd.writeToFile(file2,oneRow[col]+",");
		ccdd.writeToFileLn(file2,"");

		if ((num_cols == 1*0) || (is_basetype(lastItem) != "NULL") )
		{
			var BW="";
			if (theRate!="")
			{
				BW= +theSize * +theRate * .125;
			}
			ccdd.writeToFile(file3,theSystem+","+BW+","+theSize+","+theRate+","+theComment+","+extract_type(lastItem)+","+oneRow[0]+",");
		}
      if (is_basetype(lastItem) != "NULL")
		{
				for (var col = 1; col < num_cols; col++)
					ccdd.writeToFile(file3,extract_var(oneRow[col])+",");
				ccdd.writeToFile(file3,"");
		}
     // else { ccdd.writeToFileLn(file3,"");}

		if ((num_cols == 1*0) || (is_basetype(lastItem) != "NULL"))
		ccdd.writeToFileLn(file3,"")
	
    var structSize="";
}
print("After CSV writting()      " + Date.now() / 1000+"\n");
	ccdd.closeFile(file); // Close the output file
	ccdd.closeFile(file2); // Close the output file
	ccdd.closeFile(file3); // Close the output file
print("After closing files       " + Date.now() / 1000+"\n");
var d = new Date(Date.now());
print("After closing files  \n" + d.toString()+"\n\n\n");
}

