/**
 * CFS Command and Data Dictionary field handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.GROUP_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.PROJECT_DATA_FIELD_IDENT;
import static CCDD.CcddConstants.TYPE_DATA_FIELD_IDENT;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.InputType;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;

/**************************************************************************************************
 * CFS Command and Data Dictionary field handler class
 *************************************************************************************************/
public class CcddFieldHandler
{
    // Class references
    private final CcddDbTableCommandHandler dbTable;
    private final CcddInputTypeHandler inputTypeHandler;

    // List of field information
    private List<FieldInformation> fieldInformation;

    /**********************************************************************************************
     * Field handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *********************************************************************************************/
    CcddFieldHandler(CcddMain ccddMain)
    {
        // Get references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        inputTypeHandler = ccddMain.getInputTypeHandler();

        // Create storage for the field information
        fieldInformation = new ArrayList<FieldInformation>();

        // Use the field definitions to create the data field information
        buildFieldInformation(ccddMain.getMainFrame());
    }

    /**********************************************************************************************
     * Get the data field information
     *
     * @return data field information
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformation()
    {
        return fieldInformation;
    }

    /**********************************************************************************************
     * Create a copy of the data field information
     *
     * @return Copy of the data field information
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformationCopy()
    {
        return getFieldInformationCopy(fieldInformation);
    }

    /**********************************************************************************************
     * Static method to create a copy of the supplied data field information
     *
     * @param fieldInfo
     *            list of field information to copy
     *
     * @return Copy of the supplied data field information
     *********************************************************************************************/
    protected static List<FieldInformation> getFieldInformationCopy(List<FieldInformation> fieldInfo)
    {
        List<FieldInformation> fldInfo = new ArrayList<FieldInformation>();

        // Check if any fields exist
        if (fieldInfo != null)
        {
            // Step through each field
            for (FieldInformation info : fieldInfo)
            {
                // Add the field to the copy (note that the input field reference isn't included in
                // the copy - the input field only is stored in the field handler and the input
                // field panel's field information)
                fldInfo.add(new FieldInformation(info.getOwnerName(),
                                                 info.getFieldName(),
                                                 info.getDescription(),
                                                 info.getInputType(),
                                                 info.getSize(),
                                                 info.isRequired(),
                                                 info.getApplicabilityType(),
                                                 info.getValue(),
                                                 info.isInherited(),
                                                 info.getInputFld(),
                                                 info.getID()));
            }
        }

        return fldInfo;
    }

    /**********************************************************************************************
     * Set the data field information
     *
     * @param fieldInfo
     *            data field information to copy and use in the field handler; null or an empty
     *            list to clear the field information
     *********************************************************************************************/
    protected void setFieldInformation(List<FieldInformation> fieldInfo)
    {
        // Check if fields are defined in the supplied field information
        if (fieldInfo != null)
        {
            // Set the data field information to a copy of that supplied
            fieldInformation = fieldInfo;
        }
        // No field information is supplied
        else
        {
            // Clear the field information
            fieldInformation.clear();
        }
    }

    /**********************************************************************************************
     * Build the data field information from the field definitions stored in the database
     *
     * @param parent
     *            GUI component over which to center any error dialogs
     *********************************************************************************************/
    protected void buildFieldInformation(Component parent)
    {
        // Use the field definitions to create the data field information
        setFieldInformationFromDefinitions(dbTable.retrieveInformationTable(InternalTable.FIELDS,
                                                                            false,
                                                                            parent));
    }

    /**********************************************************************************************
     * Set the data field information built from the supplied field definitions
     *
     * @param fieldDefinitions
     *            list of data field definitions
     *********************************************************************************************/
    protected void setFieldInformationFromDefinitions(List<String[]> fieldDefinitions)
    {
        // Clear the fields from the list. Note that this eliminates the input fields (text and
        // check box) that are stored in the field information; these must be rebuilt (if needed)
        // after calling this method
        fieldInformation.clear();

        // Check if the field definitions exist
        if (fieldDefinitions != null)
        {
            // Get the list containing the field information based on the supplied field
            // definitions
            fieldInformation = getFieldInformationFromDefinitions(fieldDefinitions);
        }
    }

