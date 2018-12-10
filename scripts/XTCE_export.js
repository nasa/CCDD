/***************************************************************************************************
 * Description: Export the supplied data tables in extensible markup language (XML) telemetric and
 * command exchange (XTCE) format.
 * 
 * This JavaScript script exports the supplied data tables in XTCE format. The script uses a
 * combination of internal and external methods to accomplish the conversion. If an external method
 * is not provided in the script then the internal method is used, allowing for a user-defined mix
 * of internal and external methods.
 * 
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 **************************************************************************************************/

try
{
    load("nashorn:mozilla_compat.js");
    print("Test of a JavaScript script using Nashorn\n");
}
catch (e)
{
    print("Test of a JavaScript script using Rhino\n");
}

// CCCD script access class
importClass(Packages.CCDD.CcddScriptDataAccessHandler);

// Java classes
importClass(Packages.javax.xml.bind.JAXBContext);
importClass(Packages.javax.xml.bind.JAXBElement);
importClass(Packages.javax.xml.bind.JAXBException);
importClass(Packages.javax.xml.bind.Marshaller);
importClass(Packages.javax.xml.transform.dom.DOMSource);
importClass(Packages.javax.xml.transform.dom.DOMResult);
importClass(Packages.javax.xml.transform.OutputKeys);
importClass(Packages.javax.xml.transform.Transformer);
importClass(Packages.javax.xml.transform.TransformerFactory);
importClass(Packages.javax.xml.transform.stream.StreamResult);
importClass(Packages.java.math.BigInteger);

// JAXB classes
importClass(Packages.org.omg.space.xtce.AbsoluteTimeDataType);
importClass(Packages.org.omg.space.xtce.AggregateDataType);
importClass(Packages.org.omg.space.xtce.AlarmConditionsType);
importClass(Packages.org.omg.space.xtce.AlarmLevels);
importClass(Packages.org.omg.space.xtce.AlarmRangesType);
importClass(Packages.org.omg.space.xtce.AlarmType);
importClass(Packages.org.omg.space.xtce.AlgorithmSetType);
importClass(Packages.org.omg.space.xtce.AliasSetType);
importClass(Packages.org.omg.space.xtce.ANDedConditionsType);
importClass(Packages.org.omg.space.xtce.ArgumentTypeSetType);
importClass(Packages.org.omg.space.xtce.ArrayDataTypeType);
importClass(Packages.org.omg.space.xtce.ArrayParameterRefEntryType);
importClass(Packages.org.omg.space.xtce.BaseDataType);
importClass(Packages.org.omg.space.xtce.BaseTimeDataType);
importClass(Packages.org.omg.space.xtce.BinaryAlarmConditionType);
importClass(Packages.org.omg.space.xtce.BinaryDataEncodingType);
importClass(Packages.org.omg.space.xtce.BinaryDataType);
importClass(Packages.org.omg.space.xtce.BooleanAlarmType);
importClass(Packages.org.omg.space.xtce.BooleanDataType);
importClass(Packages.org.omg.space.xtce.BooleanExpressionType);
importClass(Packages.org.omg.space.xtce.ByteOrderType);
importClass(Packages.org.omg.space.xtce.CalibratorType);
importClass(Packages.org.omg.space.xtce.CommandContainerEntryListType);
importClass(Packages.org.omg.space.xtce.CommandContainerSetType);
importClass(Packages.org.omg.space.xtce.CommandContainerType);
importClass(Packages.org.omg.space.xtce.CommandMetaDataType);
importClass(Packages.org.omg.space.xtce.CommandVerifierType);
importClass(Packages.org.omg.space.xtce.ComparisonCheckType);
importClass(Packages.org.omg.space.xtce.ComparisonType);
importClass(Packages.org.omg.space.xtce.ContainerRefEntryType);
importClass(Packages.org.omg.space.xtce.ContainerRefType);
importClass(Packages.org.omg.space.xtce.ContainerSegmentRefEntryType);
importClass(Packages.org.omg.space.xtce.ContainerSetType);
importClass(Packages.org.omg.space.xtce.ContainerType);
importClass(Packages.org.omg.space.xtce.ContextCalibratorType);
importClass(Packages.org.omg.space.xtce.CustomStreamType);
importClass(Packages.org.omg.space.xtce.DataEncodingType);
importClass(Packages.org.omg.space.xtce.DecimalValueType);
importClass(Packages.org.omg.space.xtce.DescriptionType);
importClass(Packages.org.omg.space.xtce.EntryListType);
importClass(Packages.org.omg.space.xtce.EnumeratedDataType);
importClass(Packages.org.omg.space.xtce.EnumerationAlarmType);
importClass(Packages.org.omg.space.xtce.ErrorDetectCorrectType);
importClass(Packages.org.omg.space.xtce.FixedFrameStreamType);
importClass(Packages.org.omg.space.xtce.FloatDataEncodingType);
importClass(Packages.org.omg.space.xtce.FloatDataType);
importClass(Packages.org.omg.space.xtce.FloatRangeType);
importClass(Packages.org.omg.space.xtce.FrameStreamType);
importClass(Packages.org.omg.space.xtce.HeaderType);
importClass(Packages.org.omg.space.xtce.IndirectParameterRefEntryType);
importClass(Packages.org.omg.space.xtce.InputAlgorithmType);
importClass(Packages.org.omg.space.xtce.InputOutputAlgorithmType);
importClass(Packages.org.omg.space.xtce.InputOutputTriggerAlgorithmType);
importClass(Packages.org.omg.space.xtce.IntegerDataEncodingType);
importClass(Packages.org.omg.space.xtce.IntegerDataType);
importClass(Packages.org.omg.space.xtce.IntegerRangeType);
importClass(Packages.org.omg.space.xtce.IntegerValueType);
importClass(Packages.org.omg.space.xtce.MatchCriteriaType);
importClass(Packages.org.omg.space.xtce.MathAlgorithmType);
importClass(Packages.org.omg.space.xtce.MathOperationType);
importClass(Packages.org.omg.space.xtce.MessageRefType);
importClass(Packages.org.omg.space.xtce.MetaCommandType);
importClass(Packages.org.omg.space.xtce.NameDescriptionType);
importClass(Packages.org.omg.space.xtce.NumberToStringType);
importClass(Packages.org.omg.space.xtce.NumericAlarmType);
importClass(Packages.org.omg.space.xtce.NumericContextAlarmType);
importClass(Packages.org.omg.space.xtce.NumericDataType);
importClass(Packages.org.omg.space.xtce.ObjectFactory);
importClass(Packages.org.omg.space.xtce.OptionalNameDescriptionType);
importClass(Packages.org.omg.space.xtce.ORedConditionsType);
importClass(Packages.org.omg.space.xtce.ParameterInstanceRefType);
importClass(Packages.org.omg.space.xtce.ParameterPropertiesType);
importClass(Packages.org.omg.space.xtce.ParameterRefEntryType);
importClass(Packages.org.omg.space.xtce.ParameterRefType);
importClass(Packages.org.omg.space.xtce.ParameterSegmentRefEntryType);
importClass(Packages.org.omg.space.xtce.ParameterSetType);
importClass(Packages.org.omg.space.xtce.ParameterToSetType);
importClass(Packages.org.omg.space.xtce.ParameterTypeSetType);
importClass(Packages.org.omg.space.xtce.PCMStreamType);
importClass(Packages.org.omg.space.xtce.PhysicalAddressType);
importClass(Packages.org.omg.space.xtce.PolynomialType);
importClass(Packages.org.omg.space.xtce.RadixType);
importClass(Packages.org.omg.space.xtce.RateInStreamType);
importClass(Packages.org.omg.space.xtce.ReferenceTimeType);
importClass(Packages.org.omg.space.xtce.RelativeTimeDataType);
importClass(Packages.org.omg.space.xtce.RepeatType);
importClass(Packages.org.omg.space.xtce.SequenceContainerType);
importClass(Packages.org.omg.space.xtce.SequenceEntryType);
importClass(Packages.org.omg.space.xtce.ServiceRefType);
importClass(Packages.org.omg.space.xtce.ServiceType);
importClass(Packages.org.omg.space.xtce.SignificanceType);
importClass(Packages.org.omg.space.xtce.SimpleAlgorithmType);
importClass(Packages.org.omg.space.xtce.SpaceSystemType);
importClass(Packages.org.omg.space.xtce.SplinePointType);
importClass(Packages.org.omg.space.xtce.StreamRefType);
importClass(Packages.org.omg.space.xtce.StreamSegmentEntryType);
importClass(Packages.org.omg.space.xtce.StreamSetType);
importClass(Packages.org.omg.space.xtce.StringAlarmType);
importClass(Packages.org.omg.space.xtce.StringDataEncodingType);
importClass(Packages.org.omg.space.xtce.StringDataType);
importClass(Packages.org.omg.space.xtce.SyncStrategyType);
importClass(Packages.org.omg.space.xtce.TelemetryMetaDataType);
importClass(Packages.org.omg.space.xtce.TimeAlarmConditionType);
importClass(Packages.org.omg.space.xtce.TimeAlarmType);
importClass(Packages.org.omg.space.xtce.TimeAssociationType);
importClass(Packages.org.omg.space.xtce.TimeContextAlarmType);
importClass(Packages.org.omg.space.xtce.TimeUnits);
importClass(Packages.org.omg.space.xtce.TriggerSetType);
importClass(Packages.org.omg.space.xtce.UnitType);
importClass(Packages.org.omg.space.xtce.ValueEnumerationType);
importClass(Packages.org.omg.space.xtce.VariableFrameStreamType);
importClass(Packages.org.omg.space.xtce.VerifierEnumerationType);

