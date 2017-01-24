/**
 * CFS Command & Data Dictionary table message ID assignment dialog. Copyright
 * 2017 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration. No copyright is claimed in
 * the United States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;
import static CCDD.CcddConstants.OK_BUTTON;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;

import CCDD.CcddClasses.CCDDException;
import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddConstants.InternalTable;

/******************************************************************************
 * CFS Command & Data Dictionary table message ID assignment dialog class
 *****************************************************************************/
@SuppressWarnings("serial")
public class CcddAssignTableMsgIDDialog extends CcddDialogHandler
{
    // Class references
    private final CcddMain ccddMain;
    private final CcddDbTableCommandHandler dbTable;

    // Components that need to be accessed by multiple methods
    private JTextField msgIDNameFld;
    private JTextField startMsgIDFld;
    private JTextField msgIDIntervalFld;
    private JCheckBox overwriteCbx;

    /**************************************************************************
     * Table message ID assignment dialog class constructor
     * 
     * @param ccddMain
     *            main class
     *************************************************************************/
    protected CcddAssignTableMsgIDDialog(CcddMain ccddMain)
    {
        this.ccddMain = ccddMain;
        dbTable = ccddMain.getDbTableCommandHandler();

        // Create the table message ID assignment dialog
        initialize();
    }