    /**********************************************************************************************
     * Get the data field information built from the supplied field definitions
     *
     * @param fieldDefinitions
     *            list of data field definitions
     *
     * @return List containing the field information based on the supplied field definitions; an
     *         empty list if the field definitions list is null
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformationFromDefinitions(List<String[]> fieldDefinitions)
    {
        List<FieldInformation> fieldInfo = new ArrayList<FieldInformation>();

        // Check if the field definitions exist
        if (fieldDefinitions != null)
        {
            // Step through each field definition
            for (String[] fieldDefn : fieldDefinitions)
            {
                // Get the input type from its name
                InputType inputType = inputTypeHandler.getInputTypeByName(fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].toString());

                // Get the applicability type from its name. The all tables applicability type is
                // the default if the applicability type name is invalid
                ApplicabilityType applicability = ApplicabilityType.ALL;
                String applicabilityName = fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].toString();

                // Step through each field applicability type
                for (ApplicabilityType type : ApplicabilityType.values())
                {
                    // Check if the type matches this field's applicability type
                    if (applicabilityName.equals(type.getApplicabilityName()))
                    {
                        // Store the field applicability type and stop searching
                        applicability = type;
                        break;
                    }
                }

                // Add the field information
                fieldInfo.add(new FieldInformation(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()].toString(),
                                                   fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].toString(),
                                                   fieldDefn[FieldsColumn.FIELD_DESC.ordinal()].toString(),
                                                   inputType,
                                                   Integer.valueOf(fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()].toString()),
                                                   Boolean.valueOf(fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].toString()),
                                                   applicability,
                                                   fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()].toString(),
                                                   Boolean.valueOf(fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()].toString()),
                                                   null,
                                                   -1));
            }
        }

        return fieldInfo;
    }

    /**********************************************************************************************
     * Update the input type for each field definition following a change to the input type
     * definitions
     *
     * @param inputTypeNames
     *            list of the input type names, before and after the changes; null if none of the
     *            input type names changed
     *
     * @param fieldInfo
     *            reference to the field information list to update
     *********************************************************************************************/
    protected void updateFieldInputTypes(List<String[]> inputTypeNames,
                                         List<FieldInformation> fieldInfo)
    {
        // Step through each field definition
        for (FieldInformation fldInfo : fieldInfo)
        {
            // Get the field's input type name before the change
            String inputTypeName = fldInfo.getInputType().getInputName();

            // Check if a list of input type names is provided. If not, assume the names are
            // unchanged
            if (inputTypeNames != null)
            {
                // Step through each input type that changed
                for (String[] oldAndNewName : inputTypeNames)
                {
                    // Check if the input type name changed
                    if (oldAndNewName[0].equals(inputTypeName))
                    {
                        // Set the field's input type name to the (possibly) new input type name
                        // and stop searching
                        inputTypeName = oldAndNewName[1];
                        break;
                    }
                }
            }

            // Set the field's input type based on the input type name
            fldInfo.setInputType(inputTypeHandler.getInputTypeByName(inputTypeName));

            // Check if the field value doesn't conform to the input type match regular expression
            if (!fldInfo.getValue().matches(fldInfo.getInputType().getInputMatch()))
            {
                // Set the field value to a blank
                fldInfo.setValue("");
            }
        }
    }

    /**********************************************************************************************
     * Get the data field information for a specified owner and field from the project's field
     * information list
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param fieldName
     *            name of the field for which to get the field information (case insensitive)
     *
     * @return Reference to the data field information for the specified field; null if the field
     *         doesn't exist
     *********************************************************************************************/
    protected FieldInformation getFieldInformationByName(String ownerName, String fieldName)
    {
        return getFieldInformationByName(fieldInformation, ownerName, fieldName);
    }

    /**********************************************************************************************
     * Get the data field information for a specified owner and field from the supplied field
     * information list
     *
     * @param fieldInformationList
     *            list of data field information to search
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param fieldName
     *            name of the field for which to get the field information (case insensitive)
     *
     * @return Reference to the data field information for the specified field; null if the field
     *         doesn't exist
     *********************************************************************************************/
    protected static FieldInformation getFieldInformationByName(List<FieldInformation> fieldInformationList,
                                                                String ownerName,
                                                                String fieldName)
    {
        FieldInformation fieldInfo = null;

        // Step through each field
        for (FieldInformation info : fieldInformationList)
        {
            // Check if the owner and field names match the ones supplied (case insensitive)
            if (info.getOwnerName().equalsIgnoreCase(ownerName)
                && info.getFieldName().equalsIgnoreCase(fieldName))
            {
                // Store the field information reference and stop searching
                fieldInfo = info;
                break;
            }
        }

        return fieldInfo;
    }

