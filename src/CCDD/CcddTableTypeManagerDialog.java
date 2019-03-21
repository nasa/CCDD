/**
 * CFS Command and Data Dictionary table type manager dialog.
 *
 * Copyright 2017 United States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United States under Title
 * 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.OK_BUTTON;
import static CCDD.CcddConstants.TYPE_COMMAND;
import static CCDD.CcddConstants.TYPE_STRUCTURE;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;

import CCDD.CcddClassesDataTable.CCDDException;
import CCDD.CcddClassesDataTable.TableModification;
import CCDD.CcddConstants.DefaultColumn;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.ManagerDialogType;
import CCDD.CcddConstants.ModifiableColorInfo;
import CCDD.CcddConstants.ModifiableFontInfo;
import CCDD.CcddConstants.ModifiableSpacingInfo;
import CCDD.CcddConstants.OverwriteFieldValueType;
import CCDD.CcddTableTypeHandler.TypeDefinition;

/**************************************************************************************************
 * CFS Command and Data Dictionary table type manager dialog class
 *************************************************************************************************/
@SuppressWarnings("serial")
public class CcddTableTypeManagerDialog extends CcddDialogHandler
{
    // Class references
    private final CcddTableTypeEditorDialog editorDialog;
    private final ManagerDialogType dialogType;
    private final CcddDbTableCommandHandler dbTable;
    private final CcddTableTypeHandler tableTypeHandler;
    private final CcddFieldHandler fieldHandler;

    // Type name text input field
    private JTextField typeNameFld;

    // Name of the currently active data type
    private final String activeTypeName;

    // Storage for the type definition in the event it must be restored after being deleted
    private TypeDefinition savedDefn;

    /**********************************************************************************************
     * Table type manager dialog class constructor
     *
     * @param ccddMain
     *            main class
     *
     * @param editorDialog
     *            reference to the type editor dialog dialog
     *
     * @param dialogType
     *            type manager dialog type: NEW, EDIT, RENAME, COPY, DELETE
     *********************************************************************************************/
    CcddTableTypeManagerDialog(CcddMain ccddMain,
                               CcddTableTypeEditorDialog editorDialog,
                               ManagerDialogType dialogType)
    {
        this.editorDialog = editorDialog;
        this.dialogType = dialogType;

        // Create references to shorten subsequent calls
        dbTable = ccddMain.getDbTableCommandHandler();
        tableTypeHandler = ccddMain.getTableTypeHandler();
        fieldHandler = ccddMain.getFieldHandler();

        // Check if a table type exists
        if (editorDialog.getTypeEditor() != null)
        {
            // Get the name of the currently active table type
            activeTypeName = editorDialog.getTypeEditor().getTypeName();
        }
        // No table type exists
        else
        {
            activeTypeName = "";
        }

        // Create the type management dialog
        initialize();
    }

    /**********************************************************************************************
     * Perform the steps needed following execution of a table type management operation
     *
     * @param commandError
     *            false if the database commands successfully completed; true if an error occurred
     *            and the changes were not made
     *
     * @param fieldDefinitions
     *            list of field definitions; null if this is not a copy operation
     *
     * @param tableNames
     *            array of deleted table names; null if this is not a delete operation
     *********************************************************************************************/
    protected void doTypeOperationComplete(boolean commandError,
                                           List<String[]> fieldDefinitions,
                                           String[] tableNames)
    {
        // Check that no error occurred performing the database commands
        if (!commandError)
        {
            // Rebuild the data field information, accounting for the renamed, copied, or deleted
            // data fields
            fieldHandler.setFieldInformationFromDefinitions(fieldDefinitions);

            // Perform the steps based on the dialog type
            switch (dialogType)
            {
                case RENAME:
                    // Update the table type editor and any open table editors afftecd by the type
                    // name change
                    editorDialog.doTypeModificationComplete(commandError, null, tableNames);

                    // Update the type editor tab text
                    editorDialog.setActiveTypeName(typeNameFld.getText());
                    break;

                case COPY:
                    // Display a type editor for the copy
                    editorDialog.addTypePanes(new String[] {typeNameFld.getText()});
                    break;

                case DELETE:
                    // Create a list to store the names of tables that are no longer valid
                    List<String[]> invalidatedEditors = new ArrayList<String[]>();

                    // Step through each deleted prototype table name
                    for (String name : tableNames)
                    {
                        // Add the pattern for the data type path of tables matching the deleted
                        // prototype table
                        invalidatedEditors.add(new String[] {name, null});
                    }

                    // Close the table editor for each table of the deleted type
                    dbTable.closeDeletedTableEditors(invalidatedEditors, editorDialog);

                    // Remove the tab from the type editor dialog
                    editorDialog.removeActiveTab();
                    break;

                default:
                    break;
            }
        }
        // A database update error occurred
        else
        {
            // Perform the steps based on the dialog type
            switch (dialogType)
            {
                case RENAME:
                    // Restore the original table type name
                    tableTypeHandler.getTypeDefinition(activeTypeName).setName(activeTypeName);
                    break;

                case COPY:
                    // Delete the copied type definition
                    tableTypeHandler.getTypeDefinitions().remove(tableTypeHandler.getTypeDefinition(typeNameFld.getText()));
                    break;

                case DELETE:
                    // Restore the deleted type definition
                    tableTypeHandler.getTypeDefinitions().add(savedDefn);
                    break;

                default:
                    break;
            }
        }
    }