// Basic primitive data types
var BasePrimitiveDataType =
{
    INTEGER : 0,
    FLOAT : 1,
    STRING : 2
};

// Endian type
EndianType =
{
    BIG_ENDIAN : 0,
    LITTLE_ENDIAN : 1
};

// Text appended to the parameter and command type and array references
var TYPE = "_Type";
var ARRAY = "_Array";

/***************************************************************************************************
 * Main
 **************************************************************************************************/
ccdd.xtceExport("XTCE_SCRIPT_OUTPUT", true, true, "1.0", "Working", "DOMAIN", "SYSTEM", "INTERFACE");

/***************************************************************************************************
 * Set the space system header attributes
 * 
 * @param factory
 *            object factory reference
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param classification
 *            classification attribute
 * 
 * @param validationStatus
 *            validation status attribute
 * 
 * @param version
 *            version attribute
 * 
 * @param date
 *            creation time and date
 **************************************************************************************************/
function addSpaceSystemHeader(factory, spaceSystem, classification, validationStatus, version, date)
{
    var header = factory.createHeaderType();
    header.setClassification(classification);
    header.setValidationStatus(validationStatus);
    header.setVersion(version);
    header.setDate(date);
    spaceSystem.setHeader(header);
}

/***************************************************************************************************
 * Create the space system telemetry metadata
 * 
 * @param factory
 *            object factory reference
 * 
 * @param spaceSystem
 *            space system reference
 **************************************************************************************************/
function createTelemetryMetadata(factory, spaceSystem)
{
    spaceSystem.setTelemetryMetaData(factory.createTelemetryMetaDataType());
}

/***************************************************************************************************
 * Add a structure table's parameters to the telemetry meta data
 * 
 * @param project
 *            top-level space system element reference
 * 
 * @param factory
 *            object factory reference
 * 
 * @param isBigEndian
 *            true if the data is big endian; false for little endian
 * 
 * @param isHeaderBigEndian
 *            true if the telemetry and command headers are big endian
 * 
 * @param tlmHeaderTable
 *            telemetry header table name
 * 
 * @param spaceSystem
 *            space system to which the table belongs
 * 
 * @param tableName
 *            table name
 * 
 * @param tableData
 *            array containing the table's data
 * 
 * @param varColumn
 *            variable (parameter) name column index
 * 
 * @param typeColumn
 *            parameter data type column index
 * 
 * @param sizeColumn
 *            parameter array size column index
 * 
 * @param bitColumn
 *            parameter bit length column index
 * 
 * @param enumColumn
 *            parameter enumeration column index; -1 if no the table has no enumeration column
 * 
 * @param descColumn
 *            parameter description column index; -1 if no the table has no description column
 * 
 * @param unitsColumn
 *            parameter units column index; -1 if no the table has no units column
 * 
 * @param minColumn
 *            minimum parameter value column index; -1 if no the table has no minimum column
 * 
 * @param maxColumn
 *            maximum parameter value column index; -1 if no the table has no maximum column
 * 
 * @param isTlmHdrTable
 *            true if this table represents the telemetry header or one of its descendants
 * 
 * @param tlmHdrSysPath
 *            telemetry header table system path; null or blank is none
 * 
 * @param isRootStructure
 *            true if the table is a root structure table
 * 
 * @param applicationIDName
 *            name of the telemetry header application ID data field
 * 
 * @param applicationID
 *            telemetry header application ID
 **************************************************************************************************/
function addSpaceSystemParameters(project, factory, isBigEndian, isHeaderBigEndian, tlmHeaderTable, spaceSystem, tableName, tableData, varColumn, typeColumn, sizeColumn, bitColumn, enumColumn, descColumn, unitsColumn, minColumn, maxColumn, isTlmHdrTable, tlmHdrSysPath, isRootStructure, applicationIDName, applicationID)
{
    var entryList = factory.createEntryListType();
    var isTlmHdrRef = false;

    // Step through each row in the structure table
    for (var rowIndex = 0; rowIndex < tableData.length; rowIndex++)
    {
        var rowData = tableData[rowIndex];

        // Check if the external method exists
        if (typeof addParameterAndType != "undefined")
        {
            // Use the external method to add the variable, if it has a primitive data type, to the
            // parameter set and parameter type set. Variables with structure data types are defined
            // in the container set
            addParameterAndType(factory,
                                isBigEndian,
                                isHeaderBigEndian,
                                tlmHeaderTable,
                                spaceSystem,
                                rowData[varColumn],
                                rowData[typeColumn],
                                rowData[sizeColumn],
                                rowData[bitColumn],
                                (enumColumn != -1 && rowData[enumColumn] != "" ? rowData[enumColumn] : null),
                                (unitsColumn != -1 && rowData[unitsColumn] != "" ? rowData[unitsColumn] : null),
                                (minColumn != -1 && rowData[minColumn] != "" ? rowData[minColumn] : null),
                                (maxColumn != -1 && rowData[maxColumn] != "" ? rowData[maxColumn] : null),
                                (descColumn != -1 && rowData[descColumn] != "" ? rowData[descColumn] : null),
                                (ccdd.isDataTypeString(rowData[typeColumn]) && rowData[sizeColumn] != "" ? parseInt(rowData[sizeColumn].replace(new RegExp("^.*(\\d+)$", "g"), "$1")) : 1));
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to add the variable, if it has a primitive data type, to the
            // parameter set and parameter type set. Variables with structure data types are defined
            // in the container set
            ccdd.xtceAddParameterAndType(spaceSystem,
                                         rowData[varColumn],
                                         rowData[typeColumn],
                                         rowData[sizeColumn],
                                         rowData[bitColumn],
                                         (enumColumn != -1 && rowData[enumColumn] != "" ? rowData[enumColumn] : null),
                                         (unitsColumn != -1 && rowData[unitsColumn] != "" ? rowData[unitsColumn] : null),
                                         (minColumn != -1 && rowData[minColumn] != "" ? rowData[minColumn] : null),
                                         (maxColumn != -1 && rowData[maxColumn] != "" ? rowData[maxColumn] : null),
                                         (descColumn != -1 && rowData[descColumn] != "" ? rowData[descColumn] : null),
                                         (ccdd.isDataTypeString(rowData[typeColumn]) && rowData[sizeColumn] != "" ? parseInt(rowData[sizeColumn].replace(new RegExp("^.*(\\d+)$", "g"), "$1")) : 1));
        }

        // Check if the external method exists
        if (typeof addParameterSequenceEntry != "undefined")
        {
            // Use the external method to add the variable, with either a primitive or structure
            // data type, to the container set
            isTlmHdrRef = addParameterSequenceEntry(factory, tlmHeaderTable, spaceSystem, rowData[varColumn], rowData[typeColumn], rowData[sizeColumn], entryList, isTlmHdrRef);
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to add the variable, with either a primitive or structure
            // data type, to the container set
            isTlmHdrRef = ccdd.xtceAddParameterSequenceEntry(spaceSystem, rowData[varColumn], rowData[typeColumn], rowData[sizeColumn], entryList, isTlmHdrRef);
        }
    }

    // Check if any variables were added to the entry list for the container set
    if (!entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().isEmpty())
    {
        // Create the sequence container set
        var containerSet = factory.createContainerSetType();
        var seqContainer = factory.createSequenceContainerType();
        seqContainer.setEntryList(entryList);
        containerSet.getSequenceContainer().add(seqContainer);

        // Use the last variable name in the table's path as the container name
        seqContainer.setName(ccdd.xmlCleanSystemPath(tableName.replace(new RegExp(".*\\."), "")));

        // Check if this is the telemetry header
        if (isTlmHdrTable)
        {
            // Set the abstract flag to indicate the telemetry metadata represents a telemetry
            // header
            seqContainer.setAbstract(true);
        }
        // Not the telemetry header. Check if this is a root structure that references the telemetry
        // header table (child structures don't require a reference to the telemetry header) and if
        // the application ID information is provided
        else if (isRootStructure && isTlmHdrRef && applicationIDName != null && applicationIDName != "" && applicationID != null && applicationID != "")
        {
            // Create a base container reference to the telemetry header table so that the message
            // ID can be assigned as a restriction criteria
            var baseContainer = factory.createSequenceContainerTypeBaseContainer();
            baseContainer.setContainerRef("/" + project.getValue().getName() + (tlmHdrSysPath == null || tlmHdrSysPath == "" ? "" : "/" + ccdd.xmlCleanSystemPath(tlmHdrSysPath)) + "/" + tlmHeaderTable + "/" + tlmHeaderTable);
            var restrictCriteria = factory.createSequenceContainerTypeBaseContainerRestrictionCriteria();
            var compList = factory.createMatchCriteriaTypeComparisonList();
            var compType = factory.createComparisonType();
            compType.setParameterRef(applicationIDName);
            compType.setValue(applicationID);
            compList.getComparison().add(compType);
            restrictCriteria.setComparisonList(compList);
            baseContainer.setRestrictionCriteria(restrictCriteria);
            seqContainer.setBaseContainer(baseContainer);
        }

        // Check if the telemetry metadata doesn't exit for this system
        if (spaceSystem.getTelemetryMetaData() == null)
        {
            // Check if the external method exists
            if (typeof createTelemetryMetadata != "undefined")
            {
                // Use the external method to create the telemetry metadata
                createTelemetryMetadata(factory, spaceSystem);
            }
            // The external method doesn't exist
            else
            {
                // Use the internal method to create the telemetry metadata
                ccdd.xtceCreateTelemetryMetadata(spaceSystem);
            }
        }

        // Add the parameters to the system
        spaceSystem.getTelemetryMetaData().setContainerSet(containerSet);
    }
}