    /**********************************************************************************************
     * Get the data field information for a specified owner and input type. The first field
     * matching the input type is returned
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param inputType
     *            input type of the field for which to get the field information (InputType)
     *
     * @return Reference to the data field information for the first field that matches the owner
     *         and input type; null if the no match is found
     *********************************************************************************************/
    protected FieldInformation getFieldInformationByInputType(String ownerName,
                                                              InputType inputType)
    {
        FieldInformation fieldInfo = null;

        // Step through each field
        for (FieldInformation info : fieldInformation)
        {
            // Check if the owner and field types match the ones supplied (case insensitive)
            if (info.getOwnerName().equalsIgnoreCase(ownerName)
                && info.getInputType().equals(inputType))
            {
                // Store the field information reference and stop searching
                fieldInfo = info;
                break;
            }
        }

        return fieldInfo;
    }

    /**********************************************************************************************
     * Get the list of field information for the specified owner
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @return List of field information for the specified owner; an empty list if the owner has no
     *         fields or the owner name is invalid
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformationByOwner(String ownerName)
    {
        List<FieldInformation> ownerFieldInfo = new ArrayList<FieldInformation>();

        // Check if the owner name is provided
        if (ownerName != null)
        {
            // Step through each data field
            for (FieldInformation fieldInfo : fieldInformation)
            {
                // Check if the owner names match
                if (fieldInfo.getOwnerName().equals(ownerName))
                {
                    // Add the field to the list belonging to the specified owner
                    ownerFieldInfo.add(fieldInfo);
                }
            }
        }

        return ownerFieldInfo;
    }

    /**********************************************************************************************
     * Get a copy of the list of field information for the specified owner
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @return Copy of the list of field information for the specified owner; an empty list if the
     *         owner has no fields or the owner name is invalid
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformationByOwnerCopy(String ownerName)
    {
        return getFieldInformationCopy(getFieldInformationByOwner(ownerName));
    }

    /**********************************************************************************************
     * Replace the specified owner's current data fields with those in the supplied list
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param newOwnerFldInfo
     *            list of field information for the specified owner; an empty list if the owner has
     *            no fields
     *********************************************************************************************/
    protected void replaceFieldInformationByOwner(String ownerName,
                                                  List<FieldInformation> newOwnerFldInfo)
    {
        // Get the list of the owner's current fields
        List<FieldInformation> oldOwnerFldInfo = getFieldInformationByOwner(ownerName);

        // Remove the owner's current fields
        fieldInformation.removeAll(oldOwnerFldInfo);

        // Add the owner's new fields
        fieldInformation.addAll(getFieldInformationCopy(newOwnerFldInfo));
    }

    /**********************************************************************************************
     * Assign unique ID values to the supplied fields
     *
     * @param fieldInfo
     *            list of field information
     *********************************************************************************************/
    protected static void assignFieldIDs(List<FieldInformation> fieldInfo)
    {
        int id = 0;

        // Step through each field's information
        for (FieldInformation info : fieldInfo)
        {
            // Assign the field ID and increment the ID value
            info.setID(id);
            id++;
        }
    }

