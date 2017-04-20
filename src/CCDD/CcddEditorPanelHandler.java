/**
 * CFS Command & Data Dictionary editor panel handler. Copyright 2017 United
 * States Government as represented by the Administrator of the National
 * Aeronautics and Space Administration. No copyright is claimed in the United
 * States under Title 17, U.S. Code. All Other Rights Reserved.
 */
package CCDD;

import static CCDD.CcddConstants.LABEL_FONT_BOLD;
import static CCDD.CcddConstants.LABEL_FONT_PLAIN;
import static CCDD.CcddConstants.LABEL_HORIZONTAL_SPACING;
import static CCDD.CcddConstants.LABEL_TEXT_COLOR;
import static CCDD.CcddConstants.LABEL_VERTICAL_SPACING;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputVerifier;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotUndoException;

import CCDD.CcddClasses.FieldInformation;
import CCDD.CcddClasses.WrapLayout;
import CCDD.CcddConstants.DialogOption;
import CCDD.CcddConstants.InputDataType;
import CCDD.CcddEditorPanelHandler.UndoableCheckBox.UndoableToggleButtonModel;

/******************************************************************************
 * CFS Command & Data Dictionary editor panel handler class. The editor panel
 * contains the table description and data fields (if any)
 *****************************************************************************/
public abstract class CcddEditorPanelHandler
{
    // Class references
    private CcddFieldEditorDialog fieldEditorDialog;
    private CcddUndoManager undoManager;
    private CcddFieldHandler fieldHandler;

    // Components referenced by multiple methods
    private UndoableTextArea descriptionFld;
    private JPanel editorPnl;
    private Border border;
    private JPanel fieldPnl;
    private GridBagConstraints gbc;

    // Name of the owner (table or group) for the editor panel
    private String ownerName;

    // Reference to the editor dialog that created this editor panel
    private Component parent;

    // Description field scroll pane (not used with table descriptions)
    private JScrollPane descScrollPane;

    // Flag that indicates if each data field value update should terminate the
    // edit sequence
    private boolean isAutoEndEditSequence;

    /**************************************************************************
     * Get a reference to the editor panel handler
     * 
     * @return Reference to the editor panel handler
     *************************************************************************/
    protected CcddEditorPanelHandler getEditPanelHandler()
    {
        return this;
    }

    /**************************************************************************
     * Get the JPanel containing the table editor
     * 
     * @return JPanel containing the table editor
     *************************************************************************/
    protected JPanel getEditorPanel()
    {
        return editorPnl;
    }

    /**************************************************************************
     * Get the undo/redo manager
     * 
     * @return Reference to the undo/redo manager
     *************************************************************************/
    protected CcddUndoManager getEditPanelUndoManager()
    {
        return undoManager;
    }

    /**************************************************************************
     * Set the undo/redo manager
     * 
     * @param undoManager
     *            undo/redo manager
     *************************************************************************/
    protected void setEditPanelUndoManager(CcddUndoManager undoManager)
    {
        this.undoManager = undoManager;
    }

    /**************************************************************************
     * Enable/disable automatic ending of an edit sequence when a data field
     * value is changed
     * 
     * @param enable
     *            true to enable automatic edit sequence ending
     *************************************************************************/
    protected void setAutoEndEditSequenceEnable(boolean enable)
    {
        isAutoEndEditSequence = enable;
    }

    /**************************************************************************
     * Get the owner name for this editor
     * 
     * @return Owner name for this editor
     *************************************************************************/
    protected String getTableEditorOwnerName()
    {
        return ownerName;
    }

    /**************************************************************************
     * Set the owner name for this editor
     * 
     * @param owner
     *            name for this editor
     *************************************************************************/
    protected void setTableEditorOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    /**************************************************************************
     * Get the field information for this editor
     * 
     * @return Field information for this editor
     *************************************************************************/
    protected CcddFieldHandler getFieldHandler()
    {
        return fieldHandler;
    }

    /**************************************************************************
     * Placeholder for the method to return the editor dialog reference
     *************************************************************************/
    protected abstract Component getTableEditor();

    /**************************************************************************
     * Placeholder for the method to update the owning dialog's change
     * indicator
     *************************************************************************/
    protected abstract void updateOwnerChangeIndicator();

    /**************************************************************************
     * Get the description field text
     * 
     * @return Description field text
     *************************************************************************/
    protected String getDescription()
    {
        return descriptionFld.getText().trim();
    }

