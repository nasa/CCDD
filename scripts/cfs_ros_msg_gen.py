
from CCDD import CcddScriptDataAccessHandler
from collections import OrderedDict

CCDDPrimitiveTypes = ( \
    "int8_t", \
    "uint8_t", \
    "int16_t", \
    "uint16_t", \
    "int32_t", \
    "uint32_t", \
    "int64_t", \
    "uint64_t", \
    "float", \
    "double", \
    )

ROSPrimitiveTypes = ( \
    "int8", \
    "uint8", \
    "int16", \
    "uint16", \
    "int32", \
    "uint32", \
    "int64", \
    "uint64", \
    "float32", \
    "float64", \
    "string", \
    "bool", \
    "time", \
    "duration", \
    )

CcddToRosTypeConverter = {}

for i in range(10):
    CcddToRosTypeConverter[CCDDPrimitiveTypes[i]] = ROSPrimitiveTypes[i]

class ROSMessageDefinition:

    ############################# static data members ###############################

    KnownMessageTypes = []

    ############################### constructors ####################################

    def __init__(self, messageName):
        self.__fields = OrderedDict() # keys are field names (unique), and values are field types
        self.MessageName = messageName
        ROSMessageDefinition.KnownMessageTypes.append(messageName)

    @staticmethod
    def FromMessageFile(messageName, directoryPath=""):

        # TODO: this does not work in CCDD (works fine from normal python 
        # interpreter though) for some reason... need to investigate further...

        file = open(directoryPath + "/" + messageName + ".msg", 'r')
                    
        lines = file.read().splitlines()

        result = ROSMessageDefinition(messageName)

        for line in lines:
            
            if (line.startswith("#")):
                continue
            else:
                field = line.split()
                result.AddField(field[0], field[1])

        file.close()

        return result

    ############################### public methods ###################################

    def AddField(self, fieldType, fieldName):

        if fieldType.rstrip("[]") not in ROSPrimitiveTypes and fieldType.rstrip("[]") not in ROSMessageDefinition.KnownMessageTypes:
            raise ValueError("'fieldType' must be a ROSPrimitiveType or ROSMessageDefinition.KnownMessageType!")

        self.__fields[fieldName] = fieldType

    def Count(self):
        return len(self.__fields)

    def ToString(self):

        result = ""

        for fieldName in self.__fields:
            result += self.__fields[fieldName] + " " + fieldName + "\n"

        return result

    def ToMessageFile(self, directoryPath=""):
        
        file = ccdd.openOutputFile(directoryPath + self.MessageName + ".msg")
        ccdd.writeToFile(file, self.ToString())
        ccdd.closeFile(file)



# ######################### convert structure data ###########################

numberOfStructRows = ccdd.getStructureTableNumRows()
structureNames = ccdd.getStructureTablesByReferenceOrder()        

if (numberOfStructRows == 0):

    ccdd.showErrorDialog("No structure data supplied to script " + ccdd.getScriptName())

else:

    structureNames = ccdd.getStructureTablesByReferenceOrder()

    try:

        for structIndex in range(len(structureNames)):

            usedVariableNames = []

            currentStructName = structureNames[structIndex]

            rosMsg = ROSMessageDefinition(currentStructName)

            for rowIndex in range(numberOfStructRows):

                if currentStructName == ccdd.getStructureTableNameByRow(rowIndex):

                    variableName = ccdd.getStructureTableData("variable name", rowIndex)

                    if not variableName.endswith("]"):

                        if variableName not in usedVariableNames:

                            usedVariableNames.append(variableName)

                            memberType = ccdd.getStructureTableData("data type", rowIndex)

                            if memberType in CCDDPrimitiveTypes:
                                memberType = CcddToRosTypeConverter[memberType]

                            arraySize = ccdd.getStructureTableData("array size", rowIndex)

                            if arraySize is not None and arraySize:

                                # ros arrays are actually vectors, so they don't have a fixed-size...
                                rosMsg.AddField(memberType + "[]", variableName)

                            else:

                                rosMsg.AddField(memberType, variableName)

            if rosMsg.Count() > 0:
                rosMsg.ToMessageFile()

    except Exception as e:

        ccdd.showErrorDialog(str(e))