    /**********************************************************************************************
     * Determine if a field is applicable to the specified owner. A field is always applicable if
     * the specified applicability is for all tables, or if the owner is a table type or group. If
     * the owner is a root table then 'child only' fields are inapplicable. If the table doesn't
     * meet any of the previous criteria then the table is a child table or the prototype for a
     * child table, so 'root only' fields are inapplicable
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @param applicability
     *            one of the ApplicabilityType names; a blank is treated the same as being
     *            applicable for all tables
     *
     * @param isRootStruct
     *            true if the owner is a root structure table; null to obtain the root structure
     *            status from the list maintained in the database table handler
     *
     * @return true if the field is applicable to the owner
     *********************************************************************************************/
    protected boolean isFieldApplicable(String ownerName,
                                        String applicability,
                                        Boolean isRootStruct)
    {
        // Set the flag to indicate if the owner is a table type, group, or project
        boolean isTypeGroupProject = ownerName.startsWith(TYPE_DATA_FIELD_IDENT)
                                     || ownerName.startsWith(GROUP_DATA_FIELD_IDENT)
                                     || ownerName.startsWith(PROJECT_DATA_FIELD_IDENT);

        // Check if the owner is a table type, group, project, or child structure (the owner name
        // includes a data type & variable name)
        if (isTypeGroupProject || ownerName.contains("."))
        {
            // Set the flag to indicate the owner isn't a root structure
            isRootStruct = false;
        }
        // The owner is a prototype or root table. Check if the root structure status is unknown
        else if (isRootStruct == null)
        {
            // Set the flag that indicates if the owner is a root structure
            isRootStruct = dbTable.isRootStructure(ownerName);
        }

        return isTypeGroupProject
               || applicability.isEmpty()
               || applicability.equals(ApplicabilityType.ALL.getApplicabilityName())
               || (isRootStruct
                   && applicability.equals(ApplicabilityType.ROOT_ONLY.getApplicabilityName()))
               || (!isRootStruct
                   && applicability.equals(ApplicabilityType.CHILD_ONLY.getApplicabilityName()));
    }

    /**********************************************************************************************
     * Determine if the number of data fields, field attributes, or field contents differ between
     * two sets of data field information
     *
     * @param compFieldInfoA
     *            reference to the the first data field information with which to compare the
     *            second data field information
     *
     * @param compFieldInfoB
     *            reference to the second data field information with which to compare the first
     *            data field information
     *
     * @param isIgnoreOwnerName
     *            true if the owner name is ignored. This is the case if called by the data field
     *            or table type editors
     *
     * @return Data field definitions array
     *********************************************************************************************/
    protected boolean isFieldChanged(List<FieldInformation> compFieldInfoA,
                                     List<FieldInformation> compFieldInfoB,
                                     boolean isIgnoreOwnerName)
    {
        // Set the change flag if the number of fields in the two field handlers differ
        boolean isFieldChanged = compFieldInfoA.size() != compFieldInfoB.size();

        // Check if the number of fields is the same
        if (!isFieldChanged)
        {
            // Step through each field
            for (int index = 0; index < compFieldInfoA.size(); index++)
            {
                // Check if the field information differs
                if ((!isIgnoreOwnerName && !compFieldInfoA.get(index).getOwnerName().equals(compFieldInfoB.get(index).getOwnerName()))
                    || !compFieldInfoA.get(index).getFieldName().equals(compFieldInfoB.get(index).getFieldName())
                    || !compFieldInfoA.get(index).getDescription().equals(compFieldInfoB.get(index).getDescription())
                    || inputTypeHandler.isInputTypeChanged(compFieldInfoA.get(index).getInputType(),
                                                           compFieldInfoB.get(index).getInputType())
                    || compFieldInfoA.get(index).getSize() != compFieldInfoB.get(index).getSize()
                    || !compFieldInfoA.get(index).getValue().equals(compFieldInfoB.get(index).getValue())
                    || compFieldInfoA.get(index).isRequired() != compFieldInfoB.get(index).isRequired()
                    || compFieldInfoA.get(index).getApplicabilityType() != compFieldInfoB.get(index).getApplicabilityType())
                {
                    // Set the flag indicating a field is changed and stop searching
                    isFieldChanged = true;
                    break;
                }
            }
        }

        return isFieldChanged;
    }