/***************************************************************************************************
 * Add a parameter with a primitive data type to the parameter set and parameter type set
 * 
 * @param factory
 *            object factory reference
 * 
 * @param isBigEndian
 *            true if the data is big endian; false for little endian
 * 
 * @param isHeaderBigEndian
 *            true if the telemetry and command headers are big endian
 * 
 * @param tlmHeaderTable
 *            telemetry header table name
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param parameterName
 *            parameter name
 * 
 * @param dataType
 *            parameter primitive data type
 * 
 * @param arraySize
 *            parameter array size; null or blank if the parameter isn't an array
 * 
 * @param bitLength
 *            parameter bit length; null or blank if not a bit-wise parameter
 * 
 * @param enumeration
 *            enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify
 * 
 * @param units
 *            parameter units
 * 
 * @param minimum
 *            minimum parameter value
 * 
 * @param maximum
 *            maximum parameter value
 * 
 * @param description
 *            parameter description
 * 
 * @param stringSize
 *            size, in characters, of a string parameter; ignored if not a string or character
 **************************************************************************************************/
function addParameterAndType(factory, isBigEndian, isHeaderBigEndian, tlmHeaderTable, spaceSystem, parameterName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize)
{
    // Check if a data type is provided, that it's a primitive, and this isn't an array member. The
    // array definition is sufficient to define the array elements. Structure data types are handled
    // as containers.
    if (dataType != null && ccdd.isDataTypePrimitive(dataType) && !ccdd.isArrayMember(parameterName))
    {
        // Check if this system doesn't yet have its telemetry meta data created
        if (spaceSystem.getTelemetryMetaData() == null)
        {
            // Check if the external method exists
            if (typeof createTelemetryMetadata != "undefined")
            {
                // Use the external method to create the telemetry metadata
                createTelemetryMetadata(factory, spaceSystem);
            }
            // The external method doesn't exist
            else
            {
                // Use the internal method to create the telemetry metadata
                ccdd.xtceCreateTelemetryMetadata(spaceSystem);
            }
        }

        // Get the reference to the parameter set
        var parameterSet = spaceSystem.getTelemetryMetaData().getParameterSet();

        // Check if the parameter set doesn't exist
        if (parameterSet == null)
        {
            // Create the parameter set and its accompanying parameter type set
            parameterSet = factory.createParameterSetType();
            spaceSystem.getTelemetryMetaData().setParameterSet(parameterSet);
            spaceSystem.getTelemetryMetaData().setParameterTypeSet(factory.createParameterTypeSetType());
        }

        // Check if the external method exists
        if (typeof setParameterDataType != "undefined")
        {
            // Use the external method to set the parameter's data type information
            setParameterDataType(factory, isBigEndian, isHeaderBigEndian, tlmHeaderTable, spaceSystem, parameterName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize);
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to set the parameter's data type information
            ccdd.xtceSetParameterDataType(spaceSystem, parameterName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize);
        }

        // Create the parameter. This links the parameter name with the parameter reference type
        var parameter = factory.createParameterSetTypeParameter();
        parameter.setName(parameterName);
        parameter.setParameterTypeRef(parameterName + (arraySize == "" ? TYPE : ARRAY));

        parameterSet.getParameterOrParameterRef().add(parameter);
    }
}

/***************************************************************************************************
 * Add the parameter to the sequence container entry list
 * 
 * @param factory
 *            object factory reference
 * 
 * @param tlmHeaderTable
 *            telemetry header table name
 * 
 * @param spaceSystem
 *            reference to the space system to which the parameter belongs
 * 
 * @param parameterName
 *            parameter name
 * 
 * @param dataType
 *            data type
 * 
 * @param arraySize
 *            array size
 * 
 * @param entryList
 *            reference to the entry list into which to place the parameter (for a primitive data
 *            type) or container (for a structure data type) reference
 * 
 * @param isTlmHdrRef
 *            true if this table represents the telemetry header or one of its descendants
 * 
 * @return true if the parameter's data type references the telemetry header or one of its
 *         descendants; otherwise return the flag status unchanged
 **************************************************************************************************/
function addParameterSequenceEntry(factory, tlmHeaderTable, spaceSystem, parameterName, dataType, arraySize, entryList, isTlmHdrRef)
{
    // Check if the parameter is an array definition or member
    if (arraySize != null && arraySize != "")
    {
        // Check if this is the array definition (array members are ignored; the definition is
        // sufficient to describe the array)
        if (!ccdd.isArrayMember(parameterName))
        {
            // Check if the data type for this parameter is a primitive
            if (ccdd.isDataTypePrimitive(dataType))
            {
                // Get the list of dimensions for this parameter
                var dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                // Set the array dimension start index (always 0)
                var startVal = factory.createIntegerValueType();
                startVal.setFixedValue("0");

                // Get the array of dimension sizes
                var arrayDims = ccdd.getArrayIndexFromSize(arraySize);

                // Step through each array dimension
                for (var dimIndex = 0; dimIndex < arrayDims.length; dimIndex++)
                {
                    // Create the dimension and set the start and end indices (the end index is the
                    // number of elements in this array dimension)
                    var dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                    var endVal = factory.createIntegerValueType();
                    endVal.setFixedValue(arrayDims[dimIndex].toString());
                    dim.setStartingIndex(startVal);
                    dim.setEndingIndex(endVal);
                    dimList.getDimension().add(dim);
                }

                // Store the array parameter array reference in the list
                var arrayRef = factory.createArrayParameterRefEntryType();
                arrayRef.setParameterRef(parameterName);
                arrayRef.setDimensionList(dimList);
                entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(arrayRef);
            }
            // The data type reference is to a structure table
            else
            {
                // The XTCE aggregate data type would be used to define the structure reference, but
                // a limitation in the XTCE schema doesn't allow an array of structures to be
                // defined. In place of the aggregate data type, a sequence container is used to
                // define the table's members (for both primitive and structure data types). Each
                // individual structure array member has its own space system, and each of these has
                // an entry in the container

                // Check if the external method exists
                if (typeof addContainerReference != "undefined")
                {
                    // Use the external method to add container references to the space system in
                    // the sequence container entry list that defines each parameter array member
                    addContainerReference(factory, parameterName, dataType, arraySize, entryList);
                }
                // The external method doesn't exist
                else
                {
                    // Use the internal method to add container references to the space system in
                    // the sequence container entry list that defines each parameter array member
                    ccdd.xtceAddContainerReference(parameterName, dataType, arraySize, entryList);
                }
            }
        }
    }
    // Not an array definition or member. Check if this parameter has a primitive data type (i.e.,
    // it isn't an instance of a structure)
    else if (ccdd.isDataTypePrimitive(dataType))
    {
        // Store the non-array parameter reference in the list
        var parameterRef = factory.createParameterRefEntryType();
        parameterRef.setParameterRef(parameterName);
        entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(parameterRef);
    }
    // This is a non-array structure data type. Check if the reference isn't to the telemetry header
    // table
    else if (!dataType.equals(tlmHeaderTable))
    {
        // The XTCE aggregate data type would be used to define the structure reference, but a
        // limitation in the XTCE schema doesn't allow an array of structures to be defined. In
        // place of the aggregate data type, a sequence container is used to define the table's
        // members (for both primitive and structure data types). To be consistent with the
        // treatment of structure arrays, container references are also used for non-array structure
        // variables

        // Check if the external method exists
        if (typeof addContainerReference != "undefined")
        {
            // Use the external method to add container references to the space system in the
            // sequence container entry list that defines the parameter
            addContainerReference(factory, parameterName, dataType, arraySize, entryList);
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to add container references to the space system in the
            // sequence container entry list that defines the parameter
            ccdd.xtceAddContainerReference(parameterName, dataType, arraySize, entryList);
        }
    }
    // This is a reference to the telemetry header table
    else
    {
        // Set the flag indicating that a reference is made to the telemetry header table
        isTlmHdrRef = true;
    }

    return isTlmHdrRef;
}