    /**************************************************************************
     * Set the description field text
     * 
     * @param description
     *            description field text
     *************************************************************************/
    protected void setDescription(String description)
    {
        descriptionFld.setText(description);
    }

    /**************************************************************************
     * Enable/disable the description field, set its background color based on
     * the enable status, and set the description text
     * 
     * @param enable
     *            true to enable editing the description, false to disable
     * 
     * @param description
     *            text to place in the description field
     *************************************************************************/
    protected void enableDescriptionField(boolean enable, String description)
    {
        Color backColor = enable ? Color.white : Color.LIGHT_GRAY;
        descriptionFld.setText(description);
        descriptionFld.setEditable(enable);
        descriptionFld.setBackground(backColor);
        descScrollPane.setBackground(backColor);
    }

    /**************************************************************************
     * Update the field information to match the data field text field values
     * 
     * @param fieldInformation
     *            data field information list
     *************************************************************************/
    protected void updateCurrentFields(List<FieldInformation> fieldInformation)
    {
        // Step through each data field
        for (FieldInformation fieldInfo : fieldInformation)
        {
            // Check if a text field exists for this data field
            if (fieldInfo.getInputFld() != null)
            {
                // Check if this is a boolean input (check box) data field
                if (fieldInfo.getInputType() == InputDataType.BOOLEAN)
                {
                    // Update the data field with the check box selection state
                    fieldInfo.setValue(((UndoableCheckBox) fieldInfo.getInputFld()).isSelected()
                                                                                                ? "true"
                                                                                                : "false");
                }
                // Not a boolean input (check box) data field
                else
                {
                    // Update the data field with the text field's contents
                    fieldInfo.setValue(((UndoableTextField) fieldInfo.getInputFld()).getText());
                }
            }
        }
    }

    /**************************************************************************
     * Get the currently active data field editor dialog
     * 
     * @return Currently active data field editor dialog
     *************************************************************************/
    protected CcddFieldEditorDialog getFieldEditorDialog()
    {
        return fieldEditorDialog;
    }

    /**************************************************************************
     * Set the currently active data field editor dialog
     * 
     * @param fieldEditor
     *            currently active data field editor dialog
     *************************************************************************/
    protected void setFieldEditorDialog(CcddFieldEditorDialog fieldEditorDialog)
    {
        this.fieldEditorDialog = fieldEditorDialog;
    }

