/**
 * CFS Command and Data Dictionary import support handler.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.CANCEL_BUTTON;
import static CCDD.CcddConstants.IGNORE_BUTTON;

import java.awt.Component;
import java.util.List;

import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.FieldInformation;
import CCDD.CcddClassesDataTable.GroupInformation;
import CCDD.CcddClassesDataTable.ProjectDefinition;
import CCDD.CcddClassesDataTable.TableDefinition;
import CCDD.CcddClassesDataTable.TableTypeDefinition;
import CCDD.CcddConstants.ApplicabilityType;
import CCDD.CcddConstants.DefaultInputType;
import CCDD.CcddConstants.GroupDefinitionColumn;
import CCDD.CcddConstants.InternalTable.FieldsColumn;
import CCDD.CcddConstants.TableTypeEditorColumnInfo;

/**************************************************************************************************
 * CFS Command and Data Dictionary import support handler class
 *************************************************************************************************/
public class CcddImportSupportHandler
{
    // Names of the structure tables that represent the common header for all telemetry and command
    // tables
    protected String tlmHeaderTable;
    protected String cmdHeaderTable;

    // Telemetry and command header variable names for the application ID, and command header
    // variable name for the command function code
    protected String applicationIDName;
    protected String cmdFuncCodeName;

    // Basic primitive data types
    protected static enum BasePrimitiveDataType
    {
        INTEGER,
        FLOAT,
        STRING
    }

    /**********************************************************************************************
     * Default application ID and command function code header table variable names
     *********************************************************************************************/
    protected static enum DefaultHeaderVariableName
    {
        APP_ID("applicationID"),
        FUNC_CODE("functionCode");

        private final String defaultVariableName;

        /******************************************************************************************
         * Default application ID and command function code header table variable names constructor
         *
         * @param defaultVariableName
         *            default variable name
         *****************************************************************************************/
        DefaultHeaderVariableName(String defaultVariableName)
        {
            this.defaultVariableName = defaultVariableName;
        }

        /******************************************************************************************
         * Get the default variable name
         *
         * @return Default variable name
         *****************************************************************************************/
        protected String getDefaultVariableName()
        {
            return defaultVariableName;
        }
    }