/***************************************************************************************************
 * Create the telemetry parameter data type and set the specified attributes
 * 
 * @param factory
 *            object factory reference
 * 
 * @param isBigEndian
 *            true if the data is big endian; false for little endian
 * 
 * @param isHeaderBigEndian
 *            true if the telemetry and command headers are big endian
 * 
 * @param tlmHeaderTable
 *            telemetry header table name
 * 
 * @param spaceSystem
 *            space system
 * 
 * @param parameterName
 *            parameter name; null to not specify
 * 
 * @param dataType
 *            data type; null to not specify
 * 
 * @param arraySize
 *            parameter array size; null or blank if the parameter isn't an array
 * 
 * @param bitLength
 *            parameter bit length; null or empty if not a bit-wise parameter
 * 
 * @param enumeration
 *            enumeration in the format <enum label>|<enum value>[|...][,...]; null to not specify
 * 
 * @param units
 *            parameter units; null to not specify
 * 
 * @param minimum
 *            minimum parameter value; null to not specify
 * 
 * @param maximum
 *            maximum parameter value; null to not specify
 * 
 * @param description
 *            parameter description; null to not specify
 * 
 * @param stringSize
 *            size, in characters, of a string parameter; ignored if not a string or character
 **************************************************************************************************/
function setParameterDataType(factory, isBigEndian, isHeaderBigEndian, tlmHeaderTable, spaceSystem, parameterName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize)
{
    var parameterType = null;

    // TODO SET SIZE IN BITS IN BOTH THE TYPE AND THE ENCODING. WOULD USE ONE FOR BIT LENGTH AND
    // OTHER FOR DATA TYPE SIZE EXCEPT ONLY INTEGER TYPE HAS BOTH; ENUM ONLY HAS THE ENCODING SIZE
    // IN BITS

    // Check if the parameter is an array
    if (arraySize != null && arraySize != "")
    {
        // Create an array type and set its attributes
        var arrayType = factory.createArrayDataTypeType();
        arrayType.setName(parameterName + ARRAY);
        arrayType.setArrayTypeRef( (ccdd.isDataTypePrimitive(dataType) ? parameterName : dataType) + TYPE);
        arrayType.setNumberOfDimensions(BigInteger.valueOf(ccdd.getArrayIndexFromSize(arraySize).length));

        // Set the parameter's array information
        spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(arrayType);
    }

    var baseDataType = null;

    // Check if the external method exists
    if (typeof getBaseDataType != "undefined")
    {
        // Use the external method to get the base data type corresponding to the primitive data
        // type
        baseDataType = getBaseDataType(dataType);
    }
    // The external method doesn't exist
    else
    {
        // Use the internal method to get the base data type corresponding to the primitive data
        // type
        baseDataType = ccdd.xmlGetBaseDataType(dataType);
    }

    // Check if the a corresponding base data type exists
    if (baseDataType != null)
    {
        var intEncodingType = null;
        var unitSet = null;

        // Check if the external method exists
        if (typeof createUnitSet != "undefined")
        {
            // Use the external method to set the parameter units
            unitSet = createUnitSet(factory, units);
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to set the parameter units
            unitSet = ccdd.xtceCreateUnitSet(factory, units);
        }

        // Check if enumeration parameters are provided
        if (enumeration != null && enumeration != "")
        {
            // Create an enumeration type and enumeration list
            var enumType = factory.createParameterTypeSetTypeEnumeratedParameterType();
            var enumList = null;

            // Check if the external method exists
            if (typeof createEnumerationList != "undefined")
            {
                // Use the external method to create the enumeration list
                enumList = createEnumerationList(factory, spaceSystem, enumeration);
            }
            // The external method doesn't exist
            else
            {
                // Use the internal method to create the enumeration list
                enumList = ccdd.xtceCreateEnumerationList(factory, spaceSystem, enumeration);
            }

            // Set the integer encoding (the only encoding available for an enumeration) and the
            // size in bits
            intEncodingType = factory.createIntegerDataEncodingType();

            // Check if the parameter has a bit length
            if (bitLength != null && bitLength != "")
            {
                // Set the size in bits to the value supplied
                intEncodingType.setSizeInBits(BigInteger.valueOf(parseInt(bitLength)));
            }
            // Not a bit-wise parameter
            else
            {
                // Set the size in bits to the full size of the data type
                intEncodingType.setSizeInBits(BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType)));
            }

            // Check if the data type is an unsigned integer
            if (ccdd.isDataTypeUnsignedInt(dataType))
            {
                // Set the encoding type to indicate an unsigned integer
                intEncodingType.setEncoding("unsigned");
            }

            // Set the bit order
            intEncodingType.setBitOrder(isBigEndian || (isHeaderBigEndian && tlmHeaderTable.equals(ccdd.getPrototypeName(spaceSystem.getName()))) ? "mostSignificantBitFirst" : "leastSignificantBitFirst");

            enumType.setIntegerDataEncoding(intEncodingType);

            // Set the enumeration list and units
            enumType.setEnumerationList(enumList);
            enumType.setUnitSet(unitSet);

            parameterType = enumType;
        }
        // Not an enumeration
        else
        {
            switch (baseDataType)
            {
                case BasePrimitiveDataType.INTEGER:
                    // Create an integer parameter and set its attributes
                    var integerType = factory.createParameterTypeSetTypeIntegerParameterType();
                    intEncodingType = factory.createIntegerDataEncodingType();

                    var intSizeInBits;

                    // Check if the parameter has a bit length
                    if (bitLength != null && bitLength != "")
                    {
                        // Set the size in bits to the value supplied
                        intSizeInBits = BigInteger.valueOf(parseInt(bitLength));
                    }
                    // Not a bit-wise parameter
                    else
                    {
                        // Get the bit size of the integer type
                        intSizeInBits = BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType));
                    }

                    // Set the encoding type to indicate an unsigned integer
                    integerType.setSizeInBits(intSizeInBits);
                    intEncodingType.setSizeInBits(intSizeInBits);

                    // Check if the data type is an unsigned integer
                    if (ccdd.isDataTypeUnsignedInt(dataType))
                    {
                        // Set the encoding type to indicate an unsigned integer
                        integerType.setSigned(false);
                        intEncodingType.setEncoding("unsigned");
                    }

                    // Set the bit order
                    intEncodingType.setBitOrder(isBigEndian || (isHeaderBigEndian && tlmHeaderTable.equals(ccdd.getPrototypeName(spaceSystem.getName()))) ? "mostSignificantBitFirst" : "leastSignificantBitFirst");

                    // Set the encoding type and units
                    integerType.setIntegerDataEncoding(intEncodingType);
                    integerType.setUnitSet(unitSet);

                    // Check if a minimum or maximum value is specified
                    if ( (minimum != null && minimum != "") || (maximum != null && maximum != ""))
                    {
                        var range = factory.createIntegerRangeType();

                        // Check if a minimum value is specified
                        if (minimum != null && minimum != "")
                        {
                            // Set the minimum value
                            range.setMinInclusive(minimum);
                        }

                        // Check if a maximum value is specified
                        if (maximum != null && maximum != "")
                        {
                            // Set the maximum value
                            range.setMaxInclusive(maximum);
                        }

                        integerType.setValidRange(range);
                    }

                    parameterType = integerType;
                    break;

                case BasePrimitiveDataType.FLOAT:
                    // Get the bit size of the float type
                    var floatSizeInBits = BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType));

                    // Create a float parameter and set its attributes
                    var floatType = factory.createParameterTypeSetTypeFloatParameterType();
                    floatType.setUnitSet(unitSet);
                    floatType.setSizeInBits(floatSizeInBits);
                    var floatEncodingType = factory.createFloatDataEncodingType();
                    floatEncodingType.setSizeInBits(floatSizeInBits);
                    floatEncodingType.setEncoding("IEEE754_1985");
                    floatType.setFloatDataEncoding(floatEncodingType);
                    floatType.setUnitSet(unitSet);

                    // Check if a minimum or maximum value is specified
                    if ( (minimum != null && minimum != "") || (maximum != null && maximum != ""))
                    {
                        var range = factory.createFloatRangeType();

                        // Check if a minimum value is specified
                        if (minimum != null && minimum != "")
                        {
                            // Set the minimum value
                            range.setMinInclusive(parseFloat(minimum));
                        }

                        // Check if a maximum value is specified
                        if (maximum != null && maximum != "")
                        {
                            // Set the maximum value
                            range.setMaxInclusive(parseFloat(maximum));
                        }

                        floatType.setValidRange(range);
                    }

                    parameterType = floatType;
                    break;

                case BasePrimitiveDataType.STRING:
                    // Create a string parameter and set its attributes
                    var stringType = factory.createParameterTypeSetTypeStringParameterType();
                    StringDataEncodingType
                    stringEncodingType = factory.createStringDataEncodingType();

                    // Set the string's size in bits based on the number of characters in the string
                    // with each character occupying a single byte
                    var intValType = factory.createIntegerValueType();
                    intValType.setFixedValue( (stringSize * 8).toString());
                    var stringSizeInBits = factory.createStringDataEncodingTypeSizeInBits();
                    stringSizeInBits.setFixed(intValType);
                    stringEncodingType.setSizeInBits(stringSizeInBits);
                    stringEncodingType.setEncoding("UTF-8");
                    stringType.setStringDataEncoding(stringEncodingType);
                    stringType.setUnitSet(unitSet);
                    parameterType = stringType;
                    break;
            }
        }
    }

    // Set the parameter type name
    parameterType.setName(parameterName + TYPE);

    // Check is a description exists
    if (description != null && description != "")
    {
        // Set the description attribute
        parameterType.setLongDescription(description);
    }

    // Set the parameter's data type information
    spaceSystem.getTelemetryMetaData().getParameterTypeSet().getStringParameterTypeOrEnumeratedParameterTypeOrIntegerParameterType().add(parameterType);
}