    /**************************************************************************
     * Create the table editor panel
     * 
     * @param parent
     *            reference to the dialog calling this method
     * 
     * @param scrollPane
     *            scroll pane containing the table; null if this editor panel
     *            does not contain a table
     * 
     * @param ownerName
     *            name of the owner of this editor panel; null if no owner name
     *            is associated with the editor
     * 
     * @param description
     *            description field text
     * 
     * @param fieldHandler
     *            field handler reference
     *************************************************************************/
    protected void createEditorPanel(Component parent,
                                     JScrollPane scrollPane,
                                     String ownerName,
                                     String description,
                                     CcddFieldHandler fieldHandler)
    {
        this.parent = parent;
        this.ownerName = ownerName;
        this.fieldHandler = fieldHandler;

        // Set the flag to allow automatic ending of a data field value edit
        // sequence
        isAutoEndEditSequence = true;

        // Set the initial layout manager characteristics
        gbc = new GridBagConstraints(0,
                                     0,
                                     1,
                                     1,
                                     1.0,
                                     1.0,
                                     GridBagConstraints.LINE_START,
                                     GridBagConstraints.BOTH,
                                     new Insets(0, 0, 0, 0),
                                     0,
                                     0);

        // Create an outer panel to put the editor panel in (the border doesn't
        // appear without this) and add the table description text field
        editorPnl = new JPanel(new GridBagLayout());

        // Check if this editor contains a table
        if (scrollPane != null)
        {
            // Define the editor panel to contain the table
            JPanel innerPanel = new JPanel();
            innerPanel.setLayout(new BoxLayout(innerPanel, BoxLayout.X_AXIS));
            innerPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            innerPanel.add(scrollPane);
            editorPnl.add(innerPanel, gbc);
        }

        // Create a border for the input fields
        border = BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED,
                                                                                    Color.LIGHT_GRAY,
                                                                                    Color.GRAY),
                                                    BorderFactory.createEmptyBorder(2, 2, 2, 2));

        // Create a panel to hold the table's system name, description and, if
        // applicable, message ID information
        JPanel descriptionPnl = new JPanel(new GridBagLayout());

        // Create the description label
        JLabel descriptionLbl = new JLabel("Description");
        descriptionLbl.setFont(LABEL_FONT_BOLD);
        descriptionLbl.setForeground(LABEL_TEXT_COLOR);

        // Check if this editor doesn't contain a table
        if (scrollPane == null)
        {
            gbc.insets.top = LABEL_VERTICAL_SPACING / 2;
            gbc.insets.bottom = LABEL_VERTICAL_SPACING / 2;
        }

        // Add the table description label
        gbc.insets.left = LABEL_HORIZONTAL_SPACING / 2;
        gbc.insets.right = LABEL_HORIZONTAL_SPACING / 2;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridy++;
        descriptionPnl.add(descriptionLbl, gbc);

        // Create the description input field
        descriptionFld = new UndoableTextArea(description, 3, 20);
        descriptionFld.setFont(LABEL_FONT_PLAIN);
        descriptionFld.setEditable(true);
        descriptionFld.setForeground(Color.BLACK);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        descriptionFld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;

        // Check if this editor doesn't contain a table
        if (scrollPane == null)
        {
            // Place the description field within a scroll pane, initially
            // disabled, and add the field to the editor
            editorPnl.setBorder(BorderFactory.createEtchedBorder());
            gbc.gridy++;
            descriptionFld.setBackground(Color.LIGHT_GRAY);
            descriptionFld.setBorder(BorderFactory.createEmptyBorder());
            descScrollPane = new JScrollPane(descriptionFld);
            descScrollPane.setBackground(Color.LIGHT_GRAY);
            descScrollPane.setBorder(border);
            descriptionPnl.add(descScrollPane, gbc);
        }
        // The editor contains a table
        else
        {
            // Place the description field within a scroll pane and add the
            // field to the editor
            editorPnl.setBorder(BorderFactory.createEmptyBorder());
            gbc.gridx++;
            descriptionFld.setToolTipText("Table description");
            descriptionFld.setBackground(Color.WHITE);
            descriptionFld.setBorder(border);
            descriptionPnl.add(descriptionFld, gbc);
            gbc.insets.top = LABEL_VERTICAL_SPACING;
            gbc.insets.bottom = LABEL_VERTICAL_SPACING;
        }

        // Add the description panel to the editor
        gbc.weighty = 0.0;
        gbc.gridx = 0;
        gbc.gridy++;
        editorPnl.add(descriptionPnl, gbc);

        // Add the data field panel to the editor
        gbc.gridy++;
        gbc.insets.top = 0;
        gbc.insets.bottom = 0;
        createDataFieldPanel();
    }

    /**************************************************************************
     * Create the data fields for display below the table
     *************************************************************************/
    protected void createDataFieldPanel()
    {
        // Check if the data fields are already displayed
        if (fieldPnl != null)
        {
            // Discard the edits associated with the description and data
            // fields since these changes can't be undone/redone once the
            // components are removed. Other edits (e.g., those in an
            // associated table) remain and can still be undone/redone
            undoManager.discardEdits(new String[] {"TextField",
                                                   "TextArea",
                                                   "CheckBox"});

            // Remove the existing data fields
            editorPnl.remove(fieldPnl);
            editorPnl.validate();
        }

        // Check if any data fields exist
        if (fieldHandler.getFieldInformation() != null
            && !fieldHandler.getFieldInformation().isEmpty())
        {
            // Create a panel to contain the data fields. As the editor is
            // resized the field panel is resized to contain the data fields,
            // wrapping them to new lines as needed
            fieldPnl = new JPanel(new WrapLayout(WrapLayout.LEADING));

            // Adjust the border to align the first field with the description
            // label
            fieldPnl.setBorder(BorderFactory.createEmptyBorder(-LABEL_VERTICAL_SPACING,
                                                               -LABEL_HORIZONTAL_SPACING,
                                                               0,
                                                               0));

            // Step through each data field
            for (final FieldInformation fieldInfo : fieldHandler.getFieldInformation())
            {
                switch (fieldInfo.getInputType())
                {
                    case BREAK:
                        // Create a text field for the separator so it can be
                        // handled like other fields
                        fieldInfo.setInputFld(new UndoableTextField());

                        // Add a vertical separator to the field panel
                        fieldPnl.add(new JSeparator(JSeparator.VERTICAL));
                        break;

                    case SEPARATOR:
                        // Create a text field for the separator so it can be
                        // handled like other fields
                        fieldInfo.setInputFld(new UndoableTextField());

                        // Add a horizontal separator to the field panel
                        fieldPnl.add(new JSeparator());
                        break;

                    case BOOLEAN:
                        // Create the data field check box
                        fieldInfo.setInputFld(new UndoableCheckBox(fieldInfo.getFieldName(),
                                                                   Boolean.valueOf(fieldInfo.getValue())));
                        UndoableCheckBox booleanCb = (UndoableCheckBox) fieldInfo.getInputFld();
                        booleanCb.setFont(LABEL_FONT_BOLD);
                        booleanCb.setForeground(LABEL_TEXT_COLOR);

                        // Adjust the left and right padding around the check
                        // box so that it is spaced the same as a text field
                        // data field
                        booleanCb.setBorder(BorderFactory.createEmptyBorder(0,
                                                                            LABEL_HORIZONTAL_SPACING,
                                                                            0,
                                                                            LABEL_HORIZONTAL_SPACING));

                        // Check if a description exists for this field
                        if (!fieldInfo.getDescription().isEmpty())
                        {
                            // Set the description as the tool tip text for
                            // this check box
                            booleanCb.setToolTipText(fieldInfo.getDescription());
                        }

                        // And the check box to the field panel
                        fieldPnl.add(booleanCb);

                        break;

                    default:
                        // Create a panel for a single label and text field
                        // pair. This is necessary so that the two will stay
                        // together if line wrapping occurs due to a window
                        // size change
                        JPanel singleFldPnl = new JPanel(new FlowLayout(FlowLayout.LEADING,
                                                                        LABEL_HORIZONTAL_SPACING,
                                                                        LABEL_VERTICAL_SPACING / 4));
                        singleFldPnl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

                        // Create the data field label
                        JLabel fieldLbl = new JLabel(fieldInfo.getFieldName());
                        fieldLbl.setFont(LABEL_FONT_BOLD);
                        fieldLbl.setForeground(LABEL_TEXT_COLOR);
                        singleFldPnl.add(fieldLbl);

                        // Create the data field input field
                        fieldInfo.setInputFld(new UndoableTextField(fieldInfo.getValue(),
                                                                    fieldInfo.getSize()));
                        UndoableTextField inputFld = (UndoableTextField) fieldInfo.getInputFld();
                        inputFld.setFont(LABEL_FONT_PLAIN);
                        inputFld.setEditable(true);
                        inputFld.setBorder(border);
                        inputFld.setForeground(Color.BLACK);
                        inputFld.setBackground(fieldInfo.getValue().isEmpty()
                                               && fieldInfo.isRequired()
                                                                        ? Color.YELLOW
                                                                        : Color.WHITE);

                        // Check if a description exists for this field
                        if (!fieldInfo.getDescription().isEmpty())
                        {
                            // Set the description as the tool tip text for
                            // this text field
                            inputFld.setToolTipText(fieldInfo.getDescription());
                        }

                        // Add the data field to the single field panel
                        singleFldPnl.add(inputFld);

                        // And the single field to the field panel
                        fieldPnl.add(singleFldPnl);

                        // Create an input field verifier for the data field
                        inputFld.setInputVerifier(new InputVerifier()
                        {
                            /**************************************************
                             * Verify the contents of a the data field
                             *************************************************/
                            @Override
                            public boolean verify(JComponent input)
                            {
                                boolean isValid = true;

                                // Get the data field reference to shorten
                                // subsequent calls
                                UndoableTextField inputFld = (UndoableTextField) input;

                                // Remove leading and trailing white space
                                // characters from the data field contents
                                String inputTxt = inputFld.getText().trim();

                                // Check if the field contains an illegal
                                // character
                                if (!fieldInfo.getInputType().getInputMatch().isEmpty()
                                    && !inputTxt.isEmpty()
                                    && !inputTxt.matches(fieldInfo.getInputType().getInputMatch()))
                                {
                                    // Inform the user that the data field
                                    // contents is invalid
                                    new CcddDialogHandler().showMessageDialog(parent,
                                                                              "<html><b>Invalid characters in field '</b>"
                                                                                  + fieldInfo.getFieldName()
                                                                                  + "<b>'; "
                                                                                  + fieldInfo.getInputType().getInputName().toLowerCase()
                                                                                  + " expected",
                                                                              "Invalid "
                                                                                  + fieldInfo.getInputType().getInputName(),
                                                                              JOptionPane.WARNING_MESSAGE,
                                                                              DialogOption.OK_OPTION);

                                    // Toggle the controls enable status so
                                    // that the buttons are redrawn correctly
                                    if (parent instanceof CcddFrameHandler)
                                    {
                                        ((CcddFrameHandler) parent).setControlsEnabled(false);
                                        ((CcddFrameHandler) parent).setControlsEnabled(true);
                                    }
                                    else if (parent instanceof CcddDialogHandler)
                                    {
                                        ((CcddDialogHandler) parent).setControlsEnabled(false);
                                        ((CcddDialogHandler) parent).setControlsEnabled(true);
                                    }

                                    // Restore the previous value in the data
                                    // field and undo the input
                                    inputFld.setText(inputFld.getText(), false);

                                    isValid = false;
                                }
                                // The input is valid
                                else
                                {
                                    // Store the 'cleaned' text back into the
                                    // text field. For numeric types, reformat
                                    // the input value
                                    inputFld.setText(fieldInfo.getInputType().formatInput(inputTxt));

                                    // Set the text field background color. If
                                    // the field is empty and is flagged as
                                    // required then set the background to
                                    // indicate a value should be supplied
                                    setFieldBackground(fieldInfo);
                                }

                                return isValid;
                            }
                        });

                        break;
                }
            }

            // Check that at least one field exists
            if (fieldPnl.getComponentCount() != 0)
            {
                // Add the data field panel to the dialog
                editorPnl.add(fieldPnl, gbc);
            }
        }
    }

    /**************************************************************************
     * Clear the values from all fields
     *************************************************************************/
    protected void clearFieldValues()
    {
        // Disable automatically ending the edit sequence. This allows all of
        // the cleared fields to be grouped into a single sequence so that if
        // undone, all fields are restored
        isAutoEndEditSequence = false;

        for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
        {
            // Check if this is a boolean input (check box) data field
            if (fieldInfo.getInputType() == InputDataType.BOOLEAN)
            {
                // Set the field value to 'false'
                fieldInfo.setValue("false");

                // Set the check box
                ((UndoableCheckBox) fieldInfo.getInputFld()).setSelected(false);
            }
            // Not a boolean input (check box) data field
            else
            {
                // Get the reference to the text field
                UndoableTextField inputFld = (UndoableTextField) fieldInfo.getInputFld();

                // Clear the field value
                fieldInfo.setValue("");
                inputFld.setText("");

                // Set the text field background color. If the field is flagged
                // as required then set the background to indicate a value
                // should be supplied
                setFieldBackground(fieldInfo);
            }
        }

        // Re-enable automatic edit sequence ending, then end the edit sequence
        // to group the cleared fields
        isAutoEndEditSequence = true;
        undoManager.endEditSequence();
    }

    /**************************************************************************
     * Set the data field background color for all fields based each field's
     * value and required flag
     *************************************************************************/
    protected void setFieldBackgound()
    {
        // Create a runnable object to be executed
        SwingUtilities.invokeLater(new Runnable()
        {
            /******************************************************************
             * Set the data field colors after other pending events are
             * complete. If this isn't done following other events then the
             * colors aren't updated consistently
             *****************************************************************/
            @Override
            public void run()
            {
                // Step through each field
                for (FieldInformation fieldInfo : fieldHandler.getFieldInformation())
                {
                    // Check if this isn't a boolean input (check box) data
                    // field
                    if (fieldInfo.getInputType() != InputDataType.BOOLEAN)
                    {
                        // Set the text field background color. If the field is
                        // empty and is flagged as required then set the
                        // background to indicate a value should be supplied
                        setFieldBackground(fieldInfo);
                    }
                }
            }
        });
    }

    /**************************************************************************
     * Set the specified data field's background color based the field's value
     * and required flag
     * 
     * @param fieldInfo
     *            reference to the data field's information
     *************************************************************************/
    private void setFieldBackground(FieldInformation fieldInfo)
    {
        // Set the text field background color. If the field is empty and is
        // flagged as required then set the background to indicate a value
        // should be supplied
        ((UndoableTextField) fieldInfo.getInputFld()).setBackground(fieldInfo.getValue().isEmpty()
                                                                    && fieldInfo.isRequired()
                                                                                             ? Color.YELLOW
                                                                                             : Color.WHITE);
    }

    /**************************************************************************
     * Check box value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableCheckBox extends JCheckBox
    {
        private boolean oldValue;

        /**********************************************************************
         * Toggle button model undo/redo class
         *********************************************************************/
        protected class UndoableToggleButtonModel extends ToggleButtonModel
        {
            /******************************************************************
             * Override the default method with a method that includes a flag
             * to store the edit in the undo stack
             *****************************************************************/
            @Override
            public void setSelected(boolean select)
            {
                setSelected(select, true);
            }

            /******************************************************************
             * Change the value of a check box
             * 
             * @param select
             *            check box state; true to select
             * 
             * @param undoable
             *            true if the change can be undone
             *****************************************************************/
            public void setSelected(boolean select, boolean undoable)
            {
                super.setSelected(select);

                // Check if the edit is undoable and if the check box selection
                // state changed
                if (undoable && oldValue != isSelected())
                {
                    // Get the listeners for this event
                    UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                    // Check if there is an edit listener registered
                    if (listeners != null)
                    {
                        // Create the edit event to be passed to the listeners
                        UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                            new CheckBoxEdit(UndoableCheckBox.this,
                                                                                             oldValue,
                                                                                             isSelected()));

                        // Store the new check box selection state
                        oldValue = isSelected();

                        // Step through the registered listeners
                        for (UndoableEditListener listener : listeners)
                        {
                            // Inform the listener that an update occurred
                            listener.undoableEditHappened(editEvent);
                        }
                    }

                    // Check if the flag is set that allows automatically
                    // ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }
                }
            }
        }

        /**********************************************************************
         * Check box value undo/redo class constructor
         *********************************************************************/
        UndoableCheckBox()
        {
            // Create the check box
            super();

            // Set the model so that check box edits can be undone/redone
            setModel(new UndoableToggleButtonModel());
        }

        /**********************************************************************
         * Check box value undo/redo class constructor
         * 
         * @param text
         *            text to display beside the check box
         * 
         * @param value
         *            initial check box selection state
         *********************************************************************/
        UndoableCheckBox(String text, boolean selected)
        {
            // Create the check box
            super(text, selected);

            // Set the model so that check box edits can be undone/redone
            setModel(new UndoableToggleButtonModel());
        }
    }

    /**************************************************************************
     * Check box edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class CheckBoxEdit extends AbstractUndoableEdit
    {
        private final UndoableCheckBox checkBox;
        private final boolean oldValue;
        private final boolean newValue;

        /**********************************************************************
         * Check box edit event handler constructor
         * 
         * @param checkBox
         *            reference to the check box being edited
         * 
         * @param oldValue
         *            previous check box selection state
         * 
         * @param newValue
         *            new check box selection state
         *********************************************************************/
        private CheckBoxEdit(UndoableCheckBox checkBox,
                             boolean oldValue,
                             boolean newValue)
        {
            this.checkBox = checkBox;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the check box edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current check box selection state with the old state
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the check box where the change was undone
            setSelectedCheckBox(oldValue);
        }

        /**********************************************************************
         * Replace the current check box selection state with the new state
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the check box where the change was redone
            setSelectedCheckBox(newValue);
        }

        /**********************************************************************
         * Set the selected check box. In order for this to select the text
         * area it must be scheduled to execute after other pending events
         * 
         * @param selected
         *            check box selection state
         *********************************************************************/
        private void setSelectedCheckBox(final boolean selected)
        {
            // Update the check box selection state
            ((UndoableToggleButtonModel) checkBox.getModel()).setSelected(selected, false);

            // Request a focus change to the check box that was changed
            setComponentFocus(checkBox);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "CheckBox";
        }
    }

    /**************************************************************************
     * Text field value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextField extends JTextField
    {
        private String oldValue;
        private boolean allowUndo = false;

        /**********************************************************************
         * Text field value undo/redo class constructor
         *********************************************************************/
        UndoableTextField()
        {
            // Create the text field
            super();

            // This is initially false to prevent storing the initial values as
            // undoable edits. Set the flag to true so that subsequent edits
            // can be undone
            allowUndo = true;

            // Add a listener for text field focus changes
            addFocusChangeListener("");
        }

        /**********************************************************************
         * Text field value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text field
         *********************************************************************/
        UndoableTextField(String text)
        {
            // Create the text field
            super(text);

            // This is initially false to prevent storing the initial values as
            // undoable edits. Set the flag to true so that subsequent edits
            // can be undone
            allowUndo = true;

            // Add a listener for text field focus changes
            addFocusChangeListener(text);
        }

        /**********************************************************************
         * Text field value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text field
         * 
         * @param columns
         *            the number of columns to use to calculate the preferred
         *            width of the text field
         *********************************************************************/
        UndoableTextField(String text, int columns)
        {
            // Create the text field
            super(text, columns);

            // This is initially false to prevent storing the initial values as
            // undoable edits. Set the flag to true so that subsequent edits
            // can be undone
            allowUndo = true;

            // Add a listener for text field focus changes
            addFocusChangeListener(text);
        }

        /**********************************************************************
         * Override the default method with a method that includes a flag to
         * store the edit in the undo stack
         *********************************************************************/
        @Override
        public void setText(String text)
        {
            setText(text, true);
        }

        /**********************************************************************
         * Change the value of a text field
         * 
         * @param text
         *            new text field value
         * 
         * @param undoable
         *            true if the change can be undone
         *********************************************************************/
        protected void setText(String text, boolean undoable)
        {
            // Check if the original value hasn't been set
            if (oldValue == null)
            {
                // Set to blank to prevent an exception when comparing to the
                // new value below
                oldValue = "";
            }

            // Check if the text field text changed
            if (!oldValue.equals(text))
            {
                super.setText(text);

                // Check if the edit is undoable
                if (allowUndo && undoable)
                {
                    // Get the listeners for this event
                    UndoableEditListener listeners[] = getListeners(UndoableEditListener.class);

                    // Check if there is an edit listener registered
                    if (listeners != null)
                    {
                        // Create the edit event to be passed to the listeners
                        UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                            new TextFieldEdit(UndoableTextField.this,
                                                                                              oldValue,
                                                                                              text));

                        // Store the value
                        oldValue = text;

                        // Step through the registered listeners
                        for (UndoableEditListener listener : listeners)
                        {
                            // Inform the listener that an update occurred
                            listener.undoableEditHappened(editEvent);
                        }
                    }

                    // Check if the flag is set that allows automatically
                    // ending the edit sequence
                    if (isAutoEndEditSequence)
                    {
                        // End the editing sequence
                        undoManager.endEditSequence();
                    }
                }
            }
        }

        /**********************************************************************
         * Add a focus change listener to the text field
         *
         * @param text
         *            text to display in the text field
         *********************************************************************/
        private void addFocusChangeListener(String text)
        {
            // Initialize the text field's original value
            oldValue = text;

            // Add a listener for text field focus changes
            addFocusListener(new FocusAdapter()
            {
                /**************************************************************
                 * Handle a focus gained event
                 *************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Store the current text field value
                    oldValue = getText();
                }
            });
        }
    }

    /**************************************************************************
     * Text field edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class TextFieldEdit extends AbstractUndoableEdit
    {
        private final UndoableTextField textField;
        private final String oldValue;
        private final String newValue;

        /**********************************************************************
         * Text field edit event handler constructor
         * 
         * @param textField
         *            reference to the text field being edited
         * 
         * @param oldValue
         *            previous text field value
         * 
         * @param newValue
         *            new text field value
         *********************************************************************/
        private TextFieldEdit(UndoableTextField textField,
                              String oldValue,
                              String newValue)
        {
            this.textField = textField;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the text field edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current text field value with the old value
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text field where the change was undone
            setSelectedTextField(oldValue);
        }

        /**********************************************************************
         * Replace the current text field value with the new value
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text field where the change was redone
            setSelectedTextField(newValue);
        }

        /**********************************************************************
         * Set the selected text field. In order for this to select the text
         * field it must be scheduled to execute after other pending events
         * 
         * @param value
         *            text to place in the text field
         *********************************************************************/
        private void setSelectedTextField(final String value)
        {
            // Update the text field value
            textField.setText(value, false);

            // Request a focus change to the text field that was changed
            setComponentFocus(textField);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextField";
        }
    }

    /**************************************************************************
     * Text area value undo/redo class
     *************************************************************************/
    @SuppressWarnings("serial")
    protected class UndoableTextArea extends JTextArea
    {
        private String oldValue;

        /**********************************************************************
         * Text area value undo/redo class constructor
         *********************************************************************/
        UndoableTextArea()
        {
            // Create the text area
            super();

            // Add a listener for text area focus changes
            addFocusChangeListener("");
        }

        /**********************************************************************
         * Text area value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text area
         *********************************************************************/
        UndoableTextArea(String text)
        {
            // Create the text area
            super(text);

            // Add a listener for text area focus changes
            addFocusChangeListener(text);
        }

        /**********************************************************************
         * Text area value undo/redo class constructor
         * 
         * @param text
         *            text to display in the text area
         * 
         * @param rows
         *            number of rows
         * 
         * @param columns
         *            the number of columns to use to calculate the preferred
         *            width of the text area
         *********************************************************************/
        UndoableTextArea(String text, int rows, int columns)
        {
            // Create the text area
            super(text, rows, columns);

            // Add a listener for text area focus changes
            addFocusChangeListener(text);
        }

        /**********************************************************************
         * Add a focus change listener to the text area
         *
         * @param text
         *            text to display in the text area
         *********************************************************************/
        private void addFocusChangeListener(String text)
        {
            // Initialize the text area's original value
            oldValue = text;

            // Add a listener for text area focus changes
            addFocusListener(new FocusListener()
            {
                /**************************************************************
                 * Handle a focus gained event
                 *************************************************************/
                @Override
                public void focusGained(FocusEvent fe)
                {
                    // Store the current text area value
                    oldValue = getText();
                }

                /**************************************************************
                 * Handle a focus lost event
                 *************************************************************/
                @Override
                public void focusLost(FocusEvent fe)
                {
                    // Check if the text area value changed
                    if (!oldValue.equals(getText()))
                    {
                        // Get the listeners for this event
                        UndoableEditListener listeners[] =
                            getListeners(UndoableEditListener.class);

                        // Check if there is an edit listener registered
                        if (listeners != null)
                        {
                            // Create the edit event to be passed to the
                            // listeners
                            UndoableEditEvent editEvent = new UndoableEditEvent(this,
                                                                                new TextAreaEdit(UndoableTextArea.this,
                                                                                                 oldValue,
                                                                                                 getText()));

                            // Store the new text area value
                            oldValue = getText();

                            // Step through the registered listeners
                            for (UndoableEditListener listener : listeners)
                            {
                                // Inform the listener that an update occurred
                                listener.undoableEditHappened(editEvent);
                            }
                        }

                        // Check if the flag is set that allows automatically
                        // ending the edit sequence
                        if (isAutoEndEditSequence)
                        {
                            // End the editing sequence
                            undoManager.endEditSequence();
                        }
                    }
                }
            });
        }
    }

    /**************************************************************************
     * Text area edit event handler class
     *************************************************************************/
    @SuppressWarnings("serial")
    private class TextAreaEdit extends AbstractUndoableEdit
    {
        private final UndoableTextArea textArea;
        private final String oldValue;
        private final String newValue;

        /**********************************************************************
         * Text area edit event handler constructor
         * 
         * @param textArea
         *            reference to the text area being edited
         * 
         * @param oldValue
         *            previous text area value
         * 
         * @param newValue
         *            new text area value
         *********************************************************************/
        private TextAreaEdit(UndoableTextArea textArea,
                             String oldValue,
                             String newValue)
        {
            this.textArea = textArea;
            this.oldValue = oldValue;
            this.newValue = newValue;

            // Add the text area edit to the undo stack
            undoManager.addEditSequence(this);
        }

        /**********************************************************************
         * Replace the current text area value with the old value
         *********************************************************************/
        @Override
        public void undo() throws CannotUndoException
        {
            super.undo();

            // Select the text area where the change was undone
            setSelectedTextArea(oldValue);
        }

        /**********************************************************************
         * Replace the current text area value with the new value
         *********************************************************************/
        @Override
        public void redo() throws CannotUndoException
        {
            super.redo();

            // Select the text area where the change was redone
            setSelectedTextArea(newValue);
        }

        /**********************************************************************
         * Set the text and focus to the text area
         * 
         * @param value
         *            text to place in the text area
         *********************************************************************/
        private void setSelectedTextArea(final String value)
        {
            // Update the text area value
            textArea.setText(value);

            // Request a focus change to the text area that was changed
            setComponentFocus(textArea);
        }

        /**********************************************************************
         * Get the name of the edit type
         * 
         * @return Name of the edit type
         *********************************************************************/
        @Override
        public String getPresentationName()
        {
            return "TextArea";
        }
    }

    /**************************************************************************
     * Set the focus to the specified component, without generating a new edit
     * event
     * 
     * @param comp
     *            JComponent to which to set the focus
     *************************************************************************/
    private void setComponentFocus(JComponent comp)
    {
        // Check that the component is still visible
        if (comp.isVisible())
        {
            // Disable input verification since this sets the component value,
            // creating an edit event
            comp.setVerifyInputWhenFocusTarget(false);

            // Request a focus change to the component that was changed
            comp.requestFocusInWindow();

            // Re-enable input verification
            comp.setVerifyInputWhenFocusTarget(true);
        }
    }
}