    /**********************************************************************************************
     * Create the table type management dialog
     *********************************************************************************************/
    private void initialize()
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.BOTH,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   0),
                                                        0,
                                                        0);

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());

        // Create dialog based on supplied dialog type
        switch (dialogType)
        {
            case NEW:
                // Create the type creation dialog label and field and add it to the dialog panel
                addTypeNameField("Enter the new type's name", "", dialogPnl, gbc);

                // Create a panel containing allowing addition of default columns to the new table
                // type and add it to the dialog
                JPanel radioBtns = new JPanel(new GridBagLayout());
                addRadioButtons("None",
                                true,
                                new String[][] {{"None", ""},
                                                {TYPE_STRUCTURE, ""},
                                                {TYPE_COMMAND, ""}},
                                null,
                                "Add columns required for table type",
                                false,
                                radioBtns,
                                gbc);
                gbc.gridy++;
                dialogPnl.add(radioBtns, gbc);

                // Get the user's input
                if (showOptionsDialog(editorDialog,
                                      dialogPnl,
                                      "New Type",
                                      DialogOption.CREATE_OPTION) == OK_BUTTON)
                {
                    // Add the table type definition
                    tableTypeHandler.createReplaceTypeDefinition(typeNameFld.getText(),
                                                                 "",
                                                                 DefaultColumn.getDefaultColumnDefinitions(getRadioButtonSelected()));

                    // Add the new table type to the project database
                    dbTable.modifyTableTypeInBackground(typeNameFld.getText(),
                                                        null,
                                                        OverwriteFieldValueType.NONE,
                                                        new ArrayList<String[]>(0),
                                                        new ArrayList<String[]>(0),
                                                        new ArrayList<String[]>(0),
                                                        false,
                                                        null,
                                                        new ArrayList<TableModification>(0),
                                                        new ArrayList<TableModification>(0),
                                                        new ArrayList<TableModification>(0),
                                                        editorDialog,
                                                        null);

                    // Display an new, empty type editor
                    editorDialog.addTypePanes(new String[] {typeNameFld.getText()});
                }

                break;

            case RENAME:
                // Create the type renaming dialog label and field
                addTypeNameField("Enter the new name for the type:",
                                 activeTypeName,
                                 dialogPnl,
                                 gbc);

                // Display the type renaming dialog
                if (showOptionsDialog(editorDialog,
                                      dialogPnl,
                                      "Rename Type",
                                      DialogOption.RENAME_OPTION) == OK_BUTTON
                    && !activeTypeName.equals(typeNameFld.getText()))
                {
                    // Rename the type
                    tableTypeHandler.getTypeDefinition(activeTypeName).setName(typeNameFld.getText());

                    // Update the existing tables of this type to the new type name
                    dbTable.renameTableType(activeTypeName,
                                            typeNameFld.getText(),
                                            CcddTableTypeManagerDialog.this);
                }

                break;

            case COPY:
                // Create the type copying dialog label and field
                addTypeNameField("Enter the name for the type's copy:",
                                 activeTypeName + "_copy",
                                 dialogPnl,
                                 gbc);

                // Display the type copying dialog
                if (showOptionsDialog(editorDialog,
                                      dialogPnl,
                                      "Copy Type",
                                      DialogOption.COPY_OPTION) == OK_BUTTON)
                {
                    // Check if the type exists
                    if (tableTypeHandler.getTypeDefinition(activeTypeName) != null)
                    {
                        // Copy the selected type using the supplied name. The current type data
                        // and description is copied, which may differ from the committed data and
                        // description if the user has made uncommitted changes
                        tableTypeHandler.createReplaceTypeDefinition(typeNameFld.getText(),
                                                                     editorDialog.getTypeEditor().getDescription(),
                                                                     editorDialog.getTypeEditor().getTable().getTableData(true));

                        // Copy the table type to the new type name
                        dbTable.copyTableType(activeTypeName,
                                              typeNameFld.getText(),
                                              CcddTableTypeManagerDialog.this);
                    }
                }

                break;

            case DELETE:
                // Check that the user confirms deletion of the table type
                if (showMessageDialog(editorDialog,
                                      "<html><b>Delete table type '</b>"
                                                    + activeTypeName
                                                    + "<b>'?",
                                      "Delete Type",
                                      JOptionPane.QUESTION_MESSAGE,
                                      DialogOption.DELETE_OPTION) == OK_BUTTON)
                {
                    // Store a copy of the deleted type definition in the event an error occurs or
                    // the user cancels the operation
                    savedDefn = tableTypeHandler.getTypeDefinition(activeTypeName);

                    // Delete the type definition and tables of the deleted type
                    dbTable.deleteTableType(activeTypeName,
                                            savedDefn.isStructure(),
                                            savedDefn.isCommand(),
                                            CcddTableTypeManagerDialog.this,
                                            editorDialog);
                }

                break;

            default:
                break;
        }
    }

    /**********************************************************************************************
     * Add a type name field to the dialog
     *
     * @param labelText
     *            text to display beside the input field
     *
     * @param intialName
     *            text to initially display in the name field
     *
     * @param dialogPnl
     *            panel to which to add the input field
     *
     * @param dialogGbc
     *            dialog panel GridBagLayout layout constraints
     *********************************************************************************************/
    private void addTypeNameField(String labelText,
                                  String intialName,
                                  JPanel dialogPnl,
                                  GridBagConstraints dialogGbc)
    {
        // Set the initial layout manager characteristics
        GridBagConstraints gbc = new GridBagConstraints(0,
                                                        0,
                                                        1,
                                                        1,
                                                        1.0,
                                                        0.0,
                                                        GridBagConstraints.LINE_START,
                                                        GridBagConstraints.NONE,
                                                        new Insets(ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing(),
                                                                   0,
                                                                   ModifiableSpacingInfo.LABEL_VERTICAL_SPACING.getSpacing() / 2,
                                                                   ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing()),
                                                        0,
                                                        0);

        // Create the type name label and field
        JLabel label = new JLabel(labelText);
        label.setFont(ModifiableFontInfo.LABEL_BOLD.getFont());
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.add(label, gbc);

        typeNameFld = new JTextField(intialName, 20);
        typeNameFld.setFont(ModifiableFontInfo.INPUT_TEXT.getFont());
        typeNameFld.setEditable(true);
        typeNameFld.setForeground(ModifiableColorInfo.INPUT_TEXT.getColor());
        typeNameFld.setBackground(ModifiableColorInfo.INPUT_BACK.getColor());
        typeNameFld.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                                 Color.LIGHT_GRAY,
                                                                                                 Color.GRAY),
                                                                 BorderFactory.createEmptyBorder(ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing(),
                                                                                                 ModifiableSpacingInfo.INPUT_FIELD_PADDING.getSpacing())));

        // Add the label to the dialog panel
        gbc.gridy++;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets.left = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        gbc.insets.bottom = 0;
        pnl.add(typeNameFld, gbc);
        dialogGbc.weighty = 0.0;
        dialogGbc.gridy++;
        dialogGbc.gridwidth = GridBagConstraints.REMAINDER;
        dialogGbc.fill = GridBagConstraints.HORIZONTAL;
        dialogGbc.insets.right = ModifiableSpacingInfo.LABEL_HORIZONTAL_SPACING.getSpacing();
        dialogGbc.insets.bottom = 0;
        dialogPnl.add(pnl, dialogGbc);
    }

    /**********************************************************************************************
     * Verify that the dialog content is valid
     *
     * @return true if the input values are valid
     *********************************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            // Verify the dialog content based on the supplied dialog type
            switch (dialogType)
            {
                case NEW:
                case RENAME:
                case COPY:
                    // Remove any excess white space
                    typeNameFld.setText(typeNameFld.getText().trim());

                    // Check if the database name is blank
                    if (typeNameFld.getText().isEmpty())
                    {
                        // Inform the user that the name is invalid
                        throw new CCDDException("Table type name must be entered");
                    }

                    // Get the list of available tables
                    String[] types = tableTypeHandler.getTableTypeNames();

                    // Step through each of the type names
                    for (String type : types)
                    {
                        // Check if the user-supplied name matches an existing type name (case
                        // insensitive)
                        if (type.equalsIgnoreCase(typeNameFld.getText())
                            && !type.equals(activeTypeName))
                        {
                            // Inform the user that the name is already in use
                            throw new CCDDException("Table type name is already in use");
                        }
                    }

                    break;

                default:
                    break;
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddTableTypeManagerDialog.this,
                                                      "<html><b>" + ce.getMessage(),
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