/***************************************************************************************************
 * Create the space system command metadata
 * 
 * @param factory
 *            object factory reference
 * 
 * @param spaceSystem
 *            space system reference
 **************************************************************************************************/
function createCommandMetadata(factory, spaceSystem)
{
    spaceSystem.setCommandMetaData(factory.createCommandMetaDataType());
    spaceSystem.getCommandMetaData().setMetaCommandSet(factory.createCommandMetaDataTypeMetaCommandSet());
}

/***************************************************************************************************
 * Add the command(s) from a table to the specified space system
 * 
 * @param project
 *            top-level space system element reference
 * 
 * @param factory
 *            object factory reference
 * 
 * @param isBigEndian
 *            true if the data is big endian; false for little endian
 * 
 * @param isHeaderBigEndian
 *            true if the telemetry and command headers are big endian
 * 
 * @param cmdHeaderTable
 *            command header table name
 * 
 * @param commandArguments
 *            array of AssociatedColumns class instances that have the associated command argument
 *            column indices
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param tableData
 *            table data array
 * 
 * @param cmdNameColumn
 *            command name column index
 * 
 * @param cmdCodeColumn
 *            command code column index
 * 
 * @param cmdDescColumn
 *            command description column index
 * 
 * @param isCmdHeader
 *            true if this table represents the command header
 * 
 * @param cmdHdrSysPath
 *            command header table system path
 * 
 * @param cmdFuncCodeName
 *            command code name
 * 
 * @param applicationIDName
 *            name of the application ID
 * 
 * @param applicationID
 *            application ID
 **************************************************************************************************/
function addSpaceSystemCommands(project, factory, isBigEndian, isHeaderBigEndian, cmdHeaderTable, commandArguments, spaceSystem, tableData, cmdNameColumn, cmdCodeColumn, cmdDescColumn, isCmdHeader, cmdHdrSysPath, cmdFuncCodeName, applicationIDName, applicationID)
{
    // Step through each row in the table
    for (var rowIndex = 0; rowIndex < tableData.length; rowIndex++)
    {
        rowData = tableData[rowIndex];

        // Check if the command name exists; if the argument name is missing then the entire
        // argument is ignored
        if (cmdNameColumn != -1 && rowData[cmdNameColumn] != "")
        {
            // Store the command name
            var commandName = ccdd.xmlCleanSystemPath(rowData[cmdNameColumn]);

            // Initialize the command attributes and argument names list
            var cmdFuncCode = null;
            var commandDescription = null;
            var argumentNames = new Array(0);
            var argDataTypes = new Array(0);
            var argArraySizes = new Array(0);

            // Check if this system doesn't yet have its command metadata created
            if (spaceSystem.getCommandMetaData() == null)
            {
                // Check if the external method exists
                if (typeof addCommand != "undefined")
                {
                    // Use the external method to create the command metadata
                    createCommandMetadata(factory, spaceSystem);
                }
                // The external method doesn't exist
                else
                {
                    // Use the internal method to create the command metadata
                    ccdd.xtceCreateCommandMetadata(spaceSystem);
                }
            }

            // Check if the command code exists
            if (cmdCodeColumn != -1 && rowData[cmdCodeColumn] != "")
            {
                // Store the command code
                cmdFuncCode = rowData[cmdCodeColumn];
            }

            // Check if the command description exists
            if (cmdDescColumn != -1 && rowData[cmdDescColumn] != "")
            {
                // Store the command description
                commandDescription = rowData[cmdDescColumn];
            }

            // Step through each command argument column grouping
            for (argIndex = 0; argIndex < commandArguments.length; argIndex++)
            {
                cmdArg = commandArguments[argIndex];

                // Initialize the command argument attributes
                var argumentName = null;
                var dataType = null;
                var arraySize = null;
                var bitLength = null;
                var enumeration = null;
                var minimum = null;
                var maximum = null;
                var units = null;
                var description = null;
                var stringSize = 1;

                // Check if the command argument name and data type exist
                if (cmdArg.getName() != -1 && rowData[cmdArg.getName()] != "" && cmdArg.getDataType() != -1 && rowData[cmdArg.getDataType()] != "")
                {
                    var uniqueID = "";
                    var dupCount = 0;

                    // Store the command argument name and data type
                    argumentName = rowData[cmdArg.getName()];
                    dataType = rowData[cmdArg.getDataType()];

                    // Check if the description column exists
                    if (cmdArg.getDescription() != -1 && rowData[cmdArg.getDescription()] != "")
                    {
                        // Store the command argument description
                        description = rowData[cmdArg.getDescription()];
                    }

                    // Check if the array size column exists
                    if (cmdArg.getArraySize() != -1 && rowData[cmdArg.getArraySize()] != "")
                    {
                        // Store the command argument array size value
                        arraySize = rowData[cmdArg.getArraySize()];

                        // Check if the command argument has a string data type
                        if (ccdd.isDataTypeString(rowData[cmdArg.getDataType()]))
                        {
                            // Separate the array dimension values and get the string size
                            var arrayDims = ccdd.getArrayIndexFromSize(arraySize);
                            stringSize = arrayDims[0];
                        }
                    }

                    // Check if the bit length column exists
                    if (cmdArg.getBitLength() != -1 && rowData[cmdArg.getBitLength()] != "")
                    {
                        // Store the command argument bit length value
                        bitLength = rowData[cmdArg.getBitLength()];
                    }

                    // Check if the enumeration column exists
                    if (cmdArg.getEnumeration() != -1 && rowData[cmdArg.getEnumeration()] != "")
                    {
                        // Store the command argument enumeration value
                        enumeration = rowData[cmdArg.getEnumeration()];
                    }

                    // Check if the units column exists
                    if (cmdArg.getUnits() != -1 && rowData[cmdArg.getUnits()] != "")
                    {
                        // Store the command argument units
                        units = rowData[cmdArg.getUnits()];
                    }

                    // Check if the minimum column exists
                    if (cmdArg.getMinimum() != -1 && rowData[cmdArg.getMinimum()] != "")
                    {
                        // Store the command argument minimum value
                        minimum = rowData[cmdArg.getMinimum()];
                    }

                    // Check if the maximum column exists
                    if (cmdArg.getMaximum() != -1 && rowData[cmdArg.getMaximum()] != "")
                    {
                        // Store the command argument maximum value
                        maximum = rowData[cmdArg.getMaximum()];
                    }

                    // Step through the list of argument names used so far
                    for (var nameIndex = 0; nameIndex < argumentNames.length; nameIndex++)
                    {
                        // Check if the current argument name matches an existing one
                        if (argumentName.equals(argumentNames[nameIndex]))
                        {
                            // Increment the duplicate name count
                            dupCount++;
                        }
                    }

                    // Check if a duplicate argument name exists
                    if (dupCount != 0)
                    {
                        // Set the unique ID to the counter value
                        uniqueID = (dupCount + 1).toString();
                    }

                    // Add the name and array status to the lists
                    argumentNames.push(argumentName);
                    argDataTypes.push(dataType);
                    argArraySizes.push(arraySize);

                    // Check if the data type is a primitive. The data type for the command can be a
                    // structure reference if this is the command header table or a descendant table
                    // of the command header table
                    if (ccdd.isDataTypePrimitive(dataType))
                    {
                        // get the reference to the argument type set
                        var argument = spaceSystem.getCommandMetaData().getArgumentTypeSet();

                        // Check if the argument type set doesn't exist
                        if (argument == null)
                        {
                            // Create the argument type set
                            argument = factory.createArgumentTypeSetType();
                            spaceSystem.getCommandMetaData().setArgumentTypeSet(argument);
                        }

                        var type = null;

                        // Check if the external method exists
                        if (typeof addCommand != "undefined")
                        {
                            // Use the external method to set the command argument data type
                            // information
                            type = setArgumentDataType(factory, isBigEndian, isHeaderBigEndian, cmdHeaderTable, spaceSystem, argumentName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize, uniqueID);
                        }
                        // The external method doesn't exist
                        else
                        {
                            // Use the internal method to set the command argument data type
                            // information
                            type = ccdd.xtceSetArgumentDataType(spaceSystem, argumentName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize, uniqueID);
                        }

                        // Add the command argument type to the command space system
                        argument.getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(type);
                    }
                }
            }

            // Check if the external method exists
            if (typeof addCommand != "undefined")
            {
                // Use the external method to add the command metadata set information
                addCommand(project, factory, cmdHeaderTable, spaceSystem, commandName, cmdFuncCodeName, cmdFuncCode, applicationIDName, applicationID, isCmdHeader, cmdHdrSysPath, argumentNames, argDataTypes, argArraySizes, commandDescription);
            }
            // The external method doesn't exist
            else
            {
                // Use the internal method to add the command metadata set information
                ccdd.xtceAddCommand(spaceSystem, commandName, cmdFuncCode, applicationID, isCmdHeader, cmdHdrSysPath, argumentNames, argDataTypes, argArraySizes, commandDescription);
            }
        }
    }
}