    /**********************************************************************************************
     * Rebuild the data field definitions for the specified owner from the supplied data field
     * editor data. The owner's existing fields (if any) are removed, then the supplied definitions
     * are used to create the owner's new fields (if any)
     *
     * @param fieldData
     *            array of data field editor data
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *
     * @return List of data field information created from the supplied field definitions
     *********************************************************************************************/
    protected List<FieldInformation> getFieldInformationFromData(Object[][] fieldData,
                                                                 String ownerName)
    {
        List<FieldInformation> fieldInfo = new ArrayList<FieldInformation>();

        // Check if any data fields are defined
        if (fieldData.length != 0)
        {
            // Step through each row in the editor data array
            for (Object[] data : fieldData)
            {
                // Add the field information for this data field to the list
                fieldInfo.add(new FieldInformation(ownerName,
                                                   data[FieldEditorColumnInfo.NAME.ordinal()].toString(),
                                                   data[FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString(),
                                                   inputTypeHandler.getInputTypeByName(data[FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString()),
                                                   Integer.valueOf(data[FieldEditorColumnInfo.CHAR_SIZE.ordinal()].toString()),
                                                   Boolean.valueOf(data[FieldEditorColumnInfo.REQUIRED.ordinal()].toString()),
                                                   ApplicabilityType.getApplicabilityByName(data[FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString()),
                                                   data[FieldEditorColumnInfo.VALUE.ordinal()].toString(),
                                                   Boolean.valueOf(data[FieldEditorColumnInfo.INHERITED.ordinal()].toString()),
                                                   null,
                                                   -1));
            }
        }

        return fieldInfo;
    }

    /**********************************************************************************************
     * Get the array of data field definitions from the supplied field information list for use in
     * the data field editor
     *
     * @param fieldInfo
     *            list of field information to convert
     *
     * @return Object array containing the data field definitions for the specified owner used by
     *         the data field editor
     *********************************************************************************************/
    protected static Object[][] getFieldEditorDefinition(List<FieldInformation> fieldInfo)
    {
        List<Object[]> definitions = new ArrayList<Object[]>();

        // Step through each of the owner's fields
        for (FieldInformation fldInfo : fieldInfo)
        {
            // Create storage for a single field definition
            Object[] row = new Object[FieldEditorColumnInfo.values().length];

            // Store the field definition in the proper order
            row[FieldEditorColumnInfo.NAME.ordinal()] = fldInfo.getFieldName();
            row[FieldEditorColumnInfo.DESCRIPTION.ordinal()] = fldInfo.getDescription();
            row[FieldEditorColumnInfo.INPUT_TYPE.ordinal()] = fldInfo.getInputType().getInputName();
            row[FieldEditorColumnInfo.CHAR_SIZE.ordinal()] = fldInfo.getSize();
            row[FieldEditorColumnInfo.REQUIRED.ordinal()] = fldInfo.isRequired();
            row[FieldEditorColumnInfo.APPLICABILITY.ordinal()] = fldInfo.getApplicabilityType().getApplicabilityName();
            row[FieldEditorColumnInfo.VALUE.ordinal()] = fldInfo.getValue();
            row[FieldEditorColumnInfo.INHERITED.ordinal()] = fldInfo.isInherited();
            row[FieldEditorColumnInfo.ID.ordinal()] = fldInfo.getID();

            // Add the field definition to the list
            definitions.add(row);
        }

        return definitions.toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Get the list of all data field definitions from the field information
     *
     * @return String list containing all of the data field definitions
     *********************************************************************************************/
    protected List<String[]> getFieldDefnsFromInfo()
    {
        // Create storage for the field definitions
        List<String[]> definitions = new ArrayList<String[]>();

        // Step through each field's information
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Add the field definition to the list
            definitions.add(getFieldDefinitionArray(fieldInfo.getOwnerName(),
                                                    fieldInfo.getFieldName(),
                                                    fieldInfo.getDescription(),
                                                    fieldInfo.getInputType(),
                                                    fieldInfo.getSize(),
                                                    fieldInfo.isRequired(),
                                                    fieldInfo.getApplicabilityType(),
                                                    fieldInfo.getValue(),
                                                    fieldInfo.isInherited()));
        }

        return definitions;
    }

    /**********************************************************************************************
     * Create a field definition array from the supplied inputs
     *
     * @param ownerName
     *            name of the table/table type/group to which the field is a member
     *
     * @param fieldName
     *            name of the new field
     *
     * @param description
     *            field description
     *
     * @param inputType
     *            input type (InputType)
     *
     * @param size
     *            field display size in characters
     *
     * @param isRequired
     *            true if a value if required in this field
     *
     * @param applicability
     *            ApplicabilityType.ALL to indicate all tables, ApplicabilityType.ROOTS_ONLY for
     *            root tables only, or ApplicabilityType.CHILD_ONLY for child tables only
     *
     * @param value
     *            data field value
     *
     * @param isInherited
     *            true if the field is inherited from a table type definition
     *
     * @return Field definition array created from the supplied inputs
     *********************************************************************************************/
    protected static String[] getFieldDefinitionArray(String ownerName,
                                                      String fieldName,
                                                      String description,
                                                      InputType inputType,
                                                      int size,
                                                      boolean isRequired,
                                                      ApplicabilityType applicability,
                                                      String value,
                                                      boolean isInherited)
    {
        String[] fieldDefn = new String[FieldsColumn.values().length];

        // Store the field definition in the proper order
        fieldDefn[FieldsColumn.OWNER_NAME.ordinal()] = ownerName;
        fieldDefn[FieldsColumn.FIELD_NAME.ordinal()] = fieldName;
        fieldDefn[FieldsColumn.FIELD_DESC.ordinal()] = description;
        fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = inputType.getInputName();
        fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()] = String.valueOf(size);
        fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()] = String.valueOf(isRequired);
        fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = applicability.getApplicabilityName();
        fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()] = value;
        fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = String.valueOf(isInherited);

        return fieldDefn;
    }

