/**
 * CFS Command & Data Dictionary field handler.
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
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.FieldEditorColumnInfo;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;
import CCDD.CcddConstants.InternalTable.FieldsColumn;

/**************************************************************************************************
 * CFS Command & Data Dictionary field handler class
 *************************************************************************************************/
public class CcddFieldHandler
{
    // Main class reference
    private final CcddMain ccddMain;

    // List of field definitions
    private List<String[]> fieldDefinitions;

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
        this.ccddMain = ccddMain;

        // Create storage for the field definitions and information
        fieldDefinitions = new ArrayList<String[]>();
        fieldInformation = new ArrayList<FieldInformation>();
    }

    /**********************************************************************************************
     * Field handler class constructor
     *
     * @param ccddMain
     *            main class reference
     *
     * @param fieldDefinitions
     *            field definitions
     *********************************************************************************************/
    CcddFieldHandler(CcddMain ccddMain, List<String[]> fieldDefinitions)
    {
        this(ccddMain);
        this.fieldDefinitions = fieldDefinitions;
    }

    /**********************************************************************************************
     * Field handler class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param ownerName
     *            name of the data field owner; null to build the information for all data fields
     *
     * @param parent
     *            GUI component calling this method
     *********************************************************************************************/
    CcddFieldHandler(CcddMain ccddMain, String ownerName, Component parent)
    {
        this(ccddMain);

        // Load the data field definitions from the database
        fieldDefinitions = ccddMain.getDbTableCommandHandler().retrieveInformationTable(InternalTable.FIELDS,
                                                                                        parent);

        // Use the field definitions to create the data field information
        buildFieldInformation(ownerName);
    }

    /**********************************************************************************************
     * Get the data field definitions
     *
     * @return data field definitions
     *********************************************************************************************/
    protected List<String[]> getFieldDefinitions()
    {
        return fieldDefinitions;
    }

    /**********************************************************************************************
     * Set the data field definitions
     *
     * @param fieldDefinitions
     *            field definitions
     *********************************************************************************************/
    protected void setFieldDefinitions(List<String[]> fieldDefinitions)
    {
        this.fieldDefinitions = fieldDefinitions;
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
                // Add the field to the copy
                fldInfo.add(new FieldInformation(info.getOwnerName(),
                                                 info.getFieldName(),
                                                 info.getDescription(),
                                                 info.getSize(),
                                                 info.getInputType(),
                                                 info.isRequired(),
                                                 info.getApplicabilityType(),
                                                 info.getValue(),
                                                 info.getInputFld()));
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
     * Get the data field information for a specified owner and field
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
        FieldInformation fieldInfo = null;

        // Step through each field
        for (FieldInformation info : fieldInformation)
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
     *            input type of the field for which to get the field information
     *
     * @return Reference to the data field information for the first field that matches the owner
     *         and input type; null if the no match is found
     *********************************************************************************************/
    protected FieldInformation getFieldInformationByInputType(String ownerName,
                                                              InputDataType inputType)
    {
        FieldInformation fieldInfo = null;

        // Step through each field
        for (FieldInformation info : fieldInformation)
        {
            // Check if the owner and field types match the ones supplied (case insensitive)
            if (info.getOwnerName().equalsIgnoreCase(ownerName)
                && info.getInputType() == inputType)
            {
                // Store the field information reference and stop searching
                fieldInfo = info;
                break;
            }
        }

        return fieldInfo;
    }

    /**********************************************************************************************
     * Build the data field information from the field definitions
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name); null to get all data
     *            fields
     *********************************************************************************************/
    protected void buildFieldInformation(String ownerName)
    {
        // Clear the fields from the list. Note that this eliminates the input fields (text and
        // check box) that are stored in the field information; these must be rebuilt (if needed)
        // after calling this method
        fieldInformation.clear();

        // Check if the field definitions exist
        if (fieldDefinitions != null)
        {
            // Step through each field definition
            for (String[] fieldDefn : fieldDefinitions.toArray(new String[0][0]))
            {
                // Check if no owner name is provided (get the fields for all tables and groups for
                // this case), or if the supplied owner name matches the field owner name
                if (ownerName == null
                    || ownerName.isEmpty()
                    || ownerName.equalsIgnoreCase(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()].toString()))
                {
                    // Store the field information
                    addField(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()].toString(),
                             fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].toString(),
                             fieldDefn[FieldsColumn.FIELD_DESC.ordinal()].toString(),
                             Integer.valueOf(fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()].toString()),
                             fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].toString(),
                             Boolean.valueOf(fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].toString()),
                             fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].toString(),
                             fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()].toString());
                }
            }
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
     *            true if the owner is a root structure table
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
            isRootStruct = ccddMain.getDbTableCommandHandler().getRootStructures().contains(ownerName);
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
    protected static boolean isFieldChanged(List<FieldInformation> compFieldInfoA,
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
                    || !compFieldInfoA.get(index).getInputType().equals(compFieldInfoB.get(index).getInputType())
                    || compFieldInfoA.get(index).getSize() != compFieldInfoB.get(index).getSize()
                    || !compFieldInfoA.get(index).getValue().equals(compFieldInfoB.get(index).getValue())
                    || compFieldInfoA.get(index).isRequired() != compFieldInfoB.get(index).isRequired())
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
     * Build the data field definitions from the supplied data field editor data
     *
     * @param fieldData
     *            array of data field editor data
     *
     * @param ownerName
     *            name of the data field owner (table name, including the path if this table
     *            references a structure, group name, or table type name)
     *********************************************************************************************/
    protected void buildFieldDefinitions(Object[][] fieldData, String ownerName)
    {
        fieldDefinitions.clear();

        // Check if any data fields are defined
        if (fieldData.length != 0)
        {
            // Step through each row in the editor data array
            for (Object[] data : fieldData)
            {
                // Create the field definition array with an extra column for the owner name
                String[] defn = new String[fieldData[0].length + 1];

                // Add the table name (with path, if applicable) to the field definition
                defn[FieldsColumn.OWNER_NAME.ordinal()] = ownerName;

                // Copy the editor data to the field definition
                defn[FieldsColumn.FIELD_NAME.ordinal()] = data[FieldEditorColumnInfo.NAME.ordinal()].toString();
                defn[FieldsColumn.FIELD_DESC.ordinal()] = data[FieldEditorColumnInfo.DESCRIPTION.ordinal()].toString();
                defn[FieldsColumn.FIELD_SIZE.ordinal()] = data[FieldEditorColumnInfo.SIZE.ordinal()].toString();
                defn[FieldsColumn.FIELD_TYPE.ordinal()] = data[FieldEditorColumnInfo.INPUT_TYPE.ordinal()].toString();
                defn[FieldsColumn.FIELD_REQUIRED.ordinal()] = data[FieldEditorColumnInfo.REQUIRED.ordinal()].toString();
                defn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = data[FieldEditorColumnInfo.APPLICABILITY.ordinal()].toString();
                defn[FieldsColumn.FIELD_VALUE.ordinal()] = data[FieldEditorColumnInfo.VALUE.ordinal()].toString();

                fieldDefinitions.add(defn);
            }
        }
    }

    /**********************************************************************************************
     * Get the array of data field definitions for the data field editor
     *
     * @return Object array containing the data field definitions used by the data field editor
     *********************************************************************************************/
    protected Object[][] getFieldEditorDefinition()
    {
        List<Object[]> definitions = new ArrayList<Object[]>();

        // Step through each row
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Create storage for a single field definition
            Object[] row = new Object[FieldEditorColumnInfo.values().length];

            // Store the field definition in the proper order
            row[FieldEditorColumnInfo.NAME.ordinal()] = fieldInfo.getFieldName();
            row[FieldEditorColumnInfo.DESCRIPTION.ordinal()] = fieldInfo.getDescription();
            row[FieldEditorColumnInfo.INPUT_TYPE.ordinal()] = fieldInfo.getInputType().getInputName();
            row[FieldEditorColumnInfo.SIZE.ordinal()] = fieldInfo.getSize();
            row[FieldEditorColumnInfo.REQUIRED.ordinal()] = fieldInfo.isRequired();
            row[FieldEditorColumnInfo.APPLICABILITY.ordinal()] = fieldInfo.getApplicabilityType().getApplicabilityName();
            row[FieldEditorColumnInfo.VALUE.ordinal()] = fieldInfo.getValue();

            // Add the field definition to the list
            definitions.add(row);
        }

        return definitions.toArray(new Object[0][0]);
    }

    /**********************************************************************************************
     * Get the data field definitions from the field information
     *
     * @return String list containing the data field definitions
     *********************************************************************************************/
    protected List<String[]> getFieldDefinitionsFromInformation()
    {
        // Create storage for the field definitions
        List<String[]> definitions = new ArrayList<String[]>();

        // Step through each row
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Create storage for a single field definition
            String[] defn = new String[FieldsColumn.values().length];

            // Store the field definition in the proper order
            defn[FieldsColumn.OWNER_NAME.ordinal()] = fieldInfo.getOwnerName();
            defn[FieldsColumn.FIELD_NAME.ordinal()] = fieldInfo.getFieldName();
            defn[FieldsColumn.FIELD_DESC.ordinal()] = fieldInfo.getDescription();
            defn[FieldsColumn.FIELD_TYPE.ordinal()] = fieldInfo.getInputType().getInputName();
            defn[FieldsColumn.FIELD_SIZE.ordinal()] = String.valueOf(fieldInfo.getSize());
            defn[FieldsColumn.FIELD_REQUIRED.ordinal()] = String.valueOf(fieldInfo.isRequired());
            defn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = fieldInfo.getApplicabilityType().getApplicabilityName();
            defn[FieldsColumn.FIELD_VALUE.ordinal()] = fieldInfo.getValue();

            // Add the field definition to the list
            definitions.add(defn);
        }

        return definitions;
    }

    /**********************************************************************************************
     * Add a field. This method is not applicable if the fields for all tables, table types, and
     * groups are loaded
     *
     * @param ownerName
     *            name of the table/table type/group to which the field is a member
     *
     * @param name
     *            name of the new field
     *
     * @param description
     *            field description
     *
     * @param size
     *            field display size in characters
     *
     * @param type
     *            input data type
     *
     * @param isRequired
     *            true if a value if required in this field
     *
     * @param applicability
     *            all, parent, or child to indicate all tables, parent tables only, or child tables
     *            only, respectively
     *
     * @param value
     *            data field value
     *********************************************************************************************/
    protected void addField(String ownerName,
                            String name,
                            String description,
                            int size,
                            String type,
                            boolean isRequired,
                            String applicability,
                            String value)
    {
        fieldInformation.add(new FieldInformation(ownerName,
                                                  name,
                                                  description,
                                                  size,
                                                  type,
                                                  isRequired,
                                                  applicability,
                                                  value));
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
     * Change the owner name for the data fields
     *
     * @param newName
     *            new owner name
     *
     * @return List of field definitions with the updated owner name
     *********************************************************************************************/
    protected List<String[]> renameFieldTable(String newName)
    {
        // Step through each field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Set the owner name to the new name
            fieldInformation.get(index).setOwnerName(newName);
        }

        return getFieldDefinitionsFromInformation();
    }

    /**********************************************************************************************
     * Count the number of the specified field type that exists in the field information
     *
     * @param fieldType
     *            FieldInputType
     *
     * @return The number of the specified field type that exists in the field information
     *********************************************************************************************/
    protected int getFieldTypeCount(InputDataType fieldType)
    {
        int count = 0;

        // Step through each field definition
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Check if the field type matches the specified type
            if (fieldInfo.getInputType().equals(fieldType))
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
     *            InputDataTYpe for which to search
     *
     * @return Value of the data field with the specified input type for the specified field owner;
     *         null if the owner doesn't have a data field of that type
     *********************************************************************************************/
    protected String getFieldValue(String fieldOwner, InputDataType inputType)
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
}