/***************************************************************************************************
 * Add a command to the command metadata set
 * 
 * @param project
 *            top-level space system element reference
 * 
 * @param factory
 *            object factory reference
 * 
 * @param cmdHeaderTable
 *            command header table name
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param commandName
 *            command name
 * 
 * @param cmdFuncCodeName
 *            command code name
 * 
 * @param cmdFuncCode
 *            command code
 * 
 * @param applicationIDName
 *            name of the application ID
 * 
 * @param applicationID
 *            application ID
 * 
 * @param isCmdHeader
 *            true if this table represents the command header
 * 
 * @param cmdHdrSysPath
 *            command header table system path
 * 
 * @param argumentNames
 *            array of command argument names
 * 
 * @param argDataTypes
 *            array of of command argument data types
 * 
 * @param argArraySizes
 *            array of of command argument array sizes; the array item is null or blank if the
 *            corresponding argument isn't an array
 * 
 * @param description
 *            description of the command
 * 
 * @throws CCDDException
 *             error occurred executing an external (script) method
 **************************************************************************************************/
function addCommand(project, factory, cmdHeaderTable, spaceSystem, commandName, cmdFuncCodeName, cmdFuncCode, applicationIDName, applicationID, isCmdHeader, cmdHdrSysPath, argumentNames, argDataTypes, argArraySizes, description)
{
    var commandSet = spaceSystem.getCommandMetaData().getMetaCommandSet();
    var command = factory.createMetaCommandType();

    // Check is a command name exists
    if (commandName != null && commandName != "")
    {
        // Set the command name attribute
        command.setName(commandName);
    }

    // Check is a command description exists
    if (description != null && description != "")
    {
        // Set the command description attribute
        command.setLongDescription(description);
    }

    // Check if the command has any arguments
    if (argumentNames.length != 0)
    {
        var index = 0;
        var argList = null;
        var cmdContainer = factory.createCommandContainerType();
        cmdContainer.setName(commandName);
        var entryList = factory.createCommandContainerEntryListType();

        // Step through each argument
        for (var argIndex = 0; argIndex < argumentNames.length; argIndex++)
        {
            argumentName = argumentNames[argIndex];

            var argDataType = argDataTypes[index];
            var argArraySize = argArraySizes[index];

            // Set the flag to indicate that the argument is an array
            var isArray = argArraySize != null && argArraySize != "";

            // Check if the argument data type is a primitive
            if (ccdd.isDataTypePrimitive(argDataType))
            {
                // Check if this is the first argument
                if (argList == null)
                {
                    argList = factory.createMetaCommandTypeArgumentList();
                }

                // Add the argument to the the command's argument list
                var arg = factory.createMetaCommandTypeArgumentListArgument();
                arg.setName(argumentName);
                arg.setArgumentTypeRef(argumentName + (isArray ? ARRAY : TYPE));
                argList.getArgument().add(arg);

                // Check if the command argument is an array
                if (isArray)
                {
                    // Get the list of dimensions for this argument
                    var dimList = factory.createArrayParameterRefEntryTypeDimensionList();

                    // Set the array dimension start index (always 0)
                    var startVal = factory.createIntegerValueType();
                    startVal.setFixedValue("0");

                    // Get the array dimensions
                    arrayDims = ccdd.getArrayIndexFromSize(argArraySize);

                    // Step through each array dimension
                    for (var dimIndex = 0; dimIndex < arrayDims.length; dimIndex++)
                    {
                        // Create the dimension and set the start and end indices (the end index is
                        // the number of elements in this array dimension)
                        var dim = factory.createArrayParameterRefEntryTypeDimensionListDimension();
                        var endVal = factory.createIntegerValueType();
                        endVal.setFixedValue(arrayDims[dimIndex].toString());
                        dim.setStartingIndex(startVal);
                        dim.setEndingIndex(endVal);
                        dimList.getDimension().add(dim);
                    }

                    // Store the array parameter array reference in the list
                    var arrayRef = factory.createArrayParameterRefEntryType();
                    arrayRef.setParameterRef(argumentName);
                    arrayRef.setDimensionList(dimList);
                    var arrayRefElem = factory.createCommandContainerEntryListTypeArrayArgumentRefEntry(arrayRef);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(arrayRefElem);
                }
                // Not an array
                else
                {
                    // Store the argument reference in the list
                    var argumentRef = factory.createCommandContainerEntryListTypeArgumentRefEntry();
                    argumentRef.setArgumentRef(argumentName);
                    var argumentRefElem = factory.createCommandContainerEntryListTypeArgumentRefEntry(argumentRef);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(argumentRefElem);
                }
            }
            // The argument data type is a structure reference. This occurs if this is the command
            // header table or a descendant table of the command header table
            else
            {
                // Check if the external method exists
                if (typeof addContainerReference != "undefined")
                {
                    // Use the external method to add a container reference (or references if the
                    // argument is an array) to the space system in the command container entry list
                    // that defines the argument
                    addContainerReference(factory, argumentName, argDataType, argArraySize, entryList);
                }
                // The external method doesn't exist
                else
                {
                    // Use the internal method to add a container reference (or references if the
                    // argument is an array) to the space system in the command container entry list
                    // that defines the argument
                    ccdd.xtceAddContainerReference(argumentName, argDataType, argArraySize, entryList);
                }
            }

            index++;
        }

        // Check if this table represents the command header
        if (isCmdHeader)
        {
            // Set the abstract flag to indicate the command metadata represents a command header
            command.setAbstract(true);
        }
        // Not the command header. Check if the command application ID and command header table name
        // are provided
        else if (applicationID != null && applicationID != "" && cmdHeaderTable != null && cmdHeaderTable != "")
        {
            // Create the reference to the base meta-command and set it to the empty base, in case
            // no command header is defined
            var baseCmd = factory.createMetaCommandTypeBaseMetaCommand();
            baseCmd.setMetaCommandRef(ccdd.xmlCleanSystemPath("/" + project.getValue().getName() + (cmdHdrSysPath == null || cmdHdrSysPath == "" ? "" : "/" + cmdHdrSysPath) + "/" + cmdHeaderTable + "/" + cmdHeaderTable));

            // Create the argument assignment list and store the application ID
            var argAssnList = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentList();
            var argAssn = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentListArgumentAssignment();
            argAssn.setArgumentName(applicationIDName);
            argAssn.setArgumentValue(applicationID);
            argAssnList.getArgumentAssignment().add(argAssn);

            // Check if a command code is provided
            if (cmdFuncCode != null && cmdFuncCode != "")
            {
                // Store the command code
                argAssn = factory.createMetaCommandTypeBaseMetaCommandArgumentAssignmentListArgumentAssignment();
                argAssn.setArgumentName(cmdFuncCodeName);
                argAssn.setArgumentValue(cmdFuncCode);
                argAssnList.getArgumentAssignment().add(argAssn);
            }

            baseCmd.setArgumentAssignmentList(argAssnList);
            command.setBaseMetaCommand(baseCmd);
        }

        // Check if the command references any primitive data types
        if (argList != null)
        {
            command.setArgumentList(argList);
        }

        cmdContainer.setEntryList(entryList);
        command.setCommandContainer(cmdContainer);
    }

    commandSet.getMetaCommandOrMetaCommandRefOrBlockMetaCommand().add(command);
}