    /**********************************************************************************************
     * Update an existing data field's information
     *
     * @param updateInfo
     *            updated field information used to replace the existing field information
     *
     * @return true if the a matching owner and field exists for the provided field information
     *         update
     *********************************************************************************************/
    protected boolean updateField(FieldInformation updateInfo)
    {
        boolean isUpdate = false;

        // Get the reference to the field information for the specified owner/field combination
        FieldInformation fieldInfo = getFieldInformationByName(updateInfo.getOwnerName(),
                                                               updateInfo.getFieldName());

        // Check if the owner/field combination exists and if the field differs from the updated
        // one
        if (fieldInfo != null
            && (!fieldInfo.getDescription().equals(updateInfo.getDescription())
                || !fieldInfo.getInputType().equals(updateInfo.getInputType())
                || fieldInfo.getSize() != updateInfo.getSize()
                || fieldInfo.isRequired() != updateInfo.isRequired()
                || !fieldInfo.getValue().equals(updateInfo.getValue())))
        {
            // Get the position of the field within the list
            int index = fieldInformation.indexOf(fieldInfo);

            // Remove the existing field from the list
            fieldInformation.remove(fieldInfo);

            // Add the updated field information to the list at the same position as the old field
            fieldInformation.add(index, updateInfo);

            // Set the flag to indicate a match exists
            isUpdate = true;
        }

        return isUpdate;
    }

    /**********************************************************************************************
     * Check the supplied field information list for the specified inheritable (table type) data
     * field belonging to the specified table. If the table does not have the field then add it;
     * otherwise update the table's field to ensure it matches the one inherited from its table
     * type (and that no duplicate field name arises from an existing table field of the same name
     * but different input type)
     *
     * @param fieldInformationList
     *            list of data field information to search
     *
     * @param tablePath
     *            name of the table (including the path if this table represents a structure) for
     *            the table owning the field
     *
     * @param typeFldInfo
     *            reference to the table type data field
     *********************************************************************************************/
    protected void addUpdateInheritedField(List<FieldInformation> fieldInformationList,
                                           String tablePath,
                                           FieldInformation typeFldInfo)
    {
        // Get the table's field of the same name as the table type's field
        FieldInformation tableFldInfo = getFieldInformationByName(fieldInformationList,
                                                                  tablePath,
                                                                  typeFldInfo.getFieldName());

        // Check if the table doesn't have the inheritable field
        if (tableFldInfo == null)
        {
            // Check if the table isn't a child structure (all fields are stored for prototypes,
            // even if not displayed) or the field is applicable to this child table
            if (!tablePath.contains(".")
                || isFieldApplicable(tablePath,
                                     typeFldInfo.getApplicabilityType().getApplicabilityName(),
                                     null))
            {
                // Add the data field to the table
                fieldInformationList.add(new FieldInformation(tablePath,
                                                              typeFldInfo.getFieldName(),
                                                              typeFldInfo.getDescription(),
                                                              typeFldInfo.getInputType(),
                                                              typeFldInfo.getSize(),
                                                              typeFldInfo.isRequired(),
                                                              typeFldInfo.getApplicabilityType(),
                                                              typeFldInfo.getValue(),
                                                              true,
                                                              null,
                                                              -1));
            }
        }
        // The table has a field with the same name as the inheritable field
        else
        {
            // Check if the input types are the same (these are considered the same field)
            if (typeFldInfo.getInputType().equals(tableFldInfo.getInputType()))
            {
                // Update the description, size, required flag, and applicability type to match the
                // table type's field definition
                tableFldInfo.setDescription(typeFldInfo.getDescription());
                tableFldInfo.setSize(typeFldInfo.getSize());
                tableFldInfo.setRequired(typeFldInfo.isRequired());
                tableFldInfo.setApplicabilityType(typeFldInfo.getApplicabilityType());
                tableFldInfo.setInherited(true);
            }
            // The input types differ (these are considered different fields)
            else
            {
                // Check if the table already has a field by this name and, if so, alter the
                // existing field's name in order to prevent a duplicate
                alterFieldName(fieldInformationList, tablePath, typeFldInfo.getFieldName());

                // Add the data field to the table
                fieldInformationList.add(new FieldInformation(tablePath,
                                                              typeFldInfo.getFieldName(),
                                                              typeFldInfo.getDescription(),
                                                              typeFldInfo.getInputType(),
                                                              typeFldInfo.getSize(),
                                                              typeFldInfo.isRequired(),
                                                              typeFldInfo.getApplicabilityType(),
                                                              typeFldInfo.getValue(),
                                                              true,
                                                              null,
                                                              -1));
            }
        }
    }