    /**********************************************************************************************
     * Add a table type column definition after verifying the input parameters
     *
     * @param continueOnError
     *            current state of the flag that indicates if all table type errors should be
     *            ignored
     *
     * @param tableTypeDefn
     *            reference to the TableTypeDefinition to which this column definition applies
     *
     * @param columnDefn
     *            array containing the table type column definition
     *
     * @param fileName
     *            import file name
     *
     * @param inputTypeHandler
     *            input type handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the user elected to ignore the column error
     *
     * @throws CCDDException
     *             If the column name is missing or the user elects to stop the import operation
     *             due to an invalid input type
     *********************************************************************************************/
    protected boolean addImportedTableTypeColumnDefinition(boolean continueOnError,
                                                           TableTypeDefinition tableTypeDefn,
                                                           String[] columnDefn,
                                                           String fileName,
                                                           CcddInputTypeHandler inputTypeHandler,
                                                           Component parent) throws CCDDException
    {
        // Check if the column name is empty
        if (columnDefn[TableTypeEditorColumnInfo.NAME.ordinal()].isEmpty())
        {
            // Inform the user that the column name is missing
            throw new CCDDException("Table type '</b>"
                                    + tableTypeDefn.getTypeName()
                                    + "<b>' definition column name missing");
        }

        // Check if the input type is empty
        if (columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()].isEmpty())
        {
            // Default to text
            columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }
        // Check if the input type name is invalid
        else if (!inputTypeHandler.isInputTypeValid(columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]))
        {
            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Table type '</b>"
                                                                + tableTypeDefn.getTypeName()
                                                                + "<b>' definition input type '</b>"
                                                                + columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()]
                                                                + "<b>' unrecognized in import file '</b>"
                                                                + fileName
                                                                + "<b>'; continue?",
                                               "Table Type Error",
                                               "Ignore this error (default to 'Text')",
                                               "Ignore this and any remaining invalid table types (use default "
                                                                                        + "values where possible, or skip the affected table type)",
                                               "Stop importing",
                                               parent);

            // Default to text
            columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }

        // Add the table type column definition
        tableTypeDefn.addColumn(new Object[] {Integer.valueOf(columnDefn[TableTypeEditorColumnInfo.INDEX.ordinal()]),
                                              columnDefn[TableTypeEditorColumnInfo.NAME.ordinal()],
                                              columnDefn[TableTypeEditorColumnInfo.DESCRIPTION.ordinal()],
                                              columnDefn[TableTypeEditorColumnInfo.INPUT_TYPE.ordinal()],
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.UNIQUE.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.REQUIRED.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.STRUCTURE_ALLOWED.ordinal()]),
                                              Boolean.valueOf(columnDefn[TableTypeEditorColumnInfo.POINTER_ALLOWED.ordinal()])});

        return continueOnError;
    }

    /**********************************************************************************************
     * Add a data field definition after verifying the input parameters are valid (use defaults for
     * field size, input type, or applicability if these parameters that are not supplied). For
     * project-level or table type fields, if the field already exists for this owner compare the
     * field definition's input type, required status, applicability, and value; if a mismatch is
     * found allow the user to determine how to proceed (this check is unnecessary for table fields
     * since the new ones either replace existing ones or are ignored, based on the import flags)
     *
     * @param continueOnError
     *            current state of the flag that indicates if all data field errors should be
     *            ignored
     *
     * @param defnContainer
     *            TableDefinition, TableTypeDefinition, or projectDefinition object to which this
     *            data field applies
     *
     * @param fieldDefn
     *            array containing the data field definition
     *
     * @param fileName
     *            import file name
     *
     * @param inputTypeHandler
     *            input type handler reference
     *
     * @param fieldHandler
     *            data field handler reference
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the user elected to ignore the data field error
     *
     * @throws CCDDException
     *             If the data field name is missing or the user elects to stop the import
     *             operation due to an invalid input type
     *********************************************************************************************/
    protected boolean addImportedDataFieldDefinition(boolean continueOnError,
                                                     Object defnContainer,
                                                     String[] fieldDefn,
                                                     String fileName,
                                                     CcddInputTypeHandler inputTypeHandler,
                                                     CcddFieldHandler fieldHandler,
                                                     Component parent) throws CCDDException
    {
        boolean isError = false;

        // Check if the field name is empty
        if (fieldDefn[FieldsColumn.FIELD_NAME.ordinal()].isEmpty())
        {
            // Inform the user that the field name is missing
            throw new CCDDException("Data field name missing in import file '</b>"
                                    + fileName
                                    + "<b>'");
        }

        // Check if the field size is empty
        if (fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()].isEmpty())
        {
            // Use the default value
            fieldDefn[FieldsColumn.FIELD_SIZE.ordinal()] = "10";
        }

        // Check if the field required indicator is empty
        if (fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].isEmpty())
        {
            // Default to not required
            fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()] = "false";
        }

        // Check if the input type is empty
        if (fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].isEmpty())
        {
            // Default to text
            fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }
        // Check if the input type name is invalid
        else if (!inputTypeHandler.isInputTypeValid(fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]))
        {
            isError = true;

            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Data field '</b>"
                                                                + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                                + "<b>' definition input type '</b>"
                                                                + fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()]
                                                                + "<b>' for owner '</b>"
                                                                + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                                                + "<b>' unrecognized in import file '</b>"
                                                                + fileName
                                                                + "<b>'; continue?",
                                               "Data Field Error",
                                               "Ignore this data field error (default to 'Text')",
                                               "Ignore this and any remaining invalid data fields (use default "
                                                                                                   + "values where possible, or skip the affected data field)",
                                               "Stop importing",
                                               parent);

            // Default to text
            fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()] = DefaultInputType.TEXT.getInputName();
        }

        // Check if the applicability is empty
        if (fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].isEmpty())
        {
            // Default to all tables being applicable
            fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = ApplicabilityType.ALL.getApplicabilityName();
        }
        // Check if the applicability is invalid
        else if (ApplicabilityType.getApplicabilityByName(fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()]) == null)
        {
            isError = true;

            // Check if the error should be ignored or the import canceled
            continueOnError = getErrorResponse(continueOnError,
                                               "<html><b>Data field '</b>"
                                                                + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                                + "<b>' definition applicability type '</b>"
                                                                + fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()]
                                                                + "<b>' for owner '</b>"
                                                                + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                                                + "<b>' unrecognized in import file '</b>"
                                                                + fileName
                                                                + "<b>'; continue?",
                                               "Data Field Error",
                                               "Ignore this data field error (default to 'All tables')",
                                               "Ignore this and any remaining invalid data fields (use default values)",
                                               "Stop importing",
                                               parent);

            // Default to all tables being applicable
            fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()] = ApplicabilityType.ALL.getApplicabilityName();
        }

        // Check if the inherited status is empty
        if (fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()].isEmpty())
        {
            // Initialize the status to false (not inherited)
            fieldDefn[FieldsColumn.FIELD_INHERITED.ordinal()] = "false";
        }

        // Check if no error was detected or if the user elected to ignore an error
        if (!isError || continueOnError)
        {
            boolean addField = true;

            // Check if this isn't a table's data field definition. If the field is for a table
            // then the field is only added if the table doesn't already exist (and hence the field
            // can't already exist), or the table does exist and the flag to replace existing
            // tables is set to true (in which case the field is overwritten so its current
            // definition doesn't matter)
            if (!(defnContainer instanceof TableDefinition))
            {
                // Get the reference to the data field from the existing field information
                FieldInformation fieldInfo = fieldHandler.getFieldInformationByName(fieldDefn[FieldsColumn.OWNER_NAME.ordinal()],
                                                                                    fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]);

                // Check if this field already exists
                if (fieldInfo != null)
                {
                    // Set the flag to indicate the field shouldn't be added since it already
                    // exists
                    addField = false;

                    // Check if the field's input type, required state, applicability, or value
                    // don't match (the description and size are allowed to differ)
                    if (!fieldDefn[FieldsColumn.FIELD_TYPE.ordinal()].equals(fieldInfo.getInputType().getInputName())
                        || !fieldDefn[FieldsColumn.FIELD_REQUIRED.ordinal()].equalsIgnoreCase(Boolean.toString(fieldInfo.isRequired()))
                        || !fieldDefn[FieldsColumn.FIELD_APPLICABILITY.ordinal()].equals(fieldInfo.getApplicabilityType().getApplicabilityName())
                        || !fieldDefn[FieldsColumn.FIELD_VALUE.ordinal()].equals(fieldInfo.getValue()))
                    {
                        // Check if the error should be ignored or the import canceled
                        continueOnError = getErrorResponse(continueOnError,
                                                           "<html><b>Data field '</b>"
                                                                            + fieldDefn[FieldsColumn.FIELD_NAME.ordinal()]
                                                                            + "<b>' for owner '</b>"
                                                                            + fieldDefn[FieldsColumn.OWNER_NAME.ordinal()]
                                                                            + "<b>' doesn't match the existing definition in import file '</b>"
                                                                            + fileName
                                                                            + "<b>'; continue?",
                                                           "Data Field Error",
                                                           "Ignore this data field (keep existing field)",
                                                           "Ignore this and any remaining invalid data fields (use default values or keep existing)",
                                                           "Stop importing",
                                                           parent);
                    }
                }
            }

            // Check if the field definition should be added
            if (addField)
            {
                // Check if the field belongs to the project
                if (defnContainer instanceof ProjectDefinition)
                {
                    // Add the data field to the project
                    ((ProjectDefinition) defnContainer).addDataField(fieldDefn);
                }
                // Check if the field belongs to a table
                else if (defnContainer instanceof TableDefinition)
                {
                    // Add the data field to the table
                    ((TableDefinition) defnContainer).addDataField(fieldDefn);
                }
                // Check if the field belongs to a table type
                else if (defnContainer instanceof TableTypeDefinition)
                {
                    // Add the data field to the table type
                    ((TableTypeDefinition) defnContainer).addDataField(fieldDefn);
                }
            }
        }

        return continueOnError;
    }

    /**********************************************************************************************
     * Build the project-level and group data fields
     *
     * @param fieldHandler
     *            data field handler reference
     *
     * @param dataFields
     *            list containing the data field definitions from the import file
     *********************************************************************************************/
    protected void buildProjectAndGroupDataFields(CcddFieldHandler fieldHandler,
                                                  List<String[]> dataFields)
    {
        // Check if any project-level or group data fields exist in the import file
        if (!dataFields.isEmpty())
        {
            boolean isNewField = false;

            // Get the current data field definitions
            List<String[]> fieldDefinitions = fieldHandler.getFieldDefnsFromInfo();

            // Step through each project data field
            for (String[] dataField : dataFields)
            {
                // Add the field definition to the list and set the flag to indicate a new field is
                // added
                fieldDefinitions.add(dataField);
                isNewField = true;
            }

            // Check if a new data field was added
            if (isNewField)
            {
                // Rebuild the field information with the new field(s)
                fieldHandler.setFieldInformationFromDefinitions(fieldDefinitions);
            }
        }
    }

    /**********************************************************************************************
     * Add a group's information from the supplied group definition after verifying the input
     * parameters
     *
     * @param groupDefn
     *            array containing the group definition
     *
     * @param fileName
     *            import file name
     *
     * @param groupHandler
     *            group handler reference
     *
     * @throws CCDDException
     *             If the group name or member table list is missing
     *********************************************************************************************/
    protected void addImportedGroupDefinition(String[] groupDefn,
                                              String fileName,
                                              CcddGroupHandler groupHandler) throws CCDDException
    {
        // Check if the group name is empty
        if (groupDefn[GroupDefinitionColumn.NAME.ordinal()].isEmpty())
        {
            // Inform the user that the group name is missing
            throw new CCDDException("Group name missing in import file '</b>"
                                    + fileName
                                    + "<b>'");
        }

        // Get the reference to the data field from the existing field information
        GroupInformation groupInfo = groupHandler.getGroupInformationByName(groupDefn[GroupDefinitionColumn.NAME.ordinal()]);

        // Check if this is a new group
        if (groupInfo == null)
        {
            // Add the group information
            groupInfo = groupHandler.addGroupInformation(groupDefn[GroupDefinitionColumn.NAME.ordinal()],
                                                         groupDefn[GroupDefinitionColumn.DESCRIPTION.ordinal()],
                                                         Boolean.parseBoolean(groupDefn[GroupDefinitionColumn.IS_APPLICATION.ordinal()]));

            // Check if the group has any table members
            if (!groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].isEmpty())
            {
                // Step through each table member
                for (String member : groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].split(";"))
                {
                    // Add the member to the group
                    groupInfo.addTable(member);
                }
            }
        }
        // A group by this name already exists
        else
        {
            // Get the array of table members, if any
            String[] members = groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].isEmpty()
                                                                                            ? new String[] {}
                                                                                            : groupDefn[GroupDefinitionColumn.MEMBERS.ordinal()].split(";");

            // Set the flag if the number of members differs
            boolean isMismatch = members.length != groupInfo.getTablesAndAncestors().size();

            // Check if the number of members is the same
            if (!isMismatch)
            {
                // Step through each member
                for (int index = 0; index < members.length; index++)
                {
                    // Check if the member isn't present in the existing group definition
                    if (!groupInfo.getTablesAndAncestors().contains(members[index]))
                    {
                        // Set the flag to indicate the group definitions differ and stop searching
                        isMismatch = true;
                        break;
                    }
                }
            }

            // Check if the existing group's table members or application status don't match (the
            // description is allowed to differ)
            if (isMismatch
                || !groupDefn[GroupDefinitionColumn.IS_APPLICATION.ordinal()].equals(Boolean.toString(groupInfo.isApplication())))
            {
                throw new CCDDException("Imported group '<b>"
                                        + groupDefn[0]
                                        + "</b>' doesn't match the existing definition");
            }
        }
    }

    /**********************************************************************************************
     * Set the telemetry header table name, command header table name, application ID variable
     * name, and command function code variable name from the project database fields or default
     * values, if not present in the import file. Based on the input flag build the project-level
     * data fields for these names
     *
     * @param fieldHandler
     *            data field handler reference
     *
     * @param isCreateField
     *            true is the project-level data fields are to be created
     *
     * @param tlmHdrTable
     *            name of the structure table that represents the common header for all telemetry
     *            tables; null if not present in the import file
     *
     * @param cmdHdrTable
     *            name of the structure table that represents the common header for all command
     *            tables; null if not present in the import file
     *
     * @param appIDName
     *            telemetry and command header variable names for the application ID; null if not
     *            present in the import file
     *
     * @param funcCodeName
     *            command header variable name for the command function code; null if not present
     *            in the import file
     *
     * @throws CCDDException
     *             If the data field with the same input type already exists and the imported field
     *             doesn't match
     *********************************************************************************************/
    protected void setProjectHeaderTablesAndVariables(CcddFieldHandler fieldHandler,
                                                      boolean isCreateField,
                                                      String tlmHdrTable,
                                                      String cmdHdrTable,
                                                      String appIDName,
                                                      String funcCodeName) throws CCDDException
    {
        ProjectDefinition projectDefn = new ProjectDefinition();
        tlmHeaderTable = tlmHdrTable;
        cmdHeaderTable = cmdHdrTable;
        applicationIDName = appIDName;
        cmdFuncCodeName = funcCodeName;

        // Check if the telemetry table name isn't set in the project import file
        if (tlmHeaderTable == null)
        {
            // Get the name of the table representing the telemetry header from the project
            tlmHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                        DefaultInputType.XML_TLM_HDR);
        }
        // The telemetry header table name is set in the import file. Check if the project-level
        // data fields are to be created
        else if (isCreateField)
        {
            // Add the telemetry header table name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Telemetry header table name",
                                                   "Name of the structure table representing the telemetry header",
                                                   String.valueOf(Math.min(Math.max(tlmHeaderTable.length(),
                                                                                    5),
                                                                           40)),
                                                   DefaultInputType.XML_TLM_HDR.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   tlmHeaderTable,
                                                   "false"});
        }

        // Check if the command table name isn't set in the project import file
        if (cmdHeaderTable == null)
        {
            // Get the name of the table representing the command header from the project
            cmdHeaderTable = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                        DefaultInputType.XML_CMD_HDR);
        }
        // The command header table name is set in the import file. Check if the project-level data
        // fields are to be created
        else if (isCreateField)
        {
            // Add the command header table name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Command header table name",
                                                   "Name of the structure table representing the command header",
                                                   String.valueOf(Math.min(Math.max(cmdHeaderTable.length(),
                                                                                    5),
                                                                           40)),
                                                   DefaultInputType.XML_CMD_HDR.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   cmdHeaderTable,
                                                   "false"});
        }

        // Check if the application ID variable name isn't set in the project import file
        if (applicationIDName == null)
        {
            // Get the application ID variable name from the project field
            applicationIDName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                           DefaultInputType.XML_APP_ID);

            // Check if the application ID variable name isn't set in the project
            if (applicationIDName == null)
            {
                // Use the default application ID variable name
                applicationIDName = DefaultHeaderVariableName.APP_ID.getDefaultVariableName();
            }
        }
        // The application ID variable name is set in the import file. Check if the project-level
        // data fields are to be created
        else if (isCreateField)
        {
            // Add the application ID variable name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Application ID",
                                                   "Name of the variable containing the application ID in the structure "
                                                                     + "tables representing the telemetry and command headers",
                                                   String.valueOf(Math.min(Math.max(applicationIDName.length(),
                                                                                    5),
                                                                           40)),
                                                   DefaultInputType.XML_APP_ID.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   applicationIDName,
                                                   "false"});
        }

        // Check if the command function code variable name isn't set in the import file
        if (cmdFuncCodeName == null)
        {
            // Get the command function code variable name from the project field
            cmdFuncCodeName = fieldHandler.getFieldValue(CcddFieldHandler.getFieldProjectName(),
                                                         DefaultInputType.XML_FUNC_CODE);

            // Check if the command function code variable name isn't set in the project
            if (cmdFuncCodeName == null)
            {
                // Use the default command function code variable name
                cmdFuncCodeName = DefaultHeaderVariableName.FUNC_CODE.getDefaultVariableName();
            }
        }
        // The command function code variable name is set in the import file. Check if the
        // project-level data fields are to be created
        else if (isCreateField)
        {
            // Add the application ID variable name data field definition
            projectDefn.addDataField(new String[] {CcddFieldHandler.getFieldProjectName(),
                                                   "Command function code",
                                                   "Name of the variable containing the command function code in the "
                                                                            + "structure table representing the command header",
                                                   String.valueOf(Math.min(Math.max(cmdFuncCodeName.length(),
                                                                                    5),
                                                                           40)),
                                                   DefaultInputType.XML_FUNC_CODE.getInputName(),
                                                   "false",
                                                   ApplicabilityType.ALL.getApplicabilityName(),
                                                   cmdFuncCodeName,
                                                   "false"});
        }

        // Check if the project-level data fields are to be created
        if (isCreateField)
        {
            // Build the imported project-level data fields, if any
            buildProjectAndGroupDataFields(fieldHandler, projectDefn.getDataFields());
        }
    }

    /**********************************************************************************************
     * Display an Ignore/Ignore All/Cancel dialog in order to get the response to an error
     * condition. The user may elect to ignore the one instance of this type of error, all
     * instances of this type of error, or cancel the operation
     *
     * @param continueOnError
     *            current state of the flag that indicates if all errors of this type should be
     *            ignored
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param ignoreToolTip
     *            Ignore button tool tip text; null if no tool tip is to be displayed
     *
     * @param ignoreAllToolTip
     *            Ignore All button tool tip text; null if no tool tip is to be displayed
     *
     * @param cancelToolTip
     *            Cancel button tool tip text; null if no tool tip is to be displayed
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the user elected to ignore errors of this type
     *
     * @throws CCDDException
     *             If the user selects the Cancel button
     *********************************************************************************************/
    protected boolean getErrorResponse(boolean continueOnError,
                                       String message,
                                       String title,
                                       String ignoreToolTip,
                                       String ignoreAllToolTip,
                                       String cancelToolTip,
                                       Component parent) throws CCDDException
    {
        return getErrorResponse(continueOnError,
                                message,
                                title,
                                ignoreToolTip,
                                ignoreAllToolTip,
                                cancelToolTip,
                                false,
                                parent);
    }

    /**********************************************************************************************
     * Display an Ignore/Ignore All/Cancel or Ignore All/Cancel dialog in order to get the response
     * to an error condition. The user may elect to ignore the one instance of this type of error,
     * all instances of this type of error, or cancel the operation
     *
     * @param continueOnError
     *            current state of the flag that indicates if all errors of this type should be
     *            ignored
     *
     * @param message
     *            text message to display
     *
     * @param title
     *            title to display in the dialog window frame
     *
     * @param ignoreToolTip
     *            Ignore button tool tip text; null if no tool tip is to be displayed
     *
     * @param ignoreAllToolTip
     *            Ignore All button tool tip text; null if no tool tip is to be displayed
     *
     * @param cancelToolTip
     *            Cancel button tool tip text; null if no tool tip is to be displayed
     *
     * @param noIgnore
     *            true to not display the Ignore button
     *
     * @param parent
     *            GUI component over which to center any error dialog
     *
     * @return true if the user elected to ignore errors of this type
     *
     * @throws CCDDException
     *             If the user selects the Cancel button
     *********************************************************************************************/
    protected boolean getErrorResponse(boolean continueOnError,
                                       String message,
                                       String title,
                                       String ignoreToolTip,
                                       String ignoreAllToolTip,
                                       String cancelToolTip,
                                       boolean noIgnore,
                                       Component parent) throws CCDDException
    {
        // Check if the user hasn't already elected to ignore this type of error
        if (!continueOnError)
        {
            // Inform the user that the imported item is incorrect
            int buttonSelected = new CcddDialogHandler().showIgnoreCancelDialog(parent,
                                                                                message,
                                                                                title,
                                                                                ignoreToolTip,
                                                                                ignoreAllToolTip,
                                                                                cancelToolTip,
                                                                                noIgnore);

            // Check if the Ignore All button was pressed
            if (buttonSelected == IGNORE_BUTTON)
            {
                // Set the flag to ignore subsequent errors of this type
                continueOnError = true;
            }
            // Check if the Cancel button was pressed
            else if (buttonSelected == CANCEL_BUTTON)
            {
                // No error message is provided since the user chose this action
                throw new CCDDException();
            }
        }

        return continueOnError;
    }

    /**********************************************************************************************
     * Convert the primitive data type into the base equivalent
     *
     * @param dataType
     *            data type
     *
     * @param dataTypeHandler
     *            reference to the data type handler
     *
     * @return Base primitive data type corresponding to the specified primitive data type; null if
     *         no match
     *********************************************************************************************/
    protected static BasePrimitiveDataType getBaseDataType(String dataType,
                                                           CcddDataTypeHandler dataTypeHandler)
    {
        BasePrimitiveDataType basePrimitiveDataType = null;

        // Check if the type is an integer (signed or unsigned)
        if (dataTypeHandler.isInteger(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.INTEGER;
        }
        // Check if the type is a floating point (float or double)
        else if (dataTypeHandler.isFloat(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.FLOAT;
        }
        // Check if the type is a string (character or string)
        else if (dataTypeHandler.isCharacter(dataType))
        {
            basePrimitiveDataType = BasePrimitiveDataType.STRING;
        }

        return basePrimitiveDataType;
    }

    /**********************************************************************************************
     * Get the data type name determined by the specified data type size and match criteria
     *
     * @param sizeInBytes
     *            data type size in bytes
     *
     * @param isInteger
     *            true if the data type to match is an integer
     *
     * @param isUnsigned
     *            true if the data type to match is an unsigned integer
     *
     * @param isFloat
     *            true if the data type to match is a floating point
     *
     * @param isString
     *            true if the data type to match is a character or string
     *
     * @param dataTypeHandler
     *            reference to the data type handler
     *
     * @return The name of the data type from the existing data type definitions that matches the
     *         input criteria; null if there is no match
     *********************************************************************************************/
    protected String getMatchingDataType(long sizeInBytes,
                                         boolean isInteger,
                                         boolean isUnsigned,
                                         boolean isFloat,
                                         boolean isString,
                                         CcddDataTypeHandler dataTypeHandler)
    {
        String dataType = null;

        // Step through each defined data type
        for (String[] dataTypeDefn : dataTypeHandler.getDataTypeData())
        {
            String dataTypeName = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);

            // Check if the type to match is a string (vs a character)
            if (isString && sizeInBytes > 1 && dataTypeHandler.isString(dataTypeName))
            {
                // Store the matching string data type and stop searching
                dataType = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);
                break;
            }

            // Check if the size in bytes matches the one for this data type
            if (sizeInBytes == dataTypeHandler.getDataTypeSize(dataTypeName))
            {
                // Check if the type indicated by the input flags matches the data type
                if ((isInteger && !isUnsigned && dataTypeHandler.isInteger(dataTypeName))
                    || (isInteger && isUnsigned && dataTypeHandler.isUnsignedInt(dataTypeName))
                    || (isFloat && dataTypeHandler.isFloat(dataTypeName))
                    || (isString && dataTypeHandler.isCharacter(dataTypeName)))
                {
                    // Store the matching data type and stop searching
                    dataType = CcddDataTypeHandler.getDataTypeName(dataTypeDefn);
                    break;
                }
            }
        }

        return dataType;
    }

    /**********************************************************************************************
     * Replace each invalid character with an underscore and move any leading underscores to the
     * end of each path segment
     *
     * @param path
     *            system path in the form {@literal <</>path1</path2<...>>}
     *
     * @return Path with each invalid character replaced with an underscore and any leading
     *         underscores moved to the end of each path segment
     *********************************************************************************************/
    protected static String cleanSystemPath(String path)
    {
        // Check if the path exists
        if (path != null)
        {
            // Replace each space with an underscore and move any leading underscores to the end of
            // each path segment
            path = path.replaceAll("\\]", "")
                       .replaceAll("[^A-Za-z0-9_\\-\\/]", "_")
                       .replaceAll("(^|/)_([^/]*)", "$1$2_");
        }

        return path;
    }
}