/***************************************************************************************************
 * Set the command argument data type and set the specified attributes
 * 
 * @param isBigEndian
 *            true if the data is big endian; false for little endian
 * 
 * @param isHeaderBigEndian
 *            true if the telemetry and command headers are big endian
 * 
 * @param cmdHeaderTable
 *            command header table name
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param argumentName
 *            command argument name; null to not specify
 * 
 * @param dataType
 *            command argument data type; null to not specify
 * 
 * @param arraySize
 *            command argument array size; null or blank if the argument isn't an array
 * 
 * @param bitLength
 *            command argument bit length
 * 
 * @param enumeration
 *            command argument enumeration in the format <enum label>|<enum value>[|...][,...];
 *            null to not specify
 * 
 * @param units
 *            command argument units; null to not specify
 * 
 * @param minimum
 *            minimum parameter value; null to not specify
 * 
 * @param maximum
 *            maximum parameter value; null to not specify
 * 
 * @param description
 *            command argument description ; null to not specify
 * 
 * @param stringSize
 *            string size in bytes; ignored if the command argument does not have a string data type
 * 
 * @return Command description of the type corresponding to the primitive data type with the
 *         specified attributes set
 * 
 * @param uniqueID
 *            text used to uniquely identify data types with the same name; blank if the data type
 *            has no name conflict
 **************************************************************************************************/
function setArgumentDataType(factory, isBigEndian, isHeaderBigEndian, cmdHeaderTable, spaceSystem, argumentName, dataType, arraySize, bitLength, enumeration, units, minimum, maximum, description, stringSize, uniqueID)
{
    var commandDescription = null;

    // Check if the argument is an array
    if (arraySize != null && arraySize != "")
    {
        // Create an array type and set its attributes
        var arrayType = factory.createArrayDataTypeType();
        arrayType.setName(argumentName + ARRAY);
        arrayType.setNumberOfDimensions(BigInteger.valueOf(ccdd.getArrayIndexFromSize(arraySize).length));
        arrayType.setArrayTypeRef(argumentName + TYPE);

        // Set the argument's array information
        spaceSystem.getCommandMetaData().getArgumentTypeSet().getStringArgumentTypeOrEnumeratedArgumentTypeOrIntegerArgumentType().add(arrayType);
    }

    var baseDataType = null;

    // Check if the external method exists
    if (typeof getBaseDataType != "undefined")
    {
        // Use the external method to get the base data type corresponding to the primitive data
        // type
        baseDataType = getBaseDataType(dataType);
    }
    // The external method doesn't exist
    else
    {
        // Use the internal method to get the base data type corresponding to the primitive data
        // type
        baseDataType = ccdd.xmlGetBaseDataType(dataType);
    }

    // Check if the a corresponding base data type exists
    if (baseDataType != null)
    {
        var unitSet = null;
        var intEncodingType = null;

        // Check if the external method exists
        if (typeof createUnitSet != "undefined")
        {
            // Use the external method to set the parameter units
            unitSet = createUnitSet(factory, units);
        }
        // The external method doesn't exist
        else
        {
            // Use the internal method to set the parameter units
            unitSet = ccdd.xtceCreateUnitSet(factory, units);
        }

        // Check if enumeration parameters are provided
        if (enumeration != null && enumeration != "")
        {
            // Create an enumeration type and enumeration list
            var enumType = factory.createEnumeratedDataType();
            var enumList = null;

            // Check if the external method exists
            if (typeof createEnumerationList != "undefined")
            {
                // Use the external method to create the enumeration list
                enumList = createEnumerationList(factory, spaceSystem, enumeration);
            }
            // The external method doesn't exist
            else
            {
                // Use the internal method to create the enumeration list
                enumList = ccdd.xtceCreateEnumerationList(factory, spaceSystem, enumeration);
            }

            // Set the integer encoding (the only encoding available for an enumeration) and the
            // size in bits
            intEncodingType = factory.createIntegerDataEncodingType();

            // Check if the parameter has a bit length
            if (bitLength != null && bitLength != "")
            {
                // Set the size in bits to the value supplied
                intEncodingType.setSizeInBits(BigInteger.valueOf(parseInt(bitLength)));
            }
            // Not a bit-wise parameter
            else
            {
                // Set the size in bits to the full size of the data type
                intEncodingType.setSizeInBits(BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType)));
            }

            // Set the enumeration list and units attributes
            enumType.setEnumerationList(enumList);
            enumType.setUnitSet(unitSet);

            // Check if the data type is an unsigned integer
            if (ccdd.isDataTypeUnsignedInt(dataType))
            {
                // Set the encoding type to indicate an unsigned integer
                intEncodingType.setEncoding("unsigned");
            }

            // Set the bit order
            intEncodingType.setBitOrder(isBigEndian || (isHeaderBigEndian && cmdHeaderTable.equals(spaceSystem.getName())) ? "mostSignificantBitFirst" : "leastSignificantBitFirst");

            enumType.setIntegerDataEncoding(intEncodingType);
            commandDescription = enumType;
        }
        // This is not an enumerated command argument
        else
        {
            switch (baseDataType)
            {
                case BasePrimitiveDataType.INTEGER:
                    // Create an integer command argument and set its attributes
                    var integerType = factory.createArgumentTypeSetTypeIntegerArgumentType();
                    intEncodingType = factory.createIntegerDataEncodingType();

                    var intSizeInBits;

                    // Check if the parameter has a bit length
                    if (bitLength != null && bitLength != "")
                    {
                        // Get the bit length of the argument
                        intSizeInBits = BigInteger.valueOf(parseInt(bitLength));
                    }
                    // Not a bit-wise parameter
                    else
                    {
                        // Get the bit size of of the integer type
                        intSizeInBits = BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType));
                    }

                    // Set the size in bits to the full size of the data type
                    integerType.setSizeInBits(intSizeInBits);
                    intEncodingType.setSizeInBits(intSizeInBits);

                    // Check if the data type is an unsigned integer
                    if (ccdd.isDataTypeUnsignedInt(dataType))
                    {
                        // Set the encoding type to indicate an unsigned integer
                        integerType.setSigned(false);
                        intEncodingType.setEncoding("unsigned");
                    }

                    // Set the bit order
                    intEncodingType.setBitOrder(isBigEndian || (isHeaderBigEndian && cmdHeaderTable.equals(spaceSystem.getName())) ? "mostSignificantBitFirst" : "leastSignificantBitFirst");

                    // Set the encoding type and units
                    integerType.setIntegerDataEncoding(intEncodingType);
                    integerType.setUnitSet(unitSet);

                    // Check if a minimum or maximum value is specified
                    if ( (minimum != null && minimum != "") || (maximum != null && maximum != ""))
                    {
                        var validRange = factory.createArgumentTypeSetTypeIntegerArgumentTypeValidRangeSet();
                        var range = factory.createIntegerRangeType();

                        // Check if a minimum value is specified
                        if (minimum != null && minimum != "")
                        {
                            // Set the minimum value
                            range.setMinInclusive(minimum);
                        }

                        // Check if a maximum value is specified
                        if (maximum != null && maximum != "")
                        {
                            // Set the maximum value
                            range.setMaxInclusive(maximum);
                        }

                        validRange.getValidRange().add(range);
                        integerType.setValidRangeSet(validRange);
                    }

                    commandDescription = integerType;
                    break;

                case BasePrimitiveDataType.FLOAT:
                    // Get the bit size of the float type
                    var floatSizeInBits = BigInteger.valueOf(ccdd.getDataTypeSizeInBits(dataType));

                    // Create a float command argument and set its attributes
                    var floatType = factory.createArgumentTypeSetTypeFloatArgumentType();
                    floatType.setSizeInBits(floatSizeInBits);
                    var floatEncodingType = factory.createFloatDataEncodingType();
                    floatEncodingType.setSizeInBits(floatSizeInBits);
                    floatEncodingType.setEncoding("IEEE754_1985");
                    floatType.setFloatDataEncoding(floatEncodingType);
                    floatType.setUnitSet(unitSet);

                    // Check if a minimum or maximum value is specified
                    if ( (minimum != null && minimum != "") || (maximum != null && maximum != ""))
                    {
                        var validRange = factory.createArgumentTypeSetTypeFloatArgumentTypeValidRangeSet();
                        var range = factory.createFloatRangeType();

                        // Check if a minimum value is specified
                        if (minimum != null && minimum != "")
                        {
                            // Set the minimum value
                            range.setMinExclusive(Double.valueOf(minimum));
                        }

                        // Check if a maximum value is specified
                        if (maximum != null && maximum != "")
                        {
                            // Set the maximum value
                            range.setMaxExclusive(parseFloat(maximum));
                        }

                        validRange.getValidRange().add(range);
                        floatType.setValidRangeSet(validRange);
                    }

                    commandDescription = floatType;
                    break;

                case BasePrimitiveDataType.STRING:
                    // Create a string command argument and set its attributes
                    var stringType = factory.createStringDataType();
                    var stringEncodingType = factory.createStringDataEncodingType();

                    // Set the string's size in bits based on the number of characters in the string
                    // with each character occupying a single byte
                    var intValType = factory.createIntegerValueType();
                    intValType.setFixedValue( (stringSize * 8).toString());
                    var stringSizeInBits = factory.createStringDataEncodingTypeSizeInBits();
                    stringSizeInBits.setFixed(intValType);
                    stringEncodingType.setSizeInBits(stringSizeInBits);
                    stringEncodingType.setEncoding("UTF-8");

                    stringType.setStringDataEncoding(stringEncodingType);
                    stringType.setUnitSet(unitSet);
                    commandDescription = stringType;
                    break;
            }
        }

        // Set the command name and argument name attributes
        commandDescription.setName(argumentName + TYPE + uniqueID);

        // Check is a description exists
        if (description != null && description != "")
        {
            // Set the command description attribute
            commandDescription.setLongDescription(description);
        }
    }

    return commandDescription;
}