    /**********************************************************************************************
     * Alter an owner's existing field's name so that it doesn't match the supplied name in the
     * supplied field information list. This can be used when a table inherits a field from its
     * table type definition to prevent a duplicate field name
     *
     * @param fieldInformationList
     *            list of data field information to search
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name) from which to copy the
     *            data fields
     *
     * @param matchName
     *            field name to use when matching with an existing field for the specified owner
     *
     * @return The field name, updated to be unique by adding one or more trailing underscores
     *********************************************************************************************/
    protected static String alterFieldName(List<FieldInformation> fieldInformationList,
                                           String ownerName,
                                           String matchName)
    {
        // Get the reference to the field
        FieldInformation existingField = getFieldInformationByName(fieldInformationList,
                                                                   ownerName,
                                                                   matchName);

        // Check if the field exists
        if (existingField != null)
        {
            do
            {
                // Alter the field name
                matchName += "_";
            } while (getFieldInformationByName(fieldInformationList,
                                               ownerName,
                                               matchName) != null);
            // Continue to update the field's name until there's no match with one of the owner's
            // other fields

            // Replace the field's name with the altered one
            existingField.setFieldName(matchName);
        }

        return matchName;
    }

    /**********************************************************************************************
     * Check if a table already has a field with the specified default field name, but a different
     * input type
     *
     * @param tablesOfType
     *            list of tables of the table type containing the default field
     *
     * @param typeFieldName
     *            default data field name
     *
     * @param inputType
     *            input type name for the default field
     *
     * @return true if a table in the list has a data field with the same name but a different
     *         input type
     *********************************************************************************************/
    protected boolean checkForDuplicateField(List<String> tablesOfType,
                                             String typeFieldName,
                                             String inputType)
    {
        boolean isDuplicate = false;

        // Step through all tables of this type
        for (String tablePath : tablesOfType)
        {
            // Get the table field with the supplied name
            FieldInformation tableFld = getFieldInformationByName(tablePath, typeFieldName);

            // Check if the table has a field with this name but has a different input type
            if (tableFld != null && !tableFld.getInputType().getInputName().equals(inputType))
            {
                // Set the flag to indicate a duplicate field name exists and stop searching
                isDuplicate = true;
                break;
            }
        }

        return isDuplicate;
    }

    /**********************************************************************************************
     * Copy the data fields belonging to the specified owner to another table, group, etc.
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name) from which to copy the
     *            data fields
     *
     * @param newName
     *            name of the table, group, etc. to which to copy the data fields
     *********************************************************************************************/
    protected void copyFields(String ownerName, String newName)
    {
        // Get a copy of the owner's data field information
        List<FieldInformation> fieldInfo = getFieldInformationByOwnerCopy(ownerName);

        // Step through each of the copied fields
        for (FieldInformation fldInfo : fieldInfo)
        {
            // Set the copy's owner to the new owner name
            fldInfo.setOwnerName(newName);
        }

        // Add the copied fields to the list of all owner's field information
        fieldInformation.addAll(fieldInfo);
    }