    /**************************************************************************
     * Create the table message ID assignment dialog
     *************************************************************************/
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
                                                        GridBagConstraints.NONE,
                                                        new Insets(LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2,
                                                                   LABEL_VERTICAL_SPACING,
                                                                   LABEL_HORIZONTAL_SPACING / 2),
                                                        0,
                                                        0);

        // Create a border for the input fields
        Border border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                           Color.LIGHT_GRAY,
                                                                                           Color.GRAY),
                                                           BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Create a panel to contain the dialog components
        JPanel dialogPnl = new JPanel(new GridBagLayout());
        JPanel inputPnl = new JPanel(new GridBagLayout());
        dialogPnl.setBorder(BorderFactory.createEmptyBorder());

        // Create the message ID field name label
        JLabel idLbl = new JLabel("Message ID field name");
        idLbl.setFont(LABEL_FONT_BOLD);
        inputPnl.add(idLbl, gbc);

        // Create the message ID field name input field
        msgIDNameFld = new JTextField("Message ID", 15);
        msgIDNameFld.setFont(LABEL_FONT_PLAIN);
        msgIDNameFld.setEditable(true);
        msgIDNameFld.setForeground(Color.BLACK);
        msgIDNameFld.setBackground(Color.WHITE);
        msgIDNameFld.setBorder(border);
        gbc.gridx++;
        inputPnl.add(msgIDNameFld, gbc);

        // Create the starting message ID label
        JLabel startIDLbl = new JLabel("Starting ID");
        startIDLbl.setFont(LABEL_FONT_BOLD);
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(startIDLbl, gbc);

        // Create the starting message ID field
        startMsgIDFld = new JTextField("0x0", 7);
        startMsgIDFld.setFont(LABEL_FONT_PLAIN);
        startMsgIDFld.setEditable(true);
        startMsgIDFld.setForeground(Color.BLACK);
        startMsgIDFld.setBackground(Color.WHITE);
        startMsgIDFld.setBorder(border);
        startMsgIDFld.setToolTipText("<html>Format: <i>&lt;</i>0x<i>&gt;hexadecimal digits");
        gbc.gridx++;
        inputPnl.add(startMsgIDFld, gbc);

        // Create the message ID interval label
        JLabel intervalLbl = new JLabel("ID interval");
        intervalLbl.setFont(LABEL_FONT_BOLD);
        gbc.gridx = 0;
        gbc.gridy++;
        inputPnl.add(intervalLbl, gbc);

        // Create the message ID interval field
        msgIDIntervalFld = new JTextField("1", 5);
        msgIDIntervalFld.setFont(LABEL_FONT_PLAIN);
        msgIDIntervalFld.setEditable(true);
        msgIDIntervalFld.setForeground(Color.BLACK);
        msgIDIntervalFld.setBackground(Color.WHITE);
        msgIDIntervalFld.setBorder(border);
        gbc.gridx++;
        inputPnl.add(msgIDIntervalFld, gbc);

        // Add the input panel to the dialog panel
        gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        gbc.gridx = 0;
        gbc.gridy = 0;
        dialogPnl.add(inputPnl, gbc);

        // Create the overwrite existing IDs check box
        overwriteCbx = new JCheckBox("Overwrite existing IDs");
        overwriteCbx.setFont(LABEL_FONT_BOLD);
        overwriteCbx.setBorder(BorderFactory.createEmptyBorder());
        gbc.insets.left = LABEL_HORIZONTAL_SPACING;
        gbc.insets.bottom = 0;
        gbc.gridy++;
        dialogPnl.add(overwriteCbx, gbc);

        // Get the user's input
        if (showOptionsDialog(ccddMain.getMainFrame(),
                              dialogPnl,
                              "Assign Table Message IDs",
                              DialogOption.OK_CANCEL_OPTION) == OK_BUTTON)
        {
            // Update the table message ID data fields
            updateMessageIDFields();
        }
    }

    /**************************************************************************
     * Update the table table message ID data fields
     *************************************************************************/
    private void updateMessageIDFields()
    {
        boolean isChanges = false;

        // Get the starting message ID and ID interval values
        int startID = Integer.decode(startMsgIDFld.getText());
        int interval = Integer.valueOf(msgIDIntervalFld.getText());

        // Create a field handler and populate it with the field definitions
        // for all of the tables in the database
        CcddFieldHandler fieldHandler = new CcddFieldHandler(ccddMain,
                                                             null,
                                                             CcddAssignTableMsgIDDialog.this);
        List<FieldInformation> fieldInformation = fieldHandler.getFieldInformation();

        // Sort the field information by table name so that sequence order of
        // the message ID values is applied to the tables' alphabetical order
        Collections.sort(fieldInformation, new Comparator<FieldInformation>()
        {
            /******************************************************************
             * Compare the table names of two field definitions. Force lower
             * case to eliminate case differences in the comparison
             *****************************************************************/
            @Override
            public int compare(FieldInformation fld1, FieldInformation fld2)
            {
                return fld1.getOwnerName().toLowerCase().compareTo(fld2.getOwnerName().toLowerCase());
            }
        });

        // Create a list to contain the existing message ID values
        List<String> usedIDs = new ArrayList<String>();

        // Check if the overwrite check box is not selected
        if (!overwriteCbx.isSelected())
        {
            // Step through each defined data field
            for (int index = 0; index < fieldInformation.size(); index++)
            {
                // Get the reference to the field information
                FieldInformation fieldInfo = fieldInformation.get(index);

                // Check if the data field value isn't blank and doesn't match
                // one already added to the list
                if (!fieldInfo.getValue().isEmpty()
                    && !usedIDs.contains(fieldInfo.getValue()))
                {
                    // Add the data field value to the list of existing message
                    // ID values
                    usedIDs.add(fieldInfo.getValue());
                }
            }
        }

        // Step through each defined data field
        for (int index = 0; index < fieldInformation.size(); index++)
        {
            // Get the reference to the field information
            FieldInformation fieldInfo = fieldInformation.get(index);

            // Check if the field is for a table (and not a default field),
            // that the field name matches the one supplied by the user in the
            // text field, and that either the overwrite check box is selected
            // or the field is blank
            if (!fieldInfo.getOwnerName().contains(":")
                && fieldInfo.getFieldName().equals(msgIDNameFld.getText())
                && (overwriteCbx.isSelected()
                || fieldInfo.getValue().isEmpty()))
            {
                String idValue;

                do
                {
                    // Format the message ID value
                    idValue = String.format("0x%04x", startID);

                    // Adjust the message ID value by the interval amount and
                    // set the flag to indicate a message ID value is changed
                    startID += interval;

                } while (usedIDs.contains(idValue));
                // Continue to loop as long as the ID value matches an existing
                // one. This prevents assigning a duplicate ID

                // Set the message ID value
                fieldInfo.setValue(idValue);

                // If any table editor dialogs are open then the displayed data
                // fields need to be updated to match the message ID value
                // change. Both the currently displayed and committed field
                // values are updated so that when the editor dialog is closed
                // these changes aren't seen as table changes since they're
                // already committed to the database. Step through each table
                // editor dialog
                for (CcddTableEditorDialog editorDialog : ccddMain.getTableEditorDialogs())
                {
                    boolean isUpdate = false;

                    // Step through each table editor in the editor dialog
                    for (CcddTableEditorHandler editor : editorDialog.getTableEditors())
                    {
                        // Get the reference to the table's field handler
                        CcddFieldHandler editorFldHandler = editor.getTableInformation().getFieldHandler();

                        // Check if the table contains the message ID field; if
                        // so then update it to the new value
                        if (editorFldHandler.updateField(fieldInfo))
                        {
                            // Update the committed message ID value
                            editor.getCommittedTableInformation().getFieldHandler().updateField(fieldInfo);

                            // Update the editor data fields
                            editor.updateDataFields();

                            // Set the flag to indicate the table/field
                            // combination was located and stop searching
                            isUpdate = true;
                            break;
                        }
                    }

                    // Check if this table/field combination has been located
                    if (isUpdate)
                    {
                        // Stop searching
                        break;
                    }
                }

                // Set the flag to indicate a message ID value is changed
                isChanges = true;
            }
        }

        // Check if a message ID value changed
        if (isChanges)
        {
            // Store the updated data fields table
            dbTable.storeInformationTable(InternalTable.FIELDS,
                                          fieldHandler.getFieldDefinitionList(),
                                          null,
                                          CcddAssignTableMsgIDDialog.this);
        }
    }

    /**************************************************************************
     * Verify that the dialog content is valid
     * 
     * @return true if the input values are valid
     *************************************************************************/
    @Override
    protected boolean verifySelection()
    {
        // Assume the dialog input is valid
        boolean isValid = true;

        try
        {
            // Remove any excess white space
            msgIDNameFld.setText(msgIDNameFld.getText().trim());
            startMsgIDFld.setText(startMsgIDFld.getText().trim());

            // Check if the message ID name is blank
            if (msgIDNameFld.getText().isEmpty())
            {
                // Inform the user that the name or size is invalid
                throw new CCDDException("Message ID name must be entered");
            }

            // Check if the starting message ID value is not in hexadecimal
            // format
            if (!startMsgIDFld.getText().matches(InputDataType.HEXADECIMAL.getInputMatch()))
            {
                // Inform the user that the starting ID is invalid
                throw new CCDDException("Starting ID must be in the format<br>&#160;&#160;<i>&lt;</i>"
                                        + "0x<i>&gt;</i>#<br>where # is one or more hexadecimal digits");
            }

            // Check if the message ID interval value is not a positive integer
            if (!msgIDIntervalFld.getText().matches(InputDataType.INT_POSITIVE.getInputMatch()))
            {
                // Inform the user that the interval is invalid
                throw new CCDDException("ID interval must be a positive integer");
            }
        }
        catch (CCDDException ce)
        {
            // Inform the user that the input value is invalid
            new CcddDialogHandler().showMessageDialog(CcddAssignTableMsgIDDialog.this,
                                                      "<html><b>"
                                                          + ce.getMessage(),
                                                      "Missing/Invalid Input",
                                                      JOptionPane.WARNING_MESSAGE,
                                                      DialogOption.OK_OPTION);

            // Set the flag to indicate the dialog input is invalid
            isValid = false;
        }

        return isValid;
    }
}