/***************************************************************************************************
 * Add a container reference(s) for the telemetry or command parameter or parameter array to the
 * specified entry list
 * 
 * @param factory
 *            object factory reference
 * 
 * @param entryList
 *            reference to the telemetry or command entry list into which to place the parameter or
 *            parameter array container reference(s)
 * 
 * @param parameterName
 *            parameter name
 * 
 * @param dataType
 *            data type
 * 
 * @param arraySize
 *            parameter array size; null or blank if the parameter isn't an array
 **************************************************************************************************/
function addContainerReference(factory, parameterName, dataType, arraySize, entryList)
{
    var containerRefEntry;
    var containerRefElem;

    // Check if the parameter is an array definition or member
    if (arraySize != null && arraySize != "")
    {
        // Get the array of array dimensions and create storage for the current indices
        var totalDims = ccdd.getArrayIndexFromSize(arraySize);
        var currentIndices = new Array(totalDims.length);

        do
        {
            var subIndex;

            // Step through each index in the lowest level dimension
            for (currentIndices[0] = 0; currentIndices[0] < totalDims[totalDims.length - 1]; currentIndices[0]++)
            {
                // Get the name of the array structure table
                var arrayTablePath = dataType + "_" + parameterName;

                // Step through the remaining dimensions
                for (subIndex = currentIndices.length - 1; subIndex >= 0; subIndex--)
                {
                    // Append the current array index reference(s)
                    arrayTablePath += "_" + currentIndices[subIndex].toString();
                }

                // Store the structure reference in the list. The sequence container reference
                // components must be in the order specified by ArrayContainerReference, separated
                // by '/'s
                containerRefEntry = factory.createContainerRefEntryType();
                containerRefEntry.setContainerRef(arrayTablePath + "/" + ccdd.xmlCleanSystemPath(parameterName + ccdd.formatArrayIndex(currentIndices)) + "/" + arraySize);

                // Check if this is a telemetry list
                if (entryList instanceof EntryListType)
                {
                    // Store the container reference into the specified telemetry entry list
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefEntry);
                }
                // Check if this is a command list container
                else if (entryList instanceof CommandContainerEntryListType)
                {
                    // Store the container reference into the specified command entry list
                    containerRefElem = factory.createCommandContainerEntryListTypeContainerRefEntry(containerRefEntry);
                    entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefElem);
                }
            }

            // Go to the next higher level dimension (if any)
            for (subIndex = currentIndices.length - 2; subIndex >= 0; subIndex--)
            {
                // Increment the index
                currentIndices[subIndex]++;

                // Check if the maximum index of this dimension is reached
                if (currentIndices[subIndex] == totalDims[subIndex])
                {
                    // Check if this isn't the highest (last) dimension
                    if (subIndex != 0)
                    {
                        // Reset the index for this dimension
                        currentIndices[subIndex] = 0;
                    }
                    // This is the highest dimension
                    else
                    {
                        // All array members have been covered; stop searching, leaving the the
                        // highest dimension set to its maximum index value
                        break;
                    }
                }
                // The maximum index for this dimension hasn't been reached
                else
                {
                    // Exit the loop so that this array member can be processed
                    break;
                }
            }

        }while (currentIndices[0] < totalDims[0]);
        // Check if the highest dimension hasn't reached its maximum value. The loop continues until
        // a container reference for every array member is added to the entry list
    }
    // Not an array parameter
    else
    {
        // Create a container reference to the child command
        containerRefEntry = factory.createContainerRefEntryType();
        containerRefEntry.setContainerRef(dataType + "_" + parameterName + "/" + parameterName);

        // Check if this is a telemetry list
        if (entryList instanceof EntryListType)
        {
            // Store the container reference into the specified telemetry entry list
            entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefEntry);
        }
        // Check if this is a command list container
        else if (entryList instanceof CommandContainerEntryListType)
        {
            // Store the container reference into the specified command entry list
            containerRefElem = factory.createCommandContainerEntryListTypeContainerRefEntry(containerRefEntry);
            entryList.getParameterRefEntryOrParameterSegmentRefEntryOrContainerRefEntry().add(containerRefElem);
        }
    }
}

/***************************************************************************************************
 * Convert the primitive data type into the base equivalent
 * 
 * @param dataType
 *            data type
 * 
 * @return Base primitive data type corresponding to the specified primitive data type; null if no
 *         match
 **************************************************************************************************/
function getBaseDataType(dataType)
{
    var basePrimitiveDataType = null;

    // Check if the type is an integer (signed or unsigned)
    if (ccdd.isDataTypeInteger(dataType))
    {
        basePrimitiveDataType = BasePrimitiveDataType.INTEGER;
    }
    // Check if the type is a floating point (float or double)
    else if (ccdd.isDataTypeFloat(dataType))
    {
        basePrimitiveDataType = BasePrimitiveDataType.FLOAT;
    }
    // Check if the type is a string (character or string)
    else if (ccdd.isDataTypeCharacter(dataType))
    {
        basePrimitiveDataType = BasePrimitiveDataType.STRING;
    }

    return basePrimitiveDataType;
}

/***************************************************************************************************
 * Build a unit set from the supplied units string
 * 
 * @param factory
 *            object factory reference
 * 
 * @param units
 *            parameter or command argument units; null to not specify
 * 
 * @return Unit set for the supplied units string; an empty unit set if no units are supplied
 **************************************************************************************************/
function createUnitSet(factory, units)
{
    var unitSet = factory.createBaseDataTypeUnitSet();

    // Check if units are provided
    if (units != null && units != "")
    {
        // Set the parameter units
        var unit = factory.createUnitType();
        unit.setContent(units);
        unitSet.getUnit().add(unit);
    }

    return unitSet;
}

/***************************************************************************************************
 * Build an enumeration list from the supplied enumeration string
 * 
 * @param factory
 *            object factory reference
 * 
 * @param spaceSystem
 *            space system reference
 * 
 * @param enumeration
 *            enumeration in the format <enum value><enum value separator><enum label>[<enum
 *            value separator>...][<enum pair separator>...]
 * 
 * @return Enumeration list for the supplied enumeration string
 **************************************************************************************************/
function createEnumerationList(factory, spaceSystem, enumeration)
{
    var enumList = factory.createEnumeratedDataTypeEnumerationList();

    var enumDefn = ccdd.parseEnumerationParameters(enumeration);

    // Step through each enumeration definition
    for (var index = 0; index < enumDefn.length; index++)
    {
        // Create a new enumeration value type and add the enumerated name and value to the
        // enumeration list
        var valueEnum = factory.createValueEnumerationType();
        valueEnum.setLabel(enumDefn[index][1].trim());
        valueEnum.setValue(BigInteger.valueOf(parseInt(enumDefn[index][0].trim())));
        enumList.getEnumeration().add(valueEnum);
    }

    return enumList;
}