    /**********************************************************************************************
     * Count the number of the specified field type that exists in the field information
     *
     * @param fieldOwner
     *            field owner name
     *
     * @param fieldInputType
     *            field input type (InputType)
     *
     * @return The number of the specified field type that exists in the field information
     *********************************************************************************************/
    protected int getFieldTypeCount(String fieldOwner, InputType fieldInputType)
    {
        int count = 0;

        // Step through each field definition
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Check if the field type matches the specified owner and input type
            if (fieldInfo.getOwnerName().equals(fieldOwner)
                && fieldInfo.getInputType().equals(fieldInputType))
            {
                // Increment the type counter
                count++;
            }
        }

        return count;
    }

    /**********************************************************************************************
     * Get the value of the data field with the specified input type for the specified field owner
     *
     * @param fieldOwner
     *            field owner name
     *
     * @param inputType
     *            input type for which to search (InputType)
     *
     * @return Value of the data field with the specified input type for the specified field owner;
     *         null if the owner doesn't have a data field of that type
     *********************************************************************************************/
    protected String getFieldValue(String fieldOwner, InputType inputType)
    {
        String fieldValue = null;

        // Get a reference to the first field of the specified type
        FieldInformation fieldInfo = getFieldInformationByInputType(fieldOwner, inputType);

        // Check if a non-empty field of the specified type exists
        if (fieldInfo != null && !fieldInfo.getValue().isEmpty())
        {
            // Store the field value
            fieldValue = fieldInfo.getValue();
        }

        return fieldValue;
    }

    /**********************************************************************************************
     * Get the value of the data field with the specified default input type for the specified
     * field owner
     *
     * @param fieldOwner
     *            field owner name
     *
     * @param inputType
     *            default input type for which to search (DefaultInputType)
     *
     * @return Value of the data field with the specified default input type for the specified
     *         field owner; null if the owner doesn't have a data field of that type
     *********************************************************************************************/
    protected String getFieldValue(String fieldOwner, DefaultInputType inputType)
    {
        return getFieldValue(fieldOwner, inputTypeHandler.getInputTypeByDefaultType(inputType));
    }

    /**********************************************************************************************
     * Get the project indicator that identifies project data fields in the fields table
     *
     * @return Project indicator
     *********************************************************************************************/
    protected static String getFieldProjectName()
    {
        return PROJECT_DATA_FIELD_IDENT;
    }

    /**********************************************************************************************
     * Prepend the table type indicator to the table type name for use in identifying default data
     * fields in the fields table
     *
     * @param tableType
     *            table type name
     *
     * @return Table type name with the table type indicator prepended
     *********************************************************************************************/
    protected static String getFieldTypeName(String tableType)
    {
        return TYPE_DATA_FIELD_IDENT + tableType;
    }

    /**********************************************************************************************
     * Prepend the group indicator to the group name for use in identifying group data fields in
     * the fields table
     *
     * @param groupName
     *            group name
     *
     * @return Group name with the group indicator prepended
     *********************************************************************************************/
    protected static String getFieldGroupName(String groupName)
    {
        return GROUP_DATA_FIELD_IDENT + groupName;
    }

    /**********************************************************************************************
     * Check if a data field is owner by a table
     *
     * @param ownerName
     *            data field owner name
     *
     * @return true if the data field owner is a table
     *********************************************************************************************/
    protected static boolean isTableField(String ownerName)
    {
        return !isProjectField(ownerName)
               && !isTableTypeField(ownerName)
               && !isGroupField(ownerName);
    }

    /**********************************************************************************************
     * Check if a data field is owner by the project
     *
     * @param ownerName
     *            data field owner name
     *
     * @return true if the data field owner is the project
     *********************************************************************************************/
    protected static boolean isProjectField(String ownerName)
    {
        return ownerName.startsWith(PROJECT_DATA_FIELD_IDENT);
    }

    /**********************************************************************************************
     * Check if a data field is owner by a table type
     *
     * @param ownerName
     *            data field owner name
     *
     * @return true if the data field owner is a table type
     *********************************************************************************************/
    protected static boolean isTableTypeField(String ownerName)
    {
        return ownerName.startsWith(TYPE_DATA_FIELD_IDENT);
    }

    /**********************************************************************************************
     * Check if a data field is owner by a group
     *
     * @param ownerName
     *            data field owner name
     *
     * @return true if the data field owner is a group
     *********************************************************************************************/
    protected static boolean isGroupField(String ownerName)
    {
        return ownerName.startsWith(GROUP_DATA_FIELD_IDENT);
    }
}
